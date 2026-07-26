package ca.teamdman.sfm.client.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMJavaLocalTerminalServiceTests {
    @Test
    void supportsDeterministicSafeCommandsWithoutAHostShell() {
        SFMVirtualFileSystem filesystem = new SFMVirtualFileSystem();
        filesystem.put("/instance/readme.txt", "hello from the Java fallback");
        SFMTerminalService.SFMTerminalSession session = new SFMJavaLocalTerminalService(filesystem).openSession();

        assertEquals(java.util.List.of("/"), session.execute("pwd").lines());
        assertEquals(java.util.List.of("instance/"), session.execute("ls /").lines());
        assertTrue(session.execute("cd /instance").success());
        assertEquals(java.util.List.of("readme.txt"), session.execute("ls").lines());
        assertEquals(java.util.List.of("hello from the Java fallback"), session.execute("cat readme.txt").lines());
        assertEquals(java.util.List.of("hello world"), session.execute("echo hello world").lines());
    }

    @Test
    void supportsBoundedEditsAndTypedErrors() {
        SFMTerminalService.SFMTerminalSession session = new SFMJavaLocalTerminalService().openSession();

        assertTrue(session.execute("write /notes.txt first draft").success());
        assertEquals(java.util.List.of("first draft"), session.execute("cat /notes.txt").lines());
        assertFalse(session.execute("cat /missing.txt").success());
        assertFalse(session.execute("rm -rf /").success());
    }
}
