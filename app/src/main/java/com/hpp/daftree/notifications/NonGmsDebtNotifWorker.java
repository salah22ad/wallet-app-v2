package com.hpp.daftree.notifications;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.hpp.daftree.dailyreminder.DailyReminderManager;
import com.hpp.daftree.models.AppAuth;
import com.hpp.daftree.utils.SecureLicenseManager;

import java.util.concurrent.ExecutionException;

public class NonGmsDebtNotifWorker extends Worker {

    private static final String TAG = "NonGmsDebtNotifWorker";

    public NonGmsDebtNotifWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        if (NonGmsNotificationScheduler.isGmsAvailable(context)) {
            return Result.success();
        }

        if (!DailyReminderManager.getInstance(context).areGeneralNotificationsEnabled()) {
            return Result.success();
        }

        SecureLicenseManager licence = SecureLicenseManager.getInstance(context);
        boolean isGuest = licence.isGuest();
        String targetUid = isGuest ? licence.guestUID() : AppAuth.getUserID(context);
        if ((targetUid == null || targetUid.trim().isEmpty()) && FirebaseAuth.getInstance().getCurrentUser() != null) {
            targetUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
        if (targetUid == null || targetUid.trim().isEmpty()) {
            return Result.success();
        }

        try {
            FirebaseFirestore firestore = FirebaseFirestore.getInstance();
            Query query = firestore.collection("personal_notifications")
                    .whereEqualTo("targetUserId", targetUid);

            // للضيف نضيف شرط targetUserType لتطابق قواعد الأمان في Firestore
            if (isGuest || FirebaseAuth.getInstance().getCurrentUser() == null) {
                query = query.whereEqualTo("targetUserType", "guests");
            }

            QuerySnapshot snapshot = Tasks.await(query.get());
            if (snapshot.isEmpty()) {
                return Result.success();
            }

            NotificationHelper notifHelper = NotificationHelper.get(context);
            long baseTs = System.currentTimeMillis();
            int idx = 0;

            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                String title = doc.getString("title");
                String message = doc.getString("message");
                String type = doc.getString("type");
                if (type == null || type.isEmpty()) type = "transaction_alert";
                String imageUrl = doc.getString("imageUrl");
                String webUrl = doc.getString("webUrl");

                if (title != null && !title.isEmpty() && message != null && !message.isEmpty()) {
                    notifHelper.showBackgroundNotification(
                            title, message, baseTs + (idx++), type, imageUrl, webUrl
                    );
                }

                // حذف المستند فور الاستيراد والعرض (مطابق لمنطق Cloud Function مع أجهزة GMS)
                try {
                    Tasks.await(doc.getReference().delete());
                    Log.d(TAG, "✅ تم استيراد وحذف إشعار مالي: " + doc.getId());
                } catch (Exception delErr) {
                    Log.e(TAG, "تعذر حذف المستند " + doc.getId(), delErr);
                }
            }

            return Result.success();
        } catch (Exception e) {
            if (e instanceof ExecutionException && e.getCause() instanceof FirebaseFirestoreException) {
                FirebaseFirestoreException ffe = (FirebaseFirestoreException) e.getCause();
                if (ffe.getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    Log.w(TAG, "تم تخطي فحص الإشعارات المالية لعدم توفر جلسة مصادقة حالياً.");
                    return Result.success();
                }
            }
            Log.e(TAG, "خطأ مؤقت في فحص الإشعارات المالية بدون GMS", e);
            return Result.retry();
        }
    }
}
