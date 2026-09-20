package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.client.symbol.SFMSymbolInspectionFormatters;
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
    public static final SFMRegistryObject<SFMClientAction<?>, SFMSymbolCopyAction> COPY_DETAILS =
            copy(SFMSymbolInspectionFormatters.Projection.DETAILS);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMSymbolCopyAction> COPY_FILE =
            copy(SFMSymbolInspectionFormatters.Projection.FILE);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMSymbolCopyAction> COPY_LINE =
            copy(SFMSymbolInspectionFormatters.Projection.LINE);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMSymbolCopyAction> COPY_COLUMN =
            copy(SFMSymbolInspectionFormatters.Projection.COLUMN);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMSymbolCopyAction> COPY_BOUNDS =
            copy(SFMSymbolInspectionFormatters.Projection.BOUNDS);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMSymbolCopyAction> COPY_LOGICAL_PATH =
            copy(SFMSymbolInspectionFormatters.Projection.LOGICAL_PATH);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMSymbolCopyAction> COPY_ACCESS_TRANSFORMER_REFERENCE =
            copy(SFMSymbolInspectionFormatters.Projection.ACCESS_TRANSFORMER_REFERENCE);

    private SFMSymbolActions() {
    }

    private static SFMRegistryObject<SFMClientAction<?>, SFMSymbolCopyAction> copy(
            SFMSymbolInspectionFormatters.Projection projection
    ) {
        return REGISTERER.register("symbol/copy/" + projection.path(), () -> new SFMSymbolCopyAction(projection));
    }

    public static void register(IEventBus bus) {
        REGISTERER.register(bus);
    }
}
