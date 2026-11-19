<#
.SYNOPSIS
Builds the Windows installer of the desktop app and copies it to .work\MyAudio-<version>.msi.

.DESCRIPTION
Runs :desktop:packageMsi (native engine with MSVC, runtime image and jpackage from Temurin 21,
WiX is downloaded by the Compose plugin) and writes a SHA-256 file next to the copy. The version
comes from gradle.properties and is shared with the Android app; raise it before handing out a
new installer, otherwise Windows refuses to install over the same version.
#>
param(
    [string]$OutputDir = '.work'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
. (Join-Path $PSScriptRoot 'dev_environment.ps1')
$env:JAVA_HOME = Find-DevJdk $env:MY_AUDIO_JDK $true
if (-not $env:JAVA_HOME) { throw 'JDK 21 with jpackage not found; run setup.ps1 -Target Windows -InstallTools.' }
$env:MY_AUDIO_JDK = $env:JAVA_HOME
$env:ANDROID_HOME = Get-DevSdk $root ''

$version = (Select-String -Path (Join-Path $root 'gradle.properties') -Pattern '^myAudio\.versionName=(.+)$').Matches[0].Groups[1].Value.Trim()
& .\gradlew.bat :desktop:packageMsi --console=plain
if ($LASTEXITCODE -ne 0) { throw "packageMsi failed ($LASTEXITCODE)." }

$msi = Get-ChildItem (Join-Path $root 'desktop\build\compose\binaries\main\msi') -Filter '*.msi' |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $msi) { throw 'No MSI was produced.' }
$outDir = if ([IO.Path]::IsPathRooted($OutputDir)) { $OutputDir } else { Join-Path $root $OutputDir }
New-Item -ItemType Directory -Force $outDir | Out-Null
$target = Join-Path $outDir "MyAudio-$version.msi"
Copy-Item $msi.FullName $target -Force
$hash = (Get-FileHash $target -Algorithm SHA256).Hash.ToLower()
Set-Content -Path "$target.sha256" -Value "$hash *MyAudio-$version.msi" -Encoding ascii
Write-Output $target
Write-Output "SHA-256 $hash"
