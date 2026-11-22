package com.hpp.daftree.budget.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.PropertyName;

@IgnoreExtraProperties
@Entity(
        tableName = "monthly_budget_limits",
        indices = {
                @Index(value = {"yearMonth", "currencyId"}, name = "index_monthly_budget_limits_ym_cur", unique = true)
        }
)
public class MonthlyBudgetLimit {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public int id;

    @ColumnInfo(name = "firestoreId")
    @PropertyName("firestoreId")
    public String firestoreId;

    @ColumnInfo(name = "ownerUID")
    @PropertyName("ownerUID")
    public String ownerUID;

    @ColumnInfo(name = "currencyId")
    @PropertyName("currencyId")
    public int currencyId;

    @ColumnInfo(name = "currencyFirestoreId")
    @PropertyName("currencyFirestoreId")
    public String currencyFirestoreId;

    @ColumnInfo(name = "yearMonth")
    @PropertyName("yearMonth")
    public String yearMonth;

    @ColumnInfo(name = "limitAmount")
    @PropertyName("limitAmount")
    public double limitAmount;

    @ColumnInfo(name = "syncStatus")
    @PropertyName("syncStatus")
    public String syncStatus = "NEW";

    @ColumnInfo(name = "lastModified")
    @PropertyName("lastModified")
    public long lastModified = 0L;

    @ColumnInfo(name = "lastSyncDate")
    @PropertyName("lastSyncDate")
    public long lastSyncDate = 0L;

    @ColumnInfo(name = "isDeleted")
    @PropertyName("isDeleted")
    public boolean isDeleted = false;

    public MonthlyBudgetLimit() {}
}
