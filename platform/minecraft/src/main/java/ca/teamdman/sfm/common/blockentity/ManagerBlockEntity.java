package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.config.SFMConfigTracker;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.diagnostics.SFMDiagnostics;
import ca.teamdman.sfm.common.handler.OpenContainerTracker;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.logging.TranslatableLogger;
import ca.teamdman.sfm.common.net.ClientboundManagerGuiUpdatePacket;
import ca.teamdman.sfm.common.net.ClientboundManagerLogLevelUpdatedPacket;
import ca.teamdman.sfm.common.net.ClientboundManagerLogsPacket;
import ca.teamdman.sfm.common.program.IProgramHooks;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.timing.SFMEpochInstant;
import ca.teamdman.sfm.common.timing.SFMInstant;
import ca.teamdman.sfm.common.util.SFMContainerUtil;
import ca.teamdman.sfml.ast.Program;
import com.google.common.base.Joiner;
import net.minecraft.ChatFormatting;
import net.minecraft.CrashReportCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ManagerBlockEntity extends BaseContainerBlockEntity {
    public static final int TICK_TIME_HISTORY_SIZE = 20;

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_CONTAINER = new LocalizationEntry(
            "container.sfm.manager",
            "Factory Manager"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry LOG_LEVEL_UPDATED = new LocalizationEntry(
            "log.sfm.level_updated",
            "Log level updated to %s"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_GUI_STATE_INVALID_PROGRAM = new LocalizationEntry(
            "gui.sfm.manager.state.invalid_program",
            "invalid program"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_GUI_STATE_RUNNING = new LocalizationEntry(
            "gui.sfm.manager.state.running",
            "running"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_GUI_STATE_NO_DISK = new LocalizationEntry(
            "gui.sfm.manager.state.no_disk",
            "missing disk"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_GUI_STATE_NO_PROGRAM = new LocalizationEntry(
            "gui.sfm.manager.state.no_program",
            "no program"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry PROGRAM_TICK_TIME_MS = new LocalizationEntry(
            "program.sfm.tick.time",
            "Program tick took %.2f ms"
    );

    public final TranslatableLogger logger;

    private final NonNullList<ItemStack> ITEMS = NonNullList.withSize(1, ItemStack.EMPTY);

    private final Duration[] tickTimes = new Duration[TICK_TIME_HISTORY_SIZE];

    public IItemHandler invWrapper = new InvWrapper(this);

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
            BlockPos blockPos,
            BlockState blockState
    ) {

        this(SFMBlockEntities.MANAGER.get(), blockPos, blockState);
    }

    public ManagerBlockEntity(
            BlockEntityType<?> pType,
            BlockPos blockPos,
            BlockState blockState
    ) {

        super(pType, blockPos, blockState);
        // Logger name should be unique to (isClient,managerpos)
        // We can't check isClient here, so instead to guarantee uniqueness we can just use hash
        // This is necessary because setLogLevel in game tests will get clobbered when the client constructs the block entity
        // so the name must be unique so that the client default logger construction doesn't overwrite changes to the server logger
        String loggerName = SFM.MOD_ID
                            + ":manager@"
                            + blockPos.toShortString() + "@" + Integer.toHexString(System.identityHashCode(this));
        logger = new TranslatableLogger(loggerName);
    }

    @Override
    public String toString() {

        return "ManagerBlockEntity{" +
               "hasDisk=" + (getDisk() != null) +
               ", pos=" + getBlockPos() +
               ", level=" + getLevel() +
               '}';
    }

    public void addProgramHooks(IProgramHooks hooks) {

        if (this.programHooks == null) {
            this.programHooks = new ArrayList<>();
        }
        this.programHooks.add(hooks);
    }

    public static void serverTick(
            @SuppressWarnings("unused") Level level,
            @SuppressWarnings("unused") BlockPos pos,
            @SuppressWarnings("unused") BlockState state,
            ManagerBlockEntity manager
    ) {

        try {
            // Get timestamp for elapsed time calculations
            SFMInstant start = SFMInstant.now();

            // Update tick counters
            manager.tick++;
            manager.decrementRebuildWarningsCooldown();

            // If config changed, mark dirty
            if (manager.configRevision != SFMConfig.SERVER_CONFIG.getRevision()) {
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
            boolean didSomething = manager.program.tick(manager);
            if (!didSomething) {
                return;
            }

            // Calculate and track the elapsed time
            Duration elapsed = start.elapsed();
            manager.tickTimes[manager.tickIndex] = elapsed;
            manager.tickIndex = (manager.tickIndex + 1) % manager.tickTimes.length;
            manager.logger.trace(x -> x.accept(
                    PROGRAM_TICK_TIME_MS.get(elapsed.toNanos() / 1_000_000f)));

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
                manager.logger.info(x -> x.accept(LOG_LEVEL_UPDATED.get(newLogLevel)));
                var oldLogLevel = manager.logger.getLogLevel();
                manager.setLogLevel(newLogLevel);
                SFM.LOGGER.debug(
                        "SFM updated manager {} {} log level to {} after a single execution at {} level",
                        manager.getBlockPos(),
                        manager.getLevel(),
                        newLogLevel,
                        oldLogLevel
                );
            }
        } catch (Throwable t) {
            // Inform the user that they can disable the manager in the config
            String configPath;
            var found = SFMConfigTracker.getPathForConfig(SFMConfig.SERVER_CONFIG_SPEC);
            if (found != null) {
                configPath = found.toString();
            } else {
                configPath = "sfm-server.toml";
            }
            String configValuePath = Joiner.on(".").join(SFMConfig.SERVER_CONFIG.disableProgramExecution.getPath());
            SFM.LOGGER.fatal(
                    "SFM detected a problem while ticking a manager. You can set `{} = true` in {} to help recover your world.",
                    configValuePath,
                    configPath
            );
            throw t;
        }
    }

    @Override
    public void fillCrashReportCategory(CrashReportCategory pReportCategory) {

        super.fillCrashReportCategory(pReportCategory);
        {
            String configPath;
            var found = SFMConfigTracker.getPathForConfig(SFMConfig.SERVER_CONFIG_SPEC);
            if (found != null) {
                configPath = found.toString();
            } else {
                configPath = "sfm-server.toml";
            }
            String configValuePath = Joiner.on(".").join(SFMConfig.SERVER_CONFIG.disableProgramExecution.getPath());
            pReportCategory.setDetail(
                    "SFM Reminder",
                    "You can set `"
                    + configValuePath
                    + " = true` in "
                    + configPath
                    + " to help recover your world."
            );
        }
        {
            ItemStack disk = getDisk();
            if (disk != null && !disk.isEmpty()) {
                pReportCategory.setDetail("SFM Details", SFMDiagnostics.getDiagnosticsSummary(disk));
            }
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

            // always rebuild warnings when modifying program string
            this.ensureRebuildWarnings();

            DiskItem.setProgram(disk, program.stripTrailing().stripIndent());
            rebuildProgramAndUpdateDisk();
            setChanged();
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

    /**
     * Computes manager state without creating NBT on a blank disk.
     */
    public State getStateReadOnly() {

        var disk = getDisk();
        if (disk == null) return State.NO_DISK;
        if (DiskItem.getProgramStringReadOnly(disk).isBlank()) return State.NO_PROGRAM;
        if (program == null) return State.INVALID_PROGRAM;
        return State.RUNNING;
    }

    public @Nullable String getProgramString() {

        var disk = getDisk();
        if (disk == null) {
            return null;
        }

        var program = DiskItem.getProgramString(disk);
        return program.isBlank() ? null : program;
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
        var item = getItem(0);
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

        if (level != null && level.isClientSide()) return;
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
        this.configRevision = SFMConfig.SERVER_CONFIG.getRevision();
        sendUpdatePacket();
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
    public ItemStack removeItem(
            int slot,
            int amount
    ) {

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
    public void setItem(
            int slot,
            ItemStack stack
    ) {

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
    public boolean canPlaceItem(
            int slot,
            ItemStack stack
    ) {

        return stack.getItem() instanceof DiskItem;
    }

    @Override
    public boolean stillValid(Player player) {

        return SFMContainerUtil.stillValid(this, player);
    }

    @Override
    public void clearContent() {

        ITEMS.clear();
    }

    public void reset() {

        var disk = getDisk();
        if (disk != null) {
            LabelPositionHolder.clear(disk);
            DiskItem.clearData(disk);
            setItem(0, disk);
            setChanged();
        }
    }

    public Duration[] getTickTimes() {
        // tickTimeNanos is used as a cyclical buffer, transform it to have the first index be the most recent tick
        Duration[] result = new Duration[tickTimes.length];
        System.arraycopy(tickTimes, tickIndex, result, 0, tickTimes.length - tickIndex);
        System.arraycopy(tickTimes, 0, result, tickTimes.length - tickIndex, tickIndex);
        return result;
    }

    public void sendUpdatePacket() {
        // Create one packet and clone it for each receiver
        var managerUpdatePacket = new ClientboundManagerGuiUpdatePacket(
                -1,
                getProgramStringOrEmptyIfNull(),
                getState(),
                getTickTimes()
        );

        OpenContainerTracker.getOpenManagerMenus(getBlockPos())
                .forEach(entry -> {
                    ManagerContainerMenu menu = entry.getValue();

                    // Send a copy of the manager update packet
                    SFMPackets.sendToPlayer(entry::getKey, managerUpdatePacket.cloneWithWindowId(menu.containerId));

                    // The rest of the sync is only relevant if the log screen is open
                    if (!menu.isLogScreenOpen) return;

                    // Send log level changes
                    if (!menu.logLevel.equals(logger.getLogLevel().name())) {
                        SFMPackets.sendToPlayer(
                                entry::getKey, new ClientboundManagerLogLevelUpdatedPacket(
                                        menu.containerId,
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
                                    entry::getKey, ClientboundManagerLogsPacket.drainToCreate(
                                            menu.containerId,
                                            logsToSend
                                    )
                            );
                            if (logsToSend.size() >= remaining) {
                                throw new IllegalStateException("Failed to send logs, infinite loop detected");
                            }
                        }
                    }
                });
    }

    @Override
    protected NonNullList<ItemStack> getItems() {

        return ITEMS;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> pItems) {

        ITEMS.clear();
        ITEMS.addAll(pItems);
    }

    @Override
    protected void saveAdditional(
            CompoundTag pTag,
            HolderLookup.Provider pRegistries
    ) {

        super.saveAdditional(pTag, pRegistries);
        ContainerHelper.saveAllItems(pTag, ITEMS, pRegistries);
    }

    @Override
    protected void loadAdditional(
            CompoundTag pTag,
            HolderLookup.Provider pRegistries
    ) {

        super.loadAdditional(pTag, pRegistries);
        ContainerHelper.loadAllItems(pTag, ITEMS, pRegistries);
        this.shouldRebuildProgram = true;
        if (level != null) {
            this.tick = level.random.nextInt();
        }
    }

    @Override
    protected Component getDefaultName() {

        return MANAGER_CONTAINER.getComponent();
    }


    @Override
    protected AbstractContainerMenu createMenu(
            int windowId,
            Inventory inv
    ) {

        return new ManagerContainerMenu(windowId, inv, this);
    }


    public enum State {
        NO_PROGRAM(
                ChatFormatting.RED,
                MANAGER_GUI_STATE_NO_PROGRAM
        ), NO_DISK(
                ChatFormatting.RED,
                MANAGER_GUI_STATE_NO_DISK
        ), RUNNING(ChatFormatting.GREEN, MANAGER_GUI_STATE_RUNNING), INVALID_PROGRAM(
                ChatFormatting.DARK_RED,
                MANAGER_GUI_STATE_INVALID_PROGRAM
        );

        public final ChatFormatting COLOR;

        public final LocalizationEntry LOC;

        State(
                ChatFormatting color,
                LocalizationEntry loc
        ) {

            COLOR = color;
            LOC = loc;
        }
    }

}
