<# Tests local discovery and properties encoding without downloads, installers or devices. #>
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'dev_environment.ps1')
$testRoot = Join-Path $root ('.work/setup-tests/' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot -Force | Out-Null
$checks = 0
function Assert-Test([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "FAIL: $Message" }
    $script:checks++
}
function Assert-Throws([scriptblock]$Action, [string]$Message) {
    $threw = $false
    try { & $Action | Out-Null } catch { $threw = $true }
    Assert-Test $threw $Message
}

$versions = Get-DevVersions $root
$android = Get-DevPackages $versions $true $false
$windows = Get-DevPackages $versions $false $false
Assert-Test ($android.Contains("ndk;$($versions.Ndk)")) 'Android requests pinned NDK'
Assert-Test (-not $windows.Contains("ndk;$($versions.Ndk)")) 'Windows does not request NDK'
Assert-Test ($android.Contains("platforms;android-$($versions.Platform)")) 'SDK platform matches build'
Assert-Test (-not $android.Contains('emulator')) 'Emulator is optional'
Assert-Test ((Get-DevPackages $versions $true $true).Contains('emulator')) 'Explicit emulator request'

$fakeSdk = Join-Path $testRoot ('SDK with spaces ' + [char]0x00fc)
$property = ConvertTo-DevPropertyValue $fakeSdk
Assert-Test ($property -match '^[A-Za-z]\\:') 'Drive colon escaped for Java properties/lint'
Assert-Test ($property -match '\\u00fc') 'Unicode paths escaped without lossy ASCII conversion'
[IO.File]::WriteAllText((Join-Path $testRoot 'local.properties'), "sdk.dir=$property`n", [Text.Encoding]::ASCII)
Assert-Test ((Get-DevSdk $testRoot '') -eq $fakeSdk) 'SDK properties round trip'
Assert-Test ((Get-DevSdk $testRoot $fakeSdk) -eq $fakeSdk) 'Matching explicit SDK accepted'
Assert-Throws { Get-DevSdk $testRoot (Join-Path $testRoot 'different') } 'Conflicting SDK rejected'

$fakeJdk = Join-Path $testRoot 'JDK with spaces'
New-Item -ItemType Directory -Path (Join-Path $fakeJdk 'bin'),(Join-Path $fakeJdk 'include') -Force | Out-Null
foreach ($path in @('bin/java.exe','bin/javac.exe','include/jni.h')) { [IO.File]::WriteAllText((Join-Path $fakeJdk $path), '') }
[IO.File]::WriteAllText((Join-Path $fakeJdk 'release'), "JAVA_VERSION=`"21.0.13`"`nOS_ARCH=`"amd64`"`n")
Assert-Test (Test-DevJdk $fakeJdk $false) 'JDK 21 with JNI accepted'
Assert-Test (-not (Test-DevJdk $fakeJdk $true)) 'Packaging requires jpackage'
[IO.File]::WriteAllText((Join-Path $fakeJdk 'bin/jpackage.exe'), '')
Assert-Test (Test-DevJdk $fakeJdk $true) 'Packaging JDK accepted'
[IO.File]::WriteAllText((Join-Path $fakeJdk 'release'), "JAVA_VERSION=`"17.0.13`"`nOS_ARCH=`"amd64`"`n")
Assert-Test (-not (Test-DevJdk $fakeJdk $false)) 'Wrong Java major rejected'
Assert-Throws { Find-DevJdk $fakeJdk $false } 'Invalid explicit JDK does not silently fall back'
Assert-Throws { Assert-DevExit 'simulated installer' 3010 } 'Reboot-required code stops workflow'
Assert-Throws { Assert-DevExit 'simulated installer' 1 } 'Installer error stops workflow'
Assert-DevExit 'simulated installer' 0
Write-Host "PASS: $checks discovery and encoding checks. No downloads or installations performed."
