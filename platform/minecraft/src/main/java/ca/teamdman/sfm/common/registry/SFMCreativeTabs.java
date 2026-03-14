package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

@MCVersionDependentBehaviour
public class SFMCreativeTabs {
    public static final CreativeModeTab TAB = new SFMCreativeModeTab();

    public static class SFMCreativeModeTab extends CreativeModeTab {
        public SFMCreativeModeTab() {
            super(SFM.MOD_ID);
        }

        @Override
        public ItemStack makeIcon() {
            return new ItemStack(SFMBlocks.MANAGER_BLOCK.get());
        }

        @Override
        public Component getDisplayName() {
            return LocalizationKeys.CREATIVE_TAB.getComponent();
        }
    }
}
