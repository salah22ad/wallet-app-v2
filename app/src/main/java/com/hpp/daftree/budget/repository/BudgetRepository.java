package com.hpp.daftree.budget.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.google.firebase.auth.FirebaseAuth;
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
            if (categoryDao.getCountSync() > 0) return;
            List<BudgetCategory> defaults = new ArrayList<>();
            defaults.add(buildDefaultCat("bills", BudgetCategory.TYPE_EXPENSE, "bills", "#F59E0B", 1));
            defaults.add(buildDefaultCat("housing", BudgetCategory.TYPE_EXPENSE, "housing", "#22C55E", 2));
            defaults.add(buildDefaultCat("food", BudgetCategory.TYPE_EXPENSE, "food", "#F97316", 3));
            defaults.add(buildDefaultCat("transport", BudgetCategory.TYPE_EXPENSE, "transport", "#3B82F6", 4));
            defaults.add(buildDefaultCat("health", BudgetCategory.TYPE_EXPENSE, "health", "#A855F7", 5));
            defaults.add(buildDefaultCat("other", BudgetCategory.TYPE_EXPENSE, "other", "#06B6D4", 6));
            defaults.add(buildDefaultCat("salary", BudgetCategory.TYPE_INCOME, "salary", "#10B981", 1));
            defaults.add(buildDefaultCat("network", BudgetCategory.TYPE_INCOME, "network", "#0EA5E9", 2));
            defaults.add(buildDefaultCat("apps", BudgetCategory.TYPE_INCOME, "apps", "#6366F1", 3));
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
                limit.isDeleted = false;
                limitDao.insert(limit);
            }
            if (onDone != null) onDone.run();
        });
    }
}
