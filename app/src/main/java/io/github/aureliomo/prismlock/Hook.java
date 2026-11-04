package io.github.aureliomo.prismlock;

import android.app.AndroidAppHelper;
import android.content.Context;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;
import android.graphics.BitmapFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Replaces Samsung fingerprint artwork and touch-effect data without changing authentication.
 *
 * <p>The hook mirrors the asset family used by RingPrint: every
 * {@code ic_fingerprint_*.json} and
 * {@code indisplay_fingerprint_touch_effect_*.json} asset is eligible, while the
 * selected artwork decides what is returned. Samsung keeps ownership of timing,
 * authentication and sensor behavior.</p>
 */
public class Hook implements IXposedHookLoadPackage {
    private static final String TARGET = "com.samsung.android.biometrics.app.setting";
    private static final Uri CFG = Uri.parse("content://io.github.aureliomo.prismlock.config/cfg");
    private static final String FILE = "content://io.github.aureliomo.prismlock.config/file/";
    private static final long REFRESH_INTERVAL_MS = 2_000L;

    private static volatile RuntimeConfig config = RuntimeConfig.EMPTY;
    private static long lastRead;
    private static final Set<String> loggedAssets = ConcurrentHashMap.newKeySet();
    private static final String CUSTOM_ICON = BuildConfig.APPLICATION_ID + ".customIcon";
    private static final String ICON_VERSION = BuildConfig.APPLICATION_ID + ".iconVersion";

    private static byte[] iconPayload(RuntimeConfig snapshot, String name) {
        boolean light = name.contains("light");
        byte[] imported = snapshot.imported(light);
        return imported != null ? imported : snapshot.iconFor(light, name.contains("aod"));
    }

    /** Load selected icons before Lottie's filename cache can bypass AssetManager.open(). */
    private static void installIconLoader(ClassLoader loader) {
        try {
            Class<?> view = XposedHelpers.findClass("com.airbnb.lottie.LottieAnimationView", loader);
            Class<?> drawable = XposedHelpers.findClass("com.airbnb.lottie.LottieDrawable", loader);
            Object colorFilter = XposedHelpers.getStaticObjectField(
                    XposedHelpers.findClass("com.airbnb.lottie.LottieProperty", loader), "COLOR_FILTER");
            XposedBridge.hookAllMethods(drawable, "addValueCallback", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args.length == 3 && param.args[1] == colorFilter
                            && Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(param.thisObject, CUSTOM_ICON))) {
                        // Samsung tints stock vectors. Embedded custom PNGs already carry their colors.
                        param.args[2] = null;
                    }
                }
            });
            XposedHelpers.findAndHookMethod(view, "setAnimation", String.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    String path = (String) param.args[0];
                    if (path == null) return;
                    String name = path.substring(path.lastIndexOf('/') + 1);
                    if (!AssetPolicy.isIcon(name)) {
                        Object image = XposedHelpers.getObjectField(param.thisObject, "lottieDrawable");
                        XposedHelpers.setAdditionalInstanceField(image, CUSTOM_ICON, false);
                        return;
                    }
                    refreshConfig();
                    RuntimeConfig snapshot = config;
                    byte[] output = snapshot.enabled ? iconPayload(snapshot, name) : null;
                    Object image = XposedHelpers.getObjectField(param.thisObject, "lottieDrawable");
                    Object previous = XposedHelpers.getAdditionalInstanceField(image, CUSTOM_ICON);
                    XposedHelpers.setAdditionalInstanceField(image, CUSTOM_ICON, output != null);
                    if (output == null) return;
                    try {
                        XposedHelpers.callMethod(param.thisObject, "setAnimation",
                                new ByteArrayInputStream(output), IconCache.key(path, snapshot.versionCode));
                        param.setResult(null);
                    } catch (Throwable throwable) {
                        XposedHelpers.setAdditionalInstanceField(image, CUSTOM_ICON, previous);
                        XposedBridge.log("PrismLock: direct icon load unavailable: " + throwable);
                        // Retain the AssetManager fallback on other Lottie versions.
                    }
                }
            });
        } catch (Throwable throwable) {
            XposedBridge.log("PrismLock: versioned icon loader unavailable: " + throwable);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "com.samsung.android.biometrics.app.setting.fingerprint.UdfpsKeyguardSensorWindow",
                    loader, "setAnimationTypeIfNeeded", new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            refreshConfig();
                            Long previous = (Long) XposedHelpers.getAdditionalInstanceField(param.thisObject, ICON_VERSION);
                            if (previous != null && previous.longValue() == config.versionCode) return;
                            // Samsung otherwise skips setAnimation when the theme filename hasn't changed.
                            XposedHelpers.setObjectField(param.thisObject, "mCurrentAnimationType", null);
                            XposedHelpers.setAdditionalInstanceField(param.thisObject, ICON_VERSION, config.versionCode);
                        }
                    });
        } catch (Throwable throwable) {
            XposedBridge.log("PrismLock: keyguard icon refresh unavailable: " + throwable);
        }
    }

    private static byte[] readAll(Context context, String slot) {
        try (InputStream input = context.getContentResolver().openInputStream(Uri.parse(FILE + slot))) {
            if (input == null) return null;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) > 0) output.write(buffer, 0, count);
            return output.toByteArray();
        } catch (Throwable throwable) {
            XposedBridge.log("PrismLock: error reading " + slot + ": " + throwable);
            return null;
        }
    }

    private static synchronized void refreshConfig() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastRead < REFRESH_INTERVAL_MS) return;
        lastRead = now;
        try {
            Context app = AndroidAppHelper.currentApplication();
            if (app == null) return;
            Cursor cursor = app.getContentResolver().query(CFG, null, null, null, null);
            if (cursor == null) return;
            try {
                if (!cursor.moveToFirst()) return;
                long version = cursor.getLong(4);
                if (version == config.versionCode) return;
                RuntimeConfig next = load(app, cursor, version);
                config = next;
                XposedBridge.log("PrismLock: config size=" + next.size + " stroke10=" + next.stroke10
                        + " dark=" + next.darkKind + " light=" + next.lightKind
                        + " enabled=" + next.enabled);
            } finally {
                cursor.close();
            }
        } catch (Throwable throwable) {
            // Keep the last known-good snapshot instead of silently disabling the module.
            XposedBridge.log("PrismLock: configuration unavailable (" + throwable + ")");
        }
    }

    private static RuntimeConfig load(Context app, Cursor cursor, long version) {
        int size = cursor.getInt(0);
        int stroke10 = cursor.getInt(1);
        String darkKind = cursor.getString(2);
        String lightKind = cursor.getString(3);
        boolean enabled = cursor.getInt(5) == 1;
        String iconTag = cursor.getString(6);
        String animationTag = cursor.getString(7);

        byte[] icon = null, iconLight = null, iconAod = null, animation = null;
        try {
            Context module = app.createPackageContext(BuildConfig.APPLICATION_ID, 0);
            if (iconTag != null && !iconTag.isEmpty()) {
                icon = selected(module, "icons", iconTag, size);
                iconLight = selected(module, "icons", Catalog.iconTag(module, iconTag, "light"), size);
                iconAod = selected(module, "icons", Catalog.iconTag(module, iconTag, "aod"), size);
            }
            if (animationTag != null && !animationTag.isEmpty()) {
                animation = selected(module, "animations", animationTag, 72);
            }
        } catch (Throwable throwable) {
            XposedBridge.log("PrismLock: catalog artwork unavailable (" + throwable + ")");
        }

        byte[] darkData = darkKind == null || darkKind.isEmpty() ? null : readAll(app, "dark");
        byte[] lightData = lightKind == null || lightKind.isEmpty() ? null : readAll(app, "light");
        return RuntimeConfig.create(version, size, stroke10, enabled,
                darkKind, lightKind, darkData, lightData, icon, iconLight, iconAod, animation);
    }

    private static byte[] selected(Context module, String kind, String tag, int size) {
        try {
            return Catalog.selected(module, kind, tag, size);
        } catch (Throwable throwable) {
            XposedBridge.log("PrismLock: cannot load " + kind + "_" + tag + " (" + throwable + ")");
            return null;
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam parameter) {
        if (!TARGET.equals(parameter.packageName)) return;
        installIconLoader(parameter.classLoader);

        try {
            XposedHelpers.findAndHookMethod("com.samsung.android.biometrics.app.setting.Utils",
                    parameter.classLoader, "readFile", File.class, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            File file = (File) param.args[0];
                            if (file == null || !"user_fingerprint_touch_effect.json".equals(file.getName())) return;
                            refreshConfig();
                            RuntimeConfig snapshot = config;
                            if (snapshot.enabled && snapshot.animation != null) {
                                param.setResult(snapshot.animation);
                                if (loggedAssets.add("custom-touch-json")) {
                                    XposedBridge.log("PrismLock: using Samsung custom touch JSON path");
                                }
                            }
                        }
                    });
        } catch (Throwable throwable) {
            XposedBridge.log("PrismLock: custom touch path unavailable: " + throwable);
        }

        try {
            XposedHelpers.findAndHookMethod(BitmapFactory.class, "decodeFile", String.class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            String path = (String) param.args[0];
                            if (path == null || !"user_fingerprint_touch_effect.png".equals(new File(path).getName())) return;
                            refreshConfig();
                            RuntimeConfig snapshot = config;
                            if (snapshot.enabled && snapshot.animation != null) param.setResult(null);
                        }
                    });
        } catch (Throwable throwable) {
            XposedBridge.log("PrismLock: theme PNG fallback unavailable: " + throwable);
        }

        XC_MethodHook assetHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (param.args.length == 0 || !(param.args[0] instanceof String)) return;
                    String name = (String) param.args[0];
                    name = name.substring(name.lastIndexOf('/') + 1);
                    boolean touchEffect = AssetPolicy.isTouchEffect(name);
                    if (!AssetPolicy.isIcon(name) && !touchEffect) return;
                    refreshConfig();
                    RuntimeConfig snapshot = config;
                    if (!snapshot.enabled) return;

                    byte[] output;
                    if (touchEffect) {
                        output = snapshot.animation;
                    } else {
                        output = iconPayload(snapshot, name);
                    }
                    if (output != null) {
                        if (loggedAssets.add(name)) XposedBridge.log("PrismLock: replacing " + name);
                        param.setResult(new ByteArrayInputStream(output));
                    }
                } catch (Throwable throwable) {
                    XposedBridge.log("PrismLock: " + throwable);
                }
            }
        };

        try {
            XposedHelpers.findAndHookMethod(AssetManager.class, "open", String.class, assetHook);
            XposedHelpers.findAndHookMethod(AssetManager.class, "open", String.class, int.class, assetHook);
            XposedBridge.log("PrismLock: hook active in " + TARGET);
        } catch (Throwable throwable) {
            XposedBridge.log("PrismLock: hook failed: " + throwable);
        }
    }
}
