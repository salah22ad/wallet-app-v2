package com.hpp.daftree.viewmodels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.hpp.daftree.database.ExternalTransactionEntity;
import com.hpp.daftree.debt.ExternalDebtModels.ExternalTransaction;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.utils.DataMapper;

import java.util.ArrayList;
import java.util.List;

public class DebtDetailsViewModel extends AndroidViewModel {

    private final DaftreeRepository repository;

    // البيانات الخام من قاعدة البيانات (Entity)
    private final LiveData<List<ExternalTransactionEntity>> rawData;

    // البيانات المحولة (Model) والجاهزة للعرض (المفلترة)
    private final MediatorLiveData<List<ExternalTransaction>> displayedTransactions = new MediatorLiveData<>();

    // نحتفظ بنسخة كاملة للفلترة السريعة
    private List<ExternalTransaction> fullListCache = new ArrayList<>();

    public DebtDetailsViewModel(@NonNull Application application, String accountId, String currencyId) {
        super(application);
        repository = new DaftreeRepository(application);

        // جلب البيانات الخام من Room
        rawData = repository.getTransactionsForAccountAndCurrency(accountId, currencyId);

        // مراقبة البيانات الخام وتحديث القائمة المعروضة
        displayedTransactions.addSource(rawData, entities -> {
            // 1. تحويل Entity -> Model
            List<ExternalTransaction> mappedList = DataMapper.mapToExternalTransactions(entities);
            // 2. تحديث الكاش
            fullListCache = mappedList;
            // 3. عرض الكل افتراضياً (أو تطبيق الفلتر الحالي إذا أردت تعقيداً أكثر)
            displayedTransactions.setValue(fullListCache);
        });
    }

    public LiveData<List<ExternalTransaction>> getDisplayedTransactions() {
        return displayedTransactions;
    }

    // دالة الفلترة (تحدث القائمة المعروضة فقط)
    public void filterTransactions(long startTime, long endTime) {
        if (fullListCache == null || fullListCache.isEmpty()) {
            displayedTransactions.setValue(new ArrayList<>());
            return;
        }

        List<ExternalTransaction> filtered = new ArrayList<>();
        for (ExternalTransaction tx : fullListCache) {
            long txTime = 0;
            if (tx.timestamp instanceof Long) {
                txTime = (Long) tx.timestamp;
            }

            if (txTime >= startTime && txTime <= endTime) {
                filtered.add(tx);
            }
        }
        displayedTransactions.setValue(filtered);
    }

    // دالة لإلغاء الفلترة وعرض الكل
    public void showAllTransactions() {
        if (fullListCache != null) {
            displayedTransactions.setValue(new ArrayList<>(fullListCache));
        }
    }

    // إرجاع أول عملية لضبط التاريخ (اختياري)
    public long getFirstTransactionTimestamp() {
        if (fullListCache != null && !fullListCache.isEmpty()) {
            Object ts = fullListCache.get(0).timestamp;
            return (ts instanceof Long) ? (Long) ts : 0;
        }
        return 0;
    }
}