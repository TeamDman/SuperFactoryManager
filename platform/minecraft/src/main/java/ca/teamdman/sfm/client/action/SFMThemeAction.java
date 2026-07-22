package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import ca.teamdman.sfm.client.theme.SFMClientThemeService;
import ca.teamdman.sfm.client.theme.SFMThemeLoadResult;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;

import java.util.Optional;

public final class SFMThemeAction implements SFMClientAction<SFMClientActionContext> {
    public enum Operation { RELOAD, RESTORE_DEFAULTS, OPEN_FILE }

    private final Operation operation;

    public SFMThemeAction(Operation operation) {
        this.operation = operation;
    }

    @Override
    public Component title() {
        return Component.literal(switch (operation) {
            case RELOAD -> "Reload client theme";
            case RESTORE_DEFAULTS -> "Restore default client theme";
            case OPEN_FILE -> "Open client theme TOML";
        });
    }

    @Override
    public Component description() {
        return Component.literal(switch (operation) {
            case RELOAD -> "Atomically reload colours, syntax styles, and item icons";
            case RESTORE_DEFAULTS -> "Replace the active theme with SFM's shipped defaults";
            case OPEN_FILE -> "Open the active theme file using the operating system";
        });
    }

    @Override
    public Optional<SFMItemIcon> itemIcon(SFMClientActionContext context) {
        return Optional.of(SFMItemIcon.vanilla(switch (operation) {
            case RELOAD -> "clock";
            case RESTORE_DEFAULTS -> "barrier";
            case OPEN_FILE -> "writable_book";
        }, title().getString()));
    }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return SFMClientActionAvailability::available;
    }

    @Override
    public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> context) {
        if (operation == Operation.OPEN_FILE) {
            SFMThemeLoadResult prepared = SFMClientThemeService.reload();
            if (!prepared.valid()) return report(prepared, context);
            Util.getPlatform().openFile(SFMClientThemeService.activeThemePath().toFile());
            context.getSource().sendFeedback(Component.literal("Opened " + SFMClientThemeService.activeThemePath()));
            return 1;
        }
        SFMThemeLoadResult result = operation == Operation.RELOAD
                ? SFMClientThemeService.reload()
                : SFMClientThemeService.restoreDefaults();
        if (!result.valid()) return report(result, context);
        context.getSource().sendFeedback(Component.literal(operation == Operation.RELOAD
                ? "Client theme reloaded"
                : "Default client theme restored"));
        return 1;
    }

    private static int report(SFMThemeLoadResult result, CommandContext<SFMClientActionSource> context) {
        result.diagnostics().forEach(diagnostic ->
                context.getSource().sendFeedback(Component.literal("Theme: " + diagnostic)));
        return 0;
    }
}
