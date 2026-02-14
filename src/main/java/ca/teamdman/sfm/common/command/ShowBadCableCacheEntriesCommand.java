package ca.teamdman.sfm.common.command;

import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

public class ShowBadCableCacheEntriesCommand extends CommandBase {
    @Override
    public String getName() {
        return "show_bad_cable_cache_entries";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/sfm show_bad_cable_cache_entries";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        World level = sender.getEntityWorld();
        CableNetworkManager.getBadCableCachePositions(level).forEach(pos -> {
            // TODO: implement
        });
    }
}
