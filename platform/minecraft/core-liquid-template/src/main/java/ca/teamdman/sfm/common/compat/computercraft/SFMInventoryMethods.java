package ca.teamdman.sfm.common.compat.computercraft;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.item.LabelGunItem;
import dan200.computercraft.api.lua.GenericSource;
import dan200.computercraft.api.lua.LuaFunction;
{% case minecraft_version %}
{% when "1.19.2" %}
import net.minecraft.resources.ResourceLocation;
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
import net.minecraftforge.items.IItemHandler;
{% when "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import net.neoforged.neoforge.items.IItemHandler;
{% endcase %}

import javax.annotation.Nonnull;

/** Adds SFM item-handle acquisition to CC:Tweaked's ordinary inventory peripherals. */
public final class SFMInventoryMethods implements GenericSource {
{% case minecraft_version %}
{% when "1.19.2" %}
    private static final ResourceLocation ID = new ResourceLocation(SFM.MOD_ID, "inventory");

    @Override
    public @Nonnull ResourceLocation id() {

        return ID;
    }
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    private static final String ID = SFM.MOD_ID + ":inventory";

    @Override
    public @Nonnull String id() {

        return ID;
    }
{% endcase %}

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
