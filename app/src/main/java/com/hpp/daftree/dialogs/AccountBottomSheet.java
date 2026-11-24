package com.hpp.daftree.dialogs;

import static android.app.Activity.RESULT_OK;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.databinding.BottomSheetAccountBinding;
import com.hpp.daftree.helpers.AccountTypeIconHelper;
import com.hpp.daftree.helpers.AppFontHelper;
import com.hpp.daftree.models.AppAuth;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.notifications.TransactionNotificationManager;
import com.hpp.daftree.utils.DialogHelper;
import com.hpp.daftree.utils.SecureLicenseManager;
import com.hpp.daftree.viewmodels.AccountTypeViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AccountBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "AccountBottomSheet";
    private static final String ARG_MODE = "sheet_mode";
    private static final String ARG_ACCOUNT_ID = "account_id";
    private static final String ARG_INITIAL_NAME = "initial_name";

    public static final int MODE_CREATE = 1;
    public static final int MODE_VIEW_OPTIONS = 2;
    public static final int MODE_EDIT_DIRECT = 3;

    public interface AccountActionListener {
        default void onAccountSaved(Account account, boolean isNew) {}
        default void onAccountDeleted(Account account) {}
    }

    private BottomSheetAccountBinding binding;
    private DaftreeRepository repository;
    private AccountTypeViewModel accountTypeViewModel;
    private AccountActionListener actionListener;

    private int mode = MODE_CREATE;
    private int targetAccountId = -1;
    private String initialName = "";
    private Account loadedAccount = null;
    private String originalPhone = "";
    private List<AccountType> accountTypesList = new ArrayList<>();
    private boolean isEditingFields = true;
    private boolean isProgrammaticSwitchChange = false;

    private final ActivityResultLauncher<String> requestContactPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    openContactPicker();
                } else if (getContext() != null) {
                    Toast.makeText(getContext(), R.string.error_contact_permission, Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<Intent> pickContactLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    processSelectedContact(result.getData().getData());
                }
            });

    public static AccountBottomSheet newInstanceForCreate(@Nullable String prefillName) {
        AccountBottomSheet sheet = new AccountBottomSheet();
        Bundle args = new Bundle();
        args.putInt(ARG_MODE, MODE_CREATE);
        args.putString(ARG_INITIAL_NAME, prefillName != null ? prefillName.trim() : "");
        sheet.setArguments(args);
        return sheet;
    }

    public static AccountBottomSheet newInstanceForViewOptions(int accountId) {
        AccountBottomSheet sheet = new AccountBottomSheet();
        Bundle args = new Bundle();
        args.putInt(ARG_MODE, MODE_VIEW_OPTIONS);
        args.putInt(ARG_ACCOUNT_ID, accountId);
        sheet.setArguments(args);
        return sheet;
    }

    public static AccountBottomSheet newInstanceForEditDirect(int accountId) {
        AccountBottomSheet sheet = new AccountBottomSheet();
        Bundle args = new Bundle();
        args.putInt(ARG_MODE, MODE_EDIT_DIRECT);
        args.putInt(ARG_ACCOUNT_ID, accountId);
        sheet.setArguments(args);
        return sheet;
    }

    public void setAccountActionListener(@Nullable AccountActionListener listener) {
        this.actionListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetAccountBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        disableLayoutTransitions(binding.getRoot());
        AppFontHelper.applyFontToViewTree(requireContext(), binding.getRoot());
        MyApplication.applyGlobalTextWatcher(binding.getRoot());

        repository = new DaftreeRepository(requireActivity().getApplication());
        accountTypeViewModel = new ViewModelProvider(this).get(AccountTypeViewModel.class);

        if (getArguments() != null) {
            mode = getArguments().getInt(ARG_MODE, MODE_CREATE);
            targetAccountId = getArguments().getInt(ARG_ACCOUNT_ID, -1);
            initialName = getArguments().getString(ARG_INITIAL_NAME, "");
        }

        setupInsets();
        setupSwitchLogic();
        setupButtons();
        observeAccountTypes();

        if (mode == MODE_CREATE) {
            binding.tvSheetTitle.setText(R.string.add_new_account_title);
            binding.btnSaveAccount.setText(R.string.save);
            if (!initialName.isEmpty()) {
                binding.editAccountName.setText(initialName);
            }
            applyFieldsEditable(true);
            binding.layoutSaveCancelActions.setVisibility(View.VISIBLE);
            binding.layoutViewOptionsActions.setVisibility(View.GONE);
        } else {
            loadExistingAccount();
        }
    }

    private void disableLayoutTransitions(View v) {
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            vg.setLayoutTransition(null);
            for (int i = 0; i < vg.getChildCount(); i++) {
                disableLayoutTransitions(vg.getChildAt(i));
            }
        }
    }

    private void setupInsets() {
        final int baseBottom = binding.accountSheetContainer.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.accountSheetContainer, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), baseBottom + bars.bottom);
            return insets;
        });
    }

    private void setupSwitchLogic() {
        binding.phoneInputLayout.setEndIconOnClickListener(v -> {
            if (!isEditingFields) return;
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
                    == PackageManager.PERMISSION_GRANTED) {
                openContactPicker();
            } else {
                requestContactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS);
            }
        });

        // إلغاء التفعيل تلقائياً إذا قام المستخدم بمسح رقم الهاتف
        binding.editAccountPhone.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (s == null || s.toString().trim().isEmpty()) {
                    isProgrammaticSwitchChange = true;
                    binding.switchNotifyWhatsApp.setChecked(false);
                    binding.switchNotifySms.setChecked(false);
                    isProgrammaticSwitchChange = false;
                }
            }
        });

        binding.switchNotifyWhatsApp.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isProgrammaticSwitchChange) return;
            if (!isEditingFields) {
                isProgrammaticSwitchChange = true;
                btn.setChecked(!isChecked);
                isProgrammaticSwitchChange = false;
                return;
            }
            if (isChecked) {
                if (!hasValidPhoneInput()) {
                    isProgrammaticSwitchChange = true;
                    btn.setChecked(false);
                    isProgrammaticSwitchChange = false;
                    showPhoneRequiredWarning();
                    return;
                }
                if (binding.switchNotifySms.isChecked()) {
                    isProgrammaticSwitchChange = true;
                    binding.switchNotifySms.setChecked(false);
                    isProgrammaticSwitchChange = false;
                }
            }
        });

        binding.switchNotifySms.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isProgrammaticSwitchChange) return;
            if (!isEditingFields) {
                isProgrammaticSwitchChange = true;
                btn.setChecked(!isChecked);
                isProgrammaticSwitchChange = false;
                return;
            }
            if (isChecked) {
                if (!hasValidPhoneInput()) {
                    isProgrammaticSwitchChange = true;
                    btn.setChecked(false);
                    isProgrammaticSwitchChange = false;
                    showPhoneRequiredWarning();
                    return;
                }
                if (binding.switchNotifyWhatsApp.isChecked()) {
                    isProgrammaticSwitchChange = true;
                    binding.switchNotifyWhatsApp.setChecked(false);
                    isProgrammaticSwitchChange = false;
                }
            }
        });
    }

    private boolean hasValidPhoneInput() {
        String phone = binding.editAccountPhone.getText() != null
                ? binding.editAccountPhone.getText().toString().trim() : "";
        return !phone.isEmpty();
    }

    private void showPhoneRequiredWarning() {
        binding.editAccountPhone.requestFocus();
        DialogHelper.show(
                requireContext(),
                getString(R.string.error_phone_required_for_notify),
                DialogHelper.FeedbackType.WARNING,
                null
        );
    }

    private void setupButtons() {
        binding.btnCloseSheet.setOnClickListener(v -> dismiss());
        binding.btnCancelAccount.setOnClickListener(v -> dismiss());
        binding.btnSaveAccount.setOnClickListener(v -> validateAndSave());

        binding.btnEnterEditMode.setOnClickListener(v -> {
            binding.tvSheetTitle.setText(R.string.edit_1);
            binding.btnSaveAccount.setText(R.string.save_edit);
            applyFieldsEditable(true);
            binding.layoutViewOptionsActions.setVisibility(View.GONE);
            binding.layoutSaveCancelActions.setVisibility(View.VISIBLE);
        });

        binding.btnDeleteAccount.setOnClickListener(v -> confirmAndDeleteAccount());
    }

    private void applyFieldsEditable(boolean editable) {
        isEditingFields = editable;
        binding.editAccountName.setEnabled(editable);
        binding.editAccountPhone.setEnabled(editable);
        binding.accountTypeLayout.setEnabled(editable);
        binding.spinnerAccountType.setEnabled(editable);
        binding.switchNotifyWhatsApp.setEnabled(editable);
        binding.switchNotifySms.setEnabled(editable);
    }

    private void observeAccountTypes() {
        accountTypeViewModel.getAllAccountTypes().observe(getViewLifecycleOwner(), types -> {
            if (types == null || types.isEmpty()) return;
            accountTypesList = types;
            List<String> names = types.stream().map(AccountType::getName).collect(Collectors.toList());
            ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, names);
            binding.spinnerAccountType.setAdapter(adapter);
            int itemHeight = (int) (48 * getResources().getDisplayMetrics().density);
            binding.spinnerAccountType.setDropDownHeight(itemHeight * 4);

            if (loadedAccount != null && loadedAccount.getAccountType() != null) {
                binding.spinnerAccountType.setText(loadedAccount.getAccountType(), false);
            } else if (binding.spinnerAccountType.getText().toString().trim().isEmpty() && !names.isEmpty()) {
                binding.spinnerAccountType.setText(names.get(0), false);
            }
        });
    }

    private void loadExistingAccount() {
        if (targetAccountId == -1) {
            dismiss();
            return;
        }
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Account acc = AppDatabase.getDatabase(requireContext()).accountDao().getAccountByIdBlocking(targetAccountId);
            if (!isAdded() || getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                if (acc == null) {
                    dismiss();
                    return;
                }
                loadedAccount = new Account(acc);
                originalPhone = acc.getPhoneNumber() != null ? acc.getPhoneNumber().trim() : "";

                binding.editAccountName.setText(acc.getAccountName());
                binding.editAccountPhone.setText(acc.getPhoneNumber());
                if (acc.getAccountType() != null) {
                    binding.spinnerAccountType.setText(acc.getAccountType(), false);
                }

                isProgrammaticSwitchChange = true;
                binding.switchNotifyWhatsApp.setChecked(acc.isNotifyWhatsApp());
                binding.switchNotifySms.setChecked(acc.isNotifySms());
                isProgrammaticSwitchChange = false;

                if (mode == MODE_VIEW_OPTIONS) {
                    binding.tvSheetTitle.setText(R.string.account_details_sheet_title);
                    applyFieldsEditable(false);
                    binding.layoutViewOptionsActions.setVisibility(View.VISIBLE);
                    binding.layoutSaveCancelActions.setVisibility(View.GONE);
                } else {
                    binding.tvSheetTitle.setText(R.string.edit_1);
                    binding.btnSaveAccount.setText(R.string.save_edit);
                    applyFieldsEditable(true);
                    binding.layoutViewOptionsActions.setVisibility(View.GONE);
                    binding.layoutSaveCancelActions.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private void validateAndSave() {
        String finalName = binding.editAccountName.getText() != null
                ? binding.editAccountName.getText().toString().trim() : "";
        String phone = binding.editAccountPhone.getText() != null
                ? binding.editAccountPhone.getText().toString().trim() : "";
        String type = binding.spinnerAccountType.getText() != null
                ? binding.spinnerAccountType.getText().toString().trim() : "";
        boolean notifyWa = binding.switchNotifyWhatsApp.isChecked();
        boolean notifySms = binding.switchNotifySms.isChecked();

        if (TextUtils.isEmpty(finalName)) {
            binding.accountNameInputLayout.setError(getString(R.string.error_account_name_empty));
            return;
        }
        binding.accountNameInputLayout.setError(null);

        if (TextUtils.isEmpty(type)) {
            binding.accountTypeLayout.setError(getString(R.string.required));
            return;
        }
        binding.accountTypeLayout.setError(null);

        if ((notifyWa || notifySms) && phone.isEmpty()) {
            showPhoneRequiredWarning();
            return;
        }
        // ضمان عدم تفعيل الميزتين معاً أبداً
        if (notifyWa && notifySms) {
            notifySms = false;
        }

        binding.btnSaveAccount.setEnabled(false);
        binding.btnCancelAccount.setEnabled(false);

        final boolean finalNotifyWa = notifyWa;
        final boolean finalNotifySms = notifySms;
        final boolean isCreate = (mode == MODE_CREATE || loadedAccount == null);

        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(requireContext());
            Account duplicate = db.accountDao().getAccountByNameBlocking(finalName);
            if (duplicate != null && (isCreate || duplicate.getId() != loadedAccount.getId())) {
                if (isAdded() && getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        binding.btnSaveAccount.setEnabled(true);
                        binding.btnCancelAccount.setEnabled(true);
                        Toast.makeText(getContext(), R.string.error_account_exists, Toast.LENGTH_SHORT).show();
                    });
                }
                return;
            }

            AccountType matchedType = findTypeByName(type);
            String typeId = matchedType != null ? matchedType.getFirestoreId() : "";
            boolean isGuest = SecureLicenseManager.getInstance(requireContext()).isGuest();
            String uid = isGuest
                    ? SecureLicenseManager.getInstance(requireContext()).guestUID()
                    : AppAuth.getUserID(requireContext());

            if (!originalPhone.equals(phone) && !TextUtils.isEmpty(phone)) {
                new TransactionNotificationManager(requireContext()).sendNewAccount(phone, requireContext());
            }

            final Account resultAccount;
            if (isCreate) {
                Account newAcc = new Account();
                newAcc.setOwnerUID(uid != null ? uid : "");
                newAcc.setAccountName(finalName);
                newAcc.setPhoneNumber(phone);
                newAcc.setAccountType(type);
                newAcc.setAcTypeFirestoreId(typeId != null ? typeId : "");
                newAcc.setNotifyWhatsApp(finalNotifyWa);
                newAcc.setNotifySms(finalNotifySms);
                AccountTypeIconHelper.applyTypeVisualsToAccount(requireContext(), newAcc, matchedType);
                newAcc.setFirestoreId(com.hpp.daftree.models.UUIDGenerator.generateSequentialUUID());
                newAcc.setSyncStatus("NEW");
                newAcc.setLastModified(System.currentTimeMillis());
                long newId = db.accountDao().insert(newAcc);
                newAcc.setId((int) newId);
                repository.triggerSync();
                resultAccount = newAcc;
            } else {
                loadedAccount.setAccountName(finalName);
                loadedAccount.setPhoneNumber(phone);
                loadedAccount.setAccountType(type);
                if (typeId != null && !typeId.isEmpty()) {
                    loadedAccount.setAcTypeFirestoreId(typeId);
                }
                loadedAccount.setNotifyWhatsApp(finalNotifyWa);
                loadedAccount.setNotifySms(finalNotifySms);
                AccountTypeIconHelper.applyTypeVisualsToAccount(requireContext(), loadedAccount, matchedType);
                if (!"NEW".equals(loadedAccount.getSyncStatus())) {
                    loadedAccount.setSyncStatus("EDITED");
                }
                loadedAccount.setLastModified(System.currentTimeMillis());
                db.accountDao().update(loadedAccount);
                repository.triggerSync();
                resultAccount = loadedAccount;
            }

            if (isAdded() && getActivity() != null) {
                requireActivity().runOnUiThread(() -> {
                    if (actionListener != null) {
                        actionListener.onAccountSaved(resultAccount, isCreate);
                    }
                    dismiss();
                });
            }
        });
    }

    private void confirmAndDeleteAccount() {
        if (loadedAccount == null) return;
        final Account accountToDelete = loadedAccount;

        AppDatabase.databaseWriteExecutor.execute(() -> {
            int txCount = repository.getTransactionCountForAccount(accountToDelete.getId());
            if (!isAdded() || getActivity() == null) return;

            requireActivity().runOnUiThread(() -> {
                if (txCount > 0) {
                    String msg = getString(R.string.confirm_delete_message, String.valueOf(txCount));
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.caution_titel)
                            .setMessage(msg)
                            .setIcon(R.drawable.ic_alert)
                            .setNegativeButton(R.string.cancel, null)
                            .setPositiveButton(R.string.ok, (d, w) -> {
                                repository.deleteAccount(accountToDelete);
                                if (actionListener != null) actionListener.onAccountDeleted(accountToDelete);
                                dismiss();
                            })
                            .show();
                } else {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.confirm_1)
                            .setMessage(getString(R.string.confirm_delete_account_message, accountToDelete.getAccountName()))
                            .setNegativeButton(R.string.cancel, null)
                            .setPositiveButton(R.string.delete, (d, w) -> {
                                repository.deleteAccount(accountToDelete);
                                if (actionListener != null) actionListener.onAccountDeleted(accountToDelete);
                                dismiss();
                            })
                            .show();
                }
            });
        });
    }

    @Nullable
    private AccountType findTypeByName(String name) {
        if (accountTypesList == null || name == null) return null;
        for (AccountType t : accountTypesList) {
            if (t.getName() != null && t.getName().trim().equalsIgnoreCase(name.trim())) {
                return t;
            }
        }
        return null;
    }

    private void openContactPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        pickContactLauncher.launch(intent);
    }

    private void processSelectedContact(@Nullable Uri dataUri) {
        if (dataUri == null || binding == null || getContext() == null) return;
        String[] projection = new String[]{
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };
        try (Cursor cursor = requireContext().getContentResolver().query(dataUri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                int nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                String number = numIdx != -1 ? cursor.getString(numIdx) : null;
                String name = nameIdx != -1 ? cursor.getString(nameIdx) : null;

                if (name != null && (binding.editAccountName.getText() == null || binding.editAccountName.getText().toString().trim().isEmpty())) {
                    binding.editAccountName.setText(name);
                }
                if (number != null) {
                    binding.editAccountPhone.setText(number.replaceAll("[\\s\\-()]", ""));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error picking contact", e);
            Toast.makeText(getContext(), R.string.processSelectedContact_01, Toast.LENGTH_SHORT).show();
        }
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
