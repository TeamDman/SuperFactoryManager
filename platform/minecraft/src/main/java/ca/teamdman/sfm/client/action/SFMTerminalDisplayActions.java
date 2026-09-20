package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import net.minecraftforge.eventbus.api.IEventBus;

public final class SFMTerminalDisplayActions {
    private static final SFMDeferredRegister<SFMClientAction<?>> REGISTERER = SFMClientActions.createContributor("sfm");
    static {
        REGISTERER.register("terminal/display", () -> new SFMTerminalDisplayAction(SFMTerminalDisplayAction.Kind.DISPLAY));
        REGISTERER.register("terminal/input/status", () -> new SFMTerminalDisplayAction(SFMTerminalDisplayAction.Kind.INPUT_STATUS));
        REGISTERER.register("terminal/input", () -> new SFMTerminalDisplayAction(SFMTerminalDisplayAction.Kind.INPUT_EFFECT));
        REGISTERER.register("terminal/mounts/control", SFMTerminalMountsControlAction::new);
    }
    private SFMTerminalDisplayActions() {}
    public static void register(IEventBus bus) { REGISTERER.register(bus); }
}
