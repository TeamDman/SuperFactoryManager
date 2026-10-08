# Event-driven process pool. Callers must independently prove job isolation.
# ParallelSafe=false jobs run exclusively; every child is owned by this call.
function Invoke-BoundedProcessPool {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][object[]]$Jobs,
        [ValidateRange(1, 64)][int]$Workers = 2,
        [scriptblock]$OnStarted,
        [string]$DiagnosticDirectory
    )

    $active = [System.Collections.Generic.List[object]]::new()
    $completed = [System.Collections.Generic.List[object]]::new()
    $cursor = 0
    if ([string]::IsNullOrWhiteSpace($DiagnosticDirectory)) {
        $DiagnosticDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ('sfm-bounded-pool-' + [guid]::NewGuid().ToString('N'))
    }
    [void][System.IO.Directory]::CreateDirectory($DiagnosticDirectory)
    Write-Host "Complete per-job diagnostics: $DiagnosticDirectory"
    $failureSeen = $false
    $diskPattern = '(?i)no space left|not enough space|disk (?:is )?full|insufficient disk space|os error 112|0x80070070'
    try {
        while ($cursor -lt $Jobs.Count -or $active.Count -gt 0) {
            while (-not $failureSeen -and $cursor -lt $Jobs.Count -and $active.Count -lt $Workers) {
                $job = $Jobs[$cursor]
                if ($active.Count -gt 0 -and
                    (-not $job.ParallelSafe -or @($active | Where-Object { -not $_.Job.ParallelSafe }).Count -gt 0)) {
                    break
                }
                $start = [System.Diagnostics.ProcessStartInfo]::new()
                $start.FileName = $job.Executable
                $start.WorkingDirectory = $job.WorkingDirectory
                $start.UseShellExecute = $false
                $start.CreateNoWindow = $true
                $start.RedirectStandardOutput = $true
                $start.RedirectStandardError = $true
                foreach ($argument in $job.Arguments) { $start.ArgumentList.Add($argument) }
                $process = [System.Diagnostics.Process]::new()
                $process.StartInfo = $start
                # Names are display labels, not filenames. Create-only ordinal
                # logs retain all lines without retaining unbounded memory.
                $diagnosticPath = Join-Path $DiagnosticDirectory ('{0:D4}-{1}.log' -f $cursor, [guid]::NewGuid().ToString('N'))
                $diagnosticStream = [System.IO.FileStream]::new($diagnosticPath, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write, [System.IO.FileShare]::Read)
                $diagnosticWriter = [System.IO.StreamWriter]::new($diagnosticStream)
                try {
                    if (-not $process.Start()) { throw "Could not start $($job.Name)." }
                } catch {
                    $process.Dispose()
                    $diagnosticWriter.Dispose()
                    throw
                }
                $active.Add([pscustomobject]@{
                    Job = $job
                    Process = $process
                    ExitTask = $process.WaitForExitAsync()
                    ExitSeen = $false
                    OutputTask = $process.StandardOutput.ReadLineAsync()
                    ErrorTask = $process.StandardError.ReadLineAsync()
                    Tail = [System.Collections.Generic.Queue[string]]::new()
                    DiagnosticPath = $diagnosticPath
                    DiagnosticWriter = $diagnosticWriter
                    Summary = $null
                    Clock = [System.Diagnostics.Stopwatch]::StartNew()
                    StartTick = [System.Diagnostics.Stopwatch]::GetTimestamp()
                })
                Write-Host "START $($job.Name)"
                if ($null -ne $OnStarted) { & $OnStarted $job.Name $process.Id }
                $cursor++
                if (-not $job.ParallelSafe) { break }
            }

            # Wait for a stream line, stream closure or process exit, not a timer
            # or a repeatedly reread progress file. Drain both pipes concurrently.
            $events = [System.Collections.Generic.List[System.Threading.Tasks.Task]]::new()
            foreach ($item in $active) {
                if (-not $item.ExitSeen) { $events.Add($item.ExitTask) }
                if ($null -ne $item.OutputTask) { $events.Add($item.OutputTask) }
                if ($null -ne $item.ErrorTask) { $events.Add($item.ErrorTask) }
            }
            if ($events.Count -gt 0) {
                [void][System.Threading.Tasks.Task]::WhenAny($events.ToArray()).GetAwaiter().GetResult()
            }
            foreach ($item in @($active.ToArray())) {
                foreach ($property in @('OutputTask', 'ErrorTask')) {
                    $task = $item.$property
                    if ($null -eq $task -or -not $task.IsCompleted) { continue }
                    $line = $task.GetAwaiter().GetResult()
                    if ($null -eq $line) {
                        $item.$property = $null
                    } else {
                        $streamName = if ($property -eq 'OutputTask') { 'stdout' } else { 'stderr' }
                        $item.DiagnosticWriter.WriteLine("[$streamName] $line")
                        if ($item.Tail.Count -ge 40) { [void]$item.Tail.Dequeue() }
                        $item.Tail.Enqueue($line)
                        if ($line -match $diskPattern) {
                            throw "Disk-space error in $($item.Job.Name): $line. Stop and wait for the user."
                        }
                        if ($line -match 'test result:') { $item.Summary = $line }
                        $reader = if ($property -eq 'OutputTask') {
                            $item.Process.StandardOutput
                        } else { $item.Process.StandardError }
                        $item.$property = $reader.ReadLineAsync()
                    }
                }
                if ($item.ExitTask.IsCompleted -and -not $item.ExitSeen) {
                    [void]$item.ExitTask.GetAwaiter().GetResult()
                    $item.ExitSeen = $true
                    if ($item.Process.ExitCode -ne 0) {
                        # Stop admission now, but drain both terminal pipes
                        # before reporting: exit notification can beat output.
                        $failureSeen = $true
                    }
                }
                if ($item.ExitSeen -and $null -eq $item.OutputTask -and $null -eq $item.ErrorTask) {
                    $item.DiagnosticWriter.Flush()
                    if ($item.Process.ExitCode -ne 0) {
                        foreach ($line in $item.Tail) { Write-Host $line }
                        throw "$($item.Job.Name) failed (exit $($item.Process.ExitCode)). Complete output: $($item.DiagnosticPath). No further jobs will run."
                    }
                    $item.Clock.Stop()
                    $receipt = [pscustomobject]@{
                        Name = $item.Job.Name
                        Seconds = $item.Clock.Elapsed.TotalSeconds
                        Summary = $item.Summary
                        StartTick = $item.StartTick
                        EndTick = [System.Diagnostics.Stopwatch]::GetTimestamp()
                        DiagnosticPath = $item.DiagnosticPath
                    }
                    $completed.Add($receipt)
                    Write-Host ("PASS {0}: {1} (process wall {2:N3}s)" -f $receipt.Name, $receipt.Summary, $receipt.Seconds)
                    [void]$active.Remove($item)
                    $item.Process.Dispose()
                    $item.DiagnosticWriter.Dispose()
                }
            }
        }
        return $completed.ToArray()
    } finally {
        foreach ($item in $active) {
            try {
                if (-not $item.Process.HasExited) { $item.Process.Kill($true) }
                if (-not $item.Process.WaitForExit(5000)) {
                    throw "Owned child $($item.Process.Id) did not exit after termination."
                }
            } finally {
                $item.Process.Dispose()
                $item.DiagnosticWriter.Dispose()
            }
        }
    }
}
