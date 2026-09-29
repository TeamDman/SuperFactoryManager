<#
Test-only, offline staging of the exact Minecraft asset index 12 shared by
1.20.3 and 1.20.4. The launcher cache is read-only. A failure leaves scratch
output intact for inspection; this script never cleans or retries it.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $PrismRoot,
    [Parameter(Mandatory)] [string] $OutputRoot,
    [switch] $PreflightOnly
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$expectedIndexSha1 = '5060d8c8c8f6a52cea32ea9ccd68c9c92e5b74e7'
$expectedIndexBytes = 437785L
$expectedObjects = 3787L
$expectedBytes = 650581001L

function Test-IsWithinOrSame([string] $Path, [string] $Root) {
    $trimmedRoot = $Root.TrimEnd('\', '/')
    return $Path.Equals($trimmedRoot, [StringComparison]::OrdinalIgnoreCase) -or
        $Path.StartsWith($trimmedRoot + [IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase)
}

function Assert-NoReparseAncestor([string] $Path) {
    $current = $Path
    while ($current) {
        $item = Get-Item -LiteralPath $current -Force -ErrorAction SilentlyContinue
        if ($null -ne $item -and ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw 'OutputRoot may not traverse a symbolic link or junction'
        }
        $parent = [IO.Path]::GetDirectoryName($current.TrimEnd('\', '/'))
        if (-not $parent -or $parent -eq $current) { break }
        $current = $parent
    }
}

$prism = [IO.Path]::GetFullPath($PrismRoot).TrimEnd('\', '/')
$rawOutput = [IO.Path]::GetFullPath($OutputRoot)
if ($rawOutput -eq [IO.Path]::GetPathRoot($rawOutput)) { throw 'OutputRoot may not be a volume root' }
$output = $rawOutput.TrimEnd('\', '/')
$checkout = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\', '/')
$profileRoot = [Environment]::GetFolderPath([Environment+SpecialFolder]::UserProfile)
if ((Test-IsWithinOrSame $output $prism) -or (Test-IsWithinOrSame $prism $output) -or
    (Test-IsWithinOrSame $output $checkout) -or (Test-IsWithinOrSame $checkout $output) -or
    (Test-IsWithinOrSame $output $profileRoot)) {
    throw 'OutputRoot must be isolated from Prism, the checkout and profile'
}
Assert-NoReparseAncestor $output
if ($null -ne (Get-Item -LiteralPath $output -Force -ErrorAction SilentlyContinue)) {
    throw 'OutputRoot already exists; refusing to reuse it'
}
if (-not [IO.Directory]::Exists([IO.Path]::GetDirectoryName($output))) {
    throw 'OutputRoot parent must already exist'
}
if (-not [IO.Directory]::Exists($prism)) { throw 'PrismRoot does not exist' }

$indexFile = Join-Path $prism 'assets/indexes/12.json'
if (-not [IO.File]::Exists($indexFile) -or
    ([IO.FileInfo]::new($indexFile)).Length -ne $expectedIndexBytes -or
    (Get-FileHash -LiteralPath $indexFile -Algorithm SHA1).Hash.ToLowerInvariant() -cne $expectedIndexSha1) {
    throw 'Exact Minecraft asset index 12 size or SHA-1 mismatch'
}

$objectsRoot = Join-Path $prism 'assets/objects'
. (Join-Path $PSScriptRoot 'AssetIndexCoverage.ps1')
$coverage = Get-AssetObjectCoverage $indexFile $objectsRoot
if ($coverage.required_names -ne 3810 -or
    $coverage.required_unique_objects -ne $expectedObjects -or
    $coverage.required_unique_bytes -ne $expectedBytes -or
    $coverage.cached_verified_objects -ne $expectedObjects -or
    $coverage.cache_missing_objects -ne 0 -or $coverage.cache_invalid_objects -ne 0) {
    throw 'Exact index topology or launcher-cache coverage differs from the reviewed fixture'
}
if ($PreflightOnly) {
    return [pscustomobject]@{
        schema = 'sfm-release-asset-objects-preflight/1'
        minecraft = '1.20.3+1.20.4'
        asset_index_sha1 = $expectedIndexSha1
        cache_coverage = $coverage
        output_created = $false
    }
}

$stagedAssets = Join-Path $output 'assets'
$stagedObjects = Join-Path $stagedAssets 'objects'
$stagedIndexes = Join-Path $stagedAssets 'indexes'
[IO.Directory]::CreateDirectory($stagedObjects) | Out-Null
[IO.Directory]::CreateDirectory($stagedIndexes) | Out-Null
$stagedIndex = Join-Path $stagedIndexes '12.json'
Copy-Item -LiteralPath $indexFile -Destination $stagedIndex -ErrorAction Stop
if ((Get-FileHash -LiteralPath $stagedIndex -Algorithm SHA1).Hash.ToLowerInvariant() -cne $expectedIndexSha1) {
    throw 'Staged asset index SHA-1 mismatch'
}

$index = [IO.File]::ReadAllText($stagedIndex) | ConvertFrom-Json
$required = [Collections.Generic.Dictionary[string,long]]::new([StringComparer]::Ordinal)
foreach ($entry in $index.objects.PSObject.Properties) {
    $required[[string] $entry.Value.hash] = [long] $entry.Value.size
}
foreach ($hash in @($required.Keys | Sort-Object -CaseSensitive)) {
    $relative = Join-Path $hash.Substring(0, 2) $hash
    $source = Join-Path $objectsRoot $relative
    $destination = Join-Path $stagedObjects $relative
    if (-not [IO.File]::Exists($source) -or
        ([IO.FileInfo]::new($source)).Length -ne $required[$hash] -or
        (Get-FileHash -LiteralPath $source -Algorithm SHA1).Hash.ToLowerInvariant() -cne $hash) {
        throw "Launcher asset disappeared or changed after preflight: $hash"
    }
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination)) | Out-Null
    Copy-Item -LiteralPath $source -Destination $destination -ErrorAction Stop
    if (([IO.FileInfo]::new($destination)).Length -ne $required[$hash] -or
        (Get-FileHash -LiteralPath $destination -Algorithm SHA1).Hash.ToLowerInvariant() -cne $hash) {
        throw "Staged asset size or SHA-1 mismatch: $hash"
    }
}
$stagedCoverage = Get-AssetObjectCoverage $stagedIndex $stagedObjects
if ($stagedCoverage.cached_verified_objects -ne $expectedObjects -or
    $stagedCoverage.cache_missing_objects -ne 0 -or $stagedCoverage.cache_invalid_objects -ne 0) {
    throw 'Staged exact asset objects failed complete coverage verification'
}
$receipt = [pscustomobject]@{
    schema = 'sfm-release-asset-objects-stage/1'
    minecraft = '1.20.3+1.20.4'
    asset_index_sha1 = $expectedIndexSha1
    required_unique_objects = $expectedObjects
    required_unique_bytes = $expectedBytes
    copied_verified_objects = $expectedObjects
    downloaded_verified_objects = 0
    prism_cache_access = 'read_only'
    staged_coverage_complete = $true
}
$json = $receipt | ConvertTo-Json -Depth 4
$receiptFile = Join-Path $output 'asset-objects.json'
$stream = [IO.FileStream]::new($receiptFile, [IO.FileMode]::CreateNew,
    [IO.FileAccess]::Write, [IO.FileShare]::None)
try {
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes($json + "`n")
    $stream.Write($bytes, 0, $bytes.Length)
} finally {
    $stream.Dispose()
}
return $receipt
