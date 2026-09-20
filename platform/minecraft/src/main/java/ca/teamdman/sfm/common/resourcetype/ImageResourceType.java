package ca.teamdman.sfm.common.resourcetype;

import ca.teamdman.sfm.common.block.BufferBlock;
import ca.teamdman.sfm.common.blockentity.BufferBlockEntityContents;
import ca.teamdman.sfm.common.capability.IImageHandler;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import net.minecraft.resources.ResourceLocation;

import java.util.stream.Stream;

/** One immutable image snapshot per transfer, addressed by the `sfm:image` resource type. */
public class ImageResourceType extends ScalarResourceType<SFMImageStack, IImageHandler> {
    public ImageResourceType() {
        this(SFMWellKnownCapabilities.IMAGE_HANDLER);
    }

    /** Allows pure resource semantics to be tested without Forge's runtime capability transformer. */
    ImageResourceType(SFMBlockCapabilityKind<IImageHandler> capability) {
        super(capability, SFMResourceLocation.fromNamespaceAndPath("sfm", "image"), SFMImageStack.class);
    }

    @Override
    public IImageHandler createHandlerForBufferBlock(BufferBlockEntityContents contents) {
        return new IImageHandler() {
            private SFMImageStack held = SFMImageStack.EMPTY;

            @Override
            public SFMImageStack getImage() {
                return held;
            }

            @Override
            public SFMImageStack insertImage(SFMImageStack image, boolean simulate) {
                if (image.isEmpty()) return SFMImageStack.EMPTY;
                if (!held.isEmpty() || !contents.allowInsertion(ImageResourceType.this)) return image;
                if (!simulate) {
                    held = image;
                    contents.lastUsedResource = BufferBlock.ContainedResource.Image;
                    contents.markChanged();
                }
                return SFMImageStack.EMPTY;
            }

            @Override
            public SFMImageStack extractImage(boolean simulate) {
                SFMImageStack result = held;
                if (!simulate && !held.isEmpty()) {
                    held = SFMImageStack.EMPTY;
                    contents.lastUsedResource = BufferBlock.ContainedResource.Unknown;
                    contents.markChanged();
                }
                return result;
            }
        };
    }

    @Override
    public long getAmount(SFMImageStack stack) {
        return stack.isEmpty() ? 0 : 1;
    }

    @Override
    public SFMImageStack getStackInSlot(IImageHandler handler, int slot) {
        return slot == 0 ? handler.getImage() : SFMImageStack.EMPTY;
    }

    @Override
    public SFMImageStack extract(IImageHandler handler, int slot, long amount, boolean simulate) {
        return slot == 0 && amount >= 1 ? handler.extractImage(simulate) : SFMImageStack.EMPTY;
    }

    @Override
    public boolean canExtract(IImageHandler handler, int slot) {
        return slot == 0 && handler.canExtract();
    }

    @Override
    public int getSlots(IImageHandler handler) {
        return 1;
    }

    @Override
    public long getMaxStackSize(SFMImageStack stack) {
        return 1;
    }

    @Override
    public long getMaxStackSizeForSlot(IImageHandler handler, int slot) {
        return slot == 0 ? 1 : 0;
    }

    @Override
    public SFMImageStack insert(IImageHandler handler, int slot, SFMImageStack stack, boolean simulate) {
        return slot == 0 ? handler.insertImage(stack, simulate) : stack;
    }

    @Override
    public boolean canInsert(IImageHandler handler, int slot) {
        return slot == 0 && handler.canInsert();
    }

    @Override
    public boolean isEmpty(SFMImageStack stack) {
        return stack.isEmpty();
    }

    @Override
    public SFMImageStack getEmptyStack() {
        return SFMImageStack.EMPTY;
    }

    @Override
    public boolean matchesCapabilityHandler(Object object) {
        return object instanceof IImageHandler;
    }

    @Override
    public Stream<ResourceLocation> getTagsForStack(SFMImageStack stack) {
        return Stream.empty();
    }

    @Override
    public SFMImageStack copy(SFMImageStack stack) {
        return stack;
    }

    @Override
    protected SFMImageStack setCount(SFMImageStack stack, long amount) {
        return amount <= 0 ? SFMImageStack.EMPTY : stack;
    }
}
