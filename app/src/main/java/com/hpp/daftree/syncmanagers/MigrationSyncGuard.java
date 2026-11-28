package com.hpp.daftree.syncmanagers;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.models.AppAuth;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.utils.SecureLicenseManager;

/**
 * بوابة ذكية قابلة لإعادة الاستخدام في كل ترقية لقاعدة البيانات:
 * تمنع تكرار المستندات أو طمس البيانات عند ترقية أكثر من جهاز لنفس الحساب.
 */
public final class MigrationSyncGuard {

    private static final String TAG = "MigrationSyncGuard";

    private MigrationSyncGuard() {}

    /**
     * معالجة ترقية 19 -> 20 (حقول الإشعارات في accounts + تصنيفات الميزانية الافتراضية).
     */
    public static void handleMigration19To20(Context context, AppDatabase db) {
        Integer flagVal = db.migrationFlagDao().getFlagValue("migration_19_20_pending");
        if (flagVal == null || flagVal != 1) return;

        // 1. فحص مستقل للحسابات (notifyWhatsApp)
        evaluateAndDispatch(
                context,
                db,
                "migration_19_20_pending",
                "accounts",
                "notifyWhatsApp",
                null,
                () -> {
                    db.getOpenHelper().getWritableDatabase()
                            .execSQL("UPDATE accounts SET syncStatus = 'SYNCED' WHERE syncStatus = 'PENDING'");
                    Log.i(TAG, "Migration 19->20 accounts: remote has notifyWhatsApp -> SYNCED");
                },
                () -> {
                    db.getOpenHelper().getWritableDatabase()
                            .execSQL("UPDATE accounts SET syncStatus = 'EDITED' WHERE syncStatus = 'PENDING'");
                    Log.i(TAG, "Migration 19->20 accounts: promoted PENDING -> EDITED");
                }
        );

    }

    /**
     * دالة عامة لأي ترقية مستقبلية:
     * @param flagKey            اسم علم الترقية في migration_flags
     * @param probeCollection    المجموعة التي يُفحص فيها الحقل الجديد في Firestore
     * @param probeField         اسم الحقل الجديد المميّز للترقية
     * @param fallbackCollection مجموعة احتياطية تُفحص إذا كانت المجموعة الأساسية فارغة (اختياري)
     * @param onAlreadyMigrated  يُنفَّذ إذا وُجد الحقل في Firestore (مسح الإضافات المحلية)
     * @param onNeedsUpload      يُنفَّذ إذا لم يوجد الحقل (تحويل PENDING إلى EDITED/NEW)
     */
    public static void evaluateAndDispatch(Context context,
                                           AppDatabase db,
                                           String flagKey,
                                           String probeCollection,
                                           String probeField,
                                           String fallbackCollection,
                                           Runnable onAlreadyMigrated,
                                           Runnable onNeedsUpload) {
        Context appCtx = context.getApplicationContext();
        SecureLicenseManager mgr = SecureLicenseManager.getInstance(appCtx);

        // الضيف أو المستخدم بجهاز واحد لا يحتاج لفحص Firestore
        if (mgr.isGuest() || mgr.getDevicesNos() <= 1) {
            onNeedsUpload.run();
            if (mgr.isGuest()) {
                db.migrationFlagDao().setFlag(flagKey, 0);
            } else {
                SyncCoordinator.triggerSyncWithCallback(appCtx, () ->
                        AppDatabase.databaseWriteExecutor.execute(() ->
                                db.migrationFlagDao().setFlag(flagKey, 0)));
            }
            return;
        }

        String uid = AppAuth.getUserID(appCtx);
        if (uid == null || uid.trim().isEmpty()) {
            return; // ننتظر توفر المعرّف في التشغيل القادم
        }

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        firestore.collection(probeCollection)
                .whereEqualTo("ownerUID", uid)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot != null && !snapshot.isEmpty()) {
                        DocumentSnapshot doc = snapshot.getDocuments().get(0);
                        boolean alreadyMigrated = doc.contains(probeField);
                        finishDecision(appCtx, db, flagKey, alreadyMigrated, onAlreadyMigrated, onNeedsUpload);
                    } else if (fallbackCollection != null && !fallbackCollection.isEmpty()) {
                        // فحص احتياطي في حال لم توجد مستندات في المجموعة الأساسية
                        firestore.collection(fallbackCollection)
                                .whereEqualTo("ownerUID", uid)
                                .limit(1)
                                .get()
                                .addOnSuccessListener(fbSnap -> {
                                    boolean alreadyMigrated = (fbSnap != null && !fbSnap.isEmpty());
                                    finishDecision(appCtx, db, flagKey, alreadyMigrated, onAlreadyMigrated, onNeedsUpload);
                                })
                                .addOnFailureListener(e ->
                                        Log.w(TAG, "Fallback probe failed (offline?), will retry later", e));
                    } else {
                        finishDecision(appCtx, db, flagKey, false, onAlreadyMigrated, onNeedsUpload);
                    }
                })
                .addOnFailureListener(e ->
                        Log.w(TAG, "Firestore probe failed (offline?), keeping PENDING for retry", e));
    }

    private static void finishDecision(Context appCtx,
                                       AppDatabase db,
                                       String flagKey,
                                       boolean alreadyMigrated,
                                       Runnable onAlreadyMigrated,
                                       Runnable onNeedsUpload) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                if (alreadyMigrated) {
                    onAlreadyMigrated.run();
                    db.migrationFlagDao().setFlag(flagKey, 0);
                    DaftreeRepository repo = new DaftreeRepository((Application) appCtx);
                    repo.setUserUID(AppAuth.getUserID(appCtx));
                    FirestoreSyncManager.getInstance(appCtx).startListening(repo, appCtx, repo::triggerSync);
                } else {
                    onNeedsUpload.run();
                    SyncCoordinator.triggerSyncWithCallback(appCtx, () ->
                            AppDatabase.databaseWriteExecutor.execute(() ->
                                    db.migrationFlagDao().setFlag(flagKey, 0)));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error finishing migration guard decision for " + flagKey, e);
            }
        });
    }
}
