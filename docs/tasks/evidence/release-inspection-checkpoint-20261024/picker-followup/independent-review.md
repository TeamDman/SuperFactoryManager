Independent review of picker UI qualification, 2026-10-24.

Reviewed production HEAD `704ddb5485173093cad42c07975fa5aabfc247ed` (Explorer/picker code through `e192e234`), the test-only fixture source, all 134 final event records, and screenshots 84, 86 and 108. No production edits, builds, launches, network requests, pushes or merges were performed by this reviewer. This file is the only reviewer-written artifact.

The final README/event-assertions counts agree with the trace: 134 events, 13 picker cycles, 9 preference changes, 4 cancellations, 10 opens, 4 external factory calls and 9 save callbacks. Its V2 attempted Ctrl+S limitation and separate V3 unedited-save failure are accurately distinguished. Cleanup/launcher exit claims in final-state.json are the parent's execution receipts, not independently executed reviewer checks.

**Picker checks supported by the recorded execution**

- All 13 picker cycles preserve every observed document-state field throughout the picker transition, including text, saved-file hash, active editor identity, factory count and, where recorded, widget/context identity and cursor/selection. Independently checked cycles: events 13–15, 18–20, 23–25, 39–41, 48–50, 55–57, 67–69, 76–78, 87–89, 92–94, 104–106, 117–119 and 129–131, each compared with its preceding state.
- Cancellation retains the prior preference. Apply changes only the preferred ID within these observed fields; it does not instantiate the selected editor or replace the active document.
- Two distinct external IDs use normal Forge DeferredRegister registration and production V1 factories. Screenshot 84 visibly distinguishes their IDs despite identical translated display names. Factory callbacks occur after subsequent open requests at events 30, 64, 101 and 126.
- Restart events 125–127 open `sfm_picker_fixture:external_alt` through its normal factory, with the per-process factory count reset to 1 and exact saved text. Screenshot 108 corroborates the persisted preference. Events 129–134 restore `sfm:v1`; the final `sfm-client-program-editor.toml` also contains `preferredEditor = "sfm:v1"`.
- No fixture_error or observer_error appears in the final log. The fixture v2 canvas observation checks initialContentLoaded before calling currentDocumentText(), so it does not trigger initialization merely to observe text.

**Failed preservation checks, separate from picker overlays**

V2 open events 45–46 and V3 open events 82–83 each change `79def945…` to `d34a124d…`, exactly removing one terminal LF. V3 can recover that LF through its exact-baseline editing path; this is not an unconditional permanent loss after every edit. However, the unedited V3 open→Done sequence at events 110–112 conclusively saves the input minus one terminal LF: `67884ddd4056d41c0ae39e75e3e4582f26222acb99eddfaa41d2ef5e257aacbe` becomes `8e4e403a0309f6bb7b21b7eb889b53369f94285d63721ce9707793fb16948b56`.

Source causes, relative to `platform/minecraft/core-liquid-template/src/main/java/ca/teamdman/sfm/client/`:

- V2 initializes TextEditContext at `screen/text_editor/SFMTextEditScreenV2.java:122`. `text_editor/TextEditContext.java:25` uses `split("\n")`, discarding trailing empty fields; getContent at lines 250–258 rejoins the remaining lines.
- V3 loads through `screen/SFMDrawCanvasScreen.java:2354`. `screen/SFMDrawCanvasModel.java:359–362` stores LF only as geometry; `screen/SFMDrawCanvasDocumentIndex.java:114–145` reconstructs newlines between glyph rows, so terminal empty rows are absent. `SFMDrawCanvasScreen.java:3712–3729` exposes that projection, and saveDocumentInternal at line 3637 saves it. The separate baseline path at lines 2914–2923 explains why a later exact edit may restore the original terminal LF.
- All five source files named above are unchanged against migration commit `0918604ac558906c93a9c4ab449400e8f75792ce` (`git diff --name-only` returned no differences). These are preexisting editor-initialization/save defects, not picker overlay mutations. No fix was attempted.

**Limits and remaining prerequisite**

The extension factories delegate the production V1 renderer. V3 cursor/selection, undo history, scroll preservation and server/inventory saves are not qualified here. V2 save is untested: screenshot 93 names an attempted action, while events 59–65 show discard and subsequent reload of the prior saved text without a V2 save callback. The file-backed save checks must not be described as server-save checks.

Aggregate tests remain pending reviewed offline metadata admission for `com.github.javaparser:javaparser-parent:3.26.4@pom`: the cached POM and matching origin entry in `/workspace/.sfm-env/cache/minecraft-toolchain/maven/maven-metadata-pins.json` are absent. Retrieval into scratch succeeded (25,473 bytes, SHA-256 `0316ad7574a30bdacae02e45d88edda46578bb5a469cbd65d783787eeb82cca8`). No network-policy change is needed for that retrieved URL, no pin was applied, and further metadata closure remains unverified.

Final evidence binding:

- Events: `/workspace/SuperFactoryManager/platform/minecraft/projections/sfm-dev/mc-1.19.2/build/sfm-runtime/slots/2/run/picker-fixture/events.jsonl`; SHA-256 `c139fac07ae568e3e6745043b95618743a4a1772cbedf608f8d946784674066e`.
- The preserved copy `/workspace/scratch/release-inspection/picker-followup/events.jsonl` has the same SHA-256.
- Fixture source: `/workspace/scratch/release-inspection/picker-followup/fixture/src/qa/sfm/PickerFixture.java`; SHA-256 `dcb6d3501e233214236890ac467db5fe62cec189aaecd7cb3b8729f9874826ac`.
