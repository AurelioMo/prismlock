package io.github.aureliomo.prismlock;

import android.content.Context;
import android.content.res.Configuration;

final class Appearance {
    private Appearance() {}

    static boolean isDark(Context context) {
        String theme = context.getSharedPreferences(MainActivity.PREFS, 0).getString("theme", "system");
        return "dark".equals(theme) || ("system".equals(theme)
                && (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES);
    }
}
