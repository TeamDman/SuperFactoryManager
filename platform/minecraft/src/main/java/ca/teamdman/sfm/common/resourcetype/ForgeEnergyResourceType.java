package ca.teamdman.sfm.common.resourcetype;

import ca.teamdman.sfm.common.block.BufferBlock;
import ca.teamdman.sfm.common.blockentity.BufferBlockEntityContents;
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

public class ForgeEnergyResourceType extends IntegerResourceType<EnergyHandler> {
    public ForgeEnergyResourceType() {
        super(
                SFMWellKnownCapabilities.ENERGY,
                SFMResourceLocation.fromNamespaceAndPath("forge", "energy")
        );
    }

    @Override
    public Integer extract(
            EnergyHandler handler,
            int slot,
            long amount,
            TransactionContext tx
    ) {
        try (Transaction ctx = Transaction.open(tx)) {
            int finalAmount = amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
            int extracted = handler.extract(finalAmount, ctx);
            ctx.commit();

            return extracted;
        }
    }
    @Override
    public int getSlots(EnergyHandler handler) {
        return 1;
    }

    @Override
    public Integer insert(
            EnergyHandler handler,
            int slot,
            Integer amount,
            TransactionContext tx
    ) {
        try (var ctx = Transaction.open(tx)) {
            int inserted = handler.insert(amount, ctx);
            ctx.commit();

            return amount - inserted;
        }
    }

    @Override
    public boolean matchesCapabilityHandler(Object o) {
        return o instanceof EnergyHandler;
    }

    @Override
    public long getMaxStackSizeForSlot(
            EnergyHandler iEnergyStorage,
            int slot
    ) {
        int maxStackSize = iEnergyStorage.getCapacityAsInt();
        if (maxStackSize == Integer.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return maxStackSize;
    }

    @Override
    public SimpleEnergyHandler createHandlerForBufferBlock(BufferBlockEntityContents contents) {
        return new SimpleEnergyHandler(contents.tier.getIntScalarMaxStackSize()) {

/*
            @Override
            public boolean isValid() {
                boolean isValid = this.energy > 0 || contents.isEmpty();
                if (isValid) {
                    contents.lastUsedResource = BufferBlock.ContainedResource.Energy;
                }
                return isValid;
            }
*/
        };
    }

    @Override
    public Integer getStackInSlot(
            EnergyHandler iEnergyStorage,
            int slot
    ) {
        return iEnergyStorage.getAmountAsInt();
    }
}
