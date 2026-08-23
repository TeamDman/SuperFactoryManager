package ca.teamdman.sfm.client.screen.review.explorer;

import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewCorpus;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewKernel;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewSurfaceV1;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import ca.teamdman.sfm.client.screen.review.comment.SFMCommentHashtags;
import ca.teamdman.sfm.client.screen.review.comment.SFMFixtureReviewCommentDataSource;
import ca.teamdman.sfm.client.screen.review.comment.SFMReviewCommentDataSource;
import ca.teamdman.sfm.client.screen.review.comment.SFMReviewCommentKernelDataSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Pure tree/navigation model shared by changes, comments, and hashtag explorers. */
public final class SFMReviewExplorerModel {
    public enum Kind {
        ROOT,
        FILE,
        LANE,
        REVISION,
        DIFF,
        REVIEW_UNIT,
        STATUS_CATEGORY,
        MIGRATION,
        COMMENT,
        HASHTAG,
        REGION,
        CANDIDATE_TARGET
    }

    public record SourceLeaf(
            String id,
            String title,
            String path,
            String text,
            boolean missing,
            Optional<String> documentRevisionId,
            Optional<String> sha256,
            Optional<SFMReleaseReviewV1.Utf8Range> targetRange,
            Optional<SFMReleaseReviewSurfaceV1.Recipe> generatedSurface
    ) {
        public SourceLeaf {
            Objects.requireNonNull(id);
            Objects.requireNonNull(title);
            Objects.requireNonNull(path);
            Objects.requireNonNull(text);
            Objects.requireNonNull(documentRevisionId, "documentRevisionId");
            Objects.requireNonNull(sha256, "sha256");
            Objects.requireNonNull(targetRange, "targetRange");
            Objects.requireNonNull(generatedSurface, "generatedSurface");
            if (documentRevisionId.isPresent() != sha256.isPresent()) {
                throw new IllegalArgumentException("Pinned review identity requires both revision id and SHA-256");
            }
            if (generatedSurface.isPresent() && (missing || documentRevisionId.isPresent() || !text.isEmpty())) {
                throw new IllegalArgumentException("Generated review leaves must be lazy and independent of one revision");
            }
            targetRange.ifPresent(range -> {
                if (range.endByte() > text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length) {
                    throw new IllegalArgumentException("Review leaf target range lies beyond its UTF-8 text");
                }
            });
        }

        public SourceLeaf(
                String id,
                String title,
                String path,
                String text,
                boolean missing,
                Optional<String> documentRevisionId,
                Optional<String> sha256,
                Optional<SFMReleaseReviewV1.Utf8Range> targetRange
        ) {
            this(id, title, path, text, missing, documentRevisionId, sha256, targetRange, Optional.empty());
        }

        public SourceLeaf(
                String id,
                String title,
                String path,
                String text,
                boolean missing,
                Optional<String> documentRevisionId,
                Optional<String> sha256
        ) {
            this(id, title, path, text, missing, documentRevisionId, sha256, Optional.empty(), Optional.empty());
        }

        public SourceLeaf(String id, String title, String path, String text, boolean missing) {
            this(id, title, path, text, missing, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }
    }

    /** Typed action payload retained independently from the human-readable row label. */
    public sealed interface NodeAction permits CandidateNavigation {
    }

    /** Exact immutable candidate identity used to reopen the pinned plan/route/frame. */
    public record CandidateNavigation(
            String commentId,
            SFMReviewSessionV2.CandidateTrajectoryTarget target
    ) implements NodeAction {
        public CandidateNavigation {
            Objects.requireNonNull(commentId, "commentId");
            Objects.requireNonNull(target, "target");
            if (commentId.isBlank()) throw new IllegalArgumentException("commentId must not be blank");
        }
    }

    public static final class Node {
        private final String id;
        private final String label;
        private final Kind kind;
        private final List<Node> children;
        private final SourceLeaf leaf;
        private final NodeAction action;
        private boolean expanded;

        private Node(
                String id,
                String label,
                Kind kind,
                List<Node> children,
                SourceLeaf leaf,
                NodeAction action,
                boolean expanded
        ) {
            this.id = Objects.requireNonNull(id);
            this.label = Objects.requireNonNull(label);
            this.kind = Objects.requireNonNull(kind);
            this.children = new ArrayList<>(children);
            this.leaf = leaf;
            this.action = action;
            this.expanded = expanded;
        }

        public String id() { return id; }
        public String label() { return label; }
        public Kind kind() { return kind; }
        public List<Node> children() { return List.copyOf(children); }
        public SourceLeaf leaf() { return leaf; }
        public Optional<NodeAction> action() { return Optional.ofNullable(action); }
        public boolean expanded() { return expanded; }
        public boolean expandable() { return !children.isEmpty(); }
        public void setExpanded(boolean expanded) { this.expanded = expanded; }
    }

    public record VisibleNode(Node node, int depth) {}

    /** UI-only tree state retained while an immutable review projection is rebuilt. */
    public record ViewState(String selectedNodeId, Set<String> knownNodeIds, Set<String> expandedNodeIds) {
        public ViewState {
            Objects.requireNonNull(selectedNodeId, "selectedNodeId");
            knownNodeIds = Set.copyOf(knownNodeIds);
            expandedNodeIds = Set.copyOf(expandedNodeIds);
            if (!knownNodeIds.containsAll(expandedNodeIds)) {
                throw new IllegalArgumentException("Expanded review nodes must belong to the captured tree");
            }
        }
    }

    public record LaneData(String id, String beforeSelector, String afterSelector, List<FileData> files) {
        public LaneData {
            files = List.copyOf(files);
        }
    }

    public record FileData(String path, String beforeText, String afterText) {}

    private record ReleasePair(
            String laneId,
            SFMReleaseReviewV1.ChangeOperation operation,
            Optional<String> pathBefore,
            Optional<String> pathAfter,
            Optional<SFMReleaseReviewV1.CorpusDocument> before,
            Optional<SFMReleaseReviewV1.CorpusDocument> after,
            String language,
            List<String> reviewUnitIds
    ) {
        private ReleasePair {
            Objects.requireNonNull(laneId, "laneId");
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(pathBefore, "pathBefore");
            Objects.requireNonNull(pathAfter, "pathAfter");
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(after, "after");
            Objects.requireNonNull(language, "language");
            reviewUnitIds = List.copyOf(reviewUnitIds);
        }
    }

    private final Node root;
    private int selectionIndex;

    private SFMReviewExplorerModel(Node root) {
        this.root = root;
    }

    public static SFMReviewExplorerModel changes(String beforeSelector, String afterSelector) {
        if (beforeSelector == null || beforeSelector.isBlank()) throw new IllegalArgumentException("before selector is blank");
        if (afterSelector == null || afterSelector.isBlank()) throw new IllegalArgumentException("after selector is blank");
        List<LaneData> lanes = List.of(
                new LaneData("1.19.2", beforeSelector, afterSelector, List.of(
                        new FileData("src/Example.java", "class Example {\n    void oldName() {}\n}\n",
                                "class Example {\n    void newName() {}\n}\n"),
                        new FileData("src/Added.java", null, "final class Added {}\n"),
                        new FileData("src/Deleted.java", "final class Deleted {}\n", null)
                )),
                new LaneData("1.19.4", beforeSelector, afterSelector, List.of(
                        new FileData("src/Example.java", "class Example {\n    void oldName() {}\n}\n",
                                "class Example {\n    void newName() { audit(); }\n}\n"),
                        new FileData("src/Added.java", null, "final class Added {}\n"),
                        new FileData("src/Deleted.java", "final class Deleted {}\n", null)
                ))
        );
        Map<String, Node> files = new TreeMap<>();
        for (LaneData lane : lanes) {
            for (FileData file : lane.files()) {
                files.computeIfAbsent(file.path(), path -> node("file/" + path, path, Kind.FILE, List.of(), null, true));
            }
        }
        for (Node fileNode : files.values()) {
            List<Node> laneNodes = new ArrayList<>();
            for (LaneData lane : lanes) {
                FileData file = lane.files().stream().filter(candidate -> candidate.path().equals(fileNode.label())).findFirst().orElseThrow();
                SourceLeaf before = revisionLeaf(lane, file, true);
                SourceLeaf after = revisionLeaf(lane, file, false);
                laneNodes.add(node(fileNode.id() + "/lane/" + lane.id(),
                        lane.id() + "  " + lane.beforeSelector() + " → " + lane.afterSelector(), Kind.LANE,
                        List.of(node(before.id(), before.title(), Kind.REVISION, List.of(), before, true),
                                node(after.id(), after.title(), Kind.REVISION, List.of(), after, true)), null, true));
            }
            fileNode.children.addAll(laneNodes);
        }
        return new SFMReviewExplorerModel(node("changes", "Changes · " + beforeSelector + " → " + afterSelector,
                Kind.ROOT, new ArrayList<>(files.values()), null, true));
    }

    public static SFMReviewExplorerModel comments() {
        return fixtureComments(false);
    }

    public static SFMReviewExplorerModel hashtags() {
        return fixtureComments(true);
    }

    /** Production projection over the canonical immutable V2 review session. */
    public static SFMReviewExplorerModel comments(SFMReviewSessionV2 session) {
        return sessionComments(session, false);
    }

    /** Production hashtag projection over the canonical immutable V2 review session. */
    public static SFMReviewExplorerModel hashtags(SFMReviewSessionV2 session) {
        return sessionComments(session, true);
    }

    /** Complete pinned before/after plus lazy source-mapped diff projection. */
    public static SFMReviewExplorerModel releaseChanges(SFMReleaseReviewV1 review) {
        Objects.requireNonNull(review, "review");
        SFMReleaseReviewCorpus corpus = SFMReleaseReviewCorpus.from(review);
        Map<String, SFMReleaseReviewV1.CorpusDocument> documents = new LinkedHashMap<>();
        review.corpusDocuments().forEach(document -> documents.put(document.documentRevisionId(), document));
        Map<String, List<SFMReleaseReviewV1.ReviewUnit>> groupedUnits = new TreeMap<>();
        for (SFMReleaseReviewV1.ReviewUnit unit : review.reviewUnits()) {
            groupedUnits.computeIfAbsent(releasePairGroupingKey(unit), ignored -> new ArrayList<>()).add(unit);
        }
        Map<String, SFMReleaseReviewV1.RepositoryBinding> bindings = new LinkedHashMap<>();
        review.repositoryBindings().forEach(binding -> bindings.put(binding.laneId(), binding));
        Map<String, List<ReleasePair>> pairsByFile = new TreeMap<>();
        Set<String> pairedRevisionIds = new LinkedHashSet<>();
        for (List<SFMReleaseReviewV1.ReviewUnit> values : groupedUnits.values()) {
            ReleasePair pair = releasePair(values, documents);
            pair.before().ifPresent(document -> pairedRevisionIds.add(document.documentRevisionId()));
            pair.after().ifPresent(document -> pairedRevisionIds.add(document.documentRevisionId()));
            pairsByFile.computeIfAbsent(releasePairLabel(pair), ignored -> new ArrayList<>()).add(pair);
        }
        Map<String, List<SFMReleaseReviewV1.CorpusDocument>> unpairedCorpus = new TreeMap<>();
        for (SFMReleaseReviewV1.CorpusDocument document : review.corpusDocuments()) {
            if (pairedRevisionIds.contains(document.documentRevisionId())) continue;
            String key = document.laneId() + "\u0000" + document.path();
            unpairedCorpus.computeIfAbsent(key, ignored -> new ArrayList<>()).add(document);
        }
        for (List<SFMReleaseReviewV1.CorpusDocument> values : unpairedCorpus.values()) {
            ReleasePair pair = releaseCorpusPair(values);
            pairsByFile.computeIfAbsent(releasePairLabel(pair), ignored -> new ArrayList<>()).add(pair);
        }
        ArrayList<Node> files = new ArrayList<>();
        for (List<ReleasePair> filePairs : pairsByFile.values()) {
            filePairs.sort(Comparator.comparing(ReleasePair::laneId));
            String fileLabel = releasePairLabel(filePairs.get(0));
            List<Node> lanes = new ArrayList<>();
            for (ReleasePair pair : filePairs) {
                String laneId = pair.laneId();
                SFMReleaseReviewV1.RepositoryBinding binding = bindings.get(laneId);
                String beforeLabel = binding == null ? "before" : binding.beforeLabel();
                String afterLabel = binding == null ? "after" : binding.afterLabel();
                SourceLeaf beforeLeaf = releaseLeaf(corpus, laneId, pair.pathBefore().orElse(fileLabel),
                        pair.before().orElse(null), true, "");
                SourceLeaf afterLeaf = releaseLeaf(corpus, laneId, pair.pathAfter().orElse(fileLabel),
                        pair.after().orElse(null), false, "");
                Optional<SFMReleaseReviewSurfaceV1.FilePair> surfacePair = releaseSurfacePair(corpus, pair);
                SourceLeaf textDiff = releaseDiffLeaf(
                        pair, surfacePair, SFMReleaseReviewSurfaceV1.SurfaceKind.TEXT_DIFF, "text diff");
                SourceLeaf structuredDiff = releaseDiffLeaf(
                        pair, surfacePair, SFMReleaseReviewSurfaceV1.SurfaceKind.JAVA_STRUCTURED_DIFF,
                        "structured diff");
                lanes.add(node(
                        "release/lane/" + laneId + "/" + stableId(releaseFileGroupingKey(pair)),
                        laneId + "  " + beforeLabel + " → " + afterLabel,
                        Kind.LANE,
                        List.of(
                                node(beforeLeaf.id(), beforeLeaf.title(), Kind.REVISION, List.of(), beforeLeaf, true),
                                node(afterLeaf.id(), afterLeaf.title(), Kind.REVISION, List.of(), afterLeaf, true),
                                node(textDiff.id(), textDiff.title(), Kind.DIFF, List.of(), textDiff, true),
                                node(structuredDiff.id(), structuredDiff.title(), Kind.DIFF,
                                        List.of(), structuredDiff, true)
                        ),
                        null,
                        false
                ));
            }
            files.add(node("release/file/" + stableId(releaseFileGroupingKey(filePairs.get(0))),
                    fileLabel, Kind.FILE, lanes, null, false));
        }
        return new SFMReviewExplorerModel(node(
                "release/changes",
                "Release changes · " + pinnedRangeLabel(review),
                Kind.ROOT,
                files,
                null,
                true
        ));
    }

    /** Query-projected stable work queue over the portable release-review completion domain. */
    public static SFMReviewExplorerModel releaseQuery(SFMReleaseReviewV1 review, String expression) {
        Objects.requireNonNull(review, "review");
        SFMReleaseReviewKernel.QueryResult result = SFMReleaseReviewKernel.query(review, expression);
        SFMReviewExplorerModel model = new SFMReviewExplorerModel(node(
                "release/query",
                "Review queue · " + result.normalizedExpression(),
                Kind.ROOT,
                releaseUnitRows(review, result.reviewUnitIds()),
                null,
                true
        ));
        review.resumeState().currentUnitId().ifPresent(id -> model.selectNode("release/unit/" + id));
        return model;
    }

    /** Fail-closed completion report whose every count expands to its exact unit witnesses. */
    public static SFMReviewExplorerModel releaseStatus(SFMReleaseReviewV1 review) {
        Objects.requireNonNull(review, "review");
        SFMReleaseReviewKernel.CompletionReport report = SFMReleaseReviewKernel.completion(review);
        SFMReleaseReviewKernel.CompletionWitnesses witnesses = report.witnesses();
        List<Node> categories = List.of(
                releaseStatusCategory(review, "changed", "Changed domain", witnesses.changedDomain()),
                releaseStatusCategory(review, "approved-raw", "Approved (raw tag)", witnesses.approvedRaw()),
                releaseStatusCategory(review, "approved-effective", "Approved (effective)",
                        witnesses.approvedEffective()),
                releaseStatusCategory(review, "remaining", "Remaining", witnesses.remaining()),
                releaseStatusCategory(review, "blocking", "Blocking", witnesses.blocking()),
                releaseStatusCategory(review, "suspended", "Suspended", witnesses.suspended()),
                releaseStatusCategory(review, "missing", "Missing or ambiguous", witnesses.missing()),
                releaseStatusCategory(review, "deferred", "Deferred", witnesses.deferred()),
                releaseStatusCategory(review, "unsupported", "Unsupported", witnesses.unsupported()),
                releaseStatusCategory(review, "stale-producer", "Stale producer", witnesses.staleProducer())
        );
        SFMReviewExplorerModel model = new SFMReviewExplorerModel(node(
                "release/status",
                "Release status · " + report.status().name().toLowerCase(Locale.ROOT)
                        + " · remaining=" + report.remaining()
                        + " · blocking=" + report.blocking(),
                Kind.ROOT,
                categories,
                null,
                true
        ));
        review.resumeState().currentUnitId().ifPresent(id -> model.selectNode("release/unit/" + id));
        return model;
    }

    private static Node releaseStatusCategory(
            SFMReleaseReviewV1 review,
            String id,
            String label,
            List<String> reviewUnitIds
    ) {
        return node(
                "release/status/" + id,
                label + " · " + reviewUnitIds.size(),
                Kind.STATUS_CATEGORY,
                releaseUnitRows(review, reviewUnitIds),
                null,
                false
        );
    }

    private static List<Node> releaseUnitRows(SFMReleaseReviewV1 review, List<String> reviewUnitIds) {
        Map<String, SFMReleaseReviewV1.ReviewUnit> units = new LinkedHashMap<>();
        review.reviewUnits().forEach(unit -> units.put(unit.id(), unit));
        SFMReleaseReviewCorpus corpus = SFMReleaseReviewCorpus.from(review);
        List<Node> rows = new ArrayList<>();
        for (String unitId : reviewUnitIds) {
            SFMReleaseReviewV1.ReviewUnit unit = Objects.requireNonNull(units.get(unitId), unitId);
            String path = unit.pathAfter().orElseGet(() -> unit.pathBefore().orElse("<missing>"));
            List<Node> leaves = new ArrayList<>();
            leaves.add(releaseUnitLeaf(corpus, unit, true));
            leaves.add(releaseUnitLeaf(corpus, unit, false));
            String limitation = unit.limitation().map(value -> " · " + value).orElse("");
            rows.add(node(
                    "release/unit/" + unit.id(),
                    unit.operation().name().toLowerCase(Locale.ROOT) + " · "
                            + unit.surfaceKind().name().toLowerCase(Locale.ROOT) + " · " + path + limitation,
                    Kind.REVIEW_UNIT,
                    leaves,
                    null,
                    false
            ));
        }
        return rows;
    }

    /** Persisted migration decisions and all old/new witnesses, including unresolved candidates. */
    public static SFMReviewExplorerModel releaseMigrations(SFMReleaseReviewV1 review) {
        Objects.requireNonNull(review, "review");
        SFMReleaseReviewCorpus corpus = SFMReleaseReviewCorpus.from(review);
        List<Node> migrations = new ArrayList<>();
        for (SFMReleaseReviewV1.MigrationReport report : review.migrationReports()) {
            List<Node> witnesses = new ArrayList<>();
            for (int index = 0; index < report.oldWitnesses().size(); index++) {
                SFMReleaseReviewV1.PinnedSelectionRange range = report.oldWitnesses().get(index);
                witnesses.add(releaseAddressedLeaf(
                        corpus,
                        report.id() + "/old/" + index,
                        "old",
                        range.documentRevisionId(),
                        range.startByte(),
                        range.endByte()
                ));
            }
            for (int index = 0; index < report.newCandidates().size(); index++) {
                SFMReleaseReviewV1.AddressedRange range = report.newCandidates().get(index);
                witnesses.add(releaseAddressedLeaf(
                        corpus,
                        report.id() + "/candidate/" + index,
                        "candidate " + (index + 1),
                        range.documentRevisionId(),
                        range.startByte(),
                        range.endByte()
                ));
            }
            String candidateStatus = report.candidateEvaluation().status().name().toLowerCase(Locale.ROOT);
            migrations.add(node(
                    "release/migration/" + report.id(),
                    candidateStatus + " · " + report.decision().name().toLowerCase(Locale.ROOT)
                            + " · " + report.sourceSelectorId(),
                    Kind.MIGRATION,
                    witnesses,
                    null,
                    report.decision() == SFMReleaseReviewV1.MigrationDecision.UNRESOLVED
            ));
        }
        return new SFMReviewExplorerModel(node(
                "release/migrations",
                "Release-review migration queue",
                Kind.ROOT,
                migrations,
                null,
                true
        ));
    }

    public static SFMReviewExplorerModel message(String title, String message) {
        return new SFMReviewExplorerModel(node(
                "message",
                title,
                Kind.ROOT,
                List.of(node("message/body", message, Kind.REGION, List.of(), null, true)),
                null,
                true
        ));
    }

    private static SFMReviewExplorerModel fixtureComments(boolean hashtags) {
        SFMReviewCommentDataSource.SessionView session = new SFMFixtureReviewCommentDataSource().refresh();
        return comments(session, Map.of(), hashtags);
    }

    private static SFMReviewExplorerModel sessionComments(SFMReviewSessionV2 session, boolean hashtags) {
        Objects.requireNonNull(session, "session");
        SFMReviewCommentDataSource.SessionView view = new SFMReviewCommentKernelDataSource(session).refresh();
        Map<String, CandidateNavigation> candidateTargets = new LinkedHashMap<>();
        for (SFMReviewSessionV2.Comment comment : session.comments()) {
            if (comment.target() instanceof SFMReviewSessionV2.CandidateTrajectoryTarget target) {
                candidateTargets.put(comment.id(), new CandidateNavigation(comment.id(), target));
            }
        }
        return comments(view, candidateTargets, hashtags);
    }

    private static SFMReviewExplorerModel comments(
            SFMReviewCommentDataSource.SessionView session,
            Map<String, CandidateNavigation> candidateTargets,
            boolean hashtags
    ) {
        Map<String, SFMReviewCommentDataSource.DocumentView> documents = new LinkedHashMap<>();
        for (SFMReviewCommentDataSource.DocumentView document : session.documents()) {
            documents.put(document.id(), document);
        }
        Node root = hashtags
                ? hashtagTree(session, documents, candidateTargets)
                : commentTree(session, documents, candidateTargets);
        return new SFMReviewExplorerModel(root);
    }

    private static Node commentTree(
            SFMReviewCommentDataSource.SessionView session,
            Map<String, SFMReviewCommentDataSource.DocumentView> documents,
            Map<String, CandidateNavigation> candidateTargets
    ) {
        List<Node> comments = new ArrayList<>();
        for (SFMReviewCommentDataSource.CommentView comment : session.comments()) {
            List<Node> regions = new ArrayList<>();
            CandidateNavigation candidateTarget = candidateTargets.get(comment.id());
            if (candidateTarget != null) {
                regions.add(candidateTargetNode("comment/" + comment.id() + "/candidate", candidateTarget));
            }
            int index = 0;
            for (SFMReviewCommentDataSource.RangeView range : comment.ranges()) {
                SFMReviewCommentDataSource.DocumentView document = documents.get(range.documentRevisionId());
                if (document == null) continue;
                SourceLeaf leaf = leafForRange("comment/" + comment.id() + "/" + index, comment.id(), document, range);
                regions.add(node(leaf.id(), document.side() + " · " + document.path() + " ["
                                + range.startByte() + ".." + range.endByte() + ")", Kind.REGION,
                        List.of(), leaf, true));
                index++;
            }
            String label = candidateTarget == null
                    ? comment.id() + "  " + comment.text()
                    : "[candidate · " + projectionStatusLabel(candidateTarget.target()) + "] "
                            + comment.id() + "  " + comment.text();
            comments.add(node("comment/" + comment.id(), label, Kind.COMMENT,
                    regions, null, candidateTarget, true));
        }
        return node("comments", "Comments · " + session.title(), Kind.ROOT, comments, null, true);
    }

    private static Node hashtagTree(
            SFMReviewCommentDataSource.SessionView session,
            Map<String, SFMReviewCommentDataSource.DocumentView> documents,
            Map<String, CandidateNavigation> candidateTargets
    ) {
        Map<String, Map<String, List<Node>>> grouped = new TreeMap<>();
        Map<String, List<Node>> candidateGrouped = new TreeMap<>();
        for (SFMReviewCommentDataSource.CommentView comment : session.comments()) {
            Set<String> tags = new LinkedHashSet<>(SFMCommentHashtags.derive(comment.text()));
            CandidateNavigation candidateTarget = candidateTargets.get(comment.id());
            if (candidateTarget != null) {
                for (String tag : tags) {
                    candidateGrouped.computeIfAbsent(tag, ignored -> new ArrayList<>())
                            .add(candidateTargetNode(
                                    "hashtag/" + tag + "/candidate/" + comment.id(),
                                    candidateTarget
                            ));
                }
            }
            int index = 0;
            for (SFMReviewCommentDataSource.RangeView range : comment.ranges()) {
                SFMReviewCommentDataSource.DocumentView document = documents.get(range.documentRevisionId());
                if (document == null) continue;
                SourceLeaf leaf = leafForRange("hashtag/" + comment.id() + "/" + index, comment.id(), document, range);
                Node region = node(leaf.id(), comment.id() + " · " + document.side() + " ["
                                + range.startByte() + ".." + range.endByte() + ")", Kind.REGION,
                        List.of(), leaf, true);
                for (String tag : tags) {
                    grouped.computeIfAbsent(tag, ignored -> new TreeMap<>())
                            .computeIfAbsent(document.path(), ignored -> new ArrayList<>())
                            .add(region);
                }
                index++;
            }
        }
        List<Node> tags = new ArrayList<>();
        Set<String> allTags = new java.util.TreeSet<>(grouped.keySet());
        allTags.addAll(candidateGrouped.keySet());
        for (String tag : allTags) {
            List<Node> files = grouped.getOrDefault(tag, Map.<String, List<Node>>of()).entrySet().stream()
                    .map(file -> node("hashtag/" + tag + "/" + file.getKey(), file.getKey(), Kind.FILE,
                            file.getValue(), null, true))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            files.addAll(candidateGrouped.getOrDefault(tag, List.of()));
            tags.add(node("hashtag/" + tag, tag, Kind.HASHTAG, files, null, true));
        }
        return node("hashtags", "Hashtags · " + session.title(), Kind.ROOT, tags, null, true);
    }

    private static Node candidateTargetNode(String id, CandidateNavigation navigation) {
        return node(id, candidateTargetLabel(navigation), Kind.CANDIDATE_TARGET,
                List.of(), null, navigation, true);
    }

    private static String candidateTargetLabel(CandidateNavigation navigation) {
        SFMReviewSessionV2.CandidateTrajectoryTarget target = navigation.target();
        StringBuilder label = new StringBuilder("candidate ")
                .append(target.targetKind().name().toLowerCase(Locale.ROOT))
                .append(" · plan=").append(target.trajectoryPlanRevisionId())
                .append(" · route=").append(target.routeId())
                .append(" · frame=").append(target.routeStepPosition());
        target.trajectoryStepId().ifPresent(step -> label.append(" · step=").append(step));
        target.actionIntentId().ifPresent(action -> label.append(" · action=").append(action));
        label.append(" · state=").append(target.predictedStateId())
                .append(" · status=").append(projectionStatusLabel(target));
        target.projectedDocumentSelection().ifPresent(selection -> label
                .append(" · document=").append(selection.documentId())
                .append('[').append(selection.startByte()).append(',').append(selection.endByte()).append(')'));
        return label.toString();
    }

    private static String projectionStatusLabel(SFMReviewSessionV2.CandidateTrajectoryTarget target) {
        return target.projectionStatus().name().toLowerCase(Locale.ROOT);
    }

    private static SourceLeaf revisionLeaf(LaneData lane, FileData file, boolean before) {
        String text = before ? file.beforeText() : file.afterText();
        String side = before ? "before" : "after";
        return new SourceLeaf(
                "revision/" + lane.id() + "/" + file.path() + "/" + side,
                side + " · " + lane.id() + " · " + (text == null ? "missing" : file.path()),
                file.path(), text == null ? "" : text, text == null
        );
    }

    private static String releasePairGroupingKey(SFMReleaseReviewV1.ReviewUnit unit) {
        return unit.laneId() + "\u0000" + unit.pathBefore().orElse("") + "\u0000" + unit.pathAfter().orElse("");
    }

    private static ReleasePair releasePair(
            List<SFMReleaseReviewV1.ReviewUnit> values,
            Map<String, SFMReleaseReviewV1.CorpusDocument> documents
    ) {
        if (values.isEmpty()) throw new IllegalArgumentException("A release file pair requires review units");
        List<SFMReleaseReviewV1.ReviewUnit> units = values.stream()
                .sorted(Comparator.comparing(SFMReleaseReviewV1.ReviewUnit::id))
                .toList();
        SFMReleaseReviewV1.ReviewUnit first = units.get(0);
        for (SFMReleaseReviewV1.ReviewUnit unit : units) {
            if (!unit.laneId().equals(first.laneId())
                    || !unit.pathBefore().equals(first.pathBefore())
                    || !unit.pathAfter().equals(first.pathAfter())
                    || unit.operation() != first.operation()
                    || !unit.language().equals(first.language())) {
                throw new IllegalArgumentException("Review units disagree about their immutable file pair");
            }
        }
        Optional<String> beforeRevision = singleRevision(units, true);
        Optional<String> afterRevision = singleRevision(units, false);
        Optional<SFMReleaseReviewV1.CorpusDocument> before = beforeRevision.map(id -> {
            SFMReleaseReviewV1.CorpusDocument document = documents.get(id);
            if (document == null || document.snapshotSide() != SFMReleaseReviewV1.SnapshotSide.BEFORE) {
                throw new IllegalArgumentException("Review file pair has no exact before corpus document");
            }
            return document;
        });
        Optional<SFMReleaseReviewV1.CorpusDocument> after = afterRevision.map(id -> {
            SFMReleaseReviewV1.CorpusDocument document = documents.get(id);
            if (document == null || document.snapshotSide() != SFMReleaseReviewV1.SnapshotSide.AFTER) {
                throw new IllegalArgumentException("Review file pair has no exact after corpus document");
            }
            return document;
        });
        return new ReleasePair(
                first.laneId(), first.operation(), first.pathBefore(), first.pathAfter(), before, after,
                first.language(), units.stream().map(SFMReleaseReviewV1.ReviewUnit::id).toList());
    }

    private static Optional<String> singleRevision(List<SFMReleaseReviewV1.ReviewUnit> units, boolean before) {
        List<String> revisions = units.stream()
                .map(unit -> before ? unit.beforeDocumentRevisionId() : unit.afterDocumentRevisionId())
                .flatMap(Optional::stream)
                .distinct()
                .toList();
        if (revisions.size() > 1) throw new IllegalArgumentException("Review file pair has multiple source revisions");
        return revisions.stream().findFirst();
    }

    private static ReleasePair releaseCorpusPair(List<SFMReleaseReviewV1.CorpusDocument> values) {
        if (values.isEmpty()) throw new IllegalArgumentException("A corpus file pair requires at least one document");
        SFMReleaseReviewV1.CorpusDocument first = values.get(0);
        for (SFMReleaseReviewV1.CorpusDocument document : values) {
            if (!document.laneId().equals(first.laneId()) || !document.path().equals(first.path())) {
                throw new IllegalArgumentException("Corpus documents disagree about their lane/path pair");
            }
        }
        Optional<SFMReleaseReviewV1.CorpusDocument> before = values.stream()
                .filter(document -> document.snapshotSide() == SFMReleaseReviewV1.SnapshotSide.BEFORE)
                .findFirst();
        Optional<SFMReleaseReviewV1.CorpusDocument> after = values.stream()
                .filter(document -> document.snapshotSide() == SFMReleaseReviewV1.SnapshotSide.AFTER)
                .findFirst();
        SFMReleaseReviewV1.ChangeOperation operation = before.isEmpty()
                ? SFMReleaseReviewV1.ChangeOperation.ADDED
                : after.isEmpty()
                        ? SFMReleaseReviewV1.ChangeOperation.DELETED
                        : SFMReleaseReviewV1.ChangeOperation.MODIFIED;
        Optional<String> pathBefore = before.map(SFMReleaseReviewV1.CorpusDocument::path);
        Optional<String> pathAfter = after.map(SFMReleaseReviewV1.CorpusDocument::path);
        String path = pathAfter.orElseGet(() -> pathBefore.orElseThrow());
        String language = path.toLowerCase(Locale.ROOT).endsWith(".java") ? "java" : "text";
        return new ReleasePair(
                first.laneId(), operation, pathBefore, pathAfter, before, after, language, List.of());
    }

    private static String releaseFileGroupingKey(ReleasePair pair) {
        return pair.pathBefore().orElse("") + "\u0000" + pair.pathAfter().orElse("");
    }

    private static String releasePairLabel(ReleasePair pair) {
        if (pair.pathBefore().equals(pair.pathAfter()) && pair.pathAfter().isPresent()) {
            return pair.pathAfter().orElseThrow();
        }
        if (pair.pathBefore().isEmpty()) return pair.pathAfter().orElseThrow();
        if (pair.pathAfter().isEmpty()) return pair.pathBefore().orElseThrow();
        return pair.pathBefore().orElseThrow() + " → " + pair.pathAfter().orElseThrow();
    }

    private static Optional<SFMReleaseReviewSurfaceV1.FilePair> releaseSurfacePair(
            SFMReleaseReviewCorpus corpus,
            ReleasePair pair
    ) {
        Optional<SFMReleaseReviewSurfaceV1.Source> before = pair.before().flatMap(binding ->
                releaseSurfaceSource(corpus, binding, pair.language()));
        Optional<SFMReleaseReviewSurfaceV1.Source> after = pair.after().flatMap(binding ->
                releaseSurfaceSource(corpus, binding, pair.language()));
        String identity = pair.laneId() + "\n" + pair.operation() + "\n"
                + pair.pathBefore().orElse("<missing>") + "\n" + pair.pathAfter().orElse("<missing>") + "\n"
                + String.join("\n", pair.reviewUnitIds());
        try {
            return Optional.of(new SFMReleaseReviewSurfaceV1.FilePair(
                    "pair-" + stableId(identity), pair.laneId(), pair.operation(), pair.reviewUnitIds(), before, after));
        } catch (IllegalArgumentException incomplete) {
            return Optional.empty();
        }
    }

    private static Optional<SFMReleaseReviewSurfaceV1.Source> releaseSurfaceSource(
            SFMReleaseReviewCorpus corpus,
            SFMReleaseReviewV1.CorpusDocument binding,
            String language
    ) {
        if (binding.materialization() != SFMReleaseReviewV1.Materialization.COMPLETE) return Optional.empty();
        return corpus.documentRevision(binding.documentRevisionId())
                .flatMap(SFMReleaseReviewCorpus.DocumentView::materializedDocument)
                .map(document -> SFMReleaseReviewSurfaceV1.Source.fromCorpus(
                        binding.documentRevisionId(), binding.path(), language, document.text()));
    }

    private static SourceLeaf releaseDiffLeaf(
            ReleasePair pair,
            Optional<SFMReleaseReviewSurfaceV1.FilePair> filePair,
            SFMReleaseReviewSurfaceV1.SurfaceKind kind,
            String title
    ) {
        String path = releasePairLabel(pair);
        String id = "release/diff/" + pair.laneId() + "/" + kind.wireName() + "/"
                + stableId(releaseFileGroupingKey(pair));
        if (filePair.isEmpty()) {
            boolean reviewUnitsAbsent = pair.reviewUnitIds().isEmpty();
            return new SourceLeaf(
                    id,
                    title + " · " + pair.laneId() + " · unavailable",
                    path,
                    "Review diff unavailable\n"
                            + "code: " + (reviewUnitsAbsent
                                    ? "review.surface.review-unit-unavailable"
                                    : "review.surface.source-unavailable") + "\n"
                            + (reviewUnitsAbsent
                                    ? "The pinned corpus entry is outside the review-unit domain.\n"
                                    : "One or more immutable source snapshots were not materialized.\n"),
                    false
            );
        }
        return new SourceLeaf(
                id,
                title + " · " + pair.laneId() + " · " + path,
                path,
                "",
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(new SFMReleaseReviewSurfaceV1.Recipe(filePair.orElseThrow(), kind))
        );
    }

    private static String stableId(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    private static Node releaseUnitLeaf(
            SFMReleaseReviewCorpus corpus,
            SFMReleaseReviewV1.ReviewUnit unit,
            boolean before
    ) {
        Optional<String> revisionId = before ? unit.beforeDocumentRevisionId() : unit.afterDocumentRevisionId();
        Optional<String> path = before ? unit.pathBefore() : unit.pathAfter();
        SFMReleaseReviewV1.CorpusDocument binding = revisionId
                .flatMap(corpus::documentRevision)
                .map(SFMReleaseReviewCorpus.DocumentView::binding)
                .orElse(null);
        List<SFMReleaseReviewV1.Utf8Range> ranges = before ? unit.beforeRanges() : unit.afterRanges();
        String rangeLabel = ranges.isEmpty() ? "" : " · " + ranges.stream()
                .map(range -> "[" + range.startByte() + ".." + range.endByte() + ")")
                .collect(java.util.stream.Collectors.joining(","));
        SourceLeaf leaf = releaseLeaf(
                corpus,
                unit.laneId(),
                path.orElse("<missing>"),
                binding,
                before,
                rangeLabel,
                ranges.stream().findFirst()
        );
        return node(leaf.id() + "/" + unit.id(), leaf.title(), Kind.REVISION, List.of(), leaf, true);
    }

    private static Node releaseAddressedLeaf(
            SFMReleaseReviewCorpus corpus,
            String id,
            String label,
            String revisionId,
            int startByte,
            int endByte
    ) {
        SFMReleaseReviewCorpus.DocumentView view = corpus.documentRevision(revisionId).orElse(null);
        SFMReleaseReviewV1.CorpusDocument binding = view == null ? null : view.binding();
        String lane = binding == null ? "unknown" : binding.laneId();
        String path = binding == null ? revisionId : binding.path();
        boolean before = binding != null && binding.snapshotSide() == SFMReleaseReviewV1.SnapshotSide.BEFORE;
        SourceLeaf source = releaseLeaf(
                corpus,
                lane,
                path,
                binding,
                before,
                " · [" + startByte + ".." + endByte + ")",
                Optional.of(new SFMReleaseReviewV1.Utf8Range(startByte, endByte))
        );
        SourceLeaf unique = new SourceLeaf(
                "release/migration/" + id,
                label + " · " + source.title(),
                source.path(),
                source.text(),
                source.missing(),
                source.documentRevisionId(),
                source.sha256(),
                source.targetRange()
        );
        return node(unique.id(), unique.title(), Kind.REVISION, List.of(), unique, true);
    }

    private static SourceLeaf releaseLeaf(
            SFMReleaseReviewCorpus corpus,
            String laneId,
            String path,
            SFMReleaseReviewV1.CorpusDocument binding,
            boolean before,
            String suffix
    ) {
        return releaseLeaf(corpus, laneId, path, binding, before, suffix, Optional.empty());
    }

    private static SourceLeaf releaseLeaf(
            SFMReleaseReviewCorpus corpus,
            String laneId,
            String path,
            SFMReleaseReviewV1.CorpusDocument binding,
            boolean before,
            String suffix,
            Optional<SFMReleaseReviewV1.Utf8Range> targetRange
    ) {
        String side = before ? "before" : "after";
        Optional<SFMReleaseReviewCorpus.DocumentView> view = binding == null
                ? Optional.empty()
                : corpus.documentRevision(binding.documentRevisionId());
        Optional<String> text = view.flatMap(SFMReleaseReviewCorpus.DocumentView::materializedDocument)
                .map(value -> value.text());
        boolean missing = binding == null
                || binding.materialization() != SFMReleaseReviewV1.Materialization.COMPLETE
                || text.isEmpty();
        String id = "release/revision/" + laneId + "/" + side + "/" + path;
        return new SourceLeaf(
                id,
                side + " · " + laneId + " · " + (missing ? "missing" : path) + suffix,
                path,
                text.orElse(""),
                missing,
                binding == null ? Optional.empty() : Optional.of(binding.documentRevisionId()),
                binding == null ? Optional.empty() : Optional.of(binding.sha256()),
                missing ? Optional.empty() : targetRange
        );
    }

    private static String pinnedRangeLabel(SFMReleaseReviewV1 review) {
        return review.repositoryBindings().stream()
                .map(binding -> binding.laneId() + ":" + binding.beforeLabel() + "→" + binding.afterLabel())
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static SourceLeaf leafForRange(
            String id,
            String commentId,
            SFMReviewCommentDataSource.DocumentView document,
            SFMReviewCommentDataSource.RangeView range
    ) {
        return new SourceLeaf(
                id,
                commentId + " · " + document.side() + " · " + document.path(),
                document.path(),
                document.text(),
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.of(new SFMReleaseReviewV1.Utf8Range(range.startByte(), range.endByte()))
        );
    }

    private static Node node(String id, String label, Kind kind, List<Node> children, SourceLeaf leaf, boolean expanded) {
        return node(id, label, kind, children, leaf, null, expanded);
    }

    private static Node node(
            String id,
            String label,
            Kind kind,
            List<Node> children,
            SourceLeaf leaf,
            NodeAction action,
            boolean expanded
    ) {
        return new Node(id, label, kind, children, leaf, action, expanded);
    }

    public Node root() { return root; }

    public List<VisibleNode> visibleNodes() {
        List<VisibleNode> result = new ArrayList<>();
        appendVisible(root, 0, result);
        return List.copyOf(result);
    }

    public int selectionIndex() { return selectionIndex; }

    public void select(int index) {
        selectionIndex = Math.max(0, Math.min(Math.max(0, visibleNodes().size() - 1), index));
    }

    public Node selected() {
        List<VisibleNode> visible = visibleNodes();
        if (visible.isEmpty()) return root;
        return visible.get(Math.max(0, Math.min(selectionIndex, visible.size() - 1))).node();
    }

    public SourceLeaf selectedLeaf() { return selected().leaf(); }

    public ViewState captureViewState() {
        LinkedHashSet<String> known = new LinkedHashSet<>();
        LinkedHashSet<String> expanded = new LinkedHashSet<>();
        collectViewState(root, known, expanded);
        return new ViewState(selected().id(), known, expanded);
    }

    public void restoreViewState(ViewState state) {
        Objects.requireNonNull(state, "state");
        applyExpanded(root, state.knownNodeIds(), state.expandedNodeIds());
        selectNode(state.selectedNodeId());
    }

    public void selectNode(String id) {
        List<VisibleNode> visible = visibleNodes();
        for (int index = 0; index < visible.size(); index++) {
            if (visible.get(index).node().id().equals(id)) {
                selectionIndex = index;
                return;
            }
        }
    }

    public void selectNext() { selectionIndex = Math.min(Math.max(0, visibleNodes().size() - 1), selectionIndex + 1); }
    public void selectPrevious() { selectionIndex = Math.max(0, selectionIndex - 1); }
    public void selectFirst() { selectionIndex = 0; }
    public void selectLast() { selectionIndex = Math.max(0, visibleNodes().size() - 1); }

    public void expandSelection() {
        Node selected = selected();
        if (selected.expandable()) selected.expanded = true;
    }

    public void collapseSelectionOrSelectParent() {
        Node selected = selected();
        if (selected.expandable() && selected.expanded()) {
            selected.expanded = false;
            return;
        }
        Node parent = parentOf(root, selected);
        if (parent == null) return;
        List<VisibleNode> visible = visibleNodes();
        for (int index = 0; index < visible.size(); index++) {
            if (visible.get(index).node() == parent) {
                selectionIndex = index;
                return;
            }
        }
    }

    public void toggleSelection() {
        Node selected = selected();
        if (selected.expandable()) selected.expanded = !selected.expanded;
    }

    private static void appendVisible(Node node, int depth, List<VisibleNode> result) {
        result.add(new VisibleNode(node, depth));
        if (!node.expanded) return;
        for (Node child : node.children) appendVisible(child, depth + 1, result);
    }

    private static Node parentOf(Node current, Node target) {
        for (Node child : current.children) {
            if (child == target) return current;
            Node nested = parentOf(child, target);
            if (nested != null) return nested;
        }
        return null;
    }

    private static void collectViewState(Node node, Set<String> known, Set<String> expanded) {
        known.add(node.id());
        if (node.expanded()) expanded.add(node.id());
        node.children().forEach(child -> collectViewState(child, known, expanded));
    }

    private static void applyExpanded(Node node, Set<String> known, Set<String> expanded) {
        if (known.contains(node.id())) node.setExpanded(expanded.contains(node.id()));
        node.children().forEach(child -> applyExpanded(child, known, expanded));
    }
}
