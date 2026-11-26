package com.hpp.daftree.notifications;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.hpp.daftree.utils.SecureLicenseManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class NonGmsNotificationScheduler {

    private static final String TAG = "NonGmsNotifScheduler";
    public static final String BROADCAST_WORK_NAME = "NON_GMS_BROADCAST_WORK";
    public static final String DEBT_WORK_NAME = "NON_GMS_DEBT_WORK";

    private static final String PREFS_NAME = "non_gms_notif_prefs";
    private static final String KEY_SHOWN_IDS = "shown_notif_ids";
    private static final String KEY_LAST_BROADCAST_CHECK = "last_broadcast_check_ms";
    private static final long TWELVE_HOURS_MS = 12L * 60 * 60 * 1000;
    private static final int MAX_TRACKED_IDS = 50;

    /**
     * فحص محلي بالكامل لتوفر خدمات جوجل (لا يتطلب إنترنت)
     */
    public static boolean isGmsAvailable(Context context) {
        try {
            int status = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);
            return status == ConnectionResult.SUCCESS;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * تُستدعى عند فتح MainActivity:
     * - على أجهزة GMS: تلغي أي وركر غير لازم وتخرج فوراً.
     * - على أجهزة بدون GMS: تشغّل مسار البث (كل 12 ساعة) ومسار الديون (كل ساعة).
     */
    public static void initAndScheduleIfNeeded(Context context) {
        Context appCtx = context.getApplicationContext();
        WorkManager wm = WorkManager.getInstance(appCtx);

        if (isGmsAvailable(appCtx)) {
            wm.cancelUniqueWork(BROADCAST_WORK_NAME);
            wm.cancelUniqueWork(DEBT_WORK_NAME);
            return;
        }

        Log.d(TAG, "GMS غير متوفر — تفعيل منظومة WorkManager للإشعارات.");

        // 1) مسار الإشعارات العامة والمدراء + احتياط التذكير اليومي
        // المدراء: فحص فوري عند كل فتح للتطبيق | المستخدمون العاديون: مرة كل 12 ساعة
        boolean isAdmin = SecureLicenseManager.getInstance(appCtx).isUserAdmin();
        SharedPreferences prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastCheck = prefs.getLong(KEY_LAST_BROADCAST_CHECK, 0L);
        long elapsed = System.currentTimeMillis() - lastCheck;

        long initialDelayMs = (isAdmin || lastCheck == 0L || elapsed >= TWELVE_HOURS_MS)
                ? 0L
                : (TWELVE_HOURS_MS - elapsed);

        ExistingWorkPolicy broadcastPolicy = isAdmin
                ? ExistingWorkPolicy.REPLACE
                : ExistingWorkPolicy.KEEP;

        Constraints connectedConstraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest broadcastReq = new OneTimeWorkRequest.Builder(NonGmsBroadcastWorker.class)
                .setConstraints(connectedConstraints)
                .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build();

        wm.enqueueUniqueWork(BROADCAST_WORK_NAME, broadcastPolicy, broadcastReq);

        // 2) مسار الإشعارات المالية والديون (فحص كل ساعة عند توفر الإنترنت)
        PeriodicWorkRequest debtReq = new PeriodicWorkRequest.Builder(
                NonGmsDebtNotifWorker.class, 1, TimeUnit.HOURS)
                .setConstraints(connectedConstraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build();

        wm.enqueueUniquePeriodicWork(DEBT_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, debtReq);
    }

    /**
     * بعد اكتمال فحص البث بنجاح، يتم حفظ الوقت وجدولة الدورة التالية بعد 12 ساعة ثم يتوقف الوركر
     */
    public static void scheduleNextBroadcastIn12Hours(Context context) {
        Context appCtx = context.getApplicationContext();
        if (isGmsAvailable(appCtx)) return;

        SharedPreferences prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_LAST_BROADCAST_CHECK, System.currentTimeMillis()).apply();

        Constraints connectedConstraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest nextReq = new OneTimeWorkRequest.Builder(NonGmsBroadcastWorker.class)
                .setConstraints(connectedConstraints)
                .setInitialDelay(12, TimeUnit.HOURS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build();

        WorkManager.getInstance(appCtx).enqueueUniqueWork(
                BROADCAST_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                nextReq
        );
        Log.d(TAG, "تمت جدولة دورة إشعارات البث القادمة بعد 12 ساعة.");
    }

    public static synchronized boolean isNotifAlreadyShown(Context context, String notifId) {
        if (TextUtils.isEmpty(notifId)) return false;
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_SHOWN_IDS, "");
        if (raw.isEmpty()) return false;
        return Arrays.asList(raw.split(",")).contains(notifId);
    }

    public static synchronized void markNotifShown(Context context, String notifId) {
        if (TextUtils.isEmpty(notifId)) return;
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_SHOWN_IDS, "");
        List<String> ids = new ArrayList<>();
        if (!raw.isEmpty()) {
            for (String item : raw.split(",")) {
                if (!item.isEmpty() && !ids.contains(item)) {
                    ids.add(item);
                }
            }
        }
        if (!ids.contains(notifId)) {
            ids.add(notifId);
            while (ids.size() > MAX_TRACKED_IDS) {
                ids.remove(0);
            }
            prefs.edit().putString(KEY_SHOWN_IDS, TextUtils.join(",", ids)).apply();
        }
    }

    public static String resolveAppLanguage(Context context) {
        SharedPreferences appPrefs = context.getApplicationContext()
                .getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        String rawLang = appPrefs.getString("language", Locale.getDefault().getLanguage());
        if (rawLang == null || rawLang.trim().isEmpty()) {
            rawLang = Locale.getDefault().getLanguage();
        }
        String lang = rawLang.trim().toLowerCase(Locale.US);
        if (lang.contains("_")) lang = lang.split("_")[0];
        if (lang.contains("-")) lang = lang.split("-")[0];

        if ("in".equals(lang)) lang = "id";
        else if ("he".equals(lang)) lang = "iw";
        else if ("nb".equals(lang) || "nn".equals(lang)) lang = "no";

        return lang;
    }
}
