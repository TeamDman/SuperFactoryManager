package ca.teamdman.sfm.common.compat.computercraft;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMDeferredRegisterBuilder;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.upgrades.UpgradeSerialiser;
import net.neoforged.bus.api.IEventBus;

/** Registers SFM's optional CC:Tweaked turtle upgrade serialisers. */
@MCVersionDependentBehaviour // CC:Tweaked 1.110.2 moved turtle serialisers to its data registry
public final class SFMComputerCraftTurtleUpgrades {
    private static final SFMDeferredRegister<UpgradeSerialiser<? extends ITurtleUpgrade>> REGISTERER =
            new SFMDeferredRegisterBuilder<UpgradeSerialiser<? extends ITurtleUpgrade>>()
                    .namespace(SFM.MOD_ID)
                    .registry(ITurtleUpgrade.serialiserRegistryKey())
                    .build();

    public static final SFMRegistryObject<UpgradeSerialiser<? extends ITurtleUpgrade>, UpgradeSerialiser<SFMLabelerTurtleUpgrade>>
            LABELER = REGISTERER.register(
            "labeler",
            () -> UpgradeSerialiser.simple(SFMLabelerTurtleUpgrade::new)
    );

    private SFMComputerCraftTurtleUpgrades() {

    }

    public static void register(IEventBus bus) {

        REGISTERER.register(bus);
    }
}
