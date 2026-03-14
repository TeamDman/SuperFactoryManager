package ca.teamdman.sfm.common.command;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.cablenetwork.CableNetworkManager;
import ca.teamdman.sfm.common.net.ClientboundResetTextEditorPreferencesPacket;
import ca.teamdman.sfm.common.net.ClientboundShowChangelogPacket;
import ca.teamdman.sfm.common.registry.SFMPackets;
import ca.teamdman.sfm.common.watertanknetwork.WaterNetworkManager;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.command.EnumArgument;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

@SuppressWarnings({"LoggingSimilarMessage", "DuplicatedCode"})
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, modid = SFM.MOD_ID)
public class SFMCommand {
    @SubscribeEvent
    public static void onRegisterCommand(final RegisterCommandsEvent event) {
        var command = Commands.literal("sfm");
        command.then(Commands.literal("bust_cable_network_cache")
                             .requires(source -> source.hasPermission(Commands.LEVEL_ALL))
                             .executes(ctx -> {
                                 SFM.LOGGER.info(
                                         "Busting cable networks - slash command used by {}",
                                         ctx.getSource().getTextName()
                                 );
                                 CableNetworkManager.clear();
                                 return SINGLE_SUCCESS;
                             }));
        command.then(Commands.literal("bust_water_network_cache")
                             .requires(source -> source.hasPermission(Commands.LEVEL_ALL))
                             .executes(ctx -> {
                                 SFM.LOGGER.info(
                                         "Busting water networks - slash command used by {}",
                                         ctx.getSource().getTextName()
                                 );
                                 WaterNetworkManager.clear();
                                 return SINGLE_SUCCESS;
                             }));
        command.then(Commands.literal("show_bad_cable_cache_entries")
                             .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                             .then(Commands.argument("block", BlockStateArgument.block(event.getBuildContext()))
                                           .executes(ctx -> {
                                               ServerLevel level = ctx.getSource().getLevel();
                                               CableNetworkManager.getBadCableCachePositions(level).forEach(pos -> {
                                                   BlockInput block = BlockStateArgument
                                                           .getBlock(
                                                                   ctx,
                                                                   "block"
                                                           );
                                                   block.place(
                                                           level,
                                                           pos,
                                                           Block.UPDATE_ALL
                                                   );
                                               });
                                               return SINGLE_SUCCESS;
                                           })));
        command.then(
                Commands.literal("config")
                        .then(Commands.literal("show")
                                      .requires(source -> source.hasPermission(Commands.LEVEL_ALL))
                                      .then(Commands
                                                    .argument(
                                                            "variant",
                                                            EnumArgument.enumArgument(ConfigCommandVariantInput.class)
                                                    )
                                                    .executes(ctx -> new ConfigCommand(
                                                            ConfigCommandBehaviourInput.SHOW,
                                                            ctx.getArgument(
                                                                    "variant",
                                                                    ConfigCommandVariantInput.class
                                                            )
                                                    ).run(ctx))
                                      )
                        )
                        .then(Commands.literal("edit")
                                      .then(
                                              Commands.literal(ConfigCommandVariantInput.SERVER.name())
                                                      .requires(source -> source.hasPermission(Commands.LEVEL_OWNERS))
                                                      .executes(new ConfigCommand(
                                                              ConfigCommandBehaviourInput.EDIT,
                                                              ConfigCommandVariantInput.SERVER
                                                      ))
                                      )
                                      .then(
                                              Commands.literal(ConfigCommandVariantInput.CLIENT.name())
                                                      .requires(source -> source.hasPermission(Commands.LEVEL_ALL))
                                                      .executes(new ConfigCommand(
                                                              ConfigCommandBehaviourInput.EDIT,
                                                              ConfigCommandVariantInput.CLIENT
                                                      ))
                                      )
                                      .then(
                                              Commands.literal(ConfigCommandVariantInput.TEXT.name())
                                                      .requires(source -> source.hasPermission(Commands.LEVEL_ALL))
                                                      .executes(new ConfigCommand(
                                                              ConfigCommandBehaviourInput.EDIT,
                                                              ConfigCommandVariantInput.TEXT
                                                      ))
                                      )
                        )
        );
        command.then(Commands.literal("changelog")
                             .requires(source -> source.hasPermission(Commands.LEVEL_ALL))
                             .executes(ctx -> {
                                 ServerPlayer player = ctx.getSource().getPlayer();
                                 if (player != null) {
                                     // I tried making this a client command by registering in the client command event
                                     // but what happened was that when the command is sent in the chat
                                     // the mc logic is to set the screen to null after the command executes to close the chat
                                     // which closes the changelog gui
                                     // so doing it this way will keep the screen open lol
                                     SFMPackets.sendToPlayer(
                                             player,
                                             new ClientboundShowChangelogPacket()
                                     );
                                 }
                                 return SINGLE_SUCCESS;
                             }));
        command.then(Commands.literal("reset_text_editor_preferences")
                             .requires(source -> source.hasPermission(Commands.LEVEL_ALL))
                             .executes(ctx -> {
                                 ServerPlayer player = ctx.getSource().getPlayer();
                                 if (player != null) {
                                     SFM.LOGGER.info(
                                             "Resetting text editor preferences to V1 for player {}",
                                             player.getName().getString()
                                     );
                                     SFMPackets.sendToPlayer(
                                             player,
                                             new ClientboundResetTextEditorPreferencesPacket()
                                     );
                                 }
                                 return SINGLE_SUCCESS;
                             }));
        event.getDispatcher().register(command);
    }

}
