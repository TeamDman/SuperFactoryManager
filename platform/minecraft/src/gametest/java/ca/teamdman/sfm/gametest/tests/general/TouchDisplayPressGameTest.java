package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.common.block.TouchDisplayBlock;
import ca.teamdman.sfm.common.blockentity.TouchDisplayBlockEntity;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Ambient integrated-client variant: no camera movement, input injection or screen ownership. */
@SFMGameTest(SFMDist.CLIENT)
public class TouchDisplayPressGameTest extends TouchDisplayPressServerGameTest {
    @Override
    public void run(SFMGameTestHelper helper) {
        runProof(helper);

        BlockPos absoluteDisplay = helper.absolutePos(DISPLAY);
        AtomicBoolean probeScheduled = new AtomicBoolean();
        AtomicBoolean clientUsePassed = new AtomicBoolean();
        AtomicReference<String> clientFailure = new AtomicReference<>();
        helper.succeedWhen(() -> {
            helper.assertTrue(clientFailure.get() == null, String.valueOf(clientFailure.get()));
            if (!clientUsePassed.get() && probeScheduled.compareAndSet(false, true)) {
                Minecraft.getInstance().execute(() -> {
                    try {
                        Minecraft minecraft = Minecraft.getInstance();
                        if (minecraft.level == null || minecraft.player == null) {
                            return;
                        }
                        if (!(minecraft.level.getBlockEntity(absoluteDisplay)
                                instanceof TouchDisplayBlockEntity)) {
                            return;
                        }
                        BlockState state = minecraft.level.getBlockState(absoluteDisplay);
                        if (!(state.getBlock() instanceof TouchDisplayBlock)
                            || state.getValue(TouchDisplayBlock.FACING) != Direction.NORTH) {
                            clientFailure.set("Client did not project the server's north-facing Touch Display");
                            return;
                        }

                        // A direct client-side use tests prediction/no-GUI without
                        // moving the player or injecting a real mouse click.
                        var before = minecraft.screen;
                        InteractionResult result = state.getBlock().use(
                                state,
                                minecraft.level,
                                absoluteDisplay,
                                minecraft.player,
                                InteractionHand.MAIN_HAND,
                                hit(absoluteDisplay, Direction.NORTH, 0.25, 0.75)
                        );
                        if (result != InteractionResult.SUCCESS) {
                            clientFailure.set("Valid client-side touch was not predicted as SUCCESS: " + result);
                        } else if (minecraft.screen != before) {
                            clientFailure.set("Touch Display changed the currently open screen");
                        } else {
                            clientUsePassed.set(true);
                        }
                    } catch (RuntimeException failure) {
                        clientFailure.set(failure.toString());
                    } finally {
                        probeScheduled.set(false);
                    }
                });
            }
            helper.assertTrue(clientUsePassed.get(), "Client Touch Display projection/use has not completed");
        });
    }
}
