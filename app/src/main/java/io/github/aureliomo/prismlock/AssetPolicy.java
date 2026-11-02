package io.github.aureliomo.prismlock;

final class AssetPolicy {
    static boolean isIcon(String name) {
        return "ic_fingerprint_aod.json".equals(name)
                || "ic_fingerprint_dark_theme.json".equals(name)
                || "ic_fingerprint_dark_theme_non_alpha.json".equals(name)
                || "ic_fingerprint_light_theme.json".equals(name)
                || "ic_fingerprint_light_theme_non_alpha.json".equals(name);
    }

    static boolean isTouchEffect(String name) {
        return "indisplay_fingerprint_touch_effect_green_circle.json".equals(name)
                || "indisplay_fingerprint_touch_effect_white_circle.json".equals(name)
                || "indisplay_fingerprint_touch_effect_ripple.json".equals(name);
    }
}
