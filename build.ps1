<#
.SYNOPSIS
Builds Android APKs and/or a Windows MSI from a Windows x64 checkout; -Target WindowsStore builds the
unsigned MSIX for the Microsoft Store instead of the MSI.
.EXAMPLE
.\build.ps1 -Target All -InstallTools
.EXAMPLE
.\build.ps1 -Target Android
.EXAMPLE
.\build.ps1 -Target Android -AndroidConfiguration Release
#>
[CmdletBinding()]
param(
    [ValidateSet('Android', 'Windows', 'WindowsStore', 'All')][string]$Target = 'All',
    [ValidateSet('Debug', 'Release')][string]$AndroidConfiguration = 'Debug',
    [switch]$InstallTools,
    [switch]$AcceptLicenses,
    [switch]$Check,
    [string]$JdkHome,
    [string]$SdkRoot
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'tools/dev_environment.ps1')
$setupArguments = @{ Target = $(if ($Target -eq 'WindowsStore') { 'Windows' } else { $Target }); InstallTools = $InstallTools; AcceptLicenses = $AcceptLicenses; Check = $Check; JdkHome = $JdkHome; SdkRoot = $SdkRoot }
& (Join-Path $PSScriptRoot 'setup.ps1') @setupArguments
if ($Check) { return }
Push-Location $PSScriptRoot
try {
    $version = [regex]::Match((Get-Content 'gradle.properties' -Raw), '(?m)^myAudio.versionName=(.+)$').Groups[1].Value.Trim()
    New-Item -ItemType Directory -Force '.work' | Out-Null
    if ($Target -in @('Android', 'All')) {
        if ($AndroidConfiguration -eq 'Release') { & './tools/release_apk.ps1' }
        else {
            & './gradlew.bat' :app:assembleDebug --console=plain
            Assert-DevExit 'Android debug build' $LASTEXITCODE
            $output = ".work/MyAudio-$version-debug.apk"
            Copy-Item -LiteralPath 'app/build/outputs/apk/debug/app-debug.apk' -Destination $output -Force
            $hash = (Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash.ToLowerInvariant()
            "$hash *$(Split-Path $output -Leaf)" | Set-Content -LiteralPath "$output.sha256" -Encoding ascii
            Write-Host "Android debug APK: $output"
        }
    }
    if ($Target -in @('Windows', 'All')) { & './tools/release_desktop.ps1' }
    if ($Target -eq 'WindowsStore') { & './tools/package_msix.ps1' }
    Write-Host 'Build complete. Nothing was installed on a device, committed, pushed or published.'
} finally { Pop-Location }
