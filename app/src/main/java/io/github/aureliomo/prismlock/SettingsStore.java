package io.github.aureliomo.prismlock;

import android.content.SharedPreferences;

import java.util.function.Consumer;

/** Publishes every artwork change with a monotonic revision shared by the hook. */
final class SettingsStore {
    private SettingsStore() {}

    static synchronized void applyArtworkChange(SharedPreferences prefs, Consumer<SharedPreferences.Editor> change) {
        artworkEditor(prefs, change).apply();
    }

    static synchronized boolean commitArtworkChange(SharedPreferences prefs, Consumer<SharedPreferences.Editor> change) {
        return artworkEditor(prefs, change).commit();
    }

    private static SharedPreferences.Editor artworkEditor(SharedPreferences prefs, Consumer<SharedPreferences.Editor> change) {
        SharedPreferences.Editor editor = prefs.edit();
        change.accept(editor);
        return editor.putLong("ver", Math.max(System.currentTimeMillis(), prefs.getLong("ver", 0) + 1));
    }

    static void select(SharedPreferences prefs, String key, String tag) {
        applyArtworkChange(prefs, editor -> editor.putString(key, tag));
    }
}
