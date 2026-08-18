package ca.teamdman.sfm.client.semantic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Java-canvas-owned, directly invokable spatial coverage service. Rust may
 * supply semantic maps to an oracle, but this service alone samples the
 * painted coordinate domain.
 */
public final class SFMSpatialCoverageService {
    @FunctionalInterface
    public interface Oracle {
        Observation probe(double canvasX, double canvasY);
    }

    public record Observation(
            SFMSpatialSemanticContract.Probe probe,
            boolean realGestureRouted,
            String providerBranch,
            boolean boundaryWitnessed,
            boolean reciprocityExpected,
            boolean reciprocal
    ) {
        public Observation {
            Objects.requireNonNull(probe, "probe");
            if (providerBranch == null || providerBranch.isBlank()) {
                throw new IllegalArgumentException("provider branch must not be blank");
            }
            if (!reciprocityExpected && reciprocal) {
                throw new IllegalArgumentException("reciprocal cannot be true when not expected");
            }
        }

        Observation at(List<Double> queryPoint) {
            var value = probe;
            return new Observation(new SFMSpatialSemanticContract.Probe(
                    value.schema(), value.queryDomainId(), queryPoint, value.requestedIntent(),
                    value.certifiedRegion(), value.classification(), value.outlinks(), value.actionDrafts(),
                    value.providerEvidence(), value.workspaceGeneration(), value.documentGeneration(),
                    value.semanticGeneration(), value.layoutGeneration()),
                    realGestureRouted, providerBranch, boundaryWitnessed, reciprocityExpected, reciprocal);
        }
    }

    public record Document(
            String address,
            String sourceSet,
            String contentHash,
            int canvasWidth,
            int canvasHeight,
            SFMSpatialSemanticContract.FileState initialState,
            String initialDiagnostic,
            Oracle oracle
    ) {
        public Document {
            requireText(address, "document address");
            requireText(sourceSet, "source set");
            requireText(contentHash, "content hash");
            Objects.requireNonNull(initialState, "initial state");
            if (initialState == SFMSpatialSemanticContract.FileState.COVERED
                    || initialState == SFMSpatialSemanticContract.FileState.PARTIAL) {
                if (canvasWidth <= 0 || canvasHeight <= 0) {
                    throw new IllegalArgumentException("analyzable document canvas must be positive");
                }
                Math.multiplyExact(canvasWidth, canvasHeight);
                Objects.requireNonNull(oracle, "oracle");
            } else if (initialDiagnostic == null || initialDiagnostic.isBlank()) {
                throw new IllegalArgumentException("terminal non-analysis states require a diagnostic");
            }
        }

        public static Document failed(
                String address,
                String sourceSet,
                String contentHash,
                SFMSpatialSemanticContract.FileState state,
                String diagnostic
        ) {
            if (state == SFMSpatialSemanticContract.FileState.COVERED
                    || state == SFMSpatialSemanticContract.FileState.PARTIAL) {
                throw new IllegalArgumentException("failed document requires a terminal failure state");
            }
            return new Document(address, sourceSet, contentHash, 0, 0, state, diagnostic, null);
        }
    }

    public record WorkspaceSnapshot(String fingerprint, List<Document> documents) {
        public WorkspaceSnapshot {
            requireText(fingerprint, "workspace fingerprint");
            documents = List.copyOf(Objects.requireNonNull(documents, "documents"));
            Set<String> addresses = new LinkedHashSet<>();
            for (Document document : documents) {
                if (!addresses.add(document.address())) {
                    throw new IllegalArgumentException("duplicate workspace document: " + document.address());
                }
            }
        }
    }

    public record Sample(
            String documentAddress,
            int x,
            int y,
            long sequence,
            boolean cacheHit,
            Observation observation
    ) {
        public Sample {
            requireText(documentAddress, "sample document address");
            if (x < 0 || y < 0 || sequence < 0) throw new IllegalArgumentException("sample coordinates/order invalid");
            Objects.requireNonNull(observation, "observation");
        }
    }

    public record CertifiedPartition(
            String documentAddress,
            String regionId,
            String signature,
            long reusedPoints,
            boolean validated
    ) {
        public CertifiedPartition {
            requireText(documentAddress, "partition document address");
            requireText(regionId, "region id");
            requireText(signature, "signature");
            if (reusedPoints < 0) throw new IllegalArgumentException("reuse count must be non-negative");
        }
    }

    public record ExceptionRow(String documentAddress, int x, int y, String kind, String detail) {
        public ExceptionRow {
            requireText(documentAddress, "exception document address");
            requireText(kind, "exception kind");
            requireText(detail, "exception detail");
        }
    }

    public record Run(
            SFMSpatialSemanticContract.CoverageRequest request,
            SFMSpatialSemanticContract.CoverageReport report,
            List<Sample> samples,
            List<CertifiedPartition> certifiedPartitions,
            List<ExceptionRow> exceptions,
            List<String> nextCandidates
    ) {
        public Run {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(report, "report");
            samples = List.copyOf(samples);
            certifiedPartitions = List.copyOf(certifiedPartitions);
            exceptions = List.copyOf(exceptions);
            nextCandidates = List.copyOf(nextCandidates);
        }
    }

    private record Certificate(
            SFMSpatialSemanticContract.Region region,
            String signature,
            Observation observation,
            boolean validated
    ) {
        private boolean contains(int x, int y) {
            return validated && region.contains(List.of(x + 0.5D, y + 0.5D));
        }
    }

    public Run run(
            SFMSpatialSemanticContract.CoverageRequest request,
            WorkspaceSnapshot workspace,
            SFMSpatialSamplingPolicies.Policy policy
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(policy, "policy");
        if (!request.snapshot().workspaceFingerprint().equals(workspace.fingerprint())) {
            throw new IllegalArgumentException("coverage request workspace fingerprint is stale");
        }
        ArrayList<Sample> samples = new ArrayList<>();
        ArrayList<CertifiedPartition> partitions = new ArrayList<>();
        ArrayList<ExceptionRow> exceptions = new ArrayList<>();
        ArrayList<String> nextCandidates = new ArrayList<>();
        ArrayList<SFMSpatialSemanticContract.FileRow> fileRows = new ArrayList<>();
        Counters counters = new Counters(request.budget());

        long sequence = 0;
        for (int documentIndex = 0; documentIndex < workspace.documents().size(); documentIndex++) {
            Document document = workspace.documents().get(documentIndex);
            if (document.initialState() != SFMSpatialSemanticContract.FileState.COVERED
                    && document.initialState() != SFMSpatialSemanticContract.FileState.PARTIAL) {
                fileRows.add(new SFMSpatialSemanticContract.FileRow(
                        document.address(), document.sourceSet(), document.contentHash(),
                        document.initialState(), document.initialDiagnostic()));
                continue;
            }
            List<SFMSpatialSamplingPolicies.Cell> selected = policy.select(
                    document.canvasWidth(), document.canvasHeight(),
                    request.seed() ^ Integer.toUnsignedLong(document.address().hashCode()), request.budget());
            Map<String, MutableCertificate> certificates = new LinkedHashMap<>();
            int unclassified = 0;
            int navigationMissing = 0;
            int processed = 0;
            for (SFMSpatialSamplingPolicies.Cell cell : selected) {
                Optional<MutableCertificate> cached = certificates.values().stream()
                        .filter(certificate -> certificate.certificate.contains(cell.x(), cell.y()))
                        .findFirst();
                Observation observation;
                boolean cacheHit = cached.isPresent();
                if (cacheHit) {
                    MutableCertificate value = cached.orElseThrow();
                    value.reuse++;
                    counters.certifiedReuse++;
                    counters.cacheHits++;
                    observation = value.certificate.observation.at(List.of(cell.x() + 0.5D, cell.y() + 0.5D));
                } else {
                    if (!counters.canQuery()) {
                        nextCandidates.add(document.address() + "#canvas(" + cell.x() + "," + cell.y() + ")");
                        continue;
                    }
                    observation = query(document, cell.x(), cell.y(), counters);
                    String signature = signature(observation);
                    Validation validation = validateCertificate(document, observation, signature, counters);
                    if (validation.validated) {
                        certificates.putIfAbsent(observation.probe().certifiedRegion().id(),
                                new MutableCertificate(new Certificate(
                                        observation.probe().certifiedRegion(), signature, observation, true)));
                    } else {
                        counters.subdivisions++;
                        exceptions.add(new ExceptionRow(document.address(), cell.x(), cell.y(),
                                "inconsistent-certified-region", validation.detail));
                    }
                }
                samples.add(new Sample(document.address(), cell.x(), cell.y(), sequence++, cacheHit, observation));
                processed++;
                if (observation.probe().classification().status()
                        == SFMSpatialSemanticContract.ClassificationStatus.UNCLASSIFIED) {
                    unclassified++;
                    exceptions.add(new ExceptionRow(document.address(), cell.x(), cell.y(),
                            "unclassified", "No provider made a deliberate claim"));
                }
                if (!hasNavigationOutlink(observation)) {
                    navigationMissing++;
                    exceptions.add(new ExceptionRow(document.address(), cell.x(), cell.y(),
                            "navigation-uncovered", "No navigate-intent outlink"));
                }
            }
            certificates.values().stream()
                    .sorted(Comparator.comparing(value -> value.certificate.region.id()))
                    .forEach(value -> partitions.add(new CertifiedPartition(
                            document.address(), value.certificate.region.id(), value.certificate.signature,
                            value.reuse, value.certificate.validated)));
            boolean budgetIncomplete = processed < selected.size();
            SFMSpatialSemanticContract.FileState state = unclassified == 0 && navigationMissing == 0 && !budgetIncomplete
                    ? SFMSpatialSemanticContract.FileState.COVERED
                    : SFMSpatialSemanticContract.FileState.PARTIAL;
            String diagnostic = state == SFMSpatialSemanticContract.FileState.COVERED ? null
                    : "unclassified=" + unclassified + ", navigation_uncovered=" + navigationMissing
                    + ", budget_incomplete=" + budgetIncomplete;
            fileRows.add(new SFMSpatialSemanticContract.FileRow(
                    document.address(), document.sourceSet(), document.contentHash(), state, diagnostic));
        }

        List<SFMSpatialSemanticContract.CoverageDimension> dimensions = dimensions(samples);
        var report = new SFMSpatialSemanticContract.CoverageReport(
                SFMSpatialSemanticContract.COVERAGE_REPORT_SCHEMA,
                request.requestId(), policy.id(), policy.version(), request.seed(), request.budget(),
                samples.stream().map(Sample::sequence).toList(), dimensions, fileRows,
                counters.semanticQueries, counters.certifiedReuse, counters.cacheHits,
                counters.subdivisions, counters.fallbackProbes,
                List.of("coverage-report.json", "coverage-map.json", "coverage-heatmap.png"));
        return new Run(request, report, samples, partitions, exceptions, nextCandidates);
    }

    private static Observation query(Document document, int x, int y, Counters counters) {
        counters.semanticQueries++;
        Observation value = Objects.requireNonNull(document.oracle().probe(x + 0.5D, y + 0.5D),
                "coverage oracle result");
        if (!value.probe().queryPoint().equals(List.of(x + 0.5D, y + 0.5D))) {
            throw new IllegalStateException("oracle did not retain the exact query point");
        }
        return value;
    }

    private static Validation validateCertificate(
            Document document,
            Observation origin,
            String expectedSignature,
            Counters counters
    ) {
        var region = origin.probe().certifiedRegion();
        if (region.bounds().size() != 2) return new Validation(false, "coverage requires a 2D certified region");
        var x = region.bounds().get(0);
        var y = region.bounds().get(1);
        int minX = Math.max(0, (int) Math.floor(x.startInclusive()));
        int maxX = Math.min(document.canvasWidth() - 1, (int) Math.ceil(x.endExclusive()) - 1);
        int minY = Math.max(0, (int) Math.floor(y.startInclusive()));
        int maxY = Math.min(document.canvasHeight() - 1, (int) Math.ceil(y.endExclusive()) - 1);
        LinkedHashSet<SFMSpatialSamplingPolicies.Cell> witnesses = new LinkedHashSet<>(List.of(
                new SFMSpatialSamplingPolicies.Cell(minX, minY),
                new SFMSpatialSamplingPolicies.Cell(maxX, minY),
                new SFMSpatialSamplingPolicies.Cell(minX, maxY),
                new SFMSpatialSamplingPolicies.Cell(maxX, maxY),
                new SFMSpatialSamplingPolicies.Cell((minX + maxX) / 2, (minY + maxY) / 2)
        ));
        for (SFMSpatialSamplingPolicies.Cell witness : witnesses) {
            if (!counters.canQuery()) return new Validation(false, "query budget exhausted during certification");
            Observation observed = query(document, witness.x(), witness.y(), counters);
            if (!expectedSignature.equals(signature(observed))) {
                return new Validation(false, "witness " + witness + " disagreed with provider certificate");
            }
        }
        // Just-outside points are probes for branch/boundary evidence, not a
        // requirement that the adjacent region differ.
        for (SFMSpatialSamplingPolicies.Cell outside : List.of(
                new SFMSpatialSamplingPolicies.Cell(Math.max(0, minX - 1), minY),
                new SFMSpatialSamplingPolicies.Cell(Math.min(document.canvasWidth() - 1, maxX + 1), maxY))) {
            if (!region.contains(List.of(outside.x() + 0.5D, outside.y() + 0.5D)) && counters.canQuery()) {
                query(document, outside.x(), outside.y(), counters);
                counters.fallbackProbes++;
            }
        }
        return new Validation(true, "validated");
    }

    private static List<SFMSpatialSemanticContract.CoverageDimension> dimensions(List<Sample> samples) {
        long total = samples.size();
        long classification = samples.stream().filter(sample -> sample.observation().probe().classification().status()
                != SFMSpatialSemanticContract.ClassificationStatus.UNCLASSIFIED).count();
        long navigation = samples.stream().filter(sample -> hasNavigationOutlink(sample.observation())).count();
        long actionOnly = samples.stream().filter(sample -> !hasNavigationOutlink(sample.observation())
                && (!sample.observation().probe().actionDrafts().isEmpty()
                || !sample.observation().probe().outlinks().isEmpty())).count();
        long realGesture = samples.stream().filter(sample -> sample.observation().realGestureRouted()).count();
        long branches = samples.stream().map(sample -> sample.observation().providerBranch()).distinct().count();
        long boundaries = samples.stream().filter(sample -> sample.observation().boundaryWitnessed()).count();
        long reciprocalTotal = samples.stream().filter(sample -> sample.observation().reciprocityExpected()).count();
        long reciprocal = samples.stream().filter(sample -> sample.observation().reciprocityExpected()
                && sample.observation().reciprocal()).count();
        return List.of(
                new SFMSpatialSemanticContract.CoverageDimension("classification", classification, total),
                new SFMSpatialSemanticContract.CoverageDimension("navigation", navigation, total),
                new SFMSpatialSemanticContract.CoverageDimension("action-only", actionOnly, total),
                new SFMSpatialSemanticContract.CoverageDimension("real-gesture", realGesture, total),
                new SFMSpatialSemanticContract.CoverageDimension("provider-branch", branches, Math.max(branches, 1)),
                new SFMSpatialSemanticContract.CoverageDimension("boundary", boundaries, total),
                new SFMSpatialSemanticContract.CoverageDimension("reciprocity", reciprocal, reciprocalTotal)
        );
    }

    private static boolean hasNavigationOutlink(Observation observation) {
        return observation.probe().outlinks().stream().anyMatch(outlink ->
                outlink.intent() == SFMSpatialSemanticContract.Intent.NAVIGATE);
    }

    private static String signature(Observation observation) {
        var probe = observation.probe();
        return probe.requestedIntent().wireName() + "|" + probe.classification().status().wireName()
                + "|" + Objects.toString(probe.classification().reasonCode(), "")
                + "|" + probe.outlinks().stream().map(SFMSpatialSemanticContract.Outlink::id).toList()
                + "|" + probe.actionDrafts().stream().map(SFMSpatialSemanticContract.ActionDraft::actionId).toList()
                + "|" + observation.providerBranch() + "|" + observation.realGestureRouted()
                + "|" + observation.reciprocityExpected() + ":" + observation.reciprocal();
    }

    private record Validation(boolean validated, String detail) {
    }

    private static final class MutableCertificate {
        private final Certificate certificate;
        private long reuse;

        private MutableCertificate(Certificate certificate) {
            this.certificate = certificate;
        }
    }

    private static final class Counters {
        private final long budget;
        private long semanticQueries;
        private long certifiedReuse;
        private long cacheHits;
        private long subdivisions;
        private long fallbackProbes;

        private Counters(long budget) {
            this.budget = budget;
        }

        private boolean canQuery() {
            return semanticQueries < budget;
        }
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
    }
}
