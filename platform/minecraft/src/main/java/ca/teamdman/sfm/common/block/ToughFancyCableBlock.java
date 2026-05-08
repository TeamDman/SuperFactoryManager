package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.List;
import java.util.function.Consumer;

public class ToughFancyCableBlock extends FancyCableBlock {
    @SFMLocalizationDatagen
    public static final LocalizationEntry TOUGH_FANCY_CABLE_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.TOUGH_FANCY_CABLE.get().getDescriptionId(),
            () -> "Tough Fancy Inventory Cable"
    );

    public ToughFancyCableBlock(Properties properties) {

        super(properties);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            TooltipDisplay tooltipDisplay,
            Consumer<Component> pTooltip,
            TooltipFlag detail
    ) {

        pTooltip.accept(ToughCableBlock.TOUGH_CABLE_ITEM_TOOLTIP
                             .getComponent()
                             .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public IFacadableBlock getNonFacadeBlock() {

        return SFMBlocks.TOUGH_FANCY_CABLE.get();
    }

    @Override
    public IFacadableBlock getFacadeBlock() {

        return SFMBlocks.TOUGH_FANCY_CABLE_FACADE.get();
    }

}
