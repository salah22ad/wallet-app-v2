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
}
