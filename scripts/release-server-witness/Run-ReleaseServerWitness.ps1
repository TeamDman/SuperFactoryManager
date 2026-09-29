<#
Test-only 1.19.2/1.19.4/1.20/1.20.1/1.20.2/1.20.3/1.20.4/1.21.0/1.21.1/26.1.2 production-JAR registry/save witness.
The input JARs, JDK, installer, and compile classpath are read-only. Every
server, mod copy, world, log, and result is created below a NEW RunRoot.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $OfficialJar,
    [Parameter(Mandatory)] [string] $OfficialSha256,
    [Parameter(Mandatory)] [string] $ProjectedJar,
    [Parameter(Mandatory)] [string] $ProjectedSha256,
    [Parameter(Mandatory)] [Alias('LoaderInstaller')] [string] $ForgeInstaller,
    [Parameter(Mandatory)] [Alias('LoaderCompileJar')] [string] $ForgeSrgJar,
    [Parameter(Mandatory)] [string] $JavaHome,
    [Parameter(Mandatory)] [string] $RunRoot,
    [ValidateSet('1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21.0', '1.21.1', '26.1.2')] [string] $Target = '1.19.4',
    [string] $LauncherCacheRoot = '',
    [Alias('InstalledLoaderRoot')] [string] $InstalledForgeRoot = '',
    [ValidateRange(60, 900)] [int] $StartupTimeoutSeconds = 300,
    [ValidateRange(5, 120)] [int] $CommandTimeoutSeconds = 40
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$target = $Target
$version = switch ($target) {
    '1.19.2' {
        @{
            loader = '43.4.0'
            loader_brand = 'Forge'
            loader_id = 'forge'
            forge_group_path = 'net/minecraftforge'
            fml_group_path = 'net/minecraftforge'
            fml_core_artifact = 'fmlcore'
            fml_language_artifact = 'javafmllanguage'
            fml_library_version = '1.19.2-43.4.0'
            event_bus = '6.0.3'
            installer_sha1 = '3cf86bde9ae968eeac44a6f1b1f88f92f73e67b7'
            installer_sha256 = '13200fcc4b00959734cd7bb193cb4e5e6ea756635cdc9e61c23ec45ac632880e'
            srg_sha256 = 'ade8d66611fcbaef14a5238c681b9220383531692e5751e1467418cea3274324'
            official_sha256 = 'f2c0242a984b8b782cc995e20a59e71bbdb48981873ab31a4531f0d1db3af038'
            projected_sha256 = '024e9b10463235f10e61cb0e7285f8a082e90800ead8c3f5146b58c51146eccf'
            probe_source = 'versions/1.19.2/src/main/java/ca/teamdman/sfm/releaseprobe/ReleaseServerRegistryProbe.java'
            resources = 'versions/1.19.2/resources'
        }
    }
    '1.19.4' {
        @{
            loader = '45.0.9'
            loader_brand = 'Forge'
            loader_id = 'forge'
            forge_group_path = 'net/minecraftforge'
            fml_group_path = 'net/minecraftforge'
            fml_core_artifact = 'fmlcore'
            fml_language_artifact = 'javafmllanguage'
            fml_library_version = '1.19.4-45.0.9'
            event_bus = '6.0.5'
            installer_sha1 = 'b1cdd5fa1cc50fa23a32c9b38fd9d1f8a9a6c5e9'
            installer_sha256 = '58e32beb55e0117bba1d34a89ffa74f8130de41b762d2c20ae9adf3a1342b007'
            srg_sha256 = 'f81cf06416f7e87a91ce0769345fe2bec45e46bc271c30dbfc8dbe07fec4187c'
            official_sha256 = '09b8c7d2ae6453d39444c61174979d1938d25d4f24282f8396711f329c5b5fba'
            projected_sha256 = '4a39e9512a47925639a1ba7126ea903576917b120d43dcedfa66563ebcea9735'
            resources = 'src/main/resources'
        }
    }
    '1.20' {
        @{
            loader = '46.0.10'
            loader_brand = 'Forge'
            loader_id = 'forge'
            forge_group_path = 'net/minecraftforge'
            fml_group_path = 'net/minecraftforge'
            fml_core_artifact = 'fmlcore'
            fml_language_artifact = 'javafmllanguage'
            fml_library_version = '1.20-46.0.10'
            event_bus = '6.0.3'
            installer_sha1 = 'b87fb7da06335a907a59d32dc22698f3f2a1f885'
            installer_sha256 = 'e94cf05d3fe16b772372848e3fb66892789731a96781b652f76598bc25f2fb39'
            srg_sha256 = '64e6fc6827c1a350e4c8e9d6c4e95ae88c9dc3e5085d9e00bb171730b39dd11f'
            official_sha256 = '9943c04e04f7afc433e3f9ccea223ab15f42ba0930c4cebe31b2008ad3b35ecd'
            projected_sha256 = '90227fa968cd7351adcc5a1e8939c29ee6b3d4109c50d66c48d824425508a583'
            resources = 'versions/1.20/resources'
        }
    }
    '1.20.1' {
        @{
            loader = '47.1.65'
            loader_brand = 'NeoForge'
            loader_id = 'neoforge'
            forge_group_path = 'net/neoforged'
            fml_group_path = 'net/neoforged/fancymodloader'
            fml_core_artifact = 'core'
            fml_language_artifact = 'language-java'
            fml_library_version = '47.1.47'
            event_bus = '6.0.5'
            installer_sha1 = '63f215c2608ff0c3451a3d64c3ece283a1198d27'
            installer_sha256 = 'c0056d398ccc685db87f98939ecd22d54e4a556fcb943cee00df56ed2015b6d9'
            srg_sha256 = '7072059572222849ee76e4ae182173ce6fbd74d9170de967905fb277d2d0b3ad'
            official_sha256 = '34ee6eab2783b0b3a53450a6700c215e21dc653357862b24be7888e95554ef56'
            projected_sha256 = '6813401aeeeca8f0d1d1608e732f601b94c0eff181ba125f5bfc5dccaa6aabb9'
            resources = 'versions/1.20.1/resources'
        }
    }
    '1.20.2' {
        @{
            loader = '20.2.86'
            loader_brand = 'NeoForge'
            loader_id = 'neoforge'
            forge_group_path = 'net/neoforged'
            artifact_module = 'neoforge'
            artifact_version = '20.2.86'
            launch_version_flag = '--fml.neoForgeVersion'
            fml_group_path = 'net/neoforged/fancymodloader'
            fml_core_artifact = 'core'
            fml_language_artifact = 'language-java'
            fml_library_version = '1.0.16'
            event_bus_path = 'libraries/net/neoforged/bus/7.2.0/bus-7.2.0.jar'
            compile_extra_paths = @('libraries/com/mojang/datafixerupper/6.0.8/datafixerupper-6.0.8.jar')
            compile_jar_name = 'raw.jar'
            compile_jar_kind = 'neoform_joined_1.20.2-20231019.002635'
            compile_jar_sha256 = '4ef3f10b2a4464687aa5e19e06586a8f33e68f63caf73ee48ae93c5e7c6f24ab'
            installer_sha1 = '9546d34c7e966b6dfa952e2d6150e1713e81884c'
            installer_sha256 = 'c21378ea25e4c1b1eb367f7eda7db48af6965e3e35c2270421f10687faf8d4db'
            official_sha256 = 'afce7113bc55d12fe92db02f79b95d702e530fb5fd10accba96edcdbcf0186e2'
            projected_sha256 = '9b5f00d887edd7a72dd35a7e1d2131a17083932793f06649d362053c34f9808d'
            probe_source = 'versions/1.20.3/src/main/java/ca/teamdman/sfm/releaseprobe/ReleaseServerRegistryProbe.java'
            resources = 'versions/1.20.2/resources'
        }
    }
    '1.20.3' {
        @{
            loader = '20.3.8-beta'
            loader_brand = 'NeoForge'
            loader_id = 'neoforge'
            forge_group_path = 'net/neoforged'
            artifact_module = 'neoforge'
            artifact_version = '20.3.8-beta'
            launch_version_flag = '--fml.neoForgeVersion'
            fml_group_path = 'net/neoforged/fancymodloader'
            fml_core_artifact = 'core'
            fml_language_artifact = 'language-java'
            fml_library_version = '1.0.16'
            event_bus_path = 'libraries/net/neoforged/bus/7.2.0/bus-7.2.0.jar'
            compile_extra_paths = @('libraries/com/mojang/datafixerupper/6.0.8/datafixerupper-6.0.8.jar')
            compile_jar_name = 'raw.jar'
            compile_jar_kind = 'neoform_joined_1.20.3-20231205.165107'
            compile_jar_sha256 = '61773c57a22655ca25b9ba6057a89111fb6818cc65313d9295ba8fe863b805dd'
            installer_sha1 = '0819254b3df5039f3bdcd7f80b1a1edb395ba78c'
            installer_sha256 = 'd59ba6f0c867ddaafe0247ba7ddf527389897806795b9bd543a2c4313695f9d7'
            official_sha256 = 'e4686d536227e0ead220f56606a5527ec8057504cfc77712a1c892d6d14d82b1'
            projected_sha256 = '3cd66ed27313c067f3529f94765dfc2a51d36b376bbbf841b0f95c8874389d58'
            probe_source = 'versions/1.20.3/src/main/java/ca/teamdman/sfm/releaseprobe/ReleaseServerRegistryProbe.java'
            resources = 'versions/1.20.3/resources'
        }
    }
    '1.20.4' {
        @{
            loader = '20.4.231'
            loader_brand = 'NeoForge'
            loader_id = 'neoforge'
            forge_group_path = 'net/neoforged'
            artifact_module = 'neoforge'
            artifact_version = '20.4.231'
            launch_version_flag = '--fml.neoForgeVersion'
            fml_group_path = 'net/neoforged/fancymodloader'
            fml_core_artifact = 'loader'
            fml_language_artifact = 'spi'
            fml_library_version = '2.0.17'
            event_bus_path = 'libraries/net/neoforged/bus/7.2.0/bus-7.2.0.jar'
            compile_extra_paths = @('libraries/com/mojang/datafixerupper/6.0.8/datafixerupper-6.0.8.jar')
            compile_jar_name = 'raw.jar'
            compile_jar_kind = 'neoform_joined_1.20.4-20231207.154220'
            compile_jar_sha256 = 'c062e6791ba0159cc27ba0f3ec8cd33307b73bca22f3f6a6423e76ab02c254de'
            installer_sha1 = '1626630511c271e0e4dafef9e1513726afa71c61'
            installer_sha256 = '8002077d9454603611b0bb5fd66a107c2dd2a27942ebe52c39db2a9280eaac18'
            official_sha256 = 'b83b43fc8ee7cb7863da5f0d039b026675c2f2c68956f99643c83a9736f7a63b'
            projected_sha256 = '3964a8742211e9cc39b44ee61b4402387382c948ba7c17b1d887c013f764813e'
            probe_source = 'versions/1.20.3/src/main/java/ca/teamdman/sfm/releaseprobe/ReleaseServerRegistryProbe.java'
            resources = 'versions/1.20.4/resources'
        }
    }
    '1.21.0' {
        @{
            loader = '21.0.143'
            loader_brand = 'NeoForge'
            loader_id = 'neoforge'
            minecraft = '1.21'
            forge_group_path = 'net/neoforged'
            artifact_module = 'neoforge'
            artifact_version = '21.0.143'
            launch_version_flag = '--fml.neoForgeVersion'
            fml_group_path = 'net/neoforged/fancymodloader'
            fml_core_artifact = 'loader'
            fml_language_artifact = ''
            fml_library_version = '4.0.21'
            event_bus_path = 'libraries/net/neoforged/bus/8.0.1/bus-8.0.1.jar'
            compile_extra_paths = @('libraries/com/mojang/datafixerupper/8.0.16/datafixerupper-8.0.16.jar')
            compile_jar_name = 'raw.jar'
            compile_jar_kind = 'neoform_joined_1.21-20240613.152323'
            compile_jar_sha256 = '10ee2981219a2e0fe82a18abb6044ab29ffdb3cd60c56c281ee0ce3365584d93'
            installer_sha1 = 'cd0f2f98cee06ef3d22e27fcf5e80a88bde6cf02'
            installer_sha256 = '02e511f97bcfd2985937fc205aa95c93588bf3d9939c6ef18100add57d1e8bec'
            official_sha256 = 'c76399b2456daccd88050ef45d36cac7f5d4bf60535adb2dd0d0ffeab19daa5f'
            projected_sha256 = 'e233d788e54db4078fa90a8324c4132fc3fbfbe103b78ca03003d51d9a0e947b'
            probe_source = 'versions/1.21.0/src/main/java/ca/teamdman/sfm/releaseprobe/ReleaseServerRegistryProbe.java'
            resources = 'versions/1.21.0/resources'
            mod_manifest = 'META-INF/neoforge.mods.toml'
            fixture_source = 'versions/1.21.0/Fixture.ps1'
            java_source = '21'
        }
    }
    '1.21.1' {
        @{
            loader = '21.1.206'
            loader_brand = 'NeoForge'
            loader_id = 'neoforge'
            minecraft = '1.21.1'
            forge_group_path = 'net/neoforged'
            artifact_module = 'neoforge'
            artifact_version = '21.1.206'
            launch_version_flag = '--fml.neoForgeVersion'
            fml_group_path = 'net/neoforged/fancymodloader'
            fml_core_artifact = 'loader'
            fml_language_artifact = ''
            fml_library_version = '4.0.41'
            event_bus_path = 'libraries/net/neoforged/bus/8.0.5/bus-8.0.5.jar'
            compile_extra_paths = @('libraries/com/mojang/datafixerupper/8.0.16/datafixerupper-8.0.16.jar')
            compile_jar_name = 'raw.jar'
            compile_jar_kind = 'neoform_joined_1.21.1-20240808.144430'
            compile_jar_sha256 = 'cef91058da78da95ac888fb9056d258ac29f7dd7712d7450029c56d80f31dda6'
            installer_sha1 = 'ebefa4c23076b622b232ed8a1bcf8ff549deacec'
            installer_sha256 = '479d540cd2d1cea09d8b8cb63266578db9c58ed174546121a43a4bb4084454be'
            official_sha256 = '4b589e0b310133998f26f0c12c3ee8d367360be1771e0e96c951322cea6c7ad0'
            projected_sha256 = '544fc4344a9cf3cd73ab97173a41b97ce1c8e16cdbd3eb8c4389a4b2bcbe344c'
            probe_source = 'versions/1.21.0/src/main/java/ca/teamdman/sfm/releaseprobe/ReleaseServerRegistryProbe.java'
            resources = 'versions/1.21.0/resources'
            mod_manifest = 'META-INF/neoforge.mods.toml'
            fixture_source = 'versions/1.21.0/Fixture.ps1'
            java_source = '21'
        }
    }
    '26.1.2' {
        @{
            loader = '26.1.2.72'
            loader_brand = 'NeoForge'
            loader_id = 'neoforge'
            forge_group_path = 'net/neoforged'
            artifact_module = 'neoforge'
            artifact_version = '26.1.2.72'
            launch_version_flag = '--fml.neoForgeVersion'
            fml_group_path = 'net/neoforged/fancymodloader'
            fml_core_artifact = 'loader'
            fml_language_artifact = ''
            fml_library_version = '11.0.13'
            event_bus_path = 'libraries/net/neoforged/bus/8.0.5/bus-8.0.5.jar'
            compile_extra_paths = @('libraries/com/mojang/datafixerupper/9.0.19/datafixerupper-9.0.19.jar')
            compile_jar_name = 'raw.jar'
            compile_jar_kind = 'neoform_joined_26.1.2-1'
            compile_jar_sha256 = '9f37007fdd16c8218110edf1fa91dab10038d62bc62f0c99570f56503f4ac618'
            installer_sha1 = '011b325ef657569ea044d33d250874c7bfa283ee'
            installer_sha256 = '249799b185eb7c9fadbe91f533f1f25f6a59c2d7d545430f587c434e5e55902b'
            official_sha256 = 'cace8809600cea007dbe5c73dc04c2f780375547ec0717a1ec8c25d991140bf1'
            projected_sha256 = '9b2ff101e1158f42bf9218177c1f063375ce58254ff0de629db4dc52ef3c15b6'
            probe_source = 'versions/26.1.2/src/main/java/ca/teamdman/sfm/releaseprobe/ReleaseServerRegistryProbe.java'
            resources = 'versions/26.1.2/resources'
            mod_manifest = 'META-INF/neoforge.mods.toml'
            fixture_source = 'versions/1.21.0/Fixture.ps1'
            java_source = '25'
        }
    }
}
$loaderVersion = $version.loader
$minecraftVersion = if ($version.ContainsKey('minecraft')) { $version.minecraft } else { $target }
if (-not $version.ContainsKey('artifact_module')) { $version.artifact_module = 'forge' }
if (-not $version.ContainsKey('artifact_version')) { $version.artifact_version = "$target-$loaderVersion" }
if (-not $version.ContainsKey('launch_version_flag')) { $version.launch_version_flag = '--fml.forgeVersion' }
if (-not $version.ContainsKey('event_bus_path')) {
    $version.event_bus_path = "libraries/net/minecraftforge/eventbus/$($version.event_bus)/eventbus-$($version.event_bus).jar"
}
if (-not $version.ContainsKey('compile_extra_paths')) { $version.compile_extra_paths = @() }
if (-not $version.ContainsKey('compile_jar_name')) {
    $version.compile_jar_name = "forge-$($version.artifact_version)-srg.jar"
}
if (-not $version.ContainsKey('compile_jar_kind')) { $version.compile_jar_kind = 'forgegradle_srg' }
if (-not $version.ContainsKey('compile_jar_sha256')) { $version.compile_jar_sha256 = $version.srg_sha256 }
if (-not $version.ContainsKey('probe_source')) {
    $version.probe_source = 'src/main/java/ca/teamdman/sfm/releaseprobe/ReleaseServerRegistryProbe.java'
}
if (-not $version.ContainsKey('mod_manifest')) { $version.mod_manifest = 'META-INF/mods.toml' }
if (-not $version.ContainsKey('java_source')) { $version.java_source = '17' }
$loaderIdentity = "$($version.loader_id)-$loaderVersion"
$expectedInstallerSha1 = $version.installer_sha1
$expectedInstallerSha256 = $version.installer_sha256
$diskErrorPattern = '(?i)no space left|not enough space|insufficient disk|disk[ -]full|there is not enough space|ENOSPC'
$worldName = 'sfm-release-witness-world'
$probeRoot = $PSScriptRoot
$probeSource = Join-Path $probeRoot $version.probe_source
$probeResources = Join-Path $probeRoot $version.resources
$probeManifest = Join-Path $probeResources $version.mod_manifest
$fixture = $null
if ($version.ContainsKey('fixture_source')) {
    $fixtureScript = Join-Path $probeRoot $version.fixture_source
    if (-not [IO.File]::Exists($fixtureScript)) { throw 'Version-specific fixture script is missing' }
    . $fixtureScript
    $fixture = Get-ReleaseServerFixture1210
    if ($fixture.queries.Count -ne 6 -or -not $fixture.disk_nbt -or -not $fixture.facade_nbt -or
        -not $fixture.expected_derived_name -or -not $fixture.expected_warnings) {
        throw 'Version-specific selected-save fixture is incomplete'
    }
}

function Assert-File([string] $Path) {
    if (-not [IO.File]::Exists($Path)) { throw "Missing required input file: $Path" }
    return [IO.Path]::GetFullPath($Path)
}

function Assert-Hash([string] $Path, [string] $Algorithm, [string] $Expected) {
    if ($Expected -notmatch '^[0-9a-fA-F]{40}$' -and $Expected -notmatch '^[0-9a-fA-F]{64}$') {
        throw "Invalid expected $Algorithm digest"
    }
    $actual = (Get-FileHash -LiteralPath $Path -Algorithm $Algorithm).Hash.ToLowerInvariant()
    if ($actual -cne $Expected.ToLowerInvariant()) {
        throw "$Algorithm mismatch for an input file: expected=$Expected actual=$actual"
    }
    return $actual
}

function Get-Sha256([string] $Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Test-Within([string] $Child, [string] $Parent) {
    $parentPath = $Parent.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    return $Child.Equals($Parent, [StringComparison]::OrdinalIgnoreCase) -or
        $Child.StartsWith($parentPath, [StringComparison]::OrdinalIgnoreCase)
}

function Assert-NoDiskError([string] $Text) {
    if ($Text -match $diskErrorPattern) {
        throw "DISK_SPACE_ERROR: $($Matches[0]); stop without cleanup or retry."
    }
}

function Read-IfExists([string] $Path) {
    if (-not [IO.File]::Exists($Path)) { return '' }
    $share = [IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete
    $stream = [IO.FileStream]::new($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, $share)
    try {
        $reader = [IO.StreamReader]::new($stream, [Text.Encoding]::UTF8, $true, 4096, $true)
        try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally { $stream.Dispose() }
}

function Wait-ForProcessExit($Process, [int] $TimeoutSeconds, [string] $LogPath) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while (-not $Process.HasExited -and [DateTime]::UtcNow -lt $deadline) {
        Start-Sleep -Seconds 1
        $Process.Refresh()
        Assert-NoDiskError (Read-IfExists $LogPath)
    }
    if (-not $Process.HasExited) {
        Stop-Process -Id $Process.Id -Force
        throw "Owned process $($Process.Id) exceeded $TimeoutSeconds seconds; scratch logs retained"
    }
    Assert-NoDiskError (Read-IfExists $LogPath)
}

function Wait-ForLog([string] $LogPath, $Process, [string] $Pattern,
                     [int] $FromCharacter, [int] $TimeoutSeconds) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        $wholeLog = Read-IfExists $LogPath
        Assert-NoDiskError $wholeLog
        if ($wholeLog.Length -ge $FromCharacter) {
            $newText = $wholeLog.Substring($FromCharacter)
            $match = [regex]::Match($newText, $Pattern, [Text.RegularExpressions.RegexOptions]::Multiline)
            if ($match.Success) { return $match }
            if ($newText -match '(?i)Unknown or incomplete command|Found no elements matching|Failed to execute command') {
                throw "Server command failed: $($Matches[0]); see retained log"
            }
        }
        $Process.Refresh()
        if ($Process.HasExited) { throw "Server exited before expected log response: $Pattern" }
        Start-Sleep -Milliseconds 500
    }
    throw "Timed out waiting for server log response: $Pattern"
}

function Send-ServerCommand($Process, [string] $LogPath, [string] $TranscriptPath,
                            [string] $Command, [string] $ResponsePattern) {
    $before = (Read-IfExists $LogPath).Length
    [IO.File]::AppendAllText($TranscriptPath, $Command + "`n")
    $Process.StandardInput.WriteLine($Command)
    $Process.StandardInput.Flush()
    return Wait-ForLog $LogPath $Process $ResponsePattern $before $CommandTimeoutSeconds
}

function Get-WorldFingerprint([string] $WorldDirectory) {
    $entries = [Collections.Generic.List[string]]::new()
    Get-ChildItem -LiteralPath $WorldDirectory -Recurse -File | ForEach-Object {
        $relative = [IO.Path]::GetRelativePath($WorldDirectory, $_.FullName).Replace('\', '/')
        $entries.Add($relative + "`t" + (Get-Sha256 $_.FullName))
    }
    $entries.Sort([StringComparer]::Ordinal)
    $bytes = [Text.Encoding]::UTF8.GetBytes(($entries -join "`n") + "`n")
    return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
}

function New-BootDirectory([string] $Name, [string] $SfmInput, [string] $ExpectedHash,
                           [string] $WorldSource = '') {
    $boot = Join-Path $run $Name
    if ([IO.Directory]::Exists($boot)) { throw "Boot root already exists: $Name" }
    [IO.Directory]::CreateDirectory($boot) | Out-Null
    [IO.Directory]::CreateDirectory((Join-Path $boot 'mods')) | Out-Null
    [IO.File]::WriteAllText((Join-Path $boot 'eula.txt'), "eula=true`n", [Text.UTF8Encoding]::new($false))
    $properties = @(
        "level-name=$worldName"
        'level-type=minecraft\:flat'
        'online-mode=false'
        'server-port=0'
        'enable-rcon=false'
        'enable-query=false'
        'spawn-protection=0'
        'max-players=1'
        'motd=SFM isolated release witness'
    ) -join "`n"
    [IO.File]::WriteAllText((Join-Path $boot 'server.properties'), $properties + "`n", [Text.UTF8Encoding]::new($false))
    Copy-Item -LiteralPath $SfmInput -Destination (Join-Path $boot 'mods/sfm.jar')
    Copy-Item -LiteralPath $probeJar -Destination (Join-Path $boot 'mods/sfmreleaseprobe.jar')
    Assert-Hash (Join-Path $boot 'mods/sfm.jar') SHA256 $ExpectedHash | Out-Null
    Assert-Hash (Join-Path $boot 'mods/sfmreleaseprobe.jar') SHA256 $probeHash | Out-Null
    if ($WorldSource) {
        Copy-Item -LiteralPath $WorldSource -Destination (Join-Path $boot $worldName) -Recurse
        $sourceFingerprint = Get-WorldFingerprint $WorldSource
        $copyFingerprint = Get-WorldFingerprint (Join-Path $boot $worldName)
        if ($copyFingerprint -cne $sourceFingerprint) { throw "World copy fingerprint mismatch for $Name" }
    }
    return $boot
}

function Read-SelectedValues($Process, [string] $LogPath, [string] $TranscriptPath) {
    $queries = if ($fixture) { $fixture.queries } else { [ordered]@{
        disk_program = 'data get block 0 120 0 Items[0].tag."sfm:program"'
        derived_name = 'data get block 0 120 0 Items[0].tag."sfm:name"'
        labels       = 'data get block 0 120 0 Items[0].tag."sfm:labels"'
        errors       = 'data get block 0 120 0 Items[0].tag."sfm:errors"'
        warnings     = 'data get block 0 120 0 Items[0].tag."sfm:warnings"'
        facade       = 'data get block 2 120 0 "sfm:facade"'
    } }
    $values = [ordered]@{}
    foreach ($key in $queries.Keys) {
        $match = Send-ServerCommand $Process $LogPath $TranscriptPath $queries[$key] `
            '(?m)^.*has the following block data: (.+?)\r?$'
        $values[$key] = $match.Groups[1].Value
    }
    $expectedDerivedName = if ($fixture) { $fixture.expected_derived_name } else { '"compat-probe"' }
    $expectedWarnings = if ($fixture) { $fixture.expected_warnings } else { '[]' }
    if ($values.disk_program -notmatch 'compat-probe' -or $values.derived_name -cne $expectedDerivedName -or
        $values.labels -notmatch 'legacy' -or $values.errors -cne '[]' -or $values.warnings -cne $expectedWarnings -or
        $values.facade -notmatch 'minecraft:stone' -or $values.facade -notmatch 'STRETCH' -or
        $values.facade -notmatch 'north') {
        throw 'Selected six-value fixture did not have the expected non-vacuous shape'
    }
    return $values
}

function Invoke-ServerBoot([string] $Role, [string] $BootDirectory, [string] $SfmHash,
                           [bool] $CreateSeed) {
    $log = Join-Path $BootDirectory 'logs/latest.log'
    $transcript = Join-Path $BootDirectory 'commands.txt'
    $snapshot = Join-Path $BootDirectory 'registry-snapshot.json'
    $stdout = Join-Path $BootDirectory 'java.stdout.log'
    $stderr = Join-Path $BootDirectory 'java.stderr.log'
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $java
    $start.WorkingDirectory = $BootDirectory
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.ArgumentList.Add('-Xms512m')
    $start.ArgumentList.Add('-Xmx2g')
    $start.ArgumentList.Add('-Dfile.encoding=UTF-8')
    $start.ArgumentList.Add('-Dsfm.releaseWitness.registrySnapshot=' + $snapshot.Replace('\', '/'))
    $start.ArgumentList.Add('-Dsfm.releaseWitness.target=' + $target)
    $start.ArgumentList.Add('-Dsfm.releaseWitness.loader=' + $loaderIdentity)
    $start.ArgumentList.Add('@' + $launchArgs.Replace('\', '/'))
    $start.ArgumentList.Add('nogui')
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    try {
        if (-not $process.Start()) { throw "Could not start $Role server" }
        Write-Host "SFM_WITNESS_BOOT role=$Role pid=$($process.Id)"
        $outTask = $process.StandardOutput.ReadToEndAsync()
        $errTask = $process.StandardError.ReadToEndAsync()
        Wait-ForLog $log $process 'Done \([0-9.]+s\)!' 0 $StartupTimeoutSeconds | Out-Null
        $startupLog = Read-IfExists $log
        $startupIdentity = $version.loader_brand + ' mod loading, version ' + [regex]::Escape($loaderVersion) +
            ', for MC ' + [regex]::Escape($minecraftVersion)
        if ($startupLog -notmatch $startupIdentity) {
            throw "$Role did not log exact $($version.loader_brand) $loaderVersion and Minecraft $minecraftVersion"
        }
        $snapshotDeadline = [DateTime]::UtcNow.AddSeconds(30)
        while (-not [IO.File]::Exists($snapshot) -and [DateTime]::UtcNow -lt $snapshotDeadline) {
            $currentLog = Read-IfExists $log
            Assert-NoDiskError $currentLog
            if ($currentLog.Contains('SFM release registry snapshot failed')) {
                throw "$Role registry probe failed after Done; see retained server log"
            }
            $process.Refresh()
            if ($process.HasExited) { break }
            Start-Sleep -Milliseconds 250
        }
        if (-not [IO.File]::Exists($snapshot)) { throw "$Role probe did not emit a registry snapshot" }
        $snapshotText = [IO.File]::ReadAllText($snapshot)
        $parsed = $snapshotText | ConvertFrom-Json
        if ($parsed.schema -ne 'sfm:release_registry_snapshot@1' -or $parsed.target -ne $target -or
            $parsed.loader -ne $loaderIdentity) { throw "$Role registry snapshot identity mismatch" }

        if ($CreateSeed) {
            Send-ServerCommand $process $log $transcript 'forceload add 0 0' `
                '(?i)marked chunk|forceload|force loaded' | Out-Null
            Send-ServerCommand $process $log $transcript 'setblock 0 120 0 sfm:manager' `
                'Changed the block at 0, 120, 0' | Out-Null
            $diskNbt = if ($fixture) { $fixture.disk_nbt } else { '{Items:[{Slot:0b,id:"sfm:disk",Count:1b,tag:{"sfm:program":"NAME \"compat-probe\" EVERY 20 TICKS DO END","sfm:labels":{legacy:[L;120L]},"sfm:errors":[],"sfm:warnings":[]}}]}' }
            Send-ServerCommand $process $log $transcript ("data merge block 0 120 0 " + $diskNbt) `
                'Modified block data of 0, 120, 0' | Out-Null
            Send-ServerCommand $process $log $transcript 'setblock 2 120 0 sfm:cable_facade' `
                'Changed the block at 2, 120, 0' | Out-Null
            $facadeNbt = if ($fixture) { $fixture.facade_nbt } else { '{"sfm:facade":{block_state:{Name:"minecraft:stone"},texture_mode:"STRETCH",direction:"north"}}' }
            Send-ServerCommand $process $log $transcript ("data merge block 2 120 0 " + $facadeNbt) `
                'Modified block data of 2, 120, 0' | Out-Null
        }
        $values = if ($CreateSeed) { $null } else { Read-SelectedValues $process $log $transcript }
        Send-ServerCommand $process $log $transcript 'save-all flush' 'Saved the game' | Out-Null
        [IO.File]::AppendAllText($transcript, "stop`n")
        $process.StandardInput.WriteLine('stop')
        $process.StandardInput.Flush()
        Wait-ForProcessExit $process 90 $log
        $outTask.Wait()
        $errTask.Wait()
        [IO.File]::WriteAllText($stdout, $outTask.Result)
        [IO.File]::WriteAllText($stderr, $errTask.Result)
        Assert-NoDiskError ($outTask.Result + $errTask.Result)
        if ($process.ExitCode -ne 0) { throw "$Role server exited $($process.ExitCode)" }
        if (-not [IO.File]::Exists((Join-Path $BootDirectory "$worldName/level.dat"))) {
            throw "$Role did not save a level.dat"
        }
        return [ordered]@{
            role = $Role
            sfm_jar_sha256 = $SfmHash
            exit_code = $process.ExitCode
            startup_done = $true
            exact_loader = $true
            saved = $true
            probe_sha256 = $probeHash
            registry_snapshot_sha256 = Get-Sha256 $snapshot
            registry_snapshot = $parsed.registries
            level_dat_sha256 = Get-Sha256 (Join-Path $BootDirectory "$worldName/level.dat")
            world_fingerprint_sha256 = Get-WorldFingerprint (Join-Path $BootDirectory $worldName)
            selected_values = $values
        }
    } finally {
        $process.Refresh()
        if (-not $process.HasExited) {
            Stop-Process -Id $process.Id -Force
            Write-Host "SFM_WITNESS_STOPPED_OWNED_PID role=$Role pid=$($process.Id)"
        }
        $process.Dispose()
    }
}

$official = Assert-File $OfficialJar
$projected = Assert-File $ProjectedJar
$installer = Assert-File $ForgeInstaller
$compileJar = Assert-File $ForgeSrgJar
$launcher = if ($LauncherCacheRoot) { [IO.Path]::GetFullPath($LauncherCacheRoot).TrimEnd('\', '/') } else { '' }
$java = Assert-File (Join-Path $JavaHome 'bin/java.exe')
$javac = Assert-File (Join-Path $JavaHome 'bin/javac.exe')
$jarTool = Assert-File (Join-Path $JavaHome 'bin/jar.exe')
$run = [IO.Path]::GetFullPath($RunRoot).TrimEnd('\', '/')
$reusedInstall = if ($InstalledForgeRoot) { [IO.Path]::GetFullPath($InstalledForgeRoot).TrimEnd('\', '/') } else { '' }
if ([IO.Directory]::Exists($run) -or [IO.File]::Exists($run)) {
    throw 'RunRoot must be a new, absent directory'
}
$repoRoot = [IO.Path]::GetFullPath((Join-Path $probeRoot '../..'))
if (Test-Within $run $repoRoot) { throw 'RunRoot must be outside the repository checkout' }
$ancestor = [IO.Path]::GetDirectoryName($run)
while ($ancestor) {
    if ([IO.File]::Exists((Join-Path $ancestor 'server.properties')) -or
        [IO.File]::Exists((Join-Path $ancestor 'level.dat'))) {
        throw 'RunRoot must not be inside an existing game or world directory'
    }
    $nextAncestor = [IO.Path]::GetDirectoryName($ancestor)
    if (-not $nextAncestor -or $nextAncestor -eq $ancestor) { break }
    $ancestor = $nextAncestor
}
foreach ($protected in @($launcher, $JavaHome, [IO.Path]::GetDirectoryName($official),
                          [IO.Path]::GetDirectoryName($projected), [IO.Path]::GetDirectoryName($installer),
                          $probeRoot, $reusedInstall)) {
    if (-not $protected) { continue }
    if (Test-Within $run ([IO.Path]::GetFullPath($protected))) {
        throw 'RunRoot must not be inside any input or source directory'
    }
}
Assert-Hash $official SHA256 $OfficialSha256 | Out-Null
Assert-Hash $projected SHA256 $ProjectedSha256 | Out-Null
if ($OfficialSha256.ToLowerInvariant() -cne $version.official_sha256 -or
    $ProjectedSha256.ToLowerInvariant() -cne $version.projected_sha256) {
    throw 'SFM JAR digests do not match the pinned official/checked-in pair for this target'
}
Assert-Hash $installer SHA1 $expectedInstallerSha1 | Out-Null
Assert-Hash $installer SHA256 $expectedInstallerSha256 | Out-Null
if ((Get-Item -LiteralPath $compileJar).Name -cne $version.compile_jar_name) {
    throw "Probe compile input is not the exact $($version.compile_jar_kind) JAR"
}
Assert-Hash $compileJar SHA256 $version.compile_jar_sha256 | Out-Null
$expectedJavaSha256 = if ($target -eq '26.1.2') {
    '60c42e14617d3e23877afea74a651109a3bebaebd5963028c8d26e0d6509dd65'
} elseif ($target -in @('1.21.0', '1.21.1')) {
    'cd23f1d9b3ba8f99370503e3f13b057c3ca3b0cb32b926f1a39637a34e11ebc1'
} else {
    '186d651179d34ce21d857597bb88a7b1e244973e64f3a9bec1e9daaffd919e31'
}
Assert-Hash $java SHA256 $expectedJavaSha256 | Out-Null
Assert-File $probeSource | Out-Null
Assert-File $probeManifest | Out-Null
Assert-File (Join-Path $probeResources 'pack.mcmeta') | Out-Null
$javaVersionOutput = (& $java -version 2>&1) -join "`n"
if ($target -eq '26.1.2') {
    $javaRelease = [IO.File]::ReadAllText((Assert-File (Join-Path $JavaHome 'release')))
    if ($LASTEXITCODE -ne 0 -or $javaVersionOutput -notmatch 'version "25\.0\.3"' -or
        $javaRelease -notmatch 'IMPLEMENTOR_VERSION="JBRSDK-25\.0\.3\+9-480\.61-nomod"') {
        throw "Expected release-pinned JBRSDK 25.0.3 b480.61; observed: $javaVersionOutput"
    }
} elseif ($target -in @('1.21.0', '1.21.1')) {
    $javaRelease = [IO.File]::ReadAllText((Assert-File (Join-Path $JavaHome 'release')))
    if ($LASTEXITCODE -ne 0 -or $javaVersionOutput -notmatch 'version "21\.0\.11"' -or
        $javaRelease -notmatch 'IMPLEMENTOR_VERSION="JBRSDK-21\.0\.11\+1-1163\.116-nomod"') {
        throw "Expected release-pinned JBRSDK 21.0.11 b1163.116; observed: $javaVersionOutput"
    }
} elseif ($LASTEXITCODE -ne 0 -or $javaVersionOutput -notmatch 'version "17\.0\.14"' -or
        $javaVersionOutput -notmatch 'JBR-17\.0\.14\+1-1367\.22') {
    throw "Expected release-pinned JBRSDK 17.0.14 b1367.22; observed: $javaVersionOutput"
}
$javacVersionOutput = (& $javac -version 2>&1) -join "`n"
$expectedJavacVersion = if ($target -eq '26.1.2') { '25.0.3' } elseif ($target -in @('1.21.0', '1.21.1')) { '21.0.11' } else { '17.0.14' }
if ($LASTEXITCODE -ne 0 -or $javacVersionOutput -notmatch ('javac ' + [regex]::Escape($expectedJavacVersion))) {
    throw "Expected release-pinned javac; observed: $javacVersionOutput"
}

# Input hashes and boundaries are checked before creating the scratch root.
[IO.Directory]::CreateDirectory($run) | Out-Null

$install = if ($reusedInstall) { $reusedInstall } else { Join-Path $run "$($version.artifact_module)-install" }
if ($reusedInstall) {
    if (-not [IO.Directory]::Exists($reusedInstall)) { throw 'InstalledForgeRoot is missing' }
    Write-Host 'SFM_WITNESS_REUSE_EXACT_INSTALL read_only=true'
} else {
    [IO.Directory]::CreateDirectory($install) | Out-Null
    $installStdout = Join-Path $run 'installer.stdout.log'
    $installStderr = Join-Path $run 'installer.stderr.log'
    $installProcess = Start-Process -FilePath $java -ArgumentList @('-jar', ('"' + $installer + '"'),
        '--installServer', ('"' + $install + '"')) -WorkingDirectory $run -WindowStyle Hidden `
        -RedirectStandardOutput $installStdout -RedirectStandardError $installStderr -PassThru
    try {
        $deadline = [DateTime]::UtcNow.AddMinutes(15)
        while (-not $installProcess.HasExited -and [DateTime]::UtcNow -lt $deadline) {
            Start-Sleep -Seconds 2
            $installProcess.Refresh()
            Assert-NoDiskError ((Read-IfExists $installStdout) + (Read-IfExists $installStderr))
        }
        if (-not $installProcess.HasExited) {
            Stop-Process -Id $installProcess.Id -Force
            throw 'Exact loader installer exceeded 15 minutes; scratch retained'
        }
        Assert-NoDiskError ((Read-IfExists $installStdout) + (Read-IfExists $installStderr))
        if ($installProcess.ExitCode -ne 0) {
            throw "Exact loader installer exited $($installProcess.ExitCode); scratch logs retained"
        }
    } finally {
        $installProcess.Refresh()
        if (-not $installProcess.HasExited) { Stop-Process -Id $installProcess.Id -Force }
    }
}
$classpathRoot = if ($launcher) { $launcher } else { $install }
$fmlRoot = Join-Path $classpathRoot "libraries/$($version.fml_group_path)"
$fmlVersion = $version.fml_library_version
$coreArtifact = $version.fml_core_artifact
$languageArtifact = $version.fml_language_artifact
$fml = Assert-File (Join-Path $fmlRoot "$coreArtifact/$fmlVersion/$coreArtifact-$fmlVersion.jar")
$language = if ($languageArtifact) {
    Assert-File (Join-Path $fmlRoot "$languageArtifact/$fmlVersion/$languageArtifact-$fmlVersion.jar")
} else { '' }
$eventBus = Assert-File (Join-Path $classpathRoot $version.event_bus_path)
$compileExtras = [Collections.Generic.List[string]]::new()
foreach ($relative in $version.compile_extra_paths) {
    $compileExtras.Add((Assert-File (Join-Path $classpathRoot $relative)))
}
$probeClasses = Join-Path $run 'probe-classes'
[IO.Directory]::CreateDirectory($probeClasses) | Out-Null
$compileClasspath = (@($compileJar, $fml, $language, $eventBus) + $compileExtras.ToArray() |
    Where-Object { $_ }) -join ';'
& $javac -proc:none -source $version.java_source -target $version.java_source -classpath $compileClasspath -d $probeClasses $probeSource
if ($LASTEXITCODE -ne 0) { throw "Test-only registry probe javac failed: $LASTEXITCODE" }
$probeJar = Join-Path $run 'sfmreleaseprobe.jar'
& $jarTool --create --file $probeJar -C $probeClasses . -C $probeResources .
if ($LASTEXITCODE -ne 0) { throw "Test-only registry probe jar failed: $LASTEXITCODE" }
$probeHash = Get-Sha256 $probeJar

$winArgs = Assert-File (Join-Path $install "libraries/$($version.forge_group_path)/$($version.artifact_module)/$($version.artifact_version)/win_args.txt")
$originalArgs = [IO.File]::ReadAllText($winArgs)
if ($originalArgs -notmatch ([regex]::Escape($version.launch_version_flag) + '\s+' + [regex]::Escape($loaderVersion)) -or
    $originalArgs -notmatch ('--fml\.mcVersion\s+' + [regex]::Escape($minecraftVersion))) {
    throw 'Installed server arguments have the wrong loader or Minecraft version'
}
$libraries = (Join-Path $install 'libraries').Replace('\', '/')
$argsLines = [Collections.Generic.List[string]]::new()
foreach ($line in ([IO.File]::ReadAllLines($winArgs))) {
    if ($line.StartsWith('-p ')) {
        $argsLines.Add('-p')
        $argsLines.Add('"' + $line.Substring(3).Replace('libraries/', $libraries + '/') + '"')
    } elseif ($line.StartsWith('-DlibraryDirectory=libraries')) {
        $argsLines.Add('"-DlibraryDirectory=' + $libraries + '"')
    } elseif ($line.Contains('libraries/')) {
        $argsLines.Add('"' + $line.Replace('libraries/', $libraries + '/') + '"')
    } else {
        $argsLines.Add($line)
    }
}
$launchArgs = Join-Path $run "$($version.artifact_module)-launch.args"
[IO.File]::WriteAllLines($launchArgs, $argsLines, [Text.UTF8Encoding]::new($false))

$seed = New-BootDirectory 'official-seed' $official $OfficialSha256
$seedResult = Invoke-ServerBoot 'official-seed' $seed $OfficialSha256.ToLowerInvariant() $true
$seedWorld = Join-Path $seed $worldName
$control = New-BootDirectory 'official-control' $official $OfficialSha256 $seedWorld
$candidate = New-BootDirectory 'projected-candidate' $projected $ProjectedSha256 $seedWorld
$seedFingerprint = $seedResult.world_fingerprint_sha256
if ((Get-WorldFingerprint (Join-Path $control $worldName)) -cne $seedFingerprint -or
    (Get-WorldFingerprint (Join-Path $candidate $worldName)) -cne $seedFingerprint) {
    throw 'Official control and projected candidate did not start from byte-identical seed worlds'
}
$controlResult = Invoke-ServerBoot 'official-control' $control $OfficialSha256.ToLowerInvariant() $false
$candidateResult = Invoke-ServerBoot 'projected-candidate' $candidate $ProjectedSha256.ToLowerInvariant() $false
$reverse = New-BootDirectory 'official-reverse' $official $OfficialSha256 (Join-Path $candidate $worldName)
if ((Get-WorldFingerprint (Join-Path $reverse $worldName)) -cne $candidateResult.world_fingerprint_sha256) {
    throw 'Official reverse boot did not start from the candidate-saved world'
}
$reverseResult = Invoke-ServerBoot 'official-reverse' $reverse $OfficialSha256.ToLowerInvariant() $false

$registrySeedEqual = $seedResult.registry_snapshot_sha256 -ceq $controlResult.registry_snapshot_sha256
$registryEqual = $controlResult.registry_snapshot_sha256 -ceq $candidateResult.registry_snapshot_sha256
$registryReverseEqual = $controlResult.registry_snapshot_sha256 -ceq $reverseResult.registry_snapshot_sha256
$valuesEqual = $true
$reverseValuesEqual = $true
foreach ($key in $controlResult.selected_values.Keys) {
    if (-not [string]::Equals($controlResult.selected_values[$key], $candidateResult.selected_values[$key],
                             [StringComparison]::Ordinal)) { $valuesEqual = $false }
    if (-not [string]::Equals($controlResult.selected_values[$key], $reverseResult.selected_values[$key],
                             [StringComparison]::Ordinal)) { $reverseValuesEqual = $false }
}
$registryDiff = [ordered]@{}
$left = $controlResult.registry_snapshot
$right = $candidateResult.registry_snapshot
foreach ($registryId in (@($left.PSObject.Properties.Name) + @($right.PSObject.Properties.Name) | Sort-Object -Unique)) {
    $officialIds = @($left.$registryId)
    $candidateIds = @($right.$registryId)
    $onlyOfficial = @($officialIds | Where-Object { $candidateIds -cnotcontains $_ })
    $onlyCandidate = @($candidateIds | Where-Object { $officialIds -cnotcontains $_ })
    if ($onlyOfficial.Count -gt 0 -or $onlyCandidate.Count -gt 0) {
        $registryDiff[$registryId] = [ordered]@{ only_official = $onlyOfficial; only_projected = $onlyCandidate }
    }
}
$installMode = if ($reusedInstall) { 'verified_exact_existing_install_read_only' } else { 'scratch_installer' }
$report = [ordered]@{
    schema = 'sfm:release_server_witness@1'
    status = if ($registrySeedEqual -and $registryEqual -and $registryReverseEqual -and
                 $valuesEqual -and $reverseValuesEqual) { 'PASS' } else { 'FAIL' }
    runner_script_sha256 = Get-Sha256 $PSCommandPath
    probe_source_sha256 = Get-Sha256 $probeSource
    probe_mods_toml_sha256 = Get-Sha256 $probeManifest
    probe_metadata_file = $version.mod_manifest.Replace('\', '/')
    probe_pack_mcmeta_sha256 = Get-Sha256 (Join-Path $probeResources 'pack.mcmeta')
    target = $target
    loader = $loaderIdentity
    loader_install_mode = $installMode
    java_version = $javaVersionOutput
    java_exe_sha256 = Get-Sha256 $java
    official_jar_sha256 = $OfficialSha256.ToLowerInvariant()
    projected_jar_sha256 = $ProjectedSha256.ToLowerInvariant()
    loader_installer_sha1 = $expectedInstallerSha1
    loader_installer_sha256 = $expectedInstallerSha256
    loader_compile_jar_kind = $version.compile_jar_kind
    loader_compile_jar_sha256 = $version.compile_jar_sha256
    loader_launch_args_sha256 = Get-Sha256 $launchArgs
    probe_jar_sha256 = $probeHash
    seed_level_dat_sha256 = $seedResult.level_dat_sha256
    seed_world_fingerprint_sha256 = $seedFingerprint
    registry_id_diff = $registryDiff
    registry_seed_equal = $registrySeedEqual
    registry_ids_equal = $registryEqual
    registry_reverse_equal = $registryReverseEqual
    selected_values_equal = $valuesEqual
    official_reverse_equal = $reverseValuesEqual
    boots = @($seedResult, $controlResult, $candidateResult, $reverseResult)
    scope = 'One test-only auxiliary mod and six selected SFM save values; no transfer, client, or general gameplay parity claim.'
}
if ($fixture) { $report['selected_fixture_source_sha256'] = Get-Sha256 $fixtureScript }
if ($version.compile_jar_kind -eq 'forgegradle_srg') {
    # Retain the original field names for previous ForgeGradle/SRG targets.
    $report['forge_install_mode'] = $installMode
    $report['forge_installer_sha1'] = $expectedInstallerSha1
    $report['forge_installer_sha256'] = $expectedInstallerSha256
    $report['forge_srg_sha256'] = $version.compile_jar_sha256
    $report['forge_launch_args_sha256'] = $report.loader_launch_args_sha256
}
$reportPath = Join-Path $run 'result.json'
[IO.File]::WriteAllText($reportPath, ($report | ConvertTo-Json -Depth 15) + "`n", [Text.UTF8Encoding]::new($false))
Assert-Hash $official SHA256 $OfficialSha256 | Out-Null
Assert-Hash $projected SHA256 $ProjectedSha256 | Out-Null
Write-Host "SFM_RELEASE_SERVER_WITNESS status=$($report.status) report_sha256=$(Get-Sha256 $reportPath)"
if ($report.status -ne 'PASS') { throw 'Official/projected registry or selected-save comparison failed; see result.json' }
