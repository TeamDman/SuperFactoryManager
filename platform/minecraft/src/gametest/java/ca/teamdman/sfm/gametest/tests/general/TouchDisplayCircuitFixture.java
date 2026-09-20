package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.common.block.TouchDisplayBlock;
import ca.teamdman.sfm.common.blockentity.BufferBlockEntity;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.blockentity.TouchDisplayBlockEntity;
import ca.teamdman.sfm.common.image.SFMImageSnapshot;
import ca.teamdman.sfm.common.item.PacketItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.SFMImageStack;
import ca.teamdman.sfm.common.value.SFMTouchValue;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.items.IItemHandler;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/** Shared server-only circuit construction. No client class may enter this fixture. */
final class TouchDisplayCircuitFixture {
    static final BlockPos DISPLAY = new BlockPos(2, 2, 2);
    static final BlockPos TOUCH_MAILBOX = new BlockPos(2, 2, 1);
    static final BlockPos TOUCH_ARCHIVE = new BlockPos(0, 2, 1);
    static final BlockPos SERVER_MANAGER = new BlockPos(4, 2, 0);
    static final BlockPos CLIENT_MANAGER = new BlockPos(4, 2, 2);
    static final BlockPos IMAGES = new BlockPos(6, 2, 1);
    static final BlockPos COMMAND_MAILBOX = new BlockPos(8, 2, 1);
    static final BlockPos COMMAND_ARCHIVE = new BlockPos(10, 2, 1);
    static final SFMValue RED = SFMValue.object(Map.of("color", SFMValue.of("red")));
    static final SFMValue BLUE = SFMValue.object(Map.of("color", SFMValue.of("blue")));
    static final SFMValue COMMAND = SFMValue.object(Map.of(
            "schema", SFMValue.of("sfm:display_circuit_command@1"), "color", SFMValue.of("blue")));

    final SFMGameTestHelper helper;
    final TouchDisplayBlockEntity display;
    final SFMImageStack nextImage;

    TouchDisplayCircuitFixture(SFMGameTestHelper helper, String playerName, ResourceLocation channel) {
        this.helper = helper;
        for (int x = 0; x <= 10; x++) helper.setBlock(new BlockPos(x, 2, 0), SFMBlocks.CABLE.get());
        helper.setBlock(new BlockPos(3, 2, 1), SFMBlocks.CABLE.get());
        helper.setBlock(new BlockPos(3, 2, 2), SFMBlocks.CABLE.get());
        helper.setBlock(SERVER_MANAGER, SFMBlocks.MANAGER.get());
        for (BlockPos chest : new BlockPos[]{TOUCH_MAILBOX, TOUCH_ARCHIVE, COMMAND_MAILBOX, COMMAND_ARCHIVE}) {
            helper.setBlock(chest, Blocks.CHEST);
        }
        helper.setBlock(IMAGES, SFMBlocks.BUFFER_BLOCK.get());
        helper.setBlock(DISPLAY, SFMBlocks.TOUCH_DISPLAY.get().defaultBlockState()
                .setValue(TouchDisplayBlock.FACING, Direction.SOUTH));
        display = helper.getBlockEntity(DISPLAY, TouchDisplayBlockEntity.class);
        helper.assertTrue(display.commitContent(TouchDisplayBlockEntity.RED_FIXTURE_IMAGE, RED),
                "Could not establish the initial red semantic content");
        nextImage = SFMImageStack.of(blueImage(), BLUE);
        var imageHandler = helper.getBlockEntity(IMAGES, BufferBlockEntity.class)
                .getContents().getCapability(SFMResourceTypes.IMAGE.get()).unwrap();
        helper.assertTrue(imageHandler.insertImage(nextImage, false).isEmpty(), "Could not seed the blue image buffer");

        // This is the authoritative mover. The test never performs either archive transfer or the blue commit.
        String prefix = channel == null ? "" : "LET owner BE PLAYER OF " + playerName + "\n";
        String broadcast = channel == null ? "" : "BROADCAST TO owner CHANNEL " + channel + "\n";
        ManagerBlockEntity manager = helper.getBlockEntity(SERVER_MANAGER, ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        manager.setProgram("SERVER BTW\n" + prefix + """
                EVERY 20 TICKS DO
                    INPUT 1 sfm:packet FROM touches
                """ + broadcast + """
                    OUTPUT 1 sfm:packet TO touched
                    FORGET
                    IF commands HAS GT 0 sfm:packet THEN
                        INPUT 1 sfm:packet FROM commands
                        OUTPUT 1 sfm:packet TO commanded
                        FORGET
                        INPUT 1 IMAGE:: FROM images
                        OUTPUT 1 IMAGE:: TO display
                    END
                END
                """);
        LabelPositionHolder.empty()
                .add("touches", helper.absolutePos(TOUCH_MAILBOX))
                .add("touched", helper.absolutePos(TOUCH_ARCHIVE))
                .add("commands", helper.absolutePos(COMMAND_MAILBOX))
                .add("commanded", helper.absolutePos(COMMAND_ARCHIVE))
                .add("images", helper.absolutePos(IMAGES))
                .add("display", helper.absolutePos(DISPLAY))
                .save(Objects.requireNonNull(manager.getDisk()));
        helper.assertManagerRunning(manager);
    }

    SFMValue press() {
        var player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        var hit = TouchDisplayPressServerGameTest.hit(helper.absolutePos(DISPLAY), Direction.SOUTH, 0.25, 0.75);
        player.setPos(hit.getLocation().x, hit.getLocation().y - player.getEyeHeight(), hit.getLocation().z + 0.75);
        var before = display.content();
        var state = display.getBlockState();
        helper.assertTrue(state.getBlock().use(state, helper.getLevel(), helper.absolutePos(DISPLAY), player,
                InteractionHand.MAIN_HAND, hit) == InteractionResult.CONSUME, "Server display did not consume the press");
        SFMValue value = SFMTouchValue.press(helper.getLevel().dimension().location(), helper.absolutePos(DISPLAY),
                Direction.SOUTH, 0.25, 0.75, before.revision(), before.state());
        helper.assertCount(helper.getItemHandler(TOUCH_MAILBOX), PacketItem.create(value), 1,
                "Server press must enqueue exactly one snapshot packet before a manager moves it");
        return value;
    }

    void assertCompleted(SFMValue touch) {
        helper.assertCount(helper.getItemHandler(TOUCH_ARCHIVE), PacketItem.create(touch), 1,
                "Ordinary manager has not archived exactly one original touch");
        helper.assertCount(helper.getItemHandler(COMMAND_ARCHIVE), PacketItem.create(COMMAND), 1,
                "Ordinary manager has not archived exactly one command");
        helper.assertCount(helper.getItemHandler(TOUCH_MAILBOX), 0, "Touch mailbox was not consumed");
        helper.assertCount(helper.getItemHandler(COMMAND_MAILBOX), 0, "Command mailbox was not consumed");
        helper.assertTrue(packetCount(helper.getItemHandler(TOUCH_ARCHIVE)) == 1
                          && packetCount(helper.getItemHandler(COMMAND_ARCHIVE)) == 1,
                "Circuit duplicated an event or command");
        helper.assertTrue(helper.getBlockEntity(IMAGES, BufferBlockEntity.class).getContents()
                                  .getCapability(SFMResourceTypes.IMAGE.get()).unwrap().getImage().isEmpty(),
                "Ordinary manager did not consume the image buffer");
        var content = display.content();
        helper.assertTrue(content.revision() == 2 && content.state().equals(BLUE)
                          && Objects.equals(content.imageSnapshot(), nextImage.snapshot().orElseThrow()),
                "Command did not cause one atomic image/state/revision commit");
        var restored = new TouchDisplayBlockEntity(helper.absolutePos(DISPLAY), display.getBlockState());
        restored.load(display.getUpdateTag());
        helper.assertTrue(restored.content().equals(content), "Committed circuit state did not survive its persistence/projection codec");
    }

    static int packetCount(IItemHandler inventory) {
        int count = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (PacketItem.getValue(stack).isPresent()) count += stack.getCount();
        }
        return count;
    }

    private static SFMImageSnapshot blueImage() {
        BufferedImage pixels = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) pixels.setRGB(x, y, 0xFF0000FF);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(pixels, "png", output)) throw new IllegalStateException("No PNG writer is available");
            return SFMImageSnapshot.fromPng(output.toByteArray());
        } catch (IOException failure) { throw new IllegalStateException("Could not encode circuit image", failure); }
    }
}
