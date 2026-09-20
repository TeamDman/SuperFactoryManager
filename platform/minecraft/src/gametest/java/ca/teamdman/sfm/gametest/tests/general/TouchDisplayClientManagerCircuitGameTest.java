package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.client.program.ClientManagerFrameRuntime;
import ca.teamdman.sfm.client.program.ClientProgramConsentGate;
import ca.teamdman.sfm.client.program.ClientProgramIdentity;
import ca.teamdman.sfm.client.program.ClientProgramInboxReadSurface;
import ca.teamdman.sfm.client.program.ClientProgramReadRuntime;
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
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Screen-free integrated circuit. Only consent decisions, server use and render
 * eligibility are test seams; live SFML, inventories and transport do the work.
 * Actual client pointing/use and visible pixels have separate opt-in puppets.
 */
@SFMGameTest(SFMDist.CLIENT)
public final class TouchDisplayClientManagerCircuitGameTest extends SFMGameTestDefinition {
    private static final ResourceLocation SEND = new ResourceLocation("sfm", "packet/send");

    @Override public String template() { return "11x3x3"; }
    @Override public int maxTicks() { return 500; }

    @Override public void run(SFMGameTestHelper helper) {
        var players = helper.getLevel().getServer().getPlayerList().getPlayers();
        helper.assertTrue(players.size() == 1, "Circuit requires one private integrated owner");
        var owner = players.get(0);
        BlockPos absoluteManager = helper.absolutePos(TouchDisplayCircuitFixture.CLIENT_MANAGER);
        BlockPos absoluteDisplay = helper.absolutePos(TouchDisplayCircuitFixture.DISPLAY);
        var channel = new ResourceLocation("sfm", "client_circuit_" + absoluteManager.asLong());
        var address = new SFMClientInboxAddress(owner.getUUID(), helper.getLevel().dimension().location(), channel);
        var fixture = new TouchDisplayCircuitFixture(helper, owner.getGameProfile().getName(), channel);
        helper.setBlock(TouchDisplayCircuitFixture.CLIENT_MANAGER, SFMBlocks.CLIENT_MANAGER.get());
        String source = source(helper, channel);
        ItemStack disk = new ItemStack(SFMItems.DISK.get());
        DiskItem.setProgram(disk, source);
        LabelPositionHolder.empty().add("displays", absoluteDisplay).save(disk);
        helper.getBlockEntity(TouchDisplayCircuitFixture.CLIENT_MANAGER, ClientManagerBlockEntity.class).setDisk(disk);

        Minecraft minecraft = Minecraft.getInstance();
        AtomicInteger stage = new AtomicInteger();
        AtomicLong epoch = new AtomicLong(31000);
        AtomicBoolean scheduled = new AtomicBoolean();
        AtomicReference<SFMValue> touch = new AtomicReference<>();
        AtomicReference<ClientProgramIdentity> identity = new AtomicReference<>();
        AtomicReference<String> failure = new AtomicReference<>();
        AtomicBoolean ended = new AtomicBoolean();
        // A timeout/failure must not leave even ephemeral test approval running.
        helper.testInfo.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo test) {}
            @Override public void testPassed(GameTestInfo test) { cleanup(); }
            @Override public void testFailed(GameTestInfo test) { cleanup(); }
            private void cleanup() {
                ended.set(true);
                minecraft.execute(() -> {
                    if (identity.get() != null) ClientManagerFrameRuntime.consent().store().forget(identity.get());
                });
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(failure.get() == null, String.valueOf(failure.get()));
            if (stage.get() == 1 && touch.get() == null
                && SFMServerClientInboxTransport.isSubscribed(owner, address)) {
                helper.assertTrue(fixture.display.content().revision() == 1
                                  && fixture.display.content().state().equals(TouchDisplayCircuitFixture.RED),
                        "Display changed before its approved command existed");
                touch.set(fixture.press());
            }
            if (stage.get() == 2) {
                fixture.assertCompleted(touch.get());
                stage.set(3);
            }
            if (stage.get() == 4 && !SFMServerClientInboxTransport.isSubscribed(owner, address)) stage.set(5);
            if (stage.get() < 5 && scheduled.compareAndSet(false, true)) minecraft.execute(() -> {
                var screen = minecraft.screen;
                try {
                    if (ended.get() || minecraft.level == null
                        || !(minecraft.level.getBlockEntity(absoluteManager) instanceof ClientManagerBlockEntity manager)
                        || !(minecraft.level.getBlockEntity(absoluteDisplay) instanceof TouchDisplayBlockEntity display)
                        || manager.worldId() == null || !manager.storedSource().equals(source)) return;
                    if (stage.get() == 0) {
                        var current = ClientManagerFrameRuntime.identityFor(manager).orElseThrow(() -> new IllegalStateException(
                                ClientManagerFrameRuntime.diagnosticFor(manager).orElse("Circuit client source did not compile")));
                        identity.set(current);
                        check(current.requestedCapabilities().equals(Set.of(ClientProgramConsentGate.EXECUTE,
                                ClientProgramConsentGate.RENDER, ClientProgramInboxReadSurface.READ, SEND)),
                                "Circuit permission manifest is incomplete or wider than its program");
                        check(frame(display, epoch.incrementAndGet()).isEmpty(), "Unapproved circuit executed");
                        approve(current, ClientProgramConsentGate.EXECUTE);
                        approve(current, ClientProgramConsentGate.RENDER);
                        check(frame(display, epoch.incrementAndGet()).orElseThrow().equals(TouchDisplayBlockEntity.RED_FIXTURE_IMAGE),
                                "Unsubscribed client circuit did not start with its red fallback");
                        check(value(display, current, "readGateway").equals(SFMValue.of("awaiting_consent")),
                                "Inbox read borrowed execution/render approval");
                        check(value(display, current, "sendGateway").equals(SFMValue.of("awaiting_consent")),
                                "Packet send borrowed execution/render approval");
                        approve(current, ClientProgramInboxReadSurface.READ);
                        frame(display, epoch.incrementAndGet());
                        check(ClientProgramReadRuntime.observation(current).orElseThrow().subscriptions() == 1,
                                "Typed inbox action did not create its exact declared subscription");
                        stage.set(1);
                    } else if (stage.get() == 1) {
                        var current = identity.get();
                        frame(display, epoch.incrementAndGet());
                        if (touch.get() == null || !value(display, current, "touchValue").equals(touch.get())) return;
                        check(value(display, current, "touchSchema").equals(SFMValue.of("sfm:touch@1"))
                              && value(display, current, "touchColor").equals(SFMValue.of("red")),
                                "Typed FIELD reads lost the original server-owned touch state");
                        var painted = ClientManagerFrameRuntime.observation(display);
                        check(TouchDisplayBlockEntity.BLUE_FIXTURE_IMAGE.equals(painted.texture())
                              && painted.changedFrames() == 2, "Received touch did not drive exactly one red-to-blue repaint");
                        check(display.content().revision() == 1 && display.content().state().equals(TouchDisplayCircuitFixture.RED),
                                "Client repaint mutated server semantic state before the command");

                        // Approval lasts for one evaluated frame in this fixture.
                        // Revocation is in the same client-thread task, so a normal BER cannot race a second send.
                        approve(current, SEND);
                        try {
                            long sendEpoch = epoch.incrementAndGet();
                            frame(display, sendEpoch);
                            frame(display, sendEpoch);
                            check(value(display, current, "sendGateway").equals(SFMValue.of("ok"))
                                  && value(display, current, "sendStatus").equals(SFMValue.of("send_attempted")),
                                    "Approved client action did not attempt the typed command send");
                        } finally { ClientManagerFrameRuntime.consent().revoke(current, SEND); }
                        frame(display, epoch.incrementAndGet());
                        check(value(display, current, "sendGateway").equals(SFMValue.of("awaiting_consent")),
                                "Revoked send remained usable");
                        stage.set(2);
                    } else if (stage.get() == 3) {
                        if (display.content().revision() != 2 || !display.content().state().equals(TouchDisplayCircuitFixture.BLUE)) return;
                        check(display.content().imageSnapshot() != null
                              && display.content().imageSnapshot().equals(fixture.nextImage.snapshot().orElseThrow()),
                                "Client projection did not receive the manager's atomic image/state commit");
                        frame(display, epoch.incrementAndGet());
                        check(value(display, identity.get(), "touchValue").equals(touch.get()),
                                "Later semantic content rewrote the earlier touch snapshot");
                        check(ClientManagerFrameRuntime.observation(display).changedFrames() == 2,
                                "Unchanged client output unnecessarily repainted");
                        ClientManagerFrameRuntime.consent().store().forget(identity.get());
                        check(frame(display, epoch.incrementAndGet()).isEmpty(), "Revoked circuit retained presentation authority");
                        stage.set(4);
                    } else if (stage.get() == 4) {
                        check(frame(display, epoch.incrementAndGet()).isEmpty(), "Completed circuit resumed without consent");
                    }
                    check(minecraft.screen == screen, "Ambient circuit changed the active screen");
                } catch (RuntimeException exception) {
                    failure.set(exception.toString());
                    if (identity.get() != null) ClientManagerFrameRuntime.consent().store().forget(identity.get());
                } finally { scheduled.set(false); }
            });
            helper.assertTrue(stage.get() == 5, "Waiting for integrated touch/client-manager circuit stage " + stage.get());
            fixture.assertCompleted(touch.get());
        });
    }

    private static String source(SFMGameTestHelper helper, ResourceLocation channel) {
        BlockPos mailbox = helper.absolutePos(TouchDisplayCircuitFixture.COMMAND_MAILBOX);
        SFMValue command = SFMValue.object(Map.of(
                "dimension", SFMValue.of(helper.getLevel().dimension().location().toString()),
                "x", SFMValue.of(mailbox.getX()), "y", SFMValue.of(mailbox.getY()), "z", SFMValue.of(mailbox.getZ()),
                "value", TouchDisplayCircuitFixture.COMMAND));
        return "CLIENT BTW\nEVERY FRAME FOR displays AS display DO\n"
                + json("readRequest", SFMValue.object(Map.of("channel", SFMValue.of(channel.toString()), "mode", SFMValue.of("latest"))))
                + """
                LET readResponse BE INVOKE sfm:client_inbox/read WITH readRequest
                LET readGateway BE FIELD "status" OF readResponse
                LET readResult BE FIELD "result" OF readResponse
                LET touchValue BE FIELD "value" OF readResult
                LET touchSchema BE FIELD "schema" OF touchValue
                LET touchState BE FIELD "state" OF touchValue
                LET touchColor BE FIELD "color" OF touchState
                """
                + json("sendRequest", command)
                + """
                LET sendResponse BE INVOKE sfm:packet/send WITH sendRequest
                LET sendGateway BE FIELD "status" OF sendResponse
                LET sendResult BE FIELD "result" OF sendResponse
                LET sendStatus BE FIELD "status" OF sendResult
                """
                + "IF touchSchema EQ JSON \"\\\"sfm:touch@1\\\"\" AND touchColor EQ JSON \"\\\"red\\\"\" THEN\n"
                + "RENDER IMAGE \"minecraft:textures/block/blue_concrete.png\" TO display\n"
                + "ELSE\nRENDER IMAGE \"minecraft:textures/block/red_concrete.png\" TO display\nEND\nEND";
    }

    private static String json(String name, SFMValue value) {
        return "LET " + name + " BE JSON \"" + SFMValueSchema.canonicalActionJson(value).replace("\"", "\\\"") + "\"\n";
    }
    private static java.util.Optional<ResourceLocation> frame(TouchDisplayBlockEntity display, long epoch) {
        return ClientManagerFrameRuntime.textureForSelectedFrame(display, epoch, true);
    }
    private static SFMValue value(TouchDisplayBlockEntity display, ClientProgramIdentity identity, String name) {
        return ClientManagerFrameRuntime.valueFor(display, identity, name)
                .orElseThrow(() -> new IllegalStateException("Circuit value is absent: " + name));
    }
    private static void approve(ClientProgramIdentity identity, ResourceLocation capability) {
        var gate = ClientManagerFrameRuntime.consent();
        gate.request(identity, capability);
        gate.decide(identity, capability, ClientProgramConsentGate.Decision.APPROVE);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
