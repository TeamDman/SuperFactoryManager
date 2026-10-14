# Reusable source comparisons

The first frontend is the existing standalone HTML source explorer and diff viewer.
A comparison captures two explicit inputs into one Facet model. Pinned Git inputs
are source trees, not reconstructions of a historical Liquid catalog.

Create a JSON request:

```json
{
  "schema": "sfm:source-comparison-request@1",
  "left": { "Directory": { "root": "D:/explicit/left" } },
  "right": {
    "PinnedGit": {
      "repository": "D:/explicit/repository",
      "commit": "<full 40-character commit object ID>",
      "prefix": "src"
    }
  },
  "policy": {
    "schema_version": 1,
    "allow_generated_banner": false,
    "normalize_crlf": false,
    "allow_final_newline_difference": false,
    "allow_settings_project_identity_override": false
  }
}
```

Directory paths must be absolute. The Git prefix is relative to the pinned
tree; an empty prefix selects its root. Git refs, abbreviated IDs, symlinks,
gitlinks, replacement objects, filters and implicit checkout discovery are
not used. Missing objects are errors, never empty sides.

To render a current catalog projection, replace either side with:

```json
{ "Projection": {
    "repository": "D:/Repos/Minecraft/SFM/worktrees/sfm-main-source-projection",
    "projection": "sfm-4.35.0/mc-1.19.2"
} }
```

Projection capture renders selected core inputs in memory. It does not generate
a project, build Minecraft, or use an old catalog from the pinned Git side.
The established 8 MiB project-metadata limit is separate from the 1 MiB
catalog/feature-registry limit.

Prepare a fresh bundle whose existing parent is outside both input roots:

```text
sfm-propagate-changes --output-format json source compare-prepare --request comparison.json --output D:/explicit/reviews/comparison-001
```

The bundle contains comparison.json, index.html, left/ and right/. CLI stdout is
a small preparation receipt. The JSON report retains original bytes, SHA-256,
per-file modes where known, Git object IDs or projection input provenance,
resolved input identities, explicit transformations and deterministic diff
records. It contains no wall-clock timestamp. The directory adapter excludes
only case-insensitive .git administration paths, and records that exclusion.

Open index.html, and independently compare left/ with right/ in IntelliJ.
Materialized files are read-only and captured bytes are hash-pinned; filesystem
permissions cannot provide tamper-proof immutable storage. Windows directory
executable bits are unknown and are not silently equated with Git modes.
Concurrent edits to local input directories do not yield an atomic filesystem
snapshot. Byte differences alone establish neither behavioral changes nor
class/debug-metadata distinctions; the release class-verification view retains
its independent classification.

Only unique byte-identical removed/added pairs become one logical path move.
Ambiguous duplicate-content candidates remain separate. A move shows both
paths with common segments muted, and retains both sides' raw evidence.

Each file has accessible thumbs-up, thumbs-down and clear controls. All files
must have thumbs-up for full approval. Local reactions bind to the comparison
identity (including byte policy), scope, path pair and raw content hashes.
Reaction edits can be undone. Filters are staged until Apply; reacting and undo
update counts without hiding the current row or preview. Counts describe the
complete comparison, independently of the visible filter. Copy review emits
schema/provenance-bound JSON groups, including unreacted files. When clipboard
access fails, the HTML provides selectable JSON and a local download.

The scroll toggle controls the static paired panes. For optional Monaco, Off
controls the HTML panes. Monaco always uses its genuine synchronized split DiffEditor.
Switching preserves captured models; the static patch remains available.
Monaco is the default tab and loads from the existing pinned public CDN. HTML remains an accessible fallback. Mobile
and unavailable-CDN views retain the complete static fallback. No backend session
is needed. New release-report rendering uses the same review controls; already
saved reports and their embedded evidence are not rewritten.

Focused verification:

```text
cargo test --offline --locked --lib source_comparison:: -- --test-threads=2
cargo test --offline --locked --lib cli::source::comparison_cli:: -- --test-threads=2
cargo test --offline --locked --lib release_report:: -- --test-threads=2
```

The comparison module is also included in scripts/test-bounded.ps1 exact-once
coverage. For installed Edge browser assertions, provide two prepared fixture
HTML files with different comparison identities, a unique path-only move,
at least three logical files, and a changed Long.java large enough to scroll:

```text
node scripts/test-comparison-browser.mjs D:/reviews/one/index.html D:/reviews/two/index.html
```

The browser runner uses a fresh profile, no screenshots, no package installation,
and saves comparison-browser-results.json. It verifies persistence, identity
isolation, undo, staged filters, path/count accuracy, clipboard-denial fallback
on file URLs, native keyboard/touch controls and actual scroll positions for the
static view and real Monaco when its CDN is available.


## File reference provenance

Whole-line directives declare relevance without producing generated bytes:

```liquid
{% if features.quantity_sensitive_extraction_progress %}
{% issue 610 %}
// the corresponding changed code
{% endif %}
```

`{% pr 563 %}` and `{% discussion 42 %}` use the same implementation. The default
repository is TeamDman/SuperFactoryManager; an explicit scope is supported, for
example `{% discussion "Other/Repository" 42 %}`. These examples demonstrate
syntax; they do not associate those PR/discussion numbers with production files.
Only executed declarations contribute. References deduplicate by kind,
case-insensitive owner/repository, and number. Issue, PR and discussion routes
remain distinct (`issues`, `pull`, `discussions`).

A path-only membership change can attach a `reference_template` beside the exact
metadata variant's `when` condition. This explicit companion must render to zero
bytes, is bounded, root-checked and hash-pinned. It avoids modifying frozen JSON
inputs just to explain a move. Current recipe-namespace variants attach the
verified #527/#621 declarations; this is declared file relevance, not a quotation
naming every recipe or proof that an issue is completely fixed.

Save an explicit comparison input as `input.json`, then query an output path:

```text
sfm-propagate-changes --output-format json source references --input input.json --path src/generated/resources/data/sfm/recipe/disk_reset.json
```

The result includes status, canonical links, typed references, source/output
hashes, declaration source hashes, projection identity and resolved guard
bindings. The same optional evidence travels in collected artifacts, catalog
ownership receipts and comparison.json. Generated Java/JSON bytes stay unchanged.
The HTML explorer presents old/new Related references independently and includes
raw declaration evidence; exported review JSON preserves both sides.

`unassociated` means a render was inspected and executed no reference declarations.
`unavailable` means no recorded provenance exists for that side; `absent` means the
requested path is not in the capture. Arbitrary directory and pinned Git inputs
are not automatically rendered or assigned references, and pre-existing frozen
articles are not silently backfilled. A current projection query describes current
authored inputs, not a historical reconstruction or attestation of a disk file.
No span or commit attribution is performed.

The viewer opens Monaco by default and uses its native diff navigation. The HTML
view preserves readable patches when Monaco is unavailable. Custom numbered
hunk links are not generated. Initial selection reveals the first native diff;
unchanged payloads and path moves still show their complete source.

Certified Java whitespace changes are partitioned into a collapsed group. The
existing manifested-Java classifier compares complete token contents and syntax
structure, preserving literals and comments; unparseable and non-Java inputs
remain substantive. Path moves remain substantive regardless of body whitespace.
Group reactions affect only rows matching applied filters, form one undo action,
and persist/export alongside individual reactions. Raw source and hashes remain.

Article diff records may carry optional hash-bound reference evidence and explicit
source-side paths. A declaration association is accepted only when its output hash
matches that side's displayed payload. Older manifests remain readable with
unavailable provenance. Fresh canonical release comparisons must be distinguished
from retained historical source/class/capture records; current source rendering
alone does not establish what was packaged in an older JAR.
