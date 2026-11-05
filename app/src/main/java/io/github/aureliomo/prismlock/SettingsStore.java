package io.github.aureliomo.prismlock;

import android.content.SharedPreferences;

/** Publishes every artwork change with a monotonic revision shared by the hook. */
final class SettingsStore {
    private SettingsStore() {}

    static long nextVersion(SharedPreferences prefs) {
        return Math.max(System.currentTimeMillis(), prefs.getLong("ver", 0) + 1);
    }

    static void select(SharedPreferences prefs, String key, String tag) {
        prefs.edit().putString(key, tag).putLong("ver", nextVersion(prefs)).apply();
    }
}
