<#
Fail-closed, no-download tests for the exact 1.20.2 asset-object stage.
Optional CompletedStage validation reads a prior full scratch stage without
modifying it. The new test root and any fixtures are retained on failure.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $PrismRoot,
    [Parameter(Mandatory)] [string] $ExactIndexFile,
    [Parameter(Mandatory)] [string] $TestRoot,
    [string] $CompletedStage
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Assert-True([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw "Test failed: $Message" }
}

function Assert-Rejected([string] $Name, [scriptblock] $Action, [string] $Expected) {
    $rejected = $false
    try { & $Action | Out-Null } catch {
        if ($_.Exception.Message -notlike "*$Expected*") {
            throw "Test $Name rejected for the wrong reason: $($_.Exception.Message)"
        }
        $rejected = $true
    }
    Assert-True $rejected "$Name unexpectedly succeeded"
}

$root = [IO.Path]::GetFullPath($TestRoot).TrimEnd('\', '/')
$prism = [IO.Path]::GetFullPath($PrismRoot).TrimEnd('\', '/')
$checkout = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\', '/')
$profileRoot = [Environment]::GetFolderPath([Environment+SpecialFolder]::UserProfile)
foreach ($protected in @($prism, $checkout, $profileRoot)) {
    if ($root.Equals($protected, [StringComparison]::OrdinalIgnoreCase) -or
        $root.StartsWith($protected + [IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase) -or
        $protected.StartsWith($root + [IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase)) {
        throw 'TestRoot must be isolated from Prism, the repository and user profile'
    }
}
if ($null -ne (Get-Item -LiteralPath $root -Force -ErrorAction SilentlyContinue)) {
    throw 'TestRoot already exists; refusing to reuse it'
}
if (-not [IO.Directory]::Exists([IO.Path]::GetDirectoryName($root))) {
    throw 'TestRoot parent must already exist'
}
[IO.Directory]::CreateDirectory($root) | Out-Null
$stage = Join-Path $PSScriptRoot 'Stage-Exact1202AssetObjects.ps1'
$common = @{ PrismRoot = $prism; ExactIndexFile = $ExactIndexFile }

$preflightOutput = Join-Path $root 'preflight-output'
$preflight = & $stage @common -OutputRoot $preflightOutput -PreflightOnly
Assert-True ($preflight.schema -eq 'sfm-release-asset-objects-preflight/1' -and
    $preflight.asset_index_sha1 -eq '21beaec863755c8fd4620b22ed9bdbc6718b3c32' -and
    $preflight.cache_coverage.required_unique_objects -eq 3607 -and
    $preflight.cache_coverage.cache_invalid_objects -eq 0 -and
    -not (Test-Path -LiteralPath $preflightOutput)) 'read-only exact preflight'

$badIndex = Join-Path $root 'bad-index.json'
[IO.File]::WriteAllBytes($badIndex, [Text.Encoding]::UTF8.GetBytes('{}'))
$rejectedOutput = Join-Path $root 'rejected-output'
Assert-Rejected 'bad index' {
    & $stage -PrismRoot $prism -ExactIndexFile $badIndex -OutputRoot $rejectedOutput -PreflightOnly
} 'asset index size or SHA-1 mismatch'
Assert-True (-not (Test-Path -LiteralPath $rejectedOutput)) 'bad index created output'

$checkoutOutput = Join-Path $checkout ('asset-stage-test-output-' + [guid]::NewGuid().ToString('N'))
Assert-Rejected 'inside checkout' {
    & $stage @common -OutputRoot $checkoutOutput -PreflightOnly
} 'checkout'
Assert-True (-not (Test-Path -LiteralPath $checkoutOutput)) 'checkout rejection created output'

[IO.Directory]::CreateDirectory($rejectedOutput) | Out-Null
Assert-Rejected 'existing output' {
    & $stage @common -OutputRoot $rejectedOutput -PreflightOnly
} 'already exists'

if ($preflight.cache_coverage.cache_missing_objects -gt 0) {
    $noDownloadOutput = Join-Path $root 'no-download-output'
    Assert-Rejected 'missing without download permission' {
        & $stage @common -OutputRoot $noDownloadOutput
    } 'pass -AllowDownloadMissing'
    Assert-True (-not (Test-Path -LiteralPath $noDownloadOutput)) 'missing-object rejection created output'
}

$completedVerified = $false
if ($CompletedStage) {
    $completed = [IO.Path]::GetFullPath($CompletedStage)
    $receipt = Get-Content -LiteralPath (Join-Path $completed 'asset-objects.json') -Raw | ConvertFrom-Json
    . (Join-Path $PSScriptRoot 'AssetIndexCoverage.ps1')
    $coverage = Get-AssetObjectCoverage (Join-Path $completed 'assets/indexes/8.json') `
        (Join-Path $completed 'assets/objects')
    Assert-True ($receipt.schema -eq 'sfm-release-asset-objects-stage/1' -and
        $receipt.asset_index_sha1 -eq $preflight.asset_index_sha1 -and
        $receipt.required_unique_objects -eq 3607 -and
        $receipt.required_unique_bytes -eq 646330719L -and
        $receipt.prism_cache_access -eq 'read_only' -and
        $receipt.staged_coverage_complete -and
        $coverage.cached_verified_objects -eq 3607 -and
        $coverage.cache_missing_objects -eq 0 -and
        $coverage.cache_invalid_objects -eq 0) 'completed scratch stage coverage'
    $completedVerified = $true
}

return [pscustomobject]@{
    schema = 'sfm-release-asset-objects-tests/1'
    passed = $true
    preflight_only = $true
    completed_stage_verified = $completedVerified
    artifacts_retained = $true
}
