package com.hpp.daftree.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import com.hpp.daftree.R;
import com.hpp.daftree.databinding.ActivityContactBinding;


public class ContactActivity extends BaseActivity {

    private ActivityContactBinding binding;
    private final String PHONE_NUMBER = "780405427";
    private final String EMAIL_ADDRESS = "haddad2inc@gmail.com";
    private final String WHATSAPP_NUMBER = "+967780405427"; // مع رمز الدولة وبدون أصفار

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityContactBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupFloatingToolbar(binding.layoutMain);

        setupListeners();
    }

    private void setupListeners() {
        binding.layoutCall.setOnClickListener(v -> openDialer());
        binding.layoutEmail.setOnClickListener(v -> openEmailClient(this));
        binding.layoutWhatsapp.setOnClickListener(v -> openWhatsApp(this));
    }

    private void openDialer() {
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:" + PHONE_NUMBER));
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "لا يوجد تطبيق اتصال مثبت", Toast.LENGTH_SHORT).show();
        }
    }

    public void openEmailClient(Context context) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:" + EMAIL_ADDRESS));
        intent.putExtra(Intent.EXTRA_SUBJECT, "استفسار بخصوص تطبيق Daftree");
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "لا يوجد تطبيق بريد إلكتروني مثبت", Toast.LENGTH_SHORT).show();
        }
    }

    public void openWhatsApp(Context context) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://api.whatsapp.com/send?phone=" + WHATSAPP_NUMBER));
            startActivity(intent);
        } catch (Exception e) {
           // Toast.makeText(this, "تطبيق واتساب غير مثبت", Toast.LENGTH_SHORT).show();
            Toast.makeText(this, getString(R.string._1086), Toast.LENGTH_SHORT).show();
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.whatsapp")));
            } catch (ActivityNotFoundException playStoreException) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.whatsapp")));
            }
        }
    }
}