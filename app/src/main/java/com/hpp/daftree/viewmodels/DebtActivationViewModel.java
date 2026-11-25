package com.hpp.daftree.viewmodels;

import android.app.Application;
import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.hpp.daftree.R;
import com.hpp.daftree.utils.AppErrorHandler;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class DebtActivationViewModel extends AndroidViewModel {

    private final MutableLiveData<String> verificationId = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> codeSent = new MutableLiveData<>(false); // تم تعديل الاسم ليتوافق مع الأكتيفتي
    private final MutableLiveData<Boolean> activationSuccess = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMsg = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isUnlinkedSuccess = new MutableLiveData<>(false);

    // متغيرات LiveData جديدة طلبها الأكتيفتي
    private final MutableLiveData<PhoneAuthCredential> numberConflictEvent = new MutableLiveData<>();
    private final MutableLiveData<Boolean> showWhatsAppDialogEvent = new MutableLiveData<>(false);

    private final FirebaseAuth mAuth;
    private final FirebaseFirestore db;

    private String pendingFullName;
    private String pendingPhoneNumber;
    private String pendingCountryCode;

    public DebtActivationViewModel(@NonNull Application application) {
        super(application);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<Boolean> getCodeSent() { return codeSent; }
    public LiveData<Boolean> getActivationSuccess() { return activationSuccess; }
    public LiveData<String> getErrorMsg() { return errorMsg; }
    public LiveData<Boolean> getIsUnlinkedSuccess() { return isUnlinkedSuccess; }
    public LiveData<PhoneAuthCredential> getNumberConflictEvent() { return numberConflictEvent; }
    public LiveData<Boolean> getShowWhatsAppDialogEvent() { return showWhatsAppDialogEvent; }

    public String getPendingPhoneNumber() { return pendingPhoneNumber; }
    public String getPendingCountryCode() { return pendingCountryCode; }

    // تم تعديل الدالة لتستقبل المتغيرات الـ 5 كما تم استدعاؤها في الأكتيفتي
    public void startPhoneVerification(Activity activity, String fullNumberE164, String rawNumber, String countryCode, String name) {
        this.pendingFullName = name;
        this.pendingPhoneNumber = rawNumber;
        this.pendingCountryCode = countryCode;

        isLoading.setValue(true);
        errorMsg.setValue(null);

        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(mAuth)
                .setPhoneNumber(fullNumberE164)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                        isLoading.setValue(false);
                        linkUserCredential(credential);
                    }

                    @Override
                    public void onVerificationFailed(@NonNull FirebaseException e) {
                        isLoading.setValue(false);
                        Log.e("DebtViewModel", "Verification failed", e);

                        // تحويل النص إلى حروف صغيرة لضمان التقاط كل حالات الحظر والقيود
                        String errorString = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

                        if (errorString.contains("restricted") ||
                                errorString.contains("blocked") ||
                                errorString.contains("unusual activity") ||
                                errorString.contains("too many requests") ||
                                errorString.contains("not authorized")) {

                            // توجيه المستخدم للمسار اليدوي (واتساب)
                            showWhatsAppDialogEvent.setValue(true);
                        } else {
                            errorMsg.setValue(e.getLocalizedMessage());
                        }
                    }

                    @Override
                    public void onCodeSent(@NonNull String vId, @NonNull PhoneAuthProvider.ForceResendingToken token) {
                        isLoading.setValue(false);
                        verificationId.setValue(vId);
                        codeSent.setValue(true);
                    }
                })
                .build();

        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    public void verifyCode(String code) {
        if (verificationId.getValue() == null) {
            // المسار اليدوي البديل للحظر الإقليمي
            saveActivationDataToFirestore();
            return;
        }

        isLoading.setValue(true);
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId.getValue(), code);
        linkUserCredential(credential);
    }

    private void linkUserCredential(PhoneAuthCredential credential) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            user.linkWithCredential(credential)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            saveActivationDataToFirestore();
                        } else {
                            isLoading.setValue(false);
                            Exception exception = task.getException();
                            if (exception instanceof FirebaseAuthUserCollisionException) {
                                // الرقم مرتبط بحساب آخر (User Collision) نرسل الحدث للأكتيفتي
                                numberConflictEvent.setValue(credential);
                            } else {
                                errorMsg.setValue(exception != null ? exception.getLocalizedMessage() : getApplication().getApplicationContext().getString(R.string.error_link_failed));
                            }
                        }
                    });
        } else {
            isLoading.setValue(false);
            errorMsg.setValue(getApplication().getApplicationContext().getString(R.string.error_link_number));
        }
    }

    // دالة سحب الرقم في حال موافقة المستخدم على انه المالك الحقيقي (Reclaim Phone Number)
    public void reclaimPhoneNumber(PhoneAuthCredential credential) {
        isLoading.setValue(true);
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            // استخدام updatePhoneNumber يجبر نقل ملكية الرقم للحساب الحالي وفك ارتباطه بالحساب القديم
            user.updatePhoneNumber(credential)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            saveActivationDataToFirestore();
                        } else {
                            isLoading.setValue(false);
                            errorMsg.setValue(task.getException() != null ? task.getException().getLocalizedMessage() : "فشل سحب الرقم وتسجيله");
                        }
                    });
        }
    }

    // دالة إلغاء ربط الهاتف الرسمية
    public void unlinkPhoneNumber() {
        isLoading.setValue(true);
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            user.unlink(PhoneAuthProvider.PROVIDER_ID)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            removeActivationDataFromFirestore();
                        } else {
                            isLoading.setValue(false);
                            errorMsg.setValue(task.getException() != null ? task.getException().getLocalizedMessage() : "فشل إلغاء الربط");
                        }
                    });
        } else {
            isLoading.setValue(false);
        }
    }
    // تم إزالة الـ Context من الدالة لتعمل كما هي مستدعاة في الأكتيفتي
    public void saveActivationDataToFirestore() {
        isLoading.setValue(true);

        // 1. حماية من الـ NullPointerException
        String rawPhone = pendingPhoneNumber != null ? pendingPhoneNumber : "";
        String safeFullName = pendingFullName != null ? pendingFullName : "";
        String safeCountryCode = pendingCountryCode != null ? pendingCountryCode : "";

        // 2. تطبيق ReplaceAll بأمان
        String phoneNumberWithoutSpaces = rawPhone.replaceAll("\\s+", "");

        Map<String, Object> updates = new HashMap<>();
        updates.put("isDebtFeatureEnabled", true);
        updates.put("isLinked", true);
        updates.put("debtFeatureActivatedAt", getCurrentLocalDateTime());
        updates.put("fullName", safeFullName);
        updates.put("name", safeFullName);
        updates.put("phoneNumber", phoneNumberWithoutSpaces);
        updates.put("phone", phoneNumberWithoutSpaces);
        updates.put("countryCode", safeCountryCode);

        if (mAuth.getCurrentUser() != null) {
            db.collection("users").document(mAuth.getCurrentUser().getUid())
                    .set(updates, SetOptions.merge())
                    .addOnSuccessListener(aVoid -> {
                        isLoading.setValue(false);
                        activationSuccess.setValue(true);
                    })
                    .addOnFailureListener(e -> {
                        isLoading.setValue(false);
                        errorMsg.setValue(getApplication().getString(R.string.error_save_failed, e.getMessage()));
                        Log.e("DebtActivationViewModel", "Save failed", e);
                        AppErrorHandler.handle(getApplication().getApplicationContext()); // جلب Context من الـ Application
                    });
        } else {
            isLoading.setValue(false);
            errorMsg.setValue("User is null after operation");
        }
    }
    // تم إزالة الـ Context من الدالة لتعمل كما هي مستدعاة في الأكتيفتي
    private void removeActivationDataFromFirestore() {
        if (mAuth.getCurrentUser() != null) {
            Map<String, Object> updates = new HashMap<>();

            // استخدام FieldValue.delete() لحذف الحقل نهائياً من قاعدة البيانات
            updates.put("phoneNumber", FieldValue.delete());
            updates.put("countryCode", FieldValue.delete());
            updates.put("fullName", FieldValue.delete());
            updates.put("fullAuthPhoneNumber", FieldValue.delete());
            updates.put("debtFeatureActivatedAt", FieldValue.delete()); // إذا كنت تريد حذف تاريخ التفعيل أيضاً

            // ملاحظة هامة: إذا كان التطبيق يعتمد على وجود الحقول المنطقية (Boolean) بقيمة false
            // لتجنب أخطاء NullPointerException عند القراءة، فمن الأفضل تركها false:
//            updates.put("isDebtFeatureEnabled", false);
//            updates.put("isLinked", false);

            // أما إذا كنت تريد حذفها من الجذر تماماً، فاستخدم:
             updates.put("isDebtFeatureEnabled", FieldValue.delete());
             updates.put("isLinked", FieldValue.delete());

            db.collection("users").document(mAuth.getCurrentUser().getUid())
                    .update(updates) // استخدام update هو الأفضل والأصح عند حذف حقول معينة
                    .addOnSuccessListener(aVoid -> {
                        isLoading.setValue(false);
                        isUnlinkedSuccess.setValue(true);
                    })
                    .addOnFailureListener(e -> {
                        isLoading.setValue(false);
                        errorMsg.setValue(e.getLocalizedMessage());
                    });
        }
    }

    public static String getCurrentLocalDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss a z", Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(new Date());
    }
}