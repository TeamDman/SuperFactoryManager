package ca.teamdman.sfm.common.command;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

public class BustCableNetworkCacheCommand extends CommandBase {

    @Override
    public String getName() {
        return "bust_cable_network_cache";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/sfm bust_cable_network_cache";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        SFM.LOGGER.info(
                "Busting cable networks - slash command used by {}",
                sender.getName()
        );
        notifyCommandListener(sender, this, LocalizationKeys.COMMAND_BUST_CABLE_NETWORK_CACHE_SUCCESS.key().get());
        CableNetworkManager.clear();
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 3;
    }
}
