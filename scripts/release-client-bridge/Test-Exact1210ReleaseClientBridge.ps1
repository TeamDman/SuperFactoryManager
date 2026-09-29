<#
Read-only, exact-input preflight and fail-closed tests for the Minecraft 1.21
NeoForge 21.0.143 packaged-client witness. No game root or Java process is
created by this test.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $OfficialJar,
    [Parameter(Mandatory)] [string] $ProjectedJar,
    [Parameter(Mandatory)] [string] $PrismRoot,
    [Parameter(Mandatory)] [string] $JavaHome,
    [Parameter(Mandatory)] [string] $JoinedCompileJar,
    [Parameter(Mandatory)] [string] $ScratchParent
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$runner = Join-Path $PSScriptRoot 'Run-ReleaseClientBridge.ps1'
$officialHash = 'c76399b2456daccd88050ef45d36cac7f5d4bf60535adb2dd0d0ffeab19daa5f'
$projectedHash = 'e233d788e54db4078fa90a8324c4132fc3fbfbe103b78ca03003d51d9a0e947b'
if (-not [IO.Directory]::Exists($ScratchParent)) { throw 'ScratchParent must already exist' }

function New-UnusedRunRoot {
    return Join-Path $ScratchParent ('sfm-release-client-1210-preflight-' + [Guid]::NewGuid().ToString('N'))
}

function Invoke-Preflight([string] $SourceJar, [string] $Hash, [string] $RunRoot,
    [string] $CompileJar = $JoinedCompileJar) {
    return & $runner -SfmJar $SourceJar -ExpectedSha256 $Hash -PrismRoot $PrismRoot `
        -JavaHome $JavaHome -RunRoot $RunRoot -MinecraftVersion '1.21.0' `
        -JoinedCompileJar $CompileJar -PreflightOnly
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
    if ($result.schema -ne 'sfm-release-client-1210-preflight/1' -or
        $result.minecraft -ne '1.21.0' -or $result.loader -ne 'neoforge-21.0.143' -or
        $result.sfm_sha256 -ne $case.Hash -or
        $result.installer_sha256 -ne '02e511f97bcfd2985937fc205aa95c93588bf3d9939c6ef18100add57d1e8bec' -or
        $result.patched_client_sha1 -ne 'dbd0d15e8509c8380268210f7d3a39368484c3e0' -or
        $result.asset_index_sha1 -ne '9c535a9da093459a1766fc706674332dc5669423' -or
        $result.verified_asset_objects -ne 3887 -or $result.verified_asset_bytes -ne 820069451L -or
        $result.selected_launcher_libraries -ne 90 -or
        $result.output_created -ne $false -or $result.client_started -ne $false) {
        throw 'Exact 1.21.0 read-only preflight returned an incomplete identity'
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

$badRun = Join-Path $PSScriptRoot ('sfm-release-client-1210-refused-' + [Guid]::NewGuid().ToString('N'))
try {
    Invoke-Preflight $OfficialJar $officialHash $badRun | Out-Null
    throw 'Unexpected acceptance of RunRoot inside the checkout'
} catch {
    if ($_.Exception.Message -notmatch 'RunRoot must be isolated') { throw }
}
Assert-NoOutput $badRun

$badRun = New-UnusedRunRoot
try {
    Invoke-Preflight $OfficialJar $officialHash $badRun $OfficialJar | Out-Null
    throw 'Unexpected acceptance of a non-NeoForm compile JAR'
} catch {
    if ($_.Exception.Message -notmatch 'joined compile JAR SHA-256 mismatch') { throw }
}
Assert-NoOutput $badRun

$badRun = New-UnusedRunRoot
try {
    & $runner -SfmJar $OfficialJar -ExpectedSha256 $officialHash -PrismRoot $PrismRoot `
        -JavaHome $JavaHome -RunRoot $badRun -MinecraftVersion '1.21.0' `
        -JoinedCompileJar $JoinedCompileJar -AssetRoot (Join-Path $PrismRoot 'assets') `
        -PreflightOnly | Out-Null
    throw 'Unexpected acceptance of an alternate AssetRoot'
} catch {
    if ($_.Exception.Message -notmatch 'AssetRoot is only supported for exact 1.20.2') { throw }
}
Assert-NoOutput $badRun

$badRun = New-UnusedRunRoot
try {
    & $runner -SfmJar $OfficialJar -ExpectedSha256 $officialHash -PrismRoot $PrismRoot `
        -JavaHome $JavaHome -RunRoot $badRun -MinecraftVersion '1.21.0' `
        -JoinedCompileJar $JoinedCompileJar -NeoForgeInstaller $OfficialJar `
        -PreflightOnly | Out-Null
    throw 'Unexpected acceptance of a different installer'
} catch {
    if ($_.Exception.Message -notmatch 'installer SHA-256 mismatch') { throw }
}
Assert-NoOutput $badRun

'Exact 1.21.0 release-client bridge preflight and negative gates passed; no RunRoot was created.'
