<#
.SYNOPSIS
Builds the native unit tests under tools/*_test.cpp with the project NDK and runs them on a device.

.DESCRIPTION
The tests include engine headers from app/src/main/cpp and run as plain executables in
/data/local/tmp; app data is not touched. Without -Device, the single connected phone is used
(the Pixel 9 target system). Binaries go to .work/.

.EXAMPLE
.\tools\native_tests.ps1
.\tools\native_tests.ps1 -Device <serial> -Test stream_warmup
#>
[CmdletBinding()]
param(
    [string]$Device,
    # Base name without _test.cpp; all tests by default.
    [string]$Test
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'dev_environment.ps1')
$sdk = Get-DevSdk $repo ''
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$ndk = [regex]::Match((Get-Content (Join-Path $repo 'app\build.gradle.kts') -Raw), 'ndkVersion\s*=\s*"([^"]+)"').Groups[1].Value
$clang = Join-Path $sdk "ndk\$ndk\toolchains\llvm\prebuilt\windows-x86_64\bin\clang++.exe"
if (-not (Test-Path $clang)) { throw "NDK $ndk not found; run .\setup.ps1." }

if (-not $Device) {
    $phones = @(& $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '^(\S+)\s+device$' -and $_ -notmatch '^emulator-' } |
        ForEach-Object { ($_ -split '\s+')[0] })
    if ($phones.Count -ne 1) { throw "Expected exactly one connected phone, found $($phones.Count); pass -Device <serial>." }
    $Device = $phones[0]
}

$sources = Get-ChildItem (Join-Path $repo 'tools') -Filter '*_test.cpp' |
    Where-Object { -not $Test -or $_.BaseName -eq "${Test}_test" }
if (-not $sources) { throw "No native test matches '$Test'." }
New-Item -ItemType Directory -Force (Join-Path $repo '.work') | Out-Null

$failed = @()
foreach ($source in $sources) {
    $name = $source.BaseName -replace '_', '-'
    $binary = Join-Path $repo ".work\$name"
    $remote = "/data/local/tmp/my-audio-$name"
    $extraSources = @()
    $extraFlags = @()
    if ($source.BaseName -in @('stereo_mix_test', 'airwindows_test', 'live_chain_test')) {
        # The mix links the Airwindows effects of the effect library (slow to compile: ~1400 files).
        $airwindows = Join-Path $repo 'app\src\main\cpp\third_party\airwindows'
        $extraSources = @('ProjectMix.cpp', 'Wav.cpp', 'TrackProcessing.cpp', 'BleedRemoval.cpp', 'Metronome.cpp',
            'AirwindowsEffects.cpp', 'LiveChain.cpp') | ForEach-Object { Join-Path $repo "app\src\main\cpp\$_" }
        $extraSources += Join-Path $airwindows 'airwin_consolidated_base.cpp'
        $extraSources += (Get-ChildItem (Join-Path $airwindows 'autogen_airwin') -Filter '*.cpp').FullName
        $extraFlags = @('-O2', '-w', '-llog', "-I$airwindows")
    }
    & $clang --target=aarch64-linux-android29 -static-libstdc++ -std=c++17 $source.FullName @extraSources @extraFlags -o $binary
    if ($LASTEXITCODE -ne 0) { throw "Compiling $($source.Name) failed." }
    & $adb -s $Device push $binary $remote | Out-Null
    & $adb -s $Device shell chmod 755 $remote
    & $adb -s $Device shell $remote
    if ($LASTEXITCODE -eq 0) { Write-Host "PASS $($source.Name)" } else { Write-Host "FAIL $($source.Name)"; $failed += $source.Name }
}
if ($failed) { throw "Native tests failed: $($failed -join ', ')" }
