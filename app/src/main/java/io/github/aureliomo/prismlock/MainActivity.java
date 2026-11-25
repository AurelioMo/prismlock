package io.github.aureliomo.prismlock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    static final String PREFS = "cfg";
    static final int REQ_DARK = 1, REQ_LIGHT = 2;
    private int size;
    private ArtworkPreview fingerprintPreview, effectPreview;
    CatalogScreen catalogScreen;
    final UpdateUi updates = new UpdateUi(this);

    @Override protected void onResume() {
        super.onResume();
        updates.onResume();
    }

    @Override protected void onPause() {
        updates.onPause();
        super.onPause();
    }

    static boolean deliverInstallConfirmation(Intent confirmation) {
        return UpdateUi.deliverInstallConfirmation(confirmation);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        updates.onRequestPermissionsResult(requestCode, grants);
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
        new SettingsScreen(this).build();
        refresh();
    }

    @Override protected void onStart() {
        super.onStart();
        updates.check(false);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt("tab", catalogScreen.currentTab());
        super.onSaveInstanceState(state);
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

    void pick(int req) {
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
        if (req == UpdateUi.REQ_INSTALL_PERMISSION) {
            updates.onInstallPermissionResult();
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

    void clearArtwork(boolean resetSelection) {
        ArtworkImports.clear(this, resetSelection, error -> {
            if (isFinishing() || isDestroyed()) return;
            if (error != null) toast(error);
            else if (resetSelection) recreate();
            else refresh();
        });
    }

    void toast(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    void showPreview() {
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
