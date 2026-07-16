package ca.teamdman.sfm.common.compat.computercraft;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMDeferredRegisterBuilder;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import dan200.computercraft.api.turtle.TurtleUpgradeSerialiser;
import net.minecraftforge.eventbus.api.IEventBus;

/** Registers SFM's optional CC:Tweaked turtle upgrade serialisers. */
public final class SFMComputerCraftTurtleUpgrades {
    private static final SFMDeferredRegister<TurtleUpgradeSerialiser<?>> REGISTERER =
            new SFMDeferredRegisterBuilder<TurtleUpgradeSerialiser<?>>()
                    .namespace(SFM.MOD_ID)
                    .registry(TurtleUpgradeSerialiser.registryId())
                    .build();

    public static final SFMRegistryObject<TurtleUpgradeSerialiser<?>, TurtleUpgradeSerialiser<SFMLabelerTurtleUpgrade>>
            LABELER = REGISTERER.register(
            "labeler",
            () -> TurtleUpgradeSerialiser.simple(SFMLabelerTurtleUpgrade::new)
    );

    private SFMComputerCraftTurtleUpgrades() {

    }

    public static void register(IEventBus bus) {

        REGISTERER.register(bus);
    }
}
