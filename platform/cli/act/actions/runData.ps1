Push-Location ".."
try {
# Get to repos folder
$cwd = Get-Location
$expected = "D:\repos\Minecraft\SFM\repos"
if (-not $cwd -eq $expected) {
    Write-Host $cwd
    throw "This should be ran from a directory that is a child of D:\repos\Minecraft\SFM"
}


# Begin time measurement
$start = Get-Date

# Gather repos
$repo_clones = Get-ChildItem -Directory | Sort-Object

# Filter user choice
$chosen_names = $repo_clones `
| ForEach-Object { $_.Name } `
| Sort-Object -Descending `
| fzf `
    --multi `
    --bind "ctrl-a:select-all,ctrl-d:deselect-all,ctrl-t:toggle-all" `
    --header "Pick runData versions"
$chosen_repos = $repo_clones | Where-Object { $chosen_names -contains $_.Name }

# Perform build
foreach ($repo in $chosen_repos) {
    try {
        Push-Location $repo
        Write-Host "Running runData for $repo"
        New-Item -ItemType Directory -Force -Path "logs" | Out-Null
        $test_log_file = "logs\runData.log"
        .\gradlew.bat runData --no-daemon | Tee-Object -FilePath $test_log_file
        if ((Get-Content $test_log_file -Raw ) -notlike "*All providers took*") {
            Write-Warning "runData failed for $repo, check out `"$test_log_file`" for more information"
            break
        } else {
            Write-Host -ForegroundColor Green "runData succeeded for $repo"
        }
#        if ($? -eq $false) {
#            Write-Warning "runData failed for ${repo}"
#        }
    } finally {
        Pop-Location
    }
}



# Measure time
$end = Get-Date
$elapsed = $end - $start
Write-Host "runData performed for all versions, took $elapsed"

} finally {
    Pop-Location
}