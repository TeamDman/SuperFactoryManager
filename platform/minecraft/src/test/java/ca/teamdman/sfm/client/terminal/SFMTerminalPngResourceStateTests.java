package ca.teamdman.sfm.client.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMTerminalPngResourceStateTests {
    private static final SFMTerminalPngResourceState.TextureKey A =
            new SFMTerminalPngResourceState.TextureKey(640, 360, "RGBA");
    private static final SFMTerminalPngResourceState.TextureKey B =
            new SFMTerminalPngResourceState.TextureKey(960, 540, "RGBA");

    @Test
    void splitPanelRenderersUseIndependentTextureRegistrations() {
        SFMTerminalPngRenderer left = new SFMTerminalPngRenderer();
        SFMTerminalPngRenderer right = new SFMTerminalPngRenderer();

        assertFalse(left.textureLocation().equals(right.textureLocation()));
    }

    @Test
    void repeatedSameSizeFramesReuseTextureAndEncodedCapacity() {
        SFMTerminalPngResourceState state = new SFMTerminalPngResourceState();

        assertEquals(SFMTerminalPngResourceState.Change.ALLOCATE, state.textureChange(A));
        state.textureCommitted(A);
        assertEquals(SFMTerminalPngResourceState.Change.REUSE, state.textureChange(A));
        assertEquals(SFMTerminalPngResourceState.Change.REUSE, state.textureChange(A));

        assertEquals(SFMTerminalPngResourceState.Change.ALLOCATE, state.encodedBufferChange(3_000));
        state.encodedBufferCommitted(SFMTerminalPngRenderer.nextEncodedCapacity(3_000));
        assertEquals(4_096, state.encodedCapacity());
        assertEquals(SFMTerminalPngResourceState.Change.REUSE, state.encodedBufferChange(4_096));
        assertEquals(SFMTerminalPngResourceState.Change.REUSE, state.encodedBufferChange(512));
    }

    @Test
    void resizeAToBToAReplacesIncompatibleTextureAndClosesExactlyOnceAtShutdown() {
        SFMTerminalPngResourceState state = new SFMTerminalPngResourceState();
        int replacements = 0;
        int closes = 0;

        assertEquals(SFMTerminalPngResourceState.Change.ALLOCATE, state.textureChange(A));
        state.textureCommitted(A);
        SFMTerminalPngResourceState.Change toB = state.textureChange(B);
        assertEquals(SFMTerminalPngResourceState.Change.REPLACE, toB);
        if (toB.closesPrevious()) {
            replacements++;
            closes++;
        }
        state.textureCommitted(B);
        SFMTerminalPngResourceState.Change backToA = state.textureChange(A);
        assertEquals(SFMTerminalPngResourceState.Change.REPLACE, backToA);
        if (backToA.closesPrevious()) {
            replacements++;
            closes++;
        }
        state.textureCommitted(A);

        if (state.textureClosed()) closes++;
        assertEquals(2, replacements);
        assertEquals(3, closes);
        assertFalse(state.textureClosed());
        assertEquals(SFMTerminalPngResourceState.Change.ALLOCATE, state.textureChange(A));
    }

    @Test
    void grownEncodedBufferIsReusedWhenPayloadReturnsToItsOriginalSize() {
        SFMTerminalPngResourceState state = new SFMTerminalPngResourceState();

        state.encodedBufferCommitted(SFMTerminalPngRenderer.nextEncodedCapacity(2_000));
        assertEquals(SFMTerminalPngResourceState.Change.REPLACE, state.encodedBufferChange(5_000));
        state.encodedBufferCommitted(SFMTerminalPngRenderer.nextEncodedCapacity(5_000));
        assertEquals(8_192, state.encodedCapacity());
        assertEquals(SFMTerminalPngResourceState.Change.REUSE, state.encodedBufferChange(2_000));

        assertTrue(state.encodedBufferClosed());
        assertFalse(state.encodedBufferClosed());
    }

    @Test
    void rendererIdentityDoesNotInvalidateCompatibleTextureStorage() {
        SFMTerminalPngResourceState state = new SFMTerminalPngResourceState();
        state.textureCommitted(A);

        SFMTerminalPngResourceState.TextureKey gpuCompatiblePixels =
                new SFMTerminalPngResourceState.TextureKey(640, 360, "RGBA");

        assertEquals(SFMTerminalPngResourceState.Change.REUSE,
                state.textureChange(gpuCompatiblePixels));
    }

    @Test
    void telemetryExposesAllocationReuseReplacementAndCloseCounts() {
        SFMTerminalPngTelemetry telemetry = new SFMTerminalPngTelemetry();

        telemetry.recordDecodedImageAllocation();
        telemetry.recordDecodedImageClose();
        telemetry.recordEncodedBufferAllocation(4_096);
        telemetry.recordEncodedBufferReuse(4_096);
        telemetry.recordEncodedBufferReplacement(8_192);
        telemetry.recordEncodedBufferClose();
        telemetry.recordDynamicTextureAllocation(10);
        telemetry.recordDynamicTextureRegistration(20);
        telemetry.recordDynamicTextureUpload(30);
        telemetry.recordDynamicTextureReuse();
        telemetry.recordDynamicTextureReplacement();
        telemetry.recordDynamicTextureClose();

        SFMTerminalPngTelemetry.Snapshot snapshot = telemetry.snapshot();
        assertEquals(1, snapshot.decodedImageAllocations());
        assertEquals(1, snapshot.decodedImageCloses());
        assertEquals(1, snapshot.encodedBufferAllocations());
        assertEquals(1, snapshot.encodedBufferReuses());
        assertEquals(1, snapshot.encodedBufferReplacements());
        assertEquals(1, snapshot.encodedBufferCloses());
        assertEquals(0, snapshot.encodedBufferCapacity());
        assertEquals(8_192, snapshot.encodedBufferCapacityMax());
        assertEquals(1, snapshot.dynamicTextureAllocations());
        assertEquals(1, snapshot.dynamicTextureRegistrations());
        assertEquals(1, snapshot.dynamicTextureUploads());
        assertEquals(1, snapshot.dynamicTextureReuses());
        assertEquals(1, snapshot.dynamicTextureReplacements());
        assertEquals(1, snapshot.dynamicTextureCloses());
    }
}
