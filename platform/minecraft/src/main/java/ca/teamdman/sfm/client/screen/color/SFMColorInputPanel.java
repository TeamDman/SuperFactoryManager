package ca.teamdman.sfm.client.screen.color;

import ca.teamdman.sfm.client.input.SFMSingleLineInput;
import ca.teamdman.sfm.client.input.SFMSingleLineInputView;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.SFMGuiCrosshair;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
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
    private final SFMSingleLineInput hexInput;
    private final SFMSingleLineInputView hexView = new SFMSingleLineInputView();
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
        hexInput = new SFMSingleLineInput(hexText, 9, true, value -> value.matches("#?[0-9A-Fa-f]*"));
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
    public void render(PoseStack poseStack, Minecraft minecraft, SFMScreenPanelBounds bounds, int mouseX, int mouseY,
                       float partialTick, boolean focused) {
        SFMColorInputPanelLayout.Rect panel = layout.panel();
        fill(poseStack, panel, PANEL);
        outline(poseStack, panel, focused ? FOCUSED : BORDER);
        String heading = panel.width() < 260 ? "ARGB colour" : "Reusable ARGB colour input";
        String subtitle = panel.width() < 260 ? "HSV • hex • channels"
                : layout.compact() ? "HSV field • value • hex • channels"
                : "Hue + saturation field • value slider • typed channels";
        centered(poseStack, minecraft, heading, panel.x(), panel.width(), panel.y() + 12,
                0xFFFFAA00);
        centered(poseStack, minecraft, subtitle, panel.x(), panel.width(), panel.y() + 26, MUTED);

        renderHueSaturationField(poseStack);
        renderValueSlider(poseStack);
        renderSwatch(poseStack, minecraft);
        renderHex(poseStack, minecraft);
        renderChannels(poseStack, minecraft);
        renderRecents(poseStack, minecraft);
        renderButton(poseStack, minecraft, layout.reset(), "Reset", focus == Focus.RESET);
        renderButton(poseStack, minecraft, layout.cancel(), "Cancel", focus == Focus.CANCEL);
        renderButton(poseStack, minecraft, layout.confirm(), "Confirm", focus == Focus.CONFIRM);

        String status = diagnostic != null ? diagnostic
                : model.resolution() == SFMColorInputModel.Resolution.CONFIRMED
                ? "Confirmed typed result " + model.current().toHex(SFMArgbColor.HexOrder.ARGB)
                : model.resolution() == SFMColorInputModel.Resolution.CANCELLED
                ? "Cancelled — no result was applied"
                : "Tab navigates • arrows adjust • Enter activates";
        if (panel.height() >= 280) {
            centered(poseStack, minecraft, fitText(minecraft, status, panel.width() - 16), panel.x(), panel.width(),
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
            drag = Drag.HEX;
            moveHexCaret(mouseX, minecraft != null && net.minecraft.client.gui.screens.Screen.hasShiftDown());
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
        else if (drag == Drag.HEX) moveHexCaret(mouseX, true);
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
        if (focus == Focus.HEX && hexInput.keyPressed(keyCode, modifiers,
                () -> minecraft == null ? "" : sanitizeHex(minecraft.keyboardHandler.getClipboard()),
                value -> { if (minecraft != null) minecraft.keyboardHandler.setClipboard(value); })) {
            hexText = hexInput.text();
            diagnostic = null;
            if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) applyHex();
            return true;
        }
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
        if (character == '#' || Character.digit(character, 16) >= 0) {
            if (!hexInput.charTyped(Character.toUpperCase(character), modifiers)) return false;
            hexText = hexInput.text();
            diagnostic = null;
            return true;
        }
        return false;
    }

    private void renderHueSaturationField(PoseStack poseStack) {
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
                GuiComponent.fill(poseStack, left, top, right, bottom, rgb);
            }
        }
        outline(poseStack, field, focus == Focus.FIELD ? FOCUSED : BORDER);
        SFMArgbColor.Hsv hsv = model.current().toHsv();
        int x = field.x() + (int) Math.round(hsv.hue() * (field.width() - 1));
        int y = field.y() + (int) Math.round((1D - hsv.saturation()) * (field.height() - 1));
        SFMGuiCrosshair.draw(poseStack, x, y, 5, TEXT);
    }

    private void renderValueSlider(PoseStack poseStack) {
        SFMColorInputPanelLayout.Rect slider = layout.valueSlider();
        SFMArgbColor.Hsv hsv = model.current().toHsv();
        int columns = Math.max(1, slider.width() / 4);
        for (int i = 0; i < columns; i++) {
            int left = slider.x() + i * slider.width() / columns;
            int right = slider.x() + (i + 1) * slider.width() / columns;
            fill(poseStack, new SFMColorInputPanelLayout.Rect(left, slider.y(), right - left, slider.height()),
                    SFMArgbColor.fromHsv(255, hsv.hue(), hsv.saturation(),
                            columns == 1 ? 0D : i / (double) (columns - 1)).argb());
        }
        outline(poseStack, slider, focus == Focus.VALUE ? FOCUSED : BORDER);
        int thumb = slider.x() + (int) Math.round(hsv.value() * (slider.width() - 1));
        GuiComponent.fill(poseStack, thumb - 1, slider.y() - 2, thumb + 2, slider.bottom() + 2, TEXT);
    }

    private void renderSwatch(PoseStack poseStack, Minecraft minecraft) {
        SFMColorInputPanelLayout.Rect swatch = layout.swatch();
        int checker = 8;
        for (int y = swatch.y(); y < swatch.bottom(); y += checker) {
            for (int x = swatch.x(); x < swatch.right(); x += checker) {
                int colour = ((x / checker + y / checker) & 1) == 0 ? 0xFF555555 : 0xFFAAAAAA;
                GuiComponent.fill(poseStack, x, y, Math.min(x + checker, swatch.right()),
                        Math.min(y + checker, swatch.bottom()), colour);
            }
        }
        fill(poseStack, swatch, model.current().argb());
        outline(poseStack, swatch, BORDER);
        if (swatch.width() >= 80) {
            centered(poseStack, minecraft, model.current().toHex(SFMArgbColor.HexOrder.ARGB), swatch.x(),
                    swatch.width(), swatch.y() + (swatch.height() - 8) / 2, TEXT);
        }
    }

    private void renderHex(PoseStack poseStack, Minecraft minecraft) {
        fill(poseStack, layout.hex(), 0xFF101419);
        outline(poseStack, layout.hex(), focus == Focus.HEX ? FOCUSED : BORDER);
        hexView.render(poseStack, minecraft.font, hexInput, layout.hex().x() + 4, layout.hex().y() + 6,
                layout.hex().width() - 8, focus == Focus.HEX
                        && model.resolution() == SFMColorInputModel.Resolution.EDITING, "", TEXT, MUTED);
        renderButton(poseStack, minecraft, layout.order(), model.hexOrder().name(), false);
    }

    private void renderChannels(PoseStack poseStack, Minecraft minecraft) {
        String[] names = {"A", "R", "G", "B"};
        int[] values = {model.current().alpha(), model.current().red(), model.current().green(), model.current().blue()};
        int rowHeight = layout.channels().height() / 4;
        for (int i = 0; i < 4; i++) {
            int y = layout.channels().y() + i * rowHeight;
            int textY = y + Math.max(1, (rowHeight - 8) / 2);
            boolean selected = focus.channel == i;
            if (selected) GuiComponent.fill(poseStack, layout.channels().x(), y,
                    layout.channels().right(), y + rowHeight - 1, 0x4433FFFF);
            SFMFontUtils.draw(poseStack, minecraft.font, names[i] + "  " + values[i],
                    layout.channels().x() + 3, textY, selected ? FOCUSED : TEXT, false);
            int minusX = layout.channels().right() - 38;
            GuiComponent.fill(poseStack, minusX, y + 1, minusX + 17, y + rowHeight - 2, BUTTON);
            GuiComponent.fill(poseStack, minusX + 20, y + 1, minusX + 37, y + rowHeight - 2, BUTTON);
            centered(poseStack, minecraft, "−", minusX, 17, textY, TEXT);
            centered(poseStack, minecraft, "+", minusX + 20, 17, textY, TEXT);
        }
    }

    private void renderRecents(PoseStack poseStack, Minecraft minecraft) {
        if (layout.panel().height() >= 280) {
            SFMFontUtils.draw(poseStack, minecraft.font, "Recent", layout.recents().x(), layout.recents().y() - 11,
                    MUTED, false);
        }
        int size = Math.min(20, layout.recents().height());
        for (int i = 0; i < model.recent().size(); i++) {
            int x = layout.recents().x() + i * (size + 4);
            if (x + size > layout.recents().right()) break;
            SFMColorInputPanelLayout.Rect rect = new SFMColorInputPanelLayout.Rect(x, layout.recents().y(), size, size);
            fill(poseStack, rect, model.recent().get(i).argb());
            outline(poseStack, rect, focus == Focus.RECENTS && recentIndex == i ? FOCUSED : BORDER);
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
        hexInput.setText(hexText);
        diagnostic = null;
    }

    private void moveHexCaret(double x, boolean extend) {
        if (minecraft == null) return;
        int at = hexView.indexAt(hexInput, minecraft.font, layout.hex().width() - 8, x - layout.hex().x() - 4);
        hexInput.select(extend ? hexInput.anchor() : at, at);
    }

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
    private static void fill(PoseStack poseStack, SFMColorInputPanelLayout.Rect rect, int colour) {
        GuiComponent.fill(poseStack, rect.x(), rect.y(), rect.right(), rect.bottom(), colour);
    }
    private static void outline(PoseStack poseStack, SFMColorInputPanelLayout.Rect rect, int colour) {
        GuiComponent.fill(poseStack, rect.x(), rect.y(), rect.right(), rect.y() + 1, colour);
        GuiComponent.fill(poseStack, rect.x(), rect.bottom() - 1, rect.right(), rect.bottom(), colour);
        GuiComponent.fill(poseStack, rect.x(), rect.y(), rect.x() + 1, rect.bottom(), colour);
        GuiComponent.fill(poseStack, rect.right() - 1, rect.y(), rect.right(), rect.bottom(), colour);
    }
    private static void centered(PoseStack poseStack, Minecraft minecraft, String text, int x, int width, int y, int colour) {
        SFMFontUtils.draw(poseStack, minecraft.font, text, x + (width - minecraft.font.width(text)) / 2, y,
                colour, false);
    }
    private static String fitText(Minecraft minecraft, String text, int width) {
        if (minecraft.font.width(text) <= width) return text;
        String suffix = "…";
        int end = text.length();
        while (end > 0 && minecraft.font.width(text.substring(0, end) + suffix) > width) end--;
        return text.substring(0, end) + suffix;
    }
    private static void renderButton(PoseStack poseStack, Minecraft minecraft, SFMColorInputPanelLayout.Rect rect,
                                     String text, boolean selected) {
        fill(poseStack, rect, BUTTON);
        outline(poseStack, rect, selected ? FOCUSED : BORDER);
        centered(poseStack, minecraft, text, rect.x(), rect.width(), rect.y() + 6, TEXT);
    }

    private enum Drag { NONE, FIELD, VALUE, HEX }
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
