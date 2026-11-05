# Unpublished Mekanism 26.1.2 artifacts

The SFM development 26.1.2 lock selects `mekanism:Mekanism:26.1.2-10.8.0.86` as the compile/runtime main mod and `mekanism:Mekanism:26.1.2-10.8.0.86:api` as a compile-only API JAR. SFM GameTests use both API and implementation classes. An API-only substitution cannot satisfy that contract.

The retained source producer used Mekanism Git commit `f33ff1f438caa55d58ef1f0a08091997353afcb8`, build number `86`, and Gradle `jar` plus `apiJar`. Its retained outputs were:

| Component | Bytes | SHA-256 |
| --- | ---: | --- |
| main | 11,873,829 | `333601E9F08A83FECB936727CD423CE7D6781478F475F1AAF1AB6D19D9452A15` |
| API | 498,420 | `5C1039F15AFB35100018B8BDAF44BFA7947AA734351648B8B3DFEC30268F2D44` |

The exact selected development lock also pins BLAKE3 hashes. Verify **both** algorithms, the Maven coordinate/classifier and the source/build receipt before admission. Do not repin the lock to a different build merely because it compiles. The historical plain 26.1.2 main/API bytes are different and must stay distinct.

The existing retained Podman producer is a **manual investigation reference**, not an unattended empty-cache recipe. Its NeoForm task manifest's 127 selected tool JARs have been joined to frozen hashes, and the task-time Mojang version JSON/client/server bytes match their content-addressed URLs. The complete plugin, buildSrc, transitive repository/variant graph and top-level Mojang manifest response are not yet sealed. A fresh producer run may therefore attempt additional reads. Build in a deliberately isolated Podman environment only after reviewing that graph and recording every external input; reject an unknown read instead of letting it become an untracked dependency. The Rust SFM CLI should consume admitted exact outputs, not launch Podman as a universal dependency path.

For a published Mekanism version, first inspect its official release or CurseForge file identity and use the existing exact remote acquisition command. No published pair has been verified byte-equivalent to the locked `26.1.2-10.8.0.86` outputs. Changing version or bytes requires a reviewed lock update and requalification of affected compile/runtime/tests.

For the exact already pinned source-built outputs, admit each JAR and its pinned producer receipt during cache or cloud-image preparation:

```sh
sfm-propagate-changes dependency artifact import mekanism/main --project-root platform/minecraft/projections/sfm-dev/mc-26.1.2 --file /path/to/Mekanism-26.1.2-10.8.0.86.jar --producer-receipt /path/to/producer-receipt.json --expected-sha256 333601E9F08A83FECB936727CD423CE7D6781478F475F1AAF1AB6D19D9452A15
sfm-propagate-changes dependency artifact import mekanism/api --project-root platform/minecraft/projections/sfm-dev/mc-26.1.2 --file /path/to/Mekanism-26.1.2-10.8.0.86-api.jar --producer-receipt /path/to/producer-receipt.json --expected-sha256 5C1039F15AFB35100018B8BDAF44BFA7947AA734351648B8B3DFEC30268F2D44
```

Without `--update-lock`, the command checks the selected projection, coordinate, exact BLAKE3 lock pin, independently supplied SHA-256 and pinned producer receipt before an atomic managed copy. It records manual admission provenance in the managed cache and leaves the lock unchanged. `dependency artifact accept` is a different command for an artifact already in managed storage. A local admission can prepare an image, but it does not make the unfinished source producer runnable from an empty cache. If an image declares this target, the [cloud offline publication gate](codex-cloud-environment.md#mandatory-offline-publication-gate) remains red until both exact outputs have been admitted into that image and the full gate passes.

For an isolated recovery test or prepared image, pass the same absolute `--cache-root <managed-cache>` to **both** import commands and to `source project compile`. The import override uses the normal marked native-root ownership check. A new root must not be prefilled from the active checkout cache; retain the supplied JARs and receipt outside it as explicit manual inputs. This tests admission independently of the old managed entries. It does not by itself establish that every other selected artifact can be acquired or built from an empty cache.

For a **new generation of an existing development source-built component**, the same command accepts `--update-lock` with the new JAR, its independently stated full SHA-256 and its matching new producer receipt. It first requires the selected generated project to match its exact authored lock, verifies the new receipt against the existing coordinate, Git source and build recipe, and publishes the new bytes under a new receipt-keyed managed path. It then atomically updates the selected **authored** schema-4 lock's content hash, receipt pin and output path. The old managed generation remains available. Run `sfm-propagate-changes source project sync --repo-root . --projection sfm-dev/mc-26.1.2` to update the emitted projection lock, then requalify affected compile/runtime/tests and any cloud image scope. An interruption before the authored write leaves an unreferenced managed entry; after the write, source sync resumes the generated output. This mode refuses release/historical projections, unknown components, changed coordinate or recipe, and arbitrary version substitution. Adding a genuinely new dependency or published version uses the separate reviewed declaration/acquisition workflow.
