package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import net.minecraftforge.eventbus.api.IEventBus;

/** Registered spatial-analysis actions. */
public final class SFMSpatialActions {
    private static final SFMDeferredRegister<SFMClientAction<?>> REGISTERER =
            SFMClientActions.createContributor(SFM.MOD_ID);

    public static final SFMRegistryObject<SFMClientAction<?>, SFMSpatialCoverageRunAction> RUN_COVERAGE =
            REGISTERER.register("spatial/coverage/run", SFMSpatialCoverageRunAction::new);

    private SFMSpatialActions() {
    }

    public static void register(IEventBus bus) {
        REGISTERER.register(bus);
    }
}
