package com.hpp.daftree.syncmanagers;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.Observer;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.hpp.daftree.utils.SecureLicenseManager;

import java.util.concurrent.TimeUnit;

public class SyncCoordinator {

    private static final String UNIQUE_WORK_NAME = "UNIQUE_SYNC_WORK";
   public static void triggerSyncWithCallback(Context context, Runnable onSuccessCallback) {
        boolean isGuest = SecureLicenseManager.getInstance(context).isGuest();
        if (isGuest) return;

        WorkManager workManager = WorkManager.getInstance(context);

        // إلغاء أي عمل سابق لضمان عدم وجود مزامنة قديمة
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME);

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(constraints)
                .build();

        workManager.enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                syncRequest
        );

        // يجب تسجيل المراقب على الخيط الرئيسي
        new Handler(Looper.getMainLooper()).post(() -> {
            Observer<WorkInfo> observer = new Observer<WorkInfo>() {
                @Override
                public void onChanged(WorkInfo workInfo) {
                    if (workInfo != null && workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                        if (onSuccessCallback != null) {
                            onSuccessCallback.run();
                        }
                        workManager.getWorkInfoByIdLiveData(syncRequest.getId()).removeObserver(this);
                    } else if (workInfo != null && workInfo.getState().isFinished()) {
                        workManager.getWorkInfoByIdLiveData(syncRequest.getId()).removeObserver(this);
                    }
                }
            };
            workManager.getWorkInfoByIdLiveData(syncRequest.getId()).observeForever(observer);
        });
    }

}