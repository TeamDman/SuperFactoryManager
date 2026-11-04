# Run from this directory, or pass -RepoRoot from elsewhere.
# Examples:
#   ./check-everything.ps1 -PlanOnly
#   ./check-everything.ps1 -Projection 'sfm-dev/mc-1.20.*' -Stage Check,Compile
#   ./check-everything.ps1 -Projection '1.21.1' -Stage Check -ReadWorkers 3
# Exact-input successes resume from durable receipts. Failures retry on the
# next invocation; historical expected-red tests are reported, never relabeled
# as passes. Headless tests never open a windowed client.
param(
  [string[]]$Projection = @('*'),
  [ValidateSet('Check','Compile','Jar','Headless')][string[]]$Stage = @('Check','Compile','Jar','Headless'),
  [ValidateRange(1,8)][int]$ReadWorkers = 2,
  [string]$HeadlessFilter = '*',
  [string]$ReleasedRecipe = '',
  [string]$RepoRoot = '',
  [string]$Cli = '',
  [string]$StateRoot = '',
  [string]$WorkerScript = '',
  [long]$MinFreeDBytes = 2000000000,
  [switch]$PlanOnly
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function SaveAtomic([string]$path,$value) {
  if (Test-Path -LiteralPath $path) { throw "Summary collision: $path" }
  $temporary = "$path.writing-$([guid]::NewGuid().ToString('N'))"
  [IO.File]::WriteAllText($temporary,($value | ConvertTo-Json -Depth 9) + "`n",[Text.UTF8Encoding]::new($false))
  Move-Item -LiteralPath $temporary -Destination $path -ErrorAction Stop
}
function Invoke-One([string]$key,[string]$step) {
  $workerArguments = @{
    RepoRoot=$RepoRoot;Projection=$key;Stage=$step;Cli=$Cli;StateRoot=$StateRoot
    HeadlessFilter=$HeadlessFilter;ReleasedRecipe=$ReleasedRecipe;MinFreeDBytes=$MinFreeDBytes
  }
  & $WorkerScript @workerArguments
}

if (-not $RepoRoot) { $RepoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..')) }
$RepoRoot = [IO.Path]::GetFullPath($RepoRoot)
$catalogPath = Join-Path $RepoRoot 'platform/minecraft/projections.json'
if (-not (Test-Path -LiteralPath $catalogPath -PathType Leaf)) { throw "SFM projection catalog missing: $catalogPath; pass -RepoRoot" }
if (-not $Cli) {
  $command = Get-Command sfm-propagate-changes.exe -CommandType Application -ErrorAction Stop
  $Cli = $command.Source
}
$Cli = [IO.Path]::GetFullPath($Cli)
if (-not (Test-Path -LiteralPath $Cli -PathType Leaf)) { throw "SFM CLI missing: $Cli" }
if (-not $WorkerScript) { $WorkerScript = Join-Path $PSScriptRoot 'scripts/check-projection-one.ps1' }
$WorkerScript = [IO.Path]::GetFullPath($WorkerScript)
if (-not (Test-Path -LiteralPath $WorkerScript -PathType Leaf)) { throw "Projection worker missing: $WorkerScript" }
if (-not $StateRoot) { $StateRoot = Join-Path $RepoRoot 'sfm-toolchain-data/check-everything' }
$StateRoot = [IO.Path]::GetFullPath($StateRoot)
$catalog = Get-Content -LiteralPath $catalogPath -Raw | ConvertFrom-Json -AsHashtable
$keys = @($catalog.Keys | Sort-Object)
$patterns = @($Projection | ForEach-Object {
  [pscustomobject]@{text=$_;matcher=[Management.Automation.WildcardPattern]::new($_,[Management.Automation.WildcardOptions]::IgnoreCase)}
})
$selected = @($keys | Where-Object {
  $key = $_
  $version = [string]$catalog[$key].minecraft_version
  foreach ($pattern in $patterns) { if ($pattern.matcher.IsMatch($key) -or ($pattern.text -notmatch '/' -and $pattern.matcher.IsMatch($version))) { return $true } }
  return $false
})
if (-not $selected.Count) { throw "No registered projection matched: $($Projection -join ', ')" }
foreach ($key in $selected) {
  if ($key -notmatch '^[a-z0-9][a-z0-9.-]*/mc-[0-9.]+$') { throw "Unsafe catalog key: $key" }
}
if ($ReleasedRecipe -and ($selected.Count -ne 1 -or $selected[0] -notmatch '^sfm-4\.3[45]\.0/mc-')) {
  throw '-ReleasedRecipe is allowed only for one exact historical plain projection'
}
$orderedStages = @(@('Check','Compile','Jar','Headless') | Where-Object { $Stage -contains $_ })
if (-not $orderedStages.Count) { throw 'Select at least one stage' }
$plan = [ordered]@{
  schema='sfm:check_everything_plan@1';repo_root=$RepoRoot;catalog_sha256=(Get-FileHash -LiteralPath $catalogPath -Algorithm SHA256).Hash
  cli=$Cli;cli_sha256=(Get-FileHash -LiteralPath $Cli -Algorithm SHA256).Hash;patterns=$Projection;projections=$selected;stages=$orderedStages
  read_workers=$ReadWorkers;heavy_workers=1;state_root=$StateRoot;headless_filter=$HeadlessFilter;released_recipe=$ReleasedRecipe
  note='Read-only checks may run concurrently; compile, JAR and headless server run sequentially. Existing JARs and optional manual tests block unsafe phases. Successful exact-input receipts resume; failed attempts remain retained.'
}
if ($PlanOnly) { $plan | ConvertTo-Json -Depth 7; return }

[void](New-Item -ItemType Directory -Path $StateRoot -Force)
$globalLockPath = Join-Path $RepoRoot 'sfm-toolchain-data/check-everything.global.lock'
[void](New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($globalLockPath)) -Force)
$globalLock = [IO.File]::Open($globalLockPath,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
$lockPath = Join-Path $StateRoot 'orchestrator.lock'
try { $lock = [IO.File]::Open($lockPath,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None) }
catch { $globalLock.Dispose(); throw }
$results = [Collections.Generic.List[object]]::new()
$startedUtc = [DateTime]::UtcNow
try {
  if ($orderedStages -contains 'Check') {
    $queue = [Collections.Generic.Queue[string]]::new()
    foreach ($key in $selected) { $queue.Enqueue($key) }
    $jobs = [Collections.Generic.List[object]]::new()
    while ($queue.Count -or $jobs.Count) {
      while ($queue.Count -and $jobs.Count -lt $ReadWorkers) {
        $key = $queue.Dequeue()
        $arguments = @{RepoRoot=$RepoRoot;Projection=$key;Stage='Check';Cli=$Cli;StateRoot=$StateRoot;HeadlessFilter=$HeadlessFilter;ReleasedRecipe=$ReleasedRecipe;MinFreeDBytes=$MinFreeDBytes}
        $job = Start-ThreadJob -Name $key -ScriptBlock { param($worker,$workerArguments) & $worker @workerArguments } -ArgumentList $WorkerScript,$arguments
        $jobs.Add($job)
      }
      $done = Wait-Job -Job $jobs.ToArray() -Any -Timeout 30
      if (-not $done) { Write-Host "Source checks active: $($jobs.Count); queued: $($queue.Count)"; continue }
      try {
        $row = @(Receive-Job -Job $done -ErrorAction Stop) | Select-Object -Last 1
        if (-not $row) { throw 'Worker returned no result' }
      } catch {
        $row = [pscustomobject]@{projection=$done.Name;stage='Check';status='failed';reason=$_.Exception.Message;receipt=$null;elapsed_seconds=0;exit_code=$null}
      }
      $results.Add($row)
      Write-Host ("{0,-9} {1,-8} {2} {3}" -f $row.status,$row.stage,$row.projection,$row.reason)
      [void]$jobs.Remove($done)
      Remove-Job -Job $done -Force
    }
  }
  foreach ($key in $selected) {
    $check = @($results | Where-Object { $_.projection -ceq $key -and $_.stage -eq 'Check' } | Select-Object -Last 1)
    if (($orderedStages -contains 'Check') -and ($check.Count -ne 1 -or $check[0].status -notin @('passed','reused'))) {
      foreach ($step in @($orderedStages | Where-Object { $_ -ne 'Check' })) {
        $results.Add([pscustomobject]@{projection=$key;stage=$step;status='skipped';reason='Source check did not pass';receipt=$null;elapsed_seconds=0;exit_code=$null})
      }
      continue
    }
    $compileFailed = $false
    foreach ($step in @($orderedStages | Where-Object { $_ -ne 'Check' })) {
      if ($compileFailed) {
        $results.Add([pscustomobject]@{projection=$key;stage=$step;status='skipped';reason='Compile did not pass';receipt=$null;elapsed_seconds=0;exit_code=$null})
        continue
      }
      try { $row = Invoke-One $key $step }
      catch { $row = [pscustomobject]@{projection=$key;stage=$step;status='failed';reason=$_.Exception.Message;receipt=$null;elapsed_seconds=0;exit_code=$null} }
      $results.Add($row)
      Write-Host ("{0,-9} {1,-8} {2} {3}" -f $row.status,$row.stage,$row.projection,$row.reason)
      if ($step -eq 'Compile' -and $row.status -notin @('passed','reused')) { $compileFailed = $true }
    }
  }
} finally { $lock.Dispose(); $globalLock.Dispose() }
$summary = [ordered]@{
  schema='sfm:check_everything_summary@1';started_utc=$startedUtc.ToString('o');ended_utc=[DateTime]::UtcNow.ToString('o')
  plan=$plan;passed=@($results | Where-Object status -eq 'passed').Count;reused=@($results | Where-Object status -eq 'reused').Count
  failed=@($results | Where-Object status -eq 'failed').Count;blocked=@($results | Where-Object status -eq 'blocked').Count
  skipped=@($results | Where-Object status -eq 'skipped').Count;rows=@($results)
}
$runs = Join-Path $StateRoot 'runs'
[void](New-Item -ItemType Directory -Path $runs -Force)
$summaryPath = Join-Path $runs ("summary-$($startedUtc.ToString('yyyyMMddTHHmmssZ'))-$([guid]::NewGuid().ToString('N')).json")
SaveAtomic $summaryPath $summary
Write-Host "Summary: $summaryPath"
Write-Host "Passed $($summary.passed), reused $($summary.reused), failed $($summary.failed), blocked $($summary.blocked), skipped $($summary.skipped)."
if ($summary.failed -or $summary.blocked -or $summary.skipped) { exit 1 }
