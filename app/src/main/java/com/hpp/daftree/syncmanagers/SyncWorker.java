package com.hpp.daftree.syncmanagers;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
import com.hpp.daftree.R;
import com.hpp.daftree.budget.data.BudgetCategory;
import com.hpp.daftree.budget.data.BudgetCategoryDao;
import com.hpp.daftree.budget.data.BudgetEntry;
import com.hpp.daftree.budget.data.BudgetEntryDao;
import com.hpp.daftree.budget.data.MonthlyBudgetLimit;
import com.hpp.daftree.budget.data.MonthlyBudgetLimitDao;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.AccountDao;
import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.database.AccountTypeDao;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.CurrencyDao;
import com.hpp.daftree.database.DeletionLog;
import com.hpp.daftree.database.DeletionLogDao;
import com.hpp.daftree.database.Transaction;
import com.hpp.daftree.database.TransactionDao;
import com.hpp.daftree.database.User;
import com.hpp.daftree.database.UserDao;
import com.hpp.daftree.models.AppAuth;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.models.UUIDGenerator;
import com.hpp.daftree.notifications.NotificationHelper;
import com.hpp.daftree.utils.SecureLicenseManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class SyncWorker extends Worker {
    private static final String TAG = "SyncWorker";
    private final AccountDao accountDao;
    private final TransactionDao transactionDao;
    private final DeletionLogDao deletionLogDao;
    private final UserDao userDao;
    private final CurrencyDao currencyDao;
    private final AccountTypeDao accountTypeDao;
    private final BudgetCategoryDao budgetCategoryDao;
    private final BudgetEntryDao budgetEntryDao;
    private final MonthlyBudgetLimitDao monthlyBudgetLimitDao;

    private final DaftreeRepository repository;
    private final FirebaseFirestore firestore;
    private final boolean isGuest;
    private static final String PREF_LAST_DELETE_CHECK = "last_physical_delete_check";
    private static final long THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000;
    private long lastGeneratedSyncTs = 0L;

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        AppDatabase db = AppDatabase.getDatabase(context.getApplicationContext());
        this.accountDao = db.accountDao();
        this.transactionDao = db.transactionDao();
        this.deletionLogDao = db.deletionLogDao();
        this.userDao = db.userDao();
        this.currencyDao = db.currencyDao();
        this.accountTypeDao = db.accountTypeDao();
        this.budgetCategoryDao = db.budgetCategoryDao();
        this.budgetEntryDao = db.budgetEntryDao();
        this.monthlyBudgetLimitDao = db.monthlyBudgetLimitDao();
        this.repository = new DaftreeRepository((Application) context.getApplicationContext());
        this.firestore = FirebaseFirestore.getInstance();
        this.isGuest = SecureLicenseManager.getInstance(context).isGuest();
    }

    private synchronized long nextSyncTimestamp() {
        long now = System.currentTimeMillis();
        if (now <= lastGeneratedSyncTs) {
            now = lastGeneratedSyncTs + 1;
        }
        lastGeneratedSyncTs = now;
        return now;
    }

    private String resolveDeletedByName() {
        try {
            User localUser = userDao.getUserProfileBlocking();
            if (localUser != null && localUser.getName() != null && !localUser.getName().trim().isEmpty()) {
                return localUser.getName().trim();
            }
        } catch (Exception ignored) {
        }
        if (FirebaseAuth.getInstance().getCurrentUser() != null
                && FirebaseAuth.getInstance().getCurrentUser().getDisplayName() != null) {
            return FirebaseAuth.getInstance().getCurrentUser().getDisplayName();
        }
        return "";
    }

    private String resolveCurrentUid() {
        String uid = AppAuth.getUserID(getApplicationContext());
        if (uid != null && !uid.trim().isEmpty()) return uid;
        String fbUid = FirebaseAuth.getInstance().getUid();
        return fbUid != null ? fbUid : "";
    }

    @NonNull
    @Override
    public Result doWork() {
        if (isStopped()) {
            Log.w(TAG, "SyncWorker stopped before starting.");
            return Result.failure();
        }
        Log.d(TAG, "Sync process started.");

        SharedPreferences prefs = getApplicationContext().getSharedPreferences("MigrationPrefs", Context.MODE_PRIVATE);
        boolean migrationNeeded = prefs.getBoolean("firestore_migration_needed", false);

        FirestoreSyncManager.isSyncWorkerRunning = true;
        FirestoreSyncManager.getInstance(getApplicationContext()).stopListening();
        try {
            if (isGuest) return Result.success();
            String currentUid = resolveCurrentUid();
            if (currentUid.isEmpty()) {
                Log.e(TAG, "المستخدم غير مسجل الدخول. إلغاء المزامنة.");
                return Result.failure();
            }

            if (migrationNeeded) {
                prefs.edit().putBoolean("firestore_migration_needed", false).apply();
            }

            // الترتيب المرجعي السليم: العملات -> أنواع الحسابات -> الحسابات -> العمليات -> وحدة الميزانية -> الحذف -> الملف الشخصي -> التنظيف
            uploadCurrencies();
            uploadAccountTypes();
            uploadAccounts();
            uploadTransactions();
            uploadBudgetCategories(currentUid);
            uploadBudgetLimits(currentUid);
            uploadBudgetEntries(currentUid);
            handleDeletions();
            uploadUserProfile(currentUid);
            checkDelete();

            SecureLicenseManager.getInstance(getApplicationContext()).setLastSyncTime(System.currentTimeMillis());
            Log.d(TAG, "Sync process finished successfully.");
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Sync failed, will retry.", e);
            if (e instanceof ExecutionException && e.getCause() instanceof FirebaseFirestoreException) {
                FirebaseFirestoreException ffe = (FirebaseFirestoreException) e.getCause();
                if (ffe.getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    Log.e(TAG, "Sync failed due to permission denied. Will not retry.");
                    return Result.failure();
                }
            }
            return Result.retry();
        } finally {
            if (!isStopped()) {
                FirestoreSyncManager.isSyncWorkerRunning = false;
                repository.setUserUID(resolveCurrentUid());
                FirestoreSyncManager.getInstance().startListening(repository, getApplicationContext(), () -> {
                });
            }
            Log.d(TAG, "انتهى SyncWorker وتمت إعادة تشغيل نظام المزامنة الحي.");
        }
    }

    private Map<String, Object> getAccountMap(Account account, long syncTime) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", account.getId());
        map.put("firestoreId", account.getFirestoreId());
        map.put("ownerUID", account.getOwnerUID());
        map.put("accountName", account.getAccountName());
        map.put("phoneNumber", account.getPhoneNumber());
        map.put("accountType", account.getAccountType());
        map.put("lastModified", account.getLastModified());
        map.put("lastSyncDate", syncTime);
        map.put("acTypeFirestoreId", account.getAcTypeFirestoreId());
        map.put("isDeleted", account.isDeleted());
        map.put("deletedBy", account.getDeletedBy());
        map.put("deleteDate", account.getDeleteDate());
        account.setLastSyncDate(syncTime);
        return map;
    }

    private Map<String, Object> getTransactionMap(Transaction transaction, long syncTime) {
        Map<String, Object> map = new HashMap<>();
        map.put("ownerUID", transaction.getOwnerUID());
        map.put("accountFirestoreId", transaction.getAccountFirestoreId());
        map.put("accountId", transaction.getAccountId());
        map.put("amount", transaction.getAmount());
        map.put("currencyId", transaction.getCurrencyId());
        map.put("details", transaction.getDetails());
        map.put("importID", transaction.getImportID());
        map.put("timestamp", transaction.getTimestamp());
        map.put("type", transaction.getType());
        map.put("lastModified", transaction.getLastModified());
        map.put("lastSyncDate", syncTime);
        map.put("billType", transaction.getBillType());
        map.put("currencyFirestoreId", transaction.getCurrencyFirestoreId());
        map.put("firestoreId", transaction.getFirestoreId());
        map.put("isDeleted", transaction.isDeleted());
        map.put("deleteDate", transaction.getDeleteDate());
        map.put("deletedBy", transaction.getDeletedBy());
        map.put("imageEncoded", transaction.getImageEncoded());
        transaction.setLastSyncDate(syncTime);
        return map;
    }

    private Map<String, Object> getCurrencyMap(Currency currency, long syncTime) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", currency.id);
        map.put("name", currency.name);
        map.put("ownerUID", currency.getOwnerUID());
        map.put("lastModified", currency.getLastModified());
        map.put("firestoreId", currency.getFirestoreId());
        map.put("isDefault", currency.isDefault());
        map.put("code", currency.getCode());
        map.put("symbol", currency.getSymbol());
        map.put("isDeleted", currency.isDeleted());
        map.put("deletedBy", currency.getDeletedBy());
        map.put("lastSyncDate", syncTime);
        map.put("deleteDate", currency.getDeleteDate());
        currency.setLastSyncDate(syncTime);
        return map;
    }

    private Map<String, Object> getAccountTypeMap(AccountType accountType, long syncTime) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", accountType.name);
        map.put("firestoreId", accountType.getFirestoreId());
        map.put("ownerUID", accountType.getOwnerUID());
        map.put("lastModified", accountType.getLastModified());
        map.put("lastSyncDate", syncTime);
        map.put("isDeleted", accountType.isDeleted());
        map.put("isDefault", accountType.isDefault());
        map.put("deletedBy", accountType.getDeletedBy());
        map.put("deleteDate", accountType.getDeleteDate());
        accountType.setLastSyncDate(syncTime);
        return map;
    }

    private Map<String, Object> getBudgetCategoryMap(BudgetCategory cat, long syncTime) {
        Map<String, Object> map = new HashMap<>();
        map.put("firestoreId", cat.getFirestoreId());
        map.put("ownerUID", cat.getOwnerUID());
        map.put("code", cat.getCode());
        map.put("name", cat.getName());
        map.put("type", cat.getType());
        map.put("iconKey", cat.getIconKey());
        map.put("colorHex", cat.getColorHex());
        map.put("isDefault", cat.isDefault());
        map.put("sortOrder", cat.getSortOrder());
        map.put("isDeleted", cat.isDeleted());
        map.put("deleteDate", cat.getDeleteDate());
        map.put("deletedBy", cat.getDeletedBy());
        map.put("lastModified", cat.getLastModified());
        map.put("lastSyncDate", syncTime);
        cat.setLastSyncDate(syncTime);
        return map;
    }

    private Map<String, Object> getBudgetLimitMap(MonthlyBudgetLimit limit, long syncTime) {
        Map<String, Object> map = new HashMap<>();
        map.put("firestoreId", limit.getFirestoreId());
        map.put("ownerUID", limit.getOwnerUID());
        map.put("currencyId", limit.getCurrencyId());
        map.put("currencyFirestoreId", limit.getCurrencyFirestoreId());
        map.put("yearMonth", limit.getYearMonth());
        map.put("limitAmount", limit.getLimitAmount());
        map.put("isDeleted", limit.isDeleted());
        map.put("deleteDate", limit.getDeleteDate());
        map.put("deletedBy", limit.getDeletedBy());
        map.put("lastModified", limit.getLastModified());
        map.put("lastSyncDate", syncTime);
        limit.setLastSyncDate(syncTime);
        return map;
    }

    private Map<String, Object> getBudgetEntryMap(BudgetEntry entry, String categoryCode, long syncTime) {
        Map<String, Object> map = new HashMap<>();
        map.put("firestoreId", entry.getFirestoreId());
        map.put("ownerUID", entry.getOwnerUID());
        map.put("categoryId", entry.getCategoryId());
        map.put("categoryFirestoreId", entry.getCategoryFirestoreId());
        map.put("categoryCode", categoryCode != null ? categoryCode : "");
        map.put("currencyId", entry.getCurrencyId());
        map.put("currencyFirestoreId", entry.getCurrencyFirestoreId());
        map.put("type", entry.getType());
        map.put("amount", entry.getAmount());
        map.put("note", entry.getNote());
        map.put("entryDate", entry.getEntryDate());
        map.put("yearMonth", entry.getYearMonth());
        map.put("isDeleted", entry.isDeleted());
        map.put("deleteDate", entry.getDeleteDate());
        map.put("deletedBy", entry.getDeletedBy());
        map.put("lastModified", entry.getLastModified());
        map.put("lastSyncDate", syncTime);
        entry.setLastSyncDate(syncTime);
        return map;
    }

    private void uploadCurrencies() throws Exception {
        List<Currency> unsynced = currencyDao.getUnsyncedCurrencies();
        for (Currency currency : unsynced) {
            String fsId = currency.getFirestoreId();
            if (fsId == null || fsId.isEmpty()) continue;

            long syncTime = nextSyncTimestamp();
            Map<String, Object> currencyData = getCurrencyMap(currency, syncTime);
            Tasks.await(firestore.collection("currencies").document(fsId)
                    .set(currencyData, SetOptions.merge()));
            currency.setSyncStatus("SYNCED");
            currencyDao.update(currency);
        }
    }

    private void uploadAccountTypes() throws Exception {
        List<AccountType> unsynced = accountTypeDao.getUnsyncedAccountTypes();
        for (AccountType accountType : unsynced) {
            String fsId = accountType.getFirestoreId();
            if (fsId == null || fsId.isEmpty()) continue;

            long syncTime = nextSyncTimestamp();
            Map<String, Object> accountTypeData = getAccountTypeMap(accountType, syncTime);
            Tasks.await(firestore.collection("accountTypes").document(fsId)
                    .set(accountTypeData, SetOptions.merge()));
            accountType.setSyncStatus("SYNCED");
            accountTypeDao.update(accountType);
        }
        if (!unsynced.isEmpty()) Log.d(TAG, "Synced " + unsynced.size() + " account types.");
    }

    private void uploadAccounts() throws Exception {
        List<Account> unsynced = accountDao.getUnsyncedAccounts();
        for (Account account : unsynced) {
            if (account.getOwnerUID() == null || account.getOwnerUID().isEmpty()) {
                Log.e(TAG, "Account missing ownerUID, skipping sync. ID: " + account.getId());
                continue;
            }
            String firestoreId = account.getFirestoreId();
            if (firestoreId == null || firestoreId.isEmpty()) {
                Log.e(TAG, "Account missing firestoreId, skipping sync. ID: " + account.getId());
                continue;
            }

            long syncTime = nextSyncTimestamp();
            Map<String, Object> accountData = getAccountMap(account, syncTime);
            Tasks.await(firestore.collection("accounts").document(firestoreId)
                    .set(accountData, SetOptions.merge()));

            account.setSyncStatus("SYNCED");
            accountDao.update(account);
        }
    }

    private void uploadTransactions() throws Exception {
        TransactionUploadController controller = new TransactionUploadController(getApplicationContext());
        List<Transaction> unsynced = transactionDao.getUnsyncedTransactions();

        for (Transaction tx : unsynced) {
            if (!controller.canSend(tx)) {
                if (!controller.hasBlocked()) {
                    controller.storeFirstBlocked(tx);
                    NotificationHelper.get(getApplicationContext()).showLocalNotification(
                            getApplicationContext().getString(R.string.daily_plan_tit2),
                            getApplicationContext().getString(R.string.daily_plan_msg2),
                            1001, true);
                }
                continue;
            }

            if (tx.getOwnerUID() == null || tx.getOwnerUID().isEmpty()) {
                Log.e(TAG, "Transaction missing ownerUID, skipping sync. ID: " + tx.getId());
                continue;
            }
            String fsId = tx.getFirestoreId();
            if (fsId == null || fsId.isEmpty()) {
                fsId = UUIDGenerator.generateSequentialUUID();
                tx.setFirestoreId(fsId);
            }

            Account parentAccount = accountDao.getAccountByIdBlocking(tx.getAccountId());
            if (parentAccount == null || parentAccount.getFirestoreId() == null || parentAccount.getFirestoreId().isEmpty()) {
                Log.w(TAG, "Parent account not synced, skipping transaction. Account ID: " + tx.getAccountId());
                continue;
            }
            if (tx.getAccountFirestoreId().isEmpty()) {
                tx.setAccountFirestoreId(parentAccount.getFirestoreId());
            }
            if (tx.getCurrencyFirestoreId().isEmpty()) {
                String curFsId = currencyDao.getCurrencyFirestoreId(tx.getCurrencyId());
                if (curFsId != null) tx.setCurrencyFirestoreId(curFsId);
            }

            long syncTime = nextSyncTimestamp();
            Map<String, Object> txData = getTransactionMap(tx, syncTime);
            Tasks.await(firestore.collection("transactions").document(fsId)
                    .set(txData, SetOptions.merge()));

            tx.setSyncStatus("SYNCED");
            transactionDao.update(tx);
        }
    }

    private void uploadBudgetCategories(String currentUid) throws Exception {
        List<BudgetCategory> unsynced = budgetCategoryDao.getUnsyncedCategoriesSync();
        for (BudgetCategory cat : unsynced) {
            if (cat.getOwnerUID() == null || cat.getOwnerUID().isEmpty()) {
                cat.setOwnerUID(currentUid);
            }
            String fsId = cat.getFirestoreId();
            if (fsId == null || fsId.isEmpty()) {
                fsId = UUIDGenerator.generateSequentialUUID();
                cat.setFirestoreId(fsId);
            }
            long syncTime = nextSyncTimestamp();
            Map<String, Object> data = getBudgetCategoryMap(cat, syncTime);
            Tasks.await(firestore.collection("budget_categories").document(fsId)
                    .set(data, SetOptions.merge()));
            cat.setSyncStatus("SYNCED");
            budgetCategoryDao.update(cat);
        }
        if (!unsynced.isEmpty()) Log.d(TAG, "Synced " + unsynced.size() + " budget categories.");
    }

    private void uploadBudgetLimits(String currentUid) throws Exception {
        List<MonthlyBudgetLimit> unsynced = monthlyBudgetLimitDao.getUnsyncedLimitsSync();
        for (MonthlyBudgetLimit limit : unsynced) {
            if (limit.getOwnerUID() == null || limit.getOwnerUID().isEmpty()) {
                limit.setOwnerUID(currentUid);
            }
            String fsId = limit.getFirestoreId();
            if (fsId == null || fsId.isEmpty()) {
                fsId = UUIDGenerator.generateSequentialUUID();
                limit.setFirestoreId(fsId);
            }
            if (limit.getCurrencyFirestoreId() == null || limit.getCurrencyFirestoreId().isEmpty()) {
                String curFsId = currencyDao.getCurrencyFirestoreId(limit.getCurrencyId());
                limit.setCurrencyFirestoreId(curFsId != null ? curFsId : "");
            }
            long syncTime = nextSyncTimestamp();
            Map<String, Object> data = getBudgetLimitMap(limit, syncTime);
            Tasks.await(firestore.collection("monthly_budget_limits").document(fsId)
                    .set(data, SetOptions.merge()));
            limit.setSyncStatus("SYNCED");
            monthlyBudgetLimitDao.update(limit);
        }
        if (!unsynced.isEmpty()) Log.d(TAG, "Synced " + unsynced.size() + " budget limits.");
    }

    private void uploadBudgetEntries(String currentUid) throws Exception {
        List<BudgetEntry> unsynced = budgetEntryDao.getUnsyncedEntriesSync();
        for (BudgetEntry entry : unsynced) {
            if (entry.getOwnerUID() == null || entry.getOwnerUID().isEmpty()) {
                entry.setOwnerUID(currentUid);
            }
            String fsId = entry.getFirestoreId();
            if (fsId == null || fsId.isEmpty()) {
                fsId = UUIDGenerator.generateSequentialUUID();
                entry.setFirestoreId(fsId);
            }
            if (entry.getCurrencyFirestoreId() == null || entry.getCurrencyFirestoreId().isEmpty()) {
                String curFsId = currencyDao.getCurrencyFirestoreId(entry.getCurrencyId());
                entry.setCurrencyFirestoreId(curFsId != null ? curFsId : "");
            }
            BudgetCategory cat = budgetCategoryDao.getByIdSync(entry.getCategoryId());
            String catCode = cat != null ? cat.getCode() : "";
            if ((entry.getCategoryFirestoreId() == null || entry.getCategoryFirestoreId().isEmpty()) && cat != null) {
                entry.setCategoryFirestoreId(cat.getFirestoreId() != null ? cat.getFirestoreId() : "");
            }
            long syncTime = nextSyncTimestamp();
            Map<String, Object> data = getBudgetEntryMap(entry, catCode, syncTime);
            Tasks.await(firestore.collection("budget_entries").document(fsId)
                    .set(data, SetOptions.merge()));
            entry.setSyncStatus("SYNCED");
            budgetEntryDao.update(entry);
        }
        if (!unsynced.isEmpty()) Log.d(TAG, "Synced " + unsynced.size() + " budget entries.");
    }

    private void uploadUserProfile(String userId) throws ExecutionException, InterruptedException {
        if (userId == null || userId.isEmpty()) return;

        User localUser = userDao.getUserProfileBlocking();
        if (localUser == null) return;

        DocumentReference userDocRef = firestore.collection("users").document(userId);
        DocumentSnapshot remoteUserDoc = Tasks.await(userDocRef.get());

        if (remoteUserDoc.exists()) {
            User remoteUser = remoteUserDoc.toObject(User.class);
            if (remoteUser == null) return;

            Map<String, Object> updates = new HashMap<>();
            updates.put("company", localUser.getCompany());
            updates.put("phone", localUser.getPhone());
            updates.put("name", localUser.getName());
            updates.put("address", localUser.getAddress());

            if (localUser.getTransactions_count() > remoteUser.getTransactions_count()) {
                updates.put("transactions_count", localUser.getTransactions_count());
            } else {
                localUser.setTransactions_count(remoteUser.getTransactions_count());
            }

            if (localUser.getMax_transactions() > remoteUser.getMax_transactions()) {
                updates.put("max_transactions", localUser.getMax_transactions());
            } else {
                localUser.setMax_transactions(remoteUser.getMax_transactions());
            }

            if (localUser.getAd_rewards() > remoteUser.getAd_rewards()) {
                updates.put("ad_rewards", localUser.getAd_rewards());
            } else {
                localUser.setAd_rewards(remoteUser.getAd_rewards());
            }

            if (localUser.getReferral_rewards() > remoteUser.getReferral_rewards()) {
                updates.put("referral_rewards", localUser.getReferral_rewards());
            } else {
                localUser.setReferral_rewards(remoteUser.getReferral_rewards());
            }

            if (!updates.isEmpty()) {
                updates.put("lastModified", System.currentTimeMillis());
                Tasks.await(userDocRef.set(updates, SetOptions.merge()));
            }

            localUser.setSyncStatus("SYNCED");
            userDao.upsert(localUser);
        } else {
            localUser.setSyncStatus("SYNCED");
            Tasks.await(userDocRef.set(localUser));
            userDao.upsert(localUser);
        }
    }

    private void handleDeletions() {
        try {
            List<DeletionLog> deletions = deletionLogDao.getAllPendingDeletions();
            String deletedByName = resolveDeletedByName();

            for (DeletionLog deletion : deletions) {
                String collection = deletion.getCollectionName();
                if ("currency".equals(collection)) collection = "currencies";
                else if ("accountType".equals(collection)) collection = "accountTypes";
                String firestoreId = deletion.getFirestoreId();

                try {
                    long syncTime = nextSyncTimestamp();
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("isDeleted", true);
                    updates.put("deletedBy", deletedByName);
                    updates.put("deleteDate", syncTime);
                    updates.put("lastModified", syncTime);
                    updates.put("lastSyncDate", syncTime);

                    Tasks.await(firestore.collection(collection)
                            .document(firestoreId)
                            .update(updates));

                    updateLocalRecord(collection, firestoreId, syncTime, deletedByName);
                    deletionLogDao.deleteByFirestoreId(firestoreId);
                } catch (Exception e) {
                    if (e.getCause() instanceof FirebaseFirestoreException) {
                        FirebaseFirestoreException ffe = (FirebaseFirestoreException) e.getCause();
                        if (ffe.getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED
                                || ffe.getCode() == FirebaseFirestoreException.Code.NOT_FOUND) {
                            deletionLogDao.deleteByFirestoreId(firestoreId);
                            continue;
                        }
                    }
                    Log.e(TAG, "Error marking document as deleted: " + collection + "/" + firestoreId, e);
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, "Fatal error in handleDeletions()", ex);
        }
    }

    private void updateLocalRecord(String collectionName, String firestoreId, long syncTime, String deletedBy) {
        switch (collectionName) {
            case "accounts": {
                Account account = accountDao.getAccountByFirestoreId(firestoreId);
                if (account != null) {
                    account.setSyncStatus("SYNCED");
                    account.setDeleteDate(syncTime);
                    account.setLastSyncDate(syncTime);
                    account.setDeletedBy(deletedBy);
                    accountDao.update(account);
                }
                break;
            }
            case "transactions": {
                Transaction transaction = transactionDao.getTransactionByFirestoreId(firestoreId);
                if (transaction != null) {
                    transaction.setSyncStatus("SYNCED");
                    transaction.setDeleteDate(syncTime);
                    transaction.setLastSyncDate(syncTime);
                    transaction.setDeletedBy(deletedBy);
                    transactionDao.update(transaction);
                }
                break;
            }
            case "currencies": {
                Currency currency = currencyDao.getCurrencyByFirestoreId(firestoreId);
                if (currency != null) {
                    currency.setDeleteDate(syncTime);
                    currency.setLastSyncDate(syncTime);
                    currency.setDeletedBy(deletedBy);
                    currency.setSyncStatus("SYNCED");
                    currencyDao.update(currency);
                }
                break;
            }
            case "accountTypes": {
                AccountType accountType = accountTypeDao.getAccountTypeByFirestoreId(firestoreId);
                if (accountType != null) {
                    accountType.setDeleteDate(syncTime);
                    accountType.setLastSyncDate(syncTime);
                    accountType.setDeletedBy(deletedBy);
                    accountType.setSyncStatus("SYNCED");
                    accountTypeDao.update(accountType);
                }
                break;
            }
            case "budget_categories": {
                BudgetCategory cat = budgetCategoryDao.getByFirestoreIdSync(firestoreId);
                if (cat != null) {
                    cat.setDeleteDate(syncTime);
                    cat.setLastSyncDate(syncTime);
                    cat.setDeletedBy(deletedBy);
                    cat.setSyncStatus("SYNCED");
                    budgetCategoryDao.update(cat);
                }
                break;
            }
            case "monthly_budget_limits": {
                MonthlyBudgetLimit limit = monthlyBudgetLimitDao.getByFirestoreIdSync(firestoreId);
                if (limit != null) {
                    limit.setDeleteDate(syncTime);
                    limit.setLastSyncDate(syncTime);
                    limit.setDeletedBy(deletedBy);
                    limit.setSyncStatus("SYNCED");
                    monthlyBudgetLimitDao.update(limit);
                }
                break;
            }
            case "budget_entries": {
                BudgetEntry entry = budgetEntryDao.getByFirestoreIdSync(firestoreId);
                if (entry != null) {
                    entry.setDeleteDate(syncTime);
                    entry.setLastSyncDate(syncTime);
                    entry.setDeletedBy(deletedBy);
                    entry.setSyncStatus("SYNCED");
                    budgetEntryDao.update(entry);
                }
                break;
            }
            default:
                Log.w(TAG, "Unknown collection: " + collectionName);
        }
    }

    private void checkDelete() {
        Context context = getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE);
        long lastCheck = prefs.getLong(PREF_LAST_DELETE_CHECK, 0);
        long now = System.currentTimeMillis();

        if (now - lastCheck < 24 * 60 * 60 * 1000) {
            return;
        }

        long threshold = now - THIRTY_DAYS_MS;

        try {
            List<Transaction> oldDeletedTransactions = transactionDao.getOldDeletedTransactions(threshold);
            if (!oldDeletedTransactions.isEmpty()) {
                WriteBatch batch = firestore.batch();
                for (Transaction tx : oldDeletedTransactions) {
                    if (tx.getFirestoreId() != null && !tx.getFirestoreId().isEmpty()) {
                        batch.delete(firestore.collection("transactions").document(tx.getFirestoreId()));
                    }
                }
                Tasks.await(batch.commit());
                for (Transaction tx : oldDeletedTransactions) {
                    transactionDao.deleteByFirestoreId(tx.getFirestoreId());
                }
            }

            List<Account> oldDeletedAccounts = accountDao.getOldDeletedAccounts(threshold);
            if (!oldDeletedAccounts.isEmpty()) {
                WriteBatch batch = firestore.batch();
                for (Account acc : oldDeletedAccounts) {
                    if (acc.getFirestoreId() != null && !acc.getFirestoreId().isEmpty()) {
                        batch.delete(firestore.collection("accounts").document(acc.getFirestoreId()));
                    }
                }
                Tasks.await(batch.commit());
                for (Account acc : oldDeletedAccounts) {
                    accountDao.deleteByFirestoreId(acc.getFirestoreId());
                }
            }

            List<Currency> oldDeletedCurrencies = currencyDao.getOldDeletedCurrencies(threshold);
            if (!oldDeletedCurrencies.isEmpty()) {
                WriteBatch batch = firestore.batch();
                for (Currency cur : oldDeletedCurrencies) {
                    if (cur.getFirestoreId() != null && !cur.getFirestoreId().isEmpty()) {
                        batch.delete(firestore.collection("currencies").document(cur.getFirestoreId()));
                    }
                }
                Tasks.await(batch.commit());
                for (Currency cur : oldDeletedCurrencies) {
                    currencyDao.deleteByFirestoreId(cur.getFirestoreId());
                }
            }

            List<AccountType> oldDeletedTypes = accountTypeDao.getOldDeletedAccountTypes(threshold);
            if (!oldDeletedTypes.isEmpty()) {
                WriteBatch batch = firestore.batch();
                for (AccountType type : oldDeletedTypes) {
                    if (type.getFirestoreId() != null && !type.getFirestoreId().isEmpty()) {
                        batch.delete(firestore.collection("accountTypes").document(type.getFirestoreId()));
                    }
                }
                Tasks.await(batch.commit());
                for (AccountType type : oldDeletedTypes) {
                    accountTypeDao.deleteByFirestoreId(type.getFirestoreId());
                }
            }

            // تنظيف سجلات الميزانية المحذوفة منذ أكثر من 30 يوماً
            List<BudgetEntry> oldBudgetEntries = budgetEntryDao.getOldDeletedEntries(threshold);
            if (!oldBudgetEntries.isEmpty()) {
                WriteBatch batch = firestore.batch();
                for (BudgetEntry e : oldBudgetEntries) {
                    if (e.getFirestoreId() != null && !e.getFirestoreId().isEmpty()) {
                        batch.delete(firestore.collection("budget_entries").document(e.getFirestoreId()));
                    }
                }
                Tasks.await(batch.commit());
                for (BudgetEntry e : oldBudgetEntries) {
                    budgetEntryDao.deleteByFirestoreId(e.getFirestoreId());
                }
            }

            List<MonthlyBudgetLimit> oldBudgetLimits = monthlyBudgetLimitDao.getOldDeletedLimits(threshold);
            if (!oldBudgetLimits.isEmpty()) {
                WriteBatch batch = firestore.batch();
                for (MonthlyBudgetLimit l : oldBudgetLimits) {
                    if (l.getFirestoreId() != null && !l.getFirestoreId().isEmpty()) {
                        batch.delete(firestore.collection("monthly_budget_limits").document(l.getFirestoreId()));
                    }
                }
                Tasks.await(batch.commit());
                for (MonthlyBudgetLimit l : oldBudgetLimits) {
                    monthlyBudgetLimitDao.deleteByFirestoreId(l.getFirestoreId());
                }
            }

            List<BudgetCategory> oldBudgetCats = budgetCategoryDao.getOldDeletedCategories(threshold);
            if (!oldBudgetCats.isEmpty()) {
                WriteBatch batch = firestore.batch();
                for (BudgetCategory c : oldBudgetCats) {
                    if (c.getFirestoreId() != null && !c.getFirestoreId().isEmpty()) {
                        batch.delete(firestore.collection("budget_categories").document(c.getFirestoreId()));
                    }
                }
                Tasks.await(batch.commit());
                for (BudgetCategory c : oldBudgetCats) {
                    budgetCategoryDao.deleteByFirestoreId(c.getFirestoreId());
                }
            }

            prefs.edit().putLong(PREF_LAST_DELETE_CHECK, now).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error during physical deletion of old records", e);
        }
    }
}
