# Device acceptance

Automated checks establish file integrity and buildability, not biometric behavior.
Never simulate authentication, change enrollment or manipulate brightness controls
as part of this checklist. Keep a way to disable the module.

## Build checks

```sh
python3 scripts/check_catalog.py
./gradlew --no-daemon assembleDebug
apksigner verify app/build/outputs/apk/debug/app-debug.apk
zipalign -c -P 16 4 app/build/outputs/apk/debug/app-debug.apk
```

Use Android SDK build-tools versions that support the flags above. After installing,
compare the local SHA-256 with the installed `base.apk` and confirm package/version.
Installing an APK does not prove LSPosed loaded its hook code.

## Visual checks on hardware

- Enable only PrismLock for the documented biometrics scope; disable the old preview
  module and RingPrint. Turn off LockStar's fingerprint effect.
- Confirm the PrismLock label, launcher logo, English copy and version.
- Check all four imported fingerprints in dark mode, light mode and AOD. Their
  white/black variants should remain centered and visually consistent in size.
- Check OxygenOS and several differently shaped designs for transparent padding.
- Preview the selected icon and animation; confirm Aqua's smaller scale and fade.
- With Aqua selected, unlock normally and confirm that the effect disappears,
  does not remain on the home screen and behaves normally on repeated attempts.
- Check finger release, failed authentication, successful authentication, screen
  off/on and normal screen rotation. Record failures without altering enrollment.
- Disable customization and verify stock visual resources return after refresh.

Record device model, Android/One UI versions, LSPosed version, app version and
sanitized observations. Never commit private screenshots, raw device logs or dumps.

## Current evidence

During local research, the user reported working icon replacement and effects on
an SM-S911B. Catalog structure, original pixel equivalence, black/white variant masks
and desktop visible sizing have been checked locally. The 0.2.0 Aqua fix and new
package require user acceptance on the actual lockscreen; CI cannot establish it.
