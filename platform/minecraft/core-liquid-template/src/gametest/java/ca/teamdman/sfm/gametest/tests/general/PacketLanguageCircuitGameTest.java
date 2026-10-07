package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.client.net.SFMPacketObservationLog;
import ca.teamdman.sfm.client.net.SFMPacketObservationRuntime;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.item.PacketItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValuePattern;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@SFMGameTest(SFMDist.CLIENT)
public final class PacketLanguageCircuitGameTest extends SFMGameTestDefinition {
    private static final String EQUAL_PROMPT = "equal prompt from two physical items";
    private static final String WRITTEN_PROMPT = "written book page one\npage two";

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
        BlockPos sourcePos = new BlockPos(0, 2, 0);
        BlockPos managerPos = new BlockPos(2, 2, 0);
        BlockPos destinationPos = new BlockPos(4, 2, 0);
        helper.setBlock(sourcePos, Blocks.CHEST);
        helper.setBlock(new BlockPos(1, 2, 0), SFMBlocks.CABLE.get());
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        helper.setBlock(new BlockPos(3, 2, 0), SFMBlocks.CABLE.get());
        helper.setBlock(destinationPos, Blocks.CHEST);

        IItemHandler source = helper.getItemHandler(sourcePos);
        ItemStack promptDisk = new ItemStack(SFMItems.DISK.get());
        DiskItem.setProgram(promptDisk, EQUAL_PROMPT);
        source.insertItem(0, promptDisk, false);

        ItemStack writableBook = new ItemStack(Items.WRITABLE_BOOK);
        ListTag writablePages = new ListTag();
        writablePages.add(StringTag.valueOf(EQUAL_PROMPT));
        writableBook.getOrCreateTag().put("pages", writablePages);
        source.insertItem(1, writableBook, false);

        ItemStack writtenBook = new ItemStack(Items.WRITTEN_BOOK);
        ListTag writtenPages = new ListTag();
        writtenPages.add(StringTag.valueOf("{\"text\":\"written book page one\"}"));
        writtenPages.add(StringTag.valueOf("{\"text\":\"page two\"}"));
        writtenBook.getOrCreateTag().put("pages", writtenPages);
        source.insertItem(2, writtenBook, false);

        List<ServerPlayer> players = helper.getLevel().getServer().getPlayerList().getPlayers();
        helper.assertTrue(players.size() == 1, "Language packet circuit requires one integrated player");
        String playerName = players.get(0).getGameProfile().getName();

        ManagerBlockEntity manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        manager.setProgram(("""
                let me be player of %s
                let JobId be like guid
                let Request be like object with field type of "Request"
                    and field prompt like string
                    and field JobId

                every 20 ticks do
                    input with capability sfm:text from source as userinput
                    let userinputstring be string of invoke sfm:text/read with userinput
                    let request be Request with field prompt of userinputstring
                        and field JobId of new guid
                    create input sfm:packet with request
                    broadcast to me
                    output to destination
                end
                """).formatted(playerName));
        LabelPositionHolder.empty()
                .add("source", helper.absolutePos(sourcePos))
                .add("destination", helper.absolutePos(destinationPos))
                .save(Objects.requireNonNull(manager.getDisk()));

        IItemHandler destination = helper.getItemHandler(destinationPos);
        helper.assertManagerRunning(manager);
        helper.succeedWhen(() -> {
            helper.assertTrue(source.getStackInSlot(0).isEmpty()
                              && source.getStackInSlot(1).isEmpty()
                              && source.getStackInSlot(2).isEmpty(),
                              "All copied text inputs should move after request creation");

            List<SFMValue.ObjectValue> requests = new ArrayList<>();
            int sourceItems = 0;
            for (int slot = 0; slot < destination.getSlots(); slot++) {
                ItemStack stack = destination.getStackInSlot(slot);
                if (stack.isEmpty()) continue;
                if (stack.getItem() instanceof PacketItem) {
                    requests.add((SFMValue.ObjectValue) PacketItem.getValue(stack).orElseThrow());
                } else {
                    sourceItems += stack.getCount();
                }
            }
            helper.assertTrue(sourceItems == 3, "Output should move the three original text items");
            helper.assertTrue(requests.size() == 3, "One packet request should be created per text occurrence");
            helper.assertTrue(requests.stream()
                                      .filter(value -> SFMValue.of(EQUAL_PROMPT).equals(value.fields().get("prompt")))
                                      .count() == 2,
                              "Equal prompts must remain two occurrences");
            helper.assertTrue(requests.stream()
                                      .anyMatch(value -> SFMValue.of(WRITTEN_PROMPT).equals(value.fields().get("prompt"))),
                              "Written-book pages should be plain text joined by newlines");
            helper.assertTrue(requests.stream()
                                      .allMatch(value -> SFMValue.of("Request").equals(value.fields().get("type"))),
                              "Literal pattern fields should be included in constructed values");
            helper.assertTrue(requests.stream()
                                      .map(value -> value.fields().get("JobId"))
                                      .allMatch(SFMValuePattern.GUID::matches),
                              "Every request should contain a GUID");
            helper.assertTrue(new HashSet<>(requests.stream()
                                                    .map(value -> value.fields().get("JobId"))
                                                    .toList()).size() == 3,
                              "Each physical input occurrence should receive an independent GUID");

            SFMPacketObservationLog.Page page = SFMPacketObservationRuntime.get()
                    .page(Optional.empty(), SFMPacketObservationLog.MAX_PAGE_SIZE)
                    .orElseThrow(() -> new AssertionError("Client observation session is not active"));
            List<SFMValue> observed = page.entries().stream().map(SFMPacketObservationLog.Entry::value).toList();
            helper.assertTrue(requests.stream().allMatch(observed::contains),
                              "Broadcast should observe the exact values later moved by output");
        });
    }
}
