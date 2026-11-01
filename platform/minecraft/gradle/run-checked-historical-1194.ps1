param(
    [ValidateSet('help', 'compileJava', 'jar')]
    [string]$Task = 'jar',
    [switch]$RerunTasks
)
$ErrorActionPreference = 'Stop'
$checkout = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$project = Join-Path $checkout 'platform\minecraft\projections\sfm-4.35.0\mc-1.19.4'
$init = Join-Path $PSScriptRoot 'checked-historical-1194.init.gradle'
$manifest = Join-Path $PSScriptRoot 'checked-historical-1194-inputs.json'
$wrapper = Join-Path $project 'gradlew.bat'
if (!(Test-Path -LiteralPath (Join-Path $checkout '.git')) -or
    !(Test-Path -LiteralPath $wrapper -PathType Leaf) -or
    !(Test-Path -LiteralPath $init -PathType Leaf) -or
    !(Test-Path -LiteralPath $manifest -PathType Leaf)) {
    throw 'The selected SFM checkout, historical projection, or checked Gradle inputs are missing'
}
if (!$env:JAVA_HOME -or !(Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    throw 'Set JAVA_HOME to an installed Java 17 JDK before running the historical 1.19.4 Gradle recipe'
}
$javaVersion = & (Join-Path $env:JAVA_HOME 'bin\java.exe') -version 2>&1
if ($LASTEXITCODE -ne 0 -or (($javaVersion | Out-String) -notmatch 'version "17\.')) {
    throw 'The historical 1.19.4 Gradle recipe requires Java 17'
}
$arguments = @('--offline', '--no-daemon', '--init-script', $init)
if ($RerunTasks) { $arguments += '--rerun-tasks' }
$arguments += $Task
Push-Location $project
try {
    & $wrapper @arguments
    $result = $LASTEXITCODE
} finally {
    Pop-Location
}
if ($result -ne 0) { exit $result }
