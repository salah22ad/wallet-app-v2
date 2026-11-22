package com.hpp.daftree.budget.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.PropertyName;

@IgnoreExtraProperties
@Entity(tableName = "budget_categories")
public class BudgetCategory {

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

    @ColumnInfo(name = "code")
    @PropertyName("code")
    public String code;

    @ColumnInfo(name = "name")
    @PropertyName("name")
    public String name;

    @ColumnInfo(name = "type")
    @PropertyName("type")
    public String type;

    @ColumnInfo(name = "iconKey")
    @PropertyName("iconKey")
    public String iconKey;

    @ColumnInfo(name = "colorHex")
    @PropertyName("colorHex")
    public String colorHex;

    @ColumnInfo(name = "isDefault")
    @PropertyName("isDefault")
    public boolean isDefault = false;

    @ColumnInfo(name = "sortOrder")
    @PropertyName("sortOrder")
    public int sortOrder = 0;

    @ColumnInfo(name = "isDeleted")
    @PropertyName("isDeleted")
    public boolean isDeleted = false;

    @ColumnInfo(name = "lastModified")
    @PropertyName("lastModified")
    public long lastModified = 0L;

    @ColumnInfo(name = "syncStatus")
    @PropertyName("syncStatus")
    public String syncStatus = "NEW";

    public BudgetCategory() {}

    @Ignore
    public BudgetCategory(String code, String name, String type,
                          String iconKey, String colorHex, boolean isDefault, int sortOrder) {
        this.code = code;
        this.name = name != null ? name : "";
        this.type = type;
        this.iconKey = iconKey;
        this.colorHex = colorHex;
        this.isDefault = isDefault;
        this.sortOrder = sortOrder;
        this.lastModified = System.currentTimeMillis();
        this.syncStatus = isDefault ? "SYNCED" : "NEW";
    }
}
