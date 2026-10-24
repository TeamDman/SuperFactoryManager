# Independent review and targeted extension — 24 October 2026

Reviewed production implementation: `eaf0350b84deae772b01b95d486e8c23546c6bc5`.
Conclusive extended-suite commit: **`36c9388a3423ad4300e35409becd7bd3bd982c96`**.
The working tree was clean and all captured input hashes/HEAD remained unchanged
during the conclusive run. Production Java, public API, licensing and pinned
upstream reference are byte-identical to the original implementation checkpoint.
No editor, Explorer, palette, dependency, toolchain or artifact-path code changed.

An independent reviewer inspected the algorithm against
`helix-editor/nucleo@8c16d47cdfa9607d3e44df5f81c635c6f43c65ee`, including
overflow/length limits, fallback layout, Unicode preprocessing, tie branches,
highlight offsets, priority tiers and cancellation. **No production matcher
defect was found.** The reviewer reported three P2 verification weaknesses,
all corrected in the extended-suite commit:

1. Python `assert` could disable revision/compiler/table/output gates under
   optimization. These now use explicit failure branches. Wrong revision and
   wrong Unicode version are proven rejected under both `-O` and
   `PYTHONOPTIMIZE=1`; see `optimized-python-negative-checks.json`.
2. The receipt previously captured source identity only after execution. It now
   captures HEAD, dirty status, input inventory and SHA-256s before execution,
   requires the same identity after all stages, and rechecks the upstream pin
   and tracked-source cleanliness. The receipt marks
   `inputs_unchanged_during_run: true`. Earlier stable-run evidence remains
   historical; exploratory runs during editing are not the conclusive receipt.
3. The UTF-16 validator previously checked starts/counts and could miss an end
   halfway through a surrogate pair. It now independently builds exact merged
   half-open ranges with `String.offsetByCodePoints` and compares the complete
   span list. Its own negative checks reject split-surrogate, wrong-start,
   out-of-bounds and extra-character ranges.

## Meaningful added coverage

The reviewer independently ran 192 cases involving prefilter/class-normalization
disagreement (`µ`, `ſ`, final sigma) and 38,880 cases spanning every fold/normalize
mapping, with zero Java/Rust differences. The latter constructions are preserved
in `compare.py`: every one of 1,454 fold mappings and 706 normalization mappings,
both query/candidate directions, plus a spaced multi-character context with
competing occurrences, under all three case and two normalization modes.

The final executable suite compares **69,563 score/position cases and 768 full
ordered lists: zero mismatches**. These counts come from real Rust and Java
outputs; they are not manually calculated expected scores. Exact UTF-16 range
validation applies to every successful Java result. See `comparison.json` for
separate categories and seed.

New adversarial boundary fixtures force optimal and greedy routes to disagree,
so using the wrong transition cannot hide behind a single possible alignment.
Representative actual Rust witnesses, reproduced exactly by Java:

| Boundary | Optimal-side result | Greedy-side result |
| --- | --- | --- |
| ASCII slab, query `aa` | span length 11,093: score 62, positions `[11091,11092]` | 11,094: score 26, `[0,11092]` |
| Unicode slab, query `aa`, leading emoji outside prefiltered span | span length 8,874: score 62, `[8873,8874]` | 8,875: score 26, `[1,8874]` |
| 102,400-cell limit, query length 100 | span 1,024: score 2,610, final contiguous 100 positions | span 1,025: score 1,782, early 99 positions plus final gap endpoint |

Both release-arithmetic prefix wrap points are covered: start 21,845 → 21,846
changes a tight `aa` score 36 → 43; start 43,690 → 43,691 changes it 36 → 44.
These preserve upstream u16 penalty wrapping, not an overflow introduced by
Java. Supported match scores themselves are bounded by `26*m + 18` (at most
53,266 for m=2,048), below u16 overflow. Candidate codepoint limits imply at most
131,070 UTF-16 units, safely within Java int offsets. Full query-length and
supplementary-character boundary fixtures, including 65,535-codepoint candidates,
also compare exact positions and ranges. Oversized BMP and supplementary inputs
are rejected in CoreChecks.

Tie witnesses distinguish the algorithms: query `aa` in `aa aa` returns the
last optimal endpoint `[3,4]`; `ab` in `x_ab_ab` returns `[5,6]`; one-character
query `a` in `a a` returns the first best occurrence `[0]`. Batch ties still use
input index, independently of these within-string choices.

CoreChecks now also verifies `Integer.MIN_VALUE`/`MAX_VALUE` priority tiers,
nonmatches excluded despite high priority, empty-query ordering within tiers,
concurrent searches sharing one immutable candidate snapshot, and a superseded
worker throwing cancellation. Existing full-sort/top-K, iteration cancellation,
thread interruption and post-completion stale-publication checks remain.

Cancellation still cannot replace a caller's UI-thread publication check.
Priority tiers are an explicit SFM adapter extension, not Nucleo matcher parity
or reproduction of the palette's full history/continuation policy. Unicode
tables still derive from Nucleo's frozen Unicode 15 folding and Latin mapping
plus Rust 1.99.1's Unicode 17 category predicates. The MPL and Unicode notices
remain unchanged.

## Reproduction and scope

The conclusive command deliberately enables optimized Python:

```sh
source /workspace/.sfm-env/env.sh
PYTHONOPTIMIZE=1 python3 -O scripts/nucleo-parity/run.py \
  --upstream /workspace/reference-nucleo \
  --build-dir /workspace/nucleo-parity-reviewed-final
```

Results: Java compilation, CoreChecks including worker shutdown, UTF-16
validator negative checks, 25 default-feature upstream tests, 24 applicable
codepoint-profile upstream tests, Unicode regeneration, all differential cases
and all ordered lists pass. One incompatible unconditional grapheme test remains
explicitly skipped only in the codepoint profile, as documented in the original
contract. No new dependencies or installations were required.

Benchmarks were not repeated: production matcher/search bytes and benchmark
code are unchanged. The original generated-ID performance evidence remains
applicable to those exact bytes, with the same synthetic-data/UI-latency limits.
Native SFM tests and the portable Vox artifact-path fix remain owned by the
editor task; this follow-up does not retry or duplicate that change. No desktop,
game process, push or merge was performed. All worker/test processes launched
for this follow-up exited.

Subsequent parent handoff (reported from the editor executor, not reproduced
here): separator correction is in isolated commit
`f9831eb1aa4ba440845a1002aa055de2acbda081`, based on `e2ba833`. It exposed a second
same-pin reproducibility blocker for
`TeamDman/facet@f2afdece6c79e64085d2f8c047e22fe16b2c8c54`: expected
`blake3:4d1e88353f941be926fdf84f1dd8da9bd594b60f`, but the current producer yields
`blake3:cc19e740cf1c08970c23b2446781829106d9ab8c` and embeds host JDK information.
No hash bypass, repin or toolchain replacement was attempted. Native Minecraft
integration remains blocked pending the owning artifact/toolchain decision.
This does not invalidate the standalone matcher results.

For cross-executor consumer coordination, see `../../API.md`; it lists all public
signatures, source sizes and examples without requiring access to these files.
