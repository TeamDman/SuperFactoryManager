package ca.teamdman.sfm.toolchain.nfrt;

/** Actual stdio framing fixture; no graph, files, downloads or native tools. */
public final class SFMHostChannelProcessTests {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected exact contract identity");
        var channel = new SFMHostChannel(System.in, System.out, args[0]);
        for (String operation : new String[] {"graph", "seal", "complete"}) {
            String reply = channel.exchange(operation, "{\"fixture\":true}");
            if (!reply.equals("{\"accepted\":true}")) throw new IllegalStateException("Host changed acknowledgement");
        }
        System.err.println("PASS Java/Rust host stdio fixture: graph, seal, complete");
    }
}
