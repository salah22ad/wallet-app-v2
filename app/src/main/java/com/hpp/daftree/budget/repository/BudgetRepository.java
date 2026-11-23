package com.hpp.daftree.budget.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.lifecycle.LiveData;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.firebase.auth.FirebaseAuth;
import com.hpp.daftree.R;
import com.hpp.daftree.budget.data.BudgetCategory;
import com.hpp.daftree.budget.data.BudgetCategoryDao;
import com.hpp.daftree.budget.data.BudgetEntry;
import com.hpp.daftree.budget.data.BudgetEntryDao;
import com.hpp.daftree.budget.data.BudgetEntryWithCategory;
import com.hpp.daftree.budget.data.CategoryExpenseTotal;
import com.hpp.daftree.budget.data.MonthSummaryStats;
import com.hpp.daftree.budget.data.MonthlyBudgetLimit;
import com.hpp.daftree.budget.data.MonthlyBudgetLimitDao;
import com.hpp.daftree.budget.data.MonthlyTotals;
import com.hpp.daftree.budget.util.BudgetUtils;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.database.User;
import com.hpp.daftree.models.AppAuth;
import com.hpp.daftree.models.UUIDGenerator;
import com.hpp.daftree.syncmanagers.SyncWorker;
import com.hpp.daftree.utils.SecureLicenseManager;

import java.util.ArrayList;
import java.util.List;

public class BudgetRepository {

    private final Context appContext;
    private final AppDatabase db;
    private final BudgetCategoryDao categoryDao;
    private final BudgetEntryDao entryDao;
    private final MonthlyBudgetLimitDao limitDao;

    public BudgetRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.db = AppDatabase.getDatabase(this.appContext);
        this.categoryDao = db.budgetCategoryDao();
        this.entryDao = db.budgetEntryDao();
        this.limitDao = db.monthlyBudgetLimitDao();
        ensureDefaultCategories();
    }

    private static String resolveOwnerUid(Context ctx) {
        try {
            SecureLicenseManager slm = SecureLicenseManager.getInstance(ctx);
            if (slm.isGuest()) {
                return slm.guestUID() != null ? slm.guestUID() : "";
            }
            String uid = AppAuth.getUserID(ctx);
            if (uid != null && !uid.trim().isEmpty()) return uid;
        } catch (Exception ignored) {
        }
        String fbUid = FirebaseAuth.getInstance().getUid();
        return fbUid != null ? fbUid : "";
    }

    private static String resolveCurrentUserName(AppDatabase db) {
        try {
            User u = db.userDao().getUserProfileBlocking();
            if (u != null && u.getName() != null && !u.getName().trim().isEmpty()) {
                return u.getName().trim();
            }
        } catch (Exception ignored) {
        }
        if (FirebaseAuth.getInstance().getCurrentUser() != null
                && FirebaseAuth.getInstance().getCurrentUser().getDisplayName() != null) {
            return FirebaseAuth.getInstance().getCurrentUser().getDisplayName();
        }
        return "";
    }

    public static void triggerBudgetSync(Context ctx) {
        try {
            Context appCtx = ctx.getApplicationContext();
            if (SecureLicenseManager.getInstance(appCtx).isGuest()) return;
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();
            OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(SyncWorker.class)
                    .setConstraints(constraints)
                    .build();
            WorkManager.getInstance(appCtx).beginUniqueWork(
                    "UNIQUE_SYNC_WORK",
                    ExistingWorkPolicy.REPLACE,
                    syncRequest
            ).enqueue();
        } catch (Exception e) {
            Log.e("BudgetRepository", "Error triggering budget sync", e);
        }
    }

    private void ensureDefaultCategories() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (categoryDao.getCountSync() > 0) {
                categoryDao.migrateDefaultCategoryCode("network", "freelance", "freelance");
                categoryDao.migrateDefaultCategoryCode("apps", "investments", "investments");
                return;
            }
            String uid = resolveOwnerUid(appContext);
            long now = System.currentTimeMillis();
            List<BudgetCategory> defaults = new ArrayList<>();
            defaults.add(makeSeed(uid, "bills", BudgetCategory.TYPE_EXPENSE, "bills", "#F59E0B", 1, now));
            defaults.add(makeSeed(uid, "housing", BudgetCategory.TYPE_EXPENSE, "housing", "#22C55E", 2, now));
            defaults.add(makeSeed(uid, "food", BudgetCategory.TYPE_EXPENSE, "food", "#F97316", 3, now));
            defaults.add(makeSeed(uid, "transport", BudgetCategory.TYPE_EXPENSE, "transport", "#3B82F6", 4, now));
            defaults.add(makeSeed(uid, "health", BudgetCategory.TYPE_EXPENSE, "health", "#A855F7", 5, now));
            defaults.add(makeSeed(uid, "other", BudgetCategory.TYPE_EXPENSE, "other", "#06B6D4", 6, now));
            defaults.add(makeSeed(uid, "salary", BudgetCategory.TYPE_INCOME, "salary", "#10B981", 1, now));
            defaults.add(makeSeed(uid, "freelance", BudgetCategory.TYPE_INCOME, "freelance", "#0EA5E9", 2, now));
            defaults.add(makeSeed(uid, "investments", BudgetCategory.TYPE_INCOME, "investments", "#6366F1", 3, now));
            defaults.add(makeSeed(uid, "income_other", BudgetCategory.TYPE_INCOME, "income_other", "#EAB308", 4, now));
            categoryDao.insertAll(defaults);
        });
    }

    public void addCustomCategory(String name, String type, String iconKey, String colorHex, Runnable onDone) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            BudgetCategory cat = new BudgetCategory("", name != null ? name.trim() : "", type, iconKey, colorHex, false, 100);
            cat.setFirestoreId(UUIDGenerator.generateSequentialUUID());
            cat.setOwnerUID(resolveOwnerUid(appContext));
            cat.setSyncStatus("NEW");
            cat.setLastModified(System.currentTimeMillis());
            cat.setLastSyncDate(0L);
            categoryDao.insert(cat);
            triggerBudgetSync(appContext);
            if (onDone != null) onDone.run();
        });
    }

    public LiveData<List<BudgetCategory>> getCategoriesByType(String type) {
        return categoryDao.getCategoriesByType(type);
    }

    public LiveData<List<BudgetEntryWithCategory>> getEntriesForMonth(String yearMonth, int currencyId) {
        return entryDao.getEntriesForMonth(yearMonth, currencyId);
    }

    public LiveData<MonthSummaryStats> getMonthSummary(String yearMonth, int currencyId) {
        return entryDao.getMonthSummary(yearMonth, currencyId);
    }

    public LiveData<List<CategoryExpenseTotal>> getExpenseDistributionForMonth(String yearMonth, int currencyId) {
        return entryDao.getExpenseDistributionForMonth(yearMonth, currencyId);
    }

    public LiveData<List<MonthlyTotals>> getMonthlyComparison(String endYearMonth, int currencyId) {
        List<String> months = BudgetUtils.getLastSixMonths(endYearMonth);
        return entryDao.getMonthlyComparison(months, currencyId);
    }

    public LiveData<Double> getEffectiveLimit(String yearMonth, int currencyId) {
        return limitDao.getEffectiveLimit(yearMonth, currencyId);
    }

    public void addEntry(int categoryId, int currencyId, String currencyFirestoreId,
                         String type, double amount, String note, long entryDate, Runnable onDone) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            long now = System.currentTimeMillis();
            String catFsId = categoryDao.getCategoryFirestoreIdSync(categoryId);
            String curFsId = currencyFirestoreId;
            if (curFsId == null || curFsId.isEmpty()) {
                curFsId = db.currencyDao().getCurrencyFirestoreId(currencyId);
            }

            BudgetEntry entry = new BudgetEntry();
            entry.setFirestoreId(UUIDGenerator.generateSequentialUUID());
            entry.setOwnerUID(resolveOwnerUid(appContext));
            entry.setCategoryId(categoryId);
            entry.setCategoryFirestoreId(catFsId != null ? catFsId : "");
            entry.setCurrencyId(currencyId);
            entry.setCurrencyFirestoreId(curFsId != null ? curFsId : "");
            entry.setType(type);
            entry.setAmount(amount);
            entry.setNote(note != null ? note.trim() : "");
            entry.setEntryDate(entryDate);
            entry.setYearMonth(BudgetUtils.toYearMonth(entryDate));
            entry.setSyncStatus("NEW");
            entry.setLastModified(now);
            entry.setLastSyncDate(0L);
            entry.setDeleted(false);
            entryDao.insert(entry);
            triggerBudgetSync(appContext);
            if (onDone != null) onDone.run();
        });
    }

    public void updateEntry(int entryId, int categoryId, double amount, String note,
                            long entryDate, Runnable onDone) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            BudgetEntry existing = entryDao.getByIdSync(entryId);
            if (existing != null) {
                String catFsId = categoryDao.getCategoryFirestoreIdSync(categoryId);
                existing.setCategoryId(categoryId);
                existing.setCategoryFirestoreId(catFsId != null ? catFsId : "");
                existing.setAmount(amount);
                existing.setNote(note != null ? note.trim() : "");
                existing.setEntryDate(entryDate);
                existing.setYearMonth(BudgetUtils.toYearMonth(entryDate));
                existing.setLastModified(System.currentTimeMillis());
                if (!"NEW".equals(existing.getSyncStatus())) {
                    existing.setSyncStatus("EDITED");
                }
                entryDao.update(existing);
                triggerBudgetSync(appContext);
            }
            if (onDone != null) onDone.run();
        });
    }

    public void deleteEntry(int entryId, Runnable onDone) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            String deletedBy = resolveCurrentUserName(db);
            entryDao.softDeleteByIdWithUser(entryId, System.currentTimeMillis(), deletedBy);
            triggerBudgetSync(appContext);
            if (onDone != null) onDone.run();
        });
    }

    public void saveMonthlyLimit(String yearMonth, int currencyId, String currencyFirestoreId,
                                 double limitAmount, Runnable onDone) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            long now = System.currentTimeMillis();
            String curFsId = currencyFirestoreId;
            if (curFsId == null || curFsId.isEmpty()) {
                curFsId = db.currencyDao().getCurrencyFirestoreId(currencyId);
            }
            MonthlyBudgetLimit existing = limitDao.getExactLimitSync(yearMonth, currencyId);
            if (existing != null) {
                existing.setLimitAmount(limitAmount);
                existing.setCurrencyFirestoreId(curFsId != null ? curFsId : "");
                existing.setDeleted(false);
                existing.setLastModified(now);
                if (!"NEW".equals(existing.getSyncStatus())) {
                    existing.setSyncStatus("EDITED");
                }
                limitDao.update(existing);
            } else {
                MonthlyBudgetLimit limit = new MonthlyBudgetLimit();
                limit.setFirestoreId(UUIDGenerator.generateSequentialUUID());
                limit.setOwnerUID(resolveOwnerUid(appContext));
                limit.setCurrencyId(currencyId);
                limit.setCurrencyFirestoreId(curFsId != null ? curFsId : "");
                limit.setYearMonth(yearMonth);
                limit.setLimitAmount(limitAmount);
                limit.setSyncStatus("NEW");
                limit.setLastModified(now);
                limit.setLastSyncDate(0L);
                limit.setDeleted(false);
                limitDao.insert(limit);
            }
            triggerBudgetSync(appContext);
            if (onDone != null) onDone.run();
        });
    }

    public static void recordLinkedDebtTransaction(Context context, String accountName,
                                                   double amount, int debtTxType,
                                                   int currencyId, long entryDateMillis) {
        if (context == null || amount <= 0) {
            Log.w("BudgetLink", "Skipped linked budget entry: invalid context or amount=" + amount);
            return;
        }
        final Context appCtx = context.getApplicationContext();
        final String safeAccountName = accountName != null ? accountName.trim() : "";
        final String linkedNote;
        final String toastMsg;
        try {
            linkedNote = context.getString(R.string.budget_linked_account_note, safeAccountName);
            toastMsg = context.getString(R.string.budget_linked_saved_toast);
        } catch (Exception e) {
            Log.e("BudgetLink", "Error resolving localized strings", e);
            return;
        }

        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getDatabase(appCtx);
                BudgetCategoryDao catDao = db.budgetCategoryDao();
                BudgetEntryDao eDao = db.budgetEntryDao();

                int resolvedCurrencyId = currencyId;
                if (resolvedCurrencyId <= 0) {
                    resolvedCurrencyId = db.currencyDao().getFirstIdCurrency();
                }
                if (resolvedCurrencyId <= 0) {
                    Log.e("BudgetLink", "No valid currencyId found for linked entry");
                    return;
                }

                String uid = resolveOwnerUid(appCtx);

                if (catDao.getCountSync() == 0) {
                    long seedNow = System.currentTimeMillis();
                    List<BudgetCategory> defaults = new ArrayList<>();
                    defaults.add(makeSeed(uid, "bills", BudgetCategory.TYPE_EXPENSE, "bills", "#F59E0B", 1, seedNow));
                    defaults.add(makeSeed(uid, "housing", BudgetCategory.TYPE_EXPENSE, "housing", "#22C55E", 2, seedNow));
                    defaults.add(makeSeed(uid, "food", BudgetCategory.TYPE_EXPENSE, "food", "#F97316", 3, seedNow));
                    defaults.add(makeSeed(uid, "transport", BudgetCategory.TYPE_EXPENSE, "transport", "#3B82F6", 4, seedNow));
                    defaults.add(makeSeed(uid, "health", BudgetCategory.TYPE_EXPENSE, "health", "#A855F7", 5, seedNow));
                    defaults.add(makeSeed(uid, "other", BudgetCategory.TYPE_EXPENSE, "other", "#06B6D4", 6, seedNow));
                    defaults.add(makeSeed(uid, "salary", BudgetCategory.TYPE_INCOME, "salary", "#10B981", 1, seedNow));
                    defaults.add(makeSeed(uid, "freelance", BudgetCategory.TYPE_INCOME, "freelance", "#0EA5E9", 2, seedNow));
                    defaults.add(makeSeed(uid, "investments", BudgetCategory.TYPE_INCOME, "investments", "#6366F1", 3, seedNow));
                    defaults.add(makeSeed(uid, "income_other", BudgetCategory.TYPE_INCOME, "income_other", "#EAB308", 4, seedNow));
                    catDao.insertAll(defaults);
                }

                boolean isCredit = (debtTxType == -1);
                String budgetType = isCredit ? BudgetEntry.TYPE_INCOME : BudgetEntry.TYPE_EXPENSE;
                String targetCode = isCredit ? "income_other" : "other";
                String targetColor = isCredit ? "#EAB308" : "#06B6D4";

                Integer catId = catDao.getCategoryIdByCodeSync(targetCode);
                if (catId == null || catId <= 0) {
                    long insertedCatId = catDao.insert(makeSeed(
                            uid, targetCode, budgetType, targetCode, targetColor, 99, System.currentTimeMillis()));
                    catId = (int) insertedCatId;
                }

                String catFsId = catDao.getCategoryFirestoreIdSync(catId);
                String currencyFsId = "";
                try {
                    String fetched = db.currencyDao().getCurrencyFirestoreId(resolvedCurrencyId);
                    if (fetched != null) currencyFsId = fetched;
                } catch (Exception ignored) {
                }

                long safeDate = entryDateMillis > 0 ? entryDateMillis : System.currentTimeMillis();
                long now = System.currentTimeMillis();

                BudgetEntry entry = new BudgetEntry();
                entry.setFirestoreId(UUIDGenerator.generateSequentialUUID());
                entry.setOwnerUID(uid);
                entry.setCategoryId(catId);
                entry.setCategoryFirestoreId(catFsId != null ? catFsId : "");
                entry.setCurrencyId(resolvedCurrencyId);
                entry.setCurrencyFirestoreId(currencyFsId);
                entry.setType(budgetType);
                entry.setAmount(amount);
                entry.setNote(linkedNote);
                entry.setEntryDate(safeDate);
                entry.setYearMonth(BudgetUtils.toYearMonth(safeDate));
                entry.setSyncStatus("NEW");
                entry.setLastModified(now);
                entry.setLastSyncDate(0L);
                entry.setDeleted(false);

                long insertedId = eDao.insert(entry);
                if (insertedId > 0) {
                    triggerBudgetSync(appCtx);
                    new Handler(Looper.getMainLooper()).post(() ->
                            Toast.makeText(appCtx, toastMsg, Toast.LENGTH_SHORT).show());
                }
            } catch (Throwable t) {
                Log.e("BudgetLink", "Failed to record linked budget entry", t);
            }
        });
    }

    private static BudgetCategory makeSeed(String uid, String code, String type,
                                           String icon, String color, int order, long now) {
        BudgetCategory cat = new BudgetCategory(code, "", type, icon, color, true, order);
        cat.setFirestoreId(UUIDGenerator.generateSequentialUUID());
        cat.setOwnerUID(uid);
        cat.setLastModified(now);
        return cat;
    }
}
