package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.blockentity.TestBarrelTankBlockEntity;
import ca.teamdman.sfm.common.containermenu.TestBarrelTankContainerMenu;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.21', '1.21.1', '26.1.2' %}
{% when '1.20.3', '1.20.4' %}
import com.mojang.serialization.MapCodec;
{% endcase %}
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1', '26.1.2' %}
import com.mojang.serialization.MapCodec;
{% endcase %}
import net.minecraft.core.BlockPos;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.world.InteractionHand;
{% when '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.world.level.block.state.BlockBehaviour;
{% endcase %}
import net.minecraft.world.level.block.state.BlockState;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
import net.minecraft.world.level.material.Material;
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
import net.minecraft.world.phys.BlockHitResult;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import org.apache.commons.lang3.NotImplementedException;
{% endcase %}
import org.jetbrains.annotations.Nullable;

public class TestBarrelTankBlock extends BaseEntityBlock {
    @SFMLocalizationDatagen
    public static final LocalizationEntry TEST_BARREL_TANK_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.TEST_BARREL_TANK.get().getDescriptionId(),
            () -> "Test Barrel Tank"
    );

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public TestBarrelTankBlock() {
{% when '26.1.2' %}
    public TestBarrelTankBlock(BlockBehaviour.Properties properties) {
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
        super(Properties.of(Material.WOOD).strength(2.5F).sound(SoundType.WOOD));
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        super(Properties.of().sound(SoundType.WOOD).strength(2.5F).sound(SoundType.WOOD));
{% when '26.1.2' %}
        super(properties.sound(SoundType.WOOD).strength(2.5F).sound(SoundType.WOOD));
    }

    @Override
    protected MapCodec<WaterTankBlock> codec() {
        throw new NotImplementedException("This isn't used until 1.20.5 apparently");
{% endcase %}
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '26.1.2' %}
{% when '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    @Override
    protected MapCodec<WaterTankBlock> codec() {
        throw new NotImplementedException("This isn't used until 1.20.5 apparently");
    }

{% endcase %}
    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {

        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(
            BlockPos pPos,
            BlockState pState
    ) {

        return SFMBlockEntities.TEST_BARREL_TANK.get().create(pPos, pState);
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
    @SuppressWarnings("deprecation")
{% when '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
    public InteractionResult use(
{% when '1.21', '1.21.1', '26.1.2' %}
    protected InteractionResult useWithoutItem(
{% endcase %}
            BlockState pState,
            Level pLevel,
            BlockPos pPos,
            Player pPlayer,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
            InteractionHand pHand,
            BlockHitResult pHit
{% when '1.21', '1.21.1', '26.1.2' %}
            BlockHitResult pHitResult
{% endcase %}
    ) {

        if (pLevel.getBlockEntity(pPos) instanceof TestBarrelTankBlockEntity blockEntity) {
            pPlayer.openMenu(new SimpleMenuProvider(
                    (containerId, playerInventory, player) ->
                            new TestBarrelTankContainerMenu(
                                    containerId,
                                    playerInventory,
                                    blockEntity
                            ),
                    blockEntity.getDisplayName()
            ));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

}
