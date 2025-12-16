package ca.teamdman.sfm.client;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

public class ClientTranslationHelpers {
    public static String resolveTranslation(TextComponentTranslation contents) {
        return contents.getUnformattedComponentText();
    }

//    public static String resolveTranslation(ITextComponent contents) {
//        if (contents instanceof TextComponentTranslation translation) {
//            return I18n.format(translation.getKey(), translation.getFormatArgs());
//        }
//        return I18n.format(contents.getKey(), contents.getFormatArgs());
//    }
}
