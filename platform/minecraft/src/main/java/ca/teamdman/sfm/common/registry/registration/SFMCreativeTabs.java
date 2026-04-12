package ca.teamdman.sfm.common.registry.registration;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

@MCVersionDependentBehaviour
public class SFMCreativeTabs {
    public static final CreativeModeTab MAIN = new SFMCreativeModeTab();

    @SFMLocalizationDatagen
    public static final LocalizationEntry CREATIVE_TAB_NAME = new LocalizationEntry(
            "item_group.sfm",
            "Super Factory Manager"
    );

    public static class SFMCreativeModeTab extends CreativeModeTab {
        public SFMCreativeModeTab() {

            super(SFM.MOD_ID);
        }

        @Override
        public ItemStack makeIcon() {

            return new ItemStack(SFMBlocks.MANAGER.get());
        }

        @Override
        public Component getDisplayName() {

            return CREATIVE_TAB_NAME.getComponent();
        }

    }

}
