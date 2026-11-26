package com.hpp.daftree.notifications;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.hpp.daftree.R;
import com.hpp.daftree.dailyreminder.DailyReminderManager;
import com.hpp.daftree.dailyreminder.DailyReminderNotification;
import com.hpp.daftree.dailyreminder.DailyReminderScheduler;
import com.hpp.daftree.utils.SecureLicenseManager;

import java.util.Calendar;
import java.util.Map;

public class NonGmsBroadcastWorker extends Worker {

    private static final String TAG = "NonGmsBroadcastWorker";
    private static final long RETENTION_WINDOW_MS = 48L * 60 * 60 * 1000;

    public NonGmsBroadcastWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        if (NonGmsNotificationScheduler.isGmsAvailable(context)) {
            return Result.success();
        }

        try {
            // 1) فحص التذكير اليومي (8:30 مساءً) كطبقة احتياطية للأجهزة بدون GMS
            checkDailyReminderFallback(context);

            DailyReminderManager reminderManager = DailyReminderManager.getInstance(context);
            if (!reminderManager.areGeneralNotificationsEnabled()) {
                NonGmsNotificationScheduler.scheduleNextBroadcastIn12Hours(context);
                return Result.success();
            }

            // 2) جلب مستندات latest_notifications الحديثة (فهرس مفرد على createdAt)
            long cutoffMs = System.currentTimeMillis() - RETENTION_WINDOW_MS;
            FirebaseFirestore firestore = FirebaseFirestore.getInstance();
            QuerySnapshot snapshot = Tasks.await(
                    firestore.collection("latest_notifications")
                            .whereGreaterThan("createdAt", cutoffMs)
                            .get()
            );

            boolean isAdmin = SecureLicenseManager.getInstance(context).isUserAdmin();
            String appLang = NonGmsNotificationScheduler.resolveAppLanguage(context);
            NotificationHelper notifHelper = NotificationHelper.get(context);

            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                String notifId = doc.getString("notifId");
                if (notifId == null || notifId.isEmpty()) notifId = doc.getId();

                if (NonGmsNotificationScheduler.isNotifAlreadyShown(context, notifId)) {
                    continue;
                }

                String audience = doc.getString("audience");
                if ("admins".equalsIgnoreCase(audience) && !isAdmin) {
                    continue;
                }

                String title = null;
                String message = null;
                String btnText = null;

                Object transObj = doc.get("translations");
                if (transObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> translations = (Map<String, Object>) transObj;
                    Map<String, Object> chosen = extractLangMap(translations, appLang);
                    if (chosen == null) chosen = extractLangMap(translations, "en");
                    if (chosen == null) chosen = extractLangMap(translations, "ar");

                    if (chosen != null) {
                        title = asString(chosen.get("title"));
                        message = asString(chosen.get("message"));
                        btnText = asString(chosen.get("btnText"));
                    }
                }

                if (title == null || title.isEmpty() || message == null || message.isEmpty()) {
                    NonGmsNotificationScheduler.markNotifShown(context, notifId);
                    continue;
                }

                String type = doc.getString("type");
                if (type == null || type.isEmpty()) type = "home";
                String imageUrl = doc.getString("imageUrl");
                String webUrl = doc.getString("webUrl");
                String modelTypeStr = doc.getString("modelType");
                Boolean isPromo = doc.getBoolean("isPromo");

                if (Boolean.TRUE.equals(isPromo) || (modelTypeStr != null && !modelTypeStr.isEmpty())) {
                    int modelType = 1;
                    try {
                        if (modelTypeStr != null && !modelTypeStr.isEmpty()) {
                            modelType = Integer.parseInt(modelTypeStr);
                        }
                    } catch (Exception ignored) {}

                    if (btnText == null || btnText.isEmpty()) {
                        btnText = context.getString(R.string.open_app);
                    }
                    notifHelper.showPromoNotification(title, message, imageUrl, webUrl, type, btnText, modelType);
                } else {
                    notifHelper.showBackgroundNotification(
                            title, message, System.currentTimeMillis(), type, imageUrl, webUrl
                    );
                }

                NonGmsNotificationScheduler.markNotifShown(context, notifId);
                Log.d(TAG, "✅ تم عرض إشعار البث بدون GMS: " + notifId + " (" + appLang + ")");
            }

            // جدولة الدورة التالية بعد 12 ساعة والتوقف
            NonGmsNotificationScheduler.scheduleNextBroadcastIn12Hours(context);
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "فشل جلب إشعارات البث بدون GMS، سيتم إعادة المحاولة عند توفر الاتصال", e);
            return Result.retry();
        }
    }

    private void checkDailyReminderFallback(Context context) {
        try {
            DailyReminderManager dm = DailyReminderManager.getInstance(context);
            if (!dm.areGeneralNotificationsEnabled() || !dm.isEnabled()) return;

            Calendar now = Calendar.getInstance();
            int hour = now.get(Calendar.HOUR_OF_DAY);
            int minute = now.get(Calendar.MINUTE);
            boolean pastReminderTime = (hour > 20) || (hour == 20 && minute >= 30);

            if (pastReminderTime && dm.shouldShowNotification()) {
                Log.d(TAG, "تشغيل التذكير اليومي الاحتياطي عبر WorkManager (بدون GMS).");
                DailyReminderNotification notif = new DailyReminderNotification(context);
                notif.playCustomSound();
                notif.showNotification();
                dm.markNotificationShown();
                DailyReminderScheduler.scheduleDailyReminder(context);
            }
        } catch (Exception e) {
            Log.e(TAG, "خطأ في احتياط التذكير اليومي", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractLangMap(Map<String, Object> translations, String key) {
        Object val = translations.get(key);
        return (val instanceof Map) ? (Map<String, Object>) val : null;
    }

    private String asString(Object obj) {
        return obj != null ? String.valueOf(obj) : "";
    }
}
