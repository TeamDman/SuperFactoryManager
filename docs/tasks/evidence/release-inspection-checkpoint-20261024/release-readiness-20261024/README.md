# Projection release command readiness — 2026-10-24

**JAR building is projection-aware; live CurseForge and Modrinth publishing is not yet ready for a projection-based release.** This is a read-only audit, not a publication attempt. Source is `704ddb5485173093cad42c07975fa5aabfc247ed` on `feature/release-inspection-combined-20261024`. The repository remains clean. An independent reviewer reached the same conclusion; see `independent-review.md` for line-level findings and reviewed tests.

## Current command syntax

These command forms are established by seven successful `--help` invocations, captured in adjacent `*.help.txt` files and `help-commands.json`. Placeholder forms below document the interface; they were not executed as release actions.

```text
sfm-propagate-changes jar build --repo-root <repo> --projection '<key-or-glob>' [--java-home <jdk>] [--dry-run] [--plan-json <path>] [--output-name <filename.jar>] [--parallel[=N]] [--error-action bail|continue]
sfm-propagate-changes source project jar --repo-root <repo> --projection <exact-key> --released-recipe <recipe> --java-home <jdk>
sfm-propagate-changes curseforge release now --branch '<selector>' [--project <numeric-id>] [--dry-run]
sfm-propagate-changes modrinth release now --branch '<selector>' [--project <id-or-slug>] [--dry-run]
sfm-propagate-changes jar collect
```

`source project jar` also accepts `--dependency-profile <declared-profile>` instead of `--released-recipe`, for development projections. The two options are mutually exclusive. Its shared help advertises `--cache-root`, but JAR execution explicitly rejects it; that flag is compile-only. This command has no dry-run option.

The actual successful 4.35.0 build used this command (4.34.0 used the same command with only the projection key changed):

```bash
source /workspace/.sfm-env/env.sh
/workspace/.sfm-env/target/debug/sfm-propagate-changes source project jar \
  --repo-root /workspace/SuperFactoryManager \
  --projection sfm-4.35.0/mc-1.19.2 \
  --released-recipe sfm:released-native-inputs/4.34.0/1.19.2@1 \
  --java-home /workspace/.sfm-env/jdk17
```

The 4.34.0 recipe is the existing frozen dependency baseline, not the selected mod version. The projection catalog supplies mod version 4.35.0. Both are newly source-built inputs; no claim is made that the baseline equals a historical published JAR.

The matrix selector is:

```bash
sfm-propagate-changes jar build --repo-root /workspace/SuperFactoryManager --projection 'sfm-4.35.0/*'
```

This form was source/help-verified, **not executed as a complete release matrix here**. It selects ten catalog targets: 1.19.2, 1.19.4, 1.20, 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.21.0, 1.21.1 and 26.1.2. Target 1.21.0 maps to actual Minecraft 1.21. A single JDK17 override must not be assumed suitable for all ten targets.

## What has been adapted

- `jar build` selects catalog keys or globs, checks generated projection state, resolves the target's reviewed frozen recipe, and supports per-target failures, parallelism and plan output. Named `source project jar` accepts an exact catalog key and checks the context/source hashes again before packaging.
- Build results carry projection preparation, recipe/source identities, compiler release, tool JVM, actual SDK, output path, content hash and compile/package status. Runtime compatibility is reported separately from successful packaging.
- Native output is in the selected projection's `build/libs`, normally `Super Factory Manager (SFM)-MC<mc>-<version>-rust.jar`. `--output-name` is create-only and requires exactly one projection.
- The existing verified-package/provider-plan tools cover the ten release targets, typed loader/Java metadata, hashes and changelog intent. They are useful components for the missing publishing integration.

## What still prevents projection publishing

| Area | Current behavior | Required adaptation |
| --- | --- | --- |
| Selection/version | Both live publishers use `--branch`, matching Git worktrees and repository-level `platform/minecraft/gradle.properties`; that file still says 4.34.0. | Select exact catalog projections and obtain release version from the selected context. |
| JAR discovery | Both read a configured shared JAR directory and require `-<version>.jar`; native `-<version>-rust.jar` is excluded. `jar collect` still scans worktrees and their old `platform/minecraft/build/libs` locations. | Collect selected projection outputs into a verified manifest, retaining recipe/source and content hashes. Renaming alone does not provide provenance. |
| Completeness | Filename filtering only requires a nonempty result; missing selected targets or duplicate target files can pass. | Require the exact requested target set with one reviewed artifact per target. |
| Changelog | Both use the root source changelog and root mod version. | Bind an explicit reviewed changelog body/hash to the intended release. |
| Platform metadata | IDs come from configuration or `--project`; loaders/Java are inferred from Minecraft version. CurseForge resolves numeric game-version IDs only in the live path. | Cross-check typed projection metadata, target aliases, explicit 1.20.1 loader policy, project intent and provider IDs. |
| Upload bytes | Live publication does not compare against a reviewed build digest. Modrinth owns bytes after preparation, but does not establish that they match the reviewed projection build. | Use verified package hashes and bounded owned reads before transport. |

Modrinth's built-in public project default is `aecUorJQ`; CurseForge uses its configured project default. This audit did not inspect credentials or project configuration and did not establish remote ownership or ID validity. Loader rules currently use Forge through 1.20, Forge plus NeoForge for 1.20.1, then NeoForge. CurseForge's Java mapping includes Java25 for 26.x. These rules exist, but do not replace artifact provenance checks.

## Dry-run and stronger preparation routes

- `jar build --dry-run` resolves and prepares before skipping the final build/launch. It can update generated/cache state; it is not a pure read-only audit. It was not run during this audit.
- Both live `release now --dry-run` commands skip secret resolution and upload HTTP clients, but retain legacy root/worktree/shared-directory assumptions and interactive confirmation. CurseForge dry-run does **not** resolve numeric provider IDs, so it cannot establish provider readiness. Neither dry-run was executed here; only their help and implementation were inspected.
- `source legacy release-provider-plan` is read-only verified-package/provider-intent planning, with explicit reviewed hashes. Its exact interface is:

```text
sfm-propagate-changes source legacy release-provider-plan \
  --package-root <absolute-package-dir> \
  --completion-manifest-sha256 <sha256> \
  --reviewed-source-commit <full-commit> --reviewed-tag <tag> \
  --github-repo <owner/repo> --modrinth-project <id-or-slug> \
  --curseforge-project <numeric-id> \
  --changelog-file <absolute-file> --changelog-sha256 <sha256> \
  --modrinth-1201-loader-policy <dual-forge-neoforge|neoforge-only>
```

`source legacy release-modrinth` takes those same arguments plus `--repo-root <repo> --target-id <target> --reviewed-release-commit <commit> --request-metadata-sha256 <sha256>`. Despite its name, it only prepares one verified request (JAR at most 64 MiB), drops the owned upload, and **never uploads**. The package pipeline remains based on historical presets/candidate locks; it is not directly connected to current catalog selection/build receipts. Provider-plan alone does not verify HEAD/tag objects, remote project ownership or CurseForge numeric version IDs. The target-plan layer adds local HEAD/tag/tree checks, not remote validation or upload authorization.

## Test boundaries and handoff

Previously tested in this task: real 4.34.0 and 4.35.0 MC1.19.2 JAR builds; Dev compile/package and actual 1.19.2 inspection/picker client qualification. Release JAR SHA-256 values:

- 4.34.0: `e719d3999e23c19a6289290157d5cc24813972f0c6d438fb976c3e4bacdf2541`
- 4.35.0: `95f394db8e824815d60d865bbb9bb56be52974bf7d3c5baa38b29b11f241d36f`

Audit checks: seven CLI help calls exit0, direct implementation/test inspection, independent reviewer, no repository changes. Native source is unchanged between the inspected binary's build commit `c5fd329f19c0280cbe4c375c240a783e73ee2224` and current HEAD.

Not run: full ten-target build/runtime matrix, end-to-end projection publication, remote provider-ID/ownership checks, live publishing or publisher dry-runs, full check-all. Existing aggregate native test execution stopped before JUnit on unadmitted Maven metadata; the minimized proposal remains separate and unadmitted.

A source-evidenced Linux test mismatch also remains: `core_project_cli.rs:527` expects NeoForm preflight success only on Windows, while `engine_named_nfrt_compile.rs:61` accepts Windows or Linux. Independent review confirmed it; it was not fixed or executed in this read-only audit.

Pending implementation prerequisite: connect catalog selection → verified complete release package → reviewed provider request → transport, with deterministic missing/duplicate/stale-hash/version/changelog tests. No publication action was attempted, denied or left uncertain. Desktop main integration remains owned by the separate desktop task. No push, merge, pin change, metadata admission, security change or Library retry occurred in this audit.
