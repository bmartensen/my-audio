<#
.SYNOPSIS
Checks or installs Windows prerequisites for Android and Windows builds.
.DESCRIPTION
Without -InstallTools, missing prerequisites are reported, not installed. -Check only inspects
local files and never writes configuration or accesses the network. Android Studio and Python
are optional. Installers may request elevation; license acceptance remains interactive unless
-AcceptLicenses is explicitly provided. The supported host is Windows x64, PowerShell 5.1+.
#>
[CmdletBinding()]
param(
    [ValidateSet('Android', 'Windows', 'All')][string]$Target = 'All',
    [switch]$InstallTools,
    [switch]$Check,
    [switch]$AcceptLicenses,
    [string]$JdkHome,
    [string]$SdkRoot,
    [switch]$WithEmulator,
    [switch]$WithPython,
    [switch]$SkipPython,
    [switch]$SkipAndroid
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'tools/dev_environment.ps1')
if ($env:OS -ne 'Windows_NT' -or -not [Environment]::Is64BitProcess -or $env:PROCESSOR_ARCHITECTURE -ne 'AMD64') { throw 'Use 64-bit PowerShell on Windows x64. ARM and non-Windows setup are not implemented.' }
if ($Check -and $InstallTools) { throw 'Choose either -Check or -InstallTools.' }
if ($AcceptLicenses -and -not $InstallTools) { throw '-AcceptLicenses requires -InstallTools.' }
if ($WithPython -and $SkipPython) { throw 'Choose either -WithPython or -SkipPython.' }
if ($SkipAndroid) { $WithPython = -not $SkipPython }
$needsDesktop = -not $SkipAndroid -and $Target -in @('Windows', 'All')
$needsAndroid = -not $SkipAndroid -and $Target -in @('Android', 'All')
$missing = @()

if (-not $SkipAndroid) {
    $versions = Get-DevVersions $PSScriptRoot
    $jdk = Find-DevJdk $JdkHome $needsDesktop
    if (-not $jdk -and $InstallTools) {
        Install-DevWinget 'EclipseAdoptium.Temurin.21.JDK' $AcceptLicenses.IsPresent
        $jdk = Find-DevJdk '' $needsDesktop
    }
    if (-not $jdk) { $missing += 'JDK 21 x64 (including JNI headers; jpackage for Windows)' } else { Write-Host "JDK: $jdk" }
    $sdk = Get-DevSdk $PSScriptRoot $SdkRoot
    Write-Host "SDK: $sdk"
    $manager = Find-DevSdkManager $sdk
    if (-not $manager -and $InstallTools) {
        Install-DevCommandLineTools $PSScriptRoot $sdk $AcceptLicenses.IsPresent
        $manager = Find-DevSdkManager $sdk
    }
    if (-not $manager) { $missing += 'Android SDK command-line tools' }
    $packages = Get-DevPackages $versions $needsAndroid $WithEmulator.IsPresent
    $absent = @($packages.Keys | Where-Object { -not (Test-Path -LiteralPath (Join-Path $sdk $packages[$_])) })
    if ($absent.Count -and $InstallTools -and $jdk -and $manager) {
        $env:JAVA_HOME = $jdk
        Write-Host "Installing SDK packages: $($absent -join ', ')"
        if ($AcceptLicenses) { 1..100 | ForEach-Object { 'y' } | & $manager "--sdk_root=$sdk" --licenses }
        else { & $manager "--sdk_root=$sdk" --licenses }
        Assert-DevExit 'Android SDK license review' $LASTEXITCODE
        & $manager "--sdk_root=$sdk" @absent
        Assert-DevExit 'Android SDK package installation' $LASTEXITCODE
        $absent = @($packages.Keys | Where-Object { -not (Test-Path -LiteralPath (Join-Path $sdk $packages[$_])) })
    }
    $missing += $absent
    if ($needsDesktop) {
        $vs = Find-DevVisualStudio
        if (-not $vs -and $InstallTools) {
            $finder = "${env:ProgramFiles(x86)}/Microsoft Visual Studio/Installer/vswhere.exe"
            $existing = if (Test-Path -LiteralPath $finder) { & $finder -latest -products * -property installationPath } else { $null }
            if ($existing) {
                Write-Host "Adding the C++ workload to Visual Studio at $existing."
                $installer = Join-Path (Split-Path $finder -Parent) 'setup.exe'
                $arguments = 'modify --installPath "' + $existing.Trim() + '" --add Microsoft.VisualStudio.Workload.VCTools --includeRecommended --passive --norestart'
                $process = Start-Process -FilePath $installer -ArgumentList $arguments -Verb RunAs -WindowStyle Hidden -Wait -PassThru
                Assert-DevExit 'Visual Studio C++ workload setup' $process.ExitCode
            } else {
                Install-DevWinget 'Microsoft.VisualStudio.BuildTools' $AcceptLicenses.IsPresent '--passive --wait --norestart --add Microsoft.VisualStudio.Workload.VCTools --includeRecommended'
            }
            $vs = Find-DevVisualStudio
        }
        if (-not $vs) { $missing += 'Visual Studio C++ build tools and Windows SDK (Desktop development with C++ workload)' } else { Write-Host "C++: $vs" }
    }
}

# Python is only needed for optional artwork/analysis tools, never for compiling the applications.
if ($WithPython -and -not $SkipPython) {
    $python = Join-Path $PSScriptRoot '.venv/Scripts/python.exe'
    if (-not (Test-Path -LiteralPath $python) -and $InstallTools) {
        $py = Get-Command py.exe -ErrorAction SilentlyContinue
        if (-not $py) { throw 'Optional Python setup needs the Python install manager (https://www.python.org/downloads/). Install it and rerun -WithPython -InstallTools, or omit -WithPython to build the applications.' }
        & py.exe install 3.14
        Assert-DevExit 'Python installation' $LASTEXITCODE
        & py.exe -V:3.14 -m venv (Join-Path $PSScriptRoot '.venv')
        Assert-DevExit 'Python virtual environment creation' $LASTEXITCODE
    }
    if (-not (Test-Path -LiteralPath $python)) { $missing += 'Optional Python .venv (use -WithPython -InstallTools)' }
    elseif (-not $Check) {
        & $python -m pip install -r (Join-Path $PSScriptRoot 'requirements.txt')
        Assert-DevExit 'Optional Python requirements' $LASTEXITCODE
    }
}
if ($missing.Count) { throw "Missing prerequisites:`n - $($missing -join "`n - ")`nRun .\setup.ps1 -Target $Target -InstallTools, or install the listed tools manually. See .wiki/development.md." }
if ($Check) { Write-Host 'Prerequisite check passed. No files, environment or installed software changed.'; return }
if (-not $SkipAndroid) {
    $env:JAVA_HOME = $jdk
    $env:MY_AUDIO_JDK = $jdk
    $env:ANDROID_HOME = $sdk
    $local = Join-Path $PSScriptRoot 'local.properties'
    if (-not (Test-Path -LiteralPath $local)) { [IO.File]::WriteAllText($local, "sdk.dir=$(ConvertTo-DevPropertyValue $sdk)`n", [Text.Encoding]::ASCII) }
    Write-Host 'Build environment ready. JAVA_HOME and ANDROID_HOME are set for this process; global settings were not changed.'
}
