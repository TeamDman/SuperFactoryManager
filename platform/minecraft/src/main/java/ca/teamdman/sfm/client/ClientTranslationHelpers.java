package ca.teamdman.sfm.client;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.contents.TranslatableContents;

public class ClientTranslationHelpers {
    public static String resolveTranslation(TranslatableContents contents) {
        return I18n.get(contents.getKey(), contents.getArgs());
    }
}
