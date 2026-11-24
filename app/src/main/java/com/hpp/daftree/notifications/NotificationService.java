package com.hpp.daftree.notifications;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.RequiresPermission;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.hpp.daftree.R;
import com.hpp.daftree.MainActivity;

public class NotificationService  {
    private static final String CHANNEL_ID = NotificationHelper.CHANNEL_MAIN;
    private static final int NOTIFICATION_ID = 1001;

    private final Context context;
    private final NotificationManagerCompat notificationManager;

    public NotificationService(Context context) {
        this.context = context;
        this.notificationManager = NotificationManagerCompat.from(context);
        // توحيد إنشاء القناة عبر NotificationHelper كمصدر وحيد لمنع تضارب الصوت والإعدادات
        NotificationHelper.get(context);
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    public void showRewardNotification(String title, String message) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        // إنشاء نمط الإشعار الممتد
        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle()
                .bigText(message)
                .setBigContentTitle(title)
                .setSummaryText(title);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(bigTextStyle) // إضافة النمط الممتد
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pendingIntent, true);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    public void showGeneralNotification(String title, String message, Intent intent1) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        // إنشاء نمط الإشعار الممتد
        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle()
                .bigText(message)
                .setBigContentTitle(title)
                .setSummaryText(context.getString(R.string.finance_noti));

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(bigTextStyle) // إضافة النمط الممتد
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pendingIntent, true);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }

    // دالة جديدة للإشعار اليومي
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    public void showDailyReminderNotification(boolean isGuest) {
        String title = context.getString(R.string.daily_noti_title);
        String message;

        if (isGuest) {
            message = context.getString(R.string.guest_daily_noti_message);
        } else {
            message = context.getString(R.string.daily_noti_message);
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        // نمط إشعار ممتد للإشعار اليومي
        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle()
                .bigText(message)
                .setBigContentTitle(title)
                .setSummaryText(context.getString(R.string.daily_remainder));

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(bigTextStyle)
                .setPriority(NotificationCompat.PRIORITY_MAX) // أولوية قصوى
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pendingIntent, true)
                .setOngoing(false) // ليس مستمراً
                .setTimeoutAfter(60000); // يختفي بعد دقيقة

        notificationManager.notify(2001, builder.build());
    }
}