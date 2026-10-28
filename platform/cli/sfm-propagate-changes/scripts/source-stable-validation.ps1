param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Capture', 'Verify', 'Run')]
    [string] $Mode,
    [Parameter(Mandatory = $true)]
    [string] $ReceiptPath,
    [string[]] $ExtraFiles = @(),
    [string] $Executable,
    [string[]] $CommandArguments = @(),
    [string] $CommandDirectory
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$scopes = @(
    'platform/cli/sfm-propagate-changes',
    'platform/minecraft/sfm-toolchain.lock.json',
    'platform/minecraft/projections.json',
    'platform/minecraft',
    'docs/architecture/fixtures',
    'docs/tasks/*.json',
    '.cargo'
)
$ignoredGeneratedInput = 'target/core-nfrt-launcher-stage-v1/SFMNamedNeoFormLauncher.java'

function Get-InputIdentity {
    $paths = @(git -C $repoRoot ls-files --cached --others --exclude-standard -- $scopes)
    if ($LASTEXITCODE -ne 0) { throw 'Unable to enumerate Git inputs' }
    $paths += $ExtraFiles
    $paths += $ignoredGeneratedInput
    $files = @(
        foreach ($path in ($paths | Sort-Object -Unique)) {
            $fullPath = if ([IO.Path]::IsPathRooted($path)) { $path } else { Join-Path $repoRoot $path }
            if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
                [ordered]@{ path = $path; sha256 = '<missing>' }
            } else {
                [ordered]@{ path = $path; sha256 = (Get-FileHash -LiteralPath $fullPath -Algorithm SHA256).Hash.ToLowerInvariant() }
            }
        }
    )
    $head = (git -C $repoRoot rev-parse HEAD)
    if ($LASTEXITCODE -ne 0) { throw 'Unable to read HEAD' }
    $index = @(git -C $repoRoot ls-files --stage -- $scopes)
    if ($LASTEXITCODE -ne 0) { throw 'Unable to read the staged index' }
    $environment = [ordered]@{}
    foreach ($name in @('CARGO_HOME', 'CARGO_TARGET_DIR', 'CARGO_BUILD_TARGET', 'CARGO_ENCODED_RUSTFLAGS', 'RUSTUP_TOOLCHAIN', 'RUSTC_WRAPPER', 'RUSTFLAGS', 'CARGO_NET_OFFLINE', 'JAVA_HOME')) {
        $environment[$name] = [Environment]::GetEnvironmentVariable($name)
    }
    $userCargoConfig = Join-Path $env:USERPROFILE '.cargo/config.toml'
    $cargoConfigHash = if (Test-Path -LiteralPath $userCargoConfig -PathType Leaf) {
        (Get-FileHash -LiteralPath $userCargoConfig -Algorithm SHA256).Hash.ToLowerInvariant()
    } else { '<missing>' }
    [ordered]@{
        schema = 1
        root = $repoRoot
        head = $head
        scopes = $scopes
        staged = $index
        environment = $environment
        userCargoConfigSha256 = $cargoConfigHash
        files = $files
    }
}

if ($Mode -eq 'Run') {
    if (-not $Executable -or -not $CommandDirectory) {
        throw 'Run requires -Executable and -CommandDirectory'
    }
    if (Test-Path -LiteralPath $ReceiptPath) { throw "Receipt already exists: $ReceiptPath" }
    $leasePath = Join-Path $repoRoot 'platform/cli/sfm-propagate-changes/target/source-stable-validation.lease'
    try {
        $lease = [IO.File]::Open($leasePath, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    } catch [IO.IOException] {
        throw "Validation writer lease already owned: $leasePath"
    }
    try {
        $current = Get-InputIdentity | ConvertTo-Json -Depth 8 -Compress
        $tool = (Get-Command $Executable -ErrorAction Stop).Source
        $toolHash = (Get-FileHash -LiteralPath $tool -Algorithm SHA256).Hash.ToLowerInvariant()
        $rustc = (Get-Command rustc -ErrorAction Stop).Source
        $rustcHash = (Get-FileHash -LiteralPath $rustc -Algorithm SHA256).Hash.ToLowerInvariant()
        $cargoVersion = (& cargo -V) -join ' '
        $rustcVersion = (& rustc -V) -join ' '
        $start = [DateTime]::UtcNow.ToString('o')
        [IO.File]::WriteAllText($ReceiptPath, $current, [Text.UTF8Encoding]::new($false))
        Push-Location -LiteralPath $CommandDirectory
        try {
            & $Executable @CommandArguments
            $commandExit = $LASTEXITCODE
        } finally {
            Pop-Location
        }
        $after = Get-InputIdentity | ConvertTo-Json -Depth 8 -Compress
        $stable = $current -ceq $after
        $run = [ordered]@{
            command = @($Executable) + $CommandArguments
            commandDirectory = (Resolve-Path $CommandDirectory).Path
            toolSha256 = $toolHash
            rustcSha256 = $rustcHash
            cargoVersion = $cargoVersion
            rustcVersion = $rustcVersion
            startedUtc = $start
            endedUtc = [DateTime]::UtcNow.ToString('o')
            exitCode = $commandExit
            sourceStable = $stable
        }
        [IO.File]::WriteAllText("$ReceiptPath.run.json", ($run | ConvertTo-Json -Depth 4 -Compress), [Text.UTF8Encoding]::new($false))
        if (-not $stable) { throw 'Source/config identity drifted during validation; result is stale.' }
        if ($commandExit -ne 0) { throw "Validation command failed with exit code $commandExit" }
        Write-Output "Validation passed with stable inputs: $ReceiptPath"
    } finally {
        $lease.Dispose()
    }
    exit 0
}
$current = Get-InputIdentity | ConvertTo-Json -Depth 8 -Compress
if ($Mode -eq 'Capture') {
    if (Test-Path -LiteralPath $ReceiptPath) { throw "Receipt already exists: $ReceiptPath" }
    [IO.File]::WriteAllText($ReceiptPath, $current, [Text.UTF8Encoding]::new($false))
    Write-Output "Captured $(@(($current | ConvertFrom-Json).files).Count) inputs: $ReceiptPath"
    exit 0
}

if (-not (Test-Path -LiteralPath $ReceiptPath -PathType Leaf)) { throw "Missing receipt: $ReceiptPath" }
$expected = [IO.File]::ReadAllText($ReceiptPath)
if ($expected -cne $current) {
    Write-Error 'Source/config identity drifted; the validation result is stale.'
    exit 2
}
Write-Output "Source/config identity unchanged: $ReceiptPath"
