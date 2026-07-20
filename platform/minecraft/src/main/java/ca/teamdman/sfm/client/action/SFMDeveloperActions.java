package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.client.screen.SFMTitleScreenDevScreen;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import net.minecraftforge.eventbus.api.IEventBus;

public final class SFMDeveloperActions {
    private static final SFMDeferredRegister<SFMClientAction<?>> REGISTERER =
            SFMClientActions.createContributor(SFM.MOD_ID);

    public static final SFMRegistryObject<SFMClientAction<?>, OpenTitleScreenDevScreenAction> TEXT_EDITOR =
            REGISTERER.register(
                    "developer/open_text_editor",
                    () -> new OpenTitleScreenDevScreenAction(SFMTitleScreenDevScreen.TEXT_EDITOR)
            );

    public static final SFMRegistryObject<SFMClientAction<?>, OpenTitleScreenDevScreenAction> INPUT_DIAGNOSTICS =
            REGISTERER.register(
                    "developer/open_input_diagnostics",
                    () -> new OpenTitleScreenDevScreenAction(SFMTitleScreenDevScreen.INPUT_DIAG)
            );

    public static final SFMRegistryObject<SFMClientAction<?>, OpenTitleScreenDevScreenAction> DRAW_CANVAS =
            REGISTERER.register(
                    "developer/open_draw_canvas",
                    () -> new OpenTitleScreenDevScreenAction(SFMTitleScreenDevScreen.DRAW_CANVAS)
            );

    public static final SFMRegistryObject<SFMClientAction<?>, CreateDeveloperWorldAction> CREATE_WORLD =
            REGISTERER.register(
                    "developer/create_world",
                    () -> new CreateDeveloperWorldAction(false)
            );

    public static final SFMRegistryObject<SFMClientAction<?>, CreateDeveloperWorldAction> CREATE_WORLD_AND_RUN_GAME_TESTS =
            REGISTERER.register(
                    "developer/create_world_and_run_game_tests",
                    () -> new CreateDeveloperWorldAction(true)
            );

    private SFMDeveloperActions() {
    }

    public static void register(IEventBus bus) {
        REGISTERER.register(bus);
    }
}
