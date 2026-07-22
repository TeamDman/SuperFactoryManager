# Repository review bundle v1

Status: frozen for the first real-repository review implementation.

The repository review bundle is the portable, producer-neutral handoff between
host-side repository inspection and the in-game review workspace. Rust prepares
the bundle; Java validates and imports it. Neither side may infer source bytes
from the ambient worktree after the bundle has been written.

The canonical conformance fixture is
[`fixtures/repository-review-bundle-v1.json`](fixtures/repository-review-bundle-v1.json).

## Envelope

The UTF-8 JSON document has `schema` equal to
`sfm.repository-review-bundle/1` and contains:

- a stable bundle `id`, display `name`, and repository description;
- complete immutable `before` and `after` snapshots;
- one comparison whose snapshot ids match those snapshots;
- producer metadata used as review-comment provenance.

Unknown fields are ignored. Unknown schema versions, duplicate object keys,
duplicate paths or ids, invalid hashes, invalid ranges, and dangling references
fail closed. Writers emit deterministically ordered objects and arrays.

## Repository paths and bounds

Paths are Unicode NFC, use `/`, and compare case-sensitively by UTF-8 byte
order. They must be relative and non-empty. Backslashes, absolute/rooted paths,
drive or UNC prefixes, NUL, and empty, `.` or `..` segments are rejected. Git
paths that cannot be represented as UTF-8 NFC fail closed in v1.

An importer rejects a bundle before unbounded allocation when it exceeds any
of these v1 limits:

- 100,000 files per snapshot;
- 16 MiB decoded content per file;
- 256 MiB decoded content per snapshot;
- 512 MiB encoded JSON document size.

## Snapshots

A snapshot has schema `sfm.repository-snapshot/1`, a content-addressed `id`,
source metadata, and path-sorted `files`. Each file has a normalized path, an
encoding, content, and the lowercase SHA-256 of its exact bytes.

`utf8` content is held in `text` and must round-trip through strict UTF-8.
`base64` content is held in `data` and decodes with the RFC 4648 standard
alphabet and required padding. The encoding tag does not change the bytes.

The snapshot id is `sha256:` followed by SHA-256 over this binary framing:

1. UTF-8 bytes `sfm.repository-snapshot/1`, then one NUL byte;
2. for each path-sorted file: unsigned big-endian u32 path-byte length, UTF-8
   path bytes, one encoding byte (`0` for `utf8`, `1` for `base64`), unsigned
   big-endian u64 content-byte length, then exact content bytes.

The hash intentionally does not cover JSON whitespace, member ordering, source
labels, or producer metadata.

## Comparison

The comparison has schema `sfm.repository-comparison/1`, references the two
snapshot ids, and contains path-sorted file changes. Change kinds are `added`,
`removed`, `modified`, `renamed`, and `unchanged`. A producer may report a
rename only when it has a deterministic, unambiguous exact-byte pairing;
otherwise it reports removal and addition.

Text operations are `insert`, `delete`, or `replace`. Their before and after
selections contain a snapshot-relative path, zero-based half-open UTF-8 byte
range, and SHA-256 of the selected bytes. A side absent from an operation is
`null`. Ranges must fall on UTF-8 code-point boundaries and within the cited
file. Operations are deterministically ordered and have unique stable ids.
Snapshot files own source bytes; operations do not duplicate source text.

Binary changes carry diagnostics instead of text selections. Empty insertions
or deletions also produce a visible diagnostic rather than an invalid empty
review selection.

The v1 baseline comparator may be textual and line-oriented internally, but it
must lower results into the byte contract above. Semantic/AST operations may be
introduced later without changing the snapshot representation.

## Review-session import

Java deterministically imports the comparison into
`SFMReviewSessionV1` comments:

- `added` / `insert` yields `#added` over the after selection;
- `removed` / `delete` yields `#removed` over the before selection;
- `modified` / `replace` yields one `#modified` comment whose selection is the
  union of its before and after ranges.

Imported comments retain producer id, producer contract version, comparison
operation id, snapshot ids, and selected-byte hashes as provenance. Diagnostics
remain visible but never manufacture invalid glyph selections.

## Storage and lifecycle

The host producer writes atomically into a managed review-bundle inbox. The
game enumerates and opens bundles by id/name from that inbox; it does not expose
arbitrary host-path access. A deterministic session id derived from bundle id
allows close/reopen and process restart to restore user comments from the
existing AppData review-session store.

The first end-to-end proof uses real SFM revisions `d07bef66c` and `8e9946d9f`:
prepare the bundle, open it through the command palette, browse a changed file,
add a comment, close, and reopen with that comment restored.
