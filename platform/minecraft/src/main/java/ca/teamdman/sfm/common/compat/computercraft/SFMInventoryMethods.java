package ca.teamdman.sfm.common.compat.computercraft;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.item.LabelGunItem;
import dan200.computercraft.api.lua.GenericSource;
import dan200.computercraft.api.lua.LuaFunction;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;

/** Adds SFM item-handle acquisition to CC:Tweaked's ordinary inventory peripherals. */
public final class SFMInventoryMethods implements GenericSource {
    private static final String ID = SFM.MOD_ID + ":inventory";

    @Override
    public @Nonnull String id() {

        return ID;
    }

    @LuaFunction
    public static SFMDiskHandle getSfmDisk(
            IItemHandler inventory,
            int slot
    ) {

        return slot < 1
               ? null
               : new SFMDiskHandle(SFMItemHandleTarget.lazyInventory(
                       inventory,
                       slot - 1,
                       stack -> stack.getItem() instanceof DiskItem,
                       "not_disk"
               ));
    }

    @LuaFunction
    public static SFMLabelGunHandle getSfmLabelGun(
            IItemHandler inventory,
            int slot
    ) {

        return slot < 1
               ? null
               : new SFMLabelGunHandle(SFMItemHandleTarget.lazyInventory(
                       inventory,
                       slot - 1,
                       stack -> stack.getItem() instanceof LabelGunItem,
                       "not_label_gun"
               ));
    }
}
