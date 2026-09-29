<#
Read-only, exact-input preflight and fail-closed tests for the 1.20.2 packaged
client bridge. Does not create a game root, launch Java, or alter input caches.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $OfficialJar,
    [Parameter(Mandatory)] [string] $ProjectedJar,
    [Parameter(Mandatory)] [string] $PrismRoot,
    [Parameter(Mandatory)] [string] $JavaHome,
    [Parameter(Mandatory)] [string] $NeoForgeInstaller,
    [Parameter(Mandatory)] [string] $NeoForgeLibraryRoot,
    [Parameter(Mandatory)] [string] $NeoForgeRuntimeLibraryRoot,
    [Parameter(Mandatory)] [string] $AssetRoot,
    [Parameter(Mandatory)] [string] $JoinedCompileJar,
    [Parameter(Mandatory)] [string] $ScratchParent
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$runner = Join-Path $PSScriptRoot 'Run-ReleaseClientBridge.ps1'
$officialHash = 'afce7113bc55d12fe92db02f79b95d702e530fb5fd10accba96edcdbcf0186e2'
$projectedHash = '9b5f00d887edd7a72dd35a7e1d2131a17083932793f06649d362053c34f9808d'
if (-not [IO.Directory]::Exists($ScratchParent)) { throw 'ScratchParent must already exist' }

function New-UnusedRunRoot {
    return Join-Path $ScratchParent ('sfm-release-client-1202-preflight-' + [Guid]::NewGuid().ToString('N'))
}

function Invoke-Preflight([string] $SourceJar, [string] $Hash, [string] $RunRoot) {
    $args = @{
        SfmJar = $SourceJar
        ExpectedSha256 = $Hash
        PrismRoot = $PrismRoot
        JavaHome = $JavaHome
        RunRoot = $RunRoot
        MinecraftVersion = '1.20.2'
        NeoForgeInstaller = $NeoForgeInstaller
        NeoForgeLibraryRoot = $NeoForgeLibraryRoot
        NeoForgeRuntimeLibraryRoot = $NeoForgeRuntimeLibraryRoot
        AssetRoot = $AssetRoot
        JoinedCompileJar = $JoinedCompileJar
        PreflightOnly = $true
    }
    return & $runner @args
}

function Assert-NoOutput([string] $RunRoot) {
    if ([IO.Directory]::Exists($RunRoot) -or [IO.File]::Exists($RunRoot)) {
        throw 'Read-only bridge preflight created a RunRoot'
    }
}

foreach ($case in @(
        @{ Jar = $OfficialJar; Hash = $officialHash },
        @{ Jar = $ProjectedJar; Hash = $projectedHash })) {
    $run = New-UnusedRunRoot
    $result = Invoke-Preflight $case.Jar $case.Hash $run
    if ($result.schema -ne 'sfm-release-client-1202-preflight/1' -or
        $result.minecraft -ne '1.20.2' -or $result.loader -ne 'neoforge-20.2.86' -or
        $result.sfm_sha256 -ne $case.Hash -or $result.verified_asset_objects -ne 3607 -or
        $result.verified_asset_bytes -ne 646330719L -or $result.selected_launcher_libraries -lt 90 -or
        $result.output_created -ne $false -or $result.client_started -ne $false) {
        throw 'Exact 1.20.2 read-only preflight returned an incomplete identity'
    }
    Assert-NoOutput $run
}

$badRun = New-UnusedRunRoot
try {
    Invoke-Preflight $OfficialJar ('0' * 64) $badRun | Out-Null
    throw 'Unexpected acceptance of unapproved SFM JAR hash'
} catch {
    if ($_.Exception.Message -notmatch 'not an exact official/projected') { throw }
}
Assert-NoOutput $badRun

$badRun = New-UnusedRunRoot
try {
    & $runner -SfmJar $OfficialJar -ExpectedSha256 $officialHash -PrismRoot $PrismRoot `
        -JavaHome $JavaHome -RunRoot $badRun -MinecraftVersion '1.20.2' `
        -NeoForgeInstaller $NeoForgeInstaller -NeoForgeLibraryRoot $NeoForgeLibraryRoot `
        -NeoForgeRuntimeLibraryRoot $NeoForgeRuntimeLibraryRoot `
        -JoinedCompileJar $JoinedCompileJar -PreflightOnly | Out-Null
    throw 'Unexpected acceptance without the exact AssetRoot'
} catch {
    if ($_.Exception.Message -notmatch 'requires -AssetRoot') { throw }
}
Assert-NoOutput $badRun

$badRun = New-UnusedRunRoot
try {
    & $runner -SfmJar $OfficialJar -ExpectedSha256 $officialHash -PrismRoot $PrismRoot `
        -JavaHome $JavaHome -RunRoot $badRun -MinecraftVersion '1.20.2' `
        -NeoForgeInstaller $NeoForgeInstaller -NeoForgeLibraryRoot $NeoForgeLibraryRoot `
        -NeoForgeRuntimeLibraryRoot $NeoForgeRuntimeLibraryRoot `
        -AssetRoot (Join-Path $PrismRoot 'assets') -JoinedCompileJar $JoinedCompileJar `
        -PreflightOnly | Out-Null
    throw 'Unexpected acceptance of the launcher asset cache as AssetRoot'
} catch {
    if ($_.Exception.Message -notmatch 'Missing cached input|isolated scratch locations') { throw }
}
Assert-NoOutput $badRun

'Exact 1.20.2 release-client bridge preflight and negative gates passed; no RunRoot was created.'
