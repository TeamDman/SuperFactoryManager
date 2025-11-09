package ca.teamdman.sfm.client.widget;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

public class SFMButtonBuilder {
    private int nextButtonId = 0;

    private @Nullable ITextComponent text = null;
    private int x = 0;
    private int y = 0;
    private int width = 150;
    private int height = 20;
    private @Nullable SFMExtendedButton.OnPress onPress = null;
    private @MCVersionDependentBehaviour
    @Nullable ITextComponent tooltip = null;



    public void clearBuilder() {
        x = 0;
        y = 0;
        width = 150;
        height = 20;
        text = null;
        onPress = null;
        tooltip = null;
    }

    public SFMButtonBuilder setText(LocalizationEntry text) {
        return setText(text.getComponent());
    }

    public SFMButtonBuilder setText(ITextComponent text) {
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

    public SFMButtonBuilder setOnPress(SFMExtendedButton.OnPress onPress) {
        this.onPress = onPress;
        return this;
    }

    public SFMButtonBuilder setTooltip(
            GuiScreen screen,
            FontRenderer font,
            LocalizationEntry tooltip
    ) {
        return this.setTooltip(screen, font, tooltip.getComponent());
    }

    @MCVersionDependentBehaviour
    @SuppressWarnings("unused")
    public SFMButtonBuilder setTooltip(
            GuiScreen screen,
            FontRenderer font,
            ITextComponent tooltip
    ) {
        this.tooltip = tooltip;
        return this;
    }

    public SFMExtendedButton build() {
        if (text == null) {
            throw new IllegalArgumentException("Text must be set");
        }
        if (onPress == null) {
            throw new IllegalArgumentException("OnPress must be set");
        }
        SFMExtendedButton button;
        if (tooltip != null) {
            button = new SFMExtendedButtonWithTooltip(
                    nextButtonId++,
                    x,
                    y,
                    width,
                    height,
                    text,
                    onPress,
                    tooltip
            );
        } else {
            button = new SFMExtendedButton(
                    nextButtonId++,
                    x,
                    y,
                    width,
                    height,
                    text,
                    onPress
            );
        }

        this.clearBuilder();

        return button;
    }

}
