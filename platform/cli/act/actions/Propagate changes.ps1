Push-Location ".."
try {
    $cwd = Get-Location
    $expected = "D:\repos\Minecraft\SFM\repos"
    if (-not $cwd -eq $expected) {
        Write-Host $cwd
        Write-Warning "This should be ran from a directory that is a child of D:\repos\Minecraft\SFM"
        return
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

    # Check if anything is uncommitted
    foreach ($repo in $repo_clones) {
        try {
            Push-Location $repo.FullName
            $old_git_branch = git rev-parse --abbrev-ref HEAD
            $expected_branch = $repo.Name -split " " | Select-Object -Last 1
            if (-not $old_git_branch -eq $expected_branch) {
                Write-Warning "Branch mismatch: dir=$repo expected=$expected_branch got=$old_git_branch"
                return
            }
    
            # New method to check for uncommitted changes
            $modifiedFiles = git diff --name-only
            $stagedFiles = git diff --cached --name-only
            $allChangedFiles = @($modifiedFiles) + @($stagedFiles) | Select-Object -Unique
    
            if ($allChangedFiles.Length -gt 0) {
                if ($allChangedFiles -eq @("mergapalooza.ps1")) {
                    Write-Warning "This script has uncommitted modifications! You have been warned!"
                } else {
                    Write-Warning "Uncommitted changes in ${repo}"
                    return
                    # $allChangedFiles | ForEach-Object { Write-Host " - $_" }
                    # return 1
                }
            }
        } catch {
            Write-Warning "Encountered error validating repo checkout status, stopping: $($_.Exception.Message)"
            return
        } finally {
            Pop-Location
        }
    }
    


    # We want to enumerate each pair of (older, one step newer) directories
    $pairs = $repo_clones | ForEach-Object {
        $older = $_
        $newer = $repo_clones | Where-Object { $_.Name -gt $older.Name } | Select-Object -First 1
        if ($newer) {
            [PSCustomObject]@{
                Older = $older
                Newer = $newer
            }
        }
    }
    <#
❯ $pairs 

Older                                             Newer
-----                                             -----
D:\Repos\Minecraft\SFM\SuperFactoryManager 1.19.2 D:\Repos\Minecraft\SFM\SuperFactoryManager 1.19.4
D:\Repos\Minecraft\SFM\SuperFactoryManager 1.19.4 D:\Repos\Minecraft\SFM\SuperFactoryManager 1.20.2
D:\Repos\Minecraft\SFM\SuperFactoryManager 1.20.2 D:\Repos\Minecraft\SFM\SuperFactoryManager 1.20.3
    #>

    
    foreach ($pair in $pairs) {
        try {
            Push-Location $pair.Newer

            try {
                Push-Location $pair.Older
                $old_git_branch = git rev-parse --abbrev-ref HEAD
            } finally {
                Pop-Location
            }
            
            $new_git_branch = git rev-parse --abbrev-ref HEAD

            if (-not $old_git_branch -or -not $new_git_branch) {
                Write-Warning "Failed to determine branch names for $pair"
                return
                break
            }

            Write-Host "`nFetching $old_git_branch to $new_git_branch repository"
            git fetch "$($pair.Older)" "$old_git_branch"
            if ($? -eq $false) {
                Write-Warning "Failed to fetch $old_git_branch from $($pair.Older)"
                return
                break
            }
            
            Write-Host "`nMerging $old_git_branch -> $new_git_branch"
            git merge FETCH_HEAD --no-edit
            if ($? -eq $false) {
                Write-Warning "Failed to merge $old_git_branch into $new_git_branch"
                return
                break
            }
        } catch {
            Write-Warning "Encountered error, stopping: $($_.Exception.Message)"
            return
        } finally {
            Pop-Location
        }
    }
} finally {
    Pop-Location
}