package com.hpp.daftree.syncmanagers;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.hpp.daftree.models.AppAuth;
import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.utils.AppErrorHandler;
import com.hpp.daftree.utils.RewardManager;
import com.hpp.daftree.utils.SecureLicenseManager;
import com.hpp.daftree.utils.VersionManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirestoreSyncManager {
    private static final String TAG = "FirestoreSyncManager";
    private static FirestoreSyncManager instance;
    private final Context context;
    private final FirebaseFirestore firestore;
    private ListenerRegistration accountsListener;
    private ListenerRegistration transactionsListener;
    private ListenerRegistration licenseListener;
    private DaftreeRepository repository;
    //    private  RewardManager rewardManager;
    private static String notiMessege = "";
    private static String notiMessegeTitel = "";
    private ReferralNotificationListener referralListener;
    private final Map<String, Boolean> processedNotifs = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private List<Map<String, Object>> pendingAccountChanges = new ArrayList<>();
    private List<Map<String, Object>> pendingTransactionChanges = new ArrayList<>();
    private ListenerRegistration currenciesListener;
    private ListenerRegistration accountTypesListener;
    private ListenerRegistration usersListener;
    private RefreshMainActivity refreshMainActivity;
    private final Map<String, Long> lastKnownRewards = new HashMap<>();
    public static volatile boolean isSyncWorkerRunning = false;

    private List<Map<String, Object>> pendingCurrencyChanges = new ArrayList<>();
    private List<Map<String, Object>> pendingAccountTypeChanges = new ArrayList<>();
    private List<Map<String, Object>> pendingUserChanges = new ArrayList<>();

    // فترة التجميع (500 مللي ثانية)
    private static final long BATCH_DELAY_MS = 500;
    private static final int MAX_FREE_MULTI_DEVICE_SYNC_LIMIT = 100;
    private final SecureLicenseManager secureManager;
    private final VersionManager versionManager;

    private FirestoreSyncManager(Context context) {
        this.firestore = FirebaseFirestore.getInstance();
        this.context = context.getApplicationContext();
        this.secureManager = SecureLicenseManager.getInstance(context);
        this.versionManager = new VersionManager(context);
        // rewardManager= new RewardManager();
    }

    public static synchronized FirestoreSyncManager getInstance(Context context) {

        if (instance == null) {
            instance = new FirestoreSyncManager(context);

        }
        return instance;
    }

    public static FirestoreSyncManager getInstance() {
        if (instance == null) {
            instance = new FirestoreSyncManager(MyApplication.getContext());
        }
        return instance;
    }

    public void setReferralNotificationListener(ReferralNotificationListener listener) {
        this.referralListener = listener;
    }

    /**
     * يبدأ الاستماع للتغييرات فقط.
     *
     * @param repo       المستودع المحلي
     * @param context    سياق التطبيق للحصول على الموارد
     * @param onComplete يُستدعى مباشرة بعد ربط المستمعين
     */
    public void startListening(DaftreeRepository repo, Context context, Runnable onComplete) {
        this.repository = repo;
        boolean isGuest = SecureLicenseManager.getInstance(context).isGuest();
        if (isGuest) {
            if (onComplete != null) onComplete.run();
            return;
        }
        int minVersion = secureManager.getMinVersion();
        int currentVersion = versionManager.getCurrentVersionCode();
        if (currentVersion < minVersion) {
            if (onComplete != null) onComplete.run();
            return;
        }
        String userId = AppAuth.getUserID(context);// FirebaseAuth.getInstance().getUid();
        if (userId == null) {
            Log.e(TAG, "المستخدم الحالي غير مسجل الدخول.");
            if (onComplete != null) onComplete.run();
            return;
        }

        // تنظيف أي مستمعين سابقين قبل إعادة الربط
        stopListening();
        int devicesNos = SecureLicenseManager.getInstance(context).getDevicesNos();
//        syncListenForReferralNotifications(userId, context, () -> {
        listenForReferralNotifications(userId, context);
//            listenForUserChanges(userId);
//        });
        if (devicesNos > 1) {
            if (isSyncWorkerRunning) return;

            syncAccountsFirst(userId, () -> {
                syncCurrencies(userId, () -> {
                    syncTransactions(userId, () -> {
                        listenForTransactionChanges(userId);
                        listenForCurrencyChanges(userId);
                        listenForAccountChanges(userId);
                        listenForAccountTypeChanges(userId);
                    });
                });
            });
        }
        Log.d(TAG, "تم تفعيل مستمعي المزامنة.");
        if (onComplete != null) onComplete.run();
    }
    private boolean checkAndIncrementSyncLimit(Context context, int changeSize) {
        SecureLicenseManager secureManager = SecureLicenseManager.getInstance(context);

        // الشرط: مستخدم عادي (ليس بريميوم) ولديه أكثر من جهاز واحد
        if (!secureManager.isPremium() && secureManager.getDevicesNos() > 1) {
            android.content.SharedPreferences prefs = context.getSharedPreferences("sync_limit_prefs", Context.MODE_PRIVATE);
            String todayStr = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
            String lastSyncDate = prefs.getString("last_sync_date", "");
            int currentCount = prefs.getInt("today_sync_count", 0);

            // تصفير العداد إذا بدأ يوم جديد
            if (!todayStr.equals(lastSyncDate)) {
                currentCount = 0;
                prefs.edit().putString("last_sync_date", todayStr).putInt("today_sync_count", 0).apply();
            }

            if (currentCount >= MAX_FREE_MULTI_DEVICE_SYNC_LIMIT) {
                Log.w(TAG, "🚫 تم حجب المزامنة: تجاوز الحد اليومي (100 عملية) للمستخدمين المجانيين بمتعدد الأجهزة.");
                return false;
            }

            // تحديث العداد بعدد العمليات الحالية
            prefs.edit().putInt("today_sync_count", currentCount + changeSize).apply();
        }
        return true;
    }

    public void listenForReferralNotifications(String currentUserUid, Context context) {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        ListenerRegistration referralNotifListener = firestore.collection("referral_notifications")
                .whereEqualTo("targetUid", currentUserUid)
                .whereEqualTo("processed", false)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    Log.d(TAG, "New referral reward .");
                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        if (dc.getType() != DocumentChange.Type.ADDED) continue;

                        String notifId = dc.getDocument().getId();
                        Log.d(TAG, "notifId: " + notifId);

                        if (processedNotifs.containsKey(notifId)) continue;
                        processedNotifs.put(notifId, true); // Mark as being processed immediately

                        long points = dc.getDocument().getLong("points") != null ? dc.getDocument().getLong("points") : 0;
                        if (points == 0) continue;
                        Log.d(TAG, "points: " + points);
                        DocumentReference userRef = firestore.collection("users").document(currentUserUid);
                        DocumentReference notifRef = dc.getDocument().getReference();

                        firestore.runTransaction(transaction -> {
                            DocumentSnapshot userSnapshot = transaction.get(userRef);

                            if (!userSnapshot.exists()) {
                                throw new IllegalStateException("User document does not exist!");
                            }

                            long currentSuccessfulReferrals = userSnapshot.contains("successfulReferrals") ?
                                    userSnapshot.getLong("successfulReferrals") : 0;
                            long currentReferrals = userSnapshot.contains("referral_rewards") ?
                                    userSnapshot.getLong("referral_rewards") : 0;
                            long newTotalReferrals = currentSuccessfulReferrals + 1;
                            Log.d(TAG, "currentReferrals" + currentReferrals);

                            // Calculate milestone bonus using the new static method
                            int milestoneBonus = RewardManager.checkForMilestoneRewards((int) newTotalReferrals);

                            long totalRewardForThisReferral = points + milestoneBonus;
                            long newReferrals = 0;


                            // Prepare notification messages
                            if (milestoneBonus > 0) {
                                notiMessegeTitel = context.getString(R.string.referral_reward_title); // "🎉 مكافأة إضافية!"
                                notiMessege = context.getString(R.string.referral_reward_bonus_message, (points), (milestoneBonus));
                                newReferrals = currentReferrals + 2 * points;
                            } else {
                                notiMessegeTitel = context.getString(R.string.referral_reward_title); // "🎉 مكافأة دعوة"
                                notiMessege = context.getString(R.string.referral_reward_message, (points));
                                newReferrals = currentReferrals + points;
                            }
// Update user's rewards and referral count
                            transaction.update(userRef, "referral_rewards", newReferrals);
                            transaction.update(userRef, "successfulReferrals", newTotalReferrals);
                            transaction.update(userRef, "lastModified", System.currentTimeMillis());

                            Log.d(TAG, "تم تحديث النقاط - المستخدم: " + currentUserUid + "\n" +
                                    ", currentReferrals: " + currentReferrals + "\n" +
                                    ", newReferrals: " + newReferrals);
                            // Mark notification as processed
                            transaction.update(notifRef, "processed", true);
                            return null;
                        }).addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Successfully processed referral reward and updated user stats.");
                            if (referralListener != null) {
                                // Use the final calculated messages to show UI feedback
                                referralListener.onReferralRewardReceived(currentUserUid, points, notiMessegeTitel, notiMessege);
                            }
                        }).addOnFailureListener(ex -> {
                            Log.e(TAG, "Failed to process referral reward transaction.", ex);
                            processedNotifs.remove(notifId); // Allow reprocessing if transaction fails;
                            AppErrorHandler.handle(context);
                        });
                    }
                });
    }

    private void listenForAccountChanges(String userId) {
        if (userId == null) return;

        SecureLicenseManager licenseManager = SecureLicenseManager.getInstance(context);
        long lastSync = licenseManager.getLastSyncTime(SecureLicenseManager.getAccountKey());

        Query query = firestore.collection("accounts")
                .whereEqualTo("ownerUID", userId)
                .whereGreaterThan("lastModified", lastSync);

        accountsListener = query.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.e(TAG, "Accounts listen failed", e);
                return;
            }

            long maxTimestamp = lastSync;

            for (DocumentChange dc : snapshots.getDocumentChanges()) {
                String firestoreId = dc.getDocument().getId();
                Map<String, Object> data = dc.getDocument().getData();

                if (data != null && data.containsKey("lastModified")) {
                    Object timeObj = data.get("lastModified");
                    if (timeObj instanceof Number) {
                        long docTime = ((Number) timeObj).longValue();
                        if (docTime > maxTimestamp) {
                            maxTimestamp = docTime;
                        }
                    }
                }

                Map<String, Object> change = new HashMap<>();
                change.put("firestoreId", firestoreId);
                change.put("type", dc.getType().name());
                change.put("data", data);
                pendingAccountChanges.add(change); // تأكد من اسم المصفوفة لديك
            }

            if (maxTimestamp > lastSync) {
                licenseManager.updateLastSyncTime(SecureLicenseManager.getAccountKey(), maxTimestamp);

            }

            handler.removeCallbacks(processAccountChanges); // تأكد من اسم الـ Runnable لديك
            handler.postDelayed(processAccountChanges, BATCH_DELAY_MS);
        });
    }

    private final Runnable processAccountChanges = () -> {
        if (pendingAccountChanges.isEmpty()) return;

        // إرسال الدفعة الحالية وإعادة تهيئة القائمة المؤقتة
        List<Map<String, Object>> changesBatch = new ArrayList<>(pendingAccountChanges);
        pendingAccountChanges = new ArrayList<>();
        repository.batchUpsertAccounts(changesBatch);
    };

    private void listenForTransactionChanges(String userId) {
        if (userId == null) return;
        // الاستعلام للمعاملات التي يملكها المستخدم الحالي
        SecureLicenseManager licenseManager = SecureLicenseManager.getInstance(context);
        long lastSync = licenseManager.getLastSyncTime(SecureLicenseManager.getTransactionKey());

        Query query = firestore.collection("transactions")
                .whereEqualTo("ownerUID", userId)
                .whereGreaterThan("lastModified", lastSync)
                .orderBy("lastModified", Query.Direction.ASCENDING);

        transactionsListener = query.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.e(TAG, "Transactions listen failed", e);
                return;
            }
            int changesSize = snapshots.getDocumentChanges().size();
            // فحص العداد اليومي وإيقاف المستمعات إذا بلغ 100
            if (!checkAndIncrementSyncLimit(context, changesSize)) {
                return; // الخروج وإلغاء المعالجة المحلية لحماية التطبيق
            }
            long maxTimestamp = lastSync;
            for (DocumentChange dc : snapshots.getDocumentChanges()) {
                String firestoreId = dc.getDocument().getId();
                Map<String, Object> data = dc.getDocument().getData();
                if (data.containsKey("lastModified")) {
                    Object timeObj = data.get("lastModified");
                    if (timeObj instanceof Number) {
                        long docTime = ((Number) timeObj).longValue();
                        if (docTime > maxTimestamp) maxTimestamp = docTime;
                    }
                }
                // تجهيز التغيير كخريطة
                Map<String, Object> change = new HashMap<>();
                change.put("firestoreId", firestoreId);
                change.put("type", dc.getType().name()); // ADDED, MODIFIED, REMOVED
                change.put("data", data);

                // إضافة التغيير إلى القائمة المؤقتة
                pendingTransactionChanges.add(change);
            }
            if (maxTimestamp > lastSync) {
                licenseManager.updateLastSyncTime(SecureLicenseManager.getTransactionKey(), maxTimestamp);
            }
            // إعادة جدولة المعالجة بعد فترة تأخير
            handler.removeCallbacks(processTransactionChanges);
            handler.postDelayed(processTransactionChanges, BATCH_DELAY_MS);
        });
    }

    private final Runnable processTransactionChanges = () -> {
        if (pendingTransactionChanges.isEmpty()) return;

        // إرسال الدفعة الحالية وإعادة تهيئة القائمة المؤقتة
        List<Map<String, Object>> changesBatch = new ArrayList<>(pendingTransactionChanges);
        pendingTransactionChanges = new ArrayList<>();
        repository.batchUpsertTransactions(changesBatch);
    };

    public void stopListening() {
        if (accountsListener != null) accountsListener.remove();
        if (transactionsListener != null) transactionsListener.remove();
        if (currenciesListener != null) currenciesListener.remove();
        if (accountTypesListener != null) accountTypesListener.remove();
        if (usersListener != null) usersListener.remove();
        if (licenseListener != null) licenseListener.remove();
        handler.removeCallbacksAndMessages(null);
    }

    private void syncAccountsFirst(String userId, Runnable onComplete) {
        if (userId == null) {
            onComplete.run();
            return;
        }

        SecureLicenseManager licenseManager = SecureLicenseManager.getInstance(context);
        long lastSync = licenseManager.getLastSyncTime(SecureLicenseManager.getAccountKey());

        Query query = firestore.collection("accounts")
                .whereEqualTo("ownerUID", userId)
                .whereGreaterThan("lastSyncDate", lastSync)
                .orderBy("lastModified", Query.Direction.ASCENDING);

        query.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {

                // 🔥 الاستدعاء السحري والمنفصل: نمرر حجم المستندات القادمة من السيرفر دفعة واحدة
                int incomingSize = task.getResult().size();
                if (!checkAndIncrementSyncLimit_Mod(context, incomingSize)) {
                    onComplete.run();
                    return; // الخروج وحجب المعالجة المحلية فوراً
                }
                List<Map<String, Object>> accountsBatch = new ArrayList<>();
                long maxTimestamp = lastSync;

                for (DocumentSnapshot doc : task.getResult()) {
                    Map<String, Object> data = doc.getData();
                    if (data != null) {
                        // استخراج أعلى وقت تعديل لتحديث المخزن المحلي
                        if (data.containsKey("lastSyncDate")) {
                            Object timeObj = data.get("lastSyncDate");
                            if (timeObj instanceof Number) {
                                long docTime = ((Number) timeObj).longValue();
                                if (docTime > maxTimestamp) maxTimestamp = docTime;
                            }
                        }

                        Map<String, Object> change = new HashMap<>();
                        change.put("firestoreId", doc.getId());
                        change.put("data", data);
                        accountsBatch.add(change);
                    }
                }

                // التحديث فقط في حال وجود بيانات جديدة لتوفير موارد الجهاز
                if (!accountsBatch.isEmpty()) {
                    repository.batchUpsertAccounts(accountsBatch);
                    licenseManager.updateLastSyncTime(SecureLicenseManager.getAccountKey(), maxTimestamp);
                    Log.d(TAG, "Initial accounts sync completed. Fetched: " + accountsBatch.size());
                } else {
                    Log.d(TAG, "Initial accounts sync: No new data.");
                }
            } else {
                Log.e(TAG, "Initial accounts sync failed", task.getException());
            }
            // تشغيل الـ Callback في كل الأحوال (نجاح أو فشل أو لا يوجد بيانات)
            onComplete.run();
        });
    }
    private boolean checkAndIncrementSyncLimit_Mod(Context context, int incomingCount) {
        SecureLicenseManager secureManager = SecureLicenseManager.getInstance(context);

        // 🎯 الشرط المشترك: مستخدم عادي (مجاني) ويمتلك أكثر من جهاز واحد
        if (!secureManager.isPremium() && secureManager.getDevicesNos() > 1) {
            SharedPreferences prefs = context.getSharedPreferences("sync_limit_prefs", Context.MODE_PRIVATE);
            String todayStr = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
            String lastSyncDate = prefs.getString("last_sync_date", "");
            int currentCount = todayStr.equals(lastSyncDate) ? prefs.getInt("today_sync_count", 0) : 0;

            // 1. إذا كان المستخدم قد استهلك الـ 100 عملية مسبقاً في دالة أخرى، احجب القادم فوراً
            if (currentCount >= 100) {
                Log.w(TAG, "🚫 تم حجب المزامنة مسبقاً لتجاوز الحد اليومي (100 عملية).");
                stopListening(); // إيقاف كافة المستمعات الحية
                showLimitToast(context);
                return false;
            }

            // 2. تحديث العداد الإجمالي في ملف الإعدادات دفعة واحدة
            int totalCount = currentCount + incomingCount;
            prefs.edit()
                    .putString("last_sync_date", todayStr)
                    .putInt("today_sync_count", totalCount)
                    .apply();

            Log.d(TAG, "📊 عداد المزامنة المشترك: " + totalCount + " / 100");

            // 3. الحسم الفوري عند ملامسة أو تجاوز الـ 100 عملية الآن
            if (totalCount >= 100) {
                Log.w(TAG, "⚠️ تم الوصول إلى حد 100 عملية الآن! جاري قطع الاتصال وإيقاف المستمعات.");
                stopListening(); // إيقاف مستمعات المزامنة آلياً
                showLimitToast(context);
            }
        }
        return true; // مسموح بالمرور والمعالجة
    }

    // دالة مساعدة لإظهار التنبيه على خيط الواجهة بأمان
    private void showLimitToast(Context context) {
        new Handler(Looper.getMainLooper()).post(() -> {
            android.widget.Toast.makeText(context,
                    context.getString(R.string.sync_limit_reached),
                    android.widget.Toast.LENGTH_LONG).show();
        });
    }
    private void syncTransactions(String userId, Runnable onComplete) {
        if (userId == null) {
            onComplete.run();
            return;
        }

        SecureLicenseManager licenseManager = SecureLicenseManager.getInstance(context);
        long lastSync = licenseManager.getLastSyncTime(SecureLicenseManager.getTransactionKey());

        Query query = firestore.collection("transactions")
                .whereEqualTo("ownerUID", userId)
                .whereGreaterThan("lastSyncDate", lastSync)
                .orderBy("lastModified", Query.Direction.ASCENDING);

        query.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {

                // 🔥 الاستدعاء السحري والمنفصل: نمرر حجم المستندات القادمة من السيرفر دفعة واحدة
                int incomingSize = task.getResult().size();
                if (!checkAndIncrementSyncLimit_Mod(context, incomingSize)) {
                    onComplete.run();
                    return; // الخروج وحجب المعالجة المحلية فوراً
                }
                List<Map<String, Object>> transactionsBatch = new ArrayList<>();
                long maxTimestamp = lastSync;

                for (DocumentSnapshot doc : task.getResult()) {
                    Map<String, Object> data = doc.getData();
                    if (data != null) {
                        if (data.containsKey("lastSyncDate")) {
                            Object timeObj = data.get("lastSyncDate");
                            if (timeObj instanceof Number) {
                                long docTime = ((Number) timeObj).longValue();
                                if (docTime > maxTimestamp) maxTimestamp = docTime;
                            }
                        }

                        Map<String, Object> change = new HashMap<>();
                        change.put("firestoreId", doc.getId());
                        change.put("data", data);
                        transactionsBatch.add(change);
                    }
                }

                if (!transactionsBatch.isEmpty()) {
                    repository.batchUpsertTransactions(transactionsBatch);
                    licenseManager.updateLastSyncTime(SecureLicenseManager.getTransactionKey(), maxTimestamp);
                    Log.d(TAG, "Initial transactions sync completed. Fetched: " + transactionsBatch.size());
                } else {
                    Log.d(TAG, "Initial transactions sync: No new data.");
                }
            } else {
                Log.e(TAG, "Initial transactions sync failed", task.getException());
            }
            onComplete.run();
        });
    }

    private void syncCurrencies(String userId, Runnable onComplete) {
        if (userId == null) {
            onComplete.run();
            return;
        }

        SecureLicenseManager licenseManager = SecureLicenseManager.getInstance(context);
        long lastSync = licenseManager.getLastSyncTime(SecureLicenseManager.getCurrencyKey());

        Query query = firestore.collection("currencies")
                .whereEqualTo("ownerUID", userId)
                .whereGreaterThan("lastSyncDate", lastSync)
                .orderBy("lastModified", Query.Direction.ASCENDING); // يُفضل الترتيب التصاعدي هنا

        query.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {

                // 🔥 الاستدعاء السحري والمنفصل: نمرر حجم المستندات القادمة من السيرفر دفعة واحدة
                int incomingSize = task.getResult().size();
                if (!checkAndIncrementSyncLimit_Mod(context, incomingSize)) {
                    onComplete.run();
                    return; // الخروج وحجب المعالجة المحلية فوراً
                }
                List<Map<String, Object>> currencyBatch = new ArrayList<>();
                long maxTimestamp = lastSync;

                for (DocumentSnapshot doc : task.getResult()) {
                    Map<String, Object> data = doc.getData();
                    if (data != null) {
                        if (data.containsKey("lastSyncDate")) {
                            Object timeObj = data.get("lastSyncDate");
                            if (timeObj instanceof Number) {
                                long docTime = ((Number) timeObj).longValue();
                                if (docTime > maxTimestamp) maxTimestamp = docTime;
                            }
                        }

                        Map<String, Object> change = new HashMap<>();
                        change.put("firestoreId", doc.getId());
                        change.put("data", data);
                        currencyBatch.add(change);
                    }
                }

                if (!currencyBatch.isEmpty()) {
                    repository.batchUpsertCurrencies(currencyBatch);
                    licenseManager.updateLastSyncTime(SecureLicenseManager.getCurrencyKey(), maxTimestamp);
                    Log.d(TAG, "Initial Currencies sync completed. Fetched: " + currencyBatch.size());
                } else {
                    Log.d(TAG, "Initial Currencies sync: No new data.");
                }
            } else {
                Log.e(TAG, "Initial Currencies sync failed", task.getException());
            }
            onComplete.run();
        });
    }

    private void listenForCurrencyChanges(String userId) {
        if (userId == null) return;
        SecureLicenseManager licenseManager = SecureLicenseManager.getInstance(context);
        long lastSync = licenseManager.getLastSyncTime(SecureLicenseManager.getCurrencyKey());

        Query query = firestore.collection("currencies")
                .whereEqualTo("ownerUID", userId)
                .whereGreaterThan("lastSyncDate", lastSync);

        currenciesListener = query.addSnapshotListener((snapshots, e) -> {

            if (e != null) {
                Log.e(TAG, "Currencies listen failed", e);
                return;
            }
            long maxTimestamp = lastSync;
            for (DocumentChange dc : snapshots.getDocumentChanges()) {

                String firestoreId = dc.getDocument().getId();
                Map<String, Object> data = dc.getDocument().getData();
                Map<String, Object> change = new HashMap<>();
                if (data.containsKey("lastSyncDate")) {
                    Object timeObj = data.get("lastSyncDate");
                    if (timeObj instanceof Number) {
                        long docTime = ((Number) timeObj).longValue();
                        if (docTime > maxTimestamp) maxTimestamp = docTime;
                    }
                }
                change.put("firestoreId", firestoreId);
                change.put("type", dc.getType().name());
                change.put("data", data);
                pendingCurrencyChanges.add(change);

            }
            if (maxTimestamp > lastSync) {
                licenseManager.updateLastSyncTime(SecureLicenseManager.getCurrencyKey(), maxTimestamp);
            }
            handler.removeCallbacks(processCurrencyChanges);
            handler.postDelayed(processCurrencyChanges, BATCH_DELAY_MS);
        });
    }

    private final Runnable processCurrencyChanges = () -> {
        if (pendingCurrencyChanges.isEmpty()) return;
        List<Map<String, Object>> batch = new ArrayList<>(pendingCurrencyChanges);
        pendingCurrencyChanges = new ArrayList<>();
        repository.batchUpsertCurrencies(batch);
    };

    private void listenForAccountTypeChanges(String userId) {
        if (userId == null) return;

        // 1. استدعاء ملف الإعدادات و جلب وقت آخر مزامنة لهذا المستخدم
        SecureLicenseManager licenseManager = SecureLicenseManager.getInstance(context);
        long lastSync = licenseManager.getLastSyncTime(SecureLicenseManager.getAccountTypeKey());


        // 2. الاستعلام الذكي: جلب المستندات التي تم تعديلها بعد آخر مزامنة فقط
        Query query = firestore.collection("accountTypes")
                .whereEqualTo("ownerUID", userId)
                .whereGreaterThan("lastSyncDate", lastSync);

        accountTypesListener = query.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.e(TAG, "AccountTypes listen failed", e);
                return;
            }

            long maxTimestamp = lastSync;

            for (DocumentChange dc : snapshots.getDocumentChanges()) {
                String firestoreId = dc.getDocument().getId();
                Map<String, Object> data = dc.getDocument().getData();

                // استخراج وقت التعديل لتحديث العداد المحلي
                if (data.containsKey("lastSyncDate")) {
                    Object timeObj = data.get("lastSyncDate");
                    if (timeObj instanceof Number) {
                        long docTime = ((Number) timeObj).longValue();
                        if (docTime > maxTimestamp) {
                            maxTimestamp = docTime; // تحديث أعلى وقت تم العثور عليه
                        }
                    }
                }

                // تجهيز البيانات لإرسالها للمعالجة المحلية (نفس كودك الأصلي)
                Map<String, Object> change = new HashMap<>();
                change.put("firestoreId", firestoreId);
                change.put("type", dc.getType().name());
                change.put("data", data);
                pendingAccountTypeChanges.add(change);
            }

            // 3. حفظ أعلى وقت وصلنا إليه للمزامنة القادمة
            if (maxTimestamp > lastSync) {
                licenseManager.updateLastSyncTime(SecureLicenseManager.getAccountTypeKey(), maxTimestamp);

            }

            handler.removeCallbacks(processAccountTypeChanges);
            handler.postDelayed(processAccountTypeChanges, BATCH_DELAY_MS);
        });
    }

    private final Runnable processAccountTypeChanges = () -> {
        if (pendingAccountTypeChanges.isEmpty()) return;
        List<Map<String, Object>> batch = new ArrayList<>(pendingAccountTypeChanges);
        pendingAccountTypeChanges = new ArrayList<>();
        repository.batchUpsertAccountTypes(batch);
    };
}