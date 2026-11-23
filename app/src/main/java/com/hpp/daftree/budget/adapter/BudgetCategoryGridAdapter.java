package com.hpp.daftree.budget.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.hpp.daftree.R;
import com.hpp.daftree.budget.data.BudgetCategory;
import com.hpp.daftree.budget.util.BudgetUtils;
import com.hpp.daftree.databinding.ItemBudgetCategoryChipBinding;

import java.util.ArrayList;
import java.util.List;

public class BudgetCategoryGridAdapter extends RecyclerView.Adapter<BudgetCategoryGridAdapter.ViewHolder> {

    public interface OnCategorySelectedListener {
        void onCategorySelected(BudgetCategory category);
    }

    private final List<BudgetCategory> categories = new ArrayList<>();
    private final OnCategorySelectedListener listener;
    private int selectedCategoryId = -1;

    public BudgetCategoryGridAdapter(OnCategorySelectedListener listener) {
        this.listener = listener;
    }

    public void submitList(List<BudgetCategory> list, int preselectedId) {
        categories.clear();
        if (list != null) {
            categories.addAll(list);
        }
        if (preselectedId > 0) {
            selectedCategoryId = preselectedId;
        } else if (selectedCategoryId <= 0 && !categories.isEmpty()) {
            selectedCategoryId = categories.get(0).id;
        }
        notifyDataSetChanged();
    }

    public int getSelectedCategoryId() {
        return selectedCategoryId;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemBudgetCategoryChipBinding binding = ItemBudgetCategoryChipBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(categories.get(position));
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemBudgetCategoryChipBinding binding;

        ViewHolder(ItemBudgetCategoryChipBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(BudgetCategory cat) {
            Context ctx = binding.getRoot().getContext();
            boolean isSelected = cat.id == selectedCategoryId;
            binding.cardCategoryOption.setSelected(isSelected);

            int fallback = ContextCompat.getColor(ctx, R.color.budget_primary);
            int iconColor = BudgetUtils.parseColorSafe(cat.colorHex, fallback);
            binding.ivCatOptionIcon.setImageResource(BudgetUtils.getIconRes(cat.iconKey));
            binding.ivCatOptionIcon.setColorFilter(iconColor);

            String displayName = BudgetUtils.getCategoryDisplayName(ctx, cat.code, cat.name, cat.isDefault);
            binding.tvCatOptionName.setText(displayName);

            binding.cardCategoryOption.setOnClickListener(v -> {
                if (selectedCategoryId != cat.id) {
                    selectedCategoryId = cat.id;
                    notifyDataSetChanged();
                    if (listener != null) {
                        listener.onCategorySelected(cat);
                    }
                }
            });
        }
    }
}
