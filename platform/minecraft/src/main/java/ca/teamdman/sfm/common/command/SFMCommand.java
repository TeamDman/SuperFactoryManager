package ca.teamdman.sfm.common.command;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.block_network.WaterNetworkManager;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.command.draw.CapturingDrawCommandSource;
import ca.teamdman.sfm.common.command.draw.DrawCommandClientContext;
import ca.teamdman.sfm.common.command.draw.SFMDrawCommandCompletionCatalog;
import ca.teamdman.sfm.common.command.draw.DrawManagerProgramCard;
import ca.teamdman.sfm.common.command.draw.DrawTemplateProgramCard;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.net.ClientboundShowChangelogPacket;
import ca.teamdman.sfm.common.program.RegexCache;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.template.SFMDrawTemplate;
import ca.teamdman.sfm.common.template.SFMDrawTemplateRegistry;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import ca.teamdman.sfml.program_builder.ProgramBuilder;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.server.command.EnumArgument;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

@SuppressWarnings({"LoggingSimilarMessage", "DuplicatedCode"})
public class SFMCommand {
        private static final List<String> DRAW_VEC2_SWIZZLES = List.of("x", "y", "xy", "yx");
        private static final List<String> DRAW_VEC3_SWIZZLES = List.of(
                        "x",
                        "y",
                        "z",
                        "xy",
                        "xz",
                        "yx",
                        "yz",
                        "zx",
                        "zy",
                        "xyz",
                        "xzy",
                        "yxz",
                        "yzx",
                        "zxy",
                        "zyx"
        );

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
        var drawPlayerPosCommand = addSwizzleSubcommands(
                Commands.literal("pos")
                        .executes(ctx -> runDrawPlayerPos(ctx.getSource())),
                DRAW_VEC3_SWIZZLES,
                SFMCommand::runDrawPlayerPosSwizzle
        );
        var drawPlayerLookHitCommand = addSwizzleSubcommands(
                Commands.literal("hit")
                        .executes(ctx -> runDrawPlayerLookHit(ctx.getSource()))
                        .then(Commands.literal("block")
                                      .executes(ctx -> runDrawPlayerLookHitBlock(ctx.getSource())))
                        .then(Commands.literal("manager")
                                      .executes(ctx -> runDrawPlayerLookHitManager(ctx.getSource()))),
                DRAW_VEC3_SWIZZLES,
                SFMCommand::runDrawPlayerLookHitSwizzle
        );
        var drawPlayerCommand = Commands.literal("player")
                .executes(ctx -> runDrawPlayer(ctx.getSource()))
                .then(drawPlayerPosCommand)
                .then(Commands.literal("angle")
                              .executes(ctx -> runDrawPlayerAngle(ctx.getSource())))
                .then(Commands.literal("dimension")
                              .executes(ctx -> runDrawPlayerDimension(ctx.getSource())))
                .then(Commands.literal("look")
                              .executes(ctx -> runDrawPlayerLook(ctx.getSource()))
                              .then(Commands.literal("angle")
                                            .executes(ctx -> runDrawPlayerLookAngle(ctx.getSource())))
                              .then(drawPlayerLookHitCommand))
                .then(Commands.literal("inv")
                              .executes(ctx -> runDrawPlayerInventory(ctx.getSource(), null))
                              .then(Commands.argument("slot", IntegerArgumentType.integer(0, 35))
                                            .executes(ctx -> runDrawPlayerInventory(
                                                    ctx.getSource(),
                                                    IntegerArgumentType.getInteger(ctx, "slot")
                                            ))))
                .then(Commands.literal("armor")
                              .executes(ctx -> runDrawPlayerArmor(ctx.getSource(), null))
                              .then(Commands.argument("slot", IntegerArgumentType.integer(0, 3))
                                            .executes(ctx -> runDrawPlayerArmor(
                                                    ctx.getSource(),
                                                    IntegerArgumentType.getInteger(ctx, "slot")
                                            ))))
                .then(Commands.literal("hand")
                              .executes(ctx -> runDrawPlayerHands(ctx.getSource()))
                              .then(Commands.literal("mainhand")
                                            .executes(ctx -> runDrawPlayerHand(ctx.getSource(), InteractionHand.MAIN_HAND)))
                              .then(Commands.literal("offhand")
                                            .executes(ctx -> runDrawPlayerHand(ctx.getSource(), InteractionHand.OFF_HAND))));
        var drawCameraCommand = Commands.literal("camera")
                .then(addSwizzleSubcommands(
                        Commands.literal("pos")
                                .executes(ctx -> runDrawCameraPos(ctx.getSource())),
                        DRAW_VEC2_SWIZZLES,
                        SFMCommand::runDrawCameraPosSwizzle
                ))
                .then(Commands.literal("zoom")
                              .executes(ctx -> runDrawCameraZoom(ctx.getSource())));
        var drawMouseCommand = Commands.literal("mouse")
                .then(addSwizzleSubcommands(
                        Commands.literal("pos")
                                .executes(ctx -> runDrawMousePos(ctx.getSource())),
                        DRAW_VEC2_SWIZZLES,
                        SFMCommand::runDrawMousePosSwizzle
                ))
                .then(addSwizzleSubcommands(
                        Commands.literal("screen_pos")
                                .executes(ctx -> runDrawMouseScreenPos(ctx.getSource())),
                        DRAW_VEC2_SWIZZLES,
                        SFMCommand::runDrawMouseScreenPosSwizzle
                ));
        var drawTemplateCommand = Commands.literal("template")
                .then(Commands.literal("list")
                              .executes(ctx -> runDrawTemplateList(ctx.getSource())))
                .then(Commands.literal("open")
                              .then(Commands.argument("template", StringArgumentType.greedyString())
                                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                    SFMDrawTemplateRegistry.templateKeys(),
                                                    builder
                                            ))
                                            .executes(ctx -> runDrawTemplateOpen(
                                                    ctx.getSource(),
                                                    StringArgumentType.getString(ctx, "template")
                                            ))));
        var drawOpenCommand = Commands.literal("open")
                .executes(ctx -> runDrawLocalOnlyCommand(
                        ctx.getSource(),
                        List.of("/sfm draw open [path]")
                ))
                .then(Commands.argument("path", StringArgumentType.greedyString())
                              .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                      SFMDrawCommandCompletionCatalog.canvasPathSuggestions(),
                                      builder
                              ))
                              .executes(ctx -> runDrawLocalOnlyCommand(
                                      ctx.getSource(),
                                      List.of("/sfm draw open [path]")
                              )));
        var drawMoveCommand = Commands.literal("move")
                .then(Commands.argument("from", StringArgumentType.string())
                              .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                      SFMDrawCommandCompletionCatalog.canvasPathSuggestions(),
                                      builder
                              ))
                              .then(Commands.argument("to", StringArgumentType.greedyString())
                                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                    SFMDrawCommandCompletionCatalog.canvasPathSuggestions(),
                                                    builder
                                            ))
                                            .executes(ctx -> runDrawLocalOnlyCommand(
                                                    ctx.getSource(),
                                                    List.of("/sfm draw move <from> <to>")
                                            ))));
        var drawLsCommand = Commands.literal("ls")
                .executes(ctx -> runDrawLocalOnlyCommand(
                        ctx.getSource(),
                        List.of("/sfm draw ls [path]")
                ))
                .then(Commands.argument("path", StringArgumentType.greedyString())
                              .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                      SFMDrawCommandCompletionCatalog.browsePathSuggestions(),
                                      builder
                              ))
                              .executes(ctx -> runDrawLocalOnlyCommand(
                                      ctx.getSource(),
                                      List.of("/sfm draw ls [path]")
                              )));
        var drawBoxCommand = Commands.literal("box")
                .then(Commands.literal("list")
                              .executes(ctx -> runDrawLocalOnlyCommand(
                                      ctx.getSource(),
                                      List.of("/sfm draw box list")
                              )))
                .then(
                        Commands.argument("x1", DoubleArgumentType.doubleArg())
                                .then(
                                        Commands.argument("y1", DoubleArgumentType.doubleArg())
                                                .then(
                                                        Commands.argument("x2", DoubleArgumentType.doubleArg())
                                                                .then(
                                                                        Commands.argument("y2", DoubleArgumentType.doubleArg())
                                                                                .executes(ctx -> runDrawLocalOnlyCommand(
                                                                                        ctx.getSource(),
                                                                                        List.of("/sfm draw box <x1> <y1> <x2> <y2>")
                                                                                ))
                                                                )
                                                )
                                )
                );
        var drawConcatenateCommand = Commands.literal("concatenate")
                .executes(ctx -> runDrawLocalOnlyCommand(
                        ctx.getSource(),
                        List.of("/sfm draw concatenate @rect[x,y]", "/sfm draw concatenate @rel[dx,dy]")
                ))
                .then(Commands.argument("selector", StringArgumentType.greedyString())
                              .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                      List.of("@rect[0,4]", "@rel[0,-10]"),
                                      builder
                              ))
                              .executes(ctx -> runDrawLocalOnlyCommand(
                                      ctx.getSource(),
                                      List.of("/sfm draw concatenate @rect[x,y]", "/sfm draw concatenate @rel[dx,dy]")
                              )));
        var drawOllamaCommand = Commands.literal("ollama")
                .executes(ctx -> runDrawLocalOnlyCommand(
                        ctx.getSource(),
                        List.of(
                                "/sfm draw ollama models",
                                "/sfm draw ollama run <model> <prompt>"
                        )
                ))
                .then(Commands.literal("models")
                              .executes(ctx -> runDrawLocalOnlyCommand(
                                      ctx.getSource(),
                                      List.of("/sfm draw ollama models")
                              )))
                .then(Commands.literal("list")
                              .executes(ctx -> runDrawLocalOnlyCommand(
                                      ctx.getSource(),
                                      List.of("/sfm draw ollama models")
                              )))
                .then(Commands.literal("run")
                              .then(Commands.argument("model", StringArgumentType.string())
                                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                    SFMDrawCommandCompletionCatalog.ollamaModelFallbacks(),
                                                    builder
                                            ))
                                            .then(Commands.argument("prompt", StringArgumentType.greedyString())
                                                          .executes(ctx -> runDrawLocalOnlyCommand(
                                                                  ctx.getSource(),
                                                                  List.of("/sfm draw ollama run <model> <prompt>")
                                                          )))));
        command.then(
                Commands.literal("draw")
                        .requires(source -> source.hasPermission(Commands.LEVEL_ALL))
                        .then(Commands.literal("help")
                                      .executes(ctx -> runDrawHelp(ctx.getSource())))
                        .then(Commands.literal("echo")
                                      .executes(ctx -> runDrawEcho(ctx.getSource(), ""))
                                      .then(Commands.argument("message", StringArgumentType.greedyString())
                                                    .executes(ctx -> runDrawEcho(
                                                            ctx.getSource(),
                                                            StringArgumentType.getString(ctx, "message")
                                                    ))))
                        .then(drawOpenCommand)
                        .then(drawMoveCommand)
                        .then(drawLsCommand)
                        .then(drawBoxCommand)
                        .then(drawConcatenateCommand)
                        .then(drawOllamaCommand)
                        .then(drawPlayerCommand)
                        .then(drawCameraCommand)
                        .then(drawMouseCommand)
                        .then(drawTemplateCommand)
        );
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

        private static LiteralArgumentBuilder<CommandSourceStack> addSwizzleSubcommands(
                        LiteralArgumentBuilder<CommandSourceStack> builder,
                        List<String> swizzles,
                        BiFunction<CommandSourceStack, String, Integer> executor
        ) {
                for (String swizzle : swizzles) {
                        builder.then(Commands.literal(swizzle)
                                                                 .executes(ctx -> executor.apply(ctx.getSource(), swizzle)));
                }
                return builder;
        }

        private static int runDrawEcho(
                        CommandSourceStack source,
                        String message
        ) {

                sendSuccess(source, () -> Component.literal(message));
                return SINGLE_SUCCESS;
        }

        private static int runDrawHelp(CommandSourceStack source) {

                List<String> lines = List.of(
                                "SFM draw commands:",
                                "- /sfm draw help",
                                "- /sfm draw echo <message>",
                                "- /sfm draw open [path] (local draw screen only)",
                                "- /sfm draw move <from> <to> (local draw screen only)",
                                "- /sfm draw ls [path] (local draw screen only)",
                                "- /sfm draw box list (local draw screen only)",
                                "- /sfm draw box <x1> <y1> <x2> <y2> (local draw screen only)",
                                "- /sfm draw concatenate @rect[x,y] | @rel[dx,dy] (local draw screen only)",
                                "- /sfm draw ollama models (local draw screen only)",
                                "- /sfm draw ollama run <model> <prompt> (local draw screen only)",
                                "- /sfm draw player",
                                "- /sfm draw player pos",
                                "- /sfm draw player angle",
                                "- /sfm draw player dimension",
                                "- /sfm draw player look angle",
                                "- /sfm draw player look hit",
                                "- /sfm draw player look hit block",
                                "- /sfm draw player look hit manager",
                                "- /sfm draw player inv [slot]",
                                "- /sfm draw player armor [slot]",
                                "- /sfm draw player hand [mainhand|offhand]",
                                "- /sfm draw camera pos",
                                "- /sfm draw camera zoom",
                                "- /sfm draw mouse pos",
                                "- /sfm draw mouse screen_pos",
                                "- /sfm draw template list",
                                "- /sfm draw template open <template>"
                );
                return sendDrawLines(source, lines);
        }

        private static int runDrawLocalOnlyCommand(
                        CommandSourceStack source,
                        List<String> usages
        ) {
                List<String> lines = new ArrayList<>();
                lines.add("draw.local: this command is client-local and is intended for slash text elements inside the SFM draw screen.");
                lines.add("draw.local: it is registered here so chat completion and draw intellisense can discover it.");
                for (String usage : usages) {
                        lines.add("usage: " + usage);
                }
                return sendDrawLines(source, lines);
        }

        private static int runDrawTemplateList(CommandSourceStack source) {
                List<SFMDrawTemplate> templates = SFMDrawTemplateRegistry.gatherAll();
                List<String> lines = new ArrayList<>();
                lines.add("Draw templates:");
                if (templates.isEmpty()) {
                        lines.add("(none found)");
                        return sendDrawLines(source, lines);
                }

                for (SFMDrawTemplate template : templates) {
                        lines.add("- " + template.key() + " | " + template.displayName());
                }
                return sendDrawLines(source, lines);
        }

        private static int runDrawTemplateOpen(
                        CommandSourceStack source,
                        String templateName
        ) {
                SFMDrawTemplate template = SFMDrawTemplateRegistry.findByName(templateName);
                if (template == null) {
                        return sendDrawLines(source, List.of(
                                "template.open: not found: " + templateName,
                                "Use /sfm draw template list to inspect available template keys."
                        ));
                }

                DrawTemplateProgramCard card = buildDrawTemplateProgramCard(template);
                if (source.source instanceof CapturingDrawCommandSource capture) {
                        capture.captureTemplateProgramCard(card);
                        return SINGLE_SUCCESS;
                }
                return sendDrawLines(source, summarizeDrawTemplateProgramCard(card));
        }

        private static int runDrawPlayer(CommandSourceStack source) {
                ServerPlayer player = requireDrawPlayer(source);
                if (player == null) {
                        return 0;
                }

                List<String> lines = new ArrayList<>();
                appendPlayerDimension(lines, player);
                appendPlayerPosition(lines, player);
                appendPlayerAngle(lines, player);
                appendPlayerLookAngle(lines, player);
                appendPlayerLookHit(lines, player);
                appendPlayerLookHitBlock(lines, player);
                appendPlayerInventory(lines, player, null);
                appendPlayerArmor(lines, player, null);
                appendPlayerHands(lines, player);
                return sendDrawLines(source, lines);
        }

        private static int runDrawPlayerPos(CommandSourceStack source) {
                ServerPlayer player = requireDrawPlayer(source);
                if (player == null) {
                        return 0;
                }

                List<String> lines = new ArrayList<>();
                appendPlayerPosition(lines, player);
                return sendDrawLines(source, lines);
        }

        private static int runDrawPlayerPosSwizzle(
                        CommandSourceStack source,
                        String swizzle
        ) {
                ServerPlayer player = requireDrawPlayer(source);
                if (player == null) {
                        return 0;
                }

                return sendDrawLines(source, List.of(formatSwizzleLine("player.pos", swizzle, player.getX(), player.getY(), player.getZ())));
        }

        private static int runDrawPlayerAngle(CommandSourceStack source) {
                ServerPlayer player = requireDrawPlayer(source);
                if (player == null) {
                        return 0;
                }

                List<String> lines = new ArrayList<>();
                appendPlayerAngle(lines, player);
                return sendDrawLines(source, lines);
        }

        private static int runDrawPlayerDimension(CommandSourceStack source) {
                ServerPlayer player = requireDrawPlayer(source);
                if (player == null) {
                        return 0;
                }

                List<String> lines = new ArrayList<>();
                appendPlayerDimension(lines, player);
                return sendDrawLines(source, lines);
        }

        private static int runDrawPlayerLook(CommandSourceStack source) {
                ServerPlayer player = requireDrawPlayer(source);
                if (player == null) {
                        return 0;
                }

                List<String> lines = new ArrayList<>();
                appendPlayerLookAngle(lines, player);
                appendPlayerLookHit(lines, player);
                appendPlayerLookHitBlock(lines, player);
                return sendDrawLines(source, lines);
        }

        private static int runDrawPlayerLookAngle(CommandSourceStack source) {
                ServerPlayer player = requireDrawPlayer(source);
                if (player == null) {
                        return 0;
                }

                List<String> lines = new ArrayList<>();
                appendPlayerLookAngle(lines, player);
                return sendDrawLines(source, lines);
        }

        private static int runDrawPlayerLookHit(CommandSourceStack source) {
                ServerPlayer player = requireDrawPlayer(source);
                if (player == null) {
                        return 0;
                }

                List<String> lines = new ArrayList<>();
                appendPlayerLookHit(lines, player);
                appendPlayerLookHitBlock(lines, player);
                return sendDrawLines(source, lines);
        }

        private static int runDrawPlayerLookHitSwizzle(
                        CommandSourceStack source,
                        String swizzle
        ) {
                ServerPlayer player = requireDrawPlayer(source);
                if (player == null) {
                        return 0;
                }

                HitResult hitResult = resolvePlayerLookHit(player);
                Vec3 location = hitResult.getLocation();
                return sendDrawLines(source, List.of(formatSwizzleLine("player.look.hit", swizzle, location.x, location.y, location.z)));
        }

        private static int runDrawPlayerLookHitBlock(CommandSourceStack source) {
                ServerPlayer player = requireDrawPlayer(source);
                if (player == null) {
                        return 0;
                }

                List<String> lines = new ArrayList<>();
                appendPlayerLookHitBlock(lines, player);
                return sendDrawLines(source, lines);
        }

        private static int runDrawPlayerLookHitManager(CommandSourceStack source) {
                ServerPlayer player = requireDrawPlayer(source);
                if (player == null) {
                        return 0;
                }

                HitResult hitResult = resolvePlayerLookHit(player);
                if (!(hitResult instanceof BlockHitResult blockHitResult) || hitResult.getType() != HitResult.Type.BLOCK) {
                        return sendDrawLines(source, List.of("player.look.hit.manager: (miss)"));
                }

                if (!(player.level.getBlockEntity(blockHitResult.getBlockPos()) instanceof ManagerBlockEntity manager)) {
                        return sendDrawLines(source, List.of("player.look.hit.manager: (not a manager)"));
                }

                ItemStack disk = manager.getDisk();
                if (disk == null || DiskItem.getProgramString(disk).isBlank()) {
                        return sendDrawLines(source, List.of("player.look.hit.manager: (manager has no program disk)"));
                }

                DrawManagerProgramCard card = buildDrawManagerProgramCard(manager, disk);
                if (source.source instanceof CapturingDrawCommandSource capture) {
                        capture.captureManagerProgramCard(card);
                        return SINGLE_SUCCESS;
                }

                return sendDrawLines(source, summarizeDrawManagerProgramCard(card));
        }

        private static int runDrawPlayerInventory(
                        CommandSourceStack source,
                        Integer slot
        ) {
                ServerPlayer player = requireDrawPlayer(source);
                if (player == null) {
                        return 0;
                }

                List<String> lines = new ArrayList<>();
                appendPlayerInventory(lines, player, slot);
                return sendDrawLines(source, lines);
        }

        private static int runDrawPlayerArmor(
                        CommandSourceStack source,
                        Integer slot
        ) {
                ServerPlayer player = requireDrawPlayer(source);
                if (player == null) {
                        return 0;
                }

                List<String> lines = new ArrayList<>();
                appendPlayerArmor(lines, player, slot);
                return sendDrawLines(source, lines);
        }

        private static int runDrawPlayerHands(CommandSourceStack source) {
                ServerPlayer player = requireDrawPlayer(source);
                if (player == null) {
                        return 0;
                }

                List<String> lines = new ArrayList<>();
                appendPlayerHands(lines, player);
                return sendDrawLines(source, lines);
        }

        private static int runDrawPlayerHand(
                        CommandSourceStack source,
                        InteractionHand hand
        ) {
                ServerPlayer player = requireDrawPlayer(source);
                if (player == null) {
                        return 0;
                }

                List<String> lines = new ArrayList<>();
                appendPlayerHand(lines, player, hand);
                return sendDrawLines(source, lines);
        }

        private static int runDrawCameraPos(CommandSourceStack source) {
                DrawCommandClientContext clientContext = requireDrawClientContext(source, "/sfm draw camera pos");
                if (clientContext == null) {
                        return 0;
                }

                List<String> lines = new ArrayList<>();
                appendVector2(lines, "camera.pos", clientContext.cameraX(), clientContext.cameraY());
                return sendDrawLines(source, lines);
        }

        private static int runDrawCameraPosSwizzle(
                        CommandSourceStack source,
                        String swizzle
        ) {
                DrawCommandClientContext clientContext = requireDrawClientContext(source, "/sfm draw camera pos " + swizzle);
                if (clientContext == null) {
                        return 0;
                }

                return sendDrawLines(source, List.of(formatSwizzleLine("camera.pos", swizzle, clientContext.cameraX(), clientContext.cameraY())));
        }

        private static int runDrawCameraZoom(CommandSourceStack source) {
                DrawCommandClientContext clientContext = requireDrawClientContext(source, "/sfm draw camera zoom");
                if (clientContext == null) {
                        return 0;
                }

                return sendDrawLines(source, List.of("camera.zoom: " + formatDrawNumber(clientContext.zoom())));
        }

        private static int runDrawMousePos(CommandSourceStack source) {
                DrawCommandClientContext clientContext = requireDrawClientContext(source, "/sfm draw mouse pos");
                if (clientContext == null) {
                        return 0;
                }

                List<String> lines = new ArrayList<>();
                appendVector2(lines, "mouse.pos", clientContext.mouseCanvasX(), clientContext.mouseCanvasY());
                return sendDrawLines(source, lines);
        }

        private static int runDrawMousePosSwizzle(
                        CommandSourceStack source,
                        String swizzle
        ) {
                DrawCommandClientContext clientContext = requireDrawClientContext(source, "/sfm draw mouse pos " + swizzle);
                if (clientContext == null) {
                        return 0;
                }

                return sendDrawLines(source, List.of(formatSwizzleLine("mouse.pos", swizzle, clientContext.mouseCanvasX(), clientContext.mouseCanvasY())));
        }

        private static int runDrawMouseScreenPos(CommandSourceStack source) {
                DrawCommandClientContext clientContext = requireDrawClientContext(source, "/sfm draw mouse screen_pos");
                if (clientContext == null) {
                        return 0;
                }

                List<String> lines = new ArrayList<>();
                appendVector2(lines, "mouse.screen_pos", clientContext.mouseScreenX(), clientContext.mouseScreenY());
                return sendDrawLines(source, lines);
        }

        private static int runDrawMouseScreenPosSwizzle(
                        CommandSourceStack source,
                        String swizzle
        ) {
                DrawCommandClientContext clientContext = requireDrawClientContext(source, "/sfm draw mouse screen_pos " + swizzle);
                if (clientContext == null) {
                        return 0;
                }

                return sendDrawLines(source, List.of(formatSwizzleLine("mouse.screen_pos", swizzle, clientContext.mouseScreenX(), clientContext.mouseScreenY())));
        }

        private static ServerPlayer requireDrawPlayer(CommandSourceStack source) {
                ServerPlayer player = source.getPlayer();
                if (player != null) {
                        return player;
                }

                source.sendFailure(Component.literal("/sfm draw player requires a player source."));
                return null;
        }

        private static DrawCommandClientContext requireDrawClientContext(
                        CommandSourceStack source,
                        String commandName
        ) {
                if (source.source instanceof CapturingDrawCommandSource capture) {
                        DrawCommandClientContext clientContext = capture.getClientContext();
                        if (clientContext != null) {
                                return clientContext;
                        }
                }

                source.sendFailure(Component.literal(commandName + " requires draw screen context."));
                return null;
        }

        private static int sendDrawLines(
                        CommandSourceStack source,
                        List<String> lines
        ) {
                if (lines.isEmpty()) {
                        sendSuccess(source, () -> Component.literal("(no output)"));
                        return SINGLE_SUCCESS;
                }

                for (String line : lines) {
                        sendSuccess(source, () -> Component.literal(line));
                }
                return SINGLE_SUCCESS;
        }

        private static void appendPlayerDimension(
                        List<String> lines,
                        ServerPlayer player
        ) {
                lines.add("player.dimension: " + player.level.dimension().location());
        }

        private static void appendPlayerPosition(
                        List<String> lines,
                        ServerPlayer player
        ) {
                appendVector3(lines, "player.pos", player.getX(), player.getY(), player.getZ());
        }

        private static void appendPlayerAngle(
                        List<String> lines,
                        ServerPlayer player
        ) {
                lines.add("player.angle.yaw: " + formatDrawNumber(player.getYRot()));
                lines.add("player.angle.pitch: " + formatDrawNumber(player.getXRot()));
        }

        private static void appendPlayerLookAngle(
                        List<String> lines,
                        ServerPlayer player
        ) {
                lines.add("player.look.angle.yaw: " + formatDrawNumber(player.getYHeadRot()));
                lines.add("player.look.angle.pitch: " + formatDrawNumber(player.getXRot()));
        }

        private static void appendPlayerLookHit(
                        List<String> lines,
                        ServerPlayer player
        ) {
                HitResult hitResult = resolvePlayerLookHit(player);
                Vec3 location = hitResult.getLocation();
                appendVector3(lines, "player.look.hit", location.x, location.y, location.z);
        }

        private static void appendVector2(
                        List<String> lines,
                        String keyPrefix,
                        double x,
                        double y
        ) {
                lines.add(keyPrefix + ".x: " + formatDrawNumber(x));
                lines.add(keyPrefix + ".y: " + formatDrawNumber(y));
        }

        private static void appendVector3(
                        List<String> lines,
                        String keyPrefix,
                        double x,
                        double y,
                        double z
        ) {
                lines.add(keyPrefix + ".x: " + formatDrawNumber(x));
                lines.add(keyPrefix + ".y: " + formatDrawNumber(y));
                lines.add(keyPrefix + ".z: " + formatDrawNumber(z));
        }

        private static String formatSwizzleLine(
                        String keyPrefix,
                        String swizzle,
                        double x,
                        double y
        ) {
                List<String> values = new ArrayList<>(swizzle.length());
                for (int index = 0; index < swizzle.length(); index++) {
                        char axis = swizzle.charAt(index);
                        values.add(switch (axis) {
                                case 'x' -> formatDrawNumber(x);
                                case 'y' -> formatDrawNumber(y);
                                default -> throw new IllegalArgumentException("Unsupported 2D swizzle axis: " + axis);
                        });
                }
                return keyPrefix + "." + swizzle + ": " + String.join(", ", values);
        }

        private static String formatSwizzleLine(
                        String keyPrefix,
                        String swizzle,
                        double x,
                        double y,
                        double z
        ) {
                List<String> values = new ArrayList<>(swizzle.length());
                for (int index = 0; index < swizzle.length(); index++) {
                        char axis = swizzle.charAt(index);
                        values.add(switch (axis) {
                                case 'x' -> formatDrawNumber(x);
                                case 'y' -> formatDrawNumber(y);
                                case 'z' -> formatDrawNumber(z);
                                default -> throw new IllegalArgumentException("Unsupported 3D swizzle axis: " + axis);
                        });
                }
                return keyPrefix + "." + swizzle + ": " + String.join(", ", values);
        }

        private static void appendPlayerLookHitBlock(
                        List<String> lines,
                        ServerPlayer player
        ) {
                HitResult hitResult = resolvePlayerLookHit(player);
                String value = "(miss)";
                if (hitResult instanceof BlockHitResult blockHitResult && hitResult.getType() == HitResult.Type.BLOCK) {
                        ResourceLocation blockId = SFMWellKnownRegistries.BLOCKS.getId(
                                        player.level.getBlockState(blockHitResult.getBlockPos()).getBlock()
                        );
                        if (blockId != null) {
                                value = blockId.toString();
                        }
                }
                lines.add("player.look.hit.block: " + value);
        }

        private static DrawManagerProgramCard buildDrawManagerProgramCard(
                        ManagerBlockEntity manager,
                        ItemStack disk
        ) {
                String programString = DiskItem.getProgramString(disk).replace("\r", "");
                List<String> warningLines = DiskItem.getWarnings(disk)
                        .stream()
                        .map(MutableComponent::create)
                        .map(Component::getString)
                        .toList();
                List<String> errorLines = DiskItem.getErrors(disk)
                        .stream()
                        .map(MutableComponent::create)
                        .map(Component::getString)
                        .toList();

                List<String> detailLines = new ArrayList<>();
                int programLineCount = programString.split("\\R", -1).length;
                detailLines.add("program.lines: " + programLineCount);
                detailLines.add("program.chars: " + programString.length());

                List<String> astLines = List.of();
                var program = manager.getProgram();
                if (program != null) {
                        if (!program.name().isBlank()) {
                                detailLines.add("program.name: " + program.name());
                        }
                        detailLines.add("program.triggers: " + program.triggers().size());
                        detailLines.add("program.statements: " + program.getDescendantStatements().count());
                        detailLines.add("labels: " + summarizeValues(program.referencedLabels(), 6));
                        detailLines.add("resources: " + summarizeValues(
                                program.referencedResources().stream().map(Object::toString).toList(),
                                4
                        ));
                        astLines = buildAstSummaryLines(program);
                } else {
                        detailLines.add("program.build: failed");
                }
                detailLines.add("warnings: " + warningLines.size());
                detailLines.add("errors: " + errorLines.size());

                String diskName = DiskItem.getProgramName(disk);
                if (diskName.isBlank() && program != null && !program.name().isBlank()) {
                        diskName = program.name();
                }
                if (diskName.isBlank()) {
                        diskName = "Disk";
                }

                return new DrawManagerProgramCard(
                        manager.getBlockPos(),
                        manager.getState(),
                        diskName,
                        programString,
                        detailLines,
                        warningLines,
                        errorLines,
                        astLines
                );
        }

        private static List<String> summarizeDrawManagerProgramCard(DrawManagerProgramCard card) {
                List<String> lines = new ArrayList<>();
                lines.add("manager.pos: " + card.managerPos().toShortString());
                lines.add("manager.state: " + card.state().name());
                if (!card.diskName().isBlank()) {
                        lines.add("disk.name: " + card.diskName());
                }
                lines.addAll(card.detailLines());
                lines.add("Run this command in the draw screen to render a grouped program card.");
                return lines;
        }

        private static DrawTemplateProgramCard buildDrawTemplateProgramCard(SFMDrawTemplate template) {
                String programString = template.programString().replace("\r", "");
                var programBuildResult = new ProgramBuilder(programString).build();
                List<String> warningLines = List.of();
                List<String> errorLines = programBuildResult.metadata().errors()
                        .stream()
                        .map(MutableComponent::create)
                        .map(Component::getString)
                        .toList();

                List<String> detailLines = new ArrayList<>();
                detailLines.add("template.key: " + template.key());
                detailLines.add("template.resource: " + template.resourcePath());
                detailLines.add("program.lines: " + programString.split("\\R", -1).length);
                detailLines.add("program.chars: " + programString.length());

                List<String> astLines = List.of();
                var program = programBuildResult.program();
                if (program != null) {
                        if (!program.name().isBlank()) {
                                detailLines.add("program.name: " + program.name());
                        }
                        detailLines.add("program.triggers: " + program.triggers().size());
                        detailLines.add("program.statements: " + program.getDescendantStatements().count());
                        detailLines.add("labels: " + summarizeValues(program.referencedLabels(), 6));
                        detailLines.add("resources: " + summarizeValues(
                                program.referencedResources().stream().map(Object::toString).toList(),
                                4
                        ));
                        astLines = buildAstSummaryLines(program);
                } else {
                        detailLines.add("program.build: failed");
                }
                detailLines.add("warnings: " + warningLines.size());
                detailLines.add("errors: " + errorLines.size());

                return new DrawTemplateProgramCard(
                        template.key(),
                        template.displayName(),
                        programString,
                        detailLines,
                        warningLines,
                        errorLines,
                        astLines
                );
        }

        private static List<String> summarizeDrawTemplateProgramCard(DrawTemplateProgramCard card) {
                List<String> lines = new ArrayList<>();
                lines.add("template.key: " + card.templateKey());
                if (!card.displayName().isBlank()) {
                        lines.add("template.name: " + card.displayName());
                }
                lines.addAll(card.detailLines());
                lines.add("Run this command in the draw screen to render a grouped template card.");
                return lines;
        }

        private static List<String> buildAstSummaryLines(ca.teamdman.sfml.ast.Program program) {
                List<String> lines = new ArrayList<>();
                boolean truncated = appendAstSummary(lines, program, 0, program.astBuilder(), 64);
                if (truncated) {
                        lines.add("...");
                }
                return lines;
        }

        private static boolean appendAstSummary(
                        List<String> lines,
                        ca.teamdman.sfml.ast.ASTNode node,
                        int depth,
                        ca.teamdman.sfml.ast.ASTBuilder astBuilder,
                        int maxLines
        ) {
                if (lines.size() >= maxLines) {
                        return true;
                }

                String label = node.getClass().getSimpleName();
                if (node instanceof ca.teamdman.sfml.ast.Program program && !program.name().isBlank()) {
                        label += " \"" + program.name() + "\"";
                }
                String location = astBuilder.getContextForNode(node)
                        .map(ctx -> "@" + ctx.start.getLine() + ":" + ctx.start.getCharPositionInLine())
                        .orElse("@");
                lines.add("  ".repeat(Math.max(0, depth)) + label + " " + location);
                if (lines.size() >= maxLines) {
                        return true;
                }

                for (ca.teamdman.sfml.ast.Statement statement : node.getStatements()) {
                        if (appendAstSummary(lines, statement, depth + 1, astBuilder, maxLines)) {
                                return true;
                        }
                }
                return false;
        }

        private static String summarizeValues(
                        Collection<?> values,
                        int maxItems
        ) {
                List<String> displayValues = values.stream()
                        .map(String::valueOf)
                        .sorted()
                        .toList();
                if (displayValues.isEmpty()) {
                        return "(none)";
                }
                if (displayValues.size() <= maxItems) {
                        return String.join(", ", displayValues);
                }
                return String.join(", ", displayValues.subList(0, maxItems))
                       + " +"
                       + (displayValues.size() - maxItems)
                       + " more";
        }

        private static void appendPlayerInventory(
                        List<String> lines,
                        ServerPlayer player,
                        Integer slot
        ) {
                List<ItemStack> items = player.getInventory().items;
                if (slot != null) {
                        lines.add("player.inv." + slot + ": " + itemId(items.get(slot)));
                        return;
                }

                for (int index = 0; index < items.size(); index++) {
                        lines.add("player.inv." + index + ": " + itemId(items.get(index)));
                }
        }

        private static void appendPlayerArmor(
                        List<String> lines,
                        ServerPlayer player,
                        Integer slot
        ) {
                if (slot != null) {
                        lines.add("player.armor." + armorSlotKey(slot) + ": " + itemId(player.getItemBySlot(armorSlot(slot))));
                        return;
                }

                for (int index = 0; index < 4; index++) {
                        lines.add("player.armor." + armorSlotKey(index) + ": " + itemId(player.getItemBySlot(armorSlot(index))));
                }
        }

        private static void appendPlayerHands(
                        List<String> lines,
                        ServerPlayer player
        ) {
                appendPlayerHand(lines, player, InteractionHand.MAIN_HAND);
                appendPlayerHand(lines, player, InteractionHand.OFF_HAND);
        }

        private static void appendPlayerHand(
                        List<String> lines,
                        ServerPlayer player,
                        InteractionHand hand
        ) {
                String handKey = hand == InteractionHand.MAIN_HAND ? "mainhand" : "offhand";
                lines.add("player.hand." + handKey + ": " + itemId(player.getItemInHand(hand)));
        }

        private static String itemId(ItemStack stack) {
                ResourceLocation itemId = SFMWellKnownRegistries.ITEMS.getId(stack.getItem());
                return itemId == null ? "(unknown item)" : itemId.toString();
        }

        private static EquipmentSlot armorSlot(int slot) {
                return switch (slot) {
                        case 0 -> EquipmentSlot.HEAD;
                        case 1 -> EquipmentSlot.CHEST;
                        case 2 -> EquipmentSlot.LEGS;
                        case 3 -> EquipmentSlot.FEET;
                        default -> throw new IllegalArgumentException("Unsupported armor slot: " + slot);
                };
        }

        private static String armorSlotKey(int slot) {
                return switch (slot) {
                        case 0 -> "head";
                        case 1 -> "chest";
                        case 2 -> "legs";
                        case 3 -> "feet";
                        default -> throw new IllegalArgumentException("Unsupported armor slot: " + slot);
                };
        }

        private static HitResult resolvePlayerLookHit(ServerPlayer player) {
                Vec3 start = player.getEyePosition();
                Vec3 end = start.add(player.getViewVector(1.0F).scale(64.0D));
                return player.level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        }

        private static String formatDrawNumber(double value) {
                return String.format(Locale.ROOT, "%.3f", value);
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
        BlockPos sourcePos = new BlockPos(source.getPosition());
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
