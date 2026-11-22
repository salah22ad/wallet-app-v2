package com.hpp.daftree.database;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.PropertyName;
import java.util.Date;
@IgnoreExtraProperties
@Entity(tableName = "transactions",
        indices = {
                @Index(value = "accountId"),
                @Index(value = "currencyId")
        },
        foreignKeys = @ForeignKey(entity = Currency.class,
                parentColumns = "id",
                childColumns = "currencyId",
                onDelete = ForeignKey.RESTRICT))
public class Transaction {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private int id;

    @ColumnInfo(name = "deleteDate")
    @PropertyName("deleteDate")
    private long deleteDate = 0L;
    @ColumnInfo(name = "accountId")
    @PropertyName("accountId")
    private int accountId;

    @ColumnInfo(name = "type")
    @PropertyName("type")
    private int type;

    @ColumnInfo(name = "amount")
    @PropertyName("amount")
    private double amount;

    @ColumnInfo(name = "details")
    @PropertyName("details")
    private String details;

    @ColumnInfo(name = "currencyId")
    @PropertyName("currencyId")
    private int currencyId;

    @ColumnInfo(name = "timestamp")
    @PropertyName("timestamp")
    private Date timestamp;

    @ColumnInfo(name = "firestoreId")
    @PropertyName("firestoreId")
    private String firestoreId;

    @ColumnInfo(name = "syncStatus")
    @PropertyName("syncStatus")
    private String syncStatus;

    @ColumnInfo(name = "lastModified")
    @PropertyName("lastModified")
    private long lastModified;

    @ColumnInfo(name = "lastSyncDate")
    @PropertyName("lastSyncDate")
    private long lastSyncDate;

    @ColumnInfo(name = "importID")
    @PropertyName("importID")
    private int importID=0;

    @ColumnInfo(name = "billType")
    @PropertyName("billType")
    private String billType;

    @NonNull
    @ColumnInfo(name = "accountFirestoreId")
    @PropertyName("accountFirestoreId")
    private String accountFirestoreId = "";

    @NonNull
    @ColumnInfo(name = "currencyFirestoreId")
    @PropertyName("currencyFirestoreId")
    private String currencyFirestoreId = "";


    @ColumnInfo(name = "imageEncoded")
    @PropertyName("imageEncoded")
    private String imageEncoded; // لتخزين الصورة كـ Base64

    @NonNull
    @ColumnInfo(name = "ownerUID")
    @PropertyName("ownerUID")
    private String ownerUID = FirebaseAuth.getInstance().getCurrentUser() != null ?
            FirebaseAuth.getInstance().getCurrentUser().getUid() : "";


    // --- الحقول الجديدة للحذف ---
    @ColumnInfo(name = "isDeleted")
    @PropertyName("isDeleted")
    private boolean isDeleted = false;

    @ColumnInfo(name = "deletedBy")
    @PropertyName("deletedBy")
    private String deletedBy = "";

    // --- Getters with PropertyName ---
    public int getId() { return id; }

    @PropertyName("accountId")
    public int getAccountId() { return accountId; }

    @PropertyName("type")
    public int getType() { return type; }

    @PropertyName("amount")
    public double getAmount() { return amount; }

    @PropertyName("details")
    public String getDetails() { return details; }

    @PropertyName("currencyId")
    public int getCurrencyId() { return currencyId; }

    @PropertyName("timestamp")
    public Date getTimestamp() { return timestamp; }

    @PropertyName("firestoreId")
    public String getFirestoreId() { return firestoreId; }

    @PropertyName("syncStatus")
    public String getSyncStatus() { return syncStatus; }

    @PropertyName("lastModified")
    public long getLastModified() { return lastModified; }

    @NonNull
    @PropertyName("accountFirestoreId")
    public String getAccountFirestoreId() { return accountFirestoreId; }

    @NonNull
    @PropertyName("ownerUID")
    public String getOwnerUID() { return ownerUID; }

    @PropertyName("billType")
    public String getBillType() { return billType; }

    @PropertyName("importID")
    public int getImportID() { return (importID <= 0) ? 0 : importID; }

    @NonNull
    @PropertyName("currencyFirestoreId")
    public String getCurrencyFirestoreId(){return currencyFirestoreId; }

    @PropertyName("isDeleted")
    public boolean isDeleted() { return isDeleted; }

    @PropertyName("deletedBy")
    public String getDeletedBy() { return deletedBy; }

    // --- Setters with PropertyName ---
    public void setId(int id) { this.id = id; }

    @PropertyName("accountId")
    public void setAccountId(int accountId) { this.accountId = accountId; }

    @PropertyName("type")
    public void setType(int type) { this.type = type; }

    @PropertyName("amount")
    public void setAmount(double amount) { this.amount = amount; }

    @PropertyName("details")
    public void setDetails(String details) { this.details = details; }

    @PropertyName("currencyId")
    public void setCurrencyId(int currencyId) { this.currencyId = currencyId; }

    @PropertyName("timestamp")
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }

    @PropertyName("firestoreId")
    public void setFirestoreId(String firestoreId) { this.firestoreId = firestoreId; }

    @PropertyName("syncStatus")
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }

    @PropertyName("lastModified")
    public void setLastModified(long lastModified) { this.lastModified = lastModified; }

    @PropertyName("accountFirestoreId")
    public void setAccountFirestoreId(@NonNull String accountFirestoreId) { this.accountFirestoreId = accountFirestoreId; }

    @PropertyName("ownerUID")
    public void setOwnerUID(@NonNull String ownerUID) { this.ownerUID = ownerUID; }

    @PropertyName("importID")
    public void setImportID(int importID) { this.importID = (importID <= 0) ? 0 : importID; }

    @PropertyName("billType")
    public void setBillType(String billType) { this.billType = billType; }

    @PropertyName("currencyFirestoreId")
    public void setCurrencyFirestoreId( @NonNull String currencyFirestoreId) {this.currencyFirestoreId = currencyFirestoreId;}


    @PropertyName("isDeleted")
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    @PropertyName("deletedBy")
    public void setDeletedBy(String deletedBy) { this.deletedBy = deletedBy; }

    @PropertyName("imageEncoded")
    public String getImageEncoded() { return imageEncoded; }
    @PropertyName("imageEncoded")
    public void setImageEncoded(String imageEncoded) { this.imageEncoded = imageEncoded; }

    @PropertyName("deleteDate")
    public long getDeleteDate() { return deleteDate; }
    @PropertyName("deleteDate")
    public void setDeleteDate(long deleteDate) { this.deleteDate = deleteDate; }


    @PropertyName("lastSyncDate")
    public long getLastSyncDate() { return lastSyncDate; }
    @PropertyName("lastSyncDate")
    public void setLastSyncDate(long lastSyncDate) { this.lastSyncDate = lastSyncDate; }
}