package ca.teamdman.sfm.client.screen.widget;

import ca.teamdman.sfm.client.program.signing.ClientSigningSecretBuffer;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.SFMWidgetUtils;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Typed-only secret field: deliberately not an EditBox and never copied into the clipboard/history. */
public final class SFMSigningPassphraseWidget extends AbstractWidget implements AutoCloseable {
    private final Font font;
    private final ClientSigningSecretBuffer secret = new ClientSigningSecretBuffer();
    public SFMSigningPassphraseWidget(Font font, int x, int y, int width, Component label) {
        super(x, y, width, 20, label);
        this.font = font;
    }
    public boolean usable() { return secret.usable(); }
    public boolean matches(SFMSigningPassphraseWidget other) { return secret.matches(other.secret); }
    public char[] consume() { return secret.consume(); }
    @Override public void close() { secret.close(); }
    @Override public boolean charTyped(char character, int modifiers) {
        return active && visible && isFocused() && secret.append(character);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!active || !visible || !isFocused()) return false;
        if (key == GLFW.GLFW_KEY_BACKSPACE) { secret.backspace(); return true; }
        if (key == GLFW.GLFW_KEY_DELETE) { secret.close(); return true; }
        // Consume clipboard/undo combinations without ever invoking a clipboard service.
        if ((modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0) return true;
        return false;
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || button != 0 || !isMouseOver(mouseX, mouseY)) return false;
        setFocused(true);
        return true;
    }
    @Override
    @MCVersionDependentBehaviour
    public void renderWidget(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        if (!visible) return;
        int x = SFMWidgetUtils.getX(this);
        int y = SFMWidgetUtils.getY(this);
        fill(pose, x, y, x + width, y + height, isFocused() ? 0xFF8EBEFF : 0xFF666666);
        fill(pose, x + 1, y + 1, x + width - 1, y + height - 1, 0xFF14171D);
        String display = secret.length() == 0 ? getMessage().getString() : "*".repeat(Math.min(secret.length(), Math.max(1, (width - 12) / 6)));
        SFMFontUtils.draw(pose, font, font.plainSubstrByWidth(display, width - 12), x + 6, y + 6,
                secret.length() == 0 ? 0xFF999999 : 0xFFFFFFFF, false);
    }
    @Override
    @MCVersionDependentBehaviour
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        narration.add(NarratedElementType.TITLE, getMessage());
        narration.add(NarratedElementType.USAGE, Component.literal("Hidden, typing only. Backspace removes a character. Delete clears. Tab moves focus."));
    }
}
