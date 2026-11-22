package com.hpp.daftree.utils;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.android.installreferrer.api.InstallReferrerClient;
import com.android.installreferrer.api.InstallReferrerStateListener;
import com.android.installreferrer.api.ReferrerDetails;
import com.google.android.material.button.MaterialButton;
import com.hpp.daftree.ui.LoginActivity;
import com.hpp.daftree.MainActivity;
import com.hpp.daftree.R;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class AdsGoogleRewarder {


    private static final String PREF_NAME = "AppPrefs";
    public static final String KEY_GIFT_RECEIVED = "promo_gift_received";
    private static final String KEY_IS_PREMIUM = "is_premium";
    private static final String KEY_EXPIRY_DATE = "premium_expiry";
    public static final String KEY_REFERRAL_PENDING = "referral_pending_activation"; // مفتاح جديد
    private static final int LOGIN_REQUEST_CODE = 1001;
    // تعديل دالة تفعيل الاشتراك لحفظ حالة "الاحالة المعلقة"
    private static void activateTwoMonthsPremium(Activity activity) {
        long sixtyDaysInMillis = 60L * 24 * 60 * 60 * 1000;
        long expiryTime = System.currentTimeMillis() + sixtyDaysInMillis;

        activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_REFERRAL_PENDING, true)
                .putLong(KEY_EXPIRY_DATE, expiryTime)
                .apply();
    }



    /**
     * دالة التحقق من شروط الإحالة
     * تم التعديل لرفض الزيارات العضوية (Organic)
     */
   private static boolean isValidReferrer(String url) {
        if (url == null || url.isEmpty()) return false;

        try {
           String decodedUrl = java.net.URLDecoder.decode(url, "UTF-8").toLowerCase();

           String rawUrl = url.toLowerCase();
            Log.d("AdsGoogleRewarder", "rawUrl: " + rawUrl);
            Log.d("AdsGoogleRewarder", "decodedUrl: " + decodedUrl);
            // 2. فحص الإيجابيات (نبحث عن سبب للقبول أولاً)
            boolean isReferral = false;

            // أ) وجود وسم الحملة الخاص بك
            if (decodedUrl.contains("ramadan_60") || rawUrl.contains("ramadan_60")) {

                isReferral = true;
            }
            else if (decodedUrl.contains("ads-google") || rawUrl.contains("ads-google")) {
                isReferral = true;
            }
            // ب) وجود بصمة إعلانات جوجل (الأكثر موثوقية)
            else if (decodedUrl.contains("gclid") || rawUrl.contains("gclid")
                    || decodedUrl.contains("google_click_id")) {
                isReferral = true;
            }
           else if (decodedUrl.contains("utm_medium=cpc") || decodedUrl.contains("utm_medium=paid")
                    || decodedUrl.contains("utm_source=google_ads")) {
                isReferral = true;
            }


            if (isReferral) {
                return true;
            }

           if (decodedUrl.contains("utm_medium=organic") || decodedUrl.contains("utm_source=organic")||
                   (rawUrl.contains("organic")) || decodedUrl.contains("organic")) {
                Log.d("AdsGoogleRewarder", "تم رفض الإحالة لأنها زيارة طبيعية مؤكدة");
                return false;
            }

            return false;

        } catch (Exception e) {
            Log.e("AdsGoogleRewarder", Objects.requireNonNull(e.getMessage()));
            // في حال حدوث خطأ في فك التشفير، نعود للفحص البدائي على الرابط الأصلي
            String lowerUrl = url.toLowerCase();
            return lowerUrl.contains("ramadan_60") ||
                    lowerUrl.contains("gclid") ||
                    lowerUrl.contains("google_ads");
        }
    }
    public static void checkAndReward(Activity activity) {
       try {
           SharedPreferences prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
           boolean giftReceived = prefs.getBoolean(KEY_GIFT_RECEIVED, false);
           if (giftReceived) {
               Log.d("AdsGoogleRewarder", "gift is already Received: " + giftReceived);
               return;
           }


           InstallReferrerClient referrerClient = InstallReferrerClient.newBuilder(activity).build();
           referrerClient.startConnection(new InstallReferrerStateListener() {
               @Override
               public void onInstallReferrerSetupFinished(int responseCode) {
                   if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                       try {
                           ReferrerDetails response = referrerClient.getInstallReferrer();
                           String referrerUrl = response.getInstallReferrer();
                           Log.d("AdsGoogleRewarder", "Referrer: " + referrerUrl);
                           if (isValidReferrer(referrerUrl)) {
                               activateTwoMonthsPremium(activity);
                           }
                       } catch (RemoteException e) {
                           e.printStackTrace();
                       }
                   }
                   referrerClient.endConnection();
               }

               @Override
               public void onInstallReferrerServiceDisconnected() {
               }
           });
       } catch (RuntimeException e) {
           throw new RuntimeException(e);
       }
    }

    public static void showCongratulationDialog(Activity activity, long expiryTime) {
        if (activity == null || activity.isFinishing()) return;
        SharedPreferences prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean giftReceived = prefs.getBoolean(KEY_GIFT_RECEIVED, false);
        if (giftReceived) return;

        final Dialog dialog = new Dialog(activity);
        dialog.setContentView(R.layout.dialog_referral_reward);
        Objects.requireNonNull(dialog.getWindow()).setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        TextView tvTitle = dialog.findViewById(R.id.tv_title);
        TextView tvExpiry = dialog.findViewById(R.id.tv_expiry);
        TextView tvMassage = dialog.findViewById(R.id.tv_message);
        MaterialButton  btnContinue = dialog.findViewById(R.id.btn_register);
        MaterialButton  btnCancel = dialog.findViewById(R.id.btn_continue_guest);
       // btnCancel.setVisibility(View.GONE);
        tvTitle.setText(activity.getString(R.string.showCongratulationDialog_01));


        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd", Locale.US);
        String dateStr = sdf.format(new Date(expiryTime));
        String message = activity.getString(R.string.showCongratulationDialog_02);
        tvMassage.setText(message);
        tvExpiry.setText(activity.getString(R.string.showCongratulationDialog_03));
        tvExpiry.setVisibility(View.VISIBLE);
        btnContinue.setOnClickListener(v -> {
            dialog.dismiss();
            // إصلاح الانتقال: التأكد من استخدام النشاط الحالي
            Intent intent = new Intent(activity, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // لضمان التشغيل السليم من الديالوج
            intent.putExtra("registerGuest", true);
            activity.startActivityForResult(intent, LOGIN_REQUEST_CODE);
            activity.startActivity(intent);
        });
        btnCancel.setOnClickListener(v -> {
            prefs.edit().putBoolean(AdsGoogleRewarder.KEY_REFERRAL_PENDING, false).apply();
            prefs.edit().putBoolean(AdsGoogleRewarder.KEY_GIFT_RECEIVED, true).apply();

                    dialog.dismiss();
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).showAppTour();
            }
                });
        dialog.show();
    }



}