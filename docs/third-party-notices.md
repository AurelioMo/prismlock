# Third-party credits and provenance

PrismLock builds on community artwork and research. All third-party materials remain
the property of their respective owners. This page credits those sources; it does not
claim ownership over them or relicense them.

| Material | Source | Credit |
| --- | --- | --- |
| Original catalog artwork and reconstruction reference | Fingerprint Catalog 1.3.0 by custombeta | Catalog design and artwork by custombeta. Original license retained in [`licenses/custombeta.md`](licenses/custombeta.md). |
| Samsung integration reference and inherited implementation | ELPEDR / RingPrint | Samsung asset-hook research and implementation reference by ELPEDR / RingPrint. |
| One UI font, wallpaper and native visual resources | Local Samsung / Now Brief audit | Samsung and its licensors. Used only as local visual reference. |
| Prism, Vortex, Orbit and Neon artwork | User-supplied JPEGs | Supplied for PrismLock. Source hashes are recorded in the catalog index. |
| Aqua animation | User-supplied `output-onlinegiftools.json` | Supplied for PrismLock. Converted and shortened locally; source hash is in the index. |
| Keqing symbol and visual sequences | User-supplied `KeqingFingerPrint.apk` resource overlay | Original artwork remains with its respective owners; original artist identity is not established by the APK. Exact source SHA-256/member provenance is recorded in the catalog and [audit](catalog-audit.md). |
| Pyro symbol | User-supplied Pyro emblem image in the project request; converted to a white fingerprint icon for the catalog. | Source artwork remains with its respective owners. |
| Hydro symbol | User-supplied Hydro emblem image in the project request; converted to a white fingerprint icon. The Hydro animation is derived locally from the existing Pyro/Genshin frame timing. | Source artwork remains with its respective owners. |
| Dendro symbol | User-supplied Dendro emblem image in the project request; converted to a centered white fingerprint icon. | Source artwork remains with its respective owners. |
| PrismLock logo | User-supplied generated prism image | Provided as project branding. |
| Gradle wrapper | Gradle 8.13 generated wrapper | Generated files contain upstream notices; distribution checksum is pinned. |
| libxposed API | `io.github.libxposed:api:102.0.0` | Compile-only dependency, not bundled as module implementation. |

Research source APKs, decompiled code and full firmware dumps are not included in
the repository. Hash references document local inputs without publishing originals.

PrismLock is a free, non-commercial community project. It is not affiliated with
Samsung, Xiaomi, Good Lock, LSPosed, custombeta or ELPEDR.

## Upstream references

- https://github.com/custombeta/fingerprint-cataloge
- https://github.com/ELPEDR/RingPrint
- https://github.com/Xposed-Modules-Repo/com.custombeta.fingerprintcataloge

## Research and build guidance

Repository organization follows GitHub's official repository best-practices guide.
Build reproducibility uses the Gradle wrapper's official distribution-checksum
mechanism. CI actions are pinned to full commit IDs and receive read-only contents
permission.

- https://docs.github.com/en/repositories/creating-and-managing-repositories/best-practices-for-repositories
- https://docs.gradle.org/current/userguide/gradle_wrapper.html
- https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions
