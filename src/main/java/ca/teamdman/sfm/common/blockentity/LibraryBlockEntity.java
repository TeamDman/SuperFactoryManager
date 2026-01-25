package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.langs.SFMLLexer;
import ca.teamdman.langs.SFMLParser;
import ca.teamdman.sfm.common.cablenetwork.CableNetwork;
import ca.teamdman.sfm.common.cablenetwork.CableNetworkManager;
import ca.teamdman.sfm.common.containermenu.LibraryContainerMenu;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.registry.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.common.util.SFMContainerUtil;
import ca.teamdman.sfml.ast.ASTBuilder;
import ca.teamdman.sfml.ast.MacroDefinition;
import ca.teamdman.sfml.ast.Program;
import ca.teamdman.sfml.ast.ProtocolDefinition;
import ca.teamdman.sfml.ast.StructDefinition;
import ca.teamdman.sfml.program_builder.LibraryDefinitions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Block entity for library blocks that store disks containing SFML definitions.
 * Library blocks can hold multiple disks, and each disk's NAME becomes the importable path.
 * Libraries are auto-discovered on the cable network.
 */
public class LibraryBlockEntity extends BaseContainerBlockEntity {

    /**
     * Reserved label used to identify library blocks in the cable network.
     * This label is auto-registered when the network discovers adjacent library blocks.
     */
    public static final String LIBRARY_LABEL = "sfm:library";

    public static final int DISK_SLOT_COUNT = 10;

    private final NonNullList<ItemStack> ITEMS = NonNullList.withSize(DISK_SLOT_COUNT, ItemStack.EMPTY);

    public LibraryBlockEntity(BlockPos pos, BlockState state) {
        super(SFMBlockEntities.LIBRARY_BLOCK_ENTITY.get(), pos, state);
    }

    /**
     * Gets library entries with their slot indices from inserted disks.
     * Disks without a NAME statement are shown with a placeholder name.
     */
    public List<LibraryContainerMenu.LibraryEntry> getLibraryEntries() {
        return LibraryContainerMenu.extractLibraryEntries(this, DISK_SLOT_COUNT);
    }

    /**
     * Gets library definitions from a disk with a matching NAME.
     *
     * @param libraryName The name to search for
     * @return The parsed definitions, or null if not found
     */
    public @Nullable LibraryDefinitions getDefinitionsForLibrary(String libraryName) {
        for (int i = 0; i < DISK_SLOT_COUNT; i++) {
            ItemStack disk = getItem(i);
            if (!DiskItem.isValidDisk(disk)) continue;

            String source = DiskItem.getProgramString(disk);
            String name = DiskItem.extractName(source);

            if (libraryName.equals(name)) {
                return parseLibraryDefinitions(source);
            }
        }
        return null;
    }

    /**
     * Parses library definitions from source code.
     * Extracts only protocols, structs, and macros (ignores triggers/let statements).
     */
    public LibraryDefinitions parseLibraryDefinitions(String source) {
        SFMLLexer lexer = new SFMLLexer(CharStreams.fromString(source));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        SFMLParser parser = new SFMLParser(tokens);

        // Set up error capturing
        List<String> errors = new ArrayList<>();
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        Program.ListErrorListener listener = new Program.ListErrorListener(errors);
        lexer.addErrorListener(listener);
        parser.addErrorListener(listener);

        SFMLParser.ProgramContext context = parser.program();
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(", ", errors));
        }

        // Use ASTBuilder to parse definitions
        ASTBuilder builder = new ASTBuilder();

        // Parse protocol definitions
        List<ProtocolDefinition> protocols = new ArrayList<>();
        for (SFMLParser.ProtocolDefinitionContext protoCtx : context.protocolDefinition()) {
            protocols.add(builder.visitProtocolDefinition(protoCtx));
        }

        // Parse struct definitions
        List<StructDefinition> structs = new ArrayList<>();
        for (SFMLParser.StructDefinitionContext structCtx : context.structDefinition()) {
            structs.add(builder.visitStructDefinition(structCtx));
        }

        // Parse macro definitions
        List<MacroDefinition> macros = new ArrayList<>();
        for (SFMLParser.MacroDefinitionContext macroCtx : context.macroDefinition()) {
            macros.add(builder.visitMacroDefinition(macroCtx));
        }

        return new LibraryDefinitions(protocols, structs, macros);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, ITEMS);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        // Backward compatibility: migrate old source code format to disk
        if (tag.contains("Source")) {
            String legacySource = tag.getString("Source");
            if (!legacySource.isEmpty()) {
                ItemStack disk = new ItemStack(SFMItems.DISK_ITEM.get());
                DiskItem.setProgram(disk, legacySource);
                ITEMS.set(0, disk);
            }
        } else {
            ContainerHelper.loadAllItems(tag, ITEMS);
        }
    }

    @Override
    protected Component getDefaultName() {
        return LocalizationKeys.LIBRARY_CONTAINER.getComponent();
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new LibraryContainerMenu(containerId, inventory, this);
    }

    @Override
    public int getContainerSize() {
        return ITEMS.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack item : ITEMS) {
            if (!item.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < 0 || slot >= ITEMS.size()) return ItemStack.EMPTY;
        return ITEMS.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(ITEMS, slot, amount);
        setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = ContainerHelper.takeItem(ITEMS, slot);
        setChanged();
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= ITEMS.size()) return;
        ITEMS.set(slot, stack);
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
        ITEMS.clear();
    }

    // Client-side cache of disk mask for rendering
    private int clientDiskMask = 0;

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
     * configuration has changed, causing managers to re-validate their programs.
     */
    private void notifyNetworkLibraryChanged() {
        if (level == null || level.isClientSide()) return;

        // Find networks adjacent to this library block and notify them
        CableNetworkManager.getNetworksForLevel(level)
                .filter(network -> network.isAdjacentToCable(worldPosition))
                .forEach(CableNetwork::invalidateAutoLabelsAndNotifyManagers);
    }

    // Client sync methods for BlockEntityRenderer
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putInt("DiskMask", computeDiskMask());
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        clientDiskMask = tag.getInt("DiskMask");
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
        }
    }

    /**
     * Computes a bitmask indicating which slots have disks (server-side).
     */
    private int computeDiskMask() {
        int mask = 0;
        for (int i = 0; i < ITEMS.size(); i++) {
            if (DiskItem.isValidDisk(ITEMS.get(i))) {
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
}
