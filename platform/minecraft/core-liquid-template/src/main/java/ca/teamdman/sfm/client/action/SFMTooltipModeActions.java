package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.client.tooltip.SFMTooltipModeService;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import net.minecraftforge.eventbus.api.IEventBus;

public final class SFMTooltipModeActions {
    private static final SFMDeferredRegister<SFMClientAction<?>> REGISTERER = SFMClientActions.createContributor("sfm");
    static {
        REGISTERER.register("tooltip/more_info/expand", () -> new SFMTooltipModeAction(SFMTooltipModeService.Mode.EXPANDED));
        REGISTERER.register("tooltip/more_info/compact", () -> new SFMTooltipModeAction(SFMTooltipModeService.Mode.COMPACT));
        REGISTERER.register("tooltip/more_info/reset", () -> new SFMTooltipModeAction(SFMTooltipModeService.Mode.AUTO));
    }
    private SFMTooltipModeActions() {}
    public static void register(IEventBus bus) { REGISTERER.register(bus); }
}
