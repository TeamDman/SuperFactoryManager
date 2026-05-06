package ca.teamdman.sfm.common.resourcetype;

import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.resource.Resource;

import java.util.stream.Stream;

public abstract class IntegerResourceType<CAP> extends ScalarResourceType<Integer, CAP> {
public IntegerResourceType(
            SFMBlockCapabilityKind<CAP> capability,
            Identifier registryKey
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
    public Stream<Identifier> getTagsForStack(Class<Integer> integer) {
        return Stream.empty();
    }

    @Override
    public Class<Integer> stackToItem(Integer integer) {
        return Integer.class;
    }
}
