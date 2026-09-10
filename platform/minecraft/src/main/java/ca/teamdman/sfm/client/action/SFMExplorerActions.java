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
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerRefreshAction> REFRESH =
            REGISTERER.register("explorer/refresh", SFMExplorerRefreshAction::new);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerParentAction> PARENT =
            REGISTERER.register("explorer/root/parent/set", SFMExplorerParentAction::new);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerAction> ROOT_REMOVE = register(
            "explorer/root/remove", SFMExplorerAction.Operation.ROOT_REMOVE);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerLocationEditAction> LOCATION_EDIT =
            REGISTERER.register("explorer/location/edit", SFMExplorerLocationEditAction::new);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerLocationSetAction> LOCATION_SET =
            REGISTERER.register("explorer/location/set", SFMExplorerLocationSetAction::new);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerLocationCopyAction> LOCATION_COPY =
            REGISTERER.register("explorer/location/copy", SFMExplorerLocationCopyAction::new);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerRowCopyDetailsAction> ROW_DETAILS_COPY =
            REGISTERER.register("explorer/row/details/copy", SFMExplorerRowCopyDetailsAction::new);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMItemstackPreviewRuleAction> PREVIEW_RULE_ADD =
            REGISTERER.register("explorer/itemstack_preview_rule/add", SFMItemstackPreviewRuleAction::new);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMItemstackPreviewRulePromptAction> PREVIEW_RULE_PROMPT =
            REGISTERER.register("explorer/itemstack_preview_rule/prompt/copy", SFMItemstackPreviewRulePromptAction::new);
    static {
        for (var kind : SFMExplorerCompactAction.Kind.values())
            REGISTERER.register(kind.path, () -> new SFMExplorerCompactAction(kind));
        for (var kind : SFMExplorerSearchAction.Kind.values())
            REGISTERER.register(kind.path, () -> new SFMExplorerSearchAction(kind));
        for (var kind : SFMItemstackPreviewInspectionAction.Kind.values())
            REGISTERER.register(kind.path, () -> new SFMItemstackPreviewInspectionAction(kind));
        for (var kind : SFMItemstackPreviewRuleToolsAction.Kind.values())
            REGISTERER.register(kind.path, () -> new SFMItemstackPreviewRuleToolsAction(kind));
    }
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
    public static final SFMRegistryObject<SFMClientAction<?>, SFMPathOpenAction> PATH_OPEN =
            REGISTERER.register("path/open", SFMPathOpenAction::new);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMRevealInExplorerAction> REVEAL =
            REGISTERER.register("explorer/reveal", SFMRevealInExplorerAction::new);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMRevealHereAction> REVEAL_HERE =
            REGISTERER.register("explorer/reveal/here", SFMRevealHereAction::new);

    private SFMExplorerActions() {
    }

    private static SFMRegistryObject<SFMClientAction<?>, SFMExplorerAction> register(
            String id,
            SFMExplorerAction.Operation operation
    ) {
        return REGISTERER.register(id, () -> new SFMExplorerAction(operation));
    }

    public static void register(IEventBus bus) {
        REGISTERER.register(bus);
    }
}
