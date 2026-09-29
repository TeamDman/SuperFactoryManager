<#
Test-only, offline Forge client witness for an unchanged SFM production JAR.
All writes are confined to a new RunRoot; the launcher cache is read-only.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $SfmJar,
    [Parameter(Mandatory)] [string] $ExpectedSha256,
    [Parameter(Mandatory)] [string] $PrismRoot,
    [Parameter(Mandatory)] [string] $JavaHome,
    [Parameter(Mandatory)] [string] $RunRoot,
    [ValidateSet('1.19.2', '1.19.4', '1.20', '1.20.1')] [string] $MinecraftVersion = '1.19.2',
    [string] $NeoForgeInstaller,
    [string] $NeoForgeLibraryRoot,
    [ValidateSet('title', 'world')] [string] $CaptureMode = 'title',
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

function Resolve-CachedPath([string] $Relative, [string[]] $Roots) {
    foreach ($root in $Roots) {
        $path = Join-Path $root ($Relative -replace '/', [IO.Path]::DirectorySeparatorChar)
        if ([IO.File]::Exists($path)) { return [IO.Path]::GetFullPath($path) }
    }
    throw "Missing cached input: $Relative"
}

function Get-LibraryPath($Library, [string[]] $Roots) {
    $parts = [string] $Library.name -split ':'
    if ($parts.Count -lt 3) { throw "Invalid cached Maven coordinate: $($Library.name)" }
    $artifact = $Library.downloads.artifact
    $relative = if ($null -ne $artifact.PSObject.Properties['path']) { [string] $artifact.path } else { '' }
    if (-not $relative) {
        $relative = ($parts[0] -replace '\.', '/') + '/' + $parts[1] + '/' + $parts[2] + '/' +
            $parts[1] + '-' + $parts[2] + '.jar'
    }
    return Resolve-CachedPath $relative $Roots
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
$version = switch ($MinecraftVersion) {
    '1.19.2' { @{ Forge = '43.4.0'; Mcp = '20220805.130853'; Assets = '1.19'; DataFixer = '5.0.28'; EventBus = '6.0.3'; ForgeGroup = 'net/minecraftforge' } }
    '1.19.4' { @{ Forge = '45.0.9'; Mcp = '20230314.122934'; Assets = '3'; DataFixer = '6.0.6'; EventBus = '6.0.3'; ForgeGroup = 'net/minecraftforge' } }
    '1.20' { @{ Forge = '46.0.10'; Mcp = '20230608.053357'; Assets = '5'; DataFixer = '6.0.8'; EventBus = '6.0.3'; ForgeGroup = 'net/minecraftforge' } }
    '1.20.1' { @{ Forge = '47.1.65'; Mcp = '20230612.114412'; Assets = '5'; DataFixer = '6.0.8'; EventBus = '6.0.5'; ForgeGroup = 'net/neoforged'; Fml = '47.1.47'; ClientSha1 = '97e1bb8346f4aa0e9e1bbb04e4fd170264bee508'; InstallerSha256 = 'c0056d398ccc685db87f98939ecd22d54e4a556fcb943cee00df56ed2015b6d9' } }
}
$forgeArtifact = "$MinecraftVersion-$($version.Forge)"
$forgeRelative = "$($version.ForgeGroup)/forge/$forgeArtifact"
$mcpArtifact = "$MinecraftVersion-$($version.Mcp)"
$forgeVersionId = "$MinecraftVersion-forge-$($version.Forge)"
$bridgeSourceRoot = if ($MinecraftVersion -ne '1.19.2') {
    Join-Path $bridgeRoot 'src/1.19.4'
} else {
    Join-Path $bridgeRoot 'src/main'
}
$bridgeResourceRoot = if ($MinecraftVersion -in @('1.20', '1.20.1')) {
    Join-Path $bridgeRoot "src/$MinecraftVersion/resources"
} else {
    Join-Path $bridgeSourceRoot 'resources'
}
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
$minecraftMeta = Get-Content -LiteralPath (Assert-File (Join-Path $prism "meta/net.minecraft/$MinecraftVersion.json")) -Raw | ConvertFrom-Json
$lwjglMeta = Get-Content -LiteralPath (Assert-File (Join-Path $prism 'meta/org.lwjgl3/3.3.1.json')) -Raw | ConvertFrom-Json
$forgeLibraryRoot = $libraryRoot
if ($MinecraftVersion -eq '1.20.1') {
    if (-not $NeoForgeInstaller -or -not $NeoForgeLibraryRoot) {
        throw '1.20.1 requires -NeoForgeInstaller and -NeoForgeLibraryRoot (an isolated, complete libraries directory)'
    }
    $forgeLibraryRoot = [IO.Path]::GetFullPath($NeoForgeLibraryRoot).TrimEnd('\', '/')
    if (-not [IO.Directory]::Exists($forgeLibraryRoot)) { throw "Missing isolated NeoForge libraries: $forgeLibraryRoot" }
    if ($run.Equals($forgeLibraryRoot, [StringComparison]::OrdinalIgnoreCase) -or
        $run.StartsWith($forgeLibraryRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'RunRoot must not be within the isolated NeoForge libraries directory'
    }
    $forgeInstaller = Assert-File $NeoForgeInstaller
    if ((Get-FileHash -LiteralPath $forgeInstaller -Algorithm SHA256).Hash.ToLowerInvariant() -ne $version.InstallerSha256) {
        throw 'Exact NeoForge installer SHA-256 mismatch'
    }
    $forgeProfile = Get-ZipText $forgeInstaller 'install_profile.json' | ConvertFrom-Json
    $declaredClientSha1 = ([string] $forgeProfile.data.PATCHED_SHA.client).Trim("'")
    if ($declaredClientSha1 -ne $version.ClientSha1) { throw 'Unexpected installer-declared patched client SHA-1' }
} else {
    $forgeInstaller = Assert-File (Join-Path $libraryRoot "$forgeRelative/forge-$forgeArtifact-installer.jar")
}
$forgeMeta = Get-ZipText $forgeInstaller 'version.json' | ConvertFrom-Json
if ($forgeMeta.id -ne $forgeVersionId -or $minecraftMeta.mainJar.name -ne "com.mojang:minecraft:${MinecraftVersion}:client") {
    throw 'Unexpected cached Minecraft or Forge version manifest'
}
Assert-File (Join-Path $assetsRoot "indexes/$($version.Assets).json") | Out-Null
$libraryRoots = if ($MinecraftVersion -eq '1.20.1') { @($forgeLibraryRoot, $libraryRoot) } else { @($libraryRoot) }

$classpath = [Collections.Generic.List[string]]::new()
$seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($lib in @($forgeMeta.libraries) + @($minecraftMeta.libraries) + @($lwjglMeta.libraries)) {
    if (-not (Test-SelectedLibrary $lib)) { continue }
    $path = Get-LibraryPath $lib $libraryRoots
    if ($seen.Add($path)) { $classpath.Add($path) }
}
# These artifacts are produced by the cached Forge installer and are not in version.json.
$extra = if ($MinecraftVersion -eq '1.20.1') {
    @("$forgeRelative/forge-$forgeArtifact-client.jar", "$forgeRelative/forge-$forgeArtifact-universal.jar")
} else {
    @(
        "$forgeRelative/forge-$forgeArtifact-client.jar",
        "$forgeRelative/forge-$forgeArtifact-universal.jar",
        "net/minecraftforge/fmlcore/$forgeArtifact/fmlcore-$forgeArtifact.jar",
        "net/minecraftforge/javafmllanguage/$forgeArtifact/javafmllanguage-$forgeArtifact.jar",
        "net/minecraftforge/lowcodelanguage/$forgeArtifact/lowcodelanguage-$forgeArtifact.jar",
        "net/minecraftforge/mclanguage/$forgeArtifact/mclanguage-$forgeArtifact.jar"
    )
}
foreach ($relative in $extra) {
    $path = Resolve-CachedPath $relative @($forgeLibraryRoot)
    if ($seen.Add($path)) { $classpath.Add($path) }
}
if ($MinecraftVersion -eq '1.20.1') {
    $patchedClient = Resolve-CachedPath $extra[0] @($forgeLibraryRoot)
    if ((Get-FileHash -LiteralPath $patchedClient -Algorithm SHA1).Hash.ToLowerInvariant() -ne $version.ClientSha1) {
        throw 'Exact NeoForge patched client SHA-1 mismatch'
    }
}

$templateArgs = @($forgeMeta.arguments.jvm)
$moduleArgIndex = [Array]::IndexOf($templateArgs, '-p') + 1
if ($moduleArgIndex -le 0 -or $moduleArgIndex -ge $templateArgs.Count) { throw 'Missing pinned Forge module path' }
$modulePath = ([string] $templateArgs[$moduleArgIndex]).Replace('${library_directory}', $forgeLibraryRoot).Replace('${classpath_separator}', ';')
foreach ($module in $modulePath.Split(';')) { Assert-File $module | Out-Null }
if ($MinecraftVersion -eq '1.20.1') {
    throw '1.20.1 direct bootstrap is blocked: the patched client omits Overlay, while a separate SRG client duplicates Minecraft module packages. An exact launcher-provided merged module is required.'
}

# No run directory is created until every offline input and version boundary is checked.
[IO.Directory]::CreateDirectory($run) | Out-Null
$game = Join-Path $run 'game'
$mods = Join-Path $game 'mods'
$control = Join-Path $run 'control'
$classes = Join-Path $run 'bridge-classes'
$natives = Join-Path $run 'natives'
foreach ($dir in @($game, $mods, $control, $classes, $natives)) { [IO.Directory]::CreateDirectory($dir) | Out-Null }
if ($MinecraftVersion -in @('1.20', '1.20.1')) {
    # A fresh 1.20 game directory otherwise opens the accessibility onboarding
    # screen before the title screen. These options belong only to this run.
    @('onboardAccessibility:false', 'narrator:0', 'pauseOnLostFocus:false', 'tutorialStep:none') |
        Set-Content -LiteralPath (Join-Path $game 'options.txt') -Encoding ascii
}
$sfmCopy = Join-Path $mods 'sfm.jar'
Copy-Item -LiteralPath $sourceJar -Destination $sfmCopy
if ((Get-FileHash -LiteralPath $sfmCopy -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) {
    throw 'Scratch SFM JAR hash does not match source'
}
$runId = [Guid]::NewGuid().ToString()
@("run_id=$runId", "sfm_sha256=$expected", "capture=$CaptureMode") |
    Set-Content -LiteralPath (Join-Path $control 'request.properties') -Encoding ascii

$fmlCompile = if ($MinecraftVersion -eq '1.20.1') {
    @(
        "net/neoforged/fancymodloader/core/$($version.Fml)/core-$($version.Fml).jar",
        "net/neoforged/fancymodloader/language-java/$($version.Fml)/language-java-$($version.Fml).jar"
    )
} else {
    @(
        "net/minecraftforge/fmlcore/$forgeArtifact/fmlcore-$forgeArtifact.jar",
        "net/minecraftforge/javafmllanguage/$forgeArtifact/javafmllanguage-$forgeArtifact.jar"
    )
}
$compileRelative = @(
    "net/minecraft/client/$mcpArtifact/client-$mcpArtifact-srg.jar",
    "$forgeRelative/forge-$forgeArtifact-client.jar",
    "$forgeRelative/forge-$forgeArtifact-universal.jar"
) + $fmlCompile + @(
    "net/minecraftforge/eventbus/$($version.EventBus)/eventbus-$($version.EventBus).jar",
    "com/mojang/datafixerupper/$($version.DataFixer)/datafixerupper-$($version.DataFixer).jar"
)
$compileClasspath = @($compileRelative | ForEach-Object { Resolve-CachedPath $_ $libraryRoots })
$javaSource = Assert-File (Join-Path $bridgeSourceRoot 'java/ca/teamdman/sfm/releaseprobe/ReleaseClientBridge.java')
& $javac -proc:none -source 17 -target 17 -classpath ($compileClasspath -join ';') -d $classes $javaSource
if ($LASTEXITCODE -ne 0) { throw "Bridge javac failed: $LASTEXITCODE" }
Assert-File (Join-Path $classes 'ca/teamdman/sfm/releaseprobe/ReleaseClientBridge.class') | Out-Null
$bridgeJar = Join-Path $mods 'sfmreleaseprobe.jar'
& $jarTool --create --file $bridgeJar -C $classes . -C $bridgeResourceRoot .
if ($LASTEXITCODE -ne 0) { throw "Bridge jar failed: $LASTEXITCODE" }

# Extract only the pinned Windows x64 LWJGL DLLs into this run's scratch native dir.
foreach ($lib in $lwjglMeta.libraries) {
    if ([string] $lib.name -notmatch '-natives-windows:') { continue }
    $nativeJar = Get-LibraryPath $lib $libraryRoots
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
    $jvm.Add($arg.Replace('${library_directory}', $forgeLibraryRoot).Replace('${classpath_separator}', ';').Replace('${version_name}', $forgeVersionId))
}
$jvm.Add("-DlegacyClassPath=$($classpath -join ';')")
$jvm.Add('-cp')
$jvm.Add($classpath -join ';')
$jvm.Add('cpw.mods.bootstraplauncher.BootstrapLauncher')
$jvm.Add('--username'); $jvm.Add('ReleaseProbe')
$jvm.Add('--version'); $jvm.Add("$forgeVersionId-release-probe")
$jvm.Add('--gameDir'); $jvm.Add($game)
$jvm.Add('--assetsDir'); $jvm.Add($assetsRoot)
$jvm.Add('--assetIndex'); $jvm.Add($version.Assets)
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
    $diskLogs = @($stdout, $stderr, (Join-Path $game 'logs/latest.log'))
    $diskPattern = 'No space left|There is not enough space|not enough space|ENOSPC|disk full|disk space|insufficient storage'
    while (-not [IO.File]::Exists($resultPath) -and -not $process.HasExited -and [DateTime]::UtcNow -lt $deadline) {
        Start-Sleep -Seconds 2
        $process.Refresh()
        foreach ($log in $diskLogs) {
            if ([IO.File]::Exists($log) -and (Select-String -LiteralPath $log -Pattern $diskPattern -Quiet)) {
                if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force }
                throw "Disk-space diagnostic in $log; stopped owned client with no cleanup or retry"
            }
        }
    }
    foreach ($log in $diskLogs) {
        if ([IO.File]::Exists($log) -and (Select-String -LiteralPath $log -Pattern $diskPattern -Quiet)) {
            if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force }
            throw "Disk-space diagnostic in $log; stopped owned client with no cleanup or retry"
        }
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
    $expectedSchema = if ($CaptureMode -eq 'world') { 'sfm-release-client-world-proof/1' } else { 'sfm-release-client-proof/1' }
    $expectedScreen = if ($CaptureMode -eq 'world') { 'world' } else { 'title' }
    $expectedScreenshot = if ($CaptureMode -eq 'world') { 'sfm-release-client-world.png' } else { 'sfm-release-client-title.png' }
    if ($result.schema -ne $expectedSchema -or $result.run_id -ne $runId -or
        $result.sfm_sha256 -ne $expected -or $result.status -ne 'passed' -or
        $result.screen -ne $expectedScreen -or $result.screenshot -ne $expectedScreenshot) {
        throw "Bridge reported failure or unexpected identity: $($result | ConvertTo-Json -Compress)"
    }
    if ($CaptureMode -eq 'world' -and
        ($result.block_id -ne 'sfm:manager' -or $result.server_block_placed -ne $true -or
            $result.client_block_synced -ne $true -or $result.ray_hit -ne $true -or
            $result.rendered_world_frames -lt 30 -or $result.world_id -ne ('sfm_release_probe_' + $runId.Replace('-', '')))) {
        throw "Bridge world witness is incomplete: $($result | ConvertTo-Json -Compress)"
    }
    if ($CaptureMode -eq 'title' -and $result.rendered_title_frames -lt 90) {
        throw "Bridge title witness is incomplete: $($result | ConvertTo-Json -Compress)"
    }
    $screenshot = Assert-File (Join-Path $game "screenshots/$expectedScreenshot")
    $signature = [IO.File]::ReadAllBytes($screenshot)
    if ($signature.Length -lt 24 -or [BitConverter]::ToString($signature, 0, 8) -ne '89-50-4E-47-0D-0A-1A-0A') {
        throw 'Screenshot witness is not a nonempty PNG'
    }
    # PowerShell otherwise performs shifts at the byte width and silently
    # truncates the 24/16/8-bit terms to zero.
    $width = (([int] $signature[16] -shl 24) -bor ([int] $signature[17] -shl 16) -bor
        ([int] $signature[18] -shl 8) -bor [int] $signature[19])
    $height = (([int] $signature[20] -shl 24) -bor ([int] $signature[21] -shl 16) -bor
        ([int] $signature[22] -shl 8) -bor [int] $signature[23])
    if ($width -ne 1024 -or $height -ne 768) {
        throw "Screenshot witness has unexpected PNG dimensions: ${width}x${height}"
    }
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
