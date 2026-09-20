package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import net.minecraftforge.eventbus.api.IEventBus;

/** Registered client-action contributor for the Slice A packet control surface. */
public final class SFMPacketActions {
    private static final SFMDeferredRegister<SFMClientAction<?>> REGISTERER =
            SFMClientActions.createContributor(SFM.MOD_ID);

    public static final SFMRegistryObject<SFMClientAction<?>, SFMPacketListAction> LIST =
            REGISTERER.register("packet/list", SFMPacketListAction::new);

    public static final SFMRegistryObject<SFMClientAction<?>, SFMPacketSendAction> SEND =
            REGISTERER.register("packet/send", SFMPacketSendAction::new);

    public static final SFMRegistryObject<SFMClientAction<?>, SFMPacketRemoteStatusAction> REMOTE_STATUS =
            REGISTERER.register("packet/remote_status", SFMPacketRemoteStatusAction::new);

    private SFMPacketActions() {
    }

    public static void register(IEventBus bus) {
        REGISTERER.register(bus);
    }
}
