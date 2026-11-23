package com.hpp.daftree.budget.adapter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.hpp.daftree.R;
import com.hpp.daftree.helpers.AppFontHelper;
import com.hpp.daftree.budget.data.BudgetEntry;
import com.hpp.daftree.budget.data.BudgetEntryWithCategory;
import com.hpp.daftree.budget.util.BudgetUtils;
import com.hpp.daftree.databinding.ItemBudgetEntryBinding;

import java.util.ArrayList;
import java.util.List;

public class BudgetEntryAdapter extends RecyclerView.Adapter<BudgetEntryAdapter.ViewHolder> {

    public interface OnEntryActionListener {
        void onEditEntry(BudgetEntryWithCategory entry);
        void onDeleteEntry(BudgetEntryWithCategory entry);
    }

    private final List<BudgetEntryWithCategory> items = new ArrayList<>();
    private final OnEntryActionListener listener;
    private String currencyLabel = "";

    public BudgetEntryAdapter(OnEntryActionListener listener) {
        this.listener = listener;
    }

    public void submitData(List<BudgetEntryWithCategory> newItems, String currencyLabel) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        this.currencyLabel = currencyLabel != null ? currencyLabel : "";
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemBudgetEntryBinding binding = ItemBudgetEntryBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        AppFontHelper.applyFontToViewTree(parent.getContext(), binding.getRoot());
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemBudgetEntryBinding binding;

        ViewHolder(ItemBudgetEntryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(BudgetEntryWithCategory item) {
            Context ctx = binding.getRoot().getContext();
            boolean isIncome = BudgetEntry.TYPE_INCOME.equals(item.type);

            int fallback = ContextCompat.getColor(ctx, isIncome ? R.color.budget_income : R.color.budget_primary);
            int catColor = BudgetUtils.parseColorSafe(item.categoryColorHex, fallback);
            int softBg = Color.argb(34, Color.red(catColor), Color.green(catColor), Color.blue(catColor));

            Drawable bg = binding.containerEntryIcon.getBackground();
            if (bg != null) {
                Drawable wrapped = DrawableCompat.wrap(bg.mutate());
                DrawableCompat.setTint(wrapped, softBg);
                binding.containerEntryIcon.setBackground(wrapped);
            }

            binding.ivEntryCategoryIcon.setImageResource(BudgetUtils.getIconRes(item.categoryIconKey));
            binding.ivEntryCategoryIcon.setColorFilter(catColor);

            String catName = BudgetUtils.getCategoryDisplayName(
                    ctx, item.categoryCode, item.categoryName, item.categoryIsDefault);
            binding.tvEntryCategoryName.setText(catName);

            if (item.note != null && !item.note.trim().isEmpty()) {
                binding.tvEntryNote.setVisibility(View.VISIBLE);
                binding.tvEntryNote.setText(item.note.trim());
            } else {
                binding.tvEntryNote.setVisibility(View.GONE);
            }

            binding.tvEntryDate.setText(BudgetUtils.formatDateIso(item.entryDate));

            String prefix = isIncome ? "+" : "-";
            binding.tvEntryAmount.setText(prefix + BudgetUtils.formatAmountWithCurrency(item.amount, currencyLabel));
            binding.tvEntryAmount.setTextColor(ContextCompat.getColor(
                    ctx, isIncome ? R.color.budget_income : R.color.budget_expense));

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onEditEntry(item);
            });
            binding.btnDeleteEntry.setOnClickListener(v -> {
                if (listener != null) listener.onDeleteEntry(item);
            });
        }
    }
}
