package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditScreenOpenContext;
import ca.teamdman.sfm.client.text_editor.SFMVimModalState;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Opt-in prototype over V1's existing widget, highlighting, scrolling and save context. */
public final class SFMVimModalScreen extends SFMTextEditScreenV1 {
    @SFMLocalizationDatagen
    public static final LocalizationEntry STATUS = new LocalizationEntry(
            "gui.sfm.vim_modal.status", "Vim-style (experimental): {} | i: Insert, Esc: Normal, u: Undo, Ctrl+R: Redo");
    @SFMLocalizationDatagen
    public static final LocalizationEntry NAME = new LocalizationEntry(
            "gui.sfm.preferred_editor.sfm.vim_modal_experimental", "Vim-style (experimental)");
    private final SFMVimModalState modal;

    public SFMVimModalScreen(ISFMTextEditScreenOpenContext context) {
        super(context);
        modal = new SFMVimModalState(context.initialValue());
    }

    @Override
    protected void init() {
        if (textarea != null) captureWidget();
        super.init();
        restoreWidget();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        // The inherited resize reassigns the text after init, resetting the caret.
        // init already preserves this editor's exact snapshot and modal state.
        init(minecraft, width, height);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        captureWidget();
        if (modal.mode() == SFMVimModalState.Mode.NORMAL) {
            if ((modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) == 0) {
                modal.command(codePoint, openContext().readOnly());
                restoreWidget();
            }
            return true;
        }
        if (openContext().readOnly()) return true;
        boolean handled = super.charTyped(codePoint, modifiers);
        captureWidget();
        return handled;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        captureWidget();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            modal.escape();
            restoreWidget();
            return true;
        }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0
                && (key == GLFW.GLFW_KEY_R || key == GLFW.GLFW_KEY_Z)) {
            if (!openContext().readOnly()) {
                if (key == GLFW.GLFW_KEY_R) modal.redo();
                else modal.undo();
                restoreWidget();
            }
            return true;
        }
        if (isSaveAndCloseShortcut(key, modifiers)) {
            if (!openContext().readOnly()) saveAndClose();
            return true;
        }
        if (modal.mode() == SFMVimModalState.Mode.INSERT) {
            if (openContext().readOnly()) return true;
            boolean handled = super.keyPressed(key, scanCode, modifiers);
            captureWidget();
            return handled;
        }
        char motion = switch (key) {
            case GLFW.GLFW_KEY_LEFT -> 'h';
            case GLFW.GLFW_KEY_RIGHT -> 'l';
            case GLFW.GLFW_KEY_UP -> 'k';
            case GLFW.GLFW_KEY_DOWN -> 'j';
            case GLFW.GLFW_KEY_HOME -> '0';
            case GLFW.GLFW_KEY_END -> '$';
            default -> '\0';
        };
        if (motion != '\0') {
            modal.command(motion, openContext().readOnly());
            restoreWidget();
        } else if (key == GLFW.GLFW_KEY_C && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            return super.keyPressed(key, scanCode, modifiers);
        }
        // Printable normal commands arrive through charTyped, once per character.
        // Suppress native paste/delete/tab/completion edits in Normal mode.
        return true;
    }

    @Override
    public void saveAndClose() {
        if (openContext().readOnly()) onClose();
        else super.saveAndClose();
    }

    private void captureWidget() {
        modal.acceptWidgetValue(textarea.getValue(), textarea.getCursorPosition());
    }

    private void restoreWidget() {
        var snapshot = modal.snapshot();
        if (!textarea.getValue().equals(snapshot.text())) textarea.setValue(snapshot.text());
        textarea.setCursorPosition(snapshot.cursor());
        textarea.setSelectionCursorPosition(snapshot.cursor());
        setFocused(textarea);
    }

    @Override
    @MCVersionDependentBehaviour
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        super.render(pose, mouseX, mouseY, partialTick);
        String mode = modal.mode().name() + (modal.pendingDelete() ? " d" : "");
        Component status = STATUS.getComponent(Component.literal(mode));
        String visible = font.plainSubstrByWidth(status.getString(), Math.max(1, width - 8));
{% if features.canvas_text_editor %}
        SFMFontUtils.draw(pose, font, visible, 4, 0, 0xFFFFFF, true);
{% else %}
        drawString(pose, font, visible, 4, 0, 0xFFFFFF);
{% endif %}
    }
}
