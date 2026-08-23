package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import net.minecraftforge.eventbus.api.IEventBus;

/** Pane and exact panel-entry actions kept separate from the command-palette implementation. */
public final class SFMWorkspaceLifecycleActions {
    private static final SFMDeferredRegister<SFMClientAction<?>> REGISTERER =
            SFMClientActions.createContributor(SFM.MOD_ID);

    public static final SFMRegistryObject<SFMClientAction<?>, SFMPanelEntryAction> PANEL_ENTRY_FOCUS =
            REGISTERER.register("panel/entry/focus",
                    () -> new SFMPanelEntryAction(SFMPanelEntryAction.Operation.FOCUS));
    public static final SFMRegistryObject<SFMClientAction<?>, SFMPanelEntryAction> PANEL_ENTRY_CLOSE =
            REGISTERER.register("panel/entry/close",
                    () -> new SFMPanelEntryAction(SFMPanelEntryAction.Operation.CLOSE));
    public static final SFMRegistryObject<SFMClientAction<?>, SFMPanelEntryAction> PANEL_ENTRY_MOVE_LEFT =
            REGISTERER.register("panel/entry/move/left",
                    () -> new SFMPanelEntryAction(SFMPanelEntryAction.Operation.MOVE_LEFT));
    public static final SFMRegistryObject<SFMClientAction<?>, SFMPanelEntryAction> PANEL_ENTRY_MOVE_RIGHT =
            REGISTERER.register("panel/entry/move/right",
                    () -> new SFMPanelEntryAction(SFMPanelEntryAction.Operation.MOVE_RIGHT));
    public static final SFMRegistryObject<SFMClientAction<?>, SFMPanelEntryAction> PANEL_ENTRY_MOVE_ABOVE =
            REGISTERER.register("panel/entry/move/above",
                    () -> new SFMPanelEntryAction(SFMPanelEntryAction.Operation.MOVE_ABOVE));
    public static final SFMRegistryObject<SFMClientAction<?>, SFMPanelEntryAction> PANEL_ENTRY_MOVE_BELOW =
            REGISTERER.register("panel/entry/move/below",
                    () -> new SFMPanelEntryAction(SFMPanelEntryAction.Operation.MOVE_BELOW));
    public static final SFMRegistryObject<SFMClientAction<?>, SFMClosePaneAction> PANE_CLOSE =
            REGISTERER.register("pane/close",
                    () -> new SFMClosePaneAction(SFMClosePaneAction.Phase.PREFLIGHT));
    public static final SFMRegistryObject<SFMClientAction<?>, SFMClosePaneAction> PANE_CLOSE_CONFIRM =
            REGISTERER.register("pane/close/confirm",
                    () -> new SFMClosePaneAction(SFMClosePaneAction.Phase.CONFIRM));

    private SFMWorkspaceLifecycleActions() {
    }

    public static void register(IEventBus bus) {
        REGISTERER.register(bus);
    }

}
