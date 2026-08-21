# Global comment selection and review sessions plan

**Plan status:** Active
**Last updated:** 2026-08-21

## Purpose

Replace the provisional boolean review ledger with a general comment system
whose selection rules can identify glyphs across files, revisions, repositories,
and Minecraft-version worktrees. Human review, human approval, audit findings,
compiler diagnostics, diff presentation, derived approval, arbitrary notes, and
jump lists become conventions over this shared substrate rather than separate
incompatible data models.

The immediate product remains an in-game source-review workspace, but the model
must also be usable by Rust CLI producers, Java compiler/audit adapters, future
Vox clients, and exported review artifacts. Source code remains presented as
source code. ASTs and symbol indexes help select and migrate source regions; a
generic tree viewer is not the primary experience.

This document is authoritative for review sessions, comments, selection rules,
comment evaluation/migration, review coverage, and comment-driven source
colourization. Related plans own snapshot bytes, comparison/refactoring
production, general application theming, window management, and replay.

## Shared selection substrate relationship — 2026-08-12

`typed selections relations and lazy explorers plan.md` is authoritative for
general typed paths/path expressions, live named selections, immutable
selection revisions, set-valued selectors, parent/child relations, and lazy
explorer projection. This review plan remains authoritative for comments,
review-session scope, immutable source snapshots/hashes, review coverage, and
the rules that select glyph ranges within pinned document revisions.

The two models must converge through plan item X-9, not through a second
selection engine:

- the existing `SFMReviewSessionV1.SelectionRule` remains a pinned,
  reproducible review rule until an explicit adapter can translate it to the
  shared algebra without weakening snapshot/hash checks;
- a live explorer/editor selection may be projected into a pinned review
  selector only by recording the resolved document identity, revision/hash,
  ranges, source expression, and selection revision as provenance;
- a review selector may be viewed through `selection://...`, but viewing it
  must not silently turn a pinned review decision into a moving live head;
- comments keep their current union/intersection/difference semantics while
  shared operations supply the common typed path and set-operation machinery;
  and
- editor cursor ranges remain operational editor state. They may project into
  a selection, but the review model does not own cursor order, primary cursor,
  anchor/head direction, or insertion behavior.

This relationship deliberately defers the adapter until X-9. X-1 through X-7
must not migrate or rewrite persisted review sessions. The adapter requires
round-trip, stale-revision, hash-mismatch, live-head-versus-pinned, and
multi-document tests before either representation can replace existing review
storage.

`docs/tasks/spatial semantic surfaces outlinks and capability presenters plan.md`
now owns the generic domain/region/outlink algebra and spatial coverage that
can describe canvas and Java semantic surfaces. Comments remain owned here.
Selection X-9 plus spatial SS-7 must adapt a resolved region into a pinned
comment selector with document snapshot/hash, domain, projection, and
selection-revision provenance; neither plan may replace comments with a moving
live region or store a region as an unbounded pixel list.

## Graphical review markup extension — 2026-08-21

| ID | Active guidance | Required consequence | Coverage |
| --- | --- | --- | --- |
| RCMARK-1 | Historical source review should support Excalidraw/ShareX-like text, rectangle/polygon, line/arrow, and freehand annotation. | Reuse Text Editor v3/Draw primitives and bind them to ordinary comments; do not create a competing markup/comment engine. | Phase 6a |
| RCMARK-2 | One markup/comment may cover surfaces in several laid-out documents and revisions. | Persist a versioned multi-document layout projection plus pinned multi-document selection/evaluation. | Phase 6a + Phase 7 |
| RCMARK-3 | Drawing a rectangle means selecting/annotating what lies inside, but screen geometry must not become the only durable source address. | Resolve geometry to literal/semantic selector candidates and retain exact spatial/source witnesses; the user chooses the selector meaning. | Phase 3/4/6a |
| RCMARK-4 | Rearranging the canvas must not silently retarget an existing review decision. | Source selection remains pinned; markup uses its historical projection or an explicit witnessed projection migration. | Phase 4/6a |

The complete source-message re-audit restored one requirement that was only
implicit: a reviewer should be able to annotate historical source using an
Excalidraw/ShareX-like graphical language—text callouts, rectangles/polygons,
lines/arrows, and freehand marks—while the associated comment still targets a
durable selection that may span documents and revisions.

Ownership remains split deliberately:

- Text Editor v3/Draw owns versioned points, edges, shapes, freehand paths,
  global canvas coordinates, layers, and drawing tools.
- Spatial semantic surfaces owns document-layout projections, glyph bounds,
  point/shape-to-region queries, and coordinate witnesses.
- This plan owns the durable association among comment id, pinned selection
  rule/evaluation, markup projection, author/provenance, and style.

A review canvas may lay out revision-qualified documents at explicit origins,
for example document X at `(0,0)` and Y at `(100,0)`. That placement is a
versioned **view projection**, not the identity of either document or glyph.
Moving/reflowing a document cannot retarget an approved comment silently.

```text
ReviewMarkup {
  id,
  comment_id,
  pinned_selection_rule_and_evaluation,
  document_layout_projection_revision,
  draw_primitive_projection,
  style,
  provenance
}
```

Drawing a rectangle/freehand path first resolves the intersected glyph/semantic
regions against the exact layout/snapshot. The UI then offers literal and
structural selector candidates through the ordinary constrained action palette.
The accepted selector and exact witness are persisted. The primitive remains a
visual explanation/callout; transient screen pixels are never the only record
of what was annotated. A text callout displays the authoritative comment text
or an explicit presentation label rather than creating an accidental second
comment store.

Markup may span several laid-out documents. If a later layout projection moves
them, the markup can be transformed with a witnessed projection migration or
shown against its historical layout; its selected source ranges do not change
merely because the canvas was rearranged.

### Markup intent-audit evidence

- **Pass 1 — extraction:** Reread the complete attached review message and
  separated freehand/text/shape language, cross-document layout, historical
  revision identity, rectangle containment, semantic selector suggestions, and
  durable storage into RCMARK-1 through RCMARK-4.
- **Pass 2 — traceability:** Routed primitive geometry to Draw, spatial
  resolution to semantic surfaces, selection identity to this plan/X-9, and
  implementation/puppet evidence to Phase 6a.
- **Pass 3 — adversarial omission:** Checked that moving a document does not
  move its source identity, a transient screen rectangle is not the sole
  approval witness, callout text does not become a second comment authority,
  and multi-document gestures do not collapse to one file.
- **Known source limitation:** None; the complete attached source message and
  all three owning plans were available.

## Candidate-trajectory comment extension — 2026-08-21

| ID | Active guidance | Required consequence | Coverage |
| --- | --- | --- | --- |
| RCTEMP-1 | A projected/candidate history should be commentable while it is scrubbed, before its actions are executed. | Add a typed candidate target for trajectory plan revision, route, step/action/state, predicted hash/status, and optional projected source selection. | Phase 6b + snapshot 2.12/2.13 |
| RCTEMP-2 | Candidate comments may explain a whole route, one action/cost/barrier, one predicted state, or glyph regions within a predicted document. | Comment target is a tagged union rather than forcing every observation into a source range; region targets still retain pinned document/selection witnesses. | Phase 6b |
| RCTEMP-3 | Replanning must not silently move comments from an old proposed future to a new one. | Candidate targets pin immutable trajectory revisions. Replan leaves old comments inspectable; migration requires explicit correspondence evidence and a witnessed decision. | Phase 6b |
| RCTEMP-4 | Executing exactly what was predicted may connect a candidate comment to committed history, but must not rewrite provenance or manufacture approval. | Exact state/witness correspondence offers an explicit link/promote operation that creates a committed target and retains the candidate origin. Divergence or ambiguity suspends migration; human approval remains separate. | Phase 6b |
| RCTEMP-5 | Scrubbing a candidate is read-only and comment creation must not materialize/execute it as a side effect. | The comment UI consumes snapshot-plan candidate-frame addresses and statuses; unavailable frames may accept route/action comments but cannot invent source-region witnesses. | Phase 6b + snapshot 2.12 |

The [snapshot/episode plan](snapshot%20episodes%20and%20deterministic%20action%20environments%20plan.md)
owns candidate trajectory identities, projected state hashes/statuses, route
positions, and the read-only timeline. This plan owns comment identity, body,
tags, authorship, approval semantics, persistence, and migration. The adapter is
conceptually:

```text
CommentTarget =
    CommittedReviewTarget(...)
  | CandidateTrajectoryTarget {
      trajectory_plan_revision,
      route_id,
      step_or_action_or_state,
      predicted_state_hash_and_status,
      projected_document_and_pinned_selection?,
      candidate_provenance
    }
```

A candidate target is never “current plan head.” It names an immutable plan
revision. The UI labels it as candidate in editor, timeline, explorer, and
comment views. If the predicted frame is unavailable, blocked, or invalidated,
route/action-level comments remain valid, but no glyph-range target is created
without exact projected document bytes and layout/selection witnesses.

Executing a plan does not mutate the original comment. When committed state
hash, document identity, and selected witnesses agree exactly, an explicit
promotion creates a linked committed target/comment revision and records the
candidate source. Changed or ambiguous correspondence uses the same
conservative migration machinery as historical source changes. Direct human
approval and any candidate discussion remain distinguishable.

### Candidate-comment intent-audit evidence

- **Pass 1 — extraction:** Preserved candidate timeline scrubbing, comments on
  proposed alternatives, route/action/state/region granularity, immutable
  replans, and eventual execution as separate requirements.
- **Pass 2 — traceability:** Routed candidate state/seek identity to snapshot
  2.12/2.13 and comment persistence/migration/approval to Phase 6b.
- **Pass 3 — adversarial omission:** Prevented scrubbing/commenting from
  executing a candidate, “current plan” from acting as a moving target,
  replanning from retargeting old comments, unavailable frames from inventing
  ranges, and exact execution from silently converting discussion into human
  approval.
- **Known source limitation:** None for this follow-up.

## Release code-review profile — 2026-08-08

A release review is a required human gate on the prospective Git tag, not a
side effect of committing or passing tests. For every supported Minecraft
version, create a review lane from that lane's `previous_release` selector to
the candidate `HEAD`. The review session records the lane, baseline/candidate
snapshot identities and hashes, parser/index fingerprints, generated review
units, equivalence evidence, unresolved boundaries, and maintainer approval.
It must remain separate from Git's index, commit status, automated test
results, and release authorization.

The primary review unit is a changed function/method when the comparison can
identify one safely. Its selectable review surface is not only the method body:
it includes the declaration/signature, annotations, imports and static imports,
superclass/interface contracts, referenced fields/types/methods, callers or
method references when affected, and the statically resolved environment that
can change its meaning. Type, field, import, file, and diff-operation units are
used when a change is not safely reducible to a function.

Cross-lane deduplication is an evidence-producing operation. An unannotated
function whose body is identical across lanes is a candidate for one shared
review, but the system must re-open it when its dependency/import closure,
inheritance, mappings, annotations, configuration, feature profile, classpath,
or other relevant environment changed. `@MCVersionDependentBehaviour` marks a
deliberate version seam and prevents silent shared approval across that seam;
absence of the annotation is not itself proof of semantic equivalence.

Accepted source morphisms, such as a scoped Forge-to-NeoForge import/package
adaptation, are versioned policy rules with exact scope, tests, witnesses, and
an equivalence level. They may collapse only the declared transformation and
must leave the transformed import and its affected resolution closure visible
in the report. Unknown, ambiguous, content-changed, dependency-changed, or
out-of-scope morphisms suspend approval rather than guessing.

Comparison derives selectors after the before/after snapshots exist. A field
rename therefore narrows the changed surface to the definition and expands it
to old/new usages; a signature change expands to callers and method references;
an import change expands to affected type-resolution dependencies. The source
author does not need to annotate a change while making it. The comparison and
refactoring tools emit ordinary comments with provenance and selection rules,
including `#added`, `#removed`, `#modified`, `#renamed`, `#problem`, and
`#approved`, through this existing kernel.

The review artifact must be inspectable both in-game and outside the game. A
human-readable/HTML or equivalent structured report must support drill-down
from lane → review unit → before/after span → dependency/usages → equivalence
or morphism witness. A CLI-first workflow must provide the same information to
agents without an IDE plugin and must be able to preview a safe symbol rename
or similar transformation before explicit apply. No release tag is ready
until the maintainer has visually inspected and approved the generated review
artifacts and the prospective release JAR through the Prism harness.

This profile is the integration contract between this plan,
`release checkpoint and slim artifact plan.md`, and
`cli ast refactoring suite plan.md`; none may introduce a second snapshot,
comment, selector, approval, or equivalence model.

## Status and relationship to the existing prototype

Canonical `1.19.2` currently contains a fixture-driven source comparison panel
and `SFMReviewLedger` proof. It demonstrates separate reviewed, approved, and
audit presentation, local persistence, SHA-256 witnesses, restoration, and
stale invalidation. The newer explorer/panel vertical slice also proves the
changes, comments, and hashtag tree shapes, tombstones, stacked source leaves,
and Text Editor v3 presentation. These are valuable visual witnesses, but the
explorer still uses fixture source/comment data rather than a real
`previous_release..HEAD` multi-lane provider. The fixed decision fields remain
provisional. New review behavior must target the comment substrate described
here rather than expanding the boolean ledger.

The first migration should preserve the existing puppet as a compatibility
fixture by projecting a legacy decision into comments such as `#reviewed`,
`#approved`, and `#audit-forbidden`.

### Product-direction correction — 2026-08-02

The managed repository-review bundle was an earlier attempt to transport a
fixed before/after review into Minecraft. It proved the comment kernel against
real repository bytes, but it is superseded by direct revision-lane selectors
and composable explorers. Release-plan item P-1.2 removes the Java importer,
dedicated workspace, Rust `review prepare` producer, managed inbox, bundle
schema/fixture, tests, and puppet. Git history preserves that experiment; it is
not a compatibility surface for the unpublished release.

This removal does **not** remove `review/session`,
`review-comment-session-v1`, comment selection/evaluation, persistence,
derived hashtags, style rules, or the comment editing UI. Those are the
authoritative substrate. New adapters populate revision lanes and document
revisions directly from explicit selectors rather than reconstructing the old
bundle abstraction.

The required projections are:

- `sfm:explorer/changes <before-selector> <after-selector>`:
  `file → revision lane → before|after`;
- `sfm:explorer/comments`: `comment → file → region`; and
- `sfm:explorer/comments/hashtags`: `hashtag → file → region`.

Opening a region presents one immutable before/after document revision with
all applicable comment styles. Diff engines express added/removed presentation
through ordinary `#added` and `#removed` comments; a fixed two-pane diff screen
is not the primary review composition.

## Confirmed SFML matcher correction

Review filters should reuse the existing SFML parser and AST builder without
modifying any `.g4` grammar file. Valid examples follow the grammar's existing
tag forms, such as:

```sfml
*.java WITH TAG modified
*.java WITH #modified
```

The originating note is preserved here because it directly motivated both the
matcher correction and the comment model. Its dictation artifacts are retained.

<details>
<summary>Verbatim source note B — Wildcards, comment-derived tags, colourization, global scope, and diff comments</summary>

```text
*.java WITH TAG modified
*.java WITH #modified

I believe in our parser for the super anything-factory manager language, if the identifier is unquoted and contains an asterisk, it is converted to a regular expression, however, that conversion may not properly escape the period of character, so we might need to fix that because I believe resource locations do support period as a character, but if we want to avoid mishaps with regular expressions turning the period into a match
-factory manager language, if the ident.I believe in our parser for the super factory manager language. If the identifier is unquoted and contains an asterisk, it is converted to a regular expression. However, that conversion may not properly escape the period of character. But if we want to avoid mishaps with regular expressions turning the period into a match anything expression, we would want to make sure that that logic is escaping improperly.

(Text formatting got a bit bungled, please pardon the duplication)

> The method receives an approval highlight.


Perhaps we would need some kind of engine for colorization where given that each glyph is potentially a member of every tag or like for all the comments that exist, what glyph color do we want to assign and if that glyph is a member of a tag called hashtag problems then we want it to be read so similar to how Excel you can have colorization rules. So to our comment system and say like hashtag approval or hashtag approved is green and hashtag problem is red and hashtag needs change is orange or something then that would be an actual list of rules that we could view and that the user could add to begiven that we already have a color picker it would simply be the mapping from the tag to the color and then when it's actually time to color the text that might
 a little more algorithmically intense to find what the proper thing is but we could make it work.




>A compiler diagnostic can carry several tags while retaining one detailed message. We do not need to create a meaningless second annotation solely to hold the description unless the tag and diagnostic genuinely have different lifetimes or producers.

>Tags: #problem #compiler-error
Comment: Cannot resolve symbol 'factoryManger'.
Producer: javac


II would hesitate like we don't want to create a special case for comments versus tags when really it's a general string that is the thing that is associated. So if the rule is that hashtag problem is actually identified through, substring matching rather than it being the exact comment, then tags would merely be defined as hashtags within comments that are not broken by spaces or could be quoted so that you could do hashtag open quote something, something close quote kind of thing. So trying to nail down the terminology is it would be the comments, I guess that makes it comment is the thing and then the rule so each comment has a string value and then the rule that determines what glyphs in the document it applies to and tags is simply a derived computation from the content of the comment.


To the extreme, this comment system would let us include content like glyphs from different documents. So if I have a comment that spans every Minecraft version, and we have like, Match or Dodge Java or something, and I write a comment and I associate it with glyphs in the version of that document for each of the Minecraft versions, then that lets us create a more comprehensive review story, because sometimes there is comments, it's like, okay, in 1.20, the interface from Minecraft changes, and we should be extracting this logic to an MC version, dependent behavior, annotated method. So we need the ability to apply it across documents.

So, the rule associated with the comment would probably have some kind of hint on what files it would expect to apply to, and if the revision had changes, so if the latest version of the code has moved one of the files that this is talking about, so like in 1.20, we moved the file to a different location or whatever, and this comment that we had is now targeting something that no longer exists, then that would itself be something that is kind of flagged, similar to how we were talking about if we associate a comment with a literal substring like from glyph 0 to 200 or whatever, and then the document is only 100 glyphs long, and that itself is near, so that would also be trying to like keep things computationally responsible, so instead of saying something that would try and match across the content of every file, that's like okay, the comment, the rule of that comment indicates where and it's going to be searching for glyphs that it applies to, or rather when we have a comment, the interfaces that we interrogate and say what glyphs in what files does this comment apply to, and to evaluate that quickly is the rule itself will contain information about where it should be looking, and only in the worst case would we rip-grump through all the files or whatever as part of the rule for where the comment applies.

And this again is tied to our audit behavior where we have certain methods that we've declared as forbidden and permitted only within certain helper wrappers. And that's similar logic to what we're going to have that would say like add hashtag problem as a comment to any code that uses this forbidden annotation outside of those circumstances kind of thing.

So it's less like the comment rules are kind of living in the file next to the code or whatever It's more that we have This global session to encapsulate everything and within that session, we just have a list of comments and their rules and the Code those apply to is the super factory manager like source code locations and all the work trees that we have So it would be like we'd have our sessions in the app data or whatever where wherever we choose to persist this and You would be able to start a new session or whatever if you wanted to kind of start from a clean empty list of comments
 but the Comments themselves can span versions and multiple files


> “Reviewable” needs a precise definition. Requiring approval of every raw glyph would make whitespace and generated trivia awkward. We could initially def hashtag

We could have rules that say, like, if select a white space at the end of the file beyond the first new line character,WWe could have rules that say, like, if select a white space at the end of the file beyond the first new line character, and that would be something that is hashtag needs review or, like, hashtag should delete or something, like, select all the white space at the end of all the files, and then refine that selection to be only the white space after the, like, if the first character is a new line, then select all of the white space after that first character kind of thing, and then all of that would be subject to one comment that says, like, remove white space beyond the first new line at the end of the file, so that would be one comment with one rule that we, indicate the deletion of a method and we can't select that method if it's not in the document to say like I approved this deletion. So really it's the difference between the before and the a parYou make a good point about how it's not just the after document that matters because that document could through a missionindicate the deletion of a method and we can't select that method if it's not in the document to say like I approved this deletion so really it's the difference between the before and the after revisions is the surface of what can be selected and that is across all the versions so like if we have one branch per Minecraft version and I want to make a comment that selects changes then it's the the surface that comments can target is the surface of like defined as the change in each branch between two named revisions like not named but like you know specific so if I'm reviewing the change from 4.35 to 4.36 of the mod version then that mod version has kind of the previous mod version each branch has a tag for that so when we're creating these rules it would be like when a method was deleted then I would also be able to select the method in the old version as part of the surface of the review and through the diff algorithm just like the textual diff or the AST diff or whatever it is known that that method was removed and the review portion of that is that like I could select a method that is removed and I guess part of it would be that when we have this before and after thing the get diff or whatever diffing algorithm we do could produce a comment that is like the substrings or whatever hashtag added hashtag removed and then the colorization rule would build the like we would build the diff viewer through the comment system where the colorization of the hashtag added content in the after version of the document and then the hashtag removed comment in the before version of the document would produce the red and green coloring that we are familiar with for diff viewers and then I could additionally select that method in the before version of the document and say like add a comment why is this removed or whatever
```

</details>

The review adapter may expose file/change/comment metadata as virtual tags to
the existing matcher. It must not add review-only syntax to SFML.

The current unquoted-wildcard conversion is incorrect for literal punctuation.
`ASTBuilder.visitResource` and `visitTagMatcher` replace `*` with `.*` without
escaping the remaining literal text, so `*.java` becomes `.*.java` instead of
`.*\.java`. The period can therefore match any character. Resource-location
paths permit periods, so this is a real correctness issue rather than merely a
review-filter concern.

The fix belongs in a shared literal-glob conversion helper used by the AST
builder:

- quote every literal fragment as regex data;
- convert only unquoted `*` to the intended wildcard;
- retain the existing complete `**` tag-path-component deep-match behavior;
- keep quoted resource identifiers on their existing explicit-regex path;
- do not change lexer or parser rules; and
- preserve `RegexCache` correctness even if quoting prevents some fast paths.

Tests must prove that `*.java` matches `Example.java` but not `Examplexjava`,
that all legal literal resource-location punctuation is handled correctly, and
that existing namespace/resource wildcards plus `#forge:ingots/**` retain their
meaning.

## Core conceptual model

### Comment is the semantic primitive

A comment consists of one authoritative string plus structural identity,
selection, provenance, and lifecycle metadata. Tags are derived from hashtags
inside the string. There is no independently editable `tags` field that could
drift from the displayed comment.

```text
Comment {
    id,
    session_id,
    text,
    selection_rule,
    provenance,
    created_at,
    supersedes?,
    archived_at?
}
```

Examples of complete comment strings are:

```text
#approved Implementation and error handling reviewed.

#problem #compiler-error Cannot resolve symbol 'factoryManger'.

#problem #audit-forbidden Use @SFMSubscribeEvent instead of
@EventBusSubscriber.

#performance #"needs investigation" Does this allocate on every tick?
```

Hashtag extraction is a derived computation over `text`. The first syntax to
support is an unquoted tag such as `#approved` plus a quoted form for spaces,
such as `#"needs review"`. Escapes, Unicode normalization, case sensitivity,
word boundaries, literal hashes in code/URLs, and malformed quoted tags require
an explicit small parser and round-trip tests. This tag parser is not SFML and
does not modify the SFML grammar.

An index may cache normalized derived tags for search and rendering, but the
comment string remains authoritative. Changing the text invalidates and
rebuilds the index.

### Provenance is structural metadata

Provenance is not a second kind of comment content. It records who or what
created an otherwise ordinary comment and is required for trust, invalidation,
and explanation:

```text
human:<identity>
javac:<tool/version/invocation>
sfm-audit:<rule/version>
diff-engine:<algorithm/version>
approval-rule:<rule/version>
migration:<source-comment/evaluator-version>
```

Two comments with identical strings but different producers may have different
lifetimes and authority. Producer identity, rule version, source snapshot, and
parent comment ids must therefore remain available without being encoded only
in prose.

### A selection rule returns a set of glyphs

The rule attached to a comment is a persistable expression evaluated against a
review session. Its result is a set of glyph selections, potentially disjoint
and spanning multiple documents. It is not an in-memory Java closure and is not
limited to one contiguous range.

```text
SelectionRule -> Set<DocumentGlyphRange>
```

Initial rule variants should include:

```text
LiteralGlyphRange(document_revision, start, end, witnesses)
LiteralLineRange(document_revision, start_line, end_line, witnesses)
TextMatch(scope, literal_or_regex, occurrence_policy, witnesses)
SyntaxRegion(scope, language, node_query, projection, witnesses)
SymbolQuery(scope, symbol_constraints, projection, witnesses)
DiffRegion(comparison, operation_query, side_or_sides, projection)
Union(rules...)
Intersection(rules...)
Difference(include, exclude...)
```

The set algebra permits one comment to cover several version-specific methods,
the two sides of a modification, or an entire declaration excluding generated
regions. Cardinality expectations are explicit. A rule that expects exactly
one method and finds zero or two reports failure or ambiguity rather than
silently selecting an arbitrary match.

### Documents and glyph identity

A document revision is identified by repository/session lane, immutable
snapshot identity, normalized repository path, content hash, encoding, and
language. A glyph range uses offsets defined against the exact stored bytes or
decoded text model and records enough conversion metadata to avoid silently
mixing UTF-16 Java indices, Unicode scalar values, UTF-8 bytes, and rendered
glyph cells.

The first implementation must name which coordinate system is canonical and
provide checked conversions for editor/rendering coordinates. Line/column is a
presentation, not a durable identity by itself.

## Global review sessions

### Session ownership and persistence

Comments live in a global review session rather than beside each source file.
The application-data area is the default persistence location. A user can start
an empty session, reopen one, clone it, export it, or archive it. A session may
reference multiple repositories, snapshot pairs, and Minecraft versions.

```text
ReviewSession {
    id,
    title,
    schema_version,
    revision_lanes,
    comments,
    style_rules,
    completion_policy,
    evaluation_revision,
    created_at,
    updated_at
}
```

Persistence is deterministic and atomic. Unknown future fields are preserved
where practical. Corruption must fail closed and retain the last valid session.
Sessions can be exported as ordinary JSON even if an optimized index or binary
cache exists. Source text may be embedded by snapshot reference or supplied by
the canonical snapshot interchange, but comments must never depend on ambient
unversioned paths without recording their resolved identities.

### Revision lanes and the release surface

A release session may contain one before/after lane for every Minecraft branch:

```text
Release 4.35 -> 4.36
  1.19.2: previous release tag -> proposed release commit
  1.19.4: previous release tag -> proposed propagated commit
  1.20:   previous release tag -> proposed propagated commit
  ...
  1.21.1: previous release tag -> proposed propagated commit
```

The selectable review surface is the union of before and after documents across
all lanes. A single comment can select glyphs in several versions or several
repositories. This supports explanations such as an API changing in 1.20 and
the corresponding logic moving behind an `@MCVersionDependentBehaviour`
adapter.

Before documents are first-class. Removed methods and deleted files remain
selectable, so a human can write `#approved Deletion is intentional` or
`#needs-change Why was this compatibility check removed?`. An after-only model
could incorrectly approve an empty result while ignoring a dangerous deletion.

## Computationally responsible rule evaluation

Every selection rule carries scope hints describing where it expects to match:

```text
SelectionScope {
    repositories?,
    revision_lanes?,
    snapshot_sides?,
    path_exact_or_globs?,
    languages?,
    syntax_kinds?,
    symbol_hints?,
    content_tokens_or_hashes?
}
```

Scope is part of the execution plan and migration evidence, not casual
documentation. Evaluation narrows candidates through indexes for repository,
version, path, language, syntax kind, symbol, token, and content hash before
examining source. A repository-wide `rg`-like scan is a valid deliberate
fallback for broad textual rules, not the normal evaluation strategy.

If an expected path disappears or moves, the evaluator reports that fact. It
may use symbol/content witnesses to suggest a relocated target, but it must not
pretend the original scope still resolved exactly.

Evaluation is pure against an immutable session input. The same session,
snapshots, rule versions, and indexes must produce byte-identical normalized
results and diagnostics.

## Creating durable selectors from direct manipulation

Mouse/keyboard selection always creates a literal-range candidate. Language
analysis may additionally suggest structural interpretations of that selection:

```text
Annotate literal glyph selection
Annotate entire method declaration
Annotate method signature
Annotate return type
Annotate method body
Annotate all corresponding declarations across version lanes
```

For example, selecting `void` in `doThing(String)` may suggest:

```text
SyntaxRegion(
    language = java,
    node = method named doThing accepting String and returning void,
    projection = return_type
)
```

The rule also records redundant witnesses: original name, parameter and return
types, owner, body/source hashes, body length, original path/range, surrounding
tokens, and nearby declaration relationships. Redundancy helps distinguish a
renamed method from a newly introduced method with the same signature.

The user chooses whether to keep the literal selector or adopt/edit a suggested
structural selector. Suggestions never silently upgrade the meaning of a
selection.

## Comment evaluation and migration

Comments remain historical objects when a session advances from snapshot B to
snapshot C. Their selection rules are reevaluated; old records are not mutated
as if they always targeted C. A migration creates explicit lineage and one or
more evaluation results:

```text
RESOLVED_EXACTLY
RESOLVED_WITH_RELOCATION
RESOLVED_BY_TRANSFORMATION
AMBIGUOUS
NO_MATCH
INVALID_RULE
SCOPE_MISSING
CONTENT_CHANGED
```

Examples include:

- a method moved with identical content: relocated;
- a file path changed but a unique symbol and content witness agree: suggested
  relocation;
- two new methods satisfy the query: ambiguous;
- `LiteralGlyphRange(1000..1200)` against an 800-glyph document: invalid;
- a selected method was deleted: no match on the after side while its historical
  before selection remains inspectable;
- a trusted Forge-to-NeoForge rule explains a changed import: transformed;
- a heuristic match has similar context but changed content: content changed,
  not exact.

Ambiguous, invalid, missing, and changed evaluations form a migration work
queue. The UI allows retarget, rule edit, archive, discard, or explicit human
confirmation. Effective approval never transfers through an ambiguous match.

## Human and derived approval

`#approved` is a derived tag in comment text, not a boolean column. Absence of
effective approval normally means unseen or not yet accepted; users may add
arbitrary explanatory tags such as `#needs-change`, `#question`, or
`#performance`.

Approval migration defaults conservatively:

- exact unchanged content at a new location may retain effective approval;
- heuristic relocation with changed content carries the historical comment but
  suspends effective approval;
- ambiguous or missing targets never receive effective approval;
- a trusted transformation rule may produce a new derived approval comment;
  and
- direct human and derived-rule approval remain distinguishable through
  provenance and optional conventional tags such as `#derived-approval`.

A derived approval records the source human comment, before/after selections
and hashes, rule id/version, exact transformation evidence, and parent lineage.
For example, an approved Forge import may become an approved NeoForge import
only when a configured version-aware rule proves the permitted transformation.
If that rule later stops matching, the derived approval becomes ineffective or
unresolved rather than turning into unexplained human approval.

## Compiler, audit, and rules-engine integration

Compiler and audit results are ordinary produced comments:

```text
#problem #compiler-error Cannot resolve symbol 'factoryManger'.

#problem #audit-forbidden Use @SFMSubscribeEvent instead of
@EventBusSubscriber.
```

Their selection rules target the affected glyphs; provenance identifies the
compiler invocation or audit rule. F2/next-problem is a query over effective
comments whose derived tags contain `#problem`, ordered by session lane,
document, and first selected glyph.

The existing audit model for forbidden APIs outside permitted wrappers should
become a comment producer. A rule resembles: find every use of a forbidden
symbol, subtract uses inside approved wrapper scopes, and produce a problem
comment over the remainder. This shares the same set-oriented selection model
as human comments and derived approval.

Generated comments have explicit refresh/reconciliation rules. Rerunning javac
or audit replaces or supersedes results from the same producer invocation/rule
identity without deleting unrelated human comments.

## Diff production through comments

The comparison engine produces comments whose strings contain conventional
tags such as `#added`, `#removed`, `#modified`, and `#renamed`. Their rules
select before glyphs, after glyphs, or corresponding sets on both sides.

- `#added` normally selects after-document glyphs;
- `#removed` selects before-document glyphs;
- a modification comment may select related before and after spans;
- a rename comment may cover the old declaration, new declaration, and updated
  usages; and
- ambiguous correspondence produces a comment stating that uncertainty rather
  than inventing a match.

Traditional red/green diff presentation is therefore produced by configurable
comment-style rules rather than hard-coded comparison colors. The structured
comparison artifact still carries operations/correspondences as evidence and
an efficient source for generated comments; comments are the common review and
presentation surface, not an excuse to discard comparison structure.

## Comment-driven colourization

Each rendered glyph may belong to zero or many comment selections. A
user-editable ordered style-rule list maps comment/tag predicates to independent
presentation channels:

```text
CommentStyleRule {
    id,
    comment_query,
    priority,
    foreground?,
    background?,
    underline?,
    border?,
    gutter_marker?,
    overview_marker?,
    enabled
}
```

Illustrative defaults are:

```text
#problem       -> red foreground or underline plus red gutter marker
#needs-change  -> orange underline
#approved      -> green background tint
#added         -> green diff background
#removed       -> red diff background
```

Overlapping rules need not choose one total winner. Priority is resolved per
channel, so a glyph may keep an approval background, a problem underline, and
several hoverable comments simultaneously. Hover/details UI lists every
applicable comment and its provenance. Accessibility cannot rely on color alone.

The existing Theme Settings service and colour picker should edit these rules.
Theme snapshots contain named default styles; session/project settings may
override ordering and mappings without changing comment semantics.

Rendering must not evaluate every rule against every comment for every glyph on
every frame. Rule evaluation produces normalized document intervals. A sweep
line or interval-tree index divides a document into runs with stable applicable
comment/style sets; only invalidated rules/documents are recomputed.

## Completion policies

The engine does not hard-code whitespace or trivia as unreviewable. Selection
rules can deliberately target trailing whitespace across all files and produce
`#problem #should-delete`, including whitespace after the first final newline.

A session chooses its coverage domain and blockers. Two useful modes are:

```text
Release-change review:
  every changed before/after glyph or operation must have effective #approved
  coverage, with no unresolved blocking comments.

Codebase certification:
  every configured glyph/token/node in every after snapshot must have effective
  #approved coverage, with no unresolved blocking comments.
```

Policies define conventional blocking queries, for example tags containing
`#problem` or `#needs-change`. Arbitrary tags are informational unless the
policy says otherwise. Coverage reports show uncovered regions, blocking
comments, suspended approvals, ambiguous/missing rules, and producer freshness.

Clicking a count creates a jump list. The tool reports evidence and coverage;
it does not silently stage, commit, merge, or declare a release approved.

## User experience walkthrough

1. Start or reopen a global release-review session.
2. Add one before/after revision lane for each supported Minecraft branch.
3. Let the comparison producer create `#added`, `#removed`, `#modified`, and
   `#renamed` comments over the multi-version surface.
4. Browse changed files and code in the multiplexer. Diff colors come from
   comment-style rules.
5. Select an after method, a removed before method, or disjoint corresponding
   regions across versions.
6. Accept the literal range or choose a suggested Java structural selector.
7. Write one ordinary comment such as `#approved Adapter behavior reviewed` or
   `#needs-change Why was this guard removed?`.
8. Hover overlapping glyphs to inspect all human, compiler, audit, diff, and
   derived-rule comments.
9. Press F2 to traverse comments matching `#problem`.
10. Advance a worktree to a newer snapshot and open the migration queue.
11. Accept exact relocations, inspect transformed approvals, and manually
    resolve ambiguous, invalid, or missing selectors.
12. Open the completion report and jump through uncovered or blocking regions.

### Concrete pre-announcement walkthrough

Use the already completed integration wave as the first realistic dogfood pair:

```text
Repository  D:\Repos\Minecraft\SFM
Before      9860e924b
After       5597592f5
```

The command-palette action **Review: Open change set** opens a typed form whose
repository, before, and after values may resolve to immutable Git revisions or
prepared directory snapshots. A read-only Rust/CLI producer may prepare the
canonical snapshot/comparison JSON on disk; Minecraft consumes that file and
does not need Vox or a `.diff`/`.patch` as its central artifact.

After preparation, the multiplexer opens a changed-file tree beside before and
after source panels plus a compact session/coverage strip. File rows use themed
ItemStacks and textual change identities for added, removed, modified, renamed,
and ambiguous files. Search and filters use the existing SFML matcher through a
review-domain adapter. Proposed commands include next/previous change,
next unresolved comment, next `#problem`, annotate selection, open comment
details, open migration queue, and show completion report; all remain available
to the dynamic key-binding system rather than being hard-coded shortcuts.

Selecting `SFMThemeSettingsPanel.java`, `SFMCommandDraftScreen.java`, or
`SFMReviewLedger.java` displays syntax-highlighted source as source. The
comparison producer's `#added`/`#removed`/`#modified` comments create the normal
diff colours. Selecting a complete method creates a literal rule and offers
structural projections such as declaration, signature, return type, or body.
The reviewer might write:

```text
#approved Implementation and error handling reviewed.
```

and then overlap one expression with:

```text
#performance #"needs investigation" Does this allocate on every tick?
```

An audit-produced `#problem #audit-forbidden` comment can overlap the same
approved source; its underline/gutter marker remains visible and its provenance
explains the rule. Human approval does not suppress policy evidence. Likewise,
a removed before-side method remains selectable and may receive
`#approved Deletion is intentional` or `#needs-change Why was this removed?`.

Closing and reopening Minecraft reloads the same global session. Comments and
their historical selections remain associated with immutable snapshots, while
indexes and current projections may be rebuilt. Advancing the after lane to a
new worktree snapshot opens a migration summary: exact selections carry,
relocations are explained, transformed approval cites its trusted rule, and
ambiguous/missing/changed selections become a work queue rather than silently
moving approval.

The completion screen reports coverage and queries, not fixed boolean totals:

```text
Release 4.35 -> 4.36

Changed-surface approval coverage  94.2%
Uncovered selections               3 methods, 14 statements
Blocking comments                  2 #problem, 1 #needs-change
Suspended approval                 2 content-changed, 1 ambiguous
Missing/stale producer results     0

NOT READY FOR FINAL HUMAN APPROVAL
```

Clicking a count opens its jump list. The review system never stages, commits,
merges, or equates committed code with reviewed code. Git state, human comment
coverage, human approval, compiler/audit evidence, and release authorization
remain separable even though their presentation shares one comment engine.

### First parallel development decomposition

Once Phase 0 and the Phase 1 interchange contract are fixed, implementation can
be split without making three competing models:

1. **Snapshot/comparison producer:** accept before/after revisions or prepared
   directories; emit canonical immutable snapshots, hashes, file operations,
   line-range fallback, and deterministic generated-comment projection.
2. **Review workspace and navigation:** load the interchange, render the changed
   file tree and source panels, support search/wrapping/synchronized navigation,
   previous/next operation/comment, literal comment creation, persistence, and
   session reopen.
3. **Structural selection and migration:** add Java correspondence, suggested
   syntax/symbol selectors, witnesses, disjoint/cross-document rules, span-level
   migration results, and conservative approval effectiveness.

The coordinator owns the shared schema and canonical plans. Feature agents work
from one reviewed `1.19.2` baseline, do not update canonical plan copies, and
return tests plus captioned puppets before an integration goal merges anything.

### Historical real-repository bundle wave — 2026-07-22 (superseded)

The shared handoff was frozen in the now-superseded
`repository-review-bundle-v1` contract, recoverable from Git history after
P-1.2 removes the live artifact.
All tracks branched from the same contract commit. The coordinator alone
updated these canonical plans, merged accepted work, installed the changed
Rust CLI into `PATH`, and captured the final merged proof. Feature tracks did
not edit `.g4` files or propagate to later Minecraft versions.

1. **Repository bundle producer** — read a Git revision pair or prepared
   directories, built complete byte-accurate snapshots, calculated
   deterministic file/text changes, validated limits, and atomically wrote a
   named bundle into the managed inbox. Facet was the Rust serialization
   authority for that experiment.
2. **Bundle loader and session lifecycle** — validated the fixture in Java,
   enumerated the managed inbox, exposed a typed command-palette open action,
   imported generated comments with provenance, and deterministically reopened
   the persisted session.
3. **Real review workspace** — replaced the frozen comparison fixture with a
   responsive changed-file tree and before/after source panels backed by the
   loader, created literal review comments through the existing kernel, and
   proved the interaction at 1200x720.

The historical proof pair was SFM `d07bef66c` to `8e9946d9f`. Acceptance at
that time required the Rust command to prepare the pair, the game to open it
through the command palette, a reviewer to browse a changed file and add a
comment, and close/reopen to restore the same session and comment. Those gates
are evidence about the deleted experiment, not current implementation work.

#### Real-repository wave result — 2026-07-22

The baseline real-repository loop is implemented and integrated. Contract
commits `078a93b2c` and `68c759a32` froze the handoff; Rust producer merges
`e5e28dcd3` and `2496ae909` added `review prepare`; Java loader merges
`2b999a727` and `ea024b98d` added strict inbox/session loading; workspace merge
`541752d7c` added the changed-file browser, before/after panels, explicit
UTF-8 line selection, literal comments, command-palette opening, and reopen.
Commit `eca044fa2` corrected the final evidence to select a nonempty glyph span
and reserve source-panel space for comments.

The real `d07bef66c` to `8e9946d9f` bundle has id
`sha256:704c12c4894ecb227c0b604934fc66ab8ba62182467b69d8517f36ac5fd0e36d`.
It contained 1,983 files in each snapshot, 34,483,411 and 34,485,631 bytes, two
changed paths, and two deterministic operations. The merged 1200x720 puppet
opened that managed-inbox bundle, browsed and searched its changed Java files,
selected after-side UTF-8 bytes `[0,51)`, created one human `#question` comment,
closed, and restored the same one-comment session. This proved the literal
selection/persistence baseline; it does not claim structural selector
migration, AST correspondence, approval completion policy, or multi-version
release coverage, which remain Phases 4, 5, 7, and 8.

### Completed implementation wave — 2026-07-22

All three tracks fork from contract commit `378719839` and return to the
canonical `1.19.2` branch through coordinator-reviewed merges. No track may
change `.g4` files or propagate to later Minecraft versions.

1. **Literal-safe globs** — `feat/1.19.2/literal-safe-globs` owns Phase 0,
   including the shared conversion helper, regression tests, changelog, and an
   observable matcher puppet.
2. **Review comment kernel** — `feat/1.19.2/review-comment-kernel` owns the Java
   and Rust v1 models, fixture conformance, selector evaluation, persistence,
   and legacy projection. The coordinator alone may install a changed Rust CLI
   into `PATH`; the track uses its local build until that epoch is accepted.
3. **Review comment UI** — `feat/1.19.2/review-comment-ui` owns the in-game
   comment, overlap, colorization, navigation, migration, and legacy-projection
   presentation plus 1200x720 puppet evidence. It consumes the frozen contract
   through a narrow interface so the kernel can merge first.

The integration gate is: clean track commits, focused and full tests, inspected
puppet evidence, kernel-before-UI merge order, canonical compile and full test,
then freshly recaptured and inspected puppets from the merged head.

**Completion notes:** Contract `378719839` was followed by the literal-safe
matcher `fc68a7846`, Java/Rust kernel `3eda7e550`, comment workspace
`3261e4c0b` plus interaction correction `116bbb57c`, and canonical join
`d07bef66c`. The Java and Rust codecs agree on canonical fixture SHA-256
`a560e879a25c2d84f41ad1b322184921fe9241c46b0b6fe4f63387f4e9af2985`.
The kernel owns hashtag parsing, UTF-8 validation, literal/set evaluation,
atomic persistence, last-valid recovery, and legacy projection. The UI adapter
creates hashed literal or union rules and persists comment/style mutations
through that kernel rather than maintaining a second authority.

The merged Java compile and full suite pass, with only the two expected Windows
symlink assumption aborts. The merged Rust gate passes 324 tests with one
ignored test, and `sfm-propagate-changes.exe` was reinstalled from the final
canonical head after integration. Captioned 1200x720 evidence covers literal-dot matching and a
14-frame comment walkthrough: before/after, disjoint selection, create/edit,
overlap navigation, style precedence, colour input, visibly applied source
colour, F2, migration states, and legacy projection.

The final source audit found and corrected ten direct vanilla font calls in the
matcher diagnostic puppet. Its accepted rerun has no audit-rule violations;
the audit still reports 79 unresolved static-analysis warnings in the two
pre-existing font-rule groups, which remain warning-only rather than approved
exceptions.

No `.g4` file changed. The current grammar still does not lex a dotted
unquoted identifier such as `*.java`; that example is supported by the shared
literal-glob helper for review/filter consumers. Grammar-reachable resource and
tag wildcards already use the helper, while quoted explicit regexes retain
their prior meaning. Any grammar expansion remains a separate review decision.

## Implementation phases

### [x] Phase 0 — Correct literal wildcard conversion

- Introduce one tested glob-to-regex helper for unquoted resource/tag AST input.
- Escape periods and all other regex metacharacters in literal fragments.
- Preserve complete `**` tag component behavior and quoted explicit regexes.
- Add parser/AST, matcher, and ItemStack-picker regression tests.
- Update the gameplay changelog because SFML matching behavior changes.

### [x] Phase 1 — Define comment/session interchange

The frozen v1 wire contract and its cross-language conformance fixture live in
[`../architecture/review-comment-session-v1.md`](../architecture/review-comment-session-v1.md)
and
[`../architecture/fixtures/review-comment-session-v1.json`](../architecture/fixtures/review-comment-session-v1.json).
Both Java and Rust now consume and deterministically round-trip the fixture.

- Define versioned Java and Rust models for sessions, comments, provenance,
  document revisions, scopes, selectors, witnesses, style rules, policies, and
  evaluation results.
- Choose canonical glyph coordinates and checked conversions.
- Specify deterministic ordering, canonical JSON, atomic persistence, last-valid
  recovery, export/import, and unknown-field policy.
- Add a legacy-ledger-to-comments adapter.

### [x] Phase 2 — Implement literal comments and derived tags

- Implement literal single/disjoint range selectors across before/after docs.
- Implement the independent comment hashtag parser and derived tag index.
- Add comment creation/edit/archive UI, overlap hover/details, and persistence.
- Prove two comments overlap the same glyph and retain independent text,
  provenance, and style.

### [ ] Phase 3 — Implement selection evaluation and indexes

- Add scope planning and indexes for repository, lane, path, language, token,
  syntax kind, symbol, and content hash.
- Implement text, syntax-region, symbol, diff-region, and set-composition rules.
- Emit exact/relocated/ambiguous/missing/invalid/content-changed diagnostics.
- Keep evaluation deterministic and incrementally invalidatable.

### [ ] Phase 4 — Add selector suggestions and migration

- Suggest Java declaration/body/signature/return-type selectors from literal
  editor selections.
- Persist redundant witnesses and explain why a suggestion matches.
- Reevaluate B-targeted comments against C without mutating history.
- Build a migration queue with retarget/edit/archive/discard/confirm actions.
- Suspend approval under changed, ambiguous, or missing resolution.

### [ ] Phase 5 — Unify diff, compiler, and audit producers

- Project comparison operations into generated comments.
- Project javac diagnostics into comments with invocation provenance.
- Project SFM audit findings and permitted-wrapper subtraction into comments.
- Reconcile repeated producer runs by producer/rule identity and generation.
- Drive F2 and other jump lists through comment queries.

### [ ] Phase 6 — Comment colourization and customization

- Implement interval normalization and per-channel style precedence.
- Ship accessible default rules for approval, problems, needs-change, added,
  removed, modified, and ambiguous states.
- Integrate the rule editor with Theme Settings and the colour picker.
- Add hover/details and non-color indicators for overlapping comments.

### [ ] Phase 6a — Bind graphical markups to pinned review selections

- Reuse the Text Editor v3/Draw primitive schema through an explicit adapter;
  do not create comment-private rectangle/freehand implementations.
- Add versioned multi-document review-layout projections with exact document
  revision, origin/transform, glyph-layout fingerprint, and bounds evidence.
- Resolve rectangle, polygon, arrow endpoint, freehand, and text-callout gestures
  into literal/semantic selector candidates through the spatial-region system.
- Persist accepted comment selection/evaluation and markup projection together
  while keeping source identity independent from canvas placement.
- Support historical-layout display and explicit witnessed migration when a
  review canvas is rearranged. Ambiguous/missing projection migration leaves
  the old markup inspectable and requests a decision.
- Add action/command-palette creation, edit, style, hide/show, delete/archive,
  and jump-to-comment operations with keyboard and puppet parity.

**Validation:** Pure fixtures lay two document revisions at independent origins,
draw one rectangle spanning both, resolve disjoint glyph ranges, move/reflow the
documents, and prove source selection remains pinned while markup either
transforms through an explicit witness or remains on the historical projection.
A live puppet adds a rectangle, arrow, freehand mark, and text callout to a
review comment and reopens the session with identical source targets and visual
primitives.

**Completion criteria:** Graphical review markup is expressive and persistent,
but no comment or approval depends solely on transient pixels or current canvas
placement.

### [x] Phase 6b — Comment on scrubbed candidate trajectories

- Implement `CandidateTrajectoryTarget` against snapshot-plan 2.12/2.13 without
  duplicating trajectory, frame, or projected-state storage in the comment
  subsystem.
- Support comments on whole route, action/evaluation/transition, candidate
  state, and pinned source/glyph regions in a materialized candidate document.
- Label and filter candidate comments in the timeline, editor, explorer, and
  comment explorer. Navigation returns to the exact plan revision/route/step;
  it never follows whichever plan is currently selected.
- Preserve candidate comments after replan, invalidation, cancellation, and
  budget exhaustion. Allow route/action comments on unavailable frames while
  refusing fabricated region witnesses.
- Add explicit exact-execution promotion/link with candidate provenance and
  conservative witnessed migration for changed/ambiguous results. Never carry
  human approval implicitly.

**Validation:** A fixture comments on a candidate glyph and a route cost,
scrubs away/back, replans, and round-trips persistence. Exact execution offers
and performs explicit promotion while retaining provenance. A divergent
execution and ambiguous correspondence preserve the candidate comments,
suspend migration, and transfer no approval. Commenting/scrubbing leaves actual
history head and instruction pointer unchanged.

**Completion criteria:** Proposed futures can participate in the ordinary
comment workflow without becoming committed facts or moving targets.

**Completion evidence (2026-08-21):** Implemented
`sfm.review-comment-session/2`, its deterministic persisted codec/store and v1
migration, typed candidate route/action/state/glyph targets, ordinary review
data-source/explorer projection, exact navigation, and explicit promotion and
witnessed-migration operations. Replanning retains old-plan comments;
unavailable candidate frames accept route/action comments but reject glyph
targets; exact execution may create an additive committed target with candidate
provenance; divergence creates none; and neither promotion nor migration grants
human approval. Focused tests and the complete Java suite pass, and natural
puppet run `title_screen_can-20260821-085216-878` round-trips and reopens all
seven exercised targets while reporting zero effective approvals. No dependency
or lockfile changed, and no CLI reinstall is required.

### [ ] Phase 7 — Multi-version release review and derived approval

- Build release sessions whose default lane set spans every maintained
  Minecraft-version worktree known to the SFM toolchain, ordered oldest to
  newest, with an optional filter for narrower reviews.
- Resolve independent before/after selectors per lane. The initial typed forms
  are `mod <version>` (the per-lane `<version>-<minecraft-version>` tag) and
  `git head` (that lane's current HEAD).
- Treat each lane's `previous_release..HEAD` comparison as a required human
  code-review surface. Record lane, snapshot/source hashes, parser/index/
  classpath/configuration fingerprints, review-unit identities, diagnostics,
  equivalence proofs, accepted morphism witnesses, and explicit maintainer
  approval separately from Git state and automated test results.
- Use changed function/method definitions as primary units when safely
  identifiable, with declaration/signature, annotations, imports/static
  imports, inheritance, referenced symbols, callers/method references, and
  resolved environment in the selectable review surface. Fall back to type,
  field, import, file, or diff-operation units when required.
- Deduplicate across lanes only with evidence: an identical unannotated body is
  merely a candidate until dependency/import/inheritance/mapping/annotation/
  configuration/feature/classpath closure is equivalent. Re-open review for a
  changed closure, and treat `@MCVersionDependentBehaviour` as an explicit
  version seam that blocks silent shared approval.
- Model accepted morphisms as versioned, scoped, tested rules with witnesses;
  initially this may include a Forge-to-NeoForge import/package adaptation.
  Keep the transformed source and affected resolution closure visible. Unknown,
  ambiguous, content-changed, dependency-changed, or out-of-scope cases remain
  separate or suspend approval rather than being guessed equivalent.
- Derive selectors after comparison: field renames select the definition and
  old/new usages, signature changes select callers and method references, and
  import changes select the affected type-resolution closure. Project these
  into ordinary comments with provenance and tags such as `#added`, `#removed`,
  `#modified`, `#renamed`, `#problem`, and `#approved`.
- Project one unified changes explorer as
  `file → lane/branch → before|after`; two lanes with both sides produce four
  leaves. Always retain both side leaves; use an explicit tombstone when an
  added/deleted file is absent from one revision. Keep unresolved lanes visible
  with diagnostics rather than silently omitting them.
- Project comments as `comment → file → region` and derived hashtags as
  `hashtag → file → region`, retaining comment identity, provenance, selector
  status, side, lane, and immutable document revision at every leaf.
- Add cross-document/version selectors and moved-file diagnostics.
- Implement a first trusted version-aware transformation rule, such as a
  reviewed Forge-to-NeoForge import adaptation.
- Record source approval, rule version, before/after evidence, and derived
  comment lineage.
- Prove that a changed or removed rule invalidates its derived approval.

### [ ] Phase 8 — Completion policies and real-repository puppet

- Implement change-review and whole-after certification coverage modes.
- Make blockers and informational tags configurable queries.
- Add uncovered/problem/suspended/ambiguous jump lists and summary counts.
- Export a human-readable HTML/equivalent structured review artifact and expose
  the same lane → unit → before/after span → dependency/usage → proof drill-down
  in the in-game explorer. The CLI must provide this workflow to agents without
  an IDE plugin and must support preview-first, hash-checked symbol operations
  such as renaming a field and its statically resolved usages.
- Require explicit maintainer approval of the generated code-review artifact
  before release authorization; passing tests, audit, or propagation does not
  substitute for code review. Keep prospective release-JAR Prism experiential
  review as a separate release gate.
- Puppet a real SFM release pair across at least two version lanes, including an
  added after region, removed before method, overlapping human/audit comments,
  four-leaf changes tree, comment/hashtag explorers, explorer-owned stacked
  previews, F2 navigation, session reopen, snapshot advance, and migration
  failure.

## Proposed structural selector and migration wave — 2026-07-23

This is the next proposed Track 6 wave. It covers Phase 3 and the first complete
vertical slice of Phase 4. It does not begin Phase 5 producer unification,
multi-version derived approval, release completion policy, Vox, or general
refactoring execution. No worktree or subagent is created until the maintainer
accepts these briefs.

### Coordinator-owned contract freeze before dispatch

The canonical coordinator first commits a narrow, versioned handoff in
`docs/architecture/review-selection-evaluation-v1.md` plus deterministic JSON
fixtures. It defines the exact fields and normalization laws for the already
reserved `text_match`, `syntax_region`, `symbol_query`, and `diff_region` rule
kinds; scope hints and indexes; redundant witnesses; selector proposals;
evaluation diagnostics; and migration reports. The existing
`sfm.review-session/1` comment text and UTF-8 byte coordinates remain the
authority. The new contract must not add a second tag, approval, or comment
model.

The first implementation uses a prepared structural sidecar produced by the
Rust CLI and consumed by Java. This deliberately avoids requiring the unfinished
Vox bridge for the first in-game proof. Rust/Arborium performs bounded Java
syntax correspondence and emits deterministic proposals/evaluations; Minecraft
loads the frozen result through an injectable provider. The UI may select among
prepared candidates for an arbitrary literal range, but it must label unavailable
live recomputation honestly. A future Vox integration may implement the same
provider without changing the workspace.

Before agents fork, the coordinator freezes these conceptual interfaces:

```text
SelectionEvaluator.evaluate(session, corpus, invalidationKeys)
    -> CommentEvaluation[]

StructuralSelectorProvider.proposals(documentRevisionId, literalUtf8Range)
    -> SelectorProposal[]

MigrationProvider.migrate(sourceSession, targetSnapshot)
    -> MigrationReport
```

`SelectorProposal` contains the typed rule, human explanation, original literal
witness, structural witnesses, confidence evidence rather than a magic score,
and the exact candidate ranges. `MigrationReport` is derived evidence and never
mutates historical comments. Its per-comment states include exact, relocated,
content-changed, ambiguous, missing, invalid, and scope-missing. Effective
`#approved` is suspended for every state except an unchanged exact or explicitly
accepted relocation.

### Proposed subagent A — Selection contract implementation and indexes

| Field | Proposal |
| --- | --- |
| Branch | `feat/1.19.2/review-selection-evaluator` |
| Worktree | `D:\Repos\Minecraft\SFM\worktrees\1.19.2-review-selection-evaluator` |
| Exclusive ownership | Java and Rust session/evaluation models and codecs; selection-rule normalization; scope planner and indexes; evaluator parity fixtures; no UI or Java parsing |
| Deliverable | Executable text, syntax-region, symbol, diff-region, union, intersection, and difference rules with deterministic exact/relocated/changed/ambiguous/missing/invalid diagnostics and bounded invalidation keys |
| Must not touch | Repository-review panels, command palette, puppet definitions, Arborium correspondence implementation, `.g4` files, later Minecraft branches, canonical plans, or the shared PATH CLI |

Agent A is the only feature agent allowed to edit
`SFMReviewSessionV1`, `SFMReviewSessionV1Codec`,
`SFMReviewSessionV1Kernel`, or Rust `review_session_v1.rs` in this wave. It adds
cross-language conformance fixtures for Unicode boundaries, duplicate ranges,
missing scopes, ambiguous matches, content change, deterministic ordering, and
incremental invalidation. It returns a clean commit, focused/full Java results,
Rust `check-all.ps1`, audit impact, and proposed contract corrections to the
coordinator rather than editing the canonical contract itself.

### Proposed subagent B — Arborium Java correspondence producer

| Field | Proposal |
| --- | --- |
| Branch | `feat/1.19.2/java-selector-correspondence` |
| Worktree | `D:\Repos\Minecraft\SFM\worktrees\1.19.2-java-selector-correspondence` |
| Exclusive ownership | Rust Java-source parsing/correspondence modules, structural-sidecar production, CLI-local tests and fixtures; consumes Agent A's frozen types without changing them |
| Deliverable | Declaration, body, signature, and return-type proposals plus B-to-C correspondence for unchanged rename, renamed-and-modified, moved declaration, overload, ambiguous, parse-gap, and removed-symbol cases |
| Must not touch | Java/Minecraft UI, session codecs/evaluator types, repository bundle v1 semantics except an agreed optional sidecar reference, `.g4` files, later branches, canonical plans, or the shared PATH CLI |

Agent B uses Arborium's Java tree as syntax evidence and does not claim semantic
type inference that the implementation has not proved. It records parse gaps and
all candidate witnesses rather than selecting an arbitrary first match. The
producer must be deterministic under file-order changes, bounded by declared
repository/path scopes, and usable against prepared directories as well as a
Git revision pair. A focused CLI command or internal producer entry point is
chosen by the frozen contract; the agent must not invent a competing review
session format.

### Proposed subagent C — Selector choice and migration workspace

| Field | Proposal |
| --- | --- |
| Branch | `feat/1.19.2/review-migration-ui` |
| Worktree | `D:\Repos\Minecraft\SFM\worktrees\1.19.2-review-migration-ui` |
| Exclusive ownership | Java provider boundary, shared review-workspace model integration, selector-choice panel, migration queue/details panels, commands/actions, focused UI tests, and captioned puppet |
| Deliverable | Literal-versus-structural selector choice with explanations; exact/relocated/changed/ambiguous/missing/invalid presentation; retarget, confirm, edit, archive, and discard flows; persisted resolution and suspended-approval display |
| Must not touch | Rust CLI, session wire types/codecs/evaluator internals, Java parser dependencies, `.g4` files, later branches, canonical plans, or the shared PATH CLI |

Agent C begins against an injectable deterministic fixture implementing the
frozen provider interfaces, so it can proceed in parallel without guessing
Agent A or B internals. Integration replaces the fixture provider with the
accepted structural sidecar adapter. The migration queue is a real composable
panel group using the existing shared workspace model and Stack behavior; it
must not reintroduce application-local manual splitting.

### Required observable story and puppet evidence

The merged puppet uses one three-snapshot Java fixture and keeps one selected
method throughout the walkthrough:

1. select a literal after-side return type and open selector choices;
2. compare the conservative UTF-8 selector with an explained Java return-type
   proposal and explicitly choose the structural rule;
3. attach a human `#approved` comment and show its rule, witnesses, provenance,
   and currently effective approval;
4. advance B to C where an unchanged renamed method resolves with relocation;
5. show a renamed-and-modified method as `content_changed` with approval
   suspended;
6. show two equally plausible overloads as `ambiguous`, including both candidate
   ranges and no arbitrary winner;
7. resolve the ambiguity through retarget/confirm, close the workspace, reopen,
   and prove the decision persisted; and
8. show a missing or invalid selector remaining in the queue rather than
   silently disappearing.

Each subject area receives one captioned screenshot at requested
`1280x720@2` (effective GUI scale 2): selector choice, relocated approval,
changed/suspended approval, ambiguous migration queue, resolved persistence,
and missing/invalid diagnostics. Agent puppets may use only this exact fast path.
After integration, the coordinator reruns the combined walkthrough through the
accepted responsive profile and publishes the normal HTML contact sheet.

### Merge order, gates, and stop conditions

The coordinator reviews and merges A, then B, then C. Shared contract or codec
changes flow only through A; shared Rust CLI routing conflicts are resolved when
B merges; C consumes the integrated provider. Agents commit only their owned
worktrees and report exact commands, results, screenshots, assumptions, and
proposed plan wording. They do not update canonical plans, install to PATH,
merge, propagate, or delete old evidence.

Acceptance requires deterministic fixture round trips in Java and Rust,
focused and full tests, Rust `check-all.ps1`, canonical compile/full Java suite,
source audit, unchanged `.g4`, unchanged later-version heads, a clean canonical
worktree, exact final CLI installation, and inspected effective-scale-2 plus
responsive-profile puppet evidence. Stop the wave after selector evaluation,
suggestion, and migration are proven. Compiler/audit comment production,
colour-rule editing, multi-version approval, and release completion remain
separate follow-up goals.

## Acceptance criteria

- No `.g4` modification is required for review filtering or wildcard safety.
- Literal periods in unquoted wildcard identifiers are never regex wildcards.
- Comment text is the single authority for hashtags; indexes are derived.
- One comment can select disjoint glyphs across files, sides, and versions.
- Multiple comments can overlap one glyph without data loss.
- Scope hints prevent routine full-corpus scans and missing scopes are visible.
- Before-side deletions are selectable and reviewable.
- Diff colors, compiler diagnostics, and audit findings use the shared comment
  presentation/query substrate.
- Human approval and rule-derived approval retain distinct provenance.
- Ambiguous, changed, invalid, or missing selectors never silently carry
  effective approval.
- Whitespace remains selectable and completion policy defines its domain.
- Sessions persist atomically outside source files and export deterministically.
- A captioned puppet demonstrates the complete multi-document lifecycle.

## Open questions to resolve with fixtures

- Which canonical glyph coordinate best spans Java, Rust, JSON bytes, and the
  Minecraft editor without lossy conversions?
- What exact hashtag quoting/escaping syntax avoids false tags in URLs, code,
  and Markdown-like text?
- Are selectors stored as a typed JSON IR only, or also rendered through a
  human-editable query language with a lossless round trip?
- How are huge generated/minified/binary documents represented or excluded by
  explicit policy without declaring whitespace globally irrelevant?
- Which style channels compose, and which require priority arbitration?
- What confidence/evidence threshold permits automatic relocation of an
  ordinary comment versus an approval-bearing comment?
- How are comments merged when two session files are edited concurrently?
- Which identities authorize human approval, and how are signed/exported
  sessions verified if review evidence crosses machines?
- How should a comment whose selector currently matches no glyph be displayed:
  as session-level unresolved state, against a virtual rule document, or both?

## Verbatim source notes

Source note B is preserved beside the matcher correction above. The following
appendix preserves the other originating user message verbatim, including
dictation duplication, transcription artifacts, tentative wording, and
examples. The normative sections above organize the design but do not replace
these notes as historical requirements evidence.

### Source note A — Review annotations, approval, and migration

```text
The same SFML-style matcher language used by the ItemStack picker powers the filter.
We should take care to not modify the g4 file for this, your provided samples should probably have been "WITH TAG modified" or "WITH #modified" instead of just  and, but everything, we could annotate that whole thing as hashtag approved and anything with no tags would be like something that we hSSo, when we have a before and after document, really it's the after document that is like the current state of affairs that we want to clearly identify what is approved by a human and anything that is viewed and deemed lacking and therefore does not have approval is something that will need to change. And one possible implementation of this is instead of explicitly stating that there's only two states can either be approved or viewed or not approved and not viewed, like those two yes, no questions, it would be more of a annotation system where for any subset of code, like we can have a disjoint selection that is something, I mean, like it's a tagging system. So if I have a function, then the outer HTML of that function, if we consider that to be like the signature and stuff, so not just the body of it but everything, we could annotate that whole thing as hashtag approved and anything with no tags would be like something that we haven't viewed and we could support ad hoc tagging and say like needs review or whatever and that would let the user write tags to apply comments and some tags would have specific meaning where the review would conclude upon everything being hashtag approved and the general system of we want to mark up this document and identify what pieces I think are good and letting me attach comments to code and saying like this piece needs to change and letting tags overlap. So I might want to have two different comments that intersect the same glyph or word or paragraph in a piece of code and the idea of it being tags is, I guess, a general form of specifying a string comment and then a predicate cross every glyph in the document on whether or not that comment applies to that glyph or not.

SSimilarly, when we run our audit mechanism or the compiler mechanism, those would produce comments like, there's a problem here, a hashtag problem, and the details of that issue are like, there's no, you made a typo in a variable name or something, or you're using an annotation that our audit system identifies as forbidden because there's a different annotation you should be using instead. So it would all map onto this comment system. So that extinction on, if I want to have F2 bring me to the next problem, then we could double annotate and say, like, this piece of code is tagged with hashtag problem, and then a second independent annotation exists that contains the details of that. And we would have the hashtag problem or whatever it being the easy thing that we use for jump lists or whatever if if we have rules engine that says on 1.20.2 or whatever we switch to Neo4j then when the import statement's change, our system should like we could h we deem acceptable where, that kind of thing is automatical WThen if we have rules engine that says on 1.20.2 or whatever we switch to Neo4j then when the import statement's change, our system should like we could have rules that would approve automatically and say like, okay, this change from a Minecraft forage to a Neo4j import, that kind of thing is automatically approved because that's something that we deem acceptable where. something that we deem acceptable where if in the previous version it is a approved line that is a micro4j import, then in the next version that same line with a little bit changed to say Neo4j instead would also be approved. So there's the concept of approving new things based on approved old things plus some rules which is similar to the audit system we already have.
Then if we have rules engine that says on 1.20.2 or whatever we switch to Neo4j then when the import statement's change, our system should like we could have rules that would approve automatically and say like okay, this change from a Minecraft forage to a Neo4j import that kind of thing is automatically approved because that's something that we deem acceptable where if in the previous version it is a approved line that is a micro4j import then in the next version that same line with a little bit changed to say Neo4j instead would also be approved So there's the concept of Approving new things based on approved old things plus some rules Which is similar to the audit system we already havSo,So, in the case of a symbol So then kind of one of the unanswered questions is what happens when we have the before in the after snapshot and then changes are made such that the work tree is ahead of what the after snapshot is and that's we have all our comments associated with the after snapshot, but now we have another newer snapshot we want to have a plan for how to migrate those comments which is kind of like a diffure or whatever, but we would instead of accepting Instead of accepting changes are doing diff between the code it would be the spans the comments and the predicates that determine what content that comment is associated with would be what is updated. So So perhaps the definition of that predicate might just be like character 0 to 10 or it might be a string matching where it says like any text that matches this rejects is considered selected by this comment or it could be that we have something more like the access transformer stuff where we specify a method by providing its name and signature or something like what was mentioned earlier where we'd have some kind of query language that lets us say. Pick the first method that accepts a string and returns a void so that even if the name of that method changes the signature itself is fairly durable as a thing to identify what is selected here. So then from that we would say when the user does select a literal span in the document so if I just left mouse and drag and select some text then the ways to represent the expression on what that comment is associated with. So then from that we would say when the user does select a literal span in the document so if I just left mouse and drag and select some text then the ways to represent the expression on what that comment is associated with the user could pick from either the literal span that they initially selected or they could convert that expression to be something that is like we could suggest and say like you selected the return type on this method would you like to make. Would you like to make the expression that determines what that comment is associated with instead of the literal span which is default when you left mouse just to select stuff. It would suggest that do you want to transform this to say select the return type of the method named do thing and we would encode and duplicate some of the information say like the method name do thing that has all And we would encode and duplicate some of the information say like the method name do thing that has all the heuristics like the signature and the return type and the length of the body or whatever might be needed to help identify that method after the document is changed. Such that the method may have been renamed and maybe another method has been introduced with the same signature so that we would have a bunch of heuristics to say that this comment was originally associated with the span that's kind of located relevant to some other information that we can robustly identify even if it means like persisting that like the body of this method was 10 lines longer whatever and then we can disambiguate with that kind of thing. and then we can disambiguate with that kind of thing. So that's the when we have this kind of later version of the code and then an even newer version is made a change on disk and we're trying to migrate these comments.So then kind of one of the unanswered questions is what happens when we have the before in the after snapshot and then changes are made such that the work tree is ahead of what the after snapshot is and that's we have all our comments associated with the after snapshot, but now we have another newer snapshot we want to have a plan for how to migrate those comments which is kind of like a diffure or whatever, but we would. Instead of accepting changes are doing diff between the code it would be the spans the comments and the predicates that determine what content that comment is associated with would be what is updated. So perhaps the definition of that predicate might just be like character 0 to 10 or it might be a string matching where it says like any text that matches this rejects is considered selected by this comment or it could be that we have something more like the access transformer stuff where we specify a method by providing its name and signature or something like what was mentioned earlier where we'd have some kind of query language that lets us say. Pick the first method that accepts a string and returns a void so that even if the name of that method changes the signature itself is fairly durable as a thing to identify what is selected here. So then from that we would say when the user does select a literal span in the document so if I just left mouse and drag and select some text then the ways to represent the expression on what that comment is associated with the user could pick from either the literal span that they initially selected or they could convert that expression to be something that is like we could suggest and say like you selected the return type on this method. Would you like to make the expression that determines what that comment is associated with instead of the literal span which is default when you left mouse just to select stuff it would suggest that do you want to transform this to say select the return type of the method named do thing. And we would encode and duplicate some of the information say like the method name do thing that has all the heuristics like the signature and the return type and the length of the body or whatever might be needed to help identify that method after the document is changed. Such that the method may have been renamed and maybe another method has been introduced with the same signature so that we would have a bunch of heuristics to say that this comment was originally associated with the span that's kind of located relevant to some other information that we can robustly identify even if it means like. and then we can disambiguate with that kind of thing. So that's the when we have this kind of later version of the code and then and even newer version is made a change on disk and we're trying to migrate these comments. It is the migration of the comments is that the expressions that define what glyphs those comments apply to. can be can be evaluated against any document and. If we perform the evaluation of find that this expert this comment has an expression that is talking about like. It's a literal selection across the glyphs one thousand and one thousand two hundred and the document is only eight hundred glyphs long now then that comment would have an invalid expression so that would be a way to detect. that would be the ones most in need of fixing during the migration of comments from one version of a document to another.
```
