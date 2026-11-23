# GitHub Pages and releases

`docs/` is the GitHub Pages site (deployed from `main` → `/docs`): landing page, user guide, privacy policy and legal notice in German and English. Installers are published only as GitHub releases, each with the source archive of the bundled Java runtime; no other site hosts them, because GPLv2 counts source access only from the same place as the binaries. The martensening.com product page links the release assets.

## Site structure

| Address | Content |
|---|---|
| `/de/`, `/en/` | Landing page with downloads (`_includes/downloads.html`) |
| `/de/guide/`, `/en/guide/` | User guide |
| `/de/privacy/`, `/en/privacy/` | Privacy policy of the app, the site and the downloads (linked from About) |
| `/de/legal/`, `/en/legal/` | Legal notice |
| `/` | Chooses `/de/` or `/en/` from the browser language, with visible links as fallback |
| `/anleitung/` | Redirect to `/de/guide/`, keeping the `#section` anchor (earlier address) |

Both languages use identical slugs; `_layouts/default.html` derives navigation, footer and the language switch from `page.lang` and `page.url`, so a new page only needs `lang` and `permalink`. Redirect pages use `_layouts/redirect.html`. `repository_url` in `_config.yml` identifies the repository, so forks can publish their own site.

The guide describes current behavior and how to use it, never earlier behavior or design rationale; version history belongs in the [changelog](../CHANGELOG.md). Legal pages name the actual host and download provider; update them when distribution changes.

## Download links

`_config.yml` holds `release` (the documented version) and `release_downloads`:

- `false`: the landing pages link the repository's `releases/latest` without advertising a version.
- `true`: the buttons link the assets of the GitHub release `v<release>` directly, with its release page as a secondary link.

A tag alone is not a release, and a release without assets has no installer URLs. Before publishing any Pages change, run:

```powershell
.\tools\test_site_downloads.ps1    # offline: complete release accepted, seven incomplete cases rejected
.\tools\check_site_downloads.ps1   # online: the configured destination and both installer URLs
```

With `release_downloads: true` (or `-RequireGitHubRelease`), the check requires a published release with both installers, both checksum files and the bundled Java runtime's source archive. With `false`, it requires the same of `releases/latest`, the fallback target.

## Publishing a version

Only when the contributor explicitly authorizes it; preparing, building or committing is not permission to publish.

1. Raise the version, build the release candidate (`build.ps1 -Target All -AndroidConfiguration Release`) and let the contributor accept it on their phone (see [repository commands](repository-rules.md#ui-refinement-and-final-acceptance)).
2. Download the source archive of the exact Java runtime bundled in the MSI (`desktop/build/compose/tmp/main/runtime/release` names the version; the JDK path is printed by `build.ps1`) with its `.sha256.txt` into `.work/runtime-source/`, e.g. for Temurin `gh release download jdk-<version> -R adoptium/temurin21-binaries -p '*-jdk-sources_*.tar.gz*'`.
3. Complete the [publication audit](publication-checks.md), commit the release source, create the annotated tag `v<version>` and push both.
4. `.\tools\publish_github_release.ps1 -DryRun`, then without `-DryRun`: verifies tag, checksums and runtime source, creates a **draft** release with notes from the changelog and uploads APK, MSI, `.sha256` files and the runtime source. Review the draft, then publish it (`-Publish` or `gh release edit v<version> --draft=false --latest`).
5. `.\tools\check_site_downloads.ps1 -RequireGitHubRelease` must pass. Then set `release_downloads: true` and `release`/`released` in `docs/_config.yml`, run both checks again, and push the Pages update. Check both languages and both installer links on the live site.
6. martensening.com: set the version and date in its `html/myaudio/index.php` (the buttons link this release's assets) and let its operator deploy (that repository's `.wiki/server.md`). Never copy installers there.

## Guide screenshots

Capture from the release candidate on the contributor's phone (raw size 1080 × 2424 on the reference Pixel 9) with a synthetic demo project (`tools/demo_project.py`), never private recordings:

- Enable Android's status bar demo mode for the session and restore it afterwards; never delete or modify the contributor's own projects. Close dialogs with Cancel.
- Keep raw captures in ignored `.work/guide-raw/` and run `python tools/crop_guide_images.py .work/guide-raw .work/guide-out`. It crops status/navigation bars, detects dialogs and composes the landscape home screen so only the demo project appears. Adjust its boxes when a layout changes, inspect every output, then copy them to `docs/images/`.
- Do not fabricate calibration results; keep the previous calibration image when no real calibration can be shown.

Preserve each image's aspect ratio (`.shots` uses `align-items: flex-start`). Remove images no page references. Check both languages at 320, 390 and 1440 px: no horizontal overflow, missing images or broken anchors, and working redirects.
