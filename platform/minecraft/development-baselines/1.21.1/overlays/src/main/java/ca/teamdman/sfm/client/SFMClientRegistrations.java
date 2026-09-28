package ca.teamdman.sfm.client;

import ca.teamdman.sfm.client.action.SFMCommandPaletteActions;
import ca.teamdman.sfm.client.action.SFMDeveloperActions;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.client.registry.SFMClientScreenTypes;
import ca.teamdman.sfm.client.registry.SFMTextEditorActions;
import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.client.screen.text_editor.SFMDocumentActionTarget;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceScreenTypes;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.neoforged.bus.api.IEventBus;

/** Kept behind the common entrypoint's physical-client guard to avoid resolving GUI types on a server. */
public final class SFMClientRegistrations {
    private SFMClientRegistrations() {
    }

    @MCVersionDependentBehaviour
    public static void register(IEventBus bus) {
        SFMTextEditors.register(bus);
        SFMTextEditorActions.register(bus);
        SFMClientActions.register(bus);
        SFMClientScreenTypes.register(bus);
        SFMWorkspaceScreenTypes.register(bus);
        SFMDocumentActionTarget.Actions.register(bus);
        SFMCommandPaletteActions.register(bus);
        SFMDeveloperActions.register(bus);
    }
}
