package com.hpp.daftree;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.hpp.daftree.viewmodels.AccountDetailsViewModel;

public class ViewModelFactory implements ViewModelProvider.Factory {
    private final Application mApplication;
    private final int mParam;

    public ViewModelFactory(Application application, int param) {
        mApplication = application;
        mParam = param;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        // إنشاء AccountDetailsViewModel وتمرير البارامتر
        if (modelClass.isAssignableFrom(AccountDetailsViewModel.class)) {
            return (T) new AccountDetailsViewModel(mApplication, mParam);
        }

        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}