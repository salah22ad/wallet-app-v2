package com.hpp.daftree.budget.adapter;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.hpp.daftree.R;
import com.hpp.daftree.helpers.AppFontHelper;
import com.hpp.daftree.budget.data.CategoryExpenseTotal;
import com.hpp.daftree.budget.util.BudgetUtils;
import com.hpp.daftree.databinding.ItemBudgetCategoryDistBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CategoryDistributionAdapter extends RecyclerView.Adapter<CategoryDistributionAdapter.ViewHolder> {

    private final List<CategoryExpenseTotal> items = new ArrayList<>();
    private double totalExpense = 0.0;
    private String currencyLabel = "";

    public void submitData(List<CategoryExpenseTotal> newItems, double totalExpense, String currencyLabel) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        this.totalExpense = totalExpense;
        this.currencyLabel = currencyLabel != null ? currencyLabel : "";
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemBudgetCategoryDistBinding binding = ItemBudgetCategoryDistBinding.inflate(
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
        private final ItemBudgetCategoryDistBinding binding;

        ViewHolder(ItemBudgetCategoryDistBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(CategoryExpenseTotal item) {
            Context ctx = binding.getRoot().getContext();
            int fallback = ContextCompat.getColor(ctx, R.color.budget_primary);
            int catColor = BudgetUtils.parseColorSafe(item.colorHex, fallback);

            Drawable dot = binding.viewCategoryColorDot.getBackground();
            if (dot != null) {
                Drawable wrapped = DrawableCompat.wrap(dot.mutate());
                DrawableCompat.setTint(wrapped, catColor);
                binding.viewCategoryColorDot.setBackground(wrapped);
            }

            String displayName = BudgetUtils.getCategoryDisplayName(
                    ctx, item.categoryCode, item.name, item.isDefault);
            binding.tvCategoryDistName.setText(displayName);

            double pct = (totalExpense > 0 && item.totalAmount > 0)
                    ? (item.totalAmount / totalExpense) * 100.0 : 0.0;
            binding.tvCategoryDistPercent.setText(String.format(Locale.US, "%.1f%%", pct));
            binding.tvCategoryDistAmount.setText(
                    BudgetUtils.formatAmountWithCurrency(item.totalAmount, currencyLabel));
        }
    }
}
