package ca.teamdman.sfm.client.semantic;

import ca.teamdman.sfm.client.screen.SFMDrawCanvasDocumentIndex;
import ca.teamdman.sfm.client.screen.SFMDrawCanvasModel;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable bridge from one real editor layout to the pure spatial coverage service. */
public final class SFMCanvasSpatialCoverageSnapshot {
    @FunctionalInterface
    public interface SemanticLookup {
        Optional<SemanticResult> atUtf16(int utf16Offset);
    }

    public record SemanticResult(
            SFMSpatialSemanticContract.Classification classification,
            List<SFMSpatialSemanticContract.Outlink> outlinks,
            List<SFMSpatialSemanticContract.ActionDraft> actionDrafts,
            List<SFMSpatialSemanticContract.ProviderEvidence> providerEvidence,
            String providerBranch,
            long semanticGeneration,
            boolean reciprocityExpected,
            boolean reciprocal
    ) {
        public SemanticResult {
            Objects.requireNonNull(classification, "classification");
            outlinks = List.copyOf(Objects.requireNonNull(outlinks, "outlinks"));
            actionDrafts = List.copyOf(Objects.requireNonNull(actionDrafts, "actionDrafts"));
            providerEvidence = List.copyOf(Objects.requireNonNull(providerEvidence, "providerEvidence"));
            if (providerBranch == null || providerBranch.isBlank()) {
                throw new IllegalArgumentException("providerBranch must not be blank");
            }
            if (semanticGeneration < 0) throw new IllegalArgumentException("semanticGeneration must be non-negative");
            if (!reciprocityExpected && reciprocal) {
                throw new IllegalArgumentException("reciprocal cannot be true when not expected");
            }
            if (classification.status() == SFMSpatialSemanticContract.ClassificationStatus.ACTIONABLE
                    && outlinks.isEmpty() && actionDrafts.isEmpty()) {
                throw new IllegalArgumentException("actionable semantics require an outlink or action");
            }
        }
    }

    private SFMCanvasSpatialCoverageSnapshot() {
    }

    public static SFMSpatialCoverageService.Document capture(
            String address,
            String sourceSet,
            String contentHash,
            SFMDrawCanvasDocumentIndex layout,
            int lineHeight,
            long workspaceGeneration,
            long documentGeneration,
            long layoutGeneration,
            SemanticLookup semanticLookup
    ) {
        requireText(address, "address");
        requireText(sourceSet, "sourceSet");
        requireText(contentHash, "contentHash");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(semanticLookup, "semanticLookup");
        if (lineHeight <= 0) throw new IllegalArgumentException("lineHeight must be positive");
        if (workspaceGeneration < 0 || documentGeneration < 0 || layoutGeneration < 0) {
            throw new IllegalArgumentException("generations must be non-negative");
        }
        SFMDrawCanvasDocumentIndex.ContentBounds bounds = layout.bounds()
                .orElse(new SFMDrawCanvasDocumentIndex.ContentBounds(0, 0, 1, lineHeight));
        int width = Math.max(1, (int) Math.ceil(Math.max(1.0D, bounds.right())));
        int height = Math.max(1, (int) Math.ceil(Math.max(lineHeight, bounds.bottom())));
        String domainId = "canvas:" + contentHash + ":" + layoutGeneration;
        return new SFMSpatialCoverageService.Document(
                address,
                sourceSet,
                contentHash,
                width,
                height,
                SFMSpatialSemanticContract.FileState.PARTIAL,
                "spatial coverage pending",
                (canvasX, canvasY) -> probe(
                        layout,
                        lineHeight,
                        domainId,
                        canvasX,
                        canvasY,
                        workspaceGeneration,
                        documentGeneration,
                        layoutGeneration,
                        semanticLookup
                )
        );
    }

    private static SFMSpatialCoverageService.Observation probe(
            SFMDrawCanvasDocumentIndex layout,
            int lineHeight,
            String domainId,
            double x,
            double y,
            long workspaceGeneration,
            long documentGeneration,
            long layoutGeneration,
            SemanticLookup semanticLookup
    ) {
        SFMDrawCanvasModel.CanvasGlyph glyph = layout.glyphAt(x, y).orElse(null);
        if (glyph == null) {
            var region = unitRegion(domainId, x, y, "canvas-whitespace", "sfm:canvas-layout");
            return observation(
                    domainId, x, y, region,
                    new SemanticResult(
                            new SFMSpatialSemanticContract.Classification(
                                    SFMSpatialSemanticContract.ClassificationStatus.EXPLICIT_NO_ACTION,
                                    "whitespace"),
                            List.of(), List.of(), List.of(new SFMSpatialSemanticContract.ProviderEvidence(
                            "sfm:canvas-layout", 0, "explicit-no-action", null)),
                            "sfm:canvas-layout", 0, false, false),
                    workspaceGeneration, documentGeneration, layoutGeneration
            );
        }
        int utf16Offset = layout.utf16OffsetOf(glyph).orElseThrow();
        int ordinal = layout.glyphOrdinalOf(glyph).orElseThrow();
        var region = new SFMSpatialSemanticContract.Region(
                SFMSpatialSemanticContract.REGION_SCHEMA,
                "canvas-glyph:" + ordinal,
                domainId,
                SFMSpatialSemanticContract.Representation.RECTANGLE,
                List.of(
                        new SFMSpatialSemanticContract.AxisBound(glyph.x(), glyph.x() + glyph.width()),
                        new SFMSpatialSemanticContract.AxisBound(glyph.y(), glyph.y() + lineHeight)
                ),
                "half-open",
                "text-glyph",
                "sfm:canvas-layout",
                List.of()
        );
        SemanticResult semantic = semanticLookup.atUtf16(utf16Offset).orElseGet(() -> {
            if (glyph.text().codePoints().allMatch(Character::isWhitespace)) {
                return new SemanticResult(
                        new SFMSpatialSemanticContract.Classification(
                                SFMSpatialSemanticContract.ClassificationStatus.EXPLICIT_NO_ACTION,
                                "whitespace"),
                        List.of(), List.of(), List.of(new SFMSpatialSemanticContract.ProviderEvidence(
                        "sfm:canvas-layout", 0, "explicit-no-action", null)),
                        "sfm:canvas-layout", 0, false, false);
            }
            return new SemanticResult(
                    new SFMSpatialSemanticContract.Classification(
                            SFMSpatialSemanticContract.ClassificationStatus.UNCLASSIFIED,
                            "semantic-map-missing-region"),
                    List.of(), List.of(), List.of(new SFMSpatialSemanticContract.ProviderEvidence(
                    "sfm:java-interaction-map", 100, "unsupported", "No semantic region covered the glyph")),
                    "sfm:java-interaction-map", 0, false, false);
        });
        return observation(
                domainId, x, y, region, semantic,
                workspaceGeneration, documentGeneration, layoutGeneration
        );
    }

    private static SFMSpatialCoverageService.Observation observation(
            String domainId,
            double x,
            double y,
            SFMSpatialSemanticContract.Region certifiedRegion,
            SemanticResult semantic,
            long workspaceGeneration,
            long documentGeneration,
            long layoutGeneration
    ) {
        var probe = new SFMSpatialSemanticContract.Probe(
                SFMSpatialSemanticContract.PROBE_SCHEMA,
                domainId,
                List.of(x, y),
                SFMSpatialSemanticContract.Intent.NAVIGATE,
                certifiedRegion,
                semantic.classification(),
                semantic.outlinks(),
                semantic.actionDrafts(),
                semantic.providerEvidence(),
                workspaceGeneration,
                documentGeneration,
                semantic.semanticGeneration(),
                layoutGeneration
        );
        return new SFMSpatialCoverageService.Observation(
                probe,
                true,
                semantic.providerBranch(),
                true,
                semantic.reciprocityExpected(),
                semantic.reciprocal()
        );
    }

    private static SFMSpatialSemanticContract.Region unitRegion(
            String domainId,
            double x,
            double y,
            String semanticKind,
            String provenance
    ) {
        double left = Math.floor(x);
        double top = Math.floor(y);
        return new SFMSpatialSemanticContract.Region(
                SFMSpatialSemanticContract.REGION_SCHEMA,
                "canvas-cell:" + (long) left + ":" + (long) top,
                domainId,
                SFMSpatialSemanticContract.Representation.RECTANGLE,
                List.of(new SFMSpatialSemanticContract.AxisBound(left, left + 1.0D),
                        new SFMSpatialSemanticContract.AxisBound(top, top + 1.0D)),
                "half-open", semanticKind, provenance, List.of());
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
    }
}
