package ca.teamdman.sfm.client;

import ca.teamdman.sfm.client.action.SFMCommandPaletteActions;
import ca.teamdman.sfm.client.action.SFMClientProgramConsentActions;
import ca.teamdman.sfm.client.action.SFMClientProgramReadActions;
import ca.teamdman.sfm.client.action.SFMDeveloperActions;
import ca.teamdman.sfm.client.action.SFMDocumentHistoryActions;
import ca.teamdman.sfm.client.action.SFMExplorerActions;
import ca.teamdman.sfm.client.action.SFMOverlayActions;
import ca.teamdman.sfm.client.action.SFMPacketActions;
import ca.teamdman.sfm.client.action.SFMReviewActions;
import ca.teamdman.sfm.client.action.SFMRouteComparisonActions;
import ca.teamdman.sfm.client.action.SFMSpatialActions;
import ca.teamdman.sfm.client.action.SFMSymbolActions;
import ca.teamdman.sfm.client.action.SFMTerminalDisplayActions;
import ca.teamdman.sfm.client.action.SFMTooltipModeActions;
import ca.teamdman.sfm.client.action.SFMTrajectoryActions;
import ca.teamdman.sfm.client.action.SFMWorkspaceCounterfactualActions;
import ca.teamdman.sfm.client.action.SFMWorkspaceLifecycleActions;
import ca.teamdman.sfm.client.command.SFMCommandHistoryService;
import ca.teamdman.sfm.client.net.SFMMultiplayerClientRuntime;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.client.registry.SFMClientScreenTypes;
import ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituationRegistrations;
import ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituations;
import ca.teamdman.sfm.client.registry.SFMMenuScreens;
import ca.teamdman.sfm.client.registry.SFMTextEditorActions;
import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.client.screen.text_editor.SFMDocumentActionTarget;
import ca.teamdman.sfm.client.screen.workspace.SFMRouteComparisonScreenType;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceScreenTypes;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Kept behind the common entrypoint's physical-client guard to avoid resolving GUI types on a server. */
public final class SFMClientRegistrations {
    private SFMClientRegistrations() {
    }

    @MCVersionDependentBehaviour
    public static void register(IEventBus bus) {
        SFMTextEditors.register(bus);
        SFMTextEditorActions.register(bus);
        SFMClientActions.register(bus);
        SFMKeyboardUsageSituations.register(bus);
        SFMClientScreenTypes.register(bus);
        SFMWorkspaceScreenTypes.register(bus);
        SFMRouteComparisonScreenType.register(bus);

        SFMDocumentActionTarget.Actions.register(bus);
        SFMDocumentHistoryActions.register(bus);

        SFMCommandPaletteActions.register(bus);
        SFMExplorerActions.register(bus);
        SFMOverlayActions.register(bus);
        SFMTooltipModeActions.register(bus);
        SFMPacketActions.register(bus);
        SFMClientProgramConsentActions.register(bus);
        SFMClientProgramReadActions.register(bus);
        SFMTerminalDisplayActions.register(bus);
        SFMSymbolActions.register(bus);
        SFMSpatialActions.register(bus);
        SFMTrajectoryActions.register(bus);
        SFMReviewActions.register(bus);
        SFMRouteComparisonActions.register(bus);
        SFMWorkspaceCounterfactualActions.register(bus);
        SFMWorkspaceLifecycleActions.register(bus);

        SFMKeyboardUsageSituationRegistrations.register(bus);
        SFMDeveloperActions.register(bus);

        bus.addListener((FMLClientSetupEvent event) -> {
            SFMMultiplayerClientRuntime.initialize();
            SFMMenuScreens.register();
            SFMCommandHistoryService.initializeDefault();
            SFMClientActions.commandTree();
        });
    }
}
