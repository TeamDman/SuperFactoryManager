[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'CommandPermissionEffectReport.ps1')

function New-TestSnapshot([string] $Target) {
    $paths = @(
        'sfm', 'sfm/bust_cable_network_cache', 'sfm/bust_water_network_cache', 'sfm/changelog',
        'sfm/config', 'sfm/config/edit', 'sfm/config/edit/CLIENT', 'sfm/config/edit/SERVER',
        'sfm/config/show', 'sfm/config/show/variant', 'sfm/kit', 'sfm/kit/targets',
        'sfm/show_bad_cable_cache_entries', 'sfm/show_bad_cable_cache_entries/block'
    )
    $ids = @('low', 'operator', 'owner')
    $contexts = for ($i = 0; $i -lt 3; $i++) {
        [ordered]@{
            id = $ids[$i]; source_kind = 'server_console_without_entity'; entity_present = $false
            player_present = $false; permission_level = @(0, 2, 4)[$i]
            has_all = $true; has_operator = ($i -ge 1); has_owner = ($i -eq 2)
        }
    }
    $byPath = @{}
    $nodes = foreach ($path in $paths) {
        $node = [ordered]@{ path = $path }
        foreach ($id in $ids) {
            $own = if ($path -ceq 'sfm/config/edit/SERVER') { $id -ceq 'owner' }
                elseif ($path -cin @('sfm/kit', 'sfm/show_bad_cable_cache_entries')) { $id -cne 'low' }
                else { $true }
            $parent = if ($path -ceq 'sfm') { '' } else { $path.Substring(0, $path.LastIndexOf('/')) }
            $ancestor = if ($parent) { $byPath[$parent][$id + '_path_can_use'] } else { $true }
            $node[$id + '_can_use'] = $own
            $node[$id + '_path_can_use'] = $ancestor -and $own
        }
        $byPath[$path] = $node
        $node
    }
    $loader = if ($Target -ceq '1.19.2') { 'forge-43.4.0' } else { 'neoforge-26.1.2.72' }
    [ordered]@{
        schema = 'sfm:release_command_permission_effect_snapshot@1'; target = $Target; loader = $loader
        context_semantics = 'server_console_permission_replacement_not_player_identity'
        permission_model = if ($Target -ceq '1.19.2') { 'numeric_command_level' } else { 'level_based_permission_set' }
        contexts = @($contexts); nodes = @($nodes)
        effect = [ordered]@{
            command = 'sfm bust_cable_network_cache'; context = 'low'; server_thread = $true
            manager_placed = $true; cache_seeded = $true; cache_before = 1; command_result = 1; cache_after = 0
        }
    } | ConvertTo-Json -Depth 10 | ConvertFrom-Json
}

$passed = 0
foreach ($target in @('1.19.2', '26.1.2')) {
    $snapshot = New-TestSnapshot $target
    Assert-CommandPermissionEffectSnapshot $snapshot $target $snapshot.loader
    $passed++
}
$mutations = @(
    { param($s) $s.schema = 'wrong' },
    { param($s) $s.target = '1.20' },
    { param($s) $s.loader = 'forge-43.4.1' },
    { param($s) $s.context_semantics = 'logged_in_player' },
    { param($s) $s.permission_model = 'level_based_permission_set' },
    { param($s) $s.contexts = @($s.contexts[0], $s.contexts[1]) },
    { param($s) $s.contexts[0].player_present = $true },
    { param($s) $s.contexts[0].has_all = 'true' },
    { param($s) $s.contexts[0].has_operator = $true },
    { param($s) $s.contexts[1].permission_level = 2.0 },
    { param($s) $s.nodes = @($s.nodes | Select-Object -Skip 1) },
    { param($s) $s.nodes[1].path = 'sfm' },
    { param($s) $s.nodes[10].low_can_use = $true },
    { param($s) $s.nodes[11].low_path_can_use = $true },
    { param($s) $s.nodes[1].low_can_use = 'true' },
    { param($s) $s.effect.server_thread = $false },
    { param($s) $s.effect.manager_placed = $false },
    { param($s) $s.effect.cache_seeded = $false },
    { param($s) $s.effect.cache_before = 0 },
    { param($s) $s.effect.cache_before = '1' },
    { param($s) $s.effect.command_result = 0 },
    { param($s) $s.effect.cache_after = 1 },
    { param($s) $s.effect.cache_after = 0.0 },
    { param($s) $s.effect.command = 'sfm kit' },
    { param($s) $s.effect.PSObject.Properties.Remove('cache_before') }
)
foreach ($mutation in $mutations) {
    $snapshot = New-TestSnapshot '1.19.2'
    & $mutation $snapshot
    $rejected = $false
    try { Assert-CommandPermissionEffectSnapshot $snapshot '1.19.2' 'forge-43.4.0' }
    catch { $rejected = $true }
    if (-not $rejected) { throw 'A malformed or vacuous command permission/effect fixture was accepted' }
    $passed++
}

# These guard failures occur before input access, Java, compilation or a game root.
$runner = Join-Path $PSScriptRoot 'Run-ReleaseServerWitness.ps1'
foreach ($case in @(
    @{ Target = '1.20'; Mode = 'command-permission-effect'; InstalledForgeRoot = 'not-a-live-input'; Expected = 'only validated' },
    @{ Target = '1.19.2'; Mode = 'command-permission-effect'; InstalledForgeRoot = ''; Expected = 'requires a verified existing' },
    @{ Target = '1.19.2'; Mode = 'command-dispatcher'; InstalledForgeRoot = 'not-a-live-input'; Expected = 'PreflightOnly is available only' }
)) {
    $absentRoot = Join-Path ([IO.Path]::GetTempPath()) ('sfm-command-permission-negative-' + [Guid]::NewGuid().ToString('N'))
    $rejected = $false
    try {
        & $runner -Target $case.Target -Mode $case.Mode -PreflightOnly `
            -OfficialJar 'not-a-live-input' -OfficialSha256 ('0' * 64) `
            -ProjectedJar 'not-a-live-input' -ProjectedSha256 ('0' * 64) `
            -ForgeInstaller 'not-a-live-input' -ForgeSrgJar 'not-a-live-input' `
            -JavaHome 'not-a-live-input' -RunRoot $absentRoot -InstalledForgeRoot $case.InstalledForgeRoot
    } catch {
        if (-not $_.Exception.Message.Contains($case.Expected)) { throw }
        $rejected = $true
    }
    if (-not $rejected -or [IO.Directory]::Exists($absentRoot) -or [IO.File]::Exists($absentRoot)) {
        throw 'A rejected permission/effect request reached a scratch output root'
    }
    $passed++
}
"COMMAND_PERMISSION_EFFECT_OFFLINE passed=$passed java_started=false game_roots_created=false"
