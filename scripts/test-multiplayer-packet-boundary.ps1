#requires -Version 7.4
[CmdletBinding()]
param(
    [string]$Branch = 'feat/1.19.2/packet-computation',
    [string]$Tool = 'sfm-propagate-changes.exe',
    [ValidateSet('connection', 'packet')][string]$Proof = 'packet',
    [switch]$PrepareOnly,
    [ValidateRange(60, 1800)][int]$StartupTimeoutSeconds = 300,
    [ValidateRange(30, 1800)][int]$BuildLockTimeoutSeconds = 120
)

# Canonical compilation is sequential. The generated launches are replayed under
# one normal build lock so the two JVMs cannot observe changing build outputs.
# This isolated offline-mode server binds ONLY loopback; this is not authentication proof.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repoRoot = Split-Path -Parent $PSScriptRoot
$minecraftRoot = Join-Path $repoRoot 'platform/minecraft'
$cacheRoot = Join-Path $minecraftRoot 'build/sfm-toolchain'
$runId = [guid]::NewGuid().ToString()
$runRoot = Join-Path $cacheRoot "artifacts/multiplayer-packet-boundary/run-$runId"
$controlRoot = Join-Path $runRoot 'control'
$serverRoot = Join-Path $runRoot 'server'
$clientRoot = Join-Path $runRoot 'client'
$serverProcess = $null
$clientProcess = $null
$buildLock = $null
$portReservation = $null
$sequences = @{ server = 1; client = 1 }
$preparedArgfileHashes = @{}
$result = [ordered]@{ runId = $runId; proof = $Proof; passed = $false; packetBoundaryProven = $false }

function Write-JsonAtomic([string]$Path, $Value) {
    $temporary = "$Path.writing"
    [IO.File]::WriteAllText($temporary, ($Value | ConvertTo-Json -Depth 20), [Text.UTF8Encoding]::new($false))
    [IO.File]::Move($temporary, $Path, $true)
}

function Wait-Artifact([string]$Path, [Diagnostics.Process]$Process, [int]$Seconds) {
    $timer = [Diagnostics.Stopwatch]::StartNew()
    while (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        if ($Process.HasExited) { throw "Owned process $($Process.Id) exited ($($Process.ExitCode)) before $Path" }
        if ($timer.Elapsed.TotalSeconds -ge $Seconds) { throw "Timed out waiting for $Path; inspect $runRoot" }
        Start-Sleep -Milliseconds 100
    }
    $value = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json -AsHashtable
    if ($value.runId -ne $runId) { throw "Mismatched run identity in $Path" }
    return $value
}

function Send-Request([string]$Role, [string]$Operation, [Diagnostics.Process]$Process, [int]$Seconds = 120) {
    $sequence = $sequences[$Role]
    $prefix = '{0}-{1:D6}' -f $Role, $sequence
    $sequences[$Role] = $sequence + 1
    Write-JsonAtomic (Join-Path $controlRoot "$prefix.request.json") @{ runId = $runId; op = $Operation }
    $response = Wait-Artifact (Join-Path $controlRoot "$prefix.response.json") $Process $Seconds
    if (-not $response.success) { throw "$Role $Operation failed: $($response.message)" }
    Write-Host "$Role ${Operation}: passed (request $sequence)"
    return $response
}

function Wait-ServerEvidence([scriptblock]$Predicate, [string]$Description) {
    $timer = [Diagnostics.Stopwatch]::StartNew()
    do {
        $observation = Send-Request 'server' 'observe' $serverProcess
        if (& $Predicate $observation) { return $observation }
        if ($timer.Elapsed.TotalSeconds -gt 30) { throw "Missing dedicated-server evidence: $Description" }
        Start-Sleep -Milliseconds 250
    } while ($true)
}

function Outcome($Observation, [string]$Status) {
    if ($Observation.outcomes.ContainsKey($Status)) { return [long]$Observation.outcomes[$Status] }
    return 0L
}

function Remember-LaunchHash([string]$Kind) {
    $path = Join-Path $cacheRoot "run/$Kind/launch.java.args"
    $preparedArgfileHashes[$Kind] = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
}

function Assert-LockedLaunch([string]$Kind, [string]$Target) {
    $path = Join-Path $cacheRoot "run/$Kind/launch.java.args"
    $hash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
    if ($hash -ne $preparedArgfileHashes[$Kind]) { throw "Another preparation changed $Kind before lock acquisition; prepare again" }
    $lines = [IO.File]::ReadAllLines($path)
    $targetIndex = [Array]::IndexOf($lines, '--launchTarget')
    if ($targetIndex -lt 0 -or $targetIndex + 1 -ge $lines.Length -or $lines[$targetIndex + 1] -ne $Target) {
        throw "Unexpected canonical launch target in $Kind"
    }
    if ($Kind -eq 'runGameTestPreview' -and @($lines | Where-Object { $_ -eq '-Dsfm.gamePuppetSelection=sfm:multiplayer_packet_boundary' }).Count -ne 1) {
        throw 'The locked client argfile does not select the remote boundary puppet'
    }
    $result["${Kind}ArgfileSha256"] = $hash
}

function Wait-OwnedExit([Diagnostics.Process]$Process, [int]$Seconds, [string]$Role) {
    $timer = [Diagnostics.Stopwatch]::StartNew()
    while (-not $Process.HasExited -and $timer.Elapsed.TotalSeconds -lt $Seconds) { Start-Sleep -Milliseconds 100 }
    if (-not $Process.HasExited) {
        Write-Warning "Stopping only the owned $Role process tree $($Process.Id) after its bounded shutdown wait."
        $Process.Kill($true)
        if (-not $Process.WaitForExit(10000)) { throw "Owned $Role process $($Process.Id) did not exit" }
        $result["${Role}ForcedStop"] = $true
    }
    $result["${Role}Pid"] = $Process.Id
    $result["${Role}ExitCode"] = $Process.ExitCode
    $result["${Role}Stopped"] = $Process.HasExited
}

function Assert-PuppetCompletion([string]$Output) {
    # Match the canonical engine's completion contract. Minecraft also exits 0
    # after some harness failures, so process status alone is not acceptance.
    if ($Output.Contains('SFM_GAME_PUPPET_FAILED')) { throw 'Client log reports a failed puppet' }
    $completions = [regex]::Matches($Output, '(?m)^.*?SFM_GAME_PUPPET_COMPLETE(?=\s)([^\r\n]*)\r?$')
    if ($completions.Count -ne 1) { throw 'Client must report exactly one terminal puppet completion' }
    $fields = @{}
    foreach ($token in ($completions[0].Groups[1].Value.Trim() -split '\s+')) {
        $pair = $token -split '=', 2
        if ($pair.Count -eq 2) { $fields[$pair[0]] = $pair[1] }
    }
    [uint32]$total = 0
    if (-not $fields.ContainsKey('failed') -or $fields.failed -ne '0' -or
        -not $fields.ContainsKey('total') -or -not [uint32]::TryParse($fields.total, [ref]$total) -or $total -eq 0) {
        throw 'Client terminal completion did not prove failed=0 and total>0'
    }
    if (-not $Output.Contains('SFM_GAME_PUPPET_VIEWPORT_RESTORED')) {
        throw 'Client did not prove viewport restoration after the puppet'
    }
    return @{ failed = 0; total = $total; viewportRestored = $true }
}

function Read-OwnedClientOutput {
    $parts = foreach ($name in @('client.stdout.log', 'client.stderr.log')) {
        $path = Join-Path $runRoot $name
        $stream = [IO.File]::OpenRead($path)
        try {
            # Reads occur after the owned JVM exits; keep even malformed logging bounded.
            if ($stream.Length -gt 32MB) { throw "Owned client log exceeds the 32 MiB validation budget: $name" }
            $reader = [IO.StreamReader]::new($stream, [Text.Encoding]::UTF8, $true)
            try { $reader.ReadToEnd() } finally { $reader.Dispose() }
        } finally { $stream.Dispose() }
    }
    return $parts -join "`n"
}

function Invoke-Preparation([string[]]$Arguments, [string]$Name) {
    # Branch names cannot contain whitespace; all other arguments here are fixed literals.
    if ($Branch -match '\s|["\x00-\x1f]') { throw 'Invalid branch argument' }
    $preparation = Start-Process -FilePath $Tool -ArgumentList $Arguments -WorkingDirectory $repoRoot `
        -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runRoot "$Name.stdout.log") `
        -RedirectStandardError (Join-Path $runRoot "$Name.stderr.log")
    try {
        $timer = [Diagnostics.Stopwatch]::StartNew()
        while (-not $preparation.HasExited) {
            if ($timer.Elapsed.TotalSeconds -gt 1800) { throw "Canonical $Name preparation exceeded its bounded wait" }
            Start-Sleep -Milliseconds 500
        }
        if ($preparation.ExitCode -ne 0) { throw "Canonical $Name preparation failed ($($preparation.ExitCode)); inspect $runRoot" }
    } finally {
        if (-not $preparation.HasExited) { $preparation.Kill($true); [void]$preparation.WaitForExit(10000) }
        $preparation.Dispose()
    }
}

function Start-IsolatedJava([string]$Role, [string]$Kind, [string]$Java, [hashtable]$Environment) {
    $argfile = Join-Path $cacheRoot "run/$Kind/launch.java.args"
    if (-not (Test-Path -LiteralPath $argfile -PathType Leaf)) { throw "Missing canonical argfile: $argfile" }
    $working = if ($Role -eq 'server') { $serverRoot } else { $clientRoot }
    $arguments = @("-Dsfm.multiplayerPuppet.runId=$runId", "@`"$argfile`"")
    if ($Role -eq 'server') { $arguments += @('--nogui', '--port', [string]$port) }
    return Start-Process -FilePath $Java -ArgumentList $arguments -WorkingDirectory $working -Environment $Environment `
        -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runRoot "$Role.stdout.log") `
        -RedirectStandardError (Join-Path $runRoot "$Role.stderr.log")
}

try {
    foreach ($directory in @($runRoot, $controlRoot, $serverRoot, $clientRoot)) {
        [void](New-Item -ItemType Directory -Path $directory)
    }
    Write-Host "Proof artifacts: $runRoot"
    Invoke-Preparation @('run', 'server', '--branch', $Branch, '--dry-run', '--wait-for-build-lock', '--log-filter', 'info') 'prepare-server'
    Remember-LaunchHash 'runServer'
    Invoke-Preparation @('puppet', 'run', 'sfm:multiplayer_packet_boundary', '--branch', $Branch, '--variant', '1280x720@auto',
        '--dry-run', '--wait-for-build-lock', '--log-filter', 'info') 'prepare-client'
    Remember-LaunchHash 'runGameTestPreview'

    # .NET's Windows byte-range lock overlaps Rust File::try_lock's whole-file
    # LockFileEx region. This is the same lock, never a replacement lock file.
    $lockPath = Join-Path $cacheRoot '.locks/build-cache.lock'
    $buildLock = [IO.FileStream]::new($lockPath, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::ReadWrite)
    $timer = [Diagnostics.Stopwatch]::StartNew()
    while ($true) {
        try { $buildLock.Lock(0, 1); break } catch [IO.IOException] {
            if ($timer.Elapsed.TotalSeconds -gt $BuildLockTimeoutSeconds) {
                throw "Normal build cache is in use: $lockPath. No stale-lock deletion or lock bypass is attempted."
            }
            Start-Sleep -Milliseconds 250
        }
    }
    $plan = Get-Content -LiteralPath (Join-Path $cacheRoot 'state/last-plan.json') -Raw | ConvertFrom-Json -AsHashtable
    Assert-LockedLaunch 'runServer' 'forgeserveruserdev'
    Assert-LockedLaunch 'runGameTestPreview' 'forgeclientuserdev'
    if ($plan.branch_name -ne $Branch -or [IO.Path]::GetFullPath($plan.worktree_path) -ne [IO.Path]::GetFullPath($repoRoot)) {
        throw 'Canonical preparation selected a different branch or worktree'
    }
    if ($plan.minecraft_version -ne '1.19.2') { throw 'This launch fixture currently targets Minecraft 1.19.2 only' }
    $java = $plan.java.executable
    $result.java = $java
    $result.javaSha256 = (Get-FileHash -LiteralPath $java -Algorithm SHA256).Hash
    $toolPath = (Get-Command $Tool -CommandType Application).Source
    $result.tool = $toolPath
    $result.toolSha256 = (Get-FileHash -LiteralPath $toolPath -Algorithm SHA256).Hash
    $result.branch = $Branch
    $result.sourceRevision = (& git -c "safe.directory=$repoRoot" -C $repoRoot rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Cannot record source revision' }

    $portReservation = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $portReservation.Start()
    $port = $portReservation.LocalEndpoint.Port
    Write-JsonAtomic (Join-Path $controlRoot 'run.json') @{ runId = $runId; host = '127.0.0.1'; port = $port; proof = $Proof }
    $properties = @(
        'server-ip=127.0.0.1', "server-port=$port", 'online-mode=false', 'enforce-secure-profile=false',
        'enable-rcon=false', 'enable-query=false', 'max-players=2', 'view-distance=5', 'simulation-distance=5',
        'level-name=world', 'level-type=minecraft\:flat', 'gamemode=creative', 'force-gamemode=true',
        'spawn-protection=0', 'spawn-monsters=false', 'spawn-animals=false', 'spawn-npcs=false',
        'generate-structures=false', 'motd=SFM isolated loopback packet proof', 'max-tick-time=60000'
    )
    [IO.File]::WriteAllLines((Join-Path $serverRoot 'server.properties'), $properties, [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $serverRoot 'eula.txt'), "eula=true`n", [Text.UTF8Encoding]::new($false))
    # Keep the automated client unpaused without borrowing the developer's options/configuration.
    [IO.File]::WriteAllLines((Join-Path $clientRoot 'options.txt'), @('pauseOnLostFocus:false', 'renderDistance:5', 'simulationDistance:5'),
        [Text.UTF8Encoding]::new($false))
    $result.endpoint = "127.0.0.1:$port"
    if ($PrepareOnly) {
        $result.preparedOnly = $true
        Write-Host 'Prepared only; neither JVM was launched and no proof is claimed.'
        return
    }
    $mappings = "$($plan.properties.mapping_channel)_$($plan.properties.mapping_version)"
    $serverEnv = @{ MOD_CLASSES = "sfm%%$(Join-Path $cacheRoot 'project/run-mod-root/runServer')"; MCP_MAPPINGS = $mappings }
    $clientEnv = @{ MOD_CLASSES = "sfm%%$(Join-Path $cacheRoot 'project/run-mod-root/runGameTestPreview')"; MCP_MAPPINGS = $mappings }
    foreach ($environment in @($serverEnv, $clientEnv)) {
        if (-not (Test-Path -LiteralPath $environment.MOD_CLASSES.Substring(5) -PathType Container)) { throw 'Missing canonical mod roots' }
    }
    $portReservation.Stop()
    $portReservation = $null
    $serverProcess = Start-IsolatedJava 'server' 'runServer' $java $serverEnv
    $serverReady = Wait-Artifact (Join-Path $controlRoot 'server-ready.json') $serverProcess $StartupTimeoutSeconds
    if (-not $serverReady.dedicatedServer) { throw 'Fixture did not start a dedicated server' }
    $clientProcess = Start-IsolatedJava 'client' 'runGameTestPreview' $java $clientEnv
    [void](Wait-Artifact (Join-Path $controlRoot 'client-ready.json') $clientProcess $StartupTimeoutSeconds)
    $first = Send-Request 'client' 'connect' $clientProcess
    if (-not $first.connected -or $first.integratedServer) { throw 'First connection was not a normal remote client' }
    if ($Proof -eq 'packet') {
        [void](Send-Request 'client' 'wait_negotiation' $clientProcess)
        [void](Send-Request 'server' 'prepare' $serverProcess)
        [void](Send-Request 'client' 'subscribe_denied' $clientProcess)
        [void](Send-Request 'client' 'send_denied' $clientProcess)
        $initial = Send-Request 'server' 'observe' $serverProcess
        if ($initial.archivePackets -ne 0 -or $initial.mailboxPackets -ne 0 -or $initial.deniedPackets -ne 0) { throw 'Default-deny request changed inventory' }
        [void](Send-Request 'server' 'grant' $serverProcess)
        [void](Send-Request 'client' 'subscribe' $clientProcess)
        [void](Send-Request 'client' 'send_action' $clientProcess)
        $human = Send-Request 'client' 'wait_inbox' $clientProcess
        if (-not $human.protocol.humanRoundTrip) { throw 'Registered action / server SFML / inbox round trip was not proven' }
        [void](Wait-ServerEvidence { param($observed) $observed.archivePackets -eq 1 -and $observed.mailboxPackets -eq 0 } 'one actual SFML-archived human packet')
        [void](Send-Request 'client' 'close_subscription' $clientProcess)
        $program = Send-Request 'client' 'client_program' $clientProcess
        if (-not $program.protocol.clientProgramRoundTrip) { throw 'Consented Client Manager claim round trip was not proven' }
        $baseline = Wait-ServerEvidence { param($observed) $observed.archivePackets -eq 2 -and $observed.mailboxPackets -eq 0 } 'one additional actual Client Manager packet'
        [void](Send-Request 'client' 'raw_probes' $clientProcess)
        [void](Wait-ServerEvidence {
            param($observed)
            (Outcome $observed 'AUTHORITY_DENIED') -ge (Outcome $baseline 'AUTHORITY_DENIED') + 2 -and
            (Outcome $observed 'PROGRAM_REJECTED') -ge (Outcome $baseline 'PROGRAM_REJECTED') + 1 -and
            (Outcome $observed 'RECIPIENT_REJECTED') -ge (Outcome $baseline 'RECIPIENT_REJECTED') + 1 -and
            (Outcome $observed 'REPLAYED_REQUEST') -ge (Outcome $baseline 'REPLAYED_REQUEST') + 1
        } 'target, side, forged program, spoofed recipient, and replay rejection')
        [void](Send-Request 'server' 'edit_program' $serverProcess)
        [void](Send-Request 'client' 'raw_stale_claim' $clientProcess)
        [void](Wait-ServerEvidence { param($observed) (Outcome $observed 'PROGRAM_REJECTED') -ge (Outcome $baseline 'PROGRAM_REJECTED') + 2 } 'stale exact program claim rejection')
        [void](Send-Request 'client' 'pressure' $clientProcess)
        [void](Wait-ServerEvidence { param($observed) (Outcome $observed 'RATE_LIMITED') -gt (Outcome $baseline 'RATE_LIMITED') } 'bounded whole-frame pressure rejection')
    }
    [void](Send-Request 'client' 'disconnect' $clientProcess)
    $second = Send-Request 'client' 'connect' $clientProcess
    if (-not $second.connected -or $second.integratedServer -or $second.player -ne $first.player) { throw 'Reconnect identity changed unexpectedly' }
    $serverSeen = Send-Request 'server' 'observe' $serverProcess
    $seen = @($serverSeen.players | Where-Object { $_.uuid -eq $first.player })
    if ($seen.Count -ne 1 -or $seen[0].logins -lt 2) { throw 'Dedicated server did not observe two real logins' }
    if ($Proof -eq 'packet') {
        [void](Send-Request 'client' 'wait_negotiation' $clientProcess)
        [void](Send-Request 'client' 'assert_empty_inbox' $clientProcess)
        $beforeStale = Send-Request 'server' 'observe' $serverProcess
        [void](Send-Request 'client' 'raw_stale_session' $clientProcess)
        [void](Wait-ServerEvidence { param($observed) (Outcome $observed 'STALE_SESSION') -gt (Outcome $beforeStale 'STALE_SESSION') } 'pre-reconnect nonce rejection')
        [void](Send-Request 'server' 'revoke' $serverProcess)
        [void](Send-Request 'client' 'send_denied' $clientProcess)
        [void](Send-Request 'server' 'grant_expiring' $serverProcess)
        Start-Sleep -Milliseconds 1500
        [void](Send-Request 'client' 'send_denied' $clientProcess)
        $final = Send-Request 'server' 'observe' $serverProcess
        if ($final.archivePackets -ne 2 -or $final.mailboxPackets -ne 0 -or $final.deniedPackets -ne 0) { throw 'Rejected probes changed an inventory or duplicated a packet' }
        $result.packetBoundaryProven = $true
        $result.serverEvidence = $final
    }
    [void](Send-Request 'client' 'finish' $clientProcess)
    Wait-OwnedExit $clientProcess 30 'client'
    if ($clientProcess.ExitCode -ne 0) { throw 'Client puppet process failed' }
    $result.clientPuppetCompletion = Assert-PuppetCompletion (Read-OwnedClientOutput)
    [void](Send-Request 'server' 'stop' $serverProcess)
    Wait-OwnedExit $serverProcess 30 'server'
    if ($serverProcess.ExitCode -ne 0) { throw 'Dedicated server process failed' }
    $result.passed = $true
    $result.player = $first.player
    Write-Host "$Proof proof passed. Packet boundary assertions executed: $($result.packetBoundaryProven)"
} catch {
    $result.failure = $_.Exception.Message
    throw
} finally {
    if ($null -ne $portReservation) { $portReservation.Stop() }
    try {
        if ($null -ne $clientProcess) { Wait-OwnedExit $clientProcess 5 'client'; $clientProcess.Dispose() }
    } finally {
        try {
            if ($null -ne $serverProcess) {
                if (-not $serverProcess.HasExited) {
                    try { [void](Send-Request 'server' 'stop' $serverProcess 10) } catch { Write-Warning $_.Exception.Message }
                }
                Wait-OwnedExit $serverProcess 15 'server'
                $serverProcess.Dispose()
            }
        } finally {
            if ($null -ne $buildLock) { $buildLock.Dispose() }
            if (Test-Path -LiteralPath $controlRoot -PathType Container) {
                Write-JsonAtomic (Join-Path $runRoot 'result.json') $result
                Write-Host "Final evidence: $(Join-Path $runRoot 'result.json')"
            }
        }
    }
}
