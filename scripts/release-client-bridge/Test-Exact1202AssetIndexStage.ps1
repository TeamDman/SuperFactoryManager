<#
Offline, fail-closed checks for the exact 1.20.2 asset-index stage.
Pass a fresh scratch TestRoot outside the user profile; artifacts are retained.
An optional already-verified index exercises the successful staging path.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $TestRoot,
    [string] $ExactIndexFile
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
$profileRoot = [Environment]::GetFolderPath([Environment+SpecialFolder]::UserProfile).TrimEnd('\', '/')
if ($root.Equals($profileRoot, [StringComparison]::OrdinalIgnoreCase) -or
    $root.StartsWith($profileRoot + [IO.Path]::DirectorySeparatorChar,
        [StringComparison]::OrdinalIgnoreCase)) {
    throw 'TestRoot must not be inside the user profile'
}
$checkoutRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\', '/')
if ($root.Equals($checkoutRoot, [StringComparison]::OrdinalIgnoreCase) -or
    $root.StartsWith($checkoutRoot + [IO.Path]::DirectorySeparatorChar,
        [StringComparison]::OrdinalIgnoreCase) -or
    $checkoutRoot.StartsWith($root + [IO.Path]::DirectorySeparatorChar,
        [StringComparison]::OrdinalIgnoreCase)) {
    throw 'TestRoot must be isolated from the repository checkout'
}
$ancestor = $root
while ($ancestor) {
    $item = Get-Item -LiteralPath $ancestor -Force -ErrorAction SilentlyContinue
    if ($null -ne $item -and ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw 'TestRoot may not traverse a symbolic link or junction'
    }
    $parent = [IO.Path]::GetDirectoryName($ancestor.TrimEnd('\', '/'))
    if (-not $parent -or $parent -eq $ancestor) { break }
    $ancestor = $parent
}
if ([IO.Directory]::Exists($root) -or [IO.File]::Exists($root)) {
    throw 'TestRoot must be fresh; refusing to reuse it'
}
if (-not [IO.Directory]::Exists([IO.Path]::GetDirectoryName($root))) {
    throw 'TestRoot parent must already exist'
}
[IO.Directory]::CreateDirectory($root) | Out-Null
$prism = Join-Path $root 'fixture-prism'
$meta = Join-Path $prism 'meta/net.minecraft'
$objects = Join-Path $prism 'assets/objects'
[IO.Directory]::CreateDirectory($meta) | Out-Null
[IO.Directory]::CreateDirectory($objects) | Out-Null
$manifestFile = Join-Path $meta '1.20.2.json'
$manifest = @{
    mainJar = @{ name = 'com.mojang:minecraft:1.20.2:client' }
    assetIndex = @{
        id = '8'
        sha1 = '21beaec863755c8fd4620b22ed9bdbc6718b3c32'
        size = 416851
        url = 'https://piston-meta.mojang.com/v1/packages/21beaec863755c8fd4620b22ed9bdbc6718b3c32/8.json'
    }
}
$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $manifestFile -Encoding utf8
$stage = Join-Path $PSScriptRoot 'Stage-Exact1202AssetIndex.ps1'
. (Join-Path $PSScriptRoot 'AssetIndexCoverage.ps1')

$smallIndex = Join-Path $root 'wrong-size.json'
'{"objects":{}}' | Set-Content -LiteralPath $smallIndex -Encoding utf8
$wrongHashIndex = Join-Path $root 'wrong-hash.json'
[IO.File]::WriteAllBytes($wrongHashIndex, ([byte[]]::new(416851)))
$badOutput = Join-Path $root 'rejected-output'
Assert-Rejected 'wrong size' {
    & $stage -PrismRoot $prism -OutputRoot $badOutput -IndexFile $smallIndex
} 'size mismatch'
Assert-True (-not [IO.Directory]::Exists($badOutput)) 'wrong-size input created output'
Assert-Rejected 'same-sized substitute index' {
    & $stage -PrismRoot $prism -OutputRoot $badOutput -IndexFile $wrongHashIndex
} 'SHA-1 mismatch'
Assert-True (-not [IO.Directory]::Exists($badOutput)) 'wrong-hash input created output'
Assert-Rejected 'ambiguous acquisition' {
    & $stage -PrismRoot $prism -OutputRoot $badOutput -IndexFile $smallIndex -AllowDownload
} 'exactly one'

$manifest.assetIndex.sha1 = 'be795ea589c9dc3f23a1573de90699fea1696bb1'
$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $manifestFile -Encoding utf8
Assert-Rejected 'Gradle manifest substitution' {
    & $stage -PrismRoot $prism -OutputRoot $badOutput -AllowDownload -PreflightOnly
} 'does not declare the exact'
$manifest.assetIndex.sha1 = '21beaec863755c8fd4620b22ed9bdbc6718b3c32'
$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $manifestFile -Encoding utf8

$preflight = & $stage -PrismRoot $prism -OutputRoot $badOutput -AllowDownload -PreflightOnly
Assert-True ($preflight.preflight_only -and $preflight.download_required -and
    -not $preflight.index_verified) 'download preflight did not remain read-only'
Assert-True (-not [IO.Directory]::Exists($badOutput)) 'download preflight created output'
Assert-Rejected 'output inside Prism' {
    & $stage -PrismRoot $prism -OutputRoot (Join-Path $prism 'new-output') -AllowDownload -PreflightOnly
} 'isolated from Prism'
$checkoutOutput = Join-Path $checkoutRoot ('asset-index-test-output-' + [guid]::NewGuid().ToString('N'))
Assert-True (-not (Test-Path -LiteralPath $checkoutOutput)) 'checkout output candidate already exists'
Assert-Rejected 'output inside repository checkout' {
    & $stage -PrismRoot $prism -OutputRoot $checkoutOutput -AllowDownload -PreflightOnly
} 'repository checkout'
Assert-True (-not (Test-Path -LiteralPath $checkoutOutput)) 'checkout preflight created output'
Assert-Rejected 'output above repository checkout' {
    & $stage -PrismRoot $prism -OutputRoot ([IO.Path]::GetDirectoryName($checkoutRoot)) `
        -AllowDownload -PreflightOnly
} 'repository checkout'
[IO.Directory]::CreateDirectory($badOutput) | Out-Null
Assert-Rejected 'existing output' {
    & $stage -PrismRoot $prism -OutputRoot $badOutput -AllowDownload -PreflightOnly
} 'already exists'

$coveredBytes = [Text.Encoding]::UTF8.GetBytes('covered')
$missingBytes = [Text.Encoding]::UTF8.GetBytes('missing')
$invalidBytes = [Text.Encoding]::UTF8.GetBytes('invalid')
$coveredHash = [Convert]::ToHexString([Security.Cryptography.SHA1]::HashData($coveredBytes)).ToLowerInvariant()
$missingHash = [Convert]::ToHexString([Security.Cryptography.SHA1]::HashData($missingBytes)).ToLowerInvariant()
$invalidHash = [Convert]::ToHexString([Security.Cryptography.SHA1]::HashData($invalidBytes)).ToLowerInvariant()
$coveredDirectory = Join-Path $objects $coveredHash.Substring(0, 2)
$invalidDirectory = Join-Path $objects $invalidHash.Substring(0, 2)
[IO.Directory]::CreateDirectory($coveredDirectory) | Out-Null
[IO.Directory]::CreateDirectory($invalidDirectory) | Out-Null
[IO.File]::WriteAllBytes((Join-Path $coveredDirectory $coveredHash), $coveredBytes)
[IO.File]::WriteAllBytes((Join-Path $invalidDirectory $invalidHash),
    [Text.Encoding]::UTF8.GetBytes('badbyte'))
$syntheticIndex = Join-Path $root 'synthetic-index.json'
@{
    objects = @{
        'covered' = @{ hash = $coveredHash; size = $coveredBytes.Length }
        'alias/covered' = @{ hash = $coveredHash; size = $coveredBytes.Length }
        'missing' = @{ hash = $missingHash; size = $missingBytes.Length }
        'invalid' = @{ hash = $invalidHash; size = $invalidBytes.Length }
    }
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $syntheticIndex -Encoding utf8
$coverage = Get-AssetObjectCoverage $syntheticIndex $objects
Assert-True ($coverage.required_names -eq 4 -and $coverage.required_unique_objects -eq 3 -and
    $coverage.cached_verified_objects -eq 1 -and $coverage.cache_missing_objects -eq 1 -and
    $coverage.cache_invalid_objects -eq 1 -and $coverage.cached_verified_bytes -eq $coveredBytes.Length -and
    @($coverage.cache_missing_sha1).Count -eq 1 -and $coverage.cache_missing_sha1[0] -eq $missingHash -and
    @($coverage.cache_invalid_sha1).Count -eq 1 -and $coverage.cache_invalid_sha1[0] -eq $invalidHash) `
    'read-only coverage did not distinguish aliases, missing objects and corrupt objects'
$malformedIndex = Join-Path $root 'malformed-index.json'
'not json' | Set-Content -LiteralPath $malformedIndex -Encoding utf8
Assert-Rejected 'malformed object index' {
    Get-AssetObjectCoverage $malformedIndex $objects
} 'Conversion from JSON failed'
$conflictingIndex = Join-Path $root 'conflicting-index.json'
@{
    objects = @{
        'first' = @{ hash = $coveredHash; size = $coveredBytes.Length }
        'second' = @{ hash = $coveredHash; size = $coveredBytes.Length + 1 }
    }
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $conflictingIndex -Encoding utf8
Assert-Rejected 'conflicting alias sizes' {
    Get-AssetObjectCoverage $conflictingIndex $objects
} 'conflicting sizes'

if ($ExactIndexFile) {
    $positiveOutput = Join-Path $root 'exact-stage'
    $receipt = & $stage -PrismRoot $prism -OutputRoot $positiveOutput -IndexFile $ExactIndexFile
    Assert-True ($receipt.asset_index_sha1 -eq '21beaec863755c8fd4620b22ed9bdbc6718b3c32' -and
        $receipt.cache_coverage.required_unique_objects -gt 0) 'exact index was not staged'
    Assert-True ([IO.File]::Exists((Join-Path $positiveOutput 'assets/indexes/8.json'))) `
        'exact index was not copied into scratch'
    $receiptText = [IO.File]::ReadAllText((Join-Path $positiveOutput 'coverage.json'))
    Assert-True ($receiptText -notmatch [regex]::Escape($root)) 'receipt included a local path'
}

[pscustomobject]@{
    schema = 'sfm-release-asset-index-stage-tests/1'
    passed = $true
    exact_stage_exercised = [bool] $ExactIndexFile
    artifacts_retained = $true
}
