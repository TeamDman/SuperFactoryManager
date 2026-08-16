param(
    [Parameter(Mandatory = $true)]
    [string] $Executable,
    [Parameter(Mandatory = $true)]
    [string] $RepoRoot,
    [string] $Branch = "1.19.2",
    [string] $OutputPath,
    [ValidateRange(2, 1000)]
    [int] $WarmSampleCount = 24,
    [int64] $MaximumPeakWorkingSetBytes = 2GB,
    [int64] $MaximumSteadyPrivateMemoryBytes = 1GB
)

$ErrorActionPreference = "Stop"
$probeStage = "initialization"
$probeSucceeded = $false
$probeFailure = $null
$stderr = ""

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
            throw "Symbol worker closed stdout after $offset of $Count frame bytes were read."
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

function Get-BranchJavaSourceTreeSnapshot {
    param([Parameter(Mandatory = $true)] [string] $Root)
    $canonicalRoot = [System.IO.Path]::GetFullPath($Root)
    $sourceBase = [System.IO.Path]::Combine(
        $canonicalRoot,
        "platform",
        "minecraft",
        "src"
    )
    if (-not (Test-Path -LiteralPath $sourceBase -PathType Container)) {
        throw "Branch Java source base does not exist: $sourceBase"
    }

    $files = @(
        [System.IO.Directory]::EnumerateDirectories($sourceBase) |
            ForEach-Object { [System.IO.Path]::Combine($_, "java") } |
            Where-Object { Test-Path -LiteralPath $_ -PathType Container } |
            ForEach-Object {
                [System.IO.Directory]::EnumerateFiles(
                    $_,
                    "*.java",
                    [System.IO.SearchOption]::AllDirectories
                )
            }
    )
    [System.Array]::Sort($files, [System.StringComparer]::Ordinal)

    $hasher = [System.Security.Cryptography.IncrementalHash]::CreateHash(
        [System.Security.Cryptography.HashAlgorithmName]::SHA256
    )
    [int64] $totalBytes = 0
    try {
        foreach ($path in $files) {
            $relativePath = [System.IO.Path]::GetRelativePath($canonicalRoot, $path).Replace('\', '/')
            $contents = [System.IO.File]::ReadAllBytes($path)
            $totalBytes += $contents.LongLength
            $header = [System.Text.Encoding]::UTF8.GetBytes(
                "path:$relativePath`nbytes:$($contents.LongLength)`n"
            )
            $hasher.AppendData($header)
            $hasher.AppendData($contents)
            $hasher.AppendData([byte[]] @(10))
        }
        $digest = $hasher.GetHashAndReset()
    } finally {
        $hasher.Dispose()
    }

    return [pscustomobject] [ordered]@{
        scope = "platform/minecraft/src/*/java/**/*.java"
        file_count = $files.Count
        total_bytes = $totalBytes
        sha256 = "sha256:$([System.Convert]::ToHexString($digest).ToLowerInvariant())"
    }
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

function New-UsageRequest {
    param(
        [Parameter(Mandatory = $true)] [uint64] $RequestId,
        [Parameter(Mandatory = $true)] $Workspace,
        [Parameter(Mandatory = $true)] $Root,
        [Parameter(Mandatory = $true)] [string] $RelativePath,
        [Parameter(Mandatory = $true)] [string] $Pattern,
        [Parameter(Mandatory = $true)] [string] $Token
    )
    $fixture = New-DefinitionRequest @PSBoundParameters
    $fixture.request.schema = "sfm.usage-at-position-request/1"
    $fixture.label = "$($fixture.label):usages"
    return $fixture
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
        operation = "definition"
        label = ""
        elapsed_ms = [Math]::Round($watch.Elapsed.TotalMilliseconds, 3)
        outcome = $response.result.outcome
        completeness = $response.result.completeness
        definitions = @($response.result.definitions).Count
    }
}

function Invoke-Usage {
    param(
        [Parameter(Mandatory = $true)] $InputStream,
        [Parameter(Mandatory = $true)] $OutputStream,
        [Parameter(Mandatory = $true)] $Request
    )
    $frame = [ordered]@{
        kind = "usage-at-position"
        schema = "sfm.symbol-server.usage-at-position/1"
        request = $Request
    }
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    Write-Frame -Stream $InputStream -Value $frame
    $response = Read-Frame -Stream $OutputStream
    $watch.Stop()
    if ($response.kind -ne "usage-at-position-result") {
        throw "Expected usage-at-position-result, got '$($response.kind)': $($response | ConvertTo-Json -Depth 20 -Compress)"
    }
    return [pscustomobject] [ordered]@{
        operation = "usage-at-position"
        label = ""
        elapsed_ms = [Math]::Round($watch.Elapsed.TotalMilliseconds, 3)
        outcome = $response.result.outcome
        completeness = $response.result.completeness
        targets = @($response.result.targets).Count
        usages = @($response.result.usages).Count
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

function Get-LatencySummary {
    param([Parameter(Mandatory = $true)] [object[]] $Samples)
    $elapsed = [double[]] @($Samples | ForEach-Object { $_.elapsed_ms })
    if ($elapsed.Count -eq 0) {
        return [pscustomobject] [ordered]@{
            count = 0
            median_ms = $null
            p95_ms = $null
            maximum_ms = $null
            accepted = $false
        }
    }
    $median = Get-Percentile -Values $elapsed -Percentile 0.50
    $p95 = Get-Percentile -Values $elapsed -Percentile 0.95
    $maximum = ($elapsed | Measure-Object -Maximum).Maximum
    return [pscustomobject] [ordered]@{
        count = $elapsed.Count
        median_ms = $median
        p95_ms = $p95
        maximum_ms = $maximum
        accepted = ($median -le 250 -and $p95 -le 750 -and $maximum -lt 1000)
    }
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

function Add-ProcessMemorySample {
    param(
        [Parameter(Mandatory = $true)] [System.Diagnostics.Process] $Process,
        [Parameter(Mandatory = $true)] [string] $Stage,
        [Parameter(Mandatory = $true)]
        [AllowEmptyCollection()]
        [System.Collections.Generic.List[object]] $Samples
    )
    $Process.Refresh()
    $workingSetBytes = [int64] $Process.WorkingSet64
    $privateMemoryBytes = [int64] $Process.PrivateMemorySize64
    $script:peakWorkingSetBytes = [Math]::Max(
        [int64] $script:peakWorkingSetBytes,
        $workingSetBytes
    )
    $Samples.Add([pscustomobject] [ordered]@{
        stage = $Stage
        working_set_bytes = $workingSetBytes
        private_memory_bytes = $privateMemoryBytes
    })
}

$probeArtifactId = [System.Guid]::NewGuid().ToString('N')
$destination = if ($OutputPath) { [System.IO.Path]::GetFullPath($OutputPath) } else { $null }
$artifactBase = if ($destination) {
    [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($destination)) | Out-Null
    $destination
} else {
    [System.IO.Path]::Combine(
        [System.IO.Path]::GetTempPath(),
        "sfm-symbol-server-probe-$probeArtifactId"
    )
}
$structuredLogPath = "$artifactBase.structured.ndjson"
$stderrEvidencePath = "$artifactBase.stderr.log"
$failureEvidencePath = "$artifactBase.failure.json"
foreach ($staleEvidencePath in @($structuredLogPath, $stderrEvidencePath, $failureEvidencePath)) {
    [System.IO.File]::Delete($staleEvidencePath)
}
$probeStage = "source-tree/before"
$sourceTreeBefore = Get-BranchJavaSourceTreeSnapshot -Root $RepoRoot
$startInfo = [System.Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = [System.IO.Path]::GetFullPath($Executable)
$executableVersion = (& $startInfo.FileName --version | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($executableVersion)) {
    throw "Could not read the probed executable version."
}
$startInfo.WorkingDirectory = [System.IO.Path]::GetFullPath($RepoRoot)
$startInfo.UseShellExecute = $false
$startInfo.CreateNoWindow = $true
$startInfo.RedirectStandardInput = $true
$startInfo.RedirectStandardOutput = $true
$startInfo.RedirectStandardError = $true
$startInfo.ArgumentList.Add("--log-filter")
$startInfo.ArgumentList.Add("info")
$startInfo.ArgumentList.Add("--log-file")
$startInfo.ArgumentList.Add($structuredLogPath)
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
$peakWorkingSetBytes = [int64] 0
$memorySamples = [System.Collections.Generic.List[object]]::new()

try {
    $probeStage = "handshake/write-hello"
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
                "shutdown",
                "usage-at-position"
            )
            max_frame_bytes = 16777216
        }
    })
    $probeStage = "handshake/read-hello"
    $helloFrame = Read-Frame -Stream $stdout
    if ($helloFrame.kind -ne "hello") {
        throw "Expected server hello, got '$($helloFrame.kind)'."
    }
    if (@($helloFrame.hello.capabilities) -notcontains "usage-at-position") {
        throw "Worker hello did not negotiate usage-at-position."
    }
    Add-ProcessMemorySample -Process $process -Stage "handshake-complete" -Samples $memorySamples
    $workspace = $helloFrame.hello.workspace.request_workspace
    $roots = @($helloFrame.hello.workspace.roots)
    $managedRoots = @($helloFrame.hello.workspace.managed_source_roots)
    $jdkContextRoots = @($workspace.source_roots | Where-Object {
        $_.kind -eq "jdk" -and $_.exists
    })
    foreach ($jdkContextRoot in $jdkContextRoots) {
        $managedJdkRoot = $managedRoots | Where-Object {
            $_.resolver_id -eq "jdk-source" -and
            $_.address_scheme -eq "jdk-source" -and
            $_.root_address -eq "jdk-source://$($jdkContextRoot.id)/" -and
            $_.root_id -eq $jdkContextRoot.id -and
            $_.source_set -eq $jdkContextRoot.source_set
        } | Select-Object -First 1
        if ($null -eq $managedJdkRoot) {
            throw "Worker hello omitted canonical jdk-source mapping for '$($jdkContextRoot.id)'."
        }
        if (-not [System.IO.Path]::IsPathFullyQualified($managedJdkRoot.canonical_absolute_path) -or
            -not (Test-Path -LiteralPath $managedJdkRoot.canonical_absolute_path -PathType Container)) {
            throw "Worker hello returned an invalid canonical JDK source path for '$($jdkContextRoot.id)'."
        }
    }
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
    $usageTargets = @(
        (New-UsageRequest -RequestId $requestId -Workspace $workspace -Root $mainRoot `
            -RelativePath "ca/teamdman/sfm/common/item/DiskItem.java" `
            -Pattern "class DiskItem" -Token "DiskItem"),
        (New-UsageRequest -RequestId ($requestId + 1) -Workspace $workspace -Root $gameTestRoot `
            -RelativePath "ca/teamdman/sfm/gametest/SFMGameTestHelper.java" `
            -Pattern "class SFMGameTestHelper" -Token "SFMGameTestHelper"),
        (New-UsageRequest -RequestId ($requestId + 2) -Workspace $workspace -Root $mainRoot `
            -RelativePath "ca/teamdman/sfm/common/util/SFMBlockPosUtils.java" `
            -Pattern "import net.minecraft.core.BlockPos;" -Token "BlockPos")
    )

    $probeStage = "query/cold-definition"
    $cold = Invoke-Definition -InputStream $stdin -OutputStream $stdout -Request $targets[0].request
    $cold.label = $targets[0].label
    if ($cold.outcome -ne "success" -or $cold.definitions -lt 1) {
        throw "Cold definition query did not resolve a definition."
    }
    Add-ProcessMemorySample -Process $process -Stage "cold-definition-complete" -Samples $memorySamples
    $requestId++
    $coldUsages = [System.Collections.Generic.List[object]]::new()
    foreach ($template in $usageTargets) {
        $probeStage = "query/cold-usage-at-position-$($template.label)"
        $template.request.request_id = $requestId
        $requestId++
        $sample = Invoke-Usage -InputStream $stdin -OutputStream $stdout -Request $template.request
        $sample.label = $template.label
        if ($sample.outcome -ne "success" -or $sample.targets -lt 1 -or $sample.usages -lt 1) {
            throw "Cold usage-at-position query '$($sample.label)' did not resolve its target."
        }
        $coldUsages.Add($sample)
        Add-ProcessMemorySample -Process $process `
            -Stage "cold-usage-$($sample.label)-complete" -Samples $memorySamples
    }
    $warm = [System.Collections.Generic.List[object]]::new()
    for ($index = 0; $index -lt $WarmSampleCount; $index++) {
        $isDefinition = ($index % 2) -eq 0
        $probeStage = if ($isDefinition) {
            "query/warm-$index-definition"
        } else {
            "query/warm-$index-usage-at-position"
        }
        $templates = if ($isDefinition) { $targets } else { $usageTargets }
        $template = $templates[$index % $templates.Count]
        $template.request.request_id = $requestId
        $requestId++
        $sample = if ($isDefinition) {
            Invoke-Definition -InputStream $stdin -OutputStream $stdout -Request $template.request
        } else {
            Invoke-Usage -InputStream $stdin -OutputStream $stdout -Request $template.request
        }
        $sample.label = $template.label
        if ($sample.outcome -ne "success" -or
            ($isDefinition -and $sample.definitions -lt 1) -or
            (-not $isDefinition -and ($sample.targets -lt 1 -or $sample.usages -lt 1))) {
            throw "Warm $($sample.operation) query '$($sample.label)' did not resolve its target."
        }
        $warm.Add($sample)
        if ($index -eq 0 -or $index -eq ($WarmSampleCount - 1)) {
            Add-ProcessMemorySample -Process $process `
                -Stage "warm-$index-$($sample.operation)-complete" -Samples $memorySamples
        }
    }

    $probeStage = "query/mixed-definition-and-usage"
    $mixedDefinition = $targets[0]
    $mixedDefinition.request.request_id = $requestId
    $requestId++
    $mixedUsage = $usageTargets[0]
    $mixedUsage.request.request_id = $requestId
    $requestId++
    Write-Frame -Stream $stdin -Value ([ordered]@{
        kind = "definition"
        schema = "sfm.symbol-server.definition/1"
        request = $mixedDefinition.request
    })
    Write-Frame -Stream $stdin -Value ([ordered]@{
        kind = "usage-at-position"
        schema = "sfm.symbol-server.usage-at-position/1"
        request = $mixedUsage.request
    })
    $mixedResponses = @((Read-Frame -Stream $stdout), (Read-Frame -Stream $stdout))
    $mixedKinds = @($mixedResponses | ForEach-Object { $_.kind } | Sort-Object)
    if (($mixedKinds -join ',') -ne "definition-result,usage-at-position-result") {
        throw "Mixed requests did not produce one definition and one usage result."
    }

    foreach ($id in Get-DescendantProcessIds -RootProcessId ([uint32] $process.Id)) {
        [void] $observedDescendants.Add($id)
    }

    $probeStage = "cancellation/definition"
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

    $probeStage = "cancellation/usage-at-position"
    $usageCancelTemplate = $usageTargets[0]
    $usageCancelTemplate.request.request_id = $requestId
    $requestId++
    Write-Frame -Stream $stdin -Value ([ordered]@{
        kind = "cancel"
        schema = "sfm.symbol-server.cancel/1"
        request_id = $usageCancelTemplate.request.request_id
        request_generation = $usageCancelTemplate.request.request_generation
        workspace_generation = $workspace.workspace_generation
        reason = "installed usage probe cancellation"
    })
    Write-Frame -Stream $stdin -Value ([ordered]@{
        kind = "usage-at-position"
        schema = "sfm.symbol-server.usage-at-position/1"
        request = $usageCancelTemplate.request
    })
    $usageCancelResponses = @((Read-Frame -Stream $stdout), (Read-Frame -Stream $stdout))
    $usageCancelKinds = @($usageCancelResponses | ForEach-Object { $_.kind } | Sort-Object)
    if (($usageCancelKinds -join ',') -ne "cancelled,usage-at-position-cancelled") {
        throw "Pre-request usage cancellation did not produce exactly one acknowledgement and one terminal response."
    }

    Add-ProcessMemorySample -Process $process -Stage "before-shutdown" -Samples $memorySamples

    $probeStage = "shutdown"
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
    $probeStage = "source-tree/after"
    $sourceTreeAfter = Get-BranchJavaSourceTreeSnapshot -Root $RepoRoot
    $sourceTreeEqual = (
        $sourceTreeBefore.file_count -eq $sourceTreeAfter.file_count -and
        $sourceTreeBefore.total_bytes -eq $sourceTreeAfter.total_bytes -and
        $sourceTreeBefore.sha256 -eq $sourceTreeAfter.sha256
    )
    if (-not $sourceTreeEqual) {
        throw "Installed symbol worker mutated the analyzed branch Java source tree."
    }
    $stderr = $stderrTask.GetAwaiter().GetResult()
    $probeStage = "report"
    $warmSummary = Get-LatencySummary -Samples @($warm)
    $definitionWarmSummary = Get-LatencySummary -Samples @(
        $warm | Where-Object { $_.operation -eq "definition" }
    )
    $usageWarmSummary = Get-LatencySummary -Samples @(
        $warm | Where-Object { $_.operation -eq "usage-at-position" }
    )
    $telemetry = @($stderr -split "`r?`n" | Where-Object {
        $_ -match "symbol-server (definition|usage-at-position) (completed|failed)"
    })
    $structuredTelemetry = @(
        [System.IO.File]::ReadAllLines($structuredLogPath) |
            ForEach-Object { $_ | ConvertFrom-Json -Depth 100 } |
            Where-Object { $_.fields.message -match "symbol-server (definition|usage-at-position) (completed|failed)" } |
            ForEach-Object { $_.fields }
    )
    $steadyMemory = $memorySamples | Where-Object {
        $_.stage -eq "before-shutdown"
    } | Select-Object -Last 1
    $memoryAcceptance = [ordered]@{
        maximum_peak_working_set_bytes = $MaximumPeakWorkingSetBytes
        maximum_steady_private_memory_bytes = $MaximumSteadyPrivateMemoryBytes
        observed_peak_working_set_bytes = $peakWorkingSetBytes
        observed_steady_private_memory_bytes = $steadyMemory.private_memory_bytes
        accepted = (
            $peakWorkingSetBytes -le $MaximumPeakWorkingSetBytes -and
            $steadyMemory.private_memory_bytes -le $MaximumSteadyPrivateMemoryBytes
        )
    }
    $report = [ordered]@{
        schema = "sfm.symbol-server-installed-probe/5"
        executable = [System.IO.Path]::GetFileName($startInfo.FileName)
        executable_version = $executableVersion
        branch = $Branch
        cold = $cold
        cold_usage_at_position_samples = $coldUsages
        warm_samples = $warm
        warm_summary = $warmSummary
        definition_warm_summary = $definitionWarmSummary
        usage_at_position_warm_summary = $usageWarmSummary
        negotiated_capabilities = @($helloFrame.hello.capabilities)
        managed_source_roots = $managedRoots
        analyzed_source_tree = [ordered]@{
            before = $sourceTreeBefore
            after = $sourceTreeAfter
            equal = $sourceTreeEqual
        }
        mixed_response_kinds = $mixedKinds
        cancellation_response_kinds = $cancelKinds
        usage_cancellation_response_kinds = $usageCancelKinds
        shutdown_response_kind = $shutdown.kind
        process = [ordered]@{
            pid = $process.Id
            exited = $process.HasExited
            exit_code = $process.ExitCode
            peak_working_set_bytes = $peakWorkingSetBytes
            memory_samples = $memorySamples
            observed_descendant_processes = $observedDescendants.Count
            leaked_descendant_processes = $leakedDescendants.Count
        }
        memory_acceptance = $memoryAcceptance
        telemetry_lines = $telemetry
        telemetry = $structuredTelemetry
    }
    $json = ($report | ConvertTo-Json -Depth 100).Replace("`r`n", "`n")
    if ($OutputPath) {
        [System.IO.File]::WriteAllText($destination, $json + "`n")
    }
    $json
    if (-not $report.warm_summary.accepted -or
        -not $report.definition_warm_summary.accepted -or
        -not $report.usage_at_position_warm_summary.accepted -or
        -not $report.memory_acceptance.accepted) {
        throw "Installed symbol worker missed its warm latency or memory acceptance bounds."
    }
    $probeSucceeded = $true
} catch {
    $probeFailure = $_
} finally {
    if (-not $process.HasExited) {
        $process.Kill($true)
        $process.WaitForExit()
    }
    try {
        $stderr = $stderrTask.GetAwaiter().GetResult()
    } catch {
        $stderr = "$stderr`nFailed to collect redirected worker stderr: $($_.Exception.Message)".Trim()
    }
    if ($null -ne $probeFailure) {
        [System.IO.File]::WriteAllText(
            $stderrEvidencePath,
            $stderr + [Environment]::NewLine
        )
        $structuredLogExists = Test-Path -LiteralPath $structuredLogPath -PathType Leaf
        $structuredLogBytes = if ($structuredLogExists) {
            (Get-Item -LiteralPath $structuredLogPath).Length
        } else {
            0
        }
        $failureReport = [ordered]@{
            schema = "sfm.symbol-server-installed-probe-failure/1"
            artifact_id = $probeArtifactId
            executable = $startInfo.FileName
            executable_version = $executableVersion
            branch = $Branch
            stage = $probeStage
            exception = [ordered]@{
                message = $probeFailure.Exception.Message
                type = $probeFailure.Exception.GetType().FullName
                script_stack_trace = $probeFailure.ScriptStackTrace
            }
            process = [ordered]@{
                pid = $process.Id
                exited = $process.HasExited
                exit_code = if ($process.HasExited) { $process.ExitCode } else { $null }
                peak_working_set_bytes = $peakWorkingSetBytes
                memory_samples = $memorySamples
            }
            evidence = [ordered]@{
                stderr_path = $stderrEvidencePath
                stderr_bytes = [System.Text.Encoding]::UTF8.GetByteCount($stderr)
                structured_log_path = $structuredLogPath
                structured_log_exists = $structuredLogExists
                structured_log_bytes = $structuredLogBytes
            }
        }
        [System.IO.File]::WriteAllText(
            $failureEvidencePath,
            ($failureReport | ConvertTo-Json -Depth 100) + [Environment]::NewLine
        )
        [Console]::Error.WriteLine(
            "Installed symbol worker probe failed during '$probeStage'. Preserved failure evidence at '$failureEvidencePath', stderr at '$stderrEvidencePath', and structured logs at '$structuredLogPath'."
        )
    } elseif ($probeSucceeded) {
        Remove-Item -LiteralPath $structuredLogPath -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $stderrEvidencePath -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $failureEvidencePath -ErrorAction SilentlyContinue
    }
    $process.Dispose()
}
if ($null -ne $probeFailure) {
    throw $probeFailure
}
