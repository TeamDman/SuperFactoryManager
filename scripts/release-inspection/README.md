# In-game release inspection

The comparison is an ordinary contributed Explorer root. Select two native directory
entries (generated projections or working trees) or two JAR entries using Ctrl-click,
then right-click a row and choose **Show diff · old: … → new: …**. Both orientations
are explicit. The comparison is added to that Explorer. Unsupported input pairs and
failed preparation report an error without replacing existing roots.

Expand a comparison, then a strategy, a file pair, and a representation. Strategies
include All changes, Instruction changes only, Printed debug only, Changed resources /
source files, Unknown class changes, Added / removed, Certified source whitespace,
and Exact bytes. Empty strategies show zero. The prepared release workspace uses this
same feature and starts with exactly two collapsed comparison roots.

Instruction-only compares the printed `Code:` bodies (offsets, operands, symbolic
comments and switch targets), excluding debug tables and declarations. Other changed
printed class data with equal instruction bodies has its own strategy. Added/removed
classes are in the membership strategy. Neither equal printed bodies nor equal
instruction/signature views exhaust class attributes or prove behavior.
Bodies are keyed by method name and complete JVM descriptor, so declaration order
cannot hide swapped bodies. Identical bodies under changed method declarations stay
outside instruction-only; added/deleted bodies remain visible. Unsupported javap
structure remains unclassified. The complete printed view remains a separate choice.

The native launcher wires the source checkout's `classes.py` producer into interactive
clients. It invokes the existing release-report `javap -c -p -s -l` pipeline off the
render thread. Python and the launching JDK's javap must already exist. No installation
or dependency acquisition is performed by the comparison action. When opening an
existing client without those properties, raw JAR comparison is available and class
representations say that evidence is unavailable. Producer failures are explicit.

`printed-debug-only` means equal printed instruction/signature views with differing
printed debug tables. It does not exhaust class attributes, prove behavior, or explain
the difference as source whitespace. Exact bytes, source whitespace classification,
instruction changes, unrepresented class changes and unknowns remain separate.
The Rust reader verifies archive and member hashes, retains original bytes, source
provenance and source mappings, and never treats a failed input as an empty tree.
Original byte evidence uses explicit base64 encoding with its byte length/hash and
all source metadata. Imported class views remain producer assertions bound to those
bytes, not authenticated proof of how the producer ran.

## Prepare and open

Build the existing locked CLI without installing it:

```bash
cargo build --manifest-path platform/cli/sfm-propagate-changes/Cargo.toml --locked --offline --bin sfm-propagate-changes
```

Use its actual target path as `CLI`. Prepare into a new directory under an existing
parent; the script validates projection keys against `platform/minecraft/projections.json`.
The default is `sfm-4.34.0/mc-1.19.2` versus `sfm-4.35.0/mc-1.19.2`, rendered from current
catalog inputs with explicit provenance, not an inferred historical source tree.

```bash
python3 scripts/release-inspection/prepare.py --cli "$CLI" --output "$EVIDENCE/release-001" \
  --old-jar "$OLD_JAR" --new-jar "$NEW_JAR" --javap "$JDK/bin/javap"
"$CLI" run client --repo-root "$PWD" --projection sfm-dev/mc-1.19.2 \
  --java-home "$JDK" --inspection-workspace "$EVIDENCE/release-001/release.sfm-inspection.json"
```

Alternatively pass `--class-evidence PATH` to reuse existing hash-bound producer output.
Omitting JAR paths deliberately creates an error-labelled JAR root and returns status 2;
the independent projection root is still prepared. No JAR path or release build is guessed.
The CLI itself accepts `source inspect-prepare --request PATH --output NEW_FILE` for
arbitrary explicit source comparison inputs and JAR pairs. Existing output is preserved.

In a running development client, use the command palette:

```text
sfm action invoke sfm:explorer/diff open "/absolute/path/release.sfm-inspection.json"
```

The launch flag queues opening after the first title-screen event. Preparation is on
a worker; closing the originating screen discards its result. Explorer clicks retain
scroll; explicit reveals and keyboard navigation may scroll. A representation click
opens its preview while keeping Explorer focus; double-click focuses the preview. Previews
reuse the Explorer preview slot. Source leaves retain file language; diff leaves use
validated source maps and the existing parser-backed syntax worker, independently of
comment/review state. Missing syntax service remains visible through existing status.

## Verification

```bash
python3 scripts/release-inspection/verify.py --cli "$CLI" --java-home "$JDK" --output "$EVIDENCE/fixture-001"
cargo test --manifest-path platform/cli/sfm-propagate-changes/Cargo.toml --locked --offline --lib inspection_cli::
```

`InspectionProbe.java` exercises the production Java tree and in-memory resolver against
that generated fixture: hierarchy, strategy membership, pagination, text, mapped diffs,
missing input and disposal. Compile against the generated development sources and the
existing Gson classpath. This is headless model evidence, not UI rendering evidence.
The native JUnit suite includes `pointerSelectionPreservesScrollWhileExternalSelectionReveals`.

The complete headless qualification command uses existing local tools/classpaths:

```bash
python3 scripts/release-inspection/qualify.py --cli "$CLI" --java-home "$JDK" \
  --gson-jar "$EXISTING_GSON_CLASSPATH_JAR" --output "$EVIDENCE/new-qualification"
```

It retains real javac/JAR inputs, requests, receipts and command stdout/stderr under
that fresh directory. Checks include method reorder/swapped-body/descriptor/switch
classification, original-byte recovery, contradictory evidence, malicious archive
admission before javap, strict JSON, FIFO/symlink input rejection, alias sharing,
mount preparation without holding the publication monitor, and linear UTF-8/CRLF
decoration coordinates. Nucleo adapter tests use an explicit fake provider only.

## Interactive bounds and threading

Native comparison waits run separately from reads of mounted results. Canonical mount
graphs and immutable diff decorations are prepared before publication on the client
thread. Strategy aliases share validated surfaces and decoration lists; the editor
does not rebuild those decorations every tick.

Interactive admission limits each source file/archive member to 4 MiB, each expanded
side to 32 MiB, and archive entries/source pairs to 20,000. Retained representation
text is limited to 64 MiB per workspace, individual representation strings to 8 MiB,
and serialized workspace JSON to 128 MiB with a bounded writer. The Java reader caps
expanded nodes at 100,000, JSON nesting at 64 and values per JSON document at 500,000.
Oversized inputs fail explicitly. Projection/pinned-Git capture still uses the existing
source engine's upstream bounds before inspection's post-capture limits; this is not
a constant-memory streaming source comparator.

Prepared JSON is read as a stream from an admitted regular file. Basic identity,
size and modification checks detect ordinary replacement/editing. Local filesystem
capture is not an atomic or tamper-proof snapshot. Comparison process waits retain
their existing bounded timeouts; the native provider is not interrupted mid-row by
the future Nucleo adapter contract.

For a recording acceptance pass, launch the command above; capture actual in-game
screenshots of the two roots, instruction strategy, changed Java structured diff and
missing-input root. Select/expand a bottom visible row, record the scroll offset before
and after, Ctrl-select two inputs, Show diff, change representations, then use external
Reveal to verify intentional scrolling. Test a long diff and a long strategy separately.
Do not report this checklist as passed until the real client has executed it.

## Nucleo integration boundary

A separate matcher task owns Nucleo parity. Current Explorer already has literal,
legacy fuzzy (`~`) and regex, with independent filter and highlight options.
The integration entry points are `SFMExplorerSearchAction.OPTIONS`,
`SFMExplorerSearchBar`, `SFMTextMatchOptions.Mode`, and `SFMTextMatcher.compile`.
Do not relabel the legacy fuzzy implementation as Nucleo or silently replace it.

The required matcher contract is a distinct explicit mode, deterministic match/score
and sorted non-overlapping UTF-16 fragments in the original candidate, Unicode-safe
case handling, whole-word policy, and `SFMMatchBudget` cancellation/limit reporting.
Current `Match.score` sorts lower-first; a Nucleo higher-is-better score needs an explicit
adapter. Tests must cover astral text, repeated matches, cancellation and no-match versus
unsupported/limited results. UI tests must preserve the existing substring/regex modes,
independent filter versus highlight state, selection and scroll across mode switches.
The Nucleo button wiring is pending actual matcher source and provider binding; no
Nucleo behavior or parity is claimed here.

The parent has now supplied the API contract. `SFMExplorerNucleoAdapter` is an isolated,
contract-tested boundary with no provider registered: its Factory must construct
`NucleoMatcher.Options(RESPECT or IGNORE, SMART, DEFAULT, false)`. Native null means
absent; returned spans must be ordered original-string UTF-16 boundaries. RowMatch
deliberately exposes no ranking score. Budget/cancellation are checked before and
after each bounded provider call; the caller must retain generation/context checks
before UI publication. Existing substring/fuzzy/regex and filter/highlight behavior
has not been changed.

Minimum transfer prerequisite is the intact source of these three files, including
their MPL notices and any accompanying notice/catalog entries:

- `ca/teamdman/sfm/client/search/nucleo/NucleoMatcher.java`
- `ca/teamdman/sfm/client/search/nucleo/NucleoSearch.java`
- `ca/teamdman/sfm/client/search/nucleo/NucleoUnicode.java`

The handoff names production commit `eaf0350b84deae772b01b95d486e8c23546c6bc5`, reviewed
checkpoint `9378a217e254bfd601d8cc47a9b7e8a7839d8084`, Rust pin
`8c16d47cdfa9607d3e44df5f81c635c6f43c65ee`. Those unpublished objects are absent here.
No real matcher implementation, parity result, optional button or live integration
is claimed by the adapter tests.
