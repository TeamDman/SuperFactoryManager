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

public class ToughCableBlock extends CableBlock {
    @SFMLocalizationDatagen
    public static final LocalizationEntry TOUGH_CABLE_ITEM_TOOLTIP = new LocalizationEntry(
            () -> SFMBlocks.TOUGH_CABLE.get().getDescriptionId() + ".tooltip",
            () -> "Resists explosions. Can be facaded as tougher blocks."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry TOUGH_CABLE_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.TOUGH_CABLE.get().getDescriptionId(),
            () -> "Tough Inventory Cable"
    );

    public ToughCableBlock(Properties properties) {

        super(properties);
    }

    @Override
    public IFacadableBlock getNonFacadeBlock() {

        return SFMBlocks.TOUGH_CABLE.get();
    }

    @Override
    public IFacadableBlock getFacadeBlock() {

        return SFMBlocks.TOUGH_CABLE_FACADE.get();
    }

}
