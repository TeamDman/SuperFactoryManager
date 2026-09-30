# Fictional 9.99.99 source-candidate rehearsal — not release approval

This document is deliberately negative compatibility evidence for the local, fictional `released-9.99.99-rehearsal` preset. It binds a mechanical packaging and candidate-lock rehearsal to actual Gradle-produced JAR bytes. It does **not** approve a 9.99.99 release, establish 4.34.0 gameplay parity, or authorize promotion, tags, uploads, pushes, or a default-branch change.

The staged source-selection commit is `b9926dc49181804fe5e9fe4f6a9052112aed8ce8`, with source-projection definition SHA-256 `0735d20283abd892b6f024a18f650e904e8181314eb2480beeb4641c32a5ddd1` and preset identity `blake3:5cec0df43a34d21f5d25254d34a85049318bade4643c436534d6d7c2f57adf07`. A later documentation-only commit may carry this review without changing selected source inputs or generated output. The candidate lock must name that exact checkout HEAD, not assume the staged commit remains HEAD.

All ten external generated projects used `mod_version=9.99.99-rehearsal`. Their Gradle packaging tasks completed successfully under the selected local JBR/JBRSDK runtime and corresponding cached Gradle distribution. The 1.19.2 project used `-PsfmProfile=rust-toolchain`; its effective bundled `vox-java/main` dependency makes `jarJar` followed by `reobfJarJar` the production artifact path. The other projects used their default Gradle profile. The 1.21.1 task ran online because forcing NeoGradle offline invalidates a required cached client artifact. No packaging log contained a disk-space diagnostic.

| Target | Final Gradle task | Java / Gradle | Production JAR SHA-256 |
| --- | --- | --- | --- |
| 1.19.2 | `reobfJarJar` | 17.0.14 / 7.5 | `0cfef5dd1e0160ce9573f11804e95f490cf0089b556db433f141ec7e262eb7bc` |
| 1.19.4 | `reobfJar` | 17.0.14 / 7.5 | `4d32a269ff6ada55cbcbe73c02d8c8b65fdda4feb1852b46c2ab7320042492a4` |
| 1.20 | `reobfJar` | 17.0.14 / 8.1.1 | `bbfc0be548fb97828c24109c3ad346ac38ce8770ee6e0f29f5447d93a3d148de` |
| 1.20.1 | `reobfJar` | 17.0.14 / 8.1.1 | `ca2a86260559cbe1e692205507623bf0a917039f800edcfb9b385e5e18cc2f2e` |
| 1.20.2 | `jar` | 17.0.14 / 8.1.1 | `9e48cae7a2e22a9f62a297e35b2ee069b6f94c634caca75d669882cbf0f00f24` |
| 1.20.3 | `jar` | 17.0.14 / 8.1.1 | `963107cfea321b3af34d7ff238934e9850efa0bdcea85a81094bbf1702751ff8` |
| 1.20.4 | `jar` | 17.0.14 / 8.1.1 | `d4183fbf176dd30668e862617dd36a3ce5e2c24931fc629783e8c764deb56d6e` |
| 1.21.0 (`MC1.21`) | `jar` | 21.0.11 / 8.8 | `0fa0a559990dcb59102df383c69795f475a06be02bbea31d331223c7c27b3a45` |
| 1.21.1 | `jar` | 21.0.11 / 8.14.3 | `300bafd789b67ee887de9e899b40bd6e671cc023369b433f16af904cc927a5c6` |
| 26.1.2 | `jarJar` | 25.0.3 / 9.5.0 | `43522e2a7723e2387a82788e0af3474df390614ae81fddb4bc741f30fbb84e79` |

The JDK build IDs, Gradle task names and profiles in a candidate lock are reviewed assertions backed by retained build logs; matching a JAR hash alone cannot attest which process produced it. Prior validation of these same fictional roots includes ten-target `compileJava`, full JUnit runs, selected headless GameTests on 1.19.2, 1.19.4 and 26.1.2, and zero-write public source checks.

The full public `source release-inventory` passed against clean candidate commit `90b0adfa4d5ea744990bc37be0b9ba68efbaa33e`. It verified all ten production JARs and deterministic output for 18,272 generated files. The retained JSON report has schema `sfm:source_release_inventory@1`, `deterministic_source_check=true`, `build_inputs_are_reviewed_assertions=true` and SHA-256 `42b367bdba094267014b3cd8d702bab56b6422e729fef6b7f481e396f9f8b291`. A separate pass rehashed all ten JARs against that report.

The external candidate lock's SHA-256 is `cb1719bb0c54dabf05c0c1806cca74d8648e0367b6d7fa68e0194f42834348ab`. It binds the exact candidate HEAD and its earlier frozen copy of this evidence document, SHA-256 `a542f2f3595255c97c8dd1394fb58cc830220820bd20649b633cae90f6dec7a5`. This updated authored copy records the completed result; it must not replace the frozen copy or be substituted under the old lock. Candidate verification used an exact-hash, normal-feature M6.20 verifier copy, not the installed CLI or the later M6.21 source tree. No tag, upload, real release-preset promotion or checked-in generated-root change occurred.

Open compatibility gates remain: this fictional feature set is neither the checked-in 4.34.0 baseline nor a reviewed next release; its differences have not been approved for players. The full GameTest catalogs, client-only Touch Display and Client Manager behavior, modded integration, all-version save round trips, security review, provider metadata, and publication remain outside this rehearsal.

For 1.19.2, package verification now accepts exactly `default` or `gradle` with `reobfJar`, and `rust-toolchain` with `reobfJarJar`. The package inventory remains a reviewed assertion. The candidate verifier separately checks the projected lockfile's provenance hash, resolves the named schema-v4 profile, and requires the production task to match its effective bundled dependencies. These checks do not attest which build process produced the JAR.

New-immutable-preset Apply uses the verified candidate lock and repeats full candidate verification before destination writes. Ordinary `source promote` dry-run still applies the legacy task rule and cannot accept this bundled-profile candidate. The positive ten-target bundled immutable CLI Apply fixture passed at code checkpoint `0e7769b90`. It verified the external lock hash and acknowledgment, all ten installed manifest hashes and the completed recovery journal. Its synthetic JARs prove a temporary control path, not production packaging or authority to promote real release roots. No real generated release root should be promoted from this document.
