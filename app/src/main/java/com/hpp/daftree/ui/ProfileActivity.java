package com.hpp.daftree.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;
import com.hpp.daftree.SplashActivity;
import com.hpp.daftree.models.AppAuth;
import com.hpp.daftree.viewmodels.ProfileViewModel;
import com.hpp.daftree.R;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.database.User;
import com.hpp.daftree.databinding.ActivityProfileBinding;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.syncmanagers.FirestoreSyncManager;
import com.hpp.daftree.syncmanagers.SyncPreferences;

import com.hpp.daftree.utils.GoogleAuthHelper;
import com.hpp.daftree.utils.LicenseManager;
import com.hpp.daftree.utils.SecureLicenseManager;
import com.hpp.daftree.utils.VersionManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class ProfileActivity extends BaseActivity {

    private ActivityProfileBinding binding;
    private ProfileViewModel viewModel;
    private User currentUserProfile;
    private FirebaseUser firebaseUser;
    private Uri selectedImageUri;

    private SyncPreferences sharedPrefsManager;
    private DaftreeRepository repository;
    private GoogleAuthHelper googleAuthHelper;
    private FirebaseFunctions firebaseFunctions;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    Glide.with(this)
                            .load(selectedImageUri)
                            .into(binding.profileImageView);
                }
            }
    );
    private boolean isGuest;
    private SharedPreferences prefs;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupFloatingToolbar(binding.scrollViewLayout);
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        isGuest = SecureLicenseManager.getInstance(this).isGuest();
        String guestUID = SecureLicenseManager.getInstance(this).guestUID();
        prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        // تهيئة Firebase Functions
        firebaseFunctions = FirebaseFunctions.getInstance();

        sharedPrefsManager = new SyncPreferences(this);
        repository = new DaftreeRepository(getApplication());
        if (!isGuest) {
            googleAuthHelper = new GoogleAuthHelper(this, new LicenseManager(this), repository);
            firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
            if (firebaseUser == null) {
                Toast.makeText(this, getString(R.string.ar_long_text_15), Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            binding.deleteProfileButton.setVisibility(View.VISIBLE);
        } else {
            binding.deleteProfileButton.setVisibility(View.GONE);
        }
        observeUserProfile();
        binding.profileImageLayout.setOnClickListener(v -> openGallery());
        binding.saveProfileButton.setOnClickListener(v -> saveUserProfile());
        binding.deleteProfileButton.setOnClickListener(v -> deleteAccountDirectly()); // تم التعديل هنا
    }

    /**
     * الدالة الجديدة لحذف الحساب مباشرة دون فتح صفحة الويب
     */
    private void deleteAccountDirectly() {
        // عرض تأكيد الحذف
        new android.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_account))
                .setMessage(getString(R.string.delete_account_message))
                .setPositiveButton(getString(R.string.yes_delete_acc), (dialog, which) -> {
                    showLoading(true);
                    executeAccountDeletion();
                })
                .setNegativeButton(getString(R.string.btn_undo), (dialog, which) -> dialog.dismiss())
                .show();
    }

    /**
     * تنفيذ عملية حذف الحساب عبر Firebase Functions
     */
    private void executeAccountDeletion() {
        if (isGuest) {
           // Toast.makeText(this, "لا يمكن حذف الحساب ", Toast.LENGTH_SHORT).show();
            showLoading(false);
            return;
        }

        // إعداد بيانات الحذف
        Map<String, Object> data = new HashMap<>();
        data.put("reason", "user_initiated_from_app");
        data.put("customReason", "تم الحذف مباشرة من التطبيق");
        data.put("language", com.hpp.daftree.helpers.LanguageManager.getInstance(this).getCurrentLanguage());

        // استدعاء دالة Firebase Functions
        firebaseFunctions
                .getHttpsCallable("deleteUserAccount")
                .call(data)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // تم حذف الحساب بنجاح من السيرفر
                        HttpsCallableResult result = task.getResult();
                        if (result != null) {
                            Object dataResult = result.getData();
                            if (dataResult instanceof Map) {
                                Map<String, Object> resultData = (Map<String, Object>) dataResult;
                                Boolean success = (Boolean) resultData.get("success");
                                if (success != null && success) {
                                    // نجح الحذف من السيرفر، الآن نقوم بحذف البيانات المحلية
                                    completeLocalDeletion();
                                    return;
                                }
                            }
                        }
                        // إذا لم يكن هناك نتيجة صحيحة
                        showDeletionError(getString(R.string.error_generic));
                    } else {
                        // فشل استدعاء الدالة
                        Exception exception = task.getException();
                        String errorMessage = getString(R.string.error_generic) +  ": " +
                                (exception != null ? exception.getMessage() : "خطأ غير معروف");
                        showDeletionError(errorMessage);
                    }
                });
    }
    /**
     * إكمال عملية الحذف المحلي الشامل (مكافئ لمسح بيانات التطبيق من إعدادات الهاتف)
     */
    private void completeLocalDeletion() {
        try {
            // 1. إيقاف كافة المهام الخلفية والمستمعين والمنبهات قبل لمس الملفات
            try {
                androidx.work.WorkManager.getInstance(getApplicationContext()).cancelAllWork();
            } catch (Exception ignored) {}

            try {
                FirestoreSyncManager.getInstance().stopListening();
            } catch (Exception ignored) {}

            try {
                LicenseManager.getInstance(this).stopAndResetLicenseListener();
            } catch (Exception ignored) {}

            try {
                com.hpp.daftree.dailyreminder.DailyReminderScheduler.cancelScheduledReminder(this);
            } catch (Exception ignored) {}

            // 2. تسجيل الخروج من Firebase و Google
            FirebaseAuth.getInstance().signOut();
            if (googleAuthHelper != null) {
                googleAuthHelper.signOut(new GoogleAuthHelper.AuthCallback() {
                    @Override public void onAccountInfoReceived(String email, String token, String provider) {}
                    @Override public void onSignInProgress(String message) {}
                    @Override public void onSignInSuccess(FirebaseUser user, AuthResult authResult) {}
                    @Override public void onSignInFailure(String error) {}
                    @Override public void onSignOutSuccess() {}
                });
            }

            // 3. إغلاق وتصفير قاعدة البيانات وحذف كافة ملفات التطبيق في خيط خلفي
            AppDatabase.databaseWriteExecutor.execute(() -> {
                try {
                    try {
                        AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
                        if (db != null) {
                            db.clearAllTables();
                            db.close();
                        }
                        AppDatabase.destroyInstance();
                    } catch (Exception e) {
                        Log.e("ProfileActivity", "خطأ أثناء إغلاق قاعدة البيانات: " + e.getMessage());
                    }

                    // مسح كافة ملفات SharedPreferences من الذاكرة والقرص
                    clearAllSharedPreferences();

                    // حذف ملفات SQLite وملفات التطبيق والكاش
                    deleteDatabaseFiles();
                    deleteAppFiles();

                    // تهيئة قاعدة بيانات نظيفة جديدة قبل إعادة التشغيل
                    try {
                        AppDatabase freshDb = AppDatabase.getDatabase(getApplicationContext());
                        if (freshDb != null) {
                            freshDb.close();
                        }
                        AppDatabase.destroyInstance();
                    } catch (Exception ignored) {}

                    runOnUiThread(() -> {
                        showLoading(false);
                        restartApplication();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        showDeletionError(getString(R.string.error_generic) + ": " + e.getMessage());
                    });
                }
            });

        } catch (Exception e) {
            showLoading(false);
            showDeletionError(getString(R.string.error_generic) + ": " + e.getMessage());
        }
    }

    /**
     * مسح جميع إعدادات SharedPreferences (دون إعادة كتابة قيم لتبدأ SplashActivity كأول تشغيل)
     */
    private void clearAllSharedPreferences() {
        try {
            File sharedPrefsDir = new File(getApplicationInfo().dataDir, "shared_prefs");
            if (sharedPrefsDir.exists() && sharedPrefsDir.isDirectory()) {
                File[] prefFiles = sharedPrefsDir.listFiles();
                if (prefFiles != null) {
                    for (File file : prefFiles) {
                        String name = file.getName();
                        if (name.endsWith(".xml")) {
                            String prefName = name.substring(0, name.length() - 4);
                            try {
                                getSharedPreferences(prefName, MODE_PRIVATE).edit().clear().commit();
                            } catch (Exception ignored) {}
                        }
                        file.delete();
                    }
                }
            }
            Log.d("ProfileActivity", "تم مسح جميع ملفات SharedPreferences بالكامل");
        } catch (Exception e) {
            Log.e("ProfileActivity", "خطأ في مسح SharedPreferences: " + e.getMessage());
        }
    }

    /**
     * حذف جميع ملفات قاعدة البيانات
     */
    private void deleteDatabaseFiles() {
        try {
            File databasesDir = new File(getApplicationInfo().dataDir, "databases");
            if (databasesDir.exists() && databasesDir.isDirectory()) {
                File[] files = databasesDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        deleteRecursive(file);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("ProfileActivity", "خطأ في حذف ملفات قاعدة البيانات: " + e.getMessage());
        }
    }

    /**
     * حذف ملفات التطبيق والكاش
     */
    private void deleteAppFiles() {
        try {
            deleteRecursive(getCacheDir());
            if (getExternalCacheDir() != null) {
                deleteRecursive(getExternalCacheDir());
            }
            File filesDir = getFilesDir();
            if (filesDir != null && filesDir.exists() && filesDir.isDirectory()) {
                File[] appFiles = filesDir.listFiles();
                if (appFiles != null) {
                    for (File file : appFiles) {
                        deleteRecursive(file);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("ProfileActivity", "خطأ في حذف ملفات التطبيق: " + e.getMessage());
        }
    }

    private void deleteRecursive(File fileOrDir) {
        if (fileOrDir == null || !fileOrDir.exists()) return;
        if (fileOrDir.isDirectory()) {
            File[] children = fileOrDir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDir.delete();
    }

    /**
     * إعادة تشغيل التطبيق من SplashActivity بنفس منطق MainActivity.restartAppFromSplash()
     */
    private void restartApplication() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(ProfileActivity.this, SplashActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK
                    | Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finishAffinity();
            android.os.Process.killProcess(android.os.Process.myPid());
        }, 500);
    }

    private void showDeletionError(String errorMessage) {
       if(isFinishing() || isDestroyed())
           return;
        showLoading(false);
        new android.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_title))
//                .setMessage(errorMessage)
                .setMessage(getString(R.string.delete_message))
                .setPositiveButton(getString(R.string.retry_again), (dialog, which) -> executeAccountDeletion())
                .setNegativeButton(getString(R.string.cancel), (dialog, which) -> dialog.dismiss())
                .show();
        Log.e("ProvileActivity", "Error deleting account: " + errorMessage);
    }



    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void observeUserProfile() {
        showLoading(true);
        viewModel.getUserProfile().observe(this, user -> {
            showLoading(false);
            if (user != null) {
                currentUserProfile = user;
                binding.profileNameEdittext.setText(user.getName());
                binding.profileCompanyEdittext.setText(user.getCompany());
                binding.profileAddressEdittext.setText(user.getAddress());
                binding.profilePhoneEdittext.setText(user.getPhone());
                binding.profileEmailEdittext.setText(SecureLicenseManager.getInstance(this).getEmail());

                String imagePath = user.getProfileImageUri();
                if (imagePath != null && !imagePath.isEmpty()) {
                    Glide.with(this)
                            .load(new File(imagePath))
                            .placeholder(R.drawable.ic_person)
                            .into(binding.profileImageView);
                }
            } else {
                currentUserProfile = new User();
                if (firebaseUser != null) {
                    binding.profileNameEdittext.setText(firebaseUser.getDisplayName());
                    binding.profileEmailEdittext.setText(firebaseUser.getEmail());
                }
            }
        });
    }

    private void saveUserProfile() {
        String name = binding.profileNameEdittext.getText().toString().trim();
        String company = binding.profileCompanyEdittext.getText().toString().trim();
        String address = binding.profileAddressEdittext.getText().toString().trim();
        String phone = binding.profilePhoneEdittext.getText().toString().trim();
        String email = binding.profileEmailEdittext.getText().toString().trim();
        if (name.isEmpty()) {
            binding.profileNameEdittext.setError(getString(R.string.error_name_required));
            return;
        }

        if (currentUserProfile == null) {
            currentUserProfile = new User();
        }
        Map<String, Object> data = new HashMap<>();
        String users, uid;
        if (isGuest) {
            users = "guests";
            uid = SecureLicenseManager.getInstance(this).guestUID();
            data.clear();
            data.put("name", name);
            data.put("company", company);
            data.put("address", address);
            data.put("phone",phone );

        } else {
            users = "users";
            uid = AppAuth.getUserID(this);
            data.clear();
            data.put("name", name);
            data.put("company", company);
            data.put("address", address);
            data.put("phone",phone );
        }
        if(uid == null || uid.isEmpty()) return;
        if (currentUserProfile == null) {
            currentUserProfile = new User();
        }
        FirebaseFirestore.getInstance().collection(users).document(uid).set(data, SetOptions.merge());
        currentUserProfile.setName(name);
        currentUserProfile.setCompany(company);
        currentUserProfile.setAddress(address);
        currentUserProfile.setPhone(phone);
        currentUserProfile.setEmail(email);
        currentUserProfile.setOwnerUID(uid);
        currentUserProfile.setSyncStatus("EDITED");
        if (selectedImageUri != null) {
            String imagePath = saveImageToInternalStorage(selectedImageUri);
            if (imagePath != null) {
                currentUserProfile.setProfileImageUri(imagePath);
            }
        }

        viewModel.updateUserProfile(currentUserProfile);
        Toast.makeText(this, "تم حفظ البيانات بنجاح", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void showLoading(boolean isLoading) {
        binding.profileProgressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.saveProfileButton.setVisibility(isLoading ? View.GONE : View.VISIBLE);
       // binding.deleteProfileButton.setVisibility(isLoading ? View.GONE : View.VISIBLE);
    }

    private String saveImageToInternalStorage(Uri uri) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            File file = new File(getFilesDir(), "profile_image.jpg");
            try (FileOutputStream outputStream = new FileOutputStream(file)) {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
            }
            return file.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}