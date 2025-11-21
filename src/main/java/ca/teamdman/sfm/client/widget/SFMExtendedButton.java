package ca.teamdman.sfm.client.widget;

import net.minecraft.util.text.ITextComponent;

import com.bbscn.ExtendedButton;

public class SFMExtendedButton extends ExtendedButton {

    public SFMExtendedButton(
                             int xPos,
                             int yPos,
                             int width,
                             int height,
                             ITextComponent displayString,
                             OnPress handler) {
        super(xPos, yPos, width, height, displayString, handler);
    }
}
