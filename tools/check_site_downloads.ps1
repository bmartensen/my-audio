<#
.SYNOPSIS
Checks the actual download target before publishing GitHub Pages.
.DESCRIPTION
Read-only. Direct GitHub downloads require a published release with both installers, their
checksum files and the source archive of the Java runtime bundled in the MSI. -RequireGitHubRelease also checks this before enabling release_downloads.
A Git tag alone does not satisfy the check. No release is created or uploaded by this script.
#>
[CmdletBinding()]
param([switch]$RequireGitHubRelease)
$ErrorActionPreference = 'Stop'

function Assert-SiteReleaseAssets {
    param($Release, [string]$Version)
    if ($Release.tag_name -ne "v$Version" -or $Release.draft) {
        throw "A published release v$Version is required; a tag or draft alone is not downloadable."
    }
    foreach ($name in @("MyAudio-$Version.apk", "MyAudio-$Version.msi",
            "MyAudio-$Version.apk.sha256", "MyAudio-$Version.msi.sha256")) {
        $asset = @($Release.assets | Where-Object { $_.name -ceq $name })
        if ($asset.Count -ne 1 -or $asset[0].size -le 0 -or $asset[0].state -ne 'uploaded' -or
            -not $asset[0].browser_download_url) {
            throw "Release v$Version is missing a completed, non-empty asset: $name"
        }
    }
    # The MSI bundles OpenJDK (GPLv2 with Classpath Exception): its corresponding source ships with it.
    $source = @($Release.assets | Where-Object { $_.name -like '*-jdk-sources_*.tar.gz' })
    if ($source.Count -ne 1 -or $source[0].size -le 0 -or $source[0].state -ne 'uploaded') {
        throw "Release v$Version is missing the source archive of the bundled Java runtime."
    }
}

function Test-SiteDownloads {
    param([switch]$RequireGitHubRelease)
    $config = @{}
    foreach ($line in Get-Content -LiteralPath (Join-Path $PSScriptRoot '../docs/_config.yml') -Encoding UTF8) {
        if ($line -match '^([a-z_]+):\s*(.*?)\s*$') { $config[$Matches[1]] = $Matches[2] }
    }
    if ($config.release -notmatch '^\d+\.\d+\.\d+$') { throw 'Invalid site release version.' }
    if ($config.release_downloads -notin @('true', 'false')) { throw 'release_downloads must be true or false.' }
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    if ($RequireGitHubRelease -or $config.release_downloads -eq 'true') {
        if ($config.repository_url -notmatch '^https://github\.com/([^/]+/[^/]+)/?$') {
            throw 'repository_url must identify a GitHub repository.'
        }
        $repository = $Matches[1]
        $api = "https://api.github.com/repos/$repository/releases/tags/v$($config.release)"
        try {
            $release = Invoke-RestMethod -Uri $api -Headers @{ Accept = 'application/vnd.github+json' } -UserAgent 'MyAudio-Download-Check'
        } catch {
            throw "Cannot read published release v$($config.release). Upload and publish the installers before enabling direct downloads. $($_.Exception.Message)"
        }
        Assert-SiteReleaseAssets $release $config.release
        foreach ($asset in $release.assets | Where-Object { $_.name -match '^MyAudio-.*\.(apk|msi)(\.sha256)?$' }) {
            $response = Invoke-WebRequest -UseBasicParsing -Method Head -Uri $asset.browser_download_url
            if ($response.StatusCode -ne 200) { throw "Download unavailable: $($asset.name)" }
        }
        Write-Output "GitHub release v$($config.release): both installers and checksums are published and reachable."
    } else {
        # Without direct links the pages point at releases/latest, which must offer a complete release.
        if ($config.repository_url -notmatch '^https://github\.com/([^/]+/[^/]+)/?$') {
            throw 'repository_url must identify a GitHub repository.'
        }
        $api = "https://api.github.com/repos/$($Matches[1])/releases/latest"
        try {
            $latest = Invoke-RestMethod -Uri $api -Headers @{ Accept = 'application/vnd.github+json' } -UserAgent 'MyAudio-Download-Check'
        } catch {
            throw "The fallback releases/latest has no published release. $($_.Exception.Message)"
        }
        Assert-SiteReleaseAssets $latest ($latest.tag_name -replace '^v', '')
        Write-Output "Fallback: latest release $($latest.tag_name) offers both installers, checksums and the runtime source. No version is advertised."
    }
}

if ($MyInvocation.InvocationName -ne '.') { Test-SiteDownloads -RequireGitHubRelease:$RequireGitHubRelease }
