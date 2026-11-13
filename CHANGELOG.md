# Changelog

## 1.4.7 - 2026-11-13

- Make Dew wrap the fingerprint: the ring is drawn at 210 of the 288 canvas instead of 150, inside the same halo, so the drop surrounds the icon rather than sitting outside it.
- Slow Dew down: each frame is held ten playback frames, which plays the whole pulse over 1.42 seconds instead of 0.57, the slowest the catalog gate allows.

## 1.4.6 - 2026-11-13

- Add the Bloom fingerprint icon: the supplied fire flower is keyed off its black plate by an edge flood fill, so no haze is left behind the petals, and fitted to the shared photo-icon footprint.
- Add the Dew touch effect: sixteen frames four source frames apart cover one full pulse of the glass drop over 0.57 seconds at 120fps, and the ring is drawn at 150 of the 288 canvas inside a blurred halo so it wraps the fingerprint as an aura. The keyed alpha is lifted, because the dark glass only lights up on its rim.

## 1.4.5 - 2026-11-13

- Make Tide an aura around the sensor instead of a ring floating above it. The runtime maps an effect's catalogBounds to a fixed footprint, and Tide's crop filled its own bounds, so a tight ring was drawn at full size. The ring is now drawn at 150 of the 288 canvas inside its own blurred, dimmed halo, which puts the bounds at 284 like Pulse and leaves the visible ring close to the fingerprint.

## 1.4.4 - 2026-11-13

- Fit Tide to the sensor like Pulse: the shared crop is scaled to 170 of the 288 canvas instead of the full-bleed 240, which brings its footprint to the same range as the other on-sensor effects.
- Sample Tide every six source frames so 16 frames cover 3.2 seconds of the loop in 0.57 seconds. Consecutive frames barely differed and read as a still image; the waves now visibly move.

## 1.4.3 - 2026-11-13

- Add the Tide touch effect: 16 frames of the supplied 600x600 loop keyed off the black plate, played at 120fps with interpolation over 0.57s and ending on a generated transparent frame.

## 1.4.2 - 2026-11-13

- Restore the 1.2.0 update system unchanged: the module downloads the release APK, verifies digest, package and signing certificate, installs silently through the root path when the device is rooted and hands the file to Android's system installer otherwise, with the permission resume and the install-status callback back in place.

## 1.4.1 - 2026-11-13

- Bring back the in-app update installer, without root. The module downloads and verifies the APK and hands it to Android's system installer, which asks for confirmation; the root switch that ran a privileged package install is gone, so the update path never spawns a shell.

## 1.4.0 - 2026-11-13

- Stop downloading and installing update packages inside the app. The module no longer requests the install-packages permission, no longer runs `pm install` through root and no longer hosts a package-install receiver; the update prompt and the update notification open the GitHub release page, where Android's own installer handles the APK.
- Centre the launcher icon on the opaque artwork instead of its bounding box, because the light beam on the right made the diamond sit left of centre, and fit the logo without stretching it.

## 1.3.9 - 2026-11-12

- Re-query the configuration whenever the touch effect is read: skipping the read when no change had been flagged kept serving the previous effect (Pulse) after switching animations.

## 1.3.8 - 2026-11-12

- Add the Snowflake icon, enlarge Lotus to the footprint of the glyph icons, cache prepared catalog payloads instead of re-parsing up to 2.8MB per change, and stop forcing a configuration rebuild on every effect read.

## 1.3.7 - 2026-11-12

- Remove the screen-off lead-in: shifting layer windows left the interpolated opacity keyframes behind, so short effects (Orbit 0.47s, Pulse 0.43s) played completely invisible.

## 1.3.6 - 2026-11-12

- Raise the touch effect above the sensor icon, so bright or large icons (Lotus, Orb) no longer hide white effects such as Orbit and Pulse.

## 1.3.5 - 2026-11-12

- Rebuild the Lotus icon from the new render: clean flood-filled cutout with no backdrop haze, fitted to the same footprint as the other icons, colour only.

## 1.3.4 - 2026-11-12

- Lotus: scale by the solid crystal instead of the faint bloom so it reads at the same size as the other icons, keep the glass slightly translucent, and log which effect payload is served.

## 1.3.3 - 2026-11-12

- Add the Lotus fingerprint icon from the supplied glass-flower render, with light and AOD variants.

## 1.3.2 - 2026-11-12

- Serve touch effects shorter than 0.5s with a silent lead-in while the display is off, so they still animate when unlocked from AOD or with the screen off.

## 1.3.1 - 2026-11-12

- Fix the Orb thumbnail: use the shared 112-unit preview footprint instead of the icon footprint, which overflowed the 144px tile.

## 1.3.0 - 2026-11-12

- Add the Orb fingerprint icon from the supplied glossy sphere render.

## 1.2.9 - 2026-11-12

- Always serve the selected touch effect and wait for the configuration read, so a stale effect file left in Samsung's data directory can no longer play instead of the selection.

## 1.2.8 - 2026-11-12

- Play the whole Pulse motion (all 25 pulse frames at a 60fps source cadence in 0.433 seconds) and seat the ring on a soft halo so it draws at about half the size of a full-bleed effect.

## 1.2.7 - 2026-11-12

- Fix the Pulse effect: sample the actual pulse frames instead of the still tail of the loop, and shrink the artwork so it sits on the sensor.

## 1.2.6 - 2026-11-12

- Add the Pulse effect: the supplied GIF loop keyed off its black plate, 13 consecutive frames at 0.467 seconds ending transparent.

## 1.2.5 - 2026-11-12

- Shorten the Orbit effect to 0.467 seconds, matching the shorter catalog effects, because Samsung only stops the effect view when the animation ends.

## 1.2.4 - 2026-11-12

- End the Orbit effect on a transparent frame with a fade, like the other effects, so it leaves the screen after unlocking.

## 1.2.3 - 2026-11-12

- Match the Orbit effect pacing and size to the other effects: 24 consecutive source frames at 0.8 seconds and artwork at 240 of the 288 canvas.

## 1.2.2 - 2026-11-12

- Fix the Orbit effect: 45 consecutive source frames at 1.5 seconds keep the original rotation pace instead of sampling the whole loop into 0.8 seconds, and the artwork now fills the shared effect footprint.

## 1.2.1 - 2026-11-12

- Add the Orbit effect: the supplied video loop keyed off its black plate and converted to 24 transparent 288px frames at 0.8 seconds.

## 1.2.0 - 2026-11-07

- Keep the Anemo fingerprint icon and remove the unfinished Anemo animation from the catalog.

## 1.1.9 - 2026-11-07

- Rename the current Dendro catalog family to Anemo in the user interface.

## 1.1.8 - 2026-11-07

- Remove the remaining pink chroma-key plate from Dendro frames.
- Slow the Dendro effect to 1.47 seconds for a more natural pace.

## 1.1.7 - 2026-11-07

- Optimize the Hailuo Dendro animation by removing the pink screen, cropping the portrait canvas and converting it to 24 transparent 288px frames at 0.8 seconds.

## 1.1.6 - 2026-11-07

- Make the Dendro fingerprint fully opaque white instead of preserving the source artwork's green shading.

## 1.1.5 - 2026-11-07

- Add the centered white Dendro fingerprint family with light/AOD variants.

## 1.1.4 - 2026-11-07

- Notify users when the background update checker finds a new stable release.

## 1.1.3 - 2026-11-07

- Remove Hailuo's remaining black plate/compression haze from Hydro frames.
- Reduce the Hydro effect footprint to match the fingerprint sensor size.

## 1.1.2 - 2026-11-07

- Optimize the supplied Hailuo Hydro animation to 24 transparent 288px frames at 0.8 seconds.

## 1.1.1 - 2026-11-07

- Enlarge the Hydro water effect and center its vertical footprint to match the Pyro animation.

## 1.1.0 - 2026-11-07

- Replace the Hydro recolor with a new water ripple and splash animation without an elemental logo in the center.
- Keep the Pyro fingerprint optically centered.

## 1.0.9 - 2026-11-07

- Add the white Hydro fingerprint family and a new blue water animation derived from the Pyro timing and composition.
- Optically recenter the Pyro fingerprint artwork.

## 1.0.8 - 2026-11-07

- Center the Pyro fingerprint layer on the Samsung sensor footprint.
- Show the existing Genshin animation as “Pyro” in the catalog.

## 1.0.7 - 2026-11-07

- Add a white Pyro fingerprint family with light/AOD variants, ready to pair with the existing Genshin touch animation.

## 1.0.6 - 2026-11-06

- Center Keqing animation bounds so the visible effect stays aligned with the fingerprint sensor.
- Wait for the initial configuration snapshot before replacing Samsung's touch effect, preventing an occasional default animation on the first authentication.

## 1.0.5 - 2026-11-05

- Align catalog text/variant columns with their thumbnail grid.
- Retarget all built-in timelines to 120fps with scaled layer timing and interpolation; retain source images, source cadence and total durations. Request up to 120Hz for the app on supported displays.
- Coalesce thumbnail requests, use cached wallpaper immediately, refresh only affected visible rows, recycle unpublished canceled preview bitmaps and release idle worker threads.
- Add Keqing's static symbol, complete electro effect from 60fps source frames, and appear/disappear styles from the supplied resource overlay.
- Consolidate repeated visual designs into 33 icon families and 20 effect families, with distinct color/motion styles selectable inside each family.
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
