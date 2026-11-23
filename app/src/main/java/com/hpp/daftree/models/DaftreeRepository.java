package com.hpp.daftree.models;


import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.budget.data.BudgetCategory;
import com.hpp.daftree.budget.data.BudgetCategoryDao;
import com.hpp.daftree.budget.data.BudgetEntry;
import com.hpp.daftree.budget.data.BudgetEntryDao;
import com.hpp.daftree.budget.data.MonthlyBudgetLimit;
import com.hpp.daftree.budget.data.MonthlyBudgetLimitDao;
import com.hpp.daftree.budget.util.BudgetUtils;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.AccountDao;
import com.hpp.daftree.database.AccountLatestTransaction;
import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.database.AccountTypeDao;
import com.hpp.daftree.database.AccountWithBalance;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.CurrencyDao;
import com.hpp.daftree.database.DeletionLog;
import com.hpp.daftree.database.DeletionLogDao;
import com.hpp.daftree.database.ExternalAccountEntity;
import com.hpp.daftree.database.ExternalDebtDao;
import com.hpp.daftree.database.ExternalTransactionEntity;
import com.hpp.daftree.database.RecurringOperation;
import com.hpp.daftree.database.RecurringOperationDao;
import com.hpp.daftree.database.RecurringOperationTracking;
import com.hpp.daftree.database.RecurringOperationTrackingDao;
import com.hpp.daftree.database.Transaction;
import com.hpp.daftree.database.TransactionDao;
import com.hpp.daftree.database.User;
import com.hpp.daftree.database.UserDao;
import com.hpp.daftree.helpers.LanguageManager;
import com.hpp.daftree.syncmanagers.SyncPreferences;
import com.hpp.daftree.syncmanagers.SyncWorker;
import com.hpp.daftree.utils.SecureLicenseManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

public class DaftreeRepository {
    private static final String TAG = "DaftreeRepository";
    private final AccountDao mAccountDao;
    private final UserDao mUserDao;
    private final CurrencyDao currencyDao;
    private final RecurringOperationDao recurringOperationDao;
    private final RecurringOperationTrackingDao recurringOperationTrackingDao;
    private final  SyncPreferences sharedPrefsManager;
    private final AccountTypeDao accountTypeDao;
    private final TransactionDao mTransactionDao;
    private String currentUserId,currentUserName;
    private final DeletionLogDao mDeletionLogDao;
    private final WorkManager mWorkManager;
    private final AppDatabase db;
    private final ExternalDebtDao externalDebtDao;
    private final BudgetCategoryDao budgetCategoryDao;
    private final BudgetEntryDao budgetEntryDao;
    private final MonthlyBudgetLimitDao monthlyBudgetLimitDao;
    private Account account;
    private final Context context;
    private final SyncPreferences syncPreferences;
    private boolean isGuest;
    private String guestUID;

    public DaftreeRepository(Application application) {
        db = AppDatabase.getDatabase(application);
        context = application.getApplicationContext();
        mAccountDao = db.accountDao();
        mTransactionDao = db.transactionDao();
        mDeletionLogDao = db.deletionLogDao();
        mUserDao = db.userDao();
        recurringOperationDao= db.recurringOperationDao();
        sharedPrefsManager = new SyncPreferences(context);
        currencyDao = db.currencyDao();
        accountTypeDao = db.accountTypeDao();
        externalDebtDao = db.externalDebtDao();
        budgetCategoryDao = db.budgetCategoryDao();
        budgetEntryDao = db.budgetEntryDao();
        monthlyBudgetLimitDao = db.monthlyBudgetLimitDao();
        recurringOperationTrackingDao = db.recurringOperationTrackingDao();
        syncPreferences = new SyncPreferences(application.getApplicationContext());
        mWorkManager = WorkManager.getInstance(application);
        isGuest = SecureLicenseManager.getInstance(application.getApplicationContext()).isGuest();
        guestUID = SecureLicenseManager.getInstance(application.getApplicationContext()).guestUID();
        if (isGuest) {
            currentUserId = SecureLicenseManager.getInstance(application.getApplicationContext()).guestUID();
            currentUserName= "Guest User";
        } else {
            currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                    FirebaseAuth.getInstance().getCurrentUser().getUid() : AppAuth.getUserID(application.getApplicationContext());
            currentUserName= FirebaseAuth.getInstance().getCurrentUser() != null ?
                    FirebaseAuth.getInstance().getCurrentUser().getDisplayName() : null;
        }
    }


    public UserDao getUserDao() {
        return mUserDao;
    }

    public void deleteTransaction(Transaction transaction) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (transaction.getFirestoreId() != null && !transaction.getFirestoreId().isEmpty()) {
                DeletionLog log = new DeletionLog();
                log.setFirestoreId(transaction.getFirestoreId());
                log.setCollectionName("transactions");
                mDeletionLogDao.insert(log);
            }
        });
        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (transaction.getFirestoreId() != null && !transaction.getFirestoreId().isEmpty()) {
                transaction.setSyncStatus("DELETED");
                transaction.setDeleted(true);
                transaction.setDeletedBy(currentUserName);
                transaction.setLastModified(System.currentTimeMillis());
                mTransactionDao.update(transaction);
            }
//            mTransactionDao.deleteById(transaction.getId());
            triggerSync();
//            SyncCoordinator.requestSync(context);
        });
    }


    public AccountDao getAccountDao() {
        return mAccountDao;
    }
    public TransactionDao getTransactionsDao() {
        return mTransactionDao;
    }
    public LiveData<List<String>> getDetailSuggestions(int accountId, String query) {
        return mTransactionDao.getDetailSuggestions(accountId, query);
    }

    public LiveData<Double> getDebitForAccount(int accountId, String currency) {
        return mTransactionDao.getDebitForAccount(accountId, currency);
    }

    public LiveData<Double> getCreditForAccount(int accountId, String currency) {
        return mTransactionDao.getCreditForAccount(accountId, currency);
    }

    // للتحقق من وجود بيانات غير متزامنة
    public boolean hasUnsyncedData() {
        int unsyncedAccounts = mAccountDao.getUnsyncedCount();
        int unsyncedTransactions = mTransactionDao.getUnsyncedCount();
        int unsyncedCurrencies = currencyDao.getUnsyncedCount();
        int unsyncedAccountType = accountTypeDao.getUnsyncedCount();
        int unsyncedDeletions = mDeletionLogDao.getDeletedUnsyncedCount();
        int unsyncedBudgetCats = budgetCategoryDao.getUnsyncedCount();
        int unsyncedBudgetEntries = budgetEntryDao.getUnsyncedCount();
        int unsyncedBudgetLimits = monthlyBudgetLimitDao.getUnsyncedCount();
        return (unsyncedAccounts + unsyncedTransactions + unsyncedCurrencies
                + unsyncedAccountType + unsyncedDeletions
                + unsyncedBudgetCats + unsyncedBudgetEntries + unsyncedBudgetLimits) > 0;
    }

    public int getTransactionCountForAccount(int accountId) {
        // يجب أن يتم استدعاؤها من خيط خلفي
        return mTransactionDao.getTransactionCountForAccount(accountId);
    }

    public void updateAccount(Account account) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (!"NEW".equals(account.getSyncStatus())) {
                account.setSyncStatus("EDITED");
            }
            //account.setLastModified(System.currentTimeMillis());
            account.setLastModified(System.currentTimeMillis());
            mAccountDao.update(account);
            triggerSync(); // يمكنك إضافة دالة triggerSync مجددًا إذا أردت مزامنة فورية
//            SyncCoordinator.requestSync(context);
        });
    }

    public void triggerSync() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(constraints)
                .build();

        String uniqueWorkName = "UNIQUE_SYNC_WORK";

        mWorkManager.beginUniqueWork(
                uniqueWorkName,
                ExistingWorkPolicy.REPLACE, // المفتاح السحري هنا!
                syncRequest
        ).enqueue();
    }
    public void deleteAccount(Account account) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // الخطوة 1: جلب كل العمليات التابعة للحساب لتسجيلها للحذف من Firestore
            List<Transaction> transactionsToDelete = mTransactionDao.getTransactionsForAccountBlocking(account.getId());
            for (Transaction tx : transactionsToDelete) {
                if (tx.getFirestoreId() != null && !tx.getFirestoreId().isEmpty()) {
                    DeletionLog log = new DeletionLog();
                    log.setFirestoreId(tx.getFirestoreId());
                    log.setCollectionName("transactions");
                    Log.e("mDeletionLogDao transactions", tx.getFirestoreId());
                    mDeletionLogDao.insert(log);
                }
            }
            // الخطوة 2: تسجيل الحساب نفسه للحذف من Firestore
            if (account.getFirestoreId() != null && !account.getFirestoreId().isEmpty()) {
                DeletionLog log = new DeletionLog();
                log.setFirestoreId(account.getFirestoreId());
                log.setCollectionName("accounts");
                Log.e("mDeletionLogDao accounts", account.getFirestoreId());
                mDeletionLogDao.insert(log);
            }
        });
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // الخطوة 1: جلب كل العمليات التابعة للحساب لتسجيلها للحذف من Firestore
            List<Transaction> transactionsToDelete = mTransactionDao.getTransactionsForAccountBlocking(account.getId());
            for (Transaction tx : transactionsToDelete) {
                if (tx.getFirestoreId() != null && !tx.getFirestoreId().isEmpty()) {
                    tx.setSyncStatus("DELETED");
                    tx.setDeleted(true);
                    tx.setDeletedBy(currentUserName);
                    mTransactionDao.update(tx);
                }
            }

            // الخطوة 2: تسجيل الحساب نفسه للحذف من Firestore
            if (!account.getFirestoreId().isEmpty()) {
                account.setSyncStatus("DELETED");
                account.setDeleted(true);
                account.setDeletedBy(currentUserName);
                //account.setLastModified(System.currentTimeMillis());
                mAccountDao.update(account);
            }
            // الخطوة 3: حذف الحساب من Room (سيقوم Room بحذف كل عملياته تلقائيًا بسبب onDelete = CASCADE)
            // هذا الاستدعاء هو الذي سيُعلم LiveData بالتغيير.
            mAccountDao.delete(account);
            triggerSync();
        });

//        SyncCoordinator.requestSync(context);
    }
    private long readLongSafe(Map<String, Object> data, String key, long fallback) {
        if (data == null || !data.containsKey(key)) return fallback;
        Object val = data.get(key);
        if (val instanceof Number) return ((Number) val).longValue();
        try {
            return val != null ? Long.parseLong(val.toString()) : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private int readIntSafe(Map<String, Object> data, String key, int fallback) {
        if (data == null || !data.containsKey(key)) return fallback;
        Object val = data.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        try {
            return val != null ? Integer.parseInt(val.toString()) : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private double readDoubleSafe(Map<String, Object> data, String key, double fallback) {
        if (data == null || !data.containsKey(key)) return fallback;
        Object val = data.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        try {
            return val != null ? Double.parseDouble(val.toString()) : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private boolean readIsDeletedSafe(Map<String, Object> data) {
        if (data == null) return false;
        if (data.containsKey("isDeleted")) {
            return convertFirestoreBoolean(data.get("isDeleted"));
        }
        if (data.containsKey("isDeleted()")) {
            return convertFirestoreBoolean(data.get("isDeleted()"));
        }
        return false;
    }

    private boolean readIsDefaultSafe(Map<String, Object> data) {
        if (data == null) return false;
        if (data.containsKey("isDefault")) {
            return convertFirestoreBoolean(data.get("isDefault"));
        }
        if (data.containsKey("isDefault()")) {
            return convertFirestoreBoolean(data.get("isDefault()"));
        }
        return false;
    }

    public void batchUpsertAccounts(List<Map<String, Object>> changesBatch) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            for (Map<String, Object> change : changesBatch) {
                String changeType = (String) change.get("type");
                String firestoreId = (String) change.get("firestoreId");
                Map<String, Object> data = safeCastToMap(change.get("data"));

                if ("REMOVED".equals(changeType)) {
                    mAccountDao.deleteByFirestoreId(firestoreId);
                    continue;
                }

                long remoteLastModified = readLongSafe(data, "lastModified", System.currentTimeMillis());
                long remoteLastSyncDate = readLongSafe(data, "lastSyncDate", remoteLastModified);
                long deleteDate = readLongSafe(data, "deleteDate", 0L);
                boolean isDeletedRemote = readIsDeletedSafe(data);

                Account existing = mAccountDao.getAccountByFirestoreId(firestoreId);

                if (existing != null && !"SYNCED".equals(existing.getSyncStatus())) {
                    continue;
                }

                if (existing != null) {
                    if ((remoteLastModified >= existing.getLastModified() || remoteLastSyncDate > existing.getLastSyncDate())
                            && !"DELETED".equals(existing.getSyncStatus())) {
                        existing.setDeleteDate(deleteDate);
                        existing.setAccountName((String) data.get("accountName"));
                        existing.setPhoneNumber((String) data.get("phoneNumber"));
                        existing.setAccountType((String) data.get("accountType"));
                        existing.setLastModified(remoteLastModified);
                        existing.setLastSyncDate(remoteLastSyncDate);
                        String acTypeFsId = (String) data.get("acTypeFirestoreId");
                        if (acTypeFsId != null) existing.setAcTypeFirestoreId(acTypeFsId);
                        existing.setSyncStatus("SYNCED");
                        existing.setDeletedBy((String) data.get("deletedBy"));
                        existing.setDeleted(isDeletedRemote);
                        mAccountDao.update(existing);
                    }
                } else {
                    Account newAccount = new Account();
                    newAccount.setFirestoreId(firestoreId != null ? firestoreId : "");
                    newAccount.setDeleteDate(deleteDate);
                    newAccount.setAccountName((String) data.get("accountName"));
                    newAccount.setPhoneNumber((String) data.get("phoneNumber"));
                    newAccount.setAccountType((String) data.get("accountType"));
                    String owner = (String) data.get("ownerUID");
                    newAccount.setOwnerUID(owner != null ? owner : (currentUserId != null ? currentUserId : ""));
                    newAccount.setLastModified(remoteLastModified);
                    newAccount.setLastSyncDate(remoteLastSyncDate);
                    newAccount.setSyncStatus("SYNCED");
                    String acTypeFsId = (String) data.get("acTypeFirestoreId");
                    newAccount.setAcTypeFirestoreId(acTypeFsId != null ? acTypeFsId : "");
                    newAccount.setDeletedBy((String) data.get("deletedBy"));
                    newAccount.setDeleted(isDeletedRemote);
                    mAccountDao.insert(newAccount);
                }
            }
        });
    }

    public void batchUpsertTransactions(List<Map<String, Object>> changesBatch) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            for (Map<String, Object> change : changesBatch) {
                String changeType = (String) change.get("type");
                String firestoreId = (String) change.get("firestoreId");
                Map<String, Object> data = safeCastToMap(change.get("data"));

                if ("REMOVED".equals(changeType)) {
                    mTransactionDao.deleteByFirestoreId(firestoreId);
                    continue;
                }

                long deleteDate = readLongSafe(data, "deleteDate", 0L);
                long remoteLastModified = readLongSafe(data, "lastModified", System.currentTimeMillis());
                long remoteLastSyncDate = readLongSafe(data, "lastSyncDate", remoteLastModified);
                boolean isDeletedRemote = readIsDeletedSafe(data);

                Object timestampObj = data.get("timestamp");
                Date timestamp;
                if (timestampObj instanceof Timestamp) {
                    timestamp = ((Timestamp) timestampObj).toDate();
                } else if (timestampObj instanceof Date) {
                    timestamp = (Date) timestampObj;
                } else {
                    timestamp = new Date();
                }

                String accountFirestoreId = (String) data.get("accountFirestoreId");
                Account parentAccount = accountFirestoreId != null
                        ? mAccountDao.getAccountByFirestoreId(accountFirestoreId) : null;
                if (parentAccount == null) {
                    Log.e(TAG, "CRITICAL: Parent account not found after sync: " + accountFirestoreId);
                    continue;
                }

                Transaction existing = mTransactionDao.getTransactionByFirestoreId(firestoreId);
                if (existing != null && !"SYNCED".equals(existing.getSyncStatus())) {
                    continue;
                }

                String currencyFirestoreId = (String) data.get("currencyFirestoreId");
                int resolvedCurrencyId = readIntSafe(data, "currencyId", 0);
                if (currencyFirestoreId != null && !currencyFirestoreId.isEmpty()) {
                    Currency matchedCur = currencyDao.getCurrencyByFirestoreId(currencyFirestoreId);
                    if (matchedCur != null) {
                        resolvedCurrencyId = matchedCur.getId();
                    }
                }
                if (resolvedCurrencyId <= 0) {
                    resolvedCurrencyId = currencyDao.getFirstIdCurrency();
                }
                if (currencyFirestoreId == null || currencyFirestoreId.isEmpty()) {
                    String fetchedCurFsId = currencyDao.getCurrencyFirestoreId(resolvedCurrencyId);
                    currencyFirestoreId = fetchedCurFsId != null ? fetchedCurFsId : "";
                }

                try {
                    if (existing != null) {
                        if ((remoteLastModified >= existing.getLastModified() || remoteLastSyncDate > existing.getLastSyncDate())
                                && !Objects.equals(existing.getSyncStatus(), "DELETED")) {
                            existing.setAccountId(parentAccount.getId());
                            existing.setAmount(readDoubleSafe(data, "amount", existing.getAmount()));
                            existing.setDetails((String) data.get("details"));
                            existing.setImageEncoded((String) data.get("imageEncoded"));
                            existing.setCurrencyId(resolvedCurrencyId);
                            existing.setCurrencyFirestoreId(currencyFirestoreId);
                            existing.setType(readIntSafe(data, "type", existing.getType()));
                            existing.setAccountFirestoreId(accountFirestoreId);
                            existing.setTimestamp(timestamp);
                            existing.setDeleteDate(deleteDate);
                            existing.setBillType((String) data.get("billType"));
                            existing.setImportID(readIntSafe(data, "importID", 0));
                            existing.setLastModified(remoteLastModified);
                            existing.setLastSyncDate(remoteLastSyncDate);
                            existing.setSyncStatus("SYNCED");
                            existing.setDeletedBy((String) data.get("deletedBy"));
                            existing.setDeleted(isDeletedRemote);
                            mTransactionDao.update(existing);
                        }
                    } else {
                        Transaction newTx = new Transaction();
                        newTx.setFirestoreId(firestoreId);
                        newTx.setAccountId(parentAccount.getId());
                        newTx.setAmount(readDoubleSafe(data, "amount", 0.0));
                        newTx.setDetails((String) data.get("details"));
                        newTx.setCurrencyId(resolvedCurrencyId);
                        newTx.setCurrencyFirestoreId(currencyFirestoreId);
                        newTx.setType(readIntSafe(data, "type", -1));
                        newTx.setTimestamp(timestamp);
                        newTx.setDeleteDate(deleteDate);
                        newTx.setImageEncoded((String) data.get("imageEncoded"));
                        newTx.setDeletedBy((String) data.get("deletedBy"));
                        newTx.setDeleted(isDeletedRemote);
                        newTx.setAccountFirestoreId(accountFirestoreId);

                        Object billTypeObj = data.get("billType");
                        if (billTypeObj != null) newTx.setBillType(billTypeObj.toString());
                        newTx.setImportID(readIntSafe(data, "importID", 0));

                        Object ownerUIDObj = data.get("ownerUID");
                        newTx.setOwnerUID(ownerUIDObj != null ? ownerUIDObj.toString() : (currentUserId != null ? currentUserId : ""));
                        newTx.setLastModified(remoteLastModified);
                        newTx.setLastSyncDate(remoteLastSyncDate);
                        newTx.setSyncStatus("SYNCED");
                        mTransactionDao.insert(newTx);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "batchUpsertTransactions error: " + e.getMessage(), e);
                }
            }
        });
    }

    public void batchUpsertCurrencies(List<Map<String, Object>> changesBatch) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            for (Map<String, Object> change : changesBatch) {
                String changeType = (String) change.get("type");
                String firestoreId = (String) change.get("firestoreId");
                Map<String, Object> data = safeCastToMap(change.get("data"));

                if ("REMOVED".equals(changeType)) {
                    currencyDao.deleteByFirestoreId(firestoreId);
                    continue;
                }

                long deleteDate = readLongSafe(data, "deleteDate", 0L);
                long remoteLastModified = readLongSafe(data, "lastModified", System.currentTimeMillis());
                long remoteLastSyncDate = readLongSafe(data, "lastSyncDate", remoteLastModified);
                boolean isDeletedRemote = readIsDeletedSafe(data);
                boolean isDefaultRemote = readIsDefaultSafe(data);

                Currency existing = currencyDao.getCurrencyByFirestoreId(firestoreId);
                if (existing != null && !"SYNCED".equals(existing.getSyncStatus())) {
                    continue;
                }

                if (existing != null) {
                    if ((remoteLastModified >= existing.getLastModified() || remoteLastSyncDate > existing.getLastSyncDate())
                            && !Objects.equals(existing.getSyncStatus(), "DELETED")) {
                        existing.setName((String) data.get("name"));
                        if (data.get("code") != null) existing.setCode((String) data.get("code"));
                        if (data.get("symbol") != null) existing.setSymbol((String) data.get("symbol"));
                        existing.setOwnerUID((String) data.get("ownerUID"));
                        existing.setLastModified(remoteLastModified);
                        existing.setLastSyncDate(remoteLastSyncDate);
                        existing.setSyncStatus("SYNCED");
                        existing.setDeleteDate(deleteDate);
                        existing.setDefault(isDefaultRemote);
                        existing.setDeletedBy((String) data.get("deletedBy"));
                        existing.setDeleted(isDeletedRemote);
                        currencyDao.update(existing);
                    }
                } else {
                    Currency newCurrency = new Currency();
                    newCurrency.setFirestoreId(firestoreId);
                    newCurrency.setName((String) data.get("name"));
                    newCurrency.setCode((String) data.get("code"));
                    newCurrency.setSymbol((String) data.get("symbol"));
                    newCurrency.setOwnerUID((String) data.get("ownerUID"));
                    newCurrency.setLastModified(remoteLastModified);
                    newCurrency.setLastSyncDate(remoteLastSyncDate);
                    newCurrency.setDeleteDate(deleteDate);
                    newCurrency.setDefault(isDefaultRemote);
                    newCurrency.setDeletedBy((String) data.get("deletedBy"));
                    newCurrency.setDeleted(isDeletedRemote);
                    newCurrency.setSyncStatus("SYNCED");
                    currencyDao.insert(newCurrency);
                }
            }
        });
    }

    public void batchUpsertAccountTypes(List<Map<String, Object>> changesBatch) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            for (Map<String, Object> change : changesBatch) {
                String changeType = (String) change.get("type");
                String firestoreId = (String) change.get("firestoreId");
                Map<String, Object> data = safeCastToMap(change.get("data"));

                if ("REMOVED".equals(changeType)) {
                    accountTypeDao.deleteByFirestoreId(firestoreId);
                    continue;
                }

                long deleteDate = readLongSafe(data, "deleteDate", 0L);
                long remoteLastModified = readLongSafe(data, "lastModified", System.currentTimeMillis());
                long remoteLastSyncDate = readLongSafe(data, "lastSyncDate", remoteLastModified);
                boolean isDeletedRemote = readIsDeletedSafe(data);
                boolean isDefaultRemote = readIsDefaultSafe(data);

                AccountType existing = accountTypeDao.getAccountTypeByFirestoreId(firestoreId);
                if (existing != null && !"SYNCED".equals(existing.getSyncStatus())) {
                    continue;
                }

                if (existing != null) {
                    if ((remoteLastModified >= existing.getLastModified() || remoteLastSyncDate > existing.getLastSyncDate())
                            && !Objects.equals(existing.getSyncStatus(), "DELETED")) {
                        existing.setName((String) data.get("name"));
                        existing.setOwnerUID((String) data.get("ownerUID"));
                        existing.setLastModified(remoteLastModified);
                        existing.setLastSyncDate(remoteLastSyncDate);
                        existing.setSyncStatus("SYNCED");
                        existing.setDeleteDate(deleteDate);
                        existing.setDefault(isDefaultRemote);
                        existing.setDeletedBy((String) data.get("deletedBy"));
                        existing.setDeleted(isDeletedRemote);
                        accountTypeDao.update(existing);
                    }
                } else {
                    AccountType newType = new AccountType();
                    newType.setFirestoreId(firestoreId);
                    newType.setName((String) data.get("name"));
                    newType.setOwnerUID((String) data.get("ownerUID"));
                    newType.setLastModified(remoteLastModified);
                    newType.setLastSyncDate(remoteLastSyncDate);
                    newType.setSyncStatus("SYNCED");
                    newType.setDeleteDate(deleteDate);
                    newType.setDefault(isDefaultRemote);
                    newType.setDeletedBy((String) data.get("deletedBy"));
                    newType.setDeleted(isDeletedRemote);
                    accountTypeDao.insert(newType);
                }
            }
        });
    }

    public void batchUpsertBudgetCategories(List<Map<String, Object>> changesBatch) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            for (Map<String, Object> change : changesBatch) {
                String changeType = (String) change.get("type");
                String firestoreId = (String) change.get("firestoreId");
                Map<String, Object> data = safeCastToMap(change.get("data"));

                if ("REMOVED".equals(changeType)) {
                    budgetCategoryDao.deleteByFirestoreId(firestoreId);
                    continue;
                }

                long remoteLastModified = readLongSafe(data, "lastModified", System.currentTimeMillis());
                long remoteLastSyncDate = readLongSafe(data, "lastSyncDate", remoteLastModified);
                long deleteDate = readLongSafe(data, "deleteDate", 0L);
                boolean isDeletedRemote = readIsDeletedSafe(data);
                boolean isDefaultRemote = readIsDefaultSafe(data);
                String code = (String) data.get("code");

                BudgetCategory existing = budgetCategoryDao.getByFirestoreIdSync(firestoreId);
                if (existing == null && isDefaultRemote && code != null && !code.isEmpty()) {
                    existing = budgetCategoryDao.getByCodeSync(code);
                }
                if (existing != null && !"SYNCED".equals(existing.getSyncStatus())) {
                    continue;
                }

                if (existing != null) {
                    if (remoteLastModified >= existing.getLastModified() || remoteLastSyncDate > existing.getLastSyncDate()) {
                        existing.setFirestoreId(firestoreId);
                        existing.setOwnerUID((String) data.get("ownerUID"));
                        if (code != null) existing.setCode(code);
                        if (data.get("name") != null) existing.setName((String) data.get("name"));
                        if (data.get("type") != null) existing.setType((String) data.get("type"));
                        if (data.get("iconKey") != null) existing.setIconKey((String) data.get("iconKey"));
                        if (data.get("colorHex") != null) existing.setColorHex((String) data.get("colorHex"));
                        existing.setDefault(isDefaultRemote);
                        existing.setSortOrder(readIntSafe(data, "sortOrder", existing.getSortOrder()));
                        existing.setDeleted(isDeletedRemote);
                        existing.setDeleteDate(deleteDate);
                        existing.setDeletedBy((String) data.get("deletedBy"));
                        existing.setLastModified(remoteLastModified);
                        existing.setLastSyncDate(remoteLastSyncDate);
                        existing.setSyncStatus("SYNCED");
                        budgetCategoryDao.update(existing);
                    }
                } else {
                    BudgetCategory cat = new BudgetCategory();
                    cat.setFirestoreId(firestoreId);
                    cat.setOwnerUID((String) data.get("ownerUID"));
                    cat.setCode(code != null ? code : "");
                    cat.setName(data.get("name") != null ? (String) data.get("name") : "");
                    cat.setType(data.get("type") != null ? (String) data.get("type") : BudgetCategory.TYPE_EXPENSE);
                    cat.setIconKey(data.get("iconKey") != null ? (String) data.get("iconKey") : "other");
                    cat.setColorHex(data.get("colorHex") != null ? (String) data.get("colorHex") : "#06B6D4");
                    cat.setDefault(isDefaultRemote);
                    cat.setSortOrder(readIntSafe(data, "sortOrder", 100));
                    cat.setDeleted(isDeletedRemote);
                    cat.setDeleteDate(deleteDate);
                    cat.setDeletedBy((String) data.get("deletedBy"));
                    cat.setLastModified(remoteLastModified);
                    cat.setLastSyncDate(remoteLastSyncDate);
                    cat.setSyncStatus("SYNCED");
                    budgetCategoryDao.insert(cat);
                }
            }
        });
    }

    public void batchUpsertBudgetLimits(List<Map<String, Object>> changesBatch) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            for (Map<String, Object> change : changesBatch) {
                String changeType = (String) change.get("type");
                String firestoreId = (String) change.get("firestoreId");
                Map<String, Object> data = safeCastToMap(change.get("data"));

                if ("REMOVED".equals(changeType)) {
                    monthlyBudgetLimitDao.deleteByFirestoreId(firestoreId);
                    continue;
                }

                long remoteLastModified = readLongSafe(data, "lastModified", System.currentTimeMillis());
                long remoteLastSyncDate = readLongSafe(data, "lastSyncDate", remoteLastModified);
                long deleteDate = readLongSafe(data, "deleteDate", 0L);
                boolean isDeletedRemote = readIsDeletedSafe(data);
                String yearMonth = (String) data.get("yearMonth");
                if (yearMonth == null || yearMonth.isEmpty()) continue;

                String curFsId = (String) data.get("currencyFirestoreId");
                int resolvedCurrencyId = readIntSafe(data, "currencyId", 0);
                if (curFsId != null && !curFsId.isEmpty()) {
                    Currency matchedCur = currencyDao.getCurrencyByFirestoreId(curFsId);
                    if (matchedCur != null) resolvedCurrencyId = matchedCur.getId();
                }
                if (resolvedCurrencyId <= 0) resolvedCurrencyId = currencyDao.getFirstIdCurrency();
                if (resolvedCurrencyId <= 0) continue;

                MonthlyBudgetLimit existing = monthlyBudgetLimitDao.getByFirestoreIdSync(firestoreId);
                if (existing == null) {
                    existing = monthlyBudgetLimitDao.getExactLimitSync(yearMonth, resolvedCurrencyId);
                }
                if (existing != null && !"SYNCED".equals(existing.getSyncStatus())) {
                    continue;
                }

                if (existing != null) {
                    if (remoteLastModified >= existing.getLastModified() || remoteLastSyncDate > existing.getLastSyncDate()) {
                        existing.setFirestoreId(firestoreId);
                        existing.setOwnerUID((String) data.get("ownerUID"));
                        existing.setCurrencyId(resolvedCurrencyId);
                        existing.setCurrencyFirestoreId(curFsId != null ? curFsId : "");
                        existing.setYearMonth(yearMonth);
                        existing.setLimitAmount(readDoubleSafe(data, "limitAmount", existing.getLimitAmount()));
                        existing.setDeleted(isDeletedRemote);
                        existing.setDeleteDate(deleteDate);
                        existing.setDeletedBy((String) data.get("deletedBy"));
                        existing.setLastModified(remoteLastModified);
                        existing.setLastSyncDate(remoteLastSyncDate);
                        existing.setSyncStatus("SYNCED");
                        monthlyBudgetLimitDao.update(existing);
                    }
                } else {
                    MonthlyBudgetLimit limit = new MonthlyBudgetLimit();
                    limit.setFirestoreId(firestoreId);
                    limit.setOwnerUID((String) data.get("ownerUID"));
                    limit.setCurrencyId(resolvedCurrencyId);
                    limit.setCurrencyFirestoreId(curFsId != null ? curFsId : "");
                    limit.setYearMonth(yearMonth);
                    limit.setLimitAmount(readDoubleSafe(data, "limitAmount", 0.0));
                    limit.setDeleted(isDeletedRemote);
                    limit.setDeleteDate(deleteDate);
                    limit.setDeletedBy((String) data.get("deletedBy"));
                    limit.setLastModified(remoteLastModified);
                    limit.setLastSyncDate(remoteLastSyncDate);
                    limit.setSyncStatus("SYNCED");
                    monthlyBudgetLimitDao.insert(limit);
                }
            }
        });
    }

    public void batchUpsertBudgetEntries(List<Map<String, Object>> changesBatch) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            for (Map<String, Object> change : changesBatch) {
                String changeType = (String) change.get("type");
                String firestoreId = (String) change.get("firestoreId");
                Map<String, Object> data = safeCastToMap(change.get("data"));

                if ("REMOVED".equals(changeType)) {
                    budgetEntryDao.deleteByFirestoreId(firestoreId);
                    continue;
                }

                long remoteLastModified = readLongSafe(data, "lastModified", System.currentTimeMillis());
                long remoteLastSyncDate = readLongSafe(data, "lastSyncDate", remoteLastModified);
                long deleteDate = readLongSafe(data, "deleteDate", 0L);
                boolean isDeletedRemote = readIsDeletedSafe(data);

                BudgetEntry existing = budgetEntryDao.getByFirestoreIdSync(firestoreId);
                if (existing != null && !"SYNCED".equals(existing.getSyncStatus())) {
                    continue;
                }

                String curFsId = (String) data.get("currencyFirestoreId");
                int resolvedCurrencyId = readIntSafe(data, "currencyId", 0);
                if (curFsId != null && !curFsId.isEmpty()) {
                    Currency matchedCur = currencyDao.getCurrencyByFirestoreId(curFsId);
                    if (matchedCur != null) resolvedCurrencyId = matchedCur.getId();
                }
                if (resolvedCurrencyId <= 0) resolvedCurrencyId = currencyDao.getFirstIdCurrency();
                if (resolvedCurrencyId <= 0) continue;

                String catFsId = (String) data.get("categoryFirestoreId");
                String catCode = (String) data.get("categoryCode");
                String type = data.get("type") != null ? (String) data.get("type") : BudgetEntry.TYPE_EXPENSE;

                int resolvedCategoryId = 0;
                if (catFsId != null && !catFsId.isEmpty()) {
                    BudgetCategory byFs = budgetCategoryDao.getByFirestoreIdSync(catFsId);
                    if (byFs != null) resolvedCategoryId = byFs.getId();
                }
                if (resolvedCategoryId <= 0 && catCode != null && !catCode.isEmpty()) {
                    Integer byCode = budgetCategoryDao.getCategoryIdByCodeSync(catCode);
                    if (byCode != null && byCode > 0) resolvedCategoryId = byCode;
                }
                if (resolvedCategoryId <= 0) {
                    String fallbackCode = BudgetEntry.TYPE_INCOME.equals(type) ? "income_other" : "other";
                    Integer fb = budgetCategoryDao.getCategoryIdByCodeSync(fallbackCode);
                    if (fb != null && fb > 0) resolvedCategoryId = fb;
                }
                if (resolvedCategoryId <= 0) {
                    resolvedCategoryId = readIntSafe(data, "categoryId", 1);
                }

                long entryDate = readLongSafe(data, "entryDate", remoteLastModified);
                String yearMonth = data.get("yearMonth") != null
                        ? (String) data.get("yearMonth") : BudgetUtils.toYearMonth(entryDate);

                if (existing != null) {
                    if (remoteLastModified >= existing.getLastModified() || remoteLastSyncDate > existing.getLastSyncDate()) {
                        existing.setCategoryId(resolvedCategoryId);
                        existing.setCategoryFirestoreId(catFsId != null ? catFsId : "");
                        existing.setCurrencyId(resolvedCurrencyId);
                        existing.setCurrencyFirestoreId(curFsId != null ? curFsId : "");
                        existing.setType(type);
                        existing.setAmount(readDoubleSafe(data, "amount", existing.getAmount()));
                        existing.setNote(data.get("note") != null ? (String) data.get("note") : "");
                        existing.setEntryDate(entryDate);
                        existing.setYearMonth(yearMonth);
                        existing.setDeleted(isDeletedRemote);
                        existing.setDeleteDate(deleteDate);
                        existing.setDeletedBy((String) data.get("deletedBy"));
                        existing.setLastModified(remoteLastModified);
                        existing.setLastSyncDate(remoteLastSyncDate);
                        existing.setSyncStatus("SYNCED");
                        budgetEntryDao.update(existing);
                    }
                } else {
                    BudgetEntry entry = new BudgetEntry();
                    entry.setFirestoreId(firestoreId);
                    entry.setOwnerUID((String) data.get("ownerUID"));
                    entry.setCategoryId(resolvedCategoryId);
                    entry.setCategoryFirestoreId(catFsId != null ? catFsId : "");
                    entry.setCurrencyId(resolvedCurrencyId);
                    entry.setCurrencyFirestoreId(curFsId != null ? curFsId : "");
                    entry.setType(type);
                    entry.setAmount(readDoubleSafe(data, "amount", 0.0));
                    entry.setNote(data.get("note") != null ? (String) data.get("note") : "");
                    entry.setEntryDate(entryDate);
                    entry.setYearMonth(yearMonth);
                    entry.setDeleted(isDeletedRemote);
                    entry.setDeleteDate(deleteDate);
                    entry.setDeletedBy((String) data.get("deletedBy"));
                    entry.setLastModified(remoteLastModified);
                    entry.setLastSyncDate(remoteLastSyncDate);
                    entry.setSyncStatus("SYNCED");
                    budgetEntryDao.insert(entry);
                }
            }
        });
    }

    public LiveData<User> getUserProfile() {
        return mUserDao.getUserProfile();
    }

    public void updateUserProfile(User user) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            user.setSyncStatus("EDITED");
            user.setLastModified(System.currentTimeMillis());
            mUserDao.upsert(user);
        });
    }

    public List<Currency> getAllCurrenciesBlocking() {
        return currencyDao.getAllCurrenciesBlocking();
    }

    public LiveData<List<AccountType>> getAllAccountTypes() {
        // افترض أن DAO لديه هذه الدالة
        return accountTypeDao.getAllAccountTypes();
    }

    public void insertAccountType(AccountType accountType) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // افترض أن DAO لديه هذه الدالة
            accountTypeDao.insert(accountType);
            triggerSync(); // تشغيل المزامنة بعد إضافة عملة جديدة
        });
    }

    public List<AccountType> getAllAccountTypesBlockingReport() {
        return accountTypeDao.getAllAccountTypesBlockingReport();
    }

    public LiveData<List<String>> getCurrenciesWithTransactions() {
        if (currentUserId == null) {
            return new MutableLiveData<>(new ArrayList<>()); // إرجاع قائمة فارغة إذا لم يكن هناك مستخدم
        }
        return mTransactionDao.getCurrenciesWithTransactions(currentUserId);

    }

    public LiveData<List<String>> getActiveCurrenciesForAccount(int accountId) {
        return mTransactionDao.getActiveCurrenciesForAccount(accountId);
    }

    public Double getBalanceBeforeDate(int accountId, long startDate) {
        Double balance = mTransactionDao.getBalanceBeforeDate(accountId, startDate);
        return balance;
    }
    public List<TransactionWithAccount> getConsolidatedMovementAllCurrencies(String acTypeFirestoreId, int currency, long startDate, long endDate) {
        return mTransactionDao.getConsolidatedMovementAllCurrencies(acTypeFirestoreId, currency, startDate, endDate);
    }

    public List<MonthlySummary> getMonthlySummaryForAccountAllCurrencies(int accountId, int currency) {
        return mTransactionDao.getMonthlySummaryForAccountAllCurrencies(accountId, currency);
    }


    public List<MonthlySummary> getMonthlySummaryByAccountTypeByCurrency(String acTypeFirestoreId, int currency) {
        return mTransactionDao.getMonthlySummaryByAccountTypeByCurrency(acTypeFirestoreId, currency);
    }


    public Double getBalanceByAccountTypeBeforeDate(String acTypeFirestoreId, long startDate) {
        return mTransactionDao.getBalanceByAccountTypeBeforeDate(acTypeFirestoreId, startDate);
    }

    public int getTransactionCountForCurrency(int currencyId) {
        return currencyDao.getTransactionCountForCurrency(currencyId);
    }

     public void updateCurrency(Currency currencyToUpdate, String name, String symbol, String code) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            String oldName = currencyToUpdate.name;
            currencyToUpdate.name = name;
            currencyToUpdate.setName(name);
            currencyToUpdate.setSymbol(symbol);
            currencyToUpdate.setCode(code);
            currencyToUpdate.setSyncStatus("EDITED");
            currencyToUpdate.setLastModified(System.currentTimeMillis());
            currencyDao.update(currencyToUpdate);

            triggerSync();
        });
    }

    public void deleteCurrency(Currency currency) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (currency.getFirestoreId() != null && !currency.getFirestoreId().isEmpty()) {
                DeletionLog log = new DeletionLog();
                log.setFirestoreId(currency.getFirestoreId());
                log.setCollectionName("currencies");
                mDeletionLogDao.insert(log);
            }
        });
        AppDatabase.databaseWriteExecutor.execute(() -> {
            currency.setSyncStatus("DELETED");
            currency.setDeleted(true);
            currency.setDeletedBy(currentUserName);
            currency.setLastModified(System.currentTimeMillis());
            currencyDao.update(currency);
            triggerSync();
        });

    }

    public int getAccountCountForType(String typeName) {
        return accountTypeDao.getAccountCountForType(typeName);
    }

    public void updateAccountType(AccountType typeToUpdate, String newName) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            String oldName = typeToUpdate.name;
            long timestamp = System.currentTimeMillis();

            // 1. تحديث نوع الحساب في كل الحسابات المرتبطة
//            mAccountDao.updateAccountTypeInAccounts(oldName, newName, timestamp);
            mAccountDao.updateAccountTypeInAccounts(oldName, newName);

            // 2. تحديث كائن نوع الحساب نفسه
            typeToUpdate.name = newName;
            typeToUpdate.setSyncStatus("EDITED");
            typeToUpdate.setLastModified(timestamp);
            accountTypeDao.update(typeToUpdate);

            triggerSync();
        });
    }

    public void deleteAccountType(AccountType accountType) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (accountType.getFirestoreId() != null && !accountType.getFirestoreId().isEmpty()) {
                DeletionLog log = new DeletionLog();
                log.setFirestoreId(accountType.getFirestoreId());
                log.setCollectionName("accountTypes");
                mDeletionLogDao.insert(log);
            }
        });
        AppDatabase.databaseWriteExecutor.execute(() -> {
            accountType.setSyncStatus("DELETED");
            accountType.setDeleted(true);
            accountType.setDeletedBy(currentUserName);
            accountType.setLastModified(System.currentTimeMillis());
            accountTypeDao.update(accountType);
            triggerSync();
        });
    }

    public AccountType getAccountTypeByName(String name) {
        return accountTypeDao.getAccountTypeByName(name);
    }

    public void setUserUID(String uid) {
        this.currentUserId = uid;
    }

    public int findDuplicateTransaction(int accountId, long dateWithoutTime, double amount, int type, int currency) {
        // يجب أن يتم استدعاؤها من خيط خلفي
        return mTransactionDao.findDuplicateTransaction(accountId, dateWithoutTime, amount, type, currency);
    }

    public int getMaxImportId() {
        return mTransactionDao.getMaxImportId();
    }

    public List<Transaction> getTransactionsByImportId(int importId) {
        return mTransactionDao.getTransactionsByImportId(importId);
    }

    public void insertInvoiceTransactions(List<Transaction> transactions) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            db.runInTransaction(() -> {
                for (Transaction tx : transactions) {
                    if (tx.getOwnerUID() == null) {
                        String accountFirestoreId = mAccountDao.getAccountFireStoreById(tx.getAccountId());
                        String currencyFirestoreId = currencyDao.getCurrencyFirestoreId(tx.getCurrencyId());
                        tx.setCurrencyFirestoreId(currencyFirestoreId);
                        tx.setAccountFirestoreId(accountFirestoreId);
                        tx.setOwnerUID(currentUserId);
                    }
                    mTransactionDao.insert(tx);
                    Log.e("transactions", tx.toString());
                }
            });
            triggerSync();
        });
    }
    public void insertInvoiceTransaction(Transaction transactions) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            db.runInTransaction(() -> {

                    if (transactions.getOwnerUID() == null) {
                        String accountFirestoreId = mAccountDao.getAccountFireStoreById(transactions.getAccountId());
                       if(transactions.getCurrencyFirestoreId() == null) {
                           String currencyFirestoreId = currencyDao.getCurrencyFirestoreId(transactions.getCurrencyId());
                           transactions.setCurrencyFirestoreId(currencyFirestoreId);
                       }

                        transactions.setAccountFirestoreId(accountFirestoreId);
                        transactions.setOwnerUID(currentUserId);
                    }
                    mTransactionDao.insert(transactions);
                    Log.e("transactions", transactions.toString());

            });
        });
    }

    public void deleteInvoiceByImportId(int importId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // نستخدم الحذف الناعم لكل عملية في الفاتورة
            List<Transaction> transactions = mTransactionDao.getTransactionsByImportId(importId);
            for (Transaction tx : transactions) {
                tx.setSyncStatus("DELETED");
                tx.setDeleted(true);
                tx.setDeletedBy(currentUserName);
                tx.setLastModified(System.currentTimeMillis());
                mTransactionDao.update(tx);
                deleteTransaction(tx);
            }
            triggerSync();
        });
    }

    public void insertNeAccount(Account account) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // جديد: إنشاء ID فريد على الجهاز قبل الحفظ
            account.setFirestoreId(UUIDGenerator.generateSequentialUUID());
            account.setSyncStatus("NEW");
            account.setLastModified(System.currentTimeMillis());
            Log.i(TAG, "insertNeAccount: " + account.getAccountName() + "\n" +
                    account.getAccountType() + "\n" +
                    account.getOwnerUID() + "\n" +
                    account.getAcTypeFirestoreId() + "\n" +
                    account.getPhoneNumber() + "\n" +
                    account.getSyncStatus() + "\n" +
                    account.getLastModified());
//            mAccountDao.insert(account);
            long newId = mAccountDao.insert(account);
            Log.i(TAG, "Account inserted with ID: " + newId);

            Account savedAccount = mAccountDao.getAccountByNameBlocking(account.getAccountName());
            if (savedAccount != null) {
                Log.i(TAG, "Account verified in database - ID: " + savedAccount.getId() + "\n" +
                        "Account verified in database - OwnerUID: " + account.getOwnerUID() + "\n" +
                        "Account verified in database - AccountType: " + account.getAccountType() + "\n" +
                        "Account verified in database - AcTypeFirestoreId: " + account.getAcTypeFirestoreId() + "\n" +
                        "Account verified in database - PhoneNumber: " + account.getPhoneNumber() + "\n" +
                        "Account verified in database - SyncStatus: " + account.getSyncStatus() + "\n" +
                        "Account verified in database - LastModified: " + account.getLastModified() + "\n" +
                        "Account verified in database - FirestoreId: " + account.getFirestoreId());
            } else {
                Log.e(TAG, "Account NOT found in database after insertion!");
            }
        });
    }

    public List<MonthlySummary> getGlobalMonthlySummary(int currency) {
        return mTransactionDao.getGlobalMonthlySummary(currency);
    }

    public List<AccountTotalsByCurrency> getAccountTotalsByCurrency(int accountId) {
        return mTransactionDao.getAccountTotalsByCurrency(accountId);
    }

    public LiveData<List<AccountWithBalance>> getAccountsWithBalances(int currencyId, String filterType, String searchQuery) {
        return mAccountDao.getAccountsWithBalances(currentUserId, currencyId, filterType, "%" + searchQuery + "%");
    }

    public LiveData<List<Account>> getAllAccounts() {
        return mAccountDao.getAllAccounts(currentUserId);
    }

    public String getFirstCurrency() {
        AppDatabase db = AppDatabase.getDatabase(context);
        // التحقق من أن قاعدة البيانات مفتوحة وليست في مرحلة الإغلاق
        if (!db.isOpen()) {
            db = AppDatabase.getDatabase(context);
        }
        if (db != null && db.isOpen()) {
            try {
                return currencyDao.getFirstCurrencyById();
            } catch (Exception e) {
                Log.e("DaftreeRepository", "Error fetching currency", e);
                addCurrencyToDatabase(context.getString(R.string.localCurrencyId));
                return currencyDao.getFirstCurrencyById();
            }
        }
        Log.w("DaftreeRepository", "Database is closed, skipping query");
        addCurrencyToDatabase(context.getString(R.string.localCurrencyId));
        return currencyDao.getFirstCurrencyById();
    }
    private void addCurrencyToDatabase(String name) {
        String uid;
        Resources localizedResources = LanguageManager.getLocalizedResources(context);
        final String symbole = localizedResources.getString(R.string.currency_symbol_loc);

        if (isGuest) {
            uid = guestUID;
        } else {
            uid = AppAuth.getUserID(context);
        }
        Log.d(TAG, "إضافة العمله");
        AppDatabase.databaseWriteExecutor.execute(() -> {
//            int currencyCount = db.currencyDao().getCurrencyCount();
//            int accTypeCount = db.accountTypeDao().getAccountTypeCount();
            if (db.currencyDao().getCurrencyCount() < 1) {
                Currency existing = getCurrencyByName(name);
                Log.e(TAG, "defaultCurrencyName: " + name);
                if (existing == null) {
                    Currency newCurrency = new Currency();
                    newCurrency.setName(name);
                    newCurrency.setOwnerUID(uid);
                    newCurrency.setSyncStatus("NEW");
                    newCurrency.setDefault(true);
                    newCurrency.setSymbol(symbole);
                    newCurrency.setCode(symbole);
                    newCurrency.setFirestoreId(UUIDGenerator.generateSequentialUUID().toString());
                    newCurrency.setLastModified(System.currentTimeMillis());
                    sharedPrefsManager.setLocalCurrency(name.trim());
                    MyApplication.defaultCurrencyName = (name.trim());
                    db.currencyDao().insert(newCurrency);
                    addDollarCurrenncy();
                }
            }
        });
    }

    private void addDollarCurrenncy() {
        String uid;
        Resources localizedResources = LanguageManager.getLocalizedResources(context);
        String name = localizedResources.getString(R.string._408);
        if (isGuest) {
            uid = guestUID;
        } else {
            uid = AppAuth.getUserID(context);
        }
        AppDatabase.databaseWriteExecutor.execute(() -> {
            int currencyCount = db.currencyDao().getCurrencyCount();
            if (db.currencyDao().getCurrencyCount() < 1) {
                Currency existing = getCurrencyByName(name);
                Log.e(TAG, "defaultCurrencyName: " + name);
                if (existing == null) {
                    Currency newCurrency = new Currency();
                    newCurrency.setName(name);
                    newCurrency.setOwnerUID(uid);
                    newCurrency.setSymbol("$");
                    newCurrency.setCode("$");
                    newCurrency.setSyncStatus("NEW");
                    newCurrency.setDefault(false);
                    newCurrency.setFirestoreId(UUIDGenerator.generateSequentialUUID().toString());
                    newCurrency.setLastModified(System.currentTimeMillis());
                    db.currencyDao().insert(newCurrency);
                }
            }
        });
    }
    public LiveData<Account> getAccountById(int accountId) {
        return mAccountDao.getAccountById(accountId);
    }

    // --- دوال العمليات ---

    public LiveData<List<Transaction>> getTransactionsForAccount(int accountId, int currencyId, String searchQuery) {
        return mTransactionDao.getTransactionsForAccount(accountId, currencyId, searchQuery);
    }

    public LiveData<List<Transaction>> getLiveTransactionsForAccount(int accountId, int currencyId) {
        return mTransactionDao.getLiveTransactionsForAccount(accountId, currencyId);
    }

    public void insertTransaction(Transaction transaction) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            String accountFirestoreId = mAccountDao.getAccountFireStoreById(transaction.getAccountId());
            transaction.setAccountFirestoreId(accountFirestoreId);
            transaction.setFirestoreId(UUIDGenerator.generateSequentialUUID());
            transaction.setSyncStatus("NEW");
            transaction.setLastModified(System.currentTimeMillis());

            String currencyFirestoreId = currencyDao.getCurrencyFirestoreId(transaction.getCurrencyId());

            if (transaction.getOwnerUID() == null) {
                transaction.setOwnerUID(currentUserId);
            }
            transaction.setCurrencyFirestoreId(currencyFirestoreId);
//            Log.d(TAG, "Inserted transaction : " + transaction.getOwnerUID()+"\n"+
//                    transaction.getDetails()+"\n" + transaction.getBillType()+"\n"+
//                    transaction.getType()+"\n" + transaction.getTimestamp()+"\n"+
//                    transaction.getCurrencyId()+"\n" + transaction.getAmount()+"\n"+
//                    transaction.getAccountId()+"\n" + transaction.getFirestoreId()+"\n"+
//                    transaction.getAccountFirestoreId()+"\n");
            mTransactionDao.insert(transaction);
            triggerSync();
        });
    }

    public void insertTransactionInvoice(Transaction transaction) {
        AppDatabase.databaseWriteExecutor.execute(() -> {

            String accountFirestoreId = mAccountDao.getAccountFireStoreById(transaction.getAccountId());
            transaction.setAccountFirestoreId(accountFirestoreId);
            transaction.setFirestoreId(UUIDGenerator.generateSequentialUUID());
            transaction.setSyncStatus("NEW");
            transaction.setLastModified(System.currentTimeMillis());
            if (transaction.getOwnerUID() == null) {
                transaction.setOwnerUID(currentUserId);
            }
            mTransactionDao.insert(transaction);

        });
    }

    public void updateTransaction(Transaction transaction) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Transaction currentTransaction = mTransactionDao.getTransactionByIdBlocking(transaction.getId());
            if (currentTransaction == null) return;
            String currencyFirestoreId = currencyDao.getCurrencyFirestoreId(transaction.getCurrencyId());
            mTransactionDao.updateUserChanges(
                    transaction.getId(),
                    transaction.getAmount(),
                    transaction.getDetails(),
                    transaction.getCurrencyId(), // <-- تم التحديث هنا
                    transaction.getType(),
                    transaction.getTimestamp().getTime(),
                    System.currentTimeMillis(), currencyFirestoreId
            );
            triggerSync();
        });
    }

    public void updateInvoice(Transaction transaction) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Transaction currentTransaction = mTransactionDao.getTransactionByFirestoreId(transaction.getFirestoreId());
            if (currentTransaction == null) return;

            String currencyFirestoreId = currencyDao.getCurrencyFirestoreId(transaction.getCurrencyId());
            mTransactionDao.updateUserChanges(
                    transaction.getId(),
                    transaction.getAmount(),
                    transaction.getDetails(),
                    transaction.getCurrencyId(), // <-- تم التحديث هنا
                    transaction.getType(),
                    transaction.getTimestamp().getTime(),
                    System.currentTimeMillis(),
                    currencyFirestoreId
            );

        });
    }

    public void updateInvoiceTransactions(List<Transaction> transactions) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            db.runInTransaction(() -> {
                for (Transaction tx : transactions) {
                    if (tx.getOwnerUID() == null) {
                        tx.setOwnerUID(currentUserId);
                    }

                    String currencyFirestoreId = currencyDao.getCurrencyFirestoreId(tx.getCurrencyId());
                    tx.setCurrencyFirestoreId(currencyFirestoreId);
                    if (Objects.equals(tx.getSyncStatus(), "NEW")) {
//                        tx.setLastModified(System.currentTimeMillis());
                        mTransactionDao.insert(tx);
                    } else if (Objects.equals(tx.getSyncStatus(), "EDITED")) {
                        updateInvoice(tx);
                    } else {
                        deleteTransaction(tx);
                    }
                    Log.e("transactions", tx.toString());
                }
            });
            triggerSync();
        });
    }
    public List<AccountBalanceSummary> getAccountBalances(int accountId, int currencyId, long startDate, long endDate) {
        return mAccountDao.getAccountBalances(accountId, currencyId, startDate, endDate);
    }


    public List<Transaction> getTransactionsForAccountBlocking(int accountId, int currencyId) {
        return mTransactionDao.getTransactionsForAccountBlocking(accountId, currencyId);
    }

    public Currency getCurrencyByName(String name) {
        return currencyDao.getCurrencyByName(name);
    }

    public LiveData<List<Currency>> getAllCurrencies() {
        return currencyDao.getAllCurrencies();
    }
    public void insertCurrency(Currency currency) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            currencyDao.insert(currency);
            triggerSync();
        });
    }
    public String getCurrencyNameById(int currencyId) {
        return currencyDao.getCurrencyNameById(currencyId);
    }
    public String getCurrencyFirestoreId(int currencyId) {
        return currencyDao.getCurrencyFirestoreId(currencyId);
    }
    public List<AccountBalanceSummary> getAccountBalancesByType(String acTypeFirestoreId, int currencyId, long startDate, long endDate) {
        return mAccountDao.getAccountBalancesByType(acTypeFirestoreId, currencyId, startDate, endDate);
    }
    public List<AccountBalanceSummary> getAccountBalancesBynameType(String acType, int currencyId, long startDate, long endDate) {
        return mAccountDao.getAccountBalancesBynameType(acType, currencyId, startDate, endDate);
    }
    public String getAccountTypeNameByFirestoreId(String firestoreId) {
        return accountTypeDao.getAccountTypeNameByFirestoreId(firestoreId);
    }
    public List<Account> getAccountsByTypeBlocking(String firestoreId) {
        return mAccountDao.getAccountsByTypeBlocking(firestoreId);
    }
      public LiveData<List<AccountWithBalance>> getAccountsWithBalancesForceRefresh(int currencyId, String filterType, String searchQuery) {
        return mAccountDao.getAccountsWithBalances(currentUserId, currencyId, filterType, "%" + searchQuery + "%");
    }
    public int getRecentTransactionsCount() {
        return mTransactionDao.getTransactionsCount();
    }


      public List<Account> getAccounts() {
        Log.d("MainViewModel", "استدعاء getAccounts()");
        return mAccountDao.getAllAccounts();
    }

    private static Map<String, Object> safeCastToMap(Object obj) {
        if (obj instanceof Map) {
            try {
                return (Map<String, Object>) obj;
            } catch (ClassCastException e) {
                return Collections.emptyMap();
            }
        }
        return Collections.emptyMap();
    }

    public LiveData<List<ExternalAccountEntity>> getAllExternalAccounts() {
        return externalDebtDao.getAllExternalAccounts();
    }

    public LiveData<List<ExternalTransactionEntity>> getAllExternalTransactions() {
        return externalDebtDao.getAllTransactions();
    }

    public LiveData<List<ExternalTransactionEntity>> getTransactionsForAccountAndCurrency(String accountId, String currencyId) {
        return externalDebtDao.getTransactionsForAccountAndCurrency(accountId, currencyId);
    }


    public void syncHybridData(List<ExternalAccountEntity> validAccounts, List<ExternalTransactionEntity> incrementalTransactions) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            externalDebtDao.syncHybridData(validAccounts, incrementalTransactions);
        });
    }
    public List<String> getLocalAccountIdsBlocking() {
        return externalDebtDao.getLocalAccountIdsBlocking();
    }
       private boolean convertFirestoreBoolean(Object firestoreBool) {
        if (firestoreBool == null) {
            return false;
        }

        if (firestoreBool instanceof Boolean) {
            return (Boolean) firestoreBool;
        }

        try {
            return Boolean.parseBoolean(firestoreBool.toString());
        } catch (Exception e) {
            return false;
        }
    }// --- دوال الأمان لاستخراج البيانات (ضعها في نهاية DaftreeRepository) ---

    public LiveData<List<RecurringOperation>> getActiveRecurringOperations() {
        String uid = currentUserId;
        return recurringOperationDao.getActiveOperations(uid);
    }
    public void addRecurringOperation(RecurringOperation operation) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            operation.setOwnerUID(currentUserId);
            operation.setSyncStatus("NEW");
            operation.setLastModified(System.currentTimeMillis());
            recurringOperationDao.insert(operation);
        });
    }

    public void updateRecurringOperation(RecurringOperation operation) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            operation.setSyncStatus("EDITED");
            operation.setLastModified(System.currentTimeMillis());
            recurringOperationDao.update(operation);
        });
    }

    public void deleteRecurringOperation(RecurringOperation operation) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            recurringOperationDao.delete(operation);
        });
    }
    public boolean hasRecurringOperationBeenProcessedToday(int recurringOpId) {
        long today = getStartOfDay(System.currentTimeMillis()); // تعديل: تعيد long
        RecurringOperationTracking tracking = recurringOperationTrackingDao.getTracking(recurringOpId, today);
        return tracking != null;
    }

    public void markRecurringOperationProcessed(int recurringOpId) {
        long today = getStartOfDay(System.currentTimeMillis()); // تعديل: تعيد long
        RecurringOperationTracking tracking = new RecurringOperationTracking();
        tracking.setRecurringOperationId(recurringOpId);
        tracking.setDate(today);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            recurringOperationTrackingDao.insert(tracking);
        });
    }

    // دالة مساعدة للحصول على بداية اليوم (تعيد long)
    private long getStartOfDay(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
    public List<RecurringOperation> getRecurringOperationsForAccount(int accountId) {
        return recurringOperationDao.getRecurringOperationsForAccount(accountId);
    }
    public LiveData<List<AccountLatestTransaction>> getLatestTransactionTimestamps() {
        return mTransactionDao.getLatestTransactionTimestamps();
    }
}