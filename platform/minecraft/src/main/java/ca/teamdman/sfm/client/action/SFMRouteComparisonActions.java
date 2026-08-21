package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import net.minecraftforge.eventbus.api.IEventBus;

/** Deferred registrations for the X3 route-comparison palette surface. */
public final class SFMRouteComparisonActions {
    private static final SFMDeferredRegister<SFMClientAction<?>> REGISTERER =
            SFMClientActions.createContributor(SFM.MOD_ID);

    public static final SFMRegistryObject<SFMClientAction<?>, SFMRouteComparisonAction> MODE_SET = register(
            SFMRouteComparisonAction.Kind.MODE_SET);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMRouteComparisonAction> SEEK = register(
            SFMRouteComparisonAction.Kind.SEEK);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMRouteComparisonAction> DISPOSITION_SET = register(
            SFMRouteComparisonAction.Kind.DISPOSITION_SET);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMRouteComparisonAction> TRAJECTORY_SELECT = register(
            SFMRouteComparisonAction.Kind.TRAJECTORY_SELECT);

    private SFMRouteComparisonActions() {
    }

    private static SFMRegistryObject<SFMClientAction<?>, SFMRouteComparisonAction> register(
            SFMRouteComparisonAction.Kind kind
    ) {
        return REGISTERER.register(kind.path(), () -> new SFMRouteComparisonAction(kind));
    }

    public static void register(IEventBus bus) {
        REGISTERER.register(bus);
    }
}
