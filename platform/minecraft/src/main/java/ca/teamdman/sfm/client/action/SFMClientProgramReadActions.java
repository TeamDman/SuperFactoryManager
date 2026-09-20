package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import net.minecraftforge.eventbus.api.IEventBus;

public final class SFMClientProgramReadActions {
    private static final SFMDeferredRegister<SFMClientAction<?>> REGISTERER = SFMClientActions.createContributor("sfm");
    static {
        REGISTERER.register("world/block_state/get", () -> new SFMClientProgramReadAction(false));
        REGISTERER.register("client_inbox/read", () -> new SFMClientProgramReadAction(true));
    }
    private SFMClientProgramReadActions() {}
    public static void register(IEventBus bus) { REGISTERER.register(bus); }
}
