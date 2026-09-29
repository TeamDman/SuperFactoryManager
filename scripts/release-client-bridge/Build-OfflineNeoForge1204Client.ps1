<#
Test-only reconstruction of the exact Minecraft 1.20.4 / NeoForge 20.4.231
patched client. Installer, launcher/tool caches and source JARs are read-only.
All processor inputs and outputs are copied into a fresh scratch OutputRoot.
No downloader, Gradle task or Minecraft client is invoked.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $Installer,
    [Parameter(Mandatory)] [string] $CachedLibraries,
    [Parameter(Mandatory)] [string] $VanillaClient,
    [Parameter(Mandatory)] [string] $MojangMappings,
    [Parameter(Mandatory)] [string] $CachedPatchedClient,
    [Parameter(Mandatory)] [string] $JavaHome,
    [Parameter(Mandatory)] [string] $OutputRoot,
    [string] $ReferenceRoot,
    [switch] $PreflightOnly,
    [ValidateRange(30, 900)] [int] $WatchdogSeconds = 600
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$installerSha256 = '8002077d9454603611b0bb5fd66a107c2dd2a27942ebe52c39db2a9280eaac18'
$vanillaSha1 = 'fd19469fed4a4b4c15b2d5133985f0e3e7816a8a'
$mojmapsSha1 = 'be76ecc174ea25580bdc9bf335481a5192d9f3b7'
$neoformSha1 = 'ad0bdcc5c21199e1a9cb05836f9660c22830a4fe'
$javaSha256 = '186d651179d34ce21d857597bb88a7b1e244973e64f3a9bec1e9daaffd919e31'
$cachedPatchedSha1 = 'ec7cfa975d3b76c55088b728d4baa330b5ebeddd'
$cachedPatchedSha256 = 'bf12799c537727ce46e44d55b0f4924a92dab5ff25b8fcf9bcc64aa46865e1fe'
$cachedPatchedSize = 5288597L
$minecraftVersion = '1.20.4-20231207.154220'
$loaderVersion = 'neoforge-20.4.231'
$patchedRelative = 'libraries/net/neoforged/neoforge/20.4.231/neoforge-20.4.231-client.jar'

function Assert-File([string] $Path) {
    if (-not [IO.File]::Exists($Path)) { throw "Missing exact offline input: $Path" }
    return [IO.Path]::GetFullPath($Path)
}

function Assert-Sha1([string] $Path, [string] $Expected, [string] $Label) {
    $actual = (Get-FileHash -LiteralPath (Assert-File $Path) -Algorithm SHA1).Hash.ToLowerInvariant()
    if ($actual -cne $Expected) { throw "$Label SHA-1 mismatch: $actual" }
    return $actual
}

function Assert-Sha256([string] $Path, [string] $Expected, [string] $Label) {
    $actual = (Get-FileHash -LiteralPath (Assert-File $Path) -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -cne $Expected) { throw "$Label SHA-256 mismatch: $actual" }
    return $actual
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

function Get-ZipText([IO.Compression.ZipArchive] $Archive, [string] $EntryName) {
    $entry = $Archive.GetEntry($EntryName)
    if ($null -eq $entry) { throw "Installer is missing $EntryName" }
    $reader = [IO.StreamReader]::new($entry.Open())
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}

function Get-MavenRelative([string] $Coordinate) {
    $suffix = $Coordinate.Split('@', 2)
    $extension = if ($suffix.Count -eq 2) { $suffix[1] } else { 'jar' }
    $parts = $suffix[0].Split(':')
    if ($parts.Count -lt 3 -or $parts.Count -gt 4 -or $extension -notmatch '^(jar|zip)$' -or
        $parts[0] -notmatch '^[A-Za-z0-9_.-]+$' -or $parts[1] -notmatch '^[A-Za-z0-9_.-]+$' -or
        $parts[2] -notmatch '^[A-Za-z0-9_.+-]+$' -or
        ($parts.Count -eq 4 -and $parts[3] -notmatch '^[A-Za-z0-9_.-]+$')) {
        throw "Unexpected installer coordinate: $Coordinate"
    }
    $classifier = if ($parts.Count -eq 4) { '-' + $parts[3] } else { '' }
    return ($parts[0] -replace '\.', '/') + '/' + $parts[1] + '/' + $parts[2] + '/' +
        $parts[1] + '-' + $parts[2] + $classifier + '.' + $extension
}

function Quote-Arg([string] $Value) {
    if ($Value.Contains('"')) { throw 'Unquotable processor argument' }
    return '"' + $Value.Replace('\', '/') + '"'
}

function Invoke-Processor([string] $Name, $Descriptor, [string] $MainClass, [string[]] $Arguments) {
    $classPaths = [Collections.Generic.List[string]]::new()
    $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($coordinate in @([string] $Descriptor.jar) + @($Descriptor.classpath)) {
        $path = Assert-File (Join-Path $libraries (Get-MavenRelative ([string] $coordinate)))
        if ($seen.Add($path)) { $classPaths.Add($path) }
    }
    $stdout = Join-Path $output "$Name.stdout.log"
    $stderr = Join-Path $output "$Name.stderr.log"
    $javaArgs = @('-Xmx2g', '-cp', (Quote-Arg ($classPaths -join ';')), $MainClass) +
        @($Arguments | ForEach-Object { Quote-Arg $_ })
    $process = Start-Process -FilePath $java -ArgumentList $javaArgs -WorkingDirectory $output `
        -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
    $deadline = [DateTime]::UtcNow.AddSeconds($WatchdogSeconds)
    $diskPattern = 'No space left|There is not enough space|not enough space|ENOSPC|disk full|disk space|insufficient storage'
    while (-not $process.HasExited -and [DateTime]::UtcNow -lt $deadline) {
        Start-Sleep -Seconds 1
        $process.Refresh()
        foreach ($log in @($stdout, $stderr)) {
            if ([IO.File]::Exists($log) -and (Select-String -LiteralPath $log -Pattern $diskPattern -Quiet)) {
                if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force }
                throw "Disk-space diagnostic in $Name processor; stopped owned process without cleanup or retry"
            }
        }
    }
    foreach ($log in @($stdout, $stderr)) {
        if ([IO.File]::Exists($log) -and (Select-String -LiteralPath $log -Pattern $diskPattern -Quiet)) {
            if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force }
            throw "Disk-space diagnostic in $Name processor; stopped owned process without cleanup or retry"
        }
    }
    if (-not $process.HasExited) {
        Stop-Process -Id $process.Id -Force
        throw "Offline $Name processor watchdog expired; stopped owned process"
    }
    $process.Refresh()
    if ($process.ExitCode -ne 0) { throw "Offline $Name processor exited $($process.ExitCode); inspect retained scratch logs" }
}

function Assert-ReadableZip([string] $Path, [int] $MinimumEntries) {
    $archive = [IO.Compression.ZipFile]::OpenRead((Assert-File $Path))
    try {
        if ($archive.Entries.Count -lt $MinimumEntries) { throw 'Patched client ZIP has too few entries' }
        if ($null -eq $archive.GetEntry('net/minecraft/client/Minecraft.class')) {
            throw 'Patched client ZIP has no Minecraft client class'
        }
        [long] $uncompressed = 0
        $buffer = [byte[]]::new(65536)
        foreach ($entry in $archive.Entries) {
            $uncompressed += $entry.Length
            if ($uncompressed -gt 1073741824L) { throw 'Patched client ZIP exceeds the test-only size bound' }
            if ($entry.Name.Length -eq 0) { continue }
            $stream = $entry.Open()
            try {
                while ($stream.Read($buffer, 0, $buffer.Length) -gt 0) { }
            } finally { $stream.Dispose() }
        }
        return $archive.Entries.Count
    } finally { $archive.Dispose() }
}

function Assert-EqualBytes([string] $First, [string] $Second) {
    $firstStream = [IO.File]::OpenRead((Assert-File $First))
    $secondStream = [IO.File]::OpenRead((Assert-File $Second))
    try {
        if ($firstStream.Length -ne $secondStream.Length) { throw 'Scratch patched client differs from cached coordinate reference in size' }
        $firstBuffer = [byte[]]::new(65536)
        $secondBuffer = [byte[]]::new(65536)
        while ($true) {
            $count = $firstStream.Read($firstBuffer, 0, $firstBuffer.Length)
            if ($count -eq 0) { break }
            $read = 0
            while ($read -lt $count) {
                $next = $secondStream.Read($secondBuffer, $read, $count - $read)
                if ($next -eq 0) {
                    throw 'Scratch patched client differs from cached coordinate reference in length'
                }
                $read += $next
            }
            for ($index = 0; $index -lt $count; $index++) {
                if ($firstBuffer[$index] -ne $secondBuffer[$index]) {
                    throw 'Scratch patched client differs byte-for-byte from cached coordinate reference'
                }
            }
        }
    } finally {
        $firstStream.Dispose()
        $secondStream.Dispose()
    }
}

$installerFile = Assert-File $Installer
$clientSource = Assert-File $VanillaClient
$mojmapsSource = Assert-File $MojangMappings
$cachedPatchedSource = Assert-File $CachedPatchedClient
$cachedPatchedPath = $cachedPatchedSource.Replace('\', '/')
if (-not $cachedPatchedPath.EndsWith('/' + $patchedRelative, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Cached patched client does not occupy the installer-declared Maven coordinate'
}
$java = Assert-File (Join-Path $JavaHome 'bin/java.exe')
$cachedRoot = [IO.Path]::GetFullPath($CachedLibraries).TrimEnd('\', '/')
if (-not [IO.Directory]::Exists($cachedRoot)) { throw 'Missing cached processor libraries' }
$rawOutput = [IO.Path]::GetFullPath($OutputRoot)
if ($rawOutput -eq [IO.Path]::GetPathRoot($rawOutput)) { throw 'OutputRoot may not be a volume root' }
$output = $rawOutput.TrimEnd('\', '/')
$checkoutRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\', '/')
$profileRoot = [Environment]::GetFolderPath([Environment+SpecialFolder]::UserProfile)
$inputRoots = @($cachedRoot, [IO.Path]::GetDirectoryName($installerFile),
    [IO.Path]::GetDirectoryName($clientSource), [IO.Path]::GetDirectoryName($mojmapsSource),
    [IO.Path]::GetDirectoryName($cachedPatchedSource),
    [IO.Path]::GetFullPath($JavaHome).TrimEnd('\', '/'))
if ((Test-IsWithinOrSame $output $checkoutRoot) -or (Test-IsWithinOrSame $checkoutRoot $output) -or
    (Test-IsWithinOrSame $output $profileRoot)) {
    throw 'OutputRoot must be isolated from the repository checkout and user profile'
}
foreach ($inputRoot in $inputRoots) {
    if ((Test-IsWithinOrSame $output $inputRoot) -or (Test-IsWithinOrSame $inputRoot $output)) {
        throw 'OutputRoot must be isolated from all offline input directories'
    }
}
Assert-NoReparseAncestor $output
if ($null -ne (Get-Item -LiteralPath $output -Force -ErrorAction SilentlyContinue)) {
    throw 'OutputRoot already exists; refusing to reuse it'
}
if (-not [IO.Directory]::Exists([IO.Path]::GetDirectoryName($output))) {
    throw 'OutputRoot parent must already exist'
}

Assert-Sha256 $installerFile $installerSha256 'Exact NeoForge 20.4.231 installer' | Out-Null
Assert-Sha1 $clientSource $vanillaSha1 'Official Minecraft 1.20.4 client' | Out-Null
Assert-Sha1 $mojmapsSource $mojmapsSha1 'Official Minecraft 1.20.4 client mappings' | Out-Null
Assert-Sha256 $java $javaSha256 'Pinned JBR 17 Java executable' | Out-Null
if ([IO.FileInfo]::new($cachedPatchedSource).Length -ne $cachedPatchedSize) {
    throw 'Cached patched client size mismatch'
}
Assert-Sha1 $cachedPatchedSource $cachedPatchedSha1 'Cached patched client' | Out-Null
Assert-Sha256 $cachedPatchedSource $cachedPatchedSha256 'Cached patched client' | Out-Null
$cachedZipEntries = Assert-ReadableZip $cachedPatchedSource 1000

$stages = @(
    @{ Index = 3; Jar = 'net.neoforged.installertools:installertools:2.1.2'; Args = '--task MCP_DATA --input [net.neoforged:neoform:1.20.4-20231207.154220@zip] --output {MAPPINGS} --key mappings' },
    @{ Index = 5; Jar = 'net.neoforged.installertools:installertools:2.1.2'; Args = '--task MERGE_MAPPING --left {MAPPINGS} --right {MOJMAPS} --output {MERGED_MAPPINGS} --classes --fields --methods --reverse-right' },
    @{ Index = 6; Jar = 'net.neoforged.installertools:jarsplitter:2.1.2'; Args = '--input {MINECRAFT_JAR} --slim {MC_SLIM} --extra {MC_EXTRA} --srg {MERGED_MAPPINGS}' },
    @{ Index = 8; Jar = 'net.neoforged:AutoRenamingTool:1.0.13:all'; Args = '--input {MC_SLIM} --output {MC_SRG} --names {MERGED_MAPPINGS} --ann-fix --ids-fix --src-fix --record-fix' },
    @{ Index = 9; Jar = 'net.neoforged.installertools:binarypatcher:2.1.2'; Args = '--clean {MC_SRG} --output {PATCHED} --apply {BINPATCH}' }
)

$zip = [IO.Compression.ZipFile]::OpenRead($installerFile)
try {
    $profile = Get-ZipText $zip 'install_profile.json' | ConvertFrom-Json
    $version = Get-ZipText $zip 'version.json' | ConvertFrom-Json
    if ($profile.version -cne $loaderVersion -or $version.id -cne $loaderVersion -or
        $version.inheritsFrom -cne '1.20.4' -or @($profile.processors).Count -ne 10 -or
        $profile.data.PATCHED.client -cne '[net.neoforged:neoforge:20.4.231:client]' -or
        $profile.data.BINPATCH.client -cne '/data/client.lzma' -or
        $profile.data.MCP_VERSION.client -cne "'$minecraftVersion'") {
        throw 'Unexpected exact NeoForge 20.4.231 client profile'
    }
    if ($null -ne $profile.data.PSObject.Properties['PATCHED_SHA']) {
        throw 'Installer unexpectedly publishes a PATCHED_SHA digest; review provenance claim'
    }
    foreach ($stage in $stages) {
        $descriptor = $profile.processors[$stage.Index]
        if ($descriptor.jar -cne $stage.Jar -or ($descriptor.args -join ' ') -cne $stage.Args) {
            throw "Unexpected exact installer processor at index $($stage.Index)"
        }
    }
    if ((@($profile.processors[6].sides) -join ',') -cne 'client') {
        throw 'Unexpected client jarsplitter processor side'
    }
    $patchEntry = $zip.GetEntry('data/client.lzma')
    if ($null -eq $patchEntry -or $patchEntry.Length -ne 3014788) {
        throw 'Unexpected embedded NeoForge 20.4.231 client patch'
    }

    $metadata = [Collections.Generic.Dictionary[string,string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($library in @($profile.libraries) + @($version.libraries)) {
        $relative = Get-MavenRelative ([string] $library.name)
        $artifact = $library.downloads.artifact
        if ($null -eq $artifact -or ([string] $artifact.path) -cne $relative -or
            ([string] $artifact.sha1) -cnotmatch '^[0-9a-f]{40}$') {
            throw 'Unexpected installer library path or missing SHA-1'
        }
        $sha1 = [string] $artifact.sha1
        if ($metadata.ContainsKey($relative) -and $metadata[$relative] -cne $sha1) {
            throw 'Installer metadata has conflicting library hashes'
        }
        $metadata[$relative] = $sha1
    }
    $sources = [Collections.Generic.Dictionary[string,string]]::new([StringComparer]::OrdinalIgnoreCase)
    $declaredCoordinates = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($stage in $stages) {
        $descriptor = $profile.processors[$stage.Index]
        foreach ($coordinate in @([string] $descriptor.jar) + @($descriptor.classpath)) {
            [void] $declaredCoordinates.Add([string] $coordinate)
            $relative = Get-MavenRelative ([string] $coordinate)
            if (-not $metadata.ContainsKey($relative)) { throw 'Processor JAR lacks installer hash metadata' }
            if ($sources.ContainsKey($relative)) { continue }
            $source = Assert-File (Join-Path $cachedRoot $relative)
            Assert-Sha1 $source $metadata[$relative] 'Cached processor JAR' | Out-Null
            $sources.Add($relative, $source)
        }
    }
    if ($declaredCoordinates.Count -ne 37 -or $sources.Count -ne 33) {
        throw "Expected 37 declared processor coordinates resolving to 33 JARs; found $($declaredCoordinates.Count)/$($sources.Count)"
    }
    $neoformRelative = "net/neoforged/neoform/$minecraftVersion/neoform-$minecraftVersion.zip"
    if (-not $metadata.ContainsKey($neoformRelative) -or $metadata[$neoformRelative] -cne $neoformSha1) {
        throw 'Exact NeoForm ZIP is absent from installer metadata'
    }
    $neoformSource = Assert-File (Join-Path $cachedRoot $neoformRelative)
    Assert-Sha1 $neoformSource $neoformSha1 'Exact NeoForm ZIP' | Out-Null
    $sources.Add($neoformRelative, $neoformSource)

    if (-not $PreflightOnly) {
        [IO.Directory]::CreateDirectory($output) | Out-Null
        $libraries = Join-Path $output 'libraries'
        $inputCopies = Join-Path $output 'inputs'
        [IO.Directory]::CreateDirectory($inputCopies) | Out-Null
        $clientCopy = Join-Path $inputCopies 'minecraft-client.jar'
        $mojmapsCopy = Join-Path $inputCopies 'mojang-client-mappings.txt'
        Copy-Item -LiteralPath $clientSource -Destination $clientCopy -ErrorAction Stop
        Copy-Item -LiteralPath $mojmapsSource -Destination $mojmapsCopy -ErrorAction Stop
        Assert-Sha1 $clientCopy $vanillaSha1 'Scratch vanilla client' | Out-Null
        Assert-Sha1 $mojmapsCopy $mojmapsSha1 'Scratch Mojang mappings' | Out-Null
        foreach ($relative in $sources.Keys) {
            $target = [IO.Path]::GetFullPath((Join-Path $libraries $relative))
            if (-not (Test-IsWithinOrSame $target $libraries)) { throw 'Processor library escaped scratch root' }
            [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
            Copy-Item -LiteralPath $sources[$relative] -Destination $target -ErrorAction Stop
            Assert-Sha1 $target $metadata[$relative] 'Scratch processor input' | Out-Null
        }
        $patchFile = Join-Path $inputCopies 'client.lzma'
        [IO.Compression.ZipFileExtensions]::ExtractToFile($patchEntry, $patchFile)
        if (([IO.FileInfo]::new($patchFile)).Length -ne 3014788) { throw 'Scratch client patch size mismatch' }
    }
} finally { $zip.Dispose() }

if ($PreflightOnly) {
    return [pscustomobject]@{
        schema = 'sfm-offline-neoforge-client-preflight/1'
        minecraft = '1.20.4'
        loader = $loaderVersion
        installer_sha256 = $installerSha256
        vanilla_client_sha1 = $vanillaSha1
        mojang_mappings_sha1 = $mojmapsSha1
        neoform_zip_sha1 = $neoformSha1
        java_sha256 = $javaSha256
        processor_declared_coordinate_count = 37
        processor_unique_jar_count = 33
        installer_published_patched_digest_present = $false
        cached_reference_coordinate = 'net.neoforged:neoforge:20.4.231:client'
        cached_reference_sha1 = $cachedPatchedSha1
        cached_reference_sha256 = $cachedPatchedSha256
        cached_reference_size = $cachedPatchedSize
        cached_reference_zip_entries = $cachedZipEntries
        output_created = $false
    }
}

$mappingRoot = Join-Path $libraries "net/neoforged/neoform/$minecraftVersion"
$clientRoot = Join-Path $libraries "net/minecraft/client/$minecraftVersion"
$patched = Join-Path $output $patchedRelative
foreach ($dir in @($mappingRoot, $clientRoot, [IO.Path]::GetDirectoryName($patched))) {
    [IO.Directory]::CreateDirectory($dir) | Out-Null
}
$neoformZip = Join-Path $libraries $neoformRelative
$mappings = Join-Path $mappingRoot "neoform-$minecraftVersion-mappings.txt"
$merged = Join-Path $mappingRoot "neoform-$minecraftVersion-mappings-merged.txt"
$slim = Join-Path $clientRoot "client-$minecraftVersion-slim.jar"
$extra = Join-Path $clientRoot "client-$minecraftVersion-extra.jar"
$srg = Join-Path $clientRoot "client-$minecraftVersion-srg.jar"

Invoke-Processor 'mcp-data' $profile.processors[3] 'net.neoforged.installertools.ConsoleTool' `
    @('--task', 'MCP_DATA', '--input', $neoformZip, '--output', $mappings, '--key', 'mappings')
$mappingsHash = (Get-FileHash -LiteralPath (Assert-File $mappings) -Algorithm SHA1).Hash.ToLowerInvariant()
Invoke-Processor 'merge-mappings' $profile.processors[5] 'net.neoforged.installertools.ConsoleTool' `
    @('--task', 'MERGE_MAPPING', '--left', $mappings, '--right', $mojmapsCopy, '--output', $merged,
        '--classes', '--fields', '--methods', '--reverse-right')
$mergedHash = (Get-FileHash -LiteralPath (Assert-File $merged) -Algorithm SHA1).Hash.ToLowerInvariant()
Invoke-Processor 'jarsplitter' $profile.processors[6] 'net.neoforged.jarsplitter.ConsoleTool' `
    @('--input', $clientCopy, '--slim', $slim, '--extra', $extra, '--srg', $merged)
$slimHash = (Get-FileHash -LiteralPath (Assert-File $slim) -Algorithm SHA1).Hash.ToLowerInvariant()
$extraHash = (Get-FileHash -LiteralPath (Assert-File $extra) -Algorithm SHA1).Hash.ToLowerInvariant()
Invoke-Processor 'auto-rename' $profile.processors[8] 'net.minecraftforge.fart.Main' `
    @('--input', $slim, '--output', $srg, '--names', $merged,
        '--ann-fix', '--ids-fix', '--src-fix', '--record-fix')
$srgHash = (Get-FileHash -LiteralPath (Assert-File $srg) -Algorithm SHA1).Hash.ToLowerInvariant()
Invoke-Processor 'binarypatcher' $profile.processors[9] 'net.neoforged.binarypatcher.ConsoleTool' `
    @('--clean', $srg, '--output', $patched, '--apply', $patchFile)
$patchedHash = (Get-FileHash -LiteralPath (Assert-File $patched) -Algorithm SHA1).Hash.ToLowerInvariant()
$patchedHash256 = (Get-FileHash -LiteralPath $patched -Algorithm SHA256).Hash.ToLowerInvariant()
$zipEntries = Assert-ReadableZip $patched 1000
Assert-EqualBytes $patched $cachedPatchedSource
if ($zipEntries -ne $cachedZipEntries) { throw 'Scratch and cached patched client ZIP entry counts differ' }

$receipt = [ordered]@{
    schema = 'sfm-offline-neoforge-client/1'
    minecraft = '1.20.4'
    loader = $loaderVersion
    installer_sha256 = $installerSha256
    vanilla_client_sha1 = $vanillaSha1
    mojang_mappings_sha1 = $mojmapsSha1
    neoform_zip_sha1 = $neoformSha1
    java_sha256 = $javaSha256
    processor_declared_coordinate_count = 37
    processor_unique_jar_count = 33
    neoform_mappings_sha1 = $mappingsHash
    merged_mappings_sha1 = $mergedHash
    slim_client_sha1 = $slimHash
    extra_client_sha1 = $extraHash
    srg_client_sha1 = $srgHash
    patched_client_sha1 = $patchedHash
    patched_client_sha256 = $patchedHash256
    patched_zip_entries = $zipEntries
    installer_published_patched_digest_present = $false
    cached_reference_coordinate = 'net.neoforged:neoforge:20.4.231:client'
    cached_reference_sha1 = $cachedPatchedSha1
    cached_reference_sha256 = $cachedPatchedSha256
    cached_reference_size = $cachedPatchedSize
    cached_reference_equal = $true
    digest_provenance = 'installer_coordinate_with_local_cache_hash_and_scratch_comparison;no_installer_published_digest'
    repeated_against_reference = $false
}
if ($ReferenceRoot) {
    $reference = [IO.Path]::GetFullPath($ReferenceRoot).TrimEnd('\', '/')
    if ($reference.Equals($output, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'ReferenceRoot must differ from OutputRoot'
    }
    $referenceReceipt = Get-Content -LiteralPath (Assert-File (Join-Path $reference 'reconstruction.json')) -Raw |
        ConvertFrom-Json
    foreach ($field in @('schema', 'minecraft', 'loader', 'installer_sha256', 'vanilla_client_sha1',
            'mojang_mappings_sha1', 'neoform_zip_sha1', 'java_sha256',
            'processor_declared_coordinate_count', 'processor_unique_jar_count',
            'neoform_mappings_sha1', 'merged_mappings_sha1', 'slim_client_sha1', 'extra_client_sha1',
            'srg_client_sha1', 'patched_client_sha1', 'patched_client_sha256', 'patched_zip_entries',
            'installer_published_patched_digest_present', 'cached_reference_coordinate',
            'cached_reference_sha1', 'cached_reference_sha256', 'cached_reference_size',
            'cached_reference_equal', 'digest_provenance')) {
        if ([string] $referenceReceipt.$field -cne [string] $receipt[$field]) {
            throw "Independent reconstruction differed at $field"
        }
    }
    $referencePatched = Assert-File (Join-Path $reference $patchedRelative)
    Assert-Sha1 $referencePatched $patchedHash 'Reference patched client' | Out-Null
    Assert-Sha256 $referencePatched $patchedHash256 'Reference patched client' | Out-Null
    Assert-EqualBytes $patched $referencePatched
    if ((Assert-ReadableZip $referencePatched 1000) -ne $zipEntries) {
        throw 'Reference patched client ZIP topology differed'
    }
    $receipt.repeated_against_reference = $true
}
$receipt | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $output 'reconstruction.json') -Encoding utf8
return [pscustomobject] $receipt
