//! Test-only inverse of three reviewed registrar guard patches.
//! Current source bytes remain the production renderer input.
use super::provenance::sha256;
use eyre::Result;
use eyre::ensure;

pub(super) fn reviewed_original_registrar(current: &str) -> Result<String> {
    ensure!(
        current.len() == 11965
            && sha256(current.as_bytes())
                == "sha256:cc1f5854216346fe7922ff1b6c1b504becbfdba2ee1f07d9b878fee2a46f9666",
        "current registrar identity changed"
    );
    let mut original = current.to_owned();
    for (current_anchor, original_anchor) in [
        (
            "{% if features.client_properties %}\n{% if features.editor_document_panels %}\n{% if features.canvas_text_editor %}\n    public static final SFMRegistryObject<SFMClientScreenType, SFMDecimalNumberingChamberScreenType>\n            TEMPORAL_NUMBERING_CHAMBER = REGISTERER.register(\n                    \"chamber/temporal-decimal-numbering\",\n                    SFMDecimalNumberingChamberScreenType::new\n            );\n\n{% endif %}\n{% endif %}\n{% endif %}\n",
            "{% if features.client_properties %}\n{% if features.editor_document_panels %}\n    public static final SFMRegistryObject<SFMClientScreenType, SFMDecimalNumberingChamberScreenType>\n            TEMPORAL_NUMBERING_CHAMBER = REGISTERER.register(\n                    \"chamber/temporal-decimal-numbering\",\n                    SFMDecimalNumberingChamberScreenType::new\n            );\n\n{% endif %}\n{% endif %}\n",
        ),
        (
            "{% if features.terminal_properties %}\n{% if features.workspace_widget_hosts %}\n{% if features.client_actions %}\n{% if features.workspace_panel_reopening %}\n    public static final SFMRegistryObject<SFMClientScreenType, SFMTerminalPropertiesScreenType> TERMINAL_PROPERTIES = REGISTERER.register(\n",
            "{% if features.terminal_properties %}\n{% if features.workspace_widget_hosts %}\n{% if features.client_actions %}\n{% if features.workspace_panel_lookup %}\n    public static final SFMRegistryObject<SFMClientScreenType, SFMTerminalPropertiesScreenType> TERMINAL_PROPERTIES = REGISTERER.register(\n",
        ),
        (
            "{% if features.terminal_remote or features.terminal_vox_runtime or features.terminal_properties %}\n{% if features.workspace_widget_hosts %}\n{% if features.client_actions %}\n{% if features.workspace_panel_reopening %}\n    public static final SFMRegistryObject<SFMClientScreenType, SFMTerminalScreenType> TERMINAL = REGISTERER.register(\n            \"terminal\",\n            SFMTerminalScreenType::new\n    );\n\n{% endif %}\n{% endif %}\n{% endif %}\n{% endif %}\n",
            "{% if features.terminal_remote %}\n{% if features.workspace_widget_hosts %}\n{% if features.client_actions %}\n    public static final SFMRegistryObject<SFMClientScreenType, SFMTerminalScreenType> TERMINAL = REGISTERER.register(\n            \"terminal\",\n            SFMTerminalScreenType::new\n    );\n\n{% endif %}\n{% endif %}\n{% endif %}\n",
        ),
        (
            "{% if features.touch_display_terminal_mount %}\n{% if features.client_program_actions and features.client_frame_render and features.client_inbox and features.terminal_keyboard_input and features.terminal_frame_metadata and features.workspace_widget_hosts and features.workspace_panel_reopening and features.client_program_reads %}\n{% if features.terminal_remote or features.terminal_vox_runtime or features.terminal_properties %}\n    public static final SFMRegistryObject<SFMClientScreenType, SFMTerminalMountsScreenType> TERMINAL_MOUNTS =\n            REGISTERER.register(\"terminal_mounts\", SFMTerminalMountsScreenType::new);\n\n{% endif %}\n{% endif %}\n{% endif %}\n",
            "{% if features.touch_display_terminal_mount %}\n    public static final SFMRegistryObject<SFMClientScreenType, SFMTerminalMountsScreenType> TERMINAL_MOUNTS =\n            REGISTERER.register(\"terminal_mounts\", SFMTerminalMountsScreenType::new);\n\n{% endif %}\n",
        ),
        (
            "{% if features.workspace_panel_reopening %}\n{% if features.client_actions %}\n    public static final SFMRegistryObject<SFMClientScreenType, SFMReleaseReviewExplorerScreenType>\n            RELEASE_REVIEW_MIGRATIONS = REGISTERER.register(\n                    \"explorer/release_review/migrations\",\n                    () -> new SFMReleaseReviewExplorerScreenType(\n                            SFMReleaseReviewExplorerScreenType.Projection.MIGRATIONS)\n            );\n\n{% endif %}\n{% endif %}\n",
            "    public static final SFMRegistryObject<SFMClientScreenType, SFMReleaseReviewExplorerScreenType>\n            RELEASE_REVIEW_MIGRATIONS = REGISTERER.register(\n                    \"explorer/release_review/migrations\",\n                    () -> new SFMReleaseReviewExplorerScreenType(\n                            SFMReleaseReviewExplorerScreenType.Projection.MIGRATIONS)\n            );\n\n",
        ),
        (
            "{% if features.workspace_panel_reopening %}\n{% if features.client_actions %}\n    public static final SFMRegistryObject<SFMClientScreenType, SFMReleaseReviewExplorerScreenType>\n            RELEASE_REVIEW_STATUS = REGISTERER.register(\n                    \"explorer/release_review/status\",\n                    () -> new SFMReleaseReviewExplorerScreenType(\n                            SFMReleaseReviewExplorerScreenType.Projection.STATUS)\n            );\n\n{% endif %}\n{% endif %}\n",
            "    public static final SFMRegistryObject<SFMClientScreenType, SFMReleaseReviewExplorerScreenType>\n            RELEASE_REVIEW_STATUS = REGISTERER.register(\n                    \"explorer/release_review/status\",\n                    () -> new SFMReleaseReviewExplorerScreenType(\n                            SFMReleaseReviewExplorerScreenType.Projection.STATUS)\n            );\n\n",
        ),
        (
            "{% if features.workspace_panel_reopening %}\n{% if features.client_actions %}\n    public static final SFMRegistryObject<SFMClientScreenType, SFMReleaseReviewExplorerScreenType>\n            RELEASE_REVIEW_QUERY = REGISTERER.register(\n                    \"explorer/release_review/query\",\n                    () -> new SFMReleaseReviewExplorerScreenType(\n                            SFMReleaseReviewExplorerScreenType.Projection.QUERY)\n            );\n\n{% endif %}\n{% endif %}\n",
            "    public static final SFMRegistryObject<SFMClientScreenType, SFMReleaseReviewExplorerScreenType>\n            RELEASE_REVIEW_QUERY = REGISTERER.register(\n                    \"explorer/release_review/query\",\n                    () -> new SFMReleaseReviewExplorerScreenType(\n                            SFMReleaseReviewExplorerScreenType.Projection.QUERY)\n            );\n\n",
        ),
        (
            "{% if features.workspace_panel_reopening %}\n{% if features.client_actions %}\n    public static final SFMRegistryObject<SFMClientScreenType, SFMReleaseReviewExplorerScreenType>\n            RELEASE_REVIEW_HASHTAGS = REGISTERER.register(\n                    \"explorer/release_review/comments/hashtags\",\n                    () -> new SFMReleaseReviewExplorerScreenType(\n                            SFMReleaseReviewExplorerScreenType.Projection.HASHTAGS)\n            );\n\n{% endif %}\n{% endif %}\n",
            "    public static final SFMRegistryObject<SFMClientScreenType, SFMReleaseReviewExplorerScreenType>\n            RELEASE_REVIEW_HASHTAGS = REGISTERER.register(\n                    \"explorer/release_review/comments/hashtags\",\n                    () -> new SFMReleaseReviewExplorerScreenType(\n                            SFMReleaseReviewExplorerScreenType.Projection.HASHTAGS)\n            );\n\n",
        ),
        (
            "{% if features.workspace_panel_reopening %}\n{% if features.client_actions %}\n    public static final SFMRegistryObject<SFMClientScreenType, SFMReleaseReviewExplorerScreenType>\n            RELEASE_REVIEW_COMMENTS = REGISTERER.register(\n                    \"explorer/release_review/comments\",\n                    () -> new SFMReleaseReviewExplorerScreenType(\n                            SFMReleaseReviewExplorerScreenType.Projection.COMMENTS)\n            );\n\n{% endif %}\n{% endif %}\n",
            "    public static final SFMRegistryObject<SFMClientScreenType, SFMReleaseReviewExplorerScreenType>\n            RELEASE_REVIEW_COMMENTS = REGISTERER.register(\n                    \"explorer/release_review/comments\",\n                    () -> new SFMReleaseReviewExplorerScreenType(\n                            SFMReleaseReviewExplorerScreenType.Projection.COMMENTS)\n            );\n\n",
        ),
        (
            "{% if features.workspace_panel_reopening %}\n{% if features.client_actions %}\n    public static final SFMRegistryObject<SFMClientScreenType, SFMReleaseReviewExplorerScreenType>\n            RELEASE_REVIEW_CHANGES = REGISTERER.register(\n                    \"explorer/release_review/changes\",\n                    () -> new SFMReleaseReviewExplorerScreenType(\n                            SFMReleaseReviewExplorerScreenType.Projection.CHANGES)\n            );\n\n{% endif %}\n{% endif %}\n",
            "    public static final SFMRegistryObject<SFMClientScreenType, SFMReleaseReviewExplorerScreenType>\n            RELEASE_REVIEW_CHANGES = REGISTERER.register(\n                    \"explorer/release_review/changes\",\n                    () -> new SFMReleaseReviewExplorerScreenType(\n                            SFMReleaseReviewExplorerScreenType.Projection.CHANGES)\n            );\n\n",
        ),
        (
            "{% if features.workspace_panel_reopening %}\n{% if features.client_actions %}\n    public static final SFMRegistryObject<SFMClientScreenType, SFMReviewExplorerScreenType> REVIEW_HASHTAGS = REGISTERER.register(\n            \"explorer/comments/hashtags\",\n            () -> new SFMReviewExplorerScreenType(SFMReviewExplorerScreenType.Projection.HASHTAGS)\n    );\n\n{% endif %}\n{% endif %}\n",
            "    public static final SFMRegistryObject<SFMClientScreenType, SFMReviewExplorerScreenType> REVIEW_HASHTAGS = REGISTERER.register(\n            \"explorer/comments/hashtags\",\n            () -> new SFMReviewExplorerScreenType(SFMReviewExplorerScreenType.Projection.HASHTAGS)\n    );\n\n",
        ),
        (
            "{% if features.workspace_panel_reopening %}\n{% if features.client_actions %}\n    public static final SFMRegistryObject<SFMClientScreenType, SFMReviewExplorerScreenType> REVIEW_COMMENTS = REGISTERER.register(\n            \"explorer/comments\",\n            () -> new SFMReviewExplorerScreenType(SFMReviewExplorerScreenType.Projection.COMMENTS)\n    );\n\n{% endif %}\n{% endif %}\n",
            "    public static final SFMRegistryObject<SFMClientScreenType, SFMReviewExplorerScreenType> REVIEW_COMMENTS = REGISTERER.register(\n            \"explorer/comments\",\n            () -> new SFMReviewExplorerScreenType(SFMReviewExplorerScreenType.Projection.COMMENTS)\n    );\n\n",
        ),
        (
            "{% if features.workspace_panel_reopening %}\n{% if features.client_actions %}\n    public static final SFMRegistryObject<SFMClientScreenType, SFMReviewExplorerScreenType> REVIEW_CHANGES = REGISTERER.register(\n            \"explorer/changes\",\n            () -> new SFMReviewExplorerScreenType(SFMReviewExplorerScreenType.Projection.CHANGES)\n    );\n\n{% endif %}\n{% endif %}\n",
            "    public static final SFMRegistryObject<SFMClientScreenType, SFMReviewExplorerScreenType> REVIEW_CHANGES = REGISTERER.register(\n            \"explorer/changes\",\n            () -> new SFMReviewExplorerScreenType(SFMReviewExplorerScreenType.Projection.CHANGES)\n    );\n\n",
        ),
        (
            "{% if features.workspace_panel_reopening %}\n{% if features.client_actions %}\n    public static final SFMRegistryObject<SFMClientScreenType, SFMExplorerScreenType> EXPLORER = REGISTERER.register(\n            \"explorer\",\n            SFMExplorerScreenType::new\n    );\n\n{% endif %}\n{% endif %}\n",
            "    public static final SFMRegistryObject<SFMClientScreenType, SFMExplorerScreenType> EXPLORER = REGISTERER.register(\n            \"explorer\",\n            SFMExplorerScreenType::new\n    );\n\n",
        ),
        (
            "{% if features.workspace_panel_reopening %}\n{% if features.client_actions %}\n    public static final SFMRegistryObject<SFMClientScreenType, SFMGrammarScreenType> GRAMMAR = REGISTERER.register(\n            \"grammar\",\n            SFMGrammarScreenType::new\n    );\n\n{% endif %}\n{% endif %}\n",
            "    public static final SFMRegistryObject<SFMClientScreenType, SFMGrammarScreenType> GRAMMAR = REGISTERER.register(\n            \"grammar\",\n            SFMGrammarScreenType::new\n    );\n\n",
        ),
        (
            "{% if features.workspace_panel_reopening %}\n{% if features.client_actions %}\n    public static final SFMRegistryObject<SFMClientScreenType, SFMTextEditorScreenType> TEXT_EDITOR = REGISTERER.register(\n            \"text_editor\",\n            SFMTextEditorScreenType::new\n    );\n\n{% endif %}\n{% endif %}\n",
            "    public static final SFMRegistryObject<SFMClientScreenType, SFMTextEditorScreenType> TEXT_EDITOR = REGISTERER.register(\n            \"text_editor\",\n            SFMTextEditorScreenType::new\n    );\n\n",
        ),
        (
            "{% if features.workspace_panel_reopening %}\n{% if features.client_actions %}\n{% if features.client_properties %}\n{% if features.editor_document_panels %}\n    public static final SFMRegistryObject<SFMClientScreenType, SFMWorkspaceCounterfactualScreenType>\n            WORKSPACE_COUNTERFACTUAL_CHAMBER = REGISTERER.register(\n                    \"chamber/workspace-counterfactual\",\n                    SFMWorkspaceCounterfactualScreenType::new\n            );\n\n{% endif %}\n{% endif %}\n{% endif %}\n{% endif %}\n",
            "    public static final SFMRegistryObject<SFMClientScreenType, SFMWorkspaceCounterfactualScreenType>\n            WORKSPACE_COUNTERFACTUAL_CHAMBER = REGISTERER.register(\n                    \"chamber/workspace-counterfactual\",\n                    SFMWorkspaceCounterfactualScreenType::new\n            );\n\n",
        ),
        (
            "{% if features.workspace_panel_reopening %}\n{% if features.client_actions %}\n{% if features.client_properties %}\n{% if features.editor_document_panels %}\n    public static final SFMRegistryObject<SFMClientScreenType, SFMDecimalNumberingChamberScreenType>\n            TEMPORAL_NUMBERING_CHAMBER = REGISTERER.register(\n                    \"chamber/temporal-decimal-numbering\",\n                    SFMDecimalNumberingChamberScreenType::new\n            );\n\n{% endif %}\n{% endif %}\n{% endif %}\n{% endif %}\n",
            "    public static final SFMRegistryObject<SFMClientScreenType, SFMDecimalNumberingChamberScreenType>\n            TEMPORAL_NUMBERING_CHAMBER = REGISTERER.register(\n                    \"chamber/temporal-decimal-numbering\",\n                    SFMDecimalNumberingChamberScreenType::new\n            );\n\n",
        ),
        (
            "{% if features.workspace_panel_reopening %}\n{% if features.client_actions %}\n{% if features.client_theme %}\n    public static final SFMRegistryObject<SFMClientScreenType, SFMCandidateHistoryScreenType> CANDIDATE_HISTORY =\n            REGISTERER.register(\n                    \"episode/candidate-history\",\n                    SFMCandidateHistoryScreenType::new\n            );\n\n{% endif %}\n{% endif %}\n{% endif %}\n",
            "    public static final SFMRegistryObject<SFMClientScreenType, SFMCandidateHistoryScreenType> CANDIDATE_HISTORY =\n            REGISTERER.register(\n                    \"episode/candidate-history\",\n                    SFMCandidateHistoryScreenType::new\n            );\n\n",
        ),
        (
            "{% if features.workspace_panel_reopening %}\n{% if features.client_actions %}\n    public static final SFMRegistryObject<SFMClientScreenType, SFMHistoryGraphScreenType> HISTORY_GRAPH = REGISTERER.register(\n            \"episode/history\",\n            SFMHistoryGraphScreenType::new\n    );\n\n{% endif %}\n{% endif %}\n",
            "    public static final SFMRegistryObject<SFMClientScreenType, SFMHistoryGraphScreenType> HISTORY_GRAPH = REGISTERER.register(\n            \"episode/history\",\n            SFMHistoryGraphScreenType::new\n    );\n\n",
        ),
    ] {
        ensure!(
            original.matches(current_anchor).count() == 1,
            "reviewed registrar guard anchor changed"
        );
        original = original.replacen(current_anchor, original_anchor, 1);
    }
    ensure!(
        original.len() == 9525
            && sha256(original.as_bytes())
                == "sha256:9e58ba1b548b45c59fc773e4d692ab750e473300d896ce682732a40597ca70c8",
        "original registrar pin changed"
    );
    Ok(original)
}
