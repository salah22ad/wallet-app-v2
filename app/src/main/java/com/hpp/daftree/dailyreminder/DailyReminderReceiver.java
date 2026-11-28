package com.hpp.daftree.dailyreminder;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import com.hpp.daftree.MainActivity;

public class DailyReminderReceiver extends BroadcastReceiver {
    private static final String TAG = "DailyReminderReceiver";
    private static final String ALARM_ACTION = "DAFTREE_DAILY_REMINDER_ACTION";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Action received: " + action);

        // --- التعامل مع أزرار الإشعار ---

        // 1. حالة الإلغاء (زر "لاحقاً" أو السحب Swipe)
        if (DailyReminderNotification.ACTION_DISMISS.equals(action)) {
            // أوقف الصوت
            DailyReminderNotification.stopSound();
            // امسح الإشعار من الشريط
            cancelNotification(context);
            return;
        }

        // 2. حالة الفتح (زر "فتح التطبيق" أو الضغط على الإشعار)
        if (DailyReminderNotification.ACTION_OPEN.equals(action)) {
            // أوقف الصوت
            DailyReminderNotification.stopSound();
            // امسح الإشعار
            cancelNotification(context);

            // افتح التطبيق
            Intent appIntent = new Intent(context, MainActivity.class);
            appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(appIntent);
            return;
        }
        // 1. التعامل مع إعادة التشغيل (Reboot) - هذا هو الجزء الذي يضمن العمل بعد إطفاء الهاتف
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(action) ||
                "android.intent.action.LOCKED_BOOT_COMPLETED".equals(action) || // لأندرويد 7+
                Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) { // عند تحديث التطبيق من المتجر

            Log.d(TAG, "إعادة تشغيل الهاتف أو تحديث التطبيق.. جاري إعادة الجدولة.");
            DailyReminderScheduler.scheduleDailyReminder(context);
            return;
        }

        // 2. التعامل مع وقت التنبيه الفعلي
        if (ALARM_ACTION.equals(action)) {
            DailyReminderManager reminderManager = DailyReminderManager.getInstance(context);
            if (!reminderManager.areGeneralNotificationsEnabled() || !reminderManager.isEnabled()) {
                Log.d(TAG, "التذكير اليومي معطّل من الإعدادات، تم التخطي.");
                return;
            }
            // أ) الخطوة الأولى والأهم: جدولة موعد الغد فوراً وتسجيل العرض!
            DailyReminderScheduler.scheduleDailyReminder(context);
            reminderManager.markNotificationShown();

            // ب) تشغيل الـ MediaPlayer وعرض الإشعار (كما طلبت)
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Daftree:MediaWakeLock");

            try {
                // نمسك الـ WakeLock لمدة أقصاها دقيقة واحدة لضمان عمل الـ MediaPlayer
                wl.acquire(60 * 1000L);

                // إيقاظ الشاشة 8 ثوانٍ لإظهار الإشعار على شاشة القفل (يتحرر تلقائياً بعد 8 ثوانٍ)
                try {
                    @SuppressWarnings("deprecation")
                    PowerManager.WakeLock screenWl = pm.newWakeLock(
                            PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                                    | PowerManager.ACQUIRE_CAUSES_WAKEUP
                                    | PowerManager.ON_AFTER_RELEASE,
                            "Daftree:ReminderScreenWakeLock"
                    );
                    screenWl.acquire(8 * 1000L);
                } catch (Exception ignored) {}

                DailyReminderNotification notificationHandler = new DailyReminderNotification(context);
                notificationHandler.playCustomSound(); // تشغيل الـ MediaPlayer الخاص بك
                notificationHandler.showNotification(); // عرض الإشعار

            } catch (Exception e) {
                Log.e(TAG, "خطأ أثناء تشغيل التنبيه", e);
            } finally {
                if (wl.isHeld()) wl.release();
            }
        }
    }
    // دالة مساعدة لمسح الإشعار
    private void cancelNotification(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(DailyReminderNotification.NOTIFICATION_ID);
        }
    }
}