# Architecture

## Application and scope

Application ID and Java namespace: `io.github.aureliomo.prismlock`.
The API 102 Xposed entrypoint is registered in
`app/src/main/resources/META-INF/xposed/java_init.list`; `module.prop` requires API 102
and `scope.list` restricts loading to the biometrics package. No legacy entrypoint or
`de.robv.android.xposed` dependency is packaged.
The sole supported hook target is `com.samsung.android.biometrics.app.setting`.

| Component | Responsibility |
| --- | --- |
| `MainActivity` | Screen lifecycle, dialogs and update coordination |
| `ArtworkImports` | Bounded background PNG decoding and atomic import file replacement |
| `ArtworkPreview` | Cancelable background preview loading and display-synchronized drawing |
| `Appearance` / `SettingsStore` | Shared theme resolution and monotonic selection revisions |
| `Motion` / `FrameTimeline` / `UiMotion` | Effect-frame interpolation, preview timing and short selection transitions |
| `CatalogScreen` | Recycled native catalog lists, settings and theme presentation |
| `ThumbnailLoader` / `WallpaperLoader` | Background pixel decoding with bounded/display-sized caching |
| `IconOverflow` | Reversible custom icon clipping changes without changing sensor geometry |
| `Catalog` / `CatalogIndex` | Immutable family/style/alias lookup, theme selection and Lottie transforms |
| `CatalogScale` | Shared visible-footprint calculation |
| `ConfigProvider` | Read-only configuration/import access across processes |
| `Hook` | Samsung visual-resource interception |
| `RuntimeConfig` | Immutable, atomically published hook configuration |
| `AssetPolicy` | Samsung asset-family matching |
| `Icons` | PNG image-to-Lottie import support |
| `UpdateChecker` | Stable GitHub release discovery and version comparison |
| `UpdateInstaller` | Download, digest/signature verification and hand-off to the system installer |
| `UpdateReceiver` | Result callback for the system installer session |
| `Versions` | Semantic-version comparison for updates |

## Device compatibility

PrismLock hooks `AssetManager.open()` inside the Samsung biometrics process. It does
not patch, replace or re-sign a system APK. The hook matches the fingerprint and touch
effect families Samsung reads at runtime, so the same APK works across One UI devices
instead of being tied to one model or firmware build. This is the same technique used
by RingPrint.

The hook replaces five core `ic_fingerprint_*.json` resources plus any additional
fingerprint asset names Samsung requests from the same family. New fingerprint
entries declare `light` and `aod` variants in `catalog/index.json`; asset names select
black/light, white/AOD or colored/dark resources. User imports retain precedence over
the catalog.

Effects are supplied when Samsung reads `user_fingerprint_touch_effect.json`.
The matching custom PNG fallback is suppressed when a catalog effect is active.
Additional `indisplay_fingerprint_touch_effect_*.json` resources are handled through
the same asset hook. Samsung still owns playback, effect-window removal and
authentication behavior.

The provider accepts only the module's own UID and Android system UID, serves
configuration through a fixed path, and permits only the two import slots.
It never accepts writes from another process. Disabled customization passes through
to stock resources. Configuration fallback checks are throttled to two seconds; provider change
notifications trigger immediate background refresh.

## Runtime configuration

On supported Samsung Lottie builds, the icon hook intercepts
`LottieAnimationView.setAnimation(String)` before the filename-only composition cache.
Custom JSON uses a cache key containing the module version, configuration revision
and original asset path. The icon drawable suppresses Samsung's stock color-filter
callbacks while custom artwork is selected. Stock icons and touch-effect drawables
retain normal filtering. The keyguard refreshes its current icon when the configuration
revision changes, even if Samsung's theme filename remains the same. Missing optional
Lottie APIs are logged and the original asset replacement remains available.

`Hook` uses the modern `XposedModule.onPackageReady()` lifecycle and interceptor
chains. Optional Samsung/Lottie hooks are installed independently. The application
attach hook supplies a context without legacy `AndroidAppHelper` or XposedHelpers.

`Hook.refreshConfig()` schedules binder IO and asset preparation on a dedicated
worker, builds a complete `RuntimeConfig` before publishing it through
a volatile field. Samsung often reads several assets in quick succession, so a failed
or partial refresh keeps the last known-good snapshot instead of exposing half-loaded
artwork. Imported PNG files are converted to Lottie once per configuration change,
not on every asset read. A completed revision refreshes tracked visible icons on
the main thread; hook callbacks themselves use the cached snapshot.

## Sizing and duration

Compositions use a 288-unit canvas. A fingerprint's default visible footprint is
`size * 4`, with size clamped to 30-80. Shared alpha bounds and adjusted anchors
compensate for transparent margins without per-frame scale changes. Effects
use 132 units, independently of the fingerprint-size preference.

Built-in effects blend adjacent image layers over a single source-frame interval.
There are at most two active image layers and no generated intermediate bitmaps.
Source PNGs and total durations are unchanged. The playback timebase is 120fps;
source cadence is retained in `catalogSourceFps`/`catalogFrameStep` and every
frame/keyframe interval is scaled accordingly. Static icons,
colors, alpha bounds, light/AOD variants and imported Lottie timelines are preserved.
Selection feedback uses a 180ms alpha/scale settling curve. Existing artwork,
including baked-in glows and blur, is retained.

The catalog index is parsed once per process. Native `ListView` adapters reuse
visible rows; each tab retains its list and scroll position. Thumbnails decode on an idle-expiring worker, coalesce concurrent requests, and
share a 2 MiB pixel cache with request identity checks for recycled rows.
Wallpapers decode off the main thread, are scaled to the display, and retain a
single cached bitmap; cache hits are delivered immediately. Selection rebinds only
the old/new visible rows. AppWorkers releases idle IO/update/import/config threads
after 15 seconds; the preview worker retains its 30-second timeout. Previews share one idle-expiring worker, cancel obsolete requests, reuse their
drawing objects and redraw on display frames only while visible. They release frame
references when detached. PNG imports are sampled before allocation, retain the
256-pixel output cap, and replace the previous file only after successful decoding
and writing. Failed catalog/configuration refreshes keep the previous complete
snapshot; disabling customization skips artwork loading. Android 8 uses compatible
font weights and accessibility calls.

Enlarged OxygenOS and other custom icons retain the original 288-unit artboard
and requested scale. On Lottie versions exposing composition clipping controls,
`IconOverflow` disables clipping for the custom drawable/view and its existing
ancestors. Ancestor flags are reference-counted and restored when returning to
stock. Neither layout dimensions, touch targets, sensor position nor authentication
are modified. Older Lottie implementations without these optional controls keep
their original clipping behavior. Previews reserve extra vertical space for enlarged
glows instead of clipping the same scaled image into a fixed-height card.

Aqua retains 24 source images at 30fps source cadence, mapped to 96 playback ticks
at 120fps (0.8 seconds). Its final
four frames fade to zero opacity. The short finite composition completes on Lottie
end/cancel, so it does not remain on the home screen after unlock.

Preferences live in credential-protected storage. Before first unlock, or if the
provider is unavailable, the hook uses stock assets. Package migration does not copy
old private data. Updated hook code needs normal LSPosed activation/restart; ordinary
catalog selections do not require an Apply action.

## Updates

`UpdateChecker` requests the latest stable GitHub release
(`/repos/AurelioMo/prismlock/releases/latest`) on every foreground entry.
Concurrent automatic/manual checks share a single request. Available updates are
offered on the foreground activity without opening Settings; an already offered
version is not repeated within that activity instance. Network failures permit an
immediate retry, with cached release metadata used as an offline fallback. Pre-releases
and drafts are ignored by GitHub's endpoint. The installed `versionName` is compared
with the release tag, and the result is cached in preferences.

`UpdateInstaller` downloads the APK over HTTPS, verifies the published SHA-256 digest
when present, checks the package name, checks that the version code is newer, and
requires the downloaded APK to be signed by the same certificate as the installed
app. Rooted devices install through `pm install -r`; otherwise the platform
`PackageInstaller` flow asks for confirmation. Updating keeps the package and its
settings because every official release uses the same signing key, and release builds
are signed with APK Signature Scheme v2 and v3.

If the install permission is missing, the foreground activity opens the app-specific
unknown-sources settings. A persisted pending-update filename allows resumption after
activity or process recreation. On return, permission is checked directly and the
cached APK is verified again before installation, without redownloading it. The
installer callback uses a mutable PendingIntent targeting the private UpdateReceiver
so Android can supply status and confirmation extras.


## Catalog families and compatibility identifiers

The index lists one visible entry per reviewed visual family. Optional `styles`
retain alternate colors or motion with their own tags/files/provenance. The
immutable CatalogIndex flattens those declarations once, keeping list rendering
free of repeated JSON parsing. `aliases` resolve obsolete duplicate identifiers
before file/theme lookup. Family checkmarks and selected-style thumbnails preserve
old saved choices, while a native single-choice dialog exposes alternate styles.
See [catalog audit](catalog-audit.md) for the reviewed grouping decisions.
