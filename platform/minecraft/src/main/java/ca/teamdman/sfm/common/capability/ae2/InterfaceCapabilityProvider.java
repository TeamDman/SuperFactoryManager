package ca.teamdman.sfm.common.capability.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.IStorageMonitorableAccessor;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.capabilities.Capabilities;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityProvider;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityResult;
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.util.SFMItemUtils;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;


@SuppressWarnings("unused") // will reintroduce it later once I figure out the performance characteristics
public class InterfaceCapabilityProvider implements SFMBlockCapabilityProvider<Object> {
    @Override
    public boolean matchesCapabilityKind(SFMBlockCapabilityKind<?> capabilityKind) {
        return SFMWellKnownCapabilities.ITEM_HANDLER.equals(capabilityKind)
               || SFMWellKnownCapabilities.FLUID_HANDLER.equals(capabilityKind);
    }

    @Override
    public SFMBlockCapabilityResult<Object> getCapability(
            SFMBlockCapabilityKind<Object> capabilityKind,
            LevelAccessor level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity blockEntity,
            @Nullable Direction direction
    ) {
        var be = level.getBlockEntity(pos);
        if (!(be instanceof InterfaceBlockEntity in)) {
            return null;
        }

        if (!in.getConfig().isEmpty() || in.getMainNode() == null || in.getGridNode() == null || !in
                .getGridNode()
                .isActive()) {
            return null;
        }

        return SFMBlockCapabilityResult.of(
                be.getCapability(Capabilities.STORAGE_MONITORABLE_ACCESSOR)
                        .lazyMap(aeHandler -> {
                            // TODO: rework the InterfaceHandler to accept the aeHandler instance instead
                            return new InterfaceHandler(
                                    level,
                                    pos
                            );
                        }).cast()
        );
    }


    private static @Nullable InterfaceBlockEntity interfaceAt(
            LevelAccessor level,
            BlockPos pos
    ) {
        var be = level.getBlockEntity(pos);
        if (be instanceof InterfaceBlockEntity in) {
            return in;
        }
        return null;
    }

    @MethodsReturnNonnullByDefault
    record InterfaceHandler(
            LevelAccessor level,
            BlockPos pos
    ) implements IItemHandler, IFluidHandler {
        @Override
        public int getSlots() {
            Integer slots = this.withStorage(s -> {
                int i = 0;
                for (var stored : s.getAvailableStacks()) {
                    if (stored.getKey() instanceof AEItemKey) {
                        i++;
                    }
                }
                return i;
            });

            if (slots == null) {
                slots = this.withBaseItemHandler(IItemHandler::getSlots);
            }

            return slots == null ? 0 : slots;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            var stack = this.withStorage(s -> {
                int i = 0;
                for (var stored : s.getAvailableStacks()) {
                    if (stored.getKey() instanceof AEItemKey key) {
                        if (slot == i++) {
                            return key.toStack((int) Math.min(Integer.MAX_VALUE, stored.getLongValue()));
                        }
                    }
                }
                return ItemStack.EMPTY;
            });

            if (stack == null) {
                stack = this.withBaseItemHandler(c -> c.getStackInSlot(slot));
            }

            return stack == null ? ItemStack.EMPTY : stack;
        }

        @Override
        public ItemStack insertItem(
                int slot,
                @NotNull ItemStack stack,
                boolean simulate
        ) {
            if (stack.isEmpty()) {
                return stack;
            }

            Integer inserted = this.withStorage(s -> {
                var key = AEItemKey.of(stack);
                if (key == null) {
                    return 0;
                }

                var energy = this.getEnergy();
                if (energy == null) {
                    return 0;
                }

                return (int) StorageHelper.poweredInsert(
                        energy,
                        s,
                        key,
                        stack.getCount(),
                        IActionSource.empty(),
                        simulate ? Actionable.SIMULATE : Actionable.MODULATE
                );
            });

            if (inserted == null) {
                var stack2 = this.withBaseItemHandler(c -> c.insertItem(slot, stack, simulate));
                return stack2 == null ? ItemStack.EMPTY : stack2;
            }

            if (!simulate) {
                stack.shrink(inserted);
                return stack;
            }

            var rtn = stack.copy();
            rtn.shrink(inserted);
            return rtn;
        }

        @Override
        public ItemStack extractItem(
                int slot,
                int amount,
                boolean simulate
        ) {
            if (amount <= 0) {
                return ItemStack.EMPTY;
            }

            var stack = this.withStorage(s -> {
                int i = 0;
                for (var stored : s.getAvailableStacks()) {
                    if (stored.getKey() instanceof AEItemKey key) {
                        if (slot == i++) {
                            var energy = this.getEnergy();
                            if (energy == null) {
                                return ItemStack.EMPTY;
                            }

                            int extracted = (int) StorageHelper.poweredExtraction(
                                    energy,
                                    s,
                                    key,
                                    amount,
                                    IActionSource.empty(),
                                    simulate ? Actionable.SIMULATE : Actionable.MODULATE
                            );

                            return key.toStack(extracted);
                        }
                    }
                }
                return ItemStack.EMPTY;
            });

            if (stack == null) {
                stack = this.withBaseItemHandler(c -> c.extractItem(slot, amount, simulate));
            }

            return stack == null ? ItemStack.EMPTY : stack;
        }

        @Override
        public int getSlotLimit(int slot) {
            return this.getStackInSlot(slot).getMaxStackSize();
        }

        @Override
        public boolean isItemValid(
                int slot,
                @NotNull ItemStack stack
        ) {
            return SFMItemUtils.isSameItemSameTags(this.getStackInSlot(slot), stack);
        }

        @Override
        public int getTanks() {
            var slots = this.withStorage(s -> {
                int i = 0;
                for (var stored : s.getAvailableStacks()) {
                    if (stored.getKey() instanceof AEFluidKey) {
                        i++;
                    }
                }
                return i;
            });

            if (slots == null) {
                slots = this.withBaseFluidHandler(IFluidHandler::getTanks);
            }

            return slots == null ? 0 : slots;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            var stack = this.withStorage(s -> {
                int i = 0;
                for (var stored : s.getAvailableStacks()) {
                    if (stored.getKey() instanceof AEFluidKey key) {
                        if (tank == i++) {
                            return key.toStack((int) Math.min(Integer.MAX_VALUE, stored.getLongValue()));
                        }
                    }
                }
                return FluidStack.EMPTY;
            });

            if (stack == null) {
                stack = this.withBaseFluidHandler(c -> c.getFluidInTank(tank));
            }

            return stack == null ? FluidStack.EMPTY : stack;
        }

        @Override
        public int getTankCapacity(int tank) {
            if (this.getCapability().isPresent()) {
                return Integer.MAX_VALUE;
            }

            var capacity = this.withBaseFluidHandler(c -> c.getTankCapacity(tank));
            return capacity == null ? 0 : capacity;
        }

        @Override
        public boolean isFluidValid(
                int tank,
                @NotNull FluidStack stack
        ) {
            if (this.getCapability().isPresent()) {
                return this.getFluidInTank(tank).isFluidEqual(stack);
            }

            Boolean valid = this.withBaseFluidHandler(c -> c.isFluidValid(tank, stack));
            return valid != null && valid;
        }

        @Override
        public int fill(
                FluidStack resource,
                FluidAction action
        ) {
            Integer inserted = this.withStorage(s -> {
                var key = AEFluidKey.of(resource);
                if (key == null) {
                    return 0;
                }

                var energy = this.getEnergy();
                if (energy == null) {
                    return 0;
                }

                int ins = (int) StorageHelper.poweredInsert(
                        energy,
                        s,
                        key,
                        resource.getAmount(),
                        IActionSource.empty(),
                        fluidActionToActionable(action)
                );

                if (!action.simulate()) {
                    resource.shrink(ins);
                }

                return ins;
            });

            if (inserted == null) {
                inserted = this.withBaseFluidHandler(c -> c.fill(resource, action));
            }

            return inserted == null ? 0 : inserted;
        }

        @Override
        public FluidStack drain(
                FluidStack resource,
                FluidAction action
        ) {
            var stack = this.withStorage(s -> {
                var key = AEFluidKey.of(resource);
                if (key == null) {
                    return FluidStack.EMPTY;
                }

                var energy = this.getEnergy();
                if (energy == null) {
                    return FluidStack.EMPTY;
                }

                int extracted = (int) StorageHelper.poweredExtraction(
                        energy,
                        s,
                        key,
                        resource.getAmount(),
                        IActionSource.empty(),
                        fluidActionToActionable(action)
                );

                return key.toStack(extracted);
            });

            if (stack == null) {
                stack = this.withBaseFluidHandler(c -> c.drain(resource, action));
            }

            return stack == null ? FluidStack.EMPTY : stack;
        }

        @Override
        public FluidStack drain(
                int maxDrain,
                FluidAction action
        ) {
            var stack = this.withStorage(s -> {
                for (var stored : s.getAvailableStacks()) {
                    if (stored.getKey() instanceof AEFluidKey key) {
                        var energy = this.getEnergy();
                        if (energy == null) {
                            return FluidStack.EMPTY;
                        }

                        int extracted = (int) StorageHelper.poweredExtraction(
                                energy,
                                s,
                                key,
                                maxDrain,
                                IActionSource.empty(),
                                fluidActionToActionable(action)
                        );

                        return key.toStack(extracted);
                    }
                }
                return FluidStack.EMPTY;
            });

            if (stack == null) {
                stack = this.withBaseFluidHandler(c -> c.drain(maxDrain, action));
            }

            return stack == null ? FluidStack.EMPTY : stack;
        }

        private static Actionable fluidActionToActionable(FluidAction fluidAction) {
            return switch (fluidAction) {
                case EXECUTE -> Actionable.MODULATE;
                case SIMULATE -> Actionable.SIMULATE;
            };
        }

        @Nullable InterfaceBlockEntity getInterface() {
            return interfaceAt(this.level, this.pos);
        }

        @Nullable IEnergyService getEnergy() {
            var in = this.getInterface();
            if (in == null) {
                return null;
            }

            var grid = in.getMainNode().getGrid();
            if (grid == null) {
                return null;
            }

            return grid.getEnergyService();
        }

        LazyOptional<IStorageMonitorableAccessor> getCapability() {
            var in = this.getInterface();
            if (in == null) {
                return LazyOptional.empty();
            }

            if (!in.getConfig().isEmpty() || in.getMainNode() == null || in.getGridNode() == null || !in
                    .getGridNode()
                    .isActive()) {
                return LazyOptional.empty();
            }

            return in.getCapability(Capabilities.STORAGE_MONITORABLE_ACCESSOR);
        }

        <T> @Nullable T withStorage(Function<MEStorage, T> callback) {
            var cap = this.getCapability();
            if (cap.isPresent()) {
                //noinspection DataFlowIssue
                return callback.apply(cap.map(c -> c.getInventory(IActionSource.empty())).orElse(null));
            }
            return null;
        }

        <T> @Nullable T withBaseItemHandler(Function<IItemHandler, T> callback) {
            var in = this.getInterface();
            if (in == null) {
                return null;
            }

            var maybeCap = in.getCapability(SFMWellKnownCapabilities.ITEM_HANDLER.capabilityKind());
            if (maybeCap.isPresent()) {
                //noinspection DataFlowIssue
                return callback.apply(maybeCap.orElse(null));
            }
            return null;
        }

        <T> @Nullable T withBaseFluidHandler(Function<IFluidHandler, T> callback) {
            var in = this.getInterface();
            if (in == null) {
                return null;
            }

            var maybeCap = in.getCapability(SFMWellKnownCapabilities.FLUID_HANDLER.capabilityKind());
            if (maybeCap.isPresent()) {
                //noinspection DataFlowIssue
                return callback.apply(maybeCap.orElse(null));
            }
            return null;
        }
    }
}
