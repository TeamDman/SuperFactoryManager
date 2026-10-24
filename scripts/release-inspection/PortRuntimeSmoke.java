// Optional real-worker smoke test used by test-workspace-port.py. No client is launched.
import ca.teamdman.sfm.client.inspection.SFMInspectionRuntime;
import ca.teamdman.sfm.client.syntax.SFMSyntaxHighlightRuntime;
import ca.teamdman.sfm.client.syntax.SFMSyntaxHighlightResult;
import ca.teamdman.sfm.client.syntax.process.SFMSyntaxServerSupervisor;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

public class PortRuntimeSmoke {
    public static void main(String[] args) throws Exception {
        System.setProperty("sfm.syntax.workerExecutable", args[0]);
        var prepared = SFMInspectionRuntime.read(Path.of(args[1]));
        if (prepared.trees().size() != 2) throw new AssertionError("Expected two prepared comparison roots");
        int surfaces = prepared.trees().stream().mapToInt(t -> t.surfaces().size()).sum();
        if (surfaces == 0) throw new AssertionError("Missing prepared diff representations");
        boolean rejected = false;
        try { SFMInspectionRuntime.read(Path.of(args[2])); }
        catch (java.io.IOException expected) { rejected = true; }
        if (!rejected) throw new AssertionError("Missing workspace was not rejected");
        String source = "public final class Sample { /* real parser */ int amount = 42; String name = \"FE\"; }\n";
        var runtime = SFMSyntaxHighlightRuntime.get();
        try (var submission = runtime.query("port-smoke", 1, "java", source)) {
            var result = submission.result().get(15, TimeUnit.SECONDS);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
            if (result.outcome() != SFMSyntaxHighlightResult.Outcome.HIGHLIGHTED || !result.complete()
                    || result.spans().size() < 5 || !result.sourceSha256().equals("sha256:" + hash))
                throw new AssertionError("Real Java highlighting failed: " + result);
            System.out.println("roots=" + prepared.trees().size() + " representations=" + surfaces
                    + " missing_input_rejected=" + rejected + " syntax_spans=" + result.spans().size()
                    + " parser=" + result.parserFingerprint() + " source_sha256=" + hash);
        } finally {
            runtime.close();
            awaitClosed(runtime);
        }
    }

    private static void awaitClosed(SFMSyntaxHighlightRuntime runtime) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            var state = runtime.workerTelemetry();
            if (state.lifecycle() == SFMSyntaxServerSupervisor.Lifecycle.CLOSED && !state.processAlive()) {
                System.out.println("worker_closed=true worker_process_alive=false");
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Syntax worker shutdown did not complete: " + runtime.workerTelemetry());
    }
}
