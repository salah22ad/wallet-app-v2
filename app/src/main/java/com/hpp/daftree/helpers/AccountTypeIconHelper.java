package com.hpp.daftree.helpers;

import android.content.Context;
import android.graphics.Color;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hpp.daftree.R;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.AccountType;

import java.util.Locale;

public final class AccountTypeIconHelper {

    public static final String ICON_CUSTOMER = "customer";
    public static final String ICON_SUPPLIER = "supplier";
    public static final String ICON_GENERAL  = "general";
    public static final String ICON_CUSTOM   = "custom";

    public static final String HEX_CUSTOMER = "#0284C7";
    public static final String HEX_SUPPLIER = "#D97706";
    public static final String HEX_GENERAL  = "#0D6E6E";

    private static final String[] CUSTOM_PALETTE_HEX = new String[]{
            "#7C3AED", // Violet
            "#059669", // Emerald
            "#DB2777", // Pink
            "#EA580C", // Orange
            "#4F46E5", // Indigo
            "#0891B2", // Cyan
            "#E11D48", // Rose
            "#16A34A", // Green
            "#9333EA", // Purple
            "#2563EB", // Blue
            "#0F766E", // Deep Teal
            "#CA8A04", // Gold
            "#C026D3", // Fuchsia
            "#0369A1", // Ocean
            "#B45309", // Bronze
            "#6D28D9"  // Deep Purple
    };

    private AccountTypeIconHelper() {}

    @NonNull
    public static String resolveIconKey(@Nullable Context context, @Nullable String typeName) {
        String lower = typeName != null ? typeName.trim().toLowerCase(Locale.ROOT) : "";
        String customerStr = context != null ? context.getString(R.string.account_type_customer).trim().toLowerCase(Locale.ROOT) : "";
        String supplierStr = context != null ? context.getString(R.string.account_type_supplier).trim().toLowerCase(Locale.ROOT) : "";
        String generalStr  = context != null ? context.getString(R.string.account_type_general).trim().toLowerCase(Locale.ROOT) : "";

        if ((!customerStr.isEmpty() && lower.equals(customerStr))
                || lower.contains("عملاء") || lower.contains("عميل")
                || lower.contains("customer") || lower.contains("client")) {
            return ICON_CUSTOMER;
        }
        if ((!supplierStr.isEmpty() && lower.equals(supplierStr))
                || lower.contains("مورد")
                || lower.contains("supplier") || lower.contains("vendor")) {
            return ICON_SUPPLIER;
        }
        if ((!generalStr.isEmpty() && lower.equals(generalStr))
                || lower.equals("عام") || lower.equals("general")) {
            return ICON_GENERAL;
        }
        return ICON_CUSTOM;
    }

    @NonNull
    public static String resolveColorHex(@Nullable Context context, @Nullable String typeName, @Nullable String typeFirestoreId) {
        String iconKey = resolveIconKey(context, typeName);
        if (ICON_CUSTOMER.equals(iconKey)) return HEX_CUSTOMER;
        if (ICON_SUPPLIER.equals(iconKey)) return HEX_SUPPLIER;
        if (ICON_GENERAL.equals(iconKey))  return HEX_GENERAL;

        String seed = (typeFirestoreId != null && !typeFirestoreId.trim().isEmpty())
                ? typeFirestoreId.trim()
                : (typeName != null && !typeName.trim().isEmpty() ? typeName.trim().toLowerCase(Locale.ROOT) : "default");
        int hash = seed.hashCode() & 0x7fffffff;
        return CUSTOM_PALETTE_HEX[hash % CUSTOM_PALETTE_HEX.length];
    }

    public static void ensureTypeVisuals(@Nullable Context context, @Nullable AccountType type) {
        if (type == null) return;
        if (type.getIconKey() == null || type.getIconKey().trim().isEmpty()) {
            type.setIconKey(resolveIconKey(context, type.getName()));
        }
        if (type.getColorHex() == null || type.getColorHex().trim().isEmpty()) {
            type.setColorHex(resolveColorHex(context, type.getName(), type.getFirestoreId()));
        }
    }

    public static void applyTypeVisualsToAccount(@Nullable Context context, @Nullable Account account, @Nullable AccountType type) {
        if (account == null) return;
        if (type != null) {
            ensureTypeVisuals(context, type);
            account.setIconKey(type.getIconKey());
            account.setColorHex(type.getColorHex());
        } else {
            account.setIconKey(resolveIconKey(context, account.getAccountType()));
            account.setColorHex(resolveColorHex(context, account.getAccountType(), account.getAcTypeFirestoreId()));
        }
    }

    @DrawableRes
    public static int getIconDrawableRes(@Nullable String iconKey) {
        if (iconKey == null) return R.drawable.ic_modern_account_general;
        switch (iconKey) {
            case ICON_CUSTOMER: return R.drawable.ic_modern_account_customer;
            case ICON_SUPPLIER: return R.drawable.ic_modern_account_supplier;
            case ICON_CUSTOM:   return R.drawable.ic_modern_account_custom;
            case ICON_GENERAL:
            default:
                return R.drawable.ic_modern_account_general;
        }
    }

    public static int parseColorSafe(@Nullable String hex, int fallbackColor) {
        try {
            if (hex != null && !hex.trim().isEmpty()) {
                return Color.parseColor(hex.trim());
            }
        } catch (Exception ignored) {
        }
        return fallbackColor;
    }
}
