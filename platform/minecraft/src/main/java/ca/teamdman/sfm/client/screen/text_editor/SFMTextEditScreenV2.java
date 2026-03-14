package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMTextEditorActions;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.screen.SFMScreenRenderUtils;
import ca.teamdman.sfm.client.screen.SFMTextEditorConfigScreen;
import ca.teamdman.sfm.client.text_editor.*;
import ca.teamdman.sfm.client.text_editor.action.ITextEditAction;
import ca.teamdman.sfm.client.text_editor.action.KeyboardImpulse;
import ca.teamdman.sfm.client.widget.SFMButtonBuilder;
import ca.teamdman.sfm.client.widget.SFMExtendedButtonWithTooltip;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.math.Matrix4f;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedList;

public class SFMTextEditScreenV2 extends Screen implements ISFMTextEditScreen {
    private final @Nullable Screen previousScreen;
    protected TextEditContext textEditContext;
    protected ISFMTextEditScreenOpenContext openContext;

    public SFMTextEditScreenV2(
            ISFMTextEditScreenOpenContext openContext,
            @Nullable Screen previousScreen
    ) {
        super(LocalizationKeys.TEXT_EDIT_SCREEN_TITLE.getComponent());
        this.openContext = openContext;
        this.previousScreen = previousScreen;
        this.textEditContext = new TextEditContext(openContext.initialValue());
    }

    @Override
    public boolean keyPressed(
            int pKeyCode,
            int pScanCode,
            int pModifiers
    ) {
        // we are not calling super here because we are not using traditional widgets with tab navigation
        if (pKeyCode == GLFW.GLFW_KEY_ESCAPE && this.shouldCloseOnEsc()) {
            this.onClose();
            return true;
        }
        KeyboardImpulse impulse = new KeyboardImpulse(pKeyCode, pScanCode, pModifiers);
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
            char pCodePoint,
            int pModifiers
    ) {
        String text = Character.toString(pCodePoint);
        textEditContext.insertTextAtCursors(text);
        return true;
    }

    public boolean shouldShowLineNumbers() {
        return SFMConfig.getOrDefault(SFMConfig.CLIENT_TEXT_EDITOR_CONFIG.showLineNumbers);
    }

    @MCVersionDependentBehaviour
    public @Nullable PanoramaRenderer getPanorama() {
        if (this.openContext instanceof SFMTextEditScreenTitleScreenOpenContext titleScreenOpenContext) {
            return titleScreenOpenContext.titleScreen().panorama;
        }
        return null;
    }

    @Override
    public void render(
            PoseStack pPoseStack,
            int pMouseX,
            int pMouseY,
            float pPartialTick
    ) {
        PanoramaRenderer panorama = getPanorama();
        if (panorama != null) {
            panorama.render(pPartialTick, Mth.clamp(1.0F, 0.0F, 1.0F));
        }

        Matrix4f matrix4f = pPoseStack.last().pose();
        LinkedList<StringBuilder> lines = textEditContext.lines();
        int numLines = lines.size();
        var buffer = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
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
                        pPoseStack,
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
            renderCursor(pPoseStack, headX, headY, FastColor.ARGB32.color(255 / 2, 255, 0, 0));
            renderCursor(pPoseStack, tailX, tailY, FastColor.ARGB32.color(255 / 2, 0, 0, 255));
        }

        // Render widgets (buttons) on top of editor content
        super.render(pPoseStack, pMouseX, pMouseY, pPartialTick);
        this.renderTooltip(pPoseStack, pMouseX, pMouseY);
    }

    /**
     * The user has tried to close the GUI without saving by hitting the Esc key
     */
    @Override
    public void onClose() {
        openContext.onTryClose(
                textEditContext.getContent(),
                () -> SFMScreenChangeHelpers.setScreen(previousScreen)
        );
    }

    @Override
    public ISFMTextEditScreenOpenContext openContext() {
        return openContext;
    }

    @Override
    public OpenBehaviour openBehaviour() {
        return OpenBehaviour.Replace;
    }

    protected void renderCursor(
            PoseStack pPoseStack,
            int x,
            int y,
            int color
    ) {
        GuiComponent.fill(
                pPoseStack,
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
                        .setTooltip(this, font, LocalizationKeys.PROGRAM_EDIT_SCREEN_CONFIG_BUTTON_TOOLTIP)
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
                    .filter(net.minecraft.client.gui.components.AbstractWidget.class::isInstance)
                    .map(net.minecraft.client.gui.components.AbstractWidget.class::cast)
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
        this.renderables
                .stream()
                .filter(SFMExtendedButtonWithTooltip.class::isInstance)
                .map(SFMExtendedButtonWithTooltip.class::cast)
                .forEach(x -> x.renderToolTip(pose, mx, my));
    }
}
