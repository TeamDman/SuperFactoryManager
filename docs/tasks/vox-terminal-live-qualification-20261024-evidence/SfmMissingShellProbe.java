package ca.teamdman.sfm.client.terminal;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.facet.vox.ConnectionOptions;

/** Real SFM consumer and real Rust server; missing-shell failure only, not readiness. */
public final class SfmMissingShellProbe {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        AtomicInteger completions = new AtomicInteger();
        BlockingQueue<SFMTerminalPasteResult> results = new LinkedBlockingQueue<>();
        try (SFMVoxTerminalService service = new SFMVoxTerminalService(
                new InetSocketAddress("127.0.0.1", Integer.parseInt(args[0])),
                ConnectionOptions.builder().handshakeTimeout(Duration.ofSeconds(3))
                        .closeTimeout(Duration.ofSeconds(1)).build(), Duration.ofSeconds(3))) {
            check(service.pasteWithGuard("not forwarded", result -> {
                completions.incrementAndGet();
                results.add(result);
            }), "paste must be locally accepted for this test");
            SFMTerminalPasteResult result = results.poll(10, TimeUnit.SECONDS);
            check(result != null, "accepted paste must complete on failed session creation");
            check(result.disposition() == SFMTerminalPasteResult.Disposition.FAILED, "explicit FAILED paste");
            check(result.error().isPresent() && !result.error().get().retryable(), "structured terminal failure");
            check(result.error().get().code() == SFMTerminalErrorCode.DISCONNECTED, "unavailable outcome");
            check(result.preview().isEmpty() && result.contentId().isEmpty(), "failure must never become confirmation");
            check(!service.isConnected(), "missing shell cannot yield an established session");
            System.out.println("Observed service failure: " + service.failureMessage().orElse("<none>"));
            System.out.println("Observed paste failure: " + result.error().get());
            check(service.failureMessage().orElse("").contains("pwsh.exe"), "diagnostic must identify the missing runtime");
            service.close();
            check(results.poll(300, TimeUnit.MILLISECONDS) == null && completions.get() == 1,
                    "close after failure must not duplicate completion");
            System.out.println("PASS real SFM/Terminal missing-shell paste failure completes once without confirmation");
        }
    }
}
