package com.hpp.daftree.models;

import android.app.Application;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;

import com.hpp.daftree.utils.SecureLicenseManager;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.Transaction;
import com.hpp.daftree.helpers.LanguageManager;
import com.hpp.daftree.helpers.WebTranslationHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class LocalWebServer extends NanoHTTPD {

    private static final String TAG = "LocalWebServer";
    private final Context context;
    private final DaftreeRepository repository;
    private LanguageManager languageManager;
    private final WebTranslationHelper translationHelper;
    private final SecureLicenseManager licenseManager;

    // مفتاح التشفير من ملف gradle.properties
//    private static final String ENCRYPTION_KEY = "";// = SecurityKeys.getWebEncryptionKey();

    public LocalWebServer(int port, Context context) {
        super(port);
        this.context = context;
        this.repository = new DaftreeRepository((Application) context.getApplicationContext());
        this.languageManager = LanguageManager.getInstance(context);
        this.translationHelper = WebTranslationHelper.getInstance(context);
//        this.ENCRYPTION_KEY = SecureLicenseManager.getInstance(context).getWebEncryptionKey();

        this.licenseManager =  SecureLicenseManager.getInstance(context);
    }

    private String safeTranslation(String key, String defaultValue) {
        try {
            if (translationHelper == null) return defaultValue;
            String translation = translationHelper.getTranslation(key, defaultValue);
            if (translation == null || translation.trim().isEmpty()) return defaultValue;
            return translation;
        } catch (Exception e) {
            Log.e(TAG, "Error getting translation for key: " + key, e);
            return defaultValue;
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();

        // ✅ هنا يتم التعامل مع جميع الملفات المشفرة
        if (uri.equals("/styles.css")) {
            // 1. styles.css (مشفّر)
            return serveAsset("styles.css", "text/css");
        } else if (uri.equals("/styles_web_server.css")) {
            // 2. styles_web_server.css (مشفّر)
            return serveAsset("styles_web_server.css", "text/css");
        } else if (uri.equals("/web_server_interface.html")) {
            // 3. web_server_interface.html (مشفّر)
            return serveAsset("web_server_interface.html", "text/html");
        } else if (uri.equals("/dynamic-styles.css")) {
            // يتم توليده برمجياً (لا يحتاج فك تشفير)
            return serveDynamicStyles();
        } else if (uri.startsWith("/account-details")) {
            Map<String, String> params = session.getParms();
            if (params.containsKey("id")) {
                try {
                    int accountId = Integer.parseInt(params.get("id"));
                    // 4. account_details.html (مشفّر) -> يتم استدعاؤه داخل الدالة
                    return serveAccountDetailsPage(accountId);
                } catch (NumberFormatException e) {
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Invalid account ID");
                }
            } else {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing account ID");
            }
        } else {
            // 5. index.html (مشفّر) -> يتم استدعاؤه داخل الدالة
            return serveHomePage();
        }
    }

    /** الصفحة الرئيسية (index.html) */
    private Response serveHomePage() {
        try {
            // ✅ قراءة وفك تشفير index.html
            String template = readAssetFile("index.html");

            // ... (بقية منطق الترجمة واستبدال المتغيرات كما هو) ...
            WebTranslationHelper.WebLocaleInfo localeInfo = null;
            String direction = "rtl";
            String fontFamily = "Cairo";
            String languageCode = "ar";

            try {
                if (translationHelper != null) {
                    localeInfo = translationHelper.getWebLocaleInfo();
                    if (localeInfo != null) {
                        direction = localeInfo.getDirection();
                        fontFamily = localeInfo.getFontFamily();
                        languageCode = localeInfo.getLanguageCode();
                    }
                }
            } catch (Exception e) { Log.e(TAG, "Error locale", e); }

            template = template.replace("%%DIRECTION%%", direction);
            template = template.replace("%%FONT_FAMILY%%", fontFamily);
            template = template.replace("%%LANGUAGE_CODE%%", languageCode);

            String pageTitle = safeTranslation("page_title", "التقارير المالية");
            String financialReports = safeTranslation("financial_reports", "التقارير المالية");
            template = template.replace("%%PAGE_TITLE%%", pageTitle);
            template = template.replace("%%FINANCIAL_REPORTS%%", financialReports);

            String accountTablesHtml = generateAccountTablesHtml();
            String monthlyTotalsHtml = generateMonthlyTotalsHtml();
            template = template.replace("%%ACCOUNT_TABLES%%", accountTablesHtml);
            template = template.replace("%%MONTHLY_TOTALS%%", monthlyTotalsHtml);

            return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", template);
        } catch (Exception e) {
            Log.e(TAG, "Error serving home page", e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: " + e.getMessage());
        }
    }

    /** صفحة التفاصيل (account_details.html) */
    private Response serveAccountDetailsPage(int accountId) {
        try {
            // ✅ قراءة وفك تشفير account_details.html
            String template = readAssetFile("account_details.html");

            // ... (بقية منطق الترجمة واستبدال المتغيرات) ...
            WebTranslationHelper.WebLocaleInfo localeInfo = null;
            String direction = "rtl";
            String fontFamily = "Cairo";
            String languageCode = "ar";
            try {
                if (translationHelper != null) {
                    localeInfo = translationHelper.getWebLocaleInfo();
                    if (localeInfo != null) {
                        direction = localeInfo.getDirection();
                        fontFamily = localeInfo.getFontFamily();
                        languageCode = localeInfo.getLanguageCode();
                    }
                }
            } catch (Exception e) { Log.e(TAG, "Error locale", e); }

            template = template.replace("%%DIRECTION%%", direction);
            template = template.replace("%%FONT_FAMILY%%", fontFamily);
            template = template.replace("%%LANGUAGE_CODE%%", languageCode);

            String accountDetails = safeTranslation("account_details", "تفاصيل الحساب");
            String accountSummary = safeTranslation("account_summary", "ملخص الحساب");
            String transactionHistory = safeTranslation("transaction_history", "سجل المعاملات");
            template = template.replace("%%ACCOUNT_DETAILS%%", accountDetails);
            template = template.replace("%%ACCOUNT_SUMMARY%%", accountSummary);
            template = template.replace("%%TRANSACTION_HISTORY%%", transactionHistory);

            Account account = repository.getAccountDao().getAccountByIdBlocking(accountId);
            if (account == null) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", safeTranslation("account_not_found", "الحساب غير موجود"));
            }
            String accountName = account.getAccountName();
            if (accountName == null || accountName.trim().isEmpty()) accountName = safeTranslation("unknown_account", "حساب غير معروف");
            template = template.replace("%%ACCOUNT_NAME%%", accountName);

            String totalsTableHtml = generateAccountTotalsTable(accountId);
            String transactionTablesHtml = generateTransactionTables(accountId);
            template = template.replace("%%TRANSACTION_TABLES%%", transactionTablesHtml);
            template = template.replace("%%TOTALS_TABLE%%", totalsTableHtml);

            return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", template);
        } catch (Exception e) {
            Log.e(TAG, "Error serving account details", e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: " + e.getMessage());
        }
    }

    private Response serveDynamicStyles() {
        try {
            WebTranslationHelper.WebLocaleInfo localeInfo = null;
            if (translationHelper != null) {
                localeInfo = translationHelper.getWebLocaleInfo();
            }
            String css = generateDynamicCSS(localeInfo);
            return newFixedLengthResponse(Response.Status.OK, "text/css", css);
        } catch (Exception e) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: " + e.getMessage());
        }
    }

    // --- دوال توليد الجداول (لم تتغير) ---
    private String generateAccountTablesHtml() {
        StringBuilder html = new StringBuilder();
        List<AccountType> types = repository.getAllAccountTypesBlockingReport();
        List<Currency> currencies = repository.getAllCurrenciesBlocking();
        for (AccountType type : types) {
            html.append("<h2>").append(type.name).append("</h2>");
            html.append("<table class='accounts-table'>");
            html.append("<thead><tr>");
            html.append("<th>").append(safeTranslation("account_name", "اسم الحساب")).append("</th>");
            html.append("<th>").append(safeTranslation("currency", "العملة")).append("</th>");
            html.append("<th>").append(safeTranslation("balance_type", "نوع الرصيد")).append("</th>");
            html.append("<th>").append(safeTranslation("balance", "الرصيد")).append("</th>");
            html.append("</tr></thead><tbody>");
            boolean hasAccounts = false;
            for (Currency currency : currencies) {
                List<AccountBalanceSummary> summaries = repository.getAccountBalancesBynameType(type.name, currency.id, 0L, Long.MAX_VALUE);
                for (AccountBalanceSummary summary : summaries) {
                    if (summary.balance != 0) {
                        hasAccounts = true;
                        String balanceType = summary.balance >= 0 ? safeTranslation("debit", "مدين") : safeTranslation("credit", "دائن");
                        String balanceClass = summary.balance >= 0 ? "debit" : "credit";
                        html.append("<tr>");
                        html.append("<td><a href='/account-details?id=").append(summary.accountId).append("'>").append(summary.accountName).append("</a></td>");
                        html.append("<td>").append(currency.name).append("</td>");
                        html.append("<td>").append(balanceType).append("</td>");
                        html.append("<td class='").append(balanceClass).append("'>").append(formatCurrency(summary.balance)).append("</td>");
                        html.append("</tr>");
                    }
                }
            }
            if (!hasAccounts) html.append("<tr><td colspan='4'>").append(safeTranslation("no_accounts_with_balance", "لا توجد حسابات برصيد")).append("</td></tr>");
            html.append("</tbody></table>");
        }
        return html.toString();
    }

    private String generateMonthlyTotalsHtml() {
        StringBuilder html = new StringBuilder();
        List<Currency> currencies = repository.getAllCurrenciesBlocking();
        for (Currency currency : currencies) {
            List<MonthlySummary> summaries = repository.getGlobalMonthlySummary(currency.id);
            if (summaries.isEmpty()) continue;
            html.append("<h3>").append(safeTranslation("currency_total", "إجمالي العملة")).append(" ").append(currency.name).append("</h3>");
            html.append("<table class='monthly-table'>");
            html.append("<thead><tr>");
            html.append("<th>").append(safeTranslation("month", "الشهر")).append("</th>");
            html.append("<th>").append(safeTranslation("total_incoming", "إجمالي الوارد")).append("</th>");
            html.append("<th>").append(safeTranslation("total_outgoing", "إجمالي الصادر")).append("</th>");
            html.append("<th>").append(safeTranslation("net_movement", "صافي الحركة")).append("</th>");
            html.append("</tr></thead><tbody>");
            for (MonthlySummary summary : summaries) {
                double netChange = summary.totalDebit - summary.totalCredit;
                String netChangeClass = netChange >= 0 ? "debit" : "credit";
                html.append("<tr>");
                html.append("<td>").append(summary.yearMonth).append("</td>");
                html.append("<td class='credit'>").append(formatCurrency(summary.totalCredit)).append("</td>");
                html.append("<td class='debit'>").append(formatCurrency(summary.totalDebit)).append("</td>");
                html.append("<td class='").append(netChangeClass).append("'>").append(formatCurrency(Math.abs(netChange))).append("</td>");
                html.append("</tr>");
            }
            html.append("</tbody></table>");
        }
        return html.toString();
    }

    private String generateAccountTotalsTable(int accountId) {
        List<AccountTotalsByCurrency> totals = repository.getAccountTotalsByCurrency(accountId);
        if (totals.isEmpty()) return "<p>" + safeTranslation("no_balances_account", "لا توجد أرصدة لهذا الحساب") + "</p>";
        StringBuilder html = new StringBuilder();
        html.append("<table class='totals-table'>");
        html.append("<thead><tr>");
        html.append("<th>").append(safeTranslation("currency", "العملة")).append("</th>");
        html.append("<th>").append(safeTranslation("summary_debit", "إجمالي المدين (لك)")).append("</th>");
        html.append("<th>").append(safeTranslation("summary_credit", "إجمالي الدائن (عليك)")).append("</th>");
        html.append("<th>").append(safeTranslation("final_balance", "الرصيد النهائي")).append("</th>");
        html.append("</tr></thead><tbody>");
        for (AccountTotalsByCurrency total : totals) {
            double finalBalance = total.totalDebit - total.totalCredit;
            String balanceType = finalBalance >= 0 ? safeTranslation("debit", "مدين") : safeTranslation("credit", "دائن");
            String balanceClass = finalBalance >= 0 ? "debit" : "credit";
            html.append("<tr>");
            html.append("<td>").append(total.currency).append("</td>");
            html.append("<td class='debit'>").append(formatCurrency(total.totalDebit)).append("</td>");
            html.append("<td class='credit'>").append(formatCurrency(total.totalCredit)).append("</td>");
            html.append("<td class='").append(balanceClass).append("'>").append(formatCurrency(Math.abs(finalBalance))).append(" (").append(balanceType).append(")</td>");
            html.append("</tr>");
        }
        html.append("</tbody></table>");
        return html.toString();
    }

    private String generateTransactionTables(int accountId) {
        StringBuilder html = new StringBuilder();
        List<Currency> currencies = repository.getAllCurrenciesBlocking();
        for (Currency currency : currencies) {
            List<Transaction> transactions = repository.getTransactionsForAccountBlocking(accountId, currency.id);
            if (transactions.isEmpty()) continue;
            html.append("<h3>").append(safeTranslation("transactions_currency", "المعاملات بالعملة")).append(" ").append(currency.name).append("</h3>");
            html.append("<table class='transactions-table'>");
            html.append("<thead><tr>");
            html.append("<th>").append(safeTranslation("date", "التاريخ")).append("</th>");
            html.append("<th>").append(safeTranslation("details", "التفاصيل")).append("</th>");
            html.append("<th>").append(safeTranslation("debit", "مدين")).append("</th>");
            html.append("<th>").append(safeTranslation("credit", "دائن")).append("</th>");
            html.append("<th>").append(safeTranslation("running_balance", "الرصيد التراكمي")).append("</th>");
            html.append("</tr></thead><tbody>");
            double runningBalance = 0;
            for (Transaction tx : transactions) {
                runningBalance += (tx.getAmount() * tx.getType());
                String type = tx.getType() == 1 ? "debit" : "credit";
                html.append("<tr>");
                html.append("<td>").append(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(tx.getTimestamp())).append("</td>");
                html.append("<td>").append(tx.getDetails()).append("</td>");
                html.append("<td class='debit'>").append(tx.getType() == 1 ? formatCurrency(tx.getAmount()) : "0").append("</td>");
                html.append("<td class='credit'>").append(tx.getType() == -1 ? formatCurrency(tx.getAmount()) : "0").append("</td>");
                html.append("<td class='").append(type).append("'>").append(formatCurrency(runningBalance)).append("</td>");
                html.append("</tr>");
            }
            html.append("</tbody></table>");
        }
        return html.toString();
    }

    private String generateDynamicCSS(WebTranslationHelper.WebLocaleInfo localeInfo) {
        String direction = "rtl";
        String fontFamily = "Cairo";
        String textAlign = "right";
        boolean isRTL = true;
        if (localeInfo != null) {
            direction = localeInfo.getDirection();
            fontFamily = localeInfo.getFontFamily();
            textAlign = localeInfo.getTextAlign();
            isRTL = localeInfo.isRTL();
        }
        StringBuilder css = new StringBuilder();
        css.append("body { direction: ").append(direction).append("; font-family: ").append(fontFamily).append(", sans-serif; text-align: ").append(textAlign).append("; }\n");
        css.append("h1, h2, h3 { text-align: ").append(textAlign).append("; }\n");
        css.append("table th, table td { text-align: center; padding: 8px; border: 1px solid #ddd; }\n");
        css.append("table th { background-color: #f5f5f5; font-weight: bold; }\n");
        if (isRTL) {
            css.append("html { direction: rtl; unicode-bidi: embed; }\n");
            css.append("a { text-decoration: none; color: #0066cc; }\n");
        }
        return css.toString();
    }

    private Resources getLocalizedResources() {
        return LanguageManager.getLocalizedResources(context);
    }
    private String formatCurrency(double amount) {
        return NumberFormat.getNumberInstance(Locale.US).format(amount);
    }

    /** * ✅ دالة خدمة الملفات (CSS, HTML)
     * تستخدم readAssetFile لضمان فك تشفير أي ملف يتم طلبه (بما في ذلك styles.css, web_server_interface.html)
     */
    private Response serveAsset(String fileName, String mimeType) {
        try {
            return newFixedLengthResponse(Response.Status.OK, mimeType, readAssetFile(fileName));
        } catch (IOException e) {
            Log.e(TAG, "File not found or decrypt error: " + fileName, e);
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", safeTranslation("file_not_found", "الملف غير موجود"));
        }
    }

    /**
     * ✅ الدالة الأساسية للقراءة وفك التشفير
     * تقوم بإضافة .png تلقائياً للبحث عن الملف المشفر وفك تشفيره
     */
    private String readAssetFile(String fileName) throws IOException {
        AssetManager assetManager = context.getAssets();
        // نبحث عن النسخة المشفرة (مثلاً styles.css.png)
        String encryptedFileName = fileName + ".png";

        try (InputStream is = assetManager.open(encryptedFileName)) {
            byte[] encryptedBytes = new byte[is.available()];
            is.read(encryptedBytes);
            // فك التشفير
            byte[] decryptedBytes = decrypt(encryptedBytes);
            // إرجاع المحتوى كنص
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // محاولة أخيرة لقراءة الملف الأصلي في حال فشل العثور على المشفر (لأغراض التطوير فقط)
            try {
                InputStream is = assetManager.open(fileName);
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) builder.append(line).append("\n");
                return builder.toString();
            } catch (Exception ex) {
                Log.e(TAG, "Error reading/decrypting asset: " + fileName, e);
                throw new IOException("Failed to decrypt asset: " + fileName);
            }
        }
    }

    private byte[] decrypt(byte[] encryptedData) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(licenseManager.getWebEncryptionKey().getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        return cipher.doFinal(encryptedData);
    }
}