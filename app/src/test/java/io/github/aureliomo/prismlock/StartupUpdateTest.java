package io.github.aureliomo.prismlock;

import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 31, 36})
public class StartupUpdateTest {
    private static final long ASYNC_TIMEOUT_SECONDS = 30;

    private static UpdateChecker.Release release() {
        return new UpdateChecker.Release("v99.0.0", "99.0.0", "Test", "Notes",
                "https://github.com/AurelioMo/prismlock/releases/download/v99.0.0/PrismLock-99.0.0.apk", "", 42);
    }
    private static void await(String description, java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(ASYNC_TIMEOUT_SECONDS);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            Thread.sleep(10);
        }
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertTrue(description, condition.getAsBoolean());
    }

    @Test public void openingAppOffersUpdateWithoutSettingsOrSixHourCacheGate() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences(MainActivity.PREFS, 0).edit().clear()
                .putLong("update_last_attempt", System.currentTimeMillis())
                .putString("update_prompted", "99.0.0").commit();
        AtomicInteger requests = new AtomicInteger();
        UpdateChecker.source = () -> { requests.incrementAndGet(); return release(); };
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup().visible()) {
            await("Update dialog did not appear", () -> ShadowAlertDialog.getLatestAlertDialog() != null);
            assertEquals(1, requests.get());
            assertEquals(0, controller.get().catalogScreen.currentTab());
            assertEquals("Update available", Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).getTitle());
            ShadowAlertDialog.getLatestAlertDialog().dismiss();
            controller.pause().stop();
            controller.start().resume();
            await("Foreground re-entry did not start a second request", () -> requests.get() == 2);
            await("Foreground re-entry did not dispatch its callback", () -> !isWaiting());
        }
    }

    private static boolean isWaiting() {
        try {
            var field = UpdateChecker.class.getDeclaredField("waiting");
            field.setAccessible(true);
            java.util.List<?> waiting = (java.util.List<?>) field.get(null);
            synchronized (waiting) {
                return !waiting.isEmpty();
            }
        } catch (Exception failure) { throw new AssertionError(failure); }
    }

    @Test public void simultaneousChecksShareOneRequestAndFailureCanRetryImmediately() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences(MainActivity.PREFS, 0).edit().clear().commit();
        AtomicInteger requests = new AtomicInteger(), results = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1), finish = new CountDownLatch(1);
        UpdateChecker.source = () -> {
            requests.incrementAndGet(); entered.countDown();
            if (!finish.await(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS)) throw new IllegalStateException("test timed out");
            throw new java.io.IOException("offline");
        };
        UpdateChecker.check(app, (release, error) -> { assertNotNull(error); results.incrementAndGet(); });
        try {
            assertTrue("Update source did not start", entered.await(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            UpdateChecker.check(app, (release, error) -> { assertNotNull(error); results.incrementAndGet(); });
        } finally {
            finish.countDown();
        }
        await("Both callbacks did not receive the shared failure", () -> results.get() == 2);
        assertEquals(1, requests.get());
        AtomicReference<UpdateChecker.Release> received = new AtomicReference<>();
        UpdateChecker.source = () -> { requests.incrementAndGet(); return release(); };
        UpdateChecker.check(app, (release, error) -> received.set(release));
        await("Retry did not return a release", () -> received.get() != null);
        assertEquals("99.0.0", received.get().version);
        assertEquals(2, requests.get());
    }
}
