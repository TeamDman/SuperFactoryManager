package ca.teamdman.sfm.client.widget;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;

public class SFMExtendedButtonWithTooltip extends SFMExtendedButton {
    private final OnTooltip TOOLTIP;

    @MCVersionDependentBehaviour
    public SFMExtendedButtonWithTooltip(
            int xPos,
            int yPos,
            int width,
            int height,
            Component displayString,
            OnPress handler,
            OnTooltip tooltip
    ) {
        super(xPos, yPos, width, height, displayString, handler);
        TOOLTIP = tooltip;
    }

    @MCVersionDependentBehaviour
    @Override
    public void renderToolTip(PoseStack pose, int mx, int my) {
        if (isHovered && visible) {
            TOOLTIP.onTooltip(this, pose, mx, my);
        }
    }
}
