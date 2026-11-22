package com.hpp.daftree.syncmanagers;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.hpp.daftree.R;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.utils.SecureLicenseManager;

public class PendingTxCheckWorker extends Worker {
    private static final String TAG = "PendingTxCheckWorker";

    public PendingTxCheckWorker(@NonNull Context ctx, @NonNull WorkerParameters params) {
        super(ctx, params);
    }


    @NonNull
    @Override
    public Result doWork() {
        try {
            boolean isGuest = SecureLicenseManager.getInstance(getApplicationContext()).isGuest();
            if (isGuest) {
                return Result.success();
            }

            TransactionUploadController controller = new TransactionUploadController(getApplicationContext());

            if (controller.hasBlocked()) {
                Log.d(TAG, "تم اكتشاف عمليات معلقة، سيتم عرض تحذير في الواجهة الأمامية");

                // 🔥 **فقط تشغيل المزامنة دون أي واجهات**
                DaftreeRepository repository = new DaftreeRepository((Application) getApplicationContext());
                repository.triggerSync();
            }

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "خطأ في PendingTxCheckWorker: " + e.getMessage());
            return Result.success(); // نجاح لتجنب إعادة المحاولة المستمرة
        }
    }
}