# Development

How to set up, build, test and publish My Audio. Rules for branches, releases and verification are in [repository rules](repository-rules.md).

Supported build host: Windows x64, 64-bit PowerShell 5.1 or newer. The helper builds Android APKs and a Windows MSI; it does not install either application. No publisher credentials are needed.

## Quick start

Install Git to obtain the checkout. For automatic prerequisite installation, Windows Package Manager (`winget`) must be available through Microsoft's App Installer. Then:

```powershell
.\build.ps1 -Target All -Check
.\build.ps1 -Target All -InstallTools
```

`-Check` inspects local prerequisites without network requests, file changes or environment changes. It reports missing tools and exits unsuccessfully when setup is incomplete. `-InstallTools` explicitly permits installing missing prerequisites. Without it, normal setup reports missing tools instead of installing them. Administrative installers may request elevation. License prompts are interactive; add `-AcceptLicenses` only after reviewing and accepting the relevant vendor terms.

Use `-Target Android` or `-Target Windows` for one platform. The first native build compiles the large effect library and can take several minutes. Gradle, Maven and CMake download dependencies. Subsequent builds reuse caches. An interrupted setup can be rerun; a requested reboot must be completed first.

The helper produces `.work/MyAudio-<version>-debug.apk` and `.work/MyAudio-<version>.msi`, with SHA-256 files. `-Target WindowsStore` produces `.work/MyAudio-<version>.msix` for the Microsoft Store instead of the MSI. Android supports arm64-v8a and x86_64; Windows is x64.

If script execution is blocked, inspect the script and use a process-local override rather than changing machine policy:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -Target All -InstallTools
```

## Tool discovery and installation

| Tool | Requirement / setup |
|---|---|
| Java | JDK 21 x64 with JNI headers; Windows packaging also needs `jpackage`. Automatic setup uses Eclipse Temurin 21 through winget. |
| Android SDK | Command-line tools and platform-tools; platform 37.0, build-tools 37.0.0, NDK 30.0.16248370 and CMake 4.1.2 are pinned by `app/build.gradle.kts`. NDK is only needed for Android. |
| Windows C++ | Visual Studio Build Tools with “Desktop development with C++”, including the Windows SDK. Setup discovers installations with vswhere and adds the workload if needed. |
| MSI tooling | The Compose Gradle plugin downloads WiX and uses the selected JDK's `jpackage`. |
| MSIX tooling | `makeappx`, `makepri` and `signtool` from the Windows SDK of the C++ workload. The App Certification Kit comes with the Windows SDK. |
| Optional tools | Android Studio for IDE/emulator workflows; Python for artwork, analysis and export checks. Neither is required to compile the applications. |

SDK command-line tools use a pinned official download with a checked SHA-256. Package versions come from the build file. Setup does not change system-wide JAVA_HOME/PATH. It creates ignored `local.properties` only when absent, and does not overwrite an existing SDK choice. Java is selected from explicit arguments, process settings and installed JDKs; Java on PATH is not assumed.

```powershell
.\setup.ps1 -Target All -JdkHome 'C:\Tools\jdk-21' -SdkRoot 'C:\Tools\AndroidSdk'
.\setup.ps1 -Target Android -InstallTools -WithEmulator
.\setup.ps1 -SkipAndroid -WithPython -InstallTools
```

An explicit SDK path conflicting with `local.properties` fails with instructions to review that file. Optional Python setup uses the Python install manager (`py`), creates `.venv` and installs `requirements.txt`; the manager itself must be installed separately. `-WithEmulator` installs the emulator tool, not a system image or AVD. Use Android Studio's Device Manager or sdkmanager/avdmanager to create a device.

Manual fallback: install [Temurin 21](https://adoptium.net/temurin/releases/?version=21), [Android command-line tools](https://developer.android.com/studio#command-tools) and [Visual Studio Build Tools](https://visualstudio.microsoft.com/downloads/), select the C++ workload, and rerun `-Check`.

## Build and test

```powershell
.\setup.ps1 -Target All
.\tools\test_dev_environment.ps1
.\gradlew.bat :app:assembleDebug :shared:desktopTest :app:testDebugUnitTest :app:lintDebug
.\build.ps1 -Target Windows
```

Setup selects JAVA_HOME and ANDROID_HOME in the current process. Keep IDE/JDK selections consistent; `.idea/`, `.vscode/` and `local.properties` are local. `gradle.properties` is the shared source of versionName/versionCode for Android and Windows; raise the patch version and versionCode before every distributed build.

```powershell
.\tools\build_desktop_native.ps1
.\tools\release_apk.ps1
.\tools\release_desktop.ps1
.\gradlew.bat :desktop:run "-PmyAudio.openProject=<id>"
```

Outputs, logs, screenshots and personal test projects stay in ignored `.work/`. APK release packaging verifies its configured signing certificate. MSI redistribution requires the matching runtime notices and corresponding source described under [distribution and licenses](#distribution-and-licenses).

For Pages, guide or download-link changes, also run `tools/test_site_downloads.ps1` and `tools/check_site_downloads.ps1`; verify images, internal links and redirects in German and English at phone and desktop widths ([Pages and releases](site-publication.md)).

### Device checks

Only run installation/device commands when authorized for that device. Replacement installs preserve data only when signatures are compatible; never resolve a mismatch by automatic uninstall.

```powershell
.\tools\debug_launch.ps1 -Target emulator
.\tools\debug_launch.ps1 -Target phone -Device <serial>
.\tools\debug_launch.ps1 -NoBuild -Screen calibration
.\tools\debug_launch.ps1 -ScreenshotPath .work/home.png
.\tools\native_tests.ps1 -Device <serial>
.\tools\native_tests.ps1 -Test stereo_mix -Device <serial>
```

The launch helper waits for the token-specific `Ready` marker under `MyAudioDebug` and reports AndroidRuntime failures. Native tests compile through the pinned NDK and run in `/data/local/tmp`, without changing app projects. Effect tests compile the large Airwindows catalogue. Latency measurements need real hardware; emulator audio timing says nothing about real devices.

Native tests cover envelopes, sample-accurate loops, EQ/compression/pan, BS.1770 loudness, limiting, warmup boundaries, bleed removal, stereo compatibility and effect catalogue defaults. Logcat tags include `MyAudioProject`, `MyAudioEngine`, `MyAudioService`, `MyAudioCalibration` and `MyAudioPreview`.

### UI refinement and release candidates

1. Iterate on layout and interaction with debug builds in the emulator. Rebuild and replace the debug app there without incrementing the release version for each visual adjustment. Check screenshots with representative multitrack projects, portrait/landscape, light/dark themes and relevant navigation modes. Keep destructive actions to disposable demo data.
2. Prepare a release candidate with the next shared versionName/versionCode and build the Android release APK and Windows installer. Preserve every previously distributed package; further changes after a candidate has been installed require a new version.
3. Install the release build as an update on the contributor's connected smartphone when they ask for it. Any contributor's compatible phone can serve this role; select the intended device explicitly, no model or serial is a prerequisite. Use the contributor's configured signing identity, verify update compatibility and preserve their projects. Never substitute a debug installation over their release app.

Package preparation and installation do not authorize a commit, push or publication.

### Optional Python tools

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

## Android development

Iterate with debug builds in the emulator, then install the release build as an update on the contributor's connected smartphone ([UI refinement and release candidates](#ui-refinement-and-release-candidates)). A Pixel is not required.

```powershell
.\build.ps1 -Target Android
# Or initialize the current shell and use Gradle directly:
.\setup.ps1 -Target Android
.\gradlew.bat :app:assembleDebug
```

Gradle generates a local debug keystore when needed. No release credentials or expected-certificate fingerprint are required. The direct Gradle artifact is `app/build/outputs/apk/debug/app-debug.apk`.

Debug and release share `com.martensening.my_audio`. A debug APK cannot replace an installed APK signed by a different key. Prefer a separate emulator/test device. Never automatically uninstall the installed app or change its application ID to bypass the mismatch: uninstalling can destroy local projects. Back up projects before any deliberate uninstall.

## Optional developer-owned Android signing

Keep the keystore outside the checkout and outside any other repository, for example in `%USERPROFILE%\.gradle\signing\` next to the user Gradle properties, and keep a backup elsewhere: without it, installed apps cannot be updated. Configure all four neutral values, using process environment variables or `%USERPROFILE%\.gradle\gradle.properties`:

| Environment variable | User Gradle property |
|---|---|
| `MY_AUDIO_STORE_FILE` | `myAudio.signing.storeFile` |
| `MY_AUDIO_STORE_PASSWORD` | `myAudio.signing.storePassword` |
| `MY_AUDIO_KEY_ALIAS` | `myAudio.signing.keyAlias` |
| `MY_AUDIO_KEY_PASSWORD` | `myAudio.signing.keyPassword` |

Use an absolute keystore path; forward slashes avoid properties-file escaping problems. A nonblank environment value overrides the corresponding neutral property. If any neutral value is supplied, the neutral profile must be complete: it is never filled from another profile. When no neutral values exist, the compatible user-level `martenseningStoreFile`, `martenseningStorePassword`, `martenseningKeyAlias` and `martenseningKeyPassword` profile is recognized. No identity, domain, alias or key path is hard-coded.

Obtain passwords from a local secret manager or prompt. Do not put passwords in the repository, shell history, command-line `-P` arguments, examples or logs. Other developers can create their own Android keystore following the [Android signing guide](https://developer.android.com/studio/publish/app-signing); the publisher's key must not be shared.

```powershell
.\build.ps1 -Target Android -AndroidConfiguration Release
# Equivalent release packaging after setup:
.\tools\release_apk.ps1
```

The release helper requires a complete profile, derives its certificate fingerprint from the keystore and verifies the built APK with apksigner before copying it to `.work/MyAudio-<version>.apk`. `MY_AUDIO_SIGNING_CERT_SHA256` can additionally pin an expected fingerprint (64 hexadecimal characters, no separators). It never silently substitutes debug signing. A direct `:app:assembleRelease` without a complete profile produces an unsigned release APK; that is not installable until signed.

For an update, the signing identity must match the installed app. Keep your key backed up privately. A build signed with your key is your build, not an official publisher release.

## Windows development and packaging

```powershell
.\build.ps1 -Target Windows
.\setup.ps1 -Target Windows
.\gradlew.bat :desktop:run
```

The native build discovers Visual Studio and the pinned CMake/Ninja installation; JNI comes from the selected JDK. `:desktop:packageMsi` creates a per-user installer under `desktop/build/compose/binaries/main/msi`. `tools/release_desktop.ps1` copies it and its hash into `.work`. No code-signing certificate is required; the MSI is unsigned and may trigger Windows reputation prompts. Projects live in Documents/My Audio and are retained on uninstall. Windows installs a new MSI over an older version but not over the identical version, so every distributed build needs a new version. To test a rebuilt MSI locally under the same version, uninstall the installed build first, then install; a repair install (`REINSTALL=ALL`) does not reliably replace the application files. Projects in Documents/My Audio are kept.

## Microsoft Store package

```powershell
.\build.ps1 -Target WindowsStore
```

`tools/package_msix.ps1` packs the app image of `:desktop:createDistributable` (the same app and Java runtime as the MSI) into `.work/MyAudio-<version>.msix` with a SHA-256 file. The manifest template is `desktop/msix/AppxManifest.xml`; the script fills the identity from `myAudio.store.*` in `gradle.properties` and the version as `<versionName>.0`, copies the logos from `branding/microsoft-store/package` (made by `tools/prepare_store_assets.py`) and indexes their scale variants into `resources.pri`. The package declares `runFullTrust` and `microphone`. It stays unsigned: Partner Center accepts it and Microsoft signs it. A fork submits under its own identity by overriding the three `myAudio.store.*` values.

Projects stay in Documents/My Audio, which MSIX does not redirect, so the Store app and the MSI share them. The Store requires a higher version for every submission.

**Local test.** Windows installs only signed packages. Either switch on developer mode and register the unpacked layout, or sign a copy with a self-made certificate whose subject equals the publisher:

```powershell
# Developer mode (Settings > System > For developers), no certificate:
Add-AppxPackage -Register desktop\build\msix\layout\AppxManifest.xml

# Or a test certificate in the user's certificate store, without a PFX or password:
$cert = New-SelfSignedCertificate -Type Custom -Subject '<myAudio.store.publisher>' -FriendlyName 'My Audio MSIX test' `
  -KeyUsage DigitalSignature -CertStoreLocation Cert:\CurrentUser\My -NotAfter (Get-Date).AddYears(1) `
  -TextExtension @('2.5.29.37={text}1.3.6.1.5.5.7.3.3', '2.5.29.19={text}')
Export-Certificate $cert -FilePath $HOME\.my-audio\msix-test.cer
.\tools\package_msix.ps1 -SkipBuild -CertificateThumbprint $cert.Thumbprint   # .work\MyAudio-<version>-local.msix
# Once, in an elevated PowerShell: trust the certificate for package installs.
Import-Certificate $HOME\.my-audio\msix-test.cer -CertStoreLocation Cert:\LocalMachine\TrustedPeople
Add-AppxPackage .work\MyAudio-<version>-local.msix
```

`-CertificatePath` signs with a PFX instead, its password in `MY_AUDIO_MSIX_CERT_PASSWORD`.

**Before installing the Store version,** remove the test package, which has the same identity, and the test certificate. Projects in Documents/My Audio stay:

```powershell
Get-AppxPackage 58153Martensening.com.MyAudio | Remove-AppxPackage
Get-ChildItem Cert:\CurrentUser\My | Where-Object FriendlyName -eq 'My Audio MSIX test' | Remove-Item
# Elevated:
Get-ChildItem Cert:\LocalMachine\TrustedPeople | Where-Object Subject -eq '<myAudio.store.publisher>' | Remove-Item
```

Before a submission, run the Windows App Certification Kit in an elevated PowerShell on the signed copy:

```powershell
& "${env:ProgramFiles(x86)}\Windows Kits\10\App Certification Kit\appcert.exe" test -appxpackagepath .work\MyAudio-<version>-local.msix -reportoutputpath .work\wack-<version>.xml
```

## Publishing a version

The release itself follows [branches and releases](repository-rules.md#branches-and-releases). Building the candidate, the runtime source, the GitHub release, the download links on Pages and the Microsoft Store submission are in [publishing a version](site-publication.md#publishing-a-version); the Store fields are in [Microsoft Store](microsoft-store.md).

### Publication audit

In addition to the [publication checks](publication-checks.md), `tools/prepare_public_repo.py` (Python 3.10+, Git) inventories tracked and non-ignored files, rejects private names, unexpected binaries, symlinks/junctions and common secret patterns, and writes a SHA-256 manifest. With `--destination` it copies the reviewed files into an empty directory.

```powershell
python tools/prepare_public_repo.py --manifest .work/public-manifest.json
python tools/prepare_public_repo.py --manifest .work/public-manifest.json --destination .work/public-candidate
```

## Distribution and licenses

The MIT license covers original project work. Preserve the third-party texts and About license view when distributing builds. LAME, the MP3 encoder, is LGPL-2.0-or-later: keep it a separate shared library (`libmp3lame.so`, `mp3lame.dll`) and distribute its source, `app/src/main/cpp/third_party/lame`, with the binaries; a repository release carries it in its source archive. The MSI carries the selected Java runtime's `runtime/legal` directory; do not strip it. OpenJDK is GPLv2 with the Classpath Exception, and distributing its binary also requires a corresponding-source distribution route under those terms. An upstream link alone is not a substitute for meeting those obligations.

For Temurin, obtain the **matching full source archive** from the release in [temurin21-binaries](https://github.com/adoptium/temurin21-binaries/releases), check its published checksum, and provide it with the binary distribution (including runtime build scripts/configuration from that release). Keep the exact JDK version, source archive and checksums with release records outside Git; `tools/publish_github_release.ps1` attaches the verified archive to each GitHub release (see [Pages and releases](site-publication.md#publishing-a-version)). The JDK's `src.zip` alone is not the complete corresponding source. A different JDK distribution requires reviewing its own notices and source terms.

`LICENSE` licenses original work. `licenses/THIRD_PARTY_NOTICES.md` inventories contributors and licenses; `licenses/` carries full upstream texts and source hashes. The About view is generated from these sources. When dependencies/media change, update provenance and credits alongside the code. Preserve shipped runtime legal files and fulfill source obligations before distribution.

See [license inventory](../licenses/THIRD_PARTY_NOTICES.md) and [license provenance](../licenses/README.md). Android SDK/Visual Studio tool installers retain their vendor terms and are downloaded locally, not redistributed in this repository.
