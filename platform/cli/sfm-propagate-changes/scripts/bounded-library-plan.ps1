<#
.SYNOPSIS
Create a pure, exact-once library-test shard plan from Cargo's current test list.

.DESCRIPTION
This file defines functions only. It does not invoke Cargo, create fixtures,
change environment variables, or start processes. The runner owns test listing
and execution. Only source_projection is split, by its actual immediate child
module; all other configured library filters retain their existing semantics.
#>

function New-BoundedModuleJob {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Root,
        [Parameter(Mandatory)][string]$Fixture
    )

    $skips = if ($Root -ceq 'cli') { @($Fixture) } else { @('cli::' + $Root + '::') }
    [pscustomobject]@{
        Name = "unit:$Root"
        Kind = 'module'
        Filter = $Root + '::'
        Exact = $false
        Skips = @($skips)
        TestNames = @()
    }
}

function New-BoundedExactJob {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Kind,
        [Parameter(Mandatory)][string]$Test
    )

    [pscustomobject]@{
        Name = $Name
        Kind = $Kind
        Filter = $Test
        Exact = $true
        Skips = @()
        TestNames = @()
    }
}

function Get-BoundedConfiguredLibraryJobs {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string[]]$ModuleRoots,
        [Parameter(Mandatory)][string]$Fixture,
        [Parameter(Mandatory)][AllowEmptyCollection()][string[]]$StandaloneTests
    )

    # Focused non-source_projection runs preserve the old no-listing path.
    # The source_projection alias must use the discovered, verified plan instead.
    foreach ($root in $ModuleRoots) {
        if ($root -cne 'source_projection') {
            New-BoundedModuleJob -Root $root -Fixture $Fixture
        }
    }
    foreach ($test in $StandaloneTests) {
        New-BoundedExactJob -Name "unit:$test" -Kind 'standalone' -Test $test
    }
    New-BoundedExactJob -Name 'fixture' -Kind 'fixture' -Test $Fixture
}

function New-BoundedLibraryPlan {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][AllowEmptyCollection()][string[]]$TestNames,
        [Parameter(Mandatory)][string[]]$ModuleRoots,
        [Parameter(Mandatory)][string]$Fixture,
        [Parameter(Mandatory)][AllowEmptyCollection()][string[]]$StandaloneTests
    )

    if ($TestNames.Count -eq 0) { throw 'The library test listing is empty.' }
    $roots = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::Ordinal
    )
    foreach ($root in $ModuleRoots) {
        if ([string]::IsNullOrEmpty($root) -or -not $roots.Add($root)) {
            throw 'The configured library module list contains an empty or duplicate root.'
        }
    }
    if (-not $roots.Contains('source_projection')) {
        throw 'The configured library modules must contain source_projection exactly once.'
    }

    $coverage = [System.Collections.Generic.Dictionary[string, int]]::new(
        [System.StringComparer]::Ordinal
    )
    $children = [System.Collections.Generic.SortedSet[string]]::new(
        [System.StringComparer]::Ordinal
    )
    $prefix = 'source_projection::'
    $comparison = [System.StringComparison]::Ordinal
    $sourceProjectionCount = 0
    foreach ($name in $TestNames) {
        if ([string]::IsNullOrEmpty($name) -or $coverage.ContainsKey($name)) {
            throw "The library test list contains an empty or duplicate test: '$name'."
        }
        $coverage.Add($name, 0)
        if (-not $name.StartsWith($prefix, $comparison)) { continue }
        $remaining = $name.Substring($prefix.Length)
        $separator = $remaining.IndexOf('::', $comparison)
        if ($separator -le 0 -or $separator -eq ($remaining.Length - 2)) {
            throw "Cannot identify an immediate source_projection child module in '$name'."
        }
        [void]$children.Add($remaining.Substring(0, $separator))
        $sourceProjectionCount++
    }
    if ($children.Count -eq 0) {
        throw 'The library test list has no source_projection child-module tests.'
    }
    if (-not $coverage.ContainsKey($Fixture)) {
        throw 'The dedicated promotion fixture is missing from the library test list.'
    }
    foreach ($test in $StandaloneTests) {
        if (-not $coverage.ContainsKey($test)) {
            throw "The standalone library test '$test' is missing from the test list."
        }
    }

    $jobs = [System.Collections.Generic.List[object]]::new()
    foreach ($root in $ModuleRoots) {
        if ($root -cne 'source_projection') {
            $jobs.Add((New-BoundedModuleJob -Root $root -Fixture $Fixture))
            continue
        }
        foreach ($child in $children) {
            $jobs.Add([pscustomobject]@{
                Name = "unit:source_projection:$child"
                Kind = 'module'
                Filter = $prefix + $child + '::'
                Exact = $false
                # Preserve the previous root shard's cli namespace exclusion.
                Skips = @('cli::source_projection::')
                TestNames = @()
            })
        }
    }
    foreach ($test in $StandaloneTests) {
        $jobs.Add((New-BoundedExactJob -Name "unit:$test" -Kind 'standalone' -Test $test))
    }
    $jobs.Add((New-BoundedExactJob -Name 'fixture' -Kind 'fixture' -Test $Fixture))

    foreach ($job in $jobs) {
        $matched = [System.Collections.Generic.List[string]]::new()
        foreach ($name in $TestNames) {
            # Keep the hot coverage loop in-process without a PowerShell
            # function invocation for every job/name pair. These are libtest's
            # actual ordinal exact/substring/skip matching rules.
            $included = if ($job.Exact) {
                [string]::Equals($name, $job.Filter, $comparison)
            } else {
                $name.Contains($job.Filter, $comparison)
            }
            if (-not $included) { continue }
            foreach ($skip in $job.Skips) {
                if ($name.Contains($skip, $comparison)) {
                    $included = $false
                    break
                }
            }
            if (-not $included) { continue }
            if ($job.Name.StartsWith('unit:source_projection:', $comparison) -and
                -not $name.StartsWith($job.Filter, $comparison)) {
                throw "Source-projection substring collision in '$($job.Name)': '$name'."
            }
            $matched.Add($name)
            $coverage[$name]++
        }
        $job.TestNames = @($matched.ToArray())
    }
    $bad = @($coverage.GetEnumerator() | Where-Object { $_.Value -ne 1 })
    if ($bad.Count -ne 0) {
        $details = @($bad | Sort-Object Key | Select-Object -First 5 |
            ForEach-Object { "$($_.Value): $($_.Key)" })
        throw "Library test partition has $($bad.Count) omitted or overlapping tests: [$($details -join '; ')]."
    }

    [pscustomobject]@{
        Jobs = @($jobs.ToArray())
        ListedTestCount = $TestNames.Count
        SourceProjectionTestCount = $sourceProjectionCount
        SourceProjectionChildren = @($children)
        UnsplitModuleCount = $ModuleRoots.Count - 1
        StandaloneTestCount = $StandaloneTests.Count
    }
}

function Select-BoundedLibraryJobs {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Jobs,
        [Parameter(Mandatory)][string]$Shard
    )

    foreach ($job in $Jobs) {
        $sourceChild = $job.Name.StartsWith(
            'unit:source_projection:', [System.StringComparison]::Ordinal
        )
        $exactSelection = if ($sourceChild) {
            [string]::Equals($Shard, $job.Name, [System.StringComparison]::Ordinal)
        } else {
            # Preserve the old case-insensitive names for existing selectors.
            $Shard -eq $job.Name
        }
        if ($Shard -eq 'all' -or $exactSelection -or
            ($Shard -eq 'unit:source_projection' -and $sourceChild)) {
            $job
        }
    }
}

function New-BoundedCargoLibraryArguments {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string[]]$CommonCargoArguments,
        [Parameter(Mandatory)]$Job
    )

    $arguments = @($CommonCargoArguments) + @('--lib', $Job.Filter, '--')
    if ($Job.Exact) { $arguments += '--exact' }
    $arguments += '--test-threads=1'
    foreach ($skip in $Job.Skips) { $arguments += @('--skip', $skip) }
    $arguments
}
