package io.github.aureliomo.prismlock;

import android.app.Application;
import android.content.Context;
import android.content.res.AssetManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.libxposed.api.XposedModule;

/** API 102 entrypoint. Hooks only visual resources in Samsung's biometrics process. */
public final class Hook extends XposedModule {
    static final String TARGET = "com.samsung.android.biometrics.app.setting";
    static final Uri CFG = Uri.parse("content://io.github.aureliomo.prismlock.config/cfg");
    private static final String FILE = "content://io.github.aureliomo.prismlock.config/file/";
    private final AtomicBoolean installed = new AtomicBoolean();
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final Set<String> logged = ConcurrentHashMap.newKeySet();
    private final Map<Object, Boolean> customDrawables = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Object, String> iconViews = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Object, Long> windowVersions = Collections.synchronizedMap(new WeakHashMap<>());
    private final ExecutorService configWorker = AppWorkers.serial("prismlock-config");
    private volatile RuntimeConfig config = RuntimeConfig.EMPTY;
    private volatile Context app;
    private volatile long lastRead = -2_000;
    private Handler main;
    private Method setIcon;
    private ContentObserver observer;

    @Override public void onPackageReady(PackageReadyParam parameter) {
        if (!TARGET.equals(parameter.getPackageName()) || !installed.compareAndSet(false, true)) return;
        main = new Handler(Looper.getMainLooper());
        ClassLoader loader = parameter.getClassLoader();
        optional("application context", () -> {
            Method attach = Application.class.getDeclaredMethod("attach", Context.class);
            hook(attach).intercept(chain -> {
                Object result = chain.proceed();
                attachContext((Context) chain.getThisObject());
                return result;
            });
        });
        installIconLoader(loader);
        optional("custom touch JSON", () -> {
            Method read = Class.forName(TARGET + ".Utils", false, loader).getDeclaredMethod("readFile", File.class);
            hook(read).intercept(chain -> {
                File file = (File) chain.getArg(0);
                if (file != null && "user_fingerprint_touch_effect.json".equals(file.getName())) {
                    refreshConfig(false);
                    RuntimeConfig snapshot = config;
                    if (snapshot.enabled && snapshot.animation != null) return snapshot.animation;
                }
                return chain.proceed();
            });
        });
        optional("touch PNG fallback", () -> hook(BitmapFactory.class.getDeclaredMethod("decodeFile", String.class))
                .intercept(chain -> {
                    String path = (String) chain.getArg(0);
                    if (path != null && "user_fingerprint_touch_effect.png".equals(basename(path))) {
                        refreshConfig(false);
                        RuntimeConfig snapshot = config;
                        if (snapshot.enabled && snapshot.animation != null) return null;
                    }
                    return chain.proceed();
                }));
        for (Class<?>[] signature : new Class<?>[][]{{String.class}, {String.class, int.class}}) {
            optional("asset loader", () -> hook(AssetManager.class.getDeclaredMethod("open", signature))
                    .intercept(chain -> {
                        String name = basename((String) chain.getArg(0));
                        boolean effect = AssetPolicy.isTouchEffect(name);
                        if (!effect && !AssetPolicy.isIcon(name)) return chain.proceed();
                        refreshConfig(false);
                        RuntimeConfig snapshot = config;
                        byte[] output = !snapshot.enabled ? null : effect ? snapshot.animation : iconPayload(snapshot, name);
                        return output == null ? chain.proceed() : new ByteArrayInputStream(output);
                    }));
        }
        log(Log.INFO, "PrismLock", "API 102 hooks installed in " + TARGET);
    }

    private void installIconLoader(ClassLoader loader) {
        optional("icon loader", () -> {
            Class<?> view = Class.forName("com.airbnb.lottie.LottieAnimationView", false, loader);
            Field drawable = field(view, "lottieDrawable");
            Method stream = view.getMethod("setAnimation", InputStream.class, String.class);
            setIcon = view.getMethod("setAnimation", String.class);
            // Resolve method handles once; do not repeat reflection lookup during icon changes.
            var streamInvoker = getInvoker(stream);
            hook(setIcon).intercept(chain -> {
                Object target = chain.getThisObject();
                String path = (String) chain.getArg(0);
                if (path == null) return chain.proceed();
                String name = basename(path);
                Object image = drawable.get(target);
                if (!AssetPolicy.isIcon(name)) {
                    iconViews.remove(target);
                    customDrawables.remove(image);
                    IconOverflow.restore(target);
                    return chain.proceed();
                }
                iconViews.put(target, path);
                refreshConfig(false);
                RuntimeConfig snapshot = config;
                byte[] output = snapshot.enabled ? iconPayload(snapshot, name) : null;
                if (output == null) {
                    customDrawables.remove(image);
                    IconOverflow.restore(target);
                    return chain.proceed();
                }
                Boolean previous = customDrawables.put(image, true);
                try {
                    streamInvoker.invoke(target, new ByteArrayInputStream(output), IconCache.key(path, snapshot.versionCode));
                    IconOverflow.allow(target);
                    return null;
                } catch (Throwable failure) {
                    if (previous == null) customDrawables.remove(image);
                    else customDrawables.put(image, previous);
                    warnOnce("direct icon load", failure);
                    return chain.proceed();
                }
            });
        });
        // A missing optional tint API must not disable the independent icon loader.
        optional("custom icon tint", () -> {
            Class<?> drawable = Class.forName("com.airbnb.lottie.LottieDrawable", false, loader);
            Object color = field(Class.forName("com.airbnb.lottie.LottieProperty", false, loader), "COLOR_FILTER").get(null);
            for (Method method : drawable.getDeclaredMethods()) {
                if (!method.getName().equals("addValueCallback") || method.getParameterCount() != 3) continue;
                hook(method).intercept(chain -> {
                    if (chain.getArg(1) == color && Boolean.TRUE.equals(customDrawables.get(chain.getThisObject()))) {
                        return chain.proceed(new Object[]{chain.getArg(0), chain.getArg(1), null});
                    }
                    return chain.proceed();
                });
            }
        });
        optional("keyguard refresh", () -> {
            Class<?> window = Class.forName(TARGET + ".fingerprint.UdfpsKeyguardSensorWindow", false, loader);
            Field current = field(window, "mCurrentAnimationType");
            hook(window.getDeclaredMethod("setAnimationTypeIfNeeded")).intercept(chain -> {
                refreshConfig(false);
                long version = config.versionCode;
                Long previous = windowVersions.put(chain.getThisObject(), version);
                if (previous == null || previous != version) current.set(chain.getThisObject(), null);
                return chain.proceed();
            });
        });
    }

    private void attachContext(Context context) {
        app = context;
        observer = new ContentObserver(main) {
            @Override public void onChange(boolean selfChange) { refreshConfig(true); }
        };
        try {
            context.getContentResolver().registerContentObserver(CFG, false, observer);
        } catch (Exception failure) { warnOnce("configuration observer", failure); }
        refreshConfig(true);
    }

    /** Binder IO, asset parsing and image wrapping never run on Samsung's UI thread. */
    private void refreshConfig(boolean force) {
        if (app == null) return;
        if (force) dirty.set(true);
        if (!dirty.get() && SystemClock.elapsedRealtime() - lastRead < 2_000) return;
        if (!refreshing.compareAndSet(false, true)) return;
        configWorker.execute(() -> {
            try {
                do {
                    dirty.set(false);
                    lastRead = SystemClock.elapsedRealtime();
                    try (Cursor cursor = app.getContentResolver().query(CFG, null, null, null, null)) {
                        if (cursor == null || !cursor.moveToFirst() || cursor.getLong(4) == config.versionCode) continue;
                        RuntimeConfig next = load(app, cursor);
                        config = next;
                        main.post(this::reloadIcons);
                    } catch (Throwable failure) { warnOnce("configuration refresh", failure); }
                } while (dirty.get());
            } finally {
                refreshing.set(false);
                if (dirty.get()) refreshConfig(true);
            }
        });
    }

    private void reloadIcons() {
        if (setIcon == null) return;
        ArrayList<Map.Entry<Object, String>> views;
        synchronized (iconViews) { views = new ArrayList<>(iconViews.entrySet()); }
        for (Map.Entry<Object, String> entry : views) {
            Object view = entry.getKey();
            if (view == null) continue;
            try { getInvoker(setIcon).invoke(view, entry.getValue()); }
            catch (Throwable failure) { warnOnce("visible icon refresh", failure); }
        }
    }

    static RuntimeConfig load(Context app, Cursor cursor) throws Exception {
        long version = cursor.getLong(4);
        int size = cursor.getInt(0), stroke = cursor.getInt(1);
        String dark = cursor.getString(2), light = cursor.getString(3);
        if (cursor.getInt(5) != 1) return RuntimeConfig.create(version, size, stroke, false,
                dark, light, null, null, null, null, null, null);
        Context module = app.createPackageContext(BuildConfig.APPLICATION_ID, 0);
        String tag = cursor.getString(6), effect = cursor.getString(7);
        byte[] icon = null, iconLight = null, iconAod = null, animation = null;
        if (tag != null && !tag.isEmpty()) {
            tag = Catalog.resolve(module, "icons", tag);
            icon = Catalog.selected(module, "icons", tag, size);
            String lightTag = Catalog.iconTag(module, tag, "light"), aodTag = Catalog.iconTag(module, tag, "aod");
            iconLight = tag.equals(lightTag) ? icon : Catalog.selected(module, "icons", lightTag, size);
            iconAod = tag.equals(aodTag) ? icon : Catalog.selected(module, "icons", aodTag, size);
        }
        if (effect != null && !effect.isEmpty()) animation = Catalog.selected(module, "animations", effect, size);
        return RuntimeConfig.create(version, size, stroke, true, dark, light,
                imported(app, "dark", dark), imported(app, "light", light), icon, iconLight, iconAod, animation);
    }

    private static byte[] imported(Context app, String slot, String kind) throws Exception {
        if (kind == null || kind.isEmpty()) return null;
        try (InputStream input = app.getContentResolver().openInputStream(Uri.parse(FILE + slot))) {
            if (input == null) throw new IllegalStateException("Imported artwork unavailable");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > 900 * 1024) throw new IllegalStateException("Imported artwork too large");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    static byte[] iconPayload(RuntimeConfig snapshot, String name) {
        boolean light = name.contains("light");
        byte[] imported = snapshot.imported(light);
        return imported != null ? imported : snapshot.iconFor(light, name.contains("aod"));
    }

    private static String basename(String path) { return path == null ? "" : path.substring(path.lastIndexOf('/') + 1); }

    static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private interface Setup { void run() throws Throwable; }
    private void optional(String name, Setup setup) {
        try { setup.run(); } catch (Throwable failure) { warnOnce(name, failure); }
    }
    private void warnOnce(String name, Throwable failure) {
        if (logged.add(name)) log(Log.WARN, "PrismLock", name + " unavailable; keeping Samsung fallback", failure);
    }
}
