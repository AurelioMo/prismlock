package io.github.aureliomo.prismlock;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class Catalog {
    private static final Pattern FILE = Pattern.compile("(?:icons|animations)_[a-z0-9_]+\\.json");

    private static volatile CatalogIndex cachedIndex;

    private static CatalogIndex index(Context context) throws Exception {
        CatalogIndex current = cachedIndex;
        if (current != null) return current;
        synchronized (Catalog.class) {
            if (cachedIndex == null) cachedIndex = new CatalogIndex(new JSONObject(read(context, "index.json")));
            return cachedIndex;
        }
    }

    private Catalog() {
    }

    static String title(String tag) {
        if (tag == null || tag.isEmpty()) return "Samsung default";
        if (tag.startsWith("coloros17_") && tag.length() > "coloros17_".length()) {
            String name = tag.substring("coloros17_".length()).replace('_', ' ');
            return "ColorOS 17 · " + name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
        }
        switch (tag) {
            case "aqua": return "Aqua";
            case "buble": return "ColorOS 17 · Bubble";
            case "aosp": return "AOSP";
            case "white": return "Classic";
            case "bluegradient": return "Classic · Blue gradient";
            case "outline": return "ColorOS 17 · Bubble";
            case "keqing": return "Keqing";
            case "keqing_appear": return "Keqing · Appear";
            case "keqing_disappear": return "Keqing · Disappear";
            case "pyro": return "Pyro";
            case "coloros": return "ColorOS 17 · Ripple";
            case "coloros2": return "ColorOS 17 · Melodic stream";
            case "coloros3": return "ColorOS 17 · Fizz";
            case "flare": return "ColorOS 17 · Flare";
            case "stardust": return "ColorOS 17 · Stardust";
            case "swirl": return "ColorOS 17 · Swirl";
            case "spark": return "ColorOS 17 · Sparks";
            case "halo": return "ColorOS 17 · Halo";
            case "ripple": return "ColorOS 17 · Ripple";
            case "miui": return "MIUI";
            case "nothingos": return "Nothing OS";
            case "originos": return "OriginOS";
            case "originosblue": return "OriginOS blue";
            case "oxygenos": return "OxygenOS";
            case "starblue": return "Star blue";
            case "yinyang": return "Yin Yang";
            default:
                return tag.substring(0, 1).toUpperCase(Locale.ROOT) + tag.substring(1);
        }
    }

    static String read(Context context, String file) throws Exception {
        if (!FILE.matcher(file).matches() && !"index.json".equals(file)) {
            throw new IllegalArgumentException("Invalid catalog file");
        }
        try (InputStream input = context.getAssets().open("catalog/" + file)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    static final class Style {
        final String tag, label;
        Style(String tag, String label) { this.tag = tag; this.label = label; }
    }

    static List<String> tags(Context context, String kind) throws Exception { return index(context).tags(kind); }
    static String resolve(Context context, String kind, String tag) throws Exception { return index(context).resolve(kind, tag); }
    static String family(Context context, String kind, String tag) throws Exception { return index(context).family(kind, tag); }
    static List<Style> styles(Context context, String kind, String tag) throws Exception { return index(context).styles(kind, tag); }

    static byte[] selected(Context context, String kind, String tag, int size) throws Exception {
        JSONObject composition = new JSONObject(read(context, kind + "_" + resolve(context, kind, tag) + ".json"));
        JSONArray layers = composition.getJSONArray("layers");
        JSONArray assets = composition.getJSONArray("assets");
        JSONArray bounds = composition.getJSONArray("catalogBounds");
        double scale = CatalogScale.percent(kind, size, bounds.getDouble(2) - bounds.getDouble(0),
                bounds.getDouble(3) - bounds.getDouble(1));

        Map<String, JSONObject> byId = new HashMap<>();
        for (int index = 0; index < assets.length(); index++) {
            JSONObject asset = assets.getJSONObject(index);
            byId.put(asset.getString("id"), asset);
        }

        for (int index = 0; index < layers.length(); index++) {
            JSONObject layer = layers.getJSONObject(index);
            JSONObject asset = byId.get(layer.getString("refId"));
            if (asset == null) throw new IllegalArgumentException("Missing image asset");
            layer.getJSONObject("ks").getJSONObject("a").put("k", new JSONArray()
                    .put(asset.getInt("w") / 2.0 + (bounds.getDouble(0) + bounds.getDouble(2)) / 2)
                    .put(asset.getInt("h") / 2.0 + (bounds.getDouble(1) + bounds.getDouble(3)) / 2).put(0));
            layer.getJSONObject("ks").getJSONObject("s")
                    .put("k", new JSONArray().put(scale).put(scale).put(100));
        }
        if ("animations".equals(kind)) Motion.smooth(composition);
        return composition.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String iconTag(Context context, String tag, String variant) throws Exception {
        return index(context).iconTag(tag, variant);
    }
}
