package com.hpp.daftree.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface RecurringOperationTrackingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(RecurringOperationTracking tracking);

    @Query("SELECT * FROM recurring_operation_tracking WHERE recurringOperationId = :opId AND date = :date LIMIT 1")
    RecurringOperationTracking getTracking(int opId, long date);

    @Query("DELETE FROM recurring_operation_tracking WHERE recurringOperationId = :opId AND date = :date")
    void deleteTracking(int opId, long date);
}