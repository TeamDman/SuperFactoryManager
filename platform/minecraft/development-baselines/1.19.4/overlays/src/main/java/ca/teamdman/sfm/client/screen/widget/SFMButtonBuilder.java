package ca.teamdman.sfm.client.screen.widget;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class SFMButtonBuilder {
    private @Nullable Component text = null;
    private int x = 0;
    private int y = 0;
    private int width = 150;
    private int height = 20;
    private @Nullable Button.OnPress onPress = null;
    private @Nullable Supplier<Component> tooltip = null;

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

    public SFMButtonBuilder setTooltip(
            Screen screen,
            Font font,
            Component tooltip
    ) {
        return setTooltipSupplier(screen, font, () -> tooltip);
    }

    @SuppressWarnings("unused")
    public SFMButtonBuilder setTooltipSupplier(Screen screen, Font font,
                                               Supplier<Component> tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    @MCVersionDependentBehaviour
    private Button buildWithTooltip(Supplier<Component> supplier) {
        Component initialTooltip = supplier.get();
        return new SFMExtendedButtonWithTooltip(
                x, y, width, height, text, onPress, Tooltip.create(initialTooltip)
        ) {
            private Component lastTooltip = initialTooltip.copy();

            @Override
            @MCVersionDependentBehaviour
            public void renderWidget(PoseStack pose, int mouseX, int mouseY, float partialTick) {
                Component currentTooltip = supplier.get();
                if (!lastTooltip.equals(currentTooltip)) {
                    setTooltip(Tooltip.create(currentTooltip));
                    lastTooltip = currentTooltip.copy();
                }
                super.renderWidget(pose, mouseX, mouseY, partialTick);
            }
        };
    }

    public Button build() {
        if (text == null) {
            throw new IllegalArgumentException("Text must be set");
        }
        if (onPress == null) {
            throw new IllegalArgumentException("OnPress must be set");
        }
        if (tooltip != null) {
            return buildWithTooltip(tooltip);
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
