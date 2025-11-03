package ca.teamdman.sfm.common.localization;

import ca.teamdman.sfm.common.util.SFMTranslationUtils;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.function.Supplier;

public record LocalizationEntry(
        Supplier<String> key,
        Supplier<String> value
) {
    public LocalizationEntry(
            String key,
            String value
    ) {
        this(() -> key, () -> value);
    }

    public TextComponentTranslation get(Object... args) {
        return SFMTranslationUtils.getTextComponentTranslation(key.get(), args);
    }

    public TextComponentTranslation get() {
        return SFMTranslationUtils.getTextComponentTranslation(key.get());
    }

    public String getString() {
        return I18n.format(key.get());
    }

    public String getString(Object... args) {
        return I18n.format(key.get(), args);
    }

    /**
     * Some messages are computed on the server side.
     * Using this method is a poor substitute for proper localization.
     * <p/>
     * Sometimes that's just how it is.
     * @return the default English localization value
     */
    public String getStub() {
        return value.get();
    }

    public ITextComponent getComponent() {
        return SFMTranslationUtils.getTextComponentTranslation(key.get());
    }

    public ITextComponent getComponent(Object... args) {
        return SFMTranslationUtils.getTextComponentTranslation(key.get(), args);
    }
}
