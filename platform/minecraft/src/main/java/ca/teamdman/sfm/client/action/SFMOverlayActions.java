package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import net.minecraftforge.eventbus.api.IEventBus;

/** Registered overlay-scene controls shared by the palette and external {@code sfm.exe invoke}. */
public final class SFMOverlayActions {
    private static final SFMDeferredRegister<SFMClientAction<?>> REGISTERER =
            SFMClientActions.createContributor(SFM.MOD_ID);

    public static final SFMRegistryObject<SFMClientAction<?>, SFMOverlayAction> VISIBILITY_SET = register(
            "overlay/visibility/set", SFMOverlayAction.Kind.VISIBILITY_SET);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMOverlayAction> VISIBILITY_TOGGLE = register(
            "overlay/visibility/toggle", SFMOverlayAction.Kind.VISIBILITY_TOGGLE);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMOverlayAction> PLACEMENT_SET = register(
            "overlay/placement/set", SFMOverlayAction.Kind.PLACEMENT_SET);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMOverlayAction> INPUT_MODE_SET = register(
            "overlay/input-mode/set", SFMOverlayAction.Kind.INPUT_MODE_SET);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMOverlayAction> FOCUS_ACQUIRE = register(
            "overlay/focus/acquire", SFMOverlayAction.Kind.FOCUS_ACQUIRE);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMOverlayAction> FOCUS_RELEASE = register(
            "overlay/focus/release", SFMOverlayAction.Kind.FOCUS_RELEASE);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMOverlayAction> Z_ORDER_SET = register(
            "overlay/z-order/set", SFMOverlayAction.Kind.Z_ORDER_SET);

    private SFMOverlayActions() {
    }

    private static SFMRegistryObject<SFMClientAction<?>, SFMOverlayAction> register(
            String id,
            SFMOverlayAction.Kind kind
    ) {
        return REGISTERER.register(id, () -> new SFMOverlayAction(kind));
    }

    public static void register(IEventBus bus) {
        REGISTERER.register(bus);
    }
}
