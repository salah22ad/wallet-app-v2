package com.hpp.daftree.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Keep;

import com.google.android.gms.tasks.Tasks;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.hpp.daftree.BuildConfig;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VersionManager {
    private static final String TAG = "VersionManager";
    private static final String PREFS_NAME = "app_version_prefs";
    private static final String KEY_LAST_VERSION_NAME = "last_version_name";
    private static final String KEY_LAST_VERSION_CODE = "last_version_code";
    private static final String KEY_LAST_PREVIOUS_NAME = "last_previous_name";
    private static final String KEY_FIRST_LAUNCH = "first_launch";
    private static final String KEY_UPDATE_SHOWN = "update_shown_";
    private static final String KEY_FirestoreUser_isAdded = "false";
    private static final String KEY_First_upgrade = "true";
    private static final String KEY_IGNORE_UPDATE_VERSION = "ignore_update_version";
    private static final String KEY_LAST_UPDATE_CHECK = "last_update_check";

    // ثوابت فحص التحديثات
    private static final String GOOGLE_PLAY_URL = "https://play.google.com/store/apps/details?id=com.hpp.daftree";
    //    private static final String GOOGLE_PLAY_URL = "https://play.google.com/store/apps/details?id=com.deepseek.chat";
    private static final String UPTODOWN_API_URL = "https://api.uptodown.com/api/v2/apps/com-hpp-daftree/versions";
    private static final String UPTODOWN_PAGE_URL = "https://com-hpp-daftree.ar.uptodown.com/android";
    private static final long ONE_WEEK_MS = 7 * 24 * 60 * 60 * 1000; // أسبوع بالميلي ثانية

    private final Context context;
    private final SharedPreferences prefs;
    private  ExecutorService executorService;
    private final Handler mainHandler;

    public VersionManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());

        // إذا كان أول تشغيل، احفظ الإصدار الحالي كإصدار سابق
        if (isFirstLaunch()) {
            saveCurrentVersion();
            markFirstLaunchComplete();
        }

    }

    // ========== واجهة فحص التحديثات ==========

    public interface UpdateListener {
        void onUpdateAvailable(String latestVersion, String changelog, String downloadUrl, String source);
        void onNoUpdateAvailable();
        void onError(String error);
    }

    /**
     * التحقق مما إذا كان يجب فحص التحديثات (مع مراعاة فترة التذكير)
     */
    public boolean shouldCheckForUpdate() {
        String ignoredVersion = prefs.getString(KEY_IGNORE_UPDATE_VERSION, "");
        long lastCheck = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0);
        long currentTime = System.currentTimeMillis();

        // إذا لم يتم تجاهل أي إصدار من قبل، يجب الفحص
        if (ignoredVersion.isEmpty()) {
            return true;
        }

        // إذا مر أسبوع منذ آخر تجاهل، يجب الفحص مرة أخرى
        boolean shouldCheck = (currentTime - lastCheck) >= ONE_WEEK_MS;
        Log.d(TAG, "فحص ضرورة الفحص: ignoredVersion=" + ignoredVersion +
                ", lastCheck=" + lastCheck + ", shouldCheck=" + shouldCheck);

        return shouldCheck;
    }

    /**
     * فحص التحديثات مع المنطق المحسّن (Google Play API + Fallback)
     */
    public void checkForUpdate(UpdateListener listener, boolean manualCheck) {
        // إذا لم يكن فحص يدوي، تحقق من فترة التذكير أولاً
        if (!manualCheck && !shouldCheckForUpdate()) {
            Log.d(TAG, "تخطي فحص التحديثات - لم تنته فترة التذكير بعد");
            mainHandler.post(listener::onNoUpdateAvailable);
            return;
        }

        ensureExecutorService();

        executorService.execute(() -> {
            try {
                UpdateInfo finalUpdateInfo = null;
                String source = "";

                Log.d(TAG, "بدء فحص التحديثات...");

                // 1) المحاولة الأولى: Google Play Official API (الأكثر دقة)
                try {
                    AppUpdateManager appUpdateManager = AppUpdateManagerFactory.create(context);
                    // استخدام Tasks.await لجعل العملية متزامنة داخل الـ Thread الخلفي
                    AppUpdateInfo appUpdateInfo = Tasks.await(appUpdateManager.getAppUpdateInfo());

                    if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                        int availableVersionCode = appUpdateInfo.availableVersionCode();
                        int currentVersionCode = getCurrentVersionCode();

                        Log.i(TAG, "Google Play API: متاح كود " + availableVersionCode + " (الحالي: " + currentVersionCode + ")");

                        if (availableVersionCode > currentVersionCode) {
                            source = "Google Play API";
                            // محاولة جلب اسم الإصدار وسجل التغييرات للعرض فقط
                            String displayVersion = tryScrapeVersionNameFallback();
                            if (displayVersion == null) displayVersion = String.valueOf(availableVersionCode);

                            String changelog = tryScrapeChangelogFallback(); // تم تحديث هذه الدالة لتدعم اللغات

                            finalUpdateInfo = new UpdateInfo(displayVersion, changelog, GOOGLE_PLAY_URL);
                        }
                    }
                } catch (Exception googleEx) {
                    Log.w(TAG, "فشل فحص Google Play API: " + googleEx.getMessage());
                }

                // 2) المحاولة الثانية: Uptodown API (إذا فشل جوجل بلاي)
                if (finalUpdateInfo == null) {
                    Log.d(TAG, "الانتقال لفحص Uptodown...");
                    try {
                        UpdateInfo uptodownInfo = checkUptodownAPI();
                        if (uptodownInfo != null && isNewVersionAvailable(getCurrentVersionName(), uptodownInfo.getVersion())) {
                            source = "Uptodown API";
                            finalUpdateInfo = uptodownInfo;
                            Log.i(TAG, "تم العثور على تحديث في Uptodown API: " + uptodownInfo.getVersion());
                        }
                    } catch (Exception apiEx) {
                        Log.w(TAG, "فشل فحص Uptodown API: " + apiEx.getMessage());

                        // المحاولة الثالثة: Uptodown Scraping
                        try {
                            UpdateInfo scrapingInfo = checkUptodownScraping();
                            if (scrapingInfo != null && isNewVersionAvailable(getCurrentVersionName(), scrapingInfo.getVersion())) {
                                source = "Uptodown Scraping";
                                finalUpdateInfo = scrapingInfo;
                                Log.i(TAG, "تم العثور على تحديث في Uptodown Scraping: " + scrapingInfo.getVersion());
                            }
                        } catch (Exception scrapeEx) {
                            Log.w(TAG, "فشل فحص Uptodown Scraping: " + scrapeEx.getMessage());
                        }
                    }
                }

                if (finalUpdateInfo == null) {
                    Log.d(TAG, "لم يتم العثور على تحديثات من أي مصدر");
                    mainHandler.post(listener::onNoUpdateAvailable);
                    return;
                }

                Log.i(TAG, "تم تحديد التحديث من: " + source + " - الإصدار: " + finalUpdateInfo.getVersion());

                // التحقق مما إذا كان يجب عرض التحديث
                if (manualCheck || shouldShowUpdate(finalUpdateInfo.getVersion())) {
                    UpdateInfo finalInfo = finalUpdateInfo;
                    String finalSource = source;
                    mainHandler.post(() -> listener.onUpdateAvailable(
                            finalInfo.getVersion(),
                            finalInfo.getChangelog(),
                            finalInfo.getDownloadUrl(),
                            finalSource
                    ));
                } else {
                    Log.d(TAG, "تم تجاهل التحديث للإصدار: " + finalUpdateInfo.getVersion());
                    mainHandler.post(listener::onNoUpdateAvailable);
                }

            } catch (Exception e) {
                Log.e(TAG, "خطأ غير متوقع في فحص التحديثات", e);
                mainHandler.post(() -> listener.onError("خطأ في فحص التحديثات: " + e.getMessage()));
            }
        });
    }

    /**
     * محاولة جلب "ما الجديد" بناءً على لغة الجهاز، مع التراجع للإنجليزية
     */
    private String tryScrapeChangelogFallback() {
        try {
            // 1. تحديد لغة الجهاز الحالية
            String deviceLang = Locale.getDefault().getLanguage(); // مثال: "ar", "en"

            // 2. المحاولة بلغة الجهاز
            String changelog = scrapeChangelogByLanguage(deviceLang);

            // التحقق من صحة النص (ليس فارغاً وطوله معقول)
            if (changelog != null && changelog.length() > 5) {
                return changelog;
            }

            // 3. إذا لم تنجح المحاولة وكانت لغة الجهاز ليست الإنجليزية، جرب الإنجليزية
            if (!"en".equals(deviceLang)) {
                Log.d(TAG, "لم يتم العثور على ما الجديد باللغة " + deviceLang + "، جاري المحاولة بالإنجليزية");
                String enChangelog = scrapeChangelogByLanguage("en");
                if (enChangelog != null) {
                    return enChangelog;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scraping changelog fallback", e);
        }
        return "تحسينات عامة وإصلاح للأخطاء";
    }

    /**
     * دالة مساعدة للاتصال بصفحة المتجر بلغة محددة واستخراج الوصف
     */
    private String scrapeChangelogByLanguage(String languageCode) {
        try {
            String url = GOOGLE_PLAY_URL + "&hl=" + languageCode;
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Linux; Android 10)")
                    .referrer("https://www.google.com")
                    .timeout(5000)
                    .get();

            // البحث عن العنصر الذي يحتوي على وصف التحديث
            Elements elements = doc.select("[itemprop=description]");
            if (!elements.isEmpty()) {
                return elements.first().text();
            }

            // محاولة إضافية لبعض الهيكليات القديمة
            elements = doc.select("div.bARER");
            if (!elements.isEmpty()) {
                return elements.first().text();
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String tryScrapeVersionNameFallback() {
        try {
            Document doc = Jsoup.connect(GOOGLE_PLAY_URL)
                    .userAgent("Mozilla/5.0 (Linux; Android 10)")
                    .timeout(5000)
                    .get();
            Matcher m = Pattern.compile("(\\d+\\.\\d+\\.\\d+)").matcher(doc.text());
            if (m.find()) return m.group(1);
        } catch (Exception e) { return null; }
        return null;
    }

    /**
     * إغلاق آمن للـ ExecutorService
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            try {
                executorService.shutdownNow();
                // انتظر لمدة قصيرة للإغلاق النهائي
                if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                    Log.w(TAG, "ExecutorService لم يتم إغلاقه في الوقت المحدد");
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "تم مقاطعة انتظار إغلاق ExecutorService", e);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                Log.e(TAG, "خطأ أثناء إغلاق ExecutorService: " + e.getMessage());
            }
        }
    }

    /**
     * إعادة تهيئة الـ ExecutorService إذا لزم الأمر
     */
    public void ensureExecutorService() {
        if (executorService == null || executorService.isShutdown() || executorService.isTerminated()) {
            executorService = Executors.newSingleThreadExecutor();
            Log.d(TAG, "تم إعادة إنشاء ExecutorService");
        }
    }
    /**
     * فحص جوجل بلاي باستخدام Jsoup
     */
    private UpdateInfo checkGooglePlay() throws Exception {
        String appUrl = GOOGLE_PLAY_URL;
        Document doc;
        try {
            doc = Jsoup.connect(appUrl)
                    .userAgent("Mozilla/5.0 (Linux; Android 10; SM-G975F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.114 Mobile Safari/537.36")
                    .referrer("https://www.google.com")
                    .timeout(15000)
                    .ignoreHttpErrors(true) // تجاهل أخطاء HTTP
                    .followRedirects(true)  // اتبع إعادة التوجيه
                    .get();
        } catch (Exception e) {
            throw new Exception("Failed to connect to Google Play: " + e.getMessage());
        }

        // التحقق من وجود إعادة توجيه
        String finalUrl = doc.connection().response().url().toString();
        if (!finalUrl.contains("com.hpp.daftree")) {
            Log.w(TAG, "تم إعادة التوجيه إلى: " + finalUrl);
        }

        String pageText = doc.body() != null ? doc.body().text() : doc.text();

        // محاولات متعددة لاستخراج رقم الإصدار
        String latestVersion = extractVersionFromGooglePlay(doc, pageText);

        if (latestVersion != null) {
            String changelog = extractChangelogFromGooglePlay(doc, pageText);
            return new UpdateInfo(latestVersion, changelog, appUrl);
        }

        throw new Exception("Could not extract version from Google Play");
    }
    private UpdateInfo checkGooglePlay1() throws Exception {
        String appUrl = GOOGLE_PLAY_URL;
        Document doc = Jsoup.connect(appUrl)
                .userAgent("Mozilla/5.0 (Linux; Android 10; SM-G975F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.114 Mobile Safari/537.36")
                .referrer("https://www.google.com")
                .timeout(15000)
                .get();

        String pageText = doc.body() != null ? doc.body().text() : doc.text();

        // محاولات متعددة لاستخراج رقم الإصدار من جوجل بلاي
        String latestVersion = extractVersionFromGooglePlay(doc, pageText);

        if (latestVersion != null) {
            String changelog = extractChangelogFromGooglePlay(doc, pageText);
            return new UpdateInfo(latestVersion, changelog, appUrl);
        }

        throw new Exception("Could not extract version from Google Play");
    }

    /**
     * استخراج الإصدار من صفحة جوجل بلاي
     */
    private String extractVersionFromGooglePlay(Document doc, String pageText) {
        String latestVersion = null;

        // المحاولة 1: البحث في عناصر النص التي تحتوي على "الإصدار" أو "Version"
        Elements versionElements = doc.select("div:contains(الإصدار), div:contains(Version), span:contains(الإصدار), span:contains(Version)");
        for (org.jsoup.nodes.Element element : versionElements) {
            String text = element.text().trim();
            Matcher matcher = Pattern.compile("(?:الإصدار|Version)\\s*[:\\-]?\\s*([\\d\\.]+)", Pattern.CASE_INSENSITIVE).matcher(text);
            if (matcher.find()) {
                latestVersion = matcher.group(1);
                break;
            }
        }

        // المحاولة 2: البحث عن أنماط الإصدار في النص الكامل للصفحة
        if (latestVersion == null) {
            Matcher matcher = Pattern.compile("(?:الإصدار الحالي|Current Version|Version)\\s*[:\\-]?\\s*([\\d\\.]+)", Pattern.CASE_INSENSITIVE).matcher(pageText);
            if (matcher.find()) {
                latestVersion = matcher.group(1);
            }
        }

        // المحاولة 3: البحث عن أي نمط إصدار قريب من كلمات مثل "تحديث" أو "Update"
        if (latestVersion == null) {
            Matcher matcher = Pattern.compile("(?:تحديث|Update).*?([\\d\\.]+)", Pattern.CASE_INSENSITIVE).matcher(pageText);
            if (matcher.find()) {
                latestVersion = matcher.group(1);
            }
        }

        // المحاولة 4: البحث عن أي رقم إصدار في مناطق محددة من الصفحة
        if (latestVersion == null) {
            // البحث في عناصر محددة قد تحتوي على الإصدار
            String[] selectors = {
                    "div.hAyfc:contains(الإصدار)",
                    "div.hAyfc:contains(Version)",
                    "div.BgcNfc",
                    "span.htlgb"
            };

            for (String selector : selectors) {
                Elements elements = doc.select(selector);
                for (org.jsoup.nodes.Element element : elements) {
                    String text = element.text().trim();
                    Matcher versionMatcher = Pattern.compile("([\\d\\.]+)").matcher(text);
                    if (versionMatcher.find()) {
                        String candidate = versionMatcher.group(1);
                        // التأكد من أن هذا يشبه رقم إصدار (يحتوي على نقطة على الأقل)
                        if (candidate.contains(".")) {
                            latestVersion = candidate;
                            break;
                        }
                    }
                }
                if (latestVersion != null) break;
            }
        }

        return latestVersion;
    }

    /**
     * استخراج سجل التغييرات من جوجل بلاي
     */
    private String extractChangelogFromGooglePlay(Document doc, String pageText) {
        try {
            // البحث عن عناصر قد تحتوي على سجل التغييرات
            String[] changelogSelectors = {
                    "div[itemprop=description]",
                    "div.W4P4ne",
                    "div.DWPxHb",
                    "div.bARER",
                    "div.UD7Dzf"
            };

            for (String selector : changelogSelectors) {
                org.jsoup.nodes.Element changelogElement = doc.selectFirst(selector);
                if (changelogElement != null) {
                    String changelog = changelogElement.text().trim();
                    if (!changelog.isEmpty() && changelog.length() < 1000) { // تجنب النصوص الطويلة جداً
                        return changelog;
                    }
                }
            }

            // إذا لم نجد سجل تغييرات محدد، نعيد رسالة افتراضية
            return "تحسينات في الأداء وإصلاح للأخطاء";
        } catch (Exception e) {
            return "تحسينات في الأداء وإصلاح للأخطاء";
        }
    }

    /**
     * فحص Uptodown باستخدام API
     */
    private UpdateInfo checkUptodownAPI() throws Exception {
        URL url = new URL(UPTODOWN_API_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            UpdateInfo infoFromApi = parseUpdateInfo(sb.toString());
            if (infoFromApi != null) {
                return infoFromApi;
            }
        }
        throw new Exception("Uptodown API returned: " + responseCode);
    }

    /**
     * فحص Uptodown باستخدام Scraping (الكود الأصلي)
     */
    private UpdateInfo checkUptodownScraping() throws Exception {
        String appUrl = UPTODOWN_PAGE_URL;
        Document doc = Jsoup.connect(appUrl)
                .userAgent("Mozilla/5.0 (Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0")
                .referrer("https://www.google.com")
                .timeout(10000)
                .get();

        String latestVersion = null;
        String changelog = "";
        String pageText = doc.body() != null ? doc.body().text() : doc.text();

        // محاولة أولى: عناصر لها نص مطابق لنمط الإصدارات
        Pattern exactVersionPattern = Pattern.compile("^\\d+(?:\\.\\d+){1,}$");
        Elements all = doc.getAllElements();
        for (org.jsoup.nodes.Element el : all) {
            String own = el.ownText().trim();
            if (!own.isEmpty()) {
                Matcher m = exactVersionPattern.matcher(own);
                if (m.matches()) {
                    latestVersion = own;
                    break;
                }
            }
        }

        // محاولة ثانية: البحث بالقرب من عنوان الصفحة
        if (latestVersion == null) {
            String title = doc.select("h1").text();
            int titleIdx = pageText.indexOf(title);
            int endIdx = Math.min(pageText.length(), Math.max(1000, (titleIdx >= 0 ? titleIdx + 1000 : 1000)));
            String area = titleIdx >= 0 ? pageText.substring(titleIdx, endIdx) : pageText.substring(0, endIdx);

            Matcher m2 = Pattern.compile("\\b(\\d+\\.\\d+(?:\\.\\d+)*)\\b").matcher(area);
            if (m2.find()) latestVersion = m2.group(1);
        }

        // محاولة ثالثة: بحث صريح عن جملة "أحدث نسخة" أو ما يماثلها
        if (latestVersion == null) {
            Matcher m3 = Pattern.compile("(?:أحدث نسخة|أحدث إصدار|Latest version|latest version|Version)\\D*(\\d+(?:\\.\\d+)+)", Pattern.CASE_INSENSITIVE)
                    .matcher(pageText);
            if (m3.find()) latestVersion = m3.group(1);
        }

        // استخراج changelog
        org.jsoup.nodes.Element changelogEl = doc.selectFirst("div.changelog, .changelog, #whatsnew, .whats-new");
        if (changelogEl != null) changelog = changelogEl.text();

        if (latestVersion != null) {
            return new UpdateInfo(latestVersion, changelog, appUrl);
        }

        throw new Exception("Could not extract version from Uptodown page");
    }

    // ========== باقي الدوال بدون تغيير ==========

    /**
     * التحقق مما إذا كان يجب عرض التحديث
     */
    public boolean shouldShowUpdate(String latestVersion) {
        String ignoredVersion = prefs.getString(KEY_IGNORE_UPDATE_VERSION, "");
        if (ignoredVersion.equals(latestVersion)) {
            long lastCheck = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0);
            long currentTime = new Date().getTime();

            // إذا مر أسبوع منذ آخر تجاهل، اعرض التحديث مرة أخرى
            return (currentTime - lastCheck) >= ONE_WEEK_MS;
        }

        return true;
    }

    /**
     * تعيين أن المستخدم تجاهل هذا الإصدار
     */
    public void setUpdateIgnored(String version) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_IGNORE_UPDATE_VERSION, version);
        editor.putLong(KEY_LAST_UPDATE_CHECK, new Date().getTime());
        editor.apply();
    }

    private UpdateInfo parseUpdateInfo(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONObject latestVersion = jsonObject.getJSONArray("data").getJSONObject(0);

            String version = latestVersion.getString("version");
            String changelog = latestVersion.optString("changelog", "");
            String downloadUrl = latestVersion.optString("download_url", UPTODOWN_PAGE_URL);

            return new UpdateInfo(version, changelog, downloadUrl);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing update info: " + e.getMessage());
            return null;
        }
    }

    private boolean isNewVersionAvailable(String currentVersion, String latestVersion) {
        try {
            String[] currentParts = currentVersion.split("\\.");
            String[] latestParts = latestVersion.split("\\.");

            for (int i = 0; i < Math.max(currentParts.length, latestParts.length); i++) {
                int current = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
                int latest = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;

                if (latest > current) return true;
                if (latest < current) return false;
            }
            return false;
        } catch (Exception e) {
            return !currentVersion.equals(latestVersion);
        }
    }

    // ========== الدوال الأصلية لـ VersionManager ==========

    public int getCurrentVersionCode() {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return (int) androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(pInfo);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public String getVersionInfo() {
        return "الإصدار الحالي: " + getCurrentVersionName() +
                " (Build: " + getCurrentVersionCode() + ")" +
                "\nPreviousVersion: " + getPreviousVersion()  +
                "\nالإصدار السابق: " + getLastVersionName()  +
                " (Build: " + getLastKnownVersionCode() + ")";
    }

    public String getCurrentVersionName() {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return "1.0.1";
        }
    }

    public boolean isFirstLaunch() {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true);
    }
        public boolean isUpdateShownForCurrentVersion() {
        String currentVersionCode = String.valueOf(getCurrentVersionCode());
        return prefs.getBoolean(KEY_UPDATE_SHOWN + currentVersionCode, false);
    }

    public boolean getFirestoreUser_isAdded() {
        return  (((getLastKnownVersionCode()) ==1) && prefs.getBoolean(KEY_First_upgrade, true));
    }

    public void setFirestoreUser_isAdded(boolean isAdded) {
        prefs.edit().putBoolean(KEY_FirestoreUser_isAdded, isAdded).apply();
    }

    public void setFirst_upgrade(boolean isUpgrade) {
        prefs.edit().putBoolean(KEY_First_upgrade, isUpgrade).apply();
    }

    public boolean first_upgrade() {
        return prefs.getBoolean(KEY_First_upgrade, true);
    }

    public void markUpdateAsShown() {
        String currentVersionCode = String.valueOf(getCurrentVersionCode());
        prefs.edit().putBoolean(KEY_UPDATE_SHOWN + currentVersionCode, true).apply();
    }

    public void markFirstLaunchComplete() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply();
    }

    public String getLastVersionName() {
        return prefs.getString(KEY_LAST_VERSION_NAME, "1.0.1");
    }

    public int getLastKnownVersionCode() {
        return prefs.getInt(KEY_LAST_VERSION_CODE, -1);
    }
    public String getPreviousVersion() {
        return prefs.getString(KEY_LAST_PREVIOUS_NAME, "1.0.1");
    }

    private int compareVersions(String version1, String version2) {
        String[] parts1 = version1.split("\\.");
        String[] parts2 = version2.split("\\.");

        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int part1 = (i < parts1.length) ? Integer.parseInt(parts1[i]) : 0;
            int part2 = (i < parts2.length) ? Integer.parseInt(parts2[i]) : 0;

            if (part1 != part2) {
                return part1 - part2;
            }
        }
        return 0;
    }

    public int splitVersions(String version1) {
        String parts = version1.replace(".","");
        Log.e("VersionManager","parts : " + parts );
        return parts.isEmpty() ? 0 : Integer.parseInt(parts);
    }

    public boolean isMajorUpdate() {
        return BuildConfig.IS_MAJOR_UPDATE;
    }

    public boolean isMinorUpdate() {
        String lastVersionName = String.valueOf(getLastKnownVersionCode());
        String currentVersionName = String.valueOf(getCurrentVersionCode());
        return compareVersions(currentVersionName, lastVersionName) > 0 && !isMajorUpdate();
    }

    public void clearVersionData() {
        prefs.edit().clear().apply();
    }

    /**
     * إغلاق الـ ExecutorService عند عدم الحاجة
     */
    public void shutdown1() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
    // دالة للتحقق مما إذا كان هذا هو التشغيل الأول مطلقاً (تثبيت جديد)
    public boolean isFreshInstall() {
        int lastVersion = prefs.getInt(KEY_LAST_VERSION_CODE, -1);
        return lastVersion == -1;
    }

    // دالة للتحقق مما إذا تم تحديث التطبيق (الإصدار الحالي أكبر من المخزن)
    public boolean isAppUpdated() {
        int lastVersion = prefs.getInt(KEY_LAST_VERSION_CODE, -1);
        int currentVersion = getCurrentVersionCode();
        int diff = currentVersion- lastVersion;
        if (diff>0) {
            savePreviousVersion(getLastVersionName());
            saveCurrentVersion();
        }
        return (diff ) > 0;
    }

    // دالة لحفظ الإصدار الحالي (تستخدم عند إغلاق الديالوج أو في التثبيت الجديد)
    public void saveCurrentVersion() {
        prefs.edit().putInt(KEY_LAST_VERSION_CODE, getCurrentVersionCode()).apply();
        prefs.edit().putString(KEY_LAST_VERSION_NAME, getCurrentVersionName()).apply();
    }
    public void savePreviousVersion(String previousVersion) {
        prefs.edit().putString(KEY_LAST_PREVIOUS_NAME, previousVersion).apply();
    }
    // ========== الصنف الداخلي لتخزين معلومات التحديث ==========
    @Keep
    private static class UpdateInfo {
        private String version;
        private String changelog;
        private String downloadUrl;

        public UpdateInfo(String version, String changelog, String downloadUrl) {
            this.version = version;
            this.changelog = changelog;
            this.downloadUrl = downloadUrl;
        }

        public String getVersion() { return version; }
        public String getChangelog() { return changelog; }
        public String getDownloadUrl() { return downloadUrl; }
    }
}