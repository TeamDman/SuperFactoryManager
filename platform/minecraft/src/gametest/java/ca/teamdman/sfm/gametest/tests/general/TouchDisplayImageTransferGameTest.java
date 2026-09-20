package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.common.block.BufferBlock;
import ca.teamdman.sfm.common.block.TouchDisplayBlock;
import ca.teamdman.sfm.common.blockentity.BufferBlockEntity;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.blockentity.TouchDisplayBlockEntity;
import ca.teamdman.sfm.common.capability.IImageHandler;
import ca.teamdman.sfm.common.image.SFMImageSnapshot;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.SFMImageStack;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Real SFML IMAGE:: movement from a durable buffer into the consuming display sink. */
@SFMGameTest(SFMDist.DEDICATED_SERVER)
public final class TouchDisplayImageTransferGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "5x3x1";
    }

    @Override
    public int maxTicks() {
        return 20 * 10;
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(0, 2, 0);
        BlockPos managerPos = new BlockPos(2, 2, 0);
        BlockPos displayPos = new BlockPos(4, 2, 0);
        helper.setBlock(sourcePos, SFMBlocks.BUFFER_BLOCK.get());
        helper.setBlock(new BlockPos(1, 2, 0), SFMBlocks.CABLE.get());
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        helper.setBlock(new BlockPos(3, 2, 0), SFMBlocks.CABLE.get());
        helper.setBlock(displayPos, SFMBlocks.TOUCH_DISPLAY.get().defaultBlockState()
                .setValue(TouchDisplayBlock.FACING, Direction.EAST));

        BufferBlockEntity source = helper.getBlockEntity(sourcePos, BufferBlockEntity.class);
        IImageHandler sourceImages = source.getContents().getCapability(SFMResourceTypes.IMAGE.get()).unwrap();
        SFMValue interactionState = SFMValue.object(Map.of("button", SFMValue.of("red")));
        SFMImageStack image = SFMImageStack.of(fixture(), interactionState);
        helper.assertTrue(sourceImages.insertImage(image, true).isEmpty()
                          && sourceImages.getImage().isEmpty(),
                "Simulated image insertion must not change the buffer");
        helper.assertTrue(sourceImages.insertImage(image, false).isEmpty(),
                "Image buffer rejected a bounded image");

        CompoundTag savedBuffer = source.saveWithFullMetadata();
        BufferBlockEntity restoredBuffer = new BufferBlockEntity(helper.absolutePos(sourcePos), source.getBlockState());
        restoredBuffer.load(savedBuffer);
        helper.assertTrue(restoredBuffer.getContents().getCapability(SFMResourceTypes.IMAGE.get())
                                  .unwrap().getImage().equals(image),
                "Image bytes and interaction state did not survive buffer save/load");
        assertBufferReloads(helper, restoredBuffer, savedBuffer, image);

        ManagerBlockEntity manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        manager.setProgram("""
                EVERY 20 TICKS DO
                    INPUT 1 IMAGE:: FROM images
                    OUTPUT 1 IMAGE:: TO display
                END
                """);
        LabelPositionHolder.empty()
                .add("images", helper.absolutePos(sourcePos))
                .add("display", helper.absolutePos(displayPos))
                .save(Objects.requireNonNull(manager.getDisk()));
        helper.assertManagerRunning(manager);

        TouchDisplayBlockEntity display = helper.getBlockEntity(displayPos, TouchDisplayBlockEntity.class);
        helper.succeedWhen(() -> {
            helper.assertTrue(sourceImages.getImage().isEmpty(),
                    "Manager has not extracted the image from its buffer");
            TouchDisplayBlockEntity.DisplayContent committed = display.content();
            helper.assertTrue(committed.imageSnapshot() != null
                              && committed.imageSnapshot().equals(image.snapshot().orElseThrow())
                              && committed.state().equals(interactionState)
                              && committed.revision() == 1,
                    "Manager did not atomically commit the image, state and revision");

            TouchDisplayBlockEntity restored = new TouchDisplayBlockEntity(
                    helper.absolutePos(displayPos), display.getBlockState()
            );
            restored.load(display.getUpdateTag());
            helper.assertTrue(restored.content().equals(committed),
                    "Display image payload did not survive saved/client-synced content");
        });
    }

    private static void assertBufferReloads(
            SFMGameTestHelper helper,
            BufferBlockEntity restored,
            CompoundTag savedImage,
            SFMImageStack original
    ) {
        IImageHandler cached = restored.getContents().getCapability(SFMResourceTypes.IMAGE.get()).unwrap();
        SFMImageStack replacement = SFMImageStack.of(fixture(0xFF00FF00),
                SFMValue.object(Map.of("button", SFMValue.of("green"))));
        BufferBlockEntity replacementBuffer = new BufferBlockEntity(restored.getBlockPos(), restored.getBlockState());
        helper.assertTrue(replacementBuffer.getContents().getCapability(SFMResourceTypes.IMAGE.get())
                                  .unwrap().insertImage(replacement, false).isEmpty(),
                "Replacement image fixture was rejected");
        CompoundTag savedReplacement = replacementBuffer.saveWithFullMetadata();
        restored.load(savedReplacement);
        helper.assertTrue(cached.getImage().equals(replacement),
                "Image reload retained the old image or interaction state");
        helper.assertTrue(restored.getContents().getCapability(SFMResourceTypes.IMAGE.get()).unwrap() == cached,
                "Image reload orphaned the cached image handler");

        CompoundTag missingState = savedImage.copy();
        missingState.remove("image_interaction_state");
        CompoundTag malformedSnapshot = savedImage.copy();
        malformedSnapshot.put("image_snapshot", new CompoundTag());
        CompoundTag malformedState = savedImage.copy();
        malformedState.putString("image_interaction_state", "{");
        CompoundTag unsupportedCodec = savedImage.copy();
        unsupportedCodec.putInt("image_state_codec", Integer.MAX_VALUE);
        List<CompoundTag> emptyOrMalformed = List.of(new CompoundTag(), missingState,
                malformedSnapshot, malformedState, unsupportedCodec);
        for (CompoundTag tag : emptyOrMalformed) {
            restored.load(savedImage);
            helper.assertTrue(cached.getImage().equals(original), "Image reload setup did not restore the original image");
            restored.load(tag);
            helper.assertTrue(cached.getImage().isEmpty(), "Empty or malformed NBT retained a previously loaded image");
            helper.assertTrue(restored.getContents().getCapability(SFMResourceTypes.IMAGE.get()).unwrap() == cached,
                    "Clearing image NBT orphaned the cached image handler");
            helper.assertTrue(!restored.saveWithFullMetadata().contains("image_snapshot"),
                    "Cleared image was serialized again");
        }

        var redstone = restored.getContents().getCapability(SFMResourceTypes.REDSTONE.get()).unwrap();
        CompoundTag savedRedstone = new CompoundTag();
        savedRedstone.putInt("redstone", 8);
        restored.load(savedImage);
        restored.load(savedRedstone);
        helper.assertTrue(cached.getImage().isEmpty() && redstone.getStoredAmount() == 8,
                "Persisted redstone did not replace the old image in place");
        helper.assertTrue(restored.getContents().getCapability(SFMResourceTypes.REDSTONE.get()).unwrap() == redstone
                          && restored.getContents().lastUsedResource == BufferBlock.ContainedResource.Redstone,
                "Image-to-redstone reload replaced the cached handler or retained the image icon");
        helper.assertTrue(restored.saveWithFullMetadata().getInt("redstone") == 8
                          && !restored.saveWithFullMetadata().contains("image_snapshot"),
                "Image-to-redstone reload saved stale image data or lost redstone");
        helper.assertTrue(cached.insertImage(original, false).equals(original),
                "Cached image handler bypassed occupied redstone exclusion");
        restored.load(savedReplacement);
        helper.assertTrue(redstone.getStoredAmount() == 0 && cached.getImage().equals(replacement),
                "Persisted image did not replace the old redstone in place");
        helper.assertTrue(restored.getContents().getCapability(SFMResourceTypes.IMAGE.get()).unwrap() == cached
                          && restored.getContents().lastUsedResource == BufferBlock.ContainedResource.Image,
                "Redstone-to-image reload replaced the cached handler or retained the redstone icon");
        helper.assertTrue(redstone.insert(1, false) == 0,
                "Cached redstone handler bypassed occupied image exclusion");

        CompoundTag mixedResources = savedImage.copy();
        mixedResources.putInt("redstone", 5);
        for (CompoundTag initial : List.of(savedImage, savedRedstone, new CompoundTag())) {
            restored.load(initial);
            restored.load(mixedResources);
            helper.assertTrue(redstone.getStoredAmount() == 5 && cached.getImage().isEmpty(),
                    "Mixed persisted resources did not give redstone deterministic precedence");
            helper.assertTrue(restored.getContents().getCapability(SFMResourceTypes.REDSTONE.get()).unwrap() == redstone,
                    "Mixed-resource reload orphaned the cached redstone handler");
        }
        CompoundTag malformedRedstone = savedImage.copy();
        malformedRedstone.putString("redstone", "invalid");
        restored.load(malformedRedstone);
        helper.assertTrue(redstone.getStoredAmount() == 0 && cached.getImage().equals(original),
                "Malformed redstone prevented a valid image from replacing old redstone");
        restored.load(new CompoundTag());

        // Persistence owns redstone and images. A load must leave occupied
        // nonpersisted resources intact and preserve one-resource exclusion.
        var items = restored.getContents().getCapability(SFMResourceTypes.ITEM.get()).unwrap();
        helper.assertTrue(items.insertItem(0, new ItemStack(Items.STONE), false).isEmpty(),
                "Occupied nonpersisted-resource fixture was rejected");
        for (CompoundTag tag : List.of(savedImage, savedReplacement, savedRedstone, mixedResources,
                new CompoundTag(), malformedState)) {
            restored.load(tag);
            helper.assertTrue(items.getStackInSlot(0).is(Items.STONE) && items.getStackInSlot(0).getCount() == 1,
                    "Persisted-resource reload erased or changed an occupied nonpersisted resource");
            helper.assertTrue(restored.getContents().getCapability(SFMResourceTypes.ITEM.get()).unwrap() == items,
                    "Persisted-resource reload orphaned the cached item handler");
            helper.assertTrue(cached.getImage().isEmpty() && redstone.getStoredAmount() == 0,
                    "Persisted-resource reload mixed image or redstone with items");
        }
        items.extractItem(0, 1, false);
        restored.load(savedRedstone);
        helper.assertTrue(redstone.getStoredAmount() == 8,
                "Redstone reload stayed locked after the other resource was removed");
        restored.load(savedReplacement);
        helper.assertTrue(cached.getImage().equals(replacement),
                "Image reload stayed locked after the other resource was removed");
    }

    private static SFMImageSnapshot fixture() {
        return fixture(0xFFFF0000);
    }

    private static SFMImageSnapshot fixture(int firstPixel) {
        BufferedImage pixels = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        pixels.setRGB(0, 0, firstPixel);
        pixels.setRGB(1, 1, 0xFF0000FF);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(pixels, "png", output)) {
                throw new IllegalStateException("No PNG writer is available");
            }
            return SFMImageSnapshot.fromPng(output.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Could not make bounded PNG fixture", e);
        }
    }
}
