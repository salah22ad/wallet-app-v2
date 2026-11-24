package com.hpp.daftree.helpers;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.google.android.material.navigation.NavigationView;
import com.hpp.daftree.R;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AppFontHelper {

    private static final Set<String> CAIRO_SUPPORTED_LANGUAGES = new HashSet<>(Arrays.asList(
            "ar", "fa", "ur",
            "en", "de", "es", "fr", "tr", "no", "nb", "nn", "fi", "pt", "lt",
            "sv", "sw", "is", "it", "et", "cs", "hr", "lv", "hu", "sk", "sl",
            "ca", "zu", "pl", "da", "ro", "nl", "id", "ms"
    ));

    private static final Set<String> CJK_LANGUAGES = new HashSet<>(Arrays.asList(
            "zh", "ja", "ko"
    ));

    private static volatile Typeface cachedCairo;
    private static volatile Typeface cachedNotoSans;
    private static volatile Typeface cachedNotoCjk;
    private static volatile boolean cjkAssetChecked = false;

    private AppFontHelper() {}

    public static String normalizeLangCode(@Nullable String rawCode) {
        if (rawCode == null || rawCode.trim().isEmpty()) {
            return Locale.getDefault().getLanguage().toLowerCase(Locale.US);
        }
        String code = rawCode.trim().toLowerCase(Locale.US);
        if (code.contains("-")) code = code.split("-")[0];
        if (code.contains("_")) code = code.split("_")[0];
        return code;
    }

    public static boolean isCairoSupported(@Nullable String langCode) {
        return CAIRO_SUPPORTED_LANGUAGES.contains(normalizeLangCode(langCode));
    }

    public static void applyThemeFontOverlay(@NonNull Activity activity) {
        String lang = LanguageManager.getInstance(activity).getCurrentLanguage();
        if (isCairoSupported(lang)) {
            activity.getTheme().applyStyle(R.style.ThemeOverlay_Daftree_Font_Cairo, true);
        }
    }

    @NonNull
    public static Typeface getAppTypeface(@NonNull Context context) {
        String lang = normalizeLangCode(LanguageManager.getInstance(context).getCurrentLanguage());

        if (isCairoSupported(lang)) {
            if (cachedCairo == null) {
                try {
                    cachedCairo = ResourcesCompat.getFont(context, R.font.cairo_regular);
                } catch (Exception ignored) {
                }
            }
            if (cachedCairo != null) {
                return cachedCairo;
            }
        }

        if (CJK_LANGUAGES.contains(lang)) {
            if (!cjkAssetChecked) {
                cjkAssetChecked = true;
                try {
                    cachedNotoCjk = Typeface.createFromAsset(context.getAssets(), "NotoSansCJK-Regular.ttf");
                } catch (Exception ignored) {
                    cachedNotoCjk = null;
                }
            }
            if (cachedNotoCjk != null) {
                return cachedNotoCjk;
            }
        }

        if (cachedNotoSans == null) {
            try {
                cachedNotoSans = Typeface.createFromAsset(context.getAssets(), "NotoSans.ttf");
            } catch (Exception ignored) {
                cachedNotoSans = null;
            }
        }
        return cachedNotoSans != null ? cachedNotoSans : Typeface.SANS_SERIF;
    }

    public static void applyFontToDialog(@NonNull Context context, @Nullable Dialog dialog) {
        if (dialog == null || dialog.getWindow() == null) return;
        applyFontToViewTree(context, dialog.getWindow().getDecorView());
    }

    public static void applyFontToViewTree(@NonNull Context context, @Nullable View root) {
        if (root == null) return;
        Typeface baseTypeface = getAppTypeface(context);
        applyRecursive(root, baseTypeface);
    }

    private static void applyRecursive(@NonNull View view, @NonNull Typeface baseTypeface) {
        if ("exclude_global_font".equals(view.getTag())) {
            return;
        }
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            Typeface existing = tv.getTypeface();
            int style = existing != null ? existing.getStyle() : Typeface.NORMAL;
            tv.setTypeface(baseTypeface, style);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            int count = group.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = group.getChildAt(i);
                if (child != null) {
                    applyRecursive(child, baseTypeface);
                }
            }
        }
    }

    public static void styleNavigationView(@NonNull Context context, @Nullable NavigationView navView) {
        if (navView == null) return;
        Typeface typeface = getAppTypeface(context);

        if (navView.getHeaderCount() > 0) {
            applyFontToViewTree(context, navView.getHeaderView(0));
        }

        navView.setItemIconTintList(null);
        Map<Integer, Integer> iconColorMap = buildNavIconColors(context);
        styleMenuRecursive(navView.getMenu(), typeface, iconColorMap);
    }

    private static void styleMenuRecursive(@NonNull Menu menu,
                                           @NonNull Typeface typeface,
                                           @NonNull Map<Integer, Integer> iconColorMap) {
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item == null) continue;

            CharSequence rawTitle = item.getTitle();
            if (rawTitle != null && rawTitle.length() > 0) {
                SpannableString styled = new SpannableString(rawTitle.toString());
                styled.setSpan(new CustomTypefaceSpan(typeface), 0, styled.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                item.setTitle(styled);
            }

            Drawable icon = item.getIcon();
            if (icon != null) {
                Integer color = iconColorMap.get(item.getItemId());
                if (color != null) {
                    Drawable wrapped = DrawableCompat.wrap(icon.mutate());
                    DrawableCompat.setTint(wrapped, color);
                    item.setIcon(wrapped);
                }
            }

            if (item.hasSubMenu()) {
                SubMenu subMenu = item.getSubMenu();
                if (subMenu != null) {
                    styleMenuRecursive(subMenu, typeface, iconColorMap);
                }
            }
        }
    }

    private static Map<Integer, Integer> buildNavIconColors(@NonNull Context ctx) {
        Map<Integer, Integer> map = new HashMap<>();
        map.put(R.id.nav_login, ContextCompat.getColor(ctx, R.color.login_color));
        map.put(R.id.nav_new_bill, ContextCompat.getColor(ctx, R.color.nav_icon_bill));
        map.put(R.id.nav_reports, ContextCompat.getColor(ctx, R.color.nav_icon_reports));
        map.put(R.id.nav_recurring_operations, Color.parseColor("#0EA5E9"));
        map.put(R.id.nav_change_language, ContextCompat.getColor(ctx, R.color.nav_icon_language));
        map.put(R.id.nav_profile, ContextCompat.getColor(ctx, R.color.nav_icon_profile));
        map.put(R.id.nav_currencies, Color.parseColor("#F59E0B"));
        map.put(R.id.nav_account_types, ContextCompat.getColor(ctx, R.color.nav_icon_accounts));
        map.put(R.id.menu_linked_accounts, ContextCompat.getColor(ctx, R.color.budget_primary));
        map.put(R.id.nav_linked_accounts, ContextCompat.getColor(ctx, R.color.budget_primary));
        map.put(R.id.nav_linked_accounts_lock, ContextCompat.getColor(ctx, R.color.budget_primary));
        map.put(R.id.nav_password_lock, ContextCompat.getColor(ctx, R.color.nav_icon_security));
        map.put(R.id.nav_biometric_lock, ContextCompat.getColor(ctx, R.color.nav_icon_devices));
        map.put(R.id.nav_general_notifications, Color.parseColor("#F97316"));
        map.put(R.id.nav_daily_reminders, Color.parseColor("#06B6D4"));
        map.put(R.id.nav_daily_notification, Color.parseColor("#06B6D4"));
        map.put(R.id.nav_manage_devices, ContextCompat.getColor(ctx, R.color.nav_icon_devices));
        map.put(R.id.nav_logout, ContextCompat.getColor(ctx, R.color.nav_icon_logout));
        map.put(R.id.nav_purchase_app, Color.parseColor("#F59E0B"));
        map.put(R.id.nav_watch_ad, ContextCompat.getColor(ctx, R.color.nav_icon_ad));
        map.put(R.id.nav_invite_friend, ContextCompat.getColor(ctx, R.color.nav_icon_invite));
        map.put(R.id.nav_help, ContextCompat.getColor(ctx, R.color.nav_icon_help));
        map.put(R.id.nav_contact_support, ContextCompat.getColor(ctx, R.color.nav_icon_support));
        map.put(R.id.nav_restore_data, ContextCompat.getColor(ctx, R.color.nav_icon_restore));
        map.put(R.id.nav_terms_cond, ContextCompat.getColor(ctx, R.color.nav_icon_terms));
        map.put(R.id.nav_privacy_policy, ContextCompat.getColor(ctx, R.color.nav_icon_privacy));
        map.put(R.id.nav_rate_app, Color.parseColor("#EAB308"));
        map.put(R.id.nav_update_app, Color.parseColor("#0EA5E9"));
        map.put(R.id.nav_exit, Color.parseColor("#64748B"));
        map.put(R.id.menu_admin, ContextCompat.getColor(ctx, R.color.nav_icon_admin));
        map.put(R.id.nav_backup_data, ContextCompat.getColor(ctx, R.color.nav_icon_backup));
        return map;
    }

    private static final class CustomTypefaceSpan extends MetricAffectingSpan {
        private final Typeface typeface;

        CustomTypefaceSpan(@NonNull Typeface typeface) {
            this.typeface = typeface;
        }

        @Override
        public void updateDrawState(@NonNull TextPaint ds) {
            apply(ds);
        }

        @Override
        public void updateMeasureState(@NonNull TextPaint paint) {
            apply(paint);
        }

        private void apply(@NonNull Paint paint) {
            Typeface old = paint.getTypeface();
            int style = old != null ? old.getStyle() : Typeface.NORMAL;
            paint.setTypeface(Typeface.create(typeface, style));
        }
    }
}
