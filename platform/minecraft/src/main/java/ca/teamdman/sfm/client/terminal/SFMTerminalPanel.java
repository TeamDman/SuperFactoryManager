package ca.teamdman.sfm.client.terminal;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Composable terminal leaf. Rust/Vox frames are presented when that backend is configured. */
public final class SFMTerminalPanel implements SFMScreenPanel {
    private static final int PANEL = 0xF0101218;
    private static final int TEXT = 0xFFE8F0F2;
    private static final int MUTED = 0xFF8AA0A8;
    private static final int ERROR = 0xFFFF7777;
    private static final int INPUT = 0xFF162530;
    private final SFMTerminalClient client;
    private final SFMVoxTerminalService voxService;
    private final SFMTerminalPngRenderer pngRenderer = new SFMTerminalPngRenderer();
    private final SFMTerminalScrollback scrollback = new SFMTerminalScrollback();
    private final SFMTerminalFocusSequence focusSequence = new SFMTerminalFocusSequence();
    private String input = "";
    private SFMScreenPanelBounds bounds = new SFMScreenPanelBounds(0, 0, 1, 1);
    private SFMWorkspacePanelContext context;
    private Minecraft minecraft;
    private int renderLeft;
    private int renderTop;
    private int renderWidth;
    private int renderHeight;
    private int pressedMouseButtons;

    public SFMTerminalPanel(SFMTerminalService service) {
        this(new SFMTerminalClient(service), service instanceof SFMVoxTerminalService vox ? vox : null);
    }

    public SFMTerminalPanel(SFMTerminalClient client) {
        this(client, null);
    }

    private SFMTerminalPanel(SFMTerminalClient client, SFMVoxTerminalService voxService) {
        this.client = client;
        this.voxService = voxService;
        scrollback.appendAll(List.of(
                voxService == null
                        ? "Java-local terminal · Rust/Vox unavailable fallback"
                        : "Rust-authoritative terminal · full PNG Vox mode",
                "Type pwd, ls, cat <file>, echo <text>, or write <file> <text>"
        ));
    }

    @Override
    public Component title() {
        return Component.literal("SFM Terminal");
    }

    @Override
    public Component narration() {
        return Component.literal((voxService == null ? "Java-local terminal at " : "Rust/Vox terminal at ")
                + client.workingDirectory());
    }

    @Override
    public void opened(Minecraft minecraft, SFMScreenPanelBounds bounds, SFMWorkspacePanelContext context) {
        this.minecraft = minecraft;
        this.bounds = bounds;
        this.context = context;
    }

    @Override
    public void resized(Minecraft minecraft, SFMScreenPanelBounds bounds) {
        this.bounds = bounds;
        if (voxService != null) {
            int cellWidth = Math.max(1, minecraft.font.width("W"));
            int cellHeight = Math.max(1, minecraft.font.lineHeight + 2);
            voxService.resize(bounds.width() / cellWidth, bounds.height() / cellHeight);
        }
    }

    @Override
    public void closed() {
        if (minecraft != null) pngRenderer.close(minecraft);
        if (voxService != null) voxService.close();
        minecraft = null;
        context = null;
    }

    @Override
    public void render(PoseStack poseStack, Minecraft minecraft, SFMScreenPanelBounds bounds,
                       int mouseX, int mouseY, float partialTick, boolean focused) {
        GuiComponent.fill(poseStack, bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), PANEL);
        int left = bounds.x() + 8;
        int width = Math.max(1, bounds.width() - 16);
        int lineHeight = minecraft.font.lineHeight + 2;
        int inputY = bounds.y() + bounds.height() - lineHeight - 8;
        int visibleLines = Math.max(0, (inputY - bounds.y() - 22) / lineHeight);
        scrollback.setViewportLineCount(Math.max(1, visibleLines));
        int contentTop = bounds.y() + lineHeight + 12;
        renderLeft = left;
        renderTop = contentTop;
        renderWidth = width;
        renderHeight = Math.max(1, inputY - contentTop - 4);
        SFMFontUtils.draw(poseStack, minecraft.font, title().copy().withStyle(ChatFormatting.BOLD), left,
                bounds.y() + 8, TEXT, false);
        if (voxService != null && pngRenderer.render(poseStack, minecraft, left, contentTop, width,
                renderHeight, voxService.latestSnapshot())) {
            renderInput(poseStack, minecraft, left, width, inputY, focused);
            return;
        }
        int y = contentTop;
        for (SFMTerminalLine terminalLine : scrollback.visibleLineEntries()) {
            if (y >= inputY) break;
            String line = terminalLine.text();
            int color = line.startsWith("error:") ? ERROR : terminalLine.color();
            String remaining = line;
            do {
                String rendered = minecraft.font.plainSubstrByWidth(remaining, width);
                if (rendered.isEmpty()) rendered = remaining.substring(0, 1);
                SFMFontUtils.draw(poseStack, minecraft.font, rendered, left, y, color, false);
                y += lineHeight;
                remaining = remaining.substring(rendered.length());
            } while (!remaining.isEmpty() && y < inputY);
        }
        renderInput(poseStack, minecraft, left, width, inputY, focused);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (focusSequence.escape(System.nanoTime()) == SFMTerminalFocusSequence.Decision.EXIT) {
                if (context != null) context.submit(new ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntent.Close());
                return true;
            }
            if (voxService != null) voxService.sendKey(keyCode, modifiers, true, false);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            SFMTerminalFocusSequence.Decision decision = focusSequence.tab(System.nanoTime());
            if (decision == SFMTerminalFocusSequence.Decision.JAVA_FOCUS) {
                // Returning false lets Minecraft's Screen focus traversal own
                // this third Tab instead of sending it to the PTY.
                return false;
            }
            if (voxService != null) voxService.sendKey(keyCode, modifiers, true, false);
            else input += "\t";
            return true;
        }
        focusSequence.reset();
        if (voxService != null) {
            if (!isPrintableKey(keyCode)
                    || (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) != 0) {
                voxService.sendKey(keyCode, modifiers, true, false);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            scrollback.pageUp();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            scrollback.pageDown();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            scrollback.scrollOlder(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            scrollback.scrollNewer(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            scrollback.scrollToTop();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            scrollback.followOutput();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!input.isEmpty()) input = input.substring(0, input.length() - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submitInput();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (voxService == null) return false;
        if (!isPrintableKey(keyCode)
                || (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) != 0) {
            voxService.sendKey(keyCode, modifiers, false, false);
        }
        return true;
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (voxService != null) {
            if (character >= 0x20 && character != 0x7F) {
                voxService.sendText(String.valueOf(character));
            }
            return true;
        }
        if (character >= 0x20 && character != 0x7F && input.length() < 512) {
            input += character;
            return true;
        }
        return false;
    }

    private void submitInput() {
        String command = input.trim();
        if (command.isEmpty()) return;
        scrollback.append("> " + command);
        SFMTerminalResponse response = client.execute(command);
        if (response.success()) {
            scrollback.appendStyledAll(response.styledLines());
        } else {
            scrollback.appendStyledAll(response.styledLines().stream()
                    .map(line -> new SFMTerminalLine("error: " + line.text(), ERROR))
                    .toList());
        }
        input = "";
    }

    /** Deterministic hook for puppet proofs; normal users use keyboard input. */
    public void executeForAutomation(String command) {
        if (voxService != null) {
            SFMTerminalResponse response = client.execute(command == null ? "" : command);
            if (!response.success()) {
                throw new IllegalStateException("Rust terminal automation command failed: "
                        + String.join("; ", response.lines()));
            }
            return;
        }
        input = command == null ? "" : command;
        submitInput();
    }

    public List<String> transcript() {
        return scrollback.lines();
    }

    public List<String> visibleTranscript() {
        return scrollback.visibleLines();
    }

    public SFMTerminalScrollback scrollback() {
        return scrollback;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < bounds.x() || mouseX >= bounds.x() + bounds.width()
                || mouseY < bounds.y() || mouseY >= bounds.y() + bounds.height()) return false;
        if (voxService != null) {
            voxService.sendMouse(logicalX(mouseX), logicalY(mouseY), pressedMouseButtons, 0, false,
                    0, (int) Math.round(delta));
        } else if (delta > 0) scrollback.scrollOlder(Math.max(1, (int) Math.ceil(delta)));
        else if (delta < 0) scrollback.scrollNewer(Math.max(1, (int) Math.ceil(-delta)));
        return delta != 0;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!containsTerminalPoint(mouseX, mouseY)) return false;
        if (voxService == null) return false;
        int mask = mouseMask(button);
        pressedMouseButtons |= mask;
        voxService.sendMouse(logicalX(mouseX), logicalY(mouseY), pressedMouseButtons, button, true, 0, 0);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (voxService == null || !containsTerminalPoint(mouseX, mouseY)) return false;
        pressedMouseButtons &= ~mouseMask(button);
        voxService.sendMouse(logicalX(mouseX), logicalY(mouseY), pressedMouseButtons, button, false, 0, 0);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (voxService == null || !containsTerminalPoint(mouseX, mouseY)) return false;
        voxService.sendMouse(logicalX(mouseX), logicalY(mouseY), pressedMouseButtons, button, true, 0, 0);
        return true;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (voxService != null && pressedMouseButtons != 0 && containsTerminalPoint(mouseX, mouseY)) {
            voxService.sendMouse(logicalX(mouseX), logicalY(mouseY), pressedMouseButtons, 0, true, 0, 0);
        }
    }

    public SFMTerminalClient client() {
        return client;
    }

    private boolean containsTerminalPoint(double mouseX, double mouseY) {
        return mouseX >= renderLeft && mouseX < renderLeft + renderWidth
                && mouseY >= renderTop && mouseY < renderTop + renderHeight;
    }

    private int logicalX(double mouseX) {
        return Math.max(0, (int) ((mouseX - renderLeft) * voxService.logicalWidth() / Math.max(1, renderWidth)));
    }

    private int logicalY(double mouseY) {
        return Math.max(0, (int) ((mouseY - renderTop) * voxService.logicalHeight() / Math.max(1, renderHeight)));
    }

    private static int mouseMask(int button) {
        return button >= 0 && button < 8 ? 1 << button : 0;
    }

    private static boolean isPrintableKey(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_SPACE
                || keyCode >= GLFW.GLFW_KEY_APOSTROPHE && keyCode <= GLFW.GLFW_KEY_GRAVE_ACCENT
                || keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9
                || keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z;
    }

    private void renderInput(PoseStack poseStack, Minecraft minecraft, int left, int width, int inputY,
                             boolean focused) {
        GuiComponent.fill(poseStack, bounds.x() + 4, inputY - 4, bounds.x() + bounds.width() - 4,
                bounds.y() + bounds.height() - 4, INPUT);
        String prompt = "> " + input + (focused ? "_" : "");
        SFMFontUtils.draw(poseStack, minecraft.font,
                minecraft.font.plainSubstrByWidth(prompt, width), left, inputY, MUTED, false);
    }
}
