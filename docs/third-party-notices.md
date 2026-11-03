# Third-party notices and release blockers

This is a provenance inventory, not a grant of rights. There is intentionally no
MIT, Apache or other blanket license covering this mixed-provenance project.
Public availability does not grant additional rights over third-party materials.
The maintainer has requested public source and stable distribution. The permissions
below remain unresolved; no authorization is implied by a GitHub release.

| Material | Source | Status |
| --- | --- | --- |
| Original catalog artwork and reconstruction reference | Supplied Fingerprint Catalog 1.3.0 by custombeta | The supplied license reserves rights and prohibits modification or publication of derivatives without written permission. Permission has not been obtained. The maintainer must obtain authorization or replace the affected material. |
| Samsung integration reference and inherited implementation | Supplied ELPEDR/RingPrint project | Credit retained. A license grant was not found in the supplied copy; confirm upstream terms before redistribution. |
| One UI font, wallpaper and native visual resources | Local Samsung / Now Brief audit | Proprietary provenance; permission or replacement required before public redistribution. |
| Prism, Vortex, Orbit and Neon artwork | Four user-supplied JPEGs | Source hashes are in the catalog index. Ownership and redistribution permission are not established. |
| Aqua animation | User-supplied `output-onlinegiftools.json` | Source hash is in the index. Converted and shortened locally; redistribution permission is not established. |
| PrismLock logo | User-supplied generated prism image | Provided as app branding; source ownership and generator terms still need confirmation for distribution. |
| Gradle wrapper | Gradle 8.13 generated wrapper | Generated files contain upstream notices; distribution checksum is pinned. |
| Xposed API | `de.robv.android.xposed:api:82` | Compile-only dependency, not bundled as module implementation. Preserve applicable upstream terms. |

Research source APKs, decompiled code and full firmware dumps are not included in
the repository. Hash references document local inputs without publishing originals.

The original custombeta license is retained verbatim in
[`licenses/custombeta.md`](licenses/custombeta.md). It is not PrismLock's own license
and it does not authorize this derivative. Current upstream references:

- https://github.com/custombeta/fingerprint-cataloge/blob/main/LICENSE.md
- https://github.com/ELPEDR/RingPrint
- https://github.com/Xposed-Modules-Repo/com.custombeta.fingerprintcataloge

## Permissions still requiring resolution

1. Obtain written derivative/redistribution permission for restricted catalog
   content, or replace it with original licensed assets and implementation.
2. Verify RingPrint's code license and preserve all required notices.
3. Replace or obtain permission for Samsung fonts, wallpapers and copied resources.
4. Confirm licenses for imported artwork, Aqua and branding.
5. Choose a license only for code and assets that the maintainer can license.
6. Maintain the release-signing and in-app update policy.

## Research and build guidance

Repository organization follows GitHub's official repository best-practices guide.
Build reproducibility uses the Gradle wrapper's official distribution-checksum
mechanism. CI actions are pinned to full commit IDs and receive read-only contents
permission. These measures do not resolve third-party licensing or device safety.

- https://docs.github.com/en/repositories/creating-and-managing-repositories/best-practices-for-repositories
- https://docs.gradle.org/current/userguide/gradle_wrapper.html
- https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions
