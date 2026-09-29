<#
Test-only, offline Forge 43.4.0 client witness for an unchanged 1.19.2 SFM JAR.
All writes are confined to a new RunRoot; the launcher cache is read-only.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $SfmJar,
    [Parameter(Mandatory)] [string] $ExpectedSha256,
    [Parameter(Mandatory)] [string] $PrismRoot,
    [Parameter(Mandatory)] [string] $JavaHome,
    [Parameter(Mandatory)] [string] $RunRoot,
    [ValidateRange(30, 600)] [int] $WatchdogSeconds = 300
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Assert-File([string] $Path) {
    if (-not [IO.File]::Exists($Path)) { throw "Missing cached input: $Path" }
    return [IO.Path]::GetFullPath($Path)
}

function Get-ZipText([string] $Archive, [string] $EntryName) {
    $zip = [IO.Compression.ZipFile]::OpenRead($Archive)
    try {
        $entry = $zip.GetEntry($EntryName)
        if ($null -eq $entry) { throw "Missing $EntryName in $Archive" }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally { $zip.Dispose() }
}

function Get-LibraryPath($Library, [string] $LibraryRoot) {
    $parts = [string] $Library.name -split ':'
    if ($parts.Count -lt 3) { throw "Invalid cached Maven coordinate: $($Library.name)" }
    $artifact = $Library.downloads.artifact
    $relative = if ($null -ne $artifact.PSObject.Properties['path']) { [string] $artifact.path } else { '' }
    if (-not $relative) {
        $relative = ($parts[0] -replace '\.', '/') + '/' + $parts[1] + '/' + $parts[2] + '/' +
            $parts[1] + '-' + $parts[2] + '.jar'
    }
    return Assert-File (Join-Path $LibraryRoot ($relative -replace '/', [IO.Path]::DirectorySeparatorChar))
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

function Add-JavaArg([Collections.Generic.List[string]] $List, [string] $Value) {
    $normal = $Value.Replace('\', '/')
    if ($normal.Contains('"')) { throw "Unquotable Java argument: $Value" }
    $List.Add('"' + $normal + '"')
}

$sourceJar = Assert-File $SfmJar
$prism = [IO.Path]::GetFullPath($PrismRoot).TrimEnd('\', '/')
$java = Assert-File (Join-Path $JavaHome 'bin/java.exe')
$javac = Assert-File (Join-Path $JavaHome 'bin/javac.exe')
$jarTool = Assert-File (Join-Path $JavaHome 'bin/jar.exe')
$run = [IO.Path]::GetFullPath($RunRoot).TrimEnd('\', '/')
$bridgeRoot = $PSScriptRoot
$sourceRoot = [IO.Path]::GetDirectoryName($sourceJar).TrimEnd('\', '/')
if ($run.Equals($prism, [StringComparison]::OrdinalIgnoreCase) -or
    $run.StartsWith($prism + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
    $run.Equals($sourceRoot, [StringComparison]::OrdinalIgnoreCase) -or
    $run.StartsWith($sourceRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'RunRoot must not be within the launcher cache or SFM source-JAR directory'
}
if ([IO.Directory]::Exists($run) -or [IO.File]::Exists($run)) {
    throw "RunRoot already exists; refusing to reuse: $run"
}
if ($ExpectedSha256 -notmatch '^[0-9a-fA-F]{64}$') { throw 'ExpectedSha256 must be 64 hex digits' }
$expected = $ExpectedSha256.ToLowerInvariant()
$sourceHashBefore = (Get-FileHash -LiteralPath $sourceJar -Algorithm SHA256).Hash.ToLowerInvariant()
if ($sourceHashBefore -ne $expected) { throw 'Original SFM JAR hash does not match expectation' }

$libraryRoot = Join-Path $prism 'libraries'
$assetsRoot = Join-Path $prism 'assets'
$minecraftMeta = Get-Content -LiteralPath (Assert-File (Join-Path $prism 'meta/net.minecraft/1.19.2.json')) -Raw | ConvertFrom-Json
$lwjglMeta = Get-Content -LiteralPath (Assert-File (Join-Path $prism 'meta/org.lwjgl3/3.3.1.json')) -Raw | ConvertFrom-Json
$forgeInstaller = Assert-File (Join-Path $libraryRoot 'net/minecraftforge/forge/1.19.2-43.4.0/forge-1.19.2-43.4.0-installer.jar')
$forgeMeta = Get-ZipText $forgeInstaller 'version.json' | ConvertFrom-Json
if ($forgeMeta.id -ne '1.19.2-forge-43.4.0' -or $minecraftMeta.mainJar.name -ne 'com.mojang:minecraft:1.19.2:client') {
    throw 'Unexpected cached Minecraft or Forge version manifest'
}
Assert-File (Join-Path $assetsRoot 'indexes/1.19.json') | Out-Null

$classpath = [Collections.Generic.List[string]]::new()
$seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($lib in @($forgeMeta.libraries) + @($minecraftMeta.libraries) + @($lwjglMeta.libraries)) {
    if (-not (Test-SelectedLibrary $lib)) { continue }
    $path = Get-LibraryPath $lib $libraryRoot
    if ($seen.Add($path)) { $classpath.Add($path) }
}
# These artifacts are produced by the cached Forge installer and are not in version.json.
$extra = @(
    'net/minecraftforge/forge/1.19.2-43.4.0/forge-1.19.2-43.4.0-client.jar',
    'net/minecraftforge/forge/1.19.2-43.4.0/forge-1.19.2-43.4.0-universal.jar',
    'net/minecraftforge/fmlcore/1.19.2-43.4.0/fmlcore-1.19.2-43.4.0.jar',
    'net/minecraftforge/javafmllanguage/1.19.2-43.4.0/javafmllanguage-1.19.2-43.4.0.jar',
    'net/minecraftforge/lowcodelanguage/1.19.2-43.4.0/lowcodelanguage-1.19.2-43.4.0.jar',
    'net/minecraftforge/mclanguage/1.19.2-43.4.0/mclanguage-1.19.2-43.4.0.jar'
)
foreach ($relative in $extra) {
    $path = Assert-File (Join-Path $libraryRoot $relative)
    if ($seen.Add($path)) { $classpath.Add($path) }
}

$templateArgs = @($forgeMeta.arguments.jvm)
$moduleArgIndex = [Array]::IndexOf($templateArgs, '-p') + 1
if ($moduleArgIndex -le 0 -or $moduleArgIndex -ge $templateArgs.Count) { throw 'Missing pinned Forge module path' }
$modulePath = ([string] $templateArgs[$moduleArgIndex]).Replace('${library_directory}', $libraryRoot).Replace('${classpath_separator}', ';')
foreach ($module in $modulePath.Split(';')) { Assert-File $module | Out-Null }

# No run directory is created until every offline input and version boundary is checked.
[IO.Directory]::CreateDirectory($run) | Out-Null
$game = Join-Path $run 'game'
$mods = Join-Path $game 'mods'
$control = Join-Path $run 'control'
$classes = Join-Path $run 'bridge-classes'
$natives = Join-Path $run 'natives'
foreach ($dir in @($game, $mods, $control, $classes, $natives)) { [IO.Directory]::CreateDirectory($dir) | Out-Null }
$sfmCopy = Join-Path $mods 'sfm.jar'
Copy-Item -LiteralPath $sourceJar -Destination $sfmCopy
if ((Get-FileHash -LiteralPath $sfmCopy -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) {
    throw 'Scratch SFM JAR hash does not match source'
}
$runId = [Guid]::NewGuid().ToString()
@("run_id=$runId", "sfm_sha256=$expected", 'capture=title') |
    Set-Content -LiteralPath (Join-Path $control 'request.properties') -Encoding ascii

$compileClasspath = @(
    (Join-Path $libraryRoot 'net/minecraft/client/1.19.2-20220805.130853/client-1.19.2-20220805.130853-srg.jar'),
    (Join-Path $libraryRoot 'net/minecraftforge/forge/1.19.2-43.4.0/forge-1.19.2-43.4.0-client.jar'),
    (Join-Path $libraryRoot 'net/minecraftforge/forge/1.19.2-43.4.0/forge-1.19.2-43.4.0-universal.jar'),
    (Join-Path $libraryRoot 'net/minecraftforge/fmlcore/1.19.2-43.4.0/fmlcore-1.19.2-43.4.0.jar'),
    (Join-Path $libraryRoot 'net/minecraftforge/javafmllanguage/1.19.2-43.4.0/javafmllanguage-1.19.2-43.4.0.jar'),
    (Join-Path $libraryRoot 'net/minecraftforge/eventbus/6.0.3/eventbus-6.0.3.jar')
)
foreach ($path in $compileClasspath) { Assert-File $path | Out-Null }
$javaSource = Assert-File (Join-Path $bridgeRoot 'src/main/java/ca/teamdman/sfm/releaseprobe/ReleaseClientBridge.java')
& $javac -proc:none -source 17 -target 17 -classpath ($compileClasspath -join ';') -d $classes $javaSource
if ($LASTEXITCODE -ne 0) { throw "Bridge javac failed: $LASTEXITCODE" }
$bridgeJar = Join-Path $mods 'sfmreleaseprobe.jar'
& $jarTool --create --file $bridgeJar -C $classes . -C (Join-Path $bridgeRoot 'src/main/resources') .
if ($LASTEXITCODE -ne 0) { throw "Bridge jar failed: $LASTEXITCODE" }

# Extract only the pinned Windows x64 LWJGL DLLs into this run's scratch native dir.
foreach ($lib in $lwjglMeta.libraries) {
    if ([string] $lib.name -notmatch '-natives-windows:') { continue }
    $nativeJar = Get-LibraryPath $lib $libraryRoot
    $zip = [IO.Compression.ZipFile]::OpenRead($nativeJar)
    try {
        foreach ($entry in $zip.Entries) {
            if (-not $entry.Name.EndsWith('.dll', [StringComparison]::OrdinalIgnoreCase)) { continue }
            $destination = Join-Path $natives $entry.Name
            if ([IO.File]::Exists($destination)) { throw "Duplicate native DLL: $($entry.Name)" }
            $inputStream = $entry.Open()
            $outputStream = [IO.File]::Create($destination)
            try { $inputStream.CopyTo($outputStream) } finally { $outputStream.Dispose(); $inputStream.Dispose() }
        }
    } finally { $zip.Dispose() }
}

$jvm = [Collections.Generic.List[string]]::new()
foreach ($arg in @('-Xms512m', '-Xmx2g', '-Dfile.encoding=UTF-8', "-Dsfm.releaseProbe.controlDirectory=$control",
        "-Dorg.lwjgl.librarypath=$natives", "-Djava.library.path=$natives")) { $jvm.Add($arg) }
for ($index = 0; $index -lt $templateArgs.Count; $index++) {
    $arg = [string] $templateArgs[$index]
    if ($arg -eq '-p') { $jvm.Add('-p'); $jvm.Add($modulePath); $index++; continue }
    $jvm.Add($arg.Replace('${library_directory}', $libraryRoot).Replace('${classpath_separator}', ';').Replace('${version_name}', '1.19.2-forge-43.4.0'))
}
$jvm.Add("-DlegacyClassPath=$($classpath -join ';')")
$jvm.Add('-cp')
$jvm.Add($classpath -join ';')
$jvm.Add('cpw.mods.bootstraplauncher.BootstrapLauncher')
$jvm.Add('--username'); $jvm.Add('ReleaseProbe')
$jvm.Add('--version'); $jvm.Add('1.19.2-forge-43.4.0-release-probe')
$jvm.Add('--gameDir'); $jvm.Add($game)
$jvm.Add('--assetsDir'); $jvm.Add($assetsRoot)
$jvm.Add('--assetIndex'); $jvm.Add('1.19')
$jvm.Add('--uuid'); $jvm.Add($runId.Replace('-', ''))
$jvm.Add('--accessToken'); $jvm.Add('0')
$jvm.Add('--userType'); $jvm.Add('legacy')
$jvm.Add('--versionType'); $jvm.Add('release')
$jvm.Add('--width'); $jvm.Add('1024')
$jvm.Add('--height'); $jvm.Add('768')
foreach ($arg in $forgeMeta.arguments.game) { $jvm.Add([string] $arg) }

$argLines = [Collections.Generic.List[string]]::new()
foreach ($arg in $jvm) { Add-JavaArg $argLines $arg }
$argFile = Join-Path $run 'launch.args'
[IO.File]::WriteAllLines($argFile, $argLines, [Text.UTF8Encoding]::new($false))
$stdout = Join-Path $run 'java.stdout.log'
$stderr = Join-Path $run 'java.stderr.log'
$process = $null
try {
    Write-Host "SFM_RELEASE_PROBE_LAUNCH run_id=$runId sfm_sha256=$expected run_root=$run"
    $process = Start-Process -FilePath $java -ArgumentList ('"@' + $argFile + '"') -WorkingDirectory $game `
        -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
    $deadline = [DateTime]::UtcNow.AddSeconds($WatchdogSeconds)
    $resultPath = Join-Path $control 'result.json'
    while (-not [IO.File]::Exists($resultPath) -and -not $process.HasExited -and [DateTime]::UtcNow -lt $deadline) {
        Start-Sleep -Seconds 2
        $process.Refresh()
    }
    if ([IO.File]::Exists($resultPath)) {
        $process.WaitForExit(15000) | Out-Null
        $process.Refresh()
        if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force }
    } elseif (-not $process.HasExited) {
        Stop-Process -Id $process.Id -Force
        $stage = if (Select-String -LiteralPath $stdout -Pattern 'SFM_RELEASE_CLIENT_BRIDGE_READY' -Quiet) {
            'render-witness timeout after SFM and bridge mod load'
        } else { 'bootstrap/client-init timeout before bridge ready' }
        throw "$stage after $WatchdogSeconds seconds; only owned process $($process.Id) was stopped"
    }
    if (-not [IO.File]::Exists($resultPath)) {
        $stage = if (Select-String -LiteralPath $stdout -Pattern 'SFM_RELEASE_CLIENT_BRIDGE_READY' -Quiet) {
            'client exited after bridge ready without render witness'
        } else { 'bootstrap/client-init failed before bridge ready' }
        throw "$stage; exit_code=$($process.ExitCode)"
    }
    $result = Get-Content -LiteralPath $resultPath -Raw | ConvertFrom-Json
    if ($result.run_id -ne $runId -or $result.sfm_sha256 -ne $expected -or $result.status -ne 'passed') {
        throw "Bridge reported failure or unexpected identity: $($result | ConvertTo-Json -Compress)"
    }
    $screenshot = Assert-File (Join-Path $game 'screenshots/sfm-release-client-title.png')
    $signature = [IO.File]::ReadAllBytes($screenshot)
    if ($signature.Length -lt 24 -or [BitConverter]::ToString($signature, 0, 8) -ne '89-50-4E-47-0D-0A-1A-0A') {
        throw 'Screenshot witness is not a nonempty PNG'
    }
    $width = ($signature[16] -shl 24) -bor ($signature[17] -shl 16) -bor ($signature[18] -shl 8) -bor $signature[19]
    $height = ($signature[20] -shl 24) -bor ($signature[21] -shl 16) -bor ($signature[22] -shl 8) -bor $signature[23]
    if ($width -lt 1 -or $height -lt 1) { throw 'Screenshot witness has invalid PNG dimensions' }
    $sourceHashAfter = (Get-FileHash -LiteralPath $sourceJar -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($sourceHashAfter -ne $expected) { throw 'Original SFM JAR changed during client proof' }
    Write-Host "SFM_RELEASE_PROBE_PASS run_id=$runId sfm_sha256=$expected screenshot=$screenshot"
} finally {
    if ($null -ne $process) {
        $process.Refresh()
        if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force }
    }
    if ([IO.File]::Exists($sourceJar)) {
        $after = (Get-FileHash -LiteralPath $sourceJar -Algorithm SHA256).Hash.ToLowerInvariant()
        Write-Host "SFM_RELEASE_PROBE_SOURCE_HASH_AFTER sha256=$after"
    }
}
