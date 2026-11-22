package com.hpp.daftree.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.hpp.daftree.R;
import com.hpp.daftree.helpers.LanguageManager;
import com.hpp.daftree.helpers.WebTranslationHelper;
import com.hpp.daftree.models.LocalWebServer;

/**
 * مثال محدث لـ WebServerActivity مع دعم الترجمة والاتجاه
 */
public class WebServerActivity extends BaseActivity {

    private static final int PORT = 8080;
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private LocalWebServer server;
    private LanguageManager languageManager;
    private WebTranslationHelper translationHelper;

    private MaterialToolbar toolbar;
    private TextView tvStatus;
    private TextView tvInstructions;
    private TextView tvIpAddress;
    private Button btnStartStop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_server);
        LinearLayout llsMain = findViewById(R.id.llsMain);
        setupFloatingToolbar(llsMain);
        initializeComponents();
        setupListeners();
        updateUI();
    }

    /**
     * تهيئة المكونات
     */
    private void initializeComponents() {
        languageManager = LanguageManager.getInstance(this);
        translationHelper = WebTranslationHelper.getInstance(this);
        // تهيئة عناصر الواجهة
        tvStatus = findViewById(R.id.tv_status);
        tvInstructions = findViewById(R.id.tv_instructions);
        tvIpAddress = findViewById(R.id.tv_ip_address);
        btnStartStop = findViewById(R.id.btn_start_stop);

        // إعداد اللغة والاتجاه
        setupLanguageAndDirection();
    }

    /**
     * إعداد اللغة والاتجاه للواجهة
     */
    private void setupLanguageAndDirection() {
        WebTranslationHelper.WebLocaleInfo localeInfo = translationHelper.getWebLocaleInfo();

        // تطبيق الاتجاه على النشاط
        if (localeInfo.isRTL()) {
            getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        } else {
            getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        }

        // تحديث عنوان النشاط
        setTitle(translationHelper.getTranslation("financial_reports", getString(R.string.financial_reports)));
    }

    /**
     * إعداد مستمعي الأحداث
     */
    private void setupListeners() {
        // زر بدء/إيقاف الخادم
        btnStartStop.setOnClickListener(v -> toggleServer());

        // زر الرجوع
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        // نسخ عنوان IP عند النقر عليه
        tvIpAddress.setOnClickListener(v -> copyIpAddress());
    }

    /**
     * تشغيل الخادم
     */
    private void startServer() {
        if (!hasNetworkPermission()) {
            requestNetworkPermission();
            return;
        }

        try {
            server = new LocalWebServer(PORT, this);
            server.start();

            updateServerStatus(true);
            showToast(translationHelper.getTranslation("server_started", getString(R.string.server_started)));

        } catch (Exception e) {
            showToast(translationHelper.getTranslation("error_server_not_running", getString(R.string.error_server_not_running)));
        }
    }

    /**
     * إيقاف الخادم
     */
    private void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
            updateServerStatus(false);
            showToast(translationHelper.getTranslation("server_stopped", getString(R.string.server_was_stopped)));
        }
    }

    /**
     * تبديل حالة الخادم
     */
    private void toggleServer() {
        if (server != null) {
            stopServer();
        } else {
            startServer();
        }
    }

    /**
     * نسخ عنوان IP
     */
    private void copyIpAddress() {
        String ipAddress = tvIpAddress.getText().toString();

        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("IP Address", ipAddress);
        clipboard.setPrimaryClip(clip);

        showToast(translationHelper.getTranslation("copied", getString(R.string.copied)));
    }

    /**
     * تحديث حالة الخادم في الواجهة
     */
    private void updateServerStatus(boolean isRunning) {
        if (isRunning) {
            tvStatus.setText(translationHelper.getTranslation("server_online", getString(R.string.server_online)));
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
            btnStartStop.setText(translationHelper.getTranslation("stop_server", getString(R.string.stoping_server1)));

            // تحديث عنوان IP مع الرابط
            String serverUrl = "http://" + getLocalIpAddress() + ":" + PORT;
            tvIpAddress.setText(serverUrl);

        } else {
            tvStatus.setText(translationHelper.getTranslation("server_offline", getString(R.string.server_offline)));
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
            btnStartStop.setText(translationHelper.getTranslation("start_server", getString(R.string.start_server)));

            // عرض عنوان IP فقط
            tvIpAddress.setText(getLocalIpAddress() + ":" + PORT);
        }
    }

    /**
     * تحديث واجهة المستخدم
     */
    private void updateUI() {
        WebTranslationHelper.WebLocaleInfo localeInfo = translationHelper.getWebLocaleInfo();

        // تحديث عنوان شريط الأدوات
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(translationHelper.getTranslation("web_server_title", getString(R.string.web_server)));//
        }

        // تحديث النصوص حسب اللغة الحالية
        tvInstructions.setText(translationHelper.getTranslation("notification_procedure",getString(R.string.notification_procedures)));
        // تحديث عنوان IP
        tvIpAddress.setText(getLocalIpAddress() + ":" + PORT);

        // تطبيق الاتجاه على العناصر
        if (localeInfo.isRTL()) {
            tvStatus.setTextDirection(View.TEXT_DIRECTION_RTL);
            tvInstructions.setTextDirection(View.TEXT_DIRECTION_RTL);
            tvIpAddress.setTextDirection(View.TEXT_DIRECTION_LTR); // IP address should remain LTR
            btnStartStop.setTextDirection(View.TEXT_DIRECTION_RTL);
        } else {
            tvStatus.setTextDirection(View.TEXT_DIRECTION_LTR);
            tvInstructions.setTextDirection(View.TEXT_DIRECTION_LTR);
            tvIpAddress.setTextDirection(View.TEXT_DIRECTION_LTR);
            btnStartStop.setTextDirection(View.TEXT_DIRECTION_LTR);
        }

        // تحديث حالة الخادم
        updateServerStatus(server != null);
    }

    /**
     * التحقق من وجود إذن الشبكة
     */
    private boolean hasNetworkPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * طلب إذن الشبكة
     */
    private void requestNetworkPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.INTERNET},
                PERMISSION_REQUEST_CODE);
    }

    /**
     * الحصول على عنوان IP المحلي
     */
    private String getLocalIpAddress() {
        try {
            java.net.InetAddress localHost = java.net.InetAddress.getLocalHost();
            return localHost.getHostAddress();
        } catch (Exception e) {
            return "localhost";
        }
    }

    /**
     * عرض رسالة منبثقة
     */
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startServer();
            } else {
                showToast(translationHelper.getTranslation("permission_denied",
                        getString(R.string.permission_denieded)));

            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // تحديث الواجهة عند العودة للنشاط
        updateUI();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // تحديث عنوان IP عند بدء النشاط
        updateUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // إيقاف الخادم عند تدمير النشاط
        if (server != null) {
            server.stop();
        }
    }
}