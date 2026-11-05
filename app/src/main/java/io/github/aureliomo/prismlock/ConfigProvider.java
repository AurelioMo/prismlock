package io.github.aureliomo.prismlock;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.Binder;
import android.os.Process;

import java.io.File;
import java.io.FileNotFoundException;

/** Read-only configuration and image access for the separate biometrics process. */
public class ConfigProvider extends ContentProvider {
    private SharedPreferences.OnSharedPreferenceChangeListener changes;

    @Override public boolean onCreate() {
        changes = (preferences, key) -> {
            if ("ver".equals(key)) getContext().getContentResolver().notifyChange(
                    Uri.parse("content://io.github.aureliomo.prismlock.config/cfg"), null);
        };
        prefs().registerOnSharedPreferenceChangeListener(changes);
        return true;
    }

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences(MainActivity.PREFS, 0);
    }

    private void enforceReader() {
        int caller = Binder.getCallingUid();
        if (caller != Process.myUid() && caller != Process.SYSTEM_UID) {
            throw new SecurityException("Only the module and system biometrics may read this provider");
        }
    }

    @Override
    public Cursor query(Uri uri, String[] p, String s, String[] a, String o) {
        enforceReader();
        if (!"/cfg".equals(uri.getPath())) throw new IllegalArgumentException("Unknown path");
        SharedPreferences sp = prefs();
        MatrixCursor c = new MatrixCursor(new String[]{"size", "stroke10", "dark_kind", "light_kind", "ver", "enabled", "icon", "animation"});
        c.addRow(new Object[]{
                sp.getInt("size", Icons.DEF_SIZE),
                sp.getInt("stroke10", Icons.DEF_STROKE10),
                sp.getString("dark_kind", ""),
                sp.getString("light_kind", ""),
                sp.getLong("ver", 0L), sp.getBoolean("enabled", true) ? 1 : 0,
                sp.getString("icon", ""), sp.getString("animation", "")});
        return c;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        enforceReader();
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        String slot = uri.getLastPathSegment();
        if (!"dark".equals(slot) && !"light".equals(slot)) throw new FileNotFoundException();
        String kind = prefs().getString(slot + "_kind", "");
        File f = new File(getContext().getFilesDir(), "custom_" + slot + "." + kind);
        if (kind.isEmpty() || !f.exists()) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
