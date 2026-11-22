package com.hpp.daftree.models;

import android.content.Context;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AutoResizeTextHelper implements ViewTreeObserver.OnGlobalLayoutListener {

    private static final float MIN_TEXT_SIZE_SP = 8f;
    private static final float MAX_TEXT_SIZE_SP = 20f;
    private static final long RESIZE_DELAY_MS = 100;

    private final WeakReference<View> mRootView;
    private final Map<TextView, TextInfo> mTextViews = new ConcurrentHashMap<>();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mResizeRunnable = this::performResize;

    private boolean mIsEnabled = true;
    private boolean mIsLayoutCompleted = false;

    public AutoResizeTextHelper(View rootView) {
        this.mRootView = new WeakReference<>(rootView);
        setupGlobalLayoutListener();
    }

    private void setupGlobalLayoutListener() {
        View rootView = mRootView.get();
        if (rootView != null) {
            rootView.getViewTreeObserver().addOnGlobalLayoutListener(this);
        }
    }

    @Override
    public void onGlobalLayout() {
        if (!mIsLayoutCompleted) {
            mIsLayoutCompleted = true;
            scheduleResize();
        }
    }

    public void addView(TextView textView) {
        if (textView != null) {
            mTextViews.put(textView, new TextInfo(textView));
            setupTextWatcher(textView);
        }
    }

    public void addViews(TextView... textViews) {
        for (TextView textView : textViews) {
            addView(textView);
        }
    }

    private void setupTextWatcher(TextView textView) {
        if (textView instanceof EditText) {
            textView.addTextChangedListener(new SimpleTextWatcher() {
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    scheduleResize();
                }
            });
        }
    }

    private void scheduleResize() {
        if (!mIsEnabled) return;

        mHandler.removeCallbacks(mResizeRunnable);
        mHandler.postDelayed(mResizeRunnable, RESIZE_DELAY_MS);
    }

    private void performResize() {
        View rootView = mRootView.get();
        if (rootView == null || !mIsEnabled) return;

        for (Map.Entry<TextView, TextInfo> entry : mTextViews.entrySet()) {
            TextView textView = entry.getKey();
            TextInfo textInfo = entry.getValue();

            if (textView != null && textView.isShown()) {
                resizeTextView(textView, textInfo);
            }
        }
    }

    private void resizeTextView(TextView textView, TextInfo textInfo) {
        if (textView.getWidth() <= 0 || textView.getHeight() <= 0) return;

        CharSequence text = textView.getText();
        if (TextUtils.isEmpty(text)) return;

        float availableWidth = textView.getWidth() - textView.getPaddingLeft() - textView.getPaddingRight();
        if (availableWidth <= 0) return;

        Paint paint = textView.getPaint();
        float targetSize = findOptimalTextSize(text.toString(), paint, availableWidth,
                textInfo.minSize, textInfo.maxSize);

        float currentSize = textView.getTextSize();
        if (Math.abs(currentSize - targetSize) > 0.5f) {
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, targetSize);
        }
    }

    private float findOptimalTextSize(String text, Paint paint, float availableWidth,
                                      float minSize, float maxSize) {
        float low = minSize;
        float high = maxSize;
        float precision = 0.5f;
        float optimalSize = high;

        while (high - low > precision) {
            float mid = (low + high) / 2;
            paint.setTextSize(mid);
            float textWidth = paint.measureText(text);

            if (textWidth <= availableWidth) {
                optimalSize = mid;
                low = mid;
            } else {
                high = mid;
            }
        }

        return Math.max(minSize, optimalSize);
    }

    public void setEnabled(boolean enabled) {
        mIsEnabled = enabled;
    }

    public void destroy() {
        mHandler.removeCallbacks(mResizeRunnable);
        mTextViews.clear();

        View rootView = mRootView.get();
        if (rootView != null) {
            rootView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        }
    }

    // TextWatcher مبسط
    private abstract static class SimpleTextWatcher implements android.text.TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void afterTextChanged(android.text.Editable s) {}
    }

    // كلاس لتخزين معلومات النص
    private static class TextInfo {
        float minSize;
        float maxSize;

        TextInfo(TextView textView) {
            Context context = textView.getContext();
            this.minSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                    MIN_TEXT_SIZE_SP, context.getResources().getDisplayMetrics());
            this.maxSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                    MAX_TEXT_SIZE_SP, context.getResources().getDisplayMetrics());

            // استخدام حجم النص الحالي كحد أقصى إذا كان أصغر
            float currentSize = textView.getTextSize();
            if (currentSize < this.maxSize) {
                this.maxSize = currentSize;
            }
        }
    }
}
