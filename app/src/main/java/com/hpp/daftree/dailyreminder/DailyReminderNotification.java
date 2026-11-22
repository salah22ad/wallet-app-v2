package com.hpp.daftree.dailyreminder;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.hpp.daftree.MainActivity;
import com.hpp.daftree.R;
import androidx.core.content.ContextCompat;

/**
 * مدير عرض الإشعارات والصوت
 * المسؤولية: عرض الإشعار وتشغيل الصوت بقوة حتى في الوضع الصامت.
 */
@SuppressWarnings("deprecation")
public class DailyReminderNotification {
    private static final String CHANNEL_ID = "daftree_daily_reminder_channel_v2"; // تغيير الـ ID لضمان تحديث الإعدادات
    static final int NOTIFICATION_ID = 2001;
    private static final String TAG = "DailyReminderNotif";
    private final Context context;

    // متغير ثابت لحفظ مشغل الصوت لمنع جمعه من الـ Garbage Collector أثناء التشغيل
    private static MediaPlayer mediaPlayer;
    public static final String ACTION_DISMISS = "com.hpp.daftree.ACTION_DISMISS";
    public static final String ACTION_OPEN = "com.hpp.daftree.ACTION_OPEN";
    public DailyReminderNotification(Context context) {
        this.context = context;
        createNotificationChannel();
    }
    // دالة ستاتيك لإيقاف الصوت من أي مكان (نحتاجها في الـ Receiver)
    public static void stopSound() {
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private Bitmap getBitmapFromDrawable(Context context, int drawableId) {
        Drawable drawable = ContextCompat.getDrawable(context, drawableId);
        if (drawable == null) return null;
        if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
            return ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap();
        }
        try {
            Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    /**
     * تشغيل الصوت المخصص بقوة (يخترق الوضع الصامت)
     */
    public void playCustomSound() {
        stopSound();
        try {
            // إيقاف أي صوت سابق
            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }


            Uri soundUri = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.my_wallet_tone);

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(context, soundUri);

            // الإعداد السحري للصوت في الوضع الصامت
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM) // التعامل معه كمنبه وليس كإشعار
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
            } else {
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            }

            mediaPlayer.prepare();
            mediaPlayer.start();

            // إيقاف الصوت تلقائياً بعد اكتماله لتحرير الموارد
            mediaPlayer.setOnCompletionListener(mp -> {
                mp.release();
                mediaPlayer = null;
            });

        } catch (Exception e) {
            Log.e(TAG, "فشل تشغيل صوت التنبيه", e);
        }
    }

    /**
     * عرض الإشعار المرئي
     */
    public void showNotification() {
        String title = context.getString(R.string.daily_reminder_title);
        String message = context.getString(R.string.daily_reminder_message);
        String bigText = context.getString(R.string.daily_reminder_big_text);

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Intent openBroadcastIntent = new Intent(context, DailyReminderReceiver.class);
        openBroadcastIntent.setAction(ACTION_OPEN);
        PendingIntent openPendingIntent = PendingIntent.getBroadcast(
                context, 2, openBroadcastIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        // 2. إعداد زر "إلغاء" -> يرسل برودكاست
        Intent dismissBroadcastIntent = new Intent(context, DailyReminderReceiver.class);
        dismissBroadcastIntent.setAction(ACTION_DISMISS);
        PendingIntent dismissPendingIntent = PendingIntent.getBroadcast(
                context, 3, dismissBroadcastIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        Bitmap largeIcon = getBitmapFromDrawable(context, R.mipmap.ic_launcher);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, pendingFlags);

        @SuppressLint("NotificationTrampoline") NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setLargeIcon(largeIcon)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(bigText))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setVibrate(new long[]{0, 1000, 500, 1000, 500, 1000, 500, 1000})
                .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
                .setTicker(message)
                .setOnlyAlertOnce(false)
                .setContentIntent(openPendingIntent)// السحب للإلغاء (Swipe) يوقف الصوت أيضاً
                .setDeleteIntent(dismissPendingIntent)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true) // لنجبره يضغط على الأزرار

                // إضافة الأزرار
                .addAction(R.drawable.ic_stat_name, context.getString(R.string.open_app), openPendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.later), dismissPendingIntent);

        // لا نضع setSound هنا لأننا شغلناه يدوياً بالأعلى

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        // التحقق من صلاحية الإشعارات (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "لا توجد صلاحية لعرض الإشعار");
                return;
            }
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    /**
     * إجراء إلغاء الإشعار
     */
    private NotificationCompat.Action getDismissAction() {
        Intent dismissIntent = new Intent(context, DailyReminderReceiver.class);
        dismissIntent.setAction("DISMISS_NOTIFICATION");
        PendingIntent dismissPendingIntent = PendingIntent.getBroadcast(
                context, 0, dismissIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Action.Builder(
                R.drawable.ic_close_white,
                context.getString(R.string.dismiss),
                dismissPendingIntent
        ).build();
    }

    /**
     * إجراء فتح التطبيق
     */
    private NotificationCompat.Action getOpenAppAction() {
        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                context, 1, openIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Action.Builder(
                R.drawable.ic_add,
                context.getString(R.string.open_app),
                openPendingIntent
        ).build();
    }
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = context.getString(R.string.daily_reminder_channel_name);
            String description = context.getString(R.string.daily_reminder_channel_desc);
            int importance = NotificationManager.IMPORTANCE_HIGH;

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            // إلغاء صوت القناة الافتراضي لأننا نتحكم به يدوياً
            channel.setSound(null, null);
            channel.enableVibration(true);

            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}