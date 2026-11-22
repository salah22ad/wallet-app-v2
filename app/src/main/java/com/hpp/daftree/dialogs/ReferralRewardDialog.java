package com.hpp.daftree.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.hpp.daftree.R;
import com.hpp.daftree.utils.ReferralManager;

public class ReferralRewardDialog extends Dialog {

    private Context context;
    private Button btnRegister, btnContinueGuest;
    private TextView tvTitle, tvMessage, tvReward,  tvExpiry;
    private String referrerUid;
    private OnReferralActionListener listener;
    private ReferralManager referralManager;

    public interface OnReferralActionListener {
        void onRegisterClicked(String referrerUid);
        void onCancel(String referrerUid);
        void onDismiss();
    }

    public ReferralRewardDialog(@NonNull Context context, String referrerUid, OnReferralActionListener listener) {
        super(context);
        this.context = context;
        this.referrerUid = referrerUid;
        this.listener = listener;
        this.referralManager = new ReferralManager(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_referral_reward);
        setCancelable(false);
        
        initViews();
//        setupTexts();
        setupClickListeners();
    }

    private void initViews() {
        tvTitle = findViewById(R.id.tv_title);
        tvMessage = findViewById(R.id.tv_message);
        tvReward = findViewById(R.id.tv_reward);
        btnRegister = findViewById(R.id.btn_register);
        tvExpiry= findViewById(R.id.tv_expiry);
        btnContinueGuest = findViewById(R.id.btn_continue_guest);
        tvExpiry.setVisibility(View.GONE);
    }

    private void setupClickListeners() {
        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("ReferralRewardDialog", "تسجيل المستخدم مع الإحالة: " + referrerUid);
                
                // حفظ referrerUid للمتابعة
                referralManager.saveReferrerUid(referrerUid);
                
                if (listener != null) {
                    listener.onRegisterClicked(referrerUid);
                }
                
                dismiss();
            }
        });

        btnContinueGuest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("ReferralRewardDialog", "متابعة كضيف مع الإحالة: " + referrerUid);
                // حفظ referrerUid للمتابعة كضيف
//                referralManager.saveReferrerUid(referrerUid);
                
                if (listener != null) {
                    listener.onCancel(referrerUid);
                }
                
                dismiss();
            }
        });
    }

    @Override
    public void onBackPressed() {
        // منع إغلاق الحوار بالزر الخلفي
        super.onBackPressed();
    }

    @Override
    public void cancel() {
        // منع إلغاء الحوار
        super.cancel();
    }

    @Override
    public void dismiss() {
        if (listener != null) {
            listener.onDismiss();
        }
        super.dismiss();
    }
}