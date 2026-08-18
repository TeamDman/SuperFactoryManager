package ca.teamdman.sfm.client.symbol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Fetches every independently bounded interaction-map page and publishes one
 * immutable generation-consistent map to the editor.
 */
final class SFMJavaInteractionMapPager {
    private SFMJavaInteractionMapPager() {
    }

    record PageSubmission(
            CompletableFuture<SFMJavaInteractionMap.Result> result,
            Runnable cancellation
    ) {
        PageSubmission {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(cancellation, "cancellation");
        }
    }

    record Submission(
            CompletableFuture<SFMJavaInteractionMap.Result> result,
            Runnable cancellation
    ) {
        Submission {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(cancellation, "cancellation");
        }
    }

    static Submission collect(
            SFMJavaInteractionMap.Request initialRequest,
            LongSupplier requestIds,
            Function<SFMJavaInteractionMap.Request, PageSubmission> requester
    ) {
        Objects.requireNonNull(initialRequest, "initialRequest");
        Objects.requireNonNull(requestIds, "requestIds");
        Objects.requireNonNull(requester, "requester");
        CompletableFuture<SFMJavaInteractionMap.Result> answer = new CompletableFuture<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicReference<Runnable> activeCancellation = new AtomicReference<>(() -> { });
        Collector collector = new Collector(initialRequest, requestIds, requester, answer,
                cancelled, activeCancellation);
        collector.request(initialRequest);
        return new Submission(answer, () -> {
            if (!cancelled.compareAndSet(false, true)) return;
            activeCancellation.get().run();
            answer.cancel(false);
        });
    }

    private static final class Collector {
        private final SFMJavaInteractionMap.Request initialRequest;
        private final LongSupplier requestIds;
        private final Function<SFMJavaInteractionMap.Request, PageSubmission> requester;
        private final CompletableFuture<SFMJavaInteractionMap.Result> answer;
        private final AtomicBoolean cancelled;
        private final AtomicReference<Runnable> activeCancellation;
        private final List<SFMJavaInteractionMap.Result> pages = new ArrayList<>();
        private final Set<Cursor> requestedCursors = new LinkedHashSet<>();

        private Collector(
                SFMJavaInteractionMap.Request initialRequest,
                LongSupplier requestIds,
                Function<SFMJavaInteractionMap.Request, PageSubmission> requester,
                CompletableFuture<SFMJavaInteractionMap.Result> answer,
                AtomicBoolean cancelled,
                AtomicReference<Runnable> activeCancellation
        ) {
            this.initialRequest = initialRequest;
            this.requestIds = requestIds;
            this.requester = requester;
            this.answer = answer;
            this.cancelled = cancelled;
            this.activeCancellation = activeCancellation;
        }

        private void request(SFMJavaInteractionMap.Request request) {
            if (cancelled.get() || answer.isDone()) return;
            Cursor cursor = new Cursor(request.window().regionOffset(), request.window().inventoryOffset());
            if (!requestedCursors.add(cursor)) {
                answer.completeExceptionally(new IllegalStateException(
                        "Java interaction-map pagination repeated cursor " + cursor));
                return;
            }
            PageSubmission submitted;
            try {
                submitted = requester.apply(request);
            } catch (RuntimeException failure) {
                answer.completeExceptionally(failure);
                return;
            }
            activeCancellation.set(submitted.cancellation());
            if (cancelled.get()) {
                submitted.cancellation().run();
                return;
            }
            submitted.result().whenComplete((result, failure) -> {
                if (cancelled.get() || answer.isDone()) return;
                if (failure != null) {
                    answer.completeExceptionally(unwrap(failure));
                    return;
                }
                try {
                    accept(request, result);
                } catch (RuntimeException invalid) {
                    answer.completeExceptionally(invalid);
                }
            });
        }

        private void accept(
                SFMJavaInteractionMap.Request request,
                SFMJavaInteractionMap.Result result
        ) {
            Objects.requireNonNull(result, "interaction-map page");
            if (!result.matches(request)) {
                throw new IllegalStateException("Java interaction-map page identity drifted from its request");
            }
            if (result.outcome() != SFMJavaInteractionMap.Outcome.SUCCESS) {
                if (pages.isEmpty()) {
                    answer.complete(result);
                    return;
                }
                throw new IllegalStateException(
                        "Java interaction-map pagination ended with " + result.outcome().wireName());
            }
            validatePageShape(request, result);
            if (!pages.isEmpty()) validateStableIdentity(pages.get(0), result);
            pages.add(result);

            Optional<Long> nextRegion = result.page().nextRegionOffset();
            Optional<Long> nextInventory = result.page().nextInventoryOffset();
            if (nextRegion.isEmpty() && nextInventory.isEmpty()) {
                answer.complete(merge(pages));
                return;
            }
            long regionOffset = nextRegion.orElse(result.page().totalRegions());
            long inventoryOffset = nextInventory.orElse(result.page().totalInventoryFiles());
            SFMJavaInteractionMap.Window previous = request.window();
            SFMJavaInteractionMap.Request next = new SFMJavaInteractionMap.Request(
                    SFMJavaInteractionMap.REQUEST_SCHEMA,
                    requestIds.getAsLong(),
                    initialRequest.requestGeneration(),
                    initialRequest.workspace(),
                    initialRequest.document(),
                    new SFMJavaInteractionMap.Window(
                            regionOffset,
                            previous.maximumRegions(),
                            inventoryOffset,
                            previous.maximumInventoryFiles(),
                            previous.maximumEncodedBytes()
                    ),
                    Optional.empty()
            );
            request(next);
        }
    }

    static SFMJavaInteractionMap.Result merge(List<SFMJavaInteractionMap.Result> pages) {
        List<SFMJavaInteractionMap.Result> copy = List.copyOf(pages);
        if (copy.isEmpty()) throw new IllegalArgumentException("At least one interaction-map page is required");
        SFMJavaInteractionMap.Result first = copy.get(0);
        if (first.outcome() != SFMJavaInteractionMap.Outcome.SUCCESS) {
            if (copy.size() == 1) return first;
            throw new IllegalArgumentException("A terminal interaction-map result cannot have later pages");
        }

        LinkedHashMap<String, SFMJavaInteractionMap.Region> regions = new LinkedHashMap<>();
        LinkedHashMap<String, SFMJavaInteractionMap.Classification> classifications = new LinkedHashMap<>();
        LinkedHashMap<String, SFMJavaInteractionMap.Outlink> outlinks = new LinkedHashMap<>();
        LinkedHashMap<String, SFMJavaInteractionMap.ExceptionWitness> exceptions = new LinkedHashMap<>();
        LinkedHashSet<SFMJavaInteractionMap.Reciprocity> reciprocity = new LinkedHashSet<>();
        LinkedHashSet<SFMJavaInteractionMap.FileRow> files = new LinkedHashSet<>();
        LinkedHashSet<SFMDefinitionResult.Diagnostic> diagnostics = new LinkedHashSet<>();
        long encodedBytes = 0;
        for (SFMJavaInteractionMap.Result page : copy) {
            validateStableIdentity(first, page);
            putUnique(regions, page.regions(), SFMJavaInteractionMap.Region::id, "region");
            putUnique(classifications, page.classifications(),
                    SFMJavaInteractionMap.Classification::regionId, "classification");
            putUnique(outlinks, page.outlinks(), SFMJavaInteractionMap.Outlink::id, "outlink");
            putUnique(exceptions, page.exceptions(), SFMJavaInteractionMap.ExceptionWitness::id, "exception");
            reciprocity.addAll(page.reciprocity());
            files.addAll(page.files());
            diagnostics.addAll(page.diagnostics());
            encodedBytes = saturatingAdd(encodedBytes, page.page().encodedBytes());
        }
        if (regions.size() != first.page().totalRegions()) {
            throw new IllegalStateException("Java interaction-map region pages were incomplete: expected "
                    + first.page().totalRegions() + " but collected " + regions.size());
        }
        if (files.size() != first.page().totalInventoryFiles()) {
            throw new IllegalStateException("Java interaction-map inventory pages were incomplete: expected "
                    + first.page().totalInventoryFiles() + " but collected " + files.size());
        }
        return new SFMJavaInteractionMap.Result(
                first.schema(),
                first.requestId(),
                first.requestGeneration(),
                first.workspaceGeneration(),
                first.workspaceFingerprint(),
                first.documentGeneration(),
                first.semanticGeneration(),
                first.semanticFingerprint(),
                first.outcome(),
                first.document(),
                first.domains(),
                first.projections(),
                List.copyOf(regions.values()),
                List.copyOf(classifications.values()),
                List.copyOf(outlinks.values()),
                List.copyOf(reciprocity),
                List.copyOf(exceptions.values()),
                List.copyOf(files),
                new SFMJavaInteractionMap.Page(
                        0,
                        regions.size(),
                        first.page().totalRegions(),
                        Optional.empty(),
                        0,
                        files.size(),
                        first.page().totalInventoryFiles(),
                        Optional.empty(),
                        encodedBytes
                ),
                List.copyOf(diagnostics)
        );
    }

    private static void validatePageShape(
            SFMJavaInteractionMap.Request request,
            SFMJavaInteractionMap.Result result
    ) {
        SFMJavaInteractionMap.Page page = result.page();
        if (page.regionOffset() != request.window().regionOffset()
                || page.inventoryOffset() != request.window().inventoryOffset()) {
            throw new IllegalStateException("Java interaction-map response cursor drifted from its request");
        }
        if (page.returnedRegions() != result.regions().size()
                || page.returnedInventoryFiles() != result.files().size()) {
            throw new IllegalStateException("Java interaction-map page counts disagree with their payloads");
        }
        page.nextRegionOffset().ifPresent(next -> requireProgress(
                page.regionOffset(), page.returnedRegions(), page.totalRegions(), next, "region"));
        page.nextInventoryOffset().ifPresent(next -> requireProgress(
                page.inventoryOffset(), page.returnedInventoryFiles(),
                page.totalInventoryFiles(), next, "inventory"));
    }

    private static void requireProgress(
            long offset,
            long returned,
            long total,
            long next,
            String lane
    ) {
        if (returned <= 0 || next != offset + returned || next >= total) {
            throw new IllegalStateException("Java interaction-map " + lane + " cursor did not advance");
        }
    }

    private static void validateStableIdentity(
            SFMJavaInteractionMap.Result first,
            SFMJavaInteractionMap.Result page
    ) {
        if (page.outcome() != SFMJavaInteractionMap.Outcome.SUCCESS
                || page.requestGeneration() != first.requestGeneration()
                || page.workspaceGeneration() != first.workspaceGeneration()
                || !page.workspaceFingerprint().equals(first.workspaceFingerprint())
                || page.documentGeneration() != first.documentGeneration()
                || page.semanticGeneration() != first.semanticGeneration()
                || !page.semanticFingerprint().equals(first.semanticFingerprint())
                || !page.document().equals(first.document())
                || !page.domains().equals(first.domains())
                || !page.projections().equals(first.projections())
                || page.page().totalRegions() != first.page().totalRegions()
                || page.page().totalInventoryFiles() != first.page().totalInventoryFiles()) {
            throw new IllegalStateException("Java interaction-map semantic identity drifted between pages");
        }
    }

    private static <T> void putUnique(
            Map<String, T> output,
            List<T> values,
            Function<T, String> identity,
            String label
    ) {
        for (T value : values) {
            String id = identity.apply(value);
            T previous = output.putIfAbsent(id, value);
            if (previous != null && !previous.equals(value)) {
                throw new IllegalStateException("Conflicting Java interaction-map " + label + " id " + id);
            }
        }
    }

    private static long saturatingAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) return Long.MAX_VALUE;
        return left + right;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    private record Cursor(long regionOffset, long inventoryOffset) {
    }
}
