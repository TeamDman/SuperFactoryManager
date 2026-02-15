# sfm-propagate-changes

(note that this readme was written before the rust code was written, so it is more eager and may be out of date.)

This tool is responsible for executing the git merge commands that apply changes to later versions of Minecraft.

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