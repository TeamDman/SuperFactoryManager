package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import net.minecraftforge.eventbus.api.IEventBus;

/** Registered semantic explorer controls shared by widgets and the palette. */
public final class SFMExplorerActions {
    private static final SFMDeferredRegister<SFMClientAction<?>> REGISTERER =
            SFMClientActions.createContributor(SFM.MOD_ID);

{% if features.file_explorer or features.registry_explorer or features.explorer_search or features.explorer_compaction %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerAction> NODE_EXPAND = register(
            "explorer/node/expand", SFMExplorerAction.Operation.NODE_EXPAND);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerAction> NODE_COLLAPSE = register(
            "explorer/node/collapse", SFMExplorerAction.Operation.NODE_COLLAPSE);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerAction> NODE_TOGGLE = register(
            "explorer/node/toggle", SFMExplorerAction.Operation.NODE_TOGGLE);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerAction> NODE_REFRESH = register(
            "explorer/node/refresh", SFMExplorerAction.Operation.NODE_REFRESH);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerAction> ROOT_ADD = register(
            "explorer/root/add", SFMExplorerAction.Operation.ROOT_ADD);
{% endif %}
{% if features.explorer_navigation and features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerRefreshAction> REFRESH =
            REGISTERER.register("explorer/refresh", SFMExplorerRefreshAction::new);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerParentAction> PARENT =
            REGISTERER.register("explorer/root/parent/set", SFMExplorerParentAction::new);
{% endif %}
{% if features.file_explorer or features.registry_explorer or features.explorer_search or features.explorer_compaction %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerAction> ROOT_REMOVE = register(
            "explorer/root/remove", SFMExplorerAction.Operation.ROOT_REMOVE);
{% endif %}
{% if features.explorer_navigation and features.canvas_text_editor and features.editor_document_panels and features.workspace_panel_actions and features.workspace_panel_reopening and features.workspace_directional_opening %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerLocationEditAction> LOCATION_EDIT =
            REGISTERER.register("explorer/location/edit", SFMExplorerLocationEditAction::new);
{% endif %}
{% if features.explorer_navigation %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerLocationSetAction> LOCATION_SET =
            REGISTERER.register("explorer/location/set", SFMExplorerLocationSetAction::new);
{% endif %}
{% if features.explorer_navigation and features.clipboard_action_commands %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerLocationCopyAction> LOCATION_COPY =
            REGISTERER.register("explorer/location/copy", SFMExplorerLocationCopyAction::new);
{% endif %}
{% if features.clipboard_action_commands %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerRowCopyDetailsAction> ROW_DETAILS_COPY =
            REGISTERER.register("explorer/row/details/copy", SFMExplorerRowCopyDetailsAction::new);
{% endif %}
{% if features.clipboard_action_commands and features.theme_preview_rules %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerRowCopySummaryAction> ROW_SUMMARY_COPY =
            REGISTERER.register("explorer/row/summary/copy", SFMExplorerRowCopySummaryAction::new);
{% endif %}
{% if features.theme_preview_rules %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMItemstackPreviewRuleAction> PREVIEW_RULE_ADD =
            REGISTERER.register("explorer/itemstack_preview_rule/add", SFMItemstackPreviewRuleAction::new);
{% endif %}
{% if features.theme_preview_rules and features.clipboard_action_commands %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMItemstackPreviewRulePromptAction> PREVIEW_RULE_PROMPT =
            REGISTERER.register("explorer/itemstack_preview_rule/prompt/copy", SFMItemstackPreviewRulePromptAction::new);
{% endif %}
{% if features.theme_preview_rules or features.workspace_panel_actions %}
{% if features.theme_preview_rules or features.explorer_compaction or features.explorer_search %}
    static {
{% if features.clipboard_action_commands and features.theme_preview_rules %}
        for (var kind : SFMExplorerCollectionCopySummaryAction.Kind.values())
            REGISTERER.register(kind.path(), () -> new SFMExplorerCollectionCopySummaryAction(kind));
{% endif %}
{% if features.explorer_compaction and features.workspace_panel_actions %}
        for (var kind : SFMExplorerCompactAction.Kind.values())
            REGISTERER.register(kind.path, () -> new SFMExplorerCompactAction(kind));
{% endif %}
{% if features.explorer_search and features.workspace_panel_actions %}
        for (var kind : SFMExplorerSearchAction.Kind.values())
            REGISTERER.register(kind.path, () -> new SFMExplorerSearchAction(kind));
{% endif %}
{% if features.theme_preview_rules %}
{% if features.clipboard_action_commands or features.editor_document_panels and features.canvas_text_editor and features.workspace_panel_actions and features.workspace_panel_reopening and features.workspace_stack_controls %}
        for (var kind : SFMItemstackPreviewInspectionAction.Kind.values())
            REGISTERER.register(kind.path, () -> new SFMItemstackPreviewInspectionAction(kind));
{% endif %}
{% endif %}
{% if features.theme_preview_rules %}
        for (var kind : SFMItemstackPreviewRuleToolsAction.Kind.values())
            REGISTERER.register(kind.path, () -> new SFMItemstackPreviewRuleToolsAction(kind));
{% endif %}
    }
{% endif %}
{% endif %}
{% if features.file_explorer or features.registry_explorer or features.explorer_search or features.explorer_compaction %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerAction> VIEW_SET = register(
            "explorer/view/set", SFMExplorerAction.Operation.VIEW_SET);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerAction> SORT_SET = register(
            "explorer/sort/set", SFMExplorerAction.Operation.SORT_SET);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerAction> GROUP_SET = register(
            "explorer/group/set", SFMExplorerAction.Operation.GROUP_SET);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerAction> ROOT_HOIST_SET = register(
            "explorer/root/hoist/set", SFMExplorerAction.Operation.HOIST_SET);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerAction> PATH_DISPLAY_SET = register(
            "explorer/path-display/set", SFMExplorerAction.Operation.PATH_DISPLAY_SET);
{% endif %}
{% if features.explorer_search %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerAction> FILTER_SET = register(
            "explorer/filter/set", SFMExplorerAction.Operation.FILTER_SET);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerAction> FILTER_MATCH = register(
            "explorer/filter/match", SFMExplorerAction.Operation.FILTER_MATCH);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerAction> FILTER_CLEAR = register(
            "explorer/filter/clear", SFMExplorerAction.Operation.FILTER_CLEAR);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerAction> FIND_SET = register(
            "explorer/find/set", SFMExplorerAction.Operation.FIND_SET);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerAction> FIND_MATCH = register(
            "explorer/find/match", SFMExplorerAction.Operation.FIND_MATCH);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerAction> FIND_NEXT = register(
            "explorer/find/next", SFMExplorerAction.Operation.FIND_NEXT);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerAction> FIND_PREVIOUS = register(
            "explorer/find/previous", SFMExplorerAction.Operation.FIND_PREVIOUS);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerAction> FIND_CLEAR = register(
            "explorer/find/clear", SFMExplorerAction.Operation.FIND_CLEAR);
{% endif %}
{% if features.file_explorer or features.java_symbols or features.release_review or features.workspace_counterfactuals %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMPathOpenAction> PATH_OPEN =
            REGISTERER.register("path/open", SFMPathOpenAction::new);
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.file_explorer or features.java_symbols or features.release_review or features.workspace_counterfactuals %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMRevealInExplorerAction> REVEAL =
            REGISTERER.register("explorer/reveal", SFMRevealInExplorerAction::new);
{% endif %}
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.explorer_navigation or features.release_review %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMRevealHereAction> REVEAL_HERE =
            REGISTERER.register("explorer/reveal/here", SFMRevealHereAction::new);

{% endif %}
{% endif %}
    private SFMExplorerActions() {
    }

{% if features.file_explorer or features.registry_explorer or features.explorer_search or features.explorer_compaction %}
    private static SFMRegistryObject<SFMClientAction<?>, SFMExplorerAction> register(
            String id,
            SFMExplorerAction.Operation operation
    ) {
        return REGISTERER.register(id, () -> new SFMExplorerAction(operation));
    }

{% endif %}
    public static void register(IEventBus bus) {
        REGISTERER.register(bus);
    }
}
