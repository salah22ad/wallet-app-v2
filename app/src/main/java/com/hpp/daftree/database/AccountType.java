package com.hpp.daftree.database;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.PropertyName;
@IgnoreExtraProperties
@Entity(tableName = "account_types")
public class AccountType {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    @Exclude
    public int id;

    @ColumnInfo(name = "deleteDate")
    @PropertyName("deleteDate")
    private long deleteDate = 0L;

    @ColumnInfo(name = "name")
    @PropertyName("name")
    public String name;

    @ColumnInfo(name = "firestoreId")
    @PropertyName("firestoreId")
    private String firestoreId;

    @ColumnInfo(name = "ownerUID")
    @PropertyName("ownerUID")
    private String ownerUID;

    @ColumnInfo(name = "lastModified")
    @PropertyName("lastModified")
    private long lastModified;

    @ColumnInfo(name = "lastSyncDate")
    @PropertyName("lastSyncDate")
    private long lastSyncDate;


    @ColumnInfo(name = "syncStatus")
    @PropertyName("syncStatus")
    private String syncStatus;

    @ColumnInfo(name = "isDefault")
    @PropertyName("isDefault")
    private boolean isDefault = false;

    // --- الحقول الجديدة للحذف ---
    @ColumnInfo(name = "isDeleted")
    @PropertyName("isDeleted")
    private boolean isDeleted = false;

    @ColumnInfo(name = "deletedBy")
    @PropertyName("deletedBy")
    private String deletedBy= "";

    public AccountType() {}

    @Ignore
    public AccountType(String name) {
        this.name = name;
    }

    // Getters and Setters
    @PropertyName("name")
    public String getName() { return name; }
    @PropertyName("name")
    public void setName(String name) { this.name = name; }

    @PropertyName("deleteDate")
    public long getDeleteDate() { return deleteDate; }
    @PropertyName("deleteDate")
    public void setDeleteDate(long deleteDate) { this.deleteDate = deleteDate; }

    @PropertyName("firestoreId")
    public String getFirestoreId() { return firestoreId; }
    @PropertyName("firestoreId")
    public void setFirestoreId(String firestoreId) { this.firestoreId = firestoreId; }

    @PropertyName("ownerUID")
    public String getOwnerUID() { return ownerUID; }
    @PropertyName("ownerUID")
    public void setOwnerUID(String ownerUID) { this.ownerUID = ownerUID; }

    @PropertyName("lastModified")
    public long getLastModified() { return lastModified; }
    @PropertyName("lastModified")
    public void setLastModified(long lastModified) { this.lastModified = lastModified; }

    @PropertyName("syncStatus")
    public String getSyncStatus() { return syncStatus; }
    @PropertyName("syncStatus")
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }

    @PropertyName("isDefault")
    public boolean isDefault() { return isDefault; }
    @PropertyName("isDefault")
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }

    @PropertyName("isDeleted")
    public boolean isDeleted() { return isDeleted; }
    @PropertyName("isDeleted")
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    @PropertyName("deletedBy")
    public String getDeletedBy() { return deletedBy; }
    @PropertyName("deletedBy")
    public void setDeletedBy(String deletedBy) { this.deletedBy = deletedBy; }


    @PropertyName("lastSyncDate")
    public long getLastSyncDate() { return lastSyncDate; }
    @PropertyName("lastSyncDate")
    public void setLastSyncDate(long lastSyncDate) { this.lastSyncDate = lastSyncDate; }
}