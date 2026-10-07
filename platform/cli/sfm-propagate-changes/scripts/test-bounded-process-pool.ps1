[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'bounded-process-pool.ps1')
$executable = (Get-Process -Id $PID).Path
function New-ProbeJob {
    param([string]$Name, [bool]$ParallelSafe = $true, [string]$Code = '[System.Threading.Thread]::Sleep(500); Write-Output "test result: ok"')
    [pscustomobject]@{
        Name = $Name
        ParallelSafe = $ParallelSafe
        Executable = $executable
        WorkingDirectory = $PSScriptRoot
        Arguments = @('-NoLogo', '-NoProfile', '-NonInteractive', '-Command', $Code)
    }
}
function Assert-Probe {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}
$jobs = @((New-ProbeJob a), (New-ProbeJob b), (New-ProbeJob exclusive $false), (New-ProbeJob c), (New-ProbeJob d))
$receipts = @(Invoke-BoundedProcessPool -Jobs $jobs -Workers 2)
Assert-Probe ($receipts.Count -eq 5) 'Every job must finish exactly once.'
Assert-Probe (@($receipts.Name | Sort-Object -Unique).Count -eq 5) 'Duplicate completion.'
$a = $receipts | Where-Object Name -eq a
$b = $receipts | Where-Object Name -eq b
$exclusive = $receipts | Where-Object Name -eq exclusive
Assert-Probe ($b.StartTick -lt $a.EndTick -and $a.StartTick -lt $b.EndTick) 'Safe jobs did not overlap.'
foreach ($receipt in $receipts | Where-Object Name -ne exclusive) {
    Assert-Probe ($receipt.EndTick -le $exclusive.StartTick -or $receipt.StartTick -ge $exclusive.EndTick) 'Exclusive job overlapped another job.'
}
Assert-Probe (@($receipts | Where-Object Summary -ne 'test result: ok').Count -eq 0) 'Summary capture changed.'
foreach ($receipt in $receipts) {
    Assert-Probe ((Get-Content -LiteralPath $receipt.DiagnosticPath -Raw).Contains('[stdout] test result: ok')) 'Successful output log missing.'
}
Write-Host 'PASS exact-once, two-worker overlap, exclusive barrier and stream capture'

foreach ($failure in @(
    @{ Code = 'Write-Output "intentional failure"; exit 7'; Expected = 'failed (exit 7)' },
    @{ Code = '[Console]::Error.WriteLine("os error 112"); [System.Threading.Thread]::Sleep(10000)'; Expected = 'Disk-space error' }
)) {
    # The disk message is synthetic; no real disk-space failure is induced.
    $caught = $false
    $started = [System.Collections.Generic.List[object]]::new()
    try {
        $null = Invoke-BoundedProcessPool -Jobs @(
            (New-ProbeJob failure $true $failure.Code),
            (New-ProbeJob sibling $true '[System.Threading.Thread]::Sleep(10000)'),
            (New-ProbeJob must_not_start $true 'throw "should not launch"')
        ) -Workers 2 -OnStarted {
            param($name, $processId)
            $started.Add([pscustomobject]@{ Name = $name; Id = $processId })
        }
    } catch {
        $caught = $_.Exception.Message.Contains($failure.Expected)
        if (-not $caught) { throw }
    }
    Assert-Probe $caught "Missing failure: $($failure.Expected)"
    Assert-Probe (@($started | Where-Object Name -eq must_not_start).Count -eq 0) 'Queued job launched after failure.'
    foreach ($child in $started) {
        Assert-Probe ($null -eq (Get-Process -Id $child.Id -ErrorAction SilentlyContinue)) "Owned child $($child.Id) remains alive after failure."
    }
}
Write-Host 'PASS nonzero exit and synthetic disk error stop queue and reap owned siblings'

$burstDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ('sfm-pool-burst-proof-' + [guid]::NewGuid().ToString('N'))
$burstStarted = [System.Collections.Generic.List[string]]::new()
$burstFailure = $null
try {
    $null = Invoke-BoundedProcessPool -Jobs @(
        (New-ProbeJob burst $true '[Console]::Out.WriteLine("FIRST_CAUSE"); for ($n = 0; $n -lt 1000; $n++) { [Console]::Out.WriteLine("out-$n"); [Console]::Error.WriteLine("err-$n") }; [Console]::Error.WriteLine("LAST_CAUSE"); exit 9'),
        (New-ProbeJob sibling $true '[System.Threading.Thread]::Sleep(10000)'),
        (New-ProbeJob must_not_start $true 'throw "should not launch"')
    ) -Workers 2 -DiagnosticDirectory $burstDirectory -OnStarted {
        param($name, $processId)
        $burstStarted.Add($name)
    }
} catch { $burstFailure = $_.Exception.Message }
Assert-Probe ($null -ne $burstFailure -and $burstFailure.Contains('failed (exit 9)')) 'Burst exit not reported.'
Assert-Probe (-not $burstStarted.Contains('must_not_start')) 'Burst failure admitted queued work.'
$burstLog = @(Get-ChildItem -LiteralPath $burstDirectory -Filter '0000-*.log')
Assert-Probe ($burstLog.Count -eq 1) 'Expected one create-only burst diagnostic.'
$burstLines = @(Get-Content -LiteralPath $burstLog[0].FullName)
Assert-Probe ($burstLines.Count -eq 2002) 'Terminal pipes were not completely drained.'
Assert-Probe ($burstLines -contains '[stdout] FIRST_CAUSE') 'First cause lost outside bounded tail.'
Assert-Probe ($burstLines -contains '[stderr] LAST_CAUSE') 'Last stderr cause lost at process exit.'
Assert-Probe ($burstFailure.Contains($burstLog[0].FullName)) 'Failure omitted full diagnostic path.'
Write-Host 'PASS burst failure preserves every stdout/stderr line, first and last causes, and queue stop'
