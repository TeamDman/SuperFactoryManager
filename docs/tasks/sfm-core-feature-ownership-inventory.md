# Shared Liquid feature ownership inventory

Use this inventory to assign source ownership before consolidating the core templates. It records migration evidence and proposed feature groups. It does not register features or complete their semantic review.

Inventory status: Read-only reconnaissance complete; feature and hunk ownership review pending.
Implementation branch: `feat/sfm-main-source-projection`.
Recorded: 30 September 2026.
Source checkpoint: `f3ff2f6425434f36c7c680fa909c977b158e1860`.
Planning checkpoint: `9825759805d7a4e3501c67f139f61882b00cfe55`.

The planning commit does not change the source or import witnesses used here. Counts describe committed inputs, not concurrent uncommitted implementation.

The [shared Liquid consolidation plan](sfm-core-liquid-consolidation-plan.md) owns implementation and acceptance. This document supports tasks 2.2 and 2.3. Neither successful generation nor this inventory establishes complete source consolidation.

## Source witnesses and counting rules

The starting catalog is the Git path `platform/minecraft/source-projection.json` at the source checkpoint. Its ten targets define twenty comparison contexts:

- each release context uses its `4.34.0-<target>` tag commit, recorded in `platform/minecraft/release-baselines/4.34.0-<target>/import.json`
- each development context except 1.19.2 uses the commit recorded in `platform/minecraft/development-baselines/<target>/import.json`
- the 1.19.2 development context uses the source checkpoint itself

The target keys are 1.19.2, 1.19.4, 1.20, 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.21.0, 1.21.1 and 26.1.2. The 1.21.0 key identifies upstream Minecraft 1.21.

These commits are witnesses, not final renderer inputs. Their preservation does not justify production generation from historical source overrides.

A unique path is a repository-relative file path under `platform/minecraft/src`. A blob variant is an exact Git blob identity. Counts do not normalize imports, whitespace or line endings.

"Never present in release" means absent from all ten release witnesses. It does not identify a feature by itself. Presence proves recorded availability, not independent feature-combination compilation or runtime acceptance.

## Exact source inventory

| Source set | Unique paths | Single exact blob | Multiple blobs | Never present in release |
| --- | ---: | ---: | ---: | ---: |
| Main Java | 1,605 | 1,136 | 469 | 1,057 |
| GameTest Java | 507 | 368 | 139 | 314 |
| Test Java | 488 | 442 | 46 | 452 |
| Generated resources | 277 | 159 | 118 | 16 |
| Main resources | 102 | 91 | 11 | 13 |
| Datagen Java | 15 | 0 | 15 | 0 |
| ANTLR | 5 | 4 | 1 | 2 |
| Test resources | 2 | 2 | 0 | 2 |
| Total | 3,001 | 2,202 | 799 | 1,856 |

Main Java has 14,291 file occurrences across the twenty contexts. Of its 1,605 unique paths, 520 occur in every context. The remaining 1,085 have context-dependent membership.

The 1,136 single-blob main-Java paths divide as follows:

| Occurrence rule | Paths | Initial ownership consequence |
| --- | ---: | --- |
| All twenty contexts | 195 | Shared core without a feature gate |
| Selected targets, both release and development | 11 | Genuine target membership, not a development feature |
| Only 1.19.2 and 1.19.4 development | 822 | Specific unreleased subsystem ownership required |
| All ten development contexts | 75 | Shared unreleased feature ownership required |
| Only the eight 1.20-or-later development contexts | 20 | Preserve historical subsystem implementations absent from current primary source |
| Only 1.19.2 development | 13 | Narrow target support required |

The 11 target-specific release files include 26.1.2 rendering and tooltip adapters, newer component support, an intermediate capability registry and older Mekanism energy support. Class membership follows API support, not a broad development flag.

The 20 later-development-only files cover legacy review bundles, review ledgers, source comparison, related actions and canvas editor registration. Their absence from current primary source is not permission to discard them.

The 13 1.19.2-only single-blob additions divide into 5 file-control or log classes, 6 manager-query or authorization classes, and 2 Client Manager GUI classes.

## Current primary release differences

Compare the source checkpoint with `4.34.0-1.19.2`, rather than treating an older ledger's canonical snapshot as current source.

| Source set | Added or changed paths |
| --- | ---: |
| Main Java | 1,114 |
| Test Java | 449 |
| GameTest Java | 306 |
| Generated resources | 23 |
| Main resources | 16 |
| Datagen Java | 5 |
| ANTLR | 3 |
| Test resources | 2 |
| Total | 1,918 |

Main Java contains 1,028 additions and 86 changed existing classes. Those 86 classes contain 356 zero-context diff hunks. Hunk counts are mechanical review units, not a count of distinct behaviors.

A preliminary coarse package classifier routed 756 additions into candidate subsystem groups. It left 272 additions unclassified. The routed additions still need semantic and dependency review. The classifier did not review the 86 existing classes or their 356 hunks.

Do not treat these unresolved groups as completed feature ownership. Do not hide them behind `dev_changes`, environment selection or a catch-all feature.

## Proposed incremental feature groups

Names in this section are proposals. Except for the three existing source flags, they are not registered compilation features.

The existing catalog declares `echo_action`, `touch_display_terminal_mount` and `regex_overlap_fix`. Together they describe 32 source effects, with no resource or dependency effects. They do not account for the complete release difference.

### Client interaction and discovery

| Proposed IDs | Initial ownership | Dependencies to review |
| --- | --- | --- |
| `client_actions`, `command_palette` | Action interfaces, Brigadier dispatch, palette interaction | Palette requires action infrastructure; programmatic descriptors stay separate |
| `workspace_panels`, `canvas_text_editor` | Panel layout, canvas editing, text-editor integration | Specific panel types require their owning feature, not every feature registered in the workspace |
| `keyboard_profiles`, `context_actions` | Typed keybindings and contextual action continuations | Action infrastructure; editor and panel hooks remain conditional |
| `file_explorer`, `registry_explorer` | Filesystem and registry discovery | Explorer and panel infrastructure; unavailable registries must remain unavailable |
| `explorer_lazy`, `explorer_search`, `explorer_compaction` | Newer explorer loading, matching and layout | Basic explorer; keep enhancements separately selectable |

### Editor presentation and review

| Proposed IDs | Initial ownership | Dependencies to review |
| --- | --- | --- |
| `client_theme`, `icon_rules` | Theme configuration and rule-based item previews | Icon rules require theme and presentation support |
| `syntax_languages`, `java_symbols` | Source syntax and Java semantic navigation | Editor; native or CLI boundary where used; semantic navigation is not basic syntax |
| `review_sessions`, `legacy_review_bundles`, `release_review` | Basic sessions, historical review bundles, newer release-review evidence | Review editors and explorer; preserve different supported implementations explicitly |
| `document_history`, `command_history` | Document operation history and persisted command history | Editor or action infrastructure respectively |
| `route_comparison`, `workspace_counterfactuals`, `trajectory_panels` | Experimental history and trajectory panels | Their histories and panel infrastructure |

### Terminal and control

| Proposed IDs | Initial ownership | Dependencies to review |
| --- | --- | --- |
| `terminal_java`, `terminal_remote`, `terminal_vox` | Local Java, remote and raster terminal backends | Shared terminal service and UI; raster must not become mandatory for legacy backends |
| `terminal_tuning` | Renderer, transport and diagnostic controls | Selected terminal backend |
| `client_control`, `file_control`, `client_log_capture` | Structured control bridge, live file opt-in, bounded logs | File control and logs use client control; keep their narrower target support |
| Existing `echo_action` | Echo command and registration | Basic actions; existing support declaration needs reconciliation with witnesses |
| `manager_operator_queries` | Privileged manager inspection responses | Sender authorization, request packets and client control |

### Packet data and language

| Proposed IDs | Initial ownership | Dependencies to review |
| --- | --- | --- |
| `packet_values`, `packet_values_floating` | Packet item, bounded codecs, finite doubles | Floating-point data requires values; retain integral compatibility |
| `packet_transport_private`, `client_inbox` | Private-world insertion, observations and addressed client reads | Values, server validation and shared effect budget |
| `packet_computation` | Structural patterns, bindings, copied text reads, generated inputs and broadcasts | Values and relevant transport; classify grammar, AST and existing transfer changes together |
| `sfml_execution_side`, `sfml_worded_intervals`, `sfml_literal_globs` | Side assertions, interval compatibility and literal glob support | Parser, AST, diagnostics and editor highlighting must agree |
| `multiplayer_packets` | Negotiated exact-grant multiplayer transport | Values, quotas and operator-owned policy; preserve separate private-world authority |

### Display and client execution

| Proposed IDs | Initial ownership | Dependencies to review |
| --- | --- | --- |
| `image_resources`, `touch_display` | Image handlers, display presentation and touch packets | Display uses image and value foundations |
| `client_manager`, `client_program_consent` | Distinct block, frame runtime and exact consent | Execution-side and frame language, display bindings and consent enforcement |
| `client_program_actions`, `client_program_reads` | Machine action contracts, world and inbox reads | Actions and value schemas; explicit consent capabilities |
| `client_program_signing`, `client_program_signer_trust` | Revision signatures, keys, ceremony and signer rules | Manager and consent; signer trust requires signing |
| `client_manager_gui` | Menu and separate client screen | Client Manager; keep synchronization behavior independent of opening the GUI |
| Existing `touch_display_terminal_mount` | Owned terminal binding, raster and touch-input routing | Display and selected terminal backend; raster and input permission checks remain distinct |

### Other production and test differences

| Proposed IDs | Initial ownership | Dependencies to review |
| --- | --- | --- |
| `computercraft` | Sixteen new ComputerCraft integration classes and registration changes | Optional-mod discovery, shared label operations and manager APIs |
| `redstone_live_read`, `redstone_buffer_storage` | Signal observations, stored counters, comparator behavior | Capability discovery and buffer persistence; keep read-only observations separate from transfers |
| `buffer_image_persistence` | Saved image replacement and handler lifecycle | Image resources and buffer state |
| `tooltip_inspection`, `tooltip_mode_override` | Item inspection and testable more-info mode | Editor or action integration; packet presentation requires packet values only when enabled |
| Existing `regex_overlap_fix`, `gametest_sides`, `puppet_runtime` | Focused regex behavior, side discovery and file-driven fixtures | Tests follow production owners; do not make every production feature depend on all fixtures |

Helpers shared by several features need explicit consumers. Their inclusion may be the OR of their consumers. That rule does not permit an unreviewed general-purpose development flag.

## Witnessed target availability

This table describes class presence in the development witnesses. It does not prove isolated flag combinations or a new supported release feature.

| Availability | Witnessed families |
| --- | --- |
| All ten targets | Basic actions, palette, panels, canvas editor, file explorer, theme, Java and remote terminals, basic review sessions, ComputerCraft |
| 1.19.2 and 1.19.4 | Lazy explorer, icon rules, command history, Vox raster terminal, release review, Java symbols, client control, packet values, image resources, touch display, Client Manager, consent, reads, signing, signer trust, multiplayer packets |
| 1.19.2 only | Live file-control bridge and log actions, Client Manager GUI, manager operator queries |

Representative Git paths under `platform/minecraft/src/main/java/ca/teamdman/sfm/` are:

- base support: `client/action/SFMClientAction.java`, `client/screen/SFMCommandPaletteScreen.java`, `client/screen/workspace/SFMScreenMultiplexer.java`, `client/screen/SFMDrawCanvasScreen.java` and `common/compat/computercraft/SFMNetworkPeripheral.java`
- advanced support: `client/explorer/lazy/SFMLazyExplorerLoader.java`, `client/terminal/SFMVoxTerminalFrameInbox.java`, `client/symbol/SFMJavaInteractionMap.java`, `common/item/PacketItem.java` and `common/block/ClientManagerBlock.java`
- signing support: `client/program/signing/ClientProgramSigningRuntime.java`, `client/program/ClientProgramSignerTrustRuntime.java`, `client/program/ClientProgramConsentRuntime.java` and `client/program/ClientProgramReadRuntime.java`
- narrow support: `client/control/SFMClientControlFileBridge.java`, `client/control/SFMClientControlLogBuffer.java`, `client/screen/ClientManagerScreen.java` and `common/net/SFMManagerShowQuery.java`

`EchoAction.java` has the same blob in all ten development witnesses. The existing catalog restricts `echo_action` to 1.19.2. Resolve that mismatch using actual registration and compile evidence.

## Shared classes that need hunk ownership

Highest zero-context hunk counts against the 1.19.2 release include:

| Class | Hunks | Review concern |
| --- | ---: | --- |
| `ASTBuilder` | 21 | Several language features share parser dispatch |
| `InputStatement` | 18 | Packet and observation behavior intersects existing transfers |
| `SFMResourceLocation` | 18 | Separate template/API adaptations from functional additions |
| `ProgramSyntaxHighlightingHelper` | 18 | Language, editor and semantic integrations intersect |
| `SFMTextEditScreenV1` | 16 | Existing editor behavior and workspace integration |
| `ProgramContext` | 15 | New variable/resource runtime alongside existing execution |
| `Program` | 13 | Declarations, validation, triggers and reconstruction |
| `ProgramBuilder` | 10 | Parser and execution-side validation |
| `LimitedInputSlot` | 10 | Existing transfer behavior and generated inputs |
| `SFMPackets` | 9 | Protocol version, registration and authority boundaries |

Registration classes, client configuration, `SFMClientRegistrations`, `SFMCommandPaletteActions`, datagen classes and language resources also need per-feature ownership. A whole-file feature assignment would erase independent behavior.

### Action interface dependency trap

`SFMClientAction.java` now references programmatic descriptors and value schemas through optional default methods. The basic action interface must not force every palette build to include Client Manager and packet infrastructure.

Assign the descriptor imports and `programmaticDescriptor` and `programmaticHandler` methods to `client_program_actions`. Keep basic action behavior independently available on the historical 1.20-or-later development targets.

### Label helper dependency trap

`LabelGunActions.java` serves ComputerCraft but now imports `ClientManagerBlockEntity`. It also adds Client Manager push and pull overloads.

Keep shared label operations available for ComputerCraft across all ten targets. Gate only Client Manager imports and overloads with their actual owner.

### Release protocol and asset trap

The 1.19.2 release uses network channel version `1.0.0`. Current primary source uses `1.5.0`, appended registrations and changed direction handling.

Omitting new packet classes is insufficient. Release rendering must restore the protocol, registration order and existing packet behavior. Models, recipes, loot, block entities, menus, localization, textures and mixin declarations need matching feature conditions.

## Required ownership algorithm

1. Enumerate every path, context, blob and membership condition. Preserve the witness commits and keep comparison data separate from renderer inputs.
2. Mark genuine API differences separately from functional release and development differences. Do not disguise feature drift as a Minecraft condition.
3. Assign each added path a reviewed feature and target predicate. Assign its tests, resources, registrations and dependency use to the same owner.
4. Assign existing-class changes by declaration or hunk. Record shared ownership and review dependency cycles before deciding whether helpers need conditional inclusion.
5. Reject unresolved ownership at final acceptance. Validate enabled and disabled sources, registration, formats, assets and JAR membership against every release witness.

The next review should cover action subclasses and registration aggregators first. They account for much of the 272 unrouted additions and expose cross-feature dependencies.

The unresolved boundary remains explicit: 272 additions lack even preliminary routing; 86 existing classes contain 356 unreviewed hunks. Routed additions and 469 cross-context main-Java variant paths still need semantic review.

## Reproduce the read-only counts

Run these commands from the repository root. They inspect committed objects and do not build, generate or write source.

### Current primary difference

```pwsh
$checkpoint = 'f3ff2f6425434f36c7c680fa909c977b158e1860'
$changes = git diff --name-status 4.34.0-1.19.2 $checkpoint -- platform/minecraft/src
$changes |
    ForEach-Object {
        $parts = $_ -split '\s+', 2
        $path = $parts[1] -replace '^platform/minecraft/src/', ''
        $segments = $path -split '/'
        [pscustomobject]@{
            Status = $parts[0]
            SourceSet = $segments[0] + '/' + $segments[1]
        }
    } |
    Group-Object SourceSet |
    Sort-Object Count -Descending |
    Select-Object Name, Count |
    ConvertTo-Json

git diff --name-status --diff-filter=M 4.34.0-1.19.2 $checkpoint -- platform/minecraft/src/main/java
git diff --unified=0 --diff-filter=M 4.34.0-1.19.2 $checkpoint -- platform/minecraft/src/main/java |
    Select-String '^@@ ' |
    Measure-Object |
    Select-Object Count |
    ConvertTo-Json
```

The final command counts 356 hunks. Count `A` and `M` records restricted to `src/main/java` to obtain 1,028 and 86.

### Twenty-context blob and membership inventory

```pwsh
$checkpoint = 'f3ff2f6425434f36c7c680fa909c977b158e1860'
$catalog = git show "${checkpoint}:platform/minecraft/source-projection.json" | ConvertFrom-Json
$contexts = [Collections.Generic.List[object]]::new()

foreach ($target in $catalog.targets) {
    $releasePath = "platform/minecraft/release-baselines/4.34.0-$($target.id)/import.json"
    $release = (git show "${checkpoint}:$releasePath" | ConvertFrom-Json).targets[0]
    $developmentCommit = $checkpoint
    if ($target.id -ne '1.19.2') {
        $developmentPath = "platform/minecraft/development-baselines/$($target.id)/import.json"
        $developmentCommit = (git show "${checkpoint}:$developmentPath" | ConvertFrom-Json).targets[0].tag_commit
    }
    $contexts.Add([pscustomobject]@{ Id = "release/$($target.id)"; Commit = $release.tag_commit })
    $contexts.Add([pscustomobject]@{ Id = "development/$($target.id)"; Commit = $developmentCommit })
}

$paths = @{}
foreach ($context in $contexts) {
    foreach ($entry in (git ls-tree -r $context.Commit -- platform/minecraft/src)) {
        if ($entry -match '^\d+ blob ([0-9a-f]+)\tplatform/minecraft/(.+)$') {
            $blob = $matches[1]
            $path = $matches[2]
            if (-not $paths.ContainsKey($path)) {
                $paths[$path] = [Collections.Generic.List[object]]::new()
            }
            $paths[$path].Add([pscustomobject]@{ Context = $context.Id; Blob = $blob })
        }
    }
}

$facts = @($paths.GetEnumerator() | ForEach-Object {
    $segments = $_.Key -split '/'
    [pscustomobject]@{
        Path = $_.Key
        SourceSet = $segments[1] + '/' + $segments[2]
        Occurrences = $_.Value.Count
        Variants = @($_.Value.Blob | Select-Object -Unique).Count
        ReleaseCount = @($_.Value | Where-Object { $_.Context.StartsWith('release/') }).Count
        Membership = $_.Value.Context -join ','
    }
})

$facts | Group-Object SourceSet | ForEach-Object {
    [pscustomobject]@{
        SourceSet = $_.Name
        UniquePaths = $_.Count
        SingleBlob = @($_.Group | Where-Object Variants -eq 1).Count
        MultipleBlobs = @($_.Group | Where-Object Variants -gt 1).Count
        NeverInRelease = @($_.Group | Where-Object ReleaseCount -eq 0).Count
    }
} | ConvertTo-Json

$facts |
    Where-Object { $_.SourceSet -eq 'main/java' -and $_.Variants -eq 1 } |
    Group-Object Membership |
    Sort-Object Count -Descending |
    Select-Object Name, Count |
    ConvertTo-Json
```

To verify a feature witness, inspect its path in each development commit:

```pwsh
git ls-tree -r --name-only <development-commit> -- platform/minecraft/src/main/java
git show <development-commit>:platform/minecraft/src/main/java/ca/teamdman/sfm/client/action/SFMClientAction.java
```

To review existing behavior, compare the exact release and development commits for that target:

```pwsh
git diff --unified=3 <release-commit> <development-commit> -- <repository-relative-source-path>
```

## Evidence limits

This inventory did not run builds, installers, runtime tests or new dependency acquisition. It did not change registered features, renderer selectors, lockfiles or generated projects.

The preliminary 756-path package routing is reconnaissance, not a reproducible semantic classifier or an accepted ownership ledger. The ownership algorithm must produce a reviewed path and hunk matrix before task 2.3 can finish.

## Reviewed subset: client actions and command palette

This bounded review inspected the interfaces, default action handler, compiler, execution path, completion models, result envelope, registration aggregators and selected palette lifecycle and rendering paths. It also compared the simpler 1.20 development implementation with the primary implementation. It does not certify every method of the 2,457-line primary palette screen or compile independent feature combinations.

The following lists are exact added paths, not directory-prefix ownership rules. Paths in the tables are relative to `platform/minecraft/src/main/java/ca/teamdman/sfm/`. Every listed addition is absent from all ten release witnesses. `D10` means present in all ten development witnesses; `D2` means present only in 1.19.2 and 1.19.4 development. Blob counts use the unchanged source witnesses defined above.

The proposed IDs remain proposals. This review has not registered flags or changed the earlier 272-addition, 86-class or 356-hunk unresolved checkpoints. Those checkpoints require reconciliation against a reviewed ownership ledger, not subtraction of this table's rows.

### Basic client-action declarations

The basic `client_actions` unit owns the registry, human invocation contract, availability checks, source context, feedback and Brigadier command dispatch. Its default handler is `SFMClientAction.configureCommandNode()` delegating through `invoke()` to `execute()`, not a separate handler class. Both registration-time predicates and invocation-time availability checks belong to this unit.

| Added path | Support | Blobs | Ownership boundary |
| --- | --- | ---: | --- |
| `client/action/SFMClientAction.java` | D10 | 3 | Basic contract; machine descriptor and handler defaults are separate members |
| `client/action/SFMClientActionRequirement.java` | D10 | 1 | Context-to-availability function |
| `client/action/SFMClientActionAvailability.java` | D10 | 1 | Typed available target or unavailable component |
| `client/action/SFMClientActionContext.java` | D10 | 2 | Origin identity and freshness; panel capture is an optional workspace integration |
| `client/action/SFMClientActionSource.java` | D10 | 2 | Context and component feedback; structured-result consumer is a separate extension |
| `client/action/SFMClientActionExecutor.java` | D10 | 2 | Human dispatch; provenance scope is a keyboard integration |
| `client/action/SFMClientActionDispatcherCompiler.java` | D10 | 3 | Duplicate-ID rejection and `sfm action invoke/list/help` grammar; history supplier is optional |
| `client/action/SFMClientActionCommandTree.java` | D10 | 3 | Parse, execute and availability-filtered Brigadier suggestions; palette ranking, choice surfaces and history have separate member ownership |
| `client/registry/SFMClientActions.java` | D10 | 4 | Client-only registry and command-tree creation; two machine-binding lookup methods are separate |

These nine whole paths contain several feature-owned extensions. Keeping a path under `client_actions` must not retain all its optional imports or members when those owners are disabled. Conversely, omitting its extension must not remove the basic interface or registry required by other human actions.

The inspected 1.20 witness already has `sfm action list`, `sfm action help` and `sfm action invoke`. These are not exclusive to the newer control transport. Its source carries context and component feedback, without the structured-result consumer. Its interface already carries optional item-icon metadata, without machine descriptors.

### Command-palette declarations

The `command_palette` unit depends on `client_actions`. It owns the global palette surface, opening action, key entry point and its suggestion application. Newer typed completion and inspection support can remain one coherent palette unit initially; the evidence does not require inventing a separate flag for every helper record.

| Added path | Support | Blobs | Reviewed purpose |
| --- | --- | ---: | --- |
| `client/action/OpenCommandPaletteAction.java` | D10 | 1 | Open the palette with an optional initial query |
| `client/action/CommandPaletteHelpAction.java` | D10 | 2 | Human help document; primary action-detail result has optional machine-result and descriptor dependencies |
| `client/action/SFMClientCommandInsertion.java` | D10 | 3 | Required-argument separator and caret policy, also used by shortcut drafts |
| `client/handler/SFMCommandPaletteKeyHandler.java` | D10 | 3 | Key edge detection and opening through the registered action |
| `client/screen/SFMCommandPaletteScreen.java` | D10 | 6 | Screen lifecycle, asynchronous suggestions, execution and feedback; integrations listed below |
| `client/action/ClosePaletteAction.java` | D2 | 1 | Dismiss a transient action surface |
| `client/action/CommandPaletteSuggestionSelectionAction.java` | D2 | 1 | Select the first or last visible suggestion |
| `client/action/SFMClientActionCompletion.java` | D2 | 1 | Opt-in typed argument candidates and contextual continuations |
| `client/action/SFMCommandFrontierAnalysis.java` | D2 | 1 | Brigadier replacement range, smart usage, missing arguments and diagnostics |
| `client/action/SFMPaletteCandidate.java` | D2 | 1 | Bounded suggestion metadata and insertion intent |
| `client/action/SFMCompletionApplication.java` | D2 | 1 | Immutable evidence for one applied completion; does not depend on history |
| `client/action/SFMPaletteCandidateInspection.java` | D2 | 1 | Capture-time display, surface, command and help projections |
| `client/action/SFMPaletteCandidateCopyAction.java` | D2 | 1 | Copy one captured candidate projection |
| `client/action/SFMPaletteCandidateSetCopyAction.java` | D2 | 1 | Copy one projection of an entire captured candidate set |

The last two actions import `SFMActionChoice` to expose constrained contextual choices. Their host interfaces and clipboard writers do not import the history runtime. Preserve the copy actions with the inspected-context feature combination until their choice integration is consolidated; do not attach them to every action subclass merely because they implement `SFMClientAction`.

The primary palette directly imports theme, keyboard, document-history, icon-preview, focus, diagnostics and constrained-choice support. The 1.20 implementation already imports theme and keyboard services but lacks the newer history and choice interfaces. Therefore a standalone palette with all these units disabled is not a witnessed working combination. The first supported combination may require theme and keyboard presentation explicitly until a reviewed fallback exists. History and contextual-choice members need owner-specific guards rather than an invented cyclic dependency between the entire action registry and every consumer.

### Shared helpers and viable ownership rules

Shared helper inclusion should use the union of real consumers. A consumer does not automatically enable the helper's package's entire subsystem. The rules below identify viable consolidation boundaries; their independent compilation still needs proof.

| Added path | Support | Blobs | Viable inclusion rule or limit |
| --- | --- | ---: | --- |
| `client/screen/workspace/SFMWorkspacePanelId.java` | D10 | 1 | Pure validated long ID; include for action-context or workspace consumers without importing the panel runtime |
| `client/presentation/SFMItemIcon.java` | D10 | 4 | Pure resource-ID and accessible-label carrier; action metadata or any icon consumer, not the theme runtime |
| `client/presentation/SFMResolvedItemIcon.java` | D10 | 1 | Item-stack snapshot; any resolved-icon consumer |
| `client/presentation/SFMItemIconResolver.java` | D10 | 3 | Any item-icon presentation consumer; uses the existing well-known item registry |
| `client/presentation/SFMItemIconRenderer.java` | D10 | 4 | Any GUI icon consumer; Minecraft render adapters, not persisted theme rules |
| `client/presentation/SFMTextSummary.java` | D2 | 1 | Pure text fitting; palette or explorer or another actual text-summary consumer |
| `client/screen/widget/SFMVerticalListViewport.java` | D2 | 1 | Pure selection and geometry model; palette or another scrolling-list consumer |
| `client/screen/widget/SFMConsoleWidget.java` | D10 | 5 | Shared feedback viewport; palette or another console consumer |
| `client/screen/SFMTransientActionScreen.java` | D2 | 1 | One-method dismissal contract; palette or other transient action surfaces |
| `client/action/SFMCanonicalTokenArgument.java` | D2 | 1 | General Brigadier token helper; union of actions that use it, not every explorer runtime |
| `client/search/SFMFuzzyScorer.java` | D2 | 1 | Palette or explorer search or another fuzzy-ranking consumer; retains the existing Simmetrics dependency |
| `client/action/SFMActionElement.java` | D2 | 1 | Semantic control metadata; action-addressable widgets or audits, not the entire keyboard service |
| `client/action/SFMActionElementAudit.java` | D2 | 1 | Validation of semantic controls; its audited consumers |
| `client/action/SFMFocusTargetHost.java` | D2 | 1 | Pure focus-target contract; focus action or a contributing screen |
| `client/screen/SFMScreenDiagnosticsContributor.java` | D2 | 1 | Pure diagnostic-lines contract; diagnostic action or contributing screen |

`SFMWorkspacePanelId` does not make the action context depend on the entire workspace. The `instanceof SFMScreenMultiplexer` branch in `SFMClientActionContext.create()` is the actual runtime dependency and needs a workspace-owned integration. The ID carrier can remain shared.

Likewise, `SFMCompletionApplication` and candidate inspection records may retain history labels, icon IDs and shortcut strings without importing those runtimes. Producing those fields is the owner's integration. The bounded `SFMClientActionStructuredResult` contains a schema ID and validated JSON object, not an `SFMValue` or consent identity. It is generic structured action-result infrastructure, not a programmatic descriptor.

The registry abstractions `SFMDeferredRegister`, `SFMDeferredRegisterBuilder`, `SFMRegistryObject`, `SFMRegistryWrapper` and `SFMWellKnownRegistries` already occur in all twenty witnesses. So do `SFMButtonBuilder` and `SFMFontUtils`. Do not classify these entire existing files as added palette infrastructure. Any release-to-development hunks still need separate review. The primary console also uses `SFMScissorStack`, witnessed only in D2; newer-target render adapters remain a distinct API-consolidation task.

### Extensions excluded from the basic action unit

The following exact paths are D2 additions with one blob each. They must not enter `client_actions` just through their name or package:

- `client/action/SFMClientActionDescriptor.java`: machine input/result schemas and data scopes; imports `SFMValue` and `SFMValueSchema`
- `client/action/SFMClientActionProgrammaticHandler.java`: machine handler on validated SFM values
- `client/action/SFMClientActionProgrammaticContext.java`: caller identity and principal; imports `ClientProgramIdentity`
- `client/action/SFMClientActionAuthorizationService.java`: consent, live Client Manager identity and effect budgets
- `client/action/SFMClientProgramActionDispatcher.java`: program-adapter dispatch, not the Brigadier compiler
- `client/action/SFMClientActionStructuredResult.java`: optional generic JSON result envelope; belongs with the consumers requiring structured action results, not with the machine-descriptor feature by default
- `client/action/SFMClientActionInvocationTrace.java`: keyboard provenance carrying key events, binding snapshots and conflicts
- `client/action/SFMClientActionArgumentHistory.java`: parsed history ranking; currently recognizes panel-open scene families
- `client/action/SFMClientActionContinuation.java`: captured live workspace panel and palette ownership for delayed callbacks
- `client/screen/widget/SFMKeycapRenderer.java`: keyboard-sequence rendering; imports `SFMKeySequence` and `SFMKeyBindingDisplay`

The primary `CommandPaletteHelpAction` calls `programmaticDescriptor()` while formatting its action detail and publishes a structured result. Its basic help-document behavior is not evidence that the descriptor or result features must always be enabled. Gate those detail members with their actual owners, or initially support the coherent detail feature combination and document that limit.

`SFMActionChoice` is a pure validated command carrier, but `SFMChoiceSession` executes through its service and catalog and builds an isolated command tree. The latter is not a pure carrier. Those session services, catalogs and their further consumers remain unresolved by this bounded review; do not silently absorb the complete constrained-choice subsystem into the basic action unit.

### Registration is not subclass ownership

Two added aggregators are D10 with five exact blobs each: `client/action/SFMCommandPaletteActions.java` and `client/SFMClientRegistrations.java`.

The former registers much more than palette actions: panels, clipboard operations, toast controls, command history, keyboard settings, Minecraft screens, managers, REPL, terminals and themes, as well as primary-only file-control and log actions. Keep each field, factory, type import and referenced helper with its functional owner. Its class name is not permission to enable all these subclasses through `command_palette`.

The latter also registers explorers, overlays, packet actions, program consent/read actions, symbols, review tools and workspace history features. Its setup listener initializes multiplayer, menus, command history and the action tree. Registration calls and setup members require the same ownership as the feature being registered. A common shell can belong to the union of client features that need registration; it must not become a mandatory dependency on every registered feature.

Next, enter these exact paths and member boundaries into the ownership ledger, reconcile the baseline classifier, consolidate the shared helpers once, and validate a small supported action-plus-palette combination. No independent flag combination or cross-target compile is claimed by this appendix.
