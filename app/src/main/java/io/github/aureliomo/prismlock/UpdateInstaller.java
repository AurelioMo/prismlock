package io.github.aureliomo.prismlock;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Downloads, verifies and installs a release APK. */
final class UpdateInstaller {
    private static final long MAX_APK_BYTES = 200L * 1024L * 1024L;
    private static final String PENDING_APK = "pending_update_apk";
    private static final String WAITING_PERMISSION = "update_waiting_permission";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "prismlock-update");
        thread.setDaemon(true);
        return thread;
    });

    interface Callback {
        void onProgress(int percent);

        void onStatus(String message);

        void onFinished(Result result);
    }

    static final class Result {
        final boolean success;
        final String message;
        final boolean needsPermission;

        Result(boolean success, String message) {
            this(success, message, false);
        }

        Result(boolean success, String message, boolean needsPermission) {
            this.success = success;
            this.message = message;
            this.needsPermission = needsPermission;
        }
    }

    private enum RootResult { SUCCESS, UNAVAILABLE, FAILED }

    private UpdateInstaller() {
    }

    static void downloadAndInstall(Context context, UpdateChecker.Release release, Callback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                File apk = download(app, release, callback);
                postStatus(callback, "Verifying update\u2026");
                verify(app, apk);
                postStatus(callback, "Installing " + release.version + "\u2026");
                RootResult root = installWithRoot(app, apk);
                if (root == RootResult.SUCCESS) {
                    postFinished(callback, new Result(true,
                            "PrismLock " + release.version
                                    + " installed. Reopen the app or reboot to load the new module code."));
                    return;
                }
                if (root == RootResult.UNAVAILABLE || root == RootResult.FAILED) {
                    postStatus(callback, "Opening the system installer\u2026");
                    installWithSession(app, apk);
                    postFinished(callback, new Result(true,
                            "Follow the system installer to finish updating PrismLock."));
                }
            } catch (UnknownSourcesException exception) {
                postFinished(callback, new Result(false, exception.getMessage(), true));
            } catch (Throwable throwable) {
                postFinished(callback, new Result(false, message(throwable)));
            }
        });
    }

    private static File download(Context context, UpdateChecker.Release release, Callback callback)
            throws Exception {
        File directory = new File(context.getCacheDir(), "updates");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create the update directory.");
        }
        long expected = release.size;
        if (expected > 0 && directory.getUsableSpace() < expected * 2) {
            throw new IOException("Not enough free storage for this update.");
        }

        File target = new File(directory, "prismlock-" + safe(release.version) + ".apk");
        File temporary = new File(directory, target.getName() + ".part");
        delete(temporary);
        HttpURLConnection connection = (HttpURLConnection)
                new URL(release.apkUrl).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/octet-stream");
        connection.setRequestProperty("User-Agent", "PrismLock/" + BuildConfig.VERSION_NAME);
        long total = 0L;
        long completed = 0L;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try {
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
                            postProgress(callback, percent);
                        }
                    }
                }
                output.getFD().sync();
            }
        } finally {
            connection.disconnect();
        }
        if (total > 0 && completed != total) {
            delete(temporary);
            throw new IOException("The update download was incomplete.");
        }
        String sha256 = hex(digest.digest());
        if (release.sha256 != null && !release.sha256.isEmpty()
                && !release.sha256.equalsIgnoreCase(sha256)) {
            delete(temporary);
            throw new IOException("The update failed SHA-256 verification.");
        }
        delete(target);
        if (!temporary.renameTo(target)) {
            delete(temporary);
            throw new IOException("Could not finish writing the update file.");
        }
        return target;
    }

    private static void verify(Context context, File apk) throws Exception {
        PackageManager manager = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28
                ? Api28.SIGNING_FLAG
                : PackageManager.GET_SIGNATURES;
        PackageInfo archive = manager.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (archive == null) throw new IOException("The downloaded file is not a valid APK.");
        if (!context.getPackageName().equals(archive.packageName)) {
            throw new IOException("The downloaded APK belongs to another package.");
        }
        PackageInfo installed = manager.getPackageInfo(context.getPackageName(), flags);
        String expectedSigner = signer(installed);
        String actualSigner = signer(archive);
        if (expectedSigner == null || !expectedSigner.equals(actualSigner)) {
            throw new IOException("The downloaded APK is not signed by the PrismLock release key.");
        }
        if (versionCode(archive) <= versionCode(installed)) {
            throw new IOException("The downloaded release is not newer than the installed version.");
        }
    }

    private static RootResult installWithRoot(Context context, File apk) {
        Process process = null;
        try {
            String command = "cp " + quote(apk.getAbsolutePath())
                    + " /data/local/tmp/prismlock-update.apk"
                    + " && chmod 644 /data/local/tmp/prismlock-update.apk"
                    + " && pm install -r /data/local/tmp/prismlock-update.apk"
                    + "; result=$?; rm -f /data/local/tmp/prismlock-update.apk; exit $result";
            process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            final Process running = process;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Thread reader = new Thread(() -> copyQuietly(running.getInputStream(), output),
                    "prismlock-pm-output");
            reader.setDaemon(true);
            reader.start();
            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                process.destroy();
                return RootResult.FAILED;
            }
            reader.join(1_000L);
            String result = output.toString("UTF-8");
            return process.exitValue() == 0 && result.contains("Success")
                    ? RootResult.SUCCESS : RootResult.FAILED;
        } catch (IOException exception) {
            return RootResult.UNAVAILABLE;
        } catch (Throwable throwable) {
            return RootResult.FAILED;
        } finally {
            if (process != null) process.destroy();
        }
    }

    private static void installWithSession(Context context, File apk) throws Exception {
        context.getSharedPreferences(MainActivity.PREFS, 0).edit()
                .putString(PENDING_APK, apk.getName()).commit();
        PackageManager manager = context.getPackageManager();
        if (!manager.canRequestPackageInstalls()) {
            throw new UnknownSourcesException(
                    "Allow PrismLock to install unknown apps, then return to continue the update.");
        }
        PackageInstaller installer = manager.getPackageInstaller();
        PackageInstaller.SessionParams parameters = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        parameters.setSize(apk.length());
        int sessionId = installer.createSession(parameters);
        boolean committed = false;
        try (PackageInstaller.Session session = installer.openSession(sessionId)) {
            try (OutputStream output = session.openWrite("base.apk", 0, apk.length());
                    InputStream input = new FileInputStream(apk)) {
                copy(input, output);
                session.fsync(output);
            }
            Intent status = new Intent(context, UpdateReceiver.class)
                    .setAction(UpdateReceiver.ACTION_STATUS);
            PendingIntent pending = PendingIntent.getBroadcast(context, sessionId, status,
                    statusIntentFlags());
            session.commit(pending.getIntentSender());
            committed = true;
        } finally {
            if (!committed) installer.abandonSession(sessionId);
        }
    }

    static int statusIntentFlags() {
        // PackageInstaller fills EXTRA_STATUS and EXTRA_INTENT into this explicit intent.
        return PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
    }

    static Intent unknownSourcesIntent(Context context) {
        return new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + context.getPackageName()));
    }

    static void waitingForPermission(Context context, boolean waiting) {
        context.getSharedPreferences(MainActivity.PREFS, 0).edit()
                .putBoolean(WAITING_PERMISSION, waiting).commit();
    }

    static boolean takePermissionResume(Context context) {
        android.content.SharedPreferences preferences = context.getSharedPreferences(MainActivity.PREFS, 0);
        if (!preferences.getBoolean(WAITING_PERMISSION, false)
                || !context.getPackageManager().canRequestPackageInstalls()) return false;
        waitingForPermission(context, false);
        return true;
    }

    static void resumeInstall(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                String filename = app.getSharedPreferences(MainActivity.PREFS, 0).getString(PENDING_APK, "");
                if (!filename.matches("prismlock-[a-zA-Z0-9._-]+\\.apk")) {
                    throw new IOException("No update is ready. Tap Update to download it again.");
                }
                File apk = new File(new File(app.getCacheDir(), "updates"), filename);
                // Recheck package, version and signer after returning from Settings.
                verify(app, apk);
                installWithSession(app, apk);
                postFinished(callback, new Result(true, "Follow the system installer to finish updating PrismLock."));
            } catch (UnknownSourcesException exception) {
                postFinished(callback, new Result(false, exception.getMessage(), true));
            } catch (Throwable throwable) {
                postFinished(callback, new Result(false, message(throwable)));
            }
        });
    }

    private static String signer(PackageInfo info) throws Exception {
        Signature[] signatures = Build.VERSION.SDK_INT >= 28
                ? Api28.signatures(info)
                : info.signatures;
        if (signatures == null || signatures.length == 0) return null;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return hex(digest.digest(signatures[0].toByteArray()));
    }

    private static long versionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 ? Api28.versionCode(info) : info.versionCode;
    }

    /** Isolates API 28+ types so older devices never load them. */
    @android.annotation.TargetApi(28)
    private static final class Api28 {
        static final int SIGNING_FLAG = PackageManager.GET_SIGNING_CERTIFICATES;

        static Signature[] signatures(PackageInfo info) {
            SigningInfo signing = info.signingInfo;
            if (signing == null) return null;
            return signing.hasMultipleSigners()
                    ? signing.getApkContentsSigners()
                    : signing.getSigningCertificateHistory();
        }

        static long versionCode(PackageInfo info) {
            return info.getLongVersionCode();
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value));
        return result.toString();
    }

    private static String safe(String version) {
        return version == null ? "update" : version.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
    }

    private static void copyQuietly(InputStream input, ByteArrayOutputStream output) {
        try {
            copy(input, output);
        } catch (IOException ignored) {
            // The install result is read from the exit code.
        }
    }

    private static void delete(File file) {
        if (file.exists() && !file.delete()) file.deleteOnExit();
    }

    private static void postProgress(Callback callback, int percent) {
        MAIN.post(() -> callback.onProgress(percent));
    }

    private static void postStatus(Callback callback, String message) {
        MAIN.post(() -> callback.onStatus(message));
    }

    private static void postFinished(Callback callback, Result result) {
        MAIN.post(() -> callback.onFinished(result));
    }

    private static String message(Throwable throwable) {
        String detail = throwable.getMessage();
        if (detail == null || detail.trim().isEmpty()) detail = throwable.getClass().getSimpleName();
        return detail;
    }

    private static final class UnknownSourcesException extends Exception {
        UnknownSourcesException(String message) {
            super(message);
        }
    }
}
