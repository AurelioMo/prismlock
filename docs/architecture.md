# Architecture

## Application and scope

Application ID and Java namespace: `io.github.aureliomo.prismlock`.
The Xposed entrypoint is registered in `app/src/main/assets/xposed_init`.
The sole supported hook target is `com.samsung.android.biometrics.app.setting`.

| Component | Responsibility |
| --- | --- |
| `MainActivity` | Preferences, imports, about dialog, previews and update flow |
| `CatalogScreen` | Native catalog, settings and theme presentation |
| `Catalog` | Asset lookup, variant selection and Lottie transforms |
| `CatalogScale` | Shared visible-footprint calculation |
| `ConfigProvider` | Read-only configuration/import access across processes |
| `Hook` | Samsung visual-resource interception |
| `RuntimeConfig` | Immutable, atomically published hook configuration |
| `AssetPolicy` | Samsung asset-family matching |
| `Icons` | Legacy image-to-Lottie import support |
| `UpdateChecker` | Stable GitHub release discovery and version comparison |
| `UpdateInstaller` | Download, digest/signature verification and install |
| `UpdateReceiver` | Result callback for the system installer fallback |
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
to stock resources. Configuration refreshes are throttled to two seconds.

## Runtime configuration

On supported Samsung Lottie builds, the icon hook intercepts
`LottieAnimationView.setAnimation(String)` before the filename-only composition cache.
Custom JSON uses a cache key containing the module version, configuration revision
and original asset path. The icon drawable suppresses Samsung's stock color-filter
callbacks while custom artwork is selected. Stock icons and touch-effect drawables
retain normal filtering. The keyguard refreshes its current icon when the configuration
revision changes, even if Samsung's theme filename remains the same. Missing optional
Lottie APIs are logged and the original asset replacement remains available.

`Hook.refreshConfig()` builds a complete `RuntimeConfig` before publishing it through
a volatile field. Samsung often reads several assets in quick succession, so a failed
or partial refresh keeps the last known-good snapshot instead of exposing half-loaded
artwork. Imported PNG files are converted to Lottie once per configuration change,
not on every asset read.

## Sizing and duration

Compositions use a 288-unit canvas. A fingerprint's default visible footprint is
`size * 4`, with size clamped to 30-80. Shared alpha bounds and adjusted anchors
compensate for transparent margins without per-frame scale changes. Effects normally
use 132 units, independently of the fingerprint-size preference.

Aqua declares a 96-unit footprint and 24 frames at 30fps (0.8 seconds). Its final
four frames fade to zero opacity. The short finite composition completes on Lottie
end/cancel, so it does not remain on the home screen after unlock.

Preferences live in credential-protected storage. Before first unlock, or if the
provider is unavailable, the hook uses stock assets. Package migration does not copy
old private data. Updated hook code needs normal LSPosed activation/restart; ordinary
catalog selections do not require an Apply action.

## Updates

`UpdateChecker` requests the latest stable GitHub release
(`/repos/AurelioMo/prismlock/releases/latest`) at most once every six hours. Pre-releases
and drafts are ignored by GitHub's endpoint. The installed `versionName` is compared
with the release tag, and the result is cached in preferences.

`UpdateInstaller` downloads the APK over HTTPS, verifies the published SHA-256 digest
when present, checks the package name, checks that the version code is newer, and
requires the downloaded APK to be signed by the same certificate as the installed
app. Rooted devices install through `pm install -r`; otherwise the platform
`PackageInstaller` flow asks for confirmation. Updating keeps the package and its
settings because every official release uses the same signing key.
