package com.hpp.daftree.ui;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.hpp.daftree.viewmodels.AccountTypeViewModel;
import com.hpp.daftree.models.AppAuth;
import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.adapters.RecurringOperationsAdapter;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.RecurringOperation;
import com.hpp.daftree.databinding.ActivityRecurringOperationsBinding;
import com.hpp.daftree.databinding.DialogAddAccountBinding;
import com.hpp.daftree.databinding.DialogAddRecurringOperationBinding;
import com.hpp.daftree.models.FormatingAmount;
import com.hpp.daftree.utils.SecureLicenseManager;
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
    private List<Currency> availableCurrencies = new ArrayList<>();
    private ActivityResultLauncher<PickVisualMediaRequest> photoPickerLauncher;
    private DialogAddAccountBinding activeAddAccountDialogBinding;

    // جهات الاتصال
    private final ActivityResultLauncher<String> requestContactPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) openContactPicker();
                else Toast.makeText(this, getString(R.string.error_contact_permission), Toast.LENGTH_SHORT).show();
            });

    private final ActivityResultLauncher<Intent> pickContactLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri contactUri = result.getData().getData();
                    processSelectedContact(contactUri);
                }
            });

    private void openContactPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        pickContactLauncher.launch(intent);
    }

    private void processSelectedContact(Uri dataUri) {
        if (activeAddAccountDialogBinding == null) return;
        String[] projection = new String[]{
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };
        try (Cursor cursor = getContentResolver().query(dataUri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                String number = cursor.getString(numberIndex);
                int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                String name = cursor.getString(nameIndex);
                if (name != null) activeAddAccountDialogBinding.editAccountName.setText(name);
                if (number != null) {
                    String cleanNumber = number.replaceAll("[\\s\\-()]", "");
                    activeAddAccountDialogBinding.editAccountPhone.setText(cleanNumber);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error picking contact phone", e);
            Toast.makeText(this, "حدث خطأ أثناء جلب الرقم", Toast.LENGTH_SHORT).show();
        }
    }

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

        // ----- إعداد حقل الحساب (AutoComplete) -----
        ArrayAdapter<String> accountAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        dialogBinding.accountAutoComplete.setAdapter(accountAdapter);
        dialogBinding.accountAutoComplete.setThreshold(1);
        addTransactionViewModel.getAllAccounts().observe(this, accounts -> {
            List<String> accountNames = accounts.stream().map(Account::getAccountName).collect(Collectors.toList());
            accountAdapter.clear();
            accountAdapter.addAll(accountNames);
            accountAdapter.notifyDataSetChanged();
            if (isEdit && operationToEdit != null) {
                dialogBinding.accountAutoComplete.setText(operationToEdit.getAccountName());
            }
        });

        // ----- إعداد حقل العملة (Exposed Dropdown) -----
        ArrayAdapter<String> currencyAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        currencyViewModel.getAllCurrencies().observe(this, currencies -> {
            if (currencies == null) return;
            List<String> currencyNames = currencies.stream().map(c -> c.name).collect(Collectors.toList());
            Log.d(TAG, "Currencies loaded: " + currencyNames.size() + " items");
            currencyAdapter.clear();
            currencyAdapter.addAll(currencyNames);
            currencyAdapter.notifyDataSetChanged();

            // تعيين النص المناسب
            if (isEdit && operationToEdit != null) {
                String currencyName = findCurrencyNameById(operationToEdit.getCurrencyId());
                if (currencyName != null) {
                    dialogBinding.currencySpinnerAutocomplete.setText(currencyName);
                }
            } else if (!currencyNames.isEmpty()) {
                dialogBinding.currencySpinnerAutocomplete.setText(currencyNames.get(0));
            }
        });
       dialogBinding.currencySpinnerAutocomplete.setAdapter(currencyAdapter);
        dialogBinding.currencySpinnerAutocomplete.setThreshold(1);
        // 🔥 ضبط ارتفاع القائمة المنسدلة لعرض عدة عناصر
        int itemHeight = (int) (48 * getResources().getDisplayMetrics().density);
        dialogBinding.currencySpinnerAutocomplete.setDropDownHeight(itemHeight * 5);
        // 🔥 جعل الـ AutoCompleteTextView يظهر القائمة عند النقر مباشرة
        dialogBinding.currencySpinnerAutocomplete.setOnClickListener(v -> dialogBinding.currencySpinnerAutocomplete.showDropDown());


//        currencyViewModel.getAllCurrencies().observe(this, currencies -> {
//            if (currencies != null) {
//                allAvailableCurrencies.clear();
//                allAvailableCurrencies.addAll(currencies);
//            }
//        });
//        // إعداد العملات
//        if (!allAvailableCurrencies.isEmpty()) {
//            List<String> currencyNames = allAvailableCurrencies.stream().map(c -> c.name).collect(Collectors.toList());
//            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
//                    android.R.layout.simple_dropdown_item_1line, currencyNames);
//            activeDialogBinding.currencySpinnerAutocomplete.setAdapter(adapter);
//            int itemHeight = (int) (48 * getResources().getDisplayMetrics().density);
//            activeDialogBinding.currencySpinnerAutocomplete.setDropDownHeight(itemHeight * 4);
//        }
        // ----- تعبئة البيانات في حالة التعديل -----
        if (isEdit && operationToEdit != null) {
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
            // التواريخ الافتراضية
            Calendar cal = Calendar.getInstance();
            dialogBinding.startDateEditText.setText(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(cal.getTime()));
            cal.add(Calendar.YEAR, 1);
            dialogBinding.endDateEditText.setText(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(cal.getTime()));
            dialogBinding.radioDay.setChecked(true);
        }

        // اختيار التاريخ
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
            Integer accountId = null;
            List<Account> accounts = addTransactionViewModel.getAllAccounts().getValue();
            if (accounts != null) {
                for (Account acc : accounts) {
                    if (acc.getAccountName().equals(accountName)) {
                        accountId = acc.getId();
                        break;
                    }
                }
            }
            if (accountId == null) {
                showAddNewAccountDialog(accountName, () -> {
                    // بعد إضافة الحساب، نعيد فتح الديالوج (يمكن تحسينها)
//                    showAddEditDialog(operationToEdit);
                });
                dialog.dismiss();
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
            int recurrenceDay = 0;
            if (dialogBinding.radioDay.isChecked()) {
                recurrenceType = "day";
                recurrenceDay = 0;
            } else if (dialogBinding.radioWeek.isChecked()) {
                recurrenceType = "week";
                // يمكن إضافة Spinner لاختيار اليوم، نستخدم 1 افتراضياً
                recurrenceDay = 1;
            } else {
                recurrenceType = "month";
                recurrenceDay = 1;
            }

            long startDate = parseDate(dialogBinding.startDateEditText.getText().toString());
            long endDate = parseDate(dialogBinding.endDateEditText.getText().toString());

            RecurringOperation operation;
            if (isEdit && operationToEdit != null) {
                operation = operationToEdit;
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
                viewModel.update(operation);
                Toast.makeText(this, R.string.recurring_updated, Toast.LENGTH_SHORT).show();
            } else {
                operation = new RecurringOperation();
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
                calendar.setTime(date);
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

    private void showAddNewAccountDialog(String accountName, Runnable onSuccess) {
        activeAddAccountDialogBinding = DialogAddAccountBinding.inflate(getLayoutInflater());
        activeAddAccountDialogBinding.editAccountName.setText(accountName);
        MyApplication.applyGlobalTextWatcher(activeAddAccountDialogBinding.getRoot());

        activeAddAccountDialogBinding.dialogPhoneInputLayout.setEndIconOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                openContactPicker();
            } else {
                requestContactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS);
            }
        });

        AutoCompleteTextView typeSpinner = activeAddAccountDialogBinding.spinnerAccountType;
        AccountTypeViewModel accountTypeViewModel = new ViewModelProvider(this).get(AccountTypeViewModel.class);
        accountTypeViewModel.getAllAccountTypes().observe(this, types -> {
            if (types != null && !types.isEmpty()) {
                List<String> typeNames = types.stream().map(AccountType::getName).collect(Collectors.toList());
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, typeNames);
                typeSpinner.setAdapter(adapter);
                if (!typeNames.isEmpty()) {
                    typeSpinner.setText(typeNames.get(0), false);
                }
            }
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(activeAddAccountDialogBinding.getRoot())
                .create();

        activeAddAccountDialogBinding.btnAdd.setOnClickListener(v -> {
            String finalName = activeAddAccountDialogBinding.editAccountName.getText().toString().trim();
            String phone = activeAddAccountDialogBinding.editAccountPhone.getText().toString().trim();
            String type = typeSpinner.getText().toString();

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

            String uid = SecureLicenseManager.getInstance(this).isGuest() ?
                    SecureLicenseManager.getInstance(this).guestUID() : AppAuth.getUserID(this);
            String typeId = findFirestoreIdByAccountTypeName(type);

            Account newAccount = new Account();
            newAccount.setOwnerUID(uid);
            newAccount.setAccountName(finalName);
            newAccount.setPhoneNumber(phone);
            newAccount.setAccountType(type);
            if (typeId != null) newAccount.setAcTypeFirestoreId(typeId);

            addTransactionViewModel.createAccount(newAccount);
          Toast.makeText(this, R.string.account_added, Toast.LENGTH_SHORT).show();
            onSuccess.run();
            dialog.dismiss();
        });

        activeAddAccountDialogBinding.btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private boolean isAccountExists(String accountName) {
        List<Account> accounts = addTransactionViewModel.getAllAccounts().getValue();
        if (accounts == null) return false;
        for (Account acc : accounts) {
            if (acc.getAccountName().equals(accountName)) return true;
        }
        return false;
    }

    private String findFirestoreIdByAccountTypeName(String name) {
        AccountTypeViewModel accountTypeViewModel = new ViewModelProvider(this).get(AccountTypeViewModel.class);
        List<AccountType> types = accountTypeViewModel.getAllAccountTypes().getValue();
        if (types == null) return null;
        for (AccountType type : types) {
            if (type.getName().equals(name)) return type.getFirestoreId();
        }
        return null;
    }
}