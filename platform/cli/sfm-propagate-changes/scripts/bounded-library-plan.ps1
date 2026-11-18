<#
.SYNOPSIS
Create an exact-once library-test plan from the current compiled test list.

.DESCRIPTION
Defines pure planning functions only. Module ownership uses ordinal root prefixes,
not libtest substring filters. Execution uses the exact listed names, split into
bounded argument lists when a focused module contains many tests.
#>

function New-BoundedModuleJob {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Root)

    [pscustomobject]@{
        Name = "unit:$Root"
        Kind = 'module'
        Prefix = $Root + '::'
        ExactTest = $null
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
        Prefix = $null
        ExactTest = $Test
        TestNames = @()
    }
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
    $comparison = [System.StringComparison]::Ordinal
    $roots = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::Ordinal
    )
    foreach ($root in $ModuleRoots) {
        if ([string]::IsNullOrEmpty($root) -or $root.Contains('::', $comparison) -or
            -not $roots.Add($root)) {
            throw 'The configured library modules need unique, nonempty top-level roots.'
        }
    }
    if (-not $roots.Contains('source_projection')) {
        throw 'The configured library modules must contain source_projection exactly once.'
    }

    $reserved = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::Ordinal
    )
    foreach ($test in @($StandaloneTests) + @($Fixture)) {
        if ([string]::IsNullOrEmpty($test) -or -not $reserved.Add($test)) {
            throw 'Duplicate or overlapping standalone/fixture ownership.'
        }
    }
    $coverage = [System.Collections.Generic.Dictionary[string, int]]::new(
        [System.StringComparer]::Ordinal
    )
    $children = [System.Collections.Generic.SortedSet[string]]::new(
        [System.StringComparer]::Ordinal
    )
    $prefix = 'source_projection::'
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
            $jobs.Add((New-BoundedModuleJob -Root $root))
            continue
        }
        foreach ($child in $children) {
            $jobs.Add([pscustomobject]@{
                Name = "unit:source_projection:$child"
                Kind = 'module'
                Prefix = $prefix + $child + '::'
                ExactTest = $null
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
            $included = if ($job.Kind -ceq 'module') {
                $name.StartsWith($job.Prefix, $comparison) -and -not $reserved.Contains($name)
            } else {
                [string]::Equals($name, $job.ExactTest, $comparison)
            }
            if (-not $included) { continue }
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
            $Shard -eq $job.Name
        }
        if ($Shard -eq 'all' -or $exactSelection -or
            ($Shard -eq 'unit:source_projection' -and $sourceChild)) {
            $job
        }
    }
}

function New-BoundedLibraryExecutionJobs {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]$Job,
        [Parameter(Mandatory)][string]$Executable,
        [Parameter(Mandatory)][string]$WorkingDirectory,
        [ValidateRange(1, 256)][int]$TestThreads = 1,
        [ValidateRange(256, 24000)][int]$FilterCharacterBudget = 16000
    )

    # Windows limits a process command line to 32767 UTF-16 code units. Leave
    # room for the executable and options; charge quotes/separators per name.
    $chunks = [System.Collections.Generic.List[object]]::new()
    $names = [System.Collections.Generic.List[string]]::new()
    $characters = 0
    foreach ($name in $Job.TestNames) {
        $required = $name.Length + 3
        if ($required -gt $FilterCharacterBudget) {
            throw "Exact test name exceeds the process argument budget: '$name'."
        }
        if ($names.Count -gt 0 -and $characters + $required -gt $FilterCharacterBudget) {
            $chunks.Add([pscustomobject]@{ TestNames = @($names.ToArray()) })
            $names.Clear()
            $characters = 0
        }
        $names.Add($name)
        $characters += $required
    }
    if ($names.Count -gt 0) {
        $chunks.Add([pscustomobject]@{ TestNames = @($names.ToArray()) })
    }
    for ($index = 0; $index -lt $chunks.Count; $index++) {
        $name = if ($chunks.Count -eq 1) { $Job.Name } else { "$($Job.Name):batch-$($index + 1)" }
        [pscustomobject]@{
            Name = $name
            Executable = $Executable
            WorkingDirectory = $WorkingDirectory
            Arguments = @('--exact', "--test-threads=$TestThreads") + @($chunks[$index].TestNames)
            ParallelSafe = $true
        }
    }
}
