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

import java.util.ArrayList;
import java.util.List;

/** Composable terminal leaf. It intentionally renders only the local service's transcript. */
public final class SFMTerminalPanel implements SFMScreenPanel {
    private static final int PANEL = 0xF0101218;
    private static final int TEXT = 0xFFE8F0F2;
    private static final int MUTED = 0xFF8AA0A8;
    private static final int ERROR = 0xFFFF7777;
    private static final int INPUT = 0xFF162530;
    private final SFMTerminalClient client;
    private final List<String> transcript = new ArrayList<>();
    private String input = "";
    private SFMScreenPanelBounds bounds = new SFMScreenPanelBounds(0, 0, 1, 1);
    private SFMWorkspacePanelContext context;

    public SFMTerminalPanel(SFMTerminalService service) {
        this(new SFMTerminalClient(service));
    }

    public SFMTerminalPanel(SFMTerminalClient client) {
        this.client = client;
        transcript.add("Java-local terminal · Rust/Vox unavailable fallback");
        transcript.add("Type pwd, ls, cat <file>, echo <text>, or write <file> <text>");
    }

    @Override
    public Component title() {
        return Component.literal("SFM Terminal");
    }

    @Override
    public Component narration() {
        return Component.literal("Java-local terminal at " + client.workingDirectory());
    }

    @Override
    public void opened(Minecraft minecraft, SFMScreenPanelBounds bounds, SFMWorkspacePanelContext context) {
        this.bounds = bounds;
        this.context = context;
    }

    @Override
    public void resized(Minecraft minecraft, SFMScreenPanelBounds bounds) {
        this.bounds = bounds;
    }

    @Override
    public void closed() {
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
        int first = Math.max(0, transcript.size() - visibleLines);
        int y = bounds.y() + 8;
        SFMFontUtils.draw(poseStack, minecraft.font, title().copy().withStyle(ChatFormatting.BOLD), left, y, TEXT, false);
        y += lineHeight + 4;
        for (int i = first; i < transcript.size() && y < inputY; i++) {
            int color = transcript.get(i).startsWith("error:") ? ERROR : TEXT;
            SFMFontUtils.draw(poseStack, minecraft.font,
                    minecraft.font.plainSubstrByWidth(transcript.get(i), width), left, y, color, false);
            y += lineHeight;
        }
        GuiComponent.fill(poseStack, bounds.x() + 4, inputY - 4, bounds.x() + bounds.width() - 4,
                bounds.y() + bounds.height() - 4, INPUT);
        String prompt = "> " + input + (focused ? "_" : "");
        SFMFontUtils.draw(poseStack, minecraft.font,
                minecraft.font.plainSubstrByWidth(prompt, width), left, inputY, MUTED, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
    public boolean charTyped(char character, int modifiers) {
        if (character >= 0x20 && character != 0x7F && input.length() < 512) {
            input += character;
            return true;
        }
        return false;
    }

    private void submitInput() {
        String command = input.trim();
        if (command.isEmpty()) return;
        transcript.add("> " + command);
        SFMTerminalResponse response = client.execute(command);
        for (String line : response.lines()) transcript.add((response.success() ? "" : "error: ") + line);
        input = "";
    }

    /** Deterministic hook for puppet proofs; normal users use keyboard input. */
    public void executeForAutomation(String command) {
        input = command == null ? "" : command;
        submitInput();
    }

    public List<String> transcript() {
        return List.copyOf(transcript);
    }

    public SFMTerminalClient client() {
        return client;
    }
}
