# Release review v3: single-file durable ledger and resolved observation

Status: implementation contract, 2026-09-08; not a claim of implemented support.
Owner: [overnight plan](../tasks/single-file%20lazy%20review%20overnight%20plan.md).

## Two representations, one authoritative file

The current release-review v1/v2 model combines serialized source snapshots,
derived review units and user decisions. Keep that resolved evaluation model
available during transition, but do not use its ordinary full serializer as the
durable representation for the new format.

The v3 ledger has a stable review ID, title, target lanes/policy, user comments,
selector provenance, styles, named queries, resume state and evidence table.
Derived current changes, generated change markers, syntax, diff layouts and
indexes are observations, not authoritative ledger contents. No required sidecar.

Opening has two phases: parse/validate the bounded durable ledger; resolve its
targets off-thread into an immutable observation suitable for the existing
kernel/Explorer. Publish an observation generation and source identities. A
refresh creates a new observation, not an implicit rewrite of old comments.
Saving projects the user-owned state and its evidence closure back into v3;
it must never serialize the entire resolved observation as a convenience.

## Target kinds

- `git`: repository binding plus full commit identity. Resolve paths to exact
  blobs. Store no source text by default. If objects are absent, report missing
  evidence; never substitute HEAD, another repository, or the working tree.
- `working_tree`: repository binding, normalized scope/exclusion policy and
  include-untracked policy. This target is live. An observation captures a
  consistent bounded result or reports incomplete/racing reads. The UI explicitly
  distinguishes live target policy from an immutable observation/captured comment.
- Legacy captured targets remain frozen. Conversion may compact redundant data
  but cannot discard unreviewed legacy bytes and claim the same historical review.
  A small new live review from the same baseline is a separate explicit operation.

Persist repository identity/root hints, not ambient HEAD aliases. Resolve paths
through the existing authorized-root policy; no arbitrary path traversal.
Exclude the authoritative review path from its live domain, including renamed
save-as destinations. Preserve additional explicit exclusions.

## Evidence closure on comment save

Capture preparation must carry exact document revision, whole-source hash, bytes,
selection ranges and selected-slice hashes from the displayed observation. Treat
the preparation as immutable. A later disk read cannot replace it. If the source
changes while the user is composing, keep the comment on the displayed snapshot
and expose the newer observation separately; reject stale review leases instead
of applying the comment to a different review.

The ledger stores source metadata per captured document identity and a deduplicated
content table keyed by SHA-256. Equal bytes across paths/sides can share content;
document identity still includes path, repository and source revision. Preserve
UTF-8, CRLF and trailing newlines exactly. Initially retain whole documents rather
than inventing fragment assembly. Unavailable/binary surfaces cannot masquerade
as valid text evidence.

Persist a non-Git document only when a user comment requires it. The evidence
closure includes its literal witness and any other documents needed by a
multi-document selector. Do not decide capture necessity solely by a comment's
display hashtag or a spoofable label; use the explicit mutation/evidence path.
Generated release-change markers and derived match results do not pin all source.
Semantic selectors retain a pinned literal witness and provenance. Arbitrary
unbounded selectors must be bounded/resolved before durable capture, or fail
explicitly rather than silently capture an entire repository.

Commit comment, selector binding and new evidence in one atomic file replacement
under the existing writer lease/optimistic content hash. Validate a round trip
before replacement. Save failure leaves the prior authority untouched and retains
the draft with its evidence. Machine-local recovery remains optional recovery,
not a required component of the committed review.

## Coverage and historical context

The observation generation identifies the source state for current coverage.
An old approval cannot cover changed bytes solely because a path or byte range
still exists. Historical comments and their captured documents remain queryable
even if the current target deletes the file. Never drop an unmatched comment from
the durable save merely because it has no current review-unit match.

Initially, exact immutable identity/hash matches may contribute to current
approval. Relocation is a separate explicit operation with provenance; ambiguity
is unresolved. This conservative rule may request more review after unrelated
edits, but it must not overstate approval. The later migration stretch can refine
that ergonomics without weakening evidence.

Saved resume state uses stable semantic IDs and a known observation identity.
If the live domain changes, show that fact and resolve or reset the cursor
explicitly; do not treat an ordinal from yesterday as today's same work item.
CLI and Java queries must share these semantics, including missing evidence and
incomplete-domain results. Opening must not require successful resolution of all
historical Git objects just to read an embedded human comment.

## Integration seams found in source

- Java `SFMReleaseReviewStore.load/save`: strict old codec, validation, atomic
  replacement, lease/conflict and recovery handling. Add format-aware durable
  encoding here, not a UI-only shrink step after saving.
- Java `SFMReleaseReviewRuntime.openAsync/mutateAsync/createCommentAsync`: worker
  execution and captured lease/generation; retain evidence through this boundary.
- Java `SFMReleaseReviewV1Codec`: preserve strict v1/v2 compatibility, do not teach
  the old parser to silently ignore new fields. Introduce explicit dispatch.
- Rust `review_cli::read_review_document_at`: route new format through target
  resolution with review-path context; keep pure legacy parsing available.
- Rust creation/materializers currently construct full revision lanes. Split
  creation of the durable target ledger from materialization of an observation.
- Existing document previews use immutable review revision addressing. Publish
  resolved live observations as immutable revision snapshots to those previews;
  refresh of the live target must not mutate a document already being selected.

### Historical resolution constraint found during C2

The existing resolved kernel requires every corpus row to have a repository
binding, and working-tree bindings validate the captured lane against their
capture manifest. A historical embedded document cannot simply be appended to
the newest captured lane: it would violate the capture witness, may collide on
the same side/path, and could misstate current coverage. Do not manufacture a
Git binding for embedded disk bytes to get past this validation.

Resolution needs an explicit historical-evidence representation, distinct from
the current completion corpus. If the transitional resolved envelope requires a
new observation discriminator to support unbound historical evidence lanes, make
that extension explicit in both kernels; keep legacy validation unchanged.
Historical ranges must still validate exact hashes, UTF-8 boundaries and selector
witnesses, remain visible/queryable, and have no current completion units unless
the actual current source identity matches. The durable v3 ledger is not itself
the fully resolved observation wire format. Prove historical deletion/reopen and
current-approval exclusion before exposing the new creation action.

## Required proofs

1. Initial file contains targets/policy but no source body or generated comments.
2. Browse/find/open diff does not grow or modify the file.
3. Comment saves exact displayed evidence; repeated content is deduplicated.
4. External edit between display and save cannot change the comment's target.
5. Restart retains comment/value/selector/query results without hidden state.
6. Missing/renamed/deleted current files do not erase historical evidence.
7. Failed/conflicting save leaves previous valid authority and recoverable draft.
8. Current coverage does not carry approval across changed source identities.
9. Legacy input remains readable and conversion never overwrites the original.
10. No sidecar, new dependency, lockfile mutation or OS pointer injection.

Portable Git evidence export, compaction and recognized Git-blob reuse are later
explicit operations. They are not prerequisites for making newly live reviews
small, and must never silently turn repository-dependent evidence into a claim
of offline portability.

## Explicit live-history migration (stretch3 implementation direction)

The existing legacy migration pipeline compares BEFORE to AFTER and its decision
method retargets the original comment. It is not the implementation for this
ledger's historical AFTER to current AFTER transition. Preserve that legacy
compatibility path; do not reinterpret its decisions as ledger migrations.

For v3, preview against an explicitly selected current destination lane/domain.
Do not infer repository lineage from equal paths or synthetic evidence lane names.
Evaluate every captured range, keep all plausible literal matches, and distinguish
exact coordinates, relocation, ambiguity, missing evidence, and incomplete search.
An incomplete destination or exhausted work/candidate bound cannot prove uniqueness.
Empty witnesses need an explicit target. Syntax matching can refine this later,
but the initial literal preview must not claim semantic equivalence.

Preview is transient and read-only. Bind it to review path/open epoch/generation,
original comment/binding, exact source and destination revisions/hashes, destination
scope and matching policy. Acceptance must reject stale previews before persistence.
An explicit acceptance adds a successor comment and selector binding with parent
comment provenance and the accepted preview evidence; the original comment and
approval remain untouched. Non-Git destination bytes are retained by the existing
atomic comment-save closure. Merely previewing candidates must not grow the ledger.
Approval text can be applied to a unique witnessed destination only after explicit
human acceptance; ambiguous/missing/incomplete previews offer no approval transfer.
Reopen must expose the successor/original relationship from the single review file.

Migration is implemented and verified: pure bounded matching, scoped multi-range
plans, runtime leases, linked successors, context/palette actions and durable
roundtrip. See the overnight plan's stretch3 checkpoint and the live review guide
for current-source tests and GUI2/4 evidence. Exact matching is not a claim of
semantic equivalence; inserted bytes outside the successor remain unreviewed.

## Portable comment evidence and explicit compaction (stretch4)

Export must not silently freeze every browsed non-Git document. Its portable
payload is the already retained comment evidence closure plus target definitions,
comments, selectors, provenance, queries and resume state. Current source browsing
and current-domain completion still require the target sources. Missing Git objects
must not erase portable comment evidence or make an unavailable current domain
look like a completed review.

Two explicit preparation policies:

- Embed comment evidence: retain existing embedded bytes, resolve missing Git
  bodies using exact repository/commit/path/blob identity, verify UTF-8/SHA256 and
  Git blob SHA1, and deduplicate by content hash. Already embedded evidence does
  not require a Git read. This does not persist additional browsing content.
- Verified Git references: repository-dependent compaction, explicitly not an
  offline-portability promise. Remove a body only after checking every Git reference
  that relies on it; retain it when a non-Git document shares the hash without
  separately verified storage provenance (described below). A missing
  object is a refusal, never permission to drop data.

Preparation is pure and reports input/output bytes, embedded UTF-8 bytes, body
count, dependency count and exact input hash. Publication must recheck input
authority and publish a new destination without overwrite. A dry run writes nothing.
Recognizing that captured bytes now exist in Git must not change their target
revision or approval eligibility; do not mutate a formerly non-Git Document's
Git field casually, because current resolution treats it as identity metadata.

Current resolver limitation: it resolves current target domains before historical
evidence, so missing baseline objects can prevent even embedded history from being
opened. Offline integration must address this explicitly with a truthful unavailable
current-domain state or separate evidence-only view; an empty successful domain is
not an acceptable fallback. Query-equivalence proofs must distinguish unchanged
comment evidence from unavailable current-domain completion. This integration,
recognized-blob reporting and final GUI acceptance were tracked separately. The separate
review-evidence Explorer now reads retained comments and bodies without resolving
current targets; it explicitly labels current source coverage unavailable. Pure,
file-publication and acquisition tests preserve comment evaluation on reopen.

Export actions use `sfm:review/evidence/export/preview <review_file> <new_file>
<portable|git-references>`, followed by report or explicit confirm of a transient
preview ID. Both paths are quoted Brigadier strings. Source identity is bound in
the context choice, so an old menu cannot silently export another active review.
One preview is retained, all I/O is on a worker, and changed authority or an existing
destination fails closed. The original review remains active after publication.
Cross-directory copies rebase repository hints to their original absolute location;
the preview discloses this rather than accidentally interpreting relative hints
against another repository. Offline evidence portability is not live source
portability. Export/report/confirm/discard and offline reopening were verified at
GUI scales 2 and 4; see the goal plan's 20260908 galleries. Report context actions
expire after confirmation/discard and do not replace ordinary selection behavior.

Later-committed byte recognition now has a bounded local verification primitive:
explicit full commit and repository-relative storage path, exact content SHA256,
commit/path-to-blob equality and independently verified Git blob identity. Its
result preserves the original Observed document, including its non-Git identity,
and carries storage provenance separately. Moving aliases and unsafe paths are
rejected. A renamed storage path is permitted only when the bytes match exactly.
The optional `evidence.git_storage` array now records one location per content
SHA256 (`sha256`, repository-relative `path`, `repository_id`, full `commit`, full
`blob`). It is independent of `evidence.documents[*].git`. Java/Rust parsers reject
duplicate/unreferenced storage hashes, moving aliases and unsafe storage paths;
old files omit this optional array. Embedded bodies take precedence when reading.
Absent bodies may resolve through the pinned storage reference, preserving the
original document ID/path/hash/Git identity in all resulting comment evidence.

Explicit Git-dependent export probes the current HEAD once per unambiguous
repository and attempts exact same-path recognition for embedded non-Git content.
Unmatched bytes stay embedded. Existing storage references are reverified; missing
objects or mismatches cannot authorize new publication. Portable export hydrates
missing bodies from pinned references and never reads working bytes instead.
Acquisition is bounded to sixty seconds, 4096 attempts, and 64 MiB. This is not a
repository-wide rename search. Recognition/codec/export integration passed the
installed companion and full suite (2017 Java tests including the real companion
round trip, 711 Rust unit tests plus integration suites), and virtual GUI2/GUI4
walkthroughs. GUI4 gallery sfm-title_screen-20260908-084930-558 includes both
historical source bodies reopened from a portable copy. Targets, full comment
state and document identities remain equal across the original and both exports.
See the overnight plan for exact process, hash and size evidence.
