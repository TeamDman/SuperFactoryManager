Push-Location ".."
try {
    $cwd = Get-Location
    $expected = "D:\repos\Minecraft\SFM\repos"
    if (-not $cwd -eq $expected) {
        Write-Host $cwd
        throw "This should be ran from a directory that is a child of D:\repos\Minecraft\SFM"
    }

    
    $repo_clones = Get-ChildItem -Directory | Sort-Object
    <#
❯ $repo_clones

    Directory: D:\Repos\Minecraft\SFM

Mode                 LastWriteTime         Length Name
----                 -------------         ------ ----
d----          2024-04-14  1:41 PM                SuperFactoryManager 1.19.2
d----          2024-04-14  1:39 PM                SuperFactoryManager 1.19.4
d----          2024-04-14  1:39 PM                SuperFactoryManager 1.20
d----          2024-04-14  1:39 PM                SuperFactoryManager 1.20.1
d----          2024-04-14  1:39 PM                SuperFactoryManager 1.20.2
d----          2024-04-14  1:39 PM                SuperFactoryManager 1.20.3
    #>

    # Perform build
    foreach ($repo in $repo_clones) {
        try {
            Push-Location $repo
            # Ultimate edition
#            idea . &
            # Community edition
            idea2 . &
            # 2023.3.8 community edition
#            start "$Env:LOCALAPPDATA\Programs\IntelliJ IDEA Community Edition 2\bin\idea64.exe" . &
            Start-Sleep -Seconds 3
        } finally {
            Pop-Location
        }
    }
} finally {
    Pop-Location
}