param(
    [Parameter(Mandatory = $true)]
    [string] $Executable,
    [Parameter(Mandatory = $true)]
    [string] $RepoRoot,
    [string] $Branch = "1.19.2",
    [string] $OutputPath
)

$ErrorActionPreference = "Stop"

function Write-Frame {
    param(
        [Parameter(Mandatory = $true)] $Stream,
        [Parameter(Mandatory = $true)] $Value
    )
    $json = $Value | ConvertTo-Json -Depth 100 -Compress
    $payload = [System.Text.Encoding]::UTF8.GetBytes($json)
    if ($payload.Length -gt [uint32]::MaxValue) {
        throw "Frame exceeds the u32 protocol limit."
    }
    $header = [System.BitConverter]::GetBytes([uint32] $payload.Length)
    if (-not [System.BitConverter]::IsLittleEndian) {
        [array]::Reverse($header)
    }
    $Stream.Write($header, 0, $header.Length)
    $Stream.Write($payload, 0, $payload.Length)
    $Stream.Flush()
}

function Read-Exact {
    param(
        [Parameter(Mandatory = $true)] $Stream,
        [Parameter(Mandatory = $true)] [int] $Count
    )
    $buffer = [byte[]]::new($Count)
    $offset = 0
    while ($offset -lt $Count) {
        $read = $Stream.Read($buffer, $offset, $Count - $offset)
        if ($read -eq 0) {
            throw "Symbol worker closed stdout while a frame was being read."
        }
        $offset += $read
    }
    return ,$buffer
}

function Read-Frame {
    param([Parameter(Mandatory = $true)] $Stream)
    $header = Read-Exact -Stream $Stream -Count 4
    if (-not [System.BitConverter]::IsLittleEndian) {
        [array]::Reverse($header)
    }
    $length = [System.BitConverter]::ToUInt32($header, 0)
    if ($length -gt 16MB) {
        throw "Worker returned an oversized frame: $length bytes."
    }
    $payload = Read-Exact -Stream $Stream -Count ([int] $length)
    $json = [System.Text.Encoding]::UTF8.GetString($payload)
    return $json | ConvertFrom-Json -Depth 100
}

function Get-Sha256ContentHash {
    param([Parameter(Mandatory = $true)] [string] $Text)
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text)
    $digest = [System.Security.Cryptography.SHA256]::HashData($bytes)
    return "sha256:$([System.Convert]::ToHexString($digest).ToLowerInvariant())"
}

function New-DefinitionRequest {
    param(
        [Parameter(Mandatory = $true)] [uint64] $RequestId,
        [Parameter(Mandatory = $true)] $Workspace,
        [Parameter(Mandatory = $true)] $Root,
        [Parameter(Mandatory = $true)] [string] $RelativePath,
        [Parameter(Mandatory = $true)] [string] $Pattern,
        [Parameter(Mandatory = $true)] [string] $Token
    )
    $nativeRelative = $RelativePath.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
    $nativePath = [System.IO.Path]::Combine($Root.canonical_absolute_path, $nativeRelative)
    $text = [System.IO.File]::ReadAllText($nativePath, [System.Text.Encoding]::UTF8)
    $match = $text.IndexOf($Pattern, [System.StringComparison]::Ordinal)
    if ($match -lt 0) {
        throw "Pattern '$Pattern' was not found in the selected benchmark source."
    }
    $tokenInPattern = $Pattern.IndexOf($Token, [System.StringComparison]::Ordinal)
    if ($tokenInPattern -lt 0) {
        throw "Token '$Token' is not part of pattern '$Pattern'."
    }
    $characterOffset = $match + $tokenInPattern + [Math]::Min(1, $Token.Length - 1)
    $prefix = $text.Substring(0, $characterOffset)
    $line = ([regex]::Matches($prefix, "`n").Count + 1)
    $lastNewline = $prefix.LastIndexOf("`n", [System.StringComparison]::Ordinal)
    $column = $characterOffset - $lastNewline
    $byteOffset = [System.Text.Encoding]::UTF8.GetByteCount($prefix)
    $hash = Get-Sha256ContentHash -Text $text
    $reportRoot = $Root.report_root_path.TrimEnd('/')
    $reportPath = if ($reportRoot.Length -eq 0) {
        $RelativePath
    } else {
        "$reportRoot/$RelativePath"
    }
    $request = [ordered]@{
        schema = "sfm.definition-at-position-request/2"
        request_id = $RequestId
        request_generation = 1
        workspace = $Workspace
        document = [ordered]@{
            address = "workspace://$($Root.root_id)/$RelativePath"
            root_id = $Root.root_id
            root_relative_path = $RelativePath
            report_path = $reportPath
            source_set = $Root.source_set
            text = $text
            content_hash = $hash
            disk_content_hash = $hash
        }
        position = [ordered]@{
            line = [uint64] $line
            column = [uint64] $column
            byte_offset = [uint64] $byteOffset
        }
    }
    return [pscustomobject] [ordered]@{
        request = $request
        label = "$($Root.source_set):$Token"
    }
}

function Invoke-Definition {
    param(
        [Parameter(Mandatory = $true)] $InputStream,
        [Parameter(Mandatory = $true)] $OutputStream,
        [Parameter(Mandatory = $true)] $Request
    )
    $frame = [ordered]@{
        kind = "definition"
        schema = "sfm.symbol-server.definition/1"
        request = $Request
    }
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    Write-Frame -Stream $InputStream -Value $frame
    $response = Read-Frame -Stream $OutputStream
    $watch.Stop()
    if ($response.kind -ne "definition-result") {
        throw "Expected definition-result, got '$($response.kind)': $($response | ConvertTo-Json -Depth 20 -Compress)"
    }
    return [pscustomobject] [ordered]@{
        label = ""
        elapsed_ms = [Math]::Round($watch.Elapsed.TotalMilliseconds, 3)
        outcome = $response.result.outcome
        completeness = $response.result.completeness
        definitions = @($response.result.definitions).Count
    }
}

function Get-Percentile {
    param(
        [Parameter(Mandatory = $true)] [double[]] $Values,
        [Parameter(Mandatory = $true)] [double] $Percentile
    )
    $ordered = @($Values | Sort-Object)
    $index = [Math]::Max(0, [Math]::Ceiling($Percentile * $ordered.Count) - 1)
    return [double] $ordered[$index]
}

function Get-DescendantProcessIds {
    param([Parameter(Mandatory = $true)] [uint32] $RootProcessId)
    $all = @(Get-CimInstance Win32_Process | Select-Object ProcessId, ParentProcessId)
    $pending = [System.Collections.Generic.Queue[uint32]]::new()
    $pending.Enqueue($RootProcessId)
    $descendants = [System.Collections.Generic.List[uint32]]::new()
    while ($pending.Count -gt 0) {
        $parent = $pending.Dequeue()
        foreach ($child in $all | Where-Object { $_.ParentProcessId -eq $parent }) {
            $id = [uint32] $child.ProcessId
            $descendants.Add($id)
            $pending.Enqueue($id)
        }
    }
    return @($descendants)
}

$startInfo = [System.Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = [System.IO.Path]::GetFullPath($Executable)
$startInfo.WorkingDirectory = [System.IO.Path]::GetFullPath($RepoRoot)
$startInfo.UseShellExecute = $false
$startInfo.CreateNoWindow = $true
$startInfo.RedirectStandardInput = $true
$startInfo.RedirectStandardOutput = $true
$startInfo.RedirectStandardError = $true
$startInfo.ArgumentList.Add("symbol")
$startInfo.ArgumentList.Add("serve")
$startInfo.ArgumentList.Add("--branch")
$startInfo.ArgumentList.Add($Branch)

$process = [System.Diagnostics.Process]::new()
$process.StartInfo = $startInfo
if (-not $process.Start()) {
    throw "Failed to start the installed symbol worker."
}
$stderrTask = $process.StandardError.ReadToEndAsync()
$stdin = $process.StandardInput.BaseStream
$stdout = $process.StandardOutput.BaseStream

try {
    Write-Frame -Stream $stdin -Value ([ordered]@{
        kind = "hello"
        schema = "sfm.symbol-server.hello/1"
        hello = [ordered]@{
            protocol_schema = "sfm.symbol-server/1"
            client_name = "phase-0.10-installed-probe"
            client_version = "1"
            capabilities = @(
                "definition-at-position",
                "cancellation",
                "workspace-generation",
                "ping",
                "shutdown"
            )
            max_frame_bytes = 16777216
        }
    })
    $helloFrame = Read-Frame -Stream $stdout
    if ($helloFrame.kind -ne "hello") {
        throw "Expected server hello, got '$($helloFrame.kind)'."
    }
    $workspace = $helloFrame.hello.workspace.request_workspace
    $roots = @($helloFrame.hello.workspace.roots)
    $mainRoot = $roots | Where-Object {
        $_.source_set -eq "main" -and
        (Test-Path -LiteralPath ([System.IO.Path]::Combine($_.canonical_absolute_path, "ca\teamdman\sfm\common\item\DiskItem.java")))
    } | Select-Object -First 1
    $gameTestRoot = $roots | Where-Object {
        $_.source_set -eq "gametest" -and
        (Test-Path -LiteralPath ([System.IO.Path]::Combine($_.canonical_absolute_path, "ca\teamdman\sfm\gametest\SFMGameTestHelper.java")))
    } | Select-Object -First 1
    if ($null -eq $mainRoot -or $null -eq $gameTestRoot) {
        throw "Worker hello did not expose the expected main and gametest roots."
    }
    $observedDescendants = [System.Collections.Generic.HashSet[uint32]]::new()
    foreach ($id in Get-DescendantProcessIds -RootProcessId ([uint32] $process.Id)) {
        [void] $observedDescendants.Add($id)
    }

    [uint64] $requestId = 1
    $targets = @(
        (New-DefinitionRequest -RequestId $requestId -Workspace $workspace -Root $mainRoot `
            -RelativePath "ca/teamdman/sfm/common/item/DiskItem.java" `
            -Pattern "class DiskItem" -Token "DiskItem"),
        (New-DefinitionRequest -RequestId ($requestId + 1) -Workspace $workspace -Root $gameTestRoot `
            -RelativePath "ca/teamdman/sfm/gametest/SFMGameTestHelper.java" `
            -Pattern "class SFMGameTestHelper" -Token "SFMGameTestHelper"),
        (New-DefinitionRequest -RequestId ($requestId + 2) -Workspace $workspace -Root $mainRoot `
            -RelativePath "ca/teamdman/sfm/common/util/SFMBlockPosUtils.java" `
            -Pattern "import net.minecraft.core.BlockPos;" -Token "BlockPos")
    )

    $cold = Invoke-Definition -InputStream $stdin -OutputStream $stdout -Request $targets[0].request
    $cold.label = $targets[0].label
    if ($cold.outcome -ne "success" -or $cold.definitions -lt 1) {
        throw "Cold definition query did not resolve a definition."
    }
    $requestId++
    $warm = [System.Collections.Generic.List[object]]::new()
    for ($index = 0; $index -lt 24; $index++) {
        $template = $targets[$index % $targets.Count]
        $template.request.request_id = $requestId
        $requestId++
        $sample = Invoke-Definition -InputStream $stdin -OutputStream $stdout -Request $template.request
        $sample.label = $template.label
        if ($sample.outcome -ne "success" -or $sample.definitions -lt 1) {
            throw "Warm definition query '$($sample.label)' did not resolve a definition."
        }
        $warm.Add($sample)
        $process.Refresh()
    }

    foreach ($id in Get-DescendantProcessIds -RootProcessId ([uint32] $process.Id)) {
        [void] $observedDescendants.Add($id)
    }

    $cancelTemplate = $targets[0]
    $cancelTemplate.request.request_id = $requestId
    $requestId++
    Write-Frame -Stream $stdin -Value ([ordered]@{
        kind = "cancel"
        schema = "sfm.symbol-server.cancel/1"
        request_id = $cancelTemplate.request.request_id
        request_generation = $cancelTemplate.request.request_generation
        workspace_generation = $workspace.workspace_generation
        reason = "installed probe cancellation"
    })
    Write-Frame -Stream $stdin -Value ([ordered]@{
        kind = "definition"
        schema = "sfm.symbol-server.definition/1"
        request = $cancelTemplate.request
    })
    $cancelResponses = @((Read-Frame -Stream $stdout), (Read-Frame -Stream $stdout))
    $cancelKinds = @($cancelResponses | ForEach-Object { $_.kind } | Sort-Object)
    if (($cancelKinds -join ',') -ne "cancelled,definition-cancelled") {
        throw "Pre-request cancellation did not produce exactly one acknowledgement and one terminal response."
    }

    Write-Frame -Stream $stdin -Value ([ordered]@{
        kind = "shutdown"
        schema = "sfm.symbol-server.shutdown/1"
        reason = "installed probe complete"
    })
    $shutdown = Read-Frame -Stream $stdout
    $process.StandardInput.Close()
    if (-not $process.WaitForExit(10000)) {
        $process.Kill($true)
        throw "Symbol worker did not exit within 10 seconds after clean shutdown."
    }
    if ($shutdown.kind -ne "shutdown") {
        throw "Symbol worker did not acknowledge clean shutdown."
    }
    if ($process.ExitCode -ne 0) {
        throw "Symbol worker exited with code $($process.ExitCode)."
    }
    $leakedDescendants = @($observedDescendants | Where-Object {
        Get-Process -Id $_ -ErrorAction SilentlyContinue
    })
    if ($leakedDescendants.Count -ne 0) {
        throw "Symbol worker left descendant processes alive: $($leakedDescendants -join ', ')."
    }
    $stderr = $stderrTask.GetAwaiter().GetResult()
    $elapsed = [double[]] @($warm | ForEach-Object { $_.elapsed_ms })
    $median = Get-Percentile -Values $elapsed -Percentile 0.50
    $p95 = Get-Percentile -Values $elapsed -Percentile 0.95
    $maximum = ($elapsed | Measure-Object -Maximum).Maximum
    $telemetry = @($stderr -split "`r?`n" | Where-Object {
        $_ -match "symbol-server definition (completed|failed)"
    })
    $report = [ordered]@{
        schema = "sfm.symbol-server-installed-probe/1"
        executable = [System.IO.Path]::GetFileName($startInfo.FileName)
        branch = $Branch
        cold = $cold
        warm_samples = $warm
        warm_summary = [ordered]@{
            count = $elapsed.Count
            median_ms = $median
            p95_ms = $p95
            maximum_ms = $maximum
            accepted = ($median -le 250 -and $p95 -le 750 -and $maximum -lt 1000)
        }
        cancellation_response_kinds = $cancelKinds
        shutdown_response_kind = $shutdown.kind
        process = [ordered]@{
            pid = $process.Id
            exited = $process.HasExited
            exit_code = $process.ExitCode
            peak_working_set_bytes = $process.PeakWorkingSet64
            observed_descendant_processes = $observedDescendants.Count
            leaked_descendant_processes = $leakedDescendants.Count
        }
        telemetry_lines = $telemetry
    }
    $json = $report | ConvertTo-Json -Depth 100
    if ($OutputPath) {
        $destination = [System.IO.Path]::GetFullPath($OutputPath)
        [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($destination)) | Out-Null
        [System.IO.File]::WriteAllText($destination, $json + [Environment]::NewLine)
    }
    $json
} finally {
    if (-not $process.HasExited) {
        $process.Kill($true)
        $process.WaitForExit()
    }
    $process.Dispose()
}
