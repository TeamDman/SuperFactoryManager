package ca.teamdman.sfm.gametest.tests.compat.computercraft;

import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.item.FormItem;
import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.item.LabelGunItem.LabelGunViewMode;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import dan200.computercraft.api.detail.VanillaDetailRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;

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
        Map<String, Object> blankDiskDetails = VanillaDetailRegistries.ITEM_STACK.getDetails(blankDisk);
        helper.assertTrue(!blankDisk.hasTag(), "Read-only CC item detail created disk NBT");
        helper.assertTrue(
                "program_disk".equals(sfmDetails(blankDiskDetails).get("kind")),
                "CC detailed item query did not expose an SFM program disk"
        );

        ItemStack disk = new ItemStack(SFMItems.DISK.get());
        DiskItem.setProgramName(disk, "CC detail test");
        DiskItem.setProgram(disk, "NAME \"CC detail test\"");
        LabelPositionHolder.from(disk).add("ore", new BlockPos(7, 8, 9)).save(disk);
        Map<String, Object> diskSfm = sfmDetails(VanillaDetailRegistries.ITEM_STACK.getDetails(disk));
        helper.assertTrue("CC detail test".equals(diskSfm.get("name")), "Disk name detail was missing");
        helper.assertTrue(
                "NAME \"CC detail test\"".equals(diskSfm.get("program")),
                "Disk program detail was missing"
        );
        helper.assertTrue(
                ((Map<?, ?>) diskSfm.get("labels")).containsKey("ore"),
                "Disk label detail was missing"
        );

        ItemStack largeDisk = new ItemStack(SFMItems.DISK.get());
        DiskItem.setProgram(largeDisk, "p".repeat(9_000));
        LabelPositionHolder largeLabels = LabelPositionHolder.from(largeDisk);
        for (int position = 0; position < 65; position++) {
            largeLabels.add("a", new BlockPos(position, 0, 0));
        }
        for (int label = 0; label < 16; label++) {
            largeLabels.add("label_" + label, new BlockPos(label, 1, 0));
        }
        largeLabels.save(largeDisk);
        Map<String, Object> largeDiskSfm = sfmDetails(VanillaDetailRegistries.ITEM_STACK.getDetails(largeDisk));
        Map<?, ?> boundedLabels = (Map<?, ?>) largeDiskSfm.get("labels");
        helper.assertTrue(
                ((String) largeDiskSfm.get("program")).length() == 8_192
                        && Boolean.TRUE.equals(largeDiskSfm.get("programTruncated")),
                "Detailed program text was not bounded with a truncation marker"
        );
        helper.assertTrue(
                boundedLabels.size() == 16
                        && ((java.util.List<?>) boundedLabels.get("a")).size() == 64
                        && Boolean.TRUE.equals(largeDiskSfm.get("labelsTruncated")),
                "Detailed labels were not bounded with a truncation marker"
        );

        ItemStack malformedDisk = new ItemStack(SFMItems.DISK.get());
        malformedDisk.getOrCreateTag().putString("sfm:labels", "malformed");
        Map<String, Object> malformedDiskSfm = sfmDetails(VanillaDetailRegistries.ITEM_STACK.getDetails(malformedDisk));
        helper.assertTrue(
                ((Map<?, ?>) malformedDiskSfm.get("labels")).isEmpty(),
                "Malformed label data was not rejected safely"
        );

        ItemStack labelGun = new ItemStack(SFMItems.LABEL_GUN.get());
        LabelGunItem.setActiveLabel(labelGun, "ore");
        LabelGunItem.setViewMode(labelGun, LabelGunViewMode.SHOW_ONLY_TARGETED_BLOCK);
        Map<String, Object> labelGunSfm = sfmDetails(VanillaDetailRegistries.ITEM_STACK.getDetails(labelGun));
        helper.assertTrue("label_gun".equals(labelGunSfm.get("kind")), "Label gun kind detail was missing");
        helper.assertTrue("ore".equals(labelGunSfm.get("activeLabel")), "Label gun active label was missing");
        helper.assertTrue(
                "show_only_targeted_block".equals(labelGunSfm.get("viewMode")),
                "Label gun view mode was missing"
        );

        ItemStack form = FormItem.createFormFromReference(new ItemStack(Items.DIAMOND, 2));
        Map<String, Object> formSfm = sfmDetails(VanillaDetailRegistries.ITEM_STACK.getDetails(form));
        Map<?, ?> reference = (Map<?, ?>) formSfm.get("reference");
        helper.assertTrue("printing_form".equals(formSfm.get("kind")), "Printing form kind detail was missing");
        helper.assertTrue("minecraft:diamond".equals(reference.get("name")), "Form reference item was missing");
        helper.assertTrue(Integer.valueOf(2).equals(reference.get("count")), "Form reference count was missing");

        helper.succeed();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sfmDetails(Map<String, Object> details) {

        Object sfm = details.get("sfm");
        if (!(sfm instanceof Map<?, ?>)) {
            throw new IllegalStateException("CC detailed item query did not include the SFM table");
        }
        return (Map<String, Object>) sfm;
    }
}
