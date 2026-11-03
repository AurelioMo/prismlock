package io.github.aureliomo.prismlock;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Reads stable releases from GitHub and compares them with the installed version. */
final class UpdateChecker {
    static final String RELEASES_URL = "https://github.com/AurelioMo/prismlock/releases/latest";
    private static final String LATEST_URL =
            "https://api.github.com/repos/AurelioMo/prismlock/releases/latest";
    private static final String USER_AGENT = "PrismLock/" + BuildConfig.VERSION_NAME;
    private static final long CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L;
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "prismlock-update-check");
        thread.setDaemon(true);
        return thread;
    });

    interface Callback {
        /** Called on the main thread. Exactly one of release/error is non-null. */
        void onResult(Release release, String error);
    }

    static final class Release {
        final String tag;
        final String version;
        final String name;
        final String notes;
        final String apkUrl;
        final String sha256;
        final long size;

        Release(String tag, String version, String name, String notes,
                String apkUrl, String sha256, long size) {
            this.tag = tag;
            this.version = version;
            this.name = name;
            this.notes = notes;
            this.apkUrl = apkUrl;
            this.sha256 = sha256;
            this.size = size;
        }
    }

    private UpdateChecker() {
    }

    static void check(Context context, boolean force, Callback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            Release release = null;
            String error = null;
            SharedPreferences preferences = app.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
            try {
                long lastAttempt = preferences.getLong("update_last_attempt", 0L);
                long now = System.currentTimeMillis();
                if (!force && now - lastAttempt < CHECK_INTERVAL_MS) {
                    release = cached(preferences);
                } else {
                    preferences.edit().putLong("update_last_attempt", now).apply();
                    release = fetch();
                    store(preferences, release);
                }
            } catch (Throwable throwable) {
                release = cached(preferences);
                if (release == null) {
                    error = message(throwable);
                }
            }
            final Release result = release;
            final String failure = error;
            MAIN.post(() -> callback.onResult(result, failure));
        });
    }

    private static Release cached(SharedPreferences preferences) {
        String version = preferences.getString("update_version", "");
        String apkUrl = preferences.getString("update_apk", "");
        if (version == null || version.isEmpty() || apkUrl == null || apkUrl.isEmpty()) return null;
        if (!Versions.isNewer(version, BuildConfig.VERSION_NAME)) return null;
        return new Release(
                preferences.getString("update_tag", "v" + version),
                version,
                preferences.getString("update_name", "PrismLock " + version),
                preferences.getString("update_notes", ""),
                apkUrl,
                preferences.getString("update_sha256", ""),
                preferences.getLong("update_size", 0L));
    }

    private static void store(SharedPreferences preferences, Release release) {
        SharedPreferences.Editor editor = preferences.edit();
        if (release == null) {
            editor.putString("update_tag", "")
                    .putString("update_version", "")
                    .putString("update_name", "")
                    .putString("update_notes", "")
                    .putString("update_apk", "")
                    .putString("update_sha256", "")
                    .putLong("update_size", 0L);
        } else {
            editor.putString("update_tag", release.tag)
                    .putString("update_version", release.version)
                    .putString("update_name", release.name)
                    .putString("update_notes", release.notes)
                    .putString("update_apk", release.apkUrl)
                    .putString("update_sha256", release.sha256)
                    .putLong("update_size", release.size);
        }
        editor.apply();
    }

    private static Release fetch() throws Exception {
        HttpURLConnection connection = open(LATEST_URL, "application/vnd.github+json");
        try {
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND) return null;
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("GitHub returned HTTP " + status + ".");
            }
            String body = read(connection.getInputStream());
            JSONObject document = new JSONObject(body);
            String tag = document.optString("tag_name", "").trim();
            String version = Versions.normalize(tag);
            if (version.isEmpty() || !Versions.isNewer(version, BuildConfig.VERSION_NAME)) return null;

            JSONArray assets = document.optJSONArray("assets");
            if (assets == null) return null;
            JSONObject apk = null;
            for (int index = 0; index < assets.length(); index++) {
                JSONObject asset = assets.optJSONObject(index);
                if (asset == null) continue;
                String name = asset.optString("name", "");
                if (!name.endsWith(".apk")) continue;
                if (apk == null || name.startsWith("PrismLock-")) apk = asset;
                if (name.startsWith("PrismLock-")) break;
            }
            if (apk == null) return null;

            String url = apk.optString("browser_download_url", "");
            if (!trusted(url)) return null;
            String digest = apk.optString("digest", "");
            if (digest.startsWith("sha256:")) digest = digest.substring("sha256:".length());
            return new Release(
                    tag,
                    version,
                    document.optString("name", "PrismLock " + version),
                    document.optString("body", ""),
                    url,
                    digest,
                    apk.optLong("size", 0L));
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(String address, String accept) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(20_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", accept);
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        return connection;
    }

    private static boolean trusted(String address) {
        try {
            URI uri = new URI(address);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "github.com".equalsIgnoreCase(uri.getHost())
                    && uri.getPath() != null
                    && uri.getPath().startsWith("/AurelioMo/prismlock/releases/download/");
        } catch (Exception exception) {
            return false;
        }
    }

    private static String read(InputStream input) throws IOException {
        try (InputStream stream = input) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                output.write(buffer, 0, count);
                if (output.size() > MAX_RESPONSE_BYTES) {
                    throw new IOException("GitHub response is too large.");
                }
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String message(Throwable throwable) {
        String detail = throwable.getMessage();
        if (detail == null || detail.trim().isEmpty()) detail = throwable.getClass().getSimpleName();
        return detail;
    }
}
