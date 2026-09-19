package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.client.program.ClientManagerFrameRuntime;
import ca.teamdman.sfm.client.program.ClientProgramConsentGate;
import ca.teamdman.sfm.client.program.ClientProgramIdentity;
import ca.teamdman.sfm.common.block.TouchDisplayBlock;
import ca.teamdman.sfm.common.blockentity.ClientManagerBlockEntity;
import ca.teamdman.sfm.common.blockentity.TouchDisplayBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Ambient scheduler/consent proof using real synchronized block entities.
 * Eligibility is explicit, so this is not visual or camera/occlusion evidence.
 */
@SFMGameTest(SFMDist.CLIENT)
public final class ClientManagerFrameGameTest extends SFMGameTestDefinition {
    private static final BlockPos MANAGER = new BlockPos(0, 2, 0);
    private static final BlockPos OTHER_MANAGER = new BlockPos(0, 2, 2);
    private static final BlockPos DISPLAY = new BlockPos(2, 2, 0);
    private static final BlockPos OTHER_DISPLAY = new BlockPos(2, 2, 2);
    private static final ResourceLocation RED = TouchDisplayBlockEntity.RED_FIXTURE_IMAGE;
    private static final ResourceLocation BLUE = TouchDisplayBlockEntity.BLUE_FIXTURE_IMAGE;
    private static final String STATIC = """
            CLIENT BTW
            EVERY FRAME FOR displays AS display DO
                RENDER IMAGE "%s" TO display
            END
            """.formatted(RED);
    private static final String ANIMATION = """
            CLIENT BTW
            EVERY FRAME FOR displays AS display DO
                IF FRAME MOD 2 EQ 0 THEN
                    RENDER IMAGE "%s" TO display
                ELSE
                    RENDER IMAGE "%s" TO display
                END
            END
            """.formatted(RED, BLUE);

    @Override
    public String template() { return "3x3x3"; }

    @Override
    public int maxTicks() { return 300; }

    @Override
    public void run(SFMGameTestHelper helper) {
        helper.setBlock(MANAGER, SFMBlocks.CLIENT_MANAGER.get());
        for (BlockPos pos : Set.of(DISPLAY, OTHER_DISPLAY)) {
            helper.setBlock(pos, SFMBlocks.TOUCH_DISPLAY.get().defaultBlockState()
                    .setValue(TouchDisplayBlock.FACING, Direction.SOUTH));
        }
        install(helper, MANAGER, STATIC, DISPLAY);

        Minecraft minecraft = Minecraft.getInstance();
        AtomicInteger stage = new AtomicInteger();
        AtomicInteger serverStage = new AtomicInteger();
        AtomicBoolean scheduled = new AtomicBoolean();
        AtomicReference<String> failure = new AtomicReference<>();
        AtomicReference<ClientProgramIdentity> previousIdentity = new AtomicReference<>();
        Set<ClientProgramIdentity> identities = new HashSet<>(); // client thread only
        helper.succeedWhen(() -> {
            helper.assertTrue(failure.get() == null, String.valueOf(failure.get()));
            int next = stage.get();
            if (serverStage.get() != next) {
                switch (next) {
                    case 1 -> install(helper, MANAGER, ANIMATION, DISPLAY);
                    case 2 -> {
                        helper.setBlock(OTHER_MANAGER, SFMBlocks.CLIENT_MANAGER.get());
                        install(helper, OTHER_MANAGER, ANIMATION, DISPLAY);
                    }
                    case 3 -> {
                        helper.setBlock(OTHER_MANAGER, Blocks.AIR);
                        install(helper, MANAGER, ANIMATION, OTHER_DISPLAY);
                    }
                    case 4 -> helper.setBlock(MANAGER, Blocks.AIR);
                    default -> { }
                }
                serverStage.set(next);
            }
            if (next < 5 && scheduled.compareAndSet(false, true)) {
                minecraft.execute(() -> {
                    var screenBefore = minecraft.screen;
                    try {
                        if (minecraft.level == null) return;
                        var level = minecraft.level;
                        if (!(level.getBlockEntity(helper.absolutePos(DISPLAY)) instanceof TouchDisplayBlockEntity display)
                            || !(level.getBlockEntity(helper.absolutePos(OTHER_DISPLAY)) instanceof TouchDisplayBlockEntity otherDisplay)) return;
                        if (next == 4) {
                            if (level.getBlockEntity(helper.absolutePos(MANAGER)) instanceof ClientManagerBlockEntity) return;
                            check(ClientManagerFrameRuntime.liveIdentityFor(previousIdentity.get()).isEmpty(),
                                    "Removed manager retained live authority");
                            check(frame(otherDisplay, 500).isEmpty(), "Removed manager continued ticking");
                            forgetFixtureIdentities(identities);
                            stage.set(5);
                            return;
                        }
                        if (!(level.getBlockEntity(helper.absolutePos(MANAGER)) instanceof ClientManagerBlockEntity manager)
                            || manager.worldId() == null) return;
                        if (!manager.storedSource().equals(next == 0 ? STATIC : ANIMATION)) return;
                        BlockPos expectedTarget = helper.absolutePos(next == 3 ? OTHER_DISPLAY : DISPLAY);
                        if (!manager.labels().getPositions("displays").contains(expectedTarget)) return;
                        ClientProgramIdentity identity = ClientManagerFrameRuntime.identityFor(manager).orElseThrow(() ->
                                new IllegalStateException(ClientManagerFrameRuntime.diagnosticFor(manager).orElse("Missing identity")));
                        identities.add(identity);
                        switch (next) {
                            case 0 -> {
                                check(frame(display, 100).isEmpty(), "Unapproved program ticked");
                                approve(identity, ClientProgramConsentGate.EXECUTE);
                                check(frame(display, 101).isEmpty(), "Execution grant bypassed render grant");
                                approve(identity, ClientProgramConsentGate.RENDER);
                                check(frame(display, 102).orElseThrow().equals(RED), "Approved static program did not render red");
                                check(frame(display, 103).orElseThrow().equals(RED), "Static output changed");
                                check(ClientManagerFrameRuntime.observation(display).evaluations() == 2
                                      && ClientManagerFrameRuntime.observation(display).changedFrames() == 1,
                                        "Equal output should evaluate again without repainting");
                                frame(display, 103);
                                check(ClientManagerFrameRuntime.observation(display).evaluations() == 2,
                                        "Same render epoch executed twice");
                                check(ClientManagerFrameRuntime.textureForSelectedFrame(display, 104, false).isEmpty(),
                                        "Ineligible display executed");
                                check(frame(display, 105).orElseThrow().equals(RED), "Eligible display did not resume");
                                revoke(identity);
                                check(frame(display, 106).isEmpty(), "Revocation did not stop execution");
                                approveBoth(identity);
                                check(ClientManagerFrameRuntime.compileCount(manager) == 1,
                                        "Same synced source compiled on every frame");
                            }
                            case 1 -> {
                                check(!identity.equals(previousIdentity.get()), "Source edit reused consent identity");
                                check(ClientManagerFrameRuntime.liveIdentityFor(previousIdentity.get()).isEmpty(),
                                        "Stale source retained live authority");
                                check(frame(display, 200).isEmpty(), "Source edit retained old approval");
                                approveBoth(identity);
                                check(frame(display, 201).orElseThrow().equals(RED), "Pure animation frame 0 failed");
                                check(frame(display, 202).orElseThrow().equals(BLUE), "Pure animation frame 1 failed");
                                check(frame(display, 203).orElseThrow().equals(RED), "Pure animation frame 2 failed");
                                check(ClientManagerFrameRuntime.observation(display).changedFrames() == 3,
                                        "Pure logic did not change each frame");
                            }
                            case 2 -> {
                                if (!(level.getBlockEntity(helper.absolutePos(OTHER_MANAGER)) instanceof ClientManagerBlockEntity other)
                                    || other.worldId() == null || !other.storedSource().equals(ANIMATION)) return;
                                ClientProgramIdentity otherIdentity = ClientManagerFrameRuntime.identityFor(other).orElseThrow();
                                identities.add(otherIdentity);
                                approveBoth(otherIdentity);
                                check(frame(display, 300).isEmpty(), "Competing writers produced nondeterministic output");
                                check(ClientManagerFrameRuntime.diagnosticFor(display).isPresent(), "Competing writers lacked diagnostic");
                            }
                            case 3 -> {
                                if (level.getBlockEntity(helper.absolutePos(OTHER_MANAGER)) instanceof ClientManagerBlockEntity) return;
                                check(identity.sourceSha256().equals(previousIdentity.get().sourceSha256())
                                      && !identity.bindingSha256().equals(previousIdentity.get().bindingSha256()),
                                        "Retargeted label did not change consent scope");
                                check(frame(display, 400).isEmpty(), "Old label target continued ticking");
                                check(frame(otherDisplay, 401).isEmpty(), "Retargeting borrowed old approval");
                                approveBoth(identity);
                                check(frame(otherDisplay, 402).orElseThrow().equals(RED), "Approved label target did not render");
                            }
                            default -> throw new IllegalStateException("Unexpected fixture stage");
                        }
                        previousIdentity.set(identity);
                        check(minecraft.screen == screenBefore, "Ambient runtime changed the current screen");
                        stage.incrementAndGet();
                    } catch (RuntimeException exception) {
                        failure.set(exception.toString());
                        forgetFixtureIdentities(identities);
                    } finally {
                        scheduled.set(false);
                    }
                });
            }
            helper.assertTrue(stage.get() == 5, "Waiting for ambient client frame stage " + stage.get());
        });
    }

    private static void install(SFMGameTestHelper helper, BlockPos managerPos, String source, BlockPos target) {
        ItemStack disk = new ItemStack(SFMItems.DISK.get());
        DiskItem.setProgram(disk, source);
        LabelPositionHolder.empty().add("displays", helper.absolutePos(target)).save(disk);
        helper.getBlockEntity(managerPos, ClientManagerBlockEntity.class).setDisk(disk);
    }

    private static java.util.Optional<ResourceLocation> frame(TouchDisplayBlockEntity display, long epoch) {
        return ClientManagerFrameRuntime.textureForSelectedFrame(display, epoch, true);
    }

    private static void approveBoth(ClientProgramIdentity identity) {
        approve(identity, ClientProgramConsentGate.EXECUTE);
        approve(identity, ClientProgramConsentGate.RENDER);
    }

    private static void approve(ClientProgramIdentity identity, ResourceLocation capability) {
        var gate = ClientManagerFrameRuntime.consent();
        if (gate.state(identity, capability) == ClientProgramConsentGate.ConsentState.APPROVED) return;
        gate.request(identity, capability);
        gate.decide(identity, capability, ClientProgramConsentGate.Decision.APPROVE);
    }

    private static void revoke(ClientProgramIdentity identity) {
        ClientManagerFrameRuntime.consent().revoke(identity, ClientProgramConsentGate.EXECUTE);
        ClientManagerFrameRuntime.consent().revoke(identity, ClientProgramConsentGate.RENDER);
    }

    private static void forgetFixtureIdentities(Set<ClientProgramIdentity> identities) {
        identities.forEach(identity -> ClientManagerFrameRuntime.consent().store().forget(identity));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
