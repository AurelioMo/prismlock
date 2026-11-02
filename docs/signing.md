# Release signing

Official preview APKs use a dedicated local signing key, not the Android debug key.
The keystore and passwords are outside the repository and are never uploaded.
Keep a secure backup: future in-place updates must use the same certificate.

Official preview certificate SHA-256:

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
--print-certs` and `zipalign -c -P 16 4` before uploading it with a SHA-256 checksum.

## Switching from a developer build

A debug-signed installation cannot be updated by the release-signed APK even when
the package name is identical. Disable the module in LSPosed, save your chosen
settings, and uninstall the debug build before installing the public preview.
Uninstalling removes its app data. Enable the new installation in LSPosed again.
Future official releases signed by the same key can update it normally.

The installed local developer build is not silently uninstalled or migrated by
publishing a release. Do not publish keystores, password files or debug credentials.
