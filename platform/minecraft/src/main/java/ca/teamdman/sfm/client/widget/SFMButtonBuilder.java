package ca.teamdman.sfm.client.widget;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class SFMButtonBuilder {
    private @Nullable Component text = null;
    private int x = 0;
    private int y = 0;
    private int width = 150;
    private int height = 20;
    private @Nullable Button.OnPress onPress = null;
    private @MCVersionDependentBehaviour @Nullable Button.OnTooltip tooltip = null;

    public SFMButtonBuilder setText(LocalizationEntry text) {
        return setText(text.getComponent());
    }

    public SFMButtonBuilder setText(Component text) {
        this.text = text;
        return this;
    }

    public SFMButtonBuilder setSize(
            int width,
            int height
    ) {
        this.width = width;
        this.height = height;
        return this;
    }

    public SFMButtonBuilder setPosition(
            int x,
            int y
    ) {
        this.x = x;
        this.y = y;
        return this;
    }

    public SFMButtonBuilder setOnPress(Button.OnPress onPress) {
        this.onPress = onPress;
        return this;
    }

    public SFMButtonBuilder setTooltip(
            Screen screen,
            Font font,
            LocalizationEntry tooltip
    ) {
        return this.setTooltip(screen, font, tooltip.getComponent());
    }

    @MCVersionDependentBehaviour
    public SFMButtonBuilder setTooltip(
            Screen screen,
            Font font,
            Component tooltip
    ) {
        this.tooltip = (btn, pose, mx, my) -> screen.renderTooltip(
                pose,
                font.split(tooltip, Math.max(screen.width / 2 - 43, 170)
                ),
                mx,
                my
        );
        return this;
    }

    public Button build() {
        if (text == null) {
            throw new IllegalArgumentException("Text must be set");
        }
        if (onPress == null) {
            throw new IllegalArgumentException("OnPress must be set");
        }
        if (tooltip != null) {
            return new SFMExtendedButtonWithTooltip(
                    x,
                    y,
                    width,
                    height,
                    text,
                    onPress,
                    tooltip
            );
        } else {
            return new SFMExtendedButton(
                    x,
                    y,
                    width,
                    height,
                    text,
                    onPress
            );
        }
    }
}
