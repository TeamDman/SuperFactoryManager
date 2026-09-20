package ca.teamdman.sfm.client.terminal;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** Uploads the latest Rust-owned full-frame PNG on the Minecraft render thread. */
final class SFMTerminalPngRenderer {
    private static final AtomicLong NEXT_TEXTURE_ID = new AtomicLong();
    private static final int MAX_IMAGE_DIMENSION = 4096;
    private static final long MAX_IMAGE_PIXELS = 16L * 1024L * 1024L;
    private static final int MIN_ENCODED_BUFFER_CAPACITY = 4 * 1024;
    private static final int MAX_ENCODED_PNG_BYTES = 64 * 1024 * 1024;

    private final ResourceLocation textureLocation = new ResourceLocation(
            "sfm", "terminal_rust_frame/" + NEXT_TEXTURE_ID.incrementAndGet());
    private final SFMTerminalPngResourceState resources = new SFMTerminalPngResourceState();
    private final SFMTerminalPngTelemetry telemetry = new SFMTerminalPngTelemetry();
    private DynamicTexture texture;
    private ByteBuffer encodedBuffer;
    private String streamIdentity;
    private long sequence = Long.MIN_VALUE;
    private int imageWidth;
    private int imageHeight;
    private boolean failed;

    SFMTerminalPngTelemetry.Snapshot telemetry() {
        return telemetry.snapshot();
    }

    ResourceLocation textureLocation() {
        return textureLocation;
    }

    boolean render(PoseStack poseStack, Minecraft minecraft, int x, int y, int width, int height,
                   double localToPhysicalScaleX, double localToPhysicalScaleY,
                   Optional<SFMTerminalFrame> snapshot) {
        long startedNanos = System.nanoTime();
        boolean presented = false;
        try {
            if (snapshot.isPresent()) {
                SFMTerminalFrame frame = snapshot.get();
                FrameOrder order = classifyFrame(streamIdentity, sequence, frame);
                if (order == FrameOrder.STALE) {
                    telemetry.recordStaleFrame();
                    telemetry.recordDroppedFrame();
                } else {
                    if (!frame.png() || !frame.full()) {
                        telemetry.recordDroppedFrame();
                        return false;
                    }
                    if (order == FrameOrder.DUPLICATE && failed) return false;
                    if (order.requiresUpload()) {
                        byte[] payload = frame.payload();
                        if (!isPng(payload)) {
                            telemetry.recordDroppedFrame();
                            return false;
                        }
                        try {
                            if (failed) telemetry.recordCoalescedFrame();
                            upload(minecraft, frame, payload);
                        } catch (IOException | RuntimeException error) {
                            streamIdentity = frame.streamIdentity();
                            sequence = frame.sequence();
                            failed = true;
                            telemetry.recordUploadFailure();
                            telemetry.recordDroppedFrame();
                            return false;
                        }
                    }
                }
            }
            if (texture == null || failed || width <= 0 || height <= 0) return false;

            // Never enlarge a smaller Rust frame in Java. The bridge now
            // carries the physical target so Rust can increase its font size;
            // stretching here only creates blur and hides the real metrics.
            SFMTerminalImageLayout layout = SFMTerminalImageLayout.fitPhysical(
                    x, y, width, height, imageWidth, imageHeight,
                    localToPhysicalScaleX, localToPhysicalScaleY);
            minecraft.getTextureManager().bindForSetup(textureLocation);
            RenderSystem.setShaderTexture(0, textureLocation);
            GuiComponent.blit(poseStack, layout.x(), layout.y(), layout.width(), layout.height(),
                    0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
            telemetry.recordPresented(sequence);
            presented = true;
            return true;
        } finally {
            telemetry.recordRender(System.nanoTime() - startedNanos, presented);
        }
    }

    void close(Minecraft minecraft) {
        if (texture != null) {
            minecraft.getTextureManager().release(textureLocation);
            if (resources.textureClosed()) {
                telemetry.recordDynamicTextureClose();
            }
            texture = null;
        }
        if (encodedBuffer != null) {
            MemoryUtil.memFree(encodedBuffer);
            encodedBuffer = null;
            if (resources.encodedBufferClosed()) telemetry.recordEncodedBufferClose();
        }
        streamIdentity = null;
        sequence = Long.MIN_VALUE;
        imageWidth = 0;
        imageHeight = 0;
        failed = false;
    }

    private void upload(Minecraft minecraft, SFMTerminalFrame frame, byte[] payload) throws IOException {
        long uploadStartedNanos = System.nanoTime();
        telemetry.recordUploadStarted();
        try {
            ByteBuffer encoded = encodedPayload(payload);
            NativeImage image = null;
            try {
                long decodeStartedNanos = System.nanoTime();
                try {
                    image = NativeImage.read(encoded);
                    telemetry.recordDecodedImageAllocation();
                } finally {
                    telemetry.recordPngDecode(System.nanoTime() - decodeStartedNanos);
                }
                int width = image.getWidth();
                int height = image.getHeight();
                if (width <= 0 || height <= 0 || width > MAX_IMAGE_DIMENSION
                        || height > MAX_IMAGE_DIMENSION
                        || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new IOException("Rust terminal PNG dimensions exceed the presentation bound");
                }
                SFMTerminalPngResourceState.TextureKey key = new SFMTerminalPngResourceState.TextureKey(
                        width, height, image.format().name());
                SFMTerminalPngResourceState.Change change = resources.textureChange(key);
                if (change == SFMTerminalPngResourceState.Change.REUSE) {
                    uploadIntoExistingTexture(image);
                    telemetry.recordDynamicTextureReuse();
                } else {
                    replaceTexture(minecraft, image, key, change);
                }
                imageWidth = width;
                imageHeight = height;
                streamIdentity = frame.streamIdentity();
                sequence = frame.sequence();
                failed = false;
            } finally {
                if (image != null) {
                    image.close();
                    telemetry.recordDecodedImageClose();
                }
            }
        } finally {
            telemetry.recordUploadFinished(System.nanoTime() - uploadStartedNanos);
        }
    }

    private ByteBuffer encodedPayload(byte[] payload) throws IOException {
        if (payload.length == 0 || payload.length > MAX_ENCODED_PNG_BYTES) {
            throw new IOException("Rust terminal PNG payload exceeds the presentation bound");
        }
        SFMTerminalPngResourceState.Change change = resources.encodedBufferChange(payload.length);
        if (change != SFMTerminalPngResourceState.Change.REUSE) {
            int capacity = nextEncodedCapacity(payload.length);
            ByteBuffer next = MemoryUtil.memAlloc(capacity);
            if (encodedBuffer != null) {
                MemoryUtil.memFree(encodedBuffer);
                telemetry.recordEncodedBufferClose();
            }
            encodedBuffer = next;
            resources.encodedBufferCommitted(capacity);
            if (change == SFMTerminalPngResourceState.Change.ALLOCATE) {
                telemetry.recordEncodedBufferAllocation(capacity);
            } else {
                telemetry.recordEncodedBufferReplacement(capacity);
            }
        } else {
            telemetry.recordEncodedBufferReuse(resources.encodedCapacity());
        }
        encodedBuffer.clear();
        encodedBuffer.put(payload).flip();
        return encodedBuffer;
    }

    private void uploadIntoExistingTexture(NativeImage image) throws IOException {
        if (texture == null) throw new IOException("Reusable terminal texture is unavailable");
        NativeImage pixels = texture.getPixels();
        if (pixels == null
                || pixels.getWidth() != image.getWidth()
                || pixels.getHeight() != image.getHeight()
                || pixels.format() != image.format()) {
            throw new IOException("Reusable terminal texture storage is incompatible");
        }
        pixels.copyFrom(image);
        uploadTexture(texture);
    }

    private void replaceTexture(
            Minecraft minecraft,
            NativeImage image,
            SFMTerminalPngResourceState.TextureKey key,
            SFMTerminalPngResourceState.Change change
    ) throws IOException {
        long allocationStartedNanos = System.nanoTime();
        DynamicTexture next = new DynamicTexture(image.getWidth(), image.getHeight(), false);
        telemetry.recordDynamicTextureAllocation(System.nanoTime() - allocationStartedNanos);
        boolean registered = false;
        try {
            NativeImage pixels = next.getPixels();
            if (pixels == null || pixels.format() != image.format()) {
                throw new IOException("Allocated terminal texture storage is incompatible");
            }
            pixels.copyFrom(image);
            uploadTexture(next);
            long registrationStartedNanos = System.nanoTime();
            // TextureManager.register replaces and closes the old texture at this
            // renderer-specific location. Do not close it again here.
            minecraft.getTextureManager().register(textureLocation, next);
            telemetry.recordDynamicTextureRegistration(System.nanoTime() - registrationStartedNanos);
            registered = true;
            if (change.closesPrevious()) {
                telemetry.recordDynamicTextureReplacement();
                telemetry.recordDynamicTextureClose();
            }
            texture = next;
            resources.textureCommitted(key);
        } finally {
            if (!registered) {
                next.close();
                telemetry.recordDynamicTextureClose();
            }
        }
    }

    private void uploadTexture(DynamicTexture target) {
        long startedNanos = System.nanoTime();
        try {
            target.upload();
        } finally {
            telemetry.recordDynamicTextureUpload(System.nanoTime() - startedNanos);
        }
    }

    static int nextEncodedCapacity(int requiredCapacity) {
        if (requiredCapacity <= 0 || requiredCapacity > MAX_ENCODED_PNG_BYTES) {
            throw new IllegalArgumentException("Encoded PNG capacity is outside the presentation bound");
        }
        int capacity = MIN_ENCODED_BUFFER_CAPACITY;
        while (capacity < requiredCapacity && capacity <= MAX_ENCODED_PNG_BYTES / 2) capacity *= 2;
        return Math.max(requiredCapacity, capacity);
    }

    enum FrameOrder {
        STREAM_CHANGED,
        ADVANCED,
        DUPLICATE,
        STALE;

        boolean requiresUpload() {
            return this == STREAM_CHANGED || this == ADVANCED;
        }
    }

    static FrameOrder classifyFrame(
            String currentStreamIdentity,
            long currentSequence,
            SFMTerminalFrame frame) {
        if (!Objects.equals(currentStreamIdentity, frame.streamIdentity())) {
            return FrameOrder.STREAM_CHANGED;
        }
        if (frame.sequence() < currentSequence) return FrameOrder.STALE;
        if (frame.sequence() == currentSequence) return FrameOrder.DUPLICATE;
        return FrameOrder.ADVANCED;
    }

    private static boolean isPng(byte[] payload) {
        return payload != null
                && payload.length >= 8
                && payload[0] == (byte) 0x89
                && payload[1] == 0x50
                && payload[2] == 0x4E
                && payload[3] == 0x47
                && payload[4] == 0x0D
                && payload[5] == 0x0A
                && payload[6] == 0x1A
                && payload[7] == 0x0A;
    }
}
