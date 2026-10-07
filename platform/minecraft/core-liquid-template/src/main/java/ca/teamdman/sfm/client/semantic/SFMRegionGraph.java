package ca.teamdman.sfm.client.semantic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Generation-scoped region/outlink graph with deterministic landmark projection. */
public final class SFMRegionGraph {
    public record Destination(
            SFMSpatialSemanticContract.Region region,
            List<Double> coordinate,
            SFMNavigationProjection projection
    ) {
        public Destination {
            Objects.requireNonNull(region, "region");
            coordinate = List.copyOf(coordinate);
            Objects.requireNonNull(projection, "projection");
        }
    }

    private final SFMSpatialSemanticContract.SnapshotIdentity snapshot;
    private final Map<String, SFMSpatialSemanticContract.Region> regions = new LinkedHashMap<>();
    private final List<SFMSpatialSemanticContract.Outlink> outlinks = new ArrayList<>();

    public SFMRegionGraph(SFMSpatialSemanticContract.SnapshotIdentity snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    public SFMSpatialSemanticContract.SnapshotIdentity snapshot() {
        return snapshot;
    }

    public synchronized void addRegion(SFMSpatialSemanticContract.Region region) {
        Objects.requireNonNull(region, "region");
        if (regions.putIfAbsent(region.id(), region) != null) {
            throw new IllegalArgumentException("Duplicate region: " + region.id());
        }
    }

    public synchronized void addOutlink(SFMSpatialSemanticContract.Outlink outlink) {
        Objects.requireNonNull(outlink, "outlink");
        if (!regions.containsKey(outlink.sourceRegionId())) {
            throw new IllegalArgumentException("Unknown source region: " + outlink.sourceRegionId());
        }
        if (outlink.destinationRegionId() != null && !regions.containsKey(outlink.destinationRegionId())) {
            throw new IllegalArgumentException("Unknown destination region: " + outlink.destinationRegionId());
        }
        if (outlinks.stream().anyMatch(existing -> existing.id().equals(outlink.id()))) {
            throw new IllegalArgumentException("Duplicate outlink: " + outlink.id());
        }
        outlinks.add(outlink);
    }

    public synchronized Optional<SFMSpatialSemanticContract.Region> region(String id) {
        return Optional.ofNullable(regions.get(id));
    }

    public synchronized List<SFMSpatialSemanticContract.Region> containing(String domainId, List<Double> point) {
        return regions.values().stream()
                .filter(region -> region.domainId().equals(domainId) && region.contains(point))
                .sorted(java.util.Comparator.comparing(SFMSpatialSemanticContract.Region::id))
                .toList();
    }

    public synchronized List<SFMSpatialSemanticContract.Region> children(String parentId) {
        return outlinks.stream()
                .filter(link -> link.sourceRegionId().equals(parentId)
                        && link.relationKind().equals("child-region")
                        && link.destinationRegionId() != null)
                .map(link -> regions.get(link.destinationRegionId()))
                .filter(Objects::nonNull)
                .toList();
    }

    public synchronized Destination project(String regionId, SFMNavigationProjection projection) {
        SFMSpatialSemanticContract.Region selected = Objects.requireNonNull(regions.get(regionId),
                () -> "Unknown region: " + regionId);
        if (projection.kind() == SFMNavigationProjection.Kind.NTH_CHILD) {
            List<SFMSpatialSemanticContract.Region> children = children(regionId);
            if (projection.index() >= children.size()) throw new IllegalArgumentException("child index out of bounds");
            selected = children.get(projection.index());
        } else if (projection.kind() == SFMNavigationProjection.Kind.NAMED_LANDMARK) {
            String semantic = projection.landmark();
            selected = children(regionId).stream()
                    .filter(child -> child.semanticKind().equals(semantic))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("unknown landmark: " + semantic));
        }
        double fraction = switch (projection.kind()) {
            case END -> 1.0D;
            case CENTRE -> 0.5D;
            case PERCENTAGE -> projection.percentage();
            case START, NTH_CHILD, NAMED_LANDMARK -> 0.0D;
            case NTH_SOURCE_ROW, NTH_SOURCE_LINE -> throw new IllegalArgumentException(
                    "source row/line projection requires a line-index adapter");
        };
        List<Double> coordinate = selected.bounds().stream().map(bound ->
                bound.startInclusive() + (bound.endExclusive() - bound.startInclusive()) * fraction).toList();
        return new Destination(selected, coordinate, projection);
    }
}
