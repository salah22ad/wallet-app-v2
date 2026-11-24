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
    public String firestoreId = "";

    @ColumnInfo(name = "ownerUID")
    @PropertyName("ownerUID")
    public String ownerUID = "";

    @ColumnInfo(name = "currencyId")
    @PropertyName("currencyId")
    public int currencyId;

    @ColumnInfo(name = "currencyFirestoreId")
    @PropertyName("currencyFirestoreId")
    public String currencyFirestoreId = "";

    @ColumnInfo(name = "yearMonth")
    @PropertyName("yearMonth")
    public String yearMonth = "";

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

    @ColumnInfo(name = "deleteDate")
    @PropertyName("deleteDate")
    public long deleteDate = 0L;

    @ColumnInfo(name = "deletedBy")
    @PropertyName("deletedBy")
    public String deletedBy = "";

    public MonthlyBudgetLimit() {}

    // --- Getters & Setters ---
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    @PropertyName("firestoreId")
    public String getFirestoreId() { return firestoreId; }
    @PropertyName("firestoreId")
    public void setFirestoreId(String firestoreId) { this.firestoreId = firestoreId; }

    @PropertyName("ownerUID")
    public String getOwnerUID() { return ownerUID; }
    @PropertyName("ownerUID")
    public void setOwnerUID(String ownerUID) { this.ownerUID = ownerUID; }

    @PropertyName("currencyId")
    public int getCurrencyId() { return currencyId; }
    @PropertyName("currencyId")
    public void setCurrencyId(int currencyId) { this.currencyId = currencyId; }

    @PropertyName("currencyFirestoreId")
    public String getCurrencyFirestoreId() { return currencyFirestoreId; }
    @PropertyName("currencyFirestoreId")
    public void setCurrencyFirestoreId(String currencyFirestoreId) { this.currencyFirestoreId = currencyFirestoreId; }

    @PropertyName("yearMonth")
    public String getYearMonth() { return yearMonth; }
    @PropertyName("yearMonth")
    public void setYearMonth(String yearMonth) { this.yearMonth = yearMonth; }

    @PropertyName("limitAmount")
    public double getLimitAmount() { return limitAmount; }
    @PropertyName("limitAmount")
    public void setLimitAmount(double limitAmount) { this.limitAmount = limitAmount; }

    @PropertyName("syncStatus")
    public String getSyncStatus() { return syncStatus; }
    @PropertyName("syncStatus")
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }

    @PropertyName("lastModified")
    public long getLastModified() { return lastModified; }
    @PropertyName("lastModified")
    public void setLastModified(long lastModified) { this.lastModified = lastModified; }

    @PropertyName("lastSyncDate")
    public long getLastSyncDate() { return lastSyncDate; }
    @PropertyName("lastSyncDate")
    public void setLastSyncDate(long lastSyncDate) { this.lastSyncDate = lastSyncDate; }

    @PropertyName("isDeleted")
    public boolean isDeleted() { return isDeleted; }
    @PropertyName("isDeleted")
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    @PropertyName("deleteDate")
    public long getDeleteDate() { return deleteDate; }
    @PropertyName("deleteDate")
    public void setDeleteDate(long deleteDate) { this.deleteDate = deleteDate; }

    @PropertyName("deletedBy")
    public String getDeletedBy() { return deletedBy; }
    @PropertyName("deletedBy")
    public void setDeletedBy(String deletedBy) { this.deletedBy = deletedBy; }
}
