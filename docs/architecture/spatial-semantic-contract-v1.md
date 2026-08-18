# Spatial semantic contract v1

**Status:** Frozen for SS-0 on 2026-08-18. Any incompatible field or semantic
change requires a schema-version increment and migration of the adjacent golden
fixtures.

This contract joins Rust-owned Java semantics to Java-owned canvas interaction.
It does not make Rust authoritative for Minecraft layout and it does not let
Java silently infer semantic regions from the nearest token.

## Schema identities

| Value | Schema |
| --- | --- |
| Domain | `sfm.region-domain/1` |
| Region | `sfm.region/1` |
| Projection | `sfm.region-projection/1` |
| Outlink | `sfm.outlink/1` |
| Interaction probe | `sfm.interaction-probe-result/1` |
| Java document interaction map | `sfm.java-interaction-map/1` |
| Coverage request | `sfm.spatial-coverage-request/1` |
| Coverage report | `sfm.spatial-coverage-report/1` |
| Navigation framing observation | `sfm.navigation-framing-observation/1` |

All wire fields use `snake_case`. Resource identifiers and action identifiers
use canonical namespaced strings. Paths in portable reports use `/` separators;
machine-local paths are evidence fields and never become resolver authority.

## Ownership and generation

Rust/`sfm-propagate-changes` owns parsed-Java syntax regions, symbol identities,
definition/reference relations, matching delimiters, containment/children,
semantic recovery diagnostics, dependency/JDK source identities, and the
generation-tagged `sfm.java-interaction-map/1` payload.

Java/Minecraft owns shaped glyphs, screen-to-canvas transforms, clipping,
physical/logical panel allocations, GUI scale, pointer hit testing, gesture
routing, coverage over the actual painted canvas, heatmap rasterization, action
invocation, command-palette presentation, and destination framing.

Every map/probe carries all of:

- workspace generation and fingerprint;
- document address, content hash, and document generation;
- semantic/index generation and fingerprint; and
- layout generation and fingerprint when canvas coordinates are present.

A consumer rejects a stale generation. It never projects a stale map onto a
new document or layout because the path happens to have the same display name.

## Domains, coordinates, regions, and edges

The initial domain kinds are:

- `canvas`: two finite `f64` canvas-unit axes, `x` then `y`;
- `screen`: two finite `f64` physical-pixel axes, `x` then `y`;
- `utf8`: one non-negative integer byte-offset axis;
- `text`: one-based Unicode-scalar `line` and `column` axes plus a
  non-negative UTF-8 byte witness;
- `java-syntax`: stable document/index-scoped node identities; and
- `path`: resolver-authorized canonical address identities.

`SFMRegionDomain` fields are `schema`, `id`, `kind`, `dimensions`,
`coordinate_kinds`, `authority`, and `snapshot_identity`. A domain identifier
is stable only within its named snapshot.

`SFMRegion` fields are `schema`, `id`, `domain_id`, `representation`, `bounds`,
`edge_policy`, `semantic_kind`, `provenance`, and `projection_ids`.
Representations in v1 are `rectangle`, `interval`, `syntax-node`,
`finite-points`, and `whole-domain`. A bounds axis contains finite
`start_inclusive` and `end_exclusive` values. Empty bounds are legal and have
equal ends. Reversed bounds and non-finite coordinates are invalid.

All geometric and linear bounds are half-open: `start <= point < end`. Exact
edge tests therefore belong to exactly one adjacent non-overlapping region.
Finite-point membership is exact. Syntax-node/path membership is delegated only
to the resolver named by the region provenance; it is not guessed from text.
Unbounded regions are not serialized as pixel lists. A finite materialization,
when requested, is deterministic and explicitly budgeted.

`SFMRegionProjection` fields are `schema`, `id`, `from_domain_id`,
`to_domain_id`, `loss`, `completeness`, `transform`, `fingerprint`, and
`authority`. `loss` is `lossless`, `many-to-one`, `one-to-many`, or `partial`.
`completeness` is `complete` or `incomplete`. A partial projection must explain
its missing domain in diagnostics and cannot certify coverage outside its
witnessed range.

## Interaction classifications and outlinks

The requested intent is one of `navigate`, `inspect`, `copy`, `edit`, or
`context`. The classification status is one of:

- `actionable`: has at least one action draft or outlink for the requested
  intent;
- `explicit-no-action`: deliberately has no action and includes a reason code;
- `unsupported`: the provider cannot interpret this domain/intent and includes
  a reason code; or
- `unclassified`: no provider made a deliberate claim and coverage fails.

An inspect/copy/context action does not satisfy the strict navigation
denominator.

`SFMOutlink` fields are `schema`, `id`, `source_region_id`,
`destination_region_id`, optional `destination_query`, `relation_kind`,
`intent`, `provider_id`, `provider_generation`, `reason`, `confidence`,
`completeness`, `recommended_projection`, `action_drafts`, and `provenance`.
Provider identifiers are mod-qualified. `confidence` is `resolved`,
`partially-resolved`, or `recovery`; `completeness` is `complete` or
`incomplete`.

The v1 navigation relation kinds are:

- `definition`, `reference`, and `usage`;
- `matching-delimiter`;
- `containing-region` and `child-region`;
- `declaration-start`, `declaration-end`, `signature`, `body`, and
  `statement`;
- `file`, `path`, and `semantic-root`; and
- `recovery`, which must not be presented as an exact relation.

The v1 target projections are `start`, `end`, `centre`, `percentage`,
`nth-source-row`, `nth-source-line`, `nth-child`, and `named-landmark`.
Percentages are finite and in `[0, 1]`; indices are zero-based and
bounds-checked. Both opening and closing delimiters deliberately expose the
matching delimiter and containing-region choices. Punctuation receives an
explicit semantic relation or a witnessed strict-profile exception; it is not
silently assigned the nearest token.

Action drafts are canonical action identifiers plus ordered string arguments.
They carry no callback or ambient filesystem authority. Provider ordering is
descending integer priority, then ascending provider identifier. A duplicate
provider identifier is rejected. Equal-priority providers remain distinct and
produce a deterministic tie diagnostic rather than relying on registration
order. One provider's failure is an isolated diagnostic.

## Interaction probes and certified regions

`SFMInteractionProbeResult` fields are `schema`, `query_domain_id`,
`query_point`, `requested_intent`, `certified_region`, `classification`,
`outlinks`, `action_drafts`, `provider_evidence`, `workspace_generation`,
`document_generation`, `semantic_generation`, and `layout_generation`.

The query point is always retained. A provider-certified region is a sound
claim that every point in that region has the same intent classification and
ordered candidate set for the named generations. It is never fabricated from
nearest-token fallback. The coverage oracle validates representative interior,
edge, corner, just-inside, and just-outside witnesses. Contradiction causes
deterministic subdivision/bisection and is reported; it never broadens the
claim.

## Coverage request, profiles, and report

The canonical registered action is:

```text
sfm:spatial/coverage/run <scope> <selector> <profile> <layout-matrix> <seed> <budget> <artifact-destination>
```

The command-palette form is:

```text
sfm action invoke sfm:spatial/coverage/run document focused sfm:strict_java_navigation sfm:auto_1_through_8 0 100000 auto
```

The out-of-process remoting form is:

```text
sfm spatial coverage run document focused sfm:strict_java_navigation sfm:auto_1_through_8 0 100000 auto
```

`scope` is `document` or `workspace`. `selector` is an opaque resolver-owned
selector captured to concrete identities before asynchronous work starts;
`focused` is the v1 built-in. This contract does not freeze the future X-10
spatial selector grammar. `seed` is a non-negative decimal 64-bit integer.
`budget` is a positive maximum semantic-query count. `auto` artifact output is
the current branch's SFM-owned preview artifact root. Explicit destinations
must pass existing artifact-path authority checks.

`SFMSpatialCoverageRequest` fields are `schema`, `request_id`, `scope`,
`selector`, `profile`, `layout_matrix`, `seed`, `budget`,
`artifact_destination`, and the captured workspace/document identities and
fingerprints.

The frozen profiles are:

- `sfm:classification`: every rendered glyph is deliberately classified;
- `sfm:strict_java_navigation`: every parsed-Java non-whitespace glyph has a
  navigation outlink or an individually witnessed approved exception;
- `sfm:real_gesture`: the actual gesture route produces the selected action;
- `sfm:branch_boundary`: required provider/decision branches and region
  boundaries are witnessed; and
- `sfm:reciprocity`: resolved static definition/reference edges have consistent
  inverse evidence or a typed exception.

The report publishes separate numerators and denominators for classification,
navigation, action-only, real gesture, provider/decision branch, boundary, and
reciprocity coverage. No aggregate percentage may hide one of them.

Before analysis, workspace coverage snapshots and canonicalizes every active
root/source set and emits one file row. Terminal file states are `covered`,
`partial`, `unsupported-extension`, `missing`, `stale`, `parse-failed`,
`layout-failed`, `index-failed`, `timeout`, `skipped`, and `failed`. No absent
row counts as success.

`SFMSpatialCoverageReport` also records the policy and policy version, seed,
budget, exact sample sequence, certified partitions, exceptions, next
candidates, semantic query count, certified-region reuse, cache hits,
subdivisions, fallback probes, and JSON/compact-map/heatmap/framing artifact
paths. A `100%` statement is valid only with profile, corpus fingerprint,
layout matrix, seed/policy, and explicit denominator.

## Navigation framing

`SFMNavigationFramingObservation` fields are `schema`, `pane_id`,
`document_address`, `document_hash`, `destination_region_id`,
`landmark_projection`, `document_bounds`, `line_bounds`, `destination_bounds`,
`viewport_bounds`, `inset`, `previous_camera`, `chosen_camera`,
`visible_intersection`, `document_left_visible`, `line_left_visible`,
`document_top_visible`, `landmark_visible`, and optional `clipping_reason`.

Framing is solved independently per axis. If the chosen landmark and the
line/document left edge fit horizontally, both remain visible with positive
inset and camera coordinates never overscroll negatively. Otherwise the
landmark remains usable and deterministic leading context is maximized.
First-line targets preserve top inset when possible. Mid-file targets do not
force line 1 into view. V1 clipping reasons are `destination-wider-than-viewport`,
`leading-context-does-not-fit`, `destination-taller-than-viewport`, and
`viewport-too-small-for-inset`.

## Golden scenario corpus

The checked-in SS-0 corpus must retain examples of a package/import, annotation,
modifier, qualified name, class/nested class, field, parameter, local, method,
signature/body, both brace ends, semicolon, operators, string and numeric
literals, comment, malformed fragment, Unicode identifier/text, and CRLF.
Layout witnesses include tabs, wide Unicode glyphs, first-line and mid-file
targets, narrow/wide panels, long lines, clipping, pan/zoom, and GUI scales Auto
and 1 through 8. Generated `*-actual.json` and heatmap outputs remain ignored
beside tracked expected artifacts.

