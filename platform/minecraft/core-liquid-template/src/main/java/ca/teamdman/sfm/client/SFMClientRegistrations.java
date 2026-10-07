package ca.teamdman.sfm.client;

{% if features.client_actions %}
import ca.teamdman.sfm.client.action.SFMCommandPaletteActions;
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.client_actions %}
{% if features.client_program_consent %}
import ca.teamdman.sfm.client.action.SFMClientProgramConsentActions;
{% endif %}
{% endif %}
{% if features.client_actions %}
{% if features.client_program_reads %}
import ca.teamdman.sfm.client.action.SFMClientProgramReadActions;
{% endif %}
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
{% if features.client_actions %}
{% if features.developer_tools %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.developer_world_actions %}
import ca.teamdman.sfm.client.action.SFMDeveloperActions;
{% else %}
{% if features.workspace_panel_actions %}
{% if features.input_diagnostics_panel %}
import ca.teamdman.sfm.client.action.SFMDeveloperActions;
{% else %}
{% if features.editor_document_panels and features.workspace_panel_reopening %}
import ca.teamdman.sfm.client.action.SFMDeveloperActions;
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% else %}
import ca.teamdman.sfm.client.action.SFMDeveloperActions;
{% endcase %}
{% endif %}
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.client_actions %}
{% if features.document_history or features.editor_pointer_actions or features.editor_search %}
import ca.teamdman.sfm.client.action.SFMDocumentHistoryActions;
{% endif %}
{% endif %}
{% if features.client_actions %}
{% if features.file_explorer or features.registry_explorer or features.explorer_search or features.explorer_compaction or features.explorer_navigation or features.java_symbols or features.release_review or features.workspace_counterfactuals or features.theme_preview_rules or features.clipboard_action_commands %}
import ca.teamdman.sfm.client.action.SFMExplorerActions;
{% endif %}
{% endif %}
{% if features.client_actions %}
{% if features.client_overlay_scenes %}
import ca.teamdman.sfm.client.action.SFMOverlayActions;
{% endif %}
{% endif %}
{% if features.client_actions %}
{% if features.packet_actions %}
{% if features.packet_transport_private or features.multiplayer_packets %}
import ca.teamdman.sfm.client.action.SFMPacketActions;
{% endif %}
{% endif %}
{% endif %}
{% if features.client_actions %}
{% if features.review_sessions or features.release_review %}
import ca.teamdman.sfm.client.action.SFMReviewActions;
{% endif %}
{% endif %}
{% if features.client_actions %}
{% if features.route_comparison %}
import ca.teamdman.sfm.client.action.SFMRouteComparisonActions;
{% endif %}
{% endif %}
{% if features.client_actions %}
{% if features.spatial_coverage %}
import ca.teamdman.sfm.client.action.SFMSpatialActions;
{% endif %}
{% endif %}
{% if features.client_actions %}
{% if features.java_symbols or features.context_actions %}
import ca.teamdman.sfm.client.action.SFMSymbolActions;
{% endif %}
{% endif %}
{% if features.client_actions %}
{% if features.touch_display_terminal_mount %}
import ca.teamdman.sfm.client.action.SFMTerminalDisplayActions;
{% endif %}
{% endif %}
{% if features.client_actions %}
{% if features.tooltip_mode_override %}
import ca.teamdman.sfm.client.action.SFMTooltipModeActions;
{% endif %}
{% endif %}
{% if features.client_actions %}
{% if features.trajectory_panels %}
import ca.teamdman.sfm.client.action.SFMTrajectoryActions;
{% endif %}
{% endif %}
{% if features.client_actions %}
{% if features.workspace_counterfactuals %}
import ca.teamdman.sfm.client.action.SFMWorkspaceCounterfactualActions;
{% endif %}
{% endif %}
{% if features.client_actions %}
{% if features.workspace_lifecycle or features.workspace_panel_entry_controls %}
import ca.teamdman.sfm.client.action.SFMWorkspaceLifecycleActions;
{% endif %}
{% endif %}
{% if features.client_actions %}
{% if features.command_history %}
import ca.teamdman.sfm.client.command.SFMCommandHistoryService;
{% endif %}
{% endif %}
{% if features.multiplayer_packets %}
import ca.teamdman.sfm.client.net.SFMMultiplayerClientRuntime;
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
{% if features.client_actions %}
import ca.teamdman.sfm.client.registry.SFMClientActions;
{% endif %}
{% if features.workspace_panels %}
import ca.teamdman.sfm.client.registry.SFMClientScreenTypes;
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.keyboard_profiles %}
import ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituationRegistrations;
{% endif %}
{% if features.keyboard_profiles %}
import ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituations;
{% endif %}
import ca.teamdman.sfm.client.registry.SFMMenuScreens;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
import ca.teamdman.sfm.client.registry.SFMMenuScreens;
{% when "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
import ca.teamdman.sfm.client.registry.SFMTextEditorActions;
import ca.teamdman.sfm.client.registry.SFMTextEditors;
{% if features.client_actions %}
{% if features.manager_editor_actions %}
import ca.teamdman.sfm.client.screen.text_editor.SFMDocumentActionTarget;
{% endif %}
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panels %}
{% if features.route_comparison %}
import ca.teamdman.sfm.client.screen.workspace.SFMRouteComparisonScreenType;
{% endif %}
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
{% if features.workspace_panels %}
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceScreenTypes;
{% endif %}
{% case minecraft_version %}
{% when "1.19.2" %}
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
{% when "1.19.4", "1.20", "1.20.1" %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
{% when "1.20.2", "1.20.3", "1.20.4" %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
{% when "1.21", "1.21.1", "26.1.2" %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.neoforged.bus.api.IEventBus;
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2" %}
/**
 * Owns registrations whose classes may reference Minecraft client-only types.
 *
 * <p>The common mod constructor calls this class only through Forge's dist executor, so dedicated
 * servers can discover and run GameTests without resolving GUI classes.</p>
 */
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
/** Kept behind the common entrypoint's physical-client guard to avoid resolving GUI types on a server. */
{% endcase %}
public final class SFMClientRegistrations {
    private SFMClientRegistrations() {
    }

{% case minecraft_version %}
{% when "1.19.2" %}
    public static void register() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    @MCVersionDependentBehaviour
    public static void register(IEventBus bus) {
{% endcase %}
        SFMTextEditors.register(bus);
        SFMTextEditorActions.register(bus);
{% if features.client_actions %}
        SFMClientActions.register(bus);
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.keyboard_profiles %}
        SFMKeyboardUsageSituations.register(bus);
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
{% if features.workspace_panels %}
        SFMClientScreenTypes.register(bus);
{% endif %}
{% if features.workspace_panels %}
        SFMWorkspaceScreenTypes.register(bus);
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panels %}
{% if features.route_comparison %}
        SFMRouteComparisonScreenType.register(bus);
{% endif %}
{% endif %}

{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
{% if features.client_actions %}
{% if features.manager_editor_actions %}
        SFMDocumentActionTarget.Actions.register(bus);
{% endif %}
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.client_actions %}
{% if features.document_history or features.editor_pointer_actions or features.editor_search %}
        SFMDocumentHistoryActions.register(bus);
{% endif %}
{% endif %}

{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
{% if features.client_actions %}
        SFMCommandPaletteActions.register(bus);
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.client_actions %}
{% if features.file_explorer or features.registry_explorer or features.explorer_search or features.explorer_compaction or features.explorer_navigation or features.java_symbols or features.release_review or features.workspace_counterfactuals or features.theme_preview_rules or features.clipboard_action_commands %}
        SFMExplorerActions.register(bus);
{% endif %}
{% endif %}
{% if features.client_actions %}
{% if features.client_overlay_scenes %}
        SFMOverlayActions.register(bus);
{% endif %}
{% endif %}
{% if features.client_actions %}
{% if features.tooltip_mode_override %}
        SFMTooltipModeActions.register(bus);
{% endif %}
{% endif %}
{% if features.client_actions %}
{% if features.packet_actions %}
{% if features.packet_transport_private or features.multiplayer_packets %}
        SFMPacketActions.register(bus);
{% endif %}
{% endif %}
{% endif %}
{% if features.client_actions %}
{% if features.client_program_consent %}
        SFMClientProgramConsentActions.register(bus);
{% endif %}
{% endif %}
{% if features.client_actions %}
{% if features.client_program_reads %}
        SFMClientProgramReadActions.register(bus);
{% endif %}
{% endif %}
{% if features.client_actions %}
{% if features.touch_display_terminal_mount %}
        SFMTerminalDisplayActions.register(bus);
{% endif %}
{% endif %}
{% if features.client_actions %}
{% if features.java_symbols or features.context_actions %}
        SFMSymbolActions.register(bus);
{% endif %}
{% endif %}
{% if features.client_actions %}
{% if features.spatial_coverage %}
        SFMSpatialActions.register(bus);
{% endif %}
{% endif %}
{% if features.client_actions %}
{% if features.trajectory_panels %}
        SFMTrajectoryActions.register(bus);
{% endif %}
{% endif %}
{% if features.client_actions %}
{% if features.review_sessions or features.release_review %}
        SFMReviewActions.register(bus);
{% endif %}
{% endif %}
{% if features.client_actions %}
{% if features.route_comparison %}
        SFMRouteComparisonActions.register(bus);
{% endif %}
{% endif %}
{% if features.client_actions %}
{% if features.workspace_counterfactuals %}
        SFMWorkspaceCounterfactualActions.register(bus);
{% endif %}
{% endif %}
{% if features.client_actions %}
{% if features.workspace_lifecycle or features.workspace_panel_entry_controls %}
        SFMWorkspaceLifecycleActions.register(bus);
{% endif %}
{% endif %}

{% if features.keyboard_profiles %}
        SFMKeyboardUsageSituationRegistrations.register(bus);
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
{% if features.client_actions %}
{% if features.developer_tools %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.developer_world_actions %}
        SFMDeveloperActions.register(bus);
{% else %}
{% if features.workspace_panel_actions %}
{% if features.input_diagnostics_panel %}
        SFMDeveloperActions.register(bus);
{% else %}
{% if features.editor_document_panels and features.workspace_panel_reopening %}
        SFMDeveloperActions.register(bus);
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% else %}
        SFMDeveloperActions.register(bus);
{% endcase %}
{% endif %}
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}

        bus.addListener((FMLClientSetupEvent event) -> {
{% if features.multiplayer_packets %}
            SFMMultiplayerClientRuntime.initialize();
{% endif %}
            SFMMenuScreens.register();
{% if features.client_actions %}
{% if features.command_history %}
            SFMCommandHistoryService.initializeDefault();
{% endif %}
{% endif %}
{% if features.client_actions %}
            SFMClientActions.commandTree();
{% endif %}
        });
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
        bus.addListener((FMLClientSetupEvent event) -> {
            SFMMenuScreens.register();
{% if features.client_actions %}
            SFMClientActions.commandTree();
{% endif %}
        });
{% when "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
    }
}
