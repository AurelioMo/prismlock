package io.github.aureliomo.prismlock;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

final class SettingsScreen {
    private static final int MIN_SIZE = 30;
    private final MainActivity activity;

    SettingsScreen(MainActivity activity) {
        this.activity = activity;
    }

    void build() {
        CatalogScreen screen = activity.catalogScreen;
        LinearLayout settings = screen.column();
        LinearLayout appearance = screen.group(settings, activity.getString(R.string.settings_appearance));
        String theme = activity.preferences().getString("theme", "system");
        screen.settingValue(appearance, activity.getString(R.string.settings_theme), "light".equals(theme) ? activity.getString(R.string.settings_light) : "dark".equals(theme) ? activity.getString(R.string.settings_dark) : activity.getString(R.string.settings_system), () -> {
            String[] values = {"system", "light", "dark"};
            int selected = "light".equals(theme) ? 1 : "dark".equals(theme) ? 2 : 0;
            new AlertDialog.Builder(activity).setTitle(activity.getString(R.string.settings_theme))
                    .setSingleChoiceItems(new String[]{activity.getString(R.string.settings_system), activity.getString(R.string.settings_light), activity.getString(R.string.settings_dark)}, selected, (dialog, which) -> {
                        activity.preferences().edit().putString("theme", values[which]).apply();
                        dialog.dismiss();
                        activity.recreate();
                    }).setNegativeButton(activity.getString(R.string.action_cancel), null).show();
        });
        LinearLayout fingerprint = screen.group(settings, activity.getString(R.string.settings_fingerprint));
        screen.settingToggle(fingerprint, activity.getString(R.string.settings_customization), activity.getString(R.string.settings_customize_description),
                activity.preferences().getBoolean("enabled", true), (button, checked) ->
                        activity.preferences().edit().putBoolean("enabled", checked).putLong("ver", SettingsStore.nextVersion(activity.preferences())).apply());
        screen.settingValue(fingerprint, activity.getString(R.string.settings_fingerprint_size), (activity.fingerprintSize() * 2) + "%", () -> {
            LinearLayout controls = screen.column();
            controls.setPadding(activity.dp(24), activity.dp(16), activity.dp(24), activity.dp(8));
            TextView label = screen.text((activity.fingerprintSize() * 2) + "%", 18, 600, screen.foreground);
            controls.addView(label);
            SeekBar slider = new SeekBar(activity);
            slider.setMax(50);
            slider.setProgress(activity.fingerprintSize() - MIN_SIZE);
            slider.setProgressTintList(android.content.res.ColorStateList.valueOf(screen.accent));
            slider.setThumbTintList(android.content.res.ColorStateList.valueOf(screen.accent));
            controls.addView(slider, new LinearLayout.LayoutParams(-1, activity.dp(48)));
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                    label.setText(((MIN_SIZE + progress) * 2) + "%");
                }
                @Override public void onStartTrackingTouch(SeekBar bar) {}
                @Override public void onStopTrackingTouch(SeekBar bar) {}
            });
            new AlertDialog.Builder(activity).setTitle(activity.getString(R.string.settings_fingerprint_size)).setView(controls)
                    .setNegativeButton(activity.getString(R.string.action_cancel), null).setPositiveButton(activity.getString(R.string.action_done), (dialog, which) -> {
                        int newSize = MIN_SIZE + slider.getProgress();
                        activity.preferences().edit().putInt("size", newSize).putLong("ver", SettingsStore.nextVersion(activity.preferences())).apply();
                        activity.recreate();
                    }).show();
        });
        LinearLayout data = screen.group(settings, activity.getString(R.string.settings_collection));
        screen.settingAction(data, activity.getString(R.string.settings_custom_icons), activity.getString(R.string.settings_import_description), () ->
                new AlertDialog.Builder(activity).setTitle(activity.getString(R.string.settings_custom_icons))
                        .setItems(new String[]{activity.getString(R.string.settings_import_dark), activity.getString(R.string.settings_import_light), activity.getString(R.string.settings_remove_icons)}, (dialog, which) -> {
                            if (which == 0) activity.pick(MainActivity.REQ_DARK);
                            else if (which == 1) activity.pick(MainActivity.REQ_LIGHT);
                            else { activity.clearSlot("dark"); activity.clearSlot("light"); }
                        }).setNegativeButton(activity.getString(R.string.action_close), null).show());
        screen.settingAction(data, activity.getString(R.string.settings_reset), null, () ->
                new AlertDialog.Builder(activity).setTitle(activity.getString(R.string.settings_reset_title))
                        .setMessage(activity.getString(R.string.settings_reset_description))
                        .setNegativeButton(activity.getString(R.string.action_cancel), null).setPositiveButton(activity.getString(R.string.action_reset), (dialog, which) -> {
                            activity.clearSlot("dark"); activity.clearSlot("light");
                            activity.preferences().edit().putInt("size", Icons.DEF_SIZE).putString("icon", "")
                                    .putString("animation", "").putLong("ver", SettingsStore.nextVersion(activity.preferences())).apply();
                            activity.recreate();
                        }).show());
        LinearLayout other = screen.group(settings, activity.getString(R.string.settings_app));
        screen.settingAction(other, activity.getString(R.string.settings_preview), activity.getString(R.string.settings_preview_description), activity::showPreview);
        activity.updates.bindStatus(screen.settingValue(other, activity.getString(R.string.settings_updates), activity.getString(R.string.update_checking),
                () -> activity.updates.check(true)));
        screen.settingValue(other, activity.getString(R.string.settings_about), BuildConfig.VERSION_NAME, this::showAbout);
        screen.setSettings(settings);
    }

    private void showAbout() {
        LinearLayout body = activity.catalogScreen.column();
        body.setPadding(activity.dp(24), activity.dp(12), activity.dp(24), activity.dp(16));
        body.addView(activity.catalogScreen.text("PrismLock", 22, 700, activity.catalogScreen.foreground));
        TextView version = activity.catalogScreen.text(BuildConfig.VERSION_NAME, 14, 400, activity.catalogScreen.muted);
        version.setPadding(0, activity.dp(8), 0, activity.dp(16));
        body.addView(version);
        activity.catalogScreen.settingValue(body, activity.getString(R.string.settings_developer), "Aur\u00e9lio", () ->
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AurelioMo"))));
        activity.catalogScreen.settingValue(body, activity.getString(R.string.settings_repository), "AurelioMo/prismlock", () ->
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AurelioMo/prismlock"))));
        TextView credit = activity.catalogScreen.text(activity.getString(R.string.settings_about_credit), 13, 400, activity.catalogScreen.muted);
        credit.setPadding(0, activity.dp(16), 0, 0);
        body.addView(credit);
        new AlertDialog.Builder(activity).setView(body).setPositiveButton(activity.getString(R.string.action_close), null).show();
    }

}
