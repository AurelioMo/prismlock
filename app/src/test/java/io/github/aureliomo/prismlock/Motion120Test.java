package io.github.aureliomo.prismlock;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 31, 36})
public class Motion120Test {
    @Test public void timebaseIs120WithoutChangingAnyExistingDurationOrAddingImages() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        try (var input = getClass().getClassLoader().getResourceAsStream("catalog-native-timing.json")) {
            JSONObject reference = new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            for (String kind : new String[]{"icons", "animations"}) {
                JSONObject entries = reference.getJSONObject(kind);
                for (var tags = entries.keys(); tags.hasNext();) {
                    String tag = tags.next();
                    JSONObject expected = entries.getJSONObject(tag);
                    JSONObject composition = new JSONObject(new String(Catalog.selected(app, kind, tag, 50), StandardCharsets.UTF_8));
                    assertEquals(tag, 120, composition.getInt("fr"));
                    assertEquals(tag, expected.getDouble("seconds"),
                            (composition.getDouble("op") - composition.getDouble("ip")) / 120, 0.000001);
                    assertEquals(tag, expected.getInt("frames"), composition.getJSONArray("assets").length());
                }
            }
        }
    }

    @Test public void native60And30FramesHaveDistinctIntermediate120HzSamples() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        for (String tag : new String[]{"keqing", "aqua"}) {
            JSONObject effect = new JSONObject(new String(Catalog.selected(app, "animations", tag, 50), StandardCharsets.UTF_8));
            JSONObject first = effect.getJSONArray("layers").getJSONObject(0);
            double step = effect.getDouble("catalogFrameStep");
            double midpoint = step;
            assertEquals(50, opacity(first, midpoint), 0.00001);
            double before = opacity(first, midpoint - 0.5);
            double after = opacity(first, midpoint + 0.5);
            assertTrue(before > 50);
            assertTrue(after < 50);
            JSONObject second = effect.getJSONArray("layers").getJSONObject(1);
            assertEquals(100, opacity(first, midpoint) + opacity(second, midpoint), 0.00001);
        }
    }

    private static double opacity(JSONObject layer, double frame) throws Exception {
        JSONArray keys = layer.getJSONObject("ks").getJSONObject("o").getJSONArray("k");
        for (int i = 0; i < keys.length() - 1; i++) {
            JSONObject start = keys.getJSONObject(i), end = keys.getJSONObject(i + 1);
            if (frame >= start.getDouble("t") && frame <= end.getDouble("t")) {
                double position = (frame - start.getDouble("t")) / (end.getDouble("t") - start.getDouble("t"));
                return start.getJSONArray("s").getDouble(0) * (1 - position) + end.getJSONArray("s").getDouble(0) * position;
            }
        }
        throw new AssertionError("Sample falls outside opacity keyframes");
    }
}
