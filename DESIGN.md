# PrismLock design system

## 1. Atmosphere and identity
Existing native One UI catalog: large headings, translucent rounded lists, the current
light/dark gradient wallpaper and OneUISans. Preserve this presentation.

## 2. Color
Use CatalogScreen tokens: foreground light #191D25 / dark #F7F8FC; muted light
#566071 / dark #ABB3C3; accent light #185FC1 / dark #A8C8FF; panel light
#EDFFFFFF / dark #E620252F. Existing preview surfaces and wallpaper remain unchanged.

## 3. Typography
OneUISans-VF, weights 400/600/700. Titles 32sp/700; catalog row 16sp/600;
secondary labels 13sp/400; navigation 11sp/600. Native dialog typography follows
the current application theme, as it already does for the Theme selector.

## 4. Spacing and layout
Keep existing toolbar 48dp, navigation 58dp, collection row minimum 76dp,
44dp thumbnails, 16dp horizontal row padding, 12dp vertical padding, 24dp panel
radii and current system insets. Catalog ListView owns scrolling. Secondary
variant labels use the existing 4dp label gap. The text column has a 44dp minimum
height matching the thumbnail; an invisible one-line variant slot keeps title
baselines aligned across one-style and multi-style rows, with flexible height for
larger system fonts.

## 5. Components and states
- Catalog row: thumbnail, label column, selection checkmark; reusable native list row.
  Selected families show the current style thumbnail/label. Unselected families show
  the default thumbnail and a style-count hint when there are alternate choices.
- Variant picker: reuse AlertDialog single-choice list already used for Theme.
  Tap a family with variants to choose; selecting commits the tag and dismisses;
  Cancel preserves the current choice. One-style families select immediately.
- Preview/settings/navigation: preserve existing components and behavior.

## 6. Motion
Keep the existing 180ms alpha/scale selection settling. Built-in artwork uses a
120fps timebase with interpolated source frames and unchanged duration. The app
requests up to 120Hz at the current resolution when supported; display/power policy
remains in charge. Variant dialogs use platform motion and respect system animation settings.
No layout animation or additional per-frame image decoding.

## 7. Accessibility and adaptation
Preserve content descriptions, minimum touch areas, native list/dialog keyboard and
screen-reader behavior, weighted label width and system font scaling. Describe
variant count/current variant alongside selection state. Wrap secondary labels.

## 8. Validation and accepted debt
Existing 1.0.4 presentation is the reference. Validate recycled rows, grouped selection,
old tags, dialogs, canceled choices, themes and preview parity in Android tests.
Physical device checks depend on an authorized connected device; do not substitute
web Lighthouse scores for native Android measurements.
