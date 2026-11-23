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
import androidx.core.content.ContextCompat;
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
import com.hpp.daftree.budget.data.BudgetEntry;
import com.hpp.daftree.budget.report.BudgetReportGenerator;
import com.hpp.daftree.budget.util.BudgetUtils;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.User;
import com.hpp.daftree.databinding.BottomSheetReportsBinding;
import com.hpp.daftree.helpers.AppFontHelper;
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
    private static final String ARG_BUDGET_YEAR_MONTH = "budget_year_month";
    private String phoneNumber = "";

    public enum LaunchMode {
        GENERAL,        // من القائمة الجانبية
        SINGLE_ACCOUNT, // من تفاصيل الحساب
        FILTERED,       // من الفلتر في الواجهة الرئيسية
        BUDGET          // من شاشة الميزانية
    }

    private LanguageManager languageManager;
    private LaunchMode currentMode = LaunchMode.GENERAL;
    private boolean isBudgetTabSelected = false;
    private int selectedReportType = 1; // 1: Detailed, 2: Balances/Categories, 3: Monthly
    private String budgetFilterType = "ALL"; // ALL, EXPENSE, INCOME
    private String pendingAccountTypeName = null;
    private String pendingAccountTypeId = null;
    private String reportType = "";

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

    public static ReportsBottomSheet newInstanceForBudget(String yearMonth, String currencyName) {
        ReportsBottomSheet fragment = new ReportsBottomSheet();
        Bundle args = new Bundle();
        args.putSerializable(ARG_LAUNCH_MODE, LaunchMode.BUDGET);
        args.putString(ARG_BUDGET_YEAR_MONTH, yearMonth);
        args.putString(ARG_CURRENCY_NAME, currencyName);
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

        disableLayoutTransitions(binding.getRoot());
        AppFontHelper.applyFontToViewTree(requireContext(), binding.getRoot());

        viewModel = new ViewModelProvider(this).get(ReportsViewModel.class);
        currencyViewModel = new ViewModelProvider(this).get(CurrencyViewModel.class);
        languageManager = LanguageManager.getInstance(getContext());
        String lang = languageManager.getCurrentLanguage();
        Locale locale = Locale.forLanguageTag(lang);
        Locale.setDefault(locale);
        MyApplication.applyGlobalTextWatcher(binding.getRoot());

        Configuration config = new Configuration(requireContext().getResources().getConfiguration());
        config.setLocale(locale);
        config.setLayoutDirection(locale);
        requireContext().createConfigurationContext(config);

        if (getArguments() != null) {
            LaunchMode mode = (LaunchMode) getArguments().getSerializable(ARG_LAUNCH_MODE);
            if (mode != null) currentMode = mode;
        }

        setupBottomBarInsets();
        loadDefaultDates();
        setupUI();
        setupObservers();
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

        // 1. تبويبا القسمين: تقارير الحسابات / تقارير الميزانية
        binding.tabReportAccounts.setOnClickListener(v -> switchReportCategoryTab(false));
        binding.tabReportBudget.setOnClickListener(v -> switchReportCategoryTab(true));

        // 2. فلتر عمليات الميزانية (الكل / مصروفات / دخل)
        binding.btnReportBudgetFilterAll.setOnClickListener(v -> setBudgetFilter("ALL"));
        binding.btnReportBudgetFilterExpense.setOnClickListener(v -> setBudgetFilter(BudgetEntry.TYPE_EXPENSE));
        binding.btnReportBudgetFilterIncome.setOnClickListener(v -> setBudgetFilter(BudgetEntry.TYPE_INCOME));

        // 3. نطاق تقارير الحسابات
        binding.chipGroupScope.setOnCheckedChangeListener((group, checkedId) -> updateScopeVisibility(checkedId));

        // 4. بطاقات أنواع التقارير الثلاثة
        View.OnClickListener cardClickListener = v -> updateReportTypeSelection((MaterialCardView) v);
        binding.cardTypeStatement.setOnClickListener(cardClickListener);
        binding.cardTypeBalance.setOnClickListener(cardClickListener);
        binding.cardTypeMonthly.setOnClickListener(cardClickListener);
        updateReportTypeSelection(binding.cardTypeStatement);

        // 5. مفاتيح الفلاتر
        binding.switchDateRange.setOnCheckedChangeListener((buttonView, isChecked) ->
                binding.containerDateRange.setVisibility(isChecked ? View.VISIBLE : View.GONE));

        binding.switchCurrency.setOnCheckedChangeListener((buttonView, isChecked) ->
                binding.containerCurrency.setVisibility(isChecked ? View.VISIBLE : View.GONE));

        binding.etStartDate.setOnClickListener(v -> showDatePicker(true));
        binding.etEndDate.setOnClickListener(v -> showDatePicker(false));

        if (binding.btnSharePng != null) {
            binding.btnSharePng.setVisibility(View.GONE);
        }
        binding.btnGenerateReport.setVisibility(View.VISIBLE);
        binding.btnShareReport.setVisibility(View.VISIBLE);

        binding.btnGenerateReport.setOnClickListener(v -> generateReport(false));
        binding.btnShareReport.setOnClickListener(v -> generateReport(true));

        applyLaunchModeLogic();
    }

    private void switchReportCategoryTab(boolean showBudget) {
        isBudgetTabSelected = showBudget;
        int activeText = ContextCompat.getColor(requireContext(), R.color.budget_text_on_card);
        int inactiveText = ContextCompat.getColor(requireContext(), R.color.budget_text_secondary);

        if (showBudget) {
            binding.tabReportAccounts.setBackground(null);
            binding.tvTabReportAccounts.setTextColor(inactiveText);
            binding.tabReportBudget.setBackgroundResource(R.drawable.budget_bg_tab_active);
            binding.tvTabReportBudget.setTextColor(activeText);

            binding.containerAccountScopeSection.setVisibility(View.GONE);
            binding.llSeparateAccounts.setVisibility(View.GONE);

            binding.ivCardStatement.setImageResource(R.drawable.ic_modern_budget_list);
            binding.tvCardStatement.setText(R.string.budget_report_type_detailed);
            binding.ivCardBalance.setImageResource(R.drawable.ic_modern_budget_pie);
            binding.tvCardBalance.setText(R.string.budget_report_type_categories);
            binding.ivCardMonthly.setImageResource(R.drawable.ic_modern_budget_bar);
            binding.tvCardMonthly.setText(R.string.budget_report_type_monthly);
        } else {
            binding.tabReportAccounts.setBackgroundResource(R.drawable.budget_bg_tab_active);
            binding.tvTabReportAccounts.setTextColor(activeText);
            binding.tabReportBudget.setBackground(null);
            binding.tvTabReportBudget.setTextColor(inactiveText);

            binding.containerAccountScopeSection.setVisibility(View.VISIBLE);

            binding.ivCardStatement.setImageResource(R.drawable.ic_modern_report_statement);
            binding.tvCardStatement.setText(R.string.radio_detailed_movement_all);
            binding.ivCardBalance.setImageResource(R.drawable.ic_modern_report_balance);
            binding.tvCardBalance.setText(R.string.radio_total_balance_all);
            binding.ivCardMonthly.setImageResource(R.drawable.ic_modern_report_monthly);
            binding.tvCardMonthly.setText(R.string.radio_monthly_summary_all);
        }
        updateBudgetFilterVisibility();
        updateSeparateAccountsVisibility();
    }

    private void setBudgetFilter(String filter) {
        budgetFilterType = filter;
        styleBudgetFilterPill(binding.btnReportBudgetFilterAll, "ALL".equals(filter));
        styleBudgetFilterPill(binding.btnReportBudgetFilterExpense, BudgetEntry.TYPE_EXPENSE.equals(filter));
        styleBudgetFilterPill(binding.btnReportBudgetFilterIncome, BudgetEntry.TYPE_INCOME.equals(filter));
    }

    private void styleBudgetFilterPill(TextView pill, boolean selected) {
        pill.setBackgroundResource(selected ? R.drawable.budget_bg_tab_active : R.drawable.budget_bg_currency_chip);
        pill.setTextColor(ContextCompat.getColor(
                requireContext(),
                selected ? R.color.budget_chip_selected_text : R.color.budget_chip_unselected_text));
    }

    private void updateBudgetFilterVisibility() {
        if (binding == null) return;
        binding.layoutBudgetFilterPills.setVisibility(
                (isBudgetTabSelected && selectedReportType == 1) ? View.VISIBLE : View.GONE);
    }

    private void updateSeparateAccountsVisibility() {
        if (llSeparateAccounts == null || switchSeparateAccounts == null) return;
        boolean isAccountTypeScope = !isBudgetTabSelected && binding.chipScopeAccountType.isChecked();
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
                binding.layoutReportCategoryTabs.setVisibility(View.GONE);
                binding.tvReportSheetTitle.setVisibility(View.VISIBLE);
                switchReportCategoryTab(false);

                binding.chipScopeSingleAccount.setChecked(true);
                binding.chipScopeAccountType.setVisibility(View.GONE);
                binding.chipScopeAllAccounts.setVisibility(View.GONE);

                String accName = getArguments().getString(ARG_ACCOUNT_NAME);
                phoneNumber = getArguments().getString(ARG_PHONE_NUMBER);
                binding.spinnerAccounts.setText(accName, false);
                binding.spinnerAccounts.setEnabled(false);

                setupCurrencyIfExists(getArguments().getString(ARG_CURRENCY_NAME), false);
                break;
            }
            case BUDGET: {
                binding.layoutReportCategoryTabs.setVisibility(View.VISIBLE);
                binding.tvReportSheetTitle.setVisibility(View.GONE);
                switchReportCategoryTab(true);
                setBudgetFilter("ALL");

                String ym = getArguments().getString(ARG_BUDGET_YEAR_MONTH);
                applyBudgetMonthDateRange(ym);
                setupCurrencyIfExists(getArguments().getString(ARG_CURRENCY_NAME), true);
                break;
            }
            case FILTERED: {
                binding.layoutReportCategoryTabs.setVisibility(View.VISIBLE);
                binding.tvReportSheetTitle.setVisibility(View.GONE);
                switchReportCategoryTab(false);

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
                setupCurrencyIfExists(getArguments().getString(ARG_CURRENCY_NAME), false);
                break;
            }
            case GENERAL:
            default:
                binding.layoutReportCategoryTabs.setVisibility(View.VISIBLE);
                binding.tvReportSheetTitle.setVisibility(View.GONE);
                switchReportCategoryTab(false);
                binding.chipScopeAllAccounts.setChecked(true);
                binding.layoutAccountTypeSpinner.setVisibility(View.GONE);
                binding.layoutAccountSpinner.setVisibility(View.GONE);
                setupCurrencyIfExists(getArguments().getString(ARG_CURRENCY_NAME), false);
                break;
        }
        updateSeparateAccountsVisibility();
    }

    private void applyBudgetMonthDateRange(String yearMonth) {
        Calendar cal = BudgetUtils.parseYearMonth(yearMonth != null ? yearMonth : BudgetUtils.getCurrentYearMonth());
        startDate.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), 1, 0, 0, 0);
        startDate.set(Calendar.MILLISECOND, 0);

        int maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        endDate.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), maxDay, 23, 59, 59);
        endDate.set(Calendar.MILLISECOND, 999);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        binding.etStartDate.setText(sdf.format(startDate.getTime()));
        binding.etEndDate.setText(sdf.format(endDate.getTime()));
        binding.switchDateRange.setChecked(true);
        binding.containerDateRange.setVisibility(View.VISIBLE);
    }

    private void setupCurrencyIfExists(String currencyName, boolean enableSwitch) {
        if (currencyName != null && !currencyName.isEmpty()) {
            binding.spinnerCurrency.setText(currencyName, false);
            binding.switchCurrency.setChecked(enableSwitch);
            binding.containerCurrency.setVisibility(enableSwitch ? View.VISIBLE : View.GONE);
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
        updateBudgetFilterVisibility();
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
            List<String> names = types.stream().map(AccountType::getName).collect(Collectors.toList());
            ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, names);
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
                    if (!names.isEmpty()) binding.spinnerAccountType.setText(names.get(0), false);
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
                    AppFontHelper.applyFontToDialog(requireContext(), progressDialog);
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
        boolean specificCurrency = binding.switchCurrency.isChecked();
        int currencyId = -1;
        if (specificCurrency) {
            String currencyName = binding.spinnerCurrency.getText().toString();
            currencyId = findCurrencyIdByName(currencyName);
            if (currencyId == -1) {
                Toast.makeText(getContext(), R.string.wronge_currency, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        boolean useDateRange = binding.switchDateRange.isChecked();
        long start = useDateRange ? startDate.getTimeInMillis() : 0L;
        long end = useDateRange ? endDate.getTimeInMillis() : Long.MAX_VALUE;

        // ==================== فرع تقارير الميزانية ====================
        if (isBudgetTabSelected) {
            final int finalCurrencyId = currencyId;
            final boolean finalSpecificCurrency = specificCurrency;
            final String finalFilterType = budgetFilterType;
            showProgressDialog(getString(R.string.ar_long_text_5));

            new Thread(() -> {
                try {
                    BudgetReportGenerator budgetGenerator = new BudgetReportGenerator(
                            requireContext(),
                            currentUser,
                            languageManager.getCurrentLanguage()
                    );
                    File outputFile = null;
                    if (selectedReportType == 1) {
                        outputFile = budgetGenerator.generateDetailedBudgetReport(
                                finalCurrencyId, finalSpecificCurrency, start, end, useDateRange, finalFilterType);
                        reportType = getString(R.string.budget_report_title_detailed);
                    } else if (selectedReportType == 2) {
                        outputFile = budgetGenerator.generateCategoryAnalysisReport(
                                finalCurrencyId, finalSpecificCurrency, start, end, useDateRange);
                        reportType = getString(R.string.budget_report_title_categories);
                    } else if (selectedReportType == 3) {
                        outputFile = budgetGenerator.generateMonthlyBudgetSummaryReport(
                                finalCurrencyId, finalSpecificCurrency, start, end, useDateRange);
                        reportType = getString(R.string.budget_report_title_monthly);
                    }
                    deliverReportResult(outputFile, isForShare);
                } catch (Exception e) {
                    handleReportException(e);
                }
            }).start();
            return;
        }

        // ==================== فرع تقارير الحسابات والديون ====================
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
                Toast.makeText(getContext(), R.string.required, Toast.LENGTH_SHORT).show();
                return;
            }
        }

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
                        outputFile = finalSpecificCurrency
                                ? generator.generateDetailedMovementAccountStatement(finalAccount, finalCurrencyId, start, end, useDateRange)
                                : generator.generateDetailedMovementAccountStatementAllCurrencies(finalAccount, finalCurrencyId, start, end, useDateRange);
                        reportType = getString(R.string.radio_detailed_movement_all) + " " + finalAccount.getAccountName();
                    } else if (selectedReportType == 2) {
                        outputFile = finalSpecificCurrency
                                ? generator.generateDetailedAccountStatement(finalAccount, finalCurrencyId, start, end, useDateRange)
                                : generator.generateDetailedAccountStatementAllCurrencies(finalAccount, finalCurrencyId, start, end, useDateRange);
                        reportType = getString(R.string.balance_summary) + " " + finalAccount.getAccountName();
                    } else if (selectedReportType == 3) {
                        outputFile = finalSpecificCurrency
                                ? generator.generateMonthlyAccountSummary(finalAccount, finalCurrencyId)
                                : generator.generateMonthlyAccountSummaryAllCurrency(finalAccount, finalCurrencyId);
                        reportType = getString(R.string.monthly_account_moves) + " " + finalAccount.getAccountName();
                    }
                } else if (isAccountType && finalAccountTypeId != null) {
                    if (selectedReportType == 1) {
                        if (separateAccounts) {
                            outputFile = finalSpecificCurrency
                                    ? generator.generateSeparateAccountsMovementReportForAccountType(finalAccountTypeId, finalCurrencyId, start, end, useDateRange, true)
                                    : generator.generateSeparateAccountsMovementReportForAccountType(finalAccountTypeId, -1, start, end, useDateRange, false);
                            reportType = getString(R.string.radio_detailed_movement_all) + " - " + getString(R.string.separate_accounts);
                        } else {
                            outputFile = finalSpecificCurrency
                                    ? generator.generateConsolidatedMovementReport(finalAccountTypeId, finalCurrencyId, start, end, useDateRange)
                                    : generator.generateConsolidatedMovementReportAllCurrencies(finalAccountTypeId, start, end, useDateRange);
                            reportType = getString(R.string.radio_detailed_movement_all);
                        }
                    } else if (selectedReportType == 2) {
                        outputFile = finalSpecificCurrency
                                ? generator.generateConsolidatedBalancesReport(finalAccountTypeId, finalCurrencyId, start, end, useDateRange)
                                : generator.generateConsolidatedBalancesReportAllCurrency(finalAccountTypeId, start, end);
                        reportType = getString(R.string.summary_title);
                    } else if (selectedReportType == 3) {
                        outputFile = finalSpecificCurrency
                                ? generator.generateMonthlySummaryByAccountType(finalAccountTypeId, finalCurrencyId)
                                : generator.generateMonthlySummaryByAccountTypeAllCurrency(finalAccountTypeId);
                        reportType = getString(R.string.report_title_monthly_summary);
                    }
                } else if (isAllAccounts) {
                    if (selectedReportType == 1) {
                        outputFile = finalSpecificCurrency
                                ? generator.generateConsolidatedMovementReportAllTypes(finalCurrencyId, start, end, useDateRange)
                                : generator.generateConsolidatedMovementReportAllCurrenciesAllTypes(start, end, useDateRange);
                        reportType = getString(R.string.radio_detailed_movement_all);
                    } else if (selectedReportType == 2) {
                        outputFile = finalSpecificCurrency
                                ? generator.generateConsolidatedBalancesReportAllTypes(finalCurrencyId, start, end, useDateRange)
                                : generator.generateConsolidatedBalancesReportAllCurrencyAllTypes(start, end, useDateRange);
                        reportType = getString(R.string.summary_title);
                    } else if (selectedReportType == 3) {
                        outputFile = finalSpecificCurrency
                                ? generator.generateMonthlySummaryByAccountAllTypes(null, finalCurrencyId)
                                : generator.generateMonthlySummaryByAccountAllCurrenciesAllTypes(null, finalCurrencyId);
                        reportType = getString(R.string.report_title_monthly_summary);
                    }
                }

                deliverReportResult(outputFile, isForShare);
            } catch (Exception e) {
                handleReportException(e);
            }
        }).start();
    }

    private void deliverReportResult(File outputFile, boolean isForShare) {
        if (!isAdded() || getActivity() == null) {
            hideProgressDialog();
            return;
        }
        requireActivity().runOnUiThread(() -> {
            hideProgressDialog();
            dismiss();
            if (outputFile != null && outputFile.exists()) {
                if (isForShare) {
                    sharePdfFile(outputFile);
                } else {
                    viewPdf(outputFile);
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
    }

    private void handleReportException(Exception e) {
        Log.e("ReportsBottomSheet", "Report generation failed", e);
        if (!isAdded() || getActivity() == null) return;
        requireActivity().runOnUiThread(() -> {
            hideProgressDialog();
            Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_LONG).show();
        });
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

            if (!isBudgetTabSelected && phoneNumber != null && !phoneNumber.isEmpty()) {
                shareIntent.putExtra("address", phoneNumber);
                shareIntent.putExtra("android.intent.extra.PHONE_NUMBER", phoneNumber);
            }

            startActivity(Intent.createChooser(shareIntent, getString(R.string._1266)));
        } catch (Exception e) {
            Toast.makeText(getContext(), R.string.error, Toast.LENGTH_SHORT).show();
        }
    }

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
            if (isStartDate) {
                calendar.set(year, month, day, 0, 0, 0);
                calendar.set(Calendar.MILLISECOND, 0);
            } else {
                calendar.set(year, month, day, 23, 59, 59);
                calendar.set(Calendar.MILLISECOND, 999);
            }
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            if (isStartDate) binding.etStartDate.setText(sdf.format(calendar.getTime()));
            else binding.etEndDate.setText(sdf.format(calendar.getTime()));
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void loadDefaultDates() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar nowEnd = Calendar.getInstance();
        nowEnd.set(Calendar.HOUR_OF_DAY, 23);
        nowEnd.set(Calendar.MINUTE, 59);
        nowEnd.set(Calendar.SECOND, 59);
        nowEnd.set(Calendar.MILLISECOND, 999);
        endDate.setTimeInMillis(nowEnd.getTimeInMillis());
        binding.etEndDate.setText(sdf.format(endDate.getTime()));

        Calendar monthStart = Calendar.getInstance();
        monthStart.set(Calendar.DAY_OF_MONTH, 1);
        monthStart.set(Calendar.HOUR_OF_DAY, 0);
        monthStart.set(Calendar.MINUTE, 0);
        monthStart.set(Calendar.SECOND, 0);
        monthStart.set(Calendar.MILLISECOND, 0);
        startDate.setTimeInMillis(monthStart.getTimeInMillis());
        binding.etStartDate.setText(sdf.format(startDate.getTime()));
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
