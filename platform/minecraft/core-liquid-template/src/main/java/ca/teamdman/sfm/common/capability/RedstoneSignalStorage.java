package ca.teamdman.sfm.common.capability;

{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.util.Mth;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.ValueIOSerializable;
{% else %}
{% case minecraft_version %}
{% when "1.21", "1.21.1" %}
import net.minecraft.core.HolderLookup;
{% endcase %}
import net.minecraft.nbt.IntTag;
import net.minecraft.util.Mth;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.common.util.INBTSerializable;
{% else %}
import net.neoforged.neoforge.common.util.INBTSerializable;
{% endcase %}
{% case minecraft_version %}
{% when "1.21", "1.21.1" %}
import org.jetbrains.annotations.UnknownNullability;
{% endcase %}
{% endcase %}

/// A container for storing "redstone units", which CAN exceed 15.
{% case minecraft_version %}
{% when "26.1.2" %}
public class RedstoneSignalStorage implements IRedstoneSignalStorage, ValueIOSerializable {
{% else %}
public class RedstoneSignalStorage implements IRedstoneSignalStorage, INBTSerializable<IntTag> {
{% endcase %}
{% if features.redstone_buffer_storage %}
    private int value;
{% else %}
    public int value = 0;
{% endif %}
    private final int maxValue;

    public RedstoneSignalStorage(int signal, int maxValue) {
        this.maxValue = Mth.clamp(maxValue, 0, Integer.MAX_VALUE);
        this.value = Mth.clamp(signal, 0, this.maxValue);
    }

    @Override
    public int insert(
            int amount,
            boolean simulate
    ) {
        if (!this.canReceive()) {
            return 0; // accept nothing
        }
        int accept = Mth.clamp(amount, 0, this.maxValue - this.value);
{% if features.redstone_buffer_storage %}
        if (!simulate && accept > 0) {
{% else %}
        if (!simulate) {
{% endif %}
            this.value += accept;
{% if features.redstone_buffer_storage %}
            onContentsChanged();
{% endif %}
        }
        return accept;
    }

    @Override
    public int extract(
            int amount,
            boolean simulate
    ) {
        if (!this.canExtract()) {
            return 0; // extract nothing
        }
        int extract = Mth.clamp(amount, 0, this.value);
{% if features.redstone_buffer_storage %}
        if (!simulate && extract > 0) {
{% else %}
        if (!simulate) {
{% endif %}
            this.value -= extract;
{% if features.redstone_buffer_storage %}
            onContentsChanged();
{% endif %}
        }
        return extract;
    }

    @Override
    public int getStoredAmount() {
        return this.value;
    }

    @Override
    public int getMaxStoredAmount() {
        return this.maxValue;
    }

    @Override
    public boolean canExtract() {
        return true;
    }

    @Override
    public boolean canReceive() {
        return true;
    }

{% if features.redstone_buffer_storage %}
    protected void onContentsChanged() {
    }

{% endif %}
{% case minecraft_version %}
{% when "26.1.2" %}
    @Override
    public void serialize(ValueOutput output) {
        output.putInt("value", this.value);
    }

    @Override
    public void deserialize(ValueInput input) {
        input.getInt("value");
    }
{% when "1.21", "1.21.1" %}
    @Override
    public @UnknownNullability IntTag serializeNBT(HolderLookup.Provider provider) {
        return IntTag.valueOf(this.value);
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider,
            IntTag nbt
    ) {
        this.value = nbt.getAsInt();
    }
{% else %}
    @Override
    public void deserializeNBT(
            IntTag nbt
    ) {
{% if features.redstone_buffer_storage %}
        this.value = Mth.clamp(nbt.getAsInt(), 0, this.maxValue);
{% else %}
        this.value = nbt.getAsInt();
{% endif %}
    }

    @Override
    public IntTag serializeNBT() {
        return IntTag.valueOf(this.value);
    }
{% endcase %}
}
