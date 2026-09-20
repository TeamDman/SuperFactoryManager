<#
Run only against the ready.json directory of sfm:client_program_consent_and_signing.
The game creates a fresh private world and an isolated run-local key directory.
No passphrase, arbitrary source, or filesystem destination is sent in requests.
The puppet types its own disposable fixture phrase through the real masked widgets.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ControlDirectory,
    [switch]$SkipSignerTrust
)
$ErrorActionPreference = 'Stop'
$controlRoot = (Resolve-Path -LiteralPath $ControlDirectory).Path
$readyPath = Join-Path $controlRoot 'ready.json'
$ready = Get-Content -LiteralPath $readyPath -Raw | ConvertFrom-Json
if ($ready.schema -ne 'sfm:client_program_signing_puppet@1' -or
    [IO.Path]::GetFullPath($ready.control_directory) -ne [IO.Path]::GetFullPath($controlRoot)) {
    throw 'Choose the exact control directory from this signing puppet ready.json.'
}
$script:SigningStep = 1
$script:LastSigningResponse = $null

function Send-SigningStep {
    param([hashtable]$Request)
    $stem = '{0:D6}' -f $script:SigningStep
    $requestPath = Join-Path $controlRoot ($stem + '.request.json')
    $responsePath = Join-Path $controlRoot ($stem + '.response.json')
    if ((Test-Path -LiteralPath $requestPath) -or (Test-Path -LiteralPath $responsePath)) {
        throw "Step $stem already exists. Start this runner against a fresh puppet session; evidence is never overwritten."
    }
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes(($Request | ConvertTo-Json -Depth 10 -Compress))
    $stagedPath = Join-Path $controlRoot ($stem + '.request.staging')
    $stream = [IO.File]::Open($stagedPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try { $stream.Write($bytes, 0, $bytes.Length) } finally { $stream.Dispose() }
    Move-Item -LiteralPath $stagedPath -Destination $requestPath
    Write-Host ("Signing step {0}: {1}" -f $stem, ($Request | ConvertTo-Json -Compress))
    $deadline = [DateTime]::UtcNow.AddSeconds(90)
    while (-not (Test-Path -LiteralPath $responsePath)) {
        if ([DateTime]::UtcNow -ge $deadline) { throw "No response for $stem. Inspect the puppet log; no next request was submitted." }
        Start-Sleep -Milliseconds 200
    }
    $script:LastSigningResponse = Get-Content -LiteralPath $responsePath -Raw | ConvertFrom-Json
    if ($script:LastSigningResponse.status -ne 'dispatched') {
        throw ("Step {0} failed: {1}" -f $stem, $script:LastSigningResponse.error)
    }
    $script:SigningStep++
}
function Wait-SigningUi {
    param([hashtable]$Expected)
    Send-SigningStep @{op='await_ui'; expected=$Expected}
}
function Click-SigningControl {
    param([string]$Control)
    Send-SigningStep @{op='click'; control=$Control}
}
function Select-CurrentFixture {
    Send-SigningStep @{op='observe'}
    for ($attempt = 0; $attempt -lt 256; $attempt++) {
        if ($script:LastSigningResponse.observation.panel_current_fixture) { return }
        Send-SigningStep @{op='panel_control'; control='NEXT'}
    }
    throw 'The current exact fixture identity was not found in the consent panel.'
}
function Open-SigningReview {
    Select-CurrentFixture
    Send-SigningStep @{op='panel_control'; control='SIGN_REVIEW'}
    Wait-SigningUi @{state='READY'; sign_active=$false; cancel_focused=$true}
}
function Use-FixtureKey {
    param([string]$Role, [switch]$Create)
    Send-SigningStep @{op='key_role'; role=$Role}
    Click-SigningControl 'keys'
    Wait-SigningUi @{busy=$false}
    if ($Create) {
        Send-SigningStep @{op='type_fixture'; control='name'}
        Send-SigningStep @{op='type_fixture'; control='passphrase'}
        Send-SigningStep @{op='type_fixture'; control='confirmation'}
        Wait-SigningUi @{apply_active=$true}
        Click-SigningControl 'apply'
        Wait-SigningUi @{busy=$false; key_selected=$true}
    } else {
        Wait-SigningUi @{key_selected=$true}
    }
    Click-SigningControl 'use'
    Wait-SigningUi @{state='READY'; sign_active=$false; cosmetic_acknowledged=$false}
}
function Arm-FixtureSignature {
    param([string]$Ceremony = 'alternative')
    Send-SigningStep @{op='arm_fixture'; ceremony=$Ceremony}
    Wait-SigningUi @{state='READY'; cosmetic_acknowledged=$true; sign_active=$true}
}
function Sign-Fixture {
    param([int]$Total)
    Arm-FixtureSignature
    Click-SigningControl 'sign'
    Wait-SigningUi @{state='SIGNED'; sign_active=$false}
    Send-SigningStep @{op='await_history'; total=$Total; active=$Total}
    Click-SigningControl 'cancel'
}

# Review is not a save or signature, and key creation alone is not approval.
Send-SigningStep @{op='open_consent_panel'}
Open-SigningReview
Send-SigningStep @{op='await_history'; total=0; active=0}
Click-SigningControl 'edit'
Send-SigningStep @{op='type_fixture_source'; edited=$true}
Send-SigningStep @{op='editor_save'}
Wait-SigningUi @{state='READY'; server_status='SAVED'; sign_active=$false}
Send-SigningStep @{op='await_history'; total=0; active=0}
if ('editor_saved_ack' -notin $script:LastSigningResponse.observation.witnesses -or
    'save_did_not_sign' -notin $script:LastSigningResponse.observation.witnesses) {
    throw 'The real editor save was not acknowledged at its exact projected revision without an automatic signature.'
}
Use-FixtureKey -Role alice -Create
Arm-FixtureSignature -Ceremony pad
Click-SigningControl 'cancel'

# The accessible ceremony uses the same delayed gate; a second author invalidates it.
Open-SigningReview
Use-FixtureKey -Role alice
Arm-FixtureSignature
Send-SigningStep @{op='source_edit'; edited=$false}
Wait-SigningUi @{state='CANCELLED'; problem='STALE_TARGET'; sign_active=$false; cosmetic_acknowledged=$false}
Send-SigningStep @{op='click'; control='sign'; handled=$false}
Click-SigningControl 'cancel'

# Review the real remembered/current diff, then sign the newly acknowledged revision.
Open-SigningReview
Click-SigningControl 'view'
Wait-SigningUi @{view='SOURCE'}
Click-SigningControl 'view'
Wait-SigningUi @{view='DIFF'}
Send-SigningStep @{op='assert_review_diff'}
Use-FixtureKey -Role alice
Sign-Fixture -Total 1

if (-not $SkipSignerTrust) {
    # Alice is the only current signer, so this control unambiguously selects Alice.
    Select-CurrentFixture
    Send-SigningStep @{op='panel_control'; control='SIGNER'}
    Send-SigningStep @{op='panel_control'; control='TRUST_SIGNER'}
    $deadline = [DateTime]::UtcNow.AddSeconds(20)
    do {
        Send-SigningStep @{op='observe'}
        $confirm = @($script:LastSigningResponse.observation.confirmation_choices | Where-Object { $_.label -eq 'Confirm' -and $_.active })
        if ($confirm.Count -gt 0) { break }
        if ([DateTime]::UtcNow -ge $deadline) { throw 'The real signer confirmation did not become available.' }
    } while ($true)
    Send-SigningStep @{op='confirmation_button'; label='Confirm'}
    Send-SigningStep @{op='assert_consent'; authority='TRUSTED_SIGNER'; effective='ALLOWED'}

    # Bob appends a separate valid signature; Alice's scoped trust remains effective.
    Open-SigningReview
    Use-FixtureKey -Role bob -Create
    Sign-Fixture -Total 2
    $fixtureFingerprints = $script:LastSigningResponse.observation.fixture_fingerprints
    if ($fixtureFingerprints.alice -eq $fixtureFingerprints.bob) { throw 'Alice and Bob did not receive independent fixture keys.' }
    Send-SigningStep @{op='assert_consent'; authority='TRUSTED_SIGNER'; effective='ALLOWED'}
    Select-CurrentFixture
    Send-SigningStep @{op='panel_control'; control='DENY'}
    Send-SigningStep @{op='assert_consent'; state='DENIED'; authority='NONE'; effective='DENIED_BY_USER'}
}
Send-SigningStep @{op='close_review'}
Wait-SigningUi @{screen='none'}
Send-SigningStep @{op='finish'}
Write-Host ("Signing journey passed. Evidence: {0}" -f $controlRoot)
Write-Host ("Witnesses: {0}" -f ($script:LastSigningResponse.observation.witnesses -join ', '))
Write-Host 'Encrypted disposable fixture keys remain only inside this run directory; exact fixture consent and trust records were removed.'
