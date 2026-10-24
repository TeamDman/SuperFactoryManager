import java.net.InetSocketAddress;
import java.util.concurrent.*;
import org.facet.vox.*;
import org.facet.vox.generated.*;
import org.facet.phon.PhonCodec;
import org.facet.phon.PhonLimits;

/** Isolates schema.tracking.bindings/rpc.error.scope; never creates a PTY. */
public final class SchemaOnlyProbe {
    public static void main(String[] args) throws Exception {
        ExecutorService drivers = Executors.newCachedThreadPool();
        try (VoxConnection connection = VoxConnection.connect(
                new InetSocketAddress("127.0.0.1", Integer.parseInt(args[0])), ConnectionOptions.defaults())) {
            ServiceLane lane = TerminalProbe.open(connection, drivers);
            TerminalClient client = new TerminalClient(lane);
            byte[] reply = TerminalProbe.await(lane.call(TerminalServiceDescriptor.GET_CONTENT,
                    new byte[0], CallOptions.defaults()));
            VoxResult<TerminalContentResult, TerminalError> malformed = PhonCodec.decode(
                    TerminalGetContentResponse.ADAPTER, reply, PhonLimits.defaults());
            TerminalProbe.check(malformed.kind() == VoxResult.Kind.INVALID_PAYLOAD, "malformed-first rejection");
            System.out.println("PASS malformed-first INVALID_PAYLOAD");
            for (int i = 0; i < 2; i++) {
                TerminalProbe.missing(TerminalProbe.await(client.getContent(
                        new TerminalContentRequest("nonexistent", 0, 4096, i + 1))));
            }
            TerminalProbe.check(connection.state() == ConnectionState.OPEN, "connection remains open");
            System.out.println("PASS two subsequent typed SESSION_NOT_FOUND results on the same open lane");
        } finally {
            drivers.shutdownNow();
            drivers.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
