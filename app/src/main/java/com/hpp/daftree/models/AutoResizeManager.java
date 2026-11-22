package com.hpp.daftree.models;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class AutoResizeManager {

    private static AutoResizeTextHelper sTextHelper;

    public static void setup(Activity activity) {
        setup(activity, activity.getWindow().getDecorView());
    }

    public static void setup(Activity activity, View rootView) {
        if (sTextHelper != null) {
            sTextHelper.destroy();
        }

        sTextHelper = new AutoResizeTextHelper(rootView);
        findAllTextViewsAndAdd(rootView);
    }

    private static void findAllTextViewsAndAdd(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                findAllTextViewsAndAdd(child);
            }
        } else if (view instanceof TextView) {
            sTextHelper.addView((TextView) view);
        }
    }

    public static void addView(TextView textView) {
        if (sTextHelper != null) {
            sTextHelper.addView(textView);
        }
    }

    public static void setEnabled(boolean enabled) {
        if (sTextHelper != null) {
            sTextHelper.setEnabled(enabled);
        }
    }

    public static void destroy() {
        if (sTextHelper != null) {
            sTextHelper.destroy();
            sTextHelper = null;
        }
    }
}
