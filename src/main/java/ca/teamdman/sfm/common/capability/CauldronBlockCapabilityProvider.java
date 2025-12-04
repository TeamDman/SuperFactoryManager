package ca.teamdman.sfm.common.capability;

import net.minecraft.block.BlockCauldron;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.FluidTankProperties;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CauldronBlockCapabilityProvider implements SFMBlockCapabilityProvider<IFluidHandler> {
    @Override
    public boolean matchesCapabilityKind(SFMBlockCapabilityKind<?> capabilityKind) {
        return SFMWellKnownCapabilities.FLUID_HANDLER.equals(capabilityKind);
    }

    @Override
    public SFMBlockCapabilityResult<IFluidHandler> getCapability(
            SFMBlockCapabilityKind<IFluidHandler> capabilityKind,
            World level,
            BlockPos pos,
            IBlockState state,
            @Nullable TileEntity blockEntity,
            @Nullable EnumFacing direction
    ) {
        if (state.getBlock() == Blocks.CAULDRON) {
            return SFMBlockCapabilityResult.of(new CauldronFluidHandler(level, pos));
        } else {
            return SFMBlockCapabilityResult.empty();
        }
    }

    private static class CauldronFluidHandler implements IFluidHandler {
        private final World world;
        private final BlockPos pos;

        public CauldronFluidHandler(World world, BlockPos pos) {
            this.world = world;
            this.pos = pos;
        }


        public @NotNull FluidStack getFluidInTank() {
            IBlockState state = world.getBlockState(pos);
            if (state.getBlock() == Blocks.CAULDRON) {
                int level = state.getValue(BlockCauldron.LEVEL);
                if (level > 0) {
                    return new FluidStack(FluidRegistry.WATER, level * (Fluid.BUCKET_VOLUME / 3));
                }
            }
            return null;
        }

        @Override
        public IFluidTankProperties[] getTankProperties() {
            return new IFluidTankProperties[] {
                    new FluidTankProperties(getFluidInTank(), Fluid.BUCKET_VOLUME)
            };
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            if (resource == null || resource.getFluid() != FluidRegistry.WATER) {
                return 0;
            }

            IBlockState state = world.getBlockState(pos);
            if (state.getBlock() != Blocks.CAULDRON) {
                return 0;
            }

            int level = state.getValue(BlockCauldron.LEVEL);
            if (level >= 3) {
                return 0;
            }

            int amountToFill = resource.amount;
            int levelsToFill = amountToFill / (Fluid.BUCKET_VOLUME / 4);
            int filledAmount = 0;

            if (levelsToFill > 0) {
                int newLevel = Math.min(3, level + levelsToFill);
                filledAmount = (newLevel - level) * (Fluid.BUCKET_VOLUME / 4);
                if (doFill) {
                    world.setBlockState(pos, state.withProperty(BlockCauldron.LEVEL, newLevel), 3);
                }
            }

            return filledAmount;
        }

        @Override
        @Nullable
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            if (resource == null || resource.getFluid() != FluidRegistry.WATER) {
                return null;
            }
            return drain(resource.amount, doDrain);
        }

        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            IBlockState state = world.getBlockState(pos);
            if (state.getBlock() != Blocks.CAULDRON) {
                return null;
            }

            int level = state.getValue(BlockCauldron.LEVEL);
            if (level <= 0) {
                return null;
            }

            int amountPerLevel = Fluid.BUCKET_VOLUME / 4;
            int availableAmount = level * amountPerLevel;
            int amountToDrain = Math.min(maxDrain, availableAmount);

            int levelsToDrain = (int)Math.floor((double)amountToDrain / amountPerLevel);
            int drainedAmount = levelsToDrain * amountPerLevel;

            if (doDrain && levelsToDrain > 0) {
                world.setBlockState(pos, state.withProperty(BlockCauldron.LEVEL, level - levelsToDrain), 3);
            }

            return new FluidStack(FluidRegistry.WATER, drainedAmount);
        }
    }
}
