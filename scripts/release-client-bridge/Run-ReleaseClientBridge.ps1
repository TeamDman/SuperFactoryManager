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
    [ValidateSet('1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.21.1', '26.1.2')] [string] $MinecraftVersion = '1.19.2',
    [string] $NeoForgeInstaller,
    [string] $NeoForgeLibraryRoot,
    [string] $NeoForgeRuntimeLibraryRoot,
    [string] $AssetRoot,
    [string] $JoinedCompileJar,
    [switch] $PreflightOnly,
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
            throw '1.20.2 scratch input or RunRoot may not traverse a symbolic link or junction'
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
if ($PreflightOnly -and $MinecraftVersion -ne '1.20.2') {
    throw '-PreflightOnly currently applies only to the exact 1.20.2 client fixture'
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
    '1.21.1' { @{ Forge = '21.1.206'; Mcp = '20240808.144430'; Assets = '17'; DataFixer = '8.0.16'; EventBus = '8.0.5'; ForgeGroup = 'net/neoforged'; Fml = '4.0.41'; ClientSha1 = 'c84e84858e2a57eead05f5ca55a922af8009dce8'; SrgSha1 = 'a4827225b3c07662ca68b03ea20d11433a0c0488'; ExtraSha1 = 'db5c59932751d66c2f57c1c2de41b48712620975'; InstallerSha256 = '479d540cd2d1cea09d8b8cb63266578db9c58ed174546121a43a4bb4084454be' } }
    '26.1.2' { @{ Forge = '26.1.2.72'; Mcp = ''; Assets = '30'; ForgeGroup = 'net/neoforged'; Fml = '11.0.13'; ClientSha1 = 'b4054c9102e61029f9ad9c3238831b41444702e9'; InstallerSha256 = '249799b185eb7c9fadbe91f533f1f25f6a59c2d7d545430f587c434e5e55902b' } }
}
$neo1202 = $MinecraftVersion -eq '1.20.2'
$neoModern = $MinecraftVersion -eq '1.21.1'
$neoLatest = $MinecraftVersion -eq '26.1.2'
$neoNamed = $neo1202 -or $neoModern -or $neoLatest
$forgeArtifact = if ($neoNamed) { $version.Forge } else { "$MinecraftVersion-$($version.Forge)" }
$forgeRelative = if ($neoNamed) { "net/neoforged/neoforge/$forgeArtifact" } else { "$($version.ForgeGroup)/forge/$forgeArtifact" }
$mcpArtifact = "$MinecraftVersion-$($version.Mcp)"
$forgeVersionId = if ($neoNamed) { "neoforge-$($version.Forge)" } else { "$MinecraftVersion-forge-$($version.Forge)" }
$bridgeSourceRoot = if ($neoLatest) {
    Join-Path $bridgeRoot 'src/26.1.2'
} elseif ($neoModern) {
    Join-Path $bridgeRoot 'src/1.21.1'
} elseif ($neo1202) {
    Join-Path $bridgeRoot 'src/1.20.2'
} elseif ($MinecraftVersion -ne '1.19.2') {
    Join-Path $bridgeRoot 'src/1.19.4'
} else {
    Join-Path $bridgeRoot 'src/main'
}
$bridgeResourceRoot = if ($MinecraftVersion -in @('1.20', '1.20.1', '1.20.2', '1.21.1', '26.1.2')) {
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
if ($neo1202 -and $expected -notin @(
        'afce7113bc55d12fe92db02f79b95d702e530fb5fd10accba96edcdbcf0186e2',
        '9b5f00d887edd7a72dd35a7e1d2131a17083932793f06649d362053c34f9808d')) {
    throw '1.20.2 source JAR is not an exact official/projected 4.34.0 input'
}
if ($neoLatest -and $expected -notin @(
        'cace8809600cea007dbe5c73dc04c2f780375547ec0717a1ec8c25d991140bf1',
        '9b2ff101e1158f42bf9218177c1f063375ce58254ff0de629db4dc52ef3c15b6')) {
    throw '26.1.2 source JAR is not an exact official/projected 4.34.0 input'
}
$sourceHashBefore = (Get-FileHash -LiteralPath $sourceJar -Algorithm SHA256).Hash.ToLowerInvariant()
if ($sourceHashBefore -ne $expected) { throw 'Original SFM JAR hash does not match expectation' }

$libraryRoot = Join-Path $prism 'libraries'
$assetsRoot = if ($neo1202) {
    if (-not $AssetRoot) { throw 'Exact 1.20.2 requires -AssetRoot with the isolated complete asset stage' }
    [IO.Path]::GetFullPath($AssetRoot).TrimEnd('\', '/')
} else {
    if ($AssetRoot) { throw '-AssetRoot is only supported for exact 1.20.2' }
    Join-Path $prism 'assets'
}
$minecraftMetaPath = Assert-File (Join-Path $prism "meta/net.minecraft/$MinecraftVersion.json")
$minecraftMeta = Get-Content -LiteralPath $minecraftMetaPath -Raw | ConvertFrom-Json
$lwjglVersion = if ($neoLatest) { '3.4.1' } elseif ($neoModern) { '3.3.3' } else { '3.3.1' }
$lwjglMetaPath = Assert-File (Join-Path $prism "meta/org.lwjgl3/$lwjglVersion.json")
$lwjglMeta = Get-Content -LiteralPath $lwjglMetaPath -Raw | ConvertFrom-Json
if ($neo1202 -and
    ((Get-FileHash -LiteralPath $minecraftMetaPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne
        'f5c01fd900ed402add6a6af0cf9e2bd46a6e01d7410c7f9efcc627e3cf5ca4b5' -or
        (Get-FileHash -LiteralPath $lwjglMetaPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne
        '568dd8e999d242304ecd1bdccf1d06e968ff62d9b64d6145e0b97287575e7e08')) {
    throw 'Exact 1.20.2 Minecraft or LWJGL launcher manifest SHA-256 mismatch'
}
$forgeLibraryRoot = $libraryRoot
if ($neo1202) {
    if (-not $NeoForgeRuntimeLibraryRoot) {
        throw 'Exact 1.20.2 requires -NeoForgeRuntimeLibraryRoot with cached installer-selected libraries'
    }
    $neoRuntimeLibraryRoot = [IO.Path]::GetFullPath($NeoForgeRuntimeLibraryRoot).TrimEnd('\', '/')
    if (-not [IO.Directory]::Exists($neoRuntimeLibraryRoot) -or
        (Test-IsWithinOrSame $run $neoRuntimeLibraryRoot) -or
        (Test-IsWithinOrSame $neoRuntimeLibraryRoot $run)) {
        throw 'Exact 1.20.2 runtime library root must exist and be separate from RunRoot'
    }
} elseif ($NeoForgeRuntimeLibraryRoot) {
    throw '-NeoForgeRuntimeLibraryRoot is only supported for exact 1.20.2'
}
if ($neoLatest -and ($NeoForgeLibraryRoot -or $JoinedCompileJar)) {
    throw '26.1.2 uses its exact cached library graph; isolated 1.21.1-only inputs are not accepted'
}
if ($MinecraftVersion -in @('1.20.1', '1.20.2', '1.21.1')) {
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
if ($forgeMeta.id -ne $forgeVersionId -or $minecraftMeta.mainJar.name -ne "com.mojang:minecraft:${MinecraftVersion}:client") {
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
if ($neoLatest -and (Get-FileHash -LiteralPath $assetIndex -Algorithm SHA1).Hash.ToLowerInvariant() -ne
    [string] $minecraftMeta.assetIndex.sha1) {
    throw 'Exact 26.1.2 asset index SHA-1 mismatch'
}
$libraryRoots = if ($neoModern) {
    @($forgeLibraryRoot)
} elseif ($neo1202) {
    @($forgeLibraryRoot, $neoRuntimeLibraryRoot, $libraryRoot)
} elseif ($MinecraftVersion -eq '1.20.1') {
    @($forgeLibraryRoot, $libraryRoot)
} else {
    @($libraryRoot)
}

$classpath = [Collections.Generic.List[string]]::new()
$seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($lib in @($forgeMeta.libraries) + @($minecraftMeta.libraries) + @($lwjglMeta.libraries)) {
    if (-not (Test-SelectedLibrary $lib)) { continue }
    $path = Get-LibraryPath $lib $libraryRoots
    if (($neo1202 -or $neoLatest) -and
        (([string] $lib.downloads.artifact.sha1 -notmatch '^[0-9a-f]{40}$') -or
            (Get-FileHash -LiteralPath $path -Algorithm SHA1).Hash.ToLowerInvariant() -ne
            [string] $lib.downloads.artifact.sha1)) {
        throw "Exact launcher library SHA-1 mismatch: $($lib.name)"
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
} elseif ($neo1202) {
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
    if ($neo1202 -and $relative -eq $extra[1]) { continue }
    $path = Resolve-CachedPath $relative @($forgeLibraryRoot)
    if ($neo1202 -or $neoModern -or $neoLatest) {
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
} elseif ($MinecraftVersion -in @('1.20.1', '1.20.2', '1.21.1')) {
    $patchedClient = Resolve-CachedPath $extra[0] @($forgeLibraryRoot)
    if (($neo1202 -or $neoModern) -and $classpath.Contains($patchedClient)) {
        throw 'NeoForge client/universal must not be on the modern legacy classpath'
    }
    if ($neoModern -and
        $classpath.Contains((Resolve-CachedPath $extra[1] @($forgeLibraryRoot)))) {
        throw 'NeoForge universal must not be on the modern legacy classpath'
    }
    if ((Get-FileHash -LiteralPath $patchedClient -Algorithm SHA1).Hash.ToLowerInvariant() -ne $version.ClientSha1) {
        throw 'Exact NeoForge patched client SHA-1 mismatch'
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
}

$templateArgs = @($forgeMeta.arguments.jvm)
if (-not $neoLatest) {
    $moduleArgIndex = [Array]::IndexOf($templateArgs, '-p') + 1
    if ($moduleArgIndex -le 0 -or $moduleArgIndex -ge $templateArgs.Count) { throw 'Missing pinned Forge module path' }
    $modulePath = if ($neo1202) {
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
        schema = 'sfm-release-client-1202-preflight/1'
        minecraft = '1.20.2'
        loader = $forgeVersionId
        sfm_sha256 = $expected
        installer_sha256 = $version.InstallerSha256
        patched_client_sha1 = $version.ClientSha1
        asset_index_sha1 = '21beaec863755c8fd4620b22ed9bdbc6718b3c32'
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
$runtimeLibraries = if ($neo1202) { Join-Path $run 'libraries' } else { $forgeLibraryRoot }
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
if ($MinecraftVersion -in @('1.20', '1.20.1', '1.20.2', '1.21.1', '26.1.2')) {
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
} elseif ($neoModern) {
    @("net/neoforged/fancymodloader/loader/$($version.Fml)/loader-$($version.Fml).jar")
} elseif ($MinecraftVersion -eq '1.20.1') {
    @(
        "net/neoforged/fancymodloader/core/$($version.Fml)/core-$($version.Fml).jar",
        "net/neoforged/fancymodloader/language-java/$($version.Fml)/language-java-$($version.Fml).jar"
    )
} elseif ($neo1202) {
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
} elseif ($neoModern) {
    @($joined) + @(@(
            "net/neoforged/fancymodloader/loader/$($version.Fml)/loader-$($version.Fml).jar",
            "net/neoforged/bus/$($version.EventBus)/bus-$($version.EventBus).jar",
            'net/neoforged/mergetool/2.0.0/mergetool-2.0.0-api.jar',
            "com/mojang/datafixerupper/$($version.DataFixer)/datafixerupper-$($version.DataFixer).jar"
        ) | ForEach-Object { Resolve-CachedPath $_ $libraryRoots })
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
$javaVersion = if ($neoLatest) { '25' } elseif ($neoModern) { '21' } else { '17' }
& $javac -proc:none -source $javaVersion -target $javaVersion -classpath ($compileClasspath -join ';') -d $classes $javaSource
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
