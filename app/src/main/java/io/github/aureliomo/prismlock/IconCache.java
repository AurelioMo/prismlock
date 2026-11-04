package io.github.aureliomo.prismlock;

/** Keep customized compositions separate from Samsung's filename-only stock cache. */
final class IconCache {
    private IconCache() { }

    static String key(String assetPath, long configurationVersion) {
        return "prismlock:" + BuildConfig.VERSION_CODE + ":" + configurationVersion + ":" + assetPath;
    }
}
