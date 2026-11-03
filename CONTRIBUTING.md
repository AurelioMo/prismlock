# Contributing

PrismLock is a public project with third-party artwork and research resources.
Confirm the provenance of submitted artwork and code before proposing changes.

## Focus

Keep changes small and limited to fingerprint artwork, touch effects, catalog UI,
or build tooling. Do not modify authentication results, enrollment, illumination
masks, brightness control, device security settings, or the hook's package scope.

Use Java 17 and preserve the existing native Android structure. Avoid adding
frameworks, generated binaries or local research dumps. Update the changelog for
user-visible changes and document any required LSPosed activation steps.

## Verification

```sh
python3 scripts/check_catalog.py
./gradlew --no-daemon assembleDebug
```

Report desktop validation and actual device testing separately. For visual changes,
run the checklist in `docs/testing.md`. Sanitize screenshots and logs: do not post
serial numbers, account details, notifications, biometric information or firmware
dumps. Never include signing keys, credentials, local SDK paths or debug APKs in Git.
