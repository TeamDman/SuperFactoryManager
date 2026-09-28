package ca.teamdman.sfm.common.item;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class PrintingPressBlockItem extends BlockItem {
    @SFMLocalizationDatagen
    public static final LocalizationEntry PRINTING_PRESS_TOOLTIP = new LocalizationEntry(
            () -> SFMItems.PRINTING_PRESS.get().getDescriptionId() + ".tooltip",
            () -> "Place with an air gap below a downward facing piston. Extend the piston to use."
    );

    public PrintingPressBlockItem() {

        super(SFMBlocks.PRINTING_PRESS.get(), new Properties());
    }

    @Override
    public void appendHoverText(
            ItemStack pStack,
            TooltipContext pContext,
            List<Component> pTooltipComponents,
            TooltipFlag pTooltipFlag
    ) {
        super.appendHoverText(pStack, pContext, pTooltipComponents, pTooltipFlag);
        pTooltipComponents.add(PRINTING_PRESS_TOOLTIP.getComponent().withStyle(ChatFormatting.GRAY));
    }

}
