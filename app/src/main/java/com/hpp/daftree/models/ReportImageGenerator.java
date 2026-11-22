package com.hpp.daftree.models;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.content.res.ResourcesCompat;

import com.hpp.daftree.R;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.Transaction;
import com.hpp.daftree.database.User;
import com.hpp.daftree.helpers.LanguageHelperForReports;
import com.hpp.daftree.helpers.LanguageManager;

import java.io.File;
import java.io.FileOutputStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReportImageGenerator {

    private static final String TAG = "ReportImageGenerator";
    private final Context context;
    private final DaftreeRepository repository;
    private final User userProfile;
    private final boolean isRTL;
    private final Resources localizedResources;

    // إعدادات الصورة
    private static final int PAGE_WIDTH = 1200;
    private static final int MARGIN = 50;
    private static final int ROW_HEIGHT = 50;
    private static final int HEADER_HEIGHT = 150;
    private static final int FOOTER_HEIGHT = 60;
    private static final int TABLE_HEADER_HEIGHT = 45;

    // الخطوط
    private Typeface arabicTypeface;
    private Typeface latinTypeface;
    private Typeface defaultTypeface;

    // أدوات الرسم (نوع TextPaint للنصوص، Paint للخلفيات والخطوط)
    private TextPaint headerTextPaint;
    private TextPaint tableHeaderTextPaint;
    private TextPaint cellTextPaint;
    private TextPaint titlePaint;
    private TextPaint subTitlePaint;
    private TextPaint totalLabelPaint;
    private TextPaint totalValuePaint;
    private TextPaint redPaint;
    private TextPaint greenPaint;
    private TextPaint footerPaint;

    private Paint headerBgPaint;
    private Paint tableHeaderBgPaint;
    private Paint evenRowPaint;
    private Paint oddRowPaint;
    private Paint lightCyanPaint;
    private Paint linePaint;

    // الألوان المطابقة لتقرير PDF
    private final int primaryColor = Color.rgb(0, 77, 153);
    private final int headerBgColor = Color.rgb(230, 232, 235);
    private final int evenRowColor = Color.rgb(248, 249, 250);
    private final int lightCyan = Color.rgb(224, 247, 250);

    public ReportImageGenerator(Context context, User userProfile, String language) {
        this.context = context;
        this.repository = new DaftreeRepository((android.app.Application) context.getApplicationContext());
        this.userProfile = userProfile;
        this.isRTL = LanguageManager.isRTL(context);  // استخدام الطريقة الصحيحة
        this.localizedResources = LanguageHelperForReports.getAppropriateResources(context, language);

        initializePaints();
        loadTypefaces();
    }

    private void initializePaints() {
        headerBgPaint = new Paint();
        headerBgPaint.setColor(headerBgColor);

        headerTextPaint = new TextPaint();
        headerTextPaint.setColor(primaryColor);
        headerTextPaint.setTextSize(36);
        headerTextPaint.setFakeBoldText(true);

        tableHeaderBgPaint = new Paint();
        tableHeaderBgPaint.setColor(headerBgColor);

        tableHeaderTextPaint = new TextPaint();
        tableHeaderTextPaint.setColor(Color.BLACK);
        tableHeaderTextPaint.setTextSize(26);
        tableHeaderTextPaint.setFakeBoldText(true);

        cellTextPaint = new TextPaint();
        cellTextPaint.setColor(Color.BLACK);
        cellTextPaint.setTextSize(24);

        evenRowPaint = new Paint();
        evenRowPaint.setColor(evenRowColor);

        oddRowPaint = new Paint();
        oddRowPaint.setColor(Color.WHITE);

        lightCyanPaint = new Paint();
        lightCyanPaint.setColor(lightCyan);

        titlePaint = new TextPaint();
        titlePaint.setColor(primaryColor);
        titlePaint.setTextSize(40);
        titlePaint.setFakeBoldText(true);

        subTitlePaint = new TextPaint();
        subTitlePaint.setColor(Color.DKGRAY);
        subTitlePaint.setTextSize(26);

        totalLabelPaint = new TextPaint();
        totalLabelPaint.setColor(Color.BLACK);
        totalLabelPaint.setTextSize(28);
        totalLabelPaint.setFakeBoldText(true);

        totalValuePaint = new TextPaint();
        totalValuePaint.setColor(Color.BLACK);
        totalValuePaint.setTextSize(28);
        totalValuePaint.setFakeBoldText(true);

        redPaint = new TextPaint(cellTextPaint);
        redPaint.setColor(Color.RED);

        greenPaint = new TextPaint(cellTextPaint);
        greenPaint.setColor(Color.rgb(0, 128, 0));

        footerPaint = new TextPaint();
        footerPaint.setColor(Color.GRAY);
        footerPaint.setTextSize(22);
        footerPaint.setFakeBoldText(true);

        linePaint = new Paint();
        linePaint.setColor(primaryColor);
        linePaint.setStrokeWidth(4);
    }

    private void loadTypefaces() {
        try {
            arabicTypeface = ResourcesCompat.getFont(context, R.font.cairo_regular);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load Arabic font, using default", e);
            arabicTypeface = Typeface.DEFAULT;
        }

        try {
            latinTypeface = Typeface.createFromAsset(context.getAssets(), "NotoSans.ttf");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load Latin font, using default", e);
            latinTypeface = Typeface.DEFAULT;
        }

        defaultTypeface = Typeface.DEFAULT;
    }

    private Typeface getAppropriateTypeface(String text) {
        if (text == null) return defaultTypeface;
        if (containsArabic(text)) return arabicTypeface;
        return latinTypeface;
    }

    private boolean containsArabic(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x0600 && c <= 0x06FF) return true;
            if (c >= 0xFE70 && c <= 0xFEFF) return true;
            if (c >= 0x0750 && c <= 0x077F) return true;
            if (c >= 0xFB50 && c <= 0xFDFF) return true;
        }
        return false;
    }

    private void applyTextStyle(TextPaint paint, String text) {
        paint.setTypeface(getAppropriateTypeface(text));
        paint.setTextAlign(isRTL ? Paint.Align.RIGHT : Paint.Align.LEFT);
    }

    private String getString(int resId) {
        try {
            return localizedResources.getString(resId);
        } catch (Exception e) {
            return context.getString(resId);
        }
    }

    private String formatCurrency(double amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(2);
        return format.format(amount);
    }

    private String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return sdf.format(new Date(timestamp));
    }

    // ===================== الدالة الرئيسية للتوليد =====================

    public File generateReportImage(
            int reportType, boolean isSingleAccount, boolean isAccountType, boolean isAllAccounts,
            Account account, String accountTypeId, int currencyId,
            boolean specificCurrency, long startDate, long endDate,
            boolean useDateRange, boolean separateAccounts) throws Exception {

        // 1. جلب البيانات حسب النطاق ونوع التقرير
        List<ReportSection> sections = new ArrayList<>();
        String mainTitle = "";
        String subTitle = "";

        if (isSingleAccount && account != null) {
            mainTitle = getString(R.string.report_title_detailed_statement);
            subTitle = getString(R.string.report_label_account) + ": " + account.getAccountName();

            if (reportType == 1) { // كشف حركة تفصيلي
                if (specificCurrency) {
                    List<Transaction> txs = repository.getTransactionsForAccountBlocking(account.getId(), currencyId);
                    if (txs != null && !txs.isEmpty())
                        sections.add(ReportSection.forTransactions(account.getAccountName(), txs));
                } else {
                    List<Currency> currencies = repository.getAllCurrenciesBlocking();
                    for (Currency c : currencies) {
                        List<Transaction> txs = repository.getTransactionsForAccountBlocking(account.getId(), c.id);
                        if (txs != null && !txs.isEmpty())
                            sections.add(ReportSection.forTransactions(account.getAccountName() + " - " + c.name, txs));
                    }
                }
            } else if (reportType == 2) { // الرصيد
                String currencyName = specificCurrency ? repository.getCurrencyNameById(currencyId) : getString(R.string.report_label_all_currencies);
                if (specificCurrency) {
                    List<AccountBalanceSummary> summaries = repository.getAccountBalances(account.getId(), currencyId, startDate, endDate);
                    if (summaries != null && !summaries.isEmpty())
                        sections.add(ReportSection.forBalances(account.getAccountName(), summaries, currencyName));
                } else {
                    List<Currency> currencies = repository.getAllCurrenciesBlocking();
                    for (Currency c : currencies) {
                        List<AccountBalanceSummary> summaries = repository.getAccountBalances(account.getId(), c.id, startDate, endDate);
                        if (summaries != null && !summaries.isEmpty())
                            sections.add(ReportSection.forBalances(account.getAccountName() + " - " + c.name, summaries, c.name));
                    }
                }
            } else if (reportType == 3) { // شهري
                if (specificCurrency) {
                    List<MonthlySummary> summaries = repository.getMonthlySummaryForAccountAllCurrencies(account.getId(), currencyId);
                    if (summaries != null && !summaries.isEmpty())
                        sections.add(ReportSection.forMonthly(account.getAccountName(), summaries));
                } else {
                    List<Currency> currencies = repository.getAllCurrenciesBlocking();
                    for (Currency c : currencies) {
                        List<MonthlySummary> summaries = repository.getMonthlySummaryForAccountAllCurrencies(account.getId(), c.id);
                        if (summaries != null && !summaries.isEmpty())
                            sections.add(ReportSection.forMonthly(account.getAccountName() + " - " + c.name, summaries));
                    }
                }
            }
        } else if (isAccountType && accountTypeId != null) {
            String typeName = repository.getAccountTypeNameByFirestoreId(accountTypeId);
            mainTitle = getString(R.string.report_title_detailed_statement);
            subTitle = getString(R.string.report_label_account_type) + ": " + typeName;

            if (reportType == 1) { // حركة تفصيلية
                if (separateAccounts) {
                    // عرض كل حساب على حدة
                    List<Account> accounts = repository.getAccountsByTypeBlocking(accountTypeId);
                    if (specificCurrency) {
                        for (Account acc : accounts) {
                            List<Transaction> txs = repository.getTransactionsForAccountBlocking(acc.getId(), currencyId);
                            if (txs != null && !txs.isEmpty())
                                sections.add(ReportSection.forTransactions(acc.getAccountName(), txs));
                        }
                    } else {
                        List<Currency> currencies = repository.getAllCurrenciesBlocking();
                        for (Account acc : accounts) {
                            for (Currency c : currencies) {
                                List<Transaction> txs = repository.getTransactionsForAccountBlocking(acc.getId(), c.id);
                                if (txs != null && !txs.isEmpty())
                                    sections.add(ReportSection.forTransactions(acc.getAccountName() + " - " + c.name, txs));
                            }
                        }
                    }
                } else {
                    // مجمع
                    if (specificCurrency) {
                        List<TransactionWithAccount> txs = repository.getConsolidatedMovementAllCurrencies(accountTypeId, currencyId, startDate, endDate);
                        if (txs != null && !txs.isEmpty())
                            sections.add(ReportSection.forConsolidated(typeName, txs));
                    } else {
                        List<Currency> currencies = repository.getAllCurrenciesBlocking();
                        for (Currency c : currencies) {
                            List<TransactionWithAccount> txs = repository.getConsolidatedMovementAllCurrencies(accountTypeId, c.id, startDate, endDate);
                            if (txs != null && !txs.isEmpty())
                                sections.add(ReportSection.forConsolidated(typeName + " - " + c.name, txs));
                        }
                    }
                }
            } else if (reportType == 2) { // الرصيد
                if (specificCurrency) {
                    List<AccountBalanceSummary> summaries = repository.getAccountBalancesByType(accountTypeId, currencyId, startDate, endDate);
                    if (summaries != null && !summaries.isEmpty())
                        sections.add(ReportSection.forBalances(typeName, summaries, repository.getCurrencyNameById(currencyId)));
                } else {
                    List<Currency> currencies = repository.getAllCurrenciesBlocking();
                    for (Currency c : currencies) {
                        List<AccountBalanceSummary> summaries = repository.getAccountBalancesByType(accountTypeId, c.id, startDate, endDate);
                        if (summaries != null && !summaries.isEmpty())
                            sections.add(ReportSection.forBalances(typeName + " - " + c.name, summaries, c.name));
                    }
                }
            } else if (reportType == 3) { // شهري
                if (specificCurrency) {
                    List<MonthlySummary> summaries = repository.getMonthlySummaryByAccountTypeByCurrency(accountTypeId, currencyId);
                    if (summaries != null && !summaries.isEmpty())
                        sections.add(ReportSection.forMonthly(typeName, summaries));
                } else {
                    List<Currency> currencies = repository.getAllCurrenciesBlocking();
                    for (Currency c : currencies) {
                        List<MonthlySummary> summaries = repository.getMonthlySummaryByAccountTypeByCurrency(accountTypeId, c.id);
                        if (summaries != null && !summaries.isEmpty())
                            sections.add(ReportSection.forMonthly(typeName + " - " + c.name, summaries));
                    }
                }
            }
        } else if (isAllAccounts) {
            // يمكنك إضافة منطق جميع الحسابات هنا إن أردت
            mainTitle = getString(R.string.report_title_consolidated_movement);
            subTitle = getString(R.string.report_label_all_accounts);
            // ... (اختياري)
        }

        if (sections.isEmpty()) {
            return null;
        }

        // 2. حساب الارتفاع الكلي للصورة
        int totalHeight = HEADER_HEIGHT + FOOTER_HEIGHT + 100;
        for (ReportSection section : sections) {
            totalHeight += 60; // عنوان القسم
            if (section.transactions != null) {
                totalHeight += TABLE_HEADER_HEIGHT + (section.transactions.size() * ROW_HEIGHT) + ROW_HEIGHT; // + صف المجموع
            } else if (section.balances != null) {
                totalHeight += TABLE_HEADER_HEIGHT + (section.balances.size() * ROW_HEIGHT) + ROW_HEIGHT;
            } else if (section.monthlySummaries != null) {
                totalHeight += TABLE_HEADER_HEIGHT + (section.monthlySummaries.size() * ROW_HEIGHT) + ROW_HEIGHT;
            } else if (section.consolidatedTransactions != null) {
                totalHeight += TABLE_HEADER_HEIGHT + (section.consolidatedTransactions.size() * ROW_HEIGHT) + ROW_HEIGHT;
            }
            totalHeight += 30; // مسافة بين الأقسام
        }

        // 3. إنشاء الصورة
        Bitmap bitmap = Bitmap.createBitmap(PAGE_WIDTH, totalHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);

        int currentY = drawHeader(canvas, mainTitle, subTitle, startDate, endDate, useDateRange);
        currentY += 20;

        // 4. رسم البيانات
        for (ReportSection section : sections) {
            // عنوان القسم
            Paint sectionPaint = new Paint();
            sectionPaint.setColor(primaryColor);
            sectionPaint.setTextSize(30);
            sectionPaint.setFakeBoldText(true);
            applyTextStyle((TextPaint) sectionPaint, section.title);
            canvas.drawText(section.title, isRTL ? PAGE_WIDTH - MARGIN : MARGIN, currentY, sectionPaint);
            currentY += 50;

            if (section.transactions != null) {
                currentY = drawTransactionTable(canvas, section.transactions, currentY, startDate, useDateRange);
            } else if (section.balances != null) {
                currentY = drawBalancesTable(canvas, section.balances, currentY, section.currencyName);
            } else if (section.monthlySummaries != null) {
                currentY = drawMonthlyTable(canvas, section.monthlySummaries, currentY);
            } else if (section.consolidatedTransactions != null) {
                currentY = drawConsolidatedTransactionTable(canvas, section.consolidatedTransactions, currentY, startDate, useDateRange);
            }
            currentY += 30;
        }

        // 5. الفوتر
        drawFooter(canvas, totalHeight);

        // 6. حفظ الصورة
        File outputFile = new File(context.getCacheDir(), "report_image_" + System.currentTimeMillis() + ".png");
        FileOutputStream fos = new FileOutputStream(outputFile);
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        fos.close();
        bitmap.recycle();

        return outputFile;
    }

    // ===================== دوال الرسم المساعدة =====================

    private int drawHeader(Canvas canvas, String title, String subTitle, long start, long end, boolean useDateRange) {
        int startY = 0;
        int y = startY + 20;

        // خلفية الهيدر
        canvas.drawRect(0, startY, PAGE_WIDTH, startY + HEADER_HEIGHT, headerBgPaint);

        // اسم المستخدم واسم الشركة
        if (userProfile != null) {
            TextPaint namePaint = new TextPaint(headerTextPaint);
            applyTextStyle(namePaint, userProfile.getName());
            namePaint.setTextSize(32);
            canvas.drawText(userProfile.getName(), isRTL ? PAGE_WIDTH - MARGIN : MARGIN, y + 50, namePaint);

            if (userProfile.getCompany() != null) {
                TextPaint companyPaint = new TextPaint(headerTextPaint);
                applyTextStyle(companyPaint, userProfile.getCompany());
                companyPaint.setTextSize(28);
                canvas.drawText(userProfile.getCompany(), isRTL ? MARGIN : PAGE_WIDTH - MARGIN, y + 50, companyPaint);
            }
        }

        y += 70;

        // العنوان الرئيسي
        TextPaint titleP = new TextPaint(titlePaint);
        applyTextStyle(titleP, title);
        canvas.drawText(title, PAGE_WIDTH / 2f, y, titleP);

        y += 40;

        // العنوان الفرعي والفترة
        StringBuilder subText = new StringBuilder();
        if (subTitle != null) subText.append(subTitle);
        if (useDateRange) {
            subText.append(" | ").append(getString(R.string.report_label_from)).append(" ").append(formatDate(start))
                    .append(" ").append(getString(R.string.report_label_to)).append(" ").append(formatDate(end));
        } else {
            subText.append(" | ").append(getString(R.string.report_label_all_periods));
        }

        TextPaint subP = new TextPaint(subTitlePaint);
        applyTextStyle(subP, subText.toString());
        canvas.drawText(subText.toString(), PAGE_WIDTH / 2f, y, subP);

        // خط فاصل
        canvas.drawLine(0, startY + HEADER_HEIGHT - 2, PAGE_WIDTH, startY + HEADER_HEIGHT - 2, linePaint);

        return startY + HEADER_HEIGHT;
    }

    private void drawFooter(Canvas canvas, int pageHeight) {
        TextPaint footerP = new TextPaint(footerPaint);
        applyTextStyle(footerP, "");
        String footerText = getString(R.string.document_was_created_by) + " " + getString(R.string.app_name_report);
        footerText += " | " + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());

        float x = isRTL ? MARGIN : PAGE_WIDTH - MARGIN;
        canvas.drawText(footerText, x, pageHeight - 20, footerP);
    }

    // ----------------- جداول الحركة -----------------
    private int drawTransactionTable(Canvas canvas, List<Transaction> transactions, int startY, long filterStart, boolean useDateRange) {
        if (transactions == null || transactions.isEmpty()) {
            TextPaint noDataP = new TextPaint(cellTextPaint);
            applyTextStyle(noDataP, getString(R.string.report_no_data));
            canvas.drawText(getString(R.string.report_no_data), PAGE_WIDTH / 2f, startY + 30, noDataP);
            return startY + ROW_HEIGHT;
        }

        String[] headers;
        float[] columnWidths;
        if (isRTL) {
            headers = new String[]{
                    getString(R.string.report_label_balance),
                    getString(R.string.header_credit_you),
                    getString(R.string.header_debit_you),
                    getString(R.string.report_label_details),
                    getString(R.string.report_label_date)
            };
            columnWidths = new float[]{0.22f, 0.18f, 0.18f, 0.25f, 0.17f};
        } else {
            headers = new String[]{
                    getString(R.string.report_label_date),
                    getString(R.string.report_label_details),
                    getString(R.string.header_debit_you),
                    getString(R.string.header_credit_you),
                    getString(R.string.report_label_balance)
            };
            columnWidths = new float[]{0.17f, 0.25f, 0.18f, 0.18f, 0.22f};
        }

        int y = startY;
        float x = MARGIN;

        // رأس الجدول
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + TABLE_HEADER_HEIGHT, tableHeaderBgPaint);
        for (int i = 0; i < headers.length; i++) {
            float colWidth = columnWidths[i] * (PAGE_WIDTH - 2 * MARGIN);
            TextPaint p = new TextPaint(tableHeaderTextPaint);
            applyTextStyle(p, headers[i]);
            p.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(headers[i], x + colWidth / 2, y + TABLE_HEADER_HEIGHT - 12, p);
            x += colWidth;
        }
        y += TABLE_HEADER_HEIGHT;

        // الصفوف
        double runningBalance = 0;
        for (int i = 0; i < transactions.size(); i++) {
            Transaction tx = transactions.get(i);
            runningBalance += tx.getAmount() * tx.getType();

            boolean isEven = i % 2 == 0;
            canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + ROW_HEIGHT, isEven ? evenRowPaint : oddRowPaint);

            String[] rowData;
            if (isRTL) {
                rowData = new String[]{
                        formatCurrency(runningBalance),
                        tx.getType() == -1 ? formatCurrency(tx.getAmount()) : "0",
                        tx.getType() == 1 ? formatCurrency(tx.getAmount()) : "0",
                        tx.getDetails(),
                        formatDate(tx.getTimestamp().getTime())
                };
            } else {
                rowData = new String[]{
                        formatDate(tx.getTimestamp().getTime()),
                        tx.getDetails(),
                        tx.getType() == 1 ? formatCurrency(tx.getAmount()) : "0",
                        tx.getType() == -1 ? formatCurrency(tx.getAmount()) : "0",
                        formatCurrency(runningBalance)
                };
            }

            x = MARGIN;
            for (int j = 0; j < rowData.length; j++) {
                float colWidth = columnWidths[j] * (PAGE_WIDTH - 2 * MARGIN);
                TextPaint p;
                if (j == 2 && tx.getType() == 1) { // مدين
                    p = new TextPaint(redPaint);
                } else if (j == 3 && tx.getType() == -1) { // دائن
                    p = new TextPaint(greenPaint);
                } else {
                    p = new TextPaint(cellTextPaint);
                }
                applyTextStyle(p, rowData[j]);
                p.setTextAlign(Paint.Align.CENTER);

                // قص النص الطويل
                String displayText = rowData[j];
                Rect bounds = new Rect();
                p.getTextBounds(displayText, 0, displayText.length(), bounds);
                if (bounds.width() > colWidth - 20) {
                    displayText = TextUtils.ellipsize(displayText, p, colWidth - 20, TextUtils.TruncateAt.END).toString();
                }
                canvas.drawText(displayText, x + colWidth / 2, y + ROW_HEIGHT - 14, p);
                x += colWidth;
            }
            y += ROW_HEIGHT;
        }

        // صف المجاميع
        double totalDebit = 0, totalCredit = 0;
        for (Transaction tx : transactions) {
            if (tx.getType() == 1) totalDebit += tx.getAmount();
            else totalCredit += tx.getAmount();
        }

        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + ROW_HEIGHT, lightCyanPaint);
        String[] totalRow;
        if (isRTL) {
            totalRow = new String[]{
                    formatCurrency(runningBalance),
                    formatCurrency(totalCredit),
                    formatCurrency(totalDebit),
                    getString(R.string.report_label_totals),
                    ""
            };
        } else {
            totalRow = new String[]{
                    "",
                    getString(R.string.report_label_totals),
                    formatCurrency(totalDebit),
                    formatCurrency(totalCredit),
                    formatCurrency(runningBalance)
            };
        }

        x = MARGIN;
        for (int j = 0; j < totalRow.length; j++) {
            float colWidth = columnWidths[j] * (PAGE_WIDTH - 2 * MARGIN);
            TextPaint p = new TextPaint(totalLabelPaint);
            applyTextStyle(p, totalRow[j]);
            p.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(totalRow[j], x + colWidth / 2, y + ROW_HEIGHT - 14, p);
            x += colWidth;
        }
        y += ROW_HEIGHT;

        return y;
    }

    // ----------------- جداول الأرصدة -----------------
    private int drawBalancesTable(Canvas canvas, List<AccountBalanceSummary> summaries, int startY, String currencyName) {
        if (summaries == null || summaries.isEmpty()) {
            TextPaint noDataP = new TextPaint(cellTextPaint);
            applyTextStyle(noDataP, getString(R.string.report_no_data));
            canvas.drawText(getString(R.string.report_no_data), PAGE_WIDTH / 2f, startY + 30, noDataP);
            return startY + ROW_HEIGHT;
        }

        String[] headers;
        float[] columnWidths;
        if (isRTL) {
            headers = new String[]{
                    getString(R.string.report_label_last_transaction_date),
                    getString(R.string.report_label_balance),
                    getString(R.string.report_label_balance_type),
                    getString(R.string.report_label_account_name)
            };
            columnWidths = new float[]{0.22f, 0.25f, 0.25f, 0.28f};
        } else {
            headers = new String[]{
                    getString(R.string.report_label_account_name),
                    getString(R.string.report_label_balance_type),
                    getString(R.string.report_label_balance),
                    getString(R.string.report_label_last_transaction_date)
            };
            columnWidths = new float[]{0.28f, 0.25f, 0.25f, 0.22f};
        }

        int y = startY;
        float x = MARGIN;

        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + TABLE_HEADER_HEIGHT, tableHeaderBgPaint);
        for (int i = 0; i < headers.length; i++) {
            float colWidth = columnWidths[i] * (PAGE_WIDTH - 2 * MARGIN);
            TextPaint p = new TextPaint(tableHeaderTextPaint);
            applyTextStyle(p, headers[i]);
            p.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(headers[i], x + colWidth / 2, y + TABLE_HEADER_HEIGHT - 12, p);
            x += colWidth;
        }
        y += TABLE_HEADER_HEIGHT;

        double totalBalance = 0;
        for (int i = 0; i < summaries.size(); i++) {
            AccountBalanceSummary s = summaries.get(i);
            totalBalance += s.balance;
            boolean isEven = i % 2 == 0;
            canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + ROW_HEIGHT, isEven ? evenRowPaint : oddRowPaint);

            String balanceType = s.balance >= 0 ? getString(R.string.report_balance_type_them) : getString(R.string.report_balance_type_you);
            String balanceStr = formatCurrency(Math.abs(s.balance));

            String[] rowData;
            if (isRTL) {
                rowData = new String[]{
                        formatDate(s.lastTransactionDate.getTime()),
                        balanceStr,
                        balanceType,
                        s.accountName
                };
            } else {
                rowData = new String[]{
                        s.accountName,
                        balanceType,
                        balanceStr,
                        formatDate(s.lastTransactionDate.getTime())
                };
            }

            x = MARGIN;
            for (int j = 0; j < rowData.length; j++) {
                float colWidth = columnWidths[j] * (PAGE_WIDTH - 2 * MARGIN);
                TextPaint p;
                if (j == 2 || (isRTL && j == 1)) {
                    p = s.balance >= 0 ? new TextPaint(redPaint) : new TextPaint(greenPaint);
                } else {
                    p = new TextPaint(cellTextPaint);
                }
                applyTextStyle(p, rowData[j]);
                p.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(rowData[j], x + colWidth / 2, y + ROW_HEIGHT - 14, p);
                x += colWidth;
            }
            y += ROW_HEIGHT;
        }

        // صف المجموع
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + ROW_HEIGHT, lightCyanPaint);
        String totalLabel = getString(R.string.report_label_totals) + " (" + currencyName + ")";
        String totalVal = formatCurrency(Math.abs(totalBalance));
        TextPaint labelP = new TextPaint(totalLabelPaint);
        TextPaint valP = new TextPaint(totalValuePaint);
        applyTextStyle(labelP, totalLabel);
        applyTextStyle(valP, totalVal);
        labelP.setTextAlign(Paint.Align.CENTER);
        valP.setTextAlign(Paint.Align.CENTER);

        if (isRTL) {
            canvas.drawText(totalLabel, MARGIN + (columnWidths[0] * (PAGE_WIDTH - 2 * MARGIN)) / 2, y + ROW_HEIGHT - 14, labelP);
            canvas.drawText(totalVal, MARGIN + (columnWidths[1] * (PAGE_WIDTH - 2 * MARGIN)) / 2, y + ROW_HEIGHT - 14, valP);
        } else {
            canvas.drawText(totalLabel, MARGIN + (columnWidths[0] * (PAGE_WIDTH - 2 * MARGIN)) / 2, y + ROW_HEIGHT - 14, labelP);
            canvas.drawText(totalVal, MARGIN + (columnWidths[2] * (PAGE_WIDTH - 2 * MARGIN)) / 2, y + ROW_HEIGHT - 14, valP);
        }
        y += ROW_HEIGHT;

        return y;
    }

    // ----------------- جداول شهرية -----------------
    private int drawMonthlyTable(Canvas canvas, List<MonthlySummary> summaries, int startY) {
        if (summaries == null || summaries.isEmpty()) {
            TextPaint noDataP = new TextPaint(cellTextPaint);
            applyTextStyle(noDataP, getString(R.string.report_no_data));
            canvas.drawText(getString(R.string.report_no_data), PAGE_WIDTH / 2f, startY + 30, noDataP);
            return startY + ROW_HEIGHT;
        }

        String[] headers;
        float[] columnWidths;
        if (isRTL) {
            headers = new String[]{
                    getString(R.string.report_label_final_balance),
                    getString(R.string.report_label_net_movement),
                    getString(R.string.report_label_credit),
                    getString(R.string.report_label_debit),
                    getString(R.string.report_label_month)
            };
            columnWidths = new float[]{0.22f, 0.2f, 0.2f, 0.2f, 0.18f};
        } else {
            headers = new String[]{
                    getString(R.string.report_label_month),
                    getString(R.string.report_label_debit),
                    getString(R.string.report_label_credit),
                    getString(R.string.report_label_net_movement),
                    getString(R.string.report_label_final_balance)
            };
            columnWidths = new float[]{0.18f, 0.2f, 0.2f, 0.2f, 0.22f};
        }

        int y = startY;
        float x = MARGIN;

        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + TABLE_HEADER_HEIGHT, tableHeaderBgPaint);
        for (int i = 0; i < headers.length; i++) {
            float colWidth = columnWidths[i] * (PAGE_WIDTH - 2 * MARGIN);
            TextPaint p = new TextPaint(tableHeaderTextPaint);
            applyTextStyle(p, headers[i]);
            p.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(headers[i], x + colWidth / 2, y + TABLE_HEADER_HEIGHT - 12, p);
            x += colWidth;
        }
        y += TABLE_HEADER_HEIGHT;

        double runningBalance = 0;
        for (int i = 0; i < summaries.size(); i++) {
            MonthlySummary s = summaries.get(i);
            double net = s.totalDebit - s.totalCredit;
            runningBalance += net;
            boolean isEven = i % 2 == 0;
            canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + ROW_HEIGHT, isEven ? evenRowPaint : oddRowPaint);

            String[] rowData;
            if (isRTL) {
                rowData = new String[]{
                        formatCurrency(runningBalance),
                        formatCurrency(net),
                        formatCurrency(s.totalCredit),
                        formatCurrency(s.totalDebit),
                        s.yearMonth
                };
            } else {
                rowData = new String[]{
                        s.yearMonth,
                        formatCurrency(s.totalDebit),
                        formatCurrency(s.totalCredit),
                        formatCurrency(net),
                        formatCurrency(runningBalance)
                };
            }

            x = MARGIN;
            for (int j = 0; j < rowData.length; j++) {
                float colWidth = columnWidths[j] * (PAGE_WIDTH - 2 * MARGIN);
                TextPaint p = new TextPaint(cellTextPaint);
                applyTextStyle(p, rowData[j]);
                p.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(rowData[j], x + colWidth / 2, y + ROW_HEIGHT - 14, p);
                x += colWidth;
            }
            y += ROW_HEIGHT;
        }

        return y;
    }

    // ----------------- جداول الحركة المجمعة (مع اسم الحساب) -----------------
    private int drawConsolidatedTransactionTable(Canvas canvas, List<TransactionWithAccount> transactions, int startY, long filterStart, boolean useDateRange) {
        if (transactions == null || transactions.isEmpty()) {
            TextPaint noDataP = new TextPaint(cellTextPaint);
            applyTextStyle(noDataP, getString(R.string.report_no_data));
            canvas.drawText(getString(R.string.report_no_data), PAGE_WIDTH / 2f, startY + 30, noDataP);
            return startY + ROW_HEIGHT;
        }

        String[] headers;
        float[] columnWidths;
        if (isRTL) {
            headers = new String[]{
                    getString(R.string.report_label_cumulative_balance),
                    getString(R.string.report_label_credit),
                    getString(R.string.report_label_debit),
                    getString(R.string.report_label_details),
                    getString(R.string.report_label_account_name),
                    getString(R.string.report_label_date)
            };
            columnWidths = new float[]{0.18f, 0.15f, 0.15f, 0.20f, 0.18f, 0.14f};
        } else {
            headers = new String[]{
                    getString(R.string.report_label_date),
                    getString(R.string.report_label_account_name),
                    getString(R.string.report_label_details),
                    getString(R.string.report_label_debit),
                    getString(R.string.report_label_credit),
                    getString(R.string.report_label_cumulative_balance)
            };
            columnWidths = new float[]{0.14f, 0.18f, 0.20f, 0.15f, 0.15f, 0.18f};
        }

        int y = startY;
        float x = MARGIN;

        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + TABLE_HEADER_HEIGHT, tableHeaderBgPaint);
        for (int i = 0; i < headers.length; i++) {
            float colWidth = columnWidths[i] * (PAGE_WIDTH - 2 * MARGIN);
            TextPaint p = new TextPaint(tableHeaderTextPaint);
            applyTextStyle(p, headers[i]);
            p.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(headers[i], x + colWidth / 2, y + TABLE_HEADER_HEIGHT - 12, p);
            x += colWidth;
        }
        y += TABLE_HEADER_HEIGHT;

        double runningBalance = 0;
        for (int i = 0; i < transactions.size(); i++) {
            TransactionWithAccount twa = transactions.get(i);
            Transaction tx = twa.transaction;
            runningBalance += tx.getAmount() * tx.getType();

            boolean isEven = i % 2 == 0;
            canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + ROW_HEIGHT, isEven ? evenRowPaint : oddRowPaint);

            String[] rowData;
            if (isRTL) {
                rowData = new String[]{
                        formatCurrency(runningBalance),
                        tx.getType() == -1 ? formatCurrency(tx.getAmount()) : "0",
                        tx.getType() == 1 ? formatCurrency(tx.getAmount()) : "0",
                        tx.getDetails(),
                        twa.accountName,
                        formatDate(tx.getTimestamp().getTime())
                };
            } else {
                rowData = new String[]{
                        formatDate(tx.getTimestamp().getTime()),
                        twa.accountName,
                        tx.getDetails(),
                        tx.getType() == 1 ? formatCurrency(tx.getAmount()) : "0",
                        tx.getType() == -1 ? formatCurrency(tx.getAmount()) : "0",
                        formatCurrency(runningBalance)
                };
            }

            x = MARGIN;
            for (int j = 0; j < rowData.length; j++) {
                float colWidth = columnWidths[j] * (PAGE_WIDTH - 2 * MARGIN);
                TextPaint p;
                if (j == 3 && tx.getType() == 1) {
                    p = new TextPaint(redPaint);
                } else if (j == 4 && tx.getType() == -1) {
                    p = new TextPaint(greenPaint);
                } else {
                    p = new TextPaint(cellTextPaint);
                }
                applyTextStyle(p, rowData[j]);
                p.setTextAlign(Paint.Align.CENTER);

                String displayText = rowData[j];
                Rect bounds = new Rect();
                p.getTextBounds(displayText, 0, displayText.length(), bounds);
                if (bounds.width() > colWidth - 20) {
                    displayText = TextUtils.ellipsize(displayText, p, colWidth - 20, TextUtils.TruncateAt.END).toString();
                }
                canvas.drawText(displayText, x + colWidth / 2, y + ROW_HEIGHT - 14, p);
                x += colWidth;
            }
            y += ROW_HEIGHT;
        }

        // صف المجاميع
        double totalDebit = 0, totalCredit = 0;
        for (TransactionWithAccount twa : transactions) {
            if (twa.transaction.getType() == 1) totalDebit += twa.transaction.getAmount();
            else totalCredit += twa.transaction.getAmount();
        }

        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + ROW_HEIGHT, lightCyanPaint);
        String[] totalRow;
        if (isRTL) {
            totalRow = new String[]{
                    formatCurrency(runningBalance),
                    formatCurrency(totalCredit),
                    formatCurrency(totalDebit),
                    getString(R.string.report_label_totals),
                    "",
                    ""
            };
        } else {
            totalRow = new String[]{
                    "",
                    "",
                    getString(R.string.report_label_totals),
                    formatCurrency(totalDebit),
                    formatCurrency(totalCredit),
                    formatCurrency(runningBalance)
            };
        }

        x = MARGIN;
        for (int j = 0; j < totalRow.length; j++) {
            float colWidth = columnWidths[j] * (PAGE_WIDTH - 2 * MARGIN);
            TextPaint p = new TextPaint(totalLabelPaint);
            applyTextStyle(p, totalRow[j]);
            p.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(totalRow[j], x + colWidth / 2, y + ROW_HEIGHT - 14, p);
            x += colWidth;
        }
        y += ROW_HEIGHT;

        return y;
    }

    // ===================== كلاس ReportSection باستخدام طرق مصنع ثابتة =====================

    private static class ReportSection {
        String title;
        List<Transaction> transactions;
        List<AccountBalanceSummary> balances;
        List<MonthlySummary> monthlySummaries;
        List<TransactionWithAccount> consolidatedTransactions;
        String currencyName;

        private ReportSection() {}

        static ReportSection forTransactions(String title, List<Transaction> transactions) {
            ReportSection s = new ReportSection();
            s.title = title;
            s.transactions = transactions;
            return s;
        }

        static ReportSection forBalances(String title, List<AccountBalanceSummary> balances, String currencyName) {
            ReportSection s = new ReportSection();
            s.title = title;
            s.balances = balances;
            s.currencyName = currencyName;
            return s;
        }

        static ReportSection forMonthly(String title, List<MonthlySummary> monthlySummaries) {
            ReportSection s = new ReportSection();
            s.title = title;
            s.monthlySummaries = monthlySummaries;
            return s;
        }

        static ReportSection forConsolidated(String title, List<TransactionWithAccount> consolidatedTransactions) {
            ReportSection s = new ReportSection();
            s.title = title;
            s.consolidatedTransactions = consolidatedTransactions;
            return s;
        }
    }
}