package com.hpp.daftree;


import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.location.Address;
import android.location.Geocoder;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.DeviceInfo;
import com.hpp.daftree.database.User;
import com.hpp.daftree.databinding.ActivitySplashBinding;
import com.hpp.daftree.dialogs.LanguageSelectionDialog;
import com.hpp.daftree.dialogs.LanguageViewModel;
import com.hpp.daftree.helpers.LanguageManager;
import com.hpp.daftree.models.AppLockManager;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.models.DeviceBanManager;
import com.hpp.daftree.models.SnackbarHelper;
import com.hpp.daftree.models.UUIDGenerator;
import com.hpp.daftree.syncmanagers.SyncPreferences;
import com.hpp.daftree.ui.BaseActivity;
import com.hpp.daftree.ui.LockScreenActivity;
import com.hpp.daftree.utils.AdsGoogleRewarder;
import com.hpp.daftree.utils.LicenseManager;
import com.hpp.daftree.utils.ReferralManager;
import com.hpp.daftree.utils.SecureLicenseManager;
import com.hpp.daftree.utils.VersionManager;
import com.hpp.daftree.viewmodels.ProfileViewModel;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends BaseActivity
        implements
        LanguageSelectionDialog.LanguageSelectionListener {
    private ActivitySplashBinding binding;
    private static final String TAG = "SplashActivity";
    private AlertDialog progressDialog; // تصحيح: استبدال ProgressDialog
    private FusedLocationProviderClient fusedLocationClient;
    private View rootView;
    private SyncPreferences syncPreferences;
    private VersionManager versionManager;
    private DeviceBanManager deviceBanManager;
    private LanguageManager languageManager;
    private  int currentVersion = 0;
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    showProgressDialog(getString(R.string.preper_guest_account));
                    setupLocalCurrencyAndProceed();
                } else {
                    SnackbarHelper.showSnackbar(rootView, getString(R.string.location_permission_denied), SnackbarHelper.SnackbarType.ERROR);
                    showProgressDialog(getString(R.string.preper_guest_account));
                    addDefaultCurrencyAndProceed();
                }
            });
    private SharedPreferences prefs;
    private boolean isFirstRun, isFirstRunGuest;

    private boolean isApplyingLanguage = false;
    private LicenseManager licenseManager;
    private ReferralManager referralManager;
    private AppDatabase db;
    private FirebaseFirestore firestore;
    private DaftreeRepository repository;
    private String referrerUid;
    private FirebaseAuth firebaseAuth;
    private String lockType = "";
    FirebaseUser currentUser;
    private boolean isGuest = false;
    private String guestUID = "";
    String savedLanguage = "";
    private Executor executor;
    private ProfileViewModel viewModelUserProfile;
    private SecureLicenseManager secureManager;
    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_IS_FIRST_RUN = "first_run";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_NEEDS_LANGUAGE_SELECTION = "needs_language_selection";

    // Supported languages
    private static String[] SUPPORTED_LANGUAGES = {"ar", "en", "fr", "de", "es", "tr", "ru"};


    private LanguageViewModel languageViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.activity.EdgeToEdge.enable(this,
                androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT));
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        isFirstRun = prefs.getBoolean("first_run", true);
//        if (isFirstRun)  applySavedLanguageImmediately();
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        languageManager = LanguageManager.getInstance(this);
        referralManager = new ReferralManager(this);
        savedLanguage = languageManager.getCurrentLanguage();
        SUPPORTED_LANGUAGES = languageManager.getSupportedLanguagesArray();
        rootView = findViewById(android.R.id.content);
        LanguageManager.applyLocale(this, languageManager.getCurrentLanguage());
        applySavedLanguageImmediately();
        isFirstRunGuest = prefs.getBoolean("first_run_guest", true);
        SharedPreferences referral_prefs = getSharedPreferences("referral_prefs", MODE_PRIVATE);
        referrerUid = referral_prefs.getString("referrer_uid", "");

        waitForFirestoreAndProceed();
//        checkUserAndNavigateToMain();
    }


    // الدوال الجديدة:
    private void waitForFirestoreAndProceed() {
         new Thread(() -> {
            try {
                MyApplication.getFirestoreAsync().get(2, TimeUnit.SECONDS);
                runOnUiThread(() -> {
                    hideProgressDialog();
                    checkUserAndNavigateToMain();  // أو proceedAfterFirestoreReady
                });
            } catch (Exception e) {
                Log.e(TAG, "Firestore init error", e);
                runOnUiThread(() -> {
                    hideProgressDialog();
                    checkUserAndNavigateToMain();
                });
            }
        }).start();
    }
  /**
     * ✅ **دالة جديدة: تطبيق اللغة المحفوظة فوراً عند بدء Activity**
     */
    private void applySavedLanguageImmediately() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isFirstRun = prefs.getBoolean(KEY_IS_FIRST_RUN, true);

        if (!isFirstRun) {
            // ليس أول تشغيل - تطبيق اللغة المحفوظة مباشرة
            String savedLanguage = prefs.getString(KEY_LANGUAGE, "en");
            Log.d(TAG, "Not first run - Applying saved language immediately: " + savedLanguage);

            // تطبيق اللغة على Configuration الحالي
            LanguageManager.getInstance(this).setLocale(savedLanguage);
            // ✅ إعادة تحميل النصوص بلغة التطبيق
            updateConfigurationWithLanguage(savedLanguage);
        }
    }


    /**
     * ✅ **تحديث Configuration بالنغة المحددة**
     */
    private void updateConfigurationWithLanguage(String languageCode) {
        try {
            Resources resources = getResources();
            Configuration configuration = resources.getConfiguration();
            Locale locale = Locale.forLanguageTag(languageCode); // تصحيح: استخدام forLanguageTag

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                configuration.setLocale(locale);
                configuration.setLayoutDirection(locale);
            } else {
                configuration.setLocale(locale);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    configuration.setLayoutDirection(locale);
                }
            }

            // تصحيح: استخدام createConfigurationContext بدلاً من updateConfiguration
            createConfigurationContext(configuration);
            getResources().updateConfiguration(configuration, resources.getDisplayMetrics());
//            Context updatedContext = createConfigurationContext(configuration);
        } catch (Exception e) {
            Log.e(TAG, "Error updating configuration: " + e.getMessage());
        }
    }

    private void setupLanguageObserver() {
        languageViewModel.getSelectedLanguage().observe(this, lang -> {
            if (lang != null && !lang.isEmpty()) {
                String currentLang = savedLanguage;
                Log.e(TAG, "اللغة الجديدة: " + lang + " | اللغة الحالية: " + currentLang);

                if (!lang.equals(currentLang)) {
                    Log.e(TAG, "تم اكتشاف تغيير اللغة، جاري التطبيق...");

                    BaseActivity.applyLanguage(SplashActivity.this, lang);

                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("language", lang);
                    editor.apply();
                }
            }
        });
    }

    private void checkUserAndNavigateToMain() {
        verifyDatabaseData(() -> {
            firebaseAuth = FirebaseAuth.getInstance();
            db = AppDatabase.getDatabase(getApplicationContext());
            repository = new DaftreeRepository(getApplication());
            syncPreferences = new SyncPreferences(this);
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            firestore = FirebaseFirestore.getInstance();
            referralManager = new ReferralManager(this);
            licenseManager = new LicenseManager(this);
            secureManager = SecureLicenseManager.getInstance(this);
            versionManager = new VersionManager(this);
            viewModelUserProfile = new ViewModelProvider(this).get(ProfileViewModel.class);
            executor = ContextCompat.getMainExecutor(this);
            currentUser = FirebaseAuth.getInstance().getCurrentUser();
            isGuest = SecureLicenseManager.getInstance(this).isGuest();
            guestUID = SecureLicenseManager.getInstance(this).guestUID();
            languageViewModel = new LanguageViewModel();
            deviceBanManager = new DeviceBanManager(this);

            handleIncomingDeepLink(getIntent());
            AdsGoogleRewarder.checkAndReward(this);
            setupLanguageObserver();
            currentVersion = versionManager.getCurrentVersionCode();
            if(isNewVersionAvailable()){
                return;
            }
            new Handler(Looper.getMainLooper()).postDelayed(this::handleAppOpen, 100);
        });
    }
    private void checkUserAndNavigateToMain1() {
        verifyDatabaseData(() -> {
            new Handler(Looper.getMainLooper()).postDelayed(this::handleAppOpen, 100);
        });
    }
    private void verifyDatabaseData(Runnable onComplete) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getDatabase(this);

                int accountCount = db.accountDao().getAccountsCount();
                int transactionCount = db.transactionDao().getTransactionsCount();

                Log.d("SplashActivity", "✅ التحقق من قاعدة البيانات:");
                Log.d("SplashActivity", "   - عدد الحسابات: " + accountCount);
                Log.d("SplashActivity", "   - عدد العمليات: " + transactionCount);

                runOnUiThread(onComplete);

            } catch (Exception e) {
                Log.e("SplashActivity", "❌ خطأ في التحقق من قاعدة البيانات: " + e.getMessage());
                runOnUiThread(onComplete);
            }
        });
    }


    /**
     * ✅ تحسين الدالة الرئيسية لفتح التطبيق
     */

    public void handleAppOpen() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isFirstRun = prefs.getBoolean(KEY_IS_FIRST_RUN, true);

        if (isFirstRun) {
            String realSystemLanguage = getRealSystemLanguage();
            String systemLanguage = languageManager.getSystemLanguage();

            Log.d(TAG, "Real System Language: " + realSystemLanguage);
            Log.d(TAG, "LanguageManager System Language: " + systemLanguage);
            prefs.edit().putBoolean("isDebtFeatureEnabled", false).apply();
            boolean isSupported = isLanguageSupported(realSystemLanguage);
            Log.d(TAG, "Is language supported: " + isSupported);
            if (isSupported) {
                // System language is supported - apply it and proceed
                Log.d(TAG, "Applying supported system language: " + realSystemLanguage);
                languageManager.setLocale(realSystemLanguage);
                saveLanguagePreference(realSystemLanguage);
                SecureLicenseManager.getInstance(this).setGuest(true);
                languageViewModel.setLanguage(realSystemLanguage);
                // Mark as not first run
//                prefs.edit().putBoolean(KEY_IS_FIRST_RUN, false).apply();
                guestUserData();


            } else {
                // لغة الهاتف غير مدعومة -> عرض الديالوج
                showLanguageDialog();
            }
        } else {
            // المستخدم ليس جديداً -> الدخول العادي
            proceedToActivity();
        }
    }

    private String getRealSystemLanguage() {
        String systemLanguage = Locale.getDefault().getLanguage();
        Log.d(TAG, "Real system language detected: " + systemLanguage);
        return systemLanguage;
    }

    private boolean isLanguageSupported(String languageCode) {
        if (languageCode == null || languageCode.isEmpty()) {
            return false;
        }

        // تحقق من اللغة الأساسية (مثل en, ar, fr)
        for (String lang : SUPPORTED_LANGUAGES) {
            if (lang.equals(languageCode)) {
                return true;
            }
        }

        // تحقق من اللغات الفرعية (مثل en_US, en_GB)
        if (languageCode.length() > 2) {
            String baseLanguage = languageCode.substring(0, 2);
            for (String lang : SUPPORTED_LANGUAGES) {
                if (lang.equals(baseLanguage)) {
                    Log.d(TAG, "Base language supported: " + baseLanguage + " for " + languageCode);
                    return true;
                }
            }
        }

        Log.d(TAG, "Language not supported: " + languageCode);
        return false;
    }

    private void guestUserData() {
        Log.d(TAG, "معالجة مستخدم ضيف");
        isFirstRunGuest = prefs.getBoolean("first_run_guest", true);

        if (isFirstRun || isFirstRunGuest) {
if(!isNetworkAvailable()){
    createNewUserGuest();
    return;
}
            deviceBanManager.checkDeviceBan(new DeviceBanManager.BanCheckListener() {
                @Override
                public void onCheckComplete(boolean isBanned, String reason) {
                    runOnUiThread(() -> {
                        if (isBanned) {
                            if (com.hpp.daftree.BuildConfig.DEBUG) {
                                showProgressDialog(getString(R.string.preper_guest_account));
                                createNewUserGuest();
                                return;
                            }
                            showDeviceBanDialog(reason);
                            return;

                        }
                        Log.d(TAG, "أول تشغيل للضيف");
                        showProgressDialog(getString(R.string.preper_guest_account));
                        createNewUserGuest();
                    });
                }

                @Override
                public void onCheckError(String error) {
                    runOnUiThread(() -> {
                        showProgressDialog(getString(R.string.preper_guest_account));
                        createNewUserGuest();
                    });
                }
            });
        } else {
            proceedToActivity();
        }
    }

    boolean isDialogeShown = false;

    private void showDeviceBanDialog(String banReason) {
       // if(isA)
           if (isFinishing() || isDestroyed()) {
               return;
           }



               isDialogeShown = true;

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.device_block_title))
                .setMessage(getString(R.string.device_block_message))
                .setPositiveButton(getString(R.string.contact_support), (dialog, which) -> {
                    try {
                        String message = "\n\n" + getString(R.string.whatsapp_greeting) + "\n\n" +
                                getString(R.string.whatsapp_app_tit) + getString(R.string.app_name) + "\n\n" +
                                getString(R.string.whatsapp_request) + "\n\n" +
                                getString(R.string.deviceId, licenseManager.getDeviceId()) + "\n\n" +
                                getString(R.string.whatsapp_request_final) + "\n\n" +
                                getString(R.string.whatsapp_thank_you);
                        Intent whatsappIntent = new Intent(Intent.ACTION_VIEW);
                        whatsappIntent.setData(Uri.parse("https://wa.me/967780405427?text=" +
                                Uri.encode(message)));
                        startActivity(whatsappIntent);
                    } catch (Exception e) {
                        Intent phoneIntent = new Intent(Intent.ACTION_DIAL);
                        phoneIntent.setData(Uri.parse("tel:+967780405427"));
                        startActivity(phoneIntent);
                    }
                })
                .setNegativeButton(getString(R.string.exit), (dialog, which) -> {
                    isDialogeShown = false;
                    finishAffinity();
                })
                .setCancelable(false)
                .setIcon(R.drawable.ic_warning)
                .show();
    }

    @SuppressLint("StringFormatInvalid")
    private void showDeviceBanDialog2(String banReason) {
        isDialogeShown = true;
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.device_block_title))
                .setMessage(getString(R.string.device_block_message, banReason))
                .setPositiveButton(getString(R.string.exit), (dialog, which) -> {
                    isDialogeShown = false;
                    finishAffinity();
                })
                .setCancelable(false)
                .setIcon(R.drawable.ic_warning)
                .show();
    }

    private void showLanguageDialog() {

        LanguageSelectionDialog dialog = new LanguageSelectionDialog();
        dialog.setLanguageSelectionListener(this);
        dialog.show(getSupportFragmentManager(), "LanguageSelectionDialog");
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (languageManager != null) {
            languageManager.setLocale(savedLanguage);
        }
    }

    @Override
    public void onLanguageSelectionCancelled() {
        Log.d(TAG, "Language selection cancelled, using default Arabic");

        // If cancelled, use default Arabic and proceed
        String defaultLanguage = "en";
        languageManager.setLocale(defaultLanguage);
        saveLanguagePreference(defaultLanguage);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putBoolean(KEY_IS_FIRST_RUN, false)
                .putBoolean(KEY_NEEDS_LANGUAGE_SELECTION, false)
                .apply();
        recreate();
    }

    private void saveLanguagePreference(String languageCode) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply();
    }

    @Override
    public void onLanguageSelected(String languageCode) {
        Log.d(TAG, "✅ تم اختيار لغة من الديالوج: " + languageCode);

        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_IS_FIRST_RUN, false);
        editor.putString("language", languageCode);
        editor.putBoolean("first_run", false);
        editor.apply();
        languageManager.setLocale(languageCode);
        saveLanguagePreference(languageCode);
        SecureLicenseManager.getInstance(this).setGuest(true);
        languageViewModel.setLanguage(languageCode);
        isGuest = true;

        recreate();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleDeepLink(intent);
    }

    private void handleDeepLink(Intent intent) {
        Uri data = intent.getData();
        if (data != null) {
            if ("daftree".equals(data.getScheme()) && "invite".equals(data.getHost())) {
                referrerUid = data.getQueryParameter("ref");
            } else if ("https".equals(data.getScheme()) && "hpp-daftree.web.app".equals(data.getHost())) {
                referrerUid = data.getQueryParameter("ref");
            }

            Log.d("DeepLink", "الرابط المستلم1: " + data.toString());
            Log.d("DeepLink", "كود الدعوة1: " + referrerUid);

            if (referrerUid != null && !referrerUid.isEmpty()) {
                SharedPreferences prefs = getSharedPreferences("referral_prefs", MODE_PRIVATE);
                prefs.edit().putString("referrer_uid", referrerUid).apply();
            }
        }
    }

    private void handleIncomingDeepLink(Intent intent) {
        Uri data = intent.getData();
        if (data != null && "daftree".equals(data.getScheme())) {
            referrerUid = data.getQueryParameter("ref");
            Log.d("DeepLink", "الرابط المستلم: " + data.toString());
            Log.d("DeepLink", "كود الدعوة: " + referrerUid);
        }
    }

    void proceedToActivity() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            AppLockManager lockManager = new AppLockManager(this);
            if (lockManager.isLockEnabled()) {
                startActivity(new Intent(this, LockScreenActivity.class));
            } else {
                Intent mainIntent = new Intent(this, MainActivity.class);
                if (referrerUid != null) {
                    mainIntent.putExtra("REFERRER_UID", referrerUid);
                }
                startActivity(mainIntent);
            }
            finish();
        }, 100);
    }

    // ... باقي الدوال (checkLocationPermission, showLocationPermissionExplanation, setupLocalCurrencyAndProceed, etc.)
    // ... سيتم الحفاظ على نفس المنطق مع إضافة logs للتتبع

    private void checkLocationPermission() {
        Log.d(TAG, getString(R.string.checking_location_permission));
        updateProgressDialog(getString(R.string.checking_location_permission));
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, getString(R.string.detecting_local_currency));
            updateProgressDialog(getString(R.string.detecting_local_currency));
            setupLocalCurrencyAndProceed();
        } else {
            Log.d(TAG, getString(R.string.requesting_location_permission));
            updateProgressDialog(getString(R.string.requesting_location_permission));
            showLocationPermissionExplanation();
        }
    }

    private void showLocationPermissionExplanation() {
        hideProgressDialog();
        if (isFinishing() || isDestroyed()) {
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.currency_setup_title))
                .setMessage(getString(R.string.currency_setup_message))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.use_location), (dialog, which) -> {
                    requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
                })
                .setNegativeButton(getString(R.string.skip_location), (dialog, which) -> {
                    showProgressDialog(getString(R.string.preper_guest_account));
                    addDefaultCurrencyAndProceed();
                })
                .setCancelable(false)
                .show();
    }

    @SuppressLint("MissingPermission")
    @SuppressWarnings("deprecation")
    private void setupLocalCurrencyAndProceed() {
        Log.d(TAG, getString(R.string.detecting_local_currency));
        updateProgressDialog(getString(R.string.detecting_local_currency));

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location == null) {
                addDefaultCurrencyAndProceed();
                return;
            }

            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(latitude, longitude, 1, new Geocoder.GeocodeListener() {
                    // تم حذف حرف الـ d ليصبح المسمى كما طلبه الخطأ لديك
                    @Override
                    public void onGeocode(@NonNull List<Address> addresses) {
                        runOnUiThread(() -> handleGeocodeResult(addresses));
                    }

                    @Override
                    public void onError(@Nullable String errorMessage) {
                        Log.e("LocalCurrency", "Geocoder Error: " + errorMessage);
                        runOnUiThread(() -> addDefaultCurrencyAndProceed());
                    }
                });

            } else {
                new Thread(() -> {
                    try {
                        List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
                        runOnUiThread(() -> handleGeocodeResult(addresses));
                    } catch (Exception e) {
                        Log.e("LocalCurrency", "Geocoder failed", e);
                        runOnUiThread(() -> addDefaultCurrencyAndProceed());
                    }
                }).start();
            }
        });
    }

    /**
     * هذه الدالة تحتوي على منطق المعالجة الخاص بك (المنطق القديم)
     * تم فصلها لضمان عدم تكرار الكود وسهولة الصيانة
     */
    private void handleGeocodeResult(List<Address> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            addDefaultCurrencyAndProceed();
            return;
        }

        try {
            String countryCode = addresses.get(0).getCountryCode();
//            Locale locale = new Locale("", countryCode);
            Locale locale = new Locale.Builder().setRegion(countryCode).build();
            java.util.Currency currencyInfo = java.util.Currency.getInstance(locale);

            // استخدام savedLanguage التي كانت موجودة في كودك الأصلي
            Locale displayLocale = Locale.forLanguageTag(savedLanguage);
            String currencyName = currencyInfo.getDisplayName(displayLocale);
            String currencySymbol = currencyInfo.getSymbol(displayLocale);
            String code;

            // منطق التنسيق الخاص بك للغة العربية
            if (savedLanguage.equals("ar") && currencyName != null && currencyName.length() >= 2) {
                code = currencyName.substring(0, 1) + "." + currencyName.substring(1, 2);
            } else {
                code = currencyInfo.getCurrencyCode();
            }

            // توحيد رمز الريال كما في منطقك السابق
            if (code.equals("ر.ي") || code.equals("ر.س") || code.equals("ر.ق") || code.equals("ر.ع")) {
                currencySymbol = "﷼";
            }

            Log.d("LocalCurrency", "العملة المكتشفة: " + " language: " + savedLanguage +
                    " currencyName: " + currencyName + " Symbol: " + currencySymbol + " Code: " + code);

            // تنفيذ الخطوة الأخيرة بإضافة العملة للقاعدة
            addCurrencyToDatabase(currencyName, currencySymbol, code);

        } catch (Exception e) {
            Log.e("LocalCurrency", "خطأ أثناء معالجة بيانات العملة", e);
            addDefaultCurrencyAndProceed();
        }
    }

    private void addDefaultCurrencyAndProceed() {
        String defaultName = getString(R.string.local_currency);
        String defaultCode = "LOC";
        String symbol;

        if (savedLanguage.equals("ar") && defaultName.length() >= 2) {
            symbol = defaultName.substring(0, 1) + "." + defaultName.substring(1, 2);
        } else {
            symbol = defaultCode;
        }
        MyApplication.defaultCurrencyName = defaultName;
        MyApplication.defaultCurrencySymbol = symbol;
        MyApplication.defaultCurrencyCode = defaultCode;
        addCurrencyToDatabase(defaultName, symbol, defaultCode);
    }

    private void addCurrencyToDatabase(String name, String symbol, String code) {
        String uid;
        if (isGuest) {
            uid = guestUID;
        } else {
            uid = FirebaseAuth.getInstance().getUid();
        }
        Log.d(TAG, "إضافة العمله");
        AppDatabase.databaseWriteExecutor.execute(() -> {
            //    int currencyCount = db.currencyDao().getCurrencyCount();
            int accTypeCount = db.accountTypeDao().getAccountTypeCount();
            if (db.currencyDao().getCurrencyCount() < 1) {
                Currency existing = repository.getCurrencyByName(name);
                Log.e(TAG, "defaultCurrencyName: " + name);
                if (existing == null) {
                    Currency newCurrency = new Currency();
                    newCurrency.setName(name);
                    newCurrency.setOwnerUID(uid);
                    newCurrency.setSyncStatus("NEW");
                    newCurrency.setSymbol(symbol);
                    newCurrency.setCode(code);
                    newCurrency.setDefault(true);
                    newCurrency.setFirestoreId(UUIDGenerator.generateSequentialUUID().toString());
                    newCurrency.setLastModified(System.currentTimeMillis());
                    syncPreferences.setLocalCurrency(name.trim());
                    MyApplication.defaultCurrencyName = (name.trim());
                    db.currencyDao().insert(newCurrency);
                }
                addDollarCurrency();

                Resources localizedResources = LanguageManager.getLocalizedResources(this);
                String nameDollar = localizedResources.getString(R.string._408);
                Currency existing2 = repository.getCurrencyByName(name);
                Log.e(TAG, "defaultCurrencyNameDollar: " + name);
                if (existing2 == null) {
                    Currency newCurrency = new Currency();
                    newCurrency.setName(nameDollar);
                    newCurrency.setOwnerUID(uid);
                    newCurrency.setSymbol("$");
                    newCurrency.setCode("USD");
                    newCurrency.setSyncStatus("NEW");
                    newCurrency.setDefault(false);
                    newCurrency.setFirestoreId(UUIDGenerator.generateSequentialUUID().toString());
                    newCurrency.setLastModified(System.currentTimeMillis());
                    db.currencyDao().insert(newCurrency);
                }
                String[] accountTypes = {
                        localizedResources.getString(R.string.account_type_customer),
                        localizedResources.getString(R.string.account_type_supplier),
                        localizedResources.getString(R.string.account_type_general)
                };
                if (accTypeCount > 0) {
                    runOnUiThread(this::navigateToMainActivity);
                    return;
                }
                for (String type : accountTypes) {
                    AccountType account_Type = repository.getAccountTypeByName(type);
                    if (account_Type == null) {
                        Log.d(TAG, "إضافة انواع الحسابات: " + type);
                        AccountType accountType = new AccountType();
                        accountType.setName(type);
                        accountType.setOwnerUID(uid);
                        accountType.setFirestoreId(UUIDGenerator.generateSequentialUUID().toString());
                        accountType.setSyncStatus("NEW");
                        accountType.setDefault(true);
                        accountType.setLastModified(System.currentTimeMillis());
                        db.accountTypeDao().insert(accountType);
                    }
                }
            }
            runOnUiThread(() -> {
                MyApplication.initializeDefaultUser(getApplicationContext(), db);
                Log.d(TAG, "تمت إضافة العملة بنجاح، جاري الانتقال إلى الشاشة الرئيسية...");
                navigateToMainActivity();
            });
        });
    }

    private void addDollarCurrency() {
        String uid;
        Resources localizedResources = LanguageManager.getLocalizedResources(this);
        String name = localizedResources.getString(R.string._408);
        if (isGuest) {
            uid = guestUID;
        } else {
            uid = FirebaseAuth.getInstance().getUid();
        }

        Currency existing = repository.getCurrencyByName(name);
        Log.e(TAG, "defaultCurrencyName: " + name);
        if (existing == null) {
            Currency newCurrency = new Currency();
            newCurrency.setName(name);
            newCurrency.setOwnerUID(uid);
            newCurrency.setSymbol("$");
            newCurrency.setCode("USD");
            newCurrency.setSyncStatus("NEW");
            newCurrency.setDefault(false);
            newCurrency.setFirestoreId(UUIDGenerator.generateSequentialUUID().toString());
            newCurrency.setLastModified(System.currentTimeMillis());
            db.currencyDao().insert(newCurrency);
        }

    }

    @SuppressLint("SuspiciousIndentation")
    private void navigateToMainActivity() {
        hideProgressDialog();
        if (viewModelUserProfile.getUserProfile().hasObservers()) {
            viewModelUserProfile.getUserProfile().removeObservers(this);
        }
        Intent intent = new Intent(this, MainActivity.class);
        if (referrerUid != null) {
            intent.putExtra("REFERRER_UID", referrerUid);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        isFirstRunGuest = prefs.getBoolean("first_run_guest", true);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(KEY_IS_FIRST_RUN, false);
            editor.putBoolean("isNewUser", false);
            editor.apply();
            startActivity(intent);
            finish();
        }, 100);
    }

    private void showProgressDialog1(String message) {
        runOnUiThread(() -> {
            try {
                if (progressDialog == null) {
                    progressDialog = new MaterialAlertDialogBuilder(this)
                            .setView(new ProgressBar(this))
                            .setMessage(message)
                            .setCancelable(false)
                            .create();
                }
                progressDialog.setMessage(message);
                if (!progressDialog.isShowing()) {
                    progressDialog.show();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error showing progress dialog", e);
            }
        });
    }
    private void showProgressDialog(String message) {
        if (progressDialog != null && progressDialog.isShowing()) {
            TextView tv = progressDialog.findViewById(R.id.tv_message);
            if (tv != null) tv.setText(message);
            return;
        }
        runOnUiThread(() -> {
            try {
                androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
                View view = getLayoutInflater().inflate(R.layout.dialog_loading, null);
                TextView tvMessage = view.findViewById(R.id.tv_message);
                tvMessage.setText(message);

                builder.setView(view);
                builder.setCancelable(false);

                progressDialog = builder.create();

                // لجعل الخلفية شفافة والزوايا تبدو أجمل
                if (progressDialog.getWindow() != null) {
                    //  progressDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                }

                progressDialog.show();
            } catch (Exception e) {
                Log.e(TAG, "Error showing progress dialog", e);
            }
        });
    }

    private void updateProgressDialog(String message) {
        runOnUiThread(() -> {
            try {
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.setMessage(message);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating progress dialog", e);
            }
        });
    }

    private void hideProgressDialog() {
        runOnUiThread(() -> {
            try {
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error hiding progress dialog", e);
            }
        });
    }

    private void createNewUserGuest() {
        try {
            Log.d(TAG, "start انشاء حساب للضيف");

            updateProgressDialog(getString(R.string.preper_guest_account));
            String guestUid = UUIDGenerator.generateSequentialUUID().toString();
            SecureLicenseManager.getInstance(this).setGuestUID(guestUid);
            guestUID = guestUid;
            FirebaseFirestore firestore = FirebaseFirestore.getInstance();
            if (TextUtils.isEmpty(guestUID)) {
                guestUID = UUIDGenerator.generateSequentialUUID().toString();
                SecureLicenseManager.getInstance(this).setGuestUID(guestUID);
            }
            Log.d(TAG, "انشاء حساب للضيف" + " guestUID: " + guestUID + " isGuest: " + isGuest);
            User newUser = new User();
            newUser.setOwnerUID(guestUID);
            newUser.setEmail("");
            newUser.setName(getString(R.string.ar_long_text_20));
            newUser.setCompany(getString(R.string.ar_long_text_20));
            newUser.setAddress(getString(R.string.ar_text_10_1));
            newUser.setPhone("+967780405427");
            newUser.setUserType("guest");
            newUser.setSyncStatus("NEW");
            newUser.setIs_active(true);
            newUser.setLast_login(User.getCurrentLocalDateTime());
            newUser.setIs_premium(false);
            newUser.setCreated_at(User.getCurrentLocalDateTime());
            newUser.setLogin_count(1);
            newUser.setMax_devices(1);
            newUser.setTransactions_count(0);
            newUser.setMax_transactions(LicenseManager.FREE_TRANSACTION_LIMIT);
            newUser.setApp_Version(versionManager.getCurrentVersionName());
            newUser.setLastVersion(versionManager.getPreviousVersion());
            DeviceInfo currentDevice = licenseManager.getCurrentDeviceInfo();
            String deviceId = currentDevice.getDeviceId();
            newUser.setDeviceId(deviceId);
            saveGuestUserLocally(newUser, () -> {
                // لن يتم تنفيذ هذا الكود إلا بعد انتهاء الحفظ في قاعدة البيانات
                if (isNetworkAvailable()) {
                    saveGuestToFirestore(newUser); // هذا يمكنه العمل في الخلفية
                }else{
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("guest_state", "NEW");
                    editor.apply();
                }
                // الآن ننتقل للمرحلة التالية بمان
                checkLocationPermission();
            });
        } catch (Exception e) {
            Log.e(TAG, "createNewUserGuest Error: " + e);
            showProgressDialog(getString(R.string.preper_guest_account));
            checkLocationPermission();
        }
    }

    private void saveGuestToFirestore(User guestUser) {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        Map<String, Object> guestDataMap = new HashMap<>();
        guestDataMap.put("ownerUID", guestUID.trim());
        guestDataMap.put("userType", "guest");
        guestDataMap.put("is_premium", false);
        guestDataMap.put("created_at", User.getCurrentLocalDateTime());
        guestDataMap.put("last_login", User.getCurrentLocalDateTime());
        guestDataMap.put("app_Version", versionManager.getCurrentVersionName());
        guestDataMap.put("app_VersionLast", versionManager.getPreviousVersion());
        guestDataMap.put("login_count", 1);
        guestDataMap.put("max_devices", 1);
        guestDataMap.put("max_transactions", LicenseManager.FREE_TRANSACTION_LIMIT);
        guestDataMap.put("transactions_count", 0);
        guestDataMap.put("transactionsCount", 0);
        guestDataMap.put("ad_rewards", 0);
        guestDataMap.put("referral_rewards", 0);
        guestDataMap.put("lastModified", System.currentTimeMillis());
        DeviceInfo deviceInfo = licenseManager.getCurrentDeviceInfo();
        guestDataMap.put("deviceId", deviceInfo.getDeviceId());
        guestDataMap.put("deviceName", deviceInfo.getDeviceName());
        guestDataMap.put("deviceModel", deviceInfo.getDeviceModel());
        firestore.collection("guests").document(guestUID.trim()).set(guestDataMap)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "تم إنشاء حساب الضيف في Firestore: " + guestUID);
                        new Thread(() -> {
                            try {
                                SharedPreferences.Editor editor = prefs.edit();
                                editor.putString("guest_state", "SAVE");
                                editor.apply();
                                Log.d(TAG, "تم تحديث المستخدم الضيف محلياً في SQLite: " + guestUser.getOwnerUID());
                            } catch (Exception e) {
                                SharedPreferences.Editor editor = prefs.edit();
                                editor.putString("guest_state", "NEW");
                                editor.apply();
                                Log.e(TAG, "خطأ في تحديث الضيف محلياً: " + e.getMessage());
                            }
                        }).start();
                    } else {
                        Log.e(TAG, "فشل إضافة الضيف في Firestore، سيتم المزامنة لاحقاً: " + task.getException());
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString("guest_state", "NEW");
                        editor.apply();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "فشل إضافة الضيف في Firestore: " + e.getMessage());
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("guest_state", "NEW");
                    editor.apply();
                });
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getApplication().getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
    }

    private void saveGuestUserLocally1(User guestUser) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                db.userDao().upsert(guestUser);
                Log.d(TAG, "تم حفظ المستخدم الضيف محلياً في SQLite: " + guestUser.getOwnerUID());
            } catch (Exception e) {
                Log.e(TAG, "خطأ في حفظ الضيف محلياً: " + e.getMessage());
            }
        });
    }

    private void saveGuestUserLocally(User guestUser, Runnable onComplete) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                db.userDao().upsert(guestUser);
                Log.d(TAG, "تم حفظ المستخدم الضيف محلياً في SQLite");

                // عند الانتهاء بنجاح، قم بتشغيل الكود التالي في الـ Main Thread
                runOnUiThread(onComplete);
            } catch (Exception e) {
                Log.e(TAG, "خطأ في حفظ الضيف محلياً: " + e.getMessage());
                // حتى في حالة الخطأ، يجب أن نكمل التدفق أو نظهر رسالة
                runOnUiThread(onComplete);
            }
        });
    }

    private void showAlreadyRegisteredMessage() {
        Snackbar snackbar = Snackbar.make(rootView,
                getString(R.string.referral_already_registered),
                Snackbar.LENGTH_LONG);

        snackbar.setAction(getString(R.string.menu_more), v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.about_referral))
                    .setMessage(getString(R.string.about_referral_message))
                    .setPositiveButton(getString(R.string.ar_text_5_3), null)
                    .show();
        });

        snackbar.show();
    }


    private boolean isNewVersionAvailable() {
        int minVersion = secureManager.getMinVersion();
        int currentVersion = versionManager.getCurrentVersionCode();
        if (currentVersion < minVersion) {
            // ❌ الإصدار قديم: إيقاف الانتقال وإظهار شاشة التحديث الإجباري
            binding.layoutUpdatePrompt.setVisibility(View.VISIBLE);
            binding.tvWelcome.setVisibility(View.GONE);
            binding.btnUpdateApp.setOnClickListener(v -> {
                LanguageManager langManager =  LanguageManager.getInstance(this);
                String uptoUrl = "https://com-hpp-daftree." + langManager.getCurrentLanguage() + ".uptodown.com/android";
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + getPackageName())));
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uptoUrl)));
                    } catch (Exception e2) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://com-hpp-daftree.uptodown.com/android")));
                    }
                }
            });
            return true;
        }
        return false;
    }
    private void checkAndFetchAppConfig() {
        long lastCheck = prefs.getLong("last_appConfig_time", 0);
        long currentTime = System.currentTimeMillis();
        // إذا مر 24 ساعة أو أكثر
        if ((currentTime - lastCheck < TimeUnit.DAYS.toMillis(1))) {
            return;
        }
        Log.d(TAG, "checkAndFetchAppConfig last_appConfig_time: " + lastCheck);
        prefs.edit().putLong("last_appConfig_time", currentTime).apply();
        // 4. جلب البيانات من فايرستور (أول مرة أو للتحديث اليومي الخفي)
        FirebaseFirestore.getInstance().collection("app_config")
                .document("settings")
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && documentSnapshot.getData() != null) {
                        SecureLicenseManager.getInstance(this).saveDynamicAppConfig(documentSnapshot.getData());

                    }

                })
                .addOnFailureListener(e -> {
                });

    }

}