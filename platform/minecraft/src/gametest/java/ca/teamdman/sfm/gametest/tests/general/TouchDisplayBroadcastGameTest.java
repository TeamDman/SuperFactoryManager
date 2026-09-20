package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.client.net.SFMClientInbox;
import ca.teamdman.sfm.client.net.SFMClientInboxRuntime;
import ca.teamdman.sfm.client.net.SFMClientInboxTransport;
import ca.teamdman.sfm.common.block.TouchDisplayBlock;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.blockentity.TouchDisplayBlockEntity;
import ca.teamdman.sfm.common.item.PacketItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.net.SFMClientInboxAddress;
import ca.teamdman.sfm.common.net.SFMServerClientInboxTransport;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.items.IItemHandler;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Ambient proof that SFML BROADCAST routes semantic snapshots and actual mover
 * occurrence values into different addressed client inbox channels.
 */
@SFMGameTest(SFMDist.CLIENT)
public final class TouchDisplayBroadcastGameTest extends SFMGameTestDefinition {
    private static final ResourceLocation STATE_CHANNEL = new ResourceLocation("sfm", "touch_display_state_test");
    private static final ResourceLocation OCCURRENCE_CHANNEL = new ResourceLocation("sfm", "touch_display_occurrence_test");
    private static final BlockPos STATE_SOURCE = new BlockPos(0, 2, 0);
    private static final BlockPos STATE_MANAGER = new BlockPos(2, 2, 0);
    private static final BlockPos STATE_ARCHIVE = new BlockPos(4, 2, 0);
    private static final BlockPos EVENT_SOURCE = new BlockPos(6, 2, 0);
    private static final BlockPos EVENT_MANAGER = new BlockPos(8, 2, 0);
    private static final BlockPos EVENT_ARCHIVE = new BlockPos(10, 2, 0);
    private static final BlockPos IRON_SOURCE = new BlockPos(6, 2, 2);
    private static final BlockPos IRON_DESTINATION = new BlockPos(10, 2, 2);
    private static final BlockPos DISPLAY = new BlockPos(4, 2, 2);

    @Override
    public String template() {
        return "11x3x3";
    }

    @Override
    public int maxTicks() {
        return 20 * 20;
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        List<ServerPlayer> players = helper.getLevel().getServer().getPlayerList().getPlayers();
        helper.assertTrue(players.size() == 1, "Broadcast test requires one private integrated owner");
        ServerPlayer owner = players.get(0);
        SFMClientInboxAddress stateAddress = address(owner, STATE_CHANNEL);
        SFMClientInboxAddress eventAddress = address(owner, OCCURRENCE_CHANNEL);

        placeManagerCircuit(helper, STATE_SOURCE, STATE_MANAGER, STATE_ARCHIVE);
        placeManagerCircuit(helper, EVENT_SOURCE, EVENT_MANAGER, EVENT_ARCHIVE);
        helper.setBlock(IRON_SOURCE, Blocks.CHEST);
        helper.setBlock(IRON_DESTINATION, Blocks.CHEST);
        helper.setBlock(DISPLAY, SFMBlocks.TOUCH_DISPLAY.get().defaultBlockState()
                .setValue(TouchDisplayBlock.FACING, Direction.SOUTH));
        TouchDisplayBlockEntity display = helper.getBlockEntity(DISPLAY, TouchDisplayBlockEntity.class);

        IItemHandler stateSource = helper.getItemHandler(STATE_SOURCE);
        IItemHandler stateArchive = helper.getItemHandler(STATE_ARCHIVE);
        IItemHandler eventSource = helper.getItemHandler(EVENT_SOURCE);
        IItemHandler eventArchive = helper.getItemHandler(EVENT_ARCHIVE);
        IItemHandler ironSource = helper.getItemHandler(IRON_SOURCE);
        IItemHandler ironDestination = helper.getItemHandler(IRON_DESTINATION);
        helper.assertTrue(ironSource.insertItem(0, new ItemStack(Items.IRON_INGOT, 3), false).isEmpty(),
                "Could not seed three iron units to be moved authoritatively");

        installBroadcastProgram(helper, STATE_MANAGER, STATE_SOURCE, STATE_ARCHIVE,
                owner.getGameProfile().getName(), STATE_CHANNEL);
        installBroadcastProgram(helper, EVENT_MANAGER, EVENT_SOURCE, EVENT_ARCHIVE,
                owner.getGameProfile().getName(), OCCURRENCE_CHANNEL);

        AtomicReference<SFMClientInboxRuntime.Subscription> stateSubscription = new AtomicReference<>();
        AtomicReference<SFMClientInboxRuntime.Subscription> eventSubscription = new AtomicReference<>();
        AtomicReference<String> clientFailure = new AtomicReference<>();
        Minecraft.getInstance().execute(() -> {
            try {
                stateSubscription.set(SFMClientInboxTransport.subscribe(stateAddress).orElseThrow());
                eventSubscription.set(SFMClientInboxTransport.subscribe(eventAddress).orElseThrow());
            } catch (RuntimeException failure) {
                clientFailure.set(failure.toString());
            }
        });

        AtomicBoolean started = new AtomicBoolean();
        AtomicBoolean blueCommitted = new AtomicBoolean();
        AtomicInteger movedIron = new AtomicInteger();
        SFMValue redState = SFMValue.object(Map.of("color", SFMValue.of("red")));
        SFMValue blueState = SFMValue.object(Map.of("color", SFMValue.of("blue")));
        AtomicReference<SFMValue> redSnapshot = new AtomicReference<>();
        AtomicReference<SFMValue> blueSnapshot = new AtomicReference<>();

        helper.succeedWhen(() -> {
            helper.assertTrue(clientFailure.get() == null, String.valueOf(clientFailure.get()));
            if (SFMServerClientInboxTransport.isSubscribed(owner, stateAddress)
                && SFMServerClientInboxTransport.isSubscribed(owner, eventAddress)
                && started.compareAndSet(false, true)) {
                display.commitContent(TouchDisplayBlockEntity.RED_FIXTURE_IMAGE, redState);
                SFMValue value = snapshotValue(display, "red");
                redSnapshot.set(value);
                helper.assertTrue(stateSource.insertItem(0, PacketItem.create(value), false).isEmpty(),
                        "Could not queue the server's red content snapshot");
            }
            helper.assertTrue(started.get(), "Waiting for both addressed inbox subscriptions");

            // The fixture is the authoritative mover: it emits one event only
            // after one physical iron ingot has actually reached its output.
            if (movedIron.get() < 3) {
                ItemStack moved = ironSource.extractItem(0, 1, false);
                helper.assertTrue(moved.getCount() == 1
                                  && ironDestination.insertItem(0, moved, false).isEmpty(),
                        "One iron occurrence did not reach its destination");
                int index = movedIron.incrementAndGet();
                helper.assertTrue(eventSource.insertItem(index - 1, occurrenceValue(index), false).isEmpty(),
                        "Could not queue the matching mover occurrence");
            }

            List<SFMValue> snapshots = inboxValues(STATE_CHANNEL);
            if (redSnapshot.get() != null && snapshots.contains(redSnapshot.get())
                && blueCommitted.compareAndSet(false, true)) {
                display.commitContent(TouchDisplayBlockEntity.BLUE_FIXTURE_IMAGE, blueState);
                SFMValue value = snapshotValue(display, "blue");
                blueSnapshot.set(value);
                helper.assertTrue(stateSource.insertItem(1, PacketItem.create(value), false).isEmpty(),
                        "Could not queue the server's blue content snapshot");
            }

            helper.assertTrue(blueCommitted.get(), "Waiting for the red state broadcast");
            helper.assertTrue(snapshots.size() == 2
                              && snapshots.get(0).equals(redSnapshot.get())
                              && snapshots.get(1).equals(blueSnapshot.get()),
                    "State channel must carry one coherent red and one coherent blue snapshot");

            List<SFMValue> occurrences = inboxValues(OCCURRENCE_CHANNEL);
            helper.assertTrue(occurrences.size() == 3, "Waiting for three explicit mover occurrences");
            for (int index = 1; index <= 3; index++) {
                helper.assertTrue(occurrences.contains(occurrenceData(index)),
                        "Missing distinct iron movement occurrence " + index);
            }
            helper.assertTrue(movedIron.get() == 3
                              && ironSource.getStackInSlot(0).isEmpty()
                              && ironDestination.getStackInSlot(0).getCount() == 3,
                    "Three counted occurrences must correspond to three moved iron units");
            helper.assertTrue(countPackets(stateArchive) == 2 && countPackets(eventArchive) == 3,
                    "Broadcast inputs must be archived once rather than observed repeatedly");
            helper.assertTrue(display.content().revision() == 2
                              && display.content().state().equals(blueState),
                    "Server display did not finish with the coherent blue revision");

            Minecraft.getInstance().execute(() -> {
                close(stateSubscription.get());
                close(eventSubscription.get());
            });
        });
    }

    private static void placeManagerCircuit(
            SFMGameTestHelper helper, BlockPos source, BlockPos manager, BlockPos archive
    ) {
        helper.setBlock(source, Blocks.CHEST);
        helper.setBlock(source.east(), SFMBlocks.CABLE.get());
        helper.setBlock(manager, SFMBlocks.MANAGER.get());
        helper.setBlock(manager.east(), SFMBlocks.CABLE.get());
        helper.setBlock(archive, Blocks.CHEST);
    }

    private static void installBroadcastProgram(
            SFMGameTestHelper helper,
            BlockPos managerPos,
            BlockPos source,
            BlockPos archive,
            String playerName,
            ResourceLocation channel
    ) {
        ManagerBlockEntity manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        manager.setProgram(("""
                LET owner BE PLAYER OF %s
                EVERY 20 TICKS DO
                    INPUT 1 sfm:packet FROM source
                    BROADCAST TO owner CHANNEL %s
                    OUTPUT 1 sfm:packet TO archive
                END
                """).formatted(playerName, channel));
        LabelPositionHolder.empty()
                .add("source", helper.absolutePos(source))
                .add("archive", helper.absolutePos(archive))
                .save(Objects.requireNonNull(manager.getDisk()));
        helper.assertManagerRunning(manager);
    }

    private static SFMClientInboxAddress address(ServerPlayer owner, ResourceLocation channel) {
        return new SFMClientInboxAddress(owner.getUUID(), owner.getLevel().dimension().location(), channel);
    }

    private static SFMValue snapshotValue(TouchDisplayBlockEntity display, String color) {
        var content = display.content();
        return SFMValue.object(Map.of(
                "kind", SFMValue.of("snapshot"),
                "color", SFMValue.of(color),
                "revision", SFMValue.of(content.revision()),
                "state", content.state()
        ));
    }

    private static ItemStack occurrenceValue(int index) {
        return PacketItem.create(occurrenceData(index));
    }

    private static SFMValue occurrenceData(int index) {
        return SFMValue.object(Map.of(
                "kind", SFMValue.of("transfer_occurrence"),
                "item", SFMValue.of("minecraft:iron_ingot"),
                "amount", SFMValue.of(1L),
                "index", SFMValue.of((long) index)
        ));
    }

    private static List<SFMValue> inboxValues(ResourceLocation channel) {
        return SFMClientInboxRuntime.get().page(channel, Optional.empty(), 10)
                .map(page -> page.entries().stream().map(SFMClientInbox.Entry::value).toList())
                .orElse(List.of());
    }

    private static int countPackets(IItemHandler handler) {
        int count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (PacketItem.getValue(stack).isPresent()) count += stack.getCount();
        }
        return count;
    }

    private static void close(SFMClientInboxRuntime.Subscription subscription) {
        if (subscription != null) subscription.close();
    }
}
