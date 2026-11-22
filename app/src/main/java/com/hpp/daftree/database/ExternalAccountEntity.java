package com.hpp.daftree.database;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.PropertyName;
@IgnoreExtraProperties
@Entity(tableName = "external_accounts")
public class ExternalAccountEntity {
    @NonNull
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "firestoreId")
    @PropertyName("firestoreId")
    public String firestoreId = "";

    @ColumnInfo(name = "ownerUID")
    @PropertyName("ownerUID")
    public String ownerUID;
    @ColumnInfo(name = "ownerPhone")
    @PropertyName("ownerPhone")
    public String ownerPhone;
    @ColumnInfo(name = "ownerName")
    @PropertyName("ownerName")
    public String ownerName;

    @ColumnInfo(name = "accountName")
    @PropertyName("accountName")
    public String accountName; // اسمي كما سجله الطرف الآخر

    @ColumnInfo(name = "phoneNumber")
    @PropertyName("phoneNumber")
    public String phoneNumber;

    @ColumnInfo(name = "lastModified")
    @PropertyName("lastModified")
    public long lastModified;

    // هام جداً: حقل يحدد هل تم الحذف في السيرفر أم لا
    @ColumnInfo(name = "isDeleted")
    @PropertyName("isDeleted")
    public boolean isDeleted = false;

    @ColumnInfo(name = "deletedBy")
    @PropertyName("deletedBy")
    public String deletedBy = "";
    public ExternalAccountEntity() {}
}