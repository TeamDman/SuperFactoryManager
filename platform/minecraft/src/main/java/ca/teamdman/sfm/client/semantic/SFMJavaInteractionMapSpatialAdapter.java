package ca.teamdman.sfm.client.semantic;

import ca.teamdman.sfm.client.symbol.SFMJavaInteractionMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Projects one immutable Rust Java interaction map into the Java canvas oracle. */
public final class SFMJavaInteractionMapSpatialAdapter
        implements SFMCanvasSpatialCoverageSnapshot.SemanticLookup {
    private static final String FALLBACK_PROVIDER = "sfm:java-interaction-map";

    private final String text;
    private final SFMJavaInteractionMap.Result map;
    private final int[] utf8ByteAtUtf16;
    private final SFMJavaInteractionMap.Region[] regionAtUtf8Byte;
    private final Map<String, SFMJavaInteractionMap.Classification> classificationsByRegion;
    private final Map<String, SFMJavaInteractionMap.Outlink> outlinksById;
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
        LinkedHashMap<String, SFMJavaInteractionMap.Classification> classifications = new LinkedHashMap<>();
        map.classifications().forEach(value -> classifications.put(value.regionId(), value));
        classificationsByRegion = Map.copyOf(classifications);
        LinkedHashMap<String, SFMJavaInteractionMap.Outlink> indexedOutlinks = new LinkedHashMap<>();
        map.outlinks().forEach(outlink -> indexedOutlinks.put(outlink.id(), outlink));
        outlinksById = Map.copyOf(indexedOutlinks);
        regionAtUtf8Byte = indexRegions(
                map,
                utf8ByteAtUtf16[text.length()],
                classificationsByRegion,
                outlinksById
        );
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
        List<SFMJavaInteractionMap.Outlink> sourceOutlinks = source.navigationOutlinkIds().stream()
                .map(id -> Objects.requireNonNull(
                        outlinksById.get(id),
                        () -> "Classification references absent navigation outlink " + id
                ))
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
            int utf8Bytes,
            Map<String, SFMJavaInteractionMap.Classification> classificationsByRegion,
            Map<String, SFMJavaInteractionMap.Outlink> outlinksById
    ) {
        SFMJavaInteractionMap.Region[] result = new SFMJavaInteractionMap.Region[utf8Bytes];
        Set<String> utf8Domains = map.domains().stream()
                .filter(domain -> domain.kind().equals("utf8"))
                .map(SFMJavaInteractionMap.Domain::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Map<String, Integer> interactionRanks = new HashMap<>();
        map.regions().forEach(region -> interactionRanks.put(
                region.id(),
                interactionRank(region, classificationsByRegion, outlinksById)
        ));
        Comparator<SFMJavaInteractionMap.Region> specificity = Comparator
                .comparingInt((SFMJavaInteractionMap.Region region) ->
                        interactionRanks.getOrDefault(region.id(), 8) <= 3 ? 0 : 1)
                .thenComparingLong(SFMJavaInteractionMap.Region::byteLength)
                .thenComparingInt(region -> interactionRanks.getOrDefault(region.id(), 8))
                .thenComparing(SFMJavaInteractionMap.Region::id);
        List<SFMJavaInteractionMap.Region> starts = map.regions().stream()
                .filter(region -> utf8Domains.contains(region.domainId()))
                .sorted(Comparator.comparingLong(SFMJavaInteractionMap.Region::startByte)
                        .thenComparing(specificity))
                .toList();
        List<SFMJavaInteractionMap.Region> ends = starts.stream()
                .sorted(Comparator.comparingLong(SFMJavaInteractionMap.Region::endByte)
                        .thenComparing(specificity))
                .toList();
        TreeSet<SFMJavaInteractionMap.Region> active = new TreeSet<>(specificity);
        int startIndex = 0;
        int endIndex = 0;
        for (int offset = 0; offset < utf8Bytes; offset++) {
            while (endIndex < ends.size() && ends.get(endIndex).endByte() <= offset) {
                active.remove(ends.get(endIndex++));
            }
            while (startIndex < starts.size() && starts.get(startIndex).startByte() <= offset) {
                SFMJavaInteractionMap.Region region = starts.get(startIndex++);
                if (region.endByte() > offset) active.add(region);
            }
            if (!active.isEmpty()) result[offset] = active.first();
        }
        return result;
    }

    /**
     * Raw syntax can be narrower than the resolved semantic span (notably the
     * terminal identifier inside a qualified import). Exact definition/reference
     * relations form the first tier, then source specificity applies within a
     * tier; region identity is only the final deterministic tie-breaker.
     */
    private static int interactionRank(
            SFMJavaInteractionMap.Region region,
            Map<String, SFMJavaInteractionMap.Classification> classificationsByRegion,
            Map<String, SFMJavaInteractionMap.Outlink> outlinksById
    ) {
        SFMJavaInteractionMap.Classification classification = classificationsByRegion.get(region.id());
        if (classification == null) return 8;
        List<SFMJavaInteractionMap.Outlink> navigation = classification.navigationOutlinkIds().stream()
                .map(outlinksById::get)
                .filter(Objects::nonNull)
                .toList();
        if (navigation.stream().anyMatch(outlink -> outlink.relationKind().equals("definition")
                && outlink.completeness().equals("complete")
                && outlink.confidence().equals("resolved"))) return 0;
        if (navigation.stream().anyMatch(outlink -> outlink.relationKind().equals("reference")
                && outlink.completeness().equals("complete")
                && outlink.confidence().equals("resolved"))) return 1;
        if (navigation.stream().anyMatch(outlink -> outlink.relationKind().equals("definition"))) return 2;
        if (navigation.stream().anyMatch(outlink -> outlink.relationKind().equals("reference"))) return 3;
        if (navigation.stream().anyMatch(outlink -> outlink.intent().equals("navigate"))) return 4;
        if (!navigation.isEmpty()) return 5;
        if (!classification.contextualActionIds().isEmpty()) return 6;
        return classification.status() == SFMJavaInteractionMap.ClassificationStatus.ACTIONABLE ? 6 : 7;
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
