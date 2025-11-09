package ca.teamdman.sfm.client;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextComponentTranslation;

public class ClientTranslationHelpers {
    public static String resolveTranslation(TextComponentTranslation contents) {
        return I18n.format(contents.getKey(), contents.getFormatArgs());
    }
}
