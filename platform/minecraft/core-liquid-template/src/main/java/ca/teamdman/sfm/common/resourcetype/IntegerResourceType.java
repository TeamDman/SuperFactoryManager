package ca.teamdman.sfm.common.resourcetype;

import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.resources.ResourceLocation;
{% when '26.1.2' %}
import net.minecraft.resources.Identifier;
{% endcase %}

import java.util.stream.Stream;

public abstract class IntegerResourceType<CAP> extends ScalarResourceType<Integer, CAP> {
    public IntegerResourceType(
            SFMBlockCapabilityKind<CAP> capability,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            ResourceLocation registryKey
{% when '26.1.2' %}
            Identifier registryKey
{% endcase %}
    ) {
        super(capability, registryKey, Integer.class);
    }

    @Override
    public long getAmount(Integer stack) {
        return stack;
    }

    @Override
    public long getMaxStackSize(Integer integer) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isEmpty(Integer stack) {
        return stack == 0;
    }

    @Override
    public Integer getEmptyStack() {
        return 0;
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public Stream<ResourceLocation> getTagsForStack(Integer integer) {
{% when '26.1.2' %}
    public Stream<Identifier> getTagsForStack(Integer integer) {
{% endcase %}
        return Stream.empty();
    }

    @Override
    public Integer copy(Integer integer) {
        return integer;
    }

    @Override
    protected Integer setCount(
            Integer stack,
            long amount
    ) {
        return amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
    }

    @Override
    public Integer withCount(
            Integer integer,
            long count
    ) {
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }
}
