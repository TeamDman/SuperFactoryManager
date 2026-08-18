package ca.teamdman.sfm.client.symbol;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Generation-tagged compact Java semantic map produced by the long-lived Rust
 * symbol worker. Byte intervals are authoritative source coordinates; canvas
 * projection remains owned by the Java editor.
 */
public final class SFMJavaInteractionMap {
    public static final String REQUEST_SCHEMA = "sfm.java-interaction-map-request/1";
    public static final String RESULT_SCHEMA = "sfm.java-interaction-map/1";
    public static final String REGION_SCHEMA = "sfm.region/1";
    public static final String DOMAIN_SCHEMA = "sfm.region-domain/1";
    public static final String PROJECTION_SCHEMA = "sfm.region-projection/1";
    public static final String OUTLINK_SCHEMA = "sfm.outlink/1";
    public static final long DEFAULT_MAX_REGIONS = 4_096L;
    public static final long DEFAULT_MAX_INVENTORY_FILES = 4_096L;
    public static final long DEFAULT_MAX_ENCODED_BYTES = 8L * 1024L * 1024L;
    public static final long MAX_REGIONS = 16_384L;
    public static final long MAX_INVENTORY_FILES = 16_384L;
    public static final long MAX_ENCODED_BYTES = 12L * 1024L * 1024L;

    private SFMJavaInteractionMap() {
    }

    public enum Outcome {
        SUCCESS("success"),
        NOT_MODIFIED("not-modified"),
        STALE_DOCUMENT("stale-document"),
        INVALID_REQUEST("invalid-request"),
        UNAVAILABLE("unavailable");

        private final String wireName;

        Outcome(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static Outcome fromWireName(String value) {
            for (Outcome outcome : values()) if (outcome.wireName.equals(value)) return outcome;
            throw new IllegalArgumentException("Unknown Java interaction-map outcome: " + value);
        }
    }

    public enum ClassificationStatus {
        ACTIONABLE("actionable"),
        EXPLICIT_NO_ACTION("explicit-no-action"),
        UNSUPPORTED("unsupported"),
        UNCLASSIFIED("unclassified");

        private final String wireName;

        ClassificationStatus(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static ClassificationStatus fromWireName(String value) {
            for (ClassificationStatus status : values()) if (status.wireName.equals(value)) return status;
            throw new IllegalArgumentException("Unknown Java interaction classification: " + value);
        }
    }

    public enum FileState {
        COVERED("covered"),
        PARTIAL("partial"),
        UNSUPPORTED_EXTENSION("unsupported-extension"),
        MISSING("missing"),
        STALE("stale"),
        PARSE_FAILED("parse-failed"),
        LAYOUT_FAILED("layout-failed"),
        INDEX_FAILED("index-failed"),
        TIMEOUT("timeout"),
        SKIPPED("skipped"),
        FAILED("failed");

        private final String wireName;

        FileState(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static FileState fromWireName(String value) {
            for (FileState state : values()) if (state.wireName.equals(value)) return state;
            throw new IllegalArgumentException("Unknown Java interaction file state: " + value);
        }
    }

    public enum ReciprocityStatus {
        VERIFIED("verified"),
        TYPED_EXCEPTION("typed-exception");

        private final String wireName;

        ReciprocityStatus(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static ReciprocityStatus fromWireName(String value) {
            for (ReciprocityStatus status : values()) if (status.wireName.equals(value)) return status;
            throw new IllegalArgumentException("Unknown Java interaction reciprocity status: " + value);
        }
    }

    public enum ExceptionEffect {
        APPROVED_EXCEPTION("approved-exception"),
        FAILS_STRICT_PROFILE("fails-strict-profile"),
        INFORMATIONAL("informational");

        private final String wireName;

        ExceptionEffect(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static ExceptionEffect fromWireName(String value) {
            for (ExceptionEffect effect : values()) if (effect.wireName.equals(value)) return effect;
            throw new IllegalArgumentException("Unknown Java interaction exception effect: " + value);
        }
    }

    public record Window(
            long regionOffset,
            long maximumRegions,
            long inventoryOffset,
            long maximumInventoryFiles,
            long maximumEncodedBytes
    ) {
        public Window {
            nonNegative(regionOffset, "regionOffset");
            positiveBounded(maximumRegions, MAX_REGIONS, "maximumRegions");
            nonNegative(inventoryOffset, "inventoryOffset");
            positiveBounded(maximumInventoryFiles, MAX_INVENTORY_FILES, "maximumInventoryFiles");
            positiveBounded(maximumEncodedBytes, MAX_ENCODED_BYTES, "maximumEncodedBytes");
        }

        public static Window defaults() {
            return new Window(0, DEFAULT_MAX_REGIONS, 0, DEFAULT_MAX_INVENTORY_FILES,
                    DEFAULT_MAX_ENCODED_BYTES);
        }
    }

    public record Request(
            String schema,
            long requestId,
            long requestGeneration,
            SFMDefinitionRequest.Workspace workspace,
            SFMDefinitionRequest.Document document,
            Window window,
            Optional<String> knownSemanticFingerprint
    ) {
        public Request {
            requireSchema(schema, REQUEST_SCHEMA, "interaction-map request");
            positive(requestId, "requestId");
            nonNegative(requestGeneration, "requestGeneration");
            Objects.requireNonNull(workspace, "workspace");
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(window, "window");
            knownSemanticFingerprint = Objects.requireNonNull(
                    knownSemanticFingerprint, "knownSemanticFingerprint");
            knownSemanticFingerprint.ifPresent(value -> taggedHash(value, "known semantic fingerprint", "blake3"));
            SFMDefinitionRequest.SourceRoot sourceRoot = workspace.sourceRoots().stream()
                    .filter(root -> root.id().equals(document.rootId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Interaction-map document references an unknown source root"));
            if (!sourceRoot.sourceSet().equals(document.sourceSet())) {
                throw new IllegalArgumentException("Interaction-map document source set disagrees with its root");
            }
        }

        public Request(
                long requestId,
                long requestGeneration,
                SFMDefinitionRequest.Workspace workspace,
                SFMDefinitionRequest.Document document
        ) {
            this(REQUEST_SCHEMA, requestId, requestGeneration, workspace, document,
                    Window.defaults(), Optional.empty());
        }
    }

    public record Axis(long startInclusive, long endExclusive) {
        public Axis {
            nonNegative(startInclusive, "axis start");
            if (endExclusive < startInclusive) {
                throw new IllegalArgumentException("Interaction region axis must not be reversed");
            }
        }

        public boolean contains(long value) {
            return value >= startInclusive && value < endExclusive;
        }
    }

    public record Domain(
            String schema,
            String id,
            String kind,
            long dimensions,
            List<String> coordinateKinds,
            String authority,
            String snapshotIdentity
    ) {
        public Domain {
            requireSchema(schema, DOMAIN_SCHEMA, "interaction domain");
            text(id, "domain id");
            text(kind, "domain kind");
            positive(dimensions, "domain dimensions");
            coordinateKinds = immutableText(coordinateKinds, "coordinate kind");
            if (coordinateKinds.size() != dimensions) {
                throw new IllegalArgumentException("Domain coordinate kinds must match dimensions");
            }
            text(authority, "domain authority");
            text(snapshotIdentity, "domain snapshot identity");
        }
    }

    public record Projection(
            String schema,
            String id,
            String fromDomainId,
            String toDomainId,
            String loss,
            String completeness,
            String transform,
            String fingerprint,
            String authority
    ) {
        public Projection {
            requireSchema(schema, PROJECTION_SCHEMA, "interaction projection");
            text(id, "projection id");
            text(fromDomainId, "projection source domain");
            text(toDomainId, "projection destination domain");
            text(loss, "projection loss");
            text(completeness, "projection completeness");
            text(transform, "projection transform");
            text(fingerprint, "projection fingerprint");
            text(authority, "projection authority");
        }
    }

    public record Region(
            String schema,
            String id,
            String domainId,
            String representation,
            List<Axis> bounds,
            String edgePolicy,
            String semanticKind,
            String provenance,
            List<String> projectionIds
    ) {
        public Region {
            requireSchema(schema, REGION_SCHEMA, "interaction region");
            text(id, "region id");
            text(domainId, "region domain id");
            text(representation, "region representation");
            bounds = List.copyOf(bounds);
            if (bounds.isEmpty()) throw new IllegalArgumentException("Interaction region bounds are empty");
            text(edgePolicy, "region edge policy");
            if (!edgePolicy.equals("half-open")) {
                throw new IllegalArgumentException("Interaction regions must use half-open bounds");
            }
            text(semanticKind, "region semantic kind");
            text(provenance, "region provenance");
            projectionIds = immutableText(projectionIds, "region projection id");
        }

        public long startByte() {
            return bounds.get(0).startInclusive();
        }

        public long endByte() {
            return bounds.get(0).endExclusive();
        }

        public boolean containsByte(long value) {
            return bounds.get(0).contains(value);
        }

        public long byteLength() {
            return endByte() - startByte();
        }
    }

    public record ActionDraft(String actionId, List<String> arguments) {
        public ActionDraft {
            text(actionId, "action id");
            arguments = immutableText(arguments, "action argument");
        }
    }

    public record Outlink(
            String schema,
            String id,
            String sourceRegionId,
            String destinationRegionId,
            Optional<String> destinationQuery,
            String relationKind,
            String intent,
            String providerId,
            long providerGeneration,
            String reason,
            String confidence,
            String completeness,
            String recommendedProjection,
            List<ActionDraft> actionDrafts,
            String provenance
    ) {
        public Outlink {
            requireSchema(schema, OUTLINK_SCHEMA, "interaction outlink");
            text(id, "outlink id");
            text(sourceRegionId, "outlink source region");
            text(destinationRegionId, "outlink destination region");
            destinationQuery = Objects.requireNonNull(destinationQuery, "destinationQuery");
            destinationQuery.ifPresent(value -> text(value, "outlink destination query"));
            text(relationKind, "outlink relation kind");
            text(intent, "outlink intent");
            text(providerId, "outlink provider id");
            nonNegative(providerGeneration, "outlink provider generation");
            text(reason, "outlink reason");
            text(confidence, "outlink confidence");
            text(completeness, "outlink completeness");
            text(recommendedProjection, "outlink recommended projection");
            actionDrafts = List.copyOf(actionDrafts);
            text(provenance, "outlink provenance");
        }
    }

    public record Classification(
            String regionId,
            ClassificationStatus status,
            Optional<String> reasonCode,
            List<String> navigationOutlinkIds,
            List<String> contextualActionIds
    ) {
        public Classification {
            text(regionId, "classification region id");
            Objects.requireNonNull(status, "status");
            reasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
            reasonCode.ifPresent(value -> text(value, "classification reason code"));
            navigationOutlinkIds = immutableText(navigationOutlinkIds, "navigation outlink id");
            contextualActionIds = immutableText(contextualActionIds, "contextual action id");
            if (status == ClassificationStatus.ACTIONABLE
                    && navigationOutlinkIds.isEmpty() && contextualActionIds.isEmpty()) {
                throw new IllegalArgumentException("Actionable classification has no outlink or action");
            }
        }
    }

    public record ExceptionWitness(
            String id,
            String regionId,
            String code,
            String reason,
            ExceptionEffect effect,
            String witness
    ) {
        public ExceptionWitness {
            text(id, "exception id");
            text(regionId, "exception region id");
            text(code, "exception code");
            text(reason, "exception reason");
            Objects.requireNonNull(effect, "effect");
            text(witness, "exception witness");
        }
    }

    public record Reciprocity(
            String definitionOutlinkId,
            String referenceOutlinkId,
            ReciprocityStatus status,
            Optional<String> exceptionCode,
            String witness
    ) {
        public Reciprocity {
            text(definitionOutlinkId, "definition outlink id");
            text(referenceOutlinkId, "reference outlink id");
            Objects.requireNonNull(status, "status");
            exceptionCode = Objects.requireNonNull(exceptionCode, "exceptionCode");
            exceptionCode.ifPresent(value -> text(value, "reciprocity exception code"));
            text(witness, "reciprocity witness");
        }
    }

    public record FileRow(
            String address,
            String resolverId,
            String rootId,
            String rootRelativePath,
            String reportPath,
            String sourceSet,
            Optional<String> contentHash,
            FileState state,
            Optional<String> diagnostic
    ) {
        public FileRow {
            text(address, "file address");
            text(resolverId, "file resolver id");
            text(rootId, "file root id");
            text(rootRelativePath, "file root-relative path");
            text(reportPath, "file report path");
            text(sourceSet, "file source set");
            contentHash = Objects.requireNonNull(contentHash, "contentHash");
            contentHash.ifPresent(value -> text(value, "file content hash"));
            Objects.requireNonNull(state, "state");
            diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
        }
    }

    public record Page(
            long regionOffset,
            long returnedRegions,
            long totalRegions,
            Optional<Long> nextRegionOffset,
            long inventoryOffset,
            long returnedInventoryFiles,
            long totalInventoryFiles,
            Optional<Long> nextInventoryOffset,
            long encodedBytes
    ) {
        public Page {
            nonNegative(regionOffset, "page region offset");
            nonNegative(returnedRegions, "page returned regions");
            nonNegative(totalRegions, "page total regions");
            nextRegionOffset = optionalNonNegative(nextRegionOffset, "next region offset");
            nonNegative(inventoryOffset, "page inventory offset");
            nonNegative(returnedInventoryFiles, "page returned inventory files");
            nonNegative(totalInventoryFiles, "page total inventory files");
            nextInventoryOffset = optionalNonNegative(nextInventoryOffset, "next inventory offset");
            nonNegative(encodedBytes, "page encoded bytes");
        }
    }

    public record Result(
            String schema,
            long requestId,
            long requestGeneration,
            long workspaceGeneration,
            String workspaceFingerprint,
            long documentGeneration,
            long semanticGeneration,
            String semanticFingerprint,
            Outcome outcome,
            SFMDefinitionResult.DocumentIdentity document,
            List<Domain> domains,
            List<Projection> projections,
            List<Region> regions,
            List<Classification> classifications,
            List<Outlink> outlinks,
            List<Reciprocity> reciprocity,
            List<ExceptionWitness> exceptions,
            List<FileRow> files,
            Page page,
            List<SFMDefinitionResult.Diagnostic> diagnostics
    ) {
        public Result {
            requireSchema(schema, RESULT_SCHEMA, "interaction-map result");
            positive(requestId, "requestId");
            nonNegative(requestGeneration, "requestGeneration");
            nonNegative(workspaceGeneration, "workspaceGeneration");
            text(workspaceFingerprint, "workspace fingerprint");
            nonNegative(documentGeneration, "documentGeneration");
            nonNegative(semanticGeneration, "semanticGeneration");
            taggedHash(semanticFingerprint, "semantic fingerprint", "blake3");
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(document, "document");
            domains = uniqueById(domains, Domain::id, "domain");
            projections = uniqueById(projections, Projection::id, "projection");
            regions = uniqueById(regions, Region::id, "region");
            classifications = uniqueById(classifications, Classification::regionId, "classification");
            outlinks = uniqueById(outlinks, Outlink::id, "outlink");
            reciprocity = List.copyOf(reciprocity);
            exceptions = uniqueById(exceptions, ExceptionWitness::id, "exception");
            files = List.copyOf(files);
            Objects.requireNonNull(page, "page");
            diagnostics = List.copyOf(diagnostics);
            validateReferences(regions, classifications, outlinks);
        }

        public boolean matches(Request request) {
            return requestId == request.requestId()
                    && requestGeneration == request.requestGeneration()
                    && workspaceGeneration == request.workspace().workspaceGeneration()
                    && workspaceFingerprint.equals(request.workspace().workspaceFingerprint())
                    && documentGeneration == request.requestGeneration()
                    && document.address().equals(request.document().address())
                    && document.contentHash().equals(request.document().contentHash());
        }

        public Optional<Region> mostSpecificRegionAtByte(long byteOffset) {
            Set<String> utf8Domains = domains.stream()
                    .filter(domain -> domain.kind().equals("utf8"))
                    .map(Domain::id)
                    .collect(java.util.stream.Collectors.toSet());
            return regions.stream()
                    .filter(region -> utf8Domains.contains(region.domainId()))
                    .filter(region -> region.containsByte(byteOffset))
                    .min(java.util.Comparator.comparingLong(Region::byteLength)
                            .thenComparing(Region::id));
        }

        public Optional<Classification> classification(String regionId) {
            return classifications.stream().filter(value -> value.regionId().equals(regionId)).findFirst();
        }

        public List<Outlink> navigationOutlinks(Classification classification) {
            Set<String> ids = Set.copyOf(classification.navigationOutlinkIds());
            return outlinks.stream().filter(value -> ids.contains(value.id())).toList();
        }
    }

    private static void validateReferences(
            List<Region> regions,
            List<Classification> classifications,
            List<Outlink> outlinks
    ) {
        Set<String> regionIds = regions.stream().map(Region::id).collect(java.util.stream.Collectors.toSet());
        Set<String> outlinkIds = outlinks.stream().map(Outlink::id).collect(java.util.stream.Collectors.toSet());
        for (Classification classification : classifications) {
            if (!regionIds.contains(classification.regionId())) {
                throw new IllegalArgumentException("Classification references an unknown region");
            }
            if (!outlinkIds.containsAll(classification.navigationOutlinkIds())) {
                throw new IllegalArgumentException("Classification references an unknown navigation outlink");
            }
        }
        // Paged maps deliberately retain stable outlink identities whose source
        // or destination region may be on another page or in another document.
        // Classifications remain page-local and are therefore checked above.
    }

    private static <T> List<T> uniqueById(
            List<T> values,
            java.util.function.Function<T, String> identity,
            String label
    ) {
        List<T> copy = List.copyOf(values);
        HashSet<String> ids = new HashSet<>();
        for (T value : copy) {
            if (!ids.add(identity.apply(value))) {
                throw new IllegalArgumentException("Duplicate interaction " + label + " identity");
            }
        }
        return copy;
    }

    private static List<String> immutableText(List<String> values, String label) {
        List<String> copy = List.copyOf(values);
        copy.forEach(value -> text(value, label));
        return copy;
    }

    private static Optional<Long> optionalNonNegative(Optional<Long> value, String label) {
        Optional<Long> copy = Objects.requireNonNull(value, label);
        copy.ifPresent(candidate -> nonNegative(candidate, label));
        return copy;
    }

    private static void requireSchema(String actual, String expected, String label) {
        if (!expected.equals(actual)) throw new IllegalArgumentException("Unsupported " + label + " schema");
    }

    private static void text(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
    }

    private static void taggedHash(String value, String label, String algorithm) {
        text(value, label);
        String prefix = algorithm + ":";
        if (!value.startsWith(prefix) || value.length() != prefix.length() + 64) {
            throw new IllegalArgumentException(label + " has an unsupported shape");
        }
        for (int index = prefix.length(); index < value.length(); index++) {
            char character = value.charAt(index);
            if (!((character >= '0' && character <= '9') || (character >= 'a' && character <= 'f'))) {
                throw new IllegalArgumentException(label + " must use lowercase hexadecimal");
            }
        }
    }

    private static void positive(long value, String label) {
        if (value <= 0) throw new IllegalArgumentException(label + " must be positive");
    }

    private static void nonNegative(long value, String label) {
        if (value < 0) throw new IllegalArgumentException(label + " must not be negative");
    }

    private static void positiveBounded(long value, long maximum, String label) {
        if (value <= 0 || value > maximum) {
            throw new IllegalArgumentException(label + " must be in 1..=" + maximum);
        }
    }
}
