package com.hpp.daftree.ui;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.location.Address;
import android.location.Geocoder;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.security.crypto.EncryptedSharedPreferences;

import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;
import com.hpp.daftree.MainActivity;
import com.hpp.daftree.SplashActivity;
import com.hpp.daftree.models.DeviceBanManager;
import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.DeviceInfo;
import com.hpp.daftree.database.User;
import com.hpp.daftree.databinding.ActivityLoginBinding;
import com.hpp.daftree.databinding.DialogForgotPasswordBinding;
import com.hpp.daftree.databinding.DialogSyncBinding;
import com.hpp.daftree.dialogs.DeviceManagementDialog;
import com.hpp.daftree.dialogs.LanguageViewModel;
import com.hpp.daftree.helpers.LanguageManager;
import com.hpp.daftree.models.AppLockManager;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.models.SnackbarHelper;
import com.hpp.daftree.models.UUIDGenerator;
import com.hpp.daftree.syncmanagers.FirestoreRestoreHelper;
import com.hpp.daftree.syncmanagers.FirestoreSyncManager;
import com.hpp.daftree.syncmanagers.SyncPreferences;
import com.hpp.daftree.utils.AdsGoogleRewarder;
import com.hpp.daftree.utils.DialogHelper;
import com.hpp.daftree.utils.GoogleAuthHelper;
import com.hpp.daftree.utils.LicenseManager;
import com.hpp.daftree.utils.ReferralManager;
import com.hpp.daftree.utils.SecureLicenseManager;
import com.hpp.daftree.utils.SyncPreferencesLicence;
import com.hpp.daftree.utils.VersionManager;
import com.hpp.daftree.viewmodels.ProfileViewModel;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.security.crypto.MasterKey;
import androidx.activity.OnBackPressedCallback;

public class LoginActivity extends BaseActivity {
    private ActivityLoginBinding binding;
    //    private LoginViewModel viewModel;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    //    private CallbackManager facebookCallbackManager;
    private static final String TAG = "LoginActivity";
    private Executor executor;
    private AlertDialog accountDeletedDialog;
    private SecureLicenseManager licenceData;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;
    private FirebaseAuth firebaseAuth;
    private LanguageManager languageManager;
    private FirebaseFunctions firebaseFunctions;
    //    private GoogleSignInClient mGoogleSignInClient;
    private static final int STORAGE_PERMISSION_CODE = 1001;
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 201;
    private SyncPreferences sharedPreferences;
    private GoogleAuthHelper googleAuthHelper;
    private LicenseManager licenseManager;
    private ReferralManager referralManager;
    private AppDatabase db;
    private FirebaseFirestore firestore;
    private DaftreeRepository repository;
    private String referrerUid;
    private boolean isNewUser = false;
    private boolean isSyncDialogShowing = false;
    private int count = 0;
    private int count2 = 0;
    private View rootView;
    private static final int LOGIN_TIMEOUT = 60000 * 3; // 30 ثانية
    private Handler loginTimeoutHandler;
    private AppLockManager lockManager;
    private LanguageViewModel languageViewModel;
    private ProfileViewModel viewModelUserProfile; // لاستدعاء بيانات المستخدم
    private AlertDialog progressDialog;
    private boolean isRegistrationInProgress = false;
    private ActivityResultLauncher<Intent> restartMainActivityLauncher;
    private boolean isWaitingForEmailVerification = false;
    private Handler verificationHandler =new Handler(Looper.getMainLooper());// new Handler();
    private static final int VERIFICATION_CHECK_INTERVAL = 3000; // كل 3 ثوانٍ
    private static final int MAX_VERIFICATION_CHECKS = 200; // أقصى 10 دقائق انتظار
    private int verificationCheckCount = 0;
    private FirebaseUser pendingVerificationUser;
    private boolean isEmailVerificationInProgress = false;
    private AlertDialog verificationAlertDialog;
    private CountDownTimer verificationCountDownTimer;
    private boolean isFunctionCallFailed = false;
    private String verificationId;
    private String userEmail;
    private String userNameToSave;
    private PhoneAuthProvider.ForceResendingToken resendToken;
    private View keyboardSpacer;
    public interface UserCheckCallback {
        void onCheckComplete(boolean isDeleted);
    }
    private final GoogleAuthHelper.AuthCallback mainAuthCallback = new GoogleAuthHelper.AuthCallback() {

        @Override
        public void onAccountInfoReceived(String email, String token, String provider) {
            // ✅ الخطوة 1: استلام البيانات من (جوجل/فيسبوك) قبل دخول فايرباس
            updateProgressDialog(getString(R.string.check_regestiration_account));

            checkIfUserDeleted(email, new UserCheckCallback() {
                @Override
                public void onCheckComplete(boolean isDeleted) {
                    if (isDeleted) {
                        // الحساب محذوف: نوقف العملية وننظف الجلسة
                        hideProgressDialog();
                        googleAuthHelper.signOut();
                        dialogeDelete(); // عرض رسالة الحذف للمستخدم
                        isRegistrationInProgress = false;

                    } else {
                        // الحساب سليم: نأمر الهيلبر بإكمال الدخول لفايرباس
                        updateProgressDialog(getString(R.string.preparing_account));
                        googleAuthHelper.completeFirebaseSignIn(token, provider);
                    }
                }
            });
        }
        @Override
        public void onSignInSuccess(FirebaseUser user, AuthResult authResult) {
            cancelLoginTimeout();
            hideProgressDialog();
            isRegistrationInProgress = false;

            Log.d(TAG, "تم تسجيل الدخول بنجاح - Provider: " + user.getProviderId());

            // معالجة الإيميل المفقود (لأن فايرباس يضع شرطة "-" لحسابات فيسبوك المسجلة برقم هاتف)
            String safeEmail = user.getEmail();
            if (safeEmail == null || safeEmail.isEmpty()) {
                safeEmail = GoogleAuthHelper.facebookEmail + "@facebook.com"; // نستخدم نفس الإيميل الوهمي المبني على المعرف
            }
            userEmail=safeEmail;
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("userUID", user.getUid());
            licenceData.setUserUID(user.getUid());
            editor.putString("email", safeEmail); // نستخدم الإيميل الآمن
            licenceData.setEmail(safeEmail);
            editor.putBoolean("isUserUIDAdded", true);
            editor.apply();

            isNewUser = authResult.getAdditionalUserInfo().isNewUser();
            if (isNewUser) {
                editor.putBoolean("isNewUser", true);
                editor.apply();
                handleNewUserRegistration(user);
            } else {
                performDeviceLicenseCheck(user);
            }
        }
        @Override
        public void onSignInFailure(String error) {
            cancelLoginTimeout();
            hideProgressDialog();
            isRegistrationInProgress = false;
            SnackbarHelper.showSnackbar(binding.getRoot(), error, SnackbarHelper.SnackbarType.ERROR);
        }

        @Override
        public void onSignInProgress(String message) {
            updateProgressDialog(message);
        }

        @Override
        public void onSignOutSuccess() { }
    };
    // مشغل لطلب صلاحية الموقع

    private FusedLocationProviderClient fusedLocationClient;
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {

                if (isGranted) {
                    setupLocalCurrencyAndProceed();
                } else {
//                    Toast.makeText(this, "لم يتم منح الإذن، سيتم إضافة العملة الافتراضية.", Toast.LENGTH_LONG).show();
                    SnackbarHelper.showSnackbar(binding.getRoot(), getString(R.string.location_permission_denied), SnackbarHelper.SnackbarType.ERROR);

                    addDefaultCurrencyAndProceed();
                }
            });
    private SharedPreferences prefs, referral_prefs;
    private boolean isFirstRun;

    private boolean isApplyingLanguage = false;
    private String lockType = "";
    FirebaseUser currentUser;
    private boolean isGuest = false;
    private String guestUID = "";
    private boolean isRegisterGuestMode = false;
    private DeviceBanManager deviceBanManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        binding = DataBindingUtil.setContentView(this, R.layout.activity_login);
        languageManager = LanguageManager.getInstance(this);
        LanguageManager.applyLocale(this, languageManager.getCurrentLanguage());

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupKeyboardSpacer();
        //   binding.getRoot().setBackgroundColor(R.color.primary);  //Color.parseColor("#2196F3"));
        MyApplication.applyGlobalTextWatcher(binding.getRoot());
        rootView = binding.getRoot();
        // تسجيل الضيف
        isRegisterGuestMode = getIntent().getBooleanExtra("registerGuest", false);
        if (isRegisterGuestMode) {
            // إخفاء topContainer و tvGuest
//            binding.topContainer.setVisibility(View.GONE);
            binding.tvGuest.setVisibility(View.GONE);
        }
        deviceBanManager = new DeviceBanManager(this);
        firebaseFunctions = FirebaseFunctions.getInstance();
        setupFacebookLogin();
        prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        referral_prefs = getSharedPreferences("referral_prefs", MODE_PRIVATE);
        licenceData = SecureLicenseManager.getInstance(this);
        isFirstRun = prefs.getBoolean("first_run", true);
        isNewUser = prefs.getBoolean("isNewUser", false);
        referrerUid = referral_prefs.getString("referrer_uid", "");
        String referrer_Uid = getIntent().getStringExtra("REFERRER_UID");
        if (referrer_Uid != null && !referrer_Uid.isEmpty()) {
            referrerUid = referrer_Uid;
        }

        firebaseAuth = FirebaseAuth.getInstance();
        db = AppDatabase.getDatabase(getApplicationContext());
        repository = new DaftreeRepository(getApplication());
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        firestore = FirebaseFirestore.getInstance();
        referralManager = new ReferralManager(this);
        // استخدام GoogleAuthHelper
        licenseManager = new LicenseManager(this);
//        applySavedLanguage();
        firebaseAuth = FirebaseAuth.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        sharedPreferences = new SyncPreferences(this);
        lockManager = new AppLockManager(this);
        viewModelUserProfile = new ViewModelProvider(this).get(ProfileViewModel.class);
        languageViewModel = new ViewModelProvider(this).get(LanguageViewModel.class);


        lockType = lockManager.getLockType();
        googleAuthHelper = new GoogleAuthHelper(this, licenseManager, repository);

        restartMainActivityLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // تم إعادة فتح MainActivity
                    Log.d(TAG, "MainActivity أعيد فتحها بنجاح");
                }
        );
//        new Handler(Looper.getMainLooper()).postDelayed(() -> checkUserSession(), 500);

        executor = ContextCompat.getMainExecutor(this);
        setupDisclaimer();

//        binding.tvGuest.setOnClickListener(v -> {
//            guestUID = UUIDGenerator.generateSequentialUUID().toString();
//            SecureLicenseManager.getInstance(this).setGuest(true);
//            SecureLicenseManager.getInstance(this).setGuestUID(guestUID);
//        });
        isGuest = SecureLicenseManager.getInstance(this).isGuest();

        binding.tvGuest.setOnClickListener(v -> {
            SecureLicenseManager.getInstance(this).setGuest(true);
            isGuest = true;
//            startGuestSession();

            startGuestSession();
        });

        binding.googleSignInButton.setOnClickListener(v -> {
            if (!isNetworkAvailable()) {
                Toast.makeText(this, getString(R.string.no_internet), Toast.LENGTH_SHORT).show();
                return;
            }
//            //showLoading(true);
            loginGoogle();
        });
        binding.btnEmail1.setOnClickListener(v -> toggleFormVisibility("emailEnter"));
        binding.registerTextView.setOnClickListener(v -> {
            toggleFormVisibility("register");
        });
        binding.registerButton.setOnClickListener(v -> validateAndRegisterUser());
        binding.loginTextView.setOnClickListener(v -> toggleFormVisibility("emailEnter"));
        handleIncomingDeepLink(getIntent());

        // Trigger initial app open logic
        binding.loginButton.setOnClickListener(v -> validateAndLoginUser());

        // عند الضغط على نص "أنشئ حساباً"

        binding.forgotPasswordTextView.setOnClickListener(v -> {
            String currentEmail = binding.emailEditText.getText().toString().trim();
            showForgotPasswordDialog(currentEmail);
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!isLoginOptions) {
                    onBackToLoginOptions();
                    isLoginOptions = true;
                    return;
                }
                // 🔥 التحقق من وجود MainActivity في الـ Back Stack
                boolean hasMainActivity = isMainActivityInBackStack();

                if (hasMainActivity) {
                    // العودة إلى MainActivity الموجودة
                    Log.d(TAG, "العودة إلى MainActivity الموجودة");
                    navigateToMainActivity();
                } else {
                    // إنشاء MainActivity جديدة والعودة إليها
                    Log.d(TAG, "إنشاء MainActivity جديدة للعودة");
                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                }

                finish();

            }
        });
        handleAppOpen();
    }
    private void setupKeyboardSpacer() {
        keyboardSpacer = findViewById(R.id.keyboardSpacer);
        View root = findViewById(R.id.root);

        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect r = new Rect();
            root.getWindowVisibleDisplayFrame(r);
            int screenHeight = root.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;

            // إذا كان ارتفاع لوحة المفاتيح أكبر من 15% من الشاشة، فهي ظاهرة
            if (keypadHeight > screenHeight * 0.15) {
                keyboardSpacer.getLayoutParams().height = keypadHeight;
            } else {
                keyboardSpacer.getLayoutParams().height = 0;
            }
            keyboardSpacer.requestLayout();
        });
    }
    private void startGuestSession() {
        Log.d(TAG, "بدء جلسة الضيف");

        // ✅ فحص الحظر قبل بدء الجلسة
        deviceBanManager.checkDeviceBan(new DeviceBanManager.BanCheckListener() {
            @Override
            public void onCheckComplete(boolean isBanned, String reason) {
                runOnUiThread(() -> {
                    if (isBanned) {
                        showDeviceBanDialog(reason);
                        return;
                    }

                    // المتابعة مع جلسة الضيف
                    createNewUserGuest();
                    Log.d(TAG, "اول تشغيل للضيف");
                });
            }

            @Override
            public void onCheckError(String error) {
                runOnUiThread(() -> {
                    // في حالة الخطأ، نسمح بالمتابعة مع عرض تحذير
//                    SnackbarHelper.showSnackbar(binding.getRoot(),
//                            "تحذير: " + error,
//                            SnackbarHelper.SnackbarType.ERROR);
                    createNewUserGuest();
                    Log.d(TAG, "اول تشغيل للضيف");
                });
            }
        });
    }
    boolean isDialogeShown = false;


    private void showDeviceBanDialog(String banReason) {
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
                    finishAffinity(); // إغلاق التطبيق completamente
                })
                .setCancelable(false)
                .setIcon(R.drawable.ic_warning)
                .show();
    }

    /**
     * تهيئة جلسة الضيف
     */
    private void initializeGuestSession() {
        updateProgressDialog(getString(R.string.preparing_basic_data));

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // التحقق مما إذا كان هذا أول تشغيل للضيف
            if (isFirstRun) {
                createNewUserGuest();
                Log.d(TAG, "اول تشغيل للضيف");
            } else {
                // إذا لم يكن أول تشغيل، انتقل مباشرة
                guestUID = SecureLicenseManager.getInstance(this).guestUID();
                Log.d(TAG, "ضيف قديم " + " guestUID: " + guestUID);
                proceedToGuestMain();
            }
        }, 1000);
    }

    private void createNewUserGuest() {
        try {
            Log.d(TAG, "start انشاء حساب للضيف");

            updateProgressDialog(getString(R.string.preper_guest_account));
            String guestUid = UUIDGenerator.generateSequentialUUID().toString();
            SecureLicenseManager.getInstance(this).setGuestUID(guestUid);
            guestUID = guestUid;
            FirebaseFirestore firestore = FirebaseFirestore.getInstance();
            // ✅ التأكد من أن guestUID غير فارغ
            if (TextUtils.isEmpty(guestUID)) {
                guestUID = UUIDGenerator.generateSequentialUUID().toString();
                SecureLicenseManager.getInstance(this).setGuestUID(guestUID);
            }
            Log.d(TAG, "انشاء حساب للضيف" + " guestUID: " + guestUID + " isGuest(): " + isGuest);
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

            DeviceInfo currentDevice = licenseManager.getCurrentDeviceInfo();
            String deviceId = currentDevice.getDeviceId();
            newUser.setDeviceId(deviceId);
            // ✅ حفظ المستخدم محلياً أولاً في SQLite
            saveGuestUserLocally(newUser);

            // ✅ محاولة الحفظ في Firestore إذا كان هناك اتصال
            showProgressDialog(getString(R.string.preper_guest_account));
            checkLocationPermission();
        } catch (Exception e) {
            Log.e(TAG, "createNewUserGuest Error: " + e);
            // ✅ الاستمرار حتى مع وجود خطأ
//            setupGuestData();
            showProgressDialog(getString(R.string.preper_guest_account));
            checkLocationPermission();
        }
    }

    // ✅ دالة جديدة لحفظ الضيف محلياً في SQLite
    private void saveGuestUserLocally(User guestUser) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                // حفظ في جدول users المحلي مع تمييزه كضيف
                db.userDao().upsert(guestUser);
                Log.d(TAG, "تم حفظ المستخدم الضيف محلياً في SQLite: " + guestUser.getOwnerUID());
            } catch (Exception e) {
                Log.e(TAG, "خطأ في حفظ الضيف محلياً: " + e.getMessage());
            }
        });
    }

    private void addCurrencyToDatabaseForGuest(String name) {
        Log.d(TAG, "إضافة العملة للضيف: " + name);

        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                // التحقق من عدم وجود عملات مسبقاً
                int currencyCount = db.currencyDao().getCurrencyCount();
                if (currencyCount == 0) {
                    Currency existing = repository.getCurrencyByName(name);
                    if (existing == null) {
                        Currency newCurrency = new Currency();
                        newCurrency.setName(name);
                        newCurrency.setOwnerUID(guestUID);
                        newCurrency.setSyncStatus("NEW");
                        newCurrency.setDefault(true);
                        newCurrency.setFirestoreId(UUIDGenerator.generateSequentialUUID().toString());
                        newCurrency.setLastModified(System.currentTimeMillis());
                        sharedPreferences.setLocalCurrency(name.trim());
                        MyApplication.defaultCurrencyName = name.trim();
                        db.currencyDao().insert(newCurrency);
                        Log.d(TAG, "تم إضافة العملة: " + name);
                    }
                }

                // التحقق من أنواع الحسابات
                int accountTypeCount = db.accountTypeDao().getAccountTypeCount();
                if (accountTypeCount == 0) {
                    Resources localizedResources = LanguageManager.getLocalizedResources(this);
                    String[] accountTypes = {
                            localizedResources.getString(R.string.account_type_customer),
                            localizedResources.getString(R.string.account_type_supplier),
                            localizedResources.getString(R.string.account_type_general)
                    };

                    for (String type : accountTypes) {
                        AccountType account_Type = repository.getAccountTypeByName(type);
                        if (account_Type == null) {
                            AccountType accountType = new AccountType();
                            accountType.setName(type);
                            accountType.setOwnerUID(guestUID);
                            accountType.setFirestoreId(UUIDGenerator.generateSequentialUUID().toString());
                            accountType.setSyncStatus("NEW");
                            accountType.setDefault(true);
                            accountType.setLastModified(System.currentTimeMillis());
                            db.accountTypeDao().insert(accountType);
                            Log.d(TAG, "تم إضافة نوع الحساب: " + type);
                        }
                    }
                }

                // تهيئة المستخدم الافتراضي
                MyApplication.initializeDefaultUser(getApplicationContext(), db);

                runOnUiThread(() -> {
                    hideProgressDialog();
                    Log.d(TAG, "تم إعداد بيانات الضيف بنجاح");
                    proceedToGuestMain();
                });

            } catch (Exception e) {
                Log.e(TAG, "خطأ في إعداد بيانات الضيف", e);
                runOnUiThread(() -> {
                    hideProgressDialog();
                    SnackbarHelper.showSnackbar(binding.getRoot(),
                            "خطأ في إعداد البيانات",
                            SnackbarHelper.SnackbarType.ERROR);
                });
            }
        });
    }
    private void proceedToGuestMain() {
        Log.d(TAG, "الانتقال إلى الشاشة الرئيسية كضيف");

        // تحديث حالة first_run
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("first_run", false);
        editor.apply();

        hideProgressDialog();

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void guestUserData() {
        Log.d(TAG, " الضيف");
        if (isFirstRun) {
            Log.d(TAG, "1 الضيف");
            // إذا كان أول تشغيل، ابدأ جلسة الضيف
            startGuestSession();

        } else {

            Log.d(TAG, "2 الضيف");
            // إذا لم يكن أول تشغيل، انتقل مباشرة
            String lockType = lockManager.getLockType();
            if (lockType != null && !lockType.isEmpty()) {
                isAppLocked = true;
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    startActivity(new Intent(this, LockScreenActivity.class));
                    finish();
                }, 300);
            } else {
                proceedToGuestMain();
            }

        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleDeepLink(intent);

    }

    private void handleDeepLink(Intent intent) {
        Uri data = intent.getData();
        if (data != null) {
            // معالجة الروابط من scheme daftree

            if ("daftree".equals(data.getScheme()) && "invite".equals(data.getHost())) {
                referrerUid = data.getQueryParameter("ref");
            }
            // معالجة الروابط من HTTPS
            else if ("https".equals(data.getScheme()) && "hpp-daftree.web.app".equals(data.getHost())) {
                referrerUid = data.getQueryParameter("ref");
            }
            // سجّل تفاصيل الرابط لأغراض التصحيح
            Log.d("DeepLink", "الرابط المستلم1: " + data.toString());
            Log.d("DeepLink", "كود الدعوة1: " + referrerUid);

            if (referrerUid != null && !referrerUid.isEmpty()) {
                // حفظ كود الدعوة في SharedPreferences للاستخدام لاحقًا
                SharedPreferences prefs = getSharedPreferences("referral_prefs", MODE_PRIVATE);
                prefs.edit().putString("referrer_uid", referrerUid).apply();

            }
        }
    }

    String savedLanguage = "";

    public void handleAppOpen() {


        if (isFirstRun) {

        } else {
            Log.e(TAG, " isGuest = " + isGuest);
//            isGuest() = SecureLicenseManager.getInstance(this).isGuest()();
            savedLanguage = prefs.getString("language", "en");
            languageManager.setLocale(savedLanguage);
            if (isRegisterGuestMode) {
                toggleFormVisibility("loginOptions");
            } else if (isGuest) {
                guestUserData();
            } else {

                handleAfterLanguageSelection();
            }
        }
    }

    boolean isDelete = false;

    private void dialogeDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.user_remove_tit))
                .setMessage(getString(R.string.user_remove))
                .setPositiveButton(getString(R.string.ok), (dialog, which) -> {
                    // تنفيذ عملية تسجيل الخروج الكاملة
//                    performLogout();
                    FirebaseAuth.getInstance().signOut();
                    returnToMainActivity();
                })
                .setCancelable(false)
                .setIcon(R.drawable.ic_alert)
                .show();
    }

    boolean isAppLocked = false;

    public void onBackToLoginOptions() {
        toggleFormVisibility("loginOptions");
    }
    private boolean isMainActivityInBackStack() {
        android.app.ActivityManager activityManager = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<android.app.ActivityManager.RunningTaskInfo> runningTasks = activityManager.getRunningTasks(Integer.MAX_VALUE);

        for (android.app.ActivityManager.RunningTaskInfo task : runningTasks) {
            if (task.topActivity.getClassName().equals(MainActivity.class.getName())) {
                return true;
            }
        }
        return false;
    }
    private void navigateToMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }

    boolean isLoginOptions = true;

    private void toggleFormVisibility(String form) {
        binding.topContainer.setVisibility(View.VISIBLE); // Always show top unless in specific modes
        binding.loginOptions.setVisibility(View.GONE);
        binding.emailEnterForm.setVisibility(View.GONE);
        binding.registerForm.setVisibility(View.GONE);
        binding.getRoot().setBackgroundColor(Color.WHITE); // Default natural background

        switch (form) {
            case "initial":
                binding.getRoot().setBackgroundColor(Color.parseColor("#2196F3"));
                hideAllExceptTopContainer();
                break;
            case "loginOptions":
                showLoginOptions();
                isLoginOptions = true;
                break;
            case "emailEnter":
                isLoginOptions = false;
                binding.emailEnterForm.setVisibility(View.VISIBLE);
                // binding.topContainer.setVisibility(View.GONE);
                break;
            case "register":
                isLoginOptions = false;
                binding.registerForm.setVisibility(View.VISIBLE);
                // binding.topContainer.setVisibility(View.GONE);
                break;
            case "lock":
                // binding.topContainer.setVisibility(View.GONE);
                break;
        }
    }

    private void hideAllExceptTopContainer() {
        binding.topContainer.setVisibility(View.VISIBLE);
        binding.loginOptions.setVisibility(View.GONE);
        binding.emailEnterForm.setVisibility(View.GONE);
        binding.registerForm.setVisibility(View.GONE);
        fadeIn(binding.icLogo);
        fadeIn(binding.tvWelcome);
        fadeIn(binding.tvAppName);
    }

    private void showLoginOptions() {
        binding.getRoot().setBackgroundColor(Color.WHITE);
        binding.loginOptions.setVisibility(View.VISIBLE);
    }


    private void fadeIn(View view) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f);
        animator.setDuration(800);
        animator.start();
    }

    private void setupDisclaimer() {
        String text = getString(R.string.agreement_text);
        SpannableString spannable = new SpannableString(text);

        ClickableSpan termsSpan = new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://hpp-daftree.web.app/terms.html")));
            }
        };
        ClickableSpan privacySpan = new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://hpp-daftree.web.app/privacy.html")));
            }
        };

        int termsStart = text.indexOf(getString(R.string.terms_cond));
        int termsEnd = termsStart + (getString(R.string.terms_cond)).length();
        spannable.setSpan(termsSpan, termsStart, termsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);// يشير الخطا الى هنا

        int privacyStart = text.indexOf(getString(R.string.privacy_cond));
        int privacyEnd = privacyStart + (getString(R.string.privacy_cond)).length();
        spannable.setSpan(privacySpan, privacyStart, privacyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        binding.tvAgreement.setText(spannable);
        binding.tvAgreement.setMovementMethod(LinkMovementMethod.getInstance());
    }


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
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.currency_setup_title))
                .setMessage(getString(R.string.currency_setup_message))
                .setPositiveButton(getString(R.string.use_location), (dialog, which) -> {

                    requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
                })
                .setNegativeButton(getString(R.string.skip_location), (dialog, which) ->
                        addDefaultCurrencyAndProceed())
                .setCancelable(false)
                .show();
    }

    @SuppressLint("MissingPermission")
    private void setupLocalCurrencyAndProceed() {
        Log.d(TAG, getString(R.string.detecting_local_currency));
        updateProgressDialog(getString(R.string.detecting_local_currency));
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                try {
                    Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                    List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                    assert addresses != null;
                    String countryCode = addresses.get(0).getCountryCode();

//                    String countryCode = geocoder.getFromLocation(location.getLatitude(),location.getLongitude(), 1).get(0).getCountryCode();
                    Locale locale = new Locale.Builder().setRegion(countryCode).build();//new Locale("", countryCode);
                    java.util.Currency currencyInfo = java.util.Currency.getInstance(locale);
                    String currencyName = currencyInfo.getDisplayName(Locale.forLanguageTag(savedLanguage));
                    String currencyCode = currencyInfo.getCurrencyCode();
                    Log.d("LocalCurrency", "العملة المكتشفة: " + "language: " + savedLanguage + "currencyName: " + currencyName + "countryCode: " + countryCode);
                    addCurrencyToDatabase(currencyName);
                } catch (Exception e) {
                    Log.e("LocalCurrency", "فشل تحديد العملة، سيتم استخدام الافتراضية.", e);
                    addDefaultCurrencyAndProceed();
                }
            } else {
                Log.w("LocalCurrency", "الموقع غير متاح، سيتم استخدام العملة الافتراضية.");
                addDefaultCurrencyAndProceed();
            }
        });
    }

    private void addDefaultCurrencyAndProceed() {
        addCurrencyToDatabase(getString(R.string.local_currency));
    }

    private void addCurrencyToDatabase(String name) {
        String uid;
        if (isGuest) {
            uid = guestUID;
        } else {
            uid = FirebaseAuth.getInstance().getUid();
        }
        Log.d(TAG, "إضافة العمله");
        AppDatabase.databaseWriteExecutor.execute(() -> {
            int currencyCount = db.currencyDao().getCurrencyCount();
            int accTypeCount = db.accountTypeDao().getAccountTypeCount();
//            if (currencyCount > 0) {
//                Log.d(TAG, "تم إضافة العملة مسبقاً، تخطي الإضافة");
//                runOnUiThread(() -> {
//                    //showLoading(false);
//                    navigateToMainActivity(true);
//                });
//                return;
//            }

            if (db.currencyDao().getCurrencyCount() < 1) {
                Currency existing = repository.getCurrencyByName(name);
                Log.e(TAG, "defaultCurrencyName: " + name);
                if (existing == null) {
                    Currency newCurrency = new Currency();
                    newCurrency.setName(name);
                    newCurrency.setOwnerUID(uid);
                    newCurrency.setSyncStatus("NEW");
                    newCurrency.setDefault(true);
                    newCurrency.setFirestoreId(UUIDGenerator.generateSequentialUUID().toString());
                    newCurrency.setLastModified(System.currentTimeMillis());
                    sharedPreferences.setLocalCurrency(name.trim());
                    MyApplication.defaultCurrencyName = (name.trim());
                    db.currencyDao().insert(newCurrency);
                }
                Resources localizedResources = LanguageManager.getLocalizedResources(this);
                String[] accountTypes = {
                        localizedResources.getString(R.string.account_type_customer),
                        localizedResources.getString(R.string.account_type_supplier),
                        localizedResources.getString(R.string.account_type_general)
                };
                if (accTypeCount > 0) {
                    runOnUiThread(() -> {
                        //showLoading(false);
                        navigateToMainActivity(true);
                    });
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
                //showLoading(false);
                Log.d(TAG, "تمت إضافة العملة بنجاح، جاري الانتقال إلى الشاشة الرئيسية...");
                navigateToMainActivity(true);
            });
        });
    }

    private void showForgotPasswordDialog(String prefilledEmail) {
        DialogForgotPasswordBinding dialogBinding = DialogForgotPasswordBinding.inflate(LayoutInflater.from(this));

        dialogBinding.emailEditText.setText(prefilledEmail);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.forgot_password_title))
                .setView(dialogBinding.getRoot())
                .setPositiveButton(getString(R.string.forgot_password_send_button), null)
                .setNegativeButton(getString(R.string.forgot_password_cancel_button), (d, w) -> d.dismiss())
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                EditText emailEditText = dialogBinding.emailEditText;
                String email = emailEditText.getText().toString().trim();

                if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    Toast.makeText(this, getString(R.string.error_invalid_email), Toast.LENGTH_SHORT).show();
                    return;
                }

                sendPasswordResetEmail(email, dialog);
            });
        });

        dialog.show();
    }

    private void sendPasswordResetEmail(String email, AlertDialog dialog) {
        firebaseAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, getString(R.string.forgot_password_sent), Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                    } else {
                        Toast.makeText(this, getString(R.string.error_generic), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void navigateToSyncData() {
        hideProgressDialog();
        String uid = FirebaseAuth.getInstance().getUid() != null ?
                FirebaseAuth.getInstance().getUid() : "";
        SyncPreferencesLicence prefs = new SyncPreferencesLicence(this);
        if (uid == null) {
            Log.e(TAG, "المستخدم غير مسجل دخوله، إلغاء المزامنة");
            return;
        }
        SharedPreferences prefs1 = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs1.edit();
        editor.putString("userUID", FirebaseAuth.getInstance().getCurrentUser().getUid());
        licenceData.setUserUID(FirebaseAuth.getInstance().getCurrentUser().getUid());
        editor.putString("email", userEmail);
        licenceData.setEmail(userEmail);
        editor.putBoolean("isUserUIDAdded", true);
        editor.apply();
        SharedPreferences prefs2 = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        prefs2.edit().putString("uid", FirebaseAuth.getInstance().getUid()).apply();

        repository.setUserUID(FirebaseAuth.getInstance().getUid());

        if (!prefs.isFirstSyncComplete()) {
            //showLoading(true);
            AppDatabase.databaseWriteExecutor.execute(() -> {

                runOnUiThread(() -> {
                    if (isNewUser) {
                        addDefaultData(db, this);
                        insertDefaultUser();
                        navigateToMainActivity(true);
                    } else {
                        runOnUiThread(() -> {
                            //showLoading(false);
                            showSyncDialog();
                        });
                    }
                });
            });
        } else {
            //showLoading(false);
//            FirestoreSyncManager syncManager = FirestoreSyncManager.getInstance();
//            syncManager.startListening(new DaftreeRepository(getApplication()),this);
            navigateToMainActivity(false);
        }
    }

    private void insertDefaultUser() {
        db.databaseWriteExecutor.execute(() -> {
            runOnUiThread(() -> {
                MyApplication.initializeDefaultUser(getApplicationContext(), db);
            });
        });
    }

    private void handleIncomingDeepLink(Intent intent) {
        Uri data = intent.getData();
        if (data != null && "daftree".equals(data.getScheme())) {
            referrerUid = data.getQueryParameter("ref");
            // سجّل تفاصيل الرابط لأغراض التصحيح
            Log.d("DeepLink", "الرابط المستلم: " + data.toString());
            Log.d("DeepLink", "كود الدعوة: " + referrerUid);

            if (referrerUid != null && !referrerUid.isEmpty()) {
                Log.d("DeepLink", "تم استقبال دعوة من: " + referrerUid);
                referralManager.saveReferrerUid(referrerUid);

            }
        }
    }

    void addDefaultData(AppDatabase db, Context context) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) uid = "";

        try {
            String finalUid = uid;
            db.runInTransaction(() -> {

                insertAccountType(db, getString(R.string.clients_account_type), finalUid);
                insertAccountType(db, getString(R.string.account_type_supplier), finalUid);
                insertAccountType(db, getString(R.string.account_type_general), finalUid);
            });
        } catch (Exception e) {
            Log.e("AppDatabase", "Error in addDefaultData: " + e.toString());
        }
    }

    private static void insertAccountType(AppDatabase db, String name, String uid) {
        AccountType accountType = new AccountType();
        accountType.name = name;
        accountType.setOwnerUID(uid);
        accountType.setFirestoreId(UUIDGenerator.generateSequentialUUID().toString());
        accountType.setSyncStatus("NEW");
        accountType.setDefault(true);
        accountType.setLastModified(System.currentTimeMillis());
        db.accountTypeDao().insert(accountType);
    }

    boolean isSyncDialogShow = false;

    private void showSyncDialog() {
        if (isSyncDialogShow) return;
        isSyncDialogShow = true;
        count2 += 1;
        Log.e(TAG, "showSyncDialog Start Counting: " + count2);
        DialogSyncBinding dialogBinding = DialogSyncBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogBinding.getRoot())
                .setCancelable(false)
                .create();
        dialog.show();

        FirestoreRestoreHelper restoreHelper = new FirestoreRestoreHelper(this);

        restoreHelper.startRestore(new FirestoreRestoreHelper.RestoreListener() {
            @Override
            public void onProgressUpdate(String message, int progress, int total) {
                runOnUiThread(() -> {
                    dialogBinding.syncMessageTextview.setText(message);
                    dialogBinding.syncProgressbar.setMax(total);
                    dialogBinding.syncProgressbar.setProgress(progress);
                    dialogBinding.syncProgressTextview.setText(progress + "/" + total);
                });
            }

            @Override
            public void onComplete(String message) {
                runOnUiThread(() -> {
                    dialog.dismiss();
                    new SyncPreferencesLicence(LoginActivity.this).setFirstSyncComplete(true);
                    navigateToMainActivity(false);
                });
            }
            @Override
            public void onSucces(String message) {
                runOnUiThread(() -> {
                    dialog.dismiss();
                    new SyncPreferencesLicence(LoginActivity.this).setFirstSyncComplete(true);
                    navigateToMainActivity(false);
                });
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Log.e("FirestoreRestoreHelper", "Error during restore: " + error);
                    dialog.dismiss();
                    isSyncDialogShowing = false;
                    new AlertDialog.Builder(LoginActivity.this)
                            .setTitle(getString(R.string.sync_title))
                            .setMessage(getString(R.string.error_sync_message, error))
                            .setPositiveButton(getString(R.string.ok), (d, w) -> navigateToMainActivity(false))
                            .show();
                });
            }
        });
    }
    private void navigateToMainActivity(boolean showWelcomeBanner) {
        // التحقق من وجود إحالة معلقة
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean isPendingReferral = prefs.getBoolean(AdsGoogleRewarder.KEY_REFERRAL_PENDING, false);

        if (isPendingReferral) {
            long expiryTime = prefs.getLong("premium_expiry", 0);
            if (expiryTime != 0) {
                // عرض ديالوج المكافأة وتعليق الانتقال
                showReferralRewardDialog(expiryTime, new Runnable() {
                    @Override
                    public void run() {
                        // مسح حالة الإحالة المعلقة بعد النقر
                        prefs.edit().putBoolean(AdsGoogleRewarder.KEY_REFERRAL_PENDING, false).apply();
                        prefs.edit().putBoolean(AdsGoogleRewarder.KEY_GIFT_RECEIVED, true).apply();
                        // الآن ننتقل للشاشة الرئيسية
                        proceedToMainActivityInternal(showWelcomeBanner);
                    }
                });
                return; // نوقف التنفيذ هنا
            }
        }

        // إذا لم تكن هناك إحالة معلقة، ننتقل مباشرة
        proceedToMainActivityInternal(showWelcomeBanner);
    }

    private void proceedToMainActivityInternal(boolean showWelcomeBanner) {
        // إلغاء أي عمليات معلقة أولاً
        cancelLoginTimeout();
        hideProgressDialog();

        // التأكد من إزالة جميع المراقبين
        if (viewModelUserProfile.getUserProfile().hasObservers()) {
            viewModelUserProfile.getUserProfile().removeObservers(this);
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        if (showWelcomeBanner) {
            intent.putExtra("SHOW_WELCOME_BANNER", true);
        }

        if (isRegisterGuestMode) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("isNewUser", false);
                editor.apply();
                SecureLicenseManager.getInstance(this).setGuest(false);
                isGuest = (false);
                intent.putExtra("FORCE_REFRESH", true);
                restartAppFromSplash();
                isOldUser = false;
            }, 100);
        } else if (isGuest) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("isNewUser", false);
                editor.apply();
                restartAppFromSplash();
            }, 100);
        } else {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("isNewUser", false);
                editor.apply();
                SecureLicenseManager.getInstance(this).setGuest(false);
                isGuest = (false);
                intent.putExtra("FORCE_REFRESH", true);
                restartAppFromSplash();
                isOldUser = false;
            }, 100);
        }
    }
    private void restartAppFromSplash() {
        Log.d(TAG, "إعادة تشغيل التطبيق مع إعادة تهيئة اتصال Room");

        // 1. إغلاق اتصال Room الحالي فقط (بدون حذف البيانات)
        closeRoomConnection();

        // 2. الانتظار ثم إعادة التشغيل
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(this, SplashActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            isOldUser = false;
            finishAffinity();

            // 3. قتل العملية لضمان إعادة التهيئة الكاملة
            android.os.Process.killProcess(android.os.Process.myPid());
        }, 1000);
    }

    private void closeRoomConnection() {
        try {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                AppDatabase db = AppDatabase.getDatabase(this);
                if (db != null) {
                    db.close();
                    Log.d(TAG, "تم إغلاق اتصال Room بنجاح - البيانات محفوظة");
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "خطأ في إغلاق اتصال Room: " + e.getMessage());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // إلغاء البصمة عند مغادرة الشاشة
        if (biometricPrompt != null) {
            try {
                biometricPrompt.cancelAuthentication();
            } catch (Exception e) {
                Log.e(TAG, "Error canceling biometric authentication", e);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeDatabase();
            } else {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    new AlertDialog.Builder(this)
                            .setTitle("الإذن مطلوب")
                            .setMessage("يجب منح إذن التخزين لحفظ البيانات")
                            .setPositiveButton("موافق", (dialog, which) -> requestStoragePermission())
                            .setNegativeButton("إلغاء", null)
                            .show();
                } else {
                    Toast.makeText(this, "تم رفض الإذن بشكل دائم. الرجاء تمكينه من إعدادات التطبيق", Toast.LENGTH_LONG).show();

                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                }
            }
        }
        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "تم منح صلاحية الإشعارات.");
            } else {
                Log.w(TAG, "تم رفض صلاحية الإشعارات من المستخدم.");
                // يمكنك إظهار Toast أو Snackbar هنا لإبلاغه.
            }
        }
    }

    private void requestStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            initializeDatabase();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
        }
    }

    private void performDeviceLicenseCheck(FirebaseUser user) {
        Log.d(TAG, "بدء فحص ترخيص الجهاز للمستخدم: " + user.getEmail());

        updateProgressDialog(getString(R.string.checking_device_license));

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        DocumentReference userDocRef = firestore.collection("users").document(user.getUid());
        VersionManager versionManager = new VersionManager(this);

        if (isNewUser) {
            startLicenseCheck(user);
            return;
        }

        userDocRef.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.e(TAG, "فشل الوصول لمستند المستخدم: " + task.getException());
                hideProgressDialog();
                SnackbarHelper.showSnackbar(binding.getRoot(), getString(R.string.error_access_user_doc), SnackbarHelper.SnackbarType.ERROR);
                return;
            }

            if (!task.getResult().exists()) {
                if ((!versionManager.getFirestoreUser_isAdded()) && !isNewUser) {
                    createNewUser(userDocRef, user);
                    new VersionManager(this).setFirst_upgrade(false);
                    return;
                } else {
                    startLicenseCheck(user);
                }
                return;
            }
            startLicenseCheck(user);
        });
    }

    private void createNewUser(DocumentReference userRef, FirebaseUser firebaseUser) {
        try {
            LiveData<User> userLiveData = repository.getUserProfile();
            userLiveData.observeForever(new Observer<User>() {
                @Override
                public void onChanged(User localUser) {
                    userLiveData.removeObserver(this);
                    User newUser = new User();
                    newUser.setOwnerUID(firebaseUser.getUid());
                    newUser.setEmail(firebaseUser.getEmail());
                    // Use local data if available, otherwise use Firebase data
                    if (localUser != null) {
                        newUser.setName(localUser.getName());
                        newUser.setAddress(localUser.getAddress());
                        newUser.setCompany(localUser.getCompany());
                        newUser.setPhone(localUser.getPhone());
                    } else {
                        newUser.setName(firebaseUser.getDisplayName());
                        newUser.setName(getString(R.string.ar_long_text_20));
                        newUser.setCompany(getString(R.string.ar_long_text_20));
                        newUser.setAddress(getString(R.string.ar_text_10_1));
                        newUser.setPhone(getString(R.string.string_967_734_249_712));
                    }
                    newUser.setUserType("user");
                    newUser.setSuccessfulReferrals(0);
                    newUser.setIs_active(true);
                    newUser.setIs_premium(false);
                    newUser.setCreated_at(User.getCurrentLocalDateTime());
                    newUser.setLogin_count(1);
                    newUser.setDb_upgrade(1);
                    newUser.setMax_devices(LicenseManager.MAX_DEVICES);
                    newUser.setTransactions_count(0);
                    newUser.setMax_transactions(LicenseManager.FREE_TRANSACTION_LIMIT);
                    newUser.setAd_rewards(0);
                    newUser.setReferral_rewards(0);

                    DeviceInfo currentDevice = licenseManager.getCurrentDeviceInfo();
                    newUser.getDevices().put(currentDevice.getDeviceId(), currentDevice);
                    SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                    boolean isPendingReferral = prefs.getBoolean(AdsGoogleRewarder.KEY_REFERRAL_PENDING, false);
                    if (isPendingReferral) {
                        long expiryTime = prefs.getLong("premium_expiry", 0);
                        newUser.setIs_premium(true);
                        if (expiryTime != 0) {
                            Date expiryDate = new Date(expiryTime);
                            Timestamp expiryTimestamp = new Timestamp(expiryDate);
                            newUser.setSubscription_expiry(expiryTimestamp);
                        }
                        newUser.setPlan_type("monthly");
                        newUser.setSubscriptionStatus("activated");
                        newUser.setPremium_activated_at(User.getCurrentLocalDateTime());
                        newUser.setPremium_activated_by("Google Ads");
                        newUser.setSubscription_start(Timestamp.now());
                        newUser.setLastModified(System.currentTimeMillis());
                    }else{
                        newUser.setIs_premium(false);
                        newUser.setSubscription_expiry(null);
                        newUser.setPlan_type(null);
                        newUser.setSubscriptionStatus(null);
                        newUser.setPremium_activated_at(null);
                        newUser.setPremium_activated_by(null);
                        newUser.setSubscription_start(null);
                        newUser.setLastModified(System.currentTimeMillis());
                    }
                    userRef.set(newUser).addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
//                           repository.updateUser(newUser);
//                           referralManager.applyReferralRewardIfAvailable(firebaseUser.getUid());
                            startLicenseCheck(firebaseUser);
                            new VersionManager(LoginActivity.this).setFirestoreUser_isAdded(true);
                        } else {

                        }
                    });
                }
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "createNewUserData Error: " + e);
        }
    }


    boolean isOldUser = false;

    private void startLicenseCheck(FirebaseUser user) {
        licenseManager.checkLicense().thenAccept(result -> {
            runOnUiThread(() -> {
                Log.d(TAG, "نتيجة فحص الترخيص: " + result.getMessage());
                Log.d(TAG, "تم تجاوز الحد: " + result.isDeviceLimitExceeded());
                Log.d(TAG, "الجهاز مرخص: " + result.isCurrentDeviceAuthorized());

                if (result.isSuccess()) {
                    if (result.isCurrentDeviceAuthorized()) {
                        Log.d(TAG, "الجهاز مرخص، المتابعة إلى الشاشة الرئيسية");
                        if (isNewUser) {
                            if (referrerUid != null && !referrerUid.isEmpty()) {
                                referralManager.applyReferralRewardIfAvailable(user.getUid());
                                SharedPreferences prefs = getSharedPreferences("referral_prefs", MODE_PRIVATE);
                                prefs.edit().remove("referrer_uid").apply();
                                checkLocationPermission();
                                SnackbarHelper.showSnackbar(binding.getRoot(), getString(R.string.reward_message), SnackbarHelper.SnackbarType.SUCCESS);
                            }
                        } else {
                            if (isRegisterGuestMode && isGuest) {
                                isOldUser = true;

                                deleteDatabaseCompletely().thenAccept(success -> {
                                    runOnUiThread(() -> {
                                        if (success) {
                                            Log.d(TAG, "تم حذف قاعدة البيانات بنجاح، جاري تحويل الحساب...");

                                            if (!guestUID.isEmpty()) {
                                                deleteGuestAccountsWithSameDevice();
                                            }
                                            AppDatabase.destroyInstance(); // تأكد من استدعاء هذا مرة أخرى للأمان
                                            // ⭐ إعادة إنشاء اتصال قاعدة البيانات بعد الحذف
                                            AppDatabase newDb = AppDatabase.getDatabase(getApplicationContext());
                                            db = newDb;
                                            repository.setUserUID(user.getUid());

                                            // ⭐ إعادة تعيين حالة المزامنة
                                            new SyncPreferencesLicence(LoginActivity.this).setFirstSyncComplete(false);

                                            // ⭐ الانتظار قليلاً لضمان اكتمال التهيئة
                                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                                SecureLicenseManager.getInstance(LoginActivity.this).setGuest(false);
                                                isGuest = false;
                                                hideProgressDialog();
                                                navigateToSyncData();
                                            }, 1000);
                                            checkDebtFeatureActivation(user);

                                        } else {
                                            hideProgressDialog();
                                            SnackbarHelper.showSnackbar(binding.getRoot(),
                                                    "فشل في مسح البيانات القديمة، يرجى إعادة المحاولة",
                                                    SnackbarHelper.SnackbarType.ERROR);
                                        }
                                    });
                                }).exceptionally(throwable -> {
                                    runOnUiThread(() -> {
                                        hideProgressDialog();
                                        Log.e(TAG, "خطأ في حذف قاعدة البيانات: " + throwable.getMessage());
                                        SnackbarHelper.showSnackbar(binding.getRoot(),
                                                "خطأ في تحويل الحساب: " + throwable.getMessage(),
                                                SnackbarHelper.SnackbarType.ERROR);
                                    });
                                    return null;
                                });

                            } else {
                                checkDebtFeatureActivation(user);
                                navigateToSyncData();
                            }
                        }
                    } else if (result.isDeviceLimitExceeded()) {
                        Log.e(TAG, "DeviceLimitExceeded: " + result.isDeviceLimitExceeded());
                        //showLoading(false);
                        SnackbarHelper.showSnackbar(binding.getRoot(), getString(R.string.device_limit_exceeded), SnackbarHelper.SnackbarType.ERROR);
                        showDeviceManagementDialog(result.getUser());
                    } else {
                        Log.d(TAG, "هناك مساحة لإضافة الجهاز - جاري إضافة الجهاز");
                        addCurrentDeviceToUser(user.getUid(), result.getUser());
                        checkDebtFeatureActivation(user);
                    }
                } else {
                    //showLoading(false);
                    SnackbarHelper.showSnackbar(binding.getRoot(), getString(R.string.error_add_device, result.getMessage()), SnackbarHelper.SnackbarType.ERROR);
                }
            });
        });
    }
// داخل LoginActivity.java
    private void checkDebtFeatureActivation(FirebaseUser user) {
        FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Boolean isEnabled = documentSnapshot.getBoolean("isDebtFeatureEnabled");
                        if (isEnabled != null && isEnabled) {
                            SharedPreferences.Editor editor = prefs.edit();

                            // 1. حفظ حالة التفعيل
                            editor.putBoolean("isDebtFeatureEnabled", true);
                            // 2. حفظ الرقم
                            String savedPhone = documentSnapshot.getString("phoneNumber");
                            editor.putString("savedPhoneNumber", savedPhone);

                            // 3. حفظ مفتاح الدولة (جديد)
                            String savedCountryCode = documentSnapshot.getString("countryCode");
                            editor.putString("savedCountryCode", savedCountryCode);

                            editor.apply();
                        } else {
                            prefs.edit().putBoolean("isDebtFeatureEnabled", false).apply();
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to check debt feature activation", e));
    }

    private CompletableFuture<Boolean> deleteDatabaseCompletely() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                // إغلاق اتصال قاعدة البيانات أولاً
                AppDatabase db = AppDatabase.getDatabase(this);
                if (db != null) {
                    db.close();
                    AppDatabase.destroyInstance();
                }

                // حذف جميع ملفات قاعدة البيانات
                String[] databaseNames = {
                        "daftree_database",
                        "daftree_database-wal",
                        "daftree_database-shm",
                        "daftree_database-journal"
                };

                boolean allDeleted = true;
                for (String dbName : databaseNames) {
                    File dbFile = getDatabasePath(dbName);
                    if (dbFile.exists()) {
                        boolean deleted = dbFile.delete();
                        Log.d(TAG, "حذف ملف " + dbName + ": " + (deleted ? "نجح" : "فشل"));
                        if (!deleted) allDeleted = false;
                    }
                }

                // حذف من مجلد databases
                File databasesDir = new File(getApplicationInfo().dataDir + "/databases");
                if (databasesDir.exists() && databasesDir.isDirectory()) {
                    File[] files = databasesDir.listFiles();
                    if (files != null) {
                        for (File file : files) {
                            if (file.getName().contains("daftree_database")) {
                                boolean deleted = file.delete();
                                Log.d(TAG, "حذف " + file.getName() + ": " + (deleted ? "نجح" : "فشل"));
                                if (!deleted) allDeleted = false;
                            }
                        }
                    }
                }

                // ⭐ إعادة تعيين جميع المتغيرات الثابتة والمخبأة
                //   MyApplication.defaultCurrencyName = null;
                sharedPreferences.setLocalCurrency(null);
                Log.d(TAG, "تم حذف قاعدة البيانات بالكامل بنجاح: " + allDeleted);
                future.complete(allDeleted);

            } catch (Exception e) {
                Log.e(TAG, "خطأ في حذف قاعدة البيانات: " + e.getMessage());
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    private void addCurrentDeviceToUser(String userId, User user) {
        DocumentReference userRef = firestore.collection("users").document(userId);
        DeviceInfo currentDevice = licenseManager.getCurrentDeviceInfo();

        Map<String, Object> deviceData = new HashMap<>();
        deviceData.put("deviceId", currentDevice.getDeviceId());
        deviceData.put("deviceName", currentDevice.getDeviceName());
        deviceData.put("deviceModel", currentDevice.getDeviceModel());
        deviceData.put("androidVersion", currentDevice.getAndroidVersion());
        deviceData.put("registeredAt", DeviceInfo.getCurrentLocalDateTime());
        deviceData.put("lastActiveAt", DeviceInfo.getCurrentLocalDateTime());
        deviceData.put("active", true);

        // إضافة الجهاز إلى قائمة الأجهزة
        Map<String, Object> updates = new HashMap<>();
        updates.put("devices." + currentDevice.getDeviceId(), deviceData);

        userRef.update(updates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "تم إضافة الجهاز بنجاح");

                // عرض رسالة نجاح باستخدام الكلاس المساعد
                SnackbarHelper.showSnackbar(
                        binding.getRoot(),
                        "تم إضافة الجهاز بنجاح",
                        SnackbarHelper.SnackbarType.SUCCESS
                );

                if (isNewUser) {
                    checkLocationPermission();
                } else if (isRegisterGuestMode && isGuest) {
                    isOldUser = true;
                    deleteDatabaseCompletely().thenAccept(success -> runOnUiThread(() -> {
                        if (success) {
                            if (!guestUID.isEmpty()) {
                                deleteGuestAccountsWithSameDevice();
                            }
                            AppDatabase.destroyInstance();
                            db = AppDatabase.getDatabase(getApplicationContext());
                            repository.setUserUID(userId);
                            new SyncPreferencesLicence(LoginActivity.this).setFirstSyncComplete(false);
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                SecureLicenseManager.getInstance(LoginActivity.this).setGuest(false);
                                isGuest = false;
                                hideProgressDialog();
                                navigateToSyncData();
                            }, 1000);
                        } else {
                            hideProgressDialog();
                            SnackbarHelper.showSnackbar(binding.getRoot(),
                                    "فشل في مسح البيانات القديمة، يرجى إعادة المحاولة",
                                    SnackbarHelper.SnackbarType.ERROR);
                        }
                    }));
                } else {
                    navigateToSyncData();
                }
            } else {
                //showLoading(false);
                Log.e(TAG, "فشل في إضافة الجهاز: " + task.getException().getMessage());

                // استخدام Snackbar من الكلاس المساعد لرسالة الخطأ
                SnackbarHelper.showSnackbar(binding.getRoot(), "فشل في إضافة الجهاز", SnackbarHelper.SnackbarType.ERROR,
                        "إعادة المحاولة", v -> addCurrentDeviceToUser(userId, user)
                );
            }
        });
    }

    private void showDeviceManagementDialog(User user) {
        List<DeviceInfo> devices = new ArrayList<>(user.getDevices().values());
        DeviceManagementDialog dialog = DeviceManagementDialog.newInstance(devices, licenseManager, true);

        dialog.setDialogListener(new DeviceManagementDialog.DialogListener() {
            @Override
            public void onDeviceRemoved() {
                // بعد إزالة جهاز، أعد محاولة إضافة الجهاز الحالي
                Log.d(TAG, "تم إزالة جهاز، جاري إضافة الجهاز الحالي");
                dialog.dismiss();
                addCurrentDeviceToUser(user.getOwnerUID(), user);
            }

            @Override
            public void onDismissed() {
                // إذا قرر المستخدم عدم إزالة أي جهاز، قم بتسجيل الخروج
                Log.d(TAG, "المستخدم قرر عدم إزالة أي جهاز، جاري تسجيل الخروج");
                //showLoading(false);
                //  Toast.makeText(LoginActivity.this, "تم إلغاء تسجيل الدخول.", Toast.LENGTH_SHORT).show();
                Snackbar.make(rootView, "تم إلغاء تسجيل الدخول.", Snackbar.LENGTH_SHORT).show();
                //  Snackbar.make(btnSave, "تم إلغاء عملية الحفظ", Snackbar.LENGTH_SHORT).show();
                performLogout();

            }
        });

        dialog.show(getSupportFragmentManager(), "DeviceManagementDialog");
    }

    private void performLogout() {
        // إيقاف المزامنة
        FirestoreSyncManager.getInstance().stopListening();
        // تسجيل الخروج من Firebase
        FirebaseAuth.getInstance().signOut();

        // مسح SharedPreferences
        SharedPreferences prefs = getSharedPreferences("prefs_uid", MODE_PRIVATE);
        prefs.edit().clear().apply();

        // مسح بيانات الترخيص المحلية
        SharedPreferences licensePrefs = getSharedPreferences("secure_license_prefs", MODE_PRIVATE);
        licensePrefs.edit().clear().apply();

        // مسح معرف الجهاز من التخزين المشفر
        SharedPreferences securePrefs = getSharedPreferences("secure_license_prefs", MODE_PRIVATE);
        securePrefs.edit().remove(LicenseManager.KEY_DEVICE_ID).apply();

        // مسح تفضيلات المزامنة
        sharedPreferences.setFirstSyncComplete(false);

        // مسح بيانات الترخيص المشفرة
        try {
            MasterKey masterKey = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            SharedPreferences encryptedPrefs = EncryptedSharedPreferences.create(
                    this,
                    "secret_shared_prefs", // اسم الملف
                    masterKey, // المفتاح الجديد
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            encryptedPrefs.edit().clear().apply();
        } catch (Exception e) {
            e.printStackTrace();
        }


        licenseManager.clearDeviceData();
        // حذف قاعدة البيانات المحلية تمامًا
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(this);
            db.close();

            File databaseFile = getDatabasePath("daftree_database");
            if (databaseFile.exists()) {
                databaseFile.delete();
            }

            File databaseWal = getDatabasePath("daftree_database-wal");
            if (databaseWal.exists()) {
                databaseWal.delete();
            }

            File databaseShm = getDatabasePath("daftree_database-shm");
            if (databaseShm.exists()) {
                databaseShm.delete();
            }
            googleAuthHelper.signOut();
        });

        // الانتقال لشاشة الدخول
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }


    private void initializeDatabase() {
        AppDatabase.getDatabase(this);
    }

    private void startLoginTimeout() {
        loginTimeoutHandler = new Handler(Looper.getMainLooper());
        loginTimeoutHandler.postDelayed(() -> {
            if (isLoadingShown()) {
                handleLoginTimeout();
            }
        }, LOGIN_TIMEOUT);
    }

    private boolean isLoadingShown() {
        return binding.progressBar.getVisibility() == View.VISIBLE;
    }

    private void handleLoginTimeout() {
        //showLoading(false);
        SnackbarHelper.showSnackbar(binding.getRoot(), "انتهت مهلة تسجيل الدخول. يرجى المحاولة مرة أخرى.", SnackbarHelper.SnackbarType.ERROR);

        // إذا كان المستخدم جديدًا، قم بحذف حسابه
//        if (isNewUser && firebaseAuth.getCurrentUser() != null) {
////            deleteNewUserAccount();
//            performLogout();
//        } else {
//            performLogout();
//        }
    }

    private void cancelLoginTimeout() {
        if (loginTimeoutHandler != null) {
            loginTimeoutHandler.removeCallbacksAndMessages(null);
        }
    }
    private void loginGoogle() {
        if (isRegistrationInProgress) return;

        startLoginTimeout();
        showProgressDialog(getString(R.string.connecting_google));

        // تأخير بسيط لضمان ظهور الـ Dialog
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            isRegistrationInProgress = true;
            // نمرر الكولباك الموحد الجديد
            googleAuthHelper.signIn(this, mainAuthCallback);
        }, 300);
    }

    void returnToMainActivity() {
        FirebaseAuth.getInstance().signOut();
        googleAuthHelper.signOut();
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    private void convertGuestToOfficialUser(FirebaseUser user, String name, String email) {

        showProgressDialog(getString(R.string.converting_guest_account));

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        String uid = user.getUid();
        guestUID = SecureLicenseManager.getInstance(this).guestUID();

        // إنشاء بيانات المستخدم الرسمي
        User officialUser = new User();
        officialUser.setOwnerUID(uid);
        officialUser.setEmail(email);
        officialUser.setName(name);
        officialUser.setGuestUID(guestUID);
        officialUser.setUserType("user");
        officialUser.setSuccessfulReferrals(0);
        officialUser.setIs_active(true);
        officialUser.setIs_premium(false);
        officialUser.setPhone(user.getPhoneNumber()!= null ? user.getPhoneNumber().toString():"+967780405427");
        officialUser.setProfileImageUri(user.getPhotoUrl() != null ? user.getPhotoUrl().toString():"");
        officialUser.setCreated_at(User.getCurrentLocalDateTime());
        officialUser.setLogin_count(1);
        officialUser.setDb_upgrade(1);
        officialUser.setMax_devices(LicenseManager.MAX_DEVICES);
        officialUser.setTransactions_count(SecureLicenseManager.getInstance(this).getTransactionsCount());
        officialUser.setMax_transactions(LicenseManager.FREE_TRANSACTION_LIMIT);
        officialUser.setAd_rewards(0);
        officialUser.setReferral_rewards(0);

        DeviceInfo currentDevice = licenseManager.getCurrentDeviceInfo();
        officialUser.getDevices().put(currentDevice.getDeviceId(), currentDevice);
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean isPendingReferral = prefs.getBoolean(AdsGoogleRewarder.KEY_REFERRAL_PENDING, false);

        if (isPendingReferral) {
            long expiryTime = prefs.getLong("premium_expiry", 0);
            officialUser.setIs_premium(true);
            if (expiryTime != 0) {
                Date expiryDate = new Date(expiryTime);
                com.google.firebase.Timestamp expiryTimestamp = new com.google.firebase.Timestamp(expiryDate);
                officialUser.setSubscription_expiry(expiryTimestamp);
            }
            officialUser.setPlan_type("monthly");
            officialUser.setSubscriptionStatus("activated");
            officialUser.setPremium_activated_at(User.getCurrentLocalDateTime());
            officialUser.setPremium_activated_by("Google Ads");
            officialUser.setSubscription_start(com.google.firebase.Timestamp.now());
            officialUser.setLastModified(System.currentTimeMillis());

            licenceData.setPremiumActivatedBy("Google Ads");
            licenceData.setSubscriptionExpiry(expiryTime);
            licenceData.setPlanType("monthly");
        }
        else {
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_YEAR, 30);
            long expiryTimeMillis = calendar.getTimeInMillis();
            Date expiryDate = new Date(expiryTimeMillis);
            // حفظ وقت انتهاء التجربة في SharedPreferences
            prefs.edit().putLong("trial_expire", expiryTimeMillis).apply();
            officialUser.setIs_premium(false);
            officialUser.setSubscription_expiry(null);
            officialUser.setPlan_type(null);
            officialUser.setSubscriptionStatus(null);
            officialUser.setPremium_activated_at(null);
            officialUser.setPremium_activated_by(null);
            officialUser.setSubscription_start(null);
            officialUser.setLastModified(System.currentTimeMillis());
        }

        // حفظ المستخدم في Firestore أولاً
        firestore.collection("users").document(uid)
                .set(officialUser)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {

                        // 🟢 التعديل الجوهري: نقل تعديل قاعدة البيانات إلى Background Thread
                        // لأن onComplete تعمل على Main Thread وتعديل Room هنا سيسبب مشاكل تسابق زمني
                        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {

                            if (db == null || !db.isOpen()) {
                                db = AppDatabase.getDatabase(getApplicationContext());
                            }

                            try {
                                // ب. استخدام Transaction لضمان استقرار الاتصال وتنفيذ كل الاستعلامات معاً
                                db.runInTransaction(() -> {
                                    // 1. حذف البيانات التي حذفها الضيف ولم ترفع للسيرفر
                                    db.accountDao().deleteGuestDeletedAccounts();
                                    db.accountTypeDao().deleteGuestDeletedAccountTypes();
                                    db.currencyDao().deleteGuestDeletedCurrencies();
                                    db.transactionDao().deleteGuestDeletedTransactions();
                                    db.budgetEntryDao().deleteGuestDeletedEntries();
                                    db.monthlyBudgetLimitDao().deleteGuestDeletedLimits();
                                    db.budgetCategoryDao().deleteGuestDeletedCategories();

                                    // 2. ترقية البيانات المتبقية للمستخدم الرسمي
                                    db.accountDao().upgradeToOfficialUser(uid, "NEW");
                                    db.accountTypeDao().upgradeToOfficialUser(uid, "NEW");
                                    db.currencyDao().upgradeToOfficialUser(uid, "NEW");
                                    db.transactionDao().upgradeToOfficialUser(uid, "NEW");
                                    db.budgetCategoryDao().upgradeToOfficialUser(uid, "NEW");
                                    db.monthlyBudgetLimitDao().upgradeToOfficialUser(uid, "NEW");
                                    db.budgetEntryDao().upgradeToOfficialUser(uid, "NEW");
                                });
                                Log.d("Migration", "Local database upgraded successfully for UID: " + uid);
                            } catch (IllegalStateException e) {
                                Log.e("Migration", "Database closed during migration", e);
                            } catch (Exception e) {
                                Log.e("Migration", "General SQL error", e);
                            }

                            if (!guestUID.isEmpty()) {
                                deleteGuestAccountsWithSameDevice();
                            }

                            // 🟢 التعديل الثاني: حفظ SharedPreferences فوراً وبشكل تزامني (commit) قبل المزامنة
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putString("userUID", uid);
                            licenceData.setUserUID(uid);
                            editor.putString("email",userEmail);
                            licenceData.setEmail(userEmail);
                            editor.putBoolean("isUserUIDAdded", true);
                            editor.apply(); // استخدام commit لضمان الحفظ قبل الانتقال للسطر التالي

                            // 🟢 التعديل الثالث: العودة إلى Main Thread لإطلاق المزامنة والانتقال للواجهة
                            // الآن نحن متأكدون 100% أن البيانات في Room تم ترقيتها وأن الـ SharedPreferences تم حفظها
                            runOnUiThread(() -> {
                                hideProgressDialog();
                                repository.setUserUID(uid);
                                repository.triggerSync(); // ستعمل الآن ببيانات نظيفة تحمل الـ UID الجديد
                                SecureLicenseManager.getInstance(LoginActivity.this).setGuest(false);
                                isGuest = false;

                                navigateToMainActivity(true);
                            });
                        });

                    } else {
                        runOnUiThread(() -> {
                            hideProgressDialog();
                            SnackbarHelper.showSnackbar(binding.getRoot(),
                                    getString(R.string.conversion_failed),
                                    SnackbarHelper.SnackbarType.ERROR);
                        });
                    }
                });
    }
    private void validateAndLoginUser() {
        String email = binding.emailEditText.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.setError(getString(R.string.error_invalid_email));
            return;
        } else {
            binding.emailLayout.setError(null);
        }
        if (TextUtils.isEmpty(password)) {
            binding.etPasswordlayout.setError(getString(R.string.error_password_required));
            return;
        } else {
            binding.etPasswordlayout.setError(null);
        }

        showProgressDialog(getString(R.string.check_regestiration_account));

        // 1. التحقق أولاً مما إذا كان الحساب محذوفاً
        checkIfUserDeleted(email, new UserCheckCallback() {
            @Override
            public void onCheckComplete(boolean isDeleted) {
                if (isDeleted) {
                    hideProgressDialog();
                    dialogeDelete();
                    return;
                }



                // 2. محاولة تسجيل الدخول عبر فايرباس
                firebaseAuth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener(LoginActivity.this, task -> {
                            hideProgressDialog(); // إخفاء الديالوج فوراً عند اكتمال المحاولة

                            if (task.isSuccessful()) {
                                FirebaseUser user = task.getResult().getUser();
                                if (user != null) {

                                    // التحقق من تفعيل البريد الإلكتروني برابط التحقق
                                    if (!user.isEmailVerified()) {
                                        user.sendEmailVerification();
                                        firebaseAuth.signOut();
                                        hideProgressDialog();
                                        DialogHelper.show(LoginActivity.this, getString(R.string.must_verified), DialogHelper.FeedbackType.ERROR, null);

//                                        SnackbarHelper.showSnackbar(binding.getRoot(),
//                                                getString(R.string.must_verified),
//                                                SnackbarHelper.SnackbarType.WARNING);
                                        return;
                                    }

                                    // إذا كان الحساب سليماً ومؤكداً، يستمر التدفق الطبيعي الآمن للمحفظة
                                    SharedPreferences.Editor editor = prefs.edit();
                                    editor.putString("userUID", user.getUid());
                                    licenceData.setUserUID(user.getUid());
                                    editor.putString("email", user.getEmail());
                                    licenceData.setEmail(user.getEmail());
                                    editor.putBoolean("isUserUIDAdded", true);
                                    editor.apply();

                                    isNewUser = task.getResult().getAdditionalUserInfo().isNewUser();
                                    if (isNewUser) {
                                        editor.putBoolean("isNewUser", true);
                                        editor.apply();
                                    }
                                    performDeviceLicenseCheck(user);
                                }
                            } else {
                                // 🛑 3. معالجة الأخطاء وتفصيل الأسباب بناءً على استجابة فايرباس
                                Exception exception = task.getException();

                                if (exception instanceof com.google.firebase.auth.FirebaseAuthInvalidUserException) {
                                    // البريد غير مسجل نهائياً في الـ Authentication الخاص بفايرباس
                                    showUnregisteredEmailDialog(email);

                                } else if (exception instanceof com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
                                    // الحساب موجود ولكن كلمة المرور التي أدخلها خاطئة
                                    SnackbarHelper.showSnackbar(binding.getRoot(),
                                            getString(R.string.lock_error_incorrect),
                                            SnackbarHelper.SnackbarType.ERROR);

                                } else {
                                    // خطأ عام آخر مثل انقطاع الإنترنت أو مشاكل السيرفر
                                    String errorMsg = exception != null ? exception.getMessage() : "";
                                    SnackbarHelper.showSnackbar(binding.getRoot(),
                                            getString(R.string.error_auth_failed) + ": " + errorMsg,
                                            SnackbarHelper.SnackbarType.ERROR);
                                }
                            }
                        });
            }
        });
    }
    private void showUnregisteredEmailDialog(String unregisteredEmail) {

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.register_title_1));
        builder.setMessage(getString(R.string.register_message));
        builder.setCancelable(false);

        builder.setPositiveButton(getString(R.string.register_title), (dialog, which) -> {
            dialog.dismiss();

            // إظهار واجهة التسجيل وإخفاء واجهة الدخول كما طلبت تماماً
            if (binding.registerForm != null && binding.emailEnterForm != null) {
                binding.registerForm.setVisibility(View.VISIBLE);
                binding.emailEnterForm.setVisibility(View.GONE);
                binding.emailRegisterEditText.setText(unregisteredEmail);
            }
        });

        builder.setNegativeButton(getString(R.string.cancel), (dialog, which) -> dialog.dismiss());
        builder.show();
    }
    public static String getCurrentLocalDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss a z", Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(new Date());
    }
    public void handleAfterLanguageSelection() {
        binding.getRoot().setBackgroundColor(Color.WHITE);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser != null && !isGuest) {
            // التحقق بشكل غير متزامن مما إذا كان الحساب محذوفًا
            checkIfUserDeleted(currentUser.getEmail(), new UserCheckCallback() {
                @Override
                public void onCheckComplete(boolean isDeleted) {
                    if (isDeleted) {
                        dialogeDelete();
                        toggleFormVisibility("loginOptions");
                        isDelete = true;
                        return;
                    }

                    // إذا لم يكن محذوفًا، تابع العملية الطبيعية
                    continueAfterLanguageSelection(currentUser);
                }
            });
        } else {

            if (isGuest) {
                guestUserData();
            } else {
                continueAfterLanguageSelection(null);
            }
        }
    }

    private void continueAfterLanguageSelection(FirebaseUser currentUser) {
        if (currentUser == null) {
            toggleFormVisibility("loginOptions");
            if (referrerUid != null) {
                referralManager.saveReferrerUid(referrerUid);
            }
            return;
        }

        if (isNewUser) {
            if (referrerUid != null) {
                referralManager.saveReferrerUid(referrerUid);
                referralManager.applyReferralRewardIfAvailable(currentUser.getUid());
                SharedPreferences prefs = getSharedPreferences("referral_prefs", MODE_PRIVATE);
                prefs.edit().remove("referrer_uid").apply();
            }
            checkLocationPermission();
            return;
        }

        String lockType = lockManager.getLockType();
        if (lockType != null && !lockType.isEmpty()) {
            isAppLocked = true;
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                startActivity(new Intent(this, LockScreenActivity.class));
                finish();
            }, 300);
        } else {
            Intent mainIntent = new Intent(this, MainActivity.class);
            if (referrerUid != null && referrerUid.isEmpty()) {
                mainIntent.putExtra("REFERRER_UID", referrerUid);
                mainIntent.putExtra("SHOW_ALREADY_REGISTERED", true);
            }
            startActivity(mainIntent);
            finish();
        }
    }
    private void startEmailVerificationMonitoring() {
        verificationCheckCount = 0;
        verificationHandler.postDelayed(verificationRunnable, VERIFICATION_CHECK_INTERVAL);
    }
    private void stopEmailVerificationMonitoring() {
        verificationHandler.removeCallbacks(verificationRunnable);
        verificationCheckCount = 0;
    }
    private void resendVerificationEmail() {
        if (pendingVerificationUser != null) {
            showProgressDialog("جاري إعادة إرسال رابط التحقق...");

            pendingVerificationUser.sendEmailVerification()
                    .addOnCompleteListener(task -> {
                        hideProgressDialog();

                        if (task.isSuccessful()) {
                            SnackbarHelper.showSnackbar(binding.getRoot(),
                                    "تم إعادة إرسال رابط التحقق بنجاح",
                                    SnackbarHelper.SnackbarType.SUCCESS);

                            // إعادة بدء المراقبة
                            isWaitingForEmailVerification = true;
                            verificationCheckCount = 0;
                            showEmailVerificationDialog(
                                    pendingVerificationUser.getDisplayName(),
                                    pendingVerificationUser.getEmail()
                            );
                            startEmailVerificationMonitoring();

                        } else {
                            SnackbarHelper.showSnackbar(binding.getRoot(),
                                    "فشل إعادة إرسال الرابط: " +
                                            (task.getException() != null ? task.getException().getMessage() : "خطأ غير معروف"),
                                    SnackbarHelper.SnackbarType.ERROR);
                        }
                    });
        }
    }
    private void cancelEmailVerification() {
        stopEmailVerificationMonitoring();
        isWaitingForEmailVerification = false;
        isRegistrationInProgress = false;
        pendingVerificationUser = null;

        // تسجيل الخروج من الحساب غير المفعل
        if (firebaseAuth.getCurrentUser() != null) {
            firebaseAuth.signOut();
        }

        // تنظيف الحقول
        binding.emailRegisterEditText.setText("");
        binding.passwordEditText.setText("");
        binding.passwordRenterEditText.setText("");

        SnackbarHelper.showSnackbar(binding.getRoot(),
                "تم إلغاء عملية التسجيل",
                SnackbarHelper.SnackbarType.INFO);
    }
    private void handleNewUserRegistration(FirebaseUser user) {
        // 1. تحديد نوع المزود (هل هو سوشيال ميديا أم بريد وكلمة مرور)
        boolean isSocialLogin = false;
        String currerntEmail = user.getEmail();
        if (user.getProviderData() != null) {
            for (com.google.firebase.auth.UserInfo userInfo : user.getProviderData()) {
                if (userInfo.getProviderId().equals("facebook.com") || userInfo.getProviderId().equals("google.com")) {
                    if (userInfo.getProviderId().equals("facebook.com")){
                        currerntEmail = userEmail;

                    }
                    isSocialLogin = true;
                    break;
                }
            }
        }

        // 2. تحقق إضافي من أن البريد مفعل (نطبقه فقط على حسابات البريد العادية، ونتجاوز السوشيال ميديا)
        if (!isSocialLogin && !user.isEmailVerified()) {
            Log.w(TAG, "تم استدعاء handleNewUserRegistration مع مستخدم غير مفعل!");
            firebaseAuth.signOut();
            hideProgressDialog();
            DialogHelper.show(LoginActivity.this, getString(R.string.must_verified), DialogHelper.FeedbackType.ERROR, null);

//            SnackbarHelper.showSnackbar(binding.getRoot(),
//                    getString(R.string.must_verified),
//                    SnackbarHelper.SnackbarType.WARNING);
            return;
        }

        Log.d(TAG, "بدء إعداد بيانات الحساب الجديد بعد التحقق");

        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("isNewUser", true);
        editor.apply();

        // تطبيق مكافآت الإحالة إذا كانت متاحة
        if (referrerUid != null && !referrerUid.isEmpty()) {
            referralManager.applyReferralRewardIfAvailable(user.getUid());
            SharedPreferences referralPrefs = getSharedPreferences("referral_prefs", MODE_PRIVATE);
            referralPrefs.edit().remove("referrer_uid").apply();

            SnackbarHelper.showSnackbar(binding.getRoot(),
                    getString(R.string.reward_message),
                    SnackbarHelper.SnackbarType.SUCCESS);
        }
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean isPendingReferral = prefs.getBoolean(AdsGoogleRewarder.KEY_REFERRAL_PENDING, false);
        if (isPendingReferral) {
            long expiryTime = prefs.getLong("premium_expiry", 0);

            applyReferralRewardToUser(expiryTime);
            SnackbarHelper.showSnackbar(binding.getRoot(),
                    getString(R.string.premium_ads_activated),
                    SnackbarHelper.SnackbarType.SUCCESS);
            // مسح حالة المعلق بعد النجاح
            prefs.edit().putBoolean(AdsGoogleRewarder.KEY_REFERRAL_PENDING, false).apply();
        }
        // إذا كان في وضع تحويل الضيف
        if (isRegisterGuestMode && isGuest) {
            convertGuestToOfficialUser(user, user.getDisplayName(), currerntEmail);
        } else {
            showProgressDialog(getString(R.string.preparing_account));
            checkLocationPermission();
        }
    }
    private void showEmailVerificationDialog(String name, String email) {
        runOnUiThread(() -> {
            // إخفاء أي dialogs موجودة مسبقاً
            if (verificationAlertDialog != null && verificationAlertDialog.isShowing()) {
                verificationAlertDialog.dismiss();
            }
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_email_verification, null);
            TextView tvEmail = dialogView.findViewById(R.id.tvEmail);
            TextView tvTimer = dialogView.findViewById(R.id.tvTimer);
            Button btnResend = dialogView.findViewById(R.id.btnResend);
            Button btnCancel = dialogView.findViewById(R.id.btnCancel);
            ProgressBar progressBar = dialogView.findViewById(R.id.progressBar);

            tvEmail.setText(email);

            verificationAlertDialog = new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.email_verification_title))
                    .setView(dialogView)
                    .setCancelable(false)
                    .create();

            // بدء العد التنازلي
            startVerificationCountdown(tvTimer, btnResend, 120); // 120 ثانية = دقيقتين

            btnResend.setOnClickListener(v -> {
                progressBar.setVisibility(View.VISIBLE);
                btnResend.setEnabled(false);
                resendVerificationEmail();
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnResend.setEnabled(true);
                }, 2000);
            });

            btnCancel.setOnClickListener(v -> {
                cancelEmailVerification();
                verificationAlertDialog.dismiss();
            });

            verificationAlertDialog.show();

            // بدء المراقبة الفورية
            startEmailVerificationMonitoring();
        });
    }
    private void startVerificationCountdown(TextView tvTimer, Button btnResend, int seconds) {
        new CountDownTimer(seconds * 1000, 1000) {
            public void onTick(long millisUntilFinished) {
                long secondsRemaining = millisUntilFinished / 1000;
                String timeText = String.format(Locale.getDefault(),
                        "سيتم إعادة الإرسال بعد: %d ثانية", secondsRemaining);
                tvTimer.setText(timeText);

                if (secondsRemaining <= 0) {
                    btnResend.setEnabled(true);
                    tvTimer.setText("يمكنك إعادة إرسال الرابط الآن");
                } else {
                    btnResend.setEnabled(false);
                }
            }

            public void onFinish() {
                btnResend.setEnabled(true);
                tvTimer.setText("يمكنك إعادة إرسال الرابط الآن");
            }
        }.start();
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
    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getApplication().getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
    }
    private void setupFacebookLogin() {
        binding.facebookSignInButton.setOnClickListener(v -> {
            if (!isNetworkAvailable()) {
                SnackbarHelper.showSnackbar(binding.getRoot(),
                        getString(R.string.no_internet), SnackbarHelper.SnackbarType.ERROR);
                return;
            }
            loginWithFacebook();
        });
    }

    private void loginWithFacebook() {
        if (isRegistrationInProgress) return;

        startLoginTimeout();
        showProgressDialog(getString(R.string.connecting_facebook));

        isRegistrationInProgress = true;
        // نمرر الكولباك الموحد الجديد
        googleAuthHelper.signInWithFacebook(this, mainAuthCallback);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d(TAG, "onActivityResult - requestCode: " + requestCode + ", resultCode: " + resultCode);

        // معالجة نتيجة جوجل
        if (requestCode == GoogleAuthHelper.getSignInRequestCode()) {
            Log.d(TAG, "معالجة نتيجة جوجل");
            googleAuthHelper.handleSignInResult(data);
        }

        // ✅ معالجة نتيجة الفيسبوك
        if (googleAuthHelper.onFacebookActivityResult(requestCode, resultCode, data)) {
            Log.d(TAG, "تمت معالجة نتيجة الفيسبوك");
        }
    }

    // ✅ تحسين دالة التحقق من الحساب المحذوف
    private void checkIfUserDeleted(String email, UserCheckCallback callback) {
        if (email == null) {
            Log.d(TAG, "البريد الإلكتروني فارغ - تخطي التحقق");
            callback.onCheckComplete(false);
            return;
        }

        Log.d(TAG, "التحقق من الحساب المحذوف: " + email);
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();

        firestore.collection("deletionLogs")
                .whereEqualTo("email", email.toLowerCase())
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot userSnapshot = task.getResult();
                        if (!userSnapshot.isEmpty()) {
                            Log.e(TAG, "تم العثور على الحساب المحذوف: " + email);
                            callback.onCheckComplete(true);
                        } else {
                            Log.d(TAG, "الحساب غير محذوف: " + email);
                            callback.onCheckComplete(false);
                        }
                    } else {
                        Log.e(TAG, "خطأ في التحقق من الحساب المحذوف: " + email);
                        // في حالة الخطأ، نفترض أن الحساب غير محذوف للمتابعة
                        callback.onCheckComplete(false);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "فشل التحقق من الحساب المحذوف: " + e.getMessage());
                    callback.onCheckComplete(false);
                });
    }

    /**
     * ✅ التحقق من حالة الحساب باستخدام Firebase Functions قبل التسجيل - الإصدار المصحح
     */

    /**
     * ✅ عرض ديالوج الحساب المحذوف - الإصدار المصحح
     */
    private void showAccountDeletedDialog(String message, String reason) {
        if (isFinishing() || isDestroyed()) {
            Log.d(TAG, "Activity is finishing, skipping account deleted dialog");
            return;
        }
        runOnUiThread(() -> {
            StringBuilder dialogMessage = new StringBuilder();

            if (message != null) {
                dialogMessage.append(message);
            } else {
                dialogMessage.append(getString(R.string.account_deleted_message));
            }

            if (reason != null && !reason.isEmpty() && !reason.equals(getString(R.string.no_deletion_reason))) {
                dialogMessage.append(getString(R.string.deletion_reason)).append(reason);
            }

            dialogMessage.append(getString(R.string.deletion_final_message));

            new MaterialAlertDialogBuilder(LoginActivity.this)
                    .setTitle(getString(R.string.account_deleted_title))
                    .setMessage(dialogMessage.toString())
                    .setPositiveButton(getString(R.string.ok), (dialog, which) -> {
                        // تنظيف الحقول
                        binding.emailEditText.setText("");
                        binding.etPassword.setText("");
                        binding.emailRegisterEditText.setText("");
                        binding.passwordEditText.setText("");
                        binding.passwordRenterEditText.setText("");
                    })
                    .setCancelable(false)
                    .setIcon(R.drawable.ic_warning)
                    .show();
        });
    }
    private void showAccountDeletedDialog() {
        runOnUiThread(() -> {
            // 1. التأكد من أن الشاشة لم يتم تدميرها ولم يتم البدء في إغلاقها
            if (isFinishing() || isDestroyed()) {
                return;
            }

            // 2. إغلاق أي Dialog سابق لتجنب التكرار
            if (accountDeletedDialog != null && accountDeletedDialog.isShowing()) {
                accountDeletedDialog.dismiss();
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(LoginActivity.this);
            builder.setTitle(getString(R.string.account_deleted_title));
            builder.setMessage(getString(R.string.account_deleted_message)+"\n"+getString(R.string.deletion_final_message));
            builder.setCancelable(false);
            builder.setPositiveButton(getString(R.string.ok), (dialog, which) -> {
                dialog.dismiss();
                // أي إجراءات أخرى بعد إغلاق الرسالة
                FirebaseAuth.getInstance().signOut();
//                googleLogoutForce();
                googleAuthHelper.signOut();
                finishAffinity();
            });

            accountDeletedDialog = builder.create();
            accountDeletedDialog.show();
        });
    }

    public void deleteGuestAccountsWithSameDevice() {
        String currentDeviceId = licenseManager.getDeviceId();

        if (TextUtils.isEmpty(currentDeviceId)) {
            return;
        }

        Log.d(TAG, "بدء حذف حسابات الضيف للجهاز: " + currentDeviceId);

        // البحث عن جميع حسابات الضيف التي تستخدم نفس deviceId
        firestore.collection("guests")
                .whereEqualTo("deviceId", currentDeviceId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<DocumentSnapshot> documents = task.getResult().getDocuments();
                        int totalCount = documents.size();

                        if (totalCount == 0) {
                            Log.d(TAG, "لا توجد حسابات ضيف للحذف");

                            return;
                        }

                        Log.d(TAG, "تم العثور على " + totalCount + " حساب ضيف للحذف");
                        deleteGuestDocuments(documents);
                    } else {
                        Log.e(TAG, "خطأ في البحث عن حسابات الضيف: " + task.getException());

                    }
                });
    }

    private void deleteGuestDocuments(List<DocumentSnapshot> documents) {
        WriteBatch batch = firestore.batch();

        for (DocumentSnapshot doc : documents) {
            batch.delete(doc.getReference());
        }

        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "تم حذف " + documents.size() + " حساب ضيف بنجاح");
                    SecureLicenseManager.getInstance(this).setGuestUID("");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "فشل في حذف حسابات الضيف: " + e.getMessage());
                });
    }

    // تحسين دالة validateAndRegisterUser
    private void validateAndRegisterUser() {
        if (isWaitingForEmailVerification || isEmailVerificationInProgress) {
            SnackbarHelper.showSnackbar(binding.getRoot(),
                    getString(R.string.registration_in_progress),
                    SnackbarHelper.SnackbarType.WARNING);
            return;
        }

        String name = binding.nameRegEditText.getText().toString().trim();
        String email = binding.emailRegisterEditText.getText().toString().trim();
        String password = binding.passwordEditText.getText().toString().trim();
        String password2 = binding.passwordRenterEditText.getText().toString().trim();

        // التحقق من المدخلات
        if (TextUtils.isEmpty(name)) {
            binding.nameRegLayout.setError(getString(R.string.error_name_required));
            return;
        } else {
            binding.nameRegLayout.setError(null);
        }

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailRegisterLayout.setError(getString(R.string.error_invalid_email));
            return;
        } else {
            binding.emailRegisterLayout.setError(null);
        }

        if (password.length() < 6) {
            binding.passwordLayout.setError(getString(R.string.error_password_short));
            return;
        } else {
            binding.passwordLayout.setError(null);
        }

        if (!password.equals(password2)) {
            binding.passwordLayout.setError(getString(R.string.error_password_mismatch));
            binding.passwordRenterLayout.setError(getString(R.string.error_password_mismatch));
            return;
        } else {
            binding.passwordLayout.setError(null);
            binding.passwordRenterLayout.setError(null);
        }

        isRegistrationInProgress = true;
        isFunctionCallFailed = false; // إعادة تعيين حالة الفشل

        showProgressDialog(getString(R.string.checking_account_status));

        // استخدام النسخة المحسنة من التحقق مع التعامل مع PERMISSION_DENIED
        checkUserDeletionStatusWithFallback(email, new UserCheckCallback() {
            @Override
            public void onCheckComplete(boolean isDeleted) {
                if (isDeleted) {
                    hideProgressDialog();
                    isRegistrationInProgress = false;
                    return;
                }

                // إذا لم يكن محذوفًا، تابع إنشاء الحساب
                showProgressDialog(getString(R.string.creating_system_account));
                createUserInAuthWithProtection(name, email, password);
            }
        });
    }
    private void checkUserDeletionStatusWithFallback(String email, UserCheckCallback callback) {
        if (email == null || email.isEmpty()) {
            callback.onCheckComplete(false);
            return;
        }

        Log.d(TAG, "التحقق من حالة الحساب مع Fallback: " + email);

        AtomicBoolean callbackCalled = new AtomicBoolean(false);

        // المحاولة الأولى: استخدام Firebase Functions
        attemptFunctionCall(email, new UserCheckCallback() {
            @Override
            public void onCheckComplete(boolean isDeleted) {
                if (!callbackCalled.getAndSet(true)) {
                    if (!isDeleted && isFunctionCallFailed) {
                        // إذا فشلت الدالة، استخدم Fallback مباشرة
                        Log.d(TAG, "استخدام Fallback بعد فشل استدعاء الدالة");
                        checkDeletedAccountsDirectFallback(email, callback);
                    } else {
                        callback.onCheckComplete(isDeleted);
                    }
                }
            }
        });
    }
    // محاولة استدعاء الدالة مع التعامل مع الأخطاء
    private void attemptFunctionCall(String email, UserCheckCallback callback) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("email", email.toLowerCase().trim());

            firebaseFunctions
                    .getHttpsCallable("checkUserDeletionStatus")
                    .call(data)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null) {
                            isFunctionCallFailed = false;
                            try {
                                HttpsCallableResult result = task.getResult();
                                Map<String, Object> resultData = (Map<String, Object>) result.getData();

                                Boolean success = (Boolean) resultData.get("success");
                                Boolean isDeleted = (Boolean) resultData.get("isDeleted");

                                if (success != null && success && isDeleted != null && isDeleted) {
                                    Log.e(TAG, "تم العثور على الحساب المحذوف عبر الدالة: " + email);
                                    String message = (String) resultData.get("message");
                                    String reason = (String) resultData.get("reason");
                                    runOnUiThread(() -> {
//                                        showAccountDeletedDialog(message, reason);
                                        showAccountDeletedDialog();
                                    });
                                    callback.onCheckComplete(true);
                                } else {
                                    Log.d(TAG, "الحساب غير محذوف عبر الدالة: " + email);
                                    callback.onCheckComplete(false);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "خطأ في معالجة نتيجة الدالة: " + e.getMessage());
                                isFunctionCallFailed = true;
                                callback.onCheckComplete(false);
                            }
                        } else {
                            // هنا يتم التعامل مع PERMISSION_DENIED وغيرها من الأخطاء
                            Exception exception = task.getException();
                            String errorMessage = exception != null ? exception.getMessage() : getString(R.string.unknown_error);
                            Log.e(TAG, "فشل استدعاء دالة التحقق: " + errorMessage);

                            isFunctionCallFailed = true;

                            // لا نعرض رسالة للمستخدم هنا، ننتقل مباشرة للـ Fallback
                            callback.onCheckComplete(false);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "فشل كامل في استدعاء الدالة: " + e.getMessage());
                        isFunctionCallFailed = true;
                        callback.onCheckComplete(false);
                    });

        } catch (Exception e) {
            Log.e(TAG, "خطأ في استدعاء الدالة: " + e.getMessage());
            isFunctionCallFailed = true;
            callback.onCheckComplete(false);
        }
    }
    private void checkDeletedAccountsDirectFallback(String email, UserCheckCallback callback) {
        Log.d(TAG, "التحقق المباشر من الحساب المحذوف في deletionLogs: " + email);

        firestore.collection("deletionLogs")
                .whereEqualTo("email", email.toLowerCase().trim())
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot querySnapshot = task.getResult();
                        if (querySnapshot != null && !querySnapshot.isEmpty()) {
                            Log.e(TAG, "تم العثور على الحساب المحذوف في deletionLogs: " + email);
                            // Show dialog only if activity is still alive
                            if (!isFinishing() && !isDestroyed()) {
                                isRegistrationInProgress = false;
                                // showAccountDeletedDialog(getString(R.string.account_deleted_message), getString(R.string.no_deletion_reason));
                                runOnUiThread(this::showAccountDeletedDialog);
                            }
                            callback.onCheckComplete(true);
                        } else {
                            Log.d(TAG, "الحساب غير محذوف في deletionLogs: " + email);
                            callback.onCheckComplete(false);
                        }
                    } else {
                        Log.e(TAG, "فشل التحقق المباشر من الحساب المحذوف: " +
                                (task.getException() != null ? task.getException().getMessage() : getString(R.string.auth_unknown_error)));
                        callback.onCheckComplete(false);
                    }
                });
    }
    private void createUserInAuthWithProtection(String name, String email, String password) {
        // فحص مزدوج لمنع التنفيذ المتكرر
        if (isEmailVerificationInProgress) {
            Log.w(TAG, "محاولة إنشاء حساب أثناء وجود عملية تحقق - تم الرفض");
            hideProgressDialog();
            isRegistrationInProgress = false;
            SnackbarHelper.showSnackbar(binding.getRoot(),
                    getString(R.string.registration_in_progress),
                    SnackbarHelper.SnackbarType.WARNING);
            return;
        }

        isEmailVerificationInProgress = true;
        updateProgressDialog(getString(R.string.creating_system_account));

        // تسجيل خروج أي مستخدم موجود مسبقاً لتجنب التكرار
        if (firebaseAuth.getCurrentUser() != null) {
            Log.d(TAG, "تسجيل خروج المستخدم الحالي قبل إنشاء حساب جديد");
            firebaseAuth.signOut();
        }

        // تأخير بسيط لضمان اكتمال تسجيل الخروج
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            firebaseAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
                            if (firebaseUser != null) {
                                Log.d(TAG, getString(R.string.user_created) + firebaseUser.getUid());
                                updateAuthProfileAndSendVerification(firebaseUser, name, email);
                            } else {
                                handleAuthError(getString(R.string.auth_user_data_error));
                            }
                        } else {
                            handleAuthError(task.getException() != null ?
                                    getAuthErrorMessage(task.getException()) : getString(R.string.auth_unknown_error));
                        }
                    });
        }, 1000);
    }

    // تحسين رسائل أخطاء المصادقة
    private String getAuthErrorMessage(Exception exception) {
        String errorMessage = exception.getMessage();
        if (errorMessage.contains("email already in use")) {
            return getString(R.string.email_already_used);
        } else if (errorMessage.contains("invalid email")) {
            return getString(R.string.invalid_email);
        } else if (errorMessage.contains("weak password")) {
            return getString(R.string.weak_password);
        } else {
            return getString(R.string.auth_unknown_error) + " " + errorMessage;
        }
    }

    // دالة موحدة لتحديث الملف الشخصي وإرسال التحقق
    private void updateAuthProfileAndSendVerification(FirebaseUser firebaseUser, String name, String email) {
        updateProgressDialog(getString(R.string.preparing_account));

        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build();

        firebaseUser.updateProfile(profileUpdates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        sendEmailVerificationWithRetry(firebaseUser, name, email);
                    } else {
                        handleAuthError(getString(R.string.auth_profile_error));
                    }
                });
    }

    // إرسال التحقق مع إمكانية إعادة المحاولة
    private void sendEmailVerificationWithRetry(FirebaseUser firebaseUser, String name, String email) {
        updateProgressDialog(getString(R.string.sending_verification_email));

        firebaseUser.sendEmailVerification()
                .addOnCompleteListener(verifyTask -> {
                    if (verifyTask.isSuccessful()) {
                        Log.d(TAG, "تم إرسال رابط التحقق بنجاح إلى: " + email);

                        // حفظ حالة المستخدم المنتظر التحقق
                        pendingVerificationUser = firebaseUser;
                        isWaitingForEmailVerification = true;

                        // إخفاء progress dialog العادي وإظهار dialog التحقق
                        hideProgressDialog();
                        showStableVerificationDialog(name, email);

                    } else {
                        handleAuthError(getString(R.string.auth_verification_error) +
                                (verifyTask.getException() != null ? verifyTask.getException().getMessage() : "خطأ غير معروف"));
                    }
                });
    }

    // ديالوج تحقق مستقر لا يختفي
    private void showStableVerificationDialog(String name, String email) {
        runOnUiThread(() -> {
            try {
                // إغلاق أي dialogs موجودة مسبقاً
                if (verificationAlertDialog != null && verificationAlertDialog.isShowing()) {
                    verificationAlertDialog.dismiss();
                }

                View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_email_verification, null);
                TextView tvEmail = dialogView.findViewById(R.id.tvEmail);
                TextView tvTimer = dialogView.findViewById(R.id.tvTimer);
                TextView tvNote = dialogView.findViewById(R.id.tvNote);
                Button btnResend = dialogView.findViewById(R.id.btnResend);
                Button btnCancel = dialogView.findViewById(R.id.btnCancel);
                ProgressBar progressBar = dialogView.findViewById(R.id.progressBar);

                tvEmail.setText(email);
                tvNote.setText(getString(R.string.verification_note));
                verificationAlertDialog = new MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.account_activation_required))
                        .setView(dialogView)
                        .setCancelable(false)
                        .create();

                // بدء العد التنازلي (120 ثانية = دقيقتين)
                startStableCountdown(tvTimer, btnResend, 120);

                btnResend.setOnClickListener(v -> {
                    progressBar.setVisibility(View.VISIBLE);
                    btnResend.setEnabled(false);
                    resendVerificationWithFeedback();
                    // إعادة تفعيل الزر بعد 5 ثوانٍ
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        progressBar.setVisibility(View.GONE);
                    }, 5000);
                });

                btnCancel.setOnClickListener(v -> {
                    safelyCancelVerification();
                });

                verificationAlertDialog.show();

                // بدء مراقبة حالة التحقق بعد تأخير بسيط
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    startRobustVerificationMonitoring();
                }, 3000); // تأخير 3 ثوانٍ لضمان استقرار النظام

            } catch (Exception e) {
                Log.e(TAG, "خطأ في عرض ديالوج التحقق: " + e.getMessage());
                handleAuthError(getString(R.string.error_dialoge));
            }
        });
    }

    // بدء مراقبة قوية للتحقق
    private void startRobustVerificationMonitoring() {
        verificationCheckCount = 0;
        if (verificationHandler == null) {
            verificationHandler = new Handler(Looper.getMainLooper());
        }

        // بدء المراقبة الفورية
        verificationHandler.post(verificationRunnable);
    }

    // Runnable محسن للمراقبة
    private Runnable verificationRunnable = new Runnable() {
        @Override
        public void run() {
            checkVerificationStatusRobust();
        }
    };

    // التحقق القوي من حالة البريد
    private void checkVerificationStatusRobust() {
        Log.d(TAG, "جاري التحقق من حالة البريد... المحاولة: " + (verificationCheckCount + 1));

        // الحصول على المستخدم الحالي مباشرة من FirebaseAuth
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "لا يوجد مستخدم حالي - إيقاف المراقبة");
            stopVerificationMonitoring();
            handleVerificationError(getString(R.string.verification_error_user_null));
            return;
        }

        // تحديث المرجع
        pendingVerificationUser = currentUser;

        // إعادة تحميل بيانات المستخدم
        currentUser.reload().addOnCompleteListener(reloadTask -> {
            if (reloadTask.isSuccessful()) {
                // الحصول على أحدث بيانات المستخدم
                FirebaseUser refreshedUser = firebaseAuth.getCurrentUser();
                if (refreshedUser == null) {
                    Log.e(TAG, "المستخدم أصبح null بعد إعادة التحميل");
                    handleVerificationError(getString(R.string.verification_error_user_null));
                    return;
                }

                if (refreshedUser.isEmailVerified()) {
                    Log.d(TAG, "تم التحقق من البريد بنجاح!");
                    handleSuccessfulVerification();
                } else {
                    verificationCheckCount++;
                    Log.d(TAG, "البريد لم يتم التحقق بعد. المحاولة: " + verificationCheckCount);

                    if (verificationCheckCount >= MAX_VERIFICATION_CHECKS) {
                        handleVerificationTimeout();
                    } else {
                        // تحديث واجهة المستخدم والاستمرار في المراقبة
                        updateVerificationProgress();
                        verificationHandler.postDelayed(verificationRunnable, VERIFICATION_CHECK_INTERVAL);
                    }
                }
            } else {
                Log.e(TAG, "فشل إعادة تحميل بيانات المستخدم: " + reloadTask.getException());
                // إعادة المحاولة بعد فترة
                verificationHandler.postDelayed(verificationRunnable, VERIFICATION_CHECK_INTERVAL);
            }
        });
    }

    // التعامل مع النجاح في التحقق
    private void handleSuccessfulVerification() {
        Log.d(TAG, "بدء معالجة التحقق الناجح");

        runOnUiThread(() -> {
            // إيقاف جميع العمليات أولاً
            stopVerificationMonitoring();

            // إغلاق ديالوج التحقق
            if (verificationAlertDialog != null && verificationAlertDialog.isShowing()) {
                verificationAlertDialog.dismiss();
                verificationAlertDialog = null;
            }

            // إيقاف العد التنازلي
            if (verificationCountDownTimer != null) {
                verificationCountDownTimer.cancel();
            }

            // التأكد النهائي من أن المستخدم مفعل
            FirebaseUser currentUser = firebaseAuth.getCurrentUser();
            if (currentUser == null || !currentUser.isEmailVerified()) {
                Log.e(TAG, getString(R.string.verification_failed_not_available));
                hideProgressDialog();
                DialogHelper.show(LoginActivity.this, getString(R.string.verification_failed), DialogHelper.FeedbackType.WARNING_2, null);

//                SnackbarHelper.showSnackbar(binding.getRoot(),
//                        getString(R.string.verification_failed),
//                        SnackbarHelper.SnackbarType.ERROR);
                return;
            }

            // تحديث جميع الحالات
            isRegistrationInProgress = false;
            isEmailVerificationInProgress = false;
            isWaitingForEmailVerification = false;

            // تحديث preferences
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("isNewUser", true);
            editor.apply();

            Log.d(TAG, "تم التحقق بنجاح، جاري متابعة التسجيل...");

            // متابعة عملية التسجيل
            proceedAfterSuccessfulVerification(currentUser);
        });
    }

    // المتابعة بعد التحقق الناجح
    private void proceedAfterSuccessfulVerification(FirebaseUser user) {
        // إظهار رسالة نجاح
        SnackbarHelper.showSnackbar(binding.getRoot(),
                getString(R.string.verification_success),
                SnackbarHelper.SnackbarType.SUCCESS);

        // تطبيق مكافآت الإحالة إذا كانت متاحة
        if (referrerUid != null && !referrerUid.isEmpty()) {
            referralManager.applyReferralRewardIfAvailable(user.getUid());
            SharedPreferences referralPrefs = getSharedPreferences("referral_prefs", MODE_PRIVATE);
            referralPrefs.edit().remove("referrer_uid").apply();
        }

        // إذا كان في وضع تحويل الضيف
        if (isRegisterGuestMode && isGuest) {
            userEmail =user.getEmail();
            convertGuestToOfficialUser(user, user.getDisplayName(), user.getEmail());
        } else {
            showProgressDialog(getString(R.string.preparing_account));
            checkLocationPermission();
        }

        pendingVerificationUser = null;
    }

    // إعادة الإرسال مع تغذية راجعة
    private void resendVerificationWithFeedback() {
        if (pendingVerificationUser != null) {
            pendingVerificationUser.sendEmailVerification()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            SnackbarHelper.showSnackbar(binding.getRoot(),
                                    getString(R.string.verification_resent_success),
                                    SnackbarHelper.SnackbarType.SUCCESS);
                        } else {
                            SnackbarHelper.showSnackbar(binding.getRoot(),
                                    getString(R.string.verification_resent_failed) +
                                            (task.getException() != null ? task.getException().getMessage() : "خطأ غير معروف"),
                                    SnackbarHelper.SnackbarType.ERROR);
                        }
                    });
        }
    }

    // إلغاء آمن للتحقق
    private void safelyCancelVerification() {
        stopVerificationMonitoring();
        isRegistrationInProgress = false;
        isEmailVerificationInProgress = false;
        isWaitingForEmailVerification = false;

        // تسجيل الخروج من الحساب غير المفعل
        if (firebaseAuth.getCurrentUser() != null) {
            firebaseAuth.signOut();
        }

        // إغلاق الديالوج
        if (verificationAlertDialog != null && verificationAlertDialog.isShowing()) {
            verificationAlertDialog.dismiss();
            verificationAlertDialog = null;
        }

        pendingVerificationUser = null;

        SnackbarHelper.showSnackbar(binding.getRoot(),
                getString(R.string.registration_cancelled),
                SnackbarHelper.SnackbarType.INFO);
    }

    // إيقاف المراقبة
    private void stopVerificationMonitoring() {
        if (verificationHandler != null) {
            verificationHandler.removeCallbacks(verificationRunnable);
        }
        verificationCheckCount = 0;
    }

    // العد التنازلي المستقر
    private void startStableCountdown(TextView tvTimer, Button btnResend, int seconds) {
        verificationCountDownTimer = new CountDownTimer(seconds * 1000, 1000) {
            public void onTick(long millisUntilFinished) {
                long secondsRemaining = millisUntilFinished / 1000;
                String timeText = String.format(Locale.getDefault(),
                        getString(R.string.resend_countdown), secondsRemaining);
                tvTimer.setText(timeText);

                btnResend.setEnabled(secondsRemaining <= 0);
            }

            public void onFinish() {
                btnResend.setEnabled(true);
                tvTimer.setText(getString(R.string.resend_verification));
            }
        }.start();
    }

    // تحديث تقدم المراقبة
    private void updateVerificationProgress() {
        runOnUiThread(() -> {
            if (verificationAlertDialog != null && verificationAlertDialog.isShowing()) {
                View dialogView = verificationAlertDialog.findViewById(R.id.tvTimer);
                if (dialogView instanceof TextView) {
                    TextView tvProgress = (TextView) dialogView;
                    String progressText = String.format(Locale.getDefault(),
                            getString(R.string.verification_progress), verificationCheckCount, MAX_VERIFICATION_CHECKS);
                    tvProgress.setText(progressText);
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // تنظيف شامل لجميع الموارد
        stopVerificationMonitoring();
        if (accountDeletedDialog != null && accountDeletedDialog.isShowing()) {
            accountDeletedDialog.dismiss();
        }
        if (verificationHandler != null) {
            verificationHandler.removeCallbacksAndMessages(null);
        }

        if (verificationAlertDialog != null && verificationAlertDialog.isShowing()) {
            verificationAlertDialog.dismiss();
            verificationAlertDialog = null;
        }

        if (verificationCountDownTimer != null) {
            verificationCountDownTimer.cancel();
        }
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        // إعادة تعيين جميع الحالات
        isRegistrationInProgress = false;
        isEmailVerificationInProgress = false;
        isWaitingForEmailVerification = false;
        pendingVerificationUser = null;
        isFunctionCallFailed = false;

        cancelLoginTimeout();
        hideProgressDialog();
    }

    // دوال مساعدة لمعالجة الأخطاء
    private void handleAuthError(String error) {
        runOnUiThread(() -> {
            isRegistrationInProgress = false;
            isEmailVerificationInProgress = false;
            hideProgressDialog();

            SnackbarHelper.showSnackbar(binding.getRoot(), error,
                    SnackbarHelper.SnackbarType.ERROR);

            // تنظيف أي بيانات متبقية
            if (firebaseAuth.getCurrentUser() != null) {
                firebaseAuth.signOut();
            }
        });
    }

    private void handleVerificationError(String error) {
        runOnUiThread(() -> {
            stopVerificationMonitoring();
            if (verificationAlertDialog != null && verificationAlertDialog.isShowing()) {
                verificationAlertDialog.dismiss();
            }

            SnackbarHelper.showSnackbar(binding.getRoot(),
                    getString(R.string.verification_error) + error,
                    SnackbarHelper.SnackbarType.ERROR);

            safelyCancelVerification();
        });
    }

    private void handleVerificationTimeout() {
        runOnUiThread(() -> {
            stopVerificationMonitoring();

            if (verificationAlertDialog != null && verificationAlertDialog.isShowing()) {
                verificationAlertDialog.dismiss();
            }

            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.verification_timeout_title))
                    .setMessage(getString(R.string.verification_timeout_message))
                    .setPositiveButton(getString(R.string.retry_verification), (dialog, which) -> {
                        if (pendingVerificationUser != null) {
                            resendVerificationWithFeedback();
                            showStableVerificationDialog(
                                    pendingVerificationUser.getDisplayName(),
                                    pendingVerificationUser.getEmail()
                            );
                        }
                    })
                    .setNegativeButton(getString(R.string.cancel_verification), (dialog, which) -> safelyCancelVerification())
                    .setCancelable(false)
                    .show();
        });
    }

    private void showReferralRewardDialog(long expiryTime, Runnable onContinueAction) {
        runOnUiThread(() -> {
            Dialog dialog = new Dialog(this);
            dialog.setContentView(R.layout.dialog_referral_reward);
            Objects.requireNonNull(dialog.getWindow()).setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            TextView  tvTitle = dialog.findViewById(R.id.tv_title);
            TextView tvExpiry = dialog.findViewById(R.id.tv_expiry);
            tvExpiry.setVisibility(View.VISIBLE);
            TextView tvMessage = dialog.findViewById(R.id.tv_message);
            MaterialButton btnContinue = dialog.findViewById(R.id.btn_register);
            MaterialButton btnCancel = dialog.findViewById(R.id.btn_continue_guest);
            btnCancel.setVisibility(View.GONE);
            btnContinue.setText(getString(R.string.continue_button));
            tvTitle.setText(getString(R.string.showCongratulationDialog_01));
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd", Locale.US);
            String dateStr = sdf.format(new Date(expiryTime));
            String message = getString(R.string.showCongratulationDialog_022);
            tvMessage.setText(message);
            tvExpiry.setText(getString(R.string.showCongratulationDialog_033) + " " + dateStr);


            // تعيين Listener للزر
            btnContinue.setOnClickListener(v -> {
                dialog.dismiss();
                if (onContinueAction != null) {
                    onContinueAction.run();
                }
            });

            // منع الإغلاق بالنقر خارج الديالوج
            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);

            dialog.show();
        });
    }

    private  void applyReferralRewardToUser(long expiryTime) {

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, 2);
        Date expiryDate = calendar.getTime();

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            Map<String, Object> subData = new HashMap<>();
            subData.put("is_premium", true);
            subData.put("plan_type", "monthly");
            subData.put("subscription_expiry", new Timestamp(expiryDate));
            subData.put("subscription_start", Timestamp.now());
            subData.put("lastModified",System.currentTimeMillis());
            subData.put("subscriptionStatus", "activated");
            subData.put("premium_activated_at", User.getCurrentLocalDateTime());
            subData.put("premium_activated_by", "Google Ads");
            licenceData.setPremiumActivatedBy("Google Ads");
            licenceData.setSubscriptionExpiry(expiryTime);
            licenceData.setPlanType("monthly");
            prefs.edit().putBoolean(AdsGoogleRewarder.KEY_REFERRAL_PENDING, false).apply();
            if (prefs != null) {
                prefs.edit().putBoolean(AdsGoogleRewarder.KEY_GIFT_RECEIVED, true).apply();
            }
            FirebaseFirestore.getInstance().collection("users").document(uid)
                    .set(subData, SetOptions.merge())
                    .addOnSuccessListener(aVoid -> Log.d("Firestore", "Subscription Updated"))
                    .addOnFailureListener(e -> Log.e("Firestore", "Error updating sub", e));
        }

        SecureLicenseManager manager = SecureLicenseManager.getInstance(this);
        manager.setPremium(true);
        manager.setPlanType("monthly");
        manager.setSubscriptionExpiry(expiryDate.getTime());

    }

//  private void registerByPhone(){
//
//      // إعداد CCP
//      binding.ccp.registerCarrierNumberEditText(binding.etPhoneNumber);
//      // هذا السطر يجعل أسماء الدول تظهر بلغة التطبيق (العربية إذا كان الهاتف عربي)
//      binding.ccp.changeDefaultLanguage(CountryCodePicker.Language.ARABIC);
//      // ملاحظة: مكتبة hbb20 تدعم changeDefaultLanguage لتعريب أسماء الدول
//
//      // زر إظهار لوحة الهاتف
//      binding.btnPhoneLogin.setOnClickListener(v -> {
//          binding.buttonsContainer.setVisibility(View.GONE);
//          binding.phoneAuthContainer.setVisibility(View.VISIBLE);
//      });
//
//      // زر الإلغاء
//      binding.btnCancelPhone.setOnClickListener(v -> {
//          binding.phoneAuthContainer.setVisibility(View.GONE);
//          binding.buttonsContainer.setVisibility(View.VISIBLE);
//      });
//
//      // زر المتابعة (بدء العملية)
//      binding.btnSubmitPhone.setOnClickListener(v -> handleActivationClick());
//  }
//    private void handleActivationClick() {
//
//        binding.btnActivate.setEnabled(false);
//        String name = binding.etFullName.getText().toString().trim();
//
//        if (TextUtils.isEmpty(name)) {
//            binding.tilFullName.setError(getString(R.string.error_name_required));
//
//            return;
//        }
//        if ( name.split("\\s+").length < 2) {
////            Toast.makeText(this, R.string.error_full_name_required, Toast.LENGTH_SHORT).show();
//            DialogHelper.show(this,getString(R.string.error_full_name_required), DialogHelper.FeedbackType.ERROR, null);
////            binding.btnActivate.setEnabled(true);
//            return ;
//        }
//        if (!binding.ccp.isValidFullNumber()) {
//            // Toast.makeText(this, getString(R.string.error_phone_invalid), Toast.LENGTH_LONG).show();
//            DialogHelper.show(this,getString(R.string.error_phone_invalid), DialogHelper.FeedbackType.ERROR, null);
//
//            binding.etPhoneNumber.requestFocus();
////            binding.btnActivate.setEnabled(true);
//            return;
//        }
//
//        // 1. الرقم الدولي الكامل (للتحقق من Firebase فقط)
//        String fullNumberE164 = binding.ccp.getFullNumberWithPlus();
//
//        // 2. الرقم الخام بدون مفتاح (للحفظ في Firestore)
//        String rawNumber = binding.etPhoneNumber.getText().toString().trim();
//
//        // 3. مفتاح الدولة (للحفظ في Firestore) - مثال: +967
//        String countryCode = binding.ccp.getSelectedCountryCodeWithPlus();
//        proceedToPhoneAuth(fullNumberE164, rawNumber, countryCode, name);
//        viewModel.startPhoneVerification(this, fullNumberE164, rawNumber, countryCode, name);
//    }
//
//    private void startPhoneLoginProcess() {
//        // أ. استخراج الاسم
//        inputFullName = etFullName.getText().toString().trim();
//
//        // ب. التحقق من صحة الرقم عبر CCP
//        if (!ccp.isValidFullNumber()) {
//            etPhoneNumber.setError(getString(R.string.invalid_phone_number));
//            return;
//        }
//
//        // ج. استخراج تفاصيل الرقم (حسب طلبك لجدول المستخدم)
//        // الرقم الكامل (للأوثنتيكيشن والبحث)
//        fullInternationalNumber = ccp.getFullNumberWithPlus();
//        // مفتاح الدولة فقط (مثل +967)
//        inputCountryCode = ccp.getSelectedCountryCodeWithPlus();
//        // الرقم بدون مفتاح الدولة (يجب إزالة المسافات إن وجدت)
//        inputRawPhoneNumber = etPhoneNumber.getText().toString().replaceAll("\\s+", "");
//
//        showLoading(true);
//
//        // د. فحص هل الرقم مسجل مسبقاً في Firestore؟
//        checkIfUserExistsInFirestore();
//    }
//
//    private void checkIfUserExistsInFirestore() {
//        // نبحث باستخدام الرقم الدولي الكامل لأنه المفتاح الفريد عادةً
//        db.collection("users")
//                .whereEqualTo("phoneNumber", inputRawPhoneNumber)
//                .whereEqualTo("countryCode", inputCountryCode)
//                .get()
//                .addOnSuccessListener(querySnapshot -> {
//                    showLoading(false);
//                    if (!querySnapshot.isEmpty()) {
//                        // المستخدم موجود -> تسجيل دخول مباشر
//                        isManualNewUserCheck = false;
//                        beginPhoneVerification();
//                    } else {
//                        // المستخدم غير موجود -> نظهر تحذير "حساب جديد"
//                        isManualNewUserCheck = true;
//
//                        // التأكد من إدخال الاسم للحساب الجديد
//                        if (TextUtils.isEmpty(inputFullName)) {
//                            etFullName.setError(getString(R.string.name_required));
//                            return;
//                        }
//
//                        showConfirmationDialog();
//                    }
//                })
//                .addOnFailureListener(e -> {
//                    showLoading(false);
//                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
//                });
//    }
//
//    private void showConfirmationDialog() {
//        new MaterialAlertDialogBuilder(this)
//                .setTitle(getString(R.string.new_account_title))
//                .setMessage(getString(R.string.confirm_create_account_msg, fullInternationalNumber))
//                .setPositiveButton(getString(R.string.yes), (dialog, which) -> beginPhoneVerification())
//                .setNegativeButton(getString(R.string.cancel), null)
//                .show();
//    }
//    private void beginPhoneVerification() {
//        showLoading(true);
//
//        // نستخدم الرقم الدولي الكامل للإرسال
//        googleAuthHelper.startPhoneVerification(this, fullInternationalNumber, new GoogleAuthHelper.PhoneAuthListener() {
//            @Override
//            public void onCodeSent(String verificationId) {
//                showLoading(false);
//                showOtpInputDialog(verificationId);
//            }
//
//            @Override
//            public void onVerificationFailed(String error) {
//                showLoading(false);
//                Toast.makeText(LoginActivity.this, error, Toast.LENGTH_LONG).show();
//            }
//        });
//    }
//    private void showOtpInputDialog(String verificationId) {
//        final EditText input = new EditText(this);
//        input.setHint("- - - - - -");
//        input.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
//        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
//
//        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
//                .setTitle(getString(R.string.verification_code))
//                .setMessage(getString(R.string.code_sent_to) + " " + fullInternationalNumber)
//                .setView(input)
//                .setPositiveButton(getString(R.string.verify), null)
//                .setCancelable(false)
//                .create();
//
//        dialog.setOnShowListener(d -> {
//            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
//                String code = input.getText().toString().trim();
//                if (code.length() == 6) {
//                    // *** هنا يتم الاستدعاء النهائي ***
//                    performFinalSignIn(verificationId, code);
//                    dialog.dismiss();
//                } else {
//                    input.setError(getString(R.string.invalid_code));
//                }
//            });
//        });
//        dialog.show();
//    }
//
//    private void performFinalSignIn(String verificationId, String code) {
//        showLoading(true);
//
//        // نستدعي الدالة المعدلة في GoogleAuthHelper ونمرر البيانات المفصلة
//        googleAuthHelper.signInWithPhone(
//                verificationId,
//                code,
//                inputFullName,       // الاسم الكامل
//                inputRawPhoneNumber, // الرقم بدون كود (للحفظ في phoneNumber)
//                inputCountryCode,    // كود الدولة (للحفظ في countryCode)
//                mainAuthCallback     // الكولباك الموحد
//        );
//    }
//    private final GoogleAuthHelper.AuthCallback mainAuthCallback = new GoogleAuthHelper.AuthCallback() {
//        @Override
//        public void onSignInSuccessNewUser(FirebaseUser user) {
//            showLoading(false);
//            // مستخدم جديد: تم حفظ بياناته (الاسم، الرقم المنفصل، الدولة) في الهيلبر
//            // الآن ننقل بيانات الضيف إليه
//            convertGuestToOfficialUser();
//        }
//
//        @Override
//        public void onSignInSuccessUpgrade(FirebaseUser user) {
//            showLoading(false);
//            // مستخدم قديم
//            if (isManualNewUserCheck) {
//                // حالة خاصة: غير موجود في Firestore لكن موجود في Auth
//                convertGuestToOfficialUser();
//            } else {
//                // موجود وقديم -> فحص التراخيص
//                performDeviceLicenseCheck();
//            }
//        }
//
//        @Override
//        public void onSignInFailure(String error) {
//            showLoading(false);
//            Toast.makeText(LoginActivity.this, error, Toast.LENGTH_LONG).show();
//        }
//
//        @Override
//        public void onSignOutSuccess() { }
//    };
}