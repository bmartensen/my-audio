# Developer builds

Supported build host: Windows x64, 64-bit PowerShell 5.1 or newer. The helper builds Android APKs and a Windows MSI; it does not install either application. No publisher credentials are needed.

## Quick start

Install Git to obtain the checkout. For automatic prerequisite installation, Windows Package Manager (`winget`) must be available through Microsoft's App Installer. Then:

```powershell
.\build.ps1 -Target All -Check
.\build.ps1 -Target All -InstallTools
```

`-Check` inspects local prerequisites without network requests, file changes or environment changes. It reports missing tools and exits unsuccessfully when setup is incomplete. `-InstallTools` explicitly permits installing missing prerequisites. Without it, normal setup reports missing tools instead of installing them. Administrative installers may request elevation. License prompts are interactive; add `-AcceptLicenses` only after reviewing and accepting the relevant vendor terms.

Use `-Target Android` or `-Target Windows` for one platform. The first native build compiles the large effect library and can take several minutes. Gradle, Maven and CMake download dependencies. Subsequent builds reuse caches. An interrupted setup can be rerun; a requested reboot must be completed first.

The helper produces `.work/MyAudio-<version>-debug.apk` and `.work/MyAudio-<version>.msi`, with SHA-256 files. Android supports arm64-v8a and x86_64; Windows is x64.

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
| Optional tools | Android Studio for IDE/emulator workflows; Python for artwork, analysis and export checks. Neither is required to compile the applications. |

SDK command-line tools use a pinned official download with a checked SHA-256. Package versions come from the build file. Setup does not change system-wide JAVA_HOME/PATH. It creates ignored `local.properties` only when absent, and does not overwrite an existing SDK choice. Java is selected from explicit arguments, process settings and installed JDKs; Java on PATH is not assumed.

```powershell
.\setup.ps1 -Target All -JdkHome 'C:\Tools\jdk-21' -SdkRoot 'C:\Tools\AndroidSdk'
.\setup.ps1 -Target Android -InstallTools -WithEmulator
.\setup.ps1 -SkipAndroid -WithPython -InstallTools
```

An explicit SDK path conflicting with `local.properties` fails with instructions to review that file. Optional Python setup uses the Python install manager (`py`), creates `.venv` and installs `requirements.txt`; the manager itself must be installed separately. `-WithEmulator` installs the emulator tool, not a system image or AVD. Use Android Studio's Device Manager or sdkmanager/avdmanager to create a device.

Manual fallback: install [Temurin 21](https://adoptium.net/temurin/releases/?version=21), [Android command-line tools](https://developer.android.com/studio#command-tools) and [Visual Studio Build Tools](https://visualstudio.microsoft.com/downloads/), select the C++ workload, and rerun `-Check`.

## Android development

Use the [UI refinement and release candidate workflow](repository-rules.md#ui-refinement-and-release-candidates): iterate with debug builds in the emulator, then install the release build as an update on the contributor's connected smartphone. A Pixel is not required.

```powershell
.\build.ps1 -Target Android
# Or initialize the current shell and use Gradle directly:
.\setup.ps1 -Target Android
.\gradlew.bat :app:assembleDebug
```

Gradle generates a local debug keystore when needed. No release credentials or expected-certificate fingerprint are required. The direct Gradle artifact is `app/build/outputs/apk/debug/app-debug.apk`.

Debug and release share `com.martensening.my_audio`. A debug APK cannot replace an installed APK signed by a different key. Prefer a separate emulator/test device. Never automatically uninstall the installed app or change its application ID to bypass the mismatch: uninstalling can destroy local projects. Back up projects before any deliberate uninstall.

## Optional developer-owned Android signing

Keep the keystore outside the checkout. Configure all four neutral values, using process environment variables or `%USERPROFILE%\.gradle\gradle.properties`:

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

## Distribution and licenses

The MIT license covers original project work. Preserve the third-party texts and About license view when distributing builds. The MSI carries the selected Java runtime's `runtime/legal` directory; do not strip it. OpenJDK is GPLv2 with the Classpath Exception, and distributing its binary also requires a corresponding-source distribution route under those terms. An upstream link alone is not a substitute for meeting those obligations.

For Temurin, obtain the **matching full source archive** from the release in [temurin21-binaries](https://github.com/adoptium/temurin21-binaries/releases), check its published checksum, and provide it with the binary distribution (including runtime build scripts/configuration from that release). Keep the exact JDK version, source archive and checksums with release records outside Git; `tools/publish_github_release.ps1` attaches the verified archive to each GitHub release (see [Pages and releases](site-publication.md#publishing-a-version)). The JDK's `src.zip` alone is not the complete corresponding source. A different JDK distribution requires reviewing its own notices and source terms.

See [license inventory](../licenses/THIRD_PARTY_NOTICES.md) and [license provenance](../licenses/README.md). Android SDK/Visual Studio tool installers retain their vendor terms and are downloaded locally, not redistributed in this repository.
