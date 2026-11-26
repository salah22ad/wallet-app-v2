package com.hpp.daftree.dialogs;

import static android.content.Context.MODE_PRIVATE;

import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.Transaction;
import com.hpp.daftree.databinding.DialogNewInvoiceBinding;
import com.hpp.daftree.helpers.AppFontHelper;
import com.hpp.daftree.helpers.ShareNotificationHelper;
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
import com.hpp.daftree.utils.LicenseManager;
import com.hpp.daftree.utils.SecureLicenseManager;
import com.hpp.daftree.viewmodels.CurrencyViewModel;
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

public class InvoiceDialog extends BottomSheetDialogFragment {

    private static final String TAG = "InvoiceDialog";
    private static final String ARG_MODE = "mode";
    private static final String ARG_IMPORT_ID = "import_id";
    private static final String ARG_CURRENCY = "currency";
    private static final String ARG_ACCOUNT_NAME = "account_name";
    private static final String ARG_IS_EDITED = "is_edited";

    private enum Mode { CREATE, VIEW_EDIT, CREATE_ACC }

    private DialogNewInvoiceBinding binding;
    private DaftreeRepository repository;
    private ReportsViewModel reportsViewModel;
    private CurrencyViewModel currencyViewModel;
    private SyncPreferences syncPreferences;
    private LicenseManager licenseManager;
    private AdsManager adsManager;

    private final ExecutorService imageProcessingExecutor = Executors.newSingleThreadExecutor();
    private final List<Currency> allAvailableCurrencies = new ArrayList<>();
    private final Map<String, Account> accountNameToObjectMap = new HashMap<>();
    private final List<String> accountNames = new ArrayList<>();
    private final Calendar selectedDate = Calendar.getInstance();

    private Mode currentMode = Mode.CREATE;
    private Transaction invoiceTx, paymentTx;
    private String selectedCurrency, accountName;
    private int editImportId = -1;
    private boolean isGuest;
    private String uid;
    private Uri photoUri;
    private String encodedImage = null;

    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> photoPickerLauncher;

    private final ActivityResultLauncher<Intent> loginLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {}
    );

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
        args.putString(ARG_IS_EDITED, billEdited);
        dialog.setArguments(args);
        return dialog;
    }

    public static InvoiceDialog newInstanceForAccount(String currency, String accountName) {
        InvoiceDialog dialog = new InvoiceDialog();
        Bundle args = new Bundle();
        args.putSerializable(ARG_MODE, Mode.CREATE_ACC);
        args.putString(ARG_CURRENCY, currency);
        args.putString(ARG_ACCOUNT_NAME, accountName);
        dialog.setArguments(args);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogNewInvoiceBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        AppFontHelper.applyFontToViewTree(requireContext(), binding.getRoot());
        MyApplication.applyGlobalTextWatcher(binding.getRoot());

        repository = new DaftreeRepository(requireActivity().getApplication());
        reportsViewModel = new ViewModelProvider(this).get(ReportsViewModel.class);
        currencyViewModel = new ViewModelProvider(this).get(CurrencyViewModel.class);
        licenseManager = new LicenseManager(requireContext());
        syncPreferences = new SyncPreferences(requireContext());
        adsManager = new AdsManager(getContext());

        isGuest = SecureLicenseManager.getInstance(requireContext()).isGuest();
        uid = isGuest
                ? SecureLicenseManager.getInstance(requireContext()).guestUID()
                : (FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : AppAuth.getUserID(requireContext()));

        setupInsets();
        FormatingAmount.applyTo(binding.invoiceAmountEditText);
        FormatingAmount.applyTo(binding.paidAmountEditText);

        setupObservers();
        setupListeners();
        setupModernImageLaunchers();

        if (getArguments() != null) {
            selectedCurrency = getArguments().getString(ARG_CURRENCY);
            accountName = getArguments().getString(ARG_ACCOUNT_NAME);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                currentMode = getArguments().getSerializable(ARG_MODE, Mode.class);
            } else {
                @SuppressWarnings("deprecation")
                Object ser = getArguments().getSerializable(ARG_MODE);
                currentMode = ser instanceof Mode ? (Mode) ser : Mode.CREATE;
            }
            if (currentMode == null) currentMode = Mode.CREATE;

            if (currentMode == Mode.VIEW_EDIT) {
                editImportId = getArguments().getInt(ARG_IMPORT_ID, -1);
                binding.titleTextView.setText(R.string.editr_invoice_tit);
                loadInvoiceData(editImportId);
            } else {
                binding.titleTextView.setText(R.string.new_invoice_tit);
                switchToCreateMode();
            }
        }
    }

    private void setupInsets() {
        final int baseBottom = binding.invoiceSheetContainer.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.invoiceSheetContainer, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), baseBottom + bars.bottom);
            return insets;
        });
    }

    private void setupObservers() {
        updateDateInView();

        reportsViewModel.getAllAccounts().observe(getViewLifecycleOwner(), accounts -> {
            if (accounts == null) return;
            accountNameToObjectMap.clear();
            accountNames.clear();
            for (Account acc : accounts) {
                if (acc.getAccountName() != null) {
                    accountNames.add(acc.getAccountName());
                    accountNameToObjectMap.put(acc.getAccountName().trim().toLowerCase(Locale.ROOT), acc);
                }
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, accountNames);
            binding.accountAutoComplete.setAdapter(adapter);
            int itemHeight = (int) (48 * getResources().getDisplayMetrics().density);
            binding.accountAutoComplete.setDropDownHeight(itemHeight * 4);
        });

        currencyViewModel.getAllCurrencies().observe(getViewLifecycleOwner(), currencies -> {
            if (currencies == null) return;
            allAvailableCurrencies.clear();
            allAvailableCurrencies.addAll(currencies);
            List<String> currencyNames = currencies.stream().map(c -> c.name).collect(Collectors.toList());
            ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, currencyNames);
            binding.currencyAutoComplete.setAdapter(adapter);
            int itemHeight = (int) (48 * getResources().getDisplayMetrics().density);
            binding.currencyAutoComplete.setDropDownHeight(itemHeight * 4);

            if (selectedCurrency != null && currencyNames.contains(selectedCurrency)) {
                binding.currencyAutoComplete.setText(selectedCurrency, false);
            } else if (!currencyNames.isEmpty() && binding.currencyAutoComplete.getText().toString().trim().isEmpty()) {
                binding.currencyAutoComplete.setText(currencyNames.get(0), false);
            }
        });
    }

    private void setupListeners() {
        binding.btnCloseInvoice.setOnClickListener(v -> dismiss());
        binding.btnCancel.setOnClickListener(v -> dismiss());
        binding.radioSales.setOnClickListener(v -> switchTab(true));
        binding.radioPurchases.setOnClickListener(v -> switchTab(false));
        binding.invoiceDateEditText.setOnClickListener(v -> showDatePicker());
        binding.btnAttachImage.setOnClickListener(v -> onImageIconClicked());

        TextWatcher amountWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { updateRemainingAmount(); }
        };
        binding.invoiceAmountEditText.addTextChangedListener(amountWatcher);
        binding.paidAmountEditText.addTextChangedListener(amountWatcher);

        binding.saveButton.setOnClickListener(v -> saveInvoice());
        binding.editButton.setOnClickListener(v -> switchToEditMode());
        binding.deleteButton.setOnClickListener(v -> deleteInvoice());
    }

    private void switchTab(boolean salesSelected) {
        binding.radioSales.setChecked(salesSelected);
        binding.radioPurchases.setChecked(!salesSelected);
        int activeText = Color.WHITE;
        int inactiveText = ContextCompat.getColor(requireContext(), R.color.budget_text_secondary);

        binding.radioSales.setBackgroundResource(salesSelected ? R.drawable.bg_tab_selected : 0);
        binding.radioSales.setTextColor(salesSelected ? activeText : inactiveText);
        binding.radioPurchases.setBackgroundResource(!salesSelected ? R.drawable.bg_tab_selected : 0);
        binding.radioPurchases.setTextColor(!salesSelected ? activeText : inactiveText);
    }

    private void switchToCreateMode() {
        setFieldsEnabled(true);
        binding.layoutSaveActions.setVisibility(View.VISIBLE);
        binding.layoutEditActions.setVisibility(View.GONE);
        switchTab(true);

        if (currentMode == Mode.CREATE_ACC && accountName != null && !accountName.isEmpty()) {
            binding.accountAutoComplete.setText(accountName, false);
            binding.accountAutoComplete.setEnabled(false);
        } else {
            binding.accountAutoComplete.setEnabled(true);
        }
        if (selectedCurrency != null) {
            binding.currencyAutoComplete.setText(selectedCurrency, false);
        }
    }

    private void switchToViewMode() {
        setFieldsEnabled(false);
        binding.layoutSaveActions.setVisibility(View.GONE);
        binding.layoutEditActions.setVisibility(View.VISIBLE);
    }

    private void switchToEditMode() {
        setFieldsEnabled(true);
        binding.accountAutoComplete.setEnabled(false);
        binding.layoutSaveActions.setVisibility(View.VISIBLE);
        binding.layoutEditActions.setVisibility(View.GONE);
    }

    private void setFieldsEnabled(boolean isEnabled) {
        binding.radioSales.setEnabled(isEnabled);
        binding.radioPurchases.setEnabled(isEnabled);
        binding.invoiceDateEditText.setEnabled(isEnabled);
        binding.detailsEditText.setEnabled(isEnabled);
        binding.invoiceAmountEditText.setEnabled(isEnabled);
        binding.paidAmountEditText.setEnabled(isEnabled);
        binding.currencyAutoComplete.setEnabled(isEnabled);
        binding.btnAttachImage.setEnabled(isEnabled);
    }

    private void loadInvoiceData(int importId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<Transaction> invoiceTxs = repository.getTransactionsByImportId(importId);
            if (invoiceTxs == null || invoiceTxs.isEmpty()) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), R.string.bill_not_found, Toast.LENGTH_SHORT).show();
                        dismiss();
                    });
                }
                return;
            }
            invoiceTx = null;
            paymentTx = null;
            if (invoiceTxs.size() == 1) {
                invoiceTx = invoiceTxs.get(0);
            } else {
                for (Transaction tx : invoiceTxs) {
                    String bt = tx.getBillType();
                    if (bt != null && bt.trim().split("\\s+").length == 1) {
                        invoiceTx = tx;
                    } else {
                        paymentTx = tx;
                    }
                }
                if (invoiceTx == null) invoiceTx = invoiceTxs.get(0);
            }

            Account account = repository.getAccountDao().getAccountByIdBlocking(invoiceTx.getAccountId());
            if (!isAdded() || getActivity() == null) return;

            getActivity().runOnUiThread(() -> {
                if (account != null) {
                    binding.accountAutoComplete.setText(account.getAccountName(), false);
                }
                String details = invoiceTx.getDetails() != null ? invoiceTx.getDetails() : "";
                binding.currencyAutoComplete.setText(selectedCurrency, false);
                if (invoiceTx.getTimestamp() != null) {
                    selectedDate.setTime(invoiceTx.getTimestamp());
                    updateDateInView();
                }

                boolean isSales = invoiceTx.getType() == 1
                        || (invoiceTx.getBillType() != null && invoiceTx.getBillType().contains(getString(R.string.ar_text_6_2)));
                switchTab(isSales);

                binding.invoiceAmountEditText.setText(formatCurrency(invoiceTx.getAmount()));
                binding.paidAmountEditText.setText(paymentTx != null ? formatCurrency(paymentTx.getAmount()) : "0");
                binding.detailsEditText.setText(details.trim());

                encodedImage = (invoiceTx.getImageEncoded() != null && !invoiceTx.getImageEncoded().isEmpty())
                        ? invoiceTx.getImageEncoded() : null;
                if (encodedImage != null) {
                    binding.btnAttachImage.setColorFilter(Color.GREEN);
                }
                switchToViewMode();
            });
        });
    }

    private void openCreateAccountSheet(String prefillName) {
        AccountBottomSheet sheet = AccountBottomSheet.newInstanceForCreate(prefillName);
        sheet.setAccountActionListener(new AccountBottomSheet.AccountActionListener() {
            @Override
            public void onAccountSaved(Account account, boolean isNew) {
                if (account != null && binding != null) {
                    binding.accountAutoComplete.setText(account.getAccountName(), false);
                    accountNameToObjectMap.put(account.getAccountName().trim().toLowerCase(Locale.ROOT), account);
                }
            }
        });
        sheet.show(getParentFragmentManager(), "AccountBottomSheetFromInvoice");
    }

    private void saveInvoice() {
        String accName = binding.accountAutoComplete.getText() != null
                ? binding.accountAutoComplete.getText().toString().trim() : "";
        Account selectedAccount = accountNameToObjectMap.get(accName.toLowerCase(Locale.ROOT));

        selectedCurrency = binding.currencyAutoComplete.getText() != null
                ? binding.currencyAutoComplete.getText().toString().trim() : "";
        Integer currencyId = allAvailableCurrencies.stream()
                .filter(c -> c.name.equals(selectedCurrency))
                .findFirst().map(c -> c.id).orElse(null);

        if (currencyId == null) {
            Toast.makeText(getContext(), R.string.wronge_currency, Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedAccount == null) {
            showIfAccountNull(() -> openCreateAccountSheet(accName));
            return;
        }

        boolean isSales = binding.radioSales.isChecked();
        String billType = isSales ? getString(R.string.ar_text_6_2) : getString(R.string.ar_text_7_2);
        String subBillType = isSales ? getString(R.string.ar_long_text_45) : getString(R.string.ar_long_text_46);
        String subDetails = isSales ? getString(R.string.ar_long_text_47) : getString(R.string.ar_long_text_48);
        String details = binding.detailsEditText.getText() != null
                ? binding.detailsEditText.getText().toString().trim() : "";
        String invoiceAmtRaw = binding.invoiceAmountEditText.getText() != null
                ? binding.invoiceAmountEditText.getText().toString().trim() : "";
        double invoiceValue = getAmountValue(binding.invoiceAmountEditText);
        double paidValue = getAmountValue(binding.paidAmountEditText);

        if (TextUtils.isEmpty(details)) {
            binding.detailsEditText.setError(getString(R.string.ar_text_5));
            return;
        }
        if (TextUtils.isEmpty(invoiceAmtRaw) || invoiceValue <= 0) {
            binding.invoiceAmountEditText.setError(getString(R.string.ar_text_5));
            return;
        }

        if (currentMode != Mode.VIEW_EDIT && !licenseManager.canCreateTransaction()) {
            int transactionsCount = SecureLicenseManager.getInstance(requireContext()).getTransactionsCount();
            if (isGuest && transactionsCount >= 100) {
                DialogHelper.show(
                        requireContext(),
                        getString(R.string.login_1),
                        DialogHelper.FeedbackType.WARNING,
                        () -> {
                            Intent loginIntent = new Intent(getContext(), LoginActivity.class);
                            loginIntent.putExtra("registerGuest", true);
                            loginLauncher.launch(loginIntent);
                            dismiss();
                        },
                        null);
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

        final FragmentActivity hostActivity = getActivity();
        final String finalCurrencyName = selectedCurrency;
        final int finalCurrencyId = currencyId;

        AppDatabase.databaseWriteExecutor.execute(() -> {
            Account account = repository.getAccountDao().getAccountByIdBlocking(selectedAccount.getId());
            if (account == null) return;
            String currencyFirestoreId = repository.getCurrencyFirestoreId(finalCurrencyId);
            if (currencyFirestoreId == null) currencyFirestoreId = "";

            if (currentMode == Mode.VIEW_EDIT && invoiceTx != null) {
                List<Transaction> updates = new ArrayList<>();
                invoiceTx.setAccountId(account.getId());
                invoiceTx.setBillType(billType);
                invoiceTx.setAccountFirestoreId(account.getFirestoreId());
                invoiceTx.setDetails(details);
                invoiceTx.setAmount(invoiceValue);
                invoiceTx.setCurrencyId(finalCurrencyId);
                invoiceTx.setCurrencyFirestoreId(currencyFirestoreId);
                invoiceTx.setType(isSales ? 1 : -1);
                invoiceTx.setTimestamp(selectedDate.getTime());
                invoiceTx.setLastModified(System.currentTimeMillis());
                invoiceTx.setSyncStatus("EDITED");
                invoiceTx.setImageEncoded(encodedImage);
                updates.add(invoiceTx);

                if (paidValue > 0) {
                    if (paymentTx != null) {
                        paymentTx.setAccountId(account.getId());
                        paymentTx.setBillType(subBillType);
                        paymentTx.setDetails(subDetails + " " + details);
                        paymentTx.setAmount(paidValue);
                        paymentTx.setAccountFirestoreId(account.getFirestoreId());
                        paymentTx.setCurrencyId(finalCurrencyId);
                        paymentTx.setType(isSales ? -1 : 1);
                        paymentTx.setTimestamp(selectedDate.getTime());
                        paymentTx.setLastModified(System.currentTimeMillis());
                        paymentTx.setSyncStatus("EDITED");
                        paymentTx.setCurrencyFirestoreId(currencyFirestoreId);
                        updates.add(paymentTx);
                    } else {
                        paymentTx = new Transaction();
                        paymentTx.setAccountId(account.getId());
                        paymentTx.setImportID(editImportId);
                        paymentTx.setBillType(subBillType);
                        paymentTx.setDetails(subDetails + " " + details);
                        paymentTx.setAmount(paidValue);
                        paymentTx.setAccountFirestoreId(account.getFirestoreId());
                        paymentTx.setCurrencyId(finalCurrencyId);
                        paymentTx.setType(isSales ? -1 : 1);
                        paymentTx.setTimestamp(selectedDate.getTime());
                        paymentTx.setOwnerUID(uid);
                        paymentTx.setCurrencyFirestoreId(currencyFirestoreId);
                        paymentTx.setFirestoreId(UUIDGenerator.generateSequentialUUID());
                        paymentTx.setSyncStatus("NEW");
                        paymentTx.setLastModified(System.currentTimeMillis());
                        updates.add(paymentTx);
                    }
                } else if (paymentTx != null) {
                    paymentTx.setLastModified(System.currentTimeMillis());
                    paymentTx.setSyncStatus("DELETED");
                    updates.add(paymentTx);
                }
                repository.updateInvoiceTransactions(updates);

                if (hostActivity != null) {
                    hostActivity.runOnUiThread(() -> {
                        Toast.makeText(hostActivity, R.string.save_2, Toast.LENGTH_SHORT).show();
                        dismissAllowingStateLoss();
                    });
                }
            } else {
                int nextImportId = repository.getMaxImportId() + 1;
                AppDatabase db = AppDatabase.getDatabase(hostActivity.getApplicationContext());
                int createdCount = 1;

                Transaction newInvTx = new Transaction();
                newInvTx.setAccountId(account.getId());
                newInvTx.setImportID(nextImportId);
                newInvTx.setBillType(billType);
                newInvTx.setAccountFirestoreId(account.getFirestoreId());
                newInvTx.setDetails(details);
                newInvTx.setAmount(invoiceValue);
                newInvTx.setCurrencyId(finalCurrencyId);
                newInvTx.setType(isSales ? 1 : -1);
                newInvTx.setTimestamp(selectedDate.getTime());
                newInvTx.setOwnerUID(uid);
                newInvTx.setCurrencyFirestoreId(currencyFirestoreId);
                newInvTx.setFirestoreId(UUIDGenerator.generateSequentialUUID());
                newInvTx.setSyncStatus("NEW");
                newInvTx.setImageEncoded(encodedImage);
                newInvTx.setLastModified(System.currentTimeMillis());
                db.transactionDao().insert(newInvTx);

                new TransactionNotificationManager(hostActivity)
                        .sendTransactionNotification(newInvTx, finalCurrencyName, hostActivity);

                if (paidValue > 0) {
                    createdCount = 2;
                    Transaction newPayTx = new Transaction();
                    newPayTx.setAccountId(account.getId());
                    newPayTx.setImportID(nextImportId);
                    newPayTx.setBillType(subBillType);
                    newPayTx.setDetails(subDetails + " " + details);
                    newPayTx.setAmount(paidValue);
                    newPayTx.setAccountFirestoreId(account.getFirestoreId());
                    newPayTx.setCurrencyId(finalCurrencyId);
                    newPayTx.setType(isSales ? -1 : 1);
                    newPayTx.setTimestamp(selectedDate.getTime());
                    newPayTx.setOwnerUID(uid);
                    newPayTx.setCurrencyFirestoreId(currencyFirestoreId);
                    newPayTx.setFirestoreId(UUIDGenerator.generateSequentialUUID());
                    newPayTx.setSyncStatus("NEW");
                    newPayTx.setLastModified(System.currentTimeMillis());
                    db.transactionDao().insert(newPayTx);

                    new TransactionNotificationManager(hostActivity)
                            .sendTransactionNotification(newPayTx, finalCurrencyName, hostActivity);
                }

                db.accountDao().updateLastTransDate(account.getId(), System.currentTimeMillis());
                repository.triggerSync();
                licenseManager.incrementTransactionCount();

                final int finalCreatedCount = createdCount;
                if (hostActivity != null) {
                    hostActivity.runOnUiThread(() -> {
                        Toast.makeText(hostActivity, R.string.save_2, Toast.LENGTH_SHORT).show();
                        dismissAllowingStateLoss();
                        ShareNotificationHelper.handleAfterInvoiceSaved(
                                hostActivity, account.getId(), finalCurrencyId, finalCurrencyName,
                                isSales, invoiceValue, paidValue, details, selectedDate.getTime(), finalCreatedCount);
                    });
                }
            }
        });
    }

    private void showIfAccountNull(Runnable onConfirm) {
        String accName = binding.accountAutoComplete.getText() != null
                ? binding.accountAutoComplete.getText().toString().trim() : "";
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.caution_titel)
                .setMessage(getString(R.string.info_null_Account, accName))
                .setPositiveButton(R.string.yes, (dialog, which) -> onConfirm.run())
                .setNegativeButton(R.string.cancel, null)
                .setIcon(R.drawable.ic_alert)
                .show();
    }

    private void showDatePicker() {
        new DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
            selectedDate.set(Calendar.YEAR, year);
            selectedDate.set(Calendar.MONTH, month);
            selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            updateDateInView();
        }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDateInView() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.US);
        binding.invoiceDateEditText.setText(sdf.format(selectedDate.getTime()));
    }

    private void updateRemainingAmount() {
        try {
            double invoiceValue = FormatingAmount.parseAmount(
                    binding.invoiceAmountEditText.getText() != null ? binding.invoiceAmountEditText.getText().toString().trim() : "");
            double paidValue = FormatingAmount.parseAmount(
                    binding.paidAmountEditText.getText() != null ? binding.paidAmountEditText.getText().toString().trim() : "");
            binding.remainingAmountTextView.setText(formatCurrency(invoiceValue - paidValue));
        } catch (NumberFormatException e) {
            binding.remainingAmountTextView.setText("0");
        }
    }

    private double getAmountValue(EditText editText) {
        String text = editText.getText() != null ? editText.getText().toString().replace(",", "").trim() : "";
        if (TextUtils.isEmpty(text)) return 0.0;
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private void deleteInvoice() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.confirm_1)
                .setMessage(R.string.confirm_delete_invoice)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.yes_delete, (d, w) -> {
                    repository.deleteInvoiceByImportId(editImportId);
                    dismiss();
                })
                .show();
    }

    private void setupModernImageLaunchers() {
        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) openCamera();
                }
        );

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (photoUri == null) return;
                    try {
                        InputStream is = requireContext().getContentResolver().openInputStream(photoUri);
                        Bitmap bitmap = BitmapFactory.decodeStream(is);
                        if (bitmap != null) processAndSetImage(bitmap);
                    } catch (Exception e) {
                        Log.e(TAG, "Camera capture failed", e);
                    }
                }
        );

        photoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                uri -> {
                    if (uri != null) {
                        try {
                            InputStream is = requireContext().getContentResolver().openInputStream(uri);
                            Bitmap bitmap = BitmapFactory.decodeStream(is);
                            if (bitmap != null) processAndSetImage(bitmap);
                        } catch (Exception e) {
                            Log.e(TAG, "Photo picker failed", e);
                        }
                    }
                }
        );
    }

    private void processAndSetImage(Bitmap bitmap) {
        binding.loadingOverlay.setVisibility(View.VISIBLE);
        imageProcessingExecutor.execute(() -> {
            try {
                String processed = ImageUtils.bitmapToBase64(requireContext(), bitmap);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        encodedImage = processed;
                        binding.btnAttachImage.setColorFilter(Color.GREEN);
                        binding.loadingOverlay.setVisibility(View.GONE);
                        Toast.makeText(requireContext(), R.string.image_attached_success, Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> binding.loadingOverlay.setVisibility(View.GONE));
                }
            }
        });
    }

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
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
        cameraLauncher.launch(intent);
    }

    private boolean isTrialValid() {
        SharedPreferences prefs = requireContext().getSharedPreferences("AppPrefs", MODE_PRIVATE);
        long trialExpire = prefs.getLong("trial_expire", 0);
        return trialExpire > 0 && System.currentTimeMillis() < trialExpire;
    }

    private void onImageIconClicked() {
        if (!SecureLicenseManager.getInstance(requireContext()).isPremium() && !isTrialValid()) {
            DialogHelper.show(requireContext(), getString(R.string.add_image_premium), DialogHelper.FeedbackType.ERROR, null, null);
            return;
        }
        PopupMenu popup = new PopupMenu(requireContext(), binding.btnAttachImage);
        if (encodedImage != null) {
            popup.getMenu().add(0, 1, 0, getString(R.string.show_photo));
            popup.getMenu().add(0, 4, 3, getString(R.string.delete_photo));
        }
        popup.getMenu().add(0, 2, 1, getString(R.string.camera));
        popup.getMenu().add(0, 3, 2, getString(R.string.gallary));

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                Bitmap bitmap = ImageUtils.base64ToBitmap(encodedImage);
                if (bitmap != null) {
                    Dialog d = new Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
                    ImageView iv = new ImageView(requireContext());
                    iv.setImageBitmap(bitmap);
                    d.setContentView(iv);
                    d.show();
                }
                return true;
            } else if (id == 2) {
                checkCameraPermissionAndOpen();
                return true;
            } else if (id == 3) {
                photoPickerLauncher.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build());
                return true;
            } else if (id == 4) {
                encodedImage = null;
                binding.btnAttachImage.clearColorFilter();
                Toast.makeText(requireContext(), R.string.image_deleted, Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showNoOperationsDialog(String title, String textInfo) {
        if (!isAdded() || getContext() == null) return;
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_no_operations, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView).setCancelable(false).create();
        TextView tvTitle = dialogView.findViewById(R.id.titel_textView);
        TextView tvMsg = dialogView.findViewById(R.id.no_transaction_textView);
        MaterialButton purchaseBtn = dialogView.findViewById(R.id.btn_purchase);
        MaterialButton watchBtn = dialogView.findViewById(R.id.btn_watch_ad);
        MaterialButton cancelBtn = dialogView.findViewById(R.id.btn_cancel);
        tvTitle.setText(title);
        tvMsg.setText(textInfo);
        purchaseBtn.setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(getContext(), SubscriptionActivity.class));
        });
        watchBtn.setOnClickListener(v -> {
            dialog.dismiss();
            watchAd();
        });
        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void watchAd() {
        binding.loadingOverlay.setVisibility(View.VISIBLE);
        Handler timeoutHandler = new Handler(Looper.getMainLooper());
        Runnable timeoutRunnable = () -> {
            if (!isAdded() || getContext() == null) return;
            binding.loadingOverlay.setVisibility(View.GONE);
            DialogHelper.show(getContext(), getString(R.string.ad_not_ready_message), DialogHelper.FeedbackType.WARNING_2, null);
        };
        timeoutHandler.postDelayed(timeoutRunnable, 10000);
        adsManager.showRewardedAd((Activity) requireContext(), new AdsManager.AdListener() {
            @Override public void onAdLoaded() {
                timeoutHandler.removeCallbacks(timeoutRunnable);
                if (binding != null) binding.loadingOverlay.setVisibility(View.GONE);
            }
            @Override public void onAdFailedToLoad(String error) {
                timeoutHandler.removeCallbacks(timeoutRunnable);
                if (binding != null) binding.loadingOverlay.setVisibility(View.GONE);
                if (isAdded() && getContext() != null) {
                    DialogHelper.show(getContext(), getString(R.string.setupListeners_02), DialogHelper.FeedbackType.ERROR, null);
                }
            }
            @Override public void onPointsEarned(int amount) {
                if (!isAdded() || getContext() == null) return;
                SecureLicenseManager mgr = SecureLicenseManager.getInstance(requireContext());
                mgr.setAdRewards(mgr.getAdRewards() + amount);
                String userDoc = mgr.isGuest() ? "guests" : "users";
                String userUid = mgr.isGuest() ? mgr.guestUID() : AppAuth.getUserID(requireContext());
                if (userUid != null && !userUid.isEmpty()) {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("ad_rewards", FieldValue.increment(amount));
                    updates.put("lastModified", System.currentTimeMillis());
                    FirebaseFirestore.getInstance().collection(userDoc).document(userUid).set(updates, SetOptions.merge());
                }
            }
            @Override public void onAdClosed() {
                timeoutHandler.removeCallbacks(timeoutRunnable);
            }
        });
    }

    private String formatCurrency(double amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(2);
        return format.format(amount);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null) {
            View bottomSheet = getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundColor(Color.TRANSPARENT);
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setFitToContents(true);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        }
    }
}
