# Release validation

Automated checks establish file integrity and buildability. This checklist covers the
on-device pass for a release build. Never simulate authentication, change enrollment
or manipulate brightness controls as part of validation.

## Build checks

```sh
python3 scripts/check_catalog.py
python3 scripts/check_icon_loader.py
./gradlew --no-daemon assembleDebug
./gradlew --no-daemon testDebugUnitTest
apksigner verify app/build/outputs/apk/debug/app-debug.apk
zipalign -c -P 16 4 app/build/outputs/apk/debug/app-debug.apk
```

Use Android SDK build-tools versions that support the flags above. After installing,
compare the local SHA-256 with the installed `base.apk` and confirm package/version.
The icon-loader regression check requires `ANDROID_HOME` and Java 21. It runs
`ModernHookTest` with the real API 102 classes and a simulated framework/Samsung boundary: stock and previous
icon caches, light/AOD variants, imported artwork, color-filter scope, disable/fallback,
and keyguard refresh. It does not establish device rendering compatibility.
The Gradle update-flow tests use Robolectric on API 31 and 36 and require Java 21.
They cover one-shot permission resumption, denial, mutable status/confirmation delivery
with the old immutable callback as a negative control, and foreground/background
confirmation routing. These are simulated Android tests, not physical-device tests.

## On-device checks

- Enable only PrismLock for the documented biometrics scope; disable competing
  fingerprint modules and LockStar's fingerprint effect.
- Confirm the PrismLock label, launcher logo, English copy and version.
- Check imported fingerprints in dark mode, light mode and AOD. Their white/black
  variants should remain centered and visually consistent in size.
- Check OxygenOS and several differently shaped designs for transparent padding.
- With a dark wallpaper, check AOSP is visible. Check AOSP and OxygenOS on light
  wallpapers and AOD. Cycle Prism, Vortex, Orbit and Neon without changing the system
  theme; each selected design should replace the previous icon. Confirm their colors
  survive Samsung's stock fingerprint tint.
- Preview the selected icon and animation; confirm Aqua's smaller scale and fade.
- With Aqua selected, unlock normally and confirm that the effect disappears,
  does not remain on the home screen and behaves normally on repeated attempts.
- Check finger release, failed authentication, successful authentication, screen
  off/on and normal screen rotation. Record failures without altering enrollment.
- Disable customization and verify stock visual resources return after refresh.
- Open **Settings → App → Updates** and confirm the update status is shown.
- Publish a test release with a higher version code and confirm the updater offers it,
  verifies the signing certificate and preserves settings after installing.
- Without root installation permission, revoke the unknown-sources permission for
  PrismLock. Start an update, grant permission, and return: the cached APK should
  resume installation without a second download or Update tap. Repeat with permission
  denied and with the app recreated while Settings is open. Verify the system
  confirmation appears while the app is foregrounded, and waits until app return if
  its callback arrives in the background. Cancel installation and retry normally.

Record device model, Android/One UI versions, LSPosed version and app version for any
failure report. Never commit private screenshots, raw device logs or firmware dumps.


## Motion and compatibility regression checks

`ArtworkTest` runs on simulated API 26, 28, 31 and 36. It checks recycled-row identity,
selection persistence, retained tab lists, full-size OxygenOS preview space, startup and navigation
through all three tabs, settings-tab restoration after recreation, bounded PNG
sampling, every catalog effect's unchanged image data/frame rate/duration, the
maximum of two overlapping image layers, strictly increasing opacity keys, Aqua's
transparent ending, and all fingerprint variant footprints. The existing updater
and modern hook checks remain required.

On a physical Samsung device, compare the release baseline and this build on the
same wallpaper and brightness. Check frame pacing during repeated selections,
preview open/close, normal unlock, AOD and screen off/on. Record model, One UI and
Android versions, peak memory and dropped frames. Automated tests do not establish
all-model compatibility or a measured performance improvement. Physical-device results for 1.0.4 are recorded below.

`ModernHookTest` runs on API 26, 31 and 36. It checks scope/idempotent registration,
versioned caches, light/AOD/import precedence, tint isolation, optional-loader
fallback, keyguard refresh and reference-counted clipping restoration.
`StartupUpdateTest` verifies an automatic offer on the first catalog tab despite
old cache/prompt markers, another request after foreground return, concurrent check
coalescing and immediate recovery from a failed request.

For version 1.0.4, also verify module activation in an API 102 framework after
upgrading from legacy registration. At maximum OxygenOS size, check the glow on
dark/light/AOD screens and confirm stock clipping returns when customization is
disabled. Missing optional clipping controls on older Samsung Lottie versions
retain their original behavior.


## 1.0.4 physical-device smoke test — 2026-11-05

- Galaxy S23 / SM-S911B, reported Android 17 (API 37), One UI property `90000`,
  LSPosed v2.2.1 (7936).
- Installed the release-signed version 104 over version 103, preserving app data.
- Navigated/scrolled the existing catalog and switched among all three tabs. Three
  scripted runs recorded 148 / 160 / 155 rendered frames, with 1 / 0 / 0 janky
  frames. Median frame time was 5 ms in each run; 95th percentiles were 6 / 6 / 5 ms.
  This is a smoke-test sample on this device, not a controlled before/after benchmark
  or a compatibility guarantee for all models.
- Confirmed `API 102 hooks installed in com.samsung.android.biometrics.app.setting`
  in the LSPosed module log after restarting the scoped process.
- The user initially saw a square crop after increasing size while the old legacy
  process was still active, then confirmed normal appearance after the new process
  loaded. Module updates require scoped-process restart/reboot; ordinary selection
  changes refresh through configuration notifications.
- Existing theme/catalog presentation and selected settings remained available.
  Automated tests additionally cover maximum OxygenOS preview space, clipping
  restoration, startup update offers, import/tint/cache behavior and failure paths.


## 1.0.5 catalog/Keqing coverage

`CatalogFamiliesTest` runs on API 26, 31 and 36. It verifies every original 1.0.4
identifier against the new family/style index, the Outline alias, alternate theme
artwork, a grouped row's selected state, canceled/saved style choices, Keqing's
three complete timelines, transparent endings and original 40% symbol opacity.
The existing artwork, updater and modern-hook regressions remain required.
The optional full-frame audit scripts and source provenance are documented in
[catalog audit](catalog-audit.md).

`Motion120Test` checks all legacy and Keqing playback durations against the native
baseline, asserts the 120fps timebase and unchanged image counts, and samples
intermediate opacity values for both 30fps and 60fps sources. `AppWorkersTest`
verifies FIFO behavior, idle-thread teardown and successful restart for new work.


## 1.0.5 physical-device validation — 2026-11-05

Installed the final release-signed 120fps build on the Galaxy S23 without clearing
app data. The installed APK hash matches the release artifact. The user confirmed
functionality. Three navigation/scroll runs recorded 300 / 307 / 308 rendered
frames with 2 / 0 / 1 janky frames (3 of 915 total). Frame-interval samples had a
median of approximately 8.343 ms (~120Hz); the app window requested 120Hz. The
first run's p95 was 14ms; the two warm runs were 8ms. These are device smoke-test
results, not a guarantee of uninterrupted 120fps or an all-model comparison.
After idle, no named PrismLock worker threads remained in the app process.
The final local automated suite passed 83 tests and debug/release lint found no
errors. Restart the scoped process/reboot after updating so LSPosed loads the
new interpolation/configuration code; preferences and selections are retained.
