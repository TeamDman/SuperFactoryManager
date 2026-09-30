package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.blockentity.TestBarrelBlockEntity;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import net.minecraft.core.BlockPos;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.world.level.Level;
{% when '26.1.2' %}
{% endcase %}
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
import net.minecraft.world.level.material.Material;
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
import org.jetbrains.annotations.Nullable;

public class TestBarrelBlock extends BarrelBlock {
    @SFMLocalizationDatagen
    public static final LocalizationEntry TEST_BARREL_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.TEST_BARREL.get().getDescriptionId(),
            () -> "Test Barrel"
    );

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public TestBarrelBlock() {
{% when '26.1.2' %}
    public TestBarrelBlock(BlockBehaviour.Properties properties) {
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
        super(BlockBehaviour.Properties.of(Material.WOOD).strength(2.5F).sound(SoundType.WOOD));
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        super(BlockBehaviour.Properties.of().strength(2.5F).sound(SoundType.WOOD));
{% when '26.1.2' %}
        super(properties.strength(2.5F).sound(SoundType.WOOD));
{% endcase %}
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    @Override
{% when '26.1.2' %}

/*    @Override
{% endcase %}
    public void onRemove(
            BlockState pState,
            Level pLevel,
            BlockPos pPos,
            BlockState pNewState,
            boolean pIsMoving
    ) {

        if (!pState.is(pNewState.getBlock())) {
            // Remove the block entity manually to prevent the items from dropping on the ground from super logic.
            // Note that this doesn't drain the inventory like the normal drop behaviour does.
            // This means that if SFM has a use-after-free bug, the tests will be more likely to properly fail.
            // For example, if a source barrel is broken without SFM discarding the reference, it will continue to successfully pull items from it.
            pLevel.removeBlockEntity(pPos);

            super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving);
        }
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    }
{% when '26.1.2' %}
    }*/
{% endcase %}

    @Override
    public @Nullable BlockEntity newBlockEntity(
            BlockPos pPos,
            BlockState pState
    ) {

        return new TestBarrelBlockEntity(pPos, pState);
    }

}
