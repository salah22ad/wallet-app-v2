package com.hpp.daftree.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;
import com.hpp.daftree.MainActivity;
import com.hpp.daftree.R;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.database.User;
import com.hpp.daftree.databinding.ActivityLockScreenBinding;
import com.hpp.daftree.databinding.DialogChangePasswordBinding;
import com.hpp.daftree.helpers.LanguageManager;
import com.hpp.daftree.models.AppLockManager;
import com.hpp.daftree.utils.SecureLicenseManager;
import com.hpp.daftree.viewmodels.ProfileViewModel;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;

public class LockScreenActivity extends AppCompatActivity {

    private static final String TAG = "LockScreenActivity";

    private ActivityLockScreenBinding binding;
    private AppLockManager lockManager;
    private ProfileViewModel viewModel;
    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;
    private FirebaseFunctions firebaseFunctions;
    private AlertDialog sendingDialog;

    private User cachedUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLockScreenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        lockManager = new AppLockManager(this);
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        executor = ContextCompat.getMainExecutor(this);
        firebaseFunctions = FirebaseFunctions.getInstance();

        viewModel.getUserProfile().observe(this, user -> {
            this.cachedUser = user;
            if (user != null && binding.passwordEditText.getText() != null
                    && !TextUtils.isEmpty(binding.passwordEditText.getText().toString())
                    && binding.passwordEditText.getText().toString().equals(user.getPassword())) {
                navigateToMain();
            }
        });

        String lockType = lockManager.getLockType();
        if ("biometric".equals(lockType)) {
            setupBiometricPrompt();
            biometricPrompt.authenticate(promptInfo);
            setupPasswordScreen();
        } else if ("password".equals(lockType)) {
            setupPasswordScreen();
        } else {
            navigateToMain();
        }
    }

    private void setupBiometricPrompt() {
        biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                navigateToMain();
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    finish();
                }
            }
        });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.lock_title))
                .setSubtitle(getString(R.string.use_biometric))
                .setNegativeButtonText(getString(R.string.close))
                .build();
    }

    private void setupPasswordScreen() {
        binding.passwordEditText.setVisibility(View.VISIBLE);
        binding.passwordEditText.setText("");

        binding.passwordEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String enteredPassword = s.toString().trim();
                if (!enteredPassword.isEmpty()) {
                    verifyPasswordAutomatically(enteredPassword);
                }
            }
        });

        binding.btnEnter.setOnClickListener(v -> {
            String enteredPassword = binding.passwordEditText.getText() != null
                    ? binding.passwordEditText.getText().toString().trim()
                    : "";
            verifyPasswordManually(enteredPassword);
        });

        binding.tvChangePassword.setOnClickListener(v -> showChangePasswordDialog());
        binding.tvForgotPassword.setOnClickListener(v -> handleForgotPassword());
    }

    private void verifyPasswordManually(String enteredPassword) {
        if (TextUtils.isEmpty(enteredPassword)) {
            binding.passwordLayout.setError(getString(R.string.lock_error_empty));
            return;
        }
        binding.passwordLayout.setError(null);

        if (cachedUser != null && enteredPassword.equals(cachedUser.getPassword())) {
            navigateToMain();
        } else {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.error_text))
                    .setMessage(getString(R.string.lock_error_incorrect))
                    .setPositiveButton(getString(R.string.ok), null)
                    .show();
            binding.passwordEditText.setText("");
        }
    }

    private void verifyPasswordAutomatically(String enteredPassword) {
        if (cachedUser != null && enteredPassword.equals(cachedUser.getPassword())) {
            Log.d(TAG, "Password verified automatically - opening app");
            navigateToMain();
        }
    }

    private void showChangePasswordDialog() {
        DialogChangePasswordBinding dialogBinding = DialogChangePasswordBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.change_password_title))
                .setView(dialogBinding.getRoot())
                .setPositiveButton(getString(R.string.change_password_confirm), null)
                .setNegativeButton(getString(R.string.change_password_cancel), null)
                .create();

        dialog.setOnShowListener(dialogInterface ->
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String oldPass = dialogBinding.oldPasswordEdit.getText() != null
                            ? dialogBinding.oldPasswordEdit.getText().toString() : "";
                    String newPass = dialogBinding.newPasswordEdit.getText() != null
                            ? dialogBinding.newPasswordEdit.getText().toString() : "";
                    String confirmPass = dialogBinding.confirmPasswordEdit.getText() != null
                            ? dialogBinding.confirmPasswordEdit.getText().toString() : "";

                    if (TextUtils.isEmpty(oldPass) || TextUtils.isEmpty(newPass) || TextUtils.isEmpty(confirmPass)) {
                        Toast.makeText(this, getString(R.string.change_password_error_fields), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!newPass.equals(confirmPass)) {
                        dialogBinding.confirmPasswordLayout.setError(getString(R.string.change_password_error_mismatch));
                        return;
                    }

                    if (cachedUser != null && oldPass.equals(cachedUser.getPassword())) {
                        cachedUser.setPassword(newPass);
                        viewModel.updateUserProfile(cachedUser);
                        Toast.makeText(this, getString(R.string.change_password_success), Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    } else {
                        dialogBinding.oldPasswordLayout.setError(getString(R.string.change_password_error_old_incorrect));
                    }
                })
        );
        dialog.show();
    }

    // =========================================================================
    // استعادة كلمة مرور القفل تلقائياً عبر البريد الإلكتروني (Firebase Cloud Function)
    // =========================================================================

    private void handleForgotPassword() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            User localUser = AppDatabase.getDatabase(getApplicationContext()).userDao().getUserProfileBlocking();
            String savedPassword = (localUser != null) ? localUser.getPassword() : null;

            boolean isGuest = SecureLicenseManager.getInstance(getApplicationContext()).isGuest();
            FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
            String authEmail = (firebaseUser != null) ? firebaseUser.getEmail() : null;

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;

                if (TextUtils.isEmpty(savedPassword)) {
                    Toast.makeText(this, getString(R.string.forgot_password_not_found), Toast.LENGTH_LONG).show();
                    return;
                }

                // التحقق من وجود حساب رسمي ببريد حقيقي (يعمل مع Gmail وYahoo وOutlook وأي مزوّد آخر)
                if (isGuest || firebaseUser == null || !isValidRealEmail(authEmail)) {
                    showNoEmailWarningDialog();
                    return;
                }

                showConfirmEmailRecoveryDialog(authEmail.trim(), savedPassword);
            });
        });
    }

    private boolean isValidRealEmail(@Nullable String email) {
        if (TextUtils.isEmpty(email)) return false;
        String trimmed = email.trim().toLowerCase(Locale.US);
        if (trimmed.endsWith("@facebook.com")) return false;
        return Patterns.EMAIL_ADDRESS.matcher(trimmed).matches();
    }

    private void showNoEmailWarningDialog() {
        if (isFinishing() || isDestroyed()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.forgot_password_alert_title))
                .setMessage(getString(R.string.forgot_password_no_email_message))
                .setPositiveButton(getString(R.string.ok), null)
                .show();
    }

    private void showConfirmEmailRecoveryDialog(String registeredEmail, String password) {
        if (isFinishing() || isDestroyed()) return;
        String displayEmail = "\u200E" + registeredEmail + "\u200E";
        String message = getString(R.string.forgot_password_email_confirm_message, displayEmail);

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.forgot_password_alert_title))
                .setMessage(message)
                .setPositiveButton(getString(R.string.forgot_password_confirm_send), (d, w) ->
                        sendPasswordToRegisteredEmail(registeredEmail, password))
                .setNeutralButton(getString(R.string.forgot_password_other_options), (d, w) ->
                        showOtherOptionsAdviceDialog())
                .setNegativeButton(getString(R.string.forgot_password_lock_cancel), null)
                .show();
    }

    private void showOtherOptionsAdviceDialog() {
        if (isFinishing() || isDestroyed()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.forgot_password_other_options_title))
                .setMessage(getString(R.string.forgot_password_other_options_advice))
                .setPositiveButton(getString(R.string.ok), null)
                .show();
    }

    private void sendPasswordToRegisteredEmail(String registeredEmail, String password) {
        if (!isNetworkAvailable()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.error_text))
                    .setMessage(getString(R.string.no_internet))
                    .setPositiveButton(getString(R.string.ok), null)
                    .show();
            return;
        }

        showSendingProgressDialog();

        // جلب نصوص رسالة البريد واتجاهها مباشرةً من لغة التطبيق الحالية
        Resources localizedRes = LanguageManager.getLocalizedResources(this);
        boolean isRtl = LanguageManager.getInstance(this).isRTL();

        Map<String, Object> payload = new HashMap<>();
        payload.put("password", password);
        payload.put("appName", localizedRes.getString(R.string.app_name));
        payload.put("subject", localizedRes.getString(R.string.forgot_password_email_subject));
        payload.put("greeting", localizedRes.getString(R.string.forgot_password_email_greeting));
        payload.put("intro", localizedRes.getString(R.string.forgot_password_email_intro));
        payload.put("securityNote", localizedRes.getString(R.string.forgot_password_email_security_note));
        payload.put("isRtl", isRtl);

        firebaseFunctions
                .getHttpsCallable("sendAppLockPasswordEmail")
                .call(payload)
                .addOnCompleteListener(this, task -> {
                    dismissSendingProgressDialog();
                    if (isFinishing() || isDestroyed()) return;

                    if (task.isSuccessful()) {
                        String displayEmail = "\u200E" + registeredEmail + "\u200E";
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(getString(R.string.forgot_password_email_sent_title))
                                .setMessage(getString(R.string.forgot_password_email_sent_message, displayEmail))
                                .setPositiveButton(getString(R.string.ok), null)
                                .show();
                    } else {
                        Exception ex = task.getException();
                        Log.e(TAG, "sendAppLockPasswordEmail failed", ex);
                        String errMsg = getString(R.string.forgot_password_email_failed);

                        if (ex instanceof FirebaseFunctionsException) {
                            FirebaseFunctionsException.Code code = ((FirebaseFunctionsException) ex).getCode();
                            if (code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED) {
                                errMsg = getString(R.string.forgot_password_rate_limited);
                            } else if (code == FirebaseFunctionsException.Code.FAILED_PRECONDITION
                                    || code == FirebaseFunctionsException.Code.UNAUTHENTICATED) {
                                errMsg = getString(R.string.forgot_password_no_email_message);
                            }
                        }

                        new MaterialAlertDialogBuilder(this)
                                .setTitle(getString(R.string.error_text))
                                .setMessage(errMsg)
                                .setPositiveButton(getString(R.string.ok), null)
                                .show();
                    }
                });
    }

    private void showSendingProgressDialog() {
        dismissSendingProgressDialog();
        if (isFinishing() || isDestroyed()) return;

        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (24 * density);
        int gap = (int) (16 * density);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        container.setPadding(pad, pad, pad, pad);

        ProgressBar progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        pbParams.setMarginEnd(gap);
        container.addView(progressBar, pbParams);

        TextView tvMessage = new TextView(this);
        tvMessage.setText(R.string.forgot_password_sending_email);
        tvMessage.setTextSize(15f);
        container.addView(tvMessage, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ));

        sendingDialog = new MaterialAlertDialogBuilder(this)
                .setView(container)
                .setCancelable(false)
                .create();
        sendingDialog.show();
    }

    private void dismissSendingProgressDialog() {
        try {
            if (sendingDialog != null && sendingDialog.isShowing()) {
                sendingDialog.dismiss();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error dismissing progress dialog", e);
        } finally {
            sendingDialog = null;
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    @Override
    protected void onDestroy() {
        dismissSendingProgressDialog();
        super.onDestroy();
    }

    private void navigateToMain() {
        Intent mainIntent = new Intent(this, MainActivity.class);
        if (getIntent() != null && getIntent().getExtras() != null) {
            mainIntent.putExtras(getIntent().getExtras());
        }
        startActivity(mainIntent);
        finish();
    }
}
