package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.action.SFMClientAction;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionExecutor;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.client.screen.widget.SFMButtonBuilder;
import ca.teamdman.sfm.client.theme.SFMClientTheme;
import ca.teamdman.sfm.client.theme.SFMClientThemeService;
import ca.teamdman.sfm.client.theme.SFMColourRole;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** A small action-backed chooser with no free-form command or suggestion surface. */
public final class SFMActionChoiceScreen extends Screen implements SFMTransientActionScreen {
    private static final int WIDTH = 460;
    private static final int ROW_HEIGHT = 24;
    private static final int MARGIN = 12;

    private final @Nullable Screen origin;
    private final boolean pushed;
    private final List<SFMActionChoice> candidates;
    private final SFMClientActionContext actionContext;
    private List<SFMActionChoice> choices = List.of();
    private final List<Button> choiceButtons = new ArrayList<>();
    private int selected;
    private String error = "";
    private boolean closing;

    private SFMActionChoiceScreen(
            @Nullable Screen origin,
            Component title,
            List<SFMActionChoice> candidates,
            boolean pushed
    ) {
        super(title);
        this.origin = origin;
        this.pushed = pushed;
        this.candidates = List.copyOf(candidates);
        this.actionContext = SFMClientActionContext.create(
                origin,
                () -> Minecraft.getInstance().screen == this);
    }

    public static void open(Component title, List<SFMActionChoice> candidates) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen origin = minecraft.screen;
        SFMActionChoiceScreen chooser = new SFMActionChoiceScreen(
                origin, title, candidates, origin != null);
        SFMScreenChangeHelpers.setOrPushScreen(chooser);
    }

    /** Exact commands currently exposed by this bounded surface, for puppets. */
    public List<String> commandsForAutomation() {
        return choices.stream().map(SFMActionChoice::command).toList();
    }

    /** Exercises the real widget mouse path for one exact bounded command. */
    public void clickChoiceForAutomation(String command) {
        int index = -1;
        for (int candidate = 0; candidate < choices.size(); candidate++) {
            if (choices.get(candidate).command().equals(command)) {
                index = candidate;
                break;
            }
        }
        if (index < 0 || index >= choiceButtons.size()) {
            throw new IllegalArgumentException("Bounded chooser does not contain " + command);
        }
        Button button = choiceButtons.get(index);
        double x = button.x + button.getWidth() / 2.0D;
        double y = button.y + button.getHeight() / 2.0D;
        if (!mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            throw new IllegalStateException("Bounded chooser rejected mouse press for " + command);
        }
        mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    @Override
    protected void init() {
        choices = SFMActionChoiceCatalog.available(
                candidates,
                id -> SFMClientActions.registry().get(id),
                SFMClientActions.commandTree(),
                actionContext);
        selected = choices.isEmpty() ? -1 : Math.min(selected, choices.size() - 1);
        choiceButtons.clear();
        int left = panelLeft();
        int top = panelTop();
        for (int index = 0; index < choices.size(); index++) {
            SFMActionChoice choice = choices.get(index);
            SFMClientAction<?> action = SFMClientActions.registry().get(choice.actionId());
            if (action == null) continue;
            int choiceIndex = index;
            Button button = addRenderableWidget(new SFMButtonBuilder()
                    .setPosition(left + 10, top + 30 + index * ROW_HEIGHT)
                    .setSize(panelWidth() - 20, 20)
                    .setText(action.title())
                    .setTooltip(this, font, action.description())
                    .setOnPress(ignored -> execute(choiceIndex))
                    .build());
            choiceButtons.add(button);
        }
        if (!choiceButtons.isEmpty()) {
            setInitialFocus(choiceButtons.get(Math.max(0, selected)));
            setFocused(choiceButtons.get(Math.max(0, selected)));
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            dismissActionSurface();
            return true;
        }
        if (!choices.isEmpty() && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN)) {
            int direction = keyCode == GLFW.GLFW_KEY_UP ? -1 : 1;
            selected = Math.floorMod(selected + direction, choices.size());
            setFocused(choiceButtons.get(selected));
            return true;
        }
        if (!choices.isEmpty()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            execute(selected);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        SFMClientTheme theme = SFMClientThemeService.active();
        fill(poseStack, 0, 0, width, height, theme.colour(SFMColourRole.SCREEN_OVERLAY));
        int left = panelLeft();
        int top = panelTop();
        int right = left + panelWidth();
        int bottom = top + panelHeight();
        fill(poseStack, left, top, right, bottom, theme.colour(SFMColourRole.PANEL_BACKGROUND));
        outline(poseStack, left, top, right, bottom, theme.colour(SFMColourRole.PANEL_BORDER));
        SFMFontUtils.draw(poseStack, font, title, left + 10, top + 10,
                theme.colour(SFMColourRole.TEXT_PRIMARY), false);
        if (choices.isEmpty()) {
            SFMFontUtils.draw(poseStack, font, "No actions are available in this context",
                    left + 10, top + 34, theme.colour(SFMColourRole.TEXT_MUTED), false);
        }
        if (!error.isBlank()) {
            SFMFontUtils.draw(poseStack, font, font.plainSubstrByWidth(error, panelWidth() - 20),
                    left + 10, bottom - 16, theme.colour(SFMColourRole.TEXT_ERROR), false);
        }
        super.render(poseStack, mouseX, mouseY, partialTick);
        if (selected >= 0 && selected < choices.size()) {
            String command = choices.get(selected).command();
            SFMFontUtils.draw(poseStack, font, font.plainSubstrByWidth(command, panelWidth() - 20),
                    left + 10, bottom - (error.isBlank() ? 16 : 30),
                    theme.colour(SFMColourRole.TEXT_MUTED), false);
        }
    }

    private void execute(int index) {
        if (index < 0 || index >= choices.size()) return;
        error = "";
        try {
            int result = SFMClientActionExecutor.execute(
                    choices.get(index).command(), actionContext, ignored -> {
                    });
            if (result > 0 && Minecraft.getInstance().screen == this) dismissActionSurface();
        } catch (CommandSyntaxException | RuntimeException exception) {
            SFM.LOGGER.warn("Bounded action choice failed: {}", choices.get(index).command(), exception);
            error = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        }
    }

    @Override
    public void onClose() {
        dismissActionSurface();
    }

    @Override
    public void dismissActionSurface() {
        if (closing || Minecraft.getInstance().screen != this) return;
        closing = true;
        if (pushed) SFMScreenChangeHelpers.popScreen();
        else SFMScreenChangeHelpers.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private int panelWidth() {
        return Math.min(WIDTH, width - MARGIN * 2);
    }

    private int panelHeight() {
        return Math.min(height - MARGIN * 2, Math.max(76, 58 + choices.size() * ROW_HEIGHT));
    }

    private int panelLeft() {
        return (width - panelWidth()) / 2;
    }

    private int panelTop() {
        return (height - panelHeight()) / 2;
    }

    private static void outline(PoseStack poseStack, int left, int top, int right, int bottom, int colour) {
        fill(poseStack, left, top, right, top + 1, colour);
        fill(poseStack, left, bottom - 1, right, bottom, colour);
        fill(poseStack, left, top, left + 1, bottom, colour);
        fill(poseStack, right - 1, top, right, bottom, colour);
    }
}
