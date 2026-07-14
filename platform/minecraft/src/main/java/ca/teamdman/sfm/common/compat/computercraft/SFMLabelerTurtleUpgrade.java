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
import dan200.computercraft.api.upgrades.UpgradeType;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

/** A peripheral turtle upgrade whose crafting stack is an unmodified SFM label gun. */
public final class SFMLabelerTurtleUpgrade extends AbstractTurtleUpgrade {
    @SFMLocalizationDatagen
    public static final LocalizationEntry ADJECTIVE = new LocalizationEntry(
            "upgrade." + SFM.MOD_ID + ".labeler.adjective",
            "Labeler"
    );

    public SFMLabelerTurtleUpgrade() {

        super(TurtleUpgradeType.PERIPHERAL, ADJECTIVE.getComponent(), new ItemStack(SFMItems.LABEL_GUN.get()));
    }

    @Override
    public UpgradeType<SFMLabelerTurtleUpgrade> getType() {

        return SFMComputerCraftTurtleUpgrades.LABELER.get();
    }

    @Override
    public @Nonnull IPeripheral createPeripheral(
            @Nonnull ITurtleAccess turtle,
            @Nonnull TurtleSide side
    ) {

        return new SFMTurtleLabelerPeripheral(turtle);
    }
}
