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

## Device checks

Only run installation/device commands when authorized for that device. Replacement installs preserve data only when signatures are compatible; never resolve a mismatch by automatic uninstall.

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
