package io.github.aureliomo.prismlock;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.PathInterpolator;

/** Short, compositor-only settling motion; no bitmap captures or layout animation. */
final class UiMotion {
    private static final PathInterpolator EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);
    private UiMotion() {}

    static void configureRefresh(android.app.Activity activity) {
        android.view.Display display = activity.getWindowManager().getDefaultDisplay();
        if (display == null) return;
        android.view.Display.Mode current = display.getMode();
        float supported = current.getRefreshRate();
        for (android.view.Display.Mode mode : display.getSupportedModes()) {
            if (mode.getPhysicalWidth() == current.getPhysicalWidth()
                    && mode.getPhysicalHeight() == current.getPhysicalHeight()) {
                supported = Math.max(supported, mode.getRefreshRate());
            }
        }
        android.view.WindowManager.LayoutParams attributes = activity.getWindow().getAttributes();
        attributes.preferredRefreshRate = Math.min(120f, supported);
        activity.getWindow().setAttributes(attributes);
    }

    static void reset(View view) {
        view.animate().cancel();
        view.setAlpha(1);
        view.setScaleX(1);
        view.setScaleY(1);
        view.setTranslationX(0);
    }

    static void enterTab(View view, int direction, int offset) {
        reset(view);
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        int layoutDirection = view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? -1 : 1;
        view.setTranslationX(direction * offset * layoutDirection);
        view.setAlpha(0.72f);
        view.animate().translationX(0).alpha(1).setDuration(240).setInterpolator(EASE).start();
    }

    static void confirmSelection(View check) {
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        check.setAlpha(0);
        check.setScaleX(0.88f);
        check.setScaleY(0.88f);
        check.animate().alpha(1).scaleX(1).scaleY(1).setDuration(220).setInterpolator(EASE).start();
    }

    static void pressFeedback(View view) {
        view.setStateListAnimator(android.animation.AnimatorInflater.loadStateListAnimator(
                view.getContext(), R.animator.ui_press_scale));
    }
}
