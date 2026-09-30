package ca.teamdman.sfm.common.command;

import ca.teamdman.sfm.SFM;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20' %}
{% when '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import ca.teamdman.sfm.client.export.ClientExportHelper;
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20' %}
{% when '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import com.mojang.brigadier.arguments.BoolArgumentType;
{% endcase %}
import com.mojang.brigadier.arguments.StringArgumentType;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20' %}
{% when '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
{% endcase %}
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.core.BlockPos;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.gametest.framework.TestFunction;
{% when '1.21', '1.21.1' %}
import net.minecraft.gametest.framework.*;
{% when '26.1.2' %}
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
{% endcase %}
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.server.permissions.PermissionSet;
{% endcase %}
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.server.command.EnumArgument;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.server.command.EnumArgument;
{% endcase %}

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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                             .requires(source -> source.hasPermission(Commands.LEVEL_ALL))
{% when '26.1.2' %}
                             .requires(Commands.hasPermission(Commands.LEVEL_ALL))
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                             .requires(source -> source.hasPermission(Commands.LEVEL_ALL))
{% when '26.1.2' %}
                             .requires(Commands.hasPermission(Commands.LEVEL_ALL))
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                             .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
{% when '26.1.2' %}
                             .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                                      .requires(source -> source.hasPermission(Commands.LEVEL_ALL))
{% when '26.1.2' %}
                                      .requires(Commands.hasPermission(Commands.LEVEL_ALL))
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                                                      .requires(source -> source.hasPermission(Commands.LEVEL_OWNERS))
{% when '26.1.2' %}
                                                      .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
{% endcase %}
                                                      .executes(new ConfigCommand(
                                                              ConfigCommandBehaviourInput.EDIT,
                                                              ConfigCommandVariantInput.SERVER
                                                      ))
                                      )
                                      .then(
                                              Commands.literal(ConfigCommandVariantInput.CLIENT.name())
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                                                      .requires(source -> source.hasPermission(Commands.LEVEL_ALL))
{% when '26.1.2' %}
                                                      .requires(Commands.hasPermission(Commands.LEVEL_ALL))
{% endcase %}
                                                      .executes(new ConfigCommand(
                                                              ConfigCommandBehaviourInput.EDIT,
                                                              ConfigCommandVariantInput.CLIENT
                                                      ))
                                      )
                        )
        );
        command.then(Commands.literal("changelog")
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                             .requires(source -> source.hasPermission(Commands.LEVEL_ALL))
{% when '26.1.2' %}
                             .requires(Commands.hasPermission(Commands.LEVEL_ALL))
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                             .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
{% when '26.1.2' %}
                             .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                                 .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
{% when '26.1.2' %}
                                 .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20' %}
{% when '1.20.1' %}
        if (SFMEnvironmentUtils.isClient()) {
            command.then(Commands.literal("export_info")
                                 .requires(source -> source.hasPermission(Commands.LEVEL_ALL))
                                 .then(Commands.argument("includeHidden", BoolArgumentType.bool())
                                               .executes(ctx -> {
                                                   boolean includeHidden = BoolArgumentType.getBool(
                                                           ctx,
                                                           "includeHidden"
                                                   );
                                                   SFM.LOGGER.info(
                                                           "Exporting info, includeHidden={} - slash command used by {}",
                                                           includeHidden,
                                                           ctx.getSource().getTextName()
                                                   );
                                                   assert Minecraft.getInstance().player != null;
                                                   new Thread(() -> {
                                                       try {
                                                           var start = System.currentTimeMillis();
                                                           Minecraft.getInstance().player.sendSystemMessage(
                                                                   Component.literal("Beginning item export")
                                                           );
                                                           ClientExportHelper.dumpItems(ctx.getSource().getPlayer());
                                                           Minecraft.getInstance().player.sendSystemMessage(
                                                                   Component.literal("Beginning JEI export")
                                                           );
                                                           ClientExportHelper.dumpJei(
                                                                   ctx.getSource().getPlayer(),
                                                                   includeHidden
                                                           );
                                                           var end = System.currentTimeMillis();
                                                           Minecraft.getInstance().player.sendSystemMessage(
                                                                   Component
                                                                           .literal("Exported data in "
                                                                                    + (end - start)
                                                                                    + "ms")
                                                                           .withStyle(ChatFormatting.GREEN)
                                                           );
                                                       } catch (Exception e) {
                                                           SFM.LOGGER.error("Failed to export item data", e);
                                                       }
                                                   }).start();
                                                   return SINGLE_SUCCESS;
                                               })));
        }
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        if (SFMEnvironmentUtils.isClient()) {
            command.then(Commands.literal("export_info")
                                 .requires(source -> source.hasPermission(Commands.LEVEL_ALL))
                                 .then(Commands.argument("includeHidden", BoolArgumentType.bool())
                                               .executes(ctx -> {
                                                   boolean includeHidden = BoolArgumentType.getBool(
                                                           ctx,
                                                           "includeHidden"
                                                   );
                                                   SFM.LOGGER.info(
                                                           "Exporting info, includeHidden={} - slash command used by {}",
                                                           includeHidden,
                                                           ctx.getSource().getTextName()
                                                   );
                                                   assert Minecraft.getInstance().player != null;
                                                   new Thread(() -> {
                                                       try {
                                                           var start = System.currentTimeMillis();
                                                           Minecraft.getInstance().player.sendSystemMessage(
                                                                   Component.literal("Beginning item export")
                                                           );
                                                           ClientExportHelper.dumpItems(ctx.getSource().getPlayer());
                                                           Minecraft.getInstance().player.sendSystemMessage(
                                                                   Component.literal("Beginning JEI export")
                                                           );
//                                                           ClientExportHelper.dumpJei(
//                                                                   ctx.getSource().getPlayer(),
//                                                                   includeHidden
//                                                           );
                                                           var end = System.currentTimeMillis();
                                                           Minecraft.getInstance().player.sendSystemMessage(
                                                                   Component
                                                                           .literal("Exported data in "
                                                                                    + (end - start)
                                                                                    + "ms")
                                                                           .withStyle(ChatFormatting.GREEN)
                                                           );
                                                       } catch (Exception e) {
                                                           SFM.LOGGER.error("Failed to export item data", e);
                                                       }
                                                   }).start();
                                                   return SINGLE_SUCCESS;
                                               })));
        }
{% when '26.1.2' %}
        if (SFMEnvironmentUtils.isClient()) {
            command.then(Commands.literal("export_info")
                                 .requires(Commands.hasPermission(Commands.LEVEL_ALL))
                                 .then(Commands.argument("includeHidden", BoolArgumentType.bool())
                                               .executes(ctx -> {
                                                   boolean includeHidden = BoolArgumentType.getBool(
                                                           ctx,
                                                           "includeHidden"
                                                   );
                                                   SFM.LOGGER.info(
                                                           "Exporting info, includeHidden={} - slash command used by {}",
                                                           includeHidden,
                                                           ctx.getSource().getTextName()
                                                   );
                                                   assert Minecraft.getInstance().player != null;
                                                   new Thread(() -> {
                                                       try {
                                                           var start = System.currentTimeMillis();
                                                           Minecraft.getInstance().player.sendSystemMessage(
                                                                   Component.literal("Beginning item export")
                                                           );
                                                           ClientExportHelper.dumpItems(ctx.getSource().getPlayer());
                                                           Minecraft.getInstance().player.sendSystemMessage(
                                                                   Component.literal("Beginning JEI export")
                                                           );
//                                                           ClientExportHelper.dumpJei(
//                                                                   ctx.getSource().getPlayer(),
//                                                                   includeHidden
//                                                           );
                                                           var end = System.currentTimeMillis();
                                                           Minecraft.getInstance().player.sendSystemMessage(
                                                                   Component
                                                                           .literal("Exported data in "
                                                                                    + (end - start)
                                                                                    + "ms")
                                                                           .withStyle(ChatFormatting.GREEN)
                                                           );
                                                       } catch (Exception e) {
                                                           SFM.LOGGER.error("Failed to export item data", e);
                                                       }
                                                   }).start();
                                                   return SINGLE_SUCCESS;
                                               })));
        }
{% endcase %}
        event.getDispatcher().register(command);
    }

    @MCVersionDependentBehaviour
    private static void sendSuccess(
            CommandSourceStack commandSourceStack,
            Supplier<Component> componentSupplier
    ) {

{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
        commandSourceStack.sendSuccess(componentSupplier.get(), true);
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
        commandSourceStack.sendSuccess(componentSupplier, true);
{% endcase %}
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

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        CommandSourceStack giveSource = source.withPermission(Commands.LEVEL_GAMEMASTERS);
{% when '26.1.2' %}
        CommandSourceStack giveSource = source.withPermission(PermissionSet.ALL_PERMISSIONS); // no clue if this is right
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        List<TestFunction> matchingTests = GameTestRegistry
                .getAllTestFunctions()
                .stream()
                .filter(testFunction -> matcher.test(testFunction.getTestName()))
{% when '1.21', '1.21.1' %}
        List<TestFunction> matchingTests = GameTestRegistry
                .getAllTestFunctions()
                .stream()
                .filter(testFunction -> matcher.test(testFunction.testName()))
{% when '26.1.2' %}
        List<Holder.Reference<GameTestInstance>> matchingTests = source.getServer()
                .registryAccess()
                .lookupOrThrow(Registries.TEST_INSTANCE)
                .listElements()
                .filter(ref -> matcher.test(ref.key().identifier().toString()))
{% endcase %}
                .toList();

        if (matchingTests.isEmpty()) {
            sendSuccess(source, () -> Component.literal("No tests matched pattern: " + wildcardPattern));
            return 0;
        }

        ServerLevel level = source.getLevel();
{% case minecraft_version %}
{% when '1.19.2' %}
        BlockPos sourcePos = new BlockPos(source.getPosition());
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
        BlockPos sourcePos = BlockPos.containing(source.getPosition());
{% endcase %}
        int surfaceY = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, sourcePos).getY();
        BlockPos startPos = new BlockPos(sourcePos.getX(), surfaceY, sourcePos.getZ() + 3);

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        GameTestRunner.clearMarkers(level);
{% when '26.1.2' %}
//        GameTestRunner.clearMarkers(level);
{% endcase %}
        runTests(matchingTests, startPos, level);

        sendSuccess(
                source,
                () -> Component.literal("Running " + matchingTests.size() + " tests matching '" + wildcardPattern + "'")
        );
        return SINGLE_SUCCESS;
    }

    @MCVersionDependentBehaviour
    private static void runTests(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            List<TestFunction> matchingTests,
{% when '26.1.2' %}
            List<Holder.Reference<GameTestInstance>> matchingTests,
{% endcase %}
            BlockPos startPos,
            ServerLevel level
    ) {

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        GameTestRunner.runTests(
                matchingTests,
                startPos,
                Rotation.NONE,
                level,
                GameTestTicker.SINGLETON,
                8
        );
{% when '1.21', '1.21.1', '26.1.2' %}
        var gameTestInfos = matchingTests
                .stream()
                .map(testFunction -> new GameTestInfo(testFunction, Rotation.NONE, level, RetryOptions.noRetries()))
                .toList();

        GameTestRunner.Builder.fromInfo(gameTestInfos, level)
                .newStructureSpawner(new StructureGridSpawner(startPos, 8, false))
                .build()
                .start();
{% endcase %}
    }

    private static String wildcardToRegex(String wildcardPattern) {

        return wildcardPattern.replace("*", ".*");
    }

}
