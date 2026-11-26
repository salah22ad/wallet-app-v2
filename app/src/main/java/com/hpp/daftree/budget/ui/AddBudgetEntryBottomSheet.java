package com.hpp.daftree.budget.ui;

import android.app.DatePickerDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.models.FormatingAmount;
import com.hpp.daftree.budget.adapter.BudgetCategoryGridAdapter;
import com.hpp.daftree.budget.data.BudgetEntry;
import com.hpp.daftree.budget.data.BudgetEntryWithCategory;
import com.hpp.daftree.budget.util.BudgetUtils;
import com.hpp.daftree.budget.viewmodel.BudgetViewModel;
import com.hpp.daftree.databinding.BottomSheetAddBudgetEntryBinding;

import java.util.Calendar;

public class AddBudgetEntryBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_IS_EDIT = "is_edit";
    private static final String ARG_ENTRY_ID = "entry_id";
    private static final String ARG_ENTRY_TYPE = "entry_type";
    private static final String ARG_CATEGORY_ID = "category_id";
    private static final String ARG_AMOUNT = "amount";
    private static final String ARG_NOTE = "note";
    private static final String ARG_ENTRY_DATE = "entry_date";

    private BottomSheetAddBudgetEntryBinding binding;
    private BudgetViewModel viewModel;
    private BudgetCategoryGridAdapter categoryAdapter;

    private boolean isEditMode = false;
    private int editEntryId = -1;
    private String entryType = BudgetEntry.TYPE_EXPENSE;
    private int preselectedCategoryId = -1;
    private long selectedDateMillis;

    public static AddBudgetEntryBottomSheet newInstanceForAdd(String entryType) {
        AddBudgetEntryBottomSheet sheet = new AddBudgetEntryBottomSheet();
        Bundle args = new Bundle();
        args.putBoolean(ARG_IS_EDIT, false);
        args.putString(ARG_ENTRY_TYPE, entryType);
        sheet.setArguments(args);
        return sheet;
    }

    public static AddBudgetEntryBottomSheet newInstanceForEdit(BudgetEntryWithCategory entry) {
        AddBudgetEntryBottomSheet sheet = new AddBudgetEntryBottomSheet();
        Bundle args = new Bundle();
        args.putBoolean(ARG_IS_EDIT, true);
        args.putInt(ARG_ENTRY_ID, entry.id);
        args.putString(ARG_ENTRY_TYPE, entry.type);
        args.putInt(ARG_CATEGORY_ID, entry.categoryId);
        args.putDouble(ARG_AMOUNT, entry.amount);
        args.putString(ARG_NOTE, entry.note != null ? entry.note : "");
        args.putLong(ARG_ENTRY_DATE, entry.entryDate);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetAddBudgetEntryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundColor(Color.TRANSPARENT);
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(BudgetViewModel.class);

        Bundle args = getArguments();
        if (args != null) {
            isEditMode = args.getBoolean(ARG_IS_EDIT, false);
            editEntryId = args.getInt(ARG_ENTRY_ID, -1);
            entryType = args.getString(ARG_ENTRY_TYPE, BudgetEntry.TYPE_EXPENSE);
            preselectedCategoryId = args.getInt(ARG_CATEGORY_ID, -1);
            if (isEditMode) {
                double amount = args.getDouble(ARG_AMOUNT, 0.0);
                if (amount > 0) {
                    binding.etBudgetAmount.setText(BudgetUtils.formatAmount(amount));
                }
                binding.etBudgetNote.setText(args.getString(ARG_NOTE, ""));
                selectedDateMillis = args.getLong(ARG_ENTRY_DATE, System.currentTimeMillis());
            } else {
                selectedDateMillis = resolveDefaultDateForSelectedMonth();
            }
        } else {
            selectedDateMillis = System.currentTimeMillis();
        }

        MyApplication.applyGlobalTextWatcher(binding.getRoot());
        FormatingAmount.applyTo(binding.etBudgetAmount);
        setupHeaderAndColors();
        setupCategoryGrid();
        setupListeners();
        updateDateDisplay();
        focusAmountAndShowKeyboard();
    }

    private void focusAmountAndShowKeyboard() {
        if (binding == null) return;
        binding.etBudgetAmount.postDelayed(() -> {
            if (binding == null || !isAdded()) return;
            binding.etBudgetAmount.requestFocus();
            if (binding.etBudgetAmount.getText() != null) {
                binding.etBudgetAmount.setSelection(binding.etBudgetAmount.getText().length());
            }
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(binding.etBudgetAmount, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        }, 150);
    }

    private long resolveDefaultDateForSelectedMonth() {
        String selectedYm = viewModel.getCurrentYearMonthValue();
        String currentYm = BudgetUtils.getCurrentYearMonth();
        if (selectedYm.equals(currentYm)) {
            return System.currentTimeMillis();
        }
        Calendar target = BudgetUtils.parseYearMonth(selectedYm);
        Calendar today = Calendar.getInstance();
        int maxDay = target.getActualMaximum(Calendar.DAY_OF_MONTH);
        int day = Math.min(today.get(Calendar.DAY_OF_MONTH), maxDay);
        target.set(Calendar.DAY_OF_MONTH, day);
        target.set(Calendar.HOUR_OF_DAY, today.get(Calendar.HOUR_OF_DAY));
        target.set(Calendar.MINUTE, today.get(Calendar.MINUTE));
        return target.getTimeInMillis();
    }

    private void setupHeaderAndColors() {
        boolean isIncome = BudgetEntry.TYPE_INCOME.equals(entryType);
        int themeColor = ContextCompat.getColor(
                requireContext(), isIncome ? R.color.budget_income : R.color.budget_expense);

        String monthLabel = BudgetUtils.formatYearMonthDisplay(
                BudgetUtils.toYearMonth(selectedDateMillis), requireContext());
        if (isEditMode) {
            binding.tvSheetTitle.setText(getString(
                    isIncome ? R.string.budget_edit_income_title : R.string.budget_edit_expense_title,
                    monthLabel));
            binding.btnSaveBudgetEntry.setText(R.string.budget_update_entry);
        } else {
            binding.tvSheetTitle.setText(getString(
                    isIncome ? R.string.budget_add_income_title : R.string.budget_add_expense_title,
                    monthLabel));
            binding.btnSaveBudgetEntry.setText(R.string.budget_save_and_update);
        }

        binding.tvSheetTitle.setTextColor(themeColor);
        binding.btnSaveBudgetEntry.setBackgroundTintList(ColorStateList.valueOf(themeColor));
        binding.etBudgetNote.setHint(isIncome ? R.string.budget_note_income_hint : R.string.budget_note_expense_hint);

        String currencyDisplay = viewModel.getCurrentCurrencyNameValue();
        if (currencyDisplay.isEmpty()) {
            currencyDisplay = viewModel.getCurrentCurrencySymbolValue();
        }
        binding.tvAmountLabel.setText(getString(R.string.budget_amount_label, currencyDisplay));
    }

    private void setupCategoryGrid() {
        categoryAdapter = new BudgetCategoryGridAdapter(category -> {});
        binding.rvBudgetCategories.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        binding.rvBudgetCategories.setAdapter(categoryAdapter);

        viewModel.getCategoriesByType(entryType).observe(getViewLifecycleOwner(), categories -> {
            categoryAdapter.submitList(categories, preselectedCategoryId);
            preselectedCategoryId = -1;
        });
    }

    private void setupListeners() {
        binding.btnCloseSheet.setOnClickListener(v -> dismiss());
        binding.layoutEntryDate.setOnClickListener(v -> openDatePicker());
        binding.btnSaveBudgetEntry.setOnClickListener(v -> saveEntry());
    }

    private void openDatePicker() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(selectedDateMillis);
        DatePickerDialog datePicker = new DatePickerDialog(
                requireContext(),
                (dp, year, month, dayOfMonth) -> {
                    Calendar picked = Calendar.getInstance();
                    picked.set(Calendar.YEAR, year);
                    picked.set(Calendar.MONTH, month);
                    picked.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    selectedDateMillis = picked.getTimeInMillis();
                    updateDateDisplay();
                    setupHeaderAndColors();
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        );
        datePicker.getDatePicker().setMaxDate(BudgetUtils.getEndOfCurrentMonthMillis());
        datePicker.show();
    }

    private void updateDateDisplay() {
        binding.tvEntryDateValue.setText(BudgetUtils.formatDateIso(selectedDateMillis));
    }

    private void saveEntry() {
        String rawAmount = binding.etBudgetAmount.getText() != null
                ? binding.etBudgetAmount.getText().toString().trim() : "";
        double amount = parseAmountSafe(rawAmount);
        if (amount <= 0) {
            binding.etBudgetAmount.setError(getString(R.string.budget_invalid_amount));
            binding.etBudgetAmount.requestFocus();
            return;
        }

        int categoryId = categoryAdapter.getSelectedCategoryId();
        if (categoryId <= 0) {
            Toast.makeText(requireContext(), R.string.budget_select_category_error, Toast.LENGTH_SHORT).show();
            return;
        }

        String note = binding.etBudgetNote.getText() != null
                ? binding.etBudgetNote.getText().toString().trim() : "";

        binding.btnSaveBudgetEntry.setEnabled(false);

        Runnable onDone = () -> {
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    viewModel.setSelectedYearMonth(BudgetUtils.toYearMonth(selectedDateMillis));
                    Toast.makeText(requireContext(), R.string.budget_saved_success, Toast.LENGTH_SHORT).show();
                    dismiss();
                });
            }
        };

        if (isEditMode && editEntryId > 0) {
            viewModel.updateEntry(editEntryId, categoryId, amount, note, selectedDateMillis, onDone);
        } else {
            viewModel.addEntry(categoryId, entryType, amount, note, selectedDateMillis, onDone);
        }
    }

    private double parseAmountSafe(String input) {
        if (input == null || input.isEmpty()) return 0.0;
        String normalized = input
                .replace('٠', '0').replace('١', '1').replace('٢', '2').replace('٣', '3').replace('٤', '4')
                .replace('٥', '5').replace('٦', '6').replace('٧', '7').replace('٨', '8').replace('٩', '9')
                .replace('۰', '0').replace('۱', '1').replace('۲', '2').replace('۳', '3').replace('۴', '4')
                .replace('۵', '5').replace('۶', '6').replace('۷', '7').replace('۸', '8').replace('۹', '9')
                .replace("٬", "").replace(",", "").replace("٫", ".").trim();
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
