package com.hpp.daftree.ui;

import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.PickVisualMediaRequest;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.hpp.daftree.viewmodels.AccountTypeViewModel;
import com.hpp.daftree.models.AppAuth;
import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.models.TransactionItem;
import com.hpp.daftree.adapters.TransactionHistoryAdapter;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.RecurringOperation;
import com.hpp.daftree.database.Transaction;
import com.hpp.daftree.database.User;
import com.hpp.daftree.databinding.ActivityAddTransactionBinding;
import com.hpp.daftree.databinding.DialogAddAccountBinding;
import com.hpp.daftree.databinding.DialogEditAccountBinding;
import com.hpp.daftree.databinding.DialogShareOptionsBinding;
import com.hpp.daftree.dialogs.CalculatorDialog;
import com.hpp.daftree.models.AutoResizeTextHelper;
import com.hpp.daftree.budget.repository.BudgetRepository;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.models.FormatingAmount;
import com.hpp.daftree.notifications.TransactionNotificationManager;
import com.hpp.daftree.syncmanagers.SyncPreferences;
import com.hpp.daftree.utils.AdsManager;
import com.hpp.daftree.utils.DialogHelper;
import com.hpp.daftree.utils.ImageUtils;
import com.hpp.daftree.utils.LicenseManager;
import com.hpp.daftree.utils.ReferralManager;
import com.hpp.daftree.utils.SecureLicenseManager;
import com.hpp.daftree.viewmodels.AddTransactionViewModel;
import com.hpp.daftree.viewmodels.CurrencyViewModel;

import java.io.File;
import java.io.InputStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.hpp.daftree.viewmodels.RecurringOperationsViewModel;

public class AddTransactionActivity extends BaseActivity implements CalculatorDialog.OnCalculationCompleteListener {

    private static final String TAG = "AddTransactionActivity";
    private static final int LOGIN_REQUEST_CODE = 1001;
    private ActivityAddTransactionBinding binding;
    private RecurringOperationsViewModel recurringOperationsViewModel;
    private View bubbleView;
    private RecurringOperation currentRecurringOperation;
    private AddTransactionViewModel viewModel;
    private final ExecutorService imageProcessingExecutor = Executors.newSingleThreadExecutor();
    private Uri photoUri;
    private FirebaseAnalytics mFirebaseAnalytics;
    private TransactionHistoryAdapter historyAdapter;
    private SyncPreferences syncPreferences;
    private final Calendar selectedDate = Calendar.getInstance();
    private Transaction lastSavedTransaction;
    private DaftreeRepository repository;
    private CurrencyViewModel currencyViewModel;
    private final List<Currency> availableCurrencies = new ArrayList<>();
    private AccountTypeViewModel accountTypeViewModel;
    private List<AccountType> availableAccountTypes = new ArrayList<>();
    private User currentUser;
    private LicenseManager licenseManager;
    private boolean isGuest;
    private String guestUID;
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private  AdsManager adsManager;
    // --- المتغيرات الجديدة للمنطق المحسّن ---
    private final Map<String, Integer> accountNameToIdMap = new HashMap<>();
    private List<Account> allAccounts = new ArrayList<>();
    private ArrayAdapter<String> accountSuggestionsAdapter;
    private ArrayAdapter<String> detailSuggestionsAdapter;
    private boolean isSelectionFromList = false;
    private DialogAddAccountBinding activeAddAccountDialogBinding;
    private String encodedImage = null; // لتخزين الصورة مؤقتاً
    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> photoPickerLauncher;
    // 1. Launcher لطلب إذن جهات الاتصال
    private final ActivityResultLauncher<String> requestContactPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    openContactPicker();
                } else {
                    Toast.makeText(this, getString(R.string.error_contact_permission), Toast.LENGTH_SHORT).show();
                }
            });

    // 2. Launcher لفتح جهات الاتصال واستلام النتيجة
    private final ActivityResultLauncher<Intent> pickContactLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri contactUri = result.getData().getData();
                    processSelectedContact(contactUri);
                }
            });

    // دالة مساعدة لفتح جهات الاتصال
    private void openContactPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        pickContactLauncher.launch(intent);
    }

    // دالة لمعالجة البيانات القادمة من جهات الاتصال
    private void processSelectedContact(Uri dataUri) {
        if (activeAddAccountDialogBinding == null) return;

        // الأعمدة التي نريد قراءتها مباشرة (الاسم والرقم)
        String[] projection = new String[]{
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };

        try (Cursor cursor = getContentResolver().query(dataUri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                // 1. استخراج الرقم المختار مباشرة
                int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                String number = cursor.getString(numberIndex);

                // 2. استخراج الاسم
                int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                String name = cursor.getString(nameIndex);

                // تعيين القيم في الحقول
                if (name != null) {
                    activeAddAccountDialogBinding.editAccountName.setText(name);
                }

                if (number != null) {
                    // تنظيف الرقم (حذف المسافات، الشرطات، والأقواس)
                    String cleanNumber = number.replaceAll("[\\s\\-()]", "");
                    activeAddAccountDialogBinding.editAccountPhone.setText(cleanNumber);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error picking contact phone", e);
            Toast.makeText(this, "حدث خطأ أثناء جلب الرقم", Toast.LENGTH_SHORT).show();
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
        super.onCreate(savedInstanceState);
       binding = ActivityAddTransactionBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());
        setupFloatingToolbar(binding.inputFieldsScrollView);
    View rootView = binding.getRoot();
        AutoResizeTextHelper mTextResizeHelper = new AutoResizeTextHelper(rootView);
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        // إضافة جميع العناصر النصية يدوياً
        mTextResizeHelper.addViews(
                binding.creditButton,binding.debitButton,binding.dateEditText);
  MyApplication.applyGlobalTextWatcher(binding.getRoot());

        adsManager = new AdsManager(this);
        isGuest = SecureLicenseManager.getInstance(this).isGuest();
        guestUID = SecureLicenseManager.getInstance(this).guestUID();

        viewModel = new ViewModelProvider(this).get(AddTransactionViewModel.class);
        currencyViewModel = new ViewModelProvider(this).get(CurrencyViewModel.class);
        accountTypeViewModel = new ViewModelProvider(this).get(AccountTypeViewModel.class);
        recurringOperationsViewModel = new ViewModelProvider(this).get(RecurringOperationsViewModel.class);
        syncPreferences = new SyncPreferences(this);
        repository = new DaftreeRepository(getApplication());
        licenseManager = new LicenseManager(this);

        setupInitialState();
        setupRecyclerView();
        setupCurrencySpinner();
        setupObservers();
        setupEventListeners(); // ✅ دالة واحدة مركزية ومُنقَّحة
        FormatingAmount.applyTo(binding.amountEditText);

        handleIntentExtras();
        setupModernImageLaunchers();

        // ربط زر الكاميرا وبطاقة الربط الاختياري بالميزانية
        binding.btnAttachImage.setOnClickListener(v -> onImageIconClicked());
        binding.cardLinkToBudget.setOnClickListener(v ->
                binding.switchLinkToBudget.setChecked(!binding.switchLinkToBudget.isChecked()));
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
                        encodedImage = processedImage;
                        binding.btnAttachImage.setColorFilter(Color.GREEN);
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

    private void setupModernImageLaunchers() {
        // مشغل إذن الكاميرا
        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        openCamera();
                    } else {
                            checkCameraPermissionAndOpen();
                    }
                }
        );

        // مشغل التقاط الصورة من الكاميرا
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

        // مشغل اختيار الصور (Photo Picker) - لا يحتاج أذونات في أندرويد 13+
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
    }


    // 3. فحص إذن الكاميرا يدوياً
    private void checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void openCamera() {
        File photoFile = new File(getExternalCacheDir(), "temp_image_" + System.currentTimeMillis() + ".jpg");
        photoUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", photoFile);

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri); // هنا نخبر الكاميرا بحفظ الصورة كاملة
        cameraLauncher.launch(intent);
    }
    private boolean isTrialValid() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        long trialExpire = prefs.getLong("trial_expire", 0);
        if (trialExpire == 0) return false;
        return System.currentTimeMillis() < trialExpire;
    }
    private void onImageIconClicked() {
        // 1. فحص البريميوم (استخدم المتغير الموجود في تطبيقك)
        if (!SecureLicenseManager.getInstance(this).isPremium() && !isTrialValid()) {
            DialogHelper.show(this, getString(R.string.add_image_premium), DialogHelper.FeedbackType.ERROR, null, null);

            return;
        }

        // 2. إنشاء PopupMenu وربطه بالزر (v هو الـ btnAttachImage)
        PopupMenu popup = new PopupMenu(this, binding.btnAttachImage);

        if (encodedImage != null) {
            popup.getMenu().add(0, 1, 0, getString(R.string.show_photo));
            popup.getMenu().add(0, 4, 3, getString(R.string.delete_photo));
        }
        popup.getMenu().add(0, 2, 1, getString(R.string.camera));
        popup.getMenu().add(0, 3, 2, getString(R.string.gallary));

        // 4. معالجة النقرات على عناصر القائمة
        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == 1) { // عرض الصورة
                showImageFullScreen(encodedImage);
                return true;
            } else if (itemId == 2) { // الكاميرا
                checkCameraPermissionAndOpen();
                return true;
            } else if (itemId == 3) { // الاستوديو
                photoPickerLauncher.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build());
                return true;
            }else if (itemId == 4) {
                // منطق حذف الصورة (حذف مؤقت)
                encodedImage = null;
                binding.btnAttachImage.clearColorFilter();
                Toast.makeText(this, getString(R.string.image_deleted), Toast.LENGTH_SHORT).show();
            }
            return false;
        });

        // 5. إظهار القائمة
        popup.show();


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
    private void handleIntentExtras() {
        if (getIntent().hasExtra("ACCOUNT_ID")) {
            int passedAccountId = getIntent().getIntExtra("ACCOUNT_ID", -1);
            String passedAccountName = getIntent().getStringExtra("ACCOUNT_NAME");
            String passedCurrency = getIntent().getStringExtra("CURRENCY");

            if (passedAccountId != -1) {
                binding.accountNameAutoComplete.setText(passedAccountName);
                binding.accountNameAutoComplete.setEnabled(false);
                viewModel.setSelectedAccount(passedAccountId);
                viewModel.setSelectedCurrency(passedCurrency);
                binding.amountEditText.requestFocus();
                new Handler(Looper.getMainLooper()).postDelayed(() -> viewModel.updateTransactionSource(), 300);
            }
        } else {
            String passedCurrency = getIntent().getStringExtra("CURRENCY");
            viewModel.setSelectedCurrency(passedCurrency);
        }
    }

    private void setupInitialState() {
        updateDateInView();
        accountSuggestionsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        binding.accountNameAutoComplete.setAdapter(accountSuggestionsAdapter);
        binding.accountNameAutoComplete.setThreshold(1);

        detailSuggestionsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        binding.detailsEditText.setAdapter(detailSuggestionsAdapter);
        binding.detailsEditText.setThreshold(1);

        binding.accountNameAutoComplete.requestFocus();
        showKeyboard(binding.accountNameAutoComplete);
    }

    private void setupRecyclerView() {
        historyAdapter = new TransactionHistoryAdapter(this, null);
        binding.transactionsHistoryRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.transactionsHistoryRecyclerView.setAdapter(historyAdapter);
    }

    private void setupCurrencySpinner() {
        currencyViewModel.getAllCurrencies().observe(this, currencies -> {
            if (currencies != null && !currencies.isEmpty()) {
                availableCurrencies.clear();
                availableCurrencies.addAll(currencies);
                List<String> currencyNames = availableCurrencies.stream().map(c -> c.name).collect(Collectors.toList());
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, currencyNames);
                binding.currencySpinnerAutocomplete.setAdapter(adapter);

                binding.currencySpinnerAutocomplete.setOnItemClickListener((parent, view, position, id) -> {
                    String selectedCurrency = (String) parent.getItemAtPosition(position);
                    viewModel.setSelectedCurrency(selectedCurrency);
                });

                String passedCurrency = getIntent().getStringExtra("CURRENCY");
                if (passedCurrency != null && currencyNames.contains(passedCurrency)) {
                    binding.currencySpinnerAutocomplete.setText(passedCurrency, false);
                    viewModel.setSelectedCurrency(passedCurrency);
                } else if (!currencyNames.isEmpty()) {
                    binding.currencySpinnerAutocomplete.setText(currencyNames.get(0), false);
                    viewModel.setSelectedCurrency(currencyNames.get(0));
                }
                viewModel.initCurrencyMap(availableCurrencies);
            }
        });
    }

    private void setupObservers() {
        viewModel.getUserProfile().observe(this, user -> this.currentUser = user);

        viewModel.getAllAccounts().observe(this, accounts -> {
            if (accounts != null) {
                this.allAccounts = accounts;
                updateAccountMaps(accounts);
                Log.d(TAG, "Account list updated. Total accounts: " + allAccounts.size());
            }
        });

        viewModel.getSelectedTransactionItems().observe(this, transactionItems -> {
            if (historyAdapter != null) {
                historyAdapter.submitList(transactionItems, () -> {
                    if (transactionItems != null && !transactionItems.isEmpty()) {
                        binding.transactionsHistoryRecyclerView.smoothScrollToPosition(0);
                    }
                });
            }
        });

        viewModel.getDetailSuggestions().observe(this, suggestions -> {
            detailSuggestionsAdapter.clear();
            if (suggestions != null && !suggestions.isEmpty()) {
                detailSuggestionsAdapter.addAll(suggestions);
            }
            detailSuggestionsAdapter.notifyDataSetChanged();
        });

        Observer<Object> transactionSourceObserver = o -> viewModel.updateTransactionSource();
        viewModel.getSelectedAccount().observe(this, transactionSourceObserver);
//        viewModel.getSelectedCurrency().observe(this, transactionSourceObserver);
          viewModel.getSelectedAccount().observe(this, accountId -> {
            if (accountId != null && accountId != -1) {
                // هنا نفحص العملية الدورية
                checkRecurringOperationForAccount(accountId);
            }
        });
    }

    private void updateAccountMaps(List<Account> accounts) {
        accountNameToIdMap.clear();
        String currentUID = isGuest ? guestUID : AppAuth.getUserID(this);

                //(FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "");
        if (currentUID.isEmpty()) return;

        for (Account account : accounts) {
            if (currentUID.equals(account.getOwnerUID()) && account.getAccountName() != null) {
                accountNameToIdMap.put(account.getAccountName(), account.getId());
            }
        }
    }

    private void setupEventListeners() {
        binding.dateEditText.setOnClickListener(v -> showDatePicker());

        // --- المستمع المحسّن لحقل اسم الحساب ---
        binding.accountNameAutoComplete.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (isSelectionFromList) return;
                String query = s.toString();
                String normalizedText = normalizeText(s.toString());
                filterAccountSuggestions(query);
            }
        });

        binding.accountNameAutoComplete.setOnItemClickListener((parent, view, position, id) -> {
            String selectedName = (String) parent.getItemAtPosition(position);
            Integer accountId = accountNameToIdMap.get(selectedName);

            if (accountId != null) {
                isSelectionFromList = true;
                binding.accountNameAutoComplete.setText(selectedName);
                binding.accountNameAutoComplete.setSelection(selectedName.length());
                isSelectionFromList = false;

                viewModel.setSelectedAccount(accountId);
                binding.amountEditText.requestFocus();
                showKeyboard(binding.amountEditText);
            }
        });

        binding.accountNameAutoComplete.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE) {
                handleAccountNameAction();
                return true;
            }
            return false;
        });

        binding.accountNameAutoComplete.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                // تأخير بسيط للسماح لـ onItemClick بالعمل أولاً إذا تم الضغط على عنصر
                new Handler(Looper.getMainLooper()).postDelayed(this::handleAccountNameAction, 150);
            } else {
                binding.accountNameAutoComplete.selectAll();
            }
        });

        // --- المستمع المحسّن لحقل التفاصيل ---
        binding.detailsEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (isSelectionFromList) return;
                String normalizedText = s.toString();//normalizeText(s.toString());
//                if (!normalizedText.contains(" ")) {
                    if (!normalizedText.isEmpty()) {
                    viewModel.setDetailsQuery(normalizedText);
                } else {
                    viewModel.setDetailsQuery(null);
                }
            }
        });

        binding.detailsEditText.setOnItemClickListener((parent, view, position, id) -> {
            isSelectionFromList = true;
            String selectedDetail = (String) parent.getItemAtPosition(position);
            binding.detailsEditText.setText(selectedDetail);
            binding.detailsEditText.setSelection(selectedDetail.length());
            isSelectionFromList = false;
            hideKeyboard();
        });

        binding.detailsEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard();
                return true;
            }
            return false;
        });

        // --- مستمعي الأزرار الأخرى ---
        binding.amountEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                binding.detailsEditText.requestFocus();
                return true;
            }
            return false;
        });

        binding.debitButton.setOnClickListener(v -> validateAndSaveTransaction(1));
        binding.creditButton.setOnClickListener(v -> validateAndSaveTransaction(-1));

        binding.amountLayout.setEndIconOnClickListener(v -> {
            String currentValue = binding.amountEditText.getText().toString().replaceAll("[,]", "");
            CalculatorDialog.newInstance(currentValue).show(getSupportFragmentManager(), "CalculatorDialog");
        });
    }

    private void handleAccountNameAction() {
        // التحقق من أن الواجهة لا تزال نشطة
        if (isFinishing() || isDestroyed()) {
            return;
        }
        // التحقق من أن التركيز ليس على القائمة المنسدلة
        if (binding.accountNameAutoComplete.isPopupShowing()) {
            return;
        }

        String accountName = binding.accountNameAutoComplete.getText().toString().trim();
        if (TextUtils.isEmpty(accountName)) return;

        if (isAccountExists(accountName)) {
            Integer accountId = accountNameToIdMap.get(accountName);
            if (accountId != null && (viewModel.getSelectedAccount().getValue() == null || !viewModel.getSelectedAccount().getValue().equals(accountId))) {
                viewModel.setSelectedAccount(accountId);
            }
            // لا تنقل التركيز إذا كان المستخدم قد انتقل بالفعل إلى حقل آخر
            if (binding.accountNameAutoComplete.hasFocus()) {
                binding.amountEditText.requestFocus();
            }
        } else {
            showAddNewAccountDialog(accountName, true);
        }
    }

    private void filterAccountSuggestions(String query) {
        accountSuggestionsAdapter.clear();
        if (query == null || query.isEmpty()) {
            accountSuggestionsAdapter.notifyDataSetChanged();
            return;
        }

        List<String> filteredNames = allAccounts.stream()
                .map(Account::getAccountName)
                .filter(name -> name != null && name.toLowerCase().contains(query.toLowerCase()))
                .collect(Collectors.toList());

        if (!filteredNames.isEmpty()) {
            accountSuggestionsAdapter.addAll(filteredNames);
        }
        accountSuggestionsAdapter.notifyDataSetChanged();
    }

    private String normalizeText(String text) {
        if (text == null || text.length() < 2) {
            return text;
        }
        for (int i = (text.length() / 2) + 1; i > 0; i--) {
            String candidate = text.substring(text.length() - i);
            StringBuilder rebuiltSequence = new StringBuilder();
            for (int j = 1; j <= candidate.length(); j++) {
                rebuiltSequence.append(candidate.substring(0, j));
            }
            if (text.endsWith(rebuiltSequence.toString())) {
                return candidate;
            }
        }
        return text;
    }

    private boolean isAccountExists(String accountName) {
        if (TextUtils.isEmpty(accountName)) return false;
        return accountNameToIdMap.containsKey(accountName.trim());
    }

    private void validateAndSaveTransaction(int type) {
        hideKeyboard();


        if (!licenseManager.canCreateTransaction()) {
            int transactionsCount = SecureLicenseManager.getInstance(this).getTransactionsCount();
            if (isGuest && transactionsCount >=100){// LicenseManager.FREE_TRANSACTION_LIMIT) {
                DialogHelper.show(
                        this,
                        getString(R.string.login_1),
                        DialogHelper.FeedbackType.WARNING,
                        () -> {
                            Intent loginIntent = new Intent(AddTransactionActivity.this, LoginActivity.class);
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

        String accountName = binding.accountNameAutoComplete.getText().toString().trim();
        String amountStr = binding.amountEditText.getText().toString().trim().replaceAll(",", "");
        String details = binding.detailsEditText.getText().toString().trim();
        String currencyName = binding.currencySpinnerAutocomplete.getText().toString();

        if (TextUtils.isEmpty(accountName) || !isAccountExists(accountName)) {
            showIfAccountNull(() -> showAddNewAccountDialog(accountName, false));
            return;
        }
        if (TextUtils.isEmpty(amountStr) || Double.parseDouble(amountStr) == 0) {
            binding.amountEditText.setError(getString(R.string.amount_required));
            return;
        }
        if (TextUtils.isEmpty(details)) {
            binding.detailsEditText.setError(getString(R.string.details_required));
            return;
        }
        if (TextUtils.isEmpty(currencyName)) {
            binding.currencySpinnerLayout.setError("يجب اختيار عملة");
            return;
        }

        Integer currencyId = availableCurrencies.stream()
                .filter(c -> c.name.equals(currencyName)).findFirst().map(c -> c.id).orElse(null);
        if (currencyId == null) {
            Toast.makeText(this, getString(R.string.wronge_currency), Toast.LENGTH_SHORT).show();
            return;
        }

        Integer accountId = accountNameToIdMap.get(accountName);
        double amount = Double.parseDouble(amountStr);
        final boolean shouldLinkToBudget = binding.switchLinkToBudget.isChecked();

        AppDatabase.databaseWriteExecutor.execute(() -> {
            long dateWithoutTime = getStartOfDay(selectedDate.getTime()).getTime();
            int duplicateCount = repository.findDuplicateTransaction(accountId, dateWithoutTime, amount, type, currencyId);
            runOnUiThread(() -> {
                if (duplicateCount > 0) {
                    showDuplicateWarningDialog(() -> saveTransaction(type, accountName, amount, details, currencyId, shouldLinkToBudget));
                } else {
                    saveTransaction(type, accountName, amount, details, currencyId, shouldLinkToBudget);
                }
            });
        });
    }

    private void saveTransaction(int type, String accountName, double amount, String details, int currencyId, boolean shouldLinkToBudget) {
        Integer accountId = accountNameToIdMap.get(accountName);
        if (accountId == null) return;
// 1. الحصول على الوقت الفعلي الحالي لحظة الضغط على حفظ (للمزامنة وتاريخ الإضافة الفعلي)
        long actualSaveTimeMillis = System.currentTimeMillis();
        Calendar currentActualTime = Calendar.getInstance();
        currentActualTime.setTimeInMillis(actualSaveTimeMillis);

// 2. استنساخ التاريخ المختار (selectedDate) لكي لا نغير قيمته الأصلية في الشاشة
        Calendar transactionCalendar = (Calendar) selectedDate.clone();
        transactionCalendar.set(Calendar.HOUR_OF_DAY, currentActualTime.get(Calendar.HOUR_OF_DAY));
        transactionCalendar.set(Calendar.MINUTE, currentActualTime.get(Calendar.MINUTE));
        transactionCalendar.set(Calendar.SECOND, currentActualTime.get(Calendar.SECOND));
        transactionCalendar.set(Calendar.MILLISECOND, currentActualTime.get(Calendar.MILLISECOND));

// 4. توليد كائن Date ليتوافق مع دالة setTimestamp
        Date transactionDate = transactionCalendar.getTime();

        Transaction newTransaction = new Transaction();
        newTransaction.setAccountId(accountId);
        newTransaction.setAmount(amount);
        newTransaction.setType(type);
        newTransaction.setImageEncoded(encodedImage); // حفظ الصورة في الكائن
        newTransaction.setCurrencyId(currencyId);
        newTransaction.setDetails(details);
//        newTransaction.setTimestamp(selectedDate.getTime());
        newTransaction.setTimestamp(transactionDate);
        viewModel.addTransaction(newTransaction);
        if (shouldLinkToBudget) {
            BudgetRepository.recordLinkedDebtTransaction(
                    this, accountName, amount, type, currencyId, transactionDate.getTime());
        }
        this.lastSavedTransaction = newTransaction;
        onTransactionSaved(amount * type);
        resetFieldsForNewEntry(accountName, accountId);

        new TransactionNotificationManager(this)
                .sendTransactionNotification(newTransaction,binding.currencySpinnerAutocomplete.getText().toString(),this);
        String uid = isGuest ? "guest_"+guestUID :"user_"+(FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "");

        Bundle transactionParams = new Bundle();
        transactionParams.putString("owerUID", uid);
        transactionParams.putDouble("amount", amount);
        mFirebaseAnalytics.logEvent("log_transaction", transactionParams);
        licenseManager.incrementTransactionCount();
    }

    private void resetFieldsForNewEntry(String accountName, int accountId) {
        binding.amountEditText.setText("");
        binding.detailsEditText.setText("");
        binding.switchLinkToBudget.setChecked(false);
        binding.accountNameAutoComplete.setText(accountName);
        binding.accountNameAutoComplete.dismissDropDown();
        viewModel.setSelectedAccount(accountId);
        binding.amountEditText.requestFocus();
    }

    private void showAddNewAccountDialog(String name, boolean focusAmountAfter) {
        activeAddAccountDialogBinding = DialogAddAccountBinding.inflate(getLayoutInflater());

        activeAddAccountDialogBinding.editAccountName.setText(name);
        MyApplication.applyGlobalTextWatcher(activeAddAccountDialogBinding.getRoot());

        // --- NEW: ربط زر جهات الاتصال ---
        activeAddAccountDialogBinding.dialogPhoneInputLayout.setEndIconOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                openContactPicker();
            } else {
                requestContactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS);
            }
        });
        // ---------------------------------

        AutoCompleteTextView accountTypeSpinner = activeAddAccountDialogBinding.spinnerAccountType;
        accountTypeViewModel.getAllAccountTypes().observe(this, types -> {
            if (types != null && !types.isEmpty()) {
                this.availableAccountTypes = types;
                List<String> typeNames = types.stream().map(t -> t.name).collect(Collectors.toList());
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, typeNames);
                accountTypeSpinner.setAdapter(adapter);
                if (!typeNames.isEmpty()) {
                    accountTypeSpinner.setText(typeNames.get(0), false);
                }
            }
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
//                .setTitle(R.string.add_new_account_title)
                .setView(activeAddAccountDialogBinding.getRoot()) // تأكد من استخدام activeAddAccountDialogBinding
//                .setPositiveButton(R.string.add_account, null)
//                .setNegativeButton(R.string.cancel, (d, which) -> {
//                    binding.accountNameAutoComplete.requestFocus();
//                    activeAddAccountDialogBinding = null; // تنظيف المرجع
//                })
                .create();

        dialog.setOnDismissListener(d -> activeAddAccountDialogBinding = null); // تنظيف المرجع عند الإغلاق

        dialog.setOnShowListener(d -> {
            activeAddAccountDialogBinding.btnCancel.setOnClickListener(v -> {
                binding.accountNameAutoComplete.requestFocus();
                dialog.dismiss();
            });
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            activeAddAccountDialogBinding.btnAdd.setOnClickListener(v -> {
                String finalName = activeAddAccountDialogBinding.editAccountName.getText().toString().trim();
                String phone = activeAddAccountDialogBinding.editAccountPhone.getText().toString().trim();
                String type = accountTypeSpinner.getText().toString();

                if (TextUtils.isEmpty(finalName)) {
                    activeAddAccountDialogBinding.editAccountName.setError(getString(R.string.error_account_name_empty));
                    return;
                }
                if (isAccountExists(finalName)) {
                    Toast.makeText(this, R.string.error_account_exists, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (TextUtils.isEmpty(type)) {
                    activeAddAccountDialogBinding.accountTypeSpinnerLayout.setError(getString(R.string.required));
                    return;
                }

                String uid = isGuest ? guestUID : AppAuth.getUserID(this);

                       // (FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "");
                String typeId = findFirestoreIdByAccountTypeName(type);

                Account newAccount = new Account();
                assert uid != null;
                newAccount.setOwnerUID(uid);
                newAccount.setAccountName(finalName);
                newAccount.setPhoneNumber(phone);
                newAccount.setAccountType(type);
                if (typeId != null) newAccount.setAcTypeFirestoreId(typeId);

                viewModel.createAccount(newAccount);
                binding.accountNameAutoComplete.setText(finalName);

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    Integer newAccountId = accountNameToIdMap.get(finalName);
                    if (newAccountId != null) {
                        viewModel.setSelectedAccount(newAccountId);
                        if(!TextUtils.isEmpty(phone)) {
                            new TransactionNotificationManager(this).sendNewAccount(phone,this);
                        }
                    }
                    if (focusAmountAfter) {
                        binding.amountEditText.requestFocus();
                        showKeyboard(binding.amountEditText);
                    }
                }, 200);

                dialog.dismiss();
            });
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams();
            lp.copyFrom(dialog.getWindow().getAttributes());
            lp.width = android.view.WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;

            dialog.getWindow().setAttributes(lp);
        }
    }

    // --- بقية الدوال المساعدة (تبقى كما هي) ---
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

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    private void showDatePicker() {
        DatePickerDialog.OnDateSetListener dateSetListener = (view, year, month, dayOfMonth) -> {
            selectedDate.set(Calendar.YEAR, year);
            selectedDate.set(Calendar.MONTH, month);
            selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            updateDateInView();
            binding.detailsEditText.requestFocus();
        };
        new DatePickerDialog(this, dateSetListener, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDateInView() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        binding.dateEditText.setText(sdf.format(selectedDate.getTime()));
    }

    @Override
    public void onCalculationComplete(String result) {
        if (result != null && !result.isEmpty()) {
            try {
                double parsed = Double.parseDouble(result.replaceAll("[,]", ""));
                String formattedResult = formatCurrency(parsed);
                binding.amountEditText.setText(formattedResult);
                binding.amountEditText.setSelection(formattedResult.length());
            } catch (NumberFormatException e) {
                binding.amountEditText.setText(result);
            }
            binding.detailsEditText.requestFocus();
        }
    }

    private String findFirestoreIdByAccountTypeName(String name) {
        if (availableAccountTypes == null || name == null || name.isEmpty()) return null;
        for (AccountType type : availableAccountTypes) {
            if (name.equals(type.getName())) {
                return type.getFirestoreId();
            }
        }
        return null;
    }
    private void onTransactionSaved(double finalAmount) {
        viewModel.getSelectedTransactionItems().observe(this, new Observer<List<TransactionItem>>() {
            @Override
            public void onChanged(List<TransactionItem> transactionItems) {
                if (transactionItems != null && !transactionItems.isEmpty()) {

                    // 1. الحصول على أول عملية في القائمة الحالية ورصيدها
                    TransactionItem topItem = transactionItems.get(0);
                    double balanceFromList = topItem.getBalanceAfter();
                    double actualFinalBalance = getActualFinalBalance(topItem, balanceFromList);

                    // 3. إظهار البانر بالرصيد الصحيح نهائياً
                    showSaveConfirmationBanner(lastSavedTransaction, actualFinalBalance);

                    // 4. فك الارتباط فوراً لمنع التكرار
                    viewModel.getSelectedTransactionItems().removeObserver(this);
                }
            }

            private double getActualFinalBalance(TransactionItem topItem, double balanceFromList) {
                Transaction topTransaction = topItem.getTransaction();

                double actualFinalBalance;

                // 2. التحقق: هل هذه العملية هي التي قمنا بحفظها الآن؟
                // نقارن الوقت والتفاصيل والمبلغ للتأكد (لأن الـ ID قد لا يكون وصل بعد)
                if (topTransaction.getAmount() == lastSavedTransaction.getAmount() &&
                        topTransaction.getTimestamp().getTime() == lastSavedTransaction.getTimestamp().getTime()) {

                    // إذا كانت هي نفسها، فالرصيد balanceFromList يحتوي بالفعل على المبلغ
                    actualFinalBalance = balanceFromList;
                } else {
                    // إذا كانت عملية قديمة (لم يتم تحديث القائمة بعد)، نجمع المبلغ يدوياً
                    actualFinalBalance = balanceFromList + finalAmount;
                }
                return actualFinalBalance;
            }
        });
    }
    private void showIfAccountNull(Runnable onConfirm) {
        String accountNames = binding.accountNameAutoComplete.getText().toString().trim();
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.caution_titel))
                .setMessage(getString(R.string.info_null_Account,accountNames))
                .setPositiveButton(getString(R.string.yes), (dialog, which) -> onConfirm.run())
                .setNegativeButton(getString(R.string.cancel), (dialog, which) -> {
                    binding.accountNameAutoComplete.setText("");
                    binding.amountEditText.setText("");
                    binding.detailsEditText.setText("");
                    binding.accountNameAutoComplete.requestFocus();
                })
                .setIcon(R.drawable.ic_alert)
                .show();
    }
    private void showDuplicateWarningDialog(Runnable onConfirm) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.caution_titel))
                .setMessage(getString(R.string.info_same_trans))
                .setPositiveButton(getString(R.string.yes_save), (dialog, which) -> onConfirm.run())
                .setNegativeButton(getString(R.string.cancel), null)
                .setIcon(R.drawable.ic_alert)
                .show();
    }
    private Date getStartOfDay(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }
    private void showSaveConfirmationBanner(Transaction transaction, double finalBalance) {
        View bannerView = binding.saveConfirmationBanner.getRoot();
        Button shareButton = bannerView.findViewById(R.id.button_share);

        shareButton.setOnClickListener(v -> shareTransaction(transaction, finalBalance));

        bannerView.setVisibility(View.VISIBLE);
        bannerView.setAlpha(0f);
        bannerView.setTranslationY(200f);

        bannerView.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(600)
                .start();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            bannerView.animate()
                    .alpha(0f)
                    .setDuration(500)
                    .withEndAction(() -> bannerView.setVisibility(View.GONE))
                    .start();
        }, 5000); // إخفاء البانر بعد 5 ثواني
      checkFirstRunAndShowReminderDialog();
    }
    private void shareTransaction(Transaction transaction, double finalBalance) {
        viewModel.getSelectedAccountDetails().observe(this, new Observer<Account>() {
            @Override
            public void onChanged(Account account) {
                if (account == null) return;
                viewModel.getSelectedAccountDetails().removeObserver(this);

                String phoneNumber = account.getPhoneNumber();
                if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                    showEditAccountDialogForSharing(account, transaction, finalBalance);
                } else {
                    String message = generateShareMessage(transaction, finalBalance);
                    showShareOptionsDialog(phoneNumber, message);
                }
            }
        });
    }
    private void showEditAccountDialogForSharing(Account accountToEdit, Transaction transaction, double finalBalance) {
        DialogEditAccountBinding dialogBinding = DialogEditAccountBinding.inflate(getLayoutInflater());
        dialogBinding.editAccountName.setText(accountToEdit.getAccountName());
        dialogBinding.editAccountPhone.setText(accountToEdit.getPhoneNumber());
        dialogBinding.editAccountName.setVisibility(View.GONE); // إخفاء نوع الحساب
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.edit_1))
                .setView(dialogBinding.getRoot())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (d, which) -> {
                    String newName = dialogBinding.editAccountName.getText().toString().trim();
                    String newPhone = dialogBinding.editAccountPhone.getText().toString().trim();
                    if (!newPhone.isEmpty()) {
                        accountToEdit.setAccountName(newName);
                        accountToEdit.setPhoneNumber(newPhone);
                        viewModel.updateAccount(accountToEdit); // تحديث الحساب
                        String message = generateShareMessage(transaction, finalBalance);
                        openSmsIntent(newPhone, message);
                    }
                })
                .show();

    }
    private String generateShareMessage(Transaction transaction, double finalBalance) {
        String genMessage = "";
        String transactionType = transaction.getType() == 1 ? getString(R.string.on_you) : getString(R.string.for_you);
        String balanceType;//= finalBalance < 0 ? " لكم " : " عليكم ";
        if (finalBalance < 0) {
            balanceType = getString(R.string.report_balance_type_credit);
        } else if (finalBalance > 0) {
            balanceType = getString(R.string.report_balance_type_debit);
        } else {
            return getString(R.string.zero_balance, binding.accountNameAutoComplete.getText().toString(), binding.currencySpinnerAutocomplete.getText().toString());
//            return "مرحباً " + currentAccount.getAccountName() + "، ليس هناك رصيد حالي بعملة (" + currency + ").";
        }
        // الرصيد المتبقي
        String formattedAmount = NumberFormat.getInstance(Locale.US).format(transaction.getAmount());
        AtomicReference<String> formattedBalance = new AtomicReference<>(NumberFormat.getInstance(Locale.US).format(Math.abs(finalBalance)));
        String details = " " + transaction.getDetails() + " ";
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                Integer accountId = accountNameToIdMap.get(binding.accountNameAutoComplete.getText().toString().trim());
                int transCount = repository.getTransactionsDao().getAccountTransactionsCount(accountId);
            if(transCount==1) {
                formattedBalance.set(formattedAmount);
                }
            } catch (RuntimeException e) {
//                throw new RuntimeException(e);
            }
        });

        genMessage = transactionType + getString(R.string.amonts) + " " + formattedAmount + " " + details + "\n" +
                getString(R.string.fianal_remain) + " " + balanceType + " " + formattedBalance + " " + binding.currencySpinnerAutocomplete.getText().toString();

        return genMessage;
    }

    private void showShareOptionsDialog(String phoneNumber, String message) {
        DialogShareOptionsBinding dialogBinding = DialogShareOptionsBinding.inflate(getLayoutInflater());

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogBinding.getRoot())
                .create();

        // الضغط على خيار الواتساب
        dialogBinding.optionWhatsapp.setOnClickListener(v -> {
            openWhatsApp(phoneNumber, message);
            dialog.dismiss();
        });

        // الضغط على خيار الرسائل
        dialogBinding.optionSms.setOnClickListener(v -> {
            openSmsIntent(phoneNumber, message);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void openSmsIntent(String phoneNumber, String message) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("smsto:" + phoneNumber));
        intent.putExtra("sms_body", message);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.cant_open_app), Toast.LENGTH_SHORT).show();
        }
    }

    private void openWhatsApp(String phoneNumber, String message) {
        try {
            String formattedNumber = phoneNumber.replaceAll("[\\s\\-()]", "");
            // يمكنك تعديل هذا الشرط ليتناسب مع رمز بلدك الدولي
            // if (!formattedNumber.startsWith("+")) {
            //     formattedNumber = "+967" + formattedNumber;
            // }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://api.whatsapp.com/send?phone=" + formattedNumber + "&text=" + Uri.encode(message )));
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
              Toast.makeText(this, getString(R.string._1086), Toast.LENGTH_SHORT).show();

            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.whatsapp")));
            } catch (ActivityNotFoundException playStoreException) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.whatsapp" )));
            }
        }
    }

    private void showNoOperationsDialog(String titel, String textInfo) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_no_operations, null);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();
        TextView titelTextView = dialogView.findViewById(R.id.titel_textView);
        TextView noTransactionTextView = dialogView.findViewById(R.id.no_transaction_textView);
        MaterialButton purchaseBtn = dialogView.findViewById(R.id.btn_purchase);
        MaterialButton inviteBtn = dialogView.findViewById(R.id.btn_watch_ad);
        MaterialButton watchBtn = dialogView.findViewById(R.id.btn_invite);
        MaterialButton cancelBtn = dialogView.findViewById(R.id.btn_cancel);
        titelTextView.setText(titel);
        noTransactionTextView.setText(textInfo);
        purchaseBtn.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(this, SubscriptionActivity.class);
            startActivity(intent);
//            handlePurchaseApp();
        });
        inviteBtn.setOnClickListener(v -> {
            dialog.dismiss();
            if(isGuest){
                Toast.makeText(this, getString(R.string.login_1), Toast.LENGTH_SHORT).show();
                return;
            }

            if (currentUser != null) {
                ReferralManager referralManager = new ReferralManager(this);
                referralManager.generateAndShareReferralLink(FirebaseAuth.getInstance().getCurrentUser());
            }
        });
        watchBtn.setOnClickListener(v -> {
            dialog.dismiss();
            watchAd();
        });
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
                DialogHelper.show(AddTransactionActivity.this,
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
               // Toast.makeText(AddTransactionActivity.this, getString(R.string.setupListeners_02), Toast.LENGTH_LONG).show();
                DialogHelper.show(AddTransactionActivity.this, getString(R.string.setupListeners_02), DialogHelper.FeedbackType.ERROR, null);

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
        SecureLicenseManager.getInstance(this).setAdRewards(SecureLicenseManager.getInstance(this).getAdRewards() + points);

        isGuest = SecureLicenseManager.getInstance(this).isGuest();
        String userDoc="",userUID;
        if (isGuest) {
            userUID = SecureLicenseManager.getInstance(this).guestUID();
            userDoc = "guests";
        }else{
            userUID=AppAuth.getUserID(this);
            userDoc="users";
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
    private void checkFirstRunAndShowReminderDialog() {
        // التحقق فقط لأندرويد 12 وما فوق
        SharedPreferences prefs = getSharedPreferences("daily_reminder_prefs", MODE_PRIVATE);
        boolean isFirstRun = prefs.getBoolean("is_first_transaction_added", true);

        // إذا كانت هذه أول عملية بعد التثبيت
        if (isFirstRun) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

            // إذا لم يكن الإذن ممنوحاً
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                    showProfessionalExactAlarmDialog(prefs);
                } else {
                   // showProfessionalExactAlarmDialog(prefs);
                    // إذا كان الإذن ممنوحاً مسبقاً، نغلق العلامة للأبد
                    prefs.edit().putBoolean("is_first_transaction_added", false).apply();
                }
            }
        }
    }
    private void showProfessionalExactAlarmDialog(SharedPreferences prefs) {
        // إعداد الديالوج
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        // ربط التصميم الذي أنشأناه
        dialog.setContentView(R.layout.dialog_permission_reminder);

        // جعل الخلفية شفافة ليظهر التصميم المدور (Rounded Corners)
//        if (dialog.getWindow() != null) {
//           dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
//            // جعل الديالوج بعرض الشاشة تقريباً
//            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
//        }

        // تعريف العناصر
        MaterialButton btnAllow = dialog.findViewById(R.id.btn_allow_permission);
        MaterialButton btnDismiss = dialog.findViewById(R.id.btn_dismiss_dialog);
        TextView txtSubTitle = dialog.findViewById(R.id.txtSubTitle); // تأكد من إضافة ID للعنوان في XML إذا أردت تغييره برمجياً
        txtSubTitle.setText(getString(R.string.welcome_title) +
                getString(R.string.battery_dialog_title) +
                getString(R.string.battery_dialog_importance_title) +
                getString(R.string.early_dialog_welcome_subtitle2) +
                getString(R.string.requestDndWithDialog_53) +
                getString(R.string.requestDndWithDialog_54) +
                getString(R.string.requestDndWithDialog_55)

        );
        // منطق زر الموافقة
        btnAllow.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setData(Uri.parse("package:" + getPackageName()));
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                }
            }
            // نغلق الديالوج ونحدث القيمة لعدم الظهور مرة أخرى
            prefs.edit().putBoolean("is_first_transaction_added", false).apply();
            dialog.dismiss();
        });

        // منطق زر الإلغاء
        btnDismiss.setOnClickListener(v -> {
            // نغلق الديالوج ونحدث القيمة لعدم الظهور مرة أخرى (حتى لا نزعجه كل مرة)
            // أو يمكنك عدم تحديثها إذا أردت تذكيره في العملية التالية
            prefs.edit().putBoolean("is_first_transaction_added", false).apply();
            dialog.dismiss();
        });

        dialog.setCancelable(false); // منع الإغلاق بالضغط خارج الديالوج لضمان قراءة الرسالة
        dialog.show();
    }
    private void checkRecurringOperationForAccount(int accountId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<RecurringOperation> operations = repository.getRecurringOperationsForAccount(accountId);
            Log.d("Recurring", "Found " + operations.size() + " operations for account " + accountId);
            for (RecurringOperation op : operations) {
                Log.d("Recurring", "Op: " + op.getAccountName() + ", active=" + op.isActive() + ", type=" + op.getRecurrenceType());
                if (shouldShowBubble(op)) {
                    Log.d("Recurring", "Should show bubble");
                    runOnUiThread(() -> showRecurringBubble(op));
                    break;
                } else {
                    Log.d("Recurring", "Should NOT show bubble");
                }
            }
        });
    }
    private void showRecurringBubble(RecurringOperation operation) {
        // إظهار الحاوية أولاً (لأنها مخفية في XML)
        binding.bubbleContainer.setVisibility(View.VISIBLE);

        if (bubbleView == null) {
            bubbleView = getLayoutInflater().inflate(R.layout.bubble_recurring, null);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.CENTER;
            bubbleView.setLayoutParams(params);
            binding.bubbleContainer.addView(bubbleView);
        }
        currentRecurringOperation = operation;
        bubbleView.setVisibility(View.VISIBLE);
        bubbleView.setScaleX(0.8f);
        bubbleView.setScaleY(0.8f);
        bubbleView.setAlpha(0f);
        bubbleView.animate()
                .scaleX(1f).scaleY(1f)
                .alpha(1f)
                .setDuration(200)
                .start();

        TextView tvMessage = bubbleView.findViewById(R.id.tv_message);
        String recurrenceText = "";
        switch (operation.getRecurrenceType()) {
            case "day": recurrenceText = getString(R.string.daily); break;
            case "week": recurrenceText = getString(R.string.weekly); break;
            case "month": recurrenceText = getString(R.string.monthly); break;
        }
        tvMessage.setText(getString(R.string.recurring_bubble_message,
                recurrenceText, formatCurrency(operation.getAmount()), findCurrencyNameById(operation.getCurrencyId())));

        // ✅ استخدام المعرفات الصحيحة (btn_apply و btn_ignore)
        bubbleView.findViewById(R.id.btn_add).setOnClickListener(v -> {
            applyRecurringOperation(operation);
            hideRecurringBubble();
        });
        bubbleView.findViewById(R.id.btn_dismiss).setOnClickListener(v -> {
            markRecurringProcessed(operation);
            hideRecurringBubble();
        });
    }
    private void hideRecurringBubble() {
        if (bubbleView != null) {
            bubbleView.setVisibility(View.GONE);
        }
        // إخفاء الحاوية أيضاً
        binding.bubbleContainer.setVisibility(View.GONE);
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
    private String formatCurrency(double amount) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumFractionDigits(0);
        nf.setMaximumFractionDigits(2);
        return nf.format(amount);
    }
    private void applyRecurringOperation(RecurringOperation operation) {
        binding.amountEditText.setText(formatCurrency(operation.getAmount()));
        binding.detailsEditText.setText(operation.getDetails());
        String currencyName = findCurrencyNameById(operation.getCurrencyId());
        if (currencyName != null) {
            binding.currencySpinnerAutocomplete.setText(currencyName, false);
        }
        markRecurringProcessed(operation);
        binding.amountEditText.requestFocus();
    }

    private void markRecurringProcessed(RecurringOperation operation) {
        recurringOperationsViewModel.markProcessed(operation.getId());
    }

    private String findCurrencyNameById(int currencyId) {
        for (Currency c : availableCurrencies) {
            if (c.id == currencyId) return c.name;
        }
        return null;
    }

}
