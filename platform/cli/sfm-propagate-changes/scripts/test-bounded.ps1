<#
.SYNOPSIS
Run the Rust tests in separate, bounded test-harness processes.

.DESCRIPTION
Runs each library module, each integration target, and the large promotion
fixture separately. Only source_projection is split by the immediate child
modules discovered from Cargo's current library test list. The old
-Shard unit:source_projection selector runs all of those child shards; use
-Shard unit:source_projection:core_inputs for one child. Other existing selectors
such as -Shard unit:cli, -Shard integration:java_analysis_scenarios, and
-Shard fixture retain their behavior. Ignored tests keep Cargo's default
behavior and are not run.

-Workers 2 enables an event-driven pool for the explicitly reviewed read-only
theme groups. All other groups retain exclusive execution. This standalone
script defaults to one worker; check-all.ps1 defaults to two after the real
three-group concurrency proof. Coverage and per-group test threads are unchanged.

PowerShell 7 or later (pwsh.exe), rg.exe, git.exe, and cargo.exe must be
available to this process and its children.
If a sandbox hides rg.exe from child processes, run this script in a host
PowerShell session. The script does not elevate itself or change global Git
configuration.
#>
[CmdletBinding()]
param(
    [string]$Shard = 'all',
    [ValidateRange(1, 4)][int]$Workers = 1
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
    'dependency_locked_sources', 'dependency_sources', 'file_identity', 'jar_build',
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

. (Join-Path $PSScriptRoot 'bounded-library-plan.ps1')
. (Join-Path $PSScriptRoot 'bounded-process-pool.ps1')

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
    $script:boundedPackageId = $packages[0].id
    $script:boundedFeatureNames = @($packages[0].features.PSObject.Properties.Name | Sort-Object)
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

    # Derive child shards from the actual list, then verify their real libtest
    # substring/skip semantics cover every listed test exactly once.
    $plan = New-BoundedLibraryPlan -TestNames @($names.ToArray()) -ModuleRoots $moduleRoots -Fixture $fixture -StandaloneTests $standaloneTests
    Write-Host ("Library partition: {0} listed tests across {1} unsplit modules, {2} source_projection children ({3} tests), {4} standalone tests and one dedicated fixture." -f
        $plan.ListedTestCount, $plan.UnsplitModuleCount,
        $plan.SourceProjectionChildren.Count, $plan.SourceProjectionTestCount,
        $plan.StandaloneTestCount)
    return $plan
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

function Get-BoundedTestExecutables {
    # Build once, then execute Cargo's exact current all-feature artifacts.
    # Running cargo per concurrent shard can otherwise introduce build locks.
    $artifacts = @{}
    $arguments = @('test', '--offline', '--locked', '--all-features',
        '--manifest-path', $manifest, '--lib', '--tests', '--no-run', '--message-format=json')
    & cargo @arguments 2>&1 | ForEach-Object {
        $line = $_.ToString()
        if ($line -match $diskErrorPattern) {
            throw "Disk-space error compiling test artifacts: $line. Stop and wait for the user."
        }
        if (-not $line.StartsWith('{')) { Write-Host $line; return }
        $message = $line | ConvertFrom-Json
        if ($message.reason -ne 'compiler-artifact' -or
            $message.package_id -cne $script:boundedPackageId -or
            -not $message.profile.test -or $null -eq $message.executable) { return }
        $actualFeatures = @($message.features | Sort-Object)
        if (($actualFeatures -join ',') -cne ($script:boundedFeatureNames -join ',')) {
            throw 'Test executable does not enable the exact all-feature package set.'
        }
        $key = if ($message.target.kind -contains 'lib') {
            'lib'
        } elseif ($message.target.kind -contains 'test' -and $integrationTargets -ccontains $message.target.name) {
            "integration:$($message.target.name)"
        } else { return }
        if ($artifacts.ContainsKey($key)) { throw "Duplicate test artifact '$key'." }
        if (-not (Test-Path -LiteralPath $message.executable -PathType Leaf)) {
            throw "Cargo test executable is missing for '$key'."
        }
        $artifacts[$key] = $message.executable
    }
    if ($LASTEXITCODE -ne 0) { throw "Test artifact compilation failed (exit $LASTEXITCODE)." }
    foreach ($key in @('lib') + @($integrationTargets | ForEach-Object { "integration:$_" })) {
        if (-not $artifacts.ContainsKey($key)) { throw "Cargo omitted test artifact '$key'." }
    }
    return $artifacts
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

    if ($Shard -eq 'all' -or $Workers -gt 1) {
        Assert-IntegrationTargets
    }
    $needsLibraryPlan = $Shard -eq 'all' -or $Shard -eq 'unit:source_projection' -or
        $Shard.StartsWith('unit:source_projection:', [System.StringComparison]::OrdinalIgnoreCase)
    if ($needsLibraryPlan) {
        $plan = Assert-LibraryPartition
        $libraryDescriptors = @($plan.Jobs)
    } else {
        # Keep focused non-source_projection jobs on the existing no-list path.
        $libraryDescriptors = @(Get-BoundedConfiguredLibraryJobs -ModuleRoots $moduleRoots -Fixture $fixture -StandaloneTests $standaloneTests)
    }
    $selectedLibrary = @(Select-BoundedLibraryJobs -Jobs $libraryDescriptors -Shard $Shard)
    $jobs = [System.Collections.Generic.List[object]]::new()
    foreach ($descriptor in @($selectedLibrary | Where-Object { $_.Kind -eq 'module' })) {
        $cargoArgs = @(New-BoundedCargoLibraryArguments -CommonCargoArguments $commonCargoArgs -Job $descriptor)
        $jobs.Add([pscustomobject]@{ Name = $descriptor.Name; Arguments = $cargoArgs })
    }
    foreach ($target in $integrationTargets) {
        $name = "integration:$target"
        if ($Shard -ne 'all' -and $Shard -ne $name) { continue }
        $cargoArgs = $commonCargoArgs + @('--test', $target, '--', '--test-threads=1')
        $jobs.Add([pscustomobject]@{ Name = $name; Arguments = $cargoArgs })
    }
    foreach ($kind in @('standalone', 'fixture')) {
        foreach ($descriptor in @($selectedLibrary | Where-Object { $_.Kind -eq $kind })) {
            $cargoArgs = @(New-BoundedCargoLibraryArguments -CommonCargoArguments $commonCargoArgs -Job $descriptor)
            $jobs.Add([pscustomobject]@{ Name = $descriptor.Name; Arguments = $cargoArgs })
        }
    }
    if ($jobs.Count -eq 0) {
        throw "Unknown shard '$Shard'. Use all, fixture, unit:<module>, unit:source_projection:<listed-child>, or integration:<target>."
    }
    if ($Workers -eq 1) {
        for ($index = 0; $index -lt $jobs.Count; $index++) {
            $job = $jobs[$index]
            Invoke-CargoShard -Name $job.Name -Arguments $job.Arguments `
                -Index ($index + 1) -Total $jobs.Count
        }
    } else {
        # Initial reviewed read-only group: fixture loads inspect catalog,
        # source bytes and immutable Git blobs; mutation probes use local
        # strings only. Everything else remains exclusive until reviewed.
        $parallelNames = @(
            'unit:source_projection:core_theme_keyboard_models_slice_tests',
            'unit:source_projection:core_theme_preview_five_slice_tests',
            'unit:source_projection:core_theme_preview_models_slice_tests'
        )
        $executables = Get-BoundedTestExecutables
        $processJobs = @($jobs | ForEach-Object {
            $key = if ($_.Name.StartsWith('integration:', [System.StringComparison]::Ordinal)) {
                $_.Name
            } else { 'lib' }
            $separator = [array]::IndexOf([string[]]$_.Arguments, '--')
            if ($separator -lt 0 -or $separator -ge $_.Arguments.Count - 1) {
                throw "Missing libtest arguments for '$($_.Name)'."
            }
            $harnessArguments = @($_.Arguments[($separator + 1)..($_.Arguments.Count - 1)])
            if ($key -eq 'lib') {
                $library = [array]::IndexOf([string[]]$_.Arguments, '--lib')
                if ($library -lt 0 -or $library + 1 -ge $separator) {
                    throw "Missing library filter for '$($_.Name)'."
                }
                $harnessArguments = @($_.Arguments[$library + 1]) + $harnessArguments
            }
            [pscustomobject]@{
                Name = $_.Name
                Executable = $executables[$key]
                WorkingDirectory = $projectRoot
                Arguments = $harnessArguments
                ParallelSafe = $parallelNames -ccontains $_.Name
            }
        })
        $receipts = @(Invoke-BoundedProcessPool -Jobs $processJobs -Workers $Workers)
        if ($receipts.Count -ne $jobs.Count) { throw 'Process pool omitted test shards.' }
    }
    Write-Host "PASS: $($jobs.Count) test shards completed."
} finally {
    foreach ($key in $savedEnvironment.Keys) {
        [System.Environment]::SetEnvironmentVariable(
            $key, $savedEnvironment[$key], 'Process'
        )
    }
}
