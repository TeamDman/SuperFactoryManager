package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.action.SFMClientAction;
import ca.teamdman.sfm.client.action.SFMClientActionAvailability;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionRequirement;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.action.SFMRegistryDump;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenOverlayOpenContext;
import ca.teamdman.sfm.client.screen.widget.SFMButtonBuilder;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.eventbus.api.IEventBus;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

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

    @SFMLocalizationDatagen
    public static final LocalizationEntry DUMP_REGISTRIES_TITLE = new LocalizationEntry(
            "gui.sfm.client_action.dump_registries.title",
            "Dump registries"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry DUMP_REGISTRIES_DESCRIPTION = new LocalizationEntry(
            "gui.sfm.client_action.dump_registries.description",
            "Write known registry ids and summaries into the SFM instance directory"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry HELP_TITLE = new LocalizationEntry(
            "gui.sfm.client_action.help.title",
            "Command palette help"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry HELP_DESCRIPTION = new LocalizationEntry(
            "gui.sfm.client_action.help.description",
            "Open a short command palette help document"
    );

    private static final int PANEL = 0xF0202020;
    private static final int BORDER = 0xFF707070;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFB0B0B0;
    private static final int ERROR = 0xFFFF5555;
    private static final int MAX_SUGGESTIONS = 8;

    private static @Nullable SFMCommandPaletteScreen ACTIVE;

    private final SFMClientActionContext actionContext;
    private final boolean pushed;
    private final String initialQuery;

    @SuppressWarnings("NotNullFieldNotInitialized")
    private EditBox input;
    @SuppressWarnings("NotNullFieldNotInitialized")
    private Button executeButton;
    private List<Suggestion> suggestions = List.of();
    private final List<Component> feedback = new ArrayList<>();
    private int selectedSuggestion = -1;
    private String error = "";
    private long suggestionRevision;
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

    private static void open(
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
                return true;
            }
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = panelLeft();
        int top = panelTop();
        int width = panelWidth();
        if (mouseX >= left && mouseX <= left + width) {
            int suggestionTop = top + 72;
            int index = (int) ((mouseY - suggestionTop) / 18);
            if (index >= 0 && index < Math.min(MAX_SUGGESTIONS, suggestions.size())) {
                selectedSuggestion = index;
                applySelectedSuggestion();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
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
        if (suggestions.isEmpty()) {
            SFMFontUtils.draw(poseStack, this.font, EMPTY_RESULTS.getComponent(), left + 10, top + 72, MUTED, false);
        } else {
            for (int i = 0; i < Math.min(MAX_SUGGESTIONS, suggestions.size()); i++) {
                int y = top + 72 + i * 18;
                if (i == selectedSuggestion) {
                    fill(poseStack, left + 6, y - 2, right - 6, y + 14, 0xFF404040);
                }
                Suggestion suggestion = suggestions.get(i);
                SFMFontUtils.draw(poseStack, this.font, suggestion.getText(), left + 12, y, TEXT, false);
            }
        }
        if (!this.error.isEmpty()) {
            SFMFontUtils.draw(
                    poseStack,
                    this.font,
                    truncateToPanel(this.error),
                    left + 10,
                    bottom - 20,
                    ERROR,
                    false
            );
        }
        int feedbackY = bottom - 34;
        for (int i = Math.max(0, this.feedback.size() - 2); i < this.feedback.size(); i++) {
            SFMFontUtils.draw(poseStack, this.font, this.feedback.get(i), left + 10, feedbackY, MUTED, false);
            feedbackY -= 12;
        }
        super.render(poseStack, mouseX, mouseY, partialTick);
    }

    private int panelWidth() {
        return Math.min(460, this.width - 24);
    }

    private int panelLeft() {
        return (this.width - panelWidth()) / 2;
    }

    private int panelTop() {
        return Math.max(12, this.height / 2 - panelHeight() / 2);
    }

    private int panelHeight() {
        return 96 + MAX_SUGGESTIONS * 18;
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
        this.feedback.clear();
        try {
            SFMClientActions.commandTree().execute(
                    command,
                    new SFMClientActionSource(this.actionContext, this.feedback::add)
            );
            this.error = "";
            resetToDefaultQuery();
        } catch (CommandSyntaxException exception) {
            SFM.LOGGER.warn("Command palette command could not be executed: " + command, exception);
            this.error = EXECUTION_FAILED.getComponent(exception.getMessage()).getString();
        } catch (RuntimeException exception) {
            SFM.LOGGER.error("Command palette action failed: " + command, exception);
            this.error = EXECUTION_FAILED.getComponent(exception.getMessage()).getString();
        }
    }

    private void resetToDefaultQuery() {
        this.input.setValue(DEFAULT_QUERY);
        this.input.moveCursorToEnd();
        this.setFocused(this.input);
        this.input.setFocused(true);
    }

    public static final class Actions {
        private static final SFMDeferredRegister<SFMClientAction<?>> REGISTERER =
                SFMClientActions.createContributor(SFM.MOD_ID);

        public static final SFMRegistryObject<SFMClientAction<?>, OpenAction> OPEN = REGISTERER.register(
                "palette/open",
                OpenAction::new
        );

        public static final SFMRegistryObject<SFMClientAction<?>, DumpRegistriesAction> DUMP_REGISTRIES = REGISTERER.register(
                "dump_registries",
                DumpRegistriesAction::new
        );

        public static final SFMRegistryObject<SFMClientAction<?>, HelpAction> HELP = REGISTERER.register(
                "help",
                HelpAction::new
        );

        private Actions() {
        }

        public static void register(IEventBus bus) {
            REGISTERER.register(bus);
        }
    }

    public static final class OpenAction implements SFMClientAction<SFMClientActionContext> {
        @Override
        public Component title() {
            return TITLE.getComponent();
        }

        @Override
        public Component description() {
            return INPUT_PLACEHOLDER.getComponent();
        }

        @Override
        public SFMClientActionRequirement<SFMClientActionContext> requirement() {
            return SFMClientActionAvailability::available;
        }

        @Override
        public void configureCommandNode(
                com.mojang.brigadier.builder.LiteralArgumentBuilder<SFMClientActionSource> node
        ) {
            node.executes(this::invoke);
            node.then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                    "initial_query",
                    StringArgumentType.greedyString()
            ).executes(this::invoke));
        }

        @Override
        public int execute(
                SFMClientActionContext target,
                CommandContext<SFMClientActionSource> context
        ) {
            String initialQuery;
            try {
                initialQuery = StringArgumentType.getString(context, "initial_query");
            } catch (IllegalArgumentException ignored) {
                initialQuery = "";
            }
            open(target, initialQuery);
            return 1;
        }
    }

    public static final class DumpRegistriesAction implements SFMClientAction<SFMClientActionContext> {
        @Override
        public Component title() {
            return DUMP_REGISTRIES_TITLE.getComponent();
        }

        @Override
        public Component description() {
            return DUMP_REGISTRIES_DESCRIPTION.getComponent();
        }

        @Override
        public SFMClientActionRequirement<SFMClientActionContext> requirement() {
            return SFMClientActionAvailability::available;
        }

        @Override
        public int execute(
                SFMClientActionContext target,
                CommandContext<SFMClientActionSource> context
        ) {
            SFMRegistryDump.Result result = SFMRegistryDump.write(Minecraft.getInstance());
            context.getSource().sendFeedback(Component.literal(
                    "Registry dump written to " + result.directory()
                            + " (" + result.registryCount() + " registries, "
                            + result.unavailableCount() + " unavailable)"
            ));
            return 1;
        }
    }

    public static final class HelpAction implements SFMClientAction<SFMClientActionContext> {
        private static final String HELP_TEXT = """
                SFM Command Palette

                Open this palette with Ctrl+K from any SFM client screen.

                Type a local command, use the completion list, and press Enter to run it.
                Commands run locally on this client; they are not sent to a server.

                Available command groups include:
                  sfm action list [available|all]
                  sfm action help <action-id>
                  sfm action invoke <action-id>

                Press Escape to return to the screen that opened the palette.
                """.stripTrailing();

        @Override
        public Component title() {
            return HELP_TITLE.getComponent();
        }

        @Override
        public Component description() {
            return HELP_DESCRIPTION.getComponent();
        }

        @Override
        public SFMClientActionRequirement<SFMClientActionContext> requirement() {
            return SFMClientActionAvailability::available;
        }

        @Override
        public int execute(
                SFMClientActionContext target,
                CommandContext<SFMClientActionSource> context
        ) {
            SFMScreenChangeHelpers.showProgramEditScreen(new SFMTextEditScreenOverlayOpenContext(
                    HELP_TEXT,
                    LabelPositionHolder.empty(),
                    ignored -> {
                    }
            ));
            return 1;
        }
    }
}
