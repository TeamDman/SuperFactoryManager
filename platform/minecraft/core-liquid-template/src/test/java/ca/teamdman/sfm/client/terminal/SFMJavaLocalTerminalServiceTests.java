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

    @Test
    void capturesPowerShellRangeAndCyanWriteHostSemantics() {
        SFMTerminalService.SFMTerminalSession session = new SFMJavaLocalTerminalService().openSession();

        SFMTerminalResponse range = session.execute("1..100");
        assertEquals(100, range.lines().size());
        assertEquals("1", range.lines().get(0));
        assertEquals("100", range.lines().get(range.lines().size() - 1));

        SFMTerminalResponse cyan = session.execute("write-host -foregroundcolor cyan \"hello, world!\"");
        assertEquals(java.util.List.of("hello, world!"), cyan.lines());
        assertEquals(SFMTerminalLine.CYAN, cyan.styledLines().get(0).color());
    }

    @Test
    void scrollbackIsBoundedAndKeepsTheViewedRowsStableWhileOutputArrives() {
        SFMTerminalScrollback scrollback = new SFMTerminalScrollback(5);
        scrollback.appendAll(java.util.List.of("one", "two", "three", "four", "five"));
        scrollback.setViewportLineCount(2);
        scrollback.scrollOlder(2);
        assertEquals(java.util.List.of("two", "three"), scrollback.visibleLines());

        scrollback.appendAll(java.util.List.of("six", "seven"));
        assertEquals(java.util.List.of("three", "four"), scrollback.visibleLines());
        assertEquals(5, scrollback.lines().size());
        assertEquals(java.util.List.of("three", "four", "five", "six", "seven"), scrollback.lines());
    }

    @Test
    void scrollbackSupportsNavigationResizeAndStaleSnapshotDetection() {
        SFMTerminalScrollback scrollback = new SFMTerminalScrollback(10);
        scrollback.appendAll(java.util.List.of("0", "1", "2", "3", "4", "5"));
        scrollback.setViewportLineCount(3);
        scrollback.scrollToTop();
        assertEquals(java.util.List.of("0", "1", "2"), scrollback.visibleLines());

        scrollback.setViewportLineCount(4);
        assertEquals(java.util.List.of("0", "1", "2", "3"), scrollback.visibleLines());
        scrollback.pageDown();
        assertTrue(scrollback.isFollowingOutput());
        assertEquals(java.util.List.of("2", "3", "4", "5"), scrollback.visibleLines());

        SFMTerminalScrollback.Snapshot snapshot = scrollback.snapshot();
        scrollback.append("6");
        assertFalse(scrollback.isCurrent(snapshot));
        scrollback.followOutput();
        assertTrue(scrollback.isFollowingOutput());
        assertEquals(java.util.List.of("3", "4", "5", "6"), scrollback.visibleLines());
    }
}
