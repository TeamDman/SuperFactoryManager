package ca.teamdman.sfm.client.screen.text_editor;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SFMEditorLinkCursorHostTests {
    @Test
    void createsOnceSelectsAndRestoresWithoutRecreatingTheCursor() {
        RecordingApi api = new RecordingApi();
        SFMEditorLinkCursorHost host = new SFMEditorLinkCursorHost(41L, api);

        assertEquals(1, api.creations.get());
        host.setLink(true);
        host.setLink(true);
        host.setLink(false);
        host.setLink(false);

        assertEquals(1, api.creations.get(), "hover transitions must reuse one native hand cursor");
        assertEquals(List.of("set:41:73", "set:41:0"), api.events);
        assertEquals(0, api.destructions.get());
    }

    @Test
    void closeRestoresDefaultThenDestroysExactlyOnceAndIgnoresLaterUpdates() {
        RecordingApi api = new RecordingApi();
        SFMEditorLinkCursorHost host = new SFMEditorLinkCursorHost(41L, api);
        host.setLink(true);

        host.close();
        host.close();
        host.setLink(true);

        assertEquals(List.of("set:41:73", "set:41:0", "destroy:73"), api.events);
        assertEquals(1, api.destructions.get());
    }

    @Test
    void rejectsTheNullGlfwWindowHandleBeforeAllocatingNativeState() {
        RecordingApi api = new RecordingApi();
        assertThrows(IllegalArgumentException.class, () -> new SFMEditorLinkCursorHost(0L, api));
        assertEquals(0, api.creations.get());
    }

    private static final class RecordingApi implements SFMEditorLinkCursorHost.NativeApi {
        private final AtomicInteger creations = new AtomicInteger();
        private final AtomicInteger destructions = new AtomicInteger();
        private final ArrayList<String> events = new ArrayList<>();

        @Override public long createHandCursor() {
            creations.incrementAndGet();
            return 73L;
        }

        @Override public void setCursor(long window, long cursor) {
            events.add("set:" + window + ":" + cursor);
        }

        @Override public void destroyCursor(long cursor) {
            destructions.incrementAndGet();
            events.add("destroy:" + cursor);
        }
    }
}
