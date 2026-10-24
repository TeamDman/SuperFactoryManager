# Verification receipt — 24 October 2026

Implementation commit: **`eaf0350b84deae772b01b95d486e8c23546c6bc5`** on
`feature/nucleo-java-parity`, based on `e2ba833b`. Working tree was clean during
the conclusive run (`receipt.json` records `sfm_dirty: false` and input SHA-256s).
This evidence-only follow-up does not change the tested implementation.

## Actual passes

* **29,338 exact Java/Rust score + position comparisons, zero differences.**
  1,896 cases use 79 literal string pairs extracted from pinned upstream tests,
  each under all 24 supported settings; 624 targeted cases; 2,548 cases enumerate
  all candidate strings of length 0–5 over `aA_` against seven queries with
  selected settings; 20,000 seeded random cases; 2,498 sampled Unicode-scalar
  cases; 1,272 length/slab/overflow boundaries; 500 longer random cases crossing
  the greedy fallback. Seed: `1397116238` (`0x53464D4E`). These are real executable
  oracle comparisons, not manually calculated expected scores.
* **768 candidate lists agree with actual Rust `Atom::match_list` ordering.**
  Includes empty queries, duplicate IDs, ties, namespaces, and randomized lists.
* Rust official unit tests: **25/25 pass with default-equivalent features**
  (normalization, folding, segmentation); **24/24 pass in the codepoint profile**
  with its one incompatible unconditional grapheme-truncation test explicitly
  excluded. Two upstream elided-lifetime compiler warnings remain unmodified.
* Java 17 `javac --release 17 -Xlint:all` compiles the core and standalone harness.
  CoreChecks passes mapping, limits, original payload identity, contextual tiers,
  top-K versus full ordering, cancellation/interruption, and a caller-side
  stale-generation model. The independent adapter validates every returned
  UTF-16 span against `String.offsetByCodePoints` during differential testing.
* All Unicode table bytes regenerate identically through the real pinned Rust
  implementation; no reliance on JDK classification or manual table expectations.
* **32 benchmark score-pass checksums agree** between Java and Rust.
* Native `source project sync` and `source project check` pass for
  `sfm-dev/mc-1.19.2`. Generated matcher source differs only by the standard
  provenance comment; those generated Java sources independently compile and
  pass CoreChecks. This is source-generation and isolated Java evidence, not a
  completed Minecraft build. No release projections were regenerated.

Conclusive command (all oracle operations offline and locked):

```sh
source /workspace/.sfm-env/env.sh
python3 scripts/nucleo-parity/run.py \
  --upstream /workspace/reference-nucleo \
  --build-dir /workspace/nucleo-parity-final --benchmarks
```

## Performance, with limits

Host: Linux x86_64, Intel Xeon Platinum 8573C, 5 visible CPUs / cgroup quota 4
CPUs, 16 GiB memory limit. JDK `17.0.20.1`; Java heap fixed at 512 MiB. Rust
`1.99.1` release build. Java and Rust benchmark runs are sequential. Shared-host
load, JIT and GC noise remain; there is no CPU isolation or multi-fork JMH design.

The 100,000 IDs are **generated load-test data** with registry-like namespaces,
material names, underscores and numbered variants. They are not a snapshot of a
loaded modpack. The full CSVs cover empty, partial and no-match queries at 1K,
10K, 50K and 100K entries. Each query has six warmups and 21 measured samples.

| Candidates | Java top-50 + highlight p50 range across queries | Worst p95 |
| ---: | ---: | ---: |
| 1,000 | 0.039–0.417 ms | 3.928 ms |
| 10,000 | 0.358–3.677 ms | 6.686 ms |
| 50,000 | 2.020–7.414 ms | 10.102 ms |
| 100,000 | 3.351–14.816 ms | 19.485 ms |

Selected 100K measurements (same fixture, prepared outside timed loops):

| Query | Java score p50 | Rust score p50 | Java top-50 + highlights p50 / p95 |
| --- | ---: | ---: | ---: |
| empty | 2.245 ms | 0.568 ms | 5.490 / 7.658 ms |
| `i` | 11.608 ms | 4.733 ms | 14.816 / 19.485 ms |
| `iron` | 6.112 ms | 6.315 ms | 6.840 / 11.038 ms |
| `m:ii` | 6.016 ms | 3.712 ms | 6.174 / 7.759 ms |
| `zzzz_missing` | 3.662 ms | 2.129 ms | 3.351 / 4.380 ms |

These numbers do not establish speed parity or a robust speedup in the one
near-equal row. The earlier exploratory run was faster in several rows,
illustrating host/JVM noise. The committed CSVs are the conclusive run.
Prepared-candidate creation measured 41.240 ms at the first 1K invocation and
64.413 ms at 100K; do it off the render thread and reuse the snapshot. Forced
cancellation after 100 supplier checks measured 0.223 ms at 100K, but that is a
deterministic polling test, not real UI response latency. Mid-candidate work is
bounded rather than interrupted. No Simmetrics speed comparison, real modpack
relevance judgment, rendering test, keypress latency, or executor contention
measurement is claimed. The worst observed batch p95 exceeds a 60 Hz frame;
integrate through a worker and recheck generations before UI publication.

## Failed or unrun stages

* **Native SFM JUnit is blocked before compilation.** First attempt omitted
  `--java-home` and failed because automatic discovery had no pinned Linux
  JBRSDK. The corrected command used the installed JDK 17 and rehydrated only
  existing locked dependencies. It then failed resolving
  `org.facet:vox-java:0.10.0-rc.5`: the locked source build at
  `TeamDman/facet@f2afdece6c79e64085d2f8c047e22fe16b2c8c54` completed, but the
  resolver expected
  `/tmp/sfm-source-builds/d5d331c1dfe191f51d07a9676626ecc11f4cda56/vox\java\target\vox-java-0.10.0-rc.5.jar`.
  The actual artifact exists at the corresponding normal Linux
  `vox/java/target/vox-java-0.10.0-rc.5.jar`. No lock edit, artificial path,
  copied-artifact fallback or tool upgrade was used. Outcome: **failed**, not
  pending or uncertain. The native repository `NucleoMatcherTests` were **not
  executed**. Fixing that resolver/platform-path issue is outside matcher scope.
* The initial codepoint-feature upstream run executed the unconditional
  `utf32_str::tests::test_grapheme_truncation` and failed on CRLF (`'\r'` versus
  `'\n'`). This is outside the declared feature profile. The final runner
  transparently skips that test for codepoints and executes it successfully in
  the separate segmentation-enabled baseline. Upstream source is unchanged.
* An initial direct byte comparison of projected Java failed because the
  generator prepends its standard provenance comment. Comparing after that
  exact comment, compiling the generated files and running CoreChecks passed.
* `rustfmt` was attempted but is **not installed** for the pinned toolchain.
  No component was installed or upgraded. CLI `check-all.ps1` and Clippy were
  not run: no CLI Rust code changes, no PowerShell/rustfmt/Clippy components in
  this environment. The standalone Rust adapter builds and its pinned upstream
  unit suites run through Cargo as recorded above.
* Direct Unicode-license curl retrieval returned HTTP 403. The official page
  was independently readable through the web tool and its license text was
  retained. No trust/network changes were made.
* Minecraft/UI interaction, native full compilation, packaging and final-JAR
  license inventory are **unrun**. No global ranking replacement or gameplay
  change is made, so there is no gameplay changelog entry.

The native logs and acquisition identities/hashes are summarized in
`native-stage.json`; full logs remain at the recorded workspace paths. The test
run's wait on the Vox artifact lock progressed into the source build, then
exited with failure normally. No unrelated processes were stopped or restarted.

## Operational readiness and handoff

* Existing installed CLI remains unchanged:
  `/workspace/.sfm-env/libexec/sfm-propagate-changes`, version 0.1.1,
  revision `122e4b31`, built 2026-10-21 21:00:03 UTC; SHA-256
  `35374239ffa5562ee5452d884f09ea1fef07f2121fccdd0575c44d9e3472e405`.
  Java-only authored changes and an isolated oracle require no CLI installer.
  **User must run install.ps1: no.** No global toolchain, dependency, network,
  certificate or security settings changed.
* SFM dependency declarations and lockfiles are unchanged. The separate oracle
  lock pins already cached `memchr 2.7.6` and `unicode-segmentation 1.12.0`.
  Native test cache rehydration/source builds came only from SFM's existing
  lock; `native-stage.json` records the identities. Nucleo is the only new
  developer/reference clone, explicitly authorized. The Facet checkout was
  materialized by the existing locked native build recipe.
* Final state: all test/build processes launched by this goal have exited;
  no client, server or desktop was launched, left running, killed or restarted.
  Cached artifacts and logs are preserved. No pushes, main merges, resets or
  dependency/toolchain upgrades occurred.
* Reproduce the independent matcher evidence using the command above. After
  the native artifact-path blocker is resolved through its owning task, run:

  ```sh
  source /workspace/.sfm-env/env.sh
  sfm-propagate-changes test run --projection sfm-dev/mc-1.19.2 \
    --filter NucleoMatcherTests --repo-root /workspace/SuperFactoryManager \
    --java-home /workspace/.sfm-env/jdk17
  ```

* Integration remains opt-in. See `../README.md` for the SFML action/payload
  seam, palette eligibility/history/range policies, Explorer filtering and
  UTF-16 highlights, and required UI-generation checks. No consumer files or
  shared projection metadata were edited, avoiding the editor/Explorer tasks.
* Required core completed and checkpointed. Further UI activation, real loaded
  modpack captures and end-to-end latency checks belong to the consumer owners;
  no additional stretch item is in progress.
