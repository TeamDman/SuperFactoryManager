Get-ChildItem -File ..\..\jars `
| ForEach-Object { 
    Write-Host "Removing $_"
    $_ | Remove-Item
}