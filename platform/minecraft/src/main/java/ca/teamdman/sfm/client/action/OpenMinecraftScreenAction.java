package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.controls.ControlsScreen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Opens a named built-in Minecraft screen through one typed action family. */
public final class OpenMinecraftScreenAction implements SFMClientAction<SFMClientActionContext> {
    @Override
    public Component title() {
        return Component.literal("Open Minecraft screen");
    }

    @Override
    public Component description() {
        return Component.literal("Open a named built-in Minecraft screen");
    }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return context -> context.originatingHostIsCurrent().getAsBoolean()
                ? SFMClientActionAvailability.available(context)
                : SFMClientActionAvailability.unavailable(
                        SFMClientActionContext.ORIGINATING_HOST_CHANGED.getComponent());
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.then(LiteralArgumentBuilder.<SFMClientActionSource>literal("TitleScreen")
                .executes(context -> open(context, Target.TITLE)));
        node.then(LiteralArgumentBuilder.<SFMClientActionSource>literal("ControlsScreen")
                .executes(context -> open(context, Target.CONTROLS)));
        node.then(LiteralArgumentBuilder.<SFMClientActionSource>literal("KeyBindsScreen")
                .executes(context -> open(context, Target.KEY_BINDS)));
    }

    @Override
    public int execute(
            SFMClientActionContext target,
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        throw new SimpleCommandExceptionType(Component.literal(
                "Choose TitleScreen, ControlsScreen, or KeyBindsScreen")).create();
    }

    private int open(CommandContext<SFMClientActionSource> context, Target target) throws CommandSyntaxException {
        Minecraft minecraft = Minecraft.getInstance();
        @Nullable Screen parent = persistentOrigin(context.getSource().context());
        Screen destination = switch (target) {
            case TITLE -> {
                if (minecraft.level != null) {
                    throw new SimpleCommandExceptionType(Component.literal(
                            "TitleScreen is unavailable while a world is loaded; leave the world first")).create();
                }
                yield new TitleScreen();
            }
            case CONTROLS -> new ControlsScreen(parent, minecraft.options);
            case KEY_BINDS -> new KeyBindsScreen(parent, minecraft.options);
        };
        SFMScreenChangeHelpers.setScreen(destination);
        return 1;
    }

    private static @Nullable Screen persistentOrigin(SFMClientActionContext context) {
        @Nullable Screen origin = context.originatingHost() instanceof Screen screen ? screen : null;
        for (int depth = 0; depth < 16 && origin instanceof SFMCommandPaletteScreen palette; depth++) {
            Object parent = palette.originatingActionContext().originatingHost();
            if (!(parent instanceof Screen parentScreen) || parentScreen == origin) return null;
            origin = parentScreen;
        }
        return origin;
    }

    private enum Target {
        TITLE,
        CONTROLS,
        KEY_BINDS
    }
}
