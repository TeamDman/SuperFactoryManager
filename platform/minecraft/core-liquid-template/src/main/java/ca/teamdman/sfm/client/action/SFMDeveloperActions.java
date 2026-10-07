package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMClientActions;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% else %}
{% if features.developer_tools %}
import ca.teamdman.sfm.client.screen.SFMTitleScreenDevScreen;
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

public final class SFMDeveloperActions {
    private static final SFMDeferredRegister<SFMClientAction<?>> REGISTERER =
            SFMClientActions.createContributor(SFM.MOD_ID);

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.developer_tools and features.workspace_panel_actions and features.editor_document_panels and features.workspace_panel_reopening %}
    public static final SFMRegistryObject<SFMClientAction<?>, OpenDeveloperPanelAction> TEXT_EDITOR =
            REGISTERER.register(
                    "developer/open_text_editor",
                    () -> new OpenDeveloperPanelAction(OpenDeveloperPanelAction.Scene.TEXT_EDITOR)
            );

{% endif %}
{% else %}
{% if features.developer_tools %}
    public static final SFMRegistryObject<SFMClientAction<?>, OpenTitleScreenDevScreenAction> TEXT_EDITOR =
            REGISTERER.register(
                    "developer/open_text_editor",
                    () -> new OpenTitleScreenDevScreenAction(SFMTitleScreenDevScreen.TEXT_EDITOR)
            );

{% endif %}
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.developer_tools and features.workspace_panel_actions and features.input_diagnostics_panel %}
    public static final SFMRegistryObject<SFMClientAction<?>, OpenDeveloperPanelAction> INPUT_DIAGNOSTICS =
            REGISTERER.register(
                    "developer/open_input_diagnostics",
                    () -> new OpenDeveloperPanelAction(OpenDeveloperPanelAction.Scene.INPUT_DIAGNOSTICS)
            );

{% endif %}
{% else %}
{% if features.developer_tools and features.input_diagnostics_screen %}
    public static final SFMRegistryObject<SFMClientAction<?>, OpenTitleScreenDevScreenAction> INPUT_DIAGNOSTICS =
            REGISTERER.register(
                    "developer/open_input_diagnostics",
                    () -> new OpenTitleScreenDevScreenAction(SFMTitleScreenDevScreen.INPUT_DIAG)
            );

{% endif %}
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% else %}
{% if features.developer_tools and features.canvas_text_editor %}
    public static final SFMRegistryObject<SFMClientAction<?>, OpenTitleScreenDevScreenAction> DRAW_CANVAS =
            REGISTERER.register(
                    "developer/open_draw_canvas",
                    () -> new OpenTitleScreenDevScreenAction(SFMTitleScreenDevScreen.DRAW_CANVAS)
            );

{% endif %}
{% if features.developer_tools and features.legacy_file_explorer %}
    public static final SFMRegistryObject<SFMClientAction<?>, OpenTitleScreenDevScreenAction> FILE_EXPLORER =
            REGISTERER.register(
                    "developer/open_file_explorer",
                    () -> new OpenTitleScreenDevScreenAction(SFMTitleScreenDevScreen.FILE_EXPLORER)
            );

{% endif %}
{% if features.developer_tools and features.legacy_file_explorer %}
    public static final SFMRegistryObject<SFMClientAction<?>, OpenTitleScreenDevScreenAction> INSTANCE_FILE_EXPLORER =
            REGISTERER.register(
                    "developer/open_instance_file_explorer",
                    () -> new OpenTitleScreenDevScreenAction(SFMTitleScreenDevScreen.INSTANCE_FILE_EXPLORER)
            );

{% endif %}
{% if features.developer_tools and features.item_picker %}
    public static final SFMRegistryObject<SFMClientAction<?>, OpenTitleScreenDevScreenAction> ITEM_ICON_PICKER =
            REGISTERER.register(
                    "developer/open_item_icon_picker",
                    () -> new OpenTitleScreenDevScreenAction(SFMTitleScreenDevScreen.ITEM_ICON_PICKER)
            );

{% endif %}
{% if features.developer_tools and features.legacy_source_review_ui %}
    public static final SFMRegistryObject<SFMClientAction<?>, OpenTitleScreenDevScreenAction> SOURCE_REVIEW =
            REGISTERER.register(
                    "developer/open_source_review",
                    () -> new OpenTitleScreenDevScreenAction(SFMTitleScreenDevScreen.SOURCE_REVIEW)
            );
{% endif %}
{% if features.developer_tools and features.legacy_comment_review_ui %}
    public static final SFMRegistryObject<SFMClientAction<?>, OpenTitleScreenDevScreenAction> COMMENT_REVIEW =
            REGISTERER.register("developer/open_comment_review",
                    () -> new OpenTitleScreenDevScreenAction(SFMTitleScreenDevScreen.COMMENT_REVIEW));

{% endif %}
{% endcase %}
{% if features.developer_world_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, CreateDeveloperWorldAction> CREATE_WORLD =
            REGISTERER.register(
                    "developer/create_world",
                    () -> new CreateDeveloperWorldAction(false)
            );

    public static final SFMRegistryObject<SFMClientAction<?>, CreateDeveloperWorldAction> CREATE_WORLD_AND_RUN_GAME_TESTS =
            REGISTERER.register(
                    "developer/create_world_and_run_game_tests",
                    () -> new CreateDeveloperWorldAction(true)
            );

{% endif %}
    private SFMDeveloperActions() {
    }

    public static void register(IEventBus bus) {
        REGISTERER.register(bus);
    }
}
