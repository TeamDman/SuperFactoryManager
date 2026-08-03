package ca.teamdman.sfm.client.terminal;

import java.util.Objects;

/**
 * Render-thread-owned lifecycle decisions for the reusable PNG presentation resources.
 * Kept independent from Minecraft/GL types so replacement and close semantics are unit-testable.
 */
final class SFMTerminalPngResourceState {
    enum Change {
        ALLOCATE(false),
        REUSE(false),
        REPLACE(true);

        private final boolean closesPrevious;

        Change(boolean closesPrevious) {
            this.closesPrevious = closesPrevious;
        }

        boolean closesPrevious() {
            return closesPrevious;
        }
    }

    record TextureKey(int width, int height, String format, String backendId) {
        TextureKey {
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("Texture dimensions must be positive");
            format = Objects.requireNonNull(format, "format");
            backendId = backendId == null ? "" : backendId;
        }
    }

    private TextureKey textureKey;
    private int encodedCapacity;

    Change textureChange(TextureKey next) {
        Objects.requireNonNull(next, "next");
        if (textureKey == null) return Change.ALLOCATE;
        return textureKey.equals(next) ? Change.REUSE : Change.REPLACE;
    }

    void textureCommitted(TextureKey next) {
        textureKey = Objects.requireNonNull(next, "next");
    }

    boolean textureClosed() {
        if (textureKey == null) return false;
        textureKey = null;
        return true;
    }

    Change encodedBufferChange(int requiredCapacity) {
        if (requiredCapacity <= 0) throw new IllegalArgumentException("Encoded PNG capacity must be positive");
        if (encodedCapacity == 0) return Change.ALLOCATE;
        return encodedCapacity >= requiredCapacity ? Change.REUSE : Change.REPLACE;
    }

    void encodedBufferCommitted(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Encoded PNG capacity must be positive");
        encodedCapacity = capacity;
    }

    boolean encodedBufferClosed() {
        if (encodedCapacity == 0) return false;
        encodedCapacity = 0;
        return true;
    }

    int encodedCapacity() {
        return encodedCapacity;
    }
}
