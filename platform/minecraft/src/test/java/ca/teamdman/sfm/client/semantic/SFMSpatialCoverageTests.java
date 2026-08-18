package ca.teamdman.sfm.client.semantic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMSpatialCoverageTests {
    @Test
    void allSamplingPoliciesAreUniqueBoundedAndSeedDeterministic() {
        for (var policy : List.of(
                SFMSpatialSamplingPolicies.exhaustive(),
                SFMSpatialSamplingPolicies.uniformRandom(),
                SFMSpatialSamplingPolicies.stratified(),
                SFMSpatialSamplingPolicies.maximin(),
                SFMSpatialSamplingPolicies.adaptiveFailureSeeking())) {
            List<SFMSpatialSamplingPolicies.Cell> first = policy.select(17, 11, 42, 37);
            assertEquals(first, policy.select(17, 11, 42, 37), policy.id());
            assertEquals(37, first.size(), policy.id());
            assertEquals(first.size(), new HashSet<>(first).size(), policy.id());
            assertTrue(first.stream().allMatch(cell -> cell.x() < 17 && cell.y() < 11), policy.id());
        }
        assertNotEquals(
                SFMSpatialSamplingPolicies.uniformRandom().select(17, 11, 42, 37),
                SFMSpatialSamplingPolicies.uniformRandom().select(17, 11, 43, 37));
    }

    @Test
    void certifiedRegionOptimizationAgreesWithExhaustiveTruthAndReducesQueries() {
        AtomicInteger oracleQueries = new AtomicInteger();
        var document = document("file:///A.java", 10, 10, (x, y) -> {
            oracleQueries.incrementAndGet();
            return observation(x, y, region("all", 0, 10, 0, 10), true, true);
        });
        var run = service().run(request(100), workspace(document), SFMSpatialSamplingPolicies.exhaustive());

        assertEquals(100, run.samples().size());
        assertTrue(oracleQueries.get() < 20, "certified region should avoid per-cell semantic queries");
        assertEquals(100, dimension(run, "classification").covered());
        assertEquals(100, dimension(run, "navigation").covered());
        assertEquals(100, dimension(run, "real-gesture").covered());
        assertTrue(run.report().certifiedRegionReuse() > 80);
        assertTrue(run.certifiedPartitions().stream().allMatch(SFMSpatialCoverageService.CertifiedPartition::validated));
        assertTrue(run.exceptions().isEmpty());
    }

    @Test
    void contradictoryCertificateSubdividesAndExposesUncoveredCells() {
        var document = document("file:///Split.java", 8, 4, (x, y) -> {
            boolean left = x < 4;
            return observation(x, y, region("claimed-whole", 0, 8, 0, 4), left, left);
        });
        var run = service().run(request(500), workspace(document), SFMSpatialSamplingPolicies.exhaustive());

        assertTrue(run.report().subdivisions() > 0);
        assertTrue(run.exceptions().stream().anyMatch(row -> row.kind().equals("inconsistent-certified-region")));
        assertTrue(run.exceptions().stream().anyMatch(row -> row.kind().equals("navigation-uncovered")));
        assertEquals(SFMSpatialSemanticContract.FileState.PARTIAL, run.report().files().get(0).state());
    }

    @Test
    void witnessedWhitespaceIsExcludedFromStrictNavigationWithoutInflatingCoverage() {
        var document = document("file:///Whitespace.java", 3, 2,
                (x, y) -> explicitNoAction(x, y, region("whitespace", 0, 3, 0, 2), "whitespace"));
        var run = service().run(request(100), workspace(document), SFMSpatialSamplingPolicies.exhaustive());

        assertEquals(6, dimension(run, "classification").covered());
        assertEquals(0, dimension(run, "navigation").covered());
        assertEquals(0, dimension(run, "navigation").total());
        assertTrue(run.exceptions().stream().noneMatch(row -> row.kind().equals("navigation-uncovered")));
        assertEquals(SFMSpatialSemanticContract.FileState.COVERED, run.report().files().get(0).state());
    }

    @Test
    void workspaceInventoryRetainsFailedFilesAndArtifactsAreAdjacent(@TempDir Path directory) throws Exception {
        var covered = document("file:///A.java", 2, 2,
                (x, y) -> observation(x, y, region("all", 0, 2, 0, 2), true, true));
        var failed = SFMSpatialCoverageService.Document.failed(
                "file:///Broken.java", "gametest", "sha256:broken",
                SFMSpatialSemanticContract.FileState.PARSE_FAILED, "deliberate malformed fixture");
        var run = service().run(request(100), workspace(covered, failed),
                SFMSpatialSamplingPolicies.exhaustive());

        assertEquals(2, run.report().files().size());
        assertEquals(SFMSpatialSemanticContract.FileState.PARSE_FAILED, run.report().files().get(1).state());
        var written = SFMSpatialCoverageArtifacts.write(run, directory);
        assertTrue(Files.readString(written.report()).contains("\"parse-failed\""));
        assertTrue(Files.readString(written.map()).contains("\"certified_partitions\""));
        assertTrue(Files.readString(written.map()).contains("\"next_candidates\""));
        assertEquals(2, ImageIO.read(written.heatmap().toFile()).getWidth());
        assertFalse(Files.exists(directory.resolve("coverage-report.json.tmp")));
    }

    private static SFMSpatialSemanticContract.CoverageDimension dimension(
            SFMSpatialCoverageService.Run run, String id
    ) {
        return run.report().dimensions().stream().filter(dimension -> dimension.id().equals(id))
                .findFirst().orElseThrow();
    }

    private static SFMSpatialCoverageService service() {
        return new SFMSpatialCoverageService();
    }

    private static SFMSpatialCoverageService.WorkspaceSnapshot workspace(
            SFMSpatialCoverageService.Document... documents
    ) {
        return new SFMSpatialCoverageService.WorkspaceSnapshot("blake3:workspace", List.of(documents));
    }

    private static SFMSpatialCoverageService.Document document(
            String address, int width, int height, SFMSpatialCoverageService.Oracle oracle
    ) {
        return new SFMSpatialCoverageService.Document(
                address, "main", "sha256:" + address.hashCode(), width, height,
                SFMSpatialSemanticContract.FileState.COVERED, null, oracle);
    }

    private static SFMSpatialSemanticContract.CoverageRequest request(long budget) {
        return new SFMSpatialSemanticContract.CoverageRequest(
                SFMSpatialSemanticContract.COVERAGE_REQUEST_SCHEMA,
                "coverage:test", SFMSpatialSemanticContract.Scope.WORKSPACE, "focused",
                "sfm:strict_java_navigation", "sfm:auto_1_through_8", 7, budget, "auto",
                new SFMSpatialSemanticContract.SnapshotIdentity(
                        "blake3:workspace", 1, "file:///A.java", "sha256:a", 2,
                        "blake3:semantic", 3, "blake3:layout", 4));
    }

    private static SFMSpatialCoverageService.Observation observation(
            double x,
            double y,
            SFMSpatialSemanticContract.Region certified,
            boolean navigation,
            boolean gesture
    ) {
        List<SFMSpatialSemanticContract.Outlink> outlinks = navigation
                ? List.of(new SFMSpatialSemanticContract.Outlink(
                SFMSpatialSemanticContract.OUTLINK_SCHEMA, "outlink:" + certified.id(), certified.id(), null,
                "java:target", "definition", SFMSpatialSemanticContract.Intent.NAVIGATE, "sfm:test", 1,
                "fixture", SFMSpatialSemanticContract.Confidence.RESOLVED,
                SFMSpatialSemanticContract.Completeness.COMPLETE, "start", List.of(), "sfm:test"))
                : List.of();
        var status = navigation ? SFMSpatialSemanticContract.ClassificationStatus.ACTIONABLE
                : SFMSpatialSemanticContract.ClassificationStatus.EXPLICIT_NO_ACTION;
        var probe = new SFMSpatialSemanticContract.Probe(
                SFMSpatialSemanticContract.PROBE_SCHEMA, "canvas:test", List.of(x, y),
                SFMSpatialSemanticContract.Intent.NAVIGATE, certified,
                new SFMSpatialSemanticContract.Classification(status, navigation ? null : "fixture-no-navigation"),
                outlinks, List.of(), List.of(new SFMSpatialSemanticContract.ProviderEvidence(
                "sfm:test", 1, navigation ? "matched" : "explicit-no-action", null)),
                1, 2, 3, 4);
        return new SFMSpatialCoverageService.Observation(
                probe, gesture, navigation ? "resolved" : "explicit-no-action", true, navigation, navigation);
    }

    private static SFMSpatialCoverageService.Observation explicitNoAction(
            double x,
            double y,
            SFMSpatialSemanticContract.Region certified,
            String reason
    ) {
        var probe = new SFMSpatialSemanticContract.Probe(
                SFMSpatialSemanticContract.PROBE_SCHEMA, "canvas:test", List.of(x, y),
                SFMSpatialSemanticContract.Intent.NAVIGATE, certified,
                new SFMSpatialSemanticContract.Classification(
                        SFMSpatialSemanticContract.ClassificationStatus.EXPLICIT_NO_ACTION, reason),
                List.of(), List.of(), List.of(new SFMSpatialSemanticContract.ProviderEvidence(
                "sfm:test", 1, "explicit-no-action", null)), 1, 2, 3, 4);
        return new SFMSpatialCoverageService.Observation(
                probe, true, "explicit-no-action", true, false, false);
    }

    private static SFMSpatialSemanticContract.Region region(
            String id, double left, double right, double top, double bottom
    ) {
        return new SFMSpatialSemanticContract.Region(
                SFMSpatialSemanticContract.REGION_SCHEMA, id, "canvas:test",
                SFMSpatialSemanticContract.Representation.RECTANGLE,
                List.of(new SFMSpatialSemanticContract.AxisBound(left, right),
                        new SFMSpatialSemanticContract.AxisBound(top, bottom)),
                "half-open", "fixture", "sfm:test", List.of());
    }
}
