# Keqing extraction and visual catalog audit

## Scope and evidence

Input: operator-provided `KeqingFingerPrint.apk`, SHA-256
`6cd1a77d6f5826ee0c3f9886cd31edefc37b01947ee41b96555add85e6969087`.
The APK was inspected read-only with the APK Reverse skill, REA 6.8.0's
`inspect-artifact` CLI, ZIP/PNG decoding and Android build-tools `aapt2`.
REA MCP was not connected; its CLI produced a retained local Evidence bundle.
No REA/client registration or engine configuration was modified.

Observed: package `com.android.systemui.overlay.fingerprint.anim.keqing`,
`hasCode=false`, SystemUI overlay, 154 members, 147 PNGs, no DEX/native libraries.
Its images include 30- and 60-frame versions of the electro effect, a static
symbol, 25-frame fade-in and fade-out sequences, and chooser/preview artwork.
The 61-frame 60fps MP4 depicts the same electro design on an opaque background.
The PNG sequences preserve alpha and are the source used for PrismLock.
The OGG is inventoried but not imported into this visual-resource module.

## Imported artwork

- `icons/keqing`: original static symbol, including its original maximum alpha
  of 102/255. Explicit light/AOD variants retain identical alpha/geometry.
- `animations/keqing`: all 60 source PNG effect frames (60fps source), plus a generated
  transparent ending frame; the 120fps playback timebase retains a total duration
  of 61/60 seconds.
- `animations/keqing_appear` and `keqing_disappear`: all 25 source frames each,
  available as styles under the same Keqing family.
- No duplicate row for the 30fps rendition or MP4 preview. No overlay code,
  original APK, video/audio container or framework modification is bundled.

The overlay's main sequence is explicitly named 60fps and agrees with its video.
The fade rate integer is an OEM code rather than an explicit Hz declaration;
the Samsung port uses the equivalent of 25 source frames at 60fps (about 0.417s),
retimed onto the shared 120fps playback timebase.
That transition timing is a porting choice, not a proven original-OEM duration.
The source symbol's alpha never reaches the previous static-icon threshold 128,
so its bounds are measured at threshold 16 while its pixels remain unchanged.

## Comparison method

Every source PNG and catalog frame is decoded. Pixel comparison canonicalizes
invisible RGB, respects alpha, native bounds and layer opacity, and normalizes the
rendered footprint. Animation candidate comparisons sample 64 relative phases
across the entire sequence; the report also preserves native FPS/duration and
hashes for **every** decoded frame. Matching names or one similar thumbnail do
not establish duplication. Numeric scores rank candidates for visual inspection;
they do not automatically remove content.

The baseline has 36 icon rows and 21 effect rows. After importing Keqing and
consolidating repeated visual families, there are **30 icon families and 19 effect
families**, representing 60 selectable artwork styles plus 14 theme/AOD resources.
The current audit covers 701 source frames, plus the explicit theme/AOD files.
Different colors, materials or meaningful motion remain selectable styles.

## Reviewed consolidation decisions

| Visible family | Previous separate entries | Treatment |
| --- | --- | --- |
| Classic | White / Blue gradient | Same fingerprint ridge layout; retain white and blue gradient styles. |
| OriginOS icon | OriginOS / OriginOS blue | Same swirl geometry; preserve both colorways. |
| Star | Star / Star blue | Same dotted motif; preserve both color/opacity treatments. |
| Nothing OS | Nothing OS / ColorOS 17 Simple | Plain ring motif; preserve white and gray stroke styles. |
| OxygenOS icon | OxygenOS / ColorOS 17 Ring | Glowing ring motif; preserve colored and white glow treatments. |
| ColorOS 17 Swirl icon | ColorOS 17 Swirl / older ColorOS 2 | Same concentric orbit motif; retain the different original stroke treatment. |
| ColorOS 17 Bubble icon | ColorOS 17 Bubble / Outline | Four nested wobble rings; alias the old low-resolution Outline tag to the sharper source. |
| ColorOS 17 Ring effect | ColorOS 17 Ring / OxygenOS | Shared rotating-ring design; retain both complete timelines and their different native frame rates/durations as styles. |
| ColorOS 17 Fizz effect | ColorOS 17 Fizz / Dust | Shared blue particle-burst design; preserve both original timelines as styles. |
| OriginOS effect | OriginOS / OriginOS blue | Shared particle motif; preserve both color/motion variants. |
| Keqing effect | Electro / Appear / Disappear | One family with three distinct motion styles; no 30fps/video duplicates. |

MIUI and ColorOS 17 Halo remain separate: their ridge geometry differs. Genshin's
red fire and Keqing's violet electro effect remain separate despite the shared
game association. Dotted circles, spirals, glowing/solid rings and particle systems
are not merged merely because their names or broad shape categories overlap.

## Saved choices and interface

All 1.0.4 selection identifiers still load. Alternate colors/timelines keep their
own asset files and saved tags; the visible selection check resolves to the family.
The removed Outline file resolves through `aliases.icons.outline` to Bubble.
Light/AOD lookup also resolves aliases and nested styles. No reset or preference
migration discards selections. A native single-choice dialog chooses a family's
style; Cancel leaves the old choice intact. One-style rows still select directly.
Thumbnails, previews and Samsung runtime payloads use the selected style.

## Reproduction

Requires Pillow, NumPy and ffmpeg for the optional audit scripts; they are not app
dependencies. Core catalog validation remains standard-library-only.

```sh
python3 scripts/import_keqing.py /path/to/KeqingFingerPrint.apk
python3 scripts/consolidate_catalog.py
python3 scripts/normalize_catalog_timing.py
python3 scripts/check_catalog.py
python3 scripts/audit_keqing.py /path/to/KeqingFingerPrint.apk --output /path/to/local-audit
python3 scripts/audit_catalog.py --baseline-ref v1.0.4 --output /path/to/local-audit
```

The import and grouping commands are idempotent. Local audit outputs retain the
full source inventory, source-frame/video alignment, complete before/after frame
hashes, pair comparisons and contact sheets. Raw source APKs, local REA diagnostics,
private device logs and machine-specific paths are not committed.

Automated Android tests cover every old tag, alias equivalence, grouped selection,
light/AOD styles, canceled/saved style choices, Keqing opacity, frame counts and
transparent endings, alongside existing update, preview and API 102 hook checks.
Physical-device verification is reported separately from simulated Android tests.


## 120fps playback and process refinements

All 74 built-in compositions (including theme/AOD resources) declare a 120fps
playback timebase. `catalogSourceFps` and `catalogFrameStep` retain the source
cadence: 60fps images occupy two playback ticks and 30fps images four ticks.
Layer in/out points and static composition durations scale by the same factor.
The runtime opacity interpolation scales with that source-frame interval; PNGs,
frame counts, colors and total durations are unchanged. Previews convert playback
ticks back to source-frame positions while drawing on display-synchronized callbacks.
This is temporal blending, not newly captured/optical-flow-generated 120fps artwork.
Static icons retain static rendering rather than acquiring a redraw loop.

The app hints at up to 120Hz at the current resolution when the display supports
it. Actual refresh rate and rendering throughput still depend on the display,
power policy, Samsung's Lottie/window implementation and device load. Imported
arbitrary Lottie JSON is preserved; generated PNG wrappers use a 120fps timebase.

Selection now rebinds only affected visible rows. Concurrent thumbnail requests
share one decode; cached wallpaper returns immediately. IO/update/import/config
workers release their threads after 15 seconds idle (the preview worker retains
its existing 30-second timeout). Obsolete preview jobs recycle only their own
unpublished bitmaps; displayed images are not recycled underneath the render thread.
No additional Android service or process is introduced.
