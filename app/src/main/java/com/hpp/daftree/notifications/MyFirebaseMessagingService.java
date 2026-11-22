package com.hpp.daftree.notifications;

import android.util.Log;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.hpp.daftree.R;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {

        if (remoteMessage.getData().size() > 0) {
            String title = remoteMessage.getData().get("title");
            String body = remoteMessage.getData().get("message");
            String type = remoteMessage.getData().get("type");
            String imageUrl = remoteMessage.getData().get("imageUrl");
            String webUrl = remoteMessage.getData().get("webUrl");

            // قراءة المتغيرات الجديدة
            String modelTypeStr = remoteMessage.getData().get("modelType");
            String btnText = remoteMessage.getData().get("btnText");

            if (title == null && remoteMessage.getNotification() != null) {
                title = remoteMessage.getNotification().getTitle();
                body = remoteMessage.getNotification().getBody();
            }

            if (title != null) {
                // التوجيه: هل هو إشعار ترويجي جديد أم إشعار عادي/مالي؟
                if (modelTypeStr != null && !modelTypeStr.isEmpty()) {
                    int modelType = 1;
                    try { modelType = Integer.parseInt(modelTypeStr); } catch (Exception ignored) {}
                    if (btnText == null || btnText.isEmpty()) btnText =getApplicationContext().getString(R.string.open_app) ;//"افتح التطبيق";

                    NotificationHelper.get(getApplicationContext()).showPromoNotification(
                            title, body, imageUrl, webUrl, type, btnText, modelType
                    );
                } else {
                    NotificationHelper.get(getApplicationContext()).showBackgroundNotification(
                            title, body, System.currentTimeMillis(), type, imageUrl, webUrl
                    );
                }
            }
        }
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        Log.d("FCM", "New Token: " + token);
    }
}