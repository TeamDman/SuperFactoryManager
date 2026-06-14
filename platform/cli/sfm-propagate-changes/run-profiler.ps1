[CmdletBinding(PositionalBinding = $false)]
param(
	[switch]$Release,
	[switch]$NoOpenProfiler,
	[switch]$Memory,
	[Parameter(Position = 0, ValueFromRemainingArguments = $true)]
	[string[]]$QueryArgs
)

$tracyLayerEnvVar = 'SFM_ENABLE_TRACY_LAYER'

function Format-Elapsed {
	param(
		[Parameter(Mandatory = $true)]
		[TimeSpan]$Elapsed
	)

	if ($Elapsed.TotalHours -ge 1) {
		return $Elapsed.ToString("hh\:mm\:ss\.fff")
	}

	return $Elapsed.ToString("mm\:ss\.fff")
}

function Get-TracyCaptureProcesses {
	param(
		[Parameter(Mandatory = $true)]
		[string]$CapturePath
	)

	$slugPattern = [Regex]::Escape($CapturePath)
	Get-CimInstance Win32_Process -Filter "Name = 'tracy-capture.exe'" -ErrorAction SilentlyContinue |
		Where-Object { $_.CommandLine -and $_.CommandLine -match $slugPattern } |
		ForEach-Object {
			try {
				Get-Process -Id $_.ProcessId -ErrorAction Stop
			} catch {
				$null
			}
		} |
		Where-Object { $_ -ne $null }
}

function Wait-ForTracyCaptureReady {
	param(
		[Parameter(Mandatory = $true)]
		[string]$CapturePath,
		[Parameter(Mandatory = $true)]
		[TimeSpan]$Timeout
	)

	$deadline = (Get-Date).Add($Timeout)
	do {
		$processes = @(Get-TracyCaptureProcesses -CapturePath $CapturePath)
		if ($processes.Count -gt 0) {
			return $processes
		}

		Start-Sleep -Milliseconds 250
	} while ((Get-Date) -lt $deadline)

	throw "Timed out waiting $(Format-Elapsed $Timeout) for tracy-capture to start for $CapturePath"
}

function Wait-ForTracyCaptureExit {
	param(
		[Parameter(Mandatory = $true)]
		[string]$CapturePath,
		[Parameter(Mandatory = $true)]
		[TimeSpan]$Timeout
	)

	$waitStopwatch = [System.Diagnostics.Stopwatch]::StartNew()
	$deadline = (Get-Date).Add($Timeout)
	do {
		$processes = @(Get-TracyCaptureProcesses -CapturePath $CapturePath)
		if ($processes.Count -eq 0) {
			$waitStopwatch.Stop()
			return $waitStopwatch.Elapsed
		}

		Start-Sleep -Milliseconds 250
	} while ((Get-Date) -lt $deadline)

	$waitStopwatch.Stop()
	return $null
}

function Stop-TracyCaptureGracefully {
	param(
		[Parameter(Mandatory = $true)]
		[string]$CapturePath
	)

	$processes = @(Get-TracyCaptureProcesses -CapturePath $CapturePath)
	if ($processes.Count -eq 0) {
		return [TimeSpan]::Zero
	}

	$shutdownStopwatch = [System.Diagnostics.Stopwatch]::StartNew()
	foreach ($process in $processes) {
		if ($process.HasExited) {
			continue
		}

		$requestedClose = $false
		try {
			$requestedClose = $process.CloseMainWindow()
		} catch {
			$requestedClose = $false
		}

		if (-not $requestedClose) {
			try {
				Stop-Process -Id $process.Id -ErrorAction SilentlyContinue
			} catch {
				# Ignore shutdown failures and let the wait/kill fallback below handle them.
			}
		}
	}

	$waitDeadline = (Get-Date).AddSeconds(30)
	do {
		Start-Sleep -Milliseconds 250
		$processes = @($processes | Where-Object {
			try {
				$_.Refresh()
				-not $_.HasExited
			} catch {
				$false
			}
		})
	} while ($processes.Count -gt 0 -and (Get-Date) -lt $waitDeadline)

	foreach ($process in $processes) {
		try {
			if (-not $process.HasExited) {
				$process.Kill()
			}
		} catch {
			# Ignore final cleanup failures.
		}
	}

	$shutdownStopwatch.Stop()
	return $shutdownStopwatch.Elapsed
}

$overallStopwatch = [System.Diagnostics.Stopwatch]::StartNew()
$buildElapsed = $null
$captureLaunchElapsed = $null
$commandElapsed = $null
$cleanupElapsed = $null
$profilerElapsed = $null
$captureShutdownElapsed = [TimeSpan]::Zero
$captureFlushDelay = [TimeSpan]::FromSeconds(1)
$captureStartupTimeout = [TimeSpan]::FromSeconds(10)
$captureExitTimeout = [TimeSpan]::FromMinutes(5)
$captureReadyForPostProcessing = $false
$capturePostProcessingSkipReason = $null
$commandExitCode = 0
$commandFailureMessage = $null

$captureDir = Join-Path $PSScriptRoot "tracy"
if (-not (Test-Path $captureDir)) {
	$null = New-Item -ItemType Directory -Path $captureDir
}

$slug = "$((Get-Date).ToString("yyyy-MM-dd_HH-mm-ss")).tracy"
$capturePath = Join-Path $captureDir $slug

if (-not (Get-Command tracy-capture.exe -ErrorAction SilentlyContinue)) {
	throw "tracy-capture.exe not found in PATH"
}

$profilerCommand = Get-Command tracy-profiler.exe -ErrorAction SilentlyContinue
$csvExportCommand = Get-Command tracy-csvexport.exe -ErrorAction SilentlyContinue

if (-not $NoOpenProfiler -and -not $profilerCommand) {
	Write-Warning "tracy-profiler.exe not found in PATH; capture will still be produced at $capturePath"
}

if (-not $csvExportCommand) {
	Write-Warning "tracy-csvexport.exe not found in PATH; CSV export will be skipped"
}

if (-not $QueryArgs -or $QueryArgs.Count -eq 0) {
	$QueryArgs = @("run", "game-test-server", "--mc", "1.19.2")
}

$features = @("tracy", "tracing_detailed")
if ($Memory) {
	$features += "tracy_memory"
}
$featureText = $features -join ","
$profileOutputDirectory = if ($Release) { "release" } else { "debug" }
$profileLabel = if ($Release) { "release" } else { "debug" }
$buildArgs = @("build", "--bin", "sfm-propagate-changes", "--features", $featureText)
if ($Release) {
	$buildArgs += "--release"
}
$sfmPath = Join-Path $PSScriptRoot "target\$profileOutputDirectory\sfm-propagate-changes.exe"

$buildStopwatch = [System.Diagnostics.Stopwatch]::StartNew()
Write-Host "Building $profileLabel with features ${featureText}: cargo $($buildArgs -join ' ')"
& cargo @buildArgs
$buildStopwatch.Stop()
$buildElapsed = $buildStopwatch.Elapsed
Write-Host "Build time: $(Format-Elapsed $buildElapsed)"
if ($LASTEXITCODE -ne 0) {
	throw "cargo build failed with exit code $LASTEXITCODE"
}

if (-not (Test-Path $sfmPath)) {
	throw "built sfm-propagate-changes executable not found at $sfmPath"
}

Write-Host "Capture: $capturePath"
Write-Host "Logging SFM toolchain runtime performance information to $capturePath"
$wt = Get-Command wt.exe -ErrorAction SilentlyContinue
$captureLaunchStopwatch = [System.Diagnostics.Stopwatch]::StartNew()

if ($wt) {
	Start-Process -FilePath "wt.exe" -ArgumentList @("-w", "new", "tracy-capture.exe", "-o", $capturePath)
} else {
	Write-Warning "wt.exe not found in PATH; launching tracy-capture in the current session"
	$capture = Start-Process -FilePath "tracy-capture.exe" -ArgumentList @("-o", $capturePath) -PassThru
}
$captureLaunchStopwatch.Stop()
$captureLaunchElapsed = $captureLaunchStopwatch.Elapsed
Write-Host "Capture launch time: $(Format-Elapsed $captureLaunchElapsed)"
Write-Host "Waiting for tracy-capture process to appear (timeout $(Format-Elapsed $captureStartupTimeout))"
$captureProcesses = @(Wait-ForTracyCaptureReady -CapturePath $capturePath -Timeout $captureStartupTimeout)
Write-Host "tracy-capture ready (pid: $($captureProcesses.Id -join ', '))"
Write-Host "Waiting 00:01.000 for tracy-capture to get ready"
Start-Sleep -Seconds 1

try {
	$previousTracyLayerSetting = [Environment]::GetEnvironmentVariable($tracyLayerEnvVar)
	Set-Item -Path "Env:$tracyLayerEnvVar" -Value "1"
	Write-Host "Running built $profileLabel SFM CLI with ${tracyLayerEnvVar}=1: $sfmPath $($QueryArgs -join ' ')"
	$commandStopwatch = [System.Diagnostics.Stopwatch]::StartNew()
	& $sfmPath @QueryArgs
	$commandStopwatch.Stop()
	$commandElapsed = $commandStopwatch.Elapsed
	$commandExitCode = $LASTEXITCODE
	Write-Host "Traced command time: $(Format-Elapsed $commandElapsed)"
	if ($commandExitCode -ne 0) {
		$commandFailureMessage = "sfm-propagate-changes.exe failed with exit code $commandExitCode"
		Write-Warning $commandFailureMessage
	}
}
finally {
	if ($null -eq $previousTracyLayerSetting) {
		Remove-Item "Env:$tracyLayerEnvVar" -ErrorAction SilentlyContinue
	} else {
		Set-Item -Path "Env:$tracyLayerEnvVar" -Value $previousTracyLayerSetting
	}
	$cleanupStopwatch = [System.Diagnostics.Stopwatch]::StartNew()
	Write-Host "Waiting $(Format-Elapsed $captureFlushDelay) before watching tracy-capture shutdown"
	Start-Sleep -Milliseconds ([int]$captureFlushDelay.TotalMilliseconds)
	$naturalCaptureShutdownElapsed = Wait-ForTracyCaptureExit -CapturePath $capturePath -Timeout $captureExitTimeout
	if ($null -ne $naturalCaptureShutdownElapsed) {
		$captureShutdownElapsed = $naturalCaptureShutdownElapsed
		$captureReadyForPostProcessing = $true
		Write-Host "tracy-capture exited after the client disconnected"
	} else {
		$capturePostProcessingSkipReason = "tracy-capture did not finish saving within $(Format-Elapsed $captureExitTimeout)"
		Write-Warning "Timed out waiting $(Format-Elapsed $captureExitTimeout) for tracy-capture to finish saving; forcing shutdown"
		$captureShutdownElapsed = Stop-TracyCaptureGracefully -CapturePath $capturePath
	}
	$cleanupStopwatch.Stop()
	$cleanupElapsed = $cleanupStopwatch.Elapsed
	Write-Host "Capture cleanup time: $(Format-Elapsed $cleanupElapsed)"
	Write-Host "Capture shutdown wait: $(Format-Elapsed $captureShutdownElapsed)"
}

if ($captureReadyForPostProcessing -and -not (Test-Path $capturePath)) {
	$captureReadyForPostProcessing = $false
	$capturePostProcessingSkipReason = "tracy-capture exited but no capture file was written to $capturePath"
}

if ($NoOpenProfiler) {
	if ($captureReadyForPostProcessing) {
		Write-Host "Skipping tracy-profiler launch (-NoOpenProfiler). Capture saved to $capturePath"
	} else {
		Write-Warning "Skipping tracy-profiler launch (-NoOpenProfiler). $capturePostProcessingSkipReason"
	}
} elseif ($profilerCommand -and $captureReadyForPostProcessing) {
	Write-Host "Displaying results from $capturePath"
	$profilerStopwatch = [System.Diagnostics.Stopwatch]::StartNew()
	tracy-profiler.exe "$capturePath"
	$profilerStopwatch.Stop()
	$profilerElapsed = $profilerStopwatch.Elapsed
	Write-Host "Profiler time: $(Format-Elapsed $profilerElapsed)"
} else {
	if ($captureReadyForPostProcessing) {
		Write-Host "Capture saved to $capturePath"
	} else {
		Write-Warning "Skipping tracy-profiler launch because $capturePostProcessingSkipReason"
	}
}

if ($csvExportCommand -and $captureReadyForPostProcessing) {
	Write-Host "CSV summary from tracy-csvexport.exe $capturePath"
	tracy-csvexport.exe $capturePath
} elseif ($csvExportCommand) {
	Write-Warning "Skipping tracy-csvexport because $capturePostProcessingSkipReason"
}

$overallStopwatch.Stop()
Write-Host "Timing summary:"
Write-Host "  build:          $(Format-Elapsed $buildElapsed)"
Write-Host "  capture launch: $(Format-Elapsed $captureLaunchElapsed)"
if ($commandElapsed) {
	Write-Host "  traced command: $(Format-Elapsed $commandElapsed)"
}
Write-Host "  cleanup:        $(Format-Elapsed $cleanupElapsed)"
Write-Host "  capture stop:   $(Format-Elapsed $captureShutdownElapsed)"
if ($profilerElapsed) {
	Write-Host "  profiler:       $(Format-Elapsed $profilerElapsed)"
}
Write-Host "  total wrapper:  $(Format-Elapsed $overallStopwatch.Elapsed)"

if ($commandFailureMessage) {
	throw $commandFailureMessage
}
