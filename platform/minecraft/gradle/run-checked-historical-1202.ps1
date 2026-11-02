param(
    [ValidateSet('help', 'compileJava', 'jar')]
    [string]$Task = 'jar',
    [switch]$RerunTasks
)
$ErrorActionPreference = 'Stop'
$checkout = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$project = Join-Path $checkout 'platform\minecraft\projections\sfm-4.35.0\mc-1.20.2'
$cache = Join-Path $checkout 'sfm-toolchain-data'
$gradleHome = Join-Path $cache 'gradle\bootstrap-probe-home'
$gradle = Join-Path $cache 'gradle\distributions\gradle-8.1.1\gradle-8.1.1\bin\gradle.bat'
$distribution = Join-Path $cache 'gradle\distributions\gradle-8.1.1-bin.zip'
$settingsInit = Join-Path $PSScriptRoot 'checked-historical-1202-settings.init.gradle'
$projectInit = Join-Path $PSScriptRoot 'checked-historical-1202.init.gradle'
$manifestPath = Join-Path $PSScriptRoot 'checked-historical-1202-game-inputs.json'
$lockPath = Join-Path $project 'sfm-toolchain.lock.json'
if (!(Test-Path -LiteralPath (Join-Path $checkout '.git')) -or
    !(Test-Path -LiteralPath $project -PathType Container) -or
    !(Test-Path -LiteralPath $gradleHome -PathType Container)) {
    throw 'Selected repository projection or owned Gradle home is missing'
}
$marker = Join-Path $cache '.sfm-native-cache-root.json'
$expectedMarker = ConvertTo-Json -InputObject @('sfm:native_cache_root@1', $cache) -Compress
if (!(Test-Path -LiteralPath $marker -PathType Leaf) -or
    [IO.File]::ReadAllText($marker) -cne $expectedMarker) {
    throw 'SFM repository cache marker belongs to another root'
}
$expected = @{
    'checked-historical-1202-settings.init.gradle' = 'A6860E1BFB5FFEE2EF4332ADE3F2B4879A5DB0E587454FE0C8051B9BB94EE49A'
    'checked-historical-1202.init.gradle' = 'EC9CCED1BB45B53CB6887005D999C1FA1DF76882E297C8D57C7326F7B30DA0D3'
    'checked-historical-1202-game-inputs.json' = '19564781691C2446AB8FECE508CDABA4E2D31459A57C457644E82A38F1C248C3'
    'checked-historical-1202-neoform-tools.json' = 'FDDBA754770C84B4C75176D09339CFD092C7A3A70846F4F92D8DC3ED6B127492'
    'checked-historical-1202-at804-input.json' = 'B2816C81E2CA8031E274C2DCF4D2DCD28C2657625B475A1262FDA5F83B01A964'
    'checked-historical-1202-antlr-inputs.json' = '0BB7273988B27078F5B0BD383666CDF0ABADC855971F343EF290B02870881928'
    'checked-historical-1202-libraries.json' = '8BD5CE04FA3820B964AF37E2AEA155D167A68A2958BADC6EF6BEBA73BC13FAE3'
}
foreach ($name in $expected.Keys) {
    $path = Join-Path $PSScriptRoot $name
    if (!(Test-Path -LiteralPath $path -PathType Leaf) -or
        (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -cne $expected[$name]) {
        throw "Checked historical Gradle route changed: $name"
    }
}
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if ($manifest.schema_version -ne 1 -or $manifest.projection -cne 'sfm-4.35.0/mc-1.20.2' -or
    $manifest.file_count -ne 6 -or $manifest.files.Count -ne 6 -or
    $manifest.cache_root -cne 'sfm-toolchain-data' -or
    (Get-FileHash -LiteralPath $lockPath -Algorithm SHA256).Hash -cne $manifest.selected_lock_sha256) {
    throw 'Checked historical Minecraft input selection or frozen lock changed'
}
$zipPin = Get-Content -LiteralPath (Join-Path $checkout 'platform\minecraft\core-liquid-template\build\supplements\gradle-historical-bootstrap-inputs.json') -Raw | ConvertFrom-Json
$selected = @($zipPin.artifacts | Where-Object { $_.kind -eq 'gradle-bin-distribution' -and $_.version -eq '8.1.1' })
if ($selected.Count -ne 1 -or $selected[0].bytes -ne 124643981 -or
    $selected[0].sha256 -cne 'e111cb9948407e26351227dabce49822fb88c37ee72f1d1582a69c68af2e702f' -or
    (Get-Item -LiteralPath $distribution).Length -ne $selected[0].bytes -or
    (Get-FileHash -LiteralPath $distribution -Algorithm SHA256).Hash -cne $selected[0].sha256.ToUpperInvariant() -or
    (Get-FileHash -LiteralPath $gradle -Algorithm SHA256).Hash -cne 'A558EAC6339A8DB4EC070B04D9DEEFDD0E4F0EB0D1A632EB90775865F9B1CEA8') {
    throw 'Checked Gradle 8.1.1 distribution or launcher changed'
}
if (!$env:JAVA_HOME -or !(Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe') -PathType Leaf)) {
    throw 'Set JAVA_HOME to a Java 17 JDK'
}
$javaVersion = & (Join-Path $env:JAVA_HOME 'bin\java.exe') -version 2>&1
if ($LASTEXITCODE -ne 0 -or (($javaVersion | Out-String) -notmatch 'version "17\.')) {
    throw 'Historical 1.20.2 Gradle requires Java 17'
}
$cacheBoundary = [IO.Path]::GetFullPath($cache).TrimEnd('\') + '\'
$projectBoundary = [IO.Path]::GetFullPath($project).TrimEnd('\') + '\'
foreach ($entry in $manifest.files) {
    $sourceRel = [string]$entry.source_path
    $gradleRel = [string]$entry.gradle_path
    $projectRel = [string]$entry.project_path
    if ($projectRel -cne ('.gradle/' + $gradleRel)) { throw 'Unexpected NeoGradle project-local path' }
    foreach ($relative in @($sourceRel, $gradleRel, $projectRel)) {
        if (!$relative -or $relative -notmatch '^[A-Za-z0-9._/-]+$' -or
            @($relative.Split('/') | Where-Object { $_ -in @('', '.', '..') }).Count) {
            throw 'Unsafe checked historical Minecraft input path'
        }
    }
    $source = [IO.Path]::GetFullPath((Join-Path $cache $sourceRel))
    $target = [IO.Path]::GetFullPath((Join-Path $project $projectRel))
    if (!$source.StartsWith($cacheBoundary, [StringComparison]::OrdinalIgnoreCase) -or
        !$target.StartsWith($projectBoundary, [StringComparison]::OrdinalIgnoreCase) -or
        !(Test-Path -LiteralPath $source -PathType Leaf) -or
        (Get-Item -LiteralPath $source).Length -ne $entry.bytes -or
        (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -cne $entry.sha256) {
        throw "Checked historical Minecraft source differs: $sourceRel"
    }
    $cursor = Split-Path -Parent $target
    while ($cursor.StartsWith($projectBoundary, [StringComparison]::OrdinalIgnoreCase)) {
        if ((Test-Path -LiteralPath $cursor) -and
            ((Get-Item -LiteralPath $cursor).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw "NeoGradle project-local cache contains a link: $cursor"
        }
        $cursor = Split-Path -Parent $cursor
    }
    if (Test-Path -LiteralPath $target) {
        if (!(Test-Path -LiteralPath $target -PathType Leaf) -or
            ((Get-Item -LiteralPath $target).Attributes -band [IO.FileAttributes]::ReparsePoint) -or
            (Get-Item -LiteralPath $target).Length -ne $entry.bytes -or
            (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -cne $entry.sha256) {
            throw "NeoGradle project-local Minecraft input differs: $projectRel"
        }
    } else {
        $parent = Split-Path -Parent $target
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
        $temporary = Join-Path $parent ((Split-Path -Leaf $target) + '.tmp.' + [guid]::NewGuid().ToString('N'))
        try {
            [IO.File]::Copy($source, $temporary, $false)
            if ((Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash -cne $entry.sha256) {
                throw "NeoGradle project-local input copy differs: $projectRel"
            }
            [IO.File]::Move($temporary, $target, $false)
        } finally {
            if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force }
        }
    }
}
$libraryManifest = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'checked-historical-1202-libraries.json') -Raw | ConvertFrom-Json
$versionInput = @($manifest.files | Where-Object { $_.kind -ceq 'version-metadata' })
if ($libraryManifest.schema_version -ne 1 -or
    $libraryManifest.projection -cne 'sfm-4.35.0/mc-1.20.2' -or
    $libraryManifest.file_count -ne 88 -or $libraryManifest.files.Count -ne 88 -or
    $versionInput.Count -ne 1 -or
    $libraryManifest.version_json_sha256 -cne $versionInput[0].sha256 -or
    $libraryManifest.selected_lock_sha256 -cne $manifest.selected_lock_sha256 -or
    $libraryManifest.source_root -cne 'minecraft-toolchain/minecraft/libraries' -or
    $libraryManifest.gradle_root -cne 'caches/minecraft/libraries') {
    throw 'Checked historical library selection changed'
}
$versionJson = Join-Path $cache $versionInput[0].source_path
if ((Get-FileHash -LiteralPath $versionJson -Algorithm SHA256).Hash -cne $libraryManifest.version_json_sha256) {
    throw 'Frozen Minecraft version library metadata changed'
}
$librarySourceRoot = [IO.Path]::GetFullPath((Join-Path $cache $libraryManifest.source_root))
$libraryTargetRoot = [IO.Path]::GetFullPath((Join-Path $gradleHome $libraryManifest.gradle_root))
if (!$librarySourceRoot.StartsWith($cacheBoundary, [StringComparison]::OrdinalIgnoreCase) -or
    !$libraryTargetRoot.StartsWith(([IO.Path]::GetFullPath($gradleHome).TrimEnd('\') + '\'), [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Checked historical library root escaped owner'
}
$seenLibraries = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach ($item in $libraryManifest.files) {
    $relative = [string]$item.path
    if (!$relative -or $relative -notmatch '^[A-Za-z0-9._/-]+$' -or
        @($relative.Split('/') | Where-Object { $_ -in @('', '.', '..') }).Count -or
        !$seenLibraries.Add($relative) -or
        $item.url -cne ('https://libraries.minecraft.net/' + $relative)) {
        throw 'Unsafe or changed checked Minecraft library identity'
    }
    $source = [IO.Path]::GetFullPath((Join-Path $librarySourceRoot $relative))
    $target = [IO.Path]::GetFullPath((Join-Path $libraryTargetRoot $relative))
    if (!$source.StartsWith(($librarySourceRoot.TrimEnd('\') + '\'), [StringComparison]::OrdinalIgnoreCase) -or
        !$target.StartsWith(($libraryTargetRoot.TrimEnd('\') + '\'), [StringComparison]::OrdinalIgnoreCase) -or
        !(Test-Path -LiteralPath $source -PathType Leaf) -or
        (Get-Item -LiteralPath $source).Length -ne $item.bytes -or
        (Get-FileHash -LiteralPath $source -Algorithm SHA1).Hash -cne $item.sha1 -or
        (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -cne $item.sha256) {
        throw "Checked historical library source differs: $relative"
    }
    $cursor = Split-Path -Parent $target
    while ($cursor.StartsWith(([IO.Path]::GetFullPath($gradleHome).TrimEnd('\') + '\'), [StringComparison]::OrdinalIgnoreCase)) {
        if ((Test-Path -LiteralPath $cursor) -and
            ((Get-Item -LiteralPath $cursor).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw "Gradle library cache contains a link: $cursor"
        }
        $cursor = Split-Path -Parent $cursor
    }
    if (Test-Path -LiteralPath $target) {
        if (!(Test-Path -LiteralPath $target -PathType Leaf) -or
            ((Get-Item -LiteralPath $target).Attributes -band [IO.FileAttributes]::ReparsePoint) -or
            (Get-Item -LiteralPath $target).Length -ne $item.bytes -or
            (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -cne $item.sha256) {
            throw "Gradle-owned historical library differs: $relative"
        }
    } else {
        $parent = Split-Path -Parent $target
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
        $temporary = Join-Path $parent ((Split-Path -Leaf $target) + '.tmp.' + [guid]::NewGuid().ToString('N'))
        try {
            [IO.File]::Copy($source, $temporary, $false)
            if ((Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash -cne $item.sha256) {
                throw "Gradle library copy differs: $relative"
            }
            [IO.File]::Move($temporary, $target, $false)
        } finally {
            if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force }
        }
    }
}
if ((Get-PSDrive D).Free -lt 300000000) { throw 'Insufficient D: capacity for historical Gradle task' }
$gradleArgs = @('-p', $project, '--gradle-user-home', $gradleHome, '--offline', '--no-daemon', '--max-workers=1',
    '-I', $settingsInit, '-I', $projectInit)
if ($RerunTasks) { $gradleArgs += '--rerun-tasks' }
$gradleArgs += $Task
& $gradle @gradleArgs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }