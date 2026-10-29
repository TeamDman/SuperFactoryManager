# Mekanism 26.1.2 producer input closure — preflight draft

**Status: not build-ready.** This records verified source/recipe inputs and the missing external dependency proof for the existing SFM 26.1.2 Mekanism producer. It does not admit a warm Gradle cache, re-pin an output, or authorize a producer run. The frozen main/API JAR hashes and the historical receipt remain unchanged.

## Frozen identity already established

| Input | Verified value | Evidence |
| --- | --- | --- |
| Git remote | `https://github.com/mekanism/Mekanism/` | Generated 26.1.2 `schema-4.json` source recipe and pinned receipt |
| Commit | `f33ff1f438caa55d58ef1f0a08091997353afcb8` | Managed bare repository and receipt |
| Git tree | `ed07ebaa260846b071dcff3af2ed5cd25485db89` | `git show -s --format=%T` on the managed repository; matches receipt |
| Build system/tasks | Gradle wrapper; `jar`, `apiJar`; `BUILD_NUMBER=86` | Generated recipe and receipt |
| Historical receipt SHA-256 | `40fa66d790475ccab4066d3bbf76c427669093600af21137d8d4d4c24458214d` | Checked-in `producer-receipts/40fa...json` |
| Historical download-inventory SHA-256 | `68f7486f7bd662faf19b873f7046fc5d40a7e306146ec3e6826462130ec5a764` | Receipt field only; inventory bytes **not located** |
| Gradle distribution | `https://services.gradle.org/distributions/gradle-9.5.0-all.zip` | Pinned source `gradle/wrapper/gradle-wrapper.properties`; the property contains no distribution checksum |
| Historical Gradle archive SHA-256 | `a3c4ba4aca8f0075688b9c5b18939fd28e8cb4357c227da5c1d9f38343791439` | Receipt field; archive bytes require independent verification |
| Historical Java archive SHA-256 | `dbb698396d478e7fa2b1e50f4103324b2a99b90569ee27c33f2261f9215cf41e` | Receipt field; archive bytes require independent verification |
| Installed game/build Java candidate | `JetBrains s.r.o.|25.0.3+9-b480.61` | `C:\Users\Teamy\.jdks\jbr-25.0.3\release`; installed identity alone does not prove historical archive identity |
| Main output | 11,873,829 bytes, SHA-256 `333601e9f08a83fecb936727cd423ce7d6781478f475f1aaf1ab6d19d9452a15`, BLAKE3 `5dc6c6c6890e29e6cae2b38075cb111821361c62` | Pinned receipt and exact old-root output witness; no import approved |
| API output | 498,420 bytes, SHA-256 `5c1039f15afb35100018b8bdaf44bfa7947aa734351648b8b3dfec30268f2d44`, BLAKE3 `b5e829e94223c179b4a196b8fc098213c10a242d` | Pinned receipt and exact old-root output witness; no import approved |

The exact Git tree contains these relevant build-config blobs: `settings.gradle` `ace7bd3793c5ffe0e49671974c9bdf7dfb738819`; `build.gradle` `b503c4c5e6dab37682c448b5d70b15f4e82a071c`; `buildSrc/build.gradle` `17bb42f64ad5295b40dd1297f412227e38c088f9`; `gradle.properties` `160deba2f3db96760af915cd649e3e41ededf564`; `gradle/wrapper/gradle-wrapper.properties` `f193d72905e31afd6cc001b0a01d60591397abc7`; `gradle/wrapper/gradle-wrapper.jar` `b1b8ef56b44f16b14dc800fa8103a6d89abb526f`; `annotation-processor/build.gradle` `dcab834c1fa973bc0319d201c66e822bceb49a9e`. These are Git blob IDs, **not** SHA-256 checksums of downloaded Gradle artifacts.

## Source-declared external roots, not a resolved closure

At this commit, `settings.gradle` requests `org.gradle.toolchains.foojay-resolver-convention:1.0.0`. `buildSrc/build.gradle` requests `org.ajoberstar.grgit:grgit-gradle:5.3.3`. Root `build.gradle` requests `net.darkhax.curseforgegradle:1.2.30`, `com.modrinth.minotaur:2.9.0` and `net.neoforged.moddev:2.0.141`; `org.ajoberstar.grgit.service` is supplied through buildSrc. `gradle.properties` declares Java 25, Minecraft 26.1.2, NeoForge `26.1.2.73`, and versions for test and integration dependencies. Root `build.gradle` additionally declares multiple compile-only and datagen dependencies (JEI, EMI, Jade, WTHIT, Curios, AE2, Biomes O' Plenty, and others), plus the annotation-processor project. Plugin, transitive, metadata, transform and task-time inputs are **not** closed by these direct declarations.

The root build script configures exclusive repositories for BlameJared, Terraformers, Curios, CTM, CC:Tweaked, JsonThings, WTHIT, CurseMaven, Modrinth, The One Probe, Biomes O' Plenty, Mekanism/Jade-related integrations and Parchment, plus a restricted Maven Central repository. Plugin management and Gradle distribution resolution are separate inputs. A manifest must preserve repository identity and ordered resolution/variant selection where it affects bytes; a coordinate alone cannot identify the chosen artifact.

## Required manifest and admission sequence

1. Resolve the exact selected configurations and task-time artifacts for `jar` and `apiJar` **without running those producer tasks**. Record the Gradle wrapper ZIP, plugin/buildSrc classpath, parent/BOM/module metadata, ordered repositories, selected variants/attributes, artifact URL, byte size and SHA-256. Record local generated and source-tree inputs separately. An offline warm-cache graph listing may be used as a **candidate** only; it is not proof that all task-time reads are sealed.
2. Review the candidate against a complete task input trace or a strict verification mechanism that rejects every unlisted external artifact during a dry/non-output probe. Verify the actual files against canonical URL/size/SHA-256 pins, including metadata. If the original inventory serialization is recovered, compare its exact canonical bytes to `68f748...`; otherwise introduce a reviewed new manifest digest and receipt/recipe version. Never assert that new bytes match the historical inventory merely because the same artifacts appear plausible.
3. Add a missing-output preflight that validates the source commit/tree, wrapper/JDK/container identity, task/environment recipe and complete external manifest **before** acquiring an output lock, running Gradle or publishing cache bytes. Existing-output admission continues to verify the pinned receipt and finished JAR hashes. The two paths must be separate; today `Resolver::producer_cache_path` requires the finished output even before reaching the source-build miss branch.
4. Test absent/tampered metadata or artifact, source/recipe mismatch, unknown Gradle read, output mismatch, interrupted/partial publication and valid existing-output reuse. Only a fully sealed missing-output attempt may execute the producer offline. Admit both outputs atomically only if their frozen BLAKE3 **and** SHA-256 identities match. Keep old-root import under its separate migration/admission gate.

Any newly discovered coordinate must be traced to this exact source recipe and classified as an already-required pin or a genuinely new dependency. The former is within the user's scoped pin authority with verified origin/size/hash; the latter requires a separate decision. No dependency artifact or metadata has been admitted by this draft.
