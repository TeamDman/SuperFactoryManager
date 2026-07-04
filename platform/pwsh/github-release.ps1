Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

& sfm-propagate-changes.exe github release now --branch core @args
exit $LASTEXITCODE
