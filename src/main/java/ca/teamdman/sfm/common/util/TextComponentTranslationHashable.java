package ca.teamdman.sfm.common.util;

import net.minecraft.util.text.TextComponentTranslation;

public class TextComponentTranslationHashable extends TextComponentTranslation {

    public TextComponentTranslationHashable(String translationKey, Object... args) {
        super(translationKey, args);
    }

    @Override
    public int hashCode() {
        return this.getFormattedText().hashCode();
    }


    @Override
    protected void initializeFromFormat(String format) {
        super.initializeFromFormat(format.replace("\\n", "\n"));
    }
}
