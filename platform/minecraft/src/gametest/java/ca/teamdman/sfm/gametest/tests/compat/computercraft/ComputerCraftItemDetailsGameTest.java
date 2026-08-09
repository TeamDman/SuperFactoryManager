package ca.teamdman.sfm.gametest.tests.compat.computercraft;

import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.item.FormItem;
import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import dan200.computercraft.api.detail.VanillaDetailRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Verifies the former sfm item-detail table has been removed in favour of handle acquisition. */
@SFMGameTest
public class ComputerCraftItemDetailsGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {

        return "2x2x2";
    }

    @Override
    public void run(SFMGameTestHelper helper) {

        ItemStack blankDisk = new ItemStack(SFMItems.DISK.get());
        helper.assertTrue(!blankDisk.hasTag(), "Fresh disk unexpectedly had NBT");
        helper.assertTrue(!VanillaDetailRegistries.ITEM_STACK.getDetails(blankDisk).containsKey("sfm"), "Blank disk retained sfm detail");
        helper.assertTrue(!blankDisk.hasTag(), "CC item detail created disk NBT");

        ItemStack disk = new ItemStack(SFMItems.DISK.get());
        DiskItem.setProgram(disk, "NAME \"CC detail removal test\"");
        LabelPositionHolder.from(disk).add("ore", new BlockPos(7, 8, 9)).save(disk);
        helper.assertTrue(!VanillaDetailRegistries.ITEM_STACK.getDetails(disk).containsKey("sfm"), "Program disk retained sfm detail");

        ItemStack gun = new ItemStack(SFMItems.LABEL_GUN.get());
        LabelGunItem.setActiveLabel(gun, "ore");
        helper.assertTrue(!VanillaDetailRegistries.ITEM_STACK.getDetails(gun).containsKey("sfm"), "Label gun retained sfm detail");

        ItemStack form = FormItem.createFormFromReference(new ItemStack(Items.DIAMOND, 2));
        helper.assertTrue(!VanillaDetailRegistries.ITEM_STACK.getDetails(form).containsKey("sfm"), "Printing form retained sfm detail");
        helper.succeed();
    }
}
