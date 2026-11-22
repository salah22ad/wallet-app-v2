package com.hpp.daftree.models;

import static com.hpp.daftree.helpers.LanguageManager.isRTL;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.hpp.daftree.R;
import com.hpp.daftree.database.User;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfPageEventHelper;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.FontSelector;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReportHeaderFooter extends PdfPageEventHelper {
    private final Context context;
    private final User userProfile;
    private final Locale currentLocale;
    private final boolean isRTL;
    private Image profileImage;
    private final Font headerFont, footerFont, smallHeaderFont, companyFont;
    // ألوان متطورة تتناسب مع التقرير
    private final BaseColor primaryColor = new BaseColor(0, 77, 153);
    private final BaseColor secondaryColor = new BaseColor(230, 232, 235);
    private final BaseColor accentColor = new BaseColor(70, 130, 180);
    private final BaseColor borderColor = new BaseColor(200, 210, 230);
    private final Resources localizedResources;

    // مدير الخطوط المتعددة اللغات
    private final MultiLanguageFontManager fontManager;

    // فئة مدير الخطوط المتعددة اللغات
    private static class MultiLanguageFontManager {
        private BaseFont arabicFont;
        private BaseFont latinFont;
        private boolean initialized = false;
        private byte[] getFontBytes(Context context, int fontResId) {
            try {
                java.io.InputStream is = context.getResources().openRawResource(fontResId);
                byte[] buffer = new byte[is.available()];
                int read = is.read(buffer);
                is.close();
                return buffer;
            } catch (java.io.IOException e) {
                android.util.Log.e("ReportGenerator", "قالب الخط غير موجود في res/font: " + e.getMessage());
                return null;
            }
        }
        public void initialize(Context context) {
            if (initialized) return;

            try {
                // تحميل الخط العربي (يدعم العربية والفارسية)
                byte[] arabicFontBytes = getFontBytes(context, R.font.cairo_regular);
                if (arabicFontBytes != null) {
                    arabicFont = BaseFont.createFont("cairo_regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, arabicFontBytes, null);
                    android.util.Log.d("ReportGenerator", "تم تحميل خط كايرو من الموارد بنجاح");
                }

                // تحميل الخط اللاتيني (يدعم الإنجليزية والفيتنامية)
                try {
                    latinFont = BaseFont.createFont(
                            "assets/NotoSans.ttf",
                            BaseFont.IDENTITY_H,
                            BaseFont.EMBEDDED
                    );
                } catch (Exception e) {
                    // خط احتياطي
                    latinFont = BaseFont.createFont(
                            BaseFont.HELVETICA,
                            BaseFont.WINANSI,
                            BaseFont.EMBEDDED
                    );
                }

                initialized = true;
                Log.d("ReportHeaderFooter", "Fonts initialized successfully");

            } catch (Exception e) {
                Log.e("ReportHeaderFooter", "Error loading fonts: " + e.getMessage(), e);

                // استخدام الخطوط الافتراضية كبديل طوارئ
                try {
                    arabicFont = BaseFont.createFont(
                            BaseFont.TIMES_ROMAN,
                            BaseFont.WINANSI,
                            BaseFont.EMBEDDED
                    );
                    latinFont = BaseFont.createFont(
                            BaseFont.HELVETICA,
                            BaseFont.WINANSI,
                            BaseFont.EMBEDDED
                    );
                } catch (Exception ex) {
                    Log.e("ReportHeaderFooter", "Error loading fallback fonts: " + ex.getMessage());
                }
            }
        }

        /**
         * إنشاء Phrase يدعم جميع اللغات
         */
        public Phrase createPhrase(String text, Font styleFont, boolean arabicFirst) {
            if (text == null || text.isEmpty()) {
                return new Phrase("", styleFont);
            }

            try {
                FontSelector selector = new FontSelector();

                // ترتيب الخطوط حسب الأولوية المحددة
                if (arabicFirst) {
                    selector.addFont(new Font(arabicFont,
                            styleFont.getSize(),
                            styleFont.getStyle(),
                            styleFont.getColor()));
                    selector.addFont(new Font(latinFont,
                            styleFont.getSize(),
                            styleFont.getStyle(),
                            styleFont.getColor()));
                } else {
                    selector.addFont(new Font(latinFont,
                            styleFont.getSize(),
                            styleFont.getStyle(),
                            styleFont.getColor()));
                    selector.addFont(new Font(arabicFont,
                            styleFont.getSize(),
                            styleFont.getStyle(),
                            styleFont.getColor()));
                }

                return selector.process(text);

            } catch (Exception e) {
                Log.e("ReportHeaderFooter", "Error creating phrase: " + e.getMessage());
                return new Phrase(text, styleFont);
            }
        }

        /**
         * إنشاء Phrase يدعم جميع اللغات مع أولوية عربية حسب RTL
         */
        public Phrase createPhrase(String text, Font styleFont) {
            return createPhrase(text, styleFont, true);
        }

        public BaseFont getArabicFont() {
            return arabicFont;
        }

        public BaseFont getLatinFont() {
            return latinFont;
        }

        public boolean isInitialized() {
            return initialized;
        }
    }

    public ReportHeaderFooter(Context context, User userProfile, Locale locale, boolean isRTL, Resources localizedResources) {
        this.context = context;
        this.userProfile = userProfile;
        this.currentLocale = locale;
        this.isRTL = isRTL(context);
        this.localizedResources = localizedResources;

        // تهيئة مدير الخطوط
        this.fontManager = new MultiLanguageFontManager();
        this.fontManager.initialize(context);

        try {
            BaseFont bf = fontManager.getLatinFont();

            // استخدام الخطوط مع الحفاظ على التنسيقات
            this.headerFont = new Font(bf, 12, Font.BOLD, primaryColor);
            this.footerFont = new Font(bf, 8, Font.ITALIC, BaseColor.GRAY);
            this.smallHeaderFont = new Font(bf, 10, Font.NORMAL, BaseColor.DARK_GRAY);
            this.companyFont = new Font(bf, 14, Font.BOLD, primaryColor);

            if (userProfile != null && userProfile.getProfileImageUri() != null) {
                try {
                    Bitmap bmp = BitmapFactory.decodeFile(userProfile.getProfileImageUri());
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, stream);
                    this.profileImage = Image.getInstance(stream.toByteArray());
                    this.profileImage.scaleToFit(60, 60);
                } catch (Exception e) {
                    this.profileImage = null;
                    Log.e("HeaderFooter", "Failed to load profile image", e);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create fonts for header/footer", e);
        }
    }

    /**
     * دالة مساعدة لإنشاء Phrase يدعم جميع اللغات
     */
    private Phrase createMultiLanguagePhrase(String text, Font styleFont) {
        return fontManager.createPhrase(text, styleFont, isRTL);
    }

    private String getString(int resId) {
        return localizedResources.getString(resId);
    }

    @Override
    public void onStartPage(PdfWriter writer, Document document) {
        try {
            if (userProfile == null || userProfile.getName() == null) {
                return;
            }

            PdfPTable headerTable = new PdfPTable(3);
            headerTable.setWidthPercentage(100);
            headerTable.setRunDirection(isRTL ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
            headerTable.setWidths(new float[]{3, 2, 3});
            // إضافة خلفية وإطار مميز للهيدر
            headerTable.setSpacingBefore(10f);
            headerTable.setSpacingAfter(5f);

            // بيانات المستخدم
            Phrase userPhrase = new Phrase();
            if (userProfile.getName() != null)
                userPhrase.add(createMultiLanguagePhrase(userProfile.getName() + "\n\n", headerFont));
            if (userProfile.getAddress() != null)
                userPhrase.add(createMultiLanguagePhrase(userProfile.getAddress() + "\n\n", smallHeaderFont));
            if (userProfile.getPhone() != null)
                userPhrase.add(createMultiLanguagePhrase(userProfile.getPhone(), smallHeaderFont));

            PdfPCell userCell = new PdfPCell(userPhrase);
            userCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            userCell.setVerticalAlignment(Element.ALIGN_TOP);
            userCell.setBorder(Rectangle.NO_BORDER);
            userCell.setBackgroundColor(secondaryColor);
            userCell.setPadding(12);

            // الصورة
            PdfPCell logoCell = (profileImage != null) ? new PdfPCell(profileImage) : new PdfPCell();
            logoCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            logoCell.setBorder(Rectangle.NO_BORDER);
            logoCell.setBackgroundColor(secondaryColor);

            // اسم الشركة
            String companyText = userProfile.getCompany() != null ? userProfile.getCompany() : "";
            PdfPCell companyCell = new PdfPCell(createMultiLanguagePhrase(companyText, headerFont));
            companyCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            companyCell.setVerticalAlignment(Element.ALIGN_TOP);
            companyCell.setBorder(Rectangle.NO_BORDER);
            companyCell.setBackgroundColor(secondaryColor);
            companyCell.setPadding(12);

            // إضافة الخلايا حسب اتجاه اللغة
            if (isRTL) {
                headerTable.addCell(userCell);
                headerTable.addCell(logoCell);
                headerTable.addCell(companyCell);
            } else {
                headerTable.addCell(userCell);
                headerTable.addCell(logoCell);
                headerTable.addCell(companyCell);
            }

            headerTable.setTotalWidth(document.right() - document.left());
            headerTable.writeSelectedRows(0, -1, document.leftMargin(),
                    document.top() + 80, writer.getDirectContent());

            // إضافة خط فاصل مميز تحت الهيدر
            PdfPTable separatorTable = new PdfPTable(1);
            separatorTable.setWidthPercentage(100);
            PdfPCell separatorCell = new PdfPCell();
            separatorCell.setFixedHeight(2f);
            separatorCell.setBackgroundColor(accentColor);
            separatorCell.setBorder(Rectangle.NO_BORDER);
            separatorTable.addCell(separatorCell);

            separatorTable.setTotalWidth(document.right() - document.left());
            separatorTable.writeSelectedRows(0, -1, document.leftMargin(),
                    document.top() + 2, writer.getDirectContent());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        try {
            // إنشاء جدول الفوتر المكون من خليتين
            PdfPTable footerTable = new PdfPTable(2);
            footerTable.setWidthPercentage(100);
            footerTable.setRunDirection(isRTL ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
            footerTable.setWidths(new float[]{1, 1}); // تقسيم متساوي للخليتين

            // نص إنشاء المستند (الخلية اليمنى)
            String createdByText = localizedResources.getString(R.string.document_was_created_by) +
                    " " + localizedResources.getString(R.string.app_name_report) ;

            PdfPCell createdByCell = new PdfPCell(createMultiLanguagePhrase(createdByText, footerFont));
            createdByCell.setHorizontalAlignment(!isRTL ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            createdByCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            createdByCell.setBorder(Rectangle.NO_BORDER);
            createdByCell.setPadding(5);

            // تاريخ الطباعة ورقم الصفحة (الخلية اليسرى)
            @SuppressLint("DefaultLocale")
            String pageInfoText = String.format(
                    localizedResources.getString(R.string.page_info_format),
                    writer.getPageNumber(),
                    new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date())
            );

            PdfPCell pageInfoCell = new PdfPCell(createMultiLanguagePhrase(pageInfoText, footerFont));
            pageInfoCell.setHorizontalAlignment(!isRTL ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            pageInfoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            pageInfoCell.setBorder(Rectangle.NO_BORDER);
            pageInfoCell.setPadding(5);

            // إضافة الخلايا حسب اتجاه اللغة
            if (isRTL) {
                // للغات RTL: اليمين أولاً ثم اليسار
                footerTable.addCell(createdByCell);
                footerTable.addCell(pageInfoCell);
            } else {
                // للغات LTR: اليسار أولاً ثم اليمين
                footerTable.addCell(pageInfoCell);
                footerTable.addCell(createdByCell);
            }

            // وضع الجدول في أسفل الصفحة
            footerTable.setTotalWidth(document.right() - document.left());
            footerTable.writeSelectedRows(0, -1,
                    document.leftMargin(),
                    document.bottom() - 20, // ارتفاع من أسفل الصفحة
                    writer.getDirectContent());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}