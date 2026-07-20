package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import net.minecraftforge.eventbus.api.IEventBus;

public final class SFMCommandPaletteActions {
    private static final SFMDeferredRegister<SFMClientAction<?>> REGISTERER =
            SFMClientActions.createContributor(SFM.MOD_ID);

    public static final SFMRegistryObject<SFMClientAction<?>, OpenCommandPaletteAction> OPEN = REGISTERER.register(
            "palette/open",
            OpenCommandPaletteAction::new
    );

    public static final SFMRegistryObject<SFMClientAction<?>, DumpRegistriesAction> DUMP_REGISTRIES = REGISTERER.register(
            "dump_registries",
            DumpRegistriesAction::new
    );

    public static final SFMRegistryObject<SFMClientAction<?>, CommandPaletteHelpAction> HELP = REGISTERER.register(
            "help",
            CommandPaletteHelpAction::new
    );

    public static final SFMRegistryObject<SFMClientAction<?>, EchoAction> ECHO = REGISTERER.register(
            "echo",
            EchoAction::new
    );

    private SFMCommandPaletteActions() {
    }

    public static void register(IEventBus bus) {
        REGISTERER.register(bus);
    }
}
