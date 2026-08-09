# You should copy the contents of this file to your profile instead of sourcing this file in your profile to prevent oopsie-daisies
#region c java files
function jf {
    param(
        [string]$StartDirectory = "."
    )

    $selected = Get-ChildItem -Path $StartDirectory -Recurse -File -ErrorAction SilentlyContinue `
    | Where-Object { $_.Extension -notin @('.class', '.patch') } `
    | ForEach-Object { $_.FullName } `
    | fzf --multi --height=80% --layout=reverse --bind "ctrl-a:select-all,ctrl-d:deselect-all,ctrl-t:toggle-all"

    if ($LASTEXITCODE -ne 0) {
        return
    }

    $paths = @($selected) `
    | ForEach-Object { $_ -split "`r?`n" } `
    | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }

    if ($paths.Count -eq 0) {
        return
    }

    code -- $paths
}
#endregion


#region change source
function cs {
    # change which repo we are in 
    $repo_root = Get-Content $env:APPDATA\teamdman\sfm-propagate-changes\config\repo_root.txt
    $repo_parent = Split-Path $repo_root -Parent

    Get-ChildItem $repo_parent `
    | Where-Object { $_.Name -ne '.vscode' } `
    | ForEach-Object {
        $targets = @(Join-Path $_.FullName "platform\minecraft")
        if ($_.Name -eq "1.19.2") {
            $targets += Join-Path $_.FullName "platform\cli\sfm-propagate-changes"
        }
        $targets
    } `
    | Where-Object { Test-Path $_ } `
    | cloud_terrastodon pick `
    | Set-Location
    # substitute `cloud_terrastodon pick` with `fzf` or whatever you want to use for picking
}
#endregion


# compare version
function cv {
    param(
        [Parameter(Position = 0, Mandatory = $true)]
        [string]$Path
    )

    function Normalize-DirectoryPath {
        param([Parameter(Mandatory = $true)][string]$InputPath)

        [System.IO.Path]::GetFullPath($InputPath).Replace('/', '\').TrimEnd('\')
    }

    function Test-PathIsInsideDirectory {
        param(
            [Parameter(Mandatory = $true)][string]$ChildPath,
            [Parameter(Mandatory = $true)][string]$ParentPath
        )

        $child = Normalize-DirectoryPath $ChildPath
        $parent = Normalize-DirectoryPath $ParentPath

        $child.Equals($parent, [System.StringComparison]::OrdinalIgnoreCase) -or
        $child.StartsWith("$parent\", [System.StringComparison]::OrdinalIgnoreCase)
    }

    $repo_root = sfm-propagate-changes.exe repo-root show
    $repo_parent = Normalize-DirectoryPath (Split-Path $repo_root -Parent)
    $path_full = Normalize-DirectoryPath $Path
    $worktrees = git -C "$repo_root" worktree list --porcelain `
    | Select-String "worktree " `
    | ForEach-Object { Normalize-DirectoryPath ($_.ToString().Substring("worktree ".Length)) } `
    | Where-Object {
        Test-PathIsInsideDirectory -ChildPath $_ -ParentPath $repo_parent
    }
    $path_worktree = $worktrees `
    | Sort-Object Length -Descending `
    | Where-Object {
        Test-PathIsInsideDirectory -ChildPath $path_full -ParentPath $_
    } `
    | Select-Object -First 1
    if (-not $path_worktree) {
        Write-Error "Path '$Path' is not inside a known worktree."
        return
    }
    Write-Host "Path '$path_full' is in worktree '$path_worktree'."
    $worktrees_except_path_worktree = $worktrees | Where-Object { $_ -ne $path_worktree }
    $chosen = $worktrees_except_path_worktree | ct pick --single
    if (-not $chosen) {
        Write-Host "Cancelled."
        return
    }
    $chosen_full = Normalize-DirectoryPath $chosen
    $path_relative_to_worktree = [System.IO.Path]::GetRelativePath($path_worktree, $path_full)
    $path_in_chosen = [System.IO.Path]::GetFullPath((Join-Path -Path $chosen_full -ChildPath $path_relative_to_worktree))
    Write-Host "Comparing '$Path' to '$path_in_chosen'"
    code --diff "$Path" "$path_in_chosen"
}