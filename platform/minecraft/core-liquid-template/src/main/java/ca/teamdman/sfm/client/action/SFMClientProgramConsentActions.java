package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import net.minecraftforge.eventbus.api.IEventBus;

public final class SFMClientProgramConsentActions {
    private static final SFMDeferredRegister<SFMClientAction<?>> REGISTERER = SFMClientActions.createContributor("sfm");
    static {
{% if features.client_program_actions %}
        REGISTERER.register("client_program/consent/status", () -> new SFMClientProgramConsentAction(false));
        REGISTERER.register("client_program/consent/request", () -> new SFMClientProgramConsentAction(true));
{% endif %}
{% if features.workspace_panels %}
{% if features.workspace_panel_lookup %}
{% if features.workspace_widget_hosts %}
{% if features.confirmation_review_callbacks %}
        REGISTERER.register("client_program/consents/control", SFMClientProgramConsentControlAction::new);
{% endif %}
{% endif %}
{% endif %}
{% endif %}
    }
    private SFMClientProgramConsentActions() {}
    public static void register(IEventBus bus) { REGISTERER.register(bus); }
}
