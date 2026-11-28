package com.hpp.daftree.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.hpp.daftree.models.AccountBalanceSummary;

import java.util.List;

@Dao
public interface AccountDao {

    @Insert
    long insert(Account account);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(Account account);
    @Update
    void update(Account account);
    @Delete
    void delete(Account account);

    @Query("SELECT acc.*, " +
            "COALESCE(SUM(CASE WHEN tx.currencyId = :currencyId THEN tx.amount * tx.type ELSE 0 END), 0) as balance, " +
            "COALESCE(SUM(CASE WHEN tx.currencyId = :currencyId THEN 1 ELSE 0 END), 0) as transactionCount, " +
            "MAX(tx.timestamp) as lastTransactionDate " +
            "FROM accounts acc " +
            "LEFT JOIN transactions tx ON acc.id = tx.accountId AND tx.isDeleted = 0  " +
            "WHERE acc.ownerUID = :ownerUID AND acc.isDeleted = 0  " +
            "AND (:filterAcTypeFirestoreId IS NULL OR acc.acTypeFirestoreId = :filterAcTypeFirestoreId) " +
            "AND acc.accountName LIKE :searchQuery " +
            "GROUP BY acc.id " +
            "HAVING transactionCount > 0")
    LiveData<List<AccountWithBalance>> getAccountsWithBalances(String ownerUID, int currencyId, String filterAcTypeFirestoreId, String searchQuery);
 @Query("SELECT acc.id as accountId, acc.accountName as accountName," +
            " SUM(tx.amount * tx.type) as balance, MAX(tx.timestamp) as lastTransactionDate" +
            " FROM accounts acc JOIN transactions tx ON acc.id = tx.accountId " +
            "WHERE acc.id = :accountId AND tx.currencyId = :currencyId AND acc.isDeleted = 0  AND tx.isDeleted = 0  " +
            "AND tx.timestamp  BETWEEN :startDate AND :endDate GROUP BY acc.id ORDER BY balance DESC")
    List<AccountBalanceSummary> getAccountBalances(int accountId, int currencyId, long startDate, long endDate);

 @Query("SELECT * FROM accounts WHERE ownerUID = :ownerUID AND isDeleted = 0   ORDER BY lastModified ASC")
    LiveData<List<Account>> getAllAccounts(String ownerUID);
    @Query("SELECT * FROM accounts WHERE id = :accountId LIMIT 1")
    LiveData<Account> getAccountById(int accountId);
    @Query("SELECT firestoreId FROM accounts WHERE id = :accountId LIMIT 1")
    String getAccountFireStoreById(int accountId);
    @Query("SELECT * FROM accounts WHERE syncStatus IN ('NEW', 'EDITED','DELETED')")
    List<Account> getUnsyncedAccounts();
    @Query("SELECT * FROM accounts WHERE id = :accountId LIMIT 1")
    Account getAccountByIdBlocking(int accountId);
    @Query("SELECT * FROM accounts WHERE accountName = :name LIMIT 1")
    Account getAccountByName(String name);
    @Query("SELECT * FROM accounts WHERE isDeleted = 0  ")
    List<Account> getAll();
    @Query("SELECT COUNT(*) FROM accounts WHERE syncStatus IN ('NEW', 'EDITED','DELETED')")
    int getUnsyncedCount();
    @Query("UPDATE accounts SET syncStatus = :status, lastModified = :timestamp WHERE id = :id")
    int updateSyncStatus(int id, String status, long timestamp);

    @Query("UPDATE accounts SET lastTransDate = :timestamp WHERE id = :accountId")
    void updateLastTransDate(int accountId, long timestamp);

    @Query("UPDATE accounts SET lastTransDate = COALESCE((SELECT MAX(tx.lastModified) FROM transactions tx WHERE tx.accountId = accounts.id AND tx.isDeleted = 0), accounts.lastModified, 0)")
    void recalculateAllAccountsLastTransDate();
    @Query("SELECT * FROM accounts WHERE firestoreId = :firestoreId LIMIT 1")
    Account getAccountByFirestoreId(String firestoreId);

    @Query("SELECT id FROM accounts WHERE firestoreId = :firestoreId LIMIT 1")
    int getAccountIDByFirestoreId(String firestoreId);
    @Query("SELECT * FROM accounts WHERE accountName = :name and isDeleted = 0 LIMIT 1")
    Account getAccountByNameBlocking(String name);

    @Query("DELETE FROM accounts WHERE firestoreId = :firestoreId")
    void deleteByFirestoreId(String firestoreId);
    @Query("SELECT EXISTS (SELECT 1 FROM transactions tx JOIN accounts acc ON tx.accountId = acc.id WHERE acc.accountType = :accountType LIMIT 1)")
    int hasAnyTransactionsForType(String accountType);
    @Query("SELECT phoneNumber FROM accounts WHERE firestoreId = :firestoreId")
    String getPhoneNumber(String firestoreId);
    @Query("UPDATE accounts SET accountType = :newName, syncStatus = 'EDITED' WHERE accountType = :oldName")
    void updateAccountTypeInAccounts(String oldName, String newName);
    @Query("SELECT COUNT(*) FROM transactions t " +
            "JOIN accounts a ON t.accountId = a.id " +
            "WHERE t.currencyId = :currencyId  AND t.isDeleted = 0  " +
            "AND ((:filter IS NULL OR a.accountType = :filter)  AND a.isDeleted = 0 )")
    int hasTransactionsForCurrencyAndFilter(int currencyId, String filter);

    @Query("SELECT acc.id as accountId, acc.accountName as accountName," +
            " SUM(tx.amount * tx.type) as balance, MAX(tx.timestamp) as lastTransactionDate" +
            " FROM accounts acc JOIN transactions tx ON acc.id = tx.accountId " +
            "WHERE acc.acTypeFirestoreId = :acTypeFirestoreId AND tx.currencyId = :currencyId AND acc.isDeleted = 0  AND tx.isDeleted = 0  " +
            "AND tx.timestamp BETWEEN :startDate AND :endDate GROUP BY acc.id ORDER BY balance DESC")
    List<AccountBalanceSummary> getAccountBalancesByType(String acTypeFirestoreId, int currencyId, long startDate, long endDate);
    @Query("SELECT acc.id as accountId, acc.accountName as accountName," +
            " SUM(tx.amount * tx.type) as balance, MAX(tx.timestamp) as lastTransactionDate" +
            " FROM accounts acc JOIN transactions tx ON acc.id = tx.accountId " +
            "WHERE acc.accountType = :acType AND tx.currencyId = :currencyId AND acc.isDeleted = 0  AND tx.isDeleted = 0  " +
            "AND tx.timestamp BETWEEN :startDate AND :endDate GROUP BY acc.id ORDER BY balance DESC")
    List<AccountBalanceSummary> getAccountBalancesBynameType(String acType, int currencyId, long startDate, long endDate);
 @Query("SELECT * FROM accounts  WHERE acTypeFirestoreId = :acTypeFirestoreId")
    List<Account> getAccountsByTypeBlocking(String acTypeFirestoreId);

    @Query("SELECT * FROM accounts WHERE ownerUID = :ownerUID")
    List<Account> getAccountsByOwnerDebug(String ownerUID);
    @Query("UPDATE accounts SET syncStatus = :status, ownerUID = :ownerUID WHERE isDeleted = 0  ")
    void upgradeToOfficialUser(String ownerUID, String status);

    @Query("DELETE FROM accounts ")
    void deleteGuestData();
    @Query("SELECT COUNT(*) FROM accounts")
    int getAccountsCount();
    /**
     * جلب جميع الحسابات من قاعدة البيانات.
     * يتم إرجاع البيانات كـ LiveData، مما يعني أن واجهة المستخدم سيتم إعلامها تلقائيًا
     * بأي تغييرات تحدث في جدول الحسابات.
     * @return قائمة LiveData تحتوي على جميع الحسابات.
     */
    @Query("SELECT * FROM accounts ORDER BY accountName ASC")
    LiveData<List<Account>> getAllAccount();

    @Query("SELECT * FROM accounts WHERE isDeleted = 0 ")
    List<Account> getAllAccounts();
    @Query("SELECT * FROM accounts WHERE syncStatus IN ('SYNCED') AND isDeleted = 1")
    List<Account> getDeletedAccounts();

    @Query("DELETE FROM accounts WHERE isDeleted = 1")
    void deleteGuestDeletedAccounts();
   @Query("SELECT * FROM accounts WHERE isDeleted = 1 AND deleteDate > 0 AND deleteDate <= :threshold")
   List<Account> getOldDeletedAccounts(long threshold);
}


