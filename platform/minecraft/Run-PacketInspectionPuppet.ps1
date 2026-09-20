<#
File-only driver for sfm:in_world_packet_inspection. Use the fresh directory
reported by ready.json. This proves rendered item art and hovered Alt+D, not
physical Shift polling. Requests contain only a fixed operation name.
#>
[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$ControlDirectory)
$ErrorActionPreference = 'Stop'
$controlRoot = (Resolve-Path -LiteralPath $ControlDirectory).Path
$ready = Get-Content -LiteralPath (Join-Path $controlRoot 'ready.json') -Raw | ConvertFrom-Json
$operations = @('hover_packet', 'inspect_packet', 'prove_snapshot', 'hover_ordinary',
    'inspect_ordinary', 'hover_empty', 'inspect_empty', 'finish')
if ($ready.schema -ne 'sfm:packet_inspection_puppet@1' -or
    [IO.Path]::GetFullPath($ready.control_directory) -ne [IO.Path]::GetFullPath($controlRoot) -or
    ($ready.required_operations_in_order -join ',') -ne ($operations -join ',') -or
    $ready.physical_shift_proven -ne $false) {
    throw 'Choose the exact fresh directory from this packet inspection puppet ready.json.'
}
$packetStep = 1
$packetText = $null
foreach ($operation in $operations) {
    $stem = '{0:D6}' -f $packetStep
    $requestPath = Join-Path $controlRoot ($stem + '.request.json')
    $responsePath = Join-Path $controlRoot ($stem + '.response.json')
    if ((Test-Path -LiteralPath $requestPath) -or (Test-Path -LiteralPath $responsePath)) {
        throw "Step $stem already exists. Choose a fresh puppet session; evidence is never overwritten."
    }
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes((@{op=$operation} | ConvertTo-Json -Compress))
    $staged = Join-Path $controlRoot ($stem + '.request.staging')
    $stream = [IO.File]::Open($staged, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try { $stream.Write($bytes, 0, $bytes.Length) } finally { $stream.Dispose() }
    Move-Item -LiteralPath $staged -Destination $requestPath
    Write-Host ("Packet inspection {0}: {1}" -f $stem, $operation)
    $deadline = [DateTime]::UtcNow.AddSeconds(40)
    while (-not (Test-Path -LiteralPath $responsePath)) {
        if ([DateTime]::UtcNow -ge $deadline) { throw "No response for $stem; inspect the puppet log before issuing another request." }
        Start-Sleep -Milliseconds 200
    }
    $response = Get-Content -LiteralPath $responsePath -Raw | ConvertFrom-Json
    if ($response.sequence -ne $packetStep -or $response.request.op -ne $operation -or $response.status -ne 'passed') {
        throw ("Step {0} failed or mismatched: {1}" -f $stem, $response.error)
    }
    if ($response.observation.physical_shift_proven -ne $false -or $response.observation.failed_requests -ne 0 -or
        [string]::IsNullOrWhiteSpace($response.capture_name)) {
        throw 'The response does not establish the bounded packet-inspection evidence contract.'
    }
    if ($operation -eq 'inspect_packet') { $packetText = $response.observation.document }
    if ($operation -eq 'prove_snapshot' -and $response.observation.document -cne $packetText) {
        throw 'The captured packet document changed after fixture mutation or editing input.'
    }
    if ($operation -in @('inspect_packet', 'prove_snapshot', 'inspect_ordinary') -and $response.observation.read_only -ne $true) {
        throw 'A hovered item opened a writable inspection document.'
    }
    if ($operation -eq 'inspect_empty' -and
        ($response.observation.read_only -ne $false -or $response.observation.document -cne '')) {
        throw 'Empty hover did not preserve the blank writable fallback.'
    }
    Write-Host ("Capture: {0}" -f $response.capture_name)
    $packetStep++
}
if ($response.observation.screen -ne 'none' -or
    ($response.observation.witnesses -join ',') -ne ($operations -join ',')) {
    throw 'The fixture did not complete every acceptance step and owned-screen cleanup.'
}
Write-Host 'Packet file journey complete. Inspect the art and editor screenshots and wait for canonical puppet success. Physical Shift remains unproven.'
