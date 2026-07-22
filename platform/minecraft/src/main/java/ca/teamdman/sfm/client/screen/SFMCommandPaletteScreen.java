package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.keybinding.SFMKeyBinding;
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingDisplay;
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingService;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.client.screen.widget.SFMButtonBuilder;
import ca.teamdman.sfm.client.screen.widget.SFMConsoleWidget;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The first, deliberately small, presentation of SFM's contextual action
 * registry.  The screen is pushed over an existing screen when possible and
 * otherwise replaces the in-world view, so closing it always restores the
 * view from which the palette was opened.
 */
public final class SFMCommandPaletteScreen extends Screen {
    public static final String DEFAULT_QUERY = "sfm action invoke ";

    @SFMLocalizationDatagen
    public static final LocalizationEntry TITLE = new LocalizationEntry(
            "gui.sfm.client_action.palette.title",
            "Command Palette"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry INPUT_PLACEHOLDER = new LocalizationEntry(
            "gui.sfm.client_action.palette.placeholder",
            "Type a local SFM action..."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry EMPTY_RESULTS = new LocalizationEntry(
            "gui.sfm.client_action.palette.empty",
            "No available sub-actions"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry ACCEPT_SUGGESTION = new LocalizationEntry(
            "gui.sfm.client_action.palette.accept_suggestion",
            "Press %s to accept the suggestion"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry EXECUTE = new LocalizationEntry(
            "gui.sfm.client_action.palette.execute",
            "Execute"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry EXECUTION_FAILED = new LocalizationEntry(
            "gui.sfm.client_action.palette.execution_failed",
            "Command could not be executed: %s"
    );

    private static final int PANEL = 0xF0202020;
    private static final int BORDER = 0xFF707070;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFB0B0B0;
    private static final int ERROR = 0xFFFF5555;
    private static final int MAX_SUGGESTIONS = 8;
    private static final int CONSOLE_HEIGHT = 72;
    private static final int EMPTY_CONSOLE_HEIGHT = 18;
    private static final int PANEL_MARGIN = 12;
    private static final int PANEL_BASE_HEIGHT = 92;
    private static final int SUGGESTION_ROW_HEIGHT = 18;

    private static @Nullable SFMCommandPaletteScreen ACTIVE;

    private final SFMClientActionContext actionContext;
    private final boolean pushed;
    private final String initialQuery;

    @SuppressWarnings("NotNullFieldNotInitialized")
    private EditBox input;
    @SuppressWarnings("NotNullFieldNotInitialized")
    private Button executeButton;
    @SuppressWarnings("NotNullFieldNotInitialized")
    private SFMConsoleWidget consoleWidget;
    private List<Suggestion> suggestions = List.of();
    private final List<Component> feedback = new ArrayList<>();
    private int selectedSuggestion = -1;
    private int firstVisibleSuggestion;
    private String error = "";
    private long suggestionRevision;
    private long bindingCycleTicks;
    private boolean closing;

    private SFMCommandPaletteScreen(
            SFMClientActionContext actionContext,
            String initialQuery,
            boolean pushed
    ) {
        super(TITLE.getComponent());
        this.actionContext = actionContext;
        this.initialQuery = initialQuery.isBlank() ? DEFAULT_QUERY : initialQuery;
        this.pushed = pushed;
    }

    public static SFMClientActionContext createOriginContext() {
        Screen origin = SFMScreenChangeHelpers.getCurrentScreen();
        return SFMClientActionContext.create(origin, () -> isOriginStillActive(origin));
    }

    private static boolean isOriginStillActive(@Nullable Screen origin) {
        return ACTIVE != null
                && ACTIVE.actionContext.originatingHost() == origin
                && Minecraft.getInstance().screen == ACTIVE;
    }

    public static void open(
            SFMClientActionContext actionContext,
            String initialQuery
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof SFMCommandPaletteScreen palette) {
            palette.input.setValue(initialQuery);
            palette.setFocused(palette.input);
            return;
        }
        boolean pushed = minecraft.screen != null;
        SFMCommandPaletteScreen palette = new SFMCommandPaletteScreen(actionContext, initialQuery, pushed);
        ACTIVE = palette;
        SFMScreenChangeHelpers.setOrPushScreen(palette);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        bindingCycleTicks++;
    }

    @Override
    protected void init() {
        super.init();
        ACTIVE = this;
        SFMScreenRenderUtils.enableKeyRepeating();
        int width = Math.min(460, this.width - 24);
        int left = (this.width - width) / 2;
        int top = panelTop();
        int executeWidth = 68;
        int horizontalPadding = 10;
        int inputWidth = width - horizontalPadding * 2 - executeWidth - 4;
        this.input = this.addRenderableWidget(new EditBox(
                this.font,
                left + horizontalPadding,
                top + 30,
                inputWidth,
                20,
                INPUT_PLACEHOLDER.getComponent()
        ));
        this.input.setMaxLength(2048);
        this.input.setValue(this.initialQuery);
        this.input.setSuggestion("");
        this.input.setResponder(this::refreshSuggestions);
        this.executeButton = this.addRenderableWidget(new SFMButtonBuilder()
                .setPosition(left + width - horizontalPadding - executeWidth, top + 30)
                .setSize(executeWidth, 20)
                .setText(EXECUTE)
                .setOnPress(button -> executeInput())
                .build());
        this.executeButton.active = false;
        this.consoleWidget = new SFMConsoleWidget(
                this.font,
                left + horizontalPadding,
                consoleTop(top),
                width - horizontalPadding * 2,
                consoleHeight()
        );
        layoutWidgets();
        this.setInitialFocus(this.input);
        this.setFocused(this.input);
        this.input.setFocused(true);
        refreshSuggestions(this.initialQuery);
    }

    @Override
    public void onClose() {
        if (this.closing) return;
        this.closing = true;
        if (ACTIVE == this) ACTIVE = null;
        if (this.pushed) {
            SFMScreenChangeHelpers.popScreen();
        } else {
            SFMScreenChangeHelpers.setScreen(null);
        }
    }

    @Override
    public void removed() {
        if (ACTIVE == this) ACTIVE = null;
        super.removed();
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            executeInput();
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            if (!this.input.isFocused()) {
                this.setFocused(this.input);
                this.input.setFocused(true);
                return true;
            }
            applySelectedSuggestion();
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            if (!suggestions.isEmpty()) {
                int delta = key == GLFW.GLFW_KEY_UP ? -1 : 1;
                int next = selectedSuggestion < 0 ? 0 : selectedSuggestion + delta;
                selectedSuggestion = Math.max(0, Math.min(suggestions.size() - 1, next));
                ensureSelectedSuggestionVisible();
                return true;
            }
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.consoleWidget.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int left = panelLeft();
        int top = panelTop();
        int width = panelWidth();
        if (mouseX >= left && mouseX <= left + width) {
            int suggestionTop = top + 72;
            int visibleIndex = (int) ((mouseY - suggestionTop) / SUGGESTION_ROW_HEIGHT);
            int suggestionIndex = firstVisibleSuggestion + visibleIndex;
            if (visibleIndex >= 0
                    && visibleIndex < visibleSuggestionCount()
                    && suggestionIndex < suggestions.size()) {
                if (mouseX >= left + width - 28) {
                    Optional<ResourceLocation> actionId = suggestionActionId(suggestions.get(suggestionIndex));
                    if (actionId.isPresent()) {
                        SFMScreenChangeHelpers.setOrPushScreen(new SFMKeyBindingDetailsScreen(this, actionId.get()));
                        return true;
                    }
                }
                selectedSuggestion = suggestionIndex;
                applySelectedSuggestion();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.consoleWidget.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        if (this.consoleWidget.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.consoleWidget.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        layoutWidgets();
        fill(poseStack, 0, 0, this.width, this.height, 0x66000000);
        int left = panelLeft();
        int top = panelTop();
        int right = left + panelWidth();
        int bottom = top + panelHeight();
        fill(poseStack, left, top, right, bottom, PANEL);
        fill(poseStack, left, top, right, top + 1, BORDER);
        fill(poseStack, left, bottom - 1, right, bottom, BORDER);
        fill(poseStack, left, top, left + 1, bottom, BORDER);
        fill(poseStack, right - 1, top, right, bottom, BORDER);

        SFMFontUtils.draw(poseStack, this.font, TITLE.getComponent().withStyle(ChatFormatting.BOLD), left + 10, top + 12, TEXT, false);
        SFMFontUtils.draw(
                poseStack,
                this.font,
                ACCEPT_SUGGESTION.getComponent(Component.literal("Tab").withStyle(ChatFormatting.AQUA)),
                left + 10,
                top + 54,
                MUTED,
                false
        );
        int visibleSuggestions = visibleSuggestionCount();
        if (suggestions.isEmpty()) {
            SFMFontUtils.draw(poseStack, this.font, EMPTY_RESULTS.getComponent(), left + 10, top + 72, MUTED, false);
        } else {
            for (int i = 0; i < visibleSuggestions; i++) {
                int suggestionIndex = firstVisibleSuggestion + i;
                if (suggestionIndex >= suggestions.size()) break;
                int y = top + 72 + i * SUGGESTION_ROW_HEIGHT;
                if (suggestionIndex == selectedSuggestion) {
                    fill(poseStack, left + 6, y - 2, right - 6, y + 14, 0xFF404040);
                }
                Suggestion suggestion = suggestions.get(suggestionIndex);
                SFMFontUtils.draw(poseStack, this.font, truncateSuggestion(suggestion), left + 12, y, TEXT, false);
                renderBindingSummary(poseStack, suggestion, right, y);
            }
        }
        if (!this.error.isEmpty()) {
            SFMFontUtils.draw(
                    poseStack,
                    this.font,
                    truncateToPanel(this.error),
                    left + 10,
                    consoleTop(top) - 12,
                    ERROR,
                    false
            );
        }
        this.consoleWidget.replaceLines(this.feedback);
        this.consoleWidget.render(poseStack, mouseX, mouseY, partialTick);
        super.render(poseStack, mouseX, mouseY, partialTick);
        renderActionDetailsTooltip(poseStack, mouseX, mouseY);
    }

    private void renderBindingSummary(PoseStack poseStack, Suggestion suggestion, int right, int y) {
        Optional<ResourceLocation> actionId = suggestionActionId(suggestion);
        if (actionId.isEmpty()) return;
        List<SFMKeyBinding> bindings = SFMKeyBindingService.INSTANCE.bindingsForAction(actionId.get()).stream()
                .filter(SFMKeyBinding::enabled)
                .toList();
        String bindingText = "";
        if (!bindings.isEmpty()) {
            int index = bindings.size() == 1
                    ? 0
                    : (int) ((bindingCycleTicks / 20L) % bindings.size());
            bindingText = SFMKeyBindingDisplay.format(bindings.get(index).sequence());
        }
        String suffix = bindingText.isEmpty() ? "[?]" : bindingText + "  [?]";
        SFMFontUtils.draw(poseStack, font, suffix, right - 12 - font.width(suffix), y, 0xFF80D8FF, false);
    }

    private void renderActionDetailsTooltip(PoseStack poseStack, int mouseX, int mouseY) {
        int right = panelLeft() + panelWidth();
        int firstY = panelTop() + 70;
        int visibleIndex = (mouseY - firstY) / SUGGESTION_ROW_HEIGHT;
        int suggestionIndex = firstVisibleSuggestion + visibleIndex;
        if (mouseX < right - 28 || mouseX > right - 6 || visibleIndex < 0
                || visibleIndex >= visibleSuggestionCount() || suggestionIndex >= suggestions.size()) return;
        Optional<ResourceLocation> actionId = suggestionActionId(suggestions.get(suggestionIndex));
        if (actionId.isEmpty()) return;
        var action = SFMClientActions.registry().get(actionId.get());
        if (action == null) return;
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(action.title().copy().withStyle(ChatFormatting.AQUA));
        tooltip.add(action.description());
        List<SFMKeyBinding> bindings = SFMKeyBindingService.INSTANCE.bindingsForAction(actionId.get());
        if (bindings.isEmpty()) tooltip.add(Component.literal("No key bindings").withStyle(ChatFormatting.GRAY));
        else bindings.forEach(binding -> tooltip.add(Component.literal(SFMKeyBindingDisplay.format(binding.sequence()))));
        renderComponentTooltip(poseStack, tooltip, mouseX, mouseY);
    }

    private String truncateSuggestion(Suggestion suggestion) {
        return font.plainSubstrByWidth(suggestion.getText(), Math.max(20, panelWidth() - 150));
    }

    private static Optional<ResourceLocation> suggestionActionId(Suggestion suggestion) {
        try {
            ResourceLocation id = new ResourceLocation(suggestion.getText());
            return SFMClientActions.registry().get(id) == null ? Optional.empty() : Optional.of(id);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private int panelWidth() {
        return Math.min(460, this.width - 24);
    }

    private int panelLeft() {
        return (this.width - panelWidth()) / 2;
    }

    private int panelTop() {
        return Math.max(PANEL_MARGIN, this.height / 2 - panelHeight() / 2);
    }

    private int panelHeight() {
        return PANEL_BASE_HEIGHT + visibleSuggestionCount() * SUGGESTION_ROW_HEIGHT + consoleHeight();
    }

    private int consoleTop(int panelTop) {
        return panelTop + 72 + visibleSuggestionCount() * SUGGESTION_ROW_HEIGHT + 10;
    }

    private int visibleSuggestionCount() {
        int availableRows = Math.max(
                1,
                (this.height - PANEL_MARGIN * 2 - PANEL_BASE_HEIGHT - desiredConsoleHeight())
                        / SUGGESTION_ROW_HEIGHT
        );
        int suggestionCount = Math.min(MAX_SUGGESTIONS, this.suggestions.size());
        return Math.max(1, Math.min(availableRows, suggestionCount == 0 ? 1 : suggestionCount));
    }

    private int consoleHeight() {
        int availableHeight = this.height
                - PANEL_MARGIN * 2
                - PANEL_BASE_HEIGHT
                - visibleSuggestionCount() * SUGGESTION_ROW_HEIGHT;
        return Math.max(EMPTY_CONSOLE_HEIGHT, Math.min(desiredConsoleHeight(), availableHeight));
    }

    private int desiredConsoleHeight() {
        return this.feedback.isEmpty() ? EMPTY_CONSOLE_HEIGHT : CONSOLE_HEIGHT;
    }

    private void layoutWidgets() {
        if (this.input == null || this.executeButton == null || this.consoleWidget == null) {
            return;
        }
        int left = panelLeft();
        int top = panelTop();
        int width = panelWidth();
        int horizontalPadding = 10;
        int executeWidth = 68;
        this.input.setX(left + horizontalPadding);
        this.input.y = top + 30;
        this.executeButton.x = left + width - horizontalPadding - executeWidth;
        this.executeButton.y = top + 30;
        this.consoleWidget.setBounds(
                left + horizontalPadding,
                consoleTop(top),
                width - horizontalPadding * 2,
                consoleHeight()
        );
        ensureSelectedSuggestionVisible();
    }

    private void ensureSelectedSuggestionVisible() {
        if (this.suggestions.isEmpty() || this.selectedSuggestion < 0) {
            this.firstVisibleSuggestion = 0;
            return;
        }
        int visibleCount = visibleSuggestionCount();
        int maximumFirstVisible = Math.max(0, this.suggestions.size() - visibleCount);
        if (this.selectedSuggestion < this.firstVisibleSuggestion) {
            this.firstVisibleSuggestion = this.selectedSuggestion;
        } else if (this.selectedSuggestion >= this.firstVisibleSuggestion + visibleCount) {
            this.firstVisibleSuggestion = this.selectedSuggestion - visibleCount + 1;
        }
        this.firstVisibleSuggestion = Math.max(
                0,
                Math.min(maximumFirstVisible, this.firstVisibleSuggestion)
        );
    }

    private String truncateToPanel(String value) {
        int availableWidth = Math.max(0, panelWidth() - 20);
        if (this.font.width(value) <= availableWidth) return value;
        String ellipsis = "...";
        return this.font.plainSubstrByWidth(
                value,
                Math.max(0, availableWidth - this.font.width(ellipsis))
        ) + ellipsis;
    }

    private String normalizedCommand() {
        String value = commandInput();
        return value.trim();
    }

    private String commandInput() {
        String value = this.input.getValue();
        return value.startsWith("/") ? value.substring(1) : value;
    }

    private void refreshSuggestions(String ignored) {
        String command = commandInput();
        this.error = "";
        this.input.setSuggestion(command.isEmpty() ? INPUT_PLACEHOLDER.getString() : "");
        long revision = ++this.suggestionRevision;
        var tree = SFMClientActions.commandTree();
        ParseResults<SFMClientActionSource> parsed = tree.parse(
                command,
                new SFMClientActionSource(this.actionContext)
        );
        this.executeButton.active = isExecutable(parsed);
        tree.getCompletionSuggestions(parsed).thenAccept(result -> Minecraft.getInstance().execute(() -> {
            if (ACTIVE != this || revision != this.suggestionRevision) return;
            this.suggestions = result.getList();
            this.selectedSuggestion = this.suggestions.isEmpty() ? -1 : 0;
            this.firstVisibleSuggestion = 0;
            layoutWidgets();
        }));
    }

    private boolean isExecutable(ParseResults<SFMClientActionSource> parsed) {
        return !parsed.getReader().canRead()
                && parsed.getExceptions().isEmpty()
                && parsed.getContext().getCommand() != null;
    }

    private void applySelectedSuggestion() {
        if (this.selectedSuggestion < 0 || this.selectedSuggestion >= this.suggestions.size()) return;
        String current = commandInput();
        String value = this.suggestions.get(this.selectedSuggestion).apply(current);
        this.input.setValue(value);
        this.input.moveCursorToEnd();
    }

    private void executeInput() {
        String command = normalizedCommand();
        if (command.isBlank()) return;
        try {
            executeCommand(command);
        } catch (CommandSyntaxException exception) {
            SFM.LOGGER.warn("Command palette command could not be executed: " + command, exception);
            this.error = EXECUTION_FAILED.getComponent(exception.getMessage()).getString();
        } catch (RuntimeException exception) {
            SFM.LOGGER.error("Command palette action failed: " + command, exception);
            this.error = EXECUTION_FAILED.getComponent(exception.getMessage()).getString();
        }
    }

    private void executeCommand(String command) throws CommandSyntaxException {
        this.feedback.clear();
        SFMClientActions.commandTree().execute(
                command,
                new SFMClientActionSource(this.actionContext, this.feedback::add)
        );
        this.error = "";
        resetToDefaultQuery();
    }

    /**
     * Supplies a command and executes it for the client puppet harness.
     *
     * <p>The harness uses the same Brigadier path as a user typing into the
     * palette; it does not invoke an action implementation directly.</p>
     */
    public void executeCommandForAutomation(String command) {
        this.input.setValue(command);
        this.input.moveCursorToEnd();
        String normalized = normalizedCommand();
        try {
            executeCommand(normalized);
        } catch (CommandSyntaxException exception) {
            throw new IllegalStateException(
                    "Command palette automation command could not be executed: " + normalized,
                    exception
            );
        }
    }

    private void resetToDefaultQuery() {
        this.input.setValue(DEFAULT_QUERY);
        this.input.moveCursorToEnd();
        this.setFocused(this.input);
        this.input.setFocused(true);
    }

}
