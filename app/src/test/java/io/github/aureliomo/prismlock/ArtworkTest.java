package io.github.aureliomo.prismlock;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 28, 31, 36})
public class ArtworkTest {
    @org.junit.Before public void setup() { UpdateChecker.source = () -> null; }
    @Test public void activityRetainsAllTabsAndSettingsOnSupportedAndroidVersions() {
        Context app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences(MainActivity.PREFS, 0).edit().clear()
                .putLong("update_last_attempt", System.currentTimeMillis()).commit();
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup().visible()) {
            MainActivity activity = controller.get();
            assertEquals(0, activity.catalogScreen.currentTab());
            android.view.View root = activity.findViewById(android.R.id.content);
            assertNotNull(root);
            findDescription(root, "Animations").performClick();
            assertEquals(1, activity.catalogScreen.currentTab());
            findDescription(root, "Settings").performClick();
            assertEquals(2, activity.catalogScreen.currentTab());
            controller.recreate();
            assertEquals(2, controller.get().catalogScreen.currentTab());
        }
    }

    private static android.view.View findDescription(android.view.View view, String name) {
        if (name.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.view.View found = findDescription(group.getChildAt(i), name);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test public void catalogUsesReusableRowsAndKeepsListsAcrossTabChanges() throws Exception {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup().visible()) {
            MainActivity activity = controller.get();
            android.view.View root = activity.findViewById(android.R.id.content);
            var field = CatalogScreen.class.getDeclaredField("collections"); field.setAccessible(true);
            android.widget.ListView[] lists = (android.widget.ListView[]) field.get(activity.catalogScreen);
            android.widget.ListView icons = lists[0];
            assertEquals(Catalog.tags(activity, "icons").size() + 2, icons.getAdapter().getCount());
            android.widget.ListAdapter adapter = icons.getAdapter();
            android.view.View row = adapter.getView(1, null, icons);
            android.view.View recycled = adapter.getView(2, row, icons);
            assertSame(row, recycled);
            assertEquals(Catalog.title(Catalog.tags(activity, "icons").get(0)),
                    ((android.widget.TextView) ((android.view.ViewGroup) ((android.view.ViewGroup) recycled).getChildAt(1)).getChildAt(0)).getText().toString());
            icons.performItemClick(recycled, 2, 2);
            assertEquals(Catalog.tags(activity, "icons").get(0), activity.preferences().getString("icon", ""));
            findDescription(root, "Animations").performClick();
            findDescription(root, "Fingerprints").performClick();
            assertSame(icons, lists[0]);
        }
    }

    @Test public void maximumOxygenOsPreviewReservesSpaceForTheWholeGlow() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences(MainActivity.PREFS, 0).edit().clear()
                .putBoolean("enabled", true).putInt("size", 80).putString("icon", "oxygenos").commit();
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup().visible()) {
            MainActivity activity = controller.get();
            var method = MainActivity.class.getDeclaredMethod("showPreview"); method.setAccessible(true); method.invoke(activity);
            var field = MainActivity.class.getDeclaredField("fingerprintPreview"); field.setAccessible(true);
            ArtworkPreview preview = (ArtworkPreview) field.get(activity);
            var images = ArtworkPreview.class.getDeclaredField("images"); images.setAccessible(true);
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (images.get(preview) == null && System.nanoTime() < deadline) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
                Thread.sleep(10);
            }
            assertNotNull(images.get(preview));
            assertTrue("Large OxygenOS glow requires more than the old 132dp preview height",
                    preview.getLayoutParams().height >= activity.dp(210));
            var dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            if (dialog != null) dialog.dismiss();
        }
    }

    @Test public void largePngIsSampledBeforeFinalScaling() throws Exception {
        android.graphics.Bitmap source = android.graphics.Bitmap.createBitmap(8192, 32,
                android.graphics.Bitmap.Config.ARGB_8888);
        java.io.ByteArrayOutputStream encoded = new java.io.ByteArrayOutputStream();
        source.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, encoded);
        source.recycle();
        var method = ArtworkImports.class.getDeclaredMethod("preparePng", byte[].class);
        method.setAccessible(true);
        byte[] result = (byte[]) method.invoke(null, (Object) encoded.toByteArray());
        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(result, 0, result.length);
        assertEquals(256, bitmap.getWidth());
        assertEquals(1, bitmap.getHeight());
        bitmap.recycle();
    }

    @Test public void everyEffectKeepsArtworkDurationAndAtMostTwoVisibleLayers() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        for (String tag : Catalog.tags(app, "animations")) {
            JSONObject original = new JSONObject(Catalog.read(app, "animations_" + tag + ".json"));
            JSONObject smoothed = new JSONObject(new String(Catalog.selected(app, "animations", tag, 50),
                    java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(original.getDouble("fr"), smoothed.getDouble("fr"), 0);
            assertEquals(original.getDouble("op"), smoothed.getDouble("op"), 0);
            assertEquals(original.getJSONArray("assets").toString(), smoothed.getJSONArray("assets").toString());
            JSONArray layers = smoothed.getJSONArray("layers");
            for (double time = 0; time < smoothed.getDouble("op"); time += 0.25 * smoothed.optDouble("catalogFrameStep", 1)) {
                int active = 0;
                for (int i = 0; i < layers.length(); i++) {
                    JSONObject layer = layers.getJSONObject(i);
                    if (time >= layer.getDouble("ip") && time < layer.getDouble("op")) active++;
                }
                assertTrue(tag + " overlaps more than two frames", active <= 2);
            }
            for (int i = 0; i < layers.length(); i++) {
                JSONArray keys = layers.getJSONObject(i).getJSONObject("ks").getJSONObject("o").getJSONArray("k");
                double previous = -1;
                for (int k = 0; k < keys.length(); k++) {
                    double time = keys.getJSONObject(k).getDouble("t");
                    assertTrue(time > previous);
                    previous = time;
                }
            }
            if ("aqua".equals(tag)) {
                JSONArray last = layers.getJSONObject(layers.length() - 1).getJSONObject("ks")
                        .getJSONObject("o").getJSONArray("k");
                assertEquals(0, last.getJSONObject(last.length() - 1).getJSONArray("s").getDouble(0), 0);
            }
        }
    }

    @Test public void staticIconsAndVariantsKeepTheirOpacityAndRequestedFootprint() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        for (String tag : Catalog.tags(app, "icons")) {
            for (String variant : new String[]{"dark", "light", "aod"}) {
                JSONObject icon = new JSONObject(new String(Catalog.selected(app, "icons",
                        Catalog.iconTag(app, tag, variant), 50), java.nio.charset.StandardCharsets.UTF_8));
                JSONObject layer = icon.getJSONArray("layers").getJSONObject(0);
                assertEquals(0, layer.getJSONObject("ks").getJSONObject("o").getInt("a"));
                JSONArray bounds = icon.getJSONArray("catalogBounds");
                double extent = Math.max(bounds.getDouble(2) - bounds.getDouble(0),
                        bounds.getDouble(3) - bounds.getDouble(1));
                double scale = layer.getJSONObject("ks").getJSONObject("s").getJSONArray("k").getDouble(0);
                assertEquals(200, extent * scale / 100, 0.001);
            }
        }
    }

    @Test public void previewTimingDoesNotWrapLastFrameIntoFirst() {
        assertEquals(0, FrameTimeline.index(0, 24));
        assertEquals(0, FrameTimeline.blend(0, 0, 24), 0);
        assertEquals(0.5f, FrameTimeline.blend(1, 0, 24), 0);
        assertEquals(1, FrameTimeline.index(1.5, 24));
        assertEquals(23, FrameTimeline.index(23.99, 24));
        assertEquals(0, FrameTimeline.blend(23.99, 23, 24), 0);
    }
}
