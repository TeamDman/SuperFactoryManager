package ca.teamdman.sfm.common.item;

import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMCreativeTabs;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PrintingPressBlockItem extends BlockItem {
    public PrintingPressBlockItem() {
        super(SFMBlocks.PRINTING_PRESS_BLOCK.get(), new Properties().tab(SFMCreativeTabs.TAB));
    }

    @Override
    public void appendHoverText(ItemStack pStack, @Nullable Level pLevel, List<Component> pTooltip, TooltipFlag pFlag) {
        super.appendHoverText(pStack, pLevel, pTooltip, pFlag);
        pTooltip.add(LocalizationKeys.PRINTING_PRESS_TOOLTIP.getComponent().withStyle(ChatFormatting.GRAY));
    }
}
