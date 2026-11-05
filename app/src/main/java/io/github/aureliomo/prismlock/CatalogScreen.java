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
import android.widget.BaseAdapter;
import java.util.ArrayList;
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
    private final FrameLayout pages;
    private final ListView[] collections = new ListView[2];
    private final ImageView importAction;
    private final LinearLayout[] tabs = new LinearLayout[3];
    private LinearLayout settings;
    private int selectedTab;
    private final int[] scrollPositions = new int[3];

    CatalogScreen(Activity activity, Runnable importFile, Runnable selectionChanged, int initialTab) {
        this.activity = activity;
        this.selectionChanged = selectionChanged;
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
                if (selectedTab == 2) scrollPositions[selectedTab] = scroll.getScrollY();
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
            ((ImageView) tabs[index].getChildAt(0)).setImageDrawable(
                    symbol(new String[]{"fingerprint", "animation", "settings"}[index], selected ? foreground : muted));
            ((TextView) tabs[index].getChildAt(1)).setTextColor(selected ? foreground : muted);
            tabs[index].setAlpha(selected ? 1 : 0.7f);
        }
        scroll.setVisibility(selectedTab == 2 ? View.VISIBLE : View.GONE);
        for (int i = 0; i < collections.length; i++) {
            if (collections[i] != null) collections[i].setVisibility(i == selectedTab ? View.VISIBLE : View.GONE);
        }
        if (selectedTab == 2) {
            if (settings.getParent() instanceof ViewGroup) ((ViewGroup) settings.getParent()).removeView(settings);
            content.addView(settings);
            scroll.post(() -> scroll.scrollTo(0, scrollPositions[2]));
        } else if (collections[selectedTab] == null) {
            collections[selectedTab] = createCollection(selectedTab);
            pages.addView(collections[selectedTab], new FrameLayout.LayoutParams(-1, -1));
        }
    }

    private ListView createCollection(int tab) {
        ListView list = new ListView(activity);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(android.R.color.transparent);
        list.setVerticalScrollBarEnabled(false);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, dp(16));
        list.setClipToOutline(true);
        list.setOutlineProvider(scroll.getOutlineProvider());
        LinearLayout header = column();
        TextView heading = text(tab == 0 ? "Fingerprints" : "Animations", 32, 700, foreground);
        heading(heading);
        heading.setPadding(dp(8), dp(8), dp(8), dp(16));
        header.addView(heading);
        TextView category = text(tab == 0 ? "Fingerprint collection" : "Touch effects", 13, 600, muted);
        category.setPadding(dp(12), 0, dp(12), dp(10));
        header.addView(category);
        if (tab == 1) {
            TextView warning = text("Turn off LockStar's fingerprint effect to avoid conflicts.", 13, 400, muted);
            warning.setPadding(dp(12), 0, dp(12), dp(12));
            header.addView(warning);
        }
        list.addHeaderView(header, null, false);
        CatalogAdapter adapter = new CatalogAdapter(tab);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            int index = position - list.getHeaderViewsCount();
            if (index < 0 || index >= adapter.getCount()) return;
            String tag = adapter.tags.get(index);
            SettingsStore.select(activity.getSharedPreferences(MainActivity.PREFS, 0), adapter.key, tag);
            adapter.selected = tag;
            adapter.notifyDataSetChanged();
            UiMotion.settle(((Row) view).thumbnail);
            selectionChanged.run();
        });
        return list;
    }

    private final class CatalogAdapter extends BaseAdapter {
        final List<String> tags = new ArrayList<>();
        final String kind, key;
        String selected;
        CatalogAdapter(int tab) {
            kind = tab == 0 ? "icons" : "animations";
            key = tab == 0 ? "icon" : "animation";
            selected = activity.getSharedPreferences(MainActivity.PREFS, 0).getString(key, "");
            tags.add("");
            try { tags.addAll(Catalog.tags(activity, kind)); }
            catch (Exception failure) {
                android.widget.Toast.makeText(activity, "Could not load the collection. Reopen the app to try again.",
                        android.widget.Toast.LENGTH_LONG).show();
            }
        }
        @Override public int getCount() { return tags.size(); }
        @Override public Object getItem(int position) { return tags.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public boolean hasStableIds() { return true; }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            Row row = recycled instanceof Row ? (Row) recycled : new Row();
            String tag = tags.get(position);
            boolean checked = tag.equals(selected);
            String name = tag.isEmpty() ? "Samsung default" : Catalog.title(tag);
            row.label.setText(name);
            row.check.setVisibility(checked ? View.VISIBLE : View.INVISIBLE);
            row.setSelected(checked);
            row.setContentDescription(name + (checked ? ", selected" : ""));
            float top = position == 0 ? dp(24) : 0, bottom = position == getCount() - 1 ? dp(24) : 0;
            GradientDrawable background = new GradientDrawable();
            background.setColor(panel);
            background.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});
            row.setBackground(background);
            if (tag.isEmpty()) {
                row.thumbnail.setTag(null);
                row.thumbnail.setImageDrawable(symbol("icons".equals(kind) ? "fingerprint" : "animation", foreground));
                row.thumbnail.setPadding(dp(9), dp(9), dp(9), dp(9));
            } else {
                row.thumbnail.setPadding(dp(4), dp(4), dp(4), dp(4));
                ThumbnailLoader.load(activity, "previews/" + kind + "_" + tag + ".png", row.thumbnail, dp(36));
            }
            return row;
        }
    }

    private final class Row extends LinearLayout {
        final ImageView thumbnail = new ImageView(activity);
        final TextView label = text("", 16, 600, foreground);
        final ImageView check = new ImageView(activity);
        Row() {
            super(activity);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(16), dp(12), dp(16), dp(12));
            setMinimumHeight(dp(76));
            setForeground(ripple(Color.TRANSPARENT, 0));
            thumbnail.setBackground(round(dark ? 0xFF3B4049 : 0xFFE6EBF2, 14));
            thumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);
            thumbnail.setClipToOutline(true);
            addView(thumbnail, new LinearLayout.LayoutParams(dp(44), dp(44)));
            label.setPadding(dp(14), 0, dp(4), 0);
            addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            check.setImageDrawable(symbol("check", accent));
            addView(check, new LinearLayout.LayoutParams(dp(19), dp(19)));
        }
    }

    private static Typeface weight(Typeface family, int weight) {
        return android.os.Build.VERSION.SDK_INT >= 28 ? Typeface.create(family, weight, false)
                : Typeface.create(family, weight >= 600 ? Typeface.BOLD : Typeface.NORMAL);
    }

    private static void heading(View view) {
        if (android.os.Build.VERSION.SDK_INT >= 28) view.setAccessibilityHeading(true);
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
