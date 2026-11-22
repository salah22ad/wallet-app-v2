package com.hpp.daftree.database;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.google.firebase.firestore.PropertyName;

@Entity(tableName = "recurring_operation_tracking")
public class RecurringOperationTracking {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private int id;

    @ColumnInfo(name = "recurringOperationId")
    @PropertyName("recurringOperationId")
    private int recurringOperationId;

    @ColumnInfo(name = "date")
    @PropertyName("date")
    private long date; // start of day in milliseconds

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getRecurringOperationId() { return recurringOperationId; }
    public void setRecurringOperationId(int recurringOperationId) { this.recurringOperationId = recurringOperationId; }

    public long getDate() { return date; }
    public void setDate(long date) { this.date = date; }
}