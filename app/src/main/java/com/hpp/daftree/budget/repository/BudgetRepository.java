package com.hpp.daftree.budget.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.lifecycle.LiveData;

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
import com.hpp.daftree.models.UUIDGenerator;
import com.hpp.daftree.utils.SecureLicenseManager;

import java.util.ArrayList;
import java.util.List;

public class BudgetRepository {

    private final BudgetCategoryDao categoryDao;
    private final BudgetEntryDao entryDao;
    private final MonthlyBudgetLimitDao limitDao;

    public BudgetRepository(Context context) {
        AppDatabase db = AppDatabase.getDatabase(context.getApplicationContext());
        this.categoryDao = db.budgetCategoryDao();
        this.entryDao = db.budgetEntryDao();
        this.limitDao = db.monthlyBudgetLimitDao();
        ensureDefaultCategories();
    }

    private String getCurrentUid() {
        String uid = FirebaseAuth.getInstance().getUid();
        return uid != null ? uid : "";
    }

    private void ensureDefaultCategories() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (categoryDao.getCountSync() > 0) {
                categoryDao.migrateDefaultCategoryCode("network", "freelance", "freelance");
                categoryDao.migrateDefaultCategoryCode("apps", "investments", "investments");
                return;
            }
            List<BudgetCategory> defaults = new ArrayList<>();
            defaults.add(buildDefaultCat("bills", BudgetCategory.TYPE_EXPENSE, "bills", "#F59E0B", 1));
            defaults.add(buildDefaultCat("housing", BudgetCategory.TYPE_EXPENSE, "housing", "#22C55E", 2));
            defaults.add(buildDefaultCat("food", BudgetCategory.TYPE_EXPENSE, "food", "#F97316", 3));
            defaults.add(buildDefaultCat("transport", BudgetCategory.TYPE_EXPENSE, "transport", "#3B82F6", 4));
            defaults.add(buildDefaultCat("health", BudgetCategory.TYPE_EXPENSE, "health", "#A855F7", 5));
            defaults.add(buildDefaultCat("other", BudgetCategory.TYPE_EXPENSE, "other", "#06B6D4", 6));
            defaults.add(buildDefaultCat("salary", BudgetCategory.TYPE_INCOME, "salary", "#10B981", 1));
            defaults.add(buildDefaultCat("freelance", BudgetCategory.TYPE_INCOME, "freelance", "#0EA5E9", 2));
            defaults.add(buildDefaultCat("investments", BudgetCategory.TYPE_INCOME, "investments", "#6366F1", 3));
            defaults.add(buildDefaultCat("income_other", BudgetCategory.TYPE_INCOME, "income_other", "#EAB308", 4));
            categoryDao.insertAll(defaults);
        });
    }

    private BudgetCategory buildDefaultCat(String code, String type, String icon, String color, int order) {
        BudgetCategory cat = new BudgetCategory(code, "", type, icon, color, true, order);
        cat.firestoreId = UUIDGenerator.generateSequentialUUID();
        cat.ownerUID = getCurrentUid();
        return cat;
    }

    public void addCustomCategory(String name, String type, String iconKey, String colorHex, Runnable onDone) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            BudgetCategory cat = new BudgetCategory("", name != null ? name.trim() : "", type, iconKey, colorHex, false, 100);
            cat.firestoreId = UUIDGenerator.generateSequentialUUID();
            cat.ownerUID = getCurrentUid();
            cat.syncStatus = "NEW";
            cat.lastModified = System.currentTimeMillis();
            categoryDao.insert(cat);
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
            BudgetEntry entry = new BudgetEntry();
            entry.firestoreId = UUIDGenerator.generateSequentialUUID();
            entry.ownerUID = getCurrentUid();
            entry.categoryId = categoryId;
            entry.currencyId = currencyId;
            entry.currencyFirestoreId = currencyFirestoreId != null ? currencyFirestoreId : "";
            entry.type = type;
            entry.amount = amount;
            entry.note = note != null ? note.trim() : "";
            entry.entryDate = entryDate;
            entry.yearMonth = BudgetUtils.toYearMonth(entryDate);
            entry.syncStatus = "NEW";
            entry.lastModified = now;
            entry.lastSyncDate = 0L;
            entry.isDeleted = false;
            entryDao.insert(entry);
            if (onDone != null) onDone.run();
        });
    }

    public void updateEntry(int entryId, int categoryId, double amount, String note,
                            long entryDate, Runnable onDone) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            BudgetEntry existing = entryDao.getByIdSync(entryId);
            if (existing != null) {
                existing.categoryId = categoryId;
                existing.amount = amount;
                existing.note = note != null ? note.trim() : "";
                existing.entryDate = entryDate;
                existing.yearMonth = BudgetUtils.toYearMonth(entryDate);
                existing.lastModified = System.currentTimeMillis();
                if (!"NEW".equals(existing.syncStatus)) {
                    existing.syncStatus = "EDITED";
                }
                entryDao.update(existing);
            }
            if (onDone != null) onDone.run();
        });
    }

    public void deleteEntry(int entryId, Runnable onDone) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            entryDao.softDeleteById(entryId, System.currentTimeMillis());
            if (onDone != null) onDone.run();
        });
    }

    public void saveMonthlyLimit(String yearMonth, int currencyId, String currencyFirestoreId,
                                 double limitAmount, Runnable onDone) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            long now = System.currentTimeMillis();
            MonthlyBudgetLimit existing = limitDao.getExactLimitSync(yearMonth, currencyId);
            if (existing != null) {
                existing.limitAmount = limitAmount;
                existing.isDeleted = false;
                existing.lastModified = now;
                if (!"NEW".equals(existing.syncStatus)) {
                    existing.syncStatus = "EDITED";
                }
                limitDao.update(existing);
            } else {
                MonthlyBudgetLimit limit = new MonthlyBudgetLimit();
                limit.firestoreId = UUIDGenerator.generateSequentialUUID();
                limit.ownerUID = getCurrentUid();
                limit.currencyId = currencyId;
                limit.currencyFirestoreId = currencyFirestoreId != null ? currencyFirestoreId : "";
                limit.yearMonth = yearMonth;
                limit.limitAmount = limitAmount;
                limit.syncStatus = "NEW";
                limit.lastModified = now;
                limit.lastSyncDate = 0L;
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

                String uid = "";
                try {
                    SecureLicenseManager slm = SecureLicenseManager.getInstance(appCtx);
                    if (slm.isGuest()) {
                        uid = slm.guestUID() != null ? slm.guestUID() : "";
                    } else if (FirebaseAuth.getInstance().getUid() != null) {
                        uid = FirebaseAuth.getInstance().getUid();
                    }
                } catch (Exception ignored) {
                }

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

                // debtTxType == -1 (له / دائن) -> دخل آخر (income_other)
                // debtTxType == 1 (عليه / مدين) -> مصروف أخرى (other)
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

                String currencyFsId = "";
                try {
                    String fetched = db.currencyDao().getCurrencyFirestoreId(resolvedCurrencyId);
                    if (fetched != null) currencyFsId = fetched;
                } catch (Exception ignored) {
                }

                long safeDate = entryDateMillis > 0 ? entryDateMillis : System.currentTimeMillis();
                long now = System.currentTimeMillis();

                BudgetEntry entry = new BudgetEntry();
                entry.firestoreId = UUIDGenerator.generateSequentialUUID();
                entry.ownerUID = uid;
                entry.categoryId = catId;
                entry.currencyId = resolvedCurrencyId;
                entry.currencyFirestoreId = currencyFsId;
                entry.type = budgetType;
                entry.amount = amount;
                entry.note = linkedNote;
                entry.entryDate = safeDate;
                entry.yearMonth = BudgetUtils.toYearMonth(safeDate);
                entry.syncStatus = "NEW";
                entry.lastModified = now;
                entry.lastSyncDate = 0L;
                entry.isDeleted = false;

                long insertedId = eDao.insert(entry);
                Log.i("BudgetLink", "Inserted linked budget entry id=" + insertedId
                        + " type=" + budgetType + " amount=" + amount
                        + " currencyId=" + resolvedCurrencyId + " ym=" + entry.yearMonth);

                if (insertedId > 0) {
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
        cat.firestoreId = UUIDGenerator.generateSequentialUUID();
        cat.ownerUID = uid;
        cat.lastModified = now;
        return cat;
    }
}
