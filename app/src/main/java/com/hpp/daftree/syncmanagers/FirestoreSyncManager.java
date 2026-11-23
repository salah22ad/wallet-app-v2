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
import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.models.AppAuth;
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
    private ListenerRegistration currenciesListener;
    private ListenerRegistration accountTypesListener;
    private ListenerRegistration budgetCategoriesListener;
    private ListenerRegistration budgetLimitsListener;
    private ListenerRegistration budgetEntriesListener;
    private ListenerRegistration referralNotifListener;
    private ListenerRegistration usersListener;
    private ListenerRegistration licenseListener;

    private DaftreeRepository repository;
    private static String notiMessege = "";
    private static String notiMessegeTitel = "";
    private ReferralNotificationListener referralListener;
    private final Map<String, Boolean> processedNotifs = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private List> pendingAccountChanges = new ArrayList<>();
    private List> pendingTransactionChanges = new ArrayList<>();
    private List> pendingCurrencyChanges = new ArrayList<>();
    private List> pendingAccountTypeChanges = new ArrayList<>();
    private List> pendingBudgetCategoryChanges = new ArrayList<>();
    private List> pendingBudgetLimitChanges = new ArrayList<>();
    private List> pendingBudgetEntryChanges = new ArrayList<>();

    public static volatile boolean isSyncWorkerRunning = false;

    private static final long BATCH_DELAY_MS = 500;
    private static final int MAX_FREE_MULTI_DEVICE_SYNC_LIMIT = 100;
    private final SecureLicenseManager secureManager;
    private final VersionManager versionManager;

    private FirestoreSyncManager(Context context) {
        this.firestore = FirebaseFirestore.getInstance();
        this.context = context.getApplicationContext();
        this.secureManager = SecureLicenseManager.getInstance(context);
        this.versionManager = new VersionManager(context);
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
        String userId = AppAuth.getUserID(context);
        if (userId == null || userId.isEmpty()) {
            Log.e(TAG, "المستخدم الحالي غير مسجل الدخول.");
            if (onComplete != null) onComplete.run();
            return;
        }

        stopListening();
        int devicesNos = SecureLicenseManager.getInstance(context).getDevicesNos();
        listenForReferralNotifications(userId, context);

        if (devicesNos > 1) {
            if (isSyncWorkerRunning) return;

            syncAccountTypes(userId, () ->
                syncAccountsFirst(userId, () ->
                    syncCurrencies(userId, () ->
                        syncTransactions(userId, () ->
                            syncBudgetCategories(userId, () ->
                                syncBudgetLimits(userId, () ->
                                    syncBudgetEntries(userId, () -> {
                                        listenForAccountTypeChanges(userId);
                                        listenForAccountChanges(userId);
                                        listenForCurrencyChanges(userId);
                                        listenForTransactionChanges(userId);
                                        listenForBudgetCategoryChanges(userId);
                                        listenForBudgetLimitChanges(userId);
                                        listenForBudgetEntryChanges(userId);
                                    })
                                )
                            )
                        )
                    )
                )
            );
        }
        Log.d(TAG, "تم تفعيل مستمعي المزامنة.");
        if (onComplete != null) onComplete.run();
    }

    private long extractSyncTimestamp(Map<String, Object> data) {
        if (data == null) return 0L;
        Object syncObj = data.get("lastSyncDate");
        if (syncObj instanceof Number) return ((Number) syncObj).longValue();
        Object modObj = data.get("lastModified");
        if (modObj instanceof Number) return ((Number) modObj).longValue();
        return 0L;
    }

    private boolean checkAndIncrementSyncLimit(Context context, int changeSize) {
        SecureLicenseManager mgr = SecureLicenseManager.getInstance(context);
        if (!mgr.isPremium() && mgr.getDevicesNos() > 1) {
            SharedPreferences prefs = context.getSharedPreferences("sync_limit_prefs", Context.MODE_PRIVATE);
            String todayStr = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
            String lastSyncDate = prefs.getString("last_sync_date", "");
            int currentCount = prefs.getInt("today_sync_count", 0);

            if (!todayStr.equals(lastSyncDate)) {
                currentCount = 0;
                prefs.edit().putString("last_sync_date", todayStr).putInt("today_sync_count", 0).apply();
            }

            if (currentCount >= MAX_FREE_MULTI_DEVICE_SYNC_LIMIT) {
                Log.w(TAG, "تم حجب المزامنة: تجاوز الحد اليومي للمستخدمين المجانيين.");
                stopListening();
                return false;
            }

            prefs.edit().putInt("today_sync_count", currentCount + changeSize).apply();
        }
        return true;
    }

    private boolean checkAndIncrementSyncLimit_Mod(Context context, int incomingCount) {
        SecureLicenseManager mgr = SecureLicenseManager.getInstance(context);
        if (!mgr.isPremium() && mgr.getDevicesNos() > 1) {
            SharedPreferences prefs = context.getSharedPreferences("sync_limit_prefs", Context.MODE_PRIVATE);
            String todayStr = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
            String lastSyncDate = prefs.getString("last_sync_date", "");
            int currentCount = todayStr.equals(lastSyncDate) ? prefs.getInt("today_sync_count", 0) : 0;

            if (currentCount >= MAX_FREE_MULTI_DEVICE_SYNC_LIMIT) {
                stopListening();
                showLimitToast(context);
                return false;
            }

            int totalCount = currentCount + incomingCount;
            prefs.edit()
                    .putString("last_sync_date", todayStr)
                    .putInt("today_sync_count", totalCount)
                    .apply();

            if (totalCount >= MAX_FREE_MULTI_DEVICE_SYNC_LIMIT) {
                stopListening();
                showLimitToast(context);
            }
        }
        return true;
    }

    private void showLimitToast(Context context) {
        new Handler(Looper.getMainLooper()).post(() ->
                android.widget.Toast.makeText(context,
                        context.getString(R.string.sync_limit_reached),
                        android.widget.Toast.LENGTH_LONG).show());
    }

    public void listenForReferralNotifications(String currentUserUid, Context context) {
        if (referralNotifListener != null) {
            referralNotifListener.remove();
            referralNotifListener = null;
        }
        referralNotifListener = firestore.collection("referral_notifications")
                .whereEqualTo("targetUid", currentUserUid)
                .whereEqualTo("processed", false)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        if (dc.getType() != DocumentChange.Type.ADDED) continue;

                        String notifId = dc.getDocument().getId();
                        if (processedNotifs.containsKey(notifId)) continue;
                        processedNotifs.put(notifId, true);

                        long points = dc.getDocument().getLong("points") != null ? dc.getDocument().getLong("points") : 0;
                        if (points == 0) continue;

                        DocumentReference userRef = firestore.collection("users").document(currentUserUid);
                        DocumentReference notifRef = dc.getDocument().getReference();

                        firestore.runTransaction(transaction -> {
                            DocumentSnapshot userSnapshot = transaction.get(userRef);
                            if (!userSnapshot.exists()) {
                                throw new IllegalStateException("User document does not exist!");
                            }

                            long currentSuccessfulReferrals = userSnapshot.contains("successfulReferrals")
                                    && userSnapshot.getLong("successfulReferrals") != null
                                    ? userSnapshot.getLong("successfulReferrals") : 0;
                            long currentReferrals = userSnapshot.contains("referral_rewards")
                                    && userSnapshot.getLong("referral_rewards") != null
                                    ? userSnapshot.getLong("referral_rewards") : 0;
                            long newTotalReferrals = currentSuccessfulReferrals + 1;

                            int milestoneBonus = RewardManager.checkForMilestoneRewards((int) newTotalReferrals);
                            long newReferrals;

                            if (milestoneBonus > 0) {
                                notiMessegeTitel = context.getString(R.string.referral_reward_title);
                                notiMessege = context.getString(R.string.referral_reward_bonus_message, points, milestoneBonus);
                                newReferrals = currentReferrals + 2 * points;
                            } else {
                                notiMessegeTitel = context.getString(R.string.referral_reward_title);
                                notiMessege = context.getString(R.string.referral_reward_message, points);
                                newReferrals = currentReferrals + points;
                            }

                            transaction.update(userRef, "referral_rewards", newReferrals);
                            transaction.update(userRef, "successfulReferrals", newTotalReferrals);
                            transaction.update(userRef, "lastModified", System.currentTimeMillis());
                            transaction.update(notifRef, "processed", true);
                            return null;
                        }).addOnSuccessListener(aVoid -> {
                            if (referralListener != null) {
                                referralListener.onReferralRewardReceived(currentUserUid, points, notiMessegeTitel, notiMessege);
                            }
                        }).addOnFailureListener(ex -> {
                            processedNotifs.remove(notifId);
                            AppErrorHandler.handle(context);
                        });
                    }
                });
    }

    public void stopListening() {
        if (accountsListener != null) { accountsListener.remove(); accountsListener = null; }
        if (transactionsListener != null) { transactionsListener.remove(); transactionsListener = null; }
        if (currenciesListener != null) { currenciesListener.remove(); currenciesListener = null; }
        if (accountTypesListener != null) { accountTypesListener.remove(); accountTypesListener = null; }
        if (budgetCategoriesListener != null) { budgetCategoriesListener.remove(); budgetCategoriesListener = null; }
        if (budgetLimitsListener != null) { budgetLimitsListener.remove(); budgetLimitsListener = null; }
        if (budgetEntriesListener != null) { budgetEntriesListener.remove(); budgetEntriesListener = null; }
        if (referralNotifListener != null) { referralNotifListener.remove(); referralNotifListener = null; }
        if (usersListener != null) { usersListener.remove(); usersListener = null; }
        if (licenseListener != null) { licenseListener.remove(); licenseListener = null; }
        handler.removeCallbacksAndMessages(null);
    }

    // ==================== المزامنة الأولية (ordered by lastSyncDate) ====================

    private void syncAccountTypes(String userId, Runnable onComplete) {
        if (userId == null) { onComplete.run(); return; }
        long lastSync = secureManager.getLastSyncTime(SecureLicenseManager.getAccountTypeKey());

        firestore.collection("accountTypes")
                .whereEqualTo("ownerUID", userId)
                .whereGreaterThan("lastSyncDate", lastSync)
                .orderBy("lastSyncDate", Query.Direction.ASCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        if (!checkAndIncrementSyncLimit_Mod(context, task.getResult().size())) {
                            onComplete.run();
                            return;
                        }
                        List> batch = new ArrayList<>();
                        long maxTs = lastSync;
                        for (DocumentSnapshot doc : task.getResult()) {
                            Map<String, Object> data = doc.getData();
                            if (data != null) {
                                long ts = extractSyncTimestamp(data);
                                if (ts > maxTs) maxTs = ts;
                                Map<String, Object> change = new HashMap<>();
                                change.put("firestoreId", doc.getId());
                                change.put("data", data);
                                batch.add(change);
                            }
                        }
                        if (!batch.isEmpty()) {
                            repository.batchUpsertAccountTypes(batch);
                            secureManager.updateLastSyncTime(SecureLicenseManager.getAccountTypeKey(), maxTs);
                        }
                    } else {
                        Log.e(TAG, "Initial accountTypes sync failed", task.getException());
                    }
                    onComplete.run();
                });
    }

    private void syncAccountsFirst(String userId, Runnable onComplete) {
        if (userId == null) { onComplete.run(); return; }
        long lastSync = secureManager.getLastSyncTime(SecureLicenseManager.getAccountKey());

        firestore.collection("accounts")
                .whereEqualTo("ownerUID", userId)
                .whereGreaterThan("lastSyncDate", lastSync)
                .orderBy("lastSyncDate", Query.Direction.ASCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        if (!checkAndIncrementSyncLimit_Mod(context, task.getResult().size())) {
                            onComplete.run();
                            return;
                        }
                        List> accountsBatch = new ArrayList<>();
                        long maxTs = lastSync;
                        for (DocumentSnapshot doc : task.getResult()) {
                            Map<String, Object> data = doc.getData();
                            if (data != null) {
                                long ts = extractSyncTimestamp(data);
                                if (ts > maxTs) maxTs = ts;
                                Map<String, Object> change = new HashMap<>();
                                change.put("firestoreId", doc.getId());
                                change.put("data", data);
                                accountsBatch.add(change);
                            }
                        }
                        if (!accountsBatch.isEmpty()) {
                            repository.batchUpsertAccounts(accountsBatch);
                            secureManager.updateLastSyncTime(SecureLicenseManager.getAccountKey(), maxTs);
                        }
                    } else {
                        Log.e(TAG, "Initial accounts sync failed", task.getException());
                    }
                    onComplete.run();
                });
    }

    private void syncCurrencies(String userId, Runnable onComplete) {
        if (userId == null) { onComplete.run(); return; }
        long lastSync = secureManager.getLastSyncTime(SecureLicenseManager.getCurrencyKey());

        firestore.collection("currencies")
                .whereEqualTo("ownerUID", userId)
                .whereGreaterThan("lastSyncDate", lastSync)
                .orderBy("lastSyncDate", Query.Direction.ASCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        if (!checkAndIncrementSyncLimit_Mod(context, task.getResult().size())) {
                            onComplete.run();
                            return;
                        }
                        List> currencyBatch = new ArrayList<>();
                        long maxTs = lastSync;
                        for (DocumentSnapshot doc : task.getResult()) {
                            Map<String, Object> data = doc.getData();
                            if (data != null) {
                                long ts = extractSyncTimestamp(data);
                                if (ts > maxTs) maxTs = ts;
                                Map<String, Object> change = new HashMap<>();
                                change.put("firestoreId", doc.getId());
                                change.put("data", data);
                                currencyBatch.add(change);
                            }
                        }
                        if (!currencyBatch.isEmpty()) {
                            repository.batchUpsertCurrencies(currencyBatch);
                            secureManager.updateLastSyncTime(SecureLicenseManager.getCurrencyKey(), maxTs);
                        }
                    } else {
                        Log.e(TAG, "Initial currencies sync failed", task.getException());
                    }
                    onComplete.run();
                });
    }

    private void syncTransactions(String userId, Runnable onComplete) {
        if (userId == null) { onComplete.run(); return; }
        long lastSync = secureManager.getLastSyncTime(SecureLicenseManager.getTransactionKey());

        firestore.collection("transactions")
                .whereEqualTo("ownerUID", userId)
                .whereGreaterThan("lastSyncDate", lastSync)
                .orderBy("lastSyncDate", Query.Direction.ASCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        if (!checkAndIncrementSyncLimit_Mod(context, task.getResult().size())) {
                            onComplete.run();
                            return;
                        }
                        List> txBatch = new ArrayList<>();
                        long maxTs = lastSync;
                        for (DocumentSnapshot doc : task.getResult()) {
                            Map<String, Object> data = doc.getData();
                            if (data != null) {
                                long ts = extractSyncTimestamp(data);
                                if (ts > maxTs) maxTs = ts;
                                Map<String, Object> change = new HashMap<>();
                                change.put("firestoreId", doc.getId());
                                change.put("data", data);
                                txBatch.add(change);
                            }
                        }
                        if (!txBatch.isEmpty()) {
                            repository.batchUpsertTransactions(txBatch);
                            secureManager.updateLastSyncTime(SecureLicenseManager.getTransactionKey(), maxTs);
                        }
                    } else {
                        Log.e(TAG, "Initial transactions sync failed", task.getException());
                    }
                    onComplete.run();
                });
    }

    private void syncBudgetCategories(String userId, Runnable onComplete) {
        if (userId == null) { onComplete.run(); return; }
        long lastSync = secureManager.getLastSyncTime(SecureLicenseManager.getBudgetCategoryKey());

        firestore.collection("budget_categories")
                .whereEqualTo("ownerUID", userId)
                .whereGreaterThan("lastSyncDate", lastSync)
                .orderBy("lastSyncDate", Query.Direction.ASCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        List> batch = new ArrayList<>();
                        long maxTs = lastSync;
                        for (DocumentSnapshot doc : task.getResult()) {
                            Map<String, Object> data = doc.getData();
                            if (data != null) {
                                long ts = extractSyncTimestamp(data);
                                if (ts > maxTs) maxTs = ts;
                                Map<String, Object> change = new HashMap<>();
                                change.put("firestoreId", doc.getId());
                                change.put("data", data);
                                batch.add(change);
                            }
                        }
                        if (!batch.isEmpty()) {
                            repository.batchUpsertBudgetCategories(batch);
                            secureManager.updateLastSyncTime(SecureLicenseManager.getBudgetCategoryKey(), maxTs);
                        }
                    } else {
                        Log.e(TAG, "Initial budget_categories sync failed", task.getException());
                    }
                    onComplete.run();
                });
    }

    private void syncBudgetLimits(String userId, Runnable onComplete) {
        if (userId == null) { onComplete.run(); return; }
        long lastSync = secureManager.getLastSyncTime(SecureLicenseManager.getBudgetLimitKey());

        firestore.collection("monthly_budget_limits")
                .whereEqualTo("ownerUID", userId)
                .whereGreaterThan("lastSyncDate", lastSync)
                .orderBy("lastSyncDate", Query.Direction.ASCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        List> batch = new ArrayList<>();
                        long maxTs = lastSync;
                        for (DocumentSnapshot doc : task.getResult()) {
                            Map<String, Object> data = doc.getData();
                            if (data != null) {
                                long ts = extractSyncTimestamp(data);
                                if (ts > maxTs) maxTs = ts;
                                Map<String, Object> change = new HashMap<>();
                                change.put("firestoreId", doc.getId());
                                change.put("data", data);
                                batch.add(change);
                            }
                        }
                        if (!batch.isEmpty()) {
                            repository.batchUpsertBudgetLimits(batch);
                            secureManager.updateLastSyncTime(SecureLicenseManager.getBudgetLimitKey(), maxTs);
                        }
                    } else {
                        Log.e(TAG, "Initial monthly_budget_limits sync failed", task.getException());
                    }
                    onComplete.run();
                });
    }

    private void syncBudgetEntries(String userId, Runnable onComplete) {
        if (userId == null) { onComplete.run(); return; }
        long lastSync = secureManager.getLastSyncTime(SecureLicenseManager.getBudgetEntryKey());

        firestore.collection("budget_entries")
                .whereEqualTo("ownerUID", userId)
                .whereGreaterThan("lastSyncDate", lastSync)
                .orderBy("lastSyncDate", Query.Direction.ASCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        if (!checkAndIncrementSyncLimit_Mod(context, task.getResult().size())) {
                            onComplete.run();
                            return;
                        }
                        List> batch = new ArrayList<>();
                        long maxTs = lastSync;
                        for (DocumentSnapshot doc : task.getResult()) {
                            Map<String, Object> data = doc.getData();
                            if (data != null) {
                                long ts = extractSyncTimestamp(data);
                                if (ts > maxTs) maxTs = ts;
                                Map<String, Object> change = new HashMap<>();
                                change.put("firestoreId", doc.getId());
                                change.put("data", data);
                                batch.add(change);
                            }
                        }
                        if (!batch.isEmpty()) {
                            repository.batchUpsertBudgetEntries(batch);
                            secureManager.updateLastSyncTime(SecureLicenseManager.getBudgetEntryKey(), maxTs);
                        }
                    } else {
                        Log.e(TAG, "Initial budget_entries sync failed", task.getException());
                    }
                    onComplete.run();
                });
    }

    // ==================== المستمعون الحيّون (موحدون على lastSyncDate) ====================

    private void listenForAccountChanges(String userId) {
        if (userId == null) return;
        long lastSync = secureManager.getLastSyncTime(SecureLicenseManager.getAccountKey());

        accountsListener = firestore.collection("accounts")
                .whereEqualTo("ownerUID", userId)
                .whereGreaterThan("lastSyncDate", lastSync)
                .orderBy("lastSyncDate", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;
                    long maxTs = secureManager.getLastSyncTime(SecureLicenseManager.getAccountKey());
                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        Map<String, Object> data = dc.getDocument().getData();
                        long ts = extractSyncTimestamp(data);
                        if (ts > maxTs) maxTs = ts;
                        Map<String, Object> change = new HashMap<>();
                        change.put("firestoreId", dc.getDocument().getId());
                        change.put("type", dc.getType().name());
                        change.put("data", data);
                        pendingAccountChanges.add(change);
                    }
                    if (maxTs > lastSync) {
                        secureManager.updateLastSyncTime(SecureLicenseManager.getAccountKey(), maxTs);
                    }
                    handler.removeCallbacks(processAccountChanges);
                    handler.postDelayed(processAccountChanges, BATCH_DELAY_MS);
                });
    }

    private final Runnable processAccountChanges = () -> {
        if (pendingAccountChanges.isEmpty()) return;
        List> batch = new ArrayList<>(pendingAccountChanges);
        pendingAccountChanges = new ArrayList<>();
        repository.batchUpsertAccounts(batch);
    };

    private void listenForTransactionChanges(String userId) {
        if (userId == null) return;
        long lastSync = secureManager.getLastSyncTime(SecureLicenseManager.getTransactionKey());

        transactionsListener = firestore.collection("transactions")
                .whereEqualTo("ownerUID", userId)
                .whereGreaterThan("lastSyncDate", lastSync)
                .orderBy("lastSyncDate", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;
                    int changesSize = snapshots.getDocumentChanges().size();
                    if (changesSize == 0 || !checkAndIncrementSyncLimit(context, changesSize)) return;

                    long maxTs = secureManager.getLastSyncTime(SecureLicenseManager.getTransactionKey());
                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        Map<String, Object> data = dc.getDocument().getData();
                        long ts = extractSyncTimestamp(data);
                        if (ts > maxTs) maxTs = ts;
                        Map<String, Object> change = new HashMap<>();
                        change.put("firestoreId", dc.getDocument().getId());
                        change.put("type", dc.getType().name());
                        change.put("data", data);
                        pendingTransactionChanges.add(change);
                    }
                    if (maxTs > lastSync) {
                        secureManager.updateLastSyncTime(SecureLicenseManager.getTransactionKey(), maxTs);
                    }
                    handler.removeCallbacks(processTransactionChanges);
                    handler.postDelayed(processTransactionChanges, BATCH_DELAY_MS);
                });
    }

    private final Runnable processTransactionChanges = () -> {
        if (pendingTransactionChanges.isEmpty()) return;
        List> batch = new ArrayList<>(pendingTransactionChanges);
        pendingTransactionChanges = new ArrayList<>();
        repository.batchUpsertTransactions(batch);
    };

    private void listenForCurrencyChanges(String userId) {
        if (userId == null) return;
        long lastSync = secureManager.getLastSyncTime(SecureLicenseManager.getCurrencyKey());

        currenciesListener = firestore.collection("currencies")
                .whereEqualTo("ownerUID", userId)
                .whereGreaterThan("lastSyncDate", lastSync)
                .orderBy("lastSyncDate", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;
                    long maxTs = secureManager.getLastSyncTime(SecureLicenseManager.getCurrencyKey());
                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        Map<String, Object> data = dc.getDocument().getData();
                        long ts = extractSyncTimestamp(data);
                        if (ts > maxTs) maxTs = ts;
                        Map<String, Object> change = new HashMap<>();
                        change.put("firestoreId", dc.getDocument().getId());
                        change.put("type", dc.getType().name());
                        change.put("data", data);
                        pendingCurrencyChanges.add(change);
                    }
                    if (maxTs > lastSync) {
                        secureManager.updateLastSyncTime(SecureLicenseManager.getCurrencyKey(), maxTs);
                    }
                    handler.removeCallbacks(processCurrencyChanges);
                    handler.postDelayed(processCurrencyChanges, BATCH_DELAY_MS);
                });
    }

    private final Runnable processCurrencyChanges = () -> {
        if (pendingCurrencyChanges.isEmpty()) return;
        List> batch = new ArrayList<>(pendingCurrencyChanges);
        pendingCurrencyChanges = new ArrayList<>();
        repository.batchUpsertCurrencies(batch);
    };

    private void listenForAccountTypeChanges(String userId) {
        if (userId == null) return;
        long lastSync = secureManager.getLastSyncTime(SecureLicenseManager.getAccountTypeKey());

        accountTypesListener = firestore.collection("accountTypes")
                .whereEqualTo("ownerUID", userId)
                .whereGreaterThan("lastSyncDate", lastSync)
                .orderBy("lastSyncDate", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;
                    long maxTs = secureManager.getLastSyncTime(SecureLicenseManager.getAccountTypeKey());
                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        Map<String, Object> data = dc.getDocument().getData();
                        long ts = extractSyncTimestamp(data);
                        if (ts > maxTs) maxTs = ts;
                        Map<String, Object> change = new HashMap<>();
                        change.put("firestoreId", dc.getDocument().getId());
                        change.put("type", dc.getType().name());
                        change.put("data", data);
                        pendingAccountTypeChanges.add(change);
                    }
                    if (maxTs > lastSync) {
                        secureManager.updateLastSyncTime(SecureLicenseManager.getAccountTypeKey(), maxTs);
                    }
                    handler.removeCallbacks(processAccountTypeChanges);
                    handler.postDelayed(processAccountTypeChanges, BATCH_DELAY_MS);
                });
    }

    private final Runnable processAccountTypeChanges = () -> {
        if (pendingAccountTypeChanges.isEmpty()) return;
        List> batch = new ArrayList<>(pendingAccountTypeChanges);
        pendingAccountTypeChanges = new ArrayList<>();
        repository.batchUpsertAccountTypes(batch);
    };

    private void listenForBudgetCategoryChanges(String userId) {
        if (userId == null) return;
        long lastSync = secureManager.getLastSyncTime(SecureLicenseManager.getBudgetCategoryKey());

        budgetCategoriesListener = firestore.collection("budget_categories")
                .whereEqualTo("ownerUID", userId)
                .whereGreaterThan("lastSyncDate", lastSync)
                .orderBy("lastSyncDate", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;
                    long maxTs = secureManager.getLastSyncTime(SecureLicenseManager.getBudgetCategoryKey());
                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        Map<String, Object> data = dc.getDocument().getData();
                        long ts = extractSyncTimestamp(data);
                        if (ts > maxTs) maxTs = ts;
                        Map<String, Object> change = new HashMap<>();
                        change.put("firestoreId", dc.getDocument().getId());
                        change.put("type", dc.getType().name());
                        change.put("data", data);
                        pendingBudgetCategoryChanges.add(change);
                    }
                    if (maxTs > lastSync) {
                        secureManager.updateLastSyncTime(SecureLicenseManager.getBudgetCategoryKey(), maxTs);
                    }
                    handler.removeCallbacks(processBudgetCategoryChanges);
                    handler.postDelayed(processBudgetCategoryChanges, BATCH_DELAY_MS);
                });
    }

    private final Runnable processBudgetCategoryChanges = () -> {
        if (pendingBudgetCategoryChanges.isEmpty()) return;
        List> batch = new ArrayList<>(pendingBudgetCategoryChanges);
        pendingBudgetCategoryChanges = new ArrayList<>();
        repository.batchUpsertBudgetCategories(batch);
    };

    private void listenForBudgetLimitChanges(String userId) {
        if (userId == null) return;
        long lastSync = secureManager.getLastSyncTime(SecureLicenseManager.getBudgetLimitKey());

        budgetLimitsListener = firestore.collection("monthly_budget_limits")
                .whereEqualTo("ownerUID", userId)
                .whereGreaterThan("lastSyncDate", lastSync)
                .orderBy("lastSyncDate", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;
                    long maxTs = secureManager.getLastSyncTime(SecureLicenseManager.getBudgetLimitKey());
                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        Map<String, Object> data = dc.getDocument().getData();
                        long ts = extractSyncTimestamp(data);
                        if (ts > maxTs) maxTs = ts;
                        Map<String, Object> change = new HashMap<>();
                        change.put("firestoreId", dc.getDocument().getId());
                        change.put("type", dc.getType().name());
                        change.put("data", data);
                        pendingBudgetLimitChanges.add(change);
                    }
                    if (maxTs > lastSync) {
                        secureManager.updateLastSyncTime(SecureLicenseManager.getBudgetLimitKey(), maxTs);
                    }
                    handler.removeCallbacks(processBudgetLimitChanges);
                    handler.postDelayed(processBudgetLimitChanges, BATCH_DELAY_MS);
                });
    }

    private final Runnable processBudgetLimitChanges = () -> {
        if (pendingBudgetLimitChanges.isEmpty()) return;
        List> batch = new ArrayList<>(pendingBudgetLimitChanges);
        pendingBudgetLimitChanges = new ArrayList<>();
        repository.batchUpsertBudgetLimits(batch);
    };

    private void listenForBudgetEntryChanges(String userId) {
        if (userId == null) return;
        long lastSync = secureManager.getLastSyncTime(SecureLicenseManager.getBudgetEntryKey());

        budgetEntriesListener = firestore.collection("budget_entries")
                .whereEqualTo("ownerUID", userId)
                .whereGreaterThan("lastSyncDate", lastSync)
                .orderBy("lastSyncDate", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;
                    int changesSize = snapshots.getDocumentChanges().size();
                    if (changesSize == 0 || !checkAndIncrementSyncLimit(context, changesSize)) return;

                    long maxTs = secureManager.getLastSyncTime(SecureLicenseManager.getBudgetEntryKey());
                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        Map<String, Object> data = dc.getDocument().getData();
                        long ts = extractSyncTimestamp(data);
                        if (ts > maxTs) maxTs = ts;
                        Map<String, Object> change = new HashMap<>();
                        change.put("firestoreId", dc.getDocument().getId());
                        change.put("type", dc.getType().name());
                        change.put("data", data);
                        pendingBudgetEntryChanges.add(change);
                    }
                    if (maxTs > lastSync) {
                        secureManager.updateLastSyncTime(SecureLicenseManager.getBudgetEntryKey(), maxTs);
                    }
                    handler.removeCallbacks(processBudgetEntryChanges);
                    handler.postDelayed(processBudgetEntryChanges, BATCH_DELAY_MS);
                });
    }

    private final Runnable processBudgetEntryChanges = () -> {
        if (pendingBudgetEntryChanges.isEmpty()) return;
        List> batch = new ArrayList<>(pendingBudgetEntryChanges);
        pendingBudgetEntryChanges = new ArrayList<>();
        repository.batchUpsertBudgetEntries(batch);
    };
}
