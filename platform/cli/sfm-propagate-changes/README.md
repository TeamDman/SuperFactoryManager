# sfm-propagate-changes

(note that this readme was written before the rust code was written, so it is more eager and may be out of date.)

This tool is responsible for executing the git merge commands that apply changes to later versions of Minecraft.

## Rust parser and serialization baseline

The CLI uses the published `teamy-facet` family at `0.50.0-rc.7` and
`teamy-figue` at `6.0.0-rc.2`, matching Cloud-Terrastodon revision
`ea90bf830a4d38c83aeb2d9cec76b2b07dcec4bb`. The canonical Cargo aliases remain
`facet`, `facet-json`, `facet-pretty` and `figue` for their derive integrations.

CSV support comes from `TeamDman/facet-format` revision
`1bd90d49eb0d9f78d00b14ba85d79612dd78c927` because there is no published
`teamy-facet-csv` package. Its sibling-path dependencies are patched to the same
published Teamy reflection/format packages rather than a second reflection identity.

Styx has no published Teamy adapter for this baseline. The private
`crates/sfm-styx-state` package therefore retains the previous immutable Styx
source solely to preserve existing `state.styx` bytes and schema. Its old Facet
types are private wire types; only an owned status and strings cross into the
CLI. Compatibility tests compare both statuses and escaped/Unicode paths to the
original state schema and path proxy. The Java Vox artifact baseline is independent
of the Rust parser graph and is unchanged by this migration.

The JSON baseline no longer exposes the old `JsonWeavyPlan` optimization. The
dependency-index reader uses typed `facet_json::from_str` for retained records;
its routing filters, count validation and stream schema remain the same.

## Build and run projections

From the SFM repository root:

```pwsh
sfm-propagate-changes source list --repo-root .
sfm-propagate-changes run client --projection sfm-dev/mc-1.19.2
sfm-propagate-changes run compile --projection sfm-dev/mc-1.19.2
sfm-propagate-changes jar build --projection sfm-4.34.0/mc-1.19.2
sfm-propagate-changes game-test list --projection sfm-dev/mc-1.19.2
```

Shared build, run, test and puppet commands require `--projection`, with no
`--branch` alias. Add `--repo-root` when targeting another checkout.
See the [projection contributor guide](../../../docs/source-projection-contributor-guide.md)
for hotswap, feature combinations and generated-source ownership.

## File hashes

Use the built-in helper instead of installing a separate hashing executable:

```pwsh
sfm-propagate-changes hash file "artifact.jar"
sfm-propagate-changes hash file "artifact.jar" --algorithm sha256
sfm-propagate-changes hash file "artifact.jar" --algorithm blake3 --length 20
sfm-propagate-changes --output-format json hash file "artifact.jar" --algorithm sha256
```

BLAKE3 defaults to 32 output bytes. Explicit `--length 20` produces the
existing toolchain-lock identity; `--length` counts bytes, not hexadecimal
characters. SHA-256 and SHA-1 use their full digests and reject `--length`.

The report includes the algorithm-prefixed identity and number of bytes read.
The command uses a fixed-size streaming buffer, opens files read-only, and
rejects directories, inspected symlinks/reparse points and observable changes
while reading. Requested and canonical paths are re-opened before and after
reading to verify they still name the same file. These are point-in-time
checks, not an immutable snapshot or a hostile-path sandbox; same-object
writes that restore metadata and replacements after the final check remain
possible. It does not install tools, acquire dependencies or edit locks.
SHA-1 is provided for compatibility with existing artifacts, not as a security
recommendation.

## Schema v3 dependency and source workflow

For the complete acquisition, cache, and no-fetch source-search guide, see
[Dependency and Source Workflow](../../../docs/dependency%20and%20source%20workflow.md).

The maintained dependency intent and generated resolution checks live together in each branch's
`platform/minecraft/sfm-toolchain.lock.json`. Use an explicit `--branch <Minecraft version>` for
dependency commands. Build, run and test commands instead require a catalog
projection (see below). Work on the oldest supported branch first for dependency maintenance; use the tool's
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
CurseMaven coordinate. Dependency edits preserve schema-4 features and profiles, including
components that are inactive in the current build profile.

## Temporary CurseForge discovery login

On Windows, authorize a bounded discovery session explicitly:

```pwsh
cargo run -- curseforge auth login --purpose discovery --ttl 20m
cargo run -- curseforge mod search Mekanism --minecraft 1.19.2 --loader forge
cargo run -- curseforge mod files 268560 --minecraft 1.19.2 --loader forge
cargo run -- curseforge auth status
cargo run -- curseforge auth logout
```

Only `auth login` reads the configured Core API key from 1Password. Pass
`--op-secret '<Core-key-reference>'` to login to select a different item. A TTL of 1 second
through 1 hour is required; use `10m`, `20m` or `1h`. Repeated discovery commands reuse the lease
without opening further 1Password prompts. Missing or expired authentication fails with a login
command rather than silently requesting a secret.

The lease is DPAPI-encrypted for the current Windows user and stored outside the repository in
the application's private local-data directory with a user-only ACL. A native scheduled task
deletes its exact lease file and unregisters itself. It can catch up after a missed run, and it
cannot delete a newer lease. Request-time expiry checks do not depend on the task running on time.
The `auth cleanup --lease <generated-name>` command is the task's narrow cleanup entry point;
it does not accept arbitrary file paths. Logout removes all of this application's discovery
leases, not the underlying API key. Expiry also does not revoke that key upstream, and DPAPI is
not protection against another process running as the same Windows user.

Explicit `--api-key` and `CURSEFORGE_CORE_API_KEY` remain available, including on other platforms.
They are process-supplied credentials, not TTL-managed leases; prefer login over putting secrets
on command lines. Discovery never falls back to an author/upload token and only sends its key
to the Core API origin for GET requests. Download URLs use an unauthenticated client.

Publishing uses the separate author API and requires explicit per-invocation authorization:
`--op-secret '<author-token-reference>'` or `--token`. It cannot use discovery leases or silently
inherit `CURSEFORGE_API_TOKEN`. No upload is performed by login, search or file listing.

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

The report class explorer is losslessly DEFLATE-compressed in the single HTML using the existing ZIP dependency. JavaScript expands it locally with `DecompressionStream`, verifies the expanded size and SHA-256, and enforces a 32 MiB expansion budget. Full package/class navigation and class/debug details require JavaScript and a compatible browser. Without JavaScript or expansion support, the ten-projection status summary, representative instruction diff, authored source diffs, all captures and full JAR provenance remain readable. The optional pinned Monaco viewer is still desktop-only; mobile uses the expanded static diffs. No game evidence is fetched or rebuilt.
