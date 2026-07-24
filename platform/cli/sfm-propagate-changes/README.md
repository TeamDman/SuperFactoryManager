# sfm-propagate-changes

(note that this readme was written before the rust code was written, so it is more eager and may be out of date.)

This tool is responsible for executing the git merge commands that apply changes to later versions of Minecraft.

## Schema v3 dependency and source workflow

For the complete acquisition, cache, and no-fetch source-search guide, see
[Dependency and Source Workflow](../../../docs/dependency%20and%20source%20workflow.md).

The maintained dependency intent and generated resolution checks live together in each branch's
`platform/minecraft/sfm-toolchain.lock.json`. Use an explicit `--branch <Minecraft version>` for
dependency, source, and run commands. Work on the oldest supported branch first; use the tool's
Git propagation workflow only after that branch's gates pass.

Gradle is a compatibility consumer of this schema v3 lockfile. The Gradle scripts project locked
coordinates and semantic scopes into the active ForgeGradle or NeoGradle dialect; do not maintain
versioned dependency declarations separately in Gradle files.

Libraries shipped inside SFM use the `bundle` scope together with an explicit loader policy. The
exact Maven coordinate selects the bytes; `--bundle-accepted-version-range` records the restricted
Maven range that Forge/NeoForge may share with another enclosing mod. The exact artifact version
defaults to the coordinate version and can be stated explicitly for review:

```pwsh
cargo run -- dependency add vox-java --branch 1.19.2 `
  --maven org.facet:vox-java:0.1.3 --kind library --role library `
  --scope compile --scope runtime --scope bundle --artifact-treatment plain `
  --bundle-accepted-version-range '[0.1.0,0.2.0)' `
  --bundle-artifact-version 0.1.3
```

The initial plain-Java packaging policy requires `is_obfuscated=false`; the CLI therefore uses
that safe default and rejects its opt-in obfuscation flag. Both the Rust packager and Gradle
projection consume the same locked range and exact version.

Binary artifacts and materialized source trees are managed cache state. By default they are kept
in the platform cache directory; set `SFM_PROPAGATE_CHANGES_CACHE` to use an isolated cache. The
portable `$sfm-cache` paths recorded in the lockfile resolve beneath that cache and must not be
replaced with machine-specific paths.

Inspect providers before acquiring sources:

```pwsh
cargo run -- dependency source provider list cc-tweaked --branch 1.19.2
cargo run -- dependency source acquire cc-tweaked --provider any --branch 1.19.2
cargo run -- dependency source search IPeripheralProvider --dependency cc-tweaked --branch 1.19.2
```

Search never acquires sources. If a requested provider has no materialized root, it reports the
gap and prints the typed `dependency source acquire` command needed to populate it.

`CurseForge` discovery is deliberately separate from lockfile mutation. Search projects and list
version/loader-filtered files with `curseforge mod search` and `curseforge mod files`, then choose
the exact IDs yourself. `dependency add` accepts both `--curseforge-project` and
`--curseforge-file`, validates them through the Core API, and resolves only the resulting
CurseMaven coordinate. Core API credentials resolve from `--api-key`, then
`CURSEFORGE_CORE_API_KEY`, then an explicit or configured `1Password` secret. For a sequence of
read-only discovery commands, set that environment variable once in the current shell instead of
placing a key on the command line:

```pwsh
$env:CURSEFORGE_CORE_API_KEY = & op read '<your-1password-secret-reference>'
cargo run -- curseforge mod search Mekanism --minecraft 1.19.2 --loader forge
cargo run -- curseforge mod files 268560 --minecraft 1.19.2 --loader forge
```

This keeps the key out of command history and prompts `1Password` only once for that shell.

## Cross-version source audit

After making a change on `1.19.2`, check its propagation boundary before merging it into later
versions:

```pwsh
cargo run -- audit --branch core --version-surfaces
```

The report warns about CLI commits made directly on later version branches (including merge
resolutions that modify the CLI), any later CLI source tree that differs from `1.19.2`, and Java diff hunks outside an
`@MCVersionDependentBehaviour` declaration. Propagation merges whose CLI tree exactly matches a
parent are treated as expected propagation, not warnings. The audit is advisory so existing Java
surface-area debt remains visible without blocking unrelated work.

For example:

Exists:

- `C:\sfm\repos\1.19.2\` (repo root)
- `C:\sfm\repos\1.19.4\` (git worktree)
- `C:\sfm\repos\1.20\` (git worktree)

Changes:

- `C:\sfm\repos\1.19.2\platform\minecraft\src\SFM.java` is modified

Propagation:

- `sfm-propagate-changes repo-root set C:\sfm\repos\1.19.2`
    - The canonicalized path is saved to `$SFM_PROPAGATE_CHANGES_HOME\repo_root.txt`
- `sfm-propagate-changes.exe` is ran
    - `$SFM_PROPAGATE_CHANGES_HOME\repo_root.txt` is read to identify the repo root
    - The set of branches (repo root + worktrees) is determined
    - Non-version worktrees such as feature branches are skipped; only semver-like Minecraft version branches participate in propagation
    - The branches are sorted by semver
    - If there are any uncommitted changes in any of the branches, bail
    - The branches are merged via sliding window size 2, oldest to newest
        - 1.19.2 is merged into 1.19.4
        - if there are any merge conflicts: bail informing user to manually resolve conflicts
        - merge commit
        - 1.19.4 is merged into 1.20
        - if there are any merge conflicts: bail informing user to manually resolve conflicts
        - merge commit

Note that if there is a merge conflict and the user's resolution of the merge conflict results in no files being changed in the merged-into repo, then an empty commit is necessary to complete the merge.

So it's like

```rust
enum State {
    Idle,
    MergingWithConflict {
        source: (BranchName, PathBuf),
        dest: (BranchName, PathBuf)
    }
}
```

The state is saved to `$SFM_PROPAGATE_CHANGES_HOME/state.json`

When the CLI is ran, if the state is `Idle`, then it will initiate the merge oldest to newest behaviour.
If during that behaviour a merge conflict is detected, then the state is updated to MergingWithConflict and the program bails.

When the CLI is ran, if the state is `MergingWithConflicts`, then it will check if there are any remaining merge conflicts.
If merging with any conflicts, it will bail indicating that the user needs to resolve the conflicts.
If merging with no conflicts, then it will commit using a commit message like "Propagate changes: merge 1.19.2 into 1.19.4"
If not in the middle of a merge, change state to Idle and start over.
