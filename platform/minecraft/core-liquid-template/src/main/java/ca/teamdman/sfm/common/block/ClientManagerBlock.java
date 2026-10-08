package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.blockentity.ClientManagerBlockEntity;
{% if features.client_manager_gui %}
{% else %}
import ca.teamdman.sfm.common.item.DiskItem;
{% endif %}
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;
{% if features.client_manager_gui %}
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkHooks;
{% endif %}
import org.jetbrains.annotations.Nullable;

/** The client visual runtime has its own block and no server-side program ticker. */
public final class ClientManagerBlock extends BaseEntityBlock {
    @SFMLocalizationDatagen
    public static final LocalizationEntry NAME = new LocalizationEntry(
            () -> SFMBlocks.CLIENT_MANAGER.get().getDescriptionId(), () -> "Client Manager"
    );
{% if features.client_manager_gui %}
{% else %}
    @SFMLocalizationDatagen
    public static final LocalizationEntry PROGRAM_TOO_LARGE = new LocalizationEntry(
            "sfm.client_manager.program_too_large", "This program is too large for a Client Manager"
    );

{% endif %}
    public ClientManagerBlock() {
        super(BlockBehaviour.Properties.of(Material.METAL).strength(2.0F, 6.0F).sound(SoundType.METAL));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return SFMBlockEntities.CLIENT_MANAGER.get().create(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(
            BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit
    ) {
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) return InteractionResult.CONSUME;
        if (!(level.getBlockEntity(pos) instanceof ClientManagerBlockEntity manager)) return InteractionResult.CONSUME;
{% if features.client_manager_gui %}
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer, manager,
                    buf -> ca.teamdman.sfm.common.containermenu.ClientManagerContainerMenu.encode(manager, buf));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
{% else %}
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (held.getItem() instanceof DiskItem && manager.disk().isEmpty()) {
            ItemStack inserted = held.copy();
            inserted.setCount(1);
            try {
                manager.setDisk(inserted);
            } catch (IllegalArgumentException tooLarge) {
                player.displayClientMessage(PROGRAM_TOO_LARGE.getComponent(), true);
                return InteractionResult.CONSUME;
            }
            if (!player.getAbilities().instabuild) held.shrink(1);
        } else if (held.isEmpty() && !manager.disk().isEmpty()) {
            ItemStack removed = manager.removeDisk();
            if (!player.getInventory().add(removed)) {
                player.drop(removed, false);
            }
        }
        // The no-screen interaction deliberately cannot start a visual program.
        // Only client-local consent approval can enable its render scheduler.
        return InteractionResult.CONSUME;
{% endif %}
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock())) {
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ClientManagerBlockEntity manager) {
                ItemStack disk = manager.removeDisk();
                if (!disk.isEmpty()) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), disk);
                }
            }
            super.onRemove(state, level, pos, next, moved);
        }
    }
}
