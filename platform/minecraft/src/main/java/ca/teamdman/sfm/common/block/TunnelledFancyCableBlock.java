package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public class TunnelledFancyCableBlock extends FancyCableBlock implements EntityBlock {
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

}
