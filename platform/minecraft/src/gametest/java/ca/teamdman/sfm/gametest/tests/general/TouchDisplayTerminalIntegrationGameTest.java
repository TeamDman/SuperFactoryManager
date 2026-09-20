package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.client.program.ClientManagerFrameRuntime;
import ca.teamdman.sfm.client.program.ClientProgramConsentGate;
import ca.teamdman.sfm.client.program.ClientProgramIdentity;
import ca.teamdman.sfm.client.program.ClientProgramInboxReadSurface;
import ca.teamdman.sfm.client.raster.TouchDisplayRasterRuntime;
import ca.teamdman.sfm.client.terminal.TouchDisplayTerminalBinding;
import ca.teamdman.sfm.client.terminal.TouchDisplayTerminalBroker;
import ca.teamdman.sfm.client.terminal.TouchDisplayTerminalRuntime;
import ca.teamdman.sfm.common.blockentity.ClientManagerBlockEntity;
import ca.teamdman.sfm.common.blockentity.TouchDisplayBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.item.PacketItem;
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
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Real packet/inbox/PTY/worker/GPU loop, with no screen, pointing, camera, or keyboard ownership. */
@SFMGameTest(SFMDist.CLIENT)
public final class TouchDisplayTerminalIntegrationGameTest extends SFMGameTestDefinition {
    private final TouchDisplayTerminalVisualControl visual;

    /** Ambient discovery uses this path: no file control, viewport, screen, or real player input. */
    public TouchDisplayTerminalIntegrationGameTest() { visual = null; }

    /** Only the opt-in puppet supplies these test-only pause/input gates. */
    public TouchDisplayTerminalIntegrationGameTest(TouchDisplayTerminalVisualControl visual) {
        this.visual = java.util.Objects.requireNonNull(visual);
    }

    @Override public String template() { return visual == null ? "11x3x3" : "11x5x7"; }
    @Override public int maxTicks() { return visual == null ? 1800 : 20 * 60 * 12; }

    @Override public void run(SFMGameTestHelper helper) {
        var players = helper.getLevel().getServer().getPlayerList().getPlayers();
        helper.assertTrue(players.size() == 1, "Terminal circuit requires one private integrated owner");
        var owner = players.get(0);
        BlockPos managerPos = helper.absolutePos(TouchDisplayCircuitFixture.CLIENT_MANAGER);
        BlockPos displayPos = helper.absolutePos(TouchDisplayCircuitFixture.DISPLAY);
        // ResourceLocation accepts '-', but the current unquoted SFML channel grammar does not.
        var channel = new ResourceLocation("sfm", "terminal_circuit_" + UUID.randomUUID().toString().replace("-", ""));
        var address = new SFMClientInboxAddress(owner.getUUID(), helper.getLevel().dimension().location(), channel);
        var circuit = new TouchDisplayCircuitFixture(helper, owner.getGameProfile().getName(), channel);
        if (visual != null) {
            // The circuit faces SOUTH. Open only this fresh puppet world's camera bay.
            for (int x = 1; x <= 3; x++) for (int y = 1; y <= 4; y++) for (int z = 3; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            }
            helper.setBlock(new BlockPos(5, 3, 3), Blocks.GLOWSTONE);
        }
        helper.setBlock(TouchDisplayCircuitFixture.CLIENT_MANAGER, SFMBlocks.CLIENT_MANAGER.get());
        String source = source(displayPos, channel);
        ItemStack disk = new ItemStack(SFMItems.DISK.get());
        DiskItem.setProgram(disk, source);
        LabelPositionHolder.empty().add("displays", displayPos).save(disk);
        helper.getBlockEntity(TouchDisplayCircuitFixture.CLIENT_MANAGER, ClientManagerBlockEntity.class).setDisk(disk);

        Minecraft minecraft = Minecraft.getInstance();
        var external = new OwnedTouchDisplayTerminalFixture();
        AtomicInteger stage = new AtomicInteger();
        AtomicLong epoch = new AtomicLong(51000);
        AtomicBoolean scheduled = new AtomicBoolean();
        AtomicBoolean ended = new AtomicBoolean();
        AtomicBoolean inputArmed = new AtomicBoolean();
        AtomicReference<String> failure = new AtomicReference<>();
        AtomicReference<ClientProgramIdentity> identity = new AtomicReference<>();
        AtomicReference<TouchDisplayTerminalBinding> binding = new AtomicReference<>();
        AtomicReference<UUID> session = new AtomicReference<>();
        AtomicReference<String> initialDigest = new AtomicReference<>();
        AtomicReference<SFMValue> touch = new AtomicReference<>();
        Runnable cleanup = () -> {
            ended.set(true);
            minecraft.execute(() -> {
                try {
                    if (session.get() != null) TouchDisplayTerminalRuntime.closeSession(session.get());
                } finally {
                    if (identity.get() != null) ClientManagerFrameRuntime.consent().store().forget(identity.get());
                    external.close();
                }
            });
        };
        helper.testInfo.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo test) {}
            @Override public void testPassed(GameTestInfo test) { cleanup.run(); }
            @Override public void testFailed(GameTestInfo test) {
                if (visual != null) visual.fail("Terminal integration GameTest failed; inspect its server assertion");
                cleanup.run();
            }
        });

        helper.succeedWhen(() -> {
            external.failure().ifPresent(code -> failure.compareAndSet(null, code));
            if (visual != null && visual.snapshot().stage() == TouchDisplayTerminalVisualControl.Stage.FAILED) {
                failure.compareAndSet(null, visual.snapshot().failure());
            }
            if (failure.get() != null) cleanup.run();
            helper.assertTrue(failure.get() == null, String.valueOf(failure.get()));
            if (stage.get() == 2 && inputArmed.get() && touch.get() == null && SFMServerClientInboxTransport.isSubscribed(owner, address)) {
                if (visual == null) touch.set(circuit.press());
                else if (visual.pressRequested()) {
                    // Observe the actual vanilla client-use result; never call the server mock press in visual mode.
                    for (BlockPos mailbox : new BlockPos[]{TouchDisplayCircuitFixture.TOUCH_MAILBOX, TouchDisplayCircuitFixture.TOUCH_ARCHIVE}) {
                        var inventory = helper.getItemHandler(mailbox);
                        for (int slot = 0; slot < inventory.getSlots(); slot++) {
                            var value = PacketItem.getValue(inventory.getStackInSlot(slot));
                            if (value.isPresent()) {
                                assertVisualTouch(helper, displayPos, value.orElseThrow());
                                touch.set(value.orElseThrow());
                            }
                        }
                    }
                }
            }
            if (stage.get() == 3) {
                assertServerResult(helper, circuit, touch.get());
                if (visual == null || visual.finishRequested()) stage.set(4);
            }
            if (stage.get() == 5 && !SFMServerClientInboxTransport.isSubscribed(owner, address)) {
                external.close();
                if (external.cleaned()) {
                    if (visual != null) visual.cleaned();
                    stage.set(6);
                }
            }
            if (stage.get() < 5 && scheduled.compareAndSet(false, true)) minecraft.execute(() -> {
                var screen = minecraft.screen;
                var player = minecraft.player;
                var position = player == null ? null : player.position();
                float yaw = player == null ? 0 : player.getYRot(), pitch = player == null ? 0 : player.getXRot();
                try {
                    if (ended.get() || minecraft.level == null
                            || !(minecraft.level.getBlockEntity(managerPos) instanceof ClientManagerBlockEntity manager)
                            || !(minecraft.level.getBlockEntity(displayPos) instanceof TouchDisplayBlockEntity display)
                            || manager.worldId() == null || !manager.storedSource().equals(source)) return;
                    external.prepared().flatMap(transport -> transport.failure()).ifPresent(code -> { throw new IllegalStateException(code); });
                    if (stage.get() == 0) {
                        var prepared = external.prepared();
                        if (prepared.isEmpty()) return;
                        var current = ClientManagerFrameRuntime.identityFor(manager).orElseThrow(() -> new IllegalStateException(
                                ClientManagerFrameRuntime.diagnosticFor(manager).orElse("Terminal fixture source did not compile")));
                        identity.set(current);
                        check(current.requestedCapabilities().equals(Set.of(ClientProgramConsentGate.EXECUTE,
                                ClientProgramConsentGate.RENDER, TouchDisplayTerminalBroker.SESSION, TouchDisplayTerminalBroker.READ,
                                TouchDisplayTerminalBroker.INPUT, ClientProgramInboxReadSurface.READ)),
                                "Terminal fixture action manifest is incomplete or unexpectedly broad");
                        for (var permission : Set.of(ClientProgramConsentGate.EXECUTE, ClientProgramConsentGate.RENDER,
                                TouchDisplayTerminalBroker.SESSION, TouchDisplayTerminalBroker.READ)) approve(current, permission);
                        if (visual == null) selectFrame(display, epoch.incrementAndGet());
                        else if (ClientManagerFrameRuntime.presentationIdentity(display).filter(current::equals).isEmpty()) return;
                        var selected = new TouchDisplayTerminalBinding(current, displayPos, channel, true);
                        binding.set(selected);
                        session.set(TouchDisplayTerminalRuntime.create(selected, prepared.orElseThrow()).orElseThrow(
                                () -> new IllegalStateException("Explicit test-owned terminal mounting was rejected")));
                        check(!TouchDisplayTerminalRuntime.enableInput(selected), "Terminal read approval also granted input");
                        stage.set(1);
                    } else if (stage.get() == 1) {
                        if (visual == null) selectFrame(display, epoch.incrementAndGet());
                        TouchDisplayTerminalRuntime.pump();
                        var status = status(binding.get());
                        if (!SFMValue.of(true).equals(status.get("ready"))) return;
                        var pixels = TouchDisplayTerminalRuntime.textureInfo(binding.get());
                        if (pixels.isEmpty()) return;
                        check(pixels.orElseThrow().width() <= 512 && pixels.orElseThrow().height() <= 512,
                                "Terminal raster exceeded the bounded display dimensions");
                        initialDigest.set(pixels.orElseThrow().sha256());
                        approve(identity.get(), TouchDisplayTerminalBroker.INPUT);
                        approve(identity.get(), ClientProgramInboxReadSurface.READ);
                        check(TouchDisplayTerminalRuntime.enableInput(binding.get()), "Explicit input lease was rejected");
                        stage.set(2);
                    } else if (stage.get() == 2) {
                        if (visual == null) selectFrame(display, epoch.incrementAndGet());
                        TouchDisplayTerminalRuntime.pump();
                        var status = status(binding.get());
                        if (SFMValue.of("ready").equals(status.get("inputState"))) inputArmed.set(true);
                        if (visual != null && inputArmed.get()) {
                            var pixels = TouchDisplayTerminalRuntime.textureInfo(binding.get());
                            if (pixels.isPresent()) visual.ready(pixels.orElseThrow().sha256(), pixels.orElseThrow().width(), pixels.orElseThrow().height());
                        }
                        if (touch.get() == null || !SFMValue.of(1L).equals(status.get("acknowledged"))) return;
                        check(SFMValue.of(1L).equals(status.get("attempted")) && SFMValue.of(0L).equals(status.get("rejected")),
                                "The one touch was rejected, retried, or duplicated");
                        var pixels = TouchDisplayTerminalRuntime.textureInfo(binding.get());
                        if (pixels.isEmpty()) return;
                        String digest = pixels.orElseThrow().sha256();
                        // The visual READY capture may follow an earlier cursor/raster change.
                        // Its own stable baseline, not the ambient stage-1 baseline, gates publication.
                        if (visual == null ? digest.equals(initialDigest.get()) : !visual.hasChangedReadyRaster(digest)) return;
                        check(display.content().revision() == 1 && display.content().state().equals(TouchDisplayCircuitFixture.RED),
                                "Local terminal pixels mutated the server-owned interaction state");
                        if (visual != null) visual.acknowledged(pixels.orElseThrow().sha256(), pixels.orElseThrow().width(),
                                pixels.orElseThrow().height(), 1, 1, 0);
                        stage.set(3);
                    } else if (stage.get() == 4) {
                        ClientManagerFrameRuntime.consent().revoke(identity.get(), TouchDisplayTerminalBroker.INPUT);
                        TouchDisplayTerminalRuntime.pump();
                        check(TouchDisplayTerminalRuntime.sessions().stream().noneMatch(active -> active.id().equals(session.get())),
                                "Input revocation retained an active terminal process session");
                        check(TouchDisplayTerminalRuntime.textureInfo(binding.get()).isEmpty(), "Input-owner revocation retained the raster lease");
                        ClientManagerFrameRuntime.consent().store().forget(identity.get());
                        stage.set(5);
                    }
                } catch (RuntimeException error) {
                    failure.compareAndSet(null, error.getMessage() == null ? "Terminal fixture client operation failed" : error.getMessage());
                    if (visual != null) visual.fail(failure.get());
                    cleanup.run();
                } finally {
                    if (minecraft.screen != screen || minecraft.player != player || (player != null
                            && (!player.position().equals(position) || player.getYRot() != yaw || player.getXRot() != pitch))) {
                        failure.compareAndSet(null, "Ambient terminal fixture changed the screen, player, position, or camera");
                        cleanup.run();
                    }
                    scheduled.set(false);
                }
            });
            helper.assertTrue(stage.get() == 6, "Waiting for ambient terminal integration stage " + stage.get());
            helper.assertTrue(external.failure().isEmpty(), external.failure().orElse("Terminal fixture cleanup failed"));
            assertServerResult(helper, circuit, touch.get());
        });
    }

    private static void assertVisualTouch(SFMGameTestHelper helper, BlockPos display, SFMValue value) {
        helper.assertTrue(value instanceof SFMValue.ObjectValue, "Gameplay touch must be an object");
        var fields = ((SFMValue.ObjectValue) value).fields();
        helper.assertTrue(SFMValue.of("sfm:touch@1").equals(fields.get("schema"))
                && SFMValue.of(helper.getLevel().dimension().location().toString()).equals(fields.get("dimension"))
                && SFMValue.of(display.getX()).equals(fields.get("x")) && SFMValue.of(display.getY()).equals(fields.get("y"))
                && SFMValue.of(display.getZ()).equals(fields.get("z")) && SFMValue.of("south").equals(fields.get("face"))
                && SFMValue.of("press").equals(fields.get("action")) && SFMValue.of(1L).equals(fields.get("contentRevision"))
                && TouchDisplayCircuitFixture.RED.equals(fields.get("state")), "Gameplay touch changed its authoritative address or state");
        helper.assertTrue(fields.get("u") instanceof SFMValue.DoubleValue && fields.get("v") instanceof SFMValue.DoubleValue,
                "Gameplay touch must retain floating-point UV coordinates");
        helper.assertTrue(Math.abs(((SFMValue.DoubleValue) fields.get("u")).value() - TouchDisplayTerminalVisualControl.U) <= 1.0e-4
                && Math.abs(((SFMValue.DoubleValue) fields.get("v")).value() - TouchDisplayTerminalVisualControl.V) <= 1.0e-4,
                "Gameplay touch did not preserve the requested UV coordinates");
    }

    private static void assertServerResult(SFMGameTestHelper helper, TouchDisplayCircuitFixture circuit, SFMValue touch) {
        helper.assertCount(helper.getItemHandler(TouchDisplayCircuitFixture.TOUCH_ARCHIVE), PacketItem.create(touch), 1,
                "Server manager has not archived exactly one original touch packet");
        helper.assertTrue(TouchDisplayCircuitFixture.packetCount(helper.getItemHandler(TouchDisplayCircuitFixture.TOUCH_ARCHIVE)) == 1,
                "Terminal circuit duplicated a touch event");
        helper.assertCount(helper.getItemHandler(TouchDisplayCircuitFixture.TOUCH_MAILBOX), 0, "Touch mailbox was not consumed");
        helper.assertCount(helper.getItemHandler(TouchDisplayCircuitFixture.COMMAND_ARCHIVE), 0, "Terminal circuit invented a server command");
        helper.assertTrue(circuit.display.content().revision() == 1 && circuit.display.content().state().equals(TouchDisplayCircuitFixture.RED),
                "Terminal raster updates changed authoritative display state");
    }

    private static String source(BlockPos display, ResourceLocation channel) {
        SFMValue input = SFMValue.object(Map.of("x", SFMValue.of(display.getX()), "y", SFMValue.of(display.getY()),
                "z", SFMValue.of(display.getZ()), "channel", SFMValue.of(channel.toString()), "input", SFMValue.of(true)));
        return "CLIENT BTW\nEVERY FRAME FOR displays AS display DO\nLET terminalBinding BE JSON \""
                + SFMValueSchema.canonicalActionJson(input).replace("\"", "\\\"") + "\"\n"
                + "LET terminalStatus BE INVOKE \"sfm:terminal/display\" WITH terminalBinding\n"
                + "LET inputStatus BE INVOKE \"sfm:terminal/input/status\" WITH terminalBinding\n"
                + "RENDER IMAGE \"minecraft:textures/block/red_concrete.png\" TO display\nEND";
    }
    private static void selectFrame(TouchDisplayBlockEntity display, long epoch) {
        check(ClientManagerFrameRuntime.textureForSelectedFrame(display, epoch, true).isPresent(), "Approved client fixture stopped rendering");
        TouchDisplayRasterRuntime.textureForSelectedFrame(display, epoch);
    }
    private static Map<String, SFMValue> status(TouchDisplayTerminalBinding binding) {
        return ((SFMValue.ObjectValue) TouchDisplayTerminalRuntime.status(binding)).fields();
    }
    private static void approve(ClientProgramIdentity identity, ResourceLocation capability) {
        var gate = ClientManagerFrameRuntime.consent();
        gate.request(identity, capability);
        gate.decide(identity, capability, ClientProgramConsentGate.Decision.APPROVE);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
