package com.hpp.daftree.budget.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface BudgetCategoryDao {

    @Query("SELECT * FROM budget_categories WHERE type = :type AND isDeleted = 0 ORDER BY sortOrder ASC, id ASC")
    LiveData<List<BudgetCategory>> getCategoriesByType(String type);

    @Query("SELECT * FROM budget_categories WHERE type = :type AND isDeleted = 0 ORDER BY sortOrder ASC, id ASC")
    List<BudgetCategory> getCategoriesByTypeSync(String type);

    @Query("SELECT * FROM budget_categories WHERE isDeleted = 0 ORDER BY type ASC, sortOrder ASC, id ASC")
    LiveData<List<BudgetCategory>> getAllActiveCategories();

    @Query("SELECT COUNT(*) FROM budget_categories")
    int getCountSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(BudgetCategory category);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<BudgetCategory> categories);

    @Update
    void update(BudgetCategory category);
    @Query("UPDATE budget_categories SET code = :newCode, iconKey = :newIcon WHERE code = :oldCode AND isDefault = 1")
    void migrateDefaultCategoryCode(String oldCode, String newCode, String newIcon);

    @Query("SELECT id FROM budget_categories WHERE code = :code AND isDeleted = 0 LIMIT 1")
    Integer getCategoryIdByCodeSync(String code);
    @Query("SELECT firestoreId FROM budget_categories WHERE id = :categoryId LIMIT 1")
    String getCategoryFirestoreIdSync(int categoryId);

    @Query("SELECT * FROM budget_categories WHERE firestoreId = :firestoreId LIMIT 1")
    BudgetCategory getByFirestoreIdSync(String firestoreId);

    @Query("SELECT * FROM budget_categories WHERE syncStatus != 'SYNCED'")
    List<BudgetCategory> getUnsyncedCategoriesSync();
}
