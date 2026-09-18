package ca.teamdman.sfm.client.render;

import ca.teamdman.sfm.common.image.SFMImageSnapshot;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TouchDisplayTextureCacheTests {
    @Test
    void unchangedContentDoesNotUploadAgainAndClearReleasesOnce() throws IOException {
        FakeTextures textures = new FakeTextures();
        TouchDisplayTextureCache cache = new TouchDisplayTextureCache(textures);
        SFMImageSnapshot red = image(0xffff0000);
        SFMImageSnapshot blue = image(0xff0000ff);

        ResourceLocation redLocation = cache.textureFor(red).orElseThrow();
        for (int frame = 0; frame < 100; frame++) {
            assertEquals(redLocation, cache.textureFor(red).orElseThrow());
        }
        assertEquals(1, textures.uploads);
        assertEquals(4, cache.gpuBytes());

        cache.textureFor(blue).orElseThrow();
        assertEquals(2, textures.uploads);
        assertEquals(2, cache.size());
        cache.clear();
        assertEquals(2, textures.released.size());
        assertEquals(0, cache.gpuBytes());
        cache.clear();
        assertEquals(2, textures.released.size());
    }

    @Test
    void boundedAdmissionDefersEvictionUntilTickBoundary() throws IOException {
        FakeTextures textures = new FakeTextures();
        TouchDisplayTextureCache cache = new TouchDisplayTextureCache(textures);
        List<SFMImageSnapshot> images = new ArrayList<>();
        for (int i = 0; i <= TouchDisplayTextureCache.MAX_TEXTURES; i++) {
            images.add(image(0xff000000 | i));
        }
        for (int i = 0; i < TouchDisplayTextureCache.MAX_TEXTURES; i++) {
            cache.textureFor(images.get(i)).orElseThrow();
        }
        assertEquals(TouchDisplayTextureCache.MAX_TEXTURES, cache.size());
        assertEquals(TouchDisplayTextureCache.MAX_TEXTURES, textures.uploads);

        // An overflow uses the static fallback until the next client tick.
        cache.textureFor(images.get(0)).orElseThrow();
        assertTrue(cache.textureFor(images.get(TouchDisplayTextureCache.MAX_TEXTURES)).isEmpty());
        assertEquals(0, textures.released.size());
        cache.maintain();
        assertEquals(1, textures.released.size());
        assertEquals(TouchDisplayTextureCache.MAX_TEXTURES - 1, cache.size());
        cache.textureFor(images.get(TouchDisplayTextureCache.MAX_TEXTURES)).orElseThrow();
        assertEquals(TouchDisplayTextureCache.MAX_TEXTURES + 1, textures.uploads);
        assertEquals(TouchDisplayTextureCache.MAX_TEXTURES, cache.size());
        assertTrue(cache.gpuBytes() <= TouchDisplayTextureCache.MAX_GPU_BYTES);
    }

    @Test
    void failedUploadIsNotRetriedOnEveryFrameButCanRetryAfterWorldClear() throws IOException {
        FakeTextures textures = new FakeTextures();
        textures.fail = true;
        TouchDisplayTextureCache cache = new TouchDisplayTextureCache(textures);
        SFMImageSnapshot image = image(0xffabcdef);

        assertTrue(cache.textureFor(image).isEmpty());
        assertTrue(cache.textureFor(image).isEmpty());
        assertEquals(1, textures.uploads);
        assertEquals(0, cache.size());

        cache.clear();
        textures.fail = false;
        assertTrue(cache.textureFor(image).isPresent());
        assertEquals(2, textures.uploads);
    }

    private static SFMImageSnapshot image(int argb) throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, argb);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", bytes));
        return SFMImageSnapshot.fromPng(bytes.toByteArray());
    }

    private static final class FakeTextures implements TouchDisplayTextureCache.TextureAccess {
        private int uploads;
        private boolean fail;
        private final List<ResourceLocation> released = new ArrayList<>();

        @Override
        public Optional<ResourceLocation> upload(SFMImageSnapshot snapshot) {
            uploads++;
            return fail ? Optional.empty() : Optional.of(new ResourceLocation("sfm", "test/" + snapshot.sha256()));
        }

        @Override
        public void release(ResourceLocation location) {
            released.add(location);
        }
    }
}
