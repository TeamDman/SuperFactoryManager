# Global comment selection and review sessions plan

**Plan status:** Active
**Last updated:** 2026-09-06
**Completed overnight execution:** [Release review overnight readiness](release%20review%20overnight%20readiness%20plan.md)
records the September 6 core and every ordered stretch as verified complete.
Its task-local evidence is authoritative; no overnight item remains newly claimed.

**September 6 user-testing follow-up (verified checkpoint):**
[Explorer find, selection, compact hierarchy, and review freshness](explorer%20find%20selection%20compact%20hierarchy%20and%20review%20freshness%20plan.md)
retains EFR-01–49: shared input editing, non-hiding Find versus Filter,
multi-selection, compact path identity, explicit review freshness, and distinct
live filesystem versus Git-pinned comment targets. The reported puppet file is
untracked and absent from the user's 58ed4e431-pinned review; a newer Git HEAD
alone cannot include it. EF-11/EF-12 refine X-9 and this plan's live/pinned
adapter with immutable capture and explicit migration, never automatic approval
transfer or replacement of the existing portable review. The latest correction
confirms fuzzy matching/ranking work; the next core adds independent shared
Find/Filter options/highlights, pointer multi-selection and commit-before versus
captured-files-on-disk-after reviews. Untracked changes must support ordinary
portable comments that agents can inspect without copy/paste or committing first.
Git targets display their actual revision; `(HEAD)` is only a current equality
decoration. EF-1–6, EF-8, EF-11/12 and EF-A are the core; EF-7/9/10 the ordered
stretch ladder. Existing Git/Git reviews retain immutable meaning. The core and
EF-7/EF-9 are now verified, including captured untracked code, exact comments and
saved queries in a portable review reopened in a separate JVM. EF-10 remains
scheduled. The user's existing review was not rewritten; approval-like fixtures
are synthetic test evidence, not human review. See the linked task-local proof
and `docs/explorer find filter and working tree review guide.md` for use.

**September 6 planning refinement:** [Contextual ItemStack preview rule authoring](contextual%20itemstack%20preview%20rule%20authoring%20plan.md)
retains the ER-S4/X-8e user-theme and contextual command-building slice
as IPR-01–35. The bounded IPR-T0–T5/T3a implementation is verified complete in
the overnight plan, not part of the earlier completed exploratory goal. Review
rows use the generic rule system; flat aspect actions supersede generic Help or
Customize wrappers without changing review identities or approval semantics.
IPR-T3a adds reusable details/prompt clipboard actions: include the captured
entry and actual rule/operator contract, omit ItemStack catalogue enumeration,
and leave sharing and executing the suggested rule to the user. This is neither
an automatic AI review nor a change to stored comment/approval meaning.

**Completed bounded goal:** [Release review exploratory usability](release%20review%20exploratory%20usability%20plan.md)
E-1–E-4 (September 5): four independent JVMs, adaptive disposable review use,
concise captured-path notification actions, real SFM.java text-diff decoding,
saved-comment marker navigation, and filtered expansion reduced from an observed
1.57 s to 32 ms dispatch. Exact notes survived restart; no OS cursor injection,
maintainer review changes or release approval. The supplement records the final
clean CLI run, current Java build, unchanged installed Rust tool and qualified
full-suite failures. ER-1–ER-8 preserve the request; ER-F1–ER-F7 retain newly
observed limits and test-infrastructure follow-ups. The completed overnight core was
ER-F1–ER-F4 (open/save stalls, preview reuse, legible comments, mouse-discoverable
remaining work), not another generic agent framework. Its separate execution
plan above preserves both completed goals and their ordered stretch evidence.

**Completed bounded goal:** [Release review exact coverage acceptance](release%20review%20exact%20coverage%20acceptance%20plan.md),
RCOV-A through RCOV-E (September 5). Java/Rust now require full pinned
before/after coverage, expose exact remaining ranges, and agree on a real
285-byte hunk with one approved byte. Separate JVMs preserved comments and
portable-file identity; final in-game syntax/diff/comment and icon evidence
passed. The supplement records current installed tools, screenshots and known
full-suite/audit warnings. This does not close the broader RCS-UX6 remainder
or grant human release approval.

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
the candidate `HEAD`. One explicit repository-trackable review document records
the lane, baseline/candidate snapshot identities and hashes, parser/index
fingerprints, generated review units, named queries, resume position,
equivalence evidence, unresolved boundaries, and maintainer approval. It must
remain separate from Git's index, commit status, automated test results, and
release authorization, while still being ordinary commit-able evidence whose
history can be reviewed with Git.

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
For the **release-review profile**, the canonical session is an explicit,
portable, user-addressed file rather than hidden application state. A user can
start an empty session at a chosen path, reopen it, clone it, save it as another
path, archive it, inspect its diff, and commit it with the repository. A session
may reference multiple repositories, snapshot pairs, and Minecraft versions.
For SFM itself, the suggested location is
`docs/reviews/<before>-to-<after>.sfm-review.json`; the format and actions do not
hard-code that directory.

Application data may retain a recent-file list, an autosave recovery copy,
indexes, thumbnails, or other rebuildable caches keyed by canonical session
path and content hash. Deleting AppData must not delete review decisions,
change coverage, or prevent the explicit review file from reopening. Existing
machine-local candidate/trajectory comment sessions may continue using their
bounded AppData store, but such a hidden session cannot satisfy a release-review
gate until it is deliberately saved into a portable release-review document.

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

The commit-friendly file is a versioned orchestration envelope containing the
single authoritative v2 comment session, not a second comment database:

```text
ReleaseReviewDocument {
    schema: "sfm.release-review/1",
    review_session: ReviewSessionV2,
    repository_bindings,
    named_queries,
    resume_state,
    completion_attestations
}
```

The embedded `sfm.review-session/2` object remains the sole authority for
comments, targets, hashtags, style rules, and approval semantics. The envelope
owns only release-review orchestration that v2 does not currently model:
portable repository bindings, saved query definitions, deterministic resume
position, and explicit completion attestations. Do not add a parallel comment
list or duplicate review decisions in AppData.

The envelope defines a domain-separated `review_semantic_state_hash` over the
embedded review session, pinned source-corpus bindings, named completion
queries/policy, producer generations, and explicit review decisions. It excludes
the attestation array itself, rebuildable caches, presentation layout, and the
resume cursor. An attestation signs/references that semantic hash rather than
trying to hash a JSON file that contains its own hash. Changing a comment,
selector, corpus binding, completion query/policy, producer generation, or
review decision changes the semantic hash; merely moving the viewport or
writing the attestation does not.

Persistence is deterministic and atomic. Unknown future fields are preserved
where practical. Corruption must fail closed and retain the last valid session.
The canonical file itself is ordinary JSON even if an optimized index or binary
cache exists. Temporary atomic-write files and an AppData recovery copy are not
additional authorities and need not be committed. Every semantically committed
review action autosaves the canonical file; an unsuccessful write leaves a
visible dirty/error state and closing must not silently discard it. A
single-writer lease plus expected-content-hash check detects another process or
Git operation changing the file instead of overwriting it. Source text may be
embedded by snapshot reference or supplied by the canonical snapshot
interchange, but comments must never depend on ambient unversioned paths without
recording their resolved identities.

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

### Queryable progress and deterministic resumption

A review query evaluates to a set of addressed review surfaces. It uses the
same typed selection/set foundations as comments but queries comments,
effective approval, lanes, snapshot sides, review units, evaluation states, and
the completion domain rather than pretending a text filter is sufficient.

The first grammar supports parentheses plus `union`, `intersect`, and
`difference`, with these ergonomic atoms and their explicit canonical forms:

```text
#approved              -> comments-with-tag("#approved")
1.19.2                 -> lane("1.19.2")
HEAD                   -> side("candidate") at that lane's pinned candidate commit
previous_release       -> side("baseline") at that lane's pinned baseline commit
changed                -> completion-domain("release-change")
effective(#approved)   -> effective-approval("#approved")
state(ambiguous)       -> evaluation-state("ambiguous")
```

Therefore the user's compact query must work as written:

```text
#approved intersect 1.19.2 HEAD
```

It returns tagged coverage and displays whether each result is effective or
suspended. Release completion deliberately uses the stricter expression:

```text
effective(#approved) intersect 1.19.2 HEAD
```

`HEAD` never means whatever Git happens to resolve when the query is rerun. It
is syntactic sugar for the immutable candidate source snapshot recorded in the
review file. Ambient Git HEAD is classified as exact, a descendant containing
only explicitly recognized review-evidence-file changes, or source-affecting
divergence. Committing the canonical review file may advance ambient HEAD while
leaving the pinned source corpus valid; any changed path or environment input in
the review domain makes the session visibly stale until the user explicitly
advances/migrates it. The palette/query editor shows the normalized expansion,
pinned source commit/tree/hash, and ambient relationship before execution.

Named queries such as `approved-current`, `remaining-current`, `blocking`, and
`suspended` live in the review file. The canonical remaining-work expression is
the configured changed-surface domain minus effective approval, plus any
blocking/suspended/missing work required by policy. Query results open as an
ordinary explorer/jump list and are available through the Rust CLI in human and
structured output forms.

The review file persists the active named/ad-hoc query, deterministic work-queue
ordering, last visited stable review-unit identity, explicit deferrals, and the
last completion-report witness. Those values are navigation provenance, not
the authority for whether something is reviewed: progress is recomputed from
the current pinned corpus, comments, effective evaluations, blockers, and
policy. Closing halfway through and reopening the file resumes at the first
still-applicable item at or after that cursor; deleted, changed, or newly
uncovered work cannot disappear because an old cursor said it was complete.

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

Completion is an explicit maintainer attestation over the domain-separated
review semantic-state hash, pinned lane revisions, completion-policy/query
revision, producer/index fingerprints, and a zero-uncovered/zero-blocker
report. The program may enable
the attestation action only when policy passes, but no puppet, analyzer, comment
producer, or successful test run invokes it for the maintainer. Changing the
review file, candidate revision, review domain, policy, producer generation, or
effective approval after attestation marks that attestation stale and requires
a new explicit decision. The file may therefore be committed halfway through
as `in_progress` and later as `complete`, with both states inspectable in Git.

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

**Retained-route comparison integration (2026-08-21):** Snapshot-plan S1M-X3
now joins two immutable candidate route addresses to this phase's authoritative
comment session at presentation time. Its side-by-side comparison reports each
route's existing comments without copying, retargeting, deleting, promoting, or
approving them. Preferred/rejected comparison disposition is persisted in the
separate `sfm.route-comparison/1` model and is explicitly not a comment or human
approval. Natural puppet run `title_screen_rou-20260821-094513-349` retains both
route comments across mode changes and persistence reload, reports zero
effective approvals, and changes trajectory selection only through the
separately invoked explicit selection action.

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

## Completed cross-plan goal — review selectors, resumability, and real release domain

The 2026-08-23 whole-plan audit selected this as the lowest-regret active goal
for the stated release objective. Its implementation and pre-freeze real-corpus
proof are now complete. The active persistent goal remains open only for the
immutable two-checkpoint closeout described below; that closeout deliberately
follows the final mutation of this plan so the committed review can pin the
actual implementation checkpoint rather than a moving working tree.

Current foundations supersede parts of the 2026-07-23 proposal below: X-1
through X-7 and X-8a through X-8c are complete; SS-1 through SS-6 are complete;
Java syntax, definitions, usages, dependency sources, worker supervision,
review explorers, panel stacks, literal comments, persistence, and candidate-
trajectory comments exist. The next slice must use
`sfm.review-comment-session/2` and the current typed selection/spatial models;
it must not revive the deleted managed-bundle inbox, stale v1-only assumptions,
or the old proposed worktree names.

### Active-goal completion record and immutable closeout — 2026-08-23

Every RCS implementation item below has its bounded matrix and integration path
implemented. This record distinguishes those completed capabilities from the
final immutable repository-state transaction: after this plan's last mutation,
amend checkpoint A, install that exact CLI, regenerate the complete review
against A, repeat the separate-JVM proof, and commit only that initialized
review as checkpoint B. The persistent goal must not be marked complete until
that mechanical closeout and the final source gates have actually passed.

- **RCS-0/RCS-1:** the versioned Java/Rust contract, canonical fixtures,
  immutable snapshot-corpus adapter, ordered multi-document Unicode pinning,
  reverse projection, stale/live heads, hashes, provenance, whitespace, and
  v1/v2 compatibility all pass without introducing a second comment or approval
  authority.
- **RCS-2/RCS-3/RCS-4:** certified Java structural proposals, conservative
  literal witnesses, bounded evaluator/indexes, every terminal state,
  invalidation/set laws, retained ambiguity, migration decisions, old/new
  witnesses, and persisted edit/archive/discard/defer behavior pass. The real
  journey selected a Java interaction-map `body` proposal through the ordinary
  contextual palette rather than synthesizing a comment behind the UI.
- **RCS-5:** the self-orchestrating natural-input fixture journey passes at
  `1280x720@auto` and `1920x1080@auto`, with inspected PNG and structured
  artifacts for semantic selection, comment creation, relocation, conservative
  suspension, reopen, and cleanup.
- **RCS-6/RCS-7:** one path-addressed, atomically saved
  `sfm.release-review/1` owns all durable truth. Grammar, CLI/game query parity,
  explorer work queues, raw/effective approval, remaining/blocking results,
  stable resume IDs, conflict/recovery/lease behavior, and checkout-like
  portability pass. A fresh second JVM reopened identical review bytes after
  recovery and writer-lease deletion and recovered the exact comment, selector,
  query results, and cursor.
- **RCS-S2:** the producer reconciled the complete real
  `4.34.0-1.19.2..<candidate>` inventory: 1,613 raw changes, 1,767 represented
  before/after paths, 2,917 review units, zero unexplained exclusions, zero
  unsupported units, and deterministic preserving refresh.
- **RCS-8:** Java/Rust completion parity, fail-closed states, non-circular
  semantic hashing, invalidation, exact witnesses/jump lists, attestation gates,
  and presentation-only independence pass. The real review remained
  `in_progress` with no attestation; the staged structural `#needs-change`
  produced four blocking units while all 2,917 units remained in the work queue.
- **RCS-S1 rehearsal:** against implementation checkpoint
  `aa05e41b6fae946370bf9fcfa6b40c8440d6073f`, the real stage and resume
  puppets passed in distinct JVMs. They proved a 30,305,976-byte canonical
  review, exact SHA-256/semantic-state continuity, canonical-file immutability,
  durable Java selector provenance, cache-independent restart, and cleanup.
  Because the canonical document exceeds the puppet channel's one-megabyte
  bound, the full tracked file remains authoritative and each puppet emits a
  bounded path/size/SHA-256/semantic-hash/count/cursor witness instead of a
  lossy or oversized duplicate.

The rehearsal also exposed and closed four integration defects: Brigadier's
terminal greedy path retained quotes verbatim; complete-corpus status evaluation
was accidentally quadratic; puppet action-instance fields did not survive
checkpoint boundaries; and zero-length candidate-side hunks could be chosen for
structural review. Regression tests cover the path and batch/cache semantics,
and the real journeys cover the durable handoff and non-empty target choice.

The CLI was installed fresh for the rehearsal, but this plan mutation makes that
installation intentionally provisional. The final installation occurs only
after checkpoint A is frozen, and its revision/hash must be recorded in the
handoff so the user need not run `install.ps1`. Dependency declarations and all
checked-in lockfiles remain unchanged.

**Immutable closeout order:** amend implementation checkpoint A with this final
bookkeeping; install the CLI; regenerate the one complete canonical review
against A; rerun stage and separate-JVM resume; run focused and full Java/Rust,
compile, audit, formatting, diff, dependency-freeze, and review-status gates;
commit only the untouched initialized `in_progress` review as checkpoint B;
prove the ambient B advance is `review_evidence_only`, the installed binary is
still exactly A, the worktree is clean, and there are exactly two checkpoints.

### [x] RCS-0 Freeze the current adapter/evaluation contract and corpus fixture

**Work:** Reconcile X-9, SS-7, comment Phase 3/4, and the current Java worker
schemas into one versioned contract for pinned document revision/hash, shared
path/selection revision, ordered UTF-8 ranges, spatial/semantic region witness,
selector proposal, evaluation result, invalidation keys, and migration report.
Freeze the `sfm.release-review/1` orchestration envelope and query/progress
boundary while retaining the embedded `sfm.review-session/2` object as the
only comment/approval authority; later tracks must not improvise hidden
AppData-only progress or a second comment list.
Add the minimum authoritative snapshot-frame/revision-to-review-corpus adapter:
it enumerates immutable addressed documents and bytes/hashes from the existing
snapshot/revision owner and supplies them to review evaluation without copying
them into a competing repository bundle or mutable comment store. Use one
three-snapshot Java fixture plus one small multi-document fixture. The adapter
is intentionally bounded; it does not require completion of the entire episode,
replay, or cross-runtime snapshot program.

**Validation:** Cross-language canonical fixtures cover Unicode boundaries,
disjoint ranges, stale selection heads, current-live versus pinned revisions,
hash mismatch, missing/partial frame materialization, unknown fields,
deterministic ordering, exact source-owner provenance, and no approval field
being derived by the adapter. A fixture proves the review corpus references the
same immutable document hashes as its source snapshot rather than silently
serializing an independent copy.

**Completion criteria:** Later lanes consume one current schema and cannot
invent a second comment, selection, migration, or approval authority.

### [x] RCS-1 Complete X-9: adapt editor/explorer selections to pinned comments

**Work:** Project ordered EditorV3 cursor/range selections and shared named
selections into pinned review selectors while retaining primary cursor,
direction, source expression, resolved document identities/hashes, and
selection revision as provenance. Reverse projection is a view, not a moving
live approval target.

**Validation:** Single/multiple cursor, disjoint multi-document, direction,
whitespace, read-only source, live-head advance, stale hash, session round trip,
and existing v1/v2 migration compatibility.

**Completion criteria:** An ordinary selection can become one durable comment
target and reopen identically without coupling the comment to current cursor or
explorer state.

### [x] RCS-2 Complete SS-7: offer semantic/spatial selector candidates

**Work:** Adapt certified Java regions/outlinks and canvas projections into
literal, declaration, signature, body, return-type, symbol, and bounded
multi-region selector proposals. Every proposal retains its exact literal
witness, semantic provider/provenance, confidence evidence, projection
fingerprint, and addressed source snapshot. Rectangle/freehand creation remains
Phase 6a; this slice proves editor selection and semantic region input.

**Validation:** Method/type/field/import/punctuation boundaries, overlapping
regions, zero/one/many proposals, provider failure, stale publication,
ambiguous symbols, and exact witness reproduction. A generic nearest-token
fallback cannot masquerade as structural evidence.

**Completion criteria:** From a selected Java surface, the constrained palette
can explain and offer conservative literal versus semantic targets through the
ordinary comment action.

### [x] RCS-3 Implement the bounded Phase 3 evaluator and indexes

**Work:** Index the fixture/review corpus by lane, path, language, syntax kind,
symbol, content hash, and diff side. Evaluate text, syntax-region, symbol,
prepared diff-region, union, intersection, and difference rules into exact,
relocated, content-changed, ambiguous, missing, invalid, or scope-missing
results with bounded invalidation keys. Keep diagnostic candidates rather than
choosing the first match.

**Validation:** Repeated/file-order-independent evaluation, incremental
invalidation, overload ambiguity, moved declaration, renamed/modified method,
deleted source, parse gap, missing scope, set laws, and bounded work counters.

**Completion criteria:** Every comment in the scoped corpus has a deterministic
terminal evaluation state and every non-exact state remains inspectable.

### [x] RCS-4 Implement the first Phase 4 migration decision surface

**Work:** Reevaluate B-targeted comments against C without rewriting B. Show
selector explanation, old/new witnesses, candidate ranges, and effective-
approval suspension. Add self-contained retarget, confirm witnessed relocation,
edit selector, archive, discard, and defer actions through the constrained
palette; persistence records the human decision and provenance.

**Validation:** Exact unchanged, safe relocation, content changed, equally
specific candidates, missing/invalid, stale UI capture, close/reopen, and the
rule that automated evidence cannot grant or transfer human approval.

**Completion criteria:** A reviewer can resolve or defer each migration case,
and ambiguous/changed/missing comments never silently disappear or remain
effectively approved.

### [x] RCS-5 Prove the natural in-game review journey

**Work:** Open the fixture in the existing explorer/EditorV3, select a method
surface, choose an explained structural selector over its literal witness,
write a comment, close/reopen, advance to the next pinned snapshot, inspect the
migration queue, resolve one relocation, and leave changed/ambiguous/missing
cases suspended. Capture PNG plus structured session, selection, proposal,
evaluation, migration, and action artifacts.

**Validation:** Focused Rust/Java tests, canonical compile/full suite, one
self-orchestrating natural-input puppet at preferred and one non-default
accepted viewport, final tool installation after the last relevant mutation,
and a manual-test handoff that requires no user installer run.

**Completion criteria:** In normal in-game usage—not only a diagnostic panel—a
reviewer can create a semantic comment and watch it survive or conservatively
fail migration across revisions with truthful persisted evidence.

**Implementation progress — 2026-08-23:** The self-orchestrating natural-input
journey now passes at both `1280x720@auto` and `1920x1080@auto`. Its structured
artifacts and visually inspected PNGs prove selection, explained semantic
proposal choice, comment creation, immutable-projection refresh after a
relocation decision, conservative suspension of unresolved cases, close/reopen,
and cleanup. Focused Java release-review/action/model tests and Rust
release-review Git/materialization tests pass. The immutable closeout above owns
the shared post-mutation compile/full-suite and installed-tool freshness gates;
the natural journey itself is complete.

### [x] RCS-6 Make release review one portable, commit-friendly document

**Work:** Implement the `sfm.release-review/1` envelope described above and
path-addressed create/open/save/save-as actions. Use a canonical
`.sfm-review.json` file chosen by the user, with
`docs/reviews/<before>-to-<after>.sfm-review.json` as the SFM repository
convention. Embed exactly one authoritative `sfm.review-session/2`; persist
repository bindings, named queries, resume state, and completion attestations
beside it. Autosave every semantically committed review mutation atomically.
AppData may remember recent paths and hold hash-keyed recovery/index caches but
must not contain required review truth.

**Validation:** Canonical Java/Rust round trip, explicit-path open, save-as,
external-content-hash conflict, failed write/dirty close, crash recovery,
read-only path, unknown field, deletion of every AppData cache, Git diff, and
two process attempts to open the same file writable. A committed-path fixture
reopens after all machine-local state is removed with identical comments,
queries, progress, and completion status. A self-hosting fixture commits only
the review file after pinning its source candidate and proves that the ambient
HEAD advance is classified as review-evidence-only rather than source drift;
changing one reviewed Java byte is classified as source-affecting divergence.

**Completion criteria:** `git status` can show one review file containing all
durable progress, and copying or committing that file is sufficient to resume
the same review on another checkout with the referenced revisions available.

### [x] RCS-7 Add review-query algebra, work queues, and resume actions

**Work:** Implement the compact/canonical query grammar documented above,
including the exact accepted expression
`#approved intersect 1.19.2 HEAD`, its normalized expansion, and the stricter
`effective(#approved)` form used by completion policy. Project query results to
ordinary explorer/jump-list panels. Add saved/ad-hoc query, next/previous,
defer, resume, status, and show-normalized-query actions, plus equivalent
`sfm-propagate-changes.exe review session query|status --file ...` human/Facet
outputs. Persist only stable query/work-unit identities and explicit deferrals;
derive reviewed/unreviewed truth from the corpus and effective comments.

**Validation:** Precedence/parentheses, union/intersection/difference laws, raw
versus effective approval, lane and pinned-side aliases, moved ambient `HEAD`,
zero/one/many lanes, unknown atom, stale query revision, deterministic ordering,
close/reopen halfway, source change before resume, and CLI/game parity. A query
with suspended `#approved` results visibly differs from effective approval.

**Completion criteria:** A reviewer can ask what is approved, remaining,
blocked, suspended, or missing for the pinned 1.19.2 candidate; stop halfway;
commit the file; reopen it; and continue at the next still-valid unit.

### [x] RCS-S2 Project the complete 1.19.2 change domain into ordinary comments

**Work:** Run one structured comparison producer over the entire real 1.19.2
`previous_release..HEAD` pair and project added/removed/modified/renamed and
ambiguous operations into ordinary generated comments with stable producer,
rule, generation, and source-snapshot identity. Every changed before/after
surface enters the release-change completion domain. When structural Java
analysis is unavailable, retain a bounded file/diff-hunk fallback review unit
with an explicit limitation rather than making that change invisible.

**Validation:** Added/deleted/renamed/binary/generated/unsupported files,
empty-side tombstones, changed whitespace, repeated producer run, stale
generation, partial parser/index availability, deterministic ordering/counts,
and a raw Git diff inventory reconciled against the produced completion domain.
The reconciliation must account for every changed path and byte/hunk or explain
its explicit exclusion under the versioned policy.

**Completion criteria:** The one-lane review domain is complete enough to be
finished: every real change is navigable or appears as an explicit unsupported
work item, and producer reruns cannot duplicate or silently erase progress.

### [x] RCS-8 Make completion and attestation self-contained and fail closed

**Work:** Compute changed-domain, raw-tag, effective-approval, uncovered,
blocking, suspended, ambiguous/missing, deferred, unsupported, and stale-
producer queries from the canonical file and pinned corpus. Persist completion
reports by witness and add an explicit maintainer-attestation action that is
enabled only under the selected completion policy. Record file/corpus/policy/
producer fingerprints and mark attestations stale after any relevant change.
The required puppet proves readiness but never invokes human attestation.

**Validation:** Zero-change lane, partial review, overlapping approval/problem,
changed approval, deferred work, unsupported fallback, stale producer, changed
candidate commit, changed policy/query, post-attestation comment edit, and
reopen from a Git checkout. Prove the semantic-state hash is non-circular:
adding the attestation or changing only resume/presentation state preserves it,
while changing a comment, target, policy, producer generation, or source corpus
invalidates it. CLI status and in-game status must agree and use a non-success
exit/status for incomplete or stale review without claiming that tests or Git
cleanliness imply approval.

**Completion criteria:** The review file can truthfully say `in_progress`,
`ready_for_maintainer_attestation`, `complete`, or `stale`, explain every
blocking count through jump lists, and serve as the commit-able human-review
evidence referenced by the release gate.

### [x] RCS-S1 Prove the complete resumable loop on real 1.19.2 release code

**Work:** Resolve the existing prior-release tag and pinned `HEAD` commit for
the 1.19.2 lane, create the canonical repository review file, materialize the
complete RCS-S2 domain, and open it through the ordinary review explorer and
EditorV3. Review at least one genuine changed structural surface, run
`#approved intersect 1.19.2 HEAD` and the remaining/effective queries, stop with
the session intentionally incomplete, close Minecraft, clear process-local and
rebuildable cache state, reopen the same file, and resume at the next valid
work item. Use a two-checkpoint self-hosting sequence: first commit the completed
implementation and pin that source commit as the candidate corpus; then create
and commit the initialized `in_progress` review document in a separate local
review-evidence-only commit. Do not push. The final worktree is clean, and the
session classifies the second commit as evidence-only rather than source drift.

**Validation:** Record real tag/commit ids, source hashes, complete changed-path
reconciliation, query normalization/results, autosave generations, resume
cursor, Git diff/commit of the review file, and structured plus PNG evidence from a
self-orchestrating natural-input puppet. Commit review-progress evidence in an
isolated test repository/fixture and prove that review-file-only commits do not
retarget the pinned source candidate. Also perform a manual-test handoff from
the installed client; the user must not need to rerun the installer.

**Completion criteria:** The morning handoff is a feasible, self-contained
1.19.2 review process: the user can open a repository-tracked review file,
navigate every current release change or explicit unsupported item, annotate
and query it, pause and resume across game restarts, see exact remaining work,
and eventually produce a fail-closed completion attestation. The initialized
file is committed locally as `in_progress`, the working tree is clean, and the
goal does not perform the maintainer attestation, push, or claim the release is
reviewed.

## Post-goal natural-testing reconciliation — explorer-native review workbench

The first manual use of the initialized 1.19.2 review proved that the durable
review kernel is usable but that its current presentation is still a developer
surface rather than a coherent review workbench. This section is authoritative
for the resulting usability/safety corrections. Generic explorer, workspace,
palette, and contextual-action mechanics remain owned by their existing plans;
this plan owns their exact release-review composition and acceptance journey.

### Authoritative natural-testing guidance ledger — 2026-08-23

| ID | Active guidance | Required consequence | Primary owner |
| --- | --- | --- | --- |
| RUX-1 | The permanent `Release Review · <status>` header and `Selected: <leaf>` footer repeat the same selected-preview information and consume useful space. | Remove the bespoke duplicate chrome. The location control identifies the review; transient open/status feedback uses ordinary narration/toasts and is not repeated permanently above and below the tree. | RCS-UX1 |
| RUX-2 | The changes tree visibly lacks the generic explorer's location, search/filter, view, presenter, lazy-loading, and reveal behavior. | Replace `SFMReviewExplorerPanel` as the production review tree with an ordinary `SFMExplorerPanel` projection backed by a review resolver/presenter/action contributor. Do not independently reimplement explorer behavior. | RCS-UX1 |
| RUX-3 | A target/crosshair control like IntelliJ's should reveal the focused document in this explorer. Use Minecraft's target-block ItemStack affordance, tooltip, and narration. | Add an action-backed reveal control to generic explorer chrome. It captures the exact destination explorer and the most recently focused compatible non-explorer panel/document; it never relies on hidden focus at execution time. | RCS-UX2 |
| RUX-4 | Repeatedly opening the same before/after leaves currently grows the stack (`1,2,…17`). Reopening the same two identities should switch between the same two entries. | Give every review presentation a stable identity and focus an existing matching entry before creating one. Preview ownership/retirement remains safe for unrelated terminals, writable editors, and other explorers. | RCS-UX3 |
| RUX-5 | Bottom-right stack numbers are visible but not mouse-operable. | Left-click focuses the exact entry, middle-click closes it, and right-click opens its constrained action menu. Each number has title/position narration and a tooltip; stale captures cannot act on a replacement entry. | RCS-UX4 |
| RUX-6 | The user needs a way to close every editor/tab in one visible group, with confirmation for a large group. | Preserve the established vocabulary: a **panel entry/tab** is stacked content and a **pane** is one visible split leaf. `sfm:panel/close` closes one entry; add hierarchical `sfm:pane/close` for the whole pane, with exact-count and dirty-document confirmation and no silent data loss. | RCS-UX4 |
| RUX-7 | Each participating file/lane should offer `before`, `after`, ordinary Git/text diff, and structured diff leaves. | Add versioned text- and structure-aware diff surfaces with source mappings back to pinned before/after ranges. Java structured diff uses the already-pinned Arborium/tree-sitter stack. Local Difftastic and syndiff checkouts are implementation references only, not runtime/toolchain dependencies. | RCS-S3 / RCS-UX5 |
| RUX-8 | A review is a repository-trackable JSON file. It should be revealable and editable as JSON through the file explorer, while right-click exposes `Open review read-only` and `Open review writable`. | Keep the canonical file URI visible. Review interpretation is an explicit explorer view/presenter over that path, not a hidden shortened ID. Multiple read-only sessions may coexist; a writer lease is scoped per canonical path. Production actions carry an explicit review-session selector instead of consulting one unqualified global active review. | RCS-UX1 / RCS-UX6 |
| RUX-9 | A fresh/empty explorer should be a mouse-usable entry point instead of requiring many palette commands. Suggested roots include My Computer/drives, current instance, SFM source, registries, reviews, recent locations, and favourites. | Add a lazy explorer-home projection. Expanding My Computer or a drive must not recursively enumerate it; roots and unavailable authorities remain explicit. | RCS-UX7 |
| RUX-10 | The review must be usable primarily with the mouse while retaining the command palette as the discoverable/power-user surface. Screen elements invoke registered actions rather than mutating private state through an inaccessible path. | Every review row, tab, reveal control, selector/comment gesture, status control, and context-menu choice emits or is equivalent to a stable action with captured explicit selectors. Keyboard and mouse parity tests compare outcomes. | All RCS-UX items |
| RUX-11 | On read-only reviewed source, the reviewer should be able to drag/select a 2D/code region and apply a recently used comment such as `#approved`/`#needs-change`, or choose `Other` to write arbitrary text. Later graphical markup must compose with this rather than replace it. | Selection opens a bounded contextual palette/popover backed by ordinary comment actions. Recent choices are history-ranked; `Other` opens the preferred editor for comment text. The resulting comment stores the accepted pinned literal/semantic selector and exact spatial witness. | RCS-UX6; RCMARK-1..4 later |
| RUX-12 | Right-clicking actionable UI should include a general `Help me understand` route so the system teaches itself. | Add a contextual help action/provider whose localized presentation can explain the element class/provider, stable element/action IDs, current captured address/selectors, available gestures, and related actions without requiring execution. Tabs, explorer rows, review controls, and later arbitrary widgets use the same contract. | RCS-UX8 / contextual-input plan |
| RUX-13 | Home and End currently move the palette suggestion selection instead of the input caret. | While the input owns focus, unmodified Home/End operate on the command document. First/last suggestion navigation receives a distinct discoverable binding; selection-list navigation must not steal ordinary text-editor keys. | RCS-UX4 / contextual-input plan |
| RUX-14 | In a one-lane review, the root/lane labels repeat `4.34.0-1.19.2 → HEAD` beneath an already-identical release heading. | The location/view identifies the comparison once. A single lane is hoisted or labelled concisely as `1.19.2`; multi-lane reviews retain explicit lane grouping. Before/after/diff leaves retain their exact pinned revision tooltips/narration. | RCS-UX1 |
| RUX-15 | Manual testing crashed while two Java interaction-map requests overlapped preview use: `renderExactDocumentSelections` tried to render line 190 beyond the current canvas projection. | Selection/range publication and rendering must be generation/hash/document bound, tolerate a half-open EOF position after a trailing newline, reject stale preview results, and never turn malformed/stale highlight evidence into a render-thread exception. | RCS-UX0 |
| RUX-16 | The UI should expose review progress without forcing memorization of the command list, but the command surface remains useful and complete. | Mouse entry points and contextual menus call the same read-only/writable open, query, work-next, comment, diff, close, and help actions documented for the palette. No mouse-only review mutation is permitted. | RCS-UX1..8 |
| RUX-17 | The target-block tooltip is clipped to its Explorer pane and can be overpainted by a subsequently rendered pane. | Panels publish tooltip candidates; the workspace chooses and renders the final tooltip only after every panel, widget, and scissor scope has finished. No panel directly renders a tooltip that may escape its bounds. | RCS-UX2a / generic workspace tooltip contract |
| RUX-18 | The first target click and first command-palette open with a release review active can stall noticeably, while warm retries improve. | Action availability is constant/bounded work and never constructs a complete review projection. Build the document-to-review-row reverse index asynchronously with generation/cancellation guards; reveal dispatch stays responsive and reports pending/failure without blocking the render thread. Add cold and warm telemetry rather than accepting cache-masked behavior. | RCS-UX2a / RCS-UX1 latency correction |
| RUX-19 | With no addressed editor available, the target remains visible and invokes an unavailable Brigadier node, yielding `Incorrect argument ... sfm action invoke` instead of an intentional state. It also lacks normal button feedback. | Hide the target control entirely—including focus, hit, narration, and reserved width—when no compatible document exists. A context race fails with the action's precise unavailable reason, never a parser artifact. Accepted pointer/keyboard activation plays exactly one vanilla button-click sound. | RCS-UX2a |
| RUX-20 | Changes/Comments/Hashtags/Query/Status/Migrations can be opened by memorized palette commands but cannot be switched from the review workbench with the mouse. | Add an action-backed review-lens control using the ordinary constrained choice surface. It indicates the active lens and exposes every valid lens without creating a parallel selection widget. | RCS-UX6a |
| RUX-21 | Existing comment actions require an undiscoverable selector-proposal id, and the Comments lens only displays existing comments. This is not a feasible review-authoring loop. | On a writable reviewed before/after/diff surface, select a source-backed region and choose `#approved`, `#needs-change`, a recent template, or `Other`; persist the exact proposal/witness and refresh Comments/Hashtags/query status immediately. Read-only sessions explain and offer the explicit writable transition instead of pretending mutation succeeded. | RCS-UX6a / RCS-UX6 |
| RUX-22 | Keyboard users can open an Explorer row's contextual action surface with the hardware Menu key, but the established Alt+Enter contextual gesture does nothing. | Alt+Enter and the Menu/Shift+F10 gestures capture the same selected row and open the same constrained action surface; none may activate/open the row as a side effect. | RCS-UX6b1 / contextual-input plan |
| RUX-23 | Opening a real 30 MB writable review and expanding a cold review node pauses for one or two seconds with no visible pending state. | Parse/projection work remains off the render thread where possible. Every active lazy child request immediately renders an animated, narrated loading state while retaining the last useful rows; failure and cancellation replace pending state explicitly. | RCS-UX6b1 |
| RUX-24 | Filtering Changes for `java` showed one already-materialized fixture even though the ledger contains 1,337 Java units across 1,230 distinct paths. | A review search is complete over the immutable review projection, not merely the current lazy page. Resolve matches and their ancestor chains asynchronously with generation/cancellation guards, publish bounded pages, and report complete/incomplete search scope honestly. Do not eagerly materialize every source body. | RCS-UX6b1 |
| RUX-25 | Newly persisted human comments are hidden after 2,917 generated comments because Comments pages in append order. | Present the append-only comment feed newest-first (and use deterministic ordering within hashtag/file groupings), refresh the active lens after mutation, and reveal/select the newly created comment. Generated review-unit evidence must not bury human review work. | RCS-UX6b1 |
| RUX-26 | Comment submission freezes visibly because one render-thread action serializes, reparses, hashes, and atomically writes the full ledger plus recovery mirror. | Capture the immutable mutation intent on the client thread, perform validation/serialization/write on an owned worker, show `Saving comment…`, and publish only if review path/open epoch/generation still match. Conflicts remain explicit and no optimistic UI may claim persistence before atomic save succeeds. | RCS-UX6b1 |
| RUX-27 | A source range can be commented successfully but the open before/after/diff preview shows no durable indication that a comment targets it. | Render source-mapped comment decorations as an independent overlay layer (background/underline/gutter according to style rules), with hover/click navigation to comment details. Decorations are hash/generation bound and update after save without replacing syntax colours. | RCS-UX6b1 / RCMARK-1..4 |
| RUX-28 | Structured diff text is neutral and marker-heavy; the reviewer wants normal Java syntax plus independent added/removed meaning. | Keep syntax token foregrounds and diff semantics orthogonal. Carry source language and versioned `added`, `removed`, `modified`, `moved`, and `context` regions through the generated-surface contract; compose red/green/etc. backgrounds or gutters without destroying Arborium syntax spans. Text-diff fallback gets a registered diff provider rather than SFML highlighting. | RCS-UX6b1 / RCS-S3 follow-up |
| RUX-29 | One added-file text-diff preview reported `Document unavailable` while the paired Java structured surface opened. | Log generated-surface request identity, kind, duration, outcome, and bounded failure code; reproduce the exact file pair and make text/structured availability agree unless a typed per-kind limitation explains the difference in the document. Tombstone `before` leaves remain explicitly distinct from diff failures. | RCS-UX6b1 |
| RUX-30 | The Status lens appears as one inert root while its children are pending, so the completion witness categories are undiscoverable. | Use the common loading state, auto-expand/select the lens root when opened, and retain actionable category counts (`remaining`, `blocking`, etc.) whose children are exact navigable witnesses. Empty categories state `0` rather than appearing broken. | RCS-UX6b1 |
| RUX-31 | The 30 MB ledger's human comments did persist, yet the automated fixture claimed immediate Comments/Hashtags visibility because its corpus was tiny. | Add a production-scale deterministic fixture with more than one page of generated comments and more than one page of changed paths. Prove newest-comment visibility, complete `java` search, loading feedback, asynchronous save responsiveness, restart persistence, and inline decoration without mutating the maintainer review. | RCS-UX6b1 |
| RUX-32 | Opening Comments failed with `Invalid ARGB colour R` because the textual gutter marker was routed through the colour parser. | Preserve `gutter_marker` as an independent string from schema through datasource and rendering. Only foreground/background/underline channels accept ARGB; malformed optional colours retain the deliberate fallback style rather than erasing it. | RCS-UX6b1 / RCMARK-2 |
| RUX-33 | Once complete review filtering existed, `java` matched the inherited path repeated in every before/after/diff label and expanded every Java file into four noisy rows. | Separate visible labels from resolver-contributed search terms. A file-name query matches compact file rows plus locating ancestors; semantic role queries such as `structured` still match the corresponding diff leaves. Do not hide the exact path/revision context from display, narration, or addressing. | RCS-UX6b1 |
| RUX-34 | On the production review, activating `.java` first exposed two materialized matches and then 1,237 complete matches roughly twelve seconds later; editing or clearing the query repeatedly stalled the UI because complete-domain projection and fuzzy scoring ran on the render path. | Replace query-independent full-tree publication and render-thread rescoring with a resolver-owned, asynchronous, cancellable, generation-bound, query-specific result index/page stream. Retain the last useful result page while a new query runs, publish exact complete/incomplete counts, and make each keystroke bounded render-thread work. | RCS-UX6b2 / lazy-explorer plan |
| RUX-35 | Filtering is currently also tree pruning: a directly matched `SFM.java` row can be expanded yet its `before`, `after`, text-diff, and structured-diff children disappear because their labels do not match `.java`. The reviewer also needs navigation among findings without hiding unrelated tree context. | Keep compact direct-match rows initially. Once a directly matched expandable row is explicitly expanded, include all of its immediate contextual children even when they do not match; preserve only necessary ancestors elsewhere. Introduce a separate next/previous finder operation whose result cursor does not prune the visible hierarchy. Filter evidence distinguishes direct matches, locating ancestors, and contextual descendants. | RCS-UX6b2 / lazy-explorer plan |
| RUX-36 | A cold expansion shows only a distant aggregate footer spinner, leaving the exact row looking broken while its children are loading. | Publish a stable, non-activatable `Loading children…` placeholder at the exact future child insertion point as soon as a request starts. Replace it atomically with children, typed failure, or empty state; the footer may additionally narrate aggregate pending work. No child load blocks the render thread. | RCS-UX6b2 / lazy-explorer plan |
| RUX-37 | Target/reveal failed with `Explorer continuation could not be acquired` when the destination relation was partial or already loading. | Reveal is race-safe across partial pages and in-flight requests: deduplicate/await the active continuation or use resolver-owned direct path-chain materialization, retain generation checks, and select the exact row only after its ancestor chain exists. A concurrent filter, comment refresh, or page request must not turn an addressable row into a transient reveal failure. | RCS-UX6b2 / lazy-explorer plan |
| RUX-38 | Review and generic Explorer rows lack a copyable diagnostic description, making a failed or ambiguous row expensive to report. | Every selected row exposes `Copy details` through the common contextual action surface. The versioned payload includes explorer/session/view identity, canonical path/address, entry kind and stable id, label/search terms, parent/relation/page/generation/loading state, selected/open destination, and resolver-specific evidence without forcing materialization of source bodies. | RCS-UX6b2 / lazy-explorer and contextual-input plans |
| RUX-39 | Command-palette candidate text is truncated while useful width remains, its complete tooltip activates only over a tiny icon/details strip, and a candidate cannot be inspected or copied through right-click. | Compute candidate text width from the actual row affordances and scrollbar. Hovering any point in a truncated row exposes its complete accessible description. Right-click opens an action-backed surface for copying display text, replacement/surface text, canonical executable command, or complete versioned candidate details/help. | RCS-UX6b2 / contextual-input plan |
| RUX-40 | A comment selector proposal labelled `symbol · jdk-source://.../Override.java` looked as though the comment would target the JDK declaration even though persistence correctly targeted `SetTerminalTransportAction.java` bytes `[831,1237)` and used local `@Override` bytes `[1102,1111)` plus JDK `Override.java` only as semantic definition evidence. | Proposal labels and details lead with the effective local review target, source side/path/range, and selected excerpt. Semantic definition, provenance, confidence, and migration evidence are visually subordinate and explicitly named; they can never masquerade as the destination selector. Preserve an exact regression fixture for this local-annotation/external-definition case. | RCS-UX6b2 / contextual-input plan |
| RUX-41 | Workspace panel-moving captured every middle-button drag before Text Editor v3 could pan its 2D document, regressing an established editor gesture. | Perform explicit pointer ownership arbitration before mutation. Text-editor content owns ordinary middle-drag panning; panel movement begins only from pane chrome/numbered stack affordances or an explicit discoverable modifier such as Alt+middle-drag. Middle click without a drag may retain the panel action surface. Tests prove both gestures and cancellation without duplicate actions. | RCS-UX6b2 / workspace plan |
| RUX-42 | The Comments lens leads with opaque storage IDs and flattens target evidence into ambiguous rows, while the reviewer cannot separately open the comment value, durable selector, derived matches, selected excerpt, or provenance. | Present every comment as an inspectable object tree: a useful text/tag/path preview label, then `value`, `selector`, `matches (N)`, and `provenance`. `value` opens the full text read-only; `selector` exposes kind/expression, literal witness, and semantic evidence; `matches` contains derived side/path/range/excerpt rows that open exact source regions; `provenance` owns id, producer, kind/schema, and parent lineage. Selector intent and evaluated matches remain distinct so zero, one, many, stale, and ambiguous results are honest. | RCS-UX6b2 / review kernel |
| RUX-43 | Persisted source comments have a small decoration but no approachable, anchored details surface, especially when multiple comments overlap one range. | Make each source decoration expose a gutter/hover/click sticky-note-style details presentation backed by the same comment object and actions as the Comments lens. It shows useful comment text first, supports overlap selection, opens value/selector/matches/provenance, remains keyboard accessible, and never replaces syntax/diff styling. | RCS-UX6b2 / RCMARK-1..4 |
| RUX-44 | The Comments lens drops input while scrolling only 24 visible rows out of 129 entries; unlocked-wheel events visibly outrun rendering. | Instrument event-to-projection-to-frame latency and per-frame row/render work on a production-shaped comment corpus. Eliminate render-thread rebuilding and unbounded per-frame work, coalesce only redundant state updates without delaying the first wheel delta, retain bounded virtualization, and prove responsive wheel/key scrolling while background review work continues. | RCS-UX6b2 / lazy-explorer plan |
| RUX-45 | The Changes lens presents every repository-relative path as one long row (`docs/architecture/...`), so the changed surface is difficult to orient in at a glance. | Default Changes to a lazy path-segment hierarchy (`docs` → `architecture` → … → file) while retaining an explicit flat-path presentation. Layout changes reproject the same immutable review identities in the same Explorer/session; filter, finder, reveal, comments, and open-preview deduplication must work in either layout. The review lens control and every Changes-row context surface expose the alternate mode through `sfm:review/changes/layout/set hierarchy|flat-paths`. | RCS-UX6b3 / lazy-explorer plan |
| RUX-46 | Generic alphabetical sorting displays `after` before `before`, even though review evidence has a temporal order. | Give the four file-pair children an explicit semantic name-sort order: `before`, `after`, `text diff`, `structured diff`. Labels and durable identities remain unchanged, and other Explorer sort axes remain available. | RCS-UX6b3 |
| RUX-47 | A newly opened filesystem Explorer exposes/hoists the single `run` root differently on its first opening than on later openings. | Specify and test one deterministic single-root hoist/expansion policy across cold open, reopen, refresh, and restored session state. Do not make the first interaction depend on whether a child page happened to be warm. | RCS-UX7 / lazy-explorer plan |
| RUX-48 | The inline loading animation uses proportional-width glyphs, making `Loading children…` shift horizontally every frame. | Use a fixed-width animation cell or fixed anchor/bounds so progress remains animated without moving adjacent text. This is explicitly lower priority than the hierarchy slice. | RCS-UX6 remainder / generic explorer presentation |
| RUX-49 | Opening the writable production review still pauses for roughly half a second before the workbench appears. | Record open-to-first-useful-frame phases and move remaining parse/projection work off the render thread while keeping the previous useful screen visible. Do not optimize away durability validation or writer-lease safety. | RCS-UX6 remainder |
| RUX-50 | The lens control offers a confusing executable `Current · Changes` no-op and consumes location-bar width; the same lens choices are not yet available from ordinary review-row context menus. | Move toward one contextual review-presentation surface that identifies active lens/layout non-operatively, offers only meaningful transitions, and can be opened from review chrome or rows. Retire redundant top-right chrome only after mouse, keyboard, narration, and action discoverability are equivalent. | RCS-UX6 remainder / contextual-input plan |
| RUX-51 | Markdown before/after previews render without Markdown-aware syntax styling. | Route review source leaves through their actual source language provider; add Markdown support using the existing pinned syntax-worker path or an explicit truthful plain-text fallback until supported. Never apply SFML styling merely because the host mod is SFM. | RCS-UX6 remainder / syntax-highlighting plan |
| RUX-52 | Text and structured diffs still expose raw `@@`, `---`, and `+++`-style marker documents, making additions/removals harder to scan than an inline review surface. | Build on RUX-28: compose language syntax foregrounds with independent added/removed/modified/moved/context backgrounds or gutters, and offer a richer inline/side-by-side review presentation without losing the exact textual diff or source mappings. Difftastic remains reference material, not a runtime prerequisite. | RCS-UX6 remainder / RCS-S3 follow-up |
| RUX-53 | Hierarchical Changes now distinguishes path segments structurally, but synthetic directories such as `docs` and `platform` render as paper on the title screen and remain difficult to distinguish from source leaves. The resolver lost the chest's declared fallback, while the renderer also treated every custom model as unsafe even though vanilla's `BlockEntityWithoutLevelRenderer` and `ChestRenderer` explicitly support a null level. | Preserve requested item, title-safe fallback, and accessible label through the generic Explorer presentation seam. Review directories retain the preferred theme chest when the renderer is known level-independent and retain barrel as a useful fallback for unavailable or unproven custom rendering; files keep language/review-specific icons. Focused round-trip/resolver/policy tests and a title-screen puppet must prove the distinction. | lazy-explorer X-8d |
| RUX-54 | The review hierarchy is a useful proving ground for richer semantic presentation (`docs`, `platform`, `Minecraft`, language/tool names), but hard-coding those words in the review renderer would create another isolated UI dialect. | Reuse the generic structured, inspectable Explorer presentation registry: typed path/structural metadata, specificity-based contributed rules, Java baseline, optional async enrichment, versioned cache evidence, and independently actionable icons. Review rows may contribute semantic facets but do not own precedence, AI access, or cache policy. | lazy-explorer X-8e |
| RUX-55 | A review icon may differ between title-screen and in-world contexts, but the UI currently offers no way to learn whether that is necessary, conservative, or caused by a missing item. Silent fallback teaches the reviewer a false model of the system. | The icon itself has flat right-click aspect actions backed by semantic, replayable commands rather than widget ids. XEXP-38/IPR-02 supersedes the earlier generic Help grouping only. Subject-specific explanation reports requested/resolved items, current context, capability evidence and typed fallback reason; `sfm:explain/itemstack_rendering_in_the_title_screen` is directly offered as its own aspect. A stable non-colour-only disturbed marker appears only when degraded. Bounds/geometry remain a separate action and payload. | lazy-explorer X-8e / XEXP-35..38; IPR-T3 |

### Verified implementation evidence and constraints

- `SFMReviewExplorerPanel` currently owns its own rows, header/footer, scroll,
  and preview slot. It does **not** use `SFMExplorerPanel`; this is the verified
  cause of the missing location/filter/presenter behavior.
- `SFMReviewExplorerPanel.openSelected(false)` repeatedly calls
  `SFMScreenMultiplexer.openIntoSlot`, while
  `SFMExplorerPreviewPlacement` already owns typed immutable-preview retirement
  for the generic explorer. Stable-document deduplication is still missing from
  both paths and must compare typed identity, never display title alone.
- `SFMScreenMultiplexer.renderEntryAffordances` draws numbered stack boxes but
  has no corresponding hit regions or actions.
- `SFMCommandPaletteScreen` consumes unmodified Home/End for suggestion-first/
  suggestion-last whenever suggestions exist, even while its text input is the
  active editing surface.
- The 2026-08-23 crash at `3840x2054`, GUI scale 4, is preserved in
  `platform/minecraft/run/crash-reports/crash-2026-08-23_13.56.42-client.txt`:
  `SFMDrawCanvasScreen.lineText` rejected line 190 while rendering an exact
  document selection. The canvas textual projection may omit a trailing empty
  line even though the immutable baseline legitimately addresses EOF on that
  next line; this is not permission to clip arbitrary stale ranges silently.
- The canonical review file may be dirty because it contains the reviewer's
  live resume/presentation/comment progress. Treat it as user data. Tests use a
  copy or fixture and must never restore, rewrite, or normalize the canonical
  file without the reviewer's explicit review mutation.
- Dependency declarations and lockfiles remain frozen for this wave. Arborium
  already exists in the toolchain. Difftastic/syndiff may be read as local
  reference implementations, but this plan does not add them as binaries,
  crates, subprocesses, or user prerequisites.

### Confirmed review-workbench design

1. **Location and view are independent.** The explorer location displays the
   canonical `file:///.../*.sfm-review.json` address. A named review projection
   (`changes`, `comments`, `hashtags`, `query`, `status`, or `migrations`) is an
   explicit view/presenter recipe over that location. Ordinary file opening
   still opens the JSON as text; a context action opts into review semantics.
2. **Identity is not a title.** A source presentation key includes canonical
   review path, review semantic-state/corpus identity, lane, side/view kind,
   immutable document revision/hash, target range or diff-surface identity,
   and editor/provider recipe. Reopening an equal key focuses the existing tab;
   changed identity creates a distinct entry.
3. **Actions are self-contained.** UI controls may derive convenient explicit
   selectors such as the exact explorer/pane/panel-entry id plus a captured
   `most-recent-compatible-non-explorer` source, but execution never consults
   an unspecified current focus. Set-valued selectors retain the established
   multi-target preflight/atomicity rules.
4. **Diff display is addressable evidence.** Text and structured diff rows map
   every selectable displayed region to zero/one/many pinned before/after
   source ranges with diagnostics. Comments target those durable source
   selectors plus the diff projection witness; they do not target ephemeral
   coloured pixels alone.
5. **Pane versus panel entry is explicit.** The existing bottom-right boxes are
   compact tab selectors for entries in one pane. Closing a pane is a different
   action from closing the current entry. Confirmation reports total, dirty,
   read-only, and independently recoverable entries before destructive close.
6. **One review runtime is not the public model.** Runtime repositories become
   keyed by review-session id/canonical path. A compatibility adapter may
   expose the focused session internally during migration, but new actions and
   panel recipes carry an explicit selector and tests prove two simultaneous
   read-only reviews do not bleed state.
7. **Review content projection is not explorer layout.** `changes`, `comments`,
   `hashtags`, `query`, `status`, and `migrations` are typed review lens/content-
   projection ids. They are independent from generic explorer layout choices
   such as list, small icons, details, sorting, and grouping. The canonical
   review-file location therefore survives changing either axis.
8. **Context actions enter with the explorer migration.** RCS-UX1 includes the
   minimal generic clicked-row action seam and `.sfm-review.json` actions needed
   to open plain JSON, a read-only review, or a writable review. RCS-UX6 expands
   that seam into the complete annotation/session journey; it is not a reason
   to leave the migrated review undiscoverable by mouse.
9. **Help is progressively disclosed.** RCS-UX2 through RCS-UX4 require useful
   tooltips, narration, stable ids, and ordinary action names. They do not grow
   a private partial help framework. RCS-UX8 introduces the reusable
   `sfm:help/understand` provider and then contributes it to those controls.
10. **Pane-close prompting is deterministic.** Preflight asks for confirmation
    whenever the pane contains more than one entry or any entry is dirty or not
    independently recoverable. The prompt reports exact total, dirty,
    read-only, and recoverability counts; cancellation changes nothing.

### [x] RCS-UX0 Make exact-selection rendering fail safe without losing evidence

**Work:** Bind exact selections and Java-interaction publications to immutable
document address, source hash, canvas/document generation, and range witness.
Preserve baseline text for unchanged read-only coordinate/render operations so
a valid half-open EOF after a trailing newline remains representable. Before
paint, distinguish valid empty terminal-line endpoints from stale/out-of-bounds
ranges. The former draws the preceding non-empty part and no rectangle for the
empty endpoint; the latter clears/suspends the highlight, emits a structured
diagnostic, and never throws from `render`.

**Validation:** Add focused tests for LF/CRLF, leading/interior/trailing empty
lines, EOF endpoints, shorter replacement documents, stale asynchronous maps,
rapid A→B→A preview switches, and malformed ranges. Reproduce the attached
`3840x2054@4` sequence in a puppet or deterministic panel test and assert no
render-thread exception plus retained diagnostic evidence.

**Completion criteria:** No stale or projection-normalized selection can crash
Minecraft; valid reviewed source ranges still visibly highlight the intended
glyphs and every rejected range explains why it was rejected.

**Progress 2026-08-23:** Complete. The reproduced exception was traced to a
valid half-open full-document range whose EOF was `(190,0)` after a trailing
newline, combined with a glyph-derived projection that intentionally omitted
that empty terminal line. `SFMExactDocumentSelectionPublication` now publishes
one immutable address/hash/content-generation/range witness and rejects stale
or malformed evidence before paint without discarding its diagnostic. Focused
regressions cover LF, CRLF, Unicode, leading/interior/trailing empty lines,
multiple terminal newlines, the exact 190-line shape, malformed ranges,
shortened replacement documents, stale asynchronous maps, and A→B→A identity
reuse. The release-review journey and explorer-UX natural puppets exercise the
same live review/editor presentation path without a render-thread exception.

### [x] RCS-UX1 Project release reviews through the generic lazy explorer

**Work:** Implement review path resolver/child provider, presenter, action
contributor, and view recipes for changes/comments/hashtags/query/status/
migrations. Retire `SFMReviewExplorerPanel` from production registration after
parity. The generic location and filter controls, view modes, context-preserving
fuzzy filtering, lazy materialization, icons, keyboard navigation, reveal API,
and narration must be inherited rather than copied. Hoist redundant one-lane
comparison labels and retain truthful multi-lane/tombstone structure.

Treat the review recipes as a typed content-projection/lens axis rather than
adding them to the explorer's list/icon layout enum. In this same slice, add the
minimal reusable clicked-row context seam and expose plain-JSON, read-only
review, and writable-review actions for `.sfm-review.json` rows; defer richer
comment/session context actions to RCS-UX6.

The complete 30 MB/2,917-unit review must remain lazy: opening the review or
changing a filter cannot eagerly construct all source text or structured diffs.
Fetch-before-publish and generation checks preserve the last useful tree while
refreshing.

**Validation:** Resolver/presenter/action tests cover every projection, one and
multiple lanes, tombstones, huge-corpus bounded materialization, search with
context ancestors, read-only versus writable sessions, live refresh, and
location round trip. A natural puppet opens the canonical review from a file
explorer context action and reaches before/after leaves using only pointer
input after the initial explorer open.

**Completion criteria:** The production review tree visibly is the ordinary
explorer—canonical location, filter, view/presenter behavior, lazy status, and
reveal included—with no duplicated selected-item header/footer.

**Progress 2026-08-23:** Complete. Production review opening now creates a
generic `SFMExplorerPanel` backed by `SFMReleaseReviewExplorerRuntime` and
`SFMReleaseReviewExplorerPresenter`; changes, comments, hashtags, query,
status, and migrations are typed projection lenses over the canonical review
path. Children remain lazy and generation-checked, fuzzy filtering retains
materialized ancestors, one-lane labels are hoisted, and tombstones remain
explicit. The generic clicked-row action registry supplies review open/read-
only/writable and review-leaf presentation actions without restoring bespoke
header/footer chrome. Reopening the same durable review receives a fresh
ephemeral explorer identity through its open epoch, preventing stale resolver
state from a prior in-process session.

### [x] RCS-UX2 Add action-backed reveal-current-context explorer chrome

**Work:** Add a target-block ItemStack control to generic explorer chrome with
tooltip, narration, keyboard focus, and a stable action/element id. Its action captures
the exact destination explorer plus the most recently focused compatible
non-explorer document/panel and invokes the existing reveal coordinator. Empty,
unaddressable, stale, unauthorized, and multi-match contexts produce visible
diagnostics without changing the explorer.

Do not create a private help implementation in this slice; RCS-UX8 later
contributes the reusable `Help me understand` action to this stable control.

**Validation:** Pure capture/action tests and a split-view puppet cover text
editor→explorer reveal, review before/after/diff mapping, no compatible source,
stale tab, two explorers with deterministic destination, keyboard activation,
mouse activation, and every supported GUI scale.

**Completion criteria:** Clicking the target block scrolls/expands the explorer
to the focused document while preserving the editor's focus/history and never
guessing an unrelated row.

**Progress 2026-08-23:** Complete. Generic explorer chrome exposes a narrated,
keyboard-focusable target-block control backed by `sfm:explorer/reveal/here`.
The action session captures the exact destination explorer and compatible
source panel; stale, absent, unauthorized, and unmatched rows fail visibly and
do not mutate selection. The natural review puppet first selects a decoy
`after` row, focuses the corresponding `before` editor, then clicks the target
control and verifies expansion/selection of that exact before row.

**Manual regression 2026-08-23:** Reopened. Natural use found that the tooltip
is rendered inside the Explorer's active scissor and before later panes, the
raw hit rectangle emits no vanilla click sound, and the control remains visible
without an eligible document. In that absent-document state Brigadier's
availability `requires` hides the action node, so the fixed control command
fails generically at `sfm action invoke` instead of presenting its reason.
`SFMRevealHereAction.requirement()` can also call
`SFMReleaseReviewExplorerRuntime.revealTargets()`, whose cold path constructs
the complete review projection synchronously; palette availability evaluation
can therefore pay the same render-thread cost before the target is clicked.

**Resolution 2026-08-23:** The reopened defects are complete under RCS-UX2a.
The target now derives visibility from a cheap compatible-document snapshot,
dispatches its captured action without reparsing a command string, and uses a
generation-keyed asynchronous reverse index for review rows. The generic
workspace renders its deferred tooltip after panel scissors and later panes,
and accepted mouse/keyboard activations use the ordinary Minecraft click sound.

### [x] RCS-UX2a Repair reveal layering, availability, feedback, and latency

**Work:** Introduce one workspace-level deferred-tooltip contract and migrate
the target control to it so tooltips render after all panels with no active
panel scissor. Make target visibility a cheap snapshot derived from the most
recent compatible addressed document; absence removes the control from layout,
focus order, hit testing, tooltip, and narration. Preserve a precise stale-race
failure by resolving/dispatching the registered action directly rather than
turning unavailability into a Brigadier parse failure. Use the normal Minecraft
button activation sound for accepted pointer and keyboard activation.

Replace synchronous review projection discovery in action requirements with a
generation-keyed, cancellable reverse index from immutable document identity
(address plus source hash and side/revision evidence) to exact review rows. The
index may build asynchronously when a lens opens or the review generation
changes; reveal dispatch must be O(1)/O(depth) over published evidence and may
show a non-blocking pending state. Command-palette opening and action suggestion
generation must never trigger corpus projection, filesystem traversal, or
source loading. Add structured cold/warm timings and work counters.

**Validation:** A split-pane visual test proves a long target tooltip crosses
the pane boundary and remains above the neighbor. Geometry/focus tests prove
the target consumes no width or tab stop with no eligible document and appears
when one becomes available. Pointer and keyboard tests observe one click sound
per accepted activation. Direct, absent, stale, unauthorized, pending-index,
zero/one/many-match, and action-registry race tests produce specific feedback
and no generic parse error. A cold 30 MB review fixture and natural puppet prove
that first palette open and target input return without corpus-sized render-
thread work; telemetry distinguishes asynchronous index latency from input
dispatch latency.

**Completion criteria:** The target looks and sounds like an ordinary Minecraft
control only when useful, its tooltip is an unclipped top-level overlay, no
state emits the observed Brigadier error, and neither palette opening nor reveal
input freezes a frame while projecting the review.

**Completion notes 2026-08-23:** Complete in `6bc1dc7e0` with final shared-
layout coverage in `9f695c480`. `SFMExplorerPanel` publishes one context-
sensitive interaction layout for rendering, hit testing, tooltips, and puppet
geometry. The reveal action captures the exact explorer/document pair,
distinguishes absent/stale/unauthorized/pending/no-match/ambiguous outcomes,
and never constructs the formerly failing `sfm action invoke` string. Split-
pane tooltip, zero-width absent control, focus, sound, cold/warm reverse-index,
and natural mouse tests are green. The preferred and declared GUI-scale puppet
runs observed no parse failure or render-thread corpus projection.

### [x] RCS-UX3 Deduplicate and safely own review preview entries

**Work:** Extend generic preview placement with typed presentation identity and
focus-before-create behavior. Repeated activation of one before/after/diff leaf
focuses its existing entry. Alternating one file's before and after leaves
therefore alternates the same two entries. Ordinary preview replacement remains
available as an explicit mode; explicit open-new-tab/adjacent commands can
create duplicates only when requested. Never retire unrelated terminals,
writable editors, dirty documents, or previews owned by another explorer.

**Validation:** Model/workspace tests cover same key, same title/different key,
A→B→A, changed review generation, two explorers, missing prior panel, dirty
editor, terminal, explicit duplicate, and close/reopen. Puppet evidence asserts
bounded tab count and exact focused title/identity after repeated mouse opens.

**Completion criteria:** Natural browsing cannot grow an unbounded tab stack by
reopening the same review presentations, and preview replacement cannot destroy
unrelated content.

**Progress 2026-08-23:** Complete. Explorer preview placement now carries a
typed presentation identity and focuses an existing matching entry before
creating content. Repeated A→B→A review browsing remains bounded to the same
before/after entries, while changed generations, different explorers, explicit
duplicates, dirty/writable editors, terminals, and unrelated previews retain
their independent ownership. Focused tests and the mouse-only review puppet
assert bounded entry count and exact focused presentation identity.

### [x] RCS-UX4 Make pane/tab lifecycle and palette caret behavior mouse-complete

**Work:** Give the numbered stack affordances stable hit regions and action
drafts for focus, close, and move plus tooltip/narration and stable ids.
Left/middle/right pointer
semantics match RUX-5. Add `sfm:pane/close` with preflight and count/dirty
confirmation; retain `sfm:panel/close` for one entry. Correct palette Home/End
so the focused command document receives ordinary caret movement, and expose
separate discoverable first/last-suggestion actions/bindings.

Pane close confirms whenever more than one entry is present or any entry is
dirty/non-recoverable and reports the exact counts. RCS-UX8 later contributes
the reusable contextual-help entry rather than this task growing a private one.

**Validation:** Workspace geometry/action tests cover scaled panels, overlap,
stale ids, left/middle/right buttons, one/many/dirty stacks, confirmation cancel,
move, and tooltip/narration. Palette tests cover Home/End and Shift+Home/End
selection with zero/many suggestions plus the replacement list-navigation
bindings. A mouse-only puppet opens, switches, closes one, and closes a pane.

**Completion criteria:** Every visible tab selector is operable by mouse and
keyboard, pane close is explicit and safe, and editing a palette command no
longer loses standard Home/End behavior.

**Progress 2026-08-23:** Complete. Numbered stack entries have stable narrated
hit regions: left click focuses, middle click closes, and right click offers
captured one-shot focus, close, four-direction move, and containing-pane close
actions. `sfm:pane/close` performs an identity-sensitive preflight, reports
exact total/dirty/read-only/recoverability counts, and uses a constrained
confirmation whenever required. Home/End and Shift+Home/End once again edit the
palette command document; first/last suggestion selection is exposed through
separate registered actions and bindings. Prepaint hit caches are fail-safe so
headless and first-frame divider input cannot dereference absent geometry.

**Goal validation 2026-08-23:** The self-orchestrating
`sfm:title_screen_release_review_explorer_ux` puppet passed its complete declared
GUI-scale matrix (auto and 1–8) at `3840x2130`, and
`sfm:title_screen_explorer_interaction_fidelity` passed the same nine scales
with 54 captures. The durable release-review journey passed at its declared
`1280x720@auto` viewport, including semantic comment persistence, migration,
autosave, close, and resume. The full Java suite found 1,568 tests: 1,567
passed, zero failed, and one installed-worker integration test was
assumption-aborted because its opt-in system properties were absent. The SFM
audit exited successfully; its 346 unresolved findings are the two existing
text/font draw audit groups, not new goal failures. No CLI source or dependency
lockfile changed, so the already-installed user-facing tooling is current and
no `install.ps1` rerun is required.

### [x] RCS-UX5 / RCS-S3 Emit textual and structural review surfaces

**Work:** Freeze versioned CLI-AST `ReviewUnit`, `ReviewSurface`, source-map,
text-diff, structured-diff, and equivalence-report schemas. Produce deterministic
JSON plus human-readable presentation for the current 1.19.2 lane. Under each
file/lane, project `before`, `after`, `text diff`, and `structured diff` leaves.
Use existing Git data for text hunks and the pinned Arborium/tree-sitter stack
for Java structure matching. Preserve moves/renames, unchanged context,
parse/unsupported/ambiguous diagnostics, and explicit file/hunk fallback.

Study `G:\Programming\Repos\difftastic` and
`G:\Programming\Repos\syndiff` only as local reference material. Reimplement
the bounded algorithms/contracts needed by SFM; do not shell out, require a
user installation, or change the dependency graph for this slice.

**Validation:** Snapshot scenarios cover added/deleted/renamed files, moved and
edited methods, reordered declarations, comments/whitespace, imports, Unicode,
parse failure, unsupported file, source mappings in both directions, stable
serialization, and repeated generation. In-game tests select diff text and
create a comment whose durable target resolves against pinned source bytes.

**Completion criteria:** From the ordinary review explorer, the reviewer can
open before, after, textual diff, or Java structural diff, understand every
fallback/ambiguity, and attach/query comments without losing source identity.

**Completion notes 2026-08-23:** Complete in `b538d7bb9` and `9f695c480`.
The Rust producer freezes `sfm.review-file-pair/1`,
`sfm.review-surface-request/1`, `sfm.review-surface/1`, and
`sfm.review-correspondence-report/1`; `review surface generate --request-file`
emits deterministic bounded text or Java-structured presentations with exact
bidirectional UTF-8 source mappings. Java opens those presentations lazily from
the ordinary review explorer and retains the generated source map for comment
capture. Every corpus file remains visible even when it is outside the review-
unit domain; its four-leaf shape remains stable and unavailable generated diffs
state the exact limitation rather than dropping the pinned file.

Rust scenarios cover added/deleted/renamed files, CRLF/Unicode, output bounds,
edited/moved/renamed/reordered Java declarations, imports/fields, parse gaps,
unsupported languages, ambiguity, deterministic JSON, and repeated generation.
Java codec/runtime/model/capture tests prove lazy work, cancellation, cache
identity, source projection, tombstones, corpus-only rows, and source-backed
selection. Difftastic and syndiff remained read-only local references; no
runtime shell-out, new dependency, or lockfile change was introduced.

### [x] RCS-UX6a Prove one mouse-complete persistent comment loop

**Work:** Add an action-backed review-lens control to the generic review
Explorer. Clicking it opens the ordinary constrained palette containing
Changes, Comments, Hashtags, Query, Status, and Migrations, marks the active
lens, and preserves the canonical review path and explicit session selector.
The control obeys the deferred-tooltip, sound, focus, and availability contract
from RCS-UX2a.

For one canonical writable review journey, support pointer selection on
before/after and RCS-UX5 diff surfaces, then present recent templates plus
`#approved`, `#needs-change`, `Other`, and Cancel through an action-backed
choice. `Other` opens the preferred editor for arbitrary comment text. Resolve
the chosen literal/structural proposal explicitly, persist its pinned source
selection and projection witness atomically to the `.sfm-review.json`, and
refresh Comments, Hashtags, active query, and status lenses. A read-only review
offers an explicit reopen-writable action and performs no mutation. This slice
does not claim all multi-review/session-management work in RCS-UX6 complete.

**Validation:** Mouse and keyboard journeys cover lens switching, before/after,
text-diff and structured-diff selection, built-in/recent/arbitrary comments,
Cancel, stale and empty selections, read-only refusal/transition, autosave,
close/reopen, and exact comment/highlight/query recovery from the tracked JSON.
The natural puppet pauses after one comment, closes the workspace/runtime,
reopens only the review file, and proves that `#approved` query membership and
resume state survive. No test mutates the user's live review document.

**Completion criteria:** Without typing an action command, a reviewer can open
the workbench, switch lenses, inspect either diff, select source-backed content,
apply or write a comment, immediately find it by comment/hashtag/query, close
Minecraft, and resume from the repository-tracked review file.

**Completion notes 2026-08-23:** Complete in `9f695c480`. The review Explorer's
in-place lens control opens one constrained ordinary palette for Changes,
Comments, Hashtags, Query, Status, and Migrations. Pointer selection on pinned
source or generated diff text opens action-backed built-in/recent/custom/cancel
comment choices. Writable choices persist atomically to the selected tracked
`.sfm-review.json`; read-only sessions offer an explicit writable reopen and do
not mutate. Returning from a nested comment choice closes the stale contextual
palette and restores the review workspace. Exact selected source ranges,
projection witnesses, hashtags, query membership, and resume state survive
close/reopen.

The natural `sfm:title_screen_release_review_explorer_ux` puppet passed at its
preferred viewport and across the declared `3840x2130` GUI-scale matrix (auto
and 1–8). Its durable artifacts show a text diff, an exact `#approved` comment,
all six visited lenses, successful `#approved intersect 1.19.2 HEAD` membership,
atomic persistence, and a read-only reopen. Focused release-review tests pass;
the full Java suite reports 1,601 passed, zero failed, and one expected opt-in
installed-worker assumption abort out of 1,602. The locked/offline Rust suite
reports 727 passed and three network-dependent ignored tests. `audit --branch
1.19.2` exits successfully with 341 findings in the one pre-existing unresolved
`GuiGraphicsExtractor.text` group; this goal removed six ambiguous puppet-helper
false positives. Cargo/SFM lockfiles and the dependency graph are unchanged.

**Operational readiness 2026-08-23:** The user-facing CLI was installed from
the current goal source and resolves to
`G:\Programming\Caches\CARGO_HOME\bin\sfm-propagate-changes.exe` with SHA-256
`5445A354999C66CD5D6BAEC18986F8EDC90229AE41BDF18A14BAADF2FA52350F`.
`help list --short` exposes `review surface generate`; no user `install.ps1`
run is required. The final process check found no Java, Minecraft, SFM CLI, or
teamy-terminal process left running by validation.

**Manual acceptance walkthrough:**

1. Launch with `sfm-propagate-changes.exe run client --branch 1.19.2
   --wait-for-build-lock` and open a generic file Explorer.
2. Browse to `docs/reviews`, right-click a `.sfm-review.json`, and choose the
   writable review action. The same row also remains openable as plain JSON or
   as a read-only review.
3. In Changes, expand one file/lane and open `before`, `after`, `text diff`, or
   `structured diff`. Reopening the same leaf focuses its existing preview.
4. Drag across source-backed text, right-click, choose `Comment…`, then select
   `#approved`, `#needs-change`, a recent template, `Other…`, or Cancel.
5. Click the review-lens control and verify the comment through Comments,
   Hashtags, Query (`#approved intersect 1.19.2 HEAD`), and Status.
6. Close the workspace/game, relaunch, reopen only that tracked review file,
   and verify the comment and queue position remain. Do not use the protected
   maintainer review file for disposable experiments.

For repeatable automation, run `sfm-propagate-changes.exe puppet run
sfm:title_screen_release_review_explorer_ux --branch 1.19.2 --variant preferred
--wait-for-build-lock`; use `--variant declared` for the complete GUI-scale
matrix.

### [~] RCS-UX6b Stabilize the natural review-workbench controls

**Observed acceptance gaps:** Before/after and generated diff documents
inherited SFML colours;
directory, generic JSON, and review-ledger icons were too similar; wheel input
appeared dead over parts of both ordinary and review explorers; executable
palette commands could not be submitted with Enter; action-family shortcut
hints leaked Alt+D and Ctrl+Shift+E onto unrelated `sfm:panel/open` candidates;
Tab completion was incorrectly made conditional on whether it could transfer
focus; Alt+D was not global at the title screen and could retain a transient
palette as a workspace's return screen; closing a palette command could mutate
the removed palette; the workspace lacked direct diagnostics; and it lacked the
requested panel-level middle-button management gesture. Raising only review
JSON to 64 MiB then made a real 30,307,364-byte raw ledger stop responding,
proving that a larger eager allocation was not an acceptable fix.

**Work:** Keep every eager raw-text read, including compound
`.sfm-review.json`, capped at the existing 4 MiB bound. An oversized raw ledger
must return the typed `oversized` document immediately; users review its
projected changes, comments, and query lenses instead of eagerly editing the
whole persistence file. Carry explicit document-language metadata through literal, path,
pinned-revision, saved, and generated-surface snapshots. Java before/after
documents use the existing asynchronous Arborium worker; SFML alone uses the
local SFML highlighter; JSON, generated text/structured diffs, and unsupported
languages remain neutral until their own registered provider exists. Never
infer language from panel titles.

Use one specificity-aware theme resolver for directory (`chest`), generic file
(`paper`), Java (`cocoa_beans`), JSON (`written_book`), and compound review
ledger (`bell`) identities, with the longest case-insensitive suffix winning.
Make the complete Explorer panel the primary wheel surface so its location,
filter, list frame, and status chrome do not create dead input strips.

Repair the palette keyboard contract so an executable command wins over a
selected explanatory row on Enter. While command input owns focus, Tab belongs
only to completion and never conditionally becomes focus traversal. Give the
focused surface's registered Alt mnemonics first refusal for explicit focus
actions: register `sfm:focus <focus-target>`, bind Alt+E to `execute_button`
and Alt+C to `cancel_button`, and let right-click on either control offer both
its focus action and a canonical-command copy action. An unmatched chord may
still reach an intentional global action such as Alt+D. Localized control
tooltips render the current command-palette bindings.
Route puppet submission through the real Enter path.

Resolve shortcut badges, narration counts, and detail tooltips by exact
normalized `(action-id, command-draft)`, not action ID alone. Register
Alt+D for `sfm:panel/open sfm:text_editor` and Ctrl+Shift+E for
`sfm:panel/open sfm:explorer` in the global situation so both work from the
title screen. Persistent panel opening unwraps transient palette origins before
constructing the workspace; successful commands only reset a palette that is
still current; nested constrained palettes restore their parent palette's
active identity. `sfm:palette/close` must therefore return to the actual title,
world, or workspace rather than clearing/reinitializing a removed palette. The
generic Explorer's omitted-location default is the current Minecraft instance
directory, so Ctrl+Shift+E is concretely a File Explorer; item exploration stays
available through the explicit `registry://minecraft/item/` location.

Add a panel-management layer above child content for the middle button:
middle-clicking ordinary panel content opens the exact captured panel-entry
action surface; crossing a four-pixel drag threshold previews the destination
pane; releasing over another visible pane invokes the registered
`sfm:panel/entry/move/to <source-session> <destination-session>` action and
moves the source entry into that pane's stack without changing entry identity.
Invalid drops and Escape/focus loss cancel safely; left/right input remains
owned by panel content. The numbered stack controls retain their existing
left-focus, middle-close, and right-context semantics.

Register `sfm:screen/diagnostics`, with `sfm:overlay/diagnostics` as an alias,
to open a read-only plain-text document containing current screen class/title,
logical and framebuffer sizes, focused child, workspace panel IDs/classes/
bounds/stacks, previous screen, and every SFM overlay's identity, visibility,
content, input mode, z-order, and resolved bounds.

**Validation:** Focused tests cover fail-fast oversized review JSON and the
unchanged 4 MiB eager bound; explicit Java/diff language routing and
snapshot preservation; compound-suffix icon precedence in generic/review
explorers; immediate wheel routing over panel chrome and between hovered panes;
Enter/explanatory-row and Tab/completion ownership; exact command-scoped hint
identity; Alt+E/Alt+C focus defaults; global Alt+D/Ctrl+Shift+E defaults; panel
move identity; and click/drag/cancel gesture state. A title-screen puppet must
prove Alt+D both with and without a palette, Alt+E + Enter execution, Alt+C +
Enter cancellation, canonical palette close, diagnostics opening, and return
to the same title screen. The release-review puppet and manual journey still
verify projections, Java before/after, neutral diffs, icons, wheel, keyboard,
shortcuts, and panel movement without modifying the protected maintainer review
file.

**Completion criteria:** A restarted client rejects the real large raw review
JSON promptly while keeping its review projections usable, displays every
review surface with the correct language route and recognizable icon, scrolls
either Explorer from any chrome, submits commands with Enter, completes with
Tab, focuses controls with explicit Alt actions, shows only exact shortcut
hints, opens an untitled editor/Explorer from the title screen without losing
that screen, opens screen/overlay diagnostics, and moves or inspects a panel
with the middle button through the same typed actions available to automation.

**2026-08-23 automated evidence:** The focused regression suites and complete
Java suite pass (`1616` passed, `0` failed, with the one expected opt-in worker
test aborted); `sfm-propagate-changes.exe audit --branch 1.19.2` exits zero; and
the restarted `sfm:title_screen_release_review_explorer_ux` preferred-variant
puppet completed all captures, persisted its pointer-created `#approved`
comment, visited all six review lenses, proved
`#approved intersect 1.19.2 HEAD`, and closed/reopened the review. Its lens
assertion now waits for the lazy child page that semantically contains the
comment/hashtag rather than confusing the published root-only loading state for
a missing comment. Keep this item in verification until the maintainer manually
accepts the corrected oversized-file refusal, language/icon presentation, wheel
surface, explicit keyboard focus, exact shortcut hints, title-screen lifecycle,
diagnostics, and middle-button panel gesture in one restarted client.

**2026-08-24 corrective implementation evidence:** The natural journey's
remaining generic/review failures now have bounded implementations and tests:

- context capture rebases a visible canvas selection onto the exact current
  publication by line and Unicode-scalar column, maps EOF to EOF, and omits an
  incompatible range. Right-clicking a structured diff can no longer pass a
  stale `277` offset into a 276-byte document and crash the render thread;
  LF/CRLF, astral-Unicode, terminal-newline, and incompatible-publication
  regressions are covered;
- ItemStack presentation now composes the caller's current panel pose into the
  model-view transform before item rendering, so review/file icons remain
  visible in translated right-hand panes as well as the origin pane;
- generic Explorer projections are cached against explicit root/settings/
  relation/entry generations, so wheel and cursor movement do not repeatedly
  copy and sort the entire materialized relation graph. A visible draggable
  scrollbar and whole-panel wheel routing retain every input delta rather than
  debouncing a scroll gesture into one late jump;
- right-clicking the Explorer location offers exact-explorer copy/edit actions,
  and copy writes the canonical location rather than a clipped presentation;
- the built-in `sfm:fps` overlay is registered hidden-by-default and can be
  shown through the ordinary selector-addressed overlay visibility action;
  F3 exposes the same registered toggle as a labelled constrained choice; and
- the generated localization resources contain the palette Cancel label and
  the new keybinding-manager/input-diagnostics strings.

Canonical datagen, compilation, and the complete Java suite pass after this
correction (`1,628` passed, `0` failed, one expected opt-in worker test
aborted). Keep RCS-UX6b in maintainer verification until the restarted-client
journey visually confirms the right-pane ItemStacks, scrollbar/scroll latency,
location context action, F3 diagnostics, FPS overlay, and structured-diff
right-click path.

The restarted `sfm:title_screen_release_review_explorer_ux` puppet subsequently
passed at `3840x2130@auto` in run
`sfm-title_screen-20260824-005852-534`. Its disposable review completed lazy
text/structured diff materialization, source-backed comment creation, six-lens
querying, and close/reopen persistence. The captured split view visibly retains
Java, text-diff, structured-diff, and directory ItemStacks in the translated
right-hand pane, and no context-click range exception occurred.

### [~] RCS-UX6b1 Repair real-ledger projection completeness and feedback

**Exploratory follow-through:** E-1–E-4 in the linked exploratory usability plan
are complete, but measured review-open (~1.24 s) and note-save (~2.13 s) dispatch
remain synchronous stalls. ER-F1 owns the next bounded latency acceptance; do
not infer broad responsiveness from fast cached filtered expansion alone.

**September 5 bounded follow-through:** RCOV-A–E completed exact partial-
coverage safety, filtered nested-context action authorization, independent diff
styling and separate-process persistence. It also repaired V1 note focus/save
and per-comment selector identity. The linked supplement owns the proof and
remaining test-environment warnings. This umbrella stays active for its broader
real-ledger UX/performance obligations; do not infer all review-workbench or
language support is complete from the bounded acceptance.

**Manual evidence 2026-08-24:** The 30,314,243-byte
`manual-test.sfm-review.json` contains 2,917 review units, 1,337 Java units,
1,230 distinct Java paths, and two successfully persisted human selector
bindings. Nevertheless, `Ctrl+F java` exposed only one path because generic
filtering considered 136 materialized entries, and the Comments lens could not
reach the two human comments because they were appended after 2,917 generated
comments. The runtime currently performs full canonical serialization,
reparse/validation, existing-file hashing, authority replacement, and recovery-
mirror replacement synchronously inside `createComment`, explaining the
perceived comment-submit pause. One text-diff leaf also appeared unavailable
while its paired Java structured surface produced five mapped regions; the
current failure path does not log enough identity/evidence to distinguish a
generator failure from a stale/tombstone row interaction.

**Bounded corrective work:** First preserve keyboard parity and honest feedback:
Alt+Enter opens the same Explorer row context surface as Menu/Shift+F10; active
lazy requests retain old rows and show an animated `Loading …` status; Comments
and Hashtags project append-only comments newest-first so a newly authored
comment is in the first page. These corrections are small and independently
testable, but do not by themselves complete this item.

Then add a resolver-owned complete filter domain for immutable review
projections. The first bounded implementation is query-independent: when a
review filter becomes active, the review resolver materializes presentation
entries and complete immediate-child relations away from the render thread,
never source bodies, once per exact lens root and resolver generation. It
publishes atomically through the Explorer owner executor, is capped at 100,000
entries, and then lets the common fuzzy projection answer every subsequent
query with complete ancestor retention. A stale review generation cannot
publish. Generic filesystem filtering remains materialization-local until it
gains its own indexed resolver, and the UI labels those scopes distinctly. If a
future review exceeds the explicit domain cap, replace this bounded path with a
query-specific paged resolver operation rather than silently returning partial
matches.

**Implemented evidence 2026-08-24:** The review resolver now publishes that
bounded complete presentation domain once per exact lens root and resolver
generation. A focused production-shaped test proves that a `java` filter sees
rows beyond the first lazy page, including both `Cafe.java` and `Unicode.java`,
while retaining complete ancestor chains through the ordinary Explorer fuzzy
projection. The common loader rejects stale generations, deduplicates concurrent
requests, reports cap/failure diagnostics, and exposes the request through the
same animated loading state. Alt+Enter/Menu parity and newest-first comment
projection are also implemented and covered by focused tests. This item remains
in progress until the remaining persistence routes, generated-surface comment
mapping, composed syntax/diff styling, Status polish, and production-scale
natural evidence are complete. Generated text and structured surfaces now also
open through the existing deferred editor host, so their worker future can
display a loading panel instead of being joined by the panel-opening/render
path.

The follow-up manual regressions are represented explicitly rather than folded
into that broad claim. Resolver-owned search terms keep `java` results at the
file row while a `structured` query still finds structured-diff leaves; focused
projection tests cover both cases. Review-comment gutter markers remain textual
end-to-end, so a marker such as `R` cannot be parsed as ARGB and the Comments
lens no longer fails while building its style index.

Built-in `#approved` and `#needs-change` choices now hand one immutable mutation
to the application-owned persistence worker, report `Saving review comment…`
immediately, retain the committed document/generation until atomic persistence
succeeds, and refresh matching lenses only after publication. A queued-executor
test proves the handoff and no-optimistic-publication boundary. The free-form
`Other…` editor and non-comment review mutations still use the synchronous path,
so RUX-26 remains in progress pending their migration and production-scale frame
evidence.

Exact before/after source previews now derive hash- and generation-bound comment
decorations after a successful save. Background, underline, and textual gutter
layers compose independently with syntax foregrounds and refresh while the
document remains open. RUX-27 remains in progress because generated-diff source
mapping plus hover/click navigation to comment details are not yet implemented.

Move review mutations to a single owned persistence executor. The render thread
captures a typed comment/migration intent and immediately publishes a pending
state. The worker derives the candidate document, validates and writes it
atomically, and hands a generation-checked result back to the client executor.
During save, existing rows and editor text remain interactive. Success refreshes
every matching open review lens, reveals the new comment, updates query/status,
and publishes source-mapped decorations; conflict/failure retains the draft and
offers retry/copy diagnostics.

Extend generated review surfaces and editor styling with two composable layers:
language syntax spans (Java through the existing Arborium worker) and diff-role
regions (`added`, `removed`, `modified`, `moved`, `context`). Diff backgrounds,
gutters, and comment decorations never replace syntax foregrounds. Add bounded
surface telemetry and reproduce the observed added-file text-diff failure before
changing availability semantics.

**Validation:** Focused tests cover Alt+Enter/Menu parity, pending relation
visibility, newest-first comments past the first page, complete review search
with ancestor retention, stale-search cancellation, async-save conflict and
render-thread responsiveness, comment decoration refresh, and syntax/diff style
composition. A production-scale natural puppet opens a disposable review with
more than 1,024 changed paths and 2,917 generated comments, searches `java`,
authors a comment, observes pending/saved states, finds and clicks its inline
decoration and Comments row, inspects text and structured diffs, checks Status,
then closes/reopens from the tracked file. Artifacts record frame latency,
surface failure codes, match counts, and exact persisted selector evidence.

**Completion criteria:** On the real-size fixture, keyboard-only Alt+Enter works;
no cold operation looks inert; `java` finds the complete review corpus with its
ancestor hierarchy; a human comment appears immediately and remains visible in
the source plus Comments/Hashtags/query/status after restart; save work does not
block frames; and Java diff previews combine readable syntax with explicit
added/removed semantics. Any unavailable diff states a precise durable reason.

### [x] RCS-UX6b2 Make production review navigation inspectable and responsive

**Manual evidence and corrected boundary — 2026-08-24:** On
`manual-test.sfm-review.json`, entering `.java` first exposed two currently
materialized matches and later 1,237 complete matches. The log recorded
`explorer-1: refresh-requested` at `16:15:26.320` and `explorer-2: applied` at
`16:15:38.016`, an approximately 11.7-second opaque transition. Expanding a
matched file while the filter remained active hid all four useful children;
clearing the query stalled on successive edits; target reveal failed against an
in-flight partial continuation; and the Comments lens could not keep up with an
unlocked wheel at only `24 visible / 129 entries`. These are production-path
failures, not permission to replace the tracked maintainer review or to tune a
tiny fixture until it passes.

The same journey exposed three semantic presentation failures. Text Editor v3
lost ordinary middle-drag panning to the newer workspace panel-move gesture.
Command-palette candidates hid decisive information behind premature truncation
and a tiny tooltip hit area. Most importantly, a proposal displayed
`symbol · jdk-source://.../Override.java` even though the persisted comment's
effective target was the selected local range in
`SetTerminalTransportAction.java`; the external `Override.java` declaration was
supporting semantic evidence only. Storage happened to be correct, but the UI
made the reviewer unable to know what was being approved.

**A. Query, context, loading, and reveal (RUX-34 through RUX-37):** Move
production review search to one resolver-owned worker pipeline that accepts the
normalized query and exact review/lens generation, resolves direct matches and
ancestor chains, and publishes bounded pages with cancellation. It must not
publish a query-independent full presentation tree merely so the render thread
can rescore that tree on every keystroke. Preserve the last complete page while
new work is pending and distinguish incomplete/materialized counts from exact
complete counts. A direct matching row stays compact until the user expands it;
then its immediate before/after/text-diff/structured-diff children are contextual
descendants even if their labels do not match. A separate finding cursor owns
next/previous navigation without changing tree inclusion. Active expansion
publishes an inline `Loading children…` row, and reveal either joins/deduplicates
that exact request or directly materializes the resolver-known ancestor chain.

**B. Inspectability and honest proposal identity (RUX-38 through RUX-40):** Add
versioned copy-details payloads to Explorer/review rows and command-palette
candidates. Candidate layout uses all available row width; its entire row owns
the full-details tooltip when truncated; right-click offers display,
replacement, canonical command, and structured-details copies. Comment target
choices put the local effective destination—review side, path, range, and
excerpt—first. Definition/source-index evidence is explicitly secondary. The
exact local `@Override` versus JDK `Override.java` case is a non-negotiable
regression test.

**C. Pointer ownership (RUX-41):** Decide pointer ownership from panel chrome,
content kind, button, modifier, and drag threshold before either layer mutates.
Ordinary middle-drag over text-editor content pans the document. Moving a panel
uses pane chrome/numbered stack affordances or the selected explicit modifier;
middle click without crossing the threshold may still open the panel action
surface. Escape, focus loss, and invalid drops cancel whichever one owner won.
The live acceptance puppet must exercise these pointer paths through the
puppet plan's PA-3 virtual Minecraft-callback seam; validation may not reposition
the operating-system cursor or depend on GLFW-polled desktop-pointer agreement.

**D. Review object comprehension (RUX-42 and RUX-43):** Replace opaque
comment-id-first rows with a reusable comment-object projection:

```text
📝 #approved Reviewed local transport selection…
  value
    Reviewed local transport selection…
  selector
    kind
    expression / rule
    literal witness
    semantic evidence
  matches (N)
    after · path/to/File.java · [start,end) · "selected excerpt"
  provenance
    id · producer · kind/schema · parents
```

The selector is durable intent; `matches` are derived evaluations and therefore
may be zero, one, many, stale, or ambiguous. Opening `value` shows the complete
comment text in a read-only editor. Opening a match navigates to its exact
pinned source range and visibly shows the excerpt. Storage IDs belong in
provenance/tooltips, not as the primary human label. The same projection backs
an anchored source-decoration details surface, including overlap choice and
keyboard/action parity.

**E. Production interaction performance (RUX-44):** Add bounded telemetry for
input-received, query/relation work submitted, first useful page retained or
published, row projection, wheel offset publication, and frame presentation.
Use a deterministic disposable corpus at least as large as the observed review
shape. Fix measured render-thread recomputation and allocation; do not disguise
latency by debouncing away the first keystroke or wheel delta.

**Focused validation:**

1. Type `.java` one character at a time in a production-shaped Changes lens.
   The client remains responsive, old useful rows stay visible while pending,
   and the final exact count includes every generated path.
2. Expand a directly matched `SFM.java` row and observe inline loading followed
   by before, after, text diff, and structured diff without clearing the filter.
   Next/previous finding moves independently of tree filtering.
3. Trigger reveal while that row or its parent continuation is already loading,
   and again immediately after a comment generation refresh. Both converge on
   the exact selected row without a continuation-acquisition failure.
4. Copy details from an Explorer/review row and from a palette candidate. Hover
   anywhere over a truncated candidate to inspect its complete description;
   right-click copies each documented representation.
5. Reproduce the local `@Override` proposal. The choice names the current local
   file/range/excerpt as target and names JDK `Override.java` only as definition
   evidence; persisted selector bytes remain local.
6. Middle-drag a large source document to pan it, then move that panel through
   the explicit workspace gesture. Neither gesture invokes the other.
7. Open Comments on a production-shaped corpus, scroll with an unlocked wheel,
   expand a human comment into `value`, `selector`, `matches`, and `provenance`,
   open its complete text and exact source excerpt, then use the source marker
   to open the same details.
8. Focused model/interaction tests and a natural puppet record exact counts,
   generation/cancellation outcomes, reveal outcome, event-to-frame latency,
   and screenshots without mutating
   `docs/reviews/4.34.0-1.19.2-to-58ed4e431.sfm-review.json`.

**Completion criteria:** A reviewer can search or find, expand contextual review
surfaces, reveal a changing lazy row, understand and copy every proposed target,
pan source independently from moving panels, and inspect persisted comments as
value/intent/matches/provenance without guessing from IDs. The natural workflow
has no multi-second render-thread freeze, no missing first input delta, no
mislabelled semantic evidence, and no transient reveal race. The disposable
review survives close/reopen with the same comment target and derived matches.

**Operational boundary:** The dependency graph and checked-in dependency
declarations/lockfiles remain frozen. Introduce no new dependency and clone no
new developer/reference repository. Existing locked cache rehydration remains
permitted by the repository goal-execution guide. This slice changes Java,
tests/puppets, and plans only unless an already-existing CLI test surface needs
rebuilding; final tooling freshness and the exact manual launch path must be
reported after the last runtime-input change.

**Completion evidence — 2026-08-24:** RUX-34 through RUX-44 are implemented.
The resolver now owns cancellable query-specific filter and finder lanes;
contextual children and inline loading remain generation-safe; reveal joins
active work; Explorer rows and palette candidates expose versioned details;
local review targets lead semantic evidence; middle-drag ownership is explicit;
and Comments project value/selector/matches/provenance with source-anchored
details and bounded scrolling telemetry. The complete focused
`SFMReleaseReview*` family passed 106/106, and the Explorer, palette,
workspace-move, comment-model, lazy-loader, pagination, finder-independence,
candidate/row-copy, and source-comment navigation suites passed. The final
whole JUnit run started 1,674 tests: 1,667 passed, one property-gated worker
integration test aborted, and six shared-JVM
failures cascaded from `net.minecraft.locale.Language` initialization finding
no default-language resource; the first failing
`SFMOverlayActionGrammarTests` class passed independently after that run. The
final focused virtual-input source test also passed.

The natural command
`sfm-propagate-changes.exe puppet run sfm:title_screen_release_review_explorer_ux --branch 1.19.2 --wait-for-build-lock`
passed all nine `3840x2130@auto,1..8` variants with `failed=0 total=9`; artifacts
are under `sfm-title_screen-20260824-183208-397`. Its pointer path now uses the
PA-3 Minecraft-callback virtual input seam and cannot reposition the user's OS
cursor. The protected maintainer ledger remained dirty user state and was not
edited, staged, or used as a mutation target. No dependency/lockfile or CLI
source changed; the installed CLI at
`G:\Programming\Caches\CARGO_HOME\bin\sfm-propagate-changes.exe` (SHA-256
`5445A354999C66CD5D6BAEC18986F8EDC90229AE41BDF18A14BAADF2FA52350F`)
successfully built, tested, and launched the final Java sources, so no
`install.ps1` run is required.

### [x] RCS-UX6b3 Project review changes as a navigable repository hierarchy

**Manual evidence and boundary — 2026-09-03:** The production Changes lens
shows complete repository paths as sibling labels, obscuring the shape of the
change set, and the ordinary Explorer name sort places `after` before `before`.
This corrective slice owns RUX-45 and RUX-46. It does not absorb the separately
tracked cold-root inconsistency, proportional-width spinner, writable-open
latency, lens-chrome cleanup, Markdown provider, or richer diff rendering in
RUX-47 through RUX-52, nor the title-screen icon fallback, generic
presentation-registry, and explainable degraded-icon follow-ups in
RUX-53 through RUX-55.

**Contract:** `Changes` opens in repository hierarchy mode. Each normalized
repository path contributes lazy directory segments followed by one file-pair
node; expanding a directory materializes only its bounded child page. Renames
are grouped beneath the after-path when present while retaining the before and
after paths in labels/search evidence and preserving one stable file identity.
The explicit alternate `flat-paths` layout restores complete path rows. A
layout switch replaces only the ephemeral resolver root: review path/open
epoch, Explorer id/location/settings, immutable file/source/diff ids, and
existing preview identities remain stable. Complete filtering includes exact
ancestor chains in hierarchy mode and compact direct file matches in either
mode. Reveal resolves the actual path chain for the active layout.

The action `sfm:review/changes/layout/set hierarchy|flat-paths` is available
through Brigadier suggestions, the existing review control, and every Changes
row's ordinary context surface. The default name sort publishes semantic sort
keys for `before`, `after`, `text diff`, and `structured diff`; presentation
labels and non-name sort axes are not rewritten.

**Focused validation:** Model tests compare hierarchy and flat projections and
prove stable file identities. Resolver tests prove default hierarchy, complete
filter ancestors, row-context discoverability, semantic leaf ordering, and an
in-place flat/hierarchy switch that retains Explorer/review identity. Action
tests prove canonical suggestions plus the bounded alternate-layout choice.
The release-review puppet must still reach and open one complete four-leaf file
pair through the default hierarchy at every supported GUI scale.

**Completion criteria:** A newly opened review visibly starts with repository
segments such as `docs`, a reviewer can expand down to a file and sees `before`
above `after`, and the same workbench can switch to complete flat paths and back
without duplicate previews, lost filters, changed durable addresses, or a
render-thread traversal of every source body.

**Completion evidence — 2026-09-03:** Changes now defaults to a lazy
path-segment model and retains an explicit `flat-paths` model behind
`sfm:review/changes/layout/set`. The review control and each Changes row expose
the alternate action; switching replaces only the ephemeral resolver root and
retains Explorer id/location/settings, review path/open epoch, stable file ids,
and source/diff presentation identities. File rows contribute their complete
paths as search evidence even when the visible label is only the basename.
Review-specific name sort keys preserve `before`, `after`, `text diff`, then
`structured diff`.

`SFMReviewExplorerModelTests` passed 18/18,
`SFMReleaseReviewExplorerRuntimeTests` passed 10/10, the two new layout-action
tests and two lens-control tests passed, and the complete focused
`SFMReleaseReview*` family passed 108/108. The natural
`sfm:title_screen_release_review_explorer_ux` puppet passed its full nine-variant
`3840x2130@auto,1..8` matrix. Artifacts are under
`sfm-title_screen-20260903-173958-123`; figure 1 visibly shows `src` as a
directory, `Cafe.java` beneath it, and the four temporal/diff children in the
required order. No dependency, lockfile, CLI source, or protected maintainer
review file changed.

### [ ] RCS-UX6c Add viewport-backed read-only virtual documents

**Motivation:** A 30+ MiB review ledger must eventually be inspectable without
converting one size-limit exception into a multi-second allocation, parse, style,
layout, and render stall. The first slice is read-only; saving and arbitrary
editing remain deferred until chunk identity and transaction semantics are
proven.

**Contract:** Introduce a versioned virtual-document recipe carrying canonical
address, expected content identity/hash, byte length, encoding, and a reusable
line/byte index. Convert the editor's 2D camera and logical viewport (plus a
bounded prefetch margin) into requested line/byte ranges. Resolve ranges off the
render thread with cancellation and monotonically increasing request generation;
publish only ranges matching the current address, hash, viewport generation,
and panel lifetime. Retain an explicitly bounded LRU of immutable chunks and
render stable loading/error placeholders where bytes are absent. Panning,
scrolling, jumping, and resizing request newly exposed ranges immediately
without clearing still-valid visible chunks.

Syntax providers receive only loaded ranges plus enough overlap/state to make
their boundary behavior explicit. Selection, copy, symbol queries, diagnostics,
and full-document operations must report whether their required ranges are
materialized rather than guessing. A normal small document stays on the eager
path; virtual mode is selected by typed metadata/policy, not by a panel-title
heuristic.

**Validation:** Pure tests cover viewport-to-range math, Unicode and CRLF line
indexes, prefetch/clamping, stale-result rejection, cancellation, LRU eviction,
and placeholders. A natural puppet opens a deterministic sparse file larger
than 30 MiB near the origin, pans/jumps to distant regions, returns to a cached
region, and proves bounded resident bytes, responsive frames, correct visible
text, and no render-thread full-file read. Raw release-review JSON may opt into
this path only after that evidence passes.

### [ ] RCS-UX6 Make review opening and annotation naturally mouse-driven

**Work:** Add file-explorer contextual actions for opening a `.sfm-review.json`
as plain JSON, opening its review read-only, and opening it writable. Replace
unqualified singleton assumptions with explicit review-session selectors and
path-scoped writer leases. On reviewed source/diff surfaces, pointer drag creates
an ordinary selection; release offers recent comment templates, `#approved`,
`#needs-change`, and `Other`. `Other` opens the preferred text editor for a
comment draft. Every outcome dispatches the existing comment action with the
chosen literal/structural proposal and exact witness.

**Validation:** Two simultaneous read-only reviews, one writable lease, same
file text-versus-review views, recent-choice ordering, arbitrary comment text,
cancel, stale selection, multi-range, autosave/conflict/recovery, restart, and
mouse/keyboard parity. The canonical manual journey must be pausable after any
comment and resumable from the tracked JSON alone.

**Completion criteria:** After opening the explorer, a reviewer can find the
review file, open it, browse a change, select source, apply or write a comment,
and see persisted progress without typing an action command.

### [ ] RCS-UX7 Add a lazy explorer home

**Work:** Define a generic home location/view whose top-level entries include
My Computer, current instance, SFM source, registered
registries, review files/recent reviews, recent locations, and favourites.
Unavailable roots remain visible with explanation. Expansion is lazy and
bounded; opening `C:\` or My Computer does not recursively enumerate descendants.

This explicitly supersedes the current omitted-location default that opens the
item registry. `My Computer` is the virtual parent whose lazily fetched children
are the available drives; drives are not duplicated as peer roots. Merely
displaying or expanding an authority never grants recursive-search authority.

**Validation:** No-drive/no-source, multiple drives, multiple instances,
registry-only, recent/favourite persistence, unauthorized path, cancellation,
and huge-root lazy tests plus a mouse-only title-screen puppet.

**Completion criteria:** One generic explorer open is enough to discover the
main SFM navigation domains without a memorized command or an eager disk scan.

### [ ] RCS-UX8 Add reusable contextual self-explanation

**Work:** Register `sfm:help/understand <captured-context-selector>` and a help
provider/presenter that can explain a tab, pane, explorer row/control, action,
and review surface. Explanations include localized purpose, stable ids,
provider/class provenance in developer mode, current address/selector, common
mouse/keyboard gestures, and links/actions for deeper help. Right-click menus
include it where the capture is valid.

**Validation:** Provider composition, unknown/third-party element, stale
capture, localization, developer/release detail, keyboard-only, mouse-only,
and no-side-effect tests. Help itself must be inspectable/cancellable through
the ordinary constrained palette.

**Completion criteria:** A user can right-click any newly introduced review or
tab affordance and learn what it is and how to operate it without leaving the
application or executing the target action.

### Review-workbench execution order and bounded next goal

The safety repair is first and independently committable:

1. **RCS-UX0** — eliminate the observed render-thread crash and freeze stale
   range/publication invariants.
2. **RCS-UX1 through RCS-UX4** — complete: replace the bespoke review tree with
   the generic explorer, add reveal, deduplicated previews, mouse tabs/pane
   close, and fix palette caret behavior.
3. **RCS-UX2a** — complete: repair the manually observed reveal tooltip,
   availability, sound, parse-feedback, and cold render-thread latency defects.
4. **RCS-UX5 / RCS-S3 plus RCS-UX6a** — complete: add source-mapped textual/
   structured diff reports and prove one mouse-complete persistent comment loop.
5. **RCS-UX6b** — corrective focus/lifecycle/diagnostics slice in progress;
   rerun focused/full tests and the title/review puppets, then await maintainer
   acceptance of oversized refusal, language/icon, scrolling, explicit focus,
   exact hints, global shortcuts, diagnostics, and panel gestures.
6. **RCS-UX6b1** — reopened follow-through: completeness, comment visibility and
   composable styling have bounded acceptance, but ER-F1 retains the observed
   synchronous real-ledger open/save stalls; mutation responsiveness is not done.
7. **RCS-UX6b2** — complete: correct the production review's filter/find semantics,
   asynchronous query and reveal behavior, row/candidate inspectability,
   pointer ownership, comment object projection, and Comments scrolling through
   RUX-34 through RUX-44.
8. **RCS-UX6b3** — complete: project changed paths as a lazy repository
   hierarchy by default, retain an explicit flat-path mode, preserve identities
   across layout switches, and order temporal/diff children as `before`,
   `after`, `text diff`, `structured diff` through RUX-45 and RUX-46.
9. **RCS-UX6c** — later, add read-only viewport-backed virtual documents before
   allowing raw ledgers above the eager 4 MiB boundary.
10. **RCS-UX6 remainder** — then broaden the mouse-first workflow to complete
   multi-session leases, conflicts, recovery, and every annotation edge case.
11. **RCS-UX7/RCS-UX8** — broaden discoverability through explorer home and
   contextual self-explanation.
12. **RCS-S4** — only then prove cross-lane structural equivalence without
   deduplicating human approval.

RCS-UX0 through RCS-UX5/RCS-S3 and the bounded RCS-UX6a loop are complete.
RCS-UX6b2 / RUX-34 through RUX-44 and RCS-UX6b3 / RUX-45 through RUX-46 have
bounded acceptance, including inspectable comments and the identity-preserving
hierarchy/flat-path alternate. September 5 exploratory evidence explicitly
reopens RCS-UX6b1's latency follow-through; it does not establish general
responsiveness or every cancellation race. Prioritize ER-F1–ER-F4 before the
larger **RCS-UX6 remainder**, which adds multiple
simultaneous reviews, path-scoped writer leases,
conflict/recovery behavior, arbitrary-comment editing completion,
multi-range/stale-selection breadth, and full mouse/keyboard parity. Its
constituent follow-ups explicitly include RUX-48 through RUX-52: stable loading
animation geometry, writable-open latency, non-redundant contextual lens
controls, Markdown syntax presentation, and richer composable diff rendering.
RUX-47's cold/reopen single-root hoist consistency remains generic Explorer
work owned with RCS-UX7. RUX-53 is the bounded lazy-explorer X-8d repair that
keeps review directories container-like through title-screen safety fallback;
RUX-54 deliberately delegates richer semantic/icon/cache behavior to the
generic structured presentation registry in lazy-explorer X-8e rather than
hard-coding review-specific name rules. RUX-55 keeps title-screen capability
and fallback explanations semantic, replayable, visibly discoverable, and
separate from geometry inspection.
RCS-UX7/RCS-UX8 then broaden discovery and help;
RCS-S4 remains the first cross-lane structural-equivalence proof.

### Review-workbench risk register

| Risk | Guardrail |
| --- | --- |
| A stale range or async semantic result crashes render again | RCS-UX0 validates address/hash/generation and treats rendering as a total operation with structured rejection evidence. |
| Migrating to generic explorer loses review-specific live refresh or work-queue state | Resolver/action integration tests compare every existing projection and resume cursor before retiring the bespoke panel. |
| A 30 MB review eagerly materializes source/diff nodes and freezes the game | Generic lazy pages, bounded first paint, cancellable diff generation, and complete-corpus performance assertions in RCS-UX1/RCS-UX5. |
| Preview dedup focuses the wrong same-titled document | Typed immutable presentation keys include review/lane/side/revision/range/provider; titles never establish identity. |
| Pane close discards writable state | Preflight all entries, dirty-count confirmation, atomic close intent, and cancellation tests. |
| A global active review leaks comments between two panels | Path/session-keyed runtime and explicit action selectors; singleton access is migration-only and covered by two-review tests. |
| Structured diff invents semantic equivalence | Diff presentation carries source mappings and diagnostics only; RCS-S4 owns evidence-based cross-lane equivalence and human approval is never inferred. |
| Mouse affordances diverge from palette behavior | Every mutation is action-backed and parity tests compare mouse, keyboard, and direct command execution. |
| External diff experiments become undeclared prerequisites | Difftastic/syndiff are read-only references; dependency/lockfile freeze checks remain required. |

### Natural-testing intent audit

- **Pass 1 — extraction:** Reread the complete 2026-08-23 manual-testing
  message and separated duplicate chrome, generic explorer reuse, target-block
  reveal, preview accumulation, clickable/middle/right-click tabs, pane close,
  Home/End, textual/structured diff, no external diff prerequisite, review-file
  discoverability, session scope, explorer home, mouse-first actions/comments,
  contextual help, one-lane label repetition, and the attached crash into
  RUX-1 through RUX-16.
- **Pass 2 — traceability:** Mapped every RUX item to RCS-UX0 through RCS-UX8 or
  RCS-S3, named concrete current classes and reuse seams, and added validation,
  completion criteria, execution order, and risks. Generic explorer/window/
  palette implementation remains cross-plan-owned while this plan owns the
  release-review composition.
- **Pass 3 — adversarial omission:** Checked that “mouse-usable” does not delete
  the command surface, review JSON remains directly editable as JSON, one
  writable lease does not prohibit multiple read-only sessions, diff colours do
  not become durable addresses, local Difftastic/syndiff references do not
  become dependencies, a target button does not rely on unspecified focus, and
  the user's dirty canonical review is not treated as a disposable fixture.
- **Pass 4 — 2026-09-03 hierarchy narration:** Re-read the maintainer's complete
  hands-on Changes-lens narration and split its observations into RUX-45 through
  RUX-52. RUX-45/RUX-46 are complete in RCS-UX6b3 with a hierarchy default,
  explicit flat-path alternate, stable review/file/preview identities, retained
  full-path search evidence, and semantic temporal/diff ordering. The cold-root
  presentation mismatch, spinner jitter, writable-open pause, redundant
  current-lens control, Markdown highlighting, and richer diff rendering remain
  individually addressable follow-ups rather than being compressed into an
  unspecified polish task.
- **Pass 5 — 2026-09-03 presentation narration:** Traced the paper-like
  `docs`/`platform` rows through review structural kind, theme request, resolver
  publication, title-screen custom-render safety, and the discarded fallback.
  RUX-53 owns preservation of the chest/barrel specification plus the bounded
  correction that permits vanilla's known level-independent chest renderer.
  RUX-54 links—without duplicating—the typed subject, semantic-specificity
  ordering, diverse
  replaceable defaults, Java baseline, optional asynchronous/AI enrichment,
  versioned cache evidence, independently actionable icon bounds, and
  structured JSON/compressed-log resolver work recorded as XEXP-27 through
  XEXP-37 and X-8e/X-8f in the lazy-explorer plan. RUX-55 separately preserves
  semantic title-screen help, exact subject replay, typed degraded reasons and
  a disturbed visual affordance while forbidding geometry from being buried in
  that explanation.
- **Known source limitation:** None for this message; the original prompt and
  attached crash log were available. Earlier compacted requirements remain
  represented by the pre-existing ledgers and audits in this plan.

### Candidate core, parallel topology, and elastic continuation

The candidate required core is **RCS-0 through RCS-8 plus RCS-S1 and RCS-S2**.
The user approved this candidate as the active goal on 2026-08-23 under the
frozen dependency and bounded process-lifecycle posture. The fixture proof
remains required because it gives deterministic
exact/relocated/changed/ambiguous/missing coverage. RCS-6/RCS-7/RCS-8 make that
machinery portable, queryable,
resumable, and completable; RCS-S2/RCS-S1 ground it in the complete current
1.19.2 release-change domain. A comment-only or one-file demo does not satisfy
this candidate.

- **Track A — selection/comment adapter:** RCS-0/RCS-1 Java and cross-language
  fixtures; owns no parser or UI.
- **Track B — structural proposals/evaluation:** RCS-2/RCS-3 Rust analysis and
  pure Java evaluation adapters; owns no panel/action registration.
- **Track C — migration presentation:** RCS-4 fixture-backed panel/actions;
  consumes the frozen contract and owns no session codec.
- **Track D — portable document/query kernel:** RCS-6/RCS-7 Java/Rust codecs,
  stores, query evaluator, CLI, and pure fixtures; owns no source comparison.
- **Track E — real change domain:** RCS-S2 comparison/comment projection and
  reconciliation; owns no comment or approval semantics.
- **Integration owner:** merges current providers, completes RCS-5/RCS-8/RCS-S1,
  runs final validation/install, and updates canonical plans. RCS-5 waits for
  A/B/C; RCS-8 waits for B/D/E; RCS-S1 waits for every required track.

After the clean committed core, continue according to the current review-
workbench execution order above. RCS-UX0 through RCS-UX5/RCS-S3 and the bounded
RCS-UX6a single-review loop are complete, as are RCS-UX6b2 and RCS-UX6b3's
production navigation corrections. The next coherent goal is the RCS-UX6
remainder; RCS-S4 remains the first cross-lane equivalence proof after
multi-session review ownership and recovery are complete.

The dependency graph and checked-in lockfiles remain frozen. Local checkpoint
commits are required; no push, propagation, release tag, publication, broad
refactoring mutation, graphical markup, or maintainer approval is included. The
existing goal-readiness process authority, tool freshness, and bounded
cache-rehydration rules apply.

### 2026-08-23 trajectory intent audit

- **Pass 1 — extraction:** Preserved the goal of human inspection before a
  release tag, multi-file/revision comments, AST-aware selectors, conservative
  migration, cross-lane deduplication evidence, generated diff/audit/compiler
  comments, and in-game plus CLI/HTML review—not merely the latest puppet UI.
- **Pass 2 — dependency trace:** X-9 and SS-7 are the missing joins between
  completed editor/explorer/spatial foundations and comment Phase 3/4. Those
  feed Phase 5 producers, CLI-AST 2.5/2.5a, comment Phase 7/8, and release Phase
  5.5. Puppet browsing, rich viewport arguments, logging, generic provider
  selection, and manager remote control are useful parallel capabilities but
  are not predecessors of this join.
- **Pass 3 — adversarial omission:** The slice has visible in-game value yet
  does not confuse fixture migration with real multi-version release approval,
  a passing analyzer with human review, a moving live selection with a pinned
  comment, or a screenshot with structured evidence. The old sidecar/worktree
  proposal remains historical reference and cannot silently override current
  schemas.

## Historical structural selector and migration wave — 2026-07-23 (superseded)

This was the original proposed Track 6 wave. Its scenario and conservative
migration requirements remain useful source evidence, but the exact v1-only
contract, prepared-sidecar assumption, branch/worktree names, and “next wave”
status are superseded by RCS-0 through RCS-8 plus RCS-S2/RCS-S1 above and by
foundations completed after 2026-07-23. Do not dispatch these historical briefs
verbatim.

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
