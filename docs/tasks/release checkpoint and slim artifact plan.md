# Release checkpoint and feature graduation plan

**Plan status:** Active
**Primary implementation root:** `D:\Repos\Minecraft\SFM\repos2\1.19.2`
**Last updated:** 2026-08-06
**Update rules:** Keep this plan executable. Record decisions and evidence beside the affected work item, keep at most one current implementation focus, and update this file after every release-scope or artifact-policy change. Do not mark a phase complete from compilation alone; attach the command and the artifact or runtime evidence that proves it.

## Purpose

Checkpoint the unpublished 1.19.2 work into a releasable product scope and graduate the prospective command-palette, Rust/Vox terminal, and related tooling features to a complete, distributable implementation. The release must have an explicit support statement, a known dependency footprint, and an artifact that can be installed and exercised outside userdev. The lockfile will provide feature-aware entry-point profiles so the Rust CLI can build the full feature set while legacy Gradle remains a low-friction Java-only contributor path by default.

This plan coordinates the existing terminal-bridge, dependency-lock, and cross-version propagation plans. It does not replace them and does not authorize publishing or pushing by itself.

## Interaction follow-up guidance ledger — 2026-08-05

| ID | Active user guidance | Required plan consequence | Superseded by |
| --- | --- | --- | --- |
| I-SEL-1 | Dragging in the SFM Rust terminal must create the same visible selection as native Teamy Terminal. Rust remains authoritative, but Java must receive selection as terminal state and draw the existing inversion/highlight rather than depending only on selected pixels baked into a raster. | Vox plan V-4.2e and Teamy 3.6.4f activate the existing selection coordinates, separate remote selection presentation from base pixels, and prove drag state without rerasterizing unchanged glyphs. | — |
| I-SEL-2 | Ctrl+C copies and clears an active selection; with no selection it remains the PTY interrupt. Right click atomically copies/clears when selection exists and otherwise initiates paste. | V-4.2e defines typed copy/no-selection results and one client decision path shared by keyboard and right-click. | — |
| I-PASTE-1 | Any paste containing CR or LF must stop before PTY mutation and show the exact multiline warning, bounded clipboard preview, and `Paste anyway` / `Cancel` choices. | V-4.2e specifies guard detection, exact warning copy, bounded private preview, focus restoration, and no-write-before-confirmation tests. | — |
| I-PASTE-2 | Teamy Terminal and Java each retain their own clipboard adapter while collaborating through typed guarded and bypass paste signals, with supplied versus automatic clipboard bodies. | Teamy 3.6.4f and V-4.2e define one guard engine and Java-friendly wire records equivalent to `Paste{WithGuard,WithoutGuard}` and `PasteBody{Supplied,Auto}`. | — |
| I-KEY-1 | Focused-panel scale increase/decrease/clear use one canonical main-row physical binding each: Ctrl+Equal, Ctrl+Minus, and Ctrl+0. The UI renders separated physical-key tokens such as `Ctrl =`, not ambiguous logical-glyph strings such as `Ctrl++`; duplicate keypad defaults are not shipped. They must appear in the palette `[?]` binding behavior. | P-5.2 corrects the defaults, migration fingerprint and exact event-consumption tests, and reuses one token model for text fallbacks plus pink read-only keycaps; K-6 makes capture keycaps focusable/removable. | Earlier Ctrl+plus/main-keypad parity wording |
| I-KEY-2 | Clearing a panel scale selects auto/inherited rather than numeric zero. If auto resolves to N, the first increase selects N+1 and the first decrease selects explicit N; later adjustments continue numerically. Thus auto=4 gives `Ctrl+0, Ctrl+= -> 5` and `Ctrl+0, Ctrl+- -> 4`, never 1. | P-2.5 defines auto's ordered transition position; P-5.2/K-7 prove the registered actions and contextual shortcuts use it. | — |
| I-KEY-3 | Panel scale feedback must be a transient fading toast, not a persistent panel label. Auto reports both state and effective value (`gui scale auto (4)`). A relative adjustment at a numeric boundary repeats the current scale toast with a small shake. | P-2.6 removes the persistent affordance and P-5.5/K-7 proves fade, effective-auto text, and boundary re-notification. | — |
| I-PAL-1 | Typing a later grammar atom in the action slot, such as `sfm action invoke term`, must discover complete grammar-valid literal continuations including `sfm:panel/open sfm:terminal`, directional panel-open variants, and `sfm:terminal_properties`. | P-5.2 adds bounded literal-descendant traversal over the already-compiled Brigadier tree, returns whole continuation paths, preserves action metadata from the first token, and never invokes argument suggestion providers during the traversal. | — |
| I-HIST-1 | The palette stores command history for ranking; at the default `sfm action invoke ` query, the exact most recently executed command is the first suggestion. | P-5.3 records successful palette executions and injects full-command MRU candidates ahead of ordinary blank-query action ids. | — |
| I-HIST-2 | `sfm:palette/history/open` opens a read-only history document, supports center/left/right/above/below placement and an optional editor id, and discards changes. | P-5.4 uses hierarchical placement actions, with the base action meaning center/focused, and enforces read-only behavior across every selectable editor. | — |
| I-HIST-3 | `sfm:palette/history/clear` empties history. | P-5.4 makes maintenance actions non-recordable so clear remains truly empty. | — |
| I-LIST-1 | `sfm:keybindings/manage` needs a real scrollbar and complete wheel/keyboard traversal. | P-5.1 removes data truncation and uses a shared bounded list viewport. | — |
| I-LIST-2 | Clicking the shortcuts search box or nearby non-row space must never open row zero. | P-5.1 requires widget-first dispatch and explicit half-open row hit bounds; negative division is never used as a hit test. | — |
| I-LIST-3 | The command palette suggestion list needs a scrollbar and mouse-wheel behavior, independently from its feedback-console scrollbar. | P-5.1 routes by hovered region and proves suggestion/console scrolling cannot steal from one another. | — |
| I-CHOICE-1 | F3 and unhandled Escape present constrained choices through the ordinary command-palette view, search, suggestion selection, scrolling, metadata, and execution path. SFM must not maintain a second button-list chooser or second keyboard-focus model. | P-5.0 replaces `SFMActionChoiceScreen` with ephemeral `sfm choose <choice-session-id> ...` command surfaces; P-5.1 proves the normal and constrained palettes share one viewport implementation. | — |
| I-CHOICE-2 | A choice session exposes only its snapshotted valid choices as Brigadier-valid completions. It must preserve the originating action context, reject stale/unknown sessions, clean itself up, and never persist an ephemeral session id into command history. | P-5.0 defines session-scoped command trees, lifecycle and target capture; P-5.3 records only the canonical chosen action when ordinary history policy permits it. | — |
| I-TERM-HIT-1 | Once a Rust terminal frame is presented, clicking that viewport must never activate the disconnected `Start / Retry Rust server` control that used to occupy the same coordinates. | P-5.0 makes disconnected controls explicitly state-bound and adds a disconnected-to-presented hit-routing regression test. | — |

## Interaction guidance traceability

| Guidance | Plan coverage | Evidence when complete |
| --- | --- | --- |
| I-SEL-1, I-SEL-2, I-PASTE-1, I-PASTE-2 | Vox terminal plan V-4.2e; Teamy Terminal plan 3.6.4f | Facet round trips/package, Teamy core/Vox/native tests, SFM focused tests, and a live normal/high-scale selection/copy/paste puppet |
| I-KEY-1 | P-5.2 and P-5.5 | Storage/default/conflict tests, palette `[?]` capture, and focused-terminal non-leak assertion |
| I-KEY-2 | P-2.5, P-5.2, and P-5.5 | Pure auto/explicit transition tests plus a live clear/increase/clear/decrease artifact showing effective and stored scales |
| I-KEY-3 | P-2.6, P-5.2, and P-5.5 | Live toast captures for auto/effective text, fade-out, and repeated boundary attempts with shake |
| I-PAL-1 | P-5.2 and P-5.5 | Unit proof for full nested paths, unavailable-tree exclusion and executability, plus a live `term` palette capture |
| I-HIST-1 | P-5.3 and P-5.5 | Bounded storage/ranking tests and a live execute/reopen/MRU witness |
| I-HIST-2, I-HIST-3 | P-5.4 and P-5.5 | Action completion, all placements, editor selection/read-only enforcement, and clear-remains-empty witness |
| I-LIST-1, I-LIST-2, I-LIST-3 | P-5.1 and P-5.5 | Pure viewport geometry tests plus wheel, thumb-drag, search-hit isolation, and palette/console routing puppets |
| I-CHOICE-1, I-CHOICE-2 | P-5.0, P-5.1, P-5.3, and P-5.5 | Exact constrained Brigadier tree, shared palette viewport/navigation, captured-target and stale-session tests, canonical-history proof, and live F3/Escape artifacts |
| I-TERM-HIT-1 | P-5.0 and P-5.5 | A disconnected-to-presented render/input regression plus live proof that the old button rectangle delivers terminal mouse input without another start attempt |

**Intent audit:** Passed 2026-08-05 against the complete 2026-08-05 user-testing message.

### Intent audit evidence

- **Pass 1 — extraction:** Reread the message from terminal drag through palette scrolling and split every observable behavior, example, ownership statement, action id, warning string, and qualifier into I-SEL-1 through I-LIST-3.
- **Pass 2 — traceability:** Mapped every active id to V-4.2e/Teamy 3.6.4f or P-5.1 through P-5.5, including protocol, persistence, UI, focused tests, and live evidence rather than treating the list as detached notes.
- **Pass 3 — adversarial omission:** Rechecked the distinctions between base pixels and Java selection overlay, copy-versus-interrupt Ctrl+C, copy-versus-paste right click, guarded-versus-bypass paste, exact full-command MRU ordering, center as the base hierarchical open action, optional editor selection, read-only/discard semantics, and the two independent palette scroll regions.
- **Known source limitation:** None for this follow-up message. Earlier release and panel requirements remain in their existing durable ledgers and completed work-item notes.

### Constrained-choice follow-up — 2026-08-05

The later F3/Escape testing supersedes the bespoke bounded button chooser, but
not its curated choice sets. Both interactions now use the same command-palette
surface as ordinary commands. Each opening creates an ephemeral constrained
command surface rooted at `sfm choose <choice-session-id> `; the active
session's exact valid choices are the only Brigadier-valid descendants and
therefore the only suggestions. This is not a post-ranking filter over
`sfm action invoke`, and it must not duplicate palette selection, search,
scrolling, metadata, feedback, or keyboard behavior.

The observed terminal click-through and chooser focus split are new regression
evidence, not omissions from the preceding selection/copy/paste intent audit.
P-5.0 records their concrete state and ownership corrections before the rest of
the P-5 command-surface work.

### Contextual input, action ownership, and address follow-up — 2026-08-05

The authoritative atomized plan for the later keyboard-navigation and
Action/Registry Explorer guidance is
`docs/tasks/contextual input actions and addressable explorer plan.md`. It owns
the panel child-widget/focus contract, registered keyboard-usage situations,
contextual defaults, terminal/properties migration, panel resize/duplicate
semantics, binding-management UX, action-element audit, typed addresses,
resolvers/devices, and explorer projections.

Release P-5.2 now coordinates the contextual default/action portion of that
plan instead of seeding unscoped panel-scale keys. Its K-1 through K-7 items are
release-correctness work under the current recommendation. Address phase A
must at least freeze any public owner/address contracts before release; whether
the complete Action/Registry Explorer is a release blocker remains design gate
D-6 in the linked plan.

**Follow-up intent audit:** Passed 2026-08-05. The linked plan records the full
three-pass audit against the complete message, including the earlier move-only
decision's explicit supersession, exact Microsoft Terminal defaults, concrete
address examples, semantic-versus-coordinate action ownership, and the
release-boundary gate. This coordinating summary is not the authoritative
requirements ledger for that message.

## Scope

In scope:

- Decide which unpublished 4.35-era functionality is release-ready, deferred, or dev-only.
- Graduate the player-facing features and close their acceptance, packaging, and fallback gaps.
- Make Rust artifact planning and legacy Gradle dependency/source-set behavior agree.
- Bundle the Java Vox runtime with loader-native Jar-in-Jar and prove the final artifact contains it.
- Define the external `teamy-terminal` companion-server distribution and graceful absence behavior.
- Reproduce the current multi-second Rust-terminal presentation delay and use
  the linked renderer/transport matrix to decide whether the release path is
  Rust CPU pixels, Rust GPU/slug pixels, Java semantic-cell rendering, or a
  recorded deferral.
- Keep the full Rust-toolchain feature set as the release target while preserving a Java-only Gradle contributor profile by default.
- Add declarative lockfile feature membership and entry-point defaults; do not encode Rust/Vox or project-specific dependency names in Gradle scripts.
- Produce release artifacts, clean-install evidence, release notes, and a PR/issue disposition matrix.
- Propagate only the approved baseline changes oldest-first according to `docs/AGENTS.md`.

Out of scope for this checkpoint:

- Cloud Terrastodon changes.
- Replacing the legacy Gradle build or requiring Gradle for normal work.
- Treating the incomplete Rust/Vox terminal bridge as release-ready without its remaining acceptance and packaging proof.
- Requiring Cargo/Xtask during ordinary Gradle configuration or IntelliJ synchronization.
- Treating `--slim` as the feature model. If a compatibility alias is ever added, it must select a declarative profile rather than hard-code exclusions.
- Predetermining a GPU/Vulkan rewrite as the release answer. Teamy Terminal
  Phase 3.6 and Vox bridge V-4 may implement and compare the Teamy Studio slug
  path, but this release plan consumes their evidence rather than assuming the
  GPU candidate wins.
- Automatically merging or pushing open PRs.

## Established foundation

- The canonical maintained worktree is the 1.19.2 branch at `D:\Repos\Minecraft\SFM\repos2\1.19.2`. `docs/AGENTS.md` requires work to start there and propagate forward with `sfm-propagate-changes.exe`; newer version worktrees must not be clobbered.
- The canonical branch contains the local terminal, palette, CLI-diagnostic, dependency-lock, and plan checkpoint commits and remains intentionally unpublished. This is a checkpoint/publishing concern, not permission to push.
- `platform/minecraft/gradle.properties` still reports `mod_version=4.34.0`, while `platform/minecraft/src/main/resources/assets/sfm/template_programs/changelog.sfml` starts with `4.35.0 PRE`. The version and scope are therefore not release-locked.
- `docs/tasks/puppet propagation and preview matrix plan.md` already contains the broad release sequence: release scope, metadata, version resources, automated tests, artifact collection, isolated installs, and player-like packaged verification. This plan supplies the missing feature-profile and release-triage contract.
- `docs/tasks/vox terminal bridge and graceful degradation plan.md` remains active. Batch 1 and Batch 2 now have focused tests and live puppet proof; remaining release work includes native-size presentation, a computational witness for the current multi-second lag, the Rust CPU/Rust GPU/Java renderer matrix, packaging, companion-server distribution, and final clean-install/runtime acceptance.
- `docs/tasks/teamy terminal repository and Vulkan renderer plan.md` remains active for the Teamy Terminal side. The current CPU full-PNG bridge is the correctness baseline, not yet the release-performance winner. Phase 3.6 owns the Rust CPU/GPU renderers and comparative harness; Vox bridge V-4 owns Java renderers and end-to-end Minecraft evidence.
- `docs/tasks/cc tweaked integration plan.md` is marked Complete. Parking CC for a release is therefore a new product-scope decision; it must not silently rewrite the historical completion record.
- `docs/tasks/dependency source management v3 plan.md` is active and is the source of truth for lockfile projection. The 1.19.2 lock classifies `cc-tweaked` as a loader-managed optional mod and now classifies `vox-java` as compile/runtime/bundle with an explicit bounded policy.
- `docs/architecture/vox-java-jar-in-jar-packaging-research.md` records the existing Jar-in-Jar precedent. Forge 1.19.2 and the Rust builder both have the packaging path; the remaining release gates are source provenance/reachability, Gradle resolution of the source-built artifact, and clean-install runtime proof.
- The recent Rust/SFM terminal commits are local unpublished functionality, including Rust input/frame routing and solo bridge actions. The independent Java-local REPL must remain usable when the Rust service is absent; it is not rendered as an inline fallback inside the Rust terminal scene.
- The existing `--solo` behavior is a run-time classpath selection, not a feature profile: it may omit loader-managed integration mod jars and deobfuscated project dependencies while retaining required plain libraries. It must not become the Gradle contributor default or silently disable JEI, CC:Tweaked, Mekanism, or other independently declared integrations.

## Historical user-testing evidence — 2026-08-01 (resolved)

These observations were release-correctness inputs. Release item 0.3 and the
Batch 1 evidence below record their completed fixes:

- Typing `open` originally did not fuzzy-rank action results. The hybrid
  candidate layer now ranks the action-id slot while Brigadier remains
  authoritative for parse ranges, availability, arguments, and execution.
- The Rust PNG path originally rendered the Java-local `> _` input strip. The
  Rust frame now owns the full terminal content region.
- Triple-Escape and triple-Tab originally lacked progress cues. Their
  time-bounded status indicators are now covered by focused tests and puppets.

Do not reopen these completed findings unless a new regression is observed.

## User-testing evidence — 2026-08-02

These observations supersede the assumption that the palette/workspace slice
is complete. The requirements below preserve the concrete interaction laws,
not only the names of the proposed features.

- The command intended to open SFM “Key Binds” currently constructs
  Minecraft's `ControlsScreen`. SFM shortcut editing and Minecraft Controls are
  distinct destinations and need unambiguous ids, titles, and target tests.
- `sfm:review/open_bundle` belongs to the superseded managed-bundle review
  experiment. Its suggestion provider calls
  `SFMManagedReviewBundleRepository.listBundles()` during completion, which
  enumerates and fully parses every JSON bundle on the client thread. The
  action, Rust producer, managed-inbox model, dedicated workspace, fixtures,
  tests, puppets, and supporting documentation will be removed. The independent
  review-session/comment kernel and `review-comment-session-v1` fixture remain.
- The bundle incident is also a command-palette design failure: completion,
  ranking, titles, availability, and argument discovery run on input paths and
  must never perform filesystem, network, process, Git, or unbounded parsing
  work. Current source inspection finds the bundle provider as the only
  blocking `.suggests(...)` implementation; the registry-backed action ids,
  bounded GUI-scale values, and registered screen-type nodes are in-memory.
  A complete audit and a reusable guardrail are still required.
- Plain `Tab` belongs to the focused child. `Ctrl+Tab` and
  `Ctrl+Shift+Tab` traverse the multiplexer; `Ctrl+1` and `Ctrl+2` continue to
  select visible slots directly.
- Explorer Space and `Ctrl+Enter` both open a selected file through an
  explorer-owned preview stack. Space keeps explorer focus; `Ctrl+Enter`
  focuses the resulting preview. Neither operation may target a terminal or a
  panel owned by another workflow.
- `sfm:workspace/open_to_side sfm:test_screen` can misleadingly report “No
  available sub-actions”, then mutate the command as though a valid separator
  were accepted. Incomplete commands must show the missing argument and scene
  candidates without becoming executable or inserting phantom syntax.
- The size-display puppet panel must become an ordinary scene and expose its
  logical width, height, and effective scale through the same panel action
  surface used by production content.
- The Rust terminal has exactly two presentation states: disconnected shows a
  Start/Retry control and no Java REPL help; connected shows only the
  Rust-authoritative frame. `sfm:repl/open` remains an independent Java-local
  action.
- At GUI scale 7, Java currently appears to stretch a smaller Rust PNG. The
  eventual bridge contract must preserve the requested `columns × rows`, let
  Rust increase font/cell pixel dimensions, and present the resulting frame
  without Java bitmap upscaling. CPU `fontdue` is the correctness baseline.
- The current Rust-backed terminal also has a user-perceived multi-second lag.
  This is a release-risk observation until correlated input/output-to-present
  timing reproduces it. CPU `fontdue`, PNG encoding/transfer, Java decode,
  texture churn/upload, and Minecraft render scheduling remain hypotheses.
  The linked terminal plans compare Rust CPU/fontdue, Rust GPU/slug, Java
  vanilla-font cells, and Java Caskaydia cells visually and temporally.

## Terminal performance release gate [~]

This plan does not duplicate the implementation details in Teamy Terminal
Phase 3.6 or Vox bridge V-4. It coordinates their release consequence:

- **R-PERF.1:** The current default has a machine-readable, correlated witness
  for the same multi-second delay perceived by the user. If unattended puppets
  cannot reproduce it, typed capture actions produce the same manifest from a
  manual session.
- **R-PERF.2:** Matched evidence exists for Rust CPU/fontdue, Rust GPU/slug,
  Java vanilla-font semantic cells, and Java Caskaydia semantic cells, with
  renderer and transport varied independently. The report includes semantic
  correctness, native-size images/diffs, p50/p95/p99/max end-to-end latency,
  Rust/Java/GPU stages, bytes, allocations, texture uploads, and frame outcomes.
- **R-PERF.3:** The release scope records the selected default/fallback or an
  explicit terminal-feature deferral. A short GPU span, reduced transport
  bytes, or a visually appealing screenshot alone is not acceptance; the
  original workload must have no unexplained multi-second presentation delay.
- **R-PERF.4:** High-GUI-scale evidence records GUI-logical and physical panel
  bounds separately and proves scale-one framebuffer presentation for every
  Rust renderer/transport tuple. Raw frames larger than 1,000,000 bytes traverse
  the real generated Vox channel without applying a collection-entry limit,
  and bounded payload limits support the declared 4096x4096 RGBA8 maximum. A
  terminal-properties panel and typed auto/set/increase/decrease actions expose
  surface, font, cell-grid, scale, clamp, remainder, and last-error state so a
  visual result can be reconciled with Rust and Java telemetry.

**Progress notes — 2026-08-02:** The Rust CPU/full-PNG witness now reproduces a
2.853889-second snapshot with the dominant measured stage exposed as cold
`font_load_us=2278390`; SFM carries the same correlation vocabulary through
Java polling and has bounded decode/upload/presentation telemetry. The
canonical SFM compile and 17 focused terminal Java tests pass against Vox
`blake3:400629d4488f9544ff098e47c9478ba799f4171e`. R-PERF.1 remains partial
until a live Minecraft panel capture joins the Rust stage witness to Java
decode/texture/present measurements; no GPU/slug or Java-renderer choice is
being made from this preliminary data.

**Correction completion — 2026-08-05:** V-4.2c.2 carries the real
1,100,000-byte generated-channel regression and V-4.2d supplies the terminal-
owned properties/tuning surface. The canonical lock pins published Facet
`765f0851bef7ce764b44554b7d2db059af1e7759` /
`blake3:5f52a106cfd0a92f6917dace414f1c556423328d`, including the fix that retains
partially read frame state across Java's recoverable 25 ms socket timeouts.
SFM maps terminal GUI-logical viewport bounds to exact framebuffer pixels,
keeps the logical cell grid independent, and raises the bounded RGBA8 path to
64 MiB. Run `title_screen_rus-20260805-121733-142` completed all six tuples at
3840x2130/effective GUI scale 7 with zero compact-decode/receiver failures and
retains 24 screenshots plus 49 text artifacts. Its representative 1785x1901
physical target produces an 11,491,200-byte native full raster and reports
scale-one framebuffer presentation; the run observed payloads through
24,076,800 bytes. Typed invalid-request retention/recovery, automatic clamp and
letterbox telemetry, F3/Escape choices, focus return, alternate screen,
CPU->GPU->CPU, and independent split terminals all passed. R-PERF.4 is complete;
R-PERF.2/R-PERF.3 remain open for the deferred V-4.3 Java renderers and final
matrix/default decision. The normal-resolution companion
`title_screen_rus-20260805-123110-589` retained the same 24 screenshots and 49
text artifacts. Canonical SFM compile and tests passed (531/533, with only the
two expected Windows symlink-privilege assumptions aborted), alongside Teamy
Terminal's complete `check-all.ps1` gate and Facet's full Java packaging gate.

The renderer candidates and reporting tool can be developed concurrently in
the separate worktrees/tasks listed by the linked plans after their shared
correlation and semantic-cell contracts are reviewed. The final matrix,
release choice, packaging statement, and propagation remain sequential
integration gates.

## Action vocabulary decision — 2026-08-02

New action ids use `/` to express conceptual hierarchy. Underscores remain
valid inside one atomic resource name, but they do not flatten verbs, objects,
or directions. The canonical panel family is:

```text
sfm:panel/open <scene> [scene arguments]
sfm:panel/open/left <scene> [scene arguments]
sfm:panel/open/right <scene> [scene arguments]
sfm:panel/open/above <scene> [scene arguments]
sfm:panel/open/below <scene> [scene arguments]
sfm:panel/close
sfm:panel/move/left|right|above|below
sfm:panel/resize/left|right|above|below
sfm:panel/duplicate/left|right|above|below
sfm:panel/scale/set <n>
sfm:panel/scale/increase
sfm:panel/scale/decrease
sfm:panel/scale/clear
sfm:panel/diagnostics/open
sfm:panel/rotate/content/left|right
sfm:panel/rotate/scale/left|right
sfm:terminal/server/start [address]
sfm:terminal/server/connect [address]
sfm:terminal/properties/surface/auto|set|width/increase|width/decrease|height/increase|height/decrease
sfm:terminal/properties/font/auto|set|increase|decrease
sfm:terminal/properties/cells/auto|set|columns/increase|columns/decrease|rows/increase|rows/decrease
sfm:screen/close
sfm:palette/close
sfm:palette/history/open [editor-id]
sfm:palette/history/open/left|right|above|below [editor-id]
sfm:palette/history/clear
```

Examples include `sfm:panel/open sfm:terminal` and
`sfm:panel/open sfm:text_editor [editor-id]`,
`sfm:panel/open sfm:grammar`, and
`sfm:panel/open/right sfm:size_display`. Because these are unpublished
prospective actions, `sfm:workspace/open_to_side` should be replaced and its
puppets/keybindings migrated rather than retained indefinitely as an alias.
`sfm:panel/open sfm:terminal` is the sole terminal-opening action;
`sfm:terminal/open` is retired. The terminal-specific actions only manage the
Rust server/connection lifecycle and do not open a panel. `sfm:repl/open`
remains the independent Java-local REPL entry point.

F3 and Escape use constrained instances of the ordinary command palette rather
than a bespoke chooser or bespoke mutations. F3 offers deduplicated applicable
placements of `sfm:size_display` and contextual `sfm:terminal_properties`
choices. An unhandled Escape offers panel close, screen close, and cancel;
terminal Escape remains PTY input for the first two presses and opens that
surface on the third. The palette is seeded with
`sfm choose <choice-session-id> ` and its session-scoped Brigadier subtree
contains only those choices, so availability, fuzzy discovery, selection,
scrolling, metadata, focus, and execution all use the familiar palette path.

## Completed goal — Release cleanup and completion safety P-1 (2026-08-02)

| Id | Work | Completion evidence |
| --- | --- | --- |
| P-1.1 | Separate SFM “Key Binds” from Minecraft “Controls”. The Key Binds action opens `SFMKeyBindingScreen`; a separately named Minecraft Controls action may continue to open `ControlsScreen`. | Complete. `SFMKeybindingNavigationActionTests` passes and `title_screen_dynamic_key_bindings` captured both destinations under `title_screen_dyn-20260802-140946-423`. |
| P-1.2 | Delete the superseded bundle-review subsystem: `review/open_bundle`, its registration, Java `review/repository` and `screen/review/repository` packages, Rust `review prepare` and `repository_review_bundle_v1`, bundle-only architecture document/fixture, focused tests, and repository-review puppets/helpers. Preserve `review/session`, `screen/review/comment`, and `review-comment-session-v1`. Do not delete users' existing AppData files. | Complete. Source audit found no retired references; Rust tests pass `362/362` with one intentional ignore; `SFMReviewSessionV1Tests` and `SFMReviewCommentKernelDataSourceTests` pass. Existing user AppData was not touched. |
| P-1.3 | Audit every completion producer and candidate-construction path, including `.suggests(...)`, `configureCommandNode`, `SFMClientScreenType.createCommandNode`, action-id ranking, titles, and availability. Move any I/O or corpus work behind explicit asynchronous loading and immutable bounded snapshots. Add a regression seam proving completion does not invoke a supplied blocking loader, plus diagnostics for unexpectedly slow providers without using a flaky wall-clock assertion as the primary proof. | Complete. The audit table below records the only live providers; immutable-map/catalog regression tests and `SFMClientActionPaletteSuggestionTests` pass. Palette metadata is extracted without triggering Minecraft language loading in headless tests; slow completion is diagnostic-only. |
| P-1.4 | Introduce the hierarchical `sfm:panel/open...` command family and fix incomplete-argument UX. A parent requiring a scene/argument is non-executable, lists available scene candidates, identifies the missing argument, and never reports “No available sub-actions” or inserts a separator when candidates exist. Register `sfm:size_display` and `sfm:terminal`; remove `workspace/open_to_side` and `terminal/open` after command/keybinding/puppet migration. | Complete. `OpenPanelActionTests`, `SFMClientCommandInsertionTests`, and `SFMClientActionPaletteSuggestionTests` pass; retired IDs are absent; `title_screen_workspace` captured directional open/close/reopen under `title_screen_wor-20260802-141102-221`. |
| P-1.5 | Give the Rust terminal explicit disconnected/connected presentation. Disconnected shows status and Start/Retry only; connected shows the Rust PNG only; Java REPL instructions never leak into the Rust scene. Rename lifecycle commands to `terminal/server/start` and `terminal/server/connect`; neither command opens a panel. | Complete. `SFMVoxTerminalServiceTests` and `SFMUnavailableTerminalServiceTests` pass; `title_screen_rust_terminal` captured disconnected/lifecycle/guidance/input/alternate-screen/reconnect states under `title_screen_rus-20260802-141350-220`, with 16 machine-readable terminal artifacts and required/forbidden assertions. |

P-1.5's phrase “connected shows the Rust PNG only” predates panel-local
renderer/transport selection. The current boundary is stricter and more
precise: disconnected is a landing scene whose widget tree contains only
Start/Retry plus retained status history; connected owns the viewport,
Presentation selector, and its option rows. The 2026-08-06 K-2 correction in
the contextual-input plan replaces cross-scene visibility flags with disjoint
child trees and removes transient `isConnecting()` text from rendering.
The panel consumes one lock-consistent lifecycle snapshot per UI update and
does not enter the connected widget scene until Vox has accepted a complete
presentation frame; assigning a provisional session id cannot flash the
viewport or selector.
If an already-connected raster presentation fails, the landing scene exposes
an explicit retry that resets the remote terminal transport; it does not start
or duplicate the Rust server process. Live run
`title_screen_rus-20260806-190626-836` also proves the Start/Retry control uses
the exact Vanilla 20-pixel atlas row with no detached texture sliver.

### P-1 completion audit notes

The first implementation pass now has the following structural evidence:

- Completion candidates are built from the immutable compiled action map. Search
  labels are cached at command-tree construction, while availability remains a
  contextual check; no completion provider performs repository, filesystem, or
  network I/O.
- The only live suggestion providers are the immutable action-id provider,
  bounded GUI-scale integers, and registered panel-scene providers. The former
  managed-bundle provider was deleted. Completion duration is logged when it
  exceeds the diagnostic threshold, and a panel-scene regression test proves
  its catalog is not reloaded during completion.
- The retired bundle source/fixture/test/puppet references are absent from
  Java/Rust source. The Rust CLI suite is the first independent verification
  gate for that deletion.
- The key-bindings puppet now captures Minecraft Controls and SFM Key Binds as
  distinct destinations. The Rust-terminal puppet now captures the
  pre-server disconnected state, then exercises lifecycle start, PNG input,
  alternate-screen restoration, restart, cancellation, and machine-readable
  terminal-content artifacts with required/forbidden text assertions.

#### Completion-provider audit

| Candidate path | Current implementation | Blocking work allowed during completion | Evidence/guardrail |
| --- | --- | --- | --- |
| `SFMClientActionDispatcherCompiler` action-id suggestions | Iterates the immutable compiled action map | None; no filesystem, network, process, Git, or parsing work | Source inspection plus the immutable-provider comment |
| `SFMClientActionCommandTree` palette ranking | Uses action id/path/title/description cached when the command tree is constructed | Contextual availability checks only; no label recomputation | `SFMClientActionPaletteSuggestionTests` and cached `ActionSearchMetadata` |
| `SFMGuiScaleAction` numeric arguments | Offers the bounded integer range `0..maxGuiScale` | Bounded in-memory range construction only | `SFMGuiScaleActionTests` verifies the complete numeric domain |
| `OpenPanelAction` scene arguments | Materializes registered scene types once while compiling the command tree | Registered command-node construction only; no reload during completion | `OpenPanelActionTests.completionDoesNotReinvokeACompletedSceneCatalog` |
| Action titles, descriptions, and availability | Titles/descriptions are cached for ranking; availability resolves against the current action context | No I/O or corpus enumeration | `SFMClientActionCommandTree` slow-completion diagnostic logs providers exceeding 100 ms |

The audit found no remaining completion producer that performs synchronous
repository, filesystem, network, process, Git, or unbounded parsing work. The
slow-completion log is diagnostic evidence rather than a timing-based test
assertion; structural tests remain the primary regression guard.

The incomplete-argument guard distinguishes literal child choices from
required argument nodes. This prevents stale asynchronous suggestions from
blocking manually entered values while still requiring a panel scene literal
to be selected before scene-specific arguments are entered.

The completed goal was:

> Complete P-1.1 through P-1.5 in `docs/tasks/release checkpoint and slim artifact plan.md` and the linked action, panel, comment, snapshot, and terminal plans on canonical 1.19.2, with focused tests and live puppet evidence; do not begin P-2 panel-stack implementation, P-3 review explorers, or GPU/slug rendering.

The goal is complete. The authoritative verification boundary passed on
2026-08-02:

- Canonical `run compile --branch 1.19.2 --no-refresh` passed. The CLI emitted
  one known non-portable-artifact warning; portability was not made a hard
  failure for this checkpoint.
- Focused filters passed: `SFMKeybindingNavigationActionTests`,
  `OpenPanelActionTests`, `SFMClientCommandInsertionTests`,
  `SFMClientActionPaletteSuggestionTests`, `SFMVoxTerminalServiceTests`,
  `SFMUnavailableTerminalServiceTests`, `SFMReviewSessionV1Tests`, and
  `SFMReviewCommentKernelDataSourceTests`.
- Live puppets passed with exit 0: `title_screen_dynamic_key_bindings`,
  `title_screen_workspace`, and `title_screen_rust_terminal`, each run
  through the canonical CLI with `--wait-for-build-lock` and `--log-file`.
- `git diff --check` passed; the Rust CLI suite and source audit also passed.

P-2 panel-stack semantics, P-3 review explorers, clean-install packaging, and
GPU/slug rendering remain planned work and were not started by this goal.

## Panel state and navigation batch P-2

### P-2.1 Slot, stack, entry, and ownership model

A layout slot owns geometry and an ordered stack of panel entries. A panel
entry owns stable identity, scene/content state, optional GUI-scale override,
and provenance such as `explorer-preview(owner=<explorer-id>)`. The focused
slot, visible entry, and child focus are distinct. Pushing an entry makes it
visible but does not necessarily move slot focus.

### P-2.2 Hierarchical panel operations

Implement the action family recorded above. `panel/open` pushes into the
focused slot; directional open creates a neighboring slot; `panel/close`
closes the visible entry and collapses an empty slot. Directional move removes
the visible entry from its source stack and pushes that same entry, preserving
its identity and complete view/content state, onto the neighboring destination
stack; it creates that neighboring slot when none exists and collapses the
source slot when it becomes empty. It never clones a panel or aliases a second
copy of its state. Action help, completion, keybindings, and puppet commands
all use the same registered operation metadata.

### P-2.3 Deterministic keyboard traversal

Plain `Tab` is forwarded to the child. `Ctrl+number` focuses the numbered
visible slot without changing its visible entry. `Ctrl+Tab` traverses every
entry in visible slot order, making a visited stacked entry visible;
`Ctrl+Shift+Tab` is the exact inverse. The required forward witness is:

```text
[>1, [2,3], 4]
[1, >[2,3], 4]
[1, >[3,2], 4]
[1, [3,2], >4]
```

### P-2.4 Explorer-owned preview routing

For a selected file, Space and `Ctrl+Enter` locate the most recently created
preview slot owned by that explorer; if none exists, create one to the right.
Each open pushes a typed preview entry into that slot. Space leaves explorer
focus unchanged; `Ctrl+Enter` focuses the newly pushed preview entry. Existing
entries remain in the stack for deterministic `Ctrl+Tab` traversal; terminal
and unrelated panels remain ineligible targets. A selected directory uses
Space to expand/collapse rather than producing a file preview.

### P-2.5 Per-entry scale and independent rotations

Scale is part of panel-entry view configuration, not the global Minecraft GUI
setting. `scale 2` must expose more logical width/height than `scale 4` in
equal-sized slots, as shown by two size-display scenes. Content rotation moves
content assignments among only the currently visible entries while preserving
slot geometry and scale assignments; scale rotation moves scale assignments
among only the currently visible entries while preserving geometry and
content. Hidden entries remain unchanged in their existing stacks. Binding
both actions to one key composes the two transformations.

The nullable override is a state, not numeric zero. Auto/inherited resolves to
the current effective global GUI scale N and occupies the ordered position
immediately above explicit N: increase from auto selects N+1; decrease from
auto selects explicit N; subsequent operations adjust the explicit integer.
This avoids both the old auto-to-1 jump and an unexpected visual jump on the
first decrement. Bounds remain 1 through the runtime maximum.

### P-2.6 Stack and scale affordances

When a slot contains multiple entries, show compact numbered boxes in its
bottom-right corner and identify the visible entry. Scale mutations use a
transient workspace toast rather than a persistent panel label; clearing to
auto reports `gui scale auto (N)` with the current effective inherited value.
At a numeric boundary, the current value is shown again with a short shake.
Puppets and headless observations must expose slot order, stack order, visible
entry, focus, dimensions, scale, and toast state so the behavior can be
asserted without relying only on screenshots.

### P-2 implementation evidence

The current P-2 goal has implemented the typed layout ownership model,
identity-preserving directional movement, close/collapse behavior, deterministic
`Ctrl+number` and `Ctrl+Tab` traversal, per-entry scale and independent
content/scale rotations, and numbered stack plus scale affordances. The
multiplexer exposes both physical panel bounds and logical content bounds after
entry scaling.

Pure coverage passes for `SFMWorkspaceLayoutTests` and
`SFMFileExplorerWorkspaceTests`. The live `title_screen_workspace` puppet proves
stack traversal, reverse traversal, scale mutation, physical movement with
identity/focus/scale preservation, visible-slot mouse focus, close, and
reopen. The live `title_screen_integrated_file_explorer` puppet proves Space
opens one explorer-owned preview without moving focus and `Ctrl+Enter` pushes a
new preview entry into that slot while focusing it. These witnesses emit screenshots and
headless assertions; the only remaining CLI output is the known non-portable
artifact warning, which is not a test failure.

## Review explorer composition batch P-3

### [x] P-3.1 Multi-lane revision selectors

`sfm:explorer/changes <before-selector> <after-selector>` resolves two
independent selectors across every maintained Minecraft-version worktree known
to the SFM toolchain, ordered oldest to newest. An optional lane filter can
narrow that default set without changing selector semantics. `mod 4.34.0`
means the `4.34.0-<minecraft-version>` tag for each lane; `git head` means that
lane's current HEAD. The projection is
`file → lane/branch → before|after`; two lanes therefore expose four leaves for
every file node. If a file does not exist on one side, that side remains as an
explicit missing/tombstone leaf rather than disappearing. Resolution
diagnostics remain visible per lane. If either selector cannot resolve for one
lane, retain that lane with a diagnostic placeholder; do not silently omit it
or fail otherwise resolvable lanes.

The composed invocation is:

```text
sfm:panel/open sfm:explorer/changes "mod 4.34.0" "git head"
```

### [x] P-3.2 Comment and hashtag explorers

Preserve the existing `Comment{text, selection_rule}` authority: one selector
may evaluate to regions in multiple files, sides, and lanes. Provide
`sfm:explorer/comments` as `comment → file → region` and
`sfm:explorer/comments/hashtags` as `hashtag → file → region`. Hashtags remain
derived only from comment text using the existing kernel grammar; region leaves
retain comment identity, provenance, resolution status, and document revision.

### [x] P-3.3 Comment-driven before/after presentation

Opening a before/after leaf presents that one immutable source revision with
all applicable comment styles. Diff producers emit comments such as `#removed`
for before-side regions and `#added` for after-side regions; their style rules
produce familiar red/green review coloring. The primary experience is not a
fixed two-file diff panel: users compose before and after leaves through panel
slots and stacks. A tombstone leaf explains that the revision is absent and
retains the file/lane/side identity needed to present additions and deletions
coherently.

### [x] P-3.4 Review-surface migration

Build review from the shared explorer, panel, preview, comment, and selector
substrates. Retire `developer/open_source_review` and
`developer/open_comment_review` and delete their fixture-only workspace shells
after their still-useful comment/session and visual behaviors have migrated and
equivalent panel/explorer puppets pass. Preserve the review-session kernel,
stores, selectors, styles, persistence, and reusable comment editing
components. Do not recreate the deleted managed-bundle inbox as the transport
for the new explorer.

### [x] P-3.5 Verifiable multi-version review story

A live puppet opens a two-lane change explorer, proves four before/after leaves
including added/deleted tombstones, opens leaves with Space and `Ctrl+Enter`,
preserves a terminal panel, rotates a stack, displays derived
`#removed`/`#added` styles, and shows comment and hashtag projections whose
selectors span more than one file.

## Text editor v3 and obsolete developer-surface batch P-4

### [x] P-4.1 Retire the SFM Dev title-screen surface

Remove the IDE-only `SFM Dev` button from the vanilla title screen and delete
the chooser screen it opens. The command palette remains the discovery and
execution surface for developer workflows. Preserve useful developer actions
such as creating a developer world and running tests, but migrate screen
opening to canonical panel scenes or direct palette actions rather than
recreating the chooser. Remove obsolete chooser-only registrations, tests, and
puppet references after equivalent palette coverage exists.

### [x] P-4.2 Replace Draw's G4 button with a panel scene

Remove the Draw screen's fixed `SFML`/G4 button, tooltip, drag-to-insert
behavior, and reachable embedded grammar preview state. Register the grammar
document as an explicit panel scene, with the canonical invocation:

```text
sfm:panel/open sfm:grammar
```

The grammar scene opens the bundled `SFML.g4` content through the text-editor
panel contract as a read-only document. Its content is not inserted into the
edited program implicitly; grammar insertion, if retained later, is a
separate explicit command with a deliberate placement argument.

### [x] P-4.3 Rename the canvas editor to Text Editor v3

Rename the player-facing and registry identity from Draw to Text Editor v3.
The existing canvas/layer implementation becomes the v3 editor rather than a
separate Draw product. Update class/registration names, editor ids, titles,
configuration labels, keybinding text, command-palette metadata, tests,
puppets, and fixtures consistently. Do not leave `sfm:draw` as the preferred
or silently discoverable editor id after migration; preserve only an explicit
compatibility alias if a separate release decision requires one.

### [x] P-4.4 Widgetize the text-editor contract

Split editor behavior from full-screen ownership. An editor registration must
be able to create a panel/widget with the shared text-edit contract, lifecycle
context, bounds, focus state, child input routing, save/close operations, and
dirty-state confirmation. A thin full-screen adapter may continue to host the
same component for existing keybindings and legacy callers, but it must not
duplicate editing logic.

Extend the typed `sfm:panel/open` scene registry with:

```text
sfm:panel/open sfm:text_editor [editor-id]
```

The `editor-id` argument is optional. Without it, use the configured default
editor; with it, resolve an in-memory suggestion from the text-editor registry
and open that implementation. The argument identifies the editor
implementation, not the document. Document/source context remains a separate
typed open context so grammar, review leaves, and ordinary files can reuse the
same widget.

### [x] P-4.5 Acceptance and migration proof

Add pure contract tests for panel lifecycle, focus/input routing, dirty close,
save/close, default-editor selection, and explicit editor-id selection. Add
palette and live puppet evidence proving that `sfm:text_editor` opens the
default editor, an explicit v3 editor opens in a panel, `sfm:grammar` opens a
read-only grammar document, the old SFM Dev button is absent, and the removed
G4 button cannot be reached through the v3 screen. Review explorers must open
their immutable before/after leaves through this same panelized document path.

### Combined goal batch P-3/P-4 — review explorers and Text Editor v3 [ready]

This combined batch is the next implementation boundary. P-4 establishes the
panelized Text Editor v3 and retires the obsolete developer/Draw entry points;
P-3 then uses that shared document surface for multi-lane review explorers.
The batch includes P-3.1 through P-3.5 and P-4.1 through P-4.5. It excludes
release packaging, cross-version propagation, GPU/slug rendering, upstream
Facet/Vox Java work, and publication.

**Goal text:** Complete P-3.1 through P-3.5 and P-4.1 through P-4.5 in this
plan on canonical 1.19.2, with focused pure tests, palette coverage, and live
puppet evidence.

### P-3/P-4 completion evidence — 2026-08-02

- Added the shared `SFMReviewExplorerModel` projections for changes,
  comments, and hashtags. Changes retain stable file/lane/before/after shape
  and explicit missing tombstones; source leaves open through the panelized
  read-only Text Editor v3 path.
- Removed the title-screen SFM Dev button/chooser and the obsolete source,
  comment-review, and managed-bundle action/screen surfaces. The review
  session/comment kernel remains the authority for comment projections.
- Added `sfm:panel/open sfm:grammar` and
  `sfm:panel/open sfm:text_editor [editor-id]`; the grammar scene is
  read-only and the optional editor id resolves through the text-editor
  registry. The former canvas editor is now registered and labelled as Text
  Editor v3, with a reusable panel adapter and full-screen compatibility host.
  The old private canvas helper block remains only as an internal compatibility
  base; its SFML button, insertion path, and embedded preview are no longer
  reachable from the v3 surface.
- Focused tests cover explorer tree shape/navigation, tombstones, action
  selector arity, and panel context validation/identity. The canonical
  compile completed successfully through
  `sfm-propagate-changes.exe run compile --branch 1.19.2`.
- The final full suite completed with `430 found, 428 passed, 0 failed, 0
  skipped, 2 aborted`; both aborts are the established Windows symlink
  privilege assumptions in the file-explorer portability tests.
- The live `title_screen_review_explorer` puppet passed and produced the
  changes/tombstone, stacked immutable source, comments, and hashtags captures
  under:
  `platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/title_screen_rev-20260802-171629-032`.
- The live `title_screen_text_editor_panel` puppet passed and produced the
  grammar, configured-default, and explicit v3 panel captures under:
  `platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/title_screen_tex-20260802-174031-177`.
  Both live runs emitted only the known non-portable artifact warning.

## Command-surface reliability and history batch P-5

### [x] P-5.0 Replace the duplicate chooser and state-bind terminal controls

**Work — one command-palette choice surface:** Remove
`SFMActionChoiceScreen` as an independently rendered and navigated button list.
Retain `SFMActionChoice` as immutable choice data and introduce a bounded
client-side choice-session service. Opening F3 or the unhandled-Escape flow
snapshots the currently available, deduplicated choices and the exact
`SFMClientActionContext`/workspace target that produced them, assigns a
non-reused process-local session id, and opens the ordinary
`SFMCommandPaletteScreen` with this prefix:

```text
sfm choose <choice-session-id>
```

The fixed `sfm choose` route resolves the session, while that palette instance
uses a session-scoped Brigadier subtree whose only executable descendants are
the exact canonical choice command paths. For example, an Escape session may
offer `sfm choose 1 sfm:panel/close`; a diagnostic session may offer
`sfm choose 2 sfm:panel/open/right sfm:terminal_properties`. Do not mutate the
global action registry with per-session literals, accept an arbitrary greedy
remainder, or generate all ordinary action suggestions and filter them after
ranking. Invalid, incomplete, unavailable, unknown-session, and stale-session
paths remain non-executable with explicit feedback.

The constrained palette uses the ordinary palette's query editor, fuzzy
ranking, selected-suggestion model, action title/description/icon/binding
metadata, feedback console, mouse handling, and keyboard behavior. There is no
choice-button focus state. Arrow movement, pointer selection, Tab acceptance,
the displayed selected command and Enter execution must all resolve through
the same `selectedSuggestion`. P-5.1 completes the shared suggestion
viewport/scrollbar rather than adding constrained-palette scrolling here.

A session is bounded and single-use after successful execution or cancellation.
Closing/replacing its palette or disposing its origin invalidates it; a failed
action may leave the still-current session open with feedback. Escape while a
constrained palette is open cancels that session directly and must not open a
nested Escape session. Execution uses the captured context/target even if
ambient focus changes asynchronously. A later session cannot reuse an older
id or resolve an older target. P-5.3 never stores `sfm choose <id> ...`; after a
successful non-maintenance choice it may record the canonical underlying
`sfm action invoke ...` command under the same policy as direct palette
execution. Cancel and palette-close choices are not history.

**Work — disconnected control lifetime:** Replace the terminal's retained
manual Start/Retry coordinate sentinel with an explicit interaction state.
The Start/Retry target is active only while the disconnected presentation that
drew it is current and only for the intended primary-button activation. Before
presenting a retained or new terminal frame, invalidate disconnected controls;
terminal viewport routing must then consume the click. Never infer control
activity merely because old rectangle coordinates remain populated.

**Validation:** Add session-tree tests for exact candidate containment,
deduplication, fuzzy discovery within the constrained tree, incompleteness,
unknown/stale ids, disposal, single use, failure retention, captured targets,
cancel-without-recursion, and canonical-versus-ephemeral history. Exercise F3
and Escape through the real `SFMCommandPaletteScreen`, proving that arrow,
pointer, Tab and Enter agree on one suggestion and that no
`SFMActionChoiceScreen` path remains. Render a disconnected terminal, retain
the former button coordinates, transition to a presented frame, click there,
and assert one terminal mouse event, terminal focus, and zero server-start
attempts.

**Completion criteria:** F3 and Escape are constrained command-palette
sessions with no duplicate chooser selection mechanism; only valid session
commands parse or execute; stale sessions cannot retarget work or pollute
history; and connected terminal pixels cannot activate disconnected controls.

**Completion notes — 2026-08-05:** Commits `f678d91a8` and `425a68c9b`
state-bind the disconnected Start/Retry hit target, remove the independent
`SFMActionChoiceScreen`, and route F3 and otherwise-unhandled Escape through
bounded, process-local `sfm choose <id> ...` sessions in the ordinary command
palette. Each session owns an exact literal Brigadier subtree, immutable
choice data, and a captured action context including the originating panel id;
successful execution is single-use, cancellation/removal invalidates the
session, stale ids do not parse, and failed actions may retain the current
session for feedback. The ordinary action registry and future canonical
history surface never receive an ephemeral id.

Focused `SFMChoiceSessionTests` cover exact containment, deduplication,
incomplete and unknown paths, single use, failure retention, disposal, fuzzy
full-row discovery, and captured-panel routing after focus changes. The live
`title_screen_rust_terminal` run
`title_screen_rus-20260805-192956-719` exercises the constrained triple-Escape
surface and proves that clicking the former disconnected-button rectangle
after frame presentation produces terminal input without another start
attempt. The single-variant presentation run
`title_screen_rus-20260805-193851-950` exercises the F3 constrained palette
through Down/Up/Tab/Enter and pointer/cancel paths before completing its full
CPU/GPU presentation matrix.

### [x] P-5.1 Share bounded list scrolling and correct hit testing

**Work:** Extract a pure reusable vertical-list viewport model covering item
count, visible-row count, first visible row, selected-row visibility, wheel and
page movement, half-open row hit testing, track clicks, thumb dragging, and
scrollbar geometry. Use it for command-palette suggestions and
`SFMKeyBindingScreen`; reuse the established `SFMConsoleWidget` interaction
laws and `SFMScreenPanelBounds.contains` bounds convention without coupling
ranked suggestions to `PickList` sorting.

The normal `sfm action invoke ` surface and every P-5.0 constrained
`sfm choose <choice-session-id> ` surface use the same palette suggestion
viewport object. Search, row selection, first-visible-row state, wheel, track,
thumb, PageUp/PageDown, Home/End, resize, and filter clamping must therefore
behave identically; no choice-specific scrolling or focus implementation is
permitted.

Remove `SFMKeyBindingScreen.refresh()`'s `.limit(...)`; retain the complete
filtered action list and scroll through it. Dispatch to the search `EditBox`
before row activation and require `rowTop <= mouseY < rowBottom`, fixing the
current `(mouseY - 64) / 24` truncation that maps a small negative value to row
zero. Give both lists visible scrollbars, wheel, PageUp/PageDown, Home/End, and
clamped resize/filter behavior. In the palette, the feedback console consumes
wheel/drag only inside its own bounds; the suggestion viewport consumes only
inside its rows/scrollbar. Manual suggestion scrolling may clamp the selected
row into the new viewport and must not be undone by per-frame layout.

**Validation:** Add pure viewport and scrollbar-geometry tests, shortcut-screen
search/blank-space/row hit tests, and palette tests for wheel, thumb, keyboard,
filter shrink, resize, empty list, and independent console routing.

**Completion criteria:** Every filtered shortcut and palette suggestion is
reachable without keyboard-only traversal; both thumb and wheel work; clicking
the search box or any gap cannot open an action; and neither scroll region
steals input from the other.

**Completion notes — 2026-08-05:** Commits `03b572a9b` and `425a68c9b` add one
`SFMVerticalListViewport` model and use it for the complete, untruncated SFM
shortcut list and for both ordinary and constrained command-palette
suggestions. The model owns first-row clamping, selected-row visibility,
half-open hit testing, wheel/page/home/end movement, track paging, thumb drag,
and scrollbar geometry. Palette routing uses explicit suggestion and console
regions, so neither viewport steals wheel or drag input from the other; search
widgets are dispatched before rows and blank space cannot activate row zero.

Pure viewport, command-palette region, shortcut hit-routing, and screen tests
pass. The live `title_screen_command_palette` run
`title_screen_com-20260805-191954-807` drives actual wheel, PageDown, End, Home,
track-click, and thumb-drag events and captures `command-palette-scrolled` with
a visible nonzero scrollbar offset. The canonical compile and full test suite
both exit zero; the only two aborted tests are the existing Windows
symlink-privilege assumptions.

### [x] P-5.2 Complete contextual panel bindings and keyboard navigation

**Work:** Complete K-1 through K-7 of
`docs/tasks/contextual input actions and addressable explorer plan.md`. Replace
the current global-only binding model with registered stable keyboard-usage
situations and SFM-owned contextual defaults. Preserve schema-1 user bindings
through schema-2 migration, explicit global scope, and built-in
override/tombstone state.

The default set includes canonical Ctrl+Equal/minus/zero focused-panel scale
actions, Ctrl+Shift+W panel close, F3 focused-panel diagnostics, all four
Alt+Shift+arrow resize actions, and Microsoft Terminal's Alt+Shift+minus/right-
plus duplicate-below/right defaults. Panel scale ships one main-row default per
action; keypad alternatives remain user-bindable rather than appearing as
duplicate defaults. Do not seed Ctrl+Shift+T or browser-style Ctrl+W without a
separately approved SFM semantic action/situation.

At the `sfm action invoke <query>` slot, fuzzy ranking also inspects bounded
literal-only descendants of each available action node. A match on a later
literal contributes the complete continuation from action id through that
literal, so `term` exposes every grammar-valid panel-open terminal and terminal-
properties path. The walk has a fixed depth/candidate limit, follows no dynamic
argument values, invokes no suggestion providers, and leaves Brigadier
authoritative for parsing/execution. Metadata for a full-path suggestion comes
from its first action-id token.

This item also requires the shared panel child-widget host and migration of the
Rust terminal Start/Retry, Presentation, terminal viewport, and all terminal-
properties controls. Buttons, keyboard activation, palette commands, and
bindings converge on registered semantic actions. F3 is no longer hidden in
the multiplexer and appears in SFM Actions & Shortcuts. The linked plan's D-1
through D-3 gates govern duplication, situation precedence, and semantic-versus-
parameterized input; do not improvise those contracts in UI code.

**Validation:** Use the exact focused tests and live K-7 matrix in the linked
plan. At minimum, prove binding migration/default/tombstone behavior,
context/scope conflict and terminal non-leak, keyboard-only terminal/properties
navigation, F3 discovery, close/scale/resize/duplicate actions, action-element
audit, nested full-path palette discovery/execution, and a canonical compile/full test run through
`sfm-propagate-changes.exe`.

**Completion criteria:** K-1 through K-7 are complete with local evidence;
every shipped default is contextual and overridable; the terminal and
properties controls are Minecraft-like keyboard-navigable action elements; and
panel close, scale, resize, duplicate, and diagnostics are discoverable
semantic actions rather than hard-coded key mutations.

**Incremental correction — 2026-08-06:** I-PAL-1 now walks at most eight
literal descendant levels and returns at most 256 ranked continuation paths
without entering argument nodes or invoking their providers. Tests prove the
four representative center/left terminal and terminal-properties paths,
whole-command application/execution, and unavailable-tree exclusion. Full-path
rows recover action metadata from their first token.

I-KEY-1/KBIND-5 now ship only Ctrl+Equal, Ctrl+Minus, and Ctrl+0 for panel
scale. One physical-token model supplies `Ctrl =` text and reusable pink
palette/details keycaps; exact Equal+Control matching and absence of default
keypad duplicates are tested. Focused action-palette, keybinding, and palette-
viewport runs pass. The canonical full `Tests` run reports `650 found, 648
passed, 0 failed, 2 aborted`; both aborts are the established Windows symlink-
privilege assumptions. At that point P-5.2 remained open for K-7; the final
K-7/P-5.2 closure is recorded below, while P-5.5 still owns the broader
release join gate.

I-KEY-2 subsequently corrected relative scale actions to resolve auto through
the current effective window GUI scale. Pure tests retain the asymmetric first
step (`auto N -> N+1` on increase, `auto N -> explicit N` on decrease) and
ordinary numeric continuation thereafter. Focused `PanelScaleActionTests` and
`PanelActionTests` pass; the subsequent canonical full run reports `653 found,
651 passed, 0 failed, 2 aborted`, with only the established Windows symlink-
privilege assumptions aborted. At that point K-7/P-5.5 still owned the live
witness; the K-7 witness is recorded below.

**Initial K-5 progress — 2026-08-07:** The first semantic workspace-navigation family
is now registered (`panel/focus/next`, `panel/focus/previous`,
`panel/focus/index <1..9>`, and `panel/maximize/toggle`), and the multiplexer
routes Ctrl+Tab, Ctrl+Shift+Tab, Ctrl+1..9, and Ctrl+M through the action
executor with workspace-situation defaults. At that point K-5 remained open
for the broader raw-handler inventory, action-element audit, Manager Edit
proof, and live witness. The pure action-grammar test passes `2 found, 2 passed`, the affected
keybinding slice passes `38 found, 38 passed`, and the current main/test source
sets compile. A separate explicit compile refresh encountered a cached Forge
renaming-tool replacement failure; that cache issue is recorded as tooling
follow-up, not as a product test failure.

**K-5/K-6 completion — 2026-08-07:** K-5 now has the bounded raw-handler
disposition inventory, deterministic action-element inventory/audit, public
coordinate-draft rejection, and the contextual `sfm:manager/edit` proof. K-6
now has sortable/scoped binding management plus focusable composable capture:
physical key/modifier keycaps use the shared pink token contract, can be
removed by mouse or keyboard, and support explicit Save/Cancel and
triple-Escape cancellation. `SFMActionElementTests`,
`SFMKeyboardNavigationAuditTests`, `SFMKeyBindingListModelTests`, and
`SFMKeySequenceCaptureTests` pass through the SFM toolchain. K-5 and K-6 are
complete; at that point the remaining gate was K-7's live proof and the broader
P-5.5 release evidence.

**K-7/P-5.2 completion — 2026-08-08:** The linked contextual-input plan's
live vertical slice is complete. The final compile passed and the canonical
suite reports `669 found, 667 passed, 0 failed, 0 skipped, 2 aborted`; the two
aborts are only the Windows symlink-privilege assumptions. The final dynamic,
workspace, and Rust-terminal manifests are
`title_screen_dyn-20260808-103044-061`,
`title_screen_wor-20260808-103446-639`, and
`title_screen_rus-20260808-104057-247`. They provide respectively 12, 8, and
33 user-visible captures, with terminal text/selection/paste/properties and
push-telemetry artifacts in the terminal run. Puppet command-palette actions
now leave entered text visible for a 500 ms observation window before real
submission. The semantic Presentation selector toggle and corrected
post-header keybinding viewport geometry are included in this closure.
P-5.2 is complete; P-5.5 remains the broader release join gate.

### [ ] P-5.3 Persist successful palette commands and rank full MRU entries

**Work:** Add a bounded command-palette history service that records the exact
normalized command only after successful user-initiated palette execution.
Keep repeated executions in the bounded history document, but deduplicate by
exact command when producing MRU suggestions so the newest occurrence wins.
Use a versioned file under the instance `config` directory, a 200-entry and
16-KiB-per-command bound, atomic replacement, and background persistence; no
file read/write or corpus parsing may occur in completion/ranking callbacks.
Automation uses an injected temporary/in-memory store unless a puppet
explicitly tests persistence.

At the blank `sfm action invoke ` action slot, offer the exact newest complete,
currently available historical command—including arguments—as suggestion zero,
then older unique history, then ordinary available action ids. A typed query
keeps textual fuzzy relevance authoritative and uses recency only as a bounded
boost/tie-break; history must not make an unrelated command look like a good
match. Parse the first action-id token for icons, titles, bindings, and `[?]`
instead of treating an entire argument-bearing suggestion as a
`ResourceLocation`. Failed, incomplete, unavailable, and cancelled commands
are not recorded.

**Validation:** Test success/failure boundaries, duplicates, bounds, corrupt
file recovery, atomic snapshots, async completion safety, availability,
argument-bearing suggestions, blank-query exact MRU order, typed fuzzy order,
and action metadata on historical rows.

**Completion criteria:** Reopening the default palette places the exact last
successful palette command first without synchronous I/O or loss of existing
fuzzy/Brigadier correctness.

### [ ] P-5.4 Expose read-only history open and true clear actions

**Work:** Register the hierarchical family:

```text
sfm:palette/history/open [editor-id]
sfm:palette/history/open/left [editor-id]
sfm:palette/history/open/right [editor-id]
sfm:palette/history/open/above [editor-id]
sfm:palette/history/open/below [editor-id]
sfm:palette/history/clear
```

The base open action is the requested center/focused placement. Omitted editor
id uses the configured default; an explicit id uses registry-backed in-memory
completion. Open a point-in-time, newest-last one-command-per-line history
document through `SFMTextEditorPanelOpenContext` with `readOnly=true`, a no-op
save boundary, no dirty-close prompt, and discarded edits. Because v1/v2 do not
currently enforce the record's read-only flag, either complete that shared
contract for every advertised editor or exclude a nonconforming editor from
this action; never claim arbitrary editor support while allowing mutation.

`history/open*` and `history/clear` are maintenance commands and are not added
to history. Clear atomically empties memory and persistence before reporting
success, so reopening immediately displays an empty document and the blank
palette falls back to ordinary action ranking.

**Validation:** Test every placement, default/explicit/unknown editor,
read-only input suppression, close-without-save, snapshot ordering, clear,
clear persistence, and non-recordability.

**Completion criteria:** The action family opens the same immutable history in
the chosen panel/editor, all selectable editors remain genuinely read-only,
and clear leaves no self-repopulating maintenance entry.

### [ ] P-5.5 Prove the command-surface slice live and update release notes

Run focused tests, canonical compile/full tests through
`sfm-propagate-changes.exe`, and live puppets that exercise long-list wheel and
thumb scrolling, shortcut search hit isolation, contextual situation display,
terminal/property widget traversal, panel close, diagnostics, all three panel-
scale bindings, resize and duplicate defaults and `[?]`, successful versus
failed history recording, exact MRU
reopen, argument-bearing history execution, every history placement,
read-only/discard behavior, clear-remains-empty, F3/Escape constrained-palette
search/navigation/scrolling/cancellation, stale-session rejection, and the
disconnected-button-to-terminal click transition. Update
`changelog.sfml`. Preserve the two current generated-resource edits and do not
propagate or publish in this batch.

**Completion criteria:** Pure tests and live evidence prove every I-KEY,
I-HIST, and I-LIST ledger item plus linked plan K-1 through K-7, and no observed
UI path depends on screenshot interpretation alone.

### P-5 parallel implementation topology

P-5.0 and P-5.1 are complete. P-5.2 now follows the detailed K-phase topology
in `contextual input actions and addressable explorer plan.md`: the pure
situation/storage model, panel child-widget host, and post-D-1 layout fixtures
may proceed in parallel, but one integration owner serializes multiplexer,
terminal, action registration, binding service/UI, and puppet changes. P-5.3's
history service may proceed independently until it integrates with
`SFMCommandPaletteScreen`; P-5.4 follows that history contract. P-5.5 is the
canonical join gate. No subagent edits the
canonical plans, generated Vox outputs, lockfile, or changelog concurrently
with the integration owner.

## Resolved design decisions — 2026-08-02

- The managed repository-review bundle producer/importer/workspace is dropped
  in favor of direct revision selectors feeding the comment/session model.
- `sfm:panel/open sfm:terminal` is the sole terminal-opening action. Hierarchical
  `sfm:terminal/server/start` and `sfm:terminal/server/connect` actions manage
  lifecycle only; `sfm:terminal/open` is removed.
- Every participating file/lane has both before and after leaves. A missing
  revision is represented by an explicit tombstone leaf.
- **Superseded 2026-08-05 by contextual-input guidance KBIND-2:** the earlier
  decision prohibited panel duplication. Existing `sfm:panel/move/<direction>`
  still transfers the visible entry intact, but deliberate
  `sfm:panel/duplicate/<direction>` actions are now planned after duplication
  state/lifecycle gate D-1 closes. Do not alias one mutable panel instance into
  two slots.
- Content and scale rotations include only the currently visible entry in each
  slot. Hidden stacked entries remain unchanged and in their owning stacks.
- Change explorers default to every maintained SFM worktree known to the
  toolchain, ordered oldest to newest. An optional lane filter may narrow the
  view.
- A selector failure in one review lane leaves that lane visible with a
  diagnostic placeholder. It neither disappears nor prevents resolved lanes
  from being inspected.
- After equivalent panel/explorer behavior and puppets exist,
  `sfm:developer/open_source_review`, `sfm:developer/open_comment_review`, and
  their fixture-only workspace shells are removed. The reusable
  review-session/comment kernel, stores, persistence, selectors, styles, and
  editing components remain.

All panel/review product-input gates D-1 through D-5 are resolved. They are no
longer implementation blockers for P-2 or P-3.

## Confirmed constraints

1. The full Rust-toolchain feature set is the primary release target; the Java-only Gradle default exists for contributor compatibility and must not reduce the released product scope.
2. Prospective player-facing features graduate only with focused regression tests, live acceptance proof, documented fallback behavior, and release notes.
3. Dependency inclusion must be driven by lock/projection policy, with explicit classification for optional integrations and future dev-only tooling.
4. The Rust CLI is the normal build/run entry point. Legacy Gradle remains a compatibility path and must continue to produce a coherent artifact when invoked directly.
5. Vox is a runtime dependency of the terminal feature and must be bundled through loader-native Jar-in-Jar in the distributable SFM artifact.
6. Both Gradle and Rust builders must agree on Vox bundle policy, version range, artifact version, and `is_obfuscated=false`, and the final artifact must contain the nested JAR and metadata.
7. `teamy-terminal.exe` is a separate companion server. The release must document how it is supplied/launched, while SFM must retain a graceful Java-local/unavailable-server path.
8. CC:Tweaked is not nested in the SFM jar merely because SFM has optional CC integration. Its completed optional integration remains external unless a separate product decision changes that.
9. Feature selection is declarative. Components may require named features, feature definitions may include transitive feature requirements and source-set ownership, and entry-point profiles select defaults. Components without a feature requirement remain selected, preserving the existing external-mod contributor experience.
10. The Gradle entry point defaults to a Java-only profile with the Rust feature disabled. The Rust/SFM CLI entry point defaults to the full profile with the Rust feature enabled. Explicit feature selection may override either default.
11. A Gradle sync or ordinary Java compile must not require Cargo, `vox-xtask`, or a source-built Vox artifact when the Rust feature is disabled. Enabling the Rust feature is an explicit opt-in that may require the managed source-build artifact to exist.
12. `--solo` remains an explicit launch-mode control and is orthogonal to feature selection. It must continue to retain required plain runtime libraries even when it excludes integration mod jars.
13. Do not change Cloud Terrastodon, merge unrelated PRs, or push a checkpoint until the user explicitly asks for that release operation.

## Release decision gates

These decisions are required before implementation is considered release-directed:

| Gate | Decision required | Default working assumption | Evidence to record |
|---|---|---|---|
| Version | Is this release 4.35.0, or another version? | 4.35.0 is the candidate because the changelog has a 4.35.0 PRE section. | Approved version, changelog scope, `gradle.properties`, generated resources. |
| CC support | Is CC:Tweaked retained as an optional player-facing integration, or parked behind the dev profile? | Retain until explicitly parked; it is not current SFM Jar-in-Jar bloat. | Final `mods.toml`, lock scopes, source boundary, clean install with and without CC. |
| Vox/terminal | Is Vox and the Rust terminal a release feature, a dev-only feature, or deferred entirely? | Graduate it into the player-facing release after the remaining packaging, companion-server, and clean-install gates pass. | Feature matrix, default launch behavior, changelog/release notes. |
| Feature/profile defaults | Which features are selected by each entry point? | Gradle defaults to Java-only; the Rust CLI defaults to the full Rust/Vox feature set; explicit selection overrides defaults. | Lockfile feature/profile model, Gradle sync/compile without Cargo, Rust full build, opt-in Gradle parity build. |
| Artifact names | Which artifact is published as the release artifact? | Publish the full Rust-toolchain artifact with Vox nested. Gradle’s default Java-only output is a contributor compatibility artifact, not a replacement release artifact. | Artifact inventory, nested-JAR metadata, publication task output, profile-specific manifests. |
| Version surface | Which maintained Minecraft worktrees receive the shared release changes? | Implement and prove on 1.19.2, then propagate the proven baseline oldest-first. | Propagation log and per-version validation matrix. |
| Change intake | Which open PRs/issues are included in this release? | Require explicit triage; do not absorb old or non-mergeable work by default. | PR/issue matrix with include, defer, fix, or close decision. |

## Feature profiles and legacy Gradle compatibility

The previous `--slim` idea is superseded by a declarative feature/profile model.
“Slim” now describes the default Gradle contributor experience—Rust is not
selected and Cargo/Xtask/Vox source acquisition is not required—not a second
hand-maintained dependency list or an artifact-size switch.

The schema-v4 direction is:

- A component can declare feature requirements. Existing dependencies without
  requirements remain selected, so external integrations such as JEI,
  CC:Tweaked, and Mekanism are not accidentally treated as `--solo` or hidden
  by the Rust profile.
- Feature definitions can express transitive feature requirements and the
  source sets/resources owned by the feature. The initial `rust` feature owns
  the Rust/Vox bridge and its source-built runtime closure.
- Entry-point profiles declare defaults, for example `gradle: []` and
  `rust-toolchain: [rust]`. The profile is selected by the tool invoking the
  lockfile, not by a hardcoded module list in Groovy.
- Explicit feature selection is an override with validation: unknown features,
  missing required artifacts, and incompatible source-set combinations fail
  clearly rather than silently producing a partial runtime.

The exact field names belong in the schema migration, but the model must keep
three concerns separate: dependency feature selection, run-time `--solo`
classpath trimming, and Jar-in-Jar publication. A feature-disabled Gradle
compile must not load Rust-owned Java sources that import Vox types; those
sources need a feature-owned source boundary or an equivalent adapter boundary.

### Legacy Gradle compatibility

The immediate Gradle work is generic feature/profile projection and preserved
legacy compatibility. The compatibility work should be concentrated in the
existing projection and publication seams:

- `platform/minecraft/gradle/dependencies-from-lock.gradle`: select the
  entry-point profile before configurations are populated, then project every
  component whose feature requirements are satisfied. It must not contain
  Facet/Vox-specific conditionals.
- `platform/minecraft/gradle/repositories.gradle`: register exclusive local
  content only for enabled source-built modules; disabled source-built
  components must not cause Cargo/Xtask or local-cache requirements during
  sync.
- `platform/minecraft/gradle/jar-jar.gradle`: select the nested artifact from the projected `jarJar` configuration and publish it as the unclassified release artifact.
- `platform/minecraft/gradle/publishing.gradle`: publish the full artifact containing the nested Vox runtime.
- `platform/minecraft/gradle/dependencies/1.19.2/dependencies.gradle`: preserve legacy direct-dependency compatibility and document which lock projection wins when both mechanisms are active.

The default Gradle build must remain a valid Java-only fallback and must still
load the other declared integration mods. An explicit Gradle Rust-feature
profile must remain available for parity testing and must use the same lockfile
selection and Jar-in-Jar policy as the Rust toolchain. Any profile that excludes
classes must first introduce a stable registration boundary or equivalent
source-safe adapter; otherwise the main SFM class can fail to load before an
optional-mod check runs.

### Rust build/run compatibility

The Rust path should first gain Forge Jar-in-Jar parity in the existing artifact planning/execution modules. It must consume the lockfile’s Vox bundle policy, emit `META-INF/jarjar/<artifact>.jar` and `META-INF/jarjar/metadata.json`, and include those entries in artifact audits. Add tests for exact nested bytes, metadata, Forge toolchain selection, and a full external-style launch. Feature selection must be threaded through the same layers rather than special-cased in a command handler; the Rust entry point defaults to the full feature profile.

## Source references

- Repository rules: `D:\Repos\Minecraft\SFM\repos2\1.19.2\docs\AGENTS.md`
- Contextual input/action/address plan:
  `D:\Repos\Minecraft\SFM\repos2\1.19.2\docs\tasks\contextual input actions and addressable explorer plan.md`
- Existing release workflow: `D:\Repos\Minecraft\SFM\repos2\1.19.2\docs\tasks\puppet propagation and preview matrix plan.md`
- Terminal bridge: `D:\Repos\Minecraft\SFM\repos2\1.19.2\docs\tasks\vox terminal bridge and graceful degradation plan.md`
- Teamy Terminal integration: `D:\Repos\Minecraft\SFM\repos2\1.19.2\docs\tasks\teamy terminal repository and Vulkan renderer plan.md`
- Dependency projection: `D:\Repos\Minecraft\SFM\repos2\1.19.2\docs\tasks\dependency source management v3 plan.md`
- Jar-in-Jar research: `D:\Repos\Minecraft\SFM\repos2\1.19.2\docs\architecture\vox-java-jar-in-jar-packaging-research.md`
- Version metadata: `D:\Repos\Minecraft\SFM\repos2\1.19.2\platform\minecraft\gradle.properties`
- Mod dependency declarations: `D:\Repos\Minecraft\SFM\repos2\1.19.2\platform\minecraft\src\main\resources\META-INF\mods.toml`
- Release/changelog candidate: `D:\Repos\Minecraft\SFM\repos2\1.19.2\platform\minecraft\src\main\resources\assets\sfm\template_programs\changelog.sfml`
- Lockfile: `D:\Repos\Minecraft\SFM\repos2\1.19.2\platform\minecraft\sfm-toolchain.lock.json`
- Rust terminal repository: `G:\Programming\Repos\teamy-terminal`
- Resumable-plan instructions used for this document: `G:\Programming\Repos\skills\.github\skills\resumable-implementation-plans\SKILL.md`

## Execution order

Do not begin implementation in a later phase while an earlier decision gate is unresolved, except for read-only inventory and tests that clarify the gate.

### Phase 0 — Checkpoint inventory and change intake [in progress]

#### 0.1 Record the baseline [in progress]

**Work:** Record the current branch/ref, worktree cleanliness, version metadata, changelog candidate, existing artifacts, active plans, and the Rust/Gradle build entry points. Preserve the current local commits as the starting checkpoint.

**Validation:** Re-run `sfm-propagate-changes.exe git status`, `git status --short`, version-resource inspection, and artifact inventory from the canonical 1.19.2 worktree. Record any environment-only failures separately from product failures.

**Completion criteria:** This plan contains enough evidence to identify the exact pre-release baseline and no untracked source change is mistaken for release work. The feature-worktree safe-directory ownership issue is documented rather than “fixed” by changing global Git configuration.

#### 0.2 Triage open PRs and issues [ ]

**Work:** Create a release intake table with each candidate’s base version, mergeability, behavior risk, test evidence, and decision: include, repair then include, defer, or close as obsolete.

**Validation:** Review the current GitHub state and link each accepted/deferred item. The initial relevant candidates are [PR #582](https://github.com/TeamDman/SuperFactoryManager/pull/582), a new 1.19.2 performance PR requiring correctness/benchmark review, and [PR #486](https://github.com/TeamDman/SuperFactoryManager/pull/486), an old non-mergeable NBT feature requiring an explicit defer/repair decision. [Issue #469](https://github.com/TeamDman/SuperFactoryManager/issues/469) and [Issue #473](https://github.com/TeamDman/SuperFactoryManager/issues/473) appear housekeeping/documentation-oriented rather than automatic release blockers.

**Completion criteria:** Every included change has a validation owner and test plan; every deferred change has a reason and a follow-up location. No PR is merged solely because it is open or has a favorable mergeable flag.

#### 0.3 Convert user-testing findings into acceptance cases [x]

**Work:** Completed the deterministic cases for fuzzy action discovery from `sfm action invoke`, Rust-terminal layout without a Java input strip, and visible triple-Escape/triple-Tab progress. The command-palette parser and Rust terminal protocol remain separate: ranking does not change Brigadier execution semantics, and layout/status changes do not alter key delivery.

**Validation:** `sfm-propagate-changes.exe run compile --branch 1.19.2` passed. `sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMTerminalFocusSequenceTests` passed 3/3, and `--filter SFMClientActionPaletteSuggestionTests` passed 2/2. The real puppets `title_screen_rust_terminal` and `title_screen_command_palette` both passed with `failed=0`; captures are archived under `platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/` and `platform/minecraft/runGameTestPreview/screenshots/`.

**Completion criteria:** Each reported behavior has a regression test plus a human-readable acceptance statement, and the release matrix distinguishes Java-local terminal behavior from Rust-backed behavior.

### Batch 1 completion evidence — 2026-08-01

- `SFMTerminalPanel` now gives the Rust/Vox PNG the full terminal content area and does not render the Java-local `> _` input strip on that path; the Java-local REPL prompt remains unchanged.
- `SFMTerminalFocusSequence` exposes localized, time-bounded progress state for Escape and Tab. The Rust-terminal puppet visibly captured “Press Esc 2 more times within 1.5 seconds to close terminal” and “Press Tab 2 more times within 1.5 seconds to return focus to Minecraft.”
- `SFMClientActionCommandTree.getPaletteSuggestions(...)` originally added fuzzy action-id/title discovery only in the palette action-id slot. The V-4.2c.2 correction also ranks immutable literal children in the active nested Brigadier slot, allowing `sfm:panel/open term` to discover `sfm:terminal` without fuzzing numeric/path/free-text arguments. Brigadier remains authoritative for availability, parse ranges, typed arguments, and execution.
- The command-palette puppet visibly captured the live `sfm action invoke open` query with ranked results including the then-current `sfm:terminal/open`. P-1 retires that action in favor of `sfm:panel/open sfm:terminal`; the capture remains historical fuzzy-ranking evidence.
- The installed propagation CLI also now reports Windows `os_error=5` lock failures explicitly, warns immediately, and caps the ambiguous retry at 10 seconds instead of waiting 15 minutes. Its `check-all.ps1` gate passed with 363 tests passing and 1 ignored.

### Jar-in-Jar packaging evidence — 2026-08-01

- The Vox component now projects to `compile`, `runtime`, and `bundle` with accepted range `[0.10.0-rc.5]`, artifact version `0.10.0-rc.5`, and `is_obfuscated=false`.
- `cargo test --manifest-path platform/cli/sfm-propagate-changes/Cargo.toml loader_jarjar --offline` passed 3/3, including deterministic Forge and NeoGradle metadata coverage.
- `sfm-propagate-changes.exe jar plan --branch 1.19.2 --artifact-source G:\Programming\Repos\facet\vox\java\target --dry-run` produced a plan with `org.facet:vox-java:0.10.0-rc.5` in `jarJar` and the locked bundle policy.
- The Rust full artifact build passed with the explicit Facet/Vox source. `platform/minecraft/build/libs/Super Factory Manager (SFM)-MC1.19.2-4.34.0-rust.jar` is 2,926,764 bytes and contains `META-INF/jarjar/vox-java-0.10.0-rc.5.jar` (403,625 bytes) plus `META-INF/jarjar/metadata.json`. The nested JAR SHA-256 is byte-identical to the source artifact: `2d0a45e8339be3229fe657c465470dfa10df34d040b43f6cd2cd5e6360419007`.
- The legacy ForgeGradle `jarJar` task now passes through `sfm-propagate-changes.exe gradle run --branch 1.19.2 --show-logs jarJar` without any injected Gradle property. `repositories.gradle` discovers the SFM-managed Maven cache itself, and the 49.4-second run completed `:jarJar` and `:reobfJarJar` successfully.
- The Gradle full artifact `platform/minecraft/build/libs/Super Factory Manager (SFM)-MC1.19.2-4.34.0.jar` is 2,948,745 bytes and contains `META-INF/jarjar/vox-java-0.10.0-rc.5.jar` (403,625 bytes) plus `META-INF/jarjar/metadata.json`. Its nested Vox JAR SHA-256 is the same as the Rust artifact: `2d0a45e8339be3229fe657c465470dfa10df34d040b43f6cd2cd5e6360419007`.
- This proves the Gradle compatibility path through the managed local repository. A clean checkout still needs either the locked source-build materialization or a published Maven coordinate; the local cache is not a substitute for external distribution.
- The lock currently records Facet commit `aa75598da`, while the byte-accurate Vox artifact came from the two local `main` commits `8c3c23c31` and `973318f72`. Those commits must become reachable from the locked remote (or the artifact must be published) before a `--require-portable-artifacts` release build can pass. Do not claim this gate complete from the explicit-source build.

### Lockfile acquisition-strategy correction — next

The current schema-v3 Vox declaration says
`declaration.acquisition.maven.repository_id = maven-central` even though its
artifact provenance is `source-build` and no Vox Maven publication exists.
This is a misleading legacy declaration and must not become the basis for
repository fallback behavior.

The next lockfile slice will represent source-build acquisition explicitly. If
the strict v3 acquisition enum cannot express that without overloading Maven,
bump the lockfile schema and add a migration. Keep the source-build recipe and
exact Git provenance in the artifact evidence, while making the component
declaration point to that source-built artifact rather than to a fictional
remote publication.

Gradle should then project source-built modules through an exclusive local
repository content rule. The following is only a demonstration of the Gradle
mechanism, not the implementation target:

```groovy
exclusiveContent {
    forRepository {
        maven {
            name = 'sfm-source-builds'
            url = uri(sfmLocalMavenRepository)
            metadataSources { artifact() }
        }
    }
    filter { includeModule('<group from lockfile>', '<artifact from lockfile>') }
}
```

The actual Groovy projection must read the new lock schema, enumerate every
source-build acquisition, derive its Maven module identity from the locked
coordinate, and generate the `includeModule` filters. It must not contain
Facet, Vox, or any other project-specific module names. The generated
source-build content filters must prevent those modules from being searched in
any remote repository. This is stronger than putting the local repository
first. Remote repositories remain available for dependencies whose lock
acquisition is genuinely remote.

### Feature/profile model — next

The source-acquisition migration must be designed together with feature
selection. A source-built dependency is not merely an alternative download
location: it may also belong to a feature that is intentionally absent from a
legacy Gradle contributor build.

The v4 lock model should therefore provide, in declarative data:

- feature definitions and transitive feature requirements;
- component feature requirements, with an empty requirement meaning the
  component remains part of every normal projection;
- feature-owned source sets/resources or source-boundary declarations; and
- entry-point profiles whose defaults are selected by the invoking toolchain,
  with Gradle defaulting to no Rust feature and the Rust/SFM CLI defaulting to
  the full Rust feature set.

The initial Rust feature should own the Rust/Vox bridge and its source-built
runtime closure. Existing external integrations must remain independently
selected. In particular, disabling Rust must not be implemented as `--solo`,
and must not remove JEI, CC:Tweaked, Mekanism, or other ordinary lockfile
components from the Gradle projection.

The first implementation must also move or boundary Rust-owned Java sources
that import Vox types. Merely omitting the Vox dependency while compiling the
same source set would make the Java-only profile fail at compile time. Gradle
configuration and IntelliJ sync must be able to select the Java-only profile
without invoking Cargo, `vox-xtask`, or source acquisition. An explicit Rust
feature selection may require a previously materialized managed artifact and
must report a clear missing-artifact error.

**Validation:** Use an isolated machine/cache profile to prove that ordinary
Gradle configuration, IDE model generation, and Java compilation succeed with
no Cargo/Xtask/Vox source artifact available; verify that the same projection
still includes ordinary integration mods. Then enable the Rust feature
explicitly in Gradle and through the Rust CLI, prove the full Vox/Jar-in-Jar
closure, and run the existing `--solo` classpath tests to show that launch-mode
trimming remains independent.

**Completion criteria:** The lockfile, Gradle projection, and Rust planner
agree on feature closure and entry-point defaults; Java-only Gradle users have
no Rust setup requirement; Rust-toolchain users receive the full feature set by
default; and explicit feature selection is tested in both directions.

### Goal batch R-3A — Lockfile-driven entry-point profiles [x]

This is the first proposed implementation goal for the feature/profile
direction. It groups the work above into a single verifiable boundary; it is
not a top-k selection of unrelated incomplete tasks.

#### R-3A.1 — Migrate the lock schema and 1.19.2 fixture [x]

Add the typed source-build acquisition and feature/profile model to the next
lockfile schema, migrate v3 documents, and migrate the 1.19.2 fixture so Vox
is owned by the Rust feature rather than falsely declared as a Maven Central
publication. Preserve all existing non-Rust dependency declarations and
provide schema/migration fixtures for both old and new documents.

#### R-3A.2 — Implement generic feature closure [x]

Implement one lockfile-driven feature resolver used by both projections. It
must resolve transitive feature requirements, reject unknown features, retain
components with no feature requirement, and expose the selected source-set and
artifact closure without hardcoded dependency names.

#### R-3A.3 — Make Gradle Java-only by default [x]

Make the Gradle entry point select the Java-only profile unless explicitly
overridden. Gradle configuration, IntelliJ synchronization, and Java
compilation must not require Cargo, `vox-xtask`, Vox source acquisition, or a
managed Vox artifact. Existing JEI, CC:Tweaked, Mekanism, and other ordinary
integration projections must remain present.

#### R-3A.4 — Make the Rust entry point full-featured by default [x]

Make the Rust/SFM CLI select the full Rust profile by default, with an
explicit feature/profile override. `jar plan` must include the Vox closure in
the default Rust projection, while the source-build resolver remains the
authoritative acquisition path for that feature.

#### R-3A.5 — Establish source and launch compatibility proof [x]

Move or boundary Rust-owned Java sources that import Vox types so the
Java-only Gradle profile compiles. Add regression tests proving the two entry
point defaults, ordinary integration retention, explicit Rust opt-in, and
the existing `--solo` classpath behavior. Record isolated-cache Gradle and
Rust-plan evidence in this plan.

**R-3A completion boundary:** Stop after the schema, projections, source
boundary, and profile tests pass. Do not include the portable source-build
artifact proof, explicit Gradle Jar-in-Jar parity build, cross-version
propagation, or release publication in this goal; those are subsequent
release-plan work.

### R-3A completion evidence — 2026-08-01

- `platform/cli/sfm-propagate-changes/src/toolchain_lockfile_schema/version/v4.rs`
  adds schema v4 feature/profile closure, source-boundary declarations, v3
  migration, cycle/unknown-reference validation, and effective v3 projection
  for the existing resolver. `read_current` selects `rust-toolchain` by
  default, so the Rust/SFM entry point remains full-featured.
- `platform/minecraft/sfm-toolchain.lock.json` is schema v4. Vox is a
  `source-build` acquisition referencing its source-build artifact, owned by
  the declarative `rust` feature; it is no longer described as Maven Central.
  `gradle` selects no Rust feature, while `rust-toolchain` selects `rust`.
- Gradle feature selection is generic and lock-driven in
  `platform/minecraft/gradle/lockfile-features.gradle`. Default Gradle
  projection excludes feature-owned Vox source/artifacts; `-PsfmProfile=rust-toolchain`
  explicitly restores them. Repository projection uses exclusive local
  content for selected source-built modules, so it cannot fall through to a
  remote Maven repository.
- The shared terminal panel/PNG renderer now depend on a transport-neutral
  interface; only `SFMVoxTerminalService.java` and its test are feature-owned
  Vox source files. Ordinary Gradle builds can compile the Java path without
  the Vox jar while retaining CC:Tweaked, Mekanism, and other integrations.
- Validation: `cargo test --manifest-path platform/cli/sfm-propagate-changes/Cargo.toml toolchain_lockfile_schema --lib`
  passed 24/24; `cargo test ... solo_client --lib` passed 1/1; and
  `cargo run --manifest-path platform/cli/sfm-propagate-changes/Cargo.toml -- dependency migrate --branch 1.19.2 --check`
  reported canonical schema v4. Default `gradlew compileJava` and
  `gradlew testClasses` passed, with no Vox main/test classes emitted;
  explicit `gradlew compileJava -PsfmProfile=rust-toolchain` passed and
  emitted `SFMVoxTerminalService.class`.
- Boundary respected: no portable source-build proof, final Jar-in-Jar parity,
  propagation, Cloud Terrastodon changes, or publication was attempted.

### Source-build/API hypothesis experiment — complete (2026-08-01)

Before changing the Vox pin, the existing source/build behavior was tested from
isolated caches with no Gradle property injection. The experiment answered
whether the current lock naturally materializes the pinned Facet revision and
whether the resulting older Vox API is actually incompatible with the current
SFM Java sources.

```pwsh
$env:SFM_PROPAGATE_CHANGES_CACHE = 'C:\tmp\sfm-vox-lock-hypothesis-20260801'
sfm-propagate-changes.exe jar build --branch 1.19.2 `
  --require-portable-artifacts --wait-for-build-lock
```

- A fresh `jar build --require-portable-artifacts` first failed during remote
  artifact resolution: `modmaven.dev` returned bytes with hash
  `d4736e46...` instead of the locked `ff6894ba...`. The resolver probed
  ModMaven because `candidate_repositories` prefers display labels such as
  `Maven Central`, while the schema-v3 projection supplies canonical IDs such
  as `maven-central`; the failed preference lookup caused every repository to
  be tried. This is a repository-selection bug, not evidence that ModMaven
  hosts Vox, and it stopped the run before source materialization.
- A controlled Gradle `jarJar` run against the known older `aa75598da` Vox JAR
  failed at `:compileJava` with four missing-symbol errors for
  `TerminalContentRequest` and `TerminalContentResult` in
  `SFMVoxTerminalService.java`.
- The hypothesis is confirmed: the current SFM sources require the newer Vox
  API. Update the source-build pin and expected hash before rerunning the
  portable build.
- After repository preference is corrected, rerun the clean-cache experiment
  to verify that Maven Central's miss proceeds to the existing source-build
  fallback. Keep any remaining fallback behavior as a separate CLI hardening
  item; do not confuse it with the now-confirmed Vox pin mismatch.

### Proposed goal batch R-4A — Portable Vox source-build and artifact parity [ ]

This batch is prepared for activation after user approval. It is the next
bounded slice after R-3A: turn the declarative source-build/profile model into
reproducible artifact evidence. It does not change the Java-only Gradle
default, propagate to other Minecraft versions, publish anything, or modify
Cloud Terrastodon.

#### R-4A.1 — Reconcile the reachable Vox source pin [x]

Confirm which Facet/Vox commit is reachable from the locked remote and contains
the `TerminalContentRequest`/`TerminalContentResult` API required by the current
SFM sources. Update the schema-v4 source-build pin, artifact identity, and
expected BLAKE3 evidence only after the source commit is reachable and the
artifact can be rebuilt from it. Do not use an unpushed workstation commit as
portable provenance.

**Completion criteria:** The lockfile names a reachable source revision and
exact source-build output; the rebuilt Vox JAR has the locked coordinate and
hash; the old API-incompatible pin is no longer selected.

#### R-4A.2 — Make source-build resolution strict and lock-directed [x]

Fix the resolver path exposed by the hypothesis experiment. Source-built
components must materialize from their locked source recipe rather than probe
unrelated Maven repositories first. Remote repository selection for genuinely
remote artifacts must use canonical lockfile repository IDs, not display-label
guesses. Add regression coverage proving a source-built Vox request cannot fall
through to ModMaven or another remote repository, and retain a clear diagnostic
when the locked source revision or build output is unavailable.

**Completion criteria:** A source-build request takes the source-build path in
an isolated cache; a remote miss cannot be mistaken for a successful artifact;
and repository candidates are selected from lock-directed canonical IDs.

#### R-4A.3 — Prove an isolated portable Rust build and audit [x]

Run the SFM CLI against a fresh cache with no injected Gradle property and no
pre-existing Vox artifact. Plan/build the 1.19.2 Rust profile, materialize the
locked Facet source, verify the output hash and provenance, and run the
portable-artifact audit. Use `--log-file` when observing a long-running CLI
command so lock acquisition and source-build diagnostics remain inspectable
under the Codex harness.

**Completion criteria:** `jar plan`, `jar build`, and
`jar audit-artifacts --require-portable-artifacts` succeed from the isolated
cache, with recorded commands, artifact paths, hashes, source revision, and
any environment-only limitations separated from product failures.

#### R-4A.4 — Reprove full-profile Gradle Jar-in-Jar parity [x]

With the managed source-built Maven repository produced by R-4A.3, run the
explicit `rust-toolchain` Gradle packaging path. Inspect the final unclassified
artifact and confirm it contains the nested Vox JAR and Forge Jar-in-Jar
metadata, while the default Gradle profile remains Java-only. Compare the
nested Vox bytes and coordinates against the Rust artifact rather than merely
checking that both builds compile.

**Completion criteria:** The explicit Gradle full artifact and Rust artifact
contain byte-identical Vox JARs with matching metadata and bundle policy; the
default Gradle compile/test path still works without Cargo, Xtask, or Vox.

#### R-4A.5 — Record the release-artifact handoff boundary [x]

Record the portable-build and parity evidence in this plan and update the
artifact inventory/release gates. If an external clean Forge-style launch
still needs a separate environment or companion-server step, record it as the
next batch rather than silently expanding R-4A.

**R-4A completion boundary:** Stop after reachable source provenance,
strict source-build resolution, isolated Rust artifact proof, and explicit
Gradle/Rust nested-JAR parity are complete. Do not include cross-version
propagation, release version/changelog decisions, PR/issue intake, publication,
or Cloud Terrastodon work in this goal.

**Proposed goal text after approval:** `Complete Goal Batch R-4A in
docs/tasks/release checkpoint and slim artifact plan.md: R-4A.1 through
R-4A.5.`

### R-4A progress evidence — 2026-08-01

- R-4A.1 is complete. The Facet commits were pushed to `mine/main` (`https://github.com/teamdman/facet`), and `git ls-remote mine refs/heads/main` now reports the reachable full revision
  `973318f727128f5ec17c67ab9498cb9e9be2a5e6`. The lockfile pins that revision,
  the `org.facet:vox-java:0.10.0-rc.5` source-build output, and
  `blake3:ff6894bafc65fd9d24cafd60a967bd1c3182a377`. The rebuilt 403,625-byte
  Vox JAR contains the required `TerminalContentRequest`/`TerminalContentResult`
  and `TerminalGetContent*` generated classes.
- R-4A.2 is complete in `resolve.rs`: explicit `--artifact-source` takes
  precedence, then a locked source-build recipe, then locked remote candidates,
  then local cache fallback. Source-built artifacts therefore cannot fall
  through to ModMaven or another remote repository. Canonical repository IDs are
  used for candidate selection, with regressions for source-build isolation,
  explicit-source precedence, and `maven-central`/`blamejared`/`jei` ordering.
- The schema-v4 audit reader was corrected to project the selected current
  profile into the existing audit model while preserving schema-v1/v2 audit
  compatibility. The checked-in lockfile's volatile Mojang version-manifest
  identity was refreshed to the bytes actually consumed by this build
  (`version-manifest-v2-867e9731`, `blake3:867e97315bd3e7100125db20739d0c1ab40ef0a8`).
- R-4A.3 is complete from the fresh cache
  `build/sfm-r4a-cache-20260801-new`, with no injected Gradle property and no
  pre-existing Vox artifact. `jar plan`, `jar build --require-portable-artifacts`,
  and `jar audit-artifacts --require-portable-artifacts` succeeded. The Rust
  artifact is
  `platform/minecraft/build/libs/Super Factory Manager (SFM)-MC1.19.2-4.34.0-rust.jar`
  (2,929,365 bytes); the audit verified 109/109 artifacts with zero errors,
  warnings, or non-portable artifacts. The portable cache also records the Vox
  source-build provenance and exact locked hash.
- R-4A.4 is complete. Default Gradle validation passed with
  `gradlew compileJava testClasses -PsfmProfile=gradle`, and explicit full-profile
  packaging passed with `gradlew jarJar -PsfmProfile=rust-toolchain`. The Gradle
  artifact is
  `platform/minecraft/build/libs/Super Factory Manager (SFM)-MC1.19.2-4.34.0.jar`.
  Its nested Vox JAR and the Rust artifact's nested Vox JAR are both 403,625
  bytes with SHA-256
  `2D0A45E8339BE3229FE657C465470DFA10DF34D040B43F6CD2CD5E6360419007`, and
  their `META-INF/jarjar/metadata.json` entries match exactly for
  `org.facet:vox-java` at `[0.10.0-rc.5]`. The whole-archive comparator reports
  one intentional Gradle-only `SFMClientSmokeRunHarness.class`, with no changed
  or extra entries and identical normalized manifests; deciding whether the
  Rust executor should include that test harness is deferred beyond R-4A.
- R-4A.5 is complete at the release-artifact handoff boundary: the reachable
  source pin, strict acquisition behavior, isolated Rust artifact, full-profile
  Gradle Jar-in-Jar bytes, default Java-only Gradle path, and audit evidence are
  recorded here. The next release work may address clean Forge-style launch
  validation and the deferred smoke-harness difference, but this batch does not
  propagate across Minecraft versions, publish SFM, change release metadata,
  or modify Cloud Terrastodon.
- Final CLI validation: `cargo fmt --all -- --check` passed and the elevated
  `cargo test --offline --lib` suite passed 370 tests with 1 ignored. The
  sandbox-only ripgrep subprocess test also passes when run outside the Codex
  sandbox; its in-sandbox `os_error=5` is an environment limitation.

### Phase 1 — Freeze the release scope [in progress]

**Work:** Decide the release version, supported Minecraft versions, player-facing feature list, CC policy, Vox/terminal policy, feature/profile defaults, and whether the 4.35.0 PRE changelog is the candidate scope. The current direction is to graduate the terminal and command-palette work while keeping the Java-only Gradle profile as contributor compatibility rather than hiding product behavior behind an unmodeled `--slim` switch.

**Validation:** Compare the approved matrix against the changelog, `mods.toml`, dependency lock, active plans, and current generated/version resources. Verify that every advertised integration has a clean-install test or is explicitly labeled experimental/dev-only.

**Completion criteria:** The version and feature matrix is approved, the support statement is written, and the full artifact’s dependency closure—including Vox Jar-in-Jar and the companion-server requirement—is explicit.

### Phase 2 — Commit the current baseline [complete]

**Work:** Commit the current 1.19.2 terminal, command-palette, puppet, CLI-diagnostic, dependency-lock, and plan changes in an intentional checkpoint. Keep Facet/Vox’s existing local commits and Teamy Terminal’s source changes separate from generated build output.

**Validation:** Focused Teamy Terminal Vox tests pass; CLI artifact-lock tests pass; SFM compile and puppet evidence are recorded; each committed repository is clean except for intentionally ignored generated output.

**Completion criteria:** The current work is recoverable from local commits with no source changes silently left outside the checkpoint. This is satisfied by the clean SFM, Facet/Vox, and Teamy Terminal worktrees and the local checkpoint commits.

### Phase 3 — Bundle Vox through Jar-in-Jar [in progress]

**Work:** Add Vox’s explicit bundle policy to the lockfile, preserve compile/runtime projection, and make Gradle select the nested artifact for Forge 1.19.2. Correct the acquisition model so source-built Vox is not represented as a Maven Central publication; add the declarative feature/profile model and bump/migrate the lock schema if required. Gradle’s default profile must exclude Rust-owned sources and artifacts without excluding unrelated integration dependencies.

**Validation:** Rust packaging, lock projection, and legacy Gradle packaging through the self-discovered managed Maven cache are proven as recorded above. Complete the acquisition/profile migration, prove a Java-only Gradle sync/compile with no Cargo/Xtask/Vox artifact, then make the locked Vox source/artifact reproducible from a clean checkout, inspect both outputs, and run a clean Forge-style launch without an external Vox Java dependency.

**Completion criteria:** The explicitly Rust-enabled Gradle artifact is deterministic, unclassified, and contains the required nested Vox runtime with valid Forge metadata; the default Java-only Gradle artifact remains compilable without Rust setup; and the source/artifact provenance is reproducible from a clean checkout.

### Phase 4 — Match the Rust artifact builder [in progress]

**Work:** Keep the existing Forge/NeoGradle Rust Jar-in-Jar emission covered by the lock policy, close the portable source-build/provenance path, and make the Rust entry point select the full feature profile by default. The Forge emission and deterministic unit tests are already present.

**Validation:** Targeted Rust JarJar tests and the explicit-source full artifact pass. Re-run the artifact audit with `--require-portable-artifacts` after the Facet revision is reachable, compare nested entries and metadata with Gradle, and run an external-style launch.

**Completion criteria:** Rust and Gradle agree on Vox bundle contents, metadata, artifact naming, dependency closure, and runtime availability from reproducible inputs. A missing Rust service does not break Java-local behavior.

### Phase 5 — Graduate feature acceptance and companion distribution [ ]

**Work:** Close the remaining terminal and command-palette release gates,
including R-PERF.1 through R-PERF.3; document how `teamy-terminal.exe` is
installed or launched, retain the independently openable Java-local REPL, and
keep CC as an external optional integration. Verify that the Gradle Java-only
profile is a contributor path while the Rust CLI full profile remains the
release path; do not use profile selection to conceal missing functionality.

**Validation:** Exercise the default Java-local path, clean launch without the
Rust service, launch with the Rust service when enabled, the relevant
optional-mod combinations, and the packaged artifact with nested Vox. Attach
the correlated lag witness and matched renderer/transport visual-temporal
report to the release decision.

**Completion criteria:** Each advertised feature has deterministic behavior,
tests, a release-note/support statement, and no hidden development-only
dependency. The terminal bridge is either fully released with its
companion-server story and no unexplained multi-second delay in the matched
acceptance workload, or explicitly deferred by a recorded decision.

The user-testing fixes above are prerequisites for calling the Rust terminal or command palette release-ready; the current direction is to finish the remaining gates and promote them, not to exclude them from the artifact.

### Phase 6 — Propagate deliberately [ ]

**Work:** Implement and validate on 1.19.2 first, then propagate the shared CLI/build changes oldest-first with `sfm-propagate-changes.exe`. Resolve version-specific dependency and packaging differences explicitly; do not overwrite newer worktree changes.

**Validation:** Run the repository’s compile/test/preview matrix per maintained version, inspect each propagated diff, and re-run status. Do not treat a clean merge as feature validation.

**Completion criteria:** Every supported target has either a passing release validation result or a written reason it is excluded from this release. The propagation log identifies any target-specific adaptation.

### Phase 7 — Release proof and housekeeping [ ]

**Work:** Update version resources and changelog, build the full artifact, collect and audit it, stage isolated installs, verify clean loader launches, run gameplay/puppet/smoke tests, prepare release notes, and update the selected PR/issue records.

**Validation:** Compare artifact contents and sizes against the approved budget; inspect manifests, nested JARs, dependency closure, and classifier. Verify the full artifact outside userdev and capture the exact commands/results. Check the documented companion-server behavior, disconnected Rust scene, and independently openable Java-local REPL.

**Completion criteria:** The release candidate is reproducible from a clean checkout, has a complete support/dependency statement, passes the agreed cross-version matrix, and has a clearly documented rollback/checkpoint ref. Publication remains a separate explicit user-approved action.

## Current risks and mitigations

- **Scope/version drift:** 4.35.0 PRE changelog versus `mod_version=4.34.0`. Resolve Phase 1 before changing metadata.
- **CC source loading:** CC is optional at runtime but directly referenced by SFM initialization code. Use an adapter/source boundary and clean no-CC launch proof before excluding it.
- **Jar size misconception:** Vox is a small pure-Java runtime; it must be measured, but its size is not a reason to omit required runtime classes.
- **Vox source reachability:** Facet `main` is ahead of the locked `aa75598da` revision and the current Vox coordinate is not published in configured Maven repositories. Publish/reach the exact revision, update the lock, and rerun the portable build before release.
- **Gradle resolution:** The lock projection correctly requests `jarJar`, and legacy Gradle now consumes the source-built artifact through the self-discovered SFM-managed Maven repository. The final projection must use exclusive source-build content so a missing local artifact cannot fall through to unrelated remotes.
- **Repository candidate selection:** The resolver's preferred repository table currently uses display names while schema-v3 projected repositories use canonical IDs. Correct the mapping and add a test proving `org.facet` only probes `maven-central`; then repeat the source-build fallback experiment before changing the Vox pin.
- **Companion-server distribution:** Bundling Vox does not bundle `teamy-terminal.exe`; document and test the external server path and graceful fallback.
- **Local branch divergence:** 1.19.2 contains unpublished local checkpoint commits. Create a reviewed checkpoint ref before publishing; do not push as part of this plan without approval.
- **Open change intake:** PR #582 needs correctness and benchmark review; PR #486 is non-mergeable and should not enter the release by default.
- **Environment bookkeeping:** One feature worktree could not be inspected because Git safe-directory ownership differs for the sandbox user. Do not alter global Git configuration just to make the release appear clean.

## Overall completion criteria

This plan is complete only when:

1. The release version, support statement, and feature/dependency matrix are approved.
2. Rust-toolchain full behavior and Gradle Java-only contributor behavior are separately verified.
3. The Rust full artifact contains the required Vox nested JAR and valid loader metadata.
4. Gradle and Rust artifact builders agree on nested-JAR contents, dependency closure, and publication rules when the Rust feature is explicitly enabled.
5. The companion Rust server has a documented distribution path and graceful absence behavior.
6. The release candidate passes clean-install, external-style launch, gameplay/puppet, and cross-version checks required by the approved matrix.
7. PRs/issues have explicit disposition, release notes are ready, and a checkpoint ref is recorded.
8. Publishing or pushing has been separately approved and is not implied by completing the plan.
