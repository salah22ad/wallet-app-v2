package com.hpp.daftree.helpers;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.hpp.daftree.R;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.database.Transaction;
import com.hpp.daftree.dialogs.AccountBottomSheet;
import com.hpp.daftree.models.TransactionUtils;
import com.hpp.daftree.utils.DialogHelper;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ShareNotificationHelper {

    private static final String PKG_WHATSAPP_BUSINESS = "com.whatsapp.w4b";
    private static final String PKG_WHATSAPP_REGULAR  = "com.whatsapp";
    private static final String PREFS_SHARE = "account_share_prompts";
    public static final String FLAG_UPGRADE_BANNER = "notify_upgrade_banner_pending";

    private ShareNotificationHelper() {}

    // ==================== صيغ الرسائل الرسمية النظيفة (بدون ترحيب وبدون خاتمة) ====================

    public static String buildTransactionMessage(Context ctx, Transaction tx, String currencyName, double finalBalance) {
        String typeLabel = tx.getType() == 1
                ? ctx.getString(R.string.share_tx_type_debit)
                : ctx.getString(R.string.share_tx_type_credit);
        String amountStr = formatAmount(tx.getAmount());
        String details = tx.getDetails() != null ? tx.getDetails().trim() : "-";
        String dateStr = formatDate(tx.getTimestamp() != null ? tx.getTimestamp() : new Date());
        String cur = currencyName != null ? currencyName.trim() : "";

        String balanceLine;
        if (Math.abs(finalBalance) < 0.005) {
            balanceLine = "• " + ctx.getString(R.string.share_tx_balance_label) + ": 0 " + cur
                    + " (" + ctx.getString(R.string.share_tx_balance_settled) + ")";
        } else {
            String balSide = finalBalance > 0
                    ? ctx.getString(R.string.report_balance_type_debit)
                    : ctx.getString(R.string.report_balance_type_credit);
            balanceLine = "• " + ctx.getString(R.string.share_tx_balance_label) + ": "
                    + formatAmount(Math.abs(finalBalance)) + " " + cur + " (" + balSide + ")";
        }

        return ctx.getString(R.string.share_tx_header) + "\n\n"
                + "• " + ctx.getString(R.string.share_tx_type_label) + ": " + typeLabel + "\n"
                + "• " + ctx.getString(R.string.share_tx_amount_label) + ": " + amountStr + " " + cur + "\n"
                + "• " + ctx.getString(R.string.share_tx_details_label) + ": " + details + "\n"
                + "• " + ctx.getString(R.string.share_tx_date_label) + ": " + dateStr + "\n"
                + "─────────────\n"
                + balanceLine;
    }

    public static String buildInvoiceMessage(Context ctx, boolean isSales, double invoiceAmount,
                                             double paidAmount, String details, Date date,
                                             String currencyName, double finalBalance) {
        String header = isSales
                ? ctx.getString(R.string.share_inv_header_sales)
                : ctx.getString(R.string.share_inv_header_purchases);
        String cur = currencyName != null ? currencyName.trim() : "";
        double remaining = Math.max(0.0, invoiceAmount - paidAmount);
        String safeDetails = details != null && !details.trim().isEmpty() ? details.trim() : "-";
        String dateStr = formatDate(date != null ? date : new Date());

        String balanceLine;
        if (Math.abs(finalBalance) < 0.005) {
            balanceLine = "• " + ctx.getString(R.string.share_inv_balance_label) + ": 0 " + cur
                    + " (" + ctx.getString(R.string.share_tx_balance_settled) + ")";
        } else {
            String balSide = finalBalance > 0
                    ? ctx.getString(R.string.report_balance_type_debit)
                    : ctx.getString(R.string.report_balance_type_credit);
            balanceLine = "• " + ctx.getString(R.string.share_inv_balance_label) + ": "
                    + formatAmount(Math.abs(finalBalance)) + " " + cur + " (" + balSide + ")";
        }

        return header + "\n\n"
                + "• " + ctx.getString(R.string.share_inv_total_label) + ": " + formatAmount(invoiceAmount) + " " + cur + "\n"
                + "• " + ctx.getString(R.string.share_inv_paid_label) + ": " + formatAmount(paidAmount) + " " + cur + "\n"
                + "• " + ctx.getString(R.string.share_inv_remaining_label) + ": " + formatAmount(remaining) + " " + cur + "\n"
                + "• " + ctx.getString(R.string.share_tx_details_label) + ": " + safeDetails + "\n"
                + "• " + ctx.getString(R.string.share_tx_date_label) + ": " + dateStr + "\n"
                + "─────────────\n"
                + balanceLine;
    }

    public static String buildBalanceSummaryMessage(Context ctx, String currencyName, double finalBalance) {
        String cur = currencyName != null ? currencyName.trim() : "";
        if (Math.abs(finalBalance) < 0.005) {
            return ctx.getString(R.string.share_bal_zero, cur);
        }
        String balSide = finalBalance > 0
                ? ctx.getString(R.string.report_balance_type_debit)
                : ctx.getString(R.string.report_balance_type_credit);
        String dateStr = formatDate(new Date());

        return ctx.getString(R.string.share_bal_header, cur) + "\n\n"
                + "• " + ctx.getString(R.string.share_tx_balance_label) + ": "
                + formatAmount(Math.abs(finalBalance)) + " " + cur + " (" + balSide + ")\n"
                + "• " + ctx.getString(R.string.share_tx_date_label) + ": " + dateStr;
    }

    // ==================== صيغ رسائل SMS المختصرة (1 إلى 2 رسالة كحد أقصى) ====================

    public static String buildTransactionSmsMessage(Context ctx, Transaction tx, String currencyName, double finalBalance) {
        String typeLabel = tx.getType() == 1
                ? ctx.getString(R.string.sms_tx_debit)
                : ctx.getString(R.string.sms_tx_credit);
        String amountStr = formatAmount(tx.getAmount());
        String cur = currencyName != null ? currencyName.trim() : "";
        String details = tx.getDetails() != null ? tx.getDetails().trim() : "";
        Date txDate = tx.getTimestamp() != null ? tx.getTimestamp() : new Date();

        StringBuilder sb = new StringBuilder();
        sb.append(typeLabel).append(" ").append(amountStr);
        if (!cur.isEmpty()) sb.append(" ").append(cur);
        if (!isToday(txDate)) {
            sb.append("\n").append(ctx.getString(R.string.sms_date_prefix)).append(" ").append(formatSmsFullDate(txDate));
        }
        if (!details.isEmpty()) {
            sb.append("\n").append(details);
        }
        sb.append("\n").append(buildSmsBalanceLine(ctx, finalBalance));
        return sb.toString();
    }

    public static String buildInvoiceSmsMessage(Context ctx, boolean isSales, double invoiceAmount,
                                                double paidAmount, String details, Date date,
                                                String currencyName, double finalBalance) {
        String header = isSales
                ? ctx.getString(R.string.sms_inv_sales)
                : ctx.getString(R.string.sms_inv_purchases);
        String cur = currencyName != null ? currencyName.trim() : "";
        double remaining = Math.max(0.0, invoiceAmount - paidAmount);
        String safeDetails = details != null ? details.trim() : "";
        Date invDate = date != null ? date : new Date();

        StringBuilder sb = new StringBuilder();
        sb.append(header).append(" ").append(formatAmount(invoiceAmount));
        if (!cur.isEmpty()) sb.append(" ").append(cur);
        if (!isToday(invDate)) {
            sb.append("\n").append(ctx.getString(R.string.sms_date_prefix)).append(" ").append(formatSmsFullDate(invDate));
        }
        if (paidAmount > 0.005) {
            sb.append("\n")
              .append(ctx.getString(R.string.sms_inv_paid)).append(": ").append(formatAmount(paidAmount))
              .append(" || ")
              .append(ctx.getString(R.string.sms_inv_rem)).append(": ").append(formatAmount(remaining));
        }
        if (!safeDetails.isEmpty()) {
            sb.append("\n").append(safeDetails);
        }
        sb.append("\n").append(buildSmsBalanceLine(ctx, finalBalance));
        return sb.toString();
    }

    public static String buildBalanceSummarySmsMessage(Context ctx, String currencyName, double finalBalance) {
        String cur = currencyName != null ? currencyName.trim() : "";
        if (Math.abs(finalBalance) < 0.005) {
            return ctx.getString(R.string.sms_bal_zero, cur);
        }
        String balSide = finalBalance > 0
                ? ctx.getString(R.string.sms_balance_debit)
                : ctx.getString(R.string.sms_balance_credit);
        return ctx.getString(R.string.sms_bal_summary, formatAmount(Math.abs(finalBalance)), cur, balSide);
    }

    private static String buildSmsBalanceLine(Context ctx, double finalBalance) {
        String label = ctx.getString(R.string.sms_balance_label);
        if (Math.abs(finalBalance) < 0.005) {
            return label + ": 0 " + ctx.getString(R.string.sms_balance_settled);
        }
        String side = finalBalance > 0
                ? ctx.getString(R.string.sms_balance_debit)
                : ctx.getString(R.string.sms_balance_credit);
        return label + ": " + formatAmount(Math.abs(finalBalance)) + " " + side;
    }

    private static boolean isToday(Date date) {
        if (date == null) return true;
        Calendar c1 = Calendar.getInstance();
        Calendar c2 = Calendar.getInstance();
        c2.setTime(date);
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
                && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR);
    }

    private static String formatSmsFullDate(Date date) {
        return new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(date);
    }

    // ==================== الإرسال الصامت (واتساب أعمال -> واتساب عادي -> تنبيه / SMS) ====================

    public static boolean isPackageInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static void sendViaWhatsAppSilently(Context context, String phoneNumber, String message) {
        if (context == null) return;
        String targetPackage = null;
        if (isPackageInstalled(context, PKG_WHATSAPP_BUSINESS)) {
            targetPackage = PKG_WHATSAPP_BUSINESS;
        } else if (isPackageInstalled(context, PKG_WHATSAPP_REGULAR)) {
            targetPackage = PKG_WHATSAPP_REGULAR;
        }

        if (targetPackage == null) {
            DialogHelper.show(
                    context,
                    context.getString(R.string.whatsapp_not_installed_alert),
                    DialogHelper.FeedbackType.WARNING,
                    null
            );
            return;
        }

        String cleanPhone = phoneNumber != null ? phoneNumber.replaceAll("[\\s\\-()]", "").replace("+", "") : "";
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setPackage(targetPackage);
            intent.setData(Uri.parse("https://api.whatsapp.com/send?phone=" + cleanPhone + "&text=" + Uri.encode(message)));
            context.startActivity(intent);
        } catch (Exception e) {
            // محاولة احتياطية بحزمة واتساب الأخرى إن وُجدت
            String fallbackPkg = PKG_WHATSAPP_BUSINESS.equals(targetPackage) ? PKG_WHATSAPP_REGULAR : PKG_WHATSAPP_BUSINESS;
            if (isPackageInstalled(context, fallbackPkg)) {
                try {
                    Intent fallback = new Intent(Intent.ACTION_VIEW);
                    fallback.setPackage(fallbackPkg);
                    fallback.setData(Uri.parse("https://api.whatsapp.com/send?phone=" + cleanPhone + "&text=" + Uri.encode(message)));
                    context.startActivity(fallback);
                    return;
                } catch (Exception ignored) {}
            }
            DialogHelper.show(
                    context,
                    context.getString(R.string.whatsapp_not_installed_alert),
                    DialogHelper.FeedbackType.WARNING,
                    null
            );
        }
    }

    public static void sendViaSms(Context context, String phoneNumber, String message) {
        if (context == null) return;
        String cleanPhone = phoneNumber != null ? phoneNumber.replaceAll("[\\s\\-()]", "") : "";
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("smsto:" + cleanPhone));
        intent.putExtra("sms_body", message);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, context.getString(R.string.cant_open_app), Toast.LENGTH_SHORT).show();
        }
    }

    public static void dispatchAccordingToAccount(Context context, Account account, String message) {
        dispatchAccordingToAccount(context, account, message, message);
    }

    public static void dispatchAccordingToAccount(Context context, Account account,
                                                  String whatsappMessage, String smsMessage) {
        if (context == null || account == null) return;
        String phone = account.getPhoneNumber() != null ? account.getPhoneNumber().trim() : "";
        if (phone.isEmpty()) return;

        if (account.isNotifyWhatsApp()) {
            sendViaWhatsAppSilently(context, phone, whatsappMessage);
        } else if (account.isNotifySms()) {
            sendViaSms(context, phone, smsMessage);
        }
    }

    // ==================== نقطة الدخول الموحدة بعد حفظ عملية أو فاتورة ====================

    public static void handleAfterTransactionSaved(@NonNull FragmentActivity activity,
                                                   int accountId,
                                                   int currencyId,
                                                   @NonNull String currencyName,
                                                   @NonNull Transaction savedTx) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(activity.getApplicationContext());
            Account account = db.accountDao().getAccountByIdBlocking(accountId);
            if (account == null) return;

            double balance = computeBalanceBlocking(db, accountId, currencyId, savedTx);
            int totalTxCount = db.transactionDao().getTransactionCountForAccount(accountId);
            boolean isFirstOperation = totalTxCount <= 1;

            Integer upgradeFlag = db.migrationFlagDao().getFlagValue(FLAG_UPGRADE_BANNER);
            boolean showUpgradeNotice = (upgradeFlag != null && upgradeFlag == 1);
            if (showUpgradeNotice) {
                db.migrationFlagDao().setFlag(FLAG_UPGRADE_BANNER, 0);
            }

            String waMessage = buildTransactionMessage(activity, savedTx, currencyName, balance);
            String smsMessage = buildTransactionSmsMessage(activity, savedTx, currencyName, balance);
            activity.runOnUiThread(() ->
                    deliverPostSaveFlow(activity, account, waMessage, smsMessage, isFirstOperation, showUpgradeNotice));
        });
    }

    public static void handleAfterInvoiceSaved(@NonNull FragmentActivity activity,
                                               int accountId,
                                               int currencyId,
                                               @NonNull String currencyName,
                                               boolean isSales,
                                               double invoiceAmount,
                                               double paidAmount,
                                               String details,
                                               Date date,
                                               int newlyCreatedTxCount) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(activity.getApplicationContext());
            Account account = db.accountDao().getAccountByIdBlocking(accountId);
            if (account == null) return;

            double balance = computeBalanceBlocking(db, accountId, currencyId, null);
            int totalTxCount = db.transactionDao().getTransactionCountForAccount(accountId);
            boolean isFirstOperation = totalTxCount <= Math.max(1, newlyCreatedTxCount);

            Integer upgradeFlag = db.migrationFlagDao().getFlagValue(FLAG_UPGRADE_BANNER);
            boolean showUpgradeNotice = (upgradeFlag != null && upgradeFlag == 1);
            if (showUpgradeNotice) {
                db.migrationFlagDao().setFlag(FLAG_UPGRADE_BANNER, 0);
            }

            String waMessage = buildInvoiceMessage(
                    activity, isSales, invoiceAmount, paidAmount, details, date, currencyName, balance);
            String smsMessage = buildInvoiceSmsMessage(
                    activity, isSales, invoiceAmount, paidAmount, details, date, currencyName, balance);
            activity.runOnUiThread(() ->
                    deliverPostSaveFlow(activity, account, waMessage, smsMessage, isFirstOperation, showUpgradeNotice));
        });
    }

    private static double computeBalanceBlocking(AppDatabase db, int accountId, int currencyId, @Nullable Transaction justSaved) {
        List<Transaction> raw = db.transactionDao().getTransactionsForAccountBlocking(accountId, currencyId);
        List<Transaction> distinct = TransactionUtils.distinctByFirestoreId(raw);
        double bal = 0.0;
        boolean foundJustSaved = false;
        if (distinct != null) {
            for (Transaction t : distinct) {
                bal += (t.getAmount() * t.getType());
                if (justSaved != null && justSaved.getFirestoreId() != null
                        && !justSaved.getFirestoreId().isEmpty()
                        && justSaved.getFirestoreId().equals(t.getFirestoreId())) {
                    foundJustSaved = true;
                } else if (justSaved != null && justSaved.getTimestamp() != null && t.getTimestamp() != null
                        && t.getAmount() == justSaved.getAmount()
                        && t.getType() == justSaved.getType()
                        && Math.abs(t.getTimestamp().getTime() - justSaved.getTimestamp().getTime()) < 2000) {
                    foundJustSaved = true;
                }
            }
        }
        if (justSaved != null && !foundJustSaved) {
            bal += (justSaved.getAmount() * justSaved.getType());
        }
        return bal;
    }

    private static void deliverPostSaveFlow(@NonNull FragmentActivity activity,
                                            @NonNull Account account,
                                            @NonNull String waMessage,
                                            @NonNull String smsMessage,
                                            boolean isFirstOperation,
                                            boolean showUpgradeNotice) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        Runnable nextAction = () -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            boolean hasPhone = account.getPhoneNumber() != null && !account.getPhoneNumber().trim().isEmpty();

            if (hasPhone && (account.isNotifyWhatsApp() || account.isNotifySms())) {
                dispatchAccordingToAccount(activity, account, waMessage, smsMessage);
                return;
            }

            // لا توجد وسيلة مشاركة مفعلة -> تظهر الفقاعة لأول عملية للحساب فقط
            if (isFirstOperation) {
                SharedPreferences sp = activity.getSharedPreferences(PREFS_SHARE, Context.MODE_PRIVATE);
                String key = "prompted_acc_" + account.getId();
                if (!sp.getBoolean(key, false)) {
                    sp.edit().putBoolean(key, true).apply();
                    showEnableSharePromptBubble(activity, account, waMessage, smsMessage);
                }
            }
        };

        if (showUpgradeNotice) {
            showUpgradeNoticeBubble(activity, nextAction);
        } else {
            nextAction.run();
        }
    }

    private static void showUpgradeNoticeBubble(@NonNull FragmentActivity activity, @Nullable Runnable onDismissAction) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_share_prompt, null);
        AppFontHelper.applyFontToViewTree(activity, view);

        TextView tvTitle = view.findViewById(R.id.tvPromptTitle);
        TextView tvMessage = view.findViewById(R.id.tvPromptMessage);
        MaterialButton btnPositive = view.findViewById(R.id.btnPromptPositive);
        MaterialButton btnNegative = view.findViewById(R.id.btnPromptNegative);

        tvTitle.setText(R.string.upgrade_notify_bubble_title);
        tvMessage.setText(R.string.upgrade_notify_bubble_message);
        btnPositive.setText(R.string.ok);
        btnNegative.setVisibility(View.GONE);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(view)
                .setCancelable(true)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        btnPositive.setOnClickListener(v -> {
            dialog.dismiss();
            if (onDismissAction != null) onDismissAction.run();
        });
        dialog.setOnCancelListener(d -> {
            if (onDismissAction != null) onDismissAction.run();
        });
        dialog.show();
    }

    private static void showEnableSharePromptBubble(@NonNull FragmentActivity activity,
                                                    @NonNull Account account,
                                                    @NonNull String pendingWaMessage,
                                                    @NonNull String pendingSmsMessage) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_share_prompt, null);
        AppFontHelper.applyFontToViewTree(activity, view);

        TextView tvTitle = view.findViewById(R.id.tvPromptTitle);
        TextView tvMessage = view.findViewById(R.id.tvPromptMessage);
        MaterialButton btnPositive = view.findViewById(R.id.btnPromptPositive);
        MaterialButton btnNegative = view.findViewById(R.id.btnPromptNegative);

        tvTitle.setText(R.string.first_tx_no_share_title);
        tvMessage.setText(R.string.first_tx_no_share_message);
        btnPositive.setText(R.string.yes);
        btnNegative.setText(R.string.ignore);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(view)
                .setCancelable(true)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnNegative.setOnClickListener(v -> dialog.dismiss());
        btnPositive.setOnClickListener(v -> {
            dialog.dismiss();
            AccountBottomSheet sheet = AccountBottomSheet.newInstanceForEditDirect(account.getId());
            sheet.setAccountActionListener(new AccountBottomSheet.AccountActionListener() {
                @Override
                public void onAccountSaved(Account savedAccount, boolean isNew) {
                    if (savedAccount != null && (savedAccount.isNotifyWhatsApp() || savedAccount.isNotifySms())) {
                        dispatchAccordingToAccount(activity, savedAccount, pendingWaMessage, pendingSmsMessage);
                    }
                }
            });
            sheet.show(activity.getSupportFragmentManager(), "AccountBottomSheetEnableShare");
        });
        dialog.show();
    }

    private static String formatAmount(double amount) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumFractionDigits(0);
        nf.setMaximumFractionDigits(2);
        return nf.format(amount);
    }

    private static String formatDate(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date);
    }
}
