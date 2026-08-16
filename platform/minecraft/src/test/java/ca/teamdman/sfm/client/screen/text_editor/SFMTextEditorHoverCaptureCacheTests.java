package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.client.symbol.SFMSymbolHoverIdentity;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMTextEditorHoverCaptureCacheTests {
    @Test
    void ordinaryMovementWithoutCtrlPerformsNoCaptureOrHashWork() {
        SFMTextEditorHoverCaptureCache<String> cache = new SFMTextEditorHoverCaptureCache<>();
        AtomicInteger captures = new AtomicInteger();
        AtomicInteger hashes = new AtomicInteger();

        for (int movement = 0; movement < 1_000; movement++) {
            Optional<String> value = cache.resolve(false, key(1, 10, 20), () -> capture(captures, hashes));
            assertTrue(value.isEmpty());
        }

        assertEquals(0, captures.get());
        assertEquals(0, hashes.get());
    }

    @Test
    void stationaryCtrlHoverCapturesAndHashesOncePerStableRevisionAndPosition() {
        SFMTextEditorHoverCaptureCache<String> cache = new SFMTextEditorHoverCaptureCache<>();
        AtomicInteger captures = new AtomicInteger();
        AtomicInteger hashes = new AtomicInteger();

        for (int observation = 0; observation < 1_000; observation++) {
            assertEquals("capture-1", cache.resolve(true, key(1, 10, 20),
                    () -> capture(captures, hashes)).orElseThrow());
        }
        assertEquals(1, captures.get());
        assertEquals(1, hashes.get());

        assertEquals("capture-2", cache.resolve(true, key(1, 11, 20),
                () -> capture(captures, hashes)).orElseThrow());
        assertEquals("capture-3", cache.resolve(true, key(2, 11, 20),
                () -> capture(captures, hashes)).orElseThrow());
        assertEquals(3, captures.get());
        assertEquals(3, hashes.get());

        cache.clear();
        assertEquals("capture-4", cache.resolve(true, key(2, 11, 20),
                () -> capture(captures, hashes)).orElseThrow());
        assertEquals(4, captures.get());
        assertEquals(4, hashes.get());
    }

    private static String capture(AtomicInteger captures, AtomicInteger hashes) {
        int capture = captures.incrementAndGet();
        hashes.incrementAndGet();
        return "capture-" + capture;
    }

    private static SFMTextEditorHoverCaptureCache.Key key(
            long documentGeneration,
            double pointerX,
            double pointerY
    ) {
        return new SFMTextEditorHoverCaptureCache.Key(
                new SFMSymbolHoverIdentity.EditorOrigin(
                        "screen", "workspace", "stack", "panel", "editor", 1
                ),
                documentGeneration,
                pointerX,
                pointerY
        );
    }
}
