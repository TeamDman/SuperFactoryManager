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
            Write-Host "Running runGameTestServer for $repo"
            $test_log_file = "logs\test.log"
            New-Item -ItemType Directory -Path "logs" -ErrorAction SilentlyContinue
            Clear-Content $test_log_file -ErrorAction SilentlyContinue
            .\gradlew.bat runGameTestServer --no-daemon | Tee-Object -FilePath $test_log_file
            # Gradle 
            if ((Get-Content $test_log_file -Raw ) -notlike "*All * required tests passed :)*") {
                Write-Warning "Test failed for $repo, check out `"$test_log_file`" for more information"
                return
            } else {
                Write-Host -ForegroundColor Green "All tests passed for $repo"
            }
        } finally {
            Pop-Location
        }
    }

    # Measure time
    $end = Get-Date
    $elapsed = $end - $start
    Write-Host "All versions tested, took $elapsed"
} finally {
    Pop-Location
}