package ca.teamdman.sfm.common.util;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import net.minecraft.network.chat.MutableComponent;

import java.util.Random;

public record ConfirmationParams(
        MutableComponent confirmTitle,
        MutableComponent confirmMessage,
        MutableComponent confirmYes,
        MutableComponent confirmNo
) {
    private static final LocalizationEntry[] CONFIRM_YES_VARIANTS = new LocalizationEntry[]{
            LocalizationKeys.CONFIRM_FUNNY_YES_1,
            LocalizationKeys.CONFIRM_FUNNY_YES_2,
            LocalizationKeys.CONFIRM_FUNNY_YES_3,
            LocalizationKeys.CONFIRM_FUNNY_YES_4,
            LocalizationKeys.CONFIRM_FUNNY_YES_5,
            LocalizationKeys.CONFIRM_FUNNY_YES_6,
            };
    private static final LocalizationEntry[] CONFIRM_NO_VARIANTS = new LocalizationEntry[]{
            LocalizationKeys.CONFIRM_FUNNY_NO_1,
            LocalizationKeys.CONFIRM_FUNNY_NO_2,
            LocalizationKeys.CONFIRM_FUNNY_NO_3,
            LocalizationKeys.CONFIRM_FUNNY_NO_4,
            LocalizationKeys.CONFIRM_FUNNY_NO_5,
            LocalizationKeys.CONFIRM_FUNNY_NO_6,
            };

    public static ConfirmationParams of(
            MutableComponent confirmTitle,
            MutableComponent confirmMessage
    ) {
        Random random = new Random();
        var confirmYes = CONFIRM_YES_VARIANTS[random.nextInt(CONFIRM_YES_VARIANTS.length)].getComponent();
        var confirmNo = CONFIRM_NO_VARIANTS[random.nextInt(CONFIRM_NO_VARIANTS.length)].getComponent();
        return new ConfirmationParams(confirmTitle, confirmMessage, confirmYes, confirmNo);
    }
}
