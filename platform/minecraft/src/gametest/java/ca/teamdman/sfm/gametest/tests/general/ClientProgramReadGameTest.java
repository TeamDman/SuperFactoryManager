package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.client.program.*;
import ca.teamdman.sfm.common.block.TouchDisplayBlock;
import ca.teamdman.sfm.common.blockentity.ClientManagerBlockEntity;
import ca.teamdman.sfm.common.blockentity.TouchDisplayBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.net.SFMClientInboxAddress;
import ca.teamdman.sfm.common.net.SFMServerClientInboxTransport;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueSchema;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Reads replicated dust state and an explicit addressed broadcast without screens or player control. */
@SFMGameTest(SFMDist.CLIENT)
public final class ClientProgramReadGameTest extends SFMGameTestDefinition {
    private static final BlockPos MANAGER = new BlockPos(0, 2, 0);
    private static final BlockPos DISPLAY = new BlockPos(2, 2, 0);
    private static final BlockPos DUST = new BlockPos(1, 2, 1);
    private static final BlockPos CHEST = new BlockPos(0, 2, 2);

    @Override public String template() { return "3x3x3"; }
    @Override public int maxTicks() { return 400; }

    @Override public void run(SFMGameTestHelper helper) {
        helper.setBlock(MANAGER, SFMBlocks.CLIENT_MANAGER.get());
        helper.setBlock(DISPLAY, SFMBlocks.TOUCH_DISPLAY.get().defaultBlockState().setValue(TouchDisplayBlock.FACING, Direction.SOUTH));
        helper.setBlock(CHEST, Blocks.CHEST);
        helper.setBlock(DUST.below(), Blocks.STONE);
        helper.setBlock(DUST, Blocks.REDSTONE_WIRE);
        setPower(helper, 3);
        helper.getBlockEntity(CHEST, ChestBlockEntity.class).setItem(0, new ItemStack(Items.IRON_INGOT, 32));
        var players = helper.getLevel().getServer().getPlayerList().getPlayers();
        helper.assertTrue(players.size() == 1, "Read fixture requires one private integrated owner");
        var owner = players.get(0);
        BlockPos absoluteManager = helper.absolutePos(MANAGER);
        ResourceLocation channel = new ResourceLocation("sfm", "read_fixture_" + absoluteManager.asLong());
        SFMClientInboxAddress address = new SFMClientInboxAddress(owner.getUUID(), helper.getLevel().dimension().location(), channel);
        String source = source(helper, channel);
        install(helper, source);
        Minecraft minecraft = Minecraft.getInstance();
        AtomicInteger stage = new AtomicInteger();
        AtomicLong epoch = new AtomicLong(15000);
        AtomicBoolean scheduled = new AtomicBoolean();
        AtomicBoolean published = new AtomicBoolean();
        AtomicBoolean edited = new AtomicBoolean();
        AtomicReference<String> failure = new AtomicReference<>();
        AtomicReference<ClientProgramIdentity> original = new AtomicReference<>();

        helper.succeedWhen(() -> {
            helper.assertTrue(failure.get() == null, String.valueOf(failure.get()));
            if (stage.get() == 1 && SFMServerClientInboxTransport.isSubscribed(owner, address)
                    && published.compareAndSet(false, true)) {
                int amount = helper.getBlockEntity(CHEST, ChestBlockEntity.class).getItem(0).getCount();
                helper.assertTrue(SFMServerClientInboxTransport.publish(owner, address,
                        SFMValue.object(Map.of("amount", SFMValue.of(amount)))) == SFMServerClientInboxTransport.Result.SENT,
                        "Explicit inventory summary broadcast was rejected");
                setPower(helper, 7);
            }
            if (stage.get() == 3 && !SFMServerClientInboxTransport.isSubscribed(owner, address)
                    && edited.compareAndSet(false, true)) install(helper, source + "\n-- changed read scope identity");
            if (stage.get() < 4 && scheduled.compareAndSet(false, true)) minecraft.execute(() -> {
                var screen = minecraft.screen;
                try {
                    if (minecraft.level == null
                            || !(minecraft.level.getBlockEntity(absoluteManager) instanceof ClientManagerBlockEntity manager)
                            || !(minecraft.level.getBlockEntity(helper.absolutePos(DISPLAY)) instanceof TouchDisplayBlockEntity display)
                            || manager.worldId() == null) return;
                    if (stage.get() == 0) {
                        if (!manager.storedSource().equals(source)
                                || minecraft.level.getBlockState(helper.absolutePos(DUST)).getValue(RedStoneWireBlock.POWER) != 3) return;
                        var identity = ClientManagerFrameRuntime.identityFor(manager).orElseThrow(() -> new IllegalStateException(
                                ClientManagerFrameRuntime.diagnosticFor(manager).orElse("Read program did not compile")));
                        original.set(identity);
                        check(identity.requestedCapabilities().containsAll(Set.of(ClientProgramConsentGate.EXECUTE,
                                ClientProgramBlockReadSurface.READ_BOUND, ClientProgramInboxReadSurface.READ)), "Read manifest is incomplete");
                        approve(identity, ClientProgramConsentGate.EXECUTE);
                        frame(display, epoch.incrementAndGet(), true);
                        check(value(display, identity, "blockGateway").equals(SFMValue.of("awaiting_consent")), "Block read bypassed consent");
                        check(value(display, identity, "inboxGateway").equals(SFMValue.of("awaiting_consent")), "Inbox read bypassed consent");
                        approve(identity, ClientProgramBlockReadSurface.READ_BOUND);
                        approve(identity, ClientProgramInboxReadSurface.READ);
                        check(frame(display, epoch.incrementAndGet(), true).isEmpty(), "Read grant borrowed render authority");
                        check(value(display, identity, "power").equals(SFMValue.of(3)), "Replicated dust POWER was not read");
                        var chestValue = (SFMValue.ObjectValue) value(display, identity, "chestValue");
                        check(chestValue.fields().keySet().equals(Set.of("schema", "block", "properties")), "World read replicated chest inventory");
                        var first = ClientProgramReadRuntime.observation(identity).orElseThrow();
                        check(first.blockProjections() == 2 && first.subscriptions() == 1, "Read session was not cached/subscribed");
                        frame(display, epoch.incrementAndGet(), true);
                        frame(display, epoch.incrementAndGet(), false);
                        check(ClientProgramReadRuntime.observation(identity).orElseThrow().blockProjections() == 2,
                                "Unchanged or hidden target rebuilt block projections");
                        stage.set(1);
                    } else if (stage.get() == 1) {
                        if (!published.get()) return;
                        ClientProgramIdentity identity = original.get();
                        frame(display, epoch.incrementAndGet(), true);
                        if (!value(display, identity, "power").equals(SFMValue.of(7))
                                || !value(display, identity, "amount").equals(SFMValue.of(32))) return;
                        check(ClientProgramReadRuntime.observation(identity).orElseThrow().blockProjections() == 3,
                                "One dust update invalidated unrelated cached state");
                        var gate = ClientManagerFrameRuntime.consent();
                        gate.revoke(identity, ClientProgramInboxReadSurface.READ);
                        gate.revoke(identity, ClientProgramBlockReadSurface.READ_BOUND);
                        frame(display, epoch.incrementAndGet(), false);
                        stage.set(2);
                    } else if (stage.get() == 2) {
                        var observation = ClientProgramReadRuntime.observation(original.get()).orElseThrow();
                        if (observation.subscriptions() != 0 || observation.cachedPositions() != 0) return;
                        stage.set(3); // Client tick released subscriptions while this display was ineligible.
                    } else {
                        if (!edited.get() || !manager.storedSource().equals(source + "\n-- changed read scope identity")) return;
                        if (ClientProgramReadRuntime.observation(original.get()).isPresent()) return;
                        check(ClientManagerFrameRuntime.liveIdentityFor(original.get()).isEmpty(), "Stale read identity remained live");
                        ClientManagerFrameRuntime.consent().store().forget(original.get());
                        stage.set(4);
                    }
                    check(minecraft.screen == screen, "Read fixture opened a screen");
                } catch (RuntimeException exception) {
                    failure.set(exception.toString());
                    if (original.get() != null) ClientManagerFrameRuntime.consent().store().forget(original.get());
                } finally { scheduled.set(false); }
            });
            helper.assertTrue(stage.get() == 4, "Waiting for client read stage " + stage.get());
        });
    }

    private static String source(SFMGameTestHelper helper, ResourceLocation channel) {
        return "CLIENT BTW\nEVERY FRAME FOR displays AS display DO\n"
                + json("dustRequest", position(helper.absolutePos(DUST)))
                + "LET dustResponse BE INVOKE sfm:world/block_state/get WITH dustRequest\n"
                + "LET blockGateway BE FIELD \"status\" OF dustResponse\n"
                + "LET dustResult BE FIELD \"result\" OF dustResponse\n"
                + "LET dustValue BE FIELD \"value\" OF dustResult\n"
                + "LET properties BE FIELD \"properties\" OF dustValue\n"
                + "LET power BE FIELD \"power\" OF properties\n"
                + json("chestRequest", position(helper.absolutePos(CHEST)))
                + "LET chestResponse BE INVOKE sfm:world/block_state/get WITH chestRequest\n"
                + "LET chestResult BE FIELD \"result\" OF chestResponse\n"
                + "LET chestValue BE FIELD \"value\" OF chestResult\n"
                + json("inboxRequest", SFMValue.object(Map.of("channel", SFMValue.of(channel.toString()), "mode", SFMValue.of("latest"))))
                + "LET inboxResponse BE INVOKE sfm:client_inbox/read WITH inboxRequest\n"
                + "LET inboxGateway BE FIELD \"status\" OF inboxResponse\n"
                + "LET inboxResult BE FIELD \"result\" OF inboxResponse\n"
                + "LET inboxValue BE FIELD \"value\" OF inboxResult\n"
                + "LET amount BE FIELD \"amount\" OF inboxValue\nEND";
    }
    private static SFMValue position(BlockPos position) {
        return SFMValue.object(Map.of("x", SFMValue.of(position.getX()), "y", SFMValue.of(position.getY()),
                "z", SFMValue.of(position.getZ()), "scope", SFMValue.of("bound")));
    }
    private static String json(String name, SFMValue value) {
        return "LET " + name + " BE JSON \"" + SFMValueSchema.canonicalActionJson(value).replace("\"", "\\\"") + "\"\n";
    }
    private static void setPower(SFMGameTestHelper helper, int power) {
        helper.getLevel().setBlock(helper.absolutePos(DUST), Blocks.REDSTONE_WIRE.defaultBlockState().setValue(RedStoneWireBlock.POWER, power), 2);
    }
    private static void install(SFMGameTestHelper helper, String source) {
        ItemStack disk = new ItemStack(SFMItems.DISK.get());
        DiskItem.setProgram(disk, source);
        LabelPositionHolder.empty().add("displays", helper.absolutePos(DISPLAY)).save(disk);
        helper.getBlockEntity(MANAGER, ClientManagerBlockEntity.class).setDisk(disk);
    }
    private static java.util.Optional<ResourceLocation> frame(TouchDisplayBlockEntity display, long epoch, boolean eligible) {
        return ClientManagerFrameRuntime.textureForSelectedFrame(display, epoch, eligible);
    }
    private static SFMValue value(TouchDisplayBlockEntity display, ClientProgramIdentity identity, String name) {
        return ClientManagerFrameRuntime.valueFor(display, identity, name).orElseThrow(() -> new IllegalStateException("No value: " + name));
    }
    private static void approve(ClientProgramIdentity identity, ResourceLocation permission) {
        var gate = ClientManagerFrameRuntime.consent();
        gate.request(identity, permission); gate.decide(identity, permission, ClientProgramConsentGate.Decision.APPROVE);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
