package ca.teamdman.sfm.client.semantic;

import java.util.List;
import java.util.Objects;

/**
 * Version-one wire values joining Rust-owned source semantics to Java-owned
 * canvas interaction.  Bounds are half-open throughout this contract.
 */
public final class SFMSpatialSemanticContract {
    public static final String DOMAIN_SCHEMA = "sfm.region-domain/1";
    public static final String REGION_SCHEMA = "sfm.region/1";
    public static final String PROJECTION_SCHEMA = "sfm.region-projection/1";
    public static final String OUTLINK_SCHEMA = "sfm.outlink/1";
    public static final String PROBE_SCHEMA = "sfm.interaction-probe-result/1";
    public static final String COVERAGE_REQUEST_SCHEMA = "sfm.spatial-coverage-request/1";
    public static final String COVERAGE_REPORT_SCHEMA = "sfm.spatial-coverage-report/1";
    public static final String FRAMING_SCHEMA = "sfm.navigation-framing-observation/1";

    private SFMSpatialSemanticContract() {
    }

    public enum DomainKind {
        CANVAS("canvas"), SCREEN("screen"), UTF8("utf8"), TEXT("text"),
        JAVA_SYNTAX("java-syntax"), PATH("path");

        private final String wireName;

        DomainKind(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static DomainKind fromWireName(String value) {
            for (DomainKind kind : values()) if (kind.wireName.equals(value)) return kind;
            throw new IllegalArgumentException("Unknown domain kind: " + value);
        }
    }

    public enum Representation {
        RECTANGLE("rectangle"), INTERVAL("interval"), SYNTAX_NODE("syntax-node"),
        FINITE_POINTS("finite-points"), WHOLE_DOMAIN("whole-domain");

        private final String wireName;

        Representation(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static Representation fromWireName(String value) {
            for (Representation kind : values()) if (kind.wireName.equals(value)) return kind;
            throw new IllegalArgumentException("Unknown region representation: " + value);
        }
    }

    public enum ProjectionLoss {
        LOSSLESS("lossless"), MANY_TO_ONE("many-to-one"), ONE_TO_MANY("one-to-many"), PARTIAL("partial");

        private final String wireName;

        ProjectionLoss(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static ProjectionLoss fromWireName(String value) {
            for (ProjectionLoss kind : values()) if (kind.wireName.equals(value)) return kind;
            throw new IllegalArgumentException("Unknown projection loss: " + value);
        }
    }

    public enum Completeness {
        COMPLETE("complete"), INCOMPLETE("incomplete");

        private final String wireName;

        Completeness(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static Completeness fromWireName(String value) {
            for (Completeness kind : values()) if (kind.wireName.equals(value)) return kind;
            throw new IllegalArgumentException("Unknown completeness: " + value);
        }
    }

    public enum Intent {
        NAVIGATE("navigate"), INSPECT("inspect"), COPY("copy"), EDIT("edit"), CONTEXT("context");

        private final String wireName;

        Intent(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static Intent fromWireName(String value) {
            for (Intent kind : values()) if (kind.wireName.equals(value)) return kind;
            throw new IllegalArgumentException("Unknown intent: " + value);
        }
    }

    public enum ClassificationStatus {
        ACTIONABLE("actionable"), EXPLICIT_NO_ACTION("explicit-no-action"),
        UNSUPPORTED("unsupported"), UNCLASSIFIED("unclassified");

        private final String wireName;

        ClassificationStatus(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static ClassificationStatus fromWireName(String value) {
            for (ClassificationStatus kind : values()) if (kind.wireName.equals(value)) return kind;
            throw new IllegalArgumentException("Unknown classification: " + value);
        }
    }

    public enum Confidence {
        RESOLVED("resolved"), PARTIALLY_RESOLVED("partially-resolved"), RECOVERY("recovery");

        private final String wireName;

        Confidence(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static Confidence fromWireName(String value) {
            for (Confidence kind : values()) if (kind.wireName.equals(value)) return kind;
            throw new IllegalArgumentException("Unknown confidence: " + value);
        }
    }

    public enum Scope {
        DOCUMENT("document"), WORKSPACE("workspace");

        private final String wireName;

        Scope(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static Scope fromWireName(String value) {
            for (Scope kind : values()) if (kind.wireName.equals(value)) return kind;
            throw new IllegalArgumentException("Unknown coverage scope: " + value);
        }
    }

    public enum FileState {
        COVERED("covered"), PARTIAL("partial"), UNSUPPORTED_EXTENSION("unsupported-extension"),
        MISSING("missing"), STALE("stale"), PARSE_FAILED("parse-failed"),
        LAYOUT_FAILED("layout-failed"), INDEX_FAILED("index-failed"), TIMEOUT("timeout"),
        SKIPPED("skipped"), FAILED("failed");

        private final String wireName;

        FileState(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static FileState fromWireName(String value) {
            for (FileState kind : values()) if (kind.wireName.equals(value)) return kind;
            throw new IllegalArgumentException("Unknown file state: " + value);
        }
    }

    public record Domain(
            String schema,
            String id,
            DomainKind kind,
            int dimensions,
            List<String> coordinateKinds,
            String authority,
            String snapshotIdentity
    ) {
        public Domain {
            requireSchema(schema, DOMAIN_SCHEMA);
            requireText(id, "domain id");
            Objects.requireNonNull(kind, "kind");
            if (dimensions <= 0) throw new IllegalArgumentException("dimensions must be positive");
            coordinateKinds = immutableTextList(coordinateKinds, "coordinate kinds");
            if (coordinateKinds.size() != dimensions) {
                throw new IllegalArgumentException("coordinate kind count must equal dimensions");
            }
            requireText(authority, "domain authority");
            requireText(snapshotIdentity, "snapshot identity");
        }
    }

    public record AxisBound(double startInclusive, double endExclusive) {
        public AxisBound {
            if (!Double.isFinite(startInclusive) || !Double.isFinite(endExclusive)) {
                throw new IllegalArgumentException("bounds must be finite");
            }
            if (endExclusive < startInclusive) throw new IllegalArgumentException("bounds must be ordered");
        }

        public boolean contains(double value) {
            return Double.isFinite(value) && startInclusive <= value && value < endExclusive;
        }
    }

    public record Region(
            String schema,
            String id,
            String domainId,
            Representation representation,
            List<AxisBound> bounds,
            String edgePolicy,
            String semanticKind,
            String provenance,
            List<String> projectionIds
    ) {
        public Region {
            requireSchema(schema, REGION_SCHEMA);
            requireText(id, "region id");
            requireText(domainId, "domain id");
            Objects.requireNonNull(representation, "representation");
            bounds = List.copyOf(Objects.requireNonNull(bounds, "bounds"));
            if (!"half-open".equals(edgePolicy)) throw new IllegalArgumentException("edge_policy must be half-open");
            requireText(semanticKind, "semantic kind");
            requireText(provenance, "provenance");
            projectionIds = immutableTextList(projectionIds, "projection ids");
        }

        public boolean contains(List<Double> point) {
            Objects.requireNonNull(point, "point");
            if (bounds.size() != point.size()) return false;
            for (int index = 0; index < bounds.size(); index++) {
                if (!bounds.get(index).contains(point.get(index))) return false;
            }
            return true;
        }
    }

    public record Projection(
            String schema,
            String id,
            String fromDomainId,
            String toDomainId,
            ProjectionLoss loss,
            Completeness completeness,
            String transform,
            String fingerprint,
            String authority
    ) {
        public Projection {
            requireSchema(schema, PROJECTION_SCHEMA);
            requireText(id, "projection id");
            requireText(fromDomainId, "from domain id");
            requireText(toDomainId, "to domain id");
            Objects.requireNonNull(loss, "loss");
            Objects.requireNonNull(completeness, "completeness");
            requireText(transform, "transform");
            requireText(fingerprint, "fingerprint");
            requireText(authority, "authority");
        }
    }

    public record ActionDraft(String actionId, List<String> arguments) {
        public ActionDraft {
            requireNamespaced(actionId, "action id");
            arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        }
    }

    public record Outlink(
            String schema,
            String id,
            String sourceRegionId,
            String destinationRegionId,
            String destinationQuery,
            String relationKind,
            Intent intent,
            String providerId,
            long providerGeneration,
            String reason,
            Confidence confidence,
            Completeness completeness,
            String recommendedProjection,
            List<ActionDraft> actionDrafts,
            String provenance
    ) {
        public Outlink {
            requireSchema(schema, OUTLINK_SCHEMA);
            requireText(id, "outlink id");
            requireText(sourceRegionId, "source region id");
            if (blank(destinationRegionId) == blank(destinationQuery)) {
                throw new IllegalArgumentException("exactly one destination region or query is required");
            }
            requireText(relationKind, "relation kind");
            Objects.requireNonNull(intent, "intent");
            requireNamespaced(providerId, "provider id");
            if (providerGeneration < 0) throw new IllegalArgumentException("provider generation must be non-negative");
            requireText(reason, "reason");
            Objects.requireNonNull(confidence, "confidence");
            Objects.requireNonNull(completeness, "completeness");
            requireText(recommendedProjection, "recommended projection");
            actionDrafts = List.copyOf(Objects.requireNonNull(actionDrafts, "action drafts"));
            requireText(provenance, "provenance");
        }
    }

    public record Classification(ClassificationStatus status, String reasonCode) {
        public Classification {
            Objects.requireNonNull(status, "status");
            if (status == ClassificationStatus.ACTIONABLE) {
                if (!blank(reasonCode)) throw new IllegalArgumentException("actionable classification has no reason code");
            } else requireText(reasonCode, "classification reason code");
        }
    }

    public record ProviderEvidence(String providerId, int priority, String outcome, String diagnostic) {
        public ProviderEvidence {
            requireNamespaced(providerId, "provider id");
            requireText(outcome, "provider outcome");
        }
    }

    public record Probe(
            String schema,
            String queryDomainId,
            List<Double> queryPoint,
            Intent requestedIntent,
            Region certifiedRegion,
            Classification classification,
            List<Outlink> outlinks,
            List<ActionDraft> actionDrafts,
            List<ProviderEvidence> providerEvidence,
            long workspaceGeneration,
            long documentGeneration,
            long semanticGeneration,
            long layoutGeneration
    ) {
        public Probe {
            requireSchema(schema, PROBE_SCHEMA);
            requireText(queryDomainId, "query domain id");
            queryPoint = List.copyOf(Objects.requireNonNull(queryPoint, "query point"));
            if (queryPoint.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
                throw new IllegalArgumentException("query point must be finite");
            }
            Objects.requireNonNull(requestedIntent, "requested intent");
            Objects.requireNonNull(certifiedRegion, "certified region");
            Objects.requireNonNull(classification, "classification");
            outlinks = List.copyOf(Objects.requireNonNull(outlinks, "outlinks"));
            actionDrafts = List.copyOf(Objects.requireNonNull(actionDrafts, "action drafts"));
            providerEvidence = List.copyOf(Objects.requireNonNull(providerEvidence, "provider evidence"));
            requireNonNegative(workspaceGeneration, "workspace generation");
            requireNonNegative(documentGeneration, "document generation");
            requireNonNegative(semanticGeneration, "semantic generation");
            requireNonNegative(layoutGeneration, "layout generation");
            if (!certifiedRegion.domainId().equals(queryDomainId) || !certifiedRegion.contains(queryPoint)) {
                throw new IllegalArgumentException("certified region must contain the exact query point");
            }
            if (classification.status() == ClassificationStatus.ACTIONABLE
                    && outlinks.isEmpty() && actionDrafts.isEmpty()) {
                throw new IllegalArgumentException("actionable classification requires an outlink or action");
            }
        }
    }

    public record SnapshotIdentity(
            String workspaceFingerprint,
            long workspaceGeneration,
            String documentAddress,
            String documentHash,
            long documentGeneration,
            String semanticFingerprint,
            long semanticGeneration,
            String layoutFingerprint,
            long layoutGeneration
    ) {
        public SnapshotIdentity {
            requireText(workspaceFingerprint, "workspace fingerprint");
            requireNonNegative(workspaceGeneration, "workspace generation");
            requireText(documentAddress, "document address");
            requireText(documentHash, "document hash");
            requireNonNegative(documentGeneration, "document generation");
            requireText(semanticFingerprint, "semantic fingerprint");
            requireNonNegative(semanticGeneration, "semantic generation");
            requireText(layoutFingerprint, "layout fingerprint");
            requireNonNegative(layoutGeneration, "layout generation");
        }
    }

    public record CoverageRequest(
            String schema,
            String requestId,
            Scope scope,
            String selector,
            String profile,
            String layoutMatrix,
            long seed,
            long budget,
            String artifactDestination,
            SnapshotIdentity snapshot
    ) {
        public CoverageRequest {
            requireSchema(schema, COVERAGE_REQUEST_SCHEMA);
            requireText(requestId, "request id");
            Objects.requireNonNull(scope, "scope");
            requireText(selector, "selector");
            requireNamespaced(profile, "profile");
            requireNamespaced(layoutMatrix, "layout matrix");
            requireNonNegative(seed, "seed");
            if (budget <= 0) throw new IllegalArgumentException("budget must be positive");
            requireText(artifactDestination, "artifact destination");
            Objects.requireNonNull(snapshot, "snapshot");
        }
    }

    public record CoverageDimension(String id, long covered, long total) {
        public CoverageDimension {
            requireText(id, "dimension id");
            requireNonNegative(covered, "covered");
            requireNonNegative(total, "total");
            if (covered > total) throw new IllegalArgumentException("covered cannot exceed total");
        }
    }

    public record FileRow(String address, String sourceSet, String contentHash, FileState state, String diagnostic) {
        public FileRow {
            requireText(address, "file address");
            requireText(sourceSet, "source set");
            requireText(contentHash, "content hash");
            Objects.requireNonNull(state, "state");
            if (state != FileState.COVERED && blank(diagnostic)) {
                throw new IllegalArgumentException("non-covered file rows require a diagnostic");
            }
        }
    }

    public record CoverageReport(
            String schema,
            String requestId,
            String policy,
            String policyVersion,
            long seed,
            long budget,
            List<Long> sampleSequence,
            List<CoverageDimension> dimensions,
            List<FileRow> files,
            long semanticQueryCount,
            long certifiedRegionReuse,
            long cacheHits,
            long subdivisions,
            long fallbackProbes,
            List<String> artifactPaths
    ) {
        public CoverageReport {
            requireSchema(schema, COVERAGE_REPORT_SCHEMA);
            requireText(requestId, "request id");
            requireText(policy, "policy");
            requireText(policyVersion, "policy version");
            requireNonNegative(seed, "seed");
            if (budget <= 0) throw new IllegalArgumentException("budget must be positive");
            sampleSequence = List.copyOf(Objects.requireNonNull(sampleSequence, "sample sequence"));
            dimensions = List.copyOf(Objects.requireNonNull(dimensions, "dimensions"));
            files = List.copyOf(Objects.requireNonNull(files, "files"));
            requireNonNegative(semanticQueryCount, "semantic query count");
            requireNonNegative(certifiedRegionReuse, "certified region reuse");
            requireNonNegative(cacheHits, "cache hits");
            requireNonNegative(subdivisions, "subdivisions");
            requireNonNegative(fallbackProbes, "fallback probes");
            artifactPaths = immutableTextList(artifactPaths, "artifact paths");
        }
    }

    public record Rectangle(double left, double top, double right, double bottom) {
        public Rectangle {
            if (!Double.isFinite(left) || !Double.isFinite(top)
                    || !Double.isFinite(right) || !Double.isFinite(bottom)) {
                throw new IllegalArgumentException("rectangle coordinates must be finite");
            }
            if (right < left || bottom < top) throw new IllegalArgumentException("rectangle bounds must be ordered");
        }

        public boolean contains(Rectangle other) {
            return left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom;
        }
    }

    public record Camera(double x, double y, double zoom) {
        public Camera {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(zoom) || zoom <= 0) {
                throw new IllegalArgumentException("camera values must be finite and zoom positive");
            }
        }
    }

    public record FramingObservation(
            String schema,
            String paneId,
            String documentAddress,
            String documentHash,
            String destinationRegionId,
            String landmarkProjection,
            Rectangle documentBounds,
            Rectangle lineBounds,
            Rectangle destinationBounds,
            Rectangle viewportBounds,
            double inset,
            Camera previousCamera,
            Camera chosenCamera,
            Rectangle visibleIntersection,
            boolean documentLeftVisible,
            boolean lineLeftVisible,
            boolean documentTopVisible,
            boolean landmarkVisible,
            String clippingReason
    ) {
        public FramingObservation {
            requireSchema(schema, FRAMING_SCHEMA);
            requireText(paneId, "pane id");
            requireText(documentAddress, "document address");
            requireText(documentHash, "document hash");
            requireText(destinationRegionId, "destination region id");
            requireText(landmarkProjection, "landmark projection");
            Objects.requireNonNull(documentBounds, "document bounds");
            Objects.requireNonNull(lineBounds, "line bounds");
            Objects.requireNonNull(destinationBounds, "destination bounds");
            Objects.requireNonNull(viewportBounds, "viewport bounds");
            if (!Double.isFinite(inset) || inset < 0) throw new IllegalArgumentException("inset must be finite and non-negative");
            Objects.requireNonNull(previousCamera, "previous camera");
            Objects.requireNonNull(chosenCamera, "chosen camera");
            Objects.requireNonNull(visibleIntersection, "visible intersection");
            if (!landmarkVisible && blank(clippingReason)) {
                throw new IllegalArgumentException("invisible landmark requires a clipping reason");
            }
        }
    }

    public record Bundle(
            Domain domain,
            Region region,
            Projection projection,
            Outlink outlink,
            Probe probe,
            CoverageRequest coverageRequest,
            CoverageReport coverageReport,
            FramingObservation framingObservation
    ) {
        public Bundle {
            Objects.requireNonNull(domain, "domain");
            Objects.requireNonNull(region, "region");
            Objects.requireNonNull(projection, "projection");
            Objects.requireNonNull(outlink, "outlink");
            Objects.requireNonNull(probe, "probe");
            Objects.requireNonNull(coverageRequest, "coverage request");
            Objects.requireNonNull(coverageReport, "coverage report");
            Objects.requireNonNull(framingObservation, "framing observation");
        }
    }

    private static List<String> immutableTextList(List<String> values, String label) {
        List<String> copied = List.copyOf(Objects.requireNonNull(values, label));
        copied.forEach(value -> requireText(value, label));
        return copied;
    }

    private static void requireSchema(String actual, String expected) {
        if (!expected.equals(actual)) throw new IllegalArgumentException("Expected schema " + expected + ", got " + actual);
    }

    private static void requireNamespaced(String value, String label) {
        requireText(value, label);
        int colon = value.indexOf(':');
        if (colon <= 0 || colon == value.length() - 1) throw new IllegalArgumentException(label + " must be namespaced");
    }

    private static void requireText(String value, String label) {
        if (blank(value)) throw new IllegalArgumentException(label + " must not be blank");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static void requireNonNegative(long value, String label) {
        if (value < 0) throw new IllegalArgumentException(label + " must be non-negative");
    }
}
