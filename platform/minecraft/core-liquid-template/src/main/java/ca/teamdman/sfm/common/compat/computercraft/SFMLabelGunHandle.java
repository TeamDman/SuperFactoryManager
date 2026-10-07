package ca.teamdman.sfm.common.compat.computercraft;

import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.item.LabelGunItem.LabelGunViewMode;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import dan200.computercraft.api.lua.LuaFunction;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/** A mutable Lua handle for one specific SFM label gun item stack. */
public final class SFMLabelGunHandle {
    private final SFMItemHandleTarget target;

    SFMLabelGunHandle(SFMItemHandleTarget target) {

        this.target = target;
    }

    @LuaFunction(mainThread = true)
    public final Object[] getActiveLabel() {

        SFMItemHandleTarget.Resolution resolution = target.resolve();
        return resolution.isResolved()
               ? new Object[]{LabelGunItem.getActiveLabel(resolution.stack())}
               : SFMComputerCraftResults.unavailable(resolution.errorCode());
    }

    @LuaFunction(mainThread = true)
    public final Object[] setActiveLabel(String label) {

        if (!SFMLabelPositionHolderHandle.isValidLabel(label)) {
            return SFMComputerCraftResults.failure("invalid_label");
        }
        SFMItemHandleTarget.Resolution resolution = target.resolve();
        if (!resolution.isResolved()) {
            return SFMComputerCraftResults.failure(resolution.errorCode());
        }
        LabelGunItem.setActiveLabel(resolution.stack(), label);
        target.itemChanged(resolution.stack());
        return SFMComputerCraftResults.success();
    }

    @LuaFunction(mainThread = true)
    public final Object[] clearActiveLabel() {

        SFMItemHandleTarget.Resolution resolution = target.resolve();
        if (!resolution.isResolved()) {
            return SFMComputerCraftResults.failure(resolution.errorCode());
        }
        LabelGunItem.clearActiveLabel(resolution.stack());
        target.itemChanged(resolution.stack());
        return SFMComputerCraftResults.success();
    }

    @LuaFunction(mainThread = true)
    public final Object[] getViewMode() {

        SFMItemHandleTarget.Resolution resolution = target.resolve();
        return resolution.isResolved()
               ? new Object[]{LabelGunItem.getViewModeReadOnly(resolution.stack()).name().toLowerCase(Locale.ROOT)}
               : SFMComputerCraftResults.unavailable(resolution.errorCode());
    }

    @LuaFunction(mainThread = true)
    public final Object[] setViewMode(String viewMode) {

        LabelGunViewMode parsed;
        try {
            parsed = LabelGunViewMode.valueOf(viewMode.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return SFMComputerCraftResults.failure("invalid_view_mode");
        }
        SFMItemHandleTarget.Resolution resolution = target.resolve();
        if (!resolution.isResolved()) {
            return SFMComputerCraftResults.failure(resolution.errorCode());
        }
        LabelGunItem.setViewMode(resolution.stack(), parsed);
        target.itemChanged(resolution.stack());
        return SFMComputerCraftResults.success();
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
        labels.save(resolution.stack());
        target.itemChanged(resolution.stack());
        return resolution;
    }
}
