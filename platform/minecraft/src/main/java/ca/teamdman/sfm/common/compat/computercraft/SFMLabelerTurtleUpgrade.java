package ca.teamdman.sfm.common.compat.computercraft;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.turtle.AbstractTurtleUpgrade;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.api.turtle.TurtleUpgradeType;
import dan200.computercraft.api.upgrades.IUpgradeBase;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

/** A peripheral turtle upgrade whose crafting stack is an unmodified SFM label gun. */
public final class SFMLabelerTurtleUpgrade extends AbstractTurtleUpgrade {
    @SFMLocalizationDatagen
    public static final LocalizationEntry ADJECTIVE = new LocalizationEntry(
            IUpgradeBase.getDefaultAdjective(new ResourceLocation(SFM.MOD_ID, "labeler")),
            "Labeler"
    );

    public SFMLabelerTurtleUpgrade(ResourceLocation id) {

        super(id, TurtleUpgradeType.PERIPHERAL, new ItemStack(SFMItems.LABEL_GUN.get()));
    }

    @Override
    public @Nonnull IPeripheral createPeripheral(
            @Nonnull ITurtleAccess turtle,
            @Nonnull TurtleSide side
    ) {

        return new SFMTurtleLabelerPeripheral(turtle);
    }
}
