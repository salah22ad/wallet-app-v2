package com.hpp.daftree.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReminderExecuteReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && intent.getExtras() != null) {

            // 1. استخراج كافة البيانات التي تم تمريرها من الإشعار الأصلي
            String title = intent.getStringExtra("title");
            String body = intent.getStringExtra("body");
            String imageUrl = intent.getStringExtra("imageUrl");
            String webUrl = intent.getStringExtra("webUrl");
            String type = intent.getStringExtra("type");
            String btnText = intent.getStringExtra("btnText");
            int modelType = intent.getIntExtra("modelType", 1); // 1 هو النمط الافتراضي

            // 2. إعادة إرسال الإشعار الترويجي للمستخدم
            if (title != null) {
                // نستخدم نفس الدالة الذكية التي صممناها في NotificationHelper
                NotificationHelper.get(context).showPromoNotification(
                        title,
                        body,
                        imageUrl,
                        webUrl,
                        type,
                        btnText,
                        modelType
                );
            }
        }
    }
}