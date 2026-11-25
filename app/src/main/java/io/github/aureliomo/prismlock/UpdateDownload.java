package io.github.aureliomo.prismlock;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.function.IntConsumer;

final class UpdateDownload {
    private static final long MAX_APK_BYTES = 200L * 1024L * 1024L;

    private UpdateDownload() {}

    static File download(Context context, UpdateChecker.Release release, IntConsumer progress)
            throws Exception {
        File directory = new File(context.getCacheDir(), "updates");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create the update directory.");
        }
        long expected = release.size;
        if (expected > MAX_APK_BYTES) throw new IOException("The update file is too large.");
        if (expected > 0 && directory.getUsableSpace() < expected * 2) {
            throw new IOException("Not enough free storage for this update.");
        }

        File target = new File(directory, "prismlock-" + safe(release.version) + ".apk");
        HttpURLConnection connection = (HttpURLConnection) new URL(release.apkUrl).openConnection();
        return transfer(connection, release, progress, target);
    }

    static File transfer(HttpURLConnection connection, UpdateChecker.Release release, IntConsumer progress,
                         File target) throws Exception {
        File temporary = new File(target.getParentFile(), target.getName() + ".part");
        try {
            return save(connection, release, progress, target, temporary);
        } finally {
            connection.disconnect();
            temporary.delete();
        }
    }

    private static File save(HttpURLConnection connection, UpdateChecker.Release release, IntConsumer progress,
                             File target, File temporary) throws Exception {
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/octet-stream");
        connection.setRequestProperty("User-Agent", "PrismLock/" + BuildConfig.VERSION_NAME);
        long total = 0L;
        long completed = 0L;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        int status = connection.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) {
            throw new IOException("GitHub returned HTTP " + status + " for the update.");
        }
        total = connection.getContentLengthLong();
        if (total <= 0) total = release.size;
        if (total > MAX_APK_BYTES) throw new IOException("The update file is too large.");
        try (InputStream input = new BufferedInputStream(connection.getInputStream());
                FileOutputStream output = new FileOutputStream(temporary)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            int lastPercent = -1;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
                digest.update(buffer, 0, count);
                completed += count;
                if (completed > MAX_APK_BYTES) {
                    throw new IOException("The update file is too large.");
                }
                if (total > 0) {
                    int percent = (int) Math.min(100L, completed * 100L / total);
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        progress.accept(percent);
                    }
                }
            }
            output.getFD().sync();
        }
        if (total > 0 && completed != total) {
            throw new IOException("The update download was incomplete.");
        }
        String sha256 = hex(digest.digest());
        if (release.sha256 != null && !release.sha256.isEmpty()
                && !release.sha256.equalsIgnoreCase(sha256)) {
            throw new IOException("The update failed SHA-256 verification.");
        }
        if (!temporary.renameTo(target)) {
            throw new IOException("Could not finish writing the update file.");
        }
        return target;
    }

    static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value));
        return result.toString();
    }

    private static String safe(String version) {
        return version == null ? "update" : version.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

}
