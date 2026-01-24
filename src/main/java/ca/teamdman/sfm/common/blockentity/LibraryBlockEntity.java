package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.registry.SFMBlockEntities;
import ca.teamdman.sfml.program_builder.LibraryDefinitions;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Block entity for library blocks that store SFML definitions (protocols, structs, macros).
 * Library blocks can be referenced by manager programs to share common definitions.
 */
public class LibraryBlockEntity extends BlockEntity implements MenuProvider {

    private static final String NBT_KEY_SOURCE = "Source";

    /**
     * The raw SFML source code containing the library definitions.
     */
    private String sourceCode = "";

    /**
     * Cached parsed definitions. Null if not yet parsed or if source is invalid.
     */
    @Nullable
    private LibraryDefinitions cachedDefinitions = null;

    /**
     * Compilation errors, empty if compilation succeeded.
     */
    private List<String> compilationErrors = new ArrayList<>();

    public LibraryBlockEntity(BlockPos pos, BlockState state) {
        super(SFMBlockEntities.LIBRARY_BLOCK_ENTITY.get(), pos, state);
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
        this.cachedDefinitions = null;
        this.compilationErrors.clear();
        setChanged();
        recompile();
    }

    public List<String> getCompilationErrors() {
        return compilationErrors;
    }

    public boolean hasErrors() {
        return !compilationErrors.isEmpty();
    }

    /**
     * Gets the library definitions, recompiling if necessary.
     */
    public LibraryDefinitions getDefinitions() {
        if (cachedDefinitions == null) {
            recompile();
        }
        return cachedDefinitions != null ? cachedDefinitions : LibraryDefinitions.EMPTY;
    }

    /**
     * Recompiles the source code to extract definitions.
     */
    private void recompile() {
        compilationErrors.clear();

        if (sourceCode.isEmpty()) {
            cachedDefinitions = LibraryDefinitions.EMPTY;
            return;
        }

        try {
            // Parse the source code as a library (only protocols, structs, macros)
            // For now, we'll use a simplified approach - full parsing would need ASTBuilder
            // This is a placeholder implementation
            cachedDefinitions = parseLibraryDefinitions(sourceCode);
        } catch (Exception e) {
            compilationErrors.add(e.getMessage());
            cachedDefinitions = null;
        }
    }

    /**
     * Parses library definitions from source code.
     * TODO: Implement actual parsing using ASTBuilder to extract protocols, structs, and macros.
     * This requires refactoring ASTBuilder to support partial program parsing.
     */
    private LibraryDefinitions parseLibraryDefinitions(String source) {
        // Stub implementation - library block parsing not yet implemented
        return LibraryDefinitions.EMPTY;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString(NBT_KEY_SOURCE, sourceCode);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        sourceCode = tag.getString(NBT_KEY_SOURCE);
        cachedDefinitions = null;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.sfm.library");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        // Library blocks don't have a GUI menu yet - could be added later for editing
        return null;
    }
}
