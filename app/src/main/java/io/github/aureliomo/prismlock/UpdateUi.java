package io.github.aureliomo.prismlock;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.lang.ref.WeakReference;

final class UpdateUi {
    static final int REQ_INSTALL_PERMISSION = 3;
    private static final int REQ_NOTIFICATIONS = 4;
    private static final String UPDATE_CHANNEL = "updates";
    private static WeakReference<MainActivity> foreground = new WeakReference<>(null);
    private static Intent pendingConfirmation;

    private final MainActivity activity;
    private TextView updateDetail;
    private AlertDialog progressDialog;
    private ProgressBar progressBar;
    private TextView progressStatus;
    private UpdateChecker.Release pendingRelease;
    private UpdateChecker.Release notificationRelease;
    private String offeredVersion = "";

    UpdateUi(MainActivity activity) {
        this.activity = activity;
    }

    void bindStatus(TextView view) {
        updateDetail = view;
    }

    void onResume() {
        foreground = new WeakReference<>(activity);
        if (pendingRelease != null) {
            UpdateChecker.Release release = pendingRelease;
            pendingRelease = null;
            offerUpdate(release);
        }
        if (pendingConfirmation != null) {
            Intent confirmation = pendingConfirmation;
            pendingConfirmation = null;
            showInstallConfirmation(confirmation);
        } else if (UpdateInstaller.takePermissionResume(activity)) {
            startUpdate(null);
        }
    }

    void onPause() {
        if (foreground.get() == activity) foreground.clear();
    }

    static boolean deliverInstallConfirmation(Intent confirmation) {
        MainActivity activity = foreground.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            pendingConfirmation = confirmation;
            return false;
        }
        activity.updates.showInstallConfirmation(confirmation);
        return true;
    }

    private void showInstallConfirmation(Intent confirmation) {
        try {
            activity.startActivity(confirmation);
        } catch (Exception exception) {
            new AlertDialog.Builder(activity).setTitle(activity.getString(R.string.update_installer_open_error))
                    .setMessage("Return to Updates and try again. " + exception.getMessage())
                    .setPositiveButton(activity.getString(R.string.action_close), null).show();
        }
    }

    void check(boolean manual) {
        if (manual) setUpdateStatus(activity.getString(R.string.update_checking));
        WeakReference<UpdateUi> reference = new WeakReference<>(this);
        UpdateChecker.check(activity, (release, error) -> {
            UpdateUi ui = reference.get();
            if (ui != null) ui.onCheckFinished(manual, release, error);
        });
    }

    private void onCheckFinished(boolean manual, UpdateChecker.Release release, String error) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (error != null) {
            setUpdateStatus(manual ? activity.getString(R.string.update_check_failed) : activity.getString(R.string.update_check_action));
            if (manual) showUpdateError(error);
            return;
        }
        if (release == null) {
            setUpdateStatus(activity.getString(R.string.update_up_to_date));
            if (manual) activity.toast(activity.getString(R.string.update_up_to_date_toast));
            return;
        }
        setUpdateStatus("v" + release.version);
        if (manual) showUpdateDialog(release);
        else if (foreground.get() == activity) offerUpdate(release);
        else pendingRelease = release;
    }

    private void offerUpdate(UpdateChecker.Release release) {
        if (release.version.equals(offeredVersion)) return;
        offeredVersion = release.version;
        notifyUpdate(release);
        showUpdateDialog(release);
    }

    private void notifyUpdate(UpdateChecker.Release release) {
        SharedPreferences preferences = activity.preferences();
        if (release.version.equals(preferences.getString("notified_update_version", ""))) return;
        if (android.os.Build.VERSION.SDK_INT >= 33
                && activity.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            notificationRelease = release;
            activity.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIFICATIONS);
            return;
        }
        NotificationManager manager = activity.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(UPDATE_CHANNEL,
                activity.getString(R.string.settings_updates), NotificationManager.IMPORTANCE_DEFAULT));
        Intent intent = new Intent(activity, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(activity, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(activity, UPDATE_CHANNEL);
        manager.notify(1001, builder.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("PrismLock update available")
                .setContentText("Version " + release.version + " is ready to install.")
                .setStyle(new Notification.BigTextStyle().bigText("Version " + release.version + " is ready to install."))
                .setContentIntent(pending).setAutoCancel(true).build());
        preferences.edit().putString("notified_update_version", release.version).apply();
    }

    void onRequestPermissionsResult(int requestCode, int[] grants) {
        if (requestCode == REQ_NOTIFICATIONS && grants.length > 0
                && grants[0] == PackageManager.PERMISSION_GRANTED && notificationRelease != null) {
            UpdateChecker.Release release = notificationRelease;
            notificationRelease = null;
            notifyUpdate(release);
        }
    }

    private void setUpdateStatus(String value) {
        if (updateDetail != null) updateDetail.setText(value);
    }

    private void showUpdateDialog(UpdateChecker.Release release) {
        String notes = release.notes == null ? "" : release.notes.trim();
        if (notes.length() > 900) notes = notes.substring(0, 900).trim() + "\u2026";
        String message = "Version " + release.version + " is available."
                + (notes.isEmpty() ? "" : "\n\n" + notes);
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.update_available))
                .setMessage(message)
                .setNegativeButton(activity.getString(R.string.update_release_page), (dialog, which) -> openReleases())
                .setNeutralButton(activity.getString(R.string.update_later), null)
                .setPositiveButton(activity.getString(R.string.update_install), (dialog, which) -> startUpdate(release))
                .show();
    }

    private void showUpdateError(String detail) {
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.update_check_error))
                .setMessage(detail + "\n\nYou can download the latest release from GitHub.")
                .setNegativeButton(activity.getString(R.string.action_close), null)
                .setPositiveButton(activity.getString(R.string.update_open_github), (dialog, which) -> openReleases())
                .show();
    }

    private void startUpdate(UpdateChecker.Release release) {
        if (progressDialog != null) return;
        LinearLayout body = activity.catalogScreen.column();
        body.setPadding(activity.dp(24), activity.dp(16), activity.dp(24), activity.dp(8));
        TextView status = activity.catalogScreen.text(release == null ? activity.getString(R.string.update_opening_installer)
                : "Downloading " + release.version + "\u2026",
                15, 400, activity.catalogScreen.foreground);
        ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(0);
        body.addView(status);
        LinearLayout.LayoutParams barLayout = new LinearLayout.LayoutParams(-1, activity.dp(24));
        barLayout.topMargin = activity.dp(12);
        body.addView(bar, barLayout);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.update_in_progress))
                .setView(body)
                .setCancelable(false)
                .create();
        progressDialog = dialog;
        progressBar = bar;
        progressStatus = status;
        dialog.show();
        UpdateInstaller.Callback callback = new ProgressCallback(this);
        if (release == null) UpdateInstaller.resumeInstall(activity, callback);
        else UpdateInstaller.downloadAndInstall(activity, release, callback);
    }

    void onDestroy() {
        dismissProgress();
    }

    private void dismissProgress() {
        if (progressDialog != null) progressDialog.dismiss();
        progressDialog = null;
        progressBar = null;
        progressStatus = null;
    }

    private void finishUpdate(UpdateInstaller.Result result) {
        dismissProgress();
        if (result.needsPermission) {
            UpdateInstaller.waitingForPermission(activity, true);
            try {
                activity.startActivityForResult(UpdateInstaller.unknownSourcesIntent(activity), REQ_INSTALL_PERMISSION);
            } catch (Exception exception) {
                UpdateInstaller.waitingForPermission(activity, false);
                activity.toast(activity.getString(R.string.update_permission_help));
            }
        } else if (result.success) {
            activity.toast(result.message);
        } else {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.update_install_error)
                    .setMessage(result.message)
                    .setNegativeButton(R.string.action_close, null)
                    .setPositiveButton(R.string.update_open_github, (dialog, which) -> openReleases())
                    .show();
        }
    }

    private static final class ProgressCallback implements UpdateInstaller.Callback {
        private final WeakReference<UpdateUi> reference;

        ProgressCallback(UpdateUi ui) {
            reference = new WeakReference<>(ui);
        }

        private UpdateUi current() {
            UpdateUi ui = reference.get();
            if (ui == null || ui.progressDialog == null || ui.activity.isFinishing() || ui.activity.isDestroyed()) return null;
            return ui;
        }

        @Override public void onProgress(int percent) {
            UpdateUi ui = current();
            if (ui == null) return;
            ui.progressBar.setProgress(percent);
            ui.progressStatus.setText(ui.activity.getString(R.string.update_download_progress, percent));
        }

        @Override public void onStatus(String message) {
            UpdateUi ui = current();
            if (ui == null) return;
            ui.progressBar.setIndeterminate(true);
            ui.progressStatus.setText(message);
        }

        @Override public void onFinished(UpdateInstaller.Result result) {
            UpdateUi ui = current();
            if (ui != null) ui.finishUpdate(result);
        }
    }

    private void openReleases() {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.RELEASES_URL)));
        } catch (Exception exception) {
            activity.toast(activity.getString(R.string.update_browser_error));
        }
    }

    void onInstallPermissionResult() {
        // Settings may return RESULT_CANCELED even when the permission was granted.
        // onResume checks the permission itself and consumes the pending update once.
        if (!activity.getPackageManager().canRequestPackageInstalls()) {
            UpdateInstaller.waitingForPermission(activity, false);
            activity.toast(activity.getString(R.string.update_permission_denied));
        }
    }
}
