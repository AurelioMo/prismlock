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
    private static UpdateChecker.Release release() {
        return new UpdateChecker.Release("v99.0.0", "99.0.0", "Test", "Notes",
                "https://github.com/AurelioMo/prismlock/releases/download/v99.0.0/PrismLock-99.0.0.apk", "", 42);
    }
    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            Thread.sleep(10);
        }
        assertTrue("Update callback did not complete", condition.getAsBoolean());
    }

    @Test public void openingAppOffersUpdateWithoutSettingsOrSixHourCacheGate() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences(MainActivity.PREFS, 0).edit().clear()
                .putLong("update_last_attempt", System.currentTimeMillis())
                .putString("update_prompted", "99.0.0").commit();
        AtomicInteger requests = new AtomicInteger();
        UpdateChecker.source = () -> { requests.incrementAndGet(); return release(); };
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup().visible()) {
            await(() -> ShadowAlertDialog.getLatestAlertDialog() != null);
            assertEquals(1, requests.get());
            assertEquals(0, controller.get().catalogScreen.currentTab());
            assertEquals("Update available", Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).getTitle());
            ShadowAlertDialog.getLatestAlertDialog().dismiss();
            controller.pause().stop();
            controller.start().resume();
            await(() -> requests.get() == 2);
            await(() -> !isWaiting());
        }
    }

    private static boolean isWaiting() {
        try {
            var field = UpdateChecker.class.getDeclaredField("waiting"); field.setAccessible(true);
            return !((java.util.List<?>) field.get(null)).isEmpty();
        } catch (Exception failure) { throw new AssertionError(failure); }
    }

    @Test public void simultaneousChecksShareOneRequestAndFailureCanRetryImmediately() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences(MainActivity.PREFS, 0).edit().clear().commit();
        AtomicInteger requests = new AtomicInteger(), results = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1), finish = new CountDownLatch(1);
        UpdateChecker.source = () -> {
            requests.incrementAndGet(); entered.countDown();
            if (!finish.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timed out");
            throw new java.io.IOException("offline");
        };
        UpdateChecker.check(app, (release, error) -> { assertNotNull(error); results.incrementAndGet(); });
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        UpdateChecker.check(app, (release, error) -> { assertNotNull(error); results.incrementAndGet(); });
        finish.countDown();
        await(() -> results.get() == 2);
        assertEquals(1, requests.get());
        AtomicReference<UpdateChecker.Release> received = new AtomicReference<>();
        UpdateChecker.source = () -> { requests.incrementAndGet(); return release(); };
        UpdateChecker.check(app, (release, error) -> received.set(release));
        await(() -> received.get() != null);
        assertEquals("99.0.0", received.get().version);
        assertEquals(2, requests.get());
    }
}
