package ca.teamdman.sfm.client.screen.widget;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.gui.widget.ExtendedButton;

public class SFMExtendedButton extends ExtendedButton {
    public SFMExtendedButton(
            int xPos,
            int yPos,
            int width,
            int height,
            Component displayString,
            OnPress handler
    ) {
        super(xPos, yPos, width, height, displayString, handler);
    }
}
