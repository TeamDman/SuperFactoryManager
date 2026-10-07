package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMTextEditorActions;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.screen.SFMScreenRenderUtils;
import ca.teamdman.sfm.client.screen.SFMTextEditorConfigScreen;
import ca.teamdman.sfm.client.screen.widget.SFMButtonBuilder;
{% case minecraft_version %}
{% when "1.19.2" %}
import ca.teamdman.sfm.client.screen.widget.SFMExtendedButtonWithTooltip;
import ca.teamdman.sfm.client.text_editor.*;
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
import ca.teamdman.sfm.client.text_editor.*;
{% else %}
import ca.teamdman.sfm.client.text_editor.Caret;
import ca.teamdman.sfm.client.text_editor.Cursor;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditScreenOpenContext;
import ca.teamdman.sfm.client.text_editor.TextEditContext;
{% endcase %}
import ca.teamdman.sfm.client.text_editor.action.ITextEditAction;
import ca.teamdman.sfm.client.text_editor.action.KeyboardImpulse;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.blaze3d.vertex.PoseStack;
{% case minecraft_version %}
{% when "1.19.2" %}
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.math.Matrix4f;
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
import com.mojang.blaze3d.vertex.Tesselator;
{% else %}
{% endcase %}
import net.minecraft.ChatFormatting;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import net.minecraft.client.gui.GuiComponent;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.client.gui.GuiGraphics;
{% else %}
import net.minecraft.client.gui.GuiGraphicsExtractor;
{% endcase %}
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% else %}
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
{% endcase %}
import net.minecraft.client.renderer.MultiBufferSource;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.client.renderer.PanoramaRenderer;
{% else %}
import net.minecraft.client.renderer.Panorama;
{% endcase %}
import net.minecraft.network.chat.Component;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
{% else %}
import net.minecraft.util.ARGB;
{% endcase %}
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.jetbrains.annotations.Nullable;
{% case minecraft_version %}
{% when "1.19.2" %}
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import org.joml.Matrix4f;
{% else %}
import org.joml.Matrix3x2fStack;
{% endcase %}
import org.lwjgl.glfw.GLFW;

import java.util.LinkedList;

public class SFMTextEditScreenV2 extends Screen implements ISFMTextEditScreen {
    @SFMLocalizationDatagen
    public static final LocalizationEntry TEXT_EDIT_SCREEN_V2_TITLE = new LocalizationEntry(
            "gui.sfm.text_editor.v2.title",
            "Text Editor"
    );

    private final @Nullable Screen previousScreen;
{% if features.editor_overlay_push %}
    private final boolean pushed;
{% endif %}

    protected TextEditContext textEditContext;

    protected ISFMTextEditScreenOpenContext openContext;

    public SFMTextEditScreenV2(
            ISFMTextEditScreenOpenContext openContext,
            @Nullable Screen previousScreen
    ) {
{% if features.editor_overlay_push %}
        this(openContext, previousScreen, false);
    }
{% endif %}

{% if features.editor_overlay_push %}
    public SFMTextEditScreenV2(
            ISFMTextEditScreenOpenContext openContext,
            @Nullable Screen previousScreen,
            boolean pushed
    ) {

{% endif %}
        super(TEXT_EDIT_SCREEN_V2_TITLE.getComponent());
        this.openContext = openContext;
        this.previousScreen = previousScreen;
{% if features.editor_overlay_push %}
        this.pushed = pushed;
{% endif %}
        this.textEditContext = new TextEditContext(openContext.initialValue());
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
        // we are not calling super here because we are not using traditional widgets with tab navigation
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        if (pKeyCode == GLFW.GLFW_KEY_ESCAPE && this.shouldCloseOnEsc()) {
{% else %}
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && this.shouldCloseOnEsc()) {
{% endcase %}
            this.onClose();
            return true;
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        KeyboardImpulse impulse = new KeyboardImpulse(pKeyCode, pScanCode, pModifiers);
{% else %}
        KeyboardImpulse impulse = new KeyboardImpulse(event);
{% endcase %}
        var matchedActions = SFMTextEditorActions
                .getTextEditActions()
                .filter(action -> action.matches(textEditContext, impulse)).toArray(ITextEditAction[]::new);
        for (ITextEditAction matchedAction : matchedActions) {
            SFM.LOGGER.debug("Matched action: {}", matchedAction.getClass().getSimpleName());
            matchedAction.apply(textEditContext, impulse);
        }
        return matchedActions.length > 0;
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

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        String text = Character.toString(pCodePoint);
{% else %}
        String text = Character.toString(event.codepoint());
{% endcase %}
        textEditContext.insertTextAtCursors(text);
        return true;
    }

    public boolean shouldShowLineNumbers() {

        return SFMConfig.getOrDefault(SFMConfig.CLIENT_TEXT_EDITOR_CONFIG.showLineNumbers);
    }

    @MCVersionDependentBehaviour
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
    public @Nullable PanoramaRenderer getPanorama() {

        if (this.openContext instanceof SFMTextEditScreenTitleScreenOpenContext titleScreenOpenContext) {
            return titleScreenOpenContext.titleScreen().panorama;
        }
        return null;
{% when "1.21", "1.21.1" %}
    public @Nullable PanoramaRenderer getPanorama() {
        return PANORAMA;
{% else %}
    public @Nullable Panorama getPanorama() {
        return this.getMinecraft().gameRenderer.getPanorama();
{% endcase %}
    }

    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public void render(
            PoseStack pPoseStack,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public void render(
            GuiGraphics pGuiGraphics,
{% else %}
    public void extractRenderState(
            GuiGraphicsExtractor pGuiGraphics,
{% endcase %}
            int pMouseX,
            int pMouseY,
            float pPartialTick
    ) {

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        PanoramaRenderer panorama = getPanorama();
{% else %}
        Panorama panorama = getPanorama();
{% endcase %}
        if (panorama != null) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
            panorama.render(pPartialTick, Mth.clamp(1.0F, 0.0F, 1.0F));
{% when "1.21", "1.21.1" %}
            panorama.render(pGuiGraphics, this.width, this.height, 1.0F, Mth.clamp(1.0F, 0.0F, 1.0F));
{% else %}
            panorama.extractRenderState(pGuiGraphics, this.width, this.height, true);
{% endcase %}
        }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        Matrix4f matrix4f = pPoseStack.last().pose();
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        Matrix4f matrix4f = pGuiGraphics.pose().last().pose();
{% else %}
        Matrix3x2fStack matrix4f = pGuiGraphics.pose();
{% endcase %}
        LinkedList<StringBuilder> lines = textEditContext.lines();
        int numLines = lines.size();
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        var buffer = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
        MultiBufferSource.BufferSource buffer = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
{% when "1.21", "1.21.1" %}
        MultiBufferSource.BufferSource buffer = pGuiGraphics.bufferSource();
{% else %}
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
{% endcase %}
        boolean shouldShowLineNumbers = shouldShowLineNumbers();
        int marginForLineNumber = shouldShowLineNumbers() ? this.font.width("000") + 4 : 0;
        for (int lineIndex = 0; lineIndex < numLines; lineIndex++) {
            StringBuilder line = lines.get(lineIndex);
            int lineHeight = this.font.lineHeight;
            if (shouldShowLineNumbers) {
                SFMFontUtils.drawInBatch(
                        Component.literal(String.format("%03d", lineIndex + 1)).withStyle(ChatFormatting.GRAY),
                        this.font,
                        0,
                        lineIndex * lineHeight,
                        true,
                        false,
                        matrix4f,
                        buffer
                );
            }
            SFMFontUtils.drawInBatch(
                    line.toString(),
                    this.font,
                    marginForLineNumber,
                    lineIndex * lineHeight,
                    true,
                    false, matrix4f,
                    buffer
            );
        }
        buffer.endBatch();

        // Render selection highlights
        var selectedCharactersByLine = textEditContext.selectedCharactersByLine();
        for (int lineIndex = 0; lineIndex < numLines; lineIndex++) {
            @Nullable IntervalSet selectedCharacters = selectedCharactersByLine.get(lineIndex);
            StringBuilder line = lines.get(lineIndex);
            if (selectedCharacters == null || selectedCharacters.isNil()) {
                continue; // no selection on this line
            }
            for (Interval interval : selectedCharacters.getIntervals()) {
                int selectionStartX = this.font.width(line.substring(0, interval.a)) + marginForLineNumber;
                int selectionEndX = this.font.width(line.substring(0, interval.b + 1)) + marginForLineNumber;
                int selectionY = lineIndex * this.font.lineHeight;
                SFMScreenRenderUtils.renderHighlight(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                        pPoseStack,
{% else %}
                        pGuiGraphics,
{% endcase %}
                        selectionStartX,
                        selectionY,
                        selectionEndX,
                        selectionY + this.font.lineHeight
                );
            }
        }

        // Render cursors
        for (Cursor cursor : textEditContext.multiCursor().cursors()) {
            Caret head = cursor.head();
            Caret tail = cursor.tail();
            int headX = this.font.width(lines.get(head.lineIndex()).substring(0, head.gapIndex()))
                        + marginForLineNumber;
            int tailX = this.font.width(lines.get(tail.lineIndex()).substring(0, tail.gapIndex()))
                        + marginForLineNumber;
            int headY = head.lineIndex() * this.font.lineHeight;
            int tailY = tail.lineIndex() * this.font.lineHeight;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            renderCursor(pPoseStack, headX, headY, FastColor.ARGB32.color(255 / 2, 255, 0, 0));
            renderCursor(pPoseStack, tailX, tailY, FastColor.ARGB32.color(255 / 2, 0, 0, 255));
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            renderCursor(pGuiGraphics, headX, headY, FastColor.ARGB32.color(255 / 2, 255, 0, 0));
            renderCursor(pGuiGraphics, tailX, tailY, FastColor.ARGB32.color(255 / 2, 0, 0, 255));
{% else %}
            renderCursor(pGuiGraphics, headX, headY, ARGB.color(255 / 2, 255, 0, 0));
            renderCursor(pGuiGraphics, tailX, tailY, ARGB.color(255 / 2, 0, 0, 255));
{% endcase %}
        }

        // Render widgets (buttons) on top of editor content
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        super.render(pPoseStack, pMouseX, pMouseY, pPartialTick);
        this.renderTooltip(pPoseStack, pMouseX, pMouseY);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        super.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
{% else %}
        super.extractRenderState(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
{% endcase %}
    }

    /**
     * The user has tried to close the GUI without saving by hitting the Esc key
     */
    @Override
    public void onClose() {

        openContext.onTryClose(
                textEditContext.getContent(),
{% if features.editor_overlay_push %}
                pushed
                        ? SFMScreenChangeHelpers::popScreen
                        : () -> SFMScreenChangeHelpers.setScreen(previousScreen)
{% else %}
                () -> SFMScreenChangeHelpers.setScreen(previousScreen)
{% endif %}
        );
    }

    @Override
    public ISFMTextEditScreenOpenContext openContext() {

        return openContext;
    }

    @Override
    public OpenBehaviour openBehaviour() {

{% if features.editor_overlay_push %}
        return pushed ? OpenBehaviour.Push : OpenBehaviour.Replace;
{% else %}
        return OpenBehaviour.Replace;
{% endif %}
    }

    protected void renderCursor(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            PoseStack pPoseStack,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            GuiGraphics guiGraphics,
{% else %}
            GuiGraphicsExtractor guiGraphics,
{% endcase %}
            int x,
            int y,
            int color
    ) {

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        GuiComponent.fill(
                pPoseStack,
{% else %}
        guiGraphics.fill(
{% endcase %}
                x,
                y,
                x + 2,
                y + this.font.lineHeight,
                color
        );
    }

    @Override
    protected void init() {

        super.init();
        SFMScreenRenderUtils.enableKeyRepeating();

        // Add config button like V1 ("#"), bottom-left corner
        this.addRenderableWidget(
                new SFMButtonBuilder()
                        .setPosition(4, this.height - 24)
                        .setSize(16, 20)
                        .setText(Component.literal("#"))
                        .setOnPress((button) -> SFMScreenChangeHelpers.setOrPushScreen(
                                new SFMTextEditorConfigScreen(
                                        this,
                                        SFMConfig.CLIENT_TEXT_EDITOR_CONFIG,
                                        () -> { /* no-op */ }
                                )
                        ))
                        .setTooltip(this, font, SFMTextEditScreenV1.PROGRAM_EDIT_SCREEN_CONFIG_BUTTON_TOOLTIP)
                        .build()
        );
    }

    protected void renderTooltip(
            PoseStack pose,
            int mx,
            int my
    ) {

        if (this.minecraft != null && this.minecraft.screen != this) {
            // keep focus behavior consistent with V1 (avoid stray tooltips)
            this.renderables
                    .stream()
                    .filter(AbstractWidget.class::isInstance)
                    .map(AbstractWidget.class::cast)
                    .forEach(w -> w.setFocused(false));
            return;
        }
        drawChildTooltips(pose, mx, my);
    }

    @MCVersionDependentBehaviour
    private void drawChildTooltips(
            PoseStack pose,
            int mx,
            int my
    ) {
        // 1.19.2: manually render button tooltips
{% case minecraft_version %}
{% when "1.19.2" %}
        this.renderables
                .stream()
                .filter(SFMExtendedButtonWithTooltip.class::isInstance)
                .map(SFMExtendedButtonWithTooltip.class::cast)
                .forEach(x -> x.renderToolTip(pose, mx, my));
{% else %}
//        this.renderables
//                .stream()
//                .filter(SFMExtendedButtonWithTooltip.class::isInstance)
//                .map(SFMExtendedButtonWithTooltip.class::cast)
//                .forEach(x -> x.renderToolTip(pose, mx, my));
{% endcase %}
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
