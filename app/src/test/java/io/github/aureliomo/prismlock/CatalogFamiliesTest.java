package io.github.aureliomo.prismlock;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.View;
import android.widget.ListView;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 31, 36})
public class CatalogFamiliesTest {
    private Context app;
    @Before public void setup() {
        app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences(MainActivity.PREFS, 0).edit().clear().commit();
        UpdateChecker.source = () -> null;
    }

    @Test public void allOldSelectionsResolveAndOutlineUsesTheSharperDesign() throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream("catalog-1.0.4-tags.json")) {
            assertNotNull(input);
            JSONObject legacy = new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            for (String kind : new String[]{"icons", "animations"}) {
                var tags = legacy.getJSONArray(kind);
                for (int i = 0; i < tags.length(); i++) {
                    String tag = tags.getString(i);
                    assertNotNull(Catalog.selected(app, kind, tag, 50));
                    assertTrue(Catalog.tags(app, kind).contains(Catalog.family(app, kind, tag)));
                    String resolved = Catalog.resolve(app, kind, tag);
                    assertTrue(Catalog.styles(app, kind, tag).stream().anyMatch(style -> style.tag.equals(resolved)));
                }
            }
        }
        assertArrayEquals(Catalog.selected(app, "icons", "outline", 50),
                Catalog.selected(app, "icons", "coloros17_bubble", 50));
        // Keep the inventory assertion aligned with the current catalog index. The
        // legacy identifiers above are intentionally broader than the visible
        // families because several entries are now grouped as styles.
        assertEquals(37, Catalog.tags(app, "icons").size());
        assertEquals(24, Catalog.tags(app, "animations").size());
    }

    @Test public void alternateColorsRetainOneVisibleRowAndTheirOwnLightAodArtwork() throws Exception {
        for (String tag : new String[]{"bluegradient", "originosblue", "starblue", "coloros17_simple", "coloros17_ring", "coloros2"}) {
            String family = Catalog.family(app, "icons", tag);
            assertNotEquals(tag, family);
            assertFalse(Catalog.tags(app, "icons").contains(tag));
            for (String theme : new String[]{"dark", "light", "aod"}) {
                String resolved = Catalog.iconTag(app, tag, theme);
                assertNotNull(Catalog.selected(app, "icons", resolved, 50));
            }
        }
        // Similar category names do not imply identical ridge geometry.
        assertNotEquals(Catalog.family(app, "icons", "miui"), Catalog.family(app, "icons", "coloros17_halo"));
        assertNotEquals(Catalog.family(app, "animations", "genshin"), Catalog.family(app, "animations", "keqing"));
    }

    @Test public void variantDialogSavesSpecificArtworkAndCancelPreservesSelection() throws Exception {
        app.getSharedPreferences(MainActivity.PREFS, 0).edit().putString("icon", "bluegradient").commit();
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup().visible()) {
            MainActivity activity = controller.get();
            ListView list = collection(activity, 0);
            int position = Catalog.tags(app, "icons").indexOf("white") + 2;
            View row = list.getAdapter().getView(position, null, list);
            assertTrue(row.isSelected());
            list.performItemClick(row, position, position);
            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(dialog);
            assertEquals(2, dialog.getListView().getAdapter().getCount());
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
            assertEquals("bluegradient", activity.preferences().getString("icon", ""));
            list.performItemClick(row, position, position);
            dialog = ShadowAlertDialog.getLatestAlertDialog();
            ListView choices = dialog.getListView();
            choices.performItemClick(choices.getAdapter().getView(0, null, choices), 0, 0);
            assertEquals("white", activity.preferences().getString("icon", ""));
            assertFalse(dialog.isShowing());
            controller.recreate();
            assertEquals("white", controller.get().preferences().getString("icon", ""));
        }
    }

    @Test public void keqingContainsTheElectroAndBothTransitionSequencesWithTransparentEnding() throws Exception {
        List<Catalog.Style> styles = Catalog.styles(app, "animations", "keqing");
        assertEquals(3, styles.size());
        assertEquals("keqing", styles.get(0).tag);
        assertEquals("keqing_appear", styles.get(1).tag);
        assertEquals("keqing_disappear", styles.get(2).tag);
        for (Catalog.Style style : styles) {
            JSONObject document = new JSONObject(Catalog.read(app, "animations_" + style.tag + ".json"));
            assertEquals(120, document.getInt("fr"));
            assertEquals(60, document.getInt("catalogSourceFps"));
            assertEquals("keqing".equals(style.tag) ? 61 : 25, document.getJSONArray("layers").length());
            assertTrue(document.getDouble("op") / document.getDouble("fr") <= 1.5);
            if (!"keqing_appear".equals(style.tag)) {
                var assets = document.getJSONArray("assets");
                Bitmap ending = bitmap(assets.getJSONObject(assets.length() - 1).getString("p"));
                for (int y = 0; y < ending.getHeight(); y++) for (int x = 0; x < ending.getWidth(); x++) {
                    assertEquals(0, ending.getPixel(x, y) >>> 24);
                }
                ending.recycle();
            }
        }
    }

    @Test public void faintKeqingIconKeepsOriginalOpacityAndCorrectBoundsInAllThemes() throws Exception {
        int[] originalAlpha = null;
        for (String theme : new String[]{"dark", "light", "aod"}) {
            String tag = Catalog.iconTag(app, "keqing", theme);
            JSONObject doc = new JSONObject(Catalog.read(app, "icons_" + tag + ".json"));
            assertEquals(16, doc.getInt("catalogAlphaThreshold"));
            Bitmap image = bitmap(doc.getJSONArray("assets").getJSONObject(0).getString("p"));
            int[] alpha = new int[image.getWidth() * image.getHeight()];
            image.getPixels(alpha, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
            int peak = 0;
            for (int i = 0; i < alpha.length; i++) { alpha[i] >>>= 24; peak = Math.max(peak, alpha[i]); }
            assertEquals(102, peak);
            if (originalAlpha == null) originalAlpha = alpha; else assertArrayEquals(originalAlpha, alpha);
            assertNotNull(Catalog.selected(app, "icons", tag, 80));
            image.recycle();
        }
    }

    private static Bitmap bitmap(String encoded) {
        byte[] bytes = Base64.decode(encoded.substring(encoded.indexOf(',') + 1), Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }
    private static ListView collection(MainActivity activity, int index) throws Exception {
        var field = CatalogScreen.class.getDeclaredField("collections"); field.setAccessible(true);
        return ((ListView[]) field.get(activity.catalogScreen))[index];
    }
}
