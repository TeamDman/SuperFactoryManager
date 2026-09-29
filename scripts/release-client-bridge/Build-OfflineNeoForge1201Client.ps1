<#
Test-only reconstruction of the exact 1.20.1 NeoForge patched client from
locally cached installer processor inputs. All writes stay in a new OutputRoot.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $Installer,
    [Parameter(Mandatory)] [string] $CachedLibraries,
    [Parameter(Mandatory)] [string] $MinecraftSrg,
    [Parameter(Mandatory)] [string] $MinecraftExtra,
    [Parameter(Mandatory)] [string] $JavaHome,
    [Parameter(Mandatory)] [string] $OutputRoot,
    [ValidateRange(30, 300)] [int] $WatchdogSeconds = 120
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Assert-File([string] $Path) {
    if (-not [IO.File]::Exists($Path)) { throw "Missing offline input: $Path" }
    return [IO.Path]::GetFullPath($Path)
}

function Get-ZipText([IO.Compression.ZipArchive] $Archive, [string] $EntryName) {
    $entry = $Archive.GetEntry($EntryName)
    if ($null -eq $entry) { throw "Installer is missing $EntryName" }
    $reader = [IO.StreamReader]::new($entry.Open())
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}

function Get-LibraryRelative($Library) {
    $artifact = $Library.downloads.artifact
    if ($null -ne $artifact.PSObject.Properties['path'] -and $artifact.path) { return [string] $artifact.path }
    $parts = ([string] $Library.name).Split(':')
    if ($parts.Count -lt 3) { throw "Invalid installer library: $($Library.name)" }
    return ($parts[0] -replace '\.', '/') + '/' + $parts[1] + '/' + $parts[2] + '/' +
        $parts[1] + '-' + $parts[2] + '.jar'
}

$installerFile = Assert-File $Installer
$srgFile = Assert-File $MinecraftSrg
$extraFile = Assert-File $MinecraftExtra
$java = Assert-File (Join-Path $JavaHome 'bin/java.exe')
$cachedRoot = [IO.Path]::GetFullPath($CachedLibraries).TrimEnd('\', '/')
$output = [IO.Path]::GetFullPath($OutputRoot).TrimEnd('\', '/')
if (-not [IO.Directory]::Exists($cachedRoot)) { throw "Missing cached library root: $cachedRoot" }
if ([IO.Directory]::Exists($output) -or [IO.File]::Exists($output)) {
    throw "OutputRoot already exists; refusing to reuse: $output"
}
foreach ($inputRoot in @($cachedRoot, [IO.Path]::GetDirectoryName($srgFile),
        [IO.Path]::GetDirectoryName($extraFile), [IO.Path]::GetDirectoryName($installerFile))) {
    if ($output.Equals($inputRoot, [StringComparison]::OrdinalIgnoreCase) -or
        $output.StartsWith($inputRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'OutputRoot must not be inside any offline input directory'
    }
}
if ((Get-FileHash -LiteralPath $installerFile -Algorithm SHA256).Hash.ToLowerInvariant() -ne
    'c0056d398ccc685db87f98939ecd22d54e4a556fcb943cee00df56ed2015b6d9') {
    throw 'Exact NeoForge installer SHA-256 mismatch'
}
if ((Get-FileHash -LiteralPath $srgFile -Algorithm SHA1).Hash.ToLowerInvariant() -ne
    '3c8aa19b710a3a68f721210eb69b74594d13e218') {
    throw 'Cached 1.20.1 client SRG SHA-1 mismatch'
}
if ((Get-FileHash -LiteralPath $extraFile -Algorithm SHA1).Hash.ToLowerInvariant() -ne
    '8c5a95cbce940cfdb304376ae9fea47968d02587') {
    throw 'Cached 1.20.1 client extra SHA-1 mismatch'
}

$zip = [IO.Compression.ZipFile]::OpenRead($installerFile)
try {
    $profile = Get-ZipText $zip 'install_profile.json' | ConvertFrom-Json
    $version = Get-ZipText $zip 'version.json' | ConvertFrom-Json
    if ($version.id -ne '1.20.1-forge-47.1.65' -or
        ([string] $profile.data.PATCHED_SHA.client).Trim("'") -ne '97e1bb8346f4aa0e9e1bbb04e4fd170264bee508') {
        throw 'Unexpected exact 1.20.1 installer profile'
    }
    $patchEntry = $zip.GetEntry('data/client.lzma')
    if ($null -eq $patchEntry -or $patchEntry.Length -ne 2746757) { throw 'Unexpected embedded client patch' }

    $libraries = [Collections.Generic.Dictionary[string,string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($library in @($profile.libraries) + @($version.libraries)) {
        $relative = Get-LibraryRelative $library
        $source = Assert-File (Join-Path $cachedRoot $relative)
        $libraries[$relative] = $source
    }
    # FML resolves both processor outputs from libraryDirectory and unions
    # them with the patched client as one Minecraft module. Do not put either
    # output on the legacy classpath as a separate module.
    $mcVersion = '1.20.1-20230612.114412'
    $libraries["net/minecraft/client/$mcVersion/client-$mcVersion-srg.jar"] = $srgFile
    $libraries["net/minecraft/client/$mcVersion/client-$mcVersion-extra.jar"] = $extraFile
    $processor = @($profile.processors | Where-Object { $_.jar -eq 'net.minecraftforge:binarypatcher:1.1.1' })
    if ($processor.Count -ne 1 -or ($processor[0].args -join ' ') -ne
        '--clean {MC_SRG} --output {PATCHED} --apply {BINPATCH}') {
        throw 'Unexpected installer binarypatcher processor'
    }
    $processorPaths = @(@($processor[0].jar) + @($processor[0].classpath) | ForEach-Object {
        $parts = ([string] $_).Split(':')
        Assert-File (Join-Path $cachedRoot (($parts[0] -replace '\.', '/') + '/' + $parts[1] + '/' +
            $parts[2] + '/' + $parts[1] + '-' + $parts[2] + '.jar'))
    })
    foreach ($relative in $libraries.Keys) {
        $target = [IO.Path]::GetFullPath((Join-Path $output "libraries/$relative"))
        if (-not $target.StartsWith($output + [IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase)) { throw "Library escaped OutputRoot: $relative" }
    }

    [IO.Directory]::CreateDirectory($output) | Out-Null
    foreach ($relative in $libraries.Keys) {
        $target = Join-Path $output "libraries/$relative"
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
        Copy-Item -LiteralPath $libraries[$relative] -Destination $target -ErrorAction Stop
    }
    $patchFile = Join-Path $output 'client.lzma'
    [IO.Compression.ZipFileExtensions]::ExtractToFile($patchEntry, $patchFile)
} finally { $zip.Dispose() }

$clientRelative = 'net/neoforged/forge/1.20.1-47.1.65/forge-1.20.1-47.1.65-client.jar'
$clientJar = Join-Path $output "libraries/$clientRelative"
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($clientJar)) | Out-Null
$stdout = Join-Path $output 'binarypatcher.stdout.log'
$stderr = Join-Path $output 'binarypatcher.stderr.log'
$arguments = @('-cp', ('"' + ($processorPaths -join ';') + '"'),
    'net.minecraftforge.binarypatcher.ConsoleTool', '--clean', ('"' + $srgFile + '"'),
    '--output', ('"' + $clientJar + '"'), '--apply', ('"' + $patchFile + '"'))
$process = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $output -WindowStyle Hidden `
    -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
$deadline = [DateTime]::UtcNow.AddSeconds($WatchdogSeconds)
$diskPattern = 'No space left|There is not enough space|not enough space|ENOSPC|disk full|disk space|insufficient storage'
while (-not $process.HasExited -and [DateTime]::UtcNow -lt $deadline) {
    Start-Sleep -Seconds 1
    $process.Refresh()
    foreach ($log in @($stdout, $stderr)) {
        if ([IO.File]::Exists($log) -and (Select-String -LiteralPath $log -Pattern $diskPattern -Quiet)) {
            if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force }
            throw "Disk-space diagnostic in $log; stopped owned patcher with no cleanup or retry"
        }
    }
}
if (-not $process.HasExited) {
    Stop-Process -Id $process.Id -Force
    throw 'Offline binarypatcher watchdog expired; stopped owned process'
}
$process.Refresh()
if ($process.ExitCode -ne 0) { throw "Offline binarypatcher exited $($process.ExitCode); inspect retained logs" }
Assert-File $clientJar | Out-Null
$actualSha1 = (Get-FileHash -LiteralPath $clientJar -Algorithm SHA1).Hash.ToLowerInvariant()
if ($actualSha1 -ne '97e1bb8346f4aa0e9e1bbb04e4fd170264bee508') {
    throw "Patched client SHA-1 mismatch: $actualSha1"
}
Write-Host "SFM_EXACT_NEOFORGE_CLIENT_READY sha1=$actualSha1 libraries=$($libraries.Count) root=$output"
