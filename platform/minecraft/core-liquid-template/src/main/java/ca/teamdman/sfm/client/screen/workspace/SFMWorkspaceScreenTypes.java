package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMClientScreenTypes;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.input_diagnostics_panel %}
import ca.teamdman.sfm.client.screen.workspace.diagnostic.SFMInputDiagnosticsScreenType;
{% endif %}
{% if features.screen_diagnostics %}
import ca.teamdman.sfm.client.screen.workspace.diagnostic.SFMSizeDisplayScreenType;
{% endif %}
{% endcase %}
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.eventbus.api.IEventBus;
{% else %}
import net.neoforged.bus.api.IEventBus;
{% endcase %}

public final class SFMWorkspaceScreenTypes {
    private static final SFMDeferredRegister<SFMClientScreenType> REGISTERER =
            SFMClientScreenTypes.createContributor(SFM.MOD_ID);

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.client_actions %}
{% if features.client_program_consent %}
{% if features.confirmation_review_callbacks %}
{% if features.workspace_panel_lookup %}
{% if features.workspace_panels %}
{% if features.workspace_widget_hosts %}
    public static final SFMRegistryObject<SFMClientScreenType, SFMClientProgramConsentsScreenType> CLIENT_SCRIPT_CONSENTS =
            REGISTERER.register("client_script_consents", SFMClientProgramConsentsScreenType::new);

{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% if features.touch_display_terminal_mount %}
{% if features.client_program_actions and features.client_frame_render and features.client_inbox and features.terminal_keyboard_input and features.terminal_frame_metadata and features.workspace_widget_hosts and features.workspace_panel_reopening and features.client_program_reads %}
{% if features.terminal_remote or features.terminal_vox_runtime or features.terminal_properties %}
    public static final SFMRegistryObject<SFMClientScreenType, SFMTerminalMountsScreenType> TERMINAL_MOUNTS =
            REGISTERER.register("terminal_mounts", SFMTerminalMountsScreenType::new);

{% endif %}
{% endif %}
{% endif %}
{% endcase %}
    public static final SFMRegistryObject<SFMClientScreenType, SFMTestScreenType> TEST_SCREEN = REGISTERER.register(
            "test_screen",
            SFMTestScreenType::new
    );

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.screen_diagnostics %}
    public static final SFMRegistryObject<SFMClientScreenType, SFMSizeDisplayScreenType> SIZE_DISPLAY = REGISTERER.register(
            "size_display",
            SFMSizeDisplayScreenType::new
    );

{% endif %}
{% if features.input_diagnostics_panel %}
    public static final SFMRegistryObject<SFMClientScreenType, SFMInputDiagnosticsScreenType> INPUT_DIAGNOSTICS = REGISTERER.register(
            "input_diagnostics",
            SFMInputDiagnosticsScreenType::new
    );

{% endif %}
{% if features.terminal_remote or features.terminal_vox_runtime or features.terminal_properties %}
{% if features.workspace_widget_hosts %}
{% if features.client_actions %}
{% if features.workspace_panel_reopening %}
    public static final SFMRegistryObject<SFMClientScreenType, SFMTerminalScreenType> TERMINAL = REGISTERER.register(
            "terminal",
            SFMTerminalScreenType::new
    );

{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% if features.terminal_properties %}
{% if features.workspace_widget_hosts %}
{% if features.client_actions %}
{% if features.workspace_panel_reopening %}
    public static final SFMRegistryObject<SFMClientScreenType, SFMTerminalPropertiesScreenType> TERMINAL_PROPERTIES = REGISTERER.register(
            "terminal_properties",
            SFMTerminalPropertiesScreenType::new
    );

{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% if features.trajectory_panels %}
{% if features.workspace_panel_reopening %}
{% if features.client_actions %}
    public static final SFMRegistryObject<SFMClientScreenType, SFMHistoryGraphScreenType> HISTORY_GRAPH = REGISTERER.register(
            "episode/history",
            SFMHistoryGraphScreenType::new
    );

{% endif %}
{% endif %}
{% endif %}
{% if features.document_history %}
    public static final SFMRegistryObject<SFMClientScreenType, SFMDocumentHistoryScreenType> DOCUMENT_HISTORY = REGISTERER.register(
            "document/history",
            SFMDocumentHistoryScreenType::new
    );

{% endif %}
{% if features.trajectory_panels %}
{% if features.timeline_panels %}
{% if features.workspace_panel_reopening %}
{% if features.client_actions %}
{% if features.client_theme %}
    public static final SFMRegistryObject<SFMClientScreenType, SFMCandidateHistoryScreenType> CANDIDATE_HISTORY =
            REGISTERER.register(
                    "episode/candidate-history",
                    SFMCandidateHistoryScreenType::new
            );

{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% if features.trajectory_panels %}
{% if features.workspace_panel_reopening %}
{% if features.client_actions %}
{% if features.client_properties %}
{% if features.editor_document_panels %}
{% if features.canvas_text_editor %}
    public static final SFMRegistryObject<SFMClientScreenType, SFMDecimalNumberingChamberScreenType>
            TEMPORAL_NUMBERING_CHAMBER = REGISTERER.register(
                    "chamber/temporal-decimal-numbering",
                    SFMDecimalNumberingChamberScreenType::new
            );

{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_counterfactuals %}
{% if features.workspace_panel_reopening %}
{% if features.client_actions %}
{% if features.client_properties %}
{% if features.editor_document_panels %}
    public static final SFMRegistryObject<SFMClientScreenType, SFMWorkspaceCounterfactualScreenType>
            WORKSPACE_COUNTERFACTUAL_CHAMBER = REGISTERER.register(
                    "chamber/workspace-counterfactual",
                    SFMWorkspaceCounterfactualScreenType::new
            );

{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% if features.editor_document_panels %}
{% if features.workspace_panel_reopening %}
{% if features.client_actions %}
    public static final SFMRegistryObject<SFMClientScreenType, SFMTextEditorScreenType> TEXT_EDITOR = REGISTERER.register(
            "text_editor",
            SFMTextEditorScreenType::new
    );

{% endif %}
{% endif %}
{% endif %}
{% if features.editor_document_panels %}
{% if features.workspace_panel_reopening %}
{% if features.client_actions %}
    public static final SFMRegistryObject<SFMClientScreenType, SFMGrammarScreenType> GRAMMAR = REGISTERER.register(
            "grammar",
            SFMGrammarScreenType::new
    );

{% endif %}
{% endif %}
{% endif %}
{% if features.file_explorer %}
{% if features.workspace_panel_reopening %}
{% if features.client_actions %}
    public static final SFMRegistryObject<SFMClientScreenType, SFMExplorerScreenType> EXPLORER = REGISTERER.register(
            "explorer",
            SFMExplorerScreenType::new
    );

{% endif %}
{% endif %}
{% endif %}
{% if features.review_sessions %}
{% if features.workspace_panel_reopening %}
{% if features.client_actions %}
    public static final SFMRegistryObject<SFMClientScreenType, SFMReviewExplorerScreenType> REVIEW_CHANGES = REGISTERER.register(
            "explorer/changes",
            () -> new SFMReviewExplorerScreenType(SFMReviewExplorerScreenType.Projection.CHANGES)
    );

{% endif %}
{% endif %}
{% endif %}
{% if features.review_sessions %}
{% if features.workspace_panel_reopening %}
{% if features.client_actions %}
    public static final SFMRegistryObject<SFMClientScreenType, SFMReviewExplorerScreenType> REVIEW_COMMENTS = REGISTERER.register(
            "explorer/comments",
            () -> new SFMReviewExplorerScreenType(SFMReviewExplorerScreenType.Projection.COMMENTS)
    );

{% endif %}
{% endif %}
{% endif %}
{% if features.review_sessions %}
{% if features.workspace_panel_reopening %}
{% if features.client_actions %}
    public static final SFMRegistryObject<SFMClientScreenType, SFMReviewExplorerScreenType> REVIEW_HASHTAGS = REGISTERER.register(
            "explorer/comments/hashtags",
            () -> new SFMReviewExplorerScreenType(SFMReviewExplorerScreenType.Projection.HASHTAGS)
    );

{% endif %}
{% endif %}
{% endif %}
{% if features.release_review %}
{% if features.workspace_panel_reopening %}
{% if features.client_actions %}
    public static final SFMRegistryObject<SFMClientScreenType, SFMReleaseReviewExplorerScreenType>
            RELEASE_REVIEW_CHANGES = REGISTERER.register(
                    "explorer/release_review/changes",
                    () -> new SFMReleaseReviewExplorerScreenType(
                            SFMReleaseReviewExplorerScreenType.Projection.CHANGES)
            );

{% endif %}
{% endif %}
{% endif %}
{% if features.release_review %}
{% if features.workspace_panel_reopening %}
{% if features.client_actions %}
    public static final SFMRegistryObject<SFMClientScreenType, SFMReleaseReviewExplorerScreenType>
            RELEASE_REVIEW_COMMENTS = REGISTERER.register(
                    "explorer/release_review/comments",
                    () -> new SFMReleaseReviewExplorerScreenType(
                            SFMReleaseReviewExplorerScreenType.Projection.COMMENTS)
            );

{% endif %}
{% endif %}
{% endif %}
{% if features.release_review %}
{% if features.workspace_panel_reopening %}
{% if features.client_actions %}
    public static final SFMRegistryObject<SFMClientScreenType, SFMReleaseReviewExplorerScreenType>
            RELEASE_REVIEW_HASHTAGS = REGISTERER.register(
                    "explorer/release_review/comments/hashtags",
                    () -> new SFMReleaseReviewExplorerScreenType(
                            SFMReleaseReviewExplorerScreenType.Projection.HASHTAGS)
            );

{% endif %}
{% endif %}
{% endif %}
{% if features.release_review %}
{% if features.workspace_panel_reopening %}
{% if features.client_actions %}
    public static final SFMRegistryObject<SFMClientScreenType, SFMReleaseReviewExplorerScreenType>
            RELEASE_REVIEW_QUERY = REGISTERER.register(
                    "explorer/release_review/query",
                    () -> new SFMReleaseReviewExplorerScreenType(
                            SFMReleaseReviewExplorerScreenType.Projection.QUERY)
            );

{% endif %}
{% endif %}
{% endif %}
{% if features.release_review %}
{% if features.workspace_panel_reopening %}
{% if features.client_actions %}
    public static final SFMRegistryObject<SFMClientScreenType, SFMReleaseReviewExplorerScreenType>
            RELEASE_REVIEW_STATUS = REGISTERER.register(
                    "explorer/release_review/status",
                    () -> new SFMReleaseReviewExplorerScreenType(
                            SFMReleaseReviewExplorerScreenType.Projection.STATUS)
            );

{% endif %}
{% endif %}
{% endif %}
{% if features.release_review %}
{% if features.workspace_panel_reopening %}
{% if features.client_actions %}
    public static final SFMRegistryObject<SFMClientScreenType, SFMReleaseReviewExplorerScreenType>
            RELEASE_REVIEW_MIGRATIONS = REGISTERER.register(
                    "explorer/release_review/migrations",
                    () -> new SFMReleaseReviewExplorerScreenType(
                            SFMReleaseReviewExplorerScreenType.Projection.MIGRATIONS)
            );

{% endif %}
{% endif %}
{% endif %}
{% endcase %}
    private SFMWorkspaceScreenTypes() {
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
{% else %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
{% endcase %}
    public static void register(IEventBus bus) {
        REGISTERER.register(bus);
    }
}
