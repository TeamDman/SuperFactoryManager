<# Read-only focused gates for the exact 1.20.3/1.20.4 asset stage. #>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $PrismRoot,
    [Parameter(Mandatory)] [string] $ScratchParent
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$stageScript = Join-Path $PSScriptRoot 'Stage-Exact12AssetObjects.ps1'
$scratch = [IO.Path]::GetFullPath($ScratchParent)
if (-not [IO.Directory]::Exists($scratch)) { throw 'ScratchParent must already exist' }

function Assert-Throws([scriptblock] $Action, [string] $Case) {
    try {
        & $Action | Out-Null
    } catch {
        Write-Output "PASS $Case"
        return
    }
    throw "Expected failure: $Case"
}

$future = Join-Path $scratch ('sfm-index12-preflight-' + [guid]::NewGuid().ToString('N'))
$preflight = & $stageScript -PrismRoot $PrismRoot -OutputRoot $future -PreflightOnly
if ($preflight.schema -cne 'sfm-release-asset-objects-preflight/1' -or
    $preflight.asset_index_sha1 -cne '5060d8c8c8f6a52cea32ea9ccd68c9c92e5b74e7' -or
    $preflight.cache_coverage.cached_verified_objects -ne 3787 -or
    $preflight.cache_coverage.cache_missing_objects -ne 0 -or
    $preflight.cache_coverage.cache_invalid_objects -ne 0 -or
    $preflight.output_created -or (Test-Path -LiteralPath $future)) {
    throw 'Exact index-12 preflight did not return complete read-only coverage'
}
Write-Output 'PASS complete exact-index preflight without output'

Assert-Throws { & $stageScript -PrismRoot $PrismRoot -OutputRoot $scratch -PreflightOnly } 'existing output refused'
Assert-Throws { & $stageScript -PrismRoot $PrismRoot -OutputRoot (Join-Path $PrismRoot 'assets/objects/unsafe') -PreflightOnly } 'launcher-cache overlap refused'
Assert-Throws { & $stageScript -PrismRoot $PrismRoot -OutputRoot (Join-Path $PSScriptRoot 'unsafe') -PreflightOnly } 'checkout overlap refused'
if (Test-Path -LiteralPath $future) { throw 'Negative tests created the future output' }
