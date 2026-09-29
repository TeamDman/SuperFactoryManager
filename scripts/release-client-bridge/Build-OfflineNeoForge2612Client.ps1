<#
Test-only reconstruction of the exact NeoForge 26.1.2.72 patched client.
The launcher cache and installer stay read-only; all writes use a new output root.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $Installer,
    [Parameter(Mandatory)] [string] $CachedLibraries,
    [Parameter(Mandatory)] [string] $JavaHome,
    [Parameter(Mandatory)] [string] $OutputRoot,
    [ValidateRange(30, 600)] [int] $WatchdogSeconds = 300
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Assert-File([string] $Path) {
    if (-not [IO.File]::Exists($Path)) { throw "Missing exact offline input: $Path" }
    return [IO.Path]::GetFullPath($Path)
}

function Get-ZipText([IO.Compression.ZipArchive] $Archive, [string] $EntryName) {
    $entry = $Archive.GetEntry($EntryName)
    if ($null -eq $entry) { throw "Installer is missing $EntryName" }
    $reader = [IO.StreamReader]::new($entry.Open())
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}

function Quote-Arg([string] $Value) {
    if ($Value.Contains('"')) { throw "Unquotable processor argument: $Value" }
    return '"' + $Value.Replace('\', '/') + '"'
}

$installerFile = Assert-File $Installer
$cachedRoot = [IO.Path]::GetFullPath($CachedLibraries).TrimEnd('\', '/')
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
        throw 'OutputRoot must not be inside an offline input directory'
    }
}
if ((Get-FileHash -LiteralPath $installerFile -Algorithm SHA256).Hash.ToLowerInvariant() -ne
    '249799b185eb7c9fadbe91f533f1f25f6a59c2d7d545430f587c434e5e55902b') {
    throw 'Exact NeoForge 26.1.2.72 installer SHA-256 mismatch'
}
if ((Get-FileHash -LiteralPath $java -Algorithm SHA256).Hash.ToLowerInvariant() -ne
    '60c42e14617d3e23877afea74a651109a3bebaebd5963028c8d26e0d6509dd65') {
    throw 'Exact JBR 25.0.3 Java executable SHA-256 mismatch'
}
$vanilla = Assert-File (Join-Path $cachedRoot 'com/mojang/minecraft/26.1.2/minecraft-26.1.2-client.jar')
if ((Get-FileHash -LiteralPath $vanilla -Algorithm SHA1).Hash.ToLowerInvariant() -ne
    '4e618f09a0c649dde3fdf829df443ce0b8831e65') {
    throw 'Exact vanilla 26.1.2 client SHA-1 mismatch'
}
$tool = Assert-File (Join-Path $cachedRoot 'net/neoforged/installertools/installertools/4.0.12/installertools-4.0.12-fatjar.jar')
$cachedPatched = Assert-File (Join-Path $cachedRoot 'net/neoforged/minecraft-client-patched/26.1.2.72/minecraft-client-patched-26.1.2.72.jar')
$zip = [IO.Compression.ZipFile]::OpenRead($installerFile)
try {
    $profile = Get-ZipText $zip 'install_profile.json' | ConvertFrom-Json
    $version = Get-ZipText $zip 'version.json' | ConvertFrom-Json
    $processor = @($profile.processors | Where-Object { $_.args[1] -eq 'PROCESS_MINECRAFT_JAR' })
    $expectedArgs = @('--task', 'PROCESS_MINECRAFT_JAR', '--no-mod-manifest', '--input',
        '{MINECRAFT_JAR}', '--output', '{PATCHED}', '--extract-libraries-to',
        '{ROOT}/libraries/', '--apply-patches', '{BINPATCH}')
    if ($profile.version -ne 'neoforge-26.1.2.72' -or $version.id -ne $profile.version -or
        $version.mainClass -ne 'net.neoforged.fml.startup.Client' -or $processor.Count -ne 1 -or
        $processor[0].jar -ne 'net.neoforged.installertools:installertools:4.0.12:fatjar' -or
        @($processor[0].classpath).Count -ne 1 -or
        $processor[0].classpath[0] -ne $processor[0].jar -or
        (@($processor[0].args) -join "`n") -ne ($expectedArgs -join "`n") -or
        $profile.data.PATCHED.client -ne '[net.neoforged:minecraft-client-patched:26.1.2.72]' -or
        $profile.data.BINPATCH.client -ne '/data/client.lzma') {
        throw 'Unexpected exact 26.1.2.72 installer client processor'
    }
    $toolLibrary = @($profile.libraries | Where-Object name -eq $processor[0].jar)
    if ($toolLibrary.Count -ne 1 -or
        (Get-FileHash -LiteralPath $tool -Algorithm SHA1).Hash.ToLowerInvariant() -ne
        [string] $toolLibrary[0].downloads.artifact.sha1) {
        throw 'Exact installertools 4.0.12 SHA-1 mismatch'
    }
    if ((Get-FileHash -LiteralPath $cachedPatched -Algorithm SHA1).Hash.ToLowerInvariant() -ne
        'b4054c9102e61029f9ad9c3238831b41444702e9') {
        throw 'Cached 26.1.2.72 patched client SHA-1 mismatch'
    }
    $patchEntry = $zip.GetEntry('data/client.lzma')
    if ($null -eq $patchEntry) { throw 'Installer is missing data/client.lzma' }
    [IO.Directory]::CreateDirectory($output) | Out-Null
    $libraries = Join-Path $output 'libraries'
    $patched = Join-Path $libraries 'net/neoforged/minecraft-client-patched/26.1.2.72/minecraft-client-patched-26.1.2.72.jar'
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($patched)) | Out-Null
    $patchFile = Join-Path $output 'client.lzma'
    $inputStream = $patchEntry.Open()
    $outputStream = [IO.File]::Create($patchFile)
    try { $inputStream.CopyTo($outputStream) } finally { $outputStream.Dispose(); $inputStream.Dispose() }
} finally { $zip.Dispose() }

$args = @('-Xmx2g', '-cp', $tool, 'net.neoforged.installertools.ConsoleTool',
    '--task', 'PROCESS_MINECRAFT_JAR', '--no-mod-manifest', '--input', $vanilla,
    '--output', $patched, '--extract-libraries-to', ($libraries + '/'),
    '--apply-patches', $patchFile)
$argFile = Join-Path $output 'processor.args'
[IO.File]::WriteAllLines($argFile, @($args | ForEach-Object { Quote-Arg $_ }), [Text.UTF8Encoding]::new($false))
$stdout = Join-Path $output 'processor.stdout.log'
$stderr = Join-Path $output 'processor.stderr.log'
$process = $null
try {
    $process = Start-Process -FilePath $java -ArgumentList ('"@' + $argFile + '"') -WorkingDirectory $output `
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
        throw 'Offline client patch processor watchdog expired; stopped owned process'
    }
    $process.Refresh()
    if ($process.ExitCode -ne 0) { throw "Offline client patch processor exited $($process.ExitCode); inspect retained logs" }
    $patched = Assert-File $patched
    $rebuiltSha1 = (Get-FileHash -LiteralPath $patched -Algorithm SHA1).Hash.ToLowerInvariant()
    $rebuiltSha256 = (Get-FileHash -LiteralPath $patched -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($rebuiltSha1 -ne 'b4054c9102e61029f9ad9c3238831b41444702e9' -or
        $rebuiltSha256 -ne 'd50216d41adaab125d0d1741fd4b696e954fcc0a3cb144fc6411cc57e1b87938') {
        throw 'Reconstructed 26.1.2.72 patched client does not match cached packaged client'
    }
    $receipt = [ordered]@{
        schema = 'sfm-offline-neoforge-client/1'
        minecraft = '26.1.2'
        loader = 'neoforge-26.1.2.72'
        installer_sha256 = '249799b185eb7c9fadbe91f533f1f25f6a59c2d7d545430f587c434e5e55902b'
        vanilla_client_sha1 = '4e618f09a0c649dde3fdf829df443ce0b8831e65'
        installertools_sha1 = '94b6e47c3f51f8ca40f8c54720369a952b821e95'
        patched_client_sha1 = $rebuiltSha1
        patched_client_sha256 = $rebuiltSha256
    }
    $receipt | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $output 'reconstruction.json') -Encoding utf8
    Write-Host "SFM_OFFLINE_NEOFORGE_2612_CLIENT_PASS patched_sha256=$rebuiltSha256"
} finally {
    if ($null -ne $process) {
        $process.Refresh()
        if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force }
    }
}
