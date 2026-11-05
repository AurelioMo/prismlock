package io.github.aureliomo.prismlock;

import android.view.View;
import android.view.animation.PathInterpolator;

/** Short, compositor-only settling motion; no bitmap captures or layout animation. */
final class UiMotion {
    private static final PathInterpolator EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);
    private UiMotion() {}

    static void settle(View view) {
        view.animate().cancel();
        view.setAlpha(0.75f);
        view.setScaleX(0.96f);
        view.setScaleY(0.96f);
        view.animate().alpha(1).scaleX(1).scaleY(1).setDuration(180).setInterpolator(EASE).start();
    }
}
