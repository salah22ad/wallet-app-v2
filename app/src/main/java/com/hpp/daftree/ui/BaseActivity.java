package com.hpp.daftree.ui;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.hpp.daftree.R;
import com.hpp.daftree.SplashActivity;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.hpp.daftree.helpers.AppFontHelper;
import com.hpp.daftree.helpers.LanguageManager;

import java.util.Locale;

public abstract class BaseActivity extends AppCompatActivity {

    public static boolean isRtl = true;
    private static final String TAG = "BaseActivity";
    private LanguageManager languageManager;
    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_LANGUAGE = "language";

    //@Override
    @Override
    protected void attachBaseContext(Context newBase) {
        if (this instanceof SplashActivity) {
            super.attachBaseContext(newBase);
            return;
        }

        SharedPreferences prefs = newBase.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String lang = prefs.getString(KEY_LANGUAGE, "en");
        Locale locale = Locale.forLanguageTag(lang);
        Locale.setDefault(locale);

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.setLocale(locale);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLayoutDirection(locale);
        }

        Context updatedContext = newBase.createConfigurationContext(config);

        // الحل الجذري لمشكلة الانهيار في أندرويد 15 وأجهزة Honor
        super.attachBaseContext(new android.content.ContextWrapper(updatedContext) {
            @Override
            public Object getSystemService(String name) {
                if (Context.INPUT_METHOD_SERVICE.equals(name)) {
                    // إجبار النظام على استخدام السياق الأصلي لخدمة لوحة المفاتيح
                    return newBase.getSystemService(name);
                }
                return super.getSystemService(name);
            }
        });
    }
    private void applyLayoutDirection() {
        String lang = languageManager.getCurrentLanguage();
        Locale locale = Locale.forLanguageTag(lang);
        int layoutDir = TextUtils.getLayoutDirectionFromLocale(locale);
        isRtl = (layoutDir == View.LAYOUT_DIRECTION_RTL);

        getWindow().getDecorView().setLayoutDirection(
                isRtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR
        );
    }

    // 🔥 الاحتفاظ بالدالة كما هي مع كتم التحذير الخاص بـ updateConfiguration فقط
    @SuppressWarnings("deprecation")
    private void applyFixedFontScale() {
        Configuration configuration = getResources().getConfiguration();
        if (configuration.fontScale != 0.90f) {
            configuration.fontScale = 0.90f;
            getResources().updateConfiguration(configuration, getResources().getDisplayMetrics());
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        // ضبط حجم الخط (مع كتم التحذير لهذا الجزء)
//        @SuppressWarnings("deprecation")
//        boolean fontScaleChanged = (newConfig.fontScale != 0.90f);
//        if (fontScaleChanged) {
//            newConfig.fontScale = 0.90f;
//            getResources().updateConfiguration(newConfig, getResources().getDisplayMetrics());
//        }

        super.onConfigurationChanged(newConfig);

        int currentNightMode = newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        switch (currentNightMode) {
            case Configuration.UI_MODE_NIGHT_NO:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case Configuration.UI_MODE_NIGHT_YES:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }

        applyLayoutDirection();
    }

    // الطريقة القديمة لتغيير اللغة (قد لا تُستخدم، لكننا نحدثها لتجنب التحذيرات)
    public static void applyLanguage(Context context, String languageCode) {
        Locale locale = Locale.forLanguageTag(languageCode);
        Locale.setDefault(locale);

        Resources resources = context.getResources();
        Configuration configuration = resources.getConfiguration();
        configuration.setLocale(locale);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            configuration.setLayoutDirection(locale);
        }

        // استخدم createConfigurationContext بدلاً من updateConfiguration
        context.createConfigurationContext(configuration);
        // إذا كنت بحاجة لتحديث فوري للنشاط، يفضل إعادة إنشائه (recreate())
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppFontHelper.applyThemeFontOverlay(this);
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        languageManager = LanguageManager.getInstance(this);
        applyLanguage(this);
        applyLayoutDirection();
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(
                new FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentViewCreated(@NonNull FragmentManager fm,
                                                      @NonNull Fragment f,
                                                      @NonNull View v,
                                                      @Nullable Bundle savedInstanceState) {
                        AppFontHelper.applyFontToViewTree(BaseActivity.this, v);
                    }
                }, true);
      //  applyFixedFontScale();   // ✅ الاحتفاظ بها
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        AppFontHelper.applyFontToViewTree(this, getWindow().getDecorView());
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        AppFontHelper.applyFontToViewTree(this, view);
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        super.setContentView(view, params);
        AppFontHelper.applyFontToViewTree(this, view);
    }

    public static void applyLanguage(Context context) {
        String lang = LanguageManager.getInstance(context).getCurrentLanguage();
        Locale locale = Locale.forLanguageTag(lang);
        Locale.setDefault(locale);

        Resources resources = context.getResources();
        Configuration configuration = resources.getConfiguration();
        configuration.setLocale(locale);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            configuration.setLayoutDirection(locale);
        }

        context.createConfigurationContext(configuration);  // ✅ بديل updateConfiguration
        isRtl = (TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL);
    }

    protected void setupFloatingToolbar(View contentContainer) {
        View mainRoot = findViewById(R.id.main_root);
        View floatingToolbar = findViewById(R.id.floating_toolbar_card);
        View btnBack = findViewById(R.id.btn_back);

        if (mainRoot == null || floatingToolbar == null) return;
        View spacer = findViewById(R.id.status_bar_spacer);
        if (spacer != null) {
            ViewCompat.setOnApplyWindowInsetsListener(spacer, (view, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                android.view.ViewGroup.LayoutParams params = view.getLayoutParams();
                if (params.height != bars.top) {
                    params.height = bars.top;
                    view.setLayoutParams(params);
                }
                return insets;
            });
        }
        if (contentContainer != null) {
            ViewCompat.setOnApplyWindowInsetsListener(contentContainer, (view, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), bars.bottom);
                return insets;
            });
        }
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        }
    }

    private void applyThemeBasedOnSystem() {
        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        switch (currentNightMode) {
            case Configuration.UI_MODE_NIGHT_NO:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case Configuration.UI_MODE_NIGHT_YES:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyThemeBasedOnSystem();
       // applyFixedFontScale();   // ✅ الاحتفاظ بها
    }
}