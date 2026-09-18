package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.common.block.TouchDisplayBlock;
import ca.teamdman.sfm.common.blockentity.TouchDisplayBlockEntity;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Map;

/** Server-safe proof of one atomic, persisted image/state/revision tuple. */
@SFMGameTest
public class TouchDisplayContentCommitGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "3x3x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 0);
        var state = SFMBlocks.TOUCH_DISPLAY.get().defaultBlockState()
                .setValue(TouchDisplayBlock.FACING, Direction.EAST);
        helper.setBlock(pos, state);
        TouchDisplayBlockEntity display = helper.getBlockEntity(pos, TouchDisplayBlockEntity.class);

        helper.assertTrue(display.getBlockState().getValue(TouchDisplayBlock.FACING) == Direction.EAST,
                "Display placement must preserve its outward face");
        helper.assertTrue(display.content().revision() == 0,
                "A new display must start at revision zero");

        SFMValue redState = SFMValue.object(Map.of("color", SFMValue.of("red")));
        SFMValue blueState = SFMValue.object(Map.of("color", SFMValue.of("blue")));
        helper.assertTrue(display.commitContent(TouchDisplayBlockEntity.RED_FIXTURE_IMAGE, redState),
                "First content commit must change the display");
        TouchDisplayBlockEntity.DisplayContent red = display.content();
        helper.assertTrue(red.revision() == 1
                          && red.imageRef().equals(TouchDisplayBlockEntity.RED_FIXTURE_IMAGE)
                          && red.state().equals(redState),
                "Red image and red state must share revision one");

        helper.assertTrue(display.commitContent(TouchDisplayBlockEntity.BLUE_FIXTURE_IMAGE, blueState),
                "Second content commit must change the display");
        TouchDisplayBlockEntity.DisplayContent blue = display.content();
        helper.assertTrue(blue.revision() == 2
                          && blue.imageRef().equals(TouchDisplayBlockEntity.BLUE_FIXTURE_IMAGE)
                          && blue.state().equals(blueState),
                "Blue image and blue state must share revision two");
        helper.assertTrue(red.revision() == 1
                          && red.imageRef().equals(TouchDisplayBlockEntity.RED_FIXTURE_IMAGE)
                          && red.state().equals(redState),
                "An earlier reader must retain an immutable red snapshot");
        helper.assertTrue(!display.commitContent(TouchDisplayBlockEntity.BLUE_FIXTURE_IMAGE, blueState)
                          && display.content().revision() == 2,
                "An unchanged tuple must not allocate a new revision");

        TouchDisplayBlockEntity restored = new TouchDisplayBlockEntity(helper.absolutePos(pos), state);
        restored.load(display.getUpdateTag());
        helper.assertTrue(restored.content().equals(blue),
                "The synced and saved content tag must restore the complete blue tuple");
        helper.assertTrue(restored.getBlockState().getValue(TouchDisplayBlock.FACING) == Direction.EAST,
                "The restored display must retain its block-state orientation");

        var malformed = display.getUpdateTag();
        malformed.getCompound("display_content").putString("image", "SFM:INVALID");
        restored.load(malformed);
        helper.assertTrue(restored.content().imageRef().equals(TouchDisplayBlockEntity.DEFAULT_IMAGE)
                          && restored.content().state().equals(SFMValue.nullValue())
                          && restored.content().revision() == blue.revision(),
                "Malformed persisted image IDs must fail closed without reusing a revision");
        helper.succeed();
    }
}
