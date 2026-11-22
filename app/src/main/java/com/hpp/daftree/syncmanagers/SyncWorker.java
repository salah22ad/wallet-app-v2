package com.hpp.daftree.syncmanagers;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
import com.hpp.daftree.models.AppAuth;
import com.hpp.daftree.R;
import com.hpp.daftree.models.UUIDGenerator;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.AccountDao;
import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.database.AccountTypeDao;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.CurrencyDao;
import com.hpp.daftree.database.DeletionLog;
import com.hpp.daftree.database.DeletionLogDao;
import com.hpp.daftree.database.Transaction;
import com.hpp.daftree.database.TransactionDao;
import com.hpp.daftree.database.User;
import com.hpp.daftree.database.UserDao;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.notifications.NotificationHelper;
import com.hpp.daftree.utils.SecureLicenseManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class SyncWorker extends Worker {
    private static final String TAG = "SyncWorker";
    private final AccountDao accountDao;
    private final TransactionDao transactionDao;
    private final DeletionLogDao deletionLogDao;
    private final UserDao userDao;
    private final CurrencyDao currencyDao;
    private final AccountTypeDao accountTypeDao;

    private final DaftreeRepository repository;
    private final FirebaseFirestore firestore;
    private boolean isGuest = false;
    private static final String PREF_LAST_DELETE_CHECK = "last_physical_delete_check";
    private static final long THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000;

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        AppDatabase db = AppDatabase.getDatabase(context.getApplicationContext());
        this.accountDao = db.accountDao();
        this.transactionDao = db.transactionDao();
        this.deletionLogDao = db.deletionLogDao();
        this.userDao = db.userDao();
        this.currencyDao = db.currencyDao();
        this.accountTypeDao = db.accountTypeDao();
        this.repository = new DaftreeRepository((Application) context.getApplicationContext());
        this.firestore = FirebaseFirestore.getInstance();
        isGuest = SecureLicenseManager.getInstance(context).isGuest();
    }

    @NonNull
    @Override
    public Result doWork() {
        if (isStopped()) {
            Log.w(TAG, "SyncWorker stopped before starting.");
            return Result.failure();
        }
        Log.d(TAG, "Sync process started.");

//        String uid = FirebaseAuth.getInstance().getUid();
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("MigrationPrefs", Context.MODE_PRIVATE);

        boolean migrationNeeded = prefs.getBoolean("firestore_migration_needed", false);
        FirestoreSyncManager.isSyncWorkerRunning = true;
        FirestoreSyncManager.getInstance(getApplicationContext()).stopListening();
        try {

            if (isGuest) return Result.success();
            if (AppAuth.getUserID(getApplicationContext()) == null || AppAuth.getUserID(getApplicationContext()).isEmpty()) {
                Log.e("SyncWorker", "المستخدم غير مسجل الدخول. إلغاء المزامنة.");
                // إرجاع failure بدلاً من retry لإيقاف المحاولات اللانهائية
                return Result.failure();
            }

            if (migrationNeeded) {
                Log.i(TAG, "Firestore migration required. Starting one-time cloud data migration.");
//                migrateFirestoreData();
                prefs.edit().putBoolean("firestore_migration_needed", false).apply();
                Log.i(TAG, "Firestore migration completed and flag cleared.");
            }

            // تنفيذ خطوات المزامنة بالتسلسل المباشر على الخيط الخلفي لـ WorkManager دون الحاجة لـ RxJava3
            uploadAccounts();
            handleDeletions();
            uploadCurrencies();
            uploadTransactions();
            uploadAccountTypes();
            uploadUserProfile();
            checkDelete();

            // تحديث وقت آخر مزامنة
            SecureLicenseManager.getInstance(getApplicationContext()).setLastSyncTime(System.currentTimeMillis());
            Log.d(TAG, "Sync process finished successfully.");
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Sync failed, will retry.", e);
            if (e instanceof ExecutionException && e.getCause() instanceof FirebaseFirestoreException) {
                FirebaseFirestoreException ffe = (FirebaseFirestoreException) e.getCause();
                if (ffe.getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    Log.e(TAG, "Sync failed due to permission denied. Will not retry.");
                    return Result.failure(); // لا فائدة من إعادة المحاولة تلقائيًا إذا كانت صلاحيات مفقودة
                }
            }
            return Result.retry(); // إعادة المحاولة في الحالات الأخرى


        } finally {
            // 🎯 الخطوة 2: المقصلة الآمنة - بعد الانتهاء التام (نجاح أو فشل أو ريتراي)
            // نقوم بتصفير العلم وإعادة تشغيل المستمعين لتلقي البيانات النظيفة المستقرة
            if (!isStopped()) {
                FirestoreSyncManager.isSyncWorkerRunning = false;
                repository.setUserUID(AppAuth.getUserID(getApplicationContext()));
                // استخدم ApplicationContext فقط
                FirestoreSyncManager.getInstance().startListening(repository, getApplicationContext(), () -> {
                });
            }
            Log.d(TAG, "🏁 انتهى SyncWorker وتم تحرير وإعادة تشغيل نظام المزامنة الحي بنجاح.");
        }
    }

    private void migrateFirestoreData() throws Exception {
        // الخطوة أ: رفع كل العمليات المحلية (التي تم تحويلها بالفعل)
        // هذا سيضيف حقل currencyId إلى مستندات Firestore
        uploadTransactions();

        // الخطوة ب: تنظيف الحقل القديم
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        List<DocumentSnapshot> oldTransactions = Tasks.await(
                firestore.collection("transactions")
                        .whereEqualTo("ownerUID", uid)
                        .whereNotEqualTo("currency", null) // جلب المستندات التي لا يزال بها الحقل القديم
                        .get()
        ).getDocuments();

        if (oldTransactions.isEmpty()) {
            Log.i(TAG, "No old transaction fields to clean up in Firestore.");
            return;
        }

        Log.i(TAG, "Found " + oldTransactions.size() + " transactions with old 'currency' field. Cleaning up...");
        WriteBatch batch = firestore.batch();
        for (DocumentSnapshot doc : oldTransactions) {
            batch.update(doc.getReference(), "currency", FieldValue.delete());
        }
        Tasks.await(batch.commit());
        Log.i(TAG, "Successfully cleaned up old 'currency' field from Firestore documents.");
    }
    // --- دوال مساعدة جديدة لإنشاء بيانات نظيفة ---

    /**
     * يحول كائن Account (Entity) إلى Map نظيفة لإرسالها لـ Firestore.
     * يستثني الحقول المحلية مثل id, syncStatus, firestoreId.
     */
    private Map<String, Object> getAccountMap1(Account account) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", account.getId());
        map.put("firestoreId", account.getFirestoreId());
        map.put("ownerUID", account.getOwnerUID());
        map.put("accountName", account.getAccountName());
        map.put("phoneNumber", account.getPhoneNumber());
        map.put("accountType", account.getAccountType());
        map.put("lastModified", account.getLastModified());
        map.put("acTypeFirestoreId", account.getAcTypeFirestoreId());
        map.put("isDeleted", account.isDeleted());
        map.put("deletedBy", account.getDeletedBy());
        map.put("deleteDate", account.getDeleteDate());
        // لا نرسل id, firestoreId, syncStatus
        return map;
    }

    private Map<String, Object> getAccountMap(Account account) {
        long currentSyncTime = System.currentTimeMillis();

        Map<String, Object> map = new HashMap<>();
        map.put("id", account.getId());
        map.put("firestoreId", account.getFirestoreId());
        map.put("ownerUID", account.getOwnerUID());
        map.put("accountName", account.getAccountName());
        map.put("phoneNumber", account.getPhoneNumber());
        map.put("accountType", account.getAccountType());
        map.put("lastModified", account.getLastModified()); // يبقى كما هو (لمعرفة متى تم التعديل محلياً)
        map.put("lastSyncDate", currentSyncTime); // 🔥 الحقل السحري الجديد للمزامنة
        map.put("acTypeFirestoreId", account.getAcTypeFirestoreId());
        map.put("isDeleted", account.isDeleted());
        map.put("deletedBy", account.getDeletedBy());
        map.put("deleteDate", account.getDeleteDate());

        // تحديث العنصر في الروم (مؤقتاً في الذاكرة، سيتم حفظه لاحقاً بعد النجاح)
        account.setLastSyncDate(currentSyncTime);

        return map;
    }

    /**
     * يحول كائن Transaction (Entity) إلى Map نظيفة لإرسالها لـ Firestore.
     */
    private Map<String, Object> getTransactionMap1(Transaction transaction) {
        Map<String, Object> map = new HashMap<>();
        map.put("ownerUID", transaction.getOwnerUID());
        map.put("accountFirestoreId", transaction.getAccountFirestoreId());
        map.put("accountId", transaction.getAccountId()); // ID المحلي للحساب
        map.put("amount", transaction.getAmount());
        map.put("currencyId", transaction.getCurrencyId());
        map.put("details", transaction.getDetails());
        map.put("importID", transaction.getImportID());
        map.put("timestamp", transaction.getTimestamp());
        map.put("type", transaction.getType());
        map.put("lastModified", transaction.getLastModified());
        map.put("billType", transaction.getBillType());
        map.put("currencyFirestoreId", transaction.getCurrencyFirestoreId());
        map.put("firestoreId", transaction.getFirestoreId());
        map.put("isDeleted", transaction.isDeleted());
        map.put("deleteDate", transaction.getDeleteDate());
        map.put("deletedBy", transaction.getDeletedBy());
        map.put("imageEncoded", transaction.getImageEncoded());
        // لا نرسل id, firestoreId, syncStatus
        return map;
    }
    private Map<String, Object> getTransactionMap(Transaction transaction) {
        long currentSyncTime = System.currentTimeMillis();

        Map<String, Object> map = new HashMap<>();
        map.put("ownerUID", transaction.getOwnerUID());
        map.put("accountFirestoreId", transaction.getAccountFirestoreId());
        map.put("accountId", transaction.getAccountId());
        map.put("amount", transaction.getAmount());
        map.put("currencyId", transaction.getCurrencyId());
        map.put("details", transaction.getDetails());
        map.put("importID", transaction.getImportID());
        map.put("timestamp", transaction.getTimestamp());
        map.put("type", transaction.getType());
        map.put("lastModified", transaction.getLastModified());
        map.put("lastSyncDate", currentSyncTime); // 🔥
        map.put("billType", transaction.getBillType());
        map.put("currencyFirestoreId", transaction.getCurrencyFirestoreId());
        map.put("firestoreId", transaction.getFirestoreId());
        map.put("isDeleted", transaction.isDeleted());
        map.put("deleteDate", transaction.getDeleteDate());
        map.put("deletedBy", transaction.getDeletedBy());
        map.put("imageEncoded", transaction.getImageEncoded());

        transaction.setLastSyncDate(currentSyncTime);

        return map;
    }
    // --- دوال المزامنة المعدلة ---
    private void uploadUserProfile() throws ExecutionException, InterruptedException {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) return;

        // 1. جلب البيانات المحلية
        User localUser = userDao.getUserProfileBlocking();
        if (localUser == null) return;

        DocumentReference userDocRef = firestore.collection("users").document(userId);
        DocumentSnapshot remoteUserDoc = Tasks.await(userDocRef.get());

        if (remoteUserDoc.exists()) {
            // --- المستخدم موجود على السحابة، قم بالمقارنة والمزامنة ---
            User remoteUser = remoteUserDoc.toObject(User.class);
            if (remoteUser == null) return;

            Map<String, Object> updates = new HashMap<>();
            updates.put("company", localUser.getCompany());
            updates.put("phone", localUser.getPhone());
            updates.put("name", localUser.getName());
            updates.put("address", localUser.getAddress());
            // مقارنة عداد العمليات: دائماً نأخذ القيمة الأكبر (الأحدث)
            if (localUser.getTransactions_count() > remoteUser.getTransactions_count()) {
                updates.put("transactions_count", localUser.getTransactions_count());
            } else {
                localUser.setTransactions_count(remoteUser.getTransactions_count());
            }

            // مقارنة إجمالي العمليات المتاحة: دائماً نأخذ القيمة الأكبر
            if (localUser.getMax_transactions() > remoteUser.getMax_transactions()) {
                updates.put("max_transactions", localUser.getMax_transactions());
            } else {
                localUser.setMax_transactions(remoteUser.getMax_transactions());
            }

            // مقارنة مكافآت الإعلانات
            if (localUser.getAd_rewards() > remoteUser.getAd_rewards()) {
                updates.put("ad_rewards", localUser.getAd_rewards());
            } else {
                localUser.setAd_rewards(remoteUser.getAd_rewards());
            }

            // مقارنة مكافآت الدعوة
            if (localUser.getReferral_rewards() > remoteUser.getReferral_rewards()) {
                updates.put("referral_rewards", localUser.getReferral_rewards());
            } else {
                localUser.setReferral_rewards(remoteUser.getReferral_rewards());
            }


            // إذا كان هناك تحديثات لرفعها، قم برفعها
            if (!updates.isEmpty()) {
                updates.put("lastModified", System.currentTimeMillis()); // تحديث وقت آخر تعديل
                Tasks.await(userDocRef.set(updates, SetOptions.merge()));
                Log.d(TAG, "Synced user data TO Firestore.");
            }

            // تحديث البيانات المحلية بالقيم النهائية بعد المقارنة
            localUser.setSyncStatus("SYNCED");
            userDao.upsert(localUser);
            Log.d(TAG, "Synced user data FROM Firestore to local DB.");

        } else {
            // --- المستخدم غير موجود على السحابة (أول مزامنة له) ---
            localUser.setSyncStatus("SYNCED"); // نعتبره متزامن الآن
            Tasks.await(userDocRef.set(localUser));
            userDao.upsert(localUser);
            Log.d(TAG, "Uploaded new user profile to Firestore.");
        }
    }

    private void handleDeletions() {
        try {
            List<DeletionLog> deletions = deletionLogDao.getAllPendingDeletions();
            FirebaseFirestore firestore = FirebaseFirestore.getInstance();

            for (DeletionLog deletion : deletions) {
                String collection = deletion.getCollectionName();
                String firestoreId = deletion.getFirestoreId();

                Log.d(TAG, "Marking as deleted in Firestore: " + collection + "/" + firestoreId);

                try {
                    // 1. تحديث المستند في Firestore (تحديث بدلاً من حذف)
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("isDeleted", true);
                    updates.put("deletedBy", "Eng Salah");
                    updates.put("lastModified", System.currentTimeMillis());

                    Tasks.await(firestore.collection(collection)
                            .document(firestoreId)
                            .update(updates));

                    // 2. تحديث الجدول المحلي المقابل
                    updateLocalRecord(collection, firestoreId);

                    // 3. حذف السجل من DeletionLog
                    deletionLogDao.deleteByFirestoreId(firestoreId);

                    Log.d(TAG, "Successfully marked as deleted: " + collection + "/" + firestoreId);

                } catch (Exception e) {
                    if (e.getCause() instanceof FirebaseFirestoreException) {
                        FirebaseFirestoreException ffe = (FirebaseFirestoreException) e.getCause();
                        if (ffe.getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            deletionLogDao.deleteByFirestoreId(deletion.getFirestoreId());
                            Log.e(TAG, "Permission denied when marking as deleted: " + collection + "/" + firestoreId);
                            continue;
                        }
                        if (ffe.getCode() == FirebaseFirestoreException.Code.NOT_FOUND) {
                            // إذا لم يوجد المستند في Firestore، فقط قم بتنظيف السجل المحلي
                            deletionLogDao.deleteByFirestoreId(firestoreId);
                            Log.w(TAG, "Document not found in Firestore, cleaning local log: " + collection + "/" + firestoreId);
                            continue;
                        }
                    }
                    Log.e(TAG, "Error marking document as deleted: " + collection + "/" + firestoreId, e);
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, "Fatal error in handleDeletions()", ex);
        }
    }

    /**
     * تحديث السجل المحلي بناءً على نوع المجموعة
     */
    private void updateLocalRecord(String collectionName, String firestoreId) {
        long currentTime = System.currentTimeMillis();

        switch (collectionName) {
            case "accounts":
                Account account = accountDao.getAccountByFirestoreId(firestoreId);
                if (account != null) {
                    account.setSyncStatus("SYNCED");
                    account.setDeleteDate(currentTime);
                    accountDao.update(account);
                }
                break;

            case "transactions":
                Transaction transaction = transactionDao.getTransactionByFirestoreId(firestoreId);
                if (transaction != null) {

                    transaction.setDeleteDate(currentTime);
                    transactionDao.update(transaction);
                }
                break;

            case "currencies":
                Currency currency = currencyDao.getCurrencyByFirestoreId(firestoreId);
                if (currency != null) {

                    currency.setDeleteDate(currentTime);
                    currency.setSyncStatus("SYNCED");
                    currencyDao.update(currency);
                }
                break;

            case "accountTypes":
                AccountType accountType = accountTypeDao.getAccountTypeByFirestoreId(firestoreId);
                if (accountType != null) {
                    accountType.setDeleteDate(currentTime);
                    accountType.setSyncStatus("SYNCED");
                    accountTypeDao.update(accountType);
                }
                break;

            default:
                Log.w(TAG, "Unknown collection: " + collectionName);
        }
    }

    private void uploadAccounts() throws Exception {
        List<Account> unsynced = accountDao.getUnsyncedAccounts();
        for (Account account : unsynced) {
            Log.d(TAG, "uploadAccounts To Firestore: " + account.getAccountName());
            // تأكد من وجود ownerUID
            if (account.getOwnerUID() == null) {
                Log.e(TAG, "Account missing ownerUID, skipping sync. ID: " + account.getId());
                continue;
            }

            Map<String, Object> accountData = getAccountMap(account);
            String firestoreId = account.getFirestoreId();

            // إنشاء مستند جديد إذا لزم الأمر
            if (firestoreId == null || firestoreId.isEmpty()) {
                account.setFirestoreId(UUIDGenerator.generateSequentialUUID());
            }

            // إضافة ownerUID إلى البيانات

            // استخدام set() مع دمج البيانات
            Tasks.await(firestore.collection("accounts").document(firestoreId)
                    .set(accountData, SetOptions.merge()));

            // تحديث الحالة المحلية
            accountDao.updateSyncStatus(account.getId(), "SYNCED", System.currentTimeMillis());
        }
    }

    private void uploadCurrencies() throws Exception {
        List<Currency> unsynced = currencyDao.getUnsyncedCurrencies(); // افترض وجود هذه الدالة
        for (Currency currency : unsynced) {

            Map<String, Object> currencyData = getCurrencyMap(currency);

            Tasks.await(firestore.collection("currencies").document(currency.getFirestoreId())
                    .set(currencyData, SetOptions.merge()));
            // تحديث الحالة المحلية
            currencyDao.updateSyncStatus(currency.id, currency.getFirestoreId(), "SYNCED", currency.getLastModified());
        }
    }
   private Map<String, Object> getUserMap(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", user.getName());
        map.put("email", user.getEmail());
        map.put("company", user.getCompany());
        map.put("address", user.getAddress());
        map.put("phone", user.getPhone());
        map.put("lastModified", user.getLastModified());
        return map;
    }

    private void uploadAccountTypes() throws Exception {
        List<AccountType> unsynced = accountTypeDao.getUnsyncedAccountTypes();
        for (AccountType accountType : unsynced) {
            Map<String, Object> accountTypeData = getAccountTypeMap(accountType);
            Tasks.await(firestore.collection("accountTypes").document(accountType.getFirestoreId()).set(accountTypeData, SetOptions.merge()));
            accountTypeDao.updateSyncStatus(accountType.id, accountType.getFirestoreId(), "SYNCED", accountType.getLastModified());
        }
        if (!unsynced.isEmpty()) Log.d(TAG, "Synced " + unsynced.size() + " account types.");
    }

    // **دالة مساعدة جديدة**
    private Map<String, Object> getAccountTypeMap1(AccountType accountType) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", accountType.name);
        map.put("firestoreId", accountType.getFirestoreId());
        map.put("ownerUID", accountType.getOwnerUID());
        map.put("lastModified", accountType.getLastModified());
        map.put("isDeleted", accountType.isDeleted());
        map.put("isDefault", accountType.isDefault());
        map.put("deletedBy", accountType.getDeletedBy());
        map.put("deleteDate", accountType.getDeleteDate());
        return map;
    }
    private Map<String, Object> getCurrencyMap(Currency currency) {
        long currentSyncTime = System.currentTimeMillis();
        Map<String, Object> map = new HashMap<>();
        map.put("id", currency.id);
        map.put("name", currency.name);
        map.put("ownerUID", currency.getOwnerUID());
        map.put("lastModified", currency.getLastModified());
        map.put("firestoreId", currency.getFirestoreId());
        map.put("isDefault", currency.isDefault());
        map.put("code", currency.getCode());
        map.put("symbol", currency.getSymbol());
        map.put("isDeleted", currency.isDeleted());
        map.put("deletedBy", currency.getDeletedBy());
        map.put("lastSyncDate", currentSyncTime);
        map.put("deleteDate", currency.getDeleteDate());
        currency.setLastSyncDate(currentSyncTime);
        return map;
    }

    private Map<String, Object> getAccountTypeMap(AccountType accountType) {
        long currentSyncTime = System.currentTimeMillis();
        Map<String, Object> map = new HashMap<>();
        map.put("name", accountType.name);
        map.put("firestoreId", accountType.getFirestoreId());
        map.put("ownerUID", accountType.getOwnerUID());
        map.put("lastModified", accountType.getLastModified());
        map.put("lastSyncDate", currentSyncTime); // 🔥
        map.put("isDeleted", accountType.isDeleted());
        map.put("isDefault", accountType.isDefault());
        map.put("deletedBy", accountType.getDeletedBy());
        map.put("deleteDate", accountType.getDeleteDate());

        accountType.setLastSyncDate(currentSyncTime);
        return map;
    }
    private void uploadTransactions() throws Exception {
        TransactionUploadController controller = new TransactionUploadController(getApplicationContext());
        List<Transaction> unsynced = transactionDao.getUnsyncedTransactions();

        for (Transaction tx : unsynced) {
            if (!controller.canSend(tx)) {
                if (!controller.hasBlocked()) {
                    controller.storeFirstBlocked(tx);
                    // أظهر ديالوج تحذير مرة واحدة
//                    NotificationHelper.get().showBlockedDialog(getApplicationContext(),
//                            "عذراً هناك عمليات لم يتم إرسالها...",
//                            "يرجى رفع رصيدك سواء بمشاهدة إعلان أو دعوة صديق...");
                    NotificationHelper.get(getApplicationContext()).showLocalNotification(getApplicationContext().getString(R.string.daily_plan_tit2),
                            getApplicationContext().getString(R.string.daily_plan_msg2),
                            1001, true);
                }
                continue; // اترك العمليات معلقة
            }

            // 🔹 أكمل الرفع كالمعتاد
            // Map<String,Object> data = getTransactionMap(tx);
            String fsId = tx.getFirestoreId();
            if (fsId == null || fsId.isEmpty()) {
                //  fsId = firestore.collection("transactions").document().getId();
                tx.setFirestoreId(UUIDGenerator.generateSequentialUUID());
            }
            Log.d(TAG, "uploadTransactions To Firestore: " + tx.getId());
            if (tx.getOwnerUID() == null) {
                Log.e(TAG, "Transaction missing ownerUID, skipping sync. ID: " + tx.getId());
                continue;
            }

            // تأكد من وجود الحساب الأب
            Account parentAccount = accountDao.getAccountByIdBlocking(tx.getAccountId());
            if (parentAccount == null || parentAccount.getFirestoreId() == null) {
                Log.w(TAG, "Parent account not synced, skipping transaction. Account ID: " + tx.getAccountId());
                continue;
            }

            Map<String, Object> txData = getTransactionMap(tx);
            // إضافة الحقول المطلوبة للقواعد الأمنية
            Tasks.await(firestore.collection("transactions")
                    .document(fsId).set(txData, SetOptions.merge()));
            transactionDao.updateSyncStatus(tx.getId(), "SYNCED", System.currentTimeMillis());
        }
    }

    private void checkDelete() {
        Context context = getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE);
        long lastCheck = prefs.getLong(PREF_LAST_DELETE_CHECK, 0);
        long now = System.currentTimeMillis();

        // نعمل مرة واحدة في اليوم فقط
        if (now - lastCheck < 24 * 60 * 60 * 1000) {
            Log.d(TAG, "Physical delete check already performed today. Skipping.");
            return;
        }

        long threshold = now - THIRTY_DAYS_MS;
        Log.d(TAG, "threshold: " + threshold);
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();

        try {
            // 1. المعاملات (transactions) أولاً لتجنب مشكلة المفتاح الخارجي
            List<Transaction> oldDeletedTransactions = transactionDao.getOldDeletedTransactions(threshold);
            if (!oldDeletedTransactions.isEmpty()) {
                WriteBatch batch = firestore.batch();
                for (Transaction tx : oldDeletedTransactions) {
                    if (tx.getFirestoreId() != null && !tx.getFirestoreId().isEmpty()) {
                        batch.delete(firestore.collection("transactions").document(tx.getFirestoreId()));
                    }
                }
                Tasks.await(batch.commit());
                for (Transaction tx : oldDeletedTransactions) {
                    transactionDao.deleteByFirestoreId(tx.getFirestoreId());
                }
                Log.d(TAG, "Deleted " + oldDeletedTransactions.size() + " old transactions.");
            }

            // 2. الحسابات
            List<Account> oldDeletedAccounts = accountDao.getOldDeletedAccounts(threshold);
            if (!oldDeletedAccounts.isEmpty()) {
                WriteBatch batch = firestore.batch();
                for (Account acc : oldDeletedAccounts) {
                    if (acc.getFirestoreId() != null && !acc.getFirestoreId().isEmpty()) {
                        batch.delete(firestore.collection("accounts").document(acc.getFirestoreId()));
                    }
                }
                Tasks.await(batch.commit());
                for (Account acc : oldDeletedAccounts) {
                    accountDao.deleteByFirestoreId(acc.getFirestoreId());
                }
                Log.d(TAG, "Deleted " + oldDeletedAccounts.size() + " old accounts.");
            }

            // 3. العملات
            List<Currency> oldDeletedCurrencies = currencyDao.getOldDeletedCurrencies(threshold);
            if (!oldDeletedCurrencies.isEmpty()) {
                WriteBatch batch = firestore.batch();
                for (Currency cur : oldDeletedCurrencies) {
                    if (cur.getFirestoreId() != null && !cur.getFirestoreId().isEmpty()) {
                        batch.delete(firestore.collection("currencies")
                                .document(cur.getFirestoreId()));
                    }
                }
                Tasks.await(batch.commit());
                for (Currency cur : oldDeletedCurrencies) {
                    currencyDao.deleteByFirestoreId(cur.getFirestoreId());
                }
                Log.d(TAG, "Deleted " + oldDeletedCurrencies.size() + " old currencies.");
            }

            // 4. أنواع الحسابات
            List<AccountType> oldDeletedTypes = accountTypeDao.getOldDeletedAccountTypes(threshold);
            if (!oldDeletedTypes.isEmpty()) {
                WriteBatch batch = firestore.batch();
                for (AccountType type : oldDeletedTypes) {
                    if (type.getFirestoreId() != null && !type.getFirestoreId().isEmpty()) {
                        batch.delete(firestore.collection("accountTypes").document(type.getFirestoreId()));
                    }
                }
                Tasks.await(batch.commit());
                for (AccountType type : oldDeletedTypes) {
                    accountTypeDao.deleteByFirestoreId(type.getFirestoreId());
                }
                Log.d(TAG, "Deleted " + oldDeletedTypes.size() + " old account types.");
            }

            // تحديث وقت آخر فحص
            prefs.edit().putLong(PREF_LAST_DELETE_CHECK, now).apply();

        } catch (Exception e) {
            Log.e(TAG, "Error during physical deletion of old records", e);
        }
    }

}
