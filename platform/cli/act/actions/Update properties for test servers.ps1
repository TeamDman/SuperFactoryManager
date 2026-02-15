$servers_path = "..\..\test servers"
$servers = Get-ChildItem -Directory -Path $servers_path `
| Sort-Object -Property Name

$lowest_properties = $servers[0].GetFiles() | Where-Object { $_.Name -like "server.properties" }

$port = 25565

foreach ($server in $servers) {
    $version = $server.Name
    $properties = Join-Path -Path $servers_path -ChildPath "$version\server.properties"
    if (-not (Test-Path $properties)) {
        Write-Host "Copying properties to $version"
        Copy-Item -Path $lowest_properties -Destination $properties
    } else {
        Write-Host "$version already has properties file"
    }
}