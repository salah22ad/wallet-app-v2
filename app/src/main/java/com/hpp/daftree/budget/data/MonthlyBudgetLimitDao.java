package com.hpp.daftree.budget.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface MonthlyBudgetLimitDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(MonthlyBudgetLimit limit);

    @Update
    void update(MonthlyBudgetLimit limit);

    @Query("SELECT * FROM monthly_budget_limits WHERE yearMonth = :yearMonth AND currencyId = :currencyId LIMIT 1")
    MonthlyBudgetLimit getExactLimitSync(String yearMonth, int currencyId);

    @Query("SELECT * FROM monthly_budget_limits WHERE firestoreId = :firestoreId LIMIT 1")
    MonthlyBudgetLimit getByFirestoreIdSync(String firestoreId);

    @Query("SELECT limitAmount FROM monthly_budget_limits " +
            "WHERE currencyId = :currencyId AND yearMonth <= :yearMonth AND isDeleted = 0 " +
            "ORDER BY yearMonth DESC LIMIT 1")
    LiveData<Double> getEffectiveLimit(String yearMonth, int currencyId);

    @Query("SELECT limitAmount FROM monthly_budget_limits " +
            "WHERE currencyId = :currencyId AND yearMonth <= :yearMonth AND isDeleted = 0 " +
            "ORDER BY yearMonth DESC LIMIT 1")
    Double getEffectiveLimitSync(String yearMonth, int currencyId);

    @Query("SELECT * FROM monthly_budget_limits WHERE syncStatus != 'SYNCED'")
    List<MonthlyBudgetLimit> getUnsyncedLimitsSync();

    @Query("SELECT COUNT(*) FROM monthly_budget_limits WHERE syncStatus != 'SYNCED'")
    int getUnsyncedCount();

    @Query("SELECT COUNT(*) FROM monthly_budget_limits WHERE isDeleted = 0 AND limitAmount > 0")
    int getTotalActiveLimitCountSync();

    @Query("DELETE FROM monthly_budget_limits WHERE firestoreId = :firestoreId")
    void deleteByFirestoreId(String firestoreId);

    @Query("SELECT * FROM monthly_budget_limits WHERE isDeleted = 1 AND deleteDate > 0 AND deleteDate <= :threshold")
    List<MonthlyBudgetLimit> getOldDeletedLimits(long threshold);
    @Query("SELECT * FROM monthly_budget_limits WHERE currencyId = :currencyId AND isDeleted = 0 ORDER BY yearMonth ASC")
    List<MonthlyBudgetLimit> getAllActiveLimitsForCurrencySync(int currencyId);

    @Query("UPDATE monthly_budget_limits SET syncStatus = :status, ownerUID = :ownerUID WHERE isDeleted = 0")
    void upgradeToOfficialUser(String ownerUID, String status);

    @Query("DELETE FROM monthly_budget_limits WHERE isDeleted = 1")
    void deleteGuestDeletedLimits();

    @Query("DELETE FROM monthly_budget_limits")
    void deleteGuestData();
}