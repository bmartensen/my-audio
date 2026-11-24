<#
.SYNOPSIS
Packs the Windows app image into an MSIX for the Microsoft Store: .work\MyAudio-<version>.msix.

.DESCRIPTION
Builds the app image with :desktop:createDistributable (native engine, bundled Temurin 21 runtime),
adds desktop\msix\AppxManifest.xml with the identity from gradle.properties (myAudio.store.*) and the
logos from branding\microsoft-store\package, indexes the scaled logos with makepri and packs everything
with makeappx from the Windows SDK. The Store package stays unsigned; Microsoft signs it.

With -CertificateThumbprint (a certificate in Cert:\CurrentUser\My) or -CertificatePath (a PFX whose
password comes from MY_AUDIO_MSIX_CERT_PASSWORD), a second copy MyAudio-<version>-local.msix is signed for
a local install or the App Certification Kit. The certificate's subject must equal the publisher in
gradle.properties. Keep certificates outside the checkout.
#>
param(
    [string]$OutputDir = '.work',
    [string]$CertificateThumbprint,
    [string]$CertificatePath,
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
. (Join-Path $PSScriptRoot 'dev_environment.ps1')

function Get-Property([string]$name) {
    $match = [regex]::Match((Get-Content (Join-Path $root 'gradle.properties') -Raw), "(?m)^$([regex]::Escape($name))=(.+)$")
    if (-not $match.Success) { throw "gradle.properties has no $name." }
    $match.Groups[1].Value.Trim()
}

function Find-SdkTool([string]$name) {
    $bin = Join-Path ${env:ProgramFiles(x86)} 'Windows Kits\10\bin'
    $tool = Get-ChildItem $bin -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '^10\.' } |
        Sort-Object { [version]$_.Name } -Descending |
        ForEach-Object { Join-Path $_.FullName "x64\$name" } | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    if (-not $tool) { throw "$name not found; install the Windows SDK (setup.ps1 -Target Windows -InstallTools)." }
    $tool
}

$versionName = Get-Property 'myAudio.versionName'
$parts = @($versionName.Split('.') | ForEach-Object { [int]$_ })
while ($parts.Count -lt 3) { $parts += 0 }
# MSIX versions have four parts; the Store requires the last one to be 0.
$version = "$($parts[0]).$($parts[1]).$($parts[2]).0"
$values = @{
    IdentityName = Get-Property 'myAudio.store.identityName'
    Publisher = Get-Property 'myAudio.store.publisher'
    PublisherDisplayName = Get-Property 'myAudio.store.publisherDisplayName'
    Version = $version
}

if (-not $SkipBuild) {
    $env:JAVA_HOME = Find-DevJdk $env:MY_AUDIO_JDK $true
    if (-not $env:JAVA_HOME) { throw 'JDK 21 with jpackage not found; run setup.ps1 -Target Windows -InstallTools.' }
    $env:MY_AUDIO_JDK = $env:JAVA_HOME
    $env:ANDROID_HOME = Get-DevSdk $root ''
    & .\gradlew.bat :desktop:createDistributable --console=plain
    if ($LASTEXITCODE -ne 0) { throw "createDistributable failed ($LASTEXITCODE)." }
}
$image = Join-Path $root 'desktop\build\compose\binaries\main\app\My Audio'
if (-not (Test-Path -LiteralPath (Join-Path $image 'My Audio.exe'))) { throw "No app image at $image." }

$work = Join-Path $root 'desktop\build\msix'
$layout = Join-Path $work 'layout'
$priRoot = Join-Path $work 'pri'
if (Test-Path -LiteralPath $work) { Remove-Item -LiteralPath $work -Recurse -Force }
New-Item -ItemType Directory -Force $layout, (Join-Path $priRoot 'Assets') | Out-Null
Copy-Item -Path (Join-Path $image '*') -Destination $layout -Recurse
Copy-Item -Path (Join-Path $root 'branding\microsoft-store\package\*.png') -Destination (Join-Path $priRoot 'Assets')
Copy-Item -LiteralPath (Join-Path $priRoot 'Assets') -Destination $layout -Recurse

$manifest = Get-Content (Join-Path $root 'desktop\msix\AppxManifest.xml') -Raw
foreach ($key in $values.Keys) { $manifest = $manifest.Replace("`$($key)", [Security.SecurityElement]::Escape($values[$key])) }
if ($manifest -match '\$\(\w+\)') { throw "Unfilled manifest value $($Matches[0])." }
$utf8 = New-Object Text.UTF8Encoding $false
[IO.File]::WriteAllText((Join-Path $layout 'AppxManifest.xml'), $manifest, $utf8)
[IO.File]::WriteAllText((Join-Path $priRoot 'AppxManifest.xml'), $manifest, $utf8)

# resources.pri lets the manifest name Assets\Square44x44Logo.png while the package carries the
# scale and target-size variants. Only the logos are indexed, not the runtime.
$makepri = Find-SdkTool 'makepri.exe'
$config = Join-Path $work 'priconfig.xml'
& $makepri createconfig /cf $config /dq en-US /pv 10.0.0 /o | Out-Null
if ($LASTEXITCODE -ne 0) { throw "makepri createconfig failed ($LASTEXITCODE)." }
# Without the bundle split, every scale lands in the one resources.pri of this single package.
[xml]$priConfig = Get-Content $config -Raw
$priConfig.resources.packaging | ForEach-Object { [void]$_.ParentNode.RemoveChild($_) }
$priConfig.Save($config)
& $makepri new /pr $priRoot /cf $config /mn (Join-Path $priRoot 'AppxManifest.xml') /of (Join-Path $layout 'resources.pri') /o | Out-Null
if ($LASTEXITCODE -ne 0) { throw "makepri new failed ($LASTEXITCODE)." }

$outDir = if ([IO.Path]::IsPathRooted($OutputDir)) { $OutputDir } else { Join-Path $root $OutputDir }
New-Item -ItemType Directory -Force $outDir | Out-Null
$target = Join-Path $outDir "MyAudio-$versionName.msix"
$makeappx = Find-SdkTool 'makeappx.exe'
& $makeappx pack /d $layout /p $target /o /h SHA256 | Out-Null
if ($LASTEXITCODE -ne 0) { throw "makeappx pack failed ($LASTEXITCODE)." }

function Write-Hash([string]$path) {
    $hash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    Set-Content -LiteralPath "$path.sha256" -Value "$hash *$(Split-Path $path -Leaf)" -Encoding ascii
    Write-Output $path
    Write-Output "SHA-256 $hash"
}
Write-Output "MSIX $version for $($values.IdentityName)"
Write-Hash $target

if ($CertificateThumbprint -or $CertificatePath) {
    $certificate = if ($CertificateThumbprint) { @('/sha1', $CertificateThumbprint, '/s', 'My') }
    else {
        if (-not $env:MY_AUDIO_MSIX_CERT_PASSWORD) { throw 'Set MY_AUDIO_MSIX_CERT_PASSWORD for the PFX.' }
        @('/f', $CertificatePath, '/p', $env:MY_AUDIO_MSIX_CERT_PASSWORD)
    }
    $local = Join-Path $outDir "MyAudio-$versionName-local.msix"
    Copy-Item -LiteralPath $target -Destination $local -Force
    $signtool = Find-SdkTool 'signtool.exe'
    & $signtool sign /q /fd SHA256 @certificate $local
    if ($LASTEXITCODE -ne 0) { throw "signtool failed ($LASTEXITCODE); the certificate subject must be $($values.Publisher)." }
    Write-Hash $local
}
