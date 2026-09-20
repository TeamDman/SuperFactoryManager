package ca.teamdman.sfm.client.terminal;

/** Portable service seam shared by the explicit Java REPL and Rust terminal scene. */
public interface SFMTerminalService {
    SFMTerminalSession openSession();

    interface SFMTerminalSession {
        SFMTerminalResponse execute(String command);

        String workingDirectory();
    }
}
