package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import net.minecraftforge.eventbus.api.IEventBus;

/** Deferred registrations for the bounded X5 workspace fixture. */
public final class SFMWorkspaceCounterfactualActions {
    private static final SFMDeferredRegister<SFMClientAction<?>> REGISTERER =
            SFMClientActions.createContributor(SFM.MOD_ID);

    public static final SFMRegistryObject<SFMClientAction<?>, SFMWorkspaceCounterfactualAction> SELECTION_SET =
            register(SFMWorkspaceCounterfactualAction.Kind.SELECTION_SET);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMWorkspaceCounterfactualAction>
            FORK_BEFORE_SELECTION = register(SFMWorkspaceCounterfactualAction.Kind.FORK_BEFORE_SELECTION);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMWorkspaceCounterfactualAction>
            CHECKOUT_RECORDED_A = register(SFMWorkspaceCounterfactualAction.Kind.CHECKOUT_RECORDED_A);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMWorkspaceCounterfactualAction> REPLAY_FROZEN_A =
            register(SFMWorkspaceCounterfactualAction.Kind.REPLAY_FROZEN_A);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMWorkspaceCounterfactualAction>
            REEVALUATE_SELECTED = register(SFMWorkspaceCounterfactualAction.Kind.REEVALUATE_SELECTED);

    private SFMWorkspaceCounterfactualActions() { }

    private static SFMRegistryObject<SFMClientAction<?>, SFMWorkspaceCounterfactualAction> register(
            SFMWorkspaceCounterfactualAction.Kind kind
    ) {
        return REGISTERER.register(kind.path(), () -> new SFMWorkspaceCounterfactualAction(kind));
    }

    public static void register(IEventBus bus) { REGISTERER.register(bus); }
}
