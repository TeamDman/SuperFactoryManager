package ca.teamdman.sfm.common.resourcetype;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import ca.teamdman.sfm.common.block.BufferBlock;
{% when '26.1.2' %}
{% endcase %}
import ca.teamdman.sfm.common.blockentity.BufferBlockEntityContents;
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
{% when '26.1.2' %}
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
public class ForgeEnergyResourceType extends IntegerResourceType<IEnergyStorage> {
{% when '26.1.2' %}
public class ForgeEnergyResourceType extends IntegerResourceType<EnergyHandler> {
{% endcase %}
    public ForgeEnergyResourceType() {
        super(
                SFMWellKnownCapabilities.ENERGY,
                SFMResourceLocation.fromNamespaceAndPath("forge", "energy")
        );
    }

    @Override
    public Integer extract(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            IEnergyStorage iEnergyStorage,
{% when '26.1.2' %}
            EnergyHandler _handler,
{% endcase %}
            int slot,
            long amount,
            boolean simulate
    ) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
        IEnergyStorage handler = IEnergyStorage.of(_handler);
{% endcase %}
        int finalAmount = amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        return iEnergyStorage.extractEnergy(finalAmount, simulate);
{% when '26.1.2' %}
        return handler.extractEnergy(finalAmount, simulate);
{% endcase %}
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public boolean canExtract(IEnergyStorage capability, int slot) {
        return capability.canExtract();
{% when '26.1.2' %}
    public boolean canExtract(EnergyHandler _handler, int slot) {
        return IEnergyStorage.of(_handler).canExtract();
{% endcase %}
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public int getSlots(IEnergyStorage handler) {
{% when '26.1.2' %}
    public int getSlots(EnergyHandler _handler) {
{% endcase %}
        return 1;
    }

    @Override
    public Integer insert(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            IEnergyStorage iEnergyStorage,
{% when '26.1.2' %}
            EnergyHandler _handler,
{% endcase %}
            int slot,
            Integer stack,
            boolean simulate
    ) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        int accepted = iEnergyStorage.receiveEnergy(stack, simulate);
{% when '26.1.2' %}
        int accepted = IEnergyStorage.of(_handler).receiveEnergy(stack, simulate);
{% endcase %}
        return stack - accepted;
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public boolean canInsert(IEnergyStorage capability, int slot) {
        return capability.canReceive();
{% when '26.1.2' %}
    public boolean canInsert(EnergyHandler _handler, int slot) {
        return IEnergyStorage.of(_handler).canReceive();
{% endcase %}
    }

    @Override
    public boolean matchesCapabilityHandler(Object o) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        return o instanceof IEnergyStorage;
{% when '26.1.2' %}
        return o instanceof EnergyHandler;
{% endcase %}
    }

    @Override
    public long getMaxStackSizeForSlot(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            IEnergyStorage iEnergyStorage,
{% when '26.1.2' %}
            EnergyHandler _handler,
{% endcase %}
            int slot
    ) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        int maxStackSize = iEnergyStorage.getMaxEnergyStored();
{% when '26.1.2' %}
        int maxStackSize = IEnergyStorage.of(_handler).getMaxEnergyStored();
{% endcase %}
        if (maxStackSize == Integer.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return maxStackSize;
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public IEnergyStorage createHandlerForBufferBlock(BufferBlockEntityContents contents) {
        return new EnergyStorage(contents.tier.getIntScalarMaxStackSize()) {
            @Override
{% when '26.1.2' %}
    public EnergyHandler createHandlerForBufferBlock(BufferBlockEntityContents contents) {
        return new SimpleEnergyHandler(contents.tier.getIntScalarMaxStackSize()) {

/*            @Override
{% endcase %}
            public boolean canReceive() {
                boolean isValid = this.energy > 0 || contents.isEmpty();
                if (isValid) {
                    contents.lastUsedResource = BufferBlock.ContainedResource.Energy;
                }
                return isValid;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            }
{% when '26.1.2' %}
            }*/
{% endcase %}
        };
    }

    @Override
    public Integer getStackInSlot(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            IEnergyStorage iEnergyStorage,
{% when '26.1.2' %}
            EnergyHandler _handler,
{% endcase %}
            int slot
    ) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        return iEnergyStorage.getEnergyStored();
{% when '26.1.2' %}
        return IEnergyStorage.of(_handler).getEnergyStored();
{% endcase %}
    }
}
