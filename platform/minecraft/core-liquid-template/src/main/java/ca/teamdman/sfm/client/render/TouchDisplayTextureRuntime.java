package ca.teamdman.sfm.client.render;

import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.image.SFMImageSnapshot;
import ca.teamdman.sfm.common.util.SFMDist;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;

/** Render-thread owner of the texture cache and its world/reload lifecycle. */
public final class TouchDisplayTextureRuntime {
    private static final TouchDisplayTextureCache CACHE = new TouchDisplayTextureCache(
            new MinecraftTextureAccess()
    );
    private static @Nullable Level world;

    private TouchDisplayTextureRuntime() {}

    public static Optional<ResourceLocation> textureFor(SFMImageSnapshot snapshot, @Nullable Level currentWorld) {
        if (currentWorld == null) return Optional.empty();
        observeWorld(currentWorld);
        return CACHE.textureFor(snapshot);
    }

    @SFMSubscribeEvent(SFMDist.CLIENT)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        observeWorld(Minecraft.getInstance().level);
        CACHE.maintain();
    }

    @SFMSubscribeEvent(SFMDist.CLIENT)
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() != world) return;
        Minecraft.getInstance().execute(() -> {
            if (event.getLevel() == world) {
                CACHE.clear();
                world = null;
            }
        });
    }

    @SFMSubscribeEvent(SFMDist.CLIENT)
    public static void onRegisterReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) ignored ->
                Minecraft.getInstance().execute(CACHE::clear));
    }

    private static void observeWorld(@Nullable Level currentWorld) {
        if (world == currentWorld) return;
        CACHE.clear();
        world = currentWorld;
    }

    private static final class MinecraftTextureAccess implements TouchDisplayTextureCache.TextureAccess {
        @Override
        public Optional<ResourceLocation> upload(SFMImageSnapshot snapshot) {
            ResourceLocation location = new ResourceLocation("sfm", "image/" + snapshot.sha256());
            ByteBuffer encoded = MemoryUtil.memAlloc(snapshot.byteLength());
            NativeImage decoded = null;
            DynamicTexture texture = null;
            boolean registered = false;
            try {
                encoded.put(snapshot.pngBytes()).flip();
                decoded = NativeImage.read(encoded);
                if (decoded.getWidth() != snapshot.width() || decoded.getHeight() != snapshot.height()) {
                    return Optional.empty();
                }
                texture = new DynamicTexture(snapshot.width(), snapshot.height(), false);
                NativeImage pixels = texture.getPixels();
                if (pixels == null || pixels.format() != decoded.format()) {
                    return Optional.empty();
                }
                pixels.copyFrom(decoded);
                texture.upload();
                Minecraft.getInstance().getTextureManager().register(location, texture);
                registered = true;
                return Optional.of(location);
            } catch (IOException | RuntimeException failure) {
                return Optional.empty();
            } finally {
                if (decoded != null) decoded.close();
                if (!registered && texture != null) texture.close();
                MemoryUtil.memFree(encoded);
            }
        }

        @Override
        public void release(ResourceLocation location) {
            Minecraft.getInstance().getTextureManager().release(location);
        }
    }
}
