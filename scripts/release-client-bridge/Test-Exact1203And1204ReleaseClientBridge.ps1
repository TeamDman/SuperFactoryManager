<#
Read-only exact-input preflight and fail-closed checks for the 1.20.3 and
1.20.4 packaged-client witnesses. No game root or Java process is created.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [ValidateSet('1.20.3', '1.20.4')] [string] $MinecraftVersion,
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
$expected = switch ($MinecraftVersion) {
    '1.20.3' { @{
            Official = 'e4686d536227e0ead220f56606a5527ec8057504cfc77712a1c892d6d14d82b1'
            Projected = '3cd66ed27313c067f3529f94765dfc2a51d36b376bbbf841b0f95c8874389d58'
            Installer = 'd59ba6f0c867ddaafe0247ba7ddf527389897806795b9bd543a2c4313695f9d7'
            Client = 'ac52754a2c75b7beec03f6d2534f5c6d4e8c02b5'
            Loader = 'neoforge-20.3.8-beta'
            Libraries = 94
        } }
    '1.20.4' { @{
            Official = 'b83b43fc8ee7cb7863da5f0d039b026675c2f2c68956f99643c83a9736f7a63b'
            Projected = '3964a8742211e9cc39b44ee61b4402387382c948ba7c17b1d887c013f764813e'
            Installer = '8002077d9454603611b0bb5fd66a107c2dd2a27942ebe52c39db2a9280eaac18'
            Client = 'ec7cfa975d3b76c55088b728d4baa330b5ebeddd'
            Loader = 'neoforge-20.4.231'
            Libraries = 89
        } }
}
if (-not [IO.Directory]::Exists($ScratchParent)) { throw 'ScratchParent must already exist' }

function New-UnusedRunRoot {
    return Join-Path $ScratchParent ('sfm-release-client-exact-preflight-' + [Guid]::NewGuid().ToString('N'))
}

function Invoke-Preflight([string] $SourceJar, [string] $Hash, [string] $RunRoot,
    [string] $Installer = $NeoForgeInstaller, [string] $CompileJar = $JoinedCompileJar,
    [string] $Assets = $AssetRoot) {
    $args = @{
        SfmJar = $SourceJar
        ExpectedSha256 = $Hash
        PrismRoot = $PrismRoot
        JavaHome = $JavaHome
        RunRoot = $RunRoot
        MinecraftVersion = $MinecraftVersion
        NeoForgeInstaller = $Installer
        NeoForgeLibraryRoot = $NeoForgeLibraryRoot
        NeoForgeRuntimeLibraryRoot = $NeoForgeRuntimeLibraryRoot
        AssetRoot = $Assets
        JoinedCompileJar = $CompileJar
        PreflightOnly = $true
    }
    return & $runner @args
}

function Assert-NoOutput([string] $RunRoot) {
    if ([IO.Directory]::Exists($RunRoot) -or [IO.File]::Exists($RunRoot)) {
        throw 'Read-only bridge preflight created a RunRoot'
    }
}

function Assert-Rejected([string] $Name, [scriptblock] $Action, [string] $ExpectedMessage, [string] $RunRoot) {
    $rejected = $false
    try { & $Action | Out-Null } catch {
        if ($_.Exception.Message -notmatch $ExpectedMessage) {
            throw "$Name rejected for the wrong reason: $($_.Exception.Message)"
        }
        $rejected = $true
    }
    if (-not $rejected) { throw "$Name was unexpectedly accepted" }
    Assert-NoOutput $RunRoot
}

foreach ($case in @(
        @{ Jar = $OfficialJar; Hash = $expected.Official },
        @{ Jar = $ProjectedJar; Hash = $expected.Projected })) {
    $run = New-UnusedRunRoot
    $result = Invoke-Preflight $case.Jar $case.Hash $run
    if ($result.schema -ne "sfm-release-client-$($MinecraftVersion.Replace('.', ''))-preflight/1" -or
        $result.minecraft -ne $MinecraftVersion -or $result.loader -ne $expected.Loader -or
        $result.sfm_sha256 -ne $case.Hash -or $result.installer_sha256 -ne $expected.Installer -or
        $result.patched_client_sha1 -ne $expected.Client -or
        $result.asset_index_sha1 -ne '5060d8c8c8f6a52cea32ea9ccd68c9c92e5b74e7' -or
        $result.verified_asset_objects -ne 3787 -or $result.verified_asset_bytes -ne 650581001L -or
        $result.selected_launcher_libraries -ne $expected.Libraries -or
        $result.output_created -ne $false -or $result.client_started -ne $false) {
        throw "Exact $MinecraftVersion read-only preflight returned an incomplete identity"
    }
    Assert-NoOutput $run
}

$badRun = New-UnusedRunRoot
Assert-Rejected 'wrong SFM hash' {
    Invoke-Preflight $OfficialJar ('0' * 64) $badRun
} 'not an exact official/projected' $badRun

$badRun = New-UnusedRunRoot
Assert-Rejected 'wrong installer' {
    Invoke-Preflight $OfficialJar $expected.Official $badRun $OfficialJar
} 'installer SHA-256 mismatch' $badRun

$badRun = New-UnusedRunRoot
Assert-Rejected 'wrong joined compile JAR' {
    Invoke-Preflight $OfficialJar $expected.Official $badRun $NeoForgeInstaller $OfficialJar
} 'joined compile JAR SHA-256 mismatch' $badRun

$badRun = New-UnusedRunRoot
Assert-Rejected 'missing staged assets' {
    Invoke-Preflight $OfficialJar $expected.Official $badRun $NeoForgeInstaller $JoinedCompileJar ''
} 'requires -AssetRoot' $badRun

$badRun = New-UnusedRunRoot
Assert-Rejected 'launcher assets used as stage' {
    Invoke-Preflight $OfficialJar $expected.Official $badRun $NeoForgeInstaller $JoinedCompileJar (Join-Path $PrismRoot 'assets')
} 'separate isolated scratch locations' $badRun

$badRun = Join-Path $PSScriptRoot ('sfm-release-client-refused-' + [Guid]::NewGuid().ToString('N'))
Assert-Rejected 'RunRoot inside checkout' {
    Invoke-Preflight $OfficialJar $expected.Official $badRun
} 'separate isolated scratch locations' $badRun

"Exact $MinecraftVersion release-client bridge preflight and negative gates passed; no RunRoot was created."
