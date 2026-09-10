package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2Kernel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Pure validation, query, semantic-hash, and completion kernel. */
public final class SFMReleaseReviewKernel {
    public enum CompletionStatus {
        IN_PROGRESS,
        READY_FOR_MAINTAINER_ATTESTATION,
        COMPLETE,
        STALE
    }

    public record QueryResult(String expression, String normalizedExpression, List<String> reviewUnitIds,
                              List<SFMReleaseReviewCoverage.UnitCoverage> surfaceCoverage) {
        public QueryResult {
            reviewUnitIds = List.copyOf(reviewUnitIds);
            surfaceCoverage = List.copyOf(surfaceCoverage);
        }
    }

    /** Exact review-unit witnesses behind every redundant completion count. */
    public record CompletionWitnesses(
            List<String> changedDomain,
            List<String> approvedRaw,
            List<String> approvedEffective,
            List<String> remaining,
            List<String> blocking,
            List<String> suspended,
            List<String> missing,
            List<String> deferred,
            List<String> unsupported,
            List<String> staleProducer
    ) {
        public CompletionWitnesses {
            changedDomain = sorted(changedDomain);
            approvedRaw = sorted(approvedRaw);
            approvedEffective = sorted(approvedEffective);
            remaining = sorted(remaining);
            blocking = sorted(blocking);
            suspended = sorted(suspended);
            missing = sorted(missing);
            deferred = sorted(deferred);
            unsupported = sorted(unsupported);
            staleProducer = sorted(staleProducer);
        }
    }

    public record CompletionReport(
            CompletionStatus status,
            String reviewSemanticStateHash,
            int changedDomain,
            int approvedRaw,
            int approvedEffective,
            int remaining,
            int blocking,
            int suspended,
            int missing,
            int deferred,
            int unsupported,
            int staleProducer,
            CompletionWitnesses witnesses,
            List<String> diagnostics,
            List<SFMReleaseReviewCoverage.UnitCoverage> surfaceCoverage
    ) {
        public CompletionReport {
            Objects.requireNonNull(witnesses, "witnesses");
            diagnostics = List.copyOf(diagnostics);
            surfaceCoverage = List.copyOf(surfaceCoverage);
        }
    }

    private record Context(
            SFMReleaseReviewV1 document,
            Map<String, SFMReleaseReviewV1.NamedQuery> namedQueries,
            Map<String, SFMReviewSessionV2.Comment> comments,
            Map<String, SFMReviewSessionV2Kernel.Evaluation> evaluations,
            Set<String> domain,
            Set<String> approvedRaw,
            Set<String> approvedEffective,
            Set<String> blocking,
            Set<String> suspended,
            Set<String> missing,
            Set<String> deferred,
            Set<String> unsupported,
            Set<String> staleProducer,
            List<SFMReleaseReviewCoverage.UnitCoverage> surfaceCoverage
    ) {}

    private SFMReleaseReviewKernel() {
    }

    /** Read-only attribution using the same policy and revision-qualified ranges as completion. */
    public record ApprovalEvidence(String commentId, String explanation, String targetDescription,
            List<SFMReviewSessionV1Kernel.Range> targetRanges,
            List<SFMReviewSessionV1Kernel.Range> effectiveCurrentRanges,
            List<SFMReviewSessionV1Kernel.Range> historicalRanges) {
        public ApprovalEvidence {
            targetRanges = List.copyOf(targetRanges);
            effectiveCurrentRanges = List.copyOf(effectiveCurrentRanges);
            historicalRanges = List.copyOf(historicalRanges);
        }
    }

    public static List<ApprovalEvidence> approvalEvidence(SFMReleaseReviewV1 document) {
        validate(document);
        var context = contextUnchecked(document);
        String approval = normalizeHashtag(document.reviewSession().completionPolicy().approvalHashtag());
        var required = SFMReviewSessionV1Kernel.normalize(context.surfaceCoverage().stream()
                .flatMap(value -> value.required().stream()).toList());
        var blockers = new ArrayList<SFMReviewSessionV1Kernel.Range>();
        for (String tag : document.reviewSession().completionPolicy().blockingHashtags())
            blockers.addAll(hashtagRanges(document, context.comments(), context.evaluations(), normalizeHashtag(tag), false));
        var historicalIds = document.corpusDocuments().stream().filter(source -> isHistoricalEvidence(document, source))
                .map(SFMReleaseReviewV1.CorpusDocument::documentRevisionId).collect(java.util.stream.Collectors.toSet());
        var corpusById = new HashMap<String, SFMReleaseReviewV1.CorpusDocument>();
        var currentContentKeys = new HashSet<String>();
        for (var source : document.corpusDocuments()) {
            corpusById.put(source.documentRevisionId(), source);
            if (!historicalIds.contains(source.documentRevisionId())
                    && source.materialization() == SFMReleaseReviewV1.Materialization.COMPLETE
                    && source.snapshotSide() == SFMReleaseReviewV1.SnapshotSide.AFTER)
                currentContentKeys.add(source.path() + "\0" + source.sha256());
        }
        var answer = new ArrayList<ApprovalEvidence>();
        for (var comment : document.reviewSession().comments()) {
            var tags = SFMReviewSessionV1Kernel.derivedHashtags(comment.text());
            if (!tags.contains(approval)) continue;
            var evaluation = context.evaluations().get(comment.id());
            var targets = evaluation.ranges().isEmpty() ? originalRanges(comment.target()) : evaluation.ranges();
            var historical = targets.stream().filter(range -> historicalIds.contains(range.documentRevisionId())).toList();
            var eligible = hashtagRanges(document, Map.of(comment.id(), comment), context.evaluations(), approval, true);
            var effective = SFMReviewSessionV1Kernel.intersection(required,
                    SFMReviewSessionV1Kernel.difference(eligible, blockers));
            String explanation;
            if (tags.contains("#archived")) explanation = "Archived approval; does not count toward current completion";
            else if (!effective.isEmpty()) explanation = historical.isEmpty()
                    ? "Covers exact current changed bytes; this alone does not imply a complete file approval"
                    : "Partly covers current changed bytes; other targets are retained historical evidence";
            else if (!historical.isEmpty()) {
                boolean identicalCurrentBytes = historical.stream().map(range -> corpusById.get(range.documentRevisionId()))
                        .filter(Objects::nonNull).anyMatch(old -> old.materialization() == SFMReleaseReviewV1.Materialization.COMPLETE
                                && currentContentKeys.contains(old.path() + "\0" + old.sha256()));
                explanation = identicalCurrentBytes
                        ? "Retained historical approval; identical bytes exist at the same current path, but snapshot identity changed. Approval was not transferred"
                        : "Retained historical approval; does not approve the current source revision";
            }
            else if (eligible.isEmpty()) explanation = "Not eligible for current approval under exact-target/migration policy; evaluator=" + evaluation.status();
            else if (!SFMReviewSessionV1Kernel.intersection(required, eligible).isEmpty())
                explanation = "Current approval is excluded by blocking comments";
            else explanation = "Exact target is outside the current changed surface; no current work is approved by it";
            String targetDescription = evaluation.ranges().isEmpty()
                    ? "Original literal witness; unresolved target, not current coverage"
                    : "Evaluator target · " + evaluation.status();
            answer.add(new ApprovalEvidence(comment.id(), explanation, targetDescription, targets, effective, historical));
        }
        return List.copyOf(answer);
    }

    private static boolean isHistoricalEvidence(SFMReleaseReviewV1 document, SFMReleaseReviewV1.CorpusDocument corpus) {
        if (!SFMReleaseReviewV1.OBSERVATION_SCHEMA.equals(document.schema())
                || !SFMReleaseReviewV1.EVIDENCE_OWNER.equals(corpus.sourceOwner())
                || corpus.snapshotSide() != SFMReleaseReviewV1.SnapshotSide.AFTER
                || !corpus.laneId().equals("review-evidence:" + sha256(corpus.documentRevisionId().getBytes(StandardCharsets.UTF_8)))
                || !corpus.sourceLocator().equals("review-evidence://sha256/" + corpus.sha256())) return false;
        return document.reviewSession().revisionLanes().stream().anyMatch(lane ->
                lane.id().equals(corpus.laneId()) && lane.repository().id().equals(SFMReleaseReviewV1.EVIDENCE_OWNER)
                        && lane.before().id().equals("evidence:empty") && lane.before().documents().isEmpty()
                        && lane.after().id().equals("evidence:sha256:" + corpus.sha256())
                        && switch (corpus.materialization()) {
                    case COMPLETE -> lane.after().documents().size() == 1
                            && lane.after().documents().get(0).id().equals(corpus.documentRevisionId());
                    case MISSING -> lane.after().documents().isEmpty();
                    case PARTIAL -> false;
                });
    }

    public static void validate(SFMReleaseReviewV1 document) {
        Objects.requireNonNull(document, "document");
        SFMReviewSessionV2Kernel.evaluateAll(document.reviewSession());
        if (!SFMReleaseReviewV1.SCHEMA.equals(document.schema())) {
            var identifiers = new ArrayList<String>();
            document.namedQueries().forEach(query -> identifiers.add(query.id()));
            document.reviewSession().revisionLanes().forEach(lane -> identifiers.add(lane.id()));
            for (var identifier : identifiers) if (identifier.equalsIgnoreCase("candidate")
                    || document.repositoryBindings().stream().anyMatch(binding -> binding.matchesSnapshotAtom(identifier)))
                throw new IllegalArgumentException("Query/lane identifier collides with immutable source address: " + identifier);
        }

        Map<String, SFMReviewSessionV1.RevisionLane> sessionLanes = new HashMap<>();
        for (SFMReviewSessionV1.RevisionLane lane : document.reviewSession().revisionLanes()) {
            if (sessionLanes.put(lane.id(), lane) != null) {
                throw new IllegalArgumentException("Duplicate embedded review lane " + lane.id());
            }
        }
        Map<String, SFMReleaseReviewV1.RepositoryBinding> bindings = new HashMap<>();
        for (SFMReleaseReviewV1.RepositoryBinding binding : document.repositoryBindings()) {
            if (!sessionLanes.containsKey(binding.laneId())) {
                throw new IllegalArgumentException("Repository binding has no embedded lane " + binding.laneId());
            }
            var lane = sessionLanes.get(binding.laneId());
            if (!SFMReleaseReviewV1.SCHEMA.equals(document.schema())
                    && ((!binding.beforeCommit().equals(lane.before().id())
                    && !("git:" + binding.beforeCommit()).equals(lane.before().id()))
                    || (!binding.candidateIdentity().equals(lane.after().id())
                    && !(binding.workingTreeCapture().isEmpty()
                    && ("git:" + binding.candidateCommit()).equals(lane.after().id())))))
                throw new IllegalArgumentException("Repository source identity disagrees with embedded snapshots");
            binding.workingTreeCapture().ifPresent(capture -> capture.validateAgainst(lane, document));
            bindings.put(binding.laneId(), binding);
        }

        Map<String, SFMReviewSessionV1.DocumentRevision> sessionDocuments = embeddedDocuments(document.reviewSession());
        Map<String, SFMReleaseReviewV1.CorpusDocument> corpusByRevision = new HashMap<>();
        for (SFMReleaseReviewV1.CorpusDocument corpus : document.corpusDocuments()) {
            if (!bindings.containsKey(corpus.laneId()) && !isHistoricalEvidence(document, corpus)) {
                throw new IllegalArgumentException("Corpus document has no repository binding " + corpus.id());
            }
            SFMReviewSessionV1.DocumentRevision embedded = sessionDocuments.get(corpus.documentRevisionId());
            if (corpus.materialization() == SFMReleaseReviewV1.Materialization.COMPLETE && embedded == null) {
                throw new IllegalArgumentException("Complete corpus document is absent from embedded session " + corpus.id());
            }
            if (embedded != null) {
                if (!embedded.path().equals(corpus.path()) || !embedded.sha256().equals(corpus.sha256())) {
                    throw new IllegalArgumentException("Corpus witness disagrees with embedded document " + corpus.id());
                }
            }
            SFMReleaseReviewV1.CorpusDocument previous = corpusByRevision.put(corpus.documentRevisionId(), corpus);
            if (previous != null && !previous.equals(corpus)) {
                throw new IllegalArgumentException("Document revision is bound to multiple corpus entries "
                        + corpus.documentRevisionId());
            }
        }

        Map<String, SFMReleaseReviewV1.ProducerGeneration> producers = new HashMap<>();
        document.producerGenerations().forEach(value -> producers.put(value.producerId(), value));
        Set<String> unitIds = new HashSet<>();
        for (SFMReleaseReviewV1.ReviewUnit unit : document.reviewUnits()) {
            unitIds.add(unit.id());
            if (!bindings.containsKey(unit.laneId())) {
                throw new IllegalArgumentException("Review unit has no repository binding " + unit.id());
            }
            unit.beforeDocumentRevisionId().ifPresent(id -> requireCorpusRevision(corpusByRevision, id, unit.id()));
            unit.afterDocumentRevisionId().ifPresent(id -> requireCorpusRevision(corpusByRevision, id, unit.id()));
            if (SFMReleaseReviewV1.OBSERVATION_SCHEMA.equals(document.schema())) {
                for (var revision : List.of(unit.beforeDocumentRevisionId(), unit.afterDocumentRevisionId())) {
                    if (revision.isPresent() && !bindings.containsKey(corpusByRevision.get(revision.get()).laneId()))
                        throw new IllegalArgumentException("Current review unit cannot use historical evidence");
                }
            }
            if (!producers.containsKey(unit.producerId())) {
                throw new IllegalArgumentException("Review unit has no producer declaration " + unit.id());
            }
        }
        document.resumeState().currentUnitId().ifPresent(id -> requireUnit(unitIds, id, "current resume unit"));
        document.resumeState().deferredUnitIds().forEach(id -> requireUnit(unitIds, id, "deferred unit"));

        Set<String> queryIds = document.namedQueries().stream().map(SFMReleaseReviewV1.NamedQuery::id)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> commentIds = document.reviewSession().comments().stream()
                .map(SFMReviewSessionV2.Comment::id).collect(java.util.stream.Collectors.toSet());
        Set<String> selectorIds = new HashSet<>();
        for (SFMReleaseReviewV1.CommentSelectorBinding binding : document.selectorBindings()) {
            if (!commentIds.contains(binding.commentId())) {
                throw new IllegalArgumentException("Selector binding references unknown comment " + binding.commentId());
            }
            selectorIds.add(binding.selectedProposal().id());
            for (SFMReleaseReviewV1.PinnedSelectionRange range : binding.capturedSelection().ranges()) {
                SFMReleaseReviewV1.CorpusDocument corpus = corpusByRevision.get(range.documentRevisionId());
                if (corpus == null || !corpus.sha256().equals(range.documentSha256())) {
                    throw new IllegalArgumentException("Pinned selection disagrees with corpus revision "
                            + range.documentRevisionId());
                }
            }
        }
        if (selectorIds.size() != document.selectorBindings().size()) {
            throw new IllegalArgumentException("Duplicate selected-proposal id");
        }
        for (SFMReleaseReviewV1.MigrationReport report : document.migrationReports()) {
            if (!selectorIds.contains(report.sourceSelectorId())) {
                throw new IllegalArgumentException("Migration references unknown selector " + report.sourceSelectorId());
            }
            report.decisionCommentId().ifPresent(id -> {
                if (!commentIds.contains(id)) {
                    throw new IllegalArgumentException("Migration references unknown decision comment " + id);
                }
            });
        }
        document.resumeState().activeQueryId().ifPresent(id -> {
            if (!queryIds.contains(id)) throw new IllegalArgumentException("Unknown active named query " + id);
        });
        for (SFMReleaseReviewV1.NamedQuery query : document.namedQueries()) {
            SFMReleaseReviewQuery.parse(query.expression());
        }

        // Resolve every named query once so unknown atoms and cycles fail at load time.
        Context context = contextUnchecked(document);
        for (SFMReleaseReviewV1.NamedQuery query : document.namedQueries()) {
            evaluateExpression(context, SFMReleaseReviewQuery.parse(query.expression()), false,
                    new ArrayDeque<>(List.of(query.id())));
        }
    }

    public static QueryResult query(SFMReleaseReviewV1 document, String expression) {
        validate(document);
        SFMReleaseReviewQuery.Expression parsed = SFMReleaseReviewQuery.parse(expression);
        Context context = contextUnchecked(document);
        Set<String> ids = evaluateExpression(context, parsed, false, new ArrayDeque<>());
        return new QueryResult(expression, parsed.normalized(), sorted(ids), context.surfaceCoverage().stream()
                .filter(unit -> ids.contains(unit.reviewUnitId())).toList());
    }

    public static CompletionReport completion(SFMReleaseReviewV1 document) {
        validate(document);
        Context context = contextUnchecked(document);
        Set<String> remaining = difference(context.domain(), context.approvedEffective());
        List<String> diagnostics = new ArrayList<>();
        if (!context.staleProducer().isEmpty()) diagnostics.add("Producer generations are stale");
        if (!context.blocking().isEmpty()) diagnostics.add("Blocking comments remain");
        if (!context.suspended().isEmpty()) diagnostics.add("Raw approval is suspended by current evaluation");
        if (!context.missing().isEmpty()) diagnostics.add("Comments have missing or ambiguous targets");
        if (!remaining.isEmpty()) diagnostics.add("Review units remain without effective approval");
        if (context.surfaceCoverage().stream().anyMatch(unit -> !unit.bounded()))
            diagnostics.add("Some review operations lack a fully materialized nonempty source domain; byte approval cannot complete them");
        boolean staleQueryRevision = activeQueryRevisionStale(document);
        if (staleQueryRevision) {
            diagnostics.add("Active named-query revision differs from its persisted work-queue expression");
        }

        String semanticHash = semanticStateHash(document);
        boolean matchingAttestation = document.completionAttestations().stream()
                .anyMatch(value -> value.reviewSemanticStateHash().equals(semanticHash));
        boolean staleAttestation = !document.completionAttestations().isEmpty() && !matchingAttestation;
        if (staleAttestation) diagnostics.add("Completion attestations do not match the current semantic state");
        boolean stale = !context.staleProducer().isEmpty() || staleAttestation || staleQueryRevision;
        boolean ready = !stale && remaining.isEmpty() && context.blocking().isEmpty()
                && context.suspended().isEmpty() && context.missing().isEmpty();
        boolean attested = ready && matchingAttestation;
        CompletionStatus status = stale
                ? CompletionStatus.STALE
                : attested
                ? CompletionStatus.COMPLETE
                : ready
                ? CompletionStatus.READY_FOR_MAINTAINER_ATTESTATION
                : CompletionStatus.IN_PROGRESS;
        CompletionWitnesses witnesses = new CompletionWitnesses(
                sorted(context.domain()), sorted(context.approvedRaw()), sorted(context.approvedEffective()),
                sorted(remaining), sorted(context.blocking()), sorted(context.suspended()), sorted(context.missing()),
                sorted(context.deferred()), sorted(context.unsupported()), sorted(context.staleProducer())
        );
        return new CompletionReport(
                status, semanticHash, context.domain().size(), context.approvedRaw().size(),
                context.approvedEffective().size(), remaining.size(), context.blocking().size(),
                context.suspended().size(), context.missing().size(), context.deferred().size(),
                context.unsupported().size(), context.staleProducer().size(), witnesses, diagnostics, context.surfaceCoverage()
        );
    }

    public static String semanticStateHash(SFMReleaseReviewV1 document) {
        SFMReviewSessionV2 semanticSession = new SFMReviewSessionV2(
                document.reviewSession().schema(),
                document.reviewSession().id(),
                document.reviewSession().title(),
                document.reviewSession().coordinateSystem(),
                document.reviewSession().revisionLanes(),
                document.reviewSession().comments(),
                List.of(),
                document.reviewSession().completionPolicy()
        );
        SFMReleaseReviewV1 projection = new SFMReleaseReviewV1(
                document.schema(), semanticSession, document.repositoryBindings(), document.corpusDocuments(),
                document.reviewUnits(), document.selectorBindings(), document.migrationReports(),
                document.namedQueries(), SFMReleaseReviewV1.ResumeState.empty(),
                document.producerGenerations(), List.of()
        );
        String canonical = SFMReleaseReviewV1Codec.write(projection);
        String domain = SFMReleaseReviewV1.OBSERVATION_SCHEMA.equals(document.schema())
                ? "sfm.release-review-observation/3:semantic-state:exact-coverage/2\n"
                : SFMReleaseReviewV1.WORKING_TREE_SCHEMA.equals(document.schema())
                ? SFMReleaseReviewV1.WORKING_TREE_HASH_DOMAIN : SFMReleaseReviewV1.HASH_DOMAIN;
        return sha256((domain + canonical).getBytes(StandardCharsets.UTF_8));
    }

    /** Whether an activated named query was edited after its work-queue expression was captured. */
    public static boolean activeQueryRevisionStale(SFMReleaseReviewV1 document) {
        Optional<String> activeId = document.resumeState().activeQueryId();
        Optional<String> captured = document.resumeState().activeQueryExpression();
        if (activeId.isEmpty() || captured.isEmpty()) return false;
        SFMReleaseReviewV1.NamedQuery current = document.namedQueries().stream()
                .filter(query -> query.id().equals(activeId.orElseThrow()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown active named query " + activeId.orElseThrow()));
        return !SFMReleaseReviewQuery.parse(current.expression()).normalized()
                .equals(SFMReleaseReviewQuery.parse(captured.orElseThrow()).normalized());
    }

    private static Context contextUnchecked(SFMReleaseReviewV1 document) {
        List<SFMReviewSessionV2Kernel.Evaluation> evaluationList = SFMReviewSessionV2Kernel.evaluateAll(
                document.reviewSession());
        Map<String, SFMReviewSessionV2.Comment> comments = new LinkedHashMap<>();
        document.reviewSession().comments().forEach(value -> comments.put(value.id(), value));
        Map<String, SFMReviewSessionV2Kernel.Evaluation> evaluations = new LinkedHashMap<>();
        evaluationList.forEach(value -> evaluations.put(value.commentId(), value));

        Set<String> domain = unitIds(document.reviewUnits());
        String approval = normalizeHashtag(document.reviewSession().completionPolicy().approvalHashtag());
        Set<String> approvedRaw = hashtagUnits(document, comments, evaluations, approval, false);
        List<SFMReviewSessionV1Kernel.Range> approvalRanges = hashtagRanges(document, comments, evaluations, approval, true);
        Set<String> resolvedApproval = unitsIntersecting(document, approvalRanges);
        Set<String> blocking = new LinkedHashSet<>();
        List<SFMReviewSessionV1Kernel.Range> blockingRanges = new ArrayList<>();
        for (String tag : document.reviewSession().completionPolicy().blockingHashtags()) {
            blocking.addAll(hashtagUnits(document, comments, evaluations, normalizeHashtag(tag), false));
            blockingRanges.addAll(hashtagRanges(document, comments, evaluations, normalizeHashtag(tag), false));
        }
        List<SFMReleaseReviewCoverage.UnitCoverage> coverage = SFMReleaseReviewCoverage.evaluate(document, approvalRanges, blockingRanges);
        Set<String> covered = new LinkedHashSet<>();
        coverage.stream().filter(SFMReleaseReviewCoverage.UnitCoverage::fullyApproved)
                .map(SFMReleaseReviewCoverage.UnitCoverage::reviewUnitId).forEach(covered::add);
        Set<String> approvedEffective = difference(covered, blocking);
        // Partial but exact approval is not a suspended/stale approval.
        Set<String> suspended = difference(approvedRaw, difference(resolvedApproval, blocking));
        Set<String> missing = unresolvedUnits(document, comments, evaluations);
        Set<String> deferred = new LinkedHashSet<>(document.resumeState().deferredUnitIds());
        Set<String> unsupported = document.reviewUnits().stream()
                .filter(value -> value.surfaceKind() == SFMReleaseReviewV1.SurfaceKind.UNSUPPORTED)
                .map(SFMReleaseReviewV1.ReviewUnit::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<String, String> generations = new HashMap<>();
        document.producerGenerations().forEach(value -> generations.put(value.producerId(), value.generation()));
        Set<String> staleProducer = document.reviewUnits().stream()
                .filter(value -> !value.producerGeneration().equals(generations.get(value.producerId())))
                .map(SFMReleaseReviewV1.ReviewUnit::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<String, SFMReleaseReviewV1.NamedQuery> queries = new HashMap<>();
        document.namedQueries().forEach(value -> queries.put(value.id().toLowerCase(Locale.ROOT), value));
        return new Context(document, queries, comments, evaluations, domain, approvedRaw, approvedEffective,
                blocking, suspended, missing, deferred, unsupported, staleProducer, coverage);
    }

    private static Set<String> evaluateExpression(Context context,
                                                   SFMReleaseReviewQuery.Expression expression,
                                                   boolean effective,
                                                   ArrayDeque<String> queryStack) {
        if (expression instanceof SFMReleaseReviewQuery.Atom atom) {
            return atom(context, atom.value(), effective, queryStack);
        }
        if (expression instanceof SFMReleaseReviewQuery.Effective child) {
            return evaluateExpression(context, child.expression(), true, queryStack);
        }
        SFMReleaseReviewQuery.Binary binary = (SFMReleaseReviewQuery.Binary) expression;
        Set<String> left = evaluateExpression(context, binary.left(), effective, queryStack);
        Set<String> right = evaluateExpression(context, binary.right(), effective, queryStack);
        return switch (binary.operator()) {
            case UNION -> union(left, right);
            case INTERSECT -> intersection(left, right);
            case DIFFERENCE -> difference(left, right);
        };
    }

    private static Set<String> atom(Context context, String value, boolean effective, ArrayDeque<String> queryStack) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("#")) {
            String hashtag = normalizeHashtag(lower);
            String approval = normalizeHashtag(
                    context.document().reviewSession().completionPolicy().approvalHashtag());
            if (effective && hashtag.equals(approval)) return copySet(context.approvedEffective());
            return hashtagUnits(context.document(), context.comments(), context.evaluations(),
                    hashtag, effective);
        }
        switch (lower) {
            case "head", "changed", "changed-domain" -> { return copySet(context.domain()); }
            case "approved-raw" -> { return copySet(context.approvedRaw()); }
            case "approved-effective" -> { return copySet(context.approvedEffective()); }
            case "remaining", "uncovered" -> { return difference(context.domain(), context.approvedEffective()); }
            case "blocking" -> { return copySet(context.blocking()); }
            case "suspended" -> { return copySet(context.suspended()); }
            case "missing" -> { return copySet(context.missing()); }
            case "deferred" -> { return copySet(context.deferred()); }
            case "unsupported" -> { return copySet(context.unsupported()); }
            case "stale-producer" -> { return copySet(context.staleProducer()); }
            default -> {
            }
        }
        Set<String> lane = context.document().reviewUnits().stream()
                .filter(unit -> unit.laneId().equalsIgnoreCase(value))
                .map(SFMReleaseReviewV1.ReviewUnit::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        boolean configuredLane = context.document().repositoryBindings().stream()
                .anyMatch(binding -> binding.laneId().equalsIgnoreCase(value))
                || context.document().reviewSession().revisionLanes().stream()
                .anyMatch(revisionLane -> revisionLane.id().equalsIgnoreCase(value));
        if (configuredLane) return lane;

        SFMReleaseReviewV1.NamedQuery named = context.namedQueries().get(lower);
        if (named != null) {
            if (queryStack.stream().anyMatch(lower::equalsIgnoreCase)) {
                throw new IllegalArgumentException("Named-query cycle " + queryStack + " -> " + named.id());
            }
            queryStack.addLast(named.id());
            try {
                return evaluateExpression(context, SFMReleaseReviewQuery.parse(named.expression()), effective, queryStack);
            } finally {
                queryStack.removeLast();
            }
        }
        // Preserve existing v1 lane/named-query meanings before adding source-address aliases.
        if (lower.equals("candidate")) return copySet(context.domain());
        Set<String> sourceLanes = context.document().repositoryBindings().stream()
                .filter(binding -> binding.matchesSnapshotAtom(value)).map(SFMReleaseReviewV1.RepositoryBinding::laneId)
                .collect(java.util.stream.Collectors.toSet());
        if (!sourceLanes.isEmpty()) return context.document().reviewUnits().stream()
                .filter(unit -> sourceLanes.contains(unit.laneId())).map(SFMReleaseReviewV1.ReviewUnit::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        throw new IllegalArgumentException("Unknown release-review query atom '" + value + "'");
    }

    private static Set<String> hashtagUnits(
            SFMReleaseReviewV1 document,
            Map<String, SFMReviewSessionV2.Comment> comments,
            Map<String, SFMReviewSessionV2Kernel.Evaluation> evaluations,
            String hashtag,
            boolean effective
    ) {
        return unitsIntersecting(document, hashtagRanges(document, comments, evaluations, hashtag, effective));
    }

    private static List<SFMReviewSessionV1Kernel.Range> hashtagRanges(
            SFMReleaseReviewV1 document,
            Map<String, SFMReviewSessionV2.Comment> comments,
            Map<String, SFMReviewSessionV2Kernel.Evaluation> evaluations,
            String hashtag, boolean effective
    ) {
        List<SFMReviewSessionV1Kernel.Range> answer = new ArrayList<>();
        String approval = normalizeHashtag(document.reviewSession().completionPolicy().approvalHashtag());
        Set<String> blockingTags = document.reviewSession().completionPolicy().blockingHashtags().stream()
                .map(SFMReleaseReviewKernel::normalizeHashtag)
                .collect(java.util.stream.Collectors.toSet());
        for (SFMReviewSessionV2.Comment comment : comments.values()) {
            List<String> tags = SFMReviewSessionV1Kernel.derivedHashtags(comment.text());
            if (tags.contains("#archived")) continue;
            if (!tags.contains(hashtag)) continue;
            SFMReviewSessionV2Kernel.Evaluation evaluation = evaluations.get(comment.id());
            if (effective) {
                if (!(comment.target() instanceof SFMReviewSessionV2.CommittedReviewTarget committed)
                        || committed.candidatePromotion().isPresent()) continue;
                if (hasUnresolvedMigrationEvidence(document, comment.id())) continue;
                boolean resolved = evaluation.status() == SFMReviewSessionV2Kernel.Status.RESOLVED_EXACTLY
                        || (!hashtag.equals(approval)
                        && evaluation.status() == SFMReviewSessionV2Kernel.Status.RESOLVED_WITH_RELOCATION);
                if (!resolved) continue;
                if (hashtag.equals(approval) && tags.stream().anyMatch(blockingTags::contains)) continue;
            }
            List<SFMReviewSessionV1Kernel.Range> ranges = effective || !evaluation.ranges().isEmpty()
                    ? evaluation.ranges()
                    : originalRanges(comment.target());
            answer.addAll(ranges);
        }
        return SFMReviewSessionV1Kernel.normalize(answer);
    }

    private static Set<String> unresolvedUnits(
            SFMReleaseReviewV1 document,
            Map<String, SFMReviewSessionV2.Comment> comments,
            Map<String, SFMReviewSessionV2Kernel.Evaluation> evaluations
    ) {
        Set<String> answer = new LinkedHashSet<>();
        for (SFMReviewSessionV2.Comment comment : comments.values()) {
            if (SFMReviewSessionV1Kernel.derivedHashtags(comment.text()).contains("#archived")) continue;
            SFMReviewSessionV2Kernel.Evaluation evaluation = evaluations.get(comment.id());
            if (evaluation.status() == SFMReviewSessionV2Kernel.Status.RESOLVED_EXACTLY
                    || evaluation.status() == SFMReviewSessionV2Kernel.Status.RESOLVED_WITH_RELOCATION
                    || evaluation.status() == SFMReviewSessionV2Kernel.Status.CANDIDATE_PINNED) continue;
            answer.addAll(unitsIntersecting(document, originalRanges(comment.target())));
        }
        for (SFMReleaseReviewV1.MigrationReport report : document.migrationReports()) {
            if (!migrationDecisionPending(report) || !migrationCountsAsMissing(report.candidateEvaluation().status())) {
                continue;
            }
            document.selectorBindings().stream()
                    .filter(binding -> binding.selectedProposal().id().equals(report.sourceSelectorId()))
                    .map(SFMReleaseReviewV1.CommentSelectorBinding::commentId)
                    .map(comments::get)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .ifPresent(comment -> answer.addAll(unitsIntersecting(
                            document, originalRanges(comment.target()))));
        }
        return answer;
    }

    private static boolean hasUnresolvedMigrationEvidence(
            SFMReleaseReviewV1 document,
            String commentId
    ) {
        Optional<String> selectorId = document.selectorBindings().stream()
                .filter(binding -> binding.commentId().equals(commentId))
                .map(binding -> binding.selectedProposal().id())
                .findFirst();
        if (selectorId.isEmpty()) return false;
        return document.migrationReports().stream().anyMatch(report ->
                report.sourceSelectorId().equals(selectorId.orElseThrow())
                        && migrationDecisionPending(report)
                        && report.candidateEvaluation().status() != SFMReleaseReviewV1.EvaluationStatus.EXACT);
    }

    private static boolean migrationDecisionPending(SFMReleaseReviewV1.MigrationReport report) {
        return report.decision() == SFMReleaseReviewV1.MigrationDecision.UNRESOLVED
                || report.decision() == SFMReleaseReviewV1.MigrationDecision.DEFERRED;
    }

    private static boolean migrationCountsAsMissing(SFMReleaseReviewV1.EvaluationStatus status) {
        return status == SFMReleaseReviewV1.EvaluationStatus.AMBIGUOUS
                || status == SFMReleaseReviewV1.EvaluationStatus.MISSING
                || status == SFMReleaseReviewV1.EvaluationStatus.INVALID
                || status == SFMReleaseReviewV1.EvaluationStatus.SCOPE_MISSING;
    }

    private static List<SFMReviewSessionV1Kernel.Range> originalRanges(SFMReviewSessionV2.CommentTarget target) {
        if (!(target instanceof SFMReviewSessionV2.CommittedReviewTarget committed)) return List.of();
        List<SFMReviewSessionV1Kernel.Range> answer = new ArrayList<>();
        literalRanges(committed.selectionRule(), answer);
        return answer;
    }

    private static void literalRanges(SFMReviewSessionV1.SelectionRule rule,
                                      List<SFMReviewSessionV1Kernel.Range> answer) {
        if (rule instanceof SFMReviewSessionV1.LiteralUtf8Range literal) {
            answer.add(new SFMReviewSessionV1Kernel.Range(
                    literal.documentRevisionId(), literal.startByte(), literal.endByte()));
        } else if (rule instanceof SFMReviewSessionV1.Union union) {
            union.rules().forEach(child -> literalRanges(child, answer));
        } else if (rule instanceof SFMReviewSessionV1.Intersection intersection) {
            intersection.rules().forEach(child -> literalRanges(child, answer));
        } else if (rule instanceof SFMReviewSessionV1.Difference difference) {
            literalRanges(difference.include(), answer);
            difference.exclude().forEach(child -> literalRanges(child, answer));
        }
    }

    private static Set<String> unitsIntersecting(SFMReleaseReviewV1 document,
                                                 List<SFMReviewSessionV1Kernel.Range> ranges) {
        Set<String> answer = new LinkedHashSet<>();
        for (SFMReleaseReviewV1.ReviewUnit unit : document.reviewUnits()) {
            for (SFMReviewSessionV1Kernel.Range range : ranges) {
                if (matches(unit.beforeDocumentRevisionId(), unit.beforeRanges(), range)
                        || matches(unit.afterDocumentRevisionId(), unit.afterRanges(), range)) {
                    answer.add(unit.id());
                    break;
                }
            }
        }
        return answer;
    }

    private static boolean matches(Optional<String> documentId,
                                   List<SFMReleaseReviewV1.Utf8Range> unitRanges,
                                   SFMReviewSessionV1Kernel.Range commentRange) {
        if (documentId.isEmpty() || !documentId.orElseThrow().equals(commentRange.documentRevisionId())) return false;
        if (unitRanges.isEmpty()) return true;
        return unitRanges.stream().anyMatch(unitRange -> overlaps(
                unitRange.startByte(), unitRange.endByte(), commentRange.startByte(), commentRange.endByte()));
    }

    private static boolean overlaps(int firstStart, int firstEnd, int secondStart, int secondEnd) {
        if (firstStart == firstEnd || secondStart == secondEnd) {
            return firstStart <= secondEnd && secondStart <= firstEnd;
        }
        return Math.max(firstStart, secondStart) < Math.min(firstEnd, secondEnd);
    }

    private static Map<String, SFMReviewSessionV1.DocumentRevision> embeddedDocuments(SFMReviewSessionV2 session) {
        Map<String, SFMReviewSessionV1.DocumentRevision> answer = new HashMap<>();
        for (SFMReviewSessionV1.RevisionLane lane : session.revisionLanes()) {
            for (SFMReviewSessionV1.DocumentRevision document : lane.before().documents()) {
                putDocument(answer, document);
            }
            for (SFMReviewSessionV1.DocumentRevision document : lane.after().documents()) {
                putDocument(answer, document);
            }
        }
        return answer;
    }

    private static void putDocument(Map<String, SFMReviewSessionV1.DocumentRevision> values,
                                    SFMReviewSessionV1.DocumentRevision document) {
        SFMReviewSessionV1.DocumentRevision previous = values.put(document.id(), document);
        if (previous != null && !previous.equals(document)) {
            throw new IllegalArgumentException("Duplicate embedded document revision " + document.id());
        }
    }

    private static void requireCorpusRevision(Map<String, SFMReleaseReviewV1.CorpusDocument> corpus,
                                              String id, String unitId) {
        if (!corpus.containsKey(id)) {
            throw new IllegalArgumentException("Review unit " + unitId + " references unknown corpus revision " + id);
        }
    }

    private static void requireUnit(Set<String> units, String id, String label) {
        if (!units.contains(id)) throw new IllegalArgumentException("Unknown " + label + " " + id);
    }

    private static String normalizeHashtag(String value) {
        String answer = value.toLowerCase(Locale.ROOT);
        return answer.startsWith("#") ? answer : "#" + answer;
    }

    private static Set<String> unitIds(Collection<SFMReleaseReviewV1.ReviewUnit> units) {
        return units.stream().map(SFMReleaseReviewV1.ReviewUnit::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<String> union(Set<String> left, Set<String> right) {
        Set<String> answer = copySet(left);
        answer.addAll(right);
        return answer;
    }

    private static Set<String> intersection(Set<String> left, Set<String> right) {
        Set<String> answer = copySet(left);
        answer.retainAll(right);
        return answer;
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> answer = copySet(left);
        answer.removeAll(right);
        return answer;
    }

    private static Set<String> copySet(Set<String> value) {
        return new LinkedHashSet<>(value);
    }

    private static List<String> sorted(Collection<String> values) {
        return values.stream().sorted().toList();
    }

    public static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
