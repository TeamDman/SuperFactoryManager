# Local release reports

`sfm-propagate-changes source release-report --manifest input.json --asset-root assets --output new-report.html`

The typed input schema is `sfm:release-report-input@1`. It requires `title`,
`generated_utc`, `summary`, `limitations`, `sections`, `cases`, `diffs`, `images`
and `jars`; empty arrays are explicit. Unknown fields fail. Types live in one
file each. Controlled Liquid fragments compose the single HTML output.

Cases require stable `id`, `projection`, `status`, `observed`, `method` and
`source_paths`. Status is `passed`, `failed`, `expected-failure`, `aborted` or
`untested`. Report assembly does not promote evidence to acceptance.

Code diffs carry `id`, `title`, `language`, `before` and `after`; complete static
source is readable without JavaScript. Monaco0.52.2 loads from the approved
jsDelivr CDN only after an explicit viewer click. CDN failure leaves the static
source available. No game or evidence bytes are sent by the generator.

Images require `id`, safe `relative_path`, `sha256:<hex>`, nonempty `caption` and
`alt`. Canonical paths must remain below the explicit asset root; images are
embedded as PNG data URLs. Hash mismatch aborts before output creation.

JAR pairs require `id`, `baseline_path`, `candidate_path` and `provenance`.
The existing JAR comparator is reused with its legacy gradle/rust labels
explicitly mapped to baseline/candidate. Raw SHA256 and ZIP metadata hashes
remain separate from normalized payload differences. Only
Implementation-Timestamp is omitted from manifest comparison; differing
class bytes are not classified as behavioral equivalence. Local absolute input
paths are reduced to filenames in rendered comparison output.

Limits:32MiB JSON,8MiB per PNG,64MiB HTML,128MiB per JAR,20,000 JAR entries,
512MiB expanded JAR payload. Inputs must be quiescent during rendering.
The destination must be fresh; no existing file is overwritten. The command
never compiles, downloads evidence, installs tooling or publishes anything.

Compact static patches reuse the existing bounded gix Histogram producer (3 context lines, 128 KiB patch output, 4 MiB source per side). Complete source remains available in expandable panes. Changed lines are colored and each hunk has a navigation anchor. A CDN failure releases the cached load promise so retry starts a new load.


## Prepared class explorer evidence

The optional `class_evidence_path` and `class_evidence_sha256` on each JAR pair
load `sfm:jar-class-evidence@1` from the report's bounded asset root. Evidence is
bound to both actual JAR SHA256s before rendering. Prepare it offline with
`scripts/prepare_class_evidence.py --manifest INPUT --output ASSET_DIRECTORY
--output-manifest NEW_INPUT --asset-prefix RELATIVE_ASSET_DIRECTORY --javap
ABSOLUTE_PINNED_JAVAP`. Use one javap version for every baseline/candidate and
retain the generated tool/version/hash provenance and raw output cache.

The existing bounded Histogram renderer counts inserted/deleted representation
lines. Constant-pool operand slot numbers are normalized; symbolic comments,
string constants, bootstrap indices and offsets remain. Printed debug tables
are separated. Equal instruction/signature views are not executable or semantic
equivalence; debug-view differences do not prove every raw class difference is
debug-only. ZIP metadata-only means class payload bytes are identical.

Native details/links give a static package/class tree and unified diff without
JavaScript. JavaScript adds projection switching, search and changed-only
filtering. One optional Monaco editor is reused with old models disposed;
mobile view deliberately uses static diffs. Identical views are pooled across
classes/versions to avoid repeated editor instances and repeated diff content.

## Reviewed editorial profile

The same command can generate the reviewed 4.35 energy candidate article.
Add an optional `article` object to the existing typed input; omitting it
preserves the ordinary technical report. No CLI arguments change.

`article.profile` is `sfm-4.35-energy-candidate@1`; this is a controlled,
release-specific narrative, not a general inference of release acceptance.
`program_diff_id` and `branch_diff_id` bind the actual SFML and source branch.
`primary_images` contains the reviewed red and green later-consumer figures,
each with `image_id`, `category`, `version`, `value`, `description`, and `tooltip`.
`supporting_images`, `historical_images`, and `linux_images` hold IDs of the six
other final captures, eight historical GUI captures, and eight prior Linux
captures respectively. Every input image must be assigned exactly once.
Unknown profile, changed source branch/program, inconsistent reviewed case
statuses, missing targets, altered primary labels, and missing/duplicate or
misclassified image references fail before output creation.

Controlled Liquid fragments provide the shell, narrative, primary figures,
and collapsed evidence panels. They consume existing validated evidence
fragments directly: no completed HTML document is parsed or rewritten.
The ordinary evidence stylesheet is scoped to the appendix; the class explorer
decompresses only when its panel opens. No-JavaScript evidence and optional
Monaco model reuse remain available. Article output has a 20,000,000-byte cap
in addition to existing asset/source/JAR budgets and fresh-output protection.

The profile describes the reviewed pinned Minecraft 1.21.1 reproduction,
ten-target packaging, explicit rounding/control limitations and unreleased
status. It does not claim Productive Bees reproduction or all-version runtime
acceptance. New release narratives require a reviewed profile/template rather
than arbitrary input HTML. The test input is synthetic and acquires no assets.

SFML examples now use a report-local presentation lexer whose case-insensitive
keywords are grounded in SFML.g4. Leading indentation is normalized only outside
string literals. Original source is pooled in inert templates; interactive
Monaco models use those captured originals, with a registered SFML tokenizer.
No captured program, JSON input, game source or JAR is rewritten by presentation.
Capture captions are human-first; original labels and image SHA256 live inside
collapsed provenance details. Main article corrections distinguish the initial
25-FE gap, first requested25/accepted24 transfer and reset second-pass1FE gap.

The editorial `sfm-4.35-energy-candidate@2` profile shows only the eight final
release GUI captures. Its prominent projection text explorer groups changed
source/resource snapshots by Minecraft version; fixture and class inspections
are separate verification views. Exact input and paired class evidence are
available as a bounded offline ZIP download instead of a large visible JSON dump.
Optional `correlations` currently supports the reviewed Minecraft 1.21.1
ResourceQuantity case: source-line LCS alignment is checked at matched method/PC
coordinates, with every mapping and the raw printed diff retained. It never
applies a global line shift or claims exhaustive class-attribute equivalence.
