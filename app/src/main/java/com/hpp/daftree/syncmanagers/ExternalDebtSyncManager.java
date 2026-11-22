package com.hpp.daftree.syncmanagers;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.hpp.daftree.R;
import com.hpp.daftree.database.AppDatabase; // تأكد من استيراد قاعدة البيانات للوصول للـ Executor
import com.hpp.daftree.database.ExternalAccountEntity;
import com.hpp.daftree.database.ExternalTransactionEntity;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.utils.AppErrorHandler;
import com.hpp.daftree.utils.SecureLicenseManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExternalDebtSyncManager {

    private static ExternalDebtSyncManager instance;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static final String TAG = "ExternalDebtSync";

    public interface SyncCallback {
        void onSuccess();

        void onError(String error);
    }

    public static synchronized ExternalDebtSyncManager getInstance() {
        if (instance == null) instance = new ExternalDebtSyncManager();
        return instance;
    }

    public void startExternalDebtSync(DaftreeRepository repository, Context context, long lastSyncTime, SyncCallback callback) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            callback.onError(context.getString(R.string.error_login_required));
            return;
        }

        // الخطوة 1: جلب قائمة الحسابات المحلية في الخلفية قبل بدء أي شيء
        // نستخدم Thread بسيط أو Executor لأننا لا نستطيع الوصول لقاعدة البيانات من الـ Main Thread
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // جلب المعرفات المحلية الموجودة مسبقاً
            List<String> localAccountIds = repository.getLocalAccountIdsBlocking();

            // العودة للـ Main Thread (أو المتابعة) لبدء عمليات الفايربيس
            // ملاحظة: عمليات فايربيس تعمل في الخلفية تلقائياً، لذا يمكننا استدعاؤها مباشرة
            startFirebaseLogic(uid, repository, context, lastSyncTime, localAccountIds, callback);
        });
    }

    private void startFirebaseLogic(String uid, DaftreeRepository repository, Context context, long lastSyncTime, List<String> localAccountIds, SyncCallback callback) {
        db.collection("users").document(uid).get().addOnSuccessListener(userDoc -> {
            Boolean isEnabled = userDoc.getBoolean("isDebtFeatureEnabled");
            if (isEnabled == null || !isEnabled) {
                callback.onError(context.getString(R.string.error_feature_disabled));
                return;
            }

            String myPhone = userDoc.getString("phoneNumber");
            String countryCode = userDoc.getString("countryCode");

            if (myPhone != null && !myPhone.isEmpty()) {
                List<String> searchNumbers = preparePhoneNumbers(myPhone, countryCode);
                // نمرر القائمة المحلية للدالة التالية
                fetchAccountsAndSync(searchNumbers, repository, lastSyncTime, localAccountIds, callback,context);
            } else {
                callback.onError(context.getString(R.string.error_no_phone_registered));
            }
        }).addOnFailureListener(e -> {
            callback.onError(e.getMessage());
            AppErrorHandler.handle(context);
        });
    }

    private List<String> preparePhoneNumbers(String phone, String code) {
        List<String> numbers = new ArrayList<>();
        String cleanPhone = phone.replaceAll("\\s+", "");
        numbers.add(cleanPhone);
        if (code != null) {
            String cleanCode = code.replaceAll("\\s+", "");
            numbers.add(cleanCode + cleanPhone);
        }
        if (!cleanPhone.startsWith("0")) numbers.add("0" + cleanPhone);
        else numbers.add(cleanPhone.substring(1));
        return numbers;
    }

    private void fetchAccountsAndSync(List<String> phoneNumbers, DaftreeRepository repository, long lastSyncTime, List<String> localAccountIds, SyncCallback callback,Context context) {
        db.collectionGroup("accounts")
                .whereIn("phoneNumber", phoneNumbers)
                .whereEqualTo("isDeleted", false)
                .get(Source.DEFAULT)
                .addOnSuccessListener(accountsSnapshot -> {
                    processAccountsAndFetchTransactions(accountsSnapshot, repository, lastSyncTime, localAccountIds, callback,context);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching accounts", e);
                    callback.onError(e.getMessage());
                    AppErrorHandler.handle(context);
                });
    }

    private void processAccountsAndFetchTransactions(QuerySnapshot accountsSnapshot, DaftreeRepository repository, long globalLastSyncTime, List<String> localAccountIds, SyncCallback callback,Context context) {
        List<ExternalAccountEntity> validAccounts = Collections.synchronizedList(new ArrayList<>());
        List<ExternalTransactionEntity> incrementalTransactions = Collections.synchronizedList(new ArrayList<>());
        List<Task<Void>> tasks = new ArrayList<>();

        if (accountsSnapshot != null) {
            for (DocumentSnapshot doc : accountsSnapshot.getDocuments()) {
                if (doc.contains("isDeleted")) {
                    if (Boolean.TRUE.equals(doc.getBoolean("isDeleted"))) continue;
                }
                String ownerUID = doc.getString("ownerUID");
                if (ownerUID == null) continue;

                Task<Void> accountTask = db.collection("users").document(ownerUID).get()
                        .continueWithTask(userTask -> {
                            DocumentSnapshot userDoc = userTask.getResult();
                            if (userDoc.exists()) {
                                Boolean enabled = userDoc.getBoolean("isDebtFeatureEnabled");

                                if (Boolean.TRUE.equals(enabled)) {
                                    ExternalAccountEntity acc = mapDocumentToAccount(doc, userDoc);
                                    validAccounts.add(acc);

                                    // --- المنطق الذكي هنا ---
                                    // 1. هل الحساب موجود عندي مسبقاً؟
                                    boolean isExistingAccount = localAccountIds.contains(acc.firestoreId);

                                    // 2. تحديد وقت الجلب:
                                    // - إذا موجود: نستخدم lastSyncTime (جلب الجديد فقط).
                                    // - إذا جديد: نستخدم 0 (جلب الكل لأول مرة).
                                    long accountFetchTime = isExistingAccount ? globalLastSyncTime : 0;

                                    return fetchIncrementalTransactions(acc.firestoreId, incrementalTransactions, accountFetchTime);
                                }
                            }
                            return Tasks.forResult(null);
                        });
                tasks.add(accountTask);
            }
        }

        Tasks.whenAll(tasks).addOnSuccessListener(v -> {
            repository.syncHybridData(new ArrayList<>(validAccounts), new ArrayList<>(incrementalTransactions));
            callback.onSuccess();
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Sync Logic Failed", e);
            callback.onError("Sync Error: " + e.getMessage());
            AppErrorHandler.handle(context);
        });
    }

    private Task<Void> fetchIncrementalTransactions(String accountId, List<ExternalTransactionEntity> targetList, long fetchTimeLimit) {
        Query query = db.collection("transactions")
                .whereEqualTo("accountFirestoreId", accountId);
//                 .whereEqualTo("isDeleted", false);
        // نستخدم الوقت المحدد لكل حساب على حدة
        if (fetchTimeLimit > 0) {
            query = query.whereGreaterThan("lastModified", fetchTimeLimit);
        }

        return query.get(Source.DEFAULT).continueWithTask(task -> {
            QuerySnapshot txSnap = task.getResult();
            if (txSnap != null && !txSnap.isEmpty()) {
                List<ExternalTransactionEntity> tempList = new ArrayList<>();
                List<Task<Void>> currencyTasks = new ArrayList<>();
                Set<String> currenciesToUpdate = new HashSet<>();
                Map<String, String> currencyNameCache = new HashMap<>();

                for (DocumentSnapshot doc : txSnap.getDocuments()) {
                    ExternalTransactionEntity tx = mapDocumentToTransaction(doc);
                    tempList.add(tx);
                    if (tx.currencyFirestoreId != null) {
                        currenciesToUpdate.add(tx.currencyFirestoreId);
                    }
                }

                for (String currId : currenciesToUpdate) {
                    Task<Void> cTask = db.collection("currencies").document(currId).get()
                            .continueWith(cTaskResult -> {
                                if (cTaskResult.isSuccessful()) {
                                    DocumentSnapshot cDoc = cTaskResult.getResult();
                                    if (cDoc != null && cDoc.exists()) {
                                        String name = cDoc.getString("name");
                                        if (name != null) currencyNameCache.put(currId, name);
                                    }
                                }
                                return null;
                            });
                    currencyTasks.add(cTask);
                }

                return Tasks.whenAll(currencyTasks).continueWith(v -> {
                    for (ExternalTransactionEntity t : tempList) {
                        t.currencyName = currencyNameCache.getOrDefault(t.currencyFirestoreId, "عملة");
                    }
                    targetList.addAll(tempList);
                    return null;
                });
            }
            return Tasks.forResult(null);
        });
    }

    private ExternalAccountEntity mapDocumentToAccount(DocumentSnapshot doc, DocumentSnapshot userDoc) {
        ExternalAccountEntity acc = new ExternalAccountEntity();
        acc.firestoreId = doc.getId();
        acc.ownerUID = doc.getString("ownerUID");
        acc.accountName = doc.getString("accountName");
        acc.phoneNumber = doc.getString("phoneNumber");
        acc.lastModified = doc.getLong("lastModified") != null ? doc.getLong("lastModified") : System.currentTimeMillis();
        acc.isDeleted = false;

        String fullName = userDoc.getString("fullName");
        String ownerPhone = userDoc.getString("phoneNumber");
        acc.ownerName = (fullName != null && !fullName.isEmpty()) ? fullName : userDoc.getString("name");
        acc.ownerPhone = (ownerPhone != null && !ownerPhone.isEmpty()) ? ownerPhone : "";
        if (acc.ownerName == null) acc.ownerName = "مستخدم";
        return acc;
    }

    private ExternalTransactionEntity mapDocumentToTransaction(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) {
            return null;
        }

        try {
            ExternalTransactionEntity tx = new ExternalTransactionEntity();
            tx.firestoreId = doc.getId();
            tx.accountFirestoreId = doc.getString("accountFirestoreId");
            tx.currencyFirestoreId = doc.getString("currencyFirestoreId");
            tx.details = doc.getString("details");
            tx.ownerUID = doc.getString("ownerUID");
            tx.amount = doc.getDouble("amount") != null ? doc.getDouble("amount") : 0.0;
            tx.type = doc.getLong("type") != null ? doc.getLong("type").intValue() : 0;

            Object ts = doc.get("timestamp");
            if (ts instanceof Timestamp) {
                tx.timestamp = ((Timestamp) ts).toDate().getTime();
            } else if (ts instanceof Long) {
                tx.timestamp = (Long) ts;
            } else if (ts instanceof Date) {
                tx.timestamp = ((Date) ts).getTime();
            } else {
                tx.timestamp = System.currentTimeMillis();
            }

            Long lm = doc.getLong("lastModified");
            tx.lastModified = lm != null ? lm : System.currentTimeMillis();

            Boolean del = doc.getBoolean("isDeleted");
            tx.isDeleted = del != null ? del : false;

            return tx;
        } catch (Exception e) {
            Log.e(TAG, "Error mapping transaction document: " + doc.getId(), e);
            return null;
        }
    }

    private ExternalTransactionEntity mapDocumentToTransaction1(DocumentSnapshot doc) {
        ExternalTransactionEntity tx = new ExternalTransactionEntity();
        tx.firestoreId = doc.getId();
        tx.accountFirestoreId = doc.getString("accountFirestoreId");
        tx.currencyFirestoreId = doc.getString("currencyFirestoreId");
        tx.details = doc.getString("details");
        tx.ownerUID = doc.getString("ownerUID");
        tx.amount = doc.getDouble("amount") != null ? doc.getDouble("amount") : 0.0;
        tx.type = doc.getLong("type") != null ? doc.getLong("type").intValue() : 0;

        Object ts = doc.get("timestamp");
        if (ts instanceof Timestamp) tx.timestamp = ((Timestamp) ts).toDate().getTime();
        else if (ts instanceof Long) tx.timestamp = (Long) ts;
        else tx.timestamp = System.currentTimeMillis();

        Long lm = doc.getLong("lastModified");
        tx.lastModified = lm != null ? lm : System.currentTimeMillis();

        Boolean del = doc.getBoolean("isDeleted");
        tx.isDeleted = del != null ? del : false;
        return tx;
    }
}