# Java symbol analysis

`sfm-propagate-changes` provides read-only Java definition and usage queries
for SFM worktrees, lockfile-declared dependency sources, and tiny isolated
source scenarios. Queries do not invoke Gradle, javac, dependency acquisition,
or source mutation. The explicit `symbol index refresh` lifecycle command may
acquire preferred lockfile-declared source providers through the same Rust APIs
used by `dependency source acquire`; it still does not invoke Gradle or a shell.

## Commands

```powershell
sfm-propagate-changes.exe symbol show-definition ca.teamdman.sfm.SFM --branch 1.19.2
sfm-propagate-changes.exe symbol show-definition DiskItem --branch 1.19.2
sfm-propagate-changes.exe symbol list-usages ca.teamdman.sfm.SFM --branch 1.19.2
sfm-propagate-changes.exe symbol list-usage ca.teamdman.sfm.SFM --branch 1.19.2
sfm-propagate-changes.exe symbol list --branch 1.19.2
sfm-propagate-changes.exe symbol list 'ca.teamdman.sfm.* run*' --branch 1.19.2
sfm-propagate-changes.exe --output-format json symbol list-usages ca.teamdman.sfm.SomeType 'method(Ljava/lang/String;)V' --branch 1.19.2
sfm-propagate-changes.exe symbol index show --branch 1.19.2
sfm-propagate-changes.exe symbol index refresh --branch 1.19.2
sfm-propagate-changes.exe symbol show-definition net.minecraft.client.gui.components.MultiLineEditBox --branch 1.19.2
```

## Definition at an editor location

`show-definition` also accepts an exact document location. This is mutually
exclusive with its positional selector form:

```powershell
sfm-propagate-changes.exe --output-format json symbol show-definition `
  --source-path ca/teamdman/sfm/common/item/DiskItem.java `
  --line 43 `
  --column 14 `
  --branch 1.19.2
```

`--source-path` is relative to one selected source root. If the same relative
path exists in more than one root, pass `--source-root-id <id>` rather than
letting filesystem order choose. Direct CLI mode reads the current file from
that declared root. The shared engine and worker additionally accept exact
caller-supplied text as a request-scoped in-memory overlay; they record both
overlay and disk hashes and never write the overlay to the source tree.

The request schema is `sfm.definition-at-position-request/2`. It carries the
root id, root-relative/report path, source set, typed address, exact text and
content hash, optional disk hash, one-based Unicode-scalar row/column, derived
UTF-8 byte offset, branch/classpath/source-root visibility, parser/dependency
index/workspace fingerprints, provider-origin request generation, and global
workspace generation. A request generation correlates work from one origin;
same-origin supersession is an explicit cancellation rather than an accidental
global-generation race. Workspace generation is worker-wide and invalidates
older workspace state.

The result schema is `sfm.definition-at-position-result/2`. It preserves
zero/one/many outcomes, completeness, canonical symbol ids, confidence,
root-authoritative definition spans, source/index fingerprints, diagnostics,
and typed recovery. Stale source, invalid position, unavailable roots/indexes,
ambiguity, and an incomplete dependency index do not collapse into a guessed
match or authoritative no-match. A successful result can therefore still exit
with status 5 when its dependency coverage is explicitly incomplete.

## Warm symbol worker and Minecraft consumer

Start the reusable worker with:

```powershell
sfm-propagate-changes.exe symbol serve --branch 1.19.2
```

The worker is an API process, not a line-oriented interactive command. Stdout
is reserved for `[u32 little-endian payload length][UTF-8 JSON payload]` frames;
logs go to stderr or `--log-file`. The `sfm.symbol-server/1` handshake checks
frame schemas, capabilities, maximum frame size, pending-definition limit, and
workspace identity before work is accepted. Versioned frames support
definition, cancellation, workspace-generation update, ping, shutdown, and
typed fatal/nonfatal errors. Length framing keeps embedded newline, NUL, and
Unicode source text unambiguous.

Within one worker lifetime, exact ordered source-content identities key a
bounded immutable fact cache and at most two derived definition-resolution
surfaces. Every query still reads and hashes the selected sources, so edits made
outside the requesting editor invalidate reuse. A cache hit parses/walks only
the target document against the retained resolution surface instead of
relinking the workspace or re-expanding dependency declarations. A cancelled
or failed rebuild is never published, and no live cache persists after exit.

Minecraft consumes this protocol through `SFMSymbolNavigationProvider`, not by
parsing Java in a screen. `SFMSymbolServerSupervisor` launches the branch-
configured command above, defaults to `sfm-propagate-changes.exe`, and permits
an executable override through `-Dsfm.symbol.workerExecutable=<path>`. Its
defaults are a 10-second handshake timeout, 3-second request timeout, 2-second
shutdown grace, 16 MiB frames, eight pending definitions, and sixteen pending
controls. State, frame I/O, timers, and process reaping run on dedicated daemon
threads; the render thread never waits for the process. The lifecycle is
`STOPPED -> STARTING -> READY -> STOPPING -> CLOSED`, with typed unavailable,
timeout, protocol, stale, crash/restart, and recovery behavior. F12, Alt+Enter,
result selection, and panel navigation remain a later UI slice.

Source text necessarily appears in an explicit definition request frame. It is
not included in default logs or telemetry. Normal telemetry contains request/
provider/root ids, hashes, counts, cache state, durations, and outcomes rather
than source text or absolute workspace paths.

### Installed 1.19.2 evidence

The installed executable identified itself as revision `c32607d2f`, built
2026-08-15. Its cold `DiskItem` request took 3,219.436 ms. A following set of
24 warm requests rotated through main `DiskItem`, gametest
`SFMGameTestHelper`, and dependency `BlockPos` locations and measured:

- 72.001 ms median;
- 103.068 ms p95;
- 107.290 ms maximum;
- 501,850,112 bytes peak working set;
- one observed descendant process and zero leaked descendants;
- typed cancellation acknowledgements and acknowledged clean shutdown.

These values pass the unchanged acceptance limits of 250 ms median, 750 ms
p95, and less than one second maximum. The first warm implementation took about
1.8 seconds even with 1,504 fact-cache hits: telemetry showed that every query
relinked the workspace and re-expanded 117,694 dependency definitions. The
retained immutable resolution surface fixes that measured stage instead of
weakening the limit. The complete machine-readable run, including all sample
and stage telemetry, is
`docs/architecture/evidence/symbol-server-installed-probe-1.19.2.json`.

## Live definition-query performance baseline

Phase 0.9 uses the real installed command above as its acceptance benchmark.
On 2026-08-10, the 1.19.2 workspace contained 1,318 live Java source files.
The Phase 0.8 implementation split those files into 42 shards of at most 32
files and ran every shard serially through a `Types` pass, then every shard
again through a `Members` pass. A measured release-build invocation took 9,573
ms; user-observed invocations were approximately 11 seconds. Structured log
timestamps placed the last type worker about 5,133 ms after query start and the
last member worker about 9,440 ms after start.

Live branch queries now emit `SFM_JAVA_LIVE_QUERY_STAGE` tracing events on
stderr for context resolution, lock acquisition, source/index probing,
workspace-vocabulary collection, dependency scan, live-index construction,
live-index scan, and merge. These measurements never enter Facet text/JSON/CSV
stdout.

Phase 0.9 replaced the live `show-definition` route with four balanced,
short-lived fact workers on Windows. Each selected source is read and parsed
exactly once into a versioned `JavaFileFacts` record containing its stable
sequence/hash/source-set identity, package/import scope, type declarations,
raw member signatures, references, and parse diagnostics. Completed shards are
validated and admitted to an incremental linker immediately; later type
declarations wake only the pending member candidates that can depend on them.
`seal()` is the sole completeness barrier and decides missing/duplicate files,
remaining resolution failures, canonical ordering, ambiguity, and uniqueness.
No type/member intermediate index is written or reread, and no live-source
cache, watcher, or daemon survives the command.

The initial worker partition is one balanced shard per available worker rather
than fixed 32-file process batches. A failed multi-file shard is bisected at a
deterministic midpoint and requeued through the same bound. On Windows, all
children share a kill-on-close Job Object with an active-process limit of four,
a 1.5 GiB per-process limit, and a 6 GiB aggregate worker-memory limit. Tests
query those configured limits through Win32. Cancellation and infrastructure
failures stop scheduling, terminate the shared job, kill any remaining child,
and wait for every owned process; cleanup failures are attached to the original
error. Platforms without an equivalent aggregate-memory primitive use one
worker. The coordinator is not retroactively assigned the refresh command's
1.5 GiB parent Job Object: doing so after live-query state existed caused a
reproducible Windows access-violation failure. It instead retains bounded child
outputs and the machine-wide live-query lock; an independent coordinator hard
cap remains future work if it can be installed safely at process creation.

The installed release build at
`G:\Programming\Caches\CARGO_HOME\bin\sfm-propagate-changes.exe` produced the
following warm `DiskItem` wall times on 2026-08-10 against the unchanged 1.19.2
workspace and dependency index: 2,237, 2,163, 2,128, 2,101, and 2,088 ms. The
median was 2,128 ms and the maximum was 2,237 ms. Every run returned expected
status 5 because dependency coverage was explicitly partial. One traced
installed run took 2,192 ms: context resolution 113 ms, source/index probing
232 ms, dependency scan 653 ms, live-index build 954 ms, live-index scan 34 ms,
and merge 3 ms. Its four worker ranges were 330, 330, 330, and 328 files;
peak concurrency was four, the first facts were admitted after 545 ms while
three workers remained active, and fan-out/link/seal completed after 934 ms.

The retained Phase 0.8 developer oracle and the production path emitted
byte-identical JSON for both `DiskItem` and its fully described
`getProgramString(ItemStack)` method. Their SHA-256 values were respectively
`89816f6671b9373f3b4903c5ccdc5a7f89051e471278c848aa8ed55bc85a2edb`
and `dfff11ad1bd089bf14f19f161f9e105d3ebb1ac2dd5dea48dae843c232d68fe7`.
Definition-report fingerprints cover the canonical definitions and diagnostics
the query exposes. The suppression notice intentionally says that unrelated
diagnostics were omitted without embedding their implementation-dependent
count, so changing bounded internal collection does not change an otherwise
identical query result.

The selector grammar follows Access Transformer targets, but the class owner
may also be an unambiguous qualified suffix:

- `package.Type` or `Type` selects a class, interface, enum, record, or annotation.
- `package.Type FIELD` or `Type FIELD` selects a field.
- `package.Type method(JVMDescriptor)ReturnDescriptor` or
  `Type method(JVMDescriptor)ReturnDescriptor` selects one method
  overload, for example `example.A run(Ljava/lang/String;)V`.
- `$` identifies nested classes, for example `example.Outer$Inner`.

Exact owner matches take precedence. If there is no exact owner, the query
matches owners ending at a package (`.`) or nested-class (`$`) boundary. Thus
`DiskItem` can resolve `ca.teamdman.sfm.common.item.DiskItem`, while multiple
visible `DiskItem` declarations produce the ordinary typed `ambiguous` outcome
instead of selecting one by guesswork. Partially qualified suffixes such as
`common.item.DiskItem` work by the same rule.

`symbol list` emits the exact selector for every declaration. Its optional
pattern is a case-sensitive glob over that complete selector: `*` matches zero
or more characters and `?` matches exactly one character. Every other
character is literal, including `$`, descriptor punctuation, and brackets.
Omitting the pattern is equivalent to `*`. A listed selector can therefore be
copied unchanged into `show-definition`, `list-usages`, `rename`, or `move`.

Dotted-member aliases and descriptor-free method guesses are intentionally not
accepted. A zero-match result exits with status 2, an ambiguous result exits
with status 3, and both still emit their typed report. Successful queries exit
with status 0.

## Dependency index lifecycle

Dependency definitions and usages are prepared offline while SFM source files
are parsed live for every query. This keeps ordinary edits immediately visible
without maintaining a mutable SFM-source cache, while lockfile-pinned Minecraft,
loader, and mod sources can be reused between queries.

```powershell
# Read-only: report ready, missing, stale, or partial state and concrete paths.
sfm-propagate-changes.exe symbol index show --branch 1.19.2

# Acquire any required preferred source providers, rebuild, validate, and
# atomically publish the immutable dependency index.
sfm-propagate-changes.exe symbol index refresh --branch 1.19.2
```

The semantic identity covers the effective dependency lock, preferred source
providers and their derived checks, Minecraft/Java context, parser fingerprint,
and index format. It excludes machine-local worktree/cache paths and lockfile
formatting. A semantic change therefore reports the old index as stale and
prints an exact typed refresh recommendation. `index show` never acquires or
writes anything.

The v3 store contains a typed `manifest.json` and authenticated
`payload.ndjson` beneath `$sfm-cache/symbol-index/v3/<identity>/`. Its routed
stream schema (`sfm.dependency-java-symbol-index-stream/2`) prefixes each typed
definition or usage body with compact identity fields. A query scans those
fields with one reusable byte buffer and Facet-decodes only matching bodies;
unrelated dependency records are never materialized merely to filter them.
Refresh uses a scoped lock, bounded short-lived parser workers, streaming hash
validation, and atomic publication. Cancellation or failure cannot expose a
partial index or replace the previous valid artifact.

Live `show-definition` queries use the one-parse fact/link path described
above. `symbol list-usages`, dependency-index refresh, and persistent
dependency payload construction deliberately retain the Phase 0.8 sharded
passes for now; migrating those routes is follow-up work, not an implied part
of the completed latency slice. One cancellable cache-scoped `live-query.lock`
continues to serialize memory-intensive branch queries across top-level CLI
processes. This boundary is machine-wide by design: per-process limits alone
do not stop two independent invocations from exhausting memory in aggregate.
A concurrent branch query waits and proceeds after the first releases the
lock. Isolated tiny-source queries do not acquire it.

Branch queries load only an index whose identity and payload hash match the
current toolchain. Missing, stale, or partial coverage still returns any live
SFM matches, but marks the report `completeness: incomplete` and exits with
status 5 rather than presenting a false authoritative no-match. Isolated custom
roots do not require the branch dependency index.

`index show` can itself exit with status 5 when its typed status is `partial`;
this is usable indexed evidence with explicitly incomplete dependency-source
coverage, not a corrupt cache. The JSON report and manifest contain the exact
identity, source/definition/usage/diagnostic counts, refresh duration, payload
size/hash, missing source inputs, and typed refresh/acquisition recommendations.

## Source and classpath modes

Without `--source-root`, the selected branch contributes every configured Java
source set and generated Java root. Source-set visibility remains directed:
production `main` cannot see test declarations, while `test`, `gametest`, and
`datagen` can see `main`. Version and inactive-feature exclusions use the same
inputs as the Rust build toolchain.

Custom roots are repeatable and require an explicit classpath mode:

```powershell
sfm-propagate-changes.exe symbol show-definition example.A `
  --branch 1.19.2 `
  --source-root source `
  --classpath-mode isolated
```

- `branch` retains the selected branch's declared dependency context.
- `isolated` includes only the supplied source roots and the Java release
  selected by the branch. It does not read ambient `CLASSPATH`, project class
  outputs, Minecraft, loader, or mod jars.

A custom root ending in `<source-set>/java`, such as `source/main/java` or
`source/test/java`, retains that known source-set identity and the branch's
directed visibility rules. Other custom roots share the isolated `custom`
source set. This convention lets tiny scenarios prove main/test visibility
without creating a Gradle project.

All paths in reports are worktree- or source-root-relative. Reports include
the branch, Minecraft and Java context, roots, source-set visibility,
exclusions, source hashes, parser/index fingerprints, and the selected
classpath mode/fingerprint.

## Output

`--output-format text|json|csv` selects the rendering. The default is text for
an interactive terminal and JSON when stdout is redirected. JSON schemas are
versioned as `sfm.symbol-definition/2`, `sfm.symbol-list/1`,
`sfm.symbol-usage-list/2`, and `sfm.definition-at-position-result/2`.
CSV starts with a report row carrying the outcome/status, selector, workspace
context, and fingerprints, followed by definition/usage and diagnostic rows.
That report row keeps zero-match and ambiguity distinct even when there are no
result rows. Diagnostics and logs remain on stderr; one typed report is emitted
on stdout.

## Analysis scenarios

Read-only scenarios live adjacent to their Rust harness:

```text
platform/cli/sfm-propagate-changes/tests/java_analysis/
  java_analysis_scenario_test.rs
  scenarios/<scenario>/
    command.ps1
    source/
    output-expected.json
    output-actual.json
```

Run them with:

```powershell
Set-Location platform/cli/sfm-propagate-changes
cargo test --all-features --test java_analysis_scenarios
```

The harness parses the single documented CLI command without starting
PowerShell or an installed executable and asserts both the canonical JSON and
the public exit status implied by its typed outcome. When a snapshot differs, it preserves
the ignored adjacent `output-actual.json` and prints the exact `Copy-Item`
command needed for explicit acceptance. It never updates expected output.

## Mutation is deferred

`symbol rename` and `symbol move` parse their future public argument contract,
including exactly one of `--dry-run` or `--apply`, but Phase 0 returns a typed
unsupported report (exit status 4) before source discovery or mutation. No
Phase 0 symbol command writes Java source files.
