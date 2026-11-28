<#
.SYNOPSIS
Creates the GitHub release for the current version as a draft and uploads its verified assets.
.DESCRIPTION
Publishing is an explicit, outward-facing step: run this only when the contributor has authorized it.

The release v<version> (version from gradle.properties) must already exist as a pushed, annotated tag.
The script verifies, then uploads from -WorkDir:
  MyAudio-<version>.apk, .msi and their .sha256 files (checked against the files), and
  the source archive of the Java runtime bundled in the MSI (OpenJDK, GPLv2 with Classpath Exception),
  checked against the .sha256.txt published next to it by Adoptium.
Release notes come from the matching CHANGELOG.md section. The release stays a draft unless -Publish
is given; review it on GitHub, then publish and run tools/check_site_downloads.ps1 -RequireGitHubRelease.
-DryRun performs every check and prints the planned release without contacting GitHub's write API.
.EXAMPLE
.\tools\publish_github_release.ps1 -DryRun
.\tools\publish_github_release.ps1            # draft
.\tools\publish_github_release.ps1 -Publish   # draft, then published
#>
[CmdletBinding()]
param(
    [string]$WorkDir = '.work',
    [string]$RuntimeSourceDir = '.work/runtime-source',
    [string]$Remote = 'origin',
    [switch]$Publish,
    [switch]$DryRun
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    $properties = Get-Content gradle.properties -Encoding UTF8
    $version = ($properties | Where-Object { $_ -match '^myAudio\.versionName=(.+)$' } | ForEach-Object { $Matches[1].Trim() })
    if ($version -notmatch '^\d+\.\d+\.\d+$') { throw "Invalid versionName in gradle.properties: $version" }
    $tag = "v$version"

    # The tag must exist locally and on the remote, pointing at the same commit.
    # A dry run reports tag problems as warnings, so the remaining checks can be tried before tagging.
    function Stop-Release([string]$Message) { if ($DryRun) { Write-Warning $Message } else { throw $Message } }
    $localTag = (git rev-parse --verify --quiet "refs/tags/$tag^{commit}")
    if (-not $localTag) { Stop-Release "Tag $tag does not exist. Commit the release source and create the annotated tag first." }
    $remoteUrl = (git remote get-url --push $Remote)
    if ($remoteUrl -notmatch 'github\.com[:/]([^/]+/[^/.]+)') { throw "Remote $Remote is not a GitHub repository: $remoteUrl" }
    $repository = $Matches[1]
    $remoteTag = (git ls-remote --tags $Remote "refs/tags/$tag^{}") -split '\s+' | Select-Object -First 1
    if ($localTag -and $remoteTag -ne $localTag) { Stop-Release "Tag $tag is not pushed to $Remote ($remoteUrl) or points elsewhere." }

    # Installers and their checksum files.
    $assets = @()
    foreach ($extension in 'apk', 'msi') {
        $file = Join-Path $WorkDir "MyAudio-$version.$extension"
        $sumFile = "$file.sha256"
        if (-not (Test-Path $file) -or -not (Test-Path $sumFile)) { throw "Missing $file or its .sha256 file." }
        $expected = ((Get-Content $sumFile -Raw).Trim() -split '\s+')[0].ToLowerInvariant()
        $actual = (Get-FileHash $file -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($expected -ne $actual) { throw "Checksum mismatch for $file." }
        $assets += $file, $sumFile
    }

    # Corresponding source of the bundled Java runtime.
    $sources = @(Get-ChildItem $RuntimeSourceDir -Filter '*-jdk-sources_*.tar.gz' -ErrorAction SilentlyContinue)
    if ($sources.Count -ne 1) { throw "Expected exactly one *-jdk-sources_*.tar.gz in $RuntimeSourceDir (matching the bundled runtime)." }
    $source = $sources[0].FullName
    $sourceSum = "$source.sha256.txt"
    if (-not (Test-Path $sourceSum)) { throw "Missing $sourceSum (download it with the archive from the runtime's release)." }
    $sourceHash = (Get-FileHash $source -Algorithm SHA256).Hash.ToLowerInvariant()
    if (((Get-Content $sourceSum -Raw).Trim() -split '\s+')[0].ToLowerInvariant() -ne $sourceHash) { throw "Checksum mismatch for $source." }
    $assets += $source

    # Release notes: the CHANGELOG section of this version plus checksums. The guide links point to the GitHub Pages
    # site of the repository the release goes to, so a fork links its own guide.
    $owner, $name = $repository -split '/', 2
    $pages = "https://$($owner.ToLowerInvariant()).github.io/$name"
    $changelog = Get-Content CHANGELOG.md -Raw -Encoding UTF8
    $section = [regex]::Match($changelog, "(?ms)^## $([regex]::Escape($version))\b[^\n]*\n(.*?)(?=^## |\z)")
    if (-not $section.Success) { throw "CHANGELOG.md has no section for $version." }
    $hashes = foreach ($extension in 'apk', 'msi') {
        $line = (Get-Content (Join-Path $WorkDir "MyAudio-$version.$extension.sha256") -Raw).Trim()
        '- `' + $line + '`'
    }
    $notes = @"
$($section.Groups[1].Value.Trim())

### Downloads

- **Android** (10 or later): ``MyAudio-$version.apk``, signed.
- **Windows** (10/11, x64): ``MyAudio-$version.msi``. The installer is not code-signed, so SmartScreen may ask for confirmation.
- ``$(Split-Path $source -Leaf)``: source code of the Java runtime included in the MSI (OpenJDK, GPLv2 with Classpath Exception), SHA-256 ``$sourceHash``.

SHA-256:
$($hashes -join "`n")

User guide: $pages/en/guide/ · Anleitung: $pages/de/guide/
"@
    $notesFile = Join-Path $WorkDir "release-notes-$version.md"
    Set-Content -LiteralPath $notesFile -Value $notes -Encoding utf8

    Write-Output "Release $tag for $repository ($remoteUrl), commit $localTag"
    $assets | ForEach-Object { Write-Output ("  asset: {0} ({1:N0} bytes)" -f (Split-Path $_ -Leaf), (Get-Item $_).Length) }
    Write-Output "  notes: $notesFile"
    if ($DryRun) { Write-Output 'Dry run: nothing was created or uploaded.'; return }

    gh release create $tag --repo $repository --verify-tag --draft --title "My Audio $version" --notes-file $notesFile @assets
    if ($LASTEXITCODE -ne 0) { throw 'gh release create failed.' }
    if ($Publish) {
        gh release edit $tag --repo $repository --draft=false --latest
        if ($LASTEXITCODE -ne 0) { throw 'Publishing the draft failed.' }
        Write-Output "Published $tag. Next: .\tools\check_site_downloads.ps1 -RequireGitHubRelease"
    } else {
        Write-Output "Draft $tag created. Review it on GitHub, then: gh release edit $tag --repo $repository --draft=false --latest"
    }
} finally {
    Pop-Location
}
