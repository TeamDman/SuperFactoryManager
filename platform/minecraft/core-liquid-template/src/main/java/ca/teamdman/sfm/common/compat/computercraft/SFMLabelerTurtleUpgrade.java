package ca.teamdman.sfm.common.compat.computercraft;

{% case minecraft_version %}
{% when "1.21" %}
import ca.teamdman.sfm.common.util.SFMResourceLocation;

{% endcase %}
import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.turtle.AbstractTurtleUpgrade;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.api.turtle.TurtleUpgradeType;
{% case minecraft_version %}
{% when "1.19.2" %}
import dan200.computercraft.api.upgrades.IUpgradeBase;
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21" %}
import dan200.computercraft.api.upgrades.UpgradeBase;
{% when "1.21.1", "26.1.2" %}
import dan200.computercraft.api.upgrades.UpgradeType;
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21" %}
import net.minecraft.resources.ResourceLocation;
{% endcase %}
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

/** A peripheral turtle upgrade whose crafting stack is an unmodified SFM label gun. */
public final class SFMLabelerTurtleUpgrade extends AbstractTurtleUpgrade {
    @SFMLocalizationDatagen
    public static final LocalizationEntry ADJECTIVE = new LocalizationEntry(
{% case minecraft_version %}
{% when "1.19.2" %}
            IUpgradeBase.getDefaultAdjective(new ResourceLocation(SFM.MOD_ID, "labeler")),
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
            UpgradeBase.getDefaultAdjective(new ResourceLocation(SFM.MOD_ID, "labeler")),
{% when "1.21" %}
            UpgradeBase.getDefaultAdjective(SFMResourceLocation.fromNamespaceAndPath(SFM.MOD_ID, "labeler")),
{% when "1.21.1", "26.1.2" %}
            "upgrade." + SFM.MOD_ID + ".labeler.adjective",
{% endcase %}
            "Labeler"
    );

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21" %}
    public SFMLabelerTurtleUpgrade(ResourceLocation id) {

        super(id, TurtleUpgradeType.PERIPHERAL, new ItemStack(SFMItems.LABEL_GUN.get()));
    }
{% when "1.21.1", "26.1.2" %}
    public SFMLabelerTurtleUpgrade() {

        super(TurtleUpgradeType.PERIPHERAL, ADJECTIVE.getComponent(), new ItemStack(SFMItems.LABEL_GUN.get()));
    }
{% endcase %}
{% case minecraft_version %}
{% when "1.21.1", "26.1.2" %}

    @Override
    public UpgradeType<SFMLabelerTurtleUpgrade> getType() {

        return SFMComputerCraftTurtleUpgrades.LABELER.get();
    }
{% endcase %}

    @Override
    public @Nonnull IPeripheral createPeripheral(
            @Nonnull ITurtleAccess turtle,
            @Nonnull TurtleSide side
    ) {

        return new SFMTurtleLabelerPeripheral(turtle);
    }
}
