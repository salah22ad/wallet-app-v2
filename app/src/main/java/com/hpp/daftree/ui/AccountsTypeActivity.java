package com.hpp.daftree.ui;


import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.hpp.daftree.viewmodels.AccountTypeViewModel;
import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.models.UUIDGenerator;
import com.hpp.daftree.adapters.AccountsTypeAdapter;
import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.helpers.AccountTypeIconHelper;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.databinding.ActivityAccountsTypeBinding;
import com.hpp.daftree.models.DaftreeRepository;

import com.hpp.daftree.utils.SecureLicenseManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AccountsTypeActivity extends BaseActivity implements AccountsTypeAdapter.OnItemInteractionListener {

    private ActivityAccountsTypeBinding binding; // افترض وجود هذا الـ binding
    private AccountTypeViewModel accountTypeViewModel;
    private AccountsTypeAdapter adapter;
    private final List<AccountType> defaultTypes = new ArrayList<>();
    private final List<AccountType> allTypes = new ArrayList<>();
    private boolean isGuest;
    private  String guestUID;

    @Override
    public void onAccountTypeLongClicked(AccountType accountType) {
        List<String> defaultTypes = Arrays.asList(getString(R.string._52), getString(R.string._52_896f), getString(R.string._52_894c));

        if (defaultTypes.contains(accountType.name)) {
            showDefaultItemWarning();
        } else {
            showEditDeleteAccountTypeDialog(accountType);
        }
    }

    // **دالة جديدة لعرض رسالة التحذير**
    private void showDefaultItemWarning() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string._64))
                .setMessage(getString(R.string._65))
                .setPositiveButton(getString(R.string._66), null)
                .setIcon(R.drawable.ic_info)
                .show();
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAccountsTypeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupFloatingToolbar(binding.accountsTypesRecyclerView);
        MyApplication.applyGlobalTextWatcher(binding.getRoot());

        isGuest= SecureLicenseManager.getInstance(this).isGuest();
        guestUID= SecureLicenseManager.getInstance(this).guestUID();
//        setupDefaultAccountTypes();


        accountTypeViewModel = new ViewModelProvider(this).get(AccountTypeViewModel.class);
//        accountTypeViewModel.getAllAccountTypes().observe(this, accountsType -> {
//            adapter.setAccountsType(accountsType);
//        });
        accountTypeViewModel.getAllAccountTypes().observe(this, accountTypes -> {
            if (adapter != null) {
                adapter.setAccountsType(accountTypes);
            }
        });
        setupRecyclerView();
        binding.fabAddAccountType.setOnClickListener(v -> showAddAccountTypeDialog());
        setupInsets();
    }
    private void setupInsets() {
        View bottomNavContainer = binding.fabAddAccountType; // أو findViewById(R.id.bottom_nav_container);

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


    private void showEditDeleteAccountTypeDialog(final AccountType accountType) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        final EditText input = new EditText(this);
        input.setText(accountType.name);
        input.setGravity(Gravity.CENTER_HORIZONTAL);
        input.setTypeface(null, Typeface.BOLD);
        input.setEnabled(false);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(30), (source, start, end, dest, dstart, dend) -> {
            String text = dest.toString() + source.toString();
            if (text.split("\\s+").length > 2) return "";
            return null;
        }});
        layout.addView(input);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string._127))
                .setView(layout)
                .setPositiveButton(getString(R.string._129), null)
                .setNeutralButton(getString(R.string._130), null)
                .setNegativeButton(getString(R.string._131), null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button buttonSave = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button buttonEdit = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            Button buttonDelete = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

            buttonSave.setVisibility(View.GONE);

            buttonEdit.setOnClickListener(v -> {
                input.setEnabled(true);
                input.requestFocus();
                buttonEdit.setVisibility(View.GONE);
                buttonSave.setVisibility(View.VISIBLE);
            });

            buttonSave.setOnClickListener(v -> {
                String newName = input.getText().toString().trim();
                if (newName.isEmpty() || newName.equals(accountType.name)) {
                    dialog.dismiss();
                    return;
                }

                // **المنطق الجديد: التحقق من الاسم المكرر قبل الحفظ**
                DaftreeRepository repo = new DaftreeRepository(getApplication());
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    AccountType existing = repo.getAccountTypeByName(newName);
                    runOnUiThread(() -> {
                        if (existing != null) {
                            Toast.makeText(this, getString(R.string._161), Toast.LENGTH_SHORT).show();
                        } else {
                            repo.updateAccountType(accountType, newName);
                            Toast.makeText(this, getString(R.string._164), Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        }
                    });
                });
            });

            buttonDelete.setOnClickListener(v -> {
                // **منطق الحذف الكامل مع التحقق من الارتباط**
                DaftreeRepository repo = new DaftreeRepository(getApplication());
                // تنفيذ الفحص في خيط خلفي لتجنب تجميد الواجهة
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    int count = repo.getAccountCountForType(accountType.name);
                    // العودة للخيط الرئيسي لعرض النتيجة
                    runOnUiThread(() -> {
                        if (count > 0) {
                            // إذا كان هناك حسابات مرتبطة، اعرض رسالة تحذير
                            new MaterialAlertDialogBuilder(this)
                                    .setTitle(getString(R.string._182))
                                    .setMessage(getString(R.string._183) + count + getString(R.string._183_bb54))
                                    .setPositiveButton(getString(R.string._184), null)
                                    .setIcon(R.drawable.ic_alert)
                                    .show();
                        } else {
                            // إذا لم يكن هناك ارتباط، اعرض ديالوج تأكيد الحذف
                            new MaterialAlertDialogBuilder(this)
                                    .setTitle(getString(R.string._190))
                                    .setMessage(getString(R.string._191) + accountType.name + getString(R.string.text_191))
                                    .setNegativeButton(getString(R.string._192), null)
                                    .setPositiveButton(getString(R.string._193), (d, w) -> {
                                        repo.deleteAccountType(accountType);
                                        Toast.makeText(this, getString(R.string._195), Toast.LENGTH_SHORT).show();
                                    })
                                    .show();
                        }
                        dialog.dismiss();
                    });
                });
            });
        });
        dialog.show();
    }
    private void setupRecyclerView() {
        adapter = new AccountsTypeAdapter(new ArrayList<>(), AccountsTypeActivity.this);
        binding.accountsTypesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.accountsTypesRecyclerView.setAdapter(adapter);
    }

    private void showAddAccountTypeDialog() {
        EditText input = new EditText(this);
        // فلتر لمنع إدخال أكثر من كلمتين
        input.setFilters(new InputFilter[] { (source, start, end, dest, dstart, dend) -> {
            String text = dest.toString() + source.toString();
            if (text.split("\\s+").length > 2) {
                return ""; // منع الإدخال
            }
            return null; // السماح بالإدخال
        }});

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string._224))
                .setMessage(getString(R.string._225))
                .setView(input)
                .setPositiveButton(getString(R.string._227), (dialog, which) -> {
                    String accountTypeName = input.getText().toString().trim();
                    if (validateAccountType(accountTypeName)) {
                        saveAccountType(accountTypeName, () -> {
                            // نجاح -> اغلاق الديالوج على الـ UI thread
                            runOnUiThread(dialog::dismiss);
                        });
                    }
                })
                .setNegativeButton(getString(R.string._236), null)
                .show();
    }

    private boolean validateAccountType(String name) {
        List<AccountType> accountTypeList = adapter.getAccountTypes(allTypes); // افترض وجود دالة getter في Adapter

        if (name.isEmpty()) {
            Toast.makeText(this, getString(R.string._244), Toast.LENGTH_SHORT).show();
            return false;
        }
        // التحقق من عدم تكرار العملة
        for (AccountType accountType : accountTypeList) {
            if (accountType.name.equalsIgnoreCase(name)) {
                Toast.makeText(this, getString(R.string._250), Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        if ( name.split("\\s+").length > 2) {
            Toast.makeText(this, R.string.error_name_length, Toast.LENGTH_SHORT).show();
            return false; // <-- لن يتم إغلاق الديالوج هنا
        }
        return true;
    }
    /**
     * حفظ عملة جديدة — يقوم بفحص التكرار في DB داخل Background thread
     * @param name اسم العملة
     * @param onSuccess Runnable يُستدعى على UI thread عند نجاح الحفظ (مثلاً لإغلاق الديالوج)
     */
    private void saveAccountType(String name, Runnable onSuccess) {
        if (name == null) return;
        final String trimmed = name.trim();
        if (!validateAccountType(trimmed)) return;

        final String uid;
        if (isGuest) {
            uid = SecureLicenseManager.getInstance(this).guestUID();
        } else {
            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                Toast.makeText(this, getString(R.string._275), Toast.LENGTH_SHORT).show();
                return;
            }
            uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        if (uid == null) {
            Toast.makeText(this, getString(R.string._282), Toast.LENGTH_SHORT).show();
            return;
        }

        // فحص وجود العملة في القاعدة ثم الإدخال
        DaftreeRepository repo = new DaftreeRepository(getApplication());
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AccountType existing = repo.getAccountTypeByName(trimmed);
            runOnUiThread(() -> {
                if (existing != null) {
                    Toast.makeText(this, getString(R.string._292), Toast.LENGTH_SHORT).show();
                } else {
                    AccountType newAccountType = new AccountType();
                    newAccountType.name = name;
                    newAccountType.setOwnerUID(uid);
                    newAccountType.setFirestoreId(UUIDGenerator.generateSequentialUUID());
                    newAccountType.setSyncStatus("NEW");
                    newAccountType.setLastModified(System.currentTimeMillis());
                    newAccountType.setDefault(false);
                    AccountTypeIconHelper.ensureTypeVisuals(this, newAccountType);
                    accountTypeViewModel.insert(newAccountType);

                    Toast.makeText(this, getString(R.string._303), Toast.LENGTH_SHORT).show();

                    if (onSuccess != null) {
                        onSuccess.run(); // عادة إغلاق الديالوج
                    }
                }
            });
        });
    }
    private void saveAccountType11(String name) {
        String uid;
        if (isGuest) {
            uid = SecureLicenseManager.getInstance(this).guestUID();
        } else {
            uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
        if (uid == null) {
            Toast.makeText(this, getString(R.string._320), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!validateAccountType(name)){
            return;
        }
        AccountType newAccountType = new AccountType();
        newAccountType.name = name;
        newAccountType.setOwnerUID(uid);
        newAccountType.setFirestoreId(UUIDGenerator.generateSequentialUUID());
        newAccountType.setSyncStatus("NEW");
        newAccountType.setLastModified(System.currentTimeMillis());
        newAccountType.setDefault(false);
        accountTypeViewModel.insert(newAccountType);
        Toast.makeText(this, getString(R.string._334), Toast.LENGTH_SHORT).show();
    }
    @Override
    public void onDeleteAccountType(AccountType accountType) {
        if (accountType.isDefault()) {
            Toast.makeText(this,R.string.error_deleted , Toast.LENGTH_SHORT).show();
            return;
        }

        DaftreeRepository repo = new DaftreeRepository(getApplication());
        // تنفيذ الفحص في خيط خلفي لتجنب تجميد الواجهة
        AppDatabase.databaseWriteExecutor.execute(() -> {
            int count = repo.getAccountCountForType(accountType.name);
            // العودة للخيط الرئيسي لعرض النتيجة
            runOnUiThread(() -> {
                if (count > 0) {
                    // إذا كان هناك حسابات مرتبطة، اعرض رسالة تحذير
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(getString(R.string._352))
                            .setMessage(getString(R.string._353) + count + getString(R.string._353_ff94))
                            .setPositiveButton(getString(R.string._354), null)
                            .setIcon(R.drawable.ic_alert)
                            .show();
                } else {
                    // إذا لم يكن هناك ارتباط، اعرض ديالوج تأكيد الحذف
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(getString(R.string._360))
                            .setMessage(getString(R.string._361) + accountType.name + getString(R.string.text_361))
                            .setNegativeButton(getString(R.string._362), null)
                            .setPositiveButton(getString(R.string._363), (d, w) -> {
                                repo.deleteAccountType(accountType);
                                Toast.makeText(this, getString(R.string._365), Toast.LENGTH_SHORT).show();
                            })
                            .show();
                }
            });
        });
    }
    @Override
    public void onEditeAccountType(AccountType accountType) {
        showAccountTypeDialog(accountType, true);
    }

    private void showAccountTypeDialog(AccountType accountType, boolean isEdit) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edite, null);
        EditText editName = dialogView.findViewById(R.id.edit_name);

        if (isEdit && accountType != null) {
            editName.setText(accountType.getName());
        }

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(isEdit ? R.string.edit_account_type : R.string.add_account_type)
                .setView(dialogView)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String name = editName.getText().toString().trim();
                    if (!validateAccountType(name)) return;
                    if (name.isEmpty()) {
                        Toast.makeText(this, R.string.required_field, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (isEdit) {
                        updateAccountType(accountType, name);
                    } else {
                        saveAccountType(name, () -> {
                            // نجاح -> اغلاق الديالوج على الـ UI thread
                            runOnUiThread(dialog::dismiss);
                        });
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
    private void updateAccountType(AccountType accountType, String name) {

        if ( name.equals(accountType.name)) {
            return;
        }
        if (!validateAccountType(name)){
            return;
        }
        // **المنطق الجديد: التحقق من الاسم المكرر قبل الحفظ**
        DaftreeRepository repo = new DaftreeRepository(getApplication());
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AccountType existing = repo.getAccountTypeByName(name);
            runOnUiThread(() -> {
                if (existing != null) {
                    Toast.makeText(this, getString(R.string._422), Toast.LENGTH_SHORT).show();
                } else {
                    repo.updateAccountType(accountType, name);
                    Toast.makeText(this, getString(R.string._425), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}