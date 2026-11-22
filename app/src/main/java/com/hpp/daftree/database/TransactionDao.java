package com.hpp.daftree.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.hpp.daftree.models.MonthlySummary;
import com.hpp.daftree.models.TransactionWithAccount;
import com.hpp.daftree.models.AccountTotalsByCurrency;

import java.util.List;

@Dao
public interface TransactionDao {

    @Insert
    void insert(Transaction transaction);
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(Transaction transaction);
    @Update
    void update(Transaction transaction);

    @Delete
    void delete(Transaction transaction);
          @Query("SELECT SUM(amount) FROM transactions " +
            "WHERE accountId = :accountId AND currencyId = :currency AND type = 1 AND isDeleted = 0  ")
    LiveData<Double> getDebitForAccount(int accountId, String currency);

    @Query("SELECT SUM(amount) FROM transactions WHERE accountId = :accountId AND currencyId = :currency AND type = -1  AND isDeleted = 0  ")
    LiveData<Double> getCreditForAccount(int accountId, String currency);
     @Query("SELECT * FROM transactions" +
            " WHERE accountId = :accountId AND currencyId = :currency  AND isDeleted = 0   AND details LIKE '%' || :searchQuery || '%' ORDER BY timestamp,lastModified ASC")
    LiveData<List<Transaction>> getTransactionsForAccount1(int accountId, String currency, String searchQuery);
    @Query("SELECT * FROM transactions" +
            " WHERE accountId = :accountId AND currencyId = :currency  AND isDeleted = 0   AND details LIKE '%' || :searchQuery || '%' ORDER BY timestamp ASC,id DESC")
    LiveData<List<Transaction>> getTransactionsForAccount(int accountId, String currency, String searchQuery);


    @Query("SELECT SUM(amount) FROM transactions " +
            "WHERE accountId = :accountId AND currencyId = :currency  AND isDeleted = 0  AND type = 1")
    Double getDebitForAccountBlocking(int accountId, String currency);

    @Query("SELECT SUM(amount) FROM transactions " +
            "WHERE accountId = :accountId AND currencyId = :currency  AND isDeleted = 0   AND type = -1")
    Double getCreditForAccountBlocking(int accountId, String currency);

    @Query("SELECT tx.*, acc.accountName as accountName" +
            " FROM transactions tx JOIN accounts acc ON tx.accountId = acc.id " +
            "WHERE acc.accountType = :accountType AND tx.timestamp  AND acc.isDeleted = 0   AND tx.isDeleted = 0  BETWEEN :startDate AND :endDate ORDER BY tx.timestamp ASC")
    List<TransactionWithAccount> getConsolidatedMovement(String accountType, long startDate, long endDate);

    @Query("SELECT tx.*, acc.accountName as accountName" +
            " FROM transactions tx JOIN accounts acc ON tx.accountId = acc.id " +
            "WHERE acc.acTypeFirestoreId = :acTypeFirestoreId AND tx.currencyId = :currency " +
            "AND acc.isDeleted = 0   AND tx.isDeleted = 0  " +
            "AND tx.timestamp  BETWEEN :startDate AND :endDate ORDER BY tx.timestamp ASC")
    List<TransactionWithAccount> getConsolidatedMovementAllCurrencies(String acTypeFirestoreId,int currency, long startDate, long endDate);

    @Query("SELECT strftime('%Y-%m', timestamp / 1000, 'unixepoch') as yearMonth, SUM(CASE WHEN type = 1 THEN amount ELSE 0 END) as totalDebit, SUM(CASE WHEN type = -1 THEN amount ELSE 0 END) as totalCredit " +
            "FROM transactions WHERE accountId = :accountId  AND isDeleted = 0  " +
            " GROUP BY yearMonth ORDER BY yearMonth ASC")
    List<MonthlySummary> getMonthlySummaryForAccount(int accountId);
    @Query("SELECT strftime('%Y-%m', timestamp / 1000, 'unixepoch') as yearMonth," +
            " SUM(CASE WHEN type = 1 THEN amount ELSE 0 END) as totalDebit, " +
            "SUM(CASE WHEN type = -1 THEN amount ELSE 0 END) as totalCredit " +
            "FROM transactions WHERE accountId = :accountId AND currencyId = :currency  AND isDeleted = 0  " +
            " GROUP BY yearMonth ORDER BY yearMonth ASC")
    List<MonthlySummary> getMonthlySummaryForAccountAllCurrencies(int accountId,int currency);
    @Query("SELECT strftime('%Y-%m', timestamp / 1000, 'unixepoch') as yearMonth," +
            " SUM(CASE WHEN type = 1 THEN amount ELSE 0 END) as totalDebit," +
            " SUM(CASE WHEN type = -1 THEN amount ELSE 0 END) as totalCredit" +
            " FROM transactions " +
            " WHERE currencyId = :currency " +
            "AND accountId IN (SELECT id FROM accounts WHERE acTypeFirestoreId = :acTypeFirestoreId AND isDeleted = 0 ) AND isDeleted = 0   " +
            "GROUP BY yearMonth ORDER BY yearMonth ASC")
    List<MonthlySummary> getMonthlySummaryByAccountTypeByCurrency(String acTypeFirestoreId,int currency);

    @Query("SELECT SUM(amount * type) FROM transactions WHERE accountId = :accountId AND isDeleted = 0   AND timestamp < :startDate")
    Double getBalanceBeforeDate(int accountId, long startDate);

  @Query("SELECT SUM(tx.amount * tx.type) FROM transactions tx JOIN accounts acc ON tx.accountId = acc.id WHERE acc.acTypeFirestoreId = :acTypeFirestoreId AND tx.timestamp < :startDate")
    Double getBalanceByAccountTypeBeforeDate(String acTypeFirestoreId, long startDate);
 @Query("SELECT CASE WHEN MAX(importID) < 100000 THEN 100000 ELSE MAX(importID) + 1 END FROM transactions")
  int getMaxImportId();
    @Query("SELECT * FROM transactions WHERE importID = :importId AND importId != 0 ORDER BY firestoreId ASC")
    List<Transaction> getTransactionsByImportId(int importId);
   @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId AND date(timestamp / 1000, 'unixepoch') = date(:date / 1000, 'unixepoch') AND amount = :amount AND type = :type AND currencyId = :currency AND isDeleted = 0 ")
    int findDuplicateTransaction(int accountId, long date, double amount, int type, int currency);
  @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId  AND currencyId = :currency AND isDeleted = 0 ")
  Integer checkIfFirst(int accountId, int currency);

    // **دالة جديدة: لجلب الإجماليات الشهرية لكل الحسابات مجمعة حسب العملة**
    @Query("SELECT strftime('%Y-%m', timestamp / 1000, 'unixepoch') as yearMonth, " +
            "SUM(CASE WHEN type = 1 THEN amount ELSE 0 END) as totalDebit, " +
            "SUM(CASE WHEN type = -1 THEN amount ELSE 0 END) as totalCredit " +
            "FROM transactions WHERE currencyId = :currency AND isDeleted = 0  " +
            "GROUP BY yearMonth ORDER BY yearMonth ASC")
    List<MonthlySummary> getGlobalMonthlySummary(int currency);

    //AddTransaction
    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND currencyId = :currencyId AND isDeleted = 0   ORDER BY timestamp ASC,id DESC")
    LiveData<List<Transaction>> getLiveTransactionsForAccount(int accountId, int currencyId);

    @Query("SELECT * FROM transactions WHERE syncStatus IN ('NEW', 'EDITED','DELETED')")
    List<Transaction> getUnsyncedTransactions();

    @Query("SELECT * FROM transactions WHERE syncStatus IN ('SYNCED') AND isDeleted = 1")
    List<Transaction> getDeletedTransactions();

    @Query("SELECT * FROM transactions WHERE id = :transactionId AND isDeleted = 0   LIMIT 1")
    Transaction getTransactionByIdBlocking(int transactionId);

    // تم التعديل: currencyId بدلاً من currency
    @Query("UPDATE transactions SET " +
            "amount = :amount, " +
            "details = :details, " +
            "currencyId = :currencyId, " +
            "currencyFirestoreId = :currencyFirestoreId, " +
            "type = :type, " +
            "timestamp = :timestamp, " +
            "syncStatus = CASE WHEN syncStatus = 'NEW' THEN 'NEW' ELSE 'EDITED' END, " +
            "lastModified = :lastModified " +
            "WHERE id = :id")
    void updateUserChanges(int id, double amount, String details, int currencyId, int type, long timestamp, long lastModified,String  currencyFirestoreId);

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    int deleteById(int transactionId);

    @Query("SELECT DISTINCT details FROM transactions WHERE accountId = :accountId AND details LIKE :query || '%'")
    LiveData<List<String>> getDetailSuggestions(int accountId, String query);

    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND isDeleted = 0  ")
    List<Transaction> getTransactionsForAccountBlocking(int accountId);

    @Query("SELECT * FROM transactions WHERE accountId = :accountId")
    List<Transaction> getImportTransactionsForAccountBlocking(int accountId);

    @Query("SELECT COUNT(*) FROM transactions WHERE syncStatus IN ('NEW', 'EDITED','DELETED')")
    int getUnsyncedCount();

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId  AND isDeleted = 0  ")
    int getTransactionCountForAccount(int accountId);

    @Query("UPDATE transactions SET syncStatus = :status, lastModified = :timestamp WHERE id = :id")
    int updateSyncStatus(int id, String status, long timestamp);

    //AccountDetails
    @Query("SELECT * FROM transactions" +
            " WHERE accountId = :accountId AND currencyId = :currencyId  AND isDeleted = 0   AND details LIKE '%' || :searchQuery || '%' ORDER BY timestamp ASC, id DESC")
    LiveData<List<Transaction>> getTransactionsForAccount(int accountId, int currencyId, String searchQuery);

    @Query("SELECT * FROM transactions WHERE firestoreId = :firestoreId  AND isDeleted = 0   LIMIT 1")
    Transaction getTransactionByFirestoreId(String firestoreId);
    @Query("SELECT firestoreId FROM transactions WHERE firestoreId = :firestoreId")
   String getFirestoreId(String firestoreId);

    @Query("SELECT DISTINCT c.name FROM transactions tx JOIN currencies c ON tx.currencyId = c.id " +
            "WHERE tx.accountId IN (SELECT id FROM accounts WHERE ownerUID = :ownerUID) AND tx.isDeleted = 0 ")
    LiveData<List<String>> getCurrenciesWithTransactions(String ownerUID);

    @Query("SELECT DISTINCT c.name FROM transactions tx JOIN currencies c ON tx.currencyId = c.id WHERE tx.accountId = :accountId AND tx.isDeleted = 0 ")
    LiveData<List<String>> getActiveCurrenciesForAccount(int accountId);
  @Query("SELECT * FROM transactions WHERE accountId = :accountId AND isDeleted = 0   AND currencyId = :currencyId ORDER BY timestamp ASC")
    List<Transaction> getTransactionsForAccountBlocking(int accountId, int currencyId);

     @Query("SELECT c.name as currency, " +
            "SUM(CASE WHEN tx.type = 1 THEN tx.amount ELSE 0 END) as totalDebit, " +
            "SUM(CASE WHEN tx.type = -1 THEN tx.amount ELSE 0 END) as totalCredit " +
            "FROM transactions tx JOIN currencies c ON tx.currencyId = c.id " +
            "WHERE tx.accountId = :accountId AND tx.isDeleted = 0  " +
            "GROUP BY tx.currencyId")
    List<AccountTotalsByCurrency> getAccountTotalsByCurrency(int accountId);
 @Query("UPDATE transactions SET syncStatus = :status, ownerUID = :ownerUID WHERE isDeleted = 0 ")
  void upgradeToOfficialUser(String ownerUID, String status);
  @Query("DELETE FROM transactions ")
  void deleteGuestData();
   @Query("SELECT COUNT(*) FROM transactions")
  int getTransactionsCount();

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId")
    int getAccountTransactionsCount(int accountId);

    @Query("SELECT * FROM transactions WHERE isDeleted = 0  ORDER BY id ASC") // تم تعديل الاستعلام
    List<Transaction> getAllTransactions();

    @Query("DELETE FROM transactions WHERE firestoreId = :firestoreId")
    void deleteByFirestoreId(String firestoreId);
    @Query("DELETE FROM transactions WHERE isDeleted = 1")
    void deleteGuestDeletedTransactions();
    @Query("SELECT * FROM transactions WHERE isDeleted = 1 AND deleteDate > 0 AND deleteDate <= :threshold")
    List<Transaction> getOldDeletedTransactions(long threshold);
    @Query("SELECT accountId, CAST(MAX(timestamp) AS INTEGER) AS latestTimestamp " +
            "FROM transactions WHERE isDeleted = 0 " +
            "GROUP BY accountId")
    LiveData<List<AccountLatestTransaction>> getLatestTransactionTimestamps();
}

