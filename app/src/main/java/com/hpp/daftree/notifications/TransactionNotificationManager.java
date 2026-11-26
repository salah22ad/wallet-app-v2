package com.hpp.daftree.notifications;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hpp.daftree.R;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.database.Transaction;
import com.hpp.daftree.database.User;
import com.hpp.daftree.utils.AppErrorHandler;
import com.hpp.daftree.utils.SecureLicenseManager;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.michaelrocks.libphonenumber.android.NumberParseException;
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil;
import io.michaelrocks.libphonenumber.android.Phonenumber;

public class TransactionNotificationManager {

    private static final String TAG = "TransNotifManager";
    private final FirebaseFirestore db;
    private final FirebaseAuth mAuth;
    private final PhoneNumberUtil phoneUtil;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AppDatabase appDb;
    private static final int TYPE_DEBIT = 1;
    private static final int TYPE_CREDIT = -1;
    private final Context context;
    private static final List<String> EXCLUDED_PHONES = List.of("+967780405427", "+967734249712");

    public TransactionNotificationManager(Context context) {
        this.context = context;
        this.appDb = AppDatabase.getDatabase(context);
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        this.phoneUtil = PhoneNumberUtil.createInstance(context);
    }
    private String getCurrentUserPhoneBlocking() {
        try {
            AppDatabase db = AppDatabase.getDatabase(context);
            User user = db.userDao().getUserProfileBlocking();
            return (user != null) ? user.getPhone() : null;
        } catch (Exception e) {
            Log.e(TAG, "Error reading user phone from local DB", e);
            return null;
        }
    }
    private String normalizePhoneNumber(String phone) {
        if (phone == null) return null;
        return phone.replaceAll("\\s+", "").replaceAll("-", "");
    }

    // التحقق مما إذا كان الرقم مستثنى
    private boolean isExcludedPhone(String phone) {
        if (phone == null) return true;
        String normalized = normalizePhoneNumber(phone);
        return EXCLUDED_PHONES.contains(normalized);
    }

    public void sendTransactionNotification(Transaction transaction, String currencySymbol, Context context) {

        if (mAuth.getCurrentUser() == null) {
            return;
        }
        if (transaction == null) return;

        executor.execute(() -> {
            String targetPhone;
            String accountFirestoreId = transaction.getAccountFirestoreId();
            targetPhone = appDb.accountDao().getPhoneNumber(accountFirestoreId);
            if (targetPhone == null || targetPhone.isEmpty()) return;
            List<String> possibleNumbers = getGlobalPossiblePhoneNumbers(targetPhone);

            if (targetPhone != null && !targetPhone.isEmpty()) {
                if (SecureLicenseManager.getInstance(context).isDebtFeatureEnabled()) {
                    findUserAndPush(targetPhone, transaction, currencySymbol);
                } else {
                    sendNewTransactions(possibleNumbers, transaction, currencySymbol);
                }
            }
        });
    }

    private void findUserAndPush(String rawPhoneNumber, Transaction transaction, String currencySymbol) {
        String currentUserPhone = getCurrentUserPhoneBlocking();
        // توليد الصيغ الممكنة للرقم (محلي، دولي، مع أصفار)
        List<String> possibleNumbers = getGlobalPossiblePhoneNumbers(rawPhoneNumber);

        // البحث في مجموعة users
        // الآن البحث صار أكثر أماناً لأن phoneNumber في users مصدره هو الـ Authentication الفعلي
        db.collection("users")
                .whereIn("phoneNumber", possibleNumbers)
                .whereEqualTo("isDebtFeatureEnabled", true)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        // وجدنا المستخدم المستهدف
                        // الـ ID لهذا المستند هو نفسه الـ User UID في Authentication
                        DocumentSnapshot userDoc = querySnapshot.getDocuments().get(0);
                        String targetUserId = userDoc.getId();
                        String targetPhone = userDoc.getString("phoneNumber");
                        if (currentUserPhone != null && currentUserPhone.equals(targetPhone)) {
                            Log.d(TAG, "Skipping detailed notification (same phone)");
                            return;
                        }
                        Log.d(TAG, "تم مطابقة الهاتف مع مستخدم حقيقي UID: " + targetUserId);
                        if (SecureLicenseManager.getInstance(context).isDebtFeatureEnabled()) {
                            prepareAndPushNotification(targetUserId, transaction, currencySymbol);
                        } else {
                            sendNewTransactions(possibleNumbers, transaction, currencySymbol);
                        }
                    } else {
                        sendNewTransactions(possibleNumbers, transaction, currencySymbol);
                        Log.d(TAG, "هذا الرقم غير مفعل لميزة المتابعة أو غير مسجل بالتطبيق.");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "خطأ في عملية البحث المتقاطع", e);
                    AppErrorHandler.handle(context);
                });
    }

    public List<String> getGlobalPossiblePhoneNumbers(String phone) {
        List<String> possibleNumbers = new ArrayList<>();

        // 1. فحص أمان: إذا كان الرقم null أو فارغاً، نخرج فوراً لتجنب الكراش
        if (phone == null || phone.trim().isEmpty()) {
            Log.w(TAG, "getGlobalPossiblePhoneNumbers: Phone number is null or empty");
            return possibleNumbers;
        }
        try {
            // تنظيف الرقم من أي رموز غير الأرقام وعلامة +
            String cleanNumber = phone.replaceAll("[^0-9+]", "");
            possibleNumbers.add(cleanNumber);

            // محاولة استخراج الصيغ الدولية والمحلية
            Phonenumber.PhoneNumber phoneNumber = phoneUtil.parse(cleanNumber, "YE"); // الافتراضي اليمن

            // إضافة الصيغة الدولية الكاملة (مثال: +96777...)
            possibleNumbers.add(phoneUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164));

            // إضافة الصيغة الوطنية (مثال: 77...)
            possibleNumbers.add(String.valueOf(phoneNumber.getNationalNumber()));

        } catch (NumberParseException e) {
            Log.e(TAG, "خطأ في تحليل رقم الهاتف: " + phone, e);
        } catch (Exception e) {
            Log.e(TAG, "خطأ غير متوقع أثناء معالجة الرقم: " + phone, e);
        }

        return possibleNumbers;
    }

    /**
     * دالة توليد صيغ الأرقام العالمية
     * لا تفترض مفتاح دولة معين، بل تعالج الرموز (+ و 0 و 00) فقط.
     */

    // دالة مساعدة لمنع التكرار في القائمة
    private void addIfUnique(List<String> list, String val) {
        if (val != null && !val.isEmpty() && !list.contains(val)) {
            list.add(val);
        }
    }

    private void prepareAndPushNotification(String targetUserId, Transaction transaction, String currencySymbol) {


        String currentUserName = SecureLicenseManager.getInstance(context).getFullName();
        if (currentUserName == null || currentUserName.isEmpty()) {
            currentUserName = context.getString(R.string.prepareAndPushNotification_01);
        }

        String typeText;
        if (transaction.getType() == TYPE_CREDIT) {
            typeText = context.getString(R.string.report_balance_type_credit);
        } else {
            typeText = context.getString(R.string.report_balance_type_debit);
        }

        String title = context.getString(R.string.notif_tx_official_title);
        String body = context.getString(
                R.string.notif_tx_official_body,
                currentUserName,
                formatCurrency(transaction.getAmount()),
                currencySymbol,
                typeText
        );
        boolean senderIsGuest = mAuth.getCurrentUser() == null;

        Map<String, Object> notificationData = new HashMap<>();
        boolean isGuest = mAuth.getCurrentUser() == null;
        notificationData.put("targetUserId", targetUserId);
        notificationData.put("targetUserType", isGuest ? "guests" : "users");
        notificationData.put("senderId", senderIsGuest ? "guest_user" : mAuth.getCurrentUser().getUid());
        notificationData.put("type", "transaction_alert");
        notificationData.put("title", title);
        notificationData.put("message", body);
        notificationData.put("amount", transaction.getAmount());
        notificationData.put("currency", currencySymbol);
        notificationData.put("transType", typeText);
        notificationData.put("senderName", currentUserName);
        notificationData.put("transactionId", transaction.getFirestoreId());
        notificationData.put("timestamp", FieldValue.serverTimestamp());
        notificationData.put("isRead", false);
        notificationData.put("sentAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));

        db.collection("personal_notifications")
                .add(notificationData)
                .addOnSuccessListener(docRef -> Log.d(TAG, "تم إرسال الإشعار بنجاح"))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "فشل إرسال الإشعار", e);
                    AppErrorHandler.handle(context);
                });
    }

    private String formatCurrency(double amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(2);
        return format.format(amount);
    }


    private void prepareAndPushNotification2(String targetUserId, String title, String body, String targetUserType) {

        Map<String, Object> notificationData = new HashMap<>();

        // المرسل (حالي)
        boolean senderIsGuest = mAuth.getCurrentUser() == null;

        notificationData.put("targetUserId", targetUserId);
        notificationData.put("targetUserType", targetUserType); // القيمة التي سنمررها (users أو guests)
        notificationData.put("senderId", senderIsGuest ? "guest_user" : mAuth.getCurrentUser().getUid());
        notificationData.put("type", "transaction_alert");
        notificationData.put("title", title);
        notificationData.put("message", body);
        notificationData.put("timestamp", FieldValue.serverTimestamp());
        notificationData.put("isRead", false);
        notificationData.put("sentAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));

        db.collection("personal_notifications")
                .add(notificationData)
                .addOnSuccessListener(docRef -> Log.d(TAG, "تم إرسال الإشعار بنجاح"))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "خطأ في إرسال الإشعار", e);
                    AppErrorHandler.handle(context);
                });
    }

    // 2. تعديل دالة البحث لتمرير النوع الصحيح
    public void sendNewAccount(String phone, Context context) {
        executor.execute(() -> {
            String currentUserPhone = getCurrentUserPhoneBlocking();

            List<String> possibleNumbers = getGlobalPossiblePhoneNumbers(phone);
            String linkedAccounts = context.getString(R.string.linked_account);
            String title = context.getString(R.string.notif_new_account_title);
            String body = context.getString(R.string.prepareAndPushNotification_account_is_new, linkedAccounts);
            if(isExcludedPhone(currentUserPhone)) return;
            // البحث في المستخدمين الرسميين أولاً
            db.collection("users")
                    .whereIn("phone", possibleNumbers)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        if (!querySnapshot.isEmpty()) {
                            DocumentSnapshot userDoc = querySnapshot.getDocuments().get(0);
                            String defaultPhone = userDoc.getString("phone");
                            String targetUserId = querySnapshot.getDocuments().get(0).getId();
                            if (currentUserPhone != null && currentUserPhone.equals(defaultPhone)) {
                                Log.d(TAG, "Skipping new account notification (same phone)");
                                return;
                            }
                                Log.d(TAG, "New Account Alert: Found in Users -> " + targetUserId);
                                prepareAndPushNotification2(targetUserId, title, body, "users");

                        } else {
                            // إذا لم يوجد، نبحث في الضيوف
                            db.collection("guests")
                                    .whereIn("phone", possibleNumbers)
                                    .get()
                                    .addOnSuccessListener(guestSnapshot -> {
                                        if (!guestSnapshot.isEmpty()) {
                                            DocumentSnapshot userDoc = guestSnapshot.getDocuments().get(0);
                                            String defaultPhone = userDoc.getString("phone");
                                            if (currentUserPhone != null && currentUserPhone.equals(defaultPhone)) {
                                                Log.d(TAG, "Skipping new account notification (same phone)");
                                                return;
                                            }
                                            String targetUserId = guestSnapshot.getDocuments().get(0).getId();
                                            Log.d(TAG, "New Account Alert: Found in guests -> " + targetUserId);

                                            prepareAndPushNotification2(targetUserId, title, body, "guests");
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "فشل البحث في guests", e);
                                        AppErrorHandler.handle(context);
                                    });
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "فشل البحث في users", e);
                        AppErrorHandler.handle(context);
                    });
        });
    }

    public void sendNewTransactions(List<String> possibleNumbers, Transaction transaction, String currencySymbol) {
        //List<String> possibleNumbers = getGlobalPossiblePhoneNumbers(phone);

        executor.execute(() -> {
            String currentUserPhone = getCurrentUserPhoneBlocking(); // قراءة من Room في خيط خلفي
            if(isExcludedPhone(currentUserPhone)) return;
        String typeText;
        if (transaction.getType() == TYPE_CREDIT) {
            typeText = context.getString(R.string.report_balance_type_credit);
        } else {
            typeText = context.getString(R.string.report_balance_type_debit);
        }

        String title = context.getString(R.string.notif_tx_guest_title);
        String body = context.getString(
                R.string.notif_tx_guest_body,
                formatCurrency(transaction.getAmount()),
                currencySymbol,
                typeText
        );
        // البحث في المستخدمين الرسميين أولاً
        db.collection("users")
                .whereIn("phone", possibleNumbers)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        DocumentSnapshot userDoc = querySnapshot.getDocuments().get(0);
                        String defaultPhone = userDoc.getString("phone");
                        String targetUserId = querySnapshot.getDocuments().get(0).getId();
                        if (currentUserPhone != null && currentUserPhone.equals(defaultPhone)) {
                            Log.d(TAG, "Skipping transaction notification (same phone)");
                            return;
                        }
                            Log.d(TAG, "New Account Alert: Found in Users -> " + targetUserId);
                            prepareAndPushNotification2(targetUserId, title, body, "users");

                    } else {
                        // إذا لم يوجد، نبحث في الضيوف
                        db.collection("guests")
                                .whereIn("phone", possibleNumbers)
                                .get()
                                .addOnSuccessListener(guestSnapshot -> {
                                    if (!guestSnapshot.isEmpty()) {
                                        DocumentSnapshot userDoc = guestSnapshot.getDocuments().get(0);
                                        String defaultPhone = userDoc.getString("phone");
                                        String targetUserId = guestSnapshot.getDocuments().get(0).getId();
                                        if (currentUserPhone != null && currentUserPhone.equals(defaultPhone)) {
                                            Log.d(TAG, "Skipping transaction notification (same phone)");
                                            return;
                                        }
                                            prepareAndPushNotification2(targetUserId, title, body, "guests");

                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "فشل البحث في guests", e);
                                    AppErrorHandler.handle(context);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "فشل البحث في users", e);
                    AppErrorHandler.handle(context);
                });
        });
        }

}