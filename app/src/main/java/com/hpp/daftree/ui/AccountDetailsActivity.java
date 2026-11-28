package com.hpp.daftree.ui;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.Target;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.hpp.daftree.models.AppAuth;
import com.hpp.daftree.adapters.TransactionHistoryAdapter;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.RecurringOperation;
import com.hpp.daftree.database.Transaction;
import com.hpp.daftree.database.User;
import com.hpp.daftree.databinding.ActivityAccountDetailsBinding;
import com.hpp.daftree.dialogs.AccountBottomSheet;
import com.hpp.daftree.dialogs.CalculatorDialog;
import com.hpp.daftree.helpers.ShareNotificationHelper;
import com.hpp.daftree.dialogs.PurchaseBottomSheet;
import com.hpp.daftree.dialogs.ReportsBottomSheet;
import com.hpp.daftree.helpers.LanguageManager;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.models.FormatingAmount;
import com.hpp.daftree.notifications.TransactionNotificationManager;
import com.hpp.daftree.syncmanagers.SyncPreferences;
import com.hpp.daftree.utils.AdsManager;
import com.hpp.daftree.utils.DialogHelper;
import com.hpp.daftree.utils.ImageUtils;
import com.hpp.daftree.viewmodels.AccountDetailsViewModel;
import com.hpp.daftree.viewmodels.AddTransactionViewModel;
import com.hpp.daftree.viewmodels.CurrencyViewModel;
import com.hpp.daftree.utils.LicenseManager;

import java.io.InputStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.hpp.daftree.budget.repository.BudgetRepository;
import com.hpp.daftree.helpers.AppFontHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.models.TransactionItem;
import com.hpp.daftree.ViewModelFactory;
import com.hpp.daftree.databinding.DialogAddNewTransactionBinding;
import com.hpp.daftree.dialogs.InvoiceDialog;
import com.hpp.daftree.models.AutoResizeTextHelper;
import com.hpp.daftree.models.ReportGenerator;
import com.hpp.daftree.utils.ReferralManager;
import com.hpp.daftree.utils.SecureLicenseManager;
import com.hpp.daftree.viewmodels.RecurringOperationsViewModel;

import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class AccountDetailsActivity extends BaseActivity implements
        TransactionHistoryAdapter.OnItemInteractionListener, CalculatorDialog.OnCalculationCompleteListener {

    private static final String TAG = "AccountDetailsActivity";
    private ActivityAccountDetailsBinding binding;
    private AccountDetailsViewModel viewModel;
    private OnBackPressedCallback backPressedCallback;
    private AlertDialog progressDialog;
    private static final int LOGIN_REQUEST_CODE = 1001;
    private final ExecutorService imageProcessingExecutor = Executors.newSingleThreadExecutor();
    private AddTransactionViewModel addTransactionViewModel;
    private String tempEncodedImage = null;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private AdsManager adsManager;
    private TransactionHistoryAdapter historyAdapter;
    private User currentUser;
    private int accountId;
    private Uri photoUri;
    private List<Account> accountsList;
    private String accountName;
    //    private User currentUserData;
    private LicenseManager licenseManager;
    //  private LicenseSyncManager licenseSyncManager;
//    private final String[] currencies = {getString(R.string._115), getString(R.string._115_2b4e), getString(R.string._115_fa34)};
    private CurrencyViewModel currencyViewModel; // ViewModel جديد
    private final List<Currency> allAvailableCurrencies = new ArrayList<>(); // كل العملات المتاحة
    private final List<String> activeCurrenciesForAccount = new ArrayList<>(); // العملات النشطة فقط لهذا الحساب
    private ActivityResultLauncher<PickVisualMediaRequest> photoPickerLauncher;
    private int currentCurrencyIndex = 0;
    private Transaction lastSavedTransaction;
    private boolean hasUsdTransactions = false;
    private boolean hasSarTransactions = false;
    private ViewModelFactory factory;
    private Account currentAccount;
    private static final int PDF_PERMISSION_CODE = 102;
    private DaftreeRepository repository;
    private ActivityResultLauncher<Intent> pdfSaveLauncher;
    private EditText dialogAmountEditText; // متغير لحفظ مرجع حقل المبلغ في الديالوج
    private SyncPreferences syncPreferences;

    boolean isGuest = false;
    private String guestUID = "";
    private LanguageManager languageManager;
    // أضف هذا المتغير في الأعلى مع تعريف الـ Views
    private TextWatcher searchTextWatcher;
    private DialogAddNewTransactionBinding activeDialogBinding;
    private boolean isAddNewTransactionBindingShowing = false;
    private RecurringOperationsViewModel recurringOperationsViewModel;
    private View recurringBubble;
    // 1. Launcher لطلب إذن جهات الاتصال

    // 2. Launcher لفتح جهات الاتصال واستلام النتيجة

    // دالة مساعدة لفتح جهات الاتصال

    // دالة لمعالجة البيانات القادمة من جهات الاتصال

    @Override
    public void onCalculationComplete(String result) {
        if (dialogAmountEditText != null) {
            dialogAmountEditText.setText(result);
        }
        if (result != null && !result.isEmpty()) {
            try {
                if (dialogAmountEditText == null) return;
                String cleanString = result.replaceAll("[,]", "");
                // تنسيق الناتج مع فواصل الآلاف
                double parsed = Double.parseDouble(cleanString);
                //DecimalFormat formatter = new DecimalFormat("#,###");
                NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);
                String formattedResult = formatCurrency(parsed);
                dialogAmountEditText.setText(formattedResult);
                dialogAmountEditText.setSelection(formattedResult.length()); // نقل المؤشر للنهاية
            } catch (NumberFormatException e) {
                dialogAmountEditText.setText(result); // عرض الناتج كما هو إذا فشل التنسيق
            }
        }
    }
    private String formatCurrency(double amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(2);
        return format.format(amount);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == LOGIN_REQUEST_CODE && resultCode == RESULT_OK) {
            Log.e(TAG, "تم تسجيل الدخول بنجاح - إعادة تشغيل التطبيق من SplashActivity");
        }
    }

    private final ActivityResultLauncher<Intent> loginLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this); // تفعيل العرض الكامل
        super.onCreate(savedInstanceState);

        binding = ActivityAccountDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupFloatingToolbar(binding.recyclerHeader);
        // 1. التعامل مع الأبعاد والحواف (Keyboard + Status Bar)
        setupInsets();

        // 2. تفعيل أزرار التولبار والبحث
        setupToolbarActions();
        setupSwipeGesture();
        languageManager = LanguageManager.getInstance(this);
        MyApplication.applyGlobalTextWatcher(binding.getRoot());
        licenseManager = new LicenseManager(this);
        syncPreferences = new SyncPreferences(this);
        repository = new DaftreeRepository(getApplication());
        adsManager = new AdsManager(this);
        recurringOperationsViewModel = new ViewModelProvider(this).get(RecurringOperationsViewModel.class);
        String mainMessage = getString(R.string.account_detail_activity_help);
        // TutorialDialog.show(this, "AccountDetailsActivity", mainMessage);
        isGuest = SecureLicenseManager.getInstance(this).isGuest();
        if (isGuest) {
            guestUID = SecureLicenseManager.getInstance(this).guestUID();
        }
        accountId = getIntent().getIntExtra("ACCOUNT_ID", -1);
        if (accountId == -1) {
            finish();
            return;
        }
        accountName = getIntent().getStringExtra("ACCOUNT_NAME");


        View rootView = binding.getRoot();
        AutoResizeTextHelper mTextResizeHelper = new AutoResizeTextHelper(rootView);
        // إضافة جميع العناصر النصية يدوياً
        mTextResizeHelper.addViews(
                binding.tvTotalCredit, binding.tvTotalDebit);
        binding.tvAccountName.setText(accountName);
        // تعديل 1: تمرير context و listener للـ Adapter
        historyAdapter = new TransactionHistoryAdapter(this, new TransactionHistoryAdapter.OnItemInteractionListener() {
            @Override
            public void onItemLongClick(TransactionItem transactionItem) {
                // الكود الحالي
                Transaction tx = transactionItem.getTransaction();
                String currencyName = findCurrencyNameById(tx.getCurrencyId());
                if (currencyName == null) return;

                if (tx.getBillType() != null && !tx.getBillType().isEmpty()) {
                    InvoiceDialog dialog = InvoiceDialog.newInstanceForEdit(
                            tx.getImportID(),
                            currencyName,
                            "true"
                    );
                    dialog.show(getSupportFragmentManager(), "InvoiceDialog");
                } else {
                    showTransactionDialog(tx);
                }
            }

            @Override
            public void onEditIconClick(TransactionItem transactionItem) {
                // استدعاء نفس الدالة المستخدمة في onItemLongClick
                onItemLongClick(transactionItem);
            }
        }, true); // ⭐⭐ تمرير true لعرض أيقونة التعديل ⭐⭐

        binding.transactionsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.transactionsRecyclerView.setAdapter(historyAdapter);


        factory = new ViewModelFactory(getApplication(), accountId);
        viewModel = new ViewModelProvider(this, factory).get(AccountDetailsViewModel.class);
        currencyViewModel = new ViewModelProvider(this).get(CurrencyViewModel.class); // تهيئة
        addTransactionViewModel = new ViewModelProvider(this).get(AddTransactionViewModel.class);
        String initialCurrency = getIntent().getStringExtra("CURRENCY");
        if (initialCurrency == null) {
            initialCurrency = MyApplication.defaultCurrencyName;//= String.valueOf(R.string.currency_local); // قيمة افتراضية في حالة عدم وجودها
            // R.string.currency_local
        }
        viewModel.getAccountDetails().observe(this, account -> {
            this.currentAccount = account; // تحديث المتغير المحلي دائمًا
            if (account != null) {
                binding.tvAccountName.setText(account.getAccountName());
            }
        });
        binding.currencyTextView.setText(initialCurrency);

        // 3. إخبار الـ ViewModel بالبدء في تحميل بيانات هذه العملة فورًا
        viewModel.setCurrency(initialCurrency);

        // ... الكود الخاص بمراقبة accountDetails وإعدادات الواجهة الأخرى ...
        setupCurrencyObservers();
        setupObservers();
        updateUiForSelectedCurrency();
        setupBottomBar();
        setupModernImageLaunchers();
        pdfSaveLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == AppCompatActivity.RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null) {
                    writePdfToUri(uri); // عند اختيار مكان الحفظ، نبدأ عملية الكتابة
                }
            }
        });

        backPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding.etSearch.getVisibility() == View.VISIBLE) {
                    toggleSearchMode(false);
                } else {
                    finish();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);
        showAppTour();
        binding.totalBalanceTitle.setText(getString(R.string.balance) + ": ");
    }

    private void setupModernImageLaunchers() {
        // تسجيل مشغل الكاميرا
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    try {
                        // قراءة الصورة بالحجم الكامل من الملف الذي حفظته الكاميرا
                        InputStream is = getContentResolver().openInputStream(photoUri);
                        Bitmap bitmap = BitmapFactory.decodeStream(is);
                        processAndSetImage(bitmap); // الآن الدقة ستكون مذهلة
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
        );

        // تسجيل مشغل اختيار الصور (Photo Picker)
        photoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                uri -> {
                    if (uri != null) {
                        try {
                            InputStream is = getContentResolver().openInputStream(uri);
                            Bitmap bitmap = BitmapFactory.decodeStream(is);
                            processAndSetImage(bitmap);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
        );

        // تسجيل مشغل الأذونات
        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) openCamera();
                    else Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
                }
        );
    }

    private void showAppTour() {
        // 1. التحقق: هل هذه أول مرة يفتح فيها المستخدم التطبيق؟
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean first_time_tour = prefs.getBoolean("first_detailAccount_tour", true);

        if (!first_time_tour) {
            return; // إذا لم تكن المرة الأولى، اخرج من الدالة
        }
        TextView textView = findViewById(R.id.currencyTextView);
        // 2. إعداد تسلسل العرض التوضيحي
        try {


            new TapTargetSequence(this)
                    .targets(
                            // الخطوة 1: زر إضافة حساب
                            createTarget(findViewById(R.id.btnNewBill),
                                    getString(R.string.tut_bill_title),
                                    getString(R.string.tut_bill_desc)),


                            // الخطوة 3: الحسابات المرتبطة
                            createTarget(findViewById(R.id.btnPdf),
                                    getString(R.string.tut_Pdf_title),
                                    getString(R.string.tut_Pdf_desc)),

                            // الخطوة 4: نص العملة
                            createTarget(findViewById(R.id.currencyTextView),
                                    getString(R.string.tut_4_title),
                                    getString(R.string.tut_4_desc))
                    )
                    .listener(new TapTargetSequence.Listener() {
                        @Override
                        public void onSequenceFinish() {
                            // عند انتهاء الشرح، نحفظ أنه تم العرض لعدم إظهاره مرة أخرى
                            prefs.edit().putBoolean("first_detailAccount_tour", false).apply();

                        }

                        @Override
                        public void onSequenceStep(TapTarget lastTarget, boolean targetClicked) {
                            // كود اختياري يتم تنفيذه بعد كل خطوة
                        }

                        @Override
                        public void onSequenceCanceled(TapTarget lastTarget) {
                            // في حال ضغط المستخدم خارج الدائرة للإلغاء (اختياري: يمكن حفظ الحالة أيضاً)
                            prefs.edit().putBoolean("first_detailAccount_tour", false).apply();

                        }
                    })
                    .start();
        } catch (RuntimeException e) {
            //  throw new RuntimeException(e);
        }
    }

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

    // دالة لتهيئة السحب يمين/يسار
    @SuppressLint("ClickableViewAccessibility")
    private void setupSwipeGesture() {
        // تعريف كاشف الإيماءات
        android.view.GestureDetector gestureDetector = new android.view.GestureDetector(this, new android.view.GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(android.view.MotionEvent e1, android.view.MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;

                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                // التحقق من أن السحب أفقي (المسافة الأفقية أكبر من العمودية)
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    // التحقق من أن السحب قوي ومسافته كافية
                    if (Math.abs(diffX) > 100 && Math.abs(velocityX) > 100) {
                        // تم السحب لليمين أو اليسار -> تبديل العملة
                        cycleCurrency();
                        return true;
                    }
                }
                return super.onFling(e1, e2, velocityX, velocityY);
            }
        });

        // ربط الكاشف بـ RecyclerView
        binding.transactionsRecyclerView.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            // نعيد false للسماح للـ RecyclerView بمعالجة السكرول العمودي بشكل طبيعي
            return false;
        });
    }

    private void setupInsets() {
        View bottomNavContainer = binding.bottomBar; // أو findViewById(R.id.bottom_nav_container);

        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) bottomNavContainer.getLayoutParams();
        int originalBottomMargin = layoutParams.bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(bottomNavContainer, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            params.bottomMargin = originalBottomMargin + systemBars.bottom;
            view.setLayoutParams(params);
            return windowInsets;
        });
    }

    private void setupToolbarActions() {
        // زر الرجوع
        binding.btnBack.setOnClickListener(v -> finish());

        // زر البحث: إظهار حقل البحث وإخفاء العنوان والأزرار
        binding.btnSearch.setOnClickListener(v -> toggleSearchMode(true));

        // زر إغلاق البحث: العودة للوضع الطبيعي
        binding.btnCloseSearch.setOnClickListener(v -> toggleSearchMode(false));

        // التعامل مع بقية الأزرار
        binding.btnPdf.setOnClickListener(v -> {
            if (!licenseManager.canCreateTransaction() && !SecureLicenseManager.getInstance(this).isPremium()) {
                showExpiryDialog(getString(R.string.plan_expired_title), getString(R.string.plan_expired_message));
                return;
            }
            showReportsDialog();
        });

        binding.btnNewBill.setOnClickListener(v -> {
            String currency = binding.currencyTextView.getText().toString();
            InvoiceDialog dialog = InvoiceDialog.newInstanceForAccount(currency, accountName);
            dialog.show(getSupportFragmentManager(), "InvoiceDialog");
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

            Intent intent = new Intent(this, SubscriptionActivity.class);
            startActivity(intent);

//            handlePurchaseApp();
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

    // 2. استبدل دالة toggleSearchMode بهذا الكود المصحح
    private void toggleSearchMode(boolean show) {
        if (show) {
            binding.tvAccountName.setVisibility(View.GONE);
            binding.iconsContainer.setVisibility(View.GONE);

            binding.etSearch.setVisibility(View.VISIBLE);
            binding.btnCloseSearch.setVisibility(View.VISIBLE);
            binding.etSearch.requestFocus();

            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT);

            // تهيئة المستمع إذا لم يكن موجوداً
            if (searchTextWatcher == null) {
                searchTextWatcher = new TextWatcher() {
                    private final Handler handler = new Handler(Looper.getMainLooper());
                    private Runnable runnable;
                    private final long DELAY = 500; // تأخير نصف ثانية

                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
//                        if (runnable != null) handler.removeCallbacks(runnable);
//
//                        runnable = () -> {
//                            String query = s.toString();//.trim();
//                            viewModel.setTransactionSearchQuery(query);
//                        };
//                        handler.postDelayed(runnable, DELAY);
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        String query = s.toString();//.trim();
                        viewModel.setTransactionSearchQuery(query);
                    }
                };
            }

            // إضافة المستمع بشكل صحيح
            binding.etSearch.addTextChangedListener(searchTextWatcher);

        } else {
            // الخروج من وضع البحث

            // أولاً: إزالة المستمع (المهم جداً) قبل تغيير النص
            if (searchTextWatcher != null) {
                binding.etSearch.removeTextChangedListener(searchTextWatcher);
            }

            // ثانياً: تنظيف النص بدون خوف من الانهيار
            binding.etSearch.setText("");

            binding.etSearch.setVisibility(View.GONE);
            binding.btnCloseSearch.setVisibility(View.GONE);

            binding.tvAccountName.setVisibility(View.VISIBLE);
            binding.iconsContainer.setVisibility(View.VISIBLE);

            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(binding.etSearch.getWindowToken(), 0);

            // تنبيه: لا تستخدم addTextChangedListener(null) أبداً!
        }
    }
//    .
//    @Override
//    public void onBackPressed() {
//        // إذا كان البحث مفتوحاً نغلقه أولاً بدلاً من الخروج
//        if (binding.etSearch.getVisibility() == View.VISIBLE) {
//            toggleSearchMode(false);
//        } else {
//            super.onBackPressed();
//        }
//    }

    private void setupObservers() {
        // هذا المراقب سيستجيب للتغيير الذي قمنا به في onCreate ويعرض البيانات
        viewModel.getTransactionItems().observe(this, transactionItems -> {
            updateSummariesFromItems(transactionItems);
            historyAdapter.submitList(transactionItems, () -> {
                if (transactionItems != null && !transactionItems.isEmpty()) {
                    binding.transactionsRecyclerView.smoothScrollToPosition(0);
                }
            });
        });

        viewModel.getAccountDetails().observe(this, account -> {
            this.currentAccount = account;
            if (account != null) {
                binding.tvAccountName.setText(account.getAccountName());
                checkRecurringOperation(account);
            }
        });

        viewModel.getUserProfile().observe(this, user -> this.currentUser = user);

        // مراقبون لتحديث حالة وجود عمليات للعملات الأخرى
        viewModel.getDebitUsd().observe(this, total -> checkUsdTransactions());
        viewModel.getCreditUsd().observe(this, total -> checkUsdTransactions());
        viewModel.getDebitSar().observe(this, total -> checkSarTransactions());
        viewModel.getCreditSar().observe(this, total -> checkSarTransactions());
    }

    private void setupCurrencyObservers() {
        // 1. جلب كل العملات المتاحة في التطبيق لاستخدامها في الـ Spinner
        currencyViewModel.getAllCurrencies().observe(this, currencies -> {
            if (currencies != null) {
                allAvailableCurrencies.clear();
                allAvailableCurrencies.addAll(currencies);
            }
        });

        // 2. جلب العملات النشطة (التي لها حركة) لهذا الحساب فقط
        viewModel.getActiveCurrencies().observe(this, activeNames -> {
            if (activeNames != null) {
                activeCurrenciesForAccount.clear();
                // تأكد دائمًا من وجود العملة الحالية في القائمة النشطة
                String currentDisplayedCurrency = binding.currencyTextView.getText().toString();
                if (!activeNames.contains(currentDisplayedCurrency)) {
                    activeCurrenciesForAccount.add(currentDisplayedCurrency);
                }
                activeCurrenciesForAccount.addAll(activeNames);
            }
        });
    }

    // **تعديل كامل لمنطق التنقل بين العملات**
    private void cycleCurrency() {
        if (activeCurrenciesForAccount.isEmpty()) {
            Toast.makeText(this, getString(R.string._368), Toast.LENGTH_SHORT).show();
            return;
        }

        String currentCurrency = binding.currencyTextView.getText().toString();
        int currentIndex = activeCurrenciesForAccount.indexOf(currentCurrency);

        // الانتقال إلى العملة التالية في القائمة النشطة
        int nextIndex = (currentIndex + 1) % activeCurrenciesForAccount.size();
        String nextCurrency = activeCurrenciesForAccount.get(nextIndex);

        // تحديث الواجهة
        binding.currencyTextView.setText(nextCurrency);
        viewModel.setCurrency(nextCurrency);
    }

    private void checkUsdTransactions() {
        Double debit = viewModel.getDebitUsd().getValue();
        Double credit = viewModel.getCreditUsd().getValue();
        hasUsdTransactions = (debit != null && debit > 0) || (credit != null && credit > 0);
    }

    private void checkSarTransactions() {
        Double debit = viewModel.getDebitSar().getValue();
        Double credit = viewModel.getCreditSar().getValue();
        hasSarTransactions = (debit != null && debit > 0) || (credit != null && credit > 0);
    }

    private void updateSummariesFromItems(List<TransactionItem> items) {
        if (items == null || items.isEmpty()) {
            binding.totalDebitTextView.setText("0");
            binding.totalCreditTextView.setText("0");
            binding.totalBalanceTextView.setText("0");
            return;
        }

        double totalDebit = 0.0, totalCredit = 0.0;
        for (TransactionItem item : items) {
            Transaction tx = item.getTransaction();
            if (tx.getType() == 1) totalDebit += tx.getAmount();
            else totalCredit += tx.getAmount();
        }

        double finalBalance = items.get(0).getBalanceAfter(); // أول عنصر هو الأحدث
        if (finalBalance < 0) {
            finalBalance = Math.abs(finalBalance);
            binding.totalBalanceTextView.setTextColor(ContextCompat.getColor(this, R.color.green));
        } else  {
            binding.totalBalanceTextView.setTextColor(ContextCompat.getColor(this, R.color.red));
        }
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        binding.totalDebitTextView.setText(nf.format(totalDebit));
        binding.totalCreditTextView.setText(nf.format(totalCredit));
        binding.totalBalanceTextView.setText(nf.format(finalBalance));
    }

    // تعديل 3: الدالة تتعامل الآن مع TransactionItem
    @Override
    public void onItemLongClick(TransactionItem transactionItem) {
        // عند الضغط المطول، نمرر المعاملة لوضع التعديل
//        showTransactionDialog(transactionItem.getTransaction());
        Transaction tx = transactionItem.getTransaction();
        String currencyName = findCurrencyNameById(tx.getCurrencyId());
        if (currencyName == null) return;
        // **المنطق الجديد: التحقق من نوع العملية**
        if (tx.getBillType() != null && !tx.getBillType().isEmpty()) {
            InvoiceDialog dialog = InvoiceDialog.newInstanceForEdit(
                    tx.getImportID(),
                    currencyName,
                    "true"
            );
            dialog.show(getSupportFragmentManager(), "InvoiceDialog");
        } else {
            // إذا كانت عملية عادية، اعرض الديالوج القديم
            showTransactionDialog(tx);
        }
    }

    @Override
    public void onEditIconClick(TransactionItem transactionItem) {
        onItemLongClick(transactionItem);
    }

    // جديد: دالة مخصصة لإعداد الإكمال التلقائي في الديالوج
    private void setupDetailsAutoCompleteInDialog(DialogAddNewTransactionBinding dialogBinding) {
        // 1. مراقبة الاقتراحات من الـ ViewModel
        viewModel.getDetailSuggestions().observe(this, suggestions -> {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_dropdown_item_1line, suggestions);
            // تأكد من أن الـ id في ملف التخطيط هو detailsEditText
            (dialogBinding.detailsEditText).setAdapter(adapter);

            int itemHeight = (int) (48 * getResources().getDisplayMetrics().density);
            dialogBinding.detailsEditText.setDropDownHeight(itemHeight * 4);
        });

        // 2. إضافة TextWatcher لإرسال نص البحث للـ ViewModel
        (dialogBinding.detailsEditText).addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String text = s.toString();
                if (!text.contains(" ")) {
                    viewModel.setDetailsQuery(text);
                } else {
                    viewModel.setDetailsQuery(null);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
    }


    private void setupBottomBar() {
        binding.currencyTextView.setOnClickListener(v -> cycleCurrency());
        binding.addTransactionButton.setOnClickListener(v -> {
            showTransactionDialog(null);

        });
        binding.smsButton.setOnClickListener(v -> shareTransaction());
    }

    // جديد: دالة لفتح تطبيق الرسائل مباشرة

    private void shareTransaction() {
        if (currentAccount == null) {
            Toast.makeText(this, getString(R.string._572), Toast.LENGTH_SHORT).show();
            return;
        }
        String currency = binding.currencyTextView.getText() != null
                ? binding.currencyTextView.getText().toString().trim() : "";
        List<TransactionItem> currentItems = historyAdapter != null ? historyAdapter.getCurrentList() : null;
        double actualBalance = (currentItems != null && !currentItems.isEmpty())
                ? currentItems.get(0).getBalanceAfter() : 0.0;
        String waMessage = ShareNotificationHelper.buildBalanceSummaryMessage(this, currency, actualBalance);
        String smsMessage = ShareNotificationHelper.buildBalanceSummarySmsMessage(this, currency, actualBalance);

        String phone = currentAccount.getPhoneNumber() != null ? currentAccount.getPhoneNumber().trim() : "";
        if (phone.isEmpty()) {
            AccountBottomSheet sheet = AccountBottomSheet.newInstanceForEditDirect(currentAccount.getId());
            sheet.setAccountActionListener(new AccountBottomSheet.AccountActionListener() {
                @Override
                public void onAccountSaved(Account savedAccount, boolean isNew) {
                    if (savedAccount == null) return;
                    currentAccount = savedAccount;
                    String savedPhone = savedAccount.getPhoneNumber() != null ? savedAccount.getPhoneNumber().trim() : "";
                    if (savedPhone.isEmpty()) return;
                    if (savedAccount.isNotifyWhatsApp()) {
                        ShareNotificationHelper.sendViaWhatsAppSilently(AccountDetailsActivity.this, savedPhone, waMessage);
                    } else {
                        ShareNotificationHelper.sendViaSms(AccountDetailsActivity.this, savedPhone, smsMessage);
                    }
                }
            });
            sheet.show(getSupportFragmentManager(), "AccountBottomSheetBalanceShare");
        } else if (currentAccount.isNotifyWhatsApp()) {
            ShareNotificationHelper.sendViaWhatsAppSilently(this, phone, waMessage);
        } else {
            ShareNotificationHelper.sendViaSms(this, phone, smsMessage);
        }
    }

    // تعديل: الدالة الآن لا تحتاج لبارامترات

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
    @SuppressLint("ClickableViewAccessibility")
    private void showTransactionDialog(@Nullable Transaction transactionToEdit) {
        // إسناد الـ Binding للمتغير العام لضمان تحديث الواجهة من الـ Launchers
        if (isAddNewTransactionBindingShowing) {
            return;
        }
        isAddNewTransactionBindingShowing = true;
        activeDialogBinding = DialogAddNewTransactionBinding.inflate(LayoutInflater.from(this));
        MyApplication.applyGlobalTextWatcher(activeDialogBinding.getRoot());
        AppFontHelper.applyFontToViewTree(this, activeDialogBinding.getRoot());

        final boolean isEditMode = transactionToEdit != null;
        String title = isEditMode ? getString(R.string.edit_transaction_title) : getString(R.string.add_transaction_title);
        tempEncodedImage = (isEditMode) ? transactionToEdit.getImageEncoded() : null;
        activeDialogBinding.dialogTitel.setText(title);

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(activeDialogBinding.getRoot());
        dialog.setOnShowListener(d -> {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundColor(Color.TRANSPARENT);
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });
        activeDialogBinding.btnCloseDialog.setOnClickListener(v -> dialog.dismiss());
        activeDialogBinding.cardLinkToBudget.setVisibility(isEditMode ? View.GONE : View.VISIBLE);
        activeDialogBinding.cardLinkToBudget.setOnClickListener(v ->
                activeDialogBinding.switchLinkToBudget.setChecked(!activeDialogBinding.switchLinkToBudget.isChecked()));

// 2. إعادة تعيين المتغير عند إغلاق الشيت بأي طريقة (حفظ، إلغاء، سحب للأسفل)
        dialog.setOnDismissListener(d -> {
            isAddNewTransactionBindingShowing = false;
        });
        // إعداد العملات
        if (!allAvailableCurrencies.isEmpty()) {
            List<String> currencyNames = allAvailableCurrencies.stream().map(c -> c.name).collect(Collectors.toList());
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_dropdown_item_1line, currencyNames);
            activeDialogBinding.currencySpinnerAutocomplete.setAdapter(adapter);
            int itemHeight = (int) (48 * getResources().getDisplayMetrics().density);
            activeDialogBinding.currencySpinnerAutocomplete.setDropDownHeight(itemHeight * 4);
        }

        // تهيئة التاريخ والتحقق الآمن من الصورة لتجنب الـ NullPointerException
        final Calendar dialogSelectedDate = Calendar.getInstance();

        // منطق التحقق المطلوب: التأكد من أن الكائن ليس null وأن الحقل يحتوي على بيانات
        tempEncodedImage = (isEditMode && transactionToEdit.getImageEncoded() != null && !transactionToEdit.getImageEncoded().isEmpty())
                ? transactionToEdit.getImageEncoded()
                : null;

        if (isEditMode) {
            dialogSelectedDate.setTime(transactionToEdit.getTimestamp());
            String currencyName = findCurrencyNameById(transactionToEdit.getCurrencyId());
            if (currencyName != null) {
                activeDialogBinding.currencySpinnerAutocomplete.setText(currencyName, false);
            }
        } else {
            updateDateInDialogView(activeDialogBinding, dialogSelectedDate);
            String selectedCurrency = binding.currencyTextView.getText().toString();
            activeDialogBinding.currencySpinnerAutocomplete.setText(selectedCurrency, false);
        }

        // إعداد واجهة المستخدم والـ TextWatcher
        setupDialogUI(activeDialogBinding, transactionToEdit, isEditMode);
        activeDialogBinding.dateEditText.setOnClickListener(v -> showDatePickerInDialog(activeDialogBinding, dialogSelectedDate));
        setupDetailsAutoCompleteInDialog(activeDialogBinding);
        FormatingAmount.applyTo(activeDialogBinding.amountEditText);

        // تحديث لون أيقونة الكاميرا إذا كانت الصورة موجودة
        if (tempEncodedImage != null) {
            activeDialogBinding.btnAttachImage.setColorFilter(Color.GREEN);
        }
        // إعداد زر الكاميرا والخيارات الديناميكية
        activeDialogBinding.btnAttachImage.setOnClickListener(v -> {
            // 1. فحص النسخة المميزة
            if (!SecureLicenseManager.getInstance(this).isPremium() && !isTrialValid()) {
                DialogHelper.show(this, getString(R.string.add_image_premium), DialogHelper.FeedbackType.ERROR, null, null);
                return;
            }

            // 2. إنشاء PopupMenu وربطه بالزر (v هو الـ btnAttachImage)
            PopupMenu popup = new PopupMenu(this, v);

            // 3. إضافة الخيارات ديناميكياً
            if (tempEncodedImage != null) {
                popup.getMenu().add(0, 1, 0, getString(R.string.show_photo));
                popup.getMenu().add(0, 4, 3, getString(R.string.delete_photo));
            }
            popup.getMenu().add(0, 2, 1, getString(R.string.camera));
            popup.getMenu().add(0, 3, 2, getString(R.string.gallary));

            // 4. معالجة النقرات على عناصر القائمة
            popup.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == 1) { // عرض الصورة
                    showImageFullScreen(tempEncodedImage);
                    return true;
                } else if (itemId == 2) { // الكاميرا
                    checkCameraPermissionAndOpen();
                    return true;
                } else if (itemId == 3) { // الاستوديو
                    photoPickerLauncher.launch(new PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                            .build());
                    return true;
                } else if (itemId == 4) {
                    // منطق حذف الصورة (حذف مؤقت)
                    tempEncodedImage = null;
                    activeDialogBinding.btnAttachImage.clearColorFilter();
                    Toast.makeText(this, getString(R.string.image_deleted), Toast.LENGTH_SHORT).show();
                }
                return false;
            });

            // 5. إظهار القائمة
            popup.show();
        });

        activeDialogBinding.amountLayout.setEndIconOnClickListener(v -> {
            String currentValue = activeDialogBinding.amountEditText.getText().toString().replaceAll(",", "");
            CalculatorDialog calculatorDialog = CalculatorDialog.newInstance(currentValue);
            calculatorDialog.setOnCalculationCompleteListener(result -> {
                if (result != null && !result.isEmpty()) {
                    String cleanString = result.replaceAll(",", "");
                    try {
                        double parsed = Double.parseDouble(cleanString);
                        String formattedResult = formatCurrency(parsed);
                        activeDialogBinding.amountEditText.setText(formattedResult);
                        activeDialogBinding.amountEditText.setSelection(formattedResult.length());
                    } catch (NumberFormatException ignored) {
                    }
                    activeDialogBinding.detailsEditText.requestFocus();
                }
            });
            calculatorDialog.show(getSupportFragmentManager(), "CalculatorDialog");
        });

        View.OnClickListener saveClickListener = v -> {
            int newType = (v.getId() == R.id.debitButton) ? 1 : -1;
            hideKeyboard(activeDialogBinding.debitButton);
            saveChanges(dialog, activeDialogBinding, transactionToEdit, newType, dialogSelectedDate);
        };

        activeDialogBinding.editButton.setOnClickListener(v -> switchToEditMode(activeDialogBinding));
        activeDialogBinding.deleteButton.setOnClickListener(v -> {
            showDeleteConfirmationDialog(transactionToEdit);
            dialog.dismiss();
        });
        activeDialogBinding.creditButton.setOnClickListener(saveClickListener);
        activeDialogBinding.debitButton.setOnClickListener(saveClickListener);

        dialog.show();
        AppFontHelper.applyFontToDialog(this, dialog);
        if (!isEditMode) {
            activeDialogBinding.amountEditText.requestFocus();
            showKeyboard(activeDialogBinding.amountEditText);
        }
    }

    private void checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }
    private boolean isTrialValid() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        long trialExpire = prefs.getLong("trial_expire", 0);
        if (trialExpire == 0) return false;
        return System.currentTimeMillis() < trialExpire;
    }
    private void openCamera() {
        File photoFile = new File(getExternalCacheDir(), "temp_image_" + System.currentTimeMillis() + ".jpg");
        photoUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", photoFile);

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri); // هنا نخبر الكاميرا بحفظ الصورة كاملة
        cameraLauncher.launch(intent);
    }

    private void showImageFullScreen(String base64) {
        Bitmap bitmap = ImageUtils.base64ToBitmap(base64);
        if (bitmap == null) return;

        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(bitmap);
        dialog.setContentView(iv);
        dialog.show();
    }

    private void processAndSetImage(Bitmap bitmap) {
        // إظهار مؤشر تحميل (اختياري)
        binding.progressBar.setVisibility(View.VISIBLE);
        imageProcessingExecutor.execute(() -> {
            try {
                // معالجة الصورة في الخلفية
                String processedImage = ImageUtils.bitmapToBase64(this, bitmap);

                // الرجوع للخيط الرئيسي لتحديث الواجهة
                runOnUiThread(() -> {
                    tempEncodedImage = processedImage;
                    activeDialogBinding.btnAttachImage.setColorFilter(Color.GREEN);
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, getString(R.string.image_attached_success), Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                e.printStackTrace();

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "خطأ في معالجة الصورة", Toast.LENGTH_SHORT).show();
                });

            }
        });
    }

    private void setupDialogKeyboardInteraction(Dialog dialog, View dialogRootView) {
        ViewCompat.setOnApplyWindowInsetsListener(dialogRootView, (v, insets) -> {
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            Insets systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int bottomPad = Math.max(imeInsets.bottom, systemBarsInsets.bottom);
            v.setPadding(
                    systemBarsInsets.left,
                    v.getPaddingTop(),
                    systemBarsInsets.right,
                    bottomPad
            );
            return insets;
        });
    }

    /**
     * Called when keyboard appears - move dialog up
     */
    private void onKeyboardShown(Dialog dialog, View rootView, int keyboardHeight, int[] originalY) {
        if (dialog.getWindow() != null && rootView != null) {
            Window window = dialog.getWindow();
            WindowManager.LayoutParams params = window.getAttributes();

            // Save original Y position if not saved yet
            if (originalY[0] == -1) {
                originalY[0] = params.y;
            }

            // Calculate new Y position to move dialog above keyboard
            int[] location = new int[2];
            rootView.getLocationOnScreen(location);
            int dialogBottom = location[1] + rootView.getHeight();
            int screenHeight = rootView.getRootView().getHeight();
            int keyboardTop = screenHeight - keyboardHeight;

            // If dialog overlaps with keyboard, move it up
            if (dialogBottom > keyboardTop) {
                int overlap = dialogBottom - keyboardTop;
                params.y = originalY[0] - overlap - 50; // Extra 50dp margin
                window.setAttributes(params);
            }
        }
    }

    /**
     * Called when keyboard disappears - return dialog to center
     */
    private void onKeyboardHidden(Dialog dialog, int[] originalY) {
        if (dialog.getWindow() != null && originalY[0] != -1) {
            Window window = dialog.getWindow();
            WindowManager.LayoutParams params = window.getAttributes();
            params.y = originalY[0];
            window.setAttributes(params);
        }
    }

    private void populateDialogWithTransactionData(DialogAddNewTransactionBinding dialogBinding, Transaction transaction) {
        NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);
        dialogBinding.accountNameEditText.setText(accountName);

//        dialogBinding.amountEditText.setText(numberFormat.format(transaction.getAmount()));
        dialogBinding.amountEditText.setText(formatCurrency(transaction.getAmount()));
        FormatingAmount.applyTo(dialogBinding.amountEditText);

        dialogBinding.detailsEditText.setText(transaction.getDetails());

        // تحديد التاريخ
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        dialogBinding.dateEditText.setText(sdf.format(transaction.getTimestamp()));
        dialogBinding.dateEditText.setText(sdf.format(transaction.getTimestamp()));

    }
    private void showKeyboard(View view) {
        if (view != null) {
            // تأخير بسيط لضمان اكتمال رسم الديالوج وحصول العنصر على التركيز
            view.postDelayed(() -> {
                view.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 100); // تأخير 100 ملي ثانية (كافي جداً للأندرويد)
        }
    }
    private String findCurrencyNameById(int currencyId) {
        for (Currency currency : allAvailableCurrencies) {
            if (currency.id == currencyId) {
                return currency.name;
            }
        }
        return null; // في حالة عدم العثور عليها
    }

    private void setupDialogUI(DialogAddNewTransactionBinding dialogBinding, @Nullable Transaction transaction, boolean isEditMode) {

        dialogAmountEditText = dialogBinding.amountEditText;
        dialogBinding.accountNameEditText.setText(accountName);

        if (isEditMode) {
            assert transaction != null;
            populateDialogWithTransactionData(dialogBinding, transaction);
            switchToViewMode(dialogBinding);
        } else {
            switchToEditMode(dialogBinding);
//            String selectedCurrency = currencies[currentCurrencyIndex];
//            currencyType(dialogBinding, selectedCurrency);
            dialogBinding.editDeleteButtonsLayout.setVisibility(View.GONE);
        }

    }
    private void saveChanges(Dialog dialog, DialogAddNewTransactionBinding dialogBinding,
                             @Nullable Transaction oldTransaction, int newType, Calendar newDate) {

        // ========== 1. قراءة القيم والتحقق منها ==========
        String amountStr = dialogBinding.amountEditText.getText().toString().trim().replaceAll("[,]", "");
        String newDetails = dialogBinding.detailsEditText.getText().toString().trim();
        String newCurrencyName = dialogBinding.currencySpinnerAutocomplete.getText().toString();
        String syncStatus = "";

        Integer currencyId = allAvailableCurrencies.stream().filter(currency -> currency.name.equals(newCurrencyName)).findFirst().map(currency -> currency.id).orElse(null);

        if (currencyId == null) {
            Toast.makeText(this, getString(R.string.ar_long_text_49), Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(amountStr) || Double.parseDouble(amountStr) == 0) {
            dialogBinding.amountEditText.setError(getString(R.string.required));
            return;
        }
        if (TextUtils.isEmpty(newDetails)) {
            dialogBinding.detailsEditText.setError(getString(R.string.required));
            return;
        }

        double newAmount = Double.parseDouble(amountStr);

        // ========== 2. 🔥 التحقق من تغيير نوع العملية في وضع التعديل ==========
        // oldTransaction != null → نحن في وضع التعديل
        if (oldTransaction != null) {
            int oldType = oldTransaction.getType(); // النوع الأصلي قبل التعديل
            if (oldType != newType) {
                // النوع تغيّر (مدين → دائن أو العكس) → عرض ديالوج التأكيد
                showTransactionTypeChangeConfirmation(oldType, newType, () -> {
                    // ✅ المستخدم ضغط "متابعة" → تنفيذ الحفظ وإغلاق ديالوج الإدخال
                    performSaveChanges(dialog, oldTransaction, newType, newDate,
                            newAmount, newDetails, currencyId, newCurrencyName, syncStatus);
                });
                // ⚠️ لا نغلق الديالوج هنا. إذا ضغط المستخدم "إلغاء"،
                // ديالوج showTransactionDialog يبقى مفتوحاً كما هو
                return;
            }
        }

        // ========== 3. نفس النوع أو وضع إضافة → متابعة الحفظ مباشرة ==========
        performSaveChanges(dialog, oldTransaction, newType, newDate,
                newAmount, newDetails, currencyId, newCurrencyName, syncStatus);
    }
    /**
     * عرض ديالوج تأكيد عند تغيير نوع العملية من مدين إلى دائن أو العكس.
     *
     * @param oldType   النوع الأصلي (1 = مدين، -1 = دائن)
     * @param newType   النوع الجديد
     * @param onConfirm يُنفَّذ فقط عند ضغط المستخدم "متابعة"
     */
    private void showTransactionTypeChangeConfirmation(int oldType, int newType, Runnable onConfirm) {

        // تحديد التسمية المناسبة لكل نوع (مدين/دائن) حسب ملف strings
        String oldTypeLabel = (oldType == 1)
                ? getString(R.string.debit)        // مدين
                : getString(R.string.credit);  // دائن

        String newTypeLabel = (newType == 1)
                ? getString(R.string.debit)        // مدين
                : getString(R.string.credit);  // دائن

        // نص الرسالة: "نوع العملية كان مدين وسيتم تغييره إلى دائن. هل تريد المتابعة؟"
        String message = getString(R.string.type_change_message, oldTypeLabel, newTypeLabel);

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.type_change_title))
                .setMessage(message)
                .setIcon(R.drawable.ic_alert)
                .setCancelable(false)
                .setPositiveButton(getString(R.string.continue_button), (d, w) -> {
                    d.dismiss();
                    if (onConfirm != null) onConfirm.run();
                })
                .setNegativeButton(getString(R.string.cancel), (d, w) -> {
                    // ✅ إغلاق ديالوج التأكيد فقط — ديالوج الإدخال الأصلي يبقى مفتوحاً
                    d.dismiss();
                })
                .show();
    }
    /**
     * تنفيذ عملية الحفظ الفعلية (تعديل أو إضافة).
     * تم فصلها من saveChanges لتُستدعى إما مباشرة أو بعد تأكيد المستخدم.
     */
    private void performSaveChanges(Dialog dialog, @Nullable Transaction oldTransaction,
                                    int newType, Calendar newDate, double newAmount,
                                    String newDetails, int currencyId, String newCurrencyName,
                                    String syncStatus) {

        // ================= وضع التعديل =================
        if (oldTransaction != null) {
            oldTransaction.setId(oldTransaction.getId());
            oldTransaction.setAccountId(oldTransaction.getAccountId());
            oldTransaction.setAmount(newAmount);
            oldTransaction.setType(newType);            // ← النوع الجديد بعد موافقة المستخدم
            oldTransaction.setDetails(newDetails);
            oldTransaction.setCurrencyId(currencyId);
            oldTransaction.setLastModified(System.currentTimeMillis());
            oldTransaction.setImageEncoded(tempEncodedImage);
            oldTransaction.setTimestamp(newDate.getTime());

            viewModel.updateTransaction(oldTransaction);
            switchToCurrency(newCurrencyName);
            viewModel.refreshTransactions();
            Toast.makeText(this, getString(R.string.transaction_updated), Toast.LENGTH_SHORT).show();

            tempEncodedImage = null;
            dialog.dismiss();
            return;
        }

        // ================= وضع الإضافة =================
        if (isGuest) {
            syncStatus = "NEW";
            if (!licenseManager.canCreateTransaction()) {
                int transactionsCount = SecureLicenseManager.getInstance(this).getTransactionsCount();
                if (transactionsCount >= 100) {
                    DialogHelper.show(this, getString(R.string.login_1),
                            DialogHelper.FeedbackType.WARNING,
                            () -> {
                                Intent loginIntent = new Intent(AccountDetailsActivity.this, LoginActivity.class);
                                loginIntent.putExtra("registerGuest", true);
                                loginLauncher.launch(loginIntent);
                                finish();
                            }, null);
                    return;
                }
                if (syncPreferences.canCreateTransaction()) {
                    showNoOperationsDialog(getString(R.string.trans_limit_titel), getString(R.string.transaction_limit));
                    syncPreferences.setCanCreateTransaction(false);
                } else if (!licenseManager.checkDailyLimit()) {
                    showNoOperationsDialog(getString(R.string.daily_limit_titel), getString(R.string.daily_limit));
                    return;
                }
                syncPreferences.setCanCreateTransaction(false);
                return;
            }
        } else {
            syncStatus = "NEW";
            if (!licenseManager.canCreateTransaction()) {
                int transactionsCount = SecureLicenseManager.getInstance(this).getTransactionsCount();
                if (isGuest && transactionsCount >= 100) {
                    DialogHelper.show(this, getString(R.string.login_1),
                            DialogHelper.FeedbackType.WARNING,
                            () -> {
                                Intent loginIntent = new Intent(AccountDetailsActivity.this, LoginActivity.class);
                                loginIntent.putExtra("registerGuest", true);
                                loginLauncher.launch(loginIntent);
                                finish();
                            }, null);
                    return;
                }
                if (syncPreferences.canCreateTransaction()) {
                    showNoOperationsDialog(getString(R.string.trans_limit_titel), getString(R.string.transaction_limit));
                    syncPreferences.setCanCreateTransaction(false);
                } else if (!licenseManager.checkDailyLimit()) {
                    showNoOperationsDialog(getString(R.string.daily_limit_titel), getString(R.string.daily_limit));
                    return;
                }
                syncPreferences.setCanCreateTransaction(false);
                return;
            }
        }

        Transaction newTransaction = new Transaction();
        newTransaction.setAccountId(accountId);
        newTransaction.setAmount(newAmount);
        newTransaction.setType(newType);
        newTransaction.setDetails(newDetails);
        newTransaction.setCurrencyId(currencyId);
        newTransaction.setSyncStatus(syncStatus);
        newTransaction.setAccountFirestoreId(currentAccount.getFirestoreId());
        newTransaction.setImageEncoded(tempEncodedImage);
        newTransaction.setTimestamp(newDate.getTime());

        new TransactionNotificationManager(this)
                .sendTransactionNotification(newTransaction, newCurrencyName, this);

        viewModel.addTransaction(newTransaction);
        switchToCurrency(newCurrencyName);
        boolean linkToBudget = activeDialogBinding != null && activeDialogBinding.switchLinkToBudget.isChecked();
        if (linkToBudget) {
            String linkedAccName = currentAccount != null && currentAccount.getAccountName() != null
                    ? currentAccount.getAccountName() : accountName;
            BudgetRepository.recordLinkedDebtTransaction(
                    this, linkedAccName, newAmount, newType, currencyId, newDate.getTimeInMillis());
        }
        this.lastSavedTransaction = newTransaction;
        licenseManager.incrementTransactionCount();
        ShareNotificationHelper.handleAfterTransactionSaved(
                this, accountId, currencyId, newCurrencyName, newTransaction);

        if (isNetworkAvailable()) {
            // المزامنة الفورية إن لزم
        }

        tempEncodedImage = null;
        dialog.dismiss();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getApplication().getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
    }

    private void showNoOperationsDialog(String titel, String textInfo) {
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
        noTransactionTextView.setText(textInfo);
        purchaseBtn.setOnClickListener(v -> {
            dialog.dismiss();

            Intent intent = new Intent(this, SubscriptionActivity.class);
            startActivity(intent);

//            handlePurchaseApp();
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


    private void watchAd() {

        // 1. إظهار البروجرس
        binding.progressBar.setVisibility(View.VISIBLE);
        Handler timeoutHandler = new Handler(Looper.getMainLooper());
        Runnable timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                // إذا وصلنا هنا، يعني أن 10 ثوانٍ مرت ولم يظهر الإعلان
                binding.progressBar.setVisibility(View.GONE);
                DialogHelper.show(AccountDetailsActivity.this,
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
                DialogHelper.show(AccountDetailsActivity.this, getString(R.string.setupListeners_02), DialogHelper.FeedbackType.ERROR, null);

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
        SecureLicenseManager.getInstance(this)
                .setAdRewards(SecureLicenseManager.getInstance(this).getAdRewards() + points);
        isGuest = SecureLicenseManager.getInstance(this).isGuest();
        String userDoc = "", userUID;
        if (isGuest) {
            userUID = SecureLicenseManager.getInstance(this).guestUID();
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



    // جديد: دالة لعرض بانر التأكيد مع حركة

    private void showDatePickerInDialog(DialogAddNewTransactionBinding dialogBinding, Calendar date) {
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            date.set(Calendar.YEAR, year);
            date.set(Calendar.MONTH, month);
            date.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            updateDateInDialogView(dialogBinding, date);
        }, date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDateInDialogView(DialogAddNewTransactionBinding dialogBinding, Calendar date) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        dialogBinding.dateEditText.setText(sdf.format(date.getTime()));
    }

    private void updateUiForSelectedCurrency() {
//        String selectedCurrency = currencies[currentCurrencyIndex];
        String selectedCurrency = binding.currencyTextView.getText().toString();
        binding.currencyTextView.setText(selectedCurrency);
        // إخبار الـ ViewModel بتغيير العملة
        viewModel.setCurrency(selectedCurrency);

    }

    public void switchToCurrency(String currencyName) {
        if (currencyName == null || currencyName.trim().isEmpty() || binding == null || viewModel == null) {
            return;
        }
        String cleanCurrency = currencyName.trim();
        if (!activeCurrenciesForAccount.contains(cleanCurrency)) {
            activeCurrenciesForAccount.add(cleanCurrency);
        }
        binding.currencyTextView.setText(cleanCurrency);
        viewModel.setCurrency(cleanCurrency);
    }
    private void switchToViewMode(DialogAddNewTransactionBinding dialogBinding) {
        dialogBinding.currencySpinnerLayout.setEnabled(false);
        dialogBinding.amountLayout.setEnabled(false);
        dialogBinding.detailsLayout.setEnabled(false);
        dialogBinding.dateLayout.setEnabled(false);
        dialogBinding.saveButtonsLayout.setVisibility(View.GONE);
        dialogBinding.editDeleteButtonsLayout.setVisibility(View.VISIBLE);
        // dialogBinding.btnAttachImage.setEnabled(false);
    }

    // دالة مساعدة للانتقال لوضع التعديل (تفعيل الحقول)
    private void switchToEditMode(DialogAddNewTransactionBinding dialogBinding) {
        dialogBinding.currencySpinnerLayout.setEnabled(true);
        dialogBinding.amountLayout.setEnabled(true);
        dialogBinding.detailsLayout.setEnabled(true);
        dialogBinding.dateLayout.setEnabled(true);
        dialogBinding.saveButtonsLayout.setVisibility(View.VISIBLE);
        dialogBinding.editDeleteButtonsLayout.setVisibility(View.GONE);
        dialogBinding.btnAttachImage.setEnabled(true);
        showKeyboard(dialogBinding.amountEditText);
    }

    private void showDeleteConfirmationDialog(Transaction transactionToDelete) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_confirmation_title)
                .setMessage(R.string.delete_confirmation_message)
                .setIcon(R.drawable.ic_alert)
                // تغيير لون العنوان إلى الأحمر (يتطلب تعريف اللون في colors.xml)
                // .setTitleTextColor(ContextCompat.getColor(this, R.color.red_error))
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    // لا تفعل شيئًا عند الإلغاء
                })
                .setPositiveButton(R.string.yes_delete, (dialog, which) -> {
                    // عند التأكيد، اطلب من الـ ViewModel حذف العملية
                    viewModel.deleteTransaction(transactionToDelete);
                    //    Toast.makeText(this, getString(R.string._1175), Toast.LENGTH_SHORT).show();
                })
                .show();
    }
    private void showProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            TextView tv = progressDialog.findViewById(R.id.tv_message);
            if (tv != null) tv.setText(getString(R.string._1277));
            return;
        }
        runOnUiThread(() -> {
            try {
                androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
                View view = getLayoutInflater().inflate(R.layout.dialog_loading, null);
                TextView tvMessage = view.findViewById(R.id.tv_message);
                tvMessage.setText(getString(R.string._1277));

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

    private void writePdfToUri(Uri uri) {
        List<TransactionItem> currentList = historyAdapter.getCurrentList();
        if (currentList.isEmpty()) {
            Toast.makeText(this, getString(R.string._1272), Toast.LENGTH_SHORT).show();
            return;
        }

        showProgressDialog();

//        String currency = currencies[currentCurrencyIndex];
        String currency = binding.currencyTextView.getText().toString();
        String totalDebit = binding.totalDebitTextView.getText().toString();
        String totalCredit = binding.totalCreditTextView.getText().toString();
        String totalBalance = binding.totalBalanceTextView.getText().toString();

        new Thread(() -> {
            boolean success = false;
            try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                if (outputStream != null) {
//                    success = PdfGenerator.createPdf(outputStream, accountName, currency, currentList, totalDebit, totalCredit, totalBalance);
                }
            } catch (Exception e) {
                Log.e("PDF_SAVE", "Failed to write to URI", e);
            }

            boolean finalSuccess = success;
            runOnUiThread(() -> {
                hideProgressDialog();
                if (finalSuccess) {
                    Toast.makeText(this, getString(R.string._1300), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, getString(R.string._1302), Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }
    private void showReportsDialog() {
        if (currentAccount == null) return;
        String currency = binding.currencyTextView.getText().toString();

        // استدعاء BottomSheet الجديد بالوضع SINGLE_ACCOUNT
        ReportsBottomSheet reportsSheet = ReportsBottomSheet.newInstanceForAccount(
                currentAccount.getId(),
                currentAccount.getAccountName(),
                currency,
                currentAccount.getPhoneNumber()
        );

        reportsSheet.show(getSupportFragmentManager(), "ReportsSheetSingle");
    }
    private void checkRecurringOperation(Account account) {
        // التحقق من SharedPreferences إذا كان قد تم عرض الفقاعة لهذا الحساب اليوم
        SharedPreferences prefs = getSharedPreferences("recurring_bubble", MODE_PRIVATE);
        String key = "account_" + account.getId() + "_last_shown";
        long lastShown = prefs.getLong(key, 0);
        long today = getStartOfDay(System.currentTimeMillis());
        if (lastShown == today) {
            return;
        }

        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<RecurringOperation> operations = repository.getRecurringOperationsForAccount(account.getId());
            for (RecurringOperation op : operations) {
                if (shouldShowBubble(op)) {
                    runOnUiThread(() -> showRecurringBubble(op));
                    prefs.edit().putLong(key, today).apply();
                    break;
                }
            }
        });
    }

    private boolean shouldShowBubble(RecurringOperation op) {
        if (!op.isActive()) return false;
        long today = getStartOfDay(System.currentTimeMillis());
        if (today < op.getStartDate() || (op.getEndDate() != 0 && today > op.getEndDate()))
            return false;
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(today);
        if (op.getRecurrenceType().equals("day")) {
            // always true
        } else if (op.getRecurrenceType().equals("week")) {
            int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
            if (dayOfWeek != op.getRecurrenceDay()) return false;
        } else if (op.getRecurrenceType().equals("month")) {
            int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
            if (dayOfMonth != op.getRecurrenceDay()) return false;
        }
        return !recurringOperationsViewModel.hasBeenProcessedToday(op.getId());
    }

    private long getStartOfDay(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private void showRecurringBubble(RecurringOperation operation) {
        if (recurringBubble != null) return;
        recurringBubble = LayoutInflater.from(this).inflate(R.layout.bubble_recurring, null);

        // ✅ تعيين LayoutParams لتوسيط الفقاعة
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        recurringBubble.setLayoutParams(params);

        ViewGroup parent = findViewById(android.R.id.content);
        parent.addView(recurringBubble);


        Button btnApply = recurringBubble.findViewById(R.id.btn_add);
        Button btnIgnore = recurringBubble.findViewById(R.id.btn_dismiss);
        TextView tvMessage = recurringBubble.findViewById(R.id.tv_message);
        String recurrenceText = "";
        switch (operation.getRecurrenceType()) {
            case "day":
                recurrenceText = getString(R.string.daily);
                break;
            case "week":
                recurrenceText = getString(R.string.weekly);
                break;
            case "month":
                recurrenceText = getString(R.string.monthly);
                break;
        }

        tvMessage.setText(getString(R.string.recurring_bubble_message,
                recurrenceText, formatCurrency(operation.getAmount()), findCurrencyNameById(operation.getCurrencyId())));

        btnApply.setOnClickListener(v -> {
            showAddTransactionDialogFromRecurring(operation);
            removeBubble();
        });
        btnIgnore.setOnClickListener(v -> {
            markRecurringProcessed(operation);
            removeBubble();
        });

        recurringBubble.setAlpha(0f);
        recurringBubble.setScaleX(0.8f);
        recurringBubble.setScaleY(0.8f);
        recurringBubble.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .start();
    }

    private void removeBubble() {
        if (recurringBubble != null) {
            ViewGroup parent = (ViewGroup) recurringBubble.getParent();
            if (parent != null) parent.removeView(recurringBubble);
            recurringBubble = null;
        }
    }

    private void showAddTransactionDialogFromRecurring(RecurringOperation op) {
        // استخدام الديالوج الموجود dialog_add_new_transaction.xml
        DialogAddNewTransactionBinding dialogBinding = DialogAddNewTransactionBinding.inflate(getLayoutInflater());
        AppFontHelper.applyFontToViewTree(this, dialogBinding.getRoot());
        dialogBinding.accountNameEditText.setText(op.getAccountName());
        dialogBinding.amountEditText.setText(formatCurrency(op.getAmount()));
        dialogBinding.detailsEditText.setText(op.getDetails());
        dialogBinding.currencySpinnerAutocomplete.setText(findCurrencyNameById(op.getCurrencyId()), false);
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        dialogBinding.dateEditText.setText(sdf.format(new Date()));
        dialogBinding.cardLinkToBudget.setOnClickListener(v ->
                dialogBinding.switchLinkToBudget.setChecked(!dialogBinding.switchLinkToBudget.isChecked()));

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(dialogBinding.getRoot());
        dialogBinding.btnCloseDialog.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnShowListener(d -> {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundColor(Color.TRANSPARENT);
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });

        dialogBinding.creditButton.setOnClickListener(v -> {
            // حفظ العملية بنوع -1
            saveTransactionFromRecurring(op, -1, dialogBinding, dialog);
        });
        dialogBinding.debitButton.setOnClickListener(v -> {
            // حفظ العملية بنوع 1
            saveTransactionFromRecurring(op, 1, dialogBinding, dialog);
        });

        dialog.show();
    }

    private void saveTransactionFromRecurring(RecurringOperation op, int type,
                                              DialogAddNewTransactionBinding dialogBinding,
                                              Dialog dialog) {
        String amountStr = dialogBinding.amountEditText.getText().toString().trim().replaceAll(",", "");
        String details = dialogBinding.detailsEditText.getText().toString().trim();
        String currencyName = dialogBinding.currencySpinnerAutocomplete.getText().toString();
        Integer currencyId = findCurrencyIdByName(currencyName);
        if (currencyId == null) {
            Toast.makeText(this, R.string.wronge_currency, Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(amountStr) || Double.parseDouble(amountStr) == 0) {
            dialogBinding.amountEditText.setError(getString(R.string.required));
            return;
        }
        if (TextUtils.isEmpty(details)) {
            dialogBinding.detailsEditText.setError(getString(R.string.required));
            return;
        }
        if (isGuest) {
            if (!licenseManager.canCreateTransaction()) {
                int transactionsCount = SecureLicenseManager.getInstance(this).getTransactionsCount();
                if (isGuest && transactionsCount >= 100) {// LicenseManager.FREE_TRANSACTION_LIMIT) {
                    DialogHelper.show(
                            this,
                            getString(R.string.login_1),
                            DialogHelper.FeedbackType.WARNING,
                            () -> {
                                Intent loginIntent = new Intent(AccountDetailsActivity.this, LoginActivity.class);
                                loginIntent.putExtra("registerGuest", true);
                                loginLauncher.launch(loginIntent);
                                finish();
                            },
                            null);
                    return; // التوقف لمنع إكمال عملية الحفظ
                }

                // المنطق الحالي للتعامل مع القيود الأخرى في حال عدم تحقق الشرط أعلاه
                if (syncPreferences.canCreateTransaction()) {
                    showNoOperationsDialog(getString(R.string.trans_limit_titel), getString(R.string.transaction_limit));
                    syncPreferences.setCanCreateTransaction(false);
                } else if (!licenseManager.checkDailyLimit()) {
                    showNoOperationsDialog(getString(R.string.daily_limit_titel), getString(R.string.daily_limit));
                    return;
                }
                syncPreferences.setCanCreateTransaction(false);
                return;
            }
        } else {


            if (!licenseManager.canCreateTransaction()) {
                int transactionsCount = SecureLicenseManager.getInstance(this).getTransactionsCount();
                if (isGuest && transactionsCount >= 100) {// LicenseManager.FREE_TRANSACTION_LIMIT) {
                    DialogHelper.show(
                            this,
                            getString(R.string.login_1),
                            DialogHelper.FeedbackType.WARNING,
                            () -> {
                                Intent loginIntent = new Intent(AccountDetailsActivity.this, LoginActivity.class);
                                loginIntent.putExtra("registerGuest", true);
                                loginLauncher.launch(loginIntent);
                                finish();
                            },
                            null);
                    return; // التوقف لمنع إكمال عملية الحفظ
                }

                // المنطق الحالي للتعامل مع القيود الأخرى في حال عدم تحقق الشرط أعلاه
                if (syncPreferences.canCreateTransaction()) {
                    showNoOperationsDialog(getString(R.string.trans_limit_titel), getString(R.string.transaction_limit));
                    syncPreferences.setCanCreateTransaction(false);
                } else if (!licenseManager.checkDailyLimit()) {
                    showNoOperationsDialog(getString(R.string.daily_limit_titel), getString(R.string.daily_limit));
                    return;
                }
                syncPreferences.setCanCreateTransaction(false);
                return;
            }
        }
        double amount = Double.parseDouble(amountStr);
        // إنشاء Transaction
        Transaction transaction = new Transaction();
        transaction.setAccountId(op.getAccountId());
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setDetails(details);
        transaction.setCurrencyId(currencyId);
        transaction.setSyncStatus("NEW");
        transaction.setTimestamp(new Date());
        // حفظ العملية
        viewModel.addTransaction(transaction);
        switchToCurrency(currencyName);
        if (dialogBinding.switchLinkToBudget.isChecked()) {
            BudgetRepository.recordLinkedDebtTransaction(
                    this, op.getAccountName(), amount, type, currencyId, System.currentTimeMillis());
        }
        this.lastSavedTransaction = transaction;
        licenseManager.incrementTransactionCount();
        ShareNotificationHelper.handleAfterTransactionSaved(
                this, op.getAccountId(), currencyId, currencyName, transaction);
//        Toast.makeText(this, R.string.transaction_added, Toast.LENGTH_SHORT).show();
        dialog.dismiss();
        markRecurringProcessed(op);
    }

    private void markRecurringProcessed(RecurringOperation operation) {
        recurringOperationsViewModel.markProcessed(operation.getId());
    }


    private Integer findCurrencyIdByName(String name) {
        for (Currency c : allAvailableCurrencies) {
            if (c.name.equals(name)) return c.id;
        }
        return null;
    }

}

