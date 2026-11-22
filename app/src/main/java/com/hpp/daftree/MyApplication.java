package com.hpp.daftree;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.facebook.FacebookSdk;
import com.facebook.appevents.AppEventsLogger;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.common.ConnectionResult;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.PersistentCacheSettings;
import com.hpp.daftree.dailyreminder.DailyReminderManager;
import com.hpp.daftree.dailyreminder.DailyReminderScheduler;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.database.User;
import com.hpp.daftree.helpers.LanguageManager;
import com.hpp.daftree.models.AppAuth;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.notifications.NotificationHelper;
import com.hpp.daftree.syncmanagers.FirestoreSyncManager;
import com.hpp.daftree.syncmanagers.SyncPreferences;
import com.hpp.daftree.syncmanagers.SyncWorker;
import com.hpp.daftree.ui.BaseActivity;
import com.hpp.daftree.ui.LoginActivity;
import com.hpp.daftree.utils.CrashHandler;
import com.hpp.daftree.utils.GoogleAuthHelper;
import com.hpp.daftree.utils.SecureLicenseManager;
import com.hpp.daftree.utils.VersionManager;
import com.hpp.daftree.BuildConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.google.android.gms.common.GoogleApiAvailability;

import androidx.work.Configuration.Provider;

// فعّل الاستيراد التالي مؤقتاً عند الحاجة لتجربة App Check في وضع Debug (مع إزالة التعليق عن المكتبة في app/build.gradle.kts)
// import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;

public class MyApplication extends Application implements Provider {
    private static final String TAG = "MyApplication";
    public static boolean isProVersion = false;

    public static String defaultCurrencyName;
    public static String defaultCurrencySymbol;
    public static String defaultCurrencyCode;

    private User currentUserProfile;
    private SyncPreferences sharedPreferences;
    private DaftreeRepository repository;
    private GoogleAuthHelper googleAuthHelper;
    private LanguageManager languageManager;
    @SuppressLint("StaticFieldLeak")
    private static Context context;
    private static CompletableFuture<FirebaseFirestore> firestoreInitFuture;

    public static CompletableFuture<FirebaseFirestore> getFirestoreAsync() {
        return firestoreInitFuture;
    }
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        // androidx.multidex.MultiDex.install(this);
    }

    @NonNull
    @Override
    public androidx.work.Configuration getWorkManagerConfiguration() {
        return new androidx.work.Configuration.Builder()
                .setMinimumLoggingLevel(Log.INFO)
                .build();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();

        // Fix WebView issue in multi-process apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            String processName = getProcessName();
            if (!getPackageName().equals(processName)) {
                try {
                    WebView.setDataDirectorySuffix(processName);
                } catch (Exception e) {
                    Log.e(TAG, "WebView suffix error", e);
                }
            }
        }

        FirebaseApp.initializeApp(this);
        sharedPreferences = new SyncPreferences(this);
        languageManager = LanguageManager.getInstance(this);
//        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        applyThemeBasedOnSystem();
        firestoreInitFuture = CompletableFuture.supplyAsync(() -> {
            try {
                FirebaseFirestore db = FirebaseFirestore.getInstance();
                PersistentCacheSettings cacheSettings = PersistentCacheSettings.newBuilder()
                        .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                        .build();
                FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                        .setLocalCacheSettings(cacheSettings)
                        .build();
                db.setFirestoreSettings(settings);
                Log.i(TAG, "Firestore initialized on background thread");
                return db;
            } catch (Exception e) {
                Log.e(TAG, "Firestore init failed", e);
                return null;
            }
        });
        try {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true);
        } catch (Exception e) {
            Log.e(TAG, "FirebaseCrashlytics init failed", e);
        }

        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        new Thread(() -> {
            try {
                FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
                // لتفعيل App Check للديباج مؤقتاً: أزل // من السطور التالية ومن الاستيراد بالأعلى ومن app/build.gradle.kts
                // if (BuildConfig.DEBUG) {
                //     firebaseAppCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance());
                // } else {
                //     firebaseAppCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance());
                // }
                firebaseAppCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance());
                Log.i(TAG, "Firebase App Check initialized on background thread");
            } catch (Exception e) {
                Log.e(TAG, "Firebase App Check initialization failed", e);
            }
        }).start();


        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            // يمكنك التعامل مع الخطأ هنا، ولكن لا تظهر نافذة حوار في Application
            Log.e("MyApplication", "Google Play Services not available: " + resultCode);
        }
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean isFirstRun = prefs.getBoolean("first_run", true);
        new Thread(() -> {
            if (!isFirstRun) {
                if (!android.text.TextUtils.isEmpty(languageManager.getCurrentLanguage())) {
                    BaseActivity.applyLanguage(this, languageManager.getCurrentLanguage());
                    String userId = AppAuth.getUserID(getContext());
                    boolean isGuest = SecureLicenseManager.getInstance(this).isGuest();
                    if (!BuildConfig.DEBUG) {
                        try {
                            if (isGuest) {
                                FirebaseCrashlytics.getInstance().setUserId("Guest_" + SecureLicenseManager.getInstance(this).guestUID());
                            } else {
                                FirebaseCrashlytics.getInstance().setUserId("user_" + userId);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "FirebaseCrashlytics setUserId failed", e);
                        }
                    }
                }
            }
        }).start();
        try {
            CrashHandler.init(this);
            CrashHandler.getInstance().setCrashlyticsEnabled(true);
        } catch (Exception e) {
            Log.e(TAG, "CrashHandler init failed", e);
        }
        if (sharedPreferences.getLocalCurrency(SyncPreferences.DEFAULT_CURRENCY) == null) {
            sharedPreferences.setLocalCurrency(getString(R.string.local_currency));
        }
        String savedCurrency = sharedPreferences.getLocalCurrency(SyncPreferences.DEFAULT_CURRENCY);
        if (savedCurrency == null) {
            sharedPreferences.setLocalCurrency(getString(R.string.local_currency));
            defaultCurrencyName = getString(R.string.local_currency);
        } else {
            defaultCurrencyName = savedCurrency;
        }


        // تأجيل تهيئة Facebook SDK إلى ما بعد بدء التشغيل (لتقليل الحمل)
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (!FacebookSdk.isInitialized()) {
                    FacebookSdk.sdkInitialize(getApplicationContext());
                }
                AppEventsLogger.activateApp(this);
                FacebookSdk.setAutoLogAppEventsEnabled(true);
                FacebookSdk.setAdvertiserIDCollectionEnabled(true);
                FacebookSdk.setIsDebugEnabled(BuildConfig.DEBUG);
                Log.d(TAG, "Facebook SDK initialized after delay");
            } catch (Exception e) {
                Log.e(TAG, "Facebook SDK init failed", e);
            }
        }, 5000); // تأخير 5 ثوانٍ
        VersionManager versionManager = new VersionManager(this);
        // التهيئة الأساسية
        new Thread(() -> {
            NotificationHelper.init(this);
            DailyReminderManager.getInstance(this).initializeOnAppStart();
            initializeDailyReminderSystem();


            try {
                DailyReminderScheduler.scheduleDailyReminder(this);
            } catch (Exception e) {
                Log.e("MyApplication", "Failed to schedule reminder on app launch", e);
            }
        }).start();
        // إذا كان أول تشغيل، احفظ الإصدار الحالي
        if (versionManager.isFirstLaunch()) {
            versionManager.saveCurrentVersion();
            versionManager.savePreviousVersion(versionManager.getCurrentVersionName());
        }
        // تسجيل معلومات الإصدار للسجلات
        Log.d("AppVersion", versionManager.getVersionInfo());


//        setupPeriodicSync();
        checkProStatus();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.view.autofill.AutofillManager afm = getSystemService(android.view.autofill.AutofillManager.class);
            if (afm != null && afm.isEnabled()) {
                // هذا يمنع النظام من محاولة الوصول للخدمة أثناء تهيئة الإعلان
                // يمكنك تفعيله فقط إذا استمرت المشكلة
            }
        }
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
        new Thread(() -> {
            try {
                RequestConfiguration configuration = new RequestConfiguration.Builder()
                        .setTestDeviceIds(Arrays.asList("749D1BBD9D96FB73DFABE2D026AE2A71", // جهازك الأول
                                "EBD377935483E03BCA7AEBEB002CFAB3",            // الجهاز الثاني
                                "معرف_الجهاز_الثالث_هنا"             // الجهاز الثالث))
                        ))
                        .build();
                MobileAds.setRequestConfiguration(configuration);
                MobileAds.initialize(this, initializationStatus -> {
                    Log.d(TAG, "AdMob Initialized on background thread");
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize ads", e);
            }
        }).start();
        }, 6000);
    }


    public static Context getContext() {
        return context;
    }

    public static String getProcessName() {
        // الطريقة الرسمية والآمنة لأندرويد 9 فما فوق
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName();
        }

        // الطريقة الاحتياطية للإصدارات القديمة
        try {
            String file = "/proc/" + android.os.Process.myPid() + "/cmdline";
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line = reader.readLine();
            reader.close(); // لا تنسَ إغلاق الـ reader
            if (line != null) {
                return line.trim();
            }
        } catch (Exception e) {
            Log.e("MyApplication", "Error getting process name", e);
        }
        return ""; // تجنب إرجاع null أبداً
    }

    private void checkProStatus() {
        SharedPreferences prefs = getSharedPreferences("secure_license_prefs", MODE_PRIVATE);
        isProVersion = prefs.getBoolean("is_premium", false);
    }

    /**
     * 🔥 تهيئة نظام التذكير اليومي الجديد
     */
    private void initializeDailyReminderSystem() {
        try {
            DailyReminderManager.getInstance(this);
            Log.d("MyApplication", "Daily Reminder System initialized successfully");
        } catch (Exception e) {
            Log.e("MyApplication", "Failed to initialize Daily Reminder System", e);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // 🔥 الحفاظ على اللغة عند تغيير إعدادات الجهاز
        String lang = languageManager.getCurrentLanguage();
        Locale locale = Locale.forLanguageTag(lang); // تصحيح: استخدام forLanguageTag
        Locale.setDefault(locale);

        Configuration config = new Configuration(newConfig);
        config.setLocale(locale);

        // تصحيح: استخدام createConfigurationContext بدلاً من updateConfiguration
        createConfigurationContext(config);
    }

    private void setupPeriodicSync() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest syncRequest = new PeriodicWorkRequest.Builder(
                SyncWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "UNIQUE_SYNC_WORK",
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
        );
    }


    public static void applyGlobalTextWatcher(View rootView) {
        if (rootView instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) rootView;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyGlobalTextWatcher(group.getChildAt(i));
            }
        } else if (rootView instanceof EditText) {
            EditText editText = (EditText) rootView;
            if ("exclude_global_filter".equals(editText.getTag())) {
                return; // تجاهل هذا الحقل ولا تطبق الفلتر عليه
            }
            InputFilter numberFilter = new InputFilter() {
                @Override
                public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                    StringBuilder builder = new StringBuilder();
                    for (int i = start; i < end; i++) {
                        char c = source.charAt(i);
                        switch (c) {
                            case '٠':
                                builder.append('0');
                                break;
                            case '١':
                                builder.append('1');
                                break;
                            case '٢':
                                builder.append('2');
                                break;
                            case '٣':
                                builder.append('3');
                                break;
                            case '٤':
                                builder.append('4');
                                break;
                            case '٥':
                                builder.append('5');
                                break;
                            case '٦':
                                builder.append('6');
                                break;
                            case '٧':
                                builder.append('7');
                                break;
                            case '٨':
                                builder.append('8');
                                break;
                            case '٩':
                                builder.append('9');
                                break;
                            case '،':
                                builder.append(',');
                                break;
                            default:
                                builder.append(c);
                                break;
                        }
                    }
                    return builder.toString();
                }
            };
            // اجمع الفلاتر الحالية مع الجديد
            InputFilter[] existingFilters = editText.getFilters();
            InputFilter[] newFilters = new InputFilter[existingFilters.length + 1];
            System.arraycopy(existingFilters, 0, newFilters, 0, existingFilters.length);
            newFilters[existingFilters.length] = numberFilter;
            editText.setFilters(newFilters);
        }
    }

    public static void initializeDefaultUser(Context context, AppDatabase database) {
        if (!SecureLicenseManager.getInstance(context.getApplicationContext()).isGuest()) {
            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            if (database.userDao().getUserProfile() == null) {
                User currentUserProfile = new User(
                        "تطبيق محفظتي الذكية",
                        "ادارات مالية يومية",
                        "تعز، اليمن",
                        "+967780405427",
                        "haddad2inc@gmail.com",
                        uid
                );
                database.userDao().upsert(currentUserProfile);
            }
        }
    }

    void performLogout() {
        // إيقاف المزامنة
        FirestoreSyncManager.getInstance().stopListening();
        // تسجيل الخروج من Firebase
        FirebaseAuth.getInstance().signOut();
        // مسح البيانات المحلية
        SharedPreferences prefs = getSharedPreferences("prefs_uid", MODE_PRIVATE);
        prefs.edit().clear().apply();
        sharedPreferences.setFirstSyncComplete(false);
        // حذف قاعدة البيانات المحلية
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(this);
            db.close();

            File databaseFile = getDatabasePath("daftree_database");
            if (databaseFile.exists()) databaseFile.delete();

            File databaseWal = getDatabasePath("daftree_database-wal");
            if (databaseWal.exists()) databaseWal.delete();

            File databaseShm = getDatabasePath("daftree_database-shm");
            if (databaseShm.exists()) databaseShm.delete();
        });

        // التوجيه إلى شاشة تسجيل الدخول
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void applyThemeBasedOnSystem() {
        int currentNightMode = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;

        switch (currentNightMode) {
            case Configuration.UI_MODE_NIGHT_NO:
                // الوضع الفاتح
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;

            case Configuration.UI_MODE_NIGHT_YES:
                // الوضع الداكن
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;

            case Configuration.UI_MODE_NIGHT_UNDEFINED:
            default:
                // الوضع غير محدد: يمكن اعتماده على Light كافتراضي
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
}
