package ca.teamdman.sfm.client.semantic;

import ca.teamdman.sfm.client.symbol.SFMJavaInteractionMap;

import java.util.ArrayList;
import java.util.HashMap;
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
    private final int[] utf8ByteAtUtf16;
    private final SFMJavaInteractionMap.Region[] regionAtUtf8Byte;
    private final Map<String, SFMJavaInteractionMap.Classification> classificationsByRegion;
    private final Map<String, List<SFMJavaInteractionMap.Outlink>> outlinksBySourceRegion;
    private final Set<String> reciprocalOutlinkIds;
    private final Map<String, SFMCanvasSpatialCoverageSnapshot.SemanticResult> semanticsByRegion =
            new HashMap<>();

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
        utf8ByteAtUtf16 = utf8ByteOffsets(text);
        regionAtUtf8Byte = indexRegions(map, utf8ByteAtUtf16[text.length()]);
        LinkedHashMap<String, SFMJavaInteractionMap.Classification> classifications = new LinkedHashMap<>();
        map.classifications().forEach(value -> classifications.put(value.regionId(), value));
        classificationsByRegion = Map.copyOf(classifications);
        LinkedHashMap<String, List<SFMJavaInteractionMap.Outlink>> groupedOutlinks = new LinkedHashMap<>();
        map.outlinks().forEach(outlink -> groupedOutlinks
                .computeIfAbsent(outlink.sourceRegionId(), ignored -> new ArrayList<>())
                .add(outlink));
        groupedOutlinks.replaceAll((ignored, outlinks) -> List.copyOf(outlinks));
        outlinksBySourceRegion = Map.copyOf(groupedOutlinks);
        LinkedHashSet<String> reciprocalIds = new LinkedHashSet<>();
        map.reciprocity().stream()
                .filter(value -> value.status() == SFMJavaInteractionMap.ReciprocityStatus.VERIFIED)
                .forEach(value -> {
                    reciprocalIds.add(value.definitionOutlinkId());
                    reciprocalIds.add(value.referenceOutlinkId());
                });
        reciprocalOutlinkIds = Set.copyOf(reciprocalIds);
    }

    @Override
    public Optional<SFMCanvasSpatialCoverageSnapshot.SemanticResult> atUtf16(int utf16Offset) {
        if (utf16Offset < 0 || utf16Offset >= text.length()) return Optional.empty();
        int byteOffset = utf8ByteAtUtf16[utf16Offset];
        SFMJavaInteractionMap.Region region = byteOffset < regionAtUtf8Byte.length
                ? regionAtUtf8Byte[byteOffset]
                : null;
        if (region == null) return Optional.empty();
        SFMJavaInteractionMap.Classification source = classificationsByRegion.get(region.id());
        if (source == null) return Optional.empty();
        return Optional.of(semanticsByRegion.computeIfAbsent(
                region.id(),
                ignored -> semanticResult(region, source)
        ));
    }

    private SFMCanvasSpatialCoverageSnapshot.SemanticResult semanticResult(
            SFMJavaInteractionMap.Region region,
            SFMJavaInteractionMap.Classification source
    ) {
        List<SFMJavaInteractionMap.Outlink> sourceOutlinks =
                outlinksBySourceRegion.getOrDefault(region.id(), List.of());
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
        boolean reciprocityExpected = sourceOutlinks.stream().anyMatch(outlink ->
                outlink.relationKind().equals("definition") || outlink.relationKind().equals("reference"));
        boolean reciprocal = reciprocityExpected && sourceOutlinks.stream()
                .map(SFMJavaInteractionMap.Outlink::id)
                .anyMatch(reciprocalOutlinkIds::contains);

        return new SFMCanvasSpatialCoverageSnapshot.SemanticResult(
                classification(source),
                outlinks,
                List.copyOf(actions.values()),
                providerEvidence,
                "sfm:java-interaction-map/" + region.semanticKind(),
                map.semanticGeneration(),
                reciprocityExpected,
                reciprocal
        );
    }

    private static SFMJavaInteractionMap.Region[] indexRegions(
            SFMJavaInteractionMap.Result map,
            int utf8Bytes
    ) {
        SFMJavaInteractionMap.Region[] result = new SFMJavaInteractionMap.Region[utf8Bytes];
        Set<String> utf8Domains = map.domains().stream()
                .filter(domain -> domain.kind().equals("utf8"))
                .map(SFMJavaInteractionMap.Domain::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (SFMJavaInteractionMap.Region region : map.regions()) {
            if (!utf8Domains.contains(region.domainId())) continue;
            int start = (int) Math.max(0L, Math.min((long) utf8Bytes, region.startByte()));
            int end = (int) Math.max(start, Math.min((long) utf8Bytes, region.endByte()));
            for (int offset = start; offset < end; offset++) {
                SFMJavaInteractionMap.Region current = result[offset];
                if (current == null || isMoreSpecific(region, current)) result[offset] = region;
            }
        }
        return result;
    }

    private static boolean isMoreSpecific(
            SFMJavaInteractionMap.Region candidate,
            SFMJavaInteractionMap.Region current
    ) {
        int length = Long.compare(candidate.byteLength(), current.byteLength());
        return length < 0 || (length == 0 && candidate.id().compareTo(current.id()) < 0);
    }

    private static int[] utf8ByteOffsets(String text) {
        int[] result = new int[text.length() + 1];
        int utf16 = 0;
        int utf8 = 0;
        while (utf16 < text.length()) {
            int codePoint = text.codePointAt(utf16);
            int codeUnits = Character.charCount(codePoint);
            for (int unit = 0; unit < codeUnits; unit++) result[utf16 + unit] = utf8;
            utf16 += codeUnits;
            utf8 += utf8Length(codePoint);
        }
        result[text.length()] = utf8;
        return result;
    }

    private static int utf8Length(int codePoint) {
        if (codePoint <= 0x7f) return 1;
        if (codePoint <= 0x7ff) return 2;
        if (codePoint <= 0xffff) return 3;
        return 4;
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
