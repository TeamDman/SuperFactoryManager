# Teamy Terminal — Resumable Implementation Plan

**Plan status:** Active
**Primary implementation root:** G:\Programming\Repos\teamy-terminal (main)
**Coordination mirror:** D:\Repos\Minecraft\SFM\repos2\1.19.2 (1.19.2)
**Last updated:** 2026-07-25

## How to update this plan

- [ ] Not started
- [~] In progress
- [x] Complete
- [!] Blocked

Put the status in each work-item heading and update its completion notes at the
same time. A phase is complete only when every work item in it is [x].
Record decisions, commit IDs, commands, and follow-ups below the task they
affect; do not append a detached chronological work log. Use [!] only with
the exact blocker, evidence, and condition that unblocks it. Update this
coordination copy and the repository mirror together.

**Next slice:** Phase 1.1, converting the template-derived single package into
the planned workspace without losing the CLI quality gates.

## Purpose

Create a small public MPL-2.0 Rust repository that owns a renderer-free
terminal core, Teamy font extraction, a low-level Vulkan renderer, and a CLI
for deterministic headless proofs. The observable first milestones are:

1. teamy-terminal --help and --version work from a fresh checkout.
2. A headless terminal can consume bounded PowerShell -NoProfile output for
   1..1000 and replay the same result without launching a process.
3. A Vulkan/off-screen renderer can produce validated PNG and bounded raw or
   compressed RGBA frames for optional consumers such as SFM/Vox.

This is the development-tooling foundation for the optional Vox terminal
bridge. It does not make ordinary Minecraft gameplay depend on Rust, Vulkan,
or this repository.

## Scope

### In scope

- A Cargo workspace containing teamy-terminal-core, teamy-terminal-font,
  teamy-terminal-renderer, and teamy-terminal-cli.
- Terminal state, VT/control-byte handling, bounded scrollback, cursor/style,
  selection, damage, resize, input events, snapshots, and deterministic replay.
- A PowerShell process adapter used only for the bounded smoke proof.
- Extraction of the terminal-relevant Teamy font/rasterization algorithm.
- An ash-based Vulkan presentation layer with an off-screen/readback path,
  resize handling, and renderer-neutral frame output.
- Local SFM iteration through an isolated Cargo path override, followed by a
  reviewed pinned dependency or git revision.
- Documentation and acceptance evidence that make the optional Rust path
  understandable and keep Java-local SFM fallback independent.

### Out of scope

- Importing Teamy Studio's application, PyTorch, CUDA, Burn, DirectX, or
  unrelated windowing workspace.
- Replacing the Java-local terminal, mounted editing, or Minecraft UI with a
  Rust runtime dependency.
- Sending Minecraft Screen objects, panels, layout instructions, callbacks,
  or executable UI over Vox.
- Native DirectX/OpenGL shared-handle interop in the first frame protocol.
- Making the colour-picker flow the first cross-language demonstration.
- Supporting non-Windows Vulkan windowing before the Windows/core seams are
  proven; portability remains a later acceptance expansion.

## Established foundation

These facts are verified and should not be silently re-litigated:

- TeamDman/teamy-terminal is public, MPL-2.0, and has been pushed to
  https://github.com/TeamDman/teamy-terminal. The bootstrap commit is
  e6ff0ec.
- The checkout is at G:\Programming\Repos\teamy-terminal. The plan is
  mirrored at docs/tasks/teamy terminal repository and Vulkan renderer plan.md.
- The initial checkout was copied from
  G:\Programming\Repos\teamy-rust-cli using its compatibility initializer.
  The normal cargo run -- init path was attempted but could not compile the
  template because the direct Facet pin differs from the Facet revision pulled
  by teamy-cancellation; StopAfterArgs consequently failed its Facet bound.
  This is recorded as a dependency-repair task, not a reason to import
  Teamy Studio.
- Package identity was changed to teamy-terminal; repository metadata,
  README, environment variable names, binary test names, and implementation
  source URL no longer use the template package name.
- cargo metadata --no-deps --format-version 1 and
  rustup run nightly -- cargo fmt --all -- --check pass against the current
  checkout. A full compile/test gate is intentionally pending the Facet
  revision repair and workspace conversion.
- G:\Programming\Repos\cursor-latency at revision 6c07705 provides a
  proven local ash 0.38/ash-window 0.13/winit 0.30 instance, device,
  swapchain, and transparent-window seam.
- G:\Programming\Repos\ash is the upstream API/examples reference; its
  ash crate is 0.38.0+1.4.352 and the checkout includes ash-examples and
  ash-window. It is not a source tree to vendor wholesale.
- The related SFM bridge contract and Java-local fallback are specified in
  docs/tasks/vox terminal bridge and graceful degradation plan.md. SFM's
  repository-specific workflow is in repos2/1.19.2/docs/AGENTS.md.

## Confirmed constraints and decisions

- The project name is Teamy Terminal and the canonical remote is
  TeamDman/teamy-terminal under MPL-2.0.
- The teamy-rust-cli template supplies conventions, not a runtime dependency.
  Retain Figue/Facet parsing, built-in --help/--version/completion,
  repository/branch/revision/worktree/build metadata, color-eyre, tracing,
  cooperative cancellation, Windows ANSI handling, build.rs, lint policy,
  rustfmt.toml, clippy.toml, and the template's round-trip test pattern.
- The workspace should expose teamy-terminal-cli as its default Cargo member,
  so cargo run -- --help and cargo run -- --version remain convenient even
  after the split. Product subcommands may start empty; built-in help/version
  must remain available.
- teamy-terminal-core must not depend on ash, winit, DirectX, CUDA,
  PyTorch, Minecraft, or a process host. Process adapters feed the core from
  outside.
- The renderer uses ash directly. ash-window, raw-window-handle, and winit
  are boundary dependencies only. Vulkan unsafe code is isolated at that
  boundary with actionable loader/device errors.
- The first frame contract is portable data: sequence, dimensions, stride,
  format, full-frame/dirty-tile kind, compression, and bytes. PNG is for
  artifacts/keyframes; raw or losslessly compressed dirty tiles are for live
  interaction. Java rejects oversized, malformed, stale, and out-of-order
  frames when it consumes them.
- SFM's local path override is development-only and must never become an
  absolute path in canonical or propagated source. Ordinary SFM gameplay and
  Java-local terminal fallback must work without a Rust checkout, renderer,
  Vulkan loader, or GPU.
- SFM version propagation remains baseline-first: canonical 1.19.2 is the
  source, sfm-propagate-changes.exe performs cross-version propagation, and
  newer-target behavior must not be clobbered.

## Assumptions requiring validation

- pwsh.exe is available on the development machine; Windows PowerShell is a
  documented fallback. The smoke is bounded and not an arbitrary shell API.
- A software Vulkan device or usable off-screen Vulkan path is available on at
  least one supported development environment. Missing Vulkan must produce a
  diagnostic rather than fail core/CLI tests.
- Teamy Studio's font algorithm can be extracted without importing its
  application model or graphics dependencies.
- The current Facet/figue revision pair can be aligned for the new workspace,
  or the CLI can temporarily use a minimal compatible dependency set while
  retaining the same public built-in CLI behavior.

## Design questions that must be closed before implementation

| Question | Required decision | Acceptance consequence |
| --- | --- | --- |
| Workspace topology | Virtual root workspace with teamy-terminal-cli as a default member, or a root package wrapper? | cargo metadata, cargo run -- --help, and all-workspace quality commands must work from the repository root. |
| Facet revision policy | Which Facet revision is compatible with teamy-cancellation, and where is that pin recorded? | Fresh clone must compile without duplicate incompatible Facet traits; lockfile provenance is reviewed. |
| Initial CLI command shape | How is “no product subcommands yet” represented while Figue built-ins remain available? | --help and --version are tested before any terminal command exists. |
| Font boundary | Separate teamy-terminal-font crate now, or renderer-internal module until the extraction seam is proven? | Core remains graphics-free; a deterministic glyph/atlas fixture proves the chosen seam. |
| Vulkan off-screen path | Which target/device/readback path is required for the first proof, and what is explicitly unsupported? | Headless/software proof or an actionable unavailable-device result is recorded; no CUDA dependency appears. |
| SFM dependency pin | Which core/frame crates are exposed to SFM first, and when does the local override become a pinned revision? | Canonical SFM compile/test and Java-local puppet proof precede propagation. |

Close each row in the completion notes of the work item that resolves it.

## Source and implementation references

| Reference | Location/command | Use |
| --- | --- | --- |
| CLI template | G:\Programming\Repos\teamy-rust-cli\Cargo.toml, src/lib.rs, check-all.ps1 | Baseline CLI, metadata, lint, logging, cancellation, and test conventions. |
| Vulkan API/examples | G:\Programming\Repos\ash (ash 0.38.0+1.4.352) | Verify raw loader, extension, pointer-chain, synchronization, and validation usage. |
| Working Vulkan application | G:\Programming\Repos\cursor-latency at 6c07705 | Reuse the proven ash/ash-window/winit application seam. |
| SFM repository rules | D:\Repos\Minecraft\SFM\repos2\1.19.2\docs\AGENTS.md | No-Gradle commands, baseline-first propagation, changelog, and audit rules. |
| SFM bridge contract | D:\Repos\Minecraft\SFM\repos2\1.19.2\docs\tasks\vox terminal bridge and graceful degradation plan.md | Java-local fallback, portable frames, capability negotiation, and puppet expectations. |
| Template quality commands | rustup run nightly -- cargo fmt --all -- --check; cargo clippy --workspace --all-targets --all-features -- -D warnings; cargo build --workspace --all-features; cargo test --workspace --all-features | Exact checks to retain/adapt in teamy-terminal/check-all.ps1. |

## Execution order

~~~text
bootstrap evidence [x]
  -> workspace + dependency repair
  -> core contract + headless replay
  -> font and Vulkan tracks (intentional parallel work)
  -> portable frame output
  -> optional SFM local integration
  -> pinned dependency + cross-repository proof
~~~

## Phases and work items

## Phase 0 — Repository bootstrap [x]

### [x] 0.1 Create the public repository and MPL-2.0 baseline

**Work:** Create TeamDman/teamy-terminal, clone it to
G:\Programming\Repos\teamy-terminal, retain the generated MPL-2.0 license,
and establish main as the default branch.

**Validation:**

~~~powershell
gh repo view TeamDman/teamy-terminal --json nameWithOwner,isPrivate,defaultBranchRef,url,licenseInfo
git -C G:\Programming\Repos\teamy-terminal status --short
git -C G:\Programming\Repos\teamy-terminal log -1 --oneline
~~~

**Completion criteria:** The remote is public, reports MPL-2.0, and the local
clone has a clean pushed bootstrap commit.

**Completion notes:** e6ff0ec (chore: bootstrap teamy terminal repository) was
pushed to main. GitHub reports isPrivate: false, default branch main, and
license key mpl-2.0.

### [x] 0.2 Copy and audit the teamy-rust-cli scaffold

**Work:** Copy the template with its exclusion/preserve rules, replace package
identity and README placeholders, retain CLI metadata/lint/test conventions,
and mirror this plan under docs/tasks.

**Validation:**

~~~powershell
cargo metadata --no-deps --format-version 1
rustup run nightly -- cargo fmt --all -- --check
Get-ChildItem G:\Programming\Repos\teamy-terminal\docs\tasks
~~~

**Completion criteria:** The package identifies as teamy-terminal, the plan
exists in the repository, and metadata/format checks pass.

**Completion notes:** The normal cargo run -- init was attempted and failed
before dispatch because of the incompatible Facet revisions described above.
The compatibility initializer copied the reviewed scaffold. Metadata and
nightly formatting passed. The plan mirror has the same SHA-256 as the
canonical coordination copy.

## Phase 1 — Workspace foundation [ ]

### [ ] 1.1 Convert the single package into the four-crate workspace

**Work:** Create the root workspace and
crates/teamy-terminal-core, crates/teamy-terminal-font,
crates/teamy-terminal-renderer, and crates/teamy-terminal-cli. Set workspace
metadata, resolver, locked dependencies, and a default member for the CLI.
Move only relevant template code into the CLI crate; remove the example cache,
home, and init product API after its bootstrap value is preserved in repository
history.

**Validation:**

~~~powershell
cargo metadata --format-version 1
cargo run -- --help
cargo run -- --version
~~~

**Completion criteria:** A fresh checkout resolves all four members, the root
smoke commands work, and no crate outside the CLI depends on Windows resource
or process-hosting details unnecessarily.

### [ ] 1.2 Repair the Facet revision and adapt the workspace quality gate

**Work:** Align the direct Facet/figue dependencies with the revision used by
teamy-cancellation, or replace only the incompatible template integration while
preserving the CLI's public behavior. Adapt check-all.ps1 to run nightly
formatting, workspace Clippy with -D warnings, workspace build, and workspace
tests. Keep build.rs, rustfmt.toml, clippy.toml, cancellation, structured
logging, and Windows resource handling scoped to the CLI package.

**Validation:**

~~~powershell
./check-all.ps1
cargo tree -d
~~~

**Completion criteria:** The full quality gate passes from a fresh clone and
the lockfile contains one compatible Facet family for the CLI.

### [ ] 1.3 Establish renderer-free core and fixture layout

**Work:** Add the core crate's public session/state types, fixture directories,
bounded limits, and a minimal deterministic test without importing Vulkan,
window, CUDA, Minecraft, or process-hosting dependencies.

**Validation:**

~~~powershell
cargo test -p teamy-terminal-core
cargo tree -p teamy-terminal-core
~~~

**Completion criteria:** The core crate builds and tests independently, and its
dependency tree contains no renderer/window/process-hosting dependency.

## Phase 2 — Terminal core and headless proof [ ]

### [ ] 2.1 Define the terminal state and input contract

**Work:** Implement bounded columns/rows/scrollback, cells and styles, cursor,
selection, damage, deterministic resize, text/key/mouse events, prompt and
command-range metadata, and snapshot/replay serialization. Keep process
hosting outside the core.

**Validation:**

~~~powershell
cargo test -p teamy-terminal-core -- --nocapture
~~~

**Completion criteria:** Unit tests cover ordinary text, VT/control input,
cursor/style transitions, selection, resize, damage, malformed input, bounds,
and deterministic snapshot equality.

### [ ] 2.2 Prove bounded PowerShell 1..1000 process input

**Work:** Add the CLI/process adapter that prefers pwsh.exe -NoProfile and
documents Windows PowerShell fallback. Bound command text, environment,
working directory, output bytes, rows, timeout, and process lifetime. This is
only a harmless smoke proof, not an unrestricted Java or Rust shell API.

**Validation:**

~~~powershell
cargo run -p teamy-terminal-cli -- headless-smoke --count 1000
~~~

**Completion criteria:** The captured rows are exactly 1 through 1000 in order,
the final cursor/snapshot is recorded, bounds are enforced, and a missing
executable produces an actionable diagnostic.

### [ ] 2.3 Replay the proof without launching PowerShell

**Work:** Persist a deterministic transcript/snapshot fixture and replay it
through the same core API. Include cancellation, process exit, malformed
output, and scrollback-limit cases.

**Validation:**

~~~powershell
cargo test -p teamy-terminal-cli replay -- --nocapture
~~~

**Completion criteria:** Replay reproduces rows, cursor, scrollback, damage,
and fixture hash without a process, and bounded failure cases are asserted.

## Phase 3 — Font and Vulkan tracks [ ]

These are intentionally parallel: the renderer may use a temporary fake glyph
source while the font seam is finalized. Each track must cite the source seam
it reused and must not edit the other track's public contract silently.

### [ ] 3.1 Extract the terminal font crate

**Work:** Extract only glyph shaping/rasterization inputs, metrics, cell
placement, style/color mapping, and deterministic atlas or glyph-instance
output from Teamy Studio. Exclude application panels, window chrome,
CUDA/Burn, and unrelated workspace models.

**Validation:**

~~~powershell
cargo test -p teamy-terminal-font
~~~

**Completion criteria:** A deterministic glyph/atlas fixture and one
renderer-consumable text frame pass without Teamy Studio dependencies.

### [ ] 3.2 Build the ash Vulkan presentation boundary

**Work:** Use the Ash checkout and cursor-latency seam to load an entry,
create instance/device/queue/surface/swapchain, render terminal cells, handle
resize and synchronization, and isolate unsafe code. First support Windows;
return actionable loader/device/validation errors when unavailable.

**Validation:**

~~~powershell
cargo test -p teamy-terminal-renderer
cargo run -p teamy-terminal-renderer -- --diagnose-vulkan
~~~

**Completion criteria:** A usable Vulkan device produces a windowed proof, an
unavailable device produces a diagnostic, and no DirectX/CUDA dependency enters
the renderer or core.

### [ ] 3.3 Add off-screen readback and portable frame encoding

**Work:** Add an off-screen target and CPU-visible readback independent of a
presentable window. Expose sequence, dimensions, stride, format, full-frame or
dirty-tile kind, compression, and bytes. PNG is the archive/keyframe path;
raw or losslessly compressed tiles are the live path.

**Validation:**

~~~powershell
cargo run -p teamy-terminal-cli -- render-fixture --format png
cargo test -p teamy-terminal-renderer frame -- --nocapture
~~~

**Completion criteria:** A deterministic PNG and bounded frame bytes can be
validated without Minecraft or a native GPU shared handle; resize and dirty
region behavior are tested.

## Phase 4 — Optional SFM integration [ ]

### [ ] 4.1 Integrate a local core/frame path without runtime coupling

**Work:** Add a local-only Cargo configuration override in the SFM CLI for
G:\Programming\Repos\teamy-terminal. Depend first on core and the portable
frame/protocol crate only. Missing checkout or renderer must produce a clear
development diagnostic and must not break Java-local SFM behavior.

**Validation:** From canonical 1.19.2, use repository-approved commands:

~~~powershell
sfm-propagate-changes.exe run compile
sfm-propagate-changes.exe test run
~~~

**Completion criteria:** The local override is absent from committed canonical
source, the CLI compiles with the checkout present, and Java-local terminal
tests remain valid with it absent.

### [ ] 4.2 Replace the local override with a reviewed pin

**Work:** After the core/frame API is reviewed, publish or pin an exact git
revision. Record lockfile hashes and source provenance; do not propagate an
absolute path or unstable branch reference to later Minecraft versions.

**Validation:**

~~~powershell
cargo tree --locked
sfm-propagate-changes.exe run compile
sfm-propagate-changes.exe test run
~~~

**Completion criteria:** A fresh SFM checkout resolves the pinned artifact and
canonical compile/tests pass before any version propagation.

### [ ] 4.3 Prove the optional frame consumer boundary

**Work:** Coordinate with the SFM Vox plan to consume portable frame data while
Java owns texture upload, clipping, GUI-scale/layout, stale-frame rejection,
and fallback. Rust never sends a Minecraft panel or arbitrary UI instruction.

**Validation:** Run the existing SFM puppet/report flow for Java-local and,
when the bridge is available, Rust-backed mode; include full-screen, nested,
narrow, and supported GUI-scale captures.

**Completion criteria:** The Java-local mode remains useful with no Rust process;
the optional mode proves input round-trip, resize, disconnect/stale-frame
handling, and fallback without native GPU-handle interop.

## Phase 5 — Release and handoff [ ]

### [ ] 5.1 Complete the support matrix and evidence bundle

**Work:** Record exact evidence for each advertised target and explicitly list
unsupported targets. Include fresh-clone commands, core fixtures, renderer
diagnostics, frame artifacts, and SFM proof links.

**Validation:**

~~~powershell
./check-all.ps1
git status --short
~~~

**Completion criteria:** Every supported target has the required proof and no
unsupported target is described as working by implication.

### [ ] 5.2 Update documentation, changelog, and propagation records

**Work:** Keep this plan and its repository mirror synchronized. Update SFM's
gameplay changelog when an in-game surface changes, run the baseline-first
propagation/audit workflow, and record commit IDs and intentional exclusions.

**Validation:**

~~~powershell
cargo run -- audit --branch core --version-surfaces
sfm-propagate-changes.exe git status
~~~

**Completion criteria:** Public contract, README, plan mirror, SFM changelog,
and propagation/audit evidence agree; no later target was overwritten by
baseline code.

## Acceptance matrix

| Target/dialect | Support status | Required validation | Evidence |
| --- | --- | --- | --- |
| Windows x64 core/CLI | Supported first | workspace quality gate, headless smoke, replay | Pending Phase 1–2 |
| Windows Vulkan device | Supported when loader/device exists | renderer diagnostic plus window/off-screen proof | Pending Phase 3 |
| Windows without Vulkan/GPU | Supported for core/CLI; renderer degraded | core/CLI pass; actionable renderer error | Pending Phase 3 |
| Linux/macOS renderer | Not in first support slice | Do not claim support; later portability gate | Pending future phase |
| SFM Java-local terminal | Required independently of this repo | SFM Java tests and puppet | Tracked in Vox plan |
| SFM optional Rust/Vox mode | Optional development tooling | pinned dependency, frame/bridge puppet proof | Pending Phase 4 |

## Overall completion criteria

- [ ] All phase work items are [x] with local evidence.
- [ ] A fresh clone passes the workspace quality gate and exposes help/version.
- [ ] Core headless 1..1000 and process-free replay proofs are deterministic
  and bounded.
- [ ] Font extraction and Vulkan/off-screen output have deterministic fixtures.
- [ ] Portable frame data is validated without native GPU-handle interop.
- [ ] SFM integration is optional, pinned, and never an absolute path in
  canonical or propagated source.
- [ ] Java-local SFM functionality remains self-sufficient when Rust is absent.
- [ ] README, plan mirror, changelog, support matrix, and propagation evidence
  agree with the shipped behavior.

## Risk register

| Risk | Guardrail/mitigation | Gate |
| --- | --- | --- |
| Facet revision skew makes the template unbuildable | Align the direct/transitive revision or isolate compatible CLI dependencies; keep lockfile evidence | Phase 1.2 |
| Teamy Studio/CUDA/DirectX leaks into the portable project | Inspect dependency trees; keep core and renderer crates independent; reject unrelated workspace copies | Phase 1.3, 3.2 |
| Vulkan loader/device is unavailable | Off-screen/software attempt plus actionable diagnostics; core/CLI never require Vulkan | Phase 3.2–3.3 |
| PowerShell smoke becomes an unsafe shell surface | Fixed -NoProfile fixture, explicit bounds, timeout, environment, and no arbitrary command API | Phase 2.2 |
| Absolute local path leaks into SFM or later versions | Cargo config override only; inspect diff/lockfile; baseline-first propagation audit | Phase 4.1–4.2 |
| Font extraction couples the app to Teamy Studio | Renderer-neutral fixture and dependency-tree check before integration | Phase 3.1 |
| PNG-per-keystroke causes latency or CPU pressure | PNG only for keyframes/artifacts; dirty-tile/raw frame path for live updates | Phase 3.3–4.3 |
| Review mistakes a smoke test for runtime wiring | Pair unit/fixture checks with CLI, renderer, and SFM puppet evidence at each boundary | All phases |

The colour-picker bridge remains deferred. The next safe implementation slice
is the workspace conversion and dependency repair, followed by the core
headless proof.
