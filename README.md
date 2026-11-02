<div align="center">
  <img src="docs/branding/logo-transparent.png" width="150" alt="PrismLock logo">
  <h1>PrismLock</h1>
  <p>Fingerprint icons and touch animations for Samsung.</p>
</div>

[![Release](https://img.shields.io/github/v/release/AurelioMo/prismlock?include_prereleases&label=Download&color=blue&logo=github)](https://github.com/AurelioMo/prismlock/releases)
[![LSPosed](https://img.shields.io/badge/LSPosed-Module-orange)](https://github.com/LSPosed/LSPosed)
[![Build](https://github.com/AurelioMo/prismlock/actions/workflows/build.yml/badge.svg)](https://github.com/AurelioMo/prismlock/actions/workflows/build.yml)
[![Source](https://img.shields.io/badge/Source-Available-222222?logo=github)](https://github.com/AurelioMo/prismlock/tree/main/app)

PrismLock is an Xposed / LSPosed module for customizing fingerprint icons and touch
effects on Samsung One UI. Browse the built-in catalog, import your own artwork,
and select independent icons and animations. Changes save automatically.

## Download

**[Download the APK from Releases](https://github.com/AurelioMo/prismlock/releases).**
Open the newest release, expand **Assets**, and download `PrismLock-*.apk`.
`SHA256SUMS` verifies the download. The source ZIP/TAR files are not installable apps.
Current version: **0.2.0-preview**. This is an experimental preview, not a stable release.

## Compatibility

| Component | Requirements |
| --- | --- |
| Module framework | LSPosed with legacy Xposed API 82 support |
| Firmware | Compatible Samsung One UI with in-display fingerprint UI |
| Android | Minimum API 26 to install; firmware compatibility is not guaranteed |
| Module scope | `com.samsung.android.biometrics.app.setting` only |
| Development device | Galaxy S23 / SM-S911B, custom One UI 9 / Android 17 ROM |

Other devices and firmware are unverified. A low minimum SDK does not establish
support for every Android version. Root and a recovery path are required.

## Installation

1. Download and install the APK from **[Releases](https://github.com/AurelioMo/prismlock/releases)**.
2. Activate **PrismLock** in LSPosed, selecting only the scope listed above.
3. Follow LSPosed's activation/restart requirements. The app does not reboot or
   restart Samsung system processes automatically.
4. Open PrismLock and select a fingerprint and animation. There is no Apply step.
5. Turn off LockStar's fingerprint effect to avoid conflicts.

**Coming from the research app?** Disable `com.fpring.samsung` before enabling
PrismLock (`io.github.aureliomo.prismlock`). They are different packages; preferences
do not migrate automatically. Never enable both modules or RingPrint together.

**Coming from a debug build of PrismLock?** The public APK uses a different signing
key. Disable the module and uninstall the debug build before installing the release
APK; this removes its app data. See [signing and updates](docs/signing.md).

## Features

- Built-in library of **28 fingerprint designs** and **18 touch animations**.
- Independent fingerprint and effect selection with automatic saving.
- Color, black/light-mode and white/AOD variants for Prism, Vortex, Orbit and Neon.
- PNG and Lottie imports for light and dark/AOD modes.
- Separate fingerprint and animation previews, with visible-bound size normalization.
- English interface with system, light and dark appearance.
- Smaller **0.8-second Aqua effect**, with a transparent final frame.
- Customization switch to pass through to Samsung's default visual resources.

The module changes visual resources, not authentication results, enrollment,
illumination masks, brightness control or sensor behavior. Aqua's newest fix and
AOD behavior still need physical-device acceptance; a successful build is not proof.

## Screenshots

| Fingerprints | Animations | Settings |
| :---: | :---: | :---: |
| <img src="docs/screenshots/fingerprints.png" width="260" alt="Fingerprint catalog"> | <img src="docs/screenshots/animations.png" width="260" alt="Touch animation catalog"> | <img src="docs/screenshots/settings.png" width="260" alt="PrismLock settings"> |

## Source and build

Unlike the referenced Xiaomi distribution page, this repository includes the
application source. Source availability does not override third-party license terms.

Requires JDK 17, Android SDK platform 36 and Python 3.10+ for catalog validation.
Gradle 8.13 and its distribution checksum are pinned in the checked-in wrapper.

```sh
export ANDROID_HOME=/path/to/your/android-sdk
python3 scripts/check_catalog.py
./gradlew --no-daemon assembleDebug
```

On Windows use `gradlew.bat`. The debug APK is in
`app/build/outputs/apk/debug/app-debug.apk`. For signed distribution builds, follow
[release signing](docs/signing.md); never commit credentials or keystores.

```text
app/                 Android application, hooks and runtime artwork
docs/                Architecture, testing, branding and provenance
gradle/wrapper/      Pinned build wrapper
scripts/             Portable catalog validation and icon generation
.github/             CI and issue/PR templates
```

Research dumps, firmware APKs, local SDKs, caches, old builds and signing keys are
not included. See [architecture](docs/architecture.md), [testing](docs/testing.md),
[contributing](CONTRIBUTING.md), [security](SECURITY.md) and [changelog](CHANGELOG.md).

## Contacts and credits

Developer: **[AurelioMo](https://github.com/AurelioMo)**.
Report bugs and suggestions through **[Issues](https://github.com/AurelioMo/prismlock/issues)**.

Original catalog reference and artwork: **custombeta / Fingerprint Catalog**.
Samsung integration research and inherited implementation: **ELPEDR / RingPrint**.
Samsung resources and user-supplied artwork retain their respective ownership.
PrismLock is not affiliated with Samsung, Xiaomi, Good Lock or LSPosed.

## Third-party permissions

Public source and preview availability do **not** establish permission to reuse
third-party content. The upstream custombeta license restricts derivative
publication without written permission; that permission has not been obtained.
Other asset permissions are also unresolved. There is no blanket MIT/Apache license
for this mixed-provenance project. See [the provenance inventory](docs/third-party-notices.md)
and [permission-request drafts](docs/permission-requests.md) for the outstanding work.

> [!CAUTION]
> Experimental Samsung module. Keep a way to disable it and do not install on an
> unsupported firmware expecting guaranteed compatibility. To fully unload hooks,
> disable the module in LSPosed and follow its restart requirements. Never replace
> or re-sign Samsung's system biometrics APK.
