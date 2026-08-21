package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import net.minecraftforge.eventbus.api.IEventBus;

/** Registered trajectory-machine controls shared by History Graph widgets and the palette. */
public final class SFMTrajectoryActions {
    private static final SFMDeferredRegister<SFMClientAction<?>> REGISTERER =
            SFMClientActions.createContributor(SFM.MOD_ID);

    public static final SFMRegistryObject<SFMClientAction<?>, SFMTrajectoryMachineAction> PLAN = register(
            SFMTrajectoryMachineAction.Kind.PLAN);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMTrajectoryMachineAction> STEP = register(
            SFMTrajectoryMachineAction.Kind.STEP);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMTrajectoryMachineAction> RUN = register(
            SFMTrajectoryMachineAction.Kind.RUN);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMTrajectoryMachineAction> PAUSE = register(
            SFMTrajectoryMachineAction.Kind.PAUSE);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMTrajectoryMachineAction> REPLAN = register(
            SFMTrajectoryMachineAction.Kind.REPLAN);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMTrajectoryMachineAction> SELECT_ROUTE = register(
            SFMTrajectoryMachineAction.Kind.SELECT_ROUTE);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMTrajectoryMachineAction> INSPECT_COST = register(
            SFMTrajectoryMachineAction.Kind.INSPECT_COST);

    private SFMTrajectoryActions() {
    }

    private static SFMRegistryObject<SFMClientAction<?>, SFMTrajectoryMachineAction> register(
            SFMTrajectoryMachineAction.Kind kind
    ) {
        return REGISTERER.register(kind.path(), () -> new SFMTrajectoryMachineAction(kind));
    }

    public static void register(IEventBus bus) {
        REGISTERER.register(bus);
    }
}
