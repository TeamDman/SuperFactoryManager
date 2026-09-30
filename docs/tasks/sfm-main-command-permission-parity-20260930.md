# SFM command permission and selected cache-effect parity

## Status and scope

Both exact-loader official/projected runtime pairs passed on 30 September 2026. All 4 fresh servers proved the selected permission and cache-effect assertions. Independent inspection rehashed the actual reports, snapshots, production inputs and frozen auxiliary sources. This is bounded runtime parity evidence, not migration completion, general compatibility or release acceptance.

This closes a bounded evidence gap in the existing command-registration comparison: evaluating the registered permission predicates and exercising one harmless command effect against a nonempty cache. It uses unchanged official and checked-in projected SFM 4.34.0 JARs on exact Minecraft 1.19.2 / Forge 43.4.0 and Minecraft 26.1.2 / NeoForge 26.1.2.72. It adds no production code, dependency, loader acquisition, release selection, publication, tag, default-branch change or old-branch retirement. All world mutations belong to new isolated scratch servers.

The runner and pinned input hashes are in [the server witness guide](../../scripts/release-server-witness/README.md). The living [source-projection plan](sfm-main-source-projection-plan.md) owns broader SP-05 and migration acceptance. The earlier structural command snapshot remains separate evidence; a tree with matching node names alone does not prove permission or execution parity.

## Honest command-source contexts

Both adapters begin with the actual server console `CommandSourceStack` and replace its permissions. The source must have no entity and no player. The terms low, operator and owner describe permission capabilities only; they are not authenticated player, operator-account or server-owner identities.

| Context | 1.19.2 adapter | 26.1.2 adapter | Required all / operator / owner checks |
| --- | --- | --- | --- |
| low | `withPermission(0)` | `LevelBasedPermissionSet.ALL` | true / false / false |
| operator | `withPermission(2)` | `LevelBasedPermissionSet.GAMEMASTER` | true / true / false |
| owner | `withPermission(4)` | `LevelBasedPermissionSet.OWNER` | true / true / true |

The legacy adapter calls the released SRG methods corresponding to these mapped APIs. The 26.1.2 adapter calls `withPermission(PermissionSet)` and checks `Permission.HasCommandLevel` through the source's actual permission set. It does not union with the original console's owner permissions.

For each of the fourteen registered `sfm` nodes, the snapshot records actual `node.canUse` booleans for all three contexts and ancestor-path access. Required contrasts include cache bust available to all three, `kit` unavailable to low and available to operator/owner, and `config/edit/SERVER` available only to owner. Argument-node access is not mistaken for bypassing its parent predicate. These are comparisons within each exact official/projected pair, not cross-version wire or API equivalence.

## Selected execution assertion

On the actual server thread, the auxiliary probe places a manager at a fixed position in its fresh scratch world. It clears any existing cable-cache entries, seeds a network using `CableNetworkManager.getOrRegisterNetworkFromCablePosition`, and counts networks using the production cache API. The test requires one actual entry before execution; an empty cache cannot pass.

The probe executes exactly `sfm bust_cable_network_cache` through the server's actual dispatcher using the low-permission source. Required integer observations are `cache_before=1`, `command_result=1` and `cache_after=0`. Placement, successful seeding and same-thread execution must each be actual JSON `true`. This command removes an in-memory cache entry; it does not install a disk program or perform an inventory transfer.

## Measured runtime evidence

The source base was `819b3f56aaa071a07719ce38d93c8932bf2c278e` with the reviewed test-only patch. The root agent ran both endpoint pairs after source and process review. It compiled the auxiliary probe against each exact existing loader installation. Each official and projected boot logged the exact Minecraft/loader identity, reached `Done`, emitted `SFM_COMMAND_PERMISSION_EFFECT_V1 assertions_passed=true`, saved and exited zero.

The actual files rehashed to these values:

| Target and exact loader | Path-free `result.json` SHA-256 | Both permission/effect snapshot SHA-256 | Both registry snapshot SHA-256 |
| --- | --- | --- | --- |
| 1.19.2 / Forge 43.4.0 | `06a7f49414c714e7fc9dcbc972132d94adfcf5822aa798288ff877192c236070` | `0b865c9bb65da03a3f3ae834454851aeed39a82ed225207862cff0677d4b15ed` | `67bd300a14ab0743d1d4f8e3e47fba796cbe27a5e7fbf29bcb54335e844647ea` |
| 26.1.2 / NeoForge 26.1.2.72 | `026b7c725aaf8568ed595c7484810a108125ff48fc654f8ecfc5bd37a4bf6a60` | `a6817de70aae7075ef7519cd5c2ad16b74678aa622a8c25470cc1ec563189bdb` | `5f6adf9bf69f6ebeab28e50d21dc35b9a81168e33e0d14f4129d8daf0040500e` |

Both actual snapshots matched their embedded report snapshots. All 4 sources recorded `entity_present=false` and `player_present=false`. Their low/operator/owner permission checks matched the table above. The complete 14-node tree recorded these exact booleans in every boot, ordered low / operator / owner:

| Nodes | Own `canUse` | Ancestor-path access |
| --- | --- | --- |
| `sfm/bust_cable_network_cache` | true / true / true | true / true / true |
| `sfm/kit`, `sfm/show_bad_cable_cache_entries` | false / true / true | false / true / true |
| `sfm/kit/targets`, `sfm/show_bad_cable_cache_entries/block` | true / true / true | false / true / true |
| `sfm/config/edit/SERVER` | false / false / true | false / false / true |
| the other 8 registered nodes | true / true / true | true / true / true |

Each of the 4 servers executed `sfm bust_cable_network_cache` using the low source. Each recorded integer `cache_before=1`, `command_result=1` and `cache_after=0`. Each recorded actual `true` for server-thread execution, manager placement and successful cache seeding. The comparison did not pass on an empty cache or on a synthetic command result.

The independent audit passed 52 hash and snapshot checks. These covered both unchanged original input JARs per target and all 4 boot copies:

| Target | Official production JAR SHA-256 | Projected production JAR SHA-256 | Packaged auxiliary probe SHA-256 |
| --- | --- | --- | --- |
| 1.19.2 | `f2c0242a984b8b782cc995e20a59e71bbdb48981873ab31a4531f0d1db3af038` | `024e9b10463235f10e61cb0e7285f8a082e90800ead8c3f5146b58c51146eccf` | `95898eb9ad1d473a372adade9b2c34214af8a5eab950865d5e6b39eb2bdc5f14` |
| 26.1.2 | `cace8809600cea007dbe5c73dc04c2f780375547ec0717a1ec8c25d991140bf1` | `9b2ff101e1158f42bf9218177c1f063375ce58254ff0de629db4dc52ef3c15b6` | `d703731e2635c1a8a896f646fc32d6a8185812950ca877db598bc9cb5096196f` |

The reviewed sources stayed unchanged after the passing boots:

| Repository-relative source | SHA-256 |
| --- | --- |
| `scripts/release-server-witness/Run-ReleaseServerWitness.ps1` | `88c80cb760731741fc41e9b9ba9edd8e1870876d256a8c4963e70df132f0f451` |
| `scripts/release-server-witness/CommandPermissionEffectReport.ps1` | `bca723e15956d07dc89502d11d31594b8f57d122fefcb85ad4e8b36bfcf738f8` |
| `scripts/release-server-witness/src/main/java/ca/teamdman/sfm/releaseprobe/ReleaseCommandPermissionEffectProbe.java` | `2d33ba1c9bbc5f17e03f536bf3084f152255c8599e4217f9d167bd14516bb995` |
| `scripts/release-server-witness/versions/1.19.2/src/main/java/ca/teamdman/sfm/releaseprobe/ReleaseServerRegistryProbe.java` | `c5126fa3cf3acfa4239f7faed75c15fd30bbb2ea1797fa800ec8fc770995e744` |
| `scripts/release-server-witness/versions/26.1.2/src/main/java/ca/teamdman/sfm/releaseprobe/ReleaseServerRegistryProbe.java` | `e1b139d375d9aea624fcd21b0eee150e285e58045f550122dc341f4437c7f4c3` |

Independent rehashes also matched the report's probe resources, both installed probe copies, exact installer and compile JAR, Java executable, launch arguments and recorded compile extras. The snapshots are compared only within their exact official/projected pair; their different target/model identities prevent a cross-version byte-equivalence claim.

All 4 boots logged the same flat-layer configuration error and SFM buffer loot-table parse error. Both 26.1.2 boots also emitted nonfatal DebugFile appender exceptions while probing unsupported Netty kqueue/epoll native classes on Windows. These diagnostics did not prevent `Done`, the assertions, save or exit zero. No disk-space diagnostic was found. This proof does not claim clean general-purpose server logs or fix those diagnostics.

## Offline checks and reproducing the proof

The PowerShell parser reported zero errors for the runner, validator and offline test. Running `scripts/release-server-witness/Test-CommandPermissionEffectReport.ps1` passed 30 checks: two valid endpoint snapshots, twenty-five rejected malformed or vacuous snapshots, and three rejected CLI requests. The negatives include typed booleans and integers, permission contrasts and ancestor access, zero-entry seeding, unsuccessful execution, uncleared cache, wrong identities and missing fields. The CLI guard tests created no game roots and started no Java.

A repeat uses the same pinned inputs and exact loader installations. For either endpoint, review the source and select local read-only input paths. Run the repository-relative driver with the appropriate pinned hashes and a different absent output root:

```powershell
scripts/release-server-witness/Run-ReleaseServerWitness.ps1 `
  -Target <1.19.2-or-26.1.2> -Mode command-permission-effect -PreflightOnly `
  -OfficialJar <reviewed-official-4.34.0-jar> -OfficialSha256 <pinned-official-sha256> `
  -ProjectedJar <reviewed-projected-4.34.0-jar> -ProjectedSha256 <pinned-projected-sha256> `
  -LoaderInstaller <exact-reviewed-installer> -LoaderCompileJar <exact-reviewed-compile-jar> `
  -JavaHome <pinned-jbrsdk-home> -InstalledLoaderRoot <verified-exact-scratch-loader-install> `
  -RunRoot <new-absent-scratch-directory>
```

`-PreflightOnly` validates and hashes inputs, selected probe sources/resources, compile libraries and installed launch identity without starting Java, compiling or creating the run root. Only after explicit source/process review should the same invocation omit that switch. The full mode compiles one auxiliary probe JAR and starts two fresh isolated servers per target, official then projected. Each must log the exact loader/Minecraft identity, reach `Done`, emit the snapshot, save and exit zero. Both permission/effect snapshot digests and both registry digests must match. The preflight and runtime reports together bind runner, helper, validator, probe sources/resources/JAR, production JARs, installer, compile libraries, Java tools and launch-argument hashes. The production JAR digests must still match after execution.

## Operational readiness and limits

The root agent compiled the test-only probes and launched the 4 isolated servers through the reviewed runner. No Cargo, Gradle, new loader installation, dependency acquisition or cache rehydration was needed for this slice. The loader, SDK and dependency graph stayed frozen. No Rust or CLI implementation changed in this slice, so the user does not need an installer step for it. Broader migration tooling freshness remains owned by the living plan.

All 4 servers exited zero. A read-only host process check found no Java process whose command line referenced either exact witness scratch root. The independent audit launched no additional server and stopped no process. Scratch artifacts remain intact: inspect each `<scratch-root>/result.json`, both `command-permission-effect-snapshot.json` and `registry-snapshot.json` files, and the per-boot logs. No cleanup was performed. A future disk-space error requires a stop for the user, not deletion or cache cleanup.

Keep auxiliary code unchanged after this passing run so the hashes continue to bind the reviewed implementation. This proof remains limited to the 2 endpoints, explicit no-player permission contexts and one cache-bust effect. It does not prove logged-in-player semantics, every command's effect, all intermediate versions, client command exposure, modded integrations, packet delivery, general gameplay or migration completion. It does not authorize a real release or any public repository mutation.
