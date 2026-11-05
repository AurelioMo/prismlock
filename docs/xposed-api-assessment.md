# Modern Xposed API migration

PrismLock 1.0.4 uses `io.github.libxposed:api:102.0.0`, the latest release listed in
Maven Central at the time of this change (2026-11-05). The module requires API 102,
uses `XposedModule`/`onPackageReady` and modern META-INF registration, and removes
the legacy entrypoint, XposedHelpers and AndroidAppHelper. Update activation in an
API 102 framework after upgrading from an older version.

Method lookup is resolved during hook setup; modern invokers load selected Lottie
streams. Configuration IO/JSON preparation run in a background worker, with
ContentProvider change notifications and cached snapshots. The existing provider
retains its UID restrictions and import format, preserving saved configuration.
Hot reload is not opted into; restarting the scoped process after module updates
remains required.

API version alone is not a measured performance guarantee. This release also
addresses concrete UI costs: eager catalog creation, synchronous thumbnail and
wallpaper decoding, repeated tab construction and configuration work on Samsung's
main thread. Physical-device frame-time and memory comparisons remain necessary
to quantify the improvement on each One UI/framework combination.

## Official sources

- [libxposed API repository](https://github.com/libxposed/api)
- [Maven Central release metadata](https://repo.maven.apache.org/maven2/io/github/libxposed/api/maven-metadata.xml)
- [API 102 lifecycle reference](https://libxposed.github.io/api/io/github/libxposed/api/XposedModuleInterface.html)
- [Official module example](https://github.com/libxposed/example)
- [LSPosed migration guide](https://github.com/LSPosed/LSPosed/wiki/Develop-Xposed-Modules-Using-Modern-Xposed-API)
