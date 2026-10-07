package ca.teamdman.sfm.client.screen.color;

{% if features.single_line_input %}
import ca.teamdman.sfm.client.input.SFMSingleLineInput;
import ca.teamdman.sfm.client.input.SFMSingleLineInputView;
{% endif %}
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.SFMGuiCrosshair;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntent;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import com.mojang.blaze3d.vertex.PoseStack;
{% else %}
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.client.gui.GuiGraphicsExtractor;
{% else %}
import net.minecraft.client.gui.GuiGraphics;
{% endcase %}
{% endcase %}
import net.minecraft.client.Minecraft;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import net.minecraft.client.gui.GuiComponent;
{% endcase %}
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Reusable preference-agnostic typed ARGB input panel. */
public final class SFMColorInputPanel implements SFMScreenPanel {
    private static final int PANEL = 0xF01B2026;
    private static final int BORDER = 0xFF59636E;
    private static final int FOCUSED = 0xFF55FFFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFB0B0B0;
    private static final int ERROR = 0xFFFF7777;
    private static final int BUTTON = 0xFF303840;

    private final SFMColorInputModel model;
    private final Consumer<SFMArgbColor> confirmCallback;
    private final Runnable cancelCallback;
    private Focus focus = Focus.FIELD;
    private int recentIndex;
    private String hexText;
{% if features.single_line_input %}
    private final SFMSingleLineInput hexInput;
    private final SFMSingleLineInputView hexView = new SFMSingleLineInputView();
{% endif %}
    private @Nullable String diagnostic;
    private @Nullable SFMArgbColor confirmedResult;
    private @Nullable Minecraft minecraft;
    private SFMColorInputPanelLayout layout = SFMColorInputPanelLayout.fit(new SFMScreenPanelBounds(0, 0, 600, 360));
    private Drag drag = Drag.NONE;
    private @Nullable SFMWorkspacePanelContext hostContext;

    public SFMColorInputPanel(
            SFMArgbColor initial,
            List<SFMArgbColor> recent,
            Consumer<SFMArgbColor> confirmCallback,
            Runnable cancelCallback
    ) {
        model = new SFMColorInputModel(Objects.requireNonNull(initial), Objects.requireNonNull(recent));
        this.confirmCallback = Objects.requireNonNull(confirmCallback);
        this.cancelCallback = Objects.requireNonNull(cancelCallback);
        hexText = initial.toHex(model.hexOrder());
{% if features.single_line_input %}
        hexInput = new SFMSingleLineInput(hexText, 9, true, value -> value.matches("#?[0-9A-Fa-f]*"));
{% endif %}
    }

    public SFMColorInputModel model() { return model; }
    public SFMColorInputPanelLayout layout() { return layout; }
    public @Nullable SFMArgbColor confirmedResult() { return confirmedResult; }

    /** Typed programmatic seam used by callers restoring a draft and deterministic puppets. */
    public void setHexValue(String text, SFMArgbColor.HexOrder order) {
        if (model.hexOrder() != order) model.toggleHexOrder();
        model.applyHex(text);
        syncHex();
    }

    @Override public Component title() { return Component.literal("Colour input"); }

    @Override
    public Component narration() {
        String state = model.resolution() == SFMColorInputModel.Resolution.EDITING
                ? "Editing " + model.current().toHex(model.hexOrder()) + ". Focus " + focus.label
                : model.resolution() + " " + model.current().toHex(model.hexOrder());
        return Component.literal("Colour input. " + state + (diagnostic == null ? "" : ". " + diagnostic));
    }

    @Override
    public void opened(Minecraft minecraft, SFMScreenPanelBounds bounds, SFMWorkspacePanelContext context) {
        this.minecraft = minecraft;
        this.hostContext = context;
        this.layout = SFMColorInputPanelLayout.fit(bounds);
    }

    @Override public void resized(Minecraft minecraft, SFMScreenPanelBounds bounds) {
        this.minecraft = minecraft;
        this.layout = SFMColorInputPanelLayout.fit(bounds);
    }

    @Override
    public void closed() {
        hostContext = null;
        if (model.resolution() == SFMColorInputModel.Resolution.EDITING) cancel();
    }

    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public void render(PoseStack poseStack, Minecraft minecraft, SFMScreenPanelBounds bounds, int mouseX, int mouseY,
{% else %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
{% case minecraft_version %}
{% when "26.1.2" %}
    public void render(GuiGraphicsExtractor graphics, Minecraft minecraft, SFMScreenPanelBounds bounds, int mouseX, int mouseY,
{% else %}
    public void render(GuiGraphics graphics, Minecraft minecraft, SFMScreenPanelBounds bounds, int mouseX, int mouseY,
{% endcase %}
{% endcase %}
                       float partialTick, boolean focused) {
        SFMColorInputPanelLayout.Rect panel = layout.panel();
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        fill(poseStack, panel, PANEL);
        outline(poseStack, panel, focused ? FOCUSED : BORDER);
{% else %}
        fillRect(graphics, panel, PANEL);
        outline(graphics, panel, focused ? FOCUSED : BORDER);
{% endcase %}
        String heading = panel.width() < 260 ? "ARGB colour" : "Reusable ARGB colour input";
        String subtitle = panel.width() < 260 ? "HSV • hex • channels"
                : layout.compact() ? "HSV field • value • hex • channels"
                : "Hue + saturation field • value slider • typed channels";
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        centered(poseStack, minecraft, heading, panel.x(), panel.width(), panel.y() + 12,
{% else %}
        centered(graphics, minecraft, heading, panel.x(), panel.width(), panel.y() + 12,
{% endcase %}
                0xFFFFAA00);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        centered(poseStack, minecraft, subtitle, panel.x(), panel.width(), panel.y() + 26, MUTED);
{% else %}
        centered(graphics, minecraft, subtitle, panel.x(), panel.width(), panel.y() + 26, MUTED);
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        renderHueSaturationField(poseStack);
        renderValueSlider(poseStack);
        renderSwatch(poseStack, minecraft);
        renderHex(poseStack, minecraft);
        renderChannels(poseStack, minecraft);
        renderRecents(poseStack, minecraft);
        renderButton(poseStack, minecraft, layout.reset(), "Reset", focus == Focus.RESET);
        renderButton(poseStack, minecraft, layout.cancel(), "Cancel", focus == Focus.CANCEL);
        renderButton(poseStack, minecraft, layout.confirm(), "Confirm", focus == Focus.CONFIRM);
{% else %}
        renderHueSaturationField(graphics);
        renderValueSlider(graphics);
        renderSwatch(graphics, minecraft);
        renderHex(graphics, minecraft);
        renderChannels(graphics, minecraft);
        renderRecents(graphics, minecraft);
        renderButton(graphics, minecraft, layout.reset(), "Reset", focus == Focus.RESET);
        renderButton(graphics, minecraft, layout.cancel(), "Cancel", focus == Focus.CANCEL);
        renderButton(graphics, minecraft, layout.confirm(), "Confirm", focus == Focus.CONFIRM);
{% endcase %}

        String status = diagnostic != null ? diagnostic
                : model.resolution() == SFMColorInputModel.Resolution.CONFIRMED
                ? "Confirmed typed result " + model.current().toHex(SFMArgbColor.HexOrder.ARGB)
                : model.resolution() == SFMColorInputModel.Resolution.CANCELLED
                ? "Cancelled — no result was applied"
                : "Tab navigates • arrows adjust • Enter activates";
        if (panel.height() >= 280) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            centered(poseStack, minecraft, fitText(minecraft, status, panel.width() - 16), panel.x(), panel.width(),
{% else %}
            centered(graphics, minecraft, fitText(minecraft, status, panel.width() - 16), panel.x(), panel.width(),
{% endcase %}
                    layout.reset().y() - 13,
                    diagnostic == null ? MUTED : ERROR);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || model.resolution() != SFMColorInputModel.Resolution.EDITING) {
            return layout.panel().contains(mouseX, mouseY);
        }
        if (layout.hueSaturation().contains(mouseX, mouseY)) {
            focus = Focus.FIELD;
            drag = Drag.FIELD;
            updateField(mouseX, mouseY);
        } else if (layout.valueSlider().contains(mouseX, mouseY)) {
            focus = Focus.VALUE;
            drag = Drag.VALUE;
            updateValue(mouseX);
        } else if (layout.hex().contains(mouseX, mouseY)) {
            focus = Focus.HEX;
{% if features.single_line_input %}
            drag = Drag.HEX;
            moveHexCaret(mouseX, minecraft != null && net.minecraft.client.gui.screens.Screen.hasShiftDown());
{% endif %}
        } else if (layout.order().contains(mouseX, mouseY)) {
            model.toggleHexOrder();
            syncHex();
            focus = Focus.HEX;
        } else if (layout.channels().contains(mouseX, mouseY)) {
            clickChannel(mouseX, mouseY);
        } else if (layout.recents().contains(mouseX, mouseY)) {
            clickRecent(mouseX);
        } else if (layout.reset().contains(mouseX, mouseY)) {
            focus = Focus.RESET;
            model.reset();
            syncHex();
        } else if (layout.cancel().contains(mouseX, mouseY)) {
            focus = Focus.CANCEL;
            cancel();
        } else if (layout.confirm().contains(mouseX, mouseY)) {
            focus = Focus.CONFIRM;
            confirm();
        }
        return layout.panel().contains(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || model.resolution() != SFMColorInputModel.Resolution.EDITING) return false;
        if (drag == Drag.FIELD) updateField(mouseX, mouseY);
        else if (drag == Drag.VALUE) updateValue(mouseX);
{% if features.single_line_input %}
        else if (drag == Drag.HEX) moveHexCaret(mouseX, true);
{% endif %}
        else return false;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean consumed = drag != Drag.NONE;
        drag = Drag.NONE;
        return consumed;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (model.resolution() != SFMColorInputModel.Resolution.EDITING) return false;
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            cycleFocus((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1);
            return true;
        }
{% if features.single_line_input %}
        if (focus == Focus.HEX && hexInput.keyPressed(keyCode, modifiers,
                () -> minecraft == null ? "" : sanitizeHex(minecraft.keyboardHandler.getClipboard()),
                value -> { if (minecraft != null) minecraft.keyboardHandler.setClipboard(value); })) {
            hexText = hexInput.text();
            diagnostic = null;
            if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) applyHex();
            return true;
        }
{% else %}
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && focus == Focus.HEX) {
            if (!hexText.isEmpty()) hexText = hexText.substring(0, hexText.length() - 1);
            diagnostic = null;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && focus == Focus.HEX
                && minecraft != null) {
            hexText = sanitizeHex(minecraft.keyboardHandler.getClipboard());
            applyHex();
            return true;
        }
{% endif %}
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            activateFocus();
            return true;
        }
        int direction = keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_DOWN ? -1
                : keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_UP ? 1 : 0;
        if (direction != 0) {
            adjustFocused(direction, (modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (focus != Focus.HEX || model.resolution() != SFMColorInputModel.Resolution.EDITING) return false;
{% if features.single_line_input %}
        if (character == '#' || Character.digit(character, 16) >= 0) {
            if (!hexInput.charTyped(Character.toUpperCase(character), modifiers)) return false;
            hexText = hexInput.text();
            diagnostic = null;
            return true;
        }
{% else %}
        if ((character == '#' && hexText.isEmpty()) || Character.digit(character, 16) >= 0) {
            if (hexText.length() < 9) hexText += Character.toUpperCase(character);
            diagnostic = null;
            return true;
        }
{% endif %}
        return false;
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private void renderHueSaturationField(PoseStack poseStack) {
{% else %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
{% case minecraft_version %}
{% when "26.1.2" %}
    private void renderHueSaturationField(GuiGraphicsExtractor graphics) {
{% else %}
    private void renderHueSaturationField(GuiGraphics graphics) {
{% endcase %}
{% endcase %}
        SFMColorInputPanelLayout.Rect field = layout.hueSaturation();
        int columns = 45;
        int rows = 24;
        double value = model.current().toHsv().value();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int left = field.x() + column * field.width() / columns;
                int right = field.x() + (column + 1) * field.width() / columns;
                int top = field.y() + row * field.height() / rows;
                int bottom = field.y() + (row + 1) * field.height() / rows;
                int rgb = SFMArgbColor.fromHsv(255, column / (double) (columns - 1),
                        1D - row / (double) (rows - 1), value).argb();
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                GuiComponent.fill(poseStack, left, top, right, bottom, rgb);
{% else %}
                graphics.fill(left, top, right, bottom, rgb);
{% endcase %}
            }
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        outline(poseStack, field, focus == Focus.FIELD ? FOCUSED : BORDER);
{% else %}
        outline(graphics, field, focus == Focus.FIELD ? FOCUSED : BORDER);
{% endcase %}
        SFMArgbColor.Hsv hsv = model.current().toHsv();
        int x = field.x() + (int) Math.round(hsv.hue() * (field.width() - 1));
        int y = field.y() + (int) Math.round((1D - hsv.saturation()) * (field.height() - 1));
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        SFMGuiCrosshair.draw(poseStack, x, y, 5, TEXT);
{% else %}
        SFMGuiCrosshair.draw(graphics, x, y, 5, TEXT);
{% endcase %}
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private void renderValueSlider(PoseStack poseStack) {
{% else %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
{% case minecraft_version %}
{% when "26.1.2" %}
    private void renderValueSlider(GuiGraphicsExtractor graphics) {
{% else %}
    private void renderValueSlider(GuiGraphics graphics) {
{% endcase %}
{% endcase %}
        SFMColorInputPanelLayout.Rect slider = layout.valueSlider();
        SFMArgbColor.Hsv hsv = model.current().toHsv();
        int columns = Math.max(1, slider.width() / 4);
        for (int i = 0; i < columns; i++) {
            int left = slider.x() + i * slider.width() / columns;
            int right = slider.x() + (i + 1) * slider.width() / columns;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            fill(poseStack, new SFMColorInputPanelLayout.Rect(left, slider.y(), right - left, slider.height()),
{% else %}
            fillRect(graphics, new SFMColorInputPanelLayout.Rect(left, slider.y(), right - left, slider.height()),
{% endcase %}
                    SFMArgbColor.fromHsv(255, hsv.hue(), hsv.saturation(),
                            columns == 1 ? 0D : i / (double) (columns - 1)).argb());
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        outline(poseStack, slider, focus == Focus.VALUE ? FOCUSED : BORDER);
{% else %}
        outline(graphics, slider, focus == Focus.VALUE ? FOCUSED : BORDER);
{% endcase %}
        int thumb = slider.x() + (int) Math.round(hsv.value() * (slider.width() - 1));
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        GuiComponent.fill(poseStack, thumb - 1, slider.y() - 2, thumb + 2, slider.bottom() + 2, TEXT);
{% else %}
        graphics.fill(thumb - 1, slider.y() - 2, thumb + 2, slider.bottom() + 2, TEXT);
{% endcase %}
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private void renderSwatch(PoseStack poseStack, Minecraft minecraft) {
{% else %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
{% case minecraft_version %}
{% when "26.1.2" %}
    private void renderSwatch(GuiGraphicsExtractor graphics, Minecraft minecraft) {
{% else %}
    private void renderSwatch(GuiGraphics graphics, Minecraft minecraft) {
{% endcase %}
{% endcase %}
        SFMColorInputPanelLayout.Rect swatch = layout.swatch();
        int checker = 8;
        for (int y = swatch.y(); y < swatch.bottom(); y += checker) {
            for (int x = swatch.x(); x < swatch.right(); x += checker) {
                int colour = ((x / checker + y / checker) & 1) == 0 ? 0xFF555555 : 0xFFAAAAAA;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                GuiComponent.fill(poseStack, x, y, Math.min(x + checker, swatch.right()),
{% else %}
                graphics.fill(x, y, Math.min(x + checker, swatch.right()),
{% endcase %}
                        Math.min(y + checker, swatch.bottom()), colour);
            }
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        fill(poseStack, swatch, model.current().argb());
        outline(poseStack, swatch, BORDER);
{% else %}
        fillRect(graphics, swatch, model.current().argb());
        outline(graphics, swatch, BORDER);
{% endcase %}
        if (swatch.width() >= 80) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            centered(poseStack, minecraft, model.current().toHex(SFMArgbColor.HexOrder.ARGB), swatch.x(),
{% else %}
            centered(graphics, minecraft, model.current().toHex(SFMArgbColor.HexOrder.ARGB), swatch.x(),
{% endcase %}
                    swatch.width(), swatch.y() + (swatch.height() - 8) / 2, TEXT);
        }
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private void renderHex(PoseStack poseStack, Minecraft minecraft) {
        fill(poseStack, layout.hex(), 0xFF101419);
        outline(poseStack, layout.hex(), focus == Focus.HEX ? FOCUSED : BORDER);
{% if features.single_line_input %}
        hexView.render(poseStack, minecraft.font, hexInput, layout.hex().x() + 4, layout.hex().y() + 6,
                layout.hex().width() - 8, focus == Focus.HEX
                        && model.resolution() == SFMColorInputModel.Resolution.EDITING, "", TEXT, MUTED);
{% else %}
        String shown = hexText + (focus == Focus.HEX && model.resolution() == SFMColorInputModel.Resolution.EDITING ? "_" : "");
        SFMFontUtils.draw(poseStack, minecraft.font, shown, layout.hex().x() + 4, layout.hex().y() + 6, TEXT, false);
{% endif %}
        renderButton(poseStack, minecraft, layout.order(), model.hexOrder().name(), false);
{% else %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
{% case minecraft_version %}
{% when "26.1.2" %}
    private void renderHex(GuiGraphicsExtractor graphics, Minecraft minecraft) {
{% else %}
    private void renderHex(GuiGraphics graphics, Minecraft minecraft) {
{% endcase %}
        fillRect(graphics, layout.hex(), 0xFF101419);
        outline(graphics, layout.hex(), focus == Focus.HEX ? FOCUSED : BORDER);
{% if features.single_line_input %}
        hexView.render(graphics, minecraft.font, hexInput, layout.hex().x() + 4, layout.hex().y() + 6,
                layout.hex().width() - 8, focus == Focus.HEX
                        && model.resolution() == SFMColorInputModel.Resolution.EDITING, "", TEXT, MUTED);
{% else %}
        String shown = hexText + (focus == Focus.HEX && model.resolution() == SFMColorInputModel.Resolution.EDITING ? "_" : "");
        SFMFontUtils.draw(graphics, minecraft.font, shown, layout.hex().x() + 4, layout.hex().y() + 6, TEXT, false);
{% endif %}
        renderButton(graphics, minecraft, layout.order(), model.hexOrder().name(), false);
{% endcase %}
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private void renderChannels(PoseStack poseStack, Minecraft minecraft) {
{% else %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
{% case minecraft_version %}
{% when "26.1.2" %}
    private void renderChannels(GuiGraphicsExtractor graphics, Minecraft minecraft) {
{% else %}
    private void renderChannels(GuiGraphics graphics, Minecraft minecraft) {
{% endcase %}
{% endcase %}
        String[] names = {"A", "R", "G", "B"};
        int[] values = {model.current().alpha(), model.current().red(), model.current().green(), model.current().blue()};
        int rowHeight = layout.channels().height() / 4;
        for (int i = 0; i < 4; i++) {
            int y = layout.channels().y() + i * rowHeight;
            int textY = y + Math.max(1, (rowHeight - 8) / 2);
            boolean selected = focus.channel == i;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            if (selected) GuiComponent.fill(poseStack, layout.channels().x(), y,
{% else %}
            if (selected) graphics.fill(layout.channels().x(), y,
{% endcase %}
                    layout.channels().right(), y + rowHeight - 1, 0x4433FFFF);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            SFMFontUtils.draw(poseStack, minecraft.font, names[i] + "  " + values[i],
{% else %}
            SFMFontUtils.draw(graphics, minecraft.font, names[i] + "  " + values[i],
{% endcase %}
                    layout.channels().x() + 3, textY, selected ? FOCUSED : TEXT, false);
            int minusX = layout.channels().right() - 38;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            GuiComponent.fill(poseStack, minusX, y + 1, minusX + 17, y + rowHeight - 2, BUTTON);
            GuiComponent.fill(poseStack, minusX + 20, y + 1, minusX + 37, y + rowHeight - 2, BUTTON);
            centered(poseStack, minecraft, "−", minusX, 17, textY, TEXT);
            centered(poseStack, minecraft, "+", minusX + 20, 17, textY, TEXT);
{% else %}
            graphics.fill(minusX, y + 1, minusX + 17, y + rowHeight - 2, BUTTON);
            graphics.fill(minusX + 20, y + 1, minusX + 37, y + rowHeight - 2, BUTTON);
            centered(graphics, minecraft, "−", minusX, 17, textY, TEXT);
            centered(graphics, minecraft, "+", minusX + 20, 17, textY, TEXT);
{% endcase %}
        }
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private void renderRecents(PoseStack poseStack, Minecraft minecraft) {
{% else %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
{% case minecraft_version %}
{% when "26.1.2" %}
    private void renderRecents(GuiGraphicsExtractor graphics, Minecraft minecraft) {
{% else %}
    private void renderRecents(GuiGraphics graphics, Minecraft minecraft) {
{% endcase %}
{% endcase %}
        if (layout.panel().height() >= 280) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            SFMFontUtils.draw(poseStack, minecraft.font, "Recent", layout.recents().x(), layout.recents().y() - 11,
{% else %}
            SFMFontUtils.draw(graphics, minecraft.font, "Recent", layout.recents().x(), layout.recents().y() - 11,
{% endcase %}
                    MUTED, false);
        }
        int size = Math.min(20, layout.recents().height());
        for (int i = 0; i < model.recent().size(); i++) {
            int x = layout.recents().x() + i * (size + 4);
            if (x + size > layout.recents().right()) break;
            SFMColorInputPanelLayout.Rect rect = new SFMColorInputPanelLayout.Rect(x, layout.recents().y(), size, size);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            fill(poseStack, rect, model.recent().get(i).argb());
            outline(poseStack, rect, focus == Focus.RECENTS && recentIndex == i ? FOCUSED : BORDER);
{% else %}
            fillRect(graphics, rect, model.recent().get(i).argb());
            outline(graphics, rect, focus == Focus.RECENTS && recentIndex == i ? FOCUSED : BORDER);
{% endcase %}
        }
    }

    private void clickChannel(double mouseX, double mouseY) {
        int rowHeight = Math.max(1, layout.channels().height() / 4);
        int channel = Math.max(0, Math.min(3, (int) ((mouseY - layout.channels().y()) / rowHeight)));
        focus = Focus.forChannel(channel);
        int minusX = layout.channels().right() - 38;
        if (mouseX >= minusX && mouseX < minusX + 17) adjustChannel(channel, -1);
        else if (mouseX >= minusX + 20) adjustChannel(channel, 1);
    }

    private void clickRecent(double mouseX) {
        int size = Math.min(20, layout.recents().height());
        int index = (int) ((mouseX - layout.recents().x()) / (size + 4));
        if (index >= 0 && index < model.recent().size()) {
            recentIndex = index;
            focus = Focus.RECENTS;
            model.selectRecent(index);
            syncHex();
        }
    }

    private void updateField(double mouseX, double mouseY) {
        SFMColorInputPanelLayout.Rect field = layout.hueSaturation();
        double hue = unit((mouseX - field.x()) / Math.max(1D, field.width() - 1D));
        double saturation = 1D - unit((mouseY - field.y()) / Math.max(1D, field.height() - 1D));
        model.setHueSaturation(hue, saturation);
        syncHex();
    }

    private void updateValue(double mouseX) {
        SFMColorInputPanelLayout.Rect slider = layout.valueSlider();
        model.setValue(unit((mouseX - slider.x()) / Math.max(1D, slider.width() - 1D)));
        syncHex();
    }

    private void adjustFocused(int direction, boolean coarse) {
        int amount = coarse ? 16 : 1;
        if (focus.channel >= 0) adjustChannel(focus.channel, direction * amount);
        else if (focus == Focus.VALUE) {
            model.setValue(model.current().toHsv().value() + direction * (coarse ? 0.1D : 0.01D));
            syncHex();
        } else if (focus == Focus.FIELD) {
            SFMArgbColor.Hsv hsv = model.current().toHsv();
            model.setHueSaturation(hsv.hue() + direction * (coarse ? 0.05D : 0.01D), hsv.saturation());
            syncHex();
        } else if (focus == Focus.RECENTS && !model.recent().isEmpty()) {
            recentIndex = Math.max(0, Math.min(model.recent().size() - 1, recentIndex + direction));
            model.selectRecent(recentIndex);
            syncHex();
        }
    }

    private void adjustChannel(int channel, int delta) {
        model.adjustChannel(channel, delta);
        syncHex();
    }

    private void activateFocus() {
        if (focus == Focus.HEX) applyHex();
        else if (focus == Focus.RECENTS && !model.recent().isEmpty()) {
            model.selectRecent(recentIndex);
            syncHex();
        } else if (focus == Focus.RESET) { model.reset(); syncHex(); }
        else if (focus == Focus.CANCEL) cancel();
        else if (focus == Focus.CONFIRM) confirm();
    }

    private void applyHex() {
        try {
            model.applyHex(hexText);
            syncHex();
        } catch (IllegalArgumentException exception) {
            diagnostic = exception.getMessage();
        }
    }

    private void confirm() {
        if (model.resolution() != SFMColorInputModel.Resolution.EDITING) return;
        if (focus == Focus.HEX) {
            applyHex();
            if (diagnostic != null) return;
        }
        confirmedResult = model.confirm();
        confirmCallback.accept(confirmedResult);
        diagnostic = null;
        closeHostedPanel();
    }

    private void cancel() {
        if (model.resolution() != SFMColorInputModel.Resolution.EDITING) return;
        model.cancel();
        cancelCallback.run();
        diagnostic = null;
        closeHostedPanel();
    }

    private void closeHostedPanel() {
        if (hostContext != null) hostContext.submit(new SFMWorkspacePanelIntent.Close());
    }

    private void syncHex() {
        hexText = model.current().toHex(model.hexOrder());
{% if features.single_line_input %}
        hexInput.setText(hexText);
{% endif %}
        diagnostic = null;
    }

{% if features.single_line_input %}
    private void moveHexCaret(double x, boolean extend) {
        if (minecraft == null) return;
        int at = hexView.indexAt(hexInput, minecraft.font, layout.hex().width() - 8, x - layout.hex().x() - 4);
        hexInput.select(extend ? hexInput.anchor() : at, at);
    }

{% endif %}
    private void cycleFocus(int direction) {
        Focus[] values = Focus.values();
        int next = (focus.ordinal() + direction + values.length) % values.length;
        focus = values[next];
    }

    private static String sanitizeHex(String value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length() && result.length() < 9; i++) {
            char character = value.charAt(i);
            if ((character == '#' && result.isEmpty()) || Character.digit(character, 16) >= 0) {
                result.append(Character.toUpperCase(character));
            }
        }
        return result.toString();
    }

    private static double unit(double value) { return Math.max(0D, Math.min(1D, value)); }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private static void fill(PoseStack poseStack, SFMColorInputPanelLayout.Rect rect, int colour) {
        GuiComponent.fill(poseStack, rect.x(), rect.y(), rect.right(), rect.bottom(), colour);
{% else %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
{% case minecraft_version %}
{% when "26.1.2" %}
    private static void fillRect(GuiGraphicsExtractor graphics, SFMColorInputPanelLayout.Rect rect, int colour) {
{% else %}
    private static void fillRect(GuiGraphics graphics, SFMColorInputPanelLayout.Rect rect, int colour) {
{% endcase %}
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), colour);
{% endcase %}
    }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private static void outline(PoseStack poseStack, SFMColorInputPanelLayout.Rect rect, int colour) {
        GuiComponent.fill(poseStack, rect.x(), rect.y(), rect.right(), rect.y() + 1, colour);
        GuiComponent.fill(poseStack, rect.x(), rect.bottom() - 1, rect.right(), rect.bottom(), colour);
        GuiComponent.fill(poseStack, rect.x(), rect.y(), rect.x() + 1, rect.bottom(), colour);
        GuiComponent.fill(poseStack, rect.right() - 1, rect.y(), rect.right(), rect.bottom(), colour);
{% else %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
{% case minecraft_version %}
{% when "26.1.2" %}
    private static void outline(GuiGraphicsExtractor graphics, SFMColorInputPanelLayout.Rect rect, int colour) {
{% else %}
    private static void outline(GuiGraphics graphics, SFMColorInputPanelLayout.Rect rect, int colour) {
{% endcase %}
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.y() + 1, colour);
        graphics.fill(rect.x(), rect.bottom() - 1, rect.right(), rect.bottom(), colour);
        graphics.fill(rect.x(), rect.y(), rect.x() + 1, rect.bottom(), colour);
        graphics.fill(rect.right() - 1, rect.y(), rect.right(), rect.bottom(), colour);
{% endcase %}
    }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private static void centered(PoseStack poseStack, Minecraft minecraft, String text, int x, int width, int y, int colour) {
        SFMFontUtils.draw(poseStack, minecraft.font, text, x + (width - minecraft.font.width(text)) / 2, y,
{% else %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
{% case minecraft_version %}
{% when "26.1.2" %}
    private static void centered(GuiGraphicsExtractor graphics, Minecraft minecraft, String text, int x, int width, int y, int colour) {
{% else %}
    private static void centered(GuiGraphics graphics, Minecraft minecraft, String text, int x, int width, int y, int colour) {
{% endcase %}
        SFMFontUtils.draw(graphics, minecraft.font, text, x + (width - minecraft.font.width(text)) / 2, y,
{% endcase %}
                colour, false);
    }
    private static String fitText(Minecraft minecraft, String text, int width) {
        if (minecraft.font.width(text) <= width) return text;
        String suffix = "…";
        int end = text.length();
        while (end > 0 && minecraft.font.width(text.substring(0, end) + suffix) > width) end--;
        return text.substring(0, end) + suffix;
    }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private static void renderButton(PoseStack poseStack, Minecraft minecraft, SFMColorInputPanelLayout.Rect rect,
{% else %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
{% case minecraft_version %}
{% when "26.1.2" %}
    private static void renderButton(GuiGraphicsExtractor graphics, Minecraft minecraft, SFMColorInputPanelLayout.Rect rect,
{% else %}
    private static void renderButton(GuiGraphics graphics, Minecraft minecraft, SFMColorInputPanelLayout.Rect rect,
{% endcase %}
{% endcase %}
                                     String text, boolean selected) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        fill(poseStack, rect, BUTTON);
        outline(poseStack, rect, selected ? FOCUSED : BORDER);
        centered(poseStack, minecraft, text, rect.x(), rect.width(), rect.y() + 6, TEXT);
{% else %}
        fillRect(graphics, rect, BUTTON);
        outline(graphics, rect, selected ? FOCUSED : BORDER);
        centered(graphics, minecraft, text, rect.x(), rect.width(), rect.y() + 6, TEXT);
{% endcase %}
    }

{% if features.single_line_input %}
    private enum Drag { NONE, FIELD, VALUE, HEX }
{% else %}
    private enum Drag { NONE, FIELD, VALUE }
{% endif %}
    private enum Focus {
        FIELD("hue and saturation", -1), VALUE("value", -1), HEX("hexadecimal", -1),
        ALPHA("alpha", 0), RED("red", 1), GREEN("green", 2), BLUE("blue", 3),
        RECENTS("recent colours", -1), RESET("reset", -1), CANCEL("cancel", -1), CONFIRM("confirm", -1);
        private final String label;
        private final int channel;
        Focus(String label, int channel) { this.label = label; this.channel = channel; }
        static Focus forChannel(int channel) { return values()[ALPHA.ordinal() + channel]; }
    }
}
