
package com.hpp.daftree.syncmanagers;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Keep;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.hpp.daftree.models.AppAuth;
import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.models.UUIDGenerator;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.Transaction;
import com.hpp.daftree.database.User;
import com.hpp.daftree.helpers.LanguageManager;
import com.hpp.daftree.utils.AppErrorHandler;
import com.hpp.daftree.utils.RewardManager;
import com.hpp.daftree.utils.SecureLicenseManager;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class FirestoreRestoreHelper {
    private static final String TAG = "FirestoreRestoreHelper";

    public interface RestoreListener {
        void onProgressUpdate(String message, int progress, int total);

        void onComplete(String complate);

        void onSucces(String complate);

        void onError(String error);
    }

    private AppDatabase appDb;
    private final FirebaseFirestore firestore;
    private final SecureLicenseManager secureManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final String currentUserId;
    private final Context context;

    private final RewardManager rewardManager;

    public FirestoreRestoreHelper(Context context) {
        this.context = context.getApplicationContext();
        this.appDb = AppDatabase.getDatabase(context);
        this.firestore = FirebaseFirestore.getInstance();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        this.currentUserId = (currentUser != null) ? AppAuth.getUserID(context) : AppAuth.getUserID(context);
        rewardManager = new RewardManager();
        secureManager = SecureLicenseManager.getInstance(context);
    }


    private int totalCurrencies = 0;
    private int processedCurrencies = 0;

    private ListenerRegistration referralNotifListener;
    private final AtomicLong lastReferralCheck = new AtomicLong(0L);


    private void restoreUserProfile(RestoreListener listener) {
        // handler.post(() -> listener.onProgressUpdate(context.getString(R.string.start_impoart_userProfile), 0, 100));
        handler.post(() -> listener.onProgressUpdate(context.getString(R.string.start_impoart_accounts), 0, 100));
        firestore.collection("users")
                .document(currentUserId)
                .get()
                .addOnCompleteListener(task -> {
                    executor.execute(() -> {
                        if (task.isSuccessful() && task.getResult().exists()) {
                            DocumentSnapshot userDoc = task.getResult();
                            User remoteUser = userDoc.toObject(User.class);
                            // استخراج بيانات الترخيص
                            int maxTransactions = userDoc.getLong("max_transactions") != null ?
                                    userDoc.getLong("max_transactions").intValue() : 0;
                            int transactionsCount = userDoc.getLong("transactions_count") != null ?
                                    userDoc.getLong("transactions_count").intValue() : 0;
                            int adRewards = userDoc.getLong("ad_rewards") != null ?
                                    userDoc.getLong("ad_rewards").intValue() : 0;
                            int referralRewards = userDoc.getLong("referral_rewards") != null ?
                                    Objects.requireNonNull(userDoc.getLong("referral_rewards")).intValue() : 0;
                            boolean isPremium = Boolean.TRUE.equals(userDoc.getBoolean("is_premium"));
                            long lastModified = userDoc.contains("lastModified") ? userDoc.getLong("lastModified") : System.currentTimeMillis();

                            Object last_login = userDoc.getString("last_login") != null ?
                                    userDoc.getString("last_login") : SecureLicenseManager.getInstance(context).getLast_login();

                            // حفظ البيانات في التخزين المشفر
                            SecureLicenseManager.getInstance(context)
                                    .saveLicenseData(maxTransactions, transactionsCount,
                                            adRewards, referralRewards, isPremium, lastModified, last_login);


                            assert remoteUser != null;
                            String isAdmin = userDoc.getString("userType");
                            if (isAdmin != null) {
                                new SyncPreferences(context).setKeyUserType(isAdmin);
                            } else {
                                new SyncPreferences(context).setKeyUserType("user");
                            }
                            // String isAdmin = (data.get("userType")) != null ? (String) data.get("userType") : "";

                            remoteUser.setAddress(userDoc.getString("address"));
                            remoteUser.setName(userDoc.getString("name"));
                            remoteUser.setCompany(userDoc.getString("company"));
                            remoteUser.setPhone(userDoc.getString("phone"));
                            remoteUser.setProfileImageUri(userDoc.getString("profileImageUri"));
                            Log.d(TAG, "تم استيراد بيانات الحساب: " + "\n" +
                                    "name =" + userDoc.getString("name") + "\n" +
                                    ", phone=" + userDoc.getString("phone") + "\n" +
                                    " , transactions_count=" + transactionsCount + "\n" +
                                    " , ad_rewards=" + adRewards);
                            if (!appDb.isOpen()) {
                                appDb = AppDatabase.getDatabase(context);
                                appDb.userDao().upsert(remoteUser);
                            }
                            handler.post(() -> {
                                // listener.onProgressUpdate(context.getString(R.string.start_impoart_accounts), 5, 100);
                                Log.w(TAG, "تم الانتهاء من جلب بيانات المستخدم '");
                                restoreAccountTypes(listener);
                            });
                        } else {
                            Log.w(TAG, "لم يتم العثور على بيانات الترخيص في Firestore");
                            handler.post(() -> restoreAccountTypes(listener));
                        }
                    });

                }).addOnFailureListener(e -> {
                    Log.e(TAG, "Restore Error", e);
                    AppErrorHandler.handle(context);
                });
    }

    private void restoreAccounts(RestoreListener listener, Map<String, String> fallbackTypeMap) {
        handler.post(() -> listener.onProgressUpdate(context.getString(R.string.start_impoart_accounts), 15, 100));

        executor.execute(() -> {
            try {
                // 1. تجهيز خريطة أنواع الحسابات للسرعة
                Map<String, String> localAccountTypesMap = new HashMap<>(fallbackTypeMap);
                List<AccountType> allTypes = appDb.accountTypeDao().getAccountTypes();
                for (AccountType type : allTypes) {
                    if (type.name != null) {
                        localAccountTypesMap.put(type.name.trim().toLowerCase(), type.getFirestoreId());
                    }
                }

                // 2. جلب الحسابات
                firestore.collection("accounts")
                        .whereEqualTo("ownerUID", currentUserId)
                        .get()
                        .addOnCompleteListener(task -> {
                            executor.execute(() -> {
                                if (task.isSuccessful()) {
                                    QuerySnapshot accountsSnapshot = task.getResult();
                                    if (accountsSnapshot.isEmpty()) {
                                        Log.w(TAG, "لا توجد حسابات في السيرفر.");
                                        handler.post(() -> restoreCurrencies(listener, new ArrayList<>(), new HashMap<>()));
                                        return;
                                    }

                                    List<Account> accountsToProcess = new ArrayList<>();
                                    WriteBatch batch = firestore.batch();
                                    boolean needsBatchCommit = false;

                                    List<String> accountFirestoreIds = new ArrayList<>();
                                    Map<String, Integer> firestoreToRoomIdMap = new HashMap<>();

                                    // 3. معالجة البيانات (تعديل جذري هنا: التعيين اليدوي)
                                    for (QueryDocumentSnapshot doc : accountsSnapshot) {
                                        try {
                                            // تجاهل المحذوف

                                            boolean isDel = doc.contains("isDeleted") && Boolean.TRUE.equals(doc.getBoolean("isDeleted"));
                                            if (doc.contains("isDeleted")) {
                                                if (isDel) continue;
                                            }


                                            // إنشاء الكائن يدوياً بدلاً من toObject لضمان البيانات
                                            Account account = new Account();

                                            // --- تعبئة البيانات الأساسية يدوياً من الـ Document ---
                                            // نقرأ القيم المحتملة (مثلاً name أو accountName) لتجنب الأخطاء

                                            // الاسم
                                            String name = doc.getString("accountName");
                                            if (name == null)
                                                name = doc.getString("name"); // محاولة اسم بديل
                                            account.setAccountName(name != null ? name : "Unknown Account");
                                            account.setLastModified((long) doc.get("lastModified"));

                                            // العملة
                                            String ownerUID = doc.getString("ownerUID");
                                            account.setOwnerUID(ownerUID != null ? ownerUID : ""); // أو عملة افتراضية
                                            // التفاصيل والملاحظات
                                            String phoneNumber = doc.getString("phoneNumber");
                                            account.setPhoneNumber(phoneNumber != null ? phoneNumber : "");

                                            // رقم الهاتف والبريد
                                            account.setFirestoreId(doc.getId());
                                            account.setAcTypeFirestoreId(Objects.requireNonNull(doc.getString("acTypeFirestoreId")));

                                            // التواريخ (إذا كانت موجودة كنصوص أو TimeStamp)
                                            account.setDeletedBy(doc.getString("deletedBy"));

                                            // --- الحقول الإجبارية للنظام ---
                                            account.setFirestoreId(doc.getId());
                                            account.setOwnerUID(currentUserId);
                                            account.setSyncStatus("SYNCED");
                                            account.setDeleted(false);

                                            String typeName = doc.getString("accountType");//account.getAccountType(); // الاسم المسجل في الحساب
                                            String currentTypeId = doc.getString("acTypeFirestoreId"); //account.getAcTypeFirestoreId(); // المعرف المسجل (إن وجد)

                                            // إذا وجدنا ID وتطابق، نقوم بتحديث الحساب المحلي
                                            if (currentTypeId != null) {
                                                account.setAcTypeFirestoreId(currentTypeId);

                                                // إذا كان الحساب في السيرفر لا يملك هذا الـ ID، نجهزه للتحديث (Batch)

                                            } else {
                                                if (!doc.contains("acTypeFirestoreId") || doc.getString("acTypeFirestoreId") == null) {
                                                    currentTypeId = appDb.accountTypeDao().getAccountTypeFirestoreIdByName(typeName);
                                                    batch.update(doc.getReference(), "acTypeFirestoreId", currentTypeId);
                                                    needsBatchCommit = true;
                                                }
                                            }
                                            long lastModified = doc.contains("lastModified") ? doc.getLong("lastModified") : System.currentTimeMillis();
                                            long lastSyncDate = doc.contains("lastSyncDate") ? doc.getLong("lastSyncDate") : lastModified;
                                            secureManager.updateLastSyncTime(SecureLicenseManager.getAccountKey(), lastSyncDate);
                                            account.setLastModified(lastModified);
                                            account.setLastSyncDate(lastSyncDate);

                                            account.setAcTypeFirestoreId(currentTypeId);
                                            account.setAccountType(typeName);
                                            accountsToProcess.add(account);

                                        } catch (Exception e) {
                                            Log.e(TAG, "خطأ في قراءة بيانات الحساب: " + doc.getId(), e);
                                        }
                                    }

                                    // 4. تحديث السيرفر بالبيانات الناقصة (Types IDs)
                                    if (needsBatchCommit) {
                                        batch.commit().addOnFailureListener(e -> {
                                            Log.e(TAG, "فشل تحديث معرفات الأنواع", e);
                                            AppErrorHandler.handle(context);
                                        });
                                    }

                                    // 5. الحفظ في Room Database
                                    try {
                                        appDb.runInTransaction(() -> {
                                            for (Account acc : accountsToProcess) {
                                                try {
                                                    Account existing = appDb.accountDao().getAccountByFirestoreId(acc.getFirestoreId());
                                                    long rowId;

                                                    if (existing != null) {
                                                        acc.setId(existing.getId());
                                                        appDb.accountDao().update(acc);
                                                        rowId = existing.getId();
                                                    } else {
                                                        Account existingByName = appDb.accountDao().getAccountByName(acc.getAccountName());
                                                        if (existingByName != null) {
                                                            acc.setId(existingByName.getId());
                                                            appDb.accountDao().update(acc);
                                                            rowId = existingByName.getId();
                                                        } else {
                                                            rowId = appDb.accountDao().insert(acc);
                                                        }
                                                    }
                                                    accountFirestoreIds.add(acc.getFirestoreId());
                                                    firestoreToRoomIdMap.put(acc.getFirestoreId(), (int) rowId);
                                                } catch (Exception e) {
                                                    Log.e(TAG, "فشل إدراج الحساب محلياً", e);
                                                }
                                            }
                                        });

                                        Log.i(TAG, "تم استعادة " + accountsToProcess.size() + " حساب.");
                                        handler.post(() -> restoreCurrencies(listener, accountFirestoreIds, firestoreToRoomIdMap));

                                    } catch (Exception e) {
                                        Log.e(TAG, "فشل Transaction", e);
                                        handler.post(() -> listener.onError(context.getString(R.string.restoreAccounts_01) + e.getMessage()));
                                    }

                                } else {
                                    handler.post(() -> listener.onError(context.getString(R.string.restoreAccounts_02) + task.getException().getMessage()));
                                }
                            });

                        }).addOnFailureListener(e -> {
                            Log.e(TAG, "Restore Error", e);
                            AppErrorHandler.handle(context);
                        });
            } catch (Exception e) {
                Log.e(TAG, "Restore Error", e);
                handler.post(() -> listener.onError(e.getMessage()));
            }
        });
    }

    public void startRestore(RestoreListener listener) {
        executor.execute(() -> {
            try {
                if (currentUserId == null) {
                    throw new Exception(context.getString(R.string.startRestore_03));
                }
                // الخطوة الأولى: استيراد بيانات المستخدم والترخيص
                restoreUserProfile(listener);
            } catch (Exception e) {
                Log.e(TAG, "Restore from Firestore failed", e);
                handler.post(() -> listener.onError(e.getMessage()));
            }
        });
    }

    private void restoreTransactions(RestoreListener listener) {
        // نبدأ من 50% لأننا افترضنا أن المراحل السابقة (حسابات وعملات) أخذت النصف الأول
        handler.post(() -> listener.onProgressUpdate(context.getString(R.string.start_impoart_transactions), 50, 100));

        executor.execute(() -> {
            try {
                // 1. تجهيز خرائط الحسابات (FirestoreId -> LocalId)
                Map<String, Integer> localAccountMap = new HashMap<>();
                List<Account> accounts = appDb.accountDao().getAllAccounts();
                for (Account acc : accounts) {
                    if (acc.getFirestoreId() != null)
                        localAccountMap.put(acc.getFirestoreId(), acc.getId());
                }

                // 2. تجهيز خرائط العملات
                Map<String, Integer> localCurrencyMap = new HashMap<>();
                Map<String, Currency> localCurrencyByNameMap = new HashMap<>();
                List<Currency> currencies = appDb.currencyDao().getAllCurrenciesBlocking(); // تأكد أن هذه الدالة موجودة وتعود بـ List
                for (Currency curr : currencies) {
                    if (curr.getFirestoreId() != null)
                        localCurrencyMap.put(curr.getFirestoreId(), curr.id);
                    if (curr.name != null) localCurrencyByNameMap.put(curr.name.trim(), curr);
                }

                // 3. جلب العمليات من فايرستور
                firestore.collection("transactions")
                        .whereEqualTo("ownerUID", currentUserId)
                        .get()
                        .addOnCompleteListener(task -> {
                            executor.execute(() -> {
                                if (task.isSuccessful()) {
                                    QuerySnapshot snapshot = task.getResult();
                                    if (snapshot.isEmpty()) {
                                        handler.post(() -> listener.onSucces(context.getString(R.string.restoreTransactions_04)));
                                        return;
                                    }

                                    List<Transaction> transactionsToSync = new ArrayList<>();
                                    WriteBatch updateBatch = firestore.batch();
                                    boolean needsBatchCommit = false;

                                    // --- مرحلة التجهيز والمعالجة (سريعة في الذاكرة) ---
                                    for (QueryDocumentSnapshot doc : snapshot) {
                                        try {
                                            if (doc.contains("isDeleted") && Boolean.TRUE.equals(doc.getBoolean("isDeleted")))
                                                continue;

                                            Transaction t = new Transaction();
                                            String sAccountFId = doc.getString("accountFirestoreId");
                                            String sCurrencyFId = doc.getString("currencyFirestoreId");
                                            String sCurrencyName = doc.getString("currency");

                                            // ربط الحساب
                                            if (sAccountFId != null && localAccountMap.containsKey(sAccountFId)) {
                                                t.setAccountId(localAccountMap.get(sAccountFId));
                                            } else {
                                                Log.w(TAG, "تجاهل عملية لعدم وجود الحساب: " + doc.getId());
                                                continue;
                                            }

                                            // ربط العملة وتصحيح الناقص
                                            Integer resolvedCurrencyId = null;
                                            if (sCurrencyFId != null && localCurrencyMap.containsKey(sCurrencyFId)) {
                                                resolvedCurrencyId = localCurrencyMap.get(sCurrencyFId);
                                            } else if (sCurrencyName != null) {
                                                Currency foundCurrency = localCurrencyByNameMap.get(sCurrencyName.trim());
                                                if (foundCurrency != null) {
                                                    resolvedCurrencyId = foundCurrency.id;
                                                    updateBatch.update(doc.getReference(), "currencyFirestoreId", foundCurrency.getFirestoreId());
                                                    needsBatchCommit = true;
                                                }
                                            }

                                            if (resolvedCurrencyId != null) {
                                                t.setCurrencyId(resolvedCurrencyId);
                                            } else {
                                                // استخدام عملة افتراضية أو تخطي
                                                int currId = appDb.currencyDao().getFirstIdCurrency(); // تأكد من وجود هذه الدالة
                                                sCurrencyFId = appDb.currencyDao().getFirstFirestoreIdCurrency();
                                                t.setCurrencyId(currId);

                                            }

                                            // تعبئة البيانات
                                            t.setFirestoreId(doc.getId());
                                            t.setAccountFirestoreId(sAccountFId);
                                            t.setCurrencyFirestoreId(sCurrencyFId != null ? sCurrencyFId : doc.getString("currencyFirestoreId"));
                                            t.setOwnerUID(currentUserId);
                                            t.setImageEncoded(doc.getString("imageEncoded"));
                                            t.setSyncStatus("SYNCED"); // مهم جداً حتى لا يتم رفعها مرة أخرى كجديدة
                                            t.setDeleted(false);

                                            Double amount = doc.getDouble("amount");
                                            t.setAmount(amount != null ? amount : 0.0);

                                            Long type = doc.getLong("type");
                                            t.setType(type != null ? type.intValue() : -1);

                                            t.setDetails(doc.getString("details"));

                                            // معالجة التاريخ
                                            Object timestampObj = doc.get("timestamp");
                                            Date timestamp;
                                            if (timestampObj instanceof com.google.firebase.Timestamp) {
                                                timestamp = ((com.google.firebase.Timestamp) timestampObj).toDate();
                                            } else if (timestampObj instanceof Date) {
                                                timestamp = (Date) timestampObj;
                                            } else {
                                                timestamp = new Date();
                                            }
                                            t.setTimestamp(timestamp);

                                            // التعامل مع الحقول الإضافية
                                            if (doc.contains("lastModified"))
                                                t.setLastModified(doc.getLong("lastModified"));
                                            if (doc.contains("billType"))
                                                t.setBillType(doc.getString("billType"));
                                            if (doc.contains("importID"))
                                                t.setImportID(doc.getLong("importID") != null ? doc.getLong("importID").intValue() : 0);
                                            if (doc.contains("deletedBy"))
                                                t.setDeletedBy(doc.getString("deletedBy"));

                                            transactionsToSync.add(t);

                                        } catch (Exception e) {
                                            Log.e(TAG, "خطأ في معالجة المستند", e);
                                        }
                                    }

                                    if (needsBatchCommit) {
                                        updateBatch.commit();
                                    }

                                    // 4. الحفظ المحلي مع تحديث البروجرس (Loop inside Transaction)
                                    // تعريف المتغيرات خارج اللامبدا لاستخدامها في التحديث
                                    final int totalOps = transactionsToSync.size();
                                    final java.util.concurrent.atomic.AtomicInteger processedCount = new java.util.concurrent.atomic.AtomicInteger(0);

                                    try {
                                        appDb.runInTransaction(() -> {
                                            for (Transaction trans : transactionsToSync) {
                                                // المنطق: تحديث إذا وجد، إضافة إذا لم يوجد
                                                Transaction existing = appDb.transactionDao().getTransactionByFirestoreId(trans.getFirestoreId());
                                                if (existing != null) {
                                                    trans.setId(existing.getId());
                                                    appDb.transactionDao().update(trans);
                                                } else {
                                                    appDb.transactionDao().insert(trans);
                                                }

                                                // --- تحديث البروجرس ---
                                                int current = processedCount.incrementAndGet();

                                                // نحدث الواجهة كل 5 عمليات أو عند الوصول للنهاية لتخفيف الضغط على المعالج
                                                if (current % 5 == 0 || current == totalOps) {
                                                    // حساب النسبة المتبقية (من 50% إلى 100%)
                                                    int progress = 50 + (int) (((double) current / totalOps) * 50);
                                                    String msg = context.getString(R.string.impoart_transactions) + " " + current + "/" + totalOps;

                                                    handler.post(() -> listener.onProgressUpdate(msg, progress, 100));
                                                }
                                            }
                                        });

                                        // تم الانتهاء
                                        handler.post(() -> listener.onComplete(context.getString(R.string.restoreTransactions_05) + totalOps + context.getString(R.string.restoreTransactions_06)));

                                    } catch (Exception e) {
                                        Log.e(TAG, "فشل Transaction", e);
                                        handler.post(() -> listener.onError(context.getString(R.string.restoreTransactions_07) + e.getMessage()));
                                    }

                                } else {
                                    handler.post(() -> listener.onError(context.getString(R.string.restoreTransactions_08) + task.getException().getMessage()));
                                }
                            });
                        }).addOnFailureListener(e -> {
                            Log.e(TAG, "Restore Error", e);
                            AppErrorHandler.handle(context);
                        });
            } catch (Exception e) {
                handler.post(() -> listener.onError(e.getMessage()));
            }
        });
    }


    /**
     * كلاس مساعد لحفظ معلومات العملة
     */
    @Keep
    private static class CurrencyInfo {
        String symbol;
        String code;

        CurrencyInfo(String symbol, String code) {
            this.symbol = symbol;
            this.code = code;
        }
    }

    /**
     * كلاس مساعد لحفظ code و symbol
     */
    @Keep
    private static class CurrencyCodeSymbol {
        String code;
        String symbol;

        CurrencyCodeSymbol(String code, String symbol) {
            this.code = code;
            this.symbol = symbol;
        }
    }

    public void restoreAccountTypes1(RestoreListener listener) {
        Log.d(TAG, "بدء استعادة أنواع الحسابات...");
        handler.post(() -> listener.onProgressUpdate(context.getString(R.string.start_impoart_accountTypes), 5, 100));
        Map<String, String> typeNameToFirestoreIdMap = new HashMap<>();
        Set<String> processedTypeNames = new HashSet<>();
        firestore.collection("accountTypes")
                .whereEqualTo("ownerUID", currentUserId)
                .get()
                .addOnCompleteListener(task -> {
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    executor.execute(() -> {
                        if (task.isSuccessful()) {
                            QuerySnapshot snapshot = task.getResult();
                            if (snapshot != null) {
                                try {

                                    // 1. تجهيز البيانات المحلية للمقارنة (تجنب التكرار)
                                    List<AccountType> localTypes = appDb.accountTypeDao().getAccountTypes();

                                    Map<String, AccountType> localMapById = new HashMap<>();
                                    Map<String, AccountType> localMapByName = new HashMap<>();

                                    for (AccountType t : localTypes) {
                                        if (t.getFirestoreId() != null)
                                            localMapById.put(t.getFirestoreId(), t);
                                        if (t.name != null)
                                            localMapByName.put(t.name.trim().toLowerCase(), t);
                                    }

                                    // 2. التكرار على مستندات السيرفر
                                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                                        // أ) فحص الحذف (الطلب الجديد)
                                        if (doc.contains("isDeleted")) {
                                            if (convertFirestoreBoolean(doc.get("isDeleted")))
                                                continue;
                                        }

                                        // ب) القراءة المباشرة (حل مشكلة الريليز - Release Fix)
                                        String firestoreId = doc.getId();
                                        String name = doc.getString("name");
                                        Boolean isDefault = doc.getBoolean("isDefault");

                                        // حماية من البيانات الفاسدة
                                        if (name == null || name.trim().isEmpty()) continue;

                                        AccountType existing = null;

                                        // ج) منطق الدمج: البحث بالـ ID أولاً
                                        if (localMapById.containsKey(firestoreId)) {
                                            existing = localMapById.get(firestoreId);
                                        }
                                        // د) منطق الدمج: البحث بالاسم ثانياً
//                                        else if (localMapByName.containsKey(name.trim().toLowerCase())) {
//                                            existing = localMapByName.get(name.trim().toLowerCase());
//                                        }

                                        // هـ) التحديث أو الإضافة
                                        if (existing != null) {
                                            // تحديث
                                            existing.name = name;
                                            existing.setFirestoreId(firestoreId);
                                            existing.setOwnerUID(currentUserId);
                                            existing.setSyncStatus("SYNCED");
                                            if (doc.contains("isDefault()")) {
                                                isDefault = convertFirestoreBoolean(doc.get("isDefault()"));
                                                Map<String, Object> updates = new HashMap<>();
                                                updates.put("isDefault", isDefault);
                                                updates.put("isDefault()", FieldValue.delete());
                                                firestore.collection("accountTypes").document(doc.getId()).update(updates);

                                            } else {
                                                isDefault = convertFirestoreBoolean(doc.get("isDefault"));
                                            }
                                            existing.setDefault(isDefault);
                                            existing.setLastModified((long) doc.get("lastModified"));
                                            processedTypeNames.add(name);
                                            appDb.accountTypeDao().update(existing);

                                            typeNameToFirestoreIdMap.put(name, firestoreId);
                                        } else {
                                            // جديد
                                            AccountType newType = new AccountType();
                                            newType.name = name;
                                            newType.setFirestoreId(firestoreId);
                                            newType.setOwnerUID(currentUserId);
                                            newType.setSyncStatus("SYNCED");
                                            newType.setLastModified((long) doc.get("lastModified"));
                                            if (isDefault != null)
                                                newType.setDefault(isDefault);
                                            processedTypeNames.add(name);
                                            appDb.accountTypeDao().insert(newType);
                                            typeNameToFirestoreIdMap.put(name, firestoreId);
                                        }
                                    }

                                    // 4. الانتقال للخطوة التالية (استعادة الحسابات) وتمرير الـ Map
                                    Log.d(TAG, "تم الانتهاء من أنواع الحسابات، الانتقال للحسابات...");
                                    restoreAccounts(listener, typeNameToFirestoreIdMap);

                                } catch (Exception e) {
                                    Log.e(TAG, "خطأ أثناء معالجة أنواع الحسابات", e);
                                    if (listener != null)
                                        listener.onError(context.getString(R.string.fail_impoart_accountTypes) + e.getMessage());
                                }
                            } else {
                                // حتى لو كانت فارغة، ننتقل للخطوة التالية
                                addMissingAccountTypes(typeNameToFirestoreIdMap, processedTypeNames);
                                restoreAccounts(listener, typeNameToFirestoreIdMap);
                            }
                        } else {
                            Log.e(TAG, "فشل جلب أنواع الحسابات", task.getException());
                            if (listener != null)
                                listener.onError(context.getString(R.string.fail_impoart_accountTypes));
                        }
                    });
                }).addOnFailureListener(e -> {
                    Log.e(TAG, "Restore Error", e);
                    AppErrorHandler.handle(context);
                });
    }
    public void restoreAccountTypes(RestoreListener listener) {
        Log.d(TAG, "بدء استعادة أنواع الحسابات...");
        handler.post(() -> listener.onProgressUpdate(context.getString(R.string.start_impoart_accountTypes), 5, 100));
        Map<String, String> typeNameToFirestoreIdMap = new HashMap<>();
        Set<String> processedTypeNames = new HashSet<>();
        firestore.collection("accountTypes")
                .whereEqualTo("ownerUID", currentUserId)
                .get()
                .addOnCompleteListener(task -> {
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    executor.execute(() -> {
                        if (task.isSuccessful()) {
                            QuerySnapshot snapshot = task.getResult();
                            if (snapshot != null) {
                                try {

                                    // 1. تجهيز البيانات المحلية للمقارنة (تجنب التكرار)
                                    List<AccountType> localTypes = appDb.accountTypeDao().getAccountTypes();
//
//                                    Map<String, AccountType> localMapById = new HashMap<>();
//                                    Map<String, AccountType> localMapByName = new HashMap<>();
                                    int accountTypeoNos =0;
                                    accountTypeoNos = appDb.accountTypeDao().getAccountTypeCount();
                                    if (accountTypeoNos>0){
                                        appDb.accountTypeDao().deleteGuestData();
                                    }
                                    // 2. التكرار على مستندات السيرفر
                                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                                        long lastModified = doc.contains("lastModified") ? doc.getLong("lastModified") : System.currentTimeMillis();
                                        long lastSyncDate = doc.contains("lastSyncDate") ? doc.getLong("lastSyncDate") : lastModified;
                                        secureManager.updateLastSyncTime(SecureLicenseManager.getAccountTypeKey(), lastSyncDate);

                                        // أ) فحص الحذف (الطلب الجديد)
                                        if (doc.contains("isDeleted")) {
                                            if (convertFirestoreBoolean(doc.get("isDeleted")))
                                                continue;
                                        }

                                        // ب) القراءة المباشرة (حل مشكلة الريليز - Release Fix)
                                        String firestoreId = doc.getId();
                                        String name = doc.getString("name");
                                        Boolean isDefault = doc.getBoolean("isDefault");

                                        // حماية من البيانات الفاسدة
                                        if (name == null || name.trim().isEmpty()) continue;

                                        AccountType existing = null;

                                        // ج) منطق الدمج: البحث بالـ ID أولاً
//                                        if (localMapById.containsKey(firestoreId)) {
//                                            existing = localMapById.get(firestoreId);
//                                        }
                                        // د) منطق الدمج: البحث بالاسم ثانياً
//                                        else if (localMapByName.containsKey(name.trim().toLowerCase())) {
//                                            existing = localMapByName.get(name.trim().toLowerCase());
//                                        }

                                        // هـ) التحديث أو الإضافة
                                        if (existing != null) {
                                            // تحديث
                                            existing.name = name;
                                            existing.setFirestoreId(firestoreId);
                                            existing.setOwnerUID(currentUserId);
                                            existing.setSyncStatus("SYNCED");
                                            if (doc.contains("isDefault()")) {
                                                isDefault = convertFirestoreBoolean(doc.get("isDefault()"));
                                                Map<String, Object> updates = new HashMap<>();
                                                updates.put("isDefault", isDefault);
                                                updates.put("isDefault()", FieldValue.delete());
                                                firestore.collection("accountTypes").document(doc.getId()).update(updates);

                                            } else {
                                                isDefault = convertFirestoreBoolean(doc.get("isDefault"));
                                            }
                                            existing.setDefault(isDefault);
                                            existing.setLastModified((long) doc.get("lastModified"));
                                            existing.setLastSyncDate(lastSyncDate);
                                            processedTypeNames.add(name);
                                            appDb.accountTypeDao().update(existing);

                                            typeNameToFirestoreIdMap.put(name, firestoreId);
                                        } else {
                                            // جديد
                                            AccountType newType = new AccountType();
                                            newType.name = name;
                                            newType.setFirestoreId(firestoreId);
                                            newType.setOwnerUID(currentUserId);
                                            newType.setSyncStatus("SYNCED");
                                            newType.setLastModified((long) doc.get("lastModified"));
                                            newType.setLastSyncDate(lastSyncDate);
                                            if (isDefault != null)
                                                newType.setDefault(isDefault);
                                            processedTypeNames.add(name);
                                            appDb.accountTypeDao().insert(newType);
                                            typeNameToFirestoreIdMap.put(name, firestoreId);
                                        }
                                    }

                                    // 4. الانتقال للخطوة التالية (استعادة الحسابات) وتمرير الـ Map
                                    Log.d(TAG, "تم الانتهاء من أنواع الحسابات، الانتقال للحسابات...");
                                    restoreAccounts(listener, typeNameToFirestoreIdMap);

                                } catch (Exception e) {
                                    Log.e(TAG, "خطأ أثناء معالجة أنواع الحسابات", e);
                                    if (listener != null)
                                        listener.onError(context.getString(R.string.fail_impoart_accountTypes) + e.getMessage());
                                }
                            } else {
                                // حتى لو كانت فارغة، ننتقل للخطوة التالية
                                addMissingAccountTypes(typeNameToFirestoreIdMap, processedTypeNames);
                                restoreAccounts(listener, typeNameToFirestoreIdMap);
                            }
                        } else {
                            Log.e(TAG, "فشل جلب أنواع الحسابات", task.getException());
                            if (listener != null)
                                listener.onError(context.getString(R.string.fail_impoart_accountTypes));
                        }
                    });
                }).addOnFailureListener(e -> {
                    Log.e(TAG, "Restore Error", e);
                    AppErrorHandler.handle(context);
                });
    }

    // دالة مساعدة لإضافة الأنواع المفقودة
    private void addMissingAccountTypes(Map<String, String> typeNameToFirestoreIdMap, Set<String> processedTypeNames) {
        try {
            Resources localizedResources = LanguageManager.getLocalizedResources(context);
            String[] requiredTypes = {
                    localizedResources.getString(R.string.account_type_customer),
                    localizedResources.getString(R.string.account_type_supplier),
                    localizedResources.getString(R.string.account_type_general)
            };

            for (String requiredType : requiredTypes) {
                if (!processedTypeNames.contains(requiredType)) {
                    AccountType localType = appDb.accountTypeDao().getAccountTypeByName(requiredType);

                    if (localType == null) {
                        // إنشاء نوع حساب جديد
                        AccountType newType = new AccountType();
                        newType.setName(requiredType);
                        newType.setOwnerUID(currentUserId);
                        newType.setDefault(true);
                        newType.setSyncStatus("NEW");
                        newType.setLastModified(System.currentTimeMillis());

                        String firestoreId = UUIDGenerator.generateSequentialUUID();
                        newType.setFirestoreId(firestoreId);

                        appDb.accountTypeDao().insert(newType);
                        typeNameToFirestoreIdMap.put(requiredType, firestoreId);
                        processedTypeNames.add(requiredType);
                        Log.i(TAG, "تم إنشاء نوع حساب افتراضي: " + requiredType);
                    } else {
                        typeNameToFirestoreIdMap.put(requiredType, localType.getFirestoreId());
                        processedTypeNames.add(requiredType);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "فشل في إضافة الأنواع المفقودة", e);
        }
    }

    private void ensureDefaultCurrency(Map<String, String> currencyFirestoreIdMap, Set<String> processedCurrencyNames) {
        try {
            String defaultCurrencyName = MyApplication.defaultCurrencyName;
            if (defaultCurrencyName == null || defaultCurrencyName.isEmpty()) {
                defaultCurrencyName = context.getString(R.string.local_currency); // قيمة افتراضية
            }

            if (!processedCurrencyNames.contains(defaultCurrencyName)) {
                Currency localCurrency = appDb.currencyDao().getCurrencyByName(defaultCurrencyName);

                if (localCurrency == null) {
                    // إنشاء العملة الافتراضية
                    Currency newCurrency = new Currency();
                    newCurrency.setName(defaultCurrencyName);
                    newCurrency.setOwnerUID(currentUserId);
                    newCurrency.setDefault(true);
                    newCurrency.setSyncStatus("NEW");
                    newCurrency.setLastModified(System.currentTimeMillis());

                    // ⭐ استخدام generateCurrencyCodeAndSymbol لتعيين code و symbol
                    CurrencyCodeSymbol generated = generateCurrencyCodeAndSymbol(defaultCurrencyName);
                    newCurrency.setCode(generated.code);
                    newCurrency.setSymbol(generated.symbol);

                    String firestoreId = UUIDGenerator.generateSequentialUUID();
                    newCurrency.setFirestoreId(firestoreId);

                    appDb.currencyDao().insert(newCurrency);
                    currencyFirestoreIdMap.put(defaultCurrencyName, firestoreId);
                    Log.i(TAG, "تم إنشاء العملة الافتراضية: " + defaultCurrencyName +
                            " - code: " + generated.code + ", symbol: " + generated.symbol);
                } else {
                    // ⭐ التأكد من أن العملة المحلية الافتراضية تحتوي على code و symbol
                    if (localCurrency.getCode() == null || localCurrency.getCode().isEmpty() ||
                            localCurrency.getSymbol() == null || localCurrency.getSymbol().isEmpty()) {

                        CurrencyCodeSymbol generated = generateCurrencyCodeAndSymbol(defaultCurrencyName);
                        localCurrency.setCode(generated.code);
                        localCurrency.setSymbol(generated.symbol);
                        localCurrency.setLastModified(System.currentTimeMillis());
                        appDb.currencyDao().update(localCurrency);
                        Log.i(TAG, "تم تحديث code و symbol للعملة الافتراضية المحلية: " + defaultCurrencyName);
                    }
                    currencyFirestoreIdMap.put(defaultCurrencyName, localCurrency.getFirestoreId());
                }
            }

            new SyncPreferences(context).setLocalCurrency(defaultCurrencyName);
            Log.d(TAG, "العملة الافتراضية: " + defaultCurrencyName);

        } catch (Exception e) {
            Log.e(TAG, "فشل في التأكد من العملة الافتراضية", e);
        }
    }

    /**
     * دالة لتحويل Boolean من Firestore إلى boolean محلي
     * مع التعامل مع جميع أنواع القيم (Boolean, String, Integer, null)
     */
    private boolean convertFirestoreBoolean(Object firestoreBool) {
        if (firestoreBool == null) {
            return false; // القيمة الافتراضية إذا كانت null
        }

        if (firestoreBool instanceof Boolean) {
            return (Boolean) firestoreBool;
        }

        // إذا كانت String
        if (firestoreBool instanceof String) {
            String str = ((String) firestoreBool).toLowerCase().trim();
            return str.equals("true") || str.equals("1") || str.equals(context.getString(R.string.convertFirestoreBoolean_10)) || str.equals("yes");
        }

        // إذا كانت Integer أو Long
        if (firestoreBool instanceof Number) {
            return ((Number) firestoreBool).intValue() == 1;
        }

        // محاولة تحويل عامة
        try {
            return Boolean.parseBoolean(firestoreBool.toString());
        } catch (Exception e) {
            return false; // القيمة الافتراضية في حالة الخطأ
        }
    }


    private CurrencyCodeSymbol generateCurrencyCodeAndSymbol2(String currencyName) {
        if (currencyName == null || currencyName.trim().isEmpty()) {
            return new CurrencyCodeSymbol("UNK", "?");
        }
        String cleanName = currencyName.trim();
        String code;
        String symbol;

        boolean isArabic = cleanName.matches(".*[\\u0600-\\u06FF]+.*");
        if (isArabic) {
            String[] parts = cleanName.split("\\s+");
            if (parts.length >= 2) {
                char firstChar = parts[0].charAt(0);
                char secondChar = parts[1].charAt(0);
                code = firstChar + "." + secondChar;
            } else {
                // اسم من كلمة واحدة: أول حرفين مع نقطة
                code = cleanName.substring(0, 1) + "." + (cleanName.length() > 1 ? cleanName.substring(1, 2) : "");
            }
            symbol = cleanName;
        } else {
            // إنجليزي: أول 3 حروف كبيرة
            code = cleanName.length() >= 3 ? cleanName.substring(0, 3).toUpperCase() : cleanName.toUpperCase();
            symbol = cleanName;
        }
        return new CurrencyCodeSymbol(code, symbol);
    }

    private void restoreCurrencies2(RestoreListener listener, List<String> accountsToProcess, Map<String, Integer> accountTypesMap) {
        handler.post(() -> listener.onProgressUpdate(context.getString(R.string.start_impoart_currencies), 30, 100));

        firestore.collection("currencies")
                .whereEqualTo("ownerUID", currentUserId)
                .get()
                .addOnCompleteListener(task -> {
                    executor.execute(() -> {
                        if (task.isSuccessful()) {
                            QuerySnapshot snapshot = task.getResult();
                            if (snapshot != null && !snapshot.isEmpty()) {

                                // تجهيز الخرائط المحلية قبل الـ Loop
                                List<Currency> localCurrencies = appDb.currencyDao().getAllCurrenciesBlocking();
                                Map<String, Currency> localCurrencyByFirestoreId = new HashMap<>();
                                Map<String, Currency> localCurrencyByName = new HashMap<>();

                                for (Currency c : localCurrencies) {
                                    if (c.getFirestoreId() != null && !c.getFirestoreId().isEmpty()) {
                                        localCurrencyByFirestoreId.put(c.getFirestoreId(), c);
                                    }
                                    if (c.getName() != null) {
                                        localCurrencyByName.put(c.getName().trim(), c);
                                    }
                                }

                                // نستخدم runInTransaction لتسريع عمليتي insert و update المتكررة
                                appDb.runInTransaction(() -> {
                                    for (QueryDocumentSnapshot doc : snapshot) {
                                        boolean isDeleted = doc.contains("isDeleted") && Boolean.TRUE.equals(doc.getBoolean("isDeleted"));
                                        if (isDeleted) continue;

                                        String firestoreId = doc.getId();
                                        String name = doc.getString("name");
                                        String ownerUID = doc.getString("ownerUID");
                                        if (ownerUID == null || ownerUID.isEmpty())
                                            ownerUID = currentUserId;

                                        String code = doc.getString("code");
                                        String symbol = doc.getString("symbol");

                                        // توليد الرموز المفقودة محلياً فقط
                                        if (code == null || code.trim().isEmpty() || symbol == null || symbol.trim().isEmpty()) {
                                            CurrencyCodeSymbol generated = generateCurrencyCodeAndSymbol(name);
                                            if (code == null || code.trim().isEmpty())
                                                code = generated.code;
                                            if (symbol == null || symbol.trim().isEmpty())
                                                symbol = generated.symbol;
                                        }

                                        boolean isDefault = doc.contains("isDefault") && Boolean.TRUE.equals(doc.getBoolean("isDefault"));
                                        long lastModified = doc.contains("lastModified") ? doc.getLong("lastModified") : System.currentTimeMillis();

                                        Currency existingCurrency = localCurrencyByFirestoreId.get(firestoreId);
                                        if (existingCurrency == null) {
                                            existingCurrency = localCurrencyByName.get(name != null ? name.trim() : "");
                                        }

                                        if (existingCurrency != null) {
                                            existingCurrency.setName(name);
                                            existingCurrency.setCode(code);
                                            existingCurrency.setSymbol(symbol);
                                            existingCurrency.setFirestoreId(firestoreId);
                                            existingCurrency.setOwnerUID(ownerUID);
                                            existingCurrency.setLastModified(lastModified);
                                            existingCurrency.setSyncStatus("SYNCED");
                                            existingCurrency.setDefault(isDefault);
                                            appDb.currencyDao().update(existingCurrency);
                                        } else {
                                            Currency newCurrency = new Currency();
                                            newCurrency.setName(name);
                                            newCurrency.setCode(code);
                                            newCurrency.setSymbol(symbol);
                                            newCurrency.setFirestoreId(firestoreId);
                                            newCurrency.setOwnerUID(ownerUID);
                                            newCurrency.setLastModified(lastModified);
                                            newCurrency.setSyncStatus("SYNCED");
                                            newCurrency.setDefault(isDefault);
                                            appDb.currencyDao().insert(newCurrency);
                                        }
                                    }
                                });
                            }

                            // بعد الانتهاء، ننتقل لجلب العمليات ونمرر المتغير كنص (String)
                            handler.post(() -> restoreTransactions(listener, accountsToProcess));

                        } else {
                            handler.post(() -> listener.onError(context.getString(R.string.restoreCurrencies_11) + task.getException().getMessage()));
                        }
                    });
                });
    }

    private void restoreTransactions2(RestoreListener listener, List<String> processedAccountsIds) {
        handler.post(() -> listener.onProgressUpdate(context.getString(R.string.start_impoart_transactions), 50, 100));

        executor.execute(() -> {
            // 1. جلب الحسابات المحلية مباشرة من قاعدة البيانات لبناء الخريطة
            // (ملاحظة: تأكد من أن اسم الدالة في AccountDao هو getAllAccounts أو غيرها حسب كودك)
            List<Account> allLocalAccounts = appDb.accountDao().getAllAccounts();
            Map<String, Integer> accountFirestoreToLocalId = new HashMap<>();
            for (Account acc : allLocalAccounts) {
                if (acc.getFirestoreId() != null && !acc.getFirestoreId().isEmpty()) {
                    accountFirestoreToLocalId.put(acc.getFirestoreId(), acc.getId());
                }
            }

            List<Currency> allLocalCurrencies = appDb.currencyDao().getAllCurrenciesBlocking();
            Map<String, Integer> currencyFirestoreToLocalId = new HashMap<>();
            Map<Integer, String> localIdToFirestoreId = new HashMap<>();

            for (Currency curr : allLocalCurrencies) {
                if (curr.getFirestoreId() != null && !curr.getFirestoreId().isEmpty()) {
                    currencyFirestoreToLocalId.put(curr.getFirestoreId(), curr.getId());
                    localIdToFirestoreId.put(curr.getId(), curr.getFirestoreId());
                }
            }

            // 2. استدعاء الدالة التكرارية لجلب الدفعات
            fetchTransactionsChunk(listener, accountFirestoreToLocalId, currencyFirestoreToLocalId, localIdToFirestoreId, allLocalCurrencies, null, 0);
        });
    }

    private void fetchTransactionsChunk2(RestoreListener listener, Map<String, Integer> accountMap, Map<String, Integer> currFirestoreToLocal, Map<Integer, String> currLocalToFirestore, List<Currency> allCurrencies, DocumentSnapshot lastVisible, int totalImported) {

        // جلب 500 عملية في كل دفعة لمنع الانهيار
        Query query = firestore.collection("transactions")
                .whereEqualTo("ownerUID", currentUserId)
                .limit(500);

        if (lastVisible != null) {
            query = query.startAfter(lastVisible);
        }

        query.get().addOnCompleteListener(task -> {
            executor.execute(() -> {
                if (task.isSuccessful()) {
                    QuerySnapshot snapshot = task.getResult();
                    if (snapshot == null || snapshot.isEmpty()) {
                        handler.post(() -> listener.onComplete(context.getString(R.string.fetchTransactionsChunk_12)));
                        return;
                    }

                    // تنفيذ الإدخال بسرعة باستخدام Transaction المحلي
                    appDb.runInTransaction(() -> {
                        for (QueryDocumentSnapshot doc : snapshot) {
                            boolean isDeleted = doc.contains("isDeleted") && Boolean.TRUE.equals(doc.getBoolean("isDeleted"));
                            if (isDeleted) continue;

                            Transaction trans = new Transaction();
                            trans.setFirestoreId(doc.getId());
                            trans.setOwnerUID(currentUserId);
                            trans.setSyncStatus("SYNCED");

                            trans.setDetails(doc.getString("details"));
                            trans.setAmount(doc.getDouble("amount") != null ? doc.getDouble("amount") : 0.0);
                            trans.setType(doc.getLong("type") != null ? doc.getLong("type").intValue() : 0);
                            trans.setBillType(doc.getString("billType"));
                            trans.setImageEncoded(doc.getString("imageEncoded"));

                            if (doc.contains("timestamp")) {
                                com.google.firebase.Timestamp ts = doc.getTimestamp("timestamp");
                                if (ts != null) trans.setTimestamp(ts.toDate());
                            }

                            // === ربط الحساب ===
                            String accountFirestoreId = doc.getString("accountFirestoreId");
                            trans.setAccountFirestoreId(accountFirestoreId != null ? accountFirestoreId : "");
                            Integer localAccId = accountMap.get(trans.getAccountFirestoreId());
                            if (localAccId != null) {
                                trans.setAccountId(localAccId);
                            } else {
                                continue; // لا يمكن ربط العملية بحساب مجهول
                            }

                            // === ربط العملة ===
                            String currencyFirestoreId = doc.getString("currencyFirestoreId");
                            int oldCurrencyId = doc.getLong("currencyId") != null ? doc.getLong("currencyId").intValue() : -1;

                            // إذا كان currencyFirestoreId خالي، نستنتجه من القديم
                            if (currencyFirestoreId == null || currencyFirestoreId.trim().isEmpty()) {
                                currencyFirestoreId = currLocalToFirestore.get(oldCurrencyId);
                            }

                            Integer validLocalCurrencyId = null;
                            if (currencyFirestoreId != null && !currencyFirestoreId.isEmpty()) {
                                trans.setCurrencyFirestoreId(currencyFirestoreId);
                                validLocalCurrencyId = currFirestoreToLocal.get(currencyFirestoreId);
                            }

                            // إسناد عملة افتراضية لمنع الكراش
                            if (validLocalCurrencyId == null && !allCurrencies.isEmpty()) {
                                validLocalCurrencyId = allCurrencies.get(0).getId();
                                trans.setCurrencyFirestoreId(allCurrencies.get(0).getFirestoreId());
                            } else if (validLocalCurrencyId == null) {
                                continue;
                            }

                            trans.setCurrencyId(validLocalCurrencyId);

                            // حفظ العملية باستخدام دالة insert الفردية المعتادة (لكنها سريعة جداً لأنها داخل Transaction)
                            appDb.transactionDao().insert(trans);
                        }
                    });

                    int newTotal = totalImported + snapshot.size();
                    handler.post(() -> listener.onProgressUpdate(context.getString(R.string.fetchTransactionsChunk_13) + newTotal + context.getString(R.string.fetchTransactionsChunk_14), 50, 100));

                    // فحص إذا كان هناك المزيد من العمليات (الدفعة كانت ممتلئة بـ 500)
                    if (snapshot.size() == 500) {
                        DocumentSnapshot lastDoc = snapshot.getDocuments().get(snapshot.size() - 1);
                        fetchTransactionsChunk(listener, accountMap, currFirestoreToLocal, currLocalToFirestore, allCurrencies, lastDoc, newTotal);
                    } else {
                        handler.post(() -> listener.onComplete(context.getString(R.string.fetchTransactionsChunk_15)));
                    }

                } else {
                    handler.post(() -> listener.onError(context.getString(R.string.fetchTransactionsChunk_16) + task.getException().getMessage()));
                }
            });
        });
    }

    /**
     * دالة استعادة العملات المعدلة
     * تحافظ على البارامترات المطلوبة وتقوم بدمج البيانات حسب الترتيب
     */
    private void restoreCurrencies45(RestoreListener listener, List<String> accountFirestoreIds, Map<String, Integer> firestoreToRoomIdMap) {
        // نستخدم OrderBy لضمان ثبات الترتيب عند الاستيراد
        handler.post(() -> listener.onProgressUpdate(context.getString(R.string.start_impoart_currencies), 30, 100));

        // جلب العملات من Firestore مرتبة حسب firestoreId لضمان ثبات الترتيب
        firestore.collection("currencies")
                .whereEqualTo("ownerUID", currentUserId)
                .orderBy("firestoreId", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    executor.execute(() -> {
                        try {
                            handleCurrenciesRestore45(queryDocumentSnapshots);

                            // العودة للرئيسية لإكمال العملية
                            new Handler(Looper.getMainLooper()).post(() -> {
                                // استدعاء الخطوة التالية (TransactionRestore) أو إنهاء العملية حسب المنطق الخاص بك
                                // هنا نفترض الانتقال لاستعادة المعاملات كما هو في تسلسل الاستعادة لديك
                                restoreTransactions(listener);
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "فشل في جلب العملات", e);
                            handler.post(() -> listener.onError(context.getString(R.string.fail_impoart_currencies) + e.getMessage()));

                        }
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, context.getString(R.string.currency_transaction_error), e);
                    handler.post(() -> listener.onError(context.getString(R.string.currency_transaction_error) + e.getMessage()));

                });
    }

    private void handleCurrenciesRestore45(QuerySnapshot cloudSnapshots) {
        List<DocumentSnapshot> cloudList = cloudSnapshots.getDocuments();

        // جلب العملات المحلية مرتبة بنفس معيار الاستعلام (غالباً بالـ ID)
        List<Currency> localCurrencies = appDb.currencyDao().getAllCurrenciesBlocking();

        int localSize = localCurrencies.size();
        int cloudSize = cloudList.size();

        for (int i = 0; i < cloudSize; i++) {
            DocumentSnapshot doc = cloudList.get(i);
            if (doc.contains("isDeleted")) {
                if (convertFirestoreBoolean(doc.get("isDeleted"))) continue;
            }
            // --- 1. القراءة اليدوية (لتفادي مشاكل ProGuard مع toObject) ---

            // معالجة مشكلة الاسم الفارغ (فحص الحقلين المحتملين)
            String name = doc.getString("name");
            if (name == null || name.trim().isEmpty()) {
                name = doc.getString("currencyName");
            }
            // إذا بقي الاسم فارغاً، نحاول استنتاجه من الكود إن وجد
            if (name == null) name = "";
            name = name.trim();

            // قراءة باقي القيم
            double value = 1.0;
            Double valObj = doc.getDouble("value");
            if (valObj != null) value = valObj;

            String code = doc.getString("code");
            String symbol = doc.getString("symbol");
            String firestoreId = doc.getId();

            // قراءة حالة الحذف والمزامنة
            boolean isDeleted = convertFirestoreBoolean(doc.get("isDeleted"));

            // --- 2. توليد الرموز الناقصة (محلياً فقط) ---
            // إذا جاءت العملة من السيرفر بدون كود/رمز (بسبب خطأ سابق)، نولده الآن للتخزين المحلي
            if ((code == null || code.isEmpty() || symbol == null || symbol.isEmpty()) && !name.isEmpty()) {
                Currency template = findMatchingCurrencyLocal(name);
                if (code == null || code.isEmpty()) code = template.code;
                if (symbol == null || symbol.isEmpty()) symbol = template.symbol;
            }

            // ضمان عدم وجود Null
            if (code == null) code = "";
            if (symbol == null) symbol = "";
            boolean isDefault;
            if (doc.contains("isDefault()")) {
                isDefault = convertFirestoreBoolean(doc.get("isDefault()"));
                Map<String, Object> updates = new HashMap<>();
                updates.put("isDefault", isDefault);
                updates.put("isDefault()", FieldValue.delete());
                firestore.collection("currencies").document(doc.getId()).update(updates);

            } else {
                isDefault = convertFirestoreBoolean(doc.get("isDefault"));
            }
            // --- 3. المنطق الأساسي: تحديث الموجود أو إضافة جديد ---
            if (i < localSize) {
                // === السيناريو أ: تحديث الصف المحلي الحالي (حسب الترتيب) ===
                Currency localCurrency = localCurrencies.get(i);

                // لا نحدث العملة إذا كانت محذوفة محلياً ومزامنة بالفعل، إلا إذا أردنا فرض السيرفر
                localCurrency.name = name;
                localCurrency.code = code;
                localCurrency.symbol = symbol;

                localCurrency.setDeleted(convertFirestoreBoolean(doc.get("isDeleted")));
                localCurrency.setDeletedBy(doc.getString("deletedBy") != null ? doc.getString("deletedBy") : "");
                localCurrency.setDefault(isDefault);
                localCurrency.setFirestoreId(firestoreId);
                localCurrency.setSyncStatus("SYNCED"); // نعتبرها متزامنة
                localCurrency.setLastModified((long) doc.get("lastModified"));

                appDb.currencyDao().update(localCurrency);
                Log.d(TAG, "Updated existing local currency at index " + i + ": " + name);

            } else {
                // === السيناريو ب: عدد العملات المستوردة أكبر من المحلية -> إضافة صف جديد ===
                Currency newCurrency = new Currency();
                newCurrency.name = name;
                newCurrency.code = code;
                newCurrency.symbol = symbol;
                newCurrency.setFirestoreId(firestoreId);
                newCurrency.setSyncStatus("SYNCED");
                newCurrency.setDeleted(convertFirestoreBoolean(doc.get("isDeleted")));
                newCurrency.setDeletedBy(doc.getString("deletedBy") != null ? doc.getString("deletedBy") : "");
                newCurrency.setDefault(isDefault);
                // المالك وغيرها من الحقول الافتراضية
                String owner = doc.getString("ownerUID");
                newCurrency.setOwnerUID(owner != null ? owner : currentUserId);
                newCurrency.setLastModified((long) doc.get("lastModified"));
                appDb.currencyDao().insert(newCurrency);
                Log.d(TAG, "Inserted new currency: " + name);
            }
        }
    }

    private void handleCurrenciesRestore2(QuerySnapshot cloudSnapshots) {
        List<DocumentSnapshot> cloudList = cloudSnapshots.getDocuments();

        // 1. جلب العملات المحلية وبناء خريطة firestoreId -> Currency
        List<Currency> localCurrencies = appDb.currencyDao().getAllCurrenciesBlocking();
        Map<String, Currency> localMapById = new HashMap<>();
        for (Currency c : localCurrencies) {
            if (c.getFirestoreId() != null) {
                localMapById.put(c.getFirestoreId(), c);
            }
        }

        // 2. معالجة مستندات السيرفر
        boolean foundDefault = false;
        Currency firstCurrency = null;

        for (DocumentSnapshot doc : cloudList) {
            // تجاهل المحذوف
            if (doc.contains("isDeleted") && convertFirestoreBoolean(doc.get("isDeleted")))
                continue;

            String firestoreId = doc.getId();
            String name = doc.getString("name");
            if (name == null || name.trim().isEmpty()) {
                name = doc.getString("currencyName");
            }
            if (name == null) name = "";
            name = name.trim();

            String code = doc.getString("code");
            String symbol = doc.getString("symbol");

            // معالجة الحقل القديم isDefault()
            boolean isDefault = convertFirestoreBoolean(doc.get("isDefault"));
            if (doc.contains("isDefault()")) {
                isDefault = convertFirestoreBoolean(doc.get("isDefault()"));
                // تحديث السيرفر لإزالة الحقل القديم
                firestore.collection("currencies").document(firestoreId)
                        .update("isDefault", isDefault, "isDefault()", FieldValue.delete());
            }

            // إنشاء code و symbol إذا كانا مفقودين
            if ((code == null || code.isEmpty()) || (symbol == null || symbol.isEmpty())) {
                CurrencyCodeSymbol generated = generateCurrencyCodeAndSymbol(name);
                if (code == null || code.isEmpty()) code = generated.code;
                if (symbol == null || symbol.isEmpty()) symbol = generated.symbol;
            }

            // البحث في المحلي بواسطة firestoreId
            Currency existing = localMapById.get(firestoreId);
            if (existing != null) {
                // تحديث العملة الموجودة
                existing.name = name;
                existing.code = code;
                existing.symbol = symbol;
                existing.setDeleted(convertFirestoreBoolean(doc.get("isDeleted")));
                existing.setDeletedBy(doc.getString("deletedBy") != null ? doc.getString("deletedBy") : "");
                existing.setDefault(isDefault);
                existing.setFirestoreId(firestoreId);
                existing.setSyncStatus("SYNCED");
                existing.setLastModified((long) doc.get("lastModified"));
                appDb.currencyDao().update(existing);
                if (isDefault) foundDefault = true;
                if (firstCurrency == null) firstCurrency = existing;
            } else {
                // إضافة عملة جديدة
                Currency newCurrency = new Currency();
                newCurrency.name = name;
                newCurrency.code = code;
                newCurrency.symbol = symbol;
                newCurrency.setFirestoreId(firestoreId);
                newCurrency.setSyncStatus("SYNCED");
                newCurrency.setDeleted(convertFirestoreBoolean(doc.get("isDeleted")));
                newCurrency.setDeletedBy(doc.getString("deletedBy") != null ? doc.getString("deletedBy") : "");
                newCurrency.setDefault(isDefault);
                String owner = doc.getString("ownerUID");
                newCurrency.setOwnerUID(owner != null ? owner : currentUserId);
                newCurrency.setLastModified((long) doc.get("lastModified"));
                appDb.currencyDao().insert(newCurrency);  // ✅ تم إزالة محاولة تخزين القيمة المرجعة
                if (isDefault) foundDefault = true;
                if (firstCurrency == null) firstCurrency = newCurrency;
            }
        }

        // 3. ضمان وجود عملة افتراضية
        if (!foundDefault && !cloudList.isEmpty()) {
            // جعل أول عملة (في ترتيب السيرفر) افتراضية
            DocumentSnapshot firstDoc = cloudList.get(0);
            String firstFirestoreId = firstDoc.getId();
            Currency firstCurr = localMapById.get(firstFirestoreId);
            if (firstCurr != null) {
                firstCurr.setDefault(true);
                appDb.currencyDao().update(firstCurr);
                firestore.collection("currencies").document(firstFirestoreId).update("isDefault", true);
            }
        } else if (cloudList.isEmpty()) {
            // لا توجد عملات في السيرفر، أنشئ عملة افتراضية محليًا
            ensureDefaultCurrencyIfNeeded();
        }
    }

    private void ensureDefaultCurrencyIfNeeded() {
        List<Currency> localCurrencies = appDb.currencyDao().getAllCurrenciesBlocking();
        if (localCurrencies.isEmpty()) {
            String defaultCurrencyName = MyApplication.defaultCurrencyName;
            if (defaultCurrencyName == null || defaultCurrencyName.isEmpty()) {
                defaultCurrencyName = context.getString(R.string.local_currency);
            }

            Currency newCurrency = new Currency();
            newCurrency.setName(defaultCurrencyName);
            newCurrency.setOwnerUID(currentUserId);
            newCurrency.setDefault(true);
            newCurrency.setSyncStatus("NEW");
            newCurrency.setLastModified(System.currentTimeMillis());
            CurrencyCodeSymbol generated = generateCurrencyCodeAndSymbol(defaultCurrencyName);
            newCurrency.setCode(generated.code);
            newCurrency.setSymbol(generated.symbol);
            newCurrency.setFirestoreId(UUIDGenerator.generateSequentialUUID());
            appDb.currencyDao().insert(newCurrency);
        } else {
            boolean hasDefault = false;
            for (Currency c : localCurrencies) {
                if (c.isDefault()) {
                    hasDefault = true;
                    break;
                }
            }
            if (!hasDefault) {
                Currency first = localCurrencies.get(0);
                first.setDefault(true);
                appDb.currencyDao().update(first);
            }
        }
    }

    private Currency findMatchingCurrencyLocal(String name) {
        Currency c = new Currency();
        c.code = "";
        c.symbol = "";
        if (name == null) return c;
        String currencySymbol = "﷼";
        String cleanName = name.toLowerCase().trim();

        if (cleanName.contains("yemen") || cleanName.contains("يمني") || cleanName.contains("ريال")) {
            c.code = "YER";
            c.symbol = "﷼";
        } else if (cleanName.contains("saudi") || cleanName.contains("سعودي")) {
            c.code = "SAR";
            c.symbol = "﷼";
        } else if (cleanName.contains("emirat") || cleanName.contains("درهم اماراتي") || cleanName.contains("اماراتي")) {
            c.code = "AED";
            c.symbol = "د.إ";
        } else if (cleanName.contains("iraq") || cleanName.contains("دينار عراقي") || cleanName.contains("عراقي")) {
            c.code = "IQD";
            c.symbol = "د.ع";
        } else if (cleanName.contains("syrian") || cleanName.contains("ليرة سوري") || cleanName.contains("سوري")) {
            c.code = "SYP";
            c.symbol = "ل.س";
        } else if (cleanName.contains("jordanian") || cleanName.contains("دينار اردني") || cleanName.contains("اردني")) {
            c.code = "JOD";
            c.symbol = "د.إ";

        } else if (cleanName.contains("dollar") || cleanName.contains("دولار") || cleanName.contains("usd")) {
            c.code = "USD";
            c.symbol = "$";
        } else if (cleanName.contains("euro") || cleanName.contains("يورو")) {
            c.code = "EUR";
            c.symbol = "€";
        } else if (cleanName.contains("egypt") || cleanName.contains("جنية مصري") || cleanName.contains("مصري")) {
            c.code = "EGP";
            c.symbol = "ج.م";
        }
        // يمكن إضافة المزيد من العملات هنا

        // إذا فشل البحث بالاسم، نستخدم أول 3 حروف كرمز (كحل أخير)
        if (c.code.isEmpty() && cleanName.length() >= 3) {
            // فحص بسيط للأحرف الإنجليزية فقط لتجنب وضع رموز عربية في خانة الكود
            if (cleanName.matches(".*[a-zA-Z]+.*")) {
                c.code = cleanName.substring(0, 3).toUpperCase();
                c.symbol = c.code;
            }
        }

        return c;
    }

    /**
     * دالة مساعدة لتوليد code و symbol للعملة مع التعرف على الأسماء المختلفة
     */
    private CurrencyCodeSymbol generateCurrencyCodeAndSymbol1(String currencyName) {
        if (currencyName == null || currencyName.trim().isEmpty()) {
            return new CurrencyCodeSymbol("UNK", "?");
        }

        // أولاً: محاولة التعرف على العملة من خلال الخريطة
        CurrencyInfo recognizedCurrency = findMatchingCurrency(currencyName, createCurrencyMapping());
        if (recognizedCurrency != null) {
            return new CurrencyCodeSymbol(recognizedCurrency.code, recognizedCurrency.symbol);
        }

        // إذا لم يتم التعرف على العملة، نستخدم المنطق المبسط
        String cleanName = currencyName.trim();
        String code;
        String symbol;

        // قراءة اللغة من SharedPreferences
        SharedPreferences prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        String savedLanguage = prefs.getString("language", "en");

        if (savedLanguage.equals("ar") && cleanName.length() >= 2) {
            // للعربية: أول حرفين مع نقطة بينهما
            symbol = cleanName.substring(0, 1) + "." + cleanName.substring(1, 2);
            code = cleanName.length() >= 3 ? cleanName.substring(0, 3).toUpperCase() : cleanName.toUpperCase();
        } else {
            // للإنجليزية: أول 3 أحرف كرمز وكود
            code = cleanName.length() >= 3 ? cleanName.substring(0, 3).toUpperCase() : cleanName.toUpperCase();
            symbol = code;
        }

        return new CurrencyCodeSymbol(code, symbol);
    }

    /**
     * خريطة تحتوي على جميع الأسماء المحتملة للعملات مع رموزها وأكوادها
     */
    private Map<String, CurrencyInfo> createCurrencyMapping() {
        Map<String, CurrencyInfo> map = new HashMap<>();
        String currencySymbol = "﷼";
        // العملات العربية
        addCurrencyVariants(map, currencySymbol, "ر.س",
                "ريال سعودي", "ريال السعودي", "سعودي", "ريال سعودي", "الريال السعودي", "sar", "riyal", "saudi");

        addCurrencyVariants(map, currencySymbol, "ر.ي",
                "ريال يمني", "ريال اليمن", "يمني", "ريال يمني", "الريال اليمني", "yer", "yemeni", "yemen", "yemen rial", "yemeni rial");

        addCurrencyVariants(map, "ج.م", "EGP",
                "جنيه مصري", "جنيه مصرى", "مصري", "egp", "egyptian", "egypt");

        addCurrencyVariants(map, "د.إ", "AED",
                "درهم إماراتي", "درهم اماراتي", "درهم", "aed", "uae", "emirati");

        addCurrencyVariants(map, "د.ك", "KWD",
                "دينار كويتي", "دينار", "kwd", "kuwaiti", "kuwait");

        addCurrencyVariants(map, "د.ع", "IQD",
                "دينار عراقي", "عراقي", "iqd", "iraqi", "iraq");

        addCurrencyVariants(map, "ل.س", "SYP",
                "ليرة سورية", "ليرة سوريا", "ليرة", "syp", "syrian", "syria");

        addCurrencyVariants(map, "د.أ", "JOD",
                "دينار أردني", "دينار اردني", "أردني", "jod", "jordanian", "jordan");

        addCurrencyVariants(map, currencySymbol, "ر.ق",
                "ريال قطري", "قطري", "qar", "qatari", "qatar");

        addCurrencyVariants(map, "د.ب", "BHD",
                "دينار بحريني", "بحريني", "bhd", "bahraini", "bahrain");

        addCurrencyVariants(map, currencySymbol, "ر.ع",
                "ريال عماني", "عماني", "omr", "omani", "oman");

        addCurrencyVariants(map, "ل.ل", "LBP",
                "ليرة لبنانية", "لبناني", "lbp", "lebanese", "lebanon");

        // العملات الدولية
        addCurrencyVariants(map, "$", "USD",
                "دولار أمريكي", "دولار امريكي", "دولار", "دولار أمريكان", "دولار امريكان",
                "usd", "dollar", "american", "united states");

        addCurrencyVariants(map, "€", "EUR",
                "يورو", "eur", "euro");

        addCurrencyVariants(map, "£", "GBP",
                "جنيه إسترليني", "جنيه استرليني", "جنيه", "gbp", "pound", "british");

        addCurrencyVariants(map, "¥", "JPY",
                "ين ياباني", "ين", "jpy", "yen", "japanese");

        addCurrencyVariants(map, "₹", "INR",
                "روبية هندية", "روبية", "inr", "rupee", "indian");

        addCurrencyVariants(map, "₺", "TRY",
                "ليرة تركية", "تركي", "try", "turkish", "turkey");

        addCurrencyVariants(map, currencySymbol, "SAR",
                "sar", "saudi riyal", "saudi rial", "riyal"); // إضافة اختصارات إنجليزية

        addCurrencyVariants(map, currencySymbol, "YER",
                "yer", "yemen riyal", "yemeni riyal", "yemeni rial"); // إضافة اختصارات إنجليزية

        return map;
    }

    /**
     * إضافة متغيرات الأسماء للعملة في الخريطة
     */
    private void addCurrencyVariants(Map<String, CurrencyInfo> map, String symbol, String code, String... names) {
        CurrencyInfo info = new CurrencyInfo(symbol, code);
        for (String name : names) {
            map.put(normalizeName(name), info);
        }
    }

    /**
     * تطبيع اسم العملة لإزالة الهمزات والتباينات
     */
    private String normalizeName(String name) {
        if (name == null) return "";

        // إزالة الهمزات والتشكيل
        String normalized = name
                .replaceAll("[إأآا]", "ا")
                .replaceAll("ى", "ي")
                .replaceAll("ة", "ه")
                .replaceAll("ئ", "ي")
                .replaceAll("ؤ", "و")
                .replaceAll("\\p{M}", "") // إزالة التشكيل
                .replaceAll("\\s+", " ") // إزالة المسافات الزائدة
                .trim()
                .toLowerCase();

        return normalized;
    }

    private CurrencyInfo findMatchingCurrency(String name, Map<String, CurrencyInfo> currencyMap) {
        if (name == null) return null;

        // محاولة المطابقة المباشرة
        String normalized = normalizeName(name);
        CurrencyInfo exactMatch = currencyMap.get(normalized);
        if (exactMatch != null) {
            Log.d(TAG, "تم التعرف على العملة بالمطابقة المباشرة: " + name + " -> " + exactMatch.code);
            return exactMatch;
        }
        // مطابقة جزئية
        for (Map.Entry<String, CurrencyInfo> entry : currencyMap.entrySet()) {
            if (normalized.contains(entry.getKey()) || entry.getKey().contains(normalized)) {
                Log.d(TAG, "تم التعرف على العملة بالمطابقة الجزئية: " + name + " -> " + entry.getValue().code);
                return entry.getValue();
            }
        }

        Log.d(TAG, "لم يتم التعرف على العملة: " + name + "، سيتم استخدام المنطق المبسط");
        return null;
    }

    /**
     * دالة استعادة العملات - بالمنطق الاحترافي (الإحلال حسب الترتيب لحماية العمليات)
     */
    private void restoreCurrencies(RestoreListener listener, List<String> accountFirestoreIds, Map<String, Integer> firestoreToRoomIdMap) {
        // نستخدم OrderBy لضمان ثبات الترتيب عند الاستيراد تصاعدياً
        handler.post(() -> listener.onProgressUpdate(context.getString(R.string.start_impoart_currencies), 30, 100));

        firestore.collection("currencies")
                .whereEqualTo("ownerUID", currentUserId)
                .orderBy("id", Query.Direction.ASCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    executor.execute(() -> {
                        if (task.isSuccessful()) {
                            QuerySnapshot snapshot = task.getResult();
                            if (snapshot != null && !snapshot.isEmpty()) {
                                handleCurrenciesRestore(snapshot);
                            } else {
                                addCurrencyToDatabase(MyApplication.defaultCurrencyName,
                                        MyApplication.defaultCurrencySymbol,
                                        MyApplication.defaultCurrencyCode);

                            }
                            // العودة للرئيسية لإكمال العملية واستيراد العمليات
                            handler.post(() -> restoreTransactions(listener, accountFirestoreIds));

                        } else {
                            Log.e(TAG, "فشل في جلب العملات", task.getException());
                            handler.post(() -> listener.onError(context.getString(R.string.fail_impoart_currencies) + task.getException().getMessage()));
                        }
                    });
                }).addOnFailureListener(e -> {
                    Log.e(TAG, "Restore Error", e);
                    AppErrorHandler.handle(context);
                });
    }

    private void handleCurrenciesRestore(QuerySnapshot cloudSnapshots) {
        List<DocumentSnapshot> cloudList = cloudSnapshots.getDocuments();

        // جلب العملات المحلية مرتبة بنفس معيار الاستعلام (لضمان الإحلال الصحيح حسب الـ Index)
        List<Currency> localCurrencies = appDb.currencyDao().getAllCurrenciesBlocking();

        int localSize = localCurrencies.size();
        int cloudSize = cloudList.size();

        // تنفيذ العمليات في Transaction لتسريع الحفظ المحلي
        appDb.runInTransaction(() -> {
            for (int i = 0; i < cloudSize; i++) {
                DocumentSnapshot doc = cloudList.get(i);

                if (doc.contains("isDeleted")) {
                    if (convertFirestoreBoolean(doc.get("isDeleted"))) continue;
                }

                // --- قراءة البيانات الأساسية ---
                String name = doc.getString("name");
                if (name == null || name.trim().isEmpty()) {
                    name = doc.getString("currencyName");
                }
                if (name == null) name = "";
                name = name.trim();

                String code = doc.getString("code");
                String symbol = doc.getString("symbol");
                String firestoreId = doc.getId();

                // توليد الرموز الناقصة (محلياً فقط) باستخدام دالتك العالمية
                if (code == null || code.trim().isEmpty() || symbol == null || symbol.trim().isEmpty()) {
                    CurrencyCodeSymbol generated = generateCurrencyCodeAndSymbol(name);
                    if (code == null || code.trim().isEmpty()) code = generated.code;
                    if (symbol == null || symbol.trim().isEmpty()) symbol = generated.symbol;
                }

                boolean isDefault = false;
                if (doc.contains("isDefault()")) {
                    isDefault = convertFirestoreBoolean(doc.get("isDefault()"));
                    // تحديث الفايرستور بالصيغة الصحيحة في الخلفية
//                    Map<String, Object> updates = new HashMap<>();
//                    updates.put("isDefault", isDefault);
//                    updates.put("isDefault()", FieldValue.delete());
//                    firestore.collection("currencies").document(doc.getId()).update(updates);
                } else {
                    isDefault = convertFirestoreBoolean(doc.get("isDefault"));
                }

                long lastModified = doc.contains("lastModified") ? doc.getLong("lastModified") : System.currentTimeMillis();
                long lastSyncDate = doc.contains("lastSyncDate") ? doc.getLong("lastSyncDate") : lastModified;
                secureManager.updateLastSyncTime(SecureLicenseManager.getCurrencyKey(), lastSyncDate);

                String owner = doc.getString("ownerUID");
                String finalOwner = (owner != null && !owner.isEmpty()) ? owner : currentUserId;

                // --- المنطق الأساسي والمهم: الإحلال أو الإضافة ---
                if (i < localSize) {
                    // السيناريو أ: يوجد صف محلي بهذا الترتيب -> نقوم بتحديثه وطمس العملة القديمة تماماً
                    Currency localCurrency = localCurrencies.get(i);
                    localCurrency.setName(name);
                    localCurrency.setCode(code);
                    localCurrency.setSymbol(symbol);
                    localCurrency.setDeleted(convertFirestoreBoolean(doc.get("isDeleted")));
                    localCurrency.setDeletedBy(doc.getString("deletedBy") != null ? doc.getString("deletedBy") : "");
                    localCurrency.setDefault(isDefault);
                    localCurrency.setFirestoreId(firestoreId);
                    localCurrency.setSyncStatus("SYNCED");
                    localCurrency.setLastModified(lastModified);
                    localCurrency.setOwnerUID(finalOwner);
                    localCurrency.setLastSyncDate(lastSyncDate);
                    appDb.currencyDao().update(localCurrency);
                    Log.d(TAG, "تم تحديث العملة المحلية بالرقم " + i + " إلى العملة المستوردة: " + name);

                } else {
                    // السيناريو ب: عدد العملات المستوردة أكبر من المحلية -> إضافة صف جديد
                    Currency newCurrency = new Currency();
                    newCurrency.setName(name);
                    newCurrency.setCode(code);
                    newCurrency.setSymbol(symbol);
                    newCurrency.setFirestoreId(firestoreId);
                    newCurrency.setSyncStatus("SYNCED");
                    newCurrency.setDeleted(convertFirestoreBoolean(doc.get("isDeleted")));
                    newCurrency.setDeletedBy(doc.getString("deletedBy") != null ? doc.getString("deletedBy") : "");
                    newCurrency.setDefault(isDefault);
                    newCurrency.setOwnerUID(finalOwner);
                    newCurrency.setLastModified(lastModified);
                    newCurrency.setLastSyncDate(lastSyncDate);
                    appDb.currencyDao().insert(newCurrency);
                    Log.d(TAG, "تم إضافة عملة جديدة: " + name);
                }
            }
        });
    }

    /**
     * استيراد العمليات وتقسيمها إلى دفعات (Chunking) مع الحفاظ على الربط القديم
     */
    private void restoreTransactions(RestoreListener listener, List<String> processedAccountsIds) {
        handler.post(() -> listener.onProgressUpdate(context.getString(R.string.start_impoart_transactions), 50, 100));

        executor.execute(() -> {
            // جلب الحسابات لتكوين خريطة الربط
            List<Account> allLocalAccounts = appDb.accountDao().getAllAccounts();
            Map<String, Integer> accountFirestoreToLocalId = new HashMap<>();
            for (Account acc : allLocalAccounts) {
                if (acc.getFirestoreId() != null && !acc.getFirestoreId().isEmpty()) {
                    accountFirestoreToLocalId.put(acc.getFirestoreId(), acc.getId());
                }
            }

            // جلب العملات (بعد أن تم إحلالها وترتيبها في الخطوة السابقة) لتكوين خرائط الربط العكسي
            List<Currency> allLocalCurrencies = appDb.currencyDao().getAllCurrenciesBlocking();
            Map<String, Integer> currencyFirestoreToLocalId = new HashMap<>();
            Map<Integer, String> localIdToFirestoreId = new HashMap<>();

            for (Currency curr : allLocalCurrencies) {
                if (curr.getFirestoreId() != null && !curr.getFirestoreId().isEmpty()) {
                    currencyFirestoreToLocalId.put(curr.getFirestoreId(), curr.getId());
                    localIdToFirestoreId.put(curr.getId(), curr.getFirestoreId());
                }
            }

            // استدعاء دالة الجلب بالتقسيم
            fetchTransactionsChunk(listener, accountFirestoreToLocalId, currencyFirestoreToLocalId, localIdToFirestoreId, allLocalCurrencies, null, 0);
        });
    }

    private void fetchTransactionsChunk(RestoreListener listener, Map<String, Integer> accountMap, Map<String, Integer> currFirestoreToLocal, Map<Integer, String> currLocalToFirestore, List<Currency> allCurrencies, DocumentSnapshot lastVisible, int totalImported) {

        Query query = firestore.collection("transactions")
                .whereEqualTo("ownerUID", currentUserId)
                .limit(500);

        if (lastVisible != null) {
            query = query.startAfter(lastVisible);
        }

        query.get()
                .addOnCompleteListener(task -> {
                    executor.execute(() -> {
                        if (task.isSuccessful()) {
                            QuerySnapshot snapshot = task.getResult();
                            if (snapshot == null || snapshot.isEmpty()) {
                                handler.post(() -> listener.onComplete(context.getString(R.string.fetchTransactionsChunk_12)));
                                return;
                            }

                            appDb.runInTransaction(() -> {
                                for (QueryDocumentSnapshot doc : snapshot) {
                                    boolean isDeleted = doc.contains("isDeleted") && Boolean.TRUE.equals(doc.getBoolean("isDeleted"));
                                    if (isDeleted) continue;

                                    Transaction trans = new Transaction();
                                    trans.setFirestoreId(doc.getId());
                                    trans.setOwnerUID(currentUserId);
                                    trans.setSyncStatus("SYNCED");
                                    trans.setDetails(doc.getString("details"));
                                    trans.setAmount(doc.getDouble("amount") != null ? doc.getDouble("amount") : 0.0);
                                    trans.setType(doc.getLong("type") != null ? doc.getLong("type").intValue() : 0);
                                    trans.setBillType(doc.getString("billType"));
                                    trans.setImageEncoded(doc.getString("imageEncoded"));

                                    if (doc.contains("timestamp")) {
                                        com.google.firebase.Timestamp ts = doc.getTimestamp("timestamp");
                                        if (ts != null) trans.setTimestamp(ts.toDate());
                                    }

                                    long lastModified = doc.contains("lastModified") ? doc.getLong("lastModified") : System.currentTimeMillis();
                                    long lastSyncDate = doc.contains("lastSyncDate") ? doc.getLong("lastSyncDate") : lastModified;
                                    secureManager.updateLastSyncTime(SecureLicenseManager.getTransactionKey(), lastSyncDate);
                                    trans.setLastSyncDate(lastSyncDate);
                                    trans.setLastModified(lastModified);

                                    if (doc.contains("importID"))
                                        trans.setImportID(doc.getLong("importID") != null ? doc.getLong("importID").intValue() : 0);
                                    if (doc.contains("deletedBy"))
                                        trans.setDeletedBy(doc.getString("deletedBy"));

                                    // === ربط الحساب ===
                                    String accountFirestoreId = doc.getString("accountFirestoreId");
                                    trans.setAccountFirestoreId(accountFirestoreId != null ? accountFirestoreId : "");
                                    Integer localAccId = accountMap.get(trans.getAccountFirestoreId());
                                    if (localAccId != null) {
                                        trans.setAccountId(localAccId);
                                    } else {
                                        continue;
                                    }

                                    // === ربط العملة ===
                                    String currencyFirestoreId = doc.getString("currencyFirestoreId");
                                    int oldCurrencyId = doc.getLong("currencyId") != null ? doc.getLong("currencyId").intValue() : -1;

                                    if (currencyFirestoreId == null || currencyFirestoreId.trim().isEmpty()) {
                                        currencyFirestoreId = currLocalToFirestore.get(oldCurrencyId);
                                    }

                                    Integer validLocalCurrencyId = null;
                                    if (currencyFirestoreId != null && !currencyFirestoreId.isEmpty()) {
                                        trans.setCurrencyFirestoreId(currencyFirestoreId);
                                        validLocalCurrencyId = currFirestoreToLocal.get(currencyFirestoreId);
                                    }

                                    if (validLocalCurrencyId == null && !allCurrencies.isEmpty()) {
                                        validLocalCurrencyId = allCurrencies.get(0).getId();
                                        trans.setCurrencyFirestoreId(allCurrencies.get(0).getFirestoreId());
                                    } else if (validLocalCurrencyId == null) {
                                        continue;
                                    }

                                    trans.setCurrencyId(validLocalCurrencyId);

                                    // استخدام دالة الإضافة والتحديث
                                    Transaction existing = appDb.transactionDao().getTransactionByFirestoreId(trans.getFirestoreId());
                                    if (existing != null) {
                                        trans.setId(existing.getId());
                                        appDb.transactionDao().update(trans);
                                    } else {
                                        appDb.transactionDao().insert(trans);
                                    }
                                }
                            });

                            int newTotal = totalImported + snapshot.size();
                            handler.post(() -> listener.onProgressUpdate(context.getString(R.string.fetchTransactionsChunk_13) + " " + newTotal, 50, 100));

                            if (snapshot.size() == 500) {
                                DocumentSnapshot lastDoc = snapshot.getDocuments().get(snapshot.size() - 1);
                                fetchTransactionsChunk(listener, accountMap, currFirestoreToLocal, currLocalToFirestore, allCurrencies, lastDoc, newTotal);
                            } else {
                                handler.post(() -> listener.onComplete(context.getString(R.string.fetchTransactionsChunk_15)));
                            }

                        } else {
                            handler.post(() -> listener.onError(context.getString(R.string.fetchTransactionsChunk_16) + task.getException().getMessage()));
                        }

                    });
                }).addOnFailureListener(e -> {
                    Log.e(TAG, "Restore Error", e);
                    AppErrorHandler.handle(context);
                });
    }

    /**
     * الدالة العالمية لتوليد الرموز
     */
    private CurrencyCodeSymbol generateCurrencyCodeAndSymbol(String currencyName) {
        if (currencyName == null || currencyName.trim().isEmpty()) {
            return new CurrencyCodeSymbol("UNK", "?");
        }
        String cleanName = currencyName.trim();
        String code;
        String symbol;

        boolean isArabic = cleanName.matches(".*[\\u0600-\\u06FF]+.*");
        if (isArabic) {
            String[] parts = cleanName.split("\\s+");
            if (parts.length >= 2) {
                char firstChar = parts[0].charAt(0);
                char secondChar = parts[1].charAt(0);
                code = firstChar + "." + secondChar;
            } else {
                code = cleanName.substring(0, 1) + "." + (cleanName.length() > 1 ? cleanName.substring(1, 2) : "");
            }
            symbol = cleanName;
        } else {
            code = cleanName.length() >= 3 ? cleanName.substring(0, 3).toUpperCase() : cleanName.toUpperCase();
            symbol = cleanName;
        }
        return new CurrencyCodeSymbol(code, symbol);
    }

    private void addCurrencyToDatabase(String name, String symbol, String code) {
        String uid = currentUserId;
        AppDatabase.databaseWriteExecutor.execute(() -> {
            //    int currencyCount = db.currencyDao().getCurrencyCount();
            if (appDb.currencyDao().getCurrencyCount() < 1) {

                Currency newCurrency = new Currency();
                newCurrency.setName(name);
                newCurrency.setOwnerUID(uid);
                newCurrency.setSyncStatus("NEW");
                newCurrency.setSymbol(symbol);
                newCurrency.setCode(code);
                newCurrency.setDefault(true);
                newCurrency.setFirestoreId(UUIDGenerator.generateSequentialUUID().toString());
                newCurrency.setLastModified(System.currentTimeMillis());
                new SyncPreferences(context).setLocalCurrency(name.trim());
                MyApplication.defaultCurrencyName = (name.trim());
                appDb.currencyDao().insert(newCurrency);


                Resources localizedResources = LanguageManager.getLocalizedResources(context);
                String nameDollar = localizedResources.getString(R.string._408);

                Currency newCurrencyD = new Currency();
                newCurrencyD.setName(nameDollar);
                newCurrencyD.setOwnerUID(uid);
                newCurrencyD.setSymbol("$");
                newCurrencyD.setCode("USD");
                newCurrencyD.setSyncStatus("NEW");
                newCurrencyD.setDefault(false);
                newCurrencyD.setFirestoreId(UUIDGenerator.generateSequentialUUID().toString());
                newCurrencyD.setLastModified(System.currentTimeMillis());
                appDb.currencyDao().insert(newCurrencyD);


            }

        });
    }
}
