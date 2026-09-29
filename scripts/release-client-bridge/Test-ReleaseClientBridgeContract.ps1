<#
Static, read-only contract check for the test-only client bridge.
It does not compile Java, launch Minecraft, or create a RunRoot.
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Assert-Contains([string] $Text, [string] $Needle, [string] $Description) {
    if (-not $Text.Contains($Needle, [StringComparison]::Ordinal)) {
        throw "Missing $Description"
    }
}

function Assert-Set([string[]] $Actual, [string[]] $Expected, [string] $Description) {
    $a = @($Actual | Sort-Object)
    $e = @($Expected | Sort-Object)
    if (@(Compare-Object -ReferenceObject $e -DifferenceObject $a).Count -ne 0) {
        throw "Unexpected $Description; actual=$($a -join ',')"
    }
}

$bridgeRoot = $PSScriptRoot
$runnerPath = Join-Path $bridgeRoot 'Run-ReleaseClientBridge.ps1'
$runner = Get-Content -LiteralPath $runnerPath -Raw
$parseTokens = $null
$parseErrors = $null
[System.Management.Automation.Language.Parser]::ParseFile(
    $runnerPath, [ref] $parseTokens, [ref] $parseErrors
) | Out-Null
if (@($parseErrors).Count -ne 0) {
    throw "Runner has PowerShell parse errors: $($parseErrors -join '; ')"
}

$command = Get-Command -Name $runnerPath
$captureModes = @($command.Parameters['CaptureMode'].Attributes |
    Where-Object { $_ -is [System.Management.Automation.ValidateSetAttribute] } |
    ForEach-Object { $_.ValidValues })
$targets = @($command.Parameters['MinecraftVersion'].Attributes |
    Where-Object { $_ -is [System.Management.Automation.ValidateSetAttribute] } |
    ForEach-Object { $_.ValidValues })
$expectedTargets = @('1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2',
    '1.20.3', '1.20.4', '1.21.0', '1.21.1', '26.1.2')
Assert-Set $captureModes @('title', 'world', 'network-roundtrip') 'capture-mode validation'
Assert-Set $targets $expectedTargets 'Minecraft target validation'
if ($captureModes -contains 'network-roundtrip-unsupported' -or $targets -contains '1.22.0') {
    throw 'Invalid mode or target was accepted by command metadata'
}

Assert-Contains $runner "Join-Path `$bridgeRoot 'src/1.19.4'" 'shared 1.19.4 adapter route'
Assert-Contains $runner "elseif (`$MinecraftVersion -ne '1.19.2')" '1.20/1.20.1 shared-adapter route'
Assert-Contains $runner 'src/$MinecraftVersion' '1.20.3/1.20.4 intermediate adapter route'
foreach ($adapter in @('1.20.2', '1.21.0', '1.21.1', '26.1.2')) {
    Assert-Contains $runner "src/$adapter" "$adapter adapter route"
}
Assert-Contains $runner "'network-roundtrip' { 'sfm-release-client-network-proof/1' }" 'network result schema'
Assert-Contains $runner "serverResponses -ne 1" 'single server response assertion'
Assert-Contains $runner "CaptureMode -ne 'network-roundtrip'" 'screenshot exclusion for network mode'
Assert-Contains $runner "CaptureMode -eq 'title'" 'title regression gate'
Assert-Contains $runner "CaptureMode -eq 'world'" 'world regression gate'

$adapters = @(
    @{ Name = 'main'; Registry = 'null' },
    @{ Name = '1.19.4'; Registry = 'null' },
    @{ Name = '1.20.2'; Registry = 'null' },
    @{ Name = '1.20.3'; Registry = 'null' },
    @{ Name = '1.20.4'; Registry = 'null' },
    @{ Name = '1.21.0'; Registry = 'minecraft.level.registryAccess()' },
    @{ Name = '1.21.1'; Registry = 'minecraft.level.registryAccess()' },
    @{ Name = '26.1.2'; Registry = 'minecraft.level.registryAccess()' }
)
foreach ($adapter in $adapters) {
    $path = Join-Path $bridgeRoot "src/$($adapter.Name)/java/ca/teamdman/sfm/releaseprobe/ReleaseClientBridge.java"
    $source = Get-Content -LiteralPath $path -Raw
    foreach ($required in @(
        '"title".equals(captureMode)',
        '"world".equals(captureMode)',
        '"network-roundtrip".equals(captureMode)',
        'advanceWorldWitness();',
        'advanceNetworkRoundTrip();',
        'createScratchWorld(minecraft);',
        'ReleaseClientNetworkProbe.requestServerConfigShow();',
        'ReleaseClientNetworkProbe.isConfigResponseScreen(',
        'sfm-release-client-network-proof/1',
        'sfm-release-client-world-proof/1',
        'sfm-release-client-proof/1'
    )) {
        Assert-Contains $source $required "$($adapter.Name) $required"
    }
    Assert-Contains $source "captureCodecFixture($($adapter.Registry))" "$($adapter.Name) codec buffer type"
}

$codec = Get-Content -LiteralPath (Join-Path $bridgeRoot 'src/main/java/ca/teamdman/sfm/releaseprobe/ReleaseClientNetworkProbe.java') -Raw
foreach ($required in @('ServerboundServerConfigRequestPacket', 'ClientboundServerConfigCommandPacket',
    'FriendlyByteBuf', 'RegistryFriendlyByteBuf', 'packet.equals(decoded)', 'readableBytes',
    'responseBytes.length <= requestBytes.length')) {
    Assert-Contains $codec $required "shared codec $required"
}
Write-Host "SFM_RELEASE_CLIENT_BRIDGE_CONTRACT_PASS targets=$($expectedTargets.Count) adapters=$($adapters.Count) modes=$($captureModes.Count)"
