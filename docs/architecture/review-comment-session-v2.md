# Review comment session interchange v2

## Authority and purpose

This is the canonical persisted contract for review comments that can target
either committed source selections or immutable candidate-trajectory
coordinates. The Java authority is
[`SFMReviewSessionV2`](../../platform/minecraft/src/main/java/ca/teamdman/sfm/client/review/session/SFMReviewSessionV2.java),
with deterministic encoding and v1 migration in
[`SFMReviewSessionV2Codec`](../../platform/minecraft/src/main/java/ca/teamdman/sfm/client/review/session/SFMReviewSessionV2Codec.java)
and evaluation, approval, and exact-promotion rules in
[`SFMReviewSessionV2Kernel`](../../platform/minecraft/src/main/java/ca/teamdman/sfm/client/review/session/SFMReviewSessionV2Kernel.java).

The canonical fixture is
[`fixtures/review-comment-session-v2.json`](fixtures/review-comment-session-v2.json).
It contains route, action, state, and document-region candidate comments plus
one exact additive promotion into a committed selection.

## Frozen v1 boundary and strict import

`sfm.review-session/1` is frozen. New target forms, candidate metadata, and
promotion evidence must not be added to the v1 schema or its Java/Rust codecs.
The v2 codec imports v1 only by first parsing it with the strict v1 codec; it
does not guess, repair, or reinterpret malformed input. Each valid v1 comment
is migrated losslessly to a v2 `committed_selection` target containing the
unchanged v1 selection rule. The v1 session object and canonical v1 writer
remain unchanged.

Migration is one-way at the persistence boundary: `parseOrMigrate` accepts
exactly `sfm.review-session/1` or `sfm.review-session/2`, returns a v2 model,
and the v2 writer emits `sfm.review-session/2`. Candidate targets and promotion
links have no v1 representation and must never be down-converted.

## Root model and inherited contracts

```text
ReviewSessionV2 {
    schema,
    id,
    title,
    coordinate_system,
    revision_lanes[],
    comments[],
    style_rules[],
    completion_policy
}
```

`schema` is exactly `sfm.review-session/2`. `coordinate_system` remains exactly
`utf8_byte_half_open`. Revision lanes, immutable document revisions, style
rules, completion policy, provenance, hashtag extraction, and committed
selection-rule algebra retain their
[`sfm.review-session/1`](review-comment-session-v1.md) meanings.

Comment ids are nonblank and unique within a session. Comment text remains the
only hashtag authority; a serialized `tags` field is forbidden. A v2 comment
replaces the v1-only `selection_rule` member with a tagged `target`:

```text
CommentV2 {
    id,
    text,
    provenance { kind, producer, version, parent_comment_ids[] },
    target: CommittedReviewTarget | CandidateTrajectoryTarget
}
```

## Tagged comment targets

### Committed selection

```text
CommittedReviewTarget {
    kind: "committed_selection",
    selection_rule,
    candidate_promotion?
}
```

`selection_rule` is the v1 UTF-8 selection-rule algebra. A normal committed
comment omits `candidate_promotion`. A committed child created by explicit
exact promotion includes the promotion link described below.

### Candidate trajectory

```text
CandidateTrajectoryTarget {
    kind: "candidate_trajectory",
    machine_id,
    machine_revision,
    trajectory_plan_revision_id,
    route_id,
    route_step_position,
    trajectory_step_id?,
    predicted_state_id,
    predicted_state_hash?,
    projection_status,
    target_kind,
    action_intent_id?,
    projected_document_selection?,
    evaluator_revision?,
    evaluator_evidence[]
}
```

A candidate target is an immutable address into one proposed future. It never
means “the current plan”, “the currently selected route”, or “whatever frame
now occupies this position”. Replanning does not rewrite its machine revision,
plan revision, route, step, state, status, or witnesses.

Every candidate field has the following contract:

| JSON field | Requirement |
| --- | --- |
| `machine_id` | Nonblank stable id of the trajectory machine. |
| `machine_revision` | Nonnegative machine revision captured with the candidate. |
| `trajectory_plan_revision_id` | Nonblank immutable plan-revision id. |
| `route_id` | Nonblank immutable route id within that plan revision. |
| `route_step_position` | Nonnegative frame position. Position `0` is the route start; position `N` is the state after step `N`. |
| `trajectory_step_id` | Omitted at position `0`; required and nonblank at every position greater than `0`. |
| `predicted_state_id` | Nonblank identity of the predicted state at this position. |
| `predicted_state_hash` | Optional nonblank predicted-state witness. It is mandatory for a document-region target. It is a domain state hash, not necessarily a SHA-256 document digest. |
| `projection_status` | Lowercase form of `unrequested`, `queued`, `running`, `materialized`, `conflict`, `cancelled`, `budget_exhausted`, `unknown`, or `external_barrier`. |
| `target_kind` | Lowercase `route`, `step`, `action`, `state`, or `document_region`. |
| `action_intent_id` | Optional nonblank action-intent identity; mandatory when `target_kind` is `action`. |
| `projected_document_selection` | Mandatory only for `document_region`; forbidden for every other target kind. |
| `evaluator_revision` | Optional nonblank identity of the evaluator that produced the projection. |
| `evaluator_evidence` | Deterministically key-sorted array of nonblank `{key, value}` entries with unique keys. The array is present even when empty. |

`step` and `action` targets require `trajectory_step_id`. A route, state, or
document-region target may also retain an `action_intent_id` when that identity
was present on the source candidate frame; only an action target requires it.

## Projected document-selection witness

Candidate bytes remain authoritative in the candidate-history subsystem. The
comment store does not duplicate them. A materialized document-region target
instead pins this exact witness:

```text
ProjectedDocumentSelection {
    document_id,
    document_state_hash,
    document_text_sha256,
    start_byte,
    end_byte,
    selected_text_sha256
}
```

- `document_id` and `document_state_hash` are nonblank identities.
- `document_text_sha256` is lowercase 64-character SHA-256 of the complete
  projected UTF-8 document bytes.
- `[start_byte, end_byte)` is a nonnegative forward half-open UTF-8 byte range.
- `selected_text_sha256` is lowercase 64-character SHA-256 of exactly those
  selected bytes.
- The candidate target must also have `projection_status: "materialized"` and
  a `predicted_state_hash`.

The persisted range is not a Java UTF-16 range, Unicode-scalar range, line and
column pair, or rendered glyph rectangle. Adapters derive those views only
after validating the pinned UTF-8 witness against materialized bytes.

## Unavailable-frame rules

Route, step, action, and state comments are valid when a candidate frame is not
materialized. This permits discussion of queued work, conflicts,
cancellations, budget exhaustion, unknown outcomes, and external barriers.
Evaluation reports such a target as `candidate_pinned_unavailable` and returns
no source ranges.

A document-region comment is never valid on an unavailable frame. Producers
must not invent document bytes, ranges, document hashes, or selected-text
hashes to make one appear addressable. `projected_document_selection` is
therefore rejected unless the target kind is `document_region`, and a
`document_region` is rejected unless the frame is `materialized` and both its
state and selection witnesses are present.

Unavailable candidate comments remain pinned discussion. They do not silently
retarget to a newer plan, a trustworthy predecessor, or an executed state.

## Evaluation and approval

Committed targets use the v1 selection evaluator and add the v2 evaluator
version `sfm-review-v2/1`. Candidate targets evaluate to
`candidate_pinned` when materialized and `candidate_pinned_unavailable`
otherwise. Candidate evaluation does not synthesize committed source ranges.

Approval is deliberately conservative:

- a candidate comment is never effective approval;
- a committed comment carrying `candidate_promotion` is never effective
  approval; and
- copying text containing `#approved` during promotion does not transfer the
  human decision represented by that hashtag.

Only a separately authored, unpromoted committed comment can become effective
approval under the inherited v1 policy and exact/relocated evaluation rules.

## Exact additive promotion

Promotion is an explicit decision, not an automatic consequence of execution.
Only a candidate `document_region` with a projected selection can be promoted.
The execution witness supplies a decision id, executed history head, executed
state id and hash, the logical executed document id, a unique committed
document revision, and committed UTF-8 range. The logical id is compared with
the candidate document identity; the committed revision id remains free to be
content-addressed so multiple executed snapshots of one document coexist.

Promotion succeeds only when all correspondence is exact:

1. the predicted state hash equals the executed state hash;
2. candidate and committed document ids are identical;
3. the committed document's recorded SHA-256 equals its actual UTF-8 bytes and
   the candidate document SHA-256;
4. start and end are valid UTF-8 boundaries and exactly equal the candidate
   half-open range; and
5. SHA-256 of the selected committed bytes equals the candidate selected-text
   witness.

On success, promotion is additive. The original candidate comment and target
remain byte-for-byte addressable. A new committed comment is appended with the
same text, `candidate_promotion` provenance, the candidate comment id as its
parent, an exact literal UTF-8 selection rule, and this link:

```text
CandidatePromotionLink {
    source_candidate_comment_id,
    source_candidate_target_sha256,
    decision_id,
    executed_history_head_id,
    executed_state_id,
    executed_state_hash,
    correspondence: "exact" | "witnessed_migration",
    correspondence_evidence[]
}
```

Exact links carry an empty evidence array because their hashes and byte range
are the correspondence proof. A witnessed migration carries at least one
nonblank evidence/rationale entry and is created only by an explicit migration
decision against a validated committed document and UTF-8 range.

If the committed document is not already in a revision lane, the kernel adds a
`candidate-execution/<machine_id>` lane containing it. An existing document id
may not name different bytes.

Missing, divergent, ambiguous, and unsupported correspondence create no child,
change no target, and transfer no approval. Route, step, action, and state
comments remain candidate discussion and are unsupported by document-region
promotion.

When exact promotion reports divergence, `migrateWitnessed` is a separate,
explicit operation. It does not guess a range or reuse candidate hashes. The
decision names the current logical document, a content-addressed committed
revision, a valid UTF-8 range, and correspondence evidence. Success appends a
`candidate_migration` child with `correspondence: "witnessed_migration"`;
the original candidate remains unchanged and the child remains ineffective as
approval. Invalid or missing evidence changes nothing.

`source_candidate_target_sha256` is not a hash of incidental JSON whitespace.
The kernel starts with the domain separator
`sfm.candidate-comment-target/1`, then appends candidate values in model order.
Each value is framed as a newline, its Java UTF-16 code-unit length, a colon,
and the value. Missing optional strings contribute an empty value; enum values
use their uppercase Java names; projected-selection fields are included only
when present; evaluator evidence follows deterministic key order. The link is
lowercase SHA-256 of the resulting UTF-8 framing.

## Canonical JSON conventions

- Root `schema` is exactly `sfm.review-session/2`.
- The canonical writer uses two-space indentation and one trailing newline.
- JSON object member order is not semantic. The canonical writer and fixture
  nevertheless use the model/codec field order to make byte diffs stable.
- Semantic arrays retain model order. `evaluator_evidence` is the exception:
  the model sorts it by `key` and rejects duplicate keys before writing.
- Optional values are omitted, never encoded as `null`. Required arrays are
  present even when empty. `correspondence_evidence` is empty for exact links
  and nonempty for witnessed migration links.
- Discriminators and enum values use lowercase snake case in JSON.
- Document and selected-text SHA-256 values are lowercase 64-character hex.
  Domain state hashes are nonblank opaque strings unless their producer
  defines a stronger format.
- Text coordinates remain zero-based UTF-8 byte offsets with half-open ranges.
- Paths inherited from v1 are normalized repository-relative `/` paths and
  documents use encoding `utf-8`.
- Hashtags are derived from `text`; writers must not emit authoritative
  `tags`.
- Unknown target and selection-rule discriminators are rejected. Callers must
  not rely on arbitrary unknown members surviving a parse/write round trip.
- A parse followed by a canonical write is stable; canonical output parsed and
  written again produces identical bytes.

## Fixture conformance

The fixture demonstrates these independent facts:

1. a route-start target omits `trajectory_step_id`;
2. an action target carries its required step and action-intent identities;
3. an unavailable state target carries no fabricated document witness;
4. a materialized document-region target pins exact UTF-8 hashes and the byte
   range for `café` in `1. café\n2. tea\n`;
5. its exact promoted committed child retains the candidate as provenance,
   uses the same committed byte witness, and keeps the logical candidate
   document id distinct from the content-addressed committed revision id; and
6. the candidate and promoted text deliberately contain `#approved`, while
   both remain ineffective approval under the v2 kernel.
