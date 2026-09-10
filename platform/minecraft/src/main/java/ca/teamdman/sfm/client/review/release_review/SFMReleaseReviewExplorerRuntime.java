package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.action.OpenPanelAction;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMReleaseReviewAction;
import ca.teamdman.sfm.client.action.SFMReviewChangesLayoutSetAction;
import ca.teamdman.sfm.client.context.SFMContextTextCoordinates;
import ca.teamdman.sfm.client.explorer.SFMChildEdge;
import ca.teamdman.sfm.client.explorer.SFMChildPage;
import ca.teamdman.sfm.client.explorer.SFMExplorerRuntime;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMPathExpression;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerCancellationToken;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerFilterDomainResolver;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerResolver;
import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import ca.teamdman.sfm.client.search.SFMFuzzyScorer;
import ca.teamdman.sfm.client.search.SFMTextMatcher;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntryMatch;
import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPreviewPlacement;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import com.mojang.brigadier.arguments.StringArgumentType;
import ca.teamdman.sfm.client.screen.review.explorer.SFMReviewExplorerModel;
import ca.teamdman.sfm.client.screen.workspace.SFMReleaseReviewExplorerScreenType;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentLanguage;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSource;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorPanelRecipe;
import ca.teamdman.sfm.client.theme.SFMClientThemeService;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

/**
 * Generic lazy-explorer projection over the currently opened portable review.
 *
 * <p>The review file remains the explorer's visible location. Opaque
 * {@code review-tree://} paths are only resolver identities for its rows, so
 * view/layout state never becomes part of the durable review address.</p>
 */
public final class SFMReleaseReviewExplorerRuntime implements SFMExplorerFilterDomainResolver {
    public static final String PATH_SCHEME = "review-tree";
    private static final SFMReleaseReviewExplorerRuntime INSTANCE =
            new SFMReleaseReviewExplorerRuntime(SFMReleaseReviewRuntime.get());

    private record Lens(
            SFMPath root,
            Path reviewPath,
            long reviewOpenEpoch,
            SFMReleaseReviewExplorerScreenType.Projection projection,
            SFMReviewExplorerModel.PathLayout changesPathLayout,
            Optional<String> query,
            String title
    ) {
        private Lens {
            Objects.requireNonNull(root, "root");
            reviewPath = Objects.requireNonNull(reviewPath, "reviewPath").toAbsolutePath().normalize();
            Objects.requireNonNull(projection, "projection");
            Objects.requireNonNull(changesPathLayout, "changesPathLayout");
            Objects.requireNonNull(query, "query");
            Objects.requireNonNull(title, "title");
        }
    }

    /** Public, immutable identity for the review projection hosted by one exact Explorer. */
    public record LensDescriptor(
            SFMPath root,
            Path reviewPath,
            long reviewOpenEpoch,
            SFMReleaseReviewExplorerScreenType.Projection projection,
            SFMReviewExplorerModel.PathLayout changesPathLayout,
            Optional<String> query,
            String title
    ) {
        public LensDescriptor {
            Objects.requireNonNull(root, "root");
            reviewPath = Objects.requireNonNull(reviewPath, "reviewPath").toAbsolutePath().normalize();
            Objects.requireNonNull(projection, "projection");
            Objects.requireNonNull(changesPathLayout, "changesPathLayout");
            query = Objects.requireNonNull(query, "query").map(String::strip).filter(value -> !value.isEmpty());
            Objects.requireNonNull(title, "title");
        }
    }

    private record ChangesIndex(SFMReleaseReviewV1 source, Map<String, SFMPath> nodePaths,
                                Map<DocumentIdentity, List<SFMPath>> revealPaths) {}

    private static final class ProjectionSnapshot {
        private final long generation;
        private final SFMPath root;
        private final Map<SFMPath, SFMExplorerEntry> entries = new TreeMap<>();
        private final Map<SFMPath, SFMReviewExplorerModel.Node> nodes = new TreeMap<>();
        private final Map<SFMPath, SFMReviewExplorerModel.SourceLeaf> leaves = new TreeMap<>();
        private final Map<SFMPath, String> nodeIds = new TreeMap<>();
        private final Map<String, SFMPath> pathsByNodeId = new HashMap<>();
        private Map<DocumentIdentity, List<SFMPath>> revealPaths = new HashMap<>();
        private Map<String, SFMPath> sharedNodePaths = Map.of();
        private final ChangesIndex changesIndex;

        private ProjectionSnapshot(
                long generation,
                SFMPath root,
                SFMReviewExplorerModel.Node modelRoot,
                SFMReleaseReviewRuntime.Snapshot review,
                SFMReleaseReviewSurfaceRuntime surfaceRuntime,
                boolean changesOnly,
                ChangesIndex reusableIndex
        ) {
            this.generation = generation;
            this.root = Objects.requireNonNull(root, "root");
            Objects.requireNonNull(modelRoot, "modelRoot");
            entries.put(root, SFMReleaseReviewExplorerRuntime.entry(
                    root, modelRoot, SFMItemIcon.vanilla("spyglass", "Release review")));
            nodes.put(root, modelRoot);
            nodeIds.put(root, modelRoot.id());
            pathsByNodeId.put(modelRoot.id(), root);
            if (reusableIndex != null) {
                sharedNodePaths = reusableIndex.nodePaths();
                revealPaths = reusableIndex.revealPaths();
                changesIndex = reusableIndex;
            } else {
                indexRevealPaths(root, modelRoot, review, surfaceRuntime, changesOnly);
                changesIndex = changesOnly ? new ChangesIndex(review.document().orElseThrow(),
                        Map.copyOf(pathsByNodeId), Map.copyOf(revealPaths)) : null;
            }
        }

        private synchronized SFMExplorerEntry entry(SFMPath path) {
            return entries.get(path);
        }

        private synchronized SFMReviewExplorerModel.SourceLeaf leaf(SFMPath path, SFMReleaseReviewV1 review) {
            SFMReviewExplorerModel.SourceLeaf cached = leaves.get(path);
            if (cached != null) return cached;
            // A comment-only generation reuses row addresses, but not the lazy node maps.
            // Resolve just this exact path instead of requiring the user to expand every
            // ancestor again (or eagerly materializing the complete review on the UI thread).
            SFMPath parentPath = root;
            SFMReviewExplorerModel.Node node = nodes.get(root);
            for (int depth = root.segments().size(); depth < path.segments().size(); depth++) {
                List<SFMReviewExplorerModel.Node> children = displayedChildren(node, review);
                SFMReviewExplorerModel.Node match = null;
                SFMPath matchedPath = null;
                for (int index = 0; index < children.size(); index++) {
                    SFMPath candidate = childPath(parentPath, index, children.get(index));
                    if (candidate.segments().get(depth).equals(path.segments().get(depth))) {
                        match = children.get(index);
                        matchedPath = candidate;
                        break;
                    }
                }
                if (match == null) return null;
                node = match;
                parentPath = matchedPath;
            }
            // Adding the first comment can make a source row expandable. The
            // trailing-slash presentation hint must not invalidate its source identity.
            if (!parentPath.scheme().equals(path.scheme())
                    || !parentPath.authority().equals(path.authority())
                    || !parentPath.revision().equals(path.revision())
                    || !parentPath.segments().equals(path.segments())) return null;
            if (node.leaf() != null) leaves.put(path, node.leaf());
            return node.leaf();
        }

        private synchronized String nodeId(SFMPath path) {
            return nodeIds.get(path);
        }

        private synchronized Optional<SFMReviewExplorerModel.NodeAction> nodeAction(SFMPath path) {
            var node = nodes.get(path);
            return node == null ? Optional.empty() : node.action();
        }

        private synchronized SFMPath pathForNodeId(String nodeId) {
            return pathsByNodeId.getOrDefault(nodeId, sharedNodePaths.get(nodeId));
        }

        private synchronized List<SFMReviewExplorerModel.Node> rootChildren() {
            return nodes.get(root).children();
        }

        private synchronized ChildSlice childSlice(
                SFMPath parentPath,
                int offset,
                int pageSize,
                SFMReleaseReviewV1 review
        ) {
            SFMReviewExplorerModel.Node parentNode = nodes.get(parentPath);
            if (parentNode == null) throw new IllegalArgumentException("Unknown release-review row " + parentPath);
            List<SFMReviewExplorerModel.Node> children = displayedChildren(parentNode, review);
            if (offset > children.size()) throw new IllegalArgumentException("Continuation exceeds review child set");
            int end = Math.min(children.size(), offset + pageSize);
            ArrayList<SFMExplorerEntry> page = new ArrayList<>(end - offset);
            for (int index = offset; index < end; index++) {
                SFMReviewExplorerModel.Node child = children.get(index);
                SFMPath childPath = childPath(parentPath, index, child);
                SFMExplorerEntry childEntry = entries.computeIfAbsent(
                        childPath, ignored -> SFMReleaseReviewExplorerRuntime.entry(
                                childPath, child, icon(child)
                        ));
                nodes.putIfAbsent(childPath, child);
                nodeIds.putIfAbsent(childPath, child.id());
                pathsByNodeId.putIfAbsent(child.id(), childPath);
                if (child.leaf() != null) leaves.putIfAbsent(childPath, child.leaf());
                page.add(childEntry);
            }
            return new ChildSlice(page, end, children.size());
        }

        private synchronized FilterDomain completeFilterDomain(
                SFMReleaseReviewV1 review,
                FilterDomainRequest request
        ) {
            SFMReviewExplorerModel.Node modelRoot = nodes.get(root);
            if (modelRoot == null) throw new IllegalArgumentException("Unknown release-review row " + root);
            ArrayList<SearchNode> directMatches = new ArrayList<>();
            ArrayList<String> matchDiagnostics = new ArrayList<>();
            var budget = new ca.teamdman.sfm.client.search.SFMMatchBudget(20_000_000, request.cancellation()::isCancelled);
            var matchEvidence = new HashMap<SFMPath, SFMExplorerEntryMatch>();
            matchEvidence.put(root, new SFMExplorerEntryMatch(false, Float.POSITIVE_INFINITY, List.of(), List.of(), true, List.of()));
            SearchNode searchRoot = buildSearchTree(
                    root,
                    modelRoot,
                    review,
                    SFMTextMatcher.compile(request.query(), request.options()),
                    request.cancellation(),
                    directMatches,
                    matchDiagnostics,
                    budget,
                    matchEvidence,
                    false
            );
            directMatches.sort(Comparator
                    .comparingDouble(SearchNode::score)
                    .thenComparing(node -> node.path().canonical()));
            Set<SearchNode> selectedMatches = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            directMatches.stream().limit(request.maximumMatches()).forEach(selectedMatches::add);
            markIncluded(searchRoot, selectedMatches);

            ArrayList<SFMExplorerEntry> publishedEntries = new ArrayList<>();
            ArrayList<SFMChildPage> pages = new ArrayList<>();
            TreeSet<SFMPath> matchedPaths = new TreeSet<>();
            materializeSearchTree(
                    searchRoot,
                    review,
                    selectedMatches,
                    publishedEntries,
                    pages,
                    matchedPaths
            );
            boolean complete = directMatches.size() <= request.maximumMatches() && matchDiagnostics.isEmpty();
            var publishedPaths = publishedEntries.stream().map(SFMExplorerEntry::path).collect(java.util.stream.Collectors.toSet());
            matchEvidence.entrySet().removeIf(e -> !publishedPaths.contains(e.getKey())
                    || e.getValue().matches() != matchedPaths.contains(e.getKey()));
            if (directMatches.size() > request.maximumMatches()) matchDiagnostics.add(
                    "Filter result was bounded to " + request.maximumMatches() + " of " + directMatches.size() + " matches");
            return new FilterDomain(
                    root,
                    request.query(),
                    publishedEntries,
                    pages,
                    matchedPaths,
                    directMatches.size(),
                    complete,
                    generation,
                    matchDiagnostics,
                    request.options(),
                    matchEvidence
            );
        }

        private SearchNode buildSearchTree(
                SFMPath path,
                SFMReviewExplorerModel.Node node,
                SFMReleaseReviewV1 review,
                SFMTextMatcher matcher,
                SFMExplorerCancellationToken cancellation,
                List<SearchNode> directMatches,
                List<String> matchDiagnostics,
                ca.teamdman.sfm.client.search.SFMMatchBudget budget,
                Map<SFMPath, SFMExplorerEntryMatch> matchEvidence,
                boolean candidate
        ) {
            cancellation.throwIfCancelled();
            float score = Float.POSITIVE_INFINITY;
            if (candidate) {
                var evidence = SFMExplorerEntryMatch.evaluate(new SFMExplorerEntry(path, node.label(), false,
                        Map.of(SFMExplorerEntry.SORT_NAME, SFMExplorerEntry.SortKey.available(node.label())),
                        reviewSearchTerms(node), List.of()), matcher, budget);
                matchEvidence.put(path, evidence);
                score = evidence.score();
                if (!evidence.complete() && matchDiagnostics.size() < 64) matchDiagnostics.add(
                        "Incomplete matching for " + node.label() + ": " + String.join("; ", evidence.diagnostics()));
            }
            SearchNode search = new SearchNode(path, node, score);
            if (score <= SFMFuzzyScorer.DEFAULT_THRESHOLD) directMatches.add(search);
            List<SFMReviewExplorerModel.Node> children = displayedChildren(node, review);
            for (int index = 0; index < children.size(); index++) {
                SFMReviewExplorerModel.Node child = children.get(index);
                search.children().add(buildSearchTree(
                        childPath(path, index, child),
                        child,
                        review,
                        matcher,
                        cancellation,
                        directMatches,
                        matchDiagnostics,
                        budget,
                        matchEvidence,
                        true
                ));
            }
            return search;
        }

        private static boolean markIncluded(SearchNode node, Set<SearchNode> selectedMatches) {
            boolean included = selectedMatches.contains(node);
            for (SearchNode child : node.children()) included |= markIncluded(child, selectedMatches);
            node.included = included;
            return included;
        }

        private void materializeSearchTree(
                SearchNode node,
                SFMReleaseReviewV1 review,
                Set<SearchNode> selectedMatches,
                List<SFMExplorerEntry> publishedEntries,
                List<SFMChildPage> pages,
                Set<SFMPath> matchedPaths
        ) {
            SFMExplorerEntry publishedEntry = entries.computeIfAbsent(
                    node.path(),
                    ignored -> SFMReleaseReviewExplorerRuntime.entry(
                            node.path(), node.node(), icon(node.node())
                    )
            );
            nodes.putIfAbsent(node.path(), node.node());
            nodeIds.putIfAbsent(node.path(), node.node().id());
            if (node.node().leaf() != null) leaves.putIfAbsent(node.path(), node.node().leaf());
            var topologyKeys = new java.util.TreeMap<>(publishedEntry.sortKeys());
            topologyKeys.put(SFMExplorerEntry.SUBJECT_COMPLETE_CHILD_COUNT,
                    SFMExplorerEntry.SortKey.available(Integer.toString(node.children().size())));
            publishedEntries.add(new SFMExplorerEntry(publishedEntry.path(), publishedEntry.label(), publishedEntry.expandable(),
                    topologyKeys, publishedEntry.searchTerms(), publishedEntry.diagnostics()));
            if (selectedMatches.contains(node)) matchedPaths.add(node.path());

            ArrayList<SFMChildEdge> edges = new ArrayList<>();
            for (SearchNode child : node.children()) {
                if (!child.included) continue;
                edges.add(new SFMChildEdge(node.path(), child.path()));
            }
            pages.add(new SFMChildPage(
                    node.path(),
                    edges,
                    Optional.empty(),
                    SFMChildPage.Completeness.COMPLETE,
                    generation,
                    List.of()
            ));
            for (SearchNode child : node.children()) {
                if (child.included) materializeSearchTree(
                        child,
                        review,
                        selectedMatches,
                        publishedEntries,
                        pages,
                        matchedPaths
                );
            }
        }

        private static final class SearchNode {
            private final SFMPath path;
            private final SFMReviewExplorerModel.Node node;
            private final float score;
            private final ArrayList<SearchNode> children = new ArrayList<>();
            private boolean included;

            private SearchNode(SFMPath path, SFMReviewExplorerModel.Node node, float score) {
                this.path = path;
                this.node = node;
                this.score = score;
            }

            private SFMPath path() { return path; }
            private SFMReviewExplorerModel.Node node() { return node; }
            private float score() { return score; }
            private ArrayList<SearchNode> children() { return children; }
        }

        private synchronized MaterializationEvidence materializationEvidence() {
            return new MaterializationEvidence(entries.size(), nodes.size(), leaves.size());
        }

        private synchronized List<SFMPath> revealPaths(SFMTextDocumentSnapshot document) {
            return DocumentIdentity.from(document)
                    .map(identity -> revealPaths.getOrDefault(identity, List.of()))
                    .orElseGet(List::of);
        }

        private void indexRevealPaths(
                SFMPath parentPath,
                SFMReviewExplorerModel.Node parentNode,
                SFMReleaseReviewRuntime.Snapshot review,
                SFMReleaseReviewSurfaceRuntime surfaceRuntime,
                boolean changesOnly
        ) {
            List<SFMReviewExplorerModel.Node> children = displayedChildren(
                    parentNode,
                    review.document().orElseThrow()
            );
            for (int index = 0; index < children.size(); index++) {
                SFMReviewExplorerModel.Node child = children.get(index);
                SFMPath childPath = childPath(parentPath, index, child);
                pathsByNodeId.putIfAbsent(child.id(), childPath);
                SFMReviewExplorerModel.SourceLeaf leaf = child.leaf();
                if (leaf != null && !leaf.missing() && leaf.generatedSurface().isEmpty()
                        && leaf.documentRevisionId().isPresent()) {
                        // Reveal lookup needs only immutable address fields, not checkout
                        // analysis identity (which may read/hash working files).
                        DocumentIdentity identity = pinnedIdentity(leaf);
                        ArrayList<SFMPath> paths = new ArrayList<>(
                                revealPaths.getOrDefault(identity, List.of())
                        );
                        paths.add(childPath);
                        revealPaths.put(identity, List.copyOf(paths));
                }
                // In Changes, revision children are comment values, not pinned source
                // targets. Index those lazily through childSlice; never cache stale comments.
                if (child.expandable() && !(changesOnly && child.kind() == SFMReviewExplorerModel.Kind.REVISION))
                    indexRevealPaths(childPath, child, review, surfaceRuntime, changesOnly);
            }
        }
    }

    private record DocumentIdentity(
            SFMPath path,
            SFMPath authorizedRoot,
            String sha256,
            Optional<SFMTextDocumentRange> targetRange
    ) {
        private DocumentIdentity {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(authorizedRoot, "authorizedRoot");
            Objects.requireNonNull(sha256, "sha256");
            targetRange = Objects.requireNonNull(targetRange, "targetRange");
        }

        private static DocumentIdentity from(SFMTextDocumentSource.PinnedSnapshot source) {
            return new DocumentIdentity(
                    source.path(),
                    source.authorizedRoot(),
                    source.expectedSha256(),
                    source.targetRange()
            );
        }

        private static Optional<DocumentIdentity> from(SFMTextDocumentSnapshot snapshot) {
            if (!snapshot.ready() || snapshot.path().isEmpty()
                    || snapshot.authorizedRoot().isEmpty() || snapshot.sha256().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new DocumentIdentity(
                    snapshot.path().orElseThrow(),
                    snapshot.authorizedRoot().orElseThrow(),
                    snapshot.sha256().orElseThrow(),
                    snapshot.targetRange()
            ));
        }
    }

    private record ChildSlice(List<SFMExplorerEntry> entries, int endOffset, int observedEntries) {
        private ChildSlice {
            entries = List.copyOf(entries);
        }
    }

    public record MaterializationEvidence(int entries, int nodes, int sourceLeaves) {
        public MaterializationEvidence {
            if (entries < 0 || nodes < 0 || sourceLeaves < 0) {
                throw new IllegalArgumentException("Materialization counts must not be negative");
            }
        }
    }

    public record DocumentTarget(
            SFMPath explorerPath,
            SFMReviewExplorerModel.SourceLeaf leaf,
            String presentationIdentity,
            SFMTextDocumentSource source
    ) {
        public DocumentTarget {
            Objects.requireNonNull(explorerPath, "explorerPath");
            Objects.requireNonNull(leaf, "leaf");
            Objects.requireNonNull(presentationIdentity, "presentationIdentity");
            Objects.requireNonNull(source, "source");
        }
    }

    /** Exact bridge from one durable review document identity back to one row in this resolver. */
    public record RevealTarget(SFMPath containingRoot, SFMPath explorerPath) {
        public RevealTarget {
            Objects.requireNonNull(containingRoot, "containingRoot");
            Objects.requireNonNull(explorerPath, "explorerPath");
        }
    }

    private final SFMReleaseReviewRuntime reviewRuntime;
    private final SFMReleaseReviewSurfaceRuntime surfaceRuntime;
    private volatile java.util.concurrent.Executor commentNavigationExecutor = ForkJoinPool.commonPool();
    private final Map<SFMPath, Lens> lenses = new TreeMap<>();
    private final Map<String, ProjectionSnapshot> cachedProjections = new HashMap<>();
    private final SFMReviewExplorerModel.ChangesCache changesCache = new SFMReviewExplorerModel.ChangesCache();
    private final Map<SFMPath, ChangesIndex> cachedChangesIndexes = new LinkedHashMap<>();
    private long changesIndexBuilds;

    synchronized long changesIndexBuilds() { return changesIndexBuilds; }
    private boolean resolverRegistered;

    SFMReleaseReviewExplorerRuntime(SFMReleaseReviewRuntime reviewRuntime) {
        this(reviewRuntime, SFMReleaseReviewSurfaceRuntime.get());
    }

    SFMReleaseReviewExplorerRuntime(
            SFMReleaseReviewRuntime reviewRuntime,
            SFMReleaseReviewSurfaceRuntime surfaceRuntime
    ) {
        this.reviewRuntime = Objects.requireNonNull(reviewRuntime, "reviewRuntime");
        this.surfaceRuntime = Objects.requireNonNull(surfaceRuntime, "surfaceRuntime");
    }

    public static SFMReleaseReviewExplorerRuntime get() {
        return INSTANCE;
    }

    public synchronized SFMScreenPanel openScene(
            SFMReleaseReviewExplorerScreenType.Projection projection,
            Optional<String> query
    ) {
        Objects.requireNonNull(projection, "projection");
        query = Objects.requireNonNull(query, "query").map(String::strip).filter(value -> !value.isEmpty());
        SFMReleaseReviewRuntime.Snapshot review = requireOpenReview();
        SFMExplorerRuntime explorerRuntime = SFMExplorerRuntime.get();
        if (!resolverRegistered) {
            explorerRuntime.registerResolverIfAbsent(this);
            resolverRegistered = true;
        }
        SFMPath root = prepareLens(
                review.path().orElseThrow(),
                projection,
                query,
                SFMReviewExplorerModel.PathLayout.HIERARCHY
        );
        SFMPath displayLocation = SFMPath.fromNative(review.path().orElseThrow());
        return explorerRuntime.openProjectedScene(
                new SFMPathExpression.Literal(displayLocation),
                Set.of(root),
                true
        );
    }

    synchronized SFMPath prepareLens(
            Path reviewPath,
            SFMReleaseReviewExplorerScreenType.Projection projection,
            Optional<String> query
    ) {
        return prepareLens(reviewPath, projection, query, SFMReviewExplorerModel.PathLayout.HIERARCHY);
    }

    synchronized SFMPath prepareLens(
            Path reviewPath,
            SFMReleaseReviewExplorerScreenType.Projection projection,
            Optional<String> query,
            SFMReviewExplorerModel.PathLayout changesPathLayout
    ) {
        Objects.requireNonNull(reviewPath, "reviewPath");
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(changesPathLayout, "changesPathLayout");
        query = Objects.requireNonNull(query, "query").map(String::strip).filter(value -> !value.isEmpty());
        SFMReleaseReviewRuntime.Snapshot review = requireOpenReview();
        Path normalizedReviewPath = reviewPath.toAbsolutePath().normalize();
        if (!review.path().orElseThrow().toAbsolutePath().normalize().equals(normalizedReviewPath)) {
            throw new IllegalArgumentException("Review lens path does not match the currently open review");
        }
        String authority = authority(reviewPath);
        long openEpoch = review.openEpoch();
        String lensToken = lensToken(projection, changesPathLayout, query, openEpoch);
        SFMPath root = new SFMPath(
                SFMPath.Kind.CONTRIBUTED,
                PATH_SCHEME,
                authority,
                List.of(lensToken),
                Optional.empty(),
                true
        );
        // A replacement/reopen invalidates every prior ephemeral review-tree identity. Ordinary
        // review mutations retain the open epoch and therefore keep existing Explorer panels live.
        lenses.entrySet().removeIf(entry -> entry.getValue().reviewOpenEpoch() != openEpoch);
        lenses.put(root, new Lens(
                root,
                normalizedReviewPath,
                openEpoch,
                projection,
                changesPathLayout,
                query,
                title(projection, changesPathLayout)
        ));
        return root;
    }

    public Optional<DocumentTarget> documentTarget(SFMPath path) {
        Objects.requireNonNull(path, "path");
        Lens lens;
        synchronized (this) {
            lens = lensFor(path).orElse(null);
        }
        if (lens == null) return Optional.empty();
        SFMReleaseReviewRuntime.Snapshot review = requireOpenReview();
        ProjectionSnapshot projection = projection(review, lens);
        SFMReviewExplorerModel.SourceLeaf leaf = projection.leaf(path, review.document().orElseThrow());
        if (leaf == null || leaf.missing()) return Optional.empty();
        String identity = presentationIdentity(review.path().orElseThrow(), lens, leaf);
        return Optional.of(new DocumentTarget(path, leaf, identity, documentSource(surfaceRuntime, review, leaf)));
    }

    /**
     * Maps an immutable review document snapshot back to every exact row represented by the supplied roots.
     * Callers must reject multiplicity rather than guessing between comments, hashtags, or other projections.
     */
    public List<RevealTarget> revealTargets(Set<SFMPath> roots, SFMTextDocumentSnapshot document) {
        roots = Set.copyOf(Objects.requireNonNull(roots, "roots"));
        Objects.requireNonNull(document, "document");
        if (!document.ready() || document.path().isEmpty() || document.sha256().isEmpty()) return List.of();

        SFMReleaseReviewRuntime.Snapshot review = requireOpenReview();
        ArrayList<RevealTarget> matches = new ArrayList<>();
        for (SFMPath root : roots.stream().sorted().toList()) {
            Lens lens;
            synchronized (this) {
                lens = lenses.get(root);
            }
            if (lens == null) continue;
            ProjectionSnapshot projection = projection(review, lens);
            projection.revealPaths(document).forEach(path -> matches.add(new RevealTarget(root, path)));
        }
        return List.copyOf(matches);
    }

    /** Resolves a potentially cold review projection away from the render thread. */
    public CompletableFuture<List<RevealTarget>> revealTargetsAsync(
            Set<SFMPath> roots,
            SFMTextDocumentSnapshot document
    ) {
        Set<SFMPath> capturedRoots = Set.copyOf(Objects.requireNonNull(roots, "roots"));
        Objects.requireNonNull(document, "document");
        long submittedGeneration = generation();
        long started = System.nanoTime();
        int warmRoots;
        synchronized (this) {
            warmRoots = (int) capturedRoots.stream()
                    .map(lenses::get)
                    .filter(Objects::nonNull)
                    .filter(lens -> cachedProjections.containsKey(
                            submittedGeneration + "|" + lens.root().canonical()
                    ))
                    .count();
        }
        int capturedWarmRoots = warmRoots;
        return CompletableFuture.supplyAsync(() -> {
            List<RevealTarget> answer = revealTargets(capturedRoots, document);
            long completedGeneration = generation();
            if (completedGeneration != submittedGeneration) {
                throw new IllegalStateException(
                        "Release review changed while resolving the reveal target (generation "
                                + submittedGeneration + " -> " + completedGeneration + ")"
                );
            }
            SFM.LOGGER.info(
                    "SFM_RELEASE_REVIEW_REVEAL_RESOLVED generation={} roots={} warm_roots={} matches={} elapsed_micros={}",
                    submittedGeneration,
                    capturedRoots.size(),
                    capturedWarmRoots,
                    answer.size(),
                    (System.nanoTime() - started) / 1_000L
            );
            return answer;
        }, ForkJoinPool.commonPool());
    }

    /** Cheap lens-membership test suitable for command availability and painting. */
    public synchronized boolean hasLensRoot(Set<SFMPath> roots) {
        return Objects.requireNonNull(roots, "roots").stream().anyMatch(lenses::containsKey);
    }

    /**
     * Identifies an exact review Explorer without guessing across mixed or
     * multi-root locations. This is intentionally bounded enough for paint,
     * focus, and action-availability queries.
     */
    public synchronized Optional<LensDescriptor> lensDescriptor(Set<SFMPath> roots) {
        Set<SFMPath> captured = Set.copyOf(Objects.requireNonNull(roots, "roots"));
        if (captured.size() != 1) return Optional.empty();
        Lens lens = lenses.get(captured.iterator().next());
        if (lens == null) return Optional.empty();
        return Optional.of(descriptor(lens));
    }

    /** Cheap display evidence for an explicit or persisted active work-queue expression. */
    public Optional<String> queryExpression(LensDescriptor lens) {
        if (lens.projection() != SFMReleaseReviewExplorerScreenType.Projection.QUERY) return Optional.empty();
        var review = reviewRuntime.snapshot();
        if (review.openEpoch() != lens.reviewOpenEpoch()
                || !review.path().filter(lens.reviewPath()::equals).isPresent()) return Optional.empty();
        return Optional.of(lens.query().orElseGet(() -> activeQuery(review.document().orElseThrow())));
    }

    /** Replaces only the projection root; the Explorer's durable address and presentation survive. */
    public synchronized SFMPath prepareReopenedLens(LensDescriptor previous, long expectedNewEpoch) {
        Objects.requireNonNull(previous, "previous");
        var current = requireOpenReview();
        if (current.openEpoch() != expectedNewEpoch || current.openEpoch() == previous.reviewOpenEpoch()
                || !current.path().orElseThrow().toAbsolutePath().normalize().equals(previous.reviewPath())) {
            throw new IllegalStateException("Cannot rebind this Explorer to a different or superseded review");
        }
        return prepareLens(previous.reviewPath(), previous.projection(), previous.query(), previous.changesPathLayout());
    }

    /** Read-only work navigation must not restart an equivalent queue projection. */
    public java.util.concurrent.CompletionStage<LensDescriptor> ensureActiveWorkQueueLens(SFMExplorerPanel explorer) {
        Objects.requireNonNull(explorer, "explorer");
        LensDescriptor current = lensDescriptor(explorer.sessionSnapshot().roots()).orElseThrow(() ->
                new IllegalStateException("The originating Explorer no longer hosts one exact release-review lens"));
        SFMReleaseReviewRuntime.Snapshot review = requireOpenReview();
        if (review.openEpoch() != current.reviewOpenEpoch()
                || !review.path().orElseThrow().toAbsolutePath().normalize().equals(current.reviewPath())) {
            throw new IllegalStateException("The release-review lens belongs to a replaced review session");
        }
        String active = activeQuery(review.document().orElseThrow());
        if (current.projection() == SFMReleaseReviewExplorerScreenType.Projection.QUERY
                && SFMReleaseReviewQuery.normalize(current.query().orElse(active))
                .equals(SFMReleaseReviewQuery.normalize(active))) {
            // An explicit Remaining-work expression and the implicit saved expression can
            // designate the same queue. Keep its root, rows, and any pending publication;
            // the downstream reveal joins missing child loads instead of cancelling them.
            // Actual mutations still use switchLens/refreshExpandedProjection below.
            return CompletableFuture.completedFuture(current);
        }
        return switchLens(explorer, SFMReleaseReviewExplorerScreenType.Projection.QUERY, Optional.empty());
    }

    /** Replaces only the projection root; the Explorer's durable address and presentation survive. */
    public java.util.concurrent.CompletionStage<LensDescriptor> switchLens(
            SFMExplorerPanel explorer,
            SFMReleaseReviewExplorerScreenType.Projection projection,
            Optional<String> query
    ) {
        Objects.requireNonNull(explorer, "explorer");
        Objects.requireNonNull(projection, "projection");
        query = Objects.requireNonNull(query, "query").map(String::strip).filter(value -> !value.isEmpty());
        LensDescriptor current = lensDescriptor(explorer.sessionSnapshot().roots()).orElseThrow(() ->
                new IllegalStateException("The originating Explorer no longer hosts one exact release-review lens"));
        SFMReleaseReviewRuntime.Snapshot review = requireOpenReview();
        if (review.openEpoch() != current.reviewOpenEpoch()
                || !review.path().orElseThrow().toAbsolutePath().normalize().equals(current.reviewPath())) {
            throw new IllegalStateException("The release-review lens belongs to a replaced review session");
        }
        Optional<String> effectiveQuery = projection == SFMReleaseReviewExplorerScreenType.Projection.QUERY
                ? query
                : Optional.empty();
        SFMPath nextRoot = prepareLens(
                current.reviewPath(),
                projection,
                effectiveQuery,
                current.changesPathLayout()
        );
        LensDescriptor next = lensDescriptor(Set.of(nextRoot)).orElseThrow();
        if (explorer.sessionSnapshot().roots().equals(Set.of(nextRoot))) {
            return explorer.refreshExpandedProjection().thenApply(ignored -> next);
        }
        return explorer.replaceProjectionRoot(nextRoot).thenApply(ignored -> next);
    }

    /** Reprojects changed paths without replacing the Explorer, review, or open preview identities. */
    public java.util.concurrent.CompletionStage<LensDescriptor> switchChangesPathLayout(
            SFMExplorerPanel explorer,
            SFMReviewExplorerModel.PathLayout changesPathLayout
    ) {
        Objects.requireNonNull(explorer, "explorer");
        Objects.requireNonNull(changesPathLayout, "changesPathLayout");
        LensDescriptor current = lensDescriptor(explorer.sessionSnapshot().roots()).orElseThrow(() ->
                new IllegalStateException("The originating Explorer no longer hosts one exact release-review lens"));
        if (current.projection() != SFMReleaseReviewExplorerScreenType.Projection.CHANGES) {
            throw new IllegalStateException("Changed-path layout can only be set from the Changes lens");
        }
        SFMReleaseReviewRuntime.Snapshot review = requireOpenReview();
        if (review.openEpoch() != current.reviewOpenEpoch()
                || !review.path().orElseThrow().toAbsolutePath().normalize().equals(current.reviewPath())) {
            throw new IllegalStateException("The release-review lens belongs to a replaced review session");
        }
        SFMPath nextRoot = prepareLens(
                current.reviewPath(),
                current.projection(),
                current.query(),
                changesPathLayout
        );
        LensDescriptor next = lensDescriptor(Set.of(nextRoot)).orElseThrow();
        return explorer.replaceProjectionRoot(nextRoot).thenApply(ignored -> next);
    }

    /**
     * Resolves one comment-object field to the exact Comments-lens row away
     * from the render thread.  Source decorations use this same node identity
     * instead of maintaining a second details model.
     */
    public CompletableFuture<SFMPath> commentNodePathAsync(
            SFMPath commentsRoot,
            String commentId,
            String section
    ) {
        Objects.requireNonNull(commentsRoot, "commentsRoot");
        Objects.requireNonNull(commentId, "commentId");
        Objects.requireNonNull(section, "section");
        if (!Set.of("value", "selector", "matches", "provenance").contains(section)) {
            throw new IllegalArgumentException("Unknown comment details section: " + section);
        }
        long submittedGeneration = generation();
        return CompletableFuture.supplyAsync(() -> {
            Lens lens;
            synchronized (this) {
                lens = lenses.get(commentsRoot);
            }
            if (lens == null || lens.projection() != SFMReleaseReviewExplorerScreenType.Projection.COMMENTS) {
                throw new IllegalArgumentException("The supplied root is not a release-review Comments lens");
            }
            ProjectionSnapshot snapshot = projection(requireOpenReview(), lens);
            String nodeId = "comment/" + commentId + "/" + section;
            SFMPath path = snapshot.pathForNodeId(nodeId);
            if (path == null) throw new IllegalArgumentException(
                    "The open review has no comment details node " + nodeId);
            if (generation() != submittedGeneration) {
                throw new IllegalStateException("Release review changed while resolving " + nodeId);
            }
            return path;
        }, commentNavigationExecutor);
    }

    public record WorkCursorTarget(String unitId, SFMPath unitPath, Optional<SFMPath> documentPath) { }

    /** Resolve the saved cursor against the exact active query, never a transient row selection. */
    public CompletableFuture<WorkCursorTarget> currentWorkTargetAsync(SFMPath queueRoot) {
        var captured = requireOpenReview();
        return CompletableFuture.supplyAsync(() -> {
            Lens lens;
            synchronized (this) { lens = lenses.get(queueRoot); }
            if (lens == null || lens.projection() != SFMReleaseReviewExplorerScreenType.Projection.QUERY) {
                throw new IllegalArgumentException("Show current work requires an exact Work queue lens");
            }
            var review = captured.document().orElseThrow();
            String expression = SFMReleaseReviewWorkQueue.expression(review);
            if (!SFMReleaseReviewQuery.normalize(lens.query().orElse(expression))
                    .equals(SFMReleaseReviewQuery.normalize(expression))) {
                throw new IllegalArgumentException("This temporary query is not the saved work queue");
            }
            var snapshot = projection(captured, lens);
            var children = snapshot.rootChildren();
            String unitId = SFMReleaseReviewWorkQueue.current(review,
                    children.stream().map(node -> node.id().substring("release/unit/".length())).toList());
            var node = children.stream().filter(value -> value.id().equals("release/unit/" + unitId))
                    .findFirst().orElseThrow();
            // After is the ordinary forward review surface; deletions use before.
            // This opens the unit's first exact range; remaining ranges stay inspectable as children.
            var revisions = node.children().stream().filter(child -> child.leaf() != null
                    && !child.leaf().missing()).toList();
            Optional<SFMPath> documentPath = revisions.stream()
                    .filter(child -> child.leaf().title().startsWith("after"))
                    .findFirst().or(() -> revisions.stream().findFirst())
                    .map(child -> snapshot.pathForNodeId(child.id()));
            var current = requireOpenReview();
            if (current.openEpoch() != captured.openEpoch() || current.generation() != captured.generation()
                    || !current.path().equals(captured.path())) throw new IllegalStateException(
                    "The captured review changed while locating its saved work cursor");
            return new WorkCursorTarget(unitId, snapshot.pathForNodeId(node.id()), documentPath);
        }, commentNavigationExecutor);
    }

    MaterializationEvidence materializationEvidence(SFMPath root) {
        Objects.requireNonNull(root, "root");
        Lens lens;
        synchronized (this) {
            lens = lenses.get(root);
        }
        if (lens == null) throw new IllegalArgumentException("Unknown release-review root " + root);
        return projection(requireOpenReview(), lens).materializationEvidence();
    }

    /** Exact generated surface retained for later comment-capture projection. */
    public Optional<SFMReleaseReviewSurfaceV1.Surface> generatedSurface(SFMTextDocumentSnapshot document) {
        return surfaceRuntime.sourceMap(document);
    }

    /** Maps a generated-surface selection back to immutable before/after corpus ranges. */
    public List<SFMReleaseReviewSurfaceV1.SourceRange> projectGeneratedSelection(
            SFMTextDocumentSnapshot document,
            SFMReleaseReviewSurfaceV1.Utf8Range selected
    ) {
        return surfaceRuntime.projectToSources(document, selected);
    }

    /** Review-specific row actions contributed to the generic Explorer menu. */
    public List<SFMActionChoice> contextChoices(SFMPath path) {
        Objects.requireNonNull(path, "path");
        Lens lens;
        synchronized (this) {
            lens = lensFor(path).orElse(null);
        }
        if (lens == null) return List.of();
        ArrayList<SFMActionChoice> choices = new ArrayList<>();
        if (lens.projection() == SFMReleaseReviewExplorerScreenType.Projection.CHANGES) {
            choices.addAll(SFMReviewChangesLayoutSetAction.alternativeChoice(lens.changesPathLayout()));
        }
        SFMReleaseReviewRuntime.Snapshot review = requireOpenReview();
        var projected = projection(review, lens);
        String nodeId = projected.nodeId(path);
        projected.nodeAction(path).filter(SFMReviewExplorerModel.CommentNavigation.class::isInstance)
                .map(SFMReviewExplorerModel.CommentNavigation.class::cast)
                .ifPresent(comment -> choices.addAll(
                        ca.teamdman.sfm.client.action.SFMReleaseReviewCommentDetailsAction.rowChoices(
                                comment.commentId(), review.openEpoch())));
        if (nodeId != null && nodeId.startsWith("comment/")) {
            // Match an exact comment object, not a guessed prefix or a child property.
            review.document().orElseThrow().reviewSession().comments().stream()
                    .filter(comment -> nodeId.equals("comment/" + comment.id()))
                    .findFirst().ifPresent(comment -> choices.addAll(
                            ca.teamdman.sfm.client.action.SFMReviewMigrationAction.choices(comment.id())));
        }
        if (nodeId == null || !nodeId.startsWith("release/migration/")) return List.copyOf(choices);
        String migrationId = nodeId.substring("release/migration/".length());
        SFMReleaseReviewV1 document = review.document().orElseThrow();
        SFMReleaseReviewV1.MigrationReport report = document.migrationReports().stream()
                .filter(candidate -> candidate.id().equals(migrationId))
                .findFirst().orElse(null);
        if (report == null
                || (report.decision() != SFMReleaseReviewV1.MigrationDecision.UNRESOLVED
                && report.decision() != SFMReleaseReviewV1.MigrationDecision.DEFERRED)) {
            return List.copyOf(choices);
        }

        ResourceLocation actionId = new ResourceLocation(
                SFM.MOD_ID, SFMReleaseReviewAction.Kind.MIGRATION_DECIDE.path());
        String expectedState = SFMReleaseReviewKernel.semanticStateHash(document);
        String arguments = StringArgumentType.escapeIfRequired(migrationId) + " " + expectedState + " ";
        if (report.candidateEvaluation().status() == SFMReleaseReviewV1.EvaluationStatus.RELOCATED) {
            choices.add(SFMActionChoice.invoke(
                    actionId,
                    arguments + "relocation-confirmed none Explicitly confirmed the witnessed relocation.",
                    "Confirm witnessed relocation"
            ));
        }
        for (int index = 0; index < report.newCandidates().size(); index++) {
            int displayed = index + 1;
            choices.add(SFMActionChoice.invoke(
                    actionId,
                    arguments + "retargeted " + displayed + " Explicitly retargeted to candidate " + displayed + ".",
                    "Retarget to candidate " + displayed
            ));
            choices.add(SFMActionChoice.invoke(
                    actionId,
                    arguments + "selector-edited " + displayed
                            + " Explicitly edited the selector to candidate " + displayed + ".",
                    "Edit selector to candidate " + displayed
            ));
        }
        choices.addAll(List.of(
                SFMActionChoice.invoke(
                        actionId,
                        arguments + "archived none Explicitly archived the source comment.",
                        "Archive source comment"
                ),
                SFMActionChoice.invoke(
                        actionId,
                        arguments + "discarded none Explicitly discarded the source comment.",
                        "Discard source comment"
                ),
                SFMActionChoice.invoke(
                        actionId,
                        arguments + "deferred none Deferred for later human review.",
                        "Defer migration"
                )
        ));
        return List.copyOf(choices);
    }

    public int openDocument(
            SFMClientActionContext actionContext,
            SFMPath path,
            SFMExplorerPreviewPlacement.Mode mode
    ) {
        Objects.requireNonNull(actionContext, "actionContext");
        Objects.requireNonNull(mode, "mode");
        if (!actionContext.originatingHostIsCurrent().getAsBoolean()) {
            throw new IllegalStateException("The originating review panel is no longer current");
        }
        DocumentTarget target = documentTarget(path).orElseThrow(() ->
                new IllegalArgumentException("The selected review row is missing, stale, or not a source leaf"));
        ResourceLocation editorId = SFMTextEditors.V3.getId().orElseThrow().location();
        SFMTextEditorPanelRecipe recipe = new SFMTextEditorPanelRecipe(
                new ResourceLocation(SFM.MOD_ID, "text_editor"),
                editorId,
                target.source(),
                true,
                "Review · " + target.leaf().title()
        );
        SFMScreenPanel panel = recipe.reopen();
        SFMScreenMultiplexer workspace = actionContext.originatingHost() instanceof SFMScreenMultiplexer value
                ? value : null;
        SFMWorkspacePanelId sourcePanelId = actionContext.originatingPanelId();
        if (workspace != null && sourcePanelId != null
                && workspace.panelInstance(sourcePanelId) instanceof SFMExplorerPanel) {
            SFMExplorerPreviewPlacement.Result placement = SFMExplorerPreviewPlacement.place(
                    workspace,
                    sourcePanelId,
                    previewOwner(requireOpenReview().path().orElseThrow()),
                    mode,
                    panel,
                    recipe,
                    Optional.of(target.presentationIdentity())
            );
            if (!placement.applied()) throw new IllegalStateException("The review preview could not be placed");
            return 1;
        }
        OpenPanelAction.Direction direction = mode == SFMExplorerPreviewPlacement.Mode.ADJACENT
                ? OpenPanelAction.Direction.RIGHT
                : OpenPanelAction.Direction.FOCUSED;
        int opened = OpenPanelAction.openPanel(actionContext, panel, direction, recipe);
        if (opened == 0) throw new IllegalStateException("The review document could not be opened");
        return opened;
    }

    /** Lens-independent area ownership; pinned presentation identity still distinguishes individual tabs. */
    public static String previewOwner(Path reviewPath) {
        return "release-review-" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                reviewPath.toAbsolutePath().normalize().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public String scheme() {
        return PATH_SCHEME;
    }

    @Override
    public long generation() {
        return reviewRuntime.generation();
    }

    @Override
    public CompletableFuture<SFMExplorerEntry> describe(
            SFMPath path,
            SFMExplorerCancellationToken cancellation
    ) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(cancellation, "cancellation");
        return CompletableFuture.supplyAsync(() -> {
            cancellation.throwIfCancelled();
            Lens lens;
            synchronized (this) {
                lens = lensFor(path).orElseThrow(() ->
                        new IllegalArgumentException("Unknown release-review projection path " + path));
            }
            ProjectionSnapshot snapshot = projection(requireOpenReview(), lens);
            SFMExplorerEntry entry = snapshot.entry(path);
            if (entry == null) throw new IllegalArgumentException("Unknown release-review row " + path);
            cancellation.throwIfCancelled();
            return entry;
        }, ForkJoinPool.commonPool());
    }

    @Override
    public CompletableFuture<ChildPage> resolveChildren(ChildRequest request) {
        Objects.requireNonNull(request, "request");
        return CompletableFuture.supplyAsync(() -> {
            request.cancellation().throwIfCancelled();
            long actualGeneration = generation();
            if (request.expectedResolverGeneration() != actualGeneration) {
                throw new StaleGenerationException(request.expectedResolverGeneration(), actualGeneration);
            }
            Lens lens;
            synchronized (this) {
                lens = lensFor(request.parent()).orElseThrow(() ->
                        new IllegalArgumentException("Unknown release-review projection path " + request.parent()));
            }
            SFMReleaseReviewRuntime.Snapshot review = requireOpenReview();
            ProjectionSnapshot snapshot = projection(review, lens);
            int offset = continuationOffset(request.continuation());
            ChildSlice slice = snapshot.childSlice(
                    request.parent(),
                    offset,
                    request.pageSize(),
                    review.document().orElseThrow()
            );
            request.cancellation().throwIfCancelled();
            if (generation() != actualGeneration) throw new StaleGenerationException(actualGeneration, generation());
            return new ChildPage(
                    request.parent(),
                    slice.entries(),
                    slice.endOffset() < slice.observedEntries()
                            ? Optional.of("offset-" + slice.endOffset()) : Optional.empty(),
                    actualGeneration,
                    List.of(),
                    slice.observedEntries()
            );
        }, ForkJoinPool.commonPool());
    }

    @Override
    public CompletableFuture<FilterDomain> resolveFilterDomain(FilterDomainRequest request) {
        Objects.requireNonNull(request, "request");
        return CompletableFuture.supplyAsync(() -> {
            request.cancellation().throwIfCancelled();
            long actualGeneration = generation();
            if (request.expectedResolverGeneration() != actualGeneration) {
                throw new StaleGenerationException(request.expectedResolverGeneration(), actualGeneration);
            }
            Lens lens;
            synchronized (this) {
                lens = lenses.get(request.root());
            }
            if (lens == null) {
                throw new IllegalArgumentException("Unknown release-review filter root " + request.root());
            }
            SFMReleaseReviewRuntime.Snapshot review = requireOpenReview();
            FilterDomain domain = projection(review, lens).completeFilterDomain(
                    review.document().orElseThrow(),
                    request
            );
            request.cancellation().throwIfCancelled();
            if (generation() != actualGeneration) {
                throw new StaleGenerationException(actualGeneration, generation());
            }
            return domain;
        }, ForkJoinPool.commonPool());
    }

    private ProjectionSnapshot projection(SFMReleaseReviewRuntime.Snapshot review, Lens lens) {
        Path currentReviewPath = review.path().orElseThrow().toAbsolutePath().normalize();
        if (review.openEpoch() != lens.reviewOpenEpoch() || !currentReviewPath.equals(lens.reviewPath())) {
            throw new IllegalStateException("Release-review Explorer lens belongs to a replaced review session");
        }
        String key = review.generation() + "|" + lens.root().canonical();
        synchronized (this) {
            ProjectionSnapshot cached = cachedProjections.get(key);
            if (cached != null) return cached;
        }
        SFMReleaseReviewV1 document = review.document().orElseThrow();
        SFMReviewExplorerModel model = project(
                document,
                lens.projection(),
                lens.changesPathLayout(),
                lens.query()
        );
        boolean changesOnly = lens.projection() == SFMReleaseReviewExplorerScreenType.Projection.CHANGES;
        ChangesIndex reusableIndex;
        synchronized (this) {
            reusableIndex = changesOnly ? cachedChangesIndexes.get(lens.root()) : null;
        }
        if (reusableIndex != null && !SFMReviewExplorerModel.ChangesCache.sameSource(reusableIndex.source(), document))
            reusableIndex = null;
        ProjectionSnapshot built = new ProjectionSnapshot(review.generation(), lens.root(), model.root(),
                review, surfaceRuntime, changesOnly, reusableIndex);
        synchronized (this) {
            if (changesOnly) {
                if (reusableIndex == null) changesIndexBuilds++;
                cachedChangesIndexes.put(lens.root(), built.changesIndex);
                while (cachedChangesIndexes.size() > 16)
                    cachedChangesIndexes.remove(cachedChangesIndexes.keySet().iterator().next());
            }
            cachedProjections.keySet().removeIf(candidate -> !candidate.startsWith(review.generation() + "|"));
            return cachedProjections.computeIfAbsent(key, ignored -> built);
        }
    }

    private static SFMPath childPath(
            SFMPath parentPath,
            int index,
            SFMReviewExplorerModel.Node child
    ) {
        ArrayList<String> segments = new ArrayList<>(parentPath.segments());
        segments.add(String.format(Locale.ROOT, "%06d-%s", index, stableToken(child.id())));
        return new SFMPath(
                SFMPath.Kind.CONTRIBUTED,
                PATH_SCHEME,
                parentPath.authority(),
                segments,
                Optional.empty(),
                child.expandable()
        );
    }

    private static List<SFMReviewExplorerModel.Node> displayedChildren(
            SFMReviewExplorerModel.Node node,
            SFMReleaseReviewV1 review
    ) {
        List<SFMReviewExplorerModel.Node> children = node.children();
        if (node.kind() == SFMReviewExplorerModel.Kind.FILE
                && review.repositoryBindings().size() == 1
                && children.size() == 1
                && children.get(0).kind() == SFMReviewExplorerModel.Kind.LANE) {
            return children.get(0).children();
        }
        return children;
    }

    private static SFMItemIcon icon(SFMReviewExplorerModel.Node node) {
        if (node.leaf() != null && node.leaf().missing()) return icon("barrier", "missing source");
        return switch (node.kind()) {
            case DIRECTORY -> SFMClientThemeService.active().fileIcon("directory");
            case FILE -> fileIcon(node.label());
            case REVISION -> fileIcon(node.leaf() == null ? node.label() : node.leaf().path());
            case DIFF -> node.leaf() != null && node.leaf().generatedSurface()
                    .map(recipe -> recipe.surfaceKind() == SFMReleaseReviewSurfaceV1.SurfaceKind.JAVA_STRUCTURED_DIFF)
                    .orElse(false) ? icon("comparator", "structured diff") : icon("writable_book", "text diff");
            case COMMENT -> icon("writable_book", "review comment");
            case HASHTAG -> icon("name_tag", "hashtag");
            case MIGRATION -> icon("compass", "migration");
            case STATUS_CATEGORY -> icon("comparator", "review status");
            default -> node.expandable()
                    ? SFMClientThemeService.active().fileIcon("directory")
                    : SFMClientThemeService.active().fileIcon("unknown");
        };
    }

    private static SFMItemIcon fileIcon(String fileName) {
        return SFMClientThemeService.active().fileIconForName(fileName);
    }

    private static SFMItemIcon icon(String itemPath, String accessibleLabel) {
        return SFMItemIcon.vanilla(itemPath, accessibleLabel);
    }

    private static SFMExplorerEntry entry(
            SFMPath path,
            SFMReviewExplorerModel.Node node,
            SFMItemIcon icon
    ) {
        String label = node.label();
        String extension = labelExtension(label);
        Map<String, SFMExplorerEntry.SortKey> sort = new LinkedHashMap<>();
        String subjectName = node.leaf()!=null ? node.leaf().path() : node.label();
        subjectName = subjectName.substring(Math.max(subjectName.lastIndexOf('/'), subjectName.lastIndexOf('\\'))+1);
        if (!subjectName.isEmpty()) sort.put(SFMExplorerEntry.SUBJECT_NAME, SFMExplorerEntry.SortKey.available(subjectName));
        String subjectKind = switch (node.kind()) {
            case DIRECTORY -> "container";
            case FILE, REVISION -> "file";
            default -> "other";
        };
        sort.put(SFMExplorerEntry.SUBJECT_KIND, SFMExplorerEntry.SortKey.available(subjectKind));
        if (node.leaf() != null) {
            sort.put(SFMExplorerEntry.PRIMARY_ACTION_OPEN, SFMExplorerEntry.SortKey.available("true"));
        }
        sort.put(SFMExplorerEntry.SORT_NAME, SFMExplorerEntry.SortKey.available(reviewNameSortValue(node)));
        sort.put(SFMExplorerEntry.SORT_ICON,
                SFMExplorerEntry.SortKey.available(icon.requestedItem().toString()));
        sort.put(SFMExplorerEntry.PRESENTATION_ICON_FALLBACK,
                SFMExplorerEntry.SortKey.available(icon.fallbackItem().toString()));
        sort.put(SFMExplorerEntry.PRESENTATION_ICON_LABEL,
                SFMExplorerEntry.SortKey.available(icon.accessibleLabel()));
        sort.put(SFMExplorerEntry.SORT_EXTENSION, extension.isEmpty()
                ? SFMExplorerEntry.SortKey.unavailable("review row has no file extension")
                : SFMExplorerEntry.SortKey.available(extension));
        return new SFMExplorerEntry(
                path,
                label,
                node.expandable(),
                sort,
                reviewSearchTerms(node),
                List.of()
        );
    }

    private static List<String> reviewSearchTerms(SFMReviewExplorerModel.Node node) {
        if (node.id().startsWith("document-comment/") && node.leaf() != null
                && ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel
                        .derivedHashtags(node.leaf().text()).contains("#release-change")) {
            // Generated prose often repeats the filename/language. Do not make a
            // filename search expand every source merely to show that same evidence.
            return List.of("release-change comment");
        }
        if (node.kind() == SFMReviewExplorerModel.Kind.FILE || node.kind() == SFMReviewExplorerModel.Kind.REVIEW_UNIT) {
            java.util.LinkedHashSet<String> terms = new java.util.LinkedHashSet<>();
            terms.add(node.label());
            collectLeafPaths(node, terms);
            return List.copyOf(terms);
        }
        if (node.kind() != SFMReviewExplorerModel.Kind.REVISION
                && node.kind() != SFMReviewExplorerModel.Kind.DIFF) {
            return List.of(node.label());
        }
        int separator = node.label().indexOf(" · ");
        String role = separator < 0 ? node.label() : node.label().substring(0, separator);
        if (node.leaf() != null && node.leaf().missing()) return List.of(role, "missing");
        return List.of(role);
    }

    private static void collectLeafPaths(
            SFMReviewExplorerModel.Node node,
            java.util.Set<String> output
    ) {
        if (node.leaf() != null && !node.leaf().path().isBlank()) output.add(node.leaf().path());
        node.children().forEach(child -> collectLeafPaths(child, output));
    }

    /** Preserve review chronology independently from alphabetical Explorer sorting. */
    private static String reviewNameSortValue(SFMReviewExplorerModel.Node node) {
        if (node.kind() == SFMReviewExplorerModel.Kind.DIRECTORY) return "00-directory/" + node.label();
        if (node.kind() == SFMReviewExplorerModel.Kind.FILE) return "01-file/" + node.label();
        if (node.id().startsWith("release/revision/")) {
            if (node.label().startsWith("before ·")) return "00-before";
            if (node.label().startsWith("after ·")) return "01-after";
        }
        if (node.id().startsWith("release/diff/")) {
            if (node.label().startsWith("text diff (inline) ·")) return "02-text-diff";
            if (node.label().startsWith("structured diff (inline) ·")) return "03-structured-diff";
            if (node.label().startsWith("text diff (split) ·")) return "04-text-diff-split";
            if (node.label().startsWith("structured diff (split) ·")) return "05-structured-diff-split";
        }
        return node.label();
    }

    private static String labelExtension(String label) {
        String name = label;
        int separator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (separator >= 0) name = name.substring(separator + 1);
        int dot = name.lastIndexOf('.');
        return dot <= 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private SFMReviewExplorerModel project(
            SFMReleaseReviewV1 review,
            SFMReleaseReviewExplorerScreenType.Projection projection,
            SFMReviewExplorerModel.PathLayout changesPathLayout,
            Optional<String> query
    ) {
        return switch (projection) {
            case CHANGES -> changesCache.project(review, changesPathLayout);
            case COMMENTS -> SFMReviewExplorerModel.comments(review.reviewSession());
            case HASHTAGS -> SFMReviewExplorerModel.hashtags(review.reviewSession());
            case QUERY -> SFMReviewExplorerModel.releaseQuery(review, query.orElseGet(() -> activeQuery(review)));
            case STATUS -> SFMReviewExplorerModel.releaseStatus(review);
            case MIGRATIONS -> SFMReviewExplorerModel.releaseMigrations(review);
        };
    }

    private static String activeQuery(SFMReleaseReviewV1 review) {
        return review.resumeState().activeQueryExpression().orElseGet(() ->
                review.resumeState().activeQueryId()
                        .flatMap(id -> review.namedQueries().stream()
                                .filter(query -> query.id().equals(id))
                                .findFirst())
                        .map(SFMReleaseReviewV1.NamedQuery::expression)
                        .orElse("remaining"));
    }

    private static SFMTextDocumentSource documentSource(
            SFMReleaseReviewSurfaceRuntime surfaceRuntime,
            SFMReleaseReviewRuntime.Snapshot review,
            SFMReviewExplorerModel.SourceLeaf leaf
    ) {
        if (leaf.generatedSurface().isPresent()) {
            return new SFMTextDocumentSource.GeneratedReviewSurface(
                    surfaceRuntime,
                    leaf.generatedSurface().orElseThrow(),
                    review.generation()
            );
        }
        SFMTextDocumentLanguage language = documentLanguage(leaf);
        if (leaf.documentRevisionId().isEmpty()) {
            return new SFMTextDocumentSource.Literal(leaf.text(), language);
        }
        DocumentIdentity identity = pinnedIdentity(leaf);
        return new SFMTextDocumentSource.PinnedSnapshot(
                identity.path(), identity.authorizedRoot(), leaf.text(), identity.sha256(),
                identity.targetRange(), Optional.empty(),
                SFMReleaseReviewAnalysisIdentityResolver.resolve(
                        review.document().orElseThrow(), review.path().orElseThrow(),
                        leaf.documentRevisionId().orElseThrow()), language);
    }

    /** Pure address projection: no filesystem or language-analysis acquisition. */
    private static DocumentIdentity pinnedIdentity(SFMReviewExplorerModel.SourceLeaf leaf) {
        String revisionId = leaf.documentRevisionId().orElseThrow();
        SFMPath root = new SFMPath(
                SFMPath.Kind.CONTRIBUTED,
                "review",
                "document",
                List.of(revisionId),
                Optional.empty(),
                true
        );
        ArrayList<String> segments = new ArrayList<>(root.segments());
        for (String segment : leaf.path().replace('\\', '/').split("/")) {
            if (!segment.isEmpty()) segments.add(segment);
        }
        SFMPath path = new SFMPath(
                SFMPath.Kind.CONTRIBUTED,
                "review",
                "document",
                segments,
                Optional.empty(),
                false
        );
        Optional<SFMTextDocumentRange> targetRange = leaf.targetRange().map(range -> {
            List<Integer> offsets = SFMContextTextCoordinates.utf16OffsetsAtUtf8Bytes(
                    leaf.text(), List.of(range.startByte(), range.endByte()));
            return SFMContextTextCoordinates.rangeAtUtf16Offsets(leaf.text(), offsets.get(0), offsets.get(1));
        });
        return new DocumentIdentity(path, root, leaf.sha256().orElseThrow(), targetRange);
    }

    static SFMTextDocumentLanguage documentLanguage(SFMReviewExplorerModel.SourceLeaf leaf) {
        Objects.requireNonNull(leaf, "leaf");
        if (leaf.generatedSurface().isPresent() || leaf.id().startsWith("release/diff/")) {
            return SFMTextDocumentLanguage.diff();
        }
        return SFMTextDocumentLanguage.fromFileName(leaf.path());
    }

    private static String presentationIdentity(Path reviewPath, Lens lens, SFMReviewExplorerModel.SourceLeaf leaf) {
        return "release-review|" + reviewPath.toAbsolutePath().normalize()
                + "|" + lens.projection().name()
                + "|" + leaf.id()
                + "|" + leaf.documentRevisionId().orElse("literal")
                + "|" + leaf.sha256().orElse("unhashed")
                + "|" + leaf.targetRange().map(range -> range.startByte() + "-" + range.endByte()).orElse("full");
    }

    private synchronized Optional<Lens> lensFor(SFMPath path) {
        if (!path.scheme().equals(PATH_SCHEME) || path.kind() != SFMPath.Kind.CONTRIBUTED) {
            return Optional.empty();
        }
        return lenses.values().stream()
                .filter(lens -> contains(lens.root(), path))
                .max(Comparator.comparingInt(lens -> lens.root().segments().size()));
    }

    private static LensDescriptor descriptor(Lens lens) {
        return new LensDescriptor(
                lens.root(),
                lens.reviewPath(),
                lens.reviewOpenEpoch(),
                lens.projection(),
                lens.changesPathLayout(),
                lens.query(),
                lens.title()
        );
    }

    private SFMReleaseReviewRuntime.Snapshot requireOpenReview() {
        SFMReleaseReviewRuntime.Snapshot snapshot = reviewRuntime.snapshot();
        if (snapshot.document().isEmpty()) {
            throw new IllegalStateException("Open a .sfm-review.json file before opening a review explorer");
        }
        return snapshot;
    }

    private static boolean contains(SFMPath root, SFMPath path) {
        return root.scheme().equals(path.scheme())
                && root.authority().equals(path.authority())
                && root.segments().size() <= path.segments().size()
                && path.segments().subList(0, root.segments().size()).equals(root.segments());
    }

    private static int continuationOffset(Optional<String> continuation) {
        if (continuation.isEmpty()) return 0;
        String value = continuation.orElseThrow();
        if (!value.startsWith("offset-")) throw new IllegalArgumentException("Unsupported continuation " + value);
        try {
            int offset = Integer.parseInt(value.substring("offset-".length()));
            if (offset <= 0) throw new NumberFormatException();
            return offset;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Invalid continuation " + value);
        }
    }

    private static String authority(Path path) {
        return "review-" + UUID.nameUUIDFromBytes(
                path.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String lensToken(
            SFMReleaseReviewExplorerScreenType.Projection projection,
            SFMReviewExplorerModel.PathLayout changesPathLayout,
            Optional<String> query,
            long openEpoch
    ) {
        String input = projection.name() + "\n" + changesPathLayout.name() + "\n"
                + query.orElse("") + "\nopen-epoch=" + openEpoch;
        return projection.name().toLowerCase(Locale.ROOT) + "-" + stableToken(input);
    }

    private static String stableToken(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String title(
            SFMReleaseReviewExplorerScreenType.Projection projection,
            SFMReviewExplorerModel.PathLayout changesPathLayout
    ) {
        return switch (projection) {
            case CHANGES -> "Release review changes · " + switch (changesPathLayout) {
                case HIERARCHY -> "Hierarchy";
                case FLAT_PATHS -> "Flat paths";
            };
            case COMMENTS -> "Release review comments";
            case HASHTAGS -> "Release review hashtags";
            case QUERY -> "Release review work queue";
            case STATUS -> "Release review status witnesses";
            case MIGRATIONS -> "Release review migrations";
        };
    }
}
