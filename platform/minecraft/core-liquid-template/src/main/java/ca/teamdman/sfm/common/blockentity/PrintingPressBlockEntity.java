package ca.teamdman.sfm.common.blockentity;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.recipe.NotContainer;
{% when '1.20.3', '1.20.4' %}
import ca.teamdman.sfm.common.recipe.NotContainer;
{% when '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
import ca.teamdman.sfm.common.recipe.PrintingPressRecipe;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.registry.registration.SFMRecipeTypes;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.core.BlockPos;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
import net.minecraft.core.Direction;
{% when '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.core.HolderLookup;
{% endcase %}
import net.minecraft.nbt.CompoundTag;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.network.Connection;
{% when '26.1.2' %}
{% endcase %}
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.world.Containers;
{% endcase %}
import net.minecraft.world.item.ItemStack;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.world.item.crafting.RecipeInput;
{% endcase %}
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.NotNull;
{% when '1.20.2' %}
import net.neoforged.neoforge.common.capabilities.Capability;
import net.neoforged.neoforge.common.util.LazyOptional;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.NotNull;
{% when '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
{% when '26.1.2' %}
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStackResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;
{% endcase %}
import org.jetbrains.annotations.Nullable;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import java.util.Objects;

{% endcase %}
/**
 * Accepts a paper item and a form item.
 * When a piston is pressed on top of this block, it will print the form onto the paper.
 */
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
public class PrintingPressBlockEntity extends BlockEntity implements NotContainer {
{% when '1.21', '1.21.1', '26.1.2' %}
public class PrintingPressBlockEntity extends BlockEntity implements RecipeInput {
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    private final ItemStackHandler FORM = new ItemStackHandler(1) {
{% when '26.1.2' %}
    private final ItemStackResourceHandler FORM = new ItemStackResourceHandler() {
        private ItemStack item = ItemStack.EMPTY;
{% endcase %}
        @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null)
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
{% when '26.1.2' %}
        protected ItemStack getStack() {
            return item;
{% endcase %}
        }

        @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        public int getSlotLimit(int slot) {
{% when '26.1.2' %}
        protected void setStack(ItemStack itemStack) {
            item = itemStack;
        }

        @Override
        protected int getCapacity(ItemResource resource) {
{% endcase %}
            return 1;
        }

        @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() == SFMItems.FORM.get();
{% when '26.1.2' %}
        public boolean isValid(ItemResource resource) {
            return resource.is(SFMItems.FORM.get());
{% endcase %}
        }
    };

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    private final ItemStackHandler INK = new ItemStackHandler(1) {
{% when '26.1.2' %}
    private final ItemStackResourceHandler INK = new ItemStackResourceHandler() {
        private ItemStack item = ItemStack.EMPTY;
{% endcase %}
        @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null)
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
{% when '26.1.2' %}
        protected ItemStack getStack() {
            return item;
{% endcase %}
        }

        @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        public boolean isItemValid(int slot, ItemStack stack) {
{% when '26.1.2' %}
        protected void setStack(ItemStack itemStack) {
            item = itemStack;
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
{% endcase %}
            if (getLevel() == null) return false;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
            return getLevel().getRecipeManager()
                    .getAllRecipesFor(SFMRecipeTypes.PRINTING_PRESS.get()).stream()
                    .anyMatch(r -> r.ink().test(stack));
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            return getLevel().getRecipeManager()
                    .getAllRecipesFor(SFMRecipeTypes.PRINTING_PRESS.get()).stream()
                    .anyMatch(r -> r.value().ink().test(stack));
{% when '26.1.2' %}
            RecipeManager recipes = Objects.requireNonNull(getLevel().getServer()).getRecipeManager();
            return recipes
                    .recipeMap()
                    .byType(SFMRecipeTypes.PRINTING_PRESS.get()).stream()
                    .anyMatch(r -> r.value().ink().test(resource.toStack()));
{% endcase %}
        }
    };

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    private final ItemStackHandler PAPER = new ItemStackHandler(1) {
{% when '26.1.2' %}
    private final ItemStackResourceHandler PAPER = new ItemStackResourceHandler() {
        private ItemStack item = ItemStack.EMPTY;
{% endcase %}
        @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null)
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
{% when '26.1.2' %}
        protected ItemStack getStack() {
            return item;
{% endcase %}
        }

        @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        public int getSlotLimit(int slot) {
{% when '26.1.2' %}
        protected void setStack(ItemStack itemStack) {
            item = itemStack;
        }

        @Override
        protected int getCapacity(ItemResource resource) {
{% endcase %}
            return 1;
        }

        @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        public boolean isItemValid(int slot, ItemStack stack) {
{% when '26.1.2' %}
        public boolean isValid(int index, ItemResource resource) {
{% endcase %}
            if (getLevel() == null) return false;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
            return getLevel().getRecipeManager()
                    .getAllRecipesFor(SFMRecipeTypes.PRINTING_PRESS.get()).stream()
                    .anyMatch(r -> r.paper().test(stack));
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            return getLevel().getRecipeManager()
                    .getAllRecipesFor(SFMRecipeTypes.PRINTING_PRESS.get()).stream()
                    .anyMatch(r -> r.value().paper().test(stack));
{% when '26.1.2' %}
            RecipeManager recipes = Objects.requireNonNull(getLevel().getServer()).getRecipeManager();
            return recipes
                    .recipeMap()
                    .byType(SFMRecipeTypes.PRINTING_PRESS.get()).stream()
                    .anyMatch(r -> r.value().paper().test(resource.toStack()));
{% endcase %}
        }
    };

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
    private final LazyOptional<IItemHandler> ITEMS_CAPABILITY = LazyOptional.of(() -> new CombinedInvWrapper(
            FORM,
            INK,
            PAPER
    ));
{% when '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public final CombinedInvWrapper INVENTORY = new CombinedInvWrapper(FORM, INK, PAPER);
{% when '26.1.2' %}
    public final CombinedResourceHandler<ItemResource> INVENTORY = new CombinedResourceHandler<>(PAPER, FORM, INK);
{% endcase %}

    public PrintingPressBlockEntity(
            BlockPos pPos, BlockState pBlockState
    ) {
        super(SFMBlockEntities.PRINTING_PRESS.get(), pPos, pBlockState);
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
    public void load(
            CompoundTag tag
    ) {
        super.load(tag);
        readItems(tag);
    }

    @Override
    protected void saveAdditional(
            CompoundTag tag
    ) {
        super.saveAdditional(tag);
        writeItems(tag);
    }

    private void writeItems(
            CompoundTag tag
    ) {
        tag.put("form", FORM.serializeNBT());
        tag.put("paper", PAPER.serializeNBT());
        tag.put("ink", INK.serializeNBT());
    }

    private void readItems(
            CompoundTag tag
    ) {
        INK.deserializeNBT(tag.getCompound("ink"));
        PAPER.deserializeNBT(tag.getCompound("paper"));
        FORM.deserializeNBT(tag.getCompound("form"));
{% when '1.21', '1.21.1' %}
    public ItemStack getItem(int slot) {
        return INVENTORY.getStackInSlot(slot);
    }

    @Override
    public int size() {
        return INVENTORY.getSlots();
    }

    @Override
    protected void loadAdditional(
            CompoundTag pTag,
            HolderLookup.Provider pRegistries
    ) {
        super.loadAdditional(pTag, pRegistries);
        readItems(pTag, pRegistries);
{% when '26.1.2' %}
    public ItemStack getItem(int slot) {
        return INVENTORY.getResource(slot).toStack(INVENTORY.getAmountAsInt(slot));
{% endcase %}
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == SFMWellKnownCapabilities.ITEM_HANDLER.capabilityKind()) {
            return ITEMS_CAPABILITY.cast();
        }
        return super.getCapability(cap, side);
    }

{% when '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1' %}
    @Override
    protected void saveAdditional(
            CompoundTag pTag,
            HolderLookup.Provider pRegistries
    ) {
        super.saveAdditional(pTag, pRegistries);
        writeItems(pTag, pRegistries);
    }

{% when '26.1.2' %}
    @Override
    public int size() {
        return INVENTORY.size();
    }

{% endcase %}
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1' %}
    private void writeItems(
            CompoundTag tag,
            HolderLookup.Provider pRegistries
    ) {
        tag.put("form", FORM.serializeNBT(pRegistries));
        tag.put("paper", PAPER.serializeNBT(pRegistries));
        tag.put("ink", INK.serializeNBT(pRegistries));
    }

    private void readItems(
            CompoundTag tag,
            HolderLookup.Provider pRegistries
    ) {
        INK.deserializeNBT(pRegistries, tag.getCompound("ink"));
        PAPER.deserializeNBT(pRegistries, tag.getCompound("paper"));
        FORM.deserializeNBT(pRegistries, tag.getCompound("form"));
    }

{% when '26.1.2' %}
    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
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

{% endcase %}
    public ItemStack acceptStack(ItemStack stack) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        ItemStack remainder;
        if (!stack.isEmpty()) {
            remainder = FORM.insertItem(0, stack.copy(), false);
            if (remainder.getCount() < stack.getCount()) {
                stack.shrink(stack.getCount() - remainder.getCount());
                return stack;
{% when '26.1.2' %}
        if (stack.isEmpty()) {
            try (var ctx = Transaction.openRoot()) {
                ResourceStack<ItemResource> extracted = ResourceHandlerUtil.extractFirst(INVENTORY, (_) -> true, 64, ctx);
                if (extracted != null) {
                    ctx.commit();
                    setChanged();
                    return extracted.resource().toStack(extracted.amount());
                }
{% endcase %}
            }
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
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
{% when '26.1.2' %}
            return stack;
        }

        ItemResource resource = ItemResource.of(stack);
        for (ItemStackResourceHandler handler : new ItemStackResourceHandler[]{FORM, INK, PAPER}) {
            if (handler.isValid(0, resource)) {
                try (var ctx = Transaction.openRoot()) {
                    ItemStack remainder = ItemUtil.insertItemReturnRemaining(handler, stack, false, ctx);
                    if (remainder.getCount() < stack.getCount()) {
                        ctx.commit();
                        setChanged();
                        return remainder;
                    }
                }
{% endcase %}
            }
        }
        return stack;
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
    public CompoundTag getUpdateTag() {
        var tag = super.getUpdateTag();
        writeItems(tag);
        return tag;
{% when '1.21', '1.21.1' %}
    public CompoundTag getUpdateTag(HolderLookup.Provider pRegistries) {
        var tag = super.getUpdateTag(pRegistries);
        writeItems(tag, pRegistries);
        return tag;
{% when '26.1.2' %}
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        super.handleUpdateTag(input);
        readItems(input);
{% endcase %}
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
    @Override
    public void onDataPacket(
            Connection net,
            ClientboundBlockEntityDataPacket pkt
    ) {
        super.onDataPacket(net, pkt);
        CompoundTag tag = pkt.getTag();
        if (tag != null)
            readItems(tag);
    }

{% when '1.21', '1.21.1' %}
    @Override
    public void onDataPacket(
            Connection net,
            ClientboundBlockEntityDataPacket pkt,
            HolderLookup.Provider lookupProvider
    ) {
        super.onDataPacket(net, pkt, lookupProvider);
        readItems(pkt.getTag(), lookupProvider);
    }

{% when '26.1.2' %}
{% endcase %}
    public ItemStack getPaper() {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        return PAPER.getStackInSlot(0);
{% when '26.1.2' %}
        return PAPER.getResource(0).toStack();
{% endcase %}
    }

    public ItemStack getInk() {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        return INK.getStackInSlot(0);
{% when '26.1.2' %}
        return INK.getResource(0).toStack(INK.getAmountAsInt(0));
{% endcase %}
    }

    public ItemStack getForm() {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        return FORM.getStackInSlot(0);
{% when '26.1.2' %}
        return FORM.getResource(0).toStack();
{% endcase %}
    }

    public void performPrint() {
        if (getLevel() == null) return;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        RecipeManager recipeManager = getLevel().getRecipeManager();
{% when '26.1.2' %}
        RecipeManager recipeManager = Objects.requireNonNull(getLevel().getServer()).getRecipeManager();
{% endcase %}
        recipeManager.getRecipeFor(SFMRecipeTypes.PRINTING_PRESS.get(), this, getLevel()).ifPresent(recipe -> {
            ItemStack paper = getPaper();
            ItemStack ink = getInk();
            ItemStack form = getForm();
            if (paper.isEmpty() || ink.isEmpty() || form.isEmpty()) {
                return;
            }
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
            paper = assembleRecipe(recipe);
            PAPER.setStackInSlot(0, paper);
            ink.shrink(1);
            INK.setStackInSlot(0, ink);
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            paper = recipe.value().assemble(this, getLevel().registryAccess());
            PAPER.setStackInSlot(0, paper);
            ink.shrink(1);
            INK.setStackInSlot(0, ink);
{% when '26.1.2' %}
            ItemStack result = recipe.value().assemble(this);

            try (var tx = Transaction.openRoot()) {
                INK.extract(ItemResource.of(ink), 1, tx);
                PAPER.extract(ItemResource.of(paper), paper.getCount(), tx);

                PAPER.insert(ItemResource.of(result), result.getCount(), tx);
                setChanged();
                tx.commit();
            }
{% endcase %}
        });
    }

    @MCVersionDependentBehaviour
    private ItemStack assembleRecipe(PrintingPressRecipe recipe) {
{% case minecraft_version %}
{% when '1.19.2' %}
        return recipe.assemble(this);
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        assert level != null;
        return recipe.assemble(this, level.registryAccess());
{% when '26.1.2' %}
        assert level != null;
        return recipe.assemble(this);
{% endcase %}
    }

    public ItemStack[] getStacksToDrop() {
        return new ItemStack[]{getPaper(), getInk(), getForm()};
    }
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
//        super.preRemoveSideEffects(pos, state);
        if (this.level != null) {
            for (ItemStack item : getStacksToDrop()) {
                Containers.dropItemStack(this.level, pos.getX(), pos.getY(), pos.getZ(), item);
            }
        }
    }

{% endcase %}
}
