package ca.teamdman.sfm.client.widget;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.util.text.ITextComponent;

public class SFMExtendedButtonWithTooltip extends SFMExtendedButton {
    protected ITextComponent tooltip;

    @MCVersionDependentBehaviour
    public SFMExtendedButtonWithTooltip(
            int buttonId,
            int xPos,
            int yPos,
            int width,
            int height,
            ITextComponent displayString,
            OnPress handler,
            ITextComponent tooltip
    ) {
        super(buttonId, xPos, yPos, width, height, displayString, handler);
        setTooltip(tooltip);
    }

    public void setTooltip(ITextComponent tooltip) {
        this.tooltip = tooltip;
    }

    public ITextComponent getTooltip() {
        return tooltip;
    }
}
