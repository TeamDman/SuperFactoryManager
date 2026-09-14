package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.item.PacketItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.net.SFMPacketInventoryAddress;
import ca.teamdman.sfm.common.net.SFMPacketInventoryInserter;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.items.IItemHandler;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** Proves an ordinary loaded inventory can retain a response until a manager later runs. */
@SFMGameTest
public final class PacketMailboxPersistenceGameTest extends SFMGameTestDefinition {
    private static final SFMValue RESPONSE = SFMValue.object(Map.of(
            "type", SFMValue.of("Response"),
            "JobId", SFMValue.of("194f701c-e257-44ec-a9ce-eaa8f93ec6be"),
            "text", SFMValue.of("waited in an ordinary inventory")
    ));

    @Override
    public String template() {
        return "5x3x1";
    }

    @Override
    public int maxTicks() {
        return 20 * 15;
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        BlockPos mailboxPos = new BlockPos(0, 2, 0);
        BlockPos managerPos = new BlockPos(2, 2, 0);
        BlockPos destinationPos = new BlockPos(4, 2, 0);
        helper.setBlock(mailboxPos, Blocks.CHEST);
        helper.setBlock(destinationPos, Blocks.CHEST);

        BlockPos absoluteMailbox = helper.absolutePos(mailboxPos);
        helper.assertTrue(helper.getLevel().isLoaded(absoluteMailbox),
                          "The ordinary mailbox must already be loaded");
        helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(managerPos)).isAir(),
                          "No manager runtime should exist when the response arrives");

        SFMPacketInventoryInserter.Result result = SFMPacketInventoryInserter.insert(
                helper.getLevel().getServer(),
                new SFMPacketInventoryAddress(
                        helper.getLevel().dimension().location(),
                        absoluteMailbox,
                        Optional.empty()
                ),
                RESPONSE
        );
        helper.assertTrue(result == SFMPacketInventoryInserter.Result.INSERTED,
                          "Loaded mailbox insertion failed: " + result);

        IItemHandler mailbox = helper.getItemHandler(mailboxPos);
        IItemHandler destination = helper.getItemHandler(destinationPos);
        helper.assertCount(mailbox, PacketItem.create(RESPONSE), 1,
                           "The response was not persisted in the ordinary mailbox");

        AtomicBoolean managerStarted = new AtomicBoolean();
        helper.runAfterDelay(20, () -> {
            helper.assertCount(mailbox, PacketItem.create(RESPONSE), 1,
                               "The mailbox did not retain the response during manager absence");
            helper.setBlock(new BlockPos(1, 2, 0), SFMBlocks.CABLE.get());
            helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
            helper.setBlock(new BlockPos(3, 2, 0), SFMBlocks.CABLE.get());

            ManagerBlockEntity manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
            manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
            manager.setProgram("""
                    let Response be like object with field type of "Response"
                        and field JobId like guid
                        and field text like string

                    every 20 ticks do
                        input like Response from mailbox
                        output to responses
                    end
                    """);
            LabelPositionHolder.empty()
                    .add("mailbox", absoluteMailbox)
                    .add("responses", helper.absolutePos(destinationPos))
                    .save(Objects.requireNonNull(manager.getDisk()));
            helper.assertManagerRunning(manager);
            managerStarted.set(true);
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(managerStarted.get(), "The later manager has not started yet");
            helper.assertCount(destination, PacketItem.create(RESPONSE), 1,
                               "The later timed manager did not route the persisted response");
            helper.assertCount(mailbox, 0,
                               "The routed response should leave the ordinary mailbox");
        });
    }
}
