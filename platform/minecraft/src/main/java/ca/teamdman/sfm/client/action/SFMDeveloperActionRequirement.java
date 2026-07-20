package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.TitleScreen;

public final class SFMDeveloperActionRequirement {
    @SFMLocalizationDatagen
    public static final LocalizationEntry IDE_ONLY = new LocalizationEntry(
            "gui.sfm.client_action.developer.ide_only",
            "This developer action is only available from an IDE client"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry TITLE_SCREEN_ONLY = new LocalizationEntry(
            "gui.sfm.client_action.developer.title_screen_only",
            "This developer action is only available from the title screen"
    );

    private SFMDeveloperActionRequirement() {
    }

    public static SFMClientActionAvailability<TitleScreen> resolve(SFMClientActionContext context) {
        if (!SFMEnvironmentUtils.isInIDE()) {
            return SFMClientActionAvailability.unavailable(
                    IDE_ONLY.getComponent().withStyle(ChatFormatting.RED)
            );
        }
        return context.requireOriginatingHost(
                TitleScreen.class,
                TITLE_SCREEN_ONLY.getComponent().withStyle(ChatFormatting.RED)
        );
    }
}
