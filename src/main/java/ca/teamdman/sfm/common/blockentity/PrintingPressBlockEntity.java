package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.recipe.NotContainer;
import ca.teamdman.sfm.common.registry.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.common.registry.SFMRecipeTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * Accepts a paper item and a form item.
 * When a piston is pressed on top of this block, it will print the form onto the paper.
 */
public class PrintingPressBlockEntity extends BlockEntity implements NotContainer {


    private final ItemStackHandler FORM = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null)
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() == SFMItems.FORM_ITEM.get();
        }
    };

    private final ItemStackHandler INK = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null)
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (getLevel() == null) return false;
            return getLevel()
                    .getRecipeManager()
                    .getAllRecipesFor(SFMRecipeTypes.PRINTING_PRESS.get())
                    .stream()
                    .anyMatch(r -> r.value().INK.test(stack));
        }
    };

    private final ItemStackHandler PAPER = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null)
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (getLevel() == null) return false;
            return getLevel()
                    .getRecipeManager()
                    .getAllRecipesFor(SFMRecipeTypes.PRINTING_PRESS.get())
                    .stream()
                    .anyMatch(r -> r.value().PAPER.test(stack));
        }
    };
    public final CombinedInvWrapper INVENTORY = new CombinedInvWrapper(FORM, INK, PAPER);


    public PrintingPressBlockEntity(
            BlockPos pPos, BlockState pBlockState
    ) {
        super(SFMBlockEntities.PRINTING_PRESS_BLOCK_ENTITY.get(), pPos, pBlockState);
    }


    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        readItems(tag);
    }


    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        writeItems(tag);
    }

    private void writeItems(CompoundTag tag) {
        tag.put("form", FORM.serializeNBT());
        tag.put("paper", PAPER.serializeNBT());
        tag.put("ink", INK.serializeNBT());
    }

    private void readItems(CompoundTag tag) {
        INK.deserializeNBT(tag.getCompound("ink"));
        PAPER.deserializeNBT(tag.getCompound("paper"));
        FORM.deserializeNBT(tag.getCompound("form"));
    }


    public ItemStack acceptStack(ItemStack stack) {
        ItemStack remainder;
        if (!stack.isEmpty()) {
            remainder = FORM.insertItem(0, stack.copy(), false);
            if (remainder.getCount() < stack.getCount()) {
                stack.shrink(stack.getCount() - remainder.getCount());
                return stack;
            }
            remainder = INK.insertItem(0, stack.copy(), false);
            if (remainder.getCount() < stack.getCount()) {
                stack.shrink(stack.getCount() - remainder.getCount());
                return stack;
            }
            remainder = PAPER.insertItem(0, stack.copy(), false);
            if (remainder.getCount() < stack.getCount()) {
                stack.shrink(stack.getCount() - remainder.getCount());
                return stack;
            }
        } else {
            ItemStack found;
            found = PAPER.extractItem(0, 64, false);
            if (!found.isEmpty()) {
                return found;
            }
            found = FORM.extractItem(0, 64, false);
            if (!found.isEmpty()) {
                return found;
            }
            found = INK.extractItem(0, 64, false);
            if (!found.isEmpty()) {
                return found;
            }
        }
        return stack;
    }

    @Override
    public CompoundTag getUpdateTag() {
        var tag = super.getUpdateTag();
        writeItems(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        super.onDataPacket(net, pkt);
        CompoundTag tag = pkt.getTag();
        if (tag != null)
            readItems(tag);
    }

    public ItemStack getPaper() {
        return PAPER.getStackInSlot(0);
    }

    public ItemStack getInk() {
        return INK.getStackInSlot(0);
    }

    public ItemStack getForm() {
        return FORM.getStackInSlot(0);
    }

    public void performPrint() {
        if (getLevel() == null) return;
        RecipeManager recipeManager = getLevel().getRecipeManager();
        recipeManager.getRecipeFor(SFMRecipeTypes.PRINTING_PRESS.get(), this, getLevel()).ifPresent(recipe -> {
            ItemStack paper = getPaper();
            ItemStack ink = getInk();
            ItemStack form = getForm();
            if (paper.isEmpty() || ink.isEmpty() || form.isEmpty()) {
                return;
            }
            paper = recipe.value().assemble(this, getLevel().registryAccess());
            PAPER.setStackInSlot(0, paper);
            ink.shrink(1);
            INK.setStackInSlot(0, ink);
        });
    }

    public ItemStack[] getStacksToDrop() {
        return new ItemStack[]{getPaper(), getInk(), getForm()};
    }
}
