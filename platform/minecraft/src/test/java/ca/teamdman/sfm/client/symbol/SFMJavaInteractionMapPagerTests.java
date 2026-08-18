package ca.teamdman.sfm.client.symbol;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMJavaInteractionMapPagerTests {
    @Test
    void collectsEveryBoundedRegionAndInventoryPageWithoutGenerationDrift() {
        SFMJavaInteractionMap.Request defaults = SFMJavaInteractionMapProtocolTests.request();
        SFMJavaInteractionMap.Request initial = new SFMJavaInteractionMap.Request(
                SFMJavaInteractionMap.REQUEST_SCHEMA,
                defaults.requestId(),
                defaults.requestGeneration(),
                defaults.workspace(),
                defaults.document(),
                new SFMJavaInteractionMap.Window(0, 1, 0, 1, 4_096),
                Optional.empty()
        );
        AtomicLong requestIds = new AtomicLong(initial.requestId());
        List<SFMJavaInteractionMap.Request> observed = new ArrayList<>();

        SFMJavaInteractionMapPager.Submission submission = SFMJavaInteractionMapPager.collect(
                initial,
                requestIds::incrementAndGet,
                request -> {
                    observed.add(request);
                    return new SFMJavaInteractionMapPager.PageSubmission(
                            CompletableFuture.completedFuture(page(request)),
                            () -> { }
                    );
                }
        );
        SFMJavaInteractionMap.Result result = submission.result().join();

        assertEquals(2, observed.size());
        assertEquals(0, observed.get(0).window().regionOffset());
        assertEquals(1, observed.get(1).window().regionOffset());
        assertEquals(0, observed.get(0).window().inventoryOffset());
        assertEquals(1, observed.get(1).window().inventoryOffset());
        assertTrue(observed.get(1).knownSemanticFingerprint().isEmpty());
        assertEquals(initial.requestGeneration(), observed.get(1).requestGeneration());
        assertEquals(initial.requestId() + 1, observed.get(1).requestId());
        assertTrue(result.matches(initial));
        assertEquals(2, result.regions().size());
        assertEquals(2, result.classifications().size());
        assertEquals(2, result.files().size());
        assertEquals(2, result.page().returnedRegions());
        assertEquals(2, result.page().returnedInventoryFiles());
        assertTrue(result.page().nextRegionOffset().isEmpty());
        assertTrue(result.page().nextInventoryOffset().isEmpty());
    }

    @Test
    void cancellationStopsTheActivePageAndCancelsTheAggregate() {
        SFMJavaInteractionMap.Request request = SFMJavaInteractionMapProtocolTests.request();
        AtomicInteger cancellations = new AtomicInteger();
        SFMJavaInteractionMapPager.Submission submission = SFMJavaInteractionMapPager.collect(
                request,
                () -> request.requestId() + 1,
                ignored -> new SFMJavaInteractionMapPager.PageSubmission(
                        new CompletableFuture<>(),
                        cancellations::incrementAndGet
                )
        );

        submission.cancellation().run();

        assertEquals(1, cancellations.get());
        assertTrue(submission.result().isCancelled());
        assertFalse(submission.result().isCompletedExceptionally() && !submission.result().isCancelled());
    }

    private static SFMJavaInteractionMap.Result page(SFMJavaInteractionMap.Request request) {
        SFMJavaInteractionMap.Result base = SFMJavaInteractionMapJsonCodec.decodeResult(
                SFMJavaInteractionMapProtocolTests.resultJson(request).toString());
        boolean first = request.window().regionOffset() == 0;
        SFMJavaInteractionMap.Region region;
        SFMJavaInteractionMap.Classification classification;
        List<SFMJavaInteractionMap.Outlink> outlinks;
        SFMJavaInteractionMap.FileRow file;
        if (first) {
            region = base.regions().get(0);
            classification = base.classifications().get(0);
            outlinks = base.outlinks();
            file = base.files().get(0);
        } else {
            region = new SFMJavaInteractionMap.Region(
                    SFMJavaInteractionMap.REGION_SCHEMA,
                    "region:second",
                    base.regions().get(0).domainId(),
                    "source-interval",
                    List.of(new SFMJavaInteractionMap.Axis(6, 10)),
                    "half-open",
                    "identifier",
                    "fixture",
                    List.of()
            );
            SFMJavaInteractionMap.Outlink template = base.outlinks().get(0);
            SFMJavaInteractionMap.Outlink outlink = new SFMJavaInteractionMap.Outlink(
                    template.schema(),
                    "outlink:second-definition",
                    region.id(),
                    region.id(),
                    template.destinationQuery(),
                    template.relationKind(),
                    template.intent(),
                    template.providerId(),
                    template.providerGeneration(),
                    template.reason(),
                    template.confidence(),
                    template.completeness(),
                    template.recommendedProjection(),
                    template.actionDrafts(),
                    template.provenance()
            );
            classification = new SFMJavaInteractionMap.Classification(
                    region.id(),
                    SFMJavaInteractionMap.ClassificationStatus.ACTIONABLE,
                    Optional.empty(),
                    List.of(outlink.id()),
                    List.of()
            );
            outlinks = List.of(outlink);
            file = new SFMJavaInteractionMap.FileRow(
                    "file:///D:/workspace/source/B.java",
                    "file",
                    request.document().rootId(),
                    "B.java",
                    "source/B.java",
                    request.document().sourceSet(),
                    Optional.of("sha256:" + "2".repeat(64)),
                    SFMJavaInteractionMap.FileState.COVERED,
                    Optional.empty()
            );
        }
        Optional<Long> next = first ? Optional.of(1L) : Optional.empty();
        return new SFMJavaInteractionMap.Result(
                base.schema(),
                request.requestId(),
                request.requestGeneration(),
                base.workspaceGeneration(),
                base.workspaceFingerprint(),
                request.requestGeneration(),
                base.semanticGeneration(),
                base.semanticFingerprint(),
                base.outcome(),
                base.document(),
                base.domains(),
                base.projections(),
                List.of(region),
                List.of(classification),
                outlinks,
                List.of(),
                List.of(),
                List.of(file),
                new SFMJavaInteractionMap.Page(
                        request.window().regionOffset(),
                        1,
                        2,
                        next,
                        request.window().inventoryOffset(),
                        1,
                        2,
                        next,
                        256
                ),
                List.of()
        );
    }
}
