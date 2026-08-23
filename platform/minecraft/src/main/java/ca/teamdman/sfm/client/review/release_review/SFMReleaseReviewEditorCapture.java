package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.context.SFMContextContribution;
import ca.teamdman.sfm.client.context.SFMContextCursorProjection;
import ca.teamdman.sfm.client.context.SFMContextDocumentProjection;
import ca.teamdman.sfm.client.context.SFMContextPosition;
import ca.teamdman.sfm.client.context.SFMContextSelectionProjection;
import ca.teamdman.sfm.client.context.SFMContextSnapshot;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditorPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.symbol.SFMJavaInteractionMap;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentPosition;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSelection;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Exact bridge from one focused EditorV3 review snapshot to the durable
 * release-review selection/proposal contract.
 */
public final class SFMReleaseReviewEditorCapture {
    public record Capture(
            SFMReleaseReviewSelectionAdapter.SelectionCapture selection,
            SFMReleaseReviewSelectionAdapter.AdaptedSelection adapted,
            SFMReleaseReviewSelectionAdapter.DocumentResolver documents,
            SFMReleaseReviewSelectorProposalAdapter.ProposalBatch proposals,
            List<String> diagnostics
    ) {
        public Capture {
            Objects.requireNonNull(selection, "selection");
            Objects.requireNonNull(adapted, "adapted");
            Objects.requireNonNull(documents, "documents");
            Objects.requireNonNull(proposals, "proposals");
            diagnostics = List.copyOf(diagnostics);
        }

        public SFMReleaseReviewV1.SelectorProposal requireProposal(String proposalId) {
            return proposals.proposals().stream()
                    .filter(value -> value.id().equals(proposalId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "The review selection changed or selector proposal is unavailable: " + proposalId));
        }
    }

    private SFMReleaseReviewEditorCapture() {
    }

    public static Capture capture(
            SFMClientActionContext actionContext,
            SFMContextSnapshot snapshot,
            SFMReleaseReviewV1 review
    ) {
        Objects.requireNonNull(actionContext, "actionContext");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(review, "review");
        SFMContextContribution contribution = snapshot.focusedOriginId()
                .flatMap(origin -> snapshot.contributions().stream()
                        .filter(value -> value.originId().equals(origin))
                        .findFirst())
                .orElseThrow(() -> new IllegalArgumentException("No focused editor context is available"));
        if (!(contribution.projection() instanceof SFMContextDocumentProjection projection)) {
            throw new IllegalArgumentException("The focused context is not a text document");
        }
        return capture(actionContext, snapshot, projection, review);
    }

    static Capture capture(
            SFMClientActionContext actionContext,
            SFMContextSnapshot snapshot,
            SFMContextDocumentProjection projection,
            SFMReleaseReviewV1 review
    ) {
        if (!projection.baseline().ready()) {
            throw new IllegalArgumentException("The focused review document is not ready");
        }
        if (projection.dirty()) {
            throw new IllegalArgumentException("Release-review comments require an unchanged pinned snapshot");
        }
        SFMPath path = projection.baseline().path().orElseThrow(() ->
                new IllegalArgumentException("The focused document has no durable address"));
        if (path.kind() == SFMPath.Kind.CONTRIBUTED
                && path.scheme().equals("review-surface")
                && path.authority().equals("generated")) {
            return captureGeneratedSurface(
                    actionContext,
                    projection,
                    review,
                    path,
                    SFMReleaseReviewSurfaceRuntime.get()
            );
        }
        if (path.kind() != SFMPath.Kind.CONTRIBUTED
                || !path.scheme().equals("review")
                || !path.authority().equals("document")
                || path.segments().isEmpty()) {
            throw new IllegalArgumentException("Focus a document opened from the active release-review explorer");
        }
        String documentRevisionId = path.segments().get(0);
        SFMReleaseReviewV1.CorpusDocument corpus = review.corpusDocuments().stream()
                .filter(value -> value.documentRevisionId().equals(documentRevisionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "The focused document is absent from the active release-review corpus"));
        if (corpus.materialization() != SFMReleaseReviewV1.Materialization.COMPLETE) {
            throw new IllegalArgumentException("The focused review document is not completely materialized");
        }
        if (!corpus.sha256().equals(projection.currentSha256())) {
            throw new IllegalArgumentException("The focused document bytes do not match the pinned review corpus");
        }

        List<SFMTextDocumentSelection> exactSelections = exactSelections(projection);
        String sourceExpression = path.canonical();
        String selectionRevision = selectionRevision(documentRevisionId, projection, exactSelections);
        Optional<SFMJavaInteractionMap.Result> interactionMap = currentInteractionMap(actionContext)
                .filter(value -> value.document().contentHash().equals("sha256:" + projection.currentSha256()));
        String sourceAddress = interactionMap
                .map(value -> value.document().address())
                .orElse(sourceExpression);
        SFMReleaseReviewSelectionAdapter.DocumentWitness witness =
                SFMReleaseReviewSelectionAdapter.DocumentWitness.capture(
                        documentRevisionId,
                        selectionRevision,
                        sourceAddress,
                        projection.currentText()
                );
        SFMReleaseReviewSelectionAdapter.SelectionCapture selection =
                new SFMReleaseReviewSelectionAdapter.SelectionCapture(
                        selectionRevision,
                        sourceExpression,
                        List.of(new SFMReleaseReviewSelectionAdapter.OrderedDocumentSelection(
                                witness,
                                exactSelections
                        ))
                );
        SFMReleaseReviewSelectionAdapter.AdaptedSelection adapted =
                SFMReleaseReviewSelectionAdapter.adapt(selection);
        SFMReleaseReviewSelectionAdapter.DocumentResolver documents =
                SFMReleaseReviewSelectionAdapter.resolver(selection);
        Optional<SFMReleaseReviewSelectorProposalAdapter.SemanticEvidenceProvider> exactJavaProvider =
                interactionMap.map(value -> new SFMReleaseReviewSelectorProposalAdapter.JavaInteractionMapProvider(
                        Map.of(documentRevisionId, value)));
        List<SFMReleaseReviewSelectorProposalAdapter.SemanticEvidenceProvider> providers = combineSemanticProviders(
                SFMReleaseReviewSemanticProviderRegistry.global().snapshot(), exactJavaProvider);
        SFMReleaseReviewSelectorProposalAdapter.ProposalBatch proposals =
                SFMReleaseReviewSelectorProposalAdapter.propose(adapted, documents, providers);
        ArrayList<String> diagnostics = new ArrayList<>();
        if (interactionMap.isEmpty()) {
            diagnostics.add("No exact Java semantic publication matched this pinned snapshot; literal selection remains available");
        }
        proposals.diagnostics().forEach(value -> diagnostics.add(
                value.code() + " [" + value.providerId() + "]: " + value.message()));
        return new Capture(selection, adapted, documents, proposals, diagnostics);
    }

    /**
     * Combines ephemeral registered evidence with the exact current Java
     * publication. The exact publication wins its stable provider id, and the
     * result is provider-id ordered with no duplicates. Durable authority is
     * still only the proposal and literal witness selected by the caller.
     */
    static List<SFMReleaseReviewSelectorProposalAdapter.SemanticEvidenceProvider> combineSemanticProviders(
            List<? extends SFMReleaseReviewSelectorProposalAdapter.SemanticEvidenceProvider> registered,
            Optional<? extends SFMReleaseReviewSelectorProposalAdapter.SemanticEvidenceProvider> exactJavaProvider
    ) {
        Objects.requireNonNull(registered, "registered");
        Objects.requireNonNull(exactJavaProvider, "exactJavaProvider");
        TreeMap<String, SFMReleaseReviewSelectorProposalAdapter.SemanticEvidenceProvider> byId = new TreeMap<>();
        for (SFMReleaseReviewSelectorProposalAdapter.SemanticEvidenceProvider provider : registered) {
            Objects.requireNonNull(provider, "registered provider");
            SFMReleaseReviewSelectorProposalAdapter.SemanticEvidenceProvider previous =
                    byId.putIfAbsent(provider.id(), provider);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate registered semantic provider " + provider.id());
            }
        }
        exactJavaProvider.ifPresent(provider -> byId.put(provider.id(), provider));
        return List.copyOf(byId.values());
    }

    public static boolean isPinnedReleaseReviewDocument(SFMContextDocumentProjection projection) {
        boolean pinnedSource = projection.baseline().path()
                .filter(path -> path.kind() == SFMPath.Kind.CONTRIBUTED)
                .filter(path -> path.scheme().equals("review") && path.authority().equals("document"))
                .filter(path -> !path.segments().isEmpty())
                .isPresent();
        if (pinnedSource) return true;
        return projection.baseline().path()
                .filter(path -> path.kind() == SFMPath.Kind.CONTRIBUTED)
                .filter(path -> path.scheme().equals("review-surface") && path.authority().equals("generated"))
                .filter(ignored -> SFMReleaseReviewSurfaceRuntime.get()
                        .sourceMap(projection.baseline()).isPresent())
                .isPresent();
    }

    static Capture captureGeneratedSurface(
            SFMClientActionContext actionContext,
            SFMContextDocumentProjection projection,
            SFMReleaseReviewV1 review,
            SFMPath generatedPath,
            SFMReleaseReviewSurfaceRuntime surfaceRuntime
    ) {
        Objects.requireNonNull(surfaceRuntime, "surfaceRuntime");
        SFMReleaseReviewSurfaceV1.Surface surface = surfaceRuntime
                .sourceMap(projection.baseline())
                .orElseThrow(() -> new IllegalArgumentException(
                        "The generated review surface no longer has an exact source map"));
        List<SFMTextDocumentSelection> surfaceSelections = exactSelections(projection);
        ArrayList<SFMReleaseReviewSurfaceV1.SourceRange> sourceRanges = new ArrayList<>();
        for (SFMTextDocumentSelection selection : surfaceSelections) {
            SFMTextDocumentRange range = selection.orderedRange();
            sourceRanges.addAll(surface.sourceRangesFor(new SFMReleaseReviewSurfaceV1.Utf8Range(
                    range.start().byteOffset(), range.end().byteOffset())));
        }
        sourceRanges = sourceRanges.stream().distinct()
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (sourceRanges.isEmpty()) {
            throw new IllegalArgumentException(
                    "Select source-backed diff text; headers and diagnostic-only regions cannot own comments"
                            + " (selected=" + surfaceSelections.stream()
                            .map(selection -> selection.orderedRange().start().byteOffset() + ".."
                                    + selection.orderedRange().end().byteOffset())
                            .toList() + ", mappings=" + surface.mappings().size() + ")");
        }

        SFMReleaseReviewCorpus corpus = SFMReleaseReviewCorpus.from(review);
        TreeMap<String, ArrayList<SFMReleaseReviewSurfaceV1.SourceRange>> byRevision = new TreeMap<>();
        for (SFMReleaseReviewSurfaceV1.SourceRange sourceRange : sourceRanges) {
            SFMReleaseReviewCorpus.DocumentView source = corpus.documentRevision(sourceRange.documentRevisionId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Generated diff mapping names an absent corpus revision "
                                    + sourceRange.documentRevisionId()));
            if (!source.binding().sha256().equals(
                    SFMReleaseReviewSurfaceV1.snapshotSha256(sourceRange.documentSha256()))) {
                throw new IllegalArgumentException(
                        "Generated diff mapping hash is stale for " + sourceRange.documentRevisionId());
            }
            byRevision.computeIfAbsent(sourceRange.documentRevisionId(), ignored -> new ArrayList<>())
                    .add(sourceRange);
        }

        String selectionRevision = generatedSelectionRevision(
                projection.currentSha256(), generatedPath, sourceRanges);
        ArrayList<SFMReleaseReviewSelectionAdapter.OrderedDocumentSelection> documents = new ArrayList<>();
        int ordinal = 0;
        boolean primaryAssigned = false;
        for (Map.Entry<String, ArrayList<SFMReleaseReviewSurfaceV1.SourceRange>> entry : byRevision.entrySet()) {
            SFMReleaseReviewCorpus.DocumentView source = corpus.documentRevision(entry.getKey()).orElseThrow();
            String sourceText = source.materializedDocument().orElseThrow(() ->
                    new IllegalArgumentException("Mapped corpus source is not materialized: " + entry.getKey()))
                    .text();
            ArrayList<SFMTextDocumentSelection> selections = new ArrayList<>();
            for (SFMReleaseReviewSurfaceV1.SourceRange range : entry.getValue()) {
                SFMTextDocumentPosition start = SFMTextDocumentRange.positionAtByteOffset(
                        sourceText, range.range().startByte());
                SFMTextDocumentPosition end = SFMTextDocumentRange.positionAtByteOffset(
                        sourceText, range.range().endByte());
                boolean primary = !primaryAssigned;
                primaryAssigned |= primary;
                selections.add(new SFMTextDocumentSelection(
                        "surface-map-" + ordinal++, start, end, primary));
            }
            SFMReleaseReviewSelectionAdapter.DocumentWitness witness =
                    SFMReleaseReviewSelectionAdapter.DocumentWitness.capture(
                            entry.getKey(),
                            selectionRevision,
                            sourceAddress(source.binding()),
                            sourceText
                    );
            documents.add(new SFMReleaseReviewSelectionAdapter.OrderedDocumentSelection(witness, selections));
        }

        SFMReleaseReviewSelectionAdapter.SelectionCapture selection =
                new SFMReleaseReviewSelectionAdapter.SelectionCapture(
                        selectionRevision, generatedPath.canonical(), documents);
        SFMReleaseReviewSelectionAdapter.AdaptedSelection adapted =
                SFMReleaseReviewSelectionAdapter.adapt(selection);
        SFMReleaseReviewSelectionAdapter.DocumentResolver resolver =
                SFMReleaseReviewSelectionAdapter.resolver(selection);
        List<SFMReleaseReviewSelectorProposalAdapter.SemanticEvidenceProvider> providers =
                SFMReleaseReviewSemanticProviderRegistry.global().snapshot();
        SFMReleaseReviewSelectorProposalAdapter.ProposalBatch proposals =
                SFMReleaseReviewSelectorProposalAdapter.propose(adapted, resolver, providers);
        ArrayList<String> diagnostics = new ArrayList<>();
        diagnostics.add("Projected " + surfaceSelections.size() + " generated selection(s) to "
                + sourceRanges.size() + " pinned source range(s) through " + surface.algorithm());
        surface.diagnostics().forEach(value -> diagnostics.add(value.displayText()));
        proposals.diagnostics().forEach(value -> diagnostics.add(
                value.code() + " [" + value.providerId() + "]: " + value.message()));
        return new Capture(selection, adapted, resolver, proposals, diagnostics);
    }

    private static String generatedSelectionRevision(
            String surfaceSha256,
            SFMPath path,
            List<SFMReleaseReviewSurfaceV1.SourceRange> ranges
    ) {
        StringBuilder witness = new StringBuilder(path.canonical()).append('\n').append(surfaceSha256);
        for (SFMReleaseReviewSurfaceV1.SourceRange range : ranges) {
            witness.append('\n').append(range.documentRevisionId())
                    .append(':').append(range.range().startByte())
                    .append(':').append(range.range().endByte());
        }
        return "release-review-surface-selection:" + SFMReviewSessionV1Kernel.sha256(
                witness.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String sourceAddress(SFMReleaseReviewV1.CorpusDocument document) {
        return "review://document/" + document.documentRevisionId() + "/" + document.path();
    }

    private static List<SFMTextDocumentSelection> exactSelections(SFMContextDocumentProjection projection) {
        ArrayList<SFMTextDocumentSelection> answer = new ArrayList<>();
        HashSet<String> ids = new HashSet<>();
        for (SFMContextSelectionProjection group : projection.selections()) {
            for (SFMTextDocumentSelection selection : group.directionalSelections()) {
                String id = selection.id();
                if (!ids.add(id)) {
                    id = group.id() + "-" + id;
                    if (!ids.add(id)) throw new IllegalArgumentException("Duplicate exact selection id " + id);
                }
                answer.add(new SFMTextDocumentSelection(
                        id, selection.anchor(), selection.active(), selection.primary()));
            }
        }
        if (answer.isEmpty()) {
            SFMTextDocumentPosition position = projection.cursors().stream()
                    .filter(SFMContextCursorProjection::primary)
                    .map(SFMContextCursorProjection::position)
                    .map(SFMReleaseReviewEditorCapture::textPosition)
                    .flatMap(Optional::stream)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Select text or place the primary cursor on an addressed glyph"));
            answer.add(new SFMTextDocumentSelection("primary-cursor", position, position, true));
        }
        long primaryCount = answer.stream().filter(SFMTextDocumentSelection::primary).count();
        if (primaryCount != 1) {
            throw new IllegalArgumentException("The focused editor selection requires exactly one primary range");
        }
        answer.forEach(value -> value.validateAgainst(projection.currentText()));
        return List.copyOf(answer);
    }

    private static Optional<SFMTextDocumentPosition> textPosition(SFMContextPosition position) {
        if (position instanceof SFMContextPosition.Text text) return Optional.of(text.position());
        return ((SFMContextPosition.Canvas) position).textHit();
    }

    private static String selectionRevision(
            String documentRevisionId,
            SFMContextDocumentProjection projection,
            List<SFMTextDocumentSelection> selections
    ) {
        StringBuilder witness = new StringBuilder(documentRevisionId)
                .append('\n').append(projection.currentSha256());
        for (SFMTextDocumentSelection selection : selections) {
            witness.append('\n').append(selection.id())
                    .append(':').append(selection.anchor().byteOffset())
                    .append(':').append(selection.active().byteOffset())
                    .append(':').append(selection.primary());
        }
        return "release-review-selection:" + SFMReviewSessionV1Kernel.sha256(
                witness.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Optional<SFMJavaInteractionMap.Result> currentInteractionMap(
            SFMClientActionContext actionContext
    ) {
        if (!(actionContext.originatingHost() instanceof SFMScreenMultiplexer workspace)
                || actionContext.originatingPanelId() == null
                || !(workspace.panelInstance(actionContext.originatingPanelId()) instanceof SFMTextEditorPanel panel)) {
            return Optional.empty();
        }
        return panel.currentJavaInteractionMap();
    }
}
