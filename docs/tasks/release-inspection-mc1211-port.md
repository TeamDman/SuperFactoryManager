# Release inspection on Minecraft1.21.1

Branch: `feature/explorer-mc1211-port-20261024`, based on reviewed
`704ddb5485173093cad42c07975fa5aabfc247ed`. This is the cloud Explorer port;
launcher/puppet deferred opening and desktop human handoff remain owned by the
desktop demo task. No main merge or publication is part of this branch.

## Scope

The explicit `inspection_workspace` feature is selected only by
`sfm-4.34.0-issue610-visual/mc-1.21.1` and
`sfm-4.35.0-issue610-visual/mc-1.21.1`. It brings the existing comparison runtime,
source-map syntax highlighting, Nucleo matching, Explorer, read-only V3 document
panels and owned-preview policy to the NeoForge/GuiGraphics APIs. It does not
enable the mutable release-review/comment/history workspace.

The comparison hierarchy and classifiers are unchanged: comparison root →
strategy → file pair → representation. Exact bytes, printed debug-only changes
and instruction differences retain their existing evidence boundaries. No new
semantic-equivalence claim is introduced.

The native inspection choice menu executes the same registered actions as the
existing typed palette. It supports keyboard focus, PageUp/PageDown and Cancel.
Comparison progress/errors remain in the Explorer status strip and narration.
The native1.21 keyboard profile format remains unchanged; Explorer-local Ctrl+F,
Ctrl+Shift+F, F3/Shift+F3 and Find Enter/Shift+Enter use its registered search
commands. Ordinary1.21 Dev retains its original Canvas path and DRAW registration;
inspection adds the panel-capable V3 registration.

Rendering uses GuiGraphics directly, with four-argument scroll adapters. Nested
clip bounds are transformed from panel coordinates into screen coordinates before
entering GuiGraphics' own scissor stack. Mouse selection retains the existing
scroll-preservation policy. External reveal and keyboard navigation retain their
existing reveal behavior.

## Energy semantics

Catalog-rendered `OutputStatement.java` is byte-identical between each visual
overlay and its matching vanilla release. The path is
`src/main/java/ca/teamdman/sfml/ast/OutputStatement.java` within each projection.

| Release | Bytes | SHA-256, plain and visual |
| --- | ---: | --- |
|4.34.0|34602|`38c34a06467ee9f693716f40a630f2b242237fdfa857b4f1863f804bf87a4de2`|
|4.35.0|34685|`15a99b59b2408c9e45e5a12abe76f409cd789f11785eacf0ed91f0a8add888ab`|

No OutputStatement, launcher, puppet, lock-pin, dependency, trust or network-policy
source changes are included. The offline metadata catalog remains SHA-256
`bd9ce82e5f34a251b472178cea292d3739faaf1595682e9371cdf124dd2b05fd`;
the eighteen additional POMs have not been admitted.

## Validation and commands

The direct Java probe passed for848 main sources plus generated ANTLR sources
against the existing locked1.21.1 native classpath, without annotation processors.
This probe is supplementary and is not a full native/client qualification.

Full native before/fixed builds and the1.19.2 Dev regression build are running at
this checkpoint, with source validation enabled. Focused tests exercise preview
ownership, continuation identity, viewport/selection/scroll behavior, read-only
chrome, syntax sessions and scissor intersection. Results will be recorded in a
follow-up qualification commit. No1.21.1 live-client result is claimed yet.

```bash
source /workspace/.sfm-env/env.sh
/workspace/.sfm-env/target/debug/sfm-propagate-changes run compile \
  --repo-root /workspace/SuperFactoryManager \
  --projection sfm-4.34.0-issue610-visual/mc-1.21.1 \
  --java-home /workspace/.sfm-env/approved-temurin21/jdk-21.0.8+9
```

Repeat with `sfm-4.35.0-issue610-visual/mc-1.21.1`. Regression command uses
`sfm-dev/mc-1.19.2` and `/workspace/.sfm-env/jdk17`. The exact native CLI source is
unchanged from the reviewed production basis; no Gradle command is used.

Once the owning launcher has entered the world and opened its action surface:

```text
sfm action invoke sfm:explorer/diff open "/absolute/path/release.sfm-inspection.json"
```

The direct integration seam is `SFMInspectionRuntime.read(Path)` on
`SFMInspectionRuntime.WORKER`, followed by `SFMInspectionRuntime.open(prepared,
context, false)` on the Minecraft client thread. Passing `true` appends roots to
an originating Explorer. The loader must retain continuation/host identity and
report failures before yielding to the human.

**Pending desktop prerequisite:** the current launcher property branch for
`sfm.inspection.workspace` requires absent release-review/launch features. The
desktop owner must connect this inspection-only seam to its deferred in-world
opening and explicit handoff. This cloud branch intentionally does not edit
launcher/puppet files. It cannot yet claim a tested single-command in-world demo.

Independent source review identified and checked fixes for scroll signatures,
scissor transforms, ordinary-Dev isolation, choice/continuation ownership, action
registration, and recipe-less read-only preview metadata. Actual nested rendering,
preview replacement/cancellation and menu-overlay continuation still require the
live1.21.1 client check. The existing1.19.2 screenshots/recording ZIP remain separate
evidence; they are not relabelled as1.21.1 qualification.
