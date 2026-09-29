<#
Test-only, exact Minecraft 1.20.2 asset staging. Read the Prism object cache,
copy verified objects into a fresh scratch root, and optionally fetch only the
three reviewed cache misses from Minecraft's asset CDN. Never write to Prism.
On any failure, including disk exhaustion, retain the partial scratch output;
there is no cleanup or automatic retry.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $PrismRoot,
    [Parameter(Mandatory)] [string] $ExactIndexFile,
    [Parameter(Mandatory)] [string] $OutputRoot,
    [switch] $AllowDownloadMissing,
    [switch] $PreflightOnly
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$expectedIndexSha1 = '21beaec863755c8fd4620b22ed9bdbc6718b3c32'
$expectedIndexBytes = 416851L
$expectedMissing = @{
    '141458e36420686689a4a804fc66b2cd131cd6a1' = 844134L
    '1b3b43234e41b3de5da436ea098ebb84f8363540' = 479490L
    'a9b1ed0987ebce1032d83588fece1ef12642b602' = 20430L
}

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

function Assert-Object([string] $Path, [string] $Hash, [long] $Size) {
    if (-not [IO.File]::Exists($Path)) { throw "Missing asset object $Hash" }
    if (([IO.FileInfo]::new($Path)).Length -ne $Size -or
        (Get-FileHash -LiteralPath $Path -Algorithm SHA1).Hash.ToLowerInvariant() -cne $Hash) {
        throw "Asset object size or SHA-1 mismatch: $Hash"
    }
}

function Invoke-ExactObjectDownload([string] $Hash, [long] $Size, [string] $Destination) {
    $url = "https://resources.download.minecraft.net/$($Hash.Substring(0, 2))/$Hash"
    $handler = [Net.Http.HttpClientHandler]::new()
    $handler.AllowAutoRedirect = $false
    $client = [Net.Http.HttpClient]::new($handler)
    $client.Timeout = [TimeSpan]::FromSeconds(120)
    $response = $null
    $networkStream = $null
    $fileStream = $null
    try {
        $response = $client.GetAsync($url, [Net.Http.HttpCompletionOption]::ResponseHeadersRead).
            GetAwaiter().GetResult()
        if ([int] $response.StatusCode -ne 200) {
            throw "Exact asset object $Hash returned HTTP $([int] $response.StatusCode); redirects are refused"
        }
        $declaredLength = $response.Content.Headers.ContentLength
        if ($null -ne $declaredLength -and $declaredLength -ne $Size) {
            throw "Exact asset object $Hash declared the wrong size"
        }
        $networkStream = $response.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
        $fileStream = [IO.FileStream]::new($Destination, [IO.FileMode]::CreateNew,
            [IO.FileAccess]::Write, [IO.FileShare]::None)
        $buffer = [byte[]]::new(65536)
        [long] $received = 0
        while (($count = $networkStream.Read($buffer, 0, $buffer.Length)) -gt 0) {
            $received += $count
            if ($received -gt $Size) { throw "Exact asset object $Hash exceeded its pinned size" }
            $fileStream.Write($buffer, 0, $count)
        }
        if ($received -ne $Size) { throw "Exact asset object $Hash was incomplete" }
    } finally {
        if ($null -ne $fileStream) { $fileStream.Dispose() }
        if ($null -ne $networkStream) { $networkStream.Dispose() }
        if ($null -ne $response) { $response.Dispose() }
        $client.Dispose()
        $handler.Dispose()
    }
    Assert-Object $Destination $Hash $Size
}

$prism = [IO.Path]::GetFullPath($PrismRoot).TrimEnd('\', '/')
$indexFile = [IO.Path]::GetFullPath($ExactIndexFile)
$rawOutput = [IO.Path]::GetFullPath($OutputRoot)
if ($rawOutput -eq [IO.Path]::GetPathRoot($rawOutput)) { throw 'OutputRoot may not be a volume root' }
$output = $rawOutput.TrimEnd('\', '/')
$checkout = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\', '/')
$profileRoot = [Environment]::GetFolderPath([Environment+SpecialFolder]::UserProfile)
if ((Test-IsWithinOrSame $output $prism) -or (Test-IsWithinOrSame $prism $output) -or
    (Test-IsWithinOrSame $output $checkout) -or (Test-IsWithinOrSame $checkout $output) -or
    (Test-IsWithinOrSame $output $profileRoot) -or
    (Test-IsWithinOrSame $indexFile $output)) {
    throw 'OutputRoot must be isolated from Prism, the checkout, profile and exact index'
}
Assert-NoReparseAncestor $output
if ($null -ne (Get-Item -LiteralPath $output -Force -ErrorAction SilentlyContinue)) {
    throw 'OutputRoot already exists; refusing to reuse it'
}
if (-not [IO.Directory]::Exists([IO.Path]::GetDirectoryName($output))) {
    throw 'OutputRoot parent must already exist'
}
if (-not [IO.Directory]::Exists($prism)) { throw 'PrismRoot does not exist' }
if (-not [IO.File]::Exists($indexFile) -or
    ([IO.FileInfo]::new($indexFile)).Length -ne $expectedIndexBytes -or
    (Get-FileHash -LiteralPath $indexFile -Algorithm SHA1).Hash.ToLowerInvariant() -cne $expectedIndexSha1) {
    throw 'Exact Minecraft 1.20.2 asset index size or SHA-1 mismatch'
}

$objectsRoot = Join-Path $prism 'assets/objects'
. (Join-Path $PSScriptRoot 'AssetIndexCoverage.ps1')
$coverage = Get-AssetObjectCoverage $indexFile $objectsRoot
if ($coverage.required_names -ne 3630 -or $coverage.required_unique_objects -ne 3607 -or
    $coverage.required_unique_bytes -ne 646330719L -or $coverage.cache_invalid_objects -ne 0) {
    throw 'Exact index topology or Prism cache validity differs from the reviewed fixture'
}
foreach ($hash in $coverage.cache_missing_sha1) {
    if (-not $expectedMissing.ContainsKey($hash)) {
        throw "Unreviewed missing asset object: $hash"
    }
}
if ($PreflightOnly) {
    return [pscustomobject]@{
        schema = 'sfm-release-asset-objects-preflight/1'
        minecraft = '1.20.2'
        asset_index_sha1 = $expectedIndexSha1
        cache_coverage = $coverage
        download_allowed = [bool] $AllowDownloadMissing
        output_created = $false
    }
}
if ($coverage.cache_missing_objects -gt 0 -and -not $AllowDownloadMissing) {
    throw 'Exact asset objects are missing; pass -AllowDownloadMissing for the reviewed three hashes'
}

$stagedAssets = Join-Path $output 'assets'
$stagedObjects = Join-Path $stagedAssets 'objects'
$stagedIndexes = Join-Path $stagedAssets 'indexes'
[IO.Directory]::CreateDirectory($stagedObjects) | Out-Null
[IO.Directory]::CreateDirectory($stagedIndexes) | Out-Null
$stagedIndex = Join-Path $stagedIndexes '8.json'
Copy-Item -LiteralPath $indexFile -Destination $stagedIndex -ErrorAction Stop
if ((Get-FileHash -LiteralPath $stagedIndex -Algorithm SHA1).Hash.ToLowerInvariant() -cne $expectedIndexSha1) {
    throw 'Staged asset index SHA-1 mismatch'
}
$index = [IO.File]::ReadAllText($stagedIndex) | ConvertFrom-Json
$required = [Collections.Generic.Dictionary[string,long]]::new([StringComparer]::Ordinal)
foreach ($entry in $index.objects.PSObject.Properties) {
    $required[[string] $entry.Value.hash] = [long] $entry.Value.size
}
foreach ($hash in $expectedMissing.Keys) {
    if (-not $required.ContainsKey($hash) -or $required[$hash] -ne $expectedMissing[$hash]) {
        throw "Reviewed missing-object descriptor changed: $hash"
    }
}
$downloaded = [Collections.Generic.List[string]]::new()
foreach ($hash in @($required.Keys | Sort-Object -CaseSensitive)) {
    $relative = Join-Path $hash.Substring(0, 2) $hash
    $source = Join-Path $objectsRoot $relative
    $destination = Join-Path $stagedObjects $relative
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination)) | Out-Null
    if ([IO.File]::Exists($source)) {
        Copy-Item -LiteralPath $source -Destination $destination -ErrorAction Stop
        Assert-Object $destination $hash $required[$hash]
    } else {
        if (-not $AllowDownloadMissing -or -not $expectedMissing.ContainsKey($hash) -or
            $required[$hash] -ne $expectedMissing[$hash]) {
            throw "Asset object disappeared or is not approved for download: $hash"
        }
        Invoke-ExactObjectDownload $hash $required[$hash] $destination
        $downloaded.Add($hash)
    }
}
$stagedCoverage = Get-AssetObjectCoverage $stagedIndex $stagedObjects
if ($stagedCoverage.cached_verified_objects -ne 3607 -or
    $stagedCoverage.cache_missing_objects -ne 0 -or $stagedCoverage.cache_invalid_objects -ne 0) {
    throw 'Staged exact asset objects failed complete coverage verification'
}
$receipt = [pscustomobject]@{
    schema = 'sfm-release-asset-objects-stage/1'
    minecraft = '1.20.2'
    asset_index_sha1 = $expectedIndexSha1
    required_unique_objects = $stagedCoverage.required_unique_objects
    required_unique_bytes = $stagedCoverage.required_unique_bytes
    copied_verified_objects = $stagedCoverage.required_unique_objects - $downloaded.Count
    downloaded_verified_objects = $downloaded.Count
    downloaded_sha1 = [string[]] $downloaded.ToArray()
    prism_cache_access = 'read_only'
    staged_coverage_complete = $true
}
$receipt | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $output 'asset-objects.json') -Encoding utf8
return $receipt
