package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.semantic.SFMJavaInteractionMapSpatialAdapter;
import ca.teamdman.sfm.client.semantic.SFMSpatialSemanticContract;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SFMSymbolServerInstalledIntegrationTests {
    private static final String BRANCH_PROPERTY = "sfm.symbol.workerBranch";
    private static final String DISK_ITEM_RELATIVE_PATH =
            "ca/teamdman/sfm/common/item/DiskItem.java";
    private static final String DISK_ITEM_QUALIFIED_NAME =
            "ca.teamdman.sfm.common.item.DiskItem";
    private static final String OUTPUT_STATEMENT_RELATIVE_PATH =
            "ca/teamdman/sfml/ast/OutputStatement.java";
    private static final Duration HANDSHAKE_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    @Test
    void installedWorkerResolvesDefinitionsAndDecodesTheLargeInteractionMap() throws Exception {
        String executable = System.getProperty(SFMSymbolServerSupervisor.EXECUTABLE_PROPERTY, "").trim();
        String branch = System.getProperty(BRANCH_PROPERTY, "").trim();
        assumeTrue(
                !executable.isEmpty() && !branch.isEmpty(),
                "Set -D" + SFMSymbolServerSupervisor.EXECUTABLE_PROPERTY
                        + "=<path> and -D" + BRANCH_PROPERTY + "=<branch> to run this integration test"
        );

        Path executablePath = Path.of(executable).toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(executablePath),
                () -> "Configured symbol-worker executable is not a file: " + executablePath);

        SFMSymbolServerSupervisor.Configuration configuration =
                new SFMSymbolServerSupervisor.Configuration(
                        executablePath.toString(),
                        branch,
                        HANDSHAKE_TIMEOUT,
                        REQUEST_TIMEOUT,
                        SHUTDOWN_TIMEOUT,
                        SFMDefinitionWorkerFrameCodec.DEFAULT_MAXIMUM_FRAME_BYTES,
                        8,
                        16
                );
        SFMSymbolServerNavigationProvider provider =
                new SFMSymbolServerNavigationProvider(configuration);

        try {
            SFMSymbolServerProtocol.ServerHello hello = provider.start(HANDSHAKE_TIMEOUT)
                    .get(HANDSHAKE_TIMEOUT.plusSeconds(5).toSeconds(), TimeUnit.SECONDS);
            assertEquals(branch, hello.workspace().workspace().branch());

            SFMSymbolServerProtocol.SourceRootMapping mainRoot = hello.workspace().rootMappings().stream()
                    .filter(mapping -> mapping.sourceSet().equals("main"))
                    .filter(mapping -> Files.isRegularFile(
                            Path.of(mapping.canonicalAbsolutePath()).resolve(DISK_ITEM_RELATIVE_PATH)))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Worker hello did not expose the main source root containing DiskItem.java"));
            Path diskItemPath = Path.of(mainRoot.canonicalAbsolutePath())
                    .resolve(DISK_ITEM_RELATIVE_PATH)
                    .toAbsolutePath()
                    .normalize();
            String text = Files.readString(diskItemPath);
            SFMDefinitionRequest.Position position = positionOf(
                    text,
                    "class DiskItem",
                    "DiskItem"
            );
            String reportPath = mainRoot.reportRootPath().isEmpty()
                    ? DISK_ITEM_RELATIVE_PATH
                    : mainRoot.reportRootPath() + "/" + DISK_ITEM_RELATIVE_PATH;
            SFMDefinitionRequest.Document document = SFMDefinitionRequest.Document.sha256(
                    SFMPath.fromNative(diskItemPath).canonical(),
                    mainRoot.rootId(),
                    DISK_ITEM_RELATIVE_PATH,
                    reportPath,
                    mainRoot.sourceSet(),
                    text,
                    Optional.of(SFMDefinitionRequest.sha256(text))
            );

            SFMDefinitionRequest firstRequest = new SFMDefinitionRequest(
                    1,
                    1,
                    hello.workspace().workspace(),
                    document,
                    position
            );
            SFMDefinitionResult firstResult = provider.query(firstRequest).result()
                    .get(REQUEST_TIMEOUT.plusSeconds(5).toSeconds(), TimeUnit.SECONDS);
            assertResolvedDiskItem(firstRequest, firstResult);

            SFMDefinitionRequest secondRequest = firstRequest.withIdentity(2, 2);
            SFMDefinitionResult secondResult = provider.query(secondRequest).result()
                    .get(REQUEST_TIMEOUT.plusSeconds(5).toSeconds(), TimeUnit.SECONDS);
            assertResolvedDiskItem(secondRequest, secondResult);

            Path outputStatementPath = Path.of(mainRoot.canonicalAbsolutePath())
                    .resolve(OUTPUT_STATEMENT_RELATIVE_PATH)
                    .toAbsolutePath()
                    .normalize();
            String outputStatementText = Files.readString(outputStatementPath);
            String outputStatementReportPath = mainRoot.reportRootPath().isEmpty()
                    ? OUTPUT_STATEMENT_RELATIVE_PATH
                    : mainRoot.reportRootPath() + "/" + OUTPUT_STATEMENT_RELATIVE_PATH;
            SFMDefinitionRequest.Document outputStatementDocument = SFMDefinitionRequest.Document.sha256(
                    SFMPath.fromNative(outputStatementPath).canonical(),
                    mainRoot.rootId(),
                    OUTPUT_STATEMENT_RELATIVE_PATH,
                    outputStatementReportPath,
                    mainRoot.sourceSet(),
                    outputStatementText,
                    Optional.of(SFMDefinitionRequest.sha256(outputStatementText))
            );
            SFMJavaInteractionMap.Request interactionMapRequest = new SFMJavaInteractionMap.Request(
                    3,
                    3,
                    hello.workspace().workspace(),
                    outputStatementDocument
            );
            java.util.concurrent.atomic.AtomicLong interactionMapRequestIds =
                    new java.util.concurrent.atomic.AtomicLong(interactionMapRequest.requestId());
            SFMJavaInteractionMap.Result interactionMap = SFMJavaInteractionMapPager
                    .collect(
                            interactionMapRequest,
                            interactionMapRequestIds::incrementAndGet,
                            request -> {
                                SFMSymbolServerSupervisor.InteractionMapSubmission page =
                                        provider.queryInteractionMap(request);
                                return new SFMJavaInteractionMapPager.PageSubmission(
                                        page.result(), page.cancellation());
                            }
                    )
                    .result()
                    .get(REQUEST_TIMEOUT.plusSeconds(5).toSeconds(), TimeUnit.SECONDS);
            assertEquals(SFMJavaInteractionMap.Outcome.SUCCESS, interactionMap.outcome());
            assertTrue(interactionMap.matches(interactionMapRequest));
            assertTrue(interactionMap.regions().size() > 1_000,
                    () -> "OutputStatement interaction map was unexpectedly small: "
                            + interactionMap.regions().size());
            assertTrue(interactionMap.outlinks().size() > 1_000,
                    () -> "OutputStatement interaction-map outlinks were unexpectedly small: "
                            + interactionMap.outlinks().size());
            long adapterStarted = System.nanoTime();
            SFMJavaInteractionMapSpatialAdapter adapter =
                    new SFMJavaInteractionMapSpatialAdapter(outputStatementText, interactionMap);
            long adapterMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - adapterStarted);
            assertTrue(adapter.atUtf16(outputStatementText.indexOf("OutputStatement")).isPresent());
            for (String symbol : java.util.List.of("ProgramContext", "String", "Object", "StringBuilder")) {
                assertDefinitionRelation(interactionMap, adapter, outputStatementText, symbol);
            }
            assertDefinitionRelation(
                    interactionMap,
                    adapter,
                    outputStatementText,
                    "amountAvailableToMove",
                    1
            );
            assertTrue(adapterMillis < 5_000,
                    () -> "OutputStatement spatial adapter construction froze for " + adapterMillis + "ms");

            SFMSymbolServerSupervisor.Telemetry telemetry = provider.telemetry();
            assertEquals(SFMSymbolServerSupervisor.Lifecycle.READY, telemetry.lifecycle());
            assertEquals(1, telemetry.launchAttempts());
            assertEquals(1, telemetry.sessionsReady());
            assertEquals(0, telemetry.launchFailures());
            assertEquals(0, telemetry.restarts());
            assertTrue(telemetry.submitted() >= 4);
            assertEquals(telemetry.submitted(), telemetry.completed());
            assertEquals(0, telemetry.pendingDefinitions());
            assertTrue(telemetry.processAlive());
        } finally {
            provider.close();
            provider.termination().get(
                    SHUTDOWN_TIMEOUT.plusSeconds(5).toSeconds(),
                    TimeUnit.SECONDS
            );
        }

        SFMSymbolServerSupervisor.Telemetry closed = provider.telemetry();
        assertEquals(SFMSymbolServerSupervisor.Lifecycle.CLOSED, closed.lifecycle());
        assertFalse(closed.processAlive());
    }

    private static void assertDefinitionRelation(
            SFMJavaInteractionMap.Result interactionMap,
            SFMJavaInteractionMapSpatialAdapter adapter,
            String source,
            String symbol
    ) {
        assertDefinitionRelation(interactionMap, adapter, source, symbol, 0);
    }

    private static void assertDefinitionRelation(
            SFMJavaInteractionMap.Result interactionMap,
            SFMJavaInteractionMapSpatialAdapter adapter,
            String source,
            String symbol,
            int occurrence
    ) {
        int utf16Offset = exactJavaToken(source, symbol, occurrence);
        assertTrue(utf16Offset >= 0, () -> "OutputStatement fixture no longer contains " + symbol);
        long utf8Offset = source.substring(0, utf16Offset).getBytes(StandardCharsets.UTF_8).length;
        String containingRegions = interactionMap.regions().stream()
                .filter(region -> region.containsByte(utf8Offset))
                .map(region -> region.semanticKind() + "[" + region.startByte() + ".." + region.endByte() + "]="
                        + interactionMap.classification(region.id())
                        .map(SFMJavaInteractionMap.Classification::navigationOutlinkIds)
                        .orElseGet(java.util.List::of))
                .collect(java.util.stream.Collectors.joining(", "));
        var semantics = adapter.atUtf16(utf16Offset)
                .orElseThrow(() -> new AssertionError(symbol + " has no semantic interaction-map region"));
        assertTrue(semantics.outlinks().stream()
                        .anyMatch(outlink -> outlink.intent() == SFMSpatialSemanticContract.Intent.NAVIGATE
                                && outlink.relationKind().equals("definition")),
                () -> symbol + " semantic region has no definition navigation relation; containing regions: "
                        + containingRegions);
    }

    private static int exactJavaToken(String source, String token, int occurrence) {
        if (occurrence < 0) throw new IllegalArgumentException("occurrence must not be negative");
        int offset = 0;
        int matched = 0;
        while (offset < source.length()) {
            int found = source.indexOf(token, offset);
            if (found < 0) return -1;
            int after = found + token.length();
            boolean startsAtBoundary = found == 0 || !Character.isJavaIdentifierPart(source.charAt(found - 1));
            boolean endsAtBoundary = after == source.length()
                    || !Character.isJavaIdentifierPart(source.charAt(after));
            if (startsAtBoundary && endsAtBoundary) {
                if (matched == occurrence) return found;
                matched++;
            }
            offset = found + 1;
        }
        return -1;
    }

    private static void assertResolvedDiskItem(
            SFMDefinitionRequest request,
            SFMDefinitionResult result
    ) {
        assertEquals(request.requestId(), result.requestId());
        assertEquals(request.requestGeneration(), result.requestGeneration());
        assertEquals(request.workspace().workspaceGeneration(), result.workspaceGeneration());
        assertTrue(result.matches(request));
        assertEquals(SFMDefinitionResult.Outcome.SUCCESS, result.outcome());
        assertTrue(
                result.definitions().stream().anyMatch(definition ->
                        definition.symbol().qualifiedName().equals(DISK_ITEM_QUALIFIED_NAME)),
                () -> "DiskItem definition was absent from result: " + result.definitions()
        );
    }

    private static SFMDefinitionRequest.Position positionOf(
            String text,
            String marker,
            String token
    ) {
        int markerOffset = text.indexOf(marker);
        assertTrue(markerOffset >= 0, () -> "Source marker was not found: " + marker);
        int tokenOffsetInMarker = marker.indexOf(token);
        assertTrue(tokenOffsetInMarker >= 0, () -> "Token is not part of source marker: " + token);
        int offset = markerOffset + tokenOffsetInMarker;

        long line = 1;
        int lineStart = 0;
        for (int index = 0; index < offset; index++) {
            if (text.charAt(index) == '\n') {
                line++;
                lineStart = index + 1;
            }
        }
        long column = text.codePointCount(lineStart, offset) + 1L;
        return SFMDefinitionRequest.Position.fromText(text, line, column);
    }
}
