<#
Purely read-only asset-object coverage for a verified Minecraft asset index.
This helper does not fetch or copy objects and returns no machine-local paths.
#>
function Get-AssetObjectCoverage {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string] $IndexFile,
        [Parameter(Mandatory)] [string] $ObjectsRoot
    )

    $index = [IO.File]::ReadAllText($IndexFile) | ConvertFrom-Json
    if ($null -eq $index -or $null -eq $index.PSObject.Properties['objects'] -or
        $index.objects -isnot [pscustomobject]) {
        throw 'Asset index has no objects map'
    }

    $required = [Collections.Generic.Dictionary[string,long]]::new([StringComparer]::Ordinal)
    [long] $nameCount = 0
    foreach ($entry in $index.objects.PSObject.Properties) {
        $nameCount++
        $descriptor = $entry.Value
        if ($null -eq $descriptor -or $null -eq $descriptor.PSObject.Properties['hash'] -or
            $null -eq $descriptor.PSObject.Properties['size']) {
            throw 'Asset index has an incomplete object descriptor'
        }
        $hash = [string] $descriptor.hash
        [long] $size = 0
        if ($hash -cnotmatch '^[0-9a-f]{40}$' -or
            -not [long]::TryParse([string] $descriptor.size, [ref] $size) -or $size -lt 0) {
            throw 'Asset index has an invalid object hash or size'
        }
        [long] $previous = 0
        if ($required.TryGetValue($hash, [ref] $previous)) {
            if ($previous -ne $size) { throw 'Asset index assigns conflicting sizes to one object hash' }
        } else {
            $required.Add($hash, $size)
        }
    }
    if ($nameCount -eq 0) { throw 'Asset index has no required objects' }

    [long] $requiredBytes = 0
    [long] $verifiedBytes = 0
    [long] $missingBytes = 0
    [long] $invalidBytes = 0
    [long] $verifiedCount = 0
    [long] $missingCount = 0
    [long] $invalidCount = 0
    $missingHashes = [Collections.Generic.List[string]]::new()
    $invalidHashes = [Collections.Generic.List[string]]::new()
    foreach ($object in $required.GetEnumerator()) {
        $hash = $object.Key
        $size = $object.Value
        $requiredBytes += $size
        $path = Join-Path $ObjectsRoot (Join-Path $hash.Substring(0, 2) $hash)
        if (-not [IO.File]::Exists($path)) {
            $missingCount++
            $missingBytes += $size
            $missingHashes.Add($hash)
            continue
        }
        if (([IO.FileInfo]::new($path)).Length -ne $size -or
            (Get-FileHash -LiteralPath $path -Algorithm SHA1).Hash.ToLowerInvariant() -cne $hash) {
            $invalidCount++
            $invalidBytes += $size
            $invalidHashes.Add($hash)
            continue
        }
        $verifiedCount++
        $verifiedBytes += $size
    }
    $missingHashes.Sort([StringComparer]::Ordinal)
    $invalidHashes.Sort([StringComparer]::Ordinal)

    return [pscustomobject]@{
        required_names = $nameCount
        required_unique_objects = [long] $required.Count
        required_unique_bytes = $requiredBytes
        cached_verified_objects = $verifiedCount
        cached_verified_bytes = $verifiedBytes
        cache_missing_objects = $missingCount
        cache_missing_bytes = $missingBytes
        cache_invalid_objects = $invalidCount
        cache_invalid_bytes = $invalidBytes
        cache_missing_sha1 = [string[]] $missingHashes.ToArray()
        cache_invalid_sha1 = [string[]] $invalidHashes.ToArray()
    }
}
