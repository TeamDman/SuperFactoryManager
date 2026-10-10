package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import ca.teamdman.sfm.gametest.declarative.SFMDeclarativeTestBuilder;
import ca.teamdman.sfm.gametest.declarative.SFMTestBlockEntitySpec;
import ca.teamdman.sfm.gametest.declarative.SFMTestSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

@SFMGameTest
public class HasExceptGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        SFMTestSpec spec = new SFMTestSpec()
                .setProgram("""
                        EVERY 20 TICKS DO
                            IF a HAS EQ 1 EXCEPT stick THEN
                                INPUT 1 oak_planks FROM a
                                OUTPUT TO b
                            END
                        END
                    """)
                .addBlock(SFMTestBlockEntitySpec.<BlockEntity>of(
                        "a",
                        new BlockPos(1, 0, 0),
                        SFMBlocks.TEST_BARREL.get(),
                        blockEntity -> {
                            Container container = (Container) blockEntity;
                            container.setItem(0, new ItemStack(Items.STICK));
                            container.setItem(1, new ItemStack(Items.OAK_PLANKS));
                        }
                ))
                .addBlock(SFMTestBlockEntitySpec.of(
                        "b",
                        new BlockPos(-1, 0, 0),
                        SFMBlocks.TEST_BARREL.get()
                ))
                .preCondition("a HAS EQ 1 EXCEPT stick")
                .postCondition("a HAS EQ 0 EXCEPT stick")
                .postCondition("a HAS EQ 1 stick")
                .postCondition("b HAS EQ 1 oak_planks");
        new SFMDeclarativeTestBuilder(helper, spec).run();
    }
}
