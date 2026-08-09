package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.block_network.CableNetwork;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.containermenu.LibraryContainerMenu;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.SFMContainerUtil;
import ca.teamdman.sfml.ast.Program;
import ca.teamdman.sfml.program_builder.LibraryDefinitions;
import ca.teamdman.sfml.program_builder.LibraryResolver;
import ca.teamdman.sfml.program_builder.ProgramBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Block entity for library blocks that store disks containing SFML definitions.
 * Library blocks can hold multiple disks, and each disk's NAME becomes the importable path.
 * Libraries are auto-discovered on the cable network.
 */
public class LibraryBlockEntity extends BaseContainerBlockEntity {

    @SFMLocalizationDatagen
    public static final LocalizationEntry LIBRARY_CONTAINER = new LocalizationEntry(
            "container.sfm.library",
            "SFML Library"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry PROGRAM_WARNING_UNUSED_LIBRARY = new LocalizationEntry(
            "program.sfm.warnings.unused_library",
            "Library \"%s\" is imported but none of its definitions are used."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry PROGRAM_ERROR_LIBRARY_NOT_FOUND = new LocalizationEntry(
            "program.sfm.error.library_not_found",
            "Library \"%s\" not found in cable network."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry PROGRAM_ERROR_LIBRARY_HAS_ERRORS = new LocalizationEntry(
            "program.sfm.error.library_has_errors",
            "Library \"%s\" has compilation errors."
    );

    /**
     * Reserved label used to identify library blocks in the cable network.
     * This label is auto-registered when the network discovers adjacent library blocks.
     */
    public static final String LIBRARY_LABEL = "sfm:library";

    public static final int DISK_SLOT_COUNT = 10;

    private final NonNullList<ItemStack> items = NonNullList.withSize(DISK_SLOT_COUNT, ItemStack.EMPTY);

    public LibraryBlockEntity(BlockPos pos, BlockState state) {
        super(SFMBlockEntities.LIBRARY_BLOCK_ENTITY.get(), pos, state);
    }

    /**
     * Gets library entries with their slot indices from inserted disks.
     * Disks without a NAME statement are shown with a placeholder name.
     */
    public List<LibraryContainerMenu.LibraryEntry> getLibraryEntries() {
        return LibraryContainerMenu.extractLibraryEntries(this, items.size());
    }

    /**
     * Gets library definitions from a disk with a matching NAME.
     * This overload uses no library resolver (for backward compatibility).
     *
     * @param libraryName The name to search for
     * @return The parsed definitions, or null if not found
     */
    public @Nullable LibraryDefinitions getDefinitionsForLibrary(String libraryName) {
        return getDefinitionsForLibrary(libraryName, LibraryResolver.NONE);
    }

    /**
     * Gets library definitions from a disk with a matching NAME.
     * Supports resolving USE statements within the library disk.
     *
     * @param libraryName The name to search for
     * @param resolver The library resolver for resolving USE statements (handles circular dependency tracking)
     * @return The parsed definitions, or null if not found
     */
    public @Nullable LibraryDefinitions getDefinitionsForLibrary(
            String libraryName,
            LibraryResolver resolver
    ) {
        for (int i = 0; i < DISK_SLOT_COUNT; i++) {
            ItemStack disk = getItem(i);
            if (!DiskItem.isValidDisk(disk)) continue;

            String source = DiskItem.getProgramString(disk);
            String name = DiskItem.extractName(source);

            if (libraryName.equals(name)) {
                return parseLibraryDefinitions(source, resolver);
            }
        }
        return null;
    }

    /**
     * Parses library definitions from source code.
     * Extracts protocols, structs, and macros, including those imported via USE statements.
     *
     * @param source The source code to parse
     * @param resolver The library resolver for resolving USE statements (handles circular dependency tracking)
     * @return The parsed library definitions
     */
    public LibraryDefinitions parseLibraryDefinitions(
            String source,
            LibraryResolver resolver
    ) {
        // Use ProgramBuilder to parse the full program, including USE statements
        var buildResult = new ProgramBuilder(source)
                .withLibraryResolver(resolver)
                .useCache(false)
                .build();

        // Check for errors
        if (!buildResult.metadata().errors().isEmpty()) {
            String errorMsg = buildResult.metadata().errors().stream()
                    .map(e -> {
                        Object[] args = e.getArgs();
                        if (args.length > 0) {
                            return String.valueOf(args[0]);
                        }
                        return e.getKey();
                    })
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("Unknown error");
            throw new IllegalArgumentException(errorMsg);
        }

        Program program = buildResult.program();
        if (program == null) {
            throw new IllegalArgumentException("Failed to parse library definitions");
        }

        // Extract definitions from the parsed program (includes imported definitions)
        return new LibraryDefinitions(
                program.protocolDefinitions(),
                program.structDefinitions(),
                program.macroDefinitions()
        );
    }

    /**
     * Creates a library resolver for use when compiling disks in this library block.
     * First checks disks in this library block, then checks other libraries on the network.
     *
     * @return A library resolver that can find libraries in this block and on the network
     */
    public LibraryResolver createLibraryResolver() {
        if (level == null) {
            // Fallback: only resolve from this library block's disks
            return createLocalLibraryResolver(new HashSet<>());
        }

        // Get or register the cable network at this position
        // LibraryBlock implements ICableBlock, so it IS a cable
        // This ensures network discovery happens even after world reload
        Optional<CableNetwork> networkOpt = CableNetworkManager
                .getOrRegisterNetworkFromCablePosition(level, worldPosition);

        if (networkOpt.isEmpty()) {
            // Not connected to network: only resolve from this library block's disks
            return createLocalLibraryResolver(new HashSet<>());
        }

        // Connected to network: use the network's resolver (which includes all library blocks)
        return networkOpt.get().createLibraryResolver();
    }

    /**
     * Creates a library resolver that only resolves from this library block's disks.
     * Used when not connected to a cable network.
     *
     * @param librariesBeingResolved Shared set for tracking circular dependencies
     * @return A library resolver for this block only
     */
    private LibraryResolver createLocalLibraryResolver(Set<String> librariesBeingResolved) {
        return libraryName -> {
            // Check for circular dependency
            if (librariesBeingResolved.contains(libraryName)) {
                throw new IllegalArgumentException("Circular library dependency detected: " + libraryName);
            }

            librariesBeingResolved.add(libraryName);
            try {
                // Create a nested resolver for any USE statements in the resolved library
                LibraryResolver nestedResolver = createLocalLibraryResolver(librariesBeingResolved);
                LibraryDefinitions defs = getDefinitionsForLibrary(libraryName, nestedResolver);
                return Optional.ofNullable(defs);
            } finally {
                librariesBeingResolved.remove(libraryName);
            }
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        // Backward compatibility: migrate old source code format to disk
        if (tag.contains("Source")) {
            String legacySource = tag.getString("Source");
            if (!legacySource.isEmpty()) {
                ItemStack disk = new ItemStack(SFMItems.DISK.get());
                DiskItem.setProgram(disk, legacySource);
                items.set(0, disk);
            }
        } else {
            ContainerHelper.loadAllItems(tag, items);
        }
    }

    @Override
    protected Component getDefaultName() {
        return LIBRARY_CONTAINER.getComponent();
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new LibraryContainerMenu(containerId, inventory, this);
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack item : items) {
            if (!item.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < 0 || slot >= items.size()) return ItemStack.EMPTY;
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = ContainerHelper.takeItem(items, slot);
        setChanged();
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= items.size()) return;
        items.set(slot, stack);
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
    public void clearContent() {
        items.clear();
    }

    // Client-side cache of disk mask for rendering
    private int clientDiskMask = 0;
    private int clientErrorMask = 0;
    private int clientWarningMask = 0;

    @Override
    public void setChanged() {
        super.setChanged();
        // Sync to client for renderer updates
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            // Notify cable networks that library configuration may have changed
            notifyNetworkLibraryChanged();
        }
    }

    /**
     * Notifies any cable networks adjacent to this library block that the library
     * configuration has changed, causing managers and other library disks to recompile.
     * If not connected to any network, recompiles local disks directly.
     */
    private void notifyNetworkLibraryChanged() {
        if (level == null || level.isClientSide()) return;

        // Get or register the cable network at this position
        // LibraryBlock implements ICableBlock, so it IS a cable
        // This ensures network discovery happens even after world reload
        Optional<CableNetwork> networkOpt = CableNetworkManager
                .getOrRegisterNetworkFromCablePosition(level, worldPosition);

        if (networkOpt.isPresent()) {
            // On a network: notify dependents to recompile
            networkOpt.get().invalidateAutoLabelsAndNotifyDependents();
        } else {
            // Not on a network: recompile local disks to update errors
            // (e.g., resolved circular dependencies)
            recompileAllDisks();
        }
    }

    /**
     * Recompiles all disks in this library block.
     * Called when a library on the network changes to update circular dependency errors.
     */
    public void recompileAllDisks() {
        if (level == null || level.isClientSide()) return;

        var resolver = createLibraryResolver();
        for (int i = 0; i < DISK_SLOT_COUNT; i++) {
            ItemStack disk = getItem(i);
            if (!DiskItem.isValidDisk(disk)) continue;

            DiskItem.compileAndUpdateErrorsAndWarnings(disk, null, true, resolver);
        }

        // Sync to client for renderer updates (without triggering network notification)
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    // Client sync methods for BlockEntityRenderer
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putInt("DiskMask", computeDiskMask());
        tag.putInt("ErrorMask", computeErrorMask());
        tag.putInt("WarningMask", computeWarningMask());
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        clientDiskMask = tag.getInt("DiskMask");
        clientErrorMask = tag.getInt("ErrorMask");
        clientWarningMask = tag.getInt("WarningMask");
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            clientDiskMask = tag.getInt("DiskMask");
            clientErrorMask = tag.getInt("ErrorMask");
            clientWarningMask = tag.getInt("WarningMask");
        }
    }

    /**
     * Computes a bitmask indicating which slots have disks (server-side).
     */
    private int computeDiskMask() {
        int mask = 0;
        for (int i = 0; i < items.size(); i++) {
            if (DiskItem.isValidDisk(items.get(i))) {
                mask |= (1 << i);
            }
        }
        return mask;
    }

    /**
     * Computes a bitmask indicating which slots have disks with errors (server-side).
     */
    private int computeErrorMask() {
        int mask = 0;
        for (int i = 0; i < items.size(); i++) {
            ItemStack disk = items.get(i);
            if (DiskItem.isValidDisk(disk) && !DiskItem.getErrors(disk).isEmpty()) {
                mask |= (1 << i);
            }
        }
        return mask;
    }

    /**
     * Computes a bitmask indicating which slots have disks with warnings (server-side).
     */
    private int computeWarningMask() {
        int mask = 0;
        for (int i = 0; i < items.size(); i++) {
            ItemStack disk = items.get(i);
            if (DiskItem.isValidDisk(disk) && !DiskItem.getWarnings(disk).isEmpty()) {
                mask |= (1 << i);
            }
        }
        return mask;
    }

    /**
     * Returns a bitmask indicating which slots have disks.
     * Bit 0 = slot 0, bit 1 = slot 1, etc.
     * Used by the BlockEntityRenderer to show disk indicators.
     */
    public int getDiskSlotMask() {
        if (level != null && level.isClientSide()) {
            return clientDiskMask;
        }
        return computeDiskMask();
    }

    /**
     * Returns a bitmask indicating which slots have disks with errors.
     * Bit 0 = slot 0, bit 1 = slot 1, etc.
     * Used by the BlockEntityRenderer to show error indicators.
     */
    public int getErrorSlotMask() {
        if (level != null && level.isClientSide()) {
            return clientErrorMask;
        }
        return computeErrorMask();
    }

    /**
     * Returns a bitmask indicating which slots have disks with warnings.
     * Bit 0 = slot 0, bit 1 = slot 1, etc.
     * Used by the BlockEntityRenderer to show warning indicators.
     */
    public int getWarningSlotMask() {
        if (level != null && level.isClientSide()) {
            return clientWarningMask;
        }
        return computeWarningMask();
    }
}
