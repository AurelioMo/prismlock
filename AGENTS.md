# Project rules

## Every release ships to both repositories

A release is not finished until it exists in both places. For version `X.Y.Z` with
`versionCode N`:

1. **Source repository — `origin`** (`https://github.com/AurelioMo/prismlock.git`):
   commit on `main`, tag `vX.Y.Z`, push `main` and the tag, then create a GitHub release
   titled `PrismLock X.Y.Z` with the signed APK attached as `PrismLock-X.Y.Z.apk` and a
   `SHA-256:` line in the notes.
2. **LSPosed module repository — `lsposed`**
   (`https://github.com/Xposed-Modules-Repo/io.github.aureliomo.prismlock.git`):
   push the same commit to `main`, tag `N-X.Y.Z` (the existing convention there, for
   example `143-1.4.3`), push that tag, and publish the release `N-X.Y.Z` with the same
   `PrismLock-X.Y.Z.apk` asset and the same notes.

The LSPosed listing is what users browse for modules, so it must never lag behind the
main repository. If a release has to be corrected, republish both; do not fix only one.

## Before tagging

Run `python3 scripts/check_catalog.py` and `./gradlew assembleRelease` with the release
signing environment variables, and confirm `apksigner verify` reports v2 and v3.
