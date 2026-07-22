package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.common.program.RegexCache;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfml.ast.SFMLLiteralGlob;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Predicate;

public final class ShowLiteralGlobDiagnosticPuppetAction implements SFMPuppetAction {
    @Override
    public String description() {
        return "show literal-safe SFML wildcard diagnostic";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        String glob = "*.java";
        String regex = SFMLLiteralGlob.toRegex(glob);
        Predicate<String> matcher = RegexCache.buildPredicate(regex);
        boolean dotted = matcher.test("Example.java");
        boolean missingDot = matcher.test("Examplexjava");
        if (!dotted || missingDot) throw new IllegalStateException("Literal-safe glob invariant failed");
        Minecraft.getInstance().setScreen(new DiagnosticScreen(glob, regex, dotted, missingDot));
        return true;
    }

    private static final class DiagnosticScreen extends Screen {
        private final String glob;
        private final String regex;
        private final boolean dotted;
        private final boolean missingDot;

        private DiagnosticScreen(String glob, String regex, boolean dotted, boolean missingDot) {
            super(Component.literal("SFML wildcard diagnostic"));
            this.glob = glob;
            this.regex = regex;
            this.dotted = dotted;
            this.missingDot = missingDot;
        }

        @Override
        public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
            renderBackground(poseStack);
            int panelWidth = Math.min(360, width - 24);
            int left = (width - panelWidth) / 2;
            int top = Math.max(18, (height - 154) / 2);
            fill(poseStack, left, top, left + panelWidth, top + 154, 0xF0202020);
            fill(poseStack, left, top, left + panelWidth, top + 1, 0xFF55FFFF);
            fill(poseStack, left, top + 153, left + panelWidth, top + 154, 0xFF55FFFF);
            fill(poseStack, left, top, left + 1, top + 154, 0xFF55FFFF);
            fill(poseStack, left + panelWidth - 1, top, left + panelWidth, top + 154, 0xFF55FFFF);

            drawCenteredString(poseStack, font, title.copy().withStyle(ChatFormatting.BOLD), width / 2,
                    top + 14, 0xFFFFFFFF);
            drawString(poseStack, font, Component.literal("Unquoted literal glob"), left + 16, top + 40,
                    0xFFAAAAAA);
            drawString(poseStack, font, Component.literal(glob), left + 170, top + 40, 0xFFFFFF55);
            drawString(poseStack, font, Component.literal("Generated regex"), left + 16, top + 58,
                    0xFFAAAAAA);
            drawString(poseStack, font, Component.literal(regex), left + 170, top + 58, 0xFF80D8FF);
            drawString(poseStack, font, Component.literal("Example.java"), left + 16, top + 88,
                    0xFFFFFFFF);
            drawString(poseStack, font, Component.literal(dotted ? "MATCH" : "NO MATCH"), left + 250,
                    top + 88, dotted ? 0xFF55FF88 : 0xFFFF7777);
            drawString(poseStack, font, Component.literal("Examplexjava"), left + 16, top + 108,
                    0xFFFFFFFF);
            drawString(poseStack, font, Component.literal(missingDot ? "MATCH" : "NO MATCH"), left + 250,
                    top + 108, missingDot ? 0xFFFF7777 : 0xFF55FF88);
            drawCenteredString(poseStack, font, Component.literal("Only '*' is wildcard syntax; '.' stays literal."),
                    width / 2, top + 134, 0xFFBBBBBB);
            super.render(poseStack, mouseX, mouseY, partialTick);
        }
    }
}
