package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import net.minecraftforge.eventbus.api.IEventBus;

/** Registered document-head actions shared by panels, keybindings, and the palette. */
public final class SFMDocumentHistoryActions {
    private static final SFMDeferredRegister<SFMClientAction<?>> REGISTERER =
            SFMClientActions.createContributor(SFM.MOD_ID);

    public static final SFMRegistryObject<SFMClientAction<?>, SFMDocumentHistoryUndoAction> UNDO =
            REGISTERER.register("document/history/undo", SFMDocumentHistoryUndoAction::new);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMDocumentHistoryRedoAction> REDO =
            REGISTERER.register("document/history/redo", SFMDocumentHistoryRedoAction::new);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMDocumentHistoryViewTransposeAction> TRANSPOSE_VIEW =
            REGISTERER.register("document/history/view/transpose", SFMDocumentHistoryViewTransposeAction::new);
    static {
        for (var kind : SFMTextEditorPointerAction.Kind.values())
            REGISTERER.register("document/pointer/" + kind.name().toLowerCase(java.util.Locale.ROOT),
                    () -> new SFMTextEditorPointerAction(kind));
        for (var kind : SFMTextEditorSearchAction.Kind.values())
            REGISTERER.register("document/search/" + kind.name().toLowerCase(java.util.Locale.ROOT),
                    () -> new SFMTextEditorSearchAction(kind));
    }

    private SFMDocumentHistoryActions() {
    }

    public static void register(IEventBus bus) {
        REGISTERER.register(bus);
    }
}
