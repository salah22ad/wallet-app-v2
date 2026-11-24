package com.hpp.daftree.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.hpp.daftree.R;
import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.helpers.AccountTypeIconHelper;
import com.hpp.daftree.helpers.AppFontHelper;

import java.util.List;

public class AccountsTypeAdapter extends RecyclerView.Adapter<AccountsTypeAdapter.AccountsTypeViewHolder> {

    private List<AccountType> accountsType;
    private final OnItemInteractionListener mListener;

    public interface OnItemInteractionListener {
        void onAccountTypeLongClicked(AccountType accountsType);
        void onEditeAccountType(AccountType accountsType);
        void onDeleteAccountType(AccountType accountsType);
    }

    public AccountsTypeAdapter(List<AccountType> accountsType, OnItemInteractionListener listener) {
        this.accountsType = accountsType;
        this.mListener = listener;
    }

    @NonNull
    @Override
    public AccountsTypeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_account_type, parent, false);
        AppFontHelper.applyFontToViewTree(parent.getContext(), view);
        return new AccountsTypeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AccountsTypeViewHolder holder, int position) {
        AccountType item = accountsType.get(position);
        Context context = holder.itemView.getContext();

        AccountTypeIconHelper.ensureTypeVisuals(context, item);
        int badgeColor = AccountTypeIconHelper.parseColorSafe(item.getColorHex(), Color.parseColor(AccountTypeIconHelper.HEX_GENERAL));
        int iconRes = AccountTypeIconHelper.getIconDrawableRes(item.getIconKey());

        if (holder.iconCard != null) {
            holder.iconCard.setCardBackgroundColor(badgeColor);
        }
        if (holder.iconImage != null) {
            holder.iconImage.setImageResource(iconRes);
        }

        if (item.isDefault()) {
            holder.nameTextView.setText(getTranslatedDefaultName(item.name, context));
            holder.defaultLabel.setVisibility(View.VISIBLE);
            holder.deleteButton.setVisibility(View.GONE);
        } else {
            holder.defaultLabel.setVisibility(View.GONE);
            holder.deleteButton.setVisibility(View.VISIBLE);
            holder.nameTextView.setText(item.name);
        }

        holder.editButton.setOnClickListener(v -> {
            if (mListener != null) mListener.onEditeAccountType(item);
        });
        holder.deleteButton.setOnClickListener(v -> {
            if (mListener != null) mListener.onDeleteAccountType(item);
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (mListener != null) mListener.onAccountTypeLongClicked(item);
            return true;
        });
    }

    private String getTranslatedDefaultName(String originalName, Context context) {
        if (originalName == null) return "";
        switch (originalName) {
            case "عملاء":
                return context.getString(R.string.account_type_customer);
            case "موردين":
                return context.getString(R.string.account_type_supplier);
            case "عام":
                return context.getString(R.string.account_type_general);
            default:
                return originalName;
        }
    }

    @Override
    public int getItemCount() {
        return accountsType != null ? accountsType.size() : 0;
    }

    public void setAccountsType(List<AccountType> accountsType) {
        this.accountsType = accountsType;
        notifyDataSetChanged();
    }

    public List<AccountType> getAccountTypes(List<AccountType> accountsType) {
        this.accountsType = accountsType;
        return accountsType;
    }

    static class AccountsTypeViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView iconCard;
        ImageView iconImage;
        TextView nameTextView;
        TextView defaultLabel;
        ImageView editButton;
        ImageView deleteButton;

        AccountsTypeViewHolder(@NonNull View itemView) {
            super(itemView);
            iconCard = itemView.findViewById(R.id.icon_type_card);
            iconImage = itemView.findViewById(R.id.icon_quantity_type);
            nameTextView = itemView.findViewById(R.id.text1);
            defaultLabel = itemView.findViewById(R.id.text_default_label);
            editButton = itemView.findViewById(R.id.btn_edit);
            deleteButton = itemView.findViewById(R.id.btn_delete);
        }
    }
}
