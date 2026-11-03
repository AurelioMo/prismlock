# Release signing

Official PrismLock APKs use a dedicated release signing key, not the Android debug
key. The keystore and passwords stay outside the repository and are never uploaded.
Keep a secure backup: in-place updates must use the same certificate.

Official release certificate SHA-256:

```text
6e89f50c348d5857f85476d77622ef6c39334ae895661506a55c63a98b06c850
```

```sh
export PRISMLOCK_KEYSTORE=/secure/path/prismlock-release.jks
export PRISMLOCK_STORE_PASSWORD='your-store-password'
export PRISMLOCK_KEY_PASSWORD='your-key-password'
./gradlew --no-daemon assembleRelease
```

The key alias must be `prismlock`. The command fails if the required signing
environment is absent. Verify the APK with Android build-tools `apksigner verify
--print-certs` and `zipalign -c -P 16 4` before uploading it.

## Publishing an update

The in-app updater reads the latest **stable** GitHub release. Pre-releases and drafts
are ignored.

1. Increase `versionCode` and `versionName` in `app/build.gradle`.
2. Run `python3 scripts/check_catalog.py` and `./gradlew --no-daemon assembleRelease`.
3. Verify the certificate and alignment with the commands above.
4. Create a normal GitHub release tagged `vX.Y.Z` (for example `v1.0.1`).
5. Upload `PrismLock-X.Y.Z.apk` as the release asset.
6. Publish the release as the latest stable release, not as a pre-release.

Users on a release signed with the same certificate can update in place from
**Settings → App → Updates**. Settings are preserved.

## Signing mismatch

An APK signed with a different key cannot update an existing installation. If a
debug build is installed, uninstall it before installing the official release. This
removes its app data. Official releases can update each other normally.

The installed local developer build is not silently uninstalled or migrated by
publishing a release. Do not publish keystores, password files or debug credentials.
