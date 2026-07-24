# Review comment session interchange v1

## Authority and purpose

This is the shared contract for the first Java, Rust, and in-game comment-review
implementations. Product intent and later phases live in the [global comment
selection and review sessions
plan](../tasks/global%20comment%20selection%20and%20review%20sessions%20plan.md).
Implementations may add internal indexes but must read and write the semantic
model defined here.

The canonical fixture is
[`fixtures/review-comment-session-v1.json`](fixtures/review-comment-session-v1.json).

## Canonical source coordinate

Durable textual selections use zero-based UTF-8 byte offsets into the exact
decoded UTF-8 document byte sequence. Ranges are half-open: `[start_byte,
end_byte)`. The empty range is legal. Neither Java UTF-16 indices, Unicode
scalar indices, line/column positions, nor rendered glyph cells are persisted
as the authority.

Every text document records:

- normalized repository-relative path using `/`;
- encoding `utf-8` in v1;
- lowercase hexadecimal SHA-256 of the exact UTF-8 bytes;
- exact text in the dumb v1 fixture/interchange; and
- a stable document-revision id scoped by session/lane/snapshot.

Java and Minecraft adapters must provide checked UTF-8-byte to editor/glyph
projections. A byte offset inside a multibyte sequence is invalid. Rust and AST
tools must retain byte ranges directly. Line/column and visual rectangles are
derived views.

## JSON conventions

- Root `schema` is exactly `sfm.review-session/1`.
- Semantic collections are arrays with deterministic ordering; JSON object
  member ordering is not semantic.
- Paths never escape the declared repository root.
- Hashes are lowercase SHA-256 hex.
- Enum/discriminator values use lowercase snake case.
- The canonical writer uses two-space indentation and a trailing newline.
- Unknown selector kinds are rejected with a diagnostic; unknown nonsemantic
  presentation fields may be retained by adapters where practical.
- Persistence is atomic and last-valid-session recovery is mandatory in the
  product implementation.

## Root model

```text
ReviewSessionV1 {
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

`coordinate_system` is exactly `utf8_byte_half_open` in v1.

## Revision lanes and documents

Each lane identifies a repository/version stream and two immutable snapshots:

```text
RevisionLaneV1 {
    id,
    repository { id, root_hint },
    version_label?,
    before { id, documents[] },
    after  { id, documents[] }
}

DocumentRevisionV1 {
    id,
    path,
    encoding,
    sha256,
    text
}
```

`root_hint` helps a local adapter locate content but is not part of snapshot
identity and cannot authorize ambient filesystem access. Production snapshots
may later externalize bytes through the canonical snapshot plan; the v1 fixture
embeds text to keep Java/Rust round trips dumb and self-contained.

## Comments and derived hashtags

```text
CommentV1 {
    id,
    text,
    provenance {
        kind,
        producer,
        version,
        parent_comment_ids[]
    },
    selection_rule
}
```

`text` is the only authority for hashtags. V1 extraction recognizes unquoted
`#identifier` and quoted `#"words with spaces"`; exact escaping and Unicode
normalization are defined by the kernel tests. No serialized `tags` field is
allowed in v1.

Provenance kinds initially include `human`, `diff_engine`, `compiler`,
`sfm_audit`, `approval_rule`, `migration`, and `legacy_ledger`.

## Selection-rule algebra

Every rule is a tagged JSON object with `kind`.

### Literal range

```json
{
  "kind": "literal_utf8_range",
  "document_revision_id": "lane:after:path",
  "start_byte": 0,
  "end_byte": 10,
  "document_sha256": "...",
  "selected_text_sha256": "..."
}
```

The document hash identifies the original immutable target. The selected-text
hash is a witness for exact relocation and change detection.

### Set rules

```json
{ "kind": "union", "rules": [ ... ] }
{ "kind": "intersection", "rules": [ ... ] }
{ "kind": "difference", "include": { ... }, "exclude": [ ... ] }
```

Order is deterministic but does not make the mathematical result ordered.
Duplicate normalized ranges collapse during evaluation.

### Reserved v1-compatible rule kinds

`text_match`, `syntax_region`, `symbol_query`, and `diff_region` are reserved
discriminators. The Phase 1 fixture does not require their execution. Producers
must not encode them until their exact fields and evaluation laws are added to
this contract.

## Evaluation result

Evaluation is derived and is not embedded in the authoritative fixture. A
kernel returns one result per comment:

```text
CommentEvaluationV1 {
    comment_id,
    evaluator_version,
    status,
    ranges[],
    diagnostics[]
}
```

Initial statuses are `resolved_exactly`, `resolved_with_relocation`,
`ambiguous`, `no_match`, `invalid_rule`, `scope_missing`, and
`content_changed`. Ranges retain document-revision id and UTF-8 offsets.

Approval effectiveness is a policy projection over comment text, provenance,
and evaluation status. The kernel must not turn `#approved` into a persisted
boolean.

## Style rules

```text
CommentStyleRuleV1 {
    id,
    required_hashtags[],
    priority,
    foreground?,
    background?,
    underline?,
    gutter_marker?,
    enabled
}
```

Colours use eight-digit ARGB hex. Priority is resolved independently per
channel. The v1 UI may implement background, underline, and gutter first while
retaining unknown supported fields during round trips.

## Completion policy

```text
CompletionPolicyV1 {
    coverage_mode,
    approval_hashtag,
    blocking_hashtags[]
}
```

The fixture uses `changed_surface`, `#approved`, and blockers `#problem` plus
`#needs-change`. This contract records policy; the initial kernel need not
compute repository-wide coverage before the comparison producer exists.

## Phase 1 conformance

Java and Rust conform when they:

1. deserialize the canonical fixture;
2. serialize it deterministically without semantic loss;
3. derive the same normalized hashtags;
4. validate every UTF-8 boundary and hash;
5. evaluate literal, union, intersection, and difference rules identically;
6. report changed/invalid selections without effective approval; and
7. reject a fixture containing a serialized authoritative `tags` field.

