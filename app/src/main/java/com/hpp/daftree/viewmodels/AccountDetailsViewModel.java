package com.hpp.daftree.viewmodels;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.hpp.daftree.MyApplication;
import com.hpp.daftree.models.TransactionItem;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.Transaction;
import com.hpp.daftree.database.User;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.models.TransactionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AccountDetailsViewModel extends AndroidViewModel {

    private final DaftreeRepository mRepository;
    private final int accountId;
    private final LiveData<List<String>> activeCurrencies;
  private final MutableLiveData<String> transactionSearchQuery = new MutableLiveData<>(""); // لحالة البحث
    private final MutableLiveData<String> detailsQuery = new MutableLiveData<>(); // لحالة اقتراحات التفاصيل

    // --- LiveData النهائية التي تراقبها الواجهة ---
    private final LiveData<Account> accountDetails;
    private final LiveData<List<String>> detailSuggestions;
    private final MediatorLiveData<List<TransactionItem>> transactionItems = new MediatorLiveData<>();
    private LiveData<List<TransactionItem>> currentTransactionSource = null; // لتتبع المصدر الحالي

    // LiveData لإجماليات العملات (تبقى كما هي)
    private final LiveData<Double> debitLocal, creditLocal;
    private final LiveData<Double> debitUsd, creditUsd;
    private final LiveData<Double> debitSar, creditSar;
    private final MutableLiveData<String> currentCurrencyName = new MutableLiveData<>();

    // -- بداية التغيير: إضافة قائمة وخريطة العملات --
    private final LiveData<List<Currency>> allCurrencies;
    private final Map<String, Integer> currencyNameToIdMap = new HashMap<>();

    private final LiveData<List<Account>> allAccounts;
    public AccountDetailsViewModel(@NonNull Application application, int accountId) {
        super(application);
        this.accountId = accountId;
        mRepository = new DaftreeRepository(application);
        allAccounts = mRepository.getAllAccounts();
        // تهيئة بيانات الحساب مرة واحدة
        accountDetails = mRepository.getAccountById(accountId);
        activeCurrencies = mRepository.getActiveCurrenciesForAccount(accountId);

        // إعداد مراقب اقتراحات التفاصيل (يعتمد على نص البحث فقط)


        // جلب الإجماليات لكل عملة بشكل مسبق
        debitLocal = mRepository.getDebitForAccount(accountId, MyApplication.defaultCurrencyName);
        creditLocal = mRepository.getCreditForAccount(accountId, MyApplication.defaultCurrencyName);
        debitUsd = mRepository.getDebitForAccount(accountId, "دولار");
        creditUsd = mRepository.getCreditForAccount(accountId, "دولار");
        debitSar = mRepository.getDebitForAccount(accountId, "سعودي");
        creditSar = mRepository.getCreditForAccount(accountId, "سعودي");
       
        allCurrencies = mRepository.getAllCurrencies();
        transactionItems.addSource(allCurrencies, currencies -> {
            currencyNameToIdMap.clear();
            if (currencies != null) {
                for (Currency c : currencies) {
                    currencyNameToIdMap.put(c.name, c.id);
                }
            }
            updateTransactionListSource();
        });
        transactionItems.addSource(currentCurrencyName, s -> updateTransactionListSource());
        transactionItems.addSource(transactionSearchQuery, s -> updateTransactionListSource());
        detailSuggestions = Transformations.switchMap(detailsQuery, query -> {
            if (query == null || query.isEmpty() || query.contains(" ")) {
                return new MutableLiveData<>(new ArrayList<>());
            }
            return mRepository.getDetailSuggestions(this.accountId, query);
        });
    }
    public LiveData<List<Account>> getAllAccounts() { return allAccounts; }
    private void updateTransactionListSource() {
        if (currentTransactionSource != null) {
            transactionItems.removeSource(currentTransactionSource);
        }

        String currencyName = currentCurrencyName.getValue();
        String query = transactionSearchQuery.getValue();
        Integer currencyId = currencyNameToIdMap.get(currencyName);
        if (currencyId == null) return;

        LiveData<List<Transaction>> rawTransactions = mRepository.getTransactionsForAccount(accountId, currencyId, query != null ? query : "");
        currentTransactionSource = Transformations.map(rawTransactions, transactions -> {
            // إزالة التكرارات حسب firestoreId
            List<Transaction> distinctTransactions = TransactionUtils.distinctByFirestoreId(transactions);

            List<TransactionItem> itemsWithBalance = new ArrayList<>();
            double runningBalance = 0.0;
            for (Transaction tx : distinctTransactions) {
                runningBalance += (tx.getAmount() * tx.getType());
                itemsWithBalance.add(new TransactionItem(tx, runningBalance));
            }
            Collections.reverse(itemsWithBalance);
            return itemsWithBalance;
        });
        transactionItems.addSource(currentTransactionSource, items -> transactionItems.setValue(items));
    }

    public LiveData<Account> getAccountDetails() { return accountDetails; }
    public LiveData<List<TransactionItem>> getTransactionItems() { return transactionItems; }
    public LiveData<List<String>> getDetailSuggestions() { return detailSuggestions; }
    public LiveData<Double> getDebitUsd() { return debitUsd; }
    public LiveData<Double> getCreditUsd() { return creditUsd; }
    public LiveData<Double> getDebitSar() { return debitSar; }
    public LiveData<Double> getCreditSar() { return creditSar; }

    public void setCurrency(String currency) {
        currentCurrencyName.setValue(currency);
    }
    public void setTransactionSearchQuery(String query) {
        transactionSearchQuery.setValue(query);
    }
    public void setDetailsQuery(String query) {
        if (query == null && detailsQuery.getValue() == null) return;
        if (query != null && query.equals(detailsQuery.getValue())) return;
        detailsQuery.setValue(query);
    }

    // دوال الحفظ والتعديل والحذف
    public void addTransaction(Transaction transaction) { mRepository.insertTransaction(transaction); }
    public void updateTransaction(Transaction transaction) { mRepository.updateTransaction(transaction); }
    public void deleteTransaction(Transaction transaction) { mRepository.deleteTransaction(transaction); }
    public void updateAccount(Account account) { mRepository.updateAccount(account); }

    public LiveData<List<String>> getActiveCurrencies() {
        return activeCurrencies;
    }
    public LiveData<List<TransactionItem>> getSelectedTransactionItems() { return transactionItems; }
    public LiveData<User> getUserProfile() { return mRepository.getUserProfile(); }
    public void refreshTransactions() {
        String current = currentCurrencyName.getValue();
        if (current == null) return;
        // إعادة تعيين العملة إلى قيمة فارغة ثم إعادتها
        currentCurrencyName.setValue("");
        currentCurrencyName.setValue(current);
    }
}