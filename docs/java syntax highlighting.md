# Java syntax highlighting

SFM highlights concrete `.java` documents in Text Editor v3 by sending the
editor's exact current UTF-8 projection to a reusable Rust process. Rust owns
the Arborium parser/query/cache; Java owns document lifecycle, stale-result
rejection, UTF-8-to-glyph projection, and Minecraft `ChatFormatting` rendering.
Highlighting is advisory: a missing, incompatible, or failed service leaves
plain readable text and must not block editing or definition navigation.

## Direct command

The direct command is useful for inspecting the versioned result without
starting Minecraft:

```pwsh
Get-Content -Raw .\SomeType.java |
    sfm-propagate-changes.exe syntax highlight --language java --stdin --output-format json
```

Optional `--request-id`, `--request-generation`, `--origin-id`,
`--origin-generation`, and `--maximum-spans` arguments make deterministic
integration fixtures possible. Source text is accepted only on stdin and is
not repeated in the result or default telemetry.

## Exact request/result contract

- Request schema: `sfm.syntax-highlight.request/1`.
- Result schema: `sfm.syntax-highlight.result/1`.
- Formatting schema: `minecraft.chat-formatting/1`.
- Parser fingerprint:
  `arborium-java/2.18.1+arborium-highlight/2.18.1+sfm-chat-formatting/1`.
- The request carries positive request/request-generation and
  origin/origin-generation identities, `language`, exact `source`, a canonical
  `sha256:<64 lowercase hex>` witness, and the caller's span limit.
- Result identity repeats every request identity except source content. It also
  carries the exact UTF-8 byte length, terminal outcome, parser/format schema,
  bounded diagnostics, elapsed time, cache evidence, and sorted non-overlapping
  spans.
- Each span is a half-open UTF-8 byte range with one stable Arborium tag and a
  duplicate-free list of canonical lower-case `ChatFormatting` names.
- Both Rust and Java reject mismatched schemas, hashes, origins, generations,
  language, byte lengths, span limits, overlapping ranges, split UTF-8 scalar
  boundaries, and unknown formatting names.

The default hard limits are 4 MiB of UTF-8 source, 262,144 spans, and 256
diagnostics. The service returns typed `highlighted`, `unsupported-language`,
`invalid-request`, `cancelled`, or `failed` outcomes. Only a highlighted result
may carry style spans.

## Reusable worker protocol

`sfm-propagate-changes.exe syntax serve` is a long-lived stdin/stdout worker.
Every message is `u32` little-endian byte length followed by one UTF-8 Facet
JSON object. Stdout contains protocol frames only; bounded lifecycle/cache
telemetry goes to stderr.

The client first sends `hello`; the server replies with its protocol schema,
capabilities, frame/pending limits, supported languages, and exact request and
result schema ids. Protocol schemas are:

- `sfm.syntax-server/1`
- `sfm.syntax-server.hello/1`
- `sfm.syntax-server.highlight/1`
- `sfm.syntax-server.cancel/1`
- `sfm.syntax-server.ping/1`
- `sfm.syntax-server.shutdown/1`
- `sfm.syntax-server.error/1`

Supported operations are `highlight`, `cancel`, `ping`, and `shutdown`.
Cancellation names the full request and origin generation identity. The
default process accepts eight pending requests and 16 MiB frames. One process
compiles the Java query once, reuses parser/query state, and retains a bounded
hash-keyed result cache; it never retains source text as the cache key.

## Minecraft lifecycle

Only a ready, concrete file document whose typed path has extension `.java`
uses the Rust provider. Text Editor v3 submits after initial load and after
document mutation, never from `render`. A new generation cancels/supersedes the
older query. Results are decoded and validated on worker threads, converted
from UTF-8 byte boundaries to the existing UTF-16/glyph projection once, and
published as one immutable style snapshot on the Minecraft executor.

Before publication, the editor rechecks origin generation, exact projected
source hash, language, protocol identity, and open lifecycle. Closing or
reopening a panel invalidates its request. Cursor, selection, and target-range
overlays stay Java-owned and render above syntax styles. Existing SFML and G4
highlighting paths remain available for their document types and are cached
outside the per-frame render path.

## Language boundary and backlog

This phase enables Java only. A 2026-08-15 tracked-source audit found the next
useful extension families in this order: Rust (400), JSON (178),
Gradle/Groovy (107), PowerShell (55), Markdown (51), TypeScript (14), and TOML
(7), after 1,524 Java files. Adding a language is a later feature/dependency
decision; the audit does not silently expand the release binary. Existing
SFML/G4 behavior remains separate until an explicit migration is planned and
visually compared.

## Completion baseline — 2026-08-15

The first Java-only slice is complete. The deterministic in-game journey is
under `platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/`
`sfm-title_screen-20260815-151735-957`; its typed artifacts and five inspected
screenshots cover cold/warm highlighting, exact and ambiguous SFM definitions,
and an acquired Forge dependency source. A real cold `SFM.java` highlight
produced 356 spans, 9 distinct tags, and 10 formatting values in 1,776
microseconds of Rust work and became visible 135,044 microseconds after worker
startup. The warm cache hit used the same worker, took 75 microseconds in Rust,
and became visible in 80,420 microseconds. Raw source is absent from retained
telemetry.

The matching Auto plus GUI scales 1 through 8 visual matrix is
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/`
`sfm-title_screen-20260815-152016-576`. Every final screenshot was inspected:
directory/file ItemStacks, Java styles, and the exact contrast-backed
`Read-only` status remain legible without control overlap.

Definition navigation's warm path measured 182,577 microseconds for an existing
SFM target and 279,692 microseconds for acquired Forge source. Cold SFM lookup
remains 6,766,505 microseconds end to end; the durable probe at
`docs/architecture/evidence/symbol-server-installed-probe-1.19.2.json`
identifies parsing 1,505 Java files as the dominant Rust cold stage
(2,177,915 of 3,209,138 microseconds). This cost is recorded rather than hidden:
the supervised worker amortizes it, and any future cold-start optimization must
measure that stage before changing architecture or acceptance thresholds.

Validation included strict Rust checks (569 passed, 3 ignored), all 8 Rust
scenario tests, real Java-to-installed-Rust interoperability, canonical Java
compile/full tests, and both declared puppets. No additional Arborium grammar,
propagation, publication, or release operation was included.

## Troubleshooting

- Override worker discovery with the documented `sfm.syntax.workerExecutable`
  Java system property when testing an uninstalled CLI build.
- A missing executable, handshake/schema mismatch, crash, timeout, or
  unsupported language must leave readable text. Restart is lazy on the next
  request.
- Default diagnostics contain identities, hashes, counts, durations, cache
  status, and process lifecycle only. Do not add source text or absolute paths
  to default logs.
