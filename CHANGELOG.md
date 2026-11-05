# Changelog

## 1.0.5 - 2026-11-05

- Align catalog text/variant columns with their thumbnail grid.
- Retarget all built-in timelines to 120fps with scaled layer timing and interpolation; retain source images, source cadence and total durations. Request up to 120Hz for the app on supported displays.
- Coalesce thumbnail requests, use cached wallpaper immediately, refresh only affected visible rows, recycle unpublished canceled preview bitmaps and release idle worker threads.
- Add Keqing's static symbol, complete electro effect from 60fps source frames, and appear/disappear styles from the supplied resource overlay.
- Consolidate repeated visual designs into 30 icon families and 19 effect families, with distinct color/motion styles selectable inside each family.
- Preserve existing selections and light/AOD behavior; resolve the old Outline identifier to the sharper Bubble artwork.
- Add decoded-frame/timeline audit tools, source provenance and catalog-family regression coverage.

## 1.0.4 - 2026-11-05

- Migrate fully to libxposed API 102.0.0 and modern module registration. A framework supporting API 102 is now required; legacy-only frameworks cannot load this version.
- Replace the eager catalog with recycled native list rows; decode thumbnails and display-sized wallpapers in the background.
- Load hook configuration and artwork outside Samsung's UI thread, with change notifications and complete configuration snapshots.
- Let enlarged OxygenOS/custom icon glows draw beyond the composition and parent clipping bounds on compatible Samsung Lottie builds; restore original clipping for stock icons. Preserve sensor/touch geometry.
- Check for stable updates on every foreground entry, show available updates without opening Settings, share concurrent checks and allow immediate retries after failures.
- Preserve the full catalog, imports, light/AOD variants, themes, visual effects and finite playback durations.
- Add automated API 102 hook, clipping restoration, recycled-row and startup-update regressions.
- Validate installation, app navigation/scrolling and API 102 hook loading on Galaxy S23. Restart the scoped process/reboot after upgrading from legacy.

## 1.0.3 - 2026-11-04

- Import 11 ColorOS 17 fingerprint designs and 10 touch effects at 60 fps.
- Replace matching older artwork while preserving saved selections.
- Use the same visible footprint for all catalog effects and normalize icons to the selected size.

## 1.0.2 - 2026-11-03

- Resume the verified update automatically after granting installation permission.
- Reuse the downloaded APK and recheck its package, version and signer on return.
- Allow PackageInstaller to deliver its confirmation intent and status through the callback.
- Open installation confirmation through the foreground activity; queue it until the
  app returns when the callback arrives in the background.
- Abandon incomplete installer sessions after errors.
- Add Android host regression tests for permission resumption and installer callbacks.

## 1.0.1 - 2026-11-03

- Load custom fingerprint icons with configuration-specific Lottie cache keys so
  cached stock or previously selected artwork cannot bypass the selected icon.
- Preserve custom icon colors when Samsung applies its stock fingerprint tint.
- Add explicit light/AOD variants for AOSP and OxygenOS, and make AOSP visible on dark backgrounds.
- Refresh the keyguard icon on configuration changes even when its theme is unchanged.
- Retain the asset hook as a fallback; touch effects use their existing path.
- Device verification on the reported S26 Ultra remains pending.

## 1.0.0 - 2026-11-03

- Promote PrismLock to the stable 1.0.0 release.
- Add built-in GitHub update checks with a one-tap download and install flow.
- Verify update integrity and the PrismLock signing certificate before installing.
- Install silently through root when available, with the Android system installer as fallback.
- Match Samsung's full fingerprint and touch-effect asset families, using the same
  device-independent `AssetManager` hook as RingPrint.
- Move the hook to immutable configuration snapshots so a failed refresh keeps the
  last known-good settings.
- Pre-build imported PNG Lottie payloads once per configuration change.
- Keep the existing interface, catalog, previews and visual design unchanged.
- Refresh the README and release documentation for the stable channel.

## 0.2.0-preview - 2026-11-02

- Introduce the PrismLock name and user-provided four-point prism logo.
- Rename the package and provider to `io.github.aureliomo.prismlock`.
- Add adaptive, legacy and monochrome launcher icons.
- Reduce Aqua from 18 seconds to 0.8 seconds with a transparent final frame.
- Reduce Aqua's visible footprint from 132 to 96 composition units.
- Reflect Aqua's scale and fade-out in the in-app preview.
- Add a pinned Gradle wrapper, portable catalog checks and CI build workflow.
- Organize English documentation and third-party credits.
- Publish source and a separately signed preview APK with checksums.
- Provide a transparent repository logo and a download-first project page.

This build needs module reactivation because its package is different. The old
research app is not uninstalled and its preferences are not migrated automatically.

## 0.1.7-samsung-preview - 2026-11-02

- Add Prism, Vortex, Orbit and Neon fingerprints with automatic light/AOD variants.
- Import the original Aqua animation and remove white/black frame backgrounds.
- Show fingerprint and touch-effect previews separately.
- Retain English copy, Samsung-inspired appearance and automatic selection saving.
