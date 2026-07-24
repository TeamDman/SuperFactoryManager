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

    public static final SFMRegistryObject<SFMClientAction<?>, OpenScreenToSideAction> OPEN_SCREEN_TO_SIDE = REGISTERER.register(
            "workspace/open_to_side",
            OpenScreenToSideAction::new
    );

    public static final SFMRegistryObject<SFMClientAction<?>, OpenKeyBindingScreenAction> MANAGE_KEY_BINDINGS = REGISTERER.register(
            "keybindings/manage",
            OpenKeyBindingScreenAction::new
    );

    public static final SFMRegistryObject<SFMClientAction<?>, OpenReviewBundleAction> OPEN_REVIEW_BUNDLE = REGISTERER.register(
            "review/open_bundle",
            OpenReviewBundleAction::new
    );

    public static final SFMRegistryObject<SFMClientAction<?>, SFMThemeAction> THEME_RELOAD = REGISTERER.register(
            "theme/reload",
            () -> new SFMThemeAction(SFMThemeAction.Operation.RELOAD)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, SFMThemeAction> THEME_RESTORE_DEFAULTS = REGISTERER.register(
            "theme/restore_defaults",
            () -> new SFMThemeAction(SFMThemeAction.Operation.RESTORE_DEFAULTS)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, SFMThemeAction> THEME_OPEN_FILE = REGISTERER.register(
            "theme/open_file",
            () -> new SFMThemeAction(SFMThemeAction.Operation.OPEN_FILE)
    );
    public static final SFMRegistryObject<SFMClientAction<?>, OpenThemeSettingsAction> THEME_SETTINGS = REGISTERER.register(
            "theme/settings", OpenThemeSettingsAction::new
    );

    private SFMCommandPaletteActions() {
    }

    public static void register(IEventBus bus) {
        REGISTERER.register(bus);
    }
}
