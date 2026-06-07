param(
    [Parameter(Mandatory = $true)]
    [string]$Pattern,

    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$AdditionalGradleArgs = @()
)

$gradleArgs = @(
    "--no-daemon",
    "runGameTestServer",
    "-PsfmGameTestSelection=$Pattern"
) + $AdditionalGradleArgs

Write-Host "Running SFM game tests matching '$Pattern'"
& "$PSScriptRoot\gradlew.bat" @gradleArgs

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "Note: on 1.19.2, Forge GameTest shutdown may still report a Gradle failure after the tests finish."
    Write-Host "Check runGameTest\\logs\\latest.log for the authoritative pass/fail result."
}

exit $LASTEXITCODE
