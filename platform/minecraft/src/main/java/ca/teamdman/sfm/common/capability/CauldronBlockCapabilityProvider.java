package ca.teamdman.sfm.common.capability;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.IBlockCapabilityProvider;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;


public class CauldronBlockCapabilityProvider implements SFMBlockCapabilityProvider<ResourceHandler<FluidResource>>, IBlockCapabilityProvider<ResourceHandler<FluidResource>, Direction> {
    @Override
    public boolean matchesCapabilityKind(SFMBlockCapabilityKind<?> capabilityKind) {
        return SFMWellKnownCapabilities.FLUID_HANDLER.equals(capabilityKind);
    }

    @MCVersionDependentBehaviour
    @Override
    public SFMBlockCapabilityResult<ResourceHandler<FluidResource>> getCapability(
            SFMBlockCapabilityKind<ResourceHandler<FluidResource>> capabilityKind,
            LevelAccessor level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity blockEntity,
            @Nullable Direction direction
    ) {
        if (state.getBlock() == Blocks.CAULDRON
            || state.getBlock() == Blocks.WATER_CAULDRON
            || state.getBlock() == Blocks.LAVA_CAULDRON) {
            return SFMBlockCapabilityResult.of(new CauldronFluidHandler(level, pos));
        } else {
            return SFMBlockCapabilityResult.empty();
        }
    }

    @MCVersionDependentBehaviour
    @Override
    public @Nullable ResourceHandler<FluidResource> getCapability(
            Level level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity blockEntity,
            @Nullable Direction context
    ) {
        if (state.getBlock() == Blocks.CAULDRON
            || state.getBlock() == Blocks.WATER_CAULDRON
            || state.getBlock() == Blocks.LAVA_CAULDRON) {
            return new CauldronFluidHandler(level, pos);
        } else {
            return null;
        }
    }

    private record CauldronFluidHandler(
            LevelAccessor level,
            BlockPos pos
    ) implements ResourceHandler<FluidResource> {

        @Override
        public int size() {
            return 1;
        }

        @Override
        public FluidResource getResource(int index) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() == Blocks.WATER_CAULDRON) {
                return FluidResource.of(Fluids.WATER);
            } else if (state.getBlock() == Blocks.LAVA_CAULDRON) {
                return FluidResource.of(Fluids.LAVA);
            }
            return FluidResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() == Blocks.WATER_CAULDRON) {
                return state.getValue(LayeredCauldronBlock.LEVEL) * 250L;
            } else if (state.getBlock() == Blocks.LAVA_CAULDRON) {
                return 1000L;
            }
            return 0L;
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            return 1000L;
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return resource.equals(FluidResource.of(Fluids.WATER)) || resource.equals(FluidResource.of(Fluids.LAVA));
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            BlockState state = level.getBlockState(pos);

            if (resource.equals(FluidResource.of(Fluids.WATER))) {
                if (state.getBlock() == Blocks.CAULDRON) {
                    // Empty cauldron — fill from scratch
                    int layers = (int) Math.min(3, amount / 250);
                    if (layers > 0) {
                        transaction.addCloseCallback((tx, result) -> {
                            if (result.wasCommitted()) {
                                level.setBlock(
                                        pos,
                                        Blocks.WATER_CAULDRON.defaultBlockState()
                                                .setValue(LayeredCauldronBlock.LEVEL, layers),
                                        Block.UPDATE_ALL
                                );
                            }
                        });
                        return layers * 250;
                    }
                } else if (state.getBlock() == Blocks.WATER_CAULDRON) {
                    int waterLevel = state.getValue(LayeredCauldronBlock.LEVEL);
                    if (waterLevel >= 3) return 0;
                    int increase = (int) Math.min(3 - waterLevel, amount / 250);
                    if (increase > 0) {
                        transaction.addCloseCallback((tx, result) -> {
                            if (result.wasCommitted()) {
                                level.setBlock(
                                        pos,
                                        state.setValue(LayeredCauldronBlock.LEVEL, waterLevel + increase),
                                        Block.UPDATE_ALL
                                );
                            }
                        });
                        return increase * 250;
                    }
                }
            } else if (resource.equals(FluidResource.of(Fluids.LAVA))) {
                if (state.getBlock() == Blocks.CAULDRON && amount >= 1000) {
                    transaction.addCloseCallback((tx, result) -> {
                        if (result.wasCommitted()) {
                            level.setBlock(
                                    pos,
                                    Blocks.LAVA_CAULDRON.defaultBlockState(),
                                    Block.UPDATE_ALL
                            );
                        }
                    });
                    return 1000L;
                }
            }

            return 0L;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return 0;
        }
    }
}
