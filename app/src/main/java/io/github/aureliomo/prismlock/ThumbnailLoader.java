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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/** Coalesced image requests and a small shared cache; all view delivery runs on main. */
final class ThumbnailLoader {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(2 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getAllocationByteCount(); }
    };
    private static final ExecutorService WORKER = AppWorkers.serial("prismlock-thumbnails");
    // Accessed exclusively on the UI thread. One decode serves all waiting/recycled rows.
    private static final Map<String, List<WeakReference<ImageView>>> PENDING = new HashMap<>();
    private ThumbnailLoader() {}

    static void load(Context context, String filename, ImageView target, int pixels) {
        String key = filename + ":" + pixels;
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            target.setTag(key);
            target.setImageBitmap(cached);
            return;
        }
        if (key.equals(target.getTag()) && PENDING.containsKey(key)) return;
        target.setTag(key);
        target.setImageDrawable(null);
        List<WeakReference<ImageView>> listeners = PENDING.get(key);
        if (listeners != null) {
            listeners.add(new WeakReference<>(target));
            return;
        }
        listeners = new ArrayList<>();
        listeners.add(new WeakReference<>(target));
        PENDING.put(key, listeners);
        Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            Bitmap bitmap = null;
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                try (InputStream input = app.getAssets().open(filename)) { BitmapFactory.decodeStream(input, null, options); }
                options.inSampleSize = 1;
                while (options.outWidth / (options.inSampleSize * 2) >= pixels
                        && options.outHeight / (options.inSampleSize * 2) >= pixels) options.inSampleSize *= 2;
                options.inJustDecodeBounds = false;
                try (InputStream input = app.getAssets().open(filename)) { bitmap = BitmapFactory.decodeStream(input, null, options); }
                if (bitmap != null) CACHE.put(key, bitmap);
            } catch (Exception ignored) { }
            Bitmap result = bitmap;
            MAIN.post(() -> {
                List<WeakReference<ImageView>> waiting = PENDING.remove(key);
                if (waiting == null) return;
                for (WeakReference<ImageView> reference : waiting) {
                    ImageView view = reference.get();
                    if (view != null && key.equals(view.getTag())) view.setImageBitmap(result);
                }
            });
        });
    }
}
