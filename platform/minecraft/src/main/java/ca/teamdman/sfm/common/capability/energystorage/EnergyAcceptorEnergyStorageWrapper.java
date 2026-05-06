package ca.teamdman.sfm.common.capability.energystorage;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

public record EnergyAcceptorEnergyStorageWrapper(
        EnergyHandler inner
) implements EnergyHandler {
    @Override
    public long getAmountAsLong() {
        return inner().getAmountAsLong();
    }

    @Override
    public long getCapacityAsLong() {
        // #322: AE always reports zero, we want SFM to be able to insert energy
        return Long.MAX_VALUE;
    }

    @Override
    public int insert(int maxInsert, TransactionContext transactionContext) {
        return inner.insert(maxInsert, transactionContext);
    }

    @Override
    public int extract(int maxExtract, TransactionContext transactionContext) {
        return inner.extract(maxExtract, transactionContext);
    }
}
