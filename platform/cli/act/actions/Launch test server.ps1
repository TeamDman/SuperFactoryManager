$servers_path = "..\..\test servers"
$servers = Get-ChildItem -Directory -Path $servers_path
$chosen = $servers `
| ForEach-Object { $_.Name } `
| Sort-Object -Descending `
| fzf `
    --multi `
    --bind "ctrl-a:select-all,ctrl-d:deselect-all,ctrl-t:toggle-all" `
    --header "Pick servers to run"
$servers = $servers | Where-Object { $chosen -contains $_.Name }
foreach ($server in $servers) {
    Push-Location $server
    try {
        Write-Host "Launching $($server.Name)"
        & .\run.bat
    } finally {
        Pop-Location
    }
}