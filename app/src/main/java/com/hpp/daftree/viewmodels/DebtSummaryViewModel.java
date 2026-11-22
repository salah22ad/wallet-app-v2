package com.hpp.daftree.viewmodels;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.hpp.daftree.R;
import com.hpp.daftree.database.ExternalAccountEntity;
import com.hpp.daftree.database.ExternalTransactionEntity;
import com.hpp.daftree.debt.ExternalDebtModels.UserDebtSummary;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.syncmanagers.ExternalDebtSyncManager; // الكلاس الجديد
import com.hpp.daftree.utils.DataMapper;
import com.hpp.daftree.utils.LicenseManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DebtSummaryViewModel extends AndroidViewModel {

    private final DaftreeRepository repository;
    private final MediatorLiveData<List<UserDebtSummary>> uiSummaryList = new MediatorLiveData<>();
    private final MutableLiveData<Boolean> isOfflineMode = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> syncMessage = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_LAST_SYNC = "last_sync_time";

    private final LicenseManager licenseManager;
    private final MutableLiveData<Boolean> showLimitDialogEvent = new MutableLiveData<>();
    public DebtSummaryViewModel(@NonNull Application application) {
        super(application);
        repository = new DaftreeRepository(application);
        licenseManager = new LicenseManager(application);
        setupLocalObservers();
    }

    private void setupLocalObservers() {
        // نراقب البيانات المحلية فقط (Room)
        LiveData<List<ExternalAccountEntity>> accountsLive = repository.getAllExternalAccounts();
        LiveData<List<ExternalTransactionEntity>> transactionsLive = repository.getAllExternalTransactions();

        uiSummaryList.addSource(accountsLive, accounts -> {
            List<ExternalTransactionEntity> transactions = transactionsLive.getValue();
            uiSummaryList.setValue(DataMapper.mapToUiModel(accounts, transactions));
        });

        uiSummaryList.addSource(transactionsLive, transactions -> {
            List<ExternalAccountEntity> accounts = accountsLive.getValue();
            uiSummaryList.setValue(DataMapper.mapToUiModel(accounts, transactions));
        });
    }

    public LiveData<List<UserDebtSummary>> getSummaryList() { return uiSummaryList; }
    public LiveData<Boolean> getIsOfflineMode() { return isOfflineMode; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getError() { return error; }
    public LiveData<String> getSyncMessage() { return syncMessage; }
    public LiveData<Boolean> getShowLimitDialogEvent() { return showLimitDialogEvent; }

    public void refreshData(boolean isPremium) {
        if (!isNetworkAvailable()) {
            isOfflineMode.setValue(true);
            isLoading.setValue(false);
            return;
        }
        isOfflineMode.setValue(false);

        // إذا كان مجاني وتم التحديث اليوم، لا نحدث
//        if (!isPremium && isSyncedToday()) {
//            syncMessage.setValue(getApplication().getString(R.string.sync_message_today));
//            isLoading.setValue(false);
//            return;
//        }
        if (!licenseManager.canRetrieveMyAccounts()) {
            isLoading.setValue(false);
            // نطلق حدث إظهار الديالوج
            showLimitDialogEvent.setValue(true);
            return;
        }
        isLoading.setValue(true);
        SharedPreferences prefs = getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastSyncTime = prefs.getLong(KEY_LAST_SYNC, 0);
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (Boolean.TRUE.equals(isLoading.getValue())) {
                    isLoading.setValue(false);
                    error.setValue("Timeout occurred while syncing");
                }
            }
        };
        handler.postDelayed(timeoutRunnable, 10000);
        // استدعاء المدير الجديد للمزامنة
        ExternalDebtSyncManager.getInstance().startExternalDebtSync(
                repository,
                getApplication(),
                lastSyncTime,
                new ExternalDebtSyncManager.SyncCallback() {
                    @Override
                    public void onSuccess() {
                        handler.removeCallbacks(timeoutRunnable);
                        isLoading.setValue(false);
                        saveSyncTime();
                        // لا داعي لعمل شيء آخر، البيانات ستحفظ في Room وتظهر تلقائياً عبر LiveData
                    }

                    @Override
                    public void onError(String errorMsg) {
                        handler.removeCallbacks(timeoutRunnable);
                        isLoading.setValue(false);
                        error.setValue(errorMsg);
                        Log.e("DebtSummaryViewModel", "Error syncing external debt: " + errorMsg);
                    }
                }
        );
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getApplication().getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
    }

    private boolean isSyncedToday() {
        SharedPreferences prefs = getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastSync = prefs.getLong(KEY_LAST_SYNC, 0);
        long currentTime = System.currentTimeMillis();
        return (currentTime - lastSync) < (24 * 60 * 60 * 1000);
    }

    private void saveSyncTime() {
        SharedPreferences prefs = getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply();
    }
    /**
     * دالة واحدة تقوم باسترجاع وعرض آخر وقت مزامنة
     * @return String وقت المزامنة بصيغة yyyy-MM-dd HH:mm:ss أو "لم تتم المزامنة بعد"
     */
    public String getLastSyncTimeFormatted() {
        SharedPreferences prefs = getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastSyncTime = prefs.getLong(KEY_LAST_SYNC, 0);

        if (lastSyncTime == 0) {
            return getApplication().getString(R.string.sync_message_never)  ;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        return sdf.format(new Date(lastSyncTime));
    }
}