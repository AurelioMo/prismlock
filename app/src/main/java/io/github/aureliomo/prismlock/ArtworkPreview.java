package io.github.aureliomo.prismlock;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.Base64;
import android.view.View;
import org.json.JSONObject;
import java.io.File;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

final class ArtworkPreview extends View {
    private static final ThreadPoolExecutor LOADER = new ThreadPoolExecutor(1, 1,
            30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), task -> {
                Thread thread = new Thread(task, "prismlock-preview");
                thread.setDaemon(true);
                return thread;
            });
    static { LOADER.allowCoreThreadTimeOut(true); }
    private Future<?> loading;
    private volatile long generation;
    private long started;
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF imageBounds = new RectF();

    private final MainActivity activity;
    private final String category;
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private volatile PreviewImages images;
    private volatile String requested = "";
    private volatile String status = "";

    ArtworkPreview(MainActivity activity, String category) {
        super(activity);
        this.activity = activity;
        this.category = category;
    }

    String importSlot() {
        if (!"icons".equals(category) || !activity.preferences().getBoolean("enabled", true)) return "";
        boolean light = !Appearance.isDark(activity);
        if (light && !activity.importKind("light").isEmpty()) return "light";
        return activity.importKind("dark").isEmpty() ? "" : "dark";
    }

    void loadCatalog() {
        if (!isAttachedToWindow()) return;
        backgroundPaint.setColor(activity.catalogScreen.previewSurface());
        textPaint.setColor(activity.catalogScreen.muted);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(activity.dp(13));
        textPaint.setTypeface(activity.catalogScreen.text("", 13, 400, activity.catalogScreen.muted).getTypeface());
        String tag = activity.preferences().getBoolean("enabled", true)
                ? activity.preferences().getString("icons".equals(category) ? "icon" : "animation", "") : "";
        String slot = importSlot();
        String file = tag.isEmpty() ? "" : category + "_" + tag + ".json";
        if (!tag.isEmpty() && "icons".equals(category)) {
            try {
                boolean lightTheme = !Appearance.isDark(activity);
                file = category + "_" + Catalog.iconTag(activity, tag, lightTheme ? "light" : "dark") + ".json";
            } catch (Exception exception) {
                file = category + "_" + tag + ".json";
            }
        }
        final String previewFile = file;
        String identity = slot.isEmpty() ? file : slot + ":" + activity.importKind(slot) + ":" + activity.preferences().getLong("ver", 0);
        if (identity.equals(requested)) { invalidate(); return; }
        requested = identity;
        final long request = ++generation;
        cancelLoading();
        images = null;
        status = identity.isEmpty() ? "" : "Loading preview...";
        if (identity.isEmpty()) { invalidate(); return; }
        loading = LOADER.submit(() -> {
            try {
                PreviewImages next;
                if (!slot.isEmpty()) {
                    if (!"png".equals(activity.importKind(slot))) {
                        if (request == generation && identity.equals(requested)) {
                            status = "Imported Lottie - preview unavailable";
                            postInvalidate();
                        }
                        return;
                    }
                    Bitmap bitmap = BitmapFactory.decodeFile(new File(activity.getFilesDir(), "custom_" + slot + ".png").getPath());
                    if (bitmap == null) throw new IllegalArgumentException("Invalid imported image");
                    next = new PreviewImages(new Bitmap[]{bitmap}, new double[]{-bitmap.getWidth() / 2.0,
                            -bitmap.getHeight() / 2.0, bitmap.getWidth() / 2.0, bitmap.getHeight() / 2.0}, 30);
                } else {
                    JSONObject document = new JSONObject(Catalog.read(activity, previewFile));
                    org.json.JSONArray assets = document.getJSONArray("assets");
                    org.json.JSONArray bounds = document.getJSONArray("catalogBounds");
                    Bitmap[] frames = new Bitmap[assets.length()];
                    for (int index = 0; index < frames.length; index++) {
                        if (Thread.currentThread().isInterrupted() || request != generation || !identity.equals(requested)) return;
                        String encoded = assets.getJSONObject(index).getString("p");
                        byte[] bytes = Base64.decode(encoded.substring(encoded.indexOf(',') + 1), Base64.DEFAULT);
                        frames[index] = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        if (frames[index] == null) throw new IllegalArgumentException("Invalid preview frame");
                    }
                    next = new PreviewImages(frames, new double[]{bounds.getDouble(0), bounds.getDouble(1),
                            bounds.getDouble(2), bounds.getDouble(3)}, document.getDouble("fr"));
                    for (int index = 0; index < frames.length; index++) {
                        next.opacity[index] = document.getJSONArray("layers").getJSONObject(index)
                                .getJSONObject("ks").getJSONObject("o").optInt("k", 100);
                    }
                }
                PreviewImages ready = next;
                post(() -> {
                    if (request != generation || !identity.equals(requested) || !isAttachedToWindow()) return;
                    started = SystemClock.uptimeMillis();
                    images = ready;
                    double scale = CatalogScale.percent(category, activity.fingerprintSize(),
                            ready.bounds[2] - ready.bounds[0], ready.bounds[3] - ready.bounds[1]) / 100;
                    double extent = 288;
                    for (Bitmap bitmap : ready.frames) {
                        extent = Math.max(extent, Math.max(bitmap.getWidth() + Math.abs(ready.bounds[0] + ready.bounds[2]),
                                bitmap.getHeight() + Math.abs(ready.bounds[1] + ready.bounds[3])) * scale);
                    }
                    android.view.ViewGroup.LayoutParams layout = getLayoutParams();
                    if (layout != null) {
                        layout.height = activity.dp((int) Math.ceil(132 * extent / 288));
                        setLayoutParams(layout);
                    }
                    status = "";
                    invalidate();
                });
            } catch (Exception exception) {
                if (request == generation && identity.equals(requested)) { status = "Preview unavailable"; postInvalidate(); }
            }
        });
    }

    private void cancelLoading() {
        if (loading != null) loading.cancel(true);
        LOADER.purge();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) postInvalidateOnAnimation();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        loadCatalog();
    }

    @Override protected void onDetachedFromWindow() {
        ++generation;
        cancelLoading();
        requested = "";
        images = null;
        status = "";
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        float width = getWidth(), height = getHeight();
        canvas.drawRoundRect(0, 0, width, height, activity.dp(16), activity.dp(16), backgroundPaint);
        PreviewImages current = images;
        float compositionScale = Math.min(width, activity.dp(132)) / 288f;
        if (current != null) {
            boolean animated = "animations".equals(category) && current.frames.length > 1;
            double frame = animated ? (SystemClock.uptimeMillis() - started) * current.fps / 1000
                    % current.frames.length : 0;
            int position = FrameTimeline.index(frame, current.frames.length);
            float blend = animated ? FrameTimeline.blend(frame, position, current.frames.length) : 0;
            double[] bounds = current.bounds;
            float scale = (float) CatalogScale.percent(category, activity.fingerprintSize(),
                    bounds[2] - bounds[0], bounds[3] - bounds[1]) / 100f * compositionScale;
            float centerX = width / 2 - (float) (bounds[0] + bounds[2]) / 2 * scale;
            float centerY = height / 2 - (float) (bounds[1] + bounds[3]) / 2 * scale;
            if (blend > 0) drawFrame(canvas, current, position + 1, blend, scale, centerX, centerY);
            drawFrame(canvas, current, position, 1 - blend, scale, centerX, centerY);
            if (animated && isShown() && getWindowVisibility() == VISIBLE) postInvalidateOnAnimation();
        } else if (!status.isEmpty()) {
            textPaint.setTextSize(Math.min(activity.dp(13), width / Math.max(1, status.length()) * 1.7f));
            canvas.drawText(status, width / 2, height / 2, textPaint);
        } else if ("icons".equals(category)) {
            android.graphics.drawable.Drawable stock = getContext().getDrawable(R.drawable.ic_catalog_fingerprint).mutate();
            stock.setTint(activity.catalogScreen.foreground);
            int diameter = Math.round(200 * compositionScale);
            stock.setBounds((int) (width - diameter) / 2, (int) (height - diameter) / 2,
                    (int) (width + diameter) / 2, (int) (height + diameter) / 2);
            stock.draw(canvas);
        } else {
            canvas.drawText("Samsung default effect", width / 2, height / 2, textPaint);
        }
    }

    private void drawFrame(Canvas canvas, PreviewImages current, int index, float weight,
            float scale, float centerX, float centerY) {
        Bitmap bitmap = current.frames[index];
        float halfWidth = bitmap.getWidth() * scale / 2, halfHeight = bitmap.getHeight() * scale / 2;
        imagePaint.setAlpha(Math.max(0, Math.min(255, Math.round(current.opacity[index] * 2.55f * weight))));
        imageBounds.set(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight);
        canvas.drawBitmap(bitmap, null, imageBounds, imagePaint);
    }

    private static final class PreviewImages {
        final Bitmap[] frames;
        final double[] bounds;
        final double fps;
        final int[] opacity;

        PreviewImages(Bitmap[] frames, double[] bounds, double fps) {
            this.frames = frames;
            this.bounds = bounds;
            this.fps = fps;
            this.opacity = new int[frames.length];
            java.util.Arrays.fill(this.opacity, 100);
        }
    }
}
