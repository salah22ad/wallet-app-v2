package com.hpp.daftree.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.ArrayList;
import java.util.List;

@Dao
public abstract class ExternalDebtDao {

    // -----------------------------------------------------------
    // 1. العمليات الأساسية (Primitives)
    // -----------------------------------------------------------

    // للحسابات: نستخدم IGNORE بدلاً من REPLACE لمنع الحذف العرضي للأبناء
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract List<Long> insertAccountsIgnore(List<ExternalAccountEntity> accounts);

    // للتحديث: نستخدم Update لتحديث بيانات الحساب الموجود
    @Update
    protected abstract void updateAccounts(List<ExternalAccountEntity> accounts);

    // للعمليات: هنا REPLACE آمنة لأن العمليات هي "الأبناء" وليس لها أحفاد ليتم حذفهم
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract void upsertTransactions(List<ExternalTransactionEntity> transactions);


    // -----------------------------------------------------------
    // 2. عمليات الحذف والتنظيف
    // -----------------------------------------------------------

    // حذف الحسابات التي لم تعد موجودة في القائمة الصالحة (المطابقة)
    @Query("DELETE FROM external_accounts WHERE firestoreId NOT IN (:validIds)")
    protected abstract void deleteAccountsNotIn(List<String> validIds);

    // حذف عملية محددة (Soft Delete handling)
    @Query("DELETE FROM external_transactions WHERE firestoreId = :id")
    protected abstract void deleteTransactionById(String id);

    @Query("DELETE FROM external_accounts")
    protected abstract void deleteAllAccounts();

    @Query("SELECT firestoreId FROM external_accounts")
    public abstract List<String> getLocalAccountIdsBlocking();
    // -----------------------------------------------------------
    // 3. الدالة الهجينة (Hybrid Sync Transaction) - تم إصلاح المنطق هنا
    // -----------------------------------------------------------
    @Transaction
    public void syncHybridData(List<ExternalAccountEntity> validAccounts, List<ExternalTransactionEntity> incrementalTransactions) {

        // أولاً: معالجة الحسابات (Full Reconciliation) بدون تدمير العمليات
        if (validAccounts == null || validAccounts.isEmpty()) {
            deleteAllAccounts();
        } else {
            // أ) جمع المعرفات الصالحة
            List<String> validIds = new ArrayList<>();
            for (ExternalAccountEntity acc : validAccounts) {
                validIds.add(acc.firestoreId);
            }

            // ب) احذف أي حساب محلي ليس ضمن القائمة الصالحة
            // (هنا نريد الـ Cascade ليعمل ويحذف العمليات للحسابات المحذوفة فعلاً)
            deleteAccountsNotIn(validIds);

            // ج) تحديث بيانات الحسابات المتبقية بطريقة آمنة (Safe Upsert)
            // 1. نحاول الإضافة (للجديد)
            List<Long> insertResults = insertAccountsIgnore(validAccounts);
            List<ExternalAccountEntity> accountsToUpdate = new ArrayList<>();

            // 2. نتحقق من النتائج، إذا عاد -1 فهذا يعني الحساب موجود -> نضيفه لقائمة التحديث
            for (int i = 0; i < insertResults.size(); i++) {
                if (insertResults.get(i) == -1L) {
                    accountsToUpdate.add(validAccounts.get(i));
                }
            }

            // 3. نقوم بتحديث الحسابات الموجودة فقط
            if (!accountsToUpdate.isEmpty()) {
                updateAccounts(accountsToUpdate);
            }
        }

        // ثانياً: معالجة العمليات (Incremental Updates)
        if (incrementalTransactions != null && !incrementalTransactions.isEmpty()) {
            List<ExternalTransactionEntity> toUpsert = new ArrayList<>();

            for (ExternalTransactionEntity tx : incrementalTransactions) {
                if (tx.isDeleted) {
                    // إذا العملية محذوفة في السيرفر -> حذف محلي
                    deleteTransactionById(tx.firestoreId);
                } else {
                    // تجميع للإضافة/التحديث
                    toUpsert.add(tx);
                }
            }

            if (!toUpsert.isEmpty()) {
                upsertTransactions(toUpsert);
            }
        }
    }


    // -----------------------------------------------------------
    // 4. استعلامات العرض (UI Queries)
    // -----------------------------------------------------------
    @Query("SELECT * FROM external_accounts  ORDER BY lastModified DESC")
    public abstract LiveData<List<ExternalAccountEntity>> getAllExternalAccounts();

    @Query("SELECT * FROM external_accounts ORDER BY lastModified DESC")
    public abstract LiveData<List<ExternalAccountEntity>> getAllExternalAccounts2(); // للـ Debugging

    @Query("SELECT * FROM external_transactions WHERE accountFirestoreId = :accountId AND currencyFirestoreId = :currencyId AND isDeleted = 0 ORDER BY timestamp ASC")
    public abstract LiveData<List<ExternalTransactionEntity>> getTransactionsForAccountAndCurrency(String accountId, String currencyId);

    @Query("SELECT * FROM external_transactions ")
    public abstract LiveData<List<ExternalTransactionEntity>> getAllTransactions();



}