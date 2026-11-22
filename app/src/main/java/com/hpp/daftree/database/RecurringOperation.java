package com.hpp.daftree.database;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.google.firebase.firestore.PropertyName;

@Entity(tableName = "recurring_operations")
public class RecurringOperation {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private int id;


    @NonNull
    @ColumnInfo(name = "firestoreId")
    @PropertyName("firestoreId")
    private String firestoreId = "";

    @ColumnInfo(name = "accountId")
    @PropertyName("accountId")
    private int accountId;

    @ColumnInfo(name = "accountName")
    @PropertyName("accountName")
    private String accountName; // اسم الحساب للتسهيل

    @ColumnInfo(name = "amount")
    @PropertyName("amount")
    private double amount;

    @ColumnInfo(name = "type")
    @PropertyName("type")
    private int type; // 1 = مدين, -1 = دائن

    @ColumnInfo(name = "details")
    @PropertyName("details")
    private String details;

    @ColumnInfo(name = "currencyId")
    @PropertyName("currencyId")
    private int currencyId;

    @NonNull
    @ColumnInfo(name = "currencyFirestoreId")
    @PropertyName("currencyFirestoreId")
    private String currencyFirestoreId = "";
    @NonNull
    @ColumnInfo(name = "recurrenceType")
    @PropertyName("recurrenceType")
    private String recurrenceType = ""; // "day", "week", "month"
    @ColumnInfo(name = "recurrenceDay")
    @PropertyName("recurrenceDay")
    private int recurrenceDay ; // 1-7 للأسبوع, 1-31 للشهر, 0 لليومي

    @ColumnInfo(name = "startDate")
    @PropertyName("startDate")
    private long startDate; // timestamp

    @ColumnInfo(name = "endDate")
    @PropertyName("endDate")
    private long endDate; // timestamp

    @ColumnInfo(name = "active")
    @PropertyName("active")
    private boolean active = true;

    @ColumnInfo(name = "syncStatus")
    @PropertyName("syncStatus")
    private String syncStatus;

    @ColumnInfo(name = "lastModified")
    @PropertyName("lastModified")
    private long lastModified;

    @NonNull
    @ColumnInfo(name = "ownerUID")
    @PropertyName("ownerUID")
    private String ownerUID = "";

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    @NonNull
    public String getFirestoreId() { return firestoreId; }
    public void setFirestoreId(@NonNull String firestoreId) { this.firestoreId = firestoreId; }

    public int getAccountId() { return accountId; }
    public void setAccountId(int accountId) { this.accountId = accountId; }

    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public int getType() { return type; }
    public void setType(int type) { this.type = type; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public int getCurrencyId() { return currencyId; }
    public void setCurrencyId(int currencyId) { this.currencyId = currencyId; }

    @NonNull
    public String getCurrencyFirestoreId() { return currencyFirestoreId; }
    public void setCurrencyFirestoreId(@NonNull String currencyFirestoreId) { this.currencyFirestoreId = currencyFirestoreId; }
    @NonNull
    public String getRecurrenceType() { return recurrenceType; }
    public void setRecurrenceType(@NonNull String recurrenceType) { this.recurrenceType = recurrenceType; }
    public int getRecurrenceDay() { return recurrenceDay; }
    public void setRecurrenceDay(int recurrenceDay) { this.recurrenceDay = recurrenceDay; }

    public long getStartDate() { return startDate; }
    public void setStartDate(long startDate) { this.startDate = startDate; }

    public long getEndDate() { return endDate; }
    public void setEndDate(long endDate) { this.endDate = endDate; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }

    public long getLastModified() { return lastModified; }
    public void setLastModified(long lastModified) { this.lastModified = lastModified; }

    @NonNull
    public String getOwnerUID() { return ownerUID; }
    public void setOwnerUID(@NonNull String ownerUID) { this.ownerUID = ownerUID; }
}