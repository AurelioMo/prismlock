package io.github.aureliomo.prismlock;

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

    static void settle(View view) {
        view.animate().cancel();
        view.setAlpha(0.75f);
        view.setScaleX(0.96f);
        view.setScaleY(0.96f);
        view.animate().alpha(1).scaleX(1).scaleY(1).setDuration(180).setInterpolator(EASE).start();
    }
}
