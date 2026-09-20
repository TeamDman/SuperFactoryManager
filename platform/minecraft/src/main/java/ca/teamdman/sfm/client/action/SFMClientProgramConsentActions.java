package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import net.minecraftforge.eventbus.api.IEventBus;

public final class SFMClientProgramConsentActions {
    private static final SFMDeferredRegister<SFMClientAction<?>> REGISTERER = SFMClientActions.createContributor("sfm");
    static {
        REGISTERER.register("client_program/consent/status", () -> new SFMClientProgramConsentAction(false));
        REGISTERER.register("client_program/consent/request", () -> new SFMClientProgramConsentAction(true));
        REGISTERER.register("client_program/consents/control", SFMClientProgramConsentControlAction::new);
    }
    private SFMClientProgramConsentActions() {}
    public static void register(IEventBus bus) { REGISTERER.register(bus); }
}
