package io.github.aureliomo.prismlock;

import java.nio.charset.StandardCharsets;

/**
 * Immutable configuration snapshot handed to the hook.
 *
 * <p>Building the complete snapshot before publishing it avoids exposing a
 * half-refreshed configuration while Samsung reads several assets in a row.</p>
 */
final class RuntimeConfig {
    static final RuntimeConfig EMPTY = new RuntimeConfig(-1L, Icons.DEF_SIZE, Icons.DEF_STROKE10,
            false, "", "", null, null, null, null, null, null);

    final long versionCode;
    final int size;
    final int stroke10;
    final boolean enabled;
    final String darkKind;
    final String lightKind;
    final byte[] darkPayload;
    final byte[] lightPayload;
    final byte[] icon;
    final byte[] iconLight;
    final byte[] iconAod;
    final byte[] animation;

    private RuntimeConfig(long versionCode, int size, int stroke10, boolean enabled,
            String darkKind, String lightKind, byte[] darkPayload, byte[] lightPayload,
            byte[] icon, byte[] iconLight, byte[] iconAod, byte[] animation) {
        this.versionCode = versionCode;
        this.size = size;
        this.stroke10 = stroke10;
        this.enabled = enabled;
        this.darkKind = darkKind;
        this.lightKind = lightKind;
        this.darkPayload = darkPayload;
        this.lightPayload = lightPayload;
        this.icon = icon;
        this.iconLight = iconLight;
        this.iconAod = iconAod;
        this.animation = animation;
    }

    static RuntimeConfig create(long versionCode, int size, int stroke10, boolean enabled,
            String darkKind, String lightKind, byte[] darkData, byte[] lightData,
            byte[] icon, byte[] iconLight, byte[] iconAod, byte[] animation) {
        return new RuntimeConfig(versionCode, size, stroke10, enabled,
                darkKind == null ? "" : darkKind,
                lightKind == null ? "" : lightKind,
                payload(darkKind, darkData, size),
                payload(lightKind, lightData, size),
                icon, iconLight, iconAod, animation);
    }

    /** PNG imports are wrapped in a Lottie document once per configuration change. */
    private static byte[] payload(String kind, byte[] data, int size) {
        if (data == null || kind == null || kind.isEmpty()) return null;
        if (!"png".equals(kind)) return data;
        try {
            return Icons.imageLottie(data, size).getBytes(StandardCharsets.UTF_8);
        } catch (Throwable throwable) {
            return null;
        }
    }

    byte[] imported(boolean light) {
        if (light && lightPayload != null) return lightPayload;
        return darkPayload;
    }

    byte[] iconFor(boolean light, boolean aod) {
        if (aod && iconAod != null) return iconAod;
        if (light && iconLight != null) return iconLight;
        return icon;
    }
}
