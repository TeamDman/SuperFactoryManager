package ca.teamdman.sfm.client.screen.review.explorer;

import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewCorpus;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewCoverage;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewKernel;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewSurfaceV1;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1;
import ca.teamdman.sfm.client.review.release_review.SFMWorkingTreeCaptureV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
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
        DIRECTORY,
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

    /** How changed repository paths are projected without changing their review identity. */
    public enum PathLayout {
        HIERARCHY,
        FLAT_PATHS
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
    public sealed interface NodeAction permits CandidateNavigation, CommentNavigation {
    }

    public record CommentNavigation(String commentId) implements NodeAction {
        public CommentNavigation {
            Objects.requireNonNull(commentId);
            if (commentId.isBlank()) throw new IllegalArgumentException("Blank comment identity");
        }
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

    private record ReleaseFileProjection(String hierarchyPath, Node node) {
        private ReleaseFileProjection {
            Objects.requireNonNull(hierarchyPath, "hierarchyPath");
            Objects.requireNonNull(node, "node");
            if (hierarchyPath.isBlank()) throw new IllegalArgumentException("Release hierarchy path is blank");
        }
    }

    private static final class ReleaseDirectoryBuilder {
        private final String path;
        private final String label;
        private final Map<String, ReleaseDirectoryBuilder> directories = new TreeMap<>();
        private final List<Node> files = new ArrayList<>();

        private ReleaseDirectoryBuilder(String path, String label) {
            this.path = Objects.requireNonNull(path, "path");
            this.label = Objects.requireNonNull(label, "label");
        }

        private ReleaseDirectoryBuilder directory(String segment) {
            String childPath = path.isEmpty() ? segment : path + "/" + segment;
            return directories.computeIfAbsent(
                    segment,
                    ignored -> new ReleaseDirectoryBuilder(childPath, segment)
            );
        }

        private Node build() {
            ArrayList<Node> children = new ArrayList<>(directories.size() + files.size());
            directories.values().stream().map(ReleaseDirectoryBuilder::build).forEach(children::add);
            files.stream().sorted(Comparator.comparing(Node::label)).forEach(children::add);
            return node(
                    "release/directory/" + stableId(path),
                    label,
                    Kind.DIRECTORY,
                    children,
                    null,
                    false
            );
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

    /** Exact object projection shared by the Comments lens and source decorations. */
    public static Optional<Node> commentObject(SFMReviewSessionV2 session, String commentId) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(commentId, "commentId");
        String nodeId = "comment/" + commentId;
        return sessionComments(session, false).root().children().stream()
                .filter(node -> node.id().equals(nodeId))
                .findFirst();
    }

    /** Production hashtag projection over the canonical immutable V2 review session. */
    public static SFMReviewExplorerModel hashtags(SFMReviewSessionV2 session) {
        return sessionComments(session, true);
    }

    /** Complete pinned before/after plus lazy source-mapped diff projection. */
    public static SFMReviewExplorerModel releaseChanges(SFMReleaseReviewV1 review) {
        return releaseChanges(review, PathLayout.FLAT_PATHS);
    }

    /** Complete pinned changes in either a repository hierarchy or an explicit flat-path view. */
    public static SFMReviewExplorerModel releaseChanges(
            SFMReleaseReviewV1 review,
            PathLayout pathLayout
    ) {
        return buildReleaseChanges(review, pathLayout, documentCommentChildren(review.reviewSession()));
    }

    /** Bounded to one source observation per layout, owned by one Explorer resolver. */
    public static final class ChangesCache {
        private record Entry(SFMReleaseReviewV1 source, Node template) {}
        private final Map<PathLayout, Entry> entries = new java.util.EnumMap<>(PathLayout.class);
        private long sourceBuilds;
        private SFMReviewSessionV2 evaluationContext;
        private final Map<SFMReviewSessionV2.Comment, ca.teamdman.sfm.client.review.session.SFMReviewSessionV2Kernel.Evaluation>
                commentEvaluations = new LinkedHashMap<>();
        private long commentEvaluationBuilds;

        public synchronized long sourceBuilds() { return sourceBuilds; }
        public synchronized long commentEvaluationBuilds() { return commentEvaluationBuilds; }

        public synchronized SFMReviewExplorerModel project(SFMReleaseReviewV1 review, PathLayout layout) {
            Objects.requireNonNull(review);
            Objects.requireNonNull(layout);
            Entry entry = entries.get(layout);
            if (entry == null || !sameSource(entry.source(), review)) {
                entry = new Entry(review, buildReleaseChanges(review, layout, Map.of()).root());
                entries.put(layout, entry);
                sourceBuilds++;
            } else {
                // Source reuse does not waive validation of the new comment/resume state.
                SFMReleaseReviewKernel.validate(review);
            }
            var session = review.reviewSession();
            if (evaluationContext == null || !evaluationContext.id().equals(session.id())
                    || !evaluationContext.schema().equals(session.schema())
                    || !evaluationContext.coordinateSystem().equals(session.coordinateSystem())
                    || !evaluationContext.revisionLanes().equals(session.revisionLanes())
                    || !evaluationContext.styleRules().equals(session.styleRules())
                    || !evaluationContext.completionPolicy().equals(session.completionPolicy()))
                commentEvaluations.clear();
            evaluationContext = session;
            commentEvaluations.keySet().retainAll(new java.util.HashSet<>(session.comments()));
            // Never expose the mutable template nodes. Only immutable leaf recipes are shared.
            return new SFMReviewExplorerModel(attachComments(entry.template(),
                    documentCommentChildren(session, comment -> commentEvaluations.computeIfAbsent(comment, ignored -> {
                        commentEvaluationBuilds++;
                        return ca.teamdman.sfm.client.review.session.SFMReviewSessionV2Kernel.evaluateComment(session, comment);
                    }))));
        }

        public static boolean sameSource(SFMReleaseReviewV1 a, SFMReleaseReviewV1 b) {
            return a.schema().equals(b.schema())
                    && a.reviewSession().id().equals(b.reviewSession().id())
                    && a.repositoryBindings().equals(b.repositoryBindings())
                    && a.corpusDocuments().equals(b.corpusDocuments())
                    && a.reviewUnits().equals(b.reviewUnits())
                    && a.reviewSession().revisionLanes().equals(b.reviewSession().revisionLanes());
        }

        private static Node attachComments(Node template, Map<String, List<Node>> comments) {
            List<Node> children = template.kind == Kind.REVISION && template.leaf != null
                    ? template.leaf.documentRevisionId().map(id -> comments.getOrDefault(id, List.of())).orElse(List.of())
                    : template.children;
            return new Node(template.id, template.label, template.kind,
                    children.stream().map(child -> attachComments(child, comments)).toList(),
                    template.leaf, template.action, template.expanded);
        }
    }

    private static SFMReviewExplorerModel buildReleaseChanges(
            SFMReleaseReviewV1 review, PathLayout pathLayout, Map<String, List<Node>> documentComments
    ) {
        Objects.requireNonNull(review, "review");
        Objects.requireNonNull(pathLayout, "pathLayout");
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
        ArrayList<ReleaseFileProjection> files = new ArrayList<>();
        for (List<ReleasePair> filePairs : pairsByFile.values()) {
            filePairs.sort(Comparator.comparing(ReleasePair::laneId));
            ReleasePair representative = filePairs.get(0);
            String fileLabel = releasePairLabel(representative);
            List<Node> lanes = new ArrayList<>();
            for (ReleasePair pair : filePairs) {
                String laneId = pair.laneId();
                // Retained evidence is a single immutable commented snapshot, not
                // another change pair. Keep its address but do not invent diffs.
                var historical = pair.after().filter(document ->
                        SFMReleaseReviewV1.EVIDENCE_OWNER.equals(document.sourceOwner()));
                if (pair.before().isEmpty() && historical.isPresent()) {
                    SourceLeaf source = releaseLeaf(corpus, laneId, historical.get().path(),
                            historical.get(), false, "");
                    lanes.add(node(
                            "release/lane/" + laneId + "/" + stableId(releaseFileGroupingKey(pair)),
                            "Historical comment evidence · not current approval", Kind.LANE,
                            List.of(historicalSourceNode(source, documentComments)), null, false));
                    continue;
                }
                SFMReleaseReviewV1.RepositoryBinding binding = bindings.get(laneId);
                String beforeLabel = binding == null ? "before" : revisionLabel(binding.beforeCommit());
                String afterLabel = binding == null ? "after" : candidateLabel(binding);
                SourceLeaf beforeLeaf = releaseLeaf(corpus, laneId, pair.pathBefore().orElse(fileLabel),
                        pair.before().orElse(null), true, "");
                SourceLeaf afterLeaf = releaseLeaf(corpus, laneId, pair.pathAfter().orElse(fileLabel),
                        pair.after().orElse(null), false, "");
                Optional<SFMReleaseReviewSurfaceV1.FilePair> surfacePair = releaseSurfacePair(corpus, pair);
                SourceLeaf textDiff = releaseDiffLeaf(
                        pair, surfacePair, SFMReleaseReviewSurfaceV1.SurfaceKind.TEXT_DIFF, "text diff (inline)");
                SourceLeaf structuredDiff = releaseDiffLeaf(
                        pair, surfacePair, SFMReleaseReviewSurfaceV1.SurfaceKind.JAVA_STRUCTURED_DIFF,
                        "structured diff (inline)");
                SourceLeaf textSplit = splitLeaf(textDiff);
                SourceLeaf structuredSplit = splitLeaf(structuredDiff);
                lanes.add(node(
                        "release/lane/" + laneId + "/" + stableId(releaseFileGroupingKey(pair)),
                        laneId + "  " + beforeLabel + " → " + afterLabel,
                        Kind.LANE,
                        List.of(
                                node(beforeLeaf.id(), beforeLeaf.title(), Kind.REVISION,
                                        beforeLeaf.documentRevisionId().map(documentComments::get).orElse(List.of()), beforeLeaf, false),
                                node(afterLeaf.id(), afterLeaf.title(), Kind.REVISION,
                                        afterLeaf.documentRevisionId().map(documentComments::get).orElse(List.of()), afterLeaf, false),
                                node(textDiff.id(), textDiff.title(), Kind.DIFF, List.of(), textDiff, true),
                                node(structuredDiff.id(), structuredDiff.title(), Kind.DIFF,
                                        List.of(), structuredDiff, true),
                                node(textSplit.id(), textSplit.title(), Kind.DIFF, List.of(), textSplit, true),
                                node(structuredSplit.id(), structuredSplit.title(), Kind.DIFF, List.of(), structuredSplit, true)
                        ),
                        null,
                        false
                ));
            }
            String visibleLabel = pathLayout == PathLayout.FLAT_PATHS
                    ? fileLabel
                    : releaseHierarchyFileLabel(representative);
            files.add(new ReleaseFileProjection(
                    releasePairHierarchyPath(representative),
                    node(
                            "release/file/" + stableId(releaseFileGroupingKey(representative)),
                            visibleLabel,
                            Kind.FILE,
                            lanes,
                            null,
                            false
                    )
            ));
        }
        List<Node> projectedFiles = pathLayout == PathLayout.FLAT_PATHS
                ? files.stream().map(ReleaseFileProjection::node).toList()
                : releasePathHierarchy(files);
        return new SFMReviewExplorerModel(node(
                "release/changes",
                "Release changes · " + pinnedRangeLabel(review),
                Kind.ROOT,
                projectedFiles,
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
                releaseUnitRows(review, result.reviewUnitIds(), coverageByUnit(result.surfaceCoverage())),
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
        Map<String, SFMReleaseReviewCoverage.UnitCoverage> coverage = coverageByUnit(report.surfaceCoverage());
        List<Node> categories = List.of(
                releaseStatusCategory(review, coverage, "changed", "Changed domain", witnesses.changedDomain()),
                releaseStatusCategory(review, coverage, "approved-raw", "Approved (raw tag)", witnesses.approvedRaw()),
                releaseStatusCategory(review, coverage, "approved-effective", "Approved (complete surface)",
                        witnesses.approvedEffective()),
                releaseStatusCategory(review, coverage, "remaining", "Remaining", witnesses.remaining()),
                releaseStatusCategory(review, coverage, "blocking", "Blocking", witnesses.blocking()),
                releaseStatusCategory(review, coverage, "suspended", "Suspended", witnesses.suspended()),
                releaseStatusCategory(review, coverage, "missing", "Missing or ambiguous", witnesses.missing()),
                releaseStatusCategory(review, coverage, "deferred", "Deferred", witnesses.deferred()),
                releaseStatusCategory(review, coverage, "unsupported", "Unsupported", witnesses.unsupported()),
                releaseStatusCategory(review, coverage, "stale-producer", "Stale producer", witnesses.staleProducer()),
                releaseApprovalEvidence(review)
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
            Map<String, SFMReleaseReviewCoverage.UnitCoverage> coverage,
            String id,
            String label,
            List<String> reviewUnitIds
    ) {
        return node(
                "release/status/" + id,
                label + " · " + reviewUnitIds.size(),
                Kind.STATUS_CATEGORY,
                releaseUnitRows(review, reviewUnitIds, coverage),
                null,
                false
        );
    }

    private static Node releaseApprovalEvidence(SFMReleaseReviewV1 review) {
        var corpus = SFMReleaseReviewCorpus.from(review);
        var comments = new LinkedHashMap<String, SFMReviewSessionV2.Comment>();
        review.reviewSession().comments().forEach(comment -> comments.put(comment.id(), comment));
        List<Node> rows = new ArrayList<>();
        for (var evidence : SFMReleaseReviewKernel.approvalEvidence(review)) {
            var comment = comments.get(evidence.commentId());
            String id = "release/approval-evidence/" + stableId(comment.id());
            List<Node> children = new ArrayList<>();
            String detail = evidence.explanation() + "\nComment: " + comment.id()
                    + "\nTarget evidence: " + evidence.targetDescription()
                    + "\nCurrent changed bytes covered: " + evidence.effectiveCurrentRanges().stream()
                    .mapToLong(range -> range.endByte() - range.startByte()).sum()
                    + "\nHistorical target regions: " + evidence.historicalRanges().size()
                    + "\nSource identity, not filename or shifted coordinates, determines approval."
                    + "\nRefreshing never rewrites this comment or transfers its approval.\n";
            children.add(node(id + "/explanation", "Why this approval does or does not count", Kind.REGION, List.of(),
                    new SourceLeaf(id + "/explanation", "Approval coverage explanation", "approval-evidence.txt", detail, false), false));
            children.add(node(id + "/value", "Comment · " + preview(comment.text(), 96), Kind.REGION, List.of(),
                    new SourceLeaf(id + "/value", "Approval comment", "approval-comment.txt", comment.text(), false), false));
            int index = 0;
            for (var range : evidence.targetRanges()) children.add(releaseApprovalTargetLeaf(corpus,
                    id + "/target/" + index++, evidence.targetDescription(), range.documentRevisionId(), range.startByte(), range.endByte()));
            index = 0;
            for (var range : evidence.effectiveCurrentRanges()) children.add(releaseAddressedLeaf(corpus,
                    id + "/current/" + index++, "Exact current coverage", range.documentRevisionId(), range.startByte(), range.endByte()));
            rows.add(node(id, preview(comment.text(), 64) + " · " + evidence.explanation(), Kind.COMMENT, children, null, false));
        }
        return node("release/approval-evidence", "Approval evidence · " + rows.size()
                + " comments (not a count of approved files)", Kind.STATUS_CATEGORY, rows, null, false);
    }

    private static Node releaseApprovalTargetLeaf(SFMReleaseReviewCorpus corpus, String id, String label,
                                                  String revisionId, int startByte, int endByte) {
        var text = corpus.documentRevision(revisionId)
                .flatMap(SFMReleaseReviewCorpus.DocumentView::materializedDocument)
                .map(value -> value.text());
        if (text.isPresent()) {
            try {
                ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange.positionAtByteOffset(text.get(), startByte);
                ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange.positionAtByteOffset(text.get(), endByte);
            } catch (IllegalArgumentException invalid) {
                String detail = "Original approval target cannot be highlighted in this source.\n"
                        + "Document revision: " + revisionId + "\nOriginal UTF-8 range: [" + startByte + ".." + endByte
                        + ")\nDiagnostic: " + invalid.getMessage()
                        + "\nThe original witness is retained; no coordinates were clamped or approval transferred.\n";
                return node(id, "Unresolved original approval target", Kind.REGION, List.of(),
                        new SourceLeaf(id, label, "approval-target-diagnostic.txt", detail, false), false);
            }
        }
        Node addressed = releaseAddressedLeaf(corpus, id, label, revisionId, startByte, endByte);
        SourceLeaf source = addressed.leaf();
        String title = "Target source · " + fileName(source.path()) + " [" + startByte + ".." + endByte + ")";
        SourceLeaf compact = new SourceLeaf(source.id(), title, source.path(), source.text(), source.missing(),
                source.documentRevisionId(), source.sha256(), source.targetRange());
        return node(addressed.id(), title, Kind.REVISION, List.of(), compact, true);
    }

    private static Map<String, SFMReleaseReviewCoverage.UnitCoverage> coverageByUnit(
            List<SFMReleaseReviewCoverage.UnitCoverage> coverage) {
        Map<String, SFMReleaseReviewCoverage.UnitCoverage> byId = new LinkedHashMap<>();
        coverage.forEach(value -> byId.put(value.reviewUnitId(), value));
        return byId;
    }

    private static List<Node> releaseUnitRows(SFMReleaseReviewV1 review, List<String> reviewUnitIds,
                                             Map<String, SFMReleaseReviewCoverage.UnitCoverage> coverage) {
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
            var surface = coverage.get(unitId);
            if (surface != null) {
                List<Node> gaps = new ArrayList<>();
                for (var range : surface.remaining()) {
                    String rangeId = unitId + "/" + range.documentRevisionId() + "/" + range.startByte() + "-" + range.endByte();
                    String side = unit.beforeDocumentRevisionId().filter(range.documentRevisionId()::equals).isPresent()
                            ? "before" : "after";
                    gaps.add(releaseAddressedLeaf(corpus, "remaining/" + rangeId, "Unreviewed " + side,
                            range.documentRevisionId(), range.startByte(), range.endByte()));
                }
                String coverageLabel = "Unreviewed surface · " + gaps.size() + " regions · "
                        + surface.approved().stream().mapToLong(range -> range.endByte() - range.startByte()).sum()
                        + "/" + surface.required().stream().mapToLong(range -> range.endByte() - range.startByte()).sum()
                        + " source bytes approved · "
                        + sideCoverage("before", unit.beforeDocumentRevisionId(), surface) + " · "
                        + sideCoverage("after", unit.afterDocumentRevisionId(), surface)
                        + (surface.bounded() ? "" : " · source domain incomplete");
                leaves.add(node("release/unit/" + unitId + "/remaining", coverageLabel,
                        Kind.STATUS_CATEGORY, gaps, null, false));
            }
            String limitation = unit.limitation().map(value -> " · " + value).orElse("");
            rows.add(node(
                    "release/unit/" + unit.id(),
                    (review.resumeState().currentUnitId().equals(Optional.of(unitId)) ? "[Saved cursor] " : "")
                            + (review.resumeState().deferredUnitIds().contains(unitId) ? "[Deferred] " : "")
                            + path.substring(path.lastIndexOf('/') + 1) + " · "
                            + unitRangeSummary("before", unit.beforeRanges()) + " → "
                            + unitRangeSummary("after", unit.afterRanges()) + " · "
                            + unit.semanticKey().orElse(unit.surfaceKind().name().toLowerCase(Locale.ROOT)) + " · "
                            + unit.operation().name().toLowerCase(Locale.ROOT) + " · " + unit.laneId() + limitation,
                    Kind.REVIEW_UNIT,
                    leaves,
                    null,
                    false
            ));
        }
        return rows;
    }

    private static String unitRangeSummary(String side, List<SFMReleaseReviewV1.Utf8Range> ranges) {
        if (ranges.isEmpty()) return side + " no byte range";
        return side + " " + ranges.stream().limit(2)
                .map(range -> "[" + range.startByte() + "," + range.endByte() + ")")
                .collect(java.util.stream.Collectors.joining(", "))
                + (ranges.size() > 2 ? " +" + (ranges.size() - 2) + " ranges" : "") + " bytes";
    }

    private static String sideCoverage(String side, Optional<String> revision,
                                       SFMReleaseReviewCoverage.UnitCoverage coverage) {
        if (revision.isEmpty()) return side + " not present";
        String id = revision.orElseThrow();
        long approved = coverage.approved().stream().filter(range -> range.documentRevisionId().equals(id))
                .mapToLong(range -> range.endByte() - range.startByte()).sum();
        long required = coverage.required().stream().filter(range -> range.documentRevisionId().equals(id))
                .mapToLong(range -> range.endByte() - range.startByte()).sum();
        return side + " " + approved + "/" + required + " bytes effectively approved";
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
        return comments(session, Map.of(), Map.of(), Map.of(), hashtags);
    }

    /** Evaluate once per comment, not once per source row. Only exact resolved ranges are listed. */
    private static Map<String, List<Node>> documentCommentChildren(SFMReviewSessionV2 session) {
        return documentCommentChildren(session, comment ->
                ca.teamdman.sfm.client.review.session.SFMReviewSessionV2Kernel.evaluateComment(session, comment));
    }

    private static Map<String, List<Node>> documentCommentChildren(SFMReviewSessionV2 session,
            java.util.function.Function<SFMReviewSessionV2.Comment,
                    ca.teamdman.sfm.client.review.session.SFMReviewSessionV2Kernel.Evaluation> evaluate) {
        Map<String, List<Node>> result = new LinkedHashMap<>();
        for (var comment : session.comments()) {
            if (ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewGeneratedMarkers.isChangeMarker(comment)) continue;
            var evaluation = evaluate.apply(comment);
            if (evaluation.status() != ca.teamdman.sfm.client.review.session.SFMReviewSessionV2Kernel.Status.RESOLVED_EXACTLY
                    && evaluation.status() != ca.teamdman.sfm.client.review.session.SFMReviewSessionV2Kernel.Status.RESOLVED_WITH_RELOCATION) continue;
            Set<String> revisions = new LinkedHashSet<>();
            evaluation.ranges().forEach(range -> revisions.add(range.documentRevisionId()));
            for (String revision : revisions) {
                String id = "document-comment/" + revision + "/" + comment.id();
                String preview = comment.text().replaceAll("\\s+", " ").strip();
                if (preview.length() > 120) preview = preview.substring(0, 120) + "…";
                if (preview.isEmpty()) preview = "(empty comment)";
                SourceLeaf value = new SourceLeaf(id + "/value", "Comment · " + preview,
                        "comment.txt", comment.text(), false);
                result.computeIfAbsent(revision, ignored -> new ArrayList<>()).add(
                        node(id, value.title(), Kind.COMMENT, List.of(), value,
                                new CommentNavigation(comment.id()), false));
            }
        }
        return result;
    }

    private static SFMReviewExplorerModel sessionComments(SFMReviewSessionV2 session, boolean hashtags) {
        Objects.requireNonNull(session, "session");
        SFMReviewCommentDataSource.SessionView view = new SFMReviewCommentKernelDataSource(session).refresh();
        Map<String, CandidateNavigation> candidateTargets = new LinkedHashMap<>();
        Map<String, SFMReviewSessionV2.Comment> durableComments = new LinkedHashMap<>();
        Map<String, SFMReviewSessionV1.DocumentRevision> durableDocuments = new LinkedHashMap<>();
        for (SFMReviewSessionV1.RevisionLane lane : session.revisionLanes()) {
            lane.before().documents().forEach(document -> durableDocuments.put(document.id(), document));
            lane.after().documents().forEach(document -> durableDocuments.put(document.id(), document));
        }
        for (SFMReviewSessionV2.Comment comment : session.comments()) {
            durableComments.put(comment.id(), comment);
            if (comment.target() instanceof SFMReviewSessionV2.CandidateTrajectoryTarget target) {
                candidateTargets.put(comment.id(), new CandidateNavigation(comment.id(), target));
            }
        }
        return comments(view, candidateTargets, durableComments, durableDocuments, hashtags);
    }

    private static SFMReviewExplorerModel comments(
            SFMReviewCommentDataSource.SessionView session,
            Map<String, CandidateNavigation> candidateTargets,
            Map<String, SFMReviewSessionV2.Comment> durableComments,
            Map<String, SFMReviewSessionV1.DocumentRevision> durableDocuments,
            boolean hashtags
    ) {
        Map<String, SFMReviewCommentDataSource.DocumentView> documents = new LinkedHashMap<>();
        for (SFMReviewCommentDataSource.DocumentView document : session.documents()) {
            documents.put(document.id(), document);
        }
        Node root = hashtags
                ? hashtagTree(session, documents, candidateTargets)
                : commentTree(session, documents, candidateTargets, durableComments, durableDocuments);
        return new SFMReviewExplorerModel(root);
    }

    private static Node commentTree(
            SFMReviewCommentDataSource.SessionView session,
            Map<String, SFMReviewCommentDataSource.DocumentView> documents,
            Map<String, CandidateNavigation> candidateTargets,
            Map<String, SFMReviewSessionV2.Comment> durableComments,
            Map<String, SFMReviewSessionV1.DocumentRevision> durableDocuments
    ) {
        List<Node> comments = new ArrayList<>();
        for (SFMReviewCommentDataSource.CommentView comment : newestFirst(session.comments())) {
            CandidateNavigation candidateTarget = candidateTargets.get(comment.id());
            comments.add(commentNode(
                    comment,
                    durableComments.get(comment.id()),
                    documents,
                    durableDocuments,
                    candidateTarget
            ));
        }
        return node("comments", "Comments · " + session.title(), Kind.ROOT, comments, null, true);
    }

    /**
     * A comment is projected as an inspectable object rather than as an id-prefixed bag of ranges.
     * The persisted target lives under {@code selector}; evaluator output lives under {@code matches}.
     * Keeping those branches separate prevents a relocated, ambiguous, or stale result from silently
     * replacing the reviewer's durable intent.
     */
    private static Node commentNode(
            SFMReviewCommentDataSource.CommentView comment,
            SFMReviewSessionV2.Comment durableComment,
            Map<String, SFMReviewCommentDataSource.DocumentView> documents,
            Map<String, SFMReviewSessionV1.DocumentRevision> durableDocuments,
            CandidateNavigation candidateTarget
    ) {
        String id = "comment/" + comment.id();
        List<Node> properties = List.of(
                commentValueNode(id, comment),
                commentSelectorNode(id, comment, durableComment, documents, candidateTarget),
                commentMatchesNode(id, comment, documents, durableDocuments),
                commentProvenanceNode(id, comment, durableComment)
        );
        return node(
                id,
                commentHeadline(comment, documents, candidateTarget),
                Kind.COMMENT,
                properties,
                null,
                candidateTarget,
                false
        );
    }

    private static Node commentValueNode(String commentNodeId, SFMReviewCommentDataSource.CommentView comment) {
        String id = commentNodeId + "/value";
        SourceLeaf value = new SourceLeaf(
                id,
                "comment value · " + preview(comment.text(), 72),
                "review-comments/" + stableId(comment.id()) + ".txt",
                comment.text(),
                false
        );
        return node(id, "value · " + preview(comment.text(), 96), Kind.REGION, List.of(), value, true);
    }

    private static Node commentSelectorNode(
            String commentNodeId,
            SFMReviewCommentDataSource.CommentView view,
            SFMReviewSessionV2.Comment durableComment,
            Map<String, SFMReviewCommentDataSource.DocumentView> documents,
            CandidateNavigation candidateTarget
    ) {
        String id = commentNodeId + "/selector";
        List<Node> fields = new ArrayList<>();
        if (durableComment == null) {
            fields.add(propertyNode(id + "/kind", "kind · " + (view.candidate() ? "candidate" : "committed")));
            fields.add(propertyNode(id + "/expression", "expression · " + view.targetLabel()));
            fields.add(propertyNode(id + "/rule", "rule · unavailable in view-only fixture projection"));
            fields.add(propertyNode(id + "/literal-witnesses",
                    "literal witnesses · unavailable in view-only fixture projection"));
            fields.add(propertyNode(id + "/semantic-evidence",
                    "semantic evidence · unavailable in view-only fixture projection"));
            return node(id, "selector · " + view.targetLabel(), Kind.REGION, fields, null, candidateTarget, false);
        }

        if (durableComment.target() instanceof SFMReviewSessionV2.CommittedReviewTarget committed) {
            fields.add(propertyNode(id + "/kind", "kind · committed source selection"));
            fields.add(propertyNode(id + "/expression",
                    "expression · " + selectionRuleExpression(committed.selectionRule())));
            fields.add(selectionRuleNode(id + "/rule", committed.selectionRule(), documents));
            List<SFMReviewSessionV1.LiteralUtf8Range> literals = new ArrayList<>();
            collectLiteralRules(committed.selectionRule(), literals);
            fields.add(node(
                    id + "/literal-witnesses",
                    "literal witnesses (" + literals.size() + ")",
                    Kind.REGION,
                    java.util.stream.IntStream.range(0, literals.size())
                            .mapToObj(index -> literalWitnessNode(
                                    id + "/literal-witnesses/" + index,
                                    literals.get(index),
                                    documents
                            ))
                            .toList(),
                    null,
                    false
            ));
            fields.add(committedSemanticEvidenceNode(id + "/semantic-evidence", committed));
            return node(
                    id,
                    "selector · committed · " + preview(selectionRuleExpression(committed.selectionRule()), 84),
                    Kind.REGION,
                    fields,
                    null,
                    false
            );
        }

        SFMReviewSessionV2.CandidateTrajectoryTarget candidate =
                (SFMReviewSessionV2.CandidateTrajectoryTarget) durableComment.target();
        fields.add(propertyNode(id + "/kind", "kind · candidate "
                + candidate.targetKind().name().toLowerCase(Locale.ROOT)));
        fields.add(propertyNode(id + "/expression", "expression · " + candidate.canonicalAddress()));
        fields.add(candidateRuleNode(id + "/rule", candidate, candidateTarget));
        fields.add(candidateLiteralWitnessNode(id + "/literal-witness", candidate));
        fields.add(candidateSemanticEvidenceNode(id + "/semantic-evidence", candidate));
        return node(
                id,
                "selector · candidate " + candidate.targetKind().name().toLowerCase(Locale.ROOT)
                        + " · " + candidate.canonicalAddress(),
                Kind.REGION,
                fields,
                null,
                candidateTarget,
                false
        );
    }

    private static Node selectionRuleNode(
            String id,
            SFMReviewSessionV1.SelectionRule rule,
            Map<String, SFMReviewCommentDataSource.DocumentView> documents
    ) {
        if (rule instanceof SFMReviewSessionV1.LiteralUtf8Range literal) {
            return literalWitnessNode(id, literal, documents);
        }
        if (rule instanceof SFMReviewSessionV1.Union union) {
            return node(id, "rule · union (" + union.rules().size() + ")", Kind.REGION,
                    indexedRuleNodes(id, union.rules(), documents), null, false);
        }
        if (rule instanceof SFMReviewSessionV1.Intersection intersection) {
            return node(id, "rule · intersection (" + intersection.rules().size() + ")", Kind.REGION,
                    indexedRuleNodes(id, intersection.rules(), documents), null, false);
        }
        SFMReviewSessionV1.Difference difference = (SFMReviewSessionV1.Difference) rule;
        Node include = selectionRuleNode(id + "/include", difference.include(), documents);
        Node exclude = node(
                id + "/exclude",
                "exclude (" + difference.exclude().size() + ")",
                Kind.REGION,
                indexedRuleNodes(id + "/exclude", difference.exclude(), documents),
                null,
                false
        );
        return node(id, "rule · difference", Kind.REGION, List.of(include, exclude), null, false);
    }

    private static List<Node> indexedRuleNodes(
            String id,
            List<SFMReviewSessionV1.SelectionRule> rules,
            Map<String, SFMReviewCommentDataSource.DocumentView> documents
    ) {
        return java.util.stream.IntStream.range(0, rules.size())
                .mapToObj(index -> selectionRuleNode(id + "/" + index, rules.get(index), documents))
                .toList();
    }

    private static Node literalWitnessNode(
            String id,
            SFMReviewSessionV1.LiteralUtf8Range literal,
            Map<String, SFMReviewCommentDataSource.DocumentView> documents
    ) {
        SFMReviewCommentDataSource.DocumentView document = documents.get(literal.documentRevisionId());
        String location = document == null
                ? literal.documentRevisionId()
                : sideLabel(document.side()) + " · " + document.path();
        return node(
                id,
                "literal witness · " + location + " [" + literal.startByte() + ".." + literal.endByte() + ")",
                Kind.REGION,
                List.of(
                        propertyNode(id + "/document-revision", "document revision · "
                                + literal.documentRevisionId()),
                        propertyNode(id + "/document-sha256", "document SHA-256 · "
                                + literal.documentSha256()),
                        propertyNode(id + "/selected-text-sha256", "selected text SHA-256 · "
                                + literal.selectedTextSha256())
                ),
                null,
                false
        );
    }

    private static void collectLiteralRules(
            SFMReviewSessionV1.SelectionRule rule,
            List<SFMReviewSessionV1.LiteralUtf8Range> output
    ) {
        if (rule instanceof SFMReviewSessionV1.LiteralUtf8Range literal) {
            output.add(literal);
        } else if (rule instanceof SFMReviewSessionV1.Union union) {
            union.rules().forEach(child -> collectLiteralRules(child, output));
        } else if (rule instanceof SFMReviewSessionV1.Intersection intersection) {
            intersection.rules().forEach(child -> collectLiteralRules(child, output));
        } else {
            SFMReviewSessionV1.Difference difference = (SFMReviewSessionV1.Difference) rule;
            collectLiteralRules(difference.include(), output);
            difference.exclude().forEach(child -> collectLiteralRules(child, output));
        }
    }

    private static Node committedSemanticEvidenceNode(
            String id,
            SFMReviewSessionV2.CommittedReviewTarget committed
    ) {
        if (committed.candidatePromotion().isEmpty()) {
            return propertyNode(id, "semantic evidence · none");
        }
        SFMReviewSessionV2.CandidatePromotionLink promotion = committed.candidatePromotion().orElseThrow();
        List<Node> fields = new ArrayList<>(List.of(
                propertyNode(id + "/source-comment", "source candidate comment · "
                        + promotion.sourceCandidateCommentId()),
                propertyNode(id + "/source-target-sha256", "source target SHA-256 · "
                        + promotion.sourceCandidateTargetSha256()),
                propertyNode(id + "/decision", "decision · " + promotion.decisionId()),
                propertyNode(id + "/history-head", "executed history head · "
                        + promotion.executedHistoryHeadId()),
                propertyNode(id + "/state", "executed state · " + promotion.executedStateId()
                        + " @ " + promotion.executedStateHash()),
                propertyNode(id + "/correspondence", "correspondence · " + promotion.correspondence())
        ));
        for (int index = 0; index < promotion.correspondenceEvidence().size(); index++) {
            fields.add(propertyNode(id + "/correspondence-evidence/" + index,
                    "evidence · " + promotion.correspondenceEvidence().get(index)));
        }
        return node(id, "semantic evidence · candidate promotion", Kind.REGION, fields, null, false);
    }

    private static Node candidateRuleNode(
            String id,
            SFMReviewSessionV2.CandidateTrajectoryTarget candidate,
            CandidateNavigation navigation
    ) {
        List<Node> fields = new ArrayList<>();
        if (navigation != null) {
            fields.add(candidateTargetNode(id + "/open", navigation));
        }
        fields.add(propertyNode(id + "/machine", "machine · " + candidate.machineId()
                + " @ " + candidate.machineRevision()));
        fields.add(propertyNode(id + "/plan", "plan · " + candidate.trajectoryPlanRevisionId()));
        fields.add(propertyNode(id + "/route", "route · " + candidate.routeId()
                + " @ " + candidate.routeStepPosition()));
        fields.add(propertyNode(id + "/step", "step · " + candidate.trajectoryStepId().orElse("route start")));
        fields.add(propertyNode(id + "/action", "action · " + candidate.actionIntentId().orElse("none")));
        fields.add(propertyNode(id + "/state", "predicted state · " + candidate.predictedStateId()));
        fields.add(propertyNode(id + "/state-hash", "predicted state hash · "
                + candidate.predictedStateHash().orElse("unavailable")));
        fields.add(propertyNode(id + "/projection-status", "projection status · "
                + projectionStatusLabel(candidate)));
        return node(id, "rule · immutable candidate trajectory target", Kind.REGION, fields, null, navigation, false);
    }

    private static Node candidateLiteralWitnessNode(
            String id,
            SFMReviewSessionV2.CandidateTrajectoryTarget candidate
    ) {
        if (candidate.projectedDocumentSelection().isEmpty()) {
            return propertyNode(id, "literal witness · none");
        }
        SFMReviewSessionV2.ProjectedDocumentSelection selection =
                candidate.projectedDocumentSelection().orElseThrow();
        return node(
                id,
                "literal witness · projected document " + selection.documentId()
                        + " [" + selection.startByte() + ".." + selection.endByte() + ")",
                Kind.REGION,
                List.of(
                        propertyNode(id + "/document-state-hash", "document state hash · "
                                + selection.documentStateHash()),
                        propertyNode(id + "/document-text-sha256", "document text SHA-256 · "
                                + selection.documentTextSha256()),
                        propertyNode(id + "/selected-text-sha256", "selected text SHA-256 · "
                                + selection.selectedTextSha256())
                ),
                null,
                false
        );
    }

    private static Node candidateSemanticEvidenceNode(
            String id,
            SFMReviewSessionV2.CandidateTrajectoryTarget candidate
    ) {
        List<Node> evidence = new ArrayList<>();
        evidence.add(propertyNode(id + "/evaluator-revision", "evaluator revision · "
                + candidate.evaluatorRevision().orElse("unavailable")));
        for (int index = 0; index < candidate.evaluatorEvidence().size(); index++) {
            var item = candidate.evaluatorEvidence().get(index);
            evidence.add(propertyNode(id + "/item/" + index, item.key() + " · " + item.value()));
        }
        return node(
                id,
                "semantic evidence (" + candidate.evaluatorEvidence().size() + ")",
                Kind.REGION,
                evidence,
                null,
                false
        );
    }

    private static Node commentMatchesNode(
            String commentNodeId,
            SFMReviewCommentDataSource.CommentView comment,
            Map<String, SFMReviewCommentDataSource.DocumentView> documents,
            Map<String, SFMReviewSessionV1.DocumentRevision> durableDocuments
    ) {
        String id = commentNodeId + "/matches";
        boolean rangesAreMatches = rangesAreMatches(comment.evaluationStatus());
        int matchCount = rangesAreMatches ? comment.ranges().size() : 0;
        List<Node> ranges = new ArrayList<>();
        for (int index = 0; index < comment.ranges().size(); index++) {
            ranges.add(matchRangeNode(
                    id + "/" + index,
                    index,
                    comment.ranges().get(index),
                    documents,
                    durableDocuments,
                    rangesAreMatches,
                    comment.evaluationStatus()
            ));
        }
        if (ranges.isEmpty()) {
            ranges.add(propertyNode(id + "/none", "no derived matches · "
                    + evaluationStatusLabel(comment.evaluationStatus())));
        }
        return node(
                id,
                "matches (" + matchCount + ") · " + evaluationStatusLabel(comment.evaluationStatus()),
                Kind.REGION,
                ranges,
                null,
                false
        );
    }

    private static Node matchRangeNode(
            String id,
            int index,
            SFMReviewCommentDataSource.RangeView range,
            Map<String, SFMReviewCommentDataSource.DocumentView> documents,
            Map<String, SFMReviewSessionV1.DocumentRevision> durableDocuments,
            boolean isMatch,
            SFMReviewCommentDataSource.EvaluationStatus status
    ) {
        SFMReviewCommentDataSource.DocumentView document = documents.get(range.documentRevisionId());
        String role = isMatch
                ? status == SFMReviewCommentDataSource.EvaluationStatus.AMBIGUOUS
                        ? "possible match"
                        : status == SFMReviewCommentDataSource.EvaluationStatus.RESOLVED_WITH_RELOCATION
                                ? "relocated match"
                                : "match"
                : "stale witness (not a match)";
        if (document == null) {
            return propertyNode(id, role + " · unavailable document " + range.documentRevisionId()
                    + " [" + range.startByte() + ".." + range.endByte() + ")");
        }
        String location = sideLabel(document.side()) + " · " + document.path()
                + " [" + range.startByte() + ".." + range.endByte() + ")";
        if (!validUtf8Range(document.text(), range)) {
            return propertyNode(id, role + " · " + location + " · source range unavailable");
        }
        String excerpt = selectedExcerpt(document.text(), range);
        SFMReviewSessionV1.DocumentRevision durableDocument = durableDocuments.get(range.documentRevisionId());
        SourceLeaf leaf = durableDocument == null
                ? leafForRange(id, role + " " + (index + 1), document, range)
                : new SourceLeaf(
                        id,
                        role + " " + (index + 1) + " · " + sideLabel(document.side())
                                + " · " + document.path(),
                        durableDocument.path(),
                        durableDocument.text(),
                        false,
                        Optional.of(durableDocument.id()),
                        Optional.of(durableDocument.sha256()),
                        Optional.of(new SFMReleaseReviewV1.Utf8Range(range.startByte(), range.endByte()))
                );
        return node(
                id,
                role + " · " + location + " · “" + preview(excerpt, 64) + "”",
                Kind.REGION,
                List.of(),
                leaf,
                true
        );
    }

    private static Node commentProvenanceNode(
            String commentNodeId,
            SFMReviewCommentDataSource.CommentView view,
            SFMReviewSessionV2.Comment durableComment
    ) {
        String id = commentNodeId + "/provenance";
        if (durableComment == null) {
            return node(
                    id,
                    "provenance · " + view.provenance(),
                    Kind.REGION,
                    List.of(
                            propertyNode(id + "/id", "id · " + view.id()),
                            propertyNode(id + "/producer", "producer · " + view.provenance()),
                            propertyNode(id + "/kind-schema", "kind/schema · unavailable in view-only fixture projection"),
                            propertyNode(id + "/parents", "parents (0) · unavailable in view-only fixture projection")
                    ),
                    null,
                    false
            );
        }
        SFMReviewSessionV1.Provenance provenance = durableComment.provenance();
        List<Node> parents = java.util.stream.IntStream.range(0, provenance.parentCommentIds().size())
                .mapToObj(index -> propertyNode(
                        id + "/parents/" + index,
                        "parent · " + provenance.parentCommentIds().get(index)
                ))
                .toList();
        Node parentNode = node(
                id + "/parents",
                "parents (" + parents.size() + ")" + (parents.isEmpty() ? " · none" : ""),
                Kind.REGION,
                parents,
                null,
                false
        );
        return node(
                id,
                "provenance · " + provenance.kind() + " · " + provenance.producer(),
                Kind.REGION,
                List.of(
                        propertyNode(id + "/id", "id · " + durableComment.id()),
                        propertyNode(id + "/producer", "producer · " + provenance.producer()),
                        propertyNode(id + "/kind-schema", "kind/schema · "
                                + provenance.kind() + "@" + provenance.version()),
                        parentNode
                ),
                null,
                false
        );
    }

    private static Node propertyNode(String id, String label) {
        return node(id, label, Kind.REGION, List.of(), null, true);
    }

    private static String commentHeadline(
            SFMReviewCommentDataSource.CommentView comment,
            Map<String, SFMReviewCommentDataSource.DocumentView> documents,
            CandidateNavigation candidateTarget
    ) {
        StringBuilder label = new StringBuilder(comment.archived() ? "[archived] " : "")
                .append(commentTextPreview(comment.text()));
        Optional<SFMReviewCommentDataSource.RangeView> firstRange = comment.ranges().stream().findFirst();
        if (firstRange.isPresent()) {
            SFMReviewCommentDataSource.RangeView range = firstRange.orElseThrow();
            SFMReviewCommentDataSource.DocumentView document = documents.get(range.documentRevisionId());
            label.append(" · ");
            if (document == null) {
                label.append(range.documentRevisionId());
            } else {
                label.append(sideLabel(document.side())).append(" · ").append(document.path());
            }
            label.append(" [").append(range.startByte()).append("..").append(range.endByte()).append(')');
        } else if (candidateTarget != null) {
            label.append(" · candidate ")
                    .append(candidateTarget.target().targetKind().name().toLowerCase(Locale.ROOT));
        } else {
            label.append(" · 0 matches");
        }
        if (comment.evaluationStatus() != SFMReviewCommentDataSource.EvaluationStatus.RESOLVED_EXACTLY) {
            label.append(" · ").append(evaluationStatusLabel(comment.evaluationStatus()));
        }
        return label.toString();
    }

    private static String commentTextPreview(String text) {
        String normalized = preview(text, 88);
        Set<String> tags = SFMCommentHashtags.derive(text);
        if (tags.isEmpty() || normalized.startsWith("#")) return normalized;
        return String.join(" ", tags) + " · " + normalized;
    }

    private static String selectionRuleExpression(SFMReviewSessionV1.SelectionRule rule) {
        if (rule instanceof SFMReviewSessionV1.LiteralUtf8Range literal) {
            return literal.documentRevisionId() + '[' + literal.startByte() + ',' + literal.endByte() + ')';
        }
        if (rule instanceof SFMReviewSessionV1.Union union) {
            return "union(" + union.rules().stream()
                    .map(SFMReviewExplorerModel::selectionRuleExpression)
                    .collect(java.util.stream.Collectors.joining(", ")) + ')';
        }
        if (rule instanceof SFMReviewSessionV1.Intersection intersection) {
            return "intersection(" + intersection.rules().stream()
                    .map(SFMReviewExplorerModel::selectionRuleExpression)
                    .collect(java.util.stream.Collectors.joining(", ")) + ')';
        }
        SFMReviewSessionV1.Difference difference = (SFMReviewSessionV1.Difference) rule;
        return "difference(" + selectionRuleExpression(difference.include()) + "; exclude="
                + difference.exclude().stream()
                .map(SFMReviewExplorerModel::selectionRuleExpression)
                .collect(java.util.stream.Collectors.joining(", ")) + ')';
    }

    private static boolean rangesAreMatches(SFMReviewCommentDataSource.EvaluationStatus status) {
        return status == SFMReviewCommentDataSource.EvaluationStatus.RESOLVED_EXACTLY
                || status == SFMReviewCommentDataSource.EvaluationStatus.RESOLVED_WITH_RELOCATION
                || status == SFMReviewCommentDataSource.EvaluationStatus.AMBIGUOUS;
    }

    private static String evaluationStatusLabel(SFMReviewCommentDataSource.EvaluationStatus status) {
        return status.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String sideLabel(SFMReviewCommentDataSource.Side side) {
        return side.name().toLowerCase(Locale.ROOT);
    }

    private static String selectedExcerpt(
            String text,
            SFMReviewCommentDataSource.RangeView range
    ) {
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return new String(
                bytes,
                range.startByte(),
                range.endByte() - range.startByte(),
                java.nio.charset.StandardCharsets.UTF_8
        );
    }

    private static boolean validUtf8Range(
            String text,
            SFMReviewCommentDataSource.RangeView range
    ) {
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return range.startByte() >= 0
                && range.endByte() >= range.startByte()
                && range.endByte() <= bytes.length
                && isUtf8Boundary(bytes, range.startByte())
                && isUtf8Boundary(bytes, range.endByte());
    }

    private static boolean isUtf8Boundary(byte[] bytes, int offset) {
        return offset >= 0 && offset <= bytes.length
                && (offset == bytes.length || (bytes[offset] & 0xC0) != 0x80);
    }

    private static String preview(String text, int maximumCodePoints) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints <= maximumCodePoints) return normalized;
        int end = normalized.offsetByCodePoints(0, maximumCodePoints);
        return normalized.substring(0, end).stripTrailing() + "…";
    }

    private static Node hashtagTree(
            SFMReviewCommentDataSource.SessionView session,
            Map<String, SFMReviewCommentDataSource.DocumentView> documents,
            Map<String, CandidateNavigation> candidateTargets
    ) {
        Map<String, Map<String, List<Node>>> grouped = new TreeMap<>();
        Map<String, List<Node>> candidateGrouped = new TreeMap<>();
        for (SFMReviewCommentDataSource.CommentView comment : newestFirst(session.comments())) {
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

    /** The persisted list is append-only chronological history; review feeds present the newest work first. */
    private static List<SFMReviewCommentDataSource.CommentView> newestFirst(
            List<SFMReviewCommentDataSource.CommentView> comments
    ) {
        ArrayList<SFMReviewCommentDataSource.CommentView> answer = new ArrayList<>(comments);
        java.util.Collections.reverse(answer);
        return List.copyOf(answer);
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
        String language = ca.teamdman.sfm.client.text_editor.SFMTextDocumentLanguage.fromFileName(path).id();
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

    private static String releasePairHierarchyPath(ReleasePair pair) {
        return pair.pathAfter().or(() -> pair.pathBefore())
                .map(SFMReviewExplorerModel::normalizeReviewPath)
                .filter(path -> !path.isBlank())
                .orElseGet(() -> normalizeReviewPath(releasePairLabel(pair)));
    }

    private static String releaseHierarchyFileLabel(ReleasePair pair) {
        String before = pair.pathBefore().map(SFMReviewExplorerModel::fileName).orElse("");
        String after = pair.pathAfter().map(SFMReviewExplorerModel::fileName).orElse("");
        if (before.isEmpty()) return after;
        if (after.isEmpty() || before.equals(after)) return before;
        return before + " → " + after;
    }

    private static List<Node> releasePathHierarchy(List<ReleaseFileProjection> files) {
        ReleaseDirectoryBuilder root = new ReleaseDirectoryBuilder("", "release-root");
        for (ReleaseFileProjection file : files) {
            List<String> segments = java.util.Arrays.stream(normalizeReviewPath(file.hierarchyPath()).split("/"))
                    .filter(segment -> !segment.isBlank())
                    .toList();
            ReleaseDirectoryBuilder directory = root;
            for (int index = 0; index + 1 < segments.size(); index++) {
                directory = directory.directory(segments.get(index));
            }
            directory.files.add(file.node());
        }
        ArrayList<Node> answer = new ArrayList<>(root.directories.size() + root.files.size());
        root.directories.values().stream().map(ReleaseDirectoryBuilder::build).forEach(answer::add);
        root.files.stream().sorted(Comparator.comparing(Node::label)).forEach(answer::add);
        return List.copyOf(answer);
    }

    private static String normalizeReviewPath(String path) {
        return Objects.requireNonNull(path, "path").replace('\\', '/');
    }

    private static String fileName(String path) {
        String normalized = normalizeReviewPath(path);
        int separator = normalized.lastIndexOf('/');
        return separator < 0 ? normalized : normalized.substring(separator + 1);
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

    private static SourceLeaf splitLeaf(SourceLeaf inline) {
        return new SourceLeaf(inline.id() + "/split", inline.title().replace("(inline)", "(split)"),
                inline.path(), inline.text(), inline.missing(), inline.documentRevisionId(), inline.sha256(),
                inline.targetRange(), inline.generatedSurface().map(SFMReleaseReviewSurfaceV1.Recipe::asSplit));
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
        return boundedDiffLeaf(id, title + " · " + pair.laneId() + " · " + path, path,
                filePair.orElseThrow(), kind);
    }

    static SourceLeaf boundedDiffLeaf(String id, String title, String path,
                                     SFMReleaseReviewSurfaceV1.FilePair pair,
                                     SFMReleaseReviewSurfaceV1.SurfaceKind kind) {
        try {
            return new SourceLeaf(id, title, path, "", false, Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.of(new SFMReleaseReviewSurfaceV1.Recipe(pair, kind)));
        } catch (SFMReleaseReviewSurfaceV1.SourceLimitException limited) {
            // Preserve the row and exact explanation; do not abort unrelated files or bypass the worker bound.
            return new SourceLeaf(id, title + " · unavailable", path,
                    "Review diff unavailable\ncode: review.surface.source-oversized\n" + limited.getMessage()
                            + "\nThe immutable before/after sources and all other review rows are retained.\n",
                    false);
        }
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
                .map(binding -> binding.laneId() + ":" + revisionLabel(binding.beforeCommit())
                        + "→" + candidateLabel(binding))
                .collect(java.util.stream.Collectors.joining(", "));
    }

    /** Saved aliases such as HEAD are historical input, never evidence of the current checkout. */
    static Node historicalSourceNode(SourceLeaf source, Map<String, List<Node>> documentComments) {
        return node(source.id(), "Commented snapshot · " + source.path(), Kind.REVISION,
                source.documentRevisionId().map(documentComments::get).orElse(List.of()), source, false);
    }

    private static String revisionLabel(String revision) {
        return revision.substring(0, Math.min(12, revision.length()));
    }

    private static String candidateLabel(SFMReleaseReviewV1.RepositoryBinding binding) {
        return binding.workingTreeCapture().map(capture -> ca.teamdman.sfm.client.search.SFMExplorerSearchText.value(
                ca.teamdman.sfm.client.search.SFMExplorerSearchText.CAPTURE_LABEL,
                revisionLabel(capture.id().substring(SFMWorkingTreeCaptureV1.ID_PREFIX.length())),
                java.time.Instant.ofEpochMilli(capture.capturedAtUnixMs())))
                .orElseGet(() -> revisionLabel(binding.candidateCommit()));
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
