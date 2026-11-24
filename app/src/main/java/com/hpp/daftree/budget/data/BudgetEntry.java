package com.hpp.daftree.budget.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.PropertyName;

@IgnoreExtraProperties
@Entity(
        tableName = "budget_entries",
        indices = {
                @Index(value = {"yearMonth"}, name = "index_budget_entries_yearMonth"),
                @Index(value = {"currencyId"}, name = "index_budget_entries_currencyId"),
                @Index(value = {"categoryId"}, name = "index_budget_entries_categoryId")
        }
)
public class BudgetEntry {

    public static final String TYPE_EXPENSE = "EXPENSE";
    public static final String TYPE_INCOME = "INCOME";

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public int id;

    @ColumnInfo(name = "firestoreId")
    @PropertyName("firestoreId")
    public String firestoreId = "";

    @ColumnInfo(name = "ownerUID")
    @PropertyName("ownerUID")
    public String ownerUID = "";

    @ColumnInfo(name = "categoryId")
    @PropertyName("categoryId")
    public int categoryId;

    @ColumnInfo(name = "categoryFirestoreId")
    @PropertyName("categoryFirestoreId")
    public String categoryFirestoreId = "";

    @ColumnInfo(name = "currencyId")
    @PropertyName("currencyId")
    public int currencyId;

    @ColumnInfo(name = "currencyFirestoreId")
    @PropertyName("currencyFirestoreId")
    public String currencyFirestoreId = "";

    @ColumnInfo(name = "type")
    @PropertyName("type")
    public String type = TYPE_EXPENSE;

    @ColumnInfo(name = "amount")
    @PropertyName("amount")
    public double amount;

    @ColumnInfo(name = "note")
    @PropertyName("note")
    public String note = "";

    @ColumnInfo(name = "entryDate")
    @PropertyName("entryDate")
    public long entryDate;

    @ColumnInfo(name = "yearMonth")
    @PropertyName("yearMonth")
    public String yearMonth = "";

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

    public BudgetEntry() {}

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

    @PropertyName("categoryId")
    public int getCategoryId() { return categoryId; }
    @PropertyName("categoryId")
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }

    @PropertyName("categoryFirestoreId")
    public String getCategoryFirestoreId() { return categoryFirestoreId; }
    @PropertyName("categoryFirestoreId")
    public void setCategoryFirestoreId(String categoryFirestoreId) { this.categoryFirestoreId = categoryFirestoreId; }

    @PropertyName("currencyId")
    public int getCurrencyId() { return currencyId; }
    @PropertyName("currencyId")
    public void setCurrencyId(int currencyId) { this.currencyId = currencyId; }

    @PropertyName("currencyFirestoreId")
    public String getCurrencyFirestoreId() { return currencyFirestoreId; }
    @PropertyName("currencyFirestoreId")
    public void setCurrencyFirestoreId(String currencyFirestoreId) { this.currencyFirestoreId = currencyFirestoreId; }

    @PropertyName("type")
    public String getType() { return type; }
    @PropertyName("type")
    public void setType(String type) { this.type = type; }

    @PropertyName("amount")
    public double getAmount() { return amount; }
    @PropertyName("amount")
    public void setAmount(double amount) { this.amount = amount; }

    @PropertyName("note")
    public String getNote() { return note; }
    @PropertyName("note")
    public void setNote(String note) { this.note = note; }

    @PropertyName("entryDate")
    public long getEntryDate() { return entryDate; }
    @PropertyName("entryDate")
    public void setEntryDate(long entryDate) { this.entryDate = entryDate; }

    @PropertyName("yearMonth")
    public String getYearMonth() { return yearMonth; }
    @PropertyName("yearMonth")
    public void setYearMonth(String yearMonth) { this.yearMonth = yearMonth; }

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
