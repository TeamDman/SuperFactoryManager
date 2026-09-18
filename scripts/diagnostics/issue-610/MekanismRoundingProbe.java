package mekanism.common.integration.energy.forgeenergy;

import mekanism.api.Action;
import mekanism.api.energy.IEnergyConversion;
import mekanism.api.energy.IStrictEnergyHandler;

/** Executes the pinned 1.21.1 adapter bytecode, not a reimplementation of it. */
public class MekanismRoundingProbe {
    static class Store implements IStrictEnergyHandler {
        long energy;
        final long capacity;
        Store(long energy) { this(energy, 2500); }
        Store(long energy, long capacity) { this.energy = energy; this.capacity = capacity; }
        public int getEnergyContainerCount() { return 1; }
        public long getEnergy(int container) { return energy; }
        public void setEnergy(int container, long amount) { energy = amount; }
        public long getMaxEnergy(int container) { return capacity; }
        public long getNeededEnergy(int container) { return capacity - energy; }
        public long insertEnergy(int container, long amount, Action action) {
            long accepted = Math.min(amount, capacity - energy);
            if (action.execute()) energy += accepted;
            return amount - accepted;
        }
        public long extractEnergy(int container, long amount, Action action) {
            long extracted = Math.min(amount, energy);
            if (action.execute()) energy -= extracted;
            return extracted;
        }
    }
    public static void main(String[] args) {
        IEnergyConversion converter = new IEnergyConversion() {
            public boolean isEnabled() { return true; }
            public double getConversion() { return 2.5; }
            public boolean isOneToOne() { return false; }
        };
        for (int request : new int[] {1, 2, 3, 4, 25, 100}) {
            var store = new Store(2500);
            var adapter = new ForgeEnergyIntegration(store, converter);
            int simulated = adapter.extractEnergy(request, true);
            long afterSimulation = store.energy;
            int actual = adapter.extractEnergy(request, false);
            System.out.printf("request=%dFE simulated=%dFE actual=%dFE source-before=2500J after-simulation=%dJ after=%dJ%n",
                    request, simulated, actual, afterSimulation, store.energy);
            if (afterSimulation != 2500) throw new AssertionError("Simulation mutated energy");
            int expected = request - request % 2;
            if (simulated != expected || actual != expected || store.energy != 2500 - expected * 2.5) {
                throw new AssertionError("Unexpected pinned-adapter result for request=" + request);
            }
        }
        for (int request : new int[] {1, 2, 3, 4, 99, 100}) {
            checkInsertion(converter, 0, request, request - request % 2);
        }
        for (int gap : new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9}) {
            checkInsertion(converter, 2500 - gap, 100, (gap / 5) * 2);
        }
        // SFM simulates the stored amount before applying limits or discovering the
        // source's actual extraction limit. A truthful receiver can reject that
        // smaller insertion. Keep this distinct from a simulation/execute mismatch.
        for (int actualOffer : new int[] {1, 2, 99, 100}) {
            var store = new Store(0);
            var adapter = new ForgeEnergyIntegration(store, converter);
            int broadSimulation = adapter.receiveEnergy(100, true);
            int exactSimulation = adapter.receiveEnergy(actualOffer, true);
            int actual = adapter.receiveEnergy(actualOffer, false);
            System.out.printf("changed-offer broad=100FE simulated=%dFE final=%dFE exact-simulated=%dFE actual=%dFE remainder=%dFE%n",
                    broadSimulation, actualOffer, exactSimulation, actual, actualOffer - actual);
            if (broadSimulation != 100 || exactSimulation != actual || actual != actualOffer - actualOffer % 2) {
                throw new AssertionError("Unexpected changed-offer result for " + actualOffer);
            }
        }
        // Exact quantities from issue #608. The source was simulated at 500000 FE,
        // but a subsequent limit or partial extraction reduced the real offer to 101.
        var reportStore = new Store(97250, 1250000); // 38900 / 500000 FE
        var reportAdapter = new ForgeEnergyIntegration(reportStore, converter);
        int broad = reportAdapter.receiveEnergy(500000, true);
        int exact = reportAdapter.receiveEnergy(101, true);
        long reportAfterSimulation = reportStore.energy;
        int received = reportAdapter.receiveEnergy(101, false);
        System.out.printf("issue-608 offered=500000FE simulated=%dFE simulated-remainder=%dFE final=101FE exact-simulated=%dFE actual=%dFE lost-if-discarded=%dFE%n",
                broad, 500000 - broad, exact, received, 101 - received);
        if (broad != 461100 || exact != 100 || received != 100 || reportAfterSimulation != 97250 || reportStore.energy != 97500) {
            throw new AssertionError("Issue #608 quantities not reproduced");
        }
    }

    private static void checkInsertion(IEnergyConversion converter, long initial, int request, int expected) {
        var store = new Store(initial);
        var adapter = new ForgeEnergyIntegration(store, converter);
        int simulated = adapter.receiveEnergy(request, true);
        long afterSimulation = store.energy;
        int actual = adapter.receiveEnergy(request, false);
        System.out.printf("insert request=%dFE gap=%dJ simulated=%dFE actual=%dFE before=%dJ after=%dJ%n",
                request, 2500 - initial, simulated, actual, initial, store.energy);
        if (afterSimulation != initial || simulated != expected || actual != expected || store.energy != initial + expected * 2.5) {
            throw new AssertionError("Unexpected pinned insertion for request=" + request + ", initial=" + initial);
        }
    }
}
