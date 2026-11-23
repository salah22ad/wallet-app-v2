
package com.hpp.daftree.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hpp.daftree.models.AppAuth;
import com.hpp.daftree.database.DeviceInfo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * مدير الترخيص الآمن والمشفر
 * يحتوي على دوال مشفرة ومحمية ضد التهكير
 */
public class SecureLicenseManager {

    private static final String TAG = "SecureLicenseManager";
    private static final String PREFS_NAME = "secure_license_prefs";
    // مفاتيح مشفرة للتخزين
    public static final String ENCRYPTED_KEY_PREMIUM = "cHJlbWl1bV9zdGF0dXM="; // premium_status
    public static final String ENCRYPTED_KEY_TRANSACTION_COUNT = "cXVvdGVfY291bnQ="; // quote_count
    public static final String ENCRYPTED_KEY_MAX_TRANSACTIONS = "bWF4X3F1b3Rlcw=="; // max_quotes
    public static final String ENCRYPTED_KEY_USER_ID = "dXNlcl9pZA=="; // user_id
    private static final String ENCRYPTED_KEY_DEVICE_ID = "ZGV2aWNlX2lk"; // device_id

    public static final String ENCRYPTED_KEY_APP_CONFIGURED = "app_configured_v1";
    public static final String ENCRYPTED_KEY_ADMIN_EMAILS = "admin_emails_list";
    public static final String ENCRYPTED_KEY_USER_TYPE = "dyn_user_type";
    public static final String KEY_fstSubscribingDate = "fstSubscribingDate";
    public static final String ENCRYPTED_KEY_MIN_VERSION = "dyn_min_version";

    public static final String ENCRYPTED_KEY_V2_START_DATE = "dyn_v2_start_date";



    public static final String ENCRYPTED_KEY_PROD_PRICE = "dyn_prod_price";
    public static final String ENCRYPTED_KEY_V3_START_DATE = "dyn_v3_start_date";
    public static final String ENCRYPTED_KEY_ADMOB_BANNER = "dyn_admob_banner";
    public static final String ENCRYPTED_KEY_ADMOB_REWARDED = "dyn_admob_rewarded";
    public static final String ENCRYPTED_KEY_APP_SEC_KEY = "dyn_app_sec_key";
    public static final String ENCRYPTED_KEY_ENC_KEYS = "dyn_enc_keys";
    public static final String ENCRYPTED_KEY_WEB_KEYS = "dyn_web_keys";
    public static final String ENCRYPTED_KEY_SIG_HASH = "dyn_sig_hash";

    // مفاتيح مشفرة جديدة لتخزين بيانات الترخيص
    public static final String ENCRYPTED_MAX_TRANSACTIONS = "bWF4X3RyYW5zYWN0aW9ucw=="; // max_transactions
    public static final String ENCRYPTED_TRANSACTIONS_COUNT = "dHJhbnNhY3Rpb25zX2NvdW50"; // transactions_count
    public static final String ENCRYPTED_AD_REWARDS = "YWRfcmV3YXJkcw=="; // ad_rewards
    public static final String ENCRYPTED_REFERRAL_REWARDS = "cmVmZXJyYWxfcmV3YXJkcw=="; // referral_rewards
    private static final String KEY_MAX_TRANSACTIONS = "encrypted_max_trans";
    private static final String KEY_DEVICES_NOS = "devices_count";
    private static final String KEY_TRANSACTIONS_COUNT = "encrypted_trans_count";
    private static final String KEY_Guest_TRANSACTIONS_COUNT = "encrypted_trans_count";
    private static final String KEY_isDebtFeatureEnabled = "isDebtFeatureEnabled";
    private static final String KEY_LinkedAccount = "LinkedAccount";
    private static final String KEY_AD_REWARDS = "encrypted_ad_rewards";
    private static final String KEY_REFERRAL_REWARDS = "encrypted_ref_rewards";
    private static final String KEY_IS_PREMIUM = "encrypted_is_premium";
    private static final String KEY_IS_START = "encrypted_is_start";
    private static final String GUEST_USER = "guest_user";
    private static final String GUEST_UID = "guest_uid";
    private static final String EMAIL = "email";


    private static final String KEY_USER_TYPE = "user_type";
    // --------------- NEW
    private static final String NEW_KEY_TRANSACTIONS_COUNT = "encrypted_trans_count";
    private static final String NEW_KEY_AD_REWARDS = "encrypted_ad_rewards";
    private static final String NEW_KEY_REFERRAL_REWARDS = "encrypted_ref_rewards";
    //---------DIFF_
    private static final String DIFF_KEY_TRANSACTIONS_COUNT = "encrypted_trans_count";
    private static final String DIFF_KEY_AD_REWARDS = "encrypted_ad_rewards";
    private static final String DIFF_KEY_REFERRAL_REWARDS = "encrypted_ref_rewards";
    private static final String KEY_LAST_MODIFIED = "last_modified";
    private static final String KEY_SECURE_SUB_EXPIRY = "secure_sub_expiry";

    private static final String KEY_plan_type = "plan_type";
    private static final String KEY_PremiumActivatedBy = "premium_activated_by";
    private static final String KEY_Full_Name = "fullName";
    private static final String KEY_PHONE_NUMBER = "phoneNumber";
    private static final String KEY_CountryCode = "CountryCode";
    private static final String KEY_UserUID = "userUID";
    private static final String KEY_SYNC_ACCOUNTS = "sync_last_time_accounts";
    private static final String KEY_SYNC_TRANSACTIONS = "sync_last_time_transactions";
    private static final String KEY_SYNC_CURRENCIES = "sync_last_time_currencies";
    private static final String KEY_SYNC_ACCOUNT_TYPES = "sync_last_time_account_types";
    private static final String KEY_SYNC_BUDGET_CATEGORIES = "sync_last_time_budget_categories";
    private static final String KEY_SYNC_BUDGET_LIMITS = "sync_last_time_budget_limits";
    private static final String KEY_SYNC_BUDGET_ENTRIES = "sync_last_time_budget_entries";
    // قيم مشوشة
    private static final int OBFUSCATED_FREE_LIMIT = SecurityUtils.ObfuscationHelper.obfuscateInt(100, 0);
    private static final int OBFUSCATED_MAX_DEVICES = SecurityUtils.ObfuscationHelper.obfuscateInt(2, 1);
    private static final int OBFUSCATED_PREMIUM_LIMIT = SecurityUtils.ObfuscationHelper.obfuscateInt(Integer.MAX_VALUE, 2);

    private final Context context;
    private static SecureLicenseManager instance;
    private SharedPreferences encryptedPrefs;
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;

    private SecureLicenseManager(Context context) {
        this.context = context;
        this.auth = FirebaseAuth.getInstance();
        this.firestore = FirebaseFirestore.getInstance();
        initializeSecurePreferences();
    }

    public static synchronized SecureLicenseManager getInstance(Context context) {
        if (instance == null) {
            instance = new SecureLicenseManager(context);
        }
        return instance;
    }

    // في SecureLicenseManager.java
    private static final String ENCRYPTED_LAST_SYNC = "bGFzdF9zeW5j"; // last_sync
    private static final String ENCRYPTED_PENDING_COUNT = "cGVuZGluZ19jb3VudA=="; // pending_count


    /**
     * حفظ وقت آخر مزامنة
     */
    public void setLastSyncTime(long timestamp) {
        try {
            String encryptedTimestamp = SecurityUtils.encryptText(context,String.valueOf(timestamp), 8);
            encryptedPrefs.edit()
                    .putString(new String(Base64.decode(ENCRYPTED_LAST_SYNC, Base64.DEFAULT)), encryptedTimestamp)
                    .apply();
        } catch (Exception e) {
            Log.e(TAG, "خطأ في حفظ وقت المزامنة", e);
        }
    }

    /**
     * الحصول على وقت آخر مزامنة
     */
    public long getLastSyncTime() {
        try {
            String encryptedValue = encryptedPrefs.getString(
                    new String(Base64.decode(ENCRYPTED_LAST_SYNC, Base64.DEFAULT)),
                    SecurityUtils.encryptText(context,"0", 8)
            );
            String decryptedValue = SecurityUtils.decryptText(context,encryptedValue, 8);
            return Long.parseLong(decryptedValue);
        } catch (Exception e) {
            return 0;
        }
    }
    /**
     * جلب آخر وقت مزامنة لمجموعة معينة
     */
    public long getLastSyncTime(String entityKey) {
        return encryptedPrefs.getLong(entityKey, 0L);
    }

    /**
     * تحديث وقت المزامنة بعد النجاح
     */
    public void updateLastSyncTime(String entityKey, long timestamp) {
        encryptedPrefs.edit().putLong(entityKey, timestamp).apply();
    }

    // دوال مساعدة للوصول السريع للمفاتيح
    public static String getAccountKey() { return KEY_SYNC_ACCOUNTS; }
    public static String getTransactionKey() { return KEY_SYNC_TRANSACTIONS; }
    public static String getCurrencyKey() { return KEY_SYNC_CURRENCIES; }
    public static String getAccountTypeKey() { return KEY_SYNC_ACCOUNT_TYPES; }
    public static String getBudgetCategoryKey() { return KEY_SYNC_BUDGET_CATEGORIES; }
    public static String getBudgetLimitKey() { return KEY_SYNC_BUDGET_LIMITS; }
    public static String getBudgetEntryKey() { return KEY_SYNC_BUDGET_ENTRIES; }
    /**
     * دالة لاسترجاع معرف المستخدم المخزن محلياً في حال انقطاع الاتصال
     * @param context سياق التطبيق للوصول للبيانات المخزنة
     * @return المعرف النصي أو null إذا لم يوجد
     */
    public static String getStoredUID(Context context) {
      SharedPreferences prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);

        return prefs.getString("userUID", null);
    }
    /**
     * حفظ عدد العمليات المنتظرة
     */
    public void setPendingTransactionsCount(int count) {
        try {
            String encryptedCount = SecurityUtils.encryptText(context,String.valueOf(count), 9);
            encryptedPrefs.edit()
                    .putString(new String(Base64.decode(ENCRYPTED_PENDING_COUNT, Base64.DEFAULT)), encryptedCount)
                    .apply();
        } catch (Exception e) {
            Log.e(TAG, "خطأ في حفظ عدد العمليات المنتظرة", e);
        }
    }

    /**
     * الحصول على عدد العمليات المنتظرة
     */
    public int getPendingTransactionsCount() {
        try {
            String encryptedValue = encryptedPrefs.getString(
                    new String(Base64.decode(ENCRYPTED_PENDING_COUNT, Base64.DEFAULT)),
                    SecurityUtils.encryptText(context,"0", 9)
            );
            String decryptedValue = SecurityUtils.decryptText(context,encryptedValue, 9);
            return Integer.parseInt(decryptedValue);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * تهيئة التخزين المشفر
     */
    private void initializeSecurePreferences() {
        try {
            // التحقق من سلامة البيئة
            if (!SecurityUtils.isSecureEnvironment()) {
                Log.w(TAG, "غير آمن: تم اكتشاف بيئة غير آمنة");
                // يمكن إضافة إجراءات إضافية هنا
            }

            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            encryptedPrefs = EncryptedSharedPreferences.create(
                    PREFS_NAME,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            Log.e(TAG, "خطأ في تهيئة التخزين المشفر", e);
            // استخدام SharedPreferences عادي كبديل (مع تحذير)
            encryptedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }


    // دالة مساعدة للحصول على قيمة int من التخزين المشفر
    public int getIntValue(String encryptedKey, int keyIndex, int defaultValue) {
        try {
            String encryptedValue = encryptedPrefs.getString(
                    new String(Base64.decode(encryptedKey, Base64.DEFAULT)),
                    SecurityUtils.encryptText(context,String.valueOf(defaultValue), keyIndex)
            );
            String decryptedValue = SecurityUtils.decryptText(context,encryptedValue, keyIndex);
            return Integer.parseInt(decryptedValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * الحصول على بيانات الترخيص كسلسلة نصية للتسجيل (لأغراض التصحيح فقط)
     */
    public String getLicenseDataForLogging() {
        try {
            int maxTransactions = getIntValue(ENCRYPTED_MAX_TRANSACTIONS, 3, 0);
            int transactionsCount = getIntValue(ENCRYPTED_TRANSACTIONS_COUNT, 4, 0);
            int adRewards = getIntValue(ENCRYPTED_AD_REWARDS, 5, 0);
            int referralRewards = getIntValue(ENCRYPTED_REFERRAL_REWARDS, 6, 0);
            boolean isPremium = isSecurePremium();

            return String.format("max_transactions=%d, transactions_count=%d, ad_rewards=%d, referral_rewards=%d, is_premium=%b",
                    maxTransactions, transactionsCount, adRewards, referralRewards, isPremium);
        } catch (Exception e) {
            return "خطأ في قراءة بيانات الترخيص";
        }
    }


    /**
     * توليد كود فريد آمن
     */
    private String generateSecureUniqueCode(FirebaseUser user) {
        String rawData = user.getUid() + user.getEmail() + System.currentTimeMillis() + getCurrentDeviceInfo().getDeviceId();
        String salt = "SecureSalt2024";
        return SecurityUtils.createSecureHash(rawData, salt);
    }

    /**
     * تحديث البيانات المحلية المشفرة
     */
    private void updateSecureLocalData(boolean isPremium, int quotesCount, int maxTransactions) {
        try {
            String encryptedPremium = SecurityUtils.encryptText(context,String.valueOf(isPremium), 0);
            String encryptedTransactionCount = SecurityUtils.encryptText(context,String.valueOf(quotesCount), 1);
            String encryptedMaxTransactions = SecurityUtils.encryptText(context,String.valueOf(maxTransactions), 2);

            encryptedPrefs.edit()
                    .putString(new String(Base64.decode(ENCRYPTED_KEY_PREMIUM, Base64.DEFAULT)), encryptedPremium)
                    .putString(new String(Base64.decode(ENCRYPTED_KEY_TRANSACTION_COUNT, Base64.DEFAULT)), encryptedTransactionCount)
                    .putString(new String(Base64.decode(ENCRYPTED_KEY_MAX_TRANSACTIONS, Base64.DEFAULT)), encryptedMaxTransactions)
                    .apply();
        } catch (Exception e) {
            Log.e(TAG, "خطأ في تحديث البيانات المحلية", e);
        }
    }

    /**
     * فحص حالة Premium
     */
    public boolean isSecurePremium() {
        try {
            String encryptedValue = encryptedPrefs.getString(
                    new String(Base64.decode(ENCRYPTED_KEY_PREMIUM, Base64.DEFAULT)),
                    SecurityUtils.encryptText(context,"false", 0)
            );
            String decryptedValue = SecurityUtils.decryptText(context,encryptedValue, 0);
            return Boolean.parseBoolean(decryptedValue);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * الحصول على عدد عروض الأسعار المتبقية
     */
    public int getSecureRemainingTransactions() {
        if (isSecurePremium()) {
            return SecurityUtils.ObfuscationHelper.deobfuscateInt(OBFUSCATED_PREMIUM_LIMIT, 2);
        }

        try {
            String encryptedCount = encryptedPrefs.getString(
                    new String(Base64.decode(ENCRYPTED_KEY_TRANSACTION_COUNT, Base64.DEFAULT)),
                    SecurityUtils.encryptText(context,"0", 1)
            );
            String encryptedMax = encryptedPrefs.getString(
                    new String(Base64.decode(ENCRYPTED_KEY_MAX_TRANSACTIONS, Base64.DEFAULT)),
                    SecurityUtils.encryptText(context,String.valueOf(getDeobfuscatedFreeLimit()), 2)
            );

            int currentCount = Integer.parseInt(SecurityUtils.decryptText(context,encryptedCount, 1));
            int maxTransactions = Integer.parseInt(SecurityUtils.decryptText(context,encryptedMax, 2));

            return Math.max(0, maxTransactions - currentCount);
        } catch (Exception e) {
            return 0;
        }
    }


    public String getUserType() {
        return encryptedPrefs.getString(KEY_USER_TYPE, "user");
    }

    public void setUserType(String userType) {
        encryptedPrefs.edit().putString(KEY_USER_TYPE, userType).apply();
    }
    public long getFstSubscribingDate() {
        return encryptedPrefs.getLong(KEY_fstSubscribingDate, 0);
    }

    public void setFstSubscribingDate(long fstSubscribingDate) {
        encryptedPrefs.edit().putLong(KEY_fstSubscribingDate, fstSubscribingDate).apply();
    }
public  boolean isUserAdmin( ){
    String userType = encryptedPrefs.getString(ENCRYPTED_KEY_USER_TYPE, "admin");
    return userType.equals(getUserType());

}
    private String convertTimestampToString(Object timestamp) {
        try {
            if (timestamp instanceof Timestamp) { return formatDate(((Timestamp) timestamp).toDate()); }
            else if (timestamp instanceof String) { return (String) timestamp; }
            else if (timestamp instanceof Date) { return formatDate((Date) timestamp); }
        } catch (Exception e) { Log.e("convertTimestampToString",e.toString()); }
        return "";
    }
    private Object last_login;
    public String getLast_login() { return convertTimestampToString(last_login); }
    public void setLast_login(Object created_at) { this.last_login = created_at; }
    private String formatDate(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss a z", Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(date);
    }
    public static String getCurrentLocalDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss a z", Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(new Date());
    }

    public void saveLicenseData(int maxTransactions, int transactionsCount,
                                int adRewards, int referralRewards, boolean isPremium,
                                long lastModified,Object last_login) {
        long localLastModified = getLastModified();

        if (lastModified > localLastModified) {
            encryptedPrefs.edit()
                    .putInt(KEY_MAX_TRANSACTIONS, maxTransactions)
                    .putInt(KEY_TRANSACTIONS_COUNT, transactionsCount)
                    .putInt(KEY_AD_REWARDS, adRewards)
                    .putInt(KEY_REFERRAL_REWARDS, referralRewards)
                    .putBoolean(KEY_IS_PREMIUM, isPremium)
                    .apply();
            setLast_login(last_login);
            Log.d(TAG, "تم حفظ بيانات الترخيص: " +
                    "transactions_count=" + transactionsCount +
                    ", ad_rewards=" + adRewards);
        }
    }


    public long getLastModified() {
        return encryptedPrefs.getLong(KEY_LAST_MODIFIED, 0);
    }

    // دالة لحفظ تاريخ الانتهاء (للخطط مسبقة الدفع)
    public void setSubscriptionExpiry(long timestamp) {
        if (encryptedPrefs != null) {
            encryptedPrefs.edit().putLong(KEY_SECURE_SUB_EXPIRY, timestamp).apply();
        }
    }
    public long getSubscriptionExpired() {
        assert encryptedPrefs != null;
        return   encryptedPrefs.getLong(KEY_SECURE_SUB_EXPIRY, 0);
    }
    // دالة للتحقق مما إذا كان الاشتراك قد انتهى محلياً
    public boolean isSubscriptionExpired() {
        if (encryptedPrefs != null) {
            long expiry = encryptedPrefs.getLong(KEY_SECURE_SUB_EXPIRY, 0);
            if (expiry == 0) return false; // لا يوجد تاريخ انتهاء (مدى الحياة أو تلقائي)
            return System.currentTimeMillis() > expiry;
        }
        return true;
    }

    public int getMaxTransactions() {
        return encryptedPrefs.getInt(KEY_MAX_TRANSACTIONS, 0);
    }

    public int getTransactionsCount() {
        return encryptedPrefs.getInt(KEY_TRANSACTIONS_COUNT, 0);
    }
    public int getAdRewards() {
        return encryptedPrefs.getInt(KEY_AD_REWARDS, 0);
    }
    public void setAdRewards(int adRewards) {
        encryptedPrefs.edit().putInt(KEY_AD_REWARDS, adRewards).apply();
    }

    public int getReferralRewards() {
        return encryptedPrefs.getInt(KEY_REFERRAL_REWARDS, 0);
    }

    public String getUserUID() { return encryptedPrefs.getString(KEY_UserUID, ""); }
    public void setUserUID(String userUID) { encryptedPrefs.edit().putString(KEY_UserUID, userUID).apply(); }


    public void setPlanType(String planType) { encryptedPrefs.edit().putString(KEY_plan_type, planType).apply(); }
    public String getPlanType() { return encryptedPrefs.getString(KEY_plan_type, ""); }

    public void setPremiumActivatedBy(String premiumActivatedBy) { encryptedPrefs.edit().putString(KEY_PremiumActivatedBy, premiumActivatedBy).apply(); }
    public String getPremiumActivatedBy() { return encryptedPrefs.getString(KEY_PremiumActivatedBy, ""); }

    public void setReferralRewards(int referralRewards) {
        encryptedPrefs.edit().putInt(KEY_REFERRAL_REWARDS, referralRewards).apply();
    }

    public int getDevicesNos() {
        return encryptedPrefs.getInt(KEY_DEVICES_NOS, 0);
    }

    public void setDevicesNos(int devicesNos) {
        encryptedPrefs.edit().putInt(KEY_DEVICES_NOS, devicesNos).apply();
    }
    public boolean isStartApp() {
        Log.d(TAG,"isStartApp: " + KEY_IS_START);
        return encryptedPrefs.getBoolean(KEY_IS_START, false);
    }

    public void setStartApp(boolean isStartApp) {
        Log.d(TAG,"setStartApp: " + isStartApp);
        encryptedPrefs.edit().putBoolean(KEY_IS_START, isStartApp).apply();
    }
    public boolean isPremium() {
        return encryptedPrefs.getBoolean(KEY_IS_PREMIUM, false);
    }

    public void setPremium(boolean isPremium) {
        encryptedPrefs.edit().putBoolean(KEY_IS_PREMIUM, isPremium).apply();
    }
    public void setDebtFeatureEnabled(boolean isDebtFeatureEnabled) {
            encryptedPrefs.edit().putBoolean(KEY_isDebtFeatureEnabled, isDebtFeatureEnabled).apply();

    }
    public boolean isDebtFeatureEnabled() {
        return encryptedPrefs.getBoolean(KEY_isDebtFeatureEnabled, false);
    }
    public String getPhoneNumber() { return encryptedPrefs.getString(KEY_PHONE_NUMBER, ""); }
    public void setPhoneNumber(String phoneNumber) { encryptedPrefs.edit().putString(KEY_PHONE_NUMBER, phoneNumber).apply(); }
    public String getCountryCode() { return encryptedPrefs.getString(KEY_CountryCode, ""); }
    public void setCountryCode(String countryCode) { encryptedPrefs.edit().putString(KEY_CountryCode, countryCode).apply(); }

    public String getFullName() { return encryptedPrefs.getString(KEY_Full_Name, ""); }
    public void setFullName(String fullName) { encryptedPrefs.edit().putString(KEY_Full_Name, fullName).apply(); }

    public void setLinkedAccount(boolean linkedAccount) {
        encryptedPrefs.edit().putBoolean(KEY_LinkedAccount, linkedAccount).apply();

    }
    public boolean isLinkedAccount() {
        return encryptedPrefs.getBoolean(KEY_LinkedAccount, false);
    }

    public boolean isGuest() {

        return encryptedPrefs.getBoolean(GUEST_USER, false);
    }

    public void setGuest(boolean isComplete) {
        encryptedPrefs.edit().putBoolean(GUEST_USER, isComplete).apply();
    }

    public String guestUID() {
        return encryptedPrefs.getString(GUEST_UID, "");
    }

    public void setGuestUID(String uid) {
        encryptedPrefs.edit().putString(GUEST_UID, uid).apply();
    }

    public String getEmail() {
        return encryptedPrefs.getString(EMAIL, "");
    }

    public void setEmail(String email) {
        encryptedPrefs.edit().putString(EMAIL, email).apply();
    }

    /**
     * هل تم استيراد إعدادات التطبيق من السيرفر مسبقاً؟
     */
    public boolean isAppConfigured() {
        return encryptedPrefs.getBoolean(ENCRYPTED_KEY_APP_CONFIGURED, false);
    }

    /**
     * حفظ البيانات الواردة من فايرستور بشكل مشفر
     */
    public void saveDynamicAppConfig(Map<String, Object> configData) {
        SharedPreferences.Editor editor = encryptedPrefs.edit();
        if (configData.containsKey("user_type")) {
            editor.putString(ENCRYPTED_KEY_USER_TYPE, (String) configData.get("user_type"));
        }
        if (configData.containsKey("admob_banner_id")) {
            editor.putString(ENCRYPTED_KEY_ADMOB_BANNER, (String) configData.get("admob_banner_id"));
        }
        if (configData.containsKey("admob_rewarded_id")) {
            editor.putString(ENCRYPTED_KEY_ADMOB_REWARDED, (String) configData.get("admob_rewarded_id"));
        }

        if (configData.containsKey("web_encryption_key")) {
            editor.putString(ENCRYPTED_KEY_WEB_KEYS, (String) configData.get("web_encryption_key"));
        }

        // تحويل قائمة الإيميلات إلى نص مفصول بفاصلة لتخزينه
        if (configData.containsKey("admin_emails")) {
            List<String> emails = (List<String>) configData.get("admin_emails");
            if (emails != null && !emails.isEmpty()) {
                editor.putString(ENCRYPTED_KEY_ADMIN_EMAILS, android.text.TextUtils.join(",", emails));
            }
        }
        if (configData.containsKey("app_sec_key")) {
            editor.putString(ENCRYPTED_KEY_APP_SEC_KEY, (String) configData.get("app_sec_key"));
        }
        if (configData.containsKey("secure_signature_hash")) {
            editor.putString(ENCRYPTED_KEY_SIG_HASH, (String) configData.get("secure_signature_hash"));
        }

        // حفظ مصفوفة مفاتيح التشفير الـ 3 (تحويلها لنص مفصول بفاصلة)
        if (configData.containsKey("encryption_keys")) {
            java.util.List<String> keys = (java.util.List<String>) configData.get("encryption_keys");
            if (keys != null && !keys.isEmpty()) {
                editor.putString(ENCRYPTED_KEY_ENC_KEYS, android.text.TextUtils.join(",", keys));
            }
        }
        // حفظ خريطة الأسعار بشكل ديناميكي
        if (configData.containsKey("product_prices")) {
            Object pricesObj = configData.get("product_prices");
            if (pricesObj instanceof java.util.Map) {
                java.util.Map<String, Object> prices = (java.util.Map<String, Object>) pricesObj;
                for (String productId : prices.keySet()) {
                    Object priceObj = prices.get(productId);
                    if (priceObj instanceof Number) {
                        editor.putLong("price_" + productId, ((Number) priceObj).longValue());
                    }
                }
            }
        }

        // حفظ تواريخ المراحل (v2 و v3)
        if (configData.containsKey("v2_start_date")) {
            Object v2Obj = configData.get("v2_start_date");
            if (v2Obj instanceof com.google.firebase.Timestamp) {
                editor.putLong(ENCRYPTED_KEY_V2_START_DATE, ((com.google.firebase.Timestamp) v2Obj).toDate().getTime());
          Log.d(TAG,"v2_start_date imported: "+ ((com.google.firebase.Timestamp) v2Obj).toDate().getTime());
            }
        }
        if (configData.containsKey("v3_start_date")) {
            Object v3Obj = configData.get("v3_start_date");
            if (v3Obj instanceof com.google.firebase.Timestamp) {
                editor.putLong(ENCRYPTED_KEY_V3_START_DATE, ((com.google.firebase.Timestamp) v3Obj).toDate().getTime());
            Log.d(TAG,"v3_start_date imported: "+ ((com.google.firebase.Timestamp) v3Obj).toDate().getTime());
            }
        }

        // حفظ الحد الأدنى للإصدار
        if (configData.containsKey("min_version")) {
            Object minV = configData.get("min_version");
            if (minV instanceof Number) {
                editor.putInt(ENCRYPTED_KEY_MIN_VERSION, ((Number) minV).intValue());
            }
        }
        // تأكيد نجاح التهيئة
        editor.putBoolean(ENCRYPTED_KEY_APP_CONFIGURED, true);
        editor.apply();
    }

    /**
     * جلب معرف بانر AdMob
     */
    public String getAdmobBannerId() {
        // ca-app-pub-2600356094241738/9216874064
        String obfuscatedDefault = "Y2EtYXBwLXB1Yi0yNjAwMzU2MDk0MjQxNzM4LzkyMTY4NzQwNjQ=";
        String defaultVal = new String(android.util.Base64.decode(obfuscatedDefault, android.util.Base64.DEFAULT));
        return encryptedPrefs.getString(ENCRYPTED_KEY_ADMOB_BANNER, defaultVal);
    }

    /**
     * جلب معرف إعلان المكافأة AdMob
     */
    public String getAdmobRewardedId() {
        // ca-app-pub-2600356094241738/3368204179
        String obfuscatedDefault = "Y2EtYXBwLXB1Yi0yNjAwMzU2MDk0MjQxNzM4LzMzNjgyMDQxNzk=";
        String defaultVal = new String(android.util.Base64.decode(obfuscatedDefault, android.util.Base64.DEFAULT));
       Log.d(TAG,"getAdmobRewardedId: "+ defaultVal);
        return encryptedPrefs.getString(ENCRYPTED_KEY_ADMOB_REWARDED, defaultVal);
    }

    /**
     * جلب مفتاح أمان التطبيق الأساسي
     */
    public String getAppSecKey() {
        // m9X#G8!aP1zQ@7sT4vR&kD2hWfN$0yL
        String obfuscatedDefault = "bTlYI0c4IWFQMXpRQDdzVDR2UiZrRDJoV2ZOJDB5TA==";
        String defaultVal = new String(android.util.Base64.decode(obfuscatedDefault, android.util.Base64.DEFAULT));
        return encryptedPrefs.getString(ENCRYPTED_KEY_APP_SEC_KEY, defaultVal);
    }

    /**
     * جلب مفتاح تشفير الويب
     */
    public String getWebEncryptionKey() {
        // المفتاح الأصلي: IdMuO1peUX*6nE#s
        String obfuscatedKey = "SWRNdU8xcGVVWCo2bkUjcw==";
        Log.d(TAG,"getWebEncryptionKey: "+ obfuscatedKey);
        // فك التشفير في الذاكرة الحية فقط وقت الاستدعاء
        return new String(android.util.Base64.decode(obfuscatedKey, android.util.Base64.DEFAULT));
    }

    /**
     * جلب بصمة التوقيع الآمنة (Hash)
     */
    public String getSecureSignatureHash() {
        // 1C:36:97:38:51:F5:85:01:A8:EA:6C:94:15:51:1C:EC:7A:0F:AB:1A:F8:35:73:2A:65:81:BA:62:67:27:39:0D
        String obfuscatedDefault = "MUM6MzY6OTc6Mzg6NTE6RjU6ODU6MDE6QTg6RUE6NkM6OTQ6MTU6NTE6MUM6RUM6N0E6MEY6QUI6MUE6Rjg6MzU6NzM6MkE6NjU6ODE6QkE6NjI6Njc6Mjc6Mzk6MEQ=";
        String defaultVal = new String(android.util.Base64.decode(obfuscatedDefault, android.util.Base64.DEFAULT));
        return encryptedPrefs.getString(ENCRYPTED_KEY_SIG_HASH, defaultVal);
    }

    /**
     * جلب مفاتيح التشفير الثلاثة
     */
    public String getEncryptionKey(int index) {
        // لأن المفاتيح أصلاً مشفرة بـ Base64، لا نحتاج لتشفيرها مجدداً.
        // النص الافتراضي عبارة عن المفاتيح الثلاثة مفصولة بفاصلة.
        String defaultVal = "Z3RZYnE3dzlPUDJLM2c4Vg==,cThYSjV2TnpNMWU0UldwQQ==,MzlIbXVLN1Q0eUVSWjJvRA==";
        String keysStr = encryptedPrefs.getString(ENCRYPTED_KEY_ENC_KEYS, defaultVal);

        String[] keys = keysStr.split(",");
        return keys[index % keys.length];
    }

    /**
     * استدعاء قائمة إيميلات الإدارة
     */
    public java.util.List<String> getAdminEmails() {
        String emailsStr = encryptedPrefs.getString(ENCRYPTED_KEY_ADMIN_EMAILS, "");
        if (emailsStr.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        return java.util.Arrays.asList(emailsStr.split(","));
    }
    public boolean isAdminEmail() {
        String currentEmail = getEmail();
        if (currentEmail != null) {
            // جلب قائمة الإيميلات المصرح لها من التخزين الآمن (التي تم جلبها من فايرستور سابقاً)
            java.util.List<String> adminEmails = getAdminEmails();
Log.d(TAG,"adminEmails: "+ adminEmails);
            return !adminEmails.contains(currentEmail);
        }
        return true;
    }


    /**
     * جلب سعر منتج معين
     */
    public long getProductPrice(String productId, long defaultPrice) {
        return encryptedPrefs.getLong("price_" + productId, defaultPrice);
    }

    /**
     * جلب تاريخ بداية المرحلة v2
     */
    public long getV2StartDate() {
        return encryptedPrefs.getLong(ENCRYPTED_KEY_V2_START_DATE, 0);
    }

    /**
     * جلب تاريخ بداية المرحلة v3
     */
    public long getV3StartDate() {
        return encryptedPrefs.getLong(ENCRYPTED_KEY_V3_START_DATE, 0);
    }

    /**
     * جلب الحد الأدنى للإصدار
     */
    public int getMinVersion() {
        return encryptedPrefs.getInt(ENCRYPTED_KEY_MIN_VERSION, 0);
    }
    // دوال التحديث
    public void incrementTransactionsCount() {
        int currentCount = getTransactionsCount();
        encryptedPrefs.edit()
                .putInt(KEY_TRANSACTIONS_COUNT, currentCount + 1)
                .apply();
        incrementTransactionsCountFirestore();
    }
    private void incrementTransactionsCountFirestore() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null && !isGuest()) {
            Log.e(TAG, "User is not logged in and not a guest");
            return;
        }

        String users, uid;
        if (isGuest()) {
            users = "guests";
            uid = guestUID();
        } else {
            users = "users";

            if (currentUser != null) {
                uid = currentUser.getUid();
            } else {
                SharedPreferences prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
                // 🔥 تعديل جوهري: إذا كان الأوث محجوب، استخدم الـ UID المحفوظ محلياً
                uid = prefs.getString("userUID", "");
                Log.i(TAG, "Auth failed (possibly blocked), using local UID: " + uid);
            }
        }
        if(uid == null || users == null) return;
        DocumentReference userDocRef = firestore.collection(users).document(uid);
        Map<String, Object> updates = new HashMap<>();
        updates.put("transactions_count", FieldValue.increment(1));
        updates.put("lastModified", System.currentTimeMillis());
        userDocRef.update(updates);
        Log.e(TAG, "incrementTransactionsCountFirestore");
    }
    public void decrementAdRewards() {
        int currentRewards = getAdRewards();
        if (currentRewards > 0) {
            encryptedPrefs.edit()
                    .putInt(KEY_AD_REWARDS, currentRewards - 1)
                    .apply();
            String users, uid;
            if (isGuest()) {
                users = "guests";
                uid = guestUID();
            } else {
                users = "users";
                uid = AppAuth.getUserID(context);
            }
            DocumentReference userDocRef = firestore.collection(users).document(uid);
            userDocRef.update("ad_rewards", FieldValue.increment(-1));
        }
    }
    public void decrementReferralRewards() {
        int currentReferralRewards = getReferralRewards();
        if (currentReferralRewards > 0) {
            encryptedPrefs.edit()
                    .putInt(KEY_REFERRAL_REWARDS, currentReferralRewards - 1)
                    .apply();
            String users, uid;
            if (isGuest()) {
                users = "guests";
                uid = guestUID();
            } else {
                users = "users";
                uid = AppAuth.getUserID(context);
            }
            if(uid == null) return;
            DocumentReference userDocRef = firestore.collection(users).document(uid);
            userDocRef.update("referral_rewards", FieldValue.increment(-1));
        }
    }

    /**
     * الحصول على معلومات الجهاز الحالي
     */
    private DeviceInfo getCurrentDeviceInfo() {
        DeviceInfo deviceInfo = new DeviceInfo();
        deviceInfo.setDeviceId(SecurityUtils.createSecureHash(
                android.provider.Settings.Secure.getString(context.getContentResolver(),
                        android.provider.Settings.Secure.ANDROID_ID), "DeviceSalt"));
        deviceInfo.setDeviceName(android.os.Build.MODEL);
        deviceInfo.setDeviceModel(android.os.Build.MODEL);
        deviceInfo.setAndroidVersion(android.os.Build.VERSION.RELEASE);
        return deviceInfo;
    }

    /**
     * الحصول على الحد المجاني بعد إلغاء التشويش
     */
    private int getDeobfuscatedFreeLimit() {
        return SecurityUtils.ObfuscationHelper.deobfuscateInt(OBFUSCATED_FREE_LIMIT, 0);
    }


}