package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.client.program.*;
import ca.teamdman.sfm.common.block.TouchDisplayBlock;
import ca.teamdman.sfm.common.blockentity.ClientManagerBlockEntity;
import ca.teamdman.sfm.common.blockentity.TouchDisplayBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Real client-to-server packet insertion without a screen, camera, or human-principal adapter. */
@SFMGameTest(SFMDist.CLIENT)
public final class ClientProgramInvocationGameTest extends SFMGameTestDefinition {
    private static final BlockPos MANAGER = new BlockPos(0, 2, 0);
    private static final BlockPos DISPLAY = new BlockPos(2, 2, 0);
    private static final BlockPos CHEST = new BlockPos(0, 2, 2);
    private static final ResourceLocation SEND = new ResourceLocation("sfm", "packet/send");

    @Override public String template() { return "3x3x3"; }
    @Override public int maxTicks() { return 300; }

    @Override public void run(SFMGameTestHelper helper) {
        helper.setBlock(MANAGER, SFMBlocks.CLIENT_MANAGER.get());
        helper.setBlock(DISPLAY, SFMBlocks.TOUCH_DISPLAY.get().defaultBlockState().setValue(TouchDisplayBlock.FACING, Direction.SOUTH));
        helper.setBlock(CHEST, Blocks.CHEST);
        BlockPos target = helper.absolutePos(CHEST);
        SFMValue request = SFMValue.object(Map.of("dimension", SFMValue.of(helper.getLevel().dimension().location().toString()),
                "x", SFMValue.of(target.getX()), "y", SFMValue.of(target.getY()), "z", SFMValue.of(target.getZ()),
                "value", SFMValue.object(Map.of("schema", SFMValue.of("sfm:client_program_test@1")))));
        String source = "CLIENT BTW\nEVERY FRAME FOR displays AS display DO\n"
                + "LET request BE JSON \"" + SFMValueSchema.canonicalActionJson(request).replace("\"", "\\\"") + "\"\n"
                + "LET response BE INVOKE sfm:packet/send WITH request\n"
                + "LET gateway BE FIELD \"status\" OF response\n"
                + "LET result BE FIELD \"result\" OF response\n"
                + "LET status BE FIELD \"status\" OF result\n"
                + "RENDER IMAGE \"minecraft:textures/block/red_concrete.png\" TO display\nEND";
        install(helper, source);
        Minecraft minecraft = Minecraft.getInstance();
        AtomicInteger stage = new AtomicInteger();
        AtomicBoolean scheduled = new AtomicBoolean();
        AtomicBoolean edited = new AtomicBoolean();
        AtomicReference<String> failure = new AtomicReference<>();
        AtomicReference<ClientProgramIdentity> oldIdentity = new AtomicReference<>();
        Set<ClientProgramIdentity> identities = new HashSet<>();
        helper.succeedWhen(() -> {
            helper.assertTrue(failure.get() == null, String.valueOf(failure.get()));
            if (stage.get() == 1) {
                ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
                int count = 0;
                for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                    ItemStack stack = chest.getItem(slot);
                    if (stack.is(SFMItems.PACKET.get())) count += stack.getCount();
                }
                helper.assertTrue(count == 1, "Expected exactly one typed program packet, got " + count);
                if (edited.compareAndSet(false, true)) install(helper, source + "\n-- changed revision");
            }
            if (stage.get() < 2 && scheduled.compareAndSet(false, true)) minecraft.execute(() -> {
                var screen = minecraft.screen;
                try {
                    if (minecraft.level == null
                        || !(minecraft.level.getBlockEntity(helper.absolutePos(MANAGER)) instanceof ClientManagerBlockEntity manager)
                        || !(minecraft.level.getBlockEntity(helper.absolutePos(DISPLAY)) instanceof TouchDisplayBlockEntity display)
                        || manager.worldId() == null) return;
                    if (!manager.storedSource().equals(source + (stage.get() == 1 ? "\n-- changed revision" : ""))) return;
                    ClientProgramIdentity identity = ClientManagerFrameRuntime.identityFor(manager).orElseThrow(() ->
                            new IllegalStateException(ClientManagerFrameRuntime.diagnosticFor(manager).orElse("Missing typed identity")));
                    identities.add(identity);
                    if (stage.get() == 0) {
                        check(identity.requestedCapabilities().containsAll(Set.of(ClientProgramConsentGate.EXECUTE, SEND, ClientProgramConsentGate.RENDER)),
                                "Static manifest missed requested permissions");
                        check(frame(display, 700).isEmpty(), "Unapproved invocation executed");
                        approve(identity, ClientProgramConsentGate.EXECUTE);
                        frame(display, 701);
                        check(value(display, identity, "gateway").equals(SFMValue.of("awaiting_consent")), "Action borrowed execution approval");
                        var gate = ClientManagerFrameRuntime.consent();
                        gate.request(identity, SEND);
                        gate.decide(identity, SEND, ClientProgramConsentGate.Decision.DENY);
                        frame(display, 702);
                        check(value(display, identity, "gateway").equals(SFMValue.of("denied_by_user")), "Denied action executed");
                        gate.reopenDenied(identity, SEND);
                        gate.decide(identity, SEND, ClientProgramConsentGate.Decision.APPROVE);
                        check(frame(display, 703).isEmpty(), "Action grant borrowed drawing authority");
                        check(value(display, identity, "gateway").equals(SFMValue.of("ok")), "Typed dispatcher rejected approved action");
                        check(value(display, identity, "status").equals(SFMValue.of("send_attempted")), "Typed result was not bound");
                        frame(display, 703); // must not enqueue a second insertion in one epoch
                        gate.revoke(identity, SEND);
                        frame(display, 704);
                        check(value(display, identity, "gateway").equals(SFMValue.of("awaiting_consent")), "Revocation did not affect next invocation");
                        oldIdentity.set(identity);
                        stage.set(1);
                    } else {
                        check(!identity.equals(oldIdentity.get()), "Source revision reused consent identity");
                        check(ClientManagerFrameRuntime.liveIdentityFor(oldIdentity.get()).isEmpty(), "Old source retained live authority");
                        check(frame(display, 705).isEmpty(), "New revision borrowed approval");
                        identities.forEach(saved -> ClientManagerFrameRuntime.consent().store().forget(saved));
                        stage.set(2);
                    }
                    check(minecraft.screen == screen, "Typed program test opened a screen");
                } catch (RuntimeException exception) {
                    failure.set(exception.toString());
                    identities.forEach(saved -> ClientManagerFrameRuntime.consent().store().forget(saved));
                } finally { scheduled.set(false); }
            });
            helper.assertTrue(stage.get() == 2, "Waiting for typed client invocation stage " + stage.get());
        });
    }

    private static void install(SFMGameTestHelper helper, String source) {
        ItemStack disk = new ItemStack(SFMItems.DISK.get());
        DiskItem.setProgram(disk, source);
        LabelPositionHolder.empty().add("displays", helper.absolutePos(DISPLAY)).save(disk);
        helper.getBlockEntity(MANAGER, ClientManagerBlockEntity.class).setDisk(disk);
    }
    private static java.util.Optional<ResourceLocation> frame(TouchDisplayBlockEntity display, long epoch) {
        return ClientManagerFrameRuntime.textureForSelectedFrame(display, epoch, true);
    }
    private static SFMValue value(TouchDisplayBlockEntity display, ClientProgramIdentity identity, String name) {
        return ClientManagerFrameRuntime.valueFor(display, identity, name).orElseThrow(() -> new IllegalStateException("No value: " + name));
    }
    private static void approve(ClientProgramIdentity identity, ResourceLocation permission) {
        var gate = ClientManagerFrameRuntime.consent();
        gate.request(identity, permission); gate.decide(identity, permission, ClientProgramConsentGate.Decision.APPROVE);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
