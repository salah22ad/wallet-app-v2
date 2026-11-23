package com.hpp.daftree.budget.util;

import android.content.Context;
import android.graphics.Color;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.hpp.daftree.R;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class BudgetUtils {

    private BudgetUtils() {}

    public static String getCurrentYearMonth() {
        Calendar cal = Calendar.getInstance();
        return String.format(Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1);
    }

    public static String toYearMonth(long epochMillis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(epochMillis);
        return String.format(Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1);
    }

    public static String shiftYearMonth(String yearMonth, int deltaMonths) {
        Calendar cal = parseYearMonth(yearMonth);
        cal.add(Calendar.MONTH, deltaMonths);
        return String.format(Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1);
    }

    public static boolean isAtOrAfterCurrentMonth(String yearMonth) {
        if (yearMonth == null) return true;
        return yearMonth.compareTo(getCurrentYearMonth()) >= 0;
    }

    public static long getEndOfCurrentMonthMillis() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTimeInMillis();
    }

    public static List<String> getLastSixMonths(String endYearMonth) {
        List<String> months = new ArrayList<>(6);
        for (int i = -5; i <= 0; i++) {
            months.add(shiftYearMonth(endYearMonth, i));
        }
        return months;
    }

    public static Calendar parseYearMonth(String yearMonth) {
        Calendar cal = Calendar.getInstance();
        try {
            if (yearMonth != null && yearMonth.length() >= 7) {
                int y = Integer.parseInt(yearMonth.substring(0, 4));
                int m = Integer.parseInt(yearMonth.substring(5, 7)) - 1;
                cal.set(Calendar.YEAR, y);
                cal.set(Calendar.MONTH, m);
                cal.set(Calendar.DAY_OF_MONTH, 1);
            }
        } catch (Exception ignored) {
        }
        return cal;
    }

    public static Locale getActiveLocale(Context context) {
        if (context != null) {
            Locale loc = context.getResources().getConfiguration().getLocales().get(0);
            if (loc != null) {
                return loc;
            }
        }
        return Locale.getDefault();
    }

    public static String formatYearMonthDisplay(String yearMonth, Context context) {
        Calendar cal = parseYearMonth(yearMonth);
        Locale locale = getActiveLocale(context);
        Date date = cal.getTime();
        String monthName = new SimpleDateFormat("LLLL", locale).format(date);
        if (monthName.matches("\\d+")) {
            monthName = new SimpleDateFormat("MMMM", locale).format(date);
        }
        return monthName + " " + String.format(Locale.US, "%d", cal.get(Calendar.YEAR));
    }

    public static String formatMonthShortLabel(String yearMonth, Context context) {
        Calendar cal = parseYearMonth(yearMonth);
        Locale locale = getActiveLocale(context);
        Date date = cal.getTime();
        String label = new SimpleDateFormat("LLL", locale).format(date);
        if (label.matches("\\d+")) {
            label = new SimpleDateFormat("MMM", locale).format(date);
        }
        return label;
    }

    public static String formatDateIso(long epochMillis) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return sdf.format(new Date(epochMillis));
    }

    public static String formatAmount(double value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }

    public static String formatAmountWithCurrency(double value, String currencyLabel) {
        String num = formatAmount(Math.abs(value));
        if (currencyLabel == null || currencyLabel.trim().isEmpty()) {
            return num;
        }
        return num + " " + currencyLabel.trim();
    }

    @StringRes
    public static int getDefaultCategoryNameRes(String code) {
        if (code == null) return 0;
        switch (code) {
            case "bills": return R.string.budget_cat_bills;
            case "housing": return R.string.budget_cat_housing;
            case "food": return R.string.budget_cat_food;
            case "transport": return R.string.budget_cat_transport;
            case "health": return R.string.budget_cat_health;
            case "other": return R.string.budget_cat_other;
            case "salary": return R.string.budget_cat_salary;
            case "freelance":
            case "network":
                return R.string.budget_cat_freelance;
            case "investments":
            case "apps":
                return R.string.budget_cat_investments;
            case "income_other": return R.string.budget_cat_income_other;
            default: return 0;
        }
    }

    public static String getCategoryDisplayName(android.content.res.Resources res, String code, String customName) {
        if (customName != null && !customName.trim().isEmpty()) {
            return customName.trim();
        }
        int resId = getDefaultCategoryNameRes(code);
        if (res != null && resId != 0) {
            try {
                return res.getString(resId);
            } catch (Exception ignored) {
            }
        }
        return code != null ? code : "";
    }

    public static String getCategoryDisplayName(Context context, String code, String customName, boolean isDefault) {
        if (customName != null && !customName.trim().isEmpty()) {
            return customName.trim();
        }
        int resId = getDefaultCategoryNameRes(code);
        if (context != null && resId != 0) {
            return context.getString(resId);
        }
        return code != null ? code : "";
    }

    public static int parseColorSafe(String hex, int fallbackColor) {
        try {
            if (hex != null && !hex.isEmpty()) {
                return Color.parseColor(hex);
            }
        } catch (Exception ignored) {
        }
        return fallbackColor;
    }

    @DrawableRes
    public static int getIconRes(String iconKey) {
        if (iconKey == null) return R.drawable.ic_budget_other;
        switch (iconKey) {
            case "bills": return R.drawable.ic_budget_bills;
            case "housing": return R.drawable.ic_budget_housing;
            case "food": return R.drawable.ic_budget_food;
            case "transport": return R.drawable.ic_budget_transport;
            case "health": return R.drawable.ic_budget_health;
            case "salary": return R.drawable.ic_budget_salary;
            case "freelance":
            case "network":
                return R.drawable.ic_budget_freelance;
            case "investments":
            case "apps":
                return R.drawable.ic_budget_investments;
            case "income_other": return R.drawable.ic_budget_income_other;
            case "other":
            default:
                return R.drawable.ic_budget_other;
        }
    }
}
