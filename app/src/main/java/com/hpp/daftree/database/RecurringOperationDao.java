package com.hpp.daftree.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface RecurringOperationDao {

    @Insert
    long insert(RecurringOperation operation);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(RecurringOperation operation);

    @Update
    void update(RecurringOperation operation);

    @Delete
    void delete(RecurringOperation operation);

    @Query("SELECT * FROM recurring_operations WHERE ownerUID = :uid AND active = 1 ORDER BY accountName ASC")
    LiveData<List<RecurringOperation>> getActiveOperations(String uid);

    @Query("SELECT * FROM recurring_operations WHERE accountId = :accountId AND active = 1 AND endDate >= :today ORDER BY startDate ASC")
    LiveData<List<RecurringOperation>> getActiveOperationsForAccount(int accountId, long today);

    @Query("SELECT * FROM recurring_operations WHERE id = :id LIMIT 1")
    RecurringOperation getById(int id);

    @Query("SELECT * FROM recurring_operations WHERE syncStatus IN ('NEW', 'EDITED')")
    List<RecurringOperation> getUnsynced();

    @Query("UPDATE recurring_operations SET syncStatus = :status, lastModified = :timestamp WHERE id = :id")
    int updateSyncStatus(int id, String status, long timestamp);
    // Get recurring operations for a specific account
    @Query("SELECT * FROM recurring_operations WHERE accountId = :accountId AND active = 1")
    List<RecurringOperation> getRecurringOperationsForAccount(int accountId);
}