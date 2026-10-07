package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMClientActions;
{% if features.java_symbols and features.editor_documents and features.workspace_panels %}
import ca.teamdman.sfm.client.symbol.SFMSymbolInspectionFormatters;
{% endif %}
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import net.minecraftforge.eventbus.api.IEventBus;

/** Registered semantic symbol-navigation controls. */
public final class SFMSymbolActions {
    private static final SFMDeferredRegister<SFMClientAction<?>> REGISTERER =
            SFMClientActions.createContributor(SFM.MOD_ID);

{% if features.java_symbols %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMJumpToDefinitionAction> OPEN_DEFINITION =
            REGISTERER.register("symbol/definition/open", SFMJumpToDefinitionAction::new);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMFindReferencesAction> OPEN_REFERENCES =
            REGISTERER.register("symbol/references/open", SFMFindReferencesAction::new);
{% endif %}
{% if features.context_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMContextActionsOpenAction> OPEN_CONTEXT_ACTIONS =
            REGISTERER.register("context/actions/open", SFMContextActionsOpenAction::new);
{% endif %}
{% if features.java_symbols and features.editor_documents and features.workspace_panels %}
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

{% endif %}
    private SFMSymbolActions() {
    }

{% if features.java_symbols and features.editor_documents and features.workspace_panels %}
    private static SFMRegistryObject<SFMClientAction<?>, SFMSymbolCopyAction> copy(
            SFMSymbolInspectionFormatters.Projection projection
    ) {
        return REGISTERER.register("symbol/copy/" + projection.path(), () -> new SFMSymbolCopyAction(projection));
    }

{% endif %}
    public static void register(IEventBus bus) {
        REGISTERER.register(bus);
    }
}
