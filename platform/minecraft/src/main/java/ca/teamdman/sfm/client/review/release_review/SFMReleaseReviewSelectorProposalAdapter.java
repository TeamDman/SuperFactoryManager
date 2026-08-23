package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel;
import ca.teamdman.sfm.client.symbol.SFMDefinitionRequest;
import ca.teamdman.sfm.client.symbol.SFMJavaInteractionMap;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Pure RCS-2 proposal boundary. Literal evidence is always retained; semantic
 * providers may add only explicitly witnessed regions and fail independently.
 */
public final class SFMReleaseReviewSelectorProposalAdapter {
    public static final String LITERAL_PROVIDER = "sfm:release-review/literal";
    public static final String MULTI_REGION_PROVIDER = "sfm:release-review/explicit-multi-selection";
    public static final String JAVA_INTERACTION_MAP_PROVIDER = "sfm:java-interaction-map";

    private SFMReleaseReviewSelectorProposalAdapter() {
    }

    /** Exact source intervals supplied by one semantic authority. */
    public record SemanticEvidence(
            SFMReleaseReviewV1.SelectorKind kind,
            String semanticKey,
            List<SFMReleaseReviewV1.AddressedRange> ranges,
            List<SFMReleaseReviewV1.Evidence> provenance,
            SFMReleaseReviewV1.ProposalConfidence confidence,
            String projectionFingerprint,
            String sourceSnapshotId,
            List<String> diagnostics
    ) {
        public SemanticEvidence {
            Objects.requireNonNull(kind, "kind");
            if (kind == SFMReleaseReviewV1.SelectorKind.LITERAL) {
                throw new IllegalArgumentException("Semantic providers must not replace the literal proposal");
            }
            semanticKey = requireText(semanticKey, "semantic key");
            ranges = List.copyOf(Objects.requireNonNull(ranges, "ranges"));
            if (ranges.isEmpty()) throw new IllegalArgumentException("Semantic evidence requires a range");
            if (kind == SFMReleaseReviewV1.SelectorKind.BOUNDED_MULTI_REGION && ranges.size() < 2) {
                throw new IllegalArgumentException("Bounded multi-region evidence requires at least two ranges");
            }
            provenance = List.copyOf(Objects.requireNonNull(provenance, "provenance"));
            Objects.requireNonNull(confidence, "confidence");
            if (confidence == SFMReleaseReviewV1.ProposalConfidence.UNAVAILABLE) {
                throw new IllegalArgumentException("Unavailable evidence is a diagnostic, not a selector proposal");
            }
            projectionFingerprint = requireSha256(projectionFingerprint, "projection fingerprint");
            sourceSnapshotId = requireText(sourceSnapshotId, "source snapshot id");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }

    public interface SemanticEvidenceProvider {
        String id();

        List<SemanticEvidence> propose(
                SFMReleaseReviewV1.PinnedSelection selection,
                SFMReleaseReviewSelectionAdapter.DocumentResolver documents
        ) throws Exception;
    }

    public record ProviderDiagnostic(String code, String providerId, String message) {
        public ProviderDiagnostic {
            code = requireText(code, "diagnostic code");
            providerId = requireText(providerId, "diagnostic provider id");
            message = requireText(message, "diagnostic message");
        }
    }

    public record ProposalBatch(
            List<SFMReleaseReviewV1.SelectorProposal> proposals,
            List<ProviderDiagnostic> diagnostics
    ) {
        public ProposalBatch {
            proposals = List.copyOf(Objects.requireNonNull(proposals, "proposals"));
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
            if (proposals.isEmpty() || proposals.get(0).kind() != SFMReleaseReviewV1.SelectorKind.LITERAL) {
                throw new IllegalArgumentException("A proposal batch must begin with the exact literal proposal");
            }
        }
    }

    public static ProposalBatch propose(
            SFMReleaseReviewSelectionAdapter.AdaptedSelection adapted,
            SFMReleaseReviewSelectionAdapter.DocumentResolver documents,
            List<? extends SemanticEvidenceProvider> providers
    ) {
        Objects.requireNonNull(adapted, "adapted");
        Objects.requireNonNull(documents, "documents");
        Objects.requireNonNull(providers, "providers");
        SFMReleaseReviewV1.PinnedSelection pinned = adapted.pinnedSelection();

        ArrayList<SFMReleaseReviewV1.SelectorProposal> proposals = new ArrayList<>();
        proposals.add(literalProposal(pinned, adapted.literalRule()));
        if (pinned.ranges().size() > 1) {
            proposals.add(explicitMultiRegionProposal(pinned, adapted.literalRule()));
        }

        ArrayList<ProviderDiagnostic> diagnostics = new ArrayList<>();
        ArrayList<SemanticEvidenceProvider> orderedProviders = new ArrayList<>();
        HashSet<String> providerIds = new HashSet<>();
        for (SemanticEvidenceProvider provider : providers) {
            Objects.requireNonNull(provider, "provider");
            String id = requireText(provider.id(), "semantic provider id");
            if (!providerIds.add(id)) throw new IllegalArgumentException("Duplicate semantic provider " + id);
            orderedProviders.add(provider);
        }
        orderedProviders.sort(Comparator.comparing(SemanticEvidenceProvider::id));

        LinkedHashMap<String, SFMReleaseReviewV1.SelectorProposal> semantic = new LinkedHashMap<>();
        for (SemanticEvidenceProvider provider : orderedProviders) {
            String providerId = provider.id();
            try {
                List<SemanticEvidence> evidence = List.copyOf(provider.propose(pinned, documents));
                ArrayList<SFMReleaseReviewV1.SelectorProposal> converted = new ArrayList<>();
                for (SemanticEvidence item : evidence) {
                    converted.add(semanticProposal(providerId, pinned, item, documents));
                }
                converted.sort(Comparator
                        .comparing((SFMReleaseReviewV1.SelectorProposal value) -> value.kind().ordinal())
                        .thenComparing(value -> value.semanticKey().orElse(""))
                        .thenComparing(SFMReleaseReviewV1.SelectorProposal::id));
                for (SFMReleaseReviewV1.SelectorProposal proposal : converted) {
                    semantic.putIfAbsent(semanticIdentity(proposal), proposal);
                }
            } catch (Exception exception) {
                String message = exception.getMessage() == null || exception.getMessage().isBlank()
                        ? exception.getClass().getSimpleName()
                        : exception.getClass().getSimpleName() + ": " + exception.getMessage();
                diagnostics.add(new ProviderDiagnostic(
                        "review.semantic-provider-failed",
                        providerId,
                        message
                ));
            }
        }
        proposals.addAll(semantic.values());
        return new ProposalBatch(proposals, diagnostics);
    }

    private static SFMReleaseReviewV1.SelectorProposal literalProposal(
            SFMReleaseReviewV1.PinnedSelection pinned,
            SFMReviewSessionV1.SelectionRule rule
    ) {
        String fingerprint = fingerprint("literal", canonicalPinned(pinned));
        return new SFMReleaseReviewV1.SelectorProposal(
                "selector-proposal:literal:" + fingerprint,
                SFMReleaseReviewV1.SelectorKind.LITERAL,
                rule,
                pinned,
                Optional.empty(),
                Optional.empty(),
                List.of(),
                SFMReleaseReviewV1.ProposalConfidence.EXACT,
                fingerprint,
                pinned.selectionRevision(),
                List.of()
        );
    }

    private static SFMReleaseReviewV1.SelectorProposal explicitMultiRegionProposal(
            SFMReleaseReviewV1.PinnedSelection pinned,
            SFMReviewSessionV1.SelectionRule rule
    ) {
        String fingerprint = fingerprint("multi-region", canonicalPinned(pinned));
        return new SFMReleaseReviewV1.SelectorProposal(
                "selector-proposal:bounded-multi-region:" + fingerprint,
                SFMReleaseReviewV1.SelectorKind.BOUNDED_MULTI_REGION,
                rule,
                pinned,
                Optional.of(MULTI_REGION_PROVIDER),
                Optional.of("explicit-selection:" + fingerprint),
                List.of(
                        new SFMReleaseReviewV1.Evidence("range-count", Integer.toString(pinned.ranges().size())),
                        new SFMReleaseReviewV1.Evidence("selection-revision", pinned.selectionRevision())
                ),
                SFMReleaseReviewV1.ProposalConfidence.EXACT,
                fingerprint,
                pinned.selectionRevision(),
                List.of("review.explicit-multi-region: every range came from the captured selection")
        );
    }

    private static SFMReleaseReviewV1.SelectorProposal semanticProposal(
            String providerId,
            SFMReleaseReviewV1.PinnedSelection literalWitness,
            SemanticEvidence evidence,
            SFMReleaseReviewSelectionAdapter.DocumentResolver documents
    ) {
        ArrayList<SFMReleaseReviewV1.PinnedSelectionRange> ranges = new ArrayList<>();
        for (SFMReleaseReviewV1.AddressedRange range : evidence.ranges()) {
            SFMReleaseReviewSelectionAdapter.DocumentWitness document = documents
                    .resolve(range.documentRevisionId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Semantic evidence references unavailable document " + range.documentRevisionId()
                    ));
            if (!document.selectionRevision().equals(literalWitness.selectionRevision())) {
                throw new IllegalArgumentException("Semantic evidence was resolved at a stale selection revision");
            }
            SFMTextDocumentRange.positionAtByteOffset(document.text(), range.startByte());
            SFMTextDocumentRange.positionAtByteOffset(document.text(), range.endByte());
            ranges.add(new SFMReleaseReviewV1.PinnedSelectionRange(
                    SFMReleaseReviewV1.SelectionDirection.FORWARD,
                    range.documentRevisionId(),
                    document.sha256(),
                    range.startByte(),
                    range.endByte()
            ));
        }
        SFMReleaseReviewV1.PinnedSelection semanticSelection = new SFMReleaseReviewV1.PinnedSelection(
                literalWitness.selectionRevision(),
                literalWitness.sourceExpression(),
                0,
                ranges
        );
        SFMReviewSessionV1.SelectionRule rule =
                SFMReleaseReviewSelectionAdapter.literalRule(semanticSelection, documents);
        String identity = providerId + "\n" + evidence.kind() + "\n" + evidence.semanticKey()
                + "\n" + canonicalRanges(evidence.ranges()) + "\n" + evidence.projectionFingerprint();
        return new SFMReleaseReviewV1.SelectorProposal(
                "selector-proposal:semantic:" + fingerprint("proposal", identity),
                evidence.kind(),
                rule,
                literalWitness,
                Optional.of(providerId),
                Optional.of(evidence.semanticKey()),
                evidence.provenance(),
                evidence.confidence(),
                evidence.projectionFingerprint(),
                evidence.sourceSnapshotId(),
                evidence.diagnostics()
        );
    }

    private static String semanticIdentity(SFMReleaseReviewV1.SelectorProposal proposal) {
        return proposal.semanticProvider().orElse("") + "\u0000" + proposal.kind() + "\u0000"
                + proposal.semanticKey().orElse("") + "\u0000" + proposal.projectionFingerprint();
    }

    /**
     * Conservative provider backed only by explicit Rust interaction regions
     * and outlinks. It does not call the lexical nearest-token fallback.
     */
    public static final class JavaInteractionMapProvider implements SemanticEvidenceProvider {
        private static final Set<String> DECLARATION_KINDS = Set.of(
                "java-import",
                "java-class-declaration",
                "java-interface-declaration",
                "java-enum-declaration",
                "java-record-declaration",
                "java-field-declaration",
                "java-local-declaration",
                "java-variable-declarator",
                "java-method-declaration",
                "java-constructor-declaration",
                "java-parameter-declaration"
        );

        private final Map<String, SFMJavaInteractionMap.Result> mapsByDocumentRevision;

        public JavaInteractionMapProvider(Map<String, SFMJavaInteractionMap.Result> mapsByDocumentRevision) {
            Objects.requireNonNull(mapsByDocumentRevision, "mapsByDocumentRevision");
            TreeMap<String, SFMJavaInteractionMap.Result> copy = new TreeMap<>();
            mapsByDocumentRevision.forEach((key, value) -> copy.put(
                    requireText(key, "interaction-map document revision"),
                    Objects.requireNonNull(value, "interaction map")
            ));
            this.mapsByDocumentRevision = Map.copyOf(copy);
        }

        @Override
        public String id() {
            return JAVA_INTERACTION_MAP_PROVIDER;
        }

        @Override
        public List<SemanticEvidence> propose(
                SFMReleaseReviewV1.PinnedSelection selection,
                SFMReleaseReviewSelectionAdapter.DocumentResolver documents
        ) {
            LinkedHashMap<String, SemanticEvidence> answer = new LinkedHashMap<>();
            for (SFMReleaseReviewV1.PinnedSelectionRange selected : selection.ranges()) {
                SFMJavaInteractionMap.Result map = mapsByDocumentRevision.get(selected.documentRevisionId());
                if (map == null) continue;
                SFMReleaseReviewSelectionAdapter.DocumentWitness document = documents
                        .resolve(selected.documentRevisionId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Interaction map document is unavailable: " + selected.documentRevisionId()
                        ));
                validatePublication(document, map);
                collect(selected, map, answer);
            }
            return answer.values().stream()
                    .sorted(Comparator.comparing((SemanticEvidence value) -> value.kind().ordinal())
                            .thenComparing(SemanticEvidence::semanticKey)
                            .thenComparing(value -> canonicalRanges(value.ranges())))
                    .toList();
        }

        private static void collect(
                SFMReleaseReviewV1.PinnedSelectionRange selected,
                SFMJavaInteractionMap.Result map,
                Map<String, SemanticEvidence> output
        ) {
            Set<String> utf8Domains = map.domains().stream()
                    .filter(domain -> domain.kind().equals("utf8"))
                    .map(SFMJavaInteractionMap.Domain::id)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            Map<String, SFMJavaInteractionMap.Region> regions = new HashMap<>();
            map.regions().stream()
                    .filter(region -> utf8Domains.contains(region.domainId()))
                    .forEach(region -> regions.put(region.id(), region));
            Map<String, List<SFMJavaInteractionMap.Outlink>> outlinksBySource = new HashMap<>();
            for (SFMJavaInteractionMap.Outlink outlink : map.outlinks()) {
                outlinksBySource.computeIfAbsent(outlink.sourceRegionId(), ignored -> new ArrayList<>()).add(outlink);
            }

            List<SFMJavaInteractionMap.Region> enclosing = regions.values().stream()
                    .filter(region -> encloses(region, selected.startByte(), selected.endByte()))
                    .sorted(Comparator.comparingLong(SFMJavaInteractionMap.Region::byteLength)
                            .thenComparing(SFMJavaInteractionMap.Region::id))
                    .toList();

            LinkedHashSet<SFMJavaInteractionMap.Region> declarations = new LinkedHashSet<>();
            enclosing.stream().filter(region -> DECLARATION_KINDS.contains(region.semanticKind()))
                    .forEach(declarations::add);
            // Follow only explicit containment edges; no nearest-token ancestry is inferred.
            ArrayDeque<String> pending = new ArrayDeque<>(enclosing.stream().map(SFMJavaInteractionMap.Region::id).toList());
            HashSet<String> visited = new HashSet<>();
            while (!pending.isEmpty()) {
                String source = pending.removeFirst();
                if (!visited.add(source)) continue;
                for (SFMJavaInteractionMap.Outlink outlink : outlinksBySource.getOrDefault(source, List.of())) {
                    if (!outlink.relationKind().equals("containing-region")) continue;
                    SFMJavaInteractionMap.Region parent = regions.get(outlink.destinationRegionId());
                    if (parent == null) continue;
                    pending.addLast(parent.id());
                    if (DECLARATION_KINDS.contains(parent.semanticKind())) declarations.add(parent);
                }
            }

            for (SFMJavaInteractionMap.Region declaration : declarations) {
                addRegionEvidence(selected.documentRevisionId(), map, declaration,
                        SFMReleaseReviewV1.SelectorKind.DECLARATION,
                        declaration.id(), List.of(), output);
                for (SFMJavaInteractionMap.Outlink outlink : outlinksBySource
                        .getOrDefault(declaration.id(), List.of())) {
                    SFMReleaseReviewV1.SelectorKind kind = switch (outlink.relationKind()) {
                        case "signature" -> SFMReleaseReviewV1.SelectorKind.SIGNATURE;
                        case "body" -> SFMReleaseReviewV1.SelectorKind.BODY;
                        case "return-type" -> SFMReleaseReviewV1.SelectorKind.RETURN_TYPE;
                        default -> null;
                    };
                    if (kind == null) continue;
                    SFMJavaInteractionMap.Region target = regions.get(outlink.destinationRegionId());
                    if (target != null) {
                        addRegionEvidence(selected.documentRevisionId(), map, target, kind,
                                declaration.id() + "/" + outlink.relationKind(), List.of(outlink), output);
                    }
                }
            }

            for (SFMJavaInteractionMap.Region region : enclosing) {
                SFMReleaseReviewV1.SelectorKind kind = switch (region.semanticKind()) {
                    case "java-signature" -> SFMReleaseReviewV1.SelectorKind.SIGNATURE;
                    case "java-body" -> SFMReleaseReviewV1.SelectorKind.BODY;
                    case "java-return-type" -> SFMReleaseReviewV1.SelectorKind.RETURN_TYPE;
                    default -> null;
                };
                if (kind != null) {
                    addRegionEvidence(selected.documentRevisionId(), map, region, kind,
                            region.id(), List.of(), output);
                }
            }

            for (SFMJavaInteractionMap.Classification classification : map.classifications()) {
                SFMJavaInteractionMap.Region source = regions.get(classification.regionId());
                if (source == null || !overlaps(source, selected.startByte(), selected.endByte())) continue;
                List<SFMJavaInteractionMap.Outlink> navigation = map.navigationOutlinks(classification).stream()
                        .filter(outlink -> outlink.relationKind().equals("definition")
                                || outlink.relationKind().equals("reference"))
                        .toList();
                if (navigation.isEmpty()) continue;
                String semanticKey = navigation.stream()
                        .map(outlink -> outlink.destinationQuery().orElse(outlink.destinationRegionId()))
                        .sorted()
                        .distinct()
                        .collect(java.util.stream.Collectors.joining("|"));
                addRegionEvidence(selected.documentRevisionId(), map, source,
                        SFMReleaseReviewV1.SelectorKind.SYMBOL,
                        semanticKey, navigation, output);
            }
        }

        private static void addRegionEvidence(
                String documentRevisionId,
                SFMJavaInteractionMap.Result map,
                SFMJavaInteractionMap.Region region,
                SFMReleaseReviewV1.SelectorKind kind,
                String semanticKey,
                List<SFMJavaInteractionMap.Outlink> relationEvidence,
                Map<String, SemanticEvidence> output
        ) {
            if (region.startByte() > Integer.MAX_VALUE || region.endByte() > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Interaction region exceeds Java range capacity");
            }
            List<SFMJavaInteractionMap.Projection> projections = projections(map, region);
            String projectionWitness = map.semanticFingerprint() + "\n"
                    + projections.stream()
                    .map(projection -> projection.id() + "=" + projection.fingerprint())
                    .sorted()
                    .collect(java.util.stream.Collectors.joining("\n"));
            boolean projectionExact = !projections.isEmpty() && projections.stream().allMatch(projection ->
                    projection.loss().equals("lossless") && projection.completeness().equals("complete"));
            boolean relationExact = relationEvidence.stream().allMatch(outlink ->
                    outlink.confidence().equals("resolved") && outlink.completeness().equals("complete"));
            boolean exact = projectionExact && relationExact;
            ArrayList<String> diagnostics = new ArrayList<>();
            if (projections.isEmpty()) diagnostics.add("java.projection-unavailable: region has no canvas projection");
            else if (!projectionExact) diagnostics.add("java.projection-incomplete: canvas projection is lossy or partial");
            if (!relationExact) diagnostics.add("java.relation-incomplete: semantic relation is not fully resolved");
            if (kind == SFMReleaseReviewV1.SelectorKind.SYMBOL && relationEvidence.size() > 1) {
                diagnostics.add("java.symbol-ambiguous: multiple explicit symbol outlinks are retained");
                exact = false;
            }
            ArrayList<SFMReleaseReviewV1.Evidence> provenance = new ArrayList<>();
            provenance.add(new SFMReleaseReviewV1.Evidence("document-revision", documentRevisionId));
            provenance.add(new SFMReleaseReviewV1.Evidence("map-generation", Long.toString(map.semanticGeneration())));
            provenance.add(new SFMReleaseReviewV1.Evidence("map-semantic-fingerprint", map.semanticFingerprint()));
            provenance.add(new SFMReleaseReviewV1.Evidence("projection-ids", region.projectionIds().stream()
                    .sorted().collect(java.util.stream.Collectors.joining(","))));
            provenance.add(new SFMReleaseReviewV1.Evidence("region-id", region.id()));
            provenance.add(new SFMReleaseReviewV1.Evidence("region-kind", region.semanticKind()));
            provenance.add(new SFMReleaseReviewV1.Evidence("region-provenance", region.provenance()));
            List<SFMJavaInteractionMap.Outlink> orderedRelations = relationEvidence.stream()
                    .sorted(Comparator.comparing(SFMJavaInteractionMap.Outlink::id))
                    .toList();
            for (int index = 0; index < orderedRelations.size(); index++) {
                SFMJavaInteractionMap.Outlink relation = orderedRelations.get(index);
                provenance.add(new SFMReleaseReviewV1.Evidence(
                        "relation-" + index,
                        relation.id() + "|" + relation.relationKind() + "|"
                                + relation.destinationQuery().orElse(relation.destinationRegionId()) + "|"
                                + relation.confidence() + "|" + relation.completeness() + "|"
                                + relation.provenance()
                ));
            }

            SemanticEvidence evidence = new SemanticEvidence(
                    kind,
                    semanticKey,
                    List.of(new SFMReleaseReviewV1.AddressedRange(
                            documentRevisionId,
                            Math.toIntExact(region.startByte()),
                            Math.toIntExact(region.endByte())
                    )),
                    provenance,
                    exact ? SFMReleaseReviewV1.ProposalConfidence.EXACT
                            : SFMReleaseReviewV1.ProposalConfidence.CONSERVATIVE,
                    fingerprint("java-projection", projectionWitness),
                    map.workspaceFingerprint() + "@" + map.document().contentHash(),
                    diagnostics
            );
            output.putIfAbsent(kind + "\u0000" + semanticKey + "\u0000" + canonicalRanges(evidence.ranges()), evidence);
        }

        private static List<SFMJavaInteractionMap.Projection> projections(
                SFMJavaInteractionMap.Result map,
                SFMJavaInteractionMap.Region region
        ) {
            Map<String, SFMJavaInteractionMap.Projection> indexed = new HashMap<>();
            map.projections().forEach(projection -> indexed.put(projection.id(), projection));
            ArrayList<SFMJavaInteractionMap.Projection> result = new ArrayList<>();
            for (String id : region.projectionIds()) {
                SFMJavaInteractionMap.Projection projection = indexed.get(id);
                if (projection == null) {
                    throw new IllegalArgumentException("Interaction region references absent projection " + id);
                }
                result.add(projection);
            }
            return result;
        }

        private static void validatePublication(
                SFMReleaseReviewSelectionAdapter.DocumentWitness document,
                SFMJavaInteractionMap.Result map
        ) {
            if (map.outcome() != SFMJavaInteractionMap.Outcome.SUCCESS) {
                throw new IllegalArgumentException("Interaction-map publication is not successful: " + map.outcome());
            }
            if (!map.document().address().equals(document.sourceAddress())) {
                throw new IllegalArgumentException("Interaction-map publication belongs to a different source address");
            }
            String expected = SFMDefinitionRequest.sha256(document.text());
            if (!map.document().contentHash().equals(expected)
                    || !document.sha256().equals(expected.substring("sha256:".length()))) {
                throw new IllegalArgumentException("Interaction-map publication is stale for "
                        + document.documentRevisionId());
            }
        }

        private static boolean encloses(SFMJavaInteractionMap.Region region, int start, int end) {
            if (start == end) return region.containsByte(start);
            return region.startByte() <= start && end <= region.endByte();
        }

        private static boolean overlaps(SFMJavaInteractionMap.Region region, int start, int end) {
            if (start == end) return region.containsByte(start);
            return region.startByte() < end && start < region.endByte();
        }
    }

    private static String canonicalPinned(SFMReleaseReviewV1.PinnedSelection pinned) {
        return pinned.selectionRevision() + "\n" + pinned.sourceExpression() + "\n"
                + pinned.primaryRangeIndex() + "\n"
                + pinned.ranges().stream()
                .map(range -> range.direction() + ":" + range.documentRevisionId() + ":"
                        + range.documentSha256() + ":" + range.startByte() + ":" + range.endByte())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static String canonicalRanges(List<SFMReleaseReviewV1.AddressedRange> ranges) {
        return ranges.stream()
                .map(range -> range.documentRevisionId() + ":" + range.startByte() + ":" + range.endByte())
                .collect(java.util.stream.Collectors.joining("|"));
    }

    private static String fingerprint(String domain, String value) {
        return SFMReviewSessionV1Kernel.sha256((domain + "\n" + value).getBytes(StandardCharsets.UTF_8));
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }

    private static String requireSha256(String value, String label) {
        value = requireText(value, label);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + " must be lowercase SHA-256");
        }
        return value;
    }
}
