
package com.hpp.daftree.utils;

import static android.content.Context.MODE_PRIVATE;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.facebook.AccessToken;
import com.facebook.login.LoginBehavior;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.GraphRequest;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseException;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.hpp.daftree.models.AppAuth;
import com.hpp.daftree.R;
import com.hpp.daftree.database.DeviceInfo;
import com.hpp.daftree.database.User;
import com.hpp.daftree.models.DaftreeRepository;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("deprecation")
public class GoogleAuthHelper {

    private static final String TAG = "GoogleAuthHelper";
    private static final int RC_SIGN_IN = 9001;
    private static final int FACEBOOK_LOGIN_REQUEST_CODE = 64206;
    private static final String USERS_COLLECTION = "users";
    public static String facebookEmail;
    private final Context context;
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final GoogleSignInClient googleSignInClient;
    private AuthCallback authCallback;
    private final LicenseManager licenseManager;
    private final SecureLicenseManager secureLicenseManager;
    private final ReferralManager referralManager;
    private final DaftreeRepository repository;
    private final CallbackManager facebookCallbackManager;
    private LoginManager facebookLoginManager;
    private final VersionManager versionManager;
    private String tempRawPhoneNumber;
    private String tempCountryCode;
    private String tempFullName;
    private ActivityResultLauncher<Intent> facebookLoginLauncher;

    public interface AuthCallback {
        void onSignInFailure(String error);

        void onSignOutSuccess();

        void onSignInSuccess(FirebaseUser user, AuthResult authResult);

        void onSignInProgress(String message);

        void onAccountInfoReceived(String email, String token, String provider);
    }

    // --- واجهة خاصة لأحداث الهاتف (إرسال الكود) ---
    private PhoneAuthListener phoneAuthListener;

    public interface PhoneAuthListener {
        void onCodeSent(String verificationId);

        void onVerificationFailed(String error);
    }

    // --- الواجهة الرئيسية للمصادقة ---
    public GoogleAuthHelper(Context context, LicenseManager licenseManager, DaftreeRepository repository) {
        this.context = context;
        this.auth = FirebaseAuth.getInstance();
        this.firestore = FirebaseFirestore.getInstance();
        this.licenseManager = licenseManager;
        this.referralManager = new ReferralManager(context);
        this.repository = repository;
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(R.string.default_web_client_id))
                .requestEmail().build();
        this.googleSignInClient = GoogleSignIn.getClient(context, gso);
        try {
            if (!com.facebook.FacebookSdk.isInitialized()) {
                com.facebook.FacebookSdk.sdkInitialize(context.getApplicationContext());
            }
            this.facebookLoginManager = LoginManager.getInstance();
        } catch (Exception e) {
            Log.e(TAG, "FacebookSdk init warning: " + e.getMessage());
        }
        this.facebookCallbackManager = CallbackManager.Factory.create();
        this.versionManager = new VersionManager(context);
        this.secureLicenseManager = SecureLicenseManager.getInstance(context);
        setupFacebookCallback();
    }

    public void signInWithFacebook(Activity activity, AuthCallback callback) {
        this.authCallback = callback;

        if (!isNetworkAvailable(activity)) {
            if (authCallback != null) {
                authCallback.onSignInFailure(context.getString(R.string.no_internet));
            }
            return;
        }

        if (authCallback != null) {
            authCallback.onSignInProgress(context.getString(R.string.connecting_facebook));
        }
        try {
            if (!com.facebook.FacebookSdk.isInitialized()) {
                com.facebook.FacebookSdk.sdkInitialize(activity.getApplicationContext());
            }
            if (facebookLoginManager == null) {
                facebookLoginManager = LoginManager.getInstance();
                setupFacebookCallback();
            }
            facebookLoginManager.setLoginBehavior(LoginBehavior.NATIVE_WITH_FALLBACK);
            facebookLoginManager.logInWithReadPermissions(
                    activity,
                    Arrays.asList("email", "public_profile")
            );
        } catch (Exception e) {
            Log.e(TAG, "Facebook login error", e);
            if (authCallback != null) {
                authCallback.onSignInFailure("Facebook Error: " + e.getMessage());
            }
        }
    }

    // ✅ إضافة طريقة لمعالجة onActivityResult من Activity
    public boolean onFacebookActivityResult(int requestCode, int resultCode, Intent data) {
        return facebookCallbackManager.onActivityResult(requestCode, resultCode, data);
    }
    private void getUserEmailFromFacebook(AccessToken token) {
        GraphRequest request = GraphRequest.newMeRequest(token, (object, response) -> {
            try {
                if (object != null) {
                    // 👇 طباعة كل ما يرسله فيسبوك بالتفصيل في اللوج
                    Log.d(TAG, "Facebook Raw Data (Full): \n" + object.toString(4));

                    String id = object.optString("id", "");
                    String email = object.optString("email", "").trim();
                    facebookEmail = object.optString("id", "");
                    if (email.isEmpty()) {
                        email = id + "@facebook.com";
                        Log.d(TAG, "Facebook login without email. Generated: " + email);
                    }

                    if (authCallback != null) {
                        authCallback.onAccountInfoReceived(email, token.getToken(), "facebook");
                    }
                } else {
                    if (authCallback != null) authCallback.onSignInFailure("Facebook Error: Data is null");
                }
            } catch (Exception e) {
                Log.e(TAG, "Facebook GraphRequest Error", e);
                if (authCallback != null) authCallback.onSignInFailure("Facebook Error: " + e.getMessage());
            }
        });

        // 👇 هنا وسّعنا دائرة الطلب لتشمل: الاسم، الصورة (بحجم كبير)، الاسم الأول، والأخير، والإيميل
        Bundle parameters = new Bundle();
        parameters.putString("fields", "id,name,first_name,last_name,email,picture.type(large)");
        request.setParameters(parameters);
        request.executeAsync();
    }
    private void setupFacebookCallback() {
        if (facebookLoginManager == null) return;
        facebookLoginManager.registerCallback(facebookCallbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                if (authCallback != null) {
                    authCallback.onSignInProgress(context.getString(R.string.setupFacebookCallback_03));
                }
                // ✅ لا نقوم باستدعاء handleFacebookAccessToken مباشرة
                getUserEmailFromFacebook(loginResult.getAccessToken());
            }

            @Override
            public void onCancel() {
                if (authCallback != null)
                    authCallback.onSignInFailure(context.getString(R.string.facebook_login_canceled));
            }

            @Override
            public void onError(@NonNull FacebookException error) {
                if (authCallback != null)
                    authCallback.onSignInFailure("Facebook Error: " + error.getMessage());
            }
        });
    }

    public void signIn(Activity activity, AuthCallback callback) {
        if (!isNetworkAvailable(activity)) {
            callback.onSignInFailure(context.getString(R.string.signIn_04));
            return;
        }
        this.authCallback = callback;
        try {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            activity.startActivityForResult(signInIntent, RC_SIGN_IN);
        } catch (Exception e) {
            if (callback != null)
                callback.onSignInFailure(context.getString(R.string.signIn_05) + e.getMessage());
        }
    }
    public void handleSignInResult(Intent data) {
        if (data == null) {
            if (authCallback != null)
                authCallback.onSignInFailure(context.getString(R.string.handleSignInResult_06));
            return;
        }
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account != null && authCallback != null) {
                // ✅ التعديل الجوهري: إرسال الإيميل للأكتيفيتي للفحص بدلاً من الدخول المباشر
                authCallback.onAccountInfoReceived(account.getEmail(), account.getIdToken(), "google");
            }
        } catch (ApiException e) {
            if (authCallback != null)
                authCallback.onSignInFailure(context.getString(R.string.handleSignInResult_08) + e.getStatusCode());
            Log.e(TAG, "فشل تسجيل الدخول بـ Google: " + e.getStatusCode());
        }
    }

    // ✅ دالة جديدة لإتمام الدخول لفايرباس تُستدعى من LoginActivity بعد نجاح الفحص
    public void completeFirebaseSignIn(String token, String provider) {
        AuthCredential credential;
        if (provider.equals("google")) {
            credential = GoogleAuthProvider.getCredential(token, null);
        } else {
            credential = FacebookAuthProvider.getCredential(token);
        }

        auth.signInWithCredential(credential)
                .addOnCompleteListener((Activity) context, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            saveOrUpdateUserInFirestore(user, task.getResult());
                        } else {
                            if (authCallback != null)
                                authCallback.onSignInFailure(context.getString(R.string.ar_long_text_33));
                        }
                    } else {
                        String error = task.getException() != null ? task.getException().getMessage() : "Auth Failed";
                        if (authCallback != null) authCallback.onSignInFailure(error);
                    }
                }).addOnFailureListener(e -> {
                    Log.e(TAG, "firestoreSignIn", e);
                    AppErrorHandler.handle(context);
                });
    }


    private boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void saveOrUpdateUserInFirestore(FirebaseUser firebaseUser, AuthResult authResult) {
        Log.d(TAG, "بدء حفظ/تحديث بيانات المستخدم: " + firebaseUser.getUid());

        DocumentReference userRef = firestore.collection("users").document(firebaseUser.getUid());

        userRef.get()
                .addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document != null && document.exists()) {
                    Log.d(TAG, "المستخدم موجود في Firestore - جاري التحديث");
                    updateUserLoginData(userRef, firebaseUser, authResult);

                } else {
                    Log.d(TAG, "مستخدم جديد - جاري الإنشاء");
                    createNewUserData(userRef, firebaseUser, authResult);
                }
            } else {
//                createNewUserData(userRef, firebaseUser, authResult);
                Log.e(TAG, "فشل في التحقق من وجود المستخدم", task.getException());
                if (authCallback != null) {
                    authCallback.onSignInFailure(context.getString(R.string.saveOrUpdateUserInFirestore_10) +
                            (task.getException() != null ? task.getException().getMessage() : ""));
                }
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "استثناء في عملية التحقق", e);
            if (authCallback != null) {
                authCallback.onSignInFailure(context.getString(R.string.saveOrUpdateUserInFirestore_11) + e.getMessage());
                AppErrorHandler.handle(context);
            }

        });
    }


    public void saveOrUpdateUserInFirestoreUpgrade(FirebaseUser firebaseUser) {
        if (SecureLicenseManager.getInstance(context).isGuest()) return;
        String uuid = AppAuth.getUserID(context);
        String userUid = AppAuth.getUserID(context);
        if (userUid == null) {
            Log.e("GoogleAuthHelper", "User ID is null even in offline mode.");
            return; // تجنب الاستمرار إذا لم يتوفر معرف نهائياً
        }
        assert userUid != null;
        DocumentReference userRef = firestore.collection("users").document(userUid);

        userRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document != null && document.exists()) {
                    Log.d(TAG, "المستخدم موجود في Firestore - جاري التحديث FirestoreUpgrade ");
                    if (!document.contains("db_upgrade")) {
                        new VersionManager(context).setFirst_upgrade(true);
                    }
                    updateUserLoginDataUpgrade(userRef, firebaseUser);

                } else {
                    Log.d(TAG, "مستخدم جديد - جاري الإنشاء FirestoreUpgrade ");

                    createNewUserDataUpgrade(userRef, firebaseUser);
                }

                new VersionManager(context).setFirestoreUser_isAdded(true);
            } else {
                createNewUserDataUpgrade(userRef, firebaseUser);
                Log.e(TAG, "فشل في التحقق من وجود المستخدم FirestoreUpgrade ", task.getException());
                if (authCallback != null) {
                    authCallback.onSignInFailure(context.getString(R.string.saveOrUpdateUserInFirestoreUpgrade_12) +
                            (task.getException() != null ? task.getException().getMessage() : ""));
                }
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "استثناء في عملية التحقق FirestoreUpgrade ", e);
            if (authCallback != null) {
                authCallback.onSignInFailure(context.getString(R.string.saveOrUpdateUserInFirestoreUpgrade_13) + e.getMessage());
            }
            AppErrorHandler.handle(context);
        });
    }

    private void createNewUserData(DocumentReference userRef, FirebaseUser firebaseUser, AuthResult authResult) {
        try {
            Log.d(TAG, "جار اضافة المستخدم الجديد");

            // الحصول على LiveData للمستخدم
            LiveData<User> userLiveData = repository.getUserProfile();

            // إضافة مراقب مع timeout لضمان عدم التعلق
            final Observer<User> userObserver = new Observer<User>() {
                @Override
                public void onChanged(User localUser) {
                    // إزالة المراقب فوراً لمنع التكرار
                    userLiveData.removeObserver(this);

                    Log.d(TAG, "تم الحصول على بيانات المستخدم المحلي: " + (localUser != null ? "موجود" : "غير موجود"));

                    User newUser = createUserObject(firebaseUser, localUser);

                    // حفظ المستخدم في Firestore
                    saveUserToFirestore(userRef, newUser, firebaseUser, authResult);
                }
            };

            // إضافة المراقب
            userLiveData.observeForever(userObserver);

            // إضافة timeout للسلامة - إذا لم تأتِ البيانات خلال 5 ثوانٍ
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                userLiveData.removeObserver(userObserver);
                if (!isUserSaved) { // تأكد من أن المتغير isUserSaved يُعرِّف في الكلاس
                    Log.w(TAG, "انتهت مهلة انتظار بيانات المستخدم المحلي، استخدام البيانات الافتراضية");
                    createDefualtNewUserData(userRef, firebaseUser, authResult);
                }
            }, 5000);

        } catch (RuntimeException e) {
            Log.e(TAG, "createNewUserData Error: " + e.getMessage(), e);
            createDefualtNewUserData(userRef, firebaseUser, authResult);
        }
    }

    // متغير لتتبع حالة حفظ المستخدم
    private boolean isUserSaved = false;

    private User createUserObject(FirebaseUser firebaseUser, User localUser) {

        User newUser = new User();
        newUser.setOwnerUID(firebaseUser.getUid());
        newUser.setEmail(firebaseUser.getEmail());
        newUser.setName(firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : context.getString(R.string.createDefaultNewUserData_15));
        newUser.setPhone(firebaseUser.getPhoneNumber() != null ? firebaseUser.getPhoneNumber().toString() : "+967780405427");
        newUser.setProfileImageUri(firebaseUser.getPhotoUrl() != null ? firebaseUser.getPhotoUrl().toString() : "");

        if (localUser != null) {
            newUser.setName(localUser.getName());
            newUser.setAddress(localUser.getAddress());
            newUser.setCompany(localUser.getCompany());
            newUser.setPhone(localUser.getPhone());
        } else {
            newUser.setName(firebaseUser.getDisplayName());
            // تعيين القيم الافتراضية للحقول الأخرى
            newUser.setName(context.getString(R.string.ar_long_text_20));
            newUser.setCompany(context.getString(R.string.ar_long_text_20));
            newUser.setAddress(context.getString(R.string.ar_text_10_1));
            newUser.setPhone(context.getString(R.string.string_967_734_249_712));
        }

        newUser.setUserType("user");
        newUser.setSuccessfulReferrals(0);
        newUser.setIs_active(true);
        newUser.setIs_premium(false);
        newUser.setCreated_at(getCurrentLocalDateTime());
//        newUser.setDebtFeatureActivatedAt(getCurrentLocalDateTime());
        newUser.setLogin_count(1);
        newUser.setMax_devices(LicenseManager.MAX_DEVICES);
        newUser.setTransactions_count(0);
        newUser.setMax_transactions(LicenseManager.FREE_TRANSACTION_LIMIT);
        newUser.setAd_rewards(0);
        newUser.setDb_upgrade(1);
        newUser.setReferral_rewards(0);
        newUser.setApp_Version(versionManager.getCurrentVersionName());
        newUser.setLastVersion(versionManager.getPreviousVersion());
        DeviceInfo currentDevice = licenseManager.getCurrentDeviceInfo();
        newUser.getDevices().put(currentDevice.getDeviceId(), currentDevice);

        return newUser;
    }

    public static String getCurrentLocalDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss a z", Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(new Date());
    }

    private void saveUserToFirestore(DocumentReference userRef, User newUser, FirebaseUser firebaseUser, AuthResult authResult) {
        newUser.setApp_Version(versionManager.getCurrentVersionName());
        newUser.setLastVersion(versionManager.getPreviousVersion());
        userRef.set(newUser).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                isUserSaved = true;
                Log.d(TAG, "تم إضافة المستخدم بنجاح في Firestore");

                // تطبيق مكافأة الإحالة إذا كانت متاحة
                referralManager.applyReferralRewardIfAvailable(firebaseUser.getUid());

                if (authCallback != null) {
                    authCallback.onSignInSuccess(firebaseUser, authResult);
                }
            } else {
                Log.e(TAG, "فشل في إنشاء بيانات المستخدم في Firestore", task.getException());
                if (authCallback != null) {
                    authCallback.onSignInFailure(context.getString(R.string.ar_long_text_33) +
                            (task.getException() != null ? task.getException().getMessage() : ""));
                }
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "فشل في عملية حفظ المستخدم في Firestore", e);
            if (authCallback != null) {
                authCallback.onSignInFailure(context.getString(R.string.saveUserToFirestore_14) + e.getMessage());
                AppErrorHandler.handle(context);
            }

        });
    }

    private void createDefualtNewUserData(DocumentReference userRef, FirebaseUser firebaseUser, AuthResult authResult) {
        try {
            Log.d(TAG, "إنشاء مستخدم جديد بالبيانات الافتراضية");
            User newUser = new User();
            newUser.setOwnerUID(firebaseUser.getUid());
            newUser.setEmail(firebaseUser.getEmail());
            newUser.setName(firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : context.getString(R.string.createDefaultNewUserData_15));
            newUser.setPhone(firebaseUser.getPhoneNumber() != null ? firebaseUser.getPhoneNumber().toString() : "+967780405427");
            newUser.setProfileImageUri(firebaseUser.getPhotoUrl() != null ? firebaseUser.getPhotoUrl().toString() : "");
            newUser.setCompany(context.getString(R.string.createDefaultNewUserData_16));
            newUser.setAddress(context.getString(R.string.createDefaultNewUserData_17));
            newUser.setUserType("user");
            newUser.setSuccessfulReferrals(0);
            newUser.setIs_active(true);
            newUser.setIs_premium(false);
            newUser.setCreated_at(User.getCurrentLocalDateTime());
            newUser.setLogin_count(1);
            newUser.setMax_devices(LicenseManager.MAX_DEVICES);
            newUser.setTransactions_count(0);
            newUser.setMax_transactions(LicenseManager.FREE_TRANSACTION_LIMIT);
            newUser.setAd_rewards(0);
            newUser.setDb_upgrade(1);
            newUser.setReferral_rewards(0);
            newUser.setApp_Version(versionManager.getCurrentVersionName());
            newUser.setLastVersion(versionManager.getPreviousVersion());
            DeviceInfo currentDevice = licenseManager.getCurrentDeviceInfo();
            newUser.getDevices().put(currentDevice.getDeviceId(), currentDevice);

            saveUserToFirestore(userRef, newUser, firebaseUser, authResult);

        } catch (Exception e) {
            Log.e(TAG, "خطأ في createDefualtNewUserData", e);
            if (authCallback != null) {
                authCallback.onSignInFailure(context.getString(R.string.createDefaultNewUserData_18) + e.getMessage());
                AppErrorHandler.handle(context);
            }
        }
    }

    private void createNewUserDataUpgrade(DocumentReference userRef, FirebaseUser firebaseUser) {
        // Get user data from local database using LiveData
        try {

            LiveData<User> userLiveData = repository.getUserProfile();
            userLiveData.observeForever(new Observer<User>() {
                @Override
                public void onChanged(User localUser) {
                    SecureLicenseManager licenceData = SecureLicenseManager.getInstance(context);

                    userLiveData.removeObserver(this);
                    User newUser = new User();
                    newUser.setOwnerUID(AppAuth.getUserID(context));
                    newUser.setEmail(licenceData.getEmail());
                    // Use local data if available, otherwise use Firebase data
                    if (localUser != null) {
                        newUser.setName(localUser.getName());
                        newUser.setAddress(localUser.getAddress());
                        newUser.setCompany(localUser.getCompany());
                        newUser.setPhone(localUser.getPhone());
                    } else {

                     if (firebaseUser!=null) {
                         newUser.setName(firebaseUser.getDisplayName());
                     }
//                        newUser.setName(context.getString(R.string.ar_long_text_20));
                        newUser.setCompany(context.getString(R.string.ar_long_text_20));
                        newUser.setAddress(context.getString(R.string.ar_text_10_1));
                        newUser.setPhone(context.getString(R.string.string_967_734_249_712));
                    }
                    newUser.setUserType("user");
                    newUser.setSuccessfulReferrals(0);
                    newUser.setIs_active(true);

                    newUser.setCreated_at(User.getCurrentLocalDateTime());
                    newUser.setLogin_count(1);
                    newUser.setDb_upgrade(1);
                    newUser.setMax_devices(LicenseManager.MAX_DEVICES);
                    newUser.setTransactions_count(0);
                    newUser.setMax_transactions(LicenseManager.FREE_TRANSACTION_LIMIT);
                    newUser.setAd_rewards(0);
                    newUser.setReferral_rewards(0);
                    newUser.setApp_Version(versionManager.getCurrentVersionName());
                    newUser.setLastVersion(versionManager.getPreviousVersion());

                    DeviceInfo currentDevice = licenseManager.getCurrentDeviceInfo();
                    newUser.getDevices().put(currentDevice.getDeviceId(), currentDevice);

                    SharedPreferences prefs = context.getSharedPreferences("AppPrefs", MODE_PRIVATE);
                    boolean isPendingReferral = prefs.getBoolean(AdsGoogleRewarder.KEY_REFERRAL_PENDING, false);
                    if (isPendingReferral) {
                        long expiryTime = prefs.getLong("premium_expiry", 0);
                        newUser.setIs_premium(true);
                        if (expiryTime != 0) {
                            Date expiryDate = new Date(expiryTime);
                            Timestamp expiryTimestamp = new Timestamp(expiryDate);
                            newUser.setSubscription_expiry(expiryTimestamp);
                        }

                        licenceData.setPremiumActivatedBy("Google Ads");
                        licenceData.setSubscriptionExpiry(expiryTime);
                        licenceData.setPlanType("monthly");
                        newUser.setPlan_type("monthly");
                        newUser.setSubscriptionStatus("activated");
                        newUser.setPremium_activated_at(User.getCurrentLocalDateTime());
                        newUser.setPremium_activated_by("Google Ads");
                        newUser.setSubscription_start(Timestamp.now());
                        newUser.setLastModified(System.currentTimeMillis());
                    } else {
                        newUser.setIs_premium(false);
                        newUser.setSubscription_expiry(null);
                        newUser.setPlan_type(null);
                        newUser.setSubscription_start(null);
                        newUser.setSubscriptionStatus(null);
                        newUser.setPremium_activated_at(null);
                        newUser.setPremium_activated_by(null);
                        newUser.setLastModified(System.currentTimeMillis());
                    }
                    userRef.set(newUser).addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
//                           repository.updateUser(newUser);
//                           referralManager.applyReferralRewardIfAvailable(firebaseUser.getUid());
                            new VersionManager(context).setFirestoreUser_isAdded(true);
                            if (authCallback != null) {
                                //  authCallback.onSignInSuccessUpgrade(firebaseUser);
                            }
                        } else {
                            if (authCallback != null)
                                authCallback.onSignInFailure(context.getString(R.string.createNewUserDataUpgrade_19));
                            AppErrorHandler.handle(context);
                        }
                    });
                }
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "createNewUserData Error: " + e);
        }
    }

    private void updateUserLoginData(DocumentReference userRef, FirebaseUser firebaseUser, AuthResult authResult) {
        // تحديث بيانات المستخدم بدون إضافة الجهاز تلقائياً
        Map<String, Object> updates = new HashMap<>();
        updates.put("updated_at", User.getCurrentLocalDateTime());
        updates.put("last_login", User.getCurrentLocalDateTime());
        updates.put("login_count", FieldValue.increment(1));
        updates.put("is_active", true);
        updates.put("max_devices", secureLicenseManager.getDevicesNos());
        updates.put("app_Version", versionManager.getCurrentVersionName());
        updates.put("app_VersionLast", versionManager.getPreviousVersion());
        updates.put("profileImageUri", firebaseUser.getPhotoUrl() != null ? firebaseUser.getPhotoUrl().toString() : "");
        userRef.set(updates, SetOptions.merge()).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "تم تحديث بيانات تسجيل الدخول للمستخدم 1 .");
                if (authCallback != null) authCallback.onSignInSuccess(firebaseUser, authResult);
            } else {
                if (authCallback != null)
                    authCallback.onSignInFailure(context.getString(R.string.updateUserLoginData_20));
            }
        });
    }

    private void updateUserLoginDataUpgrade(DocumentReference userRef, FirebaseUser firebaseUser) {
        // تحديث بيانات المستخدم بدون إضافة الجهاز تلقائياً
        boolean isFirst = new VersionManager(context).first_upgrade();
        Map<String, Object> updates = new HashMap<>();
        if (isFirst) {
            updates.put("db_upgrade", 1);
        }
        updates.put("updated_at", User.getCurrentLocalDateTime());
        updates.put("last_login", User.getCurrentLocalDateTime());
        updates.put("login_count", FieldValue.increment(1));
        updates.put("is_active", true);
        updates.put("max_devices",secureLicenseManager.getDevicesNos());
        //  updates.put("profileImageUri", firebaseUser.getPhotoUrl() != null ? firebaseUser.getPhotoUrl().toString() : "");
        updates.put("app_Version", versionManager.getCurrentVersionName());
        updates.put("app_VersionLast", versionManager.getPreviousVersion());
        userRef.set(updates, SetOptions.merge()).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "تم تحديث بيانات تسجيل الدخول للمستخدم.");
                // if (authCallback != null) authCallback.onSignInSuccessUpgrade(firebaseUser);
            } else {
                if (authCallback != null)
                    authCallback.onSignInFailure(context.getString(R.string.updateUserLoginDataUpgrade_21));
            }
        });
    }

    public void signOut(AuthCallback callback) {
        this.authCallback = callback;
        googleSignInClient.signOut().addOnCompleteListener(task -> {
            licenseManager.signOutAndClearData();
            if (callback != null) callback.onSignOutSuccess();
        });
    }

    public void signOut() {
        auth.signOut();
        googleSignInClient.signOut();
        if (facebookLoginManager != null) facebookLoginManager.logOut();
        // licenseManager.signOutAndClearData();
    }

    public boolean isSignedIn() {
        return auth.getCurrentUser() != null;
    }

    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    public static int getSignInRequestCode() {
        return RC_SIGN_IN;
    }

    // 1. بدء التحقق (إرسال SMS)
    public void startPhoneVerification(Activity activity, String phoneNumber, PhoneAuthListener listener) {
        this.phoneAuthListener = listener;

        if (!isNetworkAvailable(context)) {
            if (phoneAuthListener != null)
                phoneAuthListener.onVerificationFailed(context.getString(R.string.no_internet));
            return;
        }

        // ضبط اللغة حسب لغة الجهاز الحالية
        auth.setLanguageCode(java.util.Locale.getDefault().getLanguage());

        PhoneAuthOptions options =
                PhoneAuthOptions.newBuilder(auth)
                        .setPhoneNumber(phoneNumber)
                        .setTimeout(60L, TimeUnit.SECONDS)
                        .setActivity(activity)
                        .setCallbacks(mPhoneCallbacks)
                        .build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private final PhoneAuthProvider.OnVerificationStateChangedCallbacks mPhoneCallbacks =
            new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                @Override
                public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                    // التحقق التلقائي
                    Log.d(TAG, "onVerificationCompleted: Auto sign-in");
                    // ملاحظة: هنا الاسم سيكون null لأننا لم نطلبه بعد، سيتم التعامل معه في signInWithCredentialCommon
//                    signInWithCredentialCommon(credential, null);
                }

                @Override
                public void onVerificationFailed(@NonNull FirebaseException e) {
                    Log.e(TAG, "onVerificationFailed", e);
                    if (phoneAuthListener != null) {
                        phoneAuthListener.onVerificationFailed(e.getMessage());
                    }
                }

                @Override
                public void onCodeSent(@NonNull String verificationId,
                                       @NonNull PhoneAuthProvider.ForceResendingToken token) {
                    Log.d(TAG, "onCodeSent:" + verificationId);
                    if (phoneAuthListener != null) {
                        phoneAuthListener.onCodeSent(verificationId);
                    }
                }
            };

    // داخل GoogleAuthHelper.java

//    // متغيرات مؤقتة لحفظ بيانات الهاتف لحين اكتمال التسجيل
//    private String tempRawPhoneNumber;
//    private String tempCountryCode;
//    private String tempFullName;
//
//    // 1. تحديث دالة إكمال الدخول لتستقبل البيانات التفصيلية
//    public void signInWithPhone(String verificationId, String code, String fullName, String rawPhoneNumber, String countryCode, AuthCallback callback) {
//        this.authCallback = callback;
//
//        // حفظ البيانات مؤقتاً لاستخدامها عند إنشاء المستخدم في Firestore
//        this.tempFullName = fullName;
//        this.tempRawPhoneNumber = rawPhoneNumber;
//        this.tempCountryCode = countryCode;
//
//        if (verificationId == null) {
//            if (authCallback != null) authCallback.onSignInFailure("Verification ID is null");
//            return;
//        }
//
//        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
//        // نمرر الاسم لتحديث البروفايل
//        signInWithCredentialCommon(credential, fullName);
//    }
//
//    // 2. تحديث دالة حفظ المستخدم الجديد (checkUserExistenceInFirestore)
//// هذه الدالة موجودة في كلاسك، سنعدل طريقة تعبئة البيانات فيها
//    private void checkUserExistenceInFirestore(FirebaseUser firebaseUser, DocumentReference userRef) {
//        User newUser = new User();
//        newUser.setUserId(firebaseUser.getUid());
//
//        // --- هنا التعديل الجوهري حسب طلبك ---
//        if (tempFullName != null && !tempFullName.isEmpty()) {
//            newUser.setName(tempFullName);        // الاسم الظاهر
//            newUser.setFullName(tempFullName);    // الاسم الكامل (حسب User.java)
//        } else {
//            newUser.setName(firebaseUser.getDisplayName());
//        }
//
//        // حفظ الرقم ومفتاح الدولة بشكل منفصل
//        if (tempRawPhoneNumber != null && tempCountryCode != null) {
//            newUser.setPhoneNumber(tempRawPhoneNumber); // الرقم بدون مفتاح
//            newUser.setCountryCode(tempCountryCode);    // مفتاح الدولة
//        } else {
//            // احتياط في حال كان التسجيل عبر جوجل أو فيسبوك
//            newUser.setPhoneNumber(firebaseUser.getPhoneNumber());
//        }
//        // --------------------------------------
//
//        newUser.setEmail(firebaseUser.getEmail());
//        newUser.setCreated_at(User.getCurrentLocalDateTime());
//        newUser.setUpdated_at(User.getCurrentLocalDateTime());
//        newUser.setLast_login(User.getCurrentLocalDateTime());
//        newUser.setIs_active(true);
//        newUser.setProfileImageUri(firebaseUser.getPhotoUrl() != null ? firebaseUser.getPhotoUrl().toString() : "");
//        newUser.setApp_Version(versionManager.getCurrentVersionName());
//        newUser.setDb_upgrade(1);
//
//        userRef.set(newUser).addOnCompleteListener(task -> {
//            if (task.isSuccessful()) {
//                if (authCallback != null) authCallback.onSignInSuccessNewUser(firebaseUser);
//            } else {
//                if (authCallback != null) authCallback.onSignInFailure(context.getString(R.string.checkUserExistenceInFirestore_21));
//            }
//
//            // تنظيف المتغيرات المؤقتة
//            tempRawPhoneNumber = null; tempCountryCode = null; tempFullName = null;
//        });
//    }
//    private void checkUserExistenceInFirestore(FirebaseUser firebaseUser, DocumentReference userRef) {
//        User newUser = new User();
//        newUser.setUserId(firebaseUser.getUid());
//
//        // --- هنا التعديل الجوهري حسب طلبك ---
//        if (tempFullName != null && !tempFullName.isEmpty()) {
//            newUser.setName(tempFullName);        // الاسم الظاهر
//            newUser.setFullName(tempFullName);    // الاسم الكامل (حسب User.java)
//        } else {
//            newUser.setName(firebaseUser.getDisplayName());
//        }
//
//        // حفظ الرقم ومفتاح الدولة بشكل منفصل
//        if (tempRawPhoneNumber != null && tempCountryCode != null) {
//            newUser.setPhoneNumber(tempRawPhoneNumber); // الرقم بدون مفتاح
//            newUser.setCountryCode(tempCountryCode);    // مفتاح الدولة
//        } else {
//            // احتياط في حال كان التسجيل عبر جوجل أو فيسبوك
//            newUser.setPhoneNumber(firebaseUser.getPhoneNumber());
//        }
//        // --------------------------------------
//
//        newUser.setEmail(firebaseUser.getEmail());
//        newUser.setCreated_at(User.getCurrentLocalDateTime());
//        newUser.setUpdated_at(User.getCurrentLocalDateTime());
//        newUser.setLast_login(User.getCurrentLocalDateTime());
//        newUser.setIs_active(true);
//        newUser.setProfileImageUri(firebaseUser.getPhotoUrl() != null ? firebaseUser.getPhotoUrl().toString() : "");
//        newUser.setApp_Version(versionManager.getCurrentVersionName());
//        newUser.setDb_upgrade(1);
//
//        userRef.set(newUser).addOnCompleteListener(task -> {
//            if (task.isSuccessful()) {
//                if (authCallback != null) authCallback.onSignInSuccessNewUser(firebaseUser);
//            } else {
//                if (authCallback != null) authCallback.onSignInFailure(context.getString(R.string.checkUserExistenceInFirestore_21));
//            }
//
//            // تنظيف المتغيرات المؤقتة
//            tempRawPhoneNumber = null; tempCountryCode = null; tempFullName = null;
//        });
//    }
//    // ============================================================================================
//    //  Unified Sign-In Logic (The Core Logic)
//    // ============================================================================================
//
//      // الدالة الرئيسية التي تتعامل مع Firebase Auth وتوجه إلى saveOrUpdateUserInFirestore
//    private void signInWithCredentialCommon(AuthCredential credential, String manualUserName) {
//        auth.signInWithCredential(credential).addOnCompleteListener(task -> {
//            if (task.isSuccessful()) {
//                FirebaseUser user = auth.getCurrentUser();
//
//                // منطق خاص بالهاتف: تحديث الاسم قبل الحفظ
//                if (user != null && manualUserName != null && !manualUserName.isEmpty()) {
//                    UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
//                            .setDisplayName(manualUserName)
//                            .build();
//
//                    user.updateProfile(profileUpdates).addOnCompleteListener(profileTask -> {
//                        if (authCallback != null) {
//                            authCallback.onSignInProgress(context.getString(R.string.loading_user_data));
//
//                            boolean isNewUser = Objects.requireNonNull(task.getResult().getAdditionalUserInfo()).isNewUser();
//                            Log.d(TAG, "Is new user: " + isNewUser);
//                            saveOrUpdateUserInFirestore(user, task.getResult());
//                        }
//
//                    });
//                } else {
//                    String errorMessage = context.getString(R.string.auth_failure);
//                    if (task.getException() != null) {
//                        errorMessage += ": " + task.getException().getMessage();
//                    }
//                    if (authCallback != null) {
//                        authCallback.onSignInFailure(errorMessage);
//                    }
//                }
//
//            } else {
//                Log.e(TAG, "SignIn Failed", task.getException());
//                if (authCallback != null) authCallback.onSignInFailure(task.getException() != null ?
//                        task.getException().getMessage() : "Authentication Failed");
//            }
//        });
//    }
}