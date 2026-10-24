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
The Nucleo button wiring is pending that API; no Nucleo behavior or parity is claimed here.
