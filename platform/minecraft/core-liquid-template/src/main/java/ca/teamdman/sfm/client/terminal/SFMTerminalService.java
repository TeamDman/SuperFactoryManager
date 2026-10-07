package ca.teamdman.sfm.client.terminal;

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
/** Portable service seam shared by the explicit Java REPL and Rust terminal scene. */
{% else %}
/** Portable service seam. A Vox implementation can replace the Java-local service later. */
{% endcase %}
public interface SFMTerminalService {
    SFMTerminalSession openSession();

    interface SFMTerminalSession {
        SFMTerminalResponse execute(String command);

        String workingDirectory();
    }
}
