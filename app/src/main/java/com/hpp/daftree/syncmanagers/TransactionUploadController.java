package com.hpp.daftree.syncmanagers;

import android.content.Context;
import android.content.SharedPreferences;

import com.hpp.daftree.database.Transaction;
import com.hpp.daftree.utils.LicenseManager;

public class TransactionUploadController {
    private static final String TAG = "TransactionUploadController";
    private static final String PREFS = "PendingTxPrefs";
    private static final String KEY_FIRST_BLOCKED = "first_blocked_id";
    private static final String KEY_PENDING_DIALOG_FLAG = "pending_dialog_flag"; // 🔥 علامة جديدة

    private final Context context;
    private final LicenseManager licenseManager;

    public TransactionUploadController(Context ctx) {
        this.context = ctx;
        this.licenseManager = LicenseManager.getInstance(ctx);
    }

    /** يفحص إمكانية رفع العمليات، ويعيد True إن كان مسموحاً */
    public boolean canSend(Transaction tx) {
        return licenseManager.canCreateTransaction();
    }

    /** يحفظ أول عملية تم تعليقها حتى لا يُظهر التحذير بشكل متكرر */
    public void storeFirstBlocked(Transaction tx) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!sp.contains(KEY_FIRST_BLOCKED)) {
            sp.edit()
                    .putInt(KEY_FIRST_BLOCKED, tx.getId())
                    .putBoolean(KEY_PENDING_DIALOG_FLAG, true) // 🔥 تعيين العلامة
                    .apply();
        }
    }

    /** يعيد ضبط العملية المحجوزة عند منتصف الليل بعد التجديد */
    public void resetBlocked() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_FIRST_BLOCKED)
                .remove(KEY_PENDING_DIALOG_FLAG) // 🔥 إزالة العلامة
                .apply();
    }

    public boolean hasBlocked() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .contains(KEY_FIRST_BLOCKED);
    }

    // 🔥 **دالة جديدة: التحقق من وجود ديالوج معلق**
    public boolean hasPendingDialog() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_PENDING_DIALOG_FLAG, false);
    }

    // 🔥 **دالة جديدة: مسح علامة الديالوج**
    public void clearDialogFlag() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_PENDING_DIALOG_FLAG)
                .apply();
    }
}