package io.github.aureliomo.prismlock;

import org.json.JSONArray;
import org.json.JSONObject;

/** Interpolates embedded frames without generating or retaining extra bitmaps. */
final class Motion {
    private Motion() {}

    static void smooth(JSONObject composition) throws Exception {
        JSONArray layers = composition.getJSONArray("layers");
        if (layers.length() < 2) return;
        double start = composition.getDouble("ip");
        double end = composition.getDouble("op");
        // Only the built-in sequential image timeline is eligible. Imports are untouched.
        for (int i = 0; i < layers.length(); i++) {
            JSONObject layer = layers.getJSONObject(i);
            if (layer.optInt("ty", -1) != 2 || layer.getDouble("ip") != start + i
                    || layer.getDouble("op") != start + i + 1
                    || layer.getJSONObject("ks").getJSONObject("o").optInt("a", 0) != 0) return;
        }
        if (end != start + layers.length()) return;
        for (int i = 0; i < layers.length(); i++) {
            JSONObject layer = layers.getJSONObject(i);
            double in = layer.getDouble("ip"), out = layer.getDouble("op");
            double opacity = layer.getJSONObject("ks").getJSONObject("o").getDouble("k");
            double from = i == 0 ? start : in - 0.5;
            double to = i == layers.length() - 1 ? end : out + 0.5;
            JSONArray keys = new JSONArray();
            keys.put(key(from, i == 0 ? opacity : 0));
            if (i > 0) keys.put(key(in + 0.5, opacity));
            // Neighbouring fade intervals meet at the frame's centre.
            if (i == 0) keys.put(key(out - 0.5, opacity));
            keys.put(key(to, i == layers.length() - 1 ? opacity : 0));
            layer.put("ip", from).put("op", to);
            layer.getJSONObject("ks").put("o", new JSONObject().put("a", 1).put("k", keys));
        }
    }

    private static JSONObject key(double time, double value) throws Exception {
        // Linear temporal blending keeps the source cadence and finite duration.
        return new JSONObject().put("t", time).put("s", new JSONArray().put(value))
                .put("i", new JSONObject().put("x", new JSONArray().put(1)).put("y", new JSONArray().put(1)))
                .put("o", new JSONObject().put("x", new JSONArray().put(0)).put("y", new JSONArray().put(0)));
    }
}
