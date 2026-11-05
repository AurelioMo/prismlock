package io.github.aureliomo.prismlock;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Bounded decoding and atomic publication keep imports off the UI thread. */
final class ArtworkImports {
    private static final int MAX_BYTES = 900 * 1024;
    private static final int MAX_DIMENSION = 256;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "prismlock-import");
        thread.setDaemon(true);
        return thread;
    });

    interface Callback { void complete(String error); }
    private ArtworkImports() {}

    static void load(Context context, Uri uri, String slot, Callback callback) {
        Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            String error = null;
            File pending = null;
            try {
                byte[] bytes;
                try (InputStream input = app.getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IllegalArgumentException("This file could not be opened.");
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (output.size() + count > MAX_BYTES) {
                            throw new IllegalArgumentException("Choose a file smaller than 900 KB.");
                        }
                        output.write(buffer, 0, count);
                    }
                    bytes = output.toByteArray();
                }
                boolean png = bytes.length > 8 && (bytes[0] & 255) == 137 && bytes[1] == 'P'
                        && bytes[2] == 'N' && bytes[3] == 'G';
                String kind = png ? "png" : "json";
                if (png) bytes = preparePng(bytes);
                else if (!(new JSONObject(new String(bytes, StandardCharsets.UTF_8)).opt("layers")
                        instanceof org.json.JSONArray)) {
                    throw new IllegalArgumentException("Choose a valid Lottie JSON file.");
                }
                pending = File.createTempFile("import-", ".tmp", app.getFilesDir());
                try (FileOutputStream output = new FileOutputStream(pending)) {
                    output.write(bytes);
                    output.getFD().sync();
                }
                File destination = new File(app.getFilesDir(), "custom_" + slot + "." + kind);
                if (!pending.renameTo(destination)) throw new IllegalStateException("Could not save this icon.");
                android.content.SharedPreferences prefs = app.getSharedPreferences(MainActivity.PREFS, 0);
                if (!prefs.edit().putString(slot + "_kind", kind)
                        .putLong("ver", SettingsStore.nextVersion(prefs)).commit()) {
                    throw new IllegalStateException("Could not save icon settings.");
                }
            } catch (Exception exception) {
                error = "Could not import this icon: " + exception.getMessage();
            } finally {
                if (pending != null) pending.delete();
            }
            String result = error;
            MAIN.post(() -> callback.complete(result));
        });
    }

    private static byte[] preparePng(byte[] bytes) throws Exception {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw new IllegalArgumentException("This PNG could not be opened.");
        }
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > MAX_DIMENSION * 2) {
            options.inSampleSize *= 2;
        }
        options.inJustDecodeBounds = false;
        Bitmap decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (decoded == null) throw new IllegalArgumentException("This PNG could not be opened.");
        Bitmap scaled = decoded;
        try {
            int max = Math.max(decoded.getWidth(), decoded.getHeight());
            if (max > MAX_DIMENSION) {
                float factor = (float) MAX_DIMENSION / max;
                scaled = Bitmap.createScaledBitmap(decoded, Math.max(1, Math.round(decoded.getWidth() * factor)),
                        Math.max(1, Math.round(decoded.getHeight() * factor)), true);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!scaled.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IllegalArgumentException("This PNG could not be saved.");
            }
            return output.toByteArray();
        } finally {
            if (scaled != decoded) scaled.recycle();
            decoded.recycle();
        }
    }
}
