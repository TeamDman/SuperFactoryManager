package ca.teamdman.sfm.client.semantic;

import ca.teamdman.sfm.client.screen.SFMDrawCanvasModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMCanvasSpatialCoverageSnapshotTests {
    @Test
    void semanticGlyphsUseTheMapWhileAnExplicitWhitespaceGlyphIsAnApprovedException() {
        SFMDrawCanvasModel model = new SFMDrawCanvasModel();
        model.replaceText("a b", ignored -> 1, 1);
        var layout = model.documentIndex(1, 1);
        var document = SFMCanvasSpatialCoverageSnapshot.capture(
                "file:///A.java", "main", "sha256:test", layout, 1, 1, 2, 3,
                offset -> offset == 0 || offset == 2 ? Optional.of(resolved(offset)) : Optional.empty());
        var request = new SFMSpatialSemanticContract.CoverageRequest(
                SFMSpatialSemanticContract.COVERAGE_REQUEST_SCHEMA,
                "coverage:canvas", SFMSpatialSemanticContract.Scope.DOCUMENT, "focused",
                "sfm:strict_java_navigation", "sfm:auto_1_through_8", 0, 100, "auto",
                new SFMSpatialSemanticContract.SnapshotIdentity(
                        "workspace:test", 1, "file:///A.java", "sha256:test", 2,
                        "semantic:test", 4, "layout:test", 3));
        var run = new SFMSpatialCoverageService().run(
                request,
                new SFMSpatialCoverageService.WorkspaceSnapshot("workspace:test", List.of(document)),
                SFMSpatialSamplingPolicies.exhaustive());

        var navigation = run.report().dimensions().stream()
                .filter(dimension -> dimension.id().equals("navigation")).findFirst().orElseThrow();
        assertEquals(2, navigation.total());
        assertEquals(2, navigation.covered());
        assertTrue(run.exceptions().stream().noneMatch(row -> row.kind().equals("navigation-uncovered")));
        assertEquals(SFMSpatialSemanticContract.FileState.COVERED, run.report().files().get(0).state());
    }

    private static SFMCanvasSpatialCoverageSnapshot.SemanticResult resolved(int offset) {
        String regionId = "java-region:" + offset;
        var outlink = new SFMSpatialSemanticContract.Outlink(
                SFMSpatialSemanticContract.OUTLINK_SCHEMA,
                "java-outlink:" + offset,
                regionId,
                null,
                "java:definition:" + offset,
                "definition",
                SFMSpatialSemanticContract.Intent.NAVIGATE,
                "sfm:java-interaction-map",
                4,
                "resolved fixture definition",
                SFMSpatialSemanticContract.Confidence.RESOLVED,
                SFMSpatialSemanticContract.Completeness.COMPLETE,
                "start",
                List.of(new SFMSpatialSemanticContract.ActionDraft(
                        "sfm:symbol/definition/open", List.of())),
                "sfm:test"
        );
        return new SFMCanvasSpatialCoverageSnapshot.SemanticResult(
                new SFMSpatialSemanticContract.Classification(
                        SFMSpatialSemanticContract.ClassificationStatus.ACTIONABLE, null),
                List.of(outlink),
                List.of(),
                List.of(new SFMSpatialSemanticContract.ProviderEvidence(
                        "sfm:java-interaction-map", 100, "matched", null)),
                "sfm:java-interaction-map",
                4,
                true,
                true
        );
    }
}
