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
import java.io.InputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Replaces Samsung fingerprint artwork and touch-effect data without changing authentication.
 */
public class Hook implements IXposedHookLoadPackage {
    private static final String TARGET = "com.samsung.android.biometrics.app.setting";
    private static final Uri CFG = Uri.parse("content://io.github.aureliomo.prismlock.config/cfg");
    private static final String FILE = "content://io.github.aureliomo.prismlock.config/file/";

    private static volatile int size = Icons.DEF_SIZE;
    private static volatile int stroke10 = Icons.DEF_STROKE10;
    private static volatile String darkKind = "", lightKind = "";
    private static volatile byte[] darkData, lightData;
    private static volatile boolean enabled;
    private static volatile byte[] catalogIcon, catalogIconLight, catalogIconAod, catalogAnimation;
    private static long ver = -1;
    private static long lastRead = 0;
    private static final Set<String> loggedAssets = ConcurrentHashMap.newKeySet();

    private static byte[] readAll(Context c, String slot) {
        try (InputStream in = c.getContentResolver().openInputStream(Uri.parse(FILE + slot))) {
            if (in == null) return null;
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        } catch (Throwable t) {
            XposedBridge.log("PrismLock: error reading " + slot + ": " + t);
            return null;
        }
    }

    private static synchronized void refreshConfig() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastRead < 3000) return;
        lastRead = now;
        try {
            Context c = AndroidAppHelper.currentApplication();
            if (c == null) return;
            Cursor cur = c.getContentResolver().query(CFG, null, null, null, null);
            if (cur == null) return;
            try {
                if (!cur.moveToFirst()) return;
                int nextSize = cur.getInt(0);
                String dk = cur.getString(2), lk = cur.getString(3);
                long v = cur.getLong(4);
                if (v != ver) {
                    enabled = false;
                    byte[] nextIcon = null, nextAnimation = null;
                    String icon = cur.getString(6), animation = cur.getString(7);
                    if (icon != null && !icon.isEmpty()) nextIcon = Catalog.selected(c.createPackageContext("io.github.aureliomo.prismlock", 0), "icons", icon, nextSize);
                    Context catalogContext = c.createPackageContext("io.github.aureliomo.prismlock", 0);
                    byte[] nextLight = nextIcon == null ? null : Catalog.selected(catalogContext, "icons", Catalog.iconTag(catalogContext, icon, "light"), nextSize);
                    byte[] nextAod = nextIcon == null ? null : Catalog.selected(catalogContext, "icons", Catalog.iconTag(catalogContext, icon, "aod"), nextSize);
                    if (animation != null && !animation.isEmpty()) nextAnimation = Catalog.selected(c.createPackageContext("io.github.aureliomo.prismlock", 0), "animations", animation, 72);
                    size = nextSize;
                    stroke10 = cur.getInt(1);
                    enabled = cur.getInt(5) == 1;
                    catalogIcon = nextIcon;
                    catalogIconLight = nextLight;
                    catalogIconAod = nextAod;
                    catalogAnimation = nextAnimation;
                    darkKind = dk == null ? "" : dk;
                    lightKind = lk == null ? "" : lk;
                    darkData = darkKind.isEmpty() ? null : readAll(c, "dark");
                    lightData = lightKind.isEmpty() ? null : readAll(c, "light");
                    ver = v;
                    XposedBridge.log("PrismLock: config size=" + size + " stroke10=" + stroke10
                            + " dark=" + darkKind + " light=" + lightKind);
                }
            } finally {
                cur.close();
            }
        } catch (Throwable t) {
            XposedBridge.log("PrismLock: configuration unavailable (" + t + ")");
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET.equals(lpparam.packageName)) return;

        try {
            XposedHelpers.findAndHookMethod("com.samsung.android.biometrics.app.setting.Utils",
                    lpparam.classLoader, "readFile", File.class, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            File file = (File) param.args[0];
                            if (file == null || !"user_fingerprint_touch_effect.json".equals(file.getName())) return;
                            refreshConfig();
                            if (enabled && catalogAnimation != null) {
                                param.setResult(catalogAnimation);
                                if (loggedAssets.add("custom-touch-json")) XposedBridge.log("PrismLock: using Samsung custom touch JSON path");
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
                            if (enabled && catalogAnimation != null) param.setResult(null);
                        }
                    });
        } catch (Throwable throwable) {
            XposedBridge.log("PrismLock: theme PNG fallback unavailable: " + throwable);
        }

        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (param.args.length == 0 || !(param.args[0] instanceof String)) return;
                    String name = (String) param.args[0];
                    name = name.substring(name.lastIndexOf('/') + 1);
                    boolean touchEffect = AssetPolicy.isTouchEffect(name);
                    if (!AssetPolicy.isIcon(name) && !touchEffect) return;
                    refreshConfig();
                    if (!enabled) return;

                    boolean light = name.contains("light");
                    byte[] data;
                    String kind;
                    if (light && lightData != null) { data = lightData; kind = lightKind; }
                    else { data = darkData; kind = darkKind; }

                    byte[] out = null;
                    if (touchEffect) {
                        if (catalogAnimation == null) return;
                        out = catalogAnimation;
                    } else if (data != null) {
                        out = "png".equals(kind)
                                ? Icons.imageLottie(data, size).getBytes(StandardCharsets.UTF_8)
                                : data;
                    } else if (catalogIcon != null) {
                        out = name.contains("aod") ? catalogIconAod : light ? catalogIconLight : catalogIcon;
                    } else {
                        return;
                    }
                    if (out != null) {
                        if (loggedAssets.add(name)) XposedBridge.log("PrismLock: replacing " + name);
                        param.setResult(new ByteArrayInputStream(out));
                    }
                } catch (Throwable t) {
                    XposedBridge.log("PrismLock: " + t);
                }
            }
        };

        try {
            XposedHelpers.findAndHookMethod(AssetManager.class, "open", String.class, hook);
            XposedHelpers.findAndHookMethod(AssetManager.class, "open", String.class, int.class, hook);
            XposedBridge.log("PrismLock: hook active in " + TARGET);
        } catch (Throwable t) {
            XposedBridge.log("PrismLock: hook failed: " + t);
        }
    }

}
