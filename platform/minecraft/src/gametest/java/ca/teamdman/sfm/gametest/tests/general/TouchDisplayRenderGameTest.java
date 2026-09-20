package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.client.render.TouchDisplayBlockEntityRenderer;
import ca.teamdman.sfm.common.block.TouchDisplayBlock;
import ca.teamdman.sfm.common.blockentity.TouchDisplayBlockEntity;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Ambient client proof: projection and renderer availability without taking the game window. */
@SFMGameTest(SFMDist.CLIENT)
public class TouchDisplayRenderGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "3x3x1";
    }

    @Override
    public int maxTicks() {
        return 200;
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 0);
        var state = SFMBlocks.TOUCH_DISPLAY.get().defaultBlockState()
                .setValue(TouchDisplayBlock.FACING, Direction.SOUTH);
        helper.setBlock(pos, state);
        SFMValue fixtureState = SFMValue.object(Map.of("fixture", SFMValue.of("red")));
        TouchDisplayBlockEntity display = helper.getBlockEntity(pos, TouchDisplayBlockEntity.class);
        display.commitContent(TouchDisplayBlockEntity.RED_FIXTURE_IMAGE, fixtureState);

        BlockPos absolutePos = helper.absolutePos(pos);
        Minecraft minecraft = Minecraft.getInstance();
        AtomicReference<String> failure = new AtomicReference<>();
        AtomicReference<Boolean> observed = new AtomicReference<>(false);
        Runnable clientProbe = () -> {
            try {
                if (minecraft.level == null) {
                    return;
                }
                if (!(minecraft.level.getBlockEntity(absolutePos) instanceof TouchDisplayBlockEntity clientDisplay)) {
                    return;
                }
                if (clientDisplay.content().revision() != 1) {
                    return;
                }
                if (clientDisplay.getBlockState().getValue(TouchDisplayBlock.FACING) != Direction.SOUTH
                    || !clientDisplay.content().imageRef().equals(TouchDisplayBlockEntity.RED_FIXTURE_IMAGE)
                    || !clientDisplay.content().state().equals(fixtureState)) {
                    failure.set("Client projection did not preserve the server's complete content tuple");
                    return;
                }
                if (!(minecraft.getBlockEntityRenderDispatcher().getRenderer(clientDisplay)
                        instanceof TouchDisplayBlockEntityRenderer)) {
                    failure.set("Touch Display renderer was not registered on the client");
                    return;
                }
                if (minecraft.getResourceManager().getResource(clientDisplay.content().imageRef()).isEmpty()) {
                    failure.set("The selected static image is missing from client resources");
                    return;
                }
                observed.set(true);
            } catch (RuntimeException exception) {
                failure.set(exception.toString());
            }
        };

        helper.succeedWhen(() -> {
            // GameTest retries this server-side assertion on later ticks. Queue
            // one client probe per attempt; never re-enqueue from the client
            // thread, where Minecraft.execute can run inline recursively.
            minecraft.execute(clientProbe);
            helper.assertTrue(failure.get() == null, String.valueOf(failure.get()));
            helper.assertTrue(observed.get(), "Client has not observed the rendered display projection");
        });
    }
}
