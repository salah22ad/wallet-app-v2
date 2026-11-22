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
    public String firestoreId;

    @ColumnInfo(name = "ownerUID")
    @PropertyName("ownerUID")
    public String ownerUID;

    @ColumnInfo(name = "categoryId")
    @PropertyName("categoryId")
    public int categoryId;

    @ColumnInfo(name = "currencyId")
    @PropertyName("currencyId")
    public int currencyId;

    @ColumnInfo(name = "currencyFirestoreId")
    @PropertyName("currencyFirestoreId")
    public String currencyFirestoreId;

    @ColumnInfo(name = "type")
    @PropertyName("type")
    public String type;

    @ColumnInfo(name = "amount")
    @PropertyName("amount")
    public double amount;

    @ColumnInfo(name = "note")
    @PropertyName("note")
    public String note;

    @ColumnInfo(name = "entryDate")
    @PropertyName("entryDate")
    public long entryDate;

    @ColumnInfo(name = "yearMonth")
    @PropertyName("yearMonth")
    public String yearMonth;

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
}
