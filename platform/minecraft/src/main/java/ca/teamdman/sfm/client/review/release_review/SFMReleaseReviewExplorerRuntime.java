package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.action.OpenPanelAction;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMReleaseReviewAction;
import ca.teamdman.sfm.client.context.SFMContextTextCoordinates;
import ca.teamdman.sfm.client.explorer.SFMExplorerRuntime;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMPathExpression;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerCancellationToken;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerResolver;
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
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSource;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorPanelRecipe;
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
public final class SFMReleaseReviewExplorerRuntime implements SFMExplorerResolver {
    public static final String PATH_SCHEME = "review-tree";
    private static final SFMReleaseReviewExplorerRuntime INSTANCE =
            new SFMReleaseReviewExplorerRuntime(SFMReleaseReviewRuntime.get());

    private record Lens(
            SFMPath root,
            Path reviewPath,
            long reviewOpenEpoch,
            SFMReleaseReviewExplorerScreenType.Projection projection,
            Optional<String> query,
            String title
    ) {
        private Lens {
            Objects.requireNonNull(root, "root");
            reviewPath = Objects.requireNonNull(reviewPath, "reviewPath").toAbsolutePath().normalize();
            Objects.requireNonNull(projection, "projection");
            Objects.requireNonNull(query, "query");
            Objects.requireNonNull(title, "title");
        }
    }

    private static final class ProjectionSnapshot {
        private final long generation;
        private final SFMPath root;
        private final Map<SFMPath, SFMExplorerEntry> entries = new TreeMap<>();
        private final Map<SFMPath, SFMReviewExplorerModel.Node> nodes = new TreeMap<>();
        private final Map<SFMPath, SFMReviewExplorerModel.SourceLeaf> leaves = new TreeMap<>();
        private final Map<SFMPath, String> nodeIds = new TreeMap<>();
        private final Map<DocumentIdentity, List<SFMPath>> revealPaths = new HashMap<>();

        private ProjectionSnapshot(
                long generation,
                SFMPath root,
                SFMReviewExplorerModel.Node modelRoot,
                SFMReleaseReviewRuntime.Snapshot review
        ) {
            this.generation = generation;
            this.root = Objects.requireNonNull(root, "root");
            Objects.requireNonNull(modelRoot, "modelRoot");
            entries.put(root, SFMReleaseReviewExplorerRuntime.entry(
                    root, modelRoot.label(), true, "minecraft:spyglass"));
            nodes.put(root, modelRoot);
            nodeIds.put(root, modelRoot.id());
            indexRevealPaths(root, modelRoot, review);
        }

        private synchronized SFMExplorerEntry entry(SFMPath path) {
            return entries.get(path);
        }

        private synchronized SFMReviewExplorerModel.SourceLeaf leaf(SFMPath path) {
            return leaves.get(path);
        }

        private synchronized String nodeId(SFMPath path) {
            return nodeIds.get(path);
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
                                childPath,
                                child.label(),
                                child.expandable(),
                                icon(child)
                        ));
                nodes.putIfAbsent(childPath, child);
                nodeIds.putIfAbsent(childPath, child.id());
                if (child.leaf() != null) leaves.putIfAbsent(childPath, child.leaf());
                page.add(childEntry);
            }
            return new ChildSlice(page, end, children.size());
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
                SFMReleaseReviewRuntime.Snapshot review
        ) {
            List<SFMReviewExplorerModel.Node> children = displayedChildren(
                    parentNode,
                    review.document().orElseThrow()
            );
            for (int index = 0; index < children.size(); index++) {
                SFMReviewExplorerModel.Node child = children.get(index);
                SFMPath childPath = childPath(parentPath, index, child);
                SFMReviewExplorerModel.SourceLeaf leaf = child.leaf();
                if (leaf != null && !leaf.missing()) {
                    SFMTextDocumentSource source = documentSource(review, leaf);
                    if (source instanceof SFMTextDocumentSource.PinnedSnapshot pinned) {
                        DocumentIdentity identity = DocumentIdentity.from(pinned);
                        ArrayList<SFMPath> paths = new ArrayList<>(
                                revealPaths.getOrDefault(identity, List.of())
                        );
                        paths.add(childPath);
                        revealPaths.put(identity, List.copyOf(paths));
                    }
                }
                if (child.expandable()) indexRevealPaths(childPath, child, review);
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
    private final Map<SFMPath, Lens> lenses = new TreeMap<>();
    private final Map<String, ProjectionSnapshot> cachedProjections = new HashMap<>();
    private boolean resolverRegistered;

    SFMReleaseReviewExplorerRuntime(SFMReleaseReviewRuntime reviewRuntime) {
        this.reviewRuntime = Objects.requireNonNull(reviewRuntime, "reviewRuntime");
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
        SFMPath root = prepareLens(review.path().orElseThrow(), projection, query);
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
        Objects.requireNonNull(reviewPath, "reviewPath");
        Objects.requireNonNull(projection, "projection");
        query = Objects.requireNonNull(query, "query").map(String::strip).filter(value -> !value.isEmpty());
        SFMReleaseReviewRuntime.Snapshot review = requireOpenReview();
        Path normalizedReviewPath = reviewPath.toAbsolutePath().normalize();
        if (!review.path().orElseThrow().toAbsolutePath().normalize().equals(normalizedReviewPath)) {
            throw new IllegalArgumentException("Review lens path does not match the currently open review");
        }
        String authority = authority(reviewPath);
        long openEpoch = review.openEpoch();
        String lensToken = lensToken(projection, query, openEpoch);
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
                query,
                title(projection)
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
        SFMReviewExplorerModel.SourceLeaf leaf = projection.leaf(path);
        if (leaf == null || leaf.missing()) return Optional.empty();
        String identity = presentationIdentity(review.path().orElseThrow(), lens, leaf);
        return Optional.of(new DocumentTarget(path, leaf, identity, documentSource(review, leaf)));
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

    MaterializationEvidence materializationEvidence(SFMPath root) {
        Objects.requireNonNull(root, "root");
        Lens lens;
        synchronized (this) {
            lens = lenses.get(root);
        }
        if (lens == null) throw new IllegalArgumentException("Unknown release-review root " + root);
        return projection(requireOpenReview(), lens).materializationEvidence();
    }

    /** Review-specific row actions contributed to the generic Explorer menu. */
    public List<SFMActionChoice> contextChoices(SFMPath path) {
        Objects.requireNonNull(path, "path");
        Lens lens;
        synchronized (this) {
            lens = lensFor(path).orElse(null);
        }
        if (lens == null) return List.of();
        SFMReleaseReviewRuntime.Snapshot review = requireOpenReview();
        String nodeId = projection(review, lens).nodeId(path);
        if (nodeId == null || !nodeId.startsWith("release/migration/")) return List.of();
        String migrationId = nodeId.substring("release/migration/".length());
        SFMReleaseReviewV1 document = review.document().orElseThrow();
        SFMReleaseReviewV1.MigrationReport report = document.migrationReports().stream()
                .filter(candidate -> candidate.id().equals(migrationId))
                .findFirst().orElse(null);
        if (report == null
                || (report.decision() != SFMReleaseReviewV1.MigrationDecision.UNRESOLVED
                && report.decision() != SFMReleaseReviewV1.MigrationDecision.DEFERRED)) return List.of();

        ResourceLocation actionId = new ResourceLocation(
                SFM.MOD_ID, SFMReleaseReviewAction.Kind.MIGRATION_DECIDE.path());
        String expectedState = SFMReleaseReviewKernel.semanticStateHash(document);
        String arguments = StringArgumentType.escapeIfRequired(migrationId) + " " + expectedState + " ";
        ArrayList<SFMActionChoice> choices = new ArrayList<>();
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
                && workspace.panelInstance(sourcePanelId) instanceof SFMExplorerPanel sourceExplorer) {
            SFMExplorerPreviewPlacement.Result placement = SFMExplorerPreviewPlacement.place(
                    workspace,
                    sourcePanelId,
                    sourceExplorer.explorerId().value(),
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
            if (path.equals(lens.root())) return entry(lens.root(), lens.title(), true, "minecraft:spyglass");
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
        SFMReviewExplorerModel model = project(document, lens.projection(), lens.query());
        ProjectionSnapshot built = buildProjection(review.generation(), lens.root(), model.root(), review);
        synchronized (this) {
            cachedProjections.keySet().removeIf(candidate -> !candidate.startsWith(review.generation() + "|"));
            return cachedProjections.computeIfAbsent(key, ignored -> built);
        }
    }

    private static ProjectionSnapshot buildProjection(
            long generation,
            SFMPath root,
            SFMReviewExplorerModel.Node modelRoot,
            SFMReleaseReviewRuntime.Snapshot review
    ) {
        return new ProjectionSnapshot(generation, root, modelRoot, review);
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

    private static String icon(SFMReviewExplorerModel.Node node) {
        if (node.leaf() != null && node.leaf().missing()) return "minecraft:barrier";
        return switch (node.kind()) {
            case FILE -> "minecraft:paper";
            case REVISION -> javaIcon(node.leaf()) ? "minecraft:cocoa_beans" : "minecraft:map";
            case COMMENT -> "minecraft:writable_book";
            case HASHTAG -> "minecraft:name_tag";
            case MIGRATION -> "minecraft:compass";
            case STATUS_CATEGORY -> "minecraft:comparator";
            default -> node.expandable() ? "minecraft:chest" : "minecraft:paper";
        };
    }

    private static boolean javaIcon(SFMReviewExplorerModel.SourceLeaf leaf) {
        return leaf != null && leaf.path().toLowerCase(Locale.ROOT).endsWith(".java");
    }

    private static SFMExplorerEntry entry(SFMPath path, String label, boolean expandable, String icon) {
        String extension = labelExtension(label);
        Map<String, SFMExplorerEntry.SortKey> sort = new LinkedHashMap<>();
        sort.put(SFMExplorerEntry.SORT_NAME, SFMExplorerEntry.SortKey.available(label));
        sort.put(SFMExplorerEntry.SORT_ICON, SFMExplorerEntry.SortKey.available(icon));
        sort.put(SFMExplorerEntry.SORT_EXTENSION, extension.isEmpty()
                ? SFMExplorerEntry.SortKey.unavailable("review row has no file extension")
                : SFMExplorerEntry.SortKey.available(extension));
        return new SFMExplorerEntry(path, label, expandable, sort, List.of());
    }

    private static String labelExtension(String label) {
        String name = label;
        int separator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (separator >= 0) name = name.substring(separator + 1);
        int dot = name.lastIndexOf('.');
        return dot <= 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static SFMReviewExplorerModel project(
            SFMReleaseReviewV1 review,
            SFMReleaseReviewExplorerScreenType.Projection projection,
            Optional<String> query
    ) {
        return switch (projection) {
            case CHANGES -> SFMReviewExplorerModel.releaseChanges(review);
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
            SFMReleaseReviewRuntime.Snapshot review,
            SFMReviewExplorerModel.SourceLeaf leaf
    ) {
        if (leaf.documentRevisionId().isEmpty()) return new SFMTextDocumentSource.Literal(leaf.text());
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
        return new SFMTextDocumentSource.PinnedSnapshot(
                path,
                root,
                leaf.text(),
                leaf.sha256().orElseThrow(),
                targetRange,
                Optional.empty(),
                SFMReleaseReviewAnalysisIdentityResolver.resolve(
                        review.document().orElseThrow(),
                        review.path().orElseThrow(),
                        revisionId
                )
        );
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
            Optional<String> query,
            long openEpoch
    ) {
        String input = projection.name() + "\n" + query.orElse("") + "\nopen-epoch=" + openEpoch;
        return projection.name().toLowerCase(Locale.ROOT) + "-" + stableToken(input);
    }

    private static String stableToken(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String title(SFMReleaseReviewExplorerScreenType.Projection projection) {
        return switch (projection) {
            case CHANGES -> "Release review changes";
            case COMMENTS -> "Release review comments";
            case HASHTAGS -> "Release review hashtags";
            case QUERY -> "Release review work queue";
            case STATUS -> "Release review status witnesses";
            case MIGRATIONS -> "Release review migrations";
        };
    }
}
