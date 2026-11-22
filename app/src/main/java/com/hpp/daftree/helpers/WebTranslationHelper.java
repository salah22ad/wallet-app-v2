package com.hpp.daftree.helpers;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

import com.hpp.daftree.R;

import java.util.HashMap;
import java.util.Map;

/**
 * فئة مساعدة لإدارة الترجمة في الخادم المحلي
 * توفر ترجمة النصوص وخصائص الاتجاه للواجهات الويب
 */
public class WebTranslationHelper {

    private static WebTranslationHelper instance;
    private Context context;
    private Map<String, Map<String, String>> translationCache;
    private LanguageManager languageManager;

    private WebTranslationHelper(Context context) {
        this.context = context.getApplicationContext();
        this.languageManager = LanguageManager.getInstance(context);
        this.translationCache = new HashMap<>();
        loadAllTranslations();
    }

    public static synchronized WebTranslationHelper getInstance(Context context) {
        if (instance == null) {
            instance = new WebTranslationHelper(context);
        }
        return instance;
    }

    /**
     * تحميل جميع الترجمات إلى الذاكرة المؤقتة
     */
    private void loadAllTranslations() {
        String[] supportedLanguages = LanguageManager.SUPPORTED_LANGUAGES;

        for (String languageCode : supportedLanguages) {
            Map<String, String> translations = loadLanguageTranslations(languageCode);
            if (translations != null) {
                translationCache.put(languageCode, translations);
            }
        }
    }

    /**
     * تحميل ترجمات لغة معينة
     */
    private Map<String, String> loadLanguageTranslations(String languageCode) {
        Map<String, String> translations = new HashMap<>();

        try {
            Resources res = context.getResources();
            int resId = getStringResId(languageCode);

            if (resId == 0) {
                // استخدام الترجمة الإنجليزية كلغة افتراضية
                resId = R.string.currency_setup_message; // أي معرف موجود
                languageCode = "en";
            }

            // تحميل جميع النصوص المترجمة
            loadStringsFromXml(languageCode, translations);

        } catch (Exception e) {
            Log.e("WebTranslationHelper", "Error loading translations for: " + languageCode, e);
        }

        return translations;
    }

    /**
     * الحصول على معرف الموارد للغة
     */
    private int getStringResId(String languageCode) {
        return context.getResources().getIdentifier(
                "strings", "array", context.getPackageName()
        );
    }

    /**
     * تحميل النصوص من ملف strings.xml
     */
    private void loadStringsFromXml(String languageCode, Map<String, String> translations) {
        try {
            String fileName = "values-" + languageCode + "/strings.xml";
            if (languageCode.equals("en")) {
                fileName = "values/strings.xml";
            }

            Resources resources = context.getResources();
            int stringArrayId = getStringsArrayId(languageCode);

            if (stringArrayId != 0) {
                String[] keys = resources.getStringArray(stringArrayId);
                // هنا نحتاج لقراءة قيم النصوص من كل ملف strings.xml
                // هذا تبسيط - في التطبيق الحقيقي نحتاج لقراءة XML مباشرة
            }

        } catch (Exception e) {
            Log.e("WebTranslationHelper", "Error loading strings for: " + languageCode, e);
        }
    }

    private int getStringsArrayId(String languageCode) {
        // هذه طريقة مبسطة - في التطبيق الحقيقي نحتاج لقراءة ملف strings.xml
        // وإخراج جميع المفاتيح والقيم
        return 0;
    }

    /**
     * الحصول على نص مترجم
     */
    public String getTranslation(String key, String defaultValue) {
        String currentLanguage = languageManager.getCurrentLanguage();
        return getTranslationForLanguage(key, defaultValue, currentLanguage);
    }

    /**
     * الحصول على نص مترجم بلغة معينة
     */
    public String getTranslationForLanguage(String key, String defaultValue, String languageCode) {
        try {
            Map<String, String> translations = translationCache.get(languageCode);
            if (translations != null && translations.containsKey(key)) {
                return translations.get(key);
            }

            // محاولة الحصول على الترجمة من الموارد
            int resId = getStringResourceId(key);
            if (resId != 0) {
                Resources resources = LanguageManager.getLocalizedResources(context);
                return resources.getString(resId);
            }

        } catch (Exception e) {
            Log.e("WebTranslationHelper", "Error getting translation for: " + key, e);
        }

        return defaultValue;
    }

    /**
     * الحصول على معرف مورد النص
     */
    private int getStringResourceId(String key) {
        try {
            return context.getResources().getIdentifier(key, "string", context.getPackageName());
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * الحصول على خصائص الاتجاه والترجمة للواجهة
     */
    public WebLocaleInfo getWebLocaleInfo() {
        String languageCode = languageManager.getCurrentLanguage();
        boolean isRTL = languageManager.isRTL();

        return new WebLocaleInfo(
                languageCode,
                isRTL ? "rtl" : "ltr",
                isRTL ? "Cairo, Tahoma, Arial" : "Arial, Helvetica, sans-serif",
                isRTL ? "right" : "left",
                getTextAlignValue(isRTL),
                isRTL
        );
    }

    /**
     * الحصول على قيمة text-align حسب الاتجاه
     */
    private String getTextAlignValue(boolean isRTL) {
        return isRTL ? "right" : "left";
    }

    /**
     * فئة معلومات اللغة والاتجاه للواجهة الويب
     */
    public static class WebLocaleInfo {
        private final String languageCode;
        private final String direction;
        private final String fontFamily;
        private final String textAlign;
        private final boolean isRTL;

        public WebLocaleInfo(String languageCode, String direction, String fontFamily,
                             String textAlign, String textAlignValue, boolean isRTL) {
            this.languageCode = languageCode;
            this.direction = direction;
            this.fontFamily = fontFamily;
            this.textAlign = textAlign;
            this.isRTL = isRTL;
        }

        public String getLanguageCode() { return languageCode; }
        public String getDirection() { return direction; }
        public String getFontFamily() { return fontFamily; }
        public String getTextAlign() { return textAlign; }
        public boolean isRTL() { return isRTL; }

        public String getTextAlignForTables() {
            return "center"; // الجداول دائماً في المنتصف
        }

        public Map<String, String> getAllProperties() {
            Map<String, String> properties = new HashMap<>();
            properties.put("direction", direction);
            properties.put("font-family", fontFamily);
            properties.put("text-align", textAlign);
            properties.put("text-align-tables", getTextAlignForTables());
            properties.put("language", languageCode);
            return properties;
        }
    }

    /**
     * الحصول على ترجمة مع دعم القيم المتعددة
     */
    public String formatTranslation(String key, String defaultValue, Object... args) {
        String pattern = getTranslation(key, defaultValue);
        try {
            return String.format(pattern, args);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * تحديث الترجمات (في حالة تغيير اللغة)
     */
    public void refreshTranslations() {
        translationCache.clear();
        loadAllTranslations();
    }

    /**
     * الحصول على قائمة اللغات المدعومة
     */
    public String[] getSupportedLanguages() {
        return LanguageManager.SUPPORTED_LANGUAGES;
    }

    /**
     * الحصول على اسم اللغة المحلي
     */
    public String getLanguageName(String languageCode) {
        try {
            Resources resources = LanguageManager.getLocalizedResources(context);
            int resId = getStringResourceId("language_" + languageCode);
            if (resId != 0) {
                return resources.getString(resId);
            }
        } catch (Exception e) {
            Log.e("WebTranslationHelper", "Error getting language name for: " + languageCode, e);
        }

        return languageCode; // إرجاع رمز اللغة كافتراضي
    }
}