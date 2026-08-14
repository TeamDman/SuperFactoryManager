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
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerAction> ROOT_REMOVE = register(
            "explorer/root/remove", SFMExplorerAction.Operation.ROOT_REMOVE);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerLocationEditAction> LOCATION_EDIT =
            REGISTERER.register("explorer/location/edit", SFMExplorerLocationEditAction::new);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerLocationSetAction> LOCATION_SET =
            REGISTERER.register("explorer/location/set", SFMExplorerLocationSetAction::new);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerAction> VIEW_SET = register(
            "explorer/view/set", SFMExplorerAction.Operation.VIEW_SET);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerAction> SORT_SET = register(
            "explorer/sort/set", SFMExplorerAction.Operation.SORT_SET);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerAction> GROUP_SET = register(
            "explorer/group/set", SFMExplorerAction.Operation.GROUP_SET);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMExplorerAction> ROOT_HOIST_SET = register(
            "explorer/root/hoist/set", SFMExplorerAction.Operation.HOIST_SET);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMPathOpenAction> PATH_OPEN =
            REGISTERER.register("path/open", SFMPathOpenAction::new);

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
