package io.github.aureliomo.prismlock;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

final class RuntimeConfigLoader {
    private static final String FILE = "content://io.github.aureliomo.prismlock.config/file/";

    private RuntimeConfigLoader() {}

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

}
