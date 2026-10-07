package ca.teamdman.sfm.client.raster;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** Minecraft adapter; callers must perform admission and run on the render thread. */
public final class MinecraftRasterTextureAccess implements TouchDisplayRasterTextureCache.TextureAccess<MinecraftRasterTextureAccess.Texture> {
    private static final AtomicLong NEXT_TEXTURE = new AtomicLong();
    public record Texture(ResourceLocation location, DynamicTexture texture) {}

    @Override
    public Optional<Texture> create(TouchDisplayRasterInbox.WriterLease lease, TouchDisplayRasterFrame.Image image) {
        DynamicTexture texture = null;
        boolean registered = false;
        try {
            texture = new DynamicTexture(image.width(), image.height(), false);
            if (!writePixels(texture, image)) return Optional.empty();
            ResourceLocation location = new ResourceLocation("sfm", "live_raster/" + NEXT_TEXTURE.incrementAndGet());
            Minecraft.getInstance().getTextureManager().register(location, texture);
            registered = true;
            return Optional.of(new Texture(location, texture));
        } catch (RuntimeException failure) {
            return Optional.empty();
        } finally {
            if (!registered && texture != null) texture.close();
        }
    }

    @Override
    public boolean update(Texture texture, TouchDisplayRasterFrame.Image image) {
        try {
            return writePixels(texture.texture(), image);
        } catch (RuntimeException failure) {
            return false;
        }
    }

    @Override
    public void release(Texture texture) {
        Minecraft.getInstance().getTextureManager().release(texture.location());
    }

    @MCVersionDependentBehaviour
    private static boolean writePixels(DynamicTexture texture, TouchDisplayRasterFrame.Image image) {
        NativeImage pixels = texture.getPixels();
        if (pixels == null || pixels.getWidth() != image.width() || pixels.getHeight() != image.height()) return false;
        ByteBuffer rgba = image.rgba();
        for (int y = 0; y < image.height(); y++) {
            for (int x = 0; x < image.width(); x++) {
                // NativeImage's 1.19.2 RGBA-named integer API uses ABGR packing.
                int packed = Byte.toUnsignedInt(rgba.get())
                        | Byte.toUnsignedInt(rgba.get()) << 8
                        | Byte.toUnsignedInt(rgba.get()) << 16
                        | Byte.toUnsignedInt(rgba.get()) << 24;
                pixels.setPixelRGBA(x, y, packed);
            }
        }
        texture.upload();
        return true;
    }
}
