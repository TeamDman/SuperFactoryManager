package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

import java.util.function.Consumer;

import static ca.teamdman.sfm.common.block.ToughCableBlock.TOUGH_CABLE_BLOCK;

public class ToughFancyCableBlock extends FancyCableBlock implements TooltipProvider {
    @SFMLocalizationDatagen
    public static final LocalizationEntry TOUGH_FANCY_CABLE_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.TOUGH_FANCY_CABLE.get().getDescriptionId(),
            () -> "Tough Fancy Inventory Cable"
    );

    public ToughFancyCableBlock(Properties properties) {

        super(properties);
    }

    @Override
    public IFacadableBlock getNonFacadeBlock() {

        return SFMBlocks.TOUGH_FANCY_CABLE.get();
    }

    @Override
    public IFacadableBlock getFacadeBlock() {

        return SFMBlocks.TOUGH_FANCY_CABLE_FACADE.get();
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> consumer, TooltipFlag flag, DataComponentGetter components) {
        consumer.accept(
                TOUGH_CABLE_BLOCK.getComponent().withStyle(ChatFormatting.GRAY)
        );
    }
}
