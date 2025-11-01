package ca.teamdman.sfm.client.widget;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public class SFMExtendedButtonWithTooltip extends SFMExtendedButton {
    @MCVersionDependentBehaviour
    public SFMExtendedButtonWithTooltip(
            int xPos,
            int yPos,
            int width,
            int height,
            Component displayString,
            OnPress handler,
            Tooltip tooltip
    ) {
        super(xPos, yPos, width, height, displayString, handler);
        setTooltip(tooltip);
    }
}
