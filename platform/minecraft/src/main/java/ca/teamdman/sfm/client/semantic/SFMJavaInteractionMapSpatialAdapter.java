package ca.teamdman.sfm.client.semantic;

import ca.teamdman.sfm.client.context.SFMContextTextCoordinates;
import ca.teamdman.sfm.client.symbol.SFMJavaInteractionMap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Projects one immutable Rust Java interaction map into the Java canvas oracle. */
public final class SFMJavaInteractionMapSpatialAdapter
        implements SFMCanvasSpatialCoverageSnapshot.SemanticLookup {
    private static final String FALLBACK_PROVIDER = "sfm:java-interaction-map";

    private final String text;
    private final SFMJavaInteractionMap.Result map;

    public SFMJavaInteractionMapSpatialAdapter(String text, SFMJavaInteractionMap.Result map) {
        this.text = Objects.requireNonNull(text, "text");
        this.map = Objects.requireNonNull(map, "map");
        if (map.outcome() != SFMJavaInteractionMap.Outcome.SUCCESS) {
            throw new IllegalArgumentException("Only successful Java interaction maps can back canvas coverage");
        }
        String contentHash = ca.teamdman.sfm.client.symbol.SFMDefinitionRequest.sha256(text);
        if (!map.document().contentHash().equals(contentHash)) {
            throw new IllegalArgumentException("Java interaction map does not match the canvas document");
        }
    }

    @Override
    public Optional<SFMCanvasSpatialCoverageSnapshot.SemanticResult> atUtf16(int utf16Offset) {
        if (utf16Offset < 0 || utf16Offset >= text.length()) return Optional.empty();
        long byteOffset = SFMContextTextCoordinates.atUtf16Offset(text, utf16Offset).byteOffset();
        SFMJavaInteractionMap.Region region = map.mostSpecificRegionAtByte(byteOffset).orElse(null);
        if (region == null) return Optional.empty();
        SFMJavaInteractionMap.Classification source = map.classification(region.id()).orElse(null);
        if (source == null) return Optional.empty();

        List<SFMJavaInteractionMap.Outlink> sourceOutlinks = map.outlinks().stream()
                .filter(outlink -> outlink.sourceRegionId().equals(region.id()))
                .toList();
        List<SFMSpatialSemanticContract.Outlink> outlinks = sourceOutlinks.stream()
                .map(SFMJavaInteractionMapSpatialAdapter::outlink)
                .toList();
        LinkedHashMap<String, SFMSpatialSemanticContract.ActionDraft> actions = new LinkedHashMap<>();
        for (SFMJavaInteractionMap.Outlink outlink : sourceOutlinks) {
            for (SFMJavaInteractionMap.ActionDraft action : outlink.actionDrafts()) {
                SFMSpatialSemanticContract.ActionDraft converted = new SFMSpatialSemanticContract.ActionDraft(
                        action.actionId(), action.arguments());
                actions.putIfAbsent(converted.actionId() + "\u0000" + converted.arguments(), converted);
            }
        }
        for (String actionId : source.contextualActionIds()) {
            SFMSpatialSemanticContract.ActionDraft converted =
                    new SFMSpatialSemanticContract.ActionDraft(actionId, List.of());
            actions.putIfAbsent(converted.actionId() + "\u0000[]", converted);
        }

        List<SFMSpatialSemanticContract.ProviderEvidence> providerEvidence = providerEvidence(sourceOutlinks);
        Set<String> sourceOutlinkIds = sourceOutlinks.stream()
                .map(SFMJavaInteractionMap.Outlink::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        boolean reciprocityExpected = sourceOutlinks.stream().anyMatch(outlink ->
                outlink.relationKind().equals("definition") || outlink.relationKind().equals("reference"));
        boolean reciprocal = reciprocityExpected && map.reciprocity().stream().anyMatch(value ->
                value.status() == SFMJavaInteractionMap.ReciprocityStatus.VERIFIED
                        && (sourceOutlinkIds.contains(value.definitionOutlinkId())
                        || sourceOutlinkIds.contains(value.referenceOutlinkId())));

        return Optional.of(new SFMCanvasSpatialCoverageSnapshot.SemanticResult(
                classification(source),
                outlinks,
                List.copyOf(actions.values()),
                providerEvidence,
                "sfm:java-interaction-map/" + region.semanticKind(),
                map.semanticGeneration(),
                reciprocityExpected,
                reciprocal
        ));
    }

    private static SFMSpatialSemanticContract.Classification classification(
            SFMJavaInteractionMap.Classification source
    ) {
        SFMSpatialSemanticContract.ClassificationStatus status =
                SFMSpatialSemanticContract.ClassificationStatus.fromWireName(source.status().wireName());
        String reason = status == SFMSpatialSemanticContract.ClassificationStatus.ACTIONABLE
                ? null
                : source.reasonCode().orElse(source.status().wireName());
        return new SFMSpatialSemanticContract.Classification(status, reason);
    }

    private static SFMSpatialSemanticContract.Outlink outlink(SFMJavaInteractionMap.Outlink source) {
        String destinationRegion = source.destinationRegionId().isBlank()
                ? null : source.destinationRegionId();
        String destinationQuery = destinationRegion == null
                ? source.destinationQuery().orElse(null) : null;
        return new SFMSpatialSemanticContract.Outlink(
                SFMSpatialSemanticContract.OUTLINK_SCHEMA,
                source.id(),
                source.sourceRegionId(),
                destinationRegion,
                destinationQuery,
                source.relationKind(),
                SFMSpatialSemanticContract.Intent.fromWireName(source.intent()),
                source.providerId(),
                source.providerGeneration(),
                source.reason(),
                SFMSpatialSemanticContract.Confidence.fromWireName(source.confidence()),
                SFMSpatialSemanticContract.Completeness.fromWireName(source.completeness()),
                source.recommendedProjection(),
                source.actionDrafts().stream()
                        .map(action -> new SFMSpatialSemanticContract.ActionDraft(
                                action.actionId(), action.arguments()))
                        .toList(),
                source.provenance()
        );
    }

    private static List<SFMSpatialSemanticContract.ProviderEvidence> providerEvidence(
            List<SFMJavaInteractionMap.Outlink> outlinks
    ) {
        if (outlinks.isEmpty()) {
            return List.of(new SFMSpatialSemanticContract.ProviderEvidence(
                    FALLBACK_PROVIDER, 100, "classified", null));
        }
        Map<String, SFMJavaInteractionMap.Outlink> providers = new LinkedHashMap<>();
        outlinks.forEach(outlink -> providers.putIfAbsent(outlink.providerId(), outlink));
        ArrayList<SFMSpatialSemanticContract.ProviderEvidence> evidence = new ArrayList<>();
        providers.forEach((providerId, outlink) -> evidence.add(
                new SFMSpatialSemanticContract.ProviderEvidence(
                        providerId,
                        100,
                        "resolved",
                        outlink.reason()
                )
        ));
        return List.copyOf(evidence);
    }
}
