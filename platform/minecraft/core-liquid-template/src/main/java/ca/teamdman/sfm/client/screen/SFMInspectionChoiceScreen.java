package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Small native choice surface for inspection projections without the typed palette. */
public final class SFMInspectionChoiceScreen extends Screen {
    private final SFMClientActionContext origin;
    private final List<SFMActionChoice> choices;
    private int first;
    private int visible;
    private String error = "";
    private boolean closed;

    private SFMInspectionChoiceScreen(SFMClientActionContext origin, Component title, List<SFMActionChoice> choices) {
        super(title);
        this.origin = origin;
        this.choices = List.copyOf(choices);
    }

    public static void open(SFMClientActionContext origin, Component title, List<SFMActionChoice> choices) {
        if (!origin.originatingHostIsCurrent().getAsBoolean()) return;
        SFMScreenChangeHelpers.setOrPushScreen(new SFMInspectionChoiceScreen(origin, title, choices));
    }

    public SFMClientActionContext originatingActionContext() { return origin; }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP || key == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN) {
            first = Math.max(0, Math.min(first + (key == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP ? -visible : visible),
                    Math.max(0, choices.size() - visible)));
            init();
            if (!children().isEmpty()) setFocused(children().get(0));
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    protected void init() {
        clearWidgets();
        int buttonWidth = Math.max(40, Math.min(420, width - 32));
        int left = (width - buttonWidth) / 2;
        visible = Math.max(1, (height - 100) / 24);
        first = Math.max(0, Math.min(first, Math.max(0, choices.size() - visible)));
        for (int i = first; i < Math.min(choices.size(), first + visible); i++) {
            SFMActionChoice choice = choices.get(i);
            addRenderableWidget(Button.builder(Component.literal(choice.displayText()), ignored -> choose(choice))
                    .bounds(left, 36 + (i - first) * 24, buttonWidth, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> onClose())
                .bounds(left, height - 28, buttonWidth, 20).build());
    }

    private void choose(SFMActionChoice choice) {
        if (closed || Minecraft.getInstance().screen != this) return;
        if (!origin.originatingHostIsCurrent().getAsBoolean()) {
            error = "The originating Explorer is no longer available.";
            return;
        }
        if (choice.continuation()) {
            onClose();
            SFMCommandPaletteScreen.open(origin, choice.command());
            return;
        }
        try {
            int result = SFMClientActionExecutor.execute(choice.command(), origin,
                    feedback -> error = feedback.getString());
            if (result > 0 && Minecraft.getInstance().screen == this) onClose();
        } catch (Exception failure) {
            error = String.valueOf(failure.getMessage());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xe0181818);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xffffffff);
        if (choices.isEmpty()) graphics.drawCenteredString(font, "No actions available", width / 2, 40, 0xffaaaaaa);
        if (!error.isEmpty()) graphics.drawString(font, font.plainSubstrByWidth(error, Math.max(0, width - 24)),
                12, height - 44, 0xffff7777);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical == 0) return false;
        int next = Math.max(0, Math.min(first - (int) Math.signum(vertical), Math.max(0, choices.size() - visible)));
        if (next != first) { first = next; init(); }
        return true;
    }

    @Override
    public void onClose() {
        if (closed) return;
        closed = true;
        if (Minecraft.getInstance().screen == this) SFMScreenChangeHelpers.popScreen();
    }

    @Override
    public void removed() { closed = true; }
}
