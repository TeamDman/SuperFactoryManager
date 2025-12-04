package ca.teamdman.sfm.common.command;

import net.minecraft.command.ICommandSender;
import net.minecraftforge.server.command.CommandTreeBase;

public class SFMCommand extends CommandTreeBase {

    public SFMCommand() {
        super();
        this.addSubcommand(new BustCableNetworkCacheCommand());
        this.addSubcommand(new ShowBadCableCacheEntriesCommand());
        this.addSubcommand(new ChangelogCommand());
    }


    @Override
    public String getName() {
        return "sfm";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/sfm (bust_cable_network_cache|show_bad_cable_cache_entries|changelog)";
    }
}
