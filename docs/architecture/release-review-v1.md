# Release review document v1

Status: frozen for `sfm.release-review/1`.

This contract turns the existing `sfm.review-session/2` comment model into a
portable, resumable release-review artifact. It is an orchestration envelope,
not another comment system. The embedded `review_session` is the only source of
comment text, hashtags, human decisions, and approval evidence.

## Authority and portability

- The authoritative artifact is one user-selected `.sfm-review.json` file.
- The SFM convention is
  `docs/reviews/<before>-to-<after>.sfm-review.json`.
- AppData may retain recent paths, indexes, and hash-addressed recovery bytes.
  Deleting all AppData must not change comments, queries, progress, status, or
  the next resumable work item.
- Every semantic mutation is written atomically. A writer compares the hash it
  opened with the current on-disk hash before replacing the file. A mismatch is
  an external-edit conflict and leaves the in-memory document dirty.
- Exactly one writable owner is allowed for a normalized absolute path in one
  JVM and across cooperating processes. A second writable open is refused;
  read-only opens remain possible, and closing the writable owner releases its
  in-process registration and operating-system file lock.

### Machine-local state is derivable, not authoritative

For an authoritative path `P`, the Java store normalizes `P` to an absolute
path, hashes its UTF-8 string form with SHA-256, and derives both machine-local
paths beneath:

```text
%LOCALAPPDATA%/teamdman/SFM/release-review-recovery/
  <sha256(P)>.json
  <sha256(P)>.writer.lock
```

When `LOCALAPPDATA` is absent, the same suffix is rooted at
`<user.home>/.local/share`. `SFMReleaseReviewStore.machineLocalState(P)` exposes
these exact recovery and writer-lease paths; callers do not need to discover
them by directory scanning.

The repository file is always read first. A valid repository file completely
determines comments, selector bindings, named queries, resume state, query
results, and completion status. A writable save validates and canonically
reparses the document, compares the current repository-file hash with the hash
seen by that writer, then atomically replaces both the repository file and its
recovery copy. A read-only open creates neither recovery nor lease state.

Recovery is a fail-closed fallback, not an authority switch. If the repository
file is missing or invalid but a valid recovery copy exists, the runtime loads
the recovery bytes as dirty state and requires an explicit save or save-as; it
does not silently repair or overwrite the repository file. Deleting recovery
and lease files leaves a valid tracked review fully reopenable. Copying the
same canonical bytes to another checkout produces different machine-local
keys, yet reopens with equivalent queries, resume state, and completion
evidence. Therefore the tracked `.sfm-review.json` file alone is the portable
review authority.

## Implemented creation and refresh boundaries

### CLI create

The canonical producer entry point is:

```pwsh
sfm-propagate-changes.exe review session create `
  --file <repository-relative-or-absolute-output> `
  --branch <lane-id> `
  --before <prior-release-tag-or-commit-ish> `
  --candidate <candidate-commit-ish> `
  [--repository-root <git-root>]
```

`--branch` must be nonblank. `--repository-root` defaults to the invocation
directory and is canonicalized. The output parent is created and canonicalized,
but the output itself must remain inside that repository root. The producer
resolves and pins both Git endpoints and trees, materializes the complete raw
change domain, requires reconciliation to be complete, canonicalizes the v1
document, and creates it atomically with no-clobber semantics. An existing
output file is never replaced. Successful structured output has schema
`sfm.release-review-create/1` and reports the pinned identities, raw-domain and
represented/fallback counts, reconciliation result, and initial completion
status.

### CLI refresh

The preservation-capable refresh entry point is:

```pwsh
sfm-propagate-changes.exe review session refresh --file <existing-review>
```

Refresh requires one valid document with exactly one repository binding. It
derives the repository from the review-evidence path suffix when possible,
otherwise from the binding's `root_hint`. It proceeds only when ambient Git is
the exact pinned candidate or a descendant whose intervening changes affect
only declared `review_evidence_paths`. Missing/unrelated history and any
source-affecting divergence are refused. The review file itself must
canonicalize inside the bound repository and be one of the binding's declared
review-evidence paths.

The command reproduces the original pinned before/candidate commits rather
than refreshing against moving ambient `HEAD`. The fresh producer must exactly
reproduce repository bindings, review-session identity, coordinate system,
revision lanes, and byte-pinned corpus documents. It then replaces only this
materializer's generated active units/comments and producer generations while
preserving human comments, selector bindings, migration reports, named
queries, resume/deferred state, style rules, completion policy, and completion
attestations. A previously generated unit that disappears is retained as an
explicit `unsupported` retirement witness with a generated `#needs-change`
comment, so refresh cannot silently erase already reviewed surface area.

Refresh refuses ownership inconsistencies, generated/human ID collisions,
invalid merged output, unauthorized paths, source-snapshot divergence, and
producer failures. After staging and flushing canonical output beside the
review file, it re-reads the original path and byte-compares it with the bytes
opened at the start. Concurrent modification yields
`refresh.concurrent-modification`; replacement failures yield
`refresh.atomic-replace-failed`. Every refusal returns typed
`sfm.release-review-refresh/1` output with outcome `refused`, exit code 5, and
leaves the existing review bytes unchanged. Success reports outcome
`refreshed`, generation/reconciliation counts, ambient relationship, and the
new completion status.

### Ordinary in-game asynchronous create action

The ordinary command-palette action is:

```text
sfm action invoke sfm:review/session/create <path> <lane> <before> <candidate> [repository_root]
```

All five values use Brigadier string arguments. A path or repository root that
contains `/`, `\`, whitespace, or other non-word characters must be quoted,
for example:

```text
sfm action invoke sfm:review/session/create "docs/reviews/4.34.0-to-head.sfm-review.json" 1.19.2 4.34.0-1.19.2 HEAD "D:/Repos/Minecraft/SFM/repos2/1.19.2"
```

The repository-root argument is optional. When absent, the client walks upward
from the Minecraft game directory until it finds `.git`; failure asks the user
to provide `repository_root`. Invocation immediately reports that creation was
queued and submits one task to a daemon single-thread worker. That worker runs
the installed `sfm-propagate-changes.exe` (or the test/property override) with
global `--output-format json` and the exact CLI create arguments, merges stderr
into stdout, and retains at most 64 KiB for bounded diagnostics. It does not
wait on the Minecraft render thread. On exit zero it verifies that the expected
file exists, then returns to the Minecraft executor to open that file writable.
Nonzero exit, missing output, interruption, launch failure, and open failure
produce user-facing feedback without claiming creation succeeded.

## Canonical envelope

The serialized field order is the order shown here. Lists retain wire order
unless their field contract below explicitly requires canonical sorting.

```text
ReleaseReviewDocumentV1 {
  schema: "sfm.release-review/1",
  review_session: ReviewSessionV2,
  repository_bindings: [RepositoryBindingV1],
  corpus_documents: [CorpusDocumentV1],
  review_units: [ReviewUnitV1],
  selector_bindings: [CommentSelectorBindingV1],
  migration_reports: [MigrationReportV1],
  named_queries: [NamedQueryV1],
  resume_state: ResumeStateV1,
  producer_generations: [ProducerGenerationV1],
  completion_attestations: [CompletionAttestationV1]
}
```

Unknown fields, duplicate IDs, duplicate canonical corpus addresses, dangling
document IDs, dangling resume IDs, and mismatched lane/snapshot bindings are
errors. Readers do not silently discard future fields.

### Repository bindings

```text
RepositoryBindingV1 {
  lane_id: String,
  repository_id: String,
  root_hint: String,
  before_label: String,
  before_commit: lowercase Git SHA-1,
  before_tree: lowercase Git SHA-1,
  after_label: String,
  candidate_commit: lowercase Git SHA-1,
  candidate_tree: lowercase Git SHA-1,
  review_evidence_paths: [repository-relative UTF-8 path]
}
```

`after_label == "HEAD"` is a human-facing alias for `candidate_commit`; it is
never resolved against moving ambient HEAD while evaluating a query. Evidence
paths are sorted and unique. An ambient commit descendant that changes only
those paths is `review_evidence_only`; any other candidate-tree difference is
`source_affecting_divergence`. The implemented relationship report uses that
same fail-closed divergence classification for a missing repository/commit,
Git inspection failure, or ambient commit that does not descend from the
candidate; CLI status then projects that relationship to effective review
status `stale`.

### Corpus documents

```text
CorpusDocumentV1 {
  id: String,
  lane_id: String,
  snapshot_side: before | after,
  path: repository-relative UTF-8 path,
  document_revision_id: String,
  sha256: lowercase SHA-256,
  source_owner: String,
  source_locator: String,
  materialization: complete | partial | missing
}
```

The tuple `(lane_id, snapshot_side, path)` is unique and the list is sorted by
that tuple. `document_revision_id` and `sha256` must name the same immutable
document carried by the embedded session snapshot. The adapter may hold a
borrowed view of those bytes; it must not serialize an independent mutable copy
or infer approval. `source_owner` and `source_locator` identify the exact
snapshot/frame/Git owner from which the bytes came.

### Durable review identity and authenticated analysis identity

Opening a corpus leaf gives the editor and every durable selector/comment a
contributed address of the form
`review://document/<document-revision-id>/<repository-path>`, rooted at
`review://document/<document-revision-id>/`. That identity continues to name
the immutable pinned review snapshot even when the same bytes also exist in a
candidate checkout. It is never rewritten to a checkout path in the review
document.

Language workers currently require a file-backed path and authorized source
root. For a `complete` **after-side** corpus document only, the client may
attach a worker-only `AnalysisIdentity`. Resolution finds the repository
binding for the corpus lane, interprets its `root_hint` (or searches ancestors
of the tracked review file for the relative hint), requires a `.git` entry at
the candidate root, normalizes the corpus path beneath that root, requires a
regular file that cannot escape the root, and hashes its bytes. The identity is
granted only when that SHA-256 exactly equals the pinned corpus SHA-256.
Before-side/historical, partial, missing, unreadable, absent, escaping, and
byte-divergent documents receive no borrowed identity.

The editor retains the `review://` baseline for display, navigation, capture,
and comment persistence. Immediately before publishing semantic work, it may
project the same text, hash, target range, and mutability through the
authenticated `file://` path/root. That projection is sent only to the language
worker and drops the alternate identity so it cannot recurse. A missing or
failed authentication therefore removes structural worker assistance rather
than weakening the durable review address or pretending historical bytes are
the live checkout.

### Review units

```text
ReviewUnitV1 {
  id: String,
  lane_id: String,
  operation: added | deleted | modified | renamed | copied | type_changed,
  path_before: String?,
  path_after: String?,
  before_document_revision_id: String?,
  after_document_revision_id: String?,
  before_ranges: [Utf8RangeV1],
  after_ranges: [Utf8RangeV1],
  language: String,
  surface_kind: declaration | signature | body | field | import |
                diff_hunk | file | binary | unsupported,
  semantic_key: String?,
  limitation: String?,
  producer_id: String,
  producer_generation: String
}

Utf8RangeV1 { start_byte: non-negative integer, end_byte: integer >= start }
```

Review units define the completion domain, never approval. They are sorted by
lane, canonical after-or-before path, side, range, surface kind, and ID. Added
and deleted files retain an explicit absent/tombstone side through the optional
document ID. Every raw Git change must map to at least one structural unit or
an explicit file/diff-hunk/binary/unsupported fallback. A producer rerun
reconciles by stable ID and cannot duplicate or silently erase units.

### Selector proposals and evaluation

The adapter/evaluator boundary uses these versioned values in Java, Rust, test
artifacts, and the in-game constrained palette:

```text
PinnedSelectionV1 {
  selection_revision: String,
  source_expression: String,
  primary_range_index: integer,
  ranges: [PinnedSelectionRangeV1]
}

PinnedSelectionRangeV1 {
  direction: forward | backward,
  document_revision_id: String,
  document_sha256: lowercase SHA-256,
  start_byte: integer,
  end_byte: integer
}

SelectorProposalV1 {
  id: String,
  kind: literal | declaration | signature | body | return_type | symbol |
        bounded_multi_region,
  selection_rule: ReviewSessionV2.SelectionRule,
  literal_witness: PinnedSelectionV1,
  semantic_provider: String?,
  semantic_key: String?,
  semantic_provenance: [key/value],
  confidence: exact | conservative | unavailable,
  projection_fingerprint: lowercase SHA-256,
  source_snapshot_id: String,
  diagnostics: [String]
}

EvaluationResultV1 {
  selector_id: String,
  status: exact | relocated | content_changed | ambiguous | missing |
          invalid | scope_missing,
  ranges: [document revision plus UTF-8 range],
  candidates: [document revision plus UTF-8 range],
  invalidation_keys: [InvalidationKeyV1],
  diagnostics: [String]
}

MigrationReportV1 {
  id: String,
  source_selector_id: String,
  source_evaluation: EvaluationResultV1,
  candidate_evaluation: EvaluationResultV1,
  old_witnesses: [PinnedSelectionRangeV1],
  new_candidates: [document revision plus UTF-8 range],
  decision: unresolved | retargeted | relocation_confirmed | selector_edited |
            archived | discarded | deferred,
  decision_comment_id: String?
}

CommentSelectorBindingV1 {
  comment_id: String,
  captured_selection: PinnedSelectionV1,
  selected_proposal: SelectorProposalV1
}
```

Ranges are ordered, UTF-8 half-open, Unicode-boundary checked, and may span
documents. Reverse projection is a view; advancing a live selection does not
move a pinned comment. Structural evidence must name its provider and exact
literal witness. A nearest-token fallback cannot claim structural confidence.
Automated proposals, evaluations, and migrations cannot create or transfer a
human `#approved` decision.

`selector_bindings` is the durable home for selection direction, selection
revision, source expression, literal witness, and the chosen structural key.
It is keyed to an ordinary embedded-v2 comment and cannot exist without that
comment. `migration_reports` likewise retain evidence and point to an ordinary
decision comment for every resolved human choice. Neither list contains tags or
approval state, so neither becomes a second decision authority.

### Queries and resume state

```text
NamedQueryV1 { id: String, expression: String }

ResumeStateV1 {
  active_query_id: String?,
  active_query_expression: String?,
  current_unit_id: String?,
  deferred_unit_ids: [String],
  generation: non-negative integer
}
```

Named query IDs are unique and sorted. Deferred IDs are unique and sorted.
The cursor is navigation state, not reviewed truth: deleting or changing it
does not alter completion. Reviewed/unreviewed sets are derived from current
units plus the embedded comments and evaluations.

Activating a named query persists both its ID and the expression that defined
that work queue at activation time. When both
`active_query_id` and `active_query_expression` are present, completion parses
and normalizes the current named-query definition and the captured expression.
Different normalized expressions make the review `stale` immediately—even
without an attestation—and add the exact diagnostic:

```text
Active named-query revision differs from its persisted work-queue expression
```

Equivalent spelling, such as explicit versus implicit intersection, remains
fresh after normalization. An active ID without a valid named-query target is
invalid document state. Operations that evaluate the active expression to
navigate its work queue (`select`, next, previous, and defer) refuse a stale
named-query revision and require the user to reactivate the query, thereby
capturing its new expression. Merely evaluating the named query remains useful
for inspecting what changed; it does not silently rewrite the persisted work
queue.

The grammar is case-insensitive for keywords and supports:

```text
expression   := union
union        := difference ("union" difference)*
difference   := intersection ("difference" intersection)*
intersection := unary (("intersect" | implicit-adjacency) unary)*
unary        := "effective" "(" expression ")" | primary
primary      := atom | "(" expression ")"
atom         := hashtag | lane-id | "HEAD" | named-query-id
```

The exact expression `#approved intersect 1.19.2 HEAD` is valid and normalizes
to `(#approved intersect 1.19.2) intersect HEAD`. `HEAD` means the pinned
candidate domain, including deleted-side tombstones. A hashtag maps comments
to every review unit whose before/after surface intersects the evaluated
comment selection. `effective(#approved)` excludes candidate-only, blocked,
ambiguous, missing, content-changed, scope-missing, and unconfirmed relocation
evidence. Raw `#approved` remains queryable so suspension is visible.

Required named concepts are `changed-domain`, `approved-raw`,
`approved-effective`, `remaining`, `blocking`, `suspended`, `missing`,
`deferred`, `unsupported`, and `stale-producer`.

Every `sfm.release-review-status/1` report carries both redundant counts and
the canonically ordered review-unit IDs witnessing each of those concepts.
Counts are never accepted as opaque evidence: an in-game explorer, CLI caller,
or saved structured artifact can drill from every count to the exact units.
These reports are derived from the portable document and may be persisted as
evidence artifacts; they do not become a second approval authority inside the
document.

### Producer generations and completion

```text
ProducerGenerationV1 {
  producer_id: String,
  generation: String,
  input_fingerprint: lowercase SHA-256,
  output_fingerprint: lowercase SHA-256
}

CompletionAttestationV1 {
  id: String,
  review_semantic_state_hash: lowercase SHA-256,
  maintainer: String,
  attested_at: RFC-3339 timestamp,
  statement: String
}
```

Producer IDs are unique and sorted. Completion is one of:

- `in_progress`: valid and current, but work remains;
- `ready_for_maintainer_attestation`: all fail-closed checks pass and no
  matching attestation exists;
- `complete`: a maintainer attestation exactly matches the current semantic
  state hash;
- `stale`: a binding, producer, policy, query, comment evaluation, or corpus
  witness is stale/invalid.

The semantic state hash is SHA-256 over a domain-separated canonical projection
containing the embedded review session, bindings, corpus, units, named queries,
producer generations, and explicit review decisions. Before canonical
serialization, Java and Rust replace resume state with its empty value and
clear both completion attestations and `review_session.style_rules`.
Consequently adding an attestation, moving the cursor, or changing presentation
styles is non-circular and hash-preserving. Style rules remain validated and
persisted in the portable file, but their foreground/background, underline,
gutter marker, priority, and enabled state cannot stale an otherwise matching
attestation. Changing a comment, selector target, completion policy, named
query, producer generation, repository/corpus binding, or other semantic input
does change the hash and invalidates prior attestations.

Tests/build cleanliness never imply approval. CLI status exits non-success for
`in_progress` or `stale`. The goal that introduced this contract deliberately
creates no maintainer attestation.

## Fixture obligations

The canonical fixture set covers:

- a three-snapshot Java declaration that is exact, uniquely relocated, then
  content-changed/ambiguous/missing across cases;
- a multi-document Unicode/disjoint selection with forward/backward ranges;
- stale selection heads, hash mismatch, partial/missing materialization,
  deterministic ordering, unknown-field rejection, and exact source-owner
  provenance;
- query precedence, parentheses, implicit intersection, raw/effective approval,
  and the exact `#approved intersect 1.19.2 HEAD` spelling;
- stale active named-query revisions versus normalized-equivalent spellings;
- semantic-hash exclusion of resume, attestation, and presentation-only style
  rules, plus inclusion of every approval, policy, query, producer, and corpus
  input;
- deletion of derived recovery/lease files and equivalent reopening of
  canonical bytes from a different checkout path; and
- exact candidate-byte authentication for worker-only `file://` analysis while
  durable editor/comment identity remains `review://`.

Java and Rust must parse and canonically re-emit the same fixture bytes.
