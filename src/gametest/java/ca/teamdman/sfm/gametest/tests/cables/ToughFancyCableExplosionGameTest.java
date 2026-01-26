package ca.teamdman.sfm.gametest.tests.cables;

import ca.teamdman.sfm.common.facade.FacadeData;
import ca.teamdman.sfm.common.facade.FacadeTextureMode;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.block.Blocks;

@SFMGameTest
public class ToughFancyCableExplosionGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x4x3";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        BlockPos localPos = new BlockPos(1, 2, 1);
        BlockPos absolute = helper.absolutePos(localPos);

        // place tough fancy cable facade
        helper.getLevel().setBlock(absolute, SFMBlocks.TOUGH_FANCY_CABLE_FACADE_BLOCK.get().defaultBlockState(), 3);

        // set facade to mimic obsidian
        var be = helper.getLevel().getBlockEntity(absolute);
        if (be instanceof ca.teamdman.sfm.common.blockentity.IFacadeBlockEntity facade) {
            facade.updateFacadeData(new FacadeData(Blocks.OBSIDIAN.defaultBlockState(), Direction.NORTH, FacadeTextureMode.FILL));
        } else {
            helper.fail("Block entity at test position was not a facade BE");
            return;
        }

        // spawn a lit TNT in the hollow center
        var spawnVec = helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2.5, 1.5));
        PrimedTnt primed = new PrimedTnt(helper.getLevel(), spawnVec.x, spawnVec.y, spawnVec.z, null);
        primed.setFuse((short)20);
        helper.getLevel().addFreshEntity(primed);

        // check after 40 ticks that the block still exists
        helper.runAfterDelay(40, () -> {
            if (!helper.getLevel().getBlockState(absolute).is(SFMBlocks.TOUGH_FANCY_CABLE_FACADE_BLOCK.get())) {
                helper.fail("Tough fancy cable facade was destroyed by TNT explosion");
            } else {
                helper.succeed();
            }
        });
    }
}
