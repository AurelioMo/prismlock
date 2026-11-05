package io.github.aureliomo.prismlock;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Decode wallpaper outside the first-frame path and retain only one display-sized image. */
final class WallpaperLoader {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "prismlock-wallpaper");
        thread.setDaemon(true);
        return thread;
    });
    private static Bitmap cached;
    private static String cachedKey;
    private WallpaperLoader() {}

    static void load(Context context, ImageView target, boolean dark) {
        int resource = dark ? R.drawable.catalog_background_dark : R.drawable.catalog_background_light;
        int width = context.getResources().getDisplayMetrics().widthPixels;
        int height = context.getResources().getDisplayMetrics().heightPixels;
        String key = resource + ":" + width + ":" + height;
        target.setTag(key);
        target.setBackgroundColor(dark ? 0xFF101920 : 0xFFF3F8FC);
        Context app = context.getApplicationContext();
        WeakReference<ImageView> reference = new WeakReference<>(target);
        WORKER.execute(() -> {
            if (!key.equals(cachedKey) || cached == null) {
                try {
                    Bitmap image = decode(app.getResources(), resource, width, height);
                    if (image == null) return;
                    cached = image;
                    cachedKey = key;
                } catch (Exception ignored) { return; }
            }
            Bitmap result = cached;
            MAIN.post(() -> {
                ImageView view = reference.get();
                if (view != null && key.equals(view.getTag())) view.setImageBitmap(result);
            });
        });
    }

    private static Bitmap decode(Resources resources, int resource, int width, int height) throws Exception {
        if (Build.VERSION.SDK_INT >= 28) return Api28.decode(resources, resource, width, height);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(resources, resource, options);
        options.inSampleSize = 1;
        while (options.outWidth / (options.inSampleSize * 2) >= width
                && options.outHeight / (options.inSampleSize * 2) >= height) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeResource(resources, resource, options);
        if (bitmap == null) return null;
        float ratio = ratio(bitmap.getWidth(), bitmap.getHeight(), width, height);
        if (ratio >= 1) return bitmap;
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(bitmap.getWidth() * ratio)),
                Math.max(1, Math.round(bitmap.getHeight() * ratio)), true);
        if (scaled != bitmap) bitmap.recycle();
        return scaled;
    }

    private static float ratio(int sourceWidth, int sourceHeight, int width, int height) {
        return Math.min(1f, Math.max((float) width / sourceWidth, (float) height / sourceHeight));
    }

    @android.annotation.TargetApi(28)
    private static final class Api28 {
        static Bitmap decode(Resources resources, int resource, int width, int height) throws Exception {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resources, resource), (decoder, info, source) -> {
                int sourceWidth = info.getSize().getWidth(), sourceHeight = info.getSize().getHeight();
                float ratio = ratio(sourceWidth, sourceHeight, width, height);
                decoder.setTargetSize(Math.max(1, Math.round(sourceWidth * ratio)), Math.max(1, Math.round(sourceHeight * ratio)));
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
        }
    }
}
