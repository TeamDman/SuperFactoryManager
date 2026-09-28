package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.terminal.SFMTerminalServiceFactory;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;

/** Opens the Java-only terminal without probing or requiring Rust. */
public final class OpenReplAction implements SFMClientAction<SFMClientActionContext> {
    @Override
    public Component title() { return Component.literal("Open Java REPL"); }

    @Override
    public Component description() { return Component.literal("Open the Java-only terminal, independent of Rust/Vox"); }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return SFMClientActionAvailability::available;
    }

    @Override
    public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> context) {
        return OpenTerminalAction.open(target, SFMTerminalServiceFactory.createRepl());
    }
}
