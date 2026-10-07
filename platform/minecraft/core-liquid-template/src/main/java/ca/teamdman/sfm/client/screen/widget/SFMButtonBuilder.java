package ca.teamdman.sfm.client.screen.widget;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% case minecraft_version %}
{% when "1.19.2" %}
{% when "1.19.4" %}
{% if features.canvas_text_editor %}
import com.mojang.blaze3d.vertex.PoseStack;
{% else %}
{% endif %}
{% else %}
{% endcase %}
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
{% case minecraft_version %}
{% when "1.19.2" %}
{% when "1.19.4" %}
import net.minecraft.client.gui.components.Tooltip;
{% else %}
import net.minecraft.client.gui.components.Tooltip;
{% endcase %}
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

{% case minecraft_version %}
{% when "1.19.2" %}
{% when "1.19.4" %}
{% if features.canvas_text_editor %}
import java.util.function.Supplier;

{% else %}
{% endif %}
{% else %}
{% endcase %}
public class SFMButtonBuilder {
    private @Nullable Component text = null;
    private int x = 0;
    private int y = 0;
    private int width = 150;
    private int height = 20;
    private @Nullable Button.OnPress onPress = null;
{% case minecraft_version %}
{% when "1.19.2" %}
    private @MCVersionDependentBehaviour @Nullable Button.OnTooltip tooltip = null;
{% when "1.19.4" %}
{% if features.canvas_text_editor %}
    private @Nullable Supplier<Component> tooltip = null;
{% else %}
    private @MCVersionDependentBehaviour @Nullable Tooltip tooltip = null;
{% endif %}
{% else %}
    private @MCVersionDependentBehaviour @Nullable Tooltip tooltip = null;
{% endcase %}

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

{% case minecraft_version %}
{% when "1.19.2" %}
    @MCVersionDependentBehaviour
{% when "1.19.4" %}
{% if features.canvas_text_editor %}
{% else %}
    @MCVersionDependentBehaviour
    @SuppressWarnings("unused")
{% endif %}
{% else %}
    @MCVersionDependentBehaviour
    @SuppressWarnings("unused")
{% endcase %}
    public SFMButtonBuilder setTooltip(
            Screen screen,
            Font font,
            Component tooltip
    ) {
{% case minecraft_version %}
{% when "1.19.2" %}
{% if features.canvas_text_editor %}
        return setTooltipSupplier(screen, font, () -> tooltip);
    }

    @MCVersionDependentBehaviour
    public SFMButtonBuilder setTooltipSupplier(Screen screen, Font font,
                                               java.util.function.Supplier<Component> tooltip) {
        this.tooltip = (btn, pose, mx, my) -> screen.renderTooltip(
                pose,
                font.split(tooltip.get(), Math.max(screen.width / 2 - 43, 170)
                ),
                mx,
                my
        );
{% else %}
        this.tooltip = (btn, pose, mx, my) -> screen.renderTooltip(
                pose,
                font.split(tooltip, Math.max(screen.width / 2 - 43, 170)
                ),
                mx,
                my
        );
{% endif %}
{% when "1.19.4" %}
{% if features.canvas_text_editor %}
        return setTooltipSupplier(screen, font, () -> tooltip);
    }

    @SuppressWarnings("unused")
    public SFMButtonBuilder setTooltipSupplier(Screen screen, Font font,
                                               Supplier<Component> tooltip) {
        this.tooltip = tooltip;
{% else %}
        this.tooltip = Tooltip.create(tooltip);
{% endif %}
{% else %}
        this.tooltip = Tooltip.create(tooltip);
{% endcase %}
        return this;
    }

{% case minecraft_version %}
{% when "1.19.2" %}
{% when "1.19.4" %}
{% if features.canvas_text_editor %}
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

{% else %}
{% endif %}
{% else %}
{% endcase %}
    public Button build() {
        if (text == null) {
            throw new IllegalArgumentException("Text must be set");
        }
        if (onPress == null) {
            throw new IllegalArgumentException("OnPress must be set");
        }
        if (tooltip != null) {
{% case minecraft_version %}
{% when "1.19.2" %}
            return new SFMExtendedButtonWithTooltip(
                    x,
                    y,
                    width,
                    height,
                    text,
                    onPress,
                    tooltip
            );
{% when "1.19.4" %}
{% if features.canvas_text_editor %}
            return buildWithTooltip(tooltip);
{% else %}
            return new SFMExtendedButtonWithTooltip(
                    x,
                    y,
                    width,
                    height,
                    text,
                    onPress,
                    tooltip
            );
{% endif %}
{% else %}
            return new SFMExtendedButtonWithTooltip(
                    x,
                    y,
                    width,
                    height,
                    text,
                    onPress,
                    tooltip
            );
{% endcase %}
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
