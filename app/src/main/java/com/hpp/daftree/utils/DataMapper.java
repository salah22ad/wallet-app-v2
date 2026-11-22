package com.hpp.daftree.utils;

import com.hpp.daftree.database.ExternalAccountEntity;
import com.hpp.daftree.database.ExternalTransactionEntity;
import com.hpp.daftree.debt.ExternalDebtModels;
import com.hpp.daftree.debt.ExternalDebtModels.AccountDebtSummary;
import com.hpp.daftree.debt.ExternalDebtModels.CurrencySummary;
import com.hpp.daftree.debt.ExternalDebtModels.ExternalTransaction;
import com.hpp.daftree.debt.ExternalDebtModels.UserDebtSummary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataMapper {

    // تحويل البيانات المحلية (حسابات + عمليات) إلى الهيكل الهرمي للشاشة الرئيسية
    public static List<UserDebtSummary> mapToUiModel(List<ExternalAccountEntity> accounts, List<ExternalTransactionEntity> transactions) {
        if (accounts == null || accounts.isEmpty()) return new ArrayList<>();

        // 1. تجميع العمليات حسب الحساب والعملة لحساب الإجماليات
        // Map<AccountId, Map<CurrencyId, CurrencySummary>>
        Map<String, Map<String, CurrencySummary>> accountCurrencyMap = new HashMap<>();

        if (transactions != null) {
            for (ExternalTransactionEntity tx : transactions) {
                if (tx.isDeleted) continue; // تجاهل المحذوف احتياطياً

                accountCurrencyMap.putIfAbsent(tx.accountFirestoreId, new HashMap<>());
                Map<String, CurrencySummary> currencyMap = accountCurrencyMap.get(tx.accountFirestoreId);

                String currencyId = tx.currencyFirestoreId != null ? tx.currencyFirestoreId : "UNKNOWN";
                currencyMap.putIfAbsent(currencyId, new CurrencySummary());

                CurrencySummary summary = currencyMap.get(currencyId);
                // تعيين البيانات الأساسية
                summary.currencyFirestoreId = currencyId;
                summary.currencyName = tx.currencyName != null ? tx.currencyName : "عملة غير معروفة";
                summary.accountFirestoreId = tx.accountFirestoreId;
                summary.ownerUid = tx.ownerUID;

                // الحسابات المالية
                if (tx.type == 1) { // مدين
                    summary.totalDebit += tx.amount;
                } else { // دائن
                    summary.totalCredit += tx.amount;
                }
                summary.netBalance = summary.totalDebit - summary.totalCredit;
            }
        }

        // 2. بناء الهيكل الهرمي: User -> Accounts -> Currencies
        Map<String, UserDebtSummary> userMap = new HashMap<>();

        for (ExternalAccountEntity accEntity : accounts) {
            if (accEntity.isDeleted) continue;

            String ownerUid = accEntity.ownerUID;
            userMap.putIfAbsent(ownerUid, new UserDebtSummary());

            UserDebtSummary userSummary = userMap.get(ownerUid);
            userSummary.ownerUid = ownerUid;
            userSummary.ownerName = accEntity.ownerName != null ? accEntity.ownerName : "مستخدم"; // افترضنا وجود ownerName في Entity أو جلبه بطريقة أخرى
            userSummary.ownerPhone= accEntity.ownerPhone != null ? accEntity.ownerPhone : "مستخدم";
            // إنشاء ملخص الحساب
            AccountDebtSummary accSummary = new AccountDebtSummary();
            accSummary.accountId = accEntity.firestoreId;
            accSummary.accountName = accEntity.accountName;

            // إضافة العملات المرتبطة بهذا الحساب (إن وجدت عمليات)
            if (accountCurrencyMap.containsKey(accEntity.firestoreId)) {
                accSummary.currencies.addAll(accountCurrencyMap.get(accEntity.firestoreId).values());
            }

            // إضافة الحساب للمستخدم
            userSummary.accounts.add(accSummary);
        }

        return new ArrayList<>(userMap.values());
    }

    // تحويل Entity العملية إلى موديل العرض في شاشة التفاصيل
    public static List<ExternalTransaction> mapToExternalTransactions(List<ExternalTransactionEntity> entities) {
        List<ExternalTransaction> list = new ArrayList<>();
        if (entities == null) return list;
        for (ExternalTransactionEntity entity : entities) {
            ExternalTransaction tx = new ExternalTransaction();
            tx.firestoreId = entity.firestoreId;
            tx.amount = entity.amount;
            tx.details = entity.details;
            tx.type = entity.type;
            tx.timestamp = entity.timestamp; // لاحظ أن الموديل يتوقع Object، هنا نمرر long
            tx.accountFirestoreId = entity.accountFirestoreId;
            tx.currencyFirestoreId = entity.currencyFirestoreId;
            tx.ownerUID = entity.ownerUID;
            list.add(tx);
        }
        return list;
    }
}