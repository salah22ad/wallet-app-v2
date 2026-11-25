package com.hpp.daftree.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.hpp.daftree.R;
import com.hpp.daftree.models.LanguageItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class LanguageManager {

    private static LanguageManager instance;
    private final Context context;
    private final SharedPreferences prefs;

    private static final String PREF_NAME = "AppPrefs";
    private static final String KEY_LANGUAGE = "language";

    public static final String[] SUPPORTED_LANGUAGES1 = {
            "ar", // العربية
            "en", // English
            "de", // Deutsch
            "es", // Español
            "fr", // Français
            "ru", // Русский
            "tr" };
          /*  , // Türkçe
            "hi", // Hindi
            "no", // Norsk
            "fi", // Suomi
            "pt", // Português
            "bg", // Български
            "lt", // Lietuvių
            "kn", // Kannada (كانادا)
            "sv", // Svenska
            "ko", // 한국어
            "sw", // Kiswahili
            "mr", // Marathi
            "el", // Ελληνικά
            "is", // Íslenska
            "it", // Italiano
            "ta", // Tamil
            "gu", // Gujarati
            "zh", // 中文
            "et", // Eesti
            "cs", // Čeština
            "pa", // Punjabi
            "te", // Telugu
            "vi", // Tiếng Việt
            "th", // Thai
            "ja", // 日本語
            "fa", // فارسی
            "hr", // Hrvatski
            "lv", // Latviešu
            "bn", // বাংলা
            "hu", // Magyar
            "uk", // Українська
            "sk", // Slovenčina
            "sl", // Slovenščina
            "ur", // اردو
            "ca", // Català
            "ml", // Malayalam
            "zu", // IsiZulu
            "pl", // Polski
            "da", // Dansk
            "ro", // Română
            "nl"  // Nederlands
    };*/
          public static final String[] SUPPORTED_LANGUAGES = {
                  "ar", // العربية
                  "en", // English
                  "de", // Deutsch
                  "es", // Español
                  "fr", // Français
                  "ru", // Русский
                  "tr", // Türkçe
                  "hi", // Hindi
                  "no", // Norsk
                  "fi", // Suomi
                  "pt", // Português
                  "bg", // Български
                  "lt", // Lietuvių
                  "kn", // Kannada (كانادا)
                  "sv", // Svenska
                  "ko", // 한국어
                  "sw", // Kiswahili
                  "mr", // Marathi
                  "el", // Ελληνικά
                  "is", // Íslenska
                  "it", // Italiano
                  "ta", // Tamil
                  "gu", // Gujarati
                  "zh", // 中文
                  "et", // Eesti
                  "cs", // Čeština
                  "pa", // Punjabi
                  "te", // Telugu
                  "vi", // Tiếng Việt
                  "th", // Thai
                  "ja", // 日本語
                  "fa", // فارسی
                  "hr", // Hrvatski
                  "lv", // Latviešu
                  "bn", // বাংলা
                  "hu", // Magyar
                  "uk", // Українська
                  "sk", // Slovenčina
                  "sl", // Slovenščina
                  "ur", // اردو
                  "ca", // Català
                  "ml", // Malayalam
                  "zu", // IsiZulu
                  "pl", // Polski
                  "da", // Dansk
                  "ro", // Română
                  "nl"  // Nederlands
          };

// نص للتحقق من وجود الترجمة (تأكد أن هذا الـ ID موجود في strings.xml)
private static final int VALIDATION_STRING_ID = R.string.currency_setup_message;

private LanguageManager(Context context) {
  this.context = context.getApplicationContext();
  this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
}

public static synchronized LanguageManager getInstance(Context context) {
  if (instance == null) {
      instance = new LanguageManager(context);
  }
  return instance;
}

public void setLocale(String languageCode) {
  prefs.edit().putString(KEY_LANGUAGE, languageCode).apply();
  updateResources(context, languageCode);
}

public String getCurrentLanguage() {
  return prefs.getString(KEY_LANGUAGE, getSystemLanguage());
}

    public static void applyLocale(Context context, String lang) {
        // الطريقة القديمة لتحديث الموارد العامة (لم تعد منصوحاً بها ولكن لإصلاح الخطأ):
        Resources resources = context.getResources();
        Configuration config = resources.getConfiguration();

        Locale locale = new Locale.Builder().setLanguage(lang).build();
        Locale.setDefault(locale);

        config.setLocale(locale);
        config.setLayoutDirection(locale);

        // استخدام createConfigurationContext هو البديل، ولكن بما أن الدالة void
        // سنقوم فقط بتحديث السياق الممرر إذا كان Activity
        context.createConfigurationContext(config);
    }

/**
* ✅ دالة التوافق القديمة 2: فحص الـ RTL مع تمرير Context (إذا كانت موجودة سابقاً)
*/
    public static boolean isRTL(Context context) {
        return getInstance(context).isRTL();
    }
    /**
     * ✅ دالة التوافق القديمة 1: فحص الـ RTL بناءً على اللغة الحالية المحفوظة
     * تستخدمها التقارير والشاشات القديمة
     */
    public boolean isRTL() {
        String lang = getCurrentLanguage();
        return isCodeRTL(lang);
    }
    /**
     * دالة مساعدة لفحص اتجاه أي كود لغة باستخدام مكتبة أندرويد الرسمية
     */
    public boolean isCodeRTL(String languageCode) {
        Locale locale = new Locale.Builder().setLanguage(languageCode).build();
        return TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL;
    }

    public String getSystemLanguage() {
        String systemLanguage = Locale.getDefault().getLanguage();
        if (isLanguageSupported(systemLanguage)) {
            return systemLanguage;
        }
        return "en";
    }

    public boolean isLanguageSupported(String languageCode) {
        if (languageCode == null) return false;
        List<LanguageItem> supported = getSupportedLanguageItems();
        for (LanguageItem item : supported) {
            if (item.getCode().equals(languageCode)) {
                return true;
            }
        }
        return false;
    }
    public List<LanguageItem> getSupportedLanguageItems() {
        List<LanguageItem> list = new ArrayList<>();

        // 1. جلب اللغة الحالية مرة واحدة خارج الحلقة لتحسين الأداء
        String currentAppLang = prefs.getString(KEY_LANGUAGE, "en");
        Locale appLocale = Locale.forLanguageTag(currentAppLang);

        for (String languageCode : SUPPORTED_LANGUAGES) {
            try {
                // استخدام Locale.forLanguageTag لضمان التوافق
                Locale locale = Locale.forLanguageTag(languageCode);

                // 2. الاسم بلغة التطبيق الحالية (مع إضافة حماية)
                String nameByAppLang = locale.getDisplayName(appLocale);
                if (nameByAppLang == null || nameByAppLang.isEmpty()) {
                    nameByAppLang = languageCode; // قيمة احتياطية في حال فشل النظام
                }

                // 3. الاسم الأصلي (Native Name)
                String nativeName = locale.getDisplayName(locale);

                // حماية شديدة من NullPointerException ومن خطأ Substring
                if (nativeName != null && !nativeName.isEmpty()) {
                    // تكبير أول حرف بطريقة آمنة
                    nativeName = nativeName.substring(0, 1).toUpperCase(locale) +
                            (nativeName.length() > 1 ? nativeName.substring(1) : "");
                } else {
                    nativeName = nameByAppLang; // استخدام الاسم المتوفر إذا كان الأصلي فارغاً
                }

                // العلم والاتجاه
                String flag = getFlagForLanguage(languageCode);
                boolean isRtl = isCodeRTL(languageCode);

                list.add(new LanguageItem(languageCode, nameByAppLang, nativeName, flag, isRtl));

            } catch (Exception e) {
                // في حال حدوث أي خطأ Native أو JNI، نقوم بإضافة مادة أساسية لمنع الانهيار الكامل
                Log.e("LanguageManager", "Error processing language: " + languageCode, e);
                list.add(new LanguageItem(languageCode, languageCode, languageCode, "🌐", false));
            }
        }

        return list;
    }


    private void updateResources(Context context, String language) {
        Locale locale = Locale.forLanguageTag(language);
        Locale.setDefault(locale);
        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale);
            LocaleList localeList = new LocaleList(locale);
            LocaleList.setDefault(localeList);
            config.setLocales(localeList);
        } else {
            config.setLocale(locale);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLayoutDirection(locale);
        }

        res.updateConfiguration(config, res.getDisplayMetrics());
    }
    /**
     * الدالة الرئيسية للحصول على العلم
     * @param languageCode: رمز اللغة الكامل (مثلاً "ar", "en-US", "es_MX")
     * @return إيموجي العلم
     */
    public static String getFlagForLanguage(String languageCode) {
        switch (languageCode) {

            case "ar": return "🇾🇪"; // Yemen (حسب طلبك السابق)
            case "en": return "🇺🇸"; // USA
            case "de": return "🇩🇪"; // Germany
            case "es": return "🇪🇸"; // Spain
            case "fr": return "🇫🇷"; // France
            case "ru": return "🇷🇺"; // Russia
            case "tr": return "🇹🇷"; // Turkey
            case "hi": return "🇮🇳"; // India
            case "no": return "🇳🇴"; // Norway
            case "fi": return "🇫🇮"; // Finland
            case "pt": return "🇵🇹"; // Portugal
            case "bg": return "🇧🇬"; // Bulgaria
            case "lt": return "🇱🇹"; // Lithuania
            case "kn": return "🇮🇳"; // India (Kannada)
            case "sv": return "🇸🇪"; // Sweden
            case "ko": return "🇰🇷"; // South Korea
            case "sw": return "🇰🇪"; // Kenya (Swahili) - or TZ/Global
            case "mr": return "🇮🇳"; // India (Marathi)
            case "el": return "🇬🇷"; // Greece
            case "is": return "🇮🇸"; // Iceland
            case "it": return "🇮🇹"; // Italy
            case "ta": return "🇮🇳"; // India (Tamil)
            case "gu": return "🇮🇳"; // India (Gujarati)
            case "zh": return "🇨🇳"; // China
            case "et": return "🇪🇪"; // Estonia
            case "cs": return "🇨🇿"; // Czech Republic
            case "pa": return "🇮🇳"; // India (Punjabi)
            case "te": return "🇮🇳"; // India (Telugu)
            case "vi": return "🇻🇳"; // Vietnam
            case "th": return "🇹🇭"; // Thailand
            case "ja": return "🇯🇵"; // Japan
            case "fa": return "🇮🇷"; // Iran
            case "hr": return "🇭🇷"; // Croatia
            case "lv": return "🇱🇻"; // Latvia
            case "bn": return "🇧🇩"; // Bangladesh
            case "hu": return "🇭🇺"; // Hungary
            case "uk": return "🇺🇦"; // Ukraine
            case "sk": return "🇸🇰"; // Slovakia
            case "sl": return "🇸🇮"; // Slovenia
            case "ur": return "🇵🇰"; // Pakistan
            case "ca": return "🇪🇸"; // Spain (Catalonia)
            case "ml": return "🇮🇳"; // India (Malayalam)
            case "zu": return "🇿🇦"; // South Africa
            case "pl": return "🇵🇱"; // Poland
            case "da": return "🇩🇰"; // Denmark
            case "ro": return "🇷🇴"; // Romania
            case "nl": return "🇳🇱"; // Netherlands
            default: return "🌐";
        }
    }
    public static Resources getLocalizedResources(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String languageCode = prefs.getString(KEY_LANGUAGE, "en");

        Locale locale = new Locale.Builder().setLanguage(languageCode).build();
        Locale.setDefault(locale);

        Configuration configuration = context.getResources().getConfiguration();
        configuration.setLocale(locale);
        configuration.setLayoutDirection(locale);

        return context.createConfigurationContext(configuration).getResources();
    }
    public String[] getSupportedLanguagesArray() {
        List<LanguageItem> supported = getSupportedLanguageItems();
        String[] languageCodes = new String[supported.size()];
        for (int i = 0; i < supported.size(); i++) {
            languageCodes[i] = supported.get(i).getCode();
        }
        return languageCodes;
    }
}