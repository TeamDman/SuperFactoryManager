package ca.teamdman.sfm.client.widget;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.util.text.ITextComponent;

public class SFMExtendedButtonWithTooltip extends SFMExtendedButton {

    @MCVersionDependentBehaviour
    public SFMExtendedButtonWithTooltip(
            int xPos,
            int yPos,
            int width,
            int height,
            ITextComponent displayString,
            OnPress handler,
            Tooltip tooltip
    ) {
        super(xPos, yPos, width, height, displayString, handler);
        setTooltip(tooltip);
    }


}
