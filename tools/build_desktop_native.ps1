<#
.SYNOPSIS
Builds the Windows audio engine with the detected JDK 21, MSVC and pinned CMake/Ninja.
#>
param([ValidateSet('Release', 'Debug')][string]$Configuration = 'Release')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'dev_environment.ps1')
$versions = Get-DevVersions $root
$sdk = Get-DevSdk $root ''
$jdk = Find-DevJdk $env:MY_AUDIO_JDK $true
$vs = Find-DevVisualStudio
if (-not $jdk -or -not $vs) { throw 'Desktop prerequisites are missing. Run .\setup.ps1 -Target Windows -InstallTools.' }
$cmakeBin = Join-Path $sdk "cmake/$($versions.CMake)/bin"
if (-not (Test-Path -LiteralPath (Join-Path $cmakeBin 'ninja.exe'))) { throw 'Pinned CMake/Ninja is missing. Run .\setup.ps1 -Target Windows -InstallTools.' }
$vcvars = Join-Path $vs 'VC/Auxiliary/Build/vcvars64.bat'
# Import only the toolchain environment into this process, then invoke CMake directly with
# argument arrays. Repository paths never become executable shell text.
$env:MY_AUDIO_VCVARS = $vcvars
$environment = & $env:ComSpec /d /c 'call "%MY_AUDIO_VCVARS%" >nul && set'
Assert-DevExit 'MSVC environment' $LASTEXITCODE
foreach ($line in $environment) {
    if ($line -match '^([^=]+)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process') }
}
$env:JAVA_HOME = $jdk
$env:PATH = "$cmakeBin;$env:PATH"
$source = Join-Path $root 'desktop/native'
$build = Join-Path $root "desktop/build/native-cmake/$Configuration"
$output = Join-Path $root 'desktop/build/native'
& (Join-Path $cmakeBin 'cmake.exe') -S $source -B $build -G Ninja "-DCMAKE_BUILD_TYPE=$Configuration" "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=$output" "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=$output"
Assert-DevExit 'Native CMake configuration' $LASTEXITCODE
& (Join-Path $cmakeBin 'cmake.exe') --build $build
Assert-DevExit 'Native build' $LASTEXITCODE
Get-Item -LiteralPath (Join-Path $output 'myaudio.dll')
