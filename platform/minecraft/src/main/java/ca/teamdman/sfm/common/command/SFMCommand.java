package ca.teamdman.sfm.common.command;

import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;

@SuppressWarnings({"LoggingSimilarMessage", "DuplicatedCode"})
public class SFMCommand {
    @SFMSubscribeEvent
    public static void onRegisterCommand(final RegisterCommandsEvent event) {
        var command = Commands.literal("sfm");
        command.then(SFMCacheCommands.bustCableNetworkCache());
        command.then(SFMCacheCommands.bustWaterNetworkCache());
        command.then(SFMCacheCommands.showBadCableCacheEntries(event));
        command.then(SFMConfigCommands.build());
        command.then(SFMChangelogCommand.build());
        command.then(SFMKitCommand.build());
        command.then(SFMTutorialCommand.build());

        if (SFMEnvironmentUtils.isInIDE()) {
            command.then(SFMGameTestCommand.build());
        }

        event.getDispatcher().register(command);
    }
}
