package ca.teamdman.sfm.client.terminal;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** Retained Java presenter for complete RGBA8 frames composed from full or dirty Vox events. */
final class SFMTerminalRgbaRenderer {
    private static final AtomicLong NEXT_TEXTURE_ID = new AtomicLong();
    private static final int MAX_IMAGE_DIMENSION = 4096;
    private static final long MAX_IMAGE_PIXELS = 16L * 1024L * 1024L;

    record Snapshot(long uploads, long allocations, long reuses, long closes,
                    long bytesUploaded, long uploadNanos, long staleFrames, long droppedFrames) {}

    private final ResourceLocation textureLocation = new ResourceLocation(
            "sfm", "terminal_rust_rgba/" + NEXT_TEXTURE_ID.incrementAndGet());
    private DynamicTexture texture;
    private String streamIdentity;
    private long sequence = Long.MIN_VALUE;
    private int imageWidth;
    private int imageHeight;
    private long uploads;
    private long allocations;
    private long reuses;
    private long closes;
    private long bytesUploaded;
    private long uploadNanos;
    private long staleFrames;
    private long droppedFrames;

    Snapshot telemetry() {
        return new Snapshot(uploads, allocations, reuses, closes, bytesUploaded,
                uploadNanos, staleFrames, droppedFrames);
    }

    boolean render(PoseStack poseStack, Minecraft minecraft, int x, int y, int width, int height,
                   Optional<SFMTerminalFrame> snapshot) {
        if (snapshot.isPresent()) {
            SFMTerminalFrame frame = snapshot.get();
            SFMTerminalPngRenderer.FrameOrder order = SFMTerminalPngRenderer.classifyFrame(
                    streamIdentity, sequence, frame);
            if (order == SFMTerminalPngRenderer.FrameOrder.STALE) {
                staleFrames++;
                droppedFrames++;
            } else if (frame.png() || !frame.full()) {
                droppedFrames++;
                return false;
            } else if (order.requiresUpload()) {
                if (!upload(minecraft, frame)) {
                    droppedFrames++;
                    return false;
                }
            }
        }
        if (texture == null || width <= 0 || height <= 0) return false;
        double scale = Math.min(1.0, Math.min(
                width / (double) imageWidth, height / (double) imageHeight));
        int drawWidth = Math.max(1, (int) Math.floor(imageWidth * scale));
        int drawHeight = Math.max(1, (int) Math.floor(imageHeight * scale));
        int drawX = x + (width - drawWidth) / 2;
        int drawY = y + (height - drawHeight) / 2;
        minecraft.getTextureManager().bindForSetup(textureLocation);
        RenderSystem.setShaderTexture(0, textureLocation);
        GuiComponent.blit(poseStack, drawX, drawY, drawWidth, drawHeight,
                0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        return true;
    }

    void close(Minecraft minecraft) {
        if (texture != null) {
            minecraft.getTextureManager().release(textureLocation);
            texture = null;
            closes++;
        }
        streamIdentity = null;
        sequence = Long.MIN_VALUE;
        imageWidth = 0;
        imageHeight = 0;
    }

    private boolean upload(Minecraft minecraft, SFMTerminalFrame frame) {
        int width = frame.metadata().panelWidth();
        int height = frame.metadata().panelHeight();
        byte[] payload = frame.payload();
        long expected = (long) width * height * 4L;
        if (width <= 0 || height <= 0 || width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION
                || (long) width * height > MAX_IMAGE_PIXELS || expected != payload.length) {
            return false;
        }
        long started = System.nanoTime();
        boolean replacement = texture == null || width != imageWidth || height != imageHeight;
        DynamicTexture target = texture;
        if (replacement) {
            target = new DynamicTexture(width, height, false);
            allocations++;
        } else {
            reuses++;
        }
        NativeImage pixels = target.getPixels();
        if (pixels == null || pixels.getWidth() != width || pixels.getHeight() != height) {
            if (replacement) target.close();
            return false;
        }
        int offset = 0;
        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                int red = payload[offset++] & 0xFF;
                int green = payload[offset++] & 0xFF;
                int blue = payload[offset++] & 0xFF;
                int alpha = payload[offset++] & 0xFF;
                pixels.setPixelRGBA(px, py,
                        alpha << 24 | blue << 16 | green << 8 | red);
            }
        }
        target.upload();
        if (replacement) {
            minecraft.getTextureManager().register(textureLocation, target);
            if (texture != null) closes++;
            texture = target;
        }
        uploads++;
        bytesUploaded += payload.length;
        uploadNanos += System.nanoTime() - started;
        imageWidth = width;
        imageHeight = height;
        streamIdentity = frame.streamIdentity();
        sequence = frame.sequence();
        return true;
    }
}
