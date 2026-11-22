package com.hpp.daftree.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.hpp.daftree.R;
import com.hpp.daftree.databinding.ItemCurrencySummaryBinding;
import com.hpp.daftree.debt.ExternalDebtModels.CurrencySummary;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class CurrencySummaryAdapter extends RecyclerView.Adapter<CurrencySummaryAdapter.ViewHolder> {

    private final List<CurrencySummary> items;
    private final OnCurrencyClickListener listener;

    public interface OnCurrencyClickListener {
        void onCurrencyClick(CurrencySummary currencySummary);
    }

    public CurrencySummaryAdapter(List<CurrencySummary> items, OnCurrencyClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemCurrencySummaryBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
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
        private final ItemCurrencySummaryBinding binding;

        ViewHolder(ItemCurrencySummaryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(CurrencySummary item) {
            Context context = binding.getRoot().getContext();
            binding.tvCurrencyName.setText(item.currencyName);

            binding.tvTotalDebit.setText(formatCurrency( item.totalDebit));
            binding.tvTotalCredit.setText(formatCurrency( item.totalCredit));
            binding.tvNetBalance.setText(formatCurrency( Math.abs(item.netBalance)));

            if (item.netBalance > 0) {
                binding.tvNetBalance.setTextColor(ContextCompat.getColor(context, R.color.colorCredit));
                String text = binding.tvNetBalance.getText() + context.getString(R.string.suffix_credit_you);
                binding.tvNetBalance.setText(text);
            } else if (item.netBalance < 0) {
                binding.tvNetBalance.setTextColor(ContextCompat.getColor(context, R.color.colorDebit));
                String text = binding.tvNetBalance.getText() + context.getString(R.string.suffix_debit_you);
                binding.tvNetBalance.setText(text);
            } else {
                binding.tvNetBalance.setTextColor(ContextCompat.getColor(context, android.R.color.black));
            }

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onCurrencyClick(item);
            });
        }
    }
    private String formatCurrency(double amount) {
        return NumberFormat.getNumberInstance(Locale.US).format(amount);
    }
}