param([string]$ProjectRoot)

$ErrorActionPreference = 'Stop'

try {
    if (!$ProjectRoot) {
        $ProjectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
    } else {
        $ProjectRoot = [IO.Path]::GetFullPath($ProjectRoot)
    }
    $checkout = [IO.Path]::GetFullPath((Join-Path $ProjectRoot '..\..\..\..\..'))
    $projectionParent = [IO.Path]::GetFullPath((Join-Path $checkout 'platform\minecraft\projections'))
    if ((Split-Path -Parent (Split-Path -Parent $ProjectRoot)) -cne $projectionParent -or
        (Split-Path -Leaf $ProjectRoot) -cne 'mc-1.19.2' -or
        !(Test-Path -LiteralPath (Join-Path $checkout '.git'))) {
        throw 'Pinned Gradle Java requires the selected 1.19.2 catalog projection inside its checkout'
    }
    $lockPath = Join-Path $ProjectRoot 'sfm-toolchain.lock.json'
    $lock = Get-Content -LiteralPath $lockPath -Raw | ConvertFrom-Json
    if ($lock.schema_version -ne 4) { throw 'Pinned Gradle Java requires a schema-4 projection lock' }
    $pins = @($lock.jdk_pins | Where-Object {
        $_.major -eq 17 -and $_.vendor -ceq 'JetBrains' -and
        $_.version -ceq '17.0.14' -and $_.build -ceq 'b1367.22' -and
        $_.flavor -ceq 'jbrsdk'
    })
    if ($pins.Count -ne 1) { throw 'The selected projection has no unique pinned JBRSDK 17.0.14' }
    $artifacts = @($pins[0].artifacts | Where-Object { $_.platform -ceq 'windows-x64' })
    if ($artifacts.Count -ne 1) { throw 'The selected projection has no unique windows-x64 JBRSDK archive' }
    $artifact = $artifacts[0]
    $sha = [string]$artifact.sha512
    if ($sha -cnotmatch '^[0-9a-f]{128}$' -or
        $artifact.url -cne 'https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-17.0.14-windows-x64-b1367.22.zip') {
        throw 'The selected JBRSDK archive identity changed; inspect the projection lock'
    }

    $cacheRoot = Join-Path $checkout 'sfm-toolchain-data'
    $cacheItem = Get-Item -LiteralPath $cacheRoot -ErrorAction Stop
    if (!$cacheItem.PSIsContainer -or ($cacheItem.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw 'The checkout toolchain root is missing, linked or not a real directory'
    }
    $markerPath = Join-Path $cacheRoot '.sfm-native-cache-root.json'
    $expectedMarker = ConvertTo-Json -InputObject @('sfm:native_cache_root@1',$cacheRoot) -Compress
    if (![IO.File]::Exists($markerPath) -or
        [IO.File]::ReadAllText($markerPath) -cne $expectedMarker) {
        throw 'The checkout toolchain root has no matching ownership marker'
    }

    $jdkRoot = Join-Path $cacheRoot 'minecraft-toolchain\jbrsdk'
    $archive = Join-Path $jdkRoot ("archives\$sha.zip")
    if (![IO.File]::Exists($archive)) {
        throw "Pinned JBRSDK archive is unavailable at $archive; acquire the exact lock URL with SHA-512 $sha before Gradle launch"
    }
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA512).Hash -cne $sha.ToUpperInvariant()) {
        throw "Pinned JBRSDK archive differs from the lock: $archive"
    }
    $jdkHome = Join-Path $jdkRoot ("installs\v2-" + $sha.Substring(0,64))
    $homeItem = Get-Item -LiteralPath $jdkHome -ErrorAction Stop
    if (!$homeItem.PSIsContainer -or ($homeItem.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw 'The pinned JBRSDK installation is missing, linked or not a real directory'
    }
    $receiptPath = Join-Path $jdkHome '.sfm-jbrsdk-receipt'
    $expectedReceipt = @(
        'sfm-jbrsdk-cache-v1',
        'major=17',
        'vendor=JetBrains',
        'version=17.0.14',
        'build=b1367.22',
        'flavor=jbrsdk',
        'platform=windows-x64',
        "url=$($artifact.url)",
        "sha512=$sha",
        ''
    ) -join "`n"
    if (![IO.File]::Exists($receiptPath) -or
        [IO.File]::ReadAllText($receiptPath) -cne $expectedReceipt) {
        throw 'The installed JBRSDK receipt does not match the selected archive pin'
    }

    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead($archive)
    try {
        $prefix = 'jbrsdk-17.0.14-windows-x64-b1367.22/'
        $expected = [Collections.Generic.Dictionary[string,bool]]::new([StringComparer]::OrdinalIgnoreCase)
        $expected.Add('.sfm-jbrsdk-receipt',$false)
        $totalBytes = [long]0
        if ($zip.Entries.Count -gt 100000) { throw 'Pinned JBRSDK archive has too many entries' }
        foreach ($entry in $zip.Entries) {
            $name = $entry.FullName
            if (!$name.StartsWith($prefix,[StringComparison]::Ordinal) -or
                $name.Contains('\') -or $name.Contains(':') -or $name.Contains([char]0)) {
                throw "Unsafe JBRSDK archive member: $name"
            }
            $relative = $name.Substring($prefix.Length)
            if (!$relative) { continue }
            $directory = $relative.EndsWith('/')
            if ($directory) { $relative = $relative.TrimEnd('/') }
            $parts = $relative.Split('/')
            if ($parts.Count -gt 32 -or @($parts | Where-Object { !$_ -or $_ -eq '.' -or $_ -eq '..' }).Count) {
                throw "Unsafe JBRSDK archive path: $name"
            }
            $mode = ($entry.ExternalAttributes -shr 16) -band 0xF000
            if ($mode -eq 0xA000) { throw "JBRSDK archive contains a symbolic link: $name" }
            $relativeWin = $relative.Replace('/','\')
            if ($expected.ContainsKey($relativeWin)) { throw "Repeated JBRSDK archive path: $name" }
            $expected.Add($relativeWin,$directory)
            $installedPath = Join-Path $jdkHome $relativeWin
            $item = Get-Item -LiteralPath $installedPath -Force -ErrorAction Stop
            if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -or $item.PSIsContainer -ne $directory) {
                throw "Installed JBRSDK member has wrong type or is linked: $relative"
            }
            if (!$directory) {
                $totalBytes += $entry.Length
                if ($totalBytes -gt 8589934592) { throw 'JBRSDK archive exceeds 8 GiB extracted bytes' }
                if ($item.Length -ne $entry.Length) { throw "Installed JBRSDK member differs in size: $relative" }
                $zipStream = $entry.Open()
                $installedStream = [IO.File]::OpenRead($installedPath)
                $hasher = [Security.Cryptography.SHA256]::Create()
                try {
                    $zipHash = ([BitConverter]::ToString($hasher.ComputeHash($zipStream))).Replace('-','')
                    $installedHash = ([BitConverter]::ToString($hasher.ComputeHash($installedStream))).Replace('-','')
                } finally {
                    $zipStream.Dispose(); $installedStream.Dispose(); $hasher.Dispose()
                }
                if ($zipHash -cne $installedHash) { throw "Installed JBRSDK member differs from the pinned archive: $relative" }
            }
        }
        $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
        $queue = [Collections.Generic.Queue[IO.DirectoryInfo]]::new()
        $queue.Enqueue([IO.DirectoryInfo]::new($jdkHome))
        while ($queue.Count) {
            $parent = $queue.Dequeue()
            foreach ($item in $parent.GetFileSystemInfos()) {
                if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Linked JBRSDK member: $($item.FullName)" }
                $relative = $item.FullName.Substring($jdkHome.Length + 1)
                $isDir = $item -is [IO.DirectoryInfo]
                if (!$expected.ContainsKey($relative) -or $expected[$relative] -ne $isDir -or !$seen.Add($relative)) {
                    throw "Unexpected JBRSDK installation member: $relative"
                }
                if ($isDir) { $queue.Enqueue($item) }
                if ($seen.Count -gt 100001) { throw 'Pinned JBRSDK installation has too many paths' }
            }
        }
        if ($seen.Count -ne $expected.Count) { throw 'Pinned JBRSDK installation is incomplete' }
    } finally { $zip.Dispose() }

    $java = Join-Path $jdkHome 'bin\java.exe'
    $javac = Join-Path $jdkHome 'bin\javac.exe'
    if (![IO.File]::Exists($java) -or ![IO.File]::Exists($javac)) { throw 'Pinned JBRSDK lacks Java or javac' }
    $version = (& $java -version 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0 -or $version -notmatch 'openjdk version "17\.0\.14"' -or
        $version -notmatch 'JBR-17\.0\.14\+1-1367\.22') {
        throw 'Pinned JBRSDK executable reports an unexpected runtime identity'
    }
    Write-Output $jdkHome
} catch {
    [Console]::Error.WriteLine("SFM pinned Gradle Java bootstrap failed: $($_.Exception.Message)")
    exit 1
}
