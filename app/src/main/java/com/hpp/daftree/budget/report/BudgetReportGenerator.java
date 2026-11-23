package com.hpp.daftree.budget.report;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

import com.hpp.daftree.R;
import com.hpp.daftree.budget.data.BudgetEntry;
import com.hpp.daftree.budget.data.BudgetEntryDao;
import com.hpp.daftree.budget.data.BudgetEntryWithCategory;
import com.hpp.daftree.budget.data.CategoryExpenseTotal;
import com.hpp.daftree.budget.data.MonthlyBudgetLimit;
import com.hpp.daftree.budget.data.MonthlyBudgetLimitDao;
import com.hpp.daftree.budget.data.MonthlyTotals;
import com.hpp.daftree.budget.util.BudgetUtils;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.User;
import com.hpp.daftree.helpers.LanguageHelperForReports;
import com.hpp.daftree.helpers.LanguageManager;
import com.hpp.daftree.models.ReportHeaderFooter;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.FontSelector;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BudgetReportGenerator {

    private static final String TAG = "BudgetReportGenerator";

    private final Context context;
    private final AppDatabase db;
    private final BudgetEntryDao entryDao;
    private final MonthlyBudgetLimitDao limitDao;
    private final User userProfile;
    private final boolean isRTL;
    private final boolean useEnglishOnly;
    private final Locale currentLocale;
    private final Context localizedContext;
    private final Resources localizedResources;

    private BaseFont arabicFont;
    private BaseFont latinFont;
    private BaseFont defaultFont;

    private Font titleFont, sectionFont, subTitleFont, tableHeaderFont, normalFont, totalsFont, redFont, greenFont, tealFont;

    private final BaseColor primaryTeal = new BaseColor(13, 110, 110);
    private final BaseColor headerBgColor = new BaseColor(230, 240, 240);
    private final BaseColor lightCyan = new BaseColor(224, 247, 250);
    private final BaseColor evenRowColor = new BaseColor(248, 250, 252);
    private final BaseColor highlightYellow = new BaseColor(254, 243, 199);

    public BudgetReportGenerator(Context context, User userProfile, String language) {
        this.context = context.getApplicationContext();
        this.db = AppDatabase.getDatabase(this.context);
        this.entryDao = db.budgetEntryDao();
        this.limitDao = db.monthlyBudgetLimitDao();
        this.userProfile = userProfile;

        this.useEnglishOnly = LanguageHelperForReports.isLanguageInRestrictedArea(language);
        this.localizedResources = LanguageHelperForReports.getAppropriateResources(this.context, language);
        this.currentLocale = LanguageHelperForReports.getAppropriateLocale(language);
        this.localizedContext = LanguageHelperForReports.createLocalizedContext(this.context, language);
        this.isRTL = !useEnglishOnly && LanguageManager.getInstance(this.context).isCodeRTL(language);

        initFonts();
    }

    private void initFonts() {
        try {
            try (InputStream is = context.getResources().openRawResource(R.font.cairo_regular)) {
                byte[] buf = new byte[is.available()];
                int ignored = is.read(buf);
                arabicFont = BaseFont.createFont("cairo_regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, buf, null);
            } catch (Exception e) {
                arabicFont = null;
            }
            try {
                latinFont = BaseFont.createFont("assets/NotoSans.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (Exception e) {
                latinFont = null;
            }
            defaultFont = latinFont != null ? latinFont
                    : BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.EMBEDDED);

            this.titleFont = new Font(defaultFont, 16, Font.BOLD, primaryTeal);
            this.sectionFont = new Font(defaultFont, 12, Font.BOLD, primaryTeal);
            this.subTitleFont = new Font(defaultFont, 10, Font.NORMAL, BaseColor.DARK_GRAY);
            this.tableHeaderFont = new Font(defaultFont, 10, Font.BOLD, new BaseColor(15, 76, 92));
            this.normalFont = new Font(defaultFont, 9, Font.NORMAL, BaseColor.BLACK);
            this.totalsFont = new Font(defaultFont, 10, Font.BOLD, BaseColor.BLACK);
            this.redFont = new Font(defaultFont, 9, Font.BOLD, new BaseColor(220, 38, 38));
            this.greenFont = new Font(defaultFont, 9, Font.BOLD, new BaseColor(22, 163, 74));
            this.tealFont = new Font(defaultFont, 9, Font.BOLD, primaryTeal);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize BudgetReportGenerator fonts", e);
        }
    }

    private Phrase createPhrase(String text, Font styleFont) {
        if (text == null || text.isEmpty()) return new Phrase("", styleFont);
        try {
            FontSelector selector = new FontSelector();
            if (useEnglishOnly) {
                selector.addFont(new Font(defaultFont, styleFont.getSize(), styleFont.getStyle(), styleFont.getColor()));
            } else if (isRTL) {
                if (arabicFont != null && containsArabic(text)) {
                    selector.addFont(new Font(arabicFont, styleFont.getSize(), styleFont.getStyle(), styleFont.getColor()));
                }
                if (latinFont != null) {
                    selector.addFont(new Font(latinFont, styleFont.getSize(), styleFont.getStyle(), styleFont.getColor()));
                }
            } else {
                if (latinFont != null) {
                    selector.addFont(new Font(latinFont, styleFont.getSize(), styleFont.getStyle(), styleFont.getColor()));
                }
                if (arabicFont != null && containsArabic(text)) {
                    selector.addFont(new Font(arabicFont, styleFont.getSize(), styleFont.getStyle(), styleFont.getColor()));
                }
            }
            selector.addFont(new Font(defaultFont, styleFont.getSize(), styleFont.getStyle(), styleFont.getColor()));
            return selector.process(text);
        } catch (Exception e) {
            return new Phrase(text, styleFont);
        }
    }

    private boolean containsArabic(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 0x0600 && c <= 0x06FF) || (c >= 0xFE70 && c <= 0xFEFF)) return true;
        }
        return false;
    }

    private String str(int resId) {
        try {
            return localizedResources.getString(resId);
        } catch (Exception e) {
            return context.getString(resId);
        }
    }

    private Document createDocument(File file) throws Exception {
        Document document = new Document(PageSize.A4, 36, 36, 120, 40);
        PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(file));
        if (userProfile != null) {
            writer.setPageEvent(new ReportHeaderFooter(localizedContext, userProfile, currentLocale, isRTL, localizedResources));
        }
        writer.setRunDirection(isRTL ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
        return document;
    }

    private List<Currency> resolveTargetCurrencies(int currencyId, boolean specificCurrency) {
        List<Currency> all = db.currencyDao().getAllCurrenciesBlocking();
        List<Currency> result = new ArrayList<>();
        if (all == null) return result;
        if (specificCurrency && currencyId > 0) {
            for (Currency c : all) {
                if (c.getId() == currencyId) {
                    result.add(c);
                    break;
                }
            }
        } else {
            result.addAll(all);
        }
        return result;
    }

    private double resolveEffectiveLimit(List<MonthlyBudgetLimit> limitsAsc, String yearMonth) {
        double effective = 0.0;
        if (limitsAsc == null || yearMonth == null) return 0.0;
        for (MonthlyBudgetLimit l : limitsAsc) {
            if (l.getYearMonth() != null && l.getYearMonth().compareTo(yearMonth) <= 0) {
                effective = l.getLimitAmount();
            }
        }
        return effective;
    }

    // ==================== 1. كشف عمليات الميزانية التفصيلي ====================
    public File generateDetailedBudgetReport(int currencyId, boolean specificCurrency,
                                             long startDate, long endDate, boolean useDateRange,
                                             String filterType) throws Exception {
        List<Currency> targetCurrencies = resolveTargetCurrencies(currencyId, specificCurrency);
        File file = new File(context.getCacheDir(), "budget_detailed_" + System.currentTimeMillis() + ".pdf");
        Document document = createDocument(file);
        document.open();

        String curLabel = (specificCurrency && !targetCurrencies.isEmpty())
                ? targetCurrencies.get(0).getName() : str(R.string.report_label_all_currencies);
        String filterLabel = str(R.string.budget_report_scope_label);
        if (BudgetEntry.TYPE_EXPENSE.equals(filterType)) {
            filterLabel += " - " + str(R.string.budget_filter_expenses);
        } else if (BudgetEntry.TYPE_INCOME.equals(filterType)) {
            filterLabel += " - " + str(R.string.budget_filter_income);
        }

        addTitleAndPeriod(document, str(R.string.budget_report_title_detailed), filterLabel, curLabel, startDate, endDate, useDateRange);

        boolean hasAnyData = false;
        for (Currency cur : targetCurrencies) {
            List<BudgetEntryWithCategory> rawList = useDateRange
                    ? entryDao.getEntriesByDateRangeSync(cur.getId(), startDate, endDate)
                    : entryDao.getAllEntriesForCurrencySync(cur.getId());

            List<BudgetEntryWithCategory> filtered = new ArrayList<>();
            if (rawList != null) {
                for (BudgetEntryWithCategory item : rawList) {
                    if ("ALL".equals(filterType) || filterType == null || filterType.equals(item.type)) {
                        filtered.add(item);
                    }
                }
            }
            if (filtered.isEmpty()) continue;
            hasAnyData = true;

            if (!specificCurrency) {
                addSectionTitle(document, str(R.string.report_label_currency) + ": " + cur.getName());
            }

            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setRunDirection(isRTL ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
            table.setWidths(isRTL ? new float[]{2.2f, 3.6f, 1.6f, 2.5f, 2.1f} : new float[]{2.1f, 2.5f, 1.6f, 3.6f, 2.2f});
            table.setHeaderRows(1);

            addHeaderCell(table, str(R.string.budget_report_col_date));
            addHeaderCell(table, str(R.string.budget_report_col_category));
            addHeaderCell(table, str(R.string.budget_report_col_type));
            addHeaderCell(table, str(R.string.budget_report_col_note));
            addHeaderCell(table, str(R.string.budget_report_col_amount));

            double totalIncome = 0.0;
            double totalExpense = 0.0;
            int row = 0;

            for (BudgetEntryWithCategory e : filtered) {
                boolean even = row % 2 == 0;
                boolean isIncome = BudgetEntry.TYPE_INCOME.equals(e.type);
                if (isIncome) totalIncome += e.amount;
                else totalExpense += e.amount;

                String catName = BudgetUtils.getCategoryDisplayName(localizedResources, e.categoryCode, e.categoryName);
                String typeText = isIncome ? str(R.string.budget_report_type_income) : str(R.string.budget_report_type_expense);
                String amtText = (isIncome ? "+" : "-") + formatNum(e.amount);

                addDataCell(table, formatDate(e.entryDate), Element.ALIGN_CENTER, normalFont, even);
                addDataCell(table, catName, Element.ALIGN_CENTER, normalFont, even);
                addDataCell(table, typeText, Element.ALIGN_CENTER, isIncome ? greenFont : redFont, even);
                addDataCell(table, e.note != null && !e.note.isEmpty() ? e.note : "-", Element.ALIGN_CENTER, normalFont, even);
                addDataCell(table, amtText, Element.ALIGN_CENTER, isIncome ? greenFont : redFont, even);
                row++;
            }

            // صف ملخص الدخل والمصروفات والصافي
            addSummaryFooterRow(table, str(R.string.budget_total_income), formatNum(totalIncome) + " " + cur.getName(), 5, lightCyan, greenFont);
            addSummaryFooterRow(table, str(R.string.budget_total_expenses), formatNum(totalExpense) + " " + cur.getName(), 5, lightCyan, redFont);
            double net = totalIncome - totalExpense;
            String netLabel = net >= 0 ? str(R.string.budget_report_net_surplus) : str(R.string.budget_report_net_deficit);
            addSummaryFooterRow(table, netLabel, (net >= 0 ? "+" : "-") + formatNum(Math.abs(net)) + " " + cur.getName(), 5, highlightYellow, net >= 0 ? greenFont : redFont);

            document.add(table);
            document.add(Chunk.NEWLINE);
        }

        document.close();
        if (!hasAnyData) {
            file.delete();
            return null;
        }
        return file;
    }

    // ==================== 2. تقرير تحليل وتوزيع التصنيفات ====================
    public File generateCategoryAnalysisReport(int currencyId, boolean specificCurrency,
                                               long startDate, long endDate, boolean useDateRange) throws Exception {
        List<Currency> targetCurrencies = resolveTargetCurrencies(currencyId, specificCurrency);
        File file = new File(context.getCacheDir(), "budget_categories_" + System.currentTimeMillis() + ".pdf");
        Document document = createDocument(file);
        document.open();

        String curLabel = (specificCurrency && !targetCurrencies.isEmpty())
                ? targetCurrencies.get(0).getName() : str(R.string.report_label_all_currencies);
        addTitleAndPeriod(document, str(R.string.budget_report_title_categories),
                str(R.string.budget_report_scope_label), curLabel, startDate, endDate, useDateRange);

        boolean hasAnyData = false;
        for (Currency cur : targetCurrencies) {
            List<CategoryExpenseTotal> expList = useDateRange
                    ? entryDao.getCategoryTotalsByDateRangeSync(cur.getId(), BudgetEntry.TYPE_EXPENSE, startDate, endDate)
                    : entryDao.getCategoryTotalsAllTimeSync(cur.getId(), BudgetEntry.TYPE_EXPENSE);
            List<CategoryExpenseTotal> incList = useDateRange
                    ? entryDao.getCategoryTotalsByDateRangeSync(cur.getId(), BudgetEntry.TYPE_INCOME, startDate, endDate)
                    : entryDao.getCategoryTotalsAllTimeSync(cur.getId(), BudgetEntry.TYPE_INCOME);

            boolean hasExp = expList != null && !expList.isEmpty();
            boolean hasInc = incList != null && !incList.isEmpty();
            if (!hasExp && !hasInc) continue;
            hasAnyData = true;

            if (!specificCurrency) {
                addSectionTitle(document, str(R.string.report_label_currency) + ": " + cur.getName());
            }

            double totalExpense = 0.0;
            double totalIncome = 0.0;

            if (hasExp) {
                for (CategoryExpenseTotal c : expList) totalExpense += c.totalAmount;
                addSectionTitle(document, str(R.string.budget_report_section_expenses));
                document.add(buildCategoryDistributionTable(expList, totalExpense, cur.getName(), false));
                document.add(Chunk.NEWLINE);
            }

            if (hasInc) {
                for (CategoryExpenseTotal c : incList) totalIncome += c.totalAmount;
                addSectionTitle(document, str(R.string.budget_report_section_income));
                document.add(buildCategoryDistributionTable(incList, totalIncome, cur.getName(), true));
                document.add(Chunk.NEWLINE);
            }

            // بطاقة خلاصة الصافي للعملة
            PdfPTable netTable = new PdfPTable(2);
            netTable.setWidthPercentage(100);
            netTable.setRunDirection(isRTL ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
            double net = totalIncome - totalExpense;
            String netLabel = net >= 0 ? str(R.string.budget_report_net_surplus) : str(R.string.budget_report_net_deficit);
            addSummaryFooterRow(netTable, netLabel + " (" + cur.getName() + ")",
                    (net >= 0 ? "+" : "-") + formatNum(Math.abs(net)), 2, highlightYellow, net >= 0 ? greenFont : redFont);
            document.add(netTable);
            document.add(Chunk.NEWLINE);
        }

        document.close();
        if (!hasAnyData) {
            file.delete();
            return null;
        }
        return file;
    }

    private PdfPTable buildCategoryDistributionTable(List<CategoryExpenseTotal> items, double grandTotal,
                                                     String currencyName, boolean isIncome) throws DocumentException {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setRunDirection(isRTL ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
        table.setWidths(isRTL ? new float[]{2.0f, 2.6f, 1.5f, 3.9f} : new float[]{3.9f, 1.5f, 2.6f, 2.0f});
        table.setHeaderRows(1);

        addHeaderCell(table, str(R.string.budget_report_col_category));
        addHeaderCell(table, str(R.string.budget_report_col_count));
        addHeaderCell(table, str(R.string.budget_report_col_amount));
        addHeaderCell(table, str(R.string.budget_report_col_percent));

        int totalCount = 0;
        int row = 0;
        for (CategoryExpenseTotal item : items) {
            boolean even = row % 2 == 0;
            totalCount += item.entryCount;
            double pct = grandTotal > 0 ? (item.totalAmount / grandTotal) * 100.0 : 0.0;
            String catName = BudgetUtils.getCategoryDisplayName(localizedResources, item.categoryCode, item.name);

            addDataCell(table, catName, Element.ALIGN_CENTER, normalFont, even);
            addDataCell(table, String.valueOf(item.entryCount), Element.ALIGN_CENTER, normalFont, even);
            addDataCell(table, formatNum(item.totalAmount), Element.ALIGN_CENTER, isIncome ? greenFont : redFont, even);
            addDataCell(table, String.format(Locale.US, "%.1f%%", pct), Element.ALIGN_CENTER, tealFont, even);
            row++;
        }

        addSummaryFooterRow(table, str(R.string.report_label_totals) + " (" + totalCount + ")",
                formatNum(grandTotal) + " " + currencyName, 4, lightCyan, isIncome ? greenFont : redFont);
        return table;
    }

    // ==================== 3. تقرير الملخص والمقارنة الشهرية للميزانية ====================
    public File generateMonthlyBudgetSummaryReport(int currencyId, boolean specificCurrency,
                                                   long startDate, long endDate, boolean useDateRange) throws Exception {
        List<Currency> targetCurrencies = resolveTargetCurrencies(currencyId, specificCurrency);
        File file = new File(context.getCacheDir(), "budget_monthly_" + System.currentTimeMillis() + ".pdf");
        Document document = createDocument(file);
        document.open();

        String curLabel = (specificCurrency && !targetCurrencies.isEmpty())
                ? targetCurrencies.get(0).getName() : str(R.string.report_label_all_currencies);
        addTitleAndPeriod(document, str(R.string.budget_report_title_monthly),
                str(R.string.budget_report_scope_label), curLabel, startDate, endDate, useDateRange);

        boolean hasAnyData = false;
        for (Currency cur : targetCurrencies) {
            List<MonthlyTotals> months = useDateRange
                    ? entryDao.getMonthlyTotalsByDateRangeSync(cur.getId(), startDate, endDate)
                    : entryDao.getMonthlyTotalsAllTimeSync(cur.getId());
            if (months == null || months.isEmpty()) continue;
            hasAnyData = true;

            List<MonthlyBudgetLimit> limitsAsc = limitDao.getAllActiveLimitsForCurrencySync(cur.getId());

            if (!specificCurrency) {
                addSectionTitle(document, str(R.string.report_label_currency) + ": " + cur.getName());
            }

            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setRunDirection(isRTL ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
            table.setWidths(isRTL ? new float[]{2.0f, 1.5f, 2.0f, 2.0f, 2.0f, 1.8f} : new float[]{1.8f, 2.0f, 2.0f, 2.0f, 1.5f, 2.0f});
            table.setHeaderRows(1);

            addHeaderCell(table, str(R.string.budget_report_col_month));
            addHeaderCell(table, str(R.string.budget_report_col_income));
            addHeaderCell(table, str(R.string.budget_report_col_expense));
            addHeaderCell(table, str(R.string.budget_report_col_limit));
            addHeaderCell(table, str(R.string.budget_report_col_usage));
            addHeaderCell(table, str(R.string.budget_report_col_net));

            double grandIncome = 0.0;
            double grandExpense = 0.0;
            int row = 0;

            for (MonthlyTotals mt : months) {
                boolean even = row % 2 == 0;
                grandIncome += mt.totalIncome;
                grandExpense += mt.totalExpense;
                double limit = resolveEffectiveLimit(limitsAsc, mt.yearMonth);
                double net = mt.totalIncome - mt.totalExpense;
                String limitStr = limit > 0 ? formatNum(limit) : "-";
                String usageStr = limit > 0 ? String.format(Locale.US, "%.0f%%", (mt.totalExpense / limit) * 100.0) : "-";
                Font usageFont = (limit > 0 && mt.totalExpense > limit) ? redFont : tealFont;

                addDataCell(table, mt.yearMonth, Element.ALIGN_CENTER, normalFont, even);
                addDataCell(table, formatNum(mt.totalIncome), Element.ALIGN_CENTER, greenFont, even);
                addDataCell(table, formatNum(mt.totalExpense), Element.ALIGN_CENTER, redFont, even);
                addDataCell(table, limitStr, Element.ALIGN_CENTER, normalFont, even);
                addDataCell(table, usageStr, Element.ALIGN_CENTER, usageFont, even);
                addDataCell(table, (net >= 0 ? "+" : "-") + formatNum(Math.abs(net)), Element.ALIGN_CENTER, net >= 0 ? greenFont : redFont, even);
                row++;
            }

            double grandNet = grandIncome - grandExpense;
            addSummaryFooterRow(table, str(R.string.budget_total_income), formatNum(grandIncome) + " " + cur.getName(), 6, lightCyan, greenFont);
            addSummaryFooterRow(table, str(R.string.budget_total_expenses), formatNum(grandExpense) + " " + cur.getName(), 6, lightCyan, redFont);
            String netLabel = grandNet >= 0 ? str(R.string.budget_report_net_surplus) : str(R.string.budget_report_net_deficit);
            addSummaryFooterRow(table, netLabel, (grandNet >= 0 ? "+" : "-") + formatNum(Math.abs(grandNet)) + " " + cur.getName(), 6, highlightYellow, grandNet >= 0 ? greenFont : redFont);

            document.add(table);
            document.add(Chunk.NEWLINE);
        }

        document.close();
        if (!hasAnyData) {
            file.delete();
            return null;
        }
        return file;
    }

    // ==================== دوال بناء الجداول والتنسيق ====================

    private void addTitleAndPeriod(Document doc, String title, String subTitle, String currency,
                                   long start, long end, boolean useDateRange) throws DocumentException {
        PdfPTable titleTable = new PdfPTable(1);
        titleTable.setWidthPercentage(100);
        titleTable.setRunDirection(isRTL ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);

        PdfPCell titleCell = new PdfPCell(createPhrase(title, titleFont));
        titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setPaddingBottom(6);
        titleTable.addCell(titleCell);

        StringBuilder sb = new StringBuilder();
        sb.append(str(R.string.report_label_currency)).append(": ").append(currency).append(" | ");
        if (useDateRange) {
            sb.append(str(R.string.report_label_period)).append(": ")
                    .append(str(R.string.report_label_from)).append(" ").append(formatDate(start))
                    .append(" ").append(str(R.string.report_label_to)).append(" ").append(formatDate(end));
        } else {
            sb.append(str(R.string.report_label_all_periods));
        }
        sb.append(" | ").append(subTitle);

        PdfPCell subCell = new PdfPCell(createPhrase(sb.toString(), subTitleFont));
        subCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        subCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        subCell.setBorder(Rectangle.NO_BORDER);
        subCell.setPaddingBottom(14);
        titleTable.addCell(subCell);

        doc.add(titleTable);
    }

    private void addSectionTitle(Document doc, String title) throws DocumentException {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setRunDirection(isRTL ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
        PdfPCell cell = new PdfPCell(createPhrase(title, sectionFont));
        cell.setHorizontalAlignment(isRTL ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingTop(6);
        cell.setPaddingBottom(6);
        t.addCell(cell);
        doc.add(t);
    }

    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(createPhrase(text, tableHeaderFont));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBackgroundColor(headerBgColor);
        cell.setBorderColor(new BaseColor(180, 195, 200));
        cell.setPadding(6);
        cell.setMinimumHeight(22);
        table.addCell(cell);
    }

    private void addDataCell(PdfPTable table, String text, int alignment, Font font, boolean isEven) {
        PdfPCell cell = new PdfPCell(createPhrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (isEven) cell.setBackgroundColor(evenRowColor);
        cell.setBorderColor(new BaseColor(220, 226, 232));
        cell.setPadding(6);
        cell.setMinimumHeight(20);
        table.addCell(cell);
    }

    private void addSummaryFooterRow(PdfPTable table, String label, String value, int totalCols,
                                     BaseColor bgColor, Font valueFont) {
        PdfPCell lblCell = new PdfPCell(createPhrase(label, totalsFont));
        lblCell.setColspan(Math.max(1, totalCols - 1));
        lblCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        lblCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        lblCell.setBackgroundColor(bgColor);
        lblCell.setBorderColor(new BaseColor(180, 195, 200));
        lblCell.setPadding(7);
        table.addCell(lblCell);

        PdfPCell valCell = new PdfPCell(createPhrase(value, valueFont));
        valCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        valCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        valCell.setBackgroundColor(bgColor);
        valCell.setBorderColor(new BaseColor(180, 195, 200));
        valCell.setPadding(7);
        table.addCell(valCell);
    }

    private String formatNum(double amount) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumFractionDigits(0);
        nf.setMaximumFractionDigits(2);
        return nf.format(amount);
    }

    private String formatDate(long ts) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(ts));
    }
}
