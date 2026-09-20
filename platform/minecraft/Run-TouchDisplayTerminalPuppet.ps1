<#
File-only driver for sfm:in_world_touch_display_terminal.
Use the fresh control directory named by that puppet's ready.json. The game owns
its isolated helper and fixed worker; requests contain no command, executable,
consent identity, arbitrary source, or process destination.
#>
[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$ControlDirectory)
$ErrorActionPreference = 'Stop'
$controlRoot = (Resolve-Path -LiteralPath $ControlDirectory).Path
$ready = Get-Content -LiteralPath (Join-Path $controlRoot 'ready.json') -Raw | ConvertFrom-Json
if ($ready.schema -ne 'sfm:touch_display_terminal_puppet@1' -or
    [IO.Path]::GetFullPath($ready.control_directory) -ne [IO.Path]::GetFullPath($controlRoot)) {
    throw 'Choose the exact control directory from this terminal puppet ready.json.'
}
$script:TerminalStep = 1
$script:LastTerminalResponse = $null

function Send-TerminalStep {
    param([hashtable]$Request)
    $stem = '{0:D6}' -f $script:TerminalStep
    $requestPath = Join-Path $controlRoot ($stem + '.request.json')
    $responsePath = Join-Path $controlRoot ($stem + '.response.json')
    if ((Test-Path -LiteralPath $requestPath) -or (Test-Path -LiteralPath $responsePath)) {
        throw "Step $stem already exists. Choose a fresh puppet session; evidence is never overwritten."
    }
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes(($Request | ConvertTo-Json -Compress))
    $staged = Join-Path $controlRoot ($stem + '.request.staging')
    $stream = [IO.File]::Open($staged, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try { $stream.Write($bytes, 0, $bytes.Length) } finally { $stream.Dispose() }
    Move-Item -LiteralPath $staged -Destination $requestPath
    Write-Host ("Terminal step {0}: {1}" -f $stem, ($Request | ConvertTo-Json -Compress))
    $deadline = [DateTime]::UtcNow.AddSeconds(120)
    while (-not (Test-Path -LiteralPath $responsePath)) {
        if ([DateTime]::UtcNow -ge $deadline) { throw "No response for $stem; inspect the puppet log before issuing another request." }
        Start-Sleep -Milliseconds 200
    }
    $script:LastTerminalResponse = Get-Content -LiteralPath $responsePath -Raw | ConvertFrom-Json
    if ($script:LastTerminalResponse.status -ne 'dispatched') {
        throw ("Step {0} failed: {1}" -f $stem, $script:LastTerminalResponse.error)
    }
    if ($script:LastTerminalResponse.observation.screen -ne 'none') {
        throw 'The in-world terminal proof opened a Minecraft screen.'
    }
    $script:TerminalStep++
}

# Aim first: unlike the ambient fixture, this proof never synthetically selects
# or uploads a frame. The block renderer must evaluate the program and raster.
Send-TerminalStep @{op='aim'}
Send-TerminalStep @{op='await_ready'}
$before = $script:LastTerminalResponse.observation
if ($before.stage -ne 'READY' -or -not $before.render_eligible -or
    $before.client_program_evaluations -lt 2 -or $before.acknowledged -ne 0 -or
    $before.attempted -ne 0 -or [string]::IsNullOrWhiteSpace($before.texture_sha256)) {
    throw 'The initial capture did not prove a rendered, ready, unpressed terminal.'
}
Write-Host ("Before press capture: {0}" -f $script:LastTerminalResponse.capture_name)
Send-TerminalStep @{op='press'}
Send-TerminalStep @{op='await_ack'}
$after = $script:LastTerminalResponse.observation
if ($after.stage -ne 'ACKNOWLEDGED' -or $after.acknowledged -ne 1 -or
    $after.attempted -ne 1 -or $after.rejected -ne 0 -or
    $before.texture_sha256 -eq $after.texture_sha256 -or
    $after.client_program_evaluations -le $before.client_program_evaluations) {
    throw 'The real gameplay press did not produce exactly one worker ACK and a changed rendered raster.'
}
Write-Host ("After press capture: {0}" -f $script:LastTerminalResponse.capture_name)
Send-TerminalStep @{op='finish'}
if ($script:LastTerminalResponse.observation.stage -ne 'CLEANED') {
    throw 'The fixture did not complete revoke/unsubscribe/owned-process cleanup.'
}
foreach ($witness in @('rendered_before', 'gameplay_press', 'worker_ack', 'rendered_after', 'owned_cleanup')) {
    if ($witness -notin $script:LastTerminalResponse.observation.witnesses) {
        throw "Missing terminal acceptance witness: $witness"
    }
}
Write-Host 'Terminal file journey complete. The canonical launcher must still report GameTest and puppet success; inspect both world captures.'
