package com.hpp.daftree.database;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import com.google.firebase.firestore.IgnoreExtraProperties; // هام
import com.google.firebase.firestore.PropertyName;

@IgnoreExtraProperties // تجاهل الحقول القديمة مثل id, accountId, importID
@Entity(tableName = "external_transactions",
        foreignKeys = @ForeignKey(entity = ExternalAccountEntity.class,
                parentColumns = "firestoreId",
                childColumns = "accountFirestoreId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("accountFirestoreId")})
public class ExternalTransactionEntity {

    @NonNull
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "firestoreId")
    @PropertyName("firestoreId")
    public String firestoreId = "";

    @ColumnInfo(name = "accountFirestoreId")
    @PropertyName("accountFirestoreId")
    public String accountFirestoreId;

    @ColumnInfo(name = "currencyFirestoreId")
    @PropertyName("currencyFirestoreId")
    public String currencyFirestoreId;

    @ColumnInfo(name = "amount")
    @PropertyName("amount")
    public double amount;

    @ColumnInfo(name = "type")
    @PropertyName("type")
    public int type; // 1: مدين، -1: دائن

    @ColumnInfo(name = "details")
    @PropertyName("details")
    public String details;

    @ColumnInfo(name = "timestamp")
    @PropertyName("timestamp")
    public long timestamp; // سيتم تعبئتها يدوياً في SyncManager

    @ColumnInfo(name = "lastModified")
    @PropertyName("lastModified")
    public long lastModified;

    @ColumnInfo(name = "ownerUID")
    @PropertyName("ownerUID")
    public String ownerUID;

    @ColumnInfo(name = "currencyName")
    public String currencyName;

    @ColumnInfo(name = "isDeleted")
    @PropertyName("isDeleted")
    public boolean isDeleted = false;

    @ColumnInfo(name = "deletedBy")
    @PropertyName("deletedBy")
    public String deletedBy = "";

    public ExternalTransactionEntity() {}
}