package com.hpp.daftree.dailyreminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;

public class DailyReminderScheduler {
    private static final String TAG = "DailyReminderScheduler";
    private static final int ALARM_REQUEST_CODE = 1001;

    public static void scheduleDailyReminder(Context context) {
        // إذا كان المستخدم قد عطل التنبيهات من الإعدادات، لا نفعل شيئاً
        if (!DailyReminderManager.getInstance(context).isEnabled()) {
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Missing SCHEDULE_EXACT_ALARM permission, trying anyway.");
            }
        }
        // حساب الوقت: 8:30 مساءً
        Calendar calendar = Calendar.getInstance();
        long currentTime = System.currentTimeMillis();

        calendar.set(Calendar.HOUR_OF_DAY, 20); // الساعة 20 (8 مساءً)
        calendar.set(Calendar.MINUTE, 30);      // الدقيقة 30
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        // إذا كان الوقت قد مضى (مثلاً الساعة الآن 9 مساءً)، نجدول لليوم التالي
        if (calendar.getTimeInMillis() <= currentTime) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        Intent intent = new Intent(context, DailyReminderReceiver.class);
        intent.setAction("DAFTREE_DAILY_REMINDER_ACTION");

        // FLAG_IMMUTABLE ضروري في أندرويد 12+
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                flags
        );

        // التغيير الجوهري هنا: استخدام setAlarmClock
        try {
            if (alarmManager != null) {
                // AlarmClockInfo يضمن إيقاظ الجهاز من النوم العميق (Doze Mode)
                // هذا هو الحل لمشكلة "عدم فتح التطبيق لأكثر من يوم"
                AlarmManager.AlarmClockInfo alarmClockInfo = new AlarmManager.AlarmClockInfo(
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent);

                Log.d(TAG, "تمت جدولة التنبيه القادم في: " + calendar.getTime().toString());

                // حفظ الحالة للعرض في الواجهة
                DailyReminderManager.getInstance(context).setScheduled(true);
            }
        } catch (SecurityException e) {
            // لن يحدث كراش إذا سحب النظام الصلاحية فجأة، سيتم فقط تسجيل الخطأ
            Log.e(TAG, "فشل في الجدولة بسبب نقص الصلاحيات", e);
        }
    }
    /**
     * إلغاء الجدولة الحالية
     */
    public static void cancelScheduledReminder(Context context) {
        try {
            Intent intent = new Intent(context, DailyReminderReceiver.class);
            intent.setAction("DAFTREE_DAILY_REMINDER_ACTION");

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags);
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

            if (alarmManager != null && pendingIntent != null) {
                alarmManager.cancel(pendingIntent);
                pendingIntent.cancel();
            }
            Log.d(TAG, "تم إلغاء الجدولة السابقة");
        } catch (Exception e) {
            Log.e(TAG, "خطأ أثناء إلغاء الجدولة", e);
        }
    }
    /**
     * الحصول على الوقت المتبقي للإشعار التالي
     */
    public static long getNextReminderTime(Context context) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.HOUR_OF_DAY, 20);
        calendar.set(Calendar.MINUTE, 30);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        long nextTime = calendar.getTimeInMillis();

        // إذا كان الوقت الحالي بعد 8:30، نضيف يوم
        if (nextTime <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
            nextTime = calendar.getTimeInMillis();
        }

        return nextTime - System.currentTimeMillis();
    }
}