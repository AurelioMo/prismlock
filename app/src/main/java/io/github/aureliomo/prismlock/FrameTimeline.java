package io.github.aureliomo.prismlock;

/** Shared half-frame transitions for previews and Samsung's Lottie playback. */
final class FrameTimeline {
    private FrameTimeline() {}

    static int index(double frame, int count) {
        return Math.min(count - 1, Math.max(0, (int) Math.floor(frame - 0.5)));
    }

    static float blend(double frame, int index, int count) {
        if (index >= count - 1) return 0;
        return (float) Math.max(0, Math.min(1, frame - index - 0.5));
    }
}
