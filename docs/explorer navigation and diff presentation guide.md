# Explorer navigation and diff presentation — implementation checkpoint

This guide describes the September 7 source changes. Automated checks and
remaining in-game validation are recorded in
[the implementation plan](tasks/explorer%20navigation%20and%20review%20diff%20presentation%20plan.md).
The subsequent [review revision guide](review%20refresh%20and%20comment%20interaction%20guide.md)
supersedes the original split-diff checkpoint below.

## Explorer

Right-click the location bar, empty Explorer body, or a row (not its icon):

- **Refresh this Explorer** requests fresh children for the current roots.
- **Change location to parent of root · …** replaces the named file root with
  its parent while retaining other roots. The menu captures the root revision;
  if it changes, reopen the menu instead of applying a stale choice.
- **Add parent of root as root · …** retains the original root too.
- A directory row additionally offers **Add directory as root** and
  **Refresh these children**. Adding an existing root is not offered.

Parent navigation is currently offered for native file roots only, and not at
a drive root. Contributed review/registry roots do not invent filesystem parents.
Refresh does not recursively reread every descendant; use the children action
for a nested directory. Neither operation writes to your source files.

The palette command for the focused Explorer is:

```text
sfm action invoke sfm:explorer/refresh
```

## Icons and source colouring

Default icons now distinguish Gradle (anvil), Markdown (book), PowerShell
(nautilus shell), archives (bundle), images (painting), and the inventoried
configuration/world-data extensions. A container named `minecraft` has a
DEFAULT-layer grass-block rule. Existing explicit theme preferences win;
right-click the icon and inspect its rule details if the result differs.

Gradle/Groovy, Markdown and configuration text have bounded local lexical
colouring, not semantic analysis. Documents above 262,144 UTF-16 units remain
neutral on this local path. Java continues using the existing worker.

Inline diff backgrounds have brighter added/removed colours and use exact
source text for decoration offsets. The September 8 revision adds explicitly
labelled inline and single-canvas split text/structured leaves, source-side
selection and comment access. See the linked revision guide and its validation
ledger for the current behavior and evidence.

## Launch

No Rust installer update is required for these Java-only changes. The ordinary
launcher recompiles the current source:

```powershell
sfm-propagate-changes.exe run client --branch 1.19.2 --wait-for-build-lock
```

Do not recreate or overwrite an existing review merely to test these menus.
The new navigation actions operate on the Explorer's presentation state.
