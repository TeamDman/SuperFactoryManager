package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.Constants;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.net.ClientboundManagerGuiPacket;
import ca.teamdman.sfm.common.registry.SFMBlockEntities;
import ca.teamdman.sfm.common.util.OpenContainerTracker;
import ca.teamdman.sfm.common.util.SFMContainerUtil;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

public class ManagerBlockEntity extends BaseContainerBlockEntity {
    public static final int TICK_TIME_HISTORY_SIZE = 20;
    private final NonNullList<ItemStack> ITEMS = NonNullList.withSize(1, ItemStack.EMPTY);
    private final long[] tickTimeNanos = new long[TICK_TIME_HISTORY_SIZE];
    private @Nullable Program program = null;
    private int tick = 0;
    private int unprocessedRedstonePulses = 0; // used by redstone trigger
    private boolean shouldRebuildProgram = false;
    private int tickIndex = 0;

    public ManagerBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(SFMBlockEntities.MANAGER_BLOCK_ENTITY.get(), blockPos, blockState);
    }

    public static void serverTick(
            @SuppressWarnings("unused") Level level,
            @SuppressWarnings("unused") BlockPos pos,
            @SuppressWarnings("unused") BlockState state,
            ManagerBlockEntity tile
    ) {
        long start = System.nanoTime();
        tile.tick++;
        if (tile.shouldRebuildProgram) {
            tile.rebuildProgramAndUpdateDisk();
            tile.shouldRebuildProgram = false;
        }
        if (tile.program != null) {
            boolean didSomething = tile.program.tick(tile);
            if (didSomething) {
                long nanoTimePassed = Long.min(System.nanoTime() - start, Integer.MAX_VALUE);
                tile.tickTimeNanos[tile.tickIndex] = (int) nanoTimePassed;
                tile.tickIndex = (tile.tickIndex + 1) % tile.tickTimeNanos.length;
                tile.sendUpdatePacket();
            }
        }
    }

    private void sendUpdatePacket() {
        OpenContainerTracker.getPlayersWithOpenContainer(ManagerContainerMenu.class)
                .filter(entry -> entry.getValue().MANAGER_POSITION.equals(getBlockPos()))
                .forEach(entry ->
                                 PacketDistributor.PLAYER.with(entry.getKey()).send(
                                         new ClientboundManagerGuiPacket(
                                                 entry.getValue().containerId,
                                                 getProgramString().orElse(""),
                                                 getState(),
                                                 getTickTimeNanos()
                                         )
                                 ));
    }

    public int getTick() {
        return tick;
    }

    public Optional<Program> getProgram() {
        return Optional.ofNullable(program);
    }

    public void setProgram(String program) {
        getDisk().ifPresent(disk -> {
            DiskItem.setProgram(disk, program);
            rebuildProgramAndUpdateDisk();
            setChanged();
        });
    }

    public void trackRedstonePulseUnprocessed() {
        unprocessedRedstonePulses++;
    }

    public void clearRedstonePulseQueue() {
        unprocessedRedstonePulses = 0;
    }

    public int getUnprocessedRedstonePulseCount() {
        return unprocessedRedstonePulses;
    }

    public State getState() {
        if (getDisk().isEmpty()) return State.NO_DISK;
        if (getProgramString().isEmpty()) return State.NO_PROGRAM;
        if (program == null) return State.INVALID_PROGRAM;
        return State.RUNNING;
    }

    public Optional<String> getProgramString() {
        return getDisk().map(DiskItem::getProgram).filter(prog -> !prog.isBlank());
    }

    public Set<String> getReferencedLabels() {
        if (program == null) return Collections.emptySet();
        return program.referencedLabels();
    }

    public Optional<ItemStack> getDisk() {
        var item = getItem(0);
        if (item.getItem() instanceof DiskItem) return Optional.of(item);
        return Optional.empty();
    }

    public void rebuildProgramAndUpdateDisk() {
        if (level != null && level.isClientSide()) return;
        this.program = getDisk()
                .flatMap(itemStack -> DiskItem.updateDetails(itemStack, this))
                .orElse(null);
        sendUpdatePacket();
    }

    @Override
    protected Component getDefaultName() {
        return Constants.LocalizationKeys.MANAGER_CONTAINER.getComponent();
    }

    @Override
    protected AbstractContainerMenu createMenu(int windowId, Inventory inv) {
        return new ManagerContainerMenu(windowId, inv, this);
    }

    @Override
    public int getContainerSize() {
        return ITEMS.size();
    }

    @Override
    public boolean isEmpty() {
        return ITEMS.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < 0 || slot >= ITEMS.size()) return ItemStack.EMPTY;
        return ITEMS.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        var result = ContainerHelper.removeItem(ITEMS, slot, amount);
        if (slot == 0) rebuildProgramAndUpdateDisk();
        setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        var result = ContainerHelper.takeItem(ITEMS, slot);
        if (slot == 0) rebuildProgramAndUpdateDisk();
        setChanged();
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= ITEMS.size()) return;
        ITEMS.set(slot, stack);
        if (slot == 0) rebuildProgramAndUpdateDisk();
        setChanged();
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.getItem() instanceof DiskItem;
    }

    @Override
    public boolean stillValid(Player player) {
        return SFMContainerUtil.stillValid(this, player);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ContainerHelper.loadAllItems(tag, ITEMS);
        this.shouldRebuildProgram = true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, ITEMS);
    }


    @Override
    public void clearContent() {
        ITEMS.clear();
    }

    public void reset() {
        getDisk().ifPresent(disk -> {
            disk.setTag(null);
            setItem(0, disk);
            setChanged();
        });
    }

    public long[] getTickTimeNanos() {
        // tickTimeNanos is used as a cyclical buffer, transform it to have the first index be the most recent tick
        long[] result = new long[tickTimeNanos.length];
        System.arraycopy(tickTimeNanos, tickIndex, result, 0, tickTimeNanos.length - tickIndex);
        System.arraycopy(tickTimeNanos, 0, result, tickTimeNanos.length - tickIndex, tickIndex);
        return result;
    }

    public enum State {
        NO_PROGRAM(
                ChatFormatting.RED,
                Constants.LocalizationKeys.MANAGER_GUI_STATE_NO_PROGRAM
        ), NO_DISK(
                ChatFormatting.RED,
                Constants.LocalizationKeys.MANAGER_GUI_STATE_NO_DISK
        ), RUNNING(ChatFormatting.GREEN, Constants.LocalizationKeys.MANAGER_GUI_STATE_RUNNING), INVALID_PROGRAM(
                ChatFormatting.DARK_RED,
                Constants.LocalizationKeys.MANAGER_GUI_STATE_INVALID_PROGRAM
        );

        public final ChatFormatting COLOR;
        public final Constants.LocalizationKeys.LocalizationEntry LOC;

        State(ChatFormatting color, Constants.LocalizationKeys.LocalizationEntry loc) {
            COLOR = color;
            LOC = loc;
        }
    }

}
