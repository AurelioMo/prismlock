# Changelog

## 1.0.0 - 2026-11-03

- Promote PrismLock to the stable 1.0.0 release.
- Add built-in GitHub update checks with a one-tap download and install flow.
- Verify update SHA-256 digests and the PrismLock signing certificate before installing.
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
