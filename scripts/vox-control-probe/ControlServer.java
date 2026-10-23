import ca.teamdman.sfm.client.control.generated.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.facet.vox.*;

/** Real TCP/Phon/Vox SFM contract fixture. This is deliberately not Minecraft. */
public final class ControlServer implements SfmControlHandler {
    private static final String INSTANCE = "sfm-wire-fixture";
    private static final String TOKEN = "fixture-token-not-a-game-credential";
    private static final String NONCE = "fixture-process-nonce";
    private static final long PID = ProcessHandle.current().pid();

    private static <T> CompletableFuture<VoxResult<T, SfmControlError>> result(
            String token, int version, String requestId, T value) {
        if (!TOKEN.equals(token)) return error(SfmControlErrorCode.AUTHENTICATION_FAILED, requestId);
        if (version != 2) return error(SfmControlErrorCode.PROTOCOL_MISMATCH, requestId);
        return CompletableFuture.completedFuture(VoxResult.success(value));
    }
    private static <T> CompletableFuture<VoxResult<T, SfmControlError>> error(
            SfmControlErrorCode code, String requestId) {
        return CompletableFuture.completedFuture(VoxResult.applicationError(
                new SfmControlError(code, "fixture rejection", false, requestId)));
    }
    @Override public CompletableFuture<VoxResult<SfmControlPingResult, SfmControlError>> ping(
            CallContext context, SfmControlPingRequest request) {
        return result(request.authenticationToken(), request.protocolVersion(), request.requestId(),
                new SfmControlPingResult(INSTANCE, PID, NONCE, 2, request.requestId()));
    }
    @Override public CompletableFuture<VoxResult<SfmControlDescribeResult, SfmControlError>> describe(
            CallContext context, SfmControlDescribeRequest request) {
        return result(request.authenticationToken(), request.protocolVersion(), request.requestId(),
                new SfmControlDescribeResult(INSTANCE, PID, NONCE, 2, "wire-fixture", "not-minecraft",
                        SfmControlLifecycle.READY, true, true, 1, false, "", false, "",
                        List.of("client-action.invoke"), request.requestId()));
    }
    @Override public CompletableFuture<VoxResult<SfmControlInvokeClientActionResult, SfmControlError>> invokeClientAction(
            CallContext context, SfmControlInvokeClientActionRequest request) {
        return result(request.authenticationToken(), request.protocolVersion(), request.requestId(),
                new SfmControlInvokeClientActionResult(INSTANCE, PID, request.requestId(), "sfm:echo", 1,
                        request.actionTokens(), false, "", "", false, "", false, 0));
    }
    @Override public CompletableFuture<VoxResult<SfmControlExplorerOperationResult, SfmControlError>> explorerOperation(
            CallContext context, SfmControlExplorerOperationRequest request) {
        return error(SfmControlErrorCode.ACTION_UNAVAILABLE, request.requestId());
    }
    public static void main(String[] args) throws Exception {
        Path descriptor = Path.of(args[0]);
        Files.createDirectories(descriptor.getParent());
        try (ServerSocket listener = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))) {
            listener.setSoTimeout(60_000);
            Files.writeString(descriptor, """
                    {"schema":"sfm.game-instance/1","protocol_version":2,
                    "instance_id":"%s","process_id":%d,"process_start_nonce":"%s",
                    "transport":"tcp","host":"127.0.0.1","port":%d,
                    "authentication_token":"%s","created_at_epoch_millis":1}
                    """.formatted(INSTANCE, PID, NONCE, listener.getLocalPort(), TOKEN));
            System.out.println("READY: generated SFM service fixture; not Minecraft");
            for (int i = 0; i < 12; i++) {
                var socket = listener.accept();
                Thread worker = new Thread(() -> {
                    try (socket; var connection = VoxConnection.accept(socket,
                            new ServiceRegistry().register(new SfmControlDispatcher(new ControlServer())),
                            ConnectionOptions.builder().handshakeTimeout(Duration.ofSeconds(3))
                                    .idleTimeout(Duration.ofSeconds(10)).build())) {
                        connection.drive();
                    } catch (Exception failure) {
                        System.err.println("Fixture connection closed: " + failure);
                    }
                }, "sfm-wire-fixture");
                worker.setDaemon(true);
                worker.start();
            }
        } finally {
            Files.deleteIfExists(descriptor);
        }
    }
}
