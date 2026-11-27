package io.github.aureliomo.prismlock;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.graphics.Outline;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import android.widget.ListView;

final class CatalogScreen {
    private static final int[] TAB_LABELS = {R.string.catalog_fingerprints, R.string.catalog_animations, R.string.catalog_settings};
    private static final String[] TAB_ICONS = {"fingerprint", "animation", "settings"};

    private final Activity activity;
    private final CatalogCollection collection;
    private final Typeface regular;
    private final Typeface medium;
    private final Typeface bold;
    final boolean dark;
    final int foreground;
    final int muted;
    final int accent;
    final int panel;
    private final LinearLayout content;
    private final ScrollView scroll;
    private final FrameLayout pages;
    private final ListView[] collections = new ListView[2];
    private final ImageView importAction;
    private final LinearLayout[] tabs = new LinearLayout[3];
    private int selectedTab;

    CatalogScreen(Activity activity, Runnable importFile, Runnable selectionChanged, int initialTab) {
        this.activity = activity;
        collection = new CatalogCollection(activity, this, selectionChanged);
        selectedTab = Math.max(0, Math.min(2, initialTab));
        dark = Appearance.isDark(activity);
        Typeface family = Typeface.createFromAsset(activity.getAssets(), "fonts/OneUISans-VF.ttf");
        regular = weight(family, 400);
        medium = weight(family, 600);
        bold = weight(family, 700);
        foreground = dark ? 0xFFF7F8FC : 0xFF191D25;
        muted = dark ? 0xFFABB3C3 : 0xFF566071;
        accent = dark ? 0xFFA8C8FF : 0xFF185FC1;
        panel = dark ? 0xE620252F : 0xEDFFFFFF;
        UiMotion.configureRefresh(activity);
        activity.getWindow().setStatusBarColor(Color.TRANSPARENT);
        activity.getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            activity.getWindow().setNavigationBarContrastEnforced(false);
            activity.getWindow().setStatusBarContrastEnforced(false);
        }
        int layoutFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        activity.getWindow().getDecorView().setSystemUiVisibility(layoutFlags | (dark ? 0
                : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));
        if (android.os.Build.VERSION.SDK_INT >= 30) activity.getWindow().setDecorFitsSystemWindows(false);
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            android.view.WindowManager.LayoutParams attributes = activity.getWindow().getAttributes();
            attributes.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            activity.getWindow().setAttributes(attributes);
        }

        FrameLayout frame = new FrameLayout(activity);
        ImageView wallpaper = new ImageView(activity);
        WallpaperLoader.load(activity, wallpaper, dark);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallpaper.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        frame.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout shell = column();
        frame.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        frame.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                        | android.view.WindowInsets.Type.displayCutout());
                shell.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            } else {
                shell.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });

        LinearLayout toolbar = new LinearLayout(activity);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(16), 0, dp(16), 0);
        importAction = action("add", activity.getString(R.string.catalog_import), importFile);
        toolbar.addView(importAction, new LinearLayout.LayoutParams(dp(48), dp(48)));
        toolbar.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1));
        ImageView applyAction = action("refresh", activity.getString(R.string.catalog_reload), activity::recreate);
        toolbar.addView(applyAction, new LinearLayout.LayoutParams(dp(48), dp(48)));
        shell.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(48)));

        scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout document = column();
        document.setPadding(0, 0, 0, dp(16));
        TextView title = text(activity.getString(R.string.catalog_settings), 32, 700, foreground);
        heading(title);
        title.setPadding(dp(8), dp(8), dp(8), dp(16));
        document.addView(title);
        content = column();
        document.addView(content);
        scroll.addView(document);
        scroll.setClipToOutline(true);
        scroll.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(22));
            }
        });
        LinearLayout.LayoutParams scrollLayout = new LinearLayout.LayoutParams(-1, 0, 1);
        scrollLayout.setMargins(dp(16), 0, dp(16), dp(12));
        pages = new FrameLayout(activity);
        pages.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        shell.addView(pages, scrollLayout);

        LinearLayout navigation = new LinearLayout(activity);
        navigation.setPadding(dp(12), dp(5), dp(12), dp(4));
        navigation.setBackground(round(dark ? 0xE6101920 : 0xE6F3F8FC, 26));
        for (int index = 0; index < tabs.length; index++) {
            final int destination = index;
            LinearLayout tab = column();
            tab.setGravity(Gravity.CENTER);
            String labelText = activity.getString(TAB_LABELS[index]);
            tab.setContentDescription(labelText);
            tab.setFocusable(true);
            tab.setBackground(ripple(Color.TRANSPARENT, 20));
            UiMotion.pressFeedback(tab);
            ImageView icon = new ImageView(activity);
            icon.setImageDrawable(symbol(TAB_ICONS[index], muted));
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            tab.addView(icon, new LinearLayout.LayoutParams(dp(25), dp(25)));
            TextView label = text(labelText, 11, 600, muted);
            label.setGravity(Gravity.CENTER);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            label.setPadding(0, dp(4), 0, 0);
            tab.addView(label);
            tab.setOnClickListener(view -> {
                if (selectedTab == destination) return;
                int previous = selectedTab;
                selectedTab = destination;
                render(previous);
            });
            tabs[index] = tab;
            navigation.addView(tab, new LinearLayout.LayoutParams(0, dp(58), 1));
        }
        LinearLayout.LayoutParams navigationLayout = new LinearLayout.LayoutParams(-1, -2);
        navigationLayout.setMargins(dp(12), 0, dp(12), dp(6));
        shell.addView(navigation, navigationLayout);
        activity.setContentView(frame);
        frame.requestApplyInsets();
    }

    void setSettings(LinearLayout settings) {
        content.addView(settings);
        render(-1);
    }

    int currentTab() {
        return selectedTab;
    }

    Typeface bodyTypeface() { return regular; }

    int previewSurface() { return dark ? 0xFF19222D : 0xFFEAF1F8; }

    Drawable dialogBackground() { return round(dark ? 0xFF20252F : 0xFFF7FAFD, 28); }

    LinearLayout column() {
        LinearLayout result = new LinearLayout(activity);
        result.setOrientation(LinearLayout.VERTICAL);
        return result;
    }

    TextView text(String value, int size, int weight, int color) {
        TextView result = new TextView(activity);
        result.setText(value);
        result.setTextSize(size);
        result.setTypeface(weight >= 700 ? bold : weight >= 600 ? medium : regular);
        result.setTextColor(color);
        result.setIncludeFontPadding(false);
        return result;
    }

    LinearLayout group(LinearLayout parent, String name) {
        TextView heading = text(name, 13, 600, muted);
        heading(heading);
        heading.setPadding(dp(12), dp(16), dp(12), dp(10));
        parent.addView(heading);
        LinearLayout group = column();
        group.setPadding(dp(18), dp(6), dp(18), dp(6));
        group.setBackground(round(panel, 24));
        group.setClipToOutline(true);
        parent.addView(group, new LinearLayout.LayoutParams(-1, -2));
        return group;
    }

    void settingAction(LinearLayout parent, int name, String subtitle, Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(62));
        row.setPadding(0, dp(14), 0, dp(14));
        row.setBackground(ripple(Color.TRANSPARENT, 12));
        addSettingIcon(row, name);
        LinearLayout labels = column();
        labels.addView(text(activity.getString(name), 16, 600, foreground));
        if (subtitle != null) {
            TextView description = text(subtitle, 13, 400, muted);
            description.setPadding(0, dp(5), dp(12), 0);
            labels.addView(description);
        }
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView chevron = new ImageView(activity);
        chevron.setImageDrawable(symbol("chevron", muted));
        row.addView(chevron, new LinearLayout.LayoutParams(dp(20), dp(20)));
        UiMotion.pressFeedback(row);
        row.setOnClickListener(view -> action.run());
        parent.addView(row);
    }

    TextView settingValue(LinearLayout parent, int name, String value, Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(62));
        row.setPadding(0, dp(14), 0, dp(14));
        row.setBackground(ripple(Color.TRANSPARENT, 12));
        addSettingIcon(row, name);
        row.addView(text(activity.getString(name), 16, 600, foreground), new LinearLayout.LayoutParams(0, -2, 1));
        TextView detail = text(value, 14, 400, muted);
        detail.setPadding(dp(8), 0, dp(8), 0);
        row.addView(detail);
        ImageView chevron = new ImageView(activity);
        chevron.setImageDrawable(symbol("chevron", muted));
        row.addView(chevron, new LinearLayout.LayoutParams(dp(18), dp(18)));
        UiMotion.pressFeedback(row);
        row.setOnClickListener(view -> action.run());
        parent.addView(row);
        return detail;
    }

    private void addSettingIcon(LinearLayout row, int name) {
        String glyph = "info";
        if (name == R.string.settings_theme) glyph = "theme";
        else if (name == R.string.settings_fingerprint_size) glyph = "size";
        else if (name == R.string.settings_custom_icons) glyph = "import";
        else if (name == R.string.settings_reset || name == R.string.settings_updates) glyph = "refresh";
        else if (name == R.string.settings_preview) glyph = "animation";
        ImageView icon = new ImageView(activity);
        icon.setImageDrawable(symbol(glyph, foreground));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(dp(22), dp(22));
        layout.rightMargin = dp(14);
        row.addView(icon, layout);
    }

    void settingToggle(LinearLayout parent, String label, String description, boolean checked,
            android.widget.CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(16), 0, dp(16));
        ImageView icon = new ImageView(activity);
        icon.setImageDrawable(symbol("fingerprint", foreground));
        LinearLayout.LayoutParams iconLayout = new LinearLayout.LayoutParams(dp(22), dp(22));
        iconLayout.rightMargin = dp(14);
        row.addView(icon, iconLayout);
        LinearLayout labels = column();
        labels.addView(text(label, 16, 600, foreground));
        TextView detail = text(description, 13, 400, muted);
        detail.setPadding(0, dp(5), dp(12), 0);
        labels.addView(detail);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        android.widget.Switch control = new android.widget.Switch(activity);
        control.setContentDescription(label);
        control.setChecked(checked);
        control.setOnCheckedChangeListener(listener);
        row.addView(control);
        parent.addView(row);
    }

    private void render(int previous) {
        importAction.setVisibility(selectedTab == 0 ? View.VISIBLE : View.INVISIBLE);
        for (int index = 0; index < tabs.length; index++) {
            boolean selected = index == selectedTab;
            if (previous >= 0 && index != previous && !selected) continue;
            tabs[index].setSelected(selected);
            int color = selected ? foreground : muted;
            ((ImageView) tabs[index].getChildAt(0)).getDrawable().setTint(color);
            ((TextView) tabs[index].getChildAt(1)).setTextColor(color);
            tabs[index].setAlpha(selected ? 1 : 0.7f);
        }
        if (previous >= 0) UiMotion.reset(previous == 2 ? scroll : collections[previous]);
        scroll.setVisibility(selectedTab == 2 ? View.VISIBLE : View.GONE);
        for (int i = 0; i < collections.length; i++) {
            if (collections[i] != null) collections[i].setVisibility(i == selectedTab ? View.VISIBLE : View.GONE);
        }
        if (selectedTab != 2 && collections[selectedTab] == null) {
            collections[selectedTab] = collection.create(selectedTab, scroll.getOutlineProvider());
            pages.addView(collections[selectedTab], new FrameLayout.LayoutParams(-1, -1));
        }
        if (previous >= 0) {
            View page = selectedTab == 2 ? scroll : collections[selectedTab];
            UiMotion.enterTab(page, Integer.signum(selectedTab - previous), dp(14));
        }
    }

    private static Typeface weight(Typeface family, int weight) {
        return android.os.Build.VERSION.SDK_INT >= 28 ? Typeface.create(family, weight, false)
                : Typeface.create(family, weight >= 600 ? Typeface.BOLD : Typeface.NORMAL);
    }

    static void heading(View view) {
        if (android.os.Build.VERSION.SDK_INT >= 28) view.setAccessibilityHeading(true);
    }

    private ImageView action(String symbol, String label, Runnable action) {
        ImageView result = new ImageView(activity);
        result.setImageDrawable(symbol(symbol, foreground));
        result.setPadding(dp(12), dp(12), dp(12), dp(12));
        result.setContentDescription(label);
        result.setBackground(ripple(Color.TRANSPARENT, 24));
        UiMotion.pressFeedback(result);
        result.setOnClickListener(view -> action.run());
        return result;
    }

    GradientDrawable round(int color, int radius) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(color);
        result.setCornerRadius(dp(radius));
        return result;
    }

    Drawable ripple(int color, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(dark ? 0x22FFFFFF : 0x18000000),
                color == Color.TRANSPARENT ? null : round(color, radius), round(Color.WHITE, radius));
    }

    int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    Drawable symbol(String name, int color) {
        int resource;
        switch (name) {
            case "add": resource = R.drawable.ic_catalog_add; break;
            case "refresh": resource = R.drawable.ic_catalog_apply; break;
            case "animation": resource = R.drawable.ic_catalog_animation; break;
            case "settings": resource = R.drawable.ic_catalog_settings; break;
            case "check": resource = R.drawable.ic_catalog_check; break;
            case "chevron": resource = R.drawable.ic_catalog_chevron; break;
            case "theme": resource = R.drawable.ic_catalog_theme; break;
            case "size": resource = R.drawable.ic_catalog_size; break;
            case "import": resource = R.drawable.ic_catalog_import; break;
            case "info": resource = R.drawable.ic_catalog_info; break;
            default: resource = R.drawable.ic_catalog_fingerprint; break;
        }
        Drawable drawable = activity.getDrawable(resource).mutate();
        drawable.setTint(color);
        return drawable;
    }
}
