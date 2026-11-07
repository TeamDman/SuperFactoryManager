# Linux MCP/Forge dependency cache

This is a new trusted-local cache generation for the reviewed released Forge
recipes for Minecraft 1.19.2, 1.19.4, 1.20 and 1.20.1. Implementation and fixture
coverage do not qualify a real pinned game build. The initial real acceptance
target is plain 4.34/1.19.2, followed by its unchanged repeat and plain 4.35/1.19.2.
Other rows, Windows execution and published environment qualification remain
separate work.

## Commands

Use the explicitly versioned, audited candidate supplied in the handoff. Keep the
existing selected SDK, proxy and approved Java trust configuration. These commands
must not replace the active CLI or remove genuine inherited launch controls to
force a hit. The historical recipes require an explicit verified JBR17 selection;
that does not add a Linux SDK pin to their immutable product locks.

```bash
"$CLI" --output-format json source project compile \
  --repo-root "$REPO" --projection sfm-4.34.0/mc-1.19.2 \
  --released-recipe sfm:released-native-inputs/4.34.0/1.19.2@1 \
  --cache-root "$CACHE" --java-home "$JBR17"

"$CLI" --output-format json source project sources \
  --repo-root "$REPO" --projection sfm-4.34.0/mc-1.19.2 \
  --released-recipe sfm:released-native-inputs/4.34.0/1.19.2@1 \
  --cache-root "$CACHE" --java-home "$JBR17" --layout jar

"$CLI" --output-format json source project sources \
  --repo-root "$REPO" --projection sfm-4.34.0/mc-1.19.2 \
  --released-recipe sfm:released-native-inputs/4.34.0/1.19.2@1 \
  --cache-root "$CACHE" --java-home "$JBR17" --layout filetree
```

Repeat the compile unchanged for warm evidence. Select
`sfm-4.35.0/mc-1.19.2` with the same original recipe for cross-projection evidence;
the frozen owner must still prove the admitted mod-version-only difference. The
commands accept no arbitrary source archive or extraction destination. A source
request may produce missing dependencies, but performs no SFM compilation,
packaging or source-provider admission. Refresh and forced MCP rebuild overrides
are rejected for these immutable Linux generations.

## Authority and layout

The guarded native root contains a separate `forge-linux-v1` namespace. Each
`mcp`, `forge` or `remap` key has a pathname-bound cooperative producer lease,
retained `attempt-*` directories, immutable `entry-*` outputs and a create-only
`current.json`. An entry's `production.json` uses
`sfm:linux_forge_production@1`, identifies its kind, actual-input key and fresh
attempt, and records every role's size and SHA-256. It is local successful
producer evidence; it is not an NFRT receipt or independent attestation.

MCP exports patched sources, merged mappings and the renamed game JAR. Forge
exports the development classes JAR, official and deobfuscated source archives,
and both mapping directions. Each artifact remap records its complete
SpecialSource intermediate and final rewritten JAR together. Both remap archives
retain the complete-entry/CRC checks used by the previous producer path.

Keys bind the reviewed full raw lock, actual selected tool/input bytes, ordered
recipe configuration, transformations, producer implementation, operating system,
architecture, complete SDK image and effective selected Java trust. Invocation
paths, repository/projection names, release labels and SFM source bodies do not
split dependency keys. SDK location is checked separately; its portable image
identity binds the actual SDK bytes, modes and contained links. Original
dependency configuration bindings and order are retained after physical producer
grouping. A changed dependency/tool/SDK/trust/AT/lock gets a distinct key.

Linux project compilation uses a separate `sfm:linux_forge_project@1` namespace
under `projects/<digest>`. It keeps 4.34 and 4.35 project outputs distinct and
preserves the old project cache. ANTLR, main/AP and optional source-set reuse
requires full output inventories and the held dependency/SDK authority. Main
inventory schema `sfm:local_compilation_outputs@2` includes the explicit
`compileJava-mappings.tsrg` file, including a legitimate empty file. Old missing
inventories cannot authorize a hit. The private retained `CatalogAdmitted`
all-hit optimization remains fail-closed; normal checked Catalog warm reuse is
the supported path for this generation.

Source responses retain `sfm:released_native_sources@1` and distinguish
`fresh_owned_execution` from `historical_local_cache`. The returned archive is the
held `deobfuscated_sources.jar` inside the Forge entry. The persistent tree is
under that key's `source-views/source-tree-*`, selected only after bounded safe
extraction and complete archive/tree validation. Repeated requests return the
same verified paths. The live store and tree ancestry are retained through final
report checks. Derived trees do not claim another producer execution.

## Preservation and failure behavior

Existing legacy files and receipts are never imported by hashing them now.
Missing new entries invoke the genuine producers in new owned attempts. Cold
success immediately reopens the published durable selection, so cold and warm
consumers use the same paths. Incomplete attempts remain unselected. A damaged
published entry is an error, with no legacy fallback or automatic repair.

Publication uses anchored no-follow paths, single-link regular files, ownership
and mode checks, anonymous staging, no-replace linking and directory/file sync.
Unsupported publication facilities fail closed. Warm reads rehash all selected
outputs and recheck their namespace. Repeated identical warm requests create no
new producer attempt or payload copy. The first cold attempt and its durable
exports are both retained; this patch does not retire scratch, prune caches or
migrate storage.

These are cooperative local developer-cache controls. Linux descriptors do not
exclude a malicious same-user writer or attest a manifest against that writer.
Sustained byte, link, inode, membership, mode and ancestry changes at checked
boundaries are rejected. Transient same-user edit/restore and complete forged
metadata/payload sets remain outside that authority.

NFRT's 22-file producer implementation identity and existing source route are
unchanged. Windows keeps its existing admission, publication and consumer logic;
the shared producer/input source changes conservatively change its implementation
keys, preserving prior entries rather than repinning or rewriting them. Windows
execution still requires independent qualification.

## Qualification and capacity

Use the existing authorized qualifier and its existing Cargo target. Remeasure
bytes/inodes before preparation and each expensive step. Retain the established
12 GiB / 500,000 free-inode entry gate and owned external process-group
supervisor, including its existing abort thresholds and bounded shutdown.
These operational margins are not an aggregate scratch-size guarantee.

The new store limits each exported file to 512 MiB and a production to 2 GiB.
That is not a bound on producer scratch or all simultaneous copies. A source
view separately permits at most 250,000 expanded members, 512 MiB per file and
4 GiB total, with bounded depth and cancellation checks. Defer optional filetree
materialization until its own measured capacity check passes. There is no
authorized cleanup if a gate fails.

For acceptance, record absent new-generation heads before calling a run cold;
never delete an entry to manufacture coldness. Keep exact source, candidate,
SDK/trust, command and before/after filesystem identities. Warm success needs
historical MCP/Forge/remap reuse, verified javac hits, unchanged durable paths and
hashes/mtime, zero producer children and no extra attempt/payload copy. Then prove
4.35 shares dependencies while compiling its own project, and verify the public
tree's persistent Java contents against the retained archive. Fixture-only tests
must not be reported as those real-game results.

## Verification cost and effect boundaries

The first real `8f5e98d50` 4.34/1.19.2 run reached only 27 of 38 distinct
mod-remap productions before its 30-minute supervisor bound. It had retained
raw inputs and SDK, made no network request, and had not started project javac,
another Minecraft row or a warm repeat. This is incomplete qualification.
Repeated common-authority checks grew with every retained remap; separate
transformations do not justify repeating all previous transformations' input
checks around each new child.

The performance repair retains one invocation-local remap common owner. Its
capture is sealed against the original held Forge production before any remap
attempt begins. Scoped effect checks retain that production's owner, head,
manifest, namespace and selected mapping role; they skip unrelated Forge export
contents. Each fresh remap still checks its exact original artifact and the current common
source/lock/tool/mapping/SDK/trust/environment authority around execution and
publication. Earlier remap outputs are not inputs of that independent producer.
The batch returns paths only after checking the complete selected closure.
Consumer checks hash every selected input and output, revalidate all retained
file/namespace identities, and check the shared full SDK once, with frozen source
checks bracketing that work. Packaging captures and checks the complete classpath
as a batch rather than rehashing the whole closure for each exact-role lock
lookup. No successful content check is memoized across effects, and no check is
replaced with mtime-only evidence. Publication, cancellation and corruption
failure behavior remain unchanged.

These source changes conservatively alter MCP/Forge/remap implementation keys.
Existing `8f` productions and partial attempts remain intact and cannot be
relabeled as productions of the repaired implementation. A following real run
must record whether its actual new keys were absent. NFRT's producer identity is
unchanged.

The ignored `representative_forge_authority_benchmark` test requires an existing
complete SDK and an explicit result path. It exercises actual frozen source,
raw-lock, full SDK, selected trust and retained-file checks at 27 and 38 remaps,
with counters for source scans, SDK rechecks and bytes hashed. Its artifact/tool
and producer-output payloads are synthetic; no Java transformation, Minecraft
compile, TLS request or real producer admission is performed. Its times measure
mechanical validation, not end-to-end Minecraft performance. Preserve the exact
benchmark binary/source and environment identities when comparing samples.

For bounded comparative measurements, `SFM_FORGE_BENCH_REFERENCE=8f` selects a
test-only mechanical reference to the original growing-prefix gate layout. It
uses the same full-byte primitives and payloads, explicitly restoring the two
old outer SDK checks and per-remap common owners. This is a reviewed reference,
not execution of the old release. Use the same optimized test executable for
reference and repaired samples, and retain its source and hash. The benchmark
now writes each completed sample to a companion `*.samples.jsonl` before its
terminal JSON, so an interrupted experiment retains completed measurements.
