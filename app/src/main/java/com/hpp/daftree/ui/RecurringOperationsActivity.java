package com.hpp.daftree.ui;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.adapters.RecurringOperationsAdapter;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.RecurringOperation;
import com.hpp.daftree.databinding.ActivityRecurringOperationsBinding;
import com.hpp.daftree.databinding.DialogAddRecurringOperationBinding;
import com.hpp.daftree.dialogs.AccountBottomSheet;
import com.hpp.daftree.models.FormatingAmount;
import com.hpp.daftree.viewmodels.AddTransactionViewModel;
import com.hpp.daftree.viewmodels.CurrencyViewModel;
import com.hpp.daftree.viewmodels.RecurringOperationsViewModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class RecurringOperationsActivity extends BaseActivity {

    private static final String TAG = "RecurringOpsActivity";
    private ActivityRecurringOperationsBinding binding;
    private RecurringOperationsViewModel viewModel;
    private RecurringOperationsAdapter adapter;
    private AddTransactionViewModel addTransactionViewModel;
    private CurrencyViewModel currencyViewModel;
    private final List<Currency> availableCurrencies = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRecurringOperationsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupFloatingToolbar(binding.recyclerView);

        viewModel = new ViewModelProvider(this).get(RecurringOperationsViewModel.class);
        addTransactionViewModel = new ViewModelProvider(this).get(AddTransactionViewModel.class);
        currencyViewModel = new ViewModelProvider(this).get(CurrencyViewModel.class);

        setupRecyclerView();
        setupFab();

        currencyViewModel.getAllCurrencies().observe(this, currencies -> {
            if (currencies != null) {
                availableCurrencies.clear();
                availableCurrencies.addAll(currencies);
            }
        });

        viewModel.getRecurringOperations().observe(this, operations -> {
            if (adapter != null) adapter.submitList(operations);
        });

        binding.btnBack.setOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        adapter = new RecurringOperationsAdapter(new RecurringOperationsAdapter.OnRecurringOperationListener() {
            @Override
            public void onEdit(RecurringOperation operation) {
                showAddEditDialog(operation);
            }

            @Override
            public void onDelete(RecurringOperation operation) {
                new MaterialAlertDialogBuilder(RecurringOperationsActivity.this)
                        .setTitle(R.string.delete)
                        .setMessage(R.string.confirm_delete_recurring)
                        .setPositiveButton(R.string.delete, (dialog, which) -> viewModel.delete(operation))
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            }
        });
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);
    }

    private void setupFab() {
        binding.fabAdd.setOnClickListener(v -> showAddEditDialog(null));
    }

    private void showAddEditDialog(RecurringOperation operationToEdit) {
        DialogAddRecurringOperationBinding dialogBinding = DialogAddRecurringOperationBinding.inflate(getLayoutInflater());
        boolean isEdit = operationToEdit != null;

        if (isEdit) {
            dialogBinding.dialogTitle.setText(R.string.edit_recurring);
            dialogBinding.btnAdd.setText(R.string.update);
        }
        FormatingAmount.applyTo(dialogBinding.amountEditText);
        MyApplication.applyGlobalTextWatcher(dialogBinding.getRoot());

        ArrayAdapter<String> accountAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        dialogBinding.accountAutoComplete.setAdapter(accountAdapter);
        dialogBinding.accountAutoComplete.setThreshold(1);
        addTransactionViewModel.getAllAccounts().observe(this, accounts -> {
            if (accounts == null) return;
            List<String> accountNames = accounts.stream().map(Account::getAccountName).collect(Collectors.toList());
            accountAdapter.clear();
            accountAdapter.addAll(accountNames);
            accountAdapter.notifyDataSetChanged();
            if (isEdit && dialogBinding.accountAutoComplete.getText().toString().isEmpty()) {
                dialogBinding.accountAutoComplete.setText(operationToEdit.getAccountName(), false);
            }
        });

        ArrayAdapter<String> currencyAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        currencyViewModel.getAllCurrencies().observe(this, currencies -> {
            if (currencies == null) return;
            List<String> currencyNames = currencies.stream().map(c -> c.name).collect(Collectors.toList());
            Log.d(TAG, "Currencies loaded: " + currencyNames.size() + " items");
            currencyAdapter.clear();
            currencyAdapter.addAll(currencyNames);
            currencyAdapter.notifyDataSetChanged();

            if (isEdit) {
                String currencyName = findCurrencyNameById(operationToEdit.getCurrencyId());
                if (currencyName != null) {
                    dialogBinding.currencySpinnerAutocomplete.setText(currencyName, false);
                }
            } else if (!currencyNames.isEmpty() && dialogBinding.currencySpinnerAutocomplete.getText().toString().isEmpty()) {
                dialogBinding.currencySpinnerAutocomplete.setText(currencyNames.get(0), false);
            }
        });
        dialogBinding.currencySpinnerAutocomplete.setAdapter(currencyAdapter);
        dialogBinding.currencySpinnerAutocomplete.setThreshold(1);
        int itemHeight = (int) (48 * getResources().getDisplayMetrics().density);
        dialogBinding.currencySpinnerAutocomplete.setDropDownHeight(itemHeight * 5);
        dialogBinding.currencySpinnerAutocomplete.setOnClickListener(v -> dialogBinding.currencySpinnerAutocomplete.showDropDown());

        if (isEdit) {
            dialogBinding.amountEditText.setText(FormatingAmount.formatForDisplay(operationToEdit.getAmount()));
            dialogBinding.detailsEditText.setText(operationToEdit.getDetails());
            switch (operationToEdit.getRecurrenceType()) {
                case "day":
                    dialogBinding.radioDay.setChecked(true);
                    break;
                case "week":
                    dialogBinding.radioWeek.setChecked(true);
                    break;
                case "month":
                    dialogBinding.radioMonth.setChecked(true);
                    break;
            }
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            dialogBinding.startDateEditText.setText(sdf.format(new Date(operationToEdit.getStartDate())));
            dialogBinding.endDateEditText.setText(sdf.format(new Date(operationToEdit.getEndDate())));
        } else {
            Calendar cal = Calendar.getInstance();
            dialogBinding.startDateEditText.setText(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(cal.getTime()));
            cal.add(Calendar.YEAR, 1);
            dialogBinding.endDateEditText.setText(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(cal.getTime()));
            dialogBinding.radioDay.setChecked(true);
        }

        dialogBinding.startDateLayout.setEndIconOnClickListener(v -> showDatePicker(dialogBinding.startDateEditText));
        dialogBinding.endDateLayout.setEndIconOnClickListener(v -> showDatePicker(dialogBinding.endDateEditText));

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogBinding.getRoot())
                .create();

        dialogBinding.btnAdd.setOnClickListener(v -> {
            String accountName = dialogBinding.accountAutoComplete.getText().toString().trim();
            if (accountName.isEmpty()) {
                dialogBinding.accountLayout.setError(getString(R.string.required));
                return;
            }
            dialogBinding.accountLayout.setError(null);

            Integer accountId = null;
            List<Account> accounts = addTransactionViewModel.getAllAccounts().getValue();
            if (accounts != null) {
                for (Account acc : accounts) {
                    if (accountName.equals(acc.getAccountName())) {
                        accountId = acc.getId();
                        break;
                    }
                }
            }
            if (accountId == null) {
                AccountBottomSheet sheet = AccountBottomSheet.newInstanceForCreate(accountName);
                sheet.setAccountActionListener(new AccountBottomSheet.AccountActionListener() {
                    @Override
                    public void onAccountSaved(Account account, boolean isNew) {
                        if (account != null) {
                            dialogBinding.accountAutoComplete.setText(account.getAccountName(), false);
                            Toast.makeText(RecurringOperationsActivity.this, R.string.account_added, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                sheet.show(getSupportFragmentManager(), "AccountBottomSheetFromRecurring");
                return;
            }

            String amountStr = dialogBinding.amountEditText.getText().toString().trim().replaceAll("[,]", "");
            if (amountStr.isEmpty()) {
                dialogBinding.amountLayout.setError(getString(R.string.required));
                return;
            }
            double amount;
            try {
                amount = Double.parseDouble(amountStr);
            } catch (NumberFormatException e) {
                dialogBinding.amountLayout.setError(getString(R.string.invalid_number));
                return;
            }

            String details = dialogBinding.detailsEditText.getText().toString().trim();
            if (details.isEmpty()) {
                dialogBinding.detailsLayout.setError(getString(R.string.required));
                return;
            }

            String currencyName = dialogBinding.currencySpinnerAutocomplete.getText().toString();
            Integer currencyId = null;
            for (Currency c : availableCurrencies) {
                if (c.name.equals(currencyName)) {
                    currencyId = c.id;
                    break;
                }
            }
            if (currencyId == null) {
                Toast.makeText(this, R.string.wronge_currency, Toast.LENGTH_SHORT).show();
                return;
            }

            String recurrenceType;
            int recurrenceDay;
            if (dialogBinding.radioDay.isChecked()) {
                recurrenceType = "day";
                recurrenceDay = 0;
            } else if (dialogBinding.radioWeek.isChecked()) {
                recurrenceType = "week";
                recurrenceDay = 1;
            } else {
                recurrenceType = "month";
                recurrenceDay = 1;
            }

            long startDate = parseDate(dialogBinding.startDateEditText.getText().toString());
            long endDate = parseDate(dialogBinding.endDateEditText.getText().toString());

            RecurringOperation operation = isEdit ? operationToEdit : new RecurringOperation();
            operation.setAccountId(accountId);
            operation.setAccountName(accountName);
            operation.setAmount(amount);
            operation.setType(1);
            operation.setDetails(details);
            operation.setCurrencyId(currencyId);
            operation.setRecurrenceType(recurrenceType);
            operation.setRecurrenceDay(recurrenceDay);
            operation.setStartDate(startDate);
            operation.setEndDate(endDate);
            operation.setActive(true);

            if (isEdit) {
                viewModel.update(operation);
                Toast.makeText(this, R.string.recurring_updated, Toast.LENGTH_SHORT).show();
            } else {
                viewModel.add(operation);
                Toast.makeText(this, R.string.recurring_added, Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        });

        dialogBinding.btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showDatePicker(TextInputEditText editText) {
        Calendar calendar = Calendar.getInstance();
        String dateStr = editText.getText().toString();
        if (!dateStr.isEmpty()) {
            try {
                Date date = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(dateStr);
                if (date != null) calendar.setTime(date);
            } catch (Exception ignored) {}
        }
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            calendar.set(year, month, dayOfMonth);
            editText.setText(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(calendar.getTime()));
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private long parseDate(String dateStr) {
        try {
            Date date = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(dateStr);
            return date != null ? date.getTime() : System.currentTimeMillis();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private String findCurrencyNameById(int currencyId) {
        for (Currency c : availableCurrencies) {
            if (c.id == currencyId) return c.name;
        }
        return null;
    }
}
