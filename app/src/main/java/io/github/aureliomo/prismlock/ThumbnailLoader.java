package io.github.aureliomo.prismlock;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small shared pixel cache. Catalog rows never decode PNGs on the main thread. */
final class ThumbnailLoader {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(2 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getAllocationByteCount(); }
    };
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "prismlock-thumbnails");
        thread.setDaemon(true);
        return thread;
    });
    private ThumbnailLoader() {}

    static void load(Context context, String filename, ImageView target, int pixels) {
        String key = filename + ":" + pixels;
        target.setTag(key);
        Bitmap cached = CACHE.get(key);
        target.setImageBitmap(cached);
        if (cached != null) return;
        Context app = context.getApplicationContext();
        WeakReference<ImageView> reference = new WeakReference<>(target);
        WORKER.execute(() -> {
            ImageView before = reference.get();
            if (before == null || !key.equals(before.getTag())) return;
            Bitmap bitmap = CACHE.get(key);
            if (bitmap == null) {
                try {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    try (InputStream input = app.getAssets().open(filename)) {
                        BitmapFactory.decodeStream(input, null, options);
                    }
                    options.inSampleSize = 1;
                    while (options.outWidth / (options.inSampleSize * 2) >= pixels
                            && options.outHeight / (options.inSampleSize * 2) >= pixels) options.inSampleSize *= 2;
                    options.inJustDecodeBounds = false;
                    try (InputStream input = app.getAssets().open(filename)) {
                        bitmap = BitmapFactory.decodeStream(input, null, options);
                    }
                    if (bitmap != null) CACHE.put(key, bitmap);
                } catch (Exception ignored) { }
            }
            Bitmap result = bitmap;
            MAIN.post(() -> {
                ImageView view = reference.get();
                if (view != null && key.equals(view.getTag())) view.setImageBitmap(result);
            });
        });
    }
}
