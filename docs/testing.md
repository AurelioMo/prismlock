# Release validation

Automated checks establish file integrity and buildability. This checklist covers the
on-device pass for a release build. Never simulate authentication, change enrollment
or manipulate brightness controls as part of validation.

## Build checks

```sh
python3 scripts/check_catalog.py
python3 scripts/check_icon_loader.py
./gradlew --no-daemon assembleDebug
apksigner verify app/build/outputs/apk/debug/app-debug.apk
zipalign -c -P 16 4 app/build/outputs/apk/debug/app-debug.apk
```

Use Android SDK build-tools versions that support the flags above. After installing,
compare the local SHA-256 with the installed `base.apk` and confirm package/version.
The icon-loader regression check requires `ANDROID_HOME` and Java 17. It exercises
the production hook against simulated Samsung/Xposed boundaries: stock and previous
icon caches, light/AOD variants, imported artwork, color-filter scope, disable/fallback,
and keyguard refresh. It does not establish device rendering compatibility.

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

Record device model, Android/One UI versions, LSPosed version and app version for any
failure report. Never commit private screenshots, raw device logs or firmware dumps.
