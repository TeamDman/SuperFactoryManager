package ca.teamdman.sfm.client.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMTerminalFrameIdentityTests {
    @Test
    void repeatedSequenceInDifferentStreamRequiresUploadAndPresentationLog() {
        SFMTerminalFrame replacement = frame("stream-b", 7);

        assertEquals(SFMTerminalPngRenderer.FrameOrder.STREAM_CHANGED,
                SFMTerminalPngRenderer.classifyFrame("stream-a", 7, replacement));
        assertTrue(SFMTerminalPngRenderer.classifyFrame("stream-a", 7, replacement).requiresUpload());
        assertTrue(SFMTerminalPanel.isNewPresentation("stream-a", 7, replacement));
    }

    @Test
    void lowerSequenceInSameStreamIsRejectedAsStale() {
        SFMTerminalPngRenderer.FrameOrder order =
                SFMTerminalPngRenderer.classifyFrame("stream-a", 8, frame("stream-a", 7));

        assertEquals(SFMTerminalPngRenderer.FrameOrder.STALE, order);
        assertFalse(order.requiresUpload());
    }

    @Test
    void lowerSequenceInReplacementStreamStillRequiresFullUpload() {
        SFMTerminalPngRenderer.FrameOrder order =
                SFMTerminalPngRenderer.classifyFrame("stream-a", 8, frame("stream-b", 1));

        assertEquals(SFMTerminalPngRenderer.FrameOrder.STREAM_CHANGED, order);
        assertTrue(order.requiresUpload());
    }

    @Test
    void advancingWithinOneStreamRequiresUpload() {
        SFMTerminalPngRenderer.FrameOrder order =
                SFMTerminalPngRenderer.classifyFrame("stream-a", 7, frame("stream-a", 8));

        assertEquals(SFMTerminalPngRenderer.FrameOrder.ADVANCED, order);
        assertTrue(order.requiresUpload());
    }

    @Test
    void duplicateWithinOneStreamRetainsTextureAndDoesNotRelog() {
        SFMTerminalFrame duplicate = frame("stream-a", 7);

        assertEquals(SFMTerminalPngRenderer.FrameOrder.DUPLICATE,
                SFMTerminalPngRenderer.classifyFrame("stream-a", 7, duplicate));
        assertFalse(SFMTerminalPanel.isNewPresentation("stream-a", 7, duplicate));
    }

    @Test
    void compatibilityConstructorUsesCorrelationAsStreamIdentity() {
        SFMTerminalFrameMetadata metadata = new SFMTerminalFrameMetadata(
                1, 80, 24, 640, 360, 8, 15, 15,
                "rust.cpu.fontdue", "vox.txrx",
                0, 0, 0, 0, 0, 0, 0, 0,
                "subscription/42");

        SFMTerminalFrame frame = new SFMTerminalFrame(1, true, true, png(), metadata);

        assertEquals("subscription/42", frame.streamIdentity());
    }

    private static SFMTerminalFrame frame(String streamIdentity, long sequence) {
        return new SFMTerminalFrame(sequence, true, true, png(), null, streamIdentity);
    }

    private static byte[] png() {
        return new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    }
}
