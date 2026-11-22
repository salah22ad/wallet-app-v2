
package com.hpp.daftree.database;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Upsert;

import java.util.List;

@Dao
public interface CurrencyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Currency currency); // تم التعديل من insertAll إلى insert

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(Currency currency);

    @Update
    void update(Currency currency);

    @Query("SELECT * FROM currencies WHERE isDeleted = 0  ORDER BY id ASC")
        // تم تعديل الاستعلام
    LiveData<List<Currency>> getAllCurrencies();


    @Query("SELECT * FROM currencies WHERE isDeleted = 0   ORDER BY id ASC")
        // تم تعديل الاستعلام
    List<Currency> getAllCurrency();

    @Query("SELECT * FROM currencies WHERE isDeleted = 0  ORDER BY id ASC")
        // تم تعديل الاستعلام
    List<Currency> getAllCurrenciesBlocking();

    @Query("SELECT * FROM currencies WHERE syncStatus IN ('NEW', 'EDITED','DELETED')")
    List<Currency> getUnsyncedCurrencies();

    @Query("UPDATE currencies SET syncStatus = :status, firestoreId = :firestoreId, lastModified = :timestamp WHERE id = :id")
    void updateSyncStatus(int id, String firestoreId, String status, long timestamp);

    @Query("SELECT COUNT(*) FROM currencies WHERE syncStatus IN ('NEW', 'EDITED','DELETED')")
    int getUnsyncedCount();

    @Query("SELECT * FROM currencies WHERE firestoreId = :firestoreId LIMIT 1")
    Currency getCurrencyByFirestoreId(String firestoreId);

    @Query("DELETE FROM currencies WHERE firestoreId = :firestoreId")
    void deleteByFirestoreId(String firestoreId);

    @Query("SELECT id FROM currencies WHERE name = :name ORDER BY id LIMIT 1 ")
    int checkCurrencyByName(String name);

    @Query("SELECT COUNT(*) FROM transactions WHERE currencyId = :currencyId AND isDeleted = 0 ")
    int getTransactionCountForCurrency(int currencyId);


    @Query("SELECT * FROM currencies WHERE name = :name LIMIT 1")
    Currency getCurrencyByName(String name);

    @Query("SELECT COUNT(*) FROM currencies")
    int getCurrencyCount();

    @Query("SELECT * FROM currencies WHERE isDeleted = 0 ")
    List<Currency> getDeletedCurrencies();

    @Delete
    void delete(Currency currency);

    @Query("SELECT name FROM currencies WHERE id > 0 and isDeleted = 0 ORDER BY id ASC LIMIT 1")
    String getFirstCurrencyById();

    @Query("SELECT id FROM currencies WHERE id > 0 ORDER BY id ASC LIMIT 1")
    int getFirstIdCurrency();

    @Query("SELECT firestoreId FROM currencies WHERE id > 0 ORDER BY id ASC LIMIT 1")
    String getFirstFirestoreIdCurrency();

    @Query("UPDATE currencies SET syncStatus = :status, isDefault = :isDefault")
    void updateAllSyncStatus(String status, boolean isDefault);

    @Query("SELECT name FROM currencies WHERE id = :currencyId")
    String getCurrencyNameById(int currencyId);

    @Query("SELECT firestoreId FROM currencies WHERE id = :currencyId LIMIT 1")
    String getCurrencyFirestoreId(int currencyId);

    @Query("UPDATE currencies SET syncStatus = :status, ownerUID = :ownerUID WHERE isDeleted = 0 ")
    void upgradeToOfficialUser(String ownerUID, String status);

    @Query("DELETE FROM currencies ")
    void deleteGuestData();

    @Query("SELECT * FROM currencies WHERE syncStatus IN ('SYNCED') AND isDeleted = 1")
    List<Currency> getDeletedCurrency();

    @Query("DELETE FROM currencies WHERE isDeleted = 1")
    void deleteGuestDeletedCurrencies();

    @Query("SELECT * FROM currencies WHERE isDeleted = 1 AND deleteDate > 0 AND deleteDate <= :threshold")
    List<Currency> getOldDeletedCurrencies(long threshold);
}