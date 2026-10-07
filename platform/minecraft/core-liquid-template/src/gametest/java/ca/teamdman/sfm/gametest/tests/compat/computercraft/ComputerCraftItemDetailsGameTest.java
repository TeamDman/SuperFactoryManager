package ca.teamdman.sfm.gametest.tests.compat.computercraft;

import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.item.FormItem;
import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1', '26.1.2' %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% endcase %}
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import dan200.computercraft.api.detail.VanillaDetailRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Verifies the former sfm item-detail table has been removed in favour of handle acquisition. */
@SFMGameTest
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1', '26.1.2' %}
@MCVersionDependentBehaviour // 1.21+ uses typed item components instead of mutable NBT
{% endcase %}
public class ComputerCraftItemDetailsGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {

        return "2x2x2";
    }

    @Override
    public void run(SFMGameTestHelper helper) {

        ItemStack blankDisk = new ItemStack(SFMItems.DISK.get());
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        helper.assertTrue(!blankDisk.hasTag(), "Fresh disk unexpectedly had NBT");
{% when '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
        helper.assertTrue(!VanillaDetailRegistries.ITEM_STACK.getDetails(blankDisk).containsKey("sfm"), "Blank disk retained sfm detail");
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        helper.assertTrue(!blankDisk.hasTag(), "CC item detail created disk NBT");
{% when '1.21', '1.21.1', '26.1.2' %}
{% endcase %}

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
