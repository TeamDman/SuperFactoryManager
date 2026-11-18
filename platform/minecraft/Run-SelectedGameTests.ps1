[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$Projection,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$Pattern
)

$ErrorActionPreference = 'Stop'
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$cliArgs = @(
    'game-test', 'run-server',
    '--repo-root', $repoRoot,
    '--projection', $Projection,
    '--filter', $Pattern
)

& sfm-propagate-changes @cliArgs
exit $LASTEXITCODE
