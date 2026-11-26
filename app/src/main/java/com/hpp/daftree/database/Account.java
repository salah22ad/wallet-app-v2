package com.hpp.daftree.database;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.PropertyName;

import java.util.Objects;

@IgnoreExtraProperties
@Entity(tableName = "accounts")
public class Account {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private int id;

    @NonNull
    @ColumnInfo(name = "firestoreId")
    @PropertyName("firestoreId")
    private String firestoreId = "";

    @ColumnInfo(name = "syncStatus")
    @PropertyName("syncStatus")
    private String syncStatus;

    @ColumnInfo(name = "lastModified")
    @PropertyName("lastModified")
    private long lastModified;

    @ColumnInfo(name = "lastSyncDate")
    @PropertyName("lastSyncDate")
    private long lastSyncDate;

    @ColumnInfo(name = "lastTransDate")
    @PropertyName("lastTransDate")
    private long lastTransDate = 0L;

    @NonNull
    @ColumnInfo(name = "ownerUID")
    @PropertyName("ownerUID")
    private String ownerUID = FirebaseAuth.getInstance().getCurrentUser() != null ?
            FirebaseAuth.getInstance().getCurrentUser().getUid() : "";

    @ColumnInfo(name = "accountName")
    @PropertyName("accountName")
    private String accountName;

    @ColumnInfo(name = "phoneNumber")
    @PropertyName("phoneNumber")
    private String phoneNumber;

    @ColumnInfo(name = "accountType")
    @PropertyName("accountType")
    private String accountType;

    @NonNull
    @ColumnInfo(name = "acTypeFirestoreId")
    @PropertyName("acTypeFirestoreId")
    private String acTypeFirestoreId = "";

    // --- الحقل الجديد للمزامنة والحذف ---

    // --- الحقول الجديدة للحذف ---
    @ColumnInfo(name = "isDeleted")
    @PropertyName("isDeleted")
    private boolean isDeleted = false;

    @ColumnInfo(name = "deletedBy")
    @PropertyName("deletedBy")
    private String deletedBy = "";
    @ColumnInfo(name = "deleteDate")
    @PropertyName("deleteDate")
    private long deleteDate = 0L;

    @Exclude
    @ColumnInfo(name = "iconKey")
    private String iconKey = "";

    @Exclude
    @ColumnInfo(name = "colorHex")
    private String colorHex = "";

    @ColumnInfo(name = "notifyWhatsApp")
    @PropertyName("notifyWhatsApp")
    private boolean notifyWhatsApp = false;

    @ColumnInfo(name = "notifySms")
    @PropertyName("notifySms")
    private boolean notifySms = false;
    public Account() {
    }
    // أضف هذا داخل كلاس Account
    public Account(Account other) {
        this.id = other.id;
        this.firestoreId = other.firestoreId;
        this.syncStatus = other.syncStatus;
        this.lastModified = other.lastModified;
        this.lastTransDate = other.lastTransDate;
        this.ownerUID = other.ownerUID;
        this.accountName = other.accountName;
        this.phoneNumber = other.phoneNumber;
        this.accountType = other.accountType;
        this.acTypeFirestoreId = other.acTypeFirestoreId;
        this.isDeleted = other.isDeleted;
        this.deletedBy = other.deletedBy;
        this.iconKey = other.iconKey;
        this.colorHex = other.colorHex;
        this.notifyWhatsApp = other.notifyWhatsApp;
        this.notifySms = other.notifySms;
    }
    // --- Getters ---
    public int getId() { return id; }
    public void setId(int id) {this.id = id;}

    @PropertyName("deleteDate")
    public long getDeleteDate() { return deleteDate; }
    @PropertyName("deleteDate")
    public void setDeleteDate(long deleteDate) { this.deleteDate = deleteDate; }
    @NonNull
    @PropertyName("ownerUID")
    public String getOwnerUID() { return ownerUID; }
    @PropertyName("ownerUID")
    public void setOwnerUID(@NonNull String ownerUID) { this.ownerUID = ownerUID; }

    @PropertyName("accountName")
    public String getAccountName() { return accountName; }
    @PropertyName("accountName")
    public void setAccountName(String accountName) { this.accountName = accountName; }

    @PropertyName("phoneNumber")
    public String getPhoneNumber() { return phoneNumber; }
    @PropertyName("phoneNumber")
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    @PropertyName("accountType")
    public String getAccountType() { return accountType; }
    @PropertyName("accountType")
    public void setAccountType(String accountType) { this.accountType = accountType; }

    @NonNull
    @PropertyName("firestoreId")
    public String getFirestoreId() { return firestoreId; }
    @PropertyName("firestoreId")
    public void setFirestoreId(@NonNull String firestoreId) { this.firestoreId = firestoreId; }

    @PropertyName("syncStatus")
    public String getSyncStatus() { return syncStatus; }
    @PropertyName("syncStatus")
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }

    @NonNull
    @PropertyName("acTypeFirestoreId")
    public String getAcTypeFirestoreId() { return acTypeFirestoreId; }
    @PropertyName("acTypeFirestoreId")
    public void setAcTypeFirestoreId(@NonNull String acTypeFirestoreId) { this.acTypeFirestoreId = acTypeFirestoreId; }

    @PropertyName("lastModified")
    public long getLastModified() { return lastModified; }
    @PropertyName("lastModified")
    public void setLastModified(long lastModified) { this.lastModified = lastModified; }

    @PropertyName("lastSyncDate")
    public long getLastSyncDate() { return lastSyncDate; }
    @PropertyName("lastSyncDate")
    public void setLastSyncDate(long lastSyncDate) { this.lastSyncDate = lastSyncDate; }

    @PropertyName("lastTransDate")
    public long getLastTransDate() { return lastTransDate; }
    @PropertyName("lastTransDate")
    public void setLastTransDate(long lastTransDate) { this.lastTransDate = lastTransDate; }


    @PropertyName("isDeleted")
    public boolean isDeleted() { return isDeleted; }
    @PropertyName("isDeleted")
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    @PropertyName("deletedBy")
    public String getDeletedBy() { return deletedBy; }
    @PropertyName("deletedBy")
    public void setDeletedBy(String deletedBy) { this.deletedBy = deletedBy; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Account account = (Account) o;
        return id == account.id &&
                Objects.equals(accountName, account.accountName) &&
                Objects.equals(acTypeFirestoreId, account.acTypeFirestoreId) &&
                Objects.equals(phoneNumber, account.phoneNumber) &&
                notifyWhatsApp == account.notifyWhatsApp &&
                notifySms == account.notifySms &&
                lastModified == account.lastModified;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, accountName, acTypeFirestoreId, phoneNumber, lastModified);
    }

    @Exclude
    public String getIconKey() { return iconKey; }
    @Exclude
    public void setIconKey(String iconKey) { this.iconKey = iconKey; }

    @Exclude
    public String getColorHex() { return colorHex; }
    @Exclude
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }

    @PropertyName("notifyWhatsApp")
    public boolean isNotifyWhatsApp() { return notifyWhatsApp; }
    @PropertyName("notifyWhatsApp")
    public void setNotifyWhatsApp(boolean notifyWhatsApp) { this.notifyWhatsApp = notifyWhatsApp; }

    @PropertyName("notifySms")
    public boolean isNotifySms() { return notifySms; }
    @PropertyName("notifySms")
    public void setNotifySms(boolean notifySms) { this.notifySms = notifySms; }
}