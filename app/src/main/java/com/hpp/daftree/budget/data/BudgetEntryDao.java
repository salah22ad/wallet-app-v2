package com.hpp.daftree.budget.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface BudgetEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(BudgetEntry entry);

    @Update
    void update(BudgetEntry entry);

    @Query("SELECT * FROM budget_entries WHERE id = :id LIMIT 1")
    BudgetEntry getByIdSync(int id);

    @Query("SELECT * FROM budget_entries WHERE firestoreId = :firestoreId LIMIT 1")
    BudgetEntry getByFirestoreIdSync(String firestoreId);

    @Query("UPDATE budget_entries SET isDeleted = 1, syncStatus = 'DELETED', deleteDate = :now, lastModified = :now WHERE id = :id")
    void softDeleteById(int id, long now);

    @Query("UPDATE budget_entries SET isDeleted = 1, syncStatus = 'DELETED', deleteDate = :now, deletedBy = :deletedBy, lastModified = :now WHERE id = :id")
    void softDeleteByIdWithUser(int id, long now, String deletedBy);

    @Query("SELECT e.id AS id, e.firestoreId AS firestoreId, e.categoryId AS categoryId, " +
            "e.currencyId AS currencyId, e.type AS type, e.amount AS amount, e.note AS note, " +
            "e.entryDate AS entryDate, e.yearMonth AS yearMonth, " +
            "c.code AS categoryCode, c.name AS categoryName, c.isDefault AS categoryIsDefault, " +
            "c.iconKey AS categoryIconKey, c.colorHex AS categoryColorHex " +
            "FROM budget_entries e " +
            "LEFT JOIN budget_categories c ON e.categoryId = c.id " +
            "WHERE e.yearMonth = :yearMonth AND e.currencyId = :currencyId AND e.isDeleted = 0 " +
            "ORDER BY e.entryDate DESC, e.id DESC")
    LiveData<List<BudgetEntryWithCategory>> getEntriesForMonth(String yearMonth, int currencyId);

    @Query("SELECT e.id AS id, e.firestoreId AS firestoreId, e.categoryId AS categoryId, " +
            "e.currencyId AS currencyId, e.type AS type, e.amount AS amount, e.note AS note, " +
            "e.entryDate AS entryDate, e.yearMonth AS yearMonth, " +
            "c.code AS categoryCode, c.name AS categoryName, c.isDefault AS categoryIsDefault, " +
            "c.iconKey AS categoryIconKey, c.colorHex AS categoryColorHex " +
            "FROM budget_entries e " +
            "LEFT JOIN budget_categories c ON e.categoryId = c.id " +
            "WHERE e.yearMonth = :yearMonth AND e.currencyId = :currencyId AND e.isDeleted = 0 " +
            "ORDER BY e.entryDate DESC, e.id DESC")
    List<BudgetEntryWithCategory> getEntriesForMonthSync(String yearMonth, int currencyId);

    @Query("SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0) AS totalIncome, " +
            "COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS totalExpense, " +
            "COUNT(*) AS entryCount " +
            "FROM budget_entries " +
            "WHERE yearMonth = :yearMonth AND currencyId = :currencyId AND isDeleted = 0")
    LiveData<MonthSummaryStats> getMonthSummary(String yearMonth, int currencyId);

    @Query("SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0) AS totalIncome, " +
            "COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS totalExpense, " +
            "COUNT(*) AS entryCount " +
            "FROM budget_entries " +
            "WHERE yearMonth = :yearMonth AND currencyId = :currencyId AND isDeleted = 0")
    MonthSummaryStats getMonthSummarySync(String yearMonth, int currencyId);

    @Query("SELECT c.id AS categoryId, c.code AS categoryCode, c.name AS name, c.isDefault AS isDefault, " +
            "c.iconKey AS iconKey, c.colorHex AS colorHex, " +
            "COALESCE(SUM(e.amount), 0) AS totalAmount, COUNT(e.id) AS entryCount " +
            "FROM budget_categories c " +
            "LEFT JOIN budget_entries e ON e.categoryId = c.id " +
            "AND e.yearMonth = :yearMonth AND e.currencyId = :currencyId " +
            "AND e.type = 'EXPENSE' AND e.isDeleted = 0 " +
            "WHERE c.type = 'EXPENSE' AND c.isDeleted = 0 " +
            "GROUP BY c.id " +
            "ORDER BY totalAmount DESC, c.sortOrder ASC")
    LiveData<List<CategoryExpenseTotal>> getExpenseDistributionForMonth(String yearMonth, int currencyId);

    @Query("SELECT yearMonth, " +
            "COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0) AS totalIncome, " +
            "COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS totalExpense " +
            "FROM budget_entries " +
            "WHERE yearMonth IN (:yearMonths) AND currencyId = :currencyId AND isDeleted = 0 " +
            "GROUP BY yearMonth ORDER BY yearMonth ASC")
    LiveData<List<MonthlyTotals>> getMonthlyComparison(List<String> yearMonths, int currencyId);

    @Query("SELECT * FROM budget_entries WHERE syncStatus != 'SYNCED'")
    List<BudgetEntry> getUnsyncedEntriesSync();

    @Query("SELECT COUNT(*) FROM budget_entries WHERE syncStatus != 'SYNCED'")
    int getUnsyncedCount();

    @Query("SELECT COUNT(*) FROM budget_entries WHERE isDeleted = 0")
    int getTotalActiveEntryCountSync();

    @Query("DELETE FROM budget_entries WHERE firestoreId = :firestoreId")
    void deleteByFirestoreId(String firestoreId);

    @Query("SELECT * FROM budget_entries WHERE isDeleted = 1 AND deleteDate > 0 AND deleteDate <= :threshold")
    List<BudgetEntry> getOldDeletedEntries(long threshold);
    @Query("SELECT e.id AS id, e.firestoreId AS firestoreId, e.categoryId AS categoryId, " +
            "e.currencyId AS currencyId, e.type AS type, e.amount AS amount, e.note AS note, " +
            "e.entryDate AS entryDate, e.yearMonth AS yearMonth, " +
            "c.code AS categoryCode, c.name AS categoryName, c.isDefault AS categoryIsDefault, " +
            "c.iconKey AS categoryIconKey, c.colorHex AS categoryColorHex " +
            "FROM budget_entries e LEFT JOIN budget_categories c ON e.categoryId = c.id " +
            "WHERE e.currencyId = :currencyId AND e.isDeleted = 0 AND e.entryDate >= :startDate AND e.entryDate <= :endDate " +
            "ORDER BY e.entryDate ASC, e.id ASC")
    List<BudgetEntryWithCategory> getEntriesByDateRangeSync(int currencyId, long startDate, long endDate);

    @Query("SELECT e.id AS id, e.firestoreId AS firestoreId, e.categoryId AS categoryId, " +
            "e.currencyId AS currencyId, e.type AS type, e.amount AS amount, e.note AS note, " +
            "e.entryDate AS entryDate, e.yearMonth AS yearMonth, " +
            "c.code AS categoryCode, c.name AS categoryName, c.isDefault AS categoryIsDefault, " +
            "c.iconKey AS categoryIconKey, c.colorHex AS categoryColorHex " +
            "FROM budget_entries e LEFT JOIN budget_categories c ON e.categoryId = c.id " +
            "WHERE e.currencyId = :currencyId AND e.isDeleted = 0 " +
            "ORDER BY e.entryDate ASC, e.id ASC")
    List<BudgetEntryWithCategory> getAllEntriesForCurrencySync(int currencyId);

    @Query("SELECT c.id AS categoryId, c.code AS categoryCode, c.name AS name, c.isDefault AS isDefault, " +
            "c.iconKey AS iconKey, c.colorHex AS colorHex, " +
            "COALESCE(SUM(e.amount), 0) AS totalAmount, COUNT(e.id) AS entryCount " +
            "FROM budget_entries e INNER JOIN budget_categories c ON e.categoryId = c.id " +
            "WHERE e.currencyId = :currencyId AND e.type = :type AND e.isDeleted = 0 " +
            "AND e.entryDate >= :startDate AND e.entryDate <= :endDate " +
            "GROUP BY c.id ORDER BY totalAmount DESC")
    List<CategoryExpenseTotal> getCategoryTotalsByDateRangeSync(int currencyId, String type, long startDate, long endDate);

    @Query("SELECT c.id AS categoryId, c.code AS categoryCode, c.name AS name, c.isDefault AS isDefault, " +
            "c.iconKey AS iconKey, c.colorHex AS colorHex, " +
            "COALESCE(SUM(e.amount), 0) AS totalAmount, COUNT(e.id) AS entryCount " +
            "FROM budget_entries e INNER JOIN budget_categories c ON e.categoryId = c.id " +
            "WHERE e.currencyId = :currencyId AND e.type = :type AND e.isDeleted = 0 " +
            "GROUP BY c.id ORDER BY totalAmount DESC")
    List<CategoryExpenseTotal> getCategoryTotalsAllTimeSync(int currencyId, String type);

    @Query("SELECT yearMonth, " +
            "COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0) AS totalIncome, " +
            "COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS totalExpense " +
            "FROM budget_entries WHERE currencyId = :currencyId AND isDeleted = 0 " +
            "AND entryDate >= :startDate AND entryDate <= :endDate " +
            "GROUP BY yearMonth ORDER BY yearMonth ASC")
    List<MonthlyTotals> getMonthlyTotalsByDateRangeSync(int currencyId, long startDate, long endDate);

    @Query("SELECT yearMonth, " +
            "COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0) AS totalIncome, " +
            "COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS totalExpense " +
            "FROM budget_entries WHERE currencyId = :currencyId AND isDeleted = 0 " +
            "GROUP BY yearMonth ORDER BY yearMonth ASC")
    List<MonthlyTotals> getMonthlyTotalsAllTimeSync(int currencyId);
}
