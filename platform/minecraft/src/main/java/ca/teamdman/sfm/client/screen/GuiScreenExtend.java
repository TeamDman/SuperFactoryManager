package ca.teamdman.sfm.client.screen;

import com.bbscn.GuiEventListener;
import com.bbscn.Renderable;
import com.bbscn.Tools;
import com.google.common.collect.Lists;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public class GuiScreenExtend extends GuiScreen implements IStackableScreen {


    private @Nullable GuiScreen parentScreen;

    @Override
    public void initGui() {
        super.initGui();
        children.clear();
        renderables.clear();
    }

    @Override
    public void setParent(GuiScreen parent) {
        this.parentScreen = parent;
    }

    @Nullable
    @Override
    public GuiScreen getParent() {
        return this.parentScreen;
    }

    public void onClose() {
        if (this.getParent() != null) {
            Minecraft.getMinecraft().displayGuiScreen(this.getParent());
        } else {
            this.mc.displayGuiScreen(null);
        }
    }

    @Nullable
    GuiEventListener focused;
    protected final List<GuiEventListener> children = Lists.newArrayList();
    protected final List<Renderable> renderables = new CopyOnWriteArrayList<>();
    boolean dragging;
    int lastMouseX;
    int lastMouseY;

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        for (Renderable renderable : this.renderables) {
            renderable.render(mouseX, mouseY, partialTicks);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    protected <T extends GuiEventListener & Renderable> T addRenderableWidget(T pWidget) {
        this.renderables.add(pWidget);
        return this.addWidget(pWidget);
    }

    protected <T extends GuiEventListener> T addWidget(T pListener) {
        this.children.add(pListener);
        return pListener;
    }

    protected void removeWidget(GuiEventListener pListener) {
        if (pListener instanceof Renderable) {
            this.renderables.remove((Renderable) pListener);
        }
        this.children.remove(pListener);
    }

    Optional<GuiEventListener> getChildAt(int pMouseX, int pMouseY) {
        for (GuiEventListener guieventlistener : this.children) {
            if (guieventlistener.isMouseOver(pMouseX, pMouseY)) {
                return Optional.of(guieventlistener);
            }
        }

        return Optional.empty();
    }

    void setDragging(boolean pIsDragging) {
        this.dragging = pIsDragging;
    }

    boolean isDragging() {
        return dragging;
    }

    @javax.annotation.Nullable
    public GuiEventListener getFocused() {
        return this.focused;
    }

    public void setFocused(@javax.annotation.Nullable GuiEventListener pListener) {
        if (this.focused != null) {
            this.focused.setFocused(false);
        }

        if (pListener != null) {
            pListener.setFocused(true);
        }

        this.focused = pListener;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        if (mouseButton == 0) {
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }

        for (GuiEventListener guieventlistener : this.children) {
            if (guieventlistener.mouseClicked(mouseX, mouseY, mouseButton)) {
                this.setFocused(guieventlistener);
                if (mouseButton == 0) {
                    this.setDragging(true);
                }
                return;
            }
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        if (state == 0 && this.isDragging()) {
            this.setDragging(false);
            if (this.getFocused() != null) {
                this.getFocused().mouseReleased(mouseX, mouseY, state);
            }
        }

        this.getChildAt(mouseX, mouseY).filter(p_94708_ -> p_94708_.mouseReleased(mouseX, mouseY, state));

    }

    boolean mouseScrolled(int pMouseX, int pMouseY, int pScrollX, int pScrollY) {
        return this.getChildAt(pMouseX, pMouseY).filter(p_293596_ -> p_293596_.mouseScrolled(
                pMouseX,
                pMouseY,
                pScrollX,
                pScrollY
        )).isPresent();
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (this.getFocused() != null && this.isDragging() && clickedMouseButton == 0) {
            int dragX = mouseX - lastMouseX;
            int dragY = mouseY - lastMouseY;
            this.getFocused().mouseDragged(mouseX, mouseY, clickedMouseButton, dragX, dragY);
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    public boolean charTyped(
            char pCodePoint,
            int pModifiers
    ) {
        if (GuiScreen.isCtrlKeyDown() && pCodePoint == ' ') {
            return true;
        }
        return this.getFocused() != null && this.getFocused().charTyped(pCodePoint, pModifiers);
    }

    @Override
    public void handleMouseInput() throws IOException {
        int i = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int j = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        int scroll = Mouse.getEventDWheel();
        if (scroll != 0) {
            this.mouseScrolled(i, j, 0, scroll > 0 ? 1 : -1);
        }
        super.handleMouseInput();
    }


    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // 模拟高版本 keyPressed
        boolean b = this.keyPressed(keyCode, -1, 0);
        if (b) return;

        super.keyTyped(typedChar, keyCode);

        // 模拟高版本 charTyped，仅在字符有效时调用
        if (typedChar != 0 && Tools.isAllowedChatCharacter(typedChar)) {
            this.charTyped(typedChar, 0);
        }
    }

    public boolean keyPressed(int pKeyCode, int mod1, int mod2) {
        if (pKeyCode == Keyboard.KEY_ESCAPE && this.shouldCloseOnEsc()) {
            this.onClose();
            return true;
        } else if (this.focused != null && this.focused.keyPressed(pKeyCode, mod1, mod2)) {
            return true;
        }
        return pKeyCode == Keyboard.KEY_RETURN;
    }

    public boolean shouldCloseOnEsc() {
        return true;
    }

    public void onDone() {
        onClose();
    }

    public static boolean hasShiftDown() {
        return Keyboard.isKeyDown(42) || Keyboard.isKeyDown(54);
    }
}
