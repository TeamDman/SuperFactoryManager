[CmdletBinding()]
param(
    [string]$RepoRoot = (Get-Location).Path,
    [string]$Cli = 'sfm-propagate-changes.exe'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$checkoutRoot = (Resolve-Path -LiteralPath $RepoRoot).Path
$coreRoot = Join-Path $checkoutRoot 'platform/minecraft/core-liquid-template'
$sourceRoot = Join-Path $coreRoot 'src'
$metadata = Get-Content -LiteralPath (Join-Path $coreRoot 'project-inputs.json') -Raw | ConvertFrom-Json -AsHashtable
if (@($metadata.project_files.Keys | Where-Object { $_.EndsWith('.java') }).Count) {
    throw 'Java project-file overrides require extending the audit inventory.'
}
$inventory = @(& rg --files $sourceRoot -g '*.java' | Sort-Object)
if ($LASTEXITCODE -ne 0 -or -not $inventory.Count) { throw 'Cannot enumerate Java inventory.' }
$candidates = [Collections.Generic.List[object]]::new()
$failures = [Collections.Generic.List[object]]::new()
$identityFiles = 0
$checkedFiles = 0
$contexts = 0
$timer = [Diagnostics.Stopwatch]::StartNew()
foreach ($sourcePath in $inventory) {
    $relativeFile = [IO.Path]::GetRelativePath($coreRoot, $sourcePath).Replace('\', '/')
    # The renderer copies directive-free files exactly. With no Java overrides,
    # every included context receives the same bytes: no inter-context gap can differ.
    if (-not (Get-Content -LiteralPath $sourcePath -Raw).Contains('{%')) {
        $identityFiles++
        continue
    }
    $output = @(& $Cli --output-format json source simplify verify --repo-root $checkoutRoot --file $relativeFile --summary --max-regions 1 2>&1)
    $exitCode = $LASTEXITCODE
    $checkedFiles++
    $raw = ($output | ForEach-Object { $_.ToString() }) -join "`n"
    if ($raw -match '(?i)no space left|not enough space|disk (?:is )?full|os error 112') {
        throw 'Disk-space error: stop and wait for the user.'
    }
    try {
        $report = $raw | ConvertFrom-Json
        if ($report.schema -ne 'sfm:source_simplify_worker@1') { throw 'Unexpected worker schema.' }
        $contexts += $report.contexts_checked
        if ($exitCode -ne 0 -or -not $report.all_oracles_verified) {
            $failures.Add(@{file=$relativeFile; failures=@($report.failures | Select-Object projection,status,diagnostic -First 2)})
        }
        if (-not $report.simplification_complete) {
            $candidates.Add(@{file=$relativeFile; pairs=$report.pairs_with_whitespace_candidates})
        }
    } catch {
        $failures.Add(@{file=$relativeFile; diagnostic=$raw.Substring(0, [Math]::Min(1500, $raw.Length))})
    }
    if ($checkedFiles % 100 -eq 0) {
        Write-Host "Checked $checkedFiles templates; $($candidates.Count) candidate files; $($failures.Count) failures."
    }
}
$timer.Stop()
@{
    schema='sfm:source_simplify_tree_audit@1'
    java_files=$inventory.Count
    identity_files=$identityFiles
    verified_templates=$checkedFiles
    contexts_checked=$contexts
    candidate_files=@($candidates.ToArray())
    failures=@($failures.ToArray())
    elapsed_seconds=$timer.Elapsed.TotalSeconds
    writes_performed=$false
} | ConvertTo-Json -Depth 12
if ($failures.Count -or $candidates.Count) { exit 1 }
