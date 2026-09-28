# Exact JBRSDK toolchain pins

Plan status: active implementation; the canonical 1.19.2 lockfile pins JBRSDK17 for Windows x64. Other platforms and targets remain unpinned.
Primary implementation branch: canonical 1.19.2. No later-version feature merge is authorized here.
Last updated: 23 September 2026.

## Purpose and authority

Make the Java runtime selected by `sfm-propagate-changes.exe` reproducible from a checkout, while retaining an explicit local `--java-home` escape hatch. The user authorized exact, checksum-verified JBRSDK pins **after validation**. This is a narrow exception to the otherwise frozen dependency posture. It does not authorize other dependency upgrades, a floating “latest” selector, or 1.20-and-later feature propagation.

This plan is one track in `docs/tasks/parallel-objectives-orchestration-plan.md`. The Java-analysis scenario fixture repair is separate: it must not use production JDK pinning as a substitute for a deterministic test corpus.

Intent audit: extracted the user's JDK-selection concern as a reproducible toolchain contract, separate from their current machine setup. Traced the new exact-pin authorization to J3.1–J3.4 and the prior 1.20+ hold to J3.4. Checked for omissions: a sidecar hash does not verify downloaded bytes; Java 21/25 launch success does not prove enhanced hotswap; a planner-only change would leave other Java call sites floating; and the old Java-analysis snapshots must not be silently replaced.

## Current evidence

- The schema-4 `platform/minecraft/sfm-toolchain.lock.json` now pins exact JBRSDK17 17.0.14 b1367.22 for Windows x64 to the verified ZIP URL and SHA-512 shown below. Without a catalog, the legacy resolver still discovers installed JDKs; with a catalog, the target uses exact selection. `src/jar_build/engine_plan.rs` records the selection kind, URL and SHA-512 in generated `last-plan.json`.
- The planner calls `read_current()`, which projects the v4 document to v3 dependency data. The v4-only pin field is retained separately through that projection and the dependency-inventory rewrite path. Run/hotswap, server and Prism selection paths were reviewed so the pin is not merely a planner annotation.
- `docs/tasks/hotswap-runtime-rollout.md` records a verified official JBRSDK17 17.0.14 b1367.22 Windows x64 tarball and repeated 1.19.2 hotswap with GC. The local Java 21 installation has no checked archive receipt, and the installed Java 25 build is older than the candidate below. Neither establishes a portable exact pin.
- As of 23 September 2026, JetBrains publishes [17.0.14 b1367.22](https://github.com/JetBrains/JetBrainsRuntime/releases/tag/jbr-release-17.0.14b1367.22), [21.0.11 b1163.116](https://github.com/JetBrains/JetBrainsRuntime/releases/tag/jbr-release-21.0.11b1163.116), and [25.0.4.1 b610.67](https://github.com/JetBrains/JetBrainsRuntime/releases/tag/jbr-release-25.0.4.1b610.67). These are exact release candidates, not proof that all three pass SFM's runtime tests. The Java 25 candidate supersedes the older b583.48 observation in the pasted handoff.

Official Windows x64 vanilla JBRSDK archive sidecars:

| Java | Candidate archive | Official SHA-512 | Validation state |
| --- | --- | --- | --- |
| 17 | `jbrsdk-17.0.14-windows-x64-b1367.22.tar.gz` | `d787fdb48cf28886738428621d8f400ca8d95f88aa98f0995997c755b2da94fb0b2997d876bdbe6826002cd09d973d45ae71a4871fad1b36e2afdebe1202b8b3` | Downloaded bytes and earlier 1.19.2 hotswap verified; the new resolver and same-build ZIP received a separate proof below. |
| 21 | `jbrsdk-21.0.11-windows-x64-b1163.116.tar.gz` | `a792e735ac542c3cc02207f01d16cf983a569db752a27a6533763fa5ea17ed73eaa9a5e7bb011d652313248ca45cdeebacbc678cb95777f2d99e284b5c3a2264` | Tarball sidecar retrieved; tarball bytes and SFM runtime unverified. The separate ZIP was verified below. |
| 25 | `jbrsdk-25.0.4.1-windows-x64-b610.67.tar.gz` | `e6f2575c89e866a438c10f1115b232f5ebbc03bbcf6dfb64a71312ab37366550955d324224d576112f3fe684275c695fcd6bd5c49ef3c0f898140096c27b5ea5` | Tarball sidecar retrieved; tarball bytes and SFM runtime unverified. The separate ZIP was verified below. |

For the first acquisition implementation, choose the official Windows x64 ZIP form so the existing Rust `zip` dependency can extract it safely without adding a tar/gzip decoder. This is a reversible artifact choice, not a pin or acceptance result. Official ZIP sidecars are:

| Java | ZIP candidate | Official SHA-512 |
| --- | --- | --- |
| 17 | `jbrsdk-17.0.14-windows-x64-b1367.22.zip` | `3b5101101a46778c5b1cc572adef03bffceb338284d4e03d2fe70a72fb52fb2e1ee1d2e1c0b548c86e9092f776d3ca910669326531d1a5f8ab4521ad1b6d868c` |
| 21 | `jbrsdk-21.0.11-windows-x64-b1163.116.zip` | `457c15d931bb5055ae97574fc7d899aa70917c266d4b40a1c50d949ff1d2b431eb95ad078b2038bd94561fbd57889803f867dd9c295868fbb0c3c870f1f4d1d3` |
| 25 | `jbrsdk-25.0.4.1-windows-x64-b610.67.zip` | `790baf0fe302202e2fa74355b94c20510a81ebe3cddf4a6651d25a5a16966e5dfd230e7e6a4767a4bbb72ab48dd87d6ed5394e93358f7ddf89a6c349a7d734ac` |

The prior JBR17 real-game proof covered the tarball, not this ZIP. On 23 September the Windows x64 17 ZIP was downloaded outside the repo (248,788,267 bytes), and its computed SHA-512 matched the recorded official ZIP sidecar. A fresh sidecar request returned HTTP 403; the comparison used the value recorded earlier from the official sidecar. Safe independent extraction found one SDK home, 693 ZIP entries, `java` reporting JBR 17.0.14 b1367.22 and `javac` reporting 17.0.14. The separately downloaded ZIP then passed the new Rust cache's explicit ignored smoke test, including archive verification, extraction, offline reuse and exact Java/Javac identity (1/1 in 58.50 seconds). The archive was reverified and seeded into the content-addressed cache before the no-override runtime proofs below. The first extractor rejects unsupported tarballs clearly rather than silently changing their URL or format.

The Windows x64 21 ZIP was independently downloaded outside the repo (282,824,037 bytes); the computed SHA-512 matched a freshly retrieved official `.zip.checksum`. Its 695 entries have one SDK root without unsafe paths or symlinks. Extracted `java` reports JBR 21.0.11 b1163.116 and `javac` reports 21.0.11. The Windows x64 25 ZIP was likewise downloaded (296,485,719 bytes), with computed SHA-512 matching a fresh official `.zip.checksum`; its 774 entries have one SDK root, and extracted `java`/`javac` report JBR 25.0.4.1 b610.67 / 25.0.4.1. Both official downloads required the host network context because the restricted sandbox denied the socket. These checks establish archive integrity and local runtime identity only, not SFM build, game launch or enhanced hotswap compatibility. No repository cache, installed JDK or target lockfile was changed for either validation.

## Decision contract

1. Add an optional `jdk_pins` section to the v4 lock model. Old v3/v4 documents without it retain legacy selection and report themselves as unpinned; no silent migration claim. Presence opts that target lock into strict pin mode.
2. Each pin identifies the required Java major, JetBrains vendor, exact version/build, `jbrsdk` flavour, and one or more host-platform artifact URLs with SHA-512. Reject duplicate majors/platforms, malformed digests, mismatched URL/filename identity and unsupported schema shapes. Keep SHA-512 separate from the existing dependency `ContentHash` type, which is not a general SHA-512 digest.
3. `--java-home` wins deliberately and is reported as an override. Check Java compatibility and apply the hotswap guard, but do not misreport this override as the locked SDK. Without an override, resolve only the exact required-major pin for the host platform. Do not float to another installed JBR, newer patch, or higher major.
4. Acquire an absent archive only from its immutable locked URL, hash the downloaded bytes before extraction, extract safely into a temporary target and publish atomically. Reject corrupt archives and path traversal. In offline mode with no verified local artifact, or on an unsupported host platform, return an actionable error naming the required pin and the `--java-home` override. Never silently fall back.
5. Route planner, run client/server, hotswap helper and Prism through one pin-aware selection contract. Preserve the existing JBR17 launch and target-identity guard. Do not interpret `IgnoreUnrecognizedVMOptions` as proof that enhanced hotswap is available.
6. Keep three Java roles distinct: compiled language release, build-tool runtime, and launched game's runtime. On 1.20.2–1.20.4, NeoGradle requires Java 21 to build while Minecraft can still launch under Java 17. Prism historically selects the exact game Java major from `java_release`; do not silently change it to the build runtime. A later target may need both exact 17 and 21 pins, or an explicitly tested decision to launch the game on 21.

## Execution gates

### [x] J3.1 Lock schema and selection policy

Implemented the optional v4 section, validation and canonical serialization, lossless dependency-inventory rewrite, and a pure selection policy: explicit override before exact major/platform pin; legacy discovery only when no catalog exists. Tests cover old-lock compatibility, duplicates, malformed pins, exact selection, missing platform and unchanged dependency fields. Focused Rust library tests passed (four pin schema/rewrite tests, seven JDK policy tests and the v3 migration test); formatting and `git diff --check` passed. Artifact acquisition, checksum enforcement and runtime call-site integration belong to J3.2; the J3.1 selection tests alone do not prove them.

### [x] J3.2 Artifact acquisition, canonical 1.19.2 pin and runtime proof

The optional v4 catalog is parsed separately from the planner's v3 dependency projection. When present, selection requires an exact major and host-platform artifact; without it, legacy discovery remains. The planner records selection kind and exact pin URL/SHA-512, or identifies an explicit `--java-home` override, in its generated plan and summary. The canonical 1.19.2 lockfile now contains the verified JBRSDK17 Windows x64 ZIP pin. A generated no-override plan reported `lockfile-pin` selection with that exact URL and SHA-512.

The ZIP-only cache validates its immutable URL and SHA-512, rejects missing/offline or unsupported-platform artifacts, extracts safely and publishes atomically. It now hashes and extracts through the same held archive handle, then compares every cached installed file and path with that verified ZIP. Ten active cache tests pass, including altered binary/module and injected-file rejection. The real JBRSDK17 ZIP passed the stricter ignored smoke test (1/1, including offline reuse and provenance assertions). Review gaps were closed by rejecting non-ZIP artifacts at pin validation and making cache fingerprints depend on the archive SHA-512 rather than local paths. This deliberately re-reads the archive and installed tree on each acquisition; returning a path cannot prevent later hostile same-user mutation.

The build planner and Prism consult the optional target catalog; Prism preserves its historical exact game-Java major, separate from NeoGradle's Java 21 build-tool floor. Prism also honours its explicit `--java-home` override. Hotswap uses the Java in its completed build plan rather than selecting Java 17 a second time (8/8 focused tests across 17, 21 and 25). Source decompilation uses its inventory's original lockfile for exact Java 17 selection (4/4 focused tests, including a missing-17 fail-closed case). Standalone `server launch` now supports explicit `--java-home` with per-target exact-major validation (40/40 focused `server_` tests); its default remains labelled unpinned local discovery because tracked server folders have no trustworthy branch-lock association. The clean loader probe accepts an explicit Java home for an arbitrary release JAR. Neither standalone command may infer a lockfile from Minecraft version alone.

After these changes, `check-all.ps1` passed dependency policy, format, strict Clippy, build, 761/761 non-ignored library tests (five ignored), and integration sets of 11/11, 12/12 and 40/40. The first consolidated run exposed four synthetic dependency-index fixtures that used invalid lockfile placeholder text; those fixtures now vary valid JSON whitespace, and their focused tests passed 8/8 before the green full rerun.

The ZIP-backed JBRSDK17 passed explicit-Java canonical compile and client smoke before pin activation. A file-driven hotswap puppet then completed three phases in one game process (PID 11364 at 16:29:28): `ItemTerminalPart` to `CraftingTerminalPart` to `ItemTerminalPart`. It captured each phase, redefined the one changed class successfully from each of 2 distinct directories, and ran explicit `jcmd GC.run` between redefinitions. The run reported `SFM_GAME_PUPPET_COMPLETE failed=0 total=1` and exited 0. The pre-existing preview save was backed up and restored after the disposable proof (26 files, 3,958,907 bytes); no local save path is part of this plan.

After adding the exact ZIP entry, no-override canonical compile, client smoke and dedicated-server `sfm:move_1_stack_direct` GameTest all passed using the pinned JBR. The content-addressed cache held the reverified archive. These are runtime proofs for the 1.19.2 Windows x64 pin, not for other platforms or Java 21/25. The first post-pin full Rust run passed 764/764 non-ignored library tests and 12/13 Java-analysis integration tests. The one mismatch was the Partial-index snapshot identity: the JDK source-provider identity deliberately includes the changed lockfile digest, while its semantic output stayed unchanged. The expected digest was updated after reviewing that three-field diff. The final `check-all.ps1` rerun passed dependency policy, formatting, Clippy, build, 764/764 active library tests, and integration sets of 13/13, 12/12 and 40/40. The old affected-JBR17 rejection is already covered by the Rust launch guard tests and the actual pre-redefinition rejection recorded in `docs/tasks/hotswap-runtime-rollout.md`.

### [ ] J3.3 Java 21 and 25 candidate validation

The Windows x64 JBRSDK21 and JBRSDK25 ZIP archive/checksum/runtime-identity checks are complete as described above; no target pin is active. Next run branch-specific build/runtime tests in disposable contexts without merging feature code. For enhanced hotswap, test VM flags without `IgnoreUnrecognizedVMOptions`, target identity, repeated method-body and structural redefinition/JDWP cycles with GC. A Java version number or successful launch alone is not acceptance. Decide platform coverage from verified artifacts; do not commit a partial cross-platform claim.

### [ ] J3.4 Later-target adoption

After the separate Touch Display/Client Manager G41 hold is lifted, apply and validate target-specific pins along the adjacent version chain. Preserve each destination's existing dependency graph and adapters. Java 21 covers NeoGradle runtime on 1.20.2–1.20.4 even though those targets emit Java 17 bytecode; Java 25 is for 26.1.2. Do not mark later targets complete from a successful 1.19.2 pin.

## Completion evidence

Record selected archive URL, official and computed SHA-512, platform, SDK version output, cache identity, branch and test transcript for each pin. Keep any unverified platform or runtime explicit. The overall JDK track is complete only when each authorized target uses an exact verified SDK or has an explicitly accepted unsupported-platform policy, while the full relevant test suites and hotswap controls pass.
