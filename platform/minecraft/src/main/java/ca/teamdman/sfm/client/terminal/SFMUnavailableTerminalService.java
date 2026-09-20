package ca.teamdman.sfm.client.terminal;

import java.net.InetSocketAddress;
import java.util.Optional;

/** Rust-scene placeholder used by Java-only artifacts that do not contain Vox. */
final class SFMUnavailableTerminalService implements SFMTerminalRemoteService {
    private final String failure;

    SFMUnavailableTerminalService(InetSocketAddress endpoint) {
        this.failure = "Rust/Vox terminal support is not present in this artifact ("
                + endpoint.getHostString() + ":" + endpoint.getPort() + ")";
    }

    @Override public void requestConnect() { }
    @Override public boolean isConnected() { return false; }
    @Override public boolean isConnecting() { return false; }
    @Override public Optional<String> failureMessage() { return Optional.of(failure); }
    @Override public boolean resize(int columns, int rows) { return false; }
    @Override public boolean sendKey(int keyCode, int modifiers, boolean pressed, boolean repeat) { return false; }
    @Override public boolean sendText(String text) { return false; }
    @Override public boolean sendMouse(int x, int y, int buttons, int button, boolean pressed, boolean motion,
                                       int wheelX, int wheelY) { return false; }
    @Override public Optional<SFMTerminalFrame> latestFrame() { return Optional.empty(); }
    @Override public int logicalWidth() { return 120; }
    @Override public int logicalHeight() { return 40; }
    @Override public String contentForAutomation() { throw new IllegalStateException(failure); }
    @Override public boolean cancel() { return false; }
    @Override public void reconnect() { }
    @Override public void close() { }

    @Override
    public SFMTerminalService.SFMTerminalSession openSession() {
        return new SFMTerminalService.SFMTerminalSession() {
            @Override public SFMTerminalResponse execute(String command) {
                return SFMTerminalResponse.error(failure, "vox://unavailable");
            }

            @Override public String workingDirectory() {
                return "vox://unavailable";
            }
        };
    }
}
