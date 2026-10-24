package ca.teamdman.sfm.common.capability;

import ca.teamdman.sfm.common.blockentity.TestBarrelBlockEntity;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
{% when '26.1.2' %}
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
{% else %}
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
{% endcase %}
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Test-only SFM-local provider. It does not claim Mekanism or native capability integration. */
@MCVersionDependentBehaviour
{% case minecraft_version %}
{% when '26.1.2' %}
public final class Issue610FixtureEnergyProvider implements SFMBlockCapabilityProvider<EnergyHandler> {
{% else %}
public final class Issue610FixtureEnergyProvider implements SFMBlockCapabilityProvider<IEnergyStorage> {
{% endcase %}
    private static final Map<BlockEntity, Storage> BINDINGS = Collections.synchronizedMap(new WeakHashMap<>());

    public static Storage bind(TestBarrelBlockEntity owner, int capacity, int initial, int quantum) {
        if (capacity <= 0 || initial < 0 || initial > capacity || quantum <= 0) {
            throw new IllegalArgumentException("Invalid fixture energy configuration");
        }
        Storage next = new Storage(owner, capacity, initial, quantum);
        Storage previous = BINDINGS.put(owner, next);
        if (previous != null) previous.close();
        return next;
    }

    @Override public boolean matchesCapabilityKind(SFMBlockCapabilityKind<?> kind) {
        return kind == SFMWellKnownCapabilities.ENERGY;
    }

    @Override
{% case minecraft_version %}
{% when '26.1.2' %}
    public SFMBlockCapabilityResult<EnergyHandler> getCapability(SFMBlockCapabilityKind<EnergyHandler> kind,
{% else %}
    public SFMBlockCapabilityResult<IEnergyStorage> getCapability(SFMBlockCapabilityKind<IEnergyStorage> kind,
{% endcase %}
            LevelAccessor level, BlockPos pos, BlockState state, @Nullable BlockEntity entity,
            @Nullable Direction direction) {
        Storage storage = entity == null ? null : BINDINGS.get(entity);
        return matchesCapabilityKind(kind) && storage != null && storage.active()
                ? SFMBlockCapabilityResult.of(storage) : SFMBlockCapabilityResult.empty();
    }

{% case minecraft_version %}
{% when '26.1.2' %}
    public static final class Storage extends SimpleEnergyHandler implements AutoCloseable {
{% else %}
    public static final class Storage extends EnergyStorage implements AutoCloseable {
{% endcase %}
        private final WeakReference<BlockEntity> owner;
        private final int quantum;
        private boolean closed;

        private Storage(BlockEntity owner, int capacity, int initial, int quantum) {
            super(capacity, capacity, capacity, initial);
            this.owner = new WeakReference<>(owner);
            this.quantum = quantum;
        }

        private boolean active() {
            BlockEntity entity = owner.get();
            return !closed && entity != null && !entity.isRemoved() && entity.getLevel() != null;
        }

        public int stored() {
{% case minecraft_version %}
{% when '26.1.2' %}
            return getAmountAsInt();
{% else %}
            return getEnergyStored();
{% endcase %}
        }

        public int extractForFixture(int amount, boolean simulate) {
            return SFMResourceTypes.FORGE_ENERGY.get().extract(this, 0, amount, simulate);
        }

{% case minecraft_version %}
{% when '26.1.2' %}
        @Override public int extract(int amount, TransactionContext transaction) {
            return active() ? super.extract(amount - amount % quantum, transaction) : 0;
        }
        @Override public int insert(int amount, TransactionContext transaction) {
            return active() ? super.insert(amount, transaction) : 0;
        }
{% else %}
        @Override public int extractEnergy(int amount, boolean simulate) {
            return active() ? super.extractEnergy(amount - amount % quantum, simulate) : 0;
        }
        @Override public int receiveEnergy(int amount, boolean simulate) {
            return active() ? super.receiveEnergy(amount, simulate) : 0;
        }
{% endcase %}
        @Override public void close() {
            closed = true;
            BlockEntity entity = owner.get();
            if (entity != null) BINDINGS.remove(entity, this);
        }
    }
}
