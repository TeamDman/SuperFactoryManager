package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.ProgramTokenContextActions;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.client.screen.*;
import ca.teamdman.sfm.client.screen.widget.PickList;
import ca.teamdman.sfm.client.screen.widget.PickListItem;
import ca.teamdman.sfm.client.screen.widget.SFMButtonBuilder;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditScreenOpenContext;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.editor_documents %}
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveResult;
{% endif %}
{% else %}
{% endcase %}
import ca.teamdman.sfm.client.text_styling.ProgramSyntaxHighlightingHelper;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMComponentUtils;
import ca.teamdman.sfm.common.util.SFMDisplayUtils;
import ca.teamdman.sfml.ast.Program;
import ca.teamdman.sfml.intellisense.IntellisenseAction;
import ca.teamdman.sfml.intellisense.IntellisenseContext;
import ca.teamdman.sfml.intellisense.SFMLIntellisense;
import ca.teamdman.sfml.manipulation.ManipulationResult;
import ca.teamdman.sfml.manipulation.ProgramStringManipulationUtils;
import ca.teamdman.sfml.program_builder.ProgramBuildResult;
import ca.teamdman.sfml.program_builder.ProgramBuilder;
{% case minecraft_version %}
{% when "1.19.2" %}
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.math.Matrix4f;
import net.minecraft.client.Minecraft;
{% when "1.19.4" %}
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.Minecraft;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.Minecraft;
{% when "1.21", "1.21.1" %}
import net.minecraft.client.Minecraft;
{% else %}
{% endcase %}
import net.minecraft.client.gui.Font;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import net.minecraft.client.gui.GuiComponent;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.client.gui.GuiGraphics;
{% else %}
import net.minecraft.client.gui.GuiGraphicsExtractor;
{% endcase %}
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField.StringView;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.screens.Screen;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.client.renderer.MultiBufferSource;
{% else %}
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
{% endcase %}
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
{% case minecraft_version %}
{% when "1.19.2", "26.1.2" %}
{% else %}
import org.joml.Matrix4f;
{% endcase %}
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.editor_documents %}
import java.util.Optional;
{% endif %}
{% else %}
{% endcase %}
import java.util.stream.Collectors;

@SuppressWarnings("NotNullFieldNotInitialized")
public class SFMTextEditScreenV1 extends Screen implements ISFMTextEditScreen {
    @SFMLocalizationDatagen
    public static final LocalizationEntry TEXT_EDIT_SCREEN_V1_TITLE = new LocalizationEntry(
            "gui.sfm.text_editor.v1.title",
            "Text Editor"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry INTELLISENSE_PICK_LIST_GUI_TITLE = new LocalizationEntry(
            "gui.sfm.title.intellisense_pick_list",
            "Intellisense Pick List"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry PROGRAM_EDIT_SCREEN_DONE_BUTTON_TOOLTIP = new LocalizationEntry(
            "gui.sfm.text_editor.done_button.tooltip",
            "Shift+Enter to submit"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry PROGRAM_EDIT_SCREEN_CONFIG_BUTTON_TOOLTIP = new LocalizationEntry(
            "gui.sfm.text_editor.config_button.tooltip",
            "Open editor config"
    );

    private final ISFMTextEditScreenOpenContext openContext;

    protected MyMultiLineEditBox textarea;

    protected String lastProgram = "";

    protected List<MutableComponent> content = new ArrayList<>();

    protected PickList<IntellisenseAction> suggestedActions;

    private boolean scrolledOnFirstInit = false;

    private boolean suppressNextCharTypedForIntellisenseAccept = false;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.editor_documents %}
    private Optional<Component> saveDiagnostic = Optional.empty();
{% endif %}
{% if features.editor_async_save %}
    private final ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveSession asyncSave =
            new ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveSession();
{% endif %}
{% else %}
{% endcase %}

    public SFMTextEditScreenV1(
            ISFMTextEditScreenOpenContext openContext
    ) {

        super(TEXT_EDIT_SCREEN_V1_TITLE.getComponent());
        this.openContext = openContext;
    }

    public void scrollToTop() {

        this.textarea.scrollToTop();
    }

    @Override
    public boolean isPauseScreen() {

        return false;
    }

    /**
     * The user has indicated to save by hitting Shift+Enter or by pressing the Done button
     */
    public void saveAndClose() {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.editor_async_save %}
        if (openContext.asynchronousSave()) {
            saveDiagnostic = Optional.of(ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveSession.SAVING.getComponent());
            boolean submitted = asyncSave.submit(textarea.getValue(), true, openContext::saveDocumentAsync,
                    work -> Minecraft.getInstance().execute(work), completion -> {
                        if (!openContext.saveHostIsCurrent()) return;
                        saveDiagnostic = completion.result().diagnostic();
                        if (completion.result().saved()) {
                            openContext.documentSaved(completion.submittedText());
                            if (completion.mayClose(textarea.getValue())
                                    && !openContext.detachSaveAndCloseAfterSubmission()) {
                                openContext.finishAsyncSaveClose();
                            }
                            else if (!completion.submittedText().equals(textarea.getValue())) {
                                saveDiagnostic = Optional.of(ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveSession
                                        .SAVED_NEWER_EDITS.getComponent());
                            }
                        }
                    });
            if (submitted && openContext.detachSaveAndCloseAfterSubmission()) {
                openContext.finishAsyncSaveClose();
            }
            return;
        }
{% endif %}
{% if features.editor_documents %}
        SFMTextDocumentSaveResult result = openContext.trySaveAndClose(textarea.getValue());
        saveDiagnostic = result.diagnostic();
        SFM.LOGGER.info("SFM_TEXT_EDITOR_SAVE_COMPLETED editor=sfm:v1 saved={} diagnostic={}",
                result.saved(), result.diagnostic().map(Component::getString).orElse("none"));
{% else %}

        openContext.onSaveAndClose(textarea.getValue());
{% endif %}
{% else %}

        openContext.onSaveAndClose(textarea.getValue());
{% endcase %}
    }

    /**
     * The user has tried to close the GUI without saving by hitting the Esc key
     */
    @Override
    public void onClose() {

        openContext.onTryClose(textarea.getValue(), SFMScreenChangeHelpers::popScreen);
    }

    @Override
    public ISFMTextEditScreenOpenContext openContext() {

        return openContext;
    }

    @Override
    public void onPreferenceChanged() {

        textarea.rebuildIntellisense();
    }

    @Override
    public boolean keyReleased(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            int pKeyCode,
            int pScanCode,
            int pModifiers
{% else %}
            KeyEvent event
{% endcase %}
    ) {

        boolean handled = false;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        if (pKeyCode == GLFW.GLFW_KEY_LEFT_CONTROL || pKeyCode == GLFW.GLFW_KEY_RIGHT_CONTROL) {
{% else %}
        if (event.hasControlDown()) {
{% endcase %}
            // if control released => update syntax highlighting
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            textarea.rebuild(Screen.hasControlDown());
{% else %}
            textarea.rebuild(event.hasControlDown());
{% endcase %}
            handled = true;
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        if (suppressNextCharTypedForIntellisenseAccept && isIntellisenseAcceptKey(pKeyCode, pScanCode)) {
{% else %}
        if (suppressNextCharTypedForIntellisenseAccept && isIntellisenseAcceptKey(event)) {
{% endcase %}
            suppressNextCharTypedForIntellisenseAccept = false;
            handled = true;
        }
        return handled;
    }

    @Override
    public boolean charTyped(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            char pCodePoint,
            int pModifiers
{% else %}
            CharacterEvent event
{% endcase %}
    ) {

        if (suppressNextCharTypedForIntellisenseAccept) {
            suppressNextCharTypedForIntellisenseAccept = false;
            return true;
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        if (Screen.hasControlDown() && pCodePoint == ' ') {
{% else %}
        if (SFMWidgetUtils.hasCtrlDown() && event.codepoint() == ' ') {
{% endcase %}
            return true;
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        return super.charTyped(pCodePoint, pModifiers);
{% else %}
        return super.charTyped(event);
{% endcase %}
    }

    @Override
    public boolean keyPressed(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            int pKeyCode,
            int pScanCode,
            int pModifiers
{% else %}
            KeyEvent event
{% endcase %}
    ) {
        // TODO: add separate keybindings for
        // context action - hold to arm
        // context action - execute
        // indent - increase
        // indent - decrease
        // save and close - hold to arm
        // save and close - execute
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.editor_v1_event_modifiers %}
        if (isSaveAndCloseShortcut(pKeyCode, pModifiers)) {
{% else %}
        if ((pKeyCode == GLFW.GLFW_KEY_ENTER || pKeyCode == GLFW.GLFW_KEY_KP_ENTER) && Screen.hasShiftDown()) {
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        if ((pKeyCode == GLFW.GLFW_KEY_ENTER || pKeyCode == GLFW.GLFW_KEY_KP_ENTER) && Screen.hasShiftDown()) {
{% else %}
        if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) && event.hasShiftDown()) {
{% endcase %}
            saveAndClose();
            return true;
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        if (pKeyCode == GLFW.GLFW_KEY_TAB) {
{% else %}
        if (event.key() == GLFW.GLFW_KEY_TAB) {
{% endcase %}
            // if tab pressed with no selection and not holding shift => insert 4 spaces
            // if tab pressed with no selection and holding shift => de-indent current line
            // if tab pressed with selection and not holding shift => de-indent lines containing selection 4 spaces
            // if tab pressed with selection and holding shift => indent lines containing selection 4 spaces
            String content = textarea.getValue();
            int cursor = textarea.getCursorPosition();
            int selectionCursor = textarea.getSelectionCursorPosition();
            double scrollAmount = textarea.getScrollAmount();
            ManipulationResult result;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            if (Screen.hasShiftDown()) { // de-indent
{% else %}
            if (event.hasShiftDown()) { // de-indent
{% endcase %}
                result = ProgramStringManipulationUtils.deindent(content, cursor, selectionCursor);
            } else { // indent
                result = ProgramStringManipulationUtils.indent(content, cursor, selectionCursor);
            }
            textarea.setValue(result.content());
            textarea.setCursorPosition(result.cursorPosition());
            textarea.setSelectionCursorPosition(result.selectionCursorPosition());
            textarea.setScrollAmount(scrollAmount);
            return true;
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        if (isIntellisenseAcceptKey(pKeyCode, pScanCode) && acceptSelectedIntellisenseAction()) {
{% else %}
        if (isIntellisenseAcceptKey(event) && acceptSelectedIntellisenseAction()) {
{% endcase %}
            return true;
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        if (pKeyCode == GLFW.GLFW_KEY_LEFT_CONTROL || pKeyCode == GLFW.GLFW_KEY_RIGHT_CONTROL) {
{% else %}
        if (event.key() == GLFW.GLFW_KEY_LEFT_CONTROL || event.key() == GLFW.GLFW_KEY_RIGHT_CONTROL) {
{% endcase %}
            // if control pressed => update syntax highlighting
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            textarea.rebuild(Screen.hasControlDown());
{% else %}
            textarea.rebuild(event.hasControlDown());
{% endcase %}
            return true;
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        if (pKeyCode == GLFW.GLFW_KEY_SLASH && Screen.hasControlDown()) {
{% else %}
        if (event.key() == GLFW.GLFW_KEY_SLASH && event.hasControlDown()) {
{% endcase %}
            // toggle line comments for selected lines
            String content = textarea.getValue();
            int cursor = textarea.getCursorPosition();
            int selectionCursor = textarea.getSelectionCursorPosition();
            ManipulationResult result = ProgramStringManipulationUtils.toggleComments(content, cursor, selectionCursor);
            textarea.setValue(result.content());
            textarea.setCursorPosition(result.cursorPosition());
            textarea.setSelectionCursorPosition(result.selectionCursorPosition());
            return true;
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        if (pKeyCode == GLFW.GLFW_KEY_SPACE && Screen.hasControlDown()) {
{% else %}
        if (event.key() == GLFW.GLFW_KEY_SPACE && event.hasControlDown()) {
{% endcase %}
            ProgramTokenContextActions.getContextAction(
                            textarea.getValue(),
                            textarea.getCursorPosition()
                    )
                    .ifPresent(Runnable::run);

            // disable the underline since it doesn't refresh when the context action closes
            textarea.rebuild(false);
            return true;
        }
        if (
                (
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                        pKeyCode == GLFW.GLFW_KEY_UP
                        || pKeyCode == GLFW.GLFW_KEY_DOWN
{% else %}
                        event.key() == GLFW.GLFW_KEY_UP
                        || event.key() == GLFW.GLFW_KEY_DOWN
{% endcase %}
                )
                && !suggestedActions.getItems().isEmpty()
        ) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            if (pKeyCode == GLFW.GLFW_KEY_UP) {
{% else %}
            if (event.key() == GLFW.GLFW_KEY_UP) {
{% endcase %}
                suggestedActions.selectPreviousWrapping();
            } else {
                suggestedActions.selectNextWrapping();
            }
            return true;
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        if (pKeyCode == GLFW.GLFW_KEY_ESCAPE && !suggestedActions.isEmpty()) {
{% else %}
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && !suggestedActions.isEmpty()) {
{% endcase %}
            suggestedActions.clear();
            return true;
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        return super.keyPressed(pKeyCode, pScanCode, pModifiers);
{% else %}
        return super.keyPressed(event);
{% endcase %}
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.editor_v1_event_modifiers %}
    static boolean isSaveAndCloseShortcut(int keyCode, int modifiers) {
        // Use the event's state, including virtual inputs, not an OS key poll.
        return (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
    }

{% endif %}
{% else %}
{% endcase %}
    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.editor_async_save %}
    public void onDocumentHostClosed() {
        asyncSave.detach();
    }

    @Override
{% endif %}
{% else %}
{% endcase %}
    public void resize(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            Minecraft mc,
{% else %}
{% endcase %}
            int x,
            int y
    ) {

        var prev = this.textarea.getValue();
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        init(mc, x, y);
        super.resize(mc, x, y);
{% else %}
        init(x, y);
        super.resize(x, y);
{% endcase %}
        this.textarea.setValue(prev);
    }

    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public void render(
            PoseStack poseStack,
            int mx,
            int my,
            float partialTicks
    ) {
{% when "1.20", "1.20.1" %}
    public void render(
            GuiGraphics graphics,
            int mx,
            int my,
            float partialTicks
    ) {
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public void render(
            GuiGraphics graphics,
            int mx,
            int my,
            float partialTicks
    ) {
{% else %}
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
            int mx,
            int my,
            float partialTicks
    ) {
{% endcase %}

        // render background
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        this.renderBackground(poseStack);
{% when "1.20", "1.20.1" %}
        this.renderBackground(graphics);
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        this.renderTransparentBackground(graphics);
{% else %}
        this.extractTransparentBackground(graphics);
{% endcase %}

        // render widgets
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        super.render(poseStack, mx, my, partialTicks);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        super.render(graphics, mx, my, partialTicks);
{% else %}
        super.extractRenderState(graphics, mx, my, partialTicks);
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.editor_documents %}
        if (saveDiagnostic.isPresent()) {
            String message = saveDiagnostic.orElseThrow().getString();
            String rendered = font.plainSubstrByWidth(message, Math.max(0, width - 8));
            SFMFontUtils.draw(
                    poseStack,
                    font,
                    rendered,
                    4,
                    Math.max(1, height - 34),
                    0xFFFF7777,
                    true
            );
        }

{% endif %}
{% else %}
{% endcase %}
        // render tooltips
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.editor_v1_widget_focus %}
        // A panel-hosted editor is not Minecraft.screen: the multiplexer is.
        // Do not clear widget keyboard focus while drawing tooltips. Doing so
        // accepts one character after a click, then drops all later typing on
        // the next rendered frame. Tooltips already require a hovered widget.
{% else %}
        SFMWidgetUtils.hideTooltipsWhenNotFocused(this, this.renderables);
{% endif %}
        SFMWidgetUtils.renderChildTooltips(poseStack, mx, my, this.renderables);
{% else %}
        SFMWidgetUtils.hideTooltipsWhenNotFocused(this, this.renderables);
        SFMWidgetUtils.renderChildTooltips(graphics.pose(), mx, my, this.renderables);
{% endcase %}
    }

    @Override
    public void tick() {

        this.textarea.tick();
    }

    private boolean isIntellisenseAcceptKey(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            int pKeyCode,
            int pScanCode
{% else %}
            KeyEvent event
{% endcase %}
    ) {

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        return SFMKeyMappings.TEXT_EDITOR_ACCEPT_INTELLISENSE_KEY.get().matches(pKeyCode, pScanCode);
{% else %}
        return SFMKeyMappings.TEXT_EDITOR_ACCEPT_INTELLISENSE_KEY.get().matches(event);
{% endcase %}
    }

    private boolean acceptSelectedIntellisenseAction() {

        if (suggestedActions.isEmpty()) {
            return false;
        }
        IntellisenseAction action = suggestedActions.getSelected();
        assert action != null;

        ManipulationResult result = action.perform(
                new IntellisenseContext(
                        new ProgramBuilder(textarea.getValue()).build(),
                        textarea.getCursorPosition(),
                        textarea.getSelectionCursorPosition(),
                        openContext.labelPositionHolder(),
                        SFMConfig.CLIENT_TEXT_EDITOR_CONFIG.intellisenseLevel.get()
                )
        );
        double scrollAmount = textarea.getScrollAmount();
        textarea.setValue(result.content());
        textarea.setSelectionCursorPosition(result.selectionCursorPosition());
        textarea.setCursorPosition(result.cursorPosition());
        textarea.setScrollAmount(scrollAmount);
        suppressNextCharTypedForIntellisenseAccept = true;
        return true;
    }

    @Override
    protected void init() {

        super.init();
        SFMScreenRenderUtils.enableKeyRepeating();

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.editor_v1_adaptive_layout %}
        EditorLayout layout = editorLayout(this.width, this.height, this.font.lineHeight);

{% endif %}
{% else %}
{% endcase %}
        this.textarea = this.addRenderableWidget(new MyMultiLineEditBox(
                SFMTextEditScreenV1.this.font,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.editor_v1_adaptive_layout %}
                layout.textareaX(),
                layout.textareaY(),
                layout.textareaWidth(),
                layout.textareaHeight(),
{% else %}
                SFMTextEditScreenV1.this.width / 2 - 200,
                SFMTextEditScreenV1.this.height / 2 - 110,
                400,
                200,
{% endif %}
{% else %}
                SFMTextEditScreenV1.this.width / 2 - 200,
                SFMTextEditScreenV1.this.height / 2 - 110,
                400,
                200,
{% endcase %}
                Component.literal(""),
                Component.literal("")
        ));

        this.suggestedActions = this.addRenderableWidget(new PickList<>(
                this.font,
                0,
                0,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.editor_v1_adaptive_layout %}
                layout.suggestionWidth(),
                layout.suggestionHeight(),
{% else %}
                180,
                this.font.lineHeight * 6,
{% endif %}
{% else %}
                180,
                this.font.lineHeight * 6,
{% endcase %}
                INTELLISENSE_PICK_LIST_GUI_TITLE.getComponent(),
                new ArrayList<>()
        ));

        this.addRenderableWidget(
                new SFMButtonBuilder()
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.editor_v1_adaptive_layout %}
                        .setPosition(layout.configX(), layout.footerY())
{% else %}
                        .setPosition(this.width / 2 - 200, this.height / 2 - 100 + 195)
{% endif %}
{% else %}
                        .setPosition(this.width / 2 - 200, this.height / 2 - 100 + 195)
{% endcase %}
                        .setSize(16, 20)
                        .setText(Component.literal("#"))
                        .setOnPress((button) -> {
                            int cursorPos = textarea.getCursorPosition();
                            int selectionCursorPos = textarea.getSelectionCursorPosition();
                            SFMScreenChangeHelpers.setOrPushScreen(
                                    new SFMTextEditorConfigScreen(
                                            this,
                                            SFMConfig.CLIENT_TEXT_EDITOR_CONFIG,
                                            () -> {
                                                this.setInitialFocus(textarea);
                                                textarea.setCursorPosition(cursorPos);
                                                textarea.setSelectionCursorPosition(selectionCursorPos);
                                            }
                                    )
                            );
                        })
                        .setTooltip(this, font, PROGRAM_EDIT_SCREEN_CONFIG_BUTTON_TOOLTIP)
                        .build()
        );
        this.addRenderableWidget(
                new SFMButtonBuilder()
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.editor_v1_adaptive_layout %}
                        .setPosition(layout.doneX(), layout.footerY())
                        .setSize(layout.doneWidth(), 20)
{% else %}
                        .setPosition(
                                this.width / 2 - 2 - 150,
                                this.height / 2 - 100 + 195
                        )
                        .setSize(200, 20)
{% endif %}
{% else %}
                        .setPosition(
                                this.width / 2 - 2 - 150,
                                this.height / 2 - 100 + 195
                        )
                        .setSize(200, 20)
{% endcase %}
                        .setText(CommonComponents.GUI_DONE)
                        .setOnPress((button) -> this.saveAndClose())
                        .setTooltip(this, font, PROGRAM_EDIT_SCREEN_DONE_BUTTON_TOOLTIP)
                        .build()
        );
        this.addRenderableWidget(
                new SFMButtonBuilder()
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.editor_v1_adaptive_layout %}
                        .setPosition(layout.cancelX(), layout.footerY())
                        .setSize(layout.cancelWidth(), 20)
{% else %}
                        .setPosition(
                                this.width / 2 - 2 + 100,
                                this.height / 2 - 100 + 195
                        )
                        .setSize(100, 20)
{% endif %}
{% else %}
                        .setPosition(
                                this.width / 2 - 2 + 100,
                                this.height / 2 - 100 + 195
                        )
                        .setSize(100, 20)
{% endcase %}
                        .setText(CommonComponents.GUI_CANCEL)
                        .setOnPress((button) -> this.onClose())
                        .build()
        );

        textarea.setValue(openContext.initialValue());

        // Scroll to top on first init to match previous behavior without needing external calls
        if (!scrolledOnFirstInit) {
            scrollToTop();
            scrolledOnFirstInit = true;
        }

        this.setInitialFocus(textarea);
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.editor_v1_adaptive_layout %}
    static EditorLayout editorLayout(int width, int height, int lineHeight) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        int margin = Math.min(4, Math.max(0, (safeWidth - 1) / 2));
        int footerY = Math.max(0, Math.min(safeHeight - 20, safeHeight / 2 + 95));
        int textareaY = Math.max(0, Math.min(safeHeight / 2 - 110, Math.max(0, footerY - 1)));
        int textareaWidth = Math.max(1, Math.min(400, safeWidth - margin * 2));
        int textareaX = Math.max(0, (safeWidth - textareaWidth) / 2);
        int textareaHeight = Math.max(1, Math.min(200, footerY - textareaY - 5));
        int suggestionWidth = Math.max(1, Math.min(180, safeWidth));
        int suggestionHeight = Math.max(1, Math.min(Math.max(1, lineHeight) * 6, safeHeight));

        if (safeWidth >= 408) {
            return new EditorLayout(
                    textareaX,
                    textareaY,
                    textareaWidth,
                    textareaHeight,
                    suggestionWidth,
                    suggestionHeight,
                    safeWidth / 2 - 200,
                    safeWidth / 2 - 152,
                    200,
                    safeWidth / 2 + 98,
                    100,
                    footerY
            );
        }

        int configX = margin;
        int doneX = Math.min(safeWidth, configX + 18);
        int available = Math.max(2, safeWidth - margin - doneX - 2);
        int doneWidth = Math.max(1, available / 2);
        int cancelX = Math.min(safeWidth, doneX + doneWidth + 2);
        int cancelWidth = Math.max(1, safeWidth - margin - cancelX);
        return new EditorLayout(
                textareaX,
                textareaY,
                textareaWidth,
                textareaHeight,
                suggestionWidth,
                suggestionHeight,
                configX,
                doneX,
                doneWidth,
                cancelX,
                cancelWidth,
                footerY
        );
    }

    record EditorLayout(
            int textareaX,
            int textareaY,
            int textareaWidth,
            int textareaHeight,
            int suggestionWidth,
            int suggestionHeight,
            int configX,
            int doneX,
            int doneWidth,
            int cancelX,
            int cancelWidth,
            int footerY
    ) {
    }

{% endif %}
{% else %}
{% endcase %}
    // TODO: enable scrolling without focus; respond to wheel events
    protected class MyMultiLineEditBox extends MultiLineEditBox {
        // Precomputed line start offsets for fast mapping; kept in sync in rebuild()
        private final List<Integer> displayedLineStartOffsets = new ArrayList<>();

        // Cache to avoid reparsing on cursor-only moves
        private @Nullable ProgramBuildResult cachedBuildResult;

        private String cachedBuildProgram = "";

        private boolean scrollbarDragActive;

        /// Used to debounce scrolling when click-dragging to select text.
        private boolean scrollingEnabled = true;

        private int cursorBlinkTick = 0;

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        public MyMultiLineEditBox(
{% else %}
        protected MyMultiLineEditBox(
{% endcase %}
                Font pFont,
                int pX,
                int pY,
                int pWidth,
                int pHeight,
                Component pPlaceholder,
                Component pMessage
        ) {

            super(
                    pFont,
                    pX,
                    pY,
                    pWidth,
                    pHeight,
                    pPlaceholder,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                    pMessage
{% else %}
                    pMessage, -2039584, true, -3092272, true, true
{% endcase %}
            );
            this.textField.setValueListener(this::onValueOrCursorChanged);
            this.textField.setCursorListener(() -> this.onValueOrCursorChanged(this.textField.value()));
            this.rebuild(false);
        }

{% case minecraft_version %}
{% when "1.19.2" %}
{% if features.editor_v1_panel_clipping %}
        /**
         * Vanilla's implementation uses screen-global scissor coordinates and
         * disables any parent scissor.  This editor can live in a transformed
         * workspace panel, so clip through the shared nested stack instead.
         */
        @Override
        @MCVersionDependentBehaviour
        public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
            if (!this.visible) return;
            int border = this.isFocused() ? -1 : -6250336;
            fill(poseStack, this.x, this.y, this.x + this.width, this.y + this.height, border);
            fill(
                    poseStack,
                    this.x + 1,
                    this.y + 1,
                    this.x + this.width - 1,
                    this.y + this.height - 1,
                    -16777216
            );
            SFMScissorStack.pushGui(
                    poseStack,
                    this.x + 1,
                    this.y + 1,
                    this.x + this.width - 1,
                    this.y + this.height - 1
            );
            poseStack.pushPose();
            try {
                poseStack.translate(0.0D, -this.scrollAmount(), 0.0D);
                this.renderContents(poseStack, mouseX, mouseY, partialTick);
            } finally {
                poseStack.popPose();
                SFMScissorStack.pop();
            }
            renderPanelAwareScrollbar(poseStack);
        }

        private void renderPanelAwareScrollbar(PoseStack poseStack) {
            if (!this.scrollbarVisible()) return;
            int thumbHeight = this.getScrollBarHeight();
            int left = this.x + this.width;
            int right = left + 8;
            int top = Math.max(
                    this.y,
                    (int) this.scrollAmount() * (this.height - thumbHeight)
                    / Math.max(1, this.getMaxScrollAmount()) + this.y
            );
            int bottom = top + thumbHeight;
            fill(poseStack, left, top, right, bottom, 0xFF808080);
            fill(poseStack, left, top, right - 1, bottom - 1, 0xFFC0C0C0);
        }

{% endif %}
{% when "1.19.4" %}
{% if features.editor_v1_panel_clipping %}
        /**
         * Vanilla's implementation uses screen-global scissor coordinates and
         * disables any parent scissor.  This editor can live in a transformed
         * workspace panel, so clip through the shared nested stack instead.
         */
        @Override
        @MCVersionDependentBehaviour
        public void renderWidget(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
            if (!this.visible) return;
            int x = SFMWidgetUtils.getX(this);
            int y = SFMWidgetUtils.getY(this);
            int border = this.isFocused() ? -1 : -6250336;
            fill(poseStack, x, y, x + this.width, y + this.height, border);
            fill(
                    poseStack,
                    x + 1,
                    y + 1,
                    x + this.width - 1,
                    y + this.height - 1,
                    -16777216
            );
            SFMScissorStack.pushGui(
                    poseStack,
                    x + 1,
                    y + 1,
                    x + this.width - 1,
                    y + this.height - 1
            );
            poseStack.pushPose();
            try {
                poseStack.translate(0.0D, -this.scrollAmount(), 0.0D);
                this.renderContents(poseStack, mouseX, mouseY, partialTick);
            } finally {
                poseStack.popPose();
                SFMScissorStack.pop();
            }
            renderPanelAwareScrollbar(poseStack);
        }

        private void renderPanelAwareScrollbar(PoseStack poseStack) {
            if (!this.scrollbarVisible()) return;
            int x = SFMWidgetUtils.getX(this);
            int y = SFMWidgetUtils.getY(this);
            int thumbHeight = this.getScrollBarHeight();
            int left = x + this.width;
            int right = left + 8;
            int top = Math.max(
                    y,
                    (int) this.scrollAmount() * (this.height - thumbHeight)
                    / Math.max(1, this.getMaxScrollAmount()) + y
            );
            int bottom = top + thumbHeight;
            fill(poseStack, left, top, right, bottom, 0xFF808080);
            fill(poseStack, left, top, right - 1, bottom - 1, 0xFFC0C0C0);
        }

{% endif %}
{% else %}
{% endcase %}
        public void scrollToTop() {

            this.setScrollAmount(0);
        }

        @Override
        public void setFocused(boolean focused) {

            super.setFocused(focused);
//            if (!focused) {
//                this.scrollbarDragActive = false;
//            }
        }

        public int getCursorPosition() {

            return this.textField.cursor;
        }

        public void setCursorPosition(int cursor) {

            this.textField.seekCursor(Whence.ABSOLUTE, cursor);
        }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        @Override
{% else %}
        @MCVersionDependentBehaviour
{% endcase %}
        public int getScrollBarHeight() {
            // Fix #307: divide by zero exception in AbstractScrollWidget.mouseDragged
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            int rtn = super.getScrollBarHeight();
{% else %}
            int rtn = this.getBottom() - this.getY();
{% endcase %}
            if (rtn == this.height) {
                return rtn - 1;
            } else {
                return rtn;
            }
        }

        @MCVersionDependentBehaviour
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% else %}
        protected boolean withinContentAreaPoint(double x, double y) {
            return x >= (double)this.getX()
                    && x < (double)(this.getX() + this.width)
                    && y >= (double)this.getY()
                    && y < (double)(this.getY() + this.height);
        }

        @MCVersionDependentBehaviour
{% endcase %}
        @Override
        public boolean mouseClicked(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                double pMouseX,
                double pMouseY,
                int pButton
{% else %}
                MouseButtonEvent event,
                boolean doubleClick
{% endcase %}
        ) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}

{% else %}
            int pButton = event.button();
            double pMouseX = event.x(),
                    pMouseY = event.y();
{% endcase %}
            try {
                if (pButton == 0) {
                    this.scrollbarDragActive = false;
                }
                if (pButton == 0 && this.visible && this.withinContentAreaPoint(pMouseX, pMouseY)) {
                    if (content.isEmpty()) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.editor_v1_widget_focus %}
                        // Empty literal documents (including a new review note)
                        // still need a focusable insertion target. Returning false
                        // here prevents Screen from routing subsequent characters.
                        this.setFocused(true);
                        this.textField.setSelecting(true);
                        return true;
{% else %}
                        return false;
{% endif %}
{% else %}
                        return false;
{% endcase %}
                    }
                    // Focus the editor so the caret blinks and keys go here
                    this.setFocused(true);

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                    boolean shiftDown = Screen.hasShiftDown();
{% else %}
                    boolean shiftDown = event.hasShiftDown();
{% endcase %}
                    // Move cursor to the click position
                    seekCursorFromPoint(pMouseX, pMouseY);
                    // If not extending with Shift, start a new selection anchor at the click
                    if (!shiftDown) {
                        this.textField.selectCursor = this.textField.cursor;
                    }
                    // Enable selection so dragging extends from the anchor
                    this.textField.setSelecting(true);
                    return true;
                }
                boolean clickedScrollbar =
                        pButton == 0
                        && this.visible
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                        && this.scrollbarVisible()
                        && pMouseX >= SFMWidgetUtils.getX(this) + this.width
                        && pMouseX <= SFMWidgetUtils.getX(this) + this.width + 8
                        && pMouseY >= SFMWidgetUtils.getY(this)
                        && pMouseY < SFMWidgetUtils.getY(this) + this.height;
{% else %}
                        && this.isOverScrollbar(pMouseX, pMouseY);
{% endcase %}
                if (clickedScrollbar) {
                    this.scrollbarDragActive = true;
                }

{% case minecraft_version %}
{% when "1.19.2" %}
                return super.mouseClicked(pMouseX, pMouseY, pButton);
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
//                return super.mouseClicked(pMouseX, pMouseY, pButton);
                // we need to override the default behaviour because Mojang broke it
                // if it's not scrolling, it should return false for cursor click movement
                boolean rtn;
                if (!this.visible) {
                    rtn = false;
                } else {
                    //noinspection unused
                    boolean flag = this.withinContentAreaPoint(pMouseX, pMouseY);
                    boolean flag1 = this.scrollbarVisible()
                                    && pMouseX >= (double) (this.getX() + this.width)
                                    && pMouseX <= (double) (this.getX() + this.width + 8)
                                    && pMouseY >= (double) this.getY()
                                    && pMouseY < (double) (this.getY() + this.height);
                    if (flag1 && pButton == 0) {
                        this.scrolling = true;
                        rtn = true;
                    } else {
                        //1.19.4 behaviour:
                        //rtn=flag || flag1;
                        // instead, we want to return false if we're not scrolling
                        // (like how it was in 1.19.2)
                        // https://bugs.mojang.com/browse/MC-262754
                        rtn = false;
                    }
                }

                if (rtn) {
                    return true;
                } else if (this.withinContentAreaPoint(pMouseX, pMouseY) && pButton == 0) {
                    this.textField.setSelecting(Screen.hasShiftDown());
                    this.seekCursorScreen(pMouseX, pMouseY);
                    return true;
                } else {
                    return false;
                }
{% else %}
//                return super.mouseClicked(pMouseX, pMouseY, pButton);
                // we need to override the default behaviour because Mojang broke it
                // if it's not scrolling, it should return false for cursor click movement
                boolean rtn;
                if (!this.visible) {
                    rtn = false;
                } else {
                    //noinspection unused
                    boolean flag = this.withinContentAreaPoint(pMouseX, pMouseY);
                    boolean flag1 = this.isOverScrollbar(pMouseX, pMouseY);
                    if (flag1 && pButton == 0) {
                        this.scrolling = true;
                        rtn = true;
                    } else {
                        //1.19.4 behaviour:
                        //rtn=flag || flag1;
                        // instead, we want to return false if we're not scrolling
                        // (like how it was in 1.19.2)
                        // https://bugs.mojang.com/browse/MC-262754
                        rtn = false;
                    }
                }

                if (rtn) {
                    return true;
                } else if (this.withinContentAreaPoint(pMouseX, pMouseY) && pButton == 0) {
                    this.textField.setSelecting(event.hasShiftDown());
                    this.seekCursorScreen(pMouseX, pMouseY);
                    return true;
                } else {
                    return false;
                }
{% endcase %}
            } catch (Exception e) {
                SFM.LOGGER.error("Error in SFMTextEditScreenV1.MyMultiLineEditBox.mouseClicked", e);
                return false;
            }
        }

        @Override
        public int getInnerHeight() {
            // parent method uses this.textField.getLineCount() which is split for text wrapping
            // we don't use the wrapped text, so we need to calculate the height ourselves to avoid overshooting
            return this.font.lineHeight * (content.size() + 2);
        }

        @Override
        public boolean mouseDragged(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                double mx,
                double my,
                int button,
                double dx,
                double dy
{% else %}
                MouseButtonEvent event,
                double dx,
                double dy
{% endcase %}
        ) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% else %}
            int button = event.button();
            double mx = event.x(),
                    my = event.y();
{% endcase %}
            // IMPORTANT: give the scrollbar drag priority.
            // If the drag started on the scrollbar, AbstractScrollWidget will
            // consume this, and we should not start a text selection.
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            if (this.scrollbarDragActive && super.mouseDragged(mx, my, button, dx, dy)) {
{% else %}
            if (this.scrollbarDragActive && super.mouseDragged(event, dx, dy)) {
{% endcase %}
                return true;
            }

            try {
                if (button == 0 && this.visible && this.withinContentAreaPoint(mx, my)) {
                    if (content.isEmpty()) {
                        return false;
                    }
                    // Keep selection active while dragging and update cursor
                    this.textField.setSelecting(true);
                    seekCursorFromPoint(mx, my);
                    return true;
                }
            } catch (Exception e) {
                SFM.LOGGER.error("Error in SFMTextEditScreenV1.MyMultiLineEditBox.mouseDragged", e);
                return false;
            }

            return false;
        }

        @Override
        public boolean mouseReleased(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                double mx,
                double my,
                int button
{% else %}
                MouseButtonEvent event
{% endcase %}
        ) {

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            if (button == 0) {
{% else %}
            if (event.button() == 0) {
{% endcase %}
                // Stop active selection on mouse up
                this.textField.setSelecting(false);
                this.scrollbarDragActive = false;
            }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            return super.mouseReleased(mx, my, button);
{% else %}
            return super.mouseReleased(event);
{% endcase %}
        }

        public int getSelectionCursorPosition() {

            return this.textField.selectCursor;
        }

        public void setSelectionCursorPosition(int cursor) {

            this.textField.selectCursor = cursor;
        }

        public double getScrollAmount() {

            return this.scrollAmount();
        }

        @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        protected void setScrollAmount(double pScrollAmount) {
{% else %}
        public void setScrollAmount(double pScrollAmount) {
{% endcase %}

            if (!scrollingEnabled) return;
            super.setScrollAmount(pScrollAmount);
        }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
        @Override
{% else %}
{% endcase %}
        public void tick() {

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
            super.tick();

{% else %}
{% endcase %}
            this.cursorBlinkTick++;
        }

        private void seekCursorFromPoint(
                double mx,
                double my
        ) {

            int lineCount = content.size();
            double innerX = mx - (
                    SFMWidgetUtils.getX(this)
                    + this.innerPadding()
                    + SFMTextEditorUtils.getLineNumberWidth(this.font, lineCount)
            );
            double innerY = my - (SFMWidgetUtils.getY(this) + this.innerPadding()) + this.scrollAmount();
            int lineIndex = Mth.clamp(
                    (int) Math.floor(innerY / Math.max(1, this.font.lineHeight)),
                    0,
                    Math.max(0, lineCount - 1)
            );
            int cursorPosition = pointToCursor(innerX, lineIndex);

            this.scrollingEnabled = false;
            this.textField.seekCursor(Whence.ABSOLUTE, cursorPosition);
            this.scrollingEnabled = true;
        }

        private int getLineStartIndex(int lineIndex) {

            if (displayedLineStartOffsets.isEmpty()) return 0;
            int clamped = Mth.clamp(
                    lineIndex,
                    0,
                    Math.max(0, displayedLineStartOffsets.size() - 1)
            );
            return displayedLineStartOffsets.get(clamped);
        }

        private int pointToCursor(
                double innerX,
                int lineIndex
        ) {

            int lineStartIndex = getLineStartIndex(lineIndex);
            if (content.isEmpty()) {
                return lineStartIndex;
            }
            int clampedLine = Mth.clamp(lineIndex, 0, Math.max(0, content.size() - 1));
            String plainLine = content.get(clampedLine).getString();
            int clampedX = (int) Math.max(0, innerX);
            int cursorOffsetInLine = this.font.plainSubstrByWidth(plainLine, clampedX).length();
            int widthBeforeCursor = this.font.width(plainLine.substring(0, cursorOffsetInLine));
            if (cursorOffsetInLine < plainLine.length()) {
                int nextGlyphWidth = this.font.width(plainLine.substring(cursorOffsetInLine, cursorOffsetInLine + 1));
                if ((double) (clampedX - widthBeforeCursor) >= nextGlyphWidth / 2.0D) {
                    cursorOffsetInLine = Math.min(plainLine.length(), cursorOffsetInLine + 1);
                }
            }
            return Mth.clamp(
                    lineStartIndex + cursorOffsetInLine,
                    0,
                    this.textField.value().length()
            );
        }

        @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        protected int getMaxScrollAmount() {
{% else %}
        public int maxScrollAmount() {
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            return Math.max(1, super.getMaxScrollAmount()); // Fix #307: divide by zero exception
{% else %}
            return Math.max(1, super.maxScrollAmount()); // Fix #307: divide by zero exception
{% endcase %}
        }

        private void onValueOrCursorChanged(String programString) {

            int cursorPosition = getCursorPosition();

            // Build the program only when text changed; reuse parse on cursor-only
            // moves
            ProgramBuildResult buildResult;
            if (programString.equals(cachedBuildProgram) && cachedBuildResult != null) {
                buildResult = cachedBuildResult;
            } else {

                buildResult = new ProgramBuilder(programString).build();
                cachedBuildProgram = programString;
                cachedBuildResult = buildResult;
            }

            // Update the intellisense picklist
            IntellisenseContext intellisenseContext = new IntellisenseContext(
                    buildResult,
                    cursorPosition,
                    getSelectionCursorPosition(),
                    openContext.labelPositionHolder(),
                    SFMConfig.CLIENT_TEXT_EDITOR_CONFIG.intellisenseLevel.get()
            );
            List<IntellisenseAction> suggestions = SFMLIntellisense.getSuggestions(intellisenseContext);
            SFMTextEditScreenV1.this.suggestedActions.setItems(suggestions);

            // Update the intellisense picklist query used to sort the suggestions
            String cursorWord = buildResult.getWordAtCursorPosition(cursorPosition);
            SFMTextEditScreenV1.this.suggestedActions.setQuery(Component.literal(cursorWord));

            boolean shouldPrint = false;
            //noinspection ConstantValue
            if (shouldPrint) {
                String cursorPositionDisplay = SFMDisplayUtils.getCursorPositionDisplay(programString, cursorPosition);
                String cursorTokenDisplay = SFMDisplayUtils.getCursorTokenDisplay(buildResult, cursorPosition);
                String tokenHierarchyDisplay;
                @Nullable Program program = buildResult.program();
                if (program == null) {
                    tokenHierarchyDisplay = "<INVALID PROGRAM>";
                } else {
                    tokenHierarchyDisplay = SFMDisplayUtils.getTokenHierarchyDisplay(program, cursorPosition);
                }

                String suggestionsDisplay = suggestedActions.getItems()
                        .stream()
                        .map(PickListItem::getComponent)
                        .map(Component::getString)
                        .collect(Collectors.joining(", "));

                SFM.LOGGER.info(
                        "PROGRAM OR CURSOR CHANGE! {}   {}   {}  |||  {} ||| {}",
                        cursorPositionDisplay,
                        cursorTokenDisplay,
                        tokenHierarchyDisplay,
                        cursorWord,
                        suggestionsDisplay
                );
            }
        }

        private void rebuildIntellisense() {

            onValueOrCursorChanged(getValue());
        }

        /**
         * Rebuilds the syntax-highlighted program text. This runs more frequently than
         * when the value is changed.
         *
         * @param showContextActionHints Should underline words that have context
         *                               actions
         */
        private void rebuild(boolean showContextActionHints) {

            lastProgram = this.textField.value();
            content = ProgramSyntaxHighlightingHelper.withSyntaxHighlighting(
                    lastProgram,
                    showContextActionHints
            );

            rebuildDisplayCache();
        }

        private void rebuildDisplayCache() {
            // Rebuild displayed line-start offsets to match the raw text and
            // rendered lines
            displayedLineStartOffsets.clear();
            displayedLineStartOffsets.add(0);
            for (int i = 0; i < lastProgram.length(); i++) {
                if (lastProgram.charAt(i) == '\n') {
                    displayedLineStartOffsets.add(i + 1);
                }
            }
            // Ensure the list size matches the number of rendered lines
            int lines = content.size();
            while (displayedLineStartOffsets.size() > lines) {
                displayedLineStartOffsets.remove(displayedLineStartOffsets.size() - 1);
            }
            while (displayedLineStartOffsets.size() < lines) {
                displayedLineStartOffsets.add(lastProgram.length());
            }
        }

        @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        protected void renderContents(
                PoseStack poseStack,
                int mx,
                int my,
                float partialTicks
        ) {
{% when "1.20", "1.20.1" %}
        protected void renderContents(
                GuiGraphics graphics,
                int mx,
                int my,
                float partialTicks
        ) {

            Matrix4f matrix4f = graphics.pose().last().pose();

{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        protected void renderContents(
                GuiGraphics graphics,
                int mx,
                int my,
                float partialTicks
        ) {

            Matrix4f matrix4f = graphics.pose().last().pose();

{% else %}
        protected void extractContents(
                GuiGraphicsExtractor graphics,
                int mx,
                int my,
                float partialTicks
        ) {
{% endcase %}
            // rebuild the program if necessary
            if (!lastProgram.equals(this.textField.value())) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                rebuild(Screen.hasControlDown());
{% else %}
                rebuild(SFMWidgetUtils.hasCtrlDown());
{% endcase %}
            }

            final List<MutableComponent> lines = content;
            if (lines.isEmpty()) {
                return;
            }

            final boolean isCursorVisible = this.isFocused() && this.cursorBlinkTick % 20 >= 10;
            final int cursorIndex = textField.cursor();

            final int lineHeight = Math.max(1, this.font.lineHeight);
            final int availableHeight = this.height - this.innerPadding() * 2;
            final double scroll = this.scrollAmount();

            // Determine which logical line is at the top
            final int viewLineIndexStart = Mth.clamp(
                    (int) Math.floor(scroll / lineHeight),
                    0,
                    Math.max(0, lines.size() - 1)
            );
            // Render a small overscan
            final int numVisibleLines = Math.max(1, availableHeight / lineHeight + 2);
            final int viewLineIndexEnd = Math.min(lines.size(), viewLineIndexStart + numVisibleLines);

            final int lineX =
                    SFMWidgetUtils.getX(this) + this.innerPadding()
                    + SFMTextEditorUtils.getLineNumberWidth(this.font, content.size());

            boolean isCursorAtEndOfLine = false;
            boolean drewCursorGlyph = false;

            // IMPORTANT: do not subtract (scroll % lineHeight) here.
            // The parent has already translated by -scrollAmount.
            // Draw at content-space Y positions as if there was no scrolling:
            final int contentTopY = SFMWidgetUtils.getY(this) + this.innerPadding();
            int lineY = contentTopY + viewLineIndexStart * lineHeight;
            int charCountAccum = getLineStartIndex(viewLineIndexStart);

            int cursorX = 0;
            int cursorY = 0;

            final StringView selectedRange = this.textField.getSelected();
            final int selectionStart = selectedRange.beginIndex();
            final int selectionEnd = selectedRange.endIndex();

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
            // One buffer for the entire text pass
            MultiBufferSource.BufferSource buffer = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
{% when "1.21", "1.21.1" %}
            // One buffer for the entire text pass
            MultiBufferSource.BufferSource buffer = graphics.bufferSource();
{% else %}
{% endcase %}

            // Collect selection highlights rects and draw them after the text
            List<int[]> highlightRects = new ArrayList<>();

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            Matrix4f matrix4f = poseStack.last().pose();

{% else %}
{% endcase %}
            for (int line = viewLineIndexStart; line < viewLineIndexEnd; ++line) {
                var componentColoured = lines.get(line);
                String plainLine = componentColoured.getString();
                int lineLength = plainLine.length();

                boolean isCursorOnThisLine =
                        cursorIndex >= charCountAccum
                        && cursorIndex <= charCountAccum + lineLength;

                if (SFMTextEditorUtils.shouldShowLineNumbers()) {
                    // Draw line number
                    String lineNumber = String.valueOf(line + 1);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                    SFMFontUtils.drawInBatch(
                            lineNumber,
{% else %}
                    SFMFontUtils.draw(
                            graphics,
{% endcase %}
                            this.font,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% else %}
                            lineNumber,
{% endcase %}
                            lineX - 2 - this.font.width(lineNumber),
                            lineY,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                            true,
                            false,
                            matrix4f,
                            buffer
{% else %}
                            -1,
                            true
{% endcase %}
                    );
                }

                if (isCursorOnThisLine) {
                    isCursorAtEndOfLine = cursorIndex == charCountAccum + lineLength;
                    cursorY = lineY;
                    int relativeCursorIndex = cursorIndex - charCountAccum;
                    int drawnWidthBeforeCursor = this.font.width(plainLine.substring(0, relativeCursorIndex));
                    cursorX = lineX + drawnWidthBeforeCursor;
                    // draw text before cursor
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                    SFMFontUtils.drawInBatch(
{% else %}
                    SFMFontUtils.draw(
                            graphics,
                            this.font,
{% endcase %}
                            SFMComponentUtils.substring(componentColoured, 0, relativeCursorIndex),
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                            font,
{% else %}
{% endcase %}
                            lineX,
                            lineY,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                            true,
                            false,
                            matrix4f,
                            buffer
{% else %}
                            -1,
                            true
{% endcase %}
                    );
                    SFMTextEditScreenV1.this.suggestedActions.setXY(cursorX + 10, cursorY);
                    // draw text after cursor
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                    SFMFontUtils.drawInBatch(
{% else %}
                    SFMFontUtils.draw(
                            graphics,
                            this.font,
{% endcase %}
                            SFMComponentUtils.substring(componentColoured, relativeCursorIndex, lineLength),
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                            font,
{% else %}
{% endcase %}
                            cursorX,
                            lineY,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                            true,
                            false,
                            matrix4f,
                            buffer
{% else %}
                            -1,
                            true
{% endcase %}
                    );
                    drewCursorGlyph = isCursorVisible;
                } else {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                    SFMFontUtils.drawInBatch(
{% else %}
                    SFMFontUtils.draw(
                            graphics,
                            this.font,
{% endcase %}
                            componentColoured,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                            font,
{% else %}
{% endcase %}
                            lineX,
                            lineY,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                            true,
                            false,
                            matrix4f,
                            buffer
{% else %}
                            -1,
                            true
{% endcase %}
                    );
                }

                // Check if the selection is within the current line
                if (selectionStart <= charCountAccum + lineLength && selectionEnd > charCountAccum) {
                    int lineSelectionStart = Math.max(selectionStart - charCountAccum, 0);
                    int lineSelectionEnd = Math.min(selectionEnd - charCountAccum, lineLength);

                    int highlightStartX = this.font.width(plainLine.substring(0, lineSelectionStart));
                    int highlightEndX = this.font.width(plainLine.substring(0, lineSelectionEnd));

                    highlightRects.add(new int[]{
                            lineX + highlightStartX,
                            lineY,
                            lineX + highlightEndX,
                            lineY + lineHeight
                    });
                }

                lineY += lineHeight;
                charCountAccum += lineLength + 1;
            }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            // Flush the text batch once
            buffer.endBatch();

{% else %}
{% endcase %}
            // Draw selection highlights after text
            for (int[] r : highlightRects) {
                SFMScreenRenderUtils.renderHighlight(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                        poseStack,
{% else %}
                        graphics,
{% endcase %}
                        r[0],
                        r[1],
                        r[2],
                        r[3]
                );
            }

            if (drewCursorGlyph) {
                if (isCursorAtEndOfLine) {
                    SFMFontUtils.draw(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                            poseStack,
{% else %}
                            graphics,
{% endcase %}
                            this.font,
                            "_",
                            cursorX,
                            cursorY,
                            -1,
                            true
                    );
                } else {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                    GuiComponent.fill(
                            poseStack,
                            cursorX,
                            cursorY - 1,
                            cursorX + 1,
                            cursorY + 1 + 9,
                            -1
                    );
{% else %}
                    graphics.fill(
                            cursorX,
                            cursorY - 1,
                            cursorX + 1,
                            cursorY + 1 + 9,
                            -1
                    );
{% endcase %}
                }
            }
        }

    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% else %}
    @Override
    public boolean isInGameUi() {
        return true;
    }
{% endcase %}
}
