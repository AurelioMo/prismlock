package io.github.aureliomo.prismlock;

/** Samsung asset families handled by the module. */
final class AssetPolicy {
    private AssetPolicy() {
    }

    static boolean isIcon(String name) {
        return name.startsWith("ic_fingerprint_") && name.endsWith(".json");
    }

    static boolean isTouchEffect(String name) {
        return name.startsWith("indisplay_fingerprint_touch_effect_") && name.endsWith(".json");
    }
}
