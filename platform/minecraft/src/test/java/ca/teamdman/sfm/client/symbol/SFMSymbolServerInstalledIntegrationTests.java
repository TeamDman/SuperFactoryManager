package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.client.explorer.SFMPath;
import org.junit.jupiter.api.Test;

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
    private static final Duration HANDSHAKE_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    @Test
    void installedWorkerResolvesDiskItemTwiceThroughProductionProvider() throws Exception {
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

            SFMSymbolServerSupervisor.Telemetry telemetry = provider.telemetry();
            assertEquals(SFMSymbolServerSupervisor.Lifecycle.READY, telemetry.lifecycle());
            assertEquals(1, telemetry.launchAttempts());
            assertEquals(1, telemetry.sessionsReady());
            assertEquals(0, telemetry.launchFailures());
            assertEquals(0, telemetry.restarts());
            assertEquals(2, telemetry.submitted());
            assertEquals(2, telemetry.completed());
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
