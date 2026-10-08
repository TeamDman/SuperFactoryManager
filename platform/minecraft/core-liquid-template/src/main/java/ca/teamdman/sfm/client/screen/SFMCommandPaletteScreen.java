package ca.teamdman.sfm.client.screen;

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
{% when "1.21", "1.21.1", "26.1.2" %}
import ca.teamdman.sfm.common.util.SFMResourceLocation;
{% endcase %}
import ca.teamdman.sfm.SFM;
{% if features.typed_command_palette %}
import ca.teamdman.sfm.client.action.SFMClientActionCommandTree;
{% endif %}
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionExecutor;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.action.SFMClientCommandInsertion;
{% if features.typed_command_palette %}
import ca.teamdman.sfm.client.action.SFMCompletionApplication;
import ca.teamdman.sfm.client.action.SFMFocusTargetHost;
import ca.teamdman.sfm.client.action.SFMPaletteCandidate;
import ca.teamdman.sfm.client.action.SFMPaletteCandidateCopyAction;
import ca.teamdman.sfm.client.action.SFMPaletteCandidateSetCopyAction;
import ca.teamdman.sfm.client.action.SFMPaletteCandidateInspection;
{% if features.command_history %}
import ca.teamdman.sfm.client.command.SFMCommandHistoryService;
{% endif %}
{% if features.document_history %}
import ca.teamdman.sfm.client.history.SFMDocumentHistoryHost;
import ca.teamdman.sfm.client.history.SFMDocumentHistoryHostController;
import ca.teamdman.sfm.client.history.SFMDocumentHistoryInputTarget;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistorySession;
import ca.teamdman.sfm.client.history.document.runtime.SFMDocumentHistoryRuntime;
{% endif %}
{% endif %}
import ca.teamdman.sfm.client.presentation.SFMItemIconRenderer;
import ca.teamdman.sfm.client.presentation.SFMItemIconResolver;
{% if features.typed_command_palette %}
import ca.teamdman.sfm.client.presentation.SFMTextSummary;
{% endif %}
import ca.teamdman.sfm.client.keybinding.SFMKeyBinding;
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingCycle;
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingDisplay;
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingService;
{% if features.typed_command_palette %}
import ca.teamdman.sfm.client.keybinding.SFMKeyboardUsageContextProvider;
import ca.teamdman.sfm.client.keybinding.SFMKeyboardUsageContextSnapshot;
{% endif %}
import ca.teamdman.sfm.client.registry.SFMClientActions;
{% if features.typed_command_palette %}
import ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituations;
{% endif %}
import ca.teamdman.sfm.client.screen.widget.SFMButtonBuilder;
import ca.teamdman.sfm.client.screen.widget.SFMConsoleWidget;
{% if features.typed_command_palette %}
import ca.teamdman.sfm.client.screen.widget.SFMKeycapRenderer;
import ca.teamdman.sfm.client.screen.widget.SFMVerticalListViewport;
{% else %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% endif %}
import ca.teamdman.sfm.client.theme.SFMClientTheme;
import ca.teamdman.sfm.client.theme.SFMClientThemeService;
import ca.teamdman.sfm.client.theme.SFMColourRole;
import ca.teamdman.sfm.client.text_styling.ProgramSyntaxHighlightingHelper;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
{% if features.typed_command_palette %}
{% case minecraft_version %}
{% when "1.19.2" %}
{% if features.document_history %}
import ca.teamdman.sfm.mixins.EditBoxAccessor;
{% endif %}
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
{% when "1.19.4" %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% if features.document_history %}
import ca.teamdman.sfm.mixins.EditBoxAccessor;
{% endif %}
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
{% endcase %}
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import com.mojang.blaze3d.systems.RenderSystem;
{% when "26.1.2" %}
{% endcase %}
{% endif %}
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
{% if features.typed_command_palette %}
{% else %}
import com.mojang.brigadier.suggestion.Suggestion;
{% endif %}
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
{% if features.typed_command_palette %}
{% case minecraft_version %}
{% when "1.19.2" %}
{% when "1.19.4" %}
import net.minecraft.client.gui.ComponentPath;
{% endcase %}
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import com.mojang.blaze3d.vertex.PoseStack;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.client.gui.GuiGraphics;
{% when "26.1.2" %}
import net.minecraft.client.gui.GuiGraphicsExtractor;
{% endcase %}
{% endif %}
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
{% if features.typed_command_palette %}
{% case minecraft_version %}
{% when "1.19.2" %}
{% when "1.19.4" %}
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
{% endcase %}
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% when "26.1.2" %}
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
{% endcase %}
{% endif %}
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.resources.ResourceLocation;
{% when "26.1.2" %}
import net.minecraft.resources.Identifier;
{% endcase %}
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
{% if features.typed_command_palette %}
import java.util.Objects;
{% endif %}
import java.util.Optional;
{% if features.typed_command_palette %}
import java.util.OptionalInt;
{% if features.document_history %}
import java.util.concurrent.atomic.AtomicLong;
{% endif %}
{% if features.document_history %}
import java.util.function.BooleanSupplier;
{% endif %}
import java.util.function.Consumer;
{% endif %}

/**
 * The first, deliberately small, presentation of SFM's contextual action
 * registry.  The screen is pushed over an existing screen when possible and
 * otherwise replaces the in-world view, so closing it always restores the
 * view from which the palette was opened.
 */
{% if features.typed_command_palette %}
public final class SFMCommandPaletteScreen extends Screen implements SFMTransientActionScreen,
{% if features.document_history %}
        SFMDocumentHistoryHost, SFMDocumentHistoryInputTarget, SFMKeyboardUsageContextProvider,
{% else %}
        SFMKeyboardUsageContextProvider,
{% endif %}
{% if features.screen_diagnostics %}
        SFMFocusTargetHost, SFMScreenDiagnosticsContributor, SFMPaletteCandidateCopyAction.Host,
{% else %}
        SFMFocusTargetHost, SFMPaletteCandidateCopyAction.Host,
{% endif %}
        SFMPaletteCandidateSetCopyAction.Host {
{% else %}
public final class SFMCommandPaletteScreen extends Screen {
{% endif %}
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

{% if features.typed_command_palette %}
    @SFMLocalizationDatagen
    public static final LocalizationEntry CANCEL = new LocalizationEntry(
            "gui.sfm.client_action.palette.cancel",
            "Cancel"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry FOCUS_CONTROL_TOOLTIP = new LocalizationEntry(
            "gui.sfm.client_action.palette.focus_control_tooltip",
            "Focus with %s. Right-click for actions."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry FOCUS_CONTROL_TOOLTIP_UNBOUND = new LocalizationEntry(
            "gui.sfm.client_action.palette.focus_control_tooltip_unbound",
            "No focus shortcut is assigned. Right-click for actions."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry CONTROL_ACTIONS = new LocalizationEntry(
            "gui.sfm.client_action.palette.control_actions",
            "%s actions"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry FOCUS_CONTROL = new LocalizationEntry(
            "gui.sfm.client_action.palette.focus_control",
            "Focus %s"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry COPY_FOCUS_ACTION = new LocalizationEntry(
            "gui.sfm.client_action.palette.copy_focus_action",
            "Copy focus action for %s"
    );

{% endif %}
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

{% if features.typed_command_palette %}
    @SFMLocalizationDatagen
    public static final LocalizationEntry MORE_DETAILS = new LocalizationEntry(
            "gui.sfm.client_action.palette.more_details",
            "Right-click this row for full details or to copy its complete value."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry CANONICAL_COMMAND = new LocalizationEntry(
            "gui.sfm.client_action.palette.canonical_command",
            "Canonical: %s"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry CANONICAL_COMMAND_PENDING = new LocalizationEntry(
            "gui.sfm.client_action.palette.canonical_command_pending",
            "Canonical: select a complete action"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry CANONICAL_COMMAND_COPIED = new LocalizationEntry(
            "gui.sfm.client_action.palette.canonical.copied",
            "Copied canonical command"
    );

{% endif %}
    private static final int MAX_SUGGESTIONS = 8;
    private static final int CONSOLE_HEIGHT = 72;
    private static final int EMPTY_CONSOLE_HEIGHT = 18;
    private static final int PANEL_MARGIN = 12;
{% if features.typed_command_palette %}
    private static final int PANEL_BASE_HEIGHT = 116;
    private static final int HORIZONTAL_PADDING = 10;
    private static final int INPUT_TOP_OFFSET = 30;
    private static final int BUTTON_ROW_TOP_OFFSET = 54;
    private static final int GUIDANCE_TOP_OFFSET = 78;
    private static final int SUGGESTION_TOP_OFFSET = 94;
    private static final int CONSOLE_TOP_OFFSET = 106;
    private static final int BUTTON_GAP = 4;
{% else %}
    private static final int PANEL_BASE_HEIGHT = 92;
{% endif %}
    private static final int SUGGESTION_ROW_HEIGHT = 18;
{% if features.typed_command_palette %}
    private static final int SUGGESTION_ROW_CONTENT_HEIGHT = 16;
    private static final int CANONICAL_PREVIEW_HEIGHT = 20;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_GAP = 4;
    private static final int SCROLLBAR_MIN_THUMB_HEIGHT = 12;
    static final String CLOSE_ACTION_COMMAND = "sfm action invoke sfm:palette/close";
    static final String COMMAND_INPUT_FOCUS_TARGET = "command_input";
    static final String EXECUTE_FOCUS_TARGET = "execute_button";
    static final String CANCEL_FOCUS_TARGET = "cancel_button";
    private static final ResourceLocation FOCUS_ACTION_ID = new ResourceLocation(SFM.MOD_ID, "focus");
    private static final ResourceLocation COPY_ACTION_ID = new ResourceLocation(SFM.MOD_ID, "clipboard/copy/action");
{% endif %}
    private static @Nullable SFMCommandPaletteScreen ACTIVE;
{% if features.typed_command_palette %}
{% if features.document_history %}
    private static final AtomicLong NEXT_HISTORY_SESSION = new AtomicLong();
{% endif %}
{% endif %}
    private final SFMClientActionContext actionContext;
    private final boolean pushed;
    private final String initialQuery;
{% if features.typed_command_palette %}
    private final @Nullable SFMChoiceSession choiceSession;
    private final Runnable closeListener;
    private final @Nullable SFMCommandPaletteScreen previousActive;
    @SuppressWarnings("NotNullFieldNotInitialized")
    private SFMClientActionCommandTree commandTree;
{% endif %}

    @SuppressWarnings("NotNullFieldNotInitialized")
    private EditBox input;
    @SuppressWarnings("NotNullFieldNotInitialized")
    private Button executeButton;
    @SuppressWarnings("NotNullFieldNotInitialized")
{% if features.typed_command_palette %}
    private Button cancelButton;
    @SuppressWarnings("NotNullFieldNotInitialized")
{% endif %}
    private SFMConsoleWidget consoleWidget;
{% if features.typed_command_palette %}
    private List<SFMPaletteCandidate> suggestions = List.of();
{% else %}
    private List<Suggestion> suggestions = List.of();
{% endif %}
    private final List<Component> feedback = new ArrayList<>();
{% if features.typed_command_palette %}
    private final SFMVerticalListViewport suggestionViewport = new SFMVerticalListViewport();
{% else %}
    private int selectedSuggestion = -1;
    private int firstVisibleSuggestion;
{% endif %}
    private String error = "";
    private long suggestionRevision;
{% if features.typed_command_palette %}
    private long appliedSuggestionRevision = -1L;
    private String appliedSuggestionCommand = "";
{% endif %}
    private long bindingCycleTicks;
{% if features.typed_command_palette %}
    private final CloseLifecycle closeLifecycle = new CloseLifecycle();
{% else %}
    private boolean closing;
{% endif %}
    private boolean insertedRequiredArgumentSeparator;
{% if features.typed_command_palette %}
    private @Nullable SFMCompletionApplication lastCompletionApplication;
{% if features.document_history %}
    private final String historySessionId =
            "sfm:document/command-palette/session-" + NEXT_HISTORY_SESSION.incrementAndGet();
    private @Nullable SFMDocumentHistoryHostController historyController;
    private @Nullable SFMDocumentHistoryRuntime.Registration historyRegistration;
    private boolean historyInputFocused;
{% endif %}
    private long nextCandidateInspectionId;
    private @Nullable CapturedCandidateInspection capturedCandidateInspection;
    private @Nullable CapturedCandidateSetInspection capturedCandidateSetInspection;

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

        boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < right() && pointY >= y && pointY < y + height;
        }
    }

    record ControlsLayout(
            ControlBounds input,
            ControlBounds execute,
            ControlBounds cancel
    ) {
    }

    enum EnterKeyAction {
        EXECUTE,
        APPLY_SELECTED_SUGGESTION,
        CONSUME
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

    record CapturedCandidateInspection(long id, SFMPaletteCandidateInspection inspection) {
        CapturedCandidateInspection {
            if (id <= 0) throw new IllegalArgumentException("Candidate inspection id must be positive");
            Objects.requireNonNull(inspection, "inspection");
        }
    }

    record CapturedCandidateSetInspection(long id, List<SFMPaletteCandidateInspection> inspections) {
        CapturedCandidateSetInspection {
            if (id <= 0) throw new IllegalArgumentException("Candidate-set inspection id must be positive");
            inspections = List.copyOf(inspections);
            if (inspections.isEmpty()) throw new IllegalArgumentException("Candidate-set inspection must not be empty");
        }
    }

    record SuggestionTextPresentation(String renderedText, int availableWidth, boolean truncated) {
        SuggestionTextPresentation {
            Objects.requireNonNull(renderedText, "renderedText");
            if (availableWidth < 0) throw new IllegalArgumentException("availableWidth must be non-negative");
        }
    }

    enum SuggestionTooltipKind {
        COMPLETE_CANDIDATE,
        ICON,
        ACTION_DETAILS,
        NONE
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
{% endif %}

    private SFMCommandPaletteScreen(
            SFMClientActionContext actionContext,
            String initialQuery,
            boolean pushed
    ) {
        super(TITLE.getComponent());
        this.actionContext = actionContext;
        this.initialQuery = initialQuery.isBlank() ? DEFAULT_QUERY : initialQuery;
        this.pushed = pushed;
{% if features.typed_command_palette %}
        this.choiceSession = null;
        this.closeListener = () -> { };
        this.previousActive = ACTIVE;
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
        this.previousActive = ACTIVE;
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
{% if features.workspace_panels %}
                () -> ACTIVE == this && Minecraft.getInstance().screen == this,
                capturedContext.originatingPanelId());
{% else %}
                () -> ACTIVE == this && Minecraft.getInstance().screen == this);
{% endif %}
{% if features.theme_preview_rules %}
        ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewCaptures.inherit(capturedContext,this.actionContext);
{% endif %}
        this.choiceSession = SFMChoiceSessionService.create(choices, actionContext);
        this.initialQuery = choiceSession.prefix();
        this.closeListener = Objects.requireNonNull(closeListener, "closeListener");
        this.previousActive = ACTIVE;
{% endif %}
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
{% if features.typed_command_palette %}
{% if features.document_history %}
            palette.setInputValueWithHistory(
                    initialQuery,
                    SFMDocumentHistoryContract.MutationKind.ACTION,
                    Optional.of(initialQuery),
                    "palette-open-replace-query"
            );
{% else %}
            palette.input.setValue(
                    initialQuery
            );
{% endif %}
{% else %}
            palette.input.setValue(
                    initialQuery
            );
{% endif %}
            palette.setFocused(palette.input);
            return;
        }
        boolean pushed = minecraft.screen != null;
        SFMCommandPaletteScreen palette = new SFMCommandPaletteScreen(actionContext, initialQuery, pushed);
        ACTIVE = palette;
        SFMScreenChangeHelpers.setOrPushScreen(palette);
    }

{% if features.typed_command_palette %}
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

{% endif %}
    @Override
    public boolean isPauseScreen() {
        return true;
    }

{% if features.typed_command_palette %}
{% if features.document_history %}
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

{% endif %}
    @Override
    public SFMKeyboardUsageContextSnapshot keyboardUsageContextSnapshot() {
        var ancestry = SFMKeyboardUsageSituations.catalog().resolve(
                SFMKeyboardUsageSituations.COMMAND_PALETTE);
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

{% endif %}
    @Override
    public Component getNarrationMessage() {
{% if features.typed_command_palette %}
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
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        if (selectedSuggestion < 0 || selectedSuggestion >= suggestions.size()) return TITLE.getComponent();
        Optional<ResourceLocation> actionId = suggestionActionId(suggestions.get(selectedSuggestion));
        if (actionId.isEmpty()) return TITLE.getComponent();
        var action = SFMClientActions.registry().get(actionId.get());
        if (action == null) return TITLE.getComponent();
        var narration = TITLE.getComponent().copy().append(". ").append(action.title()).append(". ")
{% when "26.1.2" %}
        if (selectedSuggestion < 0 || selectedSuggestion >= suggestions.size()) return TITLE.getComponent();
        Optional<Identifier> actionId = suggestionActionId(suggestions.get(selectedSuggestion));
        if (actionId.isEmpty()) return TITLE.getComponent();
        var action = SFMClientActions.registry().get(actionId.get()).map(reference -> reference.value()).orElse(null);
        if (action == null) return TITLE.getComponent();
        var narration = TITLE.getComponent().copy().append(". ").append(action.title()).append(". ")
{% endcase %}
{% endif %}
                .append(action.description());
        action.itemIcon(actionContext).ifPresent(icon -> narration.append(". Icon: " + icon.accessibleLabel()));
{% if features.typed_command_palette %}
        int bindingCount = bindingsForSuggestion(candidate).size();
{% else %}
        int bindingCount = SFMKeyBindingService.INSTANCE.bindingsForAction(actionId.get()).size();
{% endif %}
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
{% if features.typed_command_palette %}
        if (choiceSession != null) commandTree = choiceSession.activate();
{% endif %}
        SFMScreenRenderUtils.enableKeyRepeating();
{% if features.typed_command_palette %}
{% if features.document_history %}
        SFMDocumentHistoryContract.DocumentState restoredHistoryState = historyController == null
                ? null
                : historyController.session().currentState();
{% endif %}
        int width = panelWidth();
        int left = panelLeft();
{% else %}
        int width = Math.min(460, this.width - 24);
        int left = (this.width - width) / 2;
{% endif %}
        int top = panelTop();
{% if features.typed_command_palette %}
        ControlsLayout controls = controlsLayoutForPalette(top);
{% if features.single_line_input %}
        this.input = this.addRenderableWidget(new ca.teamdman.sfm.client.input.SFMSingleLineEditBox(
{% else %}
        this.input = this.addRenderableWidget(new EditBox(
{% endif %}
{% else %}
        int executeWidth = 68;
        int horizontalPadding = 10;
        int inputWidth = width - horizontalPadding * 2 - executeWidth - 4;
        this.input = this.addRenderableWidget(new EditBox(
{% endif %}
                this.font,
{% if features.typed_command_palette %}
                controls.input().x(),
                controls.input().y(),
                controls.input().width(),
                controls.input().height(),
{% if features.single_line_input %}
{% if features.document_history %}
                INPUT_PLACEHOLDER.getComponent(),
                false // This screen's document history already owns input mutations.
{% else %}
                INPUT_PLACEHOLDER.getComponent(),
                true
{% endif %}
{% else %}
                INPUT_PLACEHOLDER.getComponent()
{% endif %}
{% else %}
                left + horizontalPadding,
                top + 30,
                inputWidth,
                20,
                INPUT_PLACEHOLDER.getComponent()
{% endif %}
        ));
        this.input.setMaxLength(2048);
{% if features.typed_command_palette %}
{% if features.document_history %}
        this.input.setValue(restoredHistoryState == null
                ? this.initialQuery
                : restoredHistoryState.text());
{% else %}
        this.input.setValue(this.initialQuery);
{% endif %}
{% else %}
        this.input.setValue(this.initialQuery);
{% endif %}
        this.input.setSuggestion("");
        this.input.setResponder(this::refreshSuggestions);
        this.executeButton = this.addRenderableWidget(new SFMButtonBuilder()
{% if features.typed_command_palette %}
                .setPosition(controls.execute().x(), controls.execute().y())
                .setSize(controls.execute().width(), controls.execute().height())
{% else %}
                .setPosition(left + width - horizontalPadding - executeWidth, top + 30)
                .setSize(executeWidth, 20)
{% endif %}
                .setText(EXECUTE)
                .setOnPress(button -> executeInput())
{% if features.typed_command_palette %}
                .setTooltip(this, this.font, focusControlTooltip(EXECUTE_FOCUS_TARGET))
{% endif %}
                .build());
        this.executeButton.active = false;
{% if features.typed_command_palette %}
        this.cancelButton = this.addRenderableWidget(createCancelButton(
                controls.cancel(),
                this::cancelThroughAction,
                focusControlTooltip(CANCEL_FOCUS_TARGET)
        ));
{% endif %}
        this.consoleWidget = new SFMConsoleWidget(
                this.font,
{% if features.typed_command_palette %}
                left + HORIZONTAL_PADDING,
{% else %}
                left + horizontalPadding,
{% endif %}
                consoleTop(top),
{% if features.typed_command_palette %}
                Math.max(1, width - HORIZONTAL_PADDING * 2),
{% else %}
                width - horizontalPadding * 2,
{% endif %}
                consoleHeight()
        );
        layoutWidgets();
        this.setInitialFocus(this.input);
        this.setFocused(this.input);
        this.input.setFocused(true);
{% if features.typed_command_palette %}
{% if features.document_history %}
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
{% endif %}
        refreshSuggestions(this.input.getValue());
{% else %}
        refreshSuggestions(this.initialQuery);
{% endif %}
    }

    @Override
    public void onClose() {
{% if features.typed_command_palette %}
        if (!closeLifecycle.beginClose()) return;
        cleanupActionSurface();
{% else %}
        if (this.closing) return;
        this.closing = true;
        if (ACTIVE == this) ACTIVE = null;
{% endif %}
        if (this.pushed) {
            SFMScreenChangeHelpers.popScreen();
        } else {
            SFMScreenChangeHelpers.setScreen(null);
        }
    }

{% if features.typed_command_palette %}
    @Override
    public void dismissActionSurface() {
        onClose();
    }

{% endif %}
    @Override
    public void removed() {
{% if features.typed_command_palette %}
        closeLifecycle.beginClose();
        cleanupActionSurface();
{% else %}
        if (ACTIVE == this) ACTIVE = null;
{% endif %}
        super.removed();
    }

{% if features.typed_command_palette %}
    private void cleanupActionSurface() {
        closeLifecycle.runCleanupOnce(() -> {
            try {
{% if features.document_history %}
                if (historyRegistration != null) {
                    historyRegistration.close();
                    historyRegistration = null;
                }
                if (historyController != null) {
                    updateDocumentHistoryFocus(false);
                    historyController.close();
                    historyController = null;
                }
{% endif %}
                if (choiceSession != null && !choiceSession.invalidated()) {
                    SFMChoiceSessionService.invalidate(choiceSession);
                }
            } finally {
                try {
                    closeListener.run();
                } finally {
                    if (ACTIVE == this) ACTIVE = previousActive;
                }
            }
        });
    }

{% if features.document_history %}
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
{% endif %}

{% endif %}
    @Override
{% if features.typed_command_palette %}
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        int previousCursor = input == null ? -1 : input.getCursorPosition();
{% if features.document_history %}
        SFMDocumentHistoryContract.DocumentState before = historyController == null
                ? null
                : captureInputHistoryState();
{% endif %}
        boolean handled = keyPressedWithoutHistory(key, scanCode, modifiers);
        if (ACTIVE == this && input != null && previousCursor != input.getCursorPosition()
                && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT
                || key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END)) {
            refreshSuggestions(input.getValue());
        }
{% if features.document_history %}
        if (before != null) {
            observeInputHistoryMutation(
                    before,
                    mutationKindForKey(key, modifiers),
                    editDirectionForKey(key),
                    Optional.empty(),
                    "palette-key-" + key
            );
        }
{% endif %}
        return handled;
    }

    private boolean keyPressedWithoutHistory(int key, int scanCode, int modifiers) {
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public boolean keyPressed(int key, int scanCode, int modifiers) {
{% when "26.1.2" %}
    @MCVersionDependentBehaviour
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
{% endcase %}
{% endif %}
        if (key == GLFW.GLFW_KEY_ESCAPE) {
{% if features.typed_command_palette %}
            cancelThroughAction();
{% else %}
            onClose();
{% endif %}
            return true;
        }
{% if features.typed_command_palette %}
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE)
                && this.getFocused() instanceof Button) {
            return super.keyPressed(key, scanCode, modifiers);
        }
{% endif %}
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
{% if features.typed_command_palette %}
            int selected = suggestionViewport.selectedRow();
            switch (enterKeyAction(currentInputIsExecutable(), suggestions, selected)) {
                case EXECUTE -> executeInput();
                case APPLY_SELECTED_SUGGESTION -> {
                    applySelectedSuggestion();
                    if (currentInputIsExecutable()) executeInput();
                }
                case CONSUME -> {
                    // Usage/diagnostic rows explain an incomplete frontier.
                    // They remain non-activatable when the input itself is
                    // not yet executable.
                }
            }
{% else %}
            executeInput();
{% endif %}
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
{% if features.typed_command_palette %}
            boolean forward = (modifiers & GLFW.GLFW_MOD_SHIFT) == 0 && !Screen.hasShiftDown();
            if (forward && this.input.isFocused()) {
                applySelectedSuggestion();
{% else %}
            if (!this.input.isFocused()) {
                this.setFocused(this.input);
                this.input.setFocused(true);
{% endif %}
                return true;
            }
{% if features.typed_command_palette %}
{% case minecraft_version %}
{% when "1.19.2" %}
            if (!this.changeFocus(forward)) this.changeFocus(forward);
            return true;
        }
        if (handleCommandInputHomeEnd(this.input, key, modifiers, suggestions.size())) {
{% when "1.19.4" %}
            cycleWidgetFocus(forward);
            return true;
        }
        if (handleCommandInputHomeEnd(this.input, key, modifiers, suggestions.size())) {
{% endcase %}
{% else %}
            applySelectedSuggestion();
{% endif %}
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            if (!suggestions.isEmpty()) {
{% if features.typed_command_palette %}
                suggestionViewport.moveSelection(key == GLFW.GLFW_KEY_UP ? -1 : 1);
{% else %}
                int delta = key == GLFW.GLFW_KEY_UP ? -1 : 1;
                int next = selectedSuggestion < 0 ? 0 : selectedSuggestion + delta;
                selectedSuggestion = Math.max(0, Math.min(suggestions.size() - 1, next));
                ensureSelectedSuggestionVisible();
{% endif %}
                return true;
            }
        }
{% if features.typed_command_palette %}
        if (!suggestions.isEmpty() && key == GLFW.GLFW_KEY_PAGE_UP) {
            suggestionViewport.pageSelection(-1);
            return true;
        }
        if (!suggestions.isEmpty() && key == GLFW.GLFW_KEY_PAGE_DOWN) {
            suggestionViewport.pageSelection(1);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        return super.keyPressed(key, scanCode, modifiers);
{% when "26.1.2" %}
        return super.keyPressed(event);
{% endcase %}
{% endif %}
    }

{% if features.typed_command_palette %}
{% case minecraft_version %}
{% when "1.19.2" %}
    static EnterKeyAction enterKeyAction(
            boolean currentInputExecutable,
            List<SFMPaletteCandidate> candidates,
            int selectedIndex
    ) {
        Objects.requireNonNull(candidates, "candidates");
        if (currentInputExecutable) return EnterKeyAction.EXECUTE;
        if (selectedIndex < 0 || selectedIndex >= candidates.size()) return EnterKeyAction.EXECUTE;
        return candidates.get(selectedIndex).activatable()
                ? EnterKeyAction.APPLY_SELECTED_SUGGESTION
                : EnterKeyAction.CONSUME;
    }

    /**
     * Applies the single-line command-input Home/End contract.
     *
     * <p>The suggestion count is deliberately part of this boundary even
     * though it cannot change the result. This prevents a future regression
     * where suggestions make ordinary text-navigation keys select list rows
     * again.</p>
     */
    static boolean handleCommandInputHomeEnd(
            EditBox input,
            int key,
            int modifiers,
            int suggestionCount
    ) {
        if (suggestionCount < 0) throw new IllegalArgumentException("suggestionCount must be non-negative");
        if (!input.isFocused()) return false;
        if (key != GLFW.GLFW_KEY_HOME && key != GLFW.GLFW_KEY_END) return false;

        int navigationModifiers = modifiers & (
                GLFW.GLFW_MOD_SHIFT
                        | GLFW.GLFW_MOD_CONTROL
                        | GLFW.GLFW_MOD_ALT
                        | GLFW.GLFW_MOD_SUPER);
        if ((navigationModifiers & ~GLFW.GLFW_MOD_SHIFT) != 0) return false;

        int destination = key == GLFW.GLFW_KEY_HOME ? 0 : input.getValue().length();
        boolean extendSelection = (navigationModifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        input.setCursorPosition(destination);
        if (!extendSelection) input.setHighlightPos(destination);
        return true;
    }

    public boolean hasSuggestionsForAction() {
        return !suggestions.isEmpty();
    }

    public boolean selectFirstSuggestionForAction() {
        if (suggestions.isEmpty()) return false;
        suggestionViewport.selectFirst();
        return true;
    }

    public boolean selectLastSuggestionForAction() {
        if (suggestions.isEmpty()) return false;
        suggestionViewport.selectLast();
        return true;
    }

{% if features.document_history %}
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
{% endif %}

{% when "1.19.4" %}
    @MCVersionDependentBehaviour
    private void cycleWidgetFocus(boolean forward) {
        FocusNavigationEvent.TabNavigation navigation = new FocusNavigationEvent.TabNavigation(forward);
        ComponentPath next = nextFocusPath(navigation);
        ComponentPath current = getCurrentFocusPath();
        if (current != null) current.applyFocus(false);
        if (next == null) next = nextFocusPath(navigation);
        if (next != null) next.applyFocus(true);
    }

    static EnterKeyAction enterKeyAction(
            boolean currentInputExecutable,
            List<SFMPaletteCandidate> candidates,
            int selectedIndex
    ) {
        Objects.requireNonNull(candidates, "candidates");
        if (currentInputExecutable) return EnterKeyAction.EXECUTE;
        if (selectedIndex < 0 || selectedIndex >= candidates.size()) return EnterKeyAction.EXECUTE;
        return candidates.get(selectedIndex).activatable()
                ? EnterKeyAction.APPLY_SELECTED_SUGGESTION
                : EnterKeyAction.CONSUME;
    }

    /**
     * Applies the single-line command-input Home/End contract.
     *
     * <p>The suggestion count is deliberately part of this boundary even
     * though it cannot change the result. This prevents a future regression
     * where suggestions make ordinary text-navigation keys select list rows
     * again.</p>
     */
    static boolean handleCommandInputHomeEnd(
            EditBox input,
            int key,
            int modifiers,
            int suggestionCount
    ) {
        if (suggestionCount < 0) throw new IllegalArgumentException("suggestionCount must be non-negative");
        if (!input.isFocused()) return false;
        if (key != GLFW.GLFW_KEY_HOME && key != GLFW.GLFW_KEY_END) return false;

        int navigationModifiers = modifiers & (
                GLFW.GLFW_MOD_SHIFT
                        | GLFW.GLFW_MOD_CONTROL
                        | GLFW.GLFW_MOD_ALT
                        | GLFW.GLFW_MOD_SUPER);
        if ((navigationModifiers & ~GLFW.GLFW_MOD_SHIFT) != 0) return false;

        int destination = key == GLFW.GLFW_KEY_HOME ? 0 : input.getValue().length();
        boolean extendSelection = (navigationModifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        input.setCursorPosition(destination);
        if (!extendSelection) input.setHighlightPos(destination);
        return true;
    }

    public boolean hasSuggestionsForAction() {
        return !suggestions.isEmpty();
    }

    public boolean selectFirstSuggestionForAction() {
        if (suggestions.isEmpty()) return false;
        suggestionViewport.selectFirst();
        return true;
    }

    public boolean selectLastSuggestionForAction() {
        if (suggestions.isEmpty()) return false;
        suggestionViewport.selectLast();
        return true;
    }

{% if features.document_history %}
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
{% endif %}

{% endcase %}
{% endif %}
    @Override
{% if features.typed_command_palette %}
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
{% if features.document_history %}
        return observePointerMutation(
                "pointer-press-" + button,
                () -> mouseClickedWithoutHistory(mouseX, mouseY, button));
{% else %}
        return mouseClickedWithoutHistory(mouseX, mouseY, button);
{% endif %}
    }

    private boolean mouseClickedWithoutHistory(double mouseX, double mouseY, int button) {
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
{% when "26.1.2" %}
    @MCVersionDependentBehaviour
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
{% endcase %}
{% endif %}
        if (this.consoleWidget.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
{% if features.typed_command_palette %}
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && canonicalPreviewBounds(panelTop()).filter(bounds -> bounds.contains(mouseX, mouseY)).isPresent()) {
            Optional<String> command = canonicalPreviewCommand();
            if (command.isPresent()) {
                ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceCopyFeedback.production().copy(
                        actionContext,
                        command.orElseThrow(),
                        CANONICAL_COMMAND_COPIED.getComponent()
                );
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
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
{% when "26.1.2" %}
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
                    Optional<Identifier> actionId = suggestionActionId(suggestions.get(suggestionIndex));
                    if (actionId.isPresent()) {
                        SFMScreenChangeHelpers.setOrPushScreen(new SFMKeyBindingDetailsScreen(this, actionId.get()));
                        return true;
                    }
                }
                selectedSuggestion = suggestionIndex;
                applySelectedSuggestion();
{% endcase %}
{% endif %}
                return true;
            }
        }
{% if features.typed_command_palette %}
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (executeButton.isMouseOver(mouseX, mouseY)) {
                openFocusControlActions(EXECUTE_FOCUS_TARGET, EXECUTE.getComponent());
                return true;
            }
            if (cancelButton.isMouseOver(mouseX, mouseY)) {
                openFocusControlActions(CANCEL_FOCUS_TARGET, CANCEL.getComponent());
                return true;
            }
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            if (input.isFocused()) refreshSuggestions(input.getValue());
            return true;
        }
        SFMVerticalListViewport.ScrollbarGeometry scrollbar = suggestionScrollbarGeometry();
        if (suggestionViewport.mouseClickedScrollbar(mouseX, mouseY, button, scrollbar)) return true;
        OptionalInt row = suggestionViewport.rowAt(
                mouseX,
                mouseY,
                suggestionRowBounds(scrollbar.visible()),
                SUGGESTION_ROW_HEIGHT,
                SUGGESTION_ROW_CONTENT_HEIGHT);
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && row.isPresent()) {
            openCandidateActions(row.getAsInt());
            return true;
        }
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
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        return super.mouseClicked(mouseX, mouseY, button);
{% when "26.1.2" %}
        return super.mouseClicked(event, doubleClick);
{% endcase %}
{% endif %}
    }

    @Override
{% if features.typed_command_palette %}
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
{% if features.document_history %}
        return observePointerMutation(
                "pointer-release-" + button,
                () -> mouseReleasedWithoutHistory(mouseX, mouseY, button));
{% else %}
        return mouseReleasedWithoutHistory(mouseX, mouseY, button);
{% endif %}
    }

    private boolean mouseReleasedWithoutHistory(double mouseX, double mouseY, int button) {
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
{% when "26.1.2" %}
    @MCVersionDependentBehaviour
    public boolean mouseReleased(MouseButtonEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
{% endcase %}
{% endif %}
        if (this.consoleWidget.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
{% if features.typed_command_palette %}
        return suggestionViewport.mouseReleasedScrollbar(button)
                || super.mouseReleased(mouseX, mouseY, button);
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        return super.mouseReleased(mouseX, mouseY, button);
{% when "26.1.2" %}
        return super.mouseReleased(event);
{% endcase %}
{% endif %}
    }

    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% when "26.1.2" %}
    @MCVersionDependentBehaviour
{% endcase %}
    public boolean mouseDragged(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            double mouseX,
            double mouseY,
            int button,
{% when "26.1.2" %}
            MouseButtonEvent event,
{% endcase %}
            double dragX,
            double dragY
{% if features.typed_command_palette %}
    ) {
{% if features.document_history %}
        return observePointerMutation(
                "pointer-drag-" + button,
                () -> mouseDraggedWithoutHistory(mouseX, mouseY, button, dragX, dragY));
{% else %}
        return mouseDraggedWithoutHistory(mouseX, mouseY, button, dragX, dragY);
{% endif %}
    }

    private boolean mouseDraggedWithoutHistory(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
{% endif %}
    ) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% when "26.1.2" %}
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
{% endcase %}
        if (this.consoleWidget.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
{% if features.typed_command_palette %}
        return suggestionViewport.mouseDraggedScrollbar(mouseY, button, suggestionScrollbarGeometry())
                || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
{% when "26.1.2" %}
        return super.mouseDragged(event, dragX, dragY);
{% endcase %}
{% endif %}
    }

    @Override
{% if features.typed_command_palette %}
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        ScrollRegion region = scrollRegionAt(
                mouseX, mouseY, suggestionListBounds(), consoleBounds());
        if (region == ScrollRegion.CONSOLE
                && this.consoleWidget.mouseScrolled(mouseX, mouseY, delta)) return true;
        if (region == ScrollRegion.SUGGESTIONS
                && suggestionViewport.scrollWheel(delta)) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.consoleWidget.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    @MCVersionDependentBehaviour
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (this.consoleWidget.mouseScrolled(mouseX, mouseY, deltaY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
{% endcase %}
{% endif %}
    }

    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    @MCVersionDependentBehaviour
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
{% when "26.1.2" %}
    @MCVersionDependentBehaviour
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
{% endcase %}
        layoutWidgets();
        SFMClientTheme theme = SFMClientThemeService.active();
        int panel = theme.colour(SFMColourRole.PANEL_BACKGROUND);
        int border = theme.colour(SFMColourRole.PANEL_BORDER);
        int text = theme.colour(SFMColourRole.TEXT_PRIMARY);
        int muted = theme.colour(SFMColourRole.TEXT_MUTED);
        int errorColour = theme.colour(SFMColourRole.TEXT_ERROR);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        fill(poseStack, 0, 0, this.width, this.height, theme.colour(SFMColourRole.SCREEN_OVERLAY));
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        graphics.fill(0, 0, this.width, this.height, theme.colour(SFMColourRole.SCREEN_OVERLAY));
{% endcase %}
        int left = panelLeft();
        int top = panelTop();
        int right = left + panelWidth();
        int bottom = top + panelHeight();
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        fill(poseStack, left, top, right, bottom, panel);
        fill(poseStack, left, top, right, top + 1, border);
        fill(poseStack, left, bottom - 1, right, bottom, border);
        fill(poseStack, left, top, left + 1, bottom, border);
        fill(poseStack, right - 1, top, right, bottom, border);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        graphics.fill(left, top, right, bottom, panel);
        graphics.fill(left, top, right, top + 1, border);
        graphics.fill(left, bottom - 1, right, bottom, border);
        graphics.fill(left, top, left + 1, bottom, border);
        graphics.fill(right - 1, top, right, bottom, border);
{% endcase %}

{% if features.typed_command_palette %}
        String heading = SFMTextSummary.fitLine(title.getString(), panelWidth() - 20,
                value -> font.width(Component.literal(value).withStyle(ChatFormatting.BOLD)));
        SFMFontUtils.draw(poseStack, this.font, Component.literal(heading).withStyle(ChatFormatting.BOLD),
                left + 10, top + 12, text, false);
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        SFMFontUtils.draw(poseStack, this.font, TITLE.getComponent().withStyle(ChatFormatting.BOLD),
                left + 10, top + 12, text, false);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        SFMFontUtils.draw(graphics, this.font, TITLE.getComponent().withStyle(ChatFormatting.BOLD),
                left + 10, top + 12, text, false);
{% endcase %}
{% endif %}
        Component guidance = insertedRequiredArgumentSeparator
                ? REQUIRED_ARGUMENT.getComponent().withStyle(ChatFormatting.GOLD)
                : ACCEPT_SUGGESTION.getComponent(Component.literal("Tab").withStyle(ChatFormatting.AQUA));
        SFMFontUtils.draw(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                poseStack,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
                graphics,
{% endcase %}
                this.font,
                guidance,
                left + 10,
{% if features.typed_command_palette %}
                top + shiftedOffset(GUIDANCE_TOP_OFFSET),
{% else %}
                top + 54,
{% endif %}
                muted,
                false
        );
{% if features.typed_command_palette %}
{% else %}
        int visibleSuggestions = visibleSuggestionCount();
{% endif %}
        if (suggestions.isEmpty()) {
{% if features.typed_command_palette %}
            SFMFontUtils.draw(
                    poseStack,
                    this.font,
                    EMPTY_RESULTS.getComponent(),
                    left + 10,
                    top + shiftedOffset(SUGGESTION_TOP_OFFSET) + 2,
                    muted,
                    false
            );
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            SFMFontUtils.draw(
                    poseStack,
                    this.font,
                    EMPTY_RESULTS.getComponent(),
                    left + 10,
                    top + 72,
                    muted,
                    false
            );
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            SFMFontUtils.draw(
                    graphics,
                    this.font,
                    EMPTY_RESULTS.getComponent(),
                    left + 10,
                    top + 72,
                    muted,
                    false
            );
{% endcase %}
{% endif %}
        } else {
{% if features.typed_command_palette %}
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
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            for (int i = 0; i < visibleSuggestions; i++) {
                int suggestionIndex = firstVisibleSuggestion + i;
                if (suggestionIndex >= suggestions.size()) break;
                int y = top + 72 + i * SUGGESTION_ROW_HEIGHT;
                if (suggestionIndex == selectedSuggestion) {
                    fill(poseStack, left + 6, y - 2, right - 6, y + 14,
                            theme.colour(SFMColourRole.PANEL_SELECTION));
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            for (int i = 0; i < visibleSuggestions; i++) {
                int suggestionIndex = firstVisibleSuggestion + i;
                if (suggestionIndex >= suggestions.size()) break;
                int y = top + 72 + i * SUGGESTION_ROW_HEIGHT;
                if (suggestionIndex == selectedSuggestion) {
                    graphics.fill(left + 6, y - 2, right - 6, y + 14,
                            theme.colour(SFMColourRole.PANEL_SELECTION));
{% endcase %}
{% endif %}
                }
{% if features.typed_command_palette %}
                SFMPaletteCandidate suggestion = suggestions.get(suggestionIndex);
{% else %}
                Suggestion suggestion = suggestions.get(suggestionIndex);
{% endif %}
                int textX = actionIcon(suggestion).isPresent()
                        ? left + 10 + SFMItemIconRenderer.SIZE + 4
                        : left + 12;
{% if features.typed_command_palette %}
                int rowText = suggestion.activatable() ? text : muted;
                SuggestionTextPresentation textPresentation = suggestionTextPresentation(
                        suggestion, textX, rows, right, scrollbar.visible());
                SFMFontUtils.draw(poseStack, this.font, textPresentation.renderedText(),
                        textX, textY, rowText, false);
                renderBindingSummary(poseStack, suggestion, right, textY, scrollbar.visible());
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                SFMFontUtils.draw(poseStack, this.font, truncateSuggestion(suggestion, textX - left),
                        textX, y, text, false);
                renderBindingSummary(poseStack, suggestion, right, y);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
                SFMFontUtils.draw(graphics, this.font, truncateSuggestion(suggestion, textX - left),
                        textX, y, text, false);
                renderBindingSummary(graphics, suggestion, right, y);
{% endcase %}
{% endif %}
            }
{% if features.typed_command_palette %}
            renderSuggestionScrollbar(poseStack, mouseX, mouseY, scrollbar);
{% endif %}
        }
{% if features.typed_command_palette %}
        renderCanonicalCommandPreview(poseStack, mouseX, mouseY, top, panel, border, muted);
{% endif %}
        if (!this.error.isEmpty()) {
            SFMFontUtils.draw(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                    poseStack,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
                    graphics,
{% endcase %}
                    this.font,
                    truncateToPanel(this.error),
                    left + 10,
                    consoleTop(top) - 12,
                    errorColour,
                    false
            );
        }
        this.consoleWidget.replaceLines(this.feedback);
{% if features.typed_command_palette %}
        this.consoleWidget.render(poseStack, mouseX, mouseY, partialTick);
        super.render(poseStack, mouseX, mouseY, partialTick);
        renderActionIconsOnTop(poseStack);
        renderSuggestionTooltip(poseStack, mouseX, mouseY);
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        this.consoleWidget.render(poseStack, mouseX, mouseY, partialTick);
        super.render(poseStack, mouseX, mouseY, partialTick);
        renderActionIconsOnTop(poseStack);
        renderActionIconTooltip(poseStack, mouseX, mouseY);
        renderActionDetailsTooltip(poseStack, mouseX, mouseY);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        this.consoleWidget.render(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderActionIconsOnTop(graphics);
        renderActionIconTooltip(graphics, mouseX, mouseY);
        renderActionDetailsTooltip(graphics, mouseX, mouseY);
{% when "26.1.2" %}
        this.consoleWidget.render(graphics, mouseX, mouseY, partialTick);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        renderActionIconsOnTop(graphics);
        renderActionIconTooltip(graphics, mouseX, mouseY);
        renderActionDetailsTooltip(graphics, mouseX, mouseY);
{% endcase %}
{% endif %}
    }

{% if features.typed_command_palette %}
    private Optional<ca.teamdman.sfm.client.presentation.SFMItemIcon> actionIcon(SFMPaletteCandidate suggestion) {
        Optional<ResourceLocation> actionId = suggestionActionId(suggestion);
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    private Optional<ca.teamdman.sfm.client.presentation.SFMItemIcon> actionIcon(Suggestion suggestion) {
        Optional<ResourceLocation> actionId = suggestionActionId(suggestion);
{% when "26.1.2" %}
    private Optional<ca.teamdman.sfm.client.presentation.SFMItemIcon> actionIcon(Suggestion suggestion) {
        Optional<Identifier> actionId = suggestionActionId(suggestion);
{% endcase %}
{% endif %}
        if (actionId.isEmpty()) return Optional.empty();
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        var action = SFMClientActions.registry().get(actionId.get());
{% when "26.1.2" %}
        var action = SFMClientActions.registry().get(actionId.get()).map(reference -> reference.value()).orElse(null);
{% endcase %}
        if (action == null) return Optional.empty();
        var themed = SFMClientThemeService.active().actionIcons().get(actionId.get());
        return themed == null ? action.itemIcon(actionContext) : Optional.of(themed);
    }

{% if features.typed_command_palette %}
{% case minecraft_version %}
{% when "1.19.2" %}
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
{% when "1.19.4" %}
    @MCVersionDependentBehaviour
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
                    SFMItemIconRenderer.render(poseStack, minecraft, icon, panelLeft() + 10, y)
            );
{% endcase %}
{% else %}
{% case minecraft_version %}
{% when "1.19.2" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private void renderActionIconsOnTop(PoseStack poseStack) {
        // The palette is translucent and intentionally preserves the title/world colour buffer.
        // Clear only stale scene depth before GUI item models so they cannot be hidden by the origin screen.
        RenderSystem.clear(0x00000100, Minecraft.ON_OSX);
        int count = visibleSuggestionCount();
        for (int index = 0; index < count; index++) {
            int suggestionIndex = firstVisibleSuggestion + index;
            if (suggestionIndex >= suggestions.size()) break;
            int y = panelTop() + 68 + index * SUGGESTION_ROW_HEIGHT;
            actionIcon(suggestions.get(suggestionIndex)).ifPresent(icon ->
                    SFMItemIconRenderer.render(minecraft, icon, panelLeft() + 10, y)
            );
{% when "1.19.4" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private void renderActionIconsOnTop(PoseStack poseStack) {
        // The palette is translucent and intentionally preserves the title/world colour buffer.
        // Clear only stale scene depth before GUI item models so they cannot be hidden by the origin screen.
        RenderSystem.clear(0x00000100, Minecraft.ON_OSX);
        int count = visibleSuggestionCount();
        for (int index = 0; index < count; index++) {
            int suggestionIndex = firstVisibleSuggestion + index;
            if (suggestionIndex >= suggestions.size()) break;
            int y = panelTop() + 68 + index * SUGGESTION_ROW_HEIGHT;
            actionIcon(suggestions.get(suggestionIndex)).ifPresent(icon ->
                    SFMItemIconRenderer.render(poseStack, minecraft, icon, panelLeft() + 10, y)
            );
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private void renderActionIconsOnTop(GuiGraphics graphics) {
        // The palette is translucent and intentionally preserves the title/world colour buffer.
        // Clear only stale scene depth before GUI item models so they cannot be hidden by the origin screen.
        RenderSystem.clear(0x00000100, Minecraft.ON_OSX);
        int count = visibleSuggestionCount();
        for (int index = 0; index < count; index++) {
            int suggestionIndex = firstVisibleSuggestion + index;
            if (suggestionIndex >= suggestions.size()) break;
            int y = panelTop() + 68 + index * SUGGESTION_ROW_HEIGHT;
            actionIcon(suggestions.get(suggestionIndex)).ifPresent(icon ->
                    SFMItemIconRenderer.render(graphics, minecraft, icon, panelLeft() + 10, y)
            );
{% when "26.1.2" %}
    private void renderActionIconsOnTop(GuiGraphicsExtractor graphics) {
        int count = visibleSuggestionCount();
        for (int index = 0; index < count; index++) {
            int suggestionIndex = firstVisibleSuggestion + index;
            if (suggestionIndex >= suggestions.size()) break;
            int y = panelTop() + 68 + index * SUGGESTION_ROW_HEIGHT;
            actionIcon(suggestions.get(suggestionIndex)).ifPresent(icon -> {
                var resolved = SFMItemIconResolver.resolve(icon);
                graphics.item(resolved.stack(), panelLeft() + 10, y);
            });
{% endcase %}
{% endif %}
        }
    }

{% if features.typed_command_palette %}
    private void renderSuggestionTooltip(
            PoseStack poseStack,
            int mouseX,
            int mouseY
    ) {
        SFMVerticalListViewport.ScrollbarGeometry scrollbar = suggestionScrollbarGeometry();
        SFMVerticalListViewport.Bounds rows = suggestionRowBounds(scrollbar.visible());
        OptionalInt row = suggestionViewport.rowAt(
                mouseX,
                mouseY,
                rows,
                SUGGESTION_ROW_HEIGHT,
                SUGGESTION_ROW_CONTENT_HEIGHT);
        if (row.isEmpty()) return;
        int suggestionIndex = row.getAsInt();
        SFMPaletteCandidate suggestion = suggestions.get(suggestionIndex);
        int visibleIndex = suggestionIndex - suggestionViewport.firstVisibleRow();
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private void renderActionIconTooltip(
            PoseStack poseStack,
            int mouseX,
            int mouseY
    ) {
        int firstY = panelTop() + 68;
        int visibleIndex = (mouseY - firstY) / SUGGESTION_ROW_HEIGHT;
        int suggestionIndex = firstVisibleSuggestion + visibleIndex;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private void renderActionIconTooltip(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        int firstY = panelTop() + 68;
        int visibleIndex = (mouseY - firstY) / SUGGESTION_ROW_HEIGHT;
        int suggestionIndex = firstVisibleSuggestion + visibleIndex;
{% when "26.1.2" %}
    private void renderActionIconTooltip(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY
    ) {
        int firstY = panelTop() + 68;
        int visibleIndex = (mouseY - firstY) / SUGGESTION_ROW_HEIGHT;
        int suggestionIndex = firstVisibleSuggestion + visibleIndex;
{% endcase %}
{% endif %}
        int iconX = panelLeft() + 10;
{% if features.typed_command_palette %}
        int iconY = rows.y() + visibleIndex * SUGGESTION_ROW_HEIGHT;
        boolean iconHovered = mouseX >= iconX && mouseX < iconX + SFMItemIconRenderer.SIZE
                && mouseY >= iconY && mouseY < iconY + SFMItemIconRenderer.SIZE;
        boolean detailsHovered = mouseX >= suggestionDetailsLeft(scrollbar.visible());
        int textX = actionIcon(suggestion).isPresent()
                ? panelLeft() + 10 + SFMItemIconRenderer.SIZE + 4
                : panelLeft() + 12;
        SuggestionTextPresentation textPresentation = suggestionTextPresentation(
                suggestion,
                textX,
                rows,
                panelLeft() + panelWidth(),
                scrollbar.visible()
        );
        switch (suggestionTooltipKind(textPresentation.truncated(), iconHovered, detailsHovered)) {
            case COMPLETE_CANDIDATE -> renderCompleteCandidateTooltip(
                    poseStack, candidateInspection(suggestion), mouseX, mouseY);
            case ICON -> renderActionIconTooltip(poseStack, suggestion, mouseX, mouseY);
            case ACTION_DETAILS -> renderActionDetailsTooltip(poseStack, suggestion, mouseX, mouseY);
            case NONE -> {
            }
        }
    }

    static SuggestionTooltipKind suggestionTooltipKind(
            boolean truncated,
            boolean iconHovered,
            boolean detailsHovered
    ) {
        if (truncated) return SuggestionTooltipKind.COMPLETE_CANDIDATE;
        if (iconHovered) return SuggestionTooltipKind.ICON;
        if (detailsHovered) return SuggestionTooltipKind.ACTION_DETAILS;
        return SuggestionTooltipKind.NONE;
    }

    private void renderCompleteCandidateTooltip(
            PoseStack poseStack,
            SFMPaletteCandidateInspection inspection,
            int mouseX,
            int mouseY
    ) {
        int maximumWidth = Math.max(1, Math.min(360, this.width - 40));
        int maximumRows = tooltipRowBudget(this.height);
        ArrayList<net.minecraft.util.FormattedCharSequence> wrapped = new ArrayList<>();
        List<String> lines = inspection.accessibleDescriptionLines();
        boolean abbreviated = false;
        for (int index = 0; index < lines.size(); index++) {
            String bounded = SFMTextSummary.codePoints(lines.get(index), 4096);
            abbreviated |= !bounded.equals(lines.get(index));
            Component line = Component.literal(bounded).withStyle(
                    index == 0 ? ChatFormatting.AQUA : ChatFormatting.GRAY);
            wrapped.addAll(this.font.split(line, maximumWidth));
            if (wrapped.size() > maximumRows) { abbreviated = true; break; }
        }
        if (abbreviated) {
            var hint = this.font.split(MORE_DETAILS.getComponent().withStyle(ChatFormatting.GOLD), maximumWidth);
            int hintRows = Math.min(maximumRows, hint.size());
            wrapped = new ArrayList<>(wrapped.subList(0, Math.min(wrapped.size(), maximumRows - hintRows)));
            wrapped.addAll(hint.subList(0, hintRows));
        }
        int contentWidth = wrapped.stream().mapToInt(this.font::width).max().orElse(0);
        TooltipAnchor anchor = tooltipAnchor(
                this.width, this.height, mouseX, mouseY, contentWidth, wrapped.size());
        renderTooltip(poseStack, wrapped, anchor.x(), anchor.y());
    }

    static int tooltipRowBudget(int screenHeight) {
        return Math.max(1, Math.min(18, (screenHeight - 32) / 10));
    }

    record TooltipAnchor(int x, int y) { }

    static TooltipAnchor tooltipAnchor(
            int screenWidth, int screenHeight, int mouseX, int mouseY,
            int contentWidth, int rowCount
    ) {
        // Screen.renderTooltip offsets x by 12, then flips the whole tooltip left
        // if it overflows. Prewrapped text can be wider than either side of the
        // pointer, so keep the anchor in the right-placement interval instead.
        // Reserve the vanilla four-pixel border and the first-line vertical gap.
        int x = Math.max(0, Math.min(mouseX, screenWidth - contentWidth - 16));
        int contentHeight = rowCount * 10 + 2;
        int top = Math.max(4, Math.min(mouseY - 12, screenHeight - contentHeight - 6));
        return new TooltipAnchor(x, top + 12);
    }

    private void renderActionIconTooltip(
            PoseStack poseStack,
            SFMPaletteCandidate suggestion,
            int mouseX,
            int mouseY
    ) {
        Optional<ResourceLocation> actionId = suggestionActionId(suggestion);
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        int iconY = panelTop() + 68 + visibleIndex * SUGGESTION_ROW_HEIGHT;
        if (visibleIndex < 0 || visibleIndex >= visibleSuggestionCount() || suggestionIndex >= suggestions.size()
                || mouseX < iconX || mouseX >= iconX + SFMItemIconRenderer.SIZE
                || mouseY < iconY || mouseY >= iconY + SFMItemIconRenderer.SIZE) return;
        Optional<ResourceLocation> actionId = suggestionActionId(suggestions.get(suggestionIndex));
{% when "26.1.2" %}
        int iconY = panelTop() + 68 + visibleIndex * SUGGESTION_ROW_HEIGHT;
        if (visibleIndex < 0 || visibleIndex >= visibleSuggestionCount() || suggestionIndex >= suggestions.size()
                || mouseX < iconX || mouseX >= iconX + SFMItemIconRenderer.SIZE
                || mouseY < iconY || mouseY >= iconY + SFMItemIconRenderer.SIZE) return;
        Optional<Identifier> actionId = suggestionActionId(suggestions.get(suggestionIndex));
{% endcase %}
{% endif %}
        if (actionId.isEmpty()) return;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        var action = SFMClientActions.registry().get(actionId.get());
{% when "26.1.2" %}
        var action = SFMClientActions.registry().get(actionId.get()).map(reference -> reference.value()).orElse(null);
{% endcase %}
        if (action == null) return;
        action.itemIcon(actionContext).ifPresent(icon -> {
            var resolved = SFMItemIconResolver.resolve(icon);
            String fallback = resolved.usedFallback() ? " (using fallback item)" : "";
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            renderTooltip(poseStack, Component.literal(resolved.accessibleLabel() + fallback), mouseX, mouseY);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            graphics.renderTooltip(font, Component.literal(resolved.accessibleLabel() + fallback), mouseX, mouseY);
{% when "26.1.2" %}
            graphics.setTooltipForNextFrame(font, Component.literal(resolved.accessibleLabel() + fallback), mouseX, mouseY);
{% endcase %}
        });
    }

{% if features.typed_command_palette %}
    private void renderBindingSummary(
            PoseStack poseStack,
            SFMPaletteCandidate suggestion,
            int right,
            int y,
            boolean scrollbarVisible
    ) {
        Optional<ResourceLocation> actionId = suggestionActionId(suggestion);
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private void renderBindingSummary(
            PoseStack poseStack,
            Suggestion suggestion,
            int right,
            int y
    ) {
        Optional<ResourceLocation> actionId = suggestionActionId(suggestion);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private void renderBindingSummary(
            GuiGraphics graphics,
            Suggestion suggestion,
            int right,
            int y
    ) {
        Optional<ResourceLocation> actionId = suggestionActionId(suggestion);
{% when "26.1.2" %}
    private void renderBindingSummary(
            GuiGraphicsExtractor graphics,
            Suggestion suggestion,
            int right,
            int y
    ) {
        Optional<Identifier> actionId = suggestionActionId(suggestion);
{% endcase %}
{% endif %}
        if (actionId.isEmpty()) return;
{% if features.typed_command_palette %}
        List<SFMKeyBinding> bindings = bindingsForSuggestion(suggestion);
        SFMKeyBinding binding = SFMKeyBindingCycle.displayedBinding(bindings, bindingCycleTicks);
        if (binding == null) return;
        int scrollbarSpace = scrollbarVisible ? SCROLLBAR_WIDTH + SCROLLBAR_GAP : 0;
        int bindingAreaLeft = bindingAreaLeft(right, scrollbarVisible);
{% else %}
        List<SFMKeyBinding> bindings = SFMKeyBindingService.INSTANCE.bindingsForAction(actionId.get());
        String bindingText = SFMKeyBindingCycle.displayedSequence(bindings, bindingCycleTicks);
        int bindingAreaLeft = right - 146;
{% endif %}
        int bindingAreaWidth = 108;
{% if features.typed_command_palette %}
        SFMKeycapRenderer.draw(
                poseStack,
                font,
                binding.sequence(),
                bindingAreaLeft,
                y,
                bindingAreaWidth,
                binding.enabled());
        SFMFontUtils.draw(poseStack, font, "[?]", right - 28 - scrollbarSpace, y,
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        String shown = font.plainSubstrByWidth(bindingText, bindingAreaWidth);
        SFMFontUtils.draw(poseStack, font, shown, bindingAreaLeft, y,
                SFMClientThemeService.active().colour(SFMColourRole.TEXT_ACCENT), false);
        SFMFontUtils.draw(poseStack, font, "[?]", right - 28, y,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        String shown = font.plainSubstrByWidth(bindingText, bindingAreaWidth);
        SFMFontUtils.draw(graphics, font, shown, bindingAreaLeft, y,
                SFMClientThemeService.active().colour(SFMColourRole.TEXT_ACCENT), false);
        SFMFontUtils.draw(graphics, font, "[?]", right - 28, y,
{% endcase %}
{% endif %}
                SFMClientThemeService.active().colour(SFMColourRole.TEXT_ACCENT), false);
    }

{% if features.typed_command_palette %}
    private void renderActionDetailsTooltip(
            PoseStack poseStack,
            SFMPaletteCandidate suggestion,
            int mouseX,
            int mouseY
    ) {
        Optional<ResourceLocation> actionId = suggestionActionId(suggestion);
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private void renderActionDetailsTooltip(
            PoseStack poseStack,
            int mouseX,
            int mouseY
    ) {
        int right = panelLeft() + panelWidth();
        int firstY = panelTop() + 70;
        int visibleIndex = (mouseY - firstY) / SUGGESTION_ROW_HEIGHT;
        int suggestionIndex = firstVisibleSuggestion + visibleIndex;
        if (mouseX < right - 28 || mouseX > right - 6 || visibleIndex < 0
                || visibleIndex >= visibleSuggestionCount() || suggestionIndex >= suggestions.size()) return;
        Optional<ResourceLocation> actionId = suggestionActionId(suggestions.get(suggestionIndex));
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private void renderActionDetailsTooltip(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        int right = panelLeft() + panelWidth();
        int firstY = panelTop() + 70;
        int visibleIndex = (mouseY - firstY) / SUGGESTION_ROW_HEIGHT;
        int suggestionIndex = firstVisibleSuggestion + visibleIndex;
        if (mouseX < right - 28 || mouseX > right - 6 || visibleIndex < 0
                || visibleIndex >= visibleSuggestionCount() || suggestionIndex >= suggestions.size()) return;
        Optional<ResourceLocation> actionId = suggestionActionId(suggestions.get(suggestionIndex));
{% when "26.1.2" %}
    private void renderActionDetailsTooltip(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY
    ) {
        int right = panelLeft() + panelWidth();
        int firstY = panelTop() + 70;
        int visibleIndex = (mouseY - firstY) / SUGGESTION_ROW_HEIGHT;
        int suggestionIndex = firstVisibleSuggestion + visibleIndex;
        if (mouseX < right - 28 || mouseX > right - 6 || visibleIndex < 0
                || visibleIndex >= visibleSuggestionCount() || suggestionIndex >= suggestions.size()) return;
        Optional<Identifier> actionId = suggestionActionId(suggestions.get(suggestionIndex));
{% endcase %}
{% endif %}
        if (actionId.isEmpty()) return;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        var action = SFMClientActions.registry().get(actionId.get());
{% when "26.1.2" %}
        var action = SFMClientActions.registry().get(actionId.get()).map(reference -> reference.value()).orElse(null);
{% endcase %}
        if (action == null) return;
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(action.title().copy().withStyle(ChatFormatting.AQUA));
        tooltip.add(action.description());
        action.itemIcon(actionContext).ifPresent(icon -> tooltip.add(
                Component.literal("Icon: " + icon.accessibleLabel()).withStyle(ChatFormatting.GRAY)
        ));
{% if features.typed_command_palette %}
        List<SFMKeyBinding> bindings = bindingsForSuggestion(suggestion);
{% else %}
        List<SFMKeyBinding> bindings = SFMKeyBindingService.INSTANCE.bindingsForAction(actionId.get());
{% endif %}
        if (bindings.isEmpty()) tooltip.add(Component.literal("No key bindings").withStyle(ChatFormatting.GRAY));
        else bindings.forEach(binding -> tooltip.add(Component.literal(
                SFMKeyBindingDisplay.format(binding.sequence()) + (binding.enabled() ? "" : " (disabled)")
        )));
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        renderComponentTooltip(poseStack, tooltip, mouseX, mouseY);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
{% when "26.1.2" %}
        graphics.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
{% endcase %}
    }

{% if features.typed_command_palette %}
    private SuggestionTextPresentation suggestionTextPresentation(
            SFMPaletteCandidate suggestion,
            int textX,
            SFMVerticalListViewport.Bounds rows,
            int panelRight,
            boolean scrollbarVisible
    ) {
        OptionalInt trailingAffordanceLeft = bindingsForSuggestion(suggestion).isEmpty()
                ? OptionalInt.empty()
                : OptionalInt.of(bindingAreaLeft(panelRight, scrollbarVisible));
        int availableWidth = suggestionTextAvailableWidth(
                textX,
                rows.x() + rows.width(),
                trailingAffordanceLeft
        );
        String displayText = suggestion.displayText();
        String rendered = SFMTextSummary.fitLine(displayText, availableWidth, this.font::width);
        return new SuggestionTextPresentation(rendered, availableWidth, !rendered.equals(displayText));
{% else %}
    private String truncateSuggestion(
            Suggestion suggestion,
            int leftInset
    ) {
        return font.plainSubstrByWidth(suggestion.getText(), Math.max(20, panelWidth() - 150 - leftInset));
{% endif %}
    }

{% if features.typed_command_palette %}
    static int suggestionTextAvailableWidth(
            int textX,
            int rowRight,
            OptionalInt trailingAffordanceLeft
    ) {
        int textRight = trailingAffordanceLeft.isPresent()
                ? Math.min(rowRight, trailingAffordanceLeft.getAsInt())
                : rowRight;
        return Math.max(0, textRight - textX - 4);
    }

    private static int bindingAreaLeft(int panelRight, boolean scrollbarVisible) {
        int scrollbarSpace = scrollbarVisible ? SCROLLBAR_WIDTH + SCROLLBAR_GAP : 0;
        return panelRight - 146 - scrollbarSpace;
    }

    private Optional<ResourceLocation> suggestionActionId(SFMPaletteCandidate suggestion) {
        if (suggestion.actionId() != null) return Optional.of(suggestion.actionId());
        if (choiceSession != null) {
            Optional<ResourceLocation> actionId = choiceSession.actionIdForSuggestion(suggestion.replacementText());
            if (actionId.isPresent()) return actionId;
        }
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    private static Optional<ResourceLocation> suggestionActionId(Suggestion suggestion) {
{% when "26.1.2" %}
    private static Optional<Identifier> suggestionActionId(Suggestion suggestion) {
{% endcase %}
{% endif %}
        try {
{% if features.typed_command_palette %}
            String suggestionText = suggestion.replacementText().stripLeading();
            int separator = 0;
            while (separator < suggestionText.length()
                    && !Character.isWhitespace(suggestionText.charAt(separator))) separator++;
            ResourceLocation id = new ResourceLocation(suggestionText.substring(0, separator));
            return SFMClientActions.registry().get(id) == null ? Optional.empty() : Optional.of(id);
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
            ResourceLocation id = new ResourceLocation(suggestion.getText());
            return SFMClientActions.registry().get(id) == null ? Optional.empty() : Optional.of(id);
{% when "1.21", "1.21.1" %}
            ResourceLocation id = SFMResourceLocation.parse(suggestion.getText());
            return SFMClientActions.registry().get(id) == null ? Optional.empty() : Optional.of(id);
{% when "26.1.2" %}
            Identifier id = SFMResourceLocation.parse(suggestion.getText());
            return SFMClientActions.registry().get(id).map(reference -> reference.value()).orElse(null) == null ? Optional.empty() : Optional.of(id);
{% endcase %}
{% endif %}
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

{% if features.typed_command_palette %}
    private List<SFMKeyBinding> bindingsForSuggestion(SFMPaletteCandidate suggestion) {
        Optional<ResourceLocation> actionId = suggestionActionId(suggestion);
        Optional<String> command = canonicalCommandForSuggestion(suggestion);
        if (actionId.isEmpty() || command.isEmpty()) return List.of();
        return SFMKeyBindingService.INSTANCE.bindingsForCommand(actionId.get(), command.get());
    }

    private Optional<String> canonicalCommandForSuggestion(SFMPaletteCandidate suggestion) {
        if (!suggestion.activatable()) return Optional.empty();
        String surfaceCommand = suggestion.apply(commandInput()).afterValue().strip();
        if (choiceSession != null) {
            return choiceSession.canonicalCommandForSurfaceCommand(surfaceCommand);
        }
        return Optional.of(surfaceCommand);
    }

    private Optional<String> canonicalExecutableCommandForSuggestion(SFMPaletteCandidate suggestion) {
        return canonicalCommandForSuggestion(suggestion).filter(command -> isExecutable(
                SFMClientActions.commandTree().parse(
                        command,
                        new SFMClientActionSource(actionContext)
                )
        ));
    }

    private SFMPaletteCandidateInspection candidateInspection(SFMPaletteCandidate suggestion) {
        Optional<ResourceLocation> actionId = suggestionActionId(suggestion);
        var action = actionId.map(id -> SFMClientActions.registry().get(id)).orElse(null);
        List<String> bindings = bindingsForSuggestion(suggestion).stream()
                .map(binding -> SFMKeyBindingDisplay.format(binding.sequence())
                        + (binding.enabled() ? "" : " (disabled)"))
                .toList();
        return SFMPaletteCandidateInspection.capture(
                suggestion,
                commandInput(),
                canonicalExecutableCommandForSuggestion(suggestion).orElse(null),
                actionId.map(ResourceLocation::toString).orElse(null),
                action == null ? null : action.title().getString(),
                action == null ? null : action.description().getString(),
                actionIcon(suggestion).map(icon -> icon.requestedItem().toString()).orElse(null),
                actionIcon(suggestion).map(icon -> icon.accessibleLabel()).orElse(null),
                bindings
        );
    }

{% endif %}
    private int panelWidth() {
{% if features.typed_command_palette %}
        return panelWidth(this.width);
    }

    static int panelWidth(int viewportWidth) {
        return Math.max(1, Math.min(460, viewportWidth - PANEL_MARGIN * 2));
{% else %}
        return Math.min(460, this.width - 24);
{% endif %}
    }

    private int panelLeft() {
        return (this.width - panelWidth()) / 2;
    }

    private int panelTop() {
        return Math.max(PANEL_MARGIN, this.height / 2 - panelHeight() / 2);
    }

    private int panelHeight() {
{% if features.typed_command_palette %}
        return PANEL_BASE_HEIGHT + choicePreviewHeight()
                + visibleSuggestionCount() * SUGGESTION_ROW_HEIGHT + consoleHeight();
{% else %}
        return PANEL_BASE_HEIGHT + visibleSuggestionCount() * SUGGESTION_ROW_HEIGHT + consoleHeight();
{% endif %}
    }

    private int consoleTop(int panelTop) {
{% if features.typed_command_palette %}
        return panelTop + shiftedOffset(CONSOLE_TOP_OFFSET)
                + visibleSuggestionCount() * SUGGESTION_ROW_HEIGHT;
{% else %}
        return panelTop + 72 + visibleSuggestionCount() * SUGGESTION_ROW_HEIGHT + 10;
{% endif %}
    }

    private int visibleSuggestionCount() {
        int availableRows = Math.max(
                1,
{% if features.typed_command_palette %}
                (this.height - PANEL_MARGIN * 2 - PANEL_BASE_HEIGHT - choicePreviewHeight()
                        - desiredConsoleHeight())
{% else %}
                (this.height - PANEL_MARGIN * 2 - PANEL_BASE_HEIGHT - desiredConsoleHeight())
{% endif %}
                        / SUGGESTION_ROW_HEIGHT
        );
        int suggestionCount = Math.min(MAX_SUGGESTIONS, this.suggestions.size());
        return Math.max(1, Math.min(availableRows, suggestionCount == 0 ? 1 : suggestionCount));
    }

    private int consoleHeight() {
        int availableHeight = this.height
                - PANEL_MARGIN * 2
                - PANEL_BASE_HEIGHT
{% if features.typed_command_palette %}
                - choicePreviewHeight()
{% endif %}
                - visibleSuggestionCount() * SUGGESTION_ROW_HEIGHT;
        return Math.max(EMPTY_CONSOLE_HEIGHT, Math.min(desiredConsoleHeight(), availableHeight));
    }

    private int desiredConsoleHeight() {
        return this.feedback.isEmpty() ? EMPTY_CONSOLE_HEIGHT : CONSOLE_HEIGHT;
    }

{% case minecraft_version %}
{% when "1.19.2" %}
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    @MCVersionDependentBehaviour
{% endcase %}
    private void layoutWidgets() {
{% if features.typed_command_palette %}
        if (this.input == null || this.executeButton == null || this.cancelButton == null
                || this.consoleWidget == null) {
{% else %}
        if (this.input == null || this.executeButton == null || this.consoleWidget == null) {
{% endif %}
            return;
        }
{% if features.typed_command_palette %}
{% else %}
        int left = panelLeft();
{% endif %}
        int top = panelTop();
{% if features.typed_command_palette %}
{% case minecraft_version %}
{% when "1.19.2" %}
        ControlsLayout controls = controlsLayoutForPalette(top);
        this.input.setX(controls.input().x());
        this.input.y = controls.input().y();
        this.executeButton.x = controls.execute().x();
        this.executeButton.y = controls.execute().y();
        this.cancelButton.x = controls.cancel().x();
        this.cancelButton.y = controls.cancel().y();
{% when "1.19.4" %}
        ControlsLayout controls = controlsLayoutForPalette(top);
        this.input.setX(controls.input().x());
        this.input.setY(controls.input().y());
        this.executeButton.setPosition(controls.execute().x(), controls.execute().y());
        this.cancelButton.setPosition(controls.cancel().x(), controls.cancel().y());
{% endcase %}
{% else %}
{% case minecraft_version %}
{% when "1.19.2" %}
        int width = panelWidth();
        int horizontalPadding = 10;
        int executeWidth = 68;
        this.input.setX(left + horizontalPadding);
        this.input.y = top + 30;
        this.executeButton.x = left + width - horizontalPadding - executeWidth;
        this.executeButton.y = top + 30;
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        int width = panelWidth();
        int horizontalPadding = 10;
        int executeWidth = 68;
        this.input.setX(left + horizontalPadding);
        this.input.setY(top + 30);
        this.executeButton.setPosition(left + width - horizontalPadding - executeWidth, top + 30);
{% endcase %}
{% endif %}
        this.consoleWidget.setBounds(
{% if features.typed_command_palette %}
                panelLeft() + HORIZONTAL_PADDING,
{% else %}
                left + horizontalPadding,
{% endif %}
                consoleTop(top),
{% if features.typed_command_palette %}
                Math.max(1, panelWidth() - HORIZONTAL_PADDING * 2),
{% else %}
                width - horizontalPadding * 2,
{% endif %}
                consoleHeight()
        );
{% if features.typed_command_palette %}
        suggestionViewport.configure(suggestions.size(), visibleSuggestionCount());
{% else %}
        ensureSelectedSuggestionVisible();
{% endif %}
    }

{% if features.typed_command_palette %}
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
{% else %}
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
{% endif %}
        );
    }

{% if features.typed_command_palette %}
    private ControlsLayout controlsLayoutForPalette(int panelTop) {
        ControlsLayout base = controlsLayout(this.width, panelTop);
        int shift = choicePreviewHeight();
        if (shift == 0) return base;
        return new ControlsLayout(
                base.input(),
                new ControlBounds(base.execute().x(), base.execute().y() + shift,
                        base.execute().width(), base.execute().height()),
                new ControlBounds(base.cancel().x(), base.cancel().y() + shift,
                        base.cancel().width(), base.cancel().height())
        );
    }

    private int choicePreviewHeight() {
        return choiceSession == null ? 0 : CANONICAL_PREVIEW_HEIGHT;
    }

    private int shiftedOffset(int base) {
        return base + choicePreviewHeight();
    }

    private Optional<String> canonicalPreviewCommand() {
        int selected = suggestionViewport.selectedRow();
        if (selected >= 0 && selected < suggestions.size()) {
            Optional<String> command = canonicalCommandForSuggestion(suggestions.get(selected));
            if (command.isPresent()) return command;
        }
        return choiceSession == null
                ? Optional.empty()
                : choiceSession.canonicalCommandForSurfaceCommand(commandInput());
    }

    private Optional<ControlBounds> canonicalPreviewBounds(int top) {
        if (choiceSession == null) return Optional.empty();
        ControlBounds inputBounds = controlsLayoutForPalette(top).input();
        return Optional.of(new ControlBounds(
                inputBounds.x(),
                top + BUTTON_ROW_TOP_OFFSET,
                inputBounds.width(),
                CANONICAL_PREVIEW_HEIGHT
        ));
    }

    private void renderCanonicalCommandPreview(
            PoseStack poseStack,
            int mouseX,
            int mouseY,
            int top,
            int background,
            int border,
            int textColour
    ) {
        if (choiceSession == null) return;
        ControlBounds bounds = canonicalPreviewBounds(top).orElseThrow();
        fill(poseStack, bounds.x(), bounds.y(), bounds.right(), bounds.y() + bounds.height(), background);
        fill(poseStack, bounds.x(), bounds.y(), bounds.right(), bounds.y() + 1, border);
        fill(poseStack, bounds.x(), bounds.y() + bounds.height() - 1,
                bounds.right(), bounds.y() + bounds.height(), border);
        Component complete = canonicalPreviewCommand()
                .map(command -> CANONICAL_COMMAND.getComponent(Component.literal(command)))
                .orElseGet(CANONICAL_COMMAND_PENDING::getComponent);
        String fitted = SFMTextSummary.fitLine(complete.getString(), Math.max(1, bounds.width() - 8), font::width);
        SFMFontUtils.draw(poseStack, font, fitted, bounds.x() + 4, bounds.y() + 6, textColour, false);
        if (mouseX >= bounds.x() && mouseX < bounds.right()
                && mouseY >= bounds.y() && mouseY < bounds.y() + bounds.height()) {
            renderTooltip(poseStack, complete, mouseX, mouseY);
        }
    }

    static Button createCancelButton(ControlBounds bounds, Runnable onPress) {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(onPress, "onPress");
        return new SFMButtonBuilder()
                .setPosition(bounds.x(), bounds.y())
                .setSize(bounds.width(), bounds.height())
                .setText(CANCEL)
                .setOnPress(button -> onPress.run())
                .build();
    }

    private Button createCancelButton(ControlBounds bounds, Runnable onPress, Component tooltip) {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(onPress, "onPress");
        return new SFMButtonBuilder()
                .setPosition(bounds.x(), bounds.y())
                .setSize(bounds.width(), bounds.height())
                .setText(CANCEL)
                .setOnPress(button -> onPress.run())
                .setTooltip(this, this.font, tooltip)
                .build();
    }

    private Component focusControlTooltip(String targetId) {
        List<SFMKeyBinding> bindings = SFMKeyBindingService.INSTANCE.bindingsForCommand(
                FOCUS_ACTION_ID,
                focusCommand(targetId)
        );
        if (bindings.isEmpty()) return FOCUS_CONTROL_TOOLTIP_UNBOUND.getComponent();
        String labels = bindings.stream()
                .map(binding -> SFMKeyBindingDisplay.format(binding.sequence()))
                .distinct()
                .collect(java.util.stream.Collectors.joining(", "));
        return FOCUS_CONTROL_TOOLTIP.getComponent(Component.literal(labels));
    }

    private void openCandidateActions(int suggestionIndex) {
        if (suggestionIndex < 0 || suggestionIndex >= suggestions.size()) return;
        SFMPaletteCandidateInspection inspection = candidateInspection(suggestions.get(suggestionIndex));
        long captureId = ++nextCandidateInspectionId;
        if (captureId <= 0) throw new IllegalStateException("Palette candidate inspection id space exhausted");
        CapturedCandidateInspection capture = new CapturedCandidateInspection(captureId, inspection);
        capturedCandidateInspection = capture;
        List<SFMPaletteCandidateInspection> set = suggestions.stream().map(this::candidateInspection).toList();
        long setCaptureId = ++nextCandidateInspectionId;
        CapturedCandidateSetInspection setCapture = new CapturedCandidateSetInspection(setCaptureId, set);
        capturedCandidateSetInspection = setCapture;
        SFMClientActionContext targetContext = SFMClientActionContext.create(
                this,
                () -> capturedCandidateInspection == capture
        );
        try {
            SFMCommandPaletteScreen.openChoices(
                    targetContext,
                    Component.literal("Candidate actions"),
                    java.util.stream.Stream.concat(
                            SFMPaletteCandidateCopyAction.choices(captureId, inspection).stream(),
                            SFMPaletteCandidateSetCopyAction.choices(setCaptureId, set).stream()
                    ).toList(),
                    () -> {
                        if (capturedCandidateInspection == capture) capturedCandidateInspection = null;
                        if (capturedCandidateSetInspection == setCapture) capturedCandidateSetInspection = null;
                    }
            );
        } catch (RuntimeException exception) {
            if (capturedCandidateInspection == capture) capturedCandidateInspection = null;
            if (capturedCandidateSetInspection == setCapture) capturedCandidateSetInspection = null;
            throw exception;
        }
    }

    @Override
    public Optional<SFMPaletteCandidateInspection> paletteCandidateInspection(long captureId) {
        CapturedCandidateInspection capture = capturedCandidateInspection;
        return capture != null && capture.id() == captureId
                ? Optional.of(capture.inspection())
                : Optional.empty();
    }

    @Override
    public Optional<List<SFMPaletteCandidateInspection>> paletteCandidateSetInspection(long captureId) {
        CapturedCandidateSetInspection capture = capturedCandidateSetInspection;
        return capture != null && capture.id() == captureId
                ? Optional.of(capture.inspections())
                : Optional.empty();
    }

    private void openFocusControlActions(String targetId, Component controlName) {
        SFMClientActionContext targetContext = SFMClientActionContext.create(
                this,
                () -> ACTIVE != null
        );
        SFMCommandPaletteScreen.openChoices(
                targetContext,
                CONTROL_ACTIONS.getComponent(controlName),
                List.of(
                        SFMActionChoice.invoke(
                                FOCUS_ACTION_ID,
                                targetId,
                                FOCUS_CONTROL.getComponent(controlName).getString()
                        ),
                        SFMActionChoice.invoke(
                                COPY_ACTION_ID,
                                FOCUS_ACTION_ID + " " + targetId,
                                COPY_FOCUS_ACTION.getComponent(controlName).getString()
                        )
                )
        );
    }

    private static String focusCommand(String targetId) {
        return "sfm action invoke " + FOCUS_ACTION_ID + " " + targetId;
    }

    @Override
    public List<String> focusTargetIds() {
        return List.of(COMMAND_INPUT_FOCUS_TARGET, EXECUTE_FOCUS_TARGET, CANCEL_FOCUS_TARGET);
    }

    @Override
    public boolean focusTarget(String targetId) {
        net.minecraft.client.gui.components.AbstractWidget target = switch (targetId) {
            case COMMAND_INPUT_FOCUS_TARGET -> input;
            case EXECUTE_FOCUS_TARGET -> executeButton;
            case CANCEL_FOCUS_TARGET -> cancelButton;
            default -> null;
        };
        if (target == null || !target.visible) return false;
        setFocused(target);
        target.setFocused(true);
        return true;
    }

{% if features.screen_diagnostics %}
    @Override
    public List<String> screenDiagnostics() {
        return List.of(
                "palette.title=" + title.getString(),
                "palette.pushed=" + pushed,
                "palette.choice-session=" + (choiceSession == null ? "none" : choiceSession.id()),
                "palette.input=" + (input == null ? "uninitialized" : input.getValue()),
                "palette.suggestions=" + suggestions.size(),
                "palette.selected-suggestion=" + suggestionViewport.selectedRow(),
                "palette.execute-active=" + (executeButton != null && executeButton.active),
                "palette.focus-targets=" + String.join(",", focusTargetIds()),
                "palette.origin=" + (actionContext.originatingHost() == null
                        ? "none"
                        : actionContext.originatingHost().getClass().getName())
        );
    }
{% endif %}

    private SFMVerticalListViewport.Bounds suggestionListBounds() {
        return new SFMVerticalListViewport.Bounds(
                panelLeft() + 6,
                panelTop() + shiftedOffset(SUGGESTION_TOP_OFFSET),
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

{% endif %}
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

{% if features.typed_command_palette %}
{% if features.document_history %}
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

{% endif %}
{% endif %}
    private void refreshSuggestions(String ignored) {
        this.insertedRequiredArgumentSeparator = false;
        String command = commandInput();
        this.error = "";
        this.input.setSuggestion(command.isEmpty() ? INPUT_PLACEHOLDER.getString() : "");
        long revision = ++this.suggestionRevision;
{% if features.typed_command_palette %}
        // A previous query's candidates must never remain interactive while a
        // new asynchronous Brigadier/fuzzy result is in flight.
        this.suggestions = List.of();
        suggestionViewport.configure(0, visibleSuggestionCount());
        suggestionViewport.select(SFMVerticalListViewport.NO_SELECTION);
        var tree = commandTree;
{% else %}
        var tree = SFMClientActions.commandTree();
{% endif %}
        ParseResults<SFMClientActionSource> parsed = tree.parse(
                command,
                new SFMClientActionSource(this.actionContext)
        );
        this.executeButton.active = isExecutable(parsed);
{% if features.typed_command_palette %}
        int cursor = Math.max(0, Math.min(command.length(), input.getCursorPosition()
                - (input.getValue().startsWith("/") ? 1 : 0)));
        tree.getPaletteCandidates(command, parsed, cursor).thenAccept(result -> Minecraft.getInstance().execute(() -> {
{% else %}
        tree.getPaletteSuggestions(command, parsed).thenAccept(result -> Minecraft.getInstance().execute(() -> {
{% endif %}
            if (ACTIVE != this || revision != this.suggestionRevision) return;
{% if features.typed_command_palette %}
            this.suggestions = result;
            this.appliedSuggestionRevision = revision;
            this.appliedSuggestionCommand = command;
            suggestionViewport.configure(this.suggestions.size(), visibleSuggestionCount());
            suggestionViewport.select(this.suggestions.isEmpty()
                    ? SFMVerticalListViewport.NO_SELECTION
                    : 0);
{% else %}
            this.suggestions = result.getList();
            this.selectedSuggestion = this.suggestions.isEmpty() ? -1 : 0;
            this.firstVisibleSuggestion = 0;
{% endif %}
            layoutWidgets();
        }));
    }

    private boolean isExecutable(ParseResults<SFMClientActionSource> parsed) {
        return SFMClientActionExecutor.isExecutable(parsed);
    }

{% if features.typed_command_palette %}
    private boolean currentInputIsExecutable() {
        return isExecutable(commandTree.parse(
                commandInput().stripLeading(),
                new SFMClientActionSource(actionContext)));
    }

    private boolean applySelectedSuggestion() {
        int selectedSuggestion = suggestionViewport.selectedRow();
        String rawCurrent = this.input.getValue();
{% else %}
    private void applySelectedSuggestion() {
        if (this.selectedSuggestion < 0 || this.selectedSuggestion >= this.suggestions.size()) return;
{% endif %}
        String current = commandInput();
{% if features.typed_command_palette %}
        OptionalInt suggestionToApply = selectedSuggestionToApply(
                suggestions,
                selectedSuggestion,
                current
{% else %}
        String suggestedValue = this.suggestions.get(this.selectedSuggestion).apply(current);
        String value = SFMClientCommandInsertion.prepare(
                suggestedValue,
                SFMClientActions.commandTree(),
                new SFMClientActionSource(actionContext)
{% endif %}
        );
{% if features.typed_command_palette %}
        if (suggestionToApply.isEmpty()) return false;
        if (suggestionToApply.getAsInt() != selectedSuggestion) {
            selectedSuggestion = suggestionToApply.getAsInt();
            suggestionViewport.select(selectedSuggestion);
        }
        SFMPaletteCandidate candidate = this.suggestions.get(selectedSuggestion);
        SFMCompletionApplication application = candidate.apply(current);
        if (choiceSession != null) {
            Optional<String> continuation = choiceSession.continuationForSurfaceCommand(application.afterValue());
            if (continuation.isPresent()) {
                // Close this exact choice surface, then continue with its captured origin and theme arguments.
                // No action has executed and no mutation is implied by selecting the incomplete candidate.
                onClose();
                var continuedContext = new SFMClientActionContext(actionContext.originatingHost(),
{% if features.workspace_panels %}
                        () -> isOriginStillActive(actionContext.originatingHost() instanceof Screen origin ? origin : null),
                        actionContext.originatingPanelId());
{% else %}
                        () -> isOriginStillActive(actionContext.originatingHost() instanceof Screen origin ? origin : null));
{% endif %}
{% if features.theme_preview_rules %}
                ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewCaptures.inherit(actionContext,continuedContext);
{% endif %}
                SFMCommandPaletteScreen.open(continuedContext, continuation.get() + " ");
                return true;
            }
        }
        application = paletteCoordinateApplication(rawCurrent, application);
        this.lastCompletionApplication = application;
{% if features.document_history %}
        setInputValueWithHistory(
                application.afterValue(),
                SFMDocumentHistoryContract.MutationKind.COMPLETION,
                Optional.of(application.replacementText()),
                "palette-completion-" + application.candidateKind().name().toLowerCase(java.util.Locale.ROOT)
        );
{% else %}
        this.input.setValue(application.afterValue());
{% endif %}
        this.insertedRequiredArgumentSeparator = !application.deliberateSeparator().isEmpty();
        this.input.setCursorPosition(Math.min(this.input.getValue().length(),
                application.replacementRange().getStart() + application.replacementText().length()
                        + application.deliberateSeparator().length()));
        this.input.setHighlightPos(this.input.getCursorPosition());
        refreshSuggestions(this.input.getValue());
        return true;
    }

    static OptionalInt selectedSuggestionToApply(
            List<SFMPaletteCandidate> candidates,
            int selectedIndex,
            String currentValue
    ) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(currentValue, "currentValue");
        if (selectedIndex < 0 || selectedIndex >= candidates.size()) return OptionalInt.empty();
        SFMPaletteCandidate selected = candidates.get(selectedIndex);
        if (!selected.activatable()) return OptionalInt.empty();
        if (!selected.apply(currentValue).afterValue().equals(currentValue)) {
            return OptionalInt.of(selectedIndex);
        }
        return SFMPaletteCandidate.progressingIndex(candidates, selectedIndex, currentValue);
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
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
        this.input.setValue(value);
        this.insertedRequiredArgumentSeparator = !value.equals(suggestedValue);
        this.input.moveCursorToEnd();
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        this.input.setValue(value);
        this.insertedRequiredArgumentSeparator = !value.equals(suggestedValue);
        this.input.moveCursorToEnd(false);
{% endcase %}
{% endif %}
    }

    private void executeInput() {
        String rawCommand = commandInput().stripLeading();
{% if features.typed_command_palette %}
        var source = new SFMClientActionSource(actionContext);
        var parsed = commandTree.parse(rawCommand, source);
{% endif %}
        String prepared = SFMClientCommandInsertion.prepare(
                rawCommand,
{% if features.typed_command_palette %}
                commandTree,
                source
{% else %}
                SFMClientActions.commandTree(),
                new SFMClientActionSource(actionContext)
{% endif %}
        );
        if (!prepared.equals(rawCommand)) {
{% if features.typed_command_palette %}
            if (SFMClientCommandInsertion.hasAvailableLiteralChildren(parsed)) {
                this.error = "Choose an available suggestion before providing the next argument";
                return;
            }
{% if features.document_history %}
            setInputValueWithHistory(
                    prepared,
                    SFMDocumentHistoryContract.MutationKind.COMPLETION,
                    Optional.of(prepared.substring(Math.min(prepared.length(), rawCommand.length()))),
                    "palette-required-argument-separator"
            );
{% else %}
            this.input.setValue(
                    prepared
            );
{% endif %}
{% else %}
            this.input.setValue(
                    prepared
            );
{% endif %}
            this.insertedRequiredArgumentSeparator = true;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
            this.input.moveCursorToEnd();
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            this.input.moveCursorToEnd(false);
{% endcase %}
            return;
        }
        if (SFMClientCommandInsertion.isAwaitingRequiredArgument(
                rawCommand,
{% if features.typed_command_palette %}
                commandTree,
{% else %}
                SFMClientActions.commandTree(),
{% endif %}
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

{% if features.typed_command_palette %}
    private int executeCommand(String command) throws CommandSyntaxException {
{% else %}
    private void executeCommand(String command) throws CommandSyntaxException {
{% endif %}
        this.feedback.clear();
{% if features.typed_command_palette %}
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
{% else %}
        SFMClientActionExecutor.execute(command, this.actionContext, this.feedback::add);
{% endif %}
        this.error = "";
{% if features.typed_command_palette %}
{% if features.command_history %}
        if (result > 0 && SFMCommandHistoryService.isRecordable(command)) {
            SFMCommandHistoryService.recordSuccessful(command);
        }
{% endif %}
        if (choiceSession != null && result > 0) {
            if (Minecraft.getInstance().screen == this) onClose();
            return result;
        }
        // Actions may close or replace this palette. Do not mutate a removed
        // palette after command execution transfers screen ownership.
        if (Minecraft.getInstance().screen == this) resetToDefaultQuery();
        return result;
{% else %}
        resetToDefaultQuery();
{% endif %}
    }

    /**
     * Supplies a command and executes it for the client puppet harness.
     *
     * <p>The harness uses the same Brigadier path as a user typing into the
     * palette; it does not invoke an action implementation directly.</p>
     */
    public void executeCommandForAutomation(String command) {
{% if features.typed_command_palette %}
{% if features.document_history %}
        setInputValueWithHistory(
                command,
                SFMDocumentHistoryContract.MutationKind.ACTION,
                Optional.of(command),
                "automation-execute-command"
        );
{% else %}
        this.input.setValue(
                command
        );
{% endif %}
        this.input.moveCursorToEnd();
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
        this.input.setValue(
                command
        );
        this.input.moveCursorToEnd();
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        this.input.setValue(
                command
        );
        this.input.moveCursorToEnd(false);
{% endcase %}
{% endif %}
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

{% if features.typed_command_palette %}
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

    /** Captured origin used when a palette action opens a persistent workspace. */
    public SFMClientActionContext originatingActionContext() {
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
        ControlBounds bounds = controlsLayoutForPalette(panelTop()).cancel();
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
        selectLastSuggestionForAction();
        if (suggestionViewport.selectedRow() != suggestions.size() - 1) {
            throw new IllegalStateException("The last-suggestion action did not select the final suggestion");
        }
        selectFirstSuggestionForAction();
        if (suggestionViewport.firstVisibleRow() != 0 || suggestionViewport.selectedRow() != 0) {
            throw new IllegalStateException("The first-suggestion action did not restore the first suggestion");
        }

        SFMVerticalListViewport.ScrollbarGeometry geometry = suggestionScrollbarGeometry();
        double trackX = geometry.track().x() + geometry.track().width() / 2.0d;
        double trackBottom = geometry.track().y() + geometry.track().height() - 1.0d;
        if (!mouseClicked(trackX, trackBottom, 0)
                || suggestionViewport.firstVisibleRow() == 0) {
            throw new IllegalStateException("Suggestion scrollbar track click did not advance the viewport");
        }
        selectFirstSuggestionForAction();
        geometry = suggestionScrollbarGeometry();
        double thumbY = geometry.thumb().y() + geometry.thumb().height() / 2.0d;
        if (!mouseClicked(trackX, thumbY, 0)
                || !mouseDragged(trackX, trackBottom, 0, 0, trackBottom - thumbY)
                || !mouseReleased(trackX, trackBottom, 0)
                || suggestionViewport.firstVisibleRow() != suggestionViewport.maxFirstVisibleRow()) {
            throw new IllegalStateException("Suggestion scrollbar thumb did not reach the final viewport");
        }
    }

{% endif %}
    /** Sets the real palette input for a visual puppet without bypassing its responder. */
    public void setInputForAutomation(String command) {
{% if features.typed_command_palette %}
{% if features.document_history %}
        setInputValueWithHistory(
                command,
                SFMDocumentHistoryContract.MutationKind.PASTE,
                Optional.of(command),
                "automation-set-input"
        );
{% else %}
        this.input.setValue(
                command
        );
{% endif %}
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

    /** Normalized command text currently shown by the automation-driven palette. */
    public String commandInputForAutomation() {
        return commandInput();
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
        keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
        this.input.setValue(
                command
        );
        this.input.moveCursorToEnd();
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        this.input.setValue(
                command
        );
        this.input.moveCursorToEnd(false);
{% endcase %}
{% endif %}
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
{% if features.typed_command_palette %}
        keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
{% else %}
        executeInput();
{% endif %}
        if (!this.input.getValue().equals(expected)) {
            throw new IllegalStateException("Expected palette input '" + expected + "' but found '"
                    + this.input.getValue() + "'");
        }
        if (this.executeButton.active) {
            throw new IllegalStateException("Incomplete palette input unexpectedly enabled Execute");
        }
    }

    private void resetToDefaultQuery() {
{% if features.typed_command_palette %}
{% if features.document_history %}
        setInputValueWithHistory(
                DEFAULT_QUERY,
                SFMDocumentHistoryContract.MutationKind.ACTION,
                Optional.of(DEFAULT_QUERY),
                "palette-reset-after-command"
        );
{% else %}
        this.input.setValue(
                DEFAULT_QUERY
        );
{% endif %}
        this.input.moveCursorToEnd();
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
        this.input.setValue(
                DEFAULT_QUERY
        );
        this.input.moveCursorToEnd();
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        this.input.setValue(
                DEFAULT_QUERY
        );
        this.input.moveCursorToEnd(false);
{% endcase %}
{% endif %}
        this.setFocused(this.input);
        this.input.setFocused(true);
    }

}
