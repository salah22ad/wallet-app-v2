package com.hpp.daftree.utils; // تأكد من اسم الباكيج

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.hpp.daftree.R;

public class DialogHelper {

    // تعريف أنواع الرسائل
    public enum FeedbackType {
        SUCCESS,
        ERROR,
        WARNING,
        WARNING_2,
        INFO
    }

    // واجهة بسيطة لتنفيذ أكواد عند الضغط على الزر (اختياري)
    public interface OnDialogActionListener {
        void onAction();
//        void onCancel();
    }

    // --- الدالة القديمة (نبقيها كما هي لكي لا يتعطل الكود القديم) ---
    public static void show(Context context, String message, FeedbackType type, OnDialogActionListener action) {
        // نستدعي الدالة الجديدة ونمرر null للإجراء الثاني
        show(context, message, type, action, null);
    }

    // --- الدالة الجديدة (مع منطق زر الإلغاء) ---
    public static void show(Context context, String message, FeedbackType type,
                            OnDialogActionListener positiveAction,
                            OnDialogActionListener negativeAction) {
        // حماية من الكراش: التأكد من أن الأكتيفيتي لا تزال تعمل
        if (context instanceof Activity && ((Activity) context).isFinishing()) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.dialog_custom_feedback, null);
        builder.setView(view);

        AlertDialog dialog = builder.create();

        // جعل الخلفية شفافة لدعم الزوايا الدائرية
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // ربط العناصر
        LinearLayout background = view.findViewById(R.id.llDialogBackground);
        ImageView icon = view.findViewById(R.id.ivIcon);
        TextView title = view.findViewById(R.id.tvTitle);
        TextView msg = view.findViewById(R.id.tvMessage);
        MaterialButton btn = view.findViewById(R.id.btnAction);
        View lView= view.findViewById(R.id.lView);
        MaterialButton btnCancel = view.findViewById(R.id.btnCancel);
        msg.setText(message);

        // تخصيص الألوان والأيقونات
        switch (type) {
            case ERROR:
                background.setBackgroundColor(Color.parseColor("#D32F2F")); // أحمر
                icon.setImageResource(android.R.drawable.ic_delete);
                title.setText(R.string.error_title);
                break;

            case SUCCESS:
                background.setBackgroundColor(Color.parseColor("#388E3C")); // أخضر
                icon.setImageResource(android.R.drawable.checkbox_on_background);
                title.setText(R.string.success_title);
                break;
            case WARNING_2:
                background.setBackgroundColor(Color.parseColor("#F57C00")); // برتقالي
                icon.setImageResource(android.R.drawable.ic_dialog_alert);
                title.setText(R.string.warning_title);
                break;
            case WARNING:
                background.setBackgroundColor(Color.parseColor("#F57C00")); // برتقالي
                icon.setImageResource(android.R.drawable.ic_dialog_alert);
                title.setText(R.string.warning_title);
                break;
            case INFO:
                background.setBackgroundColor(ContextCompat.getColor(context, R.color.md_theme_primary));
                icon.setImageResource(R.drawable.ic_notifications);
                title.setText(R.string.warning_title);
                break;
        }

        // عند ضغط الزر
        if (type == FeedbackType.WARNING) {
            btnCancel.setVisibility(View.VISIBLE); // إظهار الزر فقط في التحذير

            btnCancel.setOnClickListener(v -> {
                dialog.dismiss();
                if (negativeAction != null) {
                    negativeAction.onAction(); // تنفيذ كود الإلغاء
                }
            });
        } else {
            btnCancel.setVisibility(View.GONE); // إخفاء الزر في الحالات الأخرى
            lView.setVisibility(View.GONE);
        }
        btn.setOnClickListener(v -> {
            dialog.dismiss();
            if (positiveAction != null) {
                positiveAction.onAction();
            }
        });

        dialog.show();
    }
}