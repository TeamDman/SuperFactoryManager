package ca.teamdman.sfm.common.compat.computercraft;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMDeferredRegisterBuilder;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.upgrades.UpgradeType;
import net.neoforged.bus.api.IEventBus;

/** Registers SFM's optional CC:Tweaked turtle upgrade serialisers. */
@MCVersionDependentBehaviour // CC:Tweaked 1.113.1 replaced turtle serialisers with upgrade types
public final class SFMComputerCraftTurtleUpgrades {
    private static final SFMDeferredRegister<UpgradeType<? extends ITurtleUpgrade>> REGISTERER =
            new SFMDeferredRegisterBuilder<UpgradeType<? extends ITurtleUpgrade>>()
                    .namespace(SFM.MOD_ID)
                    .registry(ITurtleUpgrade.typeRegistry())
                    .build();

    public static final SFMRegistryObject<UpgradeType<? extends ITurtleUpgrade>, UpgradeType<SFMLabelerTurtleUpgrade>>
            LABELER = REGISTERER.register(
            "labeler",
            () -> UpgradeType.simple(new SFMLabelerTurtleUpgrade())
    );

    private SFMComputerCraftTurtleUpgrades() {

    }

    public static void register(IEventBus bus) {

        REGISTERER.register(bus);
    }
}
