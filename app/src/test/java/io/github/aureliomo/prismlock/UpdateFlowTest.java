package io.github.aureliomo.prismlock;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowApplicationPackageManager;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {31, 36})
public class UpdateFlowTest {
    private Context app;
    private ShadowApplicationPackageManager manager;

    @Before public void setup() throws Exception {
        UpdateChecker.source = () -> null;
        app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences(MainActivity.PREFS, 0).edit().clear().commit();
        app.getSharedPreferences(MainActivity.PREFS, 0).edit()
                .putLong("update_last_attempt", System.currentTimeMillis()).commit();
        manager = (ShadowApplicationPackageManager) Shadows.shadowOf(app.getPackageManager());
        manager.setCanRequestPackageInstalls(false);
        Field pending = MainActivity.class.getDeclaredField("pendingConfirmation");
        pending.setAccessible(true);
        pending.set(null, null);
    }

    @Test public void permissionReturnResumesExactlyOnceAndSurvivesNewContext() throws Exception {
        UpdateInstaller.waitingForPermission(app, true);
        assertFalse(UpdateInstaller.takePermissionResume(app));
        manager.setCanRequestPackageInstalls(true);
        Context recreated = app.createPackageContext(app.getPackageName(), 0);
        assertTrue(UpdateInstaller.takePermissionResume(recreated));
        assertFalse(UpdateInstaller.takePermissionResume(recreated));
    }

    @Test public void canceledPermissionDoesNotResumeWhenPermissionIsGrantedLater() {
        UpdateInstaller.waitingForPermission(app, true);
        UpdateInstaller.waitingForPermission(app, false);
        manager.setCanRequestPackageInstalls(true);
        assertFalse(UpdateInstaller.takePermissionResume(app));
    }

    @Test public void installerCanFillStatusAndConfirmationIntoCallback() throws Exception {
        Intent callback = new Intent(app, UpdateReceiver.class).setAction(UpdateReceiver.ACTION_STATUS);
        PendingIntent pending = PendingIntent.getBroadcast(app, 17, callback,
                UpdateInstaller.statusIntentFlags());
        Intent confirmation = new Intent("test.confirm.install");
        Intent result = new Intent()
                .putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_PENDING_USER_ACTION)
                .putExtra(Intent.EXTRA_INTENT, confirmation);
        pending.send(app, 0, result);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        java.util.List<Intent> broadcasts = Shadows.shadowOf((android.app.Application) app).getBroadcastIntents();
        Intent broadcast = broadcasts.get(broadcasts.size() - 1);
        assertEquals(PackageInstaller.STATUS_PENDING_USER_ACTION,
                broadcast.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE));
        // Dispatch the recorded broadcast explicitly: Robolectric records explicit
        // PendingIntent broadcasts without consistently instantiating manifest receivers.
        new UpdateReceiver().onReceive(app, broadcast);
        Field saved = MainActivity.class.getDeclaredField("pendingConfirmation");
        saved.setAccessible(true);
        Intent delivered = (Intent) saved.get(null);
        assertNotNull("System confirmation extras must survive PendingIntent delivery", delivered);
        assertEquals(confirmation.getAction(), delivered.getAction());

        PendingIntent old = PendingIntent.getBroadcast(app, 18, callback,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        old.send(app, 0, result);
        broadcasts = Shadows.shadowOf((android.app.Application) app).getBroadcastIntents();
        assertFalse("The old immutable callback drops the system's confirmation extras",
                broadcasts.get(broadcasts.size() - 1).hasExtra(Intent.EXTRA_INTENT));
    }

    @Test public void settingsRequestTargetsOnlyThisApplication() {
        Intent settings = UpdateInstaller.unknownSourcesIntent(app);
        assertEquals("package:" + app.getPackageName(), settings.getDataString());
        assertEquals(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, settings.getAction());
    }

    @Test public void foregroundActivityOpensInstallerConfirmation() {
        try (org.robolectric.android.controller.ActivityController<MainActivity> controller =
                Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            assertTrue(MainActivity.deliverInstallConfirmation(new Intent("test.confirm.foreground")));
            assertEquals("test.confirm.foreground", Shadows.shadowOf(activity).getNextStartedActivity().getAction());
        }
    }

    @Test public void backgroundConfirmationWaitsUntilAppReturns() {
        assertFalse(MainActivity.deliverInstallConfirmation(new Intent("test.confirm.return")));
        try (org.robolectric.android.controller.ActivityController<MainActivity> controller =
                Robolectric.buildActivity(MainActivity.class).setup()) {
            assertEquals("test.confirm.return",
                    Shadows.shadowOf(controller.get()).getNextStartedActivity().getAction());
        }
    }
}
