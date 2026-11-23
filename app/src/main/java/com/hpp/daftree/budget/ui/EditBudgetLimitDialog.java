package com.hpp.daftree.budget.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.models.FormatingAmount;
import com.hpp.daftree.budget.util.BudgetUtils;
import com.hpp.daftree.budget.viewmodel.BudgetViewModel;
import com.hpp.daftree.databinding.DialogBudgetEditLimitBinding;

public class EditBudgetLimitDialog extends BottomSheetDialogFragment {

    private static final String ARG_CURRENT_LIMIT = "current_limit";

    private DialogBudgetEditLimitBinding binding;
    private BudgetViewModel viewModel;

    public static EditBudgetLimitDialog newInstance(double currentLimit) {
        EditBudgetLimitDialog dialog = new EditBudgetLimitDialog();
        Bundle args = new Bundle();
        args.putDouble(ARG_CURRENT_LIMIT, currentLimit);
        dialog.setArguments(args);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = DialogBudgetEditLimitBinding.inflate(inflater, container, false);
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

        String monthLabel = BudgetUtils.formatYearMonthDisplay(
                viewModel.getCurrentYearMonthValue(), requireContext());
        String curName = viewModel.getCurrentCurrencyNameValue();
        if (curName.isEmpty()) curName = viewModel.getCurrentCurrencySymbolValue();
        binding.tvLimitSubtitle.setText(getString(R.string.budget_edit_limit_subtitle, monthLabel, curName));

        double currentLimit = getArguments() != null ? getArguments().getDouble(ARG_CURRENT_LIMIT, 0.0) : 0.0;
        if (currentLimit > 0) {
            binding.etBudgetLimitAmount.setText(BudgetUtils.formatAmount(currentLimit));
            binding.btnClearBudgetLimit.setVisibility(View.VISIBLE);
        } else {
            binding.btnClearBudgetLimit.setVisibility(View.GONE);
        }

        MyApplication.applyGlobalTextWatcher(binding.getRoot());
        FormatingAmount.applyTo(binding.etBudgetLimitAmount);
        binding.btnCloseLimit.setOnClickListener(v -> dismiss());

        binding.btnClearBudgetLimit.setOnClickListener(v -> saveLimitValue(0.0));

        binding.btnSaveBudgetLimit.setOnClickListener(v -> {
            String raw = binding.etBudgetLimitAmount.getText() != null
                    ? binding.etBudgetLimitAmount.getText().toString().trim() : "";
            double value = parseAmountSafe(raw);
            if (value <= 0) {
                binding.etBudgetLimitAmount.setError(getString(R.string.budget_invalid_amount));
                binding.etBudgetLimitAmount.requestFocus();
                return;
            }
            saveLimitValue(value);
        });
    }

    private void saveLimitValue(double limitValue) {
        binding.btnSaveBudgetLimit.setEnabled(false);
        binding.btnClearBudgetLimit.setEnabled(false);
        viewModel.saveMonthlyLimit(limitValue, () -> {
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), R.string.budget_limit_updated, Toast.LENGTH_SHORT).show();
                    dismiss();
                });
            }
        });
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
