package com.hpp.daftree.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface AccountTypeDao {
    @Insert
    void insert(AccountType accountType);
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(AccountType accountType);

    @Query("SELECT * FROM account_types WHERE isDeleted = 0 ORDER BY id ASC")
    LiveData<List<AccountType>> getAllAccountTypes();
    @Query("SELECT COUNT(*) FROM account_types")
    int getAccountTypeCount();
    // دوال المزامنة
    @Query("SELECT * FROM account_types WHERE syncStatus IN ('NEW', 'EDITED','DELETED')")
    List<AccountType> getUnsyncedAccountTypes();
    @Query("SELECT COUNT(*) FROM account_types WHERE syncStatus IN ('NEW', 'EDITED','DELETED')")
    int getUnsyncedCount();
    @Query("UPDATE account_types SET syncStatus = :status, firestoreId = :firestoreId, lastModified = :timestamp WHERE id = :id")
    void updateSyncStatus(int id, String firestoreId, String status, long timestamp);

    @Query("SELECT * FROM account_types WHERE firestoreId = :firestoreId LIMIT 1")
    AccountType getAccountTypeByFirestoreId(String firestoreId);
    @Query("SELECT * FROM account_types WHERE isDeleted = 0 ORDER BY id ASC ")
    List<AccountType> getAllAccountTypesBlockingReport(); // تغيير نوع الإرجاع إلى LiveData

    @Query("SELECT name FROM account_types WHERE isDeleted = 0 ORDER BY id ASC ")
    List<String> getAllAccountTypeNames();

    @Query("SELECT COUNT(*) FROM accounts WHERE accountType = :typeName AND isDeleted = 0")
    int getAccountCountForType(String typeName);

    @Update
    void update(AccountType accountType);

    @Query("SELECT * FROM account_types WHERE name = :name and isDeleted = 0 LIMIT 1")
    AccountType getAccountTypeByName(String name);
    @Query("SELECT firestoreId FROM account_types WHERE name = :name and isDeleted = 0 LIMIT 1")
     String getAccountTypeFirestoreIdByName(String name);
    @Query("SELECT * FROM account_types WHERE isDeleted = 0 ORDER BY id ASC ")
    List<AccountType> getAccountTypes();

    @Delete
    void delete(AccountType accountType);
    @Query("DELETE FROM account_types WHERE firestoreId = :firestoreId")
    void deleteByFirestoreId(String firestoreId);

  @Query("SELECT name FROM account_types WHERE firestoreId = :firestoreId LIMIT 1")
    String getAccountTypeNameByFirestoreId(String firestoreId);
    @Query("UPDATE account_types SET syncStatus = :status, ownerUID = :ownerUID WHERE isDeleted = 0")
    void upgradeToOfficialUser(String ownerUID, String status);
    @Query("DELETE FROM account_types ")
    void deleteGuestData();
    @Query("SELECT * FROM account_types WHERE syncStatus IN ('SYNCED') AND isDeleted = 1")
    List<AccountType> getDeletedAccountTypes();
    @Query("DELETE FROM account_types WHERE isDeleted = 1")
    void deleteGuestDeletedAccountTypes();
    @Query("SELECT * FROM account_types WHERE isDeleted = 1 AND deleteDate > 0 AND deleteDate <= :threshold")
    List<AccountType> getOldDeletedAccountTypes(long threshold);

}
