package com.hpp.daftree.adapters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.AccountWithBalance;
import com.hpp.daftree.databinding.ItemAccountBinding;
import com.hpp.daftree.helpers.AppFontHelper;
import com.hpp.daftree.ui.AddTransactionActivity;

import java.text.NumberFormat;
import java.util.Locale;

public class AccountsAdapter extends ListAdapter<AccountWithBalance, AccountsAdapter.AccountViewHolder> {

    private static final int COLOR_CUSTOMER = Color.parseColor("#0284C7");
    private static final int COLOR_SUPPLIER = Color.parseColor("#D97706");
    private static final int COLOR_GENERAL  = Color.parseColor("#0D6E6E");

    // لوحة 16 لوناً متناسقاً للوضعين الفاتح والداكن لتلوين أي عدد من التصنيفات المخصصة ديناميكياً
    private static final int[] CUSTOM_TYPE_PALETTE = new int[]{
            Color.parseColor("#7C3AED"), // Violet
            Color.parseColor("#059669"), // Emerald
            Color.parseColor("#DB2777"), // Pink
            Color.parseColor("#EA580C"), // Orange
            Color.parseColor("#4F46E5"), // Indigo
            Color.parseColor("#0891B2"), // Cyan
            Color.parseColor("#E11D48"), // Rose
            Color.parseColor("#16A34A"), // Green
            Color.parseColor("#9333EA"), // Purple
            Color.parseColor("#2563EB"), // Blue
            Color.parseColor("#0F766E"), // Deep Teal
            Color.parseColor("#CA8A04"), // Gold
            Color.parseColor("#C026D3"), // Fuchsia
            Color.parseColor("#0369A1"), // Ocean
            Color.parseColor("#B45309"), // Bronze
            Color.parseColor("#6D28D9")  // Deep Purple
    };

    private final OnAccountInteractionListener mListener;
    private String currentCurrency = MyApplication.defaultCurrencyName;

    public interface OnAccountInteractionListener {
        void onAccountClicked(Account account, String currency);
        void onEditAccountClicked(Account account);
        void onAccountLongClicked(Account account);
    }

    public AccountsAdapter(OnAccountInteractionListener listener) {
        super(DIFF_CALLBACK);
        this.mListener = listener;
        this.currentCurrency = MyApplication.defaultCurrencyName;
    }

    private static final DiffUtil.ItemCallback<AccountWithBalance> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<AccountWithBalance>() {
                @Override
                public boolean areItemsTheSame(@NonNull AccountWithBalance oldItem, @NonNull AccountWithBalance newItem) {
                    return oldItem.account.getId() == newItem.account.getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull AccountWithBalance oldItem, @NonNull AccountWithBalance newItem) {
                    return oldItem.equals(newItem);
                }
            };

    @NonNull
    @Override
    public AccountViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAccountBinding binding = ItemAccountBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        AppFontHelper.applyFontToViewTree(parent.getContext(), binding.getRoot());
        return new AccountViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull AccountViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    public void setCurrency(String currency) {
        this.currentCurrency = currency;
    }

    private void applyAccountCategoryBadge(Context context, ItemAccountBinding binding, Account account) {
        String rawType = account.getAccountType() != null ? account.getAccountType().trim() : "";
        String lower = rawType.toLowerCase(Locale.ROOT);

        String customerStr = context.getString(R.string.account_type_customer).trim().toLowerCase(Locale.ROOT);
        String supplierStr = context.getString(R.string.account_type_supplier).trim().toLowerCase(Locale.ROOT);
        String generalStr  = context.getString(R.string.account_type_general).trim().toLowerCase(Locale.ROOT);

        int badgeColor;
        int iconRes;

        if (lower.equals(customerStr) || lower.contains("عملاء") || lower.contains("عميل")
                || lower.contains("customer") || lower.contains("client")) {
            badgeColor = COLOR_CUSTOMER;
            iconRes = R.drawable.ic_modern_account_customer;
        } else if (lower.equals(supplierStr) || lower.contains("مورد")
                || lower.contains("supplier") || lower.contains("vendor")) {
            badgeColor = COLOR_SUPPLIER;
            iconRes = R.drawable.ic_modern_account_supplier;
        } else if (lower.equals(generalStr) || lower.equals("عام") || lower.equals("general")) {
            badgeColor = COLOR_GENERAL;
            iconRes = R.drawable.ic_modern_account_general;
        } else {
            String key = (account.getAcTypeFirestoreId() != null && !account.getAcTypeFirestoreId().trim().isEmpty())
                    ? account.getAcTypeFirestoreId().trim()
                    : (!lower.isEmpty() ? lower : String.valueOf(account.getId()));
            int hash = key.hashCode() & 0x7fffffff;
            badgeColor = CUSTOM_TYPE_PALETTE[hash % CUSTOM_TYPE_PALETTE.length];
            iconRes = R.drawable.ic_modern_account_custom;
        }

        binding.iconCard.setCardBackgroundColor(badgeColor);
        binding.accountColorBackground.setBackgroundColor(badgeColor);
        binding.accountIcon.setImageResource(iconRes);
    }

    class AccountViewHolder extends RecyclerView.ViewHolder {
        private final ItemAccountBinding binding;

        public AccountViewHolder(ItemAccountBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(final AccountWithBalance accountWithBalance) {
            Context context = itemView.getContext();
            binding.accountNameTextView.setText(accountWithBalance.account.getAccountName());
            binding.balanceTextView.setText(NumberFormat.getNumberInstance(Locale.US).format(Math.abs(accountWithBalance.balance)));
            binding.transactionCountTextView.setText(String.valueOf(accountWithBalance.transactionCount));
            binding.accountTypeTextView.setText(accountWithBalance.account.getAccountType());

            applyAccountCategoryBadge(context, binding, accountWithBalance.account);

            if (accountWithBalance.balance > 0) {
                binding.arrowImageView.setImageResource(R.drawable.ic_arrow_down_red);
                binding.balanceTextView.setTextColor(ContextCompat.getColor(context, R.color.red));
            } else {
                binding.arrowImageView.setImageResource(R.drawable.ic_arrow_up_green);
                binding.balanceTextView.setTextColor(ContextCompat.getColor(context, R.color.green));
            }

            binding.editView.setOnClickListener(v -> {
                if (mListener != null) {
                    mListener.onEditAccountClicked(accountWithBalance.account);
                }
            });

            itemView.setOnClickListener(v -> {
                if (mListener != null) {
                    mListener.onAccountClicked(accountWithBalance.account, currentCurrency);
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (mListener != null) {
                    mListener.onAccountLongClicked(accountWithBalance.account);
                    return true;
                }
                return false;
            });

            binding.addTransactionButton.setOnClickListener(v -> {
                Intent intent = new Intent(context, AddTransactionActivity.class);
                intent.putExtra("ACCOUNT_ID", accountWithBalance.account.getId());
                intent.putExtra("ACCOUNT_NAME", accountWithBalance.account.getAccountName());
                intent.putExtra("CURRENCY", currentCurrency);
                intent.putExtra("isOld", "true");
                context.startActivity(intent);
            });
        }
    }
}
