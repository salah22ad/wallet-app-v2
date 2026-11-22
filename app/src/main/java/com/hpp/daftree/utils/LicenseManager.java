package com.hpp.daftree.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.hpp.daftree.models.AppAuth;
import com.hpp.daftree.R;
import com.hpp.daftree.database.DeviceInfo;
import com.hpp.daftree.database.User;
import com.hpp.daftree.syncmanagers.SyncPreferences;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import androidx.security.crypto.MasterKey;

public class LicenseManager {
    private static final String TAG = "LicenseManager";
    private static final String PREFS_NAME = "secure_license_prefs";
    private static final String KEY_IS_PREMIUM = "is_premium";
    public static final String KEY_DEVICE_ID = "device_id";
    private static final String USERS_COLLECTION = "users";
    private static final String PREFS_DAILY_LIMIT = "daily_limit_prefs";
    public static final int FREE_TRANSACTION_LIMIT = 1000;
    private SmartLicenseCallback currentCallback;
    private List<DeviceInfo> lastDeviceList = new ArrayList<>();
    private boolean lastIsAuthorized = true;
    private boolean lastIsExpired = false;
    private boolean lastLimitExceeded = false;
    private boolean isDataLoadedAtLeastOnce = false;
    public static final int Successful_Referrals = 0;
    public static final int MAX_DEVICES = 1; // حد الأجهزة للمستخدم المجاني والمميز
    public static final int AD_REWARD_TRANSACTIONS = 3;
    public static final int REFERRAL_REWARD_REFERRER = 5;
    public static final int REFERRAL_REWARD_REFEREE = 3;
    public static final int DAILY_FREE_LIMIT_FOR_EXPIRED_USERS = 3;

    public static final int DAILY_FREE_LIMIT_FOR_retrieve_my_accounts_USERS = 3;
    public static final int FREE_GUEST_TRANSACTION_DAILY = 10;

    private final Context context;
    private final SharedPreferences securePrefs;
    private final FirebaseFirestore firestore;
    private final SharedPreferences dailyLimitPrefs;
    private final FirebaseAuth auth;
    private AuthStateListener authStateListener;
    private ListenerRegistration licenseListener;
    private boolean isActivityLogged = false;
    private static LicenseManager instance;

    public interface AuthStateListener {
        void onSignedOut();
    }

    public LicenseManager(Context context) {
        this.context = context;
        this.firestore = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
        this.securePrefs = initializeSecurePrefs();
        this.dailyLimitPrefs = context.getSharedPreferences(PREFS_DAILY_LIMIT, Context.MODE_PRIVATE); // <-- جديد

    }

    public static synchronized LicenseManager getInstance(Context context) {
        if (instance == null) {
            instance = new LicenseManager(context);

        }
        return instance;
    }

    private SharedPreferences initializeSecurePrefs() {
        try {
//            MasterKey masterKey = new MasterKey.Builder(context)
//                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
//                    .build();
//
//            return EncryptedSharedPreferences.create(
//                    context,
//                    PREFS_NAME,
//                    masterKey,
//                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
//                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
//            );
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        } catch (RuntimeException e) {
            Log.e(TAG, "فشل في إنشاء التخزين المشفر، سيتم استخدام التخزين العادي.", e);
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void setPremiumStatus(boolean isPremium) {
        securePrefs.edit().putBoolean(KEY_IS_PREMIUM, isPremium).apply();
    }

    public String getDeviceId() {
        String deviceId = securePrefs.getString(KEY_DEVICE_ID, null);
        if (deviceId == null) {
            String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            String rawId = androidId + Build.MODEL + Build.MANUFACTURER + Build.FINGERPRINT;
            deviceId = hashString(rawId);
            securePrefs.edit().putString(KEY_DEVICE_ID, deviceId).apply();
        }
        return deviceId;
    }

    public DeviceInfo getCurrentDeviceInfo() {
        DeviceInfo deviceInfo = new DeviceInfo();
        deviceInfo.setDeviceId(getDeviceId());
        deviceInfo.setDeviceName(Build.MANUFACTURER + " " + Build.MODEL);
        deviceInfo.setDeviceModel(Build.MODEL);
        deviceInfo.setAndroidVersion("Android " + Build.VERSION.RELEASE);
        deviceInfo.setRegisteredAt(DeviceInfo.getCurrentLocalDateTime());
        deviceInfo.setLastActiveAt(DeviceInfo.getCurrentLocalDateTime());
        deviceInfo.setActive(true);
        return deviceInfo;
    }

    private String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "SHA-256 algorithm not available", e);
            return String.valueOf(input.hashCode());
        }
    }

    /**
     * الدالة المحورية الجديدة للتحقق من إمكانية إنشاء عملية.
     *
     * @param user كائن المستخدم الحالي من Firestore.
     * @return true إذا كان يمكنه إنشاء عملية جديدة.
     */
    private static final String GUEST_UsageCount = "usageCount";

    public int getGuestUsageCount() {
        return securePrefs.getInt(GUEST_UsageCount, 1);
    }

    public void setGuestUsageCount(int usageCount) {
        securePrefs.edit().putInt(GUEST_UsageCount, usageCount).apply();
    }

    public boolean canCreateTransaction() {
        // القراءة من التخزين المشفر بدلاً من User object
        SecureLicenseManager licenseData = SecureLicenseManager.getInstance(context);

        int transactionsCount = licenseData.getTransactionsCount();
        int adRewards = licenseData.getAdRewards();
        int referralRewards = licenseData.getReferralRewards();
        int maxTransactions = licenseData.getMaxTransactions();
        boolean isPremium = licenseData.isPremium();
        boolean isGuest = licenseData.isGuest();
        int freeLimit;
        if (getGuestUsageCount() < 3) {
            freeLimit = maxTransactions;
        } else {
            freeLimit = 10;
        }
        Log.d(TAG, "بيانات الترخيص: transactions_count=" + transactionsCount +
                ", ad_rewards=" + adRewards +
                ", referral_rewards=" + referralRewards);

        if (isPremium) {
            return true;
        }
        if (transactionsCount < freeLimit ) {
            return true;
        } else if (adRewards > 0) {
            licenseData.decrementAdRewards();
            return true;
        } else if (referralRewards > 0) {
            licenseData.decrementReferralRewards();
            return true;
        } else {
            incrementDailyCounter();
        }
        return checkDailyLimit();
    }

    public boolean canRetrieveMyAccounts() {
        // القراءة من التخزين المشفر بدلاً من User object
        SecureLicenseManager licenseData = SecureLicenseManager.getInstance(context);
        int adRewards = licenseData.getAdRewards();
        int referralRewards = licenseData.getReferralRewards();
        boolean isPremium = licenseData.isPremium();

        if (isPremium) {
            return true;
        }
        if (adRewards > 0) {
            licenseData.decrementAdRewards();
            return true;
        } else if (referralRewards > 0) {
            licenseData.decrementReferralRewards();
            return true;
        } else {
            incrementRetrieveMyAccountsDailyCounter();
        }
        return checkRetrieveMyAccountsDailyLimit();
    }

    private void incrementRetrieveMyAccountsDailyCounter() {
        resetRetrieveMyAccountsDailyCounter();
        int currentCount = dailyLimitPrefs.getInt("retrieve_count", 0);
        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        dailyLimitPrefs.edit()
                .putInt("retrieve_count", currentCount + 1)
                .putString("retrieve_date", todayDate)
                .apply();
    }

    public void resetRetrieveMyAccountsDailyCounter() {
        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String lastUsedDate = dailyLimitPrefs.getString("retrieve_date", "");
        if (!todayDate.equals(lastUsedDate)) {
            dailyLimitPrefs.edit()
                    .putInt("retrieve_count", 0)
                    .putString("retrieve_date", todayDate)
                    .apply();
            Log.d(TAG, "تم تصفير عداد الحصة اليومية لليوم الجديد.");
        }
    }

    public boolean checkRetrieveMyAccountsDailyLimit() {
        resetDailyCounter();
        int dailyCount = dailyLimitPrefs.getInt("retrieve_count", 0);
        return dailyCount < DAILY_FREE_LIMIT_FOR_retrieve_my_accounts_USERS;
    }

    public void incrementTransactionCount() {
        SecureLicenseManager.getInstance(context).incrementTransactionsCount();
    }

    /**
     * يتحقق مما إذا كان المستخدم لا يزال يملك عمليات في حصته اليومية.
     *
     * @return true إذا كان بإمكانه إجراء عملية.
     */
    public boolean checkDailyLimit() {
        resetDailyCounter();
        int dailyCount = dailyLimitPrefs.getInt("count", 0);
        Log.d(TAG, "Daily count: " + dailyCount + "/" + DAILY_FREE_LIMIT_FOR_EXPIRED_USERS);
        return dailyCount < DAILY_FREE_LIMIT_FOR_EXPIRED_USERS;
    }

    /**
     * يزيد عداد العمليات اليومية بواحد.
     */
    private void incrementDailyCounter() {
        resetDailyCounter();
        int currentCount = dailyLimitPrefs.getInt("count", 0);
        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        dailyLimitPrefs.edit()
                .putInt("count", currentCount + 1)
                .putString("date", todayDate)
                .apply();
    }

    /**
     * يقوم بتصفير عداد الحصة اليومية إذا كان التاريخ الحالي مختلفًا عن تاريخ آخر عملية.
     */

    public void resetDailyCounter() {
        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String lastUsedDate = dailyLimitPrefs.getString("date", "");
        if (!todayDate.equals(lastUsedDate)) {
            dailyLimitPrefs.edit()
                    .putInt("count", 0)
                    .putString("date", todayDate)
                    .apply();
            Log.d(TAG, "تم تصفير عداد الحصة اليومية لليوم الجديد.");
        }
    }

    public void addAdRewardTransactions(User user) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) return;
        DocumentReference userDocRef = firestore.collection(USERS_COLLECTION).document(firebaseUser.getUid());
        userDocRef.update("ad_rewards", FieldValue.increment(AD_REWARD_TRANSACTIONS));
    }

    public CompletableFuture<LicenseCheckResult> checkLicense() {
        CompletableFuture<LicenseCheckResult> future = new CompletableFuture<>();
        // FirebaseUser firebaseUser = auth.getCurrentUser();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String uid;
        if (currentUser != null) {
            uid = currentUser.getUid();
        } else {
            SharedPreferences prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
            // 🔥 تعديل جوهري: إذا كان الأوث محجوب، استخدم الـ UID المحفوظ محلياً
            uid = prefs.getString("userUID", "");
            Log.i(TAG, "Auth failed (possibly blocked), using local UID: " + uid);
        }


        DocumentReference userDocRef = firestore.collection(USERS_COLLECTION).document(uid);
        String currentDeviceId = getDeviceId();

        userDocRef.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || !task.getResult().exists()) {
                future.complete(new LicenseCheckResult(false, context.getString(R.string.fail_read_user_data), null, false, false));
                return;
            }
            User user = task.getResult().toObject(User.class);
            if (user == null) {
                future.complete(new LicenseCheckResult(false, context.getString(R.string.fail_read_user_data), null, false, false));
                return;
            }
            SecureLicenseManager secure = SecureLicenseManager.getInstance(context);
            setPremiumStatus(user.isIs_premium());
            secure.setPremium(user.isIs_premium());
            secure.setDevicesNos(user.getDevices().size());
            // ✅ التحقق الأساسي: هل الجهاز الحالي موجود في قائمة الأجهزة المرخصة؟
            if (user.getDevices().containsKey(currentDeviceId)) {
                // نعم، الجهاز مرخص. قم بتحديث نشاطه.
                userDocRef.update("devices." + currentDeviceId + ".lastActiveAt", DeviceInfo.getCurrentLocalDateTime(),
                        "devices." + currentDeviceId + ".active", true);
                future.complete(new LicenseCheckResult(true, "الجهاز مرخص.", user, false, true));
            } else {
                // لا، الجهاز غير موجود في القائمة.
                boolean hasSpace = user.getDevices().size() < MAX_DEVICES;

                // إضافة تحقق إضافي: هل تم تجاوز الحد الأقصى للأجهزة؟
                boolean deviceLimitExceeded = user.getDevices().size() >= 2;//MAX_DEVICES;

                future.complete(new LicenseCheckResult(true, context.getString(R.string.device_unlecinced), user, deviceLimitExceeded, false));
            }
        });

        return future;
    }

    public static String getDeviceId(Context context) {
        // طريقة بديلة للحصول على deviceId
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    public Task<Void> removeDevice(String deviceId) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            return Tasks.forException(new Exception(context.getString(R.string.ar_long_text_15)));
        }
        DocumentReference userDocRef = firestore.collection(USERS_COLLECTION).document(firebaseUser.getUid());
        // الطريقة الآمنة لحذف عنصر من خريطة
        return userDocRef.update("devices." + deviceId, FieldValue.delete());
    }

    public void setAuthStateListener(AuthStateListener listener) {
        this.authStateListener = listener;
    }

    public void signOutAndClearData() {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser != null) {
            String deviceId = getDeviceId();
            firestore.collection(USERS_COLLECTION).document(firebaseUser.getUid())
                    .update("devices." + deviceId + ".active", false);
        }
        auth.signOut();
        securePrefs.edit().clear().apply();
        Log.d(TAG, "تم تسجيل الخروج ومسح البيانات المحلية.");
        if (authStateListener != null) authStateListener.onSignedOut();
    }

    public void clearDeviceData() {
        try {
            securePrefs.edit().remove(KEY_DEVICE_ID).apply();

            // التحديث الجديد لـ clearDeviceData
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            SharedPreferences encryptedPrefs = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            encryptedPrefs.edit().clear().apply();

            Log.d(TAG, "تم مسح بيانات الجهاز والترخيص بنجاح");
        } catch (Exception e) {
            Log.e(TAG, "خطأ في مسح بيانات الجهاز", e);
        }

    }

    public interface SmartLicenseCallback {
        void onInitialLoadComplete(boolean isExpired, boolean isAuthorized, boolean limitExceeded, List<DeviceInfo> devices);

        void onDeviceRemoved();
    }

    public void startSmartLicenseListener(Context context, boolean isGuest, SmartLicenseCallback callback) {
        // 🔥 1. دائماً نحدث المرجع للكولباك النشط لتجنب استدعاء شاشات ميتة
        this.currentCallback = callback;

        if (licenseListener != null) {
            Log.d(TAG, "مستمع الترخيص يعمل بالفعل في الخلفية. تزويد الشاشة الحالية بآخر البيانات فوراً.");
            // 🔥 2. إذا كانت البيانات قد تم تحميلها سابقاً، نغذي الشاشة الجديدة بها فوراً لتحديث الواجهة بدون انتظار
            if (isDataLoadedAtLeastOnce && callback != null) {
                callback.onInitialLoadComplete(lastIsExpired, lastIsAuthorized, lastLimitExceeded, lastDeviceList);
            }
            return;
        }

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        SecureLicenseManager secureManager = SecureLicenseManager.getInstance(context);
        Log.e(TAG, "isStartApp: " + secureManager.isStartApp() + "\n" +
                "DevicesNos(): " + secureManager.getDevicesNos());
        if (!secureManager.isStartApp() && secureManager.getDevicesNos() < 2) return;
        String usersCollection = isGuest ? "guests" : "users";
        String uid = isGuest ? secureManager.guestUID() :
                (FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getUid() : AppAuth.getUserID(context));

        if (uid == null || uid.trim().isEmpty()) return;
        secureManager.setStartApp(false);
        DocumentReference userDocRef = firestore.collection(usersCollection).document(uid);
        String currentDeviceId = getCurrentDeviceInfo().getDeviceId();

        licenseListener = userDocRef.addSnapshotListener((snapshot, e) -> {
            if (e != null) {
                Log.e(TAG, "License listener failed", e);
                return;
            }
            boolean isExpired = false;
            Log.e(TAG, "start Smart License Listener");
            if (snapshot != null && snapshot.exists()) {
                // --- استخراج البيانات الخام مباشرة (تتجاوز ProGuard) ---
                boolean isPremium = Boolean.TRUE.equals(snapshot.getBoolean("is_premium"));
                Date firestoreDate = snapshot.getDate("subscription_expiry");
                //4893242085000L ----- 17 مايو 2125
                long expiryValue = (firestoreDate != null) ? firestoreDate.getTime() : 0L;
                long currentTime = System.currentTimeMillis();
                String planExpired = snapshot.getString("subscriptionStatus") != null ? snapshot.getString("subscriptionStatus") : "";
                if (isPremium) {
                    assert planExpired != null;
                    if (planExpired.equals("expired")) {
                        isPremium = false;
                        setPremiumStatus(false);
                        isExpired = true;
                        Map<String, Object> updateMap = new HashMap<>();
                        updateMap.put("is_premium", false);
                        updateMap.put("plan_type", "free");
                        updateMap.put("subscriptionStatus", "expired");
                        updateMap.put("subscription_expiry", null);
                        updateMap.put("subscription_start", null);
                        updateMap.put("premium_activated_at", null);
                        updateMap.put("premium_activated_by", null);
                        updateMap.put("premium_activated_byName", null);
                        FirebaseFirestore.getInstance().collection("users").document(uid)
                                .set(updateMap, SetOptions.merge());
                    }
                }
                int maxTransactions = snapshot.getLong("max_transactions") != null ? snapshot.getLong("max_transactions").intValue() : 0;
                int transactionsCount = snapshot.getLong("transactions_count") != null ? snapshot.getLong("transactions_count").intValue() : 0;
                int adRewards = snapshot.getLong("ad_rewards") != null ? snapshot.getLong("ad_rewards").intValue() : 0;
                int referralRewards = snapshot.getLong("referral_rewards") != null ? snapshot.getLong("referral_rewards").intValue() : 0;
                long lastModified = snapshot.contains("lastModified") ? snapshot.getLong("lastModified") : currentTime;

                String userType = snapshot.getString("userType");
                secureManager.setUserType(userType);
                new SyncPreferences(context).setKeyUserType(Objects.requireNonNullElse(userType, "user"));
                      long fstSubscribingDate = (snapshot.getDate("first_subscription_date") != null) ? snapshot.getDate("first_subscription_date").getTime() : 0L;
                secureManager.setFstSubscribingDate(fstSubscribingDate);
                Log.d(TAG, "first_subscription_date: " + fstSubscribingDate);
                if (snapshot.contains("isDebtFeatureEnabled")) {
                    boolean isDebtFeatureEnabled = snapshot.getBoolean("isDebtFeatureEnabled") != null ? snapshot.getBoolean("isDebtFeatureEnabled") : false;
                    boolean isLinked = snapshot.getBoolean("isLinked") != null ? snapshot.getBoolean("isLinked") : false;

                    secureManager.setDebtFeatureEnabled(isDebtFeatureEnabled);
                    secureManager.setLinkedAccount(isLinked);
                    secureManager.setFullName(snapshot.getString("fullName") != null ? snapshot.getString("fullName") : "");
                    secureManager.setPhoneNumber(snapshot.getString("phoneNumber") != null ? snapshot.getString("phoneNumber") : "");
                    secureManager.setCountryCode(snapshot.getString("countryCode") != null ? snapshot.getString("countryCode") : "");
                } else {
                    secureManager.setLinkedAccount(false);
                    secureManager.setDebtFeatureEnabled(false);
                }
                String premiumActivatedBy = snapshot.getString("premium_activated_by") != null ? snapshot.getString("premium_activated_by") : "";
                Object last_login = snapshot.getString("last_login") != null ? snapshot.getString("last_login") : secureManager.getLast_login();
                secureManager.setPremiumActivatedBy(snapshot.getString("premium_activated_by") != null ? snapshot.getString("premium_activated_by") : "");
                secureManager.setPlanType(snapshot.getString("plan_type") != null ? snapshot.getString("plan_type") : "free");
                secureManager.setSubscriptionExpiry(expiryValue);
                secureManager.setPremium(isPremium);
                secureManager.setEmail(snapshot.getString("email") != null ? snapshot.getString("email") : "");
                secureManager.saveLicenseData(maxTransactions, transactionsCount, adRewards, referralRewards, isPremium, lastModified, last_login);

                Log.e(TAG, "premium_activated_by: " + premiumActivatedBy + "\n" +
                        "premiumActivatedBy: " + secureManager.getPremiumActivatedBy());
                // --- التحقق من الأجهزة برمجياً ---
                Map<String, Object> devicesMap = (Map<String, Object>) snapshot.get("devices");
                List<DeviceInfo> deviceList = new ArrayList<>();
                boolean isAuthorized = true;
                boolean limitExceeded = false;
                boolean isDeviceFound = false;
                if (devicesMap != null) {
                    secureManager.setDevicesNos(devicesMap.size());
                    for (Map.Entry<String, Object> entry : devicesMap.entrySet()) {
                        DeviceInfo info = new DeviceInfo();
                        info.setDeviceId(entry.getKey());
                        if (entry.getValue() instanceof Map) {
                            Map<String, Object> devData = (Map<String, Object>) entry.getValue();
                            if (devData.containsKey("deviceName"))
                                info.setDeviceName((String) devData.get("deviceName"));
                            if (devData.containsKey("deviceModel"))
                                info.setDeviceModel((String) devData.get("deviceModel"));
                        }
                        deviceList.add(info);

                        if (entry.getKey().equals(currentDeviceId)) {
                            isDeviceFound = true;
                        }
                    }
                } else {
                    secureManager.setDevicesNos(0);
                }

                if (!isGuest) {
                    if (!isDeviceFound) {
                        isAuthorized = false;
                        limitExceeded = devicesMap != null && devicesMap.size() >= 3;

                        if (isActivityLogged && currentCallback != null) {
                            currentCallback.onDeviceRemoved();
                            return;
                        }
                    }
                }

                // 🔥 3. حفظ الحالة الحالية في المتغيرات الكلاسية لتغذية الشاشات المستقبلية فوراً
                this.lastDeviceList = deviceList;
                this.lastIsAuthorized = isAuthorized;
                this.lastLimitExceeded = limitExceeded;
                this.isDataLoadedAtLeastOnce = true;
                this.lastIsExpired = isExpired;

                // 🔥 4. استدعاء الكولباك عبر المرجع المحدث والنشط حصراً
                if (currentCallback != null) {
                    currentCallback.onInitialLoadComplete(isExpired, isAuthorized, limitExceeded, deviceList);
                }
            }
        });
    }

    public void stopAndResetLicenseListener() {
        if (licenseListener != null) {
            licenseListener.remove();
            licenseListener = null;
        }
        isActivityLogged = false;
        currentCallback = null;
        isDataLoadedAtLeastOnce = false;
        Log.d(TAG, "تم إيقاف وتصفير مستمع الترخيص.");
    }

    public static class LicenseCheckResult {
        public final boolean success;
        public final String message;
        public final User user;
        public final boolean deviceLimitExceeded;
        public final boolean isCurrentDeviceAuthorized;

        public LicenseCheckResult(boolean success, String message, User user, boolean deviceLimitExceeded, boolean isCurrentDeviceAuthorized) {
            this.success = success;
            this.message = message;
            this.user = user;
            this.deviceLimitExceeded = deviceLimitExceeded;
            this.isCurrentDeviceAuthorized = isCurrentDeviceAuthorized;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public User getUser() {
            return user;
        }

        public boolean isDeviceLimitExceeded() {
            return deviceLimitExceeded;
        }

        public boolean isPremium() {
            return user != null && user.isIs_premium();
        }

        public boolean isCurrentDeviceAuthorized() {
            return isCurrentDeviceAuthorized;
        }
    }
}
