package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.item.PacketItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
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

@SFMGameTest
public final class PacketResponseRoutingGameTest extends SFMGameTestDefinition {
    private static final String JOB_ID = "ce3d7b60-a8c9-4e07-a21f-01759a92fbfe";

    @Override
    public String template() {
        return "7x3x3";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        BlockPos inboxPos = new BlockPos(0, 2, 1);
        BlockPos managerPos = new BlockPos(3, 2, 1);
        BlockPos ackPos = new BlockPos(3, 2, 0);
        BlockPos responsePos = new BlockPos(6, 2, 1);
        helper.setBlock(inboxPos, Blocks.CHEST);
        helper.setBlock(new BlockPos(1, 2, 1), SFMBlocks.CABLE.get());
        helper.setBlock(new BlockPos(2, 2, 1), SFMBlocks.CABLE.get());
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        helper.setBlock(ackPos, Blocks.CHEST);
        helper.setBlock(new BlockPos(4, 2, 1), SFMBlocks.CABLE.get());
        helper.setBlock(new BlockPos(5, 2, 1), SFMBlocks.CABLE.get());
        helper.setBlock(responsePos, Blocks.CHEST);

        SFMValue ack = SFMValue.object(Map.of(
                "type", SFMValue.of("Ack"),
                "JobId", SFMValue.of(JOB_ID),
                "worker", SFMValue.of("deterministic-worker")
        ));
        SFMValue response = SFMValue.object(Map.of(
                "type", SFMValue.of("Response"),
                "JobId", SFMValue.of(JOB_ID),
                "text", SFMValue.of("echo"),
                "worker", SFMValue.of("deterministic-worker")
        ));
        SFMValue wrongGuid = SFMValue.object(Map.of(
                "type", SFMValue.of("Response"),
                "JobId", SFMValue.of("not-a-guid"),
                "text", SFMValue.of("must stay")
        ));
        SFMValue wrongType = SFMValue.object(Map.of(
                "type", SFMValue.of("Request"),
                "JobId", SFMValue.of(JOB_ID),
                "text", SFMValue.of("must also stay")
        ));

        IItemHandler inbox = helper.getItemHandler(inboxPos);
        inbox.insertItem(0, PacketItem.create(ack), false);
        ItemStack duplicateResponses = PacketItem.create(response);
        duplicateResponses.setCount(2);
        inbox.insertItem(1, duplicateResponses, false);
        inbox.insertItem(2, PacketItem.create(wrongGuid), false);
        inbox.insertItem(3, PacketItem.create(wrongType), false);

        ManagerBlockEntity manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        manager.setProgram("""
                let JobId be like guid
                let Ack be like object with field type of "Ack"
                    and field JobId like guid
                let Response be like object with field type of "Response"
                    and field JobId like guid
                    and field text like string

                every 20 ticks do
                    input like Ack from inbox
                    output to ackchest
                    forget
                    input like Response from inbox as r
                    output to responsechest
                end
                """);
        LabelPositionHolder.empty()
                .add("inbox", helper.absolutePos(inboxPos))
                .add("ackchest", helper.absolutePos(ackPos))
                .add("responsechest", helper.absolutePos(responsePos))
                .save(Objects.requireNonNull(manager.getDisk()));

        IItemHandler ackChest = helper.getItemHandler(ackPos);
        IItemHandler responseChest = helper.getItemHandler(responsePos);
        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            helper.assertCount(ackChest, PacketItem.create(ack), 1,
                               "Open ACK pattern did not retain and route the complete value");
            helper.assertCount(responseChest, PacketItem.create(response), 2,
                               "Two equal Response occurrences did not route together");
            helper.assertCount(inbox, PacketItem.create(wrongGuid), 1,
                               "Wrong GUID response should not match");
            helper.assertCount(inbox, PacketItem.create(wrongType), 1,
                               "Wrong type response should not match");
            helper.assertCount(inbox, 2,
                               "Only the two non-matching packet values should remain in the mailbox");
        });
    }
}
