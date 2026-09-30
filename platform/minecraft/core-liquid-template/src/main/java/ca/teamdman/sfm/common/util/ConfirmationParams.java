package ca.teamdman.sfm.common.util;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import net.minecraft.network.chat.MutableComponent;

import java.util.Random;

public record ConfirmationParams(
        MutableComponent confirmTitle,

        MutableComponent confirmMessage,

        MutableComponent confirmYes,

        MutableComponent confirmNo
) {
    @SFMLocalizationDatagen
    public static final LocalizationEntry CONFIRM_FUNNY_YES_6 = new LocalizationEntry(
            "gui.sfm.confirm.funny.yes.6",
            "Apply the change."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry CONFIRM_FUNNY_YES_5 = new LocalizationEntry(
            "gui.sfm.confirm.funny.yes.5",
            "lol do it"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry CONFIRM_FUNNY_YES_4 = new LocalizationEntry(
            "gui.sfm.confirm.funny.yes.4",
            "Go for it."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry CONFIRM_FUNNY_YES_3 = new LocalizationEntry(
            "gui.sfm.confirm.funny.yes.3",
            "Yup, go ahead."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry CONFIRM_FUNNY_YES_2 = new LocalizationEntry(
            "gui.sfm.confirm.funny.yes.2",
            "Sure, what could go wrong."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry CONFIRM_FUNNY_YES_1 = new LocalizationEntry(
            "gui.sfm.confirm.funny.yes.1",
            "Yeah, sure, why not."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry CONFIRM_FUNNY_NO_6 = new LocalizationEntry(
            "gui.sfm.confirm.funny.no.6",
            "Never mind"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry CONFIRM_FUNNY_NO_5 = new LocalizationEntry(
            "gui.sfm.confirm.funny.no.5",
            "ABORT ABORT ABORT"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry CONFIRM_FUNNY_NO_4 = new LocalizationEntry(
            "gui.sfm.confirm.funny.no.4",
            "Nope, not today"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry CONFIRM_FUNNY_NO_3 = new LocalizationEntry(
            "gui.sfm.confirm.funny.no.3",
            "no no no no no"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry CONFIRM_FUNNY_NO_2 = new LocalizationEntry(
            "gui.sfm.confirm.funny.no.2",
            "Holy guacamole, no"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry CONFIRM_FUNNY_NO_1 = new LocalizationEntry(
            "gui.sfm.confirm.funny.no.1",
            "Nah, changed my mind"
    );

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
            MutableComponent confirmTitle,
            MutableComponent confirmMessage
    ) {

        Random random = new Random();
        var confirmYes = CONFIRM_YES_VARIANTS[random.nextInt(CONFIRM_YES_VARIANTS.length)].getComponent();
        var confirmNo = CONFIRM_NO_VARIANTS[random.nextInt(CONFIRM_NO_VARIANTS.length)].getComponent();
        return new ConfirmationParams(confirmTitle, confirmMessage, confirmYes, confirmNo);
    }

}
