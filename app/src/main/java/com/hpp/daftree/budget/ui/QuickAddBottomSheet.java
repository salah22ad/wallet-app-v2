package com.hpp.daftree.budget.ui;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.hpp.daftree.databinding.BottomSheetBudgetQuickAddBinding;

public class QuickAddBottomSheet extends BottomSheetDialogFragment {

    public interface QuickAddListener {
        void onQuickAddExpense();
        void onQuickAddIncome();
        void onQuickAddDebtTransaction();
        void onQuickAddAccount();
    }

    private BottomSheetBudgetQuickAddBinding binding;
    private QuickAddListener listener;

    public static QuickAddBottomSheet newInstance() {
        return new QuickAddBottomSheet();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof QuickAddListener) {
            listener = (QuickAddListener) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetBudgetQuickAddBinding.inflate(inflater, container, false);
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

        binding.cardQuickExpense.setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onQuickAddExpense();
        });

        binding.cardQuickIncome.setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onQuickAddIncome();
        });

        binding.cardQuickDebt.setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onQuickAddDebtTransaction();
        });

        binding.cardQuickAccount.setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onQuickAddAccount();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
