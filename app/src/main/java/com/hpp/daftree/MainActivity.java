package com.hpp.daftree;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.ContactsContract;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;
import androidx.work.WorkManager;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryPurchasesParams;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.messaging.FirebaseMessaging;
import com.hpp.daftree.models.DeviceBanManager;
import com.hpp.daftree.notifications.NotificationService;
import com.hpp.daftree.notifications.NotificationHelper;
import com.hpp.daftree.adapters.AccountTypesPagerAdapter;
import com.hpp.daftree.adapters.AccountsAdapter;
import com.hpp.daftree.dailyreminder.DailyReminderManager;
import com.hpp.daftree.dailyreminder.DailyReminderScheduler;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.DeviceInfo;
import com.hpp.daftree.database.User;
import com.hpp.daftree.databinding.ActivityMainBinding;
import com.hpp.daftree.databinding.DialogWelcomeBackBinding;
import com.hpp.daftree.dialogs.AccountBottomSheet;
import com.hpp.daftree.dialogs.DeviceManagementDialog;
import com.hpp.daftree.dialogs.InvoiceDialog;
import com.hpp.daftree.dialogs.LanguageSelectionDialog;
import com.hpp.daftree.dialogs.LanguageViewModel;
import com.hpp.daftree.dialogs.PurchaseBottomSheet;
import com.hpp.daftree.dialogs.ReferralRewardDialog;
import com.hpp.daftree.dialogs.ReportsBottomSheet;
import com.hpp.daftree.dialogs.UpdateAppDialog;
import com.hpp.daftree.dialogs.UpdateDialog;
import com.hpp.daftree.helpers.AccountTypeIconHelper;
import com.hpp.daftree.helpers.AppFontHelper;
import com.hpp.daftree.helpers.LanguageManager;
import com.hpp.daftree.database.AccountWithBalance;
import com.hpp.daftree.models.AppAuth;
import com.hpp.daftree.models.AppLockManager;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.models.SnackbarHelper;
import com.hpp.daftree.models.UUIDGenerator;
import com.hpp.daftree.notifications.TransactionNotificationManager;
import com.hpp.daftree.syncmanagers.FirestoreSyncManager;
import com.hpp.daftree.syncmanagers.RestoreHelper;
import com.hpp.daftree.syncmanagers.SyncPreferences;
import com.hpp.daftree.syncmanagers.TransactionUploadController;
import com.hpp.daftree.ui.AccountDetailsActivity;
import com.hpp.daftree.ui.AccountListFragment;
import com.hpp.daftree.ui.AccountsTypeActivity;
import com.hpp.daftree.ui.AddTransactionActivity;
import com.hpp.daftree.ui.BaseActivity;
import com.hpp.daftree.ui.ContactActivity;
import com.hpp.daftree.ui.CurrenciesActivity;
import com.hpp.daftree.ui.LoginActivity;
import com.hpp.daftree.ui.ProfileActivity;
import com.hpp.daftree.ui.RecurringOperationsActivity;
import com.hpp.daftree.ui.SubscriptionActivity;
import com.hpp.daftree.ui.WebServerActivity;
import com.hpp.daftree.utils.AdsGoogleRewarder;
import com.hpp.daftree.utils.AdsManager;
import com.hpp.daftree.utils.DialogHelper;
import com.hpp.daftree.utils.TextViewUtils;
import com.hpp.daftree.viewmodels.MainViewModel;
import com.hpp.daftree.viewmodels.AccountTypeViewModel;
import com.hpp.daftree.viewmodels.CurrencyViewModel;
import com.hpp.daftree.utils.GoogleAuthHelper;
import com.hpp.daftree.utils.LicenseManager;
import com.hpp.daftree.utils.ReferralManager;
import com.hpp.daftree.utils.SecureLicenseManager;
import com.hpp.daftree.utils.TamperDetection;
import com.hpp.daftree.utils.VersionManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.hpp.daftree.debt.DebtActivationActivity;
import com.hpp.daftree.debt.DebtSummaryActivity;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;

import io.michaelrocks.libphonenumber.android.PhoneNumberUtil;
import io.michaelrocks.libphonenumber.android.Phonenumber;

import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;
import android.content.res.ColorStateList;
import android.util.TypedValue;

import com.hpp.daftree.budget.data.BudgetEntry;
import com.hpp.daftree.budget.data.BudgetEntryWithCategory;
import com.hpp.daftree.budget.ui.AddBudgetEntryBottomSheet;
import com.hpp.daftree.budget.ui.BudgetContainerFragment;
import com.hpp.daftree.budget.ui.EditBudgetLimitDialog;
import com.hpp.daftree.budget.ui.QuickAddBottomSheet;
import com.hpp.daftree.budget.util.BudgetUtils;
import com.hpp.daftree.budget.viewmodel.BudgetViewModel;
import com.hpp.daftree.viewmodels.ProfileViewModel;

import android.webkit.WebView;

import androidx.appcompat.widget.SwitchCompat;

public class MainActivity extends BaseActivity implements
        AccountsAdapter.OnAccountInteractionListener,
        NavigationView.OnNavigationItemSelectedListener, LicenseManager.AuthStateListener,
        ReferralRewardDialog.OnReferralActionListener,
        LanguageSelectionDialog.LanguageSelectionListener,
        BudgetContainerFragment.BudgetHostListener,
        QuickAddBottomSheet.QuickAddListener {

    private ActivityMainBinding binding;
    private boolean isBudgetTabActive = false;
    private BudgetContainerFragment budgetContainerFragment;
    private BudgetViewModel budgetViewModel;
    private TransactionUploadController transactionController;
    private boolean isSearchMode = false;
    private AlertDialog progressDialog;
    private AdsManager adsManager;
    private static final String TAG = "MainActivity";
    private ReviewManager reviewManager;
    private static final int STORAGE_PERMISSION_CODE = 101;
    private static final int LOGIN_REQUEST_CODE = 1001;
    private SyncPreferences sharedPrefsManager;
    private GoogleAuthHelper googleAuthHelper;
    private NavigationView navigationViewDrawer;
    private DaftreeRepository repository;
    private Handler mainHandler;
    private ActivityResultLauncher<String[]> openDocumentLauncher;
    private final boolean hasUsdTransactions = false;
    private final boolean hasSarTransactions = false;
    private ActivityResultLauncher<Intent> backupLauncher;
    private boolean isProgrammaticScroll = false; // لمنع الحلقات اللانهائية
    private AppLockManager lockManager;
    private ProfileViewModel profileViewModel;
    private MainViewModel mainViewModel;
    private CurrencyViewModel currencyViewModel;
    private AccountTypeViewModel accountTypeViewModel;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;
    private FirebaseFirestore firestore;
    private String currentUserEmail;

    private final List<Currency> availableCurrencies = new ArrayList<>(); // **قائمة ديناميكية**
    private int currentCurrencyIndex = 0;
    private final List<String> activeCurrencies = new ArrayList<>(); // **قائمة بالعملات النشطة فقط**
    private List<AccountType> accountTypesList = new ArrayList<>();
    private NavigationView navigationView;
    private DrawerLayout drawerLayout;
    private AccountTypesPagerAdapter pagerAdapter;
    // أضف هذا المتغير في الأعلى مع تعريف الـ Views
    private TextWatcher searchWatcher;
    // متغير لتتبع ما إذا كان السحب يحدث داخل RecyclerView
    private boolean isScrollingRecyclerView = false;

    private FusedLocationProviderClient fusedLocationClient;
    private User currentUserData;
    private LicenseManager licenseManager;
    private ReferralManager referralManager;
    private VersionManager versionManager;
    private LanguageViewModel languageViewModel;
    private boolean isApplyingLanguage = false;
    private SharedPreferences prefs;
    //    private RewardManager rewardManager;
    private final long RETRY_INTERVAL = 3 * 60 * 1000;
    private final Handler silentLoginHandler = new Handler(Looper.getMainLooper());
    private Runnable retryRunnable;
    private String localLanguage = "";
    private AppDatabase db;
    boolean isGuest = false;
    private String guestUID, fcmToken;
    private DeviceBanManager deviceBanManager;
    private boolean isSyncCompleted = false;
    private boolean isSyncStop = false;
    private boolean isDataLoaded = false;
    private String preAppliedLanguage = "";
    private DailyReminderManager dailyReminderManager;
    private BillingClient billingClient;
    private String referrerUid, intentString;
    private LanguageManager languageManager;
    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_LANGUAGE = "language";
    private final MutableLiveData<Boolean> syncCompletionLiveData = new MutableLiveData<>(false);
    private final Map<String, Integer> accountNameToIdMap = new HashMap<>();
    private SecureLicenseManager licenceData;
    private boolean isUserAdded = false;
    private String fullName, phoneNumber;
    private String currentUID;
    private final AtomicBoolean isCheckingSubscription = new AtomicBoolean(false);
    private OnBackPressedCallback backPressedCallback;
    private LinkedList<TapTarget> targetQueue = new LinkedList<>();


    // دالة مساعدة لفتح جهات الاتصال

    // دالة لمعالجة البيانات القادمة من جهات الاتصال

    private final ActivityResultLauncher<Intent> loginLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Log.e(TAG, "تم تسجيل الدخول بنجاح - إعادة تشغيل التطبيق من SplashActivity");
                    restartAppFromSplash();
                }
            }
    );
    private final ActivityResultLauncher<Intent> subscribeLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Log.e(TAG, "تم الاشتراك بنجاح ");
                    if (isFinishing() || isDestroyed()) return;
                    if (Objects.equals(SecureLicenseManager.getInstance(MainActivity.this).getPremiumActivatedBy(), "Google Play Store")) {
                        verifySubscriptionWithGoogle();
                    }
                }
            }
    );
    private List<Account> allAccounts = new ArrayList<>();
    private FirebaseAnalytics mFirebaseAnalytics;

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // تفعيل وضع Edge-to-Edge قبل استدعاء setContentView
        EdgeToEdge.enable(this);

        super.onCreate(savedInstanceState);
        if (!validateAppIntegrity()) {
            finish();
            return;
        }
//        setupSystemBars();
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupEdgeToEdgeInsets();
        setupFloatingToolbarActions();
        setupNavigationDrawer();

        MyApplication.applyGlobalTextWatcher(binding.getRoot());
//        rewardManager = new RewardManager();
        completelyReinitializeDataLayer();
        db = AppDatabase.getDatabase(getApplication());
        firestore = FirebaseFirestore.getInstance();
        mainViewModel = new ViewModelProvider(this).get(MainViewModel.class);
        lockManager = new AppLockManager(this);
        budgetViewModel = new ViewModelProvider(this).get(BudgetViewModel.class);
        Fragment existingBudgetFrag = getSupportFragmentManager().findFragmentByTag("BudgetContainerFragment");
        if (existingBudgetFrag instanceof BudgetContainerFragment) {
            budgetContainerFragment = (BudgetContainerFragment) existingBudgetFrag;
        }
        if (savedInstanceState != null && savedInstanceState.getBoolean("is_budget_tab_active", false)) {
            isBudgetTabActive = true;
        }
        profileViewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        repository = new DaftreeRepository(getApplication());
        googleAuthHelper = new GoogleAuthHelper(this, new LicenseManager(this), repository);
        licenseManager = new LicenseManager(this);
        licenseManager.setAuthStateListener(this);
        referralManager = new ReferralManager(this);
        versionManager = new VersionManager(this);
        dailyReminderManager = DailyReminderManager.getInstance(this);
        languageManager = LanguageManager.getInstance(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mainHandler = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        localLanguage = prefs.getString("language", "en");
        preAppliedLanguage = prefs.getString("language", "en");
        licenceData = SecureLicenseManager.getInstance(this);
        requestAllNotificationPermissions();
        adsManager = new AdsManager(this);
        currentUID = AppAuth.getUserID(this);
        deviceBanManager = new DeviceBanManager(this);
        licenceData.setStartApp(true);
        backPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isSearchMode) {
                    toggleSearchMode(false);
                } else if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START);
                } else if (isBudgetTabActive) {
                    switchMainTab(false);
                } else {
                    finish();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);
        isUserAdded = prefs.getBoolean("isUserUIDAdded", false);

        mainHandler.postDelayed(() -> {
            Intent intent = getIntent();
            if (intent != null) {
                referrerUid = intent.getStringExtra("REFERRER_UID");
                String debtGuest = intent.getStringExtra("DEBUT_GUEST");
                String debtUser = intent.getStringExtra("DEBUT_USER");
                String purchaseGuest = intent.getStringExtra("purchaseGuest");
                if (referrerUid != null && !referrerUid.isEmpty()) {
                    if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                        showReferralNotAvailableDialog();
                    } else {
                        ReferralRewardDialog dialog = new ReferralRewardDialog(this, referrerUid, this);
                        dialog.show();

                    }
                } else if ((debtGuest != null && debtGuest.equals("true")) ||
                        (purchaseGuest != null && purchaseGuest.equals("true"))) {

                    DialogHelper.show(this, getString(R.string.must_be_user), DialogHelper.FeedbackType.WARNING,
                            () -> {
                                FirestoreSyncManager.getInstance().stopListening();
                                Intent loginIntent = new Intent(MainActivity.this, LoginActivity.class);
                                loginIntent.putExtra("registerGuest", true);
                                loginLauncher.launch(loginIntent);
                                finish();
                            }, null);
                }
                // حالة 3: مستخدم مسجل يريد تفعيل الديون DEBET_USER
                else if (debtUser != null && debtUser.equals("true")) {
//                    boolean isDebtFeatureEnabled = SecureLicenseManager.getInstance(this).isDebtFeatureEnabled();

                    if (!licenceData.isLinkedAccount()) {
                        if (!isFinishing() && !isDestroyed()) {
                            DialogHelper.show(this, getString(R.string.must_register_debt), DialogHelper.FeedbackType.WARNING,
                                    () -> {
                                        FirestoreSyncManager.getInstance().stopListening();
                                        Intent debtIntent = new Intent(this, DebtActivationActivity.class);
                                        startActivity(debtIntent);
                                        finish();
                                    }, null);
                        }
                        // finish();
                    } else if (!licenceData.isDebtFeatureEnabled()) {
                        DialogHelper.show(this, getString(R.string.must_enabled_feature), DialogHelper.FeedbackType.WARNING,
                                () -> {
                                    FirestoreSyncManager.getInstance().stopListening();
                                    Intent debtIntent = new Intent(this, DebtActivationActivity.class);
                                    startActivity(debtIntent);
                                    finish();
                                }, null);
                    }
                }

            }
        }, 1000);

        guestUID = licenceData.guestUID();
        setupBillingClient();
        mainHandler.postDelayed(this::checkAndFetchAppConfig, 6000);

        // 1. التحقق من المستخدم وتسجيل الدخول
        isGuest = licenceData.isGuest();

        boolean isUserUIDAdded = prefs.getBoolean("isUserUIDAdded", false);
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            licenceData.setUserUID(FirebaseAuth.getInstance().getCurrentUser().getUid());
            prefs.edit().putString("userUID", FirebaseAuth.getInstance().getCurrentUser().getUid()).apply();
        }
        if (!isGuest && !isUserUIDAdded) {

//            String uuid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            String uuid = FirebaseAuth.getInstance().getCurrentUser() != null ?
                    FirebaseAuth.getInstance().getCurrentUser().getUid() : AppAuth.getUserID(this);

            if (uuid == null || uuid.isEmpty()) {
                return;
            }
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("userUID", FirebaseAuth.getInstance().getCurrentUser().getUid());
            editor.putBoolean("isUserUIDAdded", true);
            editor.apply();
            licenceData.setUserUID(FirebaseAuth.getInstance().getCurrentUser().getUid());
            currentUID = FirebaseAuth.getInstance().getCurrentUser().getUid();
            Log.d(TAG, "تم اضافة رقم المعرف محليا)");
            Bundle transactionParams = new Bundle();
            transactionParams.putString("owerUID", currentUID);
            transactionParams.putString("saveLocalOwerUID", "تم تخزين المعرف محليا");
            mFirebaseAnalytics.logEvent("log_saveLocalOwerUID", transactionParams);

        }
        if (isGuest) {
            guestUID = licenceData.guestUID();
            Log.d(TAG, "isGuest: " + isGuest + "\n" +
                    "guestUID: " + guestUID);
        } else {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            String savedUID = prefs.getString("userUID", ""); // الـ UID الذي قمت بحفظه سابقاً

            if (user != null) {
                // المستخدم مسجل والدخول سليم
                //  startSyncProcess(user.getUid());
                // licenceData.setEmail(user.getEmail());
                DaftreeRepository repo = new DaftreeRepository(getApplication());
                repo.setUserUID(AppAuth.getUserID(this));
                FirestoreSyncManager.getInstance().startListening(repo, this, repo::triggerSync);
            } else if (isUserAdded && !savedUID.isEmpty()) {
                // حالة "اليمن": المستخدم مسجل سابقاً لكنه الآن غير قادر على الوصول لـ FirebaseAuth
                // نسمح له بالدخول باستخدام الـ UID المخزن محلياً
                // startSyncProcess(savedUID);
                DaftreeRepository repo = new DaftreeRepository(getApplication());
                repo.setUserUID(AppAuth.getUserID(this));
                FirestoreSyncManager.getInstance().startListening(repo, this, repo::triggerSync);
                // نحاول في الخلفية استعادة الجلسة الرسمية
                attemptSilentLogin();
            } else {
                Intent loginIntent = new Intent(MainActivity.this, LoginActivity.class);
                loginIntent.putExtra("registerGuest", true);
                loginLauncher.launch(loginIntent);
                finish();
                return;
            }


            if (currentUID != null && !currentUID.isEmpty()) {
                prefs.edit().putString("uid", currentUID).apply();
            }
            syncUserPhoneWithAuth();
            normalUser();
        }

        String userUID, userType;
        if (isGuest) {
            userUID = licenceData.guestUID();
            userType = "guests";
        } else {
            userUID = currentUID;
            userType = "users";
        }
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            fcmToken = token;
            if (userUID != null && !userUID.trim().isEmpty()) {
                firestore.collection(userType).document(userUID).update("fcmToken", token)
                        .addOnSuccessListener(aVoid -> Log.d(TAG, "تم تحديث fcmToken بنجاح: " + token))
                        .addOnFailureListener(e -> Log.e(TAG, "فشل تحديث fcmToken في قاعدة البيانات", e));
            } else {
                // إذا كان فارغاً، نتجاهل التحديث بدلاً من أن ينهار التطبيق
                Log.e(TAG, "تم تخطي تحديث Firestore لأن userUID فارغ أو null");
            }
        });
        subscribeToSmartLanguageTopic();

        if (licenceData.isUserAdmin()) {
            FirebaseMessaging.getInstance().subscribeToTopic("admins");
        } else {
            // إذا تغيرت رتبته أو كان مستخدماً عادياً، نلغي اشتراكه من موضوع المدراء
            FirebaseMessaging.getInstance().unsubscribeFromTopic("admins");
        }
        FirebaseMessaging.getInstance().subscribeToTopic("all_users")
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d("FCM", "تم الاشتراك في قناة الإشعارات العامة بنجاح");
                    }
                });

        if (getIntent().getExtras() != null) {
            String type = getIntent().getExtras().getString("type");
            if ("transaction_alert".equals(type)) {
                getIntent().removeExtra("type");
                Intent intent;
                if (licenceData.isLinkedAccount()) {
                    intent = new Intent(this, DebtSummaryActivity.class);
                } else {
                    intent = new Intent(this, DebtActivationActivity.class);
                }
                startActivity(intent);
            } else if ("purchase".equals(type)) {
                getIntent().removeExtra("type");
                Intent subscribeIntent = new Intent(MainActivity.this, SubscriptionActivity.class);
                subscribeIntent.putExtra("purchase", true);
                subscribeLauncher.launch(subscribeIntent);
            } else if (type != null && !type.isEmpty()) {
                NotificationHelper.handleNotificationIntent(this, getIntent());
            }
        }
        sharedPrefsManager = new SyncPreferences(this);
        currencyViewModel = new ViewModelProvider(this).get(CurrencyViewModel.class);
        accountTypeViewModel = new ViewModelProvider(this).get(AccountTypeViewModel.class); // <-- تهيئة
        profileViewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        // ✅ هذا هو المراقب الذي سيستجيب للتحديثات اللحظية
        profileViewModel.getUserProfile().observe(this, user -> {
            if (user != null) {
                Log.d("MainActivity", "تم تحديث بيانات المستخدم المحلية، جاري تحديث الواجهة...");
                this.currentUserData = user;
                updateNavigationMenuItems(); // تحديث القائمة بالبيانات الجديدة
            }
        });
        checkLocalCurrency();
        setAdBannner();

        languageViewModel = new ViewModelProvider(this).get(LanguageViewModel.class);


        setupToolbarAndDrawer();
        setupViewPager();
        setupEventListeners();
        handleIntentExtras();
        mainViewModel.getAllAccounts().observe(this, accounts -> {
            if (accounts != null) {
                this.allAccounts = accounts;
                updateAccountMaps(accounts);
                Log.d(TAG, "Account list updated. Total accounts: " + allAccounts.size());
            }
        });
        isGuest = licenceData.isGuest();
        // ✅ بدء نظام التحقق من المزامنة (يجب أن يكون قبل setupObservers)

// ✅ إعداد المراقبين بعد نظام المزامنة
        setupObservers();
        setupCurrencyObserver(); // **مراقب جديد للعملات**
        referringNotification();
        transactionController = new TransactionUploadController(this);

        // 🔥 **التحقق من وجود ديالوج معلق عند فتح التطبيق**
        mainHandler.postDelayed(this::checkPendingTransactionsDialog, 2000);
//        .
//        PeriodicWorkRequest nightlyCheck =
//                new PeriodicWorkRequest.Builder(PendingTxCheckWorker.class, 1, TimeUnit.DAYS)
//                        .setInitialDelay(getDelayUntilMidnight(), TimeUnit.MILLISECONDS)
//                        .build();
//        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
//                "PendingTxCheck", ExistingPeriodicWorkPolicy.UPDATE, nightlyCheck);

        checkForUpdatesOrHelp();
        mainHandler.postDelayed(() -> {
            checkForAppUpdate(false);
        }, 10000);
        //setupLaunchers();
        setupOrientationAwareLayout();
        setupLanguageObserver();
//        initializeServicesSafely();
        checkPendingReferral();
// قراءة الترتيب المحفوظ وتمريره للـ ViewModel مباشرة عند تشغيل التطبيق
//        SharedPreferences sharedPreferences = getSharedPreferences("DaftreePrefs", MODE_PRIVATE);
        int savedSortOrder = prefs.getInt("KEY_SORT_ORDER", 0);
        mainViewModel.setSortOrder(savedSortOrder);

// ربط الزر btnSort بالحدث المستمع
        if (binding.btnSort != null) {
            binding.btnSort.setOnClickListener(this::showSortPopupMenu);
        }
        // تغيير العملة عند النقر على بطاقة الإجماليات السفلية
        binding.bottomSummaryCard.setOnClickListener(v -> cycleCurrency());
        if (isGuest) {

            mainHandler.postDelayed(this::updateGuestToFirestore, 6000);

            return;
        }
        mainHandler.postDelayed(() -> {
            if (licenceData.isPremium()) {
                if (Objects.equals(licenceData.getPremiumActivatedBy(), "Google Play Store")) {
                    verifySubscriptionWithGoogle();
                }
            }
        }, 3000);
    }
    private void showSortPopupMenu(View anchor) {
        // إنشاء PopupMenu مرتبط بالزر ليظهر أسفله مباشرة ويدعم الـ RTL و LTR تلقائياً
        PopupMenu popup = new PopupMenu(this, anchor);

        // إضافة العناصر برمجياً لدعم اللغات بشكل ديناميكي كامل من ملف strings.xml
        MenuItem titleItem = popup.getMenu().add(Menu.NONE, -1, 0, getString(R.string.sort_by));
        titleItem.setEnabled(false); // جعل العنوان غير قابل للنقر

        popup.getMenu().add(Menu.NONE, 0, 1, getString(R.string.sort_oldest));
        popup.getMenu().add(Menu.NONE, 1, 2, getString(R.string.sort_newest));
        popup.getMenu().add(Menu.NONE, 2, 3, getString(R.string.sort_name_az));
        popup.getMenu().add(Menu.NONE, 3, 4, getString(R.string.sort_name_za));

        popup.getMenu().add(Menu.NONE, 4, 5, getString(R.string.sort_latest_transactions));

        // تحديد واختيار العنصر المفعل حالياً ووضع علامة صح بجانبه
        int currentOrder = prefs.getInt("KEY_SORT_ORDER", 4);
        MenuItem currentItem = popup.getMenu().findItem(currentOrder);
        if (currentItem != null) {
            currentItem.setCheckable(true);
            currentItem.setChecked(true);
        }

        // معالجة حدث الضغط والاختيار من القائمة
        popup.setOnMenuItemClickListener(item -> {
            int selectedOrder = item.getItemId();
            if (selectedOrder == -1) return false;

            // حفظ الخيار الجديد في الـ SharedPreferences
            prefs.edit().putInt("KEY_SORT_ORDER", selectedOrder).apply();

            // تحديث الـ ViewModel لفرز البيانات فوراً في الواجهة
            mainViewModel.setSortOrder(selectedOrder);
            return true;
        });

        popup.show();
    }

    void toggleSearchMode(boolean enable) {
        isSearchMode = enable;
        if (enable) {
            binding.tvScreenTitle.setVisibility(View.GONE);
            binding.iconsContainer.setVisibility(View.GONE);
            binding.etSearch.setVisibility(View.VISIBLE);
            binding.btnCloseSearch.setVisibility(View.VISIBLE);
            binding.etSearch.requestFocus();

            // إظهار الكيبورد
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT);

            // 1. تهيئة الـ TextWatcher مرة واحدة فقط
            if (searchWatcher == null) {
                searchWatcher = new TextWatcher() {
                    private Handler handler = new Handler(Looper.getMainLooper());
                    private Runnable runnable;
                    private final long DELAY = 300; // تأخير 300 مللي ثانية

                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (runnable != null) {
                            handler.removeCallbacks(runnable);
                        }

                        final String query = s.toString().trim();

                        // إذا كان النص فارغاً، قم بإعادة تعيين التصفية مباشرة
                        if (query.isEmpty()) {
                            mainViewModel.setSearchQuery("");
                            return;
                        }

                        runnable = () -> {
                            mainViewModel.setSearchQuery(query);
                            if (mainViewModel != null) {
                                mainViewModel.refreshData();
                            }
                        };
                        handler.postDelayed(runnable, DELAY);
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                    }
                };
            }

            // 2. إضافة المستمع بشكل صحيح
            binding.etSearch.removeTextChangedListener(searchWatcher); // إزالة أي نسخ قديمة احتياطاً
            binding.etSearch.addTextChangedListener(searchWatcher);

        } else {
            // العودة للوضع الطبيعي

            // 3. الخطوة الأهم: إزالة المستمع أولاً قبل مسح النص
            if (searchWatcher != null) {
                binding.etSearch.removeTextChangedListener(searchWatcher);
            }

            // الآن يمكن مسح النص بأمان دون التسبب في Crash
            binding.etSearch.setText("");

            binding.etSearch.setVisibility(View.GONE);
            binding.btnCloseSearch.setVisibility(View.GONE);
            binding.tvScreenTitle.setVisibility(View.VISIBLE);
            binding.iconsContainer.setVisibility(View.VISIBLE);

            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(binding.etSearch.getWindowToken(), 0);

            if (mainViewModel != null) {
                // التعديل الهام هنا: تصفير نص البحث لكي تظهر جميع الحسابات مجدداً!
                mainViewModel.setSearchQuery("");
                mainViewModel.refreshData();
            }
        }
    }

    private void setAdBannner() {
        // افترضنا وجود متغيرات الحالة: isPremium و isGuest
        if (!licenceData.isPremium()) {
            // إعداد حاوية الإعلان (يجب أن تكون FrameLayout في XML في نفس موقع warning_text)
            adsManager.loadBannerAd(binding.adContainer, new AdsManager.AdListener() {
                @Override
                public void onAdLoaded() {
                    binding.adContainer.setVisibility(View.VISIBLE);
                    binding.warningText.setVisibility(View.GONE);
                }

                @Override
                public void onAdFailedToLoad(String error) {
                    binding.adContainer.setVisibility(View.GONE);
                    // إذا فشل الإعلان وكان المستخدم ضيف، نظهر نص التحذير
                    if (isGuest) {
                        binding.warningText.setVisibility(View.VISIBLE);
                    }
                }

                @Override
                public void onPointsEarned(int amount) {
                }

                @Override
                public void onAdClosed() {
                }
            });
        } else {
            // إذا كان premium نخفي كلاهما
            binding.adContainer.setVisibility(View.GONE);
            binding.warningText.setVisibility(View.GONE);
        }
    }

    // في MainActivity.java
    private void startSyncProcess(String uid) {
        if (uid == null || uid.isEmpty()) return;
        // 1. تحديث الكائن المحلي بالـ UID
        licenceData.setUserUID(uid);

        // 2. تفعيل المستمع (Listener) لـ Firestore
        // ميزة SnapshotListener أنه سيعطيك البيانات من الكاش فوراً (في أجزاء من الثانية)
        // ثم سيحاول الاتصال بالسيرفر في الخلفية بصمت.
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .addSnapshotListener((documentSnapshot, e) -> {
                    if (e != null) {
                        // في حالة الحجب في اليمن، سيصل الكود إلى هنا غالباً
                        // لن نظهر خطأ للمستخدم، سنتركه يعمل على البيانات المخزنة محلياً
                        Log.w(TAG, "Firestore listen failed or blocked (Yemen Logic)", e);
                        return;
                    }

                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        // البيانات وصلت (سواء من الكاش أو السيرفر)
                        boolean isFromCache = documentSnapshot.getMetadata().hasPendingWrites()
                                || documentSnapshot.getMetadata().isFromCache();

                        Log.d(TAG, "Data source: " + (isFromCache ? "Local Cache" : "Server"));

                        // هنا نحدث بيانات المستخدم في واجهة التطبيق
//                    updateUIWithUserData(documentSnapshot);
                        updateAllNavigationSwitches();
                        updateNavigationMenuItems();
                    }
                });
    }

    private void subscribeToSmartLanguageTopic() {
        SharedPreferences langPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String rawLang = langPrefs.getString(KEY_LANGUAGE, java.util.Locale.getDefault().getLanguage());
        subscribeToSmartLanguageTopic(rawLang);
    }

    private void subscribeToSmartLanguageTopic(String rawLangCode) {
        String lang = (rawLangCode != null && !rawLangCode.trim().isEmpty())
                ? rawLangCode.trim().toLowerCase(java.util.Locale.US)
                : java.util.Locale.getDefault().getLanguage().toLowerCase(java.util.Locale.US);

        if (lang.contains("_")) lang = lang.split("_")[0];
        if (lang.contains("-")) lang = lang.split("-")[0];

        // توحيد رموز اللغات الخاصة في أندرويد مع رموز قنوات الإشعارات الـ 29
        if ("in".equals(lang)) lang = "id";
        else if ("he".equals(lang)) lang = "iw";
        else if ("nb".equals(lang) || "nn".equals(lang)) lang = "no";

        String[] supportedLanguages = {
                "ar", "en", "cs", "da", "de", "es", "fi", "fr", "hi", "hu",
                "id", "it", "iw", "ja", "ko", "nl", "no", "pl", "pt", "ro",
                "ru", "sk", "sv", "th", "tr", "uk", "vi", "zh"
        };

        boolean isSupported = false;
        for (String supported : supportedLanguages) {
            if (supported.equals(lang)) {
                isSupported = true;
                break;
            }
        }

        final String newTopic = isSupported ? ("global_" + lang) : "global_en";
        SharedPreferences langPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String previousTopic = langPrefs.getString("last_fcm_lang_topic", "");

        if (!previousTopic.isEmpty() && !previousTopic.equals(newTopic)) {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(previousTopic);
        }

        FirebaseMessaging.getInstance().subscribeToTopic(newTopic)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        langPrefs.edit().putString("last_fcm_lang_topic", newTopic).apply();
                        Log.d("SmartNotifications", "Subscribed to smart topic: " + newTopic);
                    } else {
                        Log.e("SmartNotifications", "Subscription failed", task.getException());
                    }
                });
    }

    /**
     * إعادة تعيين التوجيه (لأغراض الاختبار)
     */
    private void resetTour() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        prefs.edit().putBoolean("first_time_tour", true).apply();
    }

    public void showAppTour() {
        // 1. التحقق: هل هذه أول مرة يفتح فيها المستخدم التطبيق؟
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean first_time_tour = prefs.getBoolean("first_time_tour", true);

        if (!first_time_tour) {
            return; // إذا لم تكن المرة الأولى، اخرج من الدالة
        }

        // 2. إعداد تسلسل العرض التوضيحي
        new TapTargetSequence(this)
                .targets(
                        // الخطوة 1: زر إضافة حساب
//                        createTarget(findViewById(R.id.nav_item_add_account),
//                                getString(R.string.tut_1_title),
//                                getString(R.string.tut_1_desc)),

                        // الخطوة 2: الزر العائم لإضافة عملية (Add Transaction)
                        createTarget(findViewById(R.id.addTransactionButton),
                                getString(R.string.tut_2_title),
                                getString(R.string.start_title))
                                .tintTarget(false) // لأن الزر ملون أصلاً، لا نريد تلوينه

//                        // الخطوة 3: الحسابات المرتبطة
//                        createTarget(findViewById(R.id.navItemLinkedAccounts),
//                                getString(R.string.tut_3_title),
//                                getString(R.string.tut_3_desc)),
//
//                        // الخطوة 4: نص العملة
//                        createTarget(findViewById(R.id.currencyTextView),
//                                getString(R.string.tut_4_title),
//                                getString(R.string.tut_4_desc)),
//
//                        // الخطوة 5: زر الكمبيوتر
//                        createTarget(findViewById(R.id.btnComputer),
//                                getString(R.string.tut_5_title),
//                                getString(R.string.tut_5_desc))
                )
                .listener(new TapTargetSequence.Listener() {
                    @Override
                    public void onSequenceFinish() {
                        // عند انتهاء الشرح، نحفظ أنه تم العرض لعدم إظهاره مرة أخرى ونفتح قائمة الإدخالات الموحدة
                        prefs.edit().putBoolean("first_time_tour", false).apply();
                        showQuickAddBottomSheet();
                    }

                    @Override
                    public void onSequenceStep(TapTarget lastTarget, boolean targetClicked) {
                        // كود اختياري يتم تنفيذه بعد كل خطوة
                    }

                    @Override
                    public void onSequenceCanceled(TapTarget lastTarget) {
                        // في حال ضغط المستخدم خارج الدائرة للإلغاء (اختياري: يمكن حفظ الحالة أيضاً)
                        prefs.edit().putBoolean("first_time_tour", false).apply();
                        addFirstTransaction();
                    }
                })
                .start();
    }

    void addFirstTransaction() {
        boolean firstRun = prefs.getBoolean("firstRun", true);
        mainHandler.postDelayed(() -> {
            DialogWelcomeBackBinding cardBinding = binding.cardFirstExpense;
            if (firstRun) {
                cardBinding.getRoot().setVisibility(View.VISIBLE);
                Animation anim = AnimationUtils.loadAnimation(this, R.anim.card_enter);
                cardBinding.getRoot().startAnimation(anim);
            } else {
                cardBinding.getRoot().setVisibility(View.GONE);
            }

            cardBinding.btnAddFirstExpense.setOnClickListener(v -> {
                if (!db.isOpen()) {
                    db = AppDatabase.getDatabase(MainActivity.this);
                }
                // ✅ استخدام العملة المحلية المخزنة كبديل آمن
                String currencyName = MyApplication.defaultCurrencyName;
                if (TextUtils.isEmpty(currencyName) && !availableCurrencies.isEmpty()) {
                    currencyName = availableCurrencies.get(0).name;
                }
                if (TextUtils.isEmpty(currencyName)) {
                    addDefaultCurrencyAndProceed();
                    cardBinding.getRoot().setVisibility(View.GONE);
                    return;
                }
                Intent intent = new Intent(MainActivity.this, AddTransactionActivity.class);
                intent.putExtra("CURRENCY", currencyName);
                startActivity(intent);
                prefs.edit().putBoolean("firstRun", false).apply();
                cardBinding.getRoot().setVisibility(View.GONE);
            });

            cardBinding.btnCloseCard.setOnClickListener(v -> {
                prefs.edit().putBoolean("firstRun", false).apply();
                cardBinding.getRoot().setVisibility(View.GONE);
            });
        }, 2000);
    }

    private void checkPendingReferral() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean hasPendingReferral = prefs.getBoolean(AdsGoogleRewarder.KEY_REFERRAL_PENDING, false);

        if (hasPendingReferral) {

            checkPendingReferralReward();
        } else {
            showAppTour();
        }
    }

    private void checkPendingReferralReward() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        long expiryTime = prefs.getLong("premium_expiry", 0);
        AdsGoogleRewarder.showCongratulationDialog(this, expiryTime);
    }

    /**
     * دالة مساعدة لإنشاء شكل الفقاعة بتصميم موحد واحترافي
     */
    private TapTarget createTarget(View view, String title, String description) {
        return TapTarget.forView(view, title, description)
                .outerCircleColor(R.color.colorPrimary)      // لون الدائرة الخلفية الكبيرة
                .outerCircleAlpha(0.96f)                     // شفافية الخلفية (تعتيم قوي للتركيز)
                .targetCircleColor(android.R.color.white)    // لون دائرة الهدف
                .titleTextSize(20)                           // حجم خط العنوان
                .titleTextColor(android.R.color.white)       // لون العنوان
                .descriptionTextSize(16)                     // حجم خط الوصف
                .descriptionTextColor(android.R.color.white) // لون الوصف
                .drawShadow(true)                            // ظل للنصوص لزيادة الوضوح
                .cancelable(false)                           // منع الإغلاق عند الضغط في الفراغ (إجبار المستخدم على المتابعة أو زر تخطي)
                .tintTarget(true)                            // تلوين الأيقونة المستهدفة
                .transparentTarget(false)                    // عدم جعل الهدف شفافاً بالكامل
                .targetRadius(60);                           // نصف قطر دائرة الهدف
    }


    private void updateAccountMaps(List<Account> accounts) {
        accountNameToIdMap.clear();
        String currentUID = isGuest ? guestUID : AppAuth.getUserID(this);// (FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : licenceData.getUserUID());
        if (currentUID.isEmpty()) return;

        for (Account account : accounts) {
            if (currentUID.equals(account.getOwnerUID()) && account.getAccountName() != null) {
                accountNameToIdMap.put(account.getAccountName(), account.getId());
            }
        }
    }

    private void checkPendingTransactionsDialog() {
        if (isGuest) {
            return; // لا تظهر للمستخدمين الضيوف
        }

        if (transactionController == null) {
            transactionController = new TransactionUploadController(this);
        }

        // التحقق من وجود عمليات معلقة
        if (transactionController.hasPendingDialog()) {
            showPendingTransactionsAlert();
        }
    }

    /**
     * 🔥 **دالة جديدة: عرض ديالوج تحذير العمليات المعلقة**
     */
    private void showPendingTransactionsAlert() {
        // التأكد أننا في الواجهة الرئيسية
        if (isFinishing() || isDestroyed()) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.daily_plan_tit2))
                .setMessage(getString(R.string.daily_plan_msg2))
                .setPositiveButton(getString(R.string.upgrade_plan), (dialog, which) -> {
                    // 🔥 فتح شاشة الترقية
                    handlePurchaseApp();
                    transactionController.clearDialogFlag(); // مسح العلامة
                })
                .setNegativeButton(getString(R.string.later), (dialog, which) -> {
                    // 🔥 تأجيل التحذير
                    transactionController.clearDialogFlag(); // مسح العلامة
                    dialog.dismiss();
                })
                .setCancelable(false)
                .setIcon(R.drawable.ic_alert)
                .setOnDismissListener(dialog -> {
                    transactionController.clearDialogFlag(); // 🔥 التأكد من مسح العلامة
                })
                .show();
    }

    // --- 1. إعدادات الحواف والمسافات (الذكاء في التصميم) ---
    private void setupEdgeToEdgeInsets() {
        View bottomNavContainer = binding.bottomNavContainer; // أو findViewById(R.id.bottom_nav_container);

        if (bottomNavContainer != null) {
            // 1. حفظ الهامش السفلي الأصلي المحدد في XML (مثلاً 24dp)
            // هذا مهم جداً لكي لا يلتصق العنصر بالحافة عند الرفع
            ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) bottomNavContainer.getLayoutParams();
            int originalBottomMargin = layoutParams.bottomMargin;
            // 2. تطبيق المستمع لضبط الهوامش بناءً على أبعاد الشاشة
            ViewCompat.setOnApplyWindowInsetsListener(bottomNavContainer, (view, windowInsets) -> {
                // جلب أبعاد أشرطة النظام (شريط الحالة + شريط التنقل السفلي)
                Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());

                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();

                // المعادلة: الهامش الجديد = الهامش الأصلي + ارتفاع أزرار النظام
                params.bottomMargin = originalBottomMargin + systemBars.bottom;

                view.setLayoutParams(params);

                // إرجاع Insets كما هي لباقي العناصر
                return windowInsets;
            });
        }
        if (binding.budgetFragmentContainer != null) {
            ViewGroup.MarginLayoutParams budgetLp =
                    (ViewGroup.MarginLayoutParams) binding.budgetFragmentContainer.getLayoutParams();
            int originalBudgetBottomMargin = budgetLp.bottomMargin;
            ViewCompat.setOnApplyWindowInsetsListener(binding.budgetFragmentContainer, (view, windowInsets) -> {
                Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                view.setPadding(view.getPaddingLeft(), systemBars.top, view.getPaddingRight(), view.getPaddingBottom());
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
                params.bottomMargin = originalBudgetBottomMargin + systemBars.bottom;
                view.setLayoutParams(params);
                return windowInsets;
            });
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.drawerLayout, (v, windowInsets) -> {
            // الحصول على أبعاد أشرطة النظام (شريط الحالة وشريط التنقل)
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            // الحصول على أبعاد لوحة المفاتيح (IME)
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            // 1. التعامل مع الشريط العائم (التولبار):
            // نضيف هامش علوي بمقدار ارتفاع شريط الحالة لكي لا يتداخل معه
            // نستخدم updateLayoutParams لتعديل الهامش

            // 3. ضبط ارتفاع الفاصل (Spacer) ليساوي ارتفاع شريط الحالة
            View spacer = findViewById(R.id.status_bar_spacer);
            if (spacer != null) {
                ViewCompat.setOnApplyWindowInsetsListener(spacer, (view, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

                    // تغيير ارتفاع الفاصل ليطابق ارتفاع شريط الحالة
                    ViewGroup.LayoutParams params = view.getLayoutParams();
                    if (params.height != bars.top) {
                        params.height = bars.top;
                        view.setLayoutParams(params);
                    }

                    return WindowInsetsCompat.CONSUMED;
                });
            }
            View contentContainer = findViewById(R.id.view_pager); // تأكد أن هذا الـ ID موجود في الـ XML
            if (contentContainer != null) {
                ViewCompat.setOnApplyWindowInsetsListener(contentContainer, (view, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), bars.bottom);
                    return insets;
                });
            }

            // 2. التعامل مع الكارد السفلي ولوحة المفاتيح:
            // نريد أن يرتفع الكارد بمقدار ارتفاع الكيبورد عندما يظهر
            ViewCompat.setOnApplyWindowInsetsListener(binding.bottomSummaryCard, (view, insets) -> {
                Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
                Insets navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());

                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
                // نستخدم Math.max لضمان وجود مسافة سواء الكيبورد مفتوح أو لا (مع شريط التنقل)
                int bottomPadding = Math.max(imeInsets.bottom, navBars.bottom) + 16;
                params.bottomMargin = bottomPadding;
                view.setLayoutParams(params);
                return WindowInsetsCompat.CONSUMED;
            });

            return windowInsets;
        });
    }


    // --- 2. منطق الشريط العائم والبحث ---
    private void setupFloatingToolbarActions() {

        // فتح القائمة الجانبية
        binding.btnMenu.setOnClickListener(v -> binding.drawerLayout.openDrawer(GravityCompat.START));

        // تفعيل وضع البحث
        binding.btnSearch.setOnClickListener(v -> toggleSearchMode(true));

        // إلغاء وضع البحث
        binding.btnCloseSearch.setOnClickListener(v -> toggleSearchMode(false));

        // الأيقونات الأخرى
        binding.btnComputer.setOnClickListener(v -> {
            startActivity(new Intent(this, WebServerActivity.class));
        });
        binding.btnReports.setOnClickListener(v -> showReportsDialogFromToolbar());
    }

    private void setupNavigationDrawer() {
        binding.navView.setNavigationItemSelectedListener(item -> {
            // التعامل مع النقرات
            binding.drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
    }

    private void addDefaultCurrencyAndProceed() {
        addCurrencyToDatabase(getString(R.string.local_currency));
    }

    private void addCurrencyToDatabase(String name) {
        String uid;
        Resources localizedResources = LanguageManager.getLocalizedResources(this);
        final String symbole = localizedResources.getString(R.string.currency_symbol_loc);
        MyApplication.defaultCurrencyName = getString(R.string.local_currency);
        if (isGuest) {
            uid = guestUID;
        } else {
            uid = currentUID;
        }
        if (!db.isOpen()) {
            db = AppDatabase.getDatabase(MainActivity.this);
        }
        Log.d(TAG, "إضافة العمله");
        AppDatabase.databaseWriteExecutor.execute(() -> {
            int currencyCount = db.currencyDao().getCurrencyCount();
            int accTypeCount = db.accountTypeDao().getAccountTypeCount();
            if (db.currencyDao().getCurrencyCount() < 1) {
                Currency existing = repository.getCurrencyByName(name);
                Log.e(TAG, "defaultCurrencyName: " + name);
                if (existing == null) {
                    Currency newCurrency = new Currency();
                    newCurrency.setName(name);
                    newCurrency.setOwnerUID(uid);
                    newCurrency.setSyncStatus("NEW");
                    newCurrency.setDefault(true);
                    newCurrency.setSymbol(symbole);
                    newCurrency.setCode(symbole);
                    newCurrency.setFirestoreId(UUIDGenerator.generateSequentialUUID().toString());
                    newCurrency.setLastModified(System.currentTimeMillis());
                    sharedPrefsManager.setLocalCurrency(name.trim());
                    MyApplication.defaultCurrencyName = (name.trim());
                    db.currencyDao().insert(newCurrency);
                }
                String[] accountTypes = {
                        localizedResources.getString(R.string.account_type_customer),
                        localizedResources.getString(R.string.account_type_supplier),
                        localizedResources.getString(R.string.account_type_general)
                };
                if (accTypeCount > 0) {
                    runOnUiThread(() -> {
                        mainHandler.postDelayed(() -> {
                            Intent intent = new Intent(MainActivity.this, AddTransactionActivity.class);
                            // ✅ استخدام name مباشرة بدلاً من availableCurrencies
                            intent.putExtra("CURRENCY", name);
                            startActivity(intent);
                            prefs.edit().putBoolean("firstRun", false).apply();
                        }, 1000);
                        addDollarCurrenncy();
                        prefs.edit().putBoolean("firstRun", false).apply();
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
                Log.d(TAG, "تمت إضافة العملة بنجاح، جاري الانتقال إلى الشاشة الرئيسية...");
                mainHandler.postDelayed(() -> {
                    Intent intent = new Intent(MainActivity.this, AddTransactionActivity.class);
                    // ✅ استخدام name مباشرة
                    intent.putExtra("CURRENCY", name);
                    startActivity(intent);
                    prefs.edit().putBoolean("firstRun", false).apply();
                    addDollarCurrenncy();
                }, 1000);
            });
        });
    }

    private void addDollarCurrenncy() {
        String uid;
        Resources localizedResources = LanguageManager.getLocalizedResources(this);
        String name = localizedResources.getString(R.string._408);
        if (isGuest) {
            uid = guestUID;
        } else {
            uid = currentUID;
        }
        AppDatabase.databaseWriteExecutor.execute(() -> {
            int currencyCount = db.currencyDao().getCurrencyCount();
            if (db.currencyDao().getCurrencyCount() < 1) {
                Currency existing = repository.getCurrencyByName(name);
                Log.e(TAG, "defaultCurrencyName: " + name);
                if (existing == null) {
                    Currency newCurrency = new Currency();
                    newCurrency.setName(name);
                    newCurrency.setOwnerUID(uid);
                    newCurrency.setSymbol("$");
                    newCurrency.setCode("$");
                    newCurrency.setSyncStatus("NEW");
                    newCurrency.setDefault(false);
                    newCurrency.setFirestoreId(UUIDGenerator.generateSequentialUUID().toString());
                    newCurrency.setLastModified(System.currentTimeMillis());
                    db.currencyDao().insert(newCurrency);
                }
            }
        });
    }

    int counter = 0;

    private void setupLanguageObserver() {
        languageViewModel.getSelectedLanguage().observe(this, lang -> {
            if (lang != null && !lang.isEmpty()) {
                String currentLang = preAppliedLanguage;
                Log.e(TAG, "اللغة الجديدة: " + lang + " | اللغة الحالية: " + currentLang);

                if (!lang.equals(currentLang)) {
                    Log.e(TAG, "تم اكتشاف تغيير اللغة، جاري التطبيق...");

                    // 🔥 استخدام الدالة الجديدة من BaseActivity
                    BaseActivity.applyLanguage(MainActivity.this, lang);

                    // تحديث التفضيلات
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("language", lang);
                    editor.apply();

                    // 🔥 إعادة إنشاء النشاط بشكل صحيح
                    mainHandler.postDelayed(() -> {
                        Intent intent = new Intent(MainActivity.this, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    }, 300);
                }
            }
        });
    }

    private void restartAfterChangeLang() {

        // 2. الانتظار قليلاً لضمان إغلاق قاعدة البيانات
        mainHandler.postDelayed(() -> {
            // 3. إعادة تشغيل التطبيق من SplashActivity
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            // 4. إنهاء جميع الأنشطة
            finishAffinity();
            // 5. إنهاء العملية لضمان إعادة التهيئة الكاملة
            Process.killProcess(Process.myPid());
        }, 500); // تأخير 1 ثانية لضمان إغلاق قاعدة البيانات
    }

    private void restartAppFromSplash() {
        Log.d(TAG, "إعادة تشغيل التطبيق من SplashActivity مع إعادة تهيئة قاعدة البيانات");

        // 1. إغلاق قاعدة البيانات الحالية بشكل صحيح
        closeDatabase();

        // 2. الانتظار قليلاً لضمان إغلاق قاعدة البيانات
        mainHandler.postDelayed(() -> {
            // 3. إعادة تشغيل التطبيق من SplashActivity
            Intent intent = new Intent(this, SplashActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            // 4. إنهاء جميع الأنشطة
            finishAffinity();
            // 5. إنهاء العملية لضمان إعادة التهيئة الكاملة
            Process.killProcess(Process.myPid());

        }, 1000); // تأخير 1 ثانية لضمان إغلاق قاعدة البيانات
    }

    private void closeDatabase() {
        try {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                AppDatabase db = AppDatabase.getDatabase(this);
                if (db != null) {
                    db.close();
                    Log.d(TAG, "تم إغلاق قاعدة البيانات بنجاح");
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "خطأ في إغلاق قاعدة البيانات: " + e.getMessage());
        }
    }

    private void completelyReinitializeDataLayer() {
        try {
            Log.d(TAG, "إعادة تهيئة كاملة لطبقة البيانات");

            // 1. إعادة إنشاء AppDatabase (سيقوم Room بإعادة فتح الاتصال)
            db = AppDatabase.getDatabase(getApplication());

            // 2. إعادة إنشاء Repository
            repository = new DaftreeRepository(getApplication());

            // 3. إعادة إنشاء جميع ViewModels
            mainViewModel = new ViewModelProvider(this).get(MainViewModel.class);
            profileViewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
            currencyViewModel = new ViewModelProvider(this).get(CurrencyViewModel.class);
            accountTypeViewModel = new ViewModelProvider(this).get(AccountTypeViewModel.class);

            // 4. إعداد المراقبات
            setupObservers();
            setupCurrencyObserver();

            // 5. التحقق من البيانات بعد التهيئة
            verifyDataAfterReinitialization();

            Log.d(TAG, "تمت إعادة تهيئة طبقة البيانات بنجاح");

        } catch (Exception e) {
            Log.e(TAG, "خطأ في إعادة تهيئة طبقة البيانات: " + e.getMessage());
        }
    }

    private void verifyDataAfterReinitialization() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                int accountCount = repository.getAccountDao().getAccountsCount();
                int transactionCount = repository.getTransactionsDao().getTransactionsCount();

                Log.d(TAG, "📊 بعد إعادة التهيئة:");
                Log.d(TAG, "   - عدد الحسابات: " + accountCount);
                Log.d(TAG, "   - عدد العمليات: " + transactionCount);

                if (accountCount > 0) {
                    runOnUiThread(() -> {
                        if (mainViewModel != null) {
                            mainViewModel.refreshData();
                            Log.d(TAG, "✅ تم طلب تحديث البيانات في الواجهة");
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "خطأ في التحقق من البيانات: " + e.getMessage());
            }
        });
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private void normalUser() {


        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUID == null || currentUID.isEmpty()) return;
        if (currentUser != null) {
            currentUID = currentUser.getUid();
        }
        FirestoreSyncManager.getInstance().listenForReferralNotifications(currentUID, this);


        if ((!versionManager.getFirestoreUser_isAdded() && isUserAdded)) {
            Log.e(TAG, "updateUserInFirestore : " + " جاري تحديث بيانات المستخدم");
            googleAuthHelper.saveOrUpdateUserInFirestoreUpgrade(currentUser);
        }
        currentUserEmail = licenceData.getEmail();
        updateLinkedAccountsMenuItem();
//        if (currentUser != null && currentUser.getEmail() != null) {
//            currentUserEmail = currentUser.getEmail().trim();
//            updateLinkedAccountsMenuItem();
//
//
//        }

    }

    private boolean checkAppSafety() {
        if (TamperDetection.isAppTampered(this)) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.checkAppSaftey_01))
                    .setMessage(getString(R.string.checkAppSaftey_02))
                    .setPositiveButton(getString(R.string.checkAppSaftey_03), (dialog, which) -> {
                        finishAffinity();
                    })
                    .setCancelable(false)
                    .show();
            return false;
        }

        // 2. فحص وضع المطور
        if (TamperDetection.isDeveloperOptionsEnabled(this)) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.checkAppSaftey_04))
                    .setMessage(getString(R.string.checkAppSaftey_05))
                    .setPositiveButton(getString(R.string.checkAppSaftey_06), (dialog, which) -> {
                        try {
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                            startActivity(intent);
                        } catch (Exception e) {
                            // في حال فشل الانتقال المباشر، يمكن توجيه المستخدم يدويًا
                            new AlertDialog.Builder(this)
                                    .setTitle(getString(R.string.checkAppSaftey_07))
                                    .setMessage(getString(R.string.checkAppSaftey_08))
                                    .setPositiveButton(getString(R.string.checkAppSaftey_09), (dialog2, which2) -> {
                                        finishAffinity();
                                    })
                                    .setCancelable(false)
                                    .show();
                        }
                        finishAffinity(); // إغلاق التطبيق بعد توجيه المستخدم
                    })
                    .setNegativeButton(getString(R.string.checkAppSaftey_10), (dialog, which) -> {
                        finishAffinity();
                    })
                    .setCancelable(false)
                    .show();
            return false;
        }
        return true;
    }

    private void checkForUpdatesOrHelp() {
        // تهيئة مدير الإصدارات
        if (versionManager == null) {
            versionManager = new VersionManager(this);
        }

        // 1. فحص هل التطبيق تثبيت جديد (لأول مرة)؟
        if (versionManager.isFreshInstall()) {
            // إذا كان جديداً، لا تعرض شيء واحفظ الإصدار الحالي صامتاً
            Log.d(TAG, "Fresh Install detected. Skipping update dialog.");
            versionManager.saveCurrentVersion();
            versionManager.savePreviousVersion(versionManager.getCurrentVersionName());
            // يمكنك هنا استدعاء كود المساعدة (Help) إذا أردت
            // showHelpDialog();
        }
        // 2. فحص هل قام المستخدم بتحديث التطبيق للتو؟
        else if (versionManager.isAppUpdated()) {
            if (!versionManager.isMajorUpdate()) return;
            // إذا كان تحديثاً، اعرض الديالوج
            Log.d(TAG, "App Update detected. Showing dialog.");
            UpdateDialog updateDialog = new UpdateDialog(this);
            updateDialog.setOnDismissListener(dialog -> {
//                versionManager.saveCurrentVersion();
            });
            updateDialog.show();
        }
        // 3. الحالة العادية (لا تحديث ولا تثبيت جديد)
//        else {
//            // يمكنك هنا وضع كود الفحص الدوري من السيرفر إذا أردت
//            // أو تركه فارغاً كما هو مطلوب
//        }
    }

    private void referringNotification() {
        FirestoreSyncManager sync = FirestoreSyncManager.getInstance();
        sync.setReferralNotificationListener((userId, points, notiMessegeTitel, notiMessege) -> {
            runOnUiThread(() -> {
                // ديالوج
                new AlertDialog.Builder(this)
                        .setTitle(notiMessegeTitel)
                        .setMessage(notiMessege)
                        .setPositiveButton(getString(R.string.continue_button), null)
                        .show();
// إشعار
                NotificationService ns = new NotificationService(this);
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED) {
                    ns.showRewardNotification(notiMessegeTitel,
                            notiMessege);
                }
            });
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupEventListeners() {
        setupAddTransactionButtonGlow();
        setupCurrencyTextViewColorTransition();
        binding.navItemBudget.setOnClickListener(v -> switchMainTab(true));
        binding.navItemReports.setOnClickListener(v -> {
            if (!licenseManager.canCreateTransaction() && !licenceData.isPremium()) {
                showExpiryDialog(getString(R.string.plan_expired_title), getString(R.string.plan_expired_message));
                return;
            }
            showReportsDialogFromToolbar();
        });
        binding.navItemHome.setOnClickListener(v -> {
            switchMainTab(false);
            binding.viewPager.setCurrentItem(1, false);
        });
        if (isBudgetTabActive) {
            switchMainTab(true);
        }
        binding.navItemLinkedAccounts.setOnClickListener(v -> {
            if (!isUserAdded) {
//                DialogHelper.show(this,getString(R.string.login_1), DialogHelper.FeedbackType.WARNING, null);
                notSigned();
                return;
            }

            if (!licenceData.isLinkedAccount()) {
                Intent intent = new Intent(this, DebtActivationActivity.class);
                startActivity(intent);
            } else if (!licenceData.isDebtFeatureEnabled()) {
                DialogHelper.show(this, getString(R.string.warning_linked_msg), DialogHelper.FeedbackType.WARNING,
                        () -> {
                            licenceData.setDebtFeatureEnabled(true);
                            FirebaseFirestore firestore = FirebaseFirestore.getInstance();
                            Map<String, Object> updates = new HashMap<>();
                            updates.put("isDebtFeatureEnabled", true);
                            updates.put("lastModified", System.currentTimeMillis());

                            firestore.collection("users").document(currentUID)
                                    .set(updates, SetOptions.merge())
                                    .addOnSuccessListener(aVoid -> {
                                        Log.e("DebtActivationViewModel", "Save succeeded!");
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e("DebtActivationViewModel", "Save failed", e);
                                    });
                            Intent intent = new Intent(this, DebtSummaryActivity.class);
                            startActivity(intent);
                        }, null);

            } else {

                Intent intent = new Intent(this, DebtSummaryActivity.class);
                startActivity(intent);
            }

        });

        binding.addTransactionButton.setOnClickListener(v -> showQuickAddBottomSheet());
        binding.currencyTextView.setOnClickListener(v -> cycleCurrency());
        openDocumentLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                startRestoreProcess(uri);
            }
        });

        backupLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == AppCompatActivity.RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null) {
                    performBackup(uri);
                }
            }
        });

        // إعداد GestureDetector للسحب العمودي
        final GestureDetector gestureDetector = new GestureDetector(this, new VerticalSwipeListener());

        // إعداد OnTouchListener للـ ViewPager2
        binding.viewPager.setOnTouchListener((v, event) -> {
            // إذا كان السحب يحدث داخل RecyclerView، لا نعالج السحب لتغيير العملة
            if (isScrollingRecyclerView) {
                return false;
            }

            // معالجة السحب لتغيير العملة فقط في المناطق الفارغة
            return gestureDetector.onTouchEvent(event);
        });
    }

    private void showExpiryDialog(String titel, String textInfo) {
        if (isFinishing() || isDestroyed()) return;
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_no_operations, null);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();
        TextView titelTextView = dialogView.findViewById(R.id.titel_textView);
        TextView noTransactionTextView = dialogView.findViewById(R.id.no_transaction_textView);
        MaterialButton purchaseBtn = dialogView.findViewById(R.id.btn_purchase);
        MaterialButton watchAdBtn = dialogView.findViewById(R.id.btn_watch_ad);
        MaterialButton inviteBtn = dialogView.findViewById(R.id.btn_invite);
        MaterialButton cancelBtn = dialogView.findViewById(R.id.btn_cancel);
        titelTextView.setText(titel);
        inviteBtn.setVisibility(View.VISIBLE);
        noTransactionTextView.setText(textInfo);
        purchaseBtn.setOnClickListener(v -> {
            dialog.dismiss();


          //  Intent subscribeIntent = new Intent(MainActivity.this, SubscriptionActivity.class);
          //  subscribeIntent.putExtra("purchase", true);
         //   subscribeLauncher.launch(subscribeIntent);

            handlePurchaseApp();
            
        });
        watchAdBtn.setOnClickListener(v -> {
            dialog.dismiss();
            watchAd();
        });
        inviteBtn.setOnClickListener(v -> {
            dialog.dismiss();
            ReferralManager referralManager = new ReferralManager(this);
            referralManager.generateAndShareReferralLink(FirebaseAuth.getInstance().getCurrentUser());
        });
        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        if (dialog.getWindow() != null) {
            android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams();
            lp.copyFrom(dialog.getWindow().getAttributes());
            lp.width = android.view.WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;

            dialog.getWindow().setAttributes(lp);
        }
    }

    private void updateNavigationMenuItems() {
        if (currentUserData == null) return;

        NavigationView navigationView = findViewById(R.id.nav_view);
        Menu menu = navigationView.getMenu();
        //   boolean isSignedIn = (FirebaseAuth.getInstance().getCurrentUser() != null);
//        boolean isPremium = currentUserData.isIs_premium();
        boolean isPremium = licenceData.isPremium();
        boolean shouldShowUpgradeOptions = !isPremium;
//        menu.findItem(R.id.nav_purchase_app).setVisible(shouldShowUpgradeOptions);
        MenuItem purchaseItem = menu.findItem(R.id.nav_purchase_app);
        MenuItem itemAdmin = menu.findItem(R.id.menu_admin);
        purchaseItem.setVisible(shouldShowUpgradeOptions);
        itemAdmin.setVisible(licenceData.isUserAdmin());
        final MenuItem adminDashboardItem = menu.findItem(R.id.nav_backup_data);
        adminDashboardItem.setVisible(licenceData.isUserAdmin());


        MenuItem inviteItem = menu.findItem(R.id.nav_invite_friend);
        MenuItem adWatchItem = menu.findItem(R.id.nav_watch_ad);
        //inviteItem.setVisible(shouldShowUpgradeOptions); // خيار الدعوة متاح دائماً للمسجلين
        int Referral_rewards = licenceData.getReferralRewards();
        int adWatch = licenceData.getAdRewards();
        if (adWatch > 0) {
            adWatchItem.setTitle(getString(R.string.menu_watch_ad) + "(+" + adWatch + ")");
        } else {
            adWatchItem.setTitle(getString(R.string.menu_watch_ad));
        }
        inviteItem.setVisible(true);
        if (Referral_rewards > 0) {
            inviteItem.setTitle(getString(R.string.menu_invite_friend) + "(+" + Referral_rewards + ")");
        } else {
            inviteItem.setTitle(getString(R.string.menu_invite_friend));
        }
        MenuItem manageDevice = menu.findItem(R.id.nav_manage_devices);
        if (isGuest) {
            manageDevice.setVisible(false);
        } else {
            manageDevice.setVisible(true);
        }
        AppFontHelper.styleNavigationView(this, navigationView);
    }


    private class VerticalSwipeListener extends GestureDetector.SimpleOnGestureListener {
        private static final int SWIPE_THRESHOLD = 100;
        private static final int SWIPE_VELOCITY_THRESHOLD = 100;

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            float diffY = e2.getY() - e1.getY();
            if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                if (diffY > 0) {
                    // سحب للأسفل: التمرير للخلف
                    //  handleSmartNavigation(binding.viewPager.getCurrentItem(), -1);
                    cycleCurrency();
                } else {
                    // سحب للأعلى: التمرير للأمام
                    handleSmartNavigation(binding.viewPager.getCurrentItem(), 1);
                    cycleCurrency();
                }
                return true;
            }
            return false;
        }
    }

    public void setRecyclerViewScrolling(boolean isScrolling) {
        this.isScrollingRecyclerView = isScrolling;
    }

    private void handleSmartNavigation(final int targetPosition, final int direction) {
        int totalPages = pagerAdapter.getItemCount();
        int nextPosition = targetPosition;

        if (direction == 1) { // التمرير للأمام
            nextPosition = (targetPosition + 1) % totalPages;
        } else { // التمرير للخلف
            nextPosition = (targetPosition - 1 + totalPages) % totalPages;
        }

        if (targetPosition == 0) {
            mainViewModel.setFilter(null);
            return;
        }
 //       String targetType = accountTypesList.get(targetPosition - 1).name;

//        AppDatabase.databaseWriteExecutor.execute(() -> {
  //          int count = repository.getAccountDao().hasAnyTransactionsForType(targetType);
  //          if (count > 0) {
                // النوع يحتوي على بيانات، قم بتحديث الفلتر
   //             runOnUiThread(() -> mainViewModel.setFilter(targetType));
   //         } else {
                // النوع فارغ، انتقل إلى التالي بشكل دائري
 //               runOnUiThread(() -> {
  //                    isProgrammaticScroll = true;
   //                 binding.viewPager.setCurrentItem((targetPosition + direction) % pagerAdapter.getItemCount());
   //             });
  //          }
 //       });
 AccountType targetTypeObj = accountTypesList.get(targetPosition - 1);
String targetTypeName = targetTypeObj.getName();
String targetTypeId   = targetTypeObj.getFirestoreId();

AppDatabase.databaseWriteExecutor.execute(() -> {
    int count = repository.getAccountDao().hasAnyTransactionsForType(targetTypeName);
    if (count > 0) {
        // ✅ نضبط الفلتر بنفس القيمة التي يستخدمها ViewPager (firestoreId)
        runOnUiThread(() -> mainViewModel.setFilter(targetTypeId));
    } else {
          runOnUiThread(() -> {
                      isProgrammaticScroll = true;
                    binding.viewPager.setCurrentItem((targetPosition + direction) % pagerAdapter.getItemCount());
                }); 
    }
});
        
    }

    private void performBackup(Uri destinationUri) {
        try {
            File dbFile = getDatabasePath(AppDatabase.getDatabase(this).getOpenHelper().getDatabaseName());
            try (InputStream source = new FileInputStream(dbFile);
                 OutputStream destination = getContentResolver().openOutputStream(destinationUri)) {

                byte[] buffer = new byte[1024];
                int length;
                while ((length = source.read(buffer)) > 0) {
                    destination.write(buffer, 0, length);
                }
                Toast.makeText(this, getString(R.string.performBackup_19), Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.performBackup_20) + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupToolbarAndDrawer() {
        drawerLayout = binding.drawerLayout;
        navigationView = binding.navView;
        navigationView.setNavigationItemSelectedListener(this);
        applyThemeBasedOnSystem();
        View headerView = navigationView.getHeaderView(0);
        MenuItem loginItem = navigationView.getMenu().findItem(R.id.nav_login);
        if (isGuest) {
            loginItem.setVisible(true);
            binding.warningText.setVisibility(View.VISIBLE);
        } else {
            loginItem.setVisible(false);
            binding.warningText.setVisibility(View.GONE);
        }

        TextView navUserEmail = headerView.findViewById(R.id.textViewUserEmail);
        TextView navVersionNo = headerView.findViewById(R.id.textViewVersionNo);
        LinearLayout icPrim = headerView.findViewById(R.id.llIcPrim);
        if (licenceData.isPremium()) {
            icPrim.setVisibility(View.VISIBLE);
        } else {
            icPrim.setVisibility(View.GONE);
        }

        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String versionName = pInfo.versionName;
            String appName = getString(R.string.app_name);
            String versionText = getString(R.string.version_text, appName, versionName);
            navVersionNo.setText(versionText);
        } catch (PackageManager.NameNotFoundException e) {
            navVersionNo.setText(R.string.app_name);
        }

        if (currentUserEmail != null && !isGuest) {
            navUserEmail.setText(currentUserEmail);
            updateMenuVisibility();
        } else {
            navUserEmail.setText(R.string.local_email);
        }

        // --- التعديلات تبدأ من هنا ---

        MenuItem passwordLockItem = navigationView.getMenu().findItem(R.id.nav_password_lock);
        SwitchCompat passwordSwitch = passwordLockItem.getActionView().findViewById(R.id.menu_switch);

        MenuItem biometricLockItem = navigationView.getMenu().findItem(R.id.nav_biometric_lock);
        SwitchCompat biometricSwitch = biometricLockItem.getActionView().findViewById(R.id.menu_switch);

        MenuItem generalNotificationsItem = navigationView.getMenu().findItem(R.id.nav_general_notifications);
        SwitchCompat generalSwitch = generalNotificationsItem.getActionView().findViewById(R.id.menu_switch);

        MenuItem dailyRemindersItem = navigationView.getMenu().findItem(R.id.nav_daily_reminders);
        SwitchCompat dailySwitch = dailyRemindersItem.getActionView().findViewById(R.id.menu_switch);

        updateNotificationSwitches(generalSwitch, dailySwitch);

        generalSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                handleGeneralNotificationsToggle(isChecked, dailySwitch);
            }
        });

        dailySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                handleDailyRemindersToggle(isChecked, generalSwitch);
            }
        });

        MenuItem linkedAccountItem = navigationView.getMenu().findItem(R.id.nav_linked_accounts_lock);
        SwitchCompat linkedAccountSwitch = Objects.requireNonNull(linkedAccountItem.getActionView()).findViewById(R.id.menu_switch);

        linkedAccountSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {

                licenceData.setDebtFeatureEnabled(isChecked);
                Map<String, Object> updates = new HashMap<>();
                updates.put("isDebtFeatureEnabled", isChecked);
                updates.put("lastModified", System.currentTimeMillis());
                FirebaseFirestore.getInstance().collection("users").document((currentUID))
                        .set(updates, SetOptions.merge())
                        .addOnSuccessListener(aVoid -> {
                            Log.e("DebtActivationViewModel", "Save succeeded!");
                        })
                        .addOnFailureListener(e -> {
                            Log.e("DebtActivationViewModel", "Save failed", e);
                        });
            }
        });

        boolean isDebtFeatureEnabled = licenceData.isDebtFeatureEnabled();
        linkedAccountSwitch.setChecked(isDebtFeatureEnabled);
        updateLockSwitches(passwordSwitch, biometricSwitch);

        passwordSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                handlePasswordLock(isChecked);
            }
        });

        biometricSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                handleBiometricLock(isChecked);
            }
        });

        // --- نهاية التعديلات ---
        AppFontHelper.styleNavigationView(this, navigationView);

        drawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {
            }

            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                updateMenuVisibility();
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
            }

            @Override
            public void onDrawerStateChanged(int newState) {
            }
        });
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

    private void updateNotificationSwitches(SwitchCompat generalSwitch, SwitchCompat dailySwitch) {
        boolean isGeneralEnabled = dailyReminderManager.areGeneralNotificationsEnabled();
        boolean isDailyEnabled = dailyReminderManager.isEnabled();

        generalSwitch.setChecked(isGeneralEnabled);
        dailySwitch.setChecked(isDailyEnabled);

        // تعطيل مفتاح الإشعارات اليومية إذا كانت الإشعارات العامة معطلة
        dailySwitch.setEnabled(isGeneralEnabled);
    }

    /**
     * معالجة تبديل الإشعارات العامة
     */
    private void handleGeneralNotificationsToggle(boolean isEnabled, SwitchCompat dailySwitch) {
        dailyReminderManager.setGeneralNotificationsEnabled(isEnabled);

        if (isEnabled) {
            Toast.makeText(this, getString(R.string.general_notifications_enabled), Toast.LENGTH_SHORT).show();
            // تمكين مفتاح الإشعارات اليومية
            dailySwitch.setEnabled(true);
        } else {
            Toast.makeText(this, getString(R.string.general_notifications_set_disabled), Toast.LENGTH_SHORT).show();
            // تعطيل مفتاح الإشعارات اليومية وإيقافها
            dailySwitch.setEnabled(false);
            dailySwitch.setChecked(false);
        }
    }

    /**
     * معالجة تبديل الإشعارات اليومية
     */
    private void handleDailyRemindersToggle(boolean isEnabled, SwitchCompat generalSwitch) {
        // التأكد من أن الإشعارات العامة مفعلة أولاً
        if (isEnabled && !dailyReminderManager.areGeneralNotificationsEnabled()) {
            Toast.makeText(this, getString(R.string.daily_notifications_set_disabled), Toast.LENGTH_SHORT).show();
            // إعادة المفتاح إلى وضعه السابق
            updateNotificationSwitches(generalSwitch, (SwitchCompat) generalSwitch);
            return;
        }

        dailyReminderManager.setEnabled(isEnabled);

        if (isEnabled) {
            Toast.makeText(this, getString(R.string.notifications_enabled), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, getString(R.string.notifications_disabled), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * تحديث جميع مفاتيح القائمة الجانبية (القفل + الإشعارات)
     */
    private void updateAllNavigationSwitches() {
        NavigationView navigationView = findViewById(R.id.nav_view);
        Menu menu = navigationView.getMenu();

        // تحديث مفاتيح القفل (الكود الحالي)
        MenuItem passwordLockItem = menu.findItem(R.id.nav_password_lock);
        SwitchCompat passwordSwitch = passwordLockItem.getActionView().findViewById(R.id.menu_switch);

        MenuItem biometricLockItem = menu.findItem(R.id.nav_biometric_lock);
        SwitchCompat biometricSwitch = biometricLockItem.getActionView().findViewById(R.id.menu_switch);

        updateLockSwitches(passwordSwitch, biometricSwitch);

        // تحديث مفاتيح الإشعارات (الجديدة)
        MenuItem generalNotificationsItem = menu.findItem(R.id.nav_general_notifications);
        SwitchCompat generalSwitch = generalNotificationsItem.getActionView().findViewById(R.id.menu_switch);

        MenuItem dailyRemindersItem = menu.findItem(R.id.nav_daily_reminders);
        SwitchCompat dailySwitch = dailyRemindersItem.getActionView().findViewById(R.id.menu_switch);

        updateNotificationSwitches(generalSwitch, dailySwitch);
    }

    private void showLanguageSelectionDialog() {
        LanguageSelectionDialog dialog = new LanguageSelectionDialog();
        dialog.setLanguageSelectionListener(this);
        dialog.show(getSupportFragmentManager(), "LanguageSelectionDialog");
    }

    @Override
    public void onLanguageSelected(String languageCode) {
        Log.d(TAG, "Language selected: " + languageCode);
        languageViewModel.setLanguage(languageCode);
        counter = 0;

        // Apply selected language
        languageManager.setLocale(languageCode);
        saveLanguagePreference(languageCode);
        subscribeToSmartLanguageTopic(languageCode);
        localLanguage = languageCode;

        LanguageManager.applyLocale(MainActivity.this, languageCode);

        Log.e(TAG, "تم اختيار اللغة من الديالوج: " + languageCode);

        mainHandler.postDelayed(() -> {
            recreate();
        }, 500);

    }

    @Override
    public void onLanguageSelectionCancelled() {
        Log.d(TAG, "Language selection cancelled, using default Arabic");

//        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
//        String language =prefs.getString(KEY_LANGUAGE, "en");
//        languageManager.setLocale(language);
//        saveLanguagePreference(language);
//
//        recreate();
    }

    private void saveLanguagePreference(String languageCode) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply();
    }

    private void updateMenuVisibility() {

        Menu navMenu = navigationView.getMenu();
        View headerView = navigationView.getHeaderView(0);
        TextView navUserEmail = headerView.findViewById(R.id.textViewUserEmail);
        if (currentUserEmail != null && !isGuest) {
            navUserEmail.setText(currentUserEmail);
        } else {
            navUserEmail.setText(R.string.local_email);
        }
        LinearLayout icPrim = headerView.findViewById(R.id.llIcPrim);
        if (licenceData.isPremium()) {
            icPrim.setVisibility(View.VISIBLE);

        } else {
            icPrim.setVisibility(View.GONE);

        }

        final MenuItem adminDashboardItem2 = navMenu.findItem(R.id.nav_logout);

        updateAllNavigationSwitches();
    }

    private void setupCurrencyObserver() {
        currencyViewModel.getAllCurrencies().observe(this, currencies -> {
            if (currencies != null && !currencies.isEmpty()) {
                availableCurrencies.clear();
                availableCurrencies.addAll(currencies);
                updateUiForSelectedCurrency();
            }
        });
    }

    private void setupObservers() {
        // مراقب واحد وموحد لقائمة الحسابات والإجماليات
        mainViewModel.getAccounts().observe(this, accountWithBalances -> {
//            accountsAdapter.submitList(accountWithBalances);
            updateSummariesFromBalances(accountWithBalances);
        });
        mainViewModel.getCurrenciesWithTransactions().observe(this, activeCurrencyNames -> {
            if (activeCurrencyNames != null) {
                activeCurrencies.clear();
                activeCurrencies.addAll(activeCurrencyNames);
                // تأكد دائمًا من وجود العملة المحلية في القائمة النشطة للعرض
                if (!activeCurrencies.contains(MyApplication.defaultCurrencyName)) {
                    activeCurrencies.add(0, MyApplication.defaultCurrencyName);
                }
            }
        });
    }

    private void updateSummariesFromBalances(List<AccountWithBalance> accounts) {
        double totalDebit = 0.0;  // مجموع الأرصدة المدينة (عليك)
        double totalCredit = 0.0; // مجموع الأرصدة الدائنة (لك)

        if (accounts != null) {
            for (AccountWithBalance acc : accounts) {
                if (acc.balance < 0) { // الرصيد دائن (لك)
                    totalCredit += Math.abs(acc.balance);
                } else { // الرصيد مدين (عليك)
                    totalDebit += acc.balance;
                }
            }
        }

        binding.totalDebitTextView.setText(formatNumber(totalDebit));
        binding.totalCreditTextView.setText(formatNumber(totalCredit));
    }

    private String formatNumber(Double number) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        return nf.format(number != null ? number : 0.0);
    }

    private void cycleCurrency() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean first_time_tour = prefs.getBoolean("first_time_currency", true);

        if (first_time_tour) {
            targetQueue.clear();
            targetQueue.add(createTarget(findViewById(R.id.currencyTextView),
                    getString(R.string.tut_4_title),
                    getString(R.string.tut_4_desc)));

            getSharedPreferences("AppPrefs", MODE_PRIVATE)
                    .edit()
                    .putBoolean("first_time_currency", false)
                    .apply();
        }

        if (activeCurrencies.isEmpty() || availableCurrencies.isEmpty()) {
            Toast.makeText(this, getString(R.string.cycleCurrency_21), Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. احصل على اسم العملة الحالية من الواجهة
        String currentName = binding.currencyTextView.getText().toString();

        // 2. ابحث عن موقعها في قائمة العملات النشطة
        int currentIndexInActiveList = activeCurrencies.indexOf(currentName);

        // 3. احصل على اسم العملة التالية من القائمة النشطة
        int nextIndexInActiveList = (currentIndexInActiveList + 1) % activeCurrencies.size();
        String nextCurrencyName = activeCurrencies.get(nextIndexInActiveList);

        // 4. ابحث عن موقع الاسم الجديد في القائمة الكاملة لتحديث المؤشر الرئيسي
        for (int i = 0; i < availableCurrencies.size(); i++) {
            if (availableCurrencies.get(i).name.equals(nextCurrencyName)) {
                currentCurrencyIndex = i;
                break;
            }
        }

        // 5. قم بتحديث الواجهة والـ ViewModel
        updateUiForSelectedCurrency();
    }

    private void updateUiForSelectedCurrency() {
        if (availableCurrencies.isEmpty() || currentCurrencyIndex >= availableCurrencies.size())
            return;

        Currency selectedCurrency = availableCurrencies.get(currentCurrencyIndex);
        String selectedCurrencyName = selectedCurrency.name;
        binding.currencyTextView.setText(selectedCurrencyName);
        mainViewModel.setCurrency(selectedCurrencyName);
        syncSelectedCurrencyToBudget(selectedCurrency);
    }

    private void setupBiometricAuthentication() {
        BiometricManager biometricManager = BiometricManager.from(this);

        switch (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                checkEnrolledFingerprints();
                break;
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                showCustomAlert(getString(R.string.warning_title),
                        getString(R.string.fingerprint_not_supported),
                        () -> lockManager.setBiometricLock(false));
                break;
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                showCustomAlert(getString(R.string.warning_title),
                        getString(R.string.fingerprint_unavailable),
                        () -> lockManager.setBiometricLock(false));
                break;
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                showFingerprintEnrollmentDialog();
                break;
        }
    }

    private void checkEnrolledFingerprints() {
        BiometricPrompt biometricPrompt = new BiometricPrompt(this,
                ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
//                        lockManager.enableBiometricLock();
                        lockManager.setBiometricLock(true);
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        lockManager.setBiometricLock(false);
                        Toast.makeText(MainActivity.this, errString, Toast.LENGTH_SHORT).show();
                    }
                });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.checkEnrolledFingerprints_22))
                .setDescription(getString(R.string.checkEnrolledFingerprints_23))
                .setNegativeButtonText(getString(R.string.cancel))
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void showCustomAlert(String title, String message, Runnable onOk) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(getString(R.string.ok), (dialog, which) -> {
                    if (onOk != null) onOk.run();
                })
                .show();
    }

    private void showFingerprintEnrollmentDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.warning_title))
                .setMessage(getString(R.string.fingerprint_not_enrolled))
                .setPositiveButton(getString(R.string.ok), (dialog, which) -> {
                    Intent enrollIntent = new Intent(Settings.ACTION_BIOMETRIC_ENROLL);
                    enrollIntent.putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                            BiometricManager.Authenticators.BIOMETRIC_WEAK);
                    try {
                        startActivity(enrollIntent);
                    } catch (ActivityNotFoundException e) {
                        e.printStackTrace();
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    boolean isBiometricAuthentication = false;
    boolean addBioMetric = false;

    /**
     * عند محاولة تفعيل قفل البصمة
     */
    private void handleBiometricLock(boolean isEnabled) {
        if (isEnabled) {
            String lockType = lockManager.getLockType();
            boolean isPasswordLocked = "password".equals(lockType);
            if (!isPasswordLocked) {
                // عرض تنبيه لتفعيل كلمة المرور أولاً
                addBioMetric = true;
                showEnableBiometricWarningDialog();
            } else {
                // إذا كلمة المرور مفعلة مسبقاً، فعّل البصمة
                setupBiometricAuthentication();
            }
        } else {
            if ("biometric".equals(lockManager.getLockType())) {
                lockManager.disableLock();
                Toast.makeText(this, getString(R.string.biometric_disabled), Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * ديالوج تحذير قبل إعداد البصمة
     */
    private void showEnableBiometricWarningDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.warning_title))
                .setMessage(getString(R.string.enable_password_first))
                .setPositiveButton(getString(R.string.ok), (dialog, which) -> {
                    showPasswordSetupDialog();
                })
                .setNegativeButton(getString(R.string.cancel), (dialog, which) -> {
                    dialog.dismiss();
                    // ممكن ترجّع السويتش لوضعه السابق (غير مفعّل)
                    if ("biometric".equals(lockManager.getLockType())) {
                        lockManager.disableLock();
                        Toast.makeText(this, getString(R.string.biometric_disabled), Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void handlePasswordLock(boolean isEnabled) {
        if (isEnabled) {
            showPasswordSetupDialog();
        } else {
            // لا يمكن إلغاء قفل كلمة المرور إذا كان قفل البصمة مفعلًا
            if ("biometric".equals(lockManager.getLockType())) {
                showCustomAlert(getString(R.string.handlePasswordLock_24), getString(R.string.handlePasswordLock_25), null);
//                binding.settingsRecyclerView.getAdapter().notifyDataSetChanged(); // إعادة المفتاح لوضعه
                return;
            }
            lockManager.disableLock();
            Toast.makeText(this, getString(R.string.handlePasswordLock_26), Toast.LENGTH_SHORT).show();
//            binding.settingsRecyclerView.getAdapter().notifyDataSetChanged();
        }
    }

    /**
     * ديالوج إعداد كلمة المرور
     */
    private void showPasswordSetupDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_password_setup, null);

        TextInputLayout passwordLayout = dialogView.findViewById(R.id.password_layout);
        TextInputLayout confirmPasswordLayout = dialogView.findViewById(R.id.confirm_password_layout);
        TextInputEditText passwordEdit = dialogView.findViewById(R.id.password_edit);
        passwordEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        TextInputEditText confirmPasswordEdit = dialogView.findViewById(R.id.confirm_password_edit);
        confirmPasswordEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btn_confirm);

        builder.setView(dialogView);
        android.app.AlertDialog dialog = builder.create();

        btnConfirm.setOnClickListener(v -> {
            String password = passwordEdit.getText().toString().trim();
            String confirmPassword = confirmPasswordEdit.getText().toString().trim();
            lockManager.setPasswordLock(false);
            passwordLayout.setError(null);
            confirmPasswordLayout.setError(null);

            if (password.isEmpty()) {
                passwordLayout.setError(getString(R.string.password_required));
                lockManager.disableLock();
                return;
            }

            if (!password.equals(confirmPassword)) {
                confirmPasswordLayout.setError(getString(R.string.password_not_match));
                return;
            }

            savePassword(password);
            if (addBioMetric) {
                // بعد الحفظ، شغّل البصمة
                setupBiometricAuthentication();
            }
            dialog.dismiss();


        });

        dialog.setOnCancelListener(dialogInterface -> {
            // ممكن هنا ترجّع سويتش القفل لوضعه السابق
            // passwordSwitch.setChecked(false);
        });

        dialog.show();
        AppFontHelper.applyFontToDialog(this, dialog);
    }

    private void updateLockSwitches(SwitchCompat passwordSwitch, SwitchCompat biometricSwitch) {
        String lockType = lockManager.getLockType();
        boolean isPasswordLocked = "password".equals(lockType);
        boolean isBiometricLocked = "biometric".equals(lockType);

        passwordSwitch.setChecked(isPasswordLocked || isBiometricLocked);
        passwordSwitch.setEnabled(!isBiometricLocked); // تعطيل مفتاح كلمة المرور عند تفعيل البصمة

        biometricSwitch.setChecked(isBiometricLocked);
    }

    private void checkLocalCurrency() {
        DaftreeRepository repository = new DaftreeRepository(getApplication());
        AppDatabase.databaseWriteExecutor.execute(() -> {
            String localCurrency = repository.getFirstCurrency();
            if (localCurrency != null) {
                runOnUiThread(() -> {
                    sharedPrefsManager.setLocalCurrency(localCurrency);
                });


            }
        });
    }

    private void savePassword(String password) {
        DaftreeRepository repository = new DaftreeRepository(getApplication());
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // 1. جلب المستخدم بشكل مباشر
            User user = repository.getUserDao().getUserProfileBlocking();
            if (user == null) {
                // في حالة نادرة جدًا أن المستخدم غير موجود، أنشئ واحدًا جديدًا
                user = new User();
                FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
                if (firebaseUser != null) {
                    user.setEmail(firebaseUser.getEmail());
                }
            }
            // 2. تحديث كلمة المرور
            user.setPassword(password);
            // 3. حفظ التغييرات
            repository.updateUserProfile(user);

            // 4. العودة للخيط الرئيسي لتحديث الواجهة وإظهار الرسائل
            runOnUiThread(() -> {
                lockManager.setPasswordLock(true);
                Toast.makeText(this, getString(R.string.password_saved_success), Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void handleIntentExtras() {
        if (getIntent().getBooleanExtra("SHOW_WELCOME_BANNER", false)) {
//            showWelcomeBanner();
        }
//        if (getIntent().getBooleanExtra("REFRESH_DATA", false)) {
//            if (mainViewModel != null) {
//                mainViewModel.refreshData(); // إعادة تحميل البيانات
//            }
//        }
    }

    private void confirmLogout() {
        // التحقق من وجود بيانات غير متزامنة في خيط منفصل
        AppDatabase.databaseWriteExecutor.execute(() -> {
            boolean hasUnsyncedData = repository.hasUnsyncedData();

            // العودة للخيط الرئيسي لعرض الديالوج
            mainHandler.post(() -> {
                if (hasUnsyncedData) {
                    showUnsyncedDataWarningDialog();
                } else {
                    handleLogout();
                }
            });
        });
    }

    private void showUnsyncedDataWarningDialog() {
        repository.triggerSync();
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.showUnsyncedDataWarningDialog_27))
                .setMessage(getString(R.string.showUnsyncedDataWarningDialog_28))
                .setPositiveButton(getString(R.string.showUnsyncedDataWarningDialog_29), (dialog, which) -> handleLogout())
                .setNegativeButton(getString(R.string.showUnsyncedDataWarningDialog_30), null)
                .setIcon(R.drawable.ic_alert)
                .show();
    }

    private void handleLogout() {

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.handleLogout_31))
                .setMessage(getString(R.string.handleLogout_32))
                .setPositiveButton(getString(R.string.handleLogout_33), (dialog, which) -> {
                    googleAuthHelper.signOut(new GoogleAuthHelper.AuthCallback() {
                        @Override
                        public void onAccountInfoReceived(String email, String token, String provider) {
                        }

                        @Override
                        public void onSignInProgress(String message) {
                        }

                        @Override
                        public void onSignInSuccess(FirebaseUser user, AuthResult authResult) {
                        }

                        @Override
                        public void onSignInFailure(String error) { /* Not used */ }

                        @Override
                        public void onSignOutSuccess() {
                            // سيتم استدعاء onSignedOut() تلقائيًا من المستمع
                            performLogout();
                        }
                    });
                })
                .setNegativeButton(getString(R.string.handleLogout_34), null)
                .show();
    }


    private void styleSearchView(EditText searchView) {
        // الوصول إلى أيقونة البحث
        ImageView searchIcon = searchView.findViewById(androidx.appcompat.R.id.search_mag_icon);
        searchIcon.setColorFilter(Color.WHITE);
        // الوصول إلى زر الإغلاق
        ImageView closeButton = searchView.findViewById(androidx.appcompat.R.id.search_close_btn);
        closeButton.setColorFilter(Color.WHITE);
        // الوصول إلى حقل النص
        EditText searchText = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
        searchText.setTextColor(Color.WHITE);
        searchText.setHintTextColor(Color.LTGRAY);
    }

    private void setupViewPager() {
        accountTypeViewModel.getAllAccountTypes().observe(this, types -> {
            if (types == null) return;
            this.accountTypesList = types;

            pagerAdapter = new AccountTypesPagerAdapter(MainActivity.this, accountTypesList);
            binding.viewPager.setSaveEnabled(false);
            binding.viewPager.setAdapter(pagerAdapter);

            binding.viewPager.setCurrentItem(1, false);

            binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    super.onPageSelected(position);
                    int realPosition = pagerAdapter.getRealPosition(position);
                    updateToolbarTitle(realPosition);

                    // **هذا هو الكود الذي يفعّل الفلتر بشكل صحيح الآن**
                    if (realPosition == 0) {
                        mainViewModel.setFilter(null); // فلتر getString(R.string.setupViewPager_35)
                    } else {
                        if (accountTypesList != null && !accountTypesList.isEmpty() && realPosition - 1 < accountTypesList.size()) {
                            String firestoreId = accountTypesList.get(realPosition - 1).getFirestoreId();
                            mainViewModel.setFilter(firestoreId);
                        }
                    }
                }

                @Override
                public void onPageScrollStateChanged(int state) {
                    super.onPageScrollStateChanged(state);
                    if (state == ViewPager2.SCROLL_STATE_IDLE) {
                        int currentItem = binding.viewPager.getCurrentItem();
                        int itemCount = pagerAdapter.getItemCount();

                        // إذا كنا في الصفحة الإضافية الأولى (الموضع 0)، ننتقل إلى الصفحة قبل الأخيرة الحقيقية
                        if (currentItem == 0) {
                            isProgrammaticScroll = true;
                            binding.viewPager.setCurrentItem(itemCount - 2, false);
                        }
                        // إذا كنا في الصفحة الإضافية الأخيرة (الموضع itemCount - 1)، ننتقل إلى الصفحة الثانية (الموضع 1)
                        else if (currentItem == itemCount - 1) {
                            isProgrammaticScroll = true;
                            binding.viewPager.setCurrentItem(1, false);
                        }
                    }
                }
            });
        });
    }

    String accountType;

    private void updateToolbarTitle(int realPosition) {
//        if (realPosition == 0) {
//            binding.tvScreenTitle.setText(getString(R.string.filter_all));
//            accountType = getString(R.string.filter_all);
//        } else {
//            binding.tvScreenTitle.setText(accountTypesList.get(realPosition - 1).name);
//            accountType = accountTypesList.get(realPosition - 1).name.trim();
//        }
        String title;
        if (realPosition == 0) {
            title = getString(R.string.filter_all);
            accountType = getString(R.string.filter_all);
        } else {
            title = accountTypesList.get(realPosition - 1).name;
            accountType = title.trim();
        }
//        binding.tvScreenTitle.setText(title);
//        float defaultSize = binding.tvScreenTitle.getTextSize() / getResources().getDisplayMetrics().scaledDensity;
        TextViewUtils.adjustTextSizeToFit(binding.tvScreenTitle, title, 18f, 14f);
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
                tvMessage.setText((message));

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

    private void startRestoreProcess(Uri uri) {
        try {
            String fileName = getFileName(uri);
            if (fileName != null) {
                String extension = getFileExtension(fileName).toLowerCase();

                // التحقق من الامتدادات المدعومة
                if (extension.equals("db") ||
                        extension.equals("p") ||
                        extension.equals("b")) {
                    showProgressDialog(getString(R.string.sync_start));
                    RestoreHelper restoreHelper = new RestoreHelper(this);
                    restoreHelper.importDatabase(uri, new RestoreHelper.RestoreListener() {
                        @Override
                        public void onRestoreSuccess(int accountsImported, int transactionsImported) {
                            hideProgressDialog();
//                String message = "تم بنجاح استيراد " + accountsImported + " حساب و " + transactionsImported + " عملية.";
                            @SuppressLint("StringFormatMatches") String message = getString(R.string.sync_finish, accountsImported, transactionsImported);
                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                            // تشغيل المزامنة لرفع البيانات الجديدة
                            if (!isGuest) {
                                repository.triggerSync();
                            }
                        }

                        @Override
                        public void onRestoreError(String error) {
                            hideProgressDialog();
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle(getString(R.string.startRestoreProcess_39))
                                    .setMessage(error)
                                    .setPositiveButton(getString(R.string.startRestoreProcess_40), null)
                                    .show();
                        }
                    });

                } else {
                    // عرض رسالة خطأ للمستخدم
                    Toast.makeText(this, getString(R.string.startRestoreProcess_41), Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    // دالة مساعدة للحصول على امتداد الملف
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot != -1 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1);
        }
        return "";
    }

    // دالة مساعدة للحصول على اسم الملف من URI
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (displayNameIndex != -1) {
                        result = cursor.getString(displayNameIndex);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
    private void showReportsDialogFromToolbar() {
        if (!licenseManager.canCreateTransaction() && !licenceData.isPremium()) {
            showExpiryDialog(getString(R.string.plan_expired_title), getString(R.string.plan_expired_message));
            return;
        }

        if (isBudgetTabActive) {
            String ym = budgetViewModel != null
                    ? budgetViewModel.getCurrentYearMonthValue()
                    : BudgetUtils.getCurrentYearMonth();
            String budgetCurrencyName = budgetViewModel != null
                    ? budgetViewModel.getCurrentCurrencyNameValue()
                    : "";
            if (budgetCurrencyName == null || budgetCurrencyName.trim().isEmpty()) {
                budgetCurrencyName = binding.currencyTextView.getText() != null
                        ? binding.currencyTextView.getText().toString().trim() : "";
            }
            ReportsBottomSheet budgetSheet = ReportsBottomSheet.newInstanceForBudget(ym, budgetCurrencyName);
            budgetSheet.show(getSupportFragmentManager(), "ReportsSheetBudget");
            return;
        }

        String currency = binding.currencyTextView.getText() != null
                ? binding.currencyTextView.getText().toString().trim() : "";
        String accountTypeName = null;
        String accountTypeId   = null;
        boolean isAllAccounts  = true;
        String allFilterLabel  = getString(R.string.filter_all).trim();

        // 1. الفحص الأول: من موضع ViewPager2 الفعلي
        if (pagerAdapter != null && accountTypesList != null && !accountTypesList.isEmpty()) {
            int realPosition = pagerAdapter.getRealPosition(binding.viewPager.getCurrentItem());
            if (realPosition > 0 && (realPosition - 1) < accountTypesList.size()) {
                AccountType currentType = accountTypesList.get(realPosition - 1);
                if (currentType != null) {
                    accountTypeName = currentType.getName() != null ? currentType.getName().trim() : currentType.name;
                    accountTypeId   = currentType.getFirestoreId();
                    isAllAccounts   = false;
                }
            }
        }

        // 2. الفحص الثاني: من الفلتر النشط في MainViewModel
        if (isAllAccounts && mainViewModel != null) {
            String filter = mainViewModel.getFilter().getValue();
            if (filter != null && !filter.trim().isEmpty() && !filter.trim().equals(allFilterLabel)) {
                AccountType matched = findAccountTypeByFilter(filter.trim());
                if (matched != null) {
                    accountTypeName = matched.getName() != null ? matched.getName().trim() : matched.name;
                    accountTypeId   = matched.getFirestoreId();
                } else {
                    accountTypeName = filter.trim();
                }
                isAllAccounts = false;
            }
        }

        // 3. الفحص الثالث: من متغير accountType وعنوان الشاشة tvScreenTitle
        if (isAllAccounts) {
            String currentTitle = (accountType != null && !accountType.trim().isEmpty())
                    ? accountType.trim()
                    : (binding.tvScreenTitle.getText() != null ? binding.tvScreenTitle.getText().toString().trim() : "");
            if (!currentTitle.isEmpty() && !currentTitle.equals(allFilterLabel)) {
                AccountType matched = findAccountTypeByFilter(currentTitle);
                if (matched != null) {
                    accountTypeName = matched.getName() != null ? matched.getName().trim() : matched.name;
                    accountTypeId   = matched.getFirestoreId();
                } else {
                    accountTypeName = currentTitle;
                }
                isAllAccounts = false;
            }
        }

        ReportsBottomSheet reportsSheet = ReportsBottomSheet.newInstanceFiltered(
                accountTypeName, accountTypeId, currency, isAllAccounts);
        reportsSheet.show(getSupportFragmentManager(), "ReportsSheetFiltered");
    }

    private AccountType findAccountTypeByFilter(String filter) {
        if (accountTypesList == null || filter == null) return null;
        String cleanFilter = filter.trim();
        for (AccountType t : accountTypesList) {
            if (t == null) continue;
            if (t.getFirestoreId() != null && cleanFilter.equals(t.getFirestoreId().trim())) return t;
            String tName = t.getName() != null ? t.getName().trim() : (t.name != null ? t.name.trim() : "");
            if (cleanFilter.equalsIgnoreCase(tName)) return t;
        }
        return null;
    }



    // دالة مساعدة لجلب اسم النوع (أضفها في MainActivity)
    private String getAccountTypeNameById(String firestoreId) {
        if (accountTypesList == null) return "";
        for (AccountType type : accountTypesList) {
            if (type.getFirestoreId().equals(firestoreId)) return type.getName();
        }
        return "";
    }

    @Override
    public void onAccountClicked(Account account, String currency) {
        Intent intent = new Intent(this, AccountDetailsActivity.class);
        intent.putExtra("ACCOUNT_ID", account.getId());
        intent.putExtra("ACCOUNT_NAME", account.getAccountName());
        intent.putExtra("CURRENCY", currency);
        startActivity(intent);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        if (requestCode == STORAGE_PERMISSION_CODE) {
//            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                backupDatabase();
//            } else {
//                Toast.makeText(this, "تم رفض الإذن، لا يمكن إكمال النسخ الاحتياطي.", Toast.LENGTH_SHORT).show();
//            }
//        }
        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "تم منح صلاحية الإشعارات.");
            } else {
                Log.w(TAG, "تم رفض صلاحية الإشعارات من المستخدم.");
                // يمكنك إظهار Toast أو Snackbar هنا لإبلاغه.
            }
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {

        int id = item.getItemId();
        if (id == R.id.nav_new_bill) {
//          Toast.makeText(this,  item.getTitle() + " قيد التطوير للاصدار القادم", Toast.LENGTH_SHORT).show();
            String currency = binding.currencyTextView.getText().toString();
            InvoiceDialog dialog = InvoiceDialog.newInstanceForCreate(currency);
            dialog.show(getSupportFragmentManager(), "InvoiceDialog");
            drawerLayout.closeDrawer(GravityCompat.START);
        } else if (id == R.id.nav_recurring_operations) {
            startActivity(new Intent(this, RecurringOperationsActivity.class));
            drawerLayout.closeDrawer(GravityCompat.START);
        } else if (id == R.id.nav_linked_accounts) {
            if (!isUserAdded) {
//                DialogHelper.show(this,getString(R.string.login_1), DialogHelper.FeedbackType.WARNING, null);
                notSigned();
            } else if (licenceData.isLinkedAccount()) {

                Intent intent = new Intent(this, DebtActivationActivity.class);
                intent.putExtra("editeData", true);
                intent.putExtra("fullName", fullName);
                intent.putExtra("phoneNumber", phoneNumber);
                startActivity(intent);
            }
        } else if (id == R.id.nav_purchase_app) {
            handlePurchaseApp();

        } else if (id == R.id.nav_test_tour) {
//            checkAndUpdateExpiredSubscriptions();
//            finalFixFirstSubscriptionDate(this, null);
        } else if (id == R.id.nav_watch_ad) {
            watchAd();
        } else if (id == R.id.nav_invite_friend) {
            if (!isUserAdded) {
                // DialogHelper.show(this,getString(R.string.login_1), DialogHelper.FeedbackType.WARNING, null);
                notSigned();
            } else {
                referralManager.generateAndShareReferralLink(FirebaseAuth.getInstance().getCurrentUser());
            }
        } else if (id == R.id.nav_manage_devices) {
            showDeviceManagementScreen();
        } else if (id == R.id.nav_profile) {
            startActivity(new Intent(this, ProfileActivity.class));
        } else if (id == R.id.nav_currencies) {
            startActivity(new Intent(this, CurrenciesActivity.class));
        } else if (id == R.id.nav_account_types) {
            startActivity(new Intent(this, AccountsTypeActivity.class));
        } else if (id == R.id.nav_reports) {
//            ReportsDialog reportsDialog = ReportsDialog.newInstanceGeneral();
//            reportsDialog.show(getSupportFragmentManager(), "ReportsDialogGeneral");
            showReportsDialogFromToolbar();

        } else if (id == R.id.nav_change_language) {
            showLanguageSelectionDialog();

        } else if (id == R.id.nav_update_app) {

            checkForAppUpdate(true);
            // updateApp();
        } else if (id == R.id.nav_rate_app) {
            rateApp();
        } else if (id == R.id.nav_privacy_policy) {
            String updateUrl = "https://hpp-daftree.web.app/privacy.html";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl));
            startActivity(intent);
        } else if (id == R.id.nav_terms_cond) {
            String updateUrl = "https://hpp-daftree.web.app/terms.html";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl));
            startActivity(intent);
        } else if (id == R.id.nav_restore_data) {
//            openDocumentLauncher.launch(new String[]{"*/*"}); // يمكن استخدام "application/x-sqlite3" لتحديد النوع
            openBackupFile();

        } else if (id == R.id.nav_logout) {
            confirmLogout();
        } else if (id == R.id.nav_login) {
            removeAllObservers();
            // إيقاف أي مزامنة جارية
            FirestoreSyncManager.getInstance().stopListening();

            licenseManager.stopAndResetLicenseListener();

            // إعادة تعيين حالة البيانات
            isDataLoaded = false;
            isSyncCompleted = false;
            isSyncStop = false;

//            Intent intent = new Intent(this, LoginActivity.class);
//            intent.putExtra("registerGuest", true);
//            startActivityForResult(intent, LOGIN_REQUEST_CODE);
//            startActivity(intent);
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.putExtra("registerGuest", true);
            loginLauncher.launch(intent);
            finish();
        } else if (id == R.id.nav_contact_support) {
            startActivity(new Intent(this, ContactActivity.class));
        } else if (id == R.id.nav_backup_data) {
//            checkStoragePermissionAndBackup();
            createBackupFile();
        } else if (id == R.id.nav_help) {
            String updateUrl = "https://hpp-daftree.web.app/app_info.html";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl));
            startActivity(intent);
        } else if (id == R.id.nav_exit) {
//            finish(); // إغلاق التطبيق
            finishAffinity();
        }
//        else {
//            // تعامل مع باقي العناصر
//            Toast.makeText(this, "تم الضغط على: " + item.getTitle(), Toast.LENGTH_SHORT).show();
//        }

        drawerLayout.closeDrawer(GravityCompat.START); // إغلاق القائمة بعد الاختيار
        mainHandler.postDelayed(() -> {
            // إلغاء تحديد جميع العناصر في القائمة
            Menu menu = navigationView.getMenu();
            for (int i = 0; i < menu.size(); i++) {
                menu.getItem(i).setChecked(false);
            }
        }, 100); // تأخير بسيط لضمان إلغاء التحديد بعد الانتقال

        return true;
    }

    void notSigned() {

        DialogHelper.show(
                this,
                getString(R.string.must_be_user),
                DialogHelper.FeedbackType.WARNING,
                () -> {
                    removeAllObservers();
                    // إيقاف أي مزامنة جارية
                    FirestoreSyncManager.getInstance().stopListening();
                    // إيقاف المستمع الخاص بالترخيص
                    licenseManager.stopAndResetLicenseListener();

                    // إعادة تعيين حالة البيانات
                    isDataLoaded = false;
                    isSyncCompleted = false;
                    isSyncStop = false;

                    Intent loginIntent = new Intent(MainActivity.this, LoginActivity.class);
                    loginIntent.putExtra("registerGuest", true);
                    loginLauncher.launch(loginIntent);
                    finish();
                }
                , null);
    }


    /**
     * تعديل دالة onBackPressed لدعم التنقل في التوجيه
     */
//    @Override
//    public void onBackPressed() {
//        if (isSearchMode) {
//            toggleSearchMode(false);
//        } else if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
//            binding.drawerLayout.closeDrawer(GravityCompat.START);
//        } else {
//            super.onBackPressed();
//        }
//    }
    private void rateApp() {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, getString(R.string.no_internet), Toast.LENGTH_SHORT).show();

            return;
        }
        try {
            reviewManager = ReviewManagerFactory.create(this);
            com.google.android.gms.tasks.Task<ReviewInfo> request = reviewManager.requestReviewFlow();

            request.addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    // 2. نجاح الطلب -> تشغيل نافذة التقييم داخل التطبيق
                    ReviewInfo reviewInfo = task.getResult();
                    com.google.android.gms.tasks.Task<Void> flow = reviewManager.launchReviewFlow(this, reviewInfo);

                    flow.addOnCompleteListener(reviewFlowTask -> {

                    });
                } else {
                    // فشل في التشغيل الداخلي (ربما بسبب خدمات جوجل) -> افتح المتجر كخطة بديلة
                    launchGooglePlayFallback();
                }
            });

        } catch (ActivityNotFoundException e) {
            launchGooglePlayFallback();
        }
    }

    private void removeAllObservers() {
        try {
            // إزالة مراقب بيانات المستخدم
            if (profileViewModel != null && profileViewModel.getUserProfile().hasObservers()) {
                profileViewModel.getUserProfile().removeObservers(this);
            }

//            // إزالة مراقب اللغة
//            if (languageViewModel != null && languageViewModel.getSelectedLanguage().hasObservers()) {
//                languageViewModel.getSelectedLanguage().removeObservers(this);
//            }

            // إزالة مراقب العملات
            if (currencyViewModel != null && currencyViewModel.getAllCurrencies().hasObservers()) {
                currencyViewModel.getAllCurrencies().removeObservers(this);
            }

            // إزالة مراقب أنواع الحسابات
            if (accountTypeViewModel != null && accountTypeViewModel.getAllAccountTypes().hasObservers()) {
                accountTypeViewModel.getAllAccountTypes().removeObservers(this);
            }

            // إزالة مراقبي MainViewModel
            if (mainViewModel != null) {
                if (mainViewModel.getAccounts().hasObservers()) {
                    mainViewModel.getAccounts().removeObservers(this);
                }
                if (mainViewModel.getCurrenciesWithTransactions().hasObservers()) {
                    mainViewModel.getCurrenciesWithTransactions().removeObservers(this);
                }
                if (mainViewModel.getFilter().hasObservers()) {
                    mainViewModel.getFilter().removeObservers(this);
                }
            }

            // إزالة مراقب المزامنة
            if (syncCompletionLiveData.hasObservers()) {
                syncCompletionLiveData.removeObservers(this);
            }

            Log.d(TAG, "تم إزالة جميع المراقبات بنجاح");

        } catch (Exception e) {
            Log.e(TAG, "خطأ أثناء إزالة المراقبات: " + e.getMessage());
        }
    }

    @Override
    public void onAccountLongClicked(Account account) {
        showAccountOptionsDialog(account);

    }

    @Override
    public void onEditAccountClicked(Account account) {
        showAccountOptionsDialog(account);
    }

    private void showAccountOptionsDialog(final Account account) {
        if (account == null || isFinishing() || isDestroyed()) return;
        AccountBottomSheet sheet = AccountBottomSheet.newInstanceForViewOptions(account.getId());
        sheet.setAccountActionListener(new AccountBottomSheet.AccountActionListener() {
            @Override
            public void onAccountSaved(Account saved, boolean isNew) {
                if (mainViewModel != null) mainViewModel.refreshData();
            }
            @Override
            public void onAccountDeleted(Account deleted) {
                if (mainViewModel != null) mainViewModel.refreshData();
            }
        });
        sheet.show(getSupportFragmentManager(), "AccountBottomSheetOptions");
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (isGuest) {
            if (mainViewModel != null) {
                mainViewModel.refreshData();
            }
            return;
        }

        setupSmartLicense();
        if (isNetworkAvailable()) {


        }
        if (mainViewModel != null) {
            mainViewModel.refreshData();
        }
    }

    private void attemptSilentLogin() {
        // 1. منع الانهيار: التحقق من أن الواجهة لا تزال نشطة
        if (isFinishing() || isDestroyed()) return;

        // 2. إذا نجح تسجيل الدخول سابقاً، لا داعي للمحاولة مرة أخرى
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            Log.d(TAG, "Silent Login: المستخدم متصل بالفعل.");
            stopRetryLoop(); // إيقاف أي محاولات مجدولة
            return;
        }

        Log.d(TAG, "Silent Login: بدء محاولة الدخول (ستتم إعادة المحاولة كل 3 دقائق في حال الفشل)");

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(this, gso);

        // محاولة الدخول الصامت عبر جوجل
        googleSignInClient.silentSignIn().addOnSuccessListener(this, googleSignInAccount -> {
            // نجح جوجل، الآن نربط بـ Firebase
            AuthCredential credential = GoogleAuthProvider.getCredential(googleSignInAccount.getIdToken(), null);

            FirebaseAuth.getInstance().signInWithCredential(credential)
                    .addOnSuccessListener(authResult -> {
                        // ✅ نجاح كامل: توقف عن إعادة المحاولة فوراً
                        Log.d(TAG, "✅ نجح الاتصال بـ Firebase، توقف حلقة إعادة المحاولة.");
                        stopRetryLoop();

                        currentUID = FirebaseAuth.getInstance().getCurrentUser().getUid();
                        Bundle transactionParams = new Bundle();
                        transactionParams.putString("owerUID", currentUID);
                        transactionParams.putString("attemptSilentLogin", "محاولة اعادة تسجيل الدخول الصامت");
                        mFirebaseAnalytics.logEvent("log_attemptSilentLogin", transactionParams);
                        // تم استعادة كائن FirebaseUser بنجاح!
                        Log.d(TAG, "تمت استعادة الاتصال بـ Firebase بنجاح");
                        licenceData.setUserUID(authResult.getUser().getUid());


                        DaftreeRepository repo = new DaftreeRepository(getApplication());
                        repo.setUserUID(FirebaseAuth.getInstance().getUid());
                        FirestoreSyncManager.getInstance().startListening(repo, this, repo::triggerSync);

                    })
                    .addOnFailureListener(e -> {
                        // ❌ فشل ربط Firebase (مشكلة سيرفر أو نت)
                        Log.e(TAG, "❌ فشل Firebase: " + e.getMessage());
                        scheduleNextRetry();
                    });

        }).addOnFailureListener(e -> {
            // ❌ فشل جوجل (غالباً بسبب عدم توفر الإنترنت أو انتهاء الجلسة)
            Log.e(TAG, "❌ فشل الدخول الصامت لجوجل: " + e.getMessage());
            scheduleNextRetry();
        });
    }

    /**
     * جدولة المحاولة القادمة بعد 3 دقائق
     */
    private void scheduleNextRetry() {
        stopRetryLoop(); // تنظيف أي جدولة سابقة لمنع التكرار

        retryRunnable = this::attemptSilentLogin;

        Log.d(TAG, "🕒 سيتم إعادة المحاولة ذكياً بعد 3 دقائق...");
        silentLoginHandler.postDelayed(retryRunnable, RETRY_INTERVAL);
    }

    /**
     * إيقاف حلقة إعادة المحاولة عند النجاح أو الخروج
     */
    private void stopRetryLoop() {
        if (retryRunnable != null) {
            silentLoginHandler.removeCallbacks(retryRunnable);
            retryRunnable = null;
        }
    }

    private void addCurrentDeviceToFirestore(String userId, String deviceId, LicenseManager licenseManager) {
        Map<String, Object> deviceUpdate = new HashMap<>();
        DeviceInfo currentDevice = licenseManager.getCurrentDeviceInfo();
        deviceUpdate.put("devices." + deviceId, currentDevice.toMap());

        FirebaseFirestore.getInstance().collection("users").document(userId)
                .update(deviceUpdate)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Successfully added device from v1.0.1 upgrade to Firestore.");
                    // بعد الإضافة، يمكنك تحديث الواجهة أو إجراء أي عمل必要
                    checkDeviceAuthorization(); // إعادة فحص الترخيص لتحديث الحالة
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to add device to Firestore", e);
                });
    }

    private void checkUserUpgrare() {
        verifyDeviceAuthorization();

        DaftreeRepository repo = new DaftreeRepository(getApplication());
        repo.setUserUID(AppAuth.getUserID(this));
        FirestoreSyncManager.getInstance().startListening(repo, this, () -> {
            repo.triggerSync();
        });
    }

    private void performDeviceLicenseCheck(FirebaseUser user) {
        if (user == null) {
            Log.w(TAG, "تخطي فحص الترخيص: كائن المستخدم فارغ (ربما يعمل في الوضع المحلي)");
            return;
        }
        if (versionManager.first_upgrade()) {

            Log.d(TAG, "بدء فحص ترخيص الجهاز للمستخدم: " + user.getEmail());

            // ✅ التحقق أولاً من وجود المستخدم في Firestore
            FirebaseFirestore firestore = FirebaseFirestore.getInstance();
            DocumentReference userDocRef = firestore.collection("users").document(currentUID);
            VersionManager versionManager = new VersionManager(this);

            userDocRef.get().addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    Log.e(TAG, "فشل الوصول لمستند المستخدم: " + task.getException());
                    SnackbarHelper.showSnackbar(binding.getRoot(), getString(R.string.error_access_user_doc), SnackbarHelper.SnackbarType.ERROR);
                    return;
                }

                if (!task.getResult().exists()) {
                    if ((!versionManager.getFirestoreUser_isAdded())) {
                        createNewUser(userDocRef, user);
                        new VersionManager(this).setFirst_upgrade(false);
                        return;
                    } else {
                        checkUserUpgrare();
                    }
                    return;
                }
            });
            checkUserUpgrare();
        } else {
            checkUserUpgrare();
        }
    }

    private void createNewUser(DocumentReference userRef, FirebaseUser firebaseUser) {
        if (firebaseUser == null) return;
        try {
            LiveData<User> userLiveData = repository.getUserProfile();
            userLiveData.observeForever(new Observer<User>() {
                @Override
                public void onChanged(User localUser) {
                    userLiveData.removeObserver(this);
                    User newUser = new User();
                    newUser.setOwnerUID(currentUID);
                    newUser.setEmail(firebaseUser.getEmail());
                    // Use local data if available, otherwise use Firebase data
                    if (localUser != null) {
                        newUser.setName(localUser.getName());
                        newUser.setAddress(localUser.getAddress());
                        newUser.setCompany(localUser.getCompany());
                        newUser.setPhone(localUser.getPhone());
                    } else {
                        newUser.setName(firebaseUser.getDisplayName());
                        // Set default values for other fields
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

                    userRef.set(newUser).addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
//                           repository.updateUser(newUser);
//                           referralManager.applyReferralRewardIfAvailable(firebaseUser.getUid());
                            checkUserUpgrare();

                        } else {

                        }
                    });
                }
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "createNewUserData Error: " + e);
        }
    }

    private void verifyDeviceAuthorization() {
        if (!isNetworkAvailable()) return; // لا تقم بالفحص بدون انترنت

        licenseManager.checkLicense().thenAccept(result -> {
            runOnUiThread(() -> {
                if (result.isSuccess() && result.getUser() != null) {
                    // التحقق مما إذا كان ID الجهاز الحالي موجود في القائمة القادمة من Firestore
                    if (!result.getUser().getDevices().containsKey(licenseManager.getDeviceId())) {
                        forceSignOutAndShowAlert();
                    }
                }

            });
        });
    }

    @Override
    protected void onDestroy() {
        stopRetryLoop();
        super.onDestroy();
        // اختياري ولكن جيد: التأكد من إيقاف المستمع عند تدمير الـ Activity الرئيسية
        // هذا يمنع أي تسريب في حال لم يقم المستخدم بتسجيل الخروج
        FirestoreSyncManager.getInstance().stopListening();

        licenseManager.stopAndResetLicenseListener();
        if (versionManager != null) {
            versionManager.shutdown();
        }
        if (billingClient != null) billingClient.endConnection();
        WorkManager.getInstance(this).cancelUniqueWork("UNIQUE_SYNC_WORK");
    }

    @Override
    protected void onResume() {
        super.onResume();
        // الحل هنا: اطلب من الـ ViewModel تحديث بياناته في كل مرة تعود فيها للشاشة
        if (sharedPrefsManager == null) {
            sharedPrefsManager = new SyncPreferences(this);
        }

        updateAllNavigationSwitches();
        updateNavigationMenuItems();
        if (licenceData.getTransactionsCount() > 100 && licenceData.getTransactionsCount() == 500) {
            SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
            boolean isRated = prefs.getBoolean("is_app_rated", false);

            if (isRated) return;
            View ratingCard = findViewById(R.id.layout_rating_card);
            if (ratingCard != null) {
                ratingCard.setVisibility(View.VISIBLE);
                setupRatingActions(ratingCard);
            }

        }
// التحقق من صلاحية المنبه الدقيق
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (!alarmManager.canScheduleExactAlarms()) {
                // توجيه المستخدم للإعدادات لمنح الإذن
                Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(intent);
            }
        }

        // تأكد من جدولة التنبيه دائماً عند فتح التطبيق
        DailyReminderScheduler.scheduleDailyReminder(this);
        if (mainViewModel != null) {
            mainViewModel.refreshData();
        }
        Log.e(TAG, " isGuest(): " + isGuest);
        if (!isGuest) {
            updateLinkedAccountsMenuItem();
            deleteGuestAccountsWithSameDevice();
        }
//        refreshNotificationUI();
    }

    private void updateLinkedAccountsMenuItem() {
        // الوصول للقائمة الجانبية (NavigationView)
        // binding.navView هو المعرف الافتراضي في مشاريع الـ Drawer
        Menu menu = binding.navView.getMenu();
        MenuItem item = menu.findItem(R.id.nav_linked_accounts);
        MenuItem itemMenu = menu.findItem(R.id.menu_linked_accounts);

        if (itemMenu != null) {
            itemMenu.setVisible(licenceData.isLinkedAccount());
        }

        boolean isFeatureEnabled = licenceData.isDebtFeatureEnabled();

        if (item != null) {
            if (licenceData.isLinkedAccount()) {
                // الحالة: مفعلة
                item.setTitle(getString(R.string.linked_account_edite2)); // أو getString(R.string.updateLinkedAccountsMenuItem_02)
            }
        }
    }

    boolean isDeviceAuthorized = false;

    private void checkDeviceAuthorization() {
        if (!isNetworkAvailable()) {
            Log.d("LicenseCheck", "لا يوجد اتصال بالإنترنت");
            return;
        }
        if (isGuest) return;
        checkUserisDeleted("checkDeviceAuthorization");
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String uid;
        if (currentUser != null) {
            uid = currentUser.getUid();
        } else {
            // 🔥 تعديل جوهري: إذا كان الأوث محجوب، استخدم الـ UID المحفوظ محلياً
            uid = prefs.getString("userUID", "");
            Log.i(TAG, "Auth failed (possibly blocked), using local UID: " + uid);
        }

        if (!isUserAdded) {
            forceSignOutAndShowAlert();
            return;
        }
        licenseManager.checkLicense().thenAccept(result -> {
            runOnUiThread(() -> {

                if (result.isSuccess()) {
                    if (!result.isCurrentDeviceAuthorized()) {
                        // الجهاز غير مرخص - إجبار التسجيل الخروج
                        Log.d("LicenseCheck", "الجهاز غير مرخص، يتم تسجيل الخروج");
                        googleLogoutForcs();
                        forceSignOutAndShowAlert();
                    } else {
                        // الجهاز مرخص - تحديث واجهة المستخدم
                        Log.d("LicenseCheck", "الجهاز مرخص");
                        currentUserData = result.getUser();
                        updateNavigationMenuItems();
                    }
                } else {
                    // فشل في التحقق من الترخيص
                    Log.e("LicenseCheck", "فشل التحقق من الترخيص: " + result.getMessage());
                    forceSignOutAndShowAlert();
                    Toast.makeText(this, getString(R.string.error_add_device), Toast.LENGTH_SHORT).show();
                }
            });
        }).exceptionally(e -> {
            Log.e("LicenseCheck", "خطأ أثناء التحقق من الترخيص: " + e.getMessage());
            runOnUiThread(() -> {
                Toast.makeText(this, getString(R.string.error), Toast.LENGTH_SHORT).show();
            });
            return null;
        });
    }

    private void googleLogoutForcs() {
        googleAuthHelper.signOut(new GoogleAuthHelper.AuthCallback() {
            @Override
            public void onAccountInfoReceived(String email, String token, String provider) {

            }

            @Override
            public void onSignInProgress(String message) {
            }

            @Override
            public void onSignInSuccess(FirebaseUser user, AuthResult authResult) {
            }

            @Override
            public void onSignInFailure(String error) { /* Not used */ }

            @Override
            public void onSignOutSuccess() {
                // سيتم استدعاء onSignedOut() تلقائيًا من المستمع
//            performLogout();
            }
        });
    }

    private boolean checkUserisDeleted(String checking) {
        AtomicBoolean isDeleted = new AtomicBoolean(false);
        if (!isNetworkAvailable()) {
            return isDeleted.get();
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user != null) {
            user.reload().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Log.e(TAG, " User Statues: isActivated  _" + checking);
                } else {
                    Log.e(TAG, " User Statues: isDeleted _" + checking);
                    // الحساب محذوف أو لم يعد صالحًا
                    FirebaseAuth.getInstance().signOut();
                    isDeleted.set(true);
                }
            });
        } else {
            Log.e(TAG, " User Statues: Unkown _" + checking);
            // لا يوجد مستخدم مسجل
            isDeleted.set(true);
        }
        return isDeleted.get();
    }

    private void performLogout() {
        // إيقاف المزامنة
        FirestoreSyncManager.getInstance().stopListening();
        // تسجيل الخروج من Firebase
        FirebaseAuth.getInstance().signOut();

        // مسح البيانات المحلية
        SharedPreferences prefs = getSharedPreferences("prefs_uid", MODE_PRIVATE);
        prefs.edit().clear().apply();
        sharedPrefsManager.setFirstSyncComplete(false);

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
        googleLogoutForcs();
        // التوجيه إلى شاشة تسجيل الدخول
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    boolean dialogeShow = false;

    private void forceSignOutAndShowAlert() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (dialogeShow) return;
        dialogeShow = true;
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.device_remove_tit))
                .setMessage(getString(R.string.device_remove)).setPositiveButton(getString(R.string.ok), (dialog, which) -> {
                    // تنفيذ عملية تسجيل الخروج الكاملة
                    performLogout();
                })
                .setCancelable(false)
                .setIcon(R.drawable.ic_alert)
                .show();
    }


    private void showDeviceManagementDialog1(User user, boolean isLimitExceeded) {
        if (getSupportFragmentManager().findFragmentByTag("DeviceManagementDialog") != null) {
            return; // لا تفتح الديالوج إذا كان مفتوحًا بالفعل
        }

        List<DeviceInfo> devices = new ArrayList<>(user.getDevices().values());
        DeviceManagementDialog dialog = DeviceManagementDialog.newInstance(devices, licenseManager, isLimitExceeded);
        //disableInteractiveElements();
        // تعيين المستمع لإعادة تمكين العناصر عند الإغلاق
        dialog.setOnDismissListener(new DeviceManagementDialog.OnDismissListener() {
            @Override
            public void onDismiss() {
                /*enableInteractiveElements();*/
            }
        });
        dialog.setDialogListener(new DeviceManagementDialog.DialogListener() {
            @Override
            public void onDeviceRemoved() {
                // بعد إزالة جهاز، أعد فحص الترخيص لتحديث حالة الواجهة
                showDeviceManagementScreen();
//                checkUserLicense();
            }

            @Override
            public void onDismissed() {
                Toast.makeText(MainActivity.this, getString(R.string.showDeviceManagementDialog_44), Toast.LENGTH_LONG).show();
            }
        });
        dialog.show(getSupportFragmentManager(), "DeviceManagementDialog");
    }

    private void showDeviceManagementDialog(List<DeviceInfo> devices, boolean isLimitExceeded) {
        // 1. التحقق من أن الديالوج ليس مفتوحاً بالفعل لمنع التكرار
        if (getSupportFragmentManager().findFragmentByTag("DeviceManagementDialog") != null) {
            return;
        }

        // 2. إنشاء نسخة الديالوج وتمرير القائمة الجاهزة مباشرة ومحرك التراخيص
        DeviceManagementDialog dialog = DeviceManagementDialog.newInstance(devices, licenseManager, isLimitExceeded);

        // تعيين المستمع لإعادة تمكين العناصر عند الإغلاق
        dialog.setOnDismissListener(new DeviceManagementDialog.OnDismissListener() {
            @Override
            public void onDismiss() {
                /*enableInteractiveElements();*/
            }
        });

        // تعيين مستمع الأحداث داخل الديالوج
        dialog.setDialogListener(new DeviceManagementDialog.DialogListener() {
            @Override
            public void onDeviceRemoved() {
                // بعد إزالة جهاز، نقوم بفتح شاشة الإدارة كما في منطقك القديم
                showDeviceManagementScreen();
            }

            @Override
            public void onDismissed() {
                Toast.makeText(MainActivity.this, getString(R.string.showDeviceManagementDialog_44), Toast.LENGTH_LONG).show();
            }
        });

        // 3. عرض الديالوج بأمان
        dialog.show(getSupportFragmentManager(), "DeviceManagementDialog");
    }

    private void updateNavigationHeader() {
        NavigationView navigationView = findViewById(R.id.nav_view);
        View headerView = navigationView.getHeaderView(0);
        TextView userEmailText = headerView.findViewById(R.id.textViewUserEmail);
        TextView licenseStatusText = headerView.findViewById(R.id.license_status);

        drawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {
            }

            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                // يتم استدعاؤها مباشرة عند فتح القائمة
                updateNavigationMenuItems();
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
            }

            @Override
            public void onDrawerStateChanged(int newState) {
            }
        });

        if (isUserAdded) {
            FirebaseUser user = googleAuthHelper.getCurrentUser();
//            userEmailText.setText(user.getEmail());
            licenseStatusText.setText(licenceData.isPremium() ? getString(R.string.premium_plan) : getString(R.string.free_plan));
            licenseStatusText.setTextColor(licenceData.isPremium() ? Color.GREEN : Color.YELLOW);
        } else {
//            userEmailText.setText("يرجى تسجيل الدخول");
            licenseStatusText.setText(getString(R.string.updateNavigationHeader_45));
            licenseStatusText.setTextColor(Color.WHITE);
        }
    }
private void handlePurchaseApp() {
        if (!isUserAdded) {
            notSigned();
            return;
        }

        // 1. التحقق من منح إذن الموقع الجغرافي
        boolean hasFineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarseLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (!hasFineLocation && !hasCoarseLocation) {
            openSubscriptionActivityDirect();
            return;
        }

        // 2. التحقق من أن خدمة تحديد الموقع (GPS / Network) مفعلة في الجهاز
        if (!isLocationServiceEnabled()) {
            openSubscriptionActivityDirect();
            return;
        }

        // 3. فحص الموقع الجغرافي الفعلي للمستخدم
        binding.progressBar.setVisibility(View.VISIBLE);
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (location != null) {
                        resolveCountryAndOpenPurchaseUI(location.getLatitude(), location.getLongitude());
                    } else {
                        // إذا لم يكن هناك موقع مخزن مسبقاً، نطلب موقعاً حالياً سريعاً مع مهلة 3 ثوانٍ
                        com.google.android.gms.tasks.CancellationTokenSource cts =
                                new com.google.android.gms.tasks.CancellationTokenSource();
                        mainHandler.postDelayed(cts::cancel, 3000);

                        fusedLocationClient.getCurrentLocation(
                                com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                                cts.getToken()
                        ).addOnSuccessListener(this, currentLoc -> {
                            if (isFinishing() || isDestroyed()) return;
                            if (currentLoc != null) {
                                resolveCountryAndOpenPurchaseUI(currentLoc.getLatitude(), currentLoc.getLongitude());
                            } else {
                                binding.progressBar.setVisibility(View.GONE);
                                openSubscriptionActivityDirect();
                            }
                        }).addOnFailureListener(e -> {
                            if (isFinishing() || isDestroyed()) return;
                            binding.progressBar.setVisibility(View.GONE);
                            openSubscriptionActivityDirect();
                        });
                    }
                })
                .addOnFailureListener(e -> {
                    if (isFinishing() || isDestroyed()) return;
                    binding.progressBar.setVisibility(View.GONE);
                    openSubscriptionActivityDirect();
                });
    }

    private boolean isLocationServiceEnabled() {
        android.location.LocationManager lm =
                (android.location.LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return false;
        try {
            return lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                    || lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {
            return false;
        }
    }

    private void resolveCountryAndOpenPurchaseUI(double latitude, double longitude) {
        new Thread(() -> {
            boolean isYemen = false;
            try {
                // فحص النطاق الجغرافي أولاً كاحتياط سريع، ثم التأكيد عبر Geocoder
                android.location.Geocoder geocoder = new android.location.Geocoder(this, Locale.US);
                List<android.location.Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    android.location.Address address = addresses.get(0);
                    String countryCode = address.getCountryCode();
                    String countryName = address.getCountryName();
                    if ("YE".equalsIgnoreCase(countryCode)
                            || (countryName != null && countryName.toLowerCase(Locale.US).contains("yemen"))) {
                        isYemen = true;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Geocoder check failed, falling back to SubscriptionActivity: " + e.getMessage());
            }

            final boolean showYemenSheet = isYemen;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                binding.progressBar.setVisibility(View.GONE);
                if (showYemenSheet) {
                    PurchaseBottomSheet bottomSheet = new PurchaseBottomSheet();
                    bottomSheet.show(getSupportFragmentManager(), "PurchaseBottomSheet");
                } else {
                    openSubscriptionActivityDirect();
                }
            });
        }).start();
    }

    private void openSubscriptionActivityDirect() {
        Intent subscribeIntent = new Intent(MainActivity.this, SubscriptionActivity.class);
        subscribeIntent.putExtra("purchase", true);
        subscribeLauncher.launch(subscribeIntent);
    }

    private void watchAd() {

        // 1. إظهار البروجرس
        binding.progressBar.setVisibility(View.VISIBLE);
        Handler timeoutHandler = new Handler(Looper.getMainLooper());
        Runnable timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                // إذا وصلنا هنا، يعني أن 10 ثوانٍ مرت ولم يظهر الإعلان
                binding.progressBar.setVisibility(View.GONE);
                DialogHelper.show(MainActivity.this,
                        getString(R.string.ad_not_ready_message),
                        DialogHelper.FeedbackType.WARNING_2, null);
                Log.e("AdsTimeout", "تم تجاوز المهلة الزمنية (10 ثوانٍ) دون استجابة من AdsManager");
            }
        };
        timeoutHandler.postDelayed(timeoutRunnable, 10000);
        // 2. طلب الإعلان
        adsManager.showRewardedAd(this, new AdsManager.AdListener() {
            @Override
            public void onAdLoaded() {
                timeoutHandler.removeCallbacks(timeoutRunnable);
                binding.progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onAdFailedToLoad(String error) {
                timeoutHandler.removeCallbacks(timeoutRunnable);
                binding.progressBar.setVisibility(View.GONE);
                // Toast.makeText(MainActivity.this, getString(R.string.setupListeners_02), Toast.LENGTH_LONG).show();
                DialogHelper.show(MainActivity.this, getString(R.string.setupListeners_02), DialogHelper.FeedbackType.ERROR, null);

            }

            @Override
            public void onPointsEarned(int amount) {
                updatePointsInFirestore(amount);
            }

            @Override
            public void onAdClosed() {
                timeoutHandler.removeCallbacks(timeoutRunnable);
            }
        });
    }

    private void updatePointsInFirestore(int points) {
        licenceData.setAdRewards(licenceData.getAdRewards() + points);

        isGuest = licenceData.isGuest();
        String userDoc = "", userUID;
        if (isGuest) {
            userUID = licenceData.guestUID();
            userDoc = "guests";
        } else {
            userUID = AppAuth.getUserID(this);
            userDoc = "users";
        }
        assert userUID != null;
        Map<String, Object> updates = new HashMap<>();
        updates.put("ad_rewards", FieldValue.increment(points));
        updates.put("lastModified", System.currentTimeMillis());
        FirebaseFirestore.getInstance().collection(userDoc).document(userUID)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, getString(R.string.updatePointsInFirestore_10) + points + getString(R.string.updatePointsInFirestore_11), Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(this, getString(R.string.updatePointsInFirestore_12), Toast.LENGTH_SHORT).show());
    }

    private void showDeviceManagementScreen() {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, getString(R.string.no_internet), Toast.LENGTH_SHORT).show();
            return;
        }
        licenseManager.checkLicense().thenAccept(result -> {
            runOnUiThread(() -> {
                if (result.isSuccess() && result.getUser() != null) {
                    // استدعاء الديالوج وتمرير حالة تجاوز الحد
                    showDeviceManagementDialog1(result.getUser(), result.isDeviceLimitExceeded());
                } else {
                    Toast.makeText(this, getString(R.string.fail_impoart_accounts), Toast.LENGTH_SHORT).show();
                }
                updateNavigationHeader();
                updateNavigationMenuItems();
            });
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

    @Override
    public void onSignedOut() {
        runOnUiThread(() -> {
            Toast.makeText(MainActivity.this, getString(R.string.logout_succes), Toast.LENGTH_SHORT).show();
            updateUiBasedOnLicense();
        });
    }

    private void updateUiBasedOnLicense() {
//        updateNavigationHeader();
//        updateNavigationMenuItems();
//        checkUserLicense(); // إعادة فحص الترخيص لتحديث حالة Premium
        showDeviceManagementScreen();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getExtras() != null) {
            String type = intent.getExtras().getString("type");
            if ("transaction_alert".equals(type)) {
                intent.removeExtra("type");
                Intent debtIntent = licenceData.isLinkedAccount()
                        ? new Intent(this, DebtSummaryActivity.class)
                        : new Intent(this, DebtActivationActivity.class);
                startActivity(debtIntent);
            } else if ("purchase".equals(type)) {
                intent.removeExtra("type");
                Intent subscribeIntent = new Intent(MainActivity.this, SubscriptionActivity.class);
                subscribeIntent.putExtra("purchase", true);
                subscribeLauncher.launch(subscribeIntent);
            } else if (type != null && !type.isEmpty()) {
                NotificationHelper.handleNotificationIntent(this, intent);
            }
        }

        // معالجة الروابط العميقة عندما يكون التطبيق مفتوحًا بالفعل
        Uri data = intent.getData();
        String referrerUid = null;

        if (data != null) {
            if ("daftree".equals(data.getScheme()) && "invite".equals(data.getHost())) {
                referrerUid = data.getQueryParameter("ref");
            } else if ("https".equals(data.getScheme()) && "hpp-daftree.web.app".equals(data.getHost())) {
                referrerUid = data.getQueryParameter("ref");
            }
        }

        if (referrerUid != null) {
            // Snackbar.make(binding.getRoot(), "أنت مسجل مسبقاً ولا يمكنك استخدام رابط الدعوة للحصول على النقاط.", Snackbar.LENGTH_LONG).show();
            SnackbarHelper.showSnackbar(binding.getRoot(), getString(R.string.referral_already_registered2), SnackbarHelper.SnackbarType.ERROR);
//            showAlreadyRegisteredMessage();
        }
    }

    private void setupSmartLicense() {
        // تأكد من تمرير الـ context إذا كان الـ getInstance يتطلبه، أو استخدم getInstance() مباشرة حسب تعريف الكلاس لديك
        LicenseManager.getInstance(this).startSmartLicenseListener(this, isGuest, new LicenseManager.SmartLicenseCallback() {

            @Override
            public void onInitialLoadComplete(boolean isExpired, boolean isAuthorized,
                                              boolean limitExceeded, List<DeviceInfo> devices) {
                runOnUiThread(() -> {

                    if (!isAuthorized) {
                        if (limitExceeded) {
                            // تفوقنا هنا على البروجارد بتمرير قائمة الأجهزة مباشرة للديالوج
                            showDeviceManagementDialog(devices, true);
                        } else {
                            if (licenceData.isPremium()) {
                                checkSubscriptionStatus();
                            }
                            showUnauthorizedDeviceDialog();
                        }
                    } else if (isExpired) {
                        downgradeUserToFree();
                    } else {
                        // إذا كان المستخدم مرخصاً ونسخته مدفوعة، نفحص حالة الاشتراك هنا أيضاً
                        if (licenceData.isPremium()) {
                            checkSubscriptionStatus();
                        }

                    }
                    if (isGuest) {
                        checkDeviceBanOnStart();
                    }
                    updateUIBasedOnLicense();
                });
            }

            @Override
            public void onDeviceRemoved() {
                runOnUiThread(() -> {
                    // استدعاء دالة التحقق عند إزالة الجهاز
                    showUnauthorizedDeviceDialog();
                });
            }
        });
    }

    private void checkAndFetchAppConfig() {
        if (!isNetworkAvailable()) return;
        long lastCheck = prefs.getLong("last_appConfig_time", 0);
        long currentTime = System.currentTimeMillis();
        boolean guestFstInstall = prefs.getBoolean("is_Guest_first_install", true);
        if (isGuest && !guestFstInstall) {
            return;
        }
        // إذا مر 24 ساعة أو أكثر
        if ((currentTime - lastCheck < TimeUnit.DAYS.toMillis(1)) && !licenceData.isUserAdmin()) {
            Log.d(TAG, "Last checkAndFetchAppConfig less than 24h");
            return;
        }
        Log.d(TAG, "checkAndFetchAppConfig last_appConfig_time: " + lastCheck);

        // 4. جلب البيانات من فايرستور (أول مرة أو للتحديث اليومي الخفي)
        FirebaseFirestore.getInstance().collection("app_config")
                .document("settings")
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && documentSnapshot.getData() != null) {
                        licenceData.saveDynamicAppConfig(documentSnapshot.getData());
                        prefs.edit().putLong("last_appConfig_time", currentTime).apply();
                        prefs.edit().putBoolean("is_Guest_first_install", false).apply();
                    }

                })
                .addOnFailureListener(e -> {
                });

    }

    private void updateUIBasedOnLicense() {
        SecureLicenseManager secureManager = SecureLicenseManager.getInstance(this);
        // قراءة الحالة من المخزن المحلي السريع (بدون إنترنت)
        boolean isPremium = secureManager.isPremium();
        boolean isDebtFeatureEnabled = secureManager.isDebtFeatureEnabled();
        updateAllNavigationSwitches();
        updateNavigationMenuItems();
        Log.d(TAG, "✅ تم تحديث واجهة التطبيق بنجاح بناءً على الرخصة المحلية.");
    }

    // 2. دالة تنبيه: الجهاز غير مرخص (ويجب منعه من الدخول)
    private void showUnauthorizedDeviceDialog() {
        if (isFinishing() && isDestroyed()) {
            return;
        } // تجنب الـ Crash إذا كانت الشاشة تغلق

        runOnUiThread(() -> {
            forceSignOutAndShowAlert();
        });
    }

    // 3. دالة تنبيه: تم تجاوز الحد الأقصى للأجهزة

    private void checkDeviceBanOnStart() {
        if (com.hpp.daftree.BuildConfig.DEBUG) return;
        deviceBanManager.checkDeviceBan(new DeviceBanManager.BanCheckListener() {

            @Override
            public void onCheckComplete(boolean isBanned, String reason) {
                runOnUiThread(() -> {
                    if (isBanned) {
                        Log.e(TAG, "الجهاز محظور: " + reason);
                        if (isDialogeShown) return;
                        showDeviceBanDialog(reason);
                    }
                });
            }

            @Override
            public void onCheckError(String error) {
                Log.e(TAG, "خطأ في فحص الحظر: " + error);
            }
        });
    }

    /**
     * ✅ عرض ديالوج الحظر
     */
    boolean isDialogeShown = false;

    private void showDeviceBanDialog(String banReason) {
        if (isFinishing() || isDestroyed()) return;
        isDialogeShown = true;
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.device_block_title))
                .setMessage(getString(R.string.device_block_message))
                .setPositiveButton(getString(R.string.exit), (dialog, which) -> {
                    isDialogeShown = false;
                    finishAffinity(); // إغلاق التطبيق completamente
                })
                .setNegativeButton(getString(R.string.contact_support), (dialog, which) -> {
                    isDialogeShown = false;
                    sendToWhatsApp();
                })
                .setCancelable(false)
                .setIcon(R.drawable.ic_warning)
                .show();
    }

    private void sendToWhatsApp() {


        String message = "\n\n" + getString(R.string.whatsapp_greeting) + "\n\n" +
                getString(R.string.whatsapp_app_tit) + getString(R.string.app_name) + "\n\n" +
                getString(R.string.whatsapp_request) + "\n\n" +
                getString(R.string.deviceId, licenseManager.getDeviceId()) + "\n\n" +
                getString(R.string.whatsapp_request_final) + "\n\n" +
                getString(R.string.whatsapp_thank_you);

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            // Replace with actual WhatsApp number
            intent.setData(Uri.parse("https://wa.me/967780405427?text=" + Uri.encode(message)));
            startActivity(intent);
            finishAffinity();
        } catch (Exception e) {
            Toast.makeText(this, R.string.whatsapp_not_found, Toast.LENGTH_SHORT).show();
        }
    }

    private void checkForAppUpdate(boolean manualCheck) {
        if (!isNetworkAvailable()) {
            return;
        }
        Log.d(TAG, "بدء فحص التحديثات...");
        Log.d(TAG, "الإصدار الحالي: " + versionManager.getCurrentVersionName());
        Log.d(TAG, "رقم البناء الحالي: " + versionManager.getCurrentVersionCode());
        logVersionInfo();

        versionManager.checkForUpdate(new VersionManager.UpdateListener() {
            @Override
            public void onUpdateAvailable(String latestVersion, String changelog, String downloadUrl, String source) {
                Log.d(TAG, "تم العثور على تحديث جديد: " + latestVersion + " من " + source);
                runOnUiThread(() -> {
                    try {
                        UpdateAppDialog updateDialog = new UpdateAppDialog(
                                MainActivity.this,
                                latestVersion,
                                changelog != null ? changelog : getString(R.string.update_available_message),
                                downloadUrl,
                                source
                        );
                        updateDialog.show();
                    } catch (Exception e) {
                        Log.e(TAG, "خطأ في عرض ديالوج التحديث: " + e.toString());
                    }
                });
            }

            @Override
            public void onNoUpdateAvailable() {
                Log.d(TAG, "لا توجد تحديثات جديدة");
                runOnUiThread(() -> {
                    if (manualCheck) {
                        Toast.makeText(MainActivity.this,
                                getString(R.string.last_update_using),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "خطأ في فحص التحديثات: " + error);
                runOnUiThread(() -> {
                    if (manualCheck) {
                        Toast.makeText(MainActivity.this,
                                getString(R.string.checkForAppUpdate_46) + error,
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        }, manualCheck);
    }

    private void logVersionInfo() {
        Log.d(TAG, "=== معلومات الإصدار ===");
        Log.d(TAG, "الإصدار الحالي: " + versionManager.getCurrentVersionName());
        Log.d(TAG, "رقم البناء: " + versionManager.getCurrentVersionCode());
        Log.d(TAG, "الإصدار السابق: " + versionManager.getPreviousVersion());
        Log.d(TAG, "رقم البناء السابق: " + versionManager.getLastKnownVersionCode());
        Log.d(TAG, "أول تشغيل: " + versionManager.isFirstLaunch());
        Log.d(TAG, "تحديث رئيسي: " + versionManager.isMajorUpdate());
        Log.d(TAG, "تحديث ثانوي: " + versionManager.isMinorUpdate());
        Log.d(TAG, "=========================");
    }

    private void createBackupFile() {
        Log.d(TAG, "بدء إنشاء نسخة احتياطية...");

        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/x-sqlite3");

            // التأكد من إضافة .db في اسم الملف
            String baseName = "Daftree_Backup_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String fileName = baseName + ".db";

            Log.d(TAG, "اسم ملف النسخة الاحتياطية: " + fileName);
            intent.putExtra(Intent.EXTRA_TITLE, fileName);

            backupLauncher.launch(intent);

        } catch (Exception e) {
            Log.e(TAG, "خطأ في إنشاء ملف النسخة الاحتياطية: " + e.getMessage());
            Toast.makeText(this, getString(R.string.createBackupFile_47), Toast.LENGTH_SHORT).show();
        }
    }

    private void openBackupFile() {
        Log.d(TAG, "فتح منتقي الملفات للاستيراد...");

        try {
            // فتح جميع أنواع الملفات المدعومة باستخدام OpenDocument
            String[] mimeTypes = {
                    "application/x-sqlite3",        // .db
                    "application/octet-stream",     // .p, .b
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // .xlsx, .xlcx
                    "application/vnd.ms-excel"      // .xls
            };

            openDocumentLauncher.launch(mimeTypes);
        } catch (Exception e) {
            Log.e(TAG, "خطأ في فتح منتقي الملفات: " + e.getMessage());
            Toast.makeText(this, getString(R.string.openBackupFile_48), Toast.LENGTH_SHORT).show();
        }
    }

    // ✅ دالة للتحقق ومزامنة الضيف عند توفر الإنترنت
    private void saveGuestToFirestore() {

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        Map<String, Object> guestDataMap = new HashMap<>();
        guestDataMap.put("ownerUID", guestUID.trim());
        guestDataMap.put("userType", "guest");
        guestDataMap.put("is_premium", false);
        guestDataMap.put("created_at", User.getCurrentLocalDateTime());
        guestDataMap.put("last_login", User.getCurrentLocalDateTime());
        guestDataMap.put("login_count", 1);
        guestDataMap.put("max_devices", 1);
        guestDataMap.put("transactions_count", licenceData.getTransactionsCount());
        guestDataMap.put("max_transactions", licenceData.getMaxTransactions());
        guestDataMap.put("ad_rewards", licenceData.getAdRewards());
        guestDataMap.put("referral_rewards", licenceData.getReferralRewards());
        guestDataMap.put("fcmToken", fcmToken);
        guestDataMap.put("lastModified", System.currentTimeMillis());
        DeviceInfo deviceInfo = licenseManager.getCurrentDeviceInfo();
        guestDataMap.put("deviceId", deviceInfo.getDeviceId()); // ✅ استخدام deviceId كحقل عادي
        guestDataMap.put("deviceName", deviceInfo.getDeviceName());
        guestDataMap.put("deviceModel", deviceInfo.getDeviceModel());
        firestore.collection("guests").document(guestUID.trim()).set(guestDataMap)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "تم إنشاء حساب الضيف في Firestore: " + guestUID);
                        new Thread(() -> {
                            try {
                                // حفظ في جدول users المحلي مع تمييزه كضيف

                                SharedPreferences.Editor editor = prefs.edit();
                                editor.putString("guest_state", "SAVE");
                                editor.apply();

                                Log.d(TAG, "تم إنشاء المستخدم الضيف في فايرستور: " + guestUID);
                            } catch (Exception e) {
                                Log.e(TAG, "خطأ في إنشاء الضيف في فايرستور: " + e.getMessage());
                            }
                        }).start();
                    } else {
                        Log.e(TAG, "فشل إضافة الضيف في Firestore، سيتم المزامنة لاحقاً: " + task.getException());

                    }
//                    .
//                    setupGuestData();

                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "فشل إضافة الضيف في Firestore: " + e.getMessage());

                });
    }

    private void updateGuestToFirestore() {
        if (!isNetworkAvailable()) return;
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        Map<String, Object> updates = new HashMap<>();
        updates.put("last_login", User.getCurrentLocalDateTime());
        updates.put("lastModified", System.currentTimeMillis());
        updates.put("login_count", FieldValue.increment(1));
        updates.put("app_Version", versionManager.getCurrentVersionName());
        updates.put("app_VersionLast", versionManager.getPreviousVersion());

        firestore.collection("guests").document(guestUID).update(updates)
                // firestore.collection("guests").document(guestUID.trim()).set(guestUser)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "تم تحديث حساب الضيف في Firestore: " + guestUID);

                    } else {
                        Log.e(TAG, "فشل تحديث حساب الضيف في Firestore، سيتم المزامنة لاحقاً: " + task.getException());
                        saveGuestToFirestore();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "فشل تحديث حساب الضيف في Firestore: " + e.getMessage());

                });
    }

    public void deleteGuestAccountsWithSameDevice() {
        if (licenceData.guestUID() == null
                || licenceData.guestUID().isEmpty()
                || licenceData.guestUID() == "") {
            return;
        }
        String currentDeviceId = licenseManager.getDeviceId();
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
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
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        WriteBatch batch = firestore.batch();

        for (DocumentSnapshot doc : documents) {
            batch.delete(doc.getReference());
        }

        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "تم حذف " + documents.size() + " حساب ضيف بنجاح");
                    licenceData.setGuestUID("");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "فشل في حذف حسابات الضيف: " + e.getMessage());
                });
    }

    private void setupCurrencyTextViewColorTransition() {
        // التحقق من أن هذا هو أول استخدام للمستخدم
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean isFirstTimeUser = prefs.getBoolean("first_time_currency", true);

        if (isFirstTimeUser && binding.currencyTextView != null) {
            Log.d(TAG, "إعداد تغيير لون نص العملة للمستخدم الجديد");

            // الحصول على الألوان من الموارد
            int colorPrimary = ContextCompat.getColor(this, R.color.colorPrimary);
            int redColor = Color.RED;

            // إذا كان لديك لون أحمر محدد في الموارد، يمكنك استخدامه:
            // int redColor = ContextCompat.getColor(this, R.color.red);

            // إنشاء ValueAnimator للتحويل بين الألوان
            ValueAnimator colorAnimator = ValueAnimator.ofObject(
                    new ArgbEvaluator(),
                    colorPrimary,
                    redColor,
                    colorPrimary
            );

            colorAnimator.setDuration(1500); // 1.5 ثانية للدورة الكاملة
            colorAnimator.setRepeatCount(ValueAnimator.INFINITE); // تكرار إلى ما لا نهاية
            colorAnimator.setRepeatMode(ValueAnimator.REVERSE); // ذهاب وإياب

            colorAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animator) {
                    int animatedValue = (int) animator.getAnimatedValue();
                    binding.currencyTextView.setTextColor(animatedValue);
                }
            });

            // بدء التحريك بعد تأخير قصير
            mainHandler.postDelayed(() -> {
                colorAnimator.start();

                // إيقاف التحريك بعد 10 ثوان إذا لم يتم النقر
                mainHandler.postDelayed(() -> {
                    if (colorAnimator.isRunning()) {
                        colorAnimator.cancel();
                        // إعادة تعيين اللون إلى الأصلي بعد التوقف
                        binding.currencyTextView.setTextColor(colorPrimary);
                        Log.d(TAG, "انتهى توقيت تغيير لون نص العملة");
                    }
                }, 10000); // 10 ثوان

            }, 2000); // بدء التحريك بعد ثانيتين
        }
    }

    /**
     * 🔥 إعداد توهج زر إضافة العملية للمستخدمين الجدد
     */
    private void setupAddTransactionButtonGlow() {
        // التحقق من أن هذا هو أول استخدام للمستخدم
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean isFirstTimeUser = prefs.getBoolean("first_transaction_button_glow", true);

        if (isFirstTimeUser && binding.addTransactionButton != null) {
            Log.d(TAG, "إعداد توهج زر إضافة العملية للمستخدم الجديد");

            // إنشاء ObjectAnimator للتوهج
            ObjectAnimator glowAnimator = ObjectAnimator.ofFloat(
                    binding.addTransactionButton,
                    "alpha", 1.0f, 0.6f, 1.0f
            );
            glowAnimator.setDuration(1000); // ثانية واحدة
            glowAnimator.setRepeatCount(ObjectAnimator.INFINITE); // تكرار إلى ما لا نهاية
            glowAnimator.setRepeatMode(ObjectAnimator.REVERSE); // ذهاب وإياب

            // بدء التوهج بعد تأخير قصير
            mainHandler.postDelayed(() -> {
                glowAnimator.start();

                // إيقاف التوهج عند النقر على الزر
                binding.addTransactionButton.setOnClickListener(v -> {
                    glowAnimator.cancel();

                    // حفظ حالة أن المستخدم استخدم الزر
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean("first_transaction_button_glow", false);
                    editor.apply();
                    if (!db.isOpen()) {
                        db = AppDatabase.getDatabase(MainActivity.this);
                    }
                    // تنفيذ الوظيفة الأساسية للزر الموحد
                    showQuickAddBottomSheet();
                });

                // إيقاف التوهج بعد 10 ثوان إذا لم يتم النقر
                mainHandler.postDelayed(() -> {
                    if (glowAnimator.isRunning()) {
                        glowAnimator.cancel();
                        Log.d(TAG, "انتهى توقيت توهج زر إضافة العملية");
                    }
                }, 10000); // 10 ثوان

            }, 2000); // بدء التوهج بعد ثانيتين
        }
    }


    /**
     * عرض تنبيه أن مكافآت الإحالة غير متاحة للمستخدمين الحاليين
     */
    private void showReferralNotAvailableDialog() {
        if (isFinishing() || isDestroyed()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.referral_not_available_title))
                .setMessage(getString(R.string.referral_not_available_message))
                .setPositiveButton(getString(R.string.ok), (dialog, which) -> {
                    // إزالة referrerUid وتابع كضيف
                    SharedPreferences prefs = getSharedPreferences("referral_prefs", MODE_PRIVATE);
                    prefs.edit().remove("referrer_uid").apply();
                })
                .setCancelable(false)
                .show();
    }

    /**
     * تنفيذ إجراءات الإحالة للحوار الجديد
     */
    @Override
    public void onRegisterClicked(String referrerUid) {
        Log.d("ReferralAction", "تسجيل مستخدم جديد مع إحالة: " + referrerUid);
        Intent loginIntent = new Intent(this, LoginActivity.class);
        loginIntent.putExtra("REFERRER_UID", referrerUid);
        loginIntent.putExtra("registerGuest", true);
        loginLauncher.launch(loginIntent);
    }

    @Override
    public void onCancel(String referrerUid) {
        Log.d("ReferralAction", "إلغاء الإحالة: " + referrerUid);
        SharedPreferences prefs = getSharedPreferences("referral_prefs", MODE_PRIVATE);
        prefs.edit().remove("referrer_uid").apply();
        this.referrerUid = "";
    }

    @Override
    public void onDismiss() {
        Log.d("ReferralAction", "إغلاق حوار مكافأة الإحالة");
        // في حالة إغلاق الحوار، تابع كضيف
        // 🔥 إزالة referrerUid للتأكد من عدم التداخل
        SharedPreferences prefs = getSharedPreferences("referral_prefs", MODE_PRIVATE);
        prefs.edit().remove("referrer_uid").apply();
        this.referrerUid = "";
    }

    private void updateRecyclerViewLayouts() {
        // تحديث الـ RecyclerView في الـ ViewPager الحالي
        if (binding.viewPager != null && binding.viewPager.getAdapter() != null) {
            int currentItem = binding.viewPager.getCurrentItem();

            // إعادة إنشاء الـ Adapter لتطبيق التغييرات
            Fragment currentFragment = getSupportFragmentManager()
                    .findFragmentByTag("f" + binding.viewPager.getCurrentItem());

            if (currentFragment instanceof AccountListFragment) {
                ((AccountListFragment) currentFragment).recreateLayoutManagerForMainActivity();
            }
        }
    }


    private void updateBottomCardLayout() {
        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        if (binding.bottomSummaryCard != null) {
            ViewGroup.LayoutParams params = binding.bottomSummaryCard.getLayoutParams();
            if (params instanceof ConstraintLayout.LayoutParams) {
                ConstraintLayout.LayoutParams constraintParams = (ConstraintLayout.LayoutParams) params;

                if (isLandscape) {
                    // تصميم مضغوط للوضع الأفقي
                    try {
                        constraintParams.height = getResources().getDimensionPixelSize(R.dimen.bottom_card_height);
                        binding.bottomSummaryCard.setCardElevation(getResources().getDimension(R.dimen.card_elevation_land));
                    } catch (Resources.NotFoundException e) {
                        // استخدام قيم افتراضية في حالة عدم وجود الأبعاد
                        constraintParams.height = (int) (80 * getResources().getDisplayMetrics().density);
                        binding.bottomSummaryCard.setCardElevation(6f);
                    }
                } else {
                    // تصميم عادي للوضع العمودي
                    constraintParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    try {
                        binding.bottomSummaryCard.setCardElevation(getResources().getDimension(R.dimen.card_elevation));
                    } catch (Resources.NotFoundException e) {
                        binding.bottomSummaryCard.setCardElevation(4f);
                    }
                }
                binding.bottomSummaryCard.setLayoutParams(constraintParams);
            }
        }
    }

    // استدعاء هذه الدالة في onCreate بعد setupViewPager
    private void setupOrientationAwareLayout() {
        updateBottomCardLayout();
        updateRecyclerViewLayouts();
    }

    private static final int PERMISSION_NOTIF_REQUEST = 101;

    private void requestAllNotificationPermissions() {

        // 1. طلب Permission الخاص بالإشعارات (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        PERMISSION_NOTIF_REQUEST
                );
                return; // ننتظر نتيجة الطلب قبل الانتقال للخطوة الثانية
            }
        }

        // 2. طلب تجاوز وضع عدم الإزعاج DND
//        requestDndWithDialog();
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
//            if (!nm.isNotificationPolicyAccessGranted()) {
//                Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
//                startActivity(intent);
//            }
//        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // تحديث تخطيط الـ RecyclerView في جميع الـ Fragments
        updateRecyclerViewLayouts();

        // تحديث تخطيط الكارد السفلي
        updateBottomCardLayout();
        int currentNightMode = newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;

        switch (currentNightMode) {
            case Configuration.UI_MODE_NIGHT_NO:
                // الوضع الفاتح
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                recreateIfNeeded();
                break;

            case Configuration.UI_MODE_NIGHT_YES:
                // الوضع الداكن
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                recreateIfNeeded();
                break;
        }
    }
// أضف هذه الدالة في MainActivity.java

    private void syncUserPhoneWithAuth() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String currentUid = AppAuth.getUserID(this);
        if (currentUid == null) return;

        String uid = AppAuth.getUserID(this);
        if (uid == null || uid.isEmpty()) return;
        if (!licenceData.isDebtFeatureEnabled()) return;
        String authPhoneNumber = licenceData.getCountryCode() + licenceData.getPhoneNumber(); // الرقم الدولي كاملاً مع +
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> updates = new HashMap<>();

        if (authPhoneNumber != null && !authPhoneNumber.isEmpty()) {
            // 1. استخدام PhoneNumberUtil لإزالة مفتاح الدولة بدقة
            try {
                PhoneNumberUtil phoneUtil = PhoneNumberUtil.createInstance(this);
                Phonenumber.PhoneNumber numberProto = phoneUtil.parse(authPhoneNumber, "ZZ");
                String nationalNumber = String.valueOf(numberProto.getNationalNumber());

                updates.put("phoneNumber", nationalNumber);
                // نحتفظ بالرقم الدولي الكامل أيضاً للرجوع إليه
                updates.put("fullAuthPhoneNumber", authPhoneNumber);
            } catch (Exception e) {
                // في حال فشل التحليل، نضع الرقم كما هو
                updates.put("phoneNumber", authPhoneNumber);
            }
        } else {
            // 2. إذا لم يكن هناك رقم هاتف في Auth (مثلاً سجل بجوجل أو إيميل)
            updates.put("phoneNumber", FieldValue.delete());
            updates.put("isDebtFeatureEnabled", FieldValue.delete());
        }

        // تحديث مستند المستخدم في users
        db.collection("users").document(uid)
                .update(updates)
                .addOnSuccessListener(aVoid -> Log.d("Sync", "تمت مزامنة بيانات الهاتف مع Auth بنجاح"))
                .addOnFailureListener(e -> {
                    // إذا كان المستند غير موجود أصلاً (مستخدم جديد) استخدم set بدلاً من update
                    db.collection("users").document(uid).set(updates, SetOptions.merge());
                });
    }

    // دالة مساعدة لإعادة إنشاء Activity عند الحاجة
    private void recreateIfNeeded() {
        // بعض الأجهزة لا تعيد التحديث تلقائياً عند الانتقال Dark → Light
        if (!isChangingConfigurations()) {
            recreate();
        }
    }

    private void setupRatingActions(View cardView) {
        reviewManager = ReviewManagerFactory.create(this);

        cardView.findViewById(R.id.btn_rate_now).setOnClickListener(v -> {
            // 1. طلب تدفق التقييم (Request Review Flow)
            com.google.android.gms.tasks.Task<ReviewInfo> request = reviewManager.requestReviewFlow();

            request.addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    // 2. نجاح الطلب -> تشغيل نافذة التقييم داخل التطبيق
                    ReviewInfo reviewInfo = task.getResult();
                    com.google.android.gms.tasks.Task<Void> flow = reviewManager.launchReviewFlow(this, reviewInfo);

                    flow.addOnCompleteListener(reviewFlowTask -> {
                        // 3. انتهاء العملية بنجاح (المستخدم أكمل الخطوات أو أغلقها)
                        grantRewardAndHide(cardView);
                    });
                } else {
                    // فشل في التشغيل الداخلي (ربما بسبب خدمات جوجل) -> افتح المتجر كخطة بديلة
                    launchGooglePlayFallback();
                    grantRewardAndHide(cardView);
                }
            });
        });

        cardView.findViewById(R.id.btn_remind_later).setOnClickListener(v -> {
            cardView.setVisibility(View.GONE);
        });
    }

    private void grantRewardAndHide(View cardView) {
        // منح النقاط
        ;
        licenceData.setAdRewards(20);
        // حفظ الحالة لمنع الظهور مجدداً
        getSharedPreferences("AppPrefs", MODE_PRIVATE).edit()
                .putBoolean("is_app_rated", true).apply();

        // إخفاء الكارد
        cardView.setVisibility(View.GONE);
    }

    private void launchGooglePlayFallback() {
        final String appPackageName = getPackageName();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
        } catch (android.content.ActivityNotFoundException e) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
        }
    }

    private void setupBillingClient() {
        // إنشاء PendingPurchasesParams لتفعيل العمليات المعلقة للمنتجات لمرة واحدة (Lifetime)
        PendingPurchasesParams pendingPurchasesParams = PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build();

        billingClient = BillingClient.newBuilder(this)
                .setListener((billingResult, list) -> {
                    // Listener فارغ لأن MainActivity لا يحتاج لمعالجة عمليات الشراء،
                    // ولكن يجب تمريره لتجنب NullPointerException.
                })
                .enablePendingPurchases(pendingPurchasesParams) // ✅ الطريقة الجديدة
                .build();
    }

    /**
     * دالة الفحص الذكية
     */

    private void checkSubscriptionStatus() {
        if (!isCheckingSubscription.compareAndSet(false, true)) {
            Log.d(TAG, "checkSubscriptionStatus already running, skipping.");
            return;
        }
        try {
            if (isGuest) return;
            SecureLicenseManager manager = SecureLicenseManager.getInstance(this);
            Log.d(TAG, "Start checkSubscriptionStatus..." +
                    "\n" + " : PremiumActivatedBy: " + manager.getPremiumActivatedBy());

            // 1. إذا لم يكن بريميوم، لا تفعل شيئاً
            if (!manager.isPremium()) return;

            // 2. استثناء اشتراك مدى الحياة (لأنه لا يحتاج تجديد)
            String subType = manager.getPlanType();
            if (subType != null && (subType.contains("lifetime") || subType.contains("pro"))) {
                return;
            }
            runOnUiThread(() -> {
                try {
                    long expiryTime = manager.getSubscriptionExpired();
                    long currentTime = System.currentTimeMillis();
                    Log.d(TAG, "expiryTime " + expiryTime + " currentTime " + currentTime + "\n" +
                            "Diff= " + (expiryTime - currentTime));

                    checkSubscriptionExpiry();
                    // =========================================================
                    // الحالة أ: التاريخ المحلي انتهى (يجب التحقق من جوجل + فايرستور)
                    // =========================================================
                    if (currentTime > expiryTime) {
                        Log.d(TAG, "Local subscription expired. Verifying with Google...");
                        if (isNetworkAvailable()) {
                            // يوجد إنترنت -> افحص مع جوجل وسيرفرك
                            Log.d(TAG, "Expired locally. Checking with Google...");
                            if (Objects.equals(manager.getPremiumActivatedBy(), "Google Play Store")) {
                                verifySubscriptionWithGoogle();
                            } else {
                                downgradeUserToFree();
                            }

                        } else {

                            manager.setPremium(false);
                            manager.setPlanType("free");
                            handleOfflineExpiry(manager);
                        }
                    }
                    // =========================================================
                    // الحالة ب: تحذير قرب الانتهاء (3 أيام)
                    // =========================================================
                    else {
                        long diff = expiryTime - currentTime;
                        long daysLeft = TimeUnit.MILLISECONDS.toDays(diff);
                        if (daysLeft <= 3 && daysLeft >= 0) {
                            showExpiryWarningDialog(daysLeft);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Unexpected error in checkSubscriptionStatus UI part", e);
                    // لا نرمي الاستثناء خارجاً، فقط نسجله
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in checkSubscriptionStatus", e);
        } finally {
            isCheckingSubscription.set(false);
        }
    }

    private void checkSubscriptionExpiry() {
        long expiryLong = SecureLicenseManager.getInstance(this).getSubscriptionExpired();
        long currentTime = System.currentTimeMillis();
        long diffMillis = expiryLong - currentTime;
        long diffDays = diffMillis / (24 * 60 * 60 * 1000);
        if (diffDays < 4 && diffDays >= 0) {
            // تنسيق التاريخ حسب لغة الجهاز (مثل "Dec 1, 2026" للإنجليزية، "١ ديسمبر ٢٠٢٦" للعربية)
            Date expiryDate = new Date(expiryLong);
            DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.LONG, Locale.getDefault());
            String formattedDate = dateFormat.format(expiryDate);
            // الحصول على النص المترجم ودمج التاريخ فيه
            String message = getString(R.string.subscription_expiry_message, formattedDate);
            binding.warningText.setText(message);
            binding.warningText.setVisibility(View.VISIBLE);
        } else {
            binding.warningText.setVisibility(View.GONE);
        }
    }

    private void handleOfflineExpiry(SecureLicenseManager manager) {

        // 2. عرض الديالوج التوضيحي
        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "تم إلغاء عرض ديالوج انتهاء الاشتراك لأن الشاشة الحالية ميتة أو جاري إغلاقها.");
            return;
        }

        runOnUiThread(() -> {
            // تأكد من استخدام (MainActivity.this) لضمان استخدام السياق الصحيح للشاشة النشطة
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
                    .setTitle(getString(R.string.subscribing_end_titel))
                    .setMessage(getString(R.string.subscribing_end_message))
                    .setPositiveButton(getString(R.string.ar_text_5_3), (dialog, which) -> {
                        String uid = AppAuth.getUserID(this);

                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putBoolean("is_premium", false);
                        editor.apply();
                        if (uid != null) {
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

                    })
                    .setCancelable(false) // إجبار المستخدم على الضغط
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        });
    }

    private void verifySubscriptionWithGoogle() {
        if (Objects.equals(licenceData.getPremiumActivatedBy(), "Google Play Store")) {

            billingClient.startConnection(new BillingClientStateListener() {
                @Override
                public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        Log.w(TAG, "فحص الإشتراك في جوجل");
                        // فحص الاشتراكات (SUBS)
                        billingClient.queryPurchasesAsync(
                                QueryPurchasesParams.newBuilder()
                                        .setProductType(BillingClient.ProductType.SUBS)
                                        .build(),
                                (result, purchases) -> {

                                    processGooglePurchasesResult(result, purchases);

                                });
                    } else {
                        Log.w(TAG, "لا يوجد إشتراك في جوجل");

                        // في حال فشل الاتصال، نعتمد على الإلغاء المحلي الآمن
                        downgradeUserToFree();
                    }
                }

                @Override
                public void onBillingServiceDisconnected() {
                    // فشل الاتصال بالخدمة
                }
            });
        } else {

        }
    }

    private void processGooglePurchasesResult(BillingResult result, List<Purchase> purchases) {
        if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {

            Purchase activePurchase = null;
            if (purchases != null) {
                for (Purchase p : purchases) {
                    if (p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                        activePurchase = p; // وجدنا اشتراكاً نشطاً
                        Log.d(TAG, "GooglePurchases activePurchase: " + p);
                        break;
                    }
                }
            }

            if (activePurchase != null) {
                // --- الاشتراك نشط (تجدد تلقائياً) ---
                Log.d(TAG, "Active purchase found. Updating plan details...");
                long localExpiry = SecureLicenseManager.getInstance(this).getSubscriptionExpired();
                long now = System.currentTimeMillis();
                // فقط نجدّد إذا كان الاشتراك المحلي منتهياً أو أقل من يوم
                if (now >= localExpiry - TimeUnit.DAYS.toMillis(1)) {
                    Log.d(TAG, "Renewing subscription because local expiry is near or passed");
                    fetchPlanDetailsAndRenew(activePurchase);
                } else {
                    Log.d(TAG, "Google Play has active subscription, but local expiry is still far. No renewal needed.");
                    // ربما نقوم بتحديث التوكن فقط، أو نتحقق من عدم تطابق البيانات
                }

            } else {
                // --- الاشتراك انتهى فعلياً ---
                Log.d(TAG, "No active purchase found.");
                downgradeUserToFree();
            }
        }
    }

    /**
     * جلب اسم الخطة من فايرستور (لضمان الدقة) ثم التحديث
     * هذه الخطوة ضرورية لأن Purchase object لا يعطينا اسم الخطة (Monthly/Yearly) بسهولة
     */
    private void fetchPlanDetailsAndRenew(Purchase purchase) {
        String uid = AppAuth.getUserID(this);
        if (uid == null) {
            // حالة نادرة: مستخدم غير مسجل دخول -> نعتمد على المحلي
//            updateSubscriptionLocally(purchase, licenceData.getPlanType());
            return;
        }

        FirebaseFirestore.getInstance().collection("users").document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String currentPlanType = "";

                    // 1. نحاول أخذ الخطة من فايرستور (قد يكون غيرها من جهاز آخر)
                    if (documentSnapshot.exists() && documentSnapshot.contains("plan_type")) {
                        currentPlanType = documentSnapshot.getString("plan_type");
                    }

                    // 2. إذا لم نجدها، نستخدم المخزنة محلياً
                    if (currentPlanType == null || currentPlanType.isEmpty()) {
                        currentPlanType = licenceData.getPlanType();
                    }

                    // 3. إذا فشل كل شيء، نضع افتراضي (شهري) لتجنب الكراش
                    if (currentPlanType == null || currentPlanType.isEmpty()) {
                        currentPlanType = "monthly-auto";
                    }

                    // الآن لدينا اسم الخطة + تأكيد الشراء من جوجل -> نحدث التاريخ
                    updateLocalAndRemoteSubscription(purchase, currentPlanType);
                })
                .addOnFailureListener(e -> {
                    // فشل النت -> نعتمد على المحلي مؤقتاً
                    updateSubscriptionLocally(purchase, licenceData.getPlanType());
                });
    }

    /**
     * حساب التاريخ الجديد وتحديث الكل
     */
    private void updateLocalAndRemoteSubscription(Purchase purchase, String planType) {
        // حساب تاريخ الانتهاء الجديد بناءً على نوع الخطة
        Calendar calendar = Calendar.getInstance();

        if (planType.contains("yearly")) {
            calendar.add(Calendar.YEAR, 1);
        } else {
            // الافتراضي شهري (monthly)
            calendar.add(Calendar.MONTH, 1);
        }

        Date newExpiryDate = calendar.getTime();

        // 1. تحديث محلي (SecureLicenseManager)
        SecureLicenseManager manager = SecureLicenseManager.getInstance(this);
        manager.setPremium(true);
        manager.setPlanType(planType); // تحديث الاسم أيضاً
        manager.setSubscriptionExpiry(newExpiryDate.getTime());

        // 2. تحديث فايرستور
        String uid = AppAuth.getUserID(this);
        if (uid != null) {
            Map<String, Object> updateMap = new HashMap<>();
            updateMap.put("is_premium", true);
            updateMap.put("plan_type", planType); // حفظ الاسم
            updateMap.put("subscription_expiry", newExpiryDate);
            updateMap.put("purchase_token", purchase.getPurchaseToken());
            updateMap.put("lastModified", System.currentTimeMillis());
            updateMap.put("subscription_start", FieldValue.serverTimestamp());

            FirebaseFirestore.getInstance().collection("users").document(uid)
                    .set(updateMap, SetOptions.merge());
        }

        Log.d(TAG, "Subscription renewed successfully: " + planType);
    }

    // نسخة مبسطة للتحديث المحلي فقط (Fallback)
    private void updateSubscriptionLocally(Purchase purchase, String planType) {
        if (planType == null) planType = "monthly-auto";
        updateLocalAndRemoteSubscription(purchase, planType);
    }

    /**
     * إلغاء الاشتراك (Local + Firestore)
     */
    private void downgradeUserToFree() {
        SecureLicenseManager manager = SecureLicenseManager.getInstance(this);
        if (!manager.isPremium()) return; // تم الإلغاء مسبقاً

        // 1. محلياً
        manager.setPremium(false);
        manager.setPlanType("free");
        manager.setPremiumActivatedBy("");
        manager.setSubscriptionExpiry(0);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("is_premium", false);
        editor.apply();
        // 2. فايرستور
        String uid = AppAuth.getUserID(this);
        if (uid != null) {
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

        // 3. عرض الديالوج
        runOnUiThread(this::showExpiredDialog);
    }

    private void showExpiryWarningDialog(long daysLeft) {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        long lastShownTime = prefs.getLong("last_expiry_warning_time", 0);
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastShownTime < 86400000) return;

        prefs.edit().putLong("last_expiry_warning_time", currentTime).apply();

        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "تم إلغاء عرض ديالوج انتهاء الاشتراك لأن الشاشة الحالية ميتة أو جاري إغلاقها.");
            return;
        }

        runOnUiThread(() -> {
            // تأكد من استخدام (MainActivity.this) لضمان استخدام السياق الصحيح للشاشة النشطة
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
                    .setTitle(getString(R.string.showExpiryWarningDialog_03))
                    .setMessage(getString(R.string.showExpiryWarningDialog_04) + daysLeft + getString(R.string.showExpiryWarningDialog_05))
                    .setPositiveButton(getString(R.string.showExpiryWarningDialog_06), (dialog, which) -> {
                        //startActivity (new Intent(this, SubscriptionActivity.class)); // تأكد من استيراد الكلاس الصحيح
                        openSubscriptionManagement();
                    })
                    .setNegativeButton(getString(R.string.remind_laters), null)
                    .show();
        });
    }

    private void showExpiredDialog() {
        // 🔥 حماية صارمة وصارمة جداً لمنع الـ BadTokenException
        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "تم إلغاء عرض ديالوج انتهاء الاشتراك لأن الشاشة الحالية ميتة أو جاري إغلاقها.");
            return;
        }

        runOnUiThread(() -> {
            // تأكد من استخدام (MainActivity.this) لضمان استخدام السياق الصحيح للشاشة النشطة
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
                    .setTitle(getString(R.string.showExpiredDialog_07))
                    .setMessage(getString(R.string.showExpiredDialog_08))
                    .setPositiveButton(getString(R.string.showExpiredDialog_09), (dialog, which) -> {

                        Intent subscribeIntent = new Intent(MainActivity.this, SubscriptionActivity.class);
                        subscribeIntent.putExtra("purchase", true);
                        subscribeLauncher.launch(subscribeIntent);
//                    openSubscriptionManagement();
                    })
                    .setNegativeButton(getString(R.string.showExpiredDialog_10), null)
                    .setCancelable(false)
                    .show();
        });
    }

    /**
     * دالة لتوجيه المستخدم لصفحة إدارة الاشتراكات في Google Play
     */
    private void openSubscriptionManagement() {
        try {
            //  String url = "https://play.google.com/store/account/subscriptions?package=" + getPackageName();

            String url = "https://play.google.com/store/account/subscriptions?sku=my_smart_wallet_premium&package=" + getPackageName();

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            intent.setPackage("com.android.vending"); // تحديد تطبيق المتجر لضمان الفتح فيه
            startActivity(intent);
        } catch (Exception e) {
            // في حال لم يكن تطبيق المتجر مثبتاً، نفتح الرابط في المتصفح العادي
            String url = "https://play.google.com/store/account/subscriptions";
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            startActivity(intent);
        }
    }

    private void initializeWebView() {
        // تهيئة WebView في خيط منفصل
        runOnUiThread(() -> {
            try {
                WebView webView = new WebView(getApplicationContext());
                webView.loadData("<html><body>Loading...</body></html>", "text/html", "UTF-8");
                runOnUiThread(() -> {
                    if (webView.getParent() != null) {
                        ((ViewGroup) webView.getParent()).removeView(webView);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "WebView initialization failed", e);
            }
        });
    }

    private void checkFirestoreConnection() {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        firestore.disableNetwork().addOnCompleteListener(task -> {
            // حاول الاتصال بعد إيقاف الشبكة مؤقتاً
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(() -> {
                firestore.enableNetwork().addOnCompleteListener(task1 -> {
                    if (task1.isSuccessful()) {
                        Log.d(TAG, "Firestore connection restored");
                    } else {
                        Log.e(TAG, "Failed to restore Firestore connection");
                        showNetworkErrorDialog();
                    }
                });
            }, 3000);
        });
    }

    private void showNetworkErrorDialog() {
        runOnUiThread(() -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("اتصال الشبكة")
                    .setMessage("يوجد مشكلة في الاتصال بالسحابة. سيتم العمل في الوضع المحلي حتى يتوفر اتصال إنترنت مستقر.")
                    .setPositiveButton("موافق", null)
                    .show();
        });
    }

    private boolean validateAppIntegrity() {
        try {
            // 1. فحص توقيع التطبيق
            PackageManager pm = getPackageManager();
//            PackageInfo packageInfo = pm.getPackageInfo(getPackageName(),
//                    PackageManager.GET_SIGNATURES);

            // 2. فحص Google Play Services
            GoogleApiAvailability api = GoogleApiAvailability.getInstance();
            int result = api.isGooglePlayServicesAvailable(this);

            if (result != ConnectionResult.SUCCESS) {
                Log.w(TAG, "Google Play Services not available: " + result);
                // يمكن الاستمرار ولكن مع تعطيل المزايا التي تعتمد على GMS
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "App integrity check failed", e);
            return false;
        }
    }

    public static void checkAndUpdateExpiredSubscriptions() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Timestamp now = Timestamp.now();
        // 1. استعلام عن المستخدمين الذين هم بريميوم حالياً وتاريخ انتهائهم أقل من الآن
        db.collection("users")
                .whereEqualTo("is_premium", true)
                .whereLessThan("subscription_expiry", now)  // أو .whereLessThanOrEqualTo
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        Log.d("SubscriptionCheck", "No expired premium users found.");
                        return;
                    }

                    // 2. استخدام WriteBatch لتحديث جميع المستخدمين دفعة واحدة
                    WriteBatch batch = db.batch();
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        batch.update(doc.getReference(),
                                "is_premium", false,
                                "subscriptionStatus", "expired");
                    }

                    // 3. تنفيذ التحديثات
                    batch.commit()
                            .addOnSuccessListener(aVoid -> Log.d("SubscriptionCheck", "Updated " + queryDocumentSnapshots.size() + " expired subscriptions"))
                            .addOnFailureListener(e -> Log.e("SubscriptionCheck", "Failed to update expired subscriptions", e));
                })
                .addOnFailureListener(e -> Log.e("SubscriptionCheck", "Query failed", e));
    }

    public static void finalFixFirstSubscriptionDate(Context context, @Nullable OnCompleteListener<Void> onComplete) {
        if (SecureLicenseManager.getInstance(context).isGuest()) {
            Log.d(TAG, "Guest user, skip fix");
            if (onComplete != null) onComplete.onComplete(null);
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences("subscription_final_fix2", Context.MODE_PRIVATE);
        if (prefs.getBoolean("fix_applied", false)) {
            Log.d(TAG, "Fix already applied.");
            if (onComplete != null) onComplete.onComplete(null);
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // جلب جميع المستخدمين
        db.collection("users").get().addOnSuccessListener(snapshot -> {
            WriteBatch batch = db.batch();
            int deleteCount = 0;
            int addCount = 0;

            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                // ==========================================
                // الخطوة الأولى: حذف حقل first_subscription_date من الجميع (إن وُجد)
                // ==========================================
                if (doc.contains("first_subscription_date")) {
                    //  batch.update(doc.getReference(), "first_subscription_date", FieldValue.delete());
                    deleteCount++;
                    Log.d(TAG, "Deleted first_subscription_date for: " + doc.getId());
                }

                // ==========================================
                // الخطوة الثانية: إضافة الحقل فقط للمستخدمين الذين is_premium == true (Boolean)
                // ==========================================
                Boolean isPremium = doc.getBoolean("is_premium");
                String activatedBy = "";
                activatedBy = doc.getString("premium_activated_by");
                if ("Google Ads".equals(activatedBy)) continue;
                if (isPremium != null && isPremium) {
                    batch.update(doc.getReference(), "first_subscription_date", Timestamp.now());
                    addCount++;
                    Log.d(TAG, "Added first_subscription_date for premium user: " + doc.getId());
                }
            }

            if (deleteCount > 0 || addCount > 0) {
                int finalDeleteCount = deleteCount;
                int finalAddCount = addCount;
                batch.commit().addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Final fix: Deleted from " + finalDeleteCount + " users, Added to " + finalAddCount + " premium users.");
                    prefs.edit().putBoolean("fix_applied", true).apply();
                    if (onComplete != null) onComplete.onComplete(null);
                }).addOnFailureListener(e -> {
                    Log.e(TAG, "Batch commit failed", e);
                    if (onComplete != null) onComplete.onComplete(null);
                });
            } else {
                Log.d(TAG, "No changes needed.");
                prefs.edit().putBoolean("fix_applied", true).apply();
                if (onComplete != null) onComplete.onComplete(null);
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to fetch users", e);
            if (onComplete != null) onComplete.onComplete(null);
        });
    }



    // ==================== وحدة الميزانية والشريط السفلي الموحد ====================

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("is_budget_tab_active", isBudgetTabActive);
    }

    private void switchMainTab(boolean showBudget) {
        isBudgetTabActive = showBudget;
        if (showBudget) {
            if (isSearchMode) {
                toggleSearchMode(false);
            }
            binding.mainContent.setVisibility(View.GONE);
            binding.budgetFragmentContainer.setVisibility(View.VISIBLE);

            if (budgetContainerFragment == null) {
                Fragment existing = getSupportFragmentManager().findFragmentByTag("BudgetContainerFragment");
                if (existing instanceof BudgetContainerFragment) {
                    budgetContainerFragment = (BudgetContainerFragment) existing;
                } else {
                    budgetContainerFragment = new BudgetContainerFragment();
                    getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.budget_fragment_container, budgetContainerFragment, "BudgetContainerFragment")
                            .commitNowAllowingStateLoss();
                }
            }
            if (!availableCurrencies.isEmpty() && currentCurrencyIndex < availableCurrencies.size()) {
                syncSelectedCurrencyToBudget(availableCurrencies.get(currentCurrencyIndex));
            }
            if (budgetViewModel != null) {
                budgetViewModel.forceReload();
            }
        } else {
            binding.budgetFragmentContainer.setVisibility(View.GONE);
            binding.mainContent.setVisibility(View.VISIBLE);
        }
        updateBottomNavSelectionUi();
    }

    private void updateBottomNavSelectionUi() {
        int activeColor = resolveThemeColor(androidx.appcompat.R.attr.colorPrimary, R.color.budget_primary);
        int inactiveColor = resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant, R.color.budget_text_secondary);

        int homeColor = isBudgetTabActive ? inactiveColor : activeColor;
        int budgetColor = isBudgetTabActive ? activeColor : inactiveColor;

        binding.navIconHome.setImageTintList(ColorStateList.valueOf(homeColor));
        binding.navTextHome.setTextColor(homeColor);

        binding.navIconBudget.setImageTintList(ColorStateList.valueOf(budgetColor));
        binding.navTextBudget.setTextColor(budgetColor);
    }

    private int resolveThemeColor(int attrRes, int fallbackColorRes) {
        TypedValue tv = new TypedValue();
        if (getTheme().resolveAttribute(attrRes, tv, true)) {
            if (tv.resourceId != 0) {
                return ContextCompat.getColor(this, tv.resourceId);
            }
            return tv.data;
        }
        return ContextCompat.getColor(this, fallbackColorRes);
    }

    private void syncSelectedCurrencyToBudget(@Nullable Currency currency) {
        if (currency == null) return;
        if (budgetContainerFragment != null && budgetContainerFragment.isAdded()) {
            budgetContainerFragment.syncCurrencyFromHost(currency);
        } else if (budgetViewModel != null) {
            String sym = currency.getSymbol() != null ? currency.getSymbol().trim() : "";
            String code = currency.getCode() != null ? currency.getCode().trim() : "";
            String name = currency.getName() != null ? currency.getName().trim() : "";
            String shortLabel = (!sym.isEmpty() && !"﷼".equals(sym)) ? sym : (!code.isEmpty() ? code : name);
            budgetViewModel.setSelectedCurrency(currency.getId(), name, shortLabel, currency.getFirestoreId());
        }
    }

    private void ensureBudgetCurrencyReady() {
        if (budgetViewModel != null
                && budgetViewModel.getCurrentCurrencySymbolValue().isEmpty()
                && !availableCurrencies.isEmpty()
                && currentCurrencyIndex < availableCurrencies.size()) {
            syncSelectedCurrencyToBudget(availableCurrencies.get(currentCurrencyIndex));
        }
    }

    private void showQuickAddBottomSheet() {
        if (isFinishing() || isDestroyed()) return;
        if (getSupportFragmentManager().findFragmentByTag("QuickAddBottomSheet") != null) return;
        QuickAddBottomSheet.newInstance().show(getSupportFragmentManager(), "QuickAddBottomSheet");
    }

    private void openAddDebtTransactionActivity() {
        if (!db.isOpen()) {
            db = AppDatabase.getDatabase(MainActivity.this);
        }
        if (availableCurrencies.isEmpty() || currentCurrencyIndex >= availableCurrencies.size()) {
            addDefaultCurrencyAndProceed();
            return;
        }
        Intent intent = new Intent(MainActivity.this, AddTransactionActivity.class);
        intent.putExtra("CURRENCY", availableCurrencies.get(currentCurrencyIndex).name);
        startActivity(intent);
    }

    @Override
    public void onBudgetOpenDrawer() {
        if (binding != null && binding.drawerLayout != null) {
            binding.drawerLayout.openDrawer(GravityCompat.START);
        }
    }

    @Override
    public void onBudgetRequestAddEntry(String entryType) {
        if (isFinishing() || isDestroyed()) return;
        if (getSupportFragmentManager().findFragmentByTag("AddBudgetEntryBottomSheet") != null) return;
        ensureBudgetCurrencyReady();
        AddBudgetEntryBottomSheet.newInstanceForAdd(entryType)
                .show(getSupportFragmentManager(), "AddBudgetEntryBottomSheet");
    }

    @Override
    public void onBudgetRequestEditEntry(BudgetEntryWithCategory entry) {
        if (isFinishing() || isDestroyed() || entry == null) return;
        if (getSupportFragmentManager().findFragmentByTag("EditBudgetEntryBottomSheet") != null) return;
        ensureBudgetCurrencyReady();
        AddBudgetEntryBottomSheet.newInstanceForEdit(entry)
                .show(getSupportFragmentManager(), "EditBudgetEntryBottomSheet");
    }

    @Override
    public void onBudgetRequestEditLimit(double currentLimit) {
        if (isFinishing() || isDestroyed()) return;
        if (getSupportFragmentManager().findFragmentByTag("EditBudgetLimitDialog") != null) return;
        ensureBudgetCurrencyReady();
        EditBudgetLimitDialog.newInstance(currentLimit)
                .show(getSupportFragmentManager(), "EditBudgetLimitDialog");
    }

    @Override
    public void onBudgetCurrencyChanged(Currency currency) {
        if (currency == null || availableCurrencies.isEmpty()) return;
        for (int i = 0; i < availableCurrencies.size(); i++) {
            if (availableCurrencies.get(i).getId() == currency.getId()) {
                currentCurrencyIndex = i;
                String name = availableCurrencies.get(i).name;
                binding.currencyTextView.setText(name);
                mainViewModel.setCurrency(name);
                break;
            }
        }
    }

    @Override
    public void onQuickAddExpense() {
        onBudgetRequestAddEntry(BudgetEntry.TYPE_EXPENSE);
    }

    @Override
    public void onQuickAddIncome() {
        onBudgetRequestAddEntry(BudgetEntry.TYPE_INCOME);
    }

    @Override
    public void onQuickAddDebtTransaction() {
        openAddDebtTransactionActivity();
    }

    @Override
    public void onQuickAddAccount() {
        if (isFinishing() || isDestroyed()) return;
        AccountBottomSheet sheet = AccountBottomSheet.newInstanceForCreate("");
        sheet.setAccountActionListener(new AccountBottomSheet.AccountActionListener() {
            @Override
            public void onAccountSaved(Account account, boolean isNew) {
                if (mainViewModel != null) mainViewModel.refreshData();
            }
        });
        sheet.show(getSupportFragmentManager(), "AccountBottomSheetCreate");
    }

    // واجهة مبسطة للاستدعاء
    public interface OnCompleteListener<T> {
        void onComplete(@Nullable T result);
    }
}

