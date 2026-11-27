package com.hpp.daftree.helpers;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

public final class FeatureBadgeManager {

    private static final String PREFS_NAME = "feature_badges_prefs";

    // مفاتيح الميزات الجديدة (أضف أي ميزة مستقبلية هنا)
    public static final String FEATURE_BUDGET_V2 = "badge_budget_v2";

    private FeatureBadgeManager() {}

    public static boolean shouldShowBadge(Context context, String featureKey) {
        if (context == null || featureKey == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return !prefs.getBoolean(featureKey, false);
    }

    public static void bindBadge(View badgeView, String featureKey) {
        if (badgeView == null) return;
        Context ctx = badgeView.getContext();
        if (shouldShowBadge(ctx, featureKey)) {
            badgeView.setVisibility(View.VISIBLE);
            badgeView.setAlpha(1f);
            startPulseAnimation(badgeView);
        } else {
            stopPulseAnimation(badgeView);
            badgeView.setVisibility(View.GONE);
        }
    }

    public static void markFeatureSeen(Context context, View badgeView, String featureKey) {
        if (context != null && featureKey != null) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(featureKey, true)
                    .apply();
        }
        if (badgeView != null && badgeView.getVisibility() == View.VISIBLE) {
            stopPulseAnimation(badgeView);
            badgeView.animate()
                    .alpha(0f)
                    .scaleX(0.6f)
                    .scaleY(0.6f)
                    .setDuration(220)
                    .withEndAction(() -> {
                        badgeView.setVisibility(View.GONE);
                        badgeView.setAlpha(1f);
                        badgeView.setScaleX(1f);
                        badgeView.setScaleY(1f);
                    })
                    .start();
        }
    }

    private static void startPulseAnimation(View view) {
        stopPulseAnimation(view);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.08f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.08f, 1f);
        scaleX.setRepeatCount(ValueAnimator.INFINITE);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);
        scaleX.setRepeatMode(ValueAnimator.RESTART);
        scaleY.setRepeatMode(ValueAnimator.RESTART);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.setDuration(1400);
        set.start();
        view.setTag(set);
    }

    private static void stopPulseAnimation(View view) {
        Object tag = view.getTag();
        if (tag instanceof AnimatorSet) {
            ((AnimatorSet) tag).cancel();
            view.setTag(null);
        }
        view.setScaleX(1f);
        view.setScaleY(1f);
    }
}
