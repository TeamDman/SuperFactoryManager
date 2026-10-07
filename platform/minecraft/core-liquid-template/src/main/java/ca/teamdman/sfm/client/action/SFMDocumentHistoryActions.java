package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
{% if features.document_history %}
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
{% endif %}
import net.minecraftforge.eventbus.api.IEventBus;

/** Registered document-head actions shared by panels, keybindings, and the palette. */
public final class SFMDocumentHistoryActions {
    private static final SFMDeferredRegister<SFMClientAction<?>> REGISTERER =
            SFMClientActions.createContributor(SFM.MOD_ID);

{% if features.document_history %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMDocumentHistoryUndoAction> UNDO =
            REGISTERER.register("document/history/undo", SFMDocumentHistoryUndoAction::new);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMDocumentHistoryRedoAction> REDO =
            REGISTERER.register("document/history/redo", SFMDocumentHistoryRedoAction::new);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMDocumentHistoryViewTransposeAction> TRANSPOSE_VIEW =
            REGISTERER.register("document/history/view/transpose", SFMDocumentHistoryViewTransposeAction::new);
{% endif %}
{% if features.editor_pointer_actions or features.editor_search %}
    static {
{% if features.editor_pointer_actions %}
        for (var kind : SFMTextEditorPointerAction.Kind.values())
            REGISTERER.register("document/pointer/" + kind.name().toLowerCase(java.util.Locale.ROOT),
                    () -> new SFMTextEditorPointerAction(kind));
{% endif %}
{% if features.editor_search %}
        for (var kind : SFMTextEditorSearchAction.Kind.values())
            REGISTERER.register("document/search/" + kind.name().toLowerCase(java.util.Locale.ROOT),
                    () -> new SFMTextEditorSearchAction(kind));
{% endif %}
    }
{% endif %}

    private SFMDocumentHistoryActions() {
    }

    public static void register(IEventBus bus) {
        REGISTERER.register(bus);
    }
}
