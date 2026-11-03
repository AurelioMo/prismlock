package io.github.aureliomo.prismlock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

/** Receives the result of the system PackageInstaller fallback. */
public final class UpdateReceiver extends BroadcastReceiver {
    static final String ACTION_STATUS = "io.github.aureliomo.prismlock.action.INSTALL_STATUS";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_STATUS.equals(intent.getAction())) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirmation == null) return;
            confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(confirmation);
            } catch (Throwable ignored) {
                Toast.makeText(context, "Open PrismLock and tap Update again to finish.",
                        Toast.LENGTH_LONG).show();
            }
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            Toast.makeText(context,
                    "PrismLock updated. Reopen the app or reboot to load the new module code.",
                    Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(context,
                    "Install did not finish" + (detail == null || detail.isEmpty() ? "." : ": " + detail),
                    Toast.LENGTH_LONG).show();
        }
    }
}
