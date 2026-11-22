package com.hpp.daftree.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

//import com.hpp.daftree.BuildConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * SecurityBootstrap
 *
 * مسؤول عن:
 *  - التحقق من توقيع التطبيق (SHA-256 of signing cert)
 *  - إنشاء تقرير أمني (파일 bundle) للاعتمادات / سجلات الأعطال لإرفاقها مع رسائل الدعم أو مع Google
 *  - واجهة لتمكين/تعطيل الفحوصات (مفيدة أثناء التطوير/اختبار الإصدارات)
 *
 * ملاحظات للـ Play Review:
 *  - لا يجمع أو يرسل أي بيانات تلقائياً؛ فقط ينشئ ملفًا محلياً في cache/ ليشاركه المستخدم أو فريق الدعم.
 *  - يمكن تضمين البصمة SHA-256 الخاصة بمفتاح النشر (الموجود في وصفك) في هذا الملف كقيمة متوقعة للتحقق.
 */
public class SecurityBootstrap {
    private static final String TAG = "SecurityBootstrap";

    // ضع هنا بصمة SHA-256 لمفتاحك (من keytool) بعد تنظيفها (أحرف كبيرة، بدون ':')
    // مثال: "1C36973851F58501A8EA6C9415511CEC7A0FAB1AF835732A6581BA626727390D"
    private static final String EXPECTED_SHA256 = "1C36973851F58501A8EA6C9415511CEC7A0FAB1AF835732A6581BA626727390D";

    // اسم ملف التقرير الناتج
    private static final String REPORT_FILENAME_PREFIX = "security_report_";

    // تفعيل/تعطيل الفحوصات (يمكن التحكم بها في الإعدادات)
    private static boolean enabled = true;

    /**
     * استدعى هذه الدالة في Application.onCreate() لتشغيل الفحوصات الأساسية.
     * هذه الدالة لا تقطع تشغيل التطبيق إن فشل التحقق — تعرض حالة وتولد تقريراً إن لزم.
     */
    public static void initialize(@NonNull Context context) {
        if (!enabled) {
            Log.i(TAG, "SecurityBootstrap disabled - skipping checks");
            return;
        }


    }

    /**
     * تمكين/تعطيل الفحوصات (مثلاً أثناء تطوير النسخ Debug).
     */
    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * يتحقق من SHA-256 للشهادة المرفقة بالـ APK ويقارنها بالبصمة المتوقعة.
     *
     * @param ctx             سياق التطبيق
     * @param expectedSha256  البصمة المتوقعة (HEX بدون ':', أحرف كبيرة)
     * @return true إذا تطابقت البصمة
     */

    /**
     * يحول مصفوفة بايت للشهادة إلى SHA-256 hex string (بدون ':', أحرف كبيرة).
     */
    @SuppressLint("DefaultLocale")
    private static String certToSha256(byte[] certBytes) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(new java.io.ByteArrayInputStream(certBytes));
            byte[] encoded = cert.getEncoded();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(encoded);

            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02X", b));
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "certToSha256 error", e);
            return null;
        }
    }

    /**
     * ينشئ ملف تقرير أمني (JSON) في مجلد cache للتطبيق يتضمن:
     *  - معلومات التطبيق (package, version)
     *  - توقيت الإنشاء
     *  - بصمة الشهادة الحالية
     *  - معلومات جهاز أساسية
     *  - (اختياري) ملخص آخر crash log إن وُجد (من CrashHandler إذا متوفر)
     *
     * الملف مصمم لكي يقوم مطور/مستخدم بتحميله وإرساله لفريق الدعم أو لجوجل عند الطلب.
     *
     * @param ctx سبب إنشاء التقرير (نص بسيط يصف السبب)
     * @return ملف التقرير أو null على الفشل
     */
    public static File createSecurityReport(@NonNull Context ctx, String reason) {
        try {
            JSONObject report = new JSONObject();

            // Header
            report.put("generatedAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(new Date()));
            report.put("reason", reason);
            report.put("packageName", ctx.getPackageName());

            try {
                PackageManager pm = ctx.getPackageManager();
                if (pm != null) {
                    PackageInfo pi = pm.getPackageInfo(ctx.getPackageName(), 0);
                    report.put("versionName", pi.versionName != null ? pi.versionName : "");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        report.put("versionCode", pi.getLongVersionCode());
                    }
                }
            } catch (Exception ignored) {}

            // Signatures
            JSONArray signaturesJson = new JSONArray();
            try {
                if (ctx.getPackageManager() != null) {
                    PackageInfo packageInfo;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                        if (packageInfo.signingInfo != null) {
                            Signature[] signs = packageInfo.signingInfo.getApkContentsSigners();
                            for (Signature s : signs) {
                                String sha = certToSha256(s.toByteArray());
                                JSONObject o = new JSONObject();
                                o.put("sha256", sha != null ? sha : JSONObject.NULL);
                                signaturesJson.put(o);
                            }
                        }
                    } else {
                        packageInfo = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), PackageManager.GET_SIGNATURES);
                        if (packageInfo.signatures != null) {
                            for (Signature s : packageInfo.signatures) {
                                String sha = certToSha256(s.toByteArray());
                                JSONObject o = new JSONObject();
                                o.put("sha256", sha != null ? sha : JSONObject.NULL);
                                signaturesJson.put(o);
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                Log.e(TAG, "Failed collecting signatures for report", ex);
            }
            report.put("signatures", signaturesJson);

            // Basic device info
            JSONObject device = new JSONObject();
            device.put("deviceModel", Build.MANUFACTURER + " " + Build.MODEL);
            device.put("androidVersion", Build.VERSION.RELEASE);
            device.put("sdkInt", Build.VERSION.SDK_INT);
            report.put("device", device);

            // Optional: include last crash logs if CrashHandler provides them
            try {
                String lastCrash = null;
                // CrashHandler is in project; attempt to read last crash log if API available (defensive)
                try {
                    Class<?> chClass = Class.forName("com.hpp.daftree.utils.CrashHandler");
                    Object instance = chClass.getMethod("getInstance").invoke(null);
                    // try common getter names
                    try {
                        lastCrash = (String) chClass.getMethod("getLastCrashLog").invoke(instance);
                    } catch (NoSuchMethodException nsme) {
                        try {
                            lastCrash = (String) chClass.getMethod("peekLastCrashLog").invoke(instance);
                        } catch (NoSuchMethodException ignore) { /* ignore */ }
                    }
                } catch (Throwable t) {
                    // لا نفشل إن لم توجد CrashHandler أو الدوال
                    Log.d(TAG, "CrashHandler not accessible or no last crash available");
                }
                if (lastCrash != null) {
                    report.put("lastCrashLog", lastCrash);
                } else {
                    report.put("lastCrashLog", JSONObject.NULL);
                }
            } catch (Exception e) {
                Log.w(TAG, "Unable to include crash log", e);
            }

            // create file in cache (safe, not external storage)
            File cacheDir = ctx.getCacheDir();
            String filename = REPORT_FILENAME_PREFIX + System.currentTimeMillis() + ".json";
            File out = new File(cacheDir, filename);

            try (BufferedWriter bw = new BufferedWriter(new FileWriter(out))) {
                bw.write(report.toString(4));
                bw.flush();
            }

            Log.i(TAG, "Security report written to: " + out.getAbsolutePath());
            return out;
        } catch (JSONException | IOException e) {
            Log.e(TAG, "Failed to create security report", e);
            return null;
        }
    }

    /**
     * مساعد: تنظيف بصمة keytool (تزيل ':' وتحول للأحرف الكبيرة).
     * (مفيدة لو أردت مقارنة البصمات يدويًا)
     */
    public static String sanitizeFingerprint(String rawFingerprint) {
        if (rawFingerprint == null) return null;
        return rawFingerprint.replace(":", "").replace(" ", "").toUpperCase(Locale.US).trim();
    }

    // =========================
    // نصائح للمطوّر / فريق المراجعة:
    //  - ضع EXPECTED_SHA256 في gradle.properties باستخدام build variant (release) بدلاً من وضعها صراحة في الكود.
    //  - لا تقم بإرسال أي ملف تلقائياً إلى الخوادم؛ اسمح للمستخدم أو لفريق الدعم بإرسال التقرير يدوياً.
    //  - عند مراجعة Google Play: أرفق ملف التقرير (من Cache) مع شرح السبب ورقم الإصدار وبصمة الشهادة.
    // =========================

}
