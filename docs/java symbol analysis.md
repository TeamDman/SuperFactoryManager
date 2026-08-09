# Java symbol analysis

`sfm-propagate-changes` provides read-only Java definition and usage queries
for SFM worktrees and for tiny isolated source scenarios. These commands do not
invoke Gradle, javac, dependency acquisition, or source mutation.

## Commands

```powershell
sfm-propagate-changes.exe symbol definition ca.teamdman.sfm.SFM --branch 1.19.2
sfm-propagate-changes.exe symbol usage list ca.teamdman.sfm.SFM --branch 1.19.2
sfm-propagate-changes.exe --output-format json symbol usage list ca.teamdman.sfm.SomeType 'method(Ljava/lang/String;)V' --branch 1.19.2
```

The selector grammar follows Access Transformer targets:

- `package.Type` selects a class, interface, enum, record, or annotation.
- `package.Type FIELD` selects a field.
- `package.Type method(JVMDescriptor)ReturnDescriptor` selects one method
  overload, for example `example.A run(Ljava/lang/String;)V`.
- `$` identifies nested classes, for example `example.Outer$Inner`.

Dotted-member aliases and descriptor-free method guesses are intentionally not
accepted. A zero-match result exits with status 2, an ambiguous result exits
with status 3, and both still emit their typed report. Successful queries exit
with status 0.

## Source and classpath modes

Without `--source-root`, the selected branch contributes every configured Java
source set and generated Java root. Source-set visibility remains directed:
production `main` cannot see test declarations, while `test`, `gametest`, and
`datagen` can see `main`. Version and inactive-feature exclusions use the same
inputs as the Rust build toolchain.

Custom roots are repeatable and require an explicit classpath mode:

```powershell
sfm-propagate-changes.exe symbol definition example.A `
  --branch 1.19.2 `
  --source-root source `
  --classpath-mode isolated
```

- `branch` retains the selected branch's declared dependency context.
- `isolated` includes only the supplied source roots and the Java release
  selected by the branch. It does not read ambient `CLASSPATH`, project class
  outputs, Minecraft, loader, or mod jars.

A custom root ending in `<source-set>/java`, such as `source/main/java` or
`source/test/java`, retains that known source-set identity and the branch's
directed visibility rules. Other custom roots share the isolated `custom`
source set. This convention lets tiny scenarios prove main/test visibility
without creating a Gradle project.

All paths in reports are worktree- or source-root-relative. Reports include
the branch, Minecraft and Java context, roots, source-set visibility,
exclusions, source hashes, parser/index fingerprints, and the selected
classpath mode/fingerprint.

## Output

`--output-format text|json|csv` selects the rendering. The default is text for
an interactive terminal and JSON when stdout is redirected. JSON schemas are
versioned as `sfm.symbol-definition/1` and `sfm.symbol-usage-list/1`.
CSV starts with a report row carrying the outcome/status, selector, workspace
context, and fingerprints, followed by definition/usage and diagnostic rows.
That report row keeps zero-match and ambiguity distinct even when there are no
result rows. Diagnostics and logs remain on stderr; one typed report is emitted
on stdout.

## Analysis scenarios

Read-only scenarios live adjacent to their Rust harness:

```text
platform/cli/sfm-propagate-changes/tests/java_analysis/
  java_analysis_scenario_test.rs
  scenarios/<scenario>/
    command.ps1
    source/
    output-expected.json
    output-actual.json
```

Run them with:

```powershell
Set-Location platform/cli/sfm-propagate-changes
cargo test --all-features --test java_analysis_scenarios
```

The harness parses the single documented CLI command without starting
PowerShell or an installed executable and asserts both the canonical JSON and
the public exit status implied by its typed outcome. When a snapshot differs, it preserves
the ignored adjacent `output-actual.json` and prints the exact `Copy-Item`
command needed for explicit acceptance. It never updates expected output.

## Mutation is deferred

`symbol rename` and `symbol move` parse their future public argument contract,
including exactly one of `--dry-run` or `--apply`, but Phase 0 returns a typed
unsupported report (exit status 4) before source discovery or mutation. No
Phase 0 symbol command writes Java source files.
