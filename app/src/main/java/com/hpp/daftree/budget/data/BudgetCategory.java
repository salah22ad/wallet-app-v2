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
    public String firestoreId = "";

    @ColumnInfo(name = "ownerUID")
    @PropertyName("ownerUID")
    public String ownerUID = "";

    @ColumnInfo(name = "code")
    @PropertyName("code")
    public String code = "";

    @ColumnInfo(name = "name")
    @PropertyName("name")
    public String name = "";

    @ColumnInfo(name = "type")
    @PropertyName("type")
    public String type = TYPE_EXPENSE;

    @ColumnInfo(name = "iconKey")
    @PropertyName("iconKey")
    public String iconKey = "other";

    @ColumnInfo(name = "colorHex")
    @PropertyName("colorHex")
    public String colorHex = "#06B6D4";

    @ColumnInfo(name = "isDefault")
    @PropertyName("isDefault")
    public boolean isDefault = false;

    @ColumnInfo(name = "sortOrder")
    @PropertyName("sortOrder")
    public int sortOrder = 0;

    @ColumnInfo(name = "isDeleted")
    @PropertyName("isDeleted")
    public boolean isDeleted = false;

    @ColumnInfo(name = "deleteDate")
    @PropertyName("deleteDate")
    public long deleteDate = 0L;

    @ColumnInfo(name = "deletedBy")
    @PropertyName("deletedBy")
    public String deletedBy = "";

    @ColumnInfo(name = "lastModified")
    @PropertyName("lastModified")
    public long lastModified = 0L;

    @ColumnInfo(name = "lastSyncDate")
    @PropertyName("lastSyncDate")
    public long lastSyncDate = 0L;

    @ColumnInfo(name = "syncStatus")
    @PropertyName("syncStatus")
    public String syncStatus = "NEW";

    public BudgetCategory() {}

    @Ignore
    public BudgetCategory(String code, String name, String type,
                          String iconKey, String colorHex, boolean isDefault, int sortOrder) {
        this.code = code != null ? code : "";
        this.name = name != null ? name : "";
        this.type = type != null ? type : TYPE_EXPENSE;
        this.iconKey = iconKey != null ? iconKey : "other";
        this.colorHex = colorHex != null ? colorHex : "#06B6D4";
        this.isDefault = isDefault;
        this.sortOrder = sortOrder;
        this.lastModified = System.currentTimeMillis();
        this.lastSyncDate = 0L;
        this.deleteDate = 0L;
        this.deletedBy = "";
        this.syncStatus = "NEW";
    }

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

    @PropertyName("code")
    public String getCode() { return code; }
    @PropertyName("code")
    public void setCode(String code) { this.code = code; }

    @PropertyName("name")
    public String getName() { return name; }
    @PropertyName("name")
    public void setName(String name) { this.name = name; }

    @PropertyName("type")
    public String getType() { return type; }
    @PropertyName("type")
    public void setType(String type) { this.type = type; }

    @PropertyName("iconKey")
    public String getIconKey() { return iconKey; }
    @PropertyName("iconKey")
    public void setIconKey(String iconKey) { this.iconKey = iconKey; }

    @PropertyName("colorHex")
    public String getColorHex() { return colorHex; }
    @PropertyName("colorHex")
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }

    @PropertyName("isDefault")
    public boolean isDefault() { return isDefault; }
    @PropertyName("isDefault")
    public void setDefault(boolean aDefault) { isDefault = aDefault; }

    @PropertyName("sortOrder")
    public int getSortOrder() { return sortOrder; }
    @PropertyName("sortOrder")
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

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

    @PropertyName("lastModified")
    public long getLastModified() { return lastModified; }
    @PropertyName("lastModified")
    public void setLastModified(long lastModified) { this.lastModified = lastModified; }

    @PropertyName("lastSyncDate")
    public long getLastSyncDate() { return lastSyncDate; }
    @PropertyName("lastSyncDate")
    public void setLastSyncDate(long lastSyncDate) { this.lastSyncDate = lastSyncDate; }

    @PropertyName("syncStatus")
    public String getSyncStatus() { return syncStatus; }
    @PropertyName("syncStatus")
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }
}
