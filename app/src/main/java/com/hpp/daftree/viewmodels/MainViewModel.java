package com.hpp.daftree.viewmodels;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.AccountLatestTransaction;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.AccountWithBalance;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.syncmanagers.SyncPreferences;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainViewModel extends AndroidViewModel {

    private final DaftreeRepository mRepository;
    private final MutableLiveData<String> currentCurrencyName;
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");
    private final MutableLiveData<String> accountTypeFirestoreIdFilter = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> refreshTrigger = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> forceRefresh = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> sortOrder = new MutableLiveData<>();

    private final MediatorLiveData<List<AccountWithBalance>> accounts = new MediatorLiveData<>();
    private LiveData<List<AccountWithBalance>> currentSource = null;

    private final LiveData<List<Currency>> allCurrencies;
    private final Map<String, Integer> currencyNameToIdMap = new HashMap<>();
    private final LiveData<List<String>> currenciesWithTransactions;
    private final LiveData<List<Account>> allAccounts;
    private AtomicBoolean isInitialLoad = new AtomicBoolean(true);
    public MainViewModel(@NonNull Application application) {
        super(application);
        mRepository = new DaftreeRepository(application);
        SyncPreferences syncPreferences = new SyncPreferences(application);
        allAccounts = mRepository.getAllAccounts();
        currentCurrencyName = new MutableLiveData<>(syncPreferences.getLocalCurrency(SyncPreferences.DEFAULT_CURRENCY));
        allCurrencies = mRepository.getAllCurrencies();
        currenciesWithTransactions = mRepository.getCurrenciesWithTransactions();
        android.content.SharedPreferences sharedPreferences = application.getSharedPreferences("AppPrefs", android.content.Context.MODE_PRIVATE);
        int savedSortOrder = sharedPreferences.getInt("KEY_SORT_ORDER", 4);
        this.sortOrder.setValue(savedSortOrder);
                setupDataSources();
        updateDataSource();
    }

    private void setupDataSources() {
        // إزالة جميع المصادر القديمة أولاً
        accounts.removeSource(refreshTrigger);
        accounts.removeSource(allCurrencies);
        accounts.removeSource(currentCurrencyName);
        accounts.removeSource(searchQuery);
        accounts.removeSource(accountTypeFirestoreIdFilter);
        accounts.removeSource(forceRefresh);

        // إضافة المصادر مجدداً
        accounts.addSource(refreshTrigger, s -> updateDataSource());
        accounts.addSource(forceRefresh, s -> forceUpdateDataSource());

        accounts.addSource(allCurrencies, currencies -> {
            currencyNameToIdMap.clear();
            if (currencies != null) {
                for (Currency c : currencies) {
                    currencyNameToIdMap.put(c.name, c.id);
                }
            }
            updateDataSource();
        });
        accounts.addSource(currentCurrencyName, s -> updateDataSource());
        accounts.addSource(searchQuery, s -> updateDataSource());
        accounts.addSource(accountTypeFirestoreIdFilter, s -> updateDataSource());
    }

    private void updateDataSource() {
        if (currentSource != null) {
            accounts.removeSource(currentSource);
        }

        String currencyName = currentCurrencyName.getValue();
        String search = searchQuery.getValue();
        String filterId = accountTypeFirestoreIdFilter.getValue();

        Integer currencyId = currencyNameToIdMap.get(currencyName);
        if (currencyId == null) {
            currencyId = 1;
        }

        currentSource = mRepository.getAccountsWithBalances(
                currencyId,
                filterId,
                search != null ? search : ""
        );

//        accounts.addSource(currentSource, accounts::setValue);
        accounts.addSource(currentSource, list -> accounts.setValue(applySorting(list)));
        if (isInitialLoad.get()) {
            isInitialLoad.set(false);
        }
    }

    /**
     * ✅ تحديث قسري للبيانات - يستخدم بعد المزامنة
     */
    private void forceUpdateDataSource() {
        Log.d("MainViewModel", "بدء التحديث القسري للبيانات");

        if (currentSource != null) {
            accounts.removeSource(currentSource);
        }

        String currencyName = currentCurrencyName.getValue();
        String search = searchQuery.getValue();
        String filterId = accountTypeFirestoreIdFilter.getValue();

        Integer currencyId = currencyNameToIdMap.get(currencyName);
        if (currencyId == null) {
            currencyId = 1;
        }
        // إنشاء مصدر جديد تماماً للبيانات
        currentSource = mRepository.getAccountsWithBalancesForceRefresh(
                currencyId,
                filterId,
                search != null ? search : ""
        );

//        accounts.addSource(currentSource, data -> {
//            Log.d("MainViewModel", "تم استلام البيانات بعد التحديث القسري، عدد الحسابات: " + (data != null ? data.size() : 0));
//            accounts.setValue(data);
//        });
        accounts.addSource(currentSource, list -> accounts.setValue(applySorting(list)));
    }

    public void refreshData() {
        Log.d("MainViewModel", "تنفيذ refreshData()");
        refreshTrigger.setValue(!Boolean.TRUE.equals(refreshTrigger.getValue()));
    }

    /**
     * ✅ تحديث قسري بعد المزامنة
     */
    public void forceRefreshAfterSync() {
        Log.d("MainViewModel", "تنفيذ forceRefreshAfterSync()");
        forceRefresh.setValue(!Boolean.TRUE.equals(forceRefresh.getValue()));
    }

    // --- Getters and Setters for UI ---
    public LiveData<List<AccountWithBalance>> getAccountsForType(String acTypeFirestoreId) {
        MediatorLiveData<List<AccountWithBalance>> pageAccounts = new MediatorLiveData<>();
        final LiveData<List<AccountWithBalance>>[] sourceHolder = new LiveData[]{null};

        Runnable reload = () -> {
            if (sourceHolder[0] != null) {
                pageAccounts.removeSource(sourceHolder[0]);
            }
            String currencyName = currentCurrencyName.getValue();
            String search = searchQuery.getValue();
            Integer currencyId = currencyNameToIdMap.get(currencyName);
            if (currencyId == null) currencyId = 1;

            sourceHolder[0] = mRepository.getAccountsWithBalances(
                    currencyId,
                    acTypeFirestoreId,
                    search != null ? search : ""
            );
            pageAccounts.addSource(sourceHolder[0], list -> pageAccounts.setValue(applySorting(list)));
        };

        pageAccounts.addSource(allCurrencies, c -> reload.run());
        pageAccounts.addSource(currentCurrencyName, c -> reload.run());
        pageAccounts.addSource(searchQuery, s -> reload.run());
        pageAccounts.addSource(refreshTrigger, r -> reload.run());
        pageAccounts.addSource(forceRefresh, f -> reload.run());
        pageAccounts.addSource(sortOrder, o -> {
            List<AccountWithBalance> cur = pageAccounts.getValue();
            if (cur != null) pageAccounts.setValue(applySorting(cur));
        });

        return pageAccounts;
    }

    public LiveData<List<AccountWithBalance>> getAccounts() {
        Log.d("MainViewModel", "استدعاء getAccounts()");
        return accounts;
    }


    public LiveData<String> getCurrency() { return currentCurrencyName; }

    public void setCurrency(String currencyName) {
        Log.d("MainViewModel", "تعيين العملة: " + currencyName);
        this.currentCurrencyName.setValue(currencyName);
    }

    public LiveData<List<String>> getCurrenciesWithTransactions() { return currenciesWithTransactions; }

    public void setFilter(String firestoreId) {
        Log.d("MainViewModel", "تعيين الفلتر: " + firestoreId);
        if (!Objects.equals(this.accountTypeFirestoreIdFilter.getValue(), firestoreId)) {
            this.accountTypeFirestoreIdFilter.setValue(firestoreId);
        }
    }
    public LiveData<String> getFilter() {
        return accountTypeFirestoreIdFilter;
    }
    public LiveData<List<Account>> getAllAccounts() { return allAccounts; }
    public void setSearchQuery(String query) {
        this.searchQuery.setValue(query);
    }

    public void deleteAccount(Account account) {
        mRepository.deleteAccount(account);
        refreshData(); // تحديث البيانات بعد الحذف
    }

    public void updateAccount(Account account) {
        mRepository.updateAccount(account);
       // refreshData(); // تحديث البيانات بعد التعديل
    }
    public void createAccount(Account account) {
        mRepository.insertNeAccount(account);
    }

    // متغير لتخزين وحفظ حالة الترتيب الحالية

    public LiveData<Integer> getSortOrder() {
        return sortOrder;
    }

    // دالة تحديث الترتيب وإعادة فرز القائمة الحالية فوراً
    public void setSortOrder(int order) {
        if (this.sortOrder.getValue() == null || this.sortOrder.getValue() != order) {
            this.sortOrder.setValue(order);
            List<AccountWithBalance> currentList = accounts.getValue();
            if (currentList != null) {
                accounts.setValue(applySorting(currentList));
            }
        }
    }
    // دالة الفرز البرمجية الاحترافية بناءً على خيار المستخدم وحقل lastModified
    public List<AccountWithBalance> applySorting(List<AccountWithBalance> originalList) {
        if (originalList == null || originalList.isEmpty()) {
            return originalList;
        }

        List<AccountWithBalance> sortedList = new java.util.ArrayList<>(originalList);
        int order = sortOrder.getValue() != null ? sortOrder.getValue() : 0;

        switch (order) {
            case 2: // الاسم أ ~ ي
                sortedList.sort((a, b) -> {
                    String nameA = a.account.getAccountName() != null ? a.account.getAccountName() : "";
                    String nameB = b.account.getAccountName() != null ? b.account.getAccountName() : "";
                    return nameA.compareToIgnoreCase(nameB);
                });
                break;
            case 3: // الاسم ي ~ أ
                sortedList.sort((a, b) -> {
                    String nameA = a.account.getAccountName() != null ? a.account.getAccountName() : "";
                    String nameB = b.account.getAccountName() != null ? b.account.getAccountName() : "";
                    return nameB.compareToIgnoreCase(nameA);
                });
                break;
            case 1: // التاريخ الأحدث أولاً (تنازلي بناءً على lastModified)
                sortedList.sort((a, b) -> Long.compare(b.account.getLastModified(), a.account.getLastModified()));
                break;
            case 0: // التاريخ الأقدم أولاً (تصاعدي بناءً على lastModified)
                sortedList.sort((a, b) -> Long.compare(a.account.getLastModified(), b.account.getLastModified()));
                break;
            case 4: // الأحدث عمليات أولاً (مباشرة من جدول الحسابات)
                sortedList.sort((a, b) -> {
                    long timeA = a.account.getLastTransDate() > 0 ? a.account.getLastTransDate() : a.account.getLastModified();
                    long timeB = b.account.getLastTransDate() > 0 ? b.account.getLastTransDate() : b.account.getLastModified();
                    return Long.compare(timeB, timeA);
                });
                break;
        }

        return sortedList;
    }
    // دالة الفرز البرمجية الاحترافية بناءً على خيار المستخدم وحقل lastModified
}