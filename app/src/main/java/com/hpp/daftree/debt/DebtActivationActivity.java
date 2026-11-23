package com.hpp.daftree.debt;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.telephony.TelephonyManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.hbb20.CountryCodePicker;
import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.databinding.ActivityDebtActivationBinding;
import com.hpp.daftree.helpers.AppFontHelper;
import com.hpp.daftree.ui.BaseActivity;
import com.hpp.daftree.utils.DialogHelper;
import com.hpp.daftree.utils.SecureLicenseManager;
import com.hpp.daftree.viewmodels.DebtActivationViewModel;

import io.michaelrocks.libphonenumber.android.PhoneNumberUtil;
import io.michaelrocks.libphonenumber.android.Phonenumber;
import java.util.Locale;

public class DebtActivationActivity extends BaseActivity {

    private ActivityDebtActivationBinding binding;
    private DebtActivationViewModel viewModel;
    private AlertDialog codeDialog;
    private SecureLicenseManager secureLicenseManager;
    boolean isGuest = false;
    boolean isEditeMode = false;
    private String currentSavedPhone;

    // متغيرات مساعدة للاحتفاظ ببيانات الرقم الجديد أثناء عملية إلغاء الربط للرقم القديم
    private boolean pendingPhoneChange = false;
    private String pFullNumber, pRawNumber, pCountryCode, pName;

    // مشغل (Launcher) لاستقبال الرقم من ميزة الجلب التلقائي (One-Tap)
    private final ActivityResultLauncher<IntentSenderRequest> phoneNumberHintLauncher =
            registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    try {
                        String phoneNumber = Identity.getSignInClient(this).getPhoneNumberFromIntent(result.getData());
                        applyValidatedPhoneHint(phoneNumber);
                    } catch (Exception e) {
                        Log.e("PhoneHint", "فشل جلب الرقم", e);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityDebtActivationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupEdgeToEdgeInsets();
        MyApplication.applyGlobalTextWatcher(binding.getRoot());
        secureLicenseManager = SecureLicenseManager.getInstance(this);
        isGuest = secureLicenseManager.isGuest();
        if (isGuest) {
            DialogHelper.show(this, getString(R.string.login_1), DialogHelper.FeedbackType.ERROR, null);
            finish();
            return;
        }
        setupCCPLanguage();
        viewModel = new ViewModelProvider(this).get(DebtActivationViewModel.class);

        isEditeMode = getIntent().getBooleanExtra("editeData", false);
        if (isEditeMode) {
            binding.cardFeature.setVisibility(View.GONE);
            binding.imageView.setVisibility(View.VISIBLE);
            binding.btnActivate.setText(getString(R.string.edit_data));

            currentSavedPhone = secureLicenseManager.getPhoneNumber();
            binding.etFullName.setText(secureLicenseManager.getFullName());
            binding.etPhoneNumber.setText(currentSavedPhone);
            binding.btnUnlinkPhone.setVisibility(View.VISIBLE);
        } else {
            binding.btnActivate.setText(getString(R.string.btn_activate));
            binding.btnUnlinkPhone.setVisibility(View.GONE);
            requestPhoneNumberHint();
        }

        setupCCP();
        setupKeyboardAutoScroll();
        observeViewModel();

        binding.btnActivate.setOnClickListener(v -> handleActivationClick());
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnUnlinkPhone.setOnClickListener(v -> showUnlinkConfirmationDialog());

        binding.layoutMoreInfoLink.setOnClickListener(v -> {
            String updateUrl = "https://hpp-daftree.web.app/grossery-info.html";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl));
            startActivity(intent);
        });
    }

    private void requestPhoneNumberHint() {
        GetPhoneNumberHintIntentRequest request = GetPhoneNumberHintIntentRequest.builder().build();
        Identity.getSignInClient(this)
                .getPhoneNumberHintIntent(request)
                .addOnSuccessListener(result -> {
                    try {
                        IntentSenderRequest intentSenderRequest = new IntentSenderRequest.Builder(result.getIntentSender()).build();
                        phoneNumberHintLauncher.launch(intentSenderRequest);
                    } catch (Exception e) {
                        Log.e("Auth", "فشل تشغيل نافذة الرقم", e);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Auth", "الميزة غير مدعومة أو تم رفضها، سنعتمد الإدخال اليدوي", e);
                });
    }

    private void setupCCPLanguage() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String savedLanguage = prefs.getString("language", Locale.getDefault().getLanguage());
        if (savedLanguage != null) {
            savedLanguage = savedLanguage.trim().toLowerCase(Locale.US);
            if (savedLanguage.contains("_")) savedLanguage = savedLanguage.split("_")[0];
            if (savedLanguage.contains("-")) savedLanguage = savedLanguage.split("-")[0];
        } else {
            savedLanguage = "en";
        }

        switch (savedLanguage) {
            case "ar":
                binding.ccp.changeDefaultLanguage(CountryCodePicker.Language.ARABIC);
                break;
            case "cs":
                binding.ccp.changeDefaultLanguage(CountryCodePicker.Language.CZECH);
                break;
            case "da":
                binding.ccp.changeDefaultLanguage(CountryCodePicker.Language.DANISH);
                break;
            case "de":
                binding.ccp.changeDefaultLanguage(CountryCodePicker.Language.GERMAN);
                break;
            case "es":
                binding.ccp.changeDefaultLanguage(CountryCodePicker.Language.SPANISH);
                break;
            case "fr":
                binding.ccp.changeDefaultLanguage(CountryCodePicker.Language.FRENCH);
                break;
            case "hi":
                binding.ccp.changeDefaultLanguage(CountryCodePicker.Language.HINDI);
                break;
            case "id":
            case "in":
                binding.ccp.changeDefaultLanguage(CountryCodePicker.Language.INDONESIA);
                break;
            case "it":
                binding.ccp.changeDefaultLanguage(CountryCodePicker.Language.ITALIAN);
                break;
            case "iw":
            case "he":
                binding.ccp.changeDefaultLanguage(CountryCodePicker.Language.HEBREW);
                break;
            case "ja":
                binding.ccp.changeDefaultLanguage(CountryCodePicker.Language.JAPANESE);
                break;
            case "ko":
                binding.ccp.changeDefaultLanguage(CountryCodePicker.Language.KOREAN);
                break;
            case "nl":
                binding.ccp.changeDefaultLanguage(CountryCodePicker.Language.DUTCH);
                break;
            case "pl":
                binding.ccp.changeDefaultLanguage(CountryCodePicker.Language.POLISH);
                break;
            case "pt":
                binding.ccp.changeDefaultLanguage(CountryCodePicker.Language.PORTUGUESE);
                break;
            case "ru":
                binding.ccp.changeDefaultLanguage(CountryCodePicker.Language.RUSSIAN);
                break;
            case "sk":
                binding.ccp.changeDefaultLanguage(CountryCodePicker.Language.SLOVAK);
                break;
            case "sv":
                binding.ccp.changeDefaultLanguage(CountryCodePicker.Language.SWEDISH);
                break;
            case "th":
                binding.ccp.changeDefaultLanguage(CountryCodePicker.Language.THAI);
                break;
            case "tr":
                binding.ccp.changeDefaultLanguage(CountryCodePicker.Language.TURKISH);
                break;
            case "uk":
                binding.ccp.changeDefaultLanguage(CountryCodePicker.Language.UKRAINIAN);
                break;
            case "vi":
                binding.ccp.changeDefaultLanguage(CountryCodePicker.Language.VIETNAMESE);
                break;
            case "zh":
                binding.ccp.changeDefaultLanguage(CountryCodePicker.Language.CHINESE_SIMPLIFIED);
                break;
            case "ur":
                binding.ccp.changeDefaultLanguage(CountryCodePicker.Language.URDU);
                break;
            case "fa":
                binding.ccp.changeDefaultLanguage(CountryCodePicker.Language.FARSI);
                break;
            default:
                binding.ccp.changeDefaultLanguage(CountryCodePicker.Language.ENGLISH);
                break;
        }
    }

    private void setupEdgeToEdgeInsets() {
        View spacer = findViewById(R.id.status_bar_spacer);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());

            if (spacer != null) {
                android.view.ViewGroup.LayoutParams params = spacer.getLayoutParams();
                if (params != null && params.height != systemBars.top) {
                    params.height = systemBars.top;
                    spacer.setLayoutParams(params);
                }
            }

            int bottomInset = Math.max(systemBars.bottom, ime.bottom);
            view.setPadding(systemBars.left, 0, systemBars.right, 0);
            if (binding.mainContentView != null) {
                binding.mainContentView.setPadding(
                        binding.mainContentView.getPaddingLeft(),
                        binding.mainContentView.getPaddingTop(),
                        binding.mainContentView.getPaddingRight(),
                        bottomInset + 24
                );
                if (ime.bottom > 0) {
                    binding.mainContentView.post(() -> scrollActiveInputIntoView());
                }
            }
            return windowInsets;
        });
    }

    private void setupCCP() {
        binding.ccp.registerCarrierNumberEditText(binding.etPhoneNumber);
        binding.ccp.setNumberAutoFormattingEnabled(true);
        if (isEditeMode) {
            String savedCode = secureLicenseManager.getCountryCode();
            if (!TextUtils.isEmpty(savedCode)) {
                try {
                    int codeInt = Integer.parseInt(savedCode.replaceAll("[^0-9]", ""));
                    if (codeInt > 0) {
                        binding.ccp.setCountryForPhoneCode(codeInt);
                        return;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        String detectedIso = detectBestCountryIso();
        if (!TextUtils.isEmpty(detectedIso)) {
            binding.ccp.setCountryForNameCode(detectedIso);
        }
    }

    private void setupKeyboardAutoScroll() {
        View.OnFocusChangeListener focusScroller = (v, hasFocus) -> {
            if (hasFocus && binding != null && binding.mainContentView != null) {
                binding.mainContentView.postDelayed(this::scrollActiveInputIntoView, 220);
            }
        };
        binding.etFullName.setOnFocusChangeListener(focusScroller);
        binding.etPhoneNumber.setOnFocusChangeListener(focusScroller);
    }

    private void scrollActiveInputIntoView() {
        if (binding == null || binding.mainContentView == null) return;
        binding.mainContentView.smoothScrollTo(0, binding.btnActivate.getBottom());
    }

    private String detectBestCountryIso() {
        try {
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                String netIso = tm.getNetworkCountryIso();
                if (netIso != null && netIso.trim().length() == 2) {
                    return netIso.trim().toUpperCase(Locale.US);
                }
                String simIso = tm.getSimCountryIso();
                if (simIso != null && simIso.trim().length() == 2) {
                    return simIso.trim().toUpperCase(Locale.US);
                }
            }
        } catch (Exception e) {
            Log.w("PhoneHint", "Country ISO detection failed", e);
        }
        return "YE";
    }

    private boolean isLikelyImsiOrProgrammingId(String rawHint, String digitsOnly) {
        if (TextUtils.isEmpty(digitsOnly)) return true;
        // أرقام IMSI (مثل يمن موبايل CDMA/4G تبدأ بـ 421 وطولها 14-15 خانة)
        if (digitsOnly.length() >= 14) {
            if (digitsOnly.startsWith("421")) return true;
            if (!rawHint.trim().startsWith("+")) return true;
        }
        return digitsOnly.length() > 15;
    }

    private void applyValidatedPhoneHint(String rawHint) {
        if (TextUtils.isEmpty(rawHint)) return;
        String trimmed = rawHint.trim().replaceAll("[\s\-()]", "");
        String digitsOnly = trimmed.replaceAll("[^0-9]", "");
        String detectedIso = detectBestCountryIso();

        if (isLikelyImsiOrProgrammingId(trimmed, digitsOnly)) {
            Log.w("PhoneHint", "Ignored CDMA/IMSI programming number from SIM hint");
            binding.ccp.setCountryForNameCode(detectedIso);
            return;
        }

        try {
            PhoneNumberUtil phoneUtil = PhoneNumberUtil.createInstance(this);
            Phonenumber.PhoneNumber parsed;

            if (trimmed.startsWith("+") || trimmed.startsWith("00")) {
                String normalized = trimmed.startsWith("00") ? "+" + trimmed.substring(2) : trimmed;
                parsed = phoneUtil.parse(normalized, null);
                if (phoneUtil.isValidNumber(parsed)) {
                    String national = String.valueOf(parsed.getNationalNumber());
                    if (national.length() <= 12) {
                        binding.ccp.setCountryForPhoneCode(parsed.getCountryCode());
                        binding.etPhoneNumber.setText(national);
                        return;
                    }
                }
            } else {
                parsed = phoneUtil.parse(digitsOnly, detectedIso);
                if (phoneUtil.isValidNumberForRegion(parsed, detectedIso)) {
                    binding.ccp.setCountryForNameCode(detectedIso);
                    binding.etPhoneNumber.setText(String.valueOf(parsed.getNationalNumber()));
                    return;
                }
            }
        } catch (Exception e) {
            Log.w("PhoneHint", "Phone hint failed libphonenumber validation: " + e.getMessage());
        }

        // إذا لم يكن الرقم صالحاً، نبقي مفتاح الدولة الحقيقي دون تلويث الحقل
        binding.ccp.setCountryForNameCode(detectedIso);
    }

    private void handleActivationClick() {
        binding.btnActivate.setEnabled(false);
        String name = binding.etFullName.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            binding.tilFullName.setError(getString(R.string.error_name_required));
            binding.btnActivate.setEnabled(true);
            return;
        }
        if (name.split("\\s+").length < 2) {
            DialogHelper.show(this, getString(R.string.error_full_name_required), DialogHelper.FeedbackType.ERROR, null);
            binding.btnActivate.setEnabled(true);
            return;
        }
        if (!binding.ccp.isValidFullNumber()) {
            DialogHelper.show(this, getString(R.string.error_phone_invalid), DialogHelper.FeedbackType.ERROR, null);
            binding.etPhoneNumber.requestFocus();
            binding.btnActivate.setEnabled(true);
            return;
        }

        String fullNumberE164 = binding.ccp.getFullNumberWithPlus();
        String rawNumber = binding.etPhoneNumber.getText().toString().trim();
        String countryCode = binding.ccp.getSelectedCountryCodeWithPlus();

        String cleanNewNumber = rawNumber.replaceAll("[\\s+\\-\\(\\)]", "");
        String cleanSavedPhone = currentSavedPhone != null ? currentSavedPhone.replaceAll("[\\s+\\-\\(\\)]", "") : "";

        if (isEditeMode) {
            if (cleanNewNumber.equals(cleanSavedPhone)) {
                viewModel.saveActivationDataToFirestore();
            } else {
                showChangePhoneWarningDialog(fullNumberE164, rawNumber, countryCode, name);
            }
        } else {
            viewModel.startPhoneVerification(this, fullNumberE164, rawNumber, countryCode, name);
        }
    }

    private void observeViewModel() {
        viewModel.getNumberConflictEvent().observe(this, credential -> {
            if (credential != null) {
                if (codeDialog != null && codeDialog.isShowing()) {
                    codeDialog.dismiss();
                }
                showConflictDialog(credential);
            }
        });

        viewModel.getIsLoading().observe(this, loading -> {
            binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            binding.btnActivate.setEnabled(!loading);
        });

        viewModel.getCodeSent().observe(this, sent -> {
            if (sent) showVerificationDialog();
        });

        viewModel.getActivationSuccess().observe(this, success -> {
            if (success) {
                String savedPhone = binding.etPhoneNumber.getText().toString().trim();
                String phoneNumberWithoutSpaces = savedPhone.replaceAll("\\s+", "");

                if (codeDialog != null && codeDialog.isShowing()) codeDialog.dismiss();

                SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("isDebtFeatureEnabled", true);
                editor.putString("savedPhoneNumber", phoneNumberWithoutSpaces);
                String savedCountryCode = binding.ccp.getSelectedCountryCodeWithPlus();
                editor.putString("savedCountryCode", savedCountryCode);
                editor.apply();
                secureLicenseManager.setFullName(binding.etFullName.getText().toString().trim());
                secureLicenseManager.setPhoneNumber(binding.etPhoneNumber.getText().toString().trim().replaceAll("[\\s+\\-\\(\\)]", ""));
                secureLicenseManager.setDebtFeatureEnabled(true);
                secureLicenseManager.setLinkedAccount(true);
                secureLicenseManager.setCountryCode(savedCountryCode);
                secureLicenseManager.setStartApp(true);
                startActivity(new Intent(this, DebtSummaryActivity.class));
                finish();
            }
        });

        viewModel.getIsUnlinkedSuccess().observe(this, unlinked -> {
            if (unlinked != null && unlinked) {
                if (pendingPhoneChange) {
                    pendingPhoneChange = false;
                    viewModel.startPhoneVerification(this, pFullNumber, pRawNumber, pCountryCode, pName);
                } else {
                    SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                    prefs.edit().putBoolean("isDebtFeatureEnabled", false).apply();
                    secureLicenseManager.setDebtFeatureEnabled(false);
                    secureLicenseManager.setLinkedAccount(false);
                    secureLicenseManager.setStartApp(true);
                    Toast.makeText(this, getString(R.string.observeViewModel_01), Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        });

        viewModel.getErrorMsg().observe(this, error -> {
            if (isFinishing() || isDestroyed() || TextUtils.isEmpty(error)) {
                return;
            }
            DialogHelper.show(this, error, DialogHelper.FeedbackType.ERROR, null);
            binding.btnActivate.setEnabled(true);
        });

        viewModel.getShowWhatsAppDialogEvent().observe(this, show -> {
            if (show != null && show) {
                showWhatsAppFallbackDialog();
            }
        });
    }

    private void showUnlinkConfirmationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.showUnlinkConfirmationDialog_02))
                .setMessage(getString(R.string.showUnlinkConfirmationDialog_03))
                .setPositiveButton(getString(R.string.showUnlinkConfirmationDialog_04), (dialog, which) -> {
                    pendingPhoneChange = false;
                    binding.btnActivate.setEnabled(false);
                    binding.progressBar.setVisibility(View.VISIBLE);
                    viewModel.unlinkPhoneNumber();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void showChangePhoneWarningDialog(String fullNumberE164, String rawNumber, String countryCode, String name) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.showChangePhoneWarningDialog_05))
                .setMessage(getString(R.string.showChangePhoneWarningDialog_06))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.yes), (dialog, which) -> {
                    pendingPhoneChange = true;
                    pFullNumber = fullNumberE164;
                    pRawNumber = rawNumber;
                    pCountryCode = countryCode;
                    pName = name;

                    binding.btnActivate.setEnabled(false);
                    binding.progressBar.setVisibility(View.VISIBLE);
                    viewModel.unlinkPhoneNumber();
                })
                .setNegativeButton(getString(R.string.no), (dialog, which) -> {
                    binding.btnActivate.setEnabled(true);
                })
                .show();
    }

    private void showWhatsAppFallbackDialog() {
        String fullNumber = binding.ccp.getFullNumberWithPlus();

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.restriction_notice_title))
                .setMessage(getString(R.string.restriction_notice_message))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.send_form_whatsapp), (dialog, which) -> {
                    openWhatsAppManualVerification(fullNumber);
                    dialog.dismiss();
                    showVerificationDialog();
                })
                .setNegativeButton(getString(R.string.cancel), (dialog, which) -> {
                    dialog.dismiss();
                    binding.btnActivate.setEnabled(true);
                })
                .show();
    }

    private void openWhatsAppManualVerification(String fullPhoneNumber) {
        String message = getString(R.string.owner_verification_request, fullPhoneNumber);
        String developerNumber = "+967780405427";
        String cleanNumber = developerNumber.replaceAll("[\\s+\\-]", "");

        try {
            String url = "https://api.whatsapp.com/send?phone=" + cleanNumber + "&text=" + Uri.encode(message);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse(url));
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string._1086), Toast.LENGTH_SHORT).show();
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.whatsapp")));
            } catch (ActivityNotFoundException playStoreException) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.whatsapp")));
            }
        }
    }

    private void showConflictDialog(com.google.firebase.auth.PhoneAuthCredential credential) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        DialogHelper.show(
                this,
                getString(R.string.error_phone_conflict_msg2),
                DialogHelper.FeedbackType.WARNING,
                () -> {
                    viewModel.reclaimPhoneNumber(credential);
                },
                () -> {
                    binding.btnActivate.setEnabled(true);
                    binding.etPhoneNumber.requestFocus();
                }
        );
    }

    private void showVerificationDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_verification_code, null);
        TextInputEditText etCode = view.findViewById(R.id.etVerificationCode);

        codeDialog = new MaterialAlertDialogBuilder(this)
                .setView(view)
                .setCancelable(false)
                .setPositiveButton(getString(R.string.verified), (dialog, which) -> {})
                .setNegativeButton(getString(R.string.cancel), (dialog, which) -> {
                    dialog.dismiss();
                    binding.btnActivate.setEnabled(true);
                })
                .create();

        codeDialog.show();
        AppFontHelper.applyFontToDialog(this, codeDialog);

        codeDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String code = etCode.getText().toString().trim();
            if (code.length() < 6) {
                etCode.setError(getString(R.string.error_code_incomplete));
                return;
            }
            viewModel.verifyCode(code);
        });
    }
}
