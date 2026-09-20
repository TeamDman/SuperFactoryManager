# Working-tree release-review capture

Status: EF-11 manifest contract and EF-12 Windows producer/envelope/runtime
verified, including in-game creation, exact-byte comment, portable-copy CLI
coverage query and separate-JVM reopen on 2026-09-06. Full acceptance and
evidence paths are in the Explorer find/review freshness plan.
This is a source adapter for the existing review system, not
a second comment store. Parent contract: [release-review-v1](release-review-v1.md).

## Observable contract

A reviewer chooses a Git before revision, an explicit repository-relative source
scope, and a new review file. The after side is the current files on disk,
including permitted untracked files. The producer captures immutable evidence;
opening, commenting, querying, closing, and reopening use the existing Explorer,
document previews, comments, exact-coverage kernel, and portable review store.
No commit or staging operation is required. No existing review is converted or
retargeted as a side effect.

The UI names a capture `working tree <short hash> · captured <time>`, never HEAD.
The before side names its actual commit. An ambient HEAD annotation is separate
observational evidence with a check time, never a source address. A dirty checkout
does not by itself stale a working-tree capture: compare its scoped content to
the captured manifest. Subsequent source changes do not alter the saved bytes or
transfer old approval. A new capture has its own identity and starts unapproved.

## Compatibility decision

Keep `sfm.release-review/1` canonical serialization and semantic hashes unchanged.
Git/Git creation continues producing v1. Add `sfm.release-review/2` only for the
new source capability; both schemas are read by the updated shared kernel/store.
The v2 envelope has the same fields and same embedded `sfm.review-session/2`.
The only new durable authority is a working-tree source binding. Old readers
reject the new schema, rather than silently interpreting a capture as a commit.
There is no automatic migration, destructive downgrade, or rewrite of v1 files.

Within either runtime, represent the candidate source as an exclusive choice:

- Git: `candidate_commit` and `candidate_tree`, with no working-tree capture.
- Capture (v2 only): `working_tree_capture`, with neither Git candidate field.

The common before fields remain real resolved Git commit/tree IDs. A v2 file may
also contain unchanged Git/Git bindings. Reject half a Git pair, both candidate
kinds, neither kind, capture fields under v1, unknown fields/discriminators, and
a binding whose candidate identity disagrees with its embedded after snapshot.
Do not use empty strings, zero SHAs, or a hash masquerading as a Git commit.

The record/class names may remain V1 internally for source compatibility while
the codec supports the explicitly versioned source extension. Existing Java
constructors can delegate to the Git choice; wire semantics take precedence over
internal naming. Rust optional wire fields must be validated as this exclusive
choice, not accepted independently. New working-tree semantic hashes use a v2
domain prefix; every v1 golden and existing attestation meaning remains unchanged.

## Source binding and immutable identity

`working_tree_capture` has this canonical field order:

```text
WorkingTreeCaptureV1 {
  schema: "sfm.release-review.working-tree-capture/1",
  id: "working-tree:sha256:<64 lowercase hex>",
  observed_head_commit: lowercase Git SHA-1,
  observed_head_tree: lowercase Git SHA-1,
  captured_at_unix_ms: nonnegative integer,
  consistency: "verified_two_pass",
  scope_paths: [relative path prefix; "." means repository root],
  excluded_paths: [relative path prefix],
  include_untracked: Boolean,
  entries: [WorkingTreeEntryV1]
}

WorkingTreeEntryV1 {
  path: canonical repository-relative UTF-8 path,
  kind: regular_file | deleted | symlink | gitlink,
  tracked: Boolean,
  byte_length: nonnegative integer?,
  sha256: lowercase SHA-256?,
  executable: Boolean,
  materialization: utf8 | unchanged | binary | oversized | unavailable,
  document_revision_id: String?,
  diagnostic: String?
}
```

Canonical paths are at most 4,096 UTF-8 bytes. Separators are `/`, with no absolute path, drive, `..`, NUL,
empty segment, `.git` segment or escaping root. Do not NFC/case-fold filesystem
names into different identities. Reject case-colliding aliases on a target that
cannot address them independently. Scope/exclusion prefixes are segment-aware,
sorted, unique, and redundant descendant prefixes are removed. Entries are
sorted by exact UTF-8 byte order and unique (not Java UTF-16 order). Optional
entry fields may be absent/null on input and are omitted canonically when absent.
Exclusions are part of the visible source scope,
not a way to silently report the entire repository as reviewed.

The capture ID hashes a domain-separated canonical projection of observed HEAD
commit/tree, scope/exclusions, include-untracked policy, and entry facts, excluding
capture time, root hint, diagnostics, and derived `document_revision_id` fields.
Use the same projection and field order in Java/Rust. Derived document IDs include
repository/lane identity, capture ID, side, path and byte hash. No local absolute
path or clock is the immutable identity. The embedded after snapshot's ID equals
the capture ID; complete after corpus records use `source_owner` equal to the
capture ID and a `working-tree-capture://<hash>/<path>` source locator.

The hash projection is independent of JSON escaping: begin with the UTF-8 bytes
of `sfm.release-review.working-tree-capture/1` plus LF. Frame each following value
as its decimal UTF-8 byte length, `:`, its UTF-8 bytes, and LF. Frame in order:
observed commit, observed tree, scope count, each scope, exclusion count, each
exclusion, include-untracked Boolean, entry count, then each entry's path, kind,
tracked Boolean, byte length, SHA-256, executable Boolean, materialization.
Integers use unsigned decimal without leading zeroes; Booleans are `true`/`false`;
absent byte length/hash use `-`. Hash with SHA-256 and prepend
`working-tree:sha256:`. Consistency is independently constrained to
`verified_two_pass`; capture time, diagnostics and derived document IDs are not
framed. Timestamp range is 0 through signed 64-bit maximum for Java/Rust parity.
Scope/exclusion membership checks use segment ancestors, not a scan of every
prefix for every entry. The standalone shared fixture lives in
`fixtures/working-tree-capture-v1/capture.json`; its hashes are synthetic source
facts for codec testing, not a claim that its sample documents were captured.

Only changed UTF-8 file bytes are embedded, once, in the existing after snapshot.
Unchanged manifest entries are evidence of scope, not duplicate preview bodies.
Before bytes come from immutable Git objects. Review units reconcile every
changed path/side, including deletion, binary, unsupported, and mode-only changes.
The first working-tree materializer deliberately uses conservative whole-file
units for changed text paths, with that scope disclosed in each unit. Existing
text/structured diff previews still support finer reading and exact comments;
coverage does not claim unchanged portions of that unit were reviewed. This
keeps untracked files fully represented and permits a later versioned finer
unit producer without changing the captured source bytes or comment authority.
Missing/unsupported bytes remain explicit incomplete surface evidence and cannot
receive effective approval. An empty changed domain is explicitly empty, not a
failed enumeration disguised as success.

## Bounded producer and source authority

1. Canonicalize the user-supplied repository root and output parent; require output
   containment and no-clobber creation. Read `.git` only through canonical Git
   commands. Do not write refs, objects, index, or worktree. Use optional-locks-off
   for read-only Git commands. An output may not already exist.
2. Resolve before and observed HEAD/tree. Enumerate the union of before-tree
   paths, index-tracked paths, and (when enabled) nonignored untracked paths under
   the explicit scopes. Ignore Git internals, the exact output/evidence paths,
   and explicit excluded subtrees. Untracked `.env`, `.env.*`, private-key files
   (`*.pem`, `*.key`, `id_rsa`, `id_ed25519`), and credentials files are excluded
   by a disclosed default policy; record exclusions, never read them to infer
   secrets. This is not a claim to detect every secret. Already tracked files
   remain part of the selected source scope unless explicitly excluded.
3. Capture the filesystem state, not the index's staged content. A staged change
   followed by an unstaged edit uses the final disk bytes. Staged-only deletion
   with a remaining disk file uses that file. A file absent on disk is a tombstone.
   Reject unmerged/conflicted index state for the initial producer; do not choose
   an arbitrary conflict stage. Renames may initially be explicit delete/add
   operations; there is no heuristic movement of approval.
4. Never follow symlinks, reparse points, gitlinks, or submodules into another
   source authority. Inspect path components and reject escapes before reading;
   unsupported entries retain visible limitations. Check file type/size/identity
   before and after each bounded read. Non-UTF-8 path names are an explicit
   unsupported-path refusal for this UTF-8 envelope, not lossy renamed content.
5. Perform two independently enumerated/read passes. HEAD, index path/stage
   evidence, entry kinds, and byte hashes must agree; recheck source authority.
   Any detected concurrent change refuses the capture without creating a review.
   Retry only on explicit user request (no infinite background loop). This is
   **verified two-pass observation**, not an OS-transactional snapshot and not a
   guarantee against undetectable ABA edits. Saved evidence is immutable once
   accepted, regardless of later source state.
6. Initial limits: 100,000 enumerated paths; 4 MiB per embedded text document;
   64 MiB per file read/hash; 256 MiB aggregate bytes per pass (including required
   before objects); subprocess/output/deadline limits and cancellation. Exceeding
   a read/enumeration limit refuses the capture and reports the exact limit/path.
   Valid binary or 4–64 MiB text files can be hashed but remain explicit binary/
   oversized review units without invented text. No silent truncation or false
   complete approval. The user can choose a narrower explicit scope.
7. Materialize the ordinary review units/comments/producers from captured bytes,
   validate the complete envelope and reconciliation, write an adjacent staged
   output, then atomically create with no-clobber semantics. Never mutate the
   original review or auto-stage/commit. A cancellation before publication removes
   only its proven temporary file; publication is a single visible completion.

No desktop companion is required to reopen/read/comment on saved capture bytes.
Creating a Git/working-tree review and checking live freshness may require the
companion; unavailable providers say so without invalidating portable evidence.

Initial filesystem producer target: Windows (the active 1.19.2 environment).
Ancestor and leaf handles use open-reparse-point semantics and deny write/delete
sharing during each leaf read to prevent junction swaps; no OS mouse input is
used. Other platforms retain portable read/comment/query support but must refuse
new filesystem captures until an equivalent no-follow adapter is provided.
On Windows, the executable bit follows Git's indexed mode because the filesystem
has no Unix executable bit; indexed *contents* never replace disk contents.
The producer has a 120-second cancellable deadline with bounded Git pipes and
before-object size checks before reading blobs. This is a source-protection
boundary, not a claim that the entire checkout is locked for both passes.

## Live selectors versus pinned comments

The selection adapter retains the source expression and exact byte/hash witness.
A live path or field query may be recorded as origin intent in the existing
proposal provenance (`origin.kind`, repository ID, relative path, scope/query,
and capture ID); it is not a moving approval target. The persisted selection rule
and `source_snapshot_id` refer to the immutable capture actually inspected.

Re-evaluation against a newer capture produces candidates and existing migration
reports, never an automatic retarget. `hi.txt:L25C50-L50C0` beyond the new EOF is
invalid/missing, not clamped. A field selector at another commit/capture can be
exact, relocated, changed, ambiguous, or missing; equal field names alone do not
establish equal reviewed content. Parent RCS-1–RCS-4 owns that evaluator and the
human migration decision. No new approval field or parallel comment authority.

Queries add explicit snapshot-ID atoms, including capture IDs and real commit
IDs, with collision checks against lane/named-query identifiers. Add `candidate`
as the source-kind-neutral candidate-domain spelling. Preserve historical `HEAD`
query semantics as a documented alias for the pinned candidate domain, never
ambient Git; new UI/query examples prefer explicit IDs or `candidate`.
Source atoms select the existing change units in lanes bound to that snapshot;
the query response retains each unit's explicit before/after surface coverage.
They do not pretend a two-sided change unit has become an after-only pixel mask.
Both raw Git IDs and the existing `git:<commit>` snapshot spelling are supported.
Existing v1 named queries/lanes retain precedence over new aliases; v2 refuses
identifiers colliding with its source addresses or the new `candidate` alias.
`effective(#approved) intersect <lane> <capture-id>` and its remaining surface
must be inspectable from the CLI after closing the JVM.

## Public actions and freshness

CLI creation extends the existing command with mutually exclusive source modes:

```text
review session create --file <new review> --branch <lane> --before <git revision>
  --working-tree --scope <relative prefix> [--scope <another prefix>]
  [--exclude <relative prefix>] [--tracked-only] [--repository-root <git root>]
```

Existing `--candidate <revision>` remains unchanged. `--working-tree` requires
explicit scope; default includes allowed nonignored untracked files. `--scope .`
is the explicit entire-repository request, not an implicit broad filesystem read.
Creation output identifies source kind, capture ID, observed HEAD, scopes,
exclusions, changed/represented/unsupported counts and output path.

The in-game action is
`sfm:review/session/create/working_tree <path> <lane> <before> <scope> [repository_root]`.
Use the ordinary parameter solicitation and asynchronous create/open lifecycle.
The review lens menu offers "Create working-tree review from this baseline…";
filesystem context can offer the same action scoped to the clicked source root.
Both open a parameterized command with explicit new output/scope, not a hidden
opaque-ID mutation. Existing open/read-only/writable routes work for either schema.

Freshness probes read/validate only the source bindings and necessary manifest,
not every comment, selector evaluation, or corpus body. They are not review
validation or completion attestations. Check source scope against the captured
manifest; report exact/changed/unknown independently from ambient commit ancestry
and check age. Changes outside an explicit scope do not stale that scoped capture.
Unknown never means current. A supplied binding/file hash prevents stale probe
results from being applied to another opened review. Preserve existing full
validation on review open/write/query/completion and the 30-second UI deadline.
Do not solve the current 63 MiB Git-review freshness timeout by raising deadlines
or claiming current without a check.

## Required proofs before EF-12 completion

- Java/Rust golden roundtrips for unchanged v1 and new v2; reject malformed source
  choices, wrong capture hash, mismatched snapshot/manifest/body, and future fields.
- Disposable repository: baseline commit; tracked modification, staged+unstaged
  content, allowed untracked Java, deletion, unchanged file, binary/mode-only case,
  ignored/secret exclusion, symlink escape, conflict and concurrent-edit refusal.
- Real untracked `ExploreReviewInteractivelyPuppetAction.java` appears in a scoped
  capture without committing. No changes to source files, Git refs/index/objects,
  existing reviews, or human approvals during the test.
- Select exact captured bytes in game; comment in a disposable review; close JVM;
  reopen the same portable file elsewhere; structured CLI query reports identical
  target/text and exact approved/remaining surfaces.
- Change/delete source after capture: original preview/comments remain available;
  freshness changes; new capture cannot inherit effective approval by path/name.
- Beyond-EOF and field-at-two-revisions fixtures prove fail-closed live intent.
  No giant-file virtualization, appdata-only authority, LLM/dependency acquisition,
  real-index mutation, or automatic migration is introduced.
