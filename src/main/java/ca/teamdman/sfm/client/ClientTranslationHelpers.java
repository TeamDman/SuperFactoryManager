package ca.teamdman.sfm.client;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.text.TextComponentTranslation;

public class ClientTranslationHelpers {
    public static String resolveTranslation(TextComponentTranslation contents) {
        return I18n.get(contents.getKey(), contents.getArgs());
    }
}
