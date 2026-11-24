package com.hpp.daftree.budget.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.hpp.daftree.budget.data.BudgetCategory;
import com.hpp.daftree.budget.data.BudgetEntryWithCategory;
import com.hpp.daftree.budget.data.CategoryExpenseTotal;
import com.hpp.daftree.budget.data.MonthSummaryStats;
import com.hpp.daftree.budget.data.MonthlyTotals;
import com.hpp.daftree.budget.repository.BudgetRepository;
import com.hpp.daftree.budget.util.BudgetUtils;

import java.util.List;

public class BudgetViewModel extends AndroidViewModel {

    public static final String FILTER_ALL = "ALL";
    public static final String FILTER_EXPENSE = "EXPENSE";
    public static final String FILTER_INCOME = "INCOME";

    private final BudgetRepository repository;

    private final MutableLiveData<String> selectedYearMonth = new MutableLiveData<>(BudgetUtils.getCurrentYearMonth());
    private final MutableLiveData<Integer> selectedCurrencyId = new MutableLiveData<>(1);
    private final MutableLiveData<String> selectedCurrencySymbol = new MutableLiveData<>("");
    private final MutableLiveData<String> selectedCurrencyName = new MutableLiveData<>("");
    private final MutableLiveData<String> selectedCurrencyFirestoreId = new MutableLiveData<>("");
    private final MutableLiveData<String> activeFilter = new MutableLiveData<>(FILTER_ALL);

    private final MediatorLiveData<List<BudgetEntryWithCategory>> monthEntries = new MediatorLiveData<>();
    private final MediatorLiveData<MonthSummaryStats> monthSummary = new MediatorLiveData<>();
    private final MediatorLiveData<List<CategoryExpenseTotal>> expenseDistribution = new MediatorLiveData<>();
    private final MediatorLiveData<List<MonthlyTotals>> monthlyComparison = new MediatorLiveData<>();
    private final MediatorLiveData<Double> effectiveBudgetLimit = new MediatorLiveData<>();

    private LiveData<List<BudgetEntryWithCategory>> currentEntriesSource;
    private LiveData<MonthSummaryStats> currentSummarySource;
    private LiveData<List<CategoryExpenseTotal>> currentDistSource;
    private LiveData<List<MonthlyTotals>> currentComparisonSource;
    private LiveData<Double> currentLimitSource;

    public BudgetViewModel(@NonNull Application application) {
        super(application);
        this.repository = new BudgetRepository(application);

        monthEntries.addSource(selectedYearMonth, ym -> reloadSources());
        monthEntries.addSource(selectedCurrencyId, cid -> reloadSources());
        reloadSources();
    }

    public void forceReload() {
        reloadSources();
    }

    private void reloadSources() {
        String ym = selectedYearMonth.getValue();
        Integer cid = selectedCurrencyId.getValue();
        if (ym == null || cid == null || cid <= 0) return;

        if (currentEntriesSource != null) monthEntries.removeSource(currentEntriesSource);
        currentEntriesSource = repository.getEntriesForMonth(ym, cid);
        monthEntries.addSource(currentEntriesSource, monthEntries::setValue);

        if (currentSummarySource != null) monthSummary.removeSource(currentSummarySource);
        currentSummarySource = repository.getMonthSummary(ym, cid);
        monthSummary.addSource(currentSummarySource, monthSummary::setValue);

        if (currentDistSource != null) expenseDistribution.removeSource(currentDistSource);
        currentDistSource = repository.getExpenseDistributionForMonth(ym, cid);
        expenseDistribution.addSource(currentDistSource, expenseDistribution::setValue);

        if (currentComparisonSource != null) monthlyComparison.removeSource(currentComparisonSource);
        currentComparisonSource = repository.getMonthlyComparison(ym, cid);
        monthlyComparison.addSource(currentComparisonSource, monthlyComparison::setValue);

        if (currentLimitSource != null) effectiveBudgetLimit.removeSource(currentLimitSource);
        currentLimitSource = repository.getEffectiveLimit(ym, cid);
        effectiveBudgetLimit.addSource(currentLimitSource, effectiveBudgetLimit::setValue);
    }

    public LiveData<String> getSelectedYearMonth() { return selectedYearMonth; }
    public LiveData<Integer> getSelectedCurrencyId() { return selectedCurrencyId; }
    public LiveData<String> getSelectedCurrencySymbol() { return selectedCurrencySymbol; }
    public LiveData<String> getSelectedCurrencyName() { return selectedCurrencyName; }
    public LiveData<String> getActiveFilter() { return activeFilter; }

    public LiveData<List<BudgetEntryWithCategory>> getMonthEntries() { return monthEntries; }
    public LiveData<MonthSummaryStats> getMonthSummary() { return monthSummary; }
    public LiveData<List<CategoryExpenseTotal>> getExpenseDistribution() { return expenseDistribution; }
    public LiveData<List<MonthlyTotals>> getMonthlyComparison() { return monthlyComparison; }
    public LiveData<Double> getEffectiveBudgetLimit() { return effectiveBudgetLimit; }

    public LiveData<List<BudgetCategory>> getCategoriesByType(String type) {
        return repository.getCategoriesByType(type);
    }

    public String getCurrentYearMonthValue() {
        String v = selectedYearMonth.getValue();
        return v != null ? v : BudgetUtils.getCurrentYearMonth();
    }

    public int getCurrentCurrencyIdValue() {
        Integer v = selectedCurrencyId.getValue();
        return v != null ? v : 1;
    }

    public String getCurrentCurrencySymbolValue() {
        String v = selectedCurrencySymbol.getValue();
        return v != null ? v : "";
    }

    public String getCurrentCurrencyNameValue() {
        String v = selectedCurrencyName.getValue();
        return v != null ? v : "";
    }

    public void shiftMonth(int deltaMonths) {
        String target = BudgetUtils.shiftYearMonth(getCurrentYearMonthValue(), deltaMonths);
        String maxMonth = BudgetUtils.getCurrentYearMonth();
        if (deltaMonths > 0 && target.compareTo(maxMonth) > 0) {
            return;
        }
        selectedYearMonth.setValue(target);
    }

    public void setSelectedYearMonth(String yearMonth) {
        if (yearMonth == null) return;
        String maxMonth = BudgetUtils.getCurrentYearMonth();
        String clamped = yearMonth.compareTo(maxMonth) > 0 ? maxMonth : yearMonth;
        if (!clamped.equals(selectedYearMonth.getValue())) {
            selectedYearMonth.setValue(clamped);
        }
    }

    public void setSelectedCurrency(int currencyId, String name, String symbol, String firestoreId) {
        String displaySymbol = (symbol != null && !symbol.trim().isEmpty()) ? symbol.trim()
                : (name != null ? name.trim() : "");
        selectedCurrencyName.setValue(name != null ? name : displaySymbol);
        selectedCurrencySymbol.setValue(displaySymbol);
        selectedCurrencyFirestoreId.setValue(firestoreId != null ? firestoreId : "");
        Integer current = selectedCurrencyId.getValue();
        if (current == null || current != currencyId) {
            selectedCurrencyId.setValue(currencyId);
        }
    }

    public void setActiveFilter(String filter) {
        activeFilter.setValue(filter);
    }

    public void addEntry(int categoryId, String type, double amount, String note, long entryDate, Runnable onDone) {
        repository.addEntry(
                categoryId,
                getCurrentCurrencyIdValue(),
                selectedCurrencyFirestoreId.getValue(),
                type,
                amount,
                note,
                entryDate,
                onDone
        );
    }

    public void updateEntry(int entryId, int categoryId, double amount, String note, long entryDate, Runnable onDone) {
        repository.updateEntry(entryId, categoryId, amount, note, entryDate, onDone);
    }

    public void deleteEntry(int entryId, Runnable onDone) {
        repository.deleteEntry(entryId, onDone);
    }

    public void saveMonthlyLimit(double limitAmount, Runnable onDone) {
        repository.saveMonthlyLimit(
                getCurrentYearMonthValue(),
                getCurrentCurrencyIdValue(),
                selectedCurrencyFirestoreId.getValue(),
                limitAmount,
                onDone
        );
    }
}
