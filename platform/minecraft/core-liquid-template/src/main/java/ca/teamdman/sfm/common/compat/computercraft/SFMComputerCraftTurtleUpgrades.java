package ca.teamdman.sfm.common.compat.computercraft;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMDeferredRegisterBuilder;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
import dan200.computercraft.api.turtle.TurtleUpgradeSerialiser;
import net.minecraftforge.eventbus.api.IEventBus;
{% when "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
{% endcase %}
{% case minecraft_version %}
{% when "1.20.4", "1.21" %}
import dan200.computercraft.api.upgrades.UpgradeSerialiser;
import net.neoforged.bus.api.IEventBus;
{% when "1.21.1", "26.1.2" %}
import dan200.computercraft.api.upgrades.UpgradeType;
import net.neoforged.bus.api.IEventBus;
{% endcase %}

/** Registers SFM's optional CC:Tweaked turtle upgrade serialisers. */
{% case minecraft_version %}
{% when "1.20.4", "1.21" %}
@MCVersionDependentBehaviour // CC:Tweaked 1.110.2 moved turtle serialisers to its data registry
{% when "1.21.1", "26.1.2" %}
@MCVersionDependentBehaviour // CC:Tweaked 1.113.1 replaced turtle serialisers with upgrade types
{% endcase %}
public final class SFMComputerCraftTurtleUpgrades {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
    private static final SFMDeferredRegister<TurtleUpgradeSerialiser<?>> REGISTERER =
            new SFMDeferredRegisterBuilder<TurtleUpgradeSerialiser<?>>()
{% when "1.20.4", "1.21" %}
    private static final SFMDeferredRegister<UpgradeSerialiser<? extends ITurtleUpgrade>> REGISTERER =
            new SFMDeferredRegisterBuilder<UpgradeSerialiser<? extends ITurtleUpgrade>>()
{% when "1.21.1", "26.1.2" %}
    private static final SFMDeferredRegister<UpgradeType<? extends ITurtleUpgrade>> REGISTERER =
            new SFMDeferredRegisterBuilder<UpgradeType<? extends ITurtleUpgrade>>()
{% endcase %}
                    .namespace(SFM.MOD_ID)
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                    .registry(TurtleUpgradeSerialiser.REGISTRY_ID)
{% when "1.20", "1.20.1", "1.20.2", "1.20.3" %}
                    .registry(TurtleUpgradeSerialiser.registryId())
{% when "1.20.4", "1.21" %}
                    .registry(ITurtleUpgrade.serialiserRegistryKey())
{% when "1.21.1", "26.1.2" %}
                    .registry(ITurtleUpgrade.typeRegistry())
{% endcase %}
                    .build();

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
    public static final SFMRegistryObject<TurtleUpgradeSerialiser<?>, TurtleUpgradeSerialiser<SFMLabelerTurtleUpgrade>>
{% when "1.20.4", "1.21" %}
    public static final SFMRegistryObject<UpgradeSerialiser<? extends ITurtleUpgrade>, UpgradeSerialiser<SFMLabelerTurtleUpgrade>>
{% when "1.21.1", "26.1.2" %}
    public static final SFMRegistryObject<UpgradeType<? extends ITurtleUpgrade>, UpgradeType<SFMLabelerTurtleUpgrade>>
{% endcase %}
            LABELER = REGISTERER.register(
            "labeler",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
            () -> TurtleUpgradeSerialiser.simple(SFMLabelerTurtleUpgrade::new)
{% when "1.20.4", "1.21" %}
            () -> UpgradeSerialiser.simple(SFMLabelerTurtleUpgrade::new)
{% when "1.21.1", "26.1.2" %}
            () -> UpgradeType.simple(new SFMLabelerTurtleUpgrade())
{% endcase %}
    );

    private SFMComputerCraftTurtleUpgrades() {

    }

    public static void register(IEventBus bus) {

        REGISTERER.register(bus);
    }
}
