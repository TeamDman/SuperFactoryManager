package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.action.SFMClientActionCommandTree;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionExecutor;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.action.SFMClientCommandInsertion;
import ca.teamdman.sfm.client.presentation.SFMItemIconRenderer;
import ca.teamdman.sfm.client.presentation.SFMItemIconResolver;
import ca.teamdman.sfm.client.keybinding.SFMKeyBinding;
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingCycle;
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingDisplay;
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingService;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.client.screen.widget.SFMButtonBuilder;
import ca.teamdman.sfm.client.screen.widget.SFMConsoleWidget;
import ca.teamdman.sfm.client.screen.widget.SFMVerticalListViewport;
import ca.teamdman.sfm.client.theme.SFMClientTheme;
import ca.teamdman.sfm.client.theme.SFMClientThemeService;
import ca.teamdman.sfm.client.theme.SFMColourRole;
import ca.teamdman.sfm.client.text_styling.ProgramSyntaxHighlightingHelper;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
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
import java.util.OptionalInt;

/**
 * The first, deliberately small, presentation of SFM's contextual action
 * registry.  The screen is pushed over an existing screen when possible and
 * otherwise replaces the in-world view, so closing it always restores the
 * view from which the palette was opened.
 */
public final class SFMCommandPaletteScreen extends Screen implements SFMTransientActionScreen {
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

    @SFMLocalizationDatagen
    public static final LocalizationEntry REQUIRED_ARGUMENT = new LocalizationEntry(
            "gui.sfm.client_action.palette.required_argument",
            "Separator inserted; provide the required argument"
    );

    private static final int MAX_SUGGESTIONS = 8;
    private static final int CONSOLE_HEIGHT = 72;
    private static final int EMPTY_CONSOLE_HEIGHT = 18;
    private static final int PANEL_MARGIN = 12;
    private static final int PANEL_BASE_HEIGHT = 92;
    private static final int SUGGESTION_ROW_HEIGHT = 18;
    private static final int SUGGESTION_ROW_CONTENT_HEIGHT = 16;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_GAP = 4;
    private static final int SCROLLBAR_MIN_THUMB_HEIGHT = 12;

    private static @Nullable SFMCommandPaletteScreen ACTIVE;

    private final SFMClientActionContext actionContext;
    private final boolean pushed;
    private final String initialQuery;
    private final @Nullable SFMChoiceSession choiceSession;
    @SuppressWarnings("NotNullFieldNotInitialized")
    private SFMClientActionCommandTree commandTree;

    @SuppressWarnings("NotNullFieldNotInitialized")
    private EditBox input;
    @SuppressWarnings("NotNullFieldNotInitialized")
    private Button executeButton;
    @SuppressWarnings("NotNullFieldNotInitialized")
    private SFMConsoleWidget consoleWidget;
    private List<Suggestion> suggestions = List.of();
    private final List<Component> feedback = new ArrayList<>();
    private final SFMVerticalListViewport suggestionViewport = new SFMVerticalListViewport();
    private String error = "";
    private long suggestionRevision;
    private long bindingCycleTicks;
    private boolean closing;
    private boolean insertedRequiredArgumentSeparator;

    private SFMCommandPaletteScreen(
            SFMClientActionContext actionContext,
            String initialQuery,
            boolean pushed
    ) {
        super(TITLE.getComponent());
        this.actionContext = actionContext;
        this.initialQuery = initialQuery.isBlank() ? DEFAULT_QUERY : initialQuery;
        this.pushed = pushed;
        this.choiceSession = null;
        this.commandTree = SFMClientActions.commandTree();
    }

    private SFMCommandPaletteScreen(
            @Nullable Screen origin,
            Component title,
            List<SFMActionChoice> choices,
            boolean pushed
    ) {
        super(title);
        this.pushed = pushed;
        this.actionContext = SFMClientActionContext.create(
                origin,
                () -> ACTIVE == this && Minecraft.getInstance().screen == this);
        this.choiceSession = SFMChoiceSessionService.create(choices, actionContext);
        this.initialQuery = choiceSession.prefix();
    }

    private SFMCommandPaletteScreen(
            SFMClientActionContext capturedContext,
            Component title,
            List<SFMActionChoice> choices,
            boolean pushed
    ) {
        super(title);
        this.pushed = pushed;
        this.actionContext = new SFMClientActionContext(
                capturedContext.originatingHost(),
                () -> ACTIVE == this && Minecraft.getInstance().screen == this,
                capturedContext.originatingPanelId());
        this.choiceSession = SFMChoiceSessionService.create(choices, actionContext);
        this.initialQuery = choiceSession.prefix();
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

    public static void openChoices(Component title, List<SFMActionChoice> choices) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen origin = minecraft.screen;
        boolean pushed = origin != null;
        SFMCommandPaletteScreen palette = new SFMCommandPaletteScreen(
                origin, title, choices, pushed);
        ACTIVE = palette;
        try {
            SFMScreenChangeHelpers.setOrPushScreen(palette);
        } catch (RuntimeException exception) {
            SFMChoiceSessionService.invalidate(palette.choiceSession);
            if (ACTIVE == palette) ACTIVE = null;
            throw exception;
        }
    }

    public static void openChoices(
            SFMClientActionContext capturedContext,
            Component title,
            List<SFMActionChoice> choices
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean pushed = minecraft.screen != null;
        SFMCommandPaletteScreen palette = new SFMCommandPaletteScreen(
                capturedContext, title, choices, pushed);
        ACTIVE = palette;
        try {
            SFMScreenChangeHelpers.setOrPushScreen(palette);
        } catch (RuntimeException exception) {
            SFMChoiceSessionService.invalidate(palette.choiceSession);
            if (ACTIVE == palette) ACTIVE = null;
            throw exception;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public Component getNarrationMessage() {
        int selectedSuggestion = suggestionViewport.selectedRow();
        if (selectedSuggestion < 0 || selectedSuggestion >= suggestions.size()) return title;
        Optional<ResourceLocation> actionId = suggestionActionId(suggestions.get(selectedSuggestion));
        if (actionId.isEmpty()) return title;
        var action = SFMClientActions.registry().get(actionId.get());
        if (action == null) return title;
        var narration = title.copy().append(". ").append(action.title()).append(". ")
                .append(action.description());
        action.itemIcon(actionContext).ifPresent(icon -> narration.append(". Icon: " + icon.accessibleLabel()));
        int bindingCount = SFMKeyBindingService.INSTANCE.bindingsForAction(actionId.get()).size();
        narration.append(". " + bindingCount + (bindingCount == 1 ? " binding" : " bindings")
                + ". Open details to inspect or configure them.");
        return narration;
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
        if (choiceSession != null) commandTree = choiceSession.activate();
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
        if (choiceSession != null) SFMChoiceSessionService.invalidate(choiceSession);
        if (ACTIVE == this) ACTIVE = null;
        if (this.pushed) {
            SFMScreenChangeHelpers.popScreen();
        } else {
            SFMScreenChangeHelpers.setScreen(null);
        }
    }

    @Override
    public void dismissActionSurface() {
        onClose();
    }

    @Override
    public void removed() {
        if (choiceSession != null) SFMChoiceSessionService.invalidate(choiceSession);
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
            if (!currentInputIsExecutable()
                    && suggestionViewport.selectedRow() != SFMVerticalListViewport.NO_SELECTION) {
                applySelectedSuggestion();
                if (!currentInputIsExecutable()) return true;
            }
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
                suggestionViewport.moveSelection(key == GLFW.GLFW_KEY_UP ? -1 : 1);
                return true;
            }
        }
        if (!suggestions.isEmpty() && key == GLFW.GLFW_KEY_PAGE_UP) {
            suggestionViewport.pageSelection(-1);
            return true;
        }
        if (!suggestions.isEmpty() && key == GLFW.GLFW_KEY_PAGE_DOWN) {
            suggestionViewport.pageSelection(1);
            return true;
        }
        if (!suggestions.isEmpty() && key == GLFW.GLFW_KEY_HOME) {
            suggestionViewport.selectFirst();
            return true;
        }
        if (!suggestions.isEmpty() && key == GLFW.GLFW_KEY_END) {
            suggestionViewport.selectLast();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.consoleWidget.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        SFMVerticalListViewport.ScrollbarGeometry scrollbar = suggestionScrollbarGeometry();
        if (suggestionViewport.mouseClickedScrollbar(mouseX, mouseY, button, scrollbar)) return true;
        OptionalInt row = suggestionViewport.rowAt(
                mouseX,
                mouseY,
                suggestionRowBounds(scrollbar.visible()),
                SUGGESTION_ROW_HEIGHT,
                SUGGESTION_ROW_CONTENT_HEIGHT);
        if (button == 0 && row.isPresent()) {
            int suggestionIndex = row.getAsInt();
            if (mouseX >= suggestionDetailsLeft(scrollbar.visible())) {
                Optional<ResourceLocation> actionId = suggestionActionId(suggestions.get(suggestionIndex));
                if (actionId.isPresent()) {
                    SFMScreenChangeHelpers.setOrPushScreen(new SFMKeyBindingDetailsScreen(this, actionId.get()));
                    return true;
                }
            }
            suggestionViewport.select(suggestionIndex);
            applySelectedSuggestion();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.consoleWidget.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return suggestionViewport.mouseReleasedScrollbar(button)
                || super.mouseReleased(mouseX, mouseY, button);
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
        return suggestionViewport.mouseDraggedScrollbar(mouseY, button, suggestionScrollbarGeometry())
                || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        ScrollRegion region = scrollRegionAt(
                mouseX, mouseY, suggestionListBounds(), consoleBounds());
        if (region == ScrollRegion.CONSOLE
                && this.consoleWidget.mouseScrolled(mouseX, mouseY, delta)) return true;
        if (region == ScrollRegion.SUGGESTIONS
                && suggestionViewport.scrollWheel(delta)) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        layoutWidgets();
        SFMClientTheme theme = SFMClientThemeService.active();
        int panel = theme.colour(SFMColourRole.PANEL_BACKGROUND);
        int border = theme.colour(SFMColourRole.PANEL_BORDER);
        int text = theme.colour(SFMColourRole.TEXT_PRIMARY);
        int muted = theme.colour(SFMColourRole.TEXT_MUTED);
        int errorColour = theme.colour(SFMColourRole.TEXT_ERROR);
        fill(poseStack, 0, 0, this.width, this.height, theme.colour(SFMColourRole.SCREEN_OVERLAY));
        int left = panelLeft();
        int top = panelTop();
        int right = left + panelWidth();
        int bottom = top + panelHeight();
        fill(poseStack, left, top, right, bottom, panel);
        fill(poseStack, left, top, right, top + 1, border);
        fill(poseStack, left, bottom - 1, right, bottom, border);
        fill(poseStack, left, top, left + 1, bottom, border);
        fill(poseStack, right - 1, top, right, bottom, border);

        SFMFontUtils.draw(poseStack, this.font, title.copy().withStyle(ChatFormatting.BOLD), left + 10, top + 12, text, false);
        Component guidance = insertedRequiredArgumentSeparator
                ? REQUIRED_ARGUMENT.getComponent().withStyle(ChatFormatting.GOLD)
                : ACCEPT_SUGGESTION.getComponent(Component.literal("Tab").withStyle(ChatFormatting.AQUA));
        SFMFontUtils.draw(
                poseStack,
                this.font,
                guidance,
                left + 10,
                top + 54,
                muted,
                false
        );
        if (suggestions.isEmpty()) {
            SFMFontUtils.draw(poseStack, this.font, EMPTY_RESULTS.getComponent(), left + 10, top + 72, muted, false);
        } else {
            SFMVerticalListViewport.ScrollbarGeometry scrollbar = suggestionScrollbarGeometry();
            SFMVerticalListViewport.Bounds rows = suggestionRowBounds(scrollbar.visible());
            for (int suggestionIndex = suggestionViewport.firstVisibleRow();
                 suggestionIndex < suggestionViewport.lastVisibleRowExclusive();
                 suggestionIndex++) {
                int visibleIndex = suggestionIndex - suggestionViewport.firstVisibleRow();
                int rowTop = rows.y() + visibleIndex * SUGGESTION_ROW_HEIGHT;
                int textY = rowTop + 2;
                if (suggestionIndex == suggestionViewport.selectedRow()) {
                    fill(poseStack, rows.x(), rowTop, rows.x() + rows.width(),
                            rowTop + SUGGESTION_ROW_CONTENT_HEIGHT,
                            theme.colour(SFMColourRole.PANEL_SELECTION));
                }
                Suggestion suggestion = suggestions.get(suggestionIndex);
                int textX = actionIcon(suggestion).isPresent()
                        ? left + 10 + SFMItemIconRenderer.SIZE + 4
                        : left + 12;
                SFMFontUtils.draw(poseStack, this.font, truncateSuggestion(suggestion, textX - left),
                        textX, textY, text, false);
                renderBindingSummary(poseStack, suggestion, right, textY, scrollbar.visible());
            }
            renderSuggestionScrollbar(poseStack, mouseX, mouseY, scrollbar);
        }
        if (!this.error.isEmpty()) {
            SFMFontUtils.draw(
                    poseStack,
                    this.font,
                    truncateToPanel(this.error),
                    left + 10,
                    consoleTop(top) - 12,
                    errorColour,
                    false
            );
        }
        this.consoleWidget.replaceLines(this.feedback);
        this.consoleWidget.render(poseStack, mouseX, mouseY, partialTick);
        super.render(poseStack, mouseX, mouseY, partialTick);
        renderActionIconsOnTop(poseStack);
        renderActionIconTooltip(poseStack, mouseX, mouseY);
        renderActionDetailsTooltip(poseStack, mouseX, mouseY);
    }

    private Optional<ca.teamdman.sfm.client.presentation.SFMItemIcon> actionIcon(Suggestion suggestion) {
        Optional<ResourceLocation> actionId = suggestionActionId(suggestion);
        if (actionId.isEmpty()) return Optional.empty();
        var action = SFMClientActions.registry().get(actionId.get());
        if (action == null) return Optional.empty();
        var themed = SFMClientThemeService.active().actionIcons().get(actionId.get());
        return themed == null ? action.itemIcon(actionContext) : Optional.of(themed);
    }

    private void renderActionIconsOnTop(PoseStack poseStack) {
        // The palette is translucent and intentionally preserves the title/world colour buffer.
        // Clear only stale scene depth before GUI item models so they cannot be hidden by the origin screen.
        RenderSystem.clear(0x00000100, Minecraft.ON_OSX);
        for (int suggestionIndex = suggestionViewport.firstVisibleRow();
             suggestionIndex < suggestionViewport.lastVisibleRowExclusive();
             suggestionIndex++) {
            int index = suggestionIndex - suggestionViewport.firstVisibleRow();
            int y = suggestionRowBounds(suggestionViewport.canScroll()).y()
                    + index * SUGGESTION_ROW_HEIGHT;
            actionIcon(suggestions.get(suggestionIndex)).ifPresent(icon ->
                    SFMItemIconRenderer.render(minecraft, icon, panelLeft() + 10, y)
            );
        }
    }

    private void renderActionIconTooltip(PoseStack poseStack, int mouseX, int mouseY) {
        SFMVerticalListViewport.ScrollbarGeometry scrollbar = suggestionScrollbarGeometry();
        OptionalInt row = suggestionViewport.rowAt(
                mouseX,
                mouseY,
                suggestionRowBounds(scrollbar.visible()),
                SUGGESTION_ROW_HEIGHT,
                SUGGESTION_ROW_CONTENT_HEIGHT);
        if (row.isEmpty()) return;
        int suggestionIndex = row.getAsInt();
        int visibleIndex = suggestionIndex - suggestionViewport.firstVisibleRow();
        int iconX = panelLeft() + 10;
        int iconY = suggestionRowBounds(scrollbar.visible()).y() + visibleIndex * SUGGESTION_ROW_HEIGHT;
        if (mouseX < iconX || mouseX >= iconX + SFMItemIconRenderer.SIZE
                || mouseY < iconY || mouseY >= iconY + SFMItemIconRenderer.SIZE) return;
        Optional<ResourceLocation> actionId = suggestionActionId(suggestions.get(suggestionIndex));
        if (actionId.isEmpty()) return;
        var action = SFMClientActions.registry().get(actionId.get());
        if (action == null) return;
        action.itemIcon(actionContext).ifPresent(icon -> {
            var resolved = SFMItemIconResolver.resolve(icon);
            String fallback = resolved.usedFallback() ? " (using fallback item)" : "";
            renderTooltip(poseStack, Component.literal(resolved.accessibleLabel() + fallback), mouseX, mouseY);
        });
    }

    private void renderBindingSummary(
            PoseStack poseStack,
            Suggestion suggestion,
            int right,
            int y,
            boolean scrollbarVisible
    ) {
        Optional<ResourceLocation> actionId = suggestionActionId(suggestion);
        if (actionId.isEmpty()) return;
        List<SFMKeyBinding> bindings = SFMKeyBindingService.INSTANCE.bindingsForAction(actionId.get());
        String bindingText = SFMKeyBindingCycle.displayedSequence(bindings, bindingCycleTicks);
        int scrollbarSpace = scrollbarVisible ? SCROLLBAR_WIDTH + SCROLLBAR_GAP : 0;
        int bindingAreaLeft = right - 146 - scrollbarSpace;
        int bindingAreaWidth = 108;
        String shown = font.plainSubstrByWidth(bindingText, bindingAreaWidth);
        SFMFontUtils.draw(poseStack, font, shown, bindingAreaLeft, y,
                SFMClientThemeService.active().colour(SFMColourRole.TEXT_ACCENT), false);
        SFMFontUtils.draw(poseStack, font, "[?]", right - 28 - scrollbarSpace, y,
                SFMClientThemeService.active().colour(SFMColourRole.TEXT_ACCENT), false);
    }

    private void renderActionDetailsTooltip(PoseStack poseStack, int mouseX, int mouseY) {
        SFMVerticalListViewport.ScrollbarGeometry scrollbar = suggestionScrollbarGeometry();
        OptionalInt row = suggestionViewport.rowAt(
                mouseX,
                mouseY,
                suggestionRowBounds(scrollbar.visible()),
                SUGGESTION_ROW_HEIGHT,
                SUGGESTION_ROW_CONTENT_HEIGHT);
        if (row.isEmpty() || mouseX < suggestionDetailsLeft(scrollbar.visible())) return;
        int suggestionIndex = row.getAsInt();
        Optional<ResourceLocation> actionId = suggestionActionId(suggestions.get(suggestionIndex));
        if (actionId.isEmpty()) return;
        var action = SFMClientActions.registry().get(actionId.get());
        if (action == null) return;
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(action.title().copy().withStyle(ChatFormatting.AQUA));
        tooltip.add(action.description());
        action.itemIcon(actionContext).ifPresent(icon -> tooltip.add(
                Component.literal("Icon: " + icon.accessibleLabel()).withStyle(ChatFormatting.GRAY)
        ));
        List<SFMKeyBinding> bindings = SFMKeyBindingService.INSTANCE.bindingsForAction(actionId.get());
        if (bindings.isEmpty()) tooltip.add(Component.literal("No key bindings").withStyle(ChatFormatting.GRAY));
        else bindings.forEach(binding -> tooltip.add(Component.literal(
                SFMKeyBindingDisplay.format(binding.sequence()) + (binding.enabled() ? "" : " (disabled)")
        )));
        renderComponentTooltip(poseStack, tooltip, mouseX, mouseY);
    }

    private String truncateSuggestion(Suggestion suggestion, int leftInset) {
        return font.plainSubstrByWidth(suggestion.getText(), Math.max(20, panelWidth() - 150 - leftInset));
    }

    private Optional<ResourceLocation> suggestionActionId(Suggestion suggestion) {
        if (choiceSession != null) {
            Optional<ResourceLocation> actionId = choiceSession.actionIdForSuggestion(suggestion.getText());
            if (actionId.isPresent()) return actionId;
        }
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
        suggestionViewport.configure(suggestions.size(), visibleSuggestionCount());
    }

    private SFMVerticalListViewport.Bounds suggestionListBounds() {
        return new SFMVerticalListViewport.Bounds(
                panelLeft() + 6,
                panelTop() + 70,
                Math.max(0, panelWidth() - 12),
                visibleSuggestionCount() * SUGGESTION_ROW_HEIGHT);
    }

    private SFMVerticalListViewport.Bounds consoleBounds() {
        return new SFMVerticalListViewport.Bounds(
                panelLeft() + 10,
                consoleTop(panelTop()),
                Math.max(0, panelWidth() - 20),
                consoleHeight());
    }

    static ScrollRegion scrollRegionAt(
            double mouseX,
            double mouseY,
            SFMVerticalListViewport.Bounds suggestionBounds,
            SFMVerticalListViewport.Bounds consoleBounds
    ) {
        if (consoleBounds.contains(mouseX, mouseY)) return ScrollRegion.CONSOLE;
        if (suggestionBounds.contains(mouseX, mouseY)) return ScrollRegion.SUGGESTIONS;
        return ScrollRegion.NONE;
    }

    enum ScrollRegion {
        CONSOLE,
        SUGGESTIONS,
        NONE
    }

    private SFMVerticalListViewport.Bounds suggestionRowBounds(boolean scrollbarVisible) {
        SFMVerticalListViewport.Bounds list = suggestionListBounds();
        int scrollbarSpace = scrollbarVisible ? SCROLLBAR_WIDTH + SCROLLBAR_GAP : 0;
        return new SFMVerticalListViewport.Bounds(
                list.x(), list.y(), Math.max(0, list.width() - scrollbarSpace), list.height());
    }

    private SFMVerticalListViewport.ScrollbarGeometry suggestionScrollbarGeometry() {
        SFMVerticalListViewport.Bounds list = suggestionListBounds();
        return suggestionViewport.scrollbarGeometry(new SFMVerticalListViewport.Bounds(
                list.x() + list.width() - SCROLLBAR_WIDTH,
                list.y(),
                SCROLLBAR_WIDTH,
                list.height()), SCROLLBAR_MIN_THUMB_HEIGHT);
    }

    private int suggestionDetailsLeft(boolean scrollbarVisible) {
        return panelLeft() + panelWidth() - 28
                - (scrollbarVisible ? SCROLLBAR_WIDTH + SCROLLBAR_GAP : 0);
    }

    private void renderSuggestionScrollbar(
            PoseStack poseStack,
            int mouseX,
            int mouseY,
            SFMVerticalListViewport.ScrollbarGeometry geometry
    ) {
        if (!geometry.visible()) return;
        var track = geometry.track();
        var thumb = geometry.thumb();
        fill(poseStack, track.x(), track.y(), track.x() + track.width(), track.y() + track.height(),
                0x55303030);
        boolean hovered = thumb.contains(mouseX, mouseY);
        fill(poseStack, thumb.x(), thumb.y(), thumb.x() + thumb.width(), thumb.y() + thumb.height(),
                hovered || suggestionViewport.isScrollbarDragActive() ? 0xFFAAAAAA : 0xFF707070);
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
        this.insertedRequiredArgumentSeparator = false;
        String command = commandInput();
        this.error = "";
        this.input.setSuggestion(command.isEmpty() ? INPUT_PLACEHOLDER.getString() : "");
        long revision = ++this.suggestionRevision;
        var tree = commandTree;
        ParseResults<SFMClientActionSource> parsed = tree.parse(
                command,
                new SFMClientActionSource(this.actionContext)
        );
        this.executeButton.active = isExecutable(parsed);
        tree.getPaletteSuggestions(command, parsed).thenAccept(result -> Minecraft.getInstance().execute(() -> {
            if (ACTIVE != this || revision != this.suggestionRevision) return;
            this.suggestions = result.getList();
            suggestionViewport.configure(this.suggestions.size(), visibleSuggestionCount());
            suggestionViewport.select(this.suggestions.isEmpty()
                    ? SFMVerticalListViewport.NO_SELECTION
                    : 0);
            layoutWidgets();
        }));
    }

    private boolean isExecutable(ParseResults<SFMClientActionSource> parsed) {
        return SFMClientActionExecutor.isExecutable(parsed);
    }

    private boolean currentInputIsExecutable() {
        return isExecutable(commandTree.parse(
                commandInput().stripLeading(),
                new SFMClientActionSource(actionContext)));
    }

    private void applySelectedSuggestion() {
        int selectedSuggestion = suggestionViewport.selectedRow();
        if (selectedSuggestion < 0 || selectedSuggestion >= this.suggestions.size()) return;
        String current = commandInput();
        String suggestedValue = this.suggestions.get(selectedSuggestion).apply(current);
        String value = SFMClientCommandInsertion.prepare(
                suggestedValue,
                commandTree,
                new SFMClientActionSource(actionContext)
        );
        this.input.setValue(value);
        this.insertedRequiredArgumentSeparator = !value.equals(suggestedValue);
        this.input.moveCursorToEnd();
    }

    private void executeInput() {
        String rawCommand = commandInput().stripLeading();
        var source = new SFMClientActionSource(actionContext);
        var parsed = commandTree.parse(rawCommand, source);
        String prepared = SFMClientCommandInsertion.prepare(
                rawCommand,
                commandTree,
                source
        );
        if (!prepared.equals(rawCommand)) {
            if (SFMClientCommandInsertion.hasAvailableLiteralChildren(parsed)) {
                this.error = "Choose an available suggestion before providing the next argument";
                return;
            }
            this.input.setValue(prepared);
            this.insertedRequiredArgumentSeparator = true;
            this.input.moveCursorToEnd();
            return;
        }
        if (SFMClientCommandInsertion.isAwaitingRequiredArgument(
                rawCommand,
                commandTree,
                new SFMClientActionSource(actionContext)
        )) {
            this.insertedRequiredArgumentSeparator = true;
            return;
        }
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
        int result = commandTree.execute(
                command,
                new SFMClientActionSource(this.actionContext, this.feedback::add));
        this.error = "";
        if (choiceSession != null && result > 0) {
            if (Minecraft.getInstance().screen == this) onClose();
            return;
        }
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

    /** Exact canonical actions exposed by an active constrained palette. */
    public List<String> choiceCommandsForAutomation() {
        if (choiceSession == null) {
            throw new IllegalStateException("Command palette is not constrained by a choice session");
        }
        return choiceSession.canonicalCommands();
    }

    /** Executes a canonical choice through its real session-scoped Brigadier path. */
    public void executeChoiceForAutomation(String canonicalCommand) {
        if (choiceSession == null) {
            throw new IllegalStateException("Command palette is not constrained by a choice session");
        }
        executeCommandForAutomation(choiceSession.surfaceCommand(canonicalCommand));
    }

    /** Selects a constrained choice by pointer and confirms it through the real Enter path. */
    public void clickChoiceForAutomation(String canonicalCommand) {
        if (choiceSession == null) {
            throw new IllegalStateException("Command palette is not constrained by a choice session");
        }
        String surfaceCommand = choiceSession.surfaceCommand(canonicalCommand);
        String suggestionText = surfaceCommand.substring(choiceSession.prefix().length());
        int index = -1;
        for (int candidate = 0; candidate < suggestions.size(); candidate++) {
            if (suggestions.get(candidate).getText().equals(suggestionText)) {
                index = candidate;
                break;
            }
        }
        if (index < 0) throw new IllegalStateException("Choice is not currently suggested: " + canonicalCommand);
        suggestionViewport.select(index);
        SFMVerticalListViewport.ScrollbarGeometry scrollbar = suggestionScrollbarGeometry();
        SFMVerticalListViewport.Bounds rows = suggestionRowBounds(scrollbar.visible());
        int visibleIndex = index - suggestionViewport.firstVisibleRow();
        double mouseX = rows.x() + 2;
        double mouseY = rows.y() + visibleIndex * SUGGESTION_ROW_HEIGHT
                + SUGGESTION_ROW_CONTENT_HEIGHT / 2.0d;
        if (!mouseClicked(mouseX, mouseY, 0)) {
            throw new IllegalStateException("Choice pointer event was not consumed: " + canonicalCommand);
        }
        if (!commandInput().equals(surfaceCommand)) {
            throw new IllegalStateException("Choice pointer selected '" + commandInput()
                    + "' instead of '" + surfaceCommand + "'");
        }
        keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
    }

    /** Exercises the real suggestion wheel, keyboard, track, and thumb event paths. */
    public void exerciseSuggestionViewportForAutomation() {
        layoutWidgets();
        if (!suggestionViewport.canScroll()) {
            throw new IllegalStateException("Palette needs more suggestions than visible rows for scrolling proof");
        }
        SFMVerticalListViewport.Bounds list = suggestionListBounds();
        double listX = list.x() + 2;
        double listY = list.y() + 2;
        int initialFirst = suggestionViewport.firstVisibleRow();
        if (!mouseScrolled(listX, listY, -1.0d)
                || suggestionViewport.firstVisibleRow() <= initialFirst) {
            throw new IllegalStateException("Suggestion wheel did not advance the viewport");
        }
        keyPressed(GLFW.GLFW_KEY_PAGE_DOWN, 0, 0);
        keyPressed(GLFW.GLFW_KEY_END, 0, 0);
        if (suggestionViewport.selectedRow() != suggestions.size() - 1) {
            throw new IllegalStateException("End did not select the final suggestion");
        }
        keyPressed(GLFW.GLFW_KEY_HOME, 0, 0);
        if (suggestionViewport.firstVisibleRow() != 0 || suggestionViewport.selectedRow() != 0) {
            throw new IllegalStateException("Home did not restore the first suggestion");
        }

        SFMVerticalListViewport.ScrollbarGeometry geometry = suggestionScrollbarGeometry();
        double trackX = geometry.track().x() + geometry.track().width() / 2.0d;
        double trackBottom = geometry.track().y() + geometry.track().height() - 1.0d;
        if (!mouseClicked(trackX, trackBottom, 0)
                || suggestionViewport.firstVisibleRow() == 0) {
            throw new IllegalStateException("Suggestion scrollbar track click did not advance the viewport");
        }
        keyPressed(GLFW.GLFW_KEY_HOME, 0, 0);
        geometry = suggestionScrollbarGeometry();
        double thumbY = geometry.thumb().y() + geometry.thumb().height() / 2.0d;
        if (!mouseClicked(trackX, thumbY, 0)
                || !mouseDragged(trackX, trackBottom, 0, 0, trackBottom - thumbY)
                || !mouseReleased(trackX, trackBottom, 0)
                || suggestionViewport.firstVisibleRow() != suggestionViewport.maxFirstVisibleRow()) {
            throw new IllegalStateException("Suggestion scrollbar thumb did not reach the final viewport");
        }
    }

    /** Sets the real palette input for a visual puppet without bypassing its responder. */
    public void setInputForAutomation(String command) {
        this.input.setValue(command);
        this.input.moveCursorToEnd();
    }

    /** Displays deterministic theme reload feedback for the visual puppet. */
    public void showThemeFeedbackForAutomation(List<String> messages) {
        this.feedback.clear();
        messages.forEach(message -> this.feedback.add(Component.literal(message)));
    }

    /** Displays live SFML syntax components using the current runtime theme for visual proof. */
    public void showThemeSyntaxForAutomation(String program) {
        this.feedback.clear();
        this.feedback.add(Component.literal("Live SFML syntax - gold keywords, pink italic strings")
                .withStyle(style -> style.withColor(SFMClientThemeService.active().colour(SFMColourRole.TEXT_ACCENT))));
        this.feedback.addAll(ProgramSyntaxHighlightingHelper.withSyntaxHighlighting(program, false));
    }

    /** Exercises the same Enter path as a user and verifies its resulting draft. */
    public void prepareIncompleteInputForAutomation(String command, String expected) {
        setInputForAutomation(command);
        executeInput();
        if (!this.input.getValue().equals(expected)) {
            throw new IllegalStateException("Expected palette input '" + expected + "' but found '"
                    + this.input.getValue() + "'");
        }
        if (this.executeButton.active) {
            throw new IllegalStateException("Incomplete palette input unexpectedly enabled Execute");
        }
    }

    private void resetToDefaultQuery() {
        this.input.setValue(DEFAULT_QUERY);
        this.input.moveCursorToEnd();
        this.setFocused(this.input);
        this.input.setFocused(true);
    }

}
