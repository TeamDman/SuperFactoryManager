Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

& sfm-propagate-changes.exe github release now @args
exit $LASTEXITCODE
