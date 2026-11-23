# Repository commands and working conventions

Policy is in [AGENTS.md](../AGENTS.md); setup and optional signing are in [developer builds](developer-builds.md).

## Git and project memory

Branch, remote and push rules are in [AGENTS.md](../AGENTS.md). Update the wiki alongside meaningful changes: decisions newest-first, backlog with open work only, current work with evidence and limitations. User-visible changes go into CHANGELOG.md. Shared workflows: [.agents](../.agents/README.md).

## Build and automated checks

```powershell
.\setup.ps1 -Target All
.\tools\test_dev_environment.ps1
.\gradlew.bat :app:assembleDebug :shared:desktopTest :app:testDebugUnitTest :app:lintDebug
.\build.ps1 -Target Windows
```

Setup selects JAVA_HOME and ANDROID_HOME in the current process. Keep IDE/JDK selections consistent; `.idea/`, `.vscode/` and `local.properties` are local. `gradle.properties` is the shared source of versionName/versionCode for Android and Windows. Raise the patch version and versionCode before every distributed build. Each version gets an annotated tag `v<versionName>` (e.g. `v0.8.6`) on the commit it was built from, pushed together with that commit.

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
