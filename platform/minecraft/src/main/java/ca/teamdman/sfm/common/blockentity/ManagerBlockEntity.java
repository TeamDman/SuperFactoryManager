package ca.teamdman.sfm.common.blockentity;

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
import ca.teamdman.sfm.common.program.IProgramHooks;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.registry.IGuiProvider;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.timing.SFMEpochInstant;
import ca.teamdman.sfm.common.timing.SFMInstant;
import ca.teamdman.sfm.common.util.Mth;
import ca.teamdman.sfm.common.util.SFMContainerUtil;
import ca.teamdman.sfml.ast.Program;
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
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.Level;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ManagerBlockEntity extends TileEntity implements IInventory, ITickable, IGuiProvider {
    public static final int TICK_TIME_HISTORY_SIZE = 20;

    public final TranslatableLogger logger;

    private final NonNullList<ItemStack> ITEMS = NonNullList.withSize(1, ItemStack.EMPTY);

    private final Duration[] tickTimes = new Duration[TICK_TIME_HISTORY_SIZE];
    private final Duration[] externalTickTimes = new Duration[TICK_TIME_HISTORY_SIZE];

    private @Nullable Program program = null;

    private int configRevision = -1;

    private int tick = 0;

    private int unprocessedRedstonePulses = 0; // used by redstone trigger

    private boolean shouldRebuildProgram = false;

    private int tickIndex = 0;

    /// When using a manager to frequently swap between two disks, we don't care about warnings as much.
    /// Warnings are still rebuilt when opening manager regardless of this value.
    private int automationAvoidRebuildingWarningsCooldown = 0;

    /// Callbacks for testing, used to assert postconditions
    private @Nullable List<IProgramHooks> programHooks = null;

    public ManagerBlockEntity(
    ) {
        String loggerName = SFM.MOD_ID
                + ":manager@"
                + "@" + Integer.toHexString(System.identityHashCode(this));
        logger = new TranslatableLogger(loggerName);
    }

    @Override
    public String toString() {

        return "ManagerBlockEntity{" +
                "hasDisk=" + (getDisk() != null) +
                ", pos=" + getPos() +
               ", level=" + getLevel() +
               '}';
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ManagerScreen getGui(int id, InventoryPlayer inv) {
        return new ManagerScreen(this.getContainer(id, inv));
    }

    @Override
    public ManagerContainerMenu getContainer(int id, InventoryPlayer inv) {
        return new ManagerContainerMenu(id, inv, this);
    }

    public void addProgramHooks(IProgramHooks hooks) {

        if (this.programHooks == null) {
            this.programHooks = new ArrayList<>();
        }
        this.programHooks.add(hooks);
    }


    public void serverTick() {
        var level = this.getWorld();
        var manager = this;
        try {
            // Get timestamp for elapsed time calculations
            SFMInstant start = SFMInstant.now();

            // Update tick counters
            manager.tick++;
            manager.decrementRebuildWarningsCooldown();

            // If config changed, mark dirty
            if (manager.configRevision != SFMConfig.getConfigRevision()) {
                manager.shouldRebuildProgram = true;
            }

            // Rebuild if dirty
            if (manager.shouldRebuildProgram) {
                manager.rebuildProgramAndUpdateDisk();
                manager.shouldRebuildProgram = false;
            }

            // Make sure manager has a program
            if (manager.program == null) {
                return;
            }

            // Tick the program and see if anything happened
            ProgramContext context = manager.program.tick(manager);
            boolean didSomething = context.didSomething();
            if (!didSomething) {
                return;
            }

            // Calculate and track the elapsed time
            Duration elapsed = start.elapsed();
            manager.tickTimes[manager.tickIndex] = elapsed;
            manager.externalTickTimes[manager.tickIndex] = context.getAccumulatedExternalIOTime();
            manager.tickIndex = (manager.tickIndex + 1) % manager.tickTimes.length;
            manager.logger.trace(x -> x.accept(
                    LocalizationKeys.PROGRAM_TICK_TIME_MS.get(elapsed.toNanos() / 1_000_000f)));

            // Run hooks if present
            if (manager.programHooks != null) {
                            for (IProgramHooks hook : manager.programHooks) {
                    hook.onProgramDidSomething(elapsed);
                }
            }

            // Distribute timing information to players
            manager.sendUpdatePacket();
            manager.logger.pruneSoWeDontEatAllTheRam();

            // Turn off logging after one execution
            if (manager.logger.getLogLevel() == org.apache.logging.log4j.Level.TRACE
                || manager.logger.getLogLevel() == org.apache.logging.log4j.Level.DEBUG
                || manager.logger.getLogLevel() == org.apache.logging.log4j.Level.INFO
            ) {
                org.apache.logging.log4j.Level newLogLevel = org.apache.logging.log4j.Level.OFF;
                manager.logger.info(x -> x.accept(LocalizationKeys.LOG_LEVEL_UPDATED.get(newLogLevel)));
                var oldLogLevel = manager.logger.getLogLevel();
                manager.setLogLevel(newLogLevel);
                SFM.LOGGER.debug(
                        "SFM updated manager {} {} log level to {} after a single execution at {} level",
                        manager.getPos(),
                        manager.getWorld(),
                        newLogLevel,
                        oldLogLevel
                );
            }
        } catch (Throwable t) {
            // Inform the user that they can disable the manager in the config
            String configPath = "config/superfactorymanager.cfg";
            String configValuePath = "client.disableProgramExecution";
            SFM.LOGGER.fatal(
                    "SFM detected a problem while ticking a manager. You can set `{} = true` in {} to help recover your world.",
                    configValuePath,
                    configPath
            );
            throw t;
        }
    }

    public void setLogLevel(Level logLevelObj) {
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

            // always rebuild warnings when modifying program string
            this.ensureRebuildWarnings();

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

        var program = DiskItem.getProgramString(disk);
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

    public boolean shouldRebuildWarnings() {

        return this.automationAvoidRebuildingWarningsCooldown < 300; // arbitrary threshold
    }

    public void ensureRebuildWarnings() {

        this.automationAvoidRebuildingWarningsCooldown = 0;
    }

    public void incrementRebuildWarningsCooldown() {

        this.automationAvoidRebuildingWarningsCooldown = Mth.clamp(
                this.automationAvoidRebuildingWarningsCooldown + 100,
                0,
                500
        );
    }

    public void decrementRebuildWarningsCooldown() {
        this.automationAvoidRebuildingWarningsCooldown = Math.max(
                0,
                this.automationAvoidRebuildingWarningsCooldown - 1
        );
    }

    public void rebuildProgramAndUpdateDisk() {

        if (world != null && world.isRemote) return;
        var disk = getDisk();
        if (disk == null) {
            this.program = null;
        } else {
            this.incrementRebuildWarningsCooldown();
            this.program = DiskItem.compileAndUpdateErrorsAndWarnings(
                    disk,
                    this,
                    this.shouldRebuildWarnings()
            );
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
    public void openInventory(EntityPlayer player) {
    }

    @Override
    public void closeInventory(EntityPlayer player) {
    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        return stack.getItem() instanceof DiskItem;
    }

    @Override
    public int getField(int id) {

        return 0;
    }

    @Override
    public void setField(int id, int value) {
    }

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

    public Duration[] getTickTimes() {
        // tickTimeNanos is used as a cyclical buffer, transform it to have the first index be the most recent tick
        Duration[] result = new Duration[tickTimes.length];
        System.arraycopy(tickTimes, tickIndex, result, 0, tickTimes.length - tickIndex);
        System.arraycopy(tickTimes, 0, result, tickTimes.length - tickIndex, tickIndex);
        return result;
    }
    public Duration[] getExternalTickTimes() {
        // tickTimeNanos is used as a cyclical buffer, transform it to have the first index be the most recent tick
        Duration[] result = new Duration[externalTickTimes.length];
        System.arraycopy(externalTickTimes, tickIndex, result, 0, externalTickTimes.length - tickIndex);
        System.arraycopy(externalTickTimes, 0, result, externalTickTimes.length - tickIndex, tickIndex);
        return result;
    }

    public void sendUpdatePacket() {
        if (world.isRemote) return;
        // Create one packet and clone it for each receiver
        var managerUpdatePacket = new ClientboundManagerGuiUpdatePacket(
                -1,
                getProgramStringOrEmptyIfNull(),
                getState(),
                getTickTimes(),
                getExternalTickTimes()
        );

        OpenContainerTracker.getOpenManagerMenus(getPos())
                .forEach(entry -> {
                    ManagerContainerMenu menu = entry.getValue();

                    // Send a copy of the manager update packet
                    SFMPackets.sendToPlayer(entry.getKey(), managerUpdatePacket.cloneWithWindowId(menu.windowId));

                    // The rest of the sync is only relevant if the log screen is open
                    if (!menu.isLogScreenOpen) return;

                    // Send log level changes
                    if (!menu.logLevel.equals(logger.getLogLevel().name())) {
                        SFMPackets.sendToPlayer(
                                entry.getKey(), new ClientboundManagerLogLevelUpdatedPacket(
                                        menu.windowId,
                                        logger.getLogLevel().name()
                                )
                        );
                        menu.logLevel = logger.getLogLevel().name();
                    }

                    // Send new logs by determining what logs the player already has
                    SFMEpochInstant hasSince = SFMEpochInstant.zero();
                    if (!menu.logs.isEmpty()) {
                        hasSince = menu.logs.getLast().instant();
                    }
                    var logsToSend = logger.getLogsAfter(hasSince);
                    if (!logsToSend.isEmpty()) {
                        // Add the latest entry to the server copy
                        // since the server copy is only used for checking what the latest log timestamp is
                        menu.logs.add(logsToSend.getLast());

                        // Send the logs
                        while (!logsToSend.isEmpty()) {
                            int remaining = logsToSend.size();
                            SFMPackets.sendToPlayer(
                                    entry.getKey(), ClientboundManagerLogsPacket.drainToCreate(
                                            menu.windowId,
                                            logsToSend
                                    )
                            );
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

//    @Nullable
//    @Override
//    public SPacketUpdateTileEntity getUpdatePacket() {
//        return new SPacketUpdateTileEntity(this.pos, 3, this.getUpdateTag());
//    }

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

    public BlockPos getBlockPos() {
        return this.pos;
    }

    public enum State {
        NO_PROGRAM(
                TextFormatting.RED,
                LocalizationKeys.MANAGER_GUI_STATE_NO_PROGRAM
        ), NO_DISK(
                TextFormatting.RED,
                LocalizationKeys.MANAGER_GUI_STATE_NO_DISK
        ), RUNNING(TextFormatting.GREEN, LocalizationKeys.MANAGER_GUI_STATE_RUNNING), INVALID_PROGRAM(
                TextFormatting.DARK_RED,
                LocalizationKeys.MANAGER_GUI_STATE_INVALID_PROGRAM
        );

        public final TextFormatting COLOR;
        public final LocalizationEntry LOC;

        State(
                TextFormatting color,
                LocalizationEntry loc
        ) {

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

            pReportCategory.addDetail("SFM Reminder", () -> "You can set `server.disableProgramExecution = true` in " + configPath + " to help recover your world.");
        }
        {
            ItemStack disk = getDisk();
            if (disk != null && !disk.isEmpty()) {
                pReportCategory.addDetail("SFM Details", () -> SFMDiagnostics.getDiagnosticsSummary(disk));
            }
        }
    }

    public World getLevel() {
        return this.world;
    }

}