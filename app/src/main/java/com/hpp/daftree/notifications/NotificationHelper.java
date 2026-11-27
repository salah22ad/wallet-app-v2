package com.hpp.daftree.notifications;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.RequiresPermission;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

import com.hpp.daftree.MainActivity;
import com.hpp.daftree.R;
import com.hpp.daftree.debt.DebtSummaryActivity;
import com.hpp.daftree.ui.SubscriptionActivity;
import com.hpp.daftree.viewmodels.MainViewModel;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationHelper {

    public static final String CHANNEL_MAIN = "daftree_main";
    private static final String GROUP_DAFTREE = "com.hpp.daftree.NOTIFICATIONS";
    public static final String ACTION_REMIND_LATER = "com.hpp.daftree.ACTION_REMIND_LATER";
    private static final int SUMMARY_ID = 9999;

    private static NotificationHelper instance;
    private Context context;

    private NotificationHelper(Context context) {
        this.context = context.getApplicationContext();
        createChannels();
    }
    public static synchronized void init(Application app) {
        if (instance == null) {
            instance = new NotificationHelper(app);
        }
    }
    public static synchronized NotificationHelper get(Context context) {
        if (instance == null) {
            instance = new NotificationHelper(context);
        }
        return instance;
    }
    private void createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Uri soundUri = Uri.parse("android.resource://" + context.getPackageName() + "/raw/notification_sound");

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            // قناة الإشعارات العادية
            NotificationChannel mainChannel = new NotificationChannel(CHANNEL_MAIN,
                    context.getString(R.string.channel_general_name), NotificationManager.IMPORTANCE_HIGH);
            mainChannel.setDescription(context.getString(R.string.channel_general_desc));
            mainChannel.setSound(soundUri, audioAttributes);
            mainChannel.setShowBadge(true);
            mainChannel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            mainChannel.enableVibration(true);
            mainChannel.setVibrationPattern(new long[]{0, 500, 250, 500});
            mainChannel.enableLights(true);
            mainChannel.setLightColor(0xFF00FF00);

           if (manager != null) {
                manager.createNotificationChannel(mainChannel);
            }
            // قناة الإشعارات الترويجية
//            NotificationChannel promoChannel = new NotificationChannel(CHANNEL_PROMO,
//                    context.getString(R.string.channel_poromr_name), NotificationManager.IMPORTANCE_HIGH);
//            manager.createNotificationChannel(promoChannel);
        }
    }
    private void createChannels2() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Uri soundUri = Uri.parse("android.resource://" + context.getPackageName() + "/raw/notification_sound");

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_MAIN,
                    context.getString(R.string.channel_general_name), // تأكد أن هذه النصوص موجودة في strings.xml
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(context.getString(R.string.channel_general_desc));
            channel.setSound(soundUri, audioAttributes);
            channel.enableVibration(true);

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    // الطريقة الآمنة لإنشاء الـ Flags لمنع انهيار التطبيق
    private int getSafePendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    /**
     * الدالة الذكية للتوجيه مع بناء Back Stack (لمنع الخروج من التطبيق عند الرجوع)
     */
    private PendingIntent createSmartPendingIntent(String type, String webUrl) {
        Intent targetIntent;

        // إذا كان رابط ويب خارجي، لا نحتاج StackBuilder
        if ("web".equalsIgnoreCase(type) && webUrl != null && !webUrl.isEmpty()) {
            targetIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(webUrl));
            targetIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            return PendingIntent.getActivity(context, (int) System.currentTimeMillis(), targetIntent, getSafePendingIntentFlags());
        }

        // بناء الـ Intent الداخلي للشاشات
        if ("purchase".equalsIgnoreCase(type)) {
            type="SubscriptionActivity";
            targetIntent = new Intent(context, SubscriptionActivity.class);

        } else if ("transaction_alert".equalsIgnoreCase(type)) {
            type="DebtSummaryActivity";
            targetIntent = new Intent(context, DebtSummaryActivity.class);
        } else if ("home".equalsIgnoreCase(type) || type == null || type.isEmpty()) {
            targetIntent = new Intent(context, MainActivity.class);
        } else {
            // محاولة فتح الشاشة المخصصة عبر الـ Reflection
            try {
                Class<?> clazz = Class.forName("com.hpp.daftree.ui." + type);
                targetIntent = new Intent(context, clazz);
            } catch (ClassNotFoundException e1) {
                try {
                    Class<?> clazz = Class.forName("com.hpp.daftree.debt." + type);
                    targetIntent = new Intent(context, clazz);
                } catch (ClassNotFoundException e2) {
                    try {
                        Class<?> clazz = Class.forName("com.hpp.daftree." + type);
                        targetIntent = new Intent(context, clazz);
                    } catch (ClassNotFoundException e3) {
                        targetIntent = new Intent(context, MainActivity.class);
                    }
                }
            }
        }

        // استخدام TaskStackBuilder لضمان الرجوع للشاشة الرئيسية (MainActivity)
        android.app.TaskStackBuilder stackBuilder = android.app.TaskStackBuilder.create(context);

        // 1. إضافة الشاشة الرئيسية لتكون هي الأساس (في الخلف)
        stackBuilder.addNextIntent(new Intent(context, MainActivity.class));

        // 2. إذا كانت الشاشة المستهدفة ليست الرئيسية، نضعها فوق الرئيسية
        if (!targetIntent.getComponent().getClassName().equals(MainActivity.class.getName())) {
            targetIntent.putExtra("FROM_NOTIFICATION", true);
            stackBuilder.addNextIntent(targetIntent);
        }

        // إرجاع الـ PendingIntent المجهز بـ StackBuilder
        return stackBuilder.getPendingIntent((int) System.currentTimeMillis(), getSafePendingIntentFlags());
    }

    /**
     * توجيه المستخدم عند النقر على إشعار أندرويد العام (عندما يكون التطبيق مغلقاً في الخلفية)
     */
    public static boolean handleNotificationIntent(Context context, Intent intent) {
        if (context == null || intent == null || intent.getExtras() == null) return false;
        String type = intent.getStringExtra("type");
        String webUrl = intent.getStringExtra("webUrl");

        if ((type == null || type.isEmpty()) && (webUrl == null || webUrl.isEmpty())) {
            return false;
        }

        // مسح القيم حتى لا تتكرر عند تدوير الشاشة
        intent.removeExtra("type");
        intent.removeExtra("webUrl");

        try {
            if ("web".equalsIgnoreCase(type) && webUrl != null && !webUrl.isEmpty()) {
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(webUrl));
                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(webIntent);
                return true;
            }

            Intent targetIntent = null;
            if ("purchase".equalsIgnoreCase(type)) {
                targetIntent = new Intent(context, SubscriptionActivity.class);
            } else if ("transaction_alert".equalsIgnoreCase(type)) {
                targetIntent = new Intent(context, DebtSummaryActivity.class);
            } else if (type != null && !type.isEmpty() && !"home".equalsIgnoreCase(type)) {
                try {
                    targetIntent = new Intent(context, Class.forName("com.hpp.daftree.ui." + type));
                } catch (ClassNotFoundException e1) {
                    try {
                        targetIntent = new Intent(context, Class.forName("com.hpp.daftree.debt." + type));
                    } catch (ClassNotFoundException e2) {
                        try {
                            targetIntent = new Intent(context, Class.forName("com.hpp.daftree." + type));
                        } catch (ClassNotFoundException ignored) {
                            return false;
                        }
                    }
                }
            }

            if (targetIntent != null) {
                targetIntent.putExtra("FROM_NOTIFICATION", true);
                targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(targetIntent);
                return true;
            }
        } catch (Exception e) {
            Log.e("NotificationHelper", "Failed to route notification intent", e);
        }
        return false;
    }

    @SuppressLint("MissingPermission")
    public void showPromoNotification5(String title, String body, String imageUrl, String webUrl, String type, String btnText, int modelType) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        int notificationId = (int) System.currentTimeMillis();

        int layoutResId = (modelType == 2) ? R.layout.layout_notif_model2 : R.layout.layout_notif_model1;
        boolean besaidPic = (modelType == 2); // اختصار سطر if الذي كتبته أنت

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            Bitmap bitmap = null;
            if (imageUrl != null && !imageUrl.isEmpty()) {
                bitmap = getBitmapFromUrl(imageUrl, besaidPic);
            }

            RemoteViews expandedView = new RemoteViews(context.getPackageName(), layoutResId);

            expandedView.setTextViewText(R.id.tvNotifTitle, title);
            expandedView.setTextViewText(R.id.tvNotifBody, body);
            expandedView.setTextViewText(R.id.btnMainAction, btnText);

            if (bitmap != null) {
                expandedView.setImageViewBitmap(R.id.ivNotifImage, bitmap);
                expandedView.setViewVisibility(R.id.ivNotifImage, View.VISIBLE);

                if (modelType == 1) {
                    expandedView.setInt(R.id.tvNotifBody, "setMaxLines", 4); // 4 أسطر مع الصورة الكبيرة لحماية الأزرار
                } else {
                    expandedView.setInt(R.id.tvNotifBody, "setMaxLines", 6); // 6 أسطر مع الصورة الجانبية المربعة
                }
            } else {
                expandedView.setViewVisibility(R.id.ivNotifImage, View.GONE);
                expandedView.setInt(R.id.tvNotifBody, "setMaxLines", 7);
            }

            PendingIntent mainPendingIntent = createSmartPendingIntent(type, webUrl);
            expandedView.setOnClickPendingIntent(R.id.btnMainAction, mainPendingIntent);

            Intent remindIntent = new Intent(context, NotificationReceiver.class);
            remindIntent.setAction(ACTION_REMIND_LATER);
            remindIntent.putExtra("title", title);
            remindIntent.putExtra("body", body);
            remindIntent.putExtra("imageUrl", imageUrl);
            remindIntent.putExtra("webUrl", webUrl);
            remindIntent.putExtra("type", type);
            remindIntent.putExtra("btnText", btnText);
            remindIntent.putExtra("modelType", modelType);
            remindIntent.putExtra("notifId", notificationId);

            PendingIntent remindPendingIntent = PendingIntent.getBroadcast(context, notificationId, remindIntent, getSafePendingIntentFlags());
            expandedView.setOnClickPendingIntent(R.id.btnRemindLater, remindPendingIntent);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_MAIN)
                    .setSmallIcon(R.drawable.ic_stat_name)
                    .setCustomBigContentView(expandedView)
                    // تمت إزالة setCustomContentView ليظهر الإشعار المطوي بالشكل القياسي الآمن للنظام
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setAutoCancel(true)
                    .setGroup(GROUP_DAFTREE) // التجميع مع الإشعارات القديمة
                    .setContentIntent(mainPendingIntent);

            manager.notify(notificationId, builder.build());

            // استدعاء دالة التجميع
            showSummaryNotification(manager, bitmap);
        });
    }

    /**
     * دالة التجميع (Summary) المطابقة للمنطق القديم
     */
    private void showSummaryNotification(NotificationManager manager, Bitmap largeIcon) {
        NotificationCompat.Builder summaryBuilder = new NotificationCompat.Builder(context, CHANNEL_MAIN)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.new_notifications))
                .setSmallIcon(R.drawable.ic_stat_name)
                .setStyle(new NotificationCompat.InboxStyle()
                        .setSummaryText(context.getString(R.string.noti_summery))) // تأكد من وجود هذا النص في strings
                .setGroup(GROUP_DAFTREE)
                .setGroupSummary(true) // هذا هو السطر السحري الذي يجعلها حاوية
                .setAutoCancel(true);

        if (largeIcon != null) {
            summaryBuilder.setLargeIcon(largeIcon);
        }

        manager.notify(SUMMARY_ID, summaryBuilder.build());
    }

    /**
     * دالة جلب الصورة الذكية (تدعم القص المربع للنموذج 2)
     */
    private static Bitmap getBitmapFromUrl(String imageUrl, boolean besaidPic) {
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap originalBitmap = BitmapFactory.decodeStream(input);

            if (originalBitmap != null) {
                if (besaidPic) {
                    // إذا كان نموذج 2، نقص الصورة لتكون مربع مثالي 300x300
                    return scaleToSquare(originalBitmap, 300);
                } else {
                    // إذا كان نموذج 1، نقلصها لتجنب انهيار الذاكرة 600x300
                    return scaleBitmapForRemoteViews(originalBitmap, 600, 300);
                }
            }
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // دالة مساعدة لقص الصورة إلى مربع مثالي
    private static Bitmap scaleToSquare(Bitmap bitmap, int targetSize) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int minEdge = Math.min(width, height);

        // حساب نقطة البداية للقص من المنتصف
        int dx = (width - minEdge) / 2;
        int dy = (height - minEdge) / 2;

        // قص المربع
        Bitmap squared = Bitmap.createBitmap(bitmap, dx, dy, minEdge, minEdge);

        // إعادة التحجيم للحجم المطلوب
        return Bitmap.createScaledBitmap(squared, targetSize, targetSize, true);
    }

    // دالة التقليص للصور العرضية
    private static Bitmap scaleBitmapForRemoteViews(Bitmap bitmap, int maxWidth, int maxHeight) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= maxWidth && height <= maxHeight) return bitmap;

        float ratioBitmap = (float) width / (float) height;
        float ratioMax = (float) maxWidth / (float) maxHeight;

        int finalWidth = maxWidth;
        int finalHeight = maxHeight;
        if (ratioMax > ratioBitmap) {
            finalWidth = (int) ((float)maxHeight * ratioBitmap);
        } else {
            finalHeight = (int) ((float)maxWidth / ratioBitmap);
        }

        return Bitmap.createScaledBitmap(bitmap, finalWidth, finalHeight, true);
    }
    /**
     * الإشعارات الترويجية الاحترافية (Custom RemoteViews)
     */
  @SuppressLint("MissingPermission")
    public void showPromoNotification(String title, String body, String imageUrl, String webUrl, String type, String btnText, int modelType) {
        Runnable task = () -> {
            try {
                NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager == null) return;

                int notificationId = (int) (System.currentTimeMillis() & 0x7fffffff);
                Uri soundUri = Uri.parse("android.resource://" + context.getPackageName() + "/raw/notification_sound");
                Bitmap appIcon = getBitmapFromDrawable(context, R.mipmap.ic_launcher);

                Bitmap bitmap = null;
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    try {
                        bitmap = getBitmapFromUrl(imageUrl, false);
                    } catch (Exception imgErr) {
                        android.util.Log.e("NotificationHelper", "Failed to load promo image", imgErr);
                    }
                }

                String actionLabel = (btnText != null && !btnText.trim().isEmpty())
                        ? btnText
                        : context.getString(R.string.open_app);

                PendingIntent mainPendingIntent = createSmartPendingIntent(type, webUrl);

                Intent remindIntent = new Intent(context, NotificationReceiver.class);
                remindIntent.setAction(ACTION_REMIND_LATER);
                remindIntent.putExtra("title", title);
                remindIntent.putExtra("body", body);
                remindIntent.putExtra("imageUrl", imageUrl);
                remindIntent.putExtra("webUrl", webUrl);
                remindIntent.putExtra("type", type);
                remindIntent.putExtra("btnText", actionLabel);
                remindIntent.putExtra("modelType", modelType);
                remindIntent.putExtra("notifId", notificationId);

                PendingIntent remindPendingIntent = PendingIntent.getBroadcast(
                        context, notificationId, remindIntent, getSafePendingIntentFlags()
                );

                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_MAIN)
                        .setSmallIcon(R.drawable.ic_stat_name)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                        .setSound(soundUri)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .setAutoCancel(true)
                        .setContentIntent(mainPendingIntent)
                        .addAction(0, actionLabel, mainPendingIntent)
                        .addAction(0, context.getString(R.string.later), remindPendingIntent);

                if (bitmap != null) {
                    builder.setLargeIcon(bitmap);
                    builder.setStyle(new NotificationCompat.BigPictureStyle()
                            .bigPicture(bitmap)
                            .bigLargeIcon((Bitmap) null)
                            .setBigContentTitle(title)
                            .setSummaryText(body));
                } else {
                    if (appIcon != null) {
                        builder.setLargeIcon(appIcon);
                    }
                    builder.setStyle(new NotificationCompat.BigTextStyle()
                            .bigText(body)
                            .setBigContentTitle(title));
                }

                manager.notify(notificationId, builder.build());
            } catch (Exception e) {
                android.util.Log.e("NotificationHelper", "showPromoNotification error", e);
            }
        };

        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                try {
                    task.run();
                } finally {
                    executor.shutdown();
                }
            });
        } else {
            task.run();
        }
    }

    /**
     * الدالة الجديدة: الإشعارات الترويجية الاحترافية (Custom RemoteViews)
     */

    /**
     * الدالة القديمة (تم المحافظة عليها لإشعارات النظام والعمليات المالية)
     */
    public void showBackgroundNotification(String title, String body, long timestamp, String type, String imageUrl, String webUrl) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        int notificationId = (int) timestamp;

        PendingIntent pendingIntent = createSmartPendingIntent(type, webUrl);

        Uri soundUri = Uri.parse("android.resource://" + context.getPackageName() + "/raw/notification_sound");
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_MAIN)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSound(soundUri)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setGroup(GROUP_DAFTREE)
                .setContentIntent(pendingIntent);

        if (imageUrl != null && !imageUrl.isEmpty()) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                Bitmap bitmap = getBitmapFromUrl(imageUrl,false);
                if (bitmap != null) {
                    builder.setLargeIcon(bitmap);
                    builder.setStyle(new NotificationCompat.BigPictureStyle()
                            .bigPicture(bitmap)
                            .bigLargeIcon((Bitmap) null)); // <--- تم التعديل هنا
                }
                manager.notify(notificationId, builder.build());
            });
        } else {
            manager.notify(notificationId, builder.build());
        }
    }

    /**
     * حاوية الإشعارات (Summary Notification)
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private void showSummaryNotification(Bitmap largeIcon) {
        if (largeIcon == null) {
            largeIcon = getBitmapFromDrawable(context, R.mipmap.ic_launcher);
        }

        PendingIntent summaryIntent = getMainPendingIntent(null, null);

        NotificationCompat.Builder summaryBuilder = new NotificationCompat.Builder(context, CHANNEL_MAIN)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setLargeIcon(largeIcon)
                .setStyle(new NotificationCompat.InboxStyle()
                        .setSummaryText(context.getString(R.string.app_name))
                        .setBigContentTitle(context.getString(R.string.new_notifications))) // تأكد من وجود النص في strings.xml
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setGroup(GROUP_DAFTREE)
                .setGroupSummary(true)
                .setContentIntent(summaryIntent)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setAutoCancel(true);
        // .setTimeoutAfter(TIMEOUT_MS); // <--- الحل لمشكلة الحاوية الفارغة: الملخص أيضاً يملك مؤقتاً

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(SUMMARY_ID, summaryBuilder.build());
    }
    // الدالة المحدثة لتحميل الصور

    // دالة جديدة لتقليص الأبعاد بما يتناسب مع RemoteViews بأمان تام
    private static Bitmap scaleBitmapForRemoteViews(Bitmap bitmap, boolean besaidPic) {
        // أبعاد آمنة جداً للإشعارات تمنع خطأ TransactionTooLargeException
        int MAX_WIDTH = 600;
        if (besaidPic) MAX_WIDTH=300;
        int MAX_HEIGHT = 300;

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // إذا كانت الصورة أصغر من الحد الأقصى، نعيدها كما هي
        if (width <= MAX_WIDTH && height <= MAX_HEIGHT) {
            return bitmap;
        }

        // حساب النسبة للحفاظ على جودة وأبعاد الصورة (Aspect Ratio)
        float ratioBitmap = (float) width / (float) height;
        float ratioMax = (float) MAX_WIDTH / (float) MAX_HEIGHT;

        int finalWidth = MAX_WIDTH;
        int finalHeight = MAX_HEIGHT;
        if (ratioMax > ratioBitmap) {
            finalWidth = (int) ((float)MAX_HEIGHT * ratioBitmap);
        } else {
            finalHeight = (int) ((float)MAX_WIDTH / ratioBitmap);
        }

        return Bitmap.createScaledBitmap(bitmap, finalWidth, finalHeight, true);
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    public void showLocalNotification(String title, String message, int id, boolean important) {
        // استبدال application بـ context
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Bitmap largeIcon = getBitmapFromDrawable(context, R.mipmap.ic_launcher);

        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle()
                .bigText(message)
                .setBigContentTitle(title)
                .setSummaryText(context.getString(R.string.noti_summery)); // استبدال application بـ context

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_MAIN)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setLargeIcon(largeIcon)
                .setStyle(bigTextStyle)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setGroup(GROUP_DAFTREE) // تأكد من اسم المتغير في الكلاس لديك (قد يكون GROUP_DAFTREE)
                .setContentIntent(getMainPendingIntent("notif_click", message))
                .setFullScreenIntent(getMainPendingIntent(null, null), important);
        //  .setTimeoutAfter(TIMEOUT_MS);

        // استبدال application بـ context
        NotificationManagerCompat.from(context).notify(id, builder.build());

//        showSummaryNotification(manager); // تأكد من تمرير الـ manager أو تعديلها حسب دالتك

        refreshMainViewModel();
    }
    private PendingIntent getMainPendingIntent(String extraKey, String extraValue) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (extraKey != null) intent.putExtra(extraKey, extraValue);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT |
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0);
        return PendingIntent.getActivity(context, (int) System.currentTimeMillis(), intent, flags);
    }

    private void refreshMainViewModel() {
        try {
            // استبدال application بـ context
            if (context instanceof ViewModelStoreOwner) {
                MainViewModel vm = new ViewModelProvider(
                        (ViewModelStoreOwner) context).get(MainViewModel.class);
                vm.refreshData();
            } else {
                Intent intent = new Intent(context, MainActivity.class);
                intent.setAction("REFRESH_UI");

                // --- التعديل السحري لمنع الانهيارات (Crashes) ---
                // تم استبدال FLAG_MUTABLE بالطريقة الآمنة التي تعتمد على FLAG_IMMUTABLE
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    flags |= PendingIntent.FLAG_IMMUTABLE;
                }

                PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, flags);

                // ملاحظة برمجية: لكي يعمل الـ PendingIntent الذي تم إنشاؤه في الـ else يجب إرساله
                pendingIntent.send();
            }
        } catch (Exception e) {
            Log.e("NotificationHelper", "Failed to refresh UI", e);
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
    private Bitmap getBitmapFromUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return null;
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            return BitmapFactory.decodeStream(input);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}