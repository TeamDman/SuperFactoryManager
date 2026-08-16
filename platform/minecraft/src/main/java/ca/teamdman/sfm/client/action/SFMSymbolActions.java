package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import net.minecraftforge.eventbus.api.IEventBus;

/** Registered semantic symbol-navigation controls. */
public final class SFMSymbolActions {
    private static final SFMDeferredRegister<SFMClientAction<?>> REGISTERER =
            SFMClientActions.createContributor(SFM.MOD_ID);

    public static final SFMRegistryObject<SFMClientAction<?>, SFMJumpToDefinitionAction> OPEN_DEFINITION =
            REGISTERER.register("symbol/definition/open", SFMJumpToDefinitionAction::new);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMFindReferencesAction> OPEN_REFERENCES =
            REGISTERER.register("symbol/references/open", SFMFindReferencesAction::new);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMContextActionsOpenAction> OPEN_CONTEXT_ACTIONS =
            REGISTERER.register("context/actions/open", SFMContextActionsOpenAction::new);

    private SFMSymbolActions() {
    }

    public static void register(IEventBus bus) {
        REGISTERER.register(bus);
    }
}
