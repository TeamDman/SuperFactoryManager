package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.recipe.PrintingPressRecipe;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.registry.registration.SFMRecipeTypes;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeAccess;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStackResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Accepts a paper item and a form item.
 * When a piston is pressed on top of this block, it will print the form onto the paper.
 */
public class PrintingPressBlockEntity extends BlockEntity implements RecipeInput {

    private final ItemStackResourceHandler FORM = new ItemStackResourceHandler() {
        private ItemStack item = ItemStack.EMPTY;
        @Override
        protected ItemStack getStack() {
            return item;
        }

        @Override
        protected void setStack(ItemStack itemStack) {
            item = itemStack;
        }

        @Override
        protected int getCapacity(ItemResource resource) {
            return 1;
        }

        @Override
        public boolean isValid(ItemResource resource) {
            return resource.is(SFMItems.FORM.get());
        }
    };
    private final ItemStackResourceHandler INK = new ItemStackResourceHandler() {
        private ItemStack item = ItemStack.EMPTY;
        @Override
        protected ItemStack getStack() {
            return item;
        }

        @Override
        protected void setStack(ItemStack itemStack) {
            item = itemStack;
        }

        @Override
        protected int getCapacity(ItemResource resource) {
            return 1;
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            if (getLevel() == null) return false;
            RecipeManager recipes = Objects.requireNonNull(getLevel().getServer()).getRecipeManager();
            return recipes
                    .recipeMap().byType(SFMRecipeTypes.PRINTING_PRESS.get()).stream().anyMatch(r -> r.value().ink().test(resource.toStack()));
        }
    };
    private final ItemStackResourceHandler PAPER = new ItemStackResourceHandler() {
        private ItemStack item = ItemStack.EMPTY;
        @Override
        protected ItemStack getStack() {
            return item;
        }

        @Override
        protected void setStack(ItemStack itemStack) {
            item = itemStack;
        }

        @Override
        protected int getCapacity(ItemResource resource) {
            return 1;
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            if (getLevel() == null) return false;
            RecipeManager recipes = Objects.requireNonNull(getLevel().getServer()).getRecipeManager();
            return recipes
                    .recipeMap().byType(SFMRecipeTypes.PRINTING_PRESS.get()).stream().anyMatch(r -> r.value().paper().test(resource.toStack()));
        }
    };

    public final CombinedResourceHandler<ItemResource> INVENTORY = new CombinedResourceHandler<>(FORM, INK, PAPER);

    public PrintingPressBlockEntity(
            BlockPos pPos, BlockState pBlockState
    ) {
        super(SFMBlockEntities.PRINTING_PRESS.get(), pPos, pBlockState);
    }

    @Override
    public ItemStack getItem(int slot) {
        return INVENTORY.getResource(slot).toStack(INVENTORY.getAmountAsInt(slot));
    }

    @Override
    public int size() {
        return INVENTORY.size();
    }


    @Override
    protected void loadAdditional(
            ValueInput input
    ) {
        super.loadAdditional(input);
        readItems(input);
    }

    @Override
    protected void saveAdditional(
            ValueOutput output
    ) {
        super.saveAdditional(output);
        writeItems(output);
    }

    private void writeItems(
            ValueOutput output
    ) {
        output.putChild("form", FORM);
        output.putChild("paper", PAPER);
        output.putChild("ink", INK);
    }

    private void readItems(
            ValueInput input
    ) {
        input.readChild("form", FORM);
        input.readChild("paper", PAPER);
        input.readChild("ink", INK);
    }


    public ItemStack acceptStack(ItemStack stack) {
        ItemResource resource = ItemResource.of(stack);
        if (!resource.isEmpty()) {
            try (var tx = Transaction.openRoot()) {
                ItemStack remainder = ItemUtil.insertItemReturnRemaining(INVENTORY, stack, false, tx);

                if (remainder.getCount() < stack.getCount()) {
                    tx.commit();
                    return remainder;
                }
            }
        } else {
            try (var tx = Transaction.openRoot()) {
                ResourceStack<ItemResource> extracted = ResourceHandlerUtil.extractFirst(INVENTORY, (_) -> true, 64, tx);
                if (extracted != null) {
                    return extracted.resource().toStack(extracted.amount());
                }
            }
        }
            return stack;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        super.handleUpdateTag(input);
        readItems(input);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public ItemStack getPaper() {
        return PAPER.getResource(0).toStack();
    }

    public ItemStack getInk() {
        return INK.getResource(0).toStack();
    }

    public ItemStack getForm() {
        return FORM.getResource(0).toStack();
    }

    public void performPrint() {
        if (getLevel() == null) return;
        RecipeManager recipeManager = Objects.requireNonNull(getLevel().getServer()).getRecipeManager();
        recipeManager.getRecipeFor(SFMRecipeTypes.PRINTING_PRESS.get(), this, getLevel()).ifPresent(recipe -> {
            ItemStack paper = getPaper();
            ItemStack ink = getInk();
            ItemStack form = getForm();
            if (paper.isEmpty() || ink.isEmpty() || form.isEmpty()) {
                return;
            }
            ItemStack result = recipe.value().assemble(this);

            try (var tx = Transaction.openRoot()) {
                INK.extract(ItemResource.of(ink), 1, tx);
                PAPER.extract(ItemResource.of(paper), paper.getCount(), tx);

                PAPER.insert(ItemResource.of(result), result.getCount(), tx);
                tx.commit();
            }
        });
    }

    @MCVersionDependentBehaviour
    private ItemStack assembleRecipe(PrintingPressRecipe recipe) {
        assert level != null;
        return recipe.assemble(this);
    }

    public ItemStack[] getStacksToDrop() {
        return new ItemStack[]{getPaper(), getInk(), getForm()};
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
//        super.preRemoveSideEffects(pos, state);
        if (this.level != null) {
            for (ItemStack item : getStacksToDrop()) {
                Containers.dropItemStack(this.level, pos.getX(), pos.getY(), pos.getZ(), item);
            }
        }
    }

}
