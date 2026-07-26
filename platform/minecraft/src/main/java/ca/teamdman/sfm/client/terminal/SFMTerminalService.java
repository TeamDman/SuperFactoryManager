package ca.teamdman.sfm.client.terminal;

/** Portable service seam. A Vox implementation can replace the Java-local service later. */
public interface SFMTerminalService {
    SFMTerminalSession openSession();

    interface SFMTerminalSession {
        SFMTerminalResponse execute(String command);

        String workingDirectory();
    }
}
