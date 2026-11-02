# Architecture

## Application and scope

Application ID and Java namespace: `io.github.aureliomo.prismlock`.
The Xposed entrypoint is registered in `app/src/main/assets/xposed_init`.
The sole supported hook target is `com.samsung.android.biometrics.app.setting`.

| Component | Responsibility |
| --- | --- |
| `MainActivity` | Preferences, imports, about dialog and preview |
| `CatalogScreen` | Native catalog, settings and theme presentation |
| `Catalog` | Asset lookup, variant selection and Lottie transforms |
| `CatalogScale` | Shared visible-footprint calculation |
| `ConfigProvider` | Read-only configuration/import access across processes |
| `Hook` | Samsung visual-resource interception |
| `AssetPolicy` | Exact supported asset-name allowlist |
| `Icons` | Legacy image-to-Lottie import support |

## Resource replacement

The hook replaces five Samsung `ic_fingerprint_*.json` icon paths. New fingerprint
entries declare `light` and `aod` variants in `catalog/index.json`; asset names
select black/light, white/AOD or colored/dark resources. User imports retain
precedence over the catalog. Existing unmodified catalog entries keep their art.

Effects are supplied when Samsung reads `user_fingerprint_touch_effect.json`.
The matching custom PNG fallback is suppressed when a catalog effect is active.
Three exact legacy Lottie asset paths remain fallbacks. Samsung still owns
playback, effect-window removal and authentication behavior.

The provider accepts only the module's own UID and Android system UID, serves
configuration through a fixed path, and permits only the two import slots.
It never accepts writes from another process. Disabled customization passes through
to stock resources. Configuration refreshes are throttled to three seconds.

## Sizing and duration

Compositions use a 288-unit canvas. A fingerprint's default visible footprint is
`size * 4`, with size clamped to 30-80. Shared alpha bounds and adjusted anchors
compensate for transparent margins without per-frame scale changes. Effects normally
use 132 units, independently of the fingerprint-size preference.

Aqua declares a 96-unit footprint and 24 frames at 30fps (0.8 seconds). Its final
four frames fade to zero opacity. The research preview used an 18-second clip;
that duration could outlast unlock. The firmware examined completes custom effects
on Lottie end/cancel, so a short finite composition replaces the long-running clip.
No new authentication or unlock hook is introduced by the fix. Hardware behavior
still needs verification.

Preferences live in credential-protected storage. Before first unlock, or if the
provider is unavailable, the hook may use stock assets. Package migration does not
copy old private data. Updated hook code needs normal LSPosed activation/restart;
ordinary catalog selections do not require an Apply action.
