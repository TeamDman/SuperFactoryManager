function Get-JarForVersion {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]
        $version
    )
    $jars = Get-ChildItem -Path "D:\Repos\Minecraft\SFM\jars"
    foreach ($jar in $jars) {
        if ($jar.Name -like "*-MC$version-*") {
            return $jar
        }
    }
    return $null
}

$servers_path = "..\..\test servers"
$servers = Get-ChildItem -Directory -Path $servers_path

foreach ($server in $servers) {
    $version = $server.Name
    $jar = Get-JarForVersion -version $version
    if ($null -eq $jar) {
        Write-Error "No jar found for version $version"
        return
    }
    $mods_folder = Join-Path -Path $servers_path -ChildPath "$version\mods"
    New-Item -ItemType Directory -Path $mods_folder -Force | Out-Null
    Get-ChildItem -File -Filter "*Super Factory Manager*" $mods_folder `
    | ForEach-Object {
        Write-Host "Removing old version $_"
        Remove-Item -LiteralPath $_.FullName
    }
    Write-Host "Copying $jar to $($mods_folder)"
    Copy-Item -Path $jar -Destination $mods_folder
}