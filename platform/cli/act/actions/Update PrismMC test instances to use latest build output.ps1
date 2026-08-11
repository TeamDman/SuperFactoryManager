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

$instances = @{
    "C:\Users\TeamD\AppData\Roaming\PrismLauncher\instances\sfm test 1.19.2" = "1.19.2"
    "C:\Users\TeamD\AppData\Roaming\PrismLauncher\instances\sfm test 1.19.4" = "1.19.4"
    "C:\Users\TeamD\AppData\Roaming\PrismLauncher\instances\sfm test 1.20"   = "1.20"
    "C:\Users\TeamD\AppData\Roaming\PrismLauncher\instances\sfm test 1.20.1" = "1.20.1"
    "C:\Users\TeamD\AppData\Roaming\PrismLauncher\instances\sfm test 1.20.2" = "1.20.2"
    "C:\Users\TeamD\AppData\Roaming\PrismLauncher\instances\sfm test 1.20.3" = "1.20.3"
    "C:\Users\TeamD\AppData\Roaming\PrismLauncher\instances\sfm test 1.20.4" = "1.20.4"
    "C:\Users\TeamD\AppData\Roaming\PrismLauncher\instances\sfm test 1.21.0" = "1.21"
    "C:\Users\TeamD\AppData\Roaming\PrismLauncher\instances\sfm test 1.21.1" = "1.21.1"
}
foreach ($instance in $instances.GetEnumerator()) {
    $jar = Get-JarForVersion -version $instance.Value
    if ($null -eq $jar) {
        Write-Error "No jar found for version $($instance.Value)"
        return
    }
    $mods_folder = Join-Path -Path $instance.Name -ChildPath ".minecraft\mods"
    New-Item -ItemType Directory -Path $mods_folder -Force | Out-Null
    Get-ChildItem -File -Filter "*Super Factory Manager*" $mods_folder `
    | ForEach-Object {
        Write-Host "Removing old version $_"
        Remove-Item -LiteralPath $_.FullName
    }
    Write-Host "Copying $jar to $($mods_folder)"
    Copy-Item -Path $jar -Destination $mods_folder
}