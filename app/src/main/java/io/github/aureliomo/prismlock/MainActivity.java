package io.github.aureliomo.prismlock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    static final String PREFS = "cfg";
    private static final int MIN_SIZE = 30;      // seekbar 0..50 -> 30..80
    private static final int MAX_IMPORT = 900 * 1024;
    private static final int REQ_DARK = 1, REQ_LIGHT = 2;

    private int size;
    private Preview fingerprintPreview, effectPreview;
    private CatalogScreen catalogScreen;

    @Override
    protected void onCreate(Bundle state) {
        String preference = sp().getString("theme", "system");
        if ("light".equals(preference)) setTheme(R.style.SamsungCatalogLightTheme);
        else if ("dark".equals(preference)) setTheme(R.style.SamsungCatalogDarkTheme);
        super.onCreate(state);
        size = sp().getInt("size", Icons.DEF_SIZE);
        fingerprintPreview = new Preview(this, "icons");
        effectPreview = new Preview(this, "animations");
        catalogScreen = new CatalogScreen(this, () -> pick(REQ_DARK), () -> {
            refresh();
        }, state == null ? 0 : state.getInt("tab", 0));
        CatalogScreen screen = catalogScreen;
        LinearLayout settings = screen.column();
        LinearLayout appearance = screen.group(settings, "Appearance");
        String theme = sp().getString("theme", "system");
        screen.settingValue(appearance, "Theme", "light".equals(theme) ? "Light" : "dark".equals(theme) ? "Dark" : "System", () -> {
            String[] values = {"system", "light", "dark"};
            int selected = "light".equals(theme) ? 1 : "dark".equals(theme) ? 2 : 0;
            new AlertDialog.Builder(this).setTitle("Theme")
                    .setSingleChoiceItems(new String[]{"System", "Light", "Dark"}, selected, (dialog, which) -> {
                        sp().edit().putString("theme", values[which]).commit();
                        dialog.dismiss();
                        recreate();
                    }).setNegativeButton("Cancel", null).show();
        });
        LinearLayout fingerprint = screen.group(settings, "Fingerprint");
        screen.settingToggle(fingerprint, "Customization", "Icons and touch effects on your lock screen.",
                sp().getBoolean("enabled", true), (button, checked) ->
                        sp().edit().putBoolean("enabled", checked).putLong("ver", nextVersion()).commit());
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
                        sp().edit().putInt("size", size).putLong("ver", nextVersion()).commit();
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
                            sp().edit().putInt("size", Icons.DEF_SIZE).putString("icon", "")
                                    .putString("animation", "").putLong("ver", nextVersion()).commit();
                            recreate();
                        }).show());
        LinearLayout other = screen.group(settings, "App");
        screen.settingAction(other, "Preview", "Your fingerprint and touch effect.", this::showPreview);
        screen.settingValue(other, "About this app", BuildConfig.VERSION_NAME, this::showAbout);
        screen.setSettings(settings);
        refresh();
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
        TextView credit = catalogScreen.text("Original catalog by custombeta. Samsung integration researched with RingPrint.\n\nThis is a development preview, not an official Samsung app.", 13, 400, catalogScreen.muted);
        credit.setPadding(0, dp(16), 0, 0);
        body.addView(credit);
        new AlertDialog.Builder(this).setView(body).setPositiveButton("Close", null).show();
    }

    private long nextVersion() {
        return Math.max(System.currentTimeMillis(), sp().getLong("ver", 0) + 1);
    }

    private SharedPreferences sp() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private String kind(String slot) {
        return sp().getString(slot + "_kind", "");
    }

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
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        if (req == REQ_DARK) importUri(data.getData(), "dark");
        else if (req == REQ_LIGHT) importUri(data.getData(), "light");
    }

    private void importUri(Uri uri, String slot) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while (in != null && (n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
                if (bo.size() > MAX_IMPORT) { toast("Choose a file smaller than 900 KB."); return; }
            }
            byte[] d = bo.toByteArray();
            String kind;
            byte[] out = d;
            if (d.length > 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') {
                kind = "png";
                Bitmap bm = BitmapFactory.decodeByteArray(d, 0, d.length);
                if (bm == null) { toast("This PNG could not be opened."); return; }
                int max = Math.max(bm.getWidth(), bm.getHeight());
                if (max > 256) {
                    float k = 256f / max;
                    bm = Bitmap.createScaledBitmap(bm, Math.max(1, Math.round(bm.getWidth() * k)),
                            Math.max(1, Math.round(bm.getHeight() * k)), true);
                }
                ByteArrayOutputStream po = new ByteArrayOutputStream();
                bm.compress(Bitmap.CompressFormat.PNG, 100, po);
                out = po.toByteArray();
            } else {
                JSONObject o = new JSONObject(new String(d, StandardCharsets.UTF_8));
                if (!o.has("layers")) { toast("Choose a valid Lottie JSON file."); return; }
                kind = "json";
            }
            clearFiles(slot);
            try (FileOutputStream fo = new FileOutputStream(new File(getFilesDir(), "custom_" + slot + "." + kind))) {
                fo.write(out);
            }
            sp().edit().putString(slot + "_kind", kind).putLong("ver", System.currentTimeMillis()).commit();
            toast("Icon imported.");
            refresh();
        } catch (Exception e) {
            toast("Could not import this icon: " + e.getMessage());
        }
    }

    private void clearFiles(String slot) {
        new File(getFilesDir(), "custom_" + slot + ".png").delete();
        new File(getFilesDir(), "custom_" + slot + ".json").delete();
    }

    private void clearSlot(String slot) {
        clearFiles(slot);
        sp().edit().putString(slot + "_kind", "").putLong("ver", System.currentTimeMillis()).commit();
        refresh();
    }

    private void toast(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    private int dp(int v) {
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

    private void addPreviewCard(LinearLayout parent, String heading, String key, Preview view) {
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        LinearLayout card = catalogScreen.group(parent, heading);
        String tag = sp().getString(key, "");
        String name = sp().getBoolean("enabled", true) && !tag.isEmpty() ? Catalog.title(tag) : "Samsung default";
        if ("icon".equals(key) && !view.importSlot().isEmpty()) name = "Imported icon";
        TextView label = catalogScreen.text(name, 13, 400, catalogScreen.muted);
        label.setPadding(0, dp(8), 0, dp(8));
        card.addView(label);
        card.addView(view, new LinearLayout.LayoutParams(-1, dp(132)));
    }

    private final class Preview extends View {
        private final String category;
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private volatile PreviewImages images;
        private volatile String requested = "";
        private volatile String status = "";

        Preview(Context context, String category) {
            super(context);
            this.category = category;
        }

        String importSlot() {
            if (!"icons".equals(category) || !sp().getBoolean("enabled", true)) return "";
            String theme = sp().getString("theme", "system");
            boolean light = "light".equals(theme) || ("system".equals(theme)
                    && (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    != android.content.res.Configuration.UI_MODE_NIGHT_YES);
            if (light && !kind("light").isEmpty()) return "light";
            return kind("dark").isEmpty() ? "" : "dark";
        }

        void loadCatalog() {
            if (!isAttachedToWindow()) return;
            backgroundPaint.setColor(catalogScreen.previewSurface());
            textPaint.setColor(catalogScreen.muted);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(dp(13));
            textPaint.setTypeface(catalogScreen.text("", 13, 400, catalogScreen.muted).getTypeface());
            String tag = sp().getBoolean("enabled", true)
                    ? sp().getString("icons".equals(category) ? "icon" : "animation", "") : "";
            String slot = importSlot();
            String file = tag.isEmpty() ? "" : category + "_" + tag + ".json";
            if (!tag.isEmpty() && "icons".equals(category)) {
                try {
                    boolean lightTheme = "light".equals(sp().getString("theme", "system"))
                            || ("system".equals(sp().getString("theme", "system"))
                            && (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                            != android.content.res.Configuration.UI_MODE_NIGHT_YES);
                    file = category + "_" + Catalog.iconTag(MainActivity.this, tag, lightTheme ? "light" : "dark") + ".json";
                } catch (Exception exception) {
                    file = category + "_" + tag + ".json";
                }
            }
            final String previewFile = file;
            String identity = slot.isEmpty() ? file : slot + ":" + kind(slot) + ":" + sp().getLong("ver", 0);
            if (identity.equals(requested)) { invalidate(); return; }
            requested = identity;
            images = null;
            status = identity.isEmpty() ? "" : "Loading preview...";
            if (identity.isEmpty()) { invalidate(); return; }
            new Thread(() -> {
                try {
                    PreviewImages next;
                    if (!slot.isEmpty()) {
                        if (!"png".equals(kind(slot))) {
                            if (identity.equals(requested)) {
                                status = "Imported Lottie - preview unavailable";
                                postInvalidate();
                            }
                            return;
                        }
                        Bitmap bitmap = BitmapFactory.decodeFile(new File(getFilesDir(), "custom_" + slot + ".png").getPath());
                        if (bitmap == null) throw new IllegalArgumentException("Invalid imported image");
                        next = new PreviewImages(new Bitmap[]{bitmap}, new double[]{-bitmap.getWidth() / 2.0,
                                -bitmap.getHeight() / 2.0, bitmap.getWidth() / 2.0, bitmap.getHeight() / 2.0}, 30);
                    } else {
                        JSONObject document = new JSONObject(Catalog.read(MainActivity.this, previewFile));
                        org.json.JSONArray assets = document.getJSONArray("assets");
                        org.json.JSONArray bounds = document.getJSONArray("catalogBounds");
                        Bitmap[] frames = new Bitmap[assets.length()];
                        for (int index = 0; index < frames.length; index++) {
                            if (!identity.equals(requested)) return;
                            String encoded = assets.getJSONObject(index).getString("p");
                            byte[] bytes = Base64.decode(encoded.substring(encoded.indexOf(',') + 1), Base64.DEFAULT);
                            frames[index] = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                            if (frames[index] == null) throw new IllegalArgumentException("Invalid preview frame");
                        }
                        next = new PreviewImages(frames, new double[]{bounds.getDouble(0), bounds.getDouble(1),
                                bounds.getDouble(2), bounds.getDouble(3)}, document.getDouble("fr"));
                        next.footprint = document.optDouble("catalogFootprint", 132);
                        for (int index = 0; index < frames.length; index++) {
                            next.opacity[index] = document.getJSONArray("layers").getJSONObject(index)
                                    .getJSONObject("ks").getJSONObject("o").optInt("k", 100);
                        }
                    }
                    if (identity.equals(requested)) {
                        images = next;
                        status = "";
                        postInvalidate();
                    }
                } catch (Exception exception) {
                    if (identity.equals(requested)) { status = "Preview unavailable"; postInvalidate(); }
                }
            }, "preview-" + category).start();
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            loadCatalog();
        }

        @Override protected void onDetachedFromWindow() {
            requested = "";
            images = null;
            status = "";
            super.onDetachedFromWindow();
        }

        @Override protected void onDraw(Canvas canvas) {
            float width = getWidth(), height = getHeight();
            canvas.drawRoundRect(0, 0, width, height, dp(16), dp(16), backgroundPaint);
            PreviewImages current = images;
            float compositionScale = Math.min(width, height) / 288f;
            if (current != null) {
                int position = "animations".equals(category)
                        ? (int) (SystemClock.uptimeMillis() * current.fps / 1000 % current.frames.length) : 0;
                Bitmap bitmap = current.frames[position];
                double[] bounds = current.bounds;
                float scale = (float) CatalogScale.percent(category, size, bounds[2] - bounds[0], bounds[3] - bounds[1])
                        / 100f * compositionScale;
                if ("animations".equals(category)) scale *= Math.max(48, Math.min(132, current.footprint)) / 132f;
                float centerX = width / 2 - (float) (bounds[0] + bounds[2]) / 2 * scale;
                float centerY = height / 2 - (float) (bounds[1] + bounds[3]) / 2 * scale;
                float halfWidth = bitmap.getWidth() * scale / 2, halfHeight = bitmap.getHeight() * scale / 2;
                Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                imagePaint.setAlpha(Math.max(0, Math.min(255, current.opacity[position] * 255 / 100)));
                canvas.drawBitmap(bitmap, null, new RectF(centerX - halfWidth, centerY - halfHeight,
                        centerX + halfWidth, centerY + halfHeight), imagePaint);
                if ("animations".equals(category) && isShown()) postInvalidateDelayed(Math.max(16, (long) (1000 / current.fps)));
            } else if (!status.isEmpty()) {
                textPaint.setTextSize(Math.min(dp(13), width / Math.max(1, status.length()) * 1.7f));
                canvas.drawText(status, width / 2, height / 2, textPaint);
            } else if ("icons".equals(category)) {
                android.graphics.drawable.Drawable stock = getDrawable(R.drawable.ic_catalog_fingerprint).mutate();
                stock.setTint(catalogScreen.foreground);
                int diameter = Math.round(200 * compositionScale);
                stock.setBounds((int) (width - diameter) / 2, (int) (height - diameter) / 2,
                        (int) (width + diameter) / 2, (int) (height + diameter) / 2);
                stock.draw(canvas);
            } else {
                canvas.drawText("Samsung default effect", width / 2, height / 2, textPaint);
            }
        }
    }

    private static final class PreviewImages {
        final Bitmap[] frames;
        final double[] bounds;
        final double fps;
        final int[] opacity;
        double footprint = 132;

        PreviewImages(Bitmap[] frames, double[] bounds, double fps) {
            this.frames = frames;
            this.bounds = bounds;
            this.fps = fps;
            this.opacity = new int[frames.length];
            java.util.Arrays.fill(this.opacity, 100);
        }
    }
}
