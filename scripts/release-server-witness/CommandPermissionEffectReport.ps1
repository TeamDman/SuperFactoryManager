# Pure validation shared by the runtime runner and offline negative tests.
function Assert-CommandPermissionEffectSnapshot($Snapshot, [string] $Target, [string] $Loader) {
    Set-StrictMode -Version Latest
    if ($Target -cnotin @('1.19.2', '26.1.2')) { throw 'Unsupported command permission target' }
    $model = if ($Target -ceq '1.19.2') { 'numeric_command_level' } else { 'level_based_permission_set' }
    if ($Snapshot.schema -cne 'sfm:release_command_permission_effect_snapshot@1' -or
        $Snapshot.target -cne $Target -or $Snapshot.loader -cne $Loader -or
        $Snapshot.context_semantics -cne 'server_console_permission_replacement_not_player_identity' -or
        $Snapshot.permission_model -cne $model) { throw 'Command permission snapshot identity or context mismatch' }
    $contexts = @($Snapshot.contexts)
    $ids = @('low', 'operator', 'owner')
    $levels = @(0, 2, 4)
    if ($contexts.Count -ne 3) { throw 'Expected exactly three explicit server command-source contexts' }
    for ($i = 0; $i -lt $contexts.Count; $i++) {
        $context = $contexts[$i]
        if ($context.id -cne $ids[$i] -or
            $context.source_kind -cne 'server_console_without_entity' -or
            $context.permission_level -isnot [ValueType] -or $context.permission_level -is [bool] -or
            $context.permission_level -is [double] -or $context.permission_level -is [float] -or
            $context.permission_level -is [decimal] -or
            $context.permission_level -ne $levels[$i]) { throw 'Command-source context identity mismatch' }
        foreach ($field in @('entity_present', 'player_present', 'has_all', 'has_operator', 'has_owner')) {
            if ($context.$field -isnot [bool]) { throw 'Command-source evidence must contain actual JSON booleans' }
        }
        if ($context.entity_present -or $context.player_present -or -not $context.has_all -or
            $context.has_operator -ne ($i -ge 1) -or $context.has_owner -ne ($i -eq 2)) {
            throw 'Command-source permissions or no-player boundary mismatch'
        }
    }
    $expectedPaths = @(
        'sfm', 'sfm/bust_cable_network_cache', 'sfm/bust_water_network_cache', 'sfm/changelog',
        'sfm/config', 'sfm/config/edit', 'sfm/config/edit/CLIENT', 'sfm/config/edit/SERVER',
        'sfm/config/show', 'sfm/config/show/variant', 'sfm/kit', 'sfm/kit/targets',
        'sfm/show_bad_cable_cache_entries', 'sfm/show_bad_cable_cache_entries/block'
    )
    $nodes = @($Snapshot.nodes)
    if ($nodes.Count -ne $expectedPaths.Count) { throw 'Expected the complete released fourteen-node command tree' }
    $byPath = @{}
    for ($i = 0; $i -lt $nodes.Count; $i++) {
        $node = $nodes[$i]
        if ($node.path -cne $expectedPaths[$i] -or $byPath.ContainsKey($node.path)) {
            throw 'Command permission node membership or order mismatch'
        }
        $byPath[$node.path] = $node
        foreach ($id in $ids) {
            foreach ($suffix in @('_can_use', '_path_can_use')) {
                if ($node.($id + $suffix) -isnot [bool]) { throw 'Command canUse evidence must contain actual JSON booleans' }
            }
            $ownExpected = if ($node.path -ceq 'sfm/config/edit/SERVER') {
                $id -ceq 'owner'
            } elseif ($node.path -cin @('sfm/kit', 'sfm/show_bad_cable_cache_entries')) {
                $id -cne 'low'
            } else { $true }
            $parent = if ($node.path -ceq 'sfm') { '' } else { $node.path.Substring(0, $node.path.LastIndexOf('/')) }
            $ancestorAllowed = if ($parent) { $byPath[$parent].($id + '_path_can_use') } else { $true }
            if ($node.($id + '_can_use') -ne $ownExpected -or
                $node.($id + '_path_can_use') -ne ($ancestorAllowed -and $ownExpected)) {
                throw 'Command permission predicate or ancestor access mismatch'
            }
        }
    }
    $effect = $Snapshot.effect
    if ($effect.command -cne 'sfm bust_cable_network_cache' -or $effect.context -cne 'low') {
        throw 'Unexpected selected command effect'
    }
    foreach ($field in @('server_thread', 'manager_placed', 'cache_seeded')) {
        if ($effect.$field -isnot [bool] -or $effect.$field -ne $true) {
            throw 'Command effect did not prove its actual preconditions'
        }
    }
    foreach ($field in @('cache_before', 'command_result', 'cache_after')) {
        if ($effect.$field -isnot [ValueType] -or $effect.$field -is [bool] -or
            $effect.$field -is [double] -or $effect.$field -is [float] -or $effect.$field -is [decimal]) {
            throw 'Command effect counts and result must be JSON integers'
        }
    }
    if ($effect.cache_before -ne 1 -or $effect.command_result -ne 1 -or $effect.cache_after -ne 0) {
        throw 'Command effect must clear exactly one seeded cache entry and return success'
    }
}
