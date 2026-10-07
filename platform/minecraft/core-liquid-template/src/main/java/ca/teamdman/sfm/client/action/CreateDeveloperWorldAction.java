package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.developer.SFMDeveloperWorldLauncher;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

public final class CreateDeveloperWorldAction implements SFMClientAction<TitleScreen> {
    @SFMLocalizationDatagen
    public static final LocalizationEntry CREATE_TITLE = new LocalizationEntry(
            "gui.sfm.client_action.developer.create_world.title",
            "Create Dev World"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry CREATE_DESCRIPTION = new LocalizationEntry(
            "gui.sfm.client_action.developer.create_world.description",
            "Create and open a configured SFM developer world"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry CREATE_AND_RUN_TESTS_TITLE = new LocalizationEntry(
            "gui.sfm.client_action.developer.create_world_and_run_game_tests.title",
            "Create Dev World + Run GameTests"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry CREATE_AND_RUN_TESTS_DESCRIPTION = new LocalizationEntry(
            "gui.sfm.client_action.developer.create_world_and_run_game_tests.description",
            "Create an SFM developer world and run all discovered GameTests"
    );

    private final boolean runGameTests;

    public CreateDeveloperWorldAction(boolean runGameTests) {
        this.runGameTests = runGameTests;
    }

    @Override
    public Component title() {
        return (runGameTests ? CREATE_AND_RUN_TESTS_TITLE : CREATE_TITLE).getComponent();
    }

    @Override
    public Component description() {
        return (runGameTests ? CREATE_AND_RUN_TESTS_DESCRIPTION : CREATE_DESCRIPTION).getComponent();
    }

    @Override
    public SFMClientActionRequirement<TitleScreen> requirement() {
        return SFMDeveloperActionRequirement::resolve;
    }

    @Override
    public int execute(
            TitleScreen target,
            CommandContext<SFMClientActionSource> context
    ) {
        SFMDeveloperWorldLauncher.createDeveloperWorld(runGameTests);
        return 1;
    }
}
