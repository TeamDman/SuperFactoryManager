package ca.teamdman.sfm.client.syntax.process;

import ca.teamdman.sfm.client.syntax.SFMSyntaxHighlightLimits;
import ca.teamdman.sfm.client.syntax.SFMSyntaxHighlightRequest;
import ca.teamdman.sfm.client.syntax.SFMSyntaxHighlightResult;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cross-runtime contract proof against the currently installed Rust worker. */
class SFMSyntaxServerInstalledWorkerIntegrationTests {
    @Test
    @Timeout(30)
    void javaFramesRoundTripThroughTheInstalledRustWorker() throws Exception {
        SFMSyntaxServerSupervisor.Configuration configuration =
                SFMSyntaxServerSupervisor.Configuration.defaults();
        Process process = startInstalledWorkerOrSkip(configuration);
        ExecutorService reader = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "test-installed-syntax-worker-reader");
            thread.setDaemon(true);
            return thread;
        });
        try {
            OutputStream stdin = process.getOutputStream();
            InputStream stdout = process.getInputStream();
            SFMSyntaxServerFrameCodec.write(
                    stdin,
                    SFMSyntaxServerProtocol.hello(
                            SFMSyntaxServerSupervisor.CLIENT_NAME,
                            SFMSyntaxServerSupervisor.CLIENT_VERSION,
                            configuration.maximumFrameBytes()
                    ),
                    configuration.maximumFrameBytes()
            );

            SFMSyntaxServerProtocol.HelloFrame hello = assertInstanceOf(
                    SFMSyntaxServerProtocol.HelloFrame.class,
                    readFrame(reader, stdout, configuration.maximumFrameBytes())
            );
            assertEquals(SFMSyntaxServerProtocol.PROTOCOL_SCHEMA, hello.hello().protocolSchema());
            assertTrue(hello.hello().supportedLanguages().contains("java"));
            int negotiatedFrameBytes = Math.min(
                    configuration.maximumFrameBytes(),
                    hello.hello().maximumFrameBytes()
            );

            String source = "package example; public class Café { private int value = 1; }\n";
            SFMSyntaxHighlightRequest request = SFMSyntaxHighlightRequest.create(
                    7001,
                    3,
                    "integration:installed-worker",
                    9,
                    "java",
                    source,
                    1000
            );
            SFMSyntaxServerFrameCodec.write(
                    stdin,
                    SFMSyntaxServerProtocol.highlight(request),
                    negotiatedFrameBytes
            );

            SFMSyntaxServerProtocol.HighlightResultFrame highlighted = assertInstanceOf(
                    SFMSyntaxServerProtocol.HighlightResultFrame.class,
                    readFrame(reader, stdout, negotiatedFrameBytes)
            );
            SFMSyntaxHighlightResult result = highlighted.result();
            result.validateAgainst(request, SFMSyntaxHighlightLimits.defaults());
            assertEquals(SFMSyntaxHighlightResult.Outcome.HIGHLIGHTED, result.outcome());
            assertFalse(result.spans().isEmpty());

            SFMSyntaxServerFrameCodec.write(
                    stdin,
                    SFMSyntaxServerProtocol.shutdown("installed-worker integration test complete"),
                    negotiatedFrameBytes
            );
            assertInstanceOf(
                    SFMSyntaxServerProtocol.ShutdownFrame.class,
                    readFrame(reader, stdout, negotiatedFrameBytes)
            );
            assertTrue(process.waitFor(5, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue());
        } finally {
            closeProcess(process, Duration.ofSeconds(2));
            reader.shutdownNow();
        }
    }

    private static Process startInstalledWorkerOrSkip(
            SFMSyntaxServerSupervisor.Configuration configuration
    ) {
        try {
            return new ProcessBuilder(configuration.command())
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();
        } catch (IOException unavailable) {
            Assumptions.assumeTrue(
                    false,
                    "Installed syntax worker is unavailable: " + unavailable.getClass().getSimpleName()
            );
            throw new AssertionError("assumption must abort the test", unavailable);
        }
    }

    private static SFMSyntaxServerProtocol.ServerFrame readFrame(
            ExecutorService reader,
            InputStream input,
            int maximumFrameBytes
    ) throws Exception {
        Future<Optional<String>> read = reader.submit(() ->
                SFMSyntaxServerFrameCodec.read(input, maximumFrameBytes)
        );
        Optional<String> encoded = read.get(10, TimeUnit.SECONDS);
        if (encoded.isEmpty()) throw new EOFException("Installed syntax worker reached EOF before its response");
        return SFMSyntaxServerProtocol.decodeServerFrame(encoded.orElseThrow());
    }

    private static void closeProcess(Process process, Duration grace) throws InterruptedException {
        if (!process.isAlive()) return;
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
        }
        process.destroy();
        if (!process.waitFor(grace.toMillis(), TimeUnit.MILLISECONDS)) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor(Math.max(1, grace.toMillis()), TimeUnit.MILLISECONDS);
        }
    }
}
