#requires -Version 7.0
param(
    [ValidatePattern('^[a-zA-Z0-9_-]+$')][string]$Run = 'latest',
    [string]$Tool = 'sfm-propagate-changes.exe'
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$previousRoot = $env:SFM_ICON_COVERAGE_ROOT
$previousRun = $env:SFM_ICON_COVERAGE_RUN
Push-Location -LiteralPath $repoRoot
try {
    $env:SFM_ICON_COVERAGE_ROOT = $repoRoot
    $env:SFM_ICON_COVERAGE_RUN = $Run
    & $Tool test run --branch 1.19.2 --filter SFMItemstackPreviewCoverageTests --wait-for-build-lock --log-filter info
    if ($LASTEXITCODE -ne 0) { throw "Icon coverage test failed (exit $LASTEXITCODE). Inspect the report for errors or truncation." }
    $report = Join-Path $repoRoot "platform/minecraft/build/itemstack-icon-coverage/$Run/summary.json"
    $summary = Get-Content -LiteralPath $report -Raw | ConvertFrom-Json -AsHashtable
    Write-Host "Files: $($summary.covered_files)/$($summary.files); directories: $($summary.covered_directories)/$($summary.directories)"
    Write-Host "Report: $report"
    $summary.uncovered_groups.GetEnumerator() | Sort-Object { $_.Value.count } -Descending | Select-Object -First 20 @{n='Group';e={$_.Key}}, @{n='Count';e={$_.Value.count}}, @{n='Example';e={$_.Value.examples[0]}} | Format-Table -AutoSize
} finally {
    $env:SFM_ICON_COVERAGE_ROOT = $previousRoot
    $env:SFM_ICON_COVERAGE_RUN = $previousRun
    Pop-Location
}
