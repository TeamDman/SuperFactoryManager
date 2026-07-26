package ca.teamdman.sfm.client.terminal;

import java.util.Objects;

/** Client-facing seam kept independent of the transport used by the service. */
public final class SFMTerminalClient {
    private final SFMTerminalService.SFMTerminalSession session;

    public SFMTerminalClient(SFMTerminalService service) {
        this(Objects.requireNonNull(service, "service").openSession());
    }

    public SFMTerminalClient(SFMTerminalService.SFMTerminalSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    public SFMTerminalResponse execute(String command) {
        return session.execute(command);
    }

    public String workingDirectory() {
        return session.workingDirectory();
    }
}
