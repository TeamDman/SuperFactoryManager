package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.item.PacketItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.items.IItemHandler;

import java.util.List;
import java.util.Objects;

/** Puppet-owned Slice D fixture: language request, external worker, and language response routing. */
public final class PacketLanguageTerminalWorkerGameTest extends SFMGameTestDefinition {
    public static final String FIXTURE_ID = "sfm-d-language-worker-v1";
    public static final String PROMPT = "deterministically echo this language-created request";
    public static final String WORKER = "local-terminal-language-echo";
    public static final BlockPos REQUEST_ARCHIVE = new BlockPos(2, 2, 0);
    public static final BlockPos INBOX = new BlockPos(6, 2, 1);
    public static final BlockPos ACK_DESTINATION = new BlockPos(6, 2, 0);
    public static final BlockPos RESPONSE_DESTINATION = new BlockPos(10, 2, 2);

    @Override
    public String template() {
        return "11x3x3";
    }

    @Override
    public int maxTicks() {
        return 20 * 90;
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(0, 2, 1);
        BlockPos managerPos = new BlockPos(2, 2, 1);
        helper.setBlock(sourcePos, Blocks.CHEST);
        helper.setBlock(new BlockPos(1, 2, 1), SFMBlocks.CABLE.get());
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        helper.setBlock(REQUEST_ARCHIVE, Blocks.CHEST);
        helper.setBlock(new BlockPos(3, 2, 1), SFMBlocks.CABLE.get());
        helper.setBlock(new BlockPos(4, 2, 1), SFMBlocks.CABLE.get());
        helper.setBlock(new BlockPos(5, 2, 1), SFMBlocks.CABLE.get());
        helper.setBlock(INBOX, Blocks.CHEST);
        helper.setBlock(new BlockPos(5, 2, 0), SFMBlocks.CABLE.get());
        helper.setBlock(ACK_DESTINATION, Blocks.CHEST);
        helper.setBlock(new BlockPos(2, 2, 2), SFMBlocks.CABLE.get());
        for (int x = 3; x <= 9; x++) {
            helper.setBlock(new BlockPos(x, 2, 2), SFMBlocks.CABLE.get());
        }
        helper.setBlock(RESPONSE_DESTINATION, Blocks.CHEST);

        ItemStack prompt = new ItemStack(SFMItems.DISK.get());
        DiskItem.setProgram(prompt, PROMPT);
        helper.getItemHandler(sourcePos).insertItem(0, prompt, false);

        List<ServerPlayer> players = helper.getLevel().getServer().getPlayerList().getPlayers();
        helper.assertTrue(players.size() == 1, "Terminal language worker requires one integrated player");
        String playerName = players.get(0).getGameProfile().getName();
        BlockPos absoluteInbox = helper.absolutePos(INBOX);
        String dimension = helper.getLevel().dimension().location().toString();

        ManagerBlockEntity manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        manager.setProgram(("""
                let me be player of %s
                let JobId be like guid
                let Request be like object with field type of "Request"
                    and field prompt like string
                    and field JobId
                let Ack be like object with field type of "Ack"
                    and field JobId like guid
                let Response be like object with field type of "Response"
                    and field JobId like guid
                    and field text like string

                every 20 ticks do
                    input with capability sfm:text from source as userinput
                    let userinputstring be string of invoke sfm:text/read with userinput
                    let request be Request with field prompt of userinputstring
                        and field JobId of new guid
                        and field fixture of "%s"
                        and field reply_dimension of "%s"
                        and field reply_x of "%d"
                        and field reply_y of "%d"
                        and field reply_z of "%d"
                    create input sfm:packet with request
                    broadcast to me
                    output to requestarchive
                end

                every 20 ticks do
                    input like Ack from inbox
                    output to ackchest
                    forget
                    input like Response from inbox as r
                    output to responsechest
                end
                """).formatted(
                playerName,
                FIXTURE_ID,
                dimension,
                absoluteInbox.getX(),
                absoluteInbox.getY(),
                absoluteInbox.getZ()
        ));
        LabelPositionHolder.empty()
                .add("source", helper.absolutePos(sourcePos))
                .add("requestarchive", helper.absolutePos(REQUEST_ARCHIVE))
                .add("inbox", absoluteInbox)
                .add("ackchest", helper.absolutePos(ACK_DESTINATION))
                .add("responsechest", helper.absolutePos(RESPONSE_DESTINATION))
                .save(Objects.requireNonNull(manager.getDisk()));

        IItemHandler requestArchive = helper.getItemHandler(REQUEST_ARCHIVE);
        IItemHandler ackDestination = helper.getItemHandler(ACK_DESTINATION);
        IItemHandler responseDestination = helper.getItemHandler(RESPONSE_DESTINATION);
        helper.assertManagerRunning(manager);
        helper.succeedWhen(() -> {
            SFMValue.ObjectValue request = findPacket(requestArchive, "Request");
            SFMValue jobId = request.fields().get("JobId");
            helper.assertTrue(SFMValue.of(PROMPT).equals(request.fields().get("prompt")),
                              "Archived request lost its disk text");
            helper.assertTrue(SFMValue.of(FIXTURE_ID).equals(request.fields().get("fixture")),
                              "Archived request lost its worker fixture marker");

            SFMValue.ObjectValue ack = findPacket(ackDestination, "Ack");
            helper.assertTrue(jobId.equals(ack.fields().get("JobId")),
                              "ACK did not preserve the request JobId");
            helper.assertTrue(SFMValue.of(WORKER).equals(ack.fields().get("worker")),
                              "Open ACK matching discarded the extra worker field");

            SFMValue.ObjectValue response = findPacket(responseDestination, "Response");
            helper.assertTrue(jobId.equals(response.fields().get("JobId")),
                              "Response did not preserve the request JobId");
            helper.assertTrue(SFMValue.of(PROMPT).equals(response.fields().get("text")),
                              "Deterministic worker did not echo the prompt");
            helper.assertTrue(SFMValue.of(WORKER).equals(response.fields().get("worker")),
                              "Open Response matching discarded the extra worker field");
            helper.assertCount(responseDestination, PacketItem.create(response), 2,
                               "Two independently received equal responses were not both routed");
            helper.assertCount(helper.getItemHandler(INBOX), 0,
                               "Routed ACK/Response messages should leave the ordinary mailbox");
        });
    }

    private static SFMValue.ObjectValue findPacket(IItemHandler handler, String type) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            SFMValue value = PacketItem.getValue(handler.getStackInSlot(slot)).orElse(null);
            if (value instanceof SFMValue.ObjectValue object
                && SFMValue.of(type).equals(object.fields().get("type"))) {
                return object;
            }
        }
        throw new GameTestAssertException("No " + type + " packet was present");
    }
}
