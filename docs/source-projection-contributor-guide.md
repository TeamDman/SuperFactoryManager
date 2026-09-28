# Build and edit a projected Minecraft version

This guide applies to the experimental `feat/sfm-main-source-projection` branch. The generated projects are **4.34.0 release-baseline candidates**. They are not yet approved as release-equivalent builds or a replacement for the current version branches.

## Build with Gradle only

Choose the project under `platform/minecraft/mc-version/<target>`. Each is a standalone Gradle project with ordinary Java sources, resources, build scripts and its own wrapper. You do not need Rust to build it.

| Targets | JDK major version |
| --- | ---: |
| `1.19.2`, `1.19.4`, `1.20`, `1.20.1`, `1.20.2`, `1.20.3`, `1.20.4` | 17 |
| `1.21.0`, `1.21.1` | 21 |
| `26.1.2` | 25 |

From the selected project directory, run `./gradlew jar` on Unix or `.\gradlew.bat jar` on Windows. The first build may download Minecraft, Gradle, loader and plugin artifacts. A later offline build can use `--offline` once those inputs are cached. `jar` proves compilation and packaging; it is not the same as the production reobfuscated release artifact. Use the version's `reobfJar` task where applicable when comparing release JARs.

The `.sfm-source-projection-manifest.json` in each project names its Minecraft target, feature preset, preset identity and the source and output hashes of every generated file. The checked-in projects use `released-4.34.0`. Do not treat a different local projection as that release preset just because it has the same target version.

## Contribute to generated source

You may edit a generated Java file and submit a normal pull request. The header says it is generated so maintainers know the change must also be brought back to the canonical source or a version-specific overlay. Do not edit the provenance manifest by hand. Ordinary Gradle builds continue to work with your edit.

Before the next generated-source update, a maintainer must:

1. Review the contributor's change and apply its intended effect to the canonical source or the appropriate overlay.
2. Render the same target and preset. `source sync` refuses to overwrite a generated file that has changed since its last recorded hash.
3. Run `source reconcile` only if the newly rendered bytes exactly match the contributor-edited file. Reconciliation updates provenance but never overwrites that file.
4. Run `source check` and a relevant Gradle build before committing the updated source and manifest.

If rendered bytes differ, resolve the authored-source discrepancy manually. There is no automatic inversion of arbitrary Java edits or templates.

If a file has been intentionally removed from the authored source, `source sync` will also stop rather than delete the old generated file. Review the removal, delete that generated file explicitly, then run `source reconcile` to remove its provenance entry. Reconciliation refuses to delete it on your behalf.

## Develop from the canonical source

The canonical tree is `platform/minecraft/src`. Every `.java` file passes through the projection pipeline, even if it contains no Liquid control lines. The current limited template syntax permits whole-line `{% if features.name %}`, `{% elsif ... %}`, `{% else %}` and `{% endif %}` directives. Larger version differences should live in separately selected implementation files. Do not use a runtime boolean to hide code that must be absent from a JAR: Java still compiles both sides of an ordinary `if`.

Use `sfm-propagate-changes source dry-run`, `sync` and `check` with explicit `--repo-root`, `--target`, `--preset` and `--output-root` values. Put development outputs in a separate local root, never over a checked-in `mc-version` project. The first development pilot supports `current-development-pilot` on `1.19.2`; `current-development-no-echo` is a small example of the same source with one action compiled out. These pilots currently need Gradle's `-PsfmProfile=rust-toolchain` profile because the newer canonical Java source imports Vox/Phon classes excluded by the default Gradle profile. This is a known development-profile mismatch, not a property of the 4.34.0 release preset.

Do not sync development features into the checked-in release roots during ordinary iteration. A release decision advances those roots to a new, immutable preset identity. The exact 4.34.0 tag imports under `platform/minecraft/release-baselines` are pinned evidence and should not be edited to make a build pass.

## Emergency fixes

The existing version branches and release tags remain available. For an urgent fix that must not include unreleased features, branch from the relevant published release tag, apply only the fix, and validate the normal release path. The source-projection branch does not yet replace that route. Publishing a JAR, making `sfm-main` the default branch, and retiring the old merge workflow each require separate acceptance.
