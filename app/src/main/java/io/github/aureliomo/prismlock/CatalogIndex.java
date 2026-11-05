package io.github.aureliomo.prismlock;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immutable lookup tables: one visible family, distinct optional styles and legacy aliases. */
final class CatalogIndex {
    private final Map<String, List<String>> tags = new HashMap<>();
    private final Map<String, JSONObject> entries = new HashMap<>();
    private final Map<String, String> families = new HashMap<>();
    private final Map<String, String> aliases = new HashMap<>();
    private final Map<String, List<Catalog.Style>> styles = new HashMap<>();

    CatalogIndex(JSONObject document) throws Exception {
        for (String kind : new String[]{"icons", "animations"}) {
            List<String> visible = new ArrayList<>();
            JSONArray array = document.getJSONArray(kind);
            for (int i = 0; i < array.length(); i++) {
                JSONObject root = array.getJSONObject(i);
                String family = root.getString("tag");
                visible.add(family);
                List<Catalog.Style> choices = new ArrayList<>();
                add(kind, family, root, choices, root.optString("style_label", "Original"));
                JSONArray alternates = root.optJSONArray("styles");
                if (alternates != null) for (int j = 0; j < alternates.length(); j++) {
                    JSONObject style = alternates.getJSONObject(j);
                    add(kind, family, style, choices, style.getString("label"));
                }
                styles.put(key(kind, family), Collections.unmodifiableList(choices));
            }
            tags.put(kind, Collections.unmodifiableList(visible));
            JSONObject declared = document.optJSONObject("aliases");
            JSONObject byKind = declared == null ? null : declared.optJSONObject(kind);
            if (byKind != null) for (var names = byKind.keys(); names.hasNext();) {
                String from = names.next(), to = byKind.getString(from);
                if (!entries.containsKey(key(kind, to)) || entries.containsKey(key(kind, from))) {
                    throw new IllegalArgumentException("Invalid catalog alias");
                }
                aliases.put(key(kind, from), to);
            }
        }
    }

    private void add(String kind, String family, JSONObject entry, List<Catalog.Style> choices, String label) throws Exception {
        String tag = entry.getString("tag"), key = key(kind, tag);
        if (entries.put(key, entry) != null) throw new IllegalArgumentException("Duplicate catalog tag");
        families.put(key, family);
        choices.add(new Catalog.Style(tag, label));
    }

    List<String> tags(String kind) { return new ArrayList<>(tags.getOrDefault(kind, Collections.emptyList())); }
    String resolve(String kind, String tag) { return aliases.getOrDefault(key(kind, tag), tag); }
    String family(String kind, String tag) {
        String resolved = resolve(kind, tag);
        return families.getOrDefault(key(kind, resolved), resolved);
    }
    List<Catalog.Style> styles(String kind, String tag) {
        return styles.getOrDefault(key(kind, family(kind, tag)), Collections.emptyList());
    }
    String iconTag(String tag, String variant) throws Exception {
        String resolved = resolve("icons", tag);
        JSONObject entry = entries.get(key("icons", resolved));
        JSONArray declared = entry == null ? null : entry.optJSONArray("variants");
        if (declared != null) for (int i = 0; i < declared.length(); i++) {
            if (variant.equals(declared.getString(i))) return resolved + "_" + variant;
        }
        return resolved;
    }
    private static String key(String kind, String tag) { return kind + ":" + tag; }
}
