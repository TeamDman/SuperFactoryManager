package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

import static ca.teamdman.sfm.common.block.TunnelledCableBlock.TUNNELLED_CABLE_ITEM_TOOLTIP;

public class TunnelledFancyCableBlock extends FancyCableBlock implements EntityBlock, TooltipProvider {
    @SFMLocalizationDatagen
    public static final LocalizationEntry TUNNELLED_FANCY_CABLE_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.TUNNELLED_FANCY_CABLE.get().getDescriptionId(),
            () -> "Tunnelled Fancy Inventory Cable"
    );

    public TunnelledFancyCableBlock(Properties properties) {

        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(
            BlockPos blockPos,
            BlockState blockState
    ) {

        return SFMBlockEntities.TUNNELLED_FANCY_CABLE.get().create(blockPos, blockState);
    }

    @Override
    public IFacadableBlock getNonFacadeBlock() {

        return SFMBlocks.TUNNELLED_FANCY_CABLE.get();
    }

    @Override
    public IFacadableBlock getFacadeBlock() {

        return SFMBlocks.TUNNELLED_FANCY_CABLE_FACADE.get();
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> consumer, TooltipFlag flag, DataComponentGetter components) {
        consumer.accept(
                TUNNELLED_CABLE_ITEM_TOOLTIP.getComponent().withStyle(ChatFormatting.GRAY)
        );
    }
}
