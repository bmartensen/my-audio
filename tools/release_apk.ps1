<#
.SYNOPSIS
Builds the signed release APK for sideloading and copies it to .work\MyAudio-<version>.apk.

.DESCRIPTION
Uses the developer's locally configured signing profile (myAudio.signing.* properties or
MY_AUDIO_* environment variables; compatible martensening* properties are supported).
Verifies the APK certificate against that keystore. MY_AUDIO_SIGNING_CERT_SHA256 can pin an
additional expected fingerprint. Missing credentials never silently produce a debug release.

.EXAMPLE
.\tools\release_apk.ps1
#>
[CmdletBinding()]
param([string]$OutputDir = '.work')
$ErrorActionPreference = 'Stop'
$taskRepo = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'dev_environment.ps1')
$taskSdk = Get-DevSdk $taskRepo ''
$taskVersions = Get-DevVersions $taskRepo
$taskExpectedPin = $env:MY_AUDIO_SIGNING_CERT_SHA256
if ($taskExpectedPin -and $taskExpectedPin -notmatch '^[0-9a-fA-F]{64}$') {
    throw 'MY_AUDIO_SIGNING_CERT_SHA256 must contain 64 hexadecimal characters.'
}
$taskBuildTools = Get-Item -LiteralPath (Join-Path $taskSdk "build-tools/$($taskVersions.BuildTools)")
if (-not $taskBuildTools) { throw 'Android SDK build-tools not found.' }

Push-Location $taskRepo
try {
    $env:JAVA_HOME = Find-DevJdk $env:MY_AUDIO_JDK $false
    if (-not $env:JAVA_HOME) { throw 'JDK 21 not found; run setup.ps1 -Target Android -InstallTools.' }
    $env:ANDROID_HOME = $taskSdk
    & .\gradlew.bat :app:writeReleaseSigningFingerprint :app:assembleRelease --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
    $taskApk = 'app/build/outputs/apk/release/app-release.apk'
    if (-not (Test-Path -LiteralPath $taskApk)) {
        throw 'Release APK is unsigned. Configure a complete local signing profile; see .wiki/developer-builds.md.'
    }
    $taskCerts = & (Join-Path $taskBuildTools.FullName 'apksigner.bat') verify --print-certs $taskApk
    if ($LASTEXITCODE -ne 0) { throw 'Signature verification failed.' }
    $taskCert = ($taskCerts | Select-String 'certificate SHA-256 digest: ([0-9a-f]{64})').Matches[0].Groups[1].Value
    $taskExpectedCert = (Get-Content -LiteralPath 'app/build/signing/release-certificate.sha256' -Raw).Trim()
    if ($taskExpectedPin -and $taskCert -ne $taskExpectedPin) { throw 'APK certificate differs from MY_AUDIO_SIGNING_CERT_SHA256.' }
    if ($taskCert -ne $taskExpectedCert) { throw "APK is signed with an unexpected certificate ($taskCert)." }
    $taskVersion = ((& (Join-Path $taskBuildTools.FullName 'aapt.exe') dump badging $taskApk | Select-String "versionName='([^']+)'").Matches[0].Groups[1].Value)
    New-Item -ItemType Directory -Force $OutputDir | Out-Null
    # Relative to the repository, not to the .NET working directory of the calling shell.
    $taskOutput = [IO.Path]::GetFullPath((Join-Path (Resolve-Path -LiteralPath $OutputDir) "MyAudio-$taskVersion.apk"))
    Copy-Item -LiteralPath $taskApk -Destination $taskOutput -Force
    $taskHash = (Get-FileHash -LiteralPath $taskOutput -Algorithm SHA256).Hash.ToLowerInvariant()
    "$taskHash *$(Split-Path $taskOutput -Leaf)" | Set-Content -LiteralPath "$taskOutput.sha256" -Encoding ascii
    Write-Output $taskOutput
} finally {
    Pop-Location
}
