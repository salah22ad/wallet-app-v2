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

    private static final java.util.Map<String, Integer> COLOR_CACHE = new java.util.HashMap<>();
    private static final java.util.Map<String, Integer> ICON_CACHE = new java.util.HashMap<>();
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.US);

    public AccountsAdapter(OnAccountInteractionListener listener) {
        super(DIFF_CALLBACK);
        this.mListener = listener;
        this.currentCurrency = MyApplication.defaultCurrencyName;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        AccountWithBalance item = getItem(position);
        return (item != null && item.account != null) ? item.account.getId() : RecyclerView.NO_ID;
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
        if (iconKey == null || iconKey.trim().isEmpty()) {
            iconKey = AccountTypeIconHelper.resolveIconKey(context, account.getAccountType());
            account.setIconKey(iconKey);
        }
        String colorHex = account.getColorHex();
        if (colorHex == null || colorHex.trim().isEmpty()) {
            colorHex = AccountTypeIconHelper.resolveColorHex(context, account.getAccountType(), account.getAcTypeFirestoreId());
            account.setColorHex(colorHex);
        }

        Integer cachedColor = COLOR_CACHE.get(colorHex);
        if (cachedColor == null) {
            cachedColor = AccountTypeIconHelper.parseColorSafe(colorHex, Color.parseColor(AccountTypeIconHelper.HEX_GENERAL));
            COLOR_CACHE.put(colorHex, cachedColor);
        }

        Integer cachedIcon = ICON_CACHE.get(iconKey);
        if (cachedIcon == null) {
            cachedIcon = AccountTypeIconHelper.getIconDrawableRes(iconKey);
            ICON_CACHE.put(iconKey, cachedIcon);
        }

        binding.iconCard.setCardBackgroundColor(cachedColor);
        binding.accountColorBackground.setBackgroundColor(cachedColor);
        binding.accountIcon.setImageResource(cachedIcon);
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
            binding.balanceTextView.setText(NUMBER_FORMAT.format(Math.abs(accountWithBalance.balance)));
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
