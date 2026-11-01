$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$evidence = Join-Path ([IO.Path]::GetTempPath()) ('sfm-checked-historical-1194-' + [guid]::NewGuid().ToString('N'))
$fixture = [IO.Path]::GetFullPath((Join-Path $evidence 'cache'))
$checkoutCache = Join-Path $repo 'sfm-toolchain-data'
$manifestPath = Join-Path $repo 'platform\minecraft\gradle\checked-historical-1194-inputs.json'
$productionInit = Join-Path $repo 'platform\minecraft\gradle\checked-historical-1194.init.gradle'
$testInit = Join-Path $evidence 'fixture-route.init.gradle'
$project = Join-Path $repo 'platform\minecraft\projections\sfm-4.35.0\mc-1.19.4'
$otherProject = Join-Path $repo 'platform\minecraft\projections\sfm-4.35.0\mc-1.20.1'
$wrapper = Join-Path $project 'gradlew.bat'
$jdk = $env:JAVA_HOME
$receipt = Join-Path $evidence 'checked-historical-1194-tests.json'
if (!(Test-Path -LiteralPath (Join-Path $repo '.git')) -or
    !$jdk -or !(Test-Path -LiteralPath (Join-Path $jdk 'bin\java.exe'))) {
    throw 'The selected checkout or JAVA_HOME is unavailable'
}
if (Test-Path -LiteralPath $evidence) { throw 'Unexpected test scratch collision' }
New-Item -ItemType Directory -Path $evidence | Out-Null
Write-Output "SFM test scratch: $evidence"
foreach ($path in @($fixture,$testInit,$receipt)) { if (Test-Path -LiteralPath $path) { throw "Fixture/evidence collision: $path" } }
if ((Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash -cne '641975CF7C079A06C013571A53298FCA7A301A2195BE57D2D3388D7FC5DA34C3') { throw 'Published manifest changed' }
$expectedInit = (Get-FileHash -LiteralPath $productionInit -Algorithm SHA256).Hash
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if ($manifest.files.Count -ne 53) { throw 'Published selection changed' }
$active = @(Get-Process -Name java,javaw,cargo,rustc,sfm-propagate-changes -ErrorAction SilentlyContinue)
if ($active.Count) { throw 'Task-capable process active; inspect ownership before Gradle tests' }
$fixtureDrive = [IO.DriveInfo]::new([IO.Path]::GetPathRoot($evidence))
$checkoutDrive = [IO.DriveInfo]::new([IO.Path]::GetPathRoot($repo))
if ($fixtureDrive.AvailableFreeSpace -lt 500000000 -or $checkoutDrive.AvailableFreeSpace -lt 1000000000) { throw 'Insufficient fixture or Gradle headroom' }
$head = git -C $repo rev-parse HEAD
$results = [Collections.Generic.List[object]]::new()
$priorJava = $env:JAVA_HOME
function Copy-Checked([string]$relative) {
    $source = Join-Path (Join-Path $checkoutCache 'minecraft-toolchain') $relative
    $target = Join-Path (Join-Path $fixture 'minecraft-toolchain') $relative
    $parent = Split-Path -Parent $target
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
    Copy-Item -LiteralPath $source -Destination $target -Force
    if ((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -cne (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash) { throw "Fixture copy differs: $relative" }
}
function Invoke-Case([string]$name, [string]$init, [string]$selectedProject, [string]$expected, [bool]$shouldPass) {
    $out = Join-Path $evidence ("$name.out.txt")
    $err = Join-Path $evidence ("$name.err.txt")
    foreach ($path in @($out,$err)) { if (Test-Path -LiteralPath $path) { throw "Evidence collision: $path" } }
    $ErrorActionPreference = 'Continue'
    Push-Location $project
    try { & $wrapper --offline --no-daemon --project-dir $selectedProject --init-script $init help 1> $out 2> $err; $code = $LASTEXITCODE }
    finally { Pop-Location }
    $ErrorActionPreference = 'Stop'
    $combined = ((Get-Content -LiteralPath $out -Raw) + "`n" + (Get-Content -LiteralPath $err -Raw))
    $matched = $combined.Contains($expected)
    $accepted = if ($shouldPass) { $code -eq 0 -and $matched } else { $code -ne 0 -and $matched -and !$combined.Contains('> Task :help') }
    $results.Add([ordered]@{case=$name;exit_code=$code;expected=$expected;matched=$matched;accepted=$accepted;stdout=$out;stderr=$err})
    Write-Output "$name exit=$code expected_matched=$matched accepted=$accepted"
    if (!$accepted) { throw "Focused route case failed: $name" }
}
try {
    $env:JAVA_HOME = $jdk
    New-Item -ItemType Directory -Path (Join-Path $fixture 'minecraft-toolchain') -Force | Out-Null
    $fixtureMarker = Join-Path $fixture '.sfm-native-cache-root.json'
    $ownedMarker = ConvertTo-Json -InputObject @('sfm:native_cache_root@1', $fixture) -Compress
    [IO.File]::WriteAllText($fixtureMarker, $ownedMarker, [Text.UTF8Encoding]::new($false))
    foreach ($entry in $manifest.files) { Copy-Checked $entry.path }
    Copy-Checked $manifest.historical_version_snapshot.path
    $sourceInit = Get-Content -LiteralPath $productionInit -Raw
    $needle = "def logicalCache = checkout.resolve('sfm-toolchain-data')"
    if (!$sourceInit.Contains($needle)) { throw 'Production cache-root contract changed' }
    $replacement = "def logicalCache = new File('$($fixture.Replace('\','/'))').toPath()"
    $testSource = $sourceInit.Replace($needle,$replacement)
    [IO.File]::WriteAllText($testInit,$testSource,[Text.UTF8Encoding]::new($false))
    Invoke-Case 'positive-fixture' $testInit $project 'SFM_CHECKED_HISTORICAL_1194 modules=8 direct=14 minecraft=23' $true
    $snapshotFixture = Join-Path (Join-Path $fixture 'minecraft-toolchain') $manifest.historical_version_snapshot.path
    $snapshotBytes = [IO.File]::ReadAllBytes($snapshotFixture)
    $snapshotBytes[0] = $snapshotBytes[0] -bxor 1
    [IO.File]::WriteAllBytes($snapshotFixture, $snapshotBytes)
    Invoke-Case 'version-snapshot-hash-mismatch' $testInit $project 'SFM historical version snapshot differs' $false
    Copy-Checked $manifest.historical_version_snapshot.path
    $selected = $manifest.files | Where-Object { $_.coordinate -eq 'net.minecraftforge:javafmllanguage:1.19.4-45.0.9' -and $_.kind -eq 'jar' } | Select-Object -First 1
    $mutant = Join-Path (Join-Path $fixture 'minecraft-toolchain') $selected.path
    $bytes = [IO.File]::ReadAllBytes($mutant); $bytes[0] = $bytes[0] -bxor 1; [IO.File]::WriteAllBytes($mutant,$bytes)
    Invoke-Case 'hash-mismatch' $testInit $project 'SFM checked payload differs' $false
    Copy-Checked $selected.path
    $pom = $manifest.files | Where-Object { $_.coordinate -eq 'mezz.jei:jei-1.19.4-forge:13.1.0.16' -and $_.kind -eq 'pom' } | Select-Object -First 1
    $pomFixture = Join-Path (Join-Path $fixture 'minecraft-toolchain') $pom.path
    Remove-Item -LiteralPath $pomFixture
    Invoke-Case 'missing-pom' $testInit $project 'SFM checked payload differs' $false
    Copy-Checked $pom.path
    $child = $manifest.files | Where-Object { $_.coordinate -eq 'mezz.jei:jei-1.19.4-core:13.1.0.16' -and $_.kind -eq 'jar' } | Select-Object -First 1
    $childFixture = Join-Path (Join-Path $fixture 'minecraft-toolchain') $child.path
    Remove-Item -LiteralPath $childFixture
    Invoke-Case 'missing-module-jar' $testInit $project 'SFM checked payload differs' $false
    Copy-Checked $child.path
    $wrongMarker = ConvertTo-Json -InputObject @('sfm:native_cache_root@1', (Join-Path $fixture 'another-owner')) -Compress
    [IO.File]::WriteAllText($fixtureMarker, $wrongMarker, [Text.UTF8Encoding]::new($false))
    Invoke-Case 'wrong-cache-origin' $testInit $project 'SFM repository cache marker belongs to another root' $false
    [IO.File]::WriteAllText($fixtureMarker, $ownedMarker, [Text.UTF8Encoding]::new($false))
    Invoke-Case 'wrong-projection' $productionInit $otherProject 'SFM historical checked route requires the selected repository projection' $false
    if ((Get-FileHash -LiteralPath $productionInit -Algorithm SHA256).Hash -cne $expectedInit -or (git -C $repo rev-parse HEAD) -cne $head) { throw 'Source changed during focused tests' }
} finally {
    $env:JAVA_HOME = $priorJava
    $verified = [IO.Path]::GetFullPath($fixture)
    $boundary = [IO.Path]::GetFullPath($evidence) + [IO.Path]::DirectorySeparatorChar
    if ($verified.StartsWith($boundary,[StringComparison]::OrdinalIgnoreCase) -and [IO.Path]::GetFileName($verified) -eq 'cache') {
        Remove-Item -LiteralPath $verified -Recurse -Force -ErrorAction Stop
    }
}
$record = [ordered]@{schema='sfm:historical_1194_checked_route_negative_tests@1';head=$head;production_init_sha256=$expectedInit;manifest_sha256=(Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash;fixture_copied_files=54;results=@($results);fixture_removed=!(Test-Path -LiteralPath $fixture)}
[IO.File]::WriteAllText($receipt,(ConvertTo-Json $record -Depth 7)+"`n",[Text.UTF8Encoding]::new($false))
if ($results.Count -ne 7 -or @($results | Where-Object { !$_.accepted }).Count -ne 0) { exit 1 }
$verifiedRun = [IO.Path]::GetFullPath($evidence)
$tempBoundary = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
if (!$verifiedRun.StartsWith($tempBoundary,[StringComparison]::OrdinalIgnoreCase) -or
    !([IO.Path]::GetFileName($verifiedRun).StartsWith('sfm-checked-historical-1194-'))) {
    throw "Refusing to remove unexpected test scratch: $verifiedRun"
}
Remove-Item -LiteralPath $verifiedRun -Recurse -Force
Write-Output 'Seven checked historical Gradle route cases passed; owned fixture removed.'
