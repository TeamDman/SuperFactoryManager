package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import net.minecraftforge.eventbus.api.IEventBus;

public final class SFMTerminalDisplayActions {
    private static final SFMDeferredRegister<SFMClientAction<?>> REGISTERER = SFMClientActions.createContributor("sfm");
    static {
{% if features.client_program_actions and features.client_frame_render and features.client_inbox and features.terminal_keyboard_input and features.terminal_frame_metadata and features.client_program_reads %}
{% if features.terminal_remote or features.terminal_vox_runtime or features.terminal_properties %}
        REGISTERER.register("terminal/display", () -> new SFMTerminalDisplayAction(SFMTerminalDisplayAction.Kind.DISPLAY));
        REGISTERER.register("terminal/input/status", () -> new SFMTerminalDisplayAction(SFMTerminalDisplayAction.Kind.INPUT_STATUS));
        REGISTERER.register("terminal/input", () -> new SFMTerminalDisplayAction(SFMTerminalDisplayAction.Kind.INPUT_EFFECT));
{% endif %}
{% endif %}
{% if features.client_program_actions and features.client_frame_render and features.client_inbox and features.terminal_keyboard_input and features.terminal_frame_metadata and features.client_program_reads %}
{% if features.terminal_remote or features.terminal_vox_runtime or features.terminal_properties %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.workspace_panel_lookup %}
        REGISTERER.register("terminal/mounts/control", SFMTerminalMountsControlAction::new);
{% endif %}
{% endif %}
{% endif %}
    }
    private SFMTerminalDisplayActions() {}
    public static void register(IEventBus bus) { REGISTERER.register(bus); }
}
