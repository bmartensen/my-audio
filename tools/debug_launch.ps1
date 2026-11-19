<#
.SYNOPSIS
Builds the debug APK, installs it and starts My Audio on the emulator or a connected phone.

.DESCRIPTION
Target selection: -Device <serial> wins; otherwise -Target phone|emulator; otherwise (auto) a connected
physical device (the Pixel 9 target system) is preferred over a running emulator.
The app confirms the start by logging Ready[<token>] once its first frame is laid out.

.EXAMPLE
.\tools\debug_launch.ps1
.\tools\debug_launch.ps1 -Target emulator -ScreenshotPath .work/home.png
.\tools\debug_launch.ps1 -NoBuild -Screen calibration
.\tools\debug_launch.ps1 -NoBuild -Screen project/1234567890000
#>
[CmdletBinding()]
param(
    [ValidateSet('auto','phone','emulator')][string]$Target = 'auto',
    [string]$Device,
    [ValidatePattern('^(home|settings|calibration|project/\d+)$')][string]$Screen = 'home',
    [switch]$NoBuild,
    [string]$ScreenshotPath,
    [int]$ScreenshotDelaySeconds = 2
)
$ErrorActionPreference = 'Stop'
$taskRepo = Split-Path $PSScriptRoot -Parent
$taskPackage = 'com.martensening.my_audio'
. (Join-Path $PSScriptRoot 'dev_environment.ps1')
$taskSdk = Get-DevSdk $taskRepo ''
$taskAdb = Join-Path $taskSdk 'platform-tools/adb.exe'
if (-not (Test-Path -LiteralPath $taskAdb)) { throw 'Android SDK adb not found.' }

if (-not $Device) {
    $taskDevices = & $taskAdb devices -l | Select-Object -Skip 1 | Where-Object { $_ -match '^(\S+)\s+device\b' } | ForEach-Object {
        [pscustomobject]@{ Serial = ($_ -split '\s+')[0]; Line = $_ }
    }
    $taskEmulators = @($taskDevices | Where-Object { $_.Serial -like 'emulator-*' })
    $taskPhones = @($taskDevices | Where-Object { $_.Serial -notlike 'emulator-*' })
    if ($taskPhones.Count -gt 1) {
        # Several phones: prefer the Pixel 9 target system.
        $taskPixel = @($taskPhones | Where-Object { $_.Line -match 'model:Pixel_9\b' })
        if ($taskPixel.Count -eq 1) { $taskPhones = $taskPixel }
    }
    $taskCandidates = switch ($Target) {
        'phone' { $taskPhones }
        'emulator' { $taskEmulators }
        default { if ($taskPhones.Count) { $taskPhones } else { $taskEmulators } }
    }
    if ($taskCandidates.Count -eq 0) { throw "No ready $Target device. Start the emulator or connect the phone with USB debugging." }
    if ($taskCandidates.Count -gt 1) { throw "Several devices match ($($taskCandidates.Serial -join ', ')); pass -Device <serial>." }
    $Device = $taskCandidates[0].Serial
}
$taskState = & $taskAdb -s $Device get-state 2>$null
if ($LASTEXITCODE -ne 0 -or $taskState -ne 'device') { throw "Device $Device is not ready." }
$taskModel = (& $taskAdb -s $Device shell getprop ro.product.model).Trim()
Write-Output "Device: $Device ($taskModel)"

Push-Location $taskRepo
try {
    if (-not $NoBuild) {
        $env:JAVA_HOME = Find-DevJdk $env:MY_AUDIO_JDK $false
        if (-not $env:JAVA_HOME) { throw 'JDK 21 missing; run setup.ps1 -Target Android -InstallTools.' }
        $env:ANDROID_HOME = $taskSdk
        & .\gradlew.bat assembleDebug --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
        # Replacement install keeps existing projects and recordings on the device.
        & $taskAdb -s $Device install -r 'app/build/outputs/apk/debug/app-debug.apk'
        if ($LASTEXITCODE -ne 0) { throw 'Install failed.' }
    } elseif (-not (& $taskAdb -s $Device shell pm path $taskPackage)) {
        throw "$taskPackage is not installed on $Device; run without -NoBuild."
    }
    $taskToken = [guid]::NewGuid().ToString('N')
    & $taskAdb -s $Device logcat -c
    # SINGLE_TOP: if the system relaunches the app first (it does right after an update), the
    # intent still reaches the running activity via onNewIntent.
    & $taskAdb -s $Device shell am start -S -W -f 0x20000000 -n "$taskPackage/.MainActivity" -e debugRequestId $taskToken -e debugScreen $Screen
    if ($LASTEXITCODE -ne 0) { throw 'Launch failed.' }
    $taskDeadline = [DateTime]::UtcNow.AddSeconds(20)
    $taskReady = $false
    do {
        $taskLog = (& $taskAdb -s $Device logcat -d -s 'MyAudioDebug:I' '*:S') -join "`n"
        if ($taskLog.Contains("Ready[$taskToken]")) { $taskReady = $true; break }
        Start-Sleep -Milliseconds 300
    } while ([DateTime]::UtcNow -lt $taskDeadline)
    if (-not $taskReady) {
        $taskCrash = (& $taskAdb -s $Device logcat -d -s 'AndroidRuntime:E' '*:S') -join "`n"
        throw "No readiness acknowledgement from the app.`n$taskCrash"
    }
    Write-Output "Ready[$taskToken] on $Device"
    if ($ScreenshotPath) {
        Start-Sleep -Seconds $ScreenshotDelaySeconds
        $taskOutput = [IO.Path]::GetFullPath((Join-Path $taskRepo $ScreenshotPath))
        if (-not $taskOutput.StartsWith($taskRepo + [IO.Path]::DirectorySeparatorChar)) { throw 'Screenshot must stay inside the repository.' }
        New-Item -ItemType Directory -Path (Split-Path $taskOutput -Parent) -Force | Out-Null
        & $taskAdb -s $Device shell screencap -p /sdcard/my-audio-debug.png
        if ($LASTEXITCODE -ne 0) { throw 'Capture failed.' }
        # adb reports transfer progress on stderr; only the exit code decides success.
        $pullLog = & cmd /c "`"$taskAdb`" -s $Device pull /sdcard/my-audio-debug.png `"$taskOutput`" 2>&1"
        if ($LASTEXITCODE -ne 0) { throw "Capture transfer failed: $pullLog" }
        Write-Output $taskOutput
    }
} finally { Pop-Location }
