<#
.SYNOPSIS
Run the Rust tests in separate, bounded test-harness processes.

.DESCRIPTION
Runs each library module, each integration target, and the large promotion
fixture separately. Use -Shard unit:cli, -Shard integration:java_analysis_scenarios,
or -Shard fixture to run just one shard. Ignored tests keep Cargo's default
behavior and are not run.

PowerShell 7 or later (pwsh.exe), rg.exe, git.exe, and cargo.exe must be
available to this process and its children.
If a sandbox hides rg.exe from child processes, run this script in a host
PowerShell session. The script does not elevate itself or change global Git
configuration.
#>
[CmdletBinding()]
param(
    [string]$Shard = 'all'
)

if ($PSVersionTable.PSVersion.Major -lt 7) {
    throw 'test-bounded.ps1 requires PowerShell 7 or later (pwsh.exe); Windows PowerShell 5.1 treats native stderr as a terminating error.'
}

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$diskErrorPattern = '(?i)no space left|not enough space|disk (?:is )?full|insufficient disk space|os error 112|0x80070070'

$moduleRoots = @(
    'artifact_lock', 'branch_targets', 'cancellation', 'cli', 'colour',
    'curseforge', 'dependency_classfile_stubs', 'dependency_inventory',
    'dependency_locked_sources', 'dependency_sources', 'jar_build',
    'java_analysis', 'java_source_catalog', 'jdk', 'jdk_artifact_cache',
    'logging', 'modrinth', 'propagate', 'release_review_capture',
    'release_review_capture_io', 'release_review_git',
    'release_review_java_diff', 'release_review_ledger',
    'release_review_ledger_resolve', 'release_review_source_probe',
    'release_review_surface_v1', 'release_review_text_diff',
    'release_review_v1', 'release_review_working_tree',
    'review_session_v1', 'review_session_v2', 'source_archive',
    'source_audit', 'source_cache', 'source_decompile', 'source_git',
    'source_maven', 'source_projection', 'source_provider',
    'syntax_highlight', 'toolchain_lockfile_schema',
    'toolchain_lockfile_write'
)
$integrationTargets = @(
    'java_analysis_scenarios',
    'release_review_git_test',
    'release_review_materialize_test',
    'source_jar_absence_cli_test'
)
$fixture = 'cli::source::promotion_cli::tests::real_pinned_tag_inputs_flow_through_fictional_ten_target_immutable_apply'
$standaloneTests = @(
    'tests::frozen_stage_rejects_global_log_file_before_logging_initializes'
)

$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $projectRoot '..\..\..')).Path
$manifest = Join-Path $projectRoot 'Cargo.toml'
$commonCargoArgs = @(
    'test', '--offline', '--locked', '--all-features', '--quiet',
    '--manifest-path', $manifest
)

function Assert-IntegrationTargets {
    $metadataArgs = @(
        'metadata', '--no-deps', '--offline', '--locked', '--format-version', '1',
        '--manifest-path', $manifest
    )
    $jsonLines = [System.Collections.Generic.List[string]]::new()
    $tail = [System.Collections.Generic.Queue[string]]::new()
    & cargo @metadataArgs 2>&1 | ForEach-Object {
        $line = $_.ToString()
        if ($line -match $diskErrorPattern) {
            throw "Disk-space error while reading integration targets: $line. Stop and wait for the user."
        }
        if ($tail.Count -ge 20) { [void]$tail.Dequeue() }
        $tail.Enqueue($line)
        # Cargo metadata emits one compact JSON object; stderr diagnostics
        # must not become part of the document passed to ConvertFrom-Json.
        if ($line.TrimStart().StartsWith('{')) { $jsonLines.Add($line) }
    }
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        foreach ($line in $tail) { Write-Host $line }
        throw "Could not read Cargo integration targets (exit $exitCode)."
    }
    if ($jsonLines.Count -ne 1) {
        throw 'Cargo metadata must return exactly one JSON document.'
    }
    $metadata = $jsonLines[0] | ConvertFrom-Json
    $packages = @($metadata.packages | Where-Object { $_.name -eq 'sfm-propagate-changes' })
    if ($packages.Count -ne 1) {
        throw 'Cargo metadata must contain exactly one sfm-propagate-changes package.'
    }
    $actualTargets = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::Ordinal
    )
    foreach ($target in $packages[0].targets) {
        if ($target.kind -contains 'test') { [void]$actualTargets.Add($target.name) }
    }
    $configuredTargets = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::Ordinal
    )
    foreach ($target in $integrationTargets) { [void]$configuredTargets.Add($target) }
    if ($configuredTargets.Count -ne $integrationTargets.Count) {
        throw 'The configured integration target list contains duplicates.'
    }
    if (-not $configuredTargets.SetEquals($actualTargets)) {
        $omitted = @($actualTargets | Where-Object { -not $configuredTargets.Contains($_) } | Sort-Object)
        $unknown = @($configuredTargets | Where-Object { -not $actualTargets.Contains($_) } | Sort-Object)
        throw ("Integration target partition differs from Cargo metadata; omitted: [{0}]; unknown: [{1}]." -f
            ($omitted -join ', '), ($unknown -join ', '))
    }
    Write-Host "Integration target partition: $($actualTargets.Count) exact Cargo targets."
}

function Assert-LibraryPartition {
    $listArgs = $commonCargoArgs + @('--lib', '--', '--list')
    # Keep test names for coverage and only a short tail for failure details.
    $names = [System.Collections.Generic.List[string]]::new()
    $tail = [System.Collections.Generic.Queue[string]]::new()
    & cargo @listArgs 2>&1 | ForEach-Object {
        $line = $_.ToString()
        if ($line -match $diskErrorPattern) {
            throw "Disk-space error while listing library tests: $line. Stop and wait for the user."
        }
        if ($tail.Count -ge 20) { [void]$tail.Dequeue() }
        $tail.Enqueue($line)
        if ($line -match ': test$') {
            $names.Add(($line -replace ': test$', ''))
        }
    }
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        foreach ($line in $tail) { Write-Host $line }
        throw "Could not list library tests (exit $exitCode)."
    }

    if ($names.Count -eq 0) { throw 'The library test listing is empty.' }

    # libtest filters are substrings, so verify this partition against the
    # current test list before any shard runs. The two skips remove the known
    # fixture duplication and any cli::<module>:: namespace overlap.
    $coverage = [System.Collections.Generic.Dictionary[string, int]]::new(
        [System.StringComparer]::Ordinal
    )
    foreach ($name in $names) { $coverage.Add($name, 0) }
    foreach ($root in $moduleRoots) {
        $filter = "${root}::"
        foreach ($name in $names) {
            if (-not $name.Contains($filter)) { continue }
            if ($root -eq 'cli' -and $name.Contains($fixture)) { continue }
            if ($root -ne 'cli' -and $name.Contains("cli::${root}::")) {
                continue
            }
            $coverage[$name]++
        }
    }
    if (-not $coverage.ContainsKey($fixture)) {
        throw 'The dedicated promotion fixture is missing from the library test list.'
    }
    $coverage[$fixture]++
    foreach ($test in $standaloneTests) {
        if (-not $coverage.ContainsKey($test)) {
            throw "The standalone library test '$test' is missing from the test list."
        }
        $coverage[$test]++
    }
    $bad = @($coverage.GetEnumerator() | Where-Object { $_.Value -ne 1 })
    if ($bad.Count -ne 0) {
        $bad | Select-Object -First 5 | ForEach-Object {
            Write-Host "Coverage count $($_.Value): $($_.Key)"
        }
        throw "Library test partition has $($bad.Count) omitted or overlapping tests."
    }
    Write-Host "Library partition: $($names.Count) listed tests across $($moduleRoots.Count) modules, $($standaloneTests.Count) standalone tests and one dedicated fixture."
}

function Invoke-CargoShard {
    param(
        [string]$Name,
        [string[]]$Arguments,
        [int]$Index,
        [int]$Total
    )

    Write-Host ("[{0}/{1}] {2}" -f $Index, $Total, $Name)
    $state = [pscustomobject]@{
        Summary = $null
        Tail = [System.Collections.Generic.Queue[string]]::new()
    }
    & cargo @Arguments 2>&1 | ForEach-Object {
        $line = $_.ToString()
        if ($line -match $diskErrorPattern) {
            throw "Disk-space error in $Name`: $line. Stop and wait for the user."
        }
        if ($line -match 'test result:') { $state.Summary = $line }
        if ($state.Tail.Count -ge 40) { [void]$state.Tail.Dequeue() }
        $state.Tail.Enqueue($line)
    }
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        foreach ($line in $state.Tail) { Write-Host $line }
        throw "$Name failed (exit $exitCode). No further shards will run."
    }
    if ($state.Summary) {
        Write-Host "PASS $Name`: $($state.Summary)"
    } else {
        Write-Host "PASS $Name"
    }
}

$rg = Get-Command rg.exe -CommandType Application -ErrorAction SilentlyContinue |
    Select-Object -First 1
if (-not $rg) {
    throw 'rg.exe is unavailable. Run in a host PowerShell session with ripgrep on PATH.'
}
foreach ($tool in @('git.exe', 'cargo.exe')) {
    if (-not (Get-Command $tool -CommandType Application -ErrorAction SilentlyContinue)) {
        throw "$tool is unavailable on PATH."
    }
}
& $rg.Source --version | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw 'rg.exe could not start. Run in a host PowerShell session.'
}

$jobs = [System.Collections.Generic.List[object]]::new()
foreach ($root in $moduleRoots) {
    $name = "unit:$root"
    if ($Shard -ne 'all' -and $Shard -ne $name) { continue }
    $cargoArgs = $commonCargoArgs + @('--lib', "${root}::", '--', '--test-threads=1')
    if ($root -eq 'cli') { $cargoArgs += @('--skip', $fixture) }
    if ($root -ne 'cli') { $cargoArgs += @('--skip', "cli::${root}::") }
    $jobs.Add([pscustomobject]@{ Name = $name; Arguments = $cargoArgs })
}
foreach ($target in $integrationTargets) {
    $name = "integration:$target"
    if ($Shard -ne 'all' -and $Shard -ne $name) { continue }
    $cargoArgs = $commonCargoArgs + @('--test', $target, '--', '--test-threads=1')
    $jobs.Add([pscustomobject]@{ Name = $name; Arguments = $cargoArgs })
}
foreach ($test in $standaloneTests) {
    $name = "unit:$test"
    if ($Shard -ne 'all' -and $Shard -ne $name) { continue }
    $cargoArgs = $commonCargoArgs + @('--lib', $test, '--', '--exact', '--test-threads=1')
    $jobs.Add([pscustomobject]@{ Name = $name; Arguments = $cargoArgs })
}
if ($Shard -eq 'all' -or $Shard -eq 'fixture') {
    $cargoArgs = $commonCargoArgs + @('--lib', $fixture, '--', '--exact', '--test-threads=1')
    $jobs.Add([pscustomobject]@{ Name = 'fixture'; Arguments = $cargoArgs })
}
if ($jobs.Count -eq 0) {
    throw "Unknown shard '$Shard'. Use all, fixture, unit:<module>, or integration:<target>."
}

$savedEnvironment = @{}
foreach ($key in @('Path', 'GIT_CONFIG_COUNT', 'GIT_CONFIG_KEY_0', 'GIT_CONFIG_VALUE_0')) {
    $savedEnvironment[$key] = [System.Environment]::GetEnvironmentVariable($key, 'Process')
}
try {
    # These settings apply only to this process and inherited child processes.
    # Frozen Git probes remove GIT_* themselves and inject their own exact root.
    $env:Path = "$(Split-Path -Parent $rg.Source)$([System.IO.Path]::PathSeparator)$env:Path"
    $env:GIT_CONFIG_COUNT = '1'
    $env:GIT_CONFIG_KEY_0 = 'safe.directory'
    $env:GIT_CONFIG_VALUE_0 = $repositoryRoot

    if ($Shard -eq 'all') {
        Assert-IntegrationTargets
        Assert-LibraryPartition
    }
    for ($index = 0; $index -lt $jobs.Count; $index++) {
        $job = $jobs[$index]
        Invoke-CargoShard -Name $job.Name -Arguments $job.Arguments `
            -Index ($index + 1) -Total $jobs.Count
    }
    Write-Host "PASS: $($jobs.Count) test shards completed."
} finally {
    foreach ($key in $savedEnvironment.Keys) {
        [System.Environment]::SetEnvironmentVariable(
            $key, $savedEnvironment[$key], 'Process'
        )
    }
}
