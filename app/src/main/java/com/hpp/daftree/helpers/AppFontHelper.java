package com.hpp.daftree.helpers;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import com.hpp.daftree.R;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
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
}
