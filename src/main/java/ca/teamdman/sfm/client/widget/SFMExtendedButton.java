package ca.teamdman.sfm.client.widget;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.text.ITextComponent;

public class SFMExtendedButton extends GuiButton {
    protected OnPress handler;

    public SFMExtendedButton(
            int buttonId,
            int xPos,
            int yPos,
            int width,
            int height,
            ITextComponent displayString,
            OnPress handler
    ) {
        super(buttonId, xPos, yPos, width, height, displayString.getFormattedText());
        this.handler = handler;
    }

    public OnPress getHandler() {
        return handler;
    }

    public void onClick(int mx, int my) {
        handler.press(this);
    }

    public boolean clicked(int mx, int my) {
        return true;
    }

    public interface OnPress {
        public void press(GuiButton button);
    }
}
