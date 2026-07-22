package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.theme.SFMClientTheme;
import ca.teamdman.sfm.client.theme.SFMClientThemeService;
import ca.teamdman.sfm.client.theme.SFMColourRole;
import ca.teamdman.sfm.client.theme.SFMThemeLoadResult;
import ca.teamdman.sfm.client.theme.SFMSyntaxStyle;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public record ShowRuntimeThemePuppetAction(View view) implements SFMPuppetAction {
    public enum View { DEFAULT, SWITCHED, SYNTAX, MALFORMED }

    private static final String HIGH_CONTRAST_TOML = """
            schema_version = 1
            [icons.actions]
            "sfm:theme/reload" = "minecraft:amethyst_shard"
            "sfm:theme/restore_defaults" = "minecraft:honeycomb"
            "sfm:theme/open_file" = "minecraft:emerald"
            [syntax.sfml]
            keyword = { colour = "#FFFFD700", bold = true }
            string = { colour = "#FFFF80C0", italic = true }
            [colours]
            "screen.overlay" = "#990C1020"
            "panel.background" = "#FF102A43"
            "panel.border" = "#FFFFD166"
            "panel.selection" = "#FF295E88"
            "text.primary" = "#FFFFFFFF"
            "text.muted" = "#FF9FE7F5"
            "text.accent" = "#FFFFD166"
            "timeline.track" = "#FF295E88"
            "timeline.keyframe" = "#FFFFD166"
            "timeline.time" = "#FFFF80C0"
            """;

    @Override
    public String description() {
        return "show runtime theme fixture " + view;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (!(Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette)) {
            throw new IllegalStateException("Expected command palette while applying runtime theme fixture");
        }
        switch (view) {
            case DEFAULT -> {
                SFMClientThemeService.reloadText(SFMClientThemeService.DEFAULT_TOML);
                palette.showThemeFeedbackForAutomation(List.of(
                        "Default theme snapshot active",
                        "NightConfig -> immutable render snapshot"
                ));
            }
            case SWITCHED -> {
                SFMThemeLoadResult result = SFMClientThemeService.reloadText(HIGH_CONTRAST_TOML);
                if (!result.valid()) throw new IllegalStateException("Theme fixture rejected: " + result.diagnostics());
                palette.showThemeFeedbackForAutomation(List.of(
                        "Runtime switch accepted without restart",
                        "Palette, timeline, syntax, and icons updated"
                ));
            }
            case SYNTAX -> {
                SFMSyntaxStyle keyword = SFMClientThemeService.active().syntax("keyword");
                SFMSyntaxStyle string = SFMClientThemeService.active().syntax("string");
                if (keyword.colour() != 0xFFFFD700 || !keyword.bold()
                        || string.colour() != 0xFFFF80C0 || !string.italic()) {
                    throw new IllegalStateException("Expected switched syntax styles to remain active");
                }
                palette.showThemeSyntaxForAutomation("""
                        NAME "runtime theme"
                        EVERY 20 TICKS DO
                            INPUT minecraft:iron_ingot FROM input
                            OUTPUT "minecraft:gold_ingot" TO output
                        END
                        """);
            }
            case MALFORMED -> {
                SFMClientTheme before = SFMClientThemeService.active();
                int beforePanel = before.colour(SFMColourRole.PANEL_BACKGROUND);
                SFMThemeLoadResult result = SFMClientThemeService.reloadText("schema_version = [");
                if (result.valid()) throw new IllegalStateException("Malformed theme unexpectedly loaded");
                if (SFMClientThemeService.active() != before
                        || SFMClientThemeService.active().colour(SFMColourRole.PANEL_BACKGROUND) != beforePanel) {
                    throw new IllegalStateException("Malformed theme replaced the last valid snapshot");
                }
                List<String> feedback = new ArrayList<>();
                feedback.add("Reload rejected - previous colours preserved");
                result.diagnostics().stream().limit(2).forEach(feedback::add);
                palette.showThemeFeedbackForAutomation(feedback);
            }
        }
        palette.setInputForAutomation(view == View.SYNTAX ? "sfm action invoke " : "sfm action invoke sfm:theme/");
        return true;
    }
}
