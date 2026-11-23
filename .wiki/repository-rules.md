# Repository commands and working conventions

Policy is in [AGENTS.md](../AGENTS.md); setup and optional signing are in [developer builds](developer-builds.md).

## Git and project memory

Commit and push permission rules are in [AGENTS.md](../AGENTS.md). Update the wiki alongside meaningful changes: decisions newest-first, current work with evidence and limitations. User-visible changes go into CHANGELOG.md. Shared workflows: [.agents](../.agents/README.md).

## Branches and releases

- `main` is the default branch and the latest release; GitHub Pages builds from `main` → `/docs`, so the site always describes the released app. It is protected: changes only through a pull request (no required approval), no direct or force pushes, no deletion, also for admins. Only merge commits are allowed in the repository.
- `develop` is the working branch. All commits, wiki updates and guide changes land there and are pushed there; no feature branches for now. Pull requests from contributors target `develop`.
- **Release:** on `develop`, raise the version, update CHANGELOG and current work, build and accept the candidate (see [UI refinement and final acceptance](#ui-refinement-and-final-acceptance)). Then open a pull request `develop` → `main` titled `Release <version>` whose body is the changelog entry, merge it with a merge commit, and put the annotated tag `v<version>` on that merge commit (its tree equals the built `develop` commit). Publish the GitHub release from the tag and switch the download links with a second pull request ([publishing a version](site-publication.md#publishing-a-version)). Each step needs the user's explicit request.
- After a merge into `main`, bring the merge commit back into `develop` (`git merge --ff-only origin/main`, or a plain merge if `develop` moved on) and push `develop`. `main` then never holds anything `develop` lacks, so the next release pull request has no conflicts.

## Issues

Open work, feature requests and bug reports are [GitHub Issues](https://github.com/bmartensen/my-audio/issues). Forms exist for bug reports and feature requests (`.github/ISSUE_TEMPLATE/`). Issues describe changes to the codebase and its state (features, bugs, technical work), never tests, measurements or acceptance checks to perform; those belong to the work on an issue, and their results and open verification go to [current work](current-work.md). Labels: `bug`, `enhancement`, `android`, `windows`, `audio`, `effects`, `development`. A milestone per minor version (e.g. `0.10.x`, since every build raises the patch version) groups its issues and summarizes its agreed decisions in its description. Agreed behavior still goes to [requirements](app-requirements.md) and decisions to [decisions](decisions.md); an issue links them instead of repeating them.

## Build and automated checks

```powershell
.\setup.ps1 -Target All
.\tools\test_dev_environment.ps1
.\gradlew.bat :app:assembleDebug :shared:desktopTest :app:testDebugUnitTest :app:lintDebug
.\build.ps1 -Target Windows
```

Setup selects JAVA_HOME and ANDROID_HOME in the current process. Keep IDE/JDK selections consistent; `.idea/`, `.vscode/` and `local.properties` are local. `gradle.properties` is the shared source of versionName/versionCode for Android and Windows. Raise the patch version and versionCode before every distributed build. Each released version gets an annotated tag `v<versionName>` (e.g. `v0.10.4`) on its release merge commit in `main`, whose tree equals the `develop` commit it was built from.

```powershell
.\tools\build_desktop_native.ps1
.\tools\release_apk.ps1
.\tools\release_desktop.ps1
.\gradlew.bat :desktop:run "-PmyAudio.openProject=<id>"
```

Outputs, logs, screenshots and personal test projects stay in ignored `.work/`. APK release packaging verifies its configured signing certificate. MSI redistribution requires the matching runtime notices and corresponding source described in the build guide.

For Pages, guide or download-link changes, also run `tools/test_site_downloads.ps1` and
`tools/check_site_downloads.ps1`; verify images, internal links and redirects in German and English
at phone and desktop widths. Direct release downloads require uploaded, published assets, not just a
tag; `tools/publish_github_release.ps1` creates the release only on explicit authorization. See
[Pages and releases](site-publication.md).

## Device checks

Only run installation/device commands when authorized for that device. Replacement installs preserve data only when signatures are compatible; never resolve a mismatch by automatic uninstall.

### UI refinement and final acceptance

1. Iterate on layout and interaction with debug builds in the emulator. Rebuild and replace the debug app there without incrementing the release version for each visual adjustment. Check screenshots with representative multitrack projects, portrait/landscape, light/dark themes and relevant navigation modes. Keep destructive-action tests in disposable demo data or discard unsaved changes.
2. Once the emulator checks pass, prepare the release candidate with the next shared versionName/versionCode and build the Android release APK and Windows installer. Preserve every previously distributed package; further changes after a candidate has been installed for acceptance require a new version.
3. Perform final acceptance with the release build on the contributor's connected smartphone, using their existing, finished projects and realistic track counts. The current maintainer uses a Pixel, but any contributor's compatible phone can serve this role. Select the intended device explicitly; no model or serial is a prerequisite. Use the contributor's configured signing identity, verify update compatibility and preserve their projects. Never substitute a debug installation over their release app.
4. Inspect the actual phone screenshots and let the contributor assess the result in their normal workflow. Emulator checks, successful packaging and signature verification do not constitute final human acceptance or hardware audio acceptance. Record their scope separately in current work. Windows review uses the matching installer when the shared UI is affected.

Keep the final phone pass focused on acceptance; return to the emulator for further polish rather than repeatedly distributing intermediate builds. Package preparation and local acceptance do not authorize a commit, push or publication.

```powershell
.\tools\debug_launch.ps1 -Target emulator
.\tools\debug_launch.ps1 -Target phone -Device <serial>
.\tools\debug_launch.ps1 -NoBuild -Screen calibration
.\tools\debug_launch.ps1 -ScreenshotPath .work/home.png
.\tools\native_tests.ps1 -Device <serial>
.\tools\native_tests.ps1 -Test stereo_mix -Device <serial>
```

The launch helper waits for the token-specific `Ready` marker under `MyAudioDebug` and reports AndroidRuntime failures. Native tests compile through the pinned NDK and run in `/data/local/tmp`, without changing app projects. Effect tests compile the large Airwindows catalogue. Latency measurements need real hardware; emulator audio timing is not acceptance evidence.

Native tests cover envelopes, sample-accurate loops, EQ/compression/pan, BS.1770 loudness, limiting, warmup boundaries, bleed removal, stereo compatibility and effect catalogue defaults. Logcat tags include `MyAudioProject`, `MyAudioEngine`, `MyAudioService`, `MyAudioCalibration` and `MyAudioPreview`.

## Optional Python tools

```powershell
.\setup.ps1 -SkipAndroid -WithPython -InstallTools
.venv\Scripts\python.exe tools\generate_icon.py
.venv\Scripts\python.exe tools\desktop_icon.py
.venv\Scripts\python.exe -m pip install -r tools\requirements-analysis.txt
.venv\Scripts\python.exe tools\analyze_bleed.py .work/<project> --output .work/<comparison> --cross-validate
.venv\Scripts\python.exe tools\test_prepare_public_repo.py
```

Keep analysis input/output directories distinct. The Python bleed comparison does not model the entire effect/master chain and only inventories takes with a recorded metronome. Never commit personal recordings or analysis artifacts.

Shared app resources live under `shared/src/commonMain/composeResources`. Airwindows English/German descriptions are maintained in `tools/airwindows_texts.tsv`, then generated with `tools/airwindows_texts.py`. Updating vendored effects with `tools/vendor_airwindows.py <external checkout>` requires reviewing provenance, notices and catalogue/native tests. Do not remove upstream authorship/license headers.

## License maintenance

`LICENSE` licenses original work. `licenses/THIRD_PARTY_NOTICES.md` inventories contributors and licenses; `licenses/` carries full upstream texts and source hashes. The About view is generated from these sources. When dependencies/media change, update provenance and credits alongside the code. Preserve shipped runtime legal files and fulfill source obligations before distribution.
