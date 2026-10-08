package ca.teamdman.sfm.common.resourcetype;

import ca.teamdman.sfm.common.block.BufferBlock;
import ca.teamdman.sfm.common.blockentity.BufferBlockEntityContents;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.compat.SFMMekanismCompat;
import ca.teamdman.sfm.common.registry.SFMRegistryWrapper;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalTankBuilder;
import mekanism.api.chemical.slurry.ISlurryHandler;
import mekanism.api.chemical.slurry.Slurry;
import mekanism.api.chemical.slurry.SlurryStack;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
{% when '1.20.4', '1.21', '1.21.1' %}
import mekanism.common.capabilities.Capabilities;
{% endcase %}
import mekanism.common.lib.transmitter.TransmissionType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
{% when '1.20.4', '1.21', '1.21.1' %}
{% endcase %}
import java.util.stream.Stream;

public class SlurryResourceType extends RegistryBackedResourceType<SlurryStack, Slurry, ISlurryHandler> {
    public static final SFMBlockCapabilityKind<ISlurryHandler> CAP = new SFMBlockCapabilityKind<>(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
            CapabilityManager.get(new CapabilityToken<>() {
            })
{% when '1.20.4', '1.21', '1.21.1' %}
            Capabilities.SLURRY.block()
{% endcase %}
    );

    public SlurryResourceType() {
        super(CAP);
    }

    @Override
    public ISlurryHandler createHandlerForBufferBlock(BufferBlockEntityContents contents) {
        return (ChemicalTankBuilder.BasicSlurryTank) ChemicalTankBuilder.SLURRY.create(
                Long.MAX_VALUE,
                extracting -> {
                    ResourceType<?, ?, ?> resourceType = SFMMekanismCompat.getResourceType(TransmissionType.SLURRY);
                    boolean isValid = resourceType != null && contents.allowInsertion(resourceType);
                    if (isValid) {
                        contents.lastUsedResource = BufferBlock.ContainedResource.Chemical;
                    }
                    return isValid;
                },
                null
        );
    }

    @Override
    public long getAmount(SlurryStack stack) {
        return stack.getAmount();
    }

    @Override
    public SlurryStack getStackInSlot(
            ISlurryHandler handler,
            int slot
    ) {
        return handler.getChemicalInTank(slot);
    }

    @Override
    public SlurryStack extract(
            ISlurryHandler handler,
            int slot,
            long amount,
            boolean simulate
    ) {
        return handler.extractChemical(slot, amount, simulate ? Action.SIMULATE : Action.EXECUTE);
    }

    @Override
    public int getSlots(ISlurryHandler handler) {
        return handler.getTanks();
    }

    @Override
    public long getMaxStackSize(SlurryStack stack) {
        return Long.MAX_VALUE;
    }

    @Override
    public long getMaxStackSizeForSlot(
            ISlurryHandler handler,
            int slot
    ) {
        return handler.getTankCapacity(slot);
    }

    @Override
    public SlurryStack insert(
            ISlurryHandler handler,
            int slot,
            SlurryStack stack,
            boolean simulate
    ) {
        return handler.insertChemical(slot, stack, simulate ? Action.SIMULATE : Action.EXECUTE);
    }

    @Override
    public boolean isEmpty(SlurryStack stack) {
        return stack.isEmpty();
    }

    @Override
    public SlurryStack getEmptyStack() {
        return SlurryStack.EMPTY;
    }

    @Override
    public boolean matchesStackType(Object o) {
        return o instanceof SlurryStack;
    }

    @Override
    public boolean matchesCapabilityHandler(Object o) {
        return o instanceof ISlurryHandler;
    }

    @Override
    public Stream<ResourceLocation> getTagsForStack(SlurryStack slurryStack) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.4' %}
        return slurryStack.getType().getTags().map(TagKey::location);
{% when '1.21', '1.21.1' %}
        return slurryStack.getChemical().getTags().map(TagKey::location);
{% endcase %}
    }

    @Override
    public SFMRegistryWrapper<Slurry> getRegistry() {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
        return new SFMRegistryWrapper<>(MekanismAPI.slurryRegistry());
{% when '1.20.4', '1.21', '1.21.1' %}
        return new SFMRegistryWrapper<>(MekanismAPI.SLURRY_REGISTRY);
{% endcase %}
    }

    @Override
    public Slurry getItem(SlurryStack stack) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.4' %}
        return stack.getType();
{% when '1.21', '1.21.1' %}
        return stack.getChemical();
{% endcase %}
    }

    @Override
    public SlurryStack copy(SlurryStack stack) {
        return stack.copy();
    }

    @Override
    protected SlurryStack setCount(
            SlurryStack stack,
            long amount
    ) {
        stack.setAmount(amount);
        return stack;
    }
}
