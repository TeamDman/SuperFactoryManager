$branches = @(
    "1.19.2",
    "1.19.4",
    "1.20",
    "1.20.1",
    "1.20.2",
    "1.20.3",
    "1.20.4",
    "1.21.0",
    "1.21.1"
)
foreach ($branch in $branches) {
    $dest = Join-Path ".." $branch
    $dir_exists = Test-Path "$dest"
    if (-not $dir_exists) {
        Write-Host "Creating worktree for branch $branch at $dest"
        git worktree add "$dest" "$branch"
        start-sleep -seconds 1
    }
}