package ca.teamdman.sfm.common.util;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.util.text.ITextComponent;

import java.util.Random;

import static ca.teamdman.sfm.common.localization.LocalizationKeys.*;

@Desugar public record ConfirmationParams(
        ITextComponent confirmTitle,
        ITextComponent confirmMessage,
        ITextComponent confirmYes,
        ITextComponent confirmNo
) {
    private static final LocalizationEntry[] CONFIRM_YES_VARIANTS = new LocalizationEntry[]{
            CONFIRM_FUNNY_YES_1,
            CONFIRM_FUNNY_YES_2,
            CONFIRM_FUNNY_YES_3,
            CONFIRM_FUNNY_YES_4,
            CONFIRM_FUNNY_YES_5,
            CONFIRM_FUNNY_YES_6,
            };
    private static final LocalizationEntry[] CONFIRM_NO_VARIANTS = new LocalizationEntry[]{
            CONFIRM_FUNNY_NO_1,
            CONFIRM_FUNNY_NO_2,
            CONFIRM_FUNNY_NO_3,
            CONFIRM_FUNNY_NO_4,
            CONFIRM_FUNNY_NO_5,
            CONFIRM_FUNNY_NO_6,
            };

    public static ConfirmationParams of(
            ITextComponent confirmTitle,
            ITextComponent confirmMessage
    ) {
        Random random = new Random();
        var confirmYes = CONFIRM_YES_VARIANTS[random.nextInt(CONFIRM_YES_VARIANTS.length)].getComponent();
        var confirmNo = CONFIRM_NO_VARIANTS[random.nextInt(CONFIRM_NO_VARIANTS.length)].getComponent();
        return new ConfirmationParams(confirmTitle, confirmMessage, confirmYes, confirmNo);
    }
}
