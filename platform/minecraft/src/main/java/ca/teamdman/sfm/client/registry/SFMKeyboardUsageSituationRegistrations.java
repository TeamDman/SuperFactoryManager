package ca.teamdman.sfm.client.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.keybinding.SFMKeyboardUsageSituation;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import net.minecraft.network.chat.Component;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.List;

/** SFM's own contributions to the extensible situation registry. */
public final class SFMKeyboardUsageSituationRegistrations {
    private static final SFMDeferredRegister<SFMKeyboardUsageSituation> REGISTERER =
            SFMKeyboardUsageSituations.createContributor(SFM.MOD_ID);

    static {
        REGISTERER.register("global", () -> new SFMKeyboardUsageSituation(
                Component.literal("Global"),
                Component.literal("Active across Minecraft and other mods"),
                List.of()));
        REGISTERER.register("workspace", () -> new SFMKeyboardUsageSituation(
                Component.literal("SFM workspace"),
                Component.literal("Active while an SFM panel workspace is open"),
                List.of(SFMKeyboardUsageSituations.GLOBAL)));
        REGISTERER.register("default", () -> new SFMKeyboardUsageSituation(
                Component.literal("SFM default control"),
                Component.literal("Active for ordinary controls in an SFM workspace"),
                List.of(SFMKeyboardUsageSituations.WORKSPACE)));
        REGISTERER.register("terminal", () -> new SFMKeyboardUsageSituation(
                Component.literal("SFM terminal"),
                Component.literal("Active while the Rust terminal viewport owns keyboard input"),
                List.of(SFMKeyboardUsageSituations.DEFAULT)));
    }

    private SFMKeyboardUsageSituationRegistrations() {
    }

    public static void register(IEventBus bus) {
        REGISTERER.register(bus);
    }
}
