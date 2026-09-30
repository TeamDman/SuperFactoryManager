package ca.teamdman.sfm.client.screen.widget;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% case minecraft_version %}
{% when '1.19.2' %}
import com.mojang.blaze3d.vertex.PoseStack;
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.client.gui.components.Tooltip;
{% endcase %}
import net.minecraft.network.chat.Component;

public class SFMExtendedButtonWithTooltip extends SFMExtendedButton {
{% case minecraft_version %}
{% when '1.19.2' %}
    private final OnTooltip TOOLTIP;

{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
    @MCVersionDependentBehaviour
    public SFMExtendedButtonWithTooltip(
            int xPos,
            int yPos,
            int width,
            int height,
            Component displayString,
            OnPress handler,
{% case minecraft_version %}
{% when '1.19.2' %}
            OnTooltip tooltip
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
            Tooltip tooltip
{% endcase %}
    ) {
        super(xPos, yPos, width, height, displayString, handler);
{% case minecraft_version %}
{% when '1.19.2' %}
        TOOLTIP = tooltip;
    }

    @MCVersionDependentBehaviour
    @Override
    public void renderToolTip(PoseStack pose, int mx, int my) {
        if (isHovered && visible) {
            TOOLTIP.onTooltip(this, pose, mx, my);
        }
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
        setTooltip(tooltip);
{% endcase %}
    }
}
