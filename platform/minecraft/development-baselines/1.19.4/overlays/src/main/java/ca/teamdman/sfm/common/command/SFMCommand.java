package ca.teamdman.sfm.common.command;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.block_network.WaterNetworkManager;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.net.ClientboundShowChangelogPacket;
import ca.teamdman.sfm.common.program.RegexCache;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.server.command.EnumArgument;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

@SuppressWarnings({"LoggingSimilarMessage", "DuplicatedCode"})
public class SFMCommand {
    @SFMLocalizationDatagen
    public static final LocalizationEntry COMMAND_BUST_WATER_NETWORK_CACHE_SUCCESS = new LocalizationEntry(
            "sfm.command.bust_water_network_cache.success",
            "Successfully busted water network cache."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry COMMAND_BUST_CABLE_NETWORK_CACHE_SUCCESS = new LocalizationEntry(
            "sfm.command.bust_cable_network_cache.success",
            "Successfully busted cable network cache."
    );

    @SFMSubscribeEvent
    public static void onRegisterCommand(final RegisterCommandsEvent event) {

        var command = Commands.literal("sfm");
        command.then(Commands.literal("bust_cable_network_cache")
                             .requires(source -> source.hasPermission(Commands.LEVEL_ALL))
                             .executes(ctx -> {
                                 CommandSourceStack source = ctx.getSource();
                                 SFM.LOGGER.info(
                                         "Busting cable networks - slash command used by {}",
                                         source.getTextName()
                                 );
                                 CableNetworkManager.clear();
                                 sendSuccess(source, COMMAND_BUST_CABLE_NETWORK_CACHE_SUCCESS::getComponent);
                                 return SINGLE_SUCCESS;
                             }));
        command.then(Commands.literal("bust_water_network_cache")
                             .requires(source -> source.hasPermission(Commands.LEVEL_ALL))
                             .executes(ctx -> {
                                 CommandSourceStack source = ctx.getSource();
                                 SFM.LOGGER.info(
                                         "Busting water networks - slash command used by {}",
                                         source.getTextName()
                                 );
                                 WaterNetworkManager.clear();
                                 sendSuccess(source, COMMAND_BUST_WATER_NETWORK_CACHE_SUCCESS::getComponent);
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
        command.then(Commands.literal("kit")
                             .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                             .executes(ctx -> giveKitToPlayers(
                                     ctx.getSource(),
                                     List.of(ctx.getSource().getPlayerOrException())
                             ))
                             .then(Commands.argument("targets", EntityArgument.players())
                                           .executes(ctx -> giveKitToPlayers(
                                                   ctx.getSource(),
                                                   EntityArgument.getPlayers(ctx, "targets")
                                           ))));
        if (SFMEnvironmentUtils.isInIDE()) {
            command.then(Commands.literal("test")
                                 .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                 .then(Commands.literal("run")
                                               .then(Commands.argument("pattern", StringArgumentType.greedyString())
                                                             .executes(ctx -> {
                                                                 var source = ctx.getSource();
                                                                 var wildcardPattern = StringArgumentType.getString(
                                                                         ctx,
                                                                         "pattern"
                                                                 );
                                                                 return runTestsByWildcard(source, wildcardPattern);
                                                             }))));
        }
        event.getDispatcher().register(command);
    }

    @MCVersionDependentBehaviour
    private static void sendSuccess(
            CommandSourceStack commandSourceStack,
            Supplier<Component> componentSupplier
    ) {

        commandSourceStack.sendSuccess(componentSupplier.get(), true);
    }

    private static int giveKitToPlayers(
            CommandSourceStack source,
            Collection<ServerPlayer> targets
    ) {

        List<ItemStack> kitItems = List.of(
                new ItemStack(SFMItems.LABEL_GUN.get()),
                new ItemStack(SFMItems.MANAGER.get()),
                new ItemStack(SFMItems.DISK.get()),
                new ItemStack(SFMItems.NETWORK_TOOL.get()),
                new ItemStack(SFMItems.CABLE.get()),
                new ItemStack(Items.CHEST)
        );

        CommandSourceStack giveSource = source.withPermission(Commands.LEVEL_GAMEMASTERS);
        for (ServerPlayer target : targets) {
            for (ItemStack kitItem : kitItems) {
                var itemId = SFMWellKnownRegistries.ITEMS.getId(kitItem.getItem());
                if (itemId == null) {
                    SFM.LOGGER.warn("Skipping kit item without registry id: {}", kitItem);
                    continue;
                }

                String command = "give " + target.getScoreboardName() + " " + itemId + " " + kitItem.getCount();
                source.getServer().getCommands().performPrefixedCommand(giveSource, command);
            }
        }

        sendSuccess(source, () -> Component.literal("Gave SFM kit to " + targets.size() + " player(s)."));
        return targets.size();
    }

    private static int runTestsByWildcard(
            CommandSourceStack source,
            String wildcardPattern
    ) {

        var matcher = RegexCache.buildPredicate(wildcardToRegex(wildcardPattern));
        List<TestFunction> matchingTests = GameTestRegistry
                .getAllTestFunctions()
                .stream()
                .filter(testFunction -> matcher.test(testFunction.getTestName()))
                .toList();

        if (matchingTests.isEmpty()) {
            sendSuccess(source, () -> Component.literal("No tests matched pattern: " + wildcardPattern));
            return 0;
        }

        ServerLevel level = source.getLevel();
        BlockPos sourcePos = BlockPos.containing(source.getPosition());
        int surfaceY = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, sourcePos).getY();
        BlockPos startPos = new BlockPos(sourcePos.getX(), surfaceY, sourcePos.getZ() + 3);

        GameTestRunner.clearMarkers(level);
        runTests(matchingTests, startPos, level);

        sendSuccess(
                source,
                () -> Component.literal("Running " + matchingTests.size() + " tests matching '" + wildcardPattern + "'")
        );
        return SINGLE_SUCCESS;
    }

    @MCVersionDependentBehaviour
    private static void runTests(
            List<TestFunction> matchingTests,
            BlockPos startPos,
            ServerLevel level
    ) {

        GameTestRunner.runTests(
                matchingTests,
                startPos,
                Rotation.NONE,
                level,
                GameTestTicker.SINGLETON,
                8
        );
    }

    private static String wildcardToRegex(String wildcardPattern) {

        return wildcardPattern.replace("*", ".*");
    }

}
