package ca.teamdman.sfm.common.resourcetype;

import ca.teamdman.sfm.common.block.BufferBlockTier;
import ca.teamdman.sfm.common.blockentity.BufferBlockEntityContents;
import ca.teamdman.sfm.common.capability.IImageHandler;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.image.SFMImageSnapshot;
import ca.teamdman.sfm.common.value.SFMValue;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMImageResourceTests {
    @Test
    void oneUnitImageTransferPreservesAtomicInteractionStateAndSimulation() throws IOException {
        ImageResourceType type = new ImageResourceType(new SFMBlockCapabilityKind<>(null));
        AtomicInteger mutations = new AtomicInteger();
        IImageHandler source = type.createHandlerForBufferBlock(
                new BufferBlockEntityContents(BufferBlockTier.Unit, mutations::incrementAndGet)
        );
        SFMValue state = SFMValue.object(Map.of("button", SFMValue.of("red")));
        SFMImageStack image = SFMImageStack.of(png(), state);

        assertEquals(1, type.getAmount(image));
        assertEquals(1, type.getMaxStackSize(image));
        assertEquals("sfm:image", type.getRegistryKeyForStack(image).toString());
        assertTrue(type.isEmpty(type.insert(source, 0, image, true)));
        assertTrue(type.isHandlerEmpty(source));
        assertEquals(0, mutations.get());

        assertTrue(type.isEmpty(type.insert(source, 0, image, false)));
        assertEquals(1, mutations.get());
        assertFalse(type.isHandlerEmpty(source));
        assertSame(image, type.getStackInSlot(source, 0));
        assertEquals(state, type.getStackInSlot(source, 0).interactionState());
        assertSame(image, type.extract(source, 0, 1, true));
        assertFalse(type.isHandlerEmpty(source));
        assertSame(image, type.extract(source, 0, 1, false));
        assertTrue(type.isHandlerEmpty(source));
        assertEquals(2, mutations.get());
    }

    private static SFMImageSnapshot png() throws IOException {
        BufferedImage pixels = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        pixels.setRGB(0, 0, 0xFFFF0000);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(pixels, "png", bytes));
        return SFMImageSnapshot.fromPng(bytes.toByteArray());
    }
}
