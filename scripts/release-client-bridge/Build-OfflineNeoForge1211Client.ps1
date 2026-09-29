<#
Test-only, offline reconstruction of the exact 1.21.1 NeoForge client inputs.
The launcher cache and installer are read-only; all outputs use a fresh root.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $Installer,
    [Parameter(Mandatory)] [string] $CachedLibraries,
    [Parameter(Mandatory)] [string] $JavaHome,
    [Parameter(Mandatory)] [string] $OutputRoot,
    [switch] $PreflightOnly,
    [ValidateRange(30, 600)] [int] $WatchdogSeconds = 300
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Assert-File([string] $Path) {
    if (-not [IO.File]::Exists($Path)) { throw "Missing exact offline input: $Path" }
    return [IO.Path]::GetFullPath($Path)
}

function Assert-Sha1([string] $Path, [string] $Expected, [string] $Label) {
    $actual = (Get-FileHash -LiteralPath (Assert-File $Path) -Algorithm SHA1).Hash.ToLowerInvariant()
    if ($actual -ne $Expected.ToLowerInvariant()) { throw "$Label SHA-1 mismatch: $actual" }
    return $actual
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
    if ($parts.Count -lt 3 -or $parts.Count -gt 4 -or $extension -notmatch '^[a-z]+$') {
        throw "Unexpected processor coordinate: $Coordinate"
    }
    $classifier = if ($parts.Count -eq 4) { '-' + $parts[3] } else { '' }
    return ($parts[0] -replace '\.', '/') + '/' + $parts[1] + '/' + $parts[2] + '/' +
        $parts[1] + '-' + $parts[2] + $classifier + '.' + $extension
}

function Quote-Arg([string] $Value) {
    if ($Value.Contains('"')) { throw "Unquotable processor argument: $Value" }
    return '"' + $Value.Replace('\', '/') + '"'
}

function Test-SelectedLibrary($Library) {
    $name = [string] $Library.name
    if ($name -match 'ForgeWrapper|natives-(linux|macos|windows-arm64|windows-x86)') { return $false }
    if ($null -eq $Library.PSObject.Properties['rules']) { return $true }
    $allowed = $false
    foreach ($rule in $Library.rules) {
        $os = if ($null -ne $rule.PSObject.Properties['os']) { [string] $rule.os.name } else { '' }
        if (-not $os -or $os -eq 'windows') { $allowed = $rule.action -eq 'allow' }
    }
    return $allowed
}

function Invoke-Processor([string] $Name, $Descriptor, [string] $MainClass, [string[]] $Arguments) {
    $relative = @([string] $Descriptor.jar) + @($Descriptor.classpath)
    $classPaths = [Collections.Generic.List[string]]::new()
    $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($coordinate in $relative) {
        $path = Assert-File (Join-Path $libraries (Get-MavenRelative $coordinate))
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
                throw "Disk-space diagnostic in $log; stopped owned processor with no cleanup or retry"
            }
        }
    }
    foreach ($log in @($stdout, $stderr)) {
        if ([IO.File]::Exists($log) -and (Select-String -LiteralPath $log -Pattern $diskPattern -Quiet)) {
            if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force }
            throw "Disk-space diagnostic in $log; stopped owned processor with no cleanup or retry"
        }
    }
    if (-not $process.HasExited) {
        Stop-Process -Id $process.Id -Force
        throw "Offline $Name processor watchdog expired; stopped owned process"
    }
    $process.Refresh()
    if ($process.ExitCode -ne 0) { throw "Offline $Name processor exited $($process.ExitCode); inspect retained logs" }
}

$installerFile = Assert-File $Installer
$cachedRoot = [IO.Path]::GetFullPath($CachedLibraries).TrimEnd('\', '/')
$prismRoot = [IO.Path]::GetDirectoryName($cachedRoot)
$java = Assert-File (Join-Path $JavaHome 'bin/java.exe')
$output = [IO.Path]::GetFullPath($OutputRoot).TrimEnd('\', '/')
if (-not [IO.Directory]::Exists($cachedRoot)) { throw "Missing cached libraries: $cachedRoot" }
if ([IO.Directory]::Exists($output) -or [IO.File]::Exists($output)) {
    throw "OutputRoot already exists; refusing to reuse: $output"
}
foreach ($inputRoot in @($cachedRoot, [IO.Path]::GetDirectoryName($installerFile),
        [IO.Path]::GetFullPath($JavaHome).TrimEnd('\', '/'))) {
    if ($output.Equals($inputRoot, [StringComparison]::OrdinalIgnoreCase) -or
        $output.StartsWith($inputRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'OutputRoot must not be inside any offline input directory'
    }
}
if ((Get-FileHash -LiteralPath $installerFile -Algorithm SHA256).Hash.ToLowerInvariant() -ne
    '479d540cd2d1cea09d8b8cb63266578db9c58ed174546121a43a4bb4084454be') {
    throw 'Exact NeoForge 21.1.206 installer SHA-256 mismatch'
}
$minecraftVersion = '1.21.1-20240808.144430'
$clientSource = Assert-File (Join-Path $cachedRoot 'com/mojang/minecraft/1.21.1/minecraft-1.21.1-client.jar')
$mojangMappings = Assert-File (Join-Path $cachedRoot "net/minecraft/client/$minecraftVersion/client-$minecraftVersion-mappings.txt")
Assert-Sha1 $clientSource '30c73b1c5da787909b2f73340419fdf13b9def88' 'Official vanilla client' | Out-Null
Assert-Sha1 $mojangMappings '2244b6f072256667bcd9a73df124d6c58de77992' 'Cached Mojang client mappings' | Out-Null
$minecraftMeta = Get-Content -LiteralPath (Assert-File (Join-Path $prismRoot 'meta/net.minecraft/1.21.1.json')) -Raw | ConvertFrom-Json
$lwjglMeta = Get-Content -LiteralPath (Assert-File (Join-Path $prismRoot 'meta/org.lwjgl3/3.3.3.json')) -Raw | ConvertFrom-Json
if ($minecraftMeta.mainJar.name -ne 'com.mojang:minecraft:1.21.1:client' -or
    $minecraftMeta.mainJar.downloads.artifact.sha1 -ne '30c73b1c5da787909b2f73340419fdf13b9def88' -or
    $minecraftMeta.assetIndex.id -ne '17' -or $lwjglMeta.version -ne '3.3.3') {
    throw 'Unexpected exact Minecraft 1.21.1 or LWJGL 3.3.3 launcher metadata'
}

$zip = [IO.Compression.ZipFile]::OpenRead($installerFile)
try {
    $profile = Get-ZipText $zip 'install_profile.json' | ConvertFrom-Json
    $version = Get-ZipText $zip 'version.json' | ConvertFrom-Json
    if ($version.id -ne 'neoforge-21.1.206' -or $version.inheritsFrom -ne '1.21.1' -or
        $profile.version -ne 'neoforge-21.1.206' -or @($profile.processors).Count -ne 10) {
        throw 'Unexpected exact NeoForge 1.21.1 installer profile'
    }
    $stages = @(
        @{ Index = 3; Jar = 'net.neoforged.installertools:installertools:2.1.2'; Args = '--task MCP_DATA --input [net.neoforged:neoform:1.21.1-20240808.144430@zip] --output {MAPPINGS} --key mappings' },
        @{ Index = 5; Jar = 'net.neoforged.installertools:installertools:2.1.2'; Args = '--task MERGE_MAPPING --left {MAPPINGS} --right {MOJMAPS} --output {MERGED_MAPPINGS} --classes --fields --methods --reverse-right' },
        @{ Index = 6; Jar = 'net.neoforged.installertools:jarsplitter:2.1.2'; Args = '--input {MINECRAFT_JAR} --slim {MC_SLIM} --extra {MC_EXTRA} --srg {MERGED_MAPPINGS}' },
        @{ Index = 8; Jar = 'net.neoforged:AutoRenamingTool:2.0.3:all'; Args = '--input {MC_SLIM} --output {MC_SRG} --names {MERGED_MAPPINGS} --ann-fix --ids-fix --src-fix --record-fix' },
        @{ Index = 9; Jar = 'net.neoforged.installertools:binarypatcher:2.1.2:fatjar'; Args = '--clean {MC_SRG} --output {PATCHED} --apply {BINPATCH}' }
    )
    foreach ($stage in $stages) {
        $descriptor = $profile.processors[$stage.Index]
        if ($descriptor.jar -ne $stage.Jar -or ($descriptor.args -join ' ') -ne $stage.Args) {
            throw "Unexpected exact installer processor at index $($stage.Index)"
        }
    }
    if ((@($profile.processors[6].sides) -join ',') -ne 'client') {
        throw 'Unexpected client jarsplitter processor side'
    }
    $patchEntry = $zip.GetEntry('data/client.lzma')
    if ($null -eq $patchEntry -or $patchEntry.Length -ne 3234222) { throw 'Unexpected embedded client patch' }

    $sources = [Collections.Generic.Dictionary[string,string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($library in @($profile.libraries) + @($version.libraries)) {
        $relative = [string] $library.downloads.artifact.path
        if (-not $relative -or $relative -ne (Get-MavenRelative ([string] $library.name))) {
            throw "Unexpected installer library coordinate: $($library.name)"
        }
        $source = Assert-File (Join-Path $cachedRoot $relative)
        $sha1 = [string] $library.downloads.artifact.sha1
        if ($sha1 -notmatch '^[0-9a-fA-F]{40}$') { throw "Unpinned installer library: $relative" }
        Assert-Sha1 $source $sha1 $relative | Out-Null
        $sources[$relative] = $source
    }
    # The compiler and launcher must not open Prism ZIP files through Java ZipFS.
    # Copy only the Windows-selected vanilla/LWJGL jars after checking metadata SHA-1.
    foreach ($library in @($minecraftMeta.libraries) + @($lwjglMeta.libraries)) {
        if (-not (Test-SelectedLibrary $library)) { continue }
        $relative = Get-MavenRelative ([string] $library.name)
        $declaredPath = if ($null -ne $library.downloads.artifact.PSObject.Properties['path']) {
            [string] $library.downloads.artifact.path
        } else { '' }
        if ($declaredPath -and $declaredPath -ne $relative) {
            throw "Unexpected launcher library coordinate: $($library.name)"
        }
        $source = Assert-File (Join-Path $cachedRoot $relative)
        $sha1 = [string] $library.downloads.artifact.sha1
        if ($sha1 -notmatch '^[0-9a-fA-F]{40}$') { throw "Unpinned launcher library: $relative" }
        Assert-Sha1 $source $sha1 $relative | Out-Null
        $sources[$relative] = $source
    }
    $neoformRelative = "net/neoforged/neoform/$minecraftVersion/neoform-$minecraftVersion.zip"
    if (-not $sources.ContainsKey($neoformRelative)) { throw 'NeoForm mapping input is absent from exact installer' }
    if ((Get-FileHash -LiteralPath $sources[$neoformRelative] -Algorithm SHA1).Hash.ToLowerInvariant() -ne
        '811e2bd86fa2cda2812e5e8e51d718ea8bd6d3f4') { throw 'Exact NeoForm ZIP SHA-1 mismatch' }
    foreach ($relative in $sources.Keys) {
        $target = [IO.Path]::GetFullPath((Join-Path $output "libraries/$relative"))
        if (-not $target.StartsWith($output + [IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase)) { throw "Library escaped OutputRoot: $relative" }
    }
    if (-not $PreflightOnly) {
        # No scratch output is created until every exact cached input has been checked.
        [IO.Directory]::CreateDirectory($output) | Out-Null
        $libraries = Join-Path $output 'libraries'
        $inputCopies = Join-Path $output 'inputs'
        [IO.Directory]::CreateDirectory($inputCopies) | Out-Null
        $clientCopy = Join-Path $inputCopies 'minecraft-client.jar'
        $mojangMappingsCopy = Join-Path $inputCopies 'mojang-client-mappings.txt'
        Copy-Item -LiteralPath $clientSource -Destination $clientCopy -ErrorAction Stop
        Copy-Item -LiteralPath $mojangMappings -Destination $mojangMappingsCopy -ErrorAction Stop
        Assert-Sha1 $clientCopy '30c73b1c5da787909b2f73340419fdf13b9def88' 'Scratch vanilla client' | Out-Null
        Assert-Sha1 $mojangMappingsCopy '2244b6f072256667bcd9a73df124d6c58de77992' 'Scratch Mojang mappings' | Out-Null
        foreach ($relative in $sources.Keys) {
            $target = Join-Path $libraries $relative
            [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
            Copy-Item -LiteralPath $sources[$relative] -Destination $target -ErrorAction Stop
        }
        $patchFile = Join-Path $output 'client.lzma'
        [IO.Compression.ZipFileExtensions]::ExtractToFile($patchEntry, $patchFile)
    }
} finally { $zip.Dispose() }
if ($PreflightOnly) {
    Write-Host "SFM_EXACT_NEOFORGE_CLIENT_PREFLIGHT_OK libraries=$($sources.Count) root_absent=$(-not [IO.Directory]::Exists($output))"
    return
}

$neoformZip = Join-Path $libraries "net/neoforged/neoform/$minecraftVersion/neoform-$minecraftVersion.zip"
$mappingRoot = Join-Path $libraries "net/neoforged/neoform/$minecraftVersion"
$clientRoot = Join-Path $libraries "net/minecraft/client/$minecraftVersion"
$patchedRoot = Join-Path $libraries 'net/neoforged/neoforge/21.1.206'
foreach ($dir in @($mappingRoot, $clientRoot, $patchedRoot)) { [IO.Directory]::CreateDirectory($dir) | Out-Null }
$mappings = Join-Path $mappingRoot "neoform-$minecraftVersion-mappings.txt"
$merged = Join-Path $mappingRoot "neoform-$minecraftVersion-mappings-merged.txt"
$slim = Join-Path $clientRoot "client-$minecraftVersion-slim.jar"
$extra = Join-Path $clientRoot "client-$minecraftVersion-extra.jar"
$srg = Join-Path $clientRoot "client-$minecraftVersion-srg.jar"
$patched = Join-Path $patchedRoot 'neoforge-21.1.206-client.jar'

Invoke-Processor 'mcp-data' $profile.processors[3] 'net.neoforged.installertools.ConsoleTool' `
    @('--task', 'MCP_DATA', '--input', $neoformZip, '--output', $mappings, '--key', 'mappings')
Assert-Sha1 $mappings 'c9fe69b8e39fc9ae8a56e6666204505866006cce' 'NeoForm extracted mappings' | Out-Null
Invoke-Processor 'merge-mappings' $profile.processors[5] 'net.neoforged.installertools.ConsoleTool' `
    @('--task', 'MERGE_MAPPING', '--left', $mappings, '--right', $mojangMappingsCopy, '--output', $merged,
        '--classes', '--fields', '--methods', '--reverse-right')
Assert-Sha1 $merged '8184d8b290627ae54ab0eb980801bfe011f70175' 'Merged client mappings' | Out-Null
Invoke-Processor 'jarsplitter' $profile.processors[6] 'net.neoforged.jarsplitter.ConsoleTool' `
    @('--input', $clientCopy, '--slim', $slim, '--extra', $extra, '--srg', $merged)
Assert-Sha1 $slim 'b4fb33003ee0975bc4511ff09d5d4a268b33fc2b' 'Split slim client' | Out-Null
Assert-Sha1 $extra 'db5c59932751d66c2f57c1c2de41b48712620975' 'Split extra client' | Out-Null
Invoke-Processor 'auto-rename' $profile.processors[8] 'net.neoforged.art.Main' `
    @('--input', $slim, '--output', $srg, '--names', $merged,
        '--ann-fix', '--ids-fix', '--src-fix', '--record-fix')
Assert-Sha1 $srg 'a4827225b3c07662ca68b03ea20d11433a0c0488' 'Remapped SRG client' | Out-Null
Invoke-Processor 'binarypatcher' $profile.processors[9] 'net.neoforged.binarypatcher.ConsoleTool' `
    @('--clean', $srg, '--output', $patched, '--apply', $patchFile)
$patchedHash = Assert-Sha1 $patched 'c84e84858e2a57eead05f5ca55a922af8009dce8' 'Patched NeoForge client'
$receipt = [ordered]@{
    schema = 'sfm-offline-neoforge-client/1'
    minecraft = '1.21.1'
    loader = 'neoforge-21.1.206'
    installer_sha256 = '479d540cd2d1cea09d8b8cb63266578db9c58ed174546121a43a4bb4084454be'
    vanilla_client_sha1 = '30c73b1c5da787909b2f73340419fdf13b9def88'
    mojang_mappings_sha1 = '2244b6f072256667bcd9a73df124d6c58de77992'
    neoform_zip_sha1 = '811e2bd86fa2cda2812e5e8e51d718ea8bd6d3f4'
    neoform_mappings_sha1 = 'c9fe69b8e39fc9ae8a56e6666204505866006cce'
    merged_mappings_sha1 = '8184d8b290627ae54ab0eb980801bfe011f70175'
    slim_client_sha1 = 'b4fb33003ee0975bc4511ff09d5d4a268b33fc2b'
    extra_client_sha1 = 'db5c59932751d66c2f57c1c2de41b48712620975'
    srg_client_sha1 = 'a4827225b3c07662ca68b03ea20d11433a0c0488'
    patched_client_sha1 = $patchedHash
}
$receipt | ConvertTo-Json -Depth 2 | Set-Content -LiteralPath (Join-Path $output 'reconstruction.json') -Encoding utf8
Write-Host "SFM_EXACT_NEOFORGE_CLIENT_READY sha1=$patchedHash libraries=$($sources.Count) root=$output"
