package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.menu.ManagerMenu;
import ca.teamdman.sfm.common.program.ProgramExecutor;
import ca.teamdman.sfm.common.registry.SFMBlockEntities;
import ca.teamdman.sfm.common.util.SFMContainerUtil;
import ca.teamdman.sfml.SFMLLexer;
import ca.teamdman.sfml.SFMLParser;
import ca.teamdman.sfml.ast.ASTBuilder;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.ChatFormatting;
import net.minecraft.ResourceLocationException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.antlr.v4.runtime.*;

import java.util.*;

public class ManagerBlockEntity extends BaseContainerBlockEntity {
    public static final int                    STATE_DATA_ACCESS_KEY = 0;
    private final       NonNullList<ItemStack> ITEMS                 = NonNullList.withSize(1, ItemStack.EMPTY);
    private             ProgramExecutor        compiledProgram       = null;
    private final       ContainerData          DATA_ACCESS           = new ContainerData() {
        @Override
        public int get(int key) {
            return switch (key) {
                case STATE_DATA_ACCESS_KEY -> ManagerBlockEntity.this
                        .getState()
                        .ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int key, int val) {
        }

        @Override
        public int getCount() {
            return 1;
        }
    };
    private             int                    unprocessedRedstonePulses = 0; // used by redstone trigger

    public void trackRedstonePulseUnprocessed() {
        unprocessedRedstonePulses++;
    }

    public void clearRedstonePulseQueue() {
        unprocessedRedstonePulses = 0;
    }

    public int getUnprocessedRedstonePulseCount() {
        return unprocessedRedstonePulses;
    }

    public ManagerBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(SFMBlockEntities.MANAGER_BLOCK_ENTITY.get(), blockPos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ManagerBlockEntity tile) {
        if (tile.compiledProgram != null) {
            tile.compiledProgram.tick();
        }
    }

    public State getState() {
        if (getDisk().isEmpty()) return State.NO_DISK;
        if (getProgram().isEmpty()) return State.NO_PROGRAM;
        if (compiledProgram == null) return State.INVALID_PROGRAM;
        return State.RUNNING;
    }

    public Optional<String> getProgram() {
        return getDisk()
                .map(DiskItem::getProgram)
                .filter(prog -> !prog.isBlank());
    }

    public Set<String> getReferencedLabels() {
        if (compiledProgram == null) return Collections.emptySet();
        return compiledProgram.getReferencedLabels();
    }

    public void setProgram(String program) {
        getDisk().ifPresent(disk -> {
            DiskItem.setProgram(disk, program);
            compileProgram();
            setChanged();
        });
    }

    public Optional<ItemStack> getDisk() {
        var item = getItem(0);
        if (item.getItem() instanceof DiskItem) return Optional.of(item);
        return Optional.empty();
    }

    private void compileProgram() {
        compiledProgram = null;
        if (getProgram().isEmpty()) return;
        var disk    = getDisk().get();
        var program = getProgram().get();
        var lexer   = new SFMLLexer(CharStreams.fromString(program));
        lexer.removeErrorListeners();
        var tokens = new CommonTokenStream(lexer);
        var parser = new SFMLParser(tokens);

        parser.removeErrorListeners();
        List<String> errors = new ArrayList<>();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(
                    Recognizer<?, ?> recognizer,
                    Object offendingSymbol,
                    int line,
                    int charPositionInLine,
                    String msg,
                    RecognitionException e
            ) {
                errors.add("line " + line + ":" + charPositionInLine + " " + msg);
            }
        });

        var     context    = parser.program();
        Program programAST = null;
        try {
            programAST = new ASTBuilder().visitProgram(context);
            DiskItem.setProgramName(disk, programAST.name());
            programAST.addWarnings(disk);
        } catch (ResourceLocationException | IllegalArgumentException e) {
            errors.add(e.getMessage());
        } catch (Throwable t) {
            t.printStackTrace();
        }
        if (errors.isEmpty()) {
            compiledProgram = new ProgramExecutor(programAST, this);
        }

        DiskItem.setErrors(disk, errors);
    }

    @Override
    protected Component getDefaultName() {
        return new TranslatableComponent("container.sfm.manager");
    }

    @Override
    protected AbstractContainerMenu createMenu(int windowId, Inventory inv) {
        return new ManagerMenu(windowId, inv, this, getBlockPos(), this.DATA_ACCESS, getProgram().orElse(""));
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
        if (slot == 0) compileProgram();
        setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        var result = ContainerHelper.takeItem(ITEMS, slot);
        if (slot == 0) compileProgram();
        setChanged();
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= ITEMS.size()) return;
        ITEMS.set(slot, stack);
        if (slot == 0) compileProgram();
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
        compileProgram();
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

    public enum State {
        NO_PROGRAM(ChatFormatting.RED),
        NO_DISK(ChatFormatting.RED),
        RUNNING(ChatFormatting.GREEN),
        INVALID_PROGRAM(ChatFormatting.DARK_RED);

        public final ChatFormatting COLOR;

        State(ChatFormatting color) {
            COLOR = color;
        }
    }

}
