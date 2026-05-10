package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class TunnelledCableBlock extends CableBlock implements EntityBlock {
    @SFMLocalizationDatagen
    public static final LocalizationEntry TUNNELLED_CABLE_ITEM_TOOLTIP = new LocalizationEntry(
            () -> SFMBlocks.TUNNELLED_CABLE.get().getDescriptionId() + ".tooltip",
            () -> "Passes capabilities through to the opposite side."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry TUNNELLED_CABLE_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.TUNNELLED_CABLE.get().getDescriptionId(),
            () -> "Tunnelled Inventory Cable"
    );

    public TunnelledCableBlock(Properties properties) {

        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(
            BlockPos blockPos,
            BlockState blockState
    ) {

        return SFMBlockEntities.TUNNELLED_CABLE.get().create(blockPos, blockState);
    }

    @Override
    public IFacadableBlock getNonFacadeBlock() {

        return SFMBlocks.TUNNELLED_CABLE.get();
    }

    @Override
    public IFacadableBlock getFacadeBlock() {

        return SFMBlocks.TUNNELLED_CABLE_FACADE.get();
    }

}
