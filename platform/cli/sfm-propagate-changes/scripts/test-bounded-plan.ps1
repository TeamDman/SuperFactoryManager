<#
.SYNOPSIS
Check the bounded library planner without Cargo or runtime fixtures.

.DESCRIPTION
Uses synthetic test inventories. No compiler, JVM, external test executable,
project/cache write or global environment change is performed.
#>
[CmdletBinding()]
param([string]$HelperPath = (Join-Path $PSScriptRoot 'bounded-library-plan.ps1'))

if ($PSVersionTable.PSVersion.Major -lt 7) {
    throw 'The synthetic planner tests require PowerShell 7 or later.'
}
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. $HelperPath

$script:passed = 0
function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}
function Assert-Equal {
    param($Expected, $Actual, [string]$Message)
    if ($Expected -cne $Actual) { throw "$Message; expected '$Expected', actual '$Actual'." }
}
function Assert-Sequence {
    param([string[]]$Expected, [string[]]$Actual, [string]$Message)
    Assert-Equal $Expected.Count $Actual.Count "$Message count"
    for ($index = 0; $index -lt $Expected.Count; $index++) {
        Assert-Equal $Expected[$index] $Actual[$index] "$Message at $index"
    }
}
function Assert-Throws {
    param([scriptblock]$Action, [string]$MessageFragment)
    try { & $Action } catch {
        if (-not $_.Exception.Message.Contains($MessageFragment, [System.StringComparison]::Ordinal)) {
            throw "Unexpected error; wanted '$MessageFragment', got '$($_.Exception.Message)'."
        }
        return
    }
    throw "Expected an error containing '$MessageFragment'."
}
function Invoke-SyntheticCase {
    param([string]$Name, [scriptblock]$Action)
    & $Action
    $script:passed++
    Write-Host "PASS $Name"
}
function Get-ExecutionJobs {
    param($Job, [int]$Budget = 16000, [int]$Threads = 1)
    New-BoundedLibraryExecutionJobs -Job $Job -Executable '<library-executable>' -WorkingDirectory '<project-root>' -FilterCharacterBudget $Budget -TestThreads $Threads
}

$roots = @('cli', 'jar_build', 'source_projection')
$fixture = 'cli::source::promotion_cli::tests::dedicated_fixture'
$standalone = @('tests::standalone')
$baseNames = @(
    'cli::tests::base',
    'cli::source_projection::tests::cli_shadow',
    'jar_build::tests::base',
    $standalone[0],
    $fixture,
    'source_projection::zeta::tests::z',
    'source_projection::alpha::tests::a',
    'source_projection::tests::direct_module_test'
)
function New-SyntheticPlan {
    param([string[]]$Names = $baseNames, [string[]]$Roots = $roots)
    New-BoundedLibraryPlan -TestNames $Names -ModuleRoots $Roots -Fixture $fixture -StandaloneTests $standalone
}

Invoke-SyntheticCase 'immediate source children are discovered in ordinal order' {
    $plan = New-SyntheticPlan
    Assert-Sequence @('alpha', 'tests', 'zeta') $plan.SourceProjectionChildren 'child order'
    Assert-Equal 8 $plan.ListedTestCount 'listed count'
    Assert-Equal 3 $plan.SourceProjectionTestCount 'source count'
    Assert-Sequence @(
        'unit:cli', 'unit:jar_build', 'unit:source_projection:alpha',
        'unit:source_projection:tests', 'unit:source_projection:zeta',
        'unit:tests::standalone', 'fixture'
    ) @($plan.Jobs.Name) 'job order'
}

Invoke-SyntheticCase 'synthetic 1949-name inventory is covered exactly once' {
    $names = [System.Collections.Generic.List[string]]::new()
    foreach ($name in @($baseNames | Select-Object -First 5)) { $names.Add($name) }
    $childNames = @('zeta', 'alpha', 'tests')
    for ($index = 0; $index -lt 1944; $index++) {
        $names.Add('source_projection::' + $childNames[$index % 3] + '::tests::case_' + $index)
    }
    $plan = New-SyntheticPlan -Names @($names.ToArray())
    $covered = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($job in $plan.Jobs) {
        foreach ($name in $job.TestNames) {
            Assert-True ($covered.Add($name)) "Duplicate ownership: $name"
        }
    }
    Assert-Equal 1949 $covered.Count 'covered count'
    Assert-True ($covered.SetEquals($names)) 'The plan omitted or introduced a test.'
}

Invoke-SyntheticCase 'nested and suffix module names keep their top-level owner' {
    $extra = @(
        'jar_build::cargo_source_jdk::tests::case',
        'jar_build::engine::junit_logging::tests::case',
        'jar_build::cli::tests::case',
        'jdk::tests::case', 'logging::tests::case',
        'jdk_install_control::tests::case', 'jdk_preferences::tests::case',
        'source_binding::tests::case', 'mod_id::tests::case', 'sfm_mod_id::tests::case'
    )
    $plan = New-SyntheticPlan -Names ($baseNames + $extra) -Roots ($roots + @(
        'jdk', 'logging', 'jdk_install_control', 'jdk_preferences', 'source_binding', 'mod_id', 'sfm_mod_id'
    ))
    $jdk = @(Select-BoundedLibraryJobs -Jobs $plan.Jobs -Shard 'unit:jdk')[0]
    Assert-Sequence @('jdk::tests::case') $jdk.TestNames 'jdk ownership'
    $logging = @(Select-BoundedLibraryJobs -Jobs $plan.Jobs -Shard 'unit:logging')[0]
    Assert-Sequence @('logging::tests::case') $logging.TestNames 'logging ownership'
    $mod = @(Select-BoundedLibraryJobs -Jobs $plan.Jobs -Shard 'unit:mod_id')[0]
    Assert-Sequence @('mod_id::tests::case') $mod.TestNames 'mod ownership'
    $jar = @(Select-BoundedLibraryJobs -Jobs $plan.Jobs -Shard 'unit:jar_build')[0]
    Assert-Sequence @($baseNames[2], $extra[0], $extra[1], $extra[2]) $jar.TestNames 'nested jar ownership'
}

Invoke-SyntheticCase 'nested source-projection spelling cannot select another child' {
    $foreign = 'jar_build::source_projection::alpha::tests::case'
    $nested = 'source_projection::alpha::source_projection::zeta::tests::case'
    $plan = New-SyntheticPlan -Names ($baseNames + @($foreign, $nested))
    $alpha = @(Select-BoundedLibraryJobs -Jobs $plan.Jobs -Shard 'unit:source_projection:alpha')[0]
    Assert-Sequence @($baseNames[6], $nested) $alpha.TestNames 'alpha ownership'
    $execution = @(Get-ExecutionJobs -Job $alpha)[0]
    Assert-Sequence @('--exact', '--test-threads=1', $baseNames[6], $nested) $execution.Arguments 'exact child execution'
}

Invoke-SyntheticCase 'reserved fixture and standalone tests have one owner' {
    $plan = New-BoundedLibraryPlan -TestNames $baseNames -ModuleRoots ($roots + @('tests')) -Fixture $fixture -StandaloneTests $standalone
    $cli = @(Select-BoundedLibraryJobs -Jobs $plan.Jobs -Shard 'unit:cli')[0]
    Assert-Sequence @($baseNames[0], $baseNames[1]) $cli.TestNames 'cli ownership'
    $generic = @(Select-BoundedLibraryJobs -Jobs $plan.Jobs -Shard 'unit:tests')[0]
    Assert-Equal 0 $generic.TestNames.Count 'reserved standalone leaked to generic tests root'
    Assert-Sequence @($fixture) @($plan.Jobs | Where-Object Kind -CEQ 'fixture')[0].TestNames 'fixture ownership'
}

Invoke-SyntheticCase 'legacy aliases retain their selector case behavior' {
    $plan = New-SyntheticPlan
    $selected = @(Select-BoundedLibraryJobs -Jobs $plan.Jobs -Shard 'UNIT:SOURCE_PROJECTION')
    Assert-Equal 3 $selected.Count 'source alias expansion'
    Assert-Equal 1 (@(Select-BoundedLibraryJobs -Jobs $plan.Jobs -Shard 'UNIT:CLI').Count) 'root selector case'
    Assert-Equal 1 (@(Select-BoundedLibraryJobs -Jobs $plan.Jobs -Shard 'fixture').Count) 'fixture selector'
    Assert-Equal 1 (@(Select-BoundedLibraryJobs -Jobs $plan.Jobs -Shard 'unit:tests::standalone').Count) 'standalone selector'
    Assert-Equal 0 (@(Select-BoundedLibraryJobs -Jobs $plan.Jobs -Shard 'unit:source_projection:unknown').Count) 'unknown child'
}

Invoke-SyntheticCase 'new child selectors preserve ordinal module identity' {
    $plan = New-SyntheticPlan -Names ($baseNames + @('source_projection::Alpha::tests::case'))
    Assert-Sequence @('Alpha', 'alpha', 'tests', 'zeta') $plan.SourceProjectionChildren 'ordinal child identity'
    $selected = @(Select-BoundedLibraryJobs -Jobs $plan.Jobs -Shard 'unit:source_projection:alpha')
    Assert-Equal 1 $selected.Count 'ordinal child selection'
    Assert-Equal 'unit:source_projection:alpha' $selected[0].Name 'ordinal selected name'
}

Invoke-SyntheticCase 'exact arguments preserve thread budgets and default ignored behavior' {
    foreach ($job in (New-SyntheticPlan).Jobs) {
        $execution = @(Get-ExecutionJobs -Job $job -Threads 8)
        Assert-Equal 1 $execution.Count 'small job count'
        Assert-Equal '<library-executable>' $execution[0].Executable 'executable identity'
        Assert-Equal '<project-root>' $execution[0].WorkingDirectory 'working directory'
        Assert-Sequence (@('--exact', '--test-threads=8') + $job.TestNames) $execution[0].Arguments 'exact libtest arguments'
        Assert-True (-not ($execution[0].Arguments -contains '--ignored')) 'Ignored tests enabled.'
        Assert-True (-not ($execution[0].Arguments -contains '--include-ignored')) 'Ignored tests included.'
        Assert-True (-not ($execution[0].Arguments -contains '--skip')) 'Substring skips returned.'
    }
}

Invoke-SyntheticCase 'focused large modules split into bounded exact-name processes' {
    $names = @(1..80 | ForEach-Object { 'jar_build::tests::' + ('long_name_' * 4) + $_ })
    $plan = New-SyntheticPlan -Names ($baseNames + $names)
    $job = @(Select-BoundedLibraryJobs -Jobs $plan.Jobs -Shard 'unit:jar_build')[0]
    $execution = @(Get-ExecutionJobs -Job $job -Budget 256)
    Assert-True ($execution.Count -gt 1) 'Large focused module was not split.'
    $seen = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($process in $execution) {
        Assert-True ($process.Name.StartsWith('unit:jar_build:batch-')) 'Batch identity lost.'
        $charged = 0
        foreach ($name in $process.Arguments[2..($process.Arguments.Count - 1)]) {
            Assert-True ($seen.Add($name)) 'Exact test repeated across processes.'
            $charged += $name.Length + 3
        }
        Assert-True ($charged -le 256) 'Process exceeds configured filter budget.'
    }
    Assert-True ($seen.SetEquals([string[]]$job.TestNames)) 'Chunking changed exact test membership.'
}

Invoke-SyntheticCase 'empty configured modules produce no false test process' {
    $plan = New-SyntheticPlan -Roots ($roots + @('empty'))
    $job = @(Select-BoundedLibraryJobs -Jobs $plan.Jobs -Shard 'unit:empty')[0]
    Assert-Equal 0 (@(Get-ExecutionJobs -Job $job).Count) 'An empty module launched a zero-test process.'
}

Invoke-SyntheticCase 'an individual oversized name fails before execution' {
    $name = 'jar_build::tests::' + ('a' * 300)
    $plan = New-SyntheticPlan -Names ($baseNames + @($name))
    $job = @(Select-BoundedLibraryJobs -Jobs $plan.Jobs -Shard 'unit:jar_build')[0]
    Assert-Throws { Get-ExecutionJobs -Job $job -Budget 256 } 'exceeds the process argument budget'
}

Invoke-SyntheticCase 'ignored names remain in listed coverage' {
    $name = 'source_projection::tests::intentionally_ignored_case'
    $plan = New-SyntheticPlan -Names ($baseNames + @($name))
    $job = @(Select-BoundedLibraryJobs -Jobs $plan.Jobs -Shard 'unit:source_projection:tests')[0]
    Assert-True ($job.TestNames -contains $name) 'An ignored name disappeared from coverage.'
}

Invoke-SyntheticCase 'empty duplicate and unowned inventories fail closed' {
    Assert-Throws { New-SyntheticPlan -Names @() } 'listing is empty'
    Assert-Throws { New-SyntheticPlan -Names ($baseNames + @($baseNames[0])) } 'duplicate test'
    Assert-Throws { New-SyntheticPlan -Names ($baseNames + @('new_unconfigured_root::tests::case')) } 'omitted or overlapping'
}

Invoke-SyntheticCase 'malformed source children fail closed' {
    foreach ($name in @('source_projection::unscoped_test', 'source_projection::::test', 'source_projection::empty::')) {
        Assert-Throws { New-SyntheticPlan -Names ($baseNames + @($name)) } 'immediate source_projection child'
    }
}

Invoke-SyntheticCase 'missing exact tests fail closed' {
    Assert-Throws { New-SyntheticPlan -Names @($baseNames | Where-Object { $_ -cne $fixture }) } 'dedicated promotion fixture'
    Assert-Throws { New-SyntheticPlan -Names @($baseNames | Where-Object { $_ -cne $standalone[0] }) } 'standalone library test'
}

Invoke-SyntheticCase 'overlapping configuration fails before execution' {
    Assert-Throws { New-SyntheticPlan -Roots ($roots + @('cli')) } 'unique, nonempty top-level roots'
    Assert-Throws { New-SyntheticPlan -Roots ($roots + @('cli::nested')) } 'unique, nonempty top-level roots'
    Assert-Throws { New-SyntheticPlan -Roots @('cli', 'jar_build') } 'must contain source_projection exactly once'
    Assert-Throws {
        New-BoundedLibraryPlan -TestNames $baseNames -ModuleRoots $roots -Fixture $fixture -StandaloneTests @($standalone[0], $standalone[0])
    } 'overlapping standalone/fixture ownership'
    Assert-Throws {
        New-BoundedLibraryPlan -TestNames $baseNames -ModuleRoots $roots -Fixture $fixture -StandaloneTests @($standalone[0], $fixture)
    } 'overlapping standalone/fixture ownership'
}

Write-Host "PASS: $script:passed synthetic planner cases completed. No Cargo or runtime test executable ran."
