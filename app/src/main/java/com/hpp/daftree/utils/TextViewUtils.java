package com.hpp.daftree.utils;

import android.graphics.Paint;
import android.util.TypedValue;
import android.widget.TextView;

public class TextViewUtils {

    public static void adjustTextSizeToFit(TextView textView, String text, float defaultSp, float minSp) {
        textView.setText(text);
        textView.post(() -> {
            int availableWidth = textView.getWidth() - textView.getPaddingLeft() - textView.getPaddingRight();
            if (availableWidth <= 0) return;

            Paint paint = new Paint();
            float currentSizeSp = defaultSp;
            paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, currentSizeSp, textView.getResources().getDisplayMetrics()));
            float textWidth = paint.measureText(text);

            // تنقيص أدق (0.2f بدلاً من 0.5f) للحصول على حجم مناسب بدقة أعلى
            while (textWidth > availableWidth && currentSizeSp > minSp) {
                currentSizeSp -= 0.2f;
                paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, currentSizeSp, textView.getResources().getDisplayMetrics()));
                textWidth = paint.measureText(text);
            }

            if (currentSizeSp < defaultSp) {
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentSizeSp);
                textView.invalidate(); // إعادة رسم TextView
            }
        });
    }

    public static void adjustTextSizeToFit(TextView textView, String text) {
        adjustTextSizeToFit(textView, text, 13f, 8f);
    }
}