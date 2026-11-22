package com.hpp.daftree.utils;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

// مكتبات الإعلانات
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

// مكتبات إدارة الموافقة (UMP) الجديدة
import com.google.android.ump.ConsentDebugSettings;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.FormError;
import com.google.android.ump.UserMessagingPlatform;
import com.hpp.daftree.R;

public class AdsManager {

    private static final String TAG = "AdsManager";


    // معرف الإعلان الحقيقي


    // معرف الإعلان التجريبي
//    private static final String AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917";

//    private static final String BANNER_AD_ID = "ca-app-pub-2600356094241738/9216874064";

    private RewardedAd rewardedAd;
    private final Context context;
    private boolean isLoading = false;
    public boolean isMobileAdsInitialized = false; // لتجنب التكرار
private final SecureLicenseManager licenseManager;
    // واجهة للتواصل مع الـ Activity
    public interface AdListener {
        void onAdLoaded(); // الإعلان جاهز
        void onAdFailedToLoad(String error); // فشل التحميل
        void onPointsEarned(int amount); // المستخدم كسب النقاط
        void onAdClosed(); // المستخدم أغلق الإعلان
    }

    public AdsManager(Context context) {
        this.context = context;
       this.licenseManager =  SecureLicenseManager.getInstance(context);
        // --- بداية إعدادات إدارة الموافقة (CMP) ---

        // إعدادات الديباج (للاختبار) - احذف هذا الجزء أو علق عليه عند الرفع للمتجر إذا أردت
        ConsentDebugSettings debugSettings = new ConsentDebugSettings.Builder(context)
                .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA) // محاكاة موقع داخل أوروبا
                .addTestDeviceHashedId("E04B05A84FCE52F6B3BC785F07204248") // <--- ضع المعرف من اللوج كات هنا
                .build();

        ConsentRequestParameters params = new ConsentRequestParameters.Builder()
                .setConsentDebugSettings(debugSettings) // تفعيل وضع الاختبار
                .build();

        // 1. طلب معلومات الموافقة
        ConsentInformation consentInformation = UserMessagingPlatform.getConsentInformation(context);
        consentInformation.requestConsentInfoUpdate(
                (Activity) context,
                params,
                (ConsentInformation.OnConsentInfoUpdateSuccessListener) () -> {
                    // 2. عند نجاح جلب المعلومات، تحقق مما إذا كان يجب عرض النموذج
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                            (Activity) context,
                            (FormError loadAndShowError) -> {
                                if (loadAndShowError != null) {
                                    // حدث خطأ أثناء محاولة عرض النموذج
                                    Log.w(TAG, String.format("%s: %s",
                                            loadAndShowError.getErrorCode(),
                                            loadAndShowError.getMessage()));
                                }

                                // 3. التحقق مما إذا كان يمكننا عرض الإعلانات (سواء وافق المستخدم أو لم يكن في أوروبا)
                                if (consentInformation.canRequestAds()) {
                                    initializeMobileAdsSdk();
                                }
                            }
                    );
                },
                (FormError requestConsentError) -> {
                    // فشل في جلب معلومات الموافقة، نحاول تهيئة الإعلانات احتياطياً
                    Log.w(TAG, String.format("%s: %s",
                            requestConsentError.getErrorCode(),
                            requestConsentError.getMessage()));
                    initializeMobileAdsSdk();
                });
    }

    /**
     * دالة لتهيئة حزمة الإعلانات، يتم استدعاؤها فقط بعد التعامل مع الموافقة
     */
    private void initializeMobileAdsSdk() {
        if (isMobileAdsInitialized) {
            return;
        }
        MobileAds.initialize(context, initializationStatus -> {
            Log.d(TAG, "MobileAds initialized successfully");
            isMobileAdsInitialized = true;
            // نحاول التحميل المسبق للإعلان الأول بمجرد التهيئة
            loadAdInternal(null);
        });
    }

    // --- بقية الكود كما هو تماماً دون تغيير ---

    private void loadAdInternal(AdListener listener) {
        if (isLoading) return;
        isLoading = true;

        AdRequest adRequest = new AdRequest.Builder().build();
        RewardedAd.load(context, licenseManager.getAdmobRewardedId(), adRequest, new RewardedAdLoadCallback() {
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                Log.e(TAG, "Ad Failed: " + loadAdError.getMessage());
                rewardedAd = null;
                isLoading = false;
                if (listener != null) {
                    listener.onAdFailedToLoad(context.getString(R.string.setupListeners_02) + loadAdError.getMessage());
                }
            }

            @Override
            public void onAdLoaded(@NonNull RewardedAd ad) {
                Log.d(TAG, "Ad Loaded Successfully");
                rewardedAd = ad;
                isLoading = false;
                if (listener != null) {
                    listener.onAdLoaded();
                }
            }
        });
    }

    /**
     * الدالة الرئيسية: تحاول عرض الإعلان، إذا لم يكن موجوداً، تقوم بتحميله مع انتظار
     */
    public void showRewardedAd(Activity activity, AdListener listener) {

        if (rewardedAd != null) {
            // الإعلان جاهز مسبقاً -> اعرضه فوراً
            showAdActual(activity, listener);
        } else {
            // الإعلان غير جاهز -> ابدأ التحميل وأخبر الـ Activity بالانتظار
            loadAdInternal(new AdListener() {
                @Override
                public void onAdLoaded() {
                    // بمجرد التحميل، اعرضه
                    showAdActual(activity, listener);
                }

                @Override
                public void onAdFailedToLoad(String error) {
                    // فشل التحميل
                    listener.onAdFailedToLoad(error);
                }

                @Override public void onPointsEarned(int amount) {}
                @Override public void onAdClosed() {}
            });
        }
    }

    private void showAdActual(Activity activity, AdListener listener) {
        if (rewardedAd != null) {
            rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    rewardedAd = null;
                    loadAdInternal(null); // تحميل مسبق للمرة القادمة
                    listener.onAdClosed();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                    rewardedAd = null;
                    listener.onAdFailedToLoad(context.getString(R.string.showAdActual_02));
                }
            });

            rewardedAd.show(activity, rewardItem -> {
                int amount = rewardItem.getAmount();
                if (amount == 0) amount = 5;
                listener.onPointsEarned(amount);
            });

            // نخبر الـ Activity أن الإعلان بدأ (لإخفاء الـ Progress)
            listener.onAdLoaded();
        }
    }
    public void loadBannerAd(ViewGroup adContainer, AdListener listener) {
        AdView adView = new AdView(context);
        adView.setAdUnitId(licenseManager.getAdmobBannerId());

        adView.setAdSize(AdSize.BANNER);

        adContainer.removeAllViews();
        adContainer.addView(adView);

        AdRequest adRequest = new AdRequest.Builder().build();
        adView.setAdListener(new com.google.android.gms.ads.AdListener() {
            @Override
            public void onAdLoaded() {
                if (listener != null) listener.onAdLoaded();
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError adError) {
                if (listener != null) listener.onAdFailedToLoad(adError.getMessage());
            }
        });

        adView.loadAd(adRequest);
    }
}