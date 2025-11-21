package ca.teamdman.sfm.common.blockentity;

import java.util.Collections;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import org.apache.logging.log4j.core.time.MutableInstant;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.screen.ManagerScreen;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.diagnostics.SFMDiagnostics;
import ca.teamdman.sfm.common.handler.OpenContainerTracker;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.logging.TranslatableLogger;
import ca.teamdman.sfm.common.net.ClientboundManagerGuiUpdatePacket;
import ca.teamdman.sfm.common.net.ClientboundManagerLogLevelUpdatedPacket;
import ca.teamdman.sfm.common.net.ClientboundManagerLogsPacket;
import ca.teamdman.sfm.common.registry.IGuiProvider;
import ca.teamdman.sfm.common.registry.SFMPackets;
import ca.teamdman.sfm.common.util.SFMContainerUtil;
import ca.teamdman.sfml.ast.Program;

public class ManagerBlockEntity extends TileEntity implements IInventory, ITickable, IGuiProvider {

    public static final int TICK_TIME_HISTORY_SIZE = 20;
    public final TranslatableLogger logger;
    private final NonNullList<ItemStack> ITEMS = NonNullList.withSize(1, ItemStack.EMPTY);
    private final long[] tickTimeNanos = new long[TICK_TIME_HISTORY_SIZE];
    private @Nullable Program program = null;
    private int configRevision = -1;
    private int tick = 0;
    private int unprocessedRedstonePulses = 0; // used by redstone trigger
    private boolean shouldRebuildProgram = false;
    private boolean shouldRebuildProgramLock = false;
    private int tickIndex = 0;

    public ManagerBlockEntity() {
        String loggerName = SFM.MOD_ID + ":manager@" + "@" + Integer.toHexString(System.identityHashCode(this));
        logger = new TranslatableLogger(loggerName);
    }

    @Override
    public String toString() {
        return "ManagerBlockEntity{" +
                "hasDisk=" + (getDisk() != null) +
                '}';
    }

    @Override
    public ManagerScreen getGui(int id, InventoryPlayer inv) {
        return new ManagerScreen(this.getContainer(id, inv));
    }

    @Override
    public ManagerContainerMenu getContainer(int id, InventoryPlayer inv) {
        return new ManagerContainerMenu(id, inv, this);
    }

    /**
     * Used to prevent tests which modify configs from interfering with other tests.
     * <p>
     * When the manager detects a config change and rebuilds, it clobbers the monkey patching used by the tests.
     */
    public void enableRebuildProgramLock() {
        shouldRebuildProgramLock = true;
    }

    public void serverTick() {
        var level = this.getWorld();
        var manager = this;
        try {
            long start = System.nanoTime();
            manager.tick++;
            if (manager.configRevision != SFMConfig.getConfigRevision()) {
                manager.shouldRebuildProgram = true;
            }
            if (manager.shouldRebuildProgram && !manager.shouldRebuildProgramLock) {
                manager.rebuildProgramAndUpdateDisk();
                manager.shouldRebuildProgram = false;
            }
            if (manager.program != null) {
                boolean didSomething = manager.program.tick(manager);
                if (didSomething) {
                    long nanoTimePassed = Long.min(System.nanoTime() - start, Integer.MAX_VALUE);
                    manager.tickTimeNanos[manager.tickIndex] = (int) nanoTimePassed;
                    manager.tickIndex = (manager.tickIndex + 1) % manager.tickTimeNanos.length;
                    manager.logger.trace(
                            x -> x.accept(LocalizationKeys.PROGRAM_TICK_TIME_MS.get(nanoTimePassed / 1_000_000f)));
                    manager.sendUpdatePacket();
                    manager.logger.pruneSoWeDontEatAllTheRam();

                    if (manager.logger.getLogLevel() == org.apache.logging.log4j.Level.TRACE ||
                            manager.logger.getLogLevel() == org.apache.logging.log4j.Level.DEBUG ||
                            manager.logger.getLogLevel() == org.apache.logging.log4j.Level.INFO) {
                        org.apache.logging.log4j.Level newLevel = org.apache.logging.log4j.Level.OFF;
                        manager.logger.info(x -> x.accept(LocalizationKeys.LOG_LEVEL_UPDATED.get(newLevel)));
                        var oldLevel = manager.logger.getLogLevel();
                        manager.setLogLevel(newLevel);
                        SFM.LOGGER.debug(
                                "SFM updated manager {} {} log level to {} after a single execution at {} level",
                                manager.getPos(),
                                manager.getWorld(),
                                newLevel,
                                oldLevel);
                    }
                }
            }
        } catch (Exception t) {
            String configPath = "config/superfactorymanager.cfg";
            String configValuePath = "server.disableProgramExecution";
            SFM.LOGGER.fatal(
                    "SFM detected a problem while ticking a manager. You can set `{} = true` in {} to help recover your world.",
                    configValuePath,
                    configPath);
            throw t;
        }
    }

    public void setLogLevel(org.apache.logging.log4j.Level logLevelObj) {
        logger.setLogLevel(logLevelObj);
        sendUpdatePacket();
    }

    public int getTick() {
        return tick;
    }

    public @Nullable Program getProgram() {
        return program;
    }

    public void setProgram(String program) {
        var disk = getDisk();
        if (disk != null) {
            DiskItem.setProgram(disk, program.replaceAll("\\s+$", ""));
            rebuildProgramAndUpdateDisk();
            markDirty();
        }
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
        if (getDisk() == null) return State.NO_DISK;
        if (getProgramString() == null) return State.NO_PROGRAM;
        if (program == null) return State.INVALID_PROGRAM;
        return State.RUNNING;
    }

    public @Nullable String getProgramString() {
        var disk = getDisk();
        if (disk == null) {
            return null;
        }

        var program = DiskItem.getProgram(disk);
        return program.trim().isEmpty() ? null : program;
    }

    public String getProgramStringOrEmptyIfNull() {
        var programString = this.getProgramString();
        return programString == null ? "" : programString;
    }

    public Set<String> getReferencedLabels() {
        if (program == null) return Collections.emptySet();
        return program.referencedLabels();
    }

    public @Nullable ItemStack getDisk() { // TODO: make this not nullable, should be fine to return empty :P
        var item = getStackInSlot(0);
        if (item.getItem() instanceof DiskItem) return item;
        return null;
    }

    public void rebuildProgramAndUpdateDisk() {
        if (world != null && world.isRemote) return;
        var disk = getDisk();
        if (disk == null) {
            this.program = null;
        } else {
            this.program = DiskItem.compileAndUpdateErrorsAndWarnings(disk, this);
        }
        this.configRevision = SFMConfig.getConfigRevision();
        sendUpdatePacket();
    }

    @Override
    public int getSizeInventory() {
        return ITEMS.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack itemstack : ITEMS) {
            if (!itemstack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        if (index < 0 || index >= ITEMS.size()) return ItemStack.EMPTY;
        return ITEMS.get(index);
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        return this.removeStackFromSlot(index);
    }

    @Override
    public ItemStack removeStackFromSlot(int index) {
        var result = ITEMS.get(index);
        ITEMS.set(index, ItemStack.EMPTY);
        if (index == 0) rebuildProgramAndUpdateDisk();
        markDirty();
        return result;
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        if (index < 0 || index >= ITEMS.size()) return;
        ITEMS.set(index, stack);
        if (stack.getCount() > getInventoryStackLimit()) {
            stack.setCount(getInventoryStackLimit());
        }
        if (index == 0) rebuildProgramAndUpdateDisk();
        markDirty();
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
    }

    @Override
    public boolean isUsableByPlayer(EntityPlayer player) {
        return SFMContainerUtil.stillValid(this, player);
    }

    @Override
    public void openInventory(EntityPlayer player) {}

    @Override
    public void closeInventory(EntityPlayer player) {}

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        return stack.getItem() instanceof DiskItem;
    }

    @Override
    public int getField(int id) {
        return 0;
    }

    @Override
    public void setField(int id, int value) {}

    @Override
    public int getFieldCount() {
        return 0;
    }

    @Override
    public void clear() {
        ITEMS.clear();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        ItemStackHelper.saveAllItems(tag, ITEMS);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        ItemStackHelper.loadAllItems(tag, ITEMS);
        this.shouldRebuildProgram = true;
        if (world != null) {
            this.tick = world.rand.nextInt();
        }
    }

    public void reset() {
        var disk = getDisk();
        if (disk != null) {
            LabelPositionHolder.clear(disk);
            disk.setTagCompound(null);
            setInventorySlotContents(0, disk);
            markDirty();
        }
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newSate) {
        return oldState.getBlock() != newSate.getBlock();
    }

    public long[] getTickTimeNanos() {
        // tickTimeNanos is used as a cyclical buffer, transform it to have the first index be the most recent tick
        long[] result = new long[tickTimeNanos.length];
        System.arraycopy(tickTimeNanos, tickIndex, result, 0, tickTimeNanos.length - tickIndex);
        System.arraycopy(tickTimeNanos, 0, result, tickTimeNanos.length - tickIndex, tickIndex);
        return result;
    }

    public void sendUpdatePacket() {
        if (world.isRemote) return;
        // Create one packet and clone it for each receiver
        var managerUpdatePacket = new ClientboundManagerGuiUpdatePacket(
                -1,
                getProgramStringOrEmptyIfNull(),
                getState(),
                getTickTimeNanos());

        OpenContainerTracker.getOpenManagerMenus(getPos())
                .forEach(entry -> {
                    ManagerContainerMenu menu = entry.getValue();

                    // Send a copy of the manager update packet
                    SFMPackets.SFM_CHANNEL.sendTo(managerUpdatePacket.cloneWithWindowId(menu.windowId), entry.getKey());

                    // The rest of the sync is only relevant if the log screen is open
                    if (!menu.isLogScreenOpen) return;

                    // Send log level changes
                    if (!menu.logLevel.equals(logger.getLogLevel().name())) {
                        SFMPackets.SFM_CHANNEL.sendTo(new ClientboundManagerLogLevelUpdatedPacket(
                                menu.windowId,
                                logger.getLogLevel().name()), entry.getKey());
                        menu.logLevel = logger.getLogLevel().name();
                    }

                    // Send new logs
                    MutableInstant hasSince = new MutableInstant();
                    if (!menu.logs.isEmpty()) {
                        hasSince.initFrom(menu.logs.getLast().instant());
                    }
                    var logsToSend = logger.getLogsAfter(hasSince);
                    if (!logsToSend.isEmpty()) {
                        // Add the latest entry to the server copy
                        // since the server copy is only used for checking what the latest log timestamp is
                        menu.logs.add(logsToSend.getLast());

                        // Send the logs
                        while (!logsToSend.isEmpty()) {
                            int remaining = logsToSend.size();
                            SFMPackets.SFM_CHANNEL.sendTo(ClientboundManagerLogsPacket.drainToCreate(
                                    menu.windowId,
                                    logsToSend), entry.getKey());
                            if (logsToSend.size() >= remaining) {
                                throw new IllegalStateException("Failed to send logs, infinite loop detected");
                            }
                        }
                    }
                });
        IBlockState state = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, state, state, 3);
    }

    @Override
    public void update() {
        if (world.isRemote) return;
        serverTick();
    }

    @Override
    public ITextComponent getDisplayName() {
        return LocalizationKeys.MANAGER_CONTAINER.getComponent();
    }

    // @Nullable
    // @Override
    // public SPacketUpdateTileEntity getUpdatePacket() {
    // return new SPacketUpdateTileEntity(this.pos, 3, this.getUpdateTag());
    // }

    @Override
    public NBTTagCompound getUpdateTag() {
        return this.writeToNBT(new NBTTagCompound());
    }

    @Override
    public String getName() {
        return LocalizationKeys.MANAGER_CONTAINER.get().getKey();
    }

    @Override
    public boolean hasCustomName() {
        return false;
    }

    public enum State {

        NO_PROGRAM(
                TextFormatting.RED,
                LocalizationKeys.MANAGER_GUI_STATE_NO_PROGRAM),
        NO_DISK(
                TextFormatting.RED,
                LocalizationKeys.MANAGER_GUI_STATE_NO_DISK),
        RUNNING(TextFormatting.GREEN, LocalizationKeys.MANAGER_GUI_STATE_RUNNING),
        INVALID_PROGRAM(
                TextFormatting.DARK_RED,
                LocalizationKeys.MANAGER_GUI_STATE_INVALID_PROGRAM);

        public final TextFormatting COLOR;
        public final LocalizationEntry LOC;

        State(
              TextFormatting color,
              LocalizationEntry loc) {
            COLOR = color;
            LOC = loc;
        }
    }

    @Override
    public void addInfoToCrashReport(CrashReportCategory pReportCategory) {
        super.addInfoToCrashReport(pReportCategory);
        {
            String configPath;
            configPath = "sfm-server.toml";

            pReportCategory.addDetail("SFM Reminder", () -> "You can set `server.disableProgramExecution = true` in " +
                    configPath + " to help recover your world.");
        }
        {
            ItemStack disk = getDisk();
            if (disk != null && !disk.isEmpty()) {
                pReportCategory.addDetail("SFM Details", () -> SFMDiagnostics.getDiagnosticsSummary(disk));
            }
        }
    }
}
