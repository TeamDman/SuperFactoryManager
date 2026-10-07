package ca.teamdman.sfm.common.compat.computercraft;

import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfml.ast.Program;
import dan200.computercraft.api.lua.LuaFunction;
import net.minecraft.world.item.ItemStack;

/** A mutable Lua handle for one specific SFM program disk item stack. */
public final class SFMDiskHandle {
    private final SFMItemHandleTarget target;

    SFMDiskHandle(SFMItemHandleTarget target) {

        this.target = target;
    }

    @LuaFunction(mainThread = true)
    public final Object[] getProgram() {

        SFMItemHandleTarget.Resolution resolution = target.resolve();
        return resolution.isResolved()
               ? new Object[]{DiskItem.getProgramStringReadOnly(resolution.stack())}
               : SFMComputerCraftResults.unavailable(resolution.errorCode());
    }

    @LuaFunction(mainThread = true)
    public final Object[] setProgram(String source) {

        SFMItemHandleTarget.Resolution resolution = target.resolve();
        if (!resolution.isResolved()) {
            return SFMComputerCraftResults.failure(resolution.errorCode());
        }
        ItemStack stack = resolution.stack();
        DiskItem.setProgram(stack, source);
        Program program = target.diskUpdated(stack);
        return program == null
               ? SFMComputerCraftResults.success("invalid_program")
               : SFMComputerCraftResults.success();
    }

    @LuaFunction
    public final SFMLabelPositionHolderHandle labels() {

        return new SFMLabelPositionHolderHandle(target::resolve, this::saveLabels);
    }

    private SFMItemHandleTarget.Resolution saveLabels(LabelPositionHolder labels) {

        SFMItemHandleTarget.Resolution resolution = target.resolve();
        if (!resolution.isResolved()) {
            return resolution;
        }
        ItemStack stack = resolution.stack();
        labels.save(stack);
        target.diskUpdated(stack);
        return resolution;
    }
}
