package io.github.aureliomo.prismlock;

import android.content.Context;
import android.widget.FrameLayout;
import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.airbnb.lottie.LottieProperty;
import com.samsung.android.biometrics.app.setting.fingerprint.UdfpsKeyguardSensorWindow;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

/** Real API 102 module, with only the framework interception boundary simulated. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 31, 36})
public class ModernHookTest {
    private Hook module;
    private Context app;
    private final Map<Executable, XposedInterface.Hooker> hooks = new HashMap<>();

    @Before public void setup() {
        app = RuntimeEnvironment.getApplication();
        module = new Hook();
        XposedInterface framework = (XposedInterface) proxy(XposedInterface.class, (object, method, args) -> {
            if (method.getName().equals("hook")) {
                Executable origin = (Executable) args[0];
                return proxy(XposedInterface.HookBuilder.class, (builder, operation, values) -> {
                    if (operation.getName().equals("intercept")) { hooks.put(origin, (XposedInterface.Hooker) values[0]); return null; }
                    return builder;
                });
            }
            if (method.getName().equals("getInvoker")) {
                Method origin = (Method) args[0];
                return proxy(XposedInterface.Invoker.class, (invoker, operation, values) -> {
                    if (operation.getName().equals("invoke")) return original(origin, values[0], (Object[]) values[1]);
                    return invoker;
                });
            }
            return null;
        });
        module.attachFramework(framework, () -> {});
    }

    @After public void cleanup() throws Exception {
        ((java.util.concurrent.ExecutorService) field("configWorker").get(module)).shutdownNow();
    }

    private void ready(String name) {
        module.onPackageReady((XposedModuleInterface.PackageReadyParam) proxy(
                XposedModuleInterface.PackageReadyParam.class, (object, method, args) -> {
                    if (method.getName().equals("getPackageName")) return name;
                    if (method.getName().equals("getClassLoader")) return getClass().getClassLoader();
                    return null;
                }));
    }
    private static Object proxy(Class<?> type, InvocationHandler handler) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
    private static Object original(Method method, Object target, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }
    private Object call(Object target, String name, Class<?>[] signature, Object... args) throws Throwable {
        Method method = target.getClass().getMethod(name, signature);
        XposedInterface.Hooker hook = hooks.get(method);
        if (hook == null) return original(method, target, args);
        return hook.intercept(new XposedInterface.Chain() {
            public Executable getExecutable() { return method; }
            public Object getThisObject() { return target; }
            public List<Object> getArgs() { return Arrays.asList(args); }
            public Object getArg(int index) { return args[index]; }
            public Object proceed() throws Throwable { return original(method, target, args); }
            public Object proceed(Object[] values) throws Throwable { return original(method, target, values); }
            public Object proceedWith(Object receiver) throws Throwable { return original(method, receiver, args); }
            public Object proceedWith(Object receiver, Object[] values) throws Throwable { return original(method, receiver, values); }
        });
    }
    private static Field field(String name) throws Exception { return Hook.field(Hook.class, name); }
    private void snapshot(long version, boolean enabled, String imported) throws Exception {
        field("config").set(module, RuntimeConfig.create(version, 80, 26, enabled,
                imported == null ? "" : "json", "", bytes(imported), null,
                bytes("color-" + version), bytes("light-" + version), bytes("aod-" + version), bytes("effect")));
    }
    private static byte[] bytes(String value) { return value == null ? null : value.getBytes(StandardCharsets.UTF_8); }
    private void icon(LottieAnimationView view, String name) throws Throwable {
        call(view, "setAnimation", new Class<?>[]{String.class}, name);
    }
    private void filter(LottieDrawable drawable, Object value) throws Throwable {
        call(drawable, "addValueCallback", new Class<?>[]{Object.class, Object.class, Object.class},
                new Object(), LottieProperty.COLOR_FILTER, value);
    }

    @Test public void scopedRegistrationIsIdempotentAndUsesModernEntrypoint() {
        ready("com.example.unrelated");
        assertTrue(hooks.isEmpty());
        ready(Hook.TARGET);
        assertTrue(hooks.size() >= 6);
        int count = hooks.size();
        ready(Hook.TARGET);
        assertEquals(count, hooks.size());
        assertEquals(102, module.getApiVersion());
    }

    @Test public void cacheVariantsImportsAndTintScopeRemainCorrect() throws Throwable {
        ready(Hook.TARGET);
        LottieAnimationView view = new LottieAnimationView(app);
        String dark = "ic_fingerprint_dark_theme.json";
        view.setAnimation(dark);
        snapshot(1, true, null);
        icon(view, dark);
        assertEquals("color-1", view.shown);
        String previous = view.lastKey;
        snapshot(2, true, null);
        icon(view, dark);
        assertEquals("color-2", view.shown);
        assertNotEquals(previous, view.lastKey);
        icon(view, "ic_fingerprint_light_theme.json"); assertEquals("light-2", view.shown);
        icon(view, "ic_fingerprint_aod.json"); assertEquals("aod-2", view.shown);
        Object tint = new Object();
        filter(view.lottieDrawable, tint); assertNull(view.lottieDrawable.callback);
        LottieAnimationView effect = new LottieAnimationView(app);
        icon(effect, "indisplay_fingerprint_touch_effect_ripple.json");
        filter(effect.lottieDrawable, tint); assertSame(tint, effect.lottieDrawable.callback);
        snapshot(3, true, "imported"); icon(view, dark); assertEquals("imported", view.shown);
        snapshot(4, false, null); icon(view, dark); assertEquals("stock", view.shown);
        filter(view.lottieDrawable, tint); assertSame(tint, view.lottieDrawable.callback);
    }

    @Test public void enlargedGlowEscapesClippingAndStockRestoresParentFlags() throws Throwable {
        ready(Hook.TARGET);
        FrameLayout parent = new FrameLayout(app);
        LottieAnimationView first = new LottieAnimationView(app), second = new LottieAnimationView(app);
        parent.addView(first); parent.addView(second);
        assertTrue(parent.getClipChildren());
        snapshot(1, true, null);
        icon(first, "ic_fingerprint_dark.json");
        icon(second, "ic_fingerprint_light.json");
        assertFalse(first.getClipToCompositionBounds());
        assertFalse(parent.getClipChildren());
        assertFalse(parent.getClipToPadding());
        snapshot(2, false, null);
        icon(first, "ic_fingerprint_dark.json");
        assertTrue(first.getClipToCompositionBounds());
        assertFalse(parent.getClipChildren());
        icon(second, "ic_fingerprint_light.json");
        assertTrue(parent.getClipChildren());
        assertTrue(parent.getClipToPadding());
    }

    @Test public void configurationIoIsAsynchronousAndFailedRefreshKeepsCompleteSnapshot() throws Exception {
        ready(Hook.TARGET);
        java.util.concurrent.CountDownLatch queried = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch finish = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Thread> thread = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicBoolean failing = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        ConfigProvider provider = new ConfigProvider() {
            @Override public android.database.Cursor query(android.net.Uri uri, String[] projection,
                    String selection, String[] args, String order) {
                calls.incrementAndGet();
                thread.set(Thread.currentThread());
                queried.countDown();
                try { if (!finish.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
                catch (InterruptedException failure) { throw new IllegalStateException(failure); }
                if (failing.get()) throw new IllegalStateException("simulated provider failure");
                return super.query(uri, projection, selection, args, order);
            }
        };
        android.content.pm.ProviderInfo info = new android.content.pm.ProviderInfo();
        info.authority = "io.github.aureliomo.prismlock.config";
        provider.attachInfo(app, info);
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(info.authority, provider);
        app.getSharedPreferences(MainActivity.PREFS, 0).edit().clear().putBoolean("enabled", false).putLong("ver", 2).commit();
        snapshot(1, true, null);
        field("app").set(module, app);
        Method refresh = Hook.class.getDeclaredMethod("refreshConfig", boolean.class); refresh.setAccessible(true);
        refresh.invoke(module, true);
        assertTrue(queried.await(5, java.util.concurrent.TimeUnit.SECONDS));
        assertNotSame(android.os.Looper.getMainLooper().getThread(), thread.get());
        assertEquals(1, ((RuntimeConfig) field("config").get(module)).versionCode);
        finish.countDown();
        awaitRefresh();
        RuntimeConfig complete = (RuntimeConfig) field("config").get(module);
        assertEquals(2, complete.versionCode);
        assertFalse(complete.enabled);
        failing.set(true);
        refresh.invoke(module, true);
        awaitRefresh();
        assertTrue(calls.get() >= 2);
        assertSame(complete, field("config").get(module));
    }

    private void awaitRefresh() throws Exception {
        var refreshing = (java.util.concurrent.atomic.AtomicBoolean) field("refreshing").get(module);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (refreshing.get() && System.nanoTime() < deadline) Thread.sleep(10);
        assertFalse("Background configuration task did not complete", refreshing.get());
    }

    @Test public void optionalLoaderFailureAndKeyguardRefreshKeepStockFallback() throws Throwable {
        ready(Hook.TARGET);
        LottieAnimationView view = new LottieAnimationView(app);
        snapshot(1, true, null);
        view.fail = true;
        icon(view, "ic_fingerprint_dark.json");
        assertEquals("stock", view.shown);
        UdfpsKeyguardSensorWindow window = new UdfpsKeyguardSensorWindow();
        call(window, "setAnimationTypeIfNeeded", new Class<?>[]{});
        call(window, "setAnimationTypeIfNeeded", new Class<?>[]{});
        assertEquals(1, window.loads);
        snapshot(2, true, null);
        call(window, "setAnimationTypeIfNeeded", new Class<?>[]{});
        assertEquals(2, window.loads);
    }
}
