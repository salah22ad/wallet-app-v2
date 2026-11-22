package com.hpp.daftree.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.collect.ImmutableList;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.hpp.daftree.MainActivity;
import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.database.User;
import com.hpp.daftree.databinding.ActivitySubscriptionBinding;
import com.hpp.daftree.models.AppAuth;
import com.hpp.daftree.utils.AdsManager;
import com.hpp.daftree.utils.DialogHelper;
import com.hpp.daftree.utils.SecureLicenseManager;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class SubscriptionActivity extends BaseActivity implements PurchasesUpdatedListener {
    private ActivitySubscriptionBinding binding;
    private static final String TAG = "SubscriptionActivity";
    // ==================== SIMULATION MODE (DEBUG ONLY) ====================
    private static final String PREF_SIMULATION = "simulation_mode";
    private static final String KEY_FORCED_TIER = "forced_tier";
    private int clickCount = 0;
    private ProductDetails.OneTimePurchaseOfferDetails selectedLifetimeOffer;
    private final Handler clickHandler = new Handler(Looper.getMainLooper());
    private final Runnable resetClickCount = () -> clickCount = 0;

    // ==================== OFFER TOKENS FOR LOYALTY DISCOUNTS ====================
    private static final String OFFER_TOKEN_LOYALTY_V2 = "loyalty-15-percent-off-v2";
    private static final String OFFER_TOKEN_LOYALTY_V3_30 = "loyalty-30-percent-off-v3";
    private static final String OFFER_TOKEN_LOYALTY_V3_15 = "loyalty-15-percent-off-v3";
    private Date userFirstSubscriptionDate;

    // ==================== PRODUCT IDS ====================
    private static final String PRODUCT_SUBSCRIPTION = "my_smart_wallet_premium";
    private static final String PRODUCT_LIFETIME_V1 = "my_smart_wallet_pro";
    private static final String PRODUCT_LIFETIME_V2 = "my_smart_wallet_pro_v2";
    private static final String PRODUCT_LIFETIME_V3 = "my_smart_wallet_pro_v3";

    // ==================== BASE PLAN IDS (Subscription) ====================
    private static final String PLAN_MONTHLY_AUTO_V1 = "monthly-auto";
    private static final String PLAN_MONTHLY_PREPAID_V1 = "monthly-prepaid";
    private static final String PLAN_YEARLY_AUTO_V1 = "yearly-auto";
    private static final String PLAN_YEARLY_PREPAID_V1 = "yearly-prepaid";

    private static final String PLAN_MONTHLY_AUTO_V2 = "monthly-auto-v2";
    private static final String PLAN_MONTHLY_PREPAID_V2 = "monthly-prepaid-v2";
    private static final String PLAN_YEARLY_AUTO_V2 = "yearly-auto-v2";
    private static final String PLAN_YEARLY_PREPAID_V2 = "yearly-prepaid-v2";

    private static final String PLAN_MONTHLY_AUTO_V3 = "monthly-auto-v3";
    private static final String PLAN_MONTHLY_PREPAID_V3 = "monthly-prepaid-v3";
    private static final String PLAN_YEARLY_AUTO_V3 = "yearly-auto-v3";
    private static final String PLAN_YEARLY_PREPAID_V3 = "yearly-prepaid-v3";

    // ==================== CACHE KEYS ====================
    private static final String PREFS_NAME = "subscription_prices_cache";
    private static final String KEY_CACHE_TIMESTAMP = "cache_timestamp";
    private static final String KEY_CACHE_MIGRATED = "cache_migrated_v2";

    private static final String PREF_GRANDFATHERING = "grandfathering";
    private static final String KEY_GRANDFATHER_DECIDED = "decided";
    private static final String KEY_USER_TIER = "user_tier";
    private Date V2_START_DATE;
    private Date V3_START_DATE;

    public enum UserSubscriptionTier {
        LEGACY_V1,
        LEGACY_V2,
        NEW_V3
    }

    // ==================== CLASS FIELDS ====================
    private AdsManager adsManager;
    private FirebaseFirestore db;
    private BillingClient billingClient;
    private ProductDetails detailsSubscription;
    private ProductDetails detailsLifetime;
    private ProductDetails selectedProductDetails;
    private String selectedOfferToken;
    private boolean isAutoRenewSelected = true;
    private CountDownTimer promoTimer;
    private UserSubscriptionTier userTier;
    // ==================== CACHE KEYS ====================
    private static final String KEY_MONTHLY_AUTO_PRICE = "monthly_auto_price";
    private static final String KEY_MONTHLY_PREPAID_PRICE = "monthly_prepaid_price";
    private static final String KEY_YEARLY_AUTO_PRICE = "yearly_auto_price";
    private static final String KEY_YEARLY_PREPAID_PRICE = "yearly_prepaid_price";
    private static final String KEY_LIFETIME_PRICE = "lifetime_price";
    private static final String KEY_LIFETIME_CURRENCY = "lifetime_currency";
    private static final String KEY_LIFETIME_OLD_PRICE = "lifetime_old_price";
    private static final String KEY_LIFETIME_DISCOUNT = "lifetime_discount";
    private static final String KEY_MONTHLY_AUTO_CURRENCY = "monthly_auto_currency";
    private static final String KEY_MONTHLY_PREPAID_CURRENCY = "monthly_prepaid_currency";
    private static final String KEY_YEARLY_AUTO_CURRENCY = "yearly_auto_currency";
    private static final String KEY_YEARLY_PREPAID_CURRENCY = "yearly_prepaid_currency";
    //    private static final String KEY_CACHE_MIGRATED = "cache_migrated_v2";
    private static final long CACHE_EXPIRY_HOURS = 250;

    // ==================== LIFECYCLE ====================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean isGuest = SecureLicenseManager.getInstance(this).isGuest();
        if (isGuest) {
            Intent mainIntent = new Intent(this, MainActivity.class);
            mainIntent.putExtra("purchaseGuest", "true");
            startActivity(mainIntent);
            finish();
            return;
        }
        binding = ActivitySubscriptionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupFloatingToolbar(binding.layoutMain);
        MyApplication.applyGlobalTextWatcher(binding.getRoot());
        adsManager = new AdsManager(this);
        db = FirebaseFirestore.getInstance();
        loadDynamicDates();
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply();
        getSharedPreferences(PREF_GRANDFATHERING, Context.MODE_PRIVATE).edit().clear().apply();
        Log.d(TAG, "Debug Mode: App caches cleared for fresh fetch.");

        logStage("onCreate: Initializing Views and Billing Client");
        initViews();
        binding.radioMonthly.setClickable(false);
        binding.radioYearly.setClickable(false);
        binding.radioLifetime.setClickable(false);
        migrateCacheIfNeeded();
        long userFirstSubscriptionDateLong = SecureLicenseManager.getInstance(this).getFstSubscribingDate();
        if (userFirstSubscriptionDateLong > 0) {
            userFirstSubscriptionDate = new Date(userFirstSubscriptionDateLong);
            Log.d(TAG, "User is an OLD subscriber. First sub date: " + userFirstSubscriptionDate);
        } else {
            userFirstSubscriptionDate = null;
            Log.d(TAG, "User is a NEW subscriber (or guest). No loyalty discount will be applied.");
        }

        proceedWithBillingClientSetup();
        setupLifetimeDiscountLogic();

        boolean isPremium = SecureLicenseManager.getInstance(this).isPremium();
        if (isPremium) {
            disableUIForActiveSubscriber(SecureLicenseManager.getInstance(this).getPlanType());
            binding.btnConfirmPurchase.setText(getString(R.string.already_purchased));
            binding.btnConfirmPurchase.setEnabled(false);
            long expiryLong = SecureLicenseManager.getInstance(this).getSubscriptionExpired();
            long currentTime = System.currentTimeMillis();
            long diffMillis = expiryLong - currentTime;
            long diffDays = diffMillis / (24 * 60 * 60 * 1000);
            if (diffDays < 365 && diffDays > 0) {
                Date expiryDate = new Date(expiryLong);
                DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.LONG, Locale.getDefault());
                String formattedDate = dateFormat.format(expiryDate);
                String message = getString(R.string.subscription_expiry_message, formattedDate);
                binding.tvSubscriptionStatus.setText(message);
                binding.tvSubscriptionStatus.setVisibility(View.VISIBLE);
            } else {
                binding.tvSubscriptionStatus.setVisibility(View.GONE);
            }
        }
    }

    private void loadDynamicDates() {

        Log.d(TAG, "=> START loadDynamicDates()");
        try {
            SecureLicenseManager licenseData = SecureLicenseManager.getInstance(this);

            V2_START_DATE = new Date(licenseData.getV2StartDate());
            V3_START_DATE = new Date(licenseData.getV3StartDate());
            logStage("Loaded Fallback Dates -> V2: " + V2_START_DATE + ", V3: " + V3_START_DATE);

        } catch (Exception e) {
            Log.e(TAG, "Failed to parse fallback dates.", e);

            // خطة طوارئ قصوى لمنع انهيار التطبيق (آمنة 100% ولا تتطلب ParseException)
            java.util.Calendar calV2 = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            calV2.set(2026, java.util.Calendar.AUGUST, 1, 0, 0, 0);
            calV2.set(java.util.Calendar.MILLISECOND, 0);
            V2_START_DATE = calV2.getTime();

            java.util.Calendar calV3 = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            calV3.set(2028, java.util.Calendar.JANUARY, 1, 0, 0, 0);
            calV3.set(java.util.Calendar.MILLISECOND, 0);
            V3_START_DATE = calV3.getTime();

            logStage("Loaded Emergency Fallback Dates -> V2: " + V2_START_DATE + ", V3: " + V3_START_DATE);
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (billingClient != null) billingClient.endConnection();
        if (promoTimer != null) promoTimer.cancel();
    }

    // ==================== BILLING SETUP ====================
    private void proceedWithBillingClientSetup() {
        PendingPurchasesParams pendingPurchasesParams = PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build();
        billingClient = BillingClient.newBuilder(this)
                .setListener(this)
                .enablePendingPurchases(pendingPurchasesParams)
                .build();
        connectToGooglePlay();
    }

    private void connectToGooglePlay() {
        if (!isNetworkAvailable()) {
            runOnUiThread(() -> DialogHelper.show(SubscriptionActivity.this, getString(R.string.no_internet), DialogHelper.FeedbackType.WARNING_2, null));
            return;
        }

        setLoading(true);
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    determineUserTierAndQueryProducts();
                    checkActivePurchases();
                } else {
                    setLoading(false);
                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    if (prefs.getLong(KEY_CACHE_TIMESTAMP, 0) == 0 && billingResult.getResponseCode() == BillingClient.BillingResponseCode.BILLING_UNAVAILABLE) {
                        showToast(getString(R.string.connectToGooglePlay_01) + " " + billingResult.getDebugMessage());
                        runOnUiThread(() -> DialogHelper.show(SubscriptionActivity.this, getString(R.string.billing_unavailable_error), DialogHelper.FeedbackType.ERROR, SubscriptionActivity.this::finish));
                    }
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                setLoading(false);
            }
        });
    }

    // ==================== GRANDFATHERING LOGIC (UPDATED) ====================
    private void determineUserTierAndQueryProducts() {

        String uid = AppAuth.getUserID(SubscriptionActivity.this);//FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            if (isFinishing() || isDestroyed())
                return;
            DialogHelper.show(this, getString(R.string.error_generic), DialogHelper.FeedbackType.WARNING,
                    this::recreate, null);

//            userTier = UserSubscriptionTier.NEW_V3;
//            queryProductsBasedOnUserTier();
            return;
        }
        Date currentDate = new Date();
        UserSubscriptionTier currentPhase;

        if (currentDate.before(V2_START_DATE)) {
            currentPhase = UserSubscriptionTier.LEGACY_V1;
        } else if (currentDate.before(V3_START_DATE)) {
            currentPhase = UserSubscriptionTier.LEGACY_V2;
        } else {
            currentPhase = UserSubscriptionTier.NEW_V3;
        }
        logStage("UserSubscriptionTier -> " +String.valueOf(currentPhase));
        userTier = currentPhase;
        saveUserTierToCache(currentPhase);
        runOnUiThread(this::queryProductsBasedOnUserTier);

    }

    private void saveUserTierToCache(UserSubscriptionTier tier) {
        getSharedPreferences(PREF_GRANDFATHERING, MODE_PRIVATE).edit()
                .putBoolean(KEY_GRANDFATHER_DECIDED, true)
                .putString(KEY_USER_TIER, tier.name()).apply();
    }

    // ==================== PRODUCT QUERIES ====================
    private void queryProductsBasedOnUserTier() {
        querySubscriptionProducts();
        queryLifetimeProduct();
    }

    private void querySubscriptionProducts() {
        List<QueryProductDetailsParams.Product> productList = ImmutableList.of(
                QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_SUBSCRIPTION)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
        );

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build();

        billingClient.queryProductDetailsAsync(params, (billingResult, result) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && result != null) {
                List<ProductDetails> productDetailsList = result.getProductDetailsList();
                if (productDetailsList != null) {
                    for (ProductDetails pd : productDetailsList) {
                        Log.d(TAG, "Found product: " + pd.getProductId());
                        if (pd.getProductId().equals(PRODUCT_SUBSCRIPTION)) {
                            detailsSubscription = pd;
                            Log.d(TAG, "Found subscription product: " + pd.getProductId());
                            runOnUiThread(this::updateSubscriptionPricesDisplay);
                        }
                    }
                }
            }
        });
    }

    private void queryLifetimeProduct() {
        String lifetimeProductId = (userTier == UserSubscriptionTier.LEGACY_V1) ? PRODUCT_LIFETIME_V1 :
                (userTier == UserSubscriptionTier.LEGACY_V2) ? PRODUCT_LIFETIME_V2 : PRODUCT_LIFETIME_V3;

        Log.d(TAG, "queryLifetimeProduct: userTier = " + userTier + ", productId = " + lifetimeProductId);

        List<QueryProductDetailsParams.Product> productList = ImmutableList.of(
                QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(lifetimeProductId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
        );

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build();

        billingClient.queryProductDetailsAsync(params, (billingResult, result) -> {
            setLoading(false);
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && result != null) {
                List<ProductDetails> productDetailsList = result.getProductDetailsList();
                if (productDetailsList != null) {
                    for (ProductDetails pd : productDetailsList) {
                        if (pd.getProductId().equals(lifetimeProductId)) {
                            detailsLifetime = pd;
                            // جلب قائمة بجميع عروض الشراء لمرة واحدة
                            List<ProductDetails.OneTimePurchaseOfferDetails> offerDetailsList =
                                    pd.getOneTimePurchaseOfferDetailsList();

                            if (offerDetailsList != null && !offerDetailsList.isEmpty()) {
                                Log.d(TAG, "Found " + offerDetailsList.size() + " offers for lifetime product");
                                for (ProductDetails.OneTimePurchaseOfferDetails offer : offerDetailsList) {
                                    long priceMicros = offer.getPriceAmountMicros();
                                    String offerToken = offer.getOfferToken();
                                    Log.d(TAG, "Offer Token: " + offerToken + ", Price: " + priceMicros + " micros");
                                }
                                // اختيار العرض الأقل سعراً
                                selectedLifetimeOffer = getBestDiscountedOffer(offerDetailsList);
                                if (selectedLifetimeOffer != null) {
                                    Log.d(TAG, "Selected best offer: price=" + selectedLifetimeOffer.getPriceAmountMicros()
                                            + ", token=" + selectedLifetimeOffer.getOfferToken());
                                }
                            } else {
                                selectedLifetimeOffer = null;
                            }
                            break;
                        }
                    }
                }
                savePricesToCache();
            } else {
                Log.e(TAG, "queryLifetimeProduct failed: " + billingResult.getDebugMessage());
            }
            runOnUiThread(this::updateUIWithPrices);
        });
    }

    private ProductDetails.OneTimePurchaseOfferDetails getBestDiscountedOffer(List<ProductDetails.OneTimePurchaseOfferDetails> offers) {
        if (offers == null || offers.isEmpty()) return null;
        ProductDetails.OneTimePurchaseOfferDetails best = offers.get(0);
        long lowestPrice = best.getPriceAmountMicros();
        for (ProductDetails.OneTimePurchaseOfferDetails offer : offers) {
            long price = offer.getPriceAmountMicros();
            if (price < lowestPrice) {
                lowestPrice = price;
                best = offer;
            }
        }
        return best;
    }

    // ==================== OFFERS & DISCOUNTS LOGIC (UNIFIED) ====================
    private ProductDetails.SubscriptionOfferDetails findOfferByToken(List<ProductDetails.SubscriptionOfferDetails> offers, String token) {
        for (ProductDetails.SubscriptionOfferDetails offer : offers) {
            if (token.equals(offer.getOfferToken())) return offer;
        }
        return getStandardOffer(offers); // Fallback to standard
    }

    private ProductDetails.SubscriptionOfferDetails getStandardOffer1(List<ProductDetails.SubscriptionOfferDetails> offers) {
        ProductDetails.SubscriptionOfferDetails standard = offers.get(0);
        long highestPrice = standard.getPricingPhases().getPricingPhaseList().get(0).getPriceAmountMicros();
        for (ProductDetails.SubscriptionOfferDetails offer : offers) {
            long price = offer.getPricingPhases().getPricingPhaseList().get(0).getPriceAmountMicros();
            if (price > highestPrice) {
                highestPrice = price;
                standard = offer;
            }
        }
        return standard;
    }

    private String getTargetPlanId(boolean isMonthly) {
        switch (userTier) {
            case LEGACY_V1:
                return isMonthly ? (isAutoRenewSelected ? PLAN_MONTHLY_AUTO_V1 : PLAN_MONTHLY_PREPAID_V1) : (isAutoRenewSelected ? PLAN_YEARLY_AUTO_V1 : PLAN_YEARLY_PREPAID_V1);
            case LEGACY_V2:
                return isMonthly ? (isAutoRenewSelected ? PLAN_MONTHLY_AUTO_V2 : PLAN_MONTHLY_PREPAID_V2) : (isAutoRenewSelected ? PLAN_YEARLY_AUTO_V2 : PLAN_YEARLY_PREPAID_V2);
            default:
                return isMonthly ? (isAutoRenewSelected ? PLAN_MONTHLY_AUTO_V3 : PLAN_MONTHLY_PREPAID_V3) : (isAutoRenewSelected ? PLAN_YEARLY_AUTO_V3 : PLAN_YEARLY_PREPAID_V3);
        }
    }

    private List<ProductDetails.SubscriptionOfferDetails> getOffersForPlanId(String planId) {
        List<ProductDetails.SubscriptionOfferDetails> list = new ArrayList<>();
        if (detailsSubscription != null && detailsSubscription.getSubscriptionOfferDetails() != null) {
            for (ProductDetails.SubscriptionOfferDetails o : detailsSubscription.getSubscriptionOfferDetails()) {
                if (o.getBasePlanId().equals(planId)) list.add(o);
            }
        }
        return list;
    }

    private void findOfferTokenFor(String basePlanId) {
        ProductDetails.SubscriptionOfferDetails offer = getTargetOfferForPlan(basePlanId);
        selectedOfferToken = (offer != null) ? offer.getOfferToken() : null;
    }

    // ==================== OFFERS & DISCOUNTS LOGIC (UNIFIED) ====================
    @Nullable
    private ProductDetails.SubscriptionOfferDetails getTargetOfferForPlan(String basePlanId) {
        if (detailsSubscription == null || detailsSubscription.getSubscriptionOfferDetails() == null)
            return null;

        List<ProductDetails.SubscriptionOfferDetails> offersForPlan = new ArrayList<>();
        for (ProductDetails.SubscriptionOfferDetails offer : detailsSubscription.getSubscriptionOfferDetails()) {
            if (offer.getBasePlanId().equals(basePlanId)) offersForPlan.add(offer);
        }
        if (offersForPlan.isEmpty()) return null;

        boolean isV2 = basePlanId.contains("-v2");
        boolean isV3 = basePlanId.contains("-v3");

        if (!isV2 && !isV3) return getStandardOffer(offersForPlan); // V1

        if (userFirstSubscriptionDate != null) {
            Date firstSub = userFirstSubscriptionDate;
            Date dateV2 = getDateOnly(V2_START_DATE);
            Date dateV3 = getDateOnly(V3_START_DATE);
            logStage("firstSub: " + firstSub + " dateV2: " + dateV2 + " dateV3: " + dateV3);

            if (isV2) {
                if (firstSub.before(dateV2)) {
                    // استدعاء دالة البحث بالـ ID
                    return findOfferById(offersForPlan, OFFER_TOKEN_LOYALTY_V2);
                }
            } else if (isV3) {
                if (firstSub.before(dateV2)) {
                    return findOfferById(offersForPlan, OFFER_TOKEN_LOYALTY_V3_30);
                } else if (firstSub.before(dateV3)) {
                    return findOfferById(offersForPlan, OFFER_TOKEN_LOYALTY_V3_15);
                }
            }
        }
        return getStandardOffer(offersForPlan);
    }

    // 🛑 التعديل الجوهري هنا: البحث بالـ OfferId بدلاً من الـ OfferToken
    private ProductDetails.SubscriptionOfferDetails findOfferById(List<ProductDetails.SubscriptionOfferDetails> offers, String targetOfferId) {
        for (ProductDetails.SubscriptionOfferDetails offer : offers) {
            // المطابقة الصحيحة تتم مع getOfferId الخاص بلوحة تحكم جوجل
            if (targetOfferId.equals(offer.getOfferId())) {
                return offer;
            }
        }
        return getStandardOffer(offers); // Fallback to standard
    }

    private ProductDetails.SubscriptionOfferDetails getStandardOffer(List<ProductDetails.SubscriptionOfferDetails> offers) {
        ProductDetails.SubscriptionOfferDetails standard = offers.get(0);
        long highestPrice = standard.getPricingPhases().getPricingPhaseList().get(0).getPriceAmountMicros();
        for (ProductDetails.SubscriptionOfferDetails offer : offers) {
            long price = offer.getPricingPhases().getPricingPhaseList().get(0).getPriceAmountMicros();
            if (price > highestPrice) {
                highestPrice = price;
                standard = offer;
            }
        }
        return standard;
    }

    // ==================== UI UPDATE ====================
    @SuppressLint("SetTextI18n")
    private void updateUIWithPrices() {
        try {
            binding.tvPromoTimer.setVisibility(View.GONE);
            if (detailsLifetime != null) {
                // ✅ استخدام العرض المخفَّض المختار إن وُجد، وإلا العرض الافتراضي
                ProductDetails.OneTimePurchaseOfferDetails otDetails;
                if (selectedLifetimeOffer != null) {
                    otDetails = selectedLifetimeOffer;
                    Log.d(TAG, "updateUIWithPrices: Using discounted offer, price=" + otDetails.getPriceAmountMicros());
                } else {
                    otDetails = detailsLifetime.getOneTimePurchaseOfferDetails();
                    Log.d(TAG, "updateUIWithPrices: Using default offer, price=" + otDetails.getPriceAmountMicros());
                }

                if (otDetails == null) {
                    Log.e(TAG, "updateUIWithPrices: otDetails is null");
                    return;
                }

                long priceMicros = otDetails.getPriceAmountMicros();
                String currencyCode = otDetails.getPriceCurrencyCode();

                double priceAmount = (double) priceMicros / 1_000_000;
                String formatted = String.format(Locale.US, "%.2f %s", priceAmount, currencyCode);
                binding.tvPriceLifetimeDiscounted.setText(formatted);
                binding.tvPriceLifetimeDiscounted.setVisibility(View.VISIBLE);

                String productId = detailsLifetime.getProductId();
                long OFFICIAL_ORIGINAL_PRICE_MICROS = SecureLicenseManager.getInstance(this).getProductPrice(productId, 99990000L);
                logStage("OFFICIAL_ORIGINAL_PRICE_MICROS: " + OFFICIAL_ORIGINAL_PRICE_MICROS + " DISCOUNTING: " + formatted);

                if (priceMicros < OFFICIAL_ORIGINAL_PRICE_MICROS) {
                    double originalPrice = (double) OFFICIAL_ORIGINAL_PRICE_MICROS / 1_000_000;
                    String originalFormatted = String.format(Locale.US, "%.2f %s", originalPrice, currencyCode);

                    binding.tvOldPriceLifetime.setText(originalFormatted);
                    binding.tvOldPriceLifetime.setPaintFlags(binding.tvOldPriceLifetime.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                    binding.tvOldPriceLifetime.setVisibility(View.VISIBLE);

                    long diff = OFFICIAL_ORIGINAL_PRICE_MICROS - priceMicros;
                    int discountPercent = (int) ((diff * 100) / OFFICIAL_ORIGINAL_PRICE_MICROS);
                    binding.tvDiscountLabel.setText(getString(R.string.updateUIWithPrices_04) + ": " + discountPercent + "%");
                    binding.tvDiscountLabel.setVisibility(View.VISIBLE);
                    binding.tvPromoTimer.setVisibility(View.VISIBLE);
                } else {
                    binding.tvOldPriceLifetime.setVisibility(View.GONE);
                    binding.tvDiscountLabel.setVisibility(View.GONE);
                    binding.tvPromoTimer.setVisibility(View.GONE);
                }
            } else {
                binding.tvPriceLifetimeDiscounted.setVisibility(View.GONE);
                binding.tvOldPriceLifetime.setVisibility(View.GONE);
                binding.tvDiscountLabel.setVisibility(View.GONE);
            }
            updateSubscriptionPricesDisplay();
        } catch (NumberFormatException e) {
            logStage("UI price formatting error: " + e.getMessage());
        }
    }

    private void updateSubscriptionPricesDisplay() {
        if (detailsSubscription == null) return;

        String targetMonthlyPlanId = getTargetPlanId(true);
        String targetYearlyPlanId = getTargetPlanId(false);

        // جلب وتحديث أسعار الخطة الشهرية
        List<ProductDetails.SubscriptionOfferDetails> monthlyOffers = getOffersForPlanId(targetMonthlyPlanId);
        if (!monthlyOffers.isEmpty()) {
            updateMonthlyPriceUI(getTargetOfferForPlan(targetMonthlyPlanId), getStandardOffer(monthlyOffers));
        }

        // جلب وتحديث أسعار الخطة السنوية
        List<ProductDetails.SubscriptionOfferDetails> yearlyOffers = getOffersForPlanId(targetYearlyPlanId);
        if (!yearlyOffers.isEmpty()) {
            updateYearlyPriceUI(getTargetOfferForPlan(targetYearlyPlanId), getStandardOffer(yearlyOffers));
        }
    }

    // --- دالة مخصصة للخطة الشهرية ---
    private void updateMonthlyPriceUI(ProductDetails.SubscriptionOfferDetails activeOffer, ProductDetails.SubscriptionOfferDetails standardOffer) {
        if (activeOffer == null || standardOffer == null) return;

        long activeMicros = activeOffer.getPricingPhases().getPricingPhaseList().get(0).getPriceAmountMicros();
        String currencyCode = activeOffer.getPricingPhases().getPricingPhaseList().get(0).getPriceCurrencyCode();
        double activeAmount = (double) activeMicros / 1_000_000;
        String activeFormatted = String.format(Locale.US, "%.2f %s", activeAmount, currencyCode);

        long standardMicros = standardOffer.getPricingPhases().getPricingPhaseList().get(0).getPriceAmountMicros();
        String suffix = isAutoRenewSelected ? getString(R.string.updateSubscriptionPricesDisplay_06) : "";

        // الخصم يطبق فقط في الاشتراك التلقائي (Prepaid لا يوجد فيه خصم)
        if (isAutoRenewSelected && activeMicros < standardMicros) {
            double standardAmount = (double) standardMicros / 1_000_000;
            String standardFormatted = String.format(Locale.US, "%.2f %s", standardAmount, currencyCode);

            // نظراً لعدم وجود tvMonthlySaveBadge في التصميم، نستخدم Spannable لدمج السعرين في مساحة واحدة
            String fullText = activeFormatted + "   " + standardFormatted + suffix;
            SpannableString spannable = new SpannableString(fullText);

            int startStrike = activeFormatted.length() + 3;
            int endStrike = startStrike + standardFormatted.length();

            spannable.setSpan(new StrikethroughSpan(), startStrike, endStrike, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new ForegroundColorSpan(Color.GRAY), startStrike, endStrike, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            binding.tvPriceMonthly.setText(spannable);
        } else {
            // لا يوجد خصم أو الدفع مسبق (Prepaid) -> عرض السعر العادي فقط
            binding.tvPriceMonthly.setText(activeFormatted + suffix);
        }
    }

    // --- دالة مخصصة للخطة السنوية (استغلال tvYearlySaveBadge) ---
    private void updateYearlyPriceUI(ProductDetails.SubscriptionOfferDetails activeOffer, ProductDetails.SubscriptionOfferDetails standardOffer) {
        if (activeOffer == null || standardOffer == null) return;

        long activeMicros = activeOffer.getPricingPhases().getPricingPhaseList().get(0).getPriceAmountMicros();
        String currencyCode = activeOffer.getPricingPhases().getPricingPhaseList().get(0).getPriceCurrencyCode();
        double activeAmount = (double) activeMicros / 1_000_000;
        String activeFormatted = String.format(Locale.US, "%.2f %s", activeAmount, currencyCode);

        long standardMicros = standardOffer.getPricingPhases().getPricingPhaseList().get(0).getPriceAmountMicros();
        String suffix = isAutoRenewSelected ? getString(R.string.updateSubscriptionPricesDisplay_07) : "";

        // تعيين السعر النهائي (المخفض أو العادي) في النص الأساسي
        binding.tvPriceYearly.setText(activeFormatted + suffix);

        // الخصم يطبق فقط في الاشتراك التلقائي (Prepaid لا يوجد فيه خصم)
        if (isAutoRenewSelected && activeMicros < standardMicros && binding.tvYearlySaveBadge != null) {
            double standardAmount = (double) standardMicros / 1_000_000;
            String standardFormatted = String.format(Locale.US, "%.2f %s", standardAmount, currencyCode);

            binding.tvYearlySaveBadge.setText(standardFormatted);
            binding.tvYearlySaveBadge.setPaintFlags(binding.tvYearlySaveBadge.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            binding.tvYearlySaveBadge.setVisibility(View.VISIBLE);
        } else {
            // لا يوجد خصم أو الدفع مسبق (Prepaid) -> إخفاء الـ Badge
            if (binding.tvYearlySaveBadge != null) {
                binding.tvYearlySaveBadge.setVisibility(View.GONE);
            }
        }
    }

    // ==================== CACHE METHODS ====================
    private void migrateCacheIfNeeded() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_CACHE_MIGRATED, false)) return;
        prefs.edit().clear().putBoolean(KEY_CACHE_MIGRATED, true).apply();
    }

    private void savePricesToCache() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putLong(KEY_CACHE_TIMESTAMP, System.currentTimeMillis()).apply();
    }

    // ==================== PLAN SELECTION & PURCHASE ====================
    private void initViews() {
        binding.tabAutoRenew.setOnClickListener(v -> updateTabSelection(true));
        binding.tabPrepaid.setOnClickListener(v -> updateTabSelection(false));
        binding.btnBack.setOnClickListener(v -> finish());
        binding.cardMonthly.setOnClickListener(v -> selectPlan("MONTHLY"));
        binding.cardYearly.setOnClickListener(v -> selectPlan("YEARLY"));
        binding.cardLifetime.setOnClickListener(v -> selectPlan("LIFETIME"));
        binding.btnConfirmPurchase.setText(getString(R.string.confirm_purchase));
        binding.btnConfirmPurchase.setEnabled(false);
        binding.btnConfirmPurchase.setOnClickListener(v -> startPurchaseFlow());
        updateTabSelection(true);

    }
    private void selectPlan(String planType) {
        binding.radioMonthly.setChecked(false);
        binding.radioYearly.setChecked(false);
        binding.radioLifetime.setChecked(false);
        binding.btnConfirmPurchase.setEnabled(true);

        switch (planType) {
            case "MONTHLY":
                binding.radioMonthly.setChecked(true);
                selectedProductDetails = detailsSubscription;
                findOfferTokenFor(getTargetPlanId(true));
                break;
            case "YEARLY":
                binding.radioYearly.setChecked(true);
                selectedProductDetails = detailsSubscription;
                findOfferTokenFor(getTargetPlanId(false));
                break;
            case "LIFETIME2":
                binding.radioLifetime.setChecked(true);
                selectedProductDetails = detailsLifetime;
                selectedOfferToken = null;
                break;
            case "LIFETIME":
                binding.radioLifetime.setChecked(true);
                selectedProductDetails = detailsLifetime;
                if (selectedLifetimeOffer != null) {
                    selectedOfferToken = selectedLifetimeOffer.getOfferToken();
                    Log.d(TAG, "Lifetime: Using discounted offer token");
                } else {
                    selectedOfferToken = null;
                }
                break;

        }
    }

    private void startPurchaseFlow() {
        if (selectedProductDetails == null) {
            showToast(getString(R.string.startPurchaseFlow_08));
            return;
        }

        ImmutableList<BillingFlowParams.ProductDetailsParams> paramsList;
        if (selectedOfferToken != null && !selectedOfferToken.isEmpty()) {
            paramsList = ImmutableList.of(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(selectedProductDetails)
                            .setOfferToken(selectedOfferToken)
                            .build()
            );
        } else {
            paramsList = ImmutableList.of(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(selectedProductDetails)
                            .build()
            );
        }
        BillingFlowParams flowParams = BillingFlowParams.newBuilder().setProductDetailsParamsList(paramsList).build();
        BillingResult result = billingClient.launchBillingFlow(this, flowParams);
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK)
            logError("Launch flow failed", result);
    }

    // ==================== PURCHASE HANDLING ====================
    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> list) {
        int code = billingResult.getResponseCode();
        switch (code) {
            case BillingClient.BillingResponseCode.OK:
                if (list != null) for (Purchase p : list) handlePurchase(p);
                break;
            case BillingClient.BillingResponseCode.USER_CANCELED:
                showToast(getString(R.string.onPurchasesUpdated_09));
                break;
            case BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED:
                showToast(getString(R.string.subscribing_already_owned));
                checkActivePurchases();
                break;
            case BillingClient.BillingResponseCode.SERVICE_DISCONNECTED:
                connectToGooglePlay();
                break;
            default:
                showToast(getString(R.string.onPurchasesUpdated_12) + billingResult.getDebugMessage());
                break;
        }
    }

    private void handlePurchase(Purchase purchase) {
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged()) {
                AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.getPurchaseToken()).build();
                billingClient.acknowledgePurchase(params, result -> {
                    if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        runOnUiThread(() -> {
                            handleSuccessfulPurchase(purchase);
                            grantEntitlement(purchase);
                            binding.btnConfirmPurchase.setText(getString(R.string.success_purchased));
                            binding.btnConfirmPurchase.setEnabled(false);
                        });
                    }
                });
            } else {
                grantEntitlement(purchase);
            }
        } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
            showToast(getString(R.string.handlePurchase_14));
        }
    }

    private void grantEntitlement(Purchase purchase) {
        boolean isLifetime = purchase.getProducts().contains(PRODUCT_LIFETIME_V1) ||
                purchase.getProducts().contains(PRODUCT_LIFETIME_V2) ||
                purchase.getProducts().contains(PRODUCT_LIFETIME_V3);
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("is_premium", true);
        if (isLifetime) editor.putBoolean("is_lifetime", true);
        editor.apply();
        binding.btnConfirmPurchase.setText(getString(R.string.already_purchased));
        binding.btnConfirmPurchase.setEnabled(false);
    }

    private void handleSuccessfulPurchase(Purchase purchase) {
        String productId = purchase.getProducts().get(0);
        boolean isLifetime = productId.equals(PRODUCT_LIFETIME_V1) || productId.equals(PRODUCT_LIFETIME_V2) || productId.equals(PRODUCT_LIFETIME_V3);

        String planType = isLifetime ? "lifetime" : (isAutoRenewSelected ? "auto" : "prepaid");
        if (!isLifetime) {
            if (binding.radioMonthly.isChecked()) planType = "monthly-" + planType;
            else if (binding.radioYearly.isChecked()) planType = "yearly-" + planType;
        }

        Calendar calendar = Calendar.getInstance();
        if (isLifetime) calendar.add(Calendar.YEAR, 99);
        else if (planType.contains("monthly")) calendar.add(Calendar.MONTH, 1);
        else if (planType.contains("yearly")) calendar.add(Calendar.YEAR, 1);
        Date expiryDate = calendar.getTime();

        String uid = AppAuth.getUserID(SubscriptionActivity.this);//FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        String finalPlanType = planType;
        db.collection("users").document(uid).get().addOnSuccessListener(documentSnapshot -> {
            Map<String, Object> subData = new HashMap<>();
            subData.put("is_premium", true);
            subData.put("plan_type", finalPlanType);
            subData.put("subscription_expiry", new Timestamp(expiryDate));
            subData.put("subscription_start", Timestamp.now());
            subData.put("purchase_token", purchase.getPurchaseToken());
            subData.put("lastModified", System.currentTimeMillis());
            subData.put("subscriptionStatus", "activated");
            subData.put("premium_activated_at", User.getCurrentLocalDateTime());
            subData.put("premium_activated_by", "Google Play Store");

            if (!documentSnapshot.contains("first_subscription_date")) {
                subData.put("first_subscription_date", Timestamp.now());
            }

            db.collection("users").document(uid).set(subData, SetOptions.merge());
        });

        SecureLicenseManager manager = SecureLicenseManager.getInstance(this);
        manager.setPremium(true);
        manager.setPlanType(planType);
        manager.setSubscriptionExpiry(expiryDate.getTime());
        manager.setPremiumActivatedBy("Google Play Store");

        runOnUiThread(() -> {
            Toast.makeText(this, getString(R.string.handleSuccessfulPurchase_18), Toast.LENGTH_LONG).show();
            disableUIForActiveSubscriber(finalPlanType);
            // 🔥 إرجاع النتيجة إلى MainActivity
            setResult(RESULT_OK);
        });
    }

    // ==================== RESTORE & UI HELPERS ====================
    private void checkActivePurchases() {
        billingClient.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
                (result, list) -> {
                    if (result.getResponseCode() == BillingClient.BillingResponseCode.OK)
                        processRestoreList(list);
                });
        billingClient.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
                (result, list) -> {
                    if (result.getResponseCode() == BillingClient.BillingResponseCode.OK)
                        processRestoreList(list);
                });
    }

    private void processRestoreList(List<Purchase> list) {
        boolean hasActive = false;
        for (Purchase p : list) {
            if (p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                hasActive = true;
                runOnUiThread(() -> grantEntitlement(p));
            }
        }
        if (!hasActive) {
            runOnUiThread(() -> {
                if (binding.btnConfirmPurchase.getText().toString().equals(getString(R.string.already_purchased)))
                    resetUINonSubscriber();
            });
        }
    }

    private void resetUINonSubscriber() {
        binding.tabAutoRenew.setEnabled(true);
        binding.tabPrepaid.setEnabled(true);
        binding.cardMonthly.setClickable(true);
        binding.cardYearly.setClickable(true);
        binding.cardLifetime.setClickable(true);
        binding.radioMonthly.setChecked(false);
        binding.radioYearly.setChecked(false);
        binding.radioLifetime.setChecked(false);
        binding.btnConfirmPurchase.setText(getString(R.string.confirm_purchase));
        binding.btnConfirmPurchase.setEnabled(false);
        binding.btnConfirmPurchase.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.primary_blue));
        binding.tvSubscriptionStatus.setVisibility(View.GONE);
    }

    private void updateTabSelection(boolean isAutoRenew) {
        this.isAutoRenewSelected = isAutoRenew;

        if (isAutoRenew) {
            binding.tabAutoRenew.setBackgroundResource(R.drawable.bg_tab_selected);
            binding.tabAutoRenew.setTextColor(ContextCompat.getColor(this, R.color.white));
            binding.tabPrepaid.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
            binding.tabPrepaid.setTextColor(ContextCompat.getColor(this, R.color.grey_500));
        } else {
            binding.tabPrepaid.setBackgroundResource(R.drawable.bg_tab_selected);
            binding.tabPrepaid.setTextColor(ContextCompat.getColor(this, R.color.white));
            binding.tabAutoRenew.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
            binding.tabAutoRenew.setTextColor(ContextCompat.getColor(this, R.color.grey_500));
        }
        binding.tvPlanDescription.setText(isAutoRenew ? getString(R.string.switchTab_03) : getString(R.string.switchTab_04));

        binding.radioMonthly.setChecked(false);
        binding.radioYearly.setChecked(false);
        binding.radioLifetime.setChecked(false);
        selectedProductDetails = null;
        binding.btnConfirmPurchase.setEnabled(false);

        // ✅ تحديث الأسعار فقط إذا كان billingClient جاهزاً والاشتراكات محملة
        if (billingClient != null && detailsSubscription != null) {
            updateSubscriptionPricesDisplay();
        } else if (billingClient != null) {
            // billingClient موجود ولكن الاشتراكات لم تحمل بعد → نعيد محاولة الجلب
            Log.d(TAG, "updateTabSelection: detailsSubscription is null, re-querying products");
            querySubscriptionProducts();
        } else {
            // billingClient لا يزال null، سيتم التحديث لاحقاً عند اكتمال الاتصال
            Log.d(TAG, "updateTabSelection: billingClient not ready yet, skipping query");
        }
    }

    private void disableUIForActiveSubscriber(String subType) {
        binding.tabAutoRenew.setEnabled(false);
        binding.tabPrepaid.setEnabled(false);
        binding.cardMonthly.setClickable(false);
        binding.cardYearly.setClickable(false);
        binding.cardLifetime.setClickable(false);
        if (subType.contains("prepaid") || subType.contains("lifetime")) updateTabSelection(false);
        else updateTabSelection(true);
        binding.radioMonthly.setChecked(false);
        binding.radioYearly.setChecked(false);
        binding.radioLifetime.setChecked(false);
        if (subType.contains("monthly")) binding.radioMonthly.setChecked(true);
        else if (subType.contains("yearly")) binding.radioYearly.setChecked(true);
        else if (subType.contains("lifetime") || subType.contains("pro"))
            binding.radioLifetime.setChecked(true);
        binding.btnConfirmPurchase.setText(getString(R.string.already_purchased));
        binding.btnConfirmPurchase.setEnabled(false);
        binding.btnConfirmPurchase.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.success_green));
    }

    private void setupLifetimeDiscountLogic() {
        long endTime = System.currentTimeMillis() + (24 * 60 * 60 * 1000);
        if (promoTimer != null) promoTimer.cancel();
        promoTimer = new CountDownTimer(endTime - System.currentTimeMillis(), 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                String timeText = String.format(Locale.ENGLISH, "%02d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(millisUntilFinished), TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60, TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60);
                if (binding.tvPromoTimer != null)
                    binding.tvPromoTimer.setText(getString(R.string.setupLifetimeDiscountLogic_16) + timeText);
            }

            @Override
            public void onFinish() {
                binding.tvPromoTimer.setText(getString(R.string.setupLifetimeDiscountLogic_17));
            }
        }.start();
    }

    private void setForcedTierForSimulation(String tier) {
        SharedPreferences.Editor editor = getSharedPreferences(PREF_SIMULATION, MODE_PRIVATE).edit();
        if (tier == null) editor.remove(KEY_FORCED_TIER);
        else editor.putString(KEY_FORCED_TIER, tier);
        editor.apply();
    }

    private void showSimulationDialog() {
        String[] tiers = {
                "LEGACY_V1 (الأسعار القديمة - V1)",
                "LEGACY_V2 (المرحلة الثانية بخصم 20% - V2)",
                "NEW_V3 (المرحلة الثالثة كاملة - V3)",
                "--- محاكاة السيناريوهات ---",
                "سيناريو V2: first_subscription قبل V2 (خصم 20%)",
                "سيناريو V3: first_subscription قبل V2 (خصم 30%)",
                "سيناريو V3: first_subscription بين V2 و V3 (خصم 15%)",
                "إلغاء المحاكاة (العودة للوضع العادي)"
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle("🔧 وضع المحاكاة - اختبار المراحل والخصومات")
                .setItems(tiers, (dialog, which) -> {
                    SharedPreferences simPrefs = getSharedPreferences(PREF_SIMULATION, MODE_PRIVATE);
                    switch (which) {
                        case 0:
                            setForcedTierForSimulation("LEGACY_V1");
                            simPrefs.edit().remove("simulated_first_subscription_date").apply();
                            break;
                        case 1:
                            setForcedTierForSimulation("LEGACY_V2");
                            simPrefs.edit().remove("simulated_first_subscription_date").apply();
                            break;
                        case 2:
                            setForcedTierForSimulation("NEW_V3");
                            simPrefs.edit().remove("simulated_first_subscription_date").apply();
                            break;
                        case 4:
                            setForcedTierForSimulation("LEGACY_V2");
                            simPrefs.edit().putString("simulated_first_subscription_date", "2025-01-01").apply();
                            break;
                        case 5:
                            setForcedTierForSimulation("NEW_V3");
                            simPrefs.edit().putString("simulated_first_subscription_date", "2025-01-01").apply();
                            break;
                        case 6:
                            setForcedTierForSimulation("NEW_V3");
                            simPrefs.edit().putString("simulated_first_subscription_date", "2027-01-01").apply();
                            break;
                        case 7:
                            setForcedTierForSimulation(null);
                            simPrefs.edit().remove("simulated_first_subscription_date").apply();
                            break;
                    }
                    recreate();
                }).show();
    }

    private Date getDateOnly(Date date) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getApplication().getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
    }

    private void setLoading(boolean isLoading) {
        runOnUiThread(() -> binding.loadingOverlay.setVisibility(isLoading ? View.VISIBLE : View.GONE));
    }

    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    private void logStage(String message) {
        Log.d(TAG, message);
    }

    private void logError(String message, BillingResult result) {
        Log.e(TAG, message + " | Code: " + result.getResponseCode() + " | Debug: " + result.getDebugMessage());
    }
}