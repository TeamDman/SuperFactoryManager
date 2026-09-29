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
    [ValidateSet('1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21.0', '1.21.1', '26.1.2')] [string] $MinecraftVersion = '1.19.2',
    [string] $NeoForgeInstaller,
    [string] $NeoForgeLibraryRoot,
    [string] $NeoForgeRuntimeLibraryRoot,
    [string] $AssetRoot,
    [string] $JoinedCompileJar,
    [switch] $PreflightOnly,
    [ValidateSet('title', 'world', 'network-roundtrip')] [string] $CaptureMode = 'title',
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
        $classifier = if ($parts.Count -ge 4) { '-' + $parts[3] } else { '' }
        $relative = ($parts[0] -replace '\.', '/') + '/' + $parts[1] + '/' + $parts[2] + '/' +
            $parts[1] + '-' + $parts[2] + $classifier + '.jar'
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
            throw 'Scratch input or RunRoot may not traverse a symbolic link or junction'
        }
        $parent = [IO.Path]::GetDirectoryName($current.TrimEnd('\', '/'))
        if (-not $parent -or $parent -eq $current) { break }
        $current = $parent
    }
}

function Get-ZipEntrySha256([string] $Archive, [string] $EntryName) {
    $zip = [IO.Compression.ZipFile]::OpenRead($Archive)
    try {
        $entry = $zip.GetEntry($EntryName)
        if ($null -eq $entry) { throw "Missing $EntryName in exact installer" }
        $stream = $entry.Open()
        $sha = [Security.Cryptography.SHA256]::Create()
        try { return [Convert]::ToHexString($sha.ComputeHash($stream)).ToLowerInvariant() }
        finally { $sha.Dispose(); $stream.Dispose() }
    } finally { $zip.Dispose() }
}

$sourceJar = Assert-File $SfmJar
if ($PreflightOnly -and $MinecraftVersion -notin @('1.20.2', '1.20.3', '1.20.4', '1.21.0')) {
    throw '-PreflightOnly currently applies only to exact 1.20.2–1.20.4 and 1.21.0 client fixtures'
}
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
    '1.20.1' { @{ Forge = '47.1.65'; Mcp = '20230612.114412'; Assets = '5'; DataFixer = '6.0.8'; EventBus = '6.0.5'; ForgeGroup = 'net/neoforged'; Fml = '47.1.47'; ClientSha1 = '97e1bb8346f4aa0e9e1bbb04e4fd170264bee508'; SrgSha1 = '3c8aa19b710a3a68f721210eb69b74594d13e218'; ExtraSha1 = '8c5a95cbce940cfdb304376ae9fea47968d02587'; InstallerSha256 = 'c0056d398ccc685db87f98939ecd22d54e4a556fcb943cee00df56ed2015b6d9' } }
    '1.20.2' { @{ Forge = '20.2.86'; Mcp = '20231019.002635'; Assets = '8'; DataFixer = '6.0.8'; EventBus = '7.2.0'; ForgeGroup = 'net/neoforged'; Fml = '1.0.16'; ClientSha1 = '3dc236e58db25520637344088a06384f419d58d3'; SrgSha1 = '027ab381671c2d44909547ebc81d7f71fd0cd137'; ExtraSha1 = '63aecf90378d1ba8b046078015879645b53e7a41'; InstallerSha256 = 'c21378ea25e4c1b1eb367f7eda7db48af6965e3e35c2270421f10687faf8d4db' } }
    '1.20.3' { @{ Forge = '20.3.8-beta'; Mcp = '20231205.165107'; Assets = '12'; DataFixer = '6.0.8'; EventBus = '7.2.0'; ForgeGroup = 'net/neoforged'; Fml = '1.0.16'; ClientSha1 = 'ac52754a2c75b7beec03f6d2534f5c6d4e8c02b5'; ClientSha256 = '4dd826d54009b27b2fd2ca428ebf4e4a21ef95bb9d45782d432f56fef298fdf6'; SrgSha1 = 'bb8b6fd71d847e51c8de5b8dcec04321d13bd889'; ExtraSha1 = '78d38c4da4332bf70c9a0ab26181114a4ad7b77b'; SlimSha1 = '82101e456406f4cdad1bd261a5854499df1c703a'; NeoformSha1 = '88efc93dca87914f70ef6b3ecc12db46edc64898'; InstallerSha256 = 'd59ba6f0c867ddaafe0247ba7ddf527389897806795b9bd543a2c4313695f9d7'; UniversalSha1 = '1503460abdc783188e88d1cb4b3b77c358f85937'; UniversalSha256 = 'fe1f948efc857d55b59d073a9dc0ed1cd5baa4ce4dac3d2709054834d3258fb8'; JoinedSha256 = '61773c57a22655ca25b9ba6057a89111fb6818cc65313d9295ba8fe863b805dd'; VanillaSha1 = 'b178a327a96f2cf1c9f98a45e5588d654a3e4369'; ProcessorCoordinates = 41; ProcessorJars = 37; ZipEntries = 1440 } }
    '1.20.4' { @{ Forge = '20.4.231'; Mcp = '20231207.154220'; Assets = '12'; DataFixer = '6.0.8'; EventBus = '7.2.0'; ForgeGroup = 'net/neoforged'; Fml = '2.0.17'; ClientSha1 = 'ec7cfa975d3b76c55088b728d4baa330b5ebeddd'; ClientSha256 = 'bf12799c537727ce46e44d55b0f4924a92dab5ff25b8fcf9bcc64aa46865e1fe'; SrgSha1 = '554b0ca84d7d700cc41f86e0fd33d469720aef12'; ExtraSha1 = 'cf034de685064dd33aff580c4facdd16326d6b41'; SlimSha1 = 'a9037ac663eeab89cf08b20f04e6a46b9adf8d90'; NeoformSha1 = 'ad0bdcc5c21199e1a9cb05836f9660c22830a4fe'; InstallerSha256 = '8002077d9454603611b0bb5fd66a107c2dd2a27942ebe52c39db2a9280eaac18'; UniversalSha1 = 'dac2b0193a53a1009b697df47012c992ed0093dc'; UniversalSha256 = 'e816be38b54db409b2307d65e7aaaa21afb6861fb68f99c2be18b158736f9f8f'; JoinedSha256 = 'c062e6791ba0159cc27ba0f3ec8cd33307b73bca22f3f6a6423e76ab02c254de'; VanillaSha1 = 'fd19469fed4a4b4c15b2d5133985f0e3e7816a8a'; ProcessorCoordinates = 37; ProcessorJars = 33; ZipEntries = 1499 } }
    '1.21.0' { @{ Forge = '21.0.143'; Mcp = '20240613.152323'; Assets = '17'; DataFixer = '8.0.16'; EventBus = '8.0.1'; ForgeGroup = 'net/neoforged'; Fml = '4.0.21'; ClientSha1 = 'dbd0d15e8509c8380268210f7d3a39368484c3e0'; SrgSha1 = '42fefdd28234c1e9af6aa637d866a88a4b3b1a3a'; ExtraSha1 = 'd55d73d7a253ecc9848c2a98b9284323c7966a55'; InstallerSha256 = '02e511f97bcfd2985937fc205aa95c93588bf3d9939c6ef18100add57d1e8bec' } }
    '1.21.1' { @{ Forge = '21.1.206'; Mcp = '20240808.144430'; Assets = '17'; DataFixer = '8.0.16'; EventBus = '8.0.5'; ForgeGroup = 'net/neoforged'; Fml = '4.0.41'; ClientSha1 = 'c84e84858e2a57eead05f5ca55a922af8009dce8'; SrgSha1 = 'a4827225b3c07662ca68b03ea20d11433a0c0488'; ExtraSha1 = 'db5c59932751d66c2f57c1c2de41b48712620975'; InstallerSha256 = '479d540cd2d1cea09d8b8cb63266578db9c58ed174546121a43a4bb4084454be' } }
    '26.1.2' { @{ Forge = '26.1.2.72'; Mcp = ''; Assets = '30'; ForgeGroup = 'net/neoforged'; Fml = '11.0.13'; ClientSha1 = 'b4054c9102e61029f9ad9c3238831b41444702e9'; InstallerSha256 = '249799b185eb7c9fadbe91f533f1f25f6a59c2d7d545430f587c434e5e55902b' } }
}
$neo1202 = $MinecraftVersion -eq '1.20.2'
$neoIntermediate = $MinecraftVersion -in @('1.20.3', '1.20.4')
$neo1210 = $MinecraftVersion -eq '1.21.0'
$neoModern = $MinecraftVersion -eq '1.21.1'
$neoLatest = $MinecraftVersion -eq '26.1.2'
$neoNamed = $neo1202 -or $neoIntermediate -or $neo1210 -or $neoModern -or $neoLatest
$minecraftGameVersion = if ($neo1210) { '1.21' } else { $MinecraftVersion }
$forgeArtifact = if ($neoNamed) { $version.Forge } else { "$MinecraftVersion-$($version.Forge)" }
$forgeRelative = if ($neoNamed) { "net/neoforged/neoforge/$forgeArtifact" } else { "$($version.ForgeGroup)/forge/$forgeArtifact" }
$mcpArtifact = "$minecraftGameVersion-$($version.Mcp)"
$forgeVersionId = if ($neoNamed) { "neoforge-$($version.Forge)" } else { "$MinecraftVersion-forge-$($version.Forge)" }
$bridgeSourceRoot = if ($neoLatest) {
    Join-Path $bridgeRoot 'src/26.1.2'
} elseif ($neoModern) {
    Join-Path $bridgeRoot 'src/1.21.1'
} elseif ($neo1210) {
    Join-Path $bridgeRoot 'src/1.21.0'
} elseif ($neo1202) {
    Join-Path $bridgeRoot 'src/1.20.2'
} elseif ($neoIntermediate) {
    Join-Path $bridgeRoot "src/$MinecraftVersion"
} elseif ($MinecraftVersion -ne '1.19.2') {
    Join-Path $bridgeRoot 'src/1.19.4'
} else {
    Join-Path $bridgeRoot 'src/main'
}
$bridgeResourceRoot = if ($MinecraftVersion -in @('1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21.0', '1.21.1', '26.1.2')) {
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
if ($neoModern -and $expected -notin @(
        '4b589e0b310133998f26f0c12c3ee8d367360be1771e0e96c951322cea6c7ad0',
        '544fc4344a9cf3cd73ab97173a41b97ce1c8e16cdbd3eb8c4389a4b2bcbe344c')) {
    throw '1.21.1 source JAR is not an exact official/projected 4.34.0 input'
}
if ($neo1210 -and $expected -notin @(
        'c76399b2456daccd88050ef45d36cac7f5d4bf60535adb2dd0d0ffeab19daa5f',
        'e233d788e54db4078fa90a8324c4132fc3fbfbe103b78ca03003d51d9a0e947b')) {
    throw '1.21.0 source JAR is not an exact official/projected 4.34.0 input'
}
if ($neo1202 -and $expected -notin @(
        'afce7113bc55d12fe92db02f79b95d702e530fb5fd10accba96edcdbcf0186e2',
        '9b5f00d887edd7a72dd35a7e1d2131a17083932793f06649d362053c34f9808d')) {
    throw '1.20.2 source JAR is not an exact official/projected 4.34.0 input'
}
if ($MinecraftVersion -eq '1.20.3' -and $expected -notin @(
        'e4686d536227e0ead220f56606a5527ec8057504cfc77712a1c892d6d14d82b1',
        '3cd66ed27313c067f3529f94765dfc2a51d36b376bbbf841b0f95c8874389d58')) {
    throw '1.20.3 source JAR is not an exact official/projected 4.34.0 input'
}
if ($MinecraftVersion -eq '1.20.4' -and $expected -notin @(
        'b83b43fc8ee7cb7863da5f0d039b026675c2f2c68956f99643c83a9736f7a63b',
        '3964a8742211e9cc39b44ee61b4402387382c948ba7c17b1d887c013f764813e')) {
    throw '1.20.4 source JAR is not an exact official/projected 4.34.0 input'
}
if ($neoLatest -and $expected -notin @(
        'cace8809600cea007dbe5c73dc04c2f780375547ec0717a1ec8c25d991140bf1',
        '9b2ff101e1158f42bf9218177c1f063375ce58254ff0de629db4dc52ef3c15b6')) {
    throw '26.1.2 source JAR is not an exact official/projected 4.34.0 input'
}
$sourceHashBefore = (Get-FileHash -LiteralPath $sourceJar -Algorithm SHA256).Hash.ToLowerInvariant()
if ($sourceHashBefore -ne $expected) { throw 'Original SFM JAR hash does not match expectation' }

$libraryRoot = Join-Path $prism 'libraries'
$assetsRoot = if ($neo1202 -or $neoIntermediate) {
    if (-not $AssetRoot) { throw "Exact $MinecraftVersion requires -AssetRoot with the isolated complete asset stage" }
    [IO.Path]::GetFullPath($AssetRoot).TrimEnd('\', '/')
} else {
    if ($AssetRoot) { throw '-AssetRoot is only supported for exact 1.20.2–1.20.4' }
    Join-Path $prism 'assets'
}
$minecraftMetaPath = Assert-File (Join-Path $prism "meta/net.minecraft/$minecraftGameVersion.json")
$minecraftMeta = Get-Content -LiteralPath $minecraftMetaPath -Raw | ConvertFrom-Json
$lwjglVersion = if ($neoLatest) { '3.4.1' } elseif ($neo1210 -or $neoModern) { '3.3.3' } else { '3.3.1' }
$lwjglMetaPath = Assert-File (Join-Path $prism "meta/org.lwjgl3/$lwjglVersion.json")
$lwjglMeta = Get-Content -LiteralPath $lwjglMetaPath -Raw | ConvertFrom-Json
if ($neo1202 -and
    ((Get-FileHash -LiteralPath $minecraftMetaPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne
        'f5c01fd900ed402add6a6af0cf9e2bd46a6e01d7410c7f9efcc627e3cf5ca4b5' -or
        (Get-FileHash -LiteralPath $lwjglMetaPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne
        '568dd8e999d242304ecd1bdccf1d06e968ff62d9b64d6145e0b97287575e7e08')) {
    throw 'Exact 1.20.2 Minecraft or LWJGL launcher manifest SHA-256 mismatch'
}
if ($neoIntermediate -and
    ((Get-FileHash -LiteralPath $minecraftMetaPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne
        $(if ($MinecraftVersion -eq '1.20.3') { '8a094a450c4a3a6874bb76501f31a7dae469aac010e7000ef84187b7bd87b6f9' } else { 'e603748b0153e2d92ccec92b7e6defc2293fdfd41df4c7eb31a85dee41d122e2' }) -or
        (Get-FileHash -LiteralPath $lwjglMetaPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne
        '568dd8e999d242304ecd1bdccf1d06e968ff62d9b64d6145e0b97287575e7e08')) {
    throw "Exact $MinecraftVersion Minecraft or LWJGL launcher manifest SHA-256 mismatch"
}
if ($neo1210 -and
    ((Get-FileHash -LiteralPath $minecraftMetaPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne
        '1246001f088a048d1009c50009ac8f10b9634289ccd18d6f8e72795d805bf1c3' -or
        (Get-FileHash -LiteralPath $lwjglMetaPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne
        '53d4b4f47f04bac6bc1da9c25033218e631055a8289be028b3557f2a63e1f1dc')) {
    throw 'Exact 1.21.0 Minecraft or LWJGL launcher manifest SHA-256 mismatch'
}
$forgeLibraryRoot = $libraryRoot
if ($neo1202 -or $neoIntermediate) {
    if (-not $NeoForgeRuntimeLibraryRoot) {
        throw "Exact $MinecraftVersion requires -NeoForgeRuntimeLibraryRoot with cached installer-selected libraries"
    }
    $neoRuntimeLibraryRoot = [IO.Path]::GetFullPath($NeoForgeRuntimeLibraryRoot).TrimEnd('\', '/')
    if (-not [IO.Directory]::Exists($neoRuntimeLibraryRoot) -or
        (Test-IsWithinOrSame $run $neoRuntimeLibraryRoot) -or
        (Test-IsWithinOrSame $neoRuntimeLibraryRoot $run)) {
        throw "Exact $MinecraftVersion runtime library root must exist and be separate from RunRoot"
    }
} elseif ($NeoForgeRuntimeLibraryRoot) {
    throw '-NeoForgeRuntimeLibraryRoot is only supported for exact 1.20.2–1.20.4'
}
if ($neoLatest -and ($NeoForgeLibraryRoot -or $JoinedCompileJar)) {
    throw '26.1.2 uses its exact cached library graph; isolated 1.21.1-only inputs are not accepted'
}
if ($MinecraftVersion -in @('1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21.1')) {
    if (-not $NeoForgeInstaller -or -not $NeoForgeLibraryRoot) {
        throw 'NeoForge client requires -NeoForgeInstaller and -NeoForgeLibraryRoot (an isolated, complete libraries directory)'
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
    if ($neo1202) {
        if ($forgeProfile.version -ne $forgeVersionId -or $forgeProfile.data.PATCHED.client -ne
            '[net.neoforged:neoforge:20.2.86:client]' -or @($forgeProfile.processors).Count -ne 10) {
            throw 'Unexpected exact NeoForge 1.20.2 installer client profile'
        }
        $receiptPath = Assert-File (Join-Path ([IO.Path]::GetDirectoryName($forgeLibraryRoot)) 'reconstruction.json')
        $receipt = Get-Content -LiteralPath $receiptPath -Raw | ConvertFrom-Json
        if ($receipt.schema -ne 'sfm-offline-neoforge-client/1' -or $receipt.minecraft -ne '1.20.2' -or
            $receipt.loader -ne $forgeVersionId -or $receipt.installer_sha256 -ne $version.InstallerSha256 -or
            $receipt.vanilla_client_sha1 -ne '82d1974e75fc984c5ed4b038e764e50958ac61a0' -or
            $receipt.mojang_mappings_sha1 -ne '5c292ff7d3161977041116698e295083fd5ec8f5' -or
            $receipt.neoform_zip_sha1 -ne 'ccc76c9cd813988c70b3098c6cfb3bed22395206' -or
            $receipt.java_sha256 -ne '186d651179d34ce21d857597bb88a7b1e244973e64f3a9bec1e9daaffd919e31' -or
            $receipt.processor_declared_coordinate_count -ne 41 -or $receipt.processor_unique_jar_count -ne 37 -or
            $receipt.neoform_mappings_sha1 -ne '861b07bb405cc6a1eda569d8ceca8606a883af25' -or
            $receipt.merged_mappings_sha1 -ne '94a7c2fab6cecacfcd7e2e72c499438886067cc9' -or
            $receipt.slim_client_sha1 -ne 'ee0b0899fafae86bfc71028d97508c86346e6622' -or
            $receipt.extra_client_sha1 -ne $version.ExtraSha1 -or $receipt.srg_client_sha1 -ne $version.SrgSha1 -or
            $receipt.patched_client_sha1 -ne $version.ClientSha1 -or
            $receipt.patched_client_sha256 -ne '0bebc71df20aadba9d7ee56ad5dbd92c87546d7fd0e29397cb637a1f9fd5dc94' -or
            $receipt.digest_provenance -ne 'independent_scratch_repeatability_only') {
            throw 'Exact offline NeoForge 1.20.2 reconstruction receipt mismatch'
        }
        if ((Get-FileHash -LiteralPath $java -Algorithm SHA256).Hash.ToLowerInvariant() -ne
            '186d651179d34ce21d857597bb88a7b1e244973e64f3a9bec1e9daaffd919e31') {
            throw 'Exact JBR 17 Java executable SHA-256 mismatch'
        }
        if (-not $JoinedCompileJar) {
            throw 'Exact 1.20.2 requires the hash-pinned read-only NeoForm joined compile JAR'
        }
        $joined = Assert-File $JoinedCompileJar
        if ((Get-FileHash -LiteralPath $joined -Algorithm SHA256).Hash.ToLowerInvariant() -ne
            '4ef3f10b2a4464687aa5e19e06586a8f33e68f63caf73ee48ae93c5e7c6f24ab') {
            throw 'Exact NeoForm 1.20.2 joined compile JAR SHA-256 mismatch'
        }
        $joinedZip = [IO.Compression.ZipFile]::OpenRead($joined)
        try {
            if ($null -eq $joinedZip.GetEntry('net/minecraft/client/Minecraft.class') -or
                $null -eq $joinedZip.GetEntry('net/minecraft/core/BlockPos.class')) {
                throw 'Exact NeoForm 1.20.2 joined compile JAR is incomplete'
            }
        } finally { $joinedZip.Dispose() }
    } elseif ($neoIntermediate) {
        if ($forgeProfile.version -ne $forgeVersionId -or $forgeProfile.data.PATCHED.client -ne
            "[net.neoforged:neoforge:$forgeArtifact`:client]" -or @($forgeProfile.processors).Count -ne 10 -or
            $null -ne $forgeProfile.data.PSObject.Properties['PATCHED_SHA']) {
            throw "Unexpected exact NeoForge $MinecraftVersion installer client profile"
        }
        $receiptPath = Assert-File (Join-Path ([IO.Path]::GetDirectoryName($forgeLibraryRoot)) 'reconstruction.json')
        $receipt = Get-Content -LiteralPath $receiptPath -Raw | ConvertFrom-Json
        $provenance = if ($MinecraftVersion -eq '1.20.3') {
            'independent_scratch_repeatability_only'
        } else {
            'installer_coordinate_with_local_cache_hash_and_scratch_comparison;no_installer_published_digest'
        }
        if ($receipt.schema -ne 'sfm-offline-neoforge-client/1' -or $receipt.minecraft -ne $MinecraftVersion -or
            $receipt.loader -ne $forgeVersionId -or $receipt.installer_sha256 -ne $version.InstallerSha256 -or
            $receipt.vanilla_client_sha1 -ne $version.VanillaSha1 -or
            $receipt.mojang_mappings_sha1 -ne 'be76ecc174ea25580bdc9bf335481a5192d9f3b7' -or
            $receipt.neoform_zip_sha1 -ne $version.NeoformSha1 -or
            $receipt.java_sha256 -ne '186d651179d34ce21d857597bb88a7b1e244973e64f3a9bec1e9daaffd919e31' -or
            $receipt.processor_declared_coordinate_count -ne $version.ProcessorCoordinates -or
            $receipt.processor_unique_jar_count -ne $version.ProcessorJars -or
            $receipt.neoform_mappings_sha1 -ne 'aa376dbbbed0368ec65c10df1880f4689d46fb50' -or
            $receipt.merged_mappings_sha1 -ne '287250bc0972170c092385871cf92c82faa8095e' -or
            $receipt.slim_client_sha1 -ne $version.SlimSha1 -or
            $receipt.extra_client_sha1 -ne $version.ExtraSha1 -or
            $receipt.srg_client_sha1 -ne $version.SrgSha1 -or
            $receipt.patched_client_sha1 -ne $version.ClientSha1 -or
            $receipt.patched_client_sha256 -ne $version.ClientSha256 -or
            $receipt.patched_zip_entries -ne $version.ZipEntries -or
            $receipt.digest_provenance -ne $provenance -or
            $receipt.repeated_against_reference -ne $true) {
            throw "Exact offline NeoForge $MinecraftVersion reconstruction receipt mismatch"
        }
        if ($MinecraftVersion -eq '1.20.4' -and
            ($receipt.installer_published_patched_digest_present -ne $false -or
                $receipt.cached_reference_coordinate -ne 'net.neoforged:neoforge:20.4.231:client' -or
                $receipt.cached_reference_sha1 -ne $version.ClientSha1 -or
                $receipt.cached_reference_sha256 -ne $version.ClientSha256 -or
                $receipt.cached_reference_size -ne 5288597 -or
                $receipt.cached_reference_equal -ne $true)) {
            throw 'Exact offline NeoForge 1.20.4 cached-reference receipt mismatch'
        }
        foreach ($tool in @(
                @{ Path = $java; Sha256 = '186d651179d34ce21d857597bb88a7b1e244973e64f3a9bec1e9daaffd919e31' },
                @{ Path = $javac; Sha256 = 'e84e764317af846582fb4bd168922ac5bf6fe8448264a05a71507d6dc311624f' },
                @{ Path = $jarTool; Sha256 = '2288a0d0642f58a8fe5858adf9405cf90ec048b135c4efd657933d6db2fa0990' })) {
            if ((Get-FileHash -LiteralPath $tool.Path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $tool.Sha256) {
                throw "Exact $MinecraftVersion JBR 17 tool hash mismatch"
            }
        }
        if (-not $JoinedCompileJar) { throw "Exact $MinecraftVersion requires its pinned joined compile JAR" }
        $joined = Assert-File $JoinedCompileJar
        if ((Get-FileHash -LiteralPath $joined -Algorithm SHA256).Hash.ToLowerInvariant() -ne $version.JoinedSha256) {
            throw "Exact NeoForm $MinecraftVersion joined compile JAR SHA-256 mismatch"
        }
        $joinedZip = [IO.Compression.ZipFile]::OpenRead($joined)
        try {
            if ($null -eq $joinedZip.GetEntry('net/minecraft/client/Minecraft.class') -or
                $null -eq $joinedZip.GetEntry('net/minecraft/core/BlockPos.class')) {
                throw "Exact NeoForm $MinecraftVersion joined compile JAR is incomplete"
            }
        } finally { $joinedZip.Dispose() }
    } elseif ($neoModern) {
        if ($forgeProfile.version -ne $forgeVersionId -or $forgeProfile.data.PATCHED.client -ne
            '[net.neoforged:neoforge:21.1.206:client]') {
            throw 'Unexpected exact NeoForge 1.21.1 installer client profile'
        }
        $receiptPath = Assert-File (Join-Path ([IO.Path]::GetDirectoryName($forgeLibraryRoot)) 'reconstruction.json')
        $receipt = Get-Content -LiteralPath $receiptPath -Raw | ConvertFrom-Json
        if ($receipt.schema -ne 'sfm-offline-neoforge-client/1' -or $receipt.minecraft -ne '1.21.1' -or
            $receipt.loader -ne $forgeVersionId -or $receipt.installer_sha256 -ne $version.InstallerSha256 -or
            $receipt.vanilla_client_sha1 -ne '30c73b1c5da787909b2f73340419fdf13b9def88' -or
            $receipt.mojang_mappings_sha1 -ne '2244b6f072256667bcd9a73df124d6c58de77992' -or
            $receipt.neoform_zip_sha1 -ne '811e2bd86fa2cda2812e5e8e51d718ea8bd6d3f4' -or
            $receipt.neoform_mappings_sha1 -ne 'c9fe69b8e39fc9ae8a56e6666204505866006cce' -or
            $receipt.merged_mappings_sha1 -ne '8184d8b290627ae54ab0eb980801bfe011f70175' -or
            $receipt.slim_client_sha1 -ne 'b4fb33003ee0975bc4511ff09d5d4a268b33fc2b' -or
            $receipt.extra_client_sha1 -ne $version.ExtraSha1 -or $receipt.srg_client_sha1 -ne $version.SrgSha1 -or
            $receipt.patched_client_sha1 -ne $version.ClientSha1) {
            throw 'Exact offline NeoForge 1.21.1 reconstruction receipt mismatch'
        }
        $joined = Assert-File $JoinedCompileJar
        if ((Get-FileHash -LiteralPath $joined -Algorithm SHA256).Hash.ToLowerInvariant() -ne
            'cef91058da78da95ac888fb9056d258ac29f7dd7712d7450029c56d80f31dda6') {
            throw 'Exact joined 1.21.1 compile JAR SHA-256 mismatch'
        }
        if ((Get-FileHash -LiteralPath $java -Algorithm SHA256).Hash.ToLowerInvariant() -ne
            'cd23f1d9b3ba8f99370503e3f13b057c3ca3b0cb32b926f1a39637a34e11ebc1') {
            throw 'Exact JDK 21 Java executable SHA-256 mismatch'
        }
    } else {
        $declaredClientSha1 = ([string] $forgeProfile.data.PATCHED_SHA.client).Trim("'")
        if ($declaredClientSha1 -ne $version.ClientSha1) { throw 'Unexpected installer-declared patched client SHA-1' }
    }
} elseif ($neo1210) {
    if ($NeoForgeLibraryRoot -or $NeoForgeRuntimeLibraryRoot -or $AssetRoot) {
        throw 'Exact 1.21.0 uses its hash-pinned read-only launcher cache; no alternate roots are accepted'
    }
    $forgeInstaller = if ($NeoForgeInstaller) {
        Assert-File $NeoForgeInstaller
    } else {
        Assert-File (Join-Path $libraryRoot "$forgeRelative/neoforge-$forgeArtifact-installer.jar")
    }
    if ((Get-FileHash -LiteralPath $forgeInstaller -Algorithm SHA256).Hash.ToLowerInvariant() -ne
        $version.InstallerSha256) {
        throw 'Exact NeoForge 1.21.0 installer SHA-256 mismatch'
    }
    $forgeProfile = Get-ZipText $forgeInstaller 'install_profile.json' | ConvertFrom-Json
    if ($forgeProfile.version -ne $forgeVersionId -or $forgeProfile.data.PATCHED.client -ne
        '[net.neoforged:neoforge:21.0.143:client]' -or @($forgeProfile.processors).Count -ne 10) {
        throw 'Unexpected exact NeoForge 1.21.0 installer client profile'
    }
    if (-not $JoinedCompileJar) { throw 'Exact 1.21.0 requires its hash-pinned joined compile JAR' }
    $joined = Assert-File $JoinedCompileJar
    if ((Get-FileHash -LiteralPath $joined -Algorithm SHA256).Hash.ToLowerInvariant() -ne
        '10ee2981219a2e0fe82a18abb6044ab29ffdb3cd60c56c281ee0ce3365584d93') {
        throw 'Exact NeoForm 1.21.0 joined compile JAR SHA-256 mismatch'
    }
    if ((Get-FileHash -LiteralPath $java -Algorithm SHA256).Hash.ToLowerInvariant() -ne
        'cd23f1d9b3ba8f99370503e3f13b057c3ca3b0cb32b926f1a39637a34e11ebc1') {
        throw 'Exact JBR 21 Java executable SHA-256 mismatch'
    }
} elseif ($neoLatest) {
    $forgeInstaller = if ($NeoForgeInstaller) {
        Assert-File $NeoForgeInstaller
    } else {
        Assert-File (Join-Path $libraryRoot "$forgeRelative/neoforge-$forgeArtifact-installer.jar")
    }
    if ((Get-FileHash -LiteralPath $forgeInstaller -Algorithm SHA256).Hash.ToLowerInvariant() -ne $version.InstallerSha256) {
        throw 'Exact NeoForge 26.1.2.72 installer SHA-256 mismatch'
    }
    $forgeProfile = Get-ZipText $forgeInstaller 'install_profile.json' | ConvertFrom-Json
    if ($forgeProfile.version -ne $forgeVersionId -or $forgeProfile.data.PATCHED.client -ne
        '[net.neoforged:minecraft-client-patched:26.1.2.72]') {
        throw 'Unexpected exact NeoForge 26.1.2.72 installer client profile'
    }
    if ((Get-FileHash -LiteralPath $java -Algorithm SHA256).Hash.ToLowerInvariant() -ne
        '60c42e14617d3e23877afea74a651109a3bebaebd5963028c8d26e0d6509dd65') {
        throw 'Exact JBR 25.0.3 Java executable SHA-256 mismatch'
    }
} else {
    $forgeInstaller = Assert-File (Join-Path $libraryRoot "$forgeRelative/forge-$forgeArtifact-installer.jar")
}
$forgeMeta = Get-ZipText $forgeInstaller 'version.json' | ConvertFrom-Json
if ($forgeMeta.id -ne $forgeVersionId -or
    ($neoIntermediate -and $forgeMeta.inheritsFrom -ne $MinecraftVersion) -or
    $minecraftMeta.mainJar.name -ne "com.mojang:minecraft:${minecraftGameVersion}:client") {
    throw 'Unexpected cached Minecraft or Forge version manifest'
}
$assetIndex = Assert-File (Join-Path $assetsRoot "indexes/$($version.Assets).json")
if ($neo1202) {
    $assetParent = [IO.Path]::GetDirectoryName($assetsRoot)
    $checkoutRoot = [IO.Path]::GetFullPath((Join-Path $bridgeRoot '../..')).TrimEnd('\', '/')
    $profileRoot = [Environment]::GetFolderPath([Environment+SpecialFolder]::UserProfile)
    if ([IO.Path]::GetFileName($assetsRoot) -cne 'assets' -or
        (Test-IsWithinOrSame $assetsRoot $prism) -or (Test-IsWithinOrSame $assetsRoot $checkoutRoot) -or
        (Test-IsWithinOrSame $assetsRoot $run) -or (Test-IsWithinOrSame $run $assetParent) -or
        (Test-IsWithinOrSame $run $checkoutRoot) -or (Test-IsWithinOrSame $checkoutRoot $run) -or
        (Test-IsWithinOrSame $run $profileRoot) -or (Test-IsWithinOrSame $run $prism) -or
        (Test-IsWithinOrSame $prism $run) -or (Test-IsWithinOrSame $sourceRoot $run) -or
        (Test-IsWithinOrSame $run $forgeLibraryRoot) -or (Test-IsWithinOrSame $forgeLibraryRoot $run)) {
        throw 'Exact 1.20.2 AssetRoot and RunRoot must be separate isolated scratch locations'
    }
    Assert-NoReparseAncestor $assetsRoot
    Assert-NoReparseAncestor $run
    if (([IO.FileInfo]::new($assetIndex)).Length -ne 416851 -or
        (Get-FileHash -LiteralPath $assetIndex -Algorithm SHA1).Hash.ToLowerInvariant() -ne
        '21beaec863755c8fd4620b22ed9bdbc6718b3c32' -or
        [string] $minecraftMeta.assetIndex.sha1 -ne '21beaec863755c8fd4620b22ed9bdbc6718b3c32') {
        throw 'Exact 1.20.2 staged asset index identity mismatch'
    }
    $assetReceipt = Get-Content -LiteralPath (Assert-File (Join-Path $assetParent 'asset-objects.json')) -Raw | ConvertFrom-Json
    if ($assetReceipt.schema -ne 'sfm-release-asset-objects-stage/1' -or $assetReceipt.minecraft -ne '1.20.2' -or
        $assetReceipt.asset_index_sha1 -ne '21beaec863755c8fd4620b22ed9bdbc6718b3c32' -or
        $assetReceipt.required_unique_objects -ne 3607 -or $assetReceipt.required_unique_bytes -ne 646330719L -or
        $assetReceipt.copied_verified_objects -ne 3604 -or $assetReceipt.downloaded_verified_objects -ne 3 -or
        $assetReceipt.prism_cache_access -ne 'read_only' -or $assetReceipt.staged_coverage_complete -ne $true) {
        throw 'Exact 1.20.2 staged asset-object receipt mismatch'
    }
    . (Join-Path $bridgeRoot 'AssetIndexCoverage.ps1')
    $assetCoverage = Get-AssetObjectCoverage $assetIndex (Join-Path $assetsRoot 'objects')
    if ($assetCoverage.required_names -ne 3630 -or $assetCoverage.required_unique_objects -ne 3607 -or
        $assetCoverage.required_unique_bytes -ne 646330719L -or $assetCoverage.cached_verified_objects -ne 3607 -or
        $assetCoverage.cached_verified_bytes -ne 646330719L -or $assetCoverage.cache_missing_objects -ne 0 -or
        $assetCoverage.cache_invalid_objects -ne 0) {
        throw 'Exact 1.20.2 staged asset-object coverage is incomplete'
    }
}
if ($neoIntermediate) {
    $assetParent = [IO.Path]::GetDirectoryName($assetsRoot)
    $checkoutRoot = [IO.Path]::GetFullPath((Join-Path $bridgeRoot '../..')).TrimEnd('\', '/')
    $profileRoot = [Environment]::GetFolderPath([Environment+SpecialFolder]::UserProfile)
    if ([IO.Path]::GetFileName($assetsRoot) -cne 'assets' -or
        (Test-IsWithinOrSame $assetsRoot $prism) -or (Test-IsWithinOrSame $assetsRoot $checkoutRoot) -or
        (Test-IsWithinOrSame $assetsRoot $run) -or (Test-IsWithinOrSame $run $assetParent) -or
        (Test-IsWithinOrSame $run $checkoutRoot) -or (Test-IsWithinOrSame $checkoutRoot $run) -or
        (Test-IsWithinOrSame $run $profileRoot) -or (Test-IsWithinOrSame $run $prism) -or
        (Test-IsWithinOrSame $prism $run) -or (Test-IsWithinOrSame $sourceRoot $run) -or
        (Test-IsWithinOrSame $run $forgeLibraryRoot) -or (Test-IsWithinOrSame $forgeLibraryRoot $run) -or
        (Test-IsWithinOrSame $run $neoRuntimeLibraryRoot) -or
        (Test-IsWithinOrSame $neoRuntimeLibraryRoot $run)) {
        throw "Exact $MinecraftVersion AssetRoot and RunRoot must be separate isolated scratch locations"
    }
    Assert-NoReparseAncestor $assetsRoot
    Assert-NoReparseAncestor $run
    if (([IO.FileInfo]::new($assetIndex)).Length -ne 437785 -or
        (Get-FileHash -LiteralPath $assetIndex -Algorithm SHA1).Hash.ToLowerInvariant() -ne
        '5060d8c8c8f6a52cea32ea9ccd68c9c92e5b74e7' -or
        [string] $minecraftMeta.assetIndex.sha1 -ne '5060d8c8c8f6a52cea32ea9ccd68c9c92e5b74e7') {
        throw "Exact $MinecraftVersion staged asset index identity mismatch"
    }
    $assetReceiptPath = Assert-File (Join-Path $assetParent 'asset-objects.json')
    if ((Get-FileHash -LiteralPath $assetReceiptPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne
        '0d07904470431bbe83024ab228e8a0d35e01160b91bfa37aefeef60167a4ca21') {
        throw 'Exact index-12 asset-stage receipt hash mismatch'
    }
    $assetReceipt = Get-Content -LiteralPath $assetReceiptPath -Raw | ConvertFrom-Json
    if ($assetReceipt.schema -ne 'sfm-release-asset-objects-stage/1' -or
        $assetReceipt.minecraft -ne '1.20.3+1.20.4' -or
        $assetReceipt.asset_index_sha1 -ne '5060d8c8c8f6a52cea32ea9ccd68c9c92e5b74e7' -or
        $assetReceipt.required_unique_objects -ne 3787 -or
        $assetReceipt.required_unique_bytes -ne 650581001L -or
        $assetReceipt.copied_verified_objects -ne 3787 -or
        $assetReceipt.downloaded_verified_objects -ne 0 -or
        $assetReceipt.prism_cache_access -ne 'read_only' -or
        $assetReceipt.staged_coverage_complete -ne $true) {
        throw 'Exact index-12 asset-stage receipt mismatch'
    }
    . (Join-Path $bridgeRoot 'AssetIndexCoverage.ps1')
    $assetCoverage = Get-AssetObjectCoverage $assetIndex (Join-Path $assetsRoot 'objects')
    if ($assetCoverage.required_names -ne 3810 -or
        $assetCoverage.required_unique_objects -ne 3787 -or
        $assetCoverage.required_unique_bytes -ne 650581001L -or
        $assetCoverage.cached_verified_objects -ne 3787 -or
        $assetCoverage.cached_verified_bytes -ne 650581001L -or
        $assetCoverage.cache_missing_objects -ne 0 -or $assetCoverage.cache_invalid_objects -ne 0) {
        throw "Exact $MinecraftVersion staged asset-object coverage is incomplete"
    }
}
if ($neo1210) {
    $checkoutRoot = [IO.Path]::GetFullPath((Join-Path $bridgeRoot '../..')).TrimEnd('\', '/')
    $profileRoot = [Environment]::GetFolderPath([Environment+SpecialFolder]::UserProfile)
    if ((Test-IsWithinOrSame $run $checkoutRoot) -or (Test-IsWithinOrSame $checkoutRoot $run) -or
        (Test-IsWithinOrSame $run $profileRoot) -or (Test-IsWithinOrSame $run $prism) -or
        (Test-IsWithinOrSame $prism $run) -or (Test-IsWithinOrSame $sourceRoot $run)) {
        throw 'Exact 1.21.0 RunRoot must be isolated from the checkout, launcher cache and user profile'
    }
    Assert-NoReparseAncestor $run
    if (([IO.FileInfo]::new($assetIndex)).Length -ne 449456 -or
        (Get-FileHash -LiteralPath $assetIndex -Algorithm SHA1).Hash.ToLowerInvariant() -ne
        '9c535a9da093459a1766fc706674332dc5669423' -or
        [string] $minecraftMeta.assetIndex.sha1 -ne '9c535a9da093459a1766fc706674332dc5669423') {
        throw 'Exact 1.21.0 asset index identity mismatch'
    }
    . (Join-Path $bridgeRoot 'AssetIndexCoverage.ps1')
    $assetCoverage = Get-AssetObjectCoverage $assetIndex (Join-Path $assetsRoot 'objects')
    if ($assetCoverage.required_names -ne 3910 -or $assetCoverage.required_unique_objects -ne 3887 -or
        $assetCoverage.required_unique_bytes -ne 820069451L -or $assetCoverage.cached_verified_objects -ne 3887 -or
        $assetCoverage.cached_verified_bytes -ne 820069451L -or $assetCoverage.cache_missing_objects -ne 0 -or
        $assetCoverage.cache_invalid_objects -ne 0) {
        throw 'Exact 1.21.0 cached asset-object coverage is incomplete'
    }
}
if ($neoLatest -and (Get-FileHash -LiteralPath $assetIndex -Algorithm SHA1).Hash.ToLowerInvariant() -ne
    [string] $minecraftMeta.assetIndex.sha1) {
    throw 'Exact 26.1.2 asset index SHA-1 mismatch'
}
$libraryRoots = if ($neoModern) {
    @($forgeLibraryRoot)
} elseif ($neo1202 -or $neoIntermediate) {
    @($forgeLibraryRoot, $neoRuntimeLibraryRoot, $libraryRoot)
} elseif ($MinecraftVersion -eq '1.20.1') {
    @($forgeLibraryRoot, $libraryRoot)
} else {
    @($libraryRoot)
}

$classpath = [Collections.Generic.List[string]]::new()
$seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$selectedLibraries = @(@($forgeMeta.libraries) + @($minecraftMeta.libraries) + @($lwjglMeta.libraries) |
    Where-Object { Test-SelectedLibrary $_ })
$lastLibraryByArtifact = @{}
if ($neoIntermediate -or $neo1210) {
    # Prism/Minecraft library inheritance replaces an older version of the
    # same Maven artifact. Loading both creates duplicate JPMS modules.
    for ($index = 0; $index -lt $selectedLibraries.Count; $index++) {
        $parts = [string] $selectedLibraries[$index].name -split ':'
        $identity = "$($parts[0]):$($parts[1])"
        if ($parts.Count -ge 4) { $identity += ":$($parts[3])" }
        $lastLibraryByArtifact[$identity] = $index
    }
}
for ($index = 0; $index -lt $selectedLibraries.Count; $index++) {
    $lib = $selectedLibraries[$index]
    $path = Get-LibraryPath $lib $libraryRoots
    if (($neo1202 -or $neoIntermediate -or $neo1210 -or $neoLatest) -and
        (([string] $lib.downloads.artifact.sha1 -notmatch '^[0-9a-f]{40}$') -or
            (Get-FileHash -LiteralPath $path -Algorithm SHA1).Hash.ToLowerInvariant() -ne
            [string] $lib.downloads.artifact.sha1)) {
        throw "Exact launcher library SHA-1 mismatch: $($lib.name)"
    }
    if ($neoIntermediate -or $neo1210) {
        $parts = [string] $lib.name -split ':'
        $identity = "$($parts[0]):$($parts[1])"
        if ($parts.Count -ge 4) { $identity += ":$($parts[3])" }
        if ($index -ne $lastLibraryByArtifact[$identity]) { continue }
    }
    if ($seen.Add($path)) { $classpath.Add($path) }
}
if ($neoLatest) {
    $vanillaClient = Assert-File (Join-Path $libraryRoot 'com/mojang/minecraft/26.1.2/minecraft-26.1.2-client.jar')
    if ((Get-FileHash -LiteralPath $vanillaClient -Algorithm SHA1).Hash.ToLowerInvariant() -ne
        [string] $minecraftMeta.mainJar.downloads.artifact.sha1) {
        throw 'Exact 26.1.2 vanilla client SHA-1 mismatch'
    }
    if ($seen.Add($vanillaClient)) { $classpath.Add($vanillaClient) }
}
# These game-content artifacts are not launcher libraries in version.json.
$extra = if ($neoLatest) {
    @('net/neoforged/minecraft-client-patched/26.1.2.72/minecraft-client-patched-26.1.2.72.jar',
        "$forgeRelative/neoforge-$forgeArtifact-universal.jar")
} elseif ($neoModern) {
    @("$forgeRelative/neoforge-$forgeArtifact-client.jar", "$forgeRelative/neoforge-$forgeArtifact-universal.jar")
} elseif ($neo1210) {
    @("$forgeRelative/neoforge-$forgeArtifact-client.jar", "$forgeRelative/neoforge-$forgeArtifact-universal.jar")
} elseif ($neo1202 -or $neoIntermediate) {
    @("$forgeRelative/neoforge-$forgeArtifact-client.jar", "$forgeRelative/neoforge-$forgeArtifact-universal.jar")
} elseif ($MinecraftVersion -eq '1.20.1') {
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
    if (($neo1202 -or $neoIntermediate) -and $relative -eq $extra[1]) { continue }
    $path = Resolve-CachedPath $relative @($forgeLibraryRoot)
    if ($neo1202 -or $neoIntermediate -or $neo1210 -or $neoModern -or $neoLatest) {
        # FML resolves its patched client and NeoForge content through
        # libraryDirectory; neither belongs on the initial launcher classpath.
        continue
    }
    if ($seen.Add($path)) { $classpath.Add($path) }
}
if ($neoLatest) {
    $patchedClient = Resolve-CachedPath $extra[0] @($libraryRoot)
    $neoUniversal = Resolve-CachedPath $extra[1] @($libraryRoot)
    if ((Get-FileHash -LiteralPath $patchedClient -Algorithm SHA1).Hash.ToLowerInvariant() -ne $version.ClientSha1 -or
        (Get-FileHash -LiteralPath $neoUniversal -Algorithm SHA256).Hash.ToLowerInvariant() -ne
        'f225af6c065719b4b147cb414be207501f9be55be5072dcf019970d5c3700b7f') {
        throw 'Exact 26.1.2.72 patched client or universal hash mismatch'
    }
    if ($classpath.Contains($patchedClient) -or $classpath.Contains($neoUniversal)) {
        throw '26.1.2 game content JARs must not be on the initial launcher classpath'
    }
} elseif ($MinecraftVersion -in @('1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21.0', '1.21.1')) {
    $patchedClient = Resolve-CachedPath $extra[0] @($forgeLibraryRoot)
    if (($neo1202 -or $neoIntermediate -or $neo1210 -or $neoModern) -and $classpath.Contains($patchedClient)) {
        throw 'NeoForge client/universal must not be on the modern legacy classpath'
    }
    if (($neoIntermediate -or $neo1210 -or $neoModern) -and
        $classpath.Contains((Resolve-CachedPath $extra[1] $libraryRoots))) {
        throw 'NeoForge universal must not be on the modern legacy classpath'
    }
    if ((Get-FileHash -LiteralPath $patchedClient -Algorithm SHA1).Hash.ToLowerInvariant() -ne $version.ClientSha1) {
        throw 'Exact NeoForge patched client SHA-1 mismatch'
    }
    if ($neoIntermediate -and
        (Get-FileHash -LiteralPath $patchedClient -Algorithm SHA256).Hash.ToLowerInvariant() -ne $version.ClientSha256) {
        throw "Exact NeoForge $MinecraftVersion patched client SHA-256 mismatch"
    }
    foreach ($required in @(
            @{ Classifier = 'srg'; Sha1 = $version.SrgSha1 },
            @{ Classifier = 'extra'; Sha1 = $version.ExtraSha1 })) {
        $relative = "net/minecraft/client/$mcpArtifact/client-$mcpArtifact-$($required.Classifier).jar"
        $path = Resolve-CachedPath $relative @($forgeLibraryRoot)
        if ((Get-FileHash -LiteralPath $path -Algorithm SHA1).Hash.ToLowerInvariant() -ne $required.Sha1) {
            throw "Exact Minecraft $($required.Classifier) SHA-1 mismatch"
        }
        if ($classpath.Contains($path)) { throw "Minecraft $($required.Classifier) must not be on the legacy classpath" }
    }
    if ($neoModern) {
        foreach ($required in @(
                @{ Relative = "net/neoforged/neoform/$mcpArtifact/neoform-$mcpArtifact.zip"; Sha1 = '811e2bd86fa2cda2812e5e8e51d718ea8bd6d3f4' },
                @{ Relative = "net/neoforged/neoform/$mcpArtifact/neoform-$mcpArtifact-mappings.txt"; Sha1 = 'c9fe69b8e39fc9ae8a56e6666204505866006cce' },
                @{ Relative = "net/neoforged/neoform/$mcpArtifact/neoform-$mcpArtifact-mappings-merged.txt"; Sha1 = '8184d8b290627ae54ab0eb980801bfe011f70175' },
                @{ Relative = "net/minecraft/client/$mcpArtifact/client-$mcpArtifact-slim.jar"; Sha1 = 'b4fb33003ee0975bc4511ff09d5d4a268b33fc2b' })) {
            $path = Resolve-CachedPath $required.Relative @($forgeLibraryRoot)
            if ((Get-FileHash -LiteralPath $path -Algorithm SHA1).Hash.ToLowerInvariant() -ne $required.Sha1) {
                throw "Exact reconstructed $($required.Relative) SHA-1 mismatch"
            }
        }
    }
    if ($neo1202) {
        foreach ($required in @(
                @{ Relative = "net/neoforged/neoform/$mcpArtifact/neoform-$mcpArtifact.zip"; Sha1 = 'ccc76c9cd813988c70b3098c6cfb3bed22395206' },
                @{ Relative = "net/neoforged/neoform/$mcpArtifact/neoform-$mcpArtifact-mappings.txt"; Sha1 = '861b07bb405cc6a1eda569d8ceca8606a883af25' },
                @{ Relative = "net/neoforged/neoform/$mcpArtifact/neoform-$mcpArtifact-mappings-merged.txt"; Sha1 = '94a7c2fab6cecacfcd7e2e72c499438886067cc9' },
                @{ Relative = "net/minecraft/client/$mcpArtifact/client-$mcpArtifact-slim.jar"; Sha1 = 'ee0b0899fafae86bfc71028d97508c86346e6622' })) {
            $path = Resolve-CachedPath $required.Relative @($forgeLibraryRoot)
            if ((Get-FileHash -LiteralPath $path -Algorithm SHA1).Hash.ToLowerInvariant() -ne $required.Sha1) {
                throw "Exact reconstructed $($required.Relative) SHA-1 mismatch"
            }
        }
        $universalEntry = "maven/$forgeRelative/neoforge-$forgeArtifact-universal.jar"
        if ((Get-ZipEntrySha256 $forgeInstaller $universalEntry) -ne
            'fec97f7f1d5ccb186b080711d3b2f0093e6eec54a311626fade06b3e52e7cdfe') {
            throw 'Exact embedded NeoForge 20.2.86 universal SHA-256 mismatch'
        }
    }
    if ($neoIntermediate) {
        foreach ($required in @(
                @{ Relative = "net/neoforged/neoform/$mcpArtifact/neoform-$mcpArtifact.zip"; Sha1 = $version.NeoformSha1 },
                @{ Relative = "net/neoforged/neoform/$mcpArtifact/neoform-$mcpArtifact-mappings.txt"; Sha1 = 'aa376dbbbed0368ec65c10df1880f4689d46fb50' },
                @{ Relative = "net/neoforged/neoform/$mcpArtifact/neoform-$mcpArtifact-mappings-merged.txt"; Sha1 = '287250bc0972170c092385871cf92c82faa8095e' },
                @{ Relative = "net/minecraft/client/$mcpArtifact/client-$mcpArtifact-slim.jar"; Sha1 = $version.SlimSha1 })) {
            $path = Resolve-CachedPath $required.Relative @($forgeLibraryRoot)
            if ((Get-FileHash -LiteralPath $path -Algorithm SHA1).Hash.ToLowerInvariant() -ne $required.Sha1) {
                throw "Exact reconstructed $($required.Relative) SHA-1 mismatch"
            }
        }
        $universalMetadata = @($forgeProfile.libraries | Where-Object {
                $_.name -eq "net.neoforged:neoforge:$forgeArtifact`:universal"
            })
        if ($universalMetadata.Count -ne 1 -or
            $universalMetadata[0].downloads.artifact.sha1 -ne $version.UniversalSha1 -or
            $universalMetadata[0].downloads.artifact.path -ne $extra[1]) {
            throw "Exact NeoForge $MinecraftVersion universal installer metadata mismatch"
        }
        $neoUniversal = Resolve-CachedPath $extra[1] @($neoRuntimeLibraryRoot, $libraryRoot)
        if ((Get-FileHash -LiteralPath $neoUniversal -Algorithm SHA1).Hash.ToLowerInvariant() -ne $version.UniversalSha1 -or
            (Get-FileHash -LiteralPath $neoUniversal -Algorithm SHA256).Hash.ToLowerInvariant() -ne $version.UniversalSha256) {
            throw "Exact NeoForge $MinecraftVersion universal JAR hash mismatch"
        }
        if ($MinecraftVersion -eq '1.20.3') {
            $universalEntry = "maven/$($extra[1])"
            if ((Get-ZipEntrySha256 $forgeInstaller $universalEntry) -ne $version.UniversalSha256) {
                throw 'Exact embedded NeoForge 1.20.3 universal JAR hash mismatch'
            }
        }
    }
    if ($neo1210) {
        if ((Get-FileHash -LiteralPath $patchedClient -Algorithm SHA256).Hash.ToLowerInvariant() -ne
            '4ad9456dfcac4572f9e0ecdf7019bc85b78fbe1b3aae1bcda42a943d73352df6') {
            throw 'Exact NeoForge 1.21.0 patched client SHA-256 mismatch'
        }
        $neoUniversal = Resolve-CachedPath $extra[1] @($forgeLibraryRoot)
        if ((Get-FileHash -LiteralPath $neoUniversal -Algorithm SHA1).Hash.ToLowerInvariant() -ne
            '85cf2b3f955a46444c9c9e0d8d0dd32b52152079' -or
            (Get-FileHash -LiteralPath $neoUniversal -Algorithm SHA256).Hash.ToLowerInvariant() -ne
            '8f661a7b46fac50de56e006eaf03ef6c2ef7c08adcb80665a88926786575d35e') {
            throw 'Exact NeoForge 1.21.0 universal JAR hash mismatch'
        }
        $slim = Resolve-CachedPath "net/minecraft/client/$mcpArtifact/client-$mcpArtifact-slim.jar" @($forgeLibraryRoot)
        if ((Get-FileHash -LiteralPath $slim -Algorithm SHA1).Hash.ToLowerInvariant() -ne
            'd61dd4becf68f2982d7fa2210a8445f716d283b7') {
            throw 'Exact NeoForm 1.21.0 slim client SHA-1 mismatch'
        }
    }
}

$templateArgs = @($forgeMeta.arguments.jvm)
if (-not $neoLatest) {
    $moduleArgIndex = [Array]::IndexOf($templateArgs, '-p') + 1
    if ($moduleArgIndex -le 0 -or $moduleArgIndex -ge $templateArgs.Count) { throw 'Missing pinned Forge module path' }
    $modulePath = if ($neo1202 -or $neoIntermediate) {
        $parts = @(([string] $templateArgs[$moduleArgIndex]).Split(@('${classpath_separator}'), [StringSplitOptions]::None))
        $resolved = foreach ($part in $parts) {
            if (-not $part.StartsWith('${library_directory}/', [StringComparison]::Ordinal)) {
                throw 'Unexpected exact NeoForge 1.20.2 module path template'
            }
            Resolve-CachedPath ($part.Substring('${library_directory}/'.Length)) $libraryRoots
        }
        $resolved -join ';'
    } else {
        ([string] $templateArgs[$moduleArgIndex]).Replace('${library_directory}', $forgeLibraryRoot).Replace('${classpath_separator}', ';')
    }
    foreach ($module in $modulePath.Split(';')) { Assert-File $module | Out-Null }
} elseif ([Array]::IndexOf($templateArgs, '-p') -ge 0) {
    throw 'Unexpected JPMS module path in exact 26.1.2.72 client profile'
}

if ($PreflightOnly) {
    return [pscustomobject]@{
        schema = "sfm-release-client-$($MinecraftVersion.Replace('.', ''))-preflight/1"
        minecraft = $MinecraftVersion
        loader = $forgeVersionId
        sfm_sha256 = $expected
        installer_sha256 = $version.InstallerSha256
        patched_client_sha1 = $version.ClientSha1
        asset_index_sha1 = [string] $minecraftMeta.assetIndex.sha1
        verified_asset_objects = $assetCoverage.cached_verified_objects
        verified_asset_bytes = $assetCoverage.cached_verified_bytes
        selected_launcher_libraries = $classpath.Count
        output_created = $false
        client_started = $false
    }
}

# No run directory is created until every offline input and version boundary is checked.
[IO.Directory]::CreateDirectory($run) | Out-Null
$game = Join-Path $run 'game'
$mods = Join-Path $game 'mods'
$control = Join-Path $run 'control'
$classes = Join-Path $run 'bridge-classes'
$natives = Join-Path $run 'natives'
foreach ($dir in @($game, $mods, $control, $classes, $natives)) { [IO.Directory]::CreateDirectory($dir) | Out-Null }
$runtimeLibraries = if ($neo1202 -or $neoIntermediate -or $neo1210) { Join-Path $run 'libraries' } else { $forgeLibraryRoot }
if ($neo1202) {
    foreach ($staged in @(
            @{ Relative = $extra[0]; Source = $patchedClient; Sha1 = $version.ClientSha1 },
            @{ Relative = "net/minecraft/client/$mcpArtifact/client-$mcpArtifact-srg.jar"; Source = (Resolve-CachedPath "net/minecraft/client/$mcpArtifact/client-$mcpArtifact-srg.jar" @($forgeLibraryRoot)); Sha1 = $version.SrgSha1 },
            @{ Relative = "net/minecraft/client/$mcpArtifact/client-$mcpArtifact-extra.jar"; Source = (Resolve-CachedPath "net/minecraft/client/$mcpArtifact/client-$mcpArtifact-extra.jar" @($forgeLibraryRoot)); Sha1 = $version.ExtraSha1 })) {
        $destination = Join-Path $runtimeLibraries $staged.Relative
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination)) | Out-Null
        Copy-Item -LiteralPath $staged.Source -Destination $destination
        if ((Get-FileHash -LiteralPath $destination -Algorithm SHA1).Hash.ToLowerInvariant() -ne $staged.Sha1) {
            throw 'Exact NeoForge 1.20.2 scratch runtime JAR copy mismatch'
        }
    }
    $universal = Join-Path $runtimeLibraries $extra[1]
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($universal)) | Out-Null
    $zip = [IO.Compression.ZipFile]::OpenRead($forgeInstaller)
    try {
        $entry = $zip.GetEntry($universalEntry)
        $inputStream = $entry.Open()
        $outputStream = [IO.File]::Create($universal)
        try { $inputStream.CopyTo($outputStream) } finally { $outputStream.Dispose(); $inputStream.Dispose() }
    } finally { $zip.Dispose() }
    if ((Get-FileHash -LiteralPath $universal -Algorithm SHA256).Hash.ToLowerInvariant() -ne
        'fec97f7f1d5ccb186b080711d3b2f0093e6eec54a311626fade06b3e52e7cdfe') {
        throw 'Exact NeoForge 1.20.2 scratch universal copy mismatch'
    }
}
if ($neoIntermediate) {
    foreach ($staged in @(
            @{ Relative = $extra[0]; Source = $patchedClient; Sha1 = $version.ClientSha1 },
            @{ Relative = $extra[1]; Source = $neoUniversal; Sha1 = $version.UniversalSha1 },
            @{ Relative = "net/minecraft/client/$mcpArtifact/client-$mcpArtifact-srg.jar"; Source = (Resolve-CachedPath "net/minecraft/client/$mcpArtifact/client-$mcpArtifact-srg.jar" @($forgeLibraryRoot)); Sha1 = $version.SrgSha1 },
            @{ Relative = "net/minecraft/client/$mcpArtifact/client-$mcpArtifact-extra.jar"; Source = (Resolve-CachedPath "net/minecraft/client/$mcpArtifact/client-$mcpArtifact-extra.jar" @($forgeLibraryRoot)); Sha1 = $version.ExtraSha1 })) {
        $destination = Join-Path $runtimeLibraries $staged.Relative
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination)) | Out-Null
        Copy-Item -LiteralPath $staged.Source -Destination $destination
        if ((Get-FileHash -LiteralPath $destination -Algorithm SHA1).Hash.ToLowerInvariant() -ne $staged.Sha1) {
            throw "Exact NeoForge $MinecraftVersion scratch game-content copy mismatch"
        }
    }
    # BootstrapLauncher may open legacy JARs via ZipFS; keep all such writes in
    # this fresh run, never in the launcher or installer caches.
    $stagedClasspath = [Collections.Generic.List[string]]::new()
    foreach ($source in $classpath) {
        $relative = $null
        foreach ($inputRoot in $libraryRoots) {
            if (Test-IsWithinOrSame $source $inputRoot) {
                $relative = $source.Substring($inputRoot.Length).TrimStart('\', '/')
                break
            }
        }
        if (-not $relative) { throw "Exact $MinecraftVersion launcher JAR escaped the verified input roots" }
        $destination = Join-Path $runtimeLibraries $relative
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination)) | Out-Null
        Copy-Item -LiteralPath $source -Destination $destination
        if ((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant() -ne
            (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()) {
            throw "Exact NeoForge $MinecraftVersion scratch launcher JAR copy mismatch"
        }
        $stagedClasspath.Add($destination)
    }
    $classpath = $stagedClasspath
    $moduleParts = @(([string] $templateArgs[$moduleArgIndex]).Split(@('${classpath_separator}'), [StringSplitOptions]::None))
    $modulePath = @(foreach ($part in $moduleParts) {
            Resolve-CachedPath ($part.Substring('${library_directory}/'.Length)) @($runtimeLibraries)
        }) -join ';'
    foreach ($module in $modulePath.Split(';')) { Assert-File $module | Out-Null }
}
if ($neo1210) {
    foreach ($staged in @(
            @{ Relative = $extra[0]; Source = $patchedClient; Sha1 = $version.ClientSha1 },
            @{ Relative = $extra[1]; Source = $neoUniversal; Sha1 = '85cf2b3f955a46444c9c9e0d8d0dd32b52152079' },
            @{ Relative = "net/minecraft/client/$mcpArtifact/client-$mcpArtifact-srg.jar"; Source = (Resolve-CachedPath "net/minecraft/client/$mcpArtifact/client-$mcpArtifact-srg.jar" @($forgeLibraryRoot)); Sha1 = $version.SrgSha1 },
            @{ Relative = "net/minecraft/client/$mcpArtifact/client-$mcpArtifact-extra.jar"; Source = (Resolve-CachedPath "net/minecraft/client/$mcpArtifact/client-$mcpArtifact-extra.jar" @($forgeLibraryRoot)); Sha1 = $version.ExtraSha1 })) {
        $destination = Join-Path $runtimeLibraries $staged.Relative
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination)) | Out-Null
        Copy-Item -LiteralPath $staged.Source -Destination $destination
        if ((Get-FileHash -LiteralPath $destination -Algorithm SHA1).Hash.ToLowerInvariant() -ne $staged.Sha1) {
            throw 'Exact NeoForge 1.21.0 scratch runtime JAR copy mismatch'
        }
    }
    # BootstrapLauncher opens and closes every legacy JAR through ZipFS. Stage
    # the hash-verified graph in this fresh run so Java never mutates or locks
    # the read-only user launcher cache.
    $stagedClasspath = [Collections.Generic.List[string]]::new()
    foreach ($source in $classpath) {
        if (-not (Test-IsWithinOrSame $source $forgeLibraryRoot)) {
            throw 'Exact 1.21.0 launcher library escaped the pinned cache root'
        }
        $relative = $source.Substring($forgeLibraryRoot.Length).TrimStart('\', '/')
        $destination = Join-Path $runtimeLibraries $relative
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination)) | Out-Null
        Copy-Item -LiteralPath $source -Destination $destination
        if ((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant() -ne
            (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()) {
            throw 'Exact NeoForge 1.21.0 scratch launcher JAR copy mismatch'
        }
        $stagedClasspath.Add($destination)
    }
    $classpath = $stagedClasspath
    $modulePath = $modulePath.Replace($forgeLibraryRoot, $runtimeLibraries)
    foreach ($module in $modulePath.Split(';')) { Assert-File $module | Out-Null }
}
if ($MinecraftVersion -in @('1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21.0', '1.21.1', '26.1.2')) {
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

$fmlCompile = if ($neoLatest) {
    @("net/neoforged/fancymodloader/loader/$($version.Fml)/loader-$($version.Fml).jar")
} elseif ($neo1210 -or $neoModern -or $MinecraftVersion -eq '1.20.4') {
    @("net/neoforged/fancymodloader/loader/$($version.Fml)/loader-$($version.Fml).jar")
} elseif ($MinecraftVersion -eq '1.20.1') {
    @(
        "net/neoforged/fancymodloader/core/$($version.Fml)/core-$($version.Fml).jar",
        "net/neoforged/fancymodloader/language-java/$($version.Fml)/language-java-$($version.Fml).jar"
    )
} elseif ($neo1202 -or $MinecraftVersion -eq '1.20.3') {
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
$compileClasspath = if ($neoLatest) {
    @($patchedClient, $neoUniversal) + @($classpath)
} elseif ($neo1210) {
    @($joined) + @(@(
            "net/neoforged/fancymodloader/loader/$($version.Fml)/loader-$($version.Fml).jar",
            "net/neoforged/bus/$($version.EventBus)/bus-$($version.EventBus).jar",
            'net/neoforged/mergetool/2.0.0/mergetool-2.0.0-api.jar',
            "com/mojang/datafixerupper/$($version.DataFixer)/datafixerupper-$($version.DataFixer).jar"
        ) | ForEach-Object { Resolve-CachedPath $_ @($runtimeLibraries) })
} elseif ($neoModern) {
    @($joined) + @(@(
            "net/neoforged/fancymodloader/loader/$($version.Fml)/loader-$($version.Fml).jar",
            "net/neoforged/bus/$($version.EventBus)/bus-$($version.EventBus).jar",
            'net/neoforged/mergetool/2.0.0/mergetool-2.0.0-api.jar',
            "com/mojang/datafixerupper/$($version.DataFixer)/datafixerupper-$($version.DataFixer).jar"
        ) | ForEach-Object { Resolve-CachedPath $_ $libraryRoots })
} elseif ($neoIntermediate) {
    $fmlRelative = if ($MinecraftVersion -eq '1.20.3') {
        @(
            "net/neoforged/fancymodloader/core/$($version.Fml)/core-$($version.Fml).jar",
            "net/neoforged/fancymodloader/language-java/$($version.Fml)/language-java-$($version.Fml).jar"
        )
    } else {
        @("net/neoforged/fancymodloader/loader/$($version.Fml)/loader-$($version.Fml).jar")
    }
    @($joined) + @((@($fmlRelative) + @(
                "net/neoforged/bus/$($version.EventBus)/bus-$($version.EventBus).jar",
                'net/neoforged/mergetool/2.0.0/mergetool-2.0.0-api.jar',
                "com/mojang/datafixerupper/$($version.DataFixer)/datafixerupper-$($version.DataFixer).jar"
            )) | ForEach-Object { Resolve-CachedPath $_ @($runtimeLibraries) })
} elseif ($neo1202) {
    @($joined, $universal) + @(@(
            "net/neoforged/fancymodloader/core/$($version.Fml)/core-$($version.Fml).jar",
            "net/neoforged/fancymodloader/language-java/$($version.Fml)/language-java-$($version.Fml).jar",
            "net/neoforged/bus/$($version.EventBus)/bus-$($version.EventBus).jar",
            "com/mojang/datafixerupper/$($version.DataFixer)/datafixerupper-$($version.DataFixer).jar"
        ) | ForEach-Object { Resolve-CachedPath $_ $libraryRoots })
} else {
    $compileRelative = @(
        "net/minecraft/client/$mcpArtifact/client-$mcpArtifact-srg.jar",
        "$forgeRelative/forge-$forgeArtifact-client.jar",
        "$forgeRelative/forge-$forgeArtifact-universal.jar"
    ) + $fmlCompile + @(
        "net/minecraftforge/eventbus/$($version.EventBus)/eventbus-$($version.EventBus).jar",
        "com/mojang/datafixerupper/$($version.DataFixer)/datafixerupper-$($version.DataFixer).jar"
    )
    @($compileRelative | ForEach-Object { Resolve-CachedPath $_ $libraryRoots })
}
$javaSource = Assert-File (Join-Path $bridgeSourceRoot 'java/ca/teamdman/sfm/releaseprobe/ReleaseClientBridge.java')
$networkSource = Assert-File (Join-Path $bridgeRoot 'src/main/java/ca/teamdman/sfm/releaseprobe/ReleaseClientNetworkProbe.java')
$javaVersion = if ($neoLatest) { '25' } elseif ($neo1210 -or $neoModern) { '21' } else { '17' }
& $javac -proc:none -source $javaVersion -target $javaVersion -classpath ($compileClasspath -join ';') -d $classes $javaSource $networkSource
if ($LASTEXITCODE -ne 0) { throw "Bridge javac failed: $LASTEXITCODE" }
Assert-File (Join-Path $classes 'ca/teamdman/sfm/releaseprobe/ReleaseClientBridge.class') | Out-Null
Assert-File (Join-Path $classes 'ca/teamdman/sfm/releaseprobe/ReleaseClientNetworkProbe.class') | Out-Null
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
    $jvm.Add($arg.Replace('${library_directory}', $runtimeLibraries).Replace('${classpath_separator}', ';').Replace('${version_name}', $forgeVersionId))
}
if (-not $neoLatest) { $jvm.Add("-DlegacyClassPath=$($classpath -join ';')") }
$jvm.Add('-cp')
$jvm.Add($classpath -join ';')
$jvm.Add($(if ($neoLatest) { 'net.neoforged.fml.startup.Client' } else { 'cpw.mods.bootstraplauncher.BootstrapLauncher' }))
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
    $expectedSchema = switch ($CaptureMode) {
        'world' { 'sfm-release-client-world-proof/1' }
        'network-roundtrip' { 'sfm-release-client-network-proof/1' }
        default { 'sfm-release-client-proof/1' }
    }
    if ($result.schema -ne $expectedSchema -or $result.run_id -ne $runId -or
        $result.sfm_sha256 -ne $expected -or $result.status -ne 'passed') {
        throw "Bridge reported failure or unexpected identity: $($result | ConvertTo-Json -Compress)"
    }
    if ($CaptureMode -eq 'network-roundtrip') {
        if ($result.capture_mode -ne 'network-roundtrip' -or
            $result.world_id -ne ('sfm_release_probe_' + $runId.Replace('-', '')) -or
            $result.request_packet -ne 'ServerboundServerConfigRequestPacket' -or
            $result.request_mode -ne 'SHOW' -or
            $result.request_sent -isnot [bool] -or $result.request_sent -ne $true -or
            $result.response_observed -isnot [bool] -or $result.response_observed -ne $true -or
            $result.codec_roundtrip -isnot [bool] -or $result.codec_roundtrip -ne $true -or
            $result.request_body_sha256 -cnotmatch '^[0-9a-f]{64}$' -or
            $result.response_body_sha256 -cnotmatch '^[0-9a-f]{64}$' -or
            ($result.request_body_bytes -isnot [int] -and $result.request_body_bytes -isnot [long]) -or
            ($result.response_body_bytes -isnot [int] -and $result.response_body_bytes -isnot [long]) -or
            $result.request_body_bytes -ne 1 -or $result.response_body_bytes -le 1 -or
            $result.response_screen -ne 'ca.teamdman.sfm.client.screen.TomlEditScreen') {
            throw "Bridge network witness is incomplete: $($result | ConvertTo-Json -Compress)"
        }
        $serverLog = Assert-File (Join-Path $game 'logs/latest.log')
        $serverResponses = @(Select-String -LiteralPath $serverLog -SimpleMatch -Pattern 'Sending config to player:').Count
        if ($serverResponses -ne 1) {
            throw "Expected exactly one SFM server config response; observed $serverResponses"
        }
    } else {
        $expectedScreen = if ($CaptureMode -eq 'world') { 'world' } else { 'title' }
        $expectedScreenshot = if ($CaptureMode -eq 'world') { 'sfm-release-client-world.png' } else { 'sfm-release-client-title.png' }
        if ($result.screen -ne $expectedScreen -or $result.screenshot -ne $expectedScreenshot) {
            throw "Bridge reported unexpected capture: $($result | ConvertTo-Json -Compress)"
        }
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
    if ($CaptureMode -ne 'network-roundtrip') {
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
    }
    $sourceHashAfter = (Get-FileHash -LiteralPath $sourceJar -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($sourceHashAfter -ne $expected) { throw 'Original SFM JAR changed during client proof' }
    if ($CaptureMode -eq 'network-roundtrip') {
        Write-Host "SFM_RELEASE_PROBE_PASS run_id=$runId sfm_sha256=$expected network_roundtrip=true server_config_responses=1"
    } else {
        Write-Host "SFM_RELEASE_PROBE_PASS run_id=$runId sfm_sha256=$expected screenshot=$screenshot"
    }
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
