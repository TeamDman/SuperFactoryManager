package ca.teamdman.sfm.gametest;

import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.gametest.puppet.SFMMultiplayerPuppetControl;
import ca.teamdman.sfm.gametest.puppet.SFMMultiplayerPuppetFixtureContract;
import ca.teamdman.sfm.common.block.TouchDisplayBlock;
import ca.teamdman.sfm.common.blockentity.ClientManagerBlockEntity;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.item.PacketItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerPacketPolicy;
import ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerPacketPolicySavedData;
import ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerServerRuntime;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static ca.teamdman.sfm.gametest.puppet.SFMMultiplayerPuppetFixtureContract.*;
import static ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerPacketProtocol.*;

/** Dedicated-only, opt-in fixture in a runner-created disposable loopback world. */
public final class SFMMultiplayerPuppetServerFixture {
    private static SFMMultiplayerPuppetServerFixture active;
    private final MinecraftServer server;
    private final SFMMultiplayerPuppetControl control;
    private final Map<UUID, Integer> logins = new LinkedHashMap<>();
    private final List<UUID> grants = new ArrayList<>();
    private boolean prepared;
    private CompletableFuture<JsonObject> read;
    private CompletableFuture<Void> write;
    private int sequence = 1;
    private long nextPoll;
    private boolean stopping;

    private SFMMultiplayerPuppetServerFixture(MinecraftServer server) {
        this.server = server;
        this.control = SFMMultiplayerPuppetControl.open("server");
        if (!server.isDedicatedServer() || !"127.0.0.1".equals(server.getLocalIp())
                || server.getPort() != control.port()) {
            throw new IllegalStateException("Multiplayer puppet server must bind only its declared loopback port");
        }
        publish(control.path("server", "ready.json"), observation());
    }

    @SFMSubscribeEvent(SFMDist.DEDICATED_SERVER)
    public static void started(ServerStartedEvent event) {
        if (SFMMultiplayerPuppetControl.enabled()) active = new SFMMultiplayerPuppetServerFixture(event.getServer());
    }

    @SFMSubscribeEvent(SFMDist.DEDICATED_SERVER)
    public static void stopped(ServerStoppedEvent event) {
        if (active != null && active.server == event.getServer()) active = null;
    }

    @SFMSubscribeEvent(SFMDist.DEDICATED_SERVER)
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (active != null && event.getEntity() instanceof ServerPlayer player && player.server == active.server) {
            // Bounded by the isolated server's max-players and runner lifetime; no packet-supplied identity.
            if (active.logins.size() >= 4 && !active.logins.containsKey(player.getUUID())) {
                throw new IllegalStateException("Unexpected player in isolated multiplayer fixture");
            }
            active.logins.merge(player.getUUID(), 1, Integer::sum);
        }
    }

    @SFMSubscribeEvent(SFMDist.DEDICATED_SERVER)
    public static void tick(TickEvent.ServerTickEvent event) {
        if (active != null && event.phase == TickEvent.Phase.END && active.server == event.getServer()) active.poll();
    }

    private void poll() {
        if (write != null) {
            if (!write.isDone()) return;
            write.join();
            write = null;
            if (stopping) {
                server.halt(false);
                return;
            }
        }
        if (read == null) {
            if (System.nanoTime() < nextPoll) return;
            nextPoll = System.nanoTime() + 100_000_000L;
            int requestSequence = sequence;
            read = CompletableFuture.supplyAsync(() -> control.readRequest("server", requestSequence));
            return;
        }
        if (!read.isDone()) return;
        JsonObject request = read.join();
        read = null;
        if (request == null) return;
        JsonObject response;
        try {
            switch (request.get("op").getAsString()) {
                case "observe" -> { }
                case "prepare" -> prepare();
                case "grant" -> grant(600_000L);
                case "grant_expiring" -> grant(1_000L);
                case "revoke" -> revoke();
                case "edit_program" -> editProgram();
                case "stop" -> { revoke(); stopping = true; }
                default -> throw new IllegalArgumentException("Unknown isolated server fixture operation");
            }
            response = observation();
            response.addProperty("success", true);
        } catch (RuntimeException failure) {
            response = observation();
            response.addProperty("success", false);
            response.addProperty("message", failure.getMessage());
        }
        response.addProperty("sequence", sequence);
        response.addProperty("op", request.get("op").getAsString());
        publish(control.response("server", sequence++), response);
    }

    private JsonObject observation() {
        JsonObject result = control.observation();
        result.addProperty("dedicatedServer", server.isDedicatedServer());
        result.addProperty("serverTick", server.overworld().getGameTime());
        result.addProperty("connectedPlayers", server.getPlayerList().getPlayerCount());
        result.addProperty("prepared", prepared);
        result.addProperty("fixtureGrants", grants.size());
        if (prepared) {
            result.addProperty("mailboxPackets", count(MAILBOX));
            result.addProperty("archivePackets", count(ARCHIVE));
            result.addProperty("deniedPackets", count(DENIED));
            JsonObject outcomes = new JsonObject();
            SFMMultiplayerServerRuntime.diagnostics(server).outcomes().forEach((status, count) -> outcomes.addProperty(status.name(), count));
            result.add("outcomes", outcomes);
        }
        JsonArray players = new JsonArray();
        logins.forEach((uuid, count) -> {
            JsonObject player = new JsonObject();
            player.addProperty("uuid", uuid.toString());
            player.addProperty("logins", count);
            players.add(player);
        });
        result.add("players", players);
        return result;
    }

    private ServerPlayer player() {
        var players = server.getPlayerList().getPlayers();
        if (players.size() != 1) throw new IllegalStateException("Fixture requires exactly one connected test player");
        return players.get(0);
    }

    private void prepare() {
        if (prepared) throw new IllegalStateException("Fixture is already prepared");
        ServerPlayer owner = player();
        var level = server.overworld();
        owner.teleportTo(level, 4.5, 82, 0.5, 0, 0);
        for (int x = -1; x <= 9; x++) for (int z = 1; z <= 5; z++) {
            level.setBlockAndUpdate(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState());
        }
        for (int x = 0; x <= 6; x++) level.setBlockAndUpdate(new BlockPos(x, 81, 3), SFMBlocks.CABLE.get().defaultBlockState());
        for (BlockPos position : List.of(MAILBOX, ARCHIVE, DENIED)) level.setBlockAndUpdate(position, Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(PUBLISHER, SFMBlocks.MANAGER.get().defaultBlockState());
        ManagerBlockEntity publisher = (ManagerBlockEntity) level.getBlockEntity(PUBLISHER);
        publisher.setItem(0, new ItemStack(SFMItems.DISK.get()));
        LabelPositionHolder.empty().add("mailbox", MAILBOX).add("archive", ARCHIVE).save(Objects.requireNonNull(publisher.getDisk()));
        publisher.setProgram("SERVER BTW\nLET owner BE PLAYER OF " + owner.getGameProfile().getName() + "\n"
                + "EVERY 20 TICKS DO\nINPUT 1 sfm:packet FROM mailbox\nBROADCAST TO owner CHANNEL " + CHANNEL + "\n"
                + "OUTPUT 1 sfm:packet TO archive\nEND");
        if (publisher.getState() != ManagerBlockEntity.State.RUNNING) throw new IllegalStateException("Fixture SFML publisher did not compile");
        level.setBlockAndUpdate(DISPLAY, SFMBlocks.TOUCH_DISPLAY.get().defaultBlockState().setValue(TouchDisplayBlock.FACING, Direction.NORTH));
        level.setBlockAndUpdate(CLIENT_MANAGER, SFMBlocks.CLIENT_MANAGER.get().defaultBlockState());
        ItemStack disk = new ItemStack(SFMItems.DISK.get());
        DiskItem.setProgram(disk, SFMMultiplayerPuppetFixtureContract.clientSource());
        LabelPositionHolder.empty().add("displays", DISPLAY).save(disk);
        ((ClientManagerBlockEntity) level.getBlockEntity(CLIENT_MANAGER)).setDisk(disk);
        prepared = true;
    }

    private void grant(long lifetimeMillis) {
        if (!prepared || !grants.isEmpty()) throw new IllegalStateException("Prepare the fixture and revoke old grants before granting");
        UUID owner = player().getUUID();
        var policy = SFMMultiplayerPacketPolicySavedData.forServer(server);
        if (!policy.policy().snapshot().isEmpty()) throw new IllegalStateException("Isolated fixture unexpectedly contains other grants");
        InboxScope inbox = new InboxScope(DIMENSION, CHANNEL);
        Map<Action, Scope> scopes = Map.of(Action.PACKET_SEND, new InventoryScope(TARGET),
                Action.INBOX_SUBSCRIBE, inbox,
                Action.INBOX_DELIVER, new DeliveryScope(new ManagerAddress(DIMENSION, PUBLISHER), inbox));
        long expiry = System.currentTimeMillis() + lifetimeMillis;
        scopes.forEach((action, scope) -> {
            UUID id = UUID.randomUUID();
            policy.grant(new SFMMultiplayerPacketPolicy.Grant(id, owner, action, scope, Optional.empty(), OptionalLong.of(expiry)));
            grants.add(id);
        });
    }

    private void revoke() {
        if (grants.isEmpty()) return;
        var policy = SFMMultiplayerPacketPolicySavedData.forServer(server);
        grants.forEach(policy::revoke);
        grants.clear();
    }

    private void editProgram() {
        if (!prepared) throw new IllegalStateException("Prepare the fixture first");
        var manager = (ClientManagerBlockEntity) server.overworld().getBlockEntity(CLIENT_MANAGER);
        ItemStack disk = manager.disk();
        DiskItem.setProgram(disk, manager.storedSource() + "\n-- exact acknowledged revision changed");
        manager.setDisk(disk);
    }

    private int count(BlockPos position) {
        if (!(server.overworld().getBlockEntity(position) instanceof ChestBlockEntity chest)) return -1;
        int count = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack item = chest.getItem(slot);
            if (PacketItem.getValue(item).isPresent()) count += item.getCount();
        }
        return count;
    }

    private void publish(java.nio.file.Path path, JsonObject value) {
        write = CompletableFuture.runAsync(() -> SFMMultiplayerPuppetControl.write(path, value));
    }
}
