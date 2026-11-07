package io.github.aureliomo.prismlock;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.lang.ref.WeakReference;

public class MainActivity extends Activity {
    static final String PREFS = "cfg";
    private static final int MIN_SIZE = 30;      // seekbar 0..50 -> 30..80
    private static final int REQ_DARK = 1, REQ_LIGHT = 2, REQ_INSTALL_PERMISSION = 3;
    private static final int REQ_NOTIFICATIONS = 4;
    private static final String UPDATE_CHANNEL = "updates";
    private static WeakReference<MainActivity> foreground = new WeakReference<>(null);
    private static Intent pendingConfirmation;

    private int size;
    private ArtworkPreview fingerprintPreview, effectPreview;
    CatalogScreen catalogScreen;
    private TextView updateDetail;
    private UpdateChecker.Release pendingRelease;
    private UpdateChecker.Release notificationRelease;
    private String offeredVersion = "";

    @Override protected void onResume() {
        super.onResume();
        foreground = new WeakReference<>(this);
        if (pendingRelease != null) {
            UpdateChecker.Release release = pendingRelease;
            pendingRelease = null;
            offerUpdate(release);
        }
        if (pendingConfirmation != null) {
            Intent confirmation = pendingConfirmation;
            pendingConfirmation = null;
            showInstallConfirmation(confirmation);
        } else if (UpdateInstaller.takePermissionResume(this)) {
            startUpdate(null);
        }
    }

    @Override protected void onPause() {
        if (foreground.get() == this) foreground.clear();
        super.onPause();
    }

    static boolean deliverInstallConfirmation(Intent confirmation) {
        MainActivity activity = foreground.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            pendingConfirmation = confirmation;
            return false;
        }
        activity.showInstallConfirmation(confirmation);
        return true;
    }

    private void showInstallConfirmation(Intent confirmation) {
        try {
            startActivity(confirmation);
        } catch (Exception exception) {
            new AlertDialog.Builder(this).setTitle("Could not open installer")
                    .setMessage("Return to Updates and try again. " + exception.getMessage())
                    .setPositiveButton("Close", null).show();
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        String preference = preferences().getString("theme", "system");
        if ("light".equals(preference)) setTheme(R.style.SamsungCatalogLightTheme);
        else if ("dark".equals(preference)) setTheme(R.style.SamsungCatalogDarkTheme);
        super.onCreate(state);
        size = preferences().getInt("size", Icons.DEF_SIZE);
        fingerprintPreview = new ArtworkPreview(this, "icons");
        effectPreview = new ArtworkPreview(this, "animations");
        catalogScreen = new CatalogScreen(this, () -> pick(REQ_DARK), this::refresh, state == null ? 0 : state.getInt("tab", 0));
        CatalogScreen screen = catalogScreen;
        LinearLayout settings = screen.column();
        LinearLayout appearance = screen.group(settings, "Appearance");
        String theme = preferences().getString("theme", "system");
        screen.settingValue(appearance, "Theme", "light".equals(theme) ? "Light" : "dark".equals(theme) ? "Dark" : "System", () -> {
            String[] values = {"system", "light", "dark"};
            int selected = "light".equals(theme) ? 1 : "dark".equals(theme) ? 2 : 0;
            new AlertDialog.Builder(this).setTitle("Theme")
                    .setSingleChoiceItems(new String[]{"System", "Light", "Dark"}, selected, (dialog, which) -> {
                        preferences().edit().putString("theme", values[which]).apply();
                        dialog.dismiss();
                        recreate();
                    }).setNegativeButton("Cancel", null).show();
        });
        LinearLayout fingerprint = screen.group(settings, "Fingerprint");
        screen.settingToggle(fingerprint, "Customization", "Icons and touch effects on your lock screen.",
                preferences().getBoolean("enabled", true), (button, checked) ->
                        preferences().edit().putBoolean("enabled", checked).putLong("ver", nextVersion()).apply());
        screen.settingValue(fingerprint, "Fingerprint size", (size * 2) + "%", () -> {
            LinearLayout controls = screen.column();
            controls.setPadding(dp(24), dp(16), dp(24), dp(8));
            TextView label = screen.text((size * 2) + "%", 18, 600, screen.foreground);
            controls.addView(label);
            SeekBar slider = new SeekBar(this);
            slider.setMax(50);
            slider.setProgress(size - MIN_SIZE);
            slider.setProgressTintList(android.content.res.ColorStateList.valueOf(screen.accent));
            slider.setThumbTintList(android.content.res.ColorStateList.valueOf(screen.accent));
            controls.addView(slider, new LinearLayout.LayoutParams(-1, dp(48)));
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                    label.setText(((MIN_SIZE + progress) * 2) + "%");
                }
                @Override public void onStartTrackingTouch(SeekBar bar) {}
                @Override public void onStopTrackingTouch(SeekBar bar) {}
            });
            new AlertDialog.Builder(this).setTitle("Fingerprint size").setView(controls)
                    .setNegativeButton("Cancel", null).setPositiveButton("Done", (dialog, which) -> {
                        size = MIN_SIZE + slider.getProgress();
                        preferences().edit().putInt("size", size).putLong("ver", nextVersion()).apply();
                        recreate();
                    }).show();
        });
        LinearLayout data = screen.group(settings, "Your collection");
        screen.settingAction(data, "Custom icons", "Import a PNG image or Lottie file.", () ->
                new AlertDialog.Builder(this).setTitle("Custom icons")
                        .setItems(new String[]{"Import for dark mode / AOD", "Import for light mode", "Remove imported icons"}, (dialog, which) -> {
                            if (which == 0) pick(REQ_DARK);
                            else if (which == 1) pick(REQ_LIGHT);
                            else { clearSlot("dark"); clearSlot("light"); }
                        }).setNegativeButton("Close", null).show());
        screen.settingAction(data, "Reset customization", null, () ->
                new AlertDialog.Builder(this).setTitle("Restore Samsung defaults?")
                        .setMessage("This clears your selected fingerprint, touch effect, and imported icons.")
                        .setNegativeButton("Cancel", null).setPositiveButton("Reset", (dialog, which) -> {
                            clearSlot("dark"); clearSlot("light");
                            preferences().edit().putInt("size", Icons.DEF_SIZE).putString("icon", "")
                                    .putString("animation", "").putLong("ver", nextVersion()).apply();
                            recreate();
                        }).show());
        LinearLayout other = screen.group(settings, "App");
        screen.settingAction(other, "Preview", "Your fingerprint and touch effect.", this::showPreview);
        updateDetail = screen.settingValue(other, "Updates", "Checking\u2026",
                () -> checkForUpdates(true));
        screen.settingValue(other, "About this app", BuildConfig.VERSION_NAME, this::showAbout);
        screen.setSettings(settings);
        refresh();
    }

    @Override protected void onStart() {
        super.onStart();
        checkForUpdates(false);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt("tab", catalogScreen.currentTab());
        super.onSaveInstanceState(state);
    }

    private void showAbout() {
        LinearLayout body = catalogScreen.column();
        body.setPadding(dp(24), dp(12), dp(24), dp(16));
        body.addView(catalogScreen.text("PrismLock", 22, 700, catalogScreen.foreground));
        TextView version = catalogScreen.text(BuildConfig.VERSION_NAME, 14, 400, catalogScreen.muted);
        version.setPadding(0, dp(8), 0, dp(16));
        body.addView(version);
        catalogScreen.settingValue(body, "Developer", "Aur\u00e9lio", () ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AurelioMo"))));
        catalogScreen.settingValue(body, "Repository", "AurelioMo/prismlock", () ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AurelioMo/prismlock"))));
        TextView credit = catalogScreen.text("Original catalog by custombeta. Samsung integration researched with RingPrint.\n\nPrismLock is an independent community project.", 13, 400, catalogScreen.muted);
        credit.setPadding(0, dp(16), 0, 0);
        body.addView(credit);
        new AlertDialog.Builder(this).setView(body).setPositiveButton("Close", null).show();
    }

    private void checkForUpdates(boolean manual) {
        if (manual) setUpdateStatus("Checking\u2026");
        UpdateChecker.check(this, (release, error) -> {
            if (isFinishing() || isDestroyed()) return;
            if (error != null) {
                setUpdateStatus(manual ? "Check failed" : "Tap to check");
                if (manual) showUpdateError(error);
                return;
            }
            if (release == null) {
                setUpdateStatus("Up to date");
                if (manual) toast("PrismLock is up to date.");
                return;
            }
            setUpdateStatus("v" + release.version);
            if (manual) showUpdateDialog(release);
            else if (foreground.get() == this) offerUpdate(release);
            else pendingRelease = release;
        });
    }

    private void offerUpdate(UpdateChecker.Release release) {
        if (release.version.equals(offeredVersion)) return;
        offeredVersion = release.version;
        notifyUpdate(release);
        showUpdateDialog(release);
    }

    private void notifyUpdate(UpdateChecker.Release release) {
        SharedPreferences preferences = preferences();
        if (release.version.equals(preferences.getString("notified_update_version", ""))) return;
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            notificationRelease = release;
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIFICATIONS);
            return;
        }
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(new NotificationChannel(UPDATE_CHANNEL, "Updates", NotificationManager.IMPORTANCE_DEFAULT));
        }
        Intent intent = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = android.os.Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, UPDATE_CHANNEL) : new Notification.Builder(this);
        manager.notify(1001, builder.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("PrismLock update available")
                .setContentText("Version " + release.version + " is ready to install.")
                .setStyle(new Notification.BigTextStyle().bigText("Version " + release.version + " is ready to install."))
                .setContentIntent(pending).setAutoCancel(true).build());
        preferences.edit().putString("notified_update_version", release.version).apply();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
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
        new AlertDialog.Builder(this)
                .setTitle("Update available")
                .setMessage(message)
                .setNegativeButton("Release page", (dialog, which) -> openReleases())
                .setNeutralButton("Later", null)
                .setPositiveButton("Update", (dialog, which) -> startUpdate(release))
                .show();
    }

    private void showUpdateError(String detail) {
        new AlertDialog.Builder(this)
                .setTitle("Could not check for updates")
                .setMessage(detail + "\n\nYou can download the latest release from GitHub.")
                .setNegativeButton("Close", null)
                .setPositiveButton("Open GitHub", (dialog, which) -> openReleases())
                .show();
    }

    private void startUpdate(UpdateChecker.Release release) {
        LinearLayout body = catalogScreen.column();
        body.setPadding(dp(24), dp(16), dp(24), dp(8));
        TextView status = catalogScreen.text(release == null ? "Opening the system installer\u2026"
                : "Downloading " + release.version + "\u2026",
                15, 400, catalogScreen.foreground);
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(0);
        body.addView(status);
        LinearLayout.LayoutParams barLayout = new LinearLayout.LayoutParams(-1, dp(24));
        barLayout.topMargin = dp(12);
        body.addView(bar, barLayout);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Updating PrismLock")
                .setView(body)
                .setCancelable(false)
                .create();
        dialog.show();
        UpdateInstaller.Callback callback = new UpdateInstaller.Callback() {
            @Override public void onProgress(int percent) {
                if (isFinishing() || isDestroyed()) return;
                bar.setProgress(percent);
                status.setText("Downloading update\u2026 " + percent + "%");
            }

            @Override public void onStatus(String message) {
                if (isFinishing() || isDestroyed()) return;
                bar.setIndeterminate(true);
                status.setText(message);
            }

            @Override public void onFinished(UpdateInstaller.Result result) {
                if (isFinishing() || isDestroyed()) return;
                dialog.dismiss();
                if (result.needsPermission) {
                    UpdateInstaller.waitingForPermission(MainActivity.this, true);
                    try {
                        startActivityForResult(UpdateInstaller.unknownSourcesIntent(MainActivity.this),
                                REQ_INSTALL_PERMISSION);
                    } catch (Exception exception) {
                        UpdateInstaller.waitingForPermission(MainActivity.this, false);
                        toast("Open Settings and allow PrismLock to install unknown apps, then retry Update.");
                    }
                } else if (result.success) {
                    toast(result.message);
                } else {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Update did not finish")
                            .setMessage(result.message)
                            .setNegativeButton("Close", null)
                            .setPositiveButton("Open GitHub", (dialog, which) -> openReleases())
                            .show();
                }
            }
        };
        if (release == null) UpdateInstaller.resumeInstall(this, callback);
        else UpdateInstaller.downloadAndInstall(this, release, callback);
    }

    private void openReleases() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.RELEASES_URL)));
        } catch (Exception exception) {
            toast("Could not open the browser.");
        }
    }

    private long nextVersion() {
        return SettingsStore.nextVersion(preferences());
    }

    SharedPreferences preferences() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    String importKind(String slot) {
        return preferences().getString(slot + "_kind", "");
    }

    int fingerprintSize() { return size; }

    private void refresh() {
        fingerprintPreview.loadCatalog();
        effectPreview.loadCatalog();
    }

    private void pick(int req) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "image/png", "application/json", "text/plain", "application/octet-stream"});
        startActivityForResult(i, req);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_INSTALL_PERMISSION) {
            // Settings may return RESULT_CANCELED even when the permission was granted.
            // onResume checks the permission itself and consumes the pending update once.
            if (!getPackageManager().canRequestPackageInstalls()) {
                UpdateInstaller.waitingForPermission(this, false);
                toast("Installation permission was not granted. Tap Update to try again.");
            }
            return;
        }
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        if (req == REQ_DARK) importUri(data.getData(), "dark");
        else if (req == REQ_LIGHT) importUri(data.getData(), "light");
    }

    private void importUri(Uri uri, String slot) {
        ArtworkImports.load(this, uri, slot, error -> {
            if (isFinishing() || isDestroyed()) return;
            toast(error == null ? "Icon imported." : error);
            refresh();
        });
    }

    private void clearFiles(String slot) {
        new File(getFilesDir(), "custom_" + slot + ".png").delete();
        new File(getFilesDir(), "custom_" + slot + ".json").delete();
    }

    private void clearSlot(String slot) {
        clearFiles(slot);
        preferences().edit().putString(slot + "_kind", "").putLong("ver", nextVersion()).apply();
        refresh();
    }

    private void toast(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void showPreview() {
        LinearLayout body = catalogScreen.column();
        body.setPadding(dp(20), dp(20), dp(20), dp(12));
        body.addView(catalogScreen.text("Preview", 24, 700, catalogScreen.foreground));
        addPreviewCard(body, "Fingerprint", "icon", fingerprintPreview);
        addPreviewCard(body, "Touch effect", "animation", effectPreview);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(body);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(scroll)
                .setPositiveButton("Close", null).create();
        dialog.show();
        dialog.getWindow().setBackgroundDrawable(catalogScreen.dialogBackground());
        refresh();
    }

    private void addPreviewCard(LinearLayout parent, String heading, String key, ArtworkPreview view) {
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        LinearLayout card = catalogScreen.group(parent, heading);
        String tag = preferences().getString(key, "");
        String name = preferences().getBoolean("enabled", true) && !tag.isEmpty() ? Catalog.title(tag) : "Samsung default";
        if ("icon".equals(key) && !view.importSlot().isEmpty()) name = "Imported icon";
        TextView label = catalogScreen.text(name, 13, 400, catalogScreen.muted);
        label.setPadding(0, dp(8), 0, dp(8));
        card.addView(label);
        card.addView(view, new LinearLayout.LayoutParams(-1, dp(132)));
    }

}
