package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.common.program.RegexCache;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfml.ast.SFMLLiteralGlob;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import com.mojang.blaze3d.vertex.PoseStack;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.client.gui.GuiGraphics;
{% when "26.1.2" %}
import net.minecraft.client.gui.GuiGraphicsExtractor;
{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
            renderBackground(poseStack);
{% when "1.20", "1.20.1" %}
        @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics);
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics, mouseX, mouseY, partialTick);
{% when "26.1.2" %}
        @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
{% endcase %}
            int panelWidth = Math.min(360, width - 24);
            int left = (width - panelWidth) / 2;
            int top = Math.max(18, (height - 154) / 2);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            fill(poseStack, left, top, left + panelWidth, top + 154, 0xF0202020);
            fill(poseStack, left, top, left + panelWidth, top + 1, 0xFF55FFFF);
            fill(poseStack, left, top + 153, left + panelWidth, top + 154, 0xFF55FFFF);
            fill(poseStack, left, top, left + 1, top + 154, 0xFF55FFFF);
            fill(poseStack, left + panelWidth - 1, top, left + panelWidth, top + 154, 0xFF55FFFF);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            graphics.fill(left, top, left + panelWidth, top + 154, 0xF0202020);
            graphics.fill(left, top, left + panelWidth, top + 1, 0xFF55FFFF);
            graphics.fill(left, top + 153, left + panelWidth, top + 154, 0xFF55FFFF);
            graphics.fill(left, top, left + 1, top + 154, 0xFF55FFFF);
            graphics.fill(left + panelWidth - 1, top, left + panelWidth, top + 154, 0xFF55FFFF);
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            drawCentered(poseStack, title.copy().withStyle(ChatFormatting.BOLD), width / 2, top + 14, 0xFFFFFFFF);
            SFMFontUtils.draw(poseStack, font, Component.literal("Unquoted literal glob"), left + 16, top + 40,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            drawCentered(graphics, title.copy().withStyle(ChatFormatting.BOLD), width / 2, top + 14, 0xFFFFFFFF);
            SFMFontUtils.draw(graphics, font, Component.literal("Unquoted literal glob"), left + 16, top + 40,
{% endcase %}
                    0xFFAAAAAA, false);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            SFMFontUtils.draw(poseStack, font, Component.literal(glob), left + 170, top + 40, 0xFFFFFF55, false);
            SFMFontUtils.draw(poseStack, font, Component.literal("Generated regex"), left + 16, top + 58,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            SFMFontUtils.draw(graphics, font, Component.literal(glob), left + 170, top + 40, 0xFFFFFF55, false);
            SFMFontUtils.draw(graphics, font, Component.literal("Generated regex"), left + 16, top + 58,
{% endcase %}
                    0xFFAAAAAA, false);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            SFMFontUtils.draw(poseStack, font, Component.literal(regex), left + 170, top + 58, 0xFF80D8FF, false);
            SFMFontUtils.draw(poseStack, font, Component.literal("Example.java"), left + 16, top + 88,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            SFMFontUtils.draw(graphics, font, Component.literal(regex), left + 170, top + 58, 0xFF80D8FF, false);
            SFMFontUtils.draw(graphics, font, Component.literal("Example.java"), left + 16, top + 88,
{% endcase %}
                    0xFFFFFFFF, false);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            SFMFontUtils.draw(poseStack, font, Component.literal(dotted ? "MATCH" : "NO MATCH"), left + 250,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            SFMFontUtils.draw(graphics, font, Component.literal(dotted ? "MATCH" : "NO MATCH"), left + 250,
{% endcase %}
                    top + 88, dotted ? 0xFF55FF88 : 0xFFFF7777, false);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            SFMFontUtils.draw(poseStack, font, Component.literal("Examplexjava"), left + 16, top + 108,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            SFMFontUtils.draw(graphics, font, Component.literal("Examplexjava"), left + 16, top + 108,
{% endcase %}
                    0xFFFFFFFF, false);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            SFMFontUtils.draw(poseStack, font, Component.literal(missingDot ? "MATCH" : "NO MATCH"), left + 250,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            SFMFontUtils.draw(graphics, font, Component.literal(missingDot ? "MATCH" : "NO MATCH"), left + 250,
{% endcase %}
                    top + 108, missingDot ? 0xFFFF7777 : 0xFF55FF88, false);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            drawCentered(poseStack, Component.literal("Only '*' is wildcard syntax; '.' stays literal."),
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            drawCentered(graphics, Component.literal("Only '*' is wildcard syntax; '.' stays literal."),
{% endcase %}
                    width / 2, top + 134, 0xFFBBBBBB);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            super.render(poseStack, mouseX, mouseY, partialTick);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            super.render(graphics, mouseX, mouseY, partialTick);
{% when "26.1.2" %}
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
{% endcase %}
        }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        private void drawCentered(PoseStack poseStack, Component text, int centerX, int y, int colour) {
            SFMFontUtils.draw(poseStack, font, text, centerX - font.width(text) / 2, y, colour, false);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
        private void drawCentered(GuiGraphics graphics, Component text, int centerX, int y, int colour) {
            SFMFontUtils.draw(graphics, font, text, centerX - font.width(text) / 2, y, colour, false);
{% when "26.1.2" %}
        @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
        private void drawCentered(GuiGraphicsExtractor graphics, Component text, int centerX, int y, int colour) {
            SFMFontUtils.draw(graphics, font, text, centerX - font.width(text) / 2, y, colour, false);
{% endcase %}
        }
    }
}
