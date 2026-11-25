package io.github.aureliomo.prismlock;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.widget.ListView;
import android.widget.BaseAdapter;
import java.util.ArrayList;
import java.util.List;

final class CatalogCollection {
    private final Activity activity;
    private final CatalogScreen screen;
    private final Runnable selectionChanged;

    CatalogCollection(Activity activity, CatalogScreen screen, Runnable selectionChanged) {
        this.activity = activity;
        this.screen = screen;
        this.selectionChanged = selectionChanged;
    }

    ListView create(int tab, ViewOutlineProvider outline) {
        ListView list = new ListView(activity);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(android.R.color.transparent);
        list.setVerticalScrollBarEnabled(false);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, screen.dp(16));
        list.setClipToOutline(true);
        list.setOutlineProvider(outline);
        LinearLayout header = screen.column();
        TextView heading = screen.text(activity.getString(tab == 0 ? R.string.catalog_fingerprints : R.string.catalog_animations), 32, 700, screen.foreground);
        CatalogScreen.heading(heading);
        heading.setPadding(screen.dp(8), screen.dp(8), screen.dp(8), screen.dp(16));
        header.addView(heading);
        TextView category = screen.text(activity.getString(tab == 0 ? R.string.catalog_icon_collection : R.string.catalog_touch_effects), 13, 600, screen.muted);
        category.setPadding(screen.dp(12), 0, screen.dp(12), screen.dp(10));
        header.addView(category);
        if (tab == 1) {
            TextView warning = screen.text(activity.getString(R.string.catalog_lockstar_warning), 13, 400, screen.muted);
            warning.setPadding(screen.dp(12), 0, screen.dp(12), screen.dp(12));
            header.addView(warning);
        }
        list.addHeaderView(header, null, false);
        CatalogAdapter adapter = new CatalogAdapter(tab);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            int index = position - list.getHeaderViewsCount();
            if (index < 0 || index >= adapter.getCount()) return;
            String family = adapter.tags.get(index);
            List<Catalog.Style> styles = adapter.styles.get(family);
            if (styles == null || styles.size() < 2) {
                adapter.select(family, family, (Row) view, list);
                return;
            }
            String[] labels = new String[styles.size()];
            int selected = 0;
            for (int i = 0; i < styles.size(); i++) {
                labels[i] = styles.get(i).label;
                if (styles.get(i).tag.equals(adapter.selectedArtwork)) selected = i;
            }
            new android.app.AlertDialog.Builder(activity).setTitle(Catalog.title(family))
                    .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                        adapter.select(family, styles.get(which).tag, (Row) view, list);
                        dialog.dismiss();
                    }).setNegativeButton(R.string.action_cancel, null).show();
        });
        return list;
    }

    private final class CatalogAdapter extends BaseAdapter {
        final List<String> tags = new ArrayList<>();
        final String kind, key;
        String selectedFamily, selectedArtwork;
        final java.util.Map<String, List<Catalog.Style>> styles = new java.util.HashMap<>();
        CatalogAdapter(int tab) {
            kind = tab == 0 ? "icons" : "animations";
            key = tab == 0 ? "icon" : "animation";
            String selected = activity.getSharedPreferences(MainActivity.PREFS, 0).getString(key, "");
            tags.add("");
            try {
                tags.addAll(Catalog.tags(activity, kind));
                selectedFamily = Catalog.family(activity, kind, selected);
                selectedArtwork = Catalog.resolve(activity, kind, selected);
                for (String tag : tags) styles.put(tag, Catalog.styles(activity, kind, tag));
            }
            catch (Exception failure) {
                android.widget.Toast.makeText(activity, activity.getString(R.string.catalog_load_error),
                        android.widget.Toast.LENGTH_LONG).show();
            }
        }
        void select(String family, String tag, Row row, ListView list) {
            String previousFamily = selectedFamily;
            SettingsStore.select(activity.getSharedPreferences(MainActivity.PREFS, 0), key, tag);
            selectedArtwork = tag;
            selectedFamily = family;
            for (int child = 0; child < list.getChildCount(); child++) {
                int position = list.getFirstVisiblePosition() + child - list.getHeaderViewsCount();
                if (position < 0 || position >= tags.size()) continue;
                String visibleTag = tags.get(position);
                View visible = list.getChildAt(child);
                if (visible instanceof Row && (visibleTag.equals(previousFamily) || visibleTag.equals(family))) {
                    getView(position, visible, list);
                }
            }
            UiMotion.settle(row.thumbnail);
            selectionChanged.run();
        }
        @Override public int getCount() { return tags.size(); }
        @Override public Object getItem(int position) { return tags.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public boolean hasStableIds() { return true; }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            Row row = recycled instanceof Row ? (Row) recycled : new Row();
            String tag = tags.get(position);
            boolean checked = tag.equals(selectedFamily);
            String name = tag.isEmpty() ? activity.getString(R.string.catalog_samsung_default) : Catalog.title(tag);
            row.label.setText(name);
            List<Catalog.Style> choices = styles.get(tag);
            String detail = "";
            if (choices != null && choices.size() > 1) {
                detail = activity.getResources().getQuantityString(R.plurals.catalog_styles, choices.size(), choices.size());
                if (checked) for (Catalog.Style choice : choices) {
                    if (choice.tag.equals(selectedArtwork)) detail = choice.label;
                }
            }
            row.detail.setText(detail);
            row.detail.setVisibility(detail.isEmpty() ? View.INVISIBLE : View.VISIBLE);
            row.check.setVisibility(checked ? View.VISIBLE : View.INVISIBLE);
            row.setSelected(checked);
            row.setContentDescription(name + (detail.isEmpty() ? "" : ", " + detail) + (checked ? ", selected" : ""));
            float top = position == 0 ? screen.dp(24) : 0, bottom = position == getCount() - 1 ? screen.dp(24) : 0;
            GradientDrawable background = new GradientDrawable();
            background.setColor(screen.panel);
            background.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});
            row.setBackground(background);
            if (tag.isEmpty()) {
                row.thumbnail.setTag(null);
                row.thumbnail.setImageDrawable(screen.symbol("icons".equals(kind) ? "fingerprint" : "animation", screen.foreground));
                row.thumbnail.setPadding(screen.dp(9), screen.dp(9), screen.dp(9), screen.dp(9));
            } else {
                row.thumbnail.setPadding(screen.dp(4), screen.dp(4), screen.dp(4), screen.dp(4));
                ThumbnailLoader.load(activity, "previews/" + kind + "_" + (checked ? selectedArtwork : tag) + ".png", row.thumbnail, screen.dp(36));
            }
            return row;
        }
    }

    private final class Row extends LinearLayout {
        final ImageView thumbnail = new ImageView(activity);
        final TextView label = screen.text("", 16, 600, screen.foreground);
        final TextView detail = screen.text("", 13, 400, screen.muted);
        final ImageView check = new ImageView(activity);
        Row() {
            super(activity);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(screen.dp(16), screen.dp(12), screen.dp(16), screen.dp(12));
            setMinimumHeight(screen.dp(76));
            setForeground(screen.ripple(Color.TRANSPARENT, 0));
            thumbnail.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            check.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            thumbnail.setBackground(screen.round(screen.dark ? 0xFF3B4049 : 0xFFE6EBF2, 14));
            thumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);
            thumbnail.setClipToOutline(true);
            addView(thumbnail, new LinearLayout.LayoutParams(screen.dp(44), screen.dp(44)));
            LinearLayout labels = screen.column();
            labels.setMinimumHeight(screen.dp(44));
            labels.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            labels.setPadding(screen.dp(14), 0, screen.dp(4), 0);
            labels.addView(label);
            detail.setMinLines(1);
            detail.setPadding(0, screen.dp(4), 0, 0);
            labels.addView(detail);
            addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            check.setImageDrawable(screen.symbol("check", screen.accent));
            addView(check, new LinearLayout.LayoutParams(screen.dp(19), screen.dp(19)));
        }
    }

}
