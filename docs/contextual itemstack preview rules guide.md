# Choosing Explorer icons with reusable rules

This is a presentation preference, not a file operation or a review verdict.
Rules change the ItemStack shown for matching entries; they do not rename,
rewrite or approve those entries. The first implementation is Java-only and
works without the desktop companion or an AI service.

## Start from an icon

1. Open an Explorer with Ctrl+Shift+E and find a file such as `abc.json`.
2. Right-click its **icon**, rather than the filename. Keyboard users can
   select the row and use Alt+Enter or the Menu key to reach the same actions.
3. Choose **Choose ItemStack for preview rule · files: suffix is ".json"**.
   This uses the shared searchable item picker. Search for `minecraft:bell`,
   choose it, and confirm with Enter.
4. The draft shows the predicate, captured entry, destination theme file and
   exact file revision. Nothing has been saved yet. Use **Copy command** to
   inspect the underlying command, **Choose icon** to pick again, or **Cancel**.
5. **Save rule** performs the explicit write. Wait for **Saved ItemStack preview
   rule**. Matching entries then use the new icon in every Explorer. The rule
   lives in the active theme TOML and is loaded again when the game restarts.

The regular theme is `config/sfm-client-theme.toml` in the game instance.
`sfm action invoke sfm:theme/open_file` opens it as text;
`sfm action invoke sfm:theme/settings` opens the existing theme editor.
If no valid file-backed theme is active, use
`sfm action invoke sfm:theme/reload` and inspect the icon again. A malformed
theme does not replace the last valid one. Avoid Restore Defaults unless you
really intend to replace your customization.

## Build or refine the command

The same menu offers **Add icon rule** continuations for the file's suffix,
exact name, basename and name prefixes. Selecting an incomplete continuation
builds a command and asks for the missing argument; it does not execute it.
Tab accepts a candidate. Execute is available only for a complete command.
Move the cursor into an earlier parameter to change it without deleting the
arguments after it.

The canonical shape is:

```text
sfm action invoke sfm:explorer/itemstack_preview_rule/add <theme_target> <predicate> <itemstack>
```

Do not type the placeholders literally. The context menu supplies the quoted
theme file URI and its `#sha256=...` revision. Keep that exact target: changing
the theme while a draft or copied command is open makes that command stale.
Inspect the icon again instead of removing the revision check.

A JSON-file predicate looks like this:

```text
sfm:bool/and sfm:entry/is_file sfm:entry/has_suffix ".json"
```

Operators have fixed arity, so composition needs no parentheses: `and` and `or`
take two Boolean expressions; `not` takes one. String comparisons take string
expressions. Quoted values are JSON string literals. Comparison is
case-insensitive using Locale.ROOT, without Unicode normalization. `name`,
`basename`, `path`, file/container kind and known metadata are distinct facts.

The contextual suffix rule replaces the equivalent legacy file-icon mapping
in an existing theme; Reset restores that mapping. It is case-insensitive, so
`.JSON` uses the same suffix rule as `.json`.

User rules outrank mod/default rules. More-specific rules win only where the
engine can prove narrowing. To refine an existing rule unambiguously, combine
its predicate with an additional condition using `sfm:bool/and`. Two
incomparable matching rules with different icons produce an inspectable `!`
and retain a baseline icon; registration order is not a hidden priority.

Right-click the resulting icon for **Reset this authored ItemStack rule**.
Reset removes that exact user rule and exposes the inherited result; it does
not overwrite a mod's rule or restore the entire theme to defaults.

## Ask a chat agent to propose a rule

Two independent clipboard actions are available:

- **Copy entry details to clipboard** captures the selected entry's identity,
  known structured metadata, current rule/rendering evidence and theme revision.
- **Copy prompt for soliciting a new ItemStack preview rule to clipboard** embeds
  those same details plus the registered command and operator signatures.

Paste the second payload into your chosen chat application and describe the
desired icon and scope in `desired_change` or an accompanying message. For
example: “Use a bell for JSON files, but only in this particular directory.”
The prompt includes local paths and known metadata: inspect it before sharing.
It does not acquire source contents. Entry data and contributed descriptions
are explicitly untrusted data, not instructions to the receiving agent.

The export intentionally **does not list the item registry** or enumerate every
possible expression. It describes the item argument's schema and uses already
captured icon IDs as examples. In-game completion and picking remain the way
to discover available items. Copying neither contacts a model nor executes a
returned command. Inspect the agent's answer, paste it into the palette, check
the target and predicate, then explicitly Execute. Unknown items, malformed
expressions and stale theme revisions must be corrected before a write.

## Understand what is shown

The icon menu exposes separate actions for the winning-rule explanation,
title-screen ItemStack rendering, copying item/rule/provider IDs, inspecting
bounds, and cache evidence. Geometry is not buried in a generic Help article.
Known vanilla chests can render without a loaded world; unsupported/missing
renderers may need a fallback. Requested icon, actual rendered icon and fallback
reason are different facts. Current rule decisions use a bounded in-memory
cache; there is no invented persistent cache file to browse.

## Verification and limits

**User install required: no** at the September 6 final overnight checkpoint.
The installed CLI was checked/reinstalled for the earlier Rust changes and
reverified afterward; current Java and generated translations were built and
tested. Start the game with
`sfm-propagate-changes.exe run client --branch 1.19.2 --wait-for-build-lock`.
The acceptance games/helpers exited normally; no restart of a retained test
game or manual installer step is needed.

The implementation and acceptance evidence are tracked under IPR-T5 in
[the rule-authoring plan](tasks/contextual%20itemstack%20preview%20rule%20authoring%20plan.md).
The dedicated authoring/resume puppets use new ignored themes and source files,
virtual inputs rather than the OS cursor, and separate JVMs. They never edit
the maintainer's theme or release review. All 38 focused rule tests passed;
the final full run plus explicitly configured worker integration cover all
1,820 Java tests without failures. This guide is not a claim of cross-version
propagation or absence of unrelated modpack startup diagnostics.

- [GUI 2 authoring](../platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260906-112347-188/index.html): menu, details/prompt clipboard readback, continuation, picker, Save/Cancel, earlier-parameter edit, specificity/reset and two Explorers.
- [Independent restart](../platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260906-112640-790/index.html): another JVM, identical theme/source hashes and the same saved rule in both Explorers.
- [GUI 4 authoring](../platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260906-112840-996/index.html): larger controls, scrollable command body and narrow split rendering. Figures 2/5 were inspected with View Image.

These ignored artifacts are evidence, not your preference authority. The theme
TOML you explicitly save remains the authority.

Async content/AI icon inference, persistent expensive-analysis caches, archive
exploration and a general-purpose expression runtime remain future work.
