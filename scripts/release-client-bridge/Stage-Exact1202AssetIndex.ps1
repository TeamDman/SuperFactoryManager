<#
Test-only staging of the exact Prism-manifest Minecraft 1.20.2 asset index.
The Prism launcher cache is read-only. No asset objects are downloaded or copied.
Only a new scratch OutputRoot is written, and its coverage report is path-free.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $PrismRoot,
    [Parameter(Mandatory)] [string] $OutputRoot,
    [string] $IndexFile,
    [switch] $AllowDownload,
    [switch] $PreflightOnly
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$expectedId = '8'
$expectedSha1 = '21beaec863755c8fd4620b22ed9bdbc6718b3c32'
$expectedSize = 416851L
$expectedUrl = 'https://piston-meta.mojang.com/v1/packages/21beaec863755c8fd4620b22ed9bdbc6718b3c32/8.json'

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

function Assert-ExactIndex([string] $Path) {
    if (-not [IO.File]::Exists($Path)) { throw "Missing exact asset index: $Path" }
    if (([IO.FileInfo]::new($Path)).Length -ne $expectedSize) {
        throw 'Exact Minecraft 1.20.2 asset index size mismatch'
    }
    $actual = (Get-FileHash -LiteralPath $Path -Algorithm SHA1).Hash.ToLowerInvariant()
    if ($actual -cne $expectedSha1) { throw 'Exact Minecraft 1.20.2 asset index SHA-1 mismatch' }
}

function Invoke-PinnedDownload([string] $Url, [string] $Destination) {
    $handler = [Net.Http.HttpClientHandler]::new()
    $handler.AllowAutoRedirect = $false
    $client = [Net.Http.HttpClient]::new($handler)
    $client.Timeout = [TimeSpan]::FromSeconds(60)
    $response = $null
    $networkStream = $null
    $fileStream = $null
    try {
        $response = $client.GetAsync($Url, [Net.Http.HttpCompletionOption]::ResponseHeadersRead).
            GetAwaiter().GetResult()
        if ([int] $response.StatusCode -ne 200) {
            throw "Exact asset index download returned HTTP $([int] $response.StatusCode); redirects are refused"
        }
        $declaredLength = $response.Content.Headers.ContentLength
        if ($null -ne $declaredLength -and $declaredLength -ne $expectedSize) {
            throw 'Exact asset index download declared the wrong size'
        }
        $networkStream = $response.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
        $fileStream = [IO.FileStream]::new($Destination, [IO.FileMode]::CreateNew,
            [IO.FileAccess]::Write, [IO.FileShare]::None)
        $buffer = [byte[]]::new(65536)
        [long] $received = 0
        while (($count = $networkStream.Read($buffer, 0, $buffer.Length)) -gt 0) {
            $received += $count
            if ($received -gt $expectedSize) { throw 'Exact asset index download exceeded the pinned size' }
            $fileStream.Write($buffer, 0, $count)
        }
        if ($received -ne $expectedSize) { throw 'Exact asset index download was incomplete' }
    } finally {
        if ($null -ne $fileStream) { $fileStream.Dispose() }
        if ($null -ne $networkStream) { $networkStream.Dispose() }
        if ($null -ne $response) { $response.Dispose() }
        $client.Dispose()
        $handler.Dispose()
    }
}

$hasLocalFile = -not [string]::IsNullOrWhiteSpace($IndexFile)
if ($hasLocalFile -eq [bool] $AllowDownload) {
    throw 'Choose exactly one of -IndexFile or -AllowDownload'
}

$prism = [IO.Path]::GetFullPath($PrismRoot).TrimEnd('\', '/')
if (-not [IO.Directory]::Exists($prism)) { throw 'PrismRoot does not exist' }
$manifestPath = Join-Path $prism 'meta/net.minecraft/1.20.2.json'
if (-not [IO.File]::Exists($manifestPath)) { throw 'Missing Prism Minecraft 1.20.2 manifest' }
$manifest = [IO.File]::ReadAllText($manifestPath) | ConvertFrom-Json
if ($null -eq $manifest.assetIndex -or [string] $manifest.assetIndex.id -cne $expectedId -or
    [string] $manifest.assetIndex.sha1 -cne $expectedSha1 -or
    [long] $manifest.assetIndex.size -ne $expectedSize -or
    [string] $manifest.assetIndex.url -cne $expectedUrl -or
    [string] $manifest.mainJar.name -cne 'com.mojang:minecraft:1.20.2:client') {
    throw 'Prism manifest does not declare the exact Minecraft 1.20.2 asset index'
}

$rawOutput = [IO.Path]::GetFullPath($OutputRoot)
if ($rawOutput -eq [IO.Path]::GetPathRoot($rawOutput)) { throw 'OutputRoot may not be a volume root' }
$output = $rawOutput.TrimEnd('\', '/')
$profileRoot = [Environment]::GetFolderPath([Environment+SpecialFolder]::UserProfile)
$checkoutRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\', '/')
if ((Test-IsWithinOrSame $output $prism) -or (Test-IsWithinOrSame $prism $output) -or
    (Test-IsWithinOrSame $output $profileRoot) -or
    (Test-IsWithinOrSame $output $checkoutRoot) -or
    (Test-IsWithinOrSame $checkoutRoot $output)) {
    throw 'OutputRoot must be isolated from Prism, the user profile and repository checkout'
}
Assert-NoReparseAncestor $output
if ($null -ne (Get-Item -LiteralPath $output -Force -ErrorAction SilentlyContinue)) {
    throw 'OutputRoot already exists; refusing to reuse it'
}
if (-not [IO.Directory]::Exists([IO.Path]::GetDirectoryName($output))) {
    throw 'OutputRoot parent must already exist'
}

$localIndex = if ($hasLocalFile) { [IO.Path]::GetFullPath($IndexFile) } else { $null }
if ($hasLocalFile) { Assert-ExactIndex $localIndex }
$objectsRoot = Join-Path $prism 'assets/objects'
. (Join-Path $PSScriptRoot 'AssetIndexCoverage.ps1')

if ($PreflightOnly) {
    $coverage = if ($hasLocalFile) { Get-AssetObjectCoverage $localIndex $objectsRoot } else { $null }
    return [pscustomobject]@{
        schema = 'sfm-release-asset-index-stage/1'
        minecraft = '1.20.2'
        asset_index_id = $expectedId
        asset_index_sha1 = $expectedSha1
        asset_index_bytes = $expectedSize
        preflight_only = $true
        index_verified = $hasLocalFile
        download_required = [bool] $AllowDownload
        cache_coverage = $coverage
    }
}

[IO.Directory]::CreateDirectory((Join-Path $output 'assets/indexes')) | Out-Null
$stagedIndex = Join-Path $output 'assets/indexes/8.json'
if ($hasLocalFile) {
    Copy-Item -LiteralPath $localIndex -Destination $stagedIndex -ErrorAction Stop
} else {
    $partial = "$stagedIndex.partial"
    Invoke-PinnedDownload $expectedUrl $partial
    Assert-ExactIndex $partial
    [IO.File]::Move($partial, $stagedIndex)
}
Assert-ExactIndex $stagedIndex
$coverage = Get-AssetObjectCoverage $stagedIndex $objectsRoot
$receipt = [pscustomobject]@{
    schema = 'sfm-release-asset-index-stage/1'
    minecraft = '1.20.2'
    asset_index_id = $expectedId
    asset_index_sha1 = $expectedSha1
    asset_index_bytes = $expectedSize
    source = if ($hasLocalFile) { 'explicit_file' } else { 'manifest_url_download' }
    cache_access = 'read_only'
    asset_objects_staged = 0
    cache_coverage = $coverage
}
$receipt | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $output 'coverage.json') -Encoding utf8
return $receipt
