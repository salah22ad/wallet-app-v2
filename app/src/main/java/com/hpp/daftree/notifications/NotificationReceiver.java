package com.hpp.daftree.notifications;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

public class NotificationReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (NotificationHelper.ACTION_REMIND_LATER.equals(intent.getAction())) {

            // 1. إغلاق الإشعار الحالي
            int notifId = intent.getIntExtra("notifId", 1001);
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            manager.cancel(notifId);

            // عرض رسالة للمستخدم
            Toast.makeText(context, "سيتم تذكيرك بعد ساعتين", Toast.LENGTH_SHORT).show();

            // 2. جدولة الإشعار ليظهر بعد ساعتين
            scheduleReminder(context, intent);
        }
    }

    private void scheduleReminder(Context context, Intent originalIntent) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // استخراج البيانات لمررها للمنبه
        Intent reminderIntent = new Intent(context, ReminderExecuteReceiver.class);
        reminderIntent.putExtras(originalIntent.getExtras());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 2002,
                reminderIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // حساب الوقت: بعد ساعتين من الآن (7200000 ملي ثانية)
        // للتجربة الآن، يمكنك تغييرها إلى 10 ثواني: 10 * 1000
        long triggerAtMillis = System.currentTimeMillis() + (2 * 60 * 60 * 1000);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        }
    }
}