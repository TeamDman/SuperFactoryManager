import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.*;
import org.facet.vox.*;
import org.facet.phon.PhonCodec;
import org.facet.phon.PhonLimits;
import org.facet.vox.generated.*;

/** Real Terminal TCP consumer; no Minecraft, GUI or GPU rendering. */
public final class TerminalProbe {
    static final long MAX_BYTES = 4 * 1024 * 1024;
    static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(15, TimeUnit.SECONDS);
    }
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    static <T> T success(VoxResult<T, TerminalError> result) {
        check(result.isSuccess(), "unexpected result " + result.kind()
                + (result.isApplicationError() ? ": " + result.applicationError() : ""));
        return result.success();
    }
    static void missing(VoxResult<?, TerminalError> result) {
        check(result.isApplicationError()
                && result.applicationError().code() == TerminalErrorCode.SESSION_NOT_FOUND,
                "missing/foreign session must be a typed application rejection: " + result.kind());
    }
    static ServiceLane open(VoxConnection connection, Executor executor) throws Exception {
        connection.start(executor);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (connection.state() != ConnectionState.OPEN) {
            check(!connection.closed().isDone() && System.nanoTime() < deadline,
                    "connection failed to open: " + connection.state());
            Thread.sleep(10);
        }
        ServiceLane lane = connection.openLane(TerminalServiceDescriptor.INSTANCE, LaneOptions.defaults());
        await(lane.opened());
        return lane;
    }
    static TerminalConnectRequest request(String renderer) {
        return new TerminalConnectRequest("java-consumer-probe", 80, 24,
                new TerminalSurfaceMetrics(80, 24, 800, 480, 0, 0, 0),
                new TerminalCapabilities(true, true, true, false, true, true, false, false,
                        80, 24, MAX_BYTES, "rust.cpu.fontdue", "vox"),
                1, "probe-connect", true,
                new TerminalTuningRequest(renderer, TerminalTuningMode.MANUAL,
                        800, 480, TerminalTuningMode.AUTO, 0, TerminalTuningMode.MANUAL, 80, 24));
    }
    static void png(TerminalFrameEvent frame, String session) {
        check(frame != null && frame.sessionId().equals(session), "frame session identity");
        byte[] bytes = frame.frame().payload();
        check(bytes.length > 8 && bytes.length <= MAX_BYTES && bytes[0] == (byte)137
                && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71, "bounded PNG frame");
        check(frame.backendId().equals("rust.cpu.fontdue"), "CPU renderer required");
    }
    public static void main(String[] args) throws Exception {
        InetSocketAddress endpoint = new InetSocketAddress("127.0.0.1", Integer.parseInt(args[0]));
        ExecutorService drivers = Executors.newCachedThreadPool();
        try (VoxConnection connection = VoxConnection.connect(endpoint, ConnectionOptions.defaults())) {
            ServiceLane lane = open(connection, drivers);
            TerminalClient client = new TerminalClient(lane);
            byte[] malformedReply = await(lane.call(TerminalServiceDescriptor.GET_CONTENT,
                    new byte[0], CallOptions.defaults()));
            VoxResult<TerminalContentResult, TerminalError> malformed = PhonCodec.decode(
                    TerminalGetContentResponse.ADAPTER, malformedReply, PhonLimits.defaults());
            check(malformed.kind() == VoxResult.Kind.INVALID_PAYLOAD, "empty argument payload rejected");
            System.out.println("PASS malformed payload decoded as INVALID_PAYLOAD");
            missing(await(client.getContent(new TerminalContentRequest("nonexistent", 0, 4096, 1))));
            VoxResult<TerminalConnectResult, TerminalError> rejected = await(client.connect(request("invalid-renderer")));
            check(rejected.isApplicationError() && rejected.applicationError().code()
                    == TerminalErrorCode.UNSUPPORTED_CAPABILITY, "unsupported renderer rejection");
            String session = success(await(client.connect(request("rust-cpu-fontdue")))).sessionId();
            System.out.println("PASS generated schema negotiation, renderer rejection and connect");
            try (VoxConnection foreign = VoxConnection.connect(endpoint, ConnectionOptions.defaults())) {
                TerminalClient other = new TerminalClient(open(foreign, drivers));
                missing(await(other.getContent(new TerminalContentRequest(session, 0, 4096, 2))));
            }
            System.out.println("PASS missing and cross-connection session rejection");
            VoxChannels.Pair<TerminalFrameEvent> frames = VoxChannels.channel(TerminalFrameEvent.ADAPTER);
            CompletableFuture<VoxResult<TerminalOperationResult, TerminalError>> subscription =
                    client.subscribeFrames(new TerminalSubscribeRequest(session, 0, MAX_BYTES, 3, "probe-stream"), frames.tx());
            try (VoxRx<TerminalFrameEvent> rx = frames.rx()) {
                TerminalFrameEvent initial = rx.receive(Duration.ofSeconds(15));
                png(initial, session);
                check(initial.fullResync(), "initial full resync");
                if (args.length == 1) {
                    // Connect/first frame establish transport readiness, not shell startup.
                    long readyDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                    boolean ready = false;
                    while (System.nanoTime() < readyDeadline) {
                        String text = success(await(client.getContent(
                                new TerminalContentRequest(session, 0, 8192, 4)))).text();
                        if (text.lines().anyMatch(line -> line.startsWith("PS ") && line.trim().endsWith(">"))) {
                            ready = true;
                            break;
                        }
                        Thread.sleep(20);
                    }
                    check(ready, "PowerShell prompt must be observed before sending the command");
                }
                // Construct the sentinel at runtime so echoed command text cannot satisfy it.
                success(await(client.sendText(new TerminalTextInput(session,
                        "Write-Output ([string]::Concat('VOX_JAVA_', 'READY_λ'))\r", 4, "probe-output"))));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                boolean found = false;
                while (System.nanoTime() < deadline) {
                    TerminalFrameEvent update;
                    try {
                        update = rx.receive(Duration.ofSeconds(15));
                    } catch (TimeoutException failure) {
                        System.err.println("TIMEOUT_CONTENT=" + success(await(client.getContent(
                                new TerminalContentRequest(session, 0, 8192, 5)))).text());
                        throw failure;
                    }
                    png(update, session);
                    TerminalContentResult content = success(await(client.getContent(
                            new TerminalContentRequest(session, 0, 8192, 5))));
                    if (content.text().lines().anyMatch(line -> line.trim().equals("VOX_JAVA_READY_λ"))) {
                        check(update.frameSequence() > initial.frameSequence(), "pushed frame advances");
                        found = true;
                        break;
                    }
                }
                check(found, "actual Unicode shell output through RPC and pushed CPU frames");
            }
            success(await(subscription));
            System.out.println("PASS Unicode PTY output, pushed PNG and receiver-close completion");
            success(await(client.getContent(new TerminalContentRequest(session, 0, 8192, 6))));
            success(await(client.disconnect(new TerminalDisconnectRequest(session, "probe complete", 7))));
            missing(await(client.getContent(new TerminalContentRequest(session, 0, 8192, 8))));
            System.out.println("PASS connection survives stream cancellation; disconnect invalidates session");
        } finally {
            drivers.shutdownNow();
            check(drivers.awaitTermination(5, TimeUnit.SECONDS), "connection driver cleanup");
        }
    }
}
