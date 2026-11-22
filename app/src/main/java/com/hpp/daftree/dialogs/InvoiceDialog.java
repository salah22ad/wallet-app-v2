package com.hpp.daftree.dialogs;

import static android.app.Activity.RESULT_OK;
import static android.content.Context.MODE_PRIVATE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.LocaleList;
import android.os.Looper;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.Transaction;
import com.hpp.daftree.database.User;
import com.hpp.daftree.databinding.DialogAddAccountBinding;
import com.hpp.daftree.databinding.DialogNewInvoiceBinding;
import com.hpp.daftree.helpers.LanguageManager;
import com.hpp.daftree.models.AppAuth;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.models.FormatingAmount;
import com.hpp.daftree.models.UUIDGenerator;
import com.hpp.daftree.notifications.TransactionNotificationManager;
import com.hpp.daftree.syncmanagers.SyncPreferences;
import com.hpp.daftree.ui.LoginActivity;
import com.hpp.daftree.ui.SubscriptionActivity;
import com.hpp.daftree.utils.AdsManager;
import com.hpp.daftree.utils.DialogHelper;
import com.hpp.daftree.utils.ImageUtils;
import com.hpp.daftree.viewmodels.AccountTypeViewModel;
import com.hpp.daftree.viewmodels.AddTransactionViewModel;
import com.hpp.daftree.viewmodels.CurrencyViewModel;
import com.hpp.daftree.utils.GoogleAuthHelper;
import com.hpp.daftree.utils.LicenseManager;
import com.hpp.daftree.utils.ReferralManager;
import com.hpp.daftree.utils.SecureLicenseManager;
import com.hpp.daftree.viewmodels.ReportsViewModel;

import java.io.File;
import java.io.InputStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class InvoiceDialog extends DialogFragment {

    private DialogNewInvoiceBinding binding;
    private DaftreeRepository repository;
    private static final int LOGIN_REQUEST_CODE = 1001;
    private final ExecutorService imageProcessingExecutor = Executors.newSingleThreadExecutor();
    private ReportsViewModel reportsViewModel;
    private CurrencyViewModel currencyViewModel;
    private final List<Currency> allAvailableCurrencies = new ArrayList<>();
    private AddTransactionViewModel viewModel;
    private SyncPreferences syncPreferences;
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private Uri photoUri;
    private final Calendar selectedDate = Calendar.getInstance();
    private List<Account> accountsList = new ArrayList<>();
    private final Map<String, Account> accountNameToObjectMap = new HashMap<>();
    private final Map<String, Integer> accountNameToIdMap = new HashMap<>();
    private static final String ARG_MODE = "mode";
    private static final String ARG_IMPORT_ID = "import_id";

    private enum Mode {CREATE, VIEW_EDIT, CREATE_ACC}

    private Mode currentMode;
    private AccountTypeViewModel accountTypeViewModel;
    private List<AccountType> availableAccountTypes = new ArrayList<>();
    private List<Transaction> invoiceTxs = new ArrayList<>();
    private Transaction invoiceTx, paymentTx;
    private String selectedCurrency, accountName;
    private int editImportId = -1;
    private User currentUser;
    private LicenseManager licenseManager;

    private static final String ARG_CURRENCY = "currency";
    private static final String ARG_AccountName = "currency";
    private static final String ARG_isEdited = "currency";
    private String isEdited = "false";
    private boolean isGuest;
    private boolean isAutoRenew = true;
    private String uid, guestUID;
    private Handler mainHandler;
    private LanguageManager languageManager;
    private DialogAddAccountBinding activeAddAccountDialogBinding;

    private String encodedImage = null; // لتخزين الصورة مؤقتاً
    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> photoPickerLauncher;
    private  AdsManager adsManager;
    // 1. Launcher لطلب إذن جهات الاتصال
    private final ActivityResultLauncher<String> requestContactPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    openContactPicker();
                } else {
                    Toast.makeText(getContext(), getString(R.string.error_contact_permission), Toast.LENGTH_SHORT).show();
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
    private final ActivityResultLauncher<Intent> loginLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                }
            }
    );

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

        try (Cursor cursor = requireContext().getContentResolver().query(dataUri, projection, null, null, null)) {
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
            Log.e("InvoiceDialog", "Error picking contact phone", e);
            Toast.makeText(getContext(), "حدث خطأ أثناء جلب الرقم", Toast.LENGTH_SHORT).show();
        }
    }

    public static InvoiceDialog newInstanceForCreate(String currency) {
        InvoiceDialog dialog = new InvoiceDialog();
        Bundle args = new Bundle();
        args.putSerializable(ARG_MODE, Mode.CREATE);
        args.putString(ARG_CURRENCY, currency);
        dialog.setArguments(args);
        return dialog;
    }

    public static InvoiceDialog newInstanceForEdit(int importId, String currency, String billEdited) {
        InvoiceDialog dialog = new InvoiceDialog();
        Bundle args = new Bundle();
        args.putSerializable(ARG_MODE, Mode.VIEW_EDIT);
        args.putInt(ARG_IMPORT_ID, importId);
        args.putString(ARG_CURRENCY, currency);
        args.putString(ARG_isEdited, billEdited);
        dialog.setArguments(args);
        return dialog;
    }

    public static InvoiceDialog newInstanceForAccount(String currency, String accountName) {
        InvoiceDialog dialog = new InvoiceDialog();
        Bundle args = new Bundle();
        args.putSerializable(ARG_MODE, Mode.CREATE_ACC);

        args.putString(ARG_CURRENCY, currency);
        args.putString(ARG_AccountName, accountName);
        dialog.setArguments(args);
        return dialog;

    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogNewInvoiceBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            // تعيين العرض ليملأ الشاشة (MATCH_PARENT)
            int width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
            int height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

            getDialog().getWindow().setLayout(width, height);
            android.view.WindowManager.LayoutParams params = getDialog().getWindow().getAttributes();

        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mainHandler = new Handler(Looper.getMainLooper());
        repository = new DaftreeRepository(requireActivity().getApplication());
        reportsViewModel = new ViewModelProvider(this).get(ReportsViewModel.class);
        accountTypeViewModel = new ViewModelProvider(this).get(AccountTypeViewModel.class);
        viewModel = new ViewModelProvider(this).get(AddTransactionViewModel.class);
        licenseManager = new LicenseManager(requireContext());
        syncPreferences = new SyncPreferences(requireContext());
        currencyViewModel = new ViewModelProvider(this).get(CurrencyViewModel.class);

        adsManager = new AdsManager(getContext());
        languageManager = LanguageManager.getInstance(requireContext());
        String lang = languageManager.getCurrentLanguage();
        Locale locale = Locale.forLanguageTag(lang); // تصحيح: استخدام forLanguageTag
        Locale.setDefault(locale);

        isGuest = SecureLicenseManager.getInstance(requireContext()).isGuest();
        if (isGuest) {
            uid = SecureLicenseManager.getInstance(requireContext()).guestUID();
            guestUID = SecureLicenseManager.getInstance(requireContext()).guestUID();
        } else {
            uid =  FirebaseAuth.getInstance().getCurrentUser() != null
                    ? FirebaseAuth.getInstance().getCurrentUser().getUid() : AppAuth.getUserID(requireContext());
        }

        // ====== تعامُل مع Configuration بشكل متوافق ======
        Resources res = requireContext().getResources();
        Configuration config = new Configuration(res.getConfiguration());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            LocaleList localeList = new LocaleList(locale);
            LocaleList.setDefault(localeList);
            config.setLocales(localeList);
            config.setLayoutDirection(locale);
            // تصحيح: استخدام createConfigurationContext بدلاً من updateConfiguration
            requireContext().createConfigurationContext(config);
        } else {
            config.setLocale(locale);
            config.setLayoutDirection(locale);
            //noinspection deprecation
            res.updateConfiguration(config, res.getDisplayMetrics());
        }
        // ===========================================================================

        FormatingAmount.applyTo(binding.invoiceAmountEditText);
        FormatingAmount.applyTo(binding.paidAmountEditText);

        MyApplication.applyGlobalTextWatcher(binding.getRoot());
        setupObservers();
        setupEventListeners();
        setupListeners();

        if (getArguments() != null) {
            selectedCurrency = getArguments().getString(ARG_CURRENCY);
            isEdited = getArguments().getString(ARG_isEdited);

            if (getArguments().containsKey(ARG_MODE)) {
                String modeName = getArguments().getString(ARG_MODE, null);
                if (modeName != null) {
                    try {
                        currentMode = Mode.valueOf(modeName);
                    } catch (IllegalArgumentException e) {
                        // تصحيح: استخدام getSerializable بشكل آمن نوعاً ما
                        @SuppressWarnings("deprecation")
                        Object ser = getArguments().getSerializable(ARG_MODE);
                        if (ser instanceof Mode) {
                            currentMode = (Mode) ser;
                        } else {
                            currentMode = Mode.CREATE;
                        }
                    }
                } else {
                    // تصحيح: getSerializable is deprecated in API 33
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        currentMode = getArguments().getSerializable(ARG_MODE, Mode.class);
                    } else {
                        @SuppressWarnings("deprecation")
                        Object ser = getArguments().getSerializable(ARG_MODE);
                        currentMode = (Mode) ser;
                    }
                    if (currentMode == null) currentMode = Mode.CREATE;
                }
            } else {
                currentMode = Mode.CREATE;
            }

            if (currentMode == Mode.VIEW_EDIT) {
                editImportId = getArguments().getInt(ARG_IMPORT_ID);
                binding.titleTextView.setText(R.string.editr_invoice_tit);

                loadInvoiceData(editImportId);
            } else if (currentMode == Mode.CREATE) {
                binding.titleTextView.setText(R.string.new_invoice_tit);
                binding.accountAutoComplete.requestFocus();
                switchToCreateMode();
            } else {
                binding.titleTextView.setText(R.string.new_invoice_tit);
                accountName = getArguments().getString(ARG_AccountName);
                selectedCurrency = getArguments().getString(ARG_CURRENCY);
                binding.detailsEditText.requestFocus();
                binding.accountAutoComplete.setEnabled(false);
                switchToCreateMode();
            }
        }
        binding.radioSales.setOnClickListener(v -> switchTab(true));
        binding.radioPurchases.setOnClickListener(v -> switchTab(false));

        viewModel.getUserProfile().observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                this.currentUser = user;
            }
        });

        setupModernImageLaunchers();

        // ربط زر الكاميرا (بناءً على التعديل في XML الذي اقترحته سابقاً)
        binding.btnAttachImage.setOnClickListener(v -> onImageIconClicked());
    }
    private void processAndSetImage(Bitmap bitmap) {
        // إظهار مؤشر تحميل (اختياري)
        binding.loadingOverlay.setVisibility(View.VISIBLE);
        imageProcessingExecutor.execute(() -> {
            try {
                // معالجة الصورة في الخلفية
                String processedImage = ImageUtils.bitmapToBase64(requireContext(), bitmap);

                // الرجوع للخيط الرئيسي لتحديث الواجهة
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        encodedImage = processedImage;
                        binding.btnAttachImage.setColorFilter(Color.GREEN);
                        binding.loadingOverlay.setVisibility(View.GONE);
                        Toast.makeText(requireContext(), getString(R.string.image_attached_success), Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                // الرجوع للخيط الرئيسي في حالة الخطأ
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        binding.loadingOverlay.setVisibility(View.GONE);
                        Toast.makeText(requireContext(), "خطأ في معالجة الصورة", Toast.LENGTH_SHORT).show();
                    });
                }
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
                        InputStream is = requireContext().getContentResolver().openInputStream(photoUri);
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
                            InputStream is = requireContext().getContentResolver().openInputStream(uri);
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
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void openCamera() {
        File photoFile = new File(requireContext().getExternalCacheDir(), "temp_image_" + System.currentTimeMillis() + ".jpg");
        photoUri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".provider", photoFile);

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri); // هنا نخبر الكاميرا بحفظ الصورة كاملة
        cameraLauncher.launch(intent);
    }
    private boolean isTrialValid() {
        SharedPreferences prefs = getContext().getSharedPreferences("AppPrefs", MODE_PRIVATE);
        long trialExpire = prefs.getLong("trial_expire", 0);
        if (trialExpire == 0) return false;
        return System.currentTimeMillis() < trialExpire;
    }
    private void onImageIconClicked() {
        // 1. فحص البريميوم (استخدم المتغير الموجود في تطبيقك)
        if (!SecureLicenseManager.getInstance(requireContext()).isPremium() && !isTrialValid()) {
            DialogHelper.show(requireContext(), getString(R.string.add_image_premium), DialogHelper.FeedbackType.ERROR, null, null);

            return;
        }

        // 2. إنشاء PopupMenu وربطه بالزر (v هو الـ btnAttachImage)
        PopupMenu popup = new PopupMenu(requireContext(), binding.btnAttachImage);

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
                Toast.makeText(requireContext(), getString(R.string.image_deleted), Toast.LENGTH_SHORT).show();
            }
            return false;
        });

        // 5. إظهار القائمة
        popup.show();


    }


    private void showImageFullScreen(String base64) {
        Bitmap bitmap = ImageUtils.base64ToBitmap(base64);
        if (bitmap == null) return;

        Dialog dialog = new Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        ImageView iv = new ImageView(requireContext());
        iv.setImageBitmap(bitmap);
        dialog.setContentView(iv);
        dialog.show();
    }

    private void switchTab(boolean auto) {
        isAutoRenew = auto;
        binding.radioSales.setBackgroundResource(auto ? R.drawable.bg_tab_selected : 0);
        binding.radioSales.setTextColor(auto ? Color.WHITE : Color.parseColor("#757575"));
        binding.radioPurchases.setBackgroundResource(!auto ? R.drawable.bg_tab_selected : 0);
        binding.radioPurchases.setTextColor(!auto ? Color.WHITE : Color.parseColor("#757575"));

    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupEventListeners() {
        binding.accountAutoComplete.setOnClickListener(v -> showKeyboard(binding.accountAutoComplete));

        binding.invoiceDateEditText.setOnClickListener(v -> showDatePicker());
        binding.currencyAutoComplete.setOnTouchListener((v, event) -> {
            hideKeyboard(binding.currencyAutoComplete);
            return false;
        });

        binding.accountAutoComplete.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT |
                        android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS |
                        android.text.InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        );

        binding.accountAutoComplete.setThreshold(1);

        binding.accountAutoComplete.addTextChangedListener(new TextWatcher() {
            private String lastValidText = "";
            private boolean isInternalChange = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (isInternalChange) return;

                String currentText = s.toString().trim();

                if (currentText.isEmpty() || currentText.equals(lastValidText)) {
                    return;
                }

                if (!lastValidText.isEmpty() &&
                        currentText.length() > lastValidText.length() + 10) {
                    isInternalChange = true;
                    binding.accountAutoComplete.setText(lastValidText);
                    binding.accountAutoComplete.setSelection(lastValidText.length());
                    isInternalChange = false;
                    return;
                }

                lastValidText = currentText;

                if (currentText.length() > 30) {
                    isInternalChange = true;
                    String shortened = currentText.substring(0, 30);
                    binding.accountAutoComplete.setText(shortened);
                    binding.accountAutoComplete.setSelection(shortened.length());
                    isInternalChange = false;
                    lastValidText = shortened;
                }
            }
        });

        binding.accountAutoComplete.setOnItemClickListener((parent, view, position, id) -> {
            String selectedName = (String) parent.getItemAtPosition(position);
            Integer accountId = accountNameToIdMap.get(selectedName);
            if (accountId != null) {
                binding.detailsEditText.requestFocus();
                showKeyboard(view);
            }
        });

        binding.detailsEditText.setOnEditorActionListener((v, actionId, event) -> {
            if ((actionId == EditorInfo.IME_ACTION_NEXT) || (actionId == EditorInfo.IME_ACTION_DONE)) {
                binding.invoiceAmountEditText.requestFocus();
                showKeyboard(binding.invoiceAmountEditText);
                return true;
            }
            return false;
        });
        binding.invoiceAmountEditText.setOnEditorActionListener((v, actionId, event) -> {
            if ((actionId == EditorInfo.IME_ACTION_NEXT) || (actionId == EditorInfo.IME_ACTION_DONE)) {
                binding.paidAmountEditText.requestFocus();
                showKeyboard(binding.paidAmountEditText);
                return true;
            }
            return false;
        });
        binding.paidAmountEditText.setOnEditorActionListener((v, actionId, event) -> {
            if ((actionId == EditorInfo.IME_ACTION_NEXT) || (actionId == EditorInfo.IME_ACTION_DONE)) {
                hideKeyboard(binding.paidAmountEditText);
                return true;
            }
            return false;
        });

        binding.accountAutoComplete.setOnEditorActionListener((v, actionId, event) -> {
            if ((actionId == EditorInfo.IME_ACTION_NEXT) || (actionId == EditorInfo.IME_ACTION_DONE)) {
                if (isValidate()) {
                    binding.detailsEditText.requestFocus();
                    showKeyboard(binding.detailsEditText);
                } else {
                    String name = binding.accountAutoComplete.getText().toString().trim();
                    showAddNewAccountDialog(name, "detailsEditText");
                }
                return true;
            }
            return false;
        });

        binding.accountAutoComplete.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                binding.accountAutoComplete.selectAll();
            }
        });

        binding.detailsEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                String name = binding.accountAutoComplete.getText().toString().trim();
                Log.e("accountName", name);

                mainHandler.postDelayed(() -> {
                    if (!TextUtils.isEmpty(name) && !isAccountExists(name) && !isNewAccAdded) {
                        showAddNewAccountDialog(name, "invoiceAmountEditText");
                    } else {
                        binding.detailsEditText.selectAll();
                        isNewAccAdded = false;
                    }
                }, 150);
            }
        });

        binding.invoiceAmountEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                String name = binding.accountAutoComplete.getText().toString().trim();
                Log.e("accountName", name);
                mainHandler.postDelayed(() -> {
                    if (!TextUtils.isEmpty(name) && !isAccountExists(name) && !isNewAccAdded) {
                        showAddNewAccountDialog(name, "paidAmountEditText");
                    } else {
                        binding.invoiceAmountEditText.selectAll();
                        isNewAccAdded = false;
                    }
                }, 150);
            }
        });
    }

    private boolean isAccountExists(String accountName) {
        if (TextUtils.isEmpty(accountName)) {
            return false;
        }
        String cleanName = accountName.trim();
        for (String existingName : accountNames) {
            if (existingName != null && existingName.trim().equalsIgnoreCase(cleanName)) {
                return true;
            }
        }
        return false;
    }

    boolean isNewAccAdded = true;

    private boolean isValidate() {
        String name = binding.accountAutoComplete.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(getContext(), R.string.error_account_name_empty, Toast.LENGTH_SHORT).show();
            binding.accountAutoComplete.requestFocus();
            return false;
        }
        if (accountNames.contains(name)) {
            Integer accountId = accountNameToIdMap.get(name);
            return true;
        } else {
            return false;
        }
    }

    private void showAddNewAccountDialog(String name, String nextPosition) {
        activeAddAccountDialogBinding = DialogAddAccountBinding.inflate(getLayoutInflater());

        activeAddAccountDialogBinding.editAccountName.setText(name);
        MyApplication.applyGlobalTextWatcher(activeAddAccountDialogBinding.getRoot());

        // --- NEW: ربط زر جهات الاتصال ---
        activeAddAccountDialogBinding.dialogPhoneInputLayout.setEndIconOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
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
                ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, typeNames);
                accountTypeSpinner.setAdapter(adapter);
                if (!typeNames.isEmpty()) {
                    accountTypeSpinner.setText(typeNames.get(0), false);
                }
            }
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(getContext())
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
                binding.accountAutoComplete.requestFocus();
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
                    Toast.makeText(getContext(), R.string.error_account_exists, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (TextUtils.isEmpty(type)) {
                    activeAddAccountDialogBinding.accountTypeSpinnerLayout.setError(getString(R.string.required));
                    return;
                }

                String uid = isGuest ? guestUID : AppAuth.getUserID(getContext());// ( FirebaseAuth.getInstance().getCurrentUser() != null
                // ? FirebaseAuth.getInstance().getCurrentUser().getUid() : SecureLicenseManager.getInstance(requireContext()).getUserUID());
                String typeId = findFirestoreIdByAccountTypeName(type);

                Account newAccount = new Account();
                newAccount.setOwnerUID(uid);
                newAccount.setAccountName(finalName);
                newAccount.setPhoneNumber(phone);
                newAccount.setAccountType(type);
                if (typeId != null) newAccount.setAcTypeFirestoreId(typeId);

                viewModel.createAccount(newAccount);
                binding.accountAutoComplete.setText(finalName);

                binding.accountAutoComplete.selectAll();
                binding.accountAutoComplete.requestFocus();

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    Integer newAccountId = accountNameToIdMap.get(finalName);
                    if (newAccountId != null) {
                        viewModel.setSelectedAccount(newAccountId);
                    }
                    if (nextPosition == "invoiceAmountEditText") {
                        binding.invoiceAmountEditText.requestFocus();
                        showKeyboard(binding.invoiceAmountEditText);
                    } else if (nextPosition == "detailsEditText") {
                        binding.detailsEditText.requestFocus();
                        showKeyboard(binding.detailsEditText);
                    } else if (nextPosition == "paidAmountEditText") {
                        binding.paidAmountEditText.requestFocus();
                        showKeyboard(binding.paidAmountEditText);
                    }
                }, 200);

                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private String findFirestoreIdByAccountTypeName(String name) {
        if (availableAccountTypes == null || name.isEmpty()) return null;
        for (AccountType type : availableAccountTypes) {
            if (type.getName().equals(name)) {
                return type.getFirestoreId();
            }
        }
        return null;
    }

    private void showKeyboard(View view) {
        if (view.requestFocus()) {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    List<String> accountNames = new ArrayList<>();

    private void setupObservers() {
        updateDateInView();
        reportsViewModel.getAllAccounts().observe(getViewLifecycleOwner(), accounts -> {
            if (accounts == null) return;
            this.accountsList = accounts;
            accountNameToObjectMap.clear();
            accountNames.clear();
            for (Account acc : accounts) {
                accountNames.add(acc.getAccountName());
                accountNameToObjectMap.put(acc.getAccountName(), acc);
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, accountNames);
            binding.accountAutoComplete.setAdapter(adapter);
            int itemHeight = (int) (48 * getResources().getDisplayMetrics().density);
            binding.accountAutoComplete.setDropDownHeight(itemHeight * 4);
        });
        currencyViewModel.getAllCurrencies().observe(this, currencies -> {
            if (currencies != null) {
                allAvailableCurrencies.clear();
                allAvailableCurrencies.addAll(currencies);
            }
        });
        reportsViewModel.getAllCurrencies().observe(getViewLifecycleOwner(), currencies -> {
            if (currencies == null) return;
            List<String> currencyNames = currencies.stream().map(c -> c.name).collect(Collectors.toList());
            ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, currencyNames);
            binding.currencyAutoComplete.setAdapter(adapter);
            int itemHeight = (int) (48 * getResources().getDisplayMetrics().density);
            binding.currencyAutoComplete.setDropDownHeight(itemHeight * 4);

            if (selectedCurrency != null && currencyNames.contains(selectedCurrency)) {
                binding.currencyAutoComplete.setText(selectedCurrency, false);
            } else if (!currencyNames.isEmpty()) {
                binding.currencyAutoComplete.setText(currencyNames.get(0), false);
            }
        });
    }

    private void setupListeners() {
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateRemainingAmount();
            }
        };
        binding.invoiceAmountEditText.addTextChangedListener(textWatcher);
        binding.paidAmountEditText.addTextChangedListener(textWatcher);

        binding.saveButton.setOnClickListener(v -> saveInvoice());
        binding.btnCancel.setOnClickListener(v -> dismiss());
        binding.editButton.setOnClickListener(v -> switchToEditMode());
        binding.deleteButton.setOnClickListener(v -> deleteInvoice());
    }

    private void loadInvoiceData(int importId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            invoiceTxs = repository.getTransactionsByImportId(importId);
            if (invoiceTxs.isEmpty()) {
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), R.string.bill_not_found, Toast.LENGTH_SHORT).show();
                    dismiss();
                });
                return;
            }
            invoiceTx = null;
            paymentTx = null;
            if (invoiceTxs != null && invoiceTxs.size() == 1) {
                invoiceTx = invoiceTxs.get(0);
            } else {
                for (Transaction tx : invoiceTxs) {
                    String billType = tx.getBillType();
                    // إذا كان هناك معاملة واحدة فقط، فهي الفاتورة
                    if ((billType.split("\\s+").length == 1)) {
                        invoiceTx = tx;
                    } else {
                        paymentTx = tx;
                    }
                }
            }

            Account account = repository.getAccountDao().getAccountByIdBlocking(invoiceTx.getAccountId());

            getActivity().runOnUiThread(() -> {
                binding.accountAutoComplete.setText(account.getAccountName(), false);
                String details = invoiceTx.getDetails();
                Log.e("details", details);
                if (details != null) {
                    details = details.replaceFirst("^" + R.string.ar_long_text_47 + "\\s*", "");
                    details = details.replaceFirst("^" + R.string.ar_long_text_48 + "\\s*", "");
                }

                binding.currencyAutoComplete.setText(selectedCurrency, false);
                selectedDate.setTime(invoiceTx.getTimestamp());
                boolean isSales = false;

                if (invoiceTx.getType() == 1) {
                    isSales = true;
                } else if (invoiceTx.getType() == -1) {
                    isSales = false;
                } else if (invoiceTx.getBillType() != null &&
                        invoiceTx.getBillType().contains(getString(R.string.ar_text_6_2))) {
                    isSales = true;
                } else if (invoiceTx.getBillType() != null &&
                        invoiceTx.getBillType().contains(getString(R.string.ar_text_7_2))) {
                    isSales = false;
                }

                if ((isSales)) {
                    binding.radioSales.setChecked(true);
                    switchTab(true);
                    binding.radioPurchases.setChecked(false);
                    binding.invoiceAmountEditText.setText(formatCurrency(invoiceTx.getAmount()));
                    binding.paidAmountEditText.setText(paymentTx != null ? formatCurrency(paymentTx.getAmount()) : "0");
                    binding.detailsEditText.setText(details.trim());
                } else {
                    switchTab(false);
                    binding.radioPurchases.setChecked(true);
                    binding.radioSales.setChecked(false);
                    binding.invoiceAmountEditText.setText(formatCurrency(invoiceTx.getAmount()));
                    binding.paidAmountEditText.setText(paymentTx != null ? formatCurrency(paymentTx.getAmount()) : "0");
                    binding.detailsEditText.setText(details.trim());
                }
                encodedImage = invoiceTx.getImageEncoded();
                Log.d("InvoiceDialog", "Loaded image from DB: " +
                        (encodedImage != null ? "YES, length: " + encodedImage.length() : "NO"));

                encodedImage = ( invoiceTx.getImageEncoded() != null && !invoiceTx.getImageEncoded().isEmpty())
                        ? invoiceTx.getImageEncoded()
                        : null;
                if (encodedImage != null){
                    binding.btnAttachImage.setColorFilter(Color.GREEN);
                }
                switchToViewMode();
            });
        });
    }
  
    private void switchToViewMode() {
        setFieldsEnabled(false);
        binding.layoutSaveActions.setVisibility(View.GONE);
        binding.layoutEditActions.setVisibility(View.VISIBLE);
    }

    private void switchToEditMode() {
        setFieldsEnabled(true);
        binding.layoutSaveActions.setVisibility(View.VISIBLE);
        binding.layoutEditActions.setVisibility(View.GONE);
    }

    private void switchToCreateMode() {
        setFieldsEnabled(true);
        binding.layoutSaveActions.setVisibility(View.VISIBLE);
        binding.layoutEditActions.setVisibility(View.GONE);
        binding.accountAutoComplete.setEnabled(true);
        binding.accountAutoComplete.setText(accountName, false);
        binding.currencyAutoComplete.setText(selectedCurrency, false);
        binding.radioSales.setChecked(true);

        if (currentMode == Mode.CREATE) {
            binding.accountAutoComplete.requestFocus();
            mainHandler.postDelayed(() -> {
                showKeyboard(binding.accountAutoComplete);
            }, 100);
        } else {
            binding.accountAutoComplete.setEnabled(false);
            binding.detailsEditText.requestFocus();
            mainHandler.postDelayed(() -> {
                showKeyboard(binding.detailsEditText);
            }, 100);
        }
    }

    private void setFieldsEnabled(boolean isEnabled) {
        binding.radioSales.setEnabled(isEnabled);
        binding.radioPurchases.setEnabled(isEnabled);
        binding.invoiceDateEditText.setEnabled(isEnabled);
        binding.detailsEditText.setEnabled(isEnabled);
        binding.invoiceAmountEditText.setEnabled(isEnabled);
        binding.paidAmountEditText.setEnabled(isEnabled);
        binding.currencyAutoComplete.setEnabled(isEnabled);
    }

    private void showDatePicker() {
        DatePickerDialog.OnDateSetListener dateSetListener = (view, year, month, dayOfMonth) -> {
            selectedDate.set(Calendar.YEAR, year);
            selectedDate.set(Calendar.MONTH, month);
            selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            updateDateInView();
            String name = binding.accountAutoComplete.getText().toString().trim();
            if (!name.isEmpty()) {
                binding.detailsEditText.requestFocus();
            }
        };
        new DatePickerDialog(getContext(), dateSetListener,
                selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDateInView() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        binding.invoiceDateEditText.setText(sdf.format(selectedDate.getTime()));
    }

    private void updateRemainingAmount() {
        try {
            double invoiceValue = FormatingAmount.parseAmount(binding.invoiceAmountEditText.getText().toString().trim());
            double paidValue = FormatingAmount.parseAmount(binding.paidAmountEditText.getText().toString().trim());

            double remaining = invoiceValue - paidValue;
            binding.remainingAmountTextView.setText(formatCurrency(remaining));
        } catch (NumberFormatException e) {
            binding.remainingAmountTextView.setText(formatCurrency(0.0));
        }
    }

    private double getAmountValue(EditText editText) {
        String text = editText.getText().toString().replace(",", "");
        return TextUtils.isEmpty(text) ? 0 : Double.parseDouble(text);
    }

    private void saveInvoice() {
        String accountName = binding.accountAutoComplete.getText().toString();
        Account selectedAccount = accountNameToObjectMap.get(accountName);
        selectedCurrency = binding.currencyAutoComplete.getText().toString();
        Integer currencyId = allAvailableCurrencies.stream()
                .filter(currency -> currency.name.equals(selectedCurrency))
                .findFirst().map(currency -> currency.id)
                .orElse(null);
        if (currencyId == null) {
            Toast.makeText(getContext(), R.string.wronge_currency, Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedAccount == null) {
            showIfAccountNull(() -> showAddNewAccountDialog(accountName, ""));
            return;
        }

        boolean isSales = binding.radioSales.isChecked();
        String billType = isSales ? getString(R.string.ar_text_6_2) : getString(R.string.ar_text_7_2);
        String subBillType = isSales ? getString(R.string.ar_long_text_45) : getString(R.string.ar_long_text_46);
        String subDetails = isSales ? getString(R.string.ar_long_text_47) : getString(R.string.ar_long_text_48);
        String details = binding.detailsEditText.getText().toString().trim();
        double invoiceValue = getAmountValue(binding.invoiceAmountEditText);
        double paidValue = getAmountValue(binding.paidAmountEditText);

        if (TextUtils.isEmpty(details)) {
            binding.detailsEditText.setError(getString(R.string.ar_text_5));
            return;
        }
        if (TextUtils.isEmpty(binding.invoiceAmountEditText.getText().toString().trim())) {
            binding.invoiceAmountEditText.setError(getString(R.string.ar_text_5));
            return;
        }
        if (currentMode != Mode.VIEW_EDIT) {

            if (!licenseManager.canCreateTransaction()) {
                int transactionsCount = SecureLicenseManager.getInstance(getContext()).getTransactionsCount();
                if (isGuest && transactionsCount >=100){// LicenseManager.FREE_TRANSACTION_LIMIT) {
                    if (!isAdded() || getContext() == null) return;
                    DialogHelper.show(
                            getContext(),
                            getString(R.string.login_1),
                            DialogHelper.FeedbackType.WARNING,
                            () -> {
                                Intent loginIntent = new Intent(getContext(), LoginActivity.class);
                                loginIntent.putExtra("registerGuest", true);
                                loginLauncher.launch(loginIntent);;
                                dismiss();
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


        AppDatabase.databaseWriteExecutor.execute(() -> {
            Account account = repository.getAccountDao().getAccountByIdBlocking(selectedAccount.getId());
            String currencyFirestoreId = repository.getCurrencyFirestoreId(currencyId);
            Log.d("InvoiceDialog", "Save - currentMode: " + currentMode +
                    ", invoiceTx: " + (invoiceTx != null ? "not null" : "null") +
                    ", encodedImage: " + (encodedImage != null ? "has image" : "null"));

            if (currentMode == Mode.VIEW_EDIT) {
                List<Transaction> updateInvoiceTransactions = new ArrayList<>();
                invoiceTx.setAccountId(account.getId());
                invoiceTx.setBillType(billType);
                invoiceTx.setAccountFirestoreId(account.getFirestoreId());
                if (encodedImage != null) {
                    invoiceTx.setImageEncoded(encodedImage);
                    Log.d("InvoiceDialog", "Set image to new invoiceTx");
                }
                invoiceTx.setDetails(details);
                invoiceTx.setAmount(invoiceValue);
                invoiceTx.setCurrencyId(currencyId);
                invoiceTx.setCurrencyFirestoreId(currencyFirestoreId);
                invoiceTx.setType(isSales ? 1 : -1);
                invoiceTx.setTimestamp(selectedDate.getTime());
                invoiceTx.setLastModified(System.currentTimeMillis());
                invoiceTx.setSyncStatus("EDITED");
                invoiceTx.setImageEncoded(encodedImage);
                updateInvoiceTransactions.add(invoiceTx);
                if (paidValue > 0) {
                    if (paymentTx != null) {
                        paymentTx.setAccountId(account.getId());
                        paymentTx.setBillType(subBillType);
                        paymentTx.setDetails(subDetails + " " + details);
                        paymentTx.setAmount(paidValue);
                        paymentTx.setAccountFirestoreId(account.getFirestoreId());
                        paymentTx.setCurrencyId(currencyId);
                        paymentTx.setType(isSales ? -1 : 1);
                        paymentTx.setTimestamp(selectedDate.getTime());
                        paymentTx.setLastModified(System.currentTimeMillis());
                        paymentTx.setSyncStatus("EDITED");
                        paymentTx.setCurrencyFirestoreId(currencyFirestoreId);
                        updateInvoiceTransactions.add(paymentTx);

                    } else {
                        paymentTx = new Transaction();
                        paymentTx.setAccountId(account.getId());
                        paymentTx.setImportID(editImportId);
                        paymentTx.setBillType(subBillType);
                        paymentTx.setDetails(subDetails + " " + details);
                        paymentTx.setAmount(paidValue);
                        paymentTx.setAccountFirestoreId(account.getFirestoreId());
                        paymentTx.setCurrencyId(currencyId);
                        paymentTx.setType(isSales ? -1 : 1);
                        paymentTx.setTimestamp(selectedDate.getTime());
                        paymentTx.setOwnerUID(uid);
                        paymentTx.setCurrencyFirestoreId(currencyFirestoreId);
                        paymentTx.setFirestoreId(UUIDGenerator.generateSequentialUUID());
                        paymentTx.setSyncStatus("NEW");
                        paymentTx.setLastModified(System.currentTimeMillis());
                        new TransactionNotificationManager(getContext()).
                                sendTransactionNotification(paymentTx,binding.currencyAutoComplete.getText().toString(),getContext());

                        updateInvoiceTransactions.add(paymentTx);
                    }
                } else if (paymentTx != null) {
                    paymentTx.setLastModified(System.currentTimeMillis());
                    paymentTx.setSyncStatus("DELETED");
                    updateInvoiceTransactions.add(paymentTx);
                }
                repository.updateInvoiceTransactions(updateInvoiceTransactions);
            } else {
                int nextImportId = repository.getMaxImportId() + 1;
                List<Transaction> invoiceTransactions = new ArrayList<>();
                Transaction invoiceTx = new Transaction();
                invoiceTx.setAccountId(account.getId());
                invoiceTx.setImportID(nextImportId);
                invoiceTx.setBillType(billType);
                invoiceTx.setAccountFirestoreId(account.getFirestoreId());
                invoiceTx.setDetails(details);
                invoiceTx.setAmount(invoiceValue);
                invoiceTx.setCurrencyId(currencyId);
                invoiceTx.setType(isSales ? 1 : -1);
                invoiceTx.setTimestamp(selectedDate.getTime());
                invoiceTx.setOwnerUID(uid);
                invoiceTx.setCurrencyFirestoreId(currencyFirestoreId);
                invoiceTx.setFirestoreId(UUIDGenerator.generateSequentialUUID());
                invoiceTx.setSyncStatus("NEW");
                invoiceTx.setImageEncoded(encodedImage);
                if (encodedImage != null) {
                    invoiceTx.setImageEncoded(encodedImage);
                    Log.d("InvoiceDialog", "Set image to new invoiceTx");
                }
                invoiceTx.setLastModified(System.currentTimeMillis());
                new TransactionNotificationManager(getContext()).
                        sendTransactionNotification(invoiceTx,binding.currencyAutoComplete.getText().toString(),getContext());

//                invoiceTransactions.add(invoiceTx);
                repository.insertInvoiceTransaction(invoiceTx);
                if (paidValue > 0) {
                    Transaction paymentTx = new Transaction();
                    paymentTx.setAccountId(account.getId());
                    paymentTx.setImportID(nextImportId);
                    paymentTx.setBillType(subBillType);
                    paymentTx.setDetails(subDetails + " " + details);
                    paymentTx.setAmount(paidValue);
                    paymentTx.setAccountFirestoreId(account.getFirestoreId());
                    paymentTx.setCurrencyId(currencyId);
                    paymentTx.setType(isSales ? -1 : 1);
                    paymentTx.setTimestamp(selectedDate.getTime());
                    paymentTx.setOwnerUID(uid);
                    paymentTx.setCurrencyFirestoreId(currencyFirestoreId);
                    paymentTx.setFirestoreId(UUIDGenerator.generateSequentialUUID());
                    paymentTx.setSyncStatus("NEW");
                    paymentTx.setLastModified(System.currentTimeMillis());
                    new TransactionNotificationManager(getContext())
                            .sendTransactionNotification(paymentTx,binding.currencyAutoComplete.getText().toString(),getContext());
                    invoiceTransactions.add(paymentTx);
                }

                repository.insertInvoiceTransactions(invoiceTransactions);
                licenseManager.incrementTransactionCount();
            }

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), getString(R.string.save_2), Toast.LENGTH_SHORT).show();
                    dismiss();
                });
            }
        });
    }

    private void showIfAccountNull(Runnable onConfirm) {
        String accountNames = binding.accountAutoComplete.getText().toString().trim();
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(getString(R.string.caution_titel))
                .setMessage(getString(R.string.info_null_Account, accountNames))
                .setPositiveButton(getString(R.string.yes), (dialog, which) -> onConfirm.run())
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    binding.accountAutoComplete.setText("");
                    binding.invoiceAmountEditText.setText("");
                    binding.remainingAmountTextView.setText("0.0");
                    binding.paidAmountEditText.setText("");
                    binding.detailsEditText.setText("");
                    binding.accountAutoComplete.requestFocus();
                })
                .setIcon(R.drawable.ic_alert)
                .show();
    }

    private void showNoOperationsDialog(String titel, String textInfo) {
        if (!isAdded() || getContext() == null) return;
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_no_operations, null);

        AlertDialog dialog = new MaterialAlertDialogBuilder(getContext())
                .setView(dialogView)
                .setCancelable(false)
                .create();
        TextView titelTextView = dialogView.findViewById(R.id.titel_textView);
        TextView noTransactionTextView = dialogView.findViewById(R.id.no_transaction_textView);
        MaterialButton purchaseBtn = dialogView.findViewById(R.id.btn_purchase);
        MaterialButton inviteBtn = dialogView.findViewById(R.id.btn_watch_ad);
        MaterialButton cancelBtn = dialogView.findViewById(R.id.btn_cancel);
        titelTextView.setText(titel);
        noTransactionTextView.setText(textInfo);
        purchaseBtn.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(getContext(), SubscriptionActivity.class);
            startActivity(intent);
//            handlePurchaseApp();
        });
        inviteBtn.setOnClickListener(v -> {
            dialog.dismiss();
            watchAd();
//            ReferralManager referralManager = new ReferralManager(getContext());
//            referralManager.generateAndShareReferralLink(FirebaseAuth.getInstance().getCurrentUser());
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
        binding.loadingOverlay.setVisibility(View.VISIBLE);

        Handler timeoutHandler = new Handler(Looper.getMainLooper());
        Runnable timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isAdded() || getContext() == null) return;
                // إذا وصلنا هنا، يعني أن 10 ثوانٍ مرت ولم يظهر الإعلان
                binding.loadingOverlay.setVisibility(View.GONE);
                DialogHelper.show(getContext(),
                        getString(R.string.ad_not_ready_message),
                        DialogHelper.FeedbackType.WARNING_2, null);
                Log.e("AdsTimeout", "تم تجاوز المهلة الزمنية (10 ثوانٍ) دون استجابة من AdsManager");
            }
        };
        timeoutHandler.postDelayed(timeoutRunnable, 10000);
        // 2. طلب الإعلان
        adsManager.showRewardedAd((Activity) getContext(), new AdsManager.AdListener() {
            @Override
            public void onAdLoaded() {
                timeoutHandler.removeCallbacks(timeoutRunnable);
                binding.loadingOverlay.setVisibility(View.GONE);
            }

            @Override
            public void onAdFailedToLoad(String error) {
                timeoutHandler.removeCallbacks(timeoutRunnable);
                binding.loadingOverlay.setVisibility(View.GONE);
                if (!isAdded() || getContext() == null) return;
                // Toast.makeText(getContext(), getString(R.string.setupListeners_02), Toast.LENGTH_LONG).show();
                DialogHelper.show(getContext(), getString(R.string.setupListeners_02), DialogHelper.FeedbackType.ERROR, null);

            }
/*

استخدام getContext() مباشرة دون التحقق: استخدم requireContext() فقط عندما تكون متأكداً 100% أن الـ Fragment متصل. في حالات عدم اليقين (مثل ردود الاتصال غير المتزامنة)، استخدم getContext() مع التحقق من null.
 */
            @Override
            public void onPointsEarned(int amount) {
                if (!isAdded() || getContext() == null) return;
                updatePointsInFirestore(amount);
            }

            @Override
            public void onAdClosed() {
                timeoutHandler.removeCallbacks(timeoutRunnable);
            }
        });
    }

    private void updatePointsInFirestore(int points) {
        SecureLicenseManager.getInstance(getContext()).setAdRewards(SecureLicenseManager.getInstance(getContext()).getAdRewards() + points);

        isGuest = SecureLicenseManager.getInstance(getContext()).isGuest();
        String userDoc="",userUID;
        if (isGuest) {
            userUID = SecureLicenseManager.getInstance(getContext()).guestUID();
            userDoc = "guests";
        }else{
            userUID=AppAuth.getUserID(getContext());
            userDoc="users";
        }
        assert userUID != null;
        Map<String, Object> updates = new HashMap<>();
        updates.put("ad_rewards", FieldValue.increment(points));
        updates.put("lastModified", System.currentTimeMillis());
        FirebaseFirestore.getInstance().collection(userDoc).document(userUID)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), getString(R.string.updatePointsInFirestore_10) + points + getString(R.string.updatePointsInFirestore_11), Toast.LENGTH_SHORT).show();

                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), getString(R.string.updatePointsInFirestore_12), Toast.LENGTH_SHORT).show());
    }

    private void handlePurchaseApp() {
        GoogleAuthHelper googleAuthHelper = new GoogleAuthHelper(getContext(), new LicenseManager(getContext()), repository);
        if (!googleAuthHelper.isSignedIn()) {
            Toast.makeText(getContext(), getString(R.string.login_1), Toast.LENGTH_SHORT).show();
            return;
        }

        PurchaseBottomSheet purchaseSheet = new PurchaseBottomSheet();
        purchaseSheet.show(getParentFragmentManager(), "PurchaseBottomSheet");
        dismiss();
    }

    private void deleteInvoice() {
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(getString(R.string.confirm_1))
                .setMessage(getString(R.string.confirm_delete_invoice))
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.yes_delete), (d, w) -> {
                    repository.deleteInvoiceByImportId(editImportId);
                    //Toast.makeText(getContext(), getString(R.string.success_delete_invoice), Toast.LENGTH_SHORT).show();
                    dismiss();
                })
                .show();
    }

    private String formatCurrency(double amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(2);
        return format.format(amount);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
    }
}