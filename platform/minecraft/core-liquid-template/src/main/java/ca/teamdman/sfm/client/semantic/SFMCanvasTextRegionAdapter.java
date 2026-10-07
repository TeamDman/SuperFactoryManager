package ca.teamdman.sfm.client.semantic;

import ca.teamdman.sfm.client.context.SFMContextCanvasTextMap;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentPosition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Preserves the existing canvas/text map while exposing typed v1 regions. */
public final class SFMCanvasTextRegionAdapter {
    private SFMCanvasTextRegionAdapter() {
    }

    public record Adapted(
            SFMSpatialSemanticContract.Domain canvasDomain,
            SFMSpatialSemanticContract.Domain utf8Domain,
            SFMSpatialSemanticContract.Projection projection,
            List<SFMSpatialSemanticContract.Region> canvasRegions,
            List<SFMSpatialSemanticContract.Region> utf8Regions
    ) {
        public Adapted {
            Objects.requireNonNull(canvasDomain, "canvas domain");
            Objects.requireNonNull(utf8Domain, "utf8 domain");
            Objects.requireNonNull(projection, "projection");
            canvasRegions = List.copyOf(canvasRegions);
            utf8Regions = List.copyOf(utf8Regions);
        }
    }

    public static Adapted adapt(
            SFMContextCanvasTextMap map,
            String documentIdentity,
            String layoutIdentity,
            String authority
    ) {
        Objects.requireNonNull(map, "map");
        requireText(documentIdentity, "document identity");
        requireText(layoutIdentity, "layout identity");
        requireText(authority, "authority");
        String canvasId = "canvas:" + layoutIdentity;
        String utf8Id = "utf8:" + documentIdentity;
        String projectionId = "projection:" + documentIdentity + ":" + layoutIdentity;
        var canvas = new SFMSpatialSemanticContract.Domain(
                SFMSpatialSemanticContract.DOMAIN_SCHEMA, canvasId,
                SFMSpatialSemanticContract.DomainKind.CANVAS, 2, List.of("x", "y"), authority,
                layoutIdentity);
        var utf8 = new SFMSpatialSemanticContract.Domain(
                SFMSpatialSemanticContract.DOMAIN_SCHEMA, utf8Id,
                SFMSpatialSemanticContract.DomainKind.UTF8, 1, List.of("byte-offset"), authority,
                documentIdentity);
        var projection = new SFMSpatialSemanticContract.Projection(
                SFMSpatialSemanticContract.PROJECTION_SCHEMA, projectionId, utf8Id, canvasId,
                SFMSpatialSemanticContract.ProjectionLoss.ONE_TO_MANY,
                SFMSpatialSemanticContract.Completeness.COMPLETE,
                "sfm:canvas_text_map_v1", documentIdentity + "|" + layoutIdentity, authority);
        ArrayList<SFMSpatialSemanticContract.Region> canvasRegions = new ArrayList<>();
        ArrayList<SFMSpatialSemanticContract.Region> utf8Regions = new ArrayList<>();
        int ordinal = 0;
        for (SFMContextCanvasTextMap.HitRegion hit : map.regions()) {
            String suffix = Integer.toString(ordinal++);
            SFMTextDocumentPosition position = hit.position();
            canvasRegions.add(new SFMSpatialSemanticContract.Region(
                    SFMSpatialSemanticContract.REGION_SCHEMA,
                    "region:canvas:" + suffix,
                    canvasId,
                    SFMSpatialSemanticContract.Representation.RECTANGLE,
                    List.of(
                            new SFMSpatialSemanticContract.AxisBound(hit.x(), hit.x() + hit.width()),
                            new SFMSpatialSemanticContract.AxisBound(hit.y(), hit.y() + hit.height())
                    ),
                    "half-open", "text-glyph", authority, List.of(projectionId)));
            utf8Regions.add(new SFMSpatialSemanticContract.Region(
                    SFMSpatialSemanticContract.REGION_SCHEMA,
                    "region:utf8:" + suffix,
                    utf8Id,
                    SFMSpatialSemanticContract.Representation.INTERVAL,
                    List.of(new SFMSpatialSemanticContract.AxisBound(
                            position.byteOffset(), position.byteOffset() + 1.0D)),
                    "half-open", "text-position", authority, List.of(projectionId)));
        }
        return new Adapted(canvas, utf8, projection, canvasRegions, utf8Regions);
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
    }
}
