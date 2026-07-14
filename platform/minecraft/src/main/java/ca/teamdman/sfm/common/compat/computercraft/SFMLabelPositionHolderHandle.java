package ca.teamdman.sfm.common.compat.computercraft;

import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.util.BlockPosSet;
import dan200.computercraft.api.lua.LuaFunction;
import net.minecraft.core.BlockPos;

import java.util.Comparator;
import java.util.List;

/**
 * An owned, mutable Lua editor for an SFM label position holder.
 *
 * <p>The editor is intentionally a snapshot. Reads and edits never materialise the full label
 * map in Lua; {@link #save()} explicitly replaces the source item with this owned state.</p>
 */
public final class SFMLabelPositionHolderHandle {
    private final Loader loader;
    private final Saver saver;
    private LabelPositionHolder labels;
    private String loadError;

    SFMLabelPositionHolderHandle(
            Loader loader,
            Saver saver
    ) {

        this.loader = loader;
        this.saver = saver;
    }

    @LuaFunction(mainThread = true)
    public final int labelCount() {

        return ensureLoaded() ? sortedLabelNames().size() : 0;
    }

    @LuaFunction(mainThread = true)
    public final String labelName(int index) {

        if (!ensureLoaded()) {
            return null;
        }
        List<String> names = sortedLabelNames();
        return index < 1 || index > names.size() ? null : names.get(index - 1);
    }

    @LuaFunction(mainThread = true)
    public final int positionCount(String label) {

        return ensureLoaded() ? labels.getPositions(label).size() : 0;
    }

    @LuaFunction(mainThread = true)
    public final Object[] position(
            String label,
            int index
    ) {

        if (!ensureLoaded()) {
            return SFMComputerCraftResults.unavailable(loadError);
        }
        List<BlockPos> positions = sortedPositions(label);
        if (index < 1 || index > positions.size()) {
            return new Object[]{null};
        }
        BlockPos position = positions.get(index - 1);
        return new Object[]{position.getX(), position.getY(), position.getZ()};
    }

    @LuaFunction(mainThread = true)
    public final boolean contains(
            String label,
            int x,
            int y,
            int z
    ) {

        return ensureLoaded() && labels.contains(label, new BlockPos(x, y, z));
    }

    @LuaFunction(mainThread = true)
    public final Object[] add(
            String label,
            int x,
            int y,
            int z
    ) {

        if (!ensureLoaded()) {
            return SFMComputerCraftResults.failure(loadError);
        }
        if (!isValidLabel(label)) {
            return SFMComputerCraftResults.failure("invalid_label");
        }
        labels.add(label, new BlockPos(x, y, z));
        return SFMComputerCraftResults.success();
    }

    @LuaFunction(mainThread = true)
    public final Object[] remove(
            String label,
            int x,
            int y,
            int z
    ) {

        if (!ensureLoaded()) {
            return SFMComputerCraftResults.failure(loadError);
        }
        if (!isValidLabel(label)) {
            return SFMComputerCraftResults.failure("invalid_label");
        }
        labels.getPositions(label).remove(new BlockPos(x, y, z));
        return SFMComputerCraftResults.success();
    }

    @LuaFunction(mainThread = true)
    public final Object[] removeLabel(String label) {

        if (!ensureLoaded()) {
            return SFMComputerCraftResults.failure(loadError);
        }
        if (!isValidLabel(label)) {
            return SFMComputerCraftResults.failure("invalid_label");
        }
        labels.labels().remove(label);
        return SFMComputerCraftResults.success();
    }

    @LuaFunction(mainThread = true)
    public final Object[] clear() {

        if (!ensureLoaded()) {
            return SFMComputerCraftResults.failure(loadError);
        }
        labels.clear();
        return SFMComputerCraftResults.success();
    }

    @LuaFunction(mainThread = true)
    public final Object[] save() {

        if (!ensureLoaded()) {
            return SFMComputerCraftResults.failure(loadError);
        }
        SFMItemHandleTarget.Resolution resolution = saver.save(labels.toOwned());
        if (!resolution.isResolved()) {
            return SFMComputerCraftResults.failure(resolution.errorCode());
        }
        return SFMComputerCraftResults.success();
    }

    private List<String> sortedLabelNames() {

        return labels.labels().keySet().stream().sorted().toList();
    }

    private List<BlockPos> sortedPositions(String label) {

        BlockPosSet positions = labels.getPositions(label);
        return positions
                .blockPosIterator()
                .stream()
                .map(BlockPos::immutable)
                .sorted(Comparator.comparingLong(BlockPos::asLong))
                .toList();
    }

    static boolean isValidLabel(String label) {

        return !label.isBlank() && label.length() <= 256;
    }

    @FunctionalInterface
    interface Loader {
        SFMItemHandleTarget.Resolution load();
    }

    private boolean ensureLoaded() {

        if (labels != null) {
            return true;
        }
        SFMItemHandleTarget.Resolution resolution = loader.load();
        if (!resolution.isResolved()) {
            loadError = resolution.errorCode();
            return false;
        }
        labels = LabelPositionHolder.from(resolution.stack()).toOwned();
        return true;
    }

    @FunctionalInterface
    interface Saver {
        SFMItemHandleTarget.Resolution save(LabelPositionHolder labels);
    }
}
