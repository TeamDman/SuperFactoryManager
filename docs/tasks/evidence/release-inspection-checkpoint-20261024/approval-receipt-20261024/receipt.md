# Proposed metadata admission — not applied

Coordinate: `com.github.javaparser:javaparser-parent:3.26.4@pom`.

| Field | Exact value |
| --- | --- |
| Maven URL | https://repo1.maven.org/maven2/com/github/javaparser/javaparser-parent/3.26.4/javaparser-parent-3.26.4.pom |
| Algorithm | SHA-256 |
| Hash | `0316ad7574a30bdacae02e45d88edda46578bb5a469cbd65d783787eeb82cca8` |
| Bytes | `25473` |
| Existing scratch copy | `/workspace/scratch/release-inspection/picker-followup/metadata/javaparser-parent-3.26.4.pom` |
| Previous retrieval | HTTP 200, `2026-10-24T18:14:02.552318+00:00`; no retrieval in this receipt step |

This is the parent POM referenced by the embedded POM of the already locked
`javaparser-symbol-solver-core:3.26.4`, declared for `test-compile` and `test-runtime`.
It supplies dependencyManagement versions for already locked
`org.javassist:javassist:3.30.2-GA` and `com.google.guava:guava:33.4.0-jre`.
It has no parent POM of its own. The existing native resolver reads metadata;
this proposal installs no JAR or executable, executes no Maven plugin, and changes
no dependency declaration, version, lockfile, repository list, trust, or network rule.

The proposal is limited to these two currently absent files:

1. Copy the exact verified scratch POM to
   `/workspace/.sfm-env/cache/minecraft-toolchain/maven/com/github/javaparser/javaparser-parent/3.26.4/javaparser-parent-3.26.4.pom`.
2. Create `/workspace/.sfm-env/cache/minecraft-toolchain/maven/maven-metadata-pins.json`
   with the complete JSON from
   `/workspace/scratch/release-inspection/picker-followup/metadata/proposed-entry-NOT-APPLIED.json`:

```json
{
  "schema": "sfm:maven_metadata_provision@1",
  "repository": "https://repo1.maven.org/maven2",
  "artifacts": [
    {
      "coordinate": "com.github.javaparser:javaparser-parent:3.26.4@pom",
      "url": "https://repo1.maven.org/maven2/com/github/javaparser/javaparser-parent/3.26.4/javaparser-parent-3.26.4.pom",
      "sha256": "sha256:0316ad7574a30bdacae02e45d88edda46578bb5a469cbd65d783787eeb82cca8",
      "bytes": 25473,
      "path": "com/github/javaparser/javaparser-parent/3.26.4/javaparser-parent-3.26.4.pom",
      "retrievedUtc": "2026-10-24T18:14:02.552318+00:00"
    }
  ]
}
```

If either destination appears before admission, inspect and reconcile it rather
than overwriting it. The offline resolver requires this origin/path/hash/size
receipt because parent/BOM bytes can influence dependency expansion. The earlier
HTTP 200 establishes that existing access to `repo1.maven.org` suffices; no new
network permission is requested. No other missing POM/BOM coordinate has been
confirmed. The runner stopped at this first miss, so further closure is unverified.
An earlier uncached `javaparser-core:3.26.4` JAR observation is a separate artifact
cache miss, not an additional metadata receipt or authority to fetch it here.

## Precise newline-loss sources and reproductions

Source paths below are relative to
`/workspace/SuperFactoryManager/platform/minecraft/core-liquid-template/src/main/java/ca/teamdman/sfm/client/`.

- **V2:** `screen/text_editor/SFMTextEditScreenV2.java:122` constructs TextEditContext
  from the initial text. `text_editor/TextEditContext.java:25` calls `split("\n")`,
  discarding empty trailing fields; `TextEditContext.java:250` rejoins retained lines.
  Minimal reproduction: `new TextEditContext("x\n").getContent()` returns `"x"`.
- **V3:** `screen/SFMDrawCanvasScreen.java:2354` loads through model.replaceText.
  `screen/SFMDrawCanvasModel.java:359` handles LF as a coordinate change without a
  glyph. `screen/SFMDrawCanvasDocumentIndex.java:114` emits LF between occupied
  glyph rows; line 145 does not synthesize a terminal empty row.
  `SFMDrawCanvasScreen.java:3712` exposes that reconstructed text and line 3637
  passes it to saveDocument. Minimal model reproduction: replaceText(`"x\n"`,
  constant glyph width 1, line height 9), then documentIndex(1,9).projection().text()
  returns `"x"`. The separate baseline path at line 2914 can restore LF after
  certain edits; it does not protect the reproduced unedited Done path.

Both minimal examples were executed against the already-qualified production
classes with the existing JDK and classpath. Command:

```bash
python /workspace/scratch/release-inspection/approval-receipt-20261024/run_repro.py
```

`TrailingNewlineRepro.java`, `repro-command.json`, `repro.stdout`, and `repro.stderr`
preserve the exact invocation and output. Exit 0 means both expected existing
failures were reproduced; it is not a document-preservation pass.

Actual 1.19.2 UI reproductions already preserved in picker-followup/events.jsonl:

1. Provide a file-backed document ending in LF, choose V2, save/close the current
   V1 editor, then reopen. Events 45–46 record the V2 text missing one terminal LF.
   V2 saving was not qualified.
2. Choose V3, open a file ending in LF, make no edit, and click Done. Events
   110–112 record input SHA `67884ddd4056d41c0ae39e75e3e4582f26222acb99eddfaa41d2ef5e257aacbe`
   and saved SHA `8e4e403a0309f6bb7b21b7eb889b53369f94285d63721ce9707793fb16948b56`;
   the saved text is exactly the input minus its terminal LF.

All five relevant production source files are unchanged from migration
`0918604ac558906c93a9c4ab449400e8f75792ce`. No fixes were made.
The clean checkout remains `704ddb5485173093cad42c07975fa5aabfc247ed`.
Both completed evidence ZIP hashes were rechecked unchanged. No cache/catalog
mutation, dependency fetch, Library retry, push, or merge occurred in this step.
