<#
Fail-closed preflight checks for the exact 1.20.3 client reconstruction.
With -Reconstruct, run the five offline installer stages twice in independent
fresh scratch roots and require byte-identical patched clients. All artifacts
are retained; the test never deletes or retries after an error.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $Installer,
    [Parameter(Mandatory)] [string] $CachedLibraries,
    [Parameter(Mandatory)] [string] $VanillaClient,
    [Parameter(Mandatory)] [string] $MojangMappings,
    [Parameter(Mandatory)] [string] $JavaHome,
    [Parameter(Mandatory)] [string] $TestRoot,
    [switch] $Reconstruct,
    [ValidateRange(30, 900)] [int] $WatchdogSeconds = 600
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Assert-True([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw "Test failed: $Message" }
}

function Assert-Rejected([string] $Name, [scriptblock] $Action, [string] $Expected) {
    $rejected = $false
    try { & $Action | Out-Null } catch {
        if ($_.Exception.Message -notlike "*$Expected*") {
            throw "Test $Name rejected for the wrong reason: $($_.Exception.Message)"
        }
        $rejected = $true
    }
    Assert-True $rejected "$Name unexpectedly succeeded"
}

$root = [IO.Path]::GetFullPath($TestRoot).TrimEnd('\', '/')
$checkout = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\', '/')
$profile = [Environment]::GetFolderPath([Environment+SpecialFolder]::UserProfile).TrimEnd('\', '/')
$inputRoots = @([IO.Path]::GetFullPath($CachedLibraries).TrimEnd('\', '/'),
    [IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($Installer)),
    [IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($VanillaClient)),
    [IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($MojangMappings)),
    [IO.Path]::GetFullPath($JavaHome).TrimEnd('\', '/'))
foreach ($protected in @($checkout, $profile) + $inputRoots) {
    if ($root.Equals($protected, [StringComparison]::OrdinalIgnoreCase) -or
        $root.StartsWith($protected + [IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase) -or
        $protected.StartsWith($root + [IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase)) {
        throw 'TestRoot must be isolated from repository, profile and input roots'
    }
}
$ancestor = $root
while ($ancestor) {
    $item = Get-Item -LiteralPath $ancestor -Force -ErrorAction SilentlyContinue
    if ($null -ne $item -and ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw 'TestRoot may not traverse a symbolic link or junction'
    }
    $parent = [IO.Path]::GetDirectoryName($ancestor.TrimEnd('\', '/'))
    if (-not $parent -or $parent -eq $ancestor) { break }
    $ancestor = $parent
}
if ($null -ne (Get-Item -LiteralPath $root -Force -ErrorAction SilentlyContinue)) {
    throw 'TestRoot already exists; refusing to reuse it'
}
if (-not [IO.Directory]::Exists([IO.Path]::GetDirectoryName($root))) {
    throw 'TestRoot parent must already exist'
}
[IO.Directory]::CreateDirectory($root) | Out-Null
$helper = Join-Path $PSScriptRoot 'Build-OfflineNeoForge1203Client.ps1'
$common = @{
    Installer = $Installer
    CachedLibraries = $CachedLibraries
    VanillaClient = $VanillaClient
    MojangMappings = $MojangMappings
    JavaHome = $JavaHome
    WatchdogSeconds = $WatchdogSeconds
}

$preflightRoot = Join-Path $root 'preflight-output'
$preflight = & $helper @common -OutputRoot $preflightRoot -PreflightOnly
Assert-True ($preflight.schema -eq 'sfm-offline-neoforge-client-preflight/1' -and
    $preflight.minecraft -eq '1.20.3' -and
    $preflight.loader -eq 'neoforge-20.3.8-beta' -and
    $preflight.installer_sha256 -eq 'd59ba6f0c867ddaafe0247ba7ddf527389897806795b9bd543a2c4313695f9d7' -and
    $preflight.vanilla_client_sha1 -eq 'b178a327a96f2cf1c9f98a45e5588d654a3e4369' -and
    $preflight.mojang_mappings_sha1 -eq 'be76ecc174ea25580bdc9bf335481a5192d9f3b7' -and
    $preflight.neoform_zip_sha1 -eq '88efc93dca87914f70ef6b3ecc12db46edc64898' -and
    $preflight.processor_declared_coordinate_count -eq 41 -and
    $preflight.processor_unique_jar_count -eq 37 -and
    -not $preflight.output_created -and
    -not [IO.Directory]::Exists($preflightRoot)) 'exact preflight did not verify inputs without writing'

$inputs = Join-Path $root 'negative-inputs'
[IO.Directory]::CreateDirectory($inputs) | Out-Null
$badInstaller = Join-Path $inputs 'bad-installer.jar'
$badClient = Join-Path $inputs 'bad-client.jar'
[IO.File]::WriteAllBytes($badInstaller, [Text.Encoding]::UTF8.GetBytes('bad installer'))
[IO.File]::WriteAllBytes($badClient, [Text.Encoding]::UTF8.GetBytes('bad vanilla client'))
$badOutput = Join-Path $root 'rejected-output'
$badInstallerArgs = $common.Clone()
$badInstallerArgs['Installer'] = $badInstaller
Assert-Rejected 'wrong installer' {
    & $helper @badInstallerArgs -OutputRoot $badOutput -PreflightOnly
} 'SHA-256 mismatch'
Assert-True (-not [IO.Directory]::Exists($badOutput)) 'wrong installer created output'
$badClientArgs = $common.Clone()
$badClientArgs['VanillaClient'] = $badClient
Assert-Rejected 'wrong vanilla client' {
    & $helper @badClientArgs -OutputRoot $badOutput -PreflightOnly
} 'SHA-1 mismatch'
Assert-True (-not [IO.Directory]::Exists($badOutput)) 'wrong vanilla client created output'
$checkoutOutput = Join-Path $checkout ('neo1203-client-test-output-' + [guid]::NewGuid().ToString('N'))
Assert-Rejected 'output inside checkout' {
    & $helper @common -OutputRoot $checkoutOutput -PreflightOnly
} 'repository checkout'
Assert-True (-not (Test-Path -LiteralPath $checkoutOutput)) 'checkout preflight created output'
[IO.Directory]::CreateDirectory($badOutput) | Out-Null
Assert-Rejected 'existing output' {
    & $helper @common -OutputRoot $badOutput -PreflightOnly
} 'already exists'

if (-not $Reconstruct) {
    return [pscustomobject]@{
        schema = 'sfm-offline-neoforge-client-tests/1'
        passed = $true
        reconstruction_exercised = $false
        artifacts_retained = $true
    }
}

$firstRoot = Join-Path $root 'independent-first'
$secondRoot = Join-Path $root 'independent-second'
$first = & $helper @common -OutputRoot $firstRoot
$second = & $helper @common -OutputRoot $secondRoot -ReferenceRoot $firstRoot
Assert-True ($first.schema -eq 'sfm-offline-neoforge-client/1' -and
    $second.schema -eq 'sfm-offline-neoforge-client/1' -and
    $first.minecraft -eq '1.20.3' -and $second.minecraft -eq '1.20.3' -and
    $first.loader -eq 'neoforge-20.3.8-beta' -and $second.loader -eq 'neoforge-20.3.8-beta' -and
    -not $first.repeated_against_reference -and
    $second.repeated_against_reference -and
    $first.patched_client_sha256 -eq $second.patched_client_sha256 -and
    $second.patched_zip_entries -ge 1000) 'two independent patched clients did not agree'
[pscustomobject]@{
    schema = 'sfm-offline-neoforge-client-tests/1'
    passed = $true
    reconstruction_exercised = $true
    patched_client_sha1 = $second.patched_client_sha1
    patched_client_sha256 = $second.patched_client_sha256
    patched_zip_entries = $second.patched_zip_entries
    artifacts_retained = $true
}
