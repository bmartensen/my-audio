# Shared Windows development-tool discovery. Dot-source this file; it has no setup side effects.
function Get-DevVersions([string]$Root) {
    $text = Get-Content (Join-Path $Root 'app/build.gradle.kts') -Raw
    $api = [regex]::Match($text, 'compileSdk\s*=\s*(\d+)').Groups[1].Value
    $ndk = [regex]::Match($text, 'ndkVersion\s*=\s*"([^"]+)"').Groups[1].Value
    $cmake = [regex]::Match($text, 'cmake\s*\{[^}]*version\s*=\s*"([^"]+)"').Groups[1].Value
    $buildTools = [regex]::Match($text, 'buildToolsVersion\s*=\s*"([^"]+)"').Groups[1].Value
    if (-not $api -or -not $ndk -or -not $cmake -or -not $buildTools) { throw 'Cannot read SDK versions from app/build.gradle.kts.' }
    $platform = if ([int]$api -ge 37) { "$api.0" } else { $api }
    [pscustomobject]@{ Api = $api; Platform = $platform; Ndk = $ndk; CMake = $cmake; BuildTools = $buildTools }
}

function Test-DevJdk([string]$Path, [bool]$Packaging = $false) {
    if (-not $Path -or -not (Test-Path -LiteralPath (Join-Path $Path 'release'))) { return $false }
    $release = Get-Content -LiteralPath (Join-Path $Path 'release') -Raw
    if ($release -notmatch 'JAVA_VERSION="21[.\+"]' -or $release -notmatch 'OS_ARCH="(?:amd64|x86_64)"') { return $false }
    $required = @('bin/java.exe', 'bin/javac.exe', 'include/jni.h')
    if ($Packaging) { $required += 'bin/jpackage.exe' }
    foreach ($file in $required) { if (-not (Test-Path -LiteralPath (Join-Path $Path $file))) { return $false } }
    return $true
}

function Find-DevJdk([string]$Preferred, [bool]$Packaging = $false) {
    if ($Preferred) {
        if (-not (Test-DevJdk $Preferred $Packaging)) { throw "JDK 21 x64 with required tools not found at '$Preferred'." }
        return [IO.Path]::GetFullPath($Preferred)
    }
    $candidates = @($env:MY_AUDIO_JDK, $env:JAVA_HOME)
    foreach ($base in @("$env:ProgramFiles/Eclipse Adoptium", "$env:LOCALAPPDATA/Programs/Eclipse Adoptium")) {
        $candidates += @(Get-ChildItem -LiteralPath $base -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -ExpandProperty FullName)
    }
    $candidates += "$env:ProgramFiles/Android/Android Studio/jbr"
    $java = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($java) { $candidates += Split-Path (Split-Path $java.Source -Parent) -Parent }
    foreach ($candidate in $candidates) { if (Test-DevJdk $candidate $Packaging) { return $candidate } }
    return $null
}

function ConvertTo-DevPropertyValue([string]$Value) {
    $escaped = $Value.Replace('\', '/').Replace(':', '\:')
    [regex]::Replace($escaped, '[^\x20-\x7e]', { param($match) '\u{0:x4}' -f [int][char]$match.Value })
}

function Get-DevSdk([string]$Root, [string]$Preferred) {
    $local = Join-Path $Root 'local.properties'
    $configured = $null
    if (Test-Path -LiteralPath $local) {
        $match = [regex]::Match((Get-Content -LiteralPath $local -Raw), '(?m)^\s*sdk\.dir\s*=\s*(.+?)\s*$')
        if ($match.Success) { $configured = [regex]::Replace($match.Groups[1].Value, '\\u([0-9a-fA-F]{4})', { param($item) [string][char][Convert]::ToInt32($item.Groups[1].Value, 16) }).Replace('\:', ':').Replace('\\', '\') }
    }
    if ($Preferred -and $configured -and [IO.Path]::GetFullPath($Preferred) -ne [IO.Path]::GetFullPath($configured)) {
        throw 'The requested SDK differs from local.properties. Review that local file before selecting a different SDK.'
    }
    foreach ($path in @($Preferred, $configured, $env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, "$env:LOCALAPPDATA/Android/Sdk")) {
        if ($path) { return [IO.Path]::GetFullPath($path) }
    }
}

function Get-DevPackages($Versions, [bool]$Android = $true, [bool]$Emulator = $false) {
    $packages = [ordered]@{
        'platform-tools' = 'platform-tools/adb.exe'
        "platforms;android-$($Versions.Platform)" = "platforms/android-$($Versions.Platform)/android.jar"
        "build-tools;$($Versions.BuildTools)" = "build-tools/$($Versions.BuildTools)/apksigner.bat"
        "cmake;$($Versions.CMake)" = "cmake/$($Versions.CMake)/bin/cmake.exe"
    }
    if ($Android) { $packages["ndk;$($Versions.Ndk)"] = "ndk/$($Versions.Ndk)/build/cmake/android.toolchain.cmake" }
    if ($Emulator) { $packages['emulator'] = 'emulator/emulator.exe' }
    return $packages
}

function Find-DevSdkManager([string]$Sdk) {
    foreach ($dir in @('latest', '23.0') + @(Get-ChildItem (Join-Path $Sdk 'cmdline-tools') -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -ExpandProperty Name)) {
        $path = Join-Path $Sdk "cmdline-tools/$dir/bin/sdkmanager.bat"
        if (Test-Path -LiteralPath $path) { return $path }
    }
    return $null
}

function Find-DevVisualStudio {
    $finder = "${env:ProgramFiles(x86)}/Microsoft Visual Studio/Installer/vswhere.exe"
    if (-not (Test-Path -LiteralPath $finder)) { return $null }
    $path = & $finder -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
    if ($LASTEXITCODE -eq 0 -and $path -and (Test-Path -LiteralPath (Join-Path $path 'VC/Auxiliary/Build/vcvars64.bat'))) { return $path.Trim() }
    return $null
}

function Assert-DevExit([string]$Operation, [int]$Code) {
    if ($Code -eq 3010) { throw "$Operation completed but requires a restart. Restart Windows and rerun the same command; no automatic restart was requested." }
    if ($Code -ne 0) { throw "$Operation failed (exit $Code). Resolve the reported error and rerun the same command." }
}

function Install-DevWinget([string]$Id, [bool]$AcceptLicenses, [string]$Override = '') {
    if (-not (Get-Command winget.exe -ErrorAction SilentlyContinue)) {
        throw "WinGet is unavailable. Install/update Microsoft's App Installer (https://aka.ms/getwinget), reopen PowerShell, and rerun. Alternatively install $Id manually."
    }
    $arguments = @('install', '--id', $Id, '--exact', '--source', 'winget')
    if ($AcceptLicenses) { $arguments += @('--accept-package-agreements', '--accept-source-agreements') }
    if ($Override) { $arguments += @('--override', $Override) }
    Write-Host "Installing missing prerequisite: $Id (Windows may request administrator permission)."
    & winget.exe @arguments
    Assert-DevExit "Installation of $Id" $LASTEXITCODE
}

function Install-DevCommandLineTools([string]$Root, [string]$Sdk, [bool]$AcceptLicenses) {
    if (-not $AcceptLicenses) {
        Write-Host 'Android SDK terms: https://developer.android.com/studio/terms'
        if ((Read-Host 'Read the terms. Accept and download Android command-line tools? [y/N]') -notmatch '^(?i:y|yes)$') { throw 'Android SDK terms were not accepted; nothing downloaded.' }
    }
    $archive = 'commandlinetools-win-16111833_latest.zip'
    $expected = 'e5885e2e59038c0778a85fc19f01de7ce7c567c105553f7617b936b1e0e87b2b'
    # Revision 23.0, verified against Google's repository2-3.xml archive checksum.
    $destination = Join-Path $Sdk 'cmdline-tools/23.0'
    if (Test-Path -LiteralPath $destination) { throw "Incomplete command-line tools at $destination. Review/remove that partial installation and rerun; it will not be overwritten." }
    $cache = Join-Path $Root '.work/downloads'
    New-Item -ItemType Directory -Force $cache | Out-Null
    $zip = Join-Path $cache $archive
    if (-not (Test-Path -LiteralPath $zip)) {
        [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -UseBasicParsing "https://dl.google.com/android/repository/$archive" -OutFile $zip
    }
    if ((Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash -ne $expected) { throw "Android tools checksum mismatch. Do not run this archive; remove $zip and retry." }
    $staging = Join-Path $cache ('extract-' + [guid]::NewGuid().ToString('N'))
    Expand-Archive -LiteralPath $zip -DestinationPath $staging
    if (-not (Test-Path -LiteralPath (Join-Path $staging 'cmdline-tools/bin/sdkmanager.bat'))) { throw 'Unexpected Android tools archive layout.' }
    New-Item -ItemType Directory -Force (Split-Path $destination -Parent) | Out-Null
    # Copy only the verified archive content; never delete or move an existing SDK directory.
    Copy-Item -LiteralPath (Join-Path $staging 'cmdline-tools') -Destination $destination -Recurse
}
