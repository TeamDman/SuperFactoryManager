param(
    [string]$Source,
    [string]$Output
)

if (-not (Test-Path $Source)) {
    Write-Error "Source image '$Source' not found."
    exit 1
}

Write-Host "Generating $Output from $Source"
magick convert -background transparent "$Source" -define icon:auto-resize=16,24,32,48,64,72,96,128,256 "$Output"
