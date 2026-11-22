package com.hpp.daftree.dialogs;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.hpp.daftree.R;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.database.User;
import com.hpp.daftree.ui.SubscriptionActivity;
import com.hpp.daftree.utils.SecureLicenseManager;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PurchaseBottomSheet extends BottomSheetDialogFragment {

    private String generatedCode;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_purchase_options, container, false);

        // 1. توليد كود الشراء (لخيار الواتساب)
        generatePurchaseCode();

        // 2. تعريف الأزرار
        CardView cardGooglePlay = view.findViewById(R.id.cardGooglePlay);
        CardView cardWhatsApp = view.findViewById(R.id.cardWhatsApp);

        // 3. أكشن زر جوجل بلاي -> يفتح PurchaseActivity
        cardGooglePlay.setOnClickListener(v -> {
		dismiss();
    Intent intent = new Intent(requireContext(), SubscriptionActivity.class);
    intent.putExtra("purchase", true);
    startActivity(intent);
        });

        // 4. أكشن زر الواتساب -> ينسخ الكود ويفتح الواتساب
        cardWhatsApp.setOnClickListener(v -> {
            copyCodeToClipboard();
            sendToWhatsApp();
            dismiss();
        });

        return view;
    }

    // لجعل الخلفية شفافة حتى تظهر الزوايا الدائرية
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(BottomSheetDialogFragment.STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme);
    }

    // --- منطق الواتساب القديم (تم نقله كما هو) ---

    private void generatePurchaseCode() {
        String androidId = Settings.Secure.getString(requireContext().getContentResolver(), Settings.Secure.ANDROID_ID);
        generatedCode = "HPP-" + getShortHash(androidId).toUpperCase();
    }

    private String getShortHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < 4; i++) { // Take first 4 bytes for short code
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return "ERROR";
        }
    }

    private void copyCodeToClipboard() {
        if (generatedCode == null) return;
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(getString(R.string.purchase_code_label), generatedCode);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(getContext(), R.string.code_copied_success, Toast.LENGTH_SHORT).show();
    }

    private void sendToWhatsApp() {
        if (generatedCode == null) return;

        // التحقق من أن الـ Fragment لا يزال متصلاً
        if (!isAdded() || getContext() == null) {
            return;
        }

        // حفظ السياق محلياً للاستخدام داخل الـ Executor
        final Context context = getContext();

        executor.execute(() -> {
            // تأخير إغلاق الشيت حتى اكتمال العملية
            // أو يمكن إظهار loading indicator بدلاً من ذلك

            User userData = AppDatabase.getDatabase(context).userDao().getUserProfileBlocking();
            String email = SecureLicenseManager.getInstance(getContext()).getEmail() != null ? SecureLicenseManager.getInstance(getContext()).getEmail() : getString(R.string.not_specified);

            // بناء الرسالة
            String message = getString(R.string.whatsapp_greeting) + "\n\n" +
                    getString(R.string.whatsapp_purchase_request) + "\n\n" +
                    getString(R.string.account_email_label, email) + "\n" +
                    getString(R.string.purchase_code_label_whatsapp, generatedCode) + "\n\n" +
                    getString(R.string.whatsapp_payment_details_request) + "\n\n" +
                    getString(R.string.whatsapp_thank_you);

            // الرجوع إلى الخيط الرئيسي لفتح الواتساب
            if (isAdded() && getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse("https://wa.me/967780405427?text=" + Uri.encode(message)));
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(context, R.string.whatsapp_not_found, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
}