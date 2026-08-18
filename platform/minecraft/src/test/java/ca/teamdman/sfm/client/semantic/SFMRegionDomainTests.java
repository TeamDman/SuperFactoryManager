package ca.teamdman.sfm.client.semantic;

import ca.teamdman.sfm.client.context.SFMContextCanvasTextMap;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentPosition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMRegionDomainTests {
    @Test
    void domainAndRegionBoundsAreImmutableFiniteAndHalfOpen() {
        assertThrows(IllegalArgumentException.class, () -> new SFMSpatialSemanticContract.Domain(
                SFMSpatialSemanticContract.DOMAIN_SCHEMA, "canvas:test",
                SFMSpatialSemanticContract.DomainKind.CANVAS, 2, List.of("x"), "sfm:test", "layout"));
        var region = region("glyph", 1, 4);
        assertTrue(region.contains(List.of(1.0, 0.0)));
        assertFalse(region.contains(List.of(4.0, 0.0)));
        assertFalse(region.contains(List.of(2.0)));
        assertThrows(UnsupportedOperationException.class, () -> region.bounds().clear());
    }

    @Test
    void existingCanvasTextMapAdaptsWithoutChangingItsHitSemantics() {
        var position = new SFMTextDocumentPosition(1, 2, 7);
        var map = new SFMContextCanvasTextMap(List.of(
                new SFMContextCanvasTextMap.HitRegion(10, 20, 8, 9, position)));
        var adapted = SFMCanvasTextRegionAdapter.adapt(map, "document-7", "layout-9", "sfm:test");

        assertEquals(SFMSpatialSemanticContract.DomainKind.CANVAS, adapted.canvasDomain().kind());
        assertEquals(SFMSpatialSemanticContract.DomainKind.UTF8, adapted.utf8Domain().kind());
        assertTrue(adapted.canvasRegions().get(0).contains(List.of(10.0, 20.0)));
        assertTrue(adapted.canvasRegions().get(0).contains(List.of(17.999, 28.999)));
        assertFalse(adapted.canvasRegions().get(0).contains(List.of(18.0, 20.0)));
        assertTrue(adapted.utf8Regions().get(0).contains(List.of(7.0)));
        assertEquals(SFMSpatialSemanticContract.ProjectionLoss.ONE_TO_MANY, adapted.projection().loss());
    }

    @Test
    void graphRetainsContainmentChildrenAndExplicitDestinationProjection() {
        var graph = new SFMRegionGraph(snapshot());
        var parent = region("method", 0, 20);
        var signature = region("signature", 0, 8, "signature");
        var body = region("body", 8, 20, "body");
        graph.addRegion(parent);
        graph.addRegion(signature);
        graph.addRegion(body);
        graph.addOutlink(outlink("child-signature", parent.id(), signature.id(), "child-region"));
        graph.addOutlink(outlink("child-body", parent.id(), body.id(), "child-region"));

        assertEquals(List.of(parent, signature), graph.containing("canvas:test", List.of(2.0, 1.0)));
        assertEquals(List.of(signature, body), graph.children(parent.id()));
        assertEquals(List.of(4.0, 1.0), graph.project(parent.id(), SFMNavigationProjection.percentage(0.2)).coordinate());
        assertEquals(signature, graph.project(parent.id(), SFMNavigationProjection.named("signature")).region());
        assertEquals(body, graph.project(parent.id(), SFMNavigationProjection.indexed(
                SFMNavigationProjection.Kind.NTH_CHILD, 1)).region());
        assertThrows(IllegalArgumentException.class, () -> graph.project(parent.id(),
                SFMNavigationProjection.indexed(SFMNavigationProjection.Kind.NTH_SOURCE_LINE, 100)));
    }

    @Test
    void snapshotIdentityRejectsMissingFingerprintsAndNegativeGenerations() {
        assertThrows(IllegalArgumentException.class, () -> new SFMSpatialSemanticContract.SnapshotIdentity(
                "", 0, "file:///A.java", "sha256:a", 0, "blake3:s", 0, "blake3:l", 0));
        assertThrows(IllegalArgumentException.class, () -> new SFMSpatialSemanticContract.SnapshotIdentity(
                "blake3:w", -1, "file:///A.java", "sha256:a", 0, "blake3:s", 0, "blake3:l", 0));
    }

    private static SFMSpatialSemanticContract.Region region(String id, double left, double right) {
        return region(id, left, right, "java-region");
    }

    private static SFMSpatialSemanticContract.Region region(
            String id, double left, double right, String semanticKind
    ) {
        return new SFMSpatialSemanticContract.Region(
                SFMSpatialSemanticContract.REGION_SCHEMA, id, "canvas:test",
                SFMSpatialSemanticContract.Representation.RECTANGLE,
                List.of(new SFMSpatialSemanticContract.AxisBound(left, right),
                        new SFMSpatialSemanticContract.AxisBound(0, 5)),
                "half-open", semanticKind, "sfm:test", List.of());
    }

    private static SFMSpatialSemanticContract.Outlink outlink(
            String id, String source, String destination, String relation
    ) {
        return new SFMSpatialSemanticContract.Outlink(
                SFMSpatialSemanticContract.OUTLINK_SCHEMA, id, source, destination, null, relation,
                SFMSpatialSemanticContract.Intent.NAVIGATE, "sfm:test", 1, relation,
                SFMSpatialSemanticContract.Confidence.RESOLVED,
                SFMSpatialSemanticContract.Completeness.COMPLETE, "start", List.of(), "sfm:test");
    }

    private static SFMSpatialSemanticContract.SnapshotIdentity snapshot() {
        return new SFMSpatialSemanticContract.SnapshotIdentity(
                "blake3:w", 1, "file:///A.java", "sha256:a", 2,
                "blake3:s", 3, "blake3:l", 4);
    }
}
