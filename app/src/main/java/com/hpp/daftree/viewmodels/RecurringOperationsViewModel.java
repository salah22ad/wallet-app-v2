package com.hpp.daftree.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.hpp.daftree.database.RecurringOperation;
import com.hpp.daftree.models.DaftreeRepository;

import java.util.List;

public class RecurringOperationsViewModel extends AndroidViewModel {

    private final DaftreeRepository repository;
    private final LiveData<List<RecurringOperation>> recurringOperations;

    public RecurringOperationsViewModel(@NonNull Application application) {
        super(application);
        repository = new DaftreeRepository(application);
        recurringOperations = repository.getActiveRecurringOperations();
    }

    public LiveData<List<RecurringOperation>> getRecurringOperations() {
        return recurringOperations;
    }

    public void add(RecurringOperation operation) {
        repository.addRecurringOperation(operation);
    }

    public void update(RecurringOperation operation) {
        repository.updateRecurringOperation(operation);
    }

    public void delete(RecurringOperation operation) {
        repository.deleteRecurringOperation(operation);
    }

    public boolean hasBeenProcessedToday(int recurringOpId) {
        return repository.hasRecurringOperationBeenProcessedToday(recurringOpId);
    }

    public void markProcessed(int recurringOpId) {
        repository.markRecurringOperationProcessed(recurringOpId);
    }
}