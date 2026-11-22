package com.hpp.daftree.dialogs;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.User;
import com.hpp.daftree.databinding.BottomSheetReportsBinding;
import com.hpp.daftree.helpers.LanguageManager;
import com.hpp.daftree.models.ReportGenerator;
import com.hpp.daftree.utils.DialogHelper;
import com.hpp.daftree.utils.SecureLicenseManager;
import com.hpp.daftree.viewmodels.CurrencyViewModel;
import com.hpp.daftree.viewmodels.ReportsViewModel;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class ReportsBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetReportsBinding binding;
    private ReportsViewModel viewModel;
    private CurrencyViewModel currencyViewModel;
    private AlertDialog progressDialog;
    private User currentUser;
    private MaterialSwitch switchSeparateAccounts;
    private LinearLayout llSeparateAccounts;
    private List<Account> accountsList = new ArrayList<>();
    private List<AccountType> accountTypesList = new ArrayList<>();
    private List<Currency> allAvailableCurrencies = new ArrayList<>();

    private final Calendar startDate = Calendar.getInstance();
    private final Calendar endDate = Calendar.getInstance();

    private static final String ARG_LAUNCH_MODE = "launch_mode";
    private static final String ARG_ACCOUNT_ID = "account_id";
    private static final String ARG_ACCOUNT_NAME = "account_name";
    private static final String ARG_ACCOUNT_TYPE_NAME = "account_type_name";
    private static final String ARG_ACCOUNT_TYPE_ID = "account_type_id";
    private static final String ARG_CURRENCY_NAME = "currency_name";
    private static final String ARG_PHONE_NUMBER = "phone_number";
    private String phoneNumber = "";

    public enum LaunchMode {
        GENERAL,        // من القائمة الجانبية
        SINGLE_ACCOUNT, // من تفاصيل الحساب
        FILTERED        // من الفلتر في الواجهة الرئيسية
    }

    private LanguageManager languageManager;
    private LaunchMode currentMode = LaunchMode.GENERAL;
    private int selectedReportType = 1; // 1: Statement, 2: Balance, 3: Monthly
    private boolean isGuest;
    private String guestUID;
    private String pendingAccountTypeName = null;
    private String pendingAccountTypeId = null;

    public static ReportsBottomSheet newInstanceGeneral() {
        ReportsBottomSheet fragment = new ReportsBottomSheet();
        Bundle args = new Bundle();
        args.putSerializable(ARG_LAUNCH_MODE, LaunchMode.GENERAL);
        fragment.setArguments(args);
        return fragment;
    }

    public static ReportsBottomSheet newInstanceForAccount(int accountId, String accountName,
                                                           String currency, String phoneNumber) {
        ReportsBottomSheet fragment = new ReportsBottomSheet();
        Bundle args = new Bundle();
        args.putSerializable(ARG_LAUNCH_MODE, LaunchMode.SINGLE_ACCOUNT);
        args.putInt(ARG_ACCOUNT_ID, accountId);
        args.putString(ARG_ACCOUNT_NAME, accountName);
        args.putString(ARG_CURRENCY_NAME, currency);
        args.putString(ARG_PHONE_NUMBER, phoneNumber);
        fragment.setArguments(args);
        return fragment;
    }

    public static ReportsBottomSheet newInstanceFiltered(
            String accountTypeName, String accountTypeId,
            String currency, boolean isAllAccounts) {
        ReportsBottomSheet fragment = new ReportsBottomSheet();
        Bundle args = new Bundle();
        args.putSerializable(ARG_LAUNCH_MODE, LaunchMode.FILTERED);
        if (!isAllAccounts) {
            if (accountTypeName != null && !accountTypeName.trim().isEmpty()) {
                args.putString(ARG_ACCOUNT_TYPE_NAME, accountTypeName.trim());
            }
            if (accountTypeId != null && !accountTypeId.trim().isEmpty()) {
                args.putString(ARG_ACCOUNT_TYPE_ID, accountTypeId.trim());
            }
        }
        args.putString(ARG_CURRENCY_NAME, currency);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetReportsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // إلغاء أي LayoutTransition لمنع قفز الـ BottomSheet لأعلى الشاشة عند إظهار أو إخفاء العناصر
        disableLayoutTransitions(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(ReportsViewModel.class);
        currencyViewModel = new ViewModelProvider(this).get(CurrencyViewModel.class);
        languageManager = LanguageManager.getInstance(getContext());
        String lang = languageManager.getCurrentLanguage();
        Locale locale = Locale.forLanguageTag(lang);
        Locale.setDefault(locale);
        MyApplication.applyGlobalTextWatcher(binding.getRoot());

        isGuest = SecureLicenseManager.getInstance(getContext()).isGuest();
        guestUID = SecureLicenseManager.getInstance(getContext()).guestUID();
        Configuration config = new Configuration(requireContext().getResources().getConfiguration());
        config.setLocale(locale);
        config.setLayoutDirection(locale);
        requireContext().createConfigurationContext(config);

        if (getArguments() != null) {
            currentMode = (LaunchMode) getArguments().getSerializable(ARG_LAUNCH_MODE);
        }

        setupBottomBarInsets();
        setupUI();
        setupObservers();
        loadDefaultDates();
    }

    private void disableLayoutTransitions(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            vg.setLayoutTransition(null);
            for (int i = 0; i < vg.getChildCount(); i++) {
                disableLayoutTransitions(vg.getChildAt(i));
            }
        }
    }

    private void setupBottomBarInsets() {
        final int baseBottomPadding = binding.contentContainer.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.contentContainer, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    v.getPaddingLeft(),
                    v.getPaddingTop(),
                    v.getPaddingRight(),
                    baseBottomPadding + systemBars.bottom
            );
            return insets;
        });
    }

    private void setupUI() {
        llSeparateAccounts = binding.llSeparateAccounts;
        switchSeparateAccounts = binding.switchSeparateAccounts;
        llSeparateAccounts.setVisibility(View.GONE);

        // 1. إعداد Chips (نطاق التقرير) بدون استدعاء حركات تسبب القفز
        binding.chipGroupScope.setOnCheckedChangeListener((group, checkedId) -> {
            updateScopeVisibility(checkedId);
        });

        // 2. إعداد Cards (نوع التقرير)
        View.OnClickListener cardClickListener = v -> updateReportTypeSelection((MaterialCardView) v);
        binding.cardTypeStatement.setOnClickListener(cardClickListener);
        binding.cardTypeBalance.setOnClickListener(cardClickListener);
        binding.cardTypeMonthly.setOnClickListener(cardClickListener);

        // افتراضياً: كشف حساب تفصيلي
        updateReportTypeSelection(binding.cardTypeStatement);

        // 3. إعداد مفاتيح الفلاتر بدون TransitionManager لمنع ارتداد النافذة
        binding.switchDateRange.setOnCheckedChangeListener((buttonView, isChecked) -> {
            binding.containerDateRange.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        binding.switchCurrency.setOnCheckedChangeListener((buttonView, isChecked) -> {
            binding.containerCurrency.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // 4. إعداد منتقي التاريخ
        binding.etStartDate.setOnClickListener(v -> showDatePicker(true));
        binding.etEndDate.setOnClickListener(v -> showDatePicker(false));

        // 5. إظهار أزرار PDF فقط (إنشاء تقرير PDF + مشاركة التقرير PDF) وإخفاء زر PNG
        if (binding.btnSharePng != null) {
            binding.btnSharePng.setVisibility(View.GONE);
        }
        binding.btnGenerateReport.setVisibility(View.VISIBLE);
        binding.btnShareReport.setVisibility(View.VISIBLE);

        binding.btnGenerateReport.setOnClickListener(v -> generateReport(false));
        binding.btnShareReport.setOnClickListener(v -> generateReport(true));

        // تطبيق المنطق بناءً على وضع الإطلاق
        applyLaunchModeLogic();
    }

    private void updateSeparateAccountsVisibility() {
        if (llSeparateAccounts == null || switchSeparateAccounts == null) return;
        boolean isAccountTypeScope = binding.chipScopeAccountType.isChecked();
        boolean isStatementType = (selectedReportType == 1);
        if (isAccountTypeScope && isStatementType) {
            llSeparateAccounts.setVisibility(View.VISIBLE);
        } else {
            llSeparateAccounts.setVisibility(View.GONE);
            switchSeparateAccounts.setChecked(false);
        }
    }

    private void applyLaunchModeLogic() {
        if (getArguments() == null) return;

        switch (currentMode) {
            case SINGLE_ACCOUNT: {
                binding.chipScopeSingleAccount.setChecked(true);
                binding.chipScopeAccountType.setVisibility(View.GONE);
                binding.chipScopeAllAccounts.setVisibility(View.GONE);

                String accName = getArguments().getString(ARG_ACCOUNT_NAME);
                phoneNumber = getArguments().getString(ARG_PHONE_NUMBER);
                binding.spinnerAccounts.setText(accName, false);
                binding.spinnerAccounts.setEnabled(false);

                setupCurrencyIfExists(getArguments().getString(ARG_CURRENCY_NAME));
                break;
            }
            case FILTERED: {
                String typeName = getArguments().getString(ARG_ACCOUNT_TYPE_NAME);
                String typeId = getArguments().getString(ARG_ACCOUNT_TYPE_ID);

                pendingAccountTypeName = (typeName != null && !typeName.trim().isEmpty()) ? typeName.trim() : null;
                pendingAccountTypeId = (typeId != null && !typeId.trim().isEmpty()) ? typeId.trim() : null;

                if (pendingAccountTypeName != null || pendingAccountTypeId != null) {
                    binding.chipScopeAccountType.setChecked(true);
                    binding.layoutAccountTypeSpinner.setVisibility(View.VISIBLE);
                    binding.layoutAccountSpinner.setVisibility(View.GONE);

                    if (pendingAccountTypeName != null) {
                        binding.spinnerAccountType.setText(pendingAccountTypeName, false);
                    }
                } else {
                    binding.chipScopeAllAccounts.setChecked(true);
                    binding.layoutAccountTypeSpinner.setVisibility(View.GONE);
                    binding.layoutAccountSpinner.setVisibility(View.GONE);
                }
                setupCurrencyIfExists(getArguments().getString(ARG_CURRENCY_NAME));
                break;
            }
            case GENERAL:
                binding.chipScopeAllAccounts.setChecked(true);
                binding.layoutAccountTypeSpinner.setVisibility(View.GONE);
                binding.layoutAccountSpinner.setVisibility(View.GONE);
                setupCurrencyIfExists(getArguments().getString(ARG_CURRENCY_NAME));
                break;
            default:
                binding.chipScopeSingleAccount.setChecked(true);
                break;
        }
        updateSeparateAccountsVisibility();
    }

    private void setupCurrencyIfExists(String currencyName) {
        if (currencyName != null && !currencyName.isEmpty()) {
            binding.switchCurrency.setChecked(false);
            binding.spinnerCurrency.setText(currencyName, false);
        }
    }

    private void updateScopeVisibility(int checkedId) {
        if (checkedId == R.id.chipScopeSingleAccount) {
            binding.layoutAccountSpinner.setVisibility(View.VISIBLE);
            binding.layoutAccountTypeSpinner.setVisibility(View.GONE);
            if (binding.spinnerAccounts.getText().toString().trim().isEmpty()
                    && accountsList != null && !accountsList.isEmpty()) {
                binding.spinnerAccounts.setText(accountsList.get(0).getAccountName(), false);
            }
        } else if (checkedId == R.id.chipScopeAccountType) {
            binding.layoutAccountSpinner.setVisibility(View.GONE);
            binding.layoutAccountTypeSpinner.setVisibility(View.VISIBLE);
            if (binding.spinnerAccountType.getText().toString().trim().isEmpty()
                    && accountTypesList != null && !accountTypesList.isEmpty()) {
                String fallbackType = (pendingAccountTypeName != null && !pendingAccountTypeName.isEmpty())
                        ? pendingAccountTypeName
                        : accountTypesList.get(0).getName();
                binding.spinnerAccountType.setText(fallbackType, false);
            }
        } else {
            binding.layoutAccountSpinner.setVisibility(View.GONE);
            binding.layoutAccountTypeSpinner.setVisibility(View.GONE);
        }
        updateSeparateAccountsVisibility();
    }

    private void updateReportTypeSelection(MaterialCardView selectedCard) {
        binding.cardTypeStatement.setChecked(false);
        binding.cardTypeBalance.setChecked(false);
        binding.cardTypeMonthly.setChecked(false);

        selectedCard.setChecked(true);

        if (selectedCard == binding.cardTypeStatement) selectedReportType = 1;
        else if (selectedCard == binding.cardTypeBalance) selectedReportType = 2;
        else if (selectedCard == binding.cardTypeMonthly) selectedReportType = 3;
        updateSeparateAccountsVisibility();
    }

    private void setupObservers() {
        viewModel.getUserProfile().observe(getViewLifecycleOwner(), user -> this.currentUser = user);

        viewModel.getAllAccounts().observe(getViewLifecycleOwner(), accounts -> {
            if (accounts != null) {
                this.accountsList = accounts;
                List<String> names = accounts.stream().map(Account::getAccountName).collect(Collectors.toList());
                ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, names);
                binding.spinnerAccounts.setAdapter(adapter);

                if (currentMode != LaunchMode.SINGLE_ACCOUNT && !names.isEmpty()
                        && binding.spinnerAccounts.getText().toString().trim().isEmpty()) {
                    binding.spinnerAccounts.setText(names.get(0), false);
                }
            }
        });

        viewModel.getAllAccountTypes().observe(getViewLifecycleOwner(), types -> {
            if (types == null) return;

            this.accountTypesList = types;
            List<String> names = types.stream()
                    .map(AccountType::getName)
                    .collect(Collectors.toList());

            ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_dropdown_item_1line, names);
            binding.spinnerAccountType.setAdapter(adapter);

            if (currentMode == LaunchMode.FILTERED && (pendingAccountTypeName != null || pendingAccountTypeId != null)) {
                String resolved = null;
                if (pendingAccountTypeId != null) {
                    for (AccountType t : types) {
                        if (t.getFirestoreId() != null && pendingAccountTypeId.equals(t.getFirestoreId().trim())) {
                            resolved = t.getName();
                            break;
                        }
                    }
                }
                if (resolved == null && pendingAccountTypeName != null) {
                    for (String n : names) {
                        if (n != null && n.trim().equalsIgnoreCase(pendingAccountTypeName.trim())) {
                            resolved = n;
                            break;
                        }
                    }
                }

                if (resolved != null) {
                    pendingAccountTypeName = resolved;
                    binding.spinnerAccountType.setText(resolved, false);
                    if (!binding.chipScopeAccountType.isChecked()) {
                        binding.chipScopeAccountType.setChecked(true);
                    }
                    binding.layoutAccountTypeSpinner.setVisibility(View.VISIBLE);
                    binding.layoutAccountSpinner.setVisibility(View.GONE);
                    updateSeparateAccountsVisibility();
                } else {
                    Log.w("ReportsBottomSheet",
                            "تعذّر إيجاد نوع الحساب name=" + pendingAccountTypeName
                                    + " id=" + pendingAccountTypeId);
                    if (!names.isEmpty()) {
                        binding.spinnerAccountType.setText(names.get(0), false);
                    }
                    binding.chipScopeAllAccounts.setChecked(true);
                    updateSeparateAccountsVisibility();
                }
            } else if (!names.isEmpty() && binding.spinnerAccountType.getText().toString().trim().isEmpty()) {
                binding.spinnerAccountType.setText(names.get(0), false);
            }
        });

        currencyViewModel.getAllCurrencies().observe(getViewLifecycleOwner(), currencies -> {
            if (currencies != null) {
                allAvailableCurrencies = currencies;
                List<String> names = currencies.stream().map(c -> c.name).collect(Collectors.toList());
                ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, names);
                binding.spinnerCurrency.setAdapter(adapter);

                if (binding.spinnerCurrency.getText().toString().isEmpty() && !names.isEmpty()) {
                    binding.spinnerCurrency.setText(names.get(0), false);
                }
            }
        });
    }

    private void showProgressDialog(String message) {
        if (progressDialog != null && progressDialog.isShowing()) {
            TextView tv = progressDialog.findViewById(R.id.tv_message);
            if (tv != null) tv.setText(message);
            return;
        }
        if (getActivity() != null) {
            requireActivity().runOnUiThread(() -> {
                try {
                    androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext());
                    View view = getLayoutInflater().inflate(R.layout.dialog_loading, null);
                    TextView tvMessage = view.findViewById(R.id.tv_message);
                    tvMessage.setText(message);

                    builder.setView(view);
                    builder.setCancelable(false);

                    progressDialog = builder.create();
                    progressDialog.show();
                } catch (Exception ignored) {
                }
            });
        }
    }

    private void hideProgressDialog() {
        if (getActivity() != null) {
            requireActivity().runOnUiThread(() -> {
                try {
                    if (progressDialog != null && progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                } catch (Exception ignored) {
                }
            });
        }
    }

    private void generateReport(boolean isForShare) {
        boolean isSingleAccount = binding.chipScopeSingleAccount.isChecked();
        boolean isAccountType = binding.chipScopeAccountType.isChecked();
        boolean isAllAccounts = binding.chipScopeAllAccounts.isChecked();

        Account selectedAccount = null;
        String accountTypeId = null;

        if (isSingleAccount) {
            String accName = binding.spinnerAccounts.getText().toString();
            selectedAccount = findAccountByName(accName);
            if (selectedAccount == null) {
                Toast.makeText(getContext(), R.string.error_account_name_empty, Toast.LENGTH_SHORT).show();
                return;
            }
        } else if (isAccountType) {
            String typeName = binding.spinnerAccountType.getText().toString();
            accountTypeId = findFirestoreIdByAccountTypeName(typeName);
            if (accountTypeId == null) {
                Toast.makeText(getContext(), "اختر نوع حساب صحيح", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        boolean specificCurrency = binding.switchCurrency.isChecked();
        int currencyId = -1;
        String currencyName = "";
        if (specificCurrency) {
            currencyName = binding.spinnerCurrency.getText().toString();
            currencyId = findCurrencyIdByName(currencyName);
            if (currencyId == -1) {
                Toast.makeText(getContext(), "اختر عملة صحيحة", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        boolean useDateRange = binding.switchDateRange.isChecked();
        long start = useDateRange ? startDate.getTimeInMillis() : 0;
        long end = useDateRange ? endDate.getTimeInMillis() : Long.MAX_VALUE;

        showProgressDialog(getString(R.string.ar_long_text_5));

        final Account finalAccount = selectedAccount;
        final String finalAccountTypeId = accountTypeId;
        final int finalCurrencyId = currencyId;
        final boolean finalSpecificCurrency = specificCurrency;
        final boolean separateAccounts = switchSeparateAccounts != null && switchSeparateAccounts.isChecked();

        new Thread(() -> {
            try {
                File outputFile = null;
                ReportGenerator generator = new ReportGenerator(
                        requireContext(),
                        currentUser,
                        languageManager.getCurrentLanguage()
                );

                if (isSingleAccount && finalAccount != null) {
                    if (selectedReportType == 1) {
                        if (finalSpecificCurrency)
                            outputFile = generator.generateDetailedMovementAccountStatement(
                                    finalAccount, finalCurrencyId, start, end, useDateRange);
                        else
                            outputFile = generator.generateDetailedMovementAccountStatementAllCurrencies(
                                    finalAccount, finalCurrencyId, start, end, useDateRange);
                        reportType = getString(R.string.radio_detailed_movement_all) + " " + finalAccount.getAccountName();
                    } else if (selectedReportType == 2) {
                        if (finalSpecificCurrency)
                            outputFile = generator.generateDetailedAccountStatement(
                                    finalAccount, finalCurrencyId, start, end, useDateRange);
                        else
                            outputFile = generator.generateDetailedAccountStatementAllCurrencies(
                                    finalAccount, finalCurrencyId, start, end, useDateRange);
                        reportType = getString(R.string.balance_summary) + " " + finalAccount.getAccountName();
                    } else if (selectedReportType == 3) {
                        if (finalSpecificCurrency)
                            outputFile = generator.generateMonthlyAccountSummary(finalAccount, finalCurrencyId);
                        else
                            outputFile = generator.generateMonthlyAccountSummaryAllCurrency(finalAccount, finalCurrencyId);
                        reportType = getString(R.string.monthly_account_moves) + " " + finalAccount.getAccountName();
                    }
                } else if (isAccountType && finalAccountTypeId != null) {
                    if (selectedReportType == 1) {
                        if (separateAccounts) {
                            if (finalSpecificCurrency)
                                outputFile = generator.generateSeparateAccountsMovementReportForAccountType(
                                        finalAccountTypeId, finalCurrencyId, start, end, useDateRange, true);
                            else
                                outputFile = generator.generateSeparateAccountsMovementReportForAccountType(
                                        finalAccountTypeId, -1, start, end, useDateRange, false);
                            reportType = getString(R.string.radio_detailed_movement_all) + " - " + getString(R.string.separate_accounts);
                        } else {
                            if (finalSpecificCurrency)
                                outputFile = generator.generateConsolidatedMovementReport(
                                        finalAccountTypeId, finalCurrencyId, start, end, useDateRange);
                            else
                                outputFile = generator.generateConsolidatedMovementReportAllCurrencies(
                                        finalAccountTypeId, start, end, useDateRange);
                            reportType = getString(R.string.radio_detailed_movement_all);
                        }
                    } else if (selectedReportType == 2) {
                        if (finalSpecificCurrency)
                            outputFile = generator.generateConsolidatedBalancesReport(
                                    finalAccountTypeId, finalCurrencyId, start, end, useDateRange);
                        else
                            outputFile = generator.generateConsolidatedBalancesReportAllCurrency(
                                    finalAccountTypeId, start, end);
                        reportType = getString(R.string.summary_title);
                    } else if (selectedReportType == 3) {
                        if (finalSpecificCurrency)
                            outputFile = generator.generateMonthlySummaryByAccountType(
                                    finalAccountTypeId, finalCurrencyId);
                        else
                            outputFile = generator.generateMonthlySummaryByAccountTypeAllCurrency(
                                    finalAccountTypeId);
                        reportType = getString(R.string.report_title_monthly_summary);
                    }
                } else if (isAllAccounts) {
                    if (selectedReportType == 1) {
                        if (finalSpecificCurrency)
                            outputFile = generator.generateConsolidatedMovementReportAllTypes(
                                    finalCurrencyId, start, end, useDateRange);
                        else
                            outputFile = generator.generateConsolidatedMovementReportAllCurrenciesAllTypes(
                                    start, end, useDateRange);
                        reportType = getString(R.string.radio_detailed_movement_all);
                    } else if (selectedReportType == 2) {
                        if (finalSpecificCurrency)
                            outputFile = generator.generateConsolidatedBalancesReportAllTypes(
                                    finalCurrencyId, start, end, useDateRange);
                        else
                            outputFile = generator.generateConsolidatedBalancesReportAllCurrencyAllTypes(
                                    start, end, useDateRange);
                        reportType = getString(R.string.summary_title);
                    } else if (selectedReportType == 3) {
                        if (finalSpecificCurrency)
                            outputFile = generator.generateMonthlySummaryByAccountAllTypes(
                                    null, finalCurrencyId);
                        else
                            outputFile = generator.generateMonthlySummaryByAccountAllCurrenciesAllTypes(
                                    null, finalCurrencyId);
                        reportType = getString(R.string.report_title_monthly_summary);
                    }
                }

                if (!isAdded() || getActivity() == null) {
                    if (progressDialog != null && progressDialog.isShowing()) hideProgressDialog();
                    return;
                }

                final File finalOutput = outputFile;
                requireActivity().runOnUiThread(() -> {
                    hideProgressDialog();
                    dismiss();

                    if (finalOutput != null && finalOutput.exists()) {
                        if (isForShare) {
                            sharePdfFile(finalOutput);
                        } else {
                            viewPdf(finalOutput);
                        }
                    } else {
                        DialogHelper.show(
                                getContext(),
                                getString(R.string.report_no_data_generated),
                                DialogHelper.FeedbackType.ERROR,
                                null
                        );
                    }
                });

            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    hideProgressDialog();
                    Toast.makeText(getContext(), "خطأ: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                });
            }
        }).start();
    }

    private void sharePdfFile(File pdfFile) {
        try {
            Uri fileUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".provider",
                    pdfFile
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.putExtra(Intent.EXTRA_TEXT, reportType);

            if (phoneNumber != null && !phoneNumber.isEmpty()) {
                shareIntent.putExtra("address", phoneNumber);
                shareIntent.putExtra(Intent.EXTRA_PHONE_NUMBER, phoneNumber);
            }

            startActivity(Intent.createChooser(shareIntent, getString(R.string._1266)));
        } catch (Exception e) {
            Toast.makeText(getContext(), "حدث خطأ أثناء المشاركة", Toast.LENGTH_SHORT).show();
        }
    }

    private String reportType = "";

    private Account findAccountByName(String name) {
        if (accountsList == null || name == null) return null;
        String cleanName = name.trim();
        return accountsList.stream()
                .filter(a -> a.getAccountName() != null && a.getAccountName().trim().equalsIgnoreCase(cleanName))
                .findFirst().orElse(null);
    }

    private String findFirestoreIdByAccountTypeName(String name) {
        if (accountTypesList == null || name == null) return null;
        String cleanName = name.trim();
        return accountTypesList.stream()
                .filter(t -> t.getName() != null && t.getName().trim().equalsIgnoreCase(cleanName))
                .findFirst().map(AccountType::getFirestoreId).orElse(null);
    }

    private int findCurrencyIdByName(String name) {
        if (allAvailableCurrencies == null || name == null) return -1;
        String cleanName = name.trim();
        return allAvailableCurrencies.stream()
                .filter(c -> c.name != null && c.name.trim().equalsIgnoreCase(cleanName))
                .findFirst().map(c -> c.id).orElse(-1);
    }

    private void showDatePicker(boolean isStartDate) {
        Calendar calendar = isStartDate ? startDate : endDate;
        new DatePickerDialog(requireContext(), (view, year, month, day) -> {
            calendar.set(year, month, day);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            if (isStartDate) binding.etStartDate.setText(sdf.format(calendar.getTime()));
            else binding.etEndDate.setText(sdf.format(calendar.getTime()));
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void loadDefaultDates() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        binding.etEndDate.setText(sdf.format(new Date()));
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        binding.etStartDate.setText(sdf.format(cal.getTime()));
        startDate.setTime(cal.getTime());
    }

    private void viewPdf(File pdfFile) {
        Uri fileUri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".provider", pdfFile);
        Intent viewIntent = new Intent(Intent.ACTION_VIEW);
        viewIntent.setDataAndType(fileUri, "application/pdf");
        viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(viewIntent);
        } catch (Exception e) {
            Toast.makeText(getContext(), getString(R.string.open), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        if (getDialog() != null) {
            View bottomSheet = getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                if (bottomSheet instanceof ViewGroup) {
                    ((ViewGroup) bottomSheet).setLayoutTransition(null);
                }
                bottomSheet.setBackgroundResource(android.R.color.transparent);
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setFitToContents(true);
                behavior.setSkipCollapsed(true);
                behavior.setHideable(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        }
    }
}
