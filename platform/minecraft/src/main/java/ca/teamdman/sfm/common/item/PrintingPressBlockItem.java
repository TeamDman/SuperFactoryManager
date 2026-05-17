package ca.teamdman.sfm.common.item;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

import java.util.function.Consumer;

public class PrintingPressBlockItem extends BlockItem implements TooltipProvider {
    @SFMLocalizationDatagen
    public static final LocalizationEntry PRINTING_PRESS_TOOLTIP = new LocalizationEntry(
            () -> SFMItems.PRINTING_PRESS.get().getDescriptionId() + ".tooltip",
            () -> "Place with an air gap below a downward facing piston. Extend the piston to use."
    );

    public PrintingPressBlockItem(Properties properties) {

        super(SFMBlocks.PRINTING_PRESS.get(), properties);
    }

    @Override
    public void addToTooltip(TooltipContext context, Consumer<Component> consumer, TooltipFlag flag, DataComponentGetter components) {
        consumer.accept(
                PRINTING_PRESS_TOOLTIP.getComponent().withStyle(ChatFormatting.GRAY)
        );
    }
}
