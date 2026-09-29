<#
Read-only SFM server-config comparison for one retained exact-loader witness.
Pass an explicit test-witness root. Output contains hashes, never local paths.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $PairRoot,
    [Parameter(Mandatory)]
    [ValidateSet('1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3',
        '1.20.4', '1.21.0', '1.21.1', '26.1.2')]
    [string] $Target
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$root = [IO.Path]::GetFullPath($PairRoot).TrimEnd('\', '/')
if (-not [IO.Directory]::Exists($root) -or
    [IO.Path]::GetFileName($root) -notmatch '^sfm-release-server-witness-') {
    throw 'PairRoot must be one explicit, retained release-server witness root'
}
$reportPath = Join-Path $root 'result.json'
if (-not [IO.File]::Exists($reportPath)) { throw 'Missing witness result.json' }
$report = Get-Content -LiteralPath $reportPath -Raw | ConvertFrom-Json
if ($report.schema -cne 'sfm:release_server_witness@1' -or $report.status -cne 'PASS' -or
    $report.target -cne $Target -or @($report.boots).Count -ne 4 -or
    [string] $report.official_jar_sha256 -cnotmatch '^[0-9a-f]{64}$' -or
    [string] $report.projected_jar_sha256 -cnotmatch '^[0-9a-f]{64}$') {
    throw 'Witness report identity or completion is invalid'
}
$roles = @('official-seed', 'official-control', 'projected-candidate', 'official-reverse')
foreach ($role in $roles) {
    $boot = @($report.boots | Where-Object { $_.role -ceq $role })
    if ($boot.Count -ne 1 -or $boot[0].exit_code -ne 0 -or
        $boot[0].startup_done -ne $true -or $boot[0].exact_loader -ne $true -or
        $boot[0].saved -ne $true) {
        throw "Witness report lacks one complete exact-loader $role boot"
    }
    $expectedJar = if ($role -ceq 'projected-candidate') {
        [string] $report.projected_jar_sha256
    } else {
        [string] $report.official_jar_sha256
    }
    if ($boot[0].sfm_jar_sha256 -cne $expectedJar) {
        throw "Witness report has an unexpected SFM JAR for $role"
    }
}

# The witness copies the seed world, but never the boot-root config directory.
$independent = $Target -in @('1.20.4', '1.21.0', '1.21.1', '26.1.2')
$relative = if ($independent) {
    'config/sfm-server.toml'
} else {
    'sfm-release-witness-world/serverconfig/sfm-server.toml'
}
$hashes = [ordered]@{}
$lengths = [ordered]@{}
foreach ($role in $roles) {
    $file = Join-Path (Join-Path $root $role) $relative
    if (-not [IO.File]::Exists($file)) { throw "Missing SFM server config in $role boot" }
    $item = Get-Item -LiteralPath $file
    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "SFM server config is a link in $role boot"
    }
    $hashes[$role] = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
    $lengths[$role] = $item.Length
}
$seedHash = $hashes['official-seed']
foreach ($role in $roles) {
    if ($hashes[$role] -cne $seedHash -or $lengths[$role] -ne $lengths['official-seed']) {
        throw "SFM server config bytes differ in $role boot"
    }
}

[ordered]@{
    schema = 'sfm-release-server-config-comparison/1'
    target = $Target
    generation = if ($independent) { 'independent_boot_config' } else { 'inherited_seed_serverconfig' }
    witness_report_sha256 = (Get-FileHash -LiteralPath $reportPath -Algorithm SHA256).Hash.ToLowerInvariant()
    official_jar_sha256 = [string] $report.official_jar_sha256
    projected_jar_sha256 = [string] $report.projected_jar_sha256
    config_sha256 = $seedHash
    config_bytes = $lengths['official-seed']
    all_four_equal = $true
} | ConvertTo-Json -Compress
