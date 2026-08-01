package ca.teamdman.sfm.client.terminal;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.system.MemoryUtil;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;

/** Uploads the latest Rust-owned full-frame PNG on the Minecraft render thread. */
final class SFMTerminalPngRenderer {
    private static final ResourceLocation TEXTURE = new ResourceLocation("sfm", "terminal_rust_frame");
    private static final int MAX_IMAGE_DIMENSION = 4096;
    private static final long MAX_IMAGE_PIXELS = 16L * 1024L * 1024L;

    private DynamicTexture texture;
    private long sequence = Long.MIN_VALUE;
    private int imageWidth;
    private int imageHeight;
    private boolean failed;

    boolean render(PoseStack poseStack, Minecraft minecraft, int x, int y, int width, int height,
                   Optional<SFMTerminalFrame> snapshot) {
        if (snapshot.isEmpty()) return false;
        SFMTerminalFrame frame = snapshot.get();
        if (!frame.png() || !frame.full() || !isPng(frame.payload())) return false;
        if (frame.sequence() == sequence && failed) return false;
        if (frame.sequence() != sequence) {
            try {
                upload(minecraft, frame);
            } catch (IOException | RuntimeException error) {
                sequence = frame.sequence();
                failed = true;
                return false;
            }
        }
        if (texture == null || failed || width <= 0 || height <= 0) return false;

        double scale = Math.min(width / (double) imageWidth, height / (double) imageHeight);
        int drawWidth = Math.max(1, (int) Math.floor(imageWidth * scale));
        int drawHeight = Math.max(1, (int) Math.floor(imageHeight * scale));
        int drawX = x + (width - drawWidth) / 2;
        int drawY = y + (height - drawHeight) / 2;
        minecraft.getTextureManager().bindForSetup(TEXTURE);
        RenderSystem.setShaderTexture(0, TEXTURE);
        GuiComponent.blit(poseStack, drawX, drawY, drawWidth, drawHeight,
                0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        return true;
    }

    void close(Minecraft minecraft) {
        if (texture != null) {
            minecraft.getTextureManager().register(TEXTURE, MissingTextureAtlasSprite.getTexture());
            texture = null;
        }
        sequence = Long.MIN_VALUE;
        failed = false;
    }

    private void upload(Minecraft minecraft, SFMTerminalFrame frame) throws IOException {
        byte[] payload = frame.payload();
        ByteBuffer encoded = MemoryUtil.memAlloc(payload.length);
        NativeImage image;
        try {
            encoded.put(payload).flip();
            image = NativeImage.read(encoded);
        } finally {
            MemoryUtil.memFree(encoded);
        }
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0 || width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION
                || (long) width * height > MAX_IMAGE_PIXELS) {
            image.close();
            throw new IOException("Rust terminal PNG dimensions exceed the presentation bound");
        }
        DynamicTexture next = new DynamicTexture(image);
        minecraft.getTextureManager().register(TEXTURE, next);
        texture = next;
        imageWidth = width;
        imageHeight = height;
        sequence = frame.sequence();
        failed = false;
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
