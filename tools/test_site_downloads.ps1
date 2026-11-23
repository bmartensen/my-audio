# Offline regression checks for incomplete release metadata; no network or publishing.
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'check_site_downloads.ps1')
function New-TestRelease {
    [pscustomobject]@{
        tag_name = 'v1.2.3'; draft = $false
        assets = @(@('apk', 'msi', 'apk.sha256', 'msi.sha256') | ForEach-Object {
            [pscustomobject]@{ name = "MyAudio-1.2.3.$_"; size = 100; state = 'uploaded'; browser_download_url = "https://example.test/$_" }
        }) + [pscustomobject]@{ name = 'OpenJDK21U-jdk-sources_21.0.13_9.tar.gz'; size = 100; state = 'uploaded'
            browser_download_url = 'https://example.test/source' }
    }
}
Assert-SiteReleaseAssets (New-TestRelease) '1.2.3'
foreach ($case in @('missing-msi', 'missing-checksum', 'missing-runtime-source', 'draft', 'wrong-tag', 'empty', 'unfinished')) {
    $release = New-TestRelease
    switch ($case) {
        'missing-msi' { $release.assets = @($release.assets | Where-Object { $_.name -notlike '*.msi' }) }
        'missing-checksum' { $release.assets = @($release.assets | Where-Object { $_.name -notlike '*.msi.sha256' }) }
        'missing-runtime-source' { $release.assets = @($release.assets | Where-Object { $_.name -notlike '*-jdk-sources_*' }) }
        'draft' { $release.draft = $true }
        'wrong-tag' { $release.tag_name = 'v1.2.2' }
        'empty' { $release.assets[0].size = 0 }
        'unfinished' { $release.assets[0].state = 'new' }
    }
    $rejected = $false
    try { Assert-SiteReleaseAssets $release '1.2.3' } catch { $rejected = $true }
    if (-not $rejected) { throw "Accepted invalid release: $case" }
}
Write-Output 'Passed: complete release accepted; seven incomplete/unpublished release cases rejected.'
