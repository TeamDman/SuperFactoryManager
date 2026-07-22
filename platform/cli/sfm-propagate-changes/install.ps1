$installerRevisionEnvironmentName = "SFM_PROPAGATE_CHANGES_INSTALL_GIT_REVISION"
$installerWorktreeRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..\..")).Path
$installerSafeDirectoryArgument = "safe.directory=$installerWorktreeRoot"
$installerRevision = [string](& git -c $installerSafeDirectoryArgument -C $installerWorktreeRoot rev-parse --verify --short=9 HEAD)
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to determine the sfm-propagate-changes worktree revision."
    exit $LASTEXITCODE
}
$installerRevision = $installerRevision.Trim()
if ($installerRevision -notmatch '^[0-9a-f]{9}$') {
    Write-Error "Git returned an invalid sfm-propagate-changes worktree revision: $installerRevision"
    exit 1
}

$previousInstallerRevision = [Environment]::GetEnvironmentVariable(
    $installerRevisionEnvironmentName,
    [EnvironmentVariableTarget]::Process
)
[Environment]::SetEnvironmentVariable(
    $installerRevisionEnvironmentName,
    $installerRevision,
    [EnvironmentVariableTarget]::Process
)

try {
    & cargo install --path $PSScriptRoot --locked --offline
    $installExitCode = $LASTEXITCODE
} finally {
    [Environment]::SetEnvironmentVariable(
        $installerRevisionEnvironmentName,
        $previousInstallerRevision,
        [EnvironmentVariableTarget]::Process
    )
}

if ($installExitCode -ne 0) {
    exit $installExitCode
}
