package com.bbscn;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.Arrays;
import java.util.Collection;

public class CommonComponents {
    public static final ITextComponent EMPTY = new TextComponentTranslation("");
    public static final ITextComponent OPTION_ON =new TextComponentTranslation("options.on");
    public static final ITextComponent OPTION_OFF =new TextComponentTranslation("options.off");
    public static final ITextComponent GUI_DONE =new TextComponentTranslation("gui.done");
    public static final ITextComponent GUI_CANCEL =new TextComponentTranslation("gui.cancel");
    public static final ITextComponent GUI_YES =new TextComponentTranslation("gui.yes");
    public static final ITextComponent GUI_NO =new TextComponentTranslation("gui.no");
    public static final ITextComponent GUI_OK =new TextComponentTranslation("gui.ok");
    public static final ITextComponent GUI_PROCEED =new TextComponentTranslation("gui.proceed");
    public static final ITextComponent GUI_CONTINUE =new TextComponentTranslation("gui.continue");
    public static final ITextComponent GUI_BACK =new TextComponentTranslation("gui.back");
    public static final ITextComponent GUI_TO_TITLE =new TextComponentTranslation("gui.toTitle");
    public static final ITextComponent GUI_ACKNOWLEDGE =new TextComponentTranslation("gui.acknowledge");
    public static final ITextComponent GUI_OPEN_IN_BROWSER =new TextComponentTranslation("chat.link.open");
    public static final ITextComponent GUI_COPY_LINK_TO_CLIPBOARD =new TextComponentTranslation("gui.copy_link_to_clipboard");
    public static final ITextComponent GUI_DISCONNECT =new TextComponentTranslation("menu.disconnect");
    public static final ITextComponent TRANSFER_CONNECT_FAILED =new TextComponentTranslation("connect.failed.transfer");
    public static final ITextComponent CONNECT_FAILED =new TextComponentTranslation("connect.failed");
    public static final ITextComponent NEW_LINE = new TextComponentString("\n");
    public static final ITextComponent NARRATION_SEPARATOR =  new TextComponentString(". ");
    public static final ITextComponent ELLIPSIS =  new TextComponentString("...");
    public static final ITextComponent SPACE = space();

    public static TextComponentString space() {
        return new TextComponentString(" ");
    }

    public static ITextComponent days(long pDays) {
        return new TextComponentTranslation("gui.days", pDays);
    }

    public static ITextComponent hours(long pHours) {
        return new TextComponentTranslation("gui.hours", pHours);
    }

    public static ITextComponent minutes(long pMinutes) {
        return new TextComponentTranslation("gui.minutes", pMinutes);
    }

    public static ITextComponent optionStatus(boolean pIsEnabled) {
        return pIsEnabled ? OPTION_ON : OPTION_OFF;
    }

    public static ITextComponent optionStatus(ITextComponent pMessage, boolean pComposed) {
        return new TextComponentTranslation(pComposed ? "options.on.composed" : "options.off.composed", pMessage);
    }

    public static ITextComponent optionNameValue(ITextComponent pCaption, ITextComponent pValueMessage) {
        return new TextComponentTranslation("options.generic_value", pCaption, pValueMessage);
    }

    public static ITextComponent joinForNarration(ITextComponent... pITextComponents) {
        ITextComponent mutableITextComponent = new TextComponentTranslation("");

        for (int i = 0; i < pITextComponents.length; i++) {
            mutableITextComponent.appendText(pITextComponents[i].getUnformattedText());
            if (i != pITextComponents.length - 1) {
                mutableITextComponent.appendText(NARRATION_SEPARATOR.getUnformattedText());
            }
        }

        return mutableITextComponent;
    }

    public static ITextComponent joinLines(ITextComponent... pLines) {
        return joinLines(Arrays.asList(pLines));
    }

    public static ITextComponent joinLines(Collection<? extends ITextComponent> pLines) {
        return Tools.formatList(pLines, NEW_LINE);
    }
}
