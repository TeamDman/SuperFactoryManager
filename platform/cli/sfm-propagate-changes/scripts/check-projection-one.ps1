param(
  [Parameter(Mandatory)][string]$RepoRoot,
  [Parameter(Mandatory)][string]$Projection,
  [Parameter(Mandatory)][ValidateSet('Check','Compile','Jar','Headless')][string]$Stage,
  [Parameter(Mandatory)][string]$Cli,
  [Parameter(Mandatory)][string]$StateRoot,
  [string]$HeadlessFilter = '*',
  [string]$ReleasedRecipe = '',
  [long]$MinFreeDBytes = 2000000000
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function DigestText([string]$value) {
  [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($value)))
}
function SaveAtomic([string]$path,$value) {
  if (Test-Path -LiteralPath $path) { throw "Receipt collision: $path" }
  $temporary = "$path.writing-$([guid]::NewGuid().ToString('N'))"
  [IO.File]::WriteAllText($temporary,($value | ConvertTo-Json -Depth 8) + "`n",[Text.UTF8Encoding]::new($false))
  Move-Item -LiteralPath $temporary -Destination $path -ErrorAction Stop
}
function InputState {
  $paths = @('platform/minecraft/core-liquid-template',"platform/minecraft/projections/$Projection")
  $coreTree = (& git -C $RepoRoot rev-parse 'HEAD:platform/minecraft/core-liquid-template').Trim()
  if ($LASTEXITCODE -ne 0 -or $coreTree -notmatch '^[0-9a-f]{40}$') { throw 'Cannot identify committed core source tree' }
  $diff = @(& git -C $RepoRoot diff --binary HEAD -- @paths)
  if ($LASTEXITCODE -ne 0) { throw 'Git diff failed while freezing operation inputs' }
  $untracked = @(& git -C $RepoRoot ls-files --others --exclude-standard -- @paths)
  if ($LASTEXITCODE -ne 0) { throw 'Git untracked inventory failed' }
  $extra = foreach ($relative in ($untracked | Sort-Object)) {
    $full = Join-Path $RepoRoot $relative
    if (-not (Test-Path -LiteralPath $full -PathType Leaf)) { throw "Untracked input disappeared: $relative" }
    "$relative|$((Get-FileHash -LiteralPath $full -Algorithm SHA256).Hash)"
  }
  $sourceFiles = @(
    Get-ChildItem -LiteralPath $projectRoot -File -Force -ErrorAction Stop
    Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src') -Recurse -File -Force -ErrorAction Stop
    if (Test-Path -LiteralPath (Join-Path $projectRoot 'gradle')) {
      Get-ChildItem -LiteralPath (Join-Path $projectRoot 'gradle') -Recurse -File -Force -ErrorAction Stop
    }
  )
  $sourceRows = foreach ($file in ($sourceFiles | Sort-Object FullName)) {
    if ($file.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Projection source reparse point: $($file.FullName)" }
    $relative = $file.FullName.Substring($projectRoot.Length + 1).Replace('\','/')
    "$relative|$($file.Length)|$((Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash)"
  }
  $jarIdentity = @()
  if ($Stage -eq 'Headless') {
    $libs = Join-Path $projectRoot 'build/libs'
    $jarIdentity = @(Get-ChildItem -LiteralPath $libs -Filter '*rust.jar' -File -ErrorAction SilentlyContinue |
      Sort-Object Name | ForEach-Object { "$($_.Name)|$((Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash)" })
  }
  [ordered]@{
    head = (& git -C $RepoRoot rev-parse HEAD).Trim()
    core_tree_oid = $coreTree
    worker_sha256 = (Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash
    cli_sha256 = (Get-FileHash -LiteralPath $Cli -Algorithm SHA256).Hash
    catalog_sha256 = (Get-FileHash -LiteralPath (Join-Path $RepoRoot 'platform/minecraft/projections.json') -Algorithm SHA256).Hash
    project_inputs_sha256 = (Get-FileHash -LiteralPath (Join-Path $RepoRoot 'platform/minecraft/core-liquid-template/project-inputs.json') -Algorithm SHA256).Hash
    lock_sha256 = (Get-FileHash -LiteralPath (Join-Path $projectRoot 'sfm-toolchain.lock.json') -Algorithm SHA256).Hash
    dirty_diff_sha256 = DigestText ($diff -join "`n")
    untracked_sha256 = DigestText ($extra -join "`n")
    project_source_sha256 = DigestText ($sourceRows -join "`n")
    project_source_files = $sourceFiles.Count
    headless_jar_sha256 = DigestText ($jarIdentity -join "`n")
  }
}
function RelevantState($state) {
  $relevant = [ordered]@{}
  foreach ($key in $state.Keys) { if ($key -ne 'head') { $relevant[$key] = $state[$key] } }
  $relevant
}

if ($Projection -notmatch '^[a-z0-9][a-z0-9.-]*/mc-[0-9.]+$') { throw "Unsafe projection key: $Projection" }
$RepoRoot = [IO.Path]::GetFullPath($RepoRoot)
$projectRoot = Join-Path $RepoRoot "platform/minecraft/projections/$Projection"
if (-not (Test-Path -LiteralPath $projectRoot -PathType Container)) { throw "Projection directory missing: $Projection" }
$before = InputState
$identity = DigestText (([ordered]@{schema=2;projection=$Projection;stage=$Stage;filter=$HeadlessFilter;recipe=$ReleasedRecipe;inputs=(RelevantState $before)} | ConvertTo-Json -Depth 6 -Compress))
$receiptDir = Join-Path $StateRoot ("receipts/" + $Projection.Replace('/','_') + "/" + $Stage.ToLowerInvariant())
[void](New-Item -ItemType Directory -Path $receiptDir -Force)
$passPath = Join-Path $receiptDir "$identity.pass.json"
if (Test-Path -LiteralPath $passPath) {
  $previous = Get-Content -LiteralPath $passPath -Raw | ConvertFrom-Json
  if ($previous.identity -cne $identity -or $previous.status -cne 'passed') { throw "Invalid prior pass receipt: $passPath" }
  $canReuse = $true
  if ($Stage -eq 'Jar') {
    $jar = $previous.jar
    $canReuse = $jar -and (Test-Path -LiteralPath $jar.path -PathType Leaf) -and
      (Get-FileHash -LiteralPath $jar.path -Algorithm SHA256).Hash -ceq $jar.sha256
  }
  if ($canReuse) { return [pscustomobject]@{projection=$Projection;stage=$Stage;status='reused';reason='exact prior pass receipt and unchanged input identity';receipt=$passPath;elapsed_seconds=0;exit_code=0} }
  return [pscustomobject]@{projection=$Projection;stage=$Stage;status='blocked';reason='Prior JAR receipt no longer matches the current artifact; inspect before replacement';receipt=$passPath;elapsed_seconds=0;exit_code=$null}
}
$attempt = 1 + @(Get-ChildItem -LiteralPath $receiptDir -Filter "$identity.attempt-*.started.json" -ErrorAction SilentlyContinue).Count
$base = Join-Path $receiptDir ("$identity.attempt-" + $attempt.ToString('000'))
$startedPath = "$base.started.json"
$terminalPath = "$base.receipt.json"
$console = "$base.console.log"
$events = "$base.ndjson"
foreach ($path in @($startedPath,$terminalPath,$console,$events)) { if (Test-Path -LiteralPath $path) { throw "Attempt collision: $path" } }
$started = [DateTime]::UtcNow
SaveAtomic $startedPath ([ordered]@{schema='sfm:projection_check_started@1';projection=$Projection;stage=$Stage;identity=$identity;started_utc=$started.ToString('o');pid=$PID})
$status = 'failed'; $reason = ''; $code = $null; $jarReport = $null
try {
  if ($Stage -ne 'Check' -and [IO.DriveInfo]::new('D').AvailableFreeSpace -lt $MinFreeDBytes) {
    $status = 'blocked'; $reason = "D: free space below $MinFreeDBytes bytes"
  } elseif ($Stage -ne 'Check' -and @(Get-Process -Name java,javaw,sfm-propagate-changes -ErrorAction SilentlyContinue).Count) {
    $status = 'blocked'; $reason = 'A Java or SFM process may own a mutable build/runtime root'
  } elseif (($Stage -eq 'Compile' -or $Stage -eq 'Jar') -and $Projection -match '^sfm-4\.3[45]\.0/mc-' -and -not $ReleasedRecipe) {
    $status = 'blocked'; $reason = 'Historical plain projection needs an exact --released-recipe; no recipe was inferred'
  } elseif ($Stage -eq 'Headless' -and $HeadlessFilter -eq '*' -and $Projection -match '(extended|visual)') {
    $status = 'blocked'; $reason = 'An extended/visual projection may contain a manual GameTest; supply an exact bounded -HeadlessFilter'
  } elseif ($Stage -eq 'Jar' -and @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'build/libs') -Filter '*rust.jar' -File -ErrorAction SilentlyContinue).Count) {
    $status = 'blocked'; $reason = 'Canonical generated JAR already exists; preserve it before create-only packaging'
  } else {
    $selectorArgs = if ($ReleasedRecipe) { @('--released-recipe',$ReleasedRecipe) } else { @('--dependency-profile','rust-toolchain') }
    Push-Location $projectRoot
    try {
      $ErrorActionPreference = 'Continue'
      switch ($Stage) {
        'Check' { & $Cli source project check --projection $Projection --output-format json *> $console }
        'Compile' { & $Cli source project compile --projection $Projection @selectorArgs --log-file $events *> $console }
        'Jar' { & $Cli source project jar --projection $Projection @selectorArgs --log-file $events *> $console }
        'Headless' { & $Cli game-test run-server --projection $Projection --filter $HeadlessFilter --log-file $events *> $console }
      }
      $code = $LASTEXITCODE
      $ErrorActionPreference = 'Stop'
    } finally { Pop-Location }
    $text = [IO.File]::ReadAllText($console)
    $marker = switch ($Stage) {
      'Check' { $text -match '"operation"\s*:\s*"check"' -and $text -match '"writes_performed"\s*:\s*false' }
      'Compile' { $text -match '"compilation"\s*:\s*"completed"' }
      'Jar' { $text -match '"jar_packaging"\s*:\s*"completed"' }
      'Headless' { $text -match 'Validated ([1-9][0-9]*) required game tests passed' }
    }
    if ($Stage -eq 'Jar' -and $code -eq 0 -and $marker) {
      $jars = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'build/libs') -Filter '*rust.jar' -File)
      if ($jars.Count -ne 1) { $marker = $false; $reason = 'Expected one current generated JAR' }
      else { $jarReport = [ordered]@{path=$jars[0].FullName;bytes=$jars[0].Length;sha256=(Get-FileHash -LiteralPath $jars[0].FullName -Algorithm SHA256).Hash} }
    }
    $status = if ($code -eq 0 -and $marker) { 'passed' } else { 'failed' }
    if (-not $reason -and $status -eq 'failed') { $reason = "exit=$code marker=$marker; inspect console and event log" }
    if ($Stage -eq 'Headless' -and $status -eq 'failed' -and (Test-Path -LiteralPath $events)) {
      $eventLine = Select-String -LiteralPath $events -Pattern 'Minecraft JVM output written to .*console\.log' | Select-Object -Last 1
      if ($eventLine -and $eventLine.Line -match 'Minecraft JVM output written to (.+?console\.log)') {
        $sessionConsole = $Matches[1] -replace '\\\\','\'
        if (Test-Path -LiteralPath $sessionConsole -PathType Leaf) {
          $sessionText = [IO.File]::ReadAllText($sessionConsole)
          $failureCount = [regex]::Match($sessionText,'(\d+) required tests failed')
          $failureNames = @([regex]::Matches($sessionText,'(?m)^\[[^\r\n]+\]:\s+-\s+([a-z0-9_]+)\s*$') | ForEach-Object { $_.Groups[1].Value })
          if ($failureCount.Success) { $reason = "exit=$code; $($failureCount.Groups[1].Value) required tests failed: $($failureNames -join ', ')" }
          elseif ($sessionText -match 'matched zero tests') { $reason = "exit=$code; selector matched zero tests: $HeadlessFilter" }
        }
      }
    }
    if ($status -eq 'failed' -and (Test-Path -LiteralPath $console)) {
      $lastLine = @(Get-Content -LiteralPath $console -Tail 12 | Where-Object { $_.Trim() } | Select-Object -Last 1)
      if ($lastLine.Count) { $reason += "; last output: $($lastLine[0].Trim().Substring(0,[Math]::Min(240,$lastLine[0].Trim().Length)))" }
    }
  }
} catch {
  $status = 'failed'; $reason = $_.Exception.Message
}
$after = $null
try { $after = InputState } catch { $status = 'failed'; $reason = "Cannot recheck frozen inputs: $($_.Exception.Message)" }
if ($after -and (((RelevantState $before) | ConvertTo-Json -Depth 4 -Compress) -cne ((RelevantState $after) | ConvertTo-Json -Depth 4 -Compress))) {
  $status = 'failed'; $reason = 'Frozen source, lock, catalog or CLI identity changed during operation'
}
$ended = [DateTime]::UtcNow
$receipt = [ordered]@{
  schema='sfm:projection_check_receipt@1';projection=$Projection;stage=$Stage;identity=$identity;status=$status;reason=$reason
  started_utc=$started.ToString('o');ended_utc=$ended.ToString('o');elapsed_seconds=[math]::Round(($ended-$started).TotalSeconds,3)
  exit_code=$code;before=$before;after=$after;jar=$jarReport;console=$console;events=$events
}
SaveAtomic $terminalPath $receipt
if ($status -eq 'passed' -and -not (Test-Path -LiteralPath $passPath)) { SaveAtomic $passPath $receipt }
[pscustomobject]@{projection=$Projection;stage=$Stage;status=$status;reason=$reason;receipt=$terminalPath;elapsed_seconds=$receipt.elapsed_seconds;exit_code=$code}
