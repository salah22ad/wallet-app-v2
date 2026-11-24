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
import com.hpp.daftree.helpers.AccountTypeIconHelper;
import com.hpp.daftree.helpers.AppFontHelper;
import com.hpp.daftree.ui.AddTransactionActivity;

import java.text.NumberFormat;
import java.util.Locale;

public class AccountsAdapter extends ListAdapter<AccountWithBalance, AccountsAdapter.AccountViewHolder> {

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

    private void applyStoredAccountVisuals(Context context, ItemAccountBinding binding, Account account) {
        String iconKey = account.getIconKey();
        String colorHex = account.getColorHex();

        if (iconKey == null || iconKey.trim().isEmpty()) {
            iconKey = AccountTypeIconHelper.resolveIconKey(context, account.getAccountType());
        }
        if (colorHex == null || colorHex.trim().isEmpty()) {
            colorHex = AccountTypeIconHelper.resolveColorHex(context, account.getAccountType(), account.getAcTypeFirestoreId());
        }

        int badgeColor = AccountTypeIconHelper.parseColorSafe(colorHex, Color.parseColor(AccountTypeIconHelper.HEX_GENERAL));
        int iconRes = AccountTypeIconHelper.getIconDrawableRes(iconKey);

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

            applyStoredAccountVisuals(context, binding, accountWithBalance.account);

            if (accountWithBalance.balance > 0) {
                binding.arrowImageView.setImageResource(R.drawable.ic_arrow_down_red);
                binding.balanceTextView.setTextColor(ContextCompat.getColor(context, R.color.red));
            } else {
                binding.arrowImageView.setImageResource(R.drawable.ic_arrow_up_green);
                binding.balanceTextView.setTextColor(ContextCompat.getColor(context, R.color.green));
            }

            binding.editView.setOnClickListener(v -> {
                if (mListener != null) mListener.onEditAccountClicked(accountWithBalance.account);
            });

            itemView.setOnClickListener(v -> {
                if (mListener != null) mListener.onAccountClicked(accountWithBalance.account, currentCurrency);
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
