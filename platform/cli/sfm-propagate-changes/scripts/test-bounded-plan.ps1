<#
.SYNOPSIS
Run synthetic, in-memory tests for bounded-library-plan.ps1.

.DESCRIPTION
No Cargo, compiler, JVM, external program, temporary filesystem fixture, or
project/cache write is used. These cases exercise the same pure helper loaded
by the proposed runner. They do not replace a real all-feature Cargo --list
partition check or the complete tooling gate.
#>
[CmdletBinding()]
param(
    [string]$HelperPath = (Join-Path $PSScriptRoot 'bounded-library-plan.ps1')
)

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
    if ($Expected -cne $Actual) {
        throw "$Message; expected '$Expected', actual '$Actual'."
    }
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
    try {
        & $Action
    } catch {
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
    param([string[]]$Names = $baseNames)
    New-BoundedLibraryPlan -TestNames $Names -ModuleRoots $roots -Fixture $fixture -StandaloneTests $standalone
}

Invoke-SyntheticCase 'immediate children are discovered and ordinally ordered' {
    $plan = New-SyntheticPlan
    Assert-Sequence @('alpha', 'tests', 'zeta') $plan.SourceProjectionChildren 'child order'
    Assert-Equal 8 $plan.ListedTestCount 'all library names'
    Assert-Equal 3 $plan.SourceProjectionTestCount 'source-projection count'
    Assert-Sequence @(
        'unit:cli', 'unit:jar_build',
        'unit:source_projection:alpha', 'unit:source_projection:tests',
        'unit:source_projection:zeta', 'unit:tests::standalone', 'fixture'
    ) @($plan.Jobs.Name) 'job order'
}

Invoke-SyntheticCase 'synthetic 1949-name inventory is covered exactly once' {
    $names = [System.Collections.Generic.List[string]]::new()
    foreach ($name in @($baseNames | Select-Object -First 5)) { $names.Add($name) }
    $childNames = @('zeta', 'alpha', 'tests')
    for ($index = 0; $index -lt 1944; $index++) {
        $child = $childNames[$index % $childNames.Count]
        $names.Add('source_projection::' + $child + '::tests::case_' + $index)
    }
    $plan = New-SyntheticPlan -Names @($names.ToArray())
    Assert-Equal 1949 $plan.ListedTestCount 'listed count'
    Assert-Equal 1944 $plan.SourceProjectionTestCount 'split source count'
    $covered = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::Ordinal
    )
    foreach ($job in $plan.Jobs) {
        foreach ($name in $job.TestNames) {
            Assert-True ($covered.Add($name)) "Duplicate covered test '$name'."
        }
    }
    Assert-Equal 1949 $covered.Count 'exact-once union count'
    Assert-True ($covered.SetEquals($names)) 'The planner omitted or introduced a test.'
}

Invoke-SyntheticCase 'legacy source-projection alias expands to all child jobs' {
    $plan = New-SyntheticPlan
    $selected = @(Select-BoundedLibraryJobs -Jobs $plan.Jobs -Shard 'unit:source_projection')
    Assert-Sequence @(
        'unit:source_projection:alpha', 'unit:source_projection:tests',
        'unit:source_projection:zeta'
    ) @($selected.Name) 'alias selection'
    Assert-Equal 3 (@($selected.TestNames).Count) 'alias test union'
    $uppercaseAlias = @(Select-BoundedLibraryJobs -Jobs $plan.Jobs -Shard 'UNIT:SOURCE_PROJECTION')
    Assert-Sequence @($selected.Name) @($uppercaseAlias.Name) 'legacy alias case behavior'
}

Invoke-SyntheticCase 'child focus is exact and future test-list children are discovered' {
    $plan = New-SyntheticPlan -Names ($baseNames + @(
        'source_projection::newly_registered_child::tests::future'
    ))
    $selected = @(Select-BoundedLibraryJobs -Jobs $plan.Jobs -Shard 'unit:source_projection:newly_registered_child')
    Assert-Equal 1 $selected.Count 'new child shard count'
    Assert-Sequence @('source_projection::newly_registered_child::tests::future') $selected[0].TestNames 'new child test'
    Assert-Equal 0 (@(Select-BoundedLibraryJobs -Jobs $plan.Jobs -Shard 'unit:source_projection:unknown').Count) 'unknown child is not silently substituted'
}

Invoke-SyntheticCase 'new child selectors preserve ordinal module identity' {
    $plan = New-SyntheticPlan -Names ($baseNames + @(
        'source_projection::Alpha::tests::distinct'
    ))
    Assert-Sequence @('Alpha', 'alpha', 'tests', 'zeta') $plan.SourceProjectionChildren 'ordinal children'
    $selected = @(Select-BoundedLibraryJobs -Jobs $plan.Jobs -Shard 'unit:source_projection:alpha')
    Assert-Equal 1 $selected.Count 'ordinal focus count'
    Assert-Equal 'unit:source_projection:alpha' $selected[0].Name 'ordinal focus'
}

Invoke-SyntheticCase 'cli overlap and fixture skips retain old semantics' {
    $plan = New-SyntheticPlan
    $cli = @($plan.Jobs | Where-Object { $_.Name -ceq 'unit:cli' })[0]
    Assert-Sequence @('cli::tests::base', 'cli::source_projection::tests::cli_shadow') $cli.TestNames 'cli tests'
    Assert-Sequence @($fixture) $cli.Skips 'cli skip'
    foreach ($child in @($plan.Jobs | Where-Object { $_.Name.StartsWith('unit:source_projection:') })) {
        Assert-Sequence @('cli::source_projection::') $child.Skips 'source child skip'
        Assert-True (-not ($child.TestNames -contains 'cli::source_projection::tests::cli_shadow')) 'A child duplicated the cli shadow.'
    }
    Assert-Sequence @($fixture) @($plan.Jobs | Where-Object { $_.Kind -ceq 'fixture' }).TestNames 'fixture exact membership'
}

Invoke-SyntheticCase 'other focused jobs keep configured filters without listing' {
    $configured = @(Get-BoundedConfiguredLibraryJobs -ModuleRoots $roots -Fixture $fixture -StandaloneTests $standalone)
    Assert-Sequence @('unit:cli', 'unit:jar_build', 'unit:tests::standalone', 'fixture') @($configured.Name) 'configured non-source jobs'
    $jar = @(Select-BoundedLibraryJobs -Jobs $configured -Shard 'unit:jar_build')
    Assert-Equal 1 $jar.Count 'jar focus'
    Assert-Equal 'jar_build::' $jar[0].Filter 'old jar filter'
    Assert-Sequence @('cli::jar_build::') $jar[0].Skips 'old jar skip'
    Assert-Equal 0 (@(Select-BoundedLibraryJobs -Jobs $configured -Shard 'unit:source_projection').Count) 'alias cannot select a legacy monolith'
    Assert-Equal 1 (@(Select-BoundedLibraryJobs -Jobs $configured -Shard 'UNIT:CLI').Count) 'old case-insensitive cli selector'
    Assert-Equal 1 (@(Select-BoundedLibraryJobs -Jobs $configured -Shard 'fixture').Count) 'fixture focus'
    Assert-Equal 1 (@(Select-BoundedLibraryJobs -Jobs $configured -Shard 'unit:tests::standalone').Count) 'standalone focus'
}

Invoke-SyntheticCase 'Cargo wire retains all features, default ignores and serial threads' {
    $common = @(
        'test', '--offline', '--locked', '--all-features', '--quiet',
        '--manifest-path', '<selected-manifest>'
    )
    $plan = New-SyntheticPlan
    foreach ($job in $plan.Jobs) {
        $wire = @(New-BoundedCargoLibraryArguments -CommonCargoArguments $common -Job $job)
        Assert-Sequence $common @($wire[0..($common.Count - 1)]) 'common Cargo prefix'
        Assert-Equal '--lib' $wire[$common.Count] 'lib selection'
        Assert-Equal $job.Filter $wire[$common.Count + 1] 'exact filter wire'
        Assert-Equal '--' $wire[$common.Count + 2] 'libtest separator'
        Assert-True ($wire -contains '--test-threads=1') 'The job no longer uses one libtest thread.'
        Assert-True (-not ($wire -contains '--ignored')) 'The job enabled ignored tests.'
        Assert-True (-not ($wire -contains '--include-ignored')) 'The job included ignored tests.'
        Assert-Equal ([bool]$job.Exact) ([bool]($wire -contains '--exact')) 'exact-only wire'
        foreach ($skip in $job.Skips) {
            $position = [Array]::IndexOf($wire, $skip)
            Assert-True ($position -gt 0 -and $wire[$position - 1] -ceq '--skip') 'A skip is not passed as a libtest skip.'
        }
    }
}

Invoke-SyntheticCase 'thread budget reaches libtest without changing coverage' {
    $job = (New-SyntheticPlan).Jobs[0]
    $wire = @(New-BoundedCargoLibraryArguments -CommonCargoArguments @('test') -Job $job -TestThreads 8)
    Assert-True ($wire -contains '--test-threads=8') 'Requested thread budget was lost.'
    Assert-True (-not ($wire -contains '--test-threads=1')) 'Serial flag overrides the requested budget.'
    Assert-Equal $job.Filter $wire[2] 'Concurrency changed the selected test filter.'
}

Invoke-SyntheticCase 'ignored names remain in coverage, not omitted by the plan' {
    $ignoredName = 'source_projection::tests::intentionally_ignored_case'
    $plan = New-SyntheticPlan -Names ($baseNames + @($ignoredName))
    $selected = @(Select-BoundedLibraryJobs -Jobs $plan.Jobs -Shard 'unit:source_projection:tests')
    Assert-True ($selected[0].TestNames -contains $ignoredName) 'Coverage silently dropped an ignored test.'
    # The real --list format does not expose ignore metadata. The runner neither
    # removes names nor adds --ignored/--include-ignored, so libtest retains it.
}

Invoke-SyntheticCase 'empty and duplicate listings fail closed' {
    Assert-Throws { New-SyntheticPlan -Names @() } 'listing is empty'
    Assert-Throws { New-SyntheticPlan -Names ($baseNames + @($baseNames[0])) } 'duplicate test'
}

Invoke-SyntheticCase 'omitted and overlapping tests fail closed' {
    Assert-Throws { New-SyntheticPlan -Names ($baseNames + @('new_unconfigured_root::tests::a')) } 'omitted or overlapping'
    Assert-Throws { New-SyntheticPlan -Names ($baseNames + @('jar_build::cli::tests::overlap')) } 'omitted or overlapping'
}

Invoke-SyntheticCase 'non-root source-projection substring collisions fail closed' {
    Assert-Throws {
        New-SyntheticPlan -Names ($baseNames + @('jar_build::source_projection::alpha::tests::collision'))
    } 'substring collision'
    Assert-Throws {
        New-SyntheticPlan -Names ($baseNames + @('source_projection::alpha::source_projection::zeta::tests::collision'))
    } 'substring collision'
}

Invoke-SyntheticCase 'malformed immediate-module names fail closed' {
    Assert-Throws { New-SyntheticPlan -Names ($baseNames + @('source_projection::unscoped_test')) } 'immediate source_projection child'
    Assert-Throws { New-SyntheticPlan -Names ($baseNames + @('source_projection::::test')) } 'immediate source_projection child'
    Assert-Throws { New-SyntheticPlan -Names ($baseNames + @('source_projection::empty::')) } 'immediate source_projection child'
}

Invoke-SyntheticCase 'missing dedicated or standalone tests fail closed' {
    Assert-Throws { New-SyntheticPlan -Names @($baseNames | Where-Object { $_ -cne $fixture }) } 'dedicated promotion fixture'
    Assert-Throws { New-SyntheticPlan -Names @($baseNames | Where-Object { $_ -cne $standalone[0] }) } 'standalone library test'
}

Invoke-SyntheticCase 'duplicate roots and standalone overlap fail closed' {
    Assert-Throws {
        New-BoundedLibraryPlan -TestNames $baseNames -ModuleRoots ($roots + @('cli')) -Fixture $fixture -StandaloneTests $standalone
    } 'duplicate root'
    Assert-Throws {
        New-BoundedLibraryPlan -TestNames $baseNames -ModuleRoots @('cli', 'jar_build') -Fixture $fixture -StandaloneTests $standalone
    } 'must contain source_projection exactly once'
    Assert-Throws {
        New-BoundedLibraryPlan -TestNames $baseNames -ModuleRoots $roots -Fixture $fixture -StandaloneTests @($standalone[0], $standalone[0])
    } 'omitted or overlapping'
}

Write-Host "PASS: $script:passed synthetic planner cases completed. No Cargo or other external tool ran."
