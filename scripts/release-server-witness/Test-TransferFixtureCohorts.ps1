# Test-only preflight: prove that sharing a transfer fixture follows the exact
# pinned release sources, and that every selected target reaches input checking
# without creating a game directory. No Minecraft process is started.
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$runner = Join-Path $PSScriptRoot 'Run-ReleaseServerWitness.ps1'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$tokens = $null
$parseErrors = $null
[System.Management.Automation.Language.Parser]::ParseFile($runner, [ref] $tokens, [ref] $parseErrors) | Out-Null
if ($parseErrors.Count -ne 0) {
    throw "Release server witness has $($parseErrors.Count) PowerShell parser errors"
}

$legacyTargets = @('1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4')
$componentTargets = @('1.21.0', '1.21.1')
$sourceFiles = @(
    'common/item/DiskItem.java',
    'common/label/LabelPositionHolder.java'
)
foreach ($cohort in @(
    @{ name = 'legacy'; targets = $legacyTargets; expected_sha256 = @{
        'common/item/DiskItem.java' = '4378d324b666c4d567010f5966657bad76192d778d2c8b20dd02b593b61a188e'
        'common/label/LabelPositionHolder.java' = '11837e5b678c0e62775a41f16f1203802bbee8665e7dc431d89e76c851e0d359'
    } },
    @{ name = 'components'; targets = $componentTargets; expected_sha256 = @{
        'common/item/DiskItem.java' = '79c5b1ecbda2a865c3772f435d97d8bc4fa4990476f7304558c076fc7fde9f55'
        'common/label/LabelPositionHolder.java' = 'a732a05f997fc3fd881bd84b0c67b009cd9a7903e76e95c7af828b91bbf0b976'
    } }
)) {
    foreach ($relative in $sourceFiles) {
        $expected = $null
        foreach ($target in $cohort.targets) {
            $source = Join-Path $repoRoot "platform/minecraft/release-baselines/4.34.0-$target/overlays/src/main/java/ca/teamdman/sfm/$relative"
            if (-not [IO.File]::Exists($source)) { throw "Missing pinned $($cohort.name) release source for $target" }
            $hash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
            if ($hash -cne $cohort.expected_sha256[$relative]) {
                throw "Pinned $($cohort.name) $relative has an unexpected release source hash for $target"
            }
            if ($null -eq $expected) { $expected = $hash }
            elseif ($hash -cne $expected) { throw "Pinned $($cohort.name) $relative differs for $target" }
        }
    }
}

$missing = Join-Path $PSScriptRoot 'missing-input-for-transfer-fixture-test.jar'
if ([IO.File]::Exists($missing)) { throw 'The deliberately missing test input exists' }
$targets = $legacyTargets + $componentTargets + @('26.1.2')
foreach ($target in $targets) {
    $runRoot = Join-Path ([IO.Path]::GetTempPath()) ("sfm-transfer-preflight-$target-$([Guid]::NewGuid().ToString('N'))")
    try {
        & $runner -Target $target -Mode vanilla-barrel-transfer `
            -OfficialJar $missing -OfficialSha256 ('0' * 64) `
            -ProjectedJar $missing -ProjectedSha256 ('0' * 64) `
            -ForgeInstaller $missing -ForgeSrgJar $missing `
            -JavaHome $missing -RunRoot $runRoot | Out-Null
        throw "Transfer preflight unexpectedly accepted missing inputs for $target"
    } catch {
        if ($_.Exception.Message -cnotmatch '^Missing required input file: ') {
            throw "Transfer preflight failed before exact input checking for ${target}: $($_.Exception.Message)"
        }
    }
    if (Test-Path -LiteralPath $runRoot) { throw "Transfer preflight created a RunRoot for $target" }
}

$unsupportedRoot = Join-Path ([IO.Path]::GetTempPath()) ("sfm-transfer-preflight-1192-$([Guid]::NewGuid().ToString('N'))")
try {
    & $runner -Target 1.19.2 -Mode vanilla-barrel-transfer `
        -OfficialJar $missing -OfficialSha256 ('0' * 64) `
        -ProjectedJar $missing -ProjectedSha256 ('0' * 64) `
        -ForgeInstaller $missing -ForgeSrgJar $missing `
        -JavaHome $missing -RunRoot $unsupportedRoot | Out-Null
    throw 'Unvalidated 1.19.2 transfer mode unexpectedly passed the target gate'
} catch {
    if ($_.Exception.Message -cne 'The opt-in vanilla-barrel transfer fixture is not validated for this exact target') {
        throw "Unvalidated-target gate produced a different result: $($_.Exception.Message)"
    }
}
if (Test-Path -LiteralPath $unsupportedRoot) { throw 'Rejected transfer target created a RunRoot' }

Write-Host "Transfer fixture cohorts: pinned-source equality and $($targets.Count) positive / 1 negative no-write preflights passed"
