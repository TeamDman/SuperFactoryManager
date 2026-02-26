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
    | ct pick `
    | Set-Location
}
#endregion
