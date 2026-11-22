package com.hpp.daftree.helpers;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class LanguageHelperForReports {

    // تعريف مجموعات اللغات للمناطق المحددة
    private static final Set<String> CJK_LANGUAGES1 = new HashSet<>(Arrays.asList(
            "zh", "zh-CN", "zh-TW", "zh-HK", "zh-MO", "zh-SG", // صينية
            "ja", "ja-JP", // يابانية
            "ko", "ko-KR", "ko-KP" // كورية
    ));

    private static final Set<String> SOUTHEAST_ASIAN_LANGUAGES1 = new HashSet<>(Arrays.asList(
            "th", "th-TH", // تايلاندية
            "vi", "vi-VN", // فيتنامية
            "id", "id-ID", // إندونيسية
            "ms", "ms-MY", "ms-SG", "ms-BN", // ملايو
            "fil", "tl", "tl-PH", // فلبينية
            "my", "my-MM", // بورمية
            "km", "km-KH", // خميرية (كمبوديا)
            "lo", "lo-LA", // لاوية
            "th", "th-TH" // تايلاندية
    ));

    private static final Set<String> SOUTH_ASIAN_LANGUAGES1 = new HashSet<>(Arrays.asList(
            "hi", "hi-IN", // هندية
            "bn", "bn-BD", "bn-IN", // بنغالية
            "ta", "ta-IN", "ta-LK", "ta-SG", // تاميلية
            "te", "te-IN", // تيلوجو
            "mr", "mr-IN", // مراثية
            "ur", "ur-PK", "ur-IN", // أردية
            "gu", "gu-IN", // كجراتية
            "kn", "kn-IN", // كنادية
            "ml", "ml-IN", // مالايالامية
            "pa", "pa-IN", "pa-PK", // بنجابية
            "or", "or-IN", // أوديا
            "as", "as-IN", // آسامية
            "ne", "ne-NP", // نيبالية
            "si", "si-LK", // سنهالية
            "dv", "dv-MV" // ديفيهية (جزر المالديف)
    ));
    private static final Set<String> SOUTH_ASIAN_LANGUAGES = new HashSet<>(Arrays.asList(
            "hi", "hi-IN", // هندية (ديفاناغاري - NotoSans يدعمها جزئياً)
            "bn", "bn-BD", "bn-IN", // بنغالية (NotoSans يدعمها جزئياً)
            "ta", "ta-IN", "ta-LK", "ta-SG", // تاميلية (غير مدعومة)
            "te", "te-IN", // تيلوجو (غير مدعومة)
            "mr", "mr-IN", // مراثية (ديفاناغاري - NotoSans يدعمها جزئياً)
            "gu", "gu-IN", // كجراتية (غير مدعومة)
            "kn", "kn-IN", // كنادية (غير مدعومة)
            "ml", "ml-IN", // مالايالامية (غير مدعومة)
            "or", "or-IN", // أوديا (غير مدعومة)
            "as", "as-IN", // آسامية (ديفاناغاري - NotoSans يدعمها جزئياً)
            "pa", "pa-IN", "pa-PK", // بنجابية (غير مدعومة)
            "ne", "ne-NP", // نيبالية (ديفاناغاري - NotoSans يدعمها جزئياً)
            "si", "si-LK", // سنهالية (غير مدعومة)
            "dv", "dv-MV", // ديفيهية (غير مدعومة)
            "my", "my-MM", // بورمية (غير مدعومة)
            "km", "km-KH", // خميرية (غير مدعومة)
            "lo", "lo-LA", // لاوية (غير مدعومة)
            "bo", "bo-CN", "bo-IN", // تبتية (غير مدعومة)

            // لغات القوقاز وأوروبا الشرقية (غير مدعومة أو مدعومة جزئياً)
            "ka", "ka-GE", // جورجية (غير مدعومة)
            "hy", "hy-AM", // أرمينية (غير مدعومة)
            "az", "az-AZ", // أذربيجانية (لاتينية - NotoSans يدعمها)
            "be", "be-BY", // بيلاروسية (سيريلية - NotoSans يدعم السيريلية)
            "kk", "kk-KZ", // كازاخية (سيريلية - NotoSans يدعم السيريلية)
            "ky", "ky-KG", // قرغيزية (سيريلية - NotoSans يدعم السيريلية)
            "uz", "uz-UZ", // أوزبكية (لاتينية - NotoSans يدعمها)
            "tk", "tk-TM", // تركمانية (لاتينية - NotoSans يدعمها)
            "mn", "mn-MN", // منغولية (غير مدعومة)
            "sq", "sq-AL", "sq-XK", "sq-MK", // ألبانية (لاتينية - NotoSans يدعمها)
            "mk", "mk-MK", // مقدونية (سيريلية - NotoSans يدعم السيريلية)
            "bs", "bs-BA", // بوسنية (لاتينية - NotoSans يدعمها)

            // لغات أخرى غير مدعومة
            "am", "am-ET", // أمهرية (غير مدعومة)
            "ti", "ti-ET", "ti-ER", // تغرينية (غير مدعومة)
            "om", "om-ET", "om-KE", // أورومو (غير مدعومة)
            "so", "so-SO", // صومالية (لاتينية - NotoSans يدعمها)
            "sw", "sw-KE", "sw-TZ", // سواحيلية (لاتينية - NotoSans يدعمها)
            "yo", "yo-NG", // يوربا (لاتينية - NotoSans يدعمها)
            "ig", "ig-NG", // إيغبو (لاتينية - NotoSans يدعمها)
            "ha", "ha-NG", "ha-GH", // هاوسا (لاتينية - NotoSans يدعمها)
            "zu", "zu-ZA", // زولو (لاتينية - NotoSans يدعمها)
            "xh", "xh-ZA", // خوسا (لاتينية - NotoSans يدعمها)
            "af", "af-ZA", // أفريكانس (لاتينية - NotoSans يدعمها)
            "st", "st-LS", "st-ZA",
            // جنوب آسيا (باستثناء اللغات التي تستخدم الديفاناغاري المدعومة جزئياً)
            "ta", "ta-IN", "ta-LK", "ta-SG", // تاميلية
            "te", "te-IN", // تيلوجو
            "gu", "gu-IN", // كجراتية
            "kn", "kn-IN", // كنادية
            "ml", "ml-IN", // مالايالامية
            "or", "or-IN", // أوديا
            "pa", "pa-IN", "pa-PK", // بنجابية
            "si", "si-LK", // سنهالية
            "dv", "dv-MV", // ديفيهية
            "my", "my-MM", // بورمية
            "km", "km-KH", // خميرية
            "lo", "lo-LA", // لاوية
            "bo", "bo-CN", "bo-IN", // تبتية

            // لغات القوقاز
            "ka", "ka-GE", // جورجية
            "hy", "hy-AM", // أرمينية
            "mn", "mn-MN" // منغولية

    ));


    private static final Set<String> EASTERN_EUROPEAN_LANGUAGES1 = new HashSet<>(Arrays.asList(
            "ru", "ru-RU", "ru-UA", "ru-BY", "ru-KZ", // روسية
            "pl", "pl-PL", // بولندية
            "cs", "cs-CZ", // تشيكية
            "uk", "uk-UA", // أوكرانية
            "bg", "bg-BG", // بلغارية
            "ro", "ro-RO", "ro-MD", // رومانية
            "hu", "hu-HU", // مجرية
            "sk", "sk-SK", // سلوفاكية
            "hr", "hr-HR", // كرواتية
            "sr", "sr-RS", "sr-ME", // صربية
            "sl", "sl-SI", // سلوفينية
            "mk", "mk-MK", // مقدونية
            "bs", "bs-BA", // بوسنية
            "sq", "sq-AL", "sq-XK", "sq-MK", // ألبانية
            "lt", "lt-LT", // ليتوانية
            "lv", "lv-LV", // لاتفية
            "et", "et-EE", // إستونية
            "ka", "ka-GE", // جورجية
            "hy", "hy-AM", // أرمينية
            "az", "az-AZ", // أذربيجانية
            "be", "be-BY", // بيلاروسية
            "kk", "kk-KZ", // كازاخية
            "ky", "ky-KG", // قرغيزية
            "uz", "uz-UZ", // أوزبكية
            "tk", "tk-TM", // تركمانية
            "mn", "mn-MN" // منغولية
    ));

    /**
     * التحقق مما إذا كانت اللغة تنتمي لإحدى المناطق المحددة
     */
    public static boolean isLanguageInRestrictedArea(String languageCode) {
        if (languageCode == null) return false;

        // تحويل إلى صيغة قياسية (مثل: en-US -> en)
        String baseLanguage = languageCode.split("-")[0].toLowerCase();
        String fullLanguage = languageCode.toLowerCase();

//        return CJK_LANGUAGES.contains(fullLanguage) || CJK_LANGUAGES.contains(baseLanguage) ||
//                SOUTHEAST_ASIAN_LANGUAGES.contains(fullLanguage) || SOUTHEAST_ASIAN_LANGUAGES.contains(baseLanguage) ||
//                SOUTH_ASIAN_LANGUAGES.contains(fullLanguage) || SOUTH_ASIAN_LANGUAGES.contains(baseLanguage) ||
//                EASTERN_EUROPEAN_LANGUAGES.contains(fullLanguage) || EASTERN_EUROPEAN_LANGUAGES.contains(baseLanguage);
        return  SOUTH_ASIAN_LANGUAGES.contains(fullLanguage) || SOUTH_ASIAN_LANGUAGES.contains(baseLanguage);
    }


    /**
     * الحصول على كائن Locale مع اللغة المناسبة
     */
    public static Locale getAppropriateLocale(String language) {
        if (isLanguageInRestrictedArea(language)) {
            return Locale.ENGLISH;
        } else {
            return Locale.forLanguageTag(language);
        }
    }

    /**
     * الحصول على سياق محلي مناسب
     */
    public static Context createLocalizedContext(Context context, String language) {
        Locale locale;
        if (isLanguageInRestrictedArea(language)) {
            locale = Locale.ENGLISH;
        } else {
            locale = new Locale.Builder().setLanguage(language).build();
        }

        Configuration configuration = context.getResources().getConfiguration();
        configuration.setLocale(locale);
        configuration.setLayoutDirection(locale);

        return context.createConfigurationContext(configuration);
    }

    // ثم عدل getAppropriateResources لتعيد Resources من الكونتكست الجديد:
    public static Resources getAppropriateResources(Context context, String language) {
        return createLocalizedContext(context, language).getResources();
    }
}