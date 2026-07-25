# Teamy Terminal Repository and Vulkan Renderer Plan

This plan records the corrected project name: **`teamy-terminal`**. The
voice transcription that called it `teeny-terminal` was an error.

The terminal core and renderer should move out of Teamy Studio into a small
public MPL-2.0 repository owned by `TeamDman`. Teamy Studio currently contains
unrelated application, ML/PyTorch, CUDA/Burn, and windowing work. Those things
should not be required to build, test, or embed the terminal.

The reference for the first Vulkan renderer is
`G:\Programming\Repos\cursor-latency` at revision `6c07705` (`Fix transparent
Vulkan window`). It already uses `ash 0.38`, `ash-window 0.13`, `winit 0.30`,
`shaderc`, `raw-window-handle`, and a direct Vulkan instance/device/swapchain
setup. Reuse its proven seams deliberately; do not copy its entire
application.

The upstream Ash checkout at `G:\Programming\Repos\ash` is also available as
the API and example reference. Its `ash` crate is `0.38.0+1.4.352` and the
workspace includes `ash-examples` and `ash-window`. Use it to verify loader,
instance/device, extension, swapchain, synchronization, pointer-chain, and
validation-layer conventions against the actual low-level API rather than
relying only on the cursor-latency application code. The Ash repository is a
reference checkout, not a source tree to vendor wholesale: `teamy-terminal`
should consume the reviewed crates from crates.io/git with a locked revision
and keep its own narrow renderer boundary and error diagnostics.

The two references have complementary roles:

- `ash` and `ash-examples` answer “what does the raw Vulkan API and its safety
  boundary require?”
- `cursor-latency` answers “how has this machine already assembled a working
  `ash`/`ash-window`/`winit` surface, including the transparent-window seam?”

The Vulkan worktree should cite which example or application seam informed a
change, and its acceptance evidence must still include a headless/off-screen
path so a missing window, loader, or GPU does not make the terminal core or
CLI unusable.

## Initialization from `teamy-rust-cli`

The repository should be initialized from the maintained single-crate
template at `G:\Programming\Repos\teamy-rust-cli`, rather than assembled from
an empty Cargo project or copied from Teamy Studio. The template is a source
of repository conventions and quality gates, not a dependency of the finished
terminal workspace.

After cloning the empty public repository, run the template's initializer from
the template checkout against the new destination (or use the equivalent
reviewed scaffold operation):

```powershell
Push-Location G:\Programming\Repos\teamy-rust-cli
cargo run -- init G:\Programming\Repos\teamy-terminal
Pop-Location
```

The initializer deliberately excludes `.git`, `target`, the template's
initialization skill, and the legacy `init-other-repo.ps1`; it preserves an
existing destination `README.md` and `LICENSE`. Inspect the generated diff
before committing and replace every template placeholder: package name and
URLs, README/examples, environment variable names, implementation source URL,
and the top-level CLI description. Do not copy Teamy Studio's application,
PyTorch, CUDA, Burn, or DirectX workspace into the new repository.

The generated single package is then converted into the planned workspace.
Keep the template's repository-level files and conventions where they remain
useful, but move product code into the workspace crates below. The root
workspace should set a default member for `teamy-terminal-cli` so the familiar
`cargo run -- --help` and `cargo run -- --version` smoke commands continue to
work even though the implementation is split across crates. The CLI may start
with no product subcommands; Figue's built-in help/version/completion surface
is still required and must not be removed while the terminal commands are
being designed.

### Conventions to retain from the template

- Figue/Facet argument definitions and the generated built-in `--help` and
  `--version` behavior, including repository, branch, revision, worktree, and
  build-time metadata.
- `color-eyre` error context, structured tracing/logging, cooperative
  cancellation, and Windows console/ANSI handling where applicable.
- The explicit Rust and Clippy lint policy in `Cargo.toml`; warnings remain
  visible and the quality gate continues to run with `-D warnings`.
- `build.rs` metadata/resource hooks, `rustfmt.toml`, `clippy.toml`, the MPL-2.0
  license, and the template's repository documentation pattern.
- The template's CLI round-trip/fuzz-test approach, adapted so workspace
  tests cover the real terminal commands and fixtures rather than the example
  `cache`, `home`, and `init` commands.

`check-all.ps1` must be adapted from the template's single-package commands to
explicit workspace validation while preserving its intent: nightly formatting,
all-workspace/all-target Clippy with warnings denied, an all-feature build, and
tests for every workspace member. Any Windows resource step that is not needed
by a library crate belongs only to the CLI package. The template's `init`
subcommand and example command groups are bootstrap aids and should be removed
or replaced after the first workspace commit; they must not become accidental
terminal product API.

## Repository bootstrap

The public repository now exists at
`https://github.com/TeamDman/teamy-terminal`, with an MPL-2.0 license, and the
working clone is `G:\Programming\Repos\teamy-terminal`. The repository plan is
mirrored into that checkout under `docs/tasks` so the implementation and its
design record travel together.

The intended initializer command is still:

```text
gh repo create TeamDman/teamy-terminal --public --license MPL-2.0 \
  --description "Portable terminal core and Vulkan renderer"
git clone https://github.com/TeamDman/teamy-terminal.git \
  G:\Programming\Repos\teamy-terminal
```

During this bootstrap the normal `cargo run -- init` path could not compile
the template because its direct Facet pin and the Facet revision selected by
`teamy-cancellation` were different, making `StopAfterArgs` fail its `Facet`
bound. The template's compatibility initializer was therefore used for this
first copy; it applies the same exclusions and preserve-existing-license rules
without changing the template repository. Resolving that dependency skew and
returning to the normal initializer remains a template-maintenance follow-up,
not a reason to import unrelated Teamy Studio dependencies here.

The initial commit should contain the MPL-2.0 license, README, contribution
and development notes, a Cargo workspace, and a passing headless test. The
repository must not begin by importing the Teamy Studio workspace or its
PyTorch/CUDA dependencies.

## Cargo workspace shape

Start with a deliberately small workspace:

```text
teamy-terminal/
  Cargo.toml
  crates/
    teamy-terminal-core/
    teamy-terminal-font/
    teamy-terminal-renderer/
    teamy-terminal-cli/
  fixtures/
  docs/
```

- `teamy-terminal-core` is renderer-free and owns terminal semantics.
- `teamy-terminal-font` owns the Teamy font/rasterization algorithm once the
  extraction seam is clear. It may begin as a renderer-internal module, but a
  separate crate is preferred so the core never depends on graphics.
- `teamy-terminal-renderer` owns Vulkan presentation, off-screen rendering,
  readback, frame encoding, and the windowed demo. It uses `ash` directly,
  with `ash-window`/`winit` only at the platform/window boundary.
- `teamy-terminal-cli` owns headless replay, PowerShell process plumbing,
  PNG/frame artifacts, and small diagnostics. It is not part of the core
  runtime dependency graph.

The workspace should use the repository's normal Rust patterns: edition 2024,
workspace package metadata, locked dependencies, `check-all.ps1`, explicit
error context, focused fixtures, and tests that can run without a visible
window or GPU. Renderer `unsafe` code must be isolated at the Vulkan boundary.

## Terminal core

The core is the primary product. It must be usable on a headless machine and
must not depend on `ash`, `winit`, DirectX, CUDA, PyTorch, or Minecraft.

The first stable seam should cover:

- create a session with columns, rows, scrollback, and bounded limits;
- apply terminal output bytes and mutate screen/cursor/style state;
- expose visible rows/cells, cursor, selection, scrollback, and damage;
- resize deterministically;
- encode key/text/mouse events without platform-window assumptions;
- expose prompt/command ranges and future semantic handles as metadata; and
- snapshot and replay state through deterministic fixtures.

The core should distinguish terminal semantics from process hosting. A shell or
PowerShell adapter may feed bytes into the core, but the core itself should be
testable by applying fixture bytes directly.

### Harmless PowerShell smoke

The first process-backed smoke should run PowerShell without profiles and emit
one predictable value per row, for example `1..1000`. The exact executable
selection (`pwsh.exe` first, Windows PowerShell fallback where available),
working directory, timeout, environment, and output bound must be explicit.

The smoke is not a security boundary for arbitrary commands. It exists to
prove that process output reaches the core, rows are preserved, and the
headless runner can produce a bounded transcript and snapshot.

Required headless evidence:

- `pwsh.exe -NoProfile` (or documented fallback) starts and exits cleanly;
- rows 1 through 1000 are observed in order;
- the core snapshot contains the expected final rows and cursor state;
- output and scrollback bounds are enforced; and
- the same fixture can replay without launching PowerShell.

## Vulkan renderer

The renderer should be a thin Vulkan presentation layer, not another terminal
engine. The first renderer is Windows-focused but should avoid DirectX-specific
types so that the platform boundary remains portable.

### Initial Vulkan surface

- Load Vulkan through `ash::Entry`.
- Use `ash-window` and `raw-window-handle` only to create a platform surface.
- Select a physical device with graphics and presentation support.
- Create a swapchain, render pass/pipeline, synchronization, and resize path.
- Render font glyphs and terminal cells from a core snapshot.
- Support an off-screen target and CPU-visible readback independent of a
  presentable window.
- Encode PNG snapshots and bounded raw/compressed RGBA frames for consumers
  such as Minecraft.

The first off-screen path should be able to run headlessly or with a software
Vulkan device where available. It must not require CUDA. A native GPU shared
handle is explicitly out of scope for the first cross-process frame protocol.

### Texture/frame output

The renderer should expose a renderer-neutral frame result containing sequence,
dimensions, stride, format, full-frame/dirty-tile kind, and bytes. PNG is for
artifacts/keyframes; raw or losslessly compressed dirty tiles are for live
interaction. This output maps directly to the SFM Vox texture presentation
plan without making Minecraft understand Vulkan objects.

## Font rendering extraction

Teamy Studio's existing font algorithm is valuable, but the new repository must
extract only the terminal-relevant implementation:

- glyph shaping/rasterization inputs and deterministic atlas output;
- font metrics and cell placement;
- style/color mapping; and
- a renderer-neutral glyph instance or bitmap representation.

Application panels, Teamy Studio window chrome, CUDA/Burn integrations, and
unrelated workspace models must remain outside `teamy-terminal-font`.

## SFM dependency strategy

The SFM CLI should consume the new project in two stages:

1. **Iteration:** use a local path override to the checkout at
   `G:\Programming\Repos\teamy-terminal`. The override must be local-only or
   branch-specific and never become an absolute path committed into the
   canonical SFM source or propagated to other Minecraft versions. A Cargo
   config patch is preferred over a permanent absolute path in the manifest.
2. **Stabilization:** publish a reviewed `teamy-terminal` version and replace
   the local override with an exact git revision or pinned registry version,
   including lockfile hashes and source provenance in the existing SFM
   dependency workflow.

The first SFM integration should depend only on `teamy-terminal-core` and the
portable frame/protocol crate. Vulkan and the renderer remain optional
development tooling; ordinary SFM gameplay and Java-local terminal fallback
must not require a GPU, Vulkan loader, or Rust process.

The CLI integration should be feature-gated and capability-aware. A missing
local checkout or unavailable Rust renderer must produce a clear development
tooling diagnostic, not break unrelated `sfm-propagate-changes` commands.

## Parallel subagent plan

After the repository is created and the initial workspace commit exists, use
isolated worktrees under `G:\Programming\Repos\teamy-terminal-worktrees\`:

| Track | Scope | Reviewable result |
| --- | --- | --- |
| Core | `teamy-terminal-core` semantics, replay fixtures, bounded PowerShell smoke | Headless `1..1000` proof, snapshots, parser/resize/key tests, no renderer dependencies |
| Vulkan | `teamy-terminal-renderer` from the cursor-latency `ash`/`winit` seam | Windowed and off-screen Vulkan proof, resize, PNG/raw frame output, device/loader diagnostics |
| Font | Extract the Teamy font algorithm into `teamy-terminal-font` | Deterministic glyph/atlas fixture and one renderer-consumable text frame |
| Integration | Coordinator-owned SFM path override and later pinned dependency | CLI compiles with local checkout, then lockfile-pinned artifact; no version-branch propagation until stable |

Core and font can begin in parallel after the workspace bootstrap. The Vulkan
agent can use a temporary fake glyph source while the font seam settles. The
SFM integration agent must wait for the core crate's first stable API and must
not add a permanent path dependency before the repository has a commit to
reference.

## Acceptance gates

### Repository gate

- Public `TeamDman/teamy-terminal` exists with MPL-2.0 metadata.
- Fresh clone works on the supported Windows development machine.
- `cargo fmt --all -- --check`, Clippy with warnings denied, build, and tests
  pass without Teamy Studio, PyTorch, CUDA, or Minecraft.

### Core gate

- Headless PowerShell `1..1000` smoke passes with `-NoProfile`.
- Replay fixture reproduces the same rows, cursor, scrollback, and damage.
- Bounds, malformed output, resize, cancellation, and process exit are tested.

### Renderer gate

- `ash` Vulkan window starts or fails with an actionable loader/device reason.
- Off-screen render produces a deterministic PNG and frame bytes.
- Resize and dirty-region behavior are covered.
- No DirectX handle, CUDA runtime, or Teamy Studio application dependency is
  required.

### SFM gate

- Java-local terminal remains functional with no Rust checkout or renderer.
- The CLI's local path override is isolated and documented.
- A pinned published dependency is used only after the API and artifact are
  reviewed; then canonical compile/test and puppet proof run before any
  propagation to later Minecraft branches.

## Immediate next steps

1. Convert the template-derived single package into the planned MPL-2.0
   workspace and commit the placeholder audit plus adapted quality gate.
2. Record that workspace commit and create the three isolated worktrees.
3. Dispatch core, Vulkan, and font agents with the gates above.
4. Review the core headless `1..1000` proof before connecting SFM.
5. Integrate the local core path into `sfm-propagate-changes` behind a
   development-only feature, then replace it with a pinned dependency after
   stabilization.
6. Add the SFM Java-local terminal puppet and later the optional Vox texture
   mode using the renderer-neutral frame output.

The colour-picker bridge remains deferred. This repository work is the
development-tooling foundation that lets us pursue the terminal without
dragging Teamy Studio's unrelated CUDA/ML surface into either SFM or the new
terminal project.
