package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.action.SFMClientActionCommandTree;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionExecutor;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.action.SFMClientCommandInsertion;
import ca.teamdman.sfm.client.action.SFMCompletionApplication;
import ca.teamdman.sfm.client.action.SFMPaletteCandidate;
import ca.teamdman.sfm.client.command.SFMCommandHistoryService;
import ca.teamdman.sfm.client.history.SFMDocumentHistoryHost;
import ca.teamdman.sfm.client.history.SFMDocumentHistoryHostController;
import ca.teamdman.sfm.client.history.SFMDocumentHistoryInputTarget;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistorySession;
import ca.teamdman.sfm.client.history.document.runtime.SFMDocumentHistoryRuntime;
import ca.teamdman.sfm.client.presentation.SFMItemIconRenderer;
import ca.teamdman.sfm.client.presentation.SFMItemIconResolver;
import ca.teamdman.sfm.client.keybinding.SFMKeyBinding;
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingCycle;
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingDisplay;
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingService;
import ca.teamdman.sfm.client.keybinding.SFMKeyboardUsageContextProvider;
import ca.teamdman.sfm.client.keybinding.SFMKeyboardUsageContextSnapshot;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituations;
import ca.teamdman.sfm.client.screen.widget.SFMButtonBuilder;
import ca.teamdman.sfm.client.screen.widget.SFMConsoleWidget;
import ca.teamdman.sfm.client.screen.widget.SFMKeycapRenderer;
import ca.teamdman.sfm.client.screen.widget.SFMVerticalListViewport;
import ca.teamdman.sfm.client.theme.SFMClientTheme;
import ca.teamdman.sfm.client.theme.SFMClientThemeService;
import ca.teamdman.sfm.client.theme.SFMColourRole;
import ca.teamdman.sfm.client.text_styling.ProgramSyntaxHighlightingHelper;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.mixins.EditBoxAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The first, deliberately small, presentation of SFM's contextual action
 * registry.  The screen is pushed over an existing screen when possible and
 * otherwise replaces the in-world view, so closing it always restores the
 * view from which the palette was opened.
 */
public final class SFMCommandPaletteScreen extends Screen implements SFMTransientActionScreen,
        SFMDocumentHistoryHost, SFMDocumentHistoryInputTarget, SFMKeyboardUsageContextProvider {
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
    private static final int PANEL_BASE_HEIGHT = 116;
    private static final int HORIZONTAL_PADDING = 10;
    private static final int INPUT_TOP_OFFSET = 30;
    private static final int BUTTON_ROW_TOP_OFFSET = 54;
    private static final int GUIDANCE_TOP_OFFSET = 78;
    private static final int SUGGESTION_TOP_OFFSET = 94;
    private static final int CONSOLE_TOP_OFFSET = 106;
    private static final int BUTTON_GAP = 4;
    private static final int SUGGESTION_ROW_HEIGHT = 18;
    private static final int SUGGESTION_ROW_CONTENT_HEIGHT = 16;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_GAP = 4;
    private static final int SCROLLBAR_MIN_THUMB_HEIGHT = 12;
    static final String CLOSE_ACTION_COMMAND = "sfm action invoke sfm:palette/close";

    private static @Nullable SFMCommandPaletteScreen ACTIVE;
    private static final AtomicLong NEXT_HISTORY_SESSION = new AtomicLong();

    private final SFMClientActionContext actionContext;
    private final boolean pushed;
    private final String initialQuery;
    private final @Nullable SFMChoiceSession choiceSession;
    private final Runnable closeListener;
    @SuppressWarnings("NotNullFieldNotInitialized")
    private SFMClientActionCommandTree commandTree;

    @SuppressWarnings("NotNullFieldNotInitialized")
    private EditBox input;
    @SuppressWarnings("NotNullFieldNotInitialized")
    private Button executeButton;
    @SuppressWarnings("NotNullFieldNotInitialized")
    private Button cancelButton;
    @SuppressWarnings("NotNullFieldNotInitialized")
    private SFMConsoleWidget consoleWidget;
    private List<SFMPaletteCandidate> suggestions = List.of();
    private final List<Component> feedback = new ArrayList<>();
    private final SFMVerticalListViewport suggestionViewport = new SFMVerticalListViewport();
    private String error = "";
    private long suggestionRevision;
    private long appliedSuggestionRevision = -1L;
    private String appliedSuggestionCommand = "";
    private long bindingCycleTicks;
    private final CloseLifecycle closeLifecycle = new CloseLifecycle();
    private boolean insertedRequiredArgumentSeparator;
    private @Nullable SFMCompletionApplication lastCompletionApplication;
    private final String historySessionId =
            "sfm:document/command-palette/session-" + NEXT_HISTORY_SESSION.incrementAndGet();
    private @Nullable SFMDocumentHistoryHostController historyController;
    private @Nullable SFMDocumentHistoryRuntime.Registration historyRegistration;
    private boolean historyInputFocused;

    @FunctionalInterface
    interface PaletteActionExecutor {
        int execute(
                String command,
                SFMClientActionContext context,
                Consumer<Component> feedback
        ) throws CommandSyntaxException;
    }

    record ControlBounds(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }
    }

    record ControlsLayout(
            ControlBounds input,
            ControlBounds execute,
            ControlBounds cancel
    ) {
    }

    /** Read-only witness for the real shared Cancel widget used by live puppets. */
    public record CancelControlAutomationSnapshot(
            String label,
            int x,
            int y,
            int width,
            int height,
            boolean visible,
            boolean active,
            boolean focused,
            String narrationPriority
    ) {
    }

    /** Structured candidate evidence used by puppets and later streaming tests. */
    public record PaletteCandidateAutomationSnapshot(
            int order,
            String displayText,
            boolean activatable,
            String kind,
            String origin,
            int replacementStart,
            int replacementEnd,
            String replacementText,
            String completionFrontier,
            int historyRecency,
            @Nullable String historyFamily,
            String insertionIntent
    ) {
    }

    static final class CloseLifecycle {
        private boolean closing;
        private boolean cleaned;

        boolean beginClose() {
            if (closing) return false;
            closing = true;
            return true;
        }

        boolean runCleanupOnce(Runnable cleanup) {
            if (cleaned) return false;
            cleaned = true;
            cleanup.run();
            return true;
        }
    }

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
        this.closeListener = () -> { };
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
        this.closeListener = () -> { };
    }

    private SFMCommandPaletteScreen(
            SFMClientActionContext capturedContext,
            Component title,
            List<SFMActionChoice> choices,
            boolean pushed
    ) {
        this(capturedContext, title, choices, pushed, () -> { });
    }

    private SFMCommandPaletteScreen(
            SFMClientActionContext capturedContext,
            Component title,
            List<SFMActionChoice> choices,
            boolean pushed,
            Runnable closeListener
    ) {
        super(title);
        this.pushed = pushed;
        this.actionContext = new SFMClientActionContext(
                capturedContext.originatingHost(),
                () -> ACTIVE == this && Minecraft.getInstance().screen == this,
                capturedContext.originatingPanelId());
        this.choiceSession = SFMChoiceSessionService.create(choices, actionContext);
        this.initialQuery = choiceSession.prefix();
        this.closeListener = Objects.requireNonNull(closeListener, "closeListener");
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
            palette.setInputValueWithHistory(
                    initialQuery,
                    SFMDocumentHistoryContract.MutationKind.ACTION,
                    Optional.of(initialQuery),
                    "palette-open-replace-query"
            );
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
            palette.closeLifecycle.beginClose();
            palette.cleanupActionSurface();
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
            palette.closeLifecycle.beginClose();
            palette.cleanupActionSurface();
            throw exception;
        }
    }

    /**
     * Opens a constrained surface whose lifecycle is leased by its origin.
     * The listener runs exactly once for command completion, Escape, external
     * dismissal, failed opening, or screen removal.
     */
    public static SFMCommandPaletteScreen openChoices(
            SFMClientActionContext capturedContext,
            Component title,
            List<SFMActionChoice> choices,
            Runnable closeListener
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean pushed = minecraft.screen != null;
        SFMCommandPaletteScreen palette = new SFMCommandPaletteScreen(
                capturedContext, title, choices, pushed, closeListener);
        ACTIVE = palette;
        try {
            SFMScreenChangeHelpers.setOrPushScreen(palette);
            return palette;
        } catch (RuntimeException exception) {
            palette.closeLifecycle.beginClose();
            palette.cleanupActionSurface();
            throw exception;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public String documentHistorySessionId() {
        return historySessionId;
    }

    @Override
    public SFMDocumentHistorySession documentHistorySession() {
        if (historyController == null) throw new IllegalStateException("Palette history is not initialized");
        return historyController.session();
    }

    @Override
    public SFMDocumentHistoryHostController documentHistoryController() {
        if (historyController == null) throw new IllegalStateException("Palette history is not initialized");
        return historyController;
    }

    @Override
    public void recordDocumentRawInput(
            long clientTick,
            SFMDocumentHistoryContract.RawEventKind kind,
            String source,
            String code,
            Optional<String> text,
            int modifiers,
            boolean consumed,
            boolean delivered
    ) {
        if (historyController != null) {
            historyController.recordRawInput(
                    clientTick, kind, source, code, text, modifiers, consumed, delivered);
        }
    }

    @Override
    public SFMKeyboardUsageContextSnapshot keyboardUsageContextSnapshot() {
        var ancestry = SFMKeyboardUsageSituations.catalog().resolve(
                SFMKeyboardUsageSituations.TEMPORAL_DOCUMENT);
        return new SFMKeyboardUsageContextSnapshot(
                this,
                () -> ACTIVE == this && Minecraft.getInstance().screen == this,
                null,
                null,
                0,
                0,
                ancestry.situations(),
                ancestry.depths()
        );
    }

    @Override
    public Component getNarrationMessage() {
        int selectedSuggestion = suggestionViewport.selectedRow();
        if (selectedSuggestion < 0 || selectedSuggestion >= suggestions.size()) return title;
        SFMPaletteCandidate candidate = suggestions.get(selectedSuggestion);
        if (!candidate.activatable()) {
            return title.copy().append(". ").append(candidate.displayText());
        }
        Optional<ResourceLocation> actionId = suggestionActionId(candidate);
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
        SFMDocumentHistoryContract.DocumentState restoredHistoryState = historyController == null
                ? null
                : historyController.session().currentState();
        int width = panelWidth();
        int left = panelLeft();
        int top = panelTop();
        ControlsLayout controls = controlsLayout(this.width, top);
        this.input = this.addRenderableWidget(new EditBox(
                this.font,
                controls.input().x(),
                controls.input().y(),
                controls.input().width(),
                controls.input().height(),
                INPUT_PLACEHOLDER.getComponent()
        ));
        this.input.setMaxLength(2048);
        this.input.setValue(restoredHistoryState == null
                ? this.initialQuery
                : restoredHistoryState.text());
        this.input.setSuggestion("");
        this.input.setResponder(this::refreshSuggestions);
        this.executeButton = this.addRenderableWidget(new SFMButtonBuilder()
                .setPosition(controls.execute().x(), controls.execute().y())
                .setSize(controls.execute().width(), controls.execute().height())
                .setText(EXECUTE)
                .setOnPress(button -> executeInput())
                .build());
        this.executeButton.active = false;
        this.cancelButton = this.addRenderableWidget(createCancelButton(
                controls.cancel(),
                this::cancelThroughAction
        ));
        this.consoleWidget = new SFMConsoleWidget(
                this.font,
                left + HORIZONTAL_PADDING,
                consoleTop(top),
                Math.max(1, width - HORIZONTAL_PADDING * 2),
                consoleHeight()
        );
        layoutWidgets();
        this.setInitialFocus(this.input);
        this.setFocused(this.input);
        this.input.setFocused(true);
        if (historyController == null) {
            SFMDocumentHistorySession session = SFMDocumentHistorySession.create(
                    new SFMDocumentHistoryContract.SessionIdentity(
                            historySessionId,
                            historySessionId + "/command",
                            Optional.empty()
                    ),
                    captureInputHistoryState()
            );
            historyController = new SFMDocumentHistoryHostController(
                    session,
                    "sfm:command_palette",
                    historySessionId + "/input",
                    this::checkoutInputHistoryState
            );
            // A transient palette is independently addressable, but it does
            // not steal the workspace's last focused-document selector.
            historyRegistration = SFMDocumentHistoryRuntime.get().register(historyController);
        } else {
            checkoutInputHistoryState(Objects.requireNonNull(restoredHistoryState));
        }
        updateDocumentHistoryFocus(true);
        refreshSuggestions(this.input.getValue());
    }

    @Override
    public void onClose() {
        if (!closeLifecycle.beginClose()) return;
        cleanupActionSurface();
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
        closeLifecycle.beginClose();
        cleanupActionSurface();
        super.removed();
    }

    private void cleanupActionSurface() {
        closeLifecycle.runCleanupOnce(() -> {
            try {
                if (historyRegistration != null) {
                    historyRegistration.close();
                    historyRegistration = null;
                }
                if (historyController != null) {
                    updateDocumentHistoryFocus(false);
                    historyController.close();
                    historyController = null;
                }
                if (choiceSession != null && !choiceSession.invalidated()) {
                    SFMChoiceSessionService.invalidate(choiceSession);
                }
            } finally {
                try {
                    closeListener.run();
                } finally {
                    if (ACTIVE == this) ACTIVE = null;
                }
            }
        });
    }

    private void updateDocumentHistoryFocus(boolean focused) {
        if (historyInputFocused == focused) return;
        if (historyController != null) {
            historyController.recordRawInput(
                    SFMKeyBindingService.INSTANCE.currentTick(),
                    SFMDocumentHistoryContract.RawEventKind.FOCUS,
                    "command-palette-focus",
                    focused ? "gain" : "loss",
                    Optional.empty(),
                    0,
                    false,
                    true
            );
        }
        historyInputFocused = focused;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        SFMDocumentHistoryContract.DocumentState before = historyController == null
                ? null
                : captureInputHistoryState();
        boolean handled = keyPressedWithoutHistory(key, scanCode, modifiers);
        if (before != null) {
            observeInputHistoryMutation(
                    before,
                    mutationKindForKey(key, modifiers),
                    editDirectionForKey(key),
                    Optional.empty(),
                    "palette-key-" + key
            );
        }
        return handled;
    }

    private boolean keyPressedWithoutHistory(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            cancelThroughAction();
            return true;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE)
                && this.getFocused() instanceof Button) {
            return super.keyPressed(key, scanCode, modifiers);
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            int selected = suggestionViewport.selectedRow();
            if (selected >= 0 && selected < suggestions.size()
                    && !suggestions.get(selected).activatable()) {
                // Usage/diagnostic rows explain the current frontier. Enter
                // must never execute through an explanatory selection.
                return true;
            }
            if (!currentInputIsExecutable()
                    && suggestionViewport.selectedRow() != SFMVerticalListViewport.NO_SELECTION) {
                applySelectedSuggestion();
                if (!currentInputIsExecutable()) return true;
            }
            executeInput();
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            boolean forward = (modifiers & GLFW.GLFW_MOD_SHIFT) == 0 && !Screen.hasShiftDown();
            if (forward && this.input.isFocused()) {
                if (applySelectedSuggestion()) return true;
                // A selected exact/no-op or explanatory row must not leak Tab
                // into the widget focus chain. Shift+Tab remains the explicit
                // way to leave the input in the reverse direction.
                if (!this.suggestions.isEmpty()) return true;
            }
            if (!this.changeFocus(forward)) this.changeFocus(forward);
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
    public boolean charTyped(char character, int modifiers) {
        SFMDocumentHistoryContract.DocumentState before = historyController == null
                ? null
                : captureInputHistoryState();
        boolean handled = super.charTyped(character, modifiers);
        if (before != null) {
            observeInputHistoryMutation(
                    before,
                    SFMDocumentHistoryContract.MutationKind.TYPE,
                    SFMDocumentHistoryContract.EditDirection.FORWARD,
                    Optional.of(Character.toString(character)),
                    "palette-character"
            );
        }
        return handled;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return observePointerMutation(
                "pointer-press-" + button,
                () -> mouseClickedWithoutHistory(mouseX, mouseY, button));
    }

    private boolean mouseClickedWithoutHistory(double mouseX, double mouseY, int button) {
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
        return observePointerMutation(
                "pointer-release-" + button,
                () -> mouseReleasedWithoutHistory(mouseX, mouseY, button));
    }

    private boolean mouseReleasedWithoutHistory(double mouseX, double mouseY, int button) {
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
        return observePointerMutation(
                "pointer-drag-" + button,
                () -> mouseDraggedWithoutHistory(mouseX, mouseY, button, dragX, dragY));
    }

    private boolean mouseDraggedWithoutHistory(
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
                top + GUIDANCE_TOP_OFFSET,
                muted,
                false
        );
        if (suggestions.isEmpty()) {
            SFMFontUtils.draw(
                    poseStack,
                    this.font,
                    EMPTY_RESULTS.getComponent(),
                    left + 10,
                    top + SUGGESTION_TOP_OFFSET + 2,
                    muted,
                    false
            );
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
                SFMPaletteCandidate suggestion = suggestions.get(suggestionIndex);
                int textX = actionIcon(suggestion).isPresent()
                        ? left + 10 + SFMItemIconRenderer.SIZE + 4
                        : left + 12;
                int rowText = suggestion.activatable() ? text : muted;
                SFMFontUtils.draw(poseStack, this.font, truncateSuggestion(suggestion, textX - left),
                        textX, textY, rowText, false);
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

    private Optional<ca.teamdman.sfm.client.presentation.SFMItemIcon> actionIcon(SFMPaletteCandidate suggestion) {
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
            SFMPaletteCandidate suggestion,
            int right,
            int y,
            boolean scrollbarVisible
    ) {
        Optional<ResourceLocation> actionId = suggestionActionId(suggestion);
        if (actionId.isEmpty()) return;
        List<SFMKeyBinding> bindings = SFMKeyBindingService.INSTANCE.bindingsForAction(actionId.get());
        SFMKeyBinding binding = SFMKeyBindingCycle.displayedBinding(bindings, bindingCycleTicks);
        if (binding == null) return;
        int scrollbarSpace = scrollbarVisible ? SCROLLBAR_WIDTH + SCROLLBAR_GAP : 0;
        int bindingAreaLeft = right - 146 - scrollbarSpace;
        int bindingAreaWidth = 108;
        SFMKeycapRenderer.draw(
                poseStack,
                font,
                binding.sequence(),
                bindingAreaLeft,
                y,
                bindingAreaWidth,
                binding.enabled());
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

    private String truncateSuggestion(SFMPaletteCandidate suggestion, int leftInset) {
        return font.plainSubstrByWidth(suggestion.displayText(), Math.max(20, panelWidth() - 150 - leftInset));
    }

    private Optional<ResourceLocation> suggestionActionId(SFMPaletteCandidate suggestion) {
        if (suggestion.actionId() != null) return Optional.of(suggestion.actionId());
        if (choiceSession != null) {
            Optional<ResourceLocation> actionId = choiceSession.actionIdForSuggestion(suggestion.replacementText());
            if (actionId.isPresent()) return actionId;
        }
        try {
            String suggestionText = suggestion.replacementText().stripLeading();
            int separator = 0;
            while (separator < suggestionText.length()
                    && !Character.isWhitespace(suggestionText.charAt(separator))) separator++;
            ResourceLocation id = new ResourceLocation(suggestionText.substring(0, separator));
            return SFMClientActions.registry().get(id) == null ? Optional.empty() : Optional.of(id);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private int panelWidth() {
        return panelWidth(this.width);
    }

    static int panelWidth(int viewportWidth) {
        return Math.max(1, Math.min(460, viewportWidth - PANEL_MARGIN * 2));
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
        return panelTop + CONSOLE_TOP_OFFSET + visibleSuggestionCount() * SUGGESTION_ROW_HEIGHT;
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
        if (this.input == null || this.executeButton == null || this.cancelButton == null
                || this.consoleWidget == null) {
            return;
        }
        int top = panelTop();
        ControlsLayout controls = controlsLayout(this.width, top);
        this.input.setX(controls.input().x());
        this.input.y = controls.input().y();
        this.executeButton.x = controls.execute().x();
        this.executeButton.y = controls.execute().y();
        this.cancelButton.x = controls.cancel().x();
        this.cancelButton.y = controls.cancel().y();
        this.consoleWidget.setBounds(
                panelLeft() + HORIZONTAL_PADDING,
                consoleTop(top),
                Math.max(1, panelWidth() - HORIZONTAL_PADDING * 2),
                consoleHeight()
        );
        suggestionViewport.configure(suggestions.size(), visibleSuggestionCount());
    }

    static ControlsLayout controlsLayout(int viewportWidth, int panelTop) {
        int panelWidth = panelWidth(viewportWidth);
        int panelLeft = (viewportWidth - panelWidth) / 2;
        int contentLeft = panelLeft + HORIZONTAL_PADDING;
        int contentWidth = Math.max(1, panelWidth - HORIZONTAL_PADDING * 2);
        int executeWidth = Math.max(1, (contentWidth - BUTTON_GAP) / 2);
        int cancelWidth = Math.max(1, contentWidth - BUTTON_GAP - executeWidth);
        return new ControlsLayout(
                new ControlBounds(contentLeft, panelTop + INPUT_TOP_OFFSET, contentWidth, 20),
                new ControlBounds(contentLeft, panelTop + BUTTON_ROW_TOP_OFFSET, executeWidth, 20),
                new ControlBounds(
                        contentLeft + executeWidth + BUTTON_GAP,
                        panelTop + BUTTON_ROW_TOP_OFFSET,
                        cancelWidth,
                        20
                )
        );
    }

    static Button createCancelButton(ControlBounds bounds, Runnable onPress) {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(onPress, "onPress");
        return new SFMButtonBuilder()
                .setPosition(bounds.x(), bounds.y())
                .setSize(bounds.width(), bounds.height())
                .setText(Component.literal("Cancel"))
                .setOnPress(button -> onPress.run())
                .build();
    }

    private SFMVerticalListViewport.Bounds suggestionListBounds() {
        return new SFMVerticalListViewport.Bounds(
                panelLeft() + 6,
                panelTop() + SUGGESTION_TOP_OFFSET,
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

    private SFMDocumentHistoryContract.DocumentState captureInputHistoryState() {
        String value = this.input.getValue();
        int activeUtf16 = Math.max(0, Math.min(value.length(), this.input.getCursorPosition()));
        int anchorUtf16 = Math.max(0, Math.min(
                value.length(),
                ((EditBoxAccessor) (Object) this.input).sfm$getHighlightPos()
        ));
        int activeCodePoint = value.codePointCount(0, activeUtf16);
        int anchorCodePoint = value.codePointCount(0, anchorUtf16);
        SFMDocumentHistoryContract.LogicalPoint anchor =
                SFMDocumentHistoryContract.LogicalPoint.at(value, anchorCodePoint);
        SFMDocumentHistoryContract.LogicalPoint active =
                SFMDocumentHistoryContract.LogicalPoint.at(value, activeCodePoint);
        return new SFMDocumentHistoryContract.DocumentState(
                value,
                List.of(new SFMDocumentHistoryContract.LogicalSelection("primary", anchor, active)),
                Optional.of("primary")
        );
    }

    private void checkoutInputHistoryState(SFMDocumentHistoryContract.DocumentState state) {
        this.input.setValue(state.text());
        SFMDocumentHistoryContract.LogicalSelection selection = state.primarySelectionId()
                .flatMap(primary -> state.selections().stream()
                        .filter(candidate -> candidate.id().equals(primary))
                        .findFirst())
                .orElseGet(() -> state.selections().stream().findFirst().orElse(null));
        if (selection == null) {
            this.input.moveCursorToEnd();
            return;
        }
        int activeUtf16 = state.text().offsetByCodePoints(0, selection.active().codePointOffset());
        int anchorUtf16 = state.text().offsetByCodePoints(0, selection.anchor().codePointOffset());
        this.input.setCursorPosition(activeUtf16);
        this.input.setHighlightPos(anchorUtf16);
    }

    private void observeInputHistoryMutation(
            SFMDocumentHistoryContract.DocumentState before,
            SFMDocumentHistoryContract.MutationKind kind,
            SFMDocumentHistoryContract.EditDirection direction,
            Optional<String> changedText,
            String cause
    ) {
        if (historyController == null || historyController.checkoutInProgress()) return;
        // A nested explicit programmatic cause (completion/reset/action) may
        // already have advanced the immutable head while this outer input
        // callback was on the stack.
        if (!historyController.session().currentState().equals(before)) return;
        SFMDocumentHistoryContract.DocumentState after = captureInputHistoryState();
        if (after.equals(before)) return;
        historyController.observeMutation(
                kind,
                direction,
                before,
                after,
                changedText,
                SFMKeyBindingService.INSTANCE.currentTick(),
                cause
        );
    }

    private void setInputValueWithHistory(
            String value,
            SFMDocumentHistoryContract.MutationKind kind,
            Optional<String> changedText,
            String cause
    ) {
        SFMDocumentHistoryContract.DocumentState before = historyController == null
                ? null
                : captureInputHistoryState();
        this.input.setValue(value);
        if (before != null) {
            observeInputHistoryMutation(
                    before,
                    kind,
                    SFMDocumentHistoryContract.EditDirection.NONE,
                    changedText,
                    cause
            );
        }
    }

    private boolean observePointerMutation(String code, BooleanSupplier dispatch) {
        SFMDocumentHistoryContract.DocumentState before = historyController == null
                ? null
                : captureInputHistoryState();
        if (historyController != null) {
            historyController.recordRawInput(
                    SFMKeyBindingService.INSTANCE.currentTick(),
                    SFMDocumentHistoryContract.RawEventKind.POINTER,
                    "palette-pointer",
                    code,
                    Optional.empty(),
                    0,
                    false,
                    true
            );
        }
        boolean handled = dispatch.getAsBoolean();
        if (before != null) {
            observeInputHistoryMutation(
                    before,
                    SFMDocumentHistoryContract.MutationKind.SELECTION_CHANGE,
                    SFMDocumentHistoryContract.EditDirection.NONE,
                    Optional.empty(),
                    code
            );
        }
        return handled;
    }

    private static SFMDocumentHistoryContract.MutationKind mutationKindForKey(
            int key,
            int modifiers
    ) {
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            return SFMDocumentHistoryContract.MutationKind.DELETE_BACKWARD;
        }
        if (key == GLFW.GLFW_KEY_DELETE) {
            return SFMDocumentHistoryContract.MutationKind.DELETE_FORWARD;
        }
        if (Screen.isPaste(key)) return SFMDocumentHistoryContract.MutationKind.PASTE;
        if (key == GLFW.GLFW_KEY_A && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            return SFMDocumentHistoryContract.MutationKind.SELECTION_CHANGE;
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT
                || key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END) {
            return (modifiers & GLFW.GLFW_MOD_SHIFT) != 0
                    ? SFMDocumentHistoryContract.MutationKind.SELECTION_CHANGE
                    : SFMDocumentHistoryContract.MutationKind.CARET_CHANGE;
        }
        return SFMDocumentHistoryContract.MutationKind.OTHER;
    }

    private static SFMDocumentHistoryContract.EditDirection editDirectionForKey(int key) {
        if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_LEFT) {
            return SFMDocumentHistoryContract.EditDirection.BACKWARD;
        }
        if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_RIGHT) {
            return SFMDocumentHistoryContract.EditDirection.FORWARD;
        }
        return SFMDocumentHistoryContract.EditDirection.NONE;
    }

    private void refreshSuggestions(String ignored) {
        this.insertedRequiredArgumentSeparator = false;
        String command = commandInput();
        this.error = "";
        this.input.setSuggestion(command.isEmpty() ? INPUT_PLACEHOLDER.getString() : "");
        long revision = ++this.suggestionRevision;
        // A previous query's candidates must never remain interactive while a
        // new asynchronous Brigadier/fuzzy result is in flight.
        this.suggestions = List.of();
        suggestionViewport.configure(0, visibleSuggestionCount());
        suggestionViewport.select(SFMVerticalListViewport.NO_SELECTION);
        var tree = commandTree;
        ParseResults<SFMClientActionSource> parsed = tree.parse(
                command,
                new SFMClientActionSource(this.actionContext)
        );
        this.executeButton.active = isExecutable(parsed);
        tree.getPaletteCandidates(command, parsed).thenAccept(result -> Minecraft.getInstance().execute(() -> {
            if (ACTIVE != this || revision != this.suggestionRevision) return;
            this.suggestions = result;
            this.appliedSuggestionRevision = revision;
            this.appliedSuggestionCommand = command;
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

    private boolean applySelectedSuggestion() {
        int selectedSuggestion = suggestionViewport.selectedRow();
        if (selectedSuggestion < 0 || selectedSuggestion >= this.suggestions.size()) return false;
        String rawCurrent = this.input.getValue();
        String current = commandInput();
        SFMPaletteCandidate candidate = this.suggestions.get(selectedSuggestion);
        if (!candidate.activatable()) return false;
        SFMCompletionApplication application = candidate.apply(current);
        if (application.afterValue().equals(current)) {
            OptionalInt progressing = SFMPaletteCandidate.progressingIndex(
                    this.suggestions,
                    selectedSuggestion,
                    current
            );
            if (progressing.isEmpty()) return true;
            selectedSuggestion = progressing.getAsInt();
            suggestionViewport.select(selectedSuggestion);
            candidate = this.suggestions.get(selectedSuggestion);
            application = candidate.apply(current);
        }
        application = paletteCoordinateApplication(rawCurrent, application);
        this.lastCompletionApplication = application;
        setInputValueWithHistory(
                application.afterValue(),
                SFMDocumentHistoryContract.MutationKind.COMPLETION,
                Optional.of(application.replacementText()),
                "palette-completion-" + application.candidateKind().name().toLowerCase(java.util.Locale.ROOT)
        );
        this.insertedRequiredArgumentSeparator = !application.deliberateSeparator().isEmpty();
        this.input.moveCursorToEnd();
        return true;
    }

    private static SFMCompletionApplication paletteCoordinateApplication(
            String rawCurrent,
            SFMCompletionApplication normalized
    ) {
        if (!rawCurrent.startsWith("/")) return normalized;
        return new SFMCompletionApplication(
                rawCurrent,
                com.mojang.brigadier.context.StringRange.between(
                        normalized.replacementRange().getStart() + 1,
                        normalized.replacementRange().getEnd() + 1
                ),
                normalized.replacementText(),
                normalized.deliberateSeparator(),
                normalized.candidateKind(),
                normalized.candidateOrigin(),
                "/" + normalized.afterValue()
        );
    }

    private void cancelThroughAction() {
        try {
            invokeCancelAction(SFMClientActionExecutor::execute, actionContext, feedback::add);
        } catch (CommandSyntaxException exception) {
            SFM.LOGGER.warn("Command palette could not invoke its canonical close action", exception);
            this.error = EXECUTION_FAILED.getComponent(exception.getMessage()).getString();
        } catch (RuntimeException exception) {
            SFM.LOGGER.error("Command palette close action failed", exception);
            this.error = EXECUTION_FAILED.getComponent(exception.getMessage()).getString();
        }
    }

    static int invokeCancelAction(
            PaletteActionExecutor executor,
            SFMClientActionContext context,
            Consumer<Component> feedback
    ) throws CommandSyntaxException {
        return executor.execute(CLOSE_ACTION_COMMAND, context, feedback);
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
            setInputValueWithHistory(
                    prepared,
                    SFMDocumentHistoryContract.MutationKind.COMPLETION,
                    Optional.of(prepared.substring(Math.min(prepared.length(), rawCommand.length()))),
                    "palette-required-argument-separator"
            );
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

    private int executeCommand(String command) throws CommandSyntaxException {
        this.feedback.clear();
        int result = choiceSession == null
                ? SFMClientActionExecutor.execute(
                        commandTree,
                        command,
                        this.actionContext,
                        this.feedback::add
                )
                // The choice surface delegates to its canonical action through
                // SFMClientActionExecutor, so it owns the useful provenance
                // rather than recording the ephemeral `sfm choose` wrapper.
                : commandTree.execute(
                        command,
                        new SFMClientActionSource(this.actionContext, this.feedback::add));
        this.error = "";
        if (result > 0 && SFMCommandHistoryService.isRecordable(command)) {
            SFMCommandHistoryService.recordSuccessful(command);
        }
        if (choiceSession != null && result > 0) {
            if (Minecraft.getInstance().screen == this) onClose();
            return result;
        }
        resetToDefaultQuery();
        return result;
    }

    /**
     * Supplies a command and executes it for the client puppet harness.
     *
     * <p>The harness uses the same Brigadier path as a user typing into the
     * palette; it does not invoke an action implementation directly.</p>
     */
    public void executeCommandForAutomation(String command) {
        setInputValueWithHistory(
                command,
                SFMDocumentHistoryContract.MutationKind.ACTION,
                Optional.of(command),
                "automation-execute-command"
        );
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

    /** Immutable originating context retained while automation drives a pushed palette. */
    public SFMClientActionContext actionContextForAutomation() {
        return actionContext;
    }

    /**
     * Focuses and describes the real Cancel widget after applying the current
     * responsive layout. This is deliberately an observation hook: activation
     * still travels through the widget's ordinary mouse/key path.
     */
    public CancelControlAutomationSnapshot focusCancelForAutomation() {
        layoutWidgets();
        this.setFocused(cancelButton);
        cancelButton.setFocused(true);
        CancelControlAutomationSnapshot snapshot = cancelControlSnapshotForAutomation();
        if (!snapshot.visible() || !snapshot.active() || !snapshot.focused()) {
            throw new IllegalStateException("Palette Cancel control is not visible, active, and focused: " + snapshot);
        }
        if (snapshot.x() < 0 || snapshot.y() < 0
                || snapshot.x() + snapshot.width() > this.width
                || snapshot.y() + snapshot.height() > this.height) {
            throw new IllegalStateException("Palette Cancel control is outside the logical viewport: " + snapshot
                    + " viewport=" + this.width + "x" + this.height);
        }
        return snapshot;
    }

    /** Clicks the center of the visible Cancel widget through normal screen mouse dispatch. */
    public void clickCancelForAutomation() {
        CancelControlAutomationSnapshot snapshot = focusCancelForAutomation();
        double mouseX = snapshot.x() + snapshot.width() / 2.0D;
        double mouseY = snapshot.y() + snapshot.height() / 2.0D;
        if (!mouseClicked(mouseX, mouseY, GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            throw new IllegalStateException("Palette Cancel pointer event was not consumed at "
                    + mouseX + "," + mouseY);
        }
        mouseReleased(mouseX, mouseY, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    /** Whether a constrained choice has reached the pointer-selectable suggestion viewport. */
    public boolean choiceReadyForPointerAutomation(String canonicalCommand) {
        if (choiceSession == null) {
            throw new IllegalStateException("Command palette is not constrained by a choice session");
        }
        String surfaceCommand = choiceSession.surfaceCommand(canonicalCommand);
        String suggestionText = surfaceCommand.substring(choiceSession.prefix().length());
        return suggestions.stream().anyMatch(suggestion ->
                suggestion.activatable() && suggestion.replacementText().equals(suggestionText));
    }

    private CancelControlAutomationSnapshot cancelControlSnapshotForAutomation() {
        ControlBounds bounds = controlsLayout(this.width, panelTop()).cancel();
        return new CancelControlAutomationSnapshot(
                cancelButton.getMessage().getString(),
                bounds.x(),
                bounds.y(),
                bounds.width(),
                bounds.height(),
                cancelButton.visible,
                cancelButton.active,
                cancelButton.isFocused(),
                cancelButton.narrationPriority().name()
        );
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
            if (suggestions.get(candidate).activatable()
                    && suggestions.get(candidate).replacementText().equals(suggestionText)) {
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
        setInputValueWithHistory(
                command,
                SFMDocumentHistoryContract.MutationKind.PASTE,
                Optional.of(command),
                "automation-set-input"
        );
        this.input.moveCursorToEnd();
    }

    /** Exact current input used to make visual-puppet captures self-verifying. */
    public String inputForAutomation() {
        return this.input.getValue();
    }

    /** Current Brigadier/fuzzy completion texts used by visual-puppet assertions. */
    public List<String> suggestionTextsForAutomation() {
        return this.suggestions.stream()
                .filter(SFMPaletteCandidate::activatable)
                .map(SFMPaletteCandidate::replacementText)
                .toList();
    }

    /** Full ordered candidate metadata; screenshots are not the only proof. */
    public List<PaletteCandidateAutomationSnapshot> candidateSnapshotsForAutomation() {
        ArrayList<PaletteCandidateAutomationSnapshot> result = new ArrayList<>();
        for (int index = 0; index < suggestions.size(); index++) {
            SFMPaletteCandidate candidate = suggestions.get(index);
            result.add(new PaletteCandidateAutomationSnapshot(
                    index,
                    candidate.displayText(),
                    candidate.activatable(),
                    candidate.kind().name(),
                    candidate.origin().name(),
                    candidate.replacementRange().getStart(),
                    candidate.replacementRange().getEnd(),
                    candidate.replacementText(),
                    candidate.completionFrontier(),
                    candidate.historyRecency(),
                    candidate.historyFamily(),
                    candidate.insertionIntent().name()
            ));
        }
        return List.copyOf(result);
    }

    /** True only after the latest input's asynchronous candidate set was applied. */
    public boolean suggestionsMatchCurrentInputForAutomation() {
        return appliedSuggestionRevision == suggestionRevision
               && appliedSuggestionCommand.equals(commandInput());
    }

    public String appliedSuggestionCommandForAutomation() {
        return appliedSuggestionCommand;
    }

    public long appliedSuggestionRevisionForAutomation() {
        return appliedSuggestionRevision;
    }

    /** Exact metadata for the latest Tab/mouse completion application. */
    public Optional<SFMCompletionApplication> lastCompletionApplication() {
        return Optional.ofNullable(lastCompletionApplication);
    }

    /** Submits the current automation input through the same path as Enter. */
    public void submitInputForAutomation() {
        executeInput();
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
        setInputValueWithHistory(
                DEFAULT_QUERY,
                SFMDocumentHistoryContract.MutationKind.ACTION,
                Optional.of(DEFAULT_QUERY),
                "palette-reset-after-command"
        );
        this.input.moveCursorToEnd();
        this.setFocused(this.input);
        this.input.setFocused(true);
    }

}
