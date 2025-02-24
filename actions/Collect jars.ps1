Push-Location ".."
try {
    # Get to repos folder
    $cwd = Get-Location
    $expected = "D:\repos\Minecraft\SFM\repos"
    if (-not $cwd.Path -eq $expected) {
        Write-Host $cwd.Path
        throw "This should be ran from a directory that is a child of D:\repos\Minecraft\SFM"
    }

    # Collect jars
    Write-Host "Collecting jars"
    $outdir = "..\jars"
    New-Item -ItemType Directory -Path $outdir -ErrorAction SilentlyContinue

    # Fetch all jar files in the build/libs directories
    $jars = Get-ChildItem `
        | ForEach-Object {
            Get-ChildItem -File -Path "$_\build\libs\" -Filter "*.jar"
        }

    # Sort and filter jars by semantic version
#    $sortedJars = $jars | ForEach-Object {
#        Write-Host "Interpreting name $($_.Name)"
#        $nameParts = $_.Name -split '-'
#        Write-Host "Detected $($nameParts.Count) parts"
#        $fullPath = $_.FullName
#        $major = $nameParts[1]
#        if ($nameParts[2] -eq $null) {
#            Write-Host "Found sus:"
#            $nameParts | Format-List
#        }
#        $minor = [int]($nameParts[2].Split('.')[1])
#        $patch = $nameParts[3]
#        Write-Host "Major: $major, Minor: $minor, Patch: $patch"
#        [PSCustomObject]@{
#            FullPath = $fullPath
#            Major = $major
#            Minor = $minor
#            Patch = $patch
#        }
#    } | Sort-Object -Property Major, Minor -Descending | Group-Object -Property Major | ForEach-Object {
#        $_.Group | Sort-Object -Property Minor, Patch -Descending | Select-Object -First 1
#    }
    ########
    Write-Host "Collecting jars"
    $parsedList = @()

    foreach ($jar in $jars) {
        Write-Host "`nInterpreting name $($jar.Name)"

        # Split by '-' to get parts like:
        # "Super Factory Manager (SFM)", "1.19.2", "4.20.0.jar"
        $nameParts = $jar.Name -split '-'
        Write-Host "Detected $($nameParts.Count) parts"

        # We check to ensure we have enough parts to parse consistently.
        # If there's only 2 parts or fewer, we'll treat it as 'suspicious'
        # and handle it. Modify as you wish!
        if ($nameParts.Count -lt 3) {
            Write-Host "Found suspicious entry (not enough parts):"
            $nameParts | Format-List
            # Either skip or handle differently. For now let's just skip.
            continue
        }

        # Example extraction:
        $fullPath = $jar.FullName

        # nameParts[1] might be something like "1.19.4" -> treat it as $major
        $major = $nameParts[1]

        # nameParts[2] might be something like "4.20.0.jar"
        # If you're purely interested in the numeric portion, remove ".jar"
        $afterMajor = $nameParts[2] -replace '\.jar$', ''

        # Let's assume $afterMajor is "4.20.0" or something. We'll split by '.'
        $minorSegments = $afterMajor -split '\.'

        # If you expect $afterMajor always to have a pattern like X.Y or X.Y.Z
        # then you can do:
        $minor = 0
        if ($minorSegments.Count -ge 2) {
            # We use the second element after splitting "4.20.0" -> "20"
            $minor = [int]$minorSegments[1]
        }

        $patch = ""
        if ($minorSegments.Count -ge 3) {
            # We use the third element after splitting "4.20.0" -> "0"
            $patch = $minorSegments[2]
        }

        Write-Host "Major: $major, Minor: $minor, Patch: $patch"

        # Create your custom object
        $item = [PSCustomObject]@{
            FullPath = $fullPath
            Major    = $major
            Minor    = $minor
            Patch    = $patch
        }

        $parsedList += $item
    }

    # Now do your sorting & grouping logic.
    # Sort by Major (desc), then Minor (desc).
    $parsedList = $parsedList | Sort-Object -Property Major, Minor -Descending

    # Group by Major.
    $groups = $parsedList | Group-Object -Property Major

    # Take only the top Minor/Patch for each Major.
    $finalList = @()
    foreach ($group in $groups) {
        $topItem = $group.Group | Sort-Object -Property Minor, Patch -Descending | Select-Object -First 1
        $finalList += $topItem
    }

    $sortedJars = $finalList

    Write-Host "`nFinal jars after grouping and sorting:"
    $sortedJars | Format-Table

    ########

    # Copy selected jars to the output directory
    $sortedJars | ForEach-Object {
        Copy-Item -Path $_.FullPath -Destination $outdir
    }

    # Display output directory
    Get-ChildItem $outdir
} finally {
    Pop-Location
}
