package io.github.aureliomo.prismlock;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
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

import java.io.InputStream;
import java.util.List;

final class CatalogScreen {
    private final Activity activity;
    private final Runnable selectionChanged;
    private final Typeface regular;
    private final Typeface medium;
    private final Typeface bold;
    private final boolean dark;
    final int foreground;
    final int muted;
    final int accent;
    private final int panel;
    private final LinearLayout content;
    private final TextView title;
    private final ScrollView scroll;
    private final ImageView importAction;
    private final LinearLayout[] tabs = new LinearLayout[3];
    private LinearLayout settings;
    private int selectedTab;
    private final int[] scrollPositions = new int[3];

    CatalogScreen(Activity activity, Runnable importFile, Runnable selectionChanged, int initialTab) {
        this.activity = activity;
        this.selectionChanged = selectionChanged;
        selectedTab = Math.max(0, Math.min(2, initialTab));
        String theme = activity.getSharedPreferences(MainActivity.PREFS, 0).getString("theme", "system");
        dark = "dark".equals(theme) || ("system".equals(theme)
                && (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES);
        Typeface family = Typeface.createFromAsset(activity.getAssets(), "fonts/OneUISans-VF.ttf");
        regular = Typeface.create(family, 400, false);
        medium = Typeface.create(family, 600, false);
        bold = Typeface.create(family, 700, false);
        foreground = dark ? 0xFFF7F8FC : 0xFF191D25;
        muted = dark ? 0xFFABB3C3 : 0xFF566071;
        accent = dark ? 0xFFA8C8FF : 0xFF185FC1;
        panel = dark ? 0xE620252F : 0xEDFFFFFF;
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
        wallpaper.setImageResource(dark ? R.drawable.catalog_background_dark : R.drawable.catalog_background_light);
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
        importAction = action("add", "Import fingerprint", importFile);
        toolbar.addView(importAction, new LinearLayout.LayoutParams(dp(48), dp(48)));
        toolbar.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1));
        ImageView applyAction = action("refresh", "Reload collection", activity::recreate);
        toolbar.addView(applyAction, new LinearLayout.LayoutParams(dp(48), dp(48)));
        shell.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(48)));

        scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout document = column();
        document.setPadding(0, 0, 0, dp(16));
        title = text("", 32, 700, foreground);
        title.setAccessibilityHeading(true);
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
        shell.addView(scroll, scrollLayout);

        LinearLayout navigation = new LinearLayout(activity);
        navigation.setPadding(dp(12), dp(5), dp(12), dp(4));
        navigation.setBackground(round(dark ? 0xE6101920 : 0xE6F3F8FC, 26));
        String[] names = {"Fingerprints", "Animations", "Settings"};
        String[] symbols = {"fingerprint", "animation", "settings"};
        for (int index = 0; index < tabs.length; index++) {
            final int destination = index;
            LinearLayout tab = column();
            tab.setGravity(Gravity.CENTER);
            tab.setContentDescription(names[index]);
            tab.setBackground(ripple(Color.TRANSPARENT, 20));
            ImageView icon = new ImageView(activity);
            icon.setImageDrawable(symbol(symbols[index], muted));
            tab.addView(icon, new LinearLayout.LayoutParams(dp(25), dp(25)));
            TextView label = text(names[index], 11, 600, muted);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, dp(4), 0, 0);
            tab.addView(label);
            tab.setOnClickListener(view -> {
                if (selectedTab == destination) return;
                scrollPositions[selectedTab] = scroll.getScrollY();
                selectedTab = destination;
                render();
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
        this.settings = settings;
        render();
    }

    int currentTab() {
        return selectedTab;
    }

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
        heading.setAccessibilityHeading(true);
        heading.setPadding(dp(12), dp(16), dp(12), dp(10));
        parent.addView(heading);
        LinearLayout group = column();
        group.setPadding(dp(18), dp(6), dp(18), dp(6));
        group.setBackground(round(panel, 24));
        group.setClipToOutline(true);
        parent.addView(group, new LinearLayout.LayoutParams(-1, -2));
        return group;
    }

    void settingAction(LinearLayout parent, String name, String subtitle, Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(62));
        row.setPadding(0, dp(14), 0, dp(14));
        row.setBackground(ripple(Color.TRANSPARENT, 12));
        addSettingIcon(row, name);
        LinearLayout labels = column();
        labels.addView(text(name, 16, 600, foreground));
        if (subtitle != null) {
            TextView description = text(subtitle, 13, 400, muted);
            description.setPadding(0, dp(5), dp(12), 0);
            labels.addView(description);
        }
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView chevron = new ImageView(activity);
        chevron.setImageDrawable(symbol("chevron", muted));
        row.addView(chevron, new LinearLayout.LayoutParams(dp(20), dp(20)));
        row.setOnClickListener(view -> action.run());
        parent.addView(row);
    }

    TextView settingValue(LinearLayout parent, String name, String value, Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(62));
        row.setPadding(0, dp(14), 0, dp(14));
        row.setBackground(ripple(Color.TRANSPARENT, 12));
        addSettingIcon(row, name);
        row.addView(text(name, 16, 600, foreground), new LinearLayout.LayoutParams(0, -2, 1));
        TextView detail = text(value, 14, 400, muted);
        detail.setPadding(dp(8), 0, dp(8), 0);
        row.addView(detail);
        ImageView chevron = new ImageView(activity);
        chevron.setImageDrawable(symbol("chevron", muted));
        row.addView(chevron, new LinearLayout.LayoutParams(dp(18), dp(18)));
        row.setOnClickListener(view -> action.run());
        parent.addView(row);
        return detail;
    }

    private void addSettingIcon(LinearLayout row, String name) {
        String glyph;
        switch (name) {
            case "Theme": glyph = "theme"; break;
            case "Fingerprint size": glyph = "size"; break;
            case "Custom icons": glyph = "import"; break;
            case "Reset customization": glyph = "refresh"; break;
            case "Preview": glyph = "animation"; break;
            case "Updates": glyph = "refresh"; break;
            default: glyph = "info"; break;
        }
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

    private void render() {
        content.removeAllViews();
        title.setText(new String[]{"Fingerprints", "Animations", "Settings"}[selectedTab]);
        importAction.setVisibility(selectedTab == 0 ? View.VISIBLE : View.INVISIBLE);
        for (int index = 0; index < tabs.length; index++) {
            boolean selected = index == selectedTab;
            tabs[index].setSelected(selected);
            tabs[index].setAccessibilityHeading(false);
            ((ImageView) tabs[index].getChildAt(0)).setImageDrawable(
                    symbol(new String[]{"fingerprint", "animation", "settings"}[index], selected ? foreground : muted));
            ((TextView) tabs[index].getChildAt(1)).setTextColor(selected ? foreground : muted);
            tabs[index].setAlpha(selected ? 1 : 0.7f);
        }
        if (selectedTab == 2) {
            if (settings.getParent() instanceof ViewGroup) ((ViewGroup) settings.getParent()).removeView(settings);
            content.addView(settings);
        } else {
            renderCatalog();
        }
        scroll.post(() -> scroll.scrollTo(0, scrollPositions[selectedTab]));
    }

    private void renderCatalog() {
        String kind = selectedTab == 0 ? "icons" : "animations";
        String key = selectedTab == 0 ? "icon" : "animation";
        TextView category = text(selectedTab == 0 ? "Fingerprint collection" : "Touch effects", 13, 600, muted);
        category.setPadding(dp(12), 0, dp(12), dp(10));
        content.addView(category);
        if (selectedTab == 1) {
            TextView warning = text("Turn off LockStar's fingerprint effect to avoid conflicts.", 13, 400, muted);
            warning.setPadding(dp(12), 0, dp(12), dp(12));
            content.addView(warning);
        }
        LinearLayout list = column();
        list.setBackground(round(panel, 24));
        list.setClipToOutline(true);
        content.addView(list);
        try {
            List<String> tags = Catalog.tags(activity, kind);
            tags.add(0, "");
            String selected = activity.getSharedPreferences(MainActivity.PREFS, 0).getString(key, "");
            for (String tag : tags) {
                boolean checked = tag.equals(selected);
                LinearLayout row = new LinearLayout(activity);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(16), dp(12), dp(16), dp(12));
                row.setMinimumHeight(dp(76));
                row.setBackground(ripple(Color.TRANSPARENT, 0));
                ImageView thumbnail = new ImageView(activity);
                thumbnail.setBackground(round(dark ? 0xFF3B4049 : 0xFFE6EBF2, 14));
                thumbnail.setPadding(dp(4), dp(4), dp(4), dp(4));
                thumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);
                if (tag.isEmpty()) {
                    thumbnail.setImageDrawable(symbol(selectedTab == 0 ? "fingerprint" : "animation", foreground));
                    thumbnail.setPadding(dp(9), dp(9), dp(9), dp(9));
                } else {
                    try (InputStream input = activity.getAssets().open("previews/" + kind + "_" + tag + ".png")) {
                        thumbnail.setImageBitmap(BitmapFactory.decodeStream(input));
                    }
                }
                thumbnail.setClipToOutline(true);
                row.addView(thumbnail, new LinearLayout.LayoutParams(dp(44), dp(44)));
                String name = tag.isEmpty() ? "Samsung default" : Catalog.title(tag);
                TextView label = text(name,
                        16, 600, foreground);
                label.setPadding(dp(14), 0, dp(4), 0);
                row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
                if (checked) {
                    ImageView check = new ImageView(activity);
                    check.setImageDrawable(symbol("check", accent));
                    row.addView(check, new LinearLayout.LayoutParams(dp(19), dp(19)));
                }
                row.setSelected(checked);
                row.setContentDescription(name + (checked ? ", selected" : ""));
                row.setOnClickListener(view -> {
                    activity.getSharedPreferences(MainActivity.PREFS, 0).edit().putString(key, tag)
                            .putLong("ver", Math.max(System.currentTimeMillis(),
                                    activity.getSharedPreferences(MainActivity.PREFS, 0).getLong("ver", 0) + 1)).commit();
                    selectionChanged.run();
                    scrollPositions[selectedTab] = scroll.getScrollY();
                    render();
                });
                list.addView(row);
            }
        } catch (Exception exception) {
            TextView error = text("Could not load the collection. Reopen the app to try again.", 15, 400, foreground);
            error.setPadding(dp(20), dp(20), dp(20), dp(20));
            list.addView(error);
        }
    }

    private ImageView action(String symbol, String label, Runnable action) {
        ImageView result = new ImageView(activity);
        result.setImageDrawable(symbol(symbol, foreground));
        result.setPadding(dp(12), dp(12), dp(12), dp(12));
        result.setContentDescription(label);
        result.setBackground(ripple(Color.TRANSPARENT, 24));
        result.setOnClickListener(view -> action.run());
        return result;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(color);
        result.setCornerRadius(dp(radius));
        return result;
    }

    private Drawable ripple(int color, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(dark ? 0x22FFFFFF : 0x18000000),
                round(color, radius), round(Color.WHITE, radius));
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private Drawable symbol(String name, int color) {
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
