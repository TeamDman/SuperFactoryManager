package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.gametest.LeftRightManagerTest;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

@SFMGameTest
public class MoveWithComponentGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        ItemStack damagedSword = new ItemStack(Items.IRON_SWORD);
        damagedSword.set(DataComponents.DAMAGE, 12);
        ItemStack pristineSword = new ItemStack(Items.IRON_SWORD);

        new LeftRightManagerTest(helper)
                .setProgram("""
                    EVERY 20 TICKS DO
                        INPUT WITH COMPONENT minecraft:damage = "12" FROM left
                        OUTPUT TO right
                    END
                    """)
                .preContents("left", List.of(damagedSword, pristineSword))
                .postContents("left", List.of(ItemStack.EMPTY, pristineSword))
                .postContents("right", List.of(damagedSword))
                .run();
    }
}
